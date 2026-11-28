param([switch]$Tests, [switch]$Offline, [string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$previousJava = $env:JAVA_HOME
$previousGradle = $env:GRADLE_USER_HOME
Push-Location $PSScriptRoot
try {
    $candidates = @($JavaHome)
    $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
    if (Test-Path -LiteralPath $jdkRoot) {
        $candidates += Get-ChildItem -LiteralPath $jdkRoot -Directory | Select-Object -ExpandProperty FullName
    }
    $jdk = $null
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $compiler = Join-Path $candidate 'bin/javac.exe'
        if ((Test-Path -LiteralPath $compiler) -and ((& $compiler -version 2>&1 | Out-String) -match 'javac 25(?:\.|\s|$)')) {
            $jdk = $candidate
            break
        }
    }
    if (-not $jdk) { throw 'Install JDK 25, then set JAVA_HOME or pass -JavaHome.' }
    $env:JAVA_HOME = $jdk
    $env:GRADLE_USER_HOME = Join-Path $workspace '.gradle-user'
    $tasks = @('build')
    if ($Tests) { $tasks += 'runGameTest' }
    $options = @('--console=plain')
    if ($Offline) { $options += '--offline' }
    & ./gradlew.bat @tasks @options
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:GRADLE_USER_HOME = $previousGradle
    Pop-Location
}
