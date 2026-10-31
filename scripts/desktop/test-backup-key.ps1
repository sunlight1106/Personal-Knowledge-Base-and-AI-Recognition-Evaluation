$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'backup-key.ps1')
$path=Join-Path ([IO.Path]::GetTempPath()) ('pkb-key-acl-'+[guid]::NewGuid().ToString('N')+'.txt')
try {
    [IO.File]::WriteAllText($path,'synthetic fixture, not a backup key')
    $hash=(Get-FileHash -LiteralPath $path).Hash
    $owner=(Get-Acl -LiteralPath $path).Owner
    for($attempt=0;$attempt -lt 2;$attempt++) {
        Protect-DesktopBackupKey $path
        $acl=Get-Acl -LiteralPath $path
        if(-not $acl.AreAccessRulesProtected -or $acl.Owner -ne $owner){throw 'Unexpected inheritance or ownership change.'}
        $expected=@([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18') | Sort-Object -Unique
        $actual=@($acl.Access | ForEach-Object {$_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value} | Sort-Object -Unique)
        if(@(Compare-Object $expected $actual).Count -ne 0){throw 'Backup key access is not restricted to the account and SYSTEM.'}
        foreach($rule in $acl.Access) {
            if($rule.IsInherited -or $rule.AccessControlType -ne 'Allow' -or $rule.FileSystemRights -ne 'FullControl'){throw 'Unexpected backup key access rule.'}
        }
        if((Get-FileHash -LiteralPath $path).Hash -ne $hash){throw 'Key contents changed.'}
    }
    $rejected=$false
    try {Protect-DesktopBackupKey ([IO.Path]::GetTempPath())} catch {$rejected=$true}
    if(-not $rejected){throw 'Directories must be rejected.'}
    Write-Host 'PASS: backup key access is restricted, repeatable, preserves ownership and contents, and rejects directories.'
} finally {
    if(Test-Path -LiteralPath $path -PathType Leaf){Remove-Item -LiteralPath $path}
}
