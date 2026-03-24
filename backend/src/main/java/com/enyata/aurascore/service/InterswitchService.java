package com.enyata.aurascore.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
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

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class InterswitchService {

    private static final String TOKEN_PATH = "/passport/oauth/token";
    private static final String NIN_VERIFY_PATH = "/api/v1/identities/nin/verify";
    private static final String BVN_VERIFY_PATH = "/api/v1/identities/bvn/verify";
    private static final String BILLS_CATEGORIES_PATH = "/api/v2/bills/categories";
    private static final long TOKEN_EXPIRY_SAFETY_WINDOW_SECONDS = 60;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${interswitch.api.url:https://sandbox.interswitchng.com}")
    private String baseUrl;

    @Value("${interswitch.client.id:sandbox_id}")
    private String clientId;

    @Value("${interswitch.client.secret:sandbox_secret}")
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

    public String verifyKYC(String phoneNumber) {
        return verifyNin(phoneNumber);
    }

    public String verifyBVN(String bvn) {
        return verifyBvn(bvn);
    }

    public String verifyNin(String nin) {
        if (nin == null || nin.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "nin is required for NIN verification");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("nin", nin);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, bearerJsonHeaders(getAccessToken()));
            ResponseEntity<String> response = restTemplate.postForEntity(buildUrl(NIN_VERIFY_PATH), request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Interswitch NIN Verification Failed");
            }
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex, "Interswitch NIN Verification Failed");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch NIN API unavailable", ex);
        }
    }

    public String verifyBvn(String bvn) {
        if (bvn == null || bvn.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "bvn is required for BVN verification");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("bvn", bvn);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, bearerJsonHeaders(getAccessToken()));
            ResponseEntity<String> response = restTemplate.postForEntity(buildUrl(BVN_VERIFY_PATH), request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Interswitch BVN Verification Failed");
            }
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex, "Interswitch BVN Verification Failed");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch BVN API unavailable", ex);
        }
    }

    public String getTransactionHistory(String phoneNumber) {
        return getBillsTransactions(phoneNumber);
    }

    public String getBillsTransactions(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "phoneNumber is required to fetch bills transactions");
        }

        try {
            HttpHeaders headers = bearerJsonHeaders(getAccessToken());
            headers.add("X-Phone-Number", phoneNumber);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    buildUrl(BILLS_CATEGORIES_PATH) + "?phoneNumber=" + phoneNumber,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Interswitch Bills API returned an empty response");
            }
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex, "Interswitch Bills Transactions Fetch Failed");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch Bills API unavailable", ex);
        }
    }

    private synchronized String getAccessToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && !cachedToken.isBlank() && now < (tokenExpiresAtEpochSeconds - TOKEN_EXPIRY_SAFETY_WINDOW_SECONDS)) {
            return cachedToken;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String safeClientId = clientId.trim();
            String safeClientSecret = clientSecret.trim();
            headers.set("Authorization", "Basic " + base64(safeClientId + ":" + safeClientSecret));

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    buildUrl(TOKEN_PATH),
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
            String actualInterswitchError = ex.getResponseBodyAsString();
            System.err.println("INTERSWITCH RAW ERROR: " + actualInterswitchError);
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch Auth Failed: " + actualInterswitchError, ex);
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

    private String buildUrl(String path) {
        String normalizedBase = (baseUrl == null || baseUrl.isBlank()) ? "https://sandbox.interswitchng.com" : baseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        return normalizedBase + path;
    }
}
