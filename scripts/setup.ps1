# BUDG Platform - Automatic Setup Script
# This script sets up the project automatically when moving to a new machine
# Run this once: .\setup.ps1
# Usage: .\setup.ps1 [-Auto] [-DbUrl "url"] [-DbUser "user"] [-DbPass "pass"]

param(
    [switch]$Auto,
    [string]$DbUrl = "",
    [string]$DbUser = "",
    [string]$DbPass = ""
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "BUDG Platform - Automatic Setup" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Check if .env already exists
$envExists = Test-Path ".env"
if ($envExists -and -not $Auto) {
    Write-Host ".env file already exists." -ForegroundColor Yellow
    $overwrite = Read-Host "Do you want to recreate it? (y/N)"
    if ($overwrite -ne "y" -and $overwrite -ne "Y") {
        Write-Host "Keeping existing .env file." -ForegroundColor Green
        exit 0
    }
    Remove-Item ".env" -Force
    $envExists = $false
}

Write-Host "Step 1: Creating .env file from env.example..." -ForegroundColor Green
if (Test-Path "env.example") {
    Copy-Item "env.example" ".env"
    Write-Host "✓ Created .env file" -ForegroundColor Green
} else {
    Write-Host "✗ Error: env.example not found!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Step 2: Generating secure JWT_SECRET_KEY..." -ForegroundColor Green
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 48
$rng.GetBytes($bytes)
$jwtKey = [Convert]::ToBase64String($bytes)

# Update JWT_SECRET_KEY in .env file
$content = Get-Content ".env"
$content = $content -replace 'JWT_SECRET_KEY=.*', "JWT_SECRET_KEY=$jwtKey"
$content | Set-Content ".env"
Write-Host "✓ Generated and set JWT_SECRET_KEY (64 bytes)" -ForegroundColor Green

Write-Host ""
Write-Host "Step 3: Configuring database connection..." -ForegroundColor Green

# Use provided parameters or defaults, or ask user
if ($Auto -or -not [string]::IsNullOrWhiteSpace($DbUrl)) {
    if ([string]::IsNullOrWhiteSpace($DbUrl)) {
        $dbUrl = "jdbc:mysql://localhost:3306/project"
    } else {
        $dbUrl = $DbUrl
    }
    Write-Host "Using database URL: $dbUrl" -ForegroundColor Gray
} else {
    Write-Host "Please enter database configuration (or press Enter to use defaults):" -ForegroundColor Yellow
    $inputUrl = Read-Host "Database URL [jdbc:mysql://localhost:3306/project]"
    if ([string]::IsNullOrWhiteSpace($inputUrl)) {
        $dbUrl = "jdbc:mysql://localhost:3306/project"
    } else {
        $dbUrl = $inputUrl
    }
}

if ($Auto -or -not [string]::IsNullOrWhiteSpace($DbUser)) {
    if ([string]::IsNullOrWhiteSpace($DbUser)) {
        $dbUser = "root"
    } else {
        $dbUser = $DbUser
    }
    Write-Host "Using database username: $dbUser" -ForegroundColor Gray
} else {
    $inputUser = Read-Host "Database Username [root]"
    if ([string]::IsNullOrWhiteSpace($inputUser)) {
        $dbUser = "root"
    } else {
        $dbUser = $inputUser
    }
}

if ($Auto -or -not [string]::IsNullOrWhiteSpace($DbPass)) {
    if ([string]::IsNullOrWhiteSpace($DbPass)) {
        $dbPass = ""
    } else {
        $dbPass = $DbPass
    }
    Write-Host "Using database password: ***" -ForegroundColor Gray
} else {
    $inputPass = Read-Host "Database Password []"
    if ([string]::IsNullOrWhiteSpace($inputPass)) {
        $dbPass = ""
    } else {
        $dbPass = $inputPass
    }
}

# Update database settings in .env
$content = Get-Content ".env"
$content = $content -replace 'DB_URL=.*', "DB_URL=$dbUrl"
$content = $content -replace 'DB_USERNAME=.*', "DB_USERNAME=$dbUser"
$content = $content -replace 'DB_PASSWORD=.*', "DB_PASSWORD=$dbPass"
$content | Set-Content ".env"
Write-Host "✓ Database configuration updated" -ForegroundColor Green

Write-Host ""
Write-Host "Step 4: Copying .env to src/main/resources/..." -ForegroundColor Green
if (Test-Path "src/main/resources") {
    Copy-Item ".env" "src/main/resources/.env" -Force
    Write-Host "✓ Copied .env to src/main/resources/" -ForegroundColor Green
} else {
    Write-Host "⚠ Warning: src/main/resources/ directory not found" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Setup completed successfully!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "The project is now ready to run." -ForegroundColor Green
Write-Host ""

if ($Auto) {
    Write-Host "Note: Auto mode used default values." -ForegroundColor Yellow
    Write-Host "You may want to review and update .env file if needed." -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "Important:" -ForegroundColor Yellow
Write-Host "  - .env file contains sensitive data" -ForegroundColor Yellow
Write-Host "  - Never commit .env to version control" -ForegroundColor Yellow
Write-Host "  - Keep your JWT_SECRET_KEY secure" -ForegroundColor Yellow
Write-Host ""
Write-Host "Quick start:" -ForegroundColor Cyan
Write-Host "  Run: .\setup.ps1 -Auto  (for fully automatic setup)" -ForegroundColor Gray
Write-Host ""

