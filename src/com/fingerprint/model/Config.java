package com.fingerprint.model;

/**
 * Config - Server configuration (serverPort only, webhook URL is in .env)
 */
public class Config {
    private int serverPort;

    public Config() {
        // Defaults
        this.serverPort = 8080;
    }

    public Config(int serverPort) {
        this.serverPort = serverPort;
    }

    // Getters and Setters
    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    @Override
    public String toString() {
        return "Config{serverPort=" + serverPort + "}";
    }
}
