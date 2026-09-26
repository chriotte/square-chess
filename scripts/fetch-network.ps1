$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $projectRoot 'app/src/main/assets/nn-1a298aa575a0.nnue'
$expected = '1a298aa575a085434d29027978dc36867fe9c5bcea9376654b7a8eba1e52dfc2'
if (-not (Test-Path -LiteralPath $destination)) {
    Invoke-WebRequest -Uri 'https://tests.stockfishchess.org/api/nn/nn-1a298aa575a0.nnue' -OutFile $destination
}
if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'NNUE checksum mismatch. Do not build with this asset.'
}
Write-Output 'Pinned NNUE network verified.'
