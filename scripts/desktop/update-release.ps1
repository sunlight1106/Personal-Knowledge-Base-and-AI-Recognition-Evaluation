# Called only after the launcher has verified and loaded every selected image.
# Never recreate databases or storage while updating application containers.
function Update-ReleaseApplications($Images, [switch]$Training) {
    $expected = @{}
    foreach ($image in $Images) { $expected[$image.name] = $image.id }
    $mapping = @{ backend='backend'; 'model-worker'='backend'; frontend='frontend'; 'code-lab'='code-lab'; minio='minio' }
    if ($Training) { $mapping.training='training' }
    $ids = @(& docker compose ps --all --quiet)
    if ($LASTEXITCODE -ne 0 -or -not $ids.Count) { throw 'Cannot inspect installed release containers.' }
    $lines = @(& docker inspect --format '{{.Name}} {{.Image}}' @ids)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify installed release images.' }
    $actual = @{}
    foreach ($line in $lines) {
        $parts = $line -split ' ',2
        if ($parts.Count -eq 2) { $actual[($parts[0] -replace '^/pkb-personal-(.+)-[0-9]+$','$1')] = $parts[1] }
    }
    foreach ($service in $mapping.Keys) {
        if (-not $actual.ContainsKey($service) -or -not $expected.ContainsKey($mapping[$service])) { throw ('Missing release service: '+$service) }
    }
    if ($actual.minio -ne $expected.minio) { throw 'Storage image updates require a separate migration; existing storage was preserved.' }
    $changed = @($mapping.Keys | Where-Object { $_ -ne 'minio' -and $actual[$_] -ne $expected[$mapping[$_]] })
    if (-not $changed.Count) { Write-Host 'Installed applications match this release; no update is needed.'; return }
    Write-Host ('Updating verified applications: '+($changed -join ', '))
    if ('backend' -in $changed -or 'model-worker' -in $changed) {
        Invoke-Compose -ComposeArguments @('stop','frontend','model-worker','backend')
        $changed = @(@('backend','model-worker','frontend') + $changed | Select-Object -Unique)
    }
    $services = @($changed | Where-Object { $_ -ne 'frontend' })
    if ($services.Count) { Invoke-Compose -ComposeArguments (@('up','-d','--no-deps','--no-build','--wait','--wait-timeout','240') + $services) }
    if ('frontend' -in $changed) { Invoke-Compose -ComposeArguments @('up','-d','--no-deps','--no-build','--wait','--wait-timeout','240','frontend') }
    # A health check alone cannot prove that an old healthy image was replaced.
    $ids = @(& docker compose ps --all --quiet)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect updated release containers.' }
    $lines = @(& docker inspect --format '{{.Name}} {{.Image}}' @ids)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot verify updated release images.' }
    $actual = @{}
    foreach ($line in $lines) { $parts=$line -split ' ',2; if ($parts.Count -eq 2) { $actual[($parts[0] -replace '^/pkb-personal-(.+)-[0-9]+$','$1')]=$parts[1] } }
    foreach ($service in $mapping.Keys) {
        if ($actual[$service] -ne $expected[$mapping[$service]]) { throw ('Release image was not applied: '+$service) }
    }
}
