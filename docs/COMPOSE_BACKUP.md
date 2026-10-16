# 完整部署备份与恢复

适用于本仓库默认 Docker Compose 部署。包含 MySQL、MinIO、Redis、训练数据卷和原 `.env`。使用 AES-256-GCM 加密归档，密码经 scrypt 派生；密码不写入备份或日志。

## 创建备份

在项目根目录打开终端，首次安装：

```powershell
python -m pip install -r scripts/backup/requirements.txt
```

创建备份，输入并妥善保存备份密码：

```powershell
python scripts/backup/compose_backup.py backup backups/my-backup.pkb
```

每次使用新的文件名。工具会先检查 Docker 工具，再暂时停止本 Compose 项目内运行的容器，在无写入状态下复制四个数据卷和配置，最后恢复之前运行的容器。停止时间取决于数据量。输出目录可以选择独立磁盘上的受保护文件夹。**备份含账号数据、附件和密钥，不要提交到 GitHub。** 丢失密码后不能解密。

默认不打包可重新下载的杀毒库、容器镜像或网关证书。另行保存反向代理配置、TLS 证书和所用镜像，或者保证可以取得相同镜像；卷内数据库文件需由相同兼容版本的 MySQL 打开。外部数据库、外部对象存储、其他 Compose 叠加服务及另外挂载的文件不在此工具的自动范围内。不要同时运行多个备份任务。

备份成功后，网页「学习中心 → 回收站与备份」中的管理员状态会显示最近成功时间。只读状态文件放在 `.runtime/backups/status.json`；网页不直接操作 Docker。

## 验证归档

```powershell
python scripts/backup/compose_backup.py verify backups/my-backup.pkb
```

输入原密码。验证包含加密认证、归档结构、各组件长度和 SHA-256。这个步骤确认归档可读取，但不能代替启动恢复实例进行业务验证。

## 恢复演练，不覆盖正在使用的数据

只恢复自己创建且可信的归档。恢复会创建一组全新的 Docker 数据卷，遇到已有目录或同名卷会拒绝执行。

```powershell
python scripts/backup/compose_backup.py restore backups/my-backup.pkb --restore-name pkb-restore-check01
```

恢复配置位于 `.runtime/pkb-restore-check01/`。私下编辑其中的 `.env`，将 `WEB_PORT` 改为未占用端口，例如 `4373`，`PUBLIC_WEB_URL` 改为 `http://localhost:4373`。不要把这个文件贴进截图或聊天。

然后从项目根目录启动独立实例：

```powershell
docker compose -p pkb-restore-check01 --env-file .runtime/pkb-restore-check01/.env -f compose.yaml -f .runtime/pkb-restore-check01/override.json up -d --no-build --wait
```

检查新实例可以登录、笔记和图片能打开、个人配置仍存在、训练任务可读取。确认恢复内容正确后，再安排正式切换；不要在没有验证时删除原数据卷。恢复失败时留下的新卷带 `pkb.restore` 标签，先排查失败原因；工具不会自动删除任何原卷或改写现有 `.env`。

## 定期与异地备份

可以用服务器现有任务计划程序定时运行 `backup`，每次使用不同日期的归档名。无人值守时从受保护的凭据存储设置进程环境变量 `PKB_BACKUP_PASSWORD`，不要把密码写进版本库、命令参数或日志。定期备份属于停机维护，时间应与使用者约定。

将加密归档同步到独立设备或受保护的云存储，再在另一位置执行一次验证。仓库不会自动创建云服务器、购买存储或代为保存备份密码。

## 自动恢复演练

配置每日自动备份后，默认每七天额外执行一次恢复演练。工具会在独立数据卷和禁止外网访问的网络中启动恢复实例，验证服务健康、注册与登录、笔记读写，并抽查最多三份附件的内容校验值。完成后清理本次演练环境，原来的数据卷不会被覆盖。

业务检查从恢复容器内部进行，不开放额外端口。归档中没有笔记或附件时，对应抽查数量会明确记为零。新归档记录实际镜像 ID；清理旧镜像前，先确认需要保留的归档是否依赖这些镜像。

「学习中心 → 回收站与备份」显示最近演练结果。备份可读与业务可恢复分开显示；演练失败不会删除刚生成的有效备份。演练需要额外磁盘空间与容器内存，建议空闲时执行。

也可手动检查自己创建的可信归档：

```powershell
python scripts/backup/restore_drill.py backups/my-backup.pkb
```

密码通过终端隐藏输入，或由现有自动备份计划安全传入。运行状态在 `.runtime/backups/drill.json`，不记录密码、令牌、笔记正文或附件内容。自动备份脚本的 `--drill-every-days 0` 可以关闭定期演练；默认是 7 天。

## 在线快照与数据库日志

不希望停机时，可使用 [在线快照](OPERATIONS_UPGRADE.md#在线备份)。数据库时间点恢复单独提供，恢复后的附件覆盖范围必须核对；它不能替代包含训练检查点的完整备份。训练服务从未启用时，完整备份只包含实际存在的数据组件。
