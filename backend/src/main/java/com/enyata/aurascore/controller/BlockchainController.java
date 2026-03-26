package com.enyata.aurascore.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import com.enyata.aurascore.service.Web3Service;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chain")
public class BlockchainController {

    private final Web3Service web3Service;

    public BlockchainController(Web3Service web3Service) {
        this.web3Service = web3Service;
    }

    @PostMapping("/mint")
    public ResponseEntity<Map<String, Object>> mint(@Valid @RequestBody MintRequest request) throws Exception {
        String txHash = web3Service.mintSbtScore(request.walletAddress(), request.auraScore());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("transactionHash", txHash);
        response.put("blockchainReceipt", "https://sepolia.etherscan.io/tx/" + txHash);
        return ResponseEntity.ok(response);
    }

    public record MintRequest(
            @Min(value = 0, message = "auraScore must be non-negative") int auraScore,
            @NotBlank(message = "walletAddress is required") String walletAddress
    ) {
    }
}

