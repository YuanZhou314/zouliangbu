# 本地起内容服务（Windows / PowerShell）
#
# 用法：
#   .\run-dev.ps1                 # 默认 8080
#   .\run-dev.ps1 -Port 8081      # 换端口
#   .\run-dev.ps1 -SkipBuild      # 跳过打包，直接跑已有 jar
#   .\run-dev.ps1 -Force          # 端口被占时先杀掉占用进程（仅本机开发用）
#
# 为什么必须带 --server.port：本机存在环境变量 SERVER__PORT，Spring 的宽松绑定会把它
# 当成 server.port；命令行参数优先级最高，显式传才能保证端口可控。
#
# 注意：本文件必须保存为「UTF-8 带 BOM」。Windows PowerShell 5.1 在无 BOM 时按 GBK 解码，
# 中文字符会把脚本解析坏（TerminatorExpectedAtEndOfString）。编辑后请用
#   Set-Content -Encoding UTF8
# 保存，不要用会去掉 BOM 的编辑器。

param(
    [int]$Port = 8080,
    [switch]$SkipBuild,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$Jar = 'target/zouliangbu-server-0.1.0.jar'

# ---------- 1. JDK 检查 ----------
try {
    $verLine = (java -version 2>&1 | Select-Object -First 1).ToString()
    Write-Host "java: $verLine"
} catch {
    Write-Host "[ERROR] 找不到 java，请安装 JDK 17（Temurin / Zulu）并配好 JAVA_HOME" -ForegroundColor Red
    exit 1
}
if ($verLine -notmatch '"(\d+)') {
    Write-Host "[ERROR] 无法识别 java 版本：$verLine" -ForegroundColor Red
    exit 1
}
$major = [int]$Matches[1]
if ($major -eq 1 -and $verLine -match '"1\.(\d+)') { $major = [int]$Matches[1] }
if ($major -lt 17) {
    Write-Host "[ERROR] 需要 JDK 17+，当前是 $major" -ForegroundColor Red
    exit 1
}

# ---------- 2. 打包 ----------
if (-not $SkipBuild -or -not (Test-Path $Jar)) {
    Write-Host "[..] 打包中（跳过测试）..." -ForegroundColor Cyan
    & mvn -q -s maven-settings.xml -DskipTests package
    if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] 打包失败" -ForegroundColor Red; exit 1 }
}
Write-Host "jar: $Jar"

# ---------- 3. 端口检查 ----------
$conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($conns) {
    $pids = ($conns | Select-Object -ExpandProperty OwningProcess -Unique)
    if ($Force) {
        foreach ($p in $pids) {
            Write-Host "[..] 杀掉占用 $Port 的进程 PID $p" -ForegroundColor Yellow
            Stop-Process -Id $p -Force -Confirm:$false
        }
        Start-Sleep -Seconds 1
    } else {
        Write-Host "[ERROR] 端口 $Port 已被占用（PID: $($pids -join ', ')）" -ForegroundColor Red
        Write-Host "        先停掉: Stop-Process -Id $($pids -join ',') -Force"
        Write-Host "        或换端口: .\run-dev.ps1 -Port $($Port + 1)"
        Write-Host "        或自动杀: .\run-dev.ps1 -Force"
        exit 1
    }
}

# ---------- 4. 启动 ----------
Write-Host "[OK] http://127.0.0.1:$Port/api/v1/healthz" -ForegroundColor Green
& java -jar $Jar --server.port=$Port
