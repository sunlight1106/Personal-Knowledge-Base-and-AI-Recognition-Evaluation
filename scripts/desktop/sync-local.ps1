param(
    [string]$DesktopFolder=(Join-Path ([Environment]::GetFolderPath('Desktop')) '个人知识库 0.1 修订2'),
    [switch]$ReuseImages
)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$installed=Join-Path $env:LOCALAPPDATA 'PKB-AI\0.1'
$desktop=[IO.Path]::GetFullPath($DesktopFolder)
if(-not(Test-Path -LiteralPath (Join-Path $installed '.env')) -or -not(Test-Path -LiteralPath (Join-Path $installed 'release.json'))) {throw 'An existing desktop installation is required.'}
if(-not(Test-Path -LiteralPath $desktop -PathType Container)){throw 'Desktop package folder was not found.'}
$backupRoot=Join-Path $installed '.runtime\desktop-updates'
New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
$guard=[IO.File]::Open((Join-Path $backupRoot 'sync.lock'),'OpenOrCreate','ReadWrite','None')
$savedProject=$env:COMPOSE_PROJECT_NAME; $savedCompose=$env:COMPOSE_FILE; $savedPassword=$env:PKB_BACKUP_PASSWORD
$pointer=[IntPtr]::Zero
$installGuards=@();$launcherMutex=$null;$ownsLauncher=$false
try {
    # Keep another launcher/deployment from writing while the cold backup is taken.
    $created=$false
    $launcherMutex=[Threading.Mutex]::new($true,'Local\PKB-AI-Desktop-Launcher',[ref]$created)
    if(-not $created){throw 'Close the existing desktop launcher before synchronizing.'}
    $ownsLauncher=$true
    foreach($name in @('desktop.lock','deploy.lock')) {
        $installGuards += [IO.File]::Open((Join-Path $installed ('.runtime\'+$name)),'OpenOrCreate','ReadWrite','None')
    }
    $version=([IO.File]::ReadAllText((Join-Path $repo 'VERSION'))).Trim()
    if($version -cnotmatch '^(0|[1-9][0-9]{0,3})\.(0|[1-9][0-9]{0,3})$'){throw 'Invalid current version.'}
    $backupDir=Join-Path $backupRoot ([datetime]::UtcNow.ToString('yyyyMMddHHmmssfffffff'))
    New-Item -ItemType Directory -Path $backupDir | Out-Null
    $keyFile=Join-Path $backupRoot 'key.dpapi'
    if(-not(Test-Path -LiteralPath $keyFile)) {
        $bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
        try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
        ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $keyFile
    }
    . (Join-Path $PSScriptRoot 'backup-key.ps1')
    Protect-DesktopBackupKey $keyFile
    $secure=(Get-Content -LiteralPath $keyFile -Raw).Trim() | ConvertTo-SecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    $env:PKB_BACKUP_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    $env:COMPOSE_PROJECT_NAME='pkb-personal'
    $env:COMPOSE_FILE='compose.yaml;compose.desktop.yaml'
    $configHash=(Get-FileHash -LiteralPath (Join-Path $installed '.env')).Hash
    $archive=Join-Path $backupDir 'before-update.pkb'
    Write-Host 'Backing up desktop data and configuration...'
    & python (Join-Path $repo 'scripts\backup\compose_backup.py') backup $archive --root $installed
    if($LASTEXITCODE -ne 0){throw 'Desktop backup failed; no program files were updated.'}
    & python (Join-Path $repo 'scripts\backup\compose_backup.py') verify $archive --root $installed
    if($LASTEXITCODE -ne 0){throw 'Desktop backup verification failed; no program files were updated.'}
    $env:PKB_BACKUP_PASSWORD=$null
    $env:COMPOSE_PROJECT_NAME=$savedProject;$env:COMPOSE_FILE=$savedCompose
    $buildArgs=@((Join-Path $repo 'scripts\desktop\build-local.py'),'--desktop',$desktop,'--installed',$installed,'--backup-dir',$backupDir)
    if($ReuseImages){$buildArgs+='--reuse-images'}
    Write-Host 'Building and packaging the current desktop snapshot...'
    & python @buildArgs
    if($LASTEXITCODE -ne 0){throw 'Local package preparation failed.'}
    $exe=Join-Path $desktop ($version+'.exe')
    foreach($lock in $installGuards){$lock.Dispose()};$installGuards=@()
    $launcherMutex.ReleaseMutex();$ownsLauncher=$false
    Write-Host 'Verifying the desktop EXE startup...'
    $process=Start-Process -FilePath $exe -ArgumentList '--smoke-test' -WindowStyle Hidden -PassThru
    if(-not $process.WaitForExit(600000)){throw 'Launcher is still preparing. Check launcher-smoke.log before retrying.'}
    $process.Refresh()
    if($process.ExitCode -ne 0){throw 'Desktop EXE startup failed. Inspect the installation launcher-smoke.log.'}
    if((Get-FileHash -LiteralPath (Join-Path $installed '.env')).Hash -ne $configHash){throw 'Installation configuration changed; review before claiming successful synchronization.'}
    $status=Get-Content -LiteralPath (Join-Path $installed '.runtime\startup-status.json') -Raw | ConvertFrom-Json
    $uri=[uri]$status.url
    if($status.state -ne 'ready' -or $uri.Host -ne 'localhost' -or $uri.Scheme -ne 'http'){throw 'Desktop readiness address is invalid.'}
    foreach($path in @('/','/api/v1/public/models')) {
        if((Invoke-WebRequest -UseBasicParsing -Uri ($status.url.TrimEnd('/')+$path) -TimeoutSec 30).StatusCode -ne 200){throw 'Desktop HTTP verification failed.'}
    }
    $manifest=Get-Content -LiteralPath (Join-Path $installed 'release.json') -Raw | ConvertFrom-Json
    if($manifest.version -ne $version -or $manifest.localSnapshot -ne $true){throw 'The launcher did not install this snapshot.'}
    @{version=$version;snapshotId=$manifest.snapshotId;url=$status.url;verifiedAt=[datetime]::UtcNow.ToString('o');backup=$archive;desktop=$desktop} |
        ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $backupRoot 'last-success.json')
    Write-Host ('Desktop updated and verified: '+$version+' | '+$status.url)
    Write-Host ('Backup: '+$archive)
} finally {
    $env:COMPOSE_PROJECT_NAME=$savedProject;$env:COMPOSE_FILE=$savedCompose;$env:PKB_BACKUP_PASSWORD=$savedPassword
    if($pointer -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
    foreach($lock in $installGuards){$lock.Dispose()}
    if($ownsLauncher){$launcherMutex.ReleaseMutex()}
    if($launcherMutex){$launcherMutex.Dispose()}
    $guard.Dispose()
}
