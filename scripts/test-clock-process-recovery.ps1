param(
    [string]$Serial = "emulator-5554"
)

$ErrorActionPreference = "Stop"

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw "Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK path."
}

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
$adb = Join-Path $sdk "platform-tools\adb.exe"
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradlew = Join-Path $repoRoot "gradlew.bat"
$applicationId = "com.dataespresso.squarechess.dev"
$instrumentationComponent = "$applicationId.test/androidx.test.runner.AndroidJUnitRunner"
$activity = "$applicationId/com.dataespresso.squarechess.MainActivity"
$testClass = "com.dataespresso.squarechess.ClockProcessRecoveryDeviceTest"
$env:ANDROID_SERIAL = $Serial

if (-not (Test-Path $adb)) { throw "ADB was not found at $adb" }
if (-not (Test-Path $gradlew)) { throw "Gradle wrapper was not found at $gradlew" }

function Invoke-GradleProcessRecoveryTest([string]$method, [string]$stage) {
    $output = & $adb -s $Serial shell am instrument -w `
        -e class "$testClass#$method" `
        -e processRecoveryStage $stage `
        $instrumentationComponent
    $exitCode = $LASTEXITCODE
    $text = $output -join "`n"
    Write-Output $text
    if ($exitCode -ne 0 -or $text -notmatch "OK \(1 test\)") {
        throw "Instrumentation stage '$stage' did not pass."
    }
}

function Get-AppPid {
    $result = & $adb -s $Serial shell pidof $applicationId
    return ($result | Out-String).Trim()
}

& $adb -s $Serial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "ADB device '$Serial' is not available." }

Push-Location $repoRoot
try {
    & $gradlew ":app:installDebug" ":app:installDebugAndroidTest" `
        "-Pdev=true" "--console=plain"
    if ($LASTEXITCODE -ne 0) { throw "Could not install the isolated review app." }
} finally {
    Pop-Location
}

Invoke-GradleProcessRecoveryTest "prepareRunningClockCheckpoint" "prepare"

$pidBefore = Get-AppPid
& $adb -s $Serial shell am force-stop $applicationId
if ($LASTEXITCODE -ne 0) { throw "Could not force-stop $applicationId." }
Start-Sleep -Seconds 2
if (Get-AppPid) { throw "The app process survived force-stop." }

& $adb -s $Serial shell am start -W -n $activity
if ($LASTEXITCODE -ne 0) { throw "Could not relaunch $activity." }
$pidAfter = Get-AppPid
if (-not $pidAfter) { throw "The relaunched app process could not be found." }
if ($pidBefore -and $pidBefore -eq $pidAfter) {
    throw "The relaunch reused the previous app process."
}

Invoke-GradleProcessRecoveryTest "verifyRelaunchedProcessKeepsClockPausedUntilResume" "verify"
Write-Output "Process-death clock recovery passed on $Serial."
