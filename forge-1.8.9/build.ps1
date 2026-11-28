param([switch]$Smoke, [switch]$Offline, [string]$JavaHome)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome) {
    $localJdk = Join-Path $root '.tools/forge189/jdk-path.txt'
    if (Test-Path $localJdk) { $JavaHome = (Get-Content $localJdk -Raw).Trim() }
    elseif ($env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME }
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin/javac.exe'))) {
    throw 'Set -JavaHome to a Java 8 JDK (not a JRE).'
}
$java = Join-Path $JavaHome 'bin/java.exe'
$version = & $java -version 2>&1 | Out-String
if ($version -notmatch 'version "1\.8\.') { throw 'Minecraft 1.8.9 requires a Java 8 JDK for this build.' }
$env:JAVA_HOME = $JavaHome
$env:GRADLE_USER_HOME = Join-Path $root '.gradle-legacy'
$portableGradle = Join-Path $root '.tools/forge189/gradle-2.14.1/bin/gradle.bat'
$gradle = if (Test-Path $portableGradle) { $portableGradle } else { Join-Path $PSScriptRoot 'gradlew.bat' }
$arguments = @('--console=plain')
$localRepository = Join-Path $root '.tools/forge189/local-maven.init.gradle'
if (Test-Path $localRepository) { $arguments += @('-I', $localRepository) }
if ($Offline) { $arguments += '--offline' }
Push-Location $PSScriptRoot
try {
    $started = [DateTime]::UtcNow
    if ($Smoke) { & $gradle @arguments -Psmoke runServer -x getAssets -x getAssetIndex }
    else { & $gradle @arguments build }
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
    if ($Smoke) {
        $resultFile = Get-Item -LiteralPath 'run/witherpact-smoke-results.json'
        if ($resultFile.LastWriteTimeUtc -lt $started) { throw 'The server did not produce a fresh test report.' }
        $report = Get-Content run/witherpact-smoke-results.json -Raw | ConvertFrom-Json
        if ($report.failed -gt 0 -or $report.passed -lt 86) { throw 'Integration tests failed or incomplete.' }
        $report
    } else { Get-ChildItem build/libs/*1.0.0.jar | Get-FileHash -Algorithm SHA256 }
} finally { Pop-Location }
