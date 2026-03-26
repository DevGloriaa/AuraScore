package com.enyata.aurascore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String googleApiKey;
    private final String endpoint;

    private static final String SYSTEM_PROMPT = """
            You are the Aura Credit Officer. Analyze these Nigerian bank transactions.
            Return ONLY a valid JSON object with:
            - auraScore (Integer 300-850)
            - summary (Short 2-sentence personality profile)
            - category (e.g., 'Rising Entrepreneur', 'Steady Professional', 'High Risk')
            """;

    public GeminiService(
            ObjectMapper objectMapper,
            @Value("${GOOGLE_API_KEY}") String googleApiKey
    ) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.googleApiKey = googleApiKey;
        this.endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + googleApiKey;
        System.out.println("✅ AuraScore Gemini Service Initialized with key: " + (googleApiKey != null ? "FOUND" : "MISSING"));
    }

    public JsonNode analyzeAura(JsonNode transactions) throws Exception {
        String payload = objectMapper.writeValueAsString(transactions);
        String fullPrompt = SYSTEM_PROMPT + "\n\nTransactions:\n" + payload;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
                {
                  "contents": [
                    {
                      "parts": [
                        {"text": "%s"}
                      ]
                    }
                  ]
                }
                """.formatted(fullPrompt.replace("\"", "\\\"").replace("\n", "\\n"));

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        System.out.println("🚀 Sending transactions to Gemini...");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(endpoint, request, JsonNode.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String aiText = response.getBody()
                    .path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            System.out.println("Gemini Analysis Received!");
            return normalizeAuraJson(aiText);
        }

        throw new RuntimeException("Gemini API call failed with status: " + response.getStatusCode());
    }

    private JsonNode normalizeAuraJson(String aiText) throws Exception {
        String cleaned = aiText.replaceAll("```json", "").replaceAll("```", "").trim();
        return objectMapper.readTree(cleaned);
    }
}