param(
    [Parameter(Mandatory = $true)][string]$Ref,
    [string]$Repo = "q215613905/TVBoxOS",
    [switch]$SkipRebase
)

$ErrorActionPreference = "Stop"

$git = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $git) {
    $c = "C:\Users\Administrator\DoubaoWork\tools\mingit\cmd\git.exe"
    if (Test-Path $c) { $git = $c } else { throw "找不到 git：把 git 加到 PATH，或改本脚本里的路径" }
}

$root = Split-Path -Parent $PSScriptRoot
$tmp = $null
Push-Location $root
try {
    $dirty = & $git status --porcelain
    if ($dirty) { throw "工作区有未提交改动，先 commit 或 stash，否则会被上游快照覆盖" }

    $cur = (& $git rev-parse --abbrev-ref HEAD).Trim()

    Write-Host "下载上游 $Repo @ $Ref ..."
    $tmp = Join-Path $env:TEMP ("tvbox-up-" + [guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $tmp | Out-Null
    $tgz = Join-Path $tmp "src.tar.gz"
    & curl.exe -fsSL --retry 3 -o $tgz "https://codeload.github.com/$Repo/tar.gz/$Ref"
    if ($LASTEXITCODE -ne 0) { throw "下载失败：codeload 不可达，或该 commit/tag 不存在" }
    & tar -xzf $tgz -C $tmp
    $src = Get-ChildItem -LiteralPath $tmp -Force -Directory | Select-Object -First 1

    & $git checkout -q main
    & $git rm -r -q -f --ignore-unmatch . | Out-Null
    Get-ChildItem -LiteralPath $src.FullName -Force | Copy-Item -Destination $root -Recurse -Force
    & $git add -A

    $staged = & $git diff --cached --name-only
    if ($staged) {
        & $git commit -q -m "upstream: $Repo @ $Ref"
        Write-Host ("已记录上游快照，变更文件 " + ($staged | Measure-Object).Count + " 个")
    }
    else {
        & $git commit -q --allow-empty -m "upstream: $Repo @ $Ref (无内容变化)"
        Write-Host "上游内容与当前基线一致，已记录空提交"
    }

    if (-not $SkipRebase -and $cur -ne "main") {
        Write-Host "把分支 $cur 变基到新的 main ..."
        & $git checkout -q $cur
        & $git rebase main
        if ($LASTEXITCODE -ne 0) {
            Write-Host "变基冲突：手工解决后 git add -A; git rebase --continue；想放弃用 git rebase --abort" -ForegroundColor Yellow
        }
    }
    else {
        & $git checkout -q $cur
    }

    Write-Host "`n当前分支图："
    & $git log --graph --oneline --decorate --all
}
finally {
    if ($tmp -and (Test-Path $tmp)) { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
    Pop-Location
}
