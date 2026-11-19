# 推送到你自己的 GitHub 仓库
# 用法： pwsh -File .\tools\push-fork.ps1
# 首次推送会要求认证：用户名填 GitHub 用户名，密码填 Personal Access Token（repo 权限）

$ErrorActionPreference = "Stop"

$git = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $git) {
    $c = "C:\Users\Administrator\DoubaoWork\tools\mingit\cmd\git.exe"
    if (Test-Path $c) { $git = $c } else { throw "找不到 git" }
}

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    Write-Host "1/3 推送纯上游基线 -> upstream"
    & $git push origin main:upstream
    if ($LASTEXITCODE -ne 0) { throw "upstream 推送失败（认证或网络问题）" }

    Write-Host "2/3 推送你的源码分支 -> source（约 30MB，首次稍慢）"
    & $git push origin source:source
    if ($LASTEXITCODE -ne 0) { throw "source 推送失败" }

    Write-Host "3/3 推送 CI 改造 -> master（快进，不需 force）"
    & $git push origin ci:master
    if ($LASTEXITCODE -ne 0) { throw "master 推送失败（可能远端有新提交，先 git fetch 再 rebase ci）" }

    Write-Host "`n推送完成。远端分支："
    & $git ls-remote --heads origin
}
finally {
    Pop-Location
}
