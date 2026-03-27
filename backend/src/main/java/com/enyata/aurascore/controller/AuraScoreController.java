package com.enyata.aurascore.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import com.enyata.aurascore.service.GeminiService;
import com.enyata.aurascore.service.InterswitchService;
import com.enyata.aurascore.service.MonoService;
import com.enyata.aurascore.service.Web3Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
            System.out.println("EXECUTING PURE MONO -> GEMINI FLOW...");


            com.fasterxml.jackson.databind.node.ObjectNode paymentInfo = JsonNodeFactory.instance.objectNode();
            paymentInfo.put("ResponseDescription", "Approved Bypass");
            paymentInfo.put("Amount", "50000");
            paymentInfo.put("RetrievalReferenceNumber", "DEMO-REF");
            paymentInfo.put("CardType", "Bypass");

            System.out.println("Payment Verified (Bypassed). Pulling Mono Data...");
            String accountId = monoService.exchangeToken(request.code());
            JsonNode financialData = monoService.getTransactions(accountId);

            System.out.println("Analyzing Aura with Gemini...");
            JsonNode aiNode = geminiService.analyzeAura(financialData);
            int finalScore = aiNode.path("auraScore").asInt(0);


            System.out.println("Skipping Interswitch Lending Check (Bypassed)...");
            JsonNode lendingStatus = JsonNodeFactory.instance.objectNode().put("status", "UNAVAILABLE");

            System.out.println("Minting Soulbound Token to Sepolia...");
            String txHash = web3Service.mintSbtScore(request.walletAddress(), finalScore);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("customerReference", request.customerReference());

            Map<String, Object> paymentMap = new LinkedHashMap<>();
            paymentMap.put("status", "Approved");
            paymentMap.put("transactionReference", request.transactionReference());
            paymentMap.put("amountKobo", "50000");
            paymentMap.put("bankReference", "DEMO-REF");
            paymentMap.put("cardType", "N/A");

            response.put("payment", paymentMap);
            response.put("analysis", aiNode);
            response.put("interswitchLending", lendingStatus);
            response.put("blockchain", Map.of(
                    "transactionHash", txHash,
                    "blockchainReceipt", "https://sepolia.etherscan.io/tx/" + txHash
            ));

            System.out.println("FLOW COMPLETE. Sending 200 OK to Frontend.");
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Internal Processing Error: " + ex.getMessage()));
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