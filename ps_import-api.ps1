# ps_import-api.ps1 - Called by RUN_import.bat
param(
    [string]$Port,
    [string]$ApiKey,
    [string]$FilePath
)

$headers = @{
    'Content-Type' = 'application/json'
    'Authorization' = "Bearer $ApiKey"
}

# Read the export file
$body = Get-Content -Path $FilePath -Raw

try {
    $response = Invoke-WebRequest -Uri "http://localhost:$Port/api/fingerprints/import" -Method POST -Headers $headers -Body $body -TimeoutSec 30 -UseBasicParsing
    
    Write-Host ''
    Write-Host '============================================================'
    Write-Host '  Import Result'
    Write-Host '============================================================'
    
    if ($response.StatusCode -eq 201) {
        Write-Host '  [SUCCESS] Fingerprint imported successfully!' -ForegroundColor Green
        $json = $response.Content | ConvertFrom-Json
        Write-Host "  ID: $($json.id)"
        Write-Host "  Name: $($json.name)"
        Write-Host "  Role: $($json.role)"
    } else {
        Write-Host "  Status: $($response.StatusCode)"
        Write-Host "  Response: $($response.Content)"
    }
    Write-Host '============================================================'
    
} catch {
    Write-Host ''
    Write-Host '============================================================'
    Write-Host '  Import Failed'
    Write-Host '============================================================'
    
    $statusCode = $_.Exception.Response.StatusCode.value__
    
    if ($statusCode -eq 409) {
        Write-Host '  [CONFLICT] Duplicate detected!' -ForegroundColor Yellow
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errorBody = $reader.ReadToEnd() | ConvertFrom-Json
            Write-Host "  Reason: $($errorBody.error)"
            if ($errorBody.existingUserName) {
                Write-Host "  Existing User: $($errorBody.existingUserName)"
                Write-Host "  Existing ID: $($errorBody.existingUserId)"
            }
        } catch {
            Write-Host '  Name or fingerprint already exists'
        }
    } elseif ($statusCode -eq 401) {
        Write-Host '  [UNAUTHORIZED] Invalid API key' -ForegroundColor Red
    } elseif ($statusCode -eq 400) {
        Write-Host '  [BAD REQUEST] Invalid import file format' -ForegroundColor Red
    } else {
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host '============================================================'
    exit 1
}
