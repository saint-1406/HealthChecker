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
    # The first delivered build stays as HealthChecker-base-v3.apk; every later build is a separate, numbered update
    # that installs over whatever version the phone already has.
    New-Item -ItemType Directory -Force dist | Out-Null
    $name="HealthChecker-update-v$VersionCode.apk"
    Copy-Item -LiteralPath $apk -Destination "dist/$name" -Force
    $hash=(Get-FileHash -LiteralPath "dist/$name" -Algorithm SHA256).Hash
    $sums='dist/SHA256SUMS.txt'
    $lines=@(if(Test-Path -LiteralPath $sums){Get-Content -LiteralPath $sums | Where-Object {$_ -and -not $_.EndsWith("  $name")}})+"$hash  $name"
    Set-Content -LiteralPath $sums -Value $lines
    Set-Content -LiteralPath $versionFile -Value $VersionCode
    # releases/ is committed for download from GitHub: the base installer plus only the newest update.
    New-Item -ItemType Directory -Force releases | Out-Null
    Get-ChildItem -LiteralPath releases -Filter 'HealthChecker-update-v*.apk' | Where-Object {$_.Name -ne $name} | Remove-Item
    Copy-Item -LiteralPath "dist/$name" -Destination "releases/$name" -Force
    if(Test-Path -LiteralPath 'dist/HealthChecker-base-v3.apk'){Copy-Item -LiteralPath 'dist/HealthChecker-base-v3.apk' -Destination 'releases/HealthChecker-base-v3.apk' -Force}
    Set-Content -LiteralPath releases/SHA256SUMS.txt -Value (Get-ChildItem -LiteralPath releases -Filter '*.apk' | Sort-Object Name | ForEach-Object {"$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash)  $($_.Name)"})
    Write-Output "Ready: $projectRoot\dist\$name (versionCode $VersionCode, SHA-256 $hash); releases/ refreshed for GitHub"
} finally {Pop-Location}
