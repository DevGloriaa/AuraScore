package com.enyata.aurascore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String endpoint;

    private static final String SYSTEM_PROMPT = """
            You are a Senior DeFi Credit Analyst. Analyze the following raw bank transaction data. You must evaluate cash flow stability, spending habits, and income consistency.
            
            Return ONLY a raw JSON object with absolutely no markdown formatting, no backticks, and no extra text. Use this exact schema:
            {
              "auraScore": [An integer between 300 and 850],
              "persona": [Choose one: 'The Aggressive Accumulator', 'The Consistent Saver', 'The High-Velocity Spender', 'The Risk-Prone Payer'],
              "loanEligibility": [Choose one: 'ELIGIBLE', 'HIGH_RISK', 'REJECTED'],
              "recommendation": [A punchy, one-sentence professional summary of their financial health]
            }
            """;

    public GeminiService(
            ObjectMapper objectMapper,
            @Value("${GOOGLE_API_KEY}") String googleApiKey
    ) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
        this.endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + googleApiKey;
        log.info("AuraScore Gemini Service Initialized with key: {}", (googleApiKey != null && !googleApiKey.isEmpty() ? "FOUND" : "MISSING"));
    }

    public JsonNode analyzeAura(JsonNode transactions) throws Exception {
        JsonNode normalizedTransactions = transactions;
        if (transactions != null
                && transactions.has("data")
                && transactions.path("data").has("transactions")
                && transactions.path("data").path("transactions").isArray()) {
            normalizedTransactions = transactions.path("data").path("transactions");
        }

        if (normalizedTransactions == null || normalizedTransactions.isMissingNode() || normalizedTransactions.isNull() || normalizedTransactions.isEmpty() || normalizedTransactions.toString().equals("[]")) {
            log.warn("Mono returned empty transaction data. Triggering Thin File fallback.");
            String fallback = """
                {
                  "auraScore": 0,
                  "persona": "Thin File",
                  "loanEligibility": "REJECTED",
                  "recommendation": "Insufficient transaction history to generate an Aura Score."
                }
                """;
            return objectMapper.readTree(fallback);
        }

        String payload = objectMapper.writeValueAsString(normalizedTransactions);
        String fullPrompt = SYSTEM_PROMPT + "\n\nTransaction Data:\n" + payload;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBodyMap = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", fullPrompt)
                        ))
                )
        );
        String requestBody = objectMapper.writeValueAsString(requestBodyMap);

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        log.info("Sending transactions to Gemini AI...");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(endpoint, request, JsonNode.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode candidates = response.getBody().path("candidates");
            JsonNode firstCandidate = candidates.isArray() && candidates.size() > 0 ? candidates.get(0) : null;
            if (firstCandidate == null || firstCandidate.isMissingNode() || firstCandidate.isNull()) {
                throw new RuntimeException("Gemini response missing candidates");
            }

            JsonNode parts = firstCandidate.path("content").path("parts");
            JsonNode firstPart = parts.isArray() && parts.size() > 0 ? parts.get(0) : null;
            if (firstPart == null || firstPart.isMissingNode() || firstPart.isNull()) {
                throw new RuntimeException("Gemini response missing content parts");
            }
            String aiText = firstPart.path("text").asText();

            log.info("Gemini Analysis Received!");
            return normalizeAuraJson(aiText);
        }

        throw new RuntimeException("Gemini API call failed with status: " + response.getStatusCode());
    }

    private JsonNode normalizeAuraJson(String aiText) throws Exception {
        String cleaned = aiText.replaceAll("```json", "").replaceAll("```", "").trim();
        return objectMapper.readTree(cleaned);
    }
}
