function Protect-DesktopBackupKey([string]$Path) {
    $keyInfo=Get-Item -LiteralPath $Path -ErrorAction Stop
    if($keyInfo.PSIsContainer -or ($keyInfo.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Backup key must be a regular file.'
    }
    # Persist only the existing DACL. Set-Acl may also request owner/audit writes
    # and fail with SeSecurityPrivilege under Windows PowerShell 5.1.
    $acl=Get-Acl -LiteralPath $keyInfo.FullName
    $acl.SetAccessRuleProtection($true,$false)
    foreach($rule in @($acl.Access)){$acl.RemoveAccessRuleSpecific($rule)}
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    foreach($id in @($sid,(New-Object Security.Principal.SecurityIdentifier 'S-1-5-18'))) {
        $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($id,'FullControl','Allow')))
    }
    $keyInfo.SetAccessControl($acl)
}
