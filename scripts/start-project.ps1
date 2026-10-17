# Kept separate from secret generation so startup decisions can be tested without Docker.
function Invoke-Compose([string[]]$ComposeArguments) {
    # Compose writes ordinary progress to stderr. Windows PowerShell 5.1 must
    # decide success from the exit code, not turn progress into a fatal error.
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        & docker compose @ComposeArguments 2>&1 | ForEach-Object { Write-Host ([string]$_) }
        $composeExit=$LASTEXITCODE
    } finally { $ErrorActionPreference=$previousPreference }
    if ($composeExit -ne 0) { throw 'Docker Compose failed. Inspect: docker compose logs --tail 100' }
}

function Start-Platform([switch]$Build, [switch]$FullBuild, [switch]$SkipBuild, [switch]$Training, [switch]$Prebuilt) {
    if ($Prebuilt -and ($Build -or $FullBuild)) { throw 'Prebuilt installations cannot build source images.' }
    if (([int]$Build.IsPresent + [int]$FullBuild.IsPresent + [int]$SkipBuild.IsPresent) -gt 1) {
        throw 'Choose only one of -Build, -FullBuild or -SkipBuild.'
    }
    $trainingEnabled = $Training -or ($env:COMPOSE_PROFILES -split ',' -contains 'training')
    $required = @(& docker compose config --services | Where-Object { $_ -ne 'training' -or $trainingEnabled })
    if ($trainingEnabled -and 'training' -notin $required) { $required += 'training' }
    $apps = @('backend','model-worker','code-lab'); if ($trainingEnabled) { $apps += 'training' }
    if ($LASTEXITCODE -ne 0 -or -not $required.Count) { throw 'Cannot read Compose services.' }
    $installed = @(& docker compose ps --all --format '{{.Service}}')
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect installed services.' }
    $missing = @($required | Where-Object { $_ -notin $installed })
    # Repeated clicks should only inspect the current stack. Require every
    # configured service to report running AND healthy before the fast return.
    if (-not $Build -and -not $FullBuild -and -not $missing.Count) {
        $snapshot = @(& docker compose ps --all --format json)
        if ($LASTEXITCODE -eq 0) {
            try {
                $states = @($snapshot | ForEach-Object { ConvertFrom-Json -InputObject $_ })
                $ready = @($states | Where-Object { $_.State -eq 'running' -and $_.Health -eq 'healthy' } | ForEach-Object { $_.Service })
                if (-not @($required | Where-Object { $_ -notin $ready }).Count) {
                    Write-Host 'All configured services are already healthy; no restart is needed.'
                    return
                }
            } catch { Write-Verbose 'Health snapshot could not be parsed; using normal verified startup.' }
        }
    }
    if ($FullBuild -or $missing.Count) {
        if ($Prebuilt) {
            Write-Host 'Installing verified release images without compiling source.'
            Invoke-Compose -ComposeArguments (@('up', '-d', '--no-build') + $required)
            Invoke-Compose -ComposeArguments @('start','--wait','--wait-timeout','240','mysql','redis','backend','frontend')
            return
        }
        if ($SkipBuild) { throw 'This installation is incomplete. Run deploy.ps1 once without -SkipBuild.' }
        Write-Host 'Installing services / applying full configuration. This can download and compile dependencies.'
        Invoke-Compose -ComposeArguments (@('up', '-d', '--build') + $required)
        Invoke-Compose -ComposeArguments @('start','--wait','--wait-timeout','240','mysql','redis','backend','frontend')
    } elseif ($Build) {
        Write-Host 'Building application changes; installed storage services are retained.'
        $buildServices=@('backend','frontend','code-lab'); if ($trainingEnabled) { $buildServices += 'training' }
        Invoke-Compose -ComposeArguments (@('build') + $buildServices)
        # Retire old application processes before migrating, and restart Nginx
        # afterwards so its upstream DNS points at the replacement backend.
        Invoke-Compose -ComposeArguments @('stop', 'frontend', 'model-worker', 'backend')
        Invoke-Compose -ComposeArguments @('start', 'minio', 'clamav')
        Invoke-Compose -ComposeArguments @('start', '--wait', '--wait-timeout', '240', 'mysql', 'redis')
        Invoke-Compose -ComposeArguments (@('up', '-d', '--no-deps', '--no-build', '--wait', '--wait-timeout', '240') + $apps)
        Invoke-Compose -ComposeArguments @('up', '-d', '--no-deps', '--no-build', '--wait', '--wait-timeout', '240', 'frontend')
    } else {
        Write-Host 'Starting installed services without rebuilding, downloading or recreating containers.'
        Invoke-Compose -ComposeArguments (@('start') + $required)
        Invoke-Compose -ComposeArguments @('start','--wait','--wait-timeout','240','mysql','redis','backend','frontend')
        Write-Host 'Core pages are ready. File scanning, queued jobs and optional training have separate readiness checks.'
        Write-Host 'After changing code, run: .\deploy.ps1 -Build'
    }
}
