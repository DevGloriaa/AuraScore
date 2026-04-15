package com.enyata.aurascore.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
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
            You are a bank loan underwriting assistant.
            Your task is to analyze raw bank transactions and return a risk-focused Aura Score JSON for loan officers.
            
            EVALUATION CRITERIA:
            1. Cash Flow Health: Weigh total inflows against total outflows.
            2. Transaction Velocity: Evaluate the frequency and consistency of account activity.
            3. Risk Profile: Identify erratic spending, zero balances, or high-risk transfers.
            
            Return ONLY a raw JSON object. Do NOT wrap it in ```json blocks. No markdown, no backticks, no extra text. Use this exact schema:
            {
              "auraScore": [Integer between 300 and 800. Be highly analytical and realistic based on the data provided],
              "persona": [Choose the best fit: 'The Diamond-Handed Builder', 'The Steady Fiat Maximalist', 'The Velocity Degenerate', 'The Liquid Phantom'],
              "loanEligibility": [Choose one: 'ELIGIBLE', 'HIGH_RISK', 'REJECTED'],
              "recommendation": [Write one flowing executive-summary paragraph in exactly 3-4 professional sentences for a loan officer. Do not use bullet points, line breaks, or key-value labels. CRITICAL: do not use key-value pairs like 'Capacity:' or 'Stability:'. Organically mention risk level, cash flow stability, and spending velocity in simple sentences. Use this exact style as a guide: "Due to highly volatile cash flow and inconsistent income patterns, this applicant presents a high credit risk. Money is rapidly transferred out upon arrival, resulting in minimal monthly surplus and a high burn rate. Because their outflows consistently meet or exceed inflows, they demonstrate weak repayment potential and lack the capacity to handle new loan obligations".],
              "topSpendingCategory": [Infer their highest spending category from the data, e.g., 'Transfers', 'Food & Dining', 'Subscriptions', 'Unknown'],
              "financialVelocity": [Choose one based on transaction frequency: 'High', 'Moderate', 'Low']
            }
            """;

    public GeminiService(
            ObjectMapper objectMapper,
            @Value("${GOOGLE_API_KEY}") String googleApiKey
    ) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();


        this.endpoint = "https://generativelanguage.googleapis.com" + "/v1beta/models/gemini-2.5-flash:generateContent?key=" + googleApiKey;

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

        // Fallback for empty accounts (Thin File)
        if (normalizedTransactions == null || normalizedTransactions.isMissingNode() || normalizedTransactions.isNull() || normalizedTransactions.isEmpty() || normalizedTransactions.toString().equals("[]")) {
            log.warn("Mono returned empty transaction data. Triggering Thin File fallback.");
            String fallback = """
                {
                  "auraScore": 0,
                  "persona": "Thin File",
                  "loanEligibility": "REJECTED",
                  "recommendation": "This applicant is currently high risk because the account history is too limited for confident underwriting. Cash flow stability cannot be verified due to sparse and inconsistent income activity. Spending velocity is difficult to benchmark because transaction volume is low. The inflow-to-outflow pattern is unclear, so repayment capacity cannot be confidently projected at this stage.",
                  "topSpendingCategory": "None",
                  "financialVelocity": "Low"
                }
                """;
            return objectMapper.readTree(fallback);
        }

        String payload = objectMapper.writeValueAsString(normalizedTransactions);
        String fullPrompt = SYSTEM_PROMPT + "\n\nTransaction Data:\n" + payload;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Format the request strictly to Google's Gemini API specs
        Map<String, Object> requestBodyMap = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", fullPrompt)
                        ))
                ),
                "generationConfig", Map.of(
                        "temperature", 0.0,
                        "topP", 0.0,
                        "topK", 1,
                        "candidateCount", 1
                )
        );
        String requestBody = objectMapper.writeValueAsString(requestBodyMap);

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        log.info("Sending transactions to Gemini AI...");
        Exception lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ResponseEntity<JsonNode> response = restTemplate.postForEntity(endpoint, request, JsonNode.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    JsonNode candidates = response.getBody().path("candidates");
                    JsonNode firstCandidate = candidates.isArray() && !candidates.isEmpty() ? candidates.get(0) : null;
                    if (firstCandidate == null || firstCandidate.isMissingNode() || firstCandidate.isNull()) {
                        throw new RuntimeException("Gemini response missing candidates");
                    }

                    JsonNode parts = firstCandidate.path("content").path("parts");
                    JsonNode firstPart = parts.isArray() && !parts.isEmpty() ? parts.get(0) : null;
                    if (firstPart == null || firstPart.isMissingNode() || firstPart.isNull()) {
                        throw new RuntimeException("Gemini response missing content parts");
                    }
                    String aiText = firstPart.path("text").asText();

                    log.info("Gemini Analysis Received successfully!");
                    return normalizeAuraJson(aiText);
                }

                throw new RuntimeException("Gemini API call failed with status: " + response.getStatusCode());
            } catch (RestClientResponseException ex) {
                lastException = ex;
                int status = ex.getRawStatusCode();
                boolean retryable = status == 429 || status == 503;

                if (!retryable || attempt == 3) {
                    throw ex;
                }

                log.warn("Gemini API busy, retrying in 2 seconds...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", interruptedException);
                }
            }
        }

        throw (lastException != null)
                ? lastException
                : new RuntimeException("Gemini API call failed after retries");
    }

    // Strips any accidental markdown that Gemini might return despite instructions
    private JsonNode normalizeAuraJson(String aiText) throws Exception {
        String cleaned = aiText.replaceAll("```json", "").replaceAll("```", "").trim();
        JsonNode parsed = objectMapper.readTree(cleaned);
        if (!parsed.isObject()) {
            return parsed;
        }

        ObjectNode normalized = (ObjectNode) parsed;
        normalized.put("recommendation", buildRecommendation(normalized));
        return normalized;
    }

    private String buildRecommendation(JsonNode auraJson) {
        int auraScore = auraJson.path("auraScore").asInt(0);
        String loanEligibility = auraJson.path("loanEligibility").asText("REJECTED");
        String category = auraJson.path("topSpendingCategory").asText("Unknown");
        String velocity = auraJson.path("financialVelocity").asText("Low");

        String riskSentence;
        String stabilitySentence;
        String capacityVelocitySentence;
        String ratioSentence;

        if (auraScore >= 700) {
            riskSentence = "This applicant presents as low risk for entry-level credit based on disciplined account behavior.";
            stabilitySentence = "Cash flow looks stable, with income appearing frequent and consistent across observed periods.";
            capacityVelocitySentence = "Spending velocity is controlled, and the profile shows recurring surplus after routine spending with activity concentrated in " + category + " at a " + velocity.toLowerCase() + " pace.";
            ratioSentence = "Overall, inflow generally outpaces outflow, which supports stronger debt-service potential.";
        } else if (auraScore >= 550) {
            riskSentence = "This applicant is medium risk and may be suitable for conditional credit with tighter controls.";
            stabilitySentence = "Cash flow is moderately stable, though income reliability varies between periods.";
            capacityVelocitySentence = "Spending velocity is uneven, and repayment capacity appears limited because surplus after spending is narrow, with major activity in " + category + " at a " + velocity.toLowerCase() + " pace.";
            ratioSentence = "Inflow and outflow remain closely matched, leaving only a thin margin for debt obligations.";
        } else {
            riskSentence = "This applicant is high risk due to volatile account behavior and weak repayment signals.";
            stabilitySentence = "Cash flow stability appears low, with inconsistent income patterns that are hard to project.";
            capacityVelocitySentence = "Spending velocity is high relative to available funds, and repayment capacity is constrained because little or no surplus remains after spending, with major activity in " + category + " at a " + velocity.toLowerCase() + " pace.";
            ratioSentence = "Outflow frequently matches or exceeds inflow, which increases default risk for new credit.";
        }

        if ("ELIGIBLE".equals(loanEligibility) && auraScore < 700) {
            riskSentence = "This applicant is currently better treated as conditional-approval risk and should carry tighter controls.";
        }
        if ("REJECTED".equals(loanEligibility) && auraScore >= 700) {
            riskSentence = "This case needs manual review because model strength and eligibility verdict are not aligned.";
        }

        return String.join(" ", riskSentence, stabilitySentence, capacityVelocitySentence, ratioSentence);
    }
}