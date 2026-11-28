param([switch]$Tests, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$oldJava = $env:JAVA_HOME
$oldGradle = $env:GRADLE_USER_HOME
try {
    $candidates = @($env:JAVA_HOME)
    $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path $jdkRoot) { $candidates += @(Get-ChildItem $jdkRoot -Directory | Select-Object -ExpandProperty FullName) }
    $found = $false
    foreach ($candidate in $candidates) {
        if (!$candidate) { continue }
        $java = Join-Path $candidate 'bin/java.exe'
        if (Test-Path $java) {
            $versionText = (& $java -version 2>&1 | Out-String)
            if ($versionText -match 'version "25[."]') { $env:JAVA_HOME = $candidate; $found = $true; break }
        }
    }
    if (!$found) { throw 'Install a Java 25 JDK and set JAVA_HOME before building.' }
    if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $root '.gradle-user' }
    $gradleArgs = @('-p', $PSScriptRoot, 'build', 'checkDanceAnimation')
    if ($Tests) { $gradleArgs += @('runGameTestServer', '-x', 'downloadAssets') }
    if ($Offline) { $gradleArgs += '--offline' }
    & (Join-Path $PSScriptRoot 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $oldJava
    $env:GRADLE_USER_HOME = $oldGradle
}
