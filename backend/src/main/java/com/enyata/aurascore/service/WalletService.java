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
            @org.springframework.beans.factory.annotation.Value("${web3.contract.address:0x0000000000000000000000000000000000000000}") String contractAddress,
            @org.springframework.beans.factory.annotation.Value("${web3.chain.id:11155111}") long chainId
    ) {
        this.interswitchService = interswitchService;
        this.objectMapper = objectMapper;
        this.contractAddress = contractAddress;
        this.chainId = chainId;
    }

    public MintTransaction prepareMinting(String walletAddress, int score, String accountNumber, String bankCode) {

        ensureBankIdentityVerified(accountNumber, bankCode);


        if (!ETH_ADDRESS_PATTERN.matcher(walletAddress).matches()) {
            throw new IllegalArgumentException("Invalid Ethereum wallet address format");
        }
        if (score < 0) {
            throw new IllegalArgumentException("Score must be non-negative");
        }

        // 3. ENCODE BLOCKCHAIN DATA
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

    private void ensureBankIdentityVerified(String accountNumber, String bankCode) {
        try {
            String responseJson = interswitchService.verifyBankAccount(accountNumber, bankCode);
            JsonNode root = objectMapper.readTree(responseJson);

            // Interswitch Name Enquiry usually returns "00" for success
            String responseCode = root.path("responseCode").asText("");

            if (!"00".equals(responseCode) && !"SUCCESS".equalsIgnoreCase(responseCode)) {
                throw new IllegalStateException("Identity verification failed. Account not found or mismatched.");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Identity check failed before minting: " + ex.getMessage());
        }
    }

    private String normalizeWalletAddress(String address) {
        String normalized = address.startsWith("0x") || address.startsWith("0X") ? address.substring(2) : address;
        return "0x" + normalized.toLowerCase(Locale.ROOT);
    }

    private String first4BytesHex(String functionSignature) {
        byte[] digest = Hash.sha3(functionSignature.getBytes(StandardCharsets.UTF_8));
        return Numeric.toHexStringNoPrefix(Arrays.copyOfRange(digest, 0, 4));
    }

    private String leftPad64(String hexValue) {
        return "0".repeat(Math.max(0, 64 - hexValue.length())) + hexValue;
    }

    public record MintTransaction(
            long chainId, String to, String beneficiaryWallet, int score,
            String value, String gasLimit, String data, LocalDateTime preparedAt
    ) {}
}