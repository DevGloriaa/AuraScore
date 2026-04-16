package com.enyata.aurascore.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@Service
public class InterswitchService {

    private static final Logger log = LoggerFactory.getLogger(InterswitchService.class);
    private static final ZoneId WAT_ZONE_ID = ZoneId.of("Africa/Lagos");
    private static final DateTimeFormatter WAT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ISW_MERCHANT_CODE:MX6072}")
    private String merchantCode;

    @Value("${ISW_PAY_ITEM_ID:9405967}")
    private String payItemId;

    @Value("${ISW_SECRET_KEY:ajkdpGiF6PHVrwK}")
    private String secretKey;

    @Value("${interswitch.client.id}")
    private String clientId;

    @Value("${interswitch.client.secret}")
    private String clientSecret;

    @Value("${interswitch.identity.api.url:https://api-marketplace-routing.k8.isw.la}")
    private String identityBaseUrl;

    @Value("${interswitch.token.url:https://qa.interswitchng.com/passport/oauth/token}")
    private String tokenUrl;

    private volatile String cachedToken;
    private volatile long tokenExpiresAtEpochSeconds;

    public InterswitchService(ObjectMapper objectMapper, @Qualifier("interswitchRestTemplate") RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public Map<String, Object> initiatePayment(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "email is required");
        }

        String txnRef = "AURA-" + Instant.now().getEpochSecond();
        String amount = "50000";
        String currency = "566";
        String siteRedirectUrl = "http://localhost:3000/";

        String rawString = txnRef + merchantCode + payItemId + amount + siteRedirectUrl + secretKey;
        String hash = generateSha512(rawString);

        return Map.of(
                "txnRef", txnRef,
                "amount", amount,
                "hash", hash,
                "merchantCode", merchantCode,
                "payItemId", payItemId,
                "currency", currency,
                "site_redirect_url", siteRedirectUrl
        );
    }

    private String generateSha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "SHA-512 Algorithm not found");
        }
    }

    public JsonNode validatePayment(String transactionReference, String expectedAmountKobo) {
        if (transactionReference == null || transactionReference.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "transactionReference is required");
        }
        if (expectedAmountKobo == null || expectedAmountKobo.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "amount is required");
        }

        try {
            String normalizedReference = transactionReference.trim();
            String normalizedAmount = expectedAmountKobo.trim();

            String url = UriComponentsBuilder.fromUriString("https://qa.interswitchng.com/collections/api/v1/gettransaction.json")
                    .queryParam("merchantcode", merchantCode.trim())
                    .queryParam("transactionreference", normalizedReference)
                    .queryParam("amount", normalizedAmount)
                    .build()
                    .toUriString();

            HttpHeaders headers = bearerJsonHeaders(getAccessToken());
            logOutboundRequest("GET", url, headers);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = exchangeWithTimeout(url, HttpMethod.GET, request, JsonNode.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            try {
                return objectMapper.readTree(ex.getResponseBodyAsString());
            } catch (Exception parseEx) {
                throw new ResponseStatusException(BAD_GATEWAY, "Payment verification API failed", ex);
            }
        } catch (TimeoutException ex) {
            logTimeout("validatePayment", ex);
            throw new ResponseStatusException(BAD_GATEWAY, "Payment verification API timeout", ex);
        } catch (ResourceAccessException ex) {
            logTimeout("validatePayment", ex);
            throw new ResponseStatusException(BAD_GATEWAY, "Payment verification API timeout/unreachable", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Payment verification API unavailable", ex);
        }
    }

    public String verifyBankAccount(String accountNumber, String bankCode) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "accountNumber is required");
        }
        if (bankCode == null || bankCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "bankCode is required");
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(buildIdentityUrl("/api/v1/nameenquiry/banks/accounts/names"))
                    .queryParam("accountNumber", accountNumber.trim())
                    .queryParam("bankCode", bankCode.trim())
                    .build()
                    .toUriString();

            HttpHeaders headers = bearerJsonHeaders(getAccessToken());
            logOutboundRequest("GET", url, headers);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = exchangeWithTimeout(url, HttpMethod.GET, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "Bank verification failed");
            }
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            throw mapClientError(ex, "Bank verification failed");
        } catch (TimeoutException ex) {
            logTimeout("verifyBankAccount", ex);
            throw new ResponseStatusException(BAD_GATEWAY, "Bank verification API timeout", ex);
        } catch (ResourceAccessException ex) {
            logTimeout("verifyBankAccount", ex);
            throw new ResponseStatusException(BAD_GATEWAY, "Bank verification API timeout/unreachable", ex);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Bank verification API unavailable", ex);
        }
    }

    public JsonNode checkLoanEligibility(String customerId, String requestedAmountKobo) {
        try {
            String url = "https://qa.interswitchng.com/paymentgateway/api/v1/lending/eligibility";
            Map<String, Object> body = Map.of(
                    "amount", requestedAmountKobo,
                    "customerId", customerId,
                    "entityCode", "PBL"
            );
            HttpHeaders headers = bearerJsonHeaders(getAccessToken());
            logOutboundRequest("POST", url, headers);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> response = postForEntityWithTimeout(url, entity, JsonNode.class);
            return response.getBody();
        } catch (HttpClientErrorException ex) {
            try {
                return objectMapper.readTree(ex.getResponseBodyAsString());
            } catch (Exception parseEx) {
                throw new ResponseStatusException(BAD_GATEWAY, "Lending eligibility API failed", ex);
            }
        } catch (TimeoutException ex) {
            logTimeout("checkLoanEligibility", ex);
            throw new ResponseStatusException(BAD_GATEWAY, "Lending eligibility API timeout", ex);
        } catch (ResourceAccessException ex) {
            logTimeout("checkLoanEligibility", ex);
            throw new ResponseStatusException(BAD_GATEWAY, "Lending eligibility API timeout/unreachable", ex);
        }
    }

    private synchronized String getAccessToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && !cachedToken.isBlank() && now < (tokenExpiresAtEpochSeconds - 60)) {
            return cachedToken;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Accept", "application/json");
            headers.set("User-Agent", BROWSER_USER_AGENT);
            headers.set("Authorization", "Basic " + base64(clientId.trim() + ":" + clientSecret.trim()));
            headers.set("Timestamp", watTimestamp());

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            String resolvedTokenUrl = resolveTokenUrl();
            logOutboundRequest("POST", resolvedTokenUrl, headers);
            ResponseEntity<String> response = postForEntityWithTimeout(resolvedTokenUrl, requestEntity, String.class);

            JsonNode tokenJson = objectMapper.readTree(response.getBody());
            String accessToken = tokenJson.path("access_token").asText("");
            long expiresIn = tokenJson.path("expires_in").asLong(300L);

            cachedToken = accessToken;
            tokenExpiresAtEpochSeconds = Instant.now().getEpochSecond() + Math.max(1L, expiresIn);
            return cachedToken;
        } catch (TimeoutException ex) {
            logTimeout("getAccessToken", ex);
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch Auth timeout", ex);
        } catch (ResourceAccessException ex) {
            logTimeout("getAccessToken", ex);
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch Auth timeout/unreachable", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Interswitch Auth Failed", ex);
        }
    }

    private HttpHeaders bearerJsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", BROWSER_USER_AGENT);
        headers.setBearerAuth(token);
        headers.set("TerminalID", "7000000001");
        headers.set("Timestamp", watTimestamp());
        return headers;
    }

    private String watTimestamp() {
        return ZonedDateTime.now(WAT_ZONE_ID).format(WAT_TIMESTAMP_FORMATTER);
    }

    private void logOutboundRequest(String method, String url, HttpHeaders headers) {
        System.out.println("[Interswitch] Outbound Request => method=" + method + ", url=" + url);
        System.out.println("[Interswitch] Outbound Headers => " + maskHeaders(headers));
        log.info("[Interswitch] Outbound Request => method={}, url={}", method, url);
        log.info("[Interswitch] Outbound Headers => {}", maskHeaders(headers));
    }

    private Map<String, String> maskHeaders(HttpHeaders headers) {
        Map<String, String> masked = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String value = String.join(",", values);
            String lowered = name.toLowerCase();
            boolean sensitive = lowered.contains("authorization")
                    || lowered.contains("secret")
                    || lowered.contains("token")
                    || lowered.contains("key");
            masked.put(name, sensitive ? "***MASKED***" : value);
        });
        return masked;
    }

    private <T> ResponseEntity<T> exchangeWithTimeout(String url, HttpMethod method, HttpEntity<?> entity, Class<T> responseType) throws TimeoutException {
        try {
            return restTemplate.exchange(url, method, entity, responseType);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw new TimeoutException(ex.getMessage());
            }
            throw ex;
        }
    }

    private <T> ResponseEntity<T> postForEntityWithTimeout(String url, HttpEntity<?> entity, Class<T> responseType) throws TimeoutException {
        try {
            return restTemplate.postForEntity(url, entity, responseType);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw new TimeoutException(ex.getMessage());
            }
            throw ex;
        }
    }

    private boolean isTimeout(ResourceAccessException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SocketTimeoutException) {
            return true;
        }
        String message = cause != null ? cause.getMessage() : ex.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }

    private void logTimeout(String operation, ResourceAccessException ex) {
        String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        System.err.println("[Interswitch][Timeout] Operation=" + operation + " failed: " + detail);
        log.error("[Interswitch][Timeout] Operation={} failed: {}", operation, detail, ex);
    }

    private void logTimeout(String operation, TimeoutException ex) {
        String detail = ex.getMessage() != null ? ex.getMessage() : "Operation timed out";
        System.err.println("[Interswitch][Timeout] Operation=" + operation + " timed out: " + detail);
        log.error("[Interswitch][Timeout] Operation={} timed out: {}", operation, detail, ex);
    }

    private ResponseStatusException mapClientError(HttpClientErrorException ex, String message) {
        if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            return new ResponseStatusException(BAD_GATEWAY, message + ": authorization failed", ex);
        }
        return new ResponseStatusException(BAD_REQUEST, message, ex);
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String buildIdentityUrl(String path) {
        String normalizedBase = (identityBaseUrl == null || identityBaseUrl.isBlank()) ? "https://api-marketplace-routing.k8.isw.la" : identityBaseUrl.trim();
        if (normalizedBase.endsWith("/")) normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        return normalizedBase + path;
    }

    private String resolveTokenUrl() {
        if (tokenUrl != null && !tokenUrl.isBlank()) return tokenUrl.trim();
        return "https://qa.interswitchng.com/passport/oauth/token";
    }
}