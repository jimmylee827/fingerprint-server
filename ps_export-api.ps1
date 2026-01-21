# ps_export-api.ps1 - Called by RUN_export.bat
param(
    [string]$Port,
    [string]$ApiKey,
    [string]$ExportId
)

$headers = @{
    'Content-Type' = 'application/json'
    'Authorization' = "Bearer $ApiKey"
}

# Ensure transfer folder exists
$transferFolder = Join-Path $PSScriptRoot "transfer"
if (-not (Test-Path $transferFolder)) {
    New-Item -ItemType Directory -Path $transferFolder -Force | Out-Null
}

try {
    $response = Invoke-WebRequest -Uri "http://localhost:$Port/api/fingerprints/$ExportId/export" -Method GET -Headers $headers -TimeoutSec 30 -UseBasicParsing
    
    if ($response.StatusCode -eq 200) {
        # Generate timestamped filename: fp_transfer_YYYYMMDD_HHmmss_fff.json
        $timestamp = Get-Date -Format "yyyyMMdd_HHmmss_fff"
        $fileName = "fp_transfer_$timestamp.json"
        $filePath = Join-Path $transferFolder $fileName
        
        # Save to file
        $response.Content | Out-File -FilePath $filePath -Encoding UTF8 -Force
        
        Write-Host ''
        Write-Host '============================================================'
        Write-Host '  Export Result'
        Write-Host '============================================================'
        Write-Host '  [SUCCESS] Fingerprint exported successfully!' -ForegroundColor Green
        Write-Host "  File: transfer\$fileName"
        Write-Host ''
        
        # Parse and show details
        $json = $response.Content | ConvertFrom-Json
        Write-Host "  Name: $($json.fingerprint.name)"
        Write-Host "  Role: $($json.fingerprint.role)"
        Write-Host "  Exported At: $($json.exportedAt)"
        Write-Host '============================================================'
    } else {
        Write-Host "  Status: $($response.StatusCode)"
        Write-Host "  Response: $($response.Content)"
    }
    
} catch {
    Write-Host ''
    Write-Host '============================================================'
    Write-Host '  Export Failed'
    Write-Host '============================================================'
    
    $statusCode = $_.Exception.Response.StatusCode.value__
    
    if ($statusCode -eq 404) {
        Write-Host '  [NOT FOUND] Fingerprint ID not found' -ForegroundColor Yellow
    } elseif ($statusCode -eq 401) {
        Write-Host '  [UNAUTHORIZED] Invalid API key' -ForegroundColor Red
    } else {
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host '============================================================'
    exit 1
}
