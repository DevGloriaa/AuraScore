package com.enyata.aurascore.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.enyata.aurascore.service.GeminiService;
import com.enyata.aurascore.service.MonoService;
import com.enyata.aurascore.service.Web3Service;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/score")
public class AuraScoreController {

    private final MonoService monoService;
    private final GeminiService geminiService;
    private final Web3Service web3Service;

    public AuraScoreController(MonoService monoService, GeminiService geminiService, Web3Service web3Service) {
        this.monoService = monoService;
        this.geminiService = geminiService;
        this.web3Service = web3Service;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateScore(@Valid @RequestBody GenerateScoreRequest request) {
        try {
            System.out.println("INITIATING ONE-CLICK AURA SCORE GENERATION AND MINTING");

            System.out.println("Exchanging Mono code for Account ID...");
            String accountId = monoService.exchangeToken(request.code());

            System.out.println("Fetching transactions for Account: " + accountId);
            JsonNode financialData = monoService.getTransactions(accountId);

            System.out.println("Handing transactions to Gemini AI...");
            JsonNode aiNode = geminiService.analyzeAura(financialData);
            int finalScore = aiNode.path("auraScore").asInt();

            System.out.println("Minting score to blockchain for wallet: " + request.walletAddress());
            String txHash = web3Service.mintSbtScore(request.walletAddress(), finalScore);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("customerReference", request.customerReference());
            response.put("identityData", request.identityData());
            response.put("analysis", aiNode);
            response.put("blockchain", Map.of(
                    "walletAddress", request.walletAddress(),
                    "transactionHash", txHash,
                    "blockchainReceipt", "https://sepolia.etherscan.io/tx/" + txHash
            ));

            System.out.println("--- AURA SCORE GENERATED AND MINTED SUCCESSFULLY ---");
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            ex.printStackTrace();
            Map<String, String> errorBody = new LinkedHashMap<>();
            errorBody.put("error", ex.getClass().getSimpleName());
            errorBody.put("details", ex.getMessage());
            return ResponseEntity.status(500).body(errorBody);
        }
    }

    public record GenerateScoreRequest(
            @NotBlank(message = "Mono code is required") String code,
            @NotBlank(message = "Wallet address is required") String walletAddress,
            @NotBlank(message = "customerReference is required") String customerReference,
            @NotNull(message = "identityData is required") JsonNode identityData
    ) {}
}