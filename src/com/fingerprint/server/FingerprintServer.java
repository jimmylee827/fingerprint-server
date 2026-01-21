package com.fingerprint.server;

import com.fingerprint.model.Config;
import com.fingerprint.model.ExportData;
import com.fingerprint.model.Registration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static spark.Spark.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * FingerprintServer - REST API server for fingerprint management
 */
public class FingerprintServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_ENROLLMENT_TIMEOUT = 30; // seconds

    private final StorageService storageService;
    private final WebhookService webhookService;
    private final FingerprintService fingerprintService;
    private final Gson gson;

    public FingerprintServer() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.storageService = new StorageService();
        this.webhookService = new WebhookService();
        this.fingerprintService = FingerprintService.getInstance();
    }

    public void start() {
        // Load environment variables first
        EnvLoader.load();
        
        System.out.println("============================================================");
        System.out.println("  Fingerprint REST API Server - Starting");
        System.out.println("============================================================");

        // Initialize fingerprint service
        if (!fingerprintService.initialize(storageService, webhookService)) {
            System.err.println("Failed to initialize fingerprint service!");
            System.err.println("Please ensure:");
            System.err.println("  1. Fingerprint reader is connected");
            System.err.println("  2. ZKTeco drivers are installed");
            System.err.println("  3. No other application is using the device");
            return;
        }

        // Configure Spark
        int port = storageService.getServerPort();
        if (port <= 0) port = DEFAULT_PORT;
        port(port);

        // Enable CORS
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        options("/*", (request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            return "OK";
        });

        // JSON content type
        before((request, response) -> response.type("application/json"));

        // Authentication filter
        before("/api/*", (request, response) -> {
            // Skip OPTIONS requests (CORS preflight)
            if (request.requestMethod().equals("OPTIONS")) {
                return;
            }
            
            String internalKey = EnvLoader.get("INTERNAL_KEY");
            if (internalKey == null || internalKey.isEmpty() || internalKey.equals("your-internal-api-key-here")) {
                // No auth configured, allow all
                return;
            }
            
            String authHeader = request.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                halt(401, "{\"error\": \"Missing or invalid Authorization header\"}");
                return;
            }
            
            String providedKey = authHeader.substring(7); // Remove "Bearer "
            if (!providedKey.equals(internalKey)) {
                halt(403, "{\"error\": \"Invalid API key\"}");
            }
        });

        // API request logging (after filter, logs successful requests)
        afterAfter("/api/*", (request, response) -> {
            // Skip OPTIONS requests
            if (request.requestMethod().equals("OPTIONS")) {
                return;
            }
            LogService.logApiRequest(
                request.requestMethod(),
                request.pathInfo(),
                response.status()
            );
        });

        // ==================== Routes ====================

        // Status endpoint
        get("/api/status", (req, res) -> {
            JsonObject status = new JsonObject();
            status.addProperty("initialized", fingerprintService.isInitialized());
            status.addProperty("identificationRunning", fingerprintService.isIdentificationRunning());
            status.addProperty("enrollmentInProgress", fingerprintService.isEnrollmentInProgress());
            status.addProperty("registeredCount", fingerprintService.getRegisteredCount());
            status.addProperty("webhookUrl", EnvLoader.get("WEBHOOK_URL", ""));
            return gson.toJson(status);
        });

        // Register a new fingerprint
        post("/api/register", (req, res) -> {
            JsonObject body = JsonParser.parseString(req.body()).getAsJsonObject();
            
            String name = getJsonString(body, "name");
            String role = getJsonString(body, "role");
            int timeout = body.has("timeout") ? body.get("timeout").getAsInt() : DEFAULT_ENROLLMENT_TIMEOUT;

            if (name == null || name.isEmpty()) {
                res.status(400);
                return errorJson("Name is required");
            }
            if (role == null || role.isEmpty()) {
                role = "User"; // Default role
            }

            // Validate role
            if (!role.equals("Admin") && !role.equals("User")) {
                res.status(400);
                return errorJson("Role must be 'Admin' or 'User'");
            }

            // Check for duplicate name (case-insensitive)
            if (storageService.isNameExists(name)) {
                res.status(409);
                Optional<Registration> existing = storageService.getRegistrationByName(name);
                JsonObject response = new JsonObject();
                response.addProperty("error", "Name already exists");
                if (existing.isPresent()) {
                    response.addProperty("existingUserId", existing.get().getId());
                    response.addProperty("existingUserName", existing.get().getName());
                }
                LogService.logApiRequest("POST", "/api/register", 409, "Duplicate name: " + name);
                return gson.toJson(response);
            }

            System.out.println("[Server] Registration request: name=" + name + ", role=" + role);

            FingerprintService.EnrollmentResult result = fingerprintService.enroll(name, role, timeout);

            if (result.success) {
                res.status(201);
                Optional<Registration> reg = storageService.getRegistrationById(result.registrationId);
                if (reg.isPresent()) {
                    return gson.toJson(registrationToPublicJson(reg.get()));
                }
                JsonObject response = new JsonObject();
                response.addProperty("id", result.registrationId);
                response.addProperty("name", name);
                response.addProperty("role", role);
                response.addProperty("message", result.message);
                return gson.toJson(response);
            } else {
                res.status(409); // Conflict
                JsonObject response = new JsonObject();
                response.addProperty("error", result.message);
                if (result.existingUserId != null) {
                    response.addProperty("existingUserId", result.existingUserId);
                    response.addProperty("existingUserName", result.existingUserName);
                }
                return gson.toJson(response);
            }
        });

        // List all fingerprints
        get("/api/fingerprints", (req, res) -> {
            List<Registration> registrations = storageService.getAllRegistrations();
            List<JsonObject> publicList = registrations.stream()
                    .map(this::registrationToPublicJson)
                    .collect(Collectors.toList());
            return gson.toJson(publicList);
        });

        // Get single fingerprint
        get("/api/fingerprints/:id", (req, res) -> {
            String id = req.params(":id");
            Optional<Registration> reg = storageService.getRegistrationById(id);
            
            if (reg.isPresent()) {
                return gson.toJson(registrationToPublicJson(reg.get()));
            } else {
                res.status(404);
                return errorJson("Fingerprint not found");
            }
        });

        // Export fingerprint
        get("/api/fingerprints/:id/export", (req, res) -> {
            String id = req.params(":id");
            Optional<Registration> reg = storageService.getRegistrationById(id);
            
            if (reg.isPresent()) {
                Registration r = reg.get();
                ExportData export = new ExportData();
                export.setExportedAt(Instant.now().toString());
                
                ExportData.FingerprintData fpData = new ExportData.FingerprintData();
                fpData.setName(r.getName());
                fpData.setRole(r.getRole());
                fpData.setTemplateBase64(r.getTemplateBase64());
                fpData.setOriginalCreatedAt(r.getCreatedAt());
                export.setFingerprint(fpData);

                return gson.toJson(export);
            } else {
                res.status(404);
                return errorJson("Fingerprint not found");
            }
        });

        // Import fingerprint
        post("/api/fingerprints/import", (req, res) -> {
            ExportData importData = gson.fromJson(req.body(), ExportData.class);

            if (importData == null || importData.getFingerprint() == null) {
                res.status(400);
                return errorJson("Invalid import data format");
            }

            if (!"1.0".equals(importData.getVersion())) {
                res.status(400);
                return errorJson("Unsupported export version: " + importData.getVersion());
            }

            ExportData.FingerprintData fp = importData.getFingerprint();
            
            if (fp.getTemplateBase64() == null || fp.getTemplateBase64().isEmpty()) {
                res.status(400);
                return errorJson("Template data is required");
            }

            FingerprintService.EnrollmentResult result = fingerprintService.importFingerprint(
                fp.getName(),
                fp.getRole(),
                fp.getTemplateBase64(),
                fp.getOriginalCreatedAt()
            );

            if (result.success) {
                res.status(201);
                Optional<Registration> reg = storageService.getRegistrationById(result.registrationId);
                if (reg.isPresent()) {
                    JsonObject response = registrationToPublicJson(reg.get());
                    response.addProperty("message", "Import successful");
                    return gson.toJson(response);
                }
                JsonObject response = new JsonObject();
                response.addProperty("id", result.registrationId);
                response.addProperty("message", "Import successful");
                return gson.toJson(response);
            } else {
                res.status(409);
                JsonObject response = new JsonObject();
                response.addProperty("error", result.message);
                if (result.existingUserId != null) {
                    response.addProperty("existingUserId", result.existingUserId);
                    response.addProperty("existingUserName", result.existingUserName);
                }
                return gson.toJson(response);
            }
        });

        // Delete fingerprint
        delete("/api/fingerprints/:id", (req, res) -> {
            String id = req.params(":id");
            boolean deleted = storageService.deleteRegistration(id);
            
            if (deleted) {
                JsonObject response = new JsonObject();
                response.addProperty("message", "Fingerprint deleted");
                response.addProperty("id", id);
                return gson.toJson(response);
            } else {
                res.status(404);
                return errorJson("Fingerprint not found");
            }
        });

        // Get config
        get("/api/config", (req, res) -> {
            return gson.toJson(storageService.getConfig());
        });

        // Update config (serverPort only, webhookUrl is in .env)
        put("/api/config", (req, res) -> {
            Config newConfig = gson.fromJson(req.body(), Config.class);
            storageService.updateConfig(newConfig);
            return gson.toJson(storageService.getConfig());
        });

        // Get webhook URL (read-only, from .env)
        get("/api/config/webhook", (req, res) -> {
            JsonObject response = new JsonObject();
            response.addProperty("webhookUrl", EnvLoader.get("WEBHOOK_URL", ""));
            response.addProperty("note", "Webhook URL is configured in .env file (WEBHOOK_URL)");
            return gson.toJson(response);
        });

        // Test webhook
        post("/api/config/webhook/test", (req, res) -> {
            WebhookService.WebhookTestResult result = webhookService.testWebhook();
            JsonObject response = new JsonObject();
            response.addProperty("success", result.success);
            response.addProperty("message", result.message);
            
            if (!result.success) {
                res.status(500);
            }
            return gson.toJson(response);
        });

        // Start identification
        post("/api/identification/start", (req, res) -> {
            fingerprintService.startIdentification();
            JsonObject response = new JsonObject();
            response.addProperty("message", "Identification started");
            response.addProperty("running", true);
            return gson.toJson(response);
        });

        // Stop identification
        post("/api/identification/stop", (req, res) -> {
            fingerprintService.stopIdentification();
            JsonObject response = new JsonObject();
            response.addProperty("message", "Identification stopped");
            response.addProperty("running", false);
            return gson.toJson(response);
        });

        // Exception handling
        exception(Exception.class, (e, req, res) -> {
            System.err.println("[Server] Error: " + e.getMessage());
            e.printStackTrace();
            res.status(500);
            res.type("application/json");
            res.body(errorJson("Internal server error: " + e.getMessage()));
        });

        // Set up identification callback
        fingerprintService.setOnFingerprintIdentified((registration, score) -> {
            System.out.println("[Server] Fingerprint identified: " + registration.getName());
            webhookService.notifyFingerprintDetected(registration, score);
        });

        // Start identification by default
        fingerprintService.startIdentification();

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server] Shutting down...");
            webhookService.shutdown();
            fingerprintService.shutdown();
            stop();
        }));

        awaitInitialization();

        System.out.println("============================================================");
        System.out.println("  Fingerprint REST API Server - Running");
        System.out.println("============================================================");
        System.out.println("  Server URL: http://localhost:" + port);
        System.out.println("  Registered fingerprints: " + fingerprintService.getRegisteredCount());
        String webhookUrl = EnvLoader.get("WEBHOOK_URL", "");
        System.out.println("  Webhook URL: " + (webhookUrl.isEmpty() ? "(not configured)" : webhookUrl));
        System.out.println("============================================================");
        System.out.println("  Endpoints:");
        System.out.println("    GET    /api/status              - Server status");
        System.out.println("    POST   /api/register            - Register new fingerprint");
        System.out.println("    GET    /api/fingerprints        - List all fingerprints");
        System.out.println("    GET    /api/fingerprints/:id    - Get fingerprint by ID");
        System.out.println("    GET    /api/fingerprints/:id/export - Export fingerprint");
        System.out.println("    POST   /api/fingerprints/import - Import fingerprint");
        System.out.println("    DELETE /api/fingerprints/:id    - Delete fingerprint");
        System.out.println("    GET    /api/config              - Get config");
        System.out.println("    PUT    /api/config              - Update config");
        System.out.println("    PUT    /api/config/webhook      - Update webhook URL");
        System.out.println("    POST   /api/config/webhook/test - Test webhook");
        System.out.println("    POST   /api/identification/start - Start identification");
        System.out.println("    POST   /api/identification/stop  - Stop identification");
        System.out.println("============================================================");
        System.out.println("  Press Ctrl+C to stop the server");
        System.out.println("============================================================");
    }

    // ==================== Helper Methods ====================

    private JsonObject registrationToPublicJson(Registration reg) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", reg.getId());
        obj.addProperty("name", reg.getName());
        obj.addProperty("role", reg.getRole());
        obj.addProperty("createdAt", reg.getCreatedAt());
        // Don't expose templateBase64 in list/get responses
        return obj;
    }

    private String getJsonString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private String errorJson(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return gson.toJson(error);
    }

    // ==================== Main ====================

    public static void main(String[] args) {
        FingerprintServer server = new FingerprintServer();
        server.start();
    }
}
