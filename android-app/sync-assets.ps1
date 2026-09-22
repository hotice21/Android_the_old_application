# PowerShell 脚本：把 eligo-frontend 最新 H5 产物同步到 android assets
# 用法：在 android-app 目录下执行 .\sync-assets.ps1

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'eligo-frontend'
$h5BuildDir = Join-Path $frontendRoot 'dist\build\h5'
$assetDst = Join-Path $PSScriptRoot 'app\src\main\assets\h5'

if (-not (Test-Path $h5BuildDir)) {
    Write-Error "H5 构建产物不存在：$h5BuildDir。请先在 eligo-frontend 目录执行 npm run build:h5"
    exit 1
}

if (Test-Path $assetDst) {
    Remove-Item -Path $assetDst -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $assetDst | Out-Null

Copy-Item -Path "$h5BuildDir\*" -Destination $assetDst -Recurse -Force
Write-Host "H5 资源已同步到 $assetDst" -ForegroundColor Green
