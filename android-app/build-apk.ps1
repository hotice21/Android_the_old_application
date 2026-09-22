# PowerShell 脚本：一键重新构建 H5 并打 APK（debug 版）
# 用法：在 android-app 目录下执行 .\build-apk.ps1
# 前置：本机已装 Android Studio（提供 Android SDK）+ JDK 17+

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repoRoot 'eligo-frontend'
$apkOut = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug'

Write-Host "[1/3] 构建 H5 产物..." -ForegroundColor Cyan
Push-Location $frontendRoot
try {
    npm run build:h5
    if ($LASTEXITCODE -ne 0) { throw "H5 构建失败" }
} finally {
    Pop-Location
}

Write-Host "[2/3] 同步到 Android assets..." -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'sync-assets.ps1')

Write-Host "[3/3] Gradle 打包 debug APK..." -ForegroundColor Cyan
Push-Location $PSScriptRoot
try {
    # Windows 用 gradlew.bat
    $gradlew = Join-Path $PSScriptRoot 'gradlew.bat'
    if (-not (Test-Path $gradlew)) {
        Write-Warning "gradlew.bat 不存在。请用 Android Studio 打开一次本项目，会自动生成 gradle wrapper。"
        Write-Host "或者手动执行： cd android-app; gradle wrapper --gradle-version 8.7"
        exit 1
    }
    & $gradlew assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败" }
} finally {
    Pop-Location
}

if (Test-Path (Join-Path $apkOut 'app-debug.apk')) {
    Write-Host ""
    Write-Host "APK 已生成：$apkOut\app-debug.apk" -ForegroundColor Green
    Write-Host "可通过 adb install -r 安装到老人机" -ForegroundColor Green
} else {
    Write-Warning "未找到预期 APK 输出，请检查 build/logs"
}
