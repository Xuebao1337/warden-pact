param([switch]$Smoke,[switch]$ModelCheck,[switch]$Offline,[string]$JavaHome)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if(-not $JavaHome) {
    $localJdk=Join-Path $root '.tools/forge189/jdk-path.txt'
    if(Test-Path $localJdk) { $JavaHome=(Get-Content $localJdk -Raw).Trim() }
    elseif($env:JAVA_HOME) { $JavaHome=$env:JAVA_HOME }
}
if(-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin/javac.exe'))) { throw 'Use -JavaHome with a Java 8 JDK.' }
$version=& (Join-Path $JavaHome 'bin/java.exe') -version 2>&1 | Out-String
if($version -notmatch 'version "1\.8\.') { throw 'This legacy Forge build requires Java 8.' }
$oldJava=$env:JAVA_HOME
$oldGradle=$env:GRADLE_USER_HOME
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME=$JavaHome
    $env:GRADLE_USER_HOME=Join-Path $root '.gradle-legacy'
    $portable=Join-Path $root '.tools/forge189/gradle-2.14.1/bin/gradle.bat'
    $gradle=if(Test-Path $portable) { $portable } else { Join-Path $PSScriptRoot 'gradlew.bat' }
    $arguments=@('--console=plain')
    $localMaven=Join-Path $root '.tools/forge189/local-maven.init.gradle'
    if(Test-Path $localMaven) { $arguments+=@('-I',$localMaven) }
    if($Offline) { $arguments+='--offline' }
    if($Smoke) {
        $started=[DateTime]::UtcNow
        & $gradle @arguments -Psmoke runServer -x getAssets -x getAssetIndex
        if($LASTEXITCODE -ne 0) { throw "Smoke server failed: $LASTEXITCODE" }
        $file=Get-Item -LiteralPath 'run/witherdance-smoke-results.json'
        $report=Get-Content $file.FullName -Raw | ConvertFrom-Json
        if($file.LastWriteTimeUtc -lt $started -or $report.failed -ne 0 -or $report.passed -lt 45) { throw 'Smoke checks failed or incomplete.' }
        $report
    }
    if($ModelCheck) {
        & $gradle @arguments checkDanceModel
        if($LASTEXITCODE -ne 0) { throw "Model checks failed: $LASTEXITCODE" }
    }
    # runServer deobfuscates the development JAR: always produce a fresh release afterwards.
    & $gradle @arguments build
    if($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
    Get-FileHash build/libs/witherdance-forge-1.8.9-1.0.0.jar -Algorithm SHA256
} finally {
    Pop-Location
    $env:JAVA_HOME=$oldJava
    $env:GRADLE_USER_HOME=$oldGradle
}
