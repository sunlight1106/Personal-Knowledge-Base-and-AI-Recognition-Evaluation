# 从源码安装与更新

[← 项目首页](../README.md) · [文档目录](README.md)

本文适用于准备修改代码，或在 Windows、Linux、macOS 上自行部署的用户。Windows 直接使用请看 [0.1 测试版安装指南](TEST_RELEASE_0.1.md)。两种安装方式使用独立数据，已有资料需要按 [备份与恢复](COMPOSE_BACKUP.md) 迁移。

## 下载项目

**直接下载：** [下载 ZIP](https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/archive/refs/heads/main.zip)，完整解压到固定目录，例如 `D:\KnowledgeAI`。确认目录里有 `compose.yaml`、`deploy.ps1` 和 `cle` 文件夹。

<details>
<summary>使用 Git 下载</summary>

```sh
git clone --depth 1 https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation.git
cd Personal-Knowledge-Base-and-AI-Recognition-Evaluation
```

</details>

## 首次启动

### 01 · 准备 Docker

Windows / macOS 安装并打开 [Docker Desktop](https://docs.docker.com/desktop/)，等待引擎就绪。Windows 使用 WSL 2 和 Linux 容器。Linux 安装 Docker Engine 和 Compose 插件。

Compose 至少为 **2.20.2**，建议给 Docker 分配 **8 GB 内存**。首次安装需要联网下载镜像、依赖和病毒库，比日常启动慢。当前对象存储使用 `linux/amd64`，ARM 电脑需要相应仿真支持。

使用已发布的容器镜像而不在本机编译，可改用 [预构建镜像安装](RELEASE_INSTALL.md)。下面的步骤用于从源码构建。

### 02 · 在项目目录启动

**Windows** — 打开解压后的项目文件夹，在地址栏输入 `powershell` 并回车，粘贴运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1
```

**Linux / macOS** — 在项目目录打开终端，运行：

```sh
sh deploy.sh
```

脚本会生成配置、构建镜像并等待服务就绪。看到 **`Ready: http://localhost:4173`** 后，在浏览器打开这个地址；如果调整过端口，以实际输出为准。

### 03 · 登录自己的空间

| 登录信息 | 去哪里找 |
| :--- | :--- |
| 管理员用户名 | 默认 `admin`；修改过则查看 `.env` 的 `BOOTSTRAP_ADMIN_USERNAME` |
| 管理员初始密码 | 用文本编辑器打开项目根目录的 `.env`，查看 `BOOTSTRAP_ADMIN_PASSWORD=` 后面的内容 |
| 普通用户 | 在登录页点击「注册」，或由管理员创建账号 |

登录后可在个人设置中修改密码。已有安装继续使用原来的密码。

> **保管好 `.env`。** 它包含密码和加密密钥，不要上传到 GitHub，也不要用 `.env.example` 覆盖。恢复已有数据时需要原配置。

<details>
<summary>4173 端口被占用，怎么换一个？</summary>

首次部署可指定端口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1 -Port 4273
```

如果已经生成 `.env`，先修改其中的 `WEB_PORT`，再更新前端容器以应用新端口：

```sh
docker compose up -d --no-deps frontend
```

</details>

## 再次启动与停止

| 操作 | 方法 |
| :--- | :--- |
| Windows 再次启动 | 双击项目目录里的 **`start.cmd`** |
| Linux / macOS 再次启动 | 在项目目录执行 **`sh deploy.sh`** |
| 停止并保留数据 | 在项目目录执行 **`docker compose stop`** |

日常启动会复用已有容器和镜像。Windows 安装了 Python 时会打开启动进度页，没有 Python 也能从终端启动。启动异常或想了解耗时，查看 [启动与排查](STARTUP.md)。

训练服务按需启动，Windows 使用 `deploy.ps1 -Training`，Linux / macOS 使用 `COMPOSE_PROFILES=training sh deploy.sh`。基础页面先就绪，文件上传仍须等待存储与病毒扫描可用。长期监控、在线快照和恢复演练见 [部署维护](OPERATIONS_UPGRADE.md)。

> 普通停止和更新不要使用 `docker compose down -v`，它会删除数据卷。

## 更新版本

**先备份，再更新源码，最后构建新版。** 只运行 `start.cmd` 不会安装新代码。使用预构建包的用户按 [镜像更新步骤](RELEASE_INSTALL.md#更新已有安装) 操作，安装器会先完成加密备份和校验。

Git 下载的用户，在原项目目录运行：

```sh
git pull --ff-only
```

ZIP 下载的用户：下载最新 ZIP 到临时目录，停止原项目，将新版源码覆盖到原项目目录，保留原 `.env` 和本地数据文件。

然后选择对应系统的命令：

```powershell
# Windows
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy.ps1 -Build
```

```sh
# Linux / macOS
sh deploy.sh --build
```

数据库迁移随新版后端启动执行，原数据和 `.env` 保留。如果修改了数据库、缓存或存储组件的 Compose 配置，使用 `-FullBuild` / `--full-build`。多个源码目录默认连接同一组 Compose 服务，不代表多个独立环境。

<details>
<summary>之前下载过旧名称的仓库</summary>

在原项目目录更新一次远程地址即可，本地文件夹不必改名：

```sh
git remote set-url origin https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation.git
```

</details>


## 备份与公网访问

账号和笔记保存在 MySQL，上传文件保存在 MinIO，实际运行数据使用 Docker 命名卷。仓库的 `database/` 保存结构和词库，并非当前用户数据库。迁移前同时备份数据库、文件和原 `.env`，步骤见 [完整备份与恢复](COMPOSE_BACKUP.md)。

默认只监听本机 `127.0.0.1:4173`。让其他人联网使用，需要统一的服务器、域名与 HTTPS，见 [公网部署](PUBLIC_DEPLOYMENT.md)。各自在电脑上启动的站点不会自动互通账号。
