param([ValidateRange(1,2147483647)][int]$VersionCode=1,[ValidatePattern('^[0-9]+(\.[0-9]+){0,2}$')][string]$VersionName='', [switch]$Publish,[string]$Notes='')
# -Publish creates v<VersionName> with the full APK and offline database; needs gh auth login.
# and the commit to release already pushed to main.
$ErrorActionPreference='Stop'
if(-not $VersionName){$VersionName=$VersionCode.ToString()}
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
    Invoke-Native 'Build or verification failed' { & .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease "-PversionCode=$VersionCode" "-PversionName=$VersionName" --console=plain --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' }
    $sdkPath=(Get-Content local.properties | Where-Object {$_ -like 'sdk.dir=*'}) -replace '^sdk.dir=','' -replace '\\:',':' -replace '\\\\','\'
    $buildTools=Join-Path $sdkPath 'build-tools/36.0.0'
    $apk=Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    Invoke-Native 'APK signature verification failed' { & (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $apk }
    $badging=Invoke-Native 'APK metadata could not be read' { & (Join-Path $buildTools 'aapt2.exe') dump badging $apk }
    if(-not ($badging -match "package: name='az.healthchecker.app' versionCode='$VersionCode'")){throw 'APK has an unexpected applicationId or versionCode'}
    if(-not ($badging -match ("versionName='"+[regex]::Escape($VersionName)+"'"))){throw 'APK has an unexpected versionName'}
    $permissions=Invoke-Native 'APK permissions could not be read' { & (Join-Path $buildTools 'aapt2.exe') dump permissions $apk }
    if($permissions -match 'android.permission.INTERNET'){throw 'The offline APK must not request INTERNET'}
    # The first delivered build stays as HealthChecker-base-v3.apk; every later build is a separate, numbered update
    # that installs over whatever version the phone already has.
    New-Item -ItemType Directory -Force dist | Out-Null
    $name="HealthChecker-update-v$VersionName.apk"
    Copy-Item -LiteralPath $apk -Destination "dist/$name" -Force
    $hash=(Get-FileHash -LiteralPath "dist/$name" -Algorithm SHA256).Hash
    $sums='dist/SHA256SUMS.txt'
    $lines=@(if(Test-Path -LiteralPath $sums){Get-Content -LiteralPath $sums | Where-Object {$_ -and -not $_.EndsWith("  $name")}})+"$hash  $name"
    Set-Content -LiteralPath $sums -Value $lines
    Set-Content -LiteralPath "dist/$name.sha256" -Value "$hash  $name"
    # Publish the complete derived OFF database separately as well as inside the APK (ODbL).
    $catalogRoot=Join-Path $projectRoot 'app/src/main/assets/catalog/barcodes'
    $catalogManifest=Get-Content -LiteralPath (Join-Path $catalogRoot 'manifest.json') -Raw | ConvertFrom-Json
    $catalogReport=Get-Content -LiteralPath (Join-Path $catalogRoot 'report.json') -Raw | ConvertFrom-Json
    $databaseName="HealthChecker-products-v$VersionName.sqlite.gz"
    $databasePath=Join-Path $projectRoot "dist/$databaseName"
    $databaseOutput=[System.IO.File]::Open($databasePath,[System.IO.FileMode]::Create,[System.IO.FileAccess]::Write)
    try {
        foreach($part in $catalogManifest.parts){
            if($part -notmatch '^products\.sqlite\.gz\.part[0-9]{3}$'){throw 'Invalid catalog asset part name'}
            $partInput=[System.IO.File]::OpenRead((Join-Path $catalogRoot $part))
            try{$partInput.CopyTo($databaseOutput)}finally{$partInput.Dispose()}
        }
    } finally {$databaseOutput.Dispose()}
    $databaseHash=(Get-FileHash -LiteralPath $databasePath -Algorithm SHA256).Hash
    if($databaseHash.ToLowerInvariant() -ne $catalogReport.gzip_sha256){throw 'Offline database asset checksum mismatch'}
    Set-Content -LiteralPath "$databasePath.sha256" -Value "$databaseHash  $databaseName"
    Set-Content -LiteralPath $versionFile -Value $VersionCode
    $releaseAssets=@("dist/$name","dist/$name.sha256",$databasePath,"$databasePath.sha256",(Join-Path $catalogRoot 'report.json'),(Join-Path $catalogRoot 'NOTICE.txt'),(Join-Path $catalogRoot 'ODbL-1.0.txt'),(Join-Path $catalogRoot 'DbCL-1.0.txt'))
    # Updates are delivered as GitHub Releases; releases/ in the repository keeps only the base installer for new users.
    if($Publish) {
        $gh=(Get-Command gh -ErrorAction SilentlyContinue).Source
        if(-not $gh){$gh=Join-Path $env:ProgramFiles 'GitHub CLI/gh.exe'}
        if(-not (Test-Path -LiteralPath $gh)){throw 'GitHub CLI is required for -Publish: install it and run gh auth login.'}
        if(-not $Notes){$Notes="Обновление HealthChecker v$VersionName. Скачайте $name и откройте его поверх установленного приложения — дневник сохранится. SHA-256: $hash"}
        Invoke-Native 'GitHub release failed' { & $gh release create "v$VersionName" @releaseAssets --repo saint-1406/HealthChecker --target main --title "HealthChecker v$VersionName" --notes $Notes --latest }
        Write-Output "Published: https://github.com/saint-1406/HealthChecker/releases/tag/v$VersionName"
    }
    Write-Output "Ready: $projectRoot\dist\$name (versionCode $VersionCode, SHA-256 $hash)"
} finally {Pop-Location}
