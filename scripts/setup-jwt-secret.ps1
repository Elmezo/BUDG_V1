# PowerShell script to set JWT_SECRET_KEY environment variable
# Run this script to configure JWT_SECRET_KEY for the BUDG Platform

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "BUDG Platform - JWT Secret Key Setup" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Check if JWT_SECRET_KEY already exists
$existingKey = [System.Environment]::GetEnvironmentVariable("JWT_SECRET_KEY", "User")
if ($existingKey) {
    Write-Host "JWT_SECRET_KEY already exists in User environment variables." -ForegroundColor Yellow
    Write-Host "Current value: $($existingKey.Substring(0, [Math]::Min(20, $existingKey.Length)))..." -ForegroundColor Yellow
    $overwrite = Read-Host "Do you want to overwrite it? (y/N)"
    if ($overwrite -ne "y" -and $overwrite -ne "Y") {
        Write-Host "Keeping existing JWT_SECRET_KEY." -ForegroundColor Green
        exit 0
    }
}

# Generate a secure random key (64 bytes = 512 bits)
Write-Host "Generating secure random key..." -ForegroundColor Green
$bytes = New-Object byte[] 64
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$secretKey = [Convert]::ToBase64String($bytes)

# Set environment variable for current user (persistent)
[System.Environment]::SetEnvironmentVariable("JWT_SECRET_KEY", $secretKey, "User")

# Also set for current session (immediate effect)
$env:JWT_SECRET_KEY = $secretKey

Write-Host ""
Write-Host "✓ JWT_SECRET_KEY has been set successfully!" -ForegroundColor Green
Write-Host ""
Write-Host "Key length: $($secretKey.Length) characters" -ForegroundColor Cyan
Write-Host "Key preview: $($secretKey.Substring(0, 20))..." -ForegroundColor Gray
Write-Host ""
Write-Host "Note:" -ForegroundColor Yellow
Write-Host "  - The key is saved in User environment variables (persistent)" -ForegroundColor Yellow
Write-Host "  - You may need to restart your IDE/application server for it to take effect" -ForegroundColor Yellow
Write-Host "  - Keep this key secret and never commit it to version control" -ForegroundColor Yellow
Write-Host ""
Write-Host "To verify, run: echo `$env:JWT_SECRET_KEY" -ForegroundColor Cyan
Write-Host ""

