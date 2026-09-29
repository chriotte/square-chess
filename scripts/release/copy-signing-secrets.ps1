# Copies the four GitHub Actions secrets for the release workflow to the clipboard,
# one at a time, so they can be pasted into GitHub without being shown on screen.
#
# GitHub: repository Settings > Environments > production-release > Add environment secret.
# Run in PowerShell:  .\scripts\release\copy-signing-secrets.ps1
param([string]$Properties = "$HOME\SquareChessSigning\standalone.properties")

$ErrorActionPreference = "Stop"
if (-not (Test-Path $Properties)) { throw "Not found: $Properties" }
$values = @{}
foreach ($line in Get-Content $Properties) {
    if ($line -match '^\s*([^=#]+?)\s*=\s*(.*)$') { $values[$matches[1]] = $matches[2] }
}
$store = $values["storeFile"] -replace '\\:', ':'
if (-not (Test-Path $store)) { throw "Keystore not found: $store" }

$secrets = [ordered]@{
    "SIGNING_KEYSTORE_BASE64" = [Convert]::ToBase64String([IO.File]::ReadAllBytes($store))
    "SIGNING_STORE_PASSWORD"  = $values["storePassword"]
    "SIGNING_KEY_ALIAS"       = $values["keyAlias"]
    "SIGNING_KEY_PASSWORD"    = $values["keyPassword"]
}
foreach ($name in $secrets.Keys) {
    Set-Clipboard -Value $secrets[$name]
    Read-Host "Copied $name to the clipboard. In GitHub, create a secret named $name, paste, save, then press Enter"
}
Set-Clipboard -Value " "
Write-Output "Done. The clipboard is cleared."
