<#
.SYNOPSIS
    Builds the double-clickable Windows app: target\dist\VirpeMart\VirpeMart.exe

.DESCRIPTION
    1. Builds the project with the Maven Wrapper (runs all tests unless -SkipTests).
    2. Collects virpe-mart.jar and its lib\ folder.
    3. Runs jpackage to create an app folder that contains its own Java runtime,
       so the store laptop does not need Java installed.

    Copy the whole target\dist\VirpeMart folder to the store laptop and run VirpeMart.exe.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\package.ps1
#>
param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# ---- Find jpackage (part of the JDK) ----
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\jpackage.exe'))) {
    $jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
} else {
    $found = Get-Command jpackage -ErrorAction SilentlyContinue
    if (-not $found) { throw 'jpackage not found. Install JDK 25 and set JAVA_HOME.' }
    $jpackage = $found.Source
}

Push-Location $root
try {
    # ---- 0. Remove the previous package (jpackage marks the launcher read-only, which blocks "mvnw clean") ----
    $oldDist = Join-Path $root 'target\dist'
    if (Test-Path $oldDist) { Remove-Item -Recurse -Force $oldDist }

    # ---- 1. Build with Maven ----
    $mvnArgs = @('-B', 'clean', 'package')
    if ($SkipTests) { $mvnArgs += '-DskipTests' }
    & (Join-Path $root 'mvnw.cmd') @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit code $LASTEXITCODE)." }

    # ---- 2. Collect the jar and libraries ----
    $props = Get-Content (Join-Path $root 'target\classes\app.properties') -Raw | ConvertFrom-StringData
    $version = $props.'app.version' -replace '-SNAPSHOT$', ''

    $inputDir = Join-Path $root 'target\package-input'
    $distDir = Join-Path $root 'target\dist'
    New-Item -ItemType Directory -Force $inputDir | Out-Null
    Copy-Item (Join-Path $root 'target\virpe-mart.jar') $inputDir
    Copy-Item (Join-Path $root 'target\lib') (Join-Path $inputDir 'lib') -Recurse

    # ---- 3. Create the app folder with a bundled Java runtime ----
    & $jpackage `
        --type app-image `
        --name VirpeMart `
        --app-version $version `
        --vendor 'Virpe Mart' `
        --description 'Virpe Mart billing software' `
        --input $inputDir `
        --main-jar virpe-mart.jar `
        --main-class com.virpemart.billing.Launcher `
        --java-options '--enable-native-access=ALL-UNNAMED' `
        --dest $distDir
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed (exit code $LASTEXITCODE)." }

    # Clear read-only flags so a later "mvnw clean" can delete the package folder.
    Get-ChildItem $distDir -Recurse -File | Where-Object { $_.IsReadOnly } | ForEach-Object { $_.IsReadOnly = $false }

    Write-Host ''
    Write-Host "Done. App folder: $distDir\VirpeMart"
    Write-Host "Run:              $distDir\VirpeMart\VirpeMart.exe"
} finally {
    Pop-Location
}
