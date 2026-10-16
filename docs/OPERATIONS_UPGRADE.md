# 启动、监控与恢复

日常入口仍是 `start.cmd`（Windows）或 `sh deploy.sh`。已有容器会直接启动，不会重新编译。

## 哪些服务必须先准备好

登录、笔记、词汇和群组需要 MySQL、Redis 和后端。启动脚本先确认这些服务与网页入口可用；文件扫描、对象存储和训练各自检查状态。文件扫描不可用时，上传会失败并给出提示，不会跳过扫描。

代码可视化使用独立的 `code-lab` 服务，日常部署会启动它；该服务不可用不会阻断基础页面。用法与限制见 [代码可视化](CODE_LAB.md)。

训练改为按需启动。第一次使用训练时，在项目目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1 -Training
```

Linux / macOS：`COMPOSE_PROFILES=training sh deploy.sh`。更新训练代码时同时使用 `-Build -Training`，或 `COMPOSE_PROFILES=training sh deploy.sh --build`。已有训练容器不会被日常启动脚本自动关闭。

管理员可在运行状态区域点击「启动分析」，查看当前后端启动中耗时最多的阶段。父步骤包含子步骤，不能把列表时长直接相加。这里记录的是后端初始化，不包含 Docker Desktop 启动和首次下载镜像。

## 长期监控

管理页面保留最近五分钟的即时状态。需要跨重启保存曲线时，配置可选的 Prometheus：

```sh
python scripts/ops/monitoring.py
docker compose up -d --no-deps --no-build backend model-worker
docker compose -f compose.yaml -f compose.monitoring.yaml up -d prometheus
```

前两条命令要在本次新版应用镜像构建完成后执行；第二条会让应用读取新生成的监控凭据。打开 `http://localhost:9090`。监控只监听本机，保存 30 天数据；不要把该端口直接映射到公网。

Alerts 页面包含服务不可达、5xx 错误比例、P95 请求耗时和磁盘空间告警。这里只生成监控告警，不会自动给用户发送邮件。指标接口要求独立的随机凭据，不使用管理员密码；凭据保存在本机 `.env` 和 `.runtime/monitoring` 中，不要提交到仓库。监控 API 经前端路由访问当前发布版本，任务服务单独采集。

## 在线备份

完整停机备份仍见 [备份与恢复](COMPOSE_BACKUP.md)。只备份数据库和网页上传文件、希望继续使用站点时：

```sh
python -m pip install -r scripts/backup/requirements.txt
python scripts/backup/online_snapshot.py backup backups/online-20261016.pkb
python scripts/backup/online_snapshot.py verify backups/online-20261016.pkb
```

运行时输入备份密码。忘记密码无法解密；归档包含加密后的配置与供应商密钥，应保存在受保护的位置。在线快照期间暂停文件永久删除和账号清理，其余读写继续；失败会释放锁，进程异常退出后锁最多保留两小时。

快照包含一致性 SQL、所引用的不可变文件、镜像版本和加密配置。它不包含训练模型检查点，也不备份 Redis 任务传输状态。训练数据需要完整备份。备份时不要同时执行数据库迁移、重命名或删除表。

验证密码和校验和通过后，恢复到**新环境**：

```sh
python scripts/backup/online_snapshot.py restore backups/online-20261016.pkb --name pkb-online-restore-check001 --port 14373
```

恢复工具拒绝已有同名目录、容器或数据卷。恢复出的站点默认关闭外部 AI、邮件、短信和后台学习任务，并撤销原登录会话。任务 worker 保持停止。核对笔记、附件、权限和待处理任务后，再决定是否替换线上环境；工具不会自动切换域名。

## 数据库时间点恢复

在线快照记录二进制日志起点。之后可归档数据库仍保留的已关闭日志：

```sh
python scripts/backup/binlog_archive.py archive backups/binlogs-20261016.pkb
```

归档会轮转一次日志，不删除数据库日志。应在数据库自动清理日志之前归档；文件序号缺失时不能完成该时间范围的恢复。

需要恢复误操作之前的数据时，先按上一节创建独立快照环境，再确认目标日志及事务结束位置，执行：

```sh
docker build -f scripts/backup/BinlogTools.Dockerfile -t robust-vision-binlog-tools:8.4 .
python scripts/backup/binlog_archive.py replay backups/binlogs-20261016.pkb --project pkb-online-restore-check001 --stop-file binlog.000123 --stop-position 45678 --database-only
```

第一条只需在首次恢复或更新恢复工具时执行。日志解析工具独立运行，不修改正在使用的数据库镜像；解析时禁用网络并以只读方式挂载已校验的日志。

示例日志名和位置必须替换为实际值。工具检查来源服务器、日志连续性和校验和；只向独立恢复环境重放，并先停止该环境的应用写入。中途失败时必须重新恢复一份快照再重试，避免重复重放。

**这一步只恢复数据库。** 快照之后新增的图片、附件和训练文件需要对应的对象备份，数据库日志无法补回文件字节。因此不能仅凭数据库重放成功就宣称整站已经恢复到任意时间点。恢复完成后应用仍保持停止，先检查文件覆盖范围和业务数据。

## 保留旧版本的 API 发布

只有确认数据库迁移向后兼容时，才能让两个 API 版本短时并存。新版本必须使用已构建的本地镜像：

```sh
python scripts/ops/rolling_deploy.py --image robust-vision-backend:local --compatible-schema
python scripts/ops/rolling_deploy.py --rollback
```

第一条命令等待候选实例就绪，然后切换前端转发；第二条切回仍健康的上一实例。候选失败时不切流量，切换后检查失败则恢复旧路由。旧实例保留以便回退，确认发布成功后再安排清理。

这不是任意版本的数据库回滚，也不更新前端或任务 worker。删除列、改变字段含义等迁移应使用维护窗口；前端与 worker 仍按普通更新流程发布。普通 `-Build` 更新会重新创建前端并恢复默认 `backend` 路由，完成后应清理不再需要的发布槽位。发布记录在 `.runtime/releases`，不要手工修改。

## 重新执行检查

```sh
python scripts/tests/run-mysql-contracts.py
python scripts/tests/run-redis-contracts.py
npm --prefix cle run test:ui
npm --prefix cle run test:unit
```

数据库与 Redis 检查使用独立临时容器；不会读取项目 `.env`。MySQL 检查包含 100 个合成账号、10,000 篇笔记，分 10 / 50 / 100 个并发用户测试。报告写入 `target/multi-user-load.json`；限流只在容量测试里关闭，权限检查保持启用。它测量本机 HTTP 与数据库，不代表浏览器渲染、公网延迟或付费 AI 的响应时间。

只跑功能与权限回归可加 `--skip-load`，此时临时数据库放在内存文件系统中，加快反复建库。容量测试单独运行 `python scripts/tests/run-mysql-contracts.py --tests MultiUserLoadMySqlIntegrationTest`，始终使用磁盘存储；不要把内存数据库的时间当成正式部署性能。真实备份、恢复和日志重放应另外演练。
