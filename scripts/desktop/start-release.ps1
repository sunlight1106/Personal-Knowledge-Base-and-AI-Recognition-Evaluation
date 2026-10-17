param([switch]$CheckOnly)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=New-Object Text.UTF8Encoding($false)
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
Set-Location -LiteralPath $root
$manifest=Get-Content -LiteralPath (Join-Path $root 'release.json') -Raw | ConvertFrom-Json
if ($manifest.version -ne '0.1' -or $manifest.images.Count -ne 5) { throw 'Invalid release manifest.' }
$expected=@('backend','frontend','code-lab','training','minio')
foreach($image in $manifest.images) {
    if($image.name -notin $expected -or $image.sha256 -notmatch '^[a-f0-9]{64}$' -or $image.id -notmatch '^sha256:[a-f0-9]{64}$' -or $image.file -ne ($image.name+'.tar.gz')) { throw 'Invalid image manifest.' }
}
if (@($manifest.images.name | Select-Object -Unique).Count -ne 5) { throw 'Duplicate release image.' }
$infrastructure=@{mysql='mysql';redis='redis';clamav='clamav/clamav'}
if (@($manifest.infrastructure).Count -ne 3 -or @($manifest.infrastructure.name | Select-Object -Unique).Count -ne 3) { throw 'Incomplete infrastructure manifest.' }
foreach($dependency in $manifest.infrastructure) {
    if (-not $infrastructure.ContainsKey($dependency.name) -or $dependency.image -notmatch ('^'+[regex]::Escape($infrastructure[$dependency.name])+'@sha256:[a-f0-9]{64}$')) { throw 'Invalid pinned infrastructure image.' }
}
if($CheckOnly) { Write-Host 'PASS: release 0.1 manifest; no services or private settings changed.'; exit 0 }
New-Item -ItemType Directory -Force -Path '.runtime' | Out-Null
$guard=$null
try {
    $guard=[IO.File]::Open((Join-Path $root '.runtime\desktop.lock'),'OpenOrCreate','ReadWrite','None')
    if(-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw '请先安装 Docker Desktop（Windows / Linux 容器），再点击重试：https://www.docker.com/products/docker-desktop/' }
    . "$root\scripts\ensure-docker.ps1"
    Ensure-Docker
    $engine=& docker info --format '{{.OSType}}/{{.Architecture}}'
    if($LASTEXITCODE -ne 0 -or $engine -notmatch '^linux/(x86_64|amd64)$') { throw '0.1 测试版需要 Docker Desktop 的 Linux x64 容器。' }
    $env:COMPOSE_PROJECT_NAME='pkb-personal'
    $env:COMPOSE_FILE='compose.yaml;compose.desktop.yaml'
    $env:PKB_PREBUILT_RELEASE='0.1'
    # Only the installer holds this trust flag. All image IDs are verified before use.
    $localImages=@{}
    & docker image ls --no-trunc --format '{{.Repository}}:{{.Tag}} {{.ID}}' | ForEach-Object {
        $entry=$_ -split ' ',2
        if($entry.Count -eq 2) { $localImages[$entry[0]]=$entry[1] }
    }
    if($LASTEXITCODE -ne 0) { throw '无法读取 Docker 镜像。请检查 Docker 状态后重试。' }
    foreach($image in $manifest.images) {
        if($image.name -eq 'training' -and $env:COMPOSE_PROFILES -notmatch '(^|,)training(,|$)') { continue }
        $tag='pkb-'+$image.name+':0.1'
        if($localImages[$tag] -eq $image.id) { continue }
        $cache=Join-Path $root ('.runtime\'+$image.file)
        $drive=New-Object IO.DriveInfo([IO.Path]::GetPathRoot($root))
        if($drive.AvailableFreeSpace -lt 8GB) { throw '安装磁盘至少需要 8 GB 可用空间。' }
        if(-not(Test-Path -LiteralPath $cache) -or (Get-FileHash -LiteralPath $cache -Algorithm SHA256).Hash.ToLowerInvariant() -ne $image.sha256) {
            Write-Host ('首次准备：下载 '+$image.name+'；完成后日常启动不再下载。')
            $url='https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/releases/download/v0.1/'+$image.file
            # curl supports resumable large downloads; checksum is mandatory before loading.
            & curl.exe --fail --location --retry 3 --connect-timeout 30 --max-time 3600 --continue-at - --output ($cache+'.partial') $url
            if($LASTEXITCODE -ne 0) { throw ('下载未完成：'+$image.name+'。可重试继续下载。') }
            if((Get-FileHash -LiteralPath ($cache+'.partial') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $image.sha256) { Remove-Item -LiteralPath ($cache+'.partial'); throw '镜像校验失败，已丢弃损坏文件，请重试。' }
            Move-Item -LiteralPath ($cache+'.partial') -Destination $cache -Force
        }
        Write-Host ('正在导入 '+$image.name+'…')
        & docker load --input $cache
        if($LASTEXITCODE -ne 0) { throw 'Docker 镜像导入失败。请检查可用磁盘空间。' }
        $loaded=& docker image inspect $tag --format '{{.Id}}'
        if($LASTEXITCODE -ne 0 -or $loaded -ne $image.id) { throw '导入后的镜像与发布清单不一致。' }
    }
    $port=4173
    if(-not(Test-Path -LiteralPath '.env')) {
        while($port -lt 4183) {
            $listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,$port)
            try { $listener.Start(); break } catch { $port++ } finally { $listener.Stop() }
        }
        if($port -ge 4183) { throw '4173–4182 端口均已占用，请释放一个端口后重试。' }
    }
    & "$root\deploy.ps1" -Port $port *>&1 | Tee-Object -FilePath '.runtime\desktop-startup.log'
    $status=Get-Content '.runtime\startup-status.json' -Raw | ConvertFrom-Json
    if($status.state -ne 'ready') { throw '服务尚未就绪，请检查启动日志。' }
    [IO.File]::WriteAllText((Join-Path $root '.runtime\desktop-url.txt'),$status.url,(New-Object Text.UTF8Encoding($false)))
    Write-Host '平台已就绪。首次管理员账号为 admin；密码在安装目录的 .env 中。'
} catch { Write-Host $_.Exception.Message; exit 1 }
finally { if($guard) { $guard.Dispose() } }
