package com.fingerprint.model;

/**
 * ExportData - Protocol for exporting/importing fingerprint data
 */
public class ExportData {
    private String version;
    private String exportedAt;
    private FingerprintData fingerprint;

    public ExportData() {
        this.version = "1.0";
    }

    public ExportData(String exportedAt, FingerprintData fingerprint) {
        this.version = "1.0";
        this.exportedAt = exportedAt;
        this.fingerprint = fingerprint;
    }

    // Getters and Setters
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getExportedAt() {
        return exportedAt;
    }

    public void setExportedAt(String exportedAt) {
        this.exportedAt = exportedAt;
    }

    public FingerprintData getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(FingerprintData fingerprint) {
        this.fingerprint = fingerprint;
    }

    /**
     * Nested class for fingerprint data in export
     */
    public static class FingerprintData {
        private String name;
        private String role;
        private String templateBase64;
        private String originalCreatedAt;

        public FingerprintData() {
        }

        public FingerprintData(String name, String role, String templateBase64, String originalCreatedAt) {
            this.name = name;
            this.role = role;
            this.templateBase64 = templateBase64;
            this.originalCreatedAt = originalCreatedAt;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getTemplateBase64() {
            return templateBase64;
        }

        public void setTemplateBase64(String templateBase64) {
            this.templateBase64 = templateBase64;
        }

        public String getOriginalCreatedAt() {
            return originalCreatedAt;
        }

        public void setOriginalCreatedAt(String originalCreatedAt) {
            this.originalCreatedAt = originalCreatedAt;
        }
    }
}
