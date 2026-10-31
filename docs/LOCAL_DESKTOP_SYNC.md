# 同步本机桌面版本

源码版验证后，在项目根目录运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/desktop/sync-local.ps1
```

固定入口为桌面「个人知识库 0.1 修订2」，文件夹名称保留，内部 EXE 随实际版本更新。
默认重新构建应用镜像，生成包含当前未提交修改的本地快照，随后运行桌面 EXE 并检查主页和 API。
仅当本次修改的镜像已经构建且验证过时，才使用 `-ReuseImages`。

本机同步不上传 GitHub，不改变发布状态，也不绕过正式发布所需的干净提交和 ready 状态检查。
本地快照不包含镜像下载包，需要本机已校验的 Docker 镜像；缺失时会要求重新同步，不会请求不存在的 GitHub 版本。
MySQL、对象存储和可选训练使用原安装镜像。源码版和安装版的数据彼此独立。

更新前会暂停安装版服务，使用已有完整备份工具加密保存数据库、对象文件、Redis 和配置，校验归档后恢复原运行状态。
随后启动并更新应用，保留原 `.env` 和数据卷。任何阶段失败均中止；数据库迁移后不会自动降级。
备份位于 `%LOCALAPPDATA%\PKB-AI\0.1\.runtime\desktop-updates`，旧启动文件仅归档，不应再次运行。

## 验证备份

使用创建备份的同一 Windows 用户。密钥由 Windows DPAPI 保护，不能仅复制密钥文件到另一台电脑使用。
下面只验证归档；恢复到新卷的方法见 [完整备份与恢复](COMPOSE_BACKUP.md)。

```powershell
$installation = Join-Path $env:LOCALAPPDATA 'PKB-AI\0.1'
$backupDirectory = Join-Path $installation '.runtime\desktop-updates'
$secure = (Get-Content -LiteralPath (Join-Path $backupDirectory 'key.dpapi') -Raw).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:PKB_BACKUP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    # 将归档路径替换为本次 before-update.pkb 的完整路径。
    python scripts/backup/compose_backup.py verify '完整归档路径' --root $installation
} finally {
    Remove-Item Env:PKB_BACKUP_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
```

`last-success.json` 记录最近同步成功的版本、镜像快照、网址和备份位置。加密校验通过不等于已做恢复演练。
