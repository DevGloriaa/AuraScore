package com.enyata.aurascore.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class InterswitchService {

    private static final String DEMO_AMOUNT_KOBO = "50000";
    private static final String DEMO_SITE_REDIRECT_URL = "http://localhost:3000/";
    private static final String DEMO_CURRENCY = "566";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ISW_MERCHANT_CODE:MX6072}")
    private String merchantCode;

    @Value("${interswitch.product-id:}")
    private String productId;

    @Value("${ISW_PAY_ITEM_ID:9405967}")
    private String payItemId;

    @Value("${ISW_SECRET_KEY:ajkdpGiF6PHVrwK}")
    private String secretKey;

    @Value("${interswitch.client.id}")
    private String clientId;

    @Value("${interswitch.client.secret}")
    private String clientSecret;

    @Value("${interswitch.identity.api.url:https://api-marketplace-routing.k8.isw.la}")
    private String identityBaseUrl;

    @Value("${interswitch.token.url:https://qa.interswitchng.com/passport/oauth/token}")
    private String tokenUrl;

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

    public WebpayInitResponse initiatePayment(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email is required");
        }

        String txnRef = generateTxnRef();
        String amount = DEMO_AMOUNT_KOBO;
        String siteRedirectUrl = DEMO_SITE_REDIRECT_URL;
        String resolvedProductId = resolveProductId();

        String rawString = txnRef + resolvedProductId + payItemId + amount + siteRedirectUrl + secretKey;
        String hash = generateSha512(rawString);

        return new WebpayInitResponse(
                txnRef,
                hash,
                amount,
                siteRedirectUrl,
                resolvedProductId,
                payItemId,
                DEMO_CURRENCY,
                email.trim()
        );
    }

    private String generateTxnRef() {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "AURA-" + timestamp + "-" + suffix;
    }

    private String resolveProductId() {
        if (productId != null && !productId.isBlank()) {
            return productId.trim();
        }
        return merchantCode == null ? "" : merchantCode.trim();
    }

    private String generateSha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "SHA-512 Algorithm not found");
        }
    }

    public JsonNode validatePayment(String transactionReference, String expectedAmountKobo) {
        if (transactionReference == null || transactionReference.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "transactionReference is required");
        }
        if (expectedAmountKobo == null || expectedAmountKobo.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "amount is required");
        }

        try {
            String normalizedReference = transactionReference.trim();
            String normalizedAmount = expectedAmountKobo.trim();

            String url = UriComponentsBuilder.fromUriString("https://qa.interswitchng.com/collections/api/v1/gettransaction.json")
                    .queryParam("merchantcode", merchantCode.trim())
                    .queryParam("transactionreference", normalizedReference)
                    .queryParam("amount", normalizedAmount)
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
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Payment verification API unavailable", ex);
        }
    }

    public String verifyBankAccount(String accountNumber, String bankCode) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "accountNumber is required");
        }
        if (bankCode == null || bankCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "bankCode is required");
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(buildIdentityUrl("/api/v1/nameenquiry/banks/accounts/names"))
                    .queryParam("accountNumber", accountNumber.trim())
                    .queryParam("bankCode", bankCode.trim())
                    .build()
                    .toUriString();

            HttpEntity<Void> request = new HttpEntity<>(bearerJsonHeaders(getAccessToken()));
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Bank verification failed");
            }
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex, "Bank verification failed");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Bank verification API unavailable", ex);
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

    private synchronized String getAccessToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && !cachedToken.isBlank() && now < (tokenExpiresAtEpochSeconds - 60)) {
            return cachedToken;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");
            headers.set("Authorization", "Basic " + base64(clientId.trim() + ":" + clientSecret.trim()));

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(resolveTokenUrl(), requestEntity, String.class);

            JsonNode tokenJson = objectMapper.readTree(response.getBody());
            String accessToken = tokenJson.path("access_token").asText("");
            long expiresIn = tokenJson.path("expires_in").asLong(300L);

            cachedToken = accessToken;
            tokenExpiresAtEpochSeconds = Instant.now().getEpochSecond() + Math.max(1L, expiresIn);
            return cachedToken;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch Auth Failed", ex);
        }
    }

    private HttpHeaders bearerJsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("TerminalID", "7000000001");
        return headers;
    }

    private ResponseStatusException mapClientError(HttpClientErrorException ex, String message) {
        if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            return new ResponseStatusException(BAD_GATEWAY, message + ": authorization failed", ex);
        }
        return new ResponseStatusException(BAD_REQUEST, message, ex);
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String buildIdentityUrl(String path) {
        String normalizedBase = (identityBaseUrl == null || identityBaseUrl.isBlank()) ? "https://api-marketplace-routing.k8.isw.la" : identityBaseUrl.trim();
        if (normalizedBase.endsWith("/")) normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        return normalizedBase + path;
    }

    private String resolveTokenUrl() {
        if (tokenUrl != null && !tokenUrl.isBlank()) return tokenUrl.trim();
        return "https://qa.interswitchng.com/passport/oauth/token";
    }

    public record WebpayInitResponse(
            @JsonProperty("txn_ref") String txnRef,
            @JsonProperty("hash") String hash,
            @JsonProperty("amount") String amount,
            @JsonProperty("site_redirect_url") String siteRedirectUrl,
            @JsonProperty("product_id") String productId,
            @JsonProperty("pay_item_id") String payItemId,
            @JsonProperty("currency") String currency,
            @JsonProperty("email") String email
    ) {
    }
}