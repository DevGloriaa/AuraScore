package com.enyata.aurascore.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import com.enyata.aurascore.model.UserScore;
import com.enyata.aurascore.repository.UserScoreRepository;
import com.enyata.aurascore.service.GeminiService;
import com.enyata.aurascore.service.InterswitchService;
import com.enyata.aurascore.service.WalletService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/score")
public class AuraScoreController {

    private final GeminiService geminiService;
    private final InterswitchService interswitchService;
    private final WalletService walletService;
    private final UserScoreRepository userScoreRepository;
    private final ObjectMapper objectMapper;

    public AuraScoreController(
            GeminiService geminiService,
            InterswitchService interswitchService,
            WalletService walletService,
            UserScoreRepository userScoreRepository,
            ObjectMapper objectMapper
    ) {
        this.geminiService = geminiService;
        this.interswitchService = interswitchService;
        this.walletService = walletService;
        this.userScoreRepository = userScoreRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/calculate")
    public ResponseEntity<?> calculateScore(@Valid @RequestBody CalculateScoreRequest request) {
        try {
            String kycJson = interswitchService.verifyKYC(request.phoneNumber());
            JsonNode kycNode = objectMapper.readTree(kycJson);
            String kycStatus = kycNode.path("verificationStatus").asText("");
            if (!"SUCCESS".equalsIgnoreCase(kycStatus)) {
                throw new IllegalStateException("KYC verification failed for provided phone number");
            }

            String transactionHistoryJson = interswitchService.getTransactionHistory(request.phoneNumber());
            String aiJson = geminiService.analyze(transactionHistoryJson);

            int score = 500;
            String insights = "Mock: external AI unavailable, fallback score applied.";

            JsonNode aiNode = objectMapper.readTree(aiJson);
            score = aiNode.path("score").asInt(score);
            insights = aiNode.path("insights").asText(insights);

            String walletAddress = walletService.generateHiddenWalletAddress(request.phoneNumber());

            UserScore userScore = new UserScore(
                    null,
                    request.phoneNumber(),
                    score,
                    insights,
                    walletAddress,
                    LocalDateTime.now()
            );

            UserScore saved = userScoreRepository.save(userScore);
            return ResponseEntity.ok(saved);
        } catch (Exception ex) {
            Map<String, String> errorBody = new LinkedHashMap<>();
            errorBody.put("error", ex.getClass().getSimpleName());
            errorBody.put("details", ex.getMessage() != null ? ex.getMessage() : "Unexpected error occurred");
            return ResponseEntity.status(500).body(errorBody);
        }
    }

    public record CalculateScoreRequest(
            @NotBlank(message = "phoneNumber is required") String phoneNumber
    ) {
    }
}

