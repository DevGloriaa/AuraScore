package com.enyata.aurascore.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import jakarta.annotation.PostConstruct;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

@Service
public class Web3Service {

    private final Web3j web3j;
    private final Credentials credentials;
    private final String contractAddress;
    private final RawTransactionManager txManager;

    public Web3Service(
            @Value("${BLOCKCHAIN_RPC_URL:https://ethereum-sepolia-rpc.publicnode.com}") String rpcUrl,
            @Value("${BLOCKCHAIN_PRIVATE_KEY}") String privateKey,
            @Value("${AURA_SCORE_CONTRACT_ADDRESS}") String contractAddress) {


        this.contractAddress = contractAddress.trim();
        String safePrivateKey = privateKey.trim();

        if (!safePrivateKey.startsWith("0x")) {
            safePrivateKey = "0x" + safePrivateKey;
        }


        this.web3j = Web3j.build(new HttpService(rpcUrl.trim()));
        this.credentials = Credentials.create(safePrivateKey);


        this.txManager = new RawTransactionManager(this.web3j, this.credentials, 11155111);
    }

    @PostConstruct
    public void testConnection() {
        try {
            System.out.println("🔗 Testing Blockchain Connection...");
            String version = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            System.out.println("✅ Connected to RPC: " + version);
            System.out.println("👛 Wallet Address Loaded: " + credentials.getAddress());
        } catch (Exception e) {
            System.err.println("❌ CRITICAL: Could not connect to Blockchain RPC.");
            e.printStackTrace();
        }
    }

    public String mintSbtScore(String userWalletAddress, int score) throws Exception {
        System.out.println("⏳ Preparing transaction for " + userWalletAddress + " with score: " + score);

        Function function = new Function(
                "mintAuraScore",
                Arrays.asList(new Address(userWalletAddress.trim()), new Uint256(BigInteger.valueOf(score))),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);

        EthSendTransaction transactionResponse = txManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                this.contractAddress,
                encodedFunction,
                BigInteger.ZERO
        );

        if (transactionResponse.hasError()) {
            throw new RuntimeException("Blockchain Error: " + transactionResponse.getError().getMessage());
        }

        String txHash = transactionResponse.getTransactionHash();
        System.out.println("🚀 Transaction Sent! Hash: " + txHash);

        return txHash;
    }
}