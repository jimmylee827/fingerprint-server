# register-api.ps1 - Called by register.bat
param(
    [string]$Port,
    [string]$ApiKey,
    [string]$Name,
    [string]$Role
)

$headers = @{
    'Content-Type' = 'application/json'
    'Authorization' = "Bearer $ApiKey"
}

$body = @{
    'name' = $Name
    'role' = $Role
    'timeout' = 60
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "http://localhost:$Port/api/register" -Method POST -Headers $headers -Body $body -TimeoutSec 120 -UseBasicParsing
    
    Write-Host ''
    Write-Host '============================================================'
    Write-Host '  Registration Result'
    Write-Host '============================================================'
    
    if ($response.StatusCode -eq 201) {
        Write-Host '  [SUCCESS] Fingerprint registered successfully!' -ForegroundColor Green
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
    Write-Host '  Registration Failed'
    Write-Host '============================================================'
    
    $statusCode = $_.Exception.Response.StatusCode.value__
    
    if ($statusCode -eq 409) {
        Write-Host '  [CONFLICT] Name or fingerprint already exists' -ForegroundColor Yellow
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errorBody = $reader.ReadToEnd()
            Write-Host "  Details: $errorBody"
        } catch {}
    } elseif ($statusCode -eq 401) {
        Write-Host '  [UNAUTHORIZED] Invalid API key' -ForegroundColor Red
    } elseif ($statusCode -eq 400) {
        Write-Host '  [BAD REQUEST] Invalid input' -ForegroundColor Red
    } else {
        Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    }
    Write-Host '============================================================'
}
