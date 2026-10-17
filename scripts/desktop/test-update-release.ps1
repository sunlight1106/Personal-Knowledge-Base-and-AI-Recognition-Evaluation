$ErrorActionPreference='Stop'
. "$PSScriptRoot\..\start-project.ps1"
. "$PSScriptRoot\update-release.ps1"
$script:calls=New-Object 'Collections.Generic.List[string]'
$script:actual=@{backend='api';'model-worker'='api';frontend='new';'code-lab'='code';minio='storage'}
$images=@('backend','frontend','code-lab','minio' | ForEach-Object { @{name=$_;id=$script:actual[$_]} })
$script:apply=$true; $script:inspectFails=$false
function docker {
    $line=$args -join ' ';$script:calls.Add($line);$global:LASTEXITCODE=0
    if ($line -eq 'compose ps --all --quiet') { return @('container1','container2') }
    if ($args[0] -eq 'inspect') {
        if ($script:inspectFails) { $global:LASTEXITCODE=1;return }
        return @($script:actual.Keys | ForEach-Object { '/pkb-personal-'+$_+'-1 '+$script:actual[$_] })
    }
    if ($script:apply -and $line -match '^compose up') { foreach($service in $script:actual.Keys.Clone()) { if($args -contains $service) { $script:actual[$service]=if($service -eq 'model-worker'){'api'}else{($images | Where-Object name -eq $service).id} } } }
}
function Assert($condition,$message) { if(-not $condition){throw $message};Write-Host ('PASS: '+$message) }
Update-ReleaseApplications $images
Assert (-not @($script:calls | Where-Object {$_ -match '^compose (up|stop|build|pull)'}).Count) 'Current image IDs retain the fast path'
$script:actual.frontend='old';$script:calls.Clear();Update-ReleaseApplications $images
Assert ($script:calls -contains 'compose up -d --no-deps --no-build --wait --wait-timeout 240 frontend') 'A healthy old frontend is replaced with the verified release'
Assert (-not @($script:calls | Where-Object {$_ -match '^compose (up|stop).* (mysql|redis|minio|clamav|backend|model-worker)'}).Count) 'Frontend repair preserves all storage and backend containers'
$script:actual.backend='old';$script:calls.Clear();Update-ReleaseApplications $images
Assert ($script:calls -contains 'compose stop frontend model-worker backend') 'Backend updates stop old API processes before migration'
Assert ($script:calls.IndexOf('compose stop frontend model-worker backend') -lt $script:calls.IndexOf('compose up -d --no-deps --no-build --wait --wait-timeout 240 frontend')) 'Frontend returns after the backend update'
$script:actual.frontend='old';$script:apply=$false;$rejected=$false
try{Update-ReleaseApplications $images}catch{$rejected=$true}
Assert $rejected 'An unchanged old image cannot be reported as updated'
$script:apply=$true;$script:actual.frontend='new';$script:actual.minio='old';$script:calls.Clear();$rejected=$false
try{Update-ReleaseApplications $images}catch{$rejected=$true}
Assert ($rejected -and -not @($script:calls | Where-Object {$_ -match '^compose (up|stop)'}).Count) 'Storage changes fail safely without recreating data services'
$script:actual.minio='storage';$script:inspectFails=$true;$rejected=$false
try{Update-ReleaseApplications $images}catch{$rejected=$true}
Assert $rejected 'Inspection failure cannot silently skip an update'
Remove-Item Function:docker
