package com.fingerprint.model;

import java.time.Instant;

/**
 * Registration - Represents a registered fingerprint with user info
 */
public class Registration {
    private String id;
    private String name;
    private String role;
    private String templateBase64;
    private String createdAt;

    public Registration() {
        // Default constructor for Gson
    }

    public Registration(String id, String name, String role, String templateBase64) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.templateBase64 = templateBase64;
        this.createdAt = Instant.now().toString();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Registration{id='" + id + "', name='" + name + "', role='" + role + "'}";
    }
}
