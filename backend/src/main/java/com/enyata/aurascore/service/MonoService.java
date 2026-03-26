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
        System.out.println("Attempting LIVE Mono Auth with code: " + code);
        String url = "https://api.withmono.com/v2/accounts/auth";

        HttpHeaders headers = new HttpHeaders();
        headers.set("mono-sec-key", monoSecretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("accept", "application/json");

        String body = "{\"code\": \"" + code + "\"}";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, entity, JsonNode.class);
            System.out.println("Raw Auth Response: " + response.getBody());


            String accountId = response.getBody().path("data").path("id").asText("");
            if (accountId.isBlank()) {
                throw new RuntimeException("Mono did not return an ID! Response: " + response.getBody());
            }

            System.out.println("Mono Auth Successful! Account ID: " + accountId);
            return accountId;

        } catch (HttpClientErrorException e) {

            System.err.println("MONO AUTH FAILED! Error from Mono: " + e.getResponseBodyAsString());
            throw new RuntimeException("Mono Auth failed");
        }
    }

    public JsonNode getTransactions(String accountId) {
        System.out.println("Fetching LIVE transactions for Account: " + accountId);
        String url = "https://api.withmono.com/v2/accounts/" + accountId + "/transactions";

        HttpHeaders headers = new HttpHeaders();
        headers.set("mono-sec-key", monoSecretKey);
        headers.set("accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            System.out.println("LIVE Transactions fetched successfully!");

            return response.getBody();

        } catch (HttpClientErrorException e) {
            System.err.println("MONO FETCH FAILED! Error from Mono: " + e.getResponseBodyAsString());
            throw new RuntimeException("Mono Fetch failed");
        }
    }
}