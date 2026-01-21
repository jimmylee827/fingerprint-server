package com.fingerprint.server;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * EnvLoader - Loads environment variables from .env file
 */
public class EnvLoader {
    private static final Map<String, String> envVars = new HashMap<>();
    private static boolean loaded = false;

    public static void load() {
        load(".env");
    }

    public static void load(String filename) {
        if (loaded) return;
        
        File envFile = new File(filename);
        if (!envFile.exists()) {
            System.out.println("[EnvLoader] No .env file found at: " + envFile.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Parse KEY=VALUE
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    
                    // Remove quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    
                    envVars.put(key, value);
                }
            }
            loaded = true;
            System.out.println("[EnvLoader] Loaded " + envVars.size() + " environment variables");
        } catch (IOException e) {
            System.err.println("[EnvLoader] Error loading .env file: " + e.getMessage());
        }
    }

    public static String get(String key) {
        // First check system environment, then .env file
        String value = System.getenv(key);
        if (value == null) {
            value = envVars.get(key);
        }
        return value;
    }

    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
