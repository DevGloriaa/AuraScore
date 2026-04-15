package com.enyata.aurascore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class MonoService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Value("${mono.secret.key}")
    private String monoSecretKey;

    public MonoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String exchangeToken(String code) {
        // ... (Your existing exchangeToken code is good as-is)
        String url = "https://api.withmono.com/v2/accounts/auth";
        HttpHeaders headers = new HttpHeaders();
        headers.set("mono-sec-key", monoSecretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("accept", "application/json");

        String body = "{\"code\": \"" + code + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            JsonNode responseBody = response.getBody();
            return responseBody == null ? "" : responseBody.path("data").path("id").asText("");
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Mono Auth failed: " + e.getResponseBodyAsString());
        }
    }

    public JsonNode getTransactions(String accountId) {
        String url = "https://api.withmono.com/v2/accounts/" + accountId + "/transactions";

        HttpHeaders headers = new HttpHeaders();
        headers.set("mono-sec-key", monoSecretKey);
        headers.set("accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode rawData = response.getBody();
            return rawData == null ? objectMapper.createObjectNode() : rawData;

        } catch (HttpClientErrorException e) {
            System.err.println("MONO FETCH FAILED: " + e.getResponseBodyAsString());
            throw new RuntimeException("Mono Fetch failed");
        }
    }
}