#requires -Version 5.1
<#
.SYNOPSIS
    Creates the permanent Feather Browser release keystore and its Base64 file.

.DESCRIPTION
    Prompts for a password without echoing it, creates a 2048-bit RSA keystore,
    verifies that it can be read, and writes the Base64 text used by the
    RELEASE_KEYSTORE_BASE64 GitHub Actions secret.

    The keystore and password are the app's update identity. Back them up.
    Losing them means existing users must uninstall before installing a build
    signed with a new key.

.EXAMPLE
    .\scripts\generate-release-keystore.ps1
#>
[CmdletBinding()]
param(
    [string]$KeystorePath = (Join-Path $HOME "feather-release.keystore"),
    [string]$Alias = "feather_release_key",
    [string]$DistinguishedName = "CN=Feather Browser, OU=Mobile, O=Feather, C=BD",
    [string]$Base64OutputPath
)

$ErrorActionPreference = "Stop"

function Find-Keytool {
    $candidates = @()

    if ($env:JAVA_HOME) {
        $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe")
    }

    $command = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($command) {
        $candidates += $command.Source
    }

    $searchRoots = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Microsoft",
        "C:\Program Files\Java",
        "$env:LOCALAPPDATA\Programs"
    )

    foreach ($root in $searchRoots) {
        if (-not (Test-Path $root)) { continue }
        Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                $candidates += (Join-Path $_.FullName "bin\keytool.exe")
            }
    }

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw @"
keytool.exe was not found.

Install a JDK 17 or 21, then run this script again. On Windows:
    winget install --id EclipseAdoptium.Temurin.21.JDK -e

If keytool is already installed, set JAVA_HOME or add its bin directory to PATH.
"@
}

if (-not $Base64OutputPath) {
    $repoRoot = Split-Path -Parent $PSScriptRoot
    $Base64OutputPath = Join-Path $repoRoot "keystore_base64.txt"
}

$keytool = Find-Keytool
$KeystorePath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($KeystorePath)
$Base64OutputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Base64OutputPath)

if (Test-Path $KeystorePath) {
    throw "A keystore already exists at: $KeystorePath`nMove or delete it first only if you intentionally want to replace the app's signing identity."
}

Write-Host ""
Write-Host "Feather Browser - permanent release keystore" -ForegroundColor Cyan
Write-Host "  Keystore : $KeystorePath"
Write-Host "  Alias    : $Alias"
Write-Host "  Base64   : $Base64OutputPath"
Write-Host ""
Write-Host "Use a NEW strong password. Do not reuse the password you typed in chat." -ForegroundColor Yellow
Write-Host ""

$first = Read-Host -AsSecureString "Keystore/key password"
$second = Read-Host -AsSecureString "Confirm password"

$firstPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($first)
$secondPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($second)

try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($firstPtr)
    $confirmation = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secondPtr)
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($firstPtr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secondPtr)
}

if ($password -cne $confirmation) {
    throw "Passwords do not match. Nothing was created."
}

if ($password.Length -lt 12) {
    throw "Use at least 12 characters. Nothing was created."
}

$parent = Split-Path -Parent $KeystorePath
if ($parent -and -not (Test-Path $parent)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}

Write-Host ""
Write-Host "Creating keystore..." -ForegroundColor Cyan

& $keytool -genkeypair -v `
    -keystore $KeystorePath `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -storepass $password `
    -keypass $password `
    -dname $DistinguishedName

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE. The keystore may be incomplete; delete it before retrying."
}

Write-Host ""
Write-Host "Verifying keystore..." -ForegroundColor Cyan

$details = & $keytool -list -v -keystore $KeystorePath -alias $Alias -storepass $password 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "Could not read the new keystore. Delete it and try again."
}

$fingerprint = $details |
    Select-String -Pattern 'SHA256:' |
    Select-Object -First 1 |
    ForEach-Object { ($_.Line -replace '.*SHA256:\s*', '').Trim() }

[Convert]::ToBase64String([IO.File]::ReadAllBytes($KeystorePath)) |
    Set-Content -NoNewline -Encoding ASCII $Base64OutputPath

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host ""
Write-Host "Keystore : $KeystorePath"
Write-Host "Alias    : $Alias"
Write-Host "SHA-256  : $fingerprint"
Write-Host "Base64   : $Base64OutputPath"
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "1. Back up the keystore file and the password somewhere safe."
Write-Host "2. GitHub repo -> Settings -> Secrets and variables -> Actions:"
Write-Host "     RELEASE_KEYSTORE_BASE64 = entire contents of $Base64OutputPath"
Write-Host "     STORE_PASSWORD          = the password you just entered"
Write-Host "     KEY_ALIAS               = $Alias"
Write-Host "     KEY_PASSWORD            = the same password"
Write-Host "3. Uninstall the old Feather build once (it uses a different key)."
Write-Host "4. Trigger a new release and install it."
Write-Host ""
Write-Host "Keep the keystore and password backed up. Losing them means users must uninstall to update." -ForegroundColor Yellow
