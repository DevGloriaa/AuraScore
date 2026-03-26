package com.enyata.aurascore.controller;

import com.enyata.aurascore.service.InterswitchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/verify")
public class VerificationController {

    private final InterswitchService interswitchService;

    public VerificationController(InterswitchService interswitchService) {
        this.interswitchService = interswitchService;
    }

    @PostMapping("/identity")
    public ResponseEntity<String> verifyIdentity(@Valid @RequestBody VerifyIdentityRequest request) {
        return ResponseEntity.ok(interswitchService.verifyBankAccount(request.accountNumber(), request.bankCode()));
    }

    public record VerifyIdentityRequest(
            @NotBlank(message = "accountNumber is required") String accountNumber,
            @NotBlank(message = "bankCode is required") String bankCode
    ) {
    }
}

