package com.enyata.aurascore.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;
import org.springframework.stereotype.Service;

@Service
public class WalletService {

    private static final Pattern ETH_ADDRESS_PATTERN = Pattern.compile("^(0x)?[0-9a-fA-F]{40}$");

    private final InterswitchService interswitchService;
    private final ObjectMapper objectMapper;

    private final String contractAddress;
    private final long chainId;

    public WalletService(
            InterswitchService interswitchService,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${web3.contract.address:${AURA_SCORE_CONTRACT_ADDRESS:0x0000000000000000000000000000000000000000}}")
            String contractAddress,
            @org.springframework.beans.factory.annotation.Value("${web3.chain.id:${AURA_CHAIN_ID:11155111}}")
            long chainId
    ) {
        this.interswitchService = interswitchService;
        this.objectMapper = objectMapper;
        this.contractAddress = contractAddress;
        this.chainId = chainId;
    }

    public String generateHiddenWalletAddress(String phoneNumber) {
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        ensureKycSuccess(normalizedPhone);

        byte[] hash = Hash.sha3(normalizedPhone.getBytes(StandardCharsets.UTF_8));
        byte[] addressBytes = Arrays.copyOfRange(hash, hash.length - 20, hash.length);

        String addressHex = Numeric.toHexStringNoPrefix(addressBytes);
        return "0x" + leftPad40(addressHex);
    }

    public MintTransaction mintAuraScore(String walletAddress, int score) {
        if (!ETH_ADDRESS_PATTERN.matcher(walletAddress).matches()) {
            throw new IllegalArgumentException("Invalid Ethereum wallet address format");
        }
        if (score < 0) {
            throw new IllegalArgumentException("Score must be non-negative");
        }

        String normalizedWallet = normalizeWalletAddress(walletAddress);
        String selector = first4BytesHex("mintAuraScore(address,uint256)");
        String encodedAddress = leftPad64(normalizedWallet.substring(2));
        String encodedScore = leftPad64(BigInteger.valueOf(score).toString(16));
        String data = "0x" + selector + encodedAddress + encodedScore;

        return new MintTransaction(
                chainId,
                normalizeWalletAddress(contractAddress),
                normalizedWallet,
                score,
                "0x0",
                "0x249f0",
                data,
                LocalDateTime.now()
        );
    }

    private void ensureKycSuccess(String phoneNumber) {
        try {
            String kycJson = interswitchService.verifyKYC(phoneNumber);
            JsonNode root = objectMapper.readTree(kycJson);
            String status = root.path("verificationStatus").asText(root.path("status").asText(""));

            if (!"SUCCESS".equalsIgnoreCase(status)) {
                throw new IllegalStateException("KYC verification did not return SUCCESS");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify KYC before wallet generation", ex);
        }
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber is required");
        }
        return phoneNumber.replaceAll("\\s+", "");
    }

    private String normalizeWalletAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("walletAddress is required");
        }
        String normalized = address.startsWith("0x") || address.startsWith("0X")
                ? address.substring(2)
                : address;
        if (normalized.length() != 40 || !normalized.matches("[0-9a-fA-F]{40}")) {
            throw new IllegalArgumentException("Invalid Ethereum wallet address format");
        }
        return "0x" + normalized.toLowerCase(Locale.ROOT);
    }

    private String first4BytesHex(String functionSignature) {
        byte[] digest = keccak256(functionSignature.getBytes(StandardCharsets.UTF_8));
        return Numeric.toHexStringNoPrefix(Arrays.copyOfRange(digest, 0, 4));
    }

    private String leftPad40(String hexValue) {
        String clean = hexValue == null ? "" : hexValue;
        if (clean.length() >= 40) {
            return clean.substring(clean.length() - 40);
        }
        return "0".repeat(40 - clean.length()) + clean;
    }

    private String leftPad64(String hexValue) {
        String clean = hexValue == null ? "" : hexValue;
        if (clean.length() > 64) {
            throw new IllegalArgumentException("Encoded value exceeds 32 bytes");
        }
        return "0".repeat(64 - clean.length()) + clean;
    }

    private byte[] keccak256(byte[] input) {
        return Hash.sha3(input);
    }

    public record MintTransaction(
            long chainId,
            String to,
            String beneficiaryWallet,
            int score,
            String value,
            String gasLimit,
            String data,
            LocalDateTime preparedAt
    ) {
    }
}

