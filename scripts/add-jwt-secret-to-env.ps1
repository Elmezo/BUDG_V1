# PowerShell script to add JWT_SECRET_KEY to .env file
# This script finds the .env file that the application is using and adds JWT_SECRET_KEY to it
# Usage: .\scripts\add-jwt-secret-to-env.ps1

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "BUDG Platform - Add JWT Secret Key to .env" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Function to find .env file in various locations (similar to EnvFileLoader logic)
function Find-EnvFile {
    $searchPaths = @()
    
    # 1. Current working directory
    $searchPaths += ".env"
    
    # 2. User directory (where application was started from)
    $userDir = [System.Environment]::GetEnvironmentVariable("USERDIR")
    if (-not $userDir) {
        $userDir = $PWD.Path
    }
    if ($userDir) {
        $searchPaths += Join-Path $userDir ".env"
    }
    
    # 3. Parent directories
    $currentDir = Get-Location
    $searchPaths += Join-Path (Split-Path $currentDir) ".env"
    $searchPaths += Join-Path (Split-Path (Split-Path $currentDir)) ".env"
    $searchPaths += Join-Path (Split-Path (Split-Path (Split-Path $currentDir))) ".env"
    
    # 4. Resources directory
    $searchPaths += "src/main/resources/.env"
    $searchPaths += "resources/.env"
    
    # 5. Project root (look for pom.xml or build.gradle)
    $current = $currentDir
    for ($i = 0; $i -lt 5; $i++) {
        $pomFile = Join-Path $current "pom.xml"
        $gradleFile = Join-Path $current "build.gradle"
        if ((Test-Path $pomFile) -or (Test-Path $gradleFile)) {
            $envInRoot = Join-Path $current ".env"
            if (Test-Path $envInRoot) {
                $searchPaths += $envInRoot
            }
            $resourcesEnv = Join-Path $current "src/main/resources/.env"
            if (Test-Path $resourcesEnv) {
                $searchPaths += $resourcesEnv
            }
        }
        $parent = Split-Path $current -ErrorAction SilentlyContinue
        if (-not $parent -or $parent -eq $current) {
            break
        }
        $current = $parent
    }
    
    # 6. CATALINA_BASE and CATALINA_HOME (Tomcat deployment)
    $catalinaBase = [System.Environment]::GetEnvironmentVariable("CATALINA_BASE")
    if ($catalinaBase) {
        $searchPaths += Join-Path $catalinaBase ".env"
        $searchPaths += Join-Path $catalinaBase "bin\.env"
    }
    $catalinaHome = [System.Environment]::GetEnvironmentVariable("CATALINA_HOME")
    if ($catalinaHome) {
        $searchPaths += Join-Path $catalinaHome ".env"
        $searchPaths += Join-Path $catalinaHome "bin\.env"
    }
    
    # 7. Common Tomcat locations
    $commonTomcatPaths = @(
        "C:\Program Files\Apache Software Foundation\Tomcat 11.0\bin\.env",
        "C:\Program Files\Apache Software Foundation\Tomcat 10.0\bin\.env",
        "C:\Program Files\Apache Software Foundation\Tomcat 9.0\bin\.env",
        "C:\Program Files (x86)\Apache Software Foundation\Tomcat 11.0\bin\.env",
        "C:\Program Files (x86)\Apache Software Foundation\Tomcat 10.0\bin\.env",
        "C:\Program Files (x86)\Apache Software Foundation\Tomcat 9.0\bin\.env"
    )
    $searchPaths += $commonTomcatPaths
    
    # Search all paths
    foreach ($path in $searchPaths) {
        if (Test-Path $path -PathType Leaf) {
            $fullPath = (Resolve-Path $path).Path
            Write-Host "Found .env file at: $fullPath" -ForegroundColor Green
            return $fullPath
        }
    }
    
    return $null
}

# Find .env file
Write-Host "Searching for .env file..." -ForegroundColor Yellow
$envFilePath = Find-EnvFile

if (-not $envFilePath) {
    Write-Host "Error: .env file not found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please ensure a .env file exists in one of these locations:" -ForegroundColor Yellow
    Write-Host "  - Project root directory" -ForegroundColor Gray
    Write-Host "  - src/main/resources/.env" -ForegroundColor Gray
    Write-Host "  - Tomcat bin directory" -ForegroundColor Gray
    Write-Host ""
    Write-Host "You can create one by copying env.example:" -ForegroundColor Yellow
    Write-Host "  Copy-Item scripts\env.example .env" -ForegroundColor Gray
    exit 1
}

Write-Host ""
Write-Host "Step 1: Checking if JWT_SECRET_KEY already exists..." -ForegroundColor Green
$content = Get-Content $envFilePath -Raw
$hasJwtKey = $content -match '^\s*JWT_SECRET_KEY\s*='

if ($hasJwtKey) {
    Write-Host "JWT_SECRET_KEY already exists in .env file." -ForegroundColor Yellow
    $overwrite = Read-Host "Do you want to overwrite it? (y/N)"
    if ($overwrite -ne "y" -and $overwrite -ne "Y") {
        Write-Host "Keeping existing JWT_SECRET_KEY." -ForegroundColor Green
        exit 0
    }
}

Write-Host ""
Write-Host "Step 2: Generating secure JWT secret key..." -ForegroundColor Green
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 64
$rng.GetBytes($bytes)
$jwtKey = [Convert]::ToBase64String($bytes)

Write-Host "Generated secure key (64 bytes = 512 bits)" -ForegroundColor Green

Write-Host ""
Write-Host "Step 3: Updating .env file..." -ForegroundColor Green

# Read all lines
$lines = Get-Content $envFilePath
$updatedLines = @()
$found = $false

foreach ($line in $lines) {
    $trimmed = $line.Trim()
    
    # Skip empty lines and comments
    if ($trimmed -eq "" -or $trimmed.StartsWith("#")) {
        $updatedLines += $line
        continue
    }
    
    # Check if this line contains JWT_SECRET_KEY
    if ($trimmed -match '^\s*JWT_SECRET_KEY\s*=') {
        # Update the existing line
        $updatedLines += "JWT_SECRET_KEY=$jwtKey"
        $found = $true
    } else {
        # Keep the original line
        $updatedLines += $line
    }
}

# If key not found, add it at the end
if (-not $found) {
    # Add a blank line if the file doesn't end with one
    if ($updatedLines.Count -gt 0 -and $updatedLines[-1] -ne "") {
        $updatedLines += ""
    }
    $updatedLines += "# JWT Secret Key (REQUIRED)"
    $updatedLines += "JWT_SECRET_KEY=$jwtKey"
}

# Write back to file
$updatedLines | Set-Content $envFilePath -Encoding UTF8

Write-Host "Updated .env file: $envFilePath" -ForegroundColor Green
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "Setup completed successfully!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "JWT_SECRET_KEY has been added to:" -ForegroundColor Green
Write-Host "  $envFilePath" -ForegroundColor Gray
Write-Host ""
Write-Host "Key length: $($jwtKey.Length) characters" -ForegroundColor Cyan
Write-Host "Key preview: $($jwtKey.Substring(0, [Math]::Min(20, $jwtKey.Length)))..." -ForegroundColor Gray
Write-Host ""
Write-Host "Important:" -ForegroundColor Yellow
Write-Host "  - You may need to restart your application server for the changes to take effect" -ForegroundColor Yellow
Write-Host "  - Keep this key secret and never commit it to version control" -ForegroundColor Yellow
Write-Host ""

