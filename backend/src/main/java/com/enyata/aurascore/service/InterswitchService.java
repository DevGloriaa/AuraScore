package com.enyata.aurascore.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class InterswitchService {

    private static final String OAUTH_PATH = "/passport/oauth/token";
    private static final String KYC_PATH = "/api/v1/kyc/verify";
    private static final String VAS_PATH = "/api/v1/vas/transactions";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${interswitch.api.url:https://sandbox.interswitch.com}")
    private String interswitchBaseUrl;

    @Value("${interswitch.client.id:sandbox_id}")
    private String interswitchClientId;

    @Value("${interswitch.client.secret:sandbox_secret}")
    private String interswitchClientSecret;

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
        try {
            String token = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!token.isBlank()) {
                headers.setBearerAuth(token);
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("phoneNumber", phoneNumber);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(buildUrl(KYC_PATH), requestEntity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return buildKycFallback(phoneNumber);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String name = root.path("name").asText("");
            String status = root.path("verificationStatus").asText("");

            if (name.isBlank()) {
                name = root.path("data").path("name").asText("Aura Demo User");
            }
            if (status.isBlank()) {
                status = root.path("data").path("verificationStatus").asText("SUCCESS");
            }

            Map<String, Object> normalized = new HashMap<>();
            normalized.put("source", "LIVE");
            normalized.put("phoneNumber", phoneNumber);
            normalized.put("name", name);
            normalized.put("verificationStatus", status);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return buildKycFallback(phoneNumber);
        }
    }

    public String getVASTransactions(String phoneNumber) {
        try {
            String token = getAccessToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!token.isBlank()) {
                headers.setBearerAuth(token);
            }

            String endpoint = buildUrl(VAS_PATH) + "?phoneNumber=" + phoneNumber;
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.GET, requestEntity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return buildVasFallback(phoneNumber);
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode transactionsNode = root.path("transactions");
            if (transactionsNode.isMissingNode() || !transactionsNode.isArray()) {
                transactionsNode = root.path("data").path("transactions");
            }

            if (transactionsNode.isMissingNode() || !transactionsNode.isArray() || transactionsNode.isEmpty()) {
                return buildVasFallback(phoneNumber);
            }

            Map<String, Object> normalized = new HashMap<>();
            normalized.put("source", "LIVE");
            normalized.put("phoneNumber", phoneNumber);
            normalized.put("transactions", transactionsNode);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            // Keep demo flow stable when sandbox connectivity is unavailable.
            return buildVasFallback(phoneNumber);
        }
    }

    public String getTransactionHistory(String phoneNumber) {
        return getVASTransactions(phoneNumber);
    }

    private String getAccessToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(interswitchClientId, interswitchClientSecret);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(buildUrl(OAUTH_PATH), requestEntity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return "";
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("access_token").asText("");
        } catch (Exception ex) {
            return "";
        }
    }

    private String buildKycFallback(String phoneNumber) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("source", "MOCK");
        fallback.put("phoneNumber", phoneNumber);
        fallback.put("name", "Mock Aura Demo User");
        fallback.put("verificationStatus", "SUCCESS");
        try {
            return objectMapper.writeValueAsString(fallback);
        } catch (Exception ex) {
            return "{\"source\":\"MOCK\",\"phoneNumber\":\"" + sanitize(phoneNumber) + "\",\"name\":\"Mock Aura Demo User\",\"verificationStatus\":\"SUCCESS\"}";
        }
    }

    private String buildVasFallback(String phoneNumber) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("source", "MOCK");
        fallback.put("phoneNumber", phoneNumber);
        fallback.put("transactions", List.of(
                Map.of("type", "Mock Airtime", "amount", 2500, "status", "SUCCESS"),
                Map.of("type", "Mock Electricity", "amount", 12000, "status", "SUCCESS"),
                Map.of("type", "Mock Data", "amount", 3500, "status", "SUCCESS")
        ));
        try {
            return objectMapper.writeValueAsString(fallback);
        } catch (Exception ex) {
            return "{\"source\":\"MOCK\",\"phoneNumber\":\"" + sanitize(phoneNumber) + "\",\"transactions\":[{\"type\":\"Mock Airtime\",\"amount\":2500,\"status\":\"SUCCESS\"},{\"type\":\"Mock Electricity\",\"amount\":12000,\"status\":\"SUCCESS\"},{\"type\":\"Mock Data\",\"amount\":3500,\"status\":\"SUCCESS\"}]}";
        }
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String buildUrl(String path) {
        String base = interswitchBaseUrl == null ? "https://sandbox.interswitch.com" : interswitchBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}

