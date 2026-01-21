package com.fingerprint.server;

import com.fingerprint.model.Config;
import com.fingerprint.model.Registration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * StorageService - JSON file-based persistence for fingerprints and config
 */
public class StorageService {
    private static final String DATA_DIR = "./data";
    private static final String FINGERPRINTS_DIR = DATA_DIR + "/fingerprints";
    private static final String FINGERPRINTS_FILE = DATA_DIR + "/fingerprints.json";
    private static final String CONFIG_FILE = "./config.json"; // Top level

    private final Gson gson;
    private List<Registration> registrations;
    private Config config;

    public StorageService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.registrations = new ArrayList<>();
        this.config = new Config();
        
        ensureDataDirectory();
        loadAll();
    }

    // ==================== Directory Management ====================

    private void ensureDataDirectory() {
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            if (dataDir.mkdirs()) {
                System.out.println("[StorageService] Created data directory: " + dataDir.getAbsolutePath());
            } else {
                System.err.println("[StorageService] Failed to create data directory!");
            }
        }
        
        File fingerprintsDir = new File(FINGERPRINTS_DIR);
        if (!fingerprintsDir.exists()) {
            if (fingerprintsDir.mkdirs()) {
                System.out.println("[StorageService] Created fingerprints directory: " + fingerprintsDir.getAbsolutePath());
            }
        }
    }

    /**
     * Get the directory path for a specific fingerprint's data
     */
    public String getFingerprintDir(String id) {
        return FINGERPRINTS_DIR + "/" + id;
    }

    /**
     * Ensure the fingerprint directory exists and return its path
     */
    public String ensureFingerprintDir(String id) {
        String dirPath = getFingerprintDir(id);
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dirPath;
    }

    /**
     * Save user_detail.json for a fingerprint
     */
    public void saveUserDetail(Registration registration) {
        String dirPath = ensureFingerprintDir(registration.getId());
        File userDetailFile = new File(dirPath + "/user_detail.json");
        
        try (Writer writer = new FileWriter(userDetailFile)) {
            gson.toJson(registration, writer);
            System.out.println("[StorageService] Saved user_detail.json for: " + registration.getId());
        } catch (IOException e) {
            System.err.println("[StorageService] Error saving user_detail.json: " + e.getMessage());
        }
    }

    // ==================== Load Operations ====================

    private void loadAll() {
        loadRegistrations();
        loadConfig();
    }

    private void loadRegistrations() {
        File file = new File(FINGERPRINTS_FILE);
        if (!file.exists()) {
            System.out.println("[StorageService] No fingerprints file found, starting fresh");
            registrations = new ArrayList<>();
            saveRegistrations();
            return;
        }

        try (Reader reader = new FileReader(file)) {
            FingerprintsWrapper wrapper = gson.fromJson(reader, FingerprintsWrapper.class);
            if (wrapper != null && wrapper.registrations != null) {
                registrations = wrapper.registrations;
                System.out.println("[StorageService] Loaded " + registrations.size() + " registrations");
            } else {
                registrations = new ArrayList<>();
            }
        } catch (Exception e) {
            System.err.println("[StorageService] Error loading registrations: " + e.getMessage());
            registrations = new ArrayList<>();
        }
    }

    private void loadConfig() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            System.out.println("[StorageService] No config file found, using defaults");
            config = new Config();
            saveConfig();
            return;
        }

        try (Reader reader = new FileReader(file)) {
            config = gson.fromJson(reader, Config.class);
            if (config == null) {
                config = new Config();
            }
            System.out.println("[StorageService] Loaded config: " + config);
        } catch (Exception e) {
            System.err.println("[StorageService] Error loading config: " + e.getMessage());
            config = new Config();
        }
    }

    // ==================== Save Operations ====================

    private synchronized void saveRegistrations() {
        try (Writer writer = new FileWriter(FINGERPRINTS_FILE)) {
            FingerprintsWrapper wrapper = new FingerprintsWrapper();
            wrapper.registrations = registrations;
            gson.toJson(wrapper, writer);
        } catch (IOException e) {
            System.err.println("[StorageService] Error saving registrations: " + e.getMessage());
        }
    }

    public synchronized void saveConfig() {
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            System.err.println("[StorageService] Error saving config: " + e.getMessage());
        }
    }

    // ==================== Registration CRUD ====================

    /**
     * Check if a name already exists (case-insensitive)
     */
    public boolean isNameExists(String name) {
        if (name == null) return false;
        return registrations.stream()
                .anyMatch(r -> r.getName().equalsIgnoreCase(name));
    }

    /**
     * Get existing registration by name (case-insensitive)
     */
    public Optional<Registration> getRegistrationByName(String name) {
        if (name == null) return Optional.empty();
        return registrations.stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public synchronized String addRegistration(String name, String role, String templateBase64) {
        // Check for duplicate name (case-insensitive)
        if (isNameExists(name)) {
            System.out.println("[StorageService] Duplicate name rejected: " + name);
            return null;
        }
        
        String id = UUID.randomUUID().toString();
        Registration registration = new Registration(id, name, role, templateBase64);
        registrations.add(registration);
        saveRegistrations();
        saveUserDetail(registration);
        System.out.println("[StorageService] Added registration: " + registration);
        return id;
    }

    public synchronized String addRegistrationWithPregeneratedId(String id, String name, String role, String templateBase64) {
        // Check for duplicate name (case-insensitive)
        if (isNameExists(name)) {
            System.out.println("[StorageService] Duplicate name rejected: " + name);
            return null;
        }
        
        Registration registration = new Registration(id, name, role, templateBase64);
        registrations.add(registration);
        saveRegistrations();
        saveUserDetail(registration);
        System.out.println("[StorageService] Added registration with pre-generated ID: " + registration);
        return id;
    }

    public synchronized Registration addRegistrationWithId(String id, String name, String role, 
                                                           String templateBase64, String createdAt) {
        // Check for duplicate name (case-insensitive)
        if (isNameExists(name)) {
            System.out.println("[StorageService] Duplicate name rejected: " + name);
            return null;
        }
        
        Registration registration = new Registration(id, name, role, templateBase64);
        if (createdAt != null) {
            registration.setCreatedAt(createdAt);
        }
        registrations.add(registration);
        saveRegistrations();
        saveUserDetail(registration);
        System.out.println("[StorageService] Added registration with custom ID: " + registration);
        return registration;
    }

    public List<Registration> getAllRegistrations() {
        return new ArrayList<>(registrations);
    }

    public Optional<Registration> getRegistrationById(String id) {
        return registrations.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }

    public synchronized boolean deleteRegistration(String id) {
        boolean removed = registrations.removeIf(r -> r.getId().equals(id));
        if (removed) {
            saveRegistrations();
            // Also delete the fingerprint directory
            File fpDir = new File(getFingerprintDir(id));
            if (fpDir.exists()) {
                deleteDirectory(fpDir);
            }
            System.out.println("[StorageService] Deleted registration: " + id);
        }
        return removed;
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    public int getRegistrationCount() {
        return registrations.size();
    }

    // ==================== Config Operations ====================

    public Config getConfig() {
        return config;
    }

    public synchronized void updateConfig(Config newConfig) {
        this.config = newConfig;
        saveConfig();
        System.out.println("[StorageService] Updated config: " + config);
    }

    public int getServerPort() {
        return config.getServerPort();
    }

    // ==================== Helper Classes ====================

    private static class FingerprintsWrapper {
        List<Registration> registrations;
    }

    // ==================== ID Generation ====================

    public String generateNewId() {
        return UUID.randomUUID().toString();
    }
}
