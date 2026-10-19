param([ValidateRange(1,2147483647)][int]$VersionCode=1)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
# Windows PowerShell turns any stderr line of a native tool (Gradle prints warnings there) into a terminating
# error under 'Stop'. Native steps are judged by their exit code instead.
function Invoke-Native([string]$failure,[scriptblock]$command) {
    $previous=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try { & $command 2>&1 | ForEach-Object { "$_" } } finally { $ErrorActionPreference=$previous }
    if($LASTEXITCODE -ne 0){throw $failure}
}
Push-Location $projectRoot
try {
    if(-not (Test-Path -LiteralPath 'signing.properties')) {throw 'Create the local signing key and signing.properties first; see README.'}
    $versionFile='dist/HealthChecker.version'
    if(Test-Path -LiteralPath $versionFile) {
        $delivered=[int](Get-Content -LiteralPath $versionFile -TotalCount 1)
        if($VersionCode -le $delivered){throw "VersionCode must be greater than the delivered $delivered, otherwise Android refuses to update the installed app."}
    }
    Invoke-Native 'Build or verification failed' { & .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease "-PversionCode=$VersionCode" --console=plain }
    $sdkPath=(Get-Content local.properties | Where-Object {$_ -like 'sdk.dir=*'}) -replace '^sdk.dir=','' -replace '\\:',':' -replace '\\\\','\'
    $buildTools=Join-Path $sdkPath 'build-tools/36.0.0'
    $apk=Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    Invoke-Native 'APK signature verification failed' { & (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $apk }
    $badging=Invoke-Native 'APK metadata could not be read' { & (Join-Path $buildTools 'aapt2.exe') dump badging $apk }
    if(-not ($badging -match "package: name='az.healthchecker.app' versionCode='$VersionCode'")){throw 'APK has an unexpected applicationId or versionCode'}
    New-Item -ItemType Directory -Force dist | Out-Null
    Copy-Item -LiteralPath $apk -Destination dist/HealthChecker.apk -Force
    $hash=(Get-FileHash -LiteralPath dist/HealthChecker.apk -Algorithm SHA256).Hash
    Set-Content -LiteralPath dist/HealthChecker.apk.sha256 -Value "$hash  HealthChecker.apk"
    Set-Content -LiteralPath $versionFile -Value $VersionCode
    Write-Output "Ready: $projectRoot\dist\HealthChecker.apk (versionCode $VersionCode, SHA-256 $hash)"
} finally {Pop-Location}
