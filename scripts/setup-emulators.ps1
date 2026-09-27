param(
    [string[]]$RequestedDevice = @('all'),
    [switch]$InstallMissingComponents
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
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
    }
)

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
            $sdkPath = $Matches[1].Trim('"').Replace('/', '\')
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
$imageDirectory = Join-Path $sdk 'system-images\android-36\google_apis\x86_64'

if (-not (Test-Path -LiteralPath $avdManager)) {
    throw "Android SDK Command-line Tools (avdmanager) not found at '$avdManager'. Install them in Android Studio > SDK Manager."
}

$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $env:JAVA_HOME -and -not $javaCommand) {
    throw 'Java was not found. Set JAVA_HOME to JDK 17 or add java.exe to PATH.'
}

$missing = @()
if (-not (Test-Path -LiteralPath $emulator)) {
    $missing += 'emulator'
}
if (-not (Test-Path -LiteralPath (Join-Path $imageDirectory 'package.xml'))) {
    $missing += $systemImage
}

if ($missing.Count -gt 0) {
    if (-not $InstallMissingComponents) {
        throw "Missing SDK components: $($missing -join ', '). Install them in Android Studio > SDK Manager, or rerun with -InstallMissingComponents."
    }
    if (-not (Test-Path -LiteralPath $sdkManager)) {
        throw "SDK Manager not found at '$sdkManager'. Install Android SDK Command-line Tools first."
    }

    & $sdkManager "--sdk_root=$sdk" 'emulator' $systemImage
    if ($LASTEXITCODE -ne 0) {
        throw "SDK Manager failed with exit code $LASTEXITCODE."
    }
    if (-not (Test-Path -LiteralPath $emulator) -or
        -not (Test-Path -LiteralPath (Join-Path $imageDirectory 'package.xml'))) {
        throw 'SDK Manager completed, but the emulator or API 36 x86_64 system image is still missing.'
    }
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
        $createOutput = 'no' | & $avdManager create avd -n "$($avdProfile.Name)" -k $systemImage 2>&1
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
        'hw.ramSize' = '4096'
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
