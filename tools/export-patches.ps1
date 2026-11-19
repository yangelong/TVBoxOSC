param(
    [string]$Base = "main",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"

$git = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $git) {
    $c = "C:\Users\Administrator\DoubaoWork\tools\mingit\cmd\git.exe"
    if (Test-Path $c) { $git = $c } else { throw "找不到 git" }
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (-not $OutDir) { $OutDir = Join-Path $root "patches" }
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    Get-ChildItem -LiteralPath $OutDir -Filter *.patch -ErrorAction SilentlyContinue | Remove-Item -Force

    # 以 base 为上游基线，导出所有自定义提交为补丁文件
    & $git format-patch "$Base..HEAD" -o $OutDir --no-signature
    if ($LASTEXITCODE -ne 0) { throw "format-patch 失败" }

    Write-Host "`n导出到 $OutDir ："
    Get-ChildItem -LiteralPath $OutDir -Filter *.patch | Select-Object Name, Length
}
finally {
    Pop-Location
}
