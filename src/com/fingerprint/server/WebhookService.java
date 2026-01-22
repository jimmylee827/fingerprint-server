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
     * Get the webhook URL from environment (complete endpoint URL)
     */
    private String getWebhookUrl() {
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
        String webhookUrl = getWebhookUrl();
        
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            System.out.println("[WebhookService] No webhook URL configured, skipping notification");
            return;
        }

        // Build payload with detecttype wrapper
        WebhookPayloadWrapper wrapper = new WebhookPayloadWrapper();
        wrapper.detecttype = "VALID";
        wrapper.data = new WebhookPayload();
        wrapper.data.event = "fingerprint_detected";
        wrapper.data.userId = registration.getId();
        wrapper.data.name = registration.getName();
        wrapper.data.role = registration.getRole();
        wrapper.data.timestamp = Instant.now().toString();
        wrapper.data.score = matchScore;

        // Send asynchronously
        executor.submit(() -> sendWebhook(webhookUrl, wrapper));
    }

    /**
     * Send webhook synchronously (for internal use)
     */
    private void sendWebhook(String webhookUrl, WebhookPayloadWrapper wrapper) {
        try {
            System.out.println("[WebhookService] Sending webhook to: " + webhookUrl);
            System.out.println("[WebhookService] Payload: " + gson.toJson(wrapper));

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

            String jsonPayload = gson.toJson(wrapper);
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
        String webhookUrl = getWebhookUrl();
        
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            System.out.println("[WebhookService] No webhook URL configured, skipping unidentified notification");
            return;
        }

        // Build payload with detecttype wrapper
        WebhookPayloadWrapper wrapper = new WebhookPayloadWrapper();
        wrapper.detecttype = "UNIDENTIFIED";
        wrapper.data = new WebhookPayload();
        wrapper.data.event = "fingerprint_unidentified";
        wrapper.data.userId = null;
        wrapper.data.name = null;
        wrapper.data.role = null;
        wrapper.data.timestamp = Instant.now().toString();
        wrapper.data.score = 0;

        // Send asynchronously
        executor.submit(() -> sendWebhook(webhookUrl, wrapper));
    }

    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        executor.shutdown();
    }

    // ==================== Payload Classes ====================

    public static class WebhookPayloadWrapper {
        public String detecttype;
        public WebhookPayload data;
    }

    public static class WebhookPayload {
        public String event;
        public String userId;
        public String name;
        public String role;
        public String timestamp;
        public int score;
    }
}
