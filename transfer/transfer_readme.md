# 📁 Fingerprint Transfer Folder

This folder is used for fingerprint data transfer between servers.

## 📤 Exporting Fingerprints

Run `export.bat` in the parent directory to export a fingerprint. The exported file will be saved here with a timestamped filename:

```
fp_transfer_YYYYMMDD_HHmmss_fff.json
```

**Example:** `fp_transfer_20260122_143052_123.json`

## 📥 Importing Fingerprints

Run `import.bat` in the parent directory to import a fingerprint. The script will:

1. List all transfer files in this folder with index numbers
2. Show the name and role for each file
3. Prompt you to select a file by number
4. Import the selected fingerprint

## 📄 File Format

Each transfer file is a JSON document containing:

```json
{
    "exportedAt": "2026-01-22T14:30:52Z",
    "fingerprint": {
        "id": "uuid-here",
        "name": "John Doe",
        "role": "Admin",
        "createdAt": "2026-01-20T09:00:00Z",
        "templateBase64": "..."
    }
}
```

## ⚠️ Important Notes

- **Do not modify** the JSON files manually
- Transfer files in this folder are **not tracked by git** (except this readme)
- Keep backup copies of important transfer files externally
- Files can be copied to another server's `transfer` folder for import

## 🗑️ Cleanup

You can safely delete old transfer files from this folder after successful import.

---

*This folder is auto-created by the export script if it doesn't exist.*
