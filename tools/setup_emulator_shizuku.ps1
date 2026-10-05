# Setup Shizuku on Android Emulator for App Oops debugging
$ErrorActionPreference = "Stop"

$AdbPath = "D:\software\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $AdbPath)) {
    $AdbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($AdbCmd) {
        $AdbPath = $AdbCmd.Source
    } else {
        Write-Error "ADB not found. Please ensure Android SDK platform-tools is installed."
    }
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   App Oops - Shizuku 模拟器环境一键配置" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "使用 ADB: $AdbPath"

# 1. 检测已连接的模拟器
$devices = & $AdbPath devices
Write-Host "$devices"

$connected = $devices | Where-Object { $_ -match "\tdevice$" }
if (-not $connected) {
    Write-Warning "未检测到处于运行状态的 Android 模拟器/设备！"
    Write-Host "请先在 Android Studio 中启动 AVD 模拟器，或启动第三方模拟器后再运行此脚本。"
    exit 1
}

$ToolsDir = "$PSScriptRoot"
$ShizukuApk = Join-Path $ToolsDir "shizuku.apk"

# 2. 获取 Shizuku APK
if (-not (Test-Path $ShizukuApk)) {
    Write-Host "正在下载最新 Shizuku 官方安装包..." -ForegroundColor Yellow
    $DownloadUrl = "https://github.com/RikkaApps/Shizuku/releases/download/v13.5.4.r1049.0dc72c0/shizuku-v13.5.4.r1049.0dc72c0-release.apk"
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $DownloadUrl -OutFile $ShizukuApk -UseBasicParsing
        Write-Host "下载完成: $ShizukuApk" -ForegroundColor Green
    } catch {
        Write-Warning "从 GitHub 下载失败: $_"
        Write-Host "您也可以手动将 Shizuku APK 放置于: $ShizukuApk"
        exit 1
    }
}

# 3. 安装 Shizuku 到模拟器
Write-Host "正在安装 Shizuku 到模拟器..." -ForegroundColor Yellow
& $AdbPath install -r $ShizukuApk

# 4. 执行 start.sh 启动 Shizuku 服务
Write-Host "正在通过 ADB 启动 Shizuku 服务..." -ForegroundColor Yellow
& $AdbPath shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh

# 5. 校验运行进程
Start-Sleep -Seconds 2
Write-Host "检查 Shizuku 运行进程状态..." -ForegroundColor Yellow
$proc = & $AdbPath shell ps -A
$shizukuProc = $proc | Select-String "shizuku"

if ($shizukuProc) {
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host " [成功] Shizuku 服务已成功在模拟器中启动！" -ForegroundColor Green
    Write-Host "$shizukuProc" -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host "现在即可启动 App Oops，点击【授权访问】进行完整特权功能调试！"
} else {
    Write-Warning "未检测到活跃的 Shizuku 进程，您也可以在模拟器内打开 Shizuku 应用查看具体状态。"
}
