param([string[]]$GradleTasks = @('build'))
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$previousJavaHome = $env:JAVA_HOME
try {
    $candidates = @($env:JAVA_HOME)
    foreach ($folder in @((Join-Path $env:USERPROFILE '.jdks'),
                          (Join-Path $env:ProgramFiles 'Java'),
                          (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
                          (Join-Path $env:ProgramFiles 'Microsoft'))) {
        if (Test-Path -LiteralPath $folder) {
            $candidates += Get-ChildItem -LiteralPath $folder -Directory | Select-Object -ExpandProperty FullName
        }
    }
    $jdk = $null
    foreach ($candidate in $candidates) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        $compiler = Join-Path $candidate 'bin/javac.exe'
        if (-not (Test-Path -LiteralPath $compiler)) { continue }
        $versionText = (& $compiler -version 2>&1 | Out-String)
        if ($versionText -match 'javac 25(?:\.|\s|$)') {
            $jdk = $candidate
            break
        }
    }
    if (-not $jdk) {
        throw 'JDK 25 was not found. Install JDK 25 and set JAVA_HOME to its directory.'
    }
    $env:JAVA_HOME = $jdk
    Write-Host "Using JDK 25: $jdk"
    & (Join-Path $PSScriptRoot 'gradlew.bat') @GradleTasks --console=plain
    $buildExitCode = $LASTEXITCODE
    if ($buildExitCode -ne 0) { throw "Gradle failed with exit code $buildExitCode." }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
