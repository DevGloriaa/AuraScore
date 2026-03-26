package com.enyata.aurascore.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import com.enyata.aurascore.service.GeminiService;
import com.enyata.aurascore.service.InterswitchService;
import com.enyata.aurascore.service.MonoService;
import com.enyata.aurascore.service.Web3Service;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/score")
public class AuraScoreController {

    private final MonoService monoService;
    private final GeminiService geminiService;
    private final Web3Service web3Service;
    private final InterswitchService interswitchService;

    public AuraScoreController(MonoService monoService, GeminiService geminiService, Web3Service web3Service, InterswitchService interswitchService) {
        this.monoService = monoService;
        this.geminiService = geminiService;
        this.web3Service = web3Service;
        this.interswitchService = interswitchService;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateScore(@Valid @RequestBody GenerateScoreRequest request) {
        try {
            System.out.println("🚀 EXECUTING LIVE VERIFIED FLOW FOR: " + request.transactionReference());

            JsonNode paymentInfo = interswitchService.validatePayment(request.transactionReference(), "50000");

            String responseCode = paymentInfo.path("ResponseCode").asText("");
            if (!"00".equals(responseCode) && !"000".equals(responseCode)) {
                return ResponseEntity.status(402).body(Map.of(
                        "error", "PaymentInvalid",
                        "interswitchResponse", paymentInfo
                ));
            }

            String accountId = monoService.exchangeToken(request.code());
            JsonNode financialData = monoService.getTransactions(accountId);
            JsonNode aiNode = geminiService.analyzeAura(financialData);
            int finalScore = aiNode.path("auraScore").asInt(0);

            JsonNode lendingStatus = interswitchService.checkLoanEligibility(request.customerReference(), "5000000");
            String txHash = web3Service.mintSbtScore(request.walletAddress(), finalScore);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("customerReference", request.customerReference());

            Map<String, Object> paymentMap = new LinkedHashMap<>();
            paymentMap.put("status", paymentInfo.path("ResponseDescription").asText());
            paymentMap.put("transactionReference", paymentInfo.path("TransactionReference").asText());
            paymentMap.put("amountKobo", paymentInfo.path("Amount").asText());
            paymentMap.put("bankReference", paymentInfo.path("RetrievalReferenceNumber").asText());
            paymentMap.put("cardType", paymentInfo.path("CardType").asText());

            response.put("payment", paymentMap);
            response.put("analysis", aiNode);
            response.put("interswitchLending", lendingStatus);
            response.put("blockchain", Map.of(
                    "transactionHash", txHash,
                    "blockchainReceipt", "https://sepolia.etherscan.io/tx/" + txHash
            ));

            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            ex.printStackTrace();
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
}