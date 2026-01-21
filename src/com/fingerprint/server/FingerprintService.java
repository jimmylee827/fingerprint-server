package com.fingerprint.server;

import com.fingerprint.model.Registration;
import com.zkteco.biometric.FingerprintSensorEx;
import com.zkteco.biometric.FingerprintSensorErrorCode;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * FingerprintService - Singleton managing fingerprint sensor and operations
 */
public class FingerprintService {
    private static FingerprintService instance;

    // Device and database handles
    private long deviceHandle = 0;
    private long dbHandle = 0;

    // Image dimensions
    private int imageWidth = 0;
    private int imageHeight = 0;

    // Buffers
    private byte[] imageBuffer = null;
    private byte[] captureTemplate = new byte[2048];
    private int[] captureTemplateLen = new int[1];

    // Registration buffers (3 captures for enrollment)
    private static final int REGISTER_CAPTURE_COUNT = 3;
    private byte[][] registerTemplates = new byte[REGISTER_CAPTURE_COUNT][2048];
    private int[] registerTemplateLens = new int[REGISTER_CAPTURE_COUNT];

    // Control flags
    private AtomicBoolean isInitialized = new AtomicBoolean(false);
    private AtomicBoolean isIdentificationRunning = new AtomicBoolean(false);
    private AtomicBoolean isEnrollmentInProgress = new AtomicBoolean(false);

    // Services
    private StorageService storageService;
    private WebhookService webhookService;

    // Identification callback
    private BiConsumer<Registration, Integer> onFingerprintIdentified;

    // Internal fingerprint ID counter for DBAdd
    private int nextFingerprintId = 1;

    private FingerprintService() {
    }

    public static synchronized FingerprintService getInstance() {
        if (instance == null) {
            instance = new FingerprintService();
        }
        return instance;
    }

    // ==================== Initialization ====================

    public synchronized boolean initialize(StorageService storageService, WebhookService webhookService) {
        if (isInitialized.get()) {
            System.out.println("[FingerprintService] Already initialized");
            return true;
        }

        this.storageService = storageService;
        this.webhookService = webhookService;

        System.out.println("[FingerprintService] Initializing...");

        // Step 1: Initialize SDK
        int ret = FingerprintSensorEx.Init();
        if (ret != FingerprintSensorErrorCode.ZKFP_ERR_OK) {
            System.err.println("[FingerprintService] Failed to initialize SDK, ret=" + ret);
            return false;
        }
        System.out.println("[FingerprintService] SDK initialized");

        // Step 2: Check device count
        ret = FingerprintSensorEx.GetDeviceCount();
        System.out.println("[FingerprintService] Device count: " + ret);
        if (ret < 1) {
            System.err.println("[FingerprintService] No fingerprint devices connected!");
            FingerprintSensorEx.Terminate();
            return false;
        }

        // Step 3: Open device
        deviceHandle = FingerprintSensorEx.OpenDevice(0);
        if (deviceHandle == 0) {
            System.err.println("[FingerprintService] Failed to open device");
            FingerprintSensorEx.Terminate();
            return false;
        }
        System.out.println("[FingerprintService] Device opened, handle=" + deviceHandle);

        // Step 4: Initialize database
        dbHandle = FingerprintSensorEx.DBInit();
        if (dbHandle == 0) {
            System.err.println("[FingerprintService] Failed to initialize database");
            FingerprintSensorEx.CloseDevice(deviceHandle);
            deviceHandle = 0;
            FingerprintSensorEx.Terminate();
            return false;
        }
        System.out.println("[FingerprintService] Database initialized, handle=" + dbHandle);

        // Step 5: Get image parameters
        byte[] paramValue = new byte[4];
        int[] size = new int[1];

        size[0] = 4;
        FingerprintSensorEx.GetParameters(deviceHandle, 1, paramValue, size);
        imageWidth = byteArrayToInt(paramValue);

        size[0] = 4;
        FingerprintSensorEx.GetParameters(deviceHandle, 2, paramValue, size);
        imageHeight = byteArrayToInt(paramValue);

        imageBuffer = new byte[imageWidth * imageHeight];
        System.out.println("[FingerprintService] Image size: " + imageWidth + "x" + imageHeight);

        // Step 6: Load existing registrations into DB
        loadRegistrationsIntoDb();

        isInitialized.set(true);
        System.out.println("[FingerprintService] Initialization complete");
        return true;
    }

    private void loadRegistrationsIntoDb() {
        List<Registration> registrations = storageService.getAllRegistrations();
        System.out.println("[FingerprintService] Loading " + registrations.size() + " registrations into memory DB...");

        for (Registration reg : registrations) {
            try {
                byte[] template = base64ToBytes(reg.getTemplateBase64());
                if (template != null && template.length > 0) {
                    int fid = nextFingerprintId++;
                    int ret = FingerprintSensorEx.DBAdd(dbHandle, fid, template);
                    if (ret == FingerprintSensorErrorCode.ZKFP_ERR_OK) {
                        // Store mapping between fid and registration id
                        fidToRegistrationId.put(fid, reg.getId());
                        System.out.println("[FingerprintService] Loaded: " + reg.getName() + " (fid=" + fid + ")");
                    } else {
                        System.err.println("[FingerprintService] Failed to add to DB: " + reg.getName() + ", ret=" + ret);
                    }
                }
            } catch (Exception e) {
                System.err.println("[FingerprintService] Error loading registration: " + reg.getId() + " - " + e.getMessage());
            }
        }
    }

    // Mapping from internal fingerprint ID to registration ID
    private java.util.Map<Integer, String> fidToRegistrationId = new java.util.concurrent.ConcurrentHashMap<>();

    // ==================== Enrollment (Registration) ====================

    public static class EnrollmentResult {
        public boolean success;
        public String message;
        public String registrationId;
        public String existingUserId;
        public String existingUserName;

        public static EnrollmentResult success(String registrationId) {
            EnrollmentResult r = new EnrollmentResult();
            r.success = true;
            r.registrationId = registrationId;
            r.message = "Enrollment successful";
            return r;
        }

        public static EnrollmentResult failure(String message) {
            EnrollmentResult r = new EnrollmentResult();
            r.success = false;
            r.message = message;
            return r;
        }

        public static EnrollmentResult duplicate(String message, String existingUserId, String existingUserName) {
            EnrollmentResult r = new EnrollmentResult();
            r.success = false;
            r.message = message;
            r.existingUserId = existingUserId;
            r.existingUserName = existingUserName;
            return r;
        }
    }

    /**
     * Synchronous enrollment - blocks until 3 fingerprints captured or timeout
     * @param name User's name
     * @param role User's role (Admin/User)
     * @param timeoutSeconds Timeout in seconds for entire enrollment
     * @return EnrollmentResult with success status and registration ID or error details
     */
    public EnrollmentResult enroll(String name, String role, int timeoutSeconds) {
        if (!isInitialized.get()) {
            return EnrollmentResult.failure("Fingerprint service not initialized");
        }

        if (isEnrollmentInProgress.get()) {
            return EnrollmentResult.failure("Another enrollment is already in progress");
        }

        // Pause identification while enrolling
        boolean wasIdentificationRunning = isIdentificationRunning.get();
        if (wasIdentificationRunning) {
            stopIdentification();
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }

        isEnrollmentInProgress.set(true);
        System.out.println("[FingerprintService] Starting enrollment for: " + name);

        // Pre-generate registration ID for saving BMPs
        String registrationId = storageService.generateNewId();

        try {
            int captureCount = 0;
            long startTime = System.currentTimeMillis();
            long timeoutMs = timeoutSeconds * 1000L;

            // Clear previous templates
            for (int i = 0; i < REGISTER_CAPTURE_COUNT; i++) {
                registerTemplates[i] = new byte[2048];
                registerTemplateLens[i] = 0;
            }

            // Capture 3 fingerprints
            while (captureCount < REGISTER_CAPTURE_COUNT) {
                // Check timeout
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    return EnrollmentResult.failure("Enrollment timed out. Captured " + captureCount + "/" + REGISTER_CAPTURE_COUNT);
                }

                // Capture fingerprint
                captureTemplateLen[0] = 2048;
                int ret = FingerprintSensorEx.AcquireFingerprint(deviceHandle, imageBuffer, captureTemplate, captureTemplateLen);

                if (ret == FingerprintSensorErrorCode.ZKFP_ERR_OK) {
                    System.out.println("[FingerprintService] Capture " + (captureCount + 1) + "/" + REGISTER_CAPTURE_COUNT);

                    // First capture - check for duplicates against existing registrations
                    if (captureCount == 0) {
                        IdentifyResult dupCheck = identify(captureTemplate, captureTemplateLen[0]);
                        if (dupCheck.matched) {
                            return EnrollmentResult.duplicate(
                                "Fingerprint already registered",
                                dupCheck.registration.getId(),
                                dupCheck.registration.getName()
                            );
                        }
                    }

                    // Verify same finger as previous capture
                    if (captureCount > 0) {
                        int matchScore = FingerprintSensorEx.DBMatch(dbHandle, captureTemplate, registerTemplates[captureCount - 1]);
                        if (matchScore <= 0) {
                            System.out.println("[FingerprintService] Different finger detected, please use the same finger");
                            continue; // Don't increment, try again
                        }
                    }

                    // Save BMP for this capture
                    saveCapturedBmp(registrationId, captureCount + 1, imageBuffer.clone());

                    // Store this capture
                    System.arraycopy(captureTemplate, 0, registerTemplates[captureCount], 0, captureTemplateLen[0]);
                    registerTemplateLens[captureCount] = captureTemplateLen[0];
                    captureCount++;

                    System.out.println("[FingerprintService] Capture " + captureCount + " stored");

                    // Wait a bit before next capture
                    try { Thread.sleep(500); } catch (InterruptedException e) {}
                }

                // Small delay between capture attempts
                try { Thread.sleep(100); } catch (InterruptedException e) {}
            }

            // Merge 3 captures into one registration template
            byte[] mergedTemplate = new byte[2048];
            int[] mergedLen = new int[1];
            mergedLen[0] = 2048;

            int ret = FingerprintSensorEx.DBMerge(dbHandle, 
                registerTemplates[0], registerTemplates[1], registerTemplates[2],
                mergedTemplate, mergedLen);

            if (ret != FingerprintSensorErrorCode.ZKFP_ERR_OK) {
                return EnrollmentResult.failure("Failed to merge fingerprint templates, ret=" + ret);
            }

            System.out.println("[FingerprintService] Templates merged, size=" + mergedLen[0]);

            // Convert to Base64 for storage
            String templateBase64 = FingerprintSensorEx.BlobToBase64(mergedTemplate, mergedLen[0]);

            // Save to storage with pre-generated ID
            storageService.addRegistrationWithPregeneratedId(registrationId, name, role, templateBase64);

            // Add to in-memory DB
            int fid = nextFingerprintId++;
            ret = FingerprintSensorEx.DBAdd(dbHandle, fid, mergedTemplate);
            if (ret == FingerprintSensorErrorCode.ZKFP_ERR_OK) {
                fidToRegistrationId.put(fid, registrationId);
                System.out.println("[FingerprintService] Added to DB with fid=" + fid);
            } else {
                System.err.println("[FingerprintService] Warning: Failed to add to DB, ret=" + ret);
            }

            System.out.println("[FingerprintService] Enrollment complete: " + registrationId);
            return EnrollmentResult.success(registrationId);

        } finally {
            isEnrollmentInProgress.set(false);
            if (wasIdentificationRunning) {
                startIdentification();
            }
        }
    }

    // ==================== Identification ====================

    public static class IdentifyResult {
        public boolean matched;
        public Registration registration;
        public int score;

        public static IdentifyResult notFound() {
            IdentifyResult r = new IdentifyResult();
            r.matched = false;
            return r;
        }

        public static IdentifyResult found(Registration registration, int score) {
            IdentifyResult r = new IdentifyResult();
            r.matched = true;
            r.registration = registration;
            r.score = score;
            return r;
        }
    }

    /**
     * Identify a fingerprint template against the database
     */
    public IdentifyResult identify(byte[] template, int templateLen) {
        if (!isInitialized.get() || dbHandle == 0) {
            return IdentifyResult.notFound();
        }

        int[] fid = new int[1];
        int[] score = new int[1];

        int ret = FingerprintSensorEx.DBIdentify(dbHandle, template, fid, score);

        if (ret == FingerprintSensorErrorCode.ZKFP_ERR_OK) {
            String registrationId = fidToRegistrationId.get(fid[0]);
            if (registrationId != null) {
                Optional<Registration> reg = storageService.getRegistrationById(registrationId);
                if (reg.isPresent()) {
                    return IdentifyResult.found(reg.get(), score[0]);
                }
            }
        }

        return IdentifyResult.notFound();
    }

    /**
     * Check if a template matches any existing registration (for import duplicate check)
     */
    public IdentifyResult checkDuplicate(String templateBase64) {
        byte[] template = base64ToBytes(templateBase64);
        if (template == null || template.length == 0) {
            return IdentifyResult.notFound();
        }
        return identify(template, template.length);
    }

    // ==================== Background Identification ====================

    private Thread identificationThread;

    public void setOnFingerprintIdentified(BiConsumer<Registration, Integer> callback) {
        this.onFingerprintIdentified = callback;
    }

    public void startIdentification() {
        if (!isInitialized.get()) {
            System.err.println("[FingerprintService] Cannot start identification - not initialized");
            return;
        }

        if (isIdentificationRunning.get()) {
            System.out.println("[FingerprintService] Identification already running");
            return;
        }

        isIdentificationRunning.set(true);
        identificationThread = new Thread(this::identificationLoop, "IdentificationThread");
        identificationThread.start();
        System.out.println("[FingerprintService] Identification started");
    }

    public void stopIdentification() {
        isIdentificationRunning.set(false);
        if (identificationThread != null) {
            identificationThread.interrupt();
            try {
                identificationThread.join(2000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
        System.out.println("[FingerprintService] Identification stopped");
    }

    private void identificationLoop() {
        System.out.println("[FingerprintService] Identification loop started");

        while (isIdentificationRunning.get()) {
            // Skip if enrollment is in progress
            if (isEnrollmentInProgress.get()) {
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                continue;
            }

            captureTemplateLen[0] = 2048;
            int ret = FingerprintSensorEx.AcquireFingerprint(deviceHandle, imageBuffer, captureTemplate, captureTemplateLen);

            if (ret == FingerprintSensorErrorCode.ZKFP_ERR_OK) {
                IdentifyResult result = identify(captureTemplate, captureTemplateLen[0]);
                String timestamp = Instant.now().toString();

                if (result.matched && onFingerprintIdentified != null) {
                    System.out.println("[" + timestamp + "] [FingerprintService] Fingerprint identified: " + result.registration.getName() + " (score=" + result.score + ")");
                    LogService.logIdentified(result.registration.getName(), result.score);
                    onFingerprintIdentified.accept(result.registration, result.score);
                } else if (!result.matched) {
                    System.out.println("[" + timestamp + "] [FingerprintService] Unidentified fingerprint scan - no match found");
                    LogService.logUnidentified();
                    // Notify via webhook about unidentified scan
                    if (webhookService != null) {
                        webhookService.notifyFingerprintUnidentified();
                    }
                }

                // Debounce - wait before accepting another scan
                try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
            } else {
                // No finger detected, poll again
                try { Thread.sleep(200); } catch (InterruptedException e) { break; }
            }
        }

        System.out.println("[FingerprintService] Identification loop stopped");
    }

    // ==================== Import Support ====================

    /**
     * Import a fingerprint from exported data
     * @return Registration ID if successful, null if duplicate or error
     */
    public EnrollmentResult importFingerprint(String name, String role, String templateBase64, String originalCreatedAt) {
        if (!isInitialized.get()) {
            return EnrollmentResult.failure("Fingerprint service not initialized");
        }

        // Check for duplicate
        IdentifyResult dupCheck = checkDuplicate(templateBase64);
        if (dupCheck.matched) {
            return EnrollmentResult.duplicate(
                "Fingerprint already registered",
                dupCheck.registration.getId(),
                dupCheck.registration.getName()
            );
        }

        // Decode template
        byte[] template = base64ToBytes(templateBase64);
        if (template == null || template.length == 0) {
            return EnrollmentResult.failure("Invalid template data");
        }

        // Generate new ID and save
        String newId = storageService.generateNewId();
        Registration registration = storageService.addRegistrationWithId(
            newId, name, role, templateBase64, null // Use current time, not original
        );

        // Add to in-memory DB
        int fid = nextFingerprintId++;
        int ret = FingerprintSensorEx.DBAdd(dbHandle, fid, template);
        if (ret == FingerprintSensorErrorCode.ZKFP_ERR_OK) {
            fidToRegistrationId.put(fid, newId);
        }

        System.out.println("[FingerprintService] Imported fingerprint: " + name + " (id=" + newId + ")");
        return EnrollmentResult.success(newId);
    }

    // ==================== Cleanup ====================

    public synchronized void shutdown() {
        System.out.println("[FingerprintService] Shutting down...");

        stopIdentification();

        if (dbHandle != 0) {
            FingerprintSensorEx.DBFree(dbHandle);
            dbHandle = 0;
        }

        if (deviceHandle != 0) {
            FingerprintSensorEx.CloseDevice(deviceHandle);
            deviceHandle = 0;
        }

        FingerprintSensorEx.Terminate();
        isInitialized.set(false);

        System.out.println("[FingerprintService] Shutdown complete");
    }

    // ==================== Status ====================

    public boolean isInitialized() {
        return isInitialized.get();
    }

    public boolean isIdentificationRunning() {
        return isIdentificationRunning.get();
    }

    public boolean isEnrollmentInProgress() {
        return isEnrollmentInProgress.get();
    }

    public int getRegisteredCount() {
        return storageService != null ? storageService.getRegistrationCount() : 0;
    }

    // ==================== Utilities ====================

    private static int byteArrayToInt(byte[] bytes) {
        int number = bytes[0] & 0xFF;
        number |= ((bytes[1] << 8) & 0xFF00);
        number |= ((bytes[2] << 16) & 0xFF0000);
        number |= ((bytes[3] << 24) & 0xFF000000);
        return number;
    }

    /**
     * Convert Base64 string to byte array using SDK method
     */
    private static byte[] base64ToBytes(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            // SDK's Base64ToBlob requires output buffer and length
            byte[] buffer = new byte[2048];
            int[] length = new int[1];
            length[0] = buffer.length;
            int ret = FingerprintSensorEx.Base64ToBlob(base64, buffer, length[0]);
            if (ret > 0) {
                byte[] result = new byte[ret];
                System.arraycopy(buffer, 0, result, 0, ret);
                return result;
            }
            return null;
        } catch (Exception e) {
            System.err.println("[FingerprintService] Base64 decode error: " + e.getMessage());
            return null;
        }
    }

    // ==================== BMP Writing ====================

    /**
     * Save captured fingerprint image as BMP
     */
    private void saveCapturedBmp(String registrationId, int captureNumber, byte[] imageData) {
        try {
            String dirPath = storageService.ensureFingerprintDir(registrationId);
            String bmpPath = dirPath + "/capture_" + captureNumber + ".bmp";
            writeBitmap(imageData, imageWidth, imageHeight, bmpPath);
            System.out.println("[FingerprintService] Saved BMP: " + bmpPath);
        } catch (IOException e) {
            System.err.println("[FingerprintService] Failed to save BMP: " + e.getMessage());
        }
    }

    private static void writeBitmap(byte[] imageBuf, int nWidth, int nHeight, String path) throws IOException {
        FileOutputStream fos = new FileOutputStream(path);
        DataOutputStream dos = new DataOutputStream(fos);

        int w = (((nWidth + 3) / 4) * 4);
        int bfType = 0x424d;
        int bfSize = 54 + 1024 + w * nHeight;
        int bfReserved1 = 0;
        int bfReserved2 = 0;
        int bfOffBits = 54 + 1024;

        dos.writeShort(bfType);
        dos.write(intToByteArray(bfSize), 0, 4);
        dos.write(intToByteArray(bfReserved1), 0, 2);
        dos.write(intToByteArray(bfReserved2), 0, 2);
        dos.write(intToByteArray(bfOffBits), 0, 4);

        int biSize = 40;
        int biPlanes = 1;
        int biBitcount = 8;
        int biCompression = 0;
        int biSizeImage = w * nHeight;

        dos.write(intToByteArray(biSize), 0, 4);
        dos.write(intToByteArray(nWidth), 0, 4);
        dos.write(intToByteArray(nHeight), 0, 4);
        dos.write(intToByteArray(biPlanes), 0, 2);
        dos.write(intToByteArray(biBitcount), 0, 2);
        dos.write(intToByteArray(biCompression), 0, 4);
        dos.write(intToByteArray(biSizeImage), 0, 4);
        dos.write(intToByteArray(0), 0, 4); // biXPelsPerMeter
        dos.write(intToByteArray(0), 0, 4); // biYPelsPerMeter
        dos.write(intToByteArray(0), 0, 4); // biClrUsed
        dos.write(intToByteArray(0), 0, 4); // biClrImportant

        // Write grayscale palette
        for (int i = 0; i < 256; i++) {
            dos.writeByte(i);
            dos.writeByte(i);
            dos.writeByte(i);
            dos.writeByte(0);
        }

        // Write image data (bottom-up)
        byte[] padding = new byte[w - nWidth];
        for (int i = 0; i < nHeight; i++) {
            dos.write(imageBuf, (nHeight - 1 - i) * nWidth, nWidth);
            if (w > nWidth) {
                dos.write(padding, 0, w - nWidth);
            }
        }

        dos.flush();
        dos.close();
        fos.close();
    }

    private static byte[] intToByteArray(int number) {
        byte[] abyte = new byte[4];
        abyte[0] = (byte) (0xff & number);
        abyte[1] = (byte) ((0xff00 & number) >> 8);
        abyte[2] = (byte) ((0xff0000 & number) >> 16);
        abyte[3] = (byte) ((0xff000000 & number) >> 24);
        return abyte;
    }
}
