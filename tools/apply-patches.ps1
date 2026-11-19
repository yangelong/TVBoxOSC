param(
    [string]$PatchDir = ""
)

$ErrorActionPreference = "Stop"

$git = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $git) {
    $c = "C:\Users\Administrator\DoubaoWork\tools\mingit\cmd\git.exe"
    if (Test-Path $c) { $git = $c } else { throw "找不到 git" }
}

$root = Split-Path -Parent $PSScriptRoot
if (-not $PatchDir) { $PatchDir = Join-Path $root "patches" }
if (-not (Test-Path $PatchDir)) { throw "补丁目录不存在：$PatchDir" }

$patches = Get-ChildItem -LiteralPath $PatchDir -Filter *.patch | Sort-Object Name
if (-not $patches) { throw "补丁目录里没有 .patch 文件" }

Push-Location $root
try {
    foreach ($p in $patches) {
        Write-Host ("应用 " + $p.Name)
        & $git apply --3way $p.FullName
        if ($LASTEXITCODE -ne 0) { throw ("打补丁失败：" + $p.Name + "（可能上游同段代码已改动，需要手工处理）") }
    }
    Write-Host "全部补丁已应用"
}
finally {
    Pop-Location
}
