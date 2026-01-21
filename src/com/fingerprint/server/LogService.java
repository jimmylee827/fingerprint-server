package com.fingerprint.server;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LogService - File-based logging with daily rotation
 * 
 * Writes to:
 * - ./log_scan_YYYY-MM-DD.log for fingerprint scans and webhook activity
 * - ./log_api_YYYY-MM-DD.log for API requests
 */
public class LogService {
    private static final String SCAN_LOG_PREFIX = "./log_scan_";
    private static final String API_LOG_PREFIX = "./log_api_";
    private static final String LOG_SUFFIX = ".log";
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    
    private static String currentScanLogDate = "";
    private static String currentApiLogDate = "";
    private static PrintWriter scanLogWriter = null;
    private static PrintWriter apiLogWriter = null;
    
    private static final Object scanLock = new Object();
    private static final Object apiLock = new Object();
    
    /**
     * Log a fingerprint scan event (identified, unidentified, or webhook)
     */
    public static void logScan(String message) {
        synchronized (scanLock) {
            try {
                ensureScanLogWriter();
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String logLine = "[" + timestamp + "] " + message;
                scanLogWriter.println(logLine);
                scanLogWriter.flush();
            } catch (Exception e) {
                System.err.println("[LogService] Error writing to scan log: " + e.getMessage());
            }
        }
    }
    
    /**
     * Log an identified fingerprint scan
     */
    public static void logIdentified(String name, int score) {
        logScan("IDENTIFIED | name=" + name + " | score=" + score);
    }
    
    /**
     * Log an unidentified fingerprint scan
     */
    public static void logUnidentified() {
        logScan("UNIDENTIFIED | no match found");
    }
    
    /**
     * Log a webhook call
     */
    public static void logWebhook(String method, String url, int responseCode, String status) {
        logScan("WEBHOOK | " + method + " " + url + " | " + responseCode + " " + status);
    }
    
    /**
     * Log a webhook error
     */
    public static void logWebhookError(String method, String url, String error) {
        logScan("WEBHOOK_ERROR | " + method + " " + url + " | " + error);
    }
    
    /**
     * Log an API request
     */
    public static void logApi(String message) {
        synchronized (apiLock) {
            try {
                ensureApiLogWriter();
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
                String logLine = "[" + timestamp + "] " + message;
                apiLogWriter.println(logLine);
                apiLogWriter.flush();
            } catch (Exception e) {
                System.err.println("[LogService] Error writing to API log: " + e.getMessage());
            }
        }
    }
    
    /**
     * Log an API request with method, path, and status
     */
    public static void logApiRequest(String method, String path, int statusCode) {
        logApi(method + " " + path + " | " + statusCode);
    }
    
    /**
     * Log an API request with method, path, status, and additional info
     */
    public static void logApiRequest(String method, String path, int statusCode, String info) {
        if (info != null && !info.isEmpty()) {
            logApi(method + " " + path + " | " + statusCode + " | " + info);
        } else {
            logApi(method + " " + path + " | " + statusCode);
        }
    }
    
    /**
     * Ensure scan log writer is open and pointing to today's file
     */
    private static void ensureScanLogWriter() throws IOException {
        String today = LocalDate.now().format(DATE_FORMAT);
        
        if (!today.equals(currentScanLogDate)) {
            // Date changed, close old writer and open new one
            if (scanLogWriter != null) {
                scanLogWriter.close();
            }
            
            String filename = SCAN_LOG_PREFIX + today + LOG_SUFFIX;
            scanLogWriter = new PrintWriter(new FileWriter(filename, true)); // append mode
            currentScanLogDate = today;
            System.out.println("[LogService] Opened scan log: " + filename);
        }
    }
    
    /**
     * Ensure API log writer is open and pointing to today's file
     */
    private static void ensureApiLogWriter() throws IOException {
        String today = LocalDate.now().format(DATE_FORMAT);
        
        if (!today.equals(currentApiLogDate)) {
            // Date changed, close old writer and open new one
            if (apiLogWriter != null) {
                apiLogWriter.close();
            }
            
            String filename = API_LOG_PREFIX + today + LOG_SUFFIX;
            apiLogWriter = new PrintWriter(new FileWriter(filename, true)); // append mode
            currentApiLogDate = today;
            System.out.println("[LogService] Opened API log: " + filename);
        }
    }
    
    /**
     * Close all log writers (call on shutdown)
     */
    public static void shutdown() {
        synchronized (scanLock) {
            if (scanLogWriter != null) {
                scanLogWriter.close();
                scanLogWriter = null;
                currentScanLogDate = "";
            }
        }
        synchronized (apiLock) {
            if (apiLogWriter != null) {
                apiLogWriter.close();
                apiLogWriter = null;
                currentApiLogDate = "";
            }
        }
        System.out.println("[LogService] Log writers closed");
    }
}
