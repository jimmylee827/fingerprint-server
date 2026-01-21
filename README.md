# 🔐 Fingerprint REST API Server

A powerful, production-ready **biometric fingerprint authentication server** built for seamless integration with your access control, time attendance, and identity verification systems.

[![Platform](https://img.shields.io/badge/Platform-Windows-blue.svg)](https://www.microsoft.com/windows)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](#license)

---

## ✨ Features

### 🎯 Core Capabilities
- **REST API Interface** - Modern JSON-based API for easy integration with any platform
- **Real-time Fingerprint Detection** - Background identification with instant webhook notifications
- **Secure Authentication** - Bearer token authentication for all API endpoints
- **Duplicate Prevention** - Automatic detection of duplicate fingerprints AND duplicate names (case-insensitive)

### 📦 Data Management
- **Export/Import** - Portable fingerprint data format for backup and migration
- **Structured Storage** - Each fingerprint stored with metadata and captured BMP images
- **Daily Log Rotation** - Automatic log files for API requests and scan events

### 🔔 Webhook Integration
- **Identified Scans** - Instant POST notification when a registered user is detected
- **Unidentified Scans** - Alert webhook when an unknown fingerprint is scanned
- **Configurable Endpoint** - Dynamic webhook URL with Bearer token authentication

### 🛡️ Enterprise Ready
- **ZKTeco SDK Integration** - Professional-grade fingerprint sensor support
- **High Accuracy Matching** - Industry-standard fingerprint template matching
- **Concurrent Access** - Thread-safe operations for multi-client environments

---

## 📋 Requirements

| Component | Version | Notes |
|-----------|---------|-------|
| **Java Runtime** | 21+ | [Download OpenJDK](https://adoptium.net/) |
| **Windows** | 10/11 | 64-bit recommended |
| **ZKTeco Fingerprint Reader** | - | USB connection required |
| **ZKTeco SDK** | - | Drivers must be installed |

---

## 🚀 Quick Start

### 1. Install ZKTeco Drivers

Run `setup.exe` to install the fingerprint reader drivers, or install them manually from ZKTeco's website.

### 2. Configure Environment

Create or edit `.env` file in the project root:

```env
# Fingerprint REST API Server - Environment Configuration

# Internal API Key (required for all API requests)
INTERNAL_KEY=your-secure-api-key-here

# Webhook Base URL (without endpoint path)
# Events will be sent to:
#   {WEBHOOK_URL}/fingerprint-detected     - when a registered fingerprint is scanned
#   {WEBHOOK_URL}/fingerprint-unidentified - when an unknown fingerprint is scanned
WEBHOOK_URL=https://your-server.com

# External Webhook Bearer Token (sent to webhook endpoint)
WEBHOOK_EXTERNAL_KEY=your-webhook-bearer-token-here
```

> 💡 **Tip:** Generate secure keys using a password generator. Keys should be at least 32 characters.
> 
> 💡 **Note:** A `.env.example` file is provided as a template.

### 3. Configure Server Settings

Edit `config.json` in the project root:

```json
{
  "serverPort": 8080
}
```

| Setting | Description | Default |
|---------|-------------|---------|
| `serverPort` | HTTP port for the REST API | `8080` |

> 💡 **Note:** Webhook URL is now configured in `.env` file (`WEBHOOK_URL`), not in `config.json`.

### 4. Start the Server

Double-click `start.bat` or run from command line:

```batch
start.bat
```

You should see:
```
============================================================
  Fingerprint REST API Server - Starting
============================================================
[FingerprintService] Device opened successfully
[FingerprintService] Initialized with 0 registered fingerprints
[Server] Fingerprint REST API Server running on port 8080
```

### 5. Register Your First Fingerprint

**Option A: Use the Registration Script**
```batch
register.bat
```

**Option B: Use the API directly**
```bash
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{"name": "John Doe", "role": "Admin"}'
```

---

## 📚 API Reference

### Authentication

All API endpoints require Bearer token authentication:

```
Authorization: Bearer {INTERNAL_KEY}
```

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/status` | Get server status |
| `POST` | `/api/register` | Register new fingerprint |
| `GET` | `/api/fingerprints` | List all fingerprints |
| `GET` | `/api/fingerprints/:id` | Get fingerprint by ID |
| `DELETE` | `/api/fingerprints/:id` | Delete fingerprint |
| `GET` | `/api/fingerprints/:id/export` | Export fingerprint data |
| `POST` | `/api/fingerprints/import` | Import fingerprint data |
| `GET` | `/api/config` | Get server configuration |
| `PUT` | `/api/config` | Update configuration |
| `PUT` | `/api/config/webhook` | Update webhook URL |
| `POST` | `/api/config/webhook/test` | Test webhook connectivity |
| `POST` | `/api/identification/start` | Start background scanning |
| `POST` | `/api/identification/stop` | Stop background scanning |

### Register a Fingerprint

```http
POST /api/register
Content-Type: application/json
Authorization: Bearer {INTERNAL_KEY}

{
    "name": "John Doe",
    "role": "Admin",
    "timeout": 30
}
```

**Response (201 Created):**
```json
{
    "id": "5497b689-a712-4204-beb6-7dd901e48570",
    "name": "John Doe",
    "role": "Admin",
    "createdAt": "2026-01-22T10:30:00Z"
}
```

**Roles:** `Admin` or `User`

**Timeout:** Seconds to wait for fingerprint captures (default: 30)

### Webhook Payload

When a registered fingerprint is detected, the server sends a POST to `{WEBHOOK_URL}/fingerprint-detected`:

```http
POST {webhookUrl}
Authorization: Bearer {WEBHOOK_EXTERNAL_KEY}
Content-Type: application/json

{
    "event": "fingerprint_detected",
    "userId": "5497b689-a712-4204-beb6-7dd901e48570",
    "name": "John Doe",
    "role": "Admin",
    "timestamp": "2026-01-22T10:30:00Z",
    "score": 85
}
```

When an unidentified fingerprint is detected, the server sends a POST to `{WEBHOOK_URL}/fingerprint-unidentified`:

```http
POST {webhookUrl}/fingerprint-unidentified
Authorization: Bearer {WEBHOOK_EXTERNAL_KEY}
Content-Type: application/json

{
    "event": "fingerprint_unidentified",
    "userId": null,
    "name": null,
    "role": null,
    "timestamp": "2026-01-22T10:31:00Z",
    "score": 0
}
```

---

## 📁 Project Structure

```
fingerprint-server/
├── 📄 config.json              # Server configuration (port only)
├── 📄 .env                     # API keys & webhook URL (DO NOT COMMIT!)
├── 📄 .env.example             # Template for .env file
├── 📄 start.bat                # Start the server
├── 📄 register.bat             # Quick registration script
├── 📄 api-tests.http           # VS Code REST Client tests
│
├── 📁 data/
│   ├── 📄 fingerprints.json    # Master registration list
│   └── 📁 fingerprints/
│       └── 📁 {uuid}/
│           ├── 📄 user_detail.json
│           ├── 🖼️ capture_1.bmp
│           ├── 🖼️ capture_2.bmp
│           └── 🖼️ capture_3.bmp
│
├── 📁 lib/                     # Java dependencies
├── 📁 src/                     # Source code
├── 📁 bin/                     # Compiled classes
│
├── 📄 log_api_YYYY-MM-DD.log   # API request logs
└── 📄 log_scan_YYYY-MM-DD.log  # Fingerprint scan logs
```

---

## 📊 Logging

### API Log (`log_api_YYYY-MM-DD.log`)

Tracks all API requests:

```
[2026-01-22T10:30:00.001] GET /api/status | 200
[2026-01-22T10:30:15.234] POST /api/register | 201
[2026-01-22T10:30:20.567] POST /api/register | 409 | Duplicate name: John Doe
```

### Scan Log (`log_scan_YYYY-MM-DD.log`)

Tracks fingerprint scans and webhook activity:

```
[2026-01-22T10:30:45.123] IDENTIFIED | name=John Doe | score=85
[2026-01-22T10:31:02.456] UNIDENTIFIED | no match found
[2026-01-22T10:31:02.789] WEBHOOK | POST https://example.com/webhook | 200 OK
```

---

## 🛠️ Troubleshooting

### Server won't start

1. **Check Java version:** `java -version` (must be 21+)
2. **Check fingerprint reader connection:** Ensure USB is connected and drivers installed
3. **Check port availability:** Ensure port 8080 (or configured port) is not in use

### Fingerprint reader not detected

1. Run `setup.exe` to install drivers
2. Reconnect the USB device
3. Check Device Manager for ZKTeco device

### Webhook not receiving events

1. Check `.env` has correct `WEBHOOK_URL` (base URL only, no path)
2. Test with `/api/config/webhook/test`
3. Check `log_scan_*.log` for webhook errors
4. Ensure your webhook endpoint accepts POST requests at `/fingerprint-detected`

### Registration fails with "Duplicate"

- **Duplicate fingerprint:** The fingerprint is already registered to another user
- **Duplicate name:** Another user exists with the same name (case-insensitive)

---

## 🔒 Security Best Practices

1. **Keep `.env` private** - Never commit API keys to version control
2. **Use strong API keys** - Generate random keys of at least 32 characters
3. **Use HTTPS** - Deploy behind a reverse proxy with SSL for production
4. **Restrict network access** - Limit access to trusted networks/IPs
5. **Regular backups** - Export fingerprint data regularly

---

## 📄 License

This software is proprietary. All rights reserved.

---

## 🤝 Support

For technical support, please contact your system administrator or the development team.

---

<div align="center">

**Built with ❤️ for secure biometric authentication**

</div>
