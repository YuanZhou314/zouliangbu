# 一键发布（Windows / PowerShell 包装器）
#
#   .\deploy\release.ps1                 # 完整发布
#   .\deploy\release.ps1 -SkipTests      # 跳过测试
#   .\deploy\release.ps1 -Rollback       # 回滚
#
# 实际逻辑在 release.sh，这里只负责找 Git-Bash 并转发参数。
# 之所以不重写一份 PS 版本：避免两套脚本行为漂移。

[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$Rollback
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

# 找 bash
$bash = $null
foreach ($p in @(
    'C:\Program Files\Git\bin\bash.exe',
    'C:\Program Files\Git\usr\bin\bash.exe',
    "$env:LOCALAPPDATA\Programs\Git\bin\bash.exe"
)) {
    if (Test-Path $p) { $bash = $p; break }
}
if (-not $bash) {
    $cmd = Get-Command bash -ErrorAction SilentlyContinue
    if ($cmd) { $bash = $cmd.Source }
}
if (-not $bash) {
    Write-Host '[x] 找不到 Git-Bash，请装 Git for Windows' -ForegroundColor Red
    exit 1
}

# 统一成 /c/... 形式
$shPath = & $bash -c "cygpath -u '$here'" 2>$null
if (-not $shPath) { $shPath = $here -replace '\\','/' -replace '^([A-Za-z]):', '/$1' }
$shPath = $shPath.Trim()

$args = @("$shPath/release.sh")
if ($SkipTests) { $args += '--skip-tests' }
if ($Rollback)  { $args += '--rollback' }

& $bash @args
exit $LASTEXITCODE
