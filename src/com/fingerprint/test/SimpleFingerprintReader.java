package com.fingerprint.test;

import com.zkteco.biometric.FingerprintSensorEx;
import com.zkteco.biometric.FingerprintSensorErrorCode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

/**
 * SimpleFingerprintReader - A verbose fingerprint capture utility
 * 
 * This program continuously captures fingerprints from a ZKTeco fingerprint reader,
 * saving images and template data to the outputs folder with detailed logging.
 */
public class SimpleFingerprintReader {

    // Device and database handles
    private static long mhDevice = 0;
    private static long mhDB = 0;

    // Image dimensions
    private static int fpWidth = 0;
    private static int fpHeight = 0;

    // Buffers
    private static byte[] imgbuf = null;
    private static byte[] template = new byte[2048];
    private static int[] templateLen = new int[1];

    // Control flags
    private static volatile boolean mbStop = false;

    // Capture counter
    private static int captureCount = 0;

    // Output directory (relative path from working directory)
    private static final String OUTPUT_DIR = "./outputs";

    // Date formatters
    private static final SimpleDateFormat LOG_TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final SimpleDateFormat FILE_TIME_FORMAT = new SimpleDateFormat("yyyyMMdd_HHmmss");

    // ==================== Logging Methods ====================

    private static void logInfo(String message) {
        System.out.println("[" + LOG_TIME_FORMAT.format(new Date()) + "] [INFO] " + message);
    }

    private static void logWarn(String message) {
        System.out.println("[" + LOG_TIME_FORMAT.format(new Date()) + "] [WARN] " + message);
    }

    private static void logError(String message) {
        System.out.println("[" + LOG_TIME_FORMAT.format(new Date()) + "] [ERROR] " + message);
    }

    // ==================== Main Entry Point ====================

    public static void main(String[] args) {
        logInfo("===========================================");
        logInfo("  SimpleFingerprintReader - Starting Up");
        logInfo("===========================================");

        // Check and create output directory
        if (!checkOutputDirectory()) {
            return;
        }

        // Initialize the fingerprint sensor
        if (!initializeSensor()) {
            return;
        }

        // Start the capture thread
        Thread captureThread = new Thread(new CaptureRunnable());
        captureThread.start();

        // Main thread monitors for exit command
        logInfo("Ready to capture fingerprints. Type 'exit' to quit.");
        logInfo("-------------------------------------------");

        Scanner scanner = new Scanner(System.in);
        while (!mbStop) {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine().trim().toLowerCase();
                if (input.equals("exit") || input.equals("quit") || input.equals("q")) {
                    logInfo("User requested exit");
                    mbStop = true;
                    break;
                }
            }
        }
        scanner.close();

        // Wait for capture thread to stop
        try {
            logInfo("Waiting for capture thread to stop...");
            captureThread.join(2000);
            logInfo("Capture thread stopped");
        } catch (InterruptedException e) {
            logWarn("Interrupted while waiting for capture thread: " + e.getMessage());
        }

        // Cleanup
        freeSensor();

        logInfo("===========================================");
        logInfo("  Total fingerprints captured: " + captureCount);
        logInfo("  SimpleFingerprintReader - Shutdown Complete");
        logInfo("===========================================");
    }

    // ==================== Output Directory ====================

    private static boolean checkOutputDirectory() {
        logInfo("Checking output directory: " + OUTPUT_DIR);

        File outputDir = new File(OUTPUT_DIR);

        if (outputDir.exists()) {
            if (outputDir.isDirectory()) {
                logInfo("Output directory exists: " + outputDir.getAbsolutePath());
                return true;
            } else {
                logError("Output path exists but is not a directory: " + outputDir.getAbsolutePath());
                return false;
            }
        }

        logInfo("Output directory does not exist, creating...");
        if (outputDir.mkdirs()) {
            logInfo("Output directory created successfully: " + outputDir.getAbsolutePath());
            return true;
        } else {
            logError("Failed to create output directory: " + outputDir.getAbsolutePath());
            return false;
        }
    }

    // ==================== Sensor Initialization ====================

    private static boolean initializeSensor() {
        int ret;

        // Step 1: Initialize SDK
        logInfo("Initializing SDK...");
        ret = FingerprintSensorEx.Init();
        if (ret != FingerprintSensorErrorCode.ZKFP_ERR_OK) {
            logError("Failed to initialize SDK, ret=" + ret);
            return false;
        }
        logInfo("SDK initialized successfully");

        // Step 2: Get device count
        logInfo("Getting device count...");
        ret = FingerprintSensorEx.GetDeviceCount();
        logInfo("Device count: " + ret);
        if (ret < 1) {
            logWarn("No fingerprint devices connected!");
            logError("Cannot proceed without a connected device");
            FingerprintSensorEx.Terminate();
            return false;
        }

        // Step 3: Open device
        logInfo("Opening device 0...");
        mhDevice = FingerprintSensorEx.OpenDevice(0);
        if (mhDevice == 0) {
            logError("Failed to open device");
            FingerprintSensorEx.Terminate();
            return false;
        }
        logInfo("Device opened successfully, handle=" + mhDevice);

        // Step 4: Initialize database
        logInfo("Initializing fingerprint database...");
        mhDB = FingerprintSensorEx.DBInit();
        if (mhDB == 0) {
            logError("Failed to initialize fingerprint database");
            FingerprintSensorEx.CloseDevice(mhDevice);
            mhDevice = 0;
            FingerprintSensorEx.Terminate();
            return false;
        }
        logInfo("Database initialized successfully, handle=" + mhDB);

        // Step 5: Get image parameters
        logInfo("Getting device parameters...");
        byte[] paramValue = new byte[4];
        int[] size = new int[1];

        size[0] = 4;
        FingerprintSensorEx.GetParameters(mhDevice, 1, paramValue, size);
        fpWidth = byteArrayToInt(paramValue);
        logInfo("Image width: " + fpWidth + " pixels");

        size[0] = 4;
        FingerprintSensorEx.GetParameters(mhDevice, 2, paramValue, size);
        fpHeight = byteArrayToInt(paramValue);
        logInfo("Image height: " + fpHeight + " pixels");

        // Allocate image buffer
        imgbuf = new byte[fpWidth * fpHeight];
        logInfo("Image buffer allocated: " + imgbuf.length + " bytes");

        logInfo("-------------------------------------------");
        logInfo("Device Information:");
        logInfo("  - Device Count: " + ret);
        logInfo("  - Image Width:  " + fpWidth + " px");
        logInfo("  - Image Height: " + fpHeight + " px");
        logInfo("  - Buffer Size:  " + imgbuf.length + " bytes");
        logInfo("-------------------------------------------");

        return true;
    }

    // ==================== Sensor Cleanup ====================

    private static void freeSensor() {
        logInfo("-------------------------------------------");
        logInfo("Starting cleanup...");

        // Stop flag already set by main
        logInfo("Stop flag set");

        // Free database
        if (mhDB != 0) {
            logInfo("Freeing database handle " + mhDB + "...");
            int ret = FingerprintSensorEx.DBFree(mhDB);
            if (ret == 0) {
                logInfo("Database freed successfully");
            } else {
                logError("Failed to free database, ret=" + ret);
            }
            mhDB = 0;
        } else {
            logInfo("Database handle already null, skipping");
        }

        // Close device
        if (mhDevice != 0) {
            logInfo("Closing device handle " + mhDevice + "...");
            int ret = FingerprintSensorEx.CloseDevice(mhDevice);
            if (ret == 0) {
                logInfo("Device closed successfully");
            } else {
                logError("Failed to close device, ret=" + ret);
            }
            mhDevice = 0;
        } else {
            logInfo("Device handle already null, skipping");
        }

        // Terminate SDK
        logInfo("Terminating SDK...");
        FingerprintSensorEx.Terminate();
        logInfo("SDK terminated");

        logInfo("Cleanup complete");
        logInfo("-------------------------------------------");
    }

    // ==================== Capture Thread ====================

    private static class CaptureRunnable implements Runnable {
        @Override
        public void run() {
            logInfo("Capture thread started");

            while (!mbStop) {
                logInfo("Polling for fingerprint...");

                templateLen[0] = 2048;
                int ret = FingerprintSensorEx.AcquireFingerprint(mhDevice, imgbuf, template, templateLen);

                if (ret == 0) {
                    // Fingerprint captured successfully
                    captureCount++;
                    String timestamp = FILE_TIME_FORMAT.format(new Date());

                    logInfo("========== FINGERPRINT CAPTURED ==========");
                    logInfo("Capture #" + captureCount);
                    logInfo("Timestamp: " + timestamp);
                    logInfo("Template size: " + templateLen[0] + " bytes");

                    // Analyze template
                    int nonZeroBytes = countNonZeroBytes(template, templateLen[0]);
                    String hexPreview = bytesToHex(template, Math.min(20, templateLen[0]));
                    logInfo("Non-zero bytes: " + nonZeroBytes + " / " + templateLen[0]);
                    logInfo("First 20 bytes (hex): " + hexPreview);

                    // Generate filename (always overwrite)
                    String bmpPath = OUTPUT_DIR + "/test_fingerprint.bmp";
                    String txtPath = OUTPUT_DIR + "/test_fingerprint.txt";

                    // Save image
                    logInfo("Saving image to: " + bmpPath);
                    try {
                        writeBitmap(imgbuf, fpWidth, fpHeight, bmpPath);
                        logInfo("Image saved successfully");
                    } catch (IOException e) {
                        logError("Failed to save image: " + e.getMessage());
                    }

                    // Save template with analysis
                    logInfo("Saving template to: " + txtPath);
                    try {
                        saveTemplateWithAnalysis(txtPath, timestamp, templateLen[0], nonZeroBytes, hexPreview, template);
                        logInfo("Template analysis saved successfully");
                    } catch (IOException e) {
                        logError("Failed to save template: " + e.getMessage());
                    }

                    logInfo("==========================================");
                    logInfo("Captured #" + captureCount + " at " + timestamp);
                    logInfo("Ready for next fingerprint...");
                    logInfo("-------------------------------------------");

                } else {
                    logInfo("No finger detected, ret=" + ret);
                }

                // Wait before next poll
                logInfo("Waiting 500ms...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    logWarn("Capture thread interrupted: " + e.getMessage());
                    break;
                }
            }

            logInfo("Capture thread exiting");
        }
    }

    // ==================== File Handling ====================

    private static String getUniqueFilename(String basePath, String extension) {
        File file = new File(basePath + extension);
        if (!file.exists()) {
            return basePath + extension;
        }

        int seq = 1;
        while (true) {
            String seqPath = basePath + "_seq" + seq + extension;
            file = new File(seqPath);
            if (!file.exists()) {
                logWarn("File exists, using sequence " + seq + ": " + seqPath);
                return seqPath;
            }
            seq++;
        }
    }

    private static void saveTemplateWithAnalysis(String path, String timestamp, int size,
                                                  int nonZeroBytes, String hexPreview, byte[] template) throws IOException {
        String base64 = FingerprintSensorEx.BlobToBase64(template, size);

        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            writer.println("===============================================");
            writer.println("  Fingerprint Template Analysis Report");
            writer.println("===============================================");
            writer.println();
            writer.println("Timestamp:        " + timestamp);
            writer.println("Template Size:    " + size + " bytes");
            writer.println("Non-zero Bytes:   " + nonZeroBytes + " / " + size + " (" +
                    String.format("%.1f", (nonZeroBytes * 100.0 / size)) + "%)");
            writer.println();
            writer.println("First 20 bytes (hex):");
            writer.println("  " + hexPreview);
            writer.println();
            writer.println("===============================================");
            writer.println("  Base64 Encoded Template");
            writer.println("===============================================");
            writer.println();
            writer.println(base64);
            writer.println();
            writer.println("===============================================");
            writer.println("  End of Report");
            writer.println("===============================================");
        }
    }

    // ==================== Template Analysis ====================

    private static int countNonZeroBytes(byte[] data, int length) {
        int count = 0;
        for (int i = 0; i < length; i++) {
            if (data[i] != 0) {
                count++;
            }
        }
        return count;
    }

    private static String bytesToHex(byte[] data, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }

    // ==================== Byte Conversion Utilities ====================

    public static byte[] intToByteArray(final int number) {
        byte[] abyte = new byte[4];
        abyte[0] = (byte) (0xff & number);
        abyte[1] = (byte) ((0xff00 & number) >> 8);
        abyte[2] = (byte) ((0xff0000 & number) >> 16);
        abyte[3] = (byte) ((0xff000000 & number) >> 24);
        return abyte;
    }

    public static int byteArrayToInt(byte[] bytes) {
        int number = bytes[0] & 0xFF;
        number |= ((bytes[1] << 8) & 0xFF00);
        number |= ((bytes[2] << 16) & 0xFF0000);
        number |= ((bytes[3] << 24) & 0xFF000000);
        return number;
    }

    public static byte[] changeByte(int data) {
        return intToByteArray(data);
    }

    // ==================== BMP Writing ====================

    public static void writeBitmap(byte[] imageBuf, int nWidth, int nHeight, String path) throws IOException {
        java.io.FileOutputStream fos = new java.io.FileOutputStream(path);
        java.io.DataOutputStream dos = new java.io.DataOutputStream(fos);

        int w = (((nWidth + 3) / 4) * 4);
        int bfType = 0x424d;
        int bfSize = 54 + 1024 + w * nHeight;
        int bfReserved1 = 0;
        int bfReserved2 = 0;
        int bfOffBits = 54 + 1024;

        dos.writeShort(bfType);
        dos.write(changeByte(bfSize), 0, 4);
        dos.write(changeByte(bfReserved1), 0, 2);
        dos.write(changeByte(bfReserved2), 0, 2);
        dos.write(changeByte(bfOffBits), 0, 4);

        int biSize = 40;
        int biWidth = nWidth;
        int biHeight = nHeight;
        int biPlanes = 1;
        int biBitcount = 8;
        int biCompression = 0;
        int biSizeImage = w * nHeight;
        int biXPelsPerMeter = 0;
        int biYPelsPerMeter = 0;
        int biClrUsed = 0;
        int biClrImportant = 0;

        dos.write(changeByte(biSize), 0, 4);
        dos.write(changeByte(biWidth), 0, 4);
        dos.write(changeByte(biHeight), 0, 4);
        dos.write(changeByte(biPlanes), 0, 2);
        dos.write(changeByte(biBitcount), 0, 2);
        dos.write(changeByte(biCompression), 0, 4);
        dos.write(changeByte(biSizeImage), 0, 4);
        dos.write(changeByte(biXPelsPerMeter), 0, 4);
        dos.write(changeByte(biYPelsPerMeter), 0, 4);
        dos.write(changeByte(biClrUsed), 0, 4);
        dos.write(changeByte(biClrImportant), 0, 4);

        // Write grayscale palette
        for (int i = 0; i < 256; i++) {
            dos.writeByte(i);
            dos.writeByte(i);
            dos.writeByte(i);
            dos.writeByte(0);
        }

        // Write image data (bottom-up)
        byte[] filter = null;
        if (w > nWidth) {
            filter = new byte[w - nWidth];
        }

        for (int i = 0; i < nHeight; i++) {
            dos.write(imageBuf, (nHeight - 1 - i) * nWidth, nWidth);
            if (w > nWidth) {
                dos.write(filter, 0, w - nWidth);
            }
        }

        dos.flush();
        dos.close();
        fos.close();
    }
}
