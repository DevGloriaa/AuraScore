package com.enyata.aurascore.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/interswitch")
@Slf4j
public class WebhookController {

    public static final ConcurrentHashMap<String, JsonNode> VERIFIED_PAYMENTS = new ConcurrentHashMap<>();

    @PostMapping("/webhook")
    public ResponseEntity<String> handlePaymentWebhook(@RequestBody JsonNode payload) {
        log.info("Received Interswitch Webhook: {}", payload.toString());

        String txnRef = payload.path("transactionReference").asText();
        String responseCode = payload.path("responseCode").asText();

        if ("00".equals(responseCode)) {
            log.info("Webhook confirmed payment success for Ref: {}", txnRef);
            VERIFIED_PAYMENTS.put(txnRef, payload);
        } else {
            log.warn("Webhook reported failed payment for Ref: {}", txnRef);
        }

        return ResponseEntity.ok("Webhook processed");
    }
}