# 不编译源码，直接安装

**Windows 用户优先使用 [0.1 测试版启动器](TEST_RELEASE_0.1.md)**：解压 ZIP 后双击 `0.1.exe`，无需 Python。下面的命令行流程用于按提交编号发布的镜像包。

发布页提供 Linux amd64 容器镜像。Windows 使用 Docker Desktop 的 Linux 容器即可；ARM 电脑暂时使用源码部署。预构建包省去 Java、前端和训练环境的本机构建，第一次仍需下载镜像与病毒库。

## 第一次安装

1. 打开仓库的 **Releases**，选择带有 `backend.tar.gz`、`frontend.tar.gz`、`code-lab.tar.gz`、`training.tar.gz`、`minio.tar.gz`、`SHA256SUMS` 和 `SOURCE.json` 的完整版本。发布名称为 `sha-` 加 40 位提交编号。
2. 下载该版本的 **Source code (zip)**，解压到固定目录。安装 Docker 和 Python 3.10 或以上，打开 Docker，等待引擎就绪。
3. 在项目目录打开终端。把下面的 `sha-完整提交编号` 换成发布页的真实版本号：

```sh
python scripts/release/install.py --version sha-完整提交编号
python scripts/release/install.py --version sha-完整提交编号 --apply
```

第一条只检查发布完整性和 Docker 环境。第二条下载并校验五个镜像、导入 Docker、生成首次配置、等待服务健康。默认地址 `http://localhost:4173`；首次安装可加 `--port 4273`。管理员密码在本地 `.env` 中，不会打印到终端。

脚本至少要求 8 GB 可用空间。数据库、缓存和杀毒镜像仍由 Docker 下载。镜像与部署配置均核对校验值；版本不匹配、包缺失或下载损坏时会停止。

## 更新已有安装

先准备备份工具依赖：

```sh
python -m pip install -r scripts/backup/requirements.txt
```

保留原 `.env`、数据卷和 `.runtime`，将源码更新到目标发布对应的提交，再执行上述检查和安装命令。Git 用户应先保存本地修改，再切换到发布的完整提交编号；ZIP 用户用对应版本源码替换应用文件，不能覆盖原 `.env`。

更新过程会先下载校验镜像，再询问加密备份密码，备份并验证当前数据库与文件，最后应用新版。已有安装备份失败时不会开始升级。备份期间会短暂停机；请安排在空闲时间。

日常继续使用 `start.cmd` 启动现有容器，不需要每次下载。手动重建发布容器时，需要同时使用 `compose.yaml` 和 `compose.images.yaml` 并指定 `PKB_RELEASE_TAG`；单独运行源码构建命令会切回本地构建方式。

## 更新失败

先看 `.runtime/release-status.json` 中的状态和备份路径。该记录只包含版本、镜像 ID 和备份位置，不保存密码或正文。

数据库迁移后不能只换回旧镜像。按 [完整备份与恢复](COMPOSE_BACKUP.md) 将升级前备份恢复到独立的新数据卷，验证后切换；保留原卷直到确认恢复成功。脚本不会清空数据卷，也不会自动降级数据库。

## 发布方式

主分支的 Verify 检查通过后，Release images 工作流构建五个镜像。全部构建完成才发布可下载版本；失败时不会产生半套安装包。同一提交的正式包不覆盖重发。

GitHub Actions 的首次发布可能较慢。以 Releases 中实际存在的完整附件为准，源码提交成功不等于预构建包已发布。
