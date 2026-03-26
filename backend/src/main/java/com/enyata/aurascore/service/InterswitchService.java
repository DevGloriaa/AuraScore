package com.enyata.aurascore.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class InterswitchService {

    private static final String BANK_ACCOUNT_PATH = "/api/v1/nameenquiry/banks/accounts/names";
    private static final String DEFAULT_IDENTITY_BASE_URL = "https://api-marketplace-routing.k8.isw.la";
    private static final long TOKEN_EXPIRY_SAFETY_WINDOW_SECONDS = 60;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${interswitch.identity.api.url:https://api-marketplace-routing.k8.isw.la}")
    private String identityBaseUrl;

    @Value("${interswitch.token.url:https://qa.interswitchng.com/passport/oauth/token}")
    private String tokenUrl;

    @Value("${interswitch.client.id}")
    private String clientId;

    @Value("${interswitch.client.secret}")
    private String clientSecret;

    private volatile String cachedToken;
    private volatile long tokenExpiresAtEpochSeconds;

    public InterswitchService(ObjectMapper objectMapper, RestTemplateBuilder restTemplateBuilder) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplateBuilder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(10_000);
                    factory.setReadTimeout(10_000);
                    return factory;
                })
                .build();
    }

    public JsonNode validatePayment(String transactionReference, String expectedAmountKobo) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://qa.interswitchng.com/collections/api/v1/gettransaction.json")
                    .queryParam("merchantcode", clientId.trim())
                    .queryParam("transactionreference", transactionReference.trim())
                    .queryParam("amount", expectedAmountKobo.trim())
                    .build()
                    .toUriString();

            HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(getAccessToken()));
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            try {
                return objectMapper.readTree(ex.getResponseBodyAsString());
            } catch (Exception parseEx) {
                throw new ResponseStatusException(BAD_GATEWAY, "Payment verification API failed", ex);
            }
        }
    }

    public JsonNode checkLoanEligibility(String customerId, String requestedAmountKobo) {
        try {
            String url = "https://qa.interswitchng.com/paymentgateway/api/v1/lending/eligibility";
            Map<String, Object> body = Map.of(
                    "amount", requestedAmountKobo,
                    "customerId", customerId,
                    "entityCode", "PBL"
            );
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, bearerJsonHeaders(getAccessToken()));
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            try {
                return objectMapper.readTree(ex.getResponseBodyAsString());
            } catch (Exception parseEx) {
                throw new ResponseStatusException(BAD_GATEWAY, "Lending eligibility API failed", ex);
            }
        }
    }

    public String verifyBankAccount(String accountNumber, String bankCode) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "accountNumber is required for bank account verification");
        }
        if (bankCode == null || bankCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "bankCode is required for bank account verification");
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(buildIdentityUrl(BANK_ACCOUNT_PATH))
                    .queryParam("accountNumber", accountNumber.trim())
                    .queryParam("bankCode", bankCode.trim())
                    .build()
                    .toUriString();

            HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(getAccessToken()));
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Interswitch bank account verification failed");
            }
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex, "Interswitch bank account verification failed");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch bank account verification API unavailable", ex);
        }
    }

    @Deprecated
    public String getTransactionHistory(String phoneNumber) {
        throw new ResponseStatusException(BAD_REQUEST, "Interswitch transaction history is no longer supported in this service");
    }

    private synchronized String getAccessToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && !cachedToken.isBlank() && now < (tokenExpiresAtEpochSeconds - TOKEN_EXPIRY_SAFETY_WINDOW_SECONDS)) {
            return cachedToken;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");

            String safeClientId = clientId.trim();
            String safeClientSecret = clientSecret.trim();
            headers.set("Authorization", "Basic " + base64(safeClientId + ":" + safeClientSecret));

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    resolveTokenUrl(),
                    requestEntity,
                    String.class
            );

            JsonNode tokenJson = objectMapper.readTree(response.getBody());
            String accessToken = tokenJson.path("access_token").asText("");
            long expiresIn = tokenJson.path("expires_in").asLong(300L);

            cachedToken = accessToken;
            tokenExpiresAtEpochSeconds = Instant.now().getEpochSecond() + Math.max(1L, expiresIn);
            return cachedToken;

        } catch (HttpClientErrorException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch Auth Failed", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to parse token response", ex);
        }
    }

    private HttpHeaders bearerJsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("TerminalID", "3PBA0001");
        return headers;
    }

    private ResponseStatusException mapClientError(HttpClientErrorException ex, String message) {
        if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            return new ResponseStatusException(BAD_GATEWAY, message + ": upstream authorization failed", ex);
        }
        return new ResponseStatusException(BAD_REQUEST, message, ex);
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String buildIdentityUrl(String path) {
        String normalizedBase = (identityBaseUrl == null || identityBaseUrl.isBlank())
                ? DEFAULT_IDENTITY_BASE_URL
                : identityBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return normalizedBase + path;
    }

    private String resolveTokenUrl() {
        if (tokenUrl != null && !tokenUrl.isBlank()) {
            return tokenUrl.trim();
        }
        return "https://qa.interswitchng.com/passport/oauth/token";
    }
}
