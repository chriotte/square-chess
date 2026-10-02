param(
    [string[]]$RequestedDevice = @('all'),
    [switch]$InstallMissingComponents
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
# Default image; a profile can name its own (Image) and memory size (RamMb).
$systemImage = 'system-images;android-36;google_apis;x86_64'

$deviceProfiles = @(
    [pscustomobject]@{
        Name = 'SquareChess_Fold_Inner'
        DisplayName = 'Square Chess - Fold inner screen'
        Width = 1840
        Height = 2208
        Density = 420
        Keyboard = $false
    },
    [pscustomobject]@{
        Name = 'SquareChess_Fold_Outer'
        DisplayName = 'Square Chess - Fold outer screen'
        Width = 1080
        Height = 2092
        Density = 420
        Keyboard = $false
    },
    [pscustomobject]@{
        Name = 'SquareChess_Clicks_Communicator'
        DisplayName = 'Square Chess - Clicks Communicator'
        Width = 1080
        Height = 1200
        Density = 400
        Keyboard = $true
    },
    [pscustomobject]@{
        Name = 'SquareChess_Titan_2'
        DisplayName = 'Square Chess - Unihertz Titan 2'
        Width = 1440
        Height = 1440
        Density = 480
        Keyboard = $true
    },
    [pscustomobject]@{
        Name = 'SquareChess_Titan_2_Elite'
        DisplayName = 'Square Chess - Unihertz Titan 2 Elite'
        Width = 1080
        Height = 1200
        Density = 300
        Keyboard = $true
    },
    [pscustomobject]@{
        # Light Phone III: 3.92 in AMOLED, 1080 x 1240, about 419 ppi (420 is the
        # nearest Android density), so about 411 x 472 dp. No keyboard. This is a
        # stock Android profile; it does not emulate LightOS or its SDK.
        Name = 'SquareChess_Light_Phone_III'
        DisplayName = 'Square Chess - Light Phone III'
        Width = 1080
        Height = 1240
        Density = 420
        Keyboard = $false
    },
    [pscustomobject]@{
        # E-ink tablet layout, close to an ONYX BOOX Note3 (10.3 in, 1404 x 1872, 227 ppi).
        # Only the screen size and density: an emulator cannot show e-paper refresh.
        Name = 'SquareChess_Eink_Tablet'
        DisplayName = 'Square Chess - e-ink tablet (layout only)'
        Width = 1404
        Height = 1872
        Density = 227
        Keyboard = $false
    },
    [pscustomobject]@{
        # BlackBerry Priv: 5.4 in, 1440 x 2560, about 540 ppi, slide-out QWERTY keyboard.
        # The real Priv stops at Android 6, below the app's minimum (Android 8.0), so this
        # profile runs Android 8.0: it checks the screen and the keyboard, not the Priv's OS.
        Name = 'SquareChess_BlackBerry_Priv'
        DisplayName = 'Square Chess - BlackBerry Priv (Android 8.0)'
        Width = 1440
        Height = 2560
        Density = 560
        Keyboard = $true
        Image = 'system-images;android-26;default;x86_64'
        RamMb = 3072
    },
    [pscustomobject]@{
        # Older e-ink tablet: 7.8 in, 1404 x 1872, 300 ppi, Android 8.0, 32-bit, 2 GB RAM.
        # The 32-bit x86 image runs the app's 32-bit engine build (the closest an x86 PC gets
        # to 32-bit ARM tablets). Layout only: an emulator cannot show e-paper refresh.
        Name = 'SquareChess_Eink_Android8'
        DisplayName = 'Square Chess - e-ink tablet, Android 8.0, 32-bit'
        Width = 1404
        Height = 1872
        Density = 300
        Keyboard = $false
        Image = 'system-images;android-26;default;x86'
        RamMb = 2048
    },
    [pscustomobject]@{
        # ONYX BOOX Go 6: 6 in e-ink, 1072 x 1448, 300 ppi, Android 13. A user reported that the
        # app works on it. Layout only: an emulator cannot show e-paper refresh.
        Name = 'SquareChess_Boox_Go_6'
        DisplayName = 'Square Chess - BOOX Go 6 (layout only)'
        Width = 1072
        Height = 1448
        Density = 300
        Keyboard = $false
        Image = 'system-images;android-33;google_apis;x86_64'
        RamMb = 2048
    }
)
foreach ($p in $deviceProfiles) {
    if (-not $p.PSObject.Properties['Image']) { $p | Add-Member Image $systemImage }
    if (-not $p.PSObject.Properties['RamMb']) { $p | Add-Member RamMb 4096 }
}

function Get-AndroidSdk {
    foreach ($variable in @('ANDROID_HOME', 'ANDROID_SDK_ROOT')) {
        $value = [Environment]::GetEnvironmentVariable($variable)
        if ($value -and (Test-Path -LiteralPath $value)) {
            return (Resolve-Path -LiteralPath $value).Path
        }
    }

    $localProperties = Join-Path $projectRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($sdkLine -match '^\s*sdk\.dir\s*=\s*(.+?)\s*$') {
            # local.properties escapes the drive colon: C\:/Users/...
            $sdkPath = $Matches[1].Trim('"').Replace('\:', ':').Replace('/', '\')
            if (Test-Path -LiteralPath $sdkPath) {
                return (Resolve-Path -LiteralPath $sdkPath).Path
            }
        }
    }

    if ($env:LOCALAPPDATA) {
        $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
        if (Test-Path -LiteralPath $defaultSdk) {
            return (Resolve-Path -LiteralPath $defaultSdk).Path
        }
    }

    throw 'Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT, or add sdk.dir to local.properties.'
}

$sdk = Get-AndroidSdk
$sdkManager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
$avdManager = Join-Path $sdk 'cmdline-tools\latest\bin\avdmanager.bat'
$emulator = Join-Path $sdk 'emulator\emulator.exe'

if (-not (Test-Path -LiteralPath $avdManager)) {
    throw "Android SDK Command-line Tools (avdmanager) not found at '$avdManager'. Install them in Android Studio > SDK Manager."
}

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $env:JAVA_HOME -and -not $javaCommand) {
    throw 'Java was not found. Set JAVA_HOME to JDK 17 or add java.exe to PATH.'
}

$selectedProfiles = @()
if ($RequestedDevice -contains 'all') {
    $selectedProfiles = $deviceProfiles
} else {
    foreach ($requestedName in $RequestedDevice) {
        $selected = $deviceProfiles | Where-Object {
            $_.Name -eq $requestedName -or $_.Name -eq "SquareChess_$requestedName"
        } | Select-Object -First 1
        if (-not $selected) {
            $validNames = ($deviceProfiles | ForEach-Object { $_.Name -replace '^SquareChess_', '' }) -join ', '
            throw "Unknown profile '$requestedName'. Choose one of: $validNames."
        }
        $selectedProfiles += $selected
    }
}

# 'system-images;android-26;default;x86' is installed in system-images\android-26\default\x86.
function Get-ImageDirectory([string]$image) { Join-Path $sdk ($image -replace ';', '\') }

$missing = @()
if (-not (Test-Path -LiteralPath $emulator)) {
    $missing += 'emulator'
}
foreach ($image in @($selectedProfiles | ForEach-Object { $_.Image } | Select-Object -Unique)) {
    if (-not (Test-Path -LiteralPath (Join-Path (Get-ImageDirectory $image) 'package.xml'))) {
        $missing += $image
    }
}

if ($missing.Count -gt 0) {
    if (-not $InstallMissingComponents) {
        throw "Missing SDK components: $($missing -join ', '). Install them in Android Studio > SDK Manager, or rerun with -InstallMissingComponents."
    }
    if (-not (Test-Path -LiteralPath $sdkManager)) {
        throw "SDK Manager not found at '$sdkManager'. Install Android SDK Command-line Tools first."
    }

    & $sdkManager "--sdk_root=$sdk" @missing
    if ($LASTEXITCODE -ne 0) {
        throw "SDK Manager failed with exit code $LASTEXITCODE."
    }
    $stillMissing = @($missing | Where-Object {
        if ($_ -eq 'emulator') { -not (Test-Path -LiteralPath $emulator) }
        else { -not (Test-Path -LiteralPath (Join-Path (Get-ImageDirectory $_) 'package.xml')) }
    })
    if ($stillMissing.Count -gt 0) {
        throw "SDK Manager completed, but these are still missing: $($stillMissing -join ', ')."
    }
}

foreach ($avdProfile in $selectedProfiles) {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $avdList = & $avdManager list avd 2>&1
        $avdListExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($avdListExitCode -ne 0) {
        throw "Could not list AVDs: $($avdList -join [Environment]::NewLine)"
    }
    $existingNames = @(
        $avdList | ForEach-Object {
            if ("$_" -match '^\s*Name:\s*(.+?)\s*$') {
                $Matches[1]
            }
        }
    )
    if ($existingNames -contains $avdProfile.Name) {
        Write-Output "Keeping existing AVD: $($avdProfile.Name)"
        continue
    }

    Write-Output "Creating AVD: $($avdProfile.Name)"
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        # cmd supplies the "no": a PowerShell pipe can add a UTF-8 byte-order mark,
        # and avdmanager then rejects the reply.
        $createOutput = cmd /c "echo no| `"$avdManager`" create avd -n `"$($avdProfile.Name)`" -k `"$($avdProfile.Image)`"" 2>&1
        $createExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorPreference
    }
    if ($createExitCode -ne 0) {
        throw "Could not create $($avdProfile.Name): $($createOutput -join [Environment]::NewLine)"
    }

    if ($env:ANDROID_AVD_HOME) {
        $avdDirectory = Join-Path $env:ANDROID_AVD_HOME "$($avdProfile.Name).avd"
    } elseif ($env:ANDROID_USER_HOME) {
        $avdDirectory = Join-Path (Join-Path $env:ANDROID_USER_HOME 'avd') "$($avdProfile.Name).avd"
    } else {
        $avdDirectory = Join-Path (Join-Path $env:USERPROFILE '.android\avd') "$($avdProfile.Name).avd"
    }
    $configPath = Join-Path $avdDirectory 'config.ini'
    if (-not (Test-Path -LiteralPath $configPath)) {
        throw "AVD was created but its configuration was not found at '$configPath'."
    }

    $settings = [ordered]@{
        'avd.ini.displayname' = $avdProfile.DisplayName
        'hw.lcd.width' = $avdProfile.Width
        'hw.lcd.height' = $avdProfile.Height
        'hw.lcd.density' = $avdProfile.Density
        'hw.keyboard' = if ($avdProfile.Keyboard) { 'yes' } else { 'no' }
        'hw.gpu.enabled' = 'yes'
        'hw.gpu.mode' = 'auto'
        'hw.initialOrientation' = 'Portrait'
        'hw.ramSize' = "$($avdProfile.RamMb)"
        'vm.heapSize' = '512'
        'skin.dynamic' = 'yes'
    }
    $configLines = @()
    foreach ($line in Get-Content -LiteralPath $configPath) {
        if ($line -match '^([^=]+)=(.*)$' -and $settings.Contains($Matches[1])) {
            $key = $Matches[1]
            $configLines += "$key=$($settings[$key])"
            $settings.Remove($key)
        } else {
            $configLines += $line
        }
    }
    foreach ($key in $settings.Keys) {
        $configLines += "$key=$($settings[$key])"
    }
    Set-Content -LiteralPath $configPath -Value $configLines -Encoding ASCII
    Write-Output "Configured $($avdProfile.Width)x$($avdProfile.Height) at $($avdProfile.Density) dpi; hardware keyboard: $($avdProfile.Keyboard)."
}

Write-Output ''
Write-Output "AVDs are ready. Start one with: `"$emulator`" -avd <AVD_NAME>"
