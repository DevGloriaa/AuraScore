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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiService {

    private static final String FALLBACK_RESPONSE =
            "{\"score\":500,\"insights\":\"Mock: external AI unavailable, fallback score applied.\"}";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    public GeminiService(ObjectMapper objectMapper, RestTemplateBuilder restTemplateBuilder) {
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

    public String analyzeTransactions(String transactionData) {
        try {
            String prompt = "You are an expert FinTech AI. Analyze this user's Interswitch transaction history: "
                    + transactionData
                    + ". Generate a Web3 credit score between 300 and 850. Return ONLY a valid JSON object with exactly two keys: 'score' (integer) and 'insights' (a short 1-sentence reason based on the data). Do not use markdown blocks.";

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(textPart));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.2);

            Map<String, Object> payload = new HashMap<>();
            payload.put("contents", List.of(content));
            payload.put("generationConfig", generationConfig);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);
            String endpoint = buildEndpoint(geminiApiUrl, geminiApiKey);

            ResponseEntity<String> response = restTemplate.postForEntity(endpoint, requestEntity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return FALLBACK_RESPONSE;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String modelText = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText();

            if (modelText == null || modelText.isBlank()) {
                return FALLBACK_RESPONSE;
            }

            // Gemini can still wrap JSON in fences occasionally; sanitize before returning.
            String cleaned = modelText.replace("```json", "")
                    .replace("```", "")
                    .trim();

            JsonNode candidateJson = objectMapper.readTree(cleaned);
            int score = candidateJson.path("score").asInt(500);
            String insights = candidateJson.path("insights")
                    .asText("Mock: external AI unavailable, fallback score applied.");

            Map<String, Object> normalized = new HashMap<>();
            normalized.put("score", score);
            normalized.put("insights", insights);
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            return FALLBACK_RESPONSE;
        }
    }

    public String analyze(String transactionHistory) {
        return analyzeTransactions(transactionHistory);
    }

    private String buildEndpoint(String apiUrl, String apiKey) {
        if (apiUrl.contains("?")) {
            return apiUrl + "&key=" + apiKey;
        }
        return apiUrl + "?key=" + apiKey;
    }
}

