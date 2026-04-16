package com.enyata.aurascore.controller;

import com.enyata.aurascore.model.ScoreRecord;
import com.enyata.aurascore.repository.ScoreRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import com.enyata.aurascore.service.GeminiService;
import com.enyata.aurascore.service.InterswitchService;
import com.enyata.aurascore.service.MonoService;
import com.enyata.aurascore.service.Web3Service;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/score")
public class AuraScoreController {

    private static final Logger logger = LoggerFactory.getLogger(AuraScoreController.class);

    private final MonoService monoService;
    private final GeminiService geminiService;
    private final Web3Service web3Service;
    private final InterswitchService interswitchService;
    private final ScoreRepository scoreRepository;

    public AuraScoreController(MonoService monoService, GeminiService geminiService, Web3Service web3Service, InterswitchService interswitchService, ScoreRepository scoreRepository) {
        this.monoService = monoService;
        this.geminiService = geminiService;
        this.web3Service = web3Service;
        this.interswitchService = interswitchService;
        this.scoreRepository = scoreRepository;
    }

    @PostMapping("/initiate-payment")
    public ResponseEntity<?> initiatePayment(@Valid @RequestBody InitiateRequest request) {
        Map<String, Object> result = interswitchService.initiatePayment(request.email());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/verify-payment")
    public ResponseEntity<?> verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        String expectedAmountKobo = "50000";
        JsonNode requeryResponse = interswitchService.validatePayment(request.txnRef(), expectedAmountKobo);

        // DEMO MODE: Update database to mark payment as PAID
        try {
            ScoreRecord existingRecord = scoreRepository.findByTransactionReference(request.txnRef()).orElse(null);
            if (existingRecord != null) {
                existingRecord.setPaymentStatus("PAID");
                existingRecord.setTransactionReference(request.txnRef());
                scoreRepository.save(existingRecord);
            }
        } catch (Exception ex) {
            // Log but don't fail the response if DB update fails
            logger.warn("Failed to update payment status in database for txnRef: {}", request.txnRef(), ex);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("txnRef", request.txnRef());
        response.put("amountKobo", expectedAmountKobo);
        response.put("verification", requeryResponse);
        response.put("status", "PAID");  // Add explicit status for frontend
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateScore(@Valid @RequestBody GenerateScoreRequest request) {
        try {
            String accountId = monoService.exchangeToken(request.code());
            JsonNode financialData = monoService.getTransactions(accountId);
            String financialFingerprint = sha256(financialData.toString());

            ScoreRecord cachedRecord = scoreRepository.findById(financialFingerprint).orElse(null);
            if (cachedRecord != null) {
                Map<String, Object> cachedResponse = new LinkedHashMap<>();
                cachedResponse.put("customerReference", request.customerReference());
                cachedResponse.put("fingerprint", financialFingerprint);
                cachedResponse.put("analysis", cachedRecord.getAiAnalysis());
                cachedResponse.put("cached", true);
                return ResponseEntity.ok(cachedResponse);
            }

            JsonNode aiNode = geminiService.analyzeAura(financialData);

            ScoreRecord scoreRecord = new ScoreRecord();
            scoreRecord.setId(financialFingerprint);
            scoreRecord.setCustomerReference(request.customerReference());
            scoreRecord.setAiAnalysis(aiNode);
            scoreRecord.setCreatedAt(LocalDateTime.now());
            scoreRepository.save(scoreRecord);

            int finalScore = aiNode.path("auraScore").asInt(0);

            String txHash = web3Service.mintSbtScore(request.walletAddress(), finalScore);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("customerReference", request.customerReference());

            Map<String, Object> paymentMap = new LinkedHashMap<>();
            paymentMap.put("status", "Approved");
            paymentMap.put("transactionReference", request.transactionReference());
            paymentMap.put("amountKobo", "50000");

            response.put("payment", paymentMap);
            response.put("analysis", aiNode);
            response.put("fingerprint", financialFingerprint);
            response.put("cached", false);
            response.put("blockchain", Map.of(
                    "transactionHash", txHash,
                    "blockchainReceipt", "https://sepolia.etherscan.io/tx/" + txHash
            ));

            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            logger.error("Error generating score", ex);
            return ResponseEntity.status(500).body(Map.of("error", ex.getMessage()));
        }
    }

    public record GenerateScoreRequest(
            @NotBlank(message = "Mono code is required") String code,
            @NotBlank(message = "Wallet address is required") String walletAddress,
            @NotBlank(message = "customerReference is required") String customerReference,
            @NotBlank(message = "transactionReference is required") String transactionReference,
            @NotNull(message = "identityData is required") JsonNode identityData
    ) {}

    public record InitiateRequest(
            @NotBlank(message = "email is required") String email
    ) {}

    public record VerifyPaymentRequest(
            @NotBlank(message = "txnRef is required")
            @JsonProperty("txnRef")
            String txnRef
    ) {}

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}