package com.fingerprint.server;

import com.fingerprint.model.Registration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WebhookService - Sends POST requests when fingerprints are detected
 */
public class WebhookService {
    private final Gson gson;
    private final ExecutorService executor;

    public WebhookService() {
        this.gson = new GsonBuilder().create();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Get the webhook base URL from environment
     */
    private String getWebhookBaseUrl() {
        return EnvLoader.get("WEBHOOK_URL", "");
    }

    /**
     * Get the external webhook bearer token from environment
     */
    private String getWebhookExternalKey() {
        return EnvLoader.get("WEBHOOK_EXTERNAL_KEY", "");
    }

    /**
     * Send webhook notification asynchronously when a fingerprint is detected
     */
    public void notifyFingerprintDetected(Registration registration, int matchScore) {
        String baseUrl = getWebhookBaseUrl();
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            System.out.println("[WebhookService] No webhook URL configured, skipping notification");
            return;
        }

        // Build full URL for identified fingerprints
        String webhookUrl = baseUrl.endsWith("/") 
            ? baseUrl + "fingerprint-detected" 
            : baseUrl + "/fingerprint-detected";

        // Build payload
        WebhookPayload payload = new WebhookPayload();
        payload.event = "fingerprint_detected";
        payload.userId = registration.getId();
        payload.name = registration.getName();
        payload.role = registration.getRole();
        payload.timestamp = Instant.now().toString();
        payload.score = matchScore;

        // Send asynchronously
        executor.submit(() -> sendWebhook(webhookUrl, payload));
    }

    /**
     * Send webhook synchronously (for internal use)
     */
    private void sendWebhook(String webhookUrl, WebhookPayload payload) {
        try {
            System.out.println("[WebhookService] Sending webhook to: " + webhookUrl);
            System.out.println("[WebhookService] Payload: " + gson.toJson(payload));

            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            
            // Add Bearer token if configured
            String externalKey = getWebhookExternalKey();
            if (externalKey != null && !externalKey.isEmpty() && !externalKey.equals("your-webhook-bearer-token-here")) {
                conn.setRequestProperty("Authorization", "Bearer " + externalKey);
                System.out.println("[WebhookService] Added Bearer token to request");
            }
            
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String jsonPayload = gson.toJson(payload);
            byte[] payloadBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payloadBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            System.out.println("[WebhookService] Response code: " + responseCode);

            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("[WebhookService] Webhook sent successfully");
                LogService.logWebhook("POST", webhookUrl, responseCode, "OK");
            } else {
                System.err.println("[WebhookService] Webhook failed with code: " + responseCode);
                LogService.logWebhook("POST", webhookUrl, responseCode, "FAILED");
            }

            conn.disconnect();

        } catch (Exception e) {
            System.err.println("[WebhookService] Failed to send webhook: " + e.getMessage());
            LogService.logWebhookError("POST", webhookUrl, e.getMessage());
            // Don't throw - webhook failures shouldn't break the main application
        }
    }

    /**
     * Send webhook notification asynchronously when an unidentified fingerprint is detected
     */
    public void notifyFingerprintUnidentified() {
        String baseUrl = getWebhookBaseUrl();
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            System.out.println("[WebhookService] No webhook URL configured, skipping unidentified notification");
            return;
        }

        // Build full URL for unidentified fingerprints
        String webhookUrl = baseUrl.endsWith("/") 
            ? baseUrl + "fingerprint-unidentified" 
            : baseUrl + "/fingerprint-unidentified";

        // Build payload
        WebhookPayload payload = new WebhookPayload();
        payload.event = "fingerprint_unidentified";
        payload.userId = null;
        payload.name = null;
        payload.role = null;
        payload.timestamp = Instant.now().toString();
        payload.score = 0;

        // Send asynchronously
        executor.submit(() -> sendWebhook(webhookUrl, payload));
    }

    /**
     * Test webhook connectivity
     */
    public WebhookTestResult testWebhook() {
        String baseUrl = getWebhookBaseUrl();
        
        if (baseUrl == null || baseUrl.isEmpty()) {
            return new WebhookTestResult(false, "No webhook URL configured (WEBHOOK_URL in .env)");
        }

        // Test the fingerprint-detected endpoint
        String webhookUrl = baseUrl.endsWith("/") 
            ? baseUrl + "fingerprint-detected" 
            : baseUrl + "/fingerprint-detected";

        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            
            // Add Bearer token if configured
            String externalKey = getWebhookExternalKey();
            if (externalKey != null && !externalKey.isEmpty() && !externalKey.equals("your-webhook-bearer-token-here")) {
                conn.setRequestProperty("Authorization", "Bearer " + externalKey);
            }
            
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            // Send test payload
            WebhookPayload testPayload = new WebhookPayload();
            testPayload.event = "webhook_test";
            testPayload.userId = "test";
            testPayload.name = "Test User";
            testPayload.role = "Test";
            testPayload.timestamp = Instant.now().toString();
            testPayload.score = 0;

            String jsonPayload = gson.toJson(testPayload);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                return new WebhookTestResult(true, "Webhook test successful, response code: " + responseCode);
            } else {
                return new WebhookTestResult(false, "Webhook returned error code: " + responseCode);
            }

        } catch (Exception e) {
            return new WebhookTestResult(false, "Failed to connect: " + e.getMessage());
        }
    }

    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executor.shutdown();
    }

    // ==================== Payload Classes ====================

    public static class WebhookPayload {
        public String event;
        public String userId;
        public String name;
        public String role;
        public String timestamp;
        public int score;
    }

    public static class WebhookTestResult {
        public boolean success;
        public String message;

        public WebhookTestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
