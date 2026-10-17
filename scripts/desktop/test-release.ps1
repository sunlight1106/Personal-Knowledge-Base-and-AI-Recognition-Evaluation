$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$fixture=Join-Path $workspace ('.runtime\desktop-manifest-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'scripts\desktop') | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start-release.ps1') -Destination (Join-Path $fixture 'scripts\desktop\start-release.ps1')
$manifest=@{version='0.1';images=@('backend','frontend','code-lab','training','minio') | ForEach-Object { @{name=$_;file=$_+'.tar.gz';sha256=('a'*64);id='sha256:'+('b'*64)} }}
$manifest.infrastructure=@(@{name='mysql';image='mysql@sha256:'+('c'*64)},@{name='redis';image='redis@sha256:'+('c'*64)},@{name='clamav';image='clamav/clamav@sha256:'+('c'*64)})
$scriptPath=Join-Path $fixture 'scripts\desktop\start-release.ps1'
function Check-Manifest([bool]$valid,[string]$label) {
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 -LiteralPath (Join-Path $fixture 'release.json')
    $probe=New-Object Diagnostics.Process
    $probe.StartInfo.FileName='powershell.exe'
    $probe.StartInfo.Arguments='-NoProfile -ExecutionPolicy Bypass -File "'+$scriptPath+'" -CheckOnly'
    $probe.StartInfo.UseShellExecute=$false; $probe.StartInfo.CreateNoWindow=$true
    $probe.StartInfo.RedirectStandardOutput=$true; $probe.StartInfo.RedirectStandardError=$true
    [void]$probe.Start(); $stdout=$probe.StandardOutput.ReadToEndAsync(); $stderr=$probe.StandardError.ReadToEndAsync()
    if(-not $probe.WaitForExit(15000)) { $probe.Kill(); throw 'Manifest validation timed out.' }
    $code=$probe.ExitCode; $probe.Dispose()
    if(($code -eq 0) -ne $valid) { throw $label }
    Write-Host ('PASS: '+$label)
}
try {
    Check-Manifest $true 'Complete pinned manifest is accepted without Docker or network'
    $manifest.version='other';Check-Manifest $false 'Unexpected version is rejected';$manifest.version='0.1'
    $manifest.images[0].file='../outside.tar.gz';Check-Manifest $false 'Path traversal is rejected';$manifest.images[0].file='backend.tar.gz'
    $manifest.images[0].sha256='bad';Check-Manifest $false 'Invalid checksums are rejected';$manifest.images[0].sha256='a'*64
    $manifest.images[0].name='frontend';$manifest.images[0].file='frontend.tar.gz';Check-Manifest $false 'Duplicate image entries are rejected'
    $manifest.images[0].name='backend';$manifest.images[0].file='backend.tar.gz'
    $manifest.infrastructure[0].image='mysql:latest';Check-Manifest $false 'Mutable infrastructure tags are rejected'
    $manifest.infrastructure[0].image='untrusted/mysql@sha256:'+('c'*64);Check-Manifest $false 'Unexpected infrastructure repositories are rejected'
    $manifest.infrastructure[0].image='mysql@sha256:'+('c'*64)
    $manifest.infrastructure=$manifest.infrastructure[0..1];Check-Manifest $false 'Missing infrastructure is rejected'
    if(Test-Path -LiteralPath (Join-Path $fixture '.env')) { throw 'Validation must not create credentials.' }
    Write-Host 'PASS: Validation leaves private configuration untouched'
} finally {
    Set-Location -LiteralPath $workspace
    $resolved=[IO.Path]::GetFullPath($fixture)
    if(-not $resolved.StartsWith((Join-Path $workspace '.runtime\desktop-manifest-'),[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe fixture path.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
