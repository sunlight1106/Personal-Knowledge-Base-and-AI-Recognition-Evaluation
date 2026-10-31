# v__VERSION__ Windows 测试版

1. 完整解压 Windows 包，双击 `__VERSION__.exe`，将 `runtime-__VERSION__.zip` 保持在同一文件夹。
2. 首次需要已启动的 Docker Desktop（Linux x64 容器）及网络。启动器下载并校验必要镜像，无需另装 Java、Node.js 或 Python。
3. 等待「平台已就绪」，浏览器会自动打开；日常启动复用已有服务与数据。

需要 Windows 10 / 11 x64；建议给 Docker 分配 8 GB 内存并预留至少 12 GB 磁盘。首次管理员为 `admin`，密码在「打开安装目录」中的 `.env` 文件 `BOOTSTRAP_ADMIN_PASSWORD`，登录后请修改密码。

升级前先做完整备份。启动器沿用 `%LOCALAPPDATA%\PKB-AI\0.1` 目录，保留 `.env`、账号与数据卷；目录名是历史兼容路径。新版本含数据库迁移时不要尝试降级。`pkb-personal` 安装版和 `robust-vision` 源码版数据独立，不会互相复制。

更新内容见同目录 `RELEASE-NOTES.md`，网站「设置 → 版本与更新」可查看历史记录。关闭网页或启动窗口不会关闭后台服务；需要停止时在 Docker Desktop 停止 `pkb-personal` 项目。

完整发布文件与校验清单：[v__VERSION__ 发布页](https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/releases/tag/v__VERSION__)。尚未签名的测试程序可能显示发行者未知，请核对 SHA256SUMS，不要关闭系统安全功能。

备份、配置和故障处理文档随运行包保存在安装目录的 `docs` 下。不要删除 `.env` 或 Docker 数据卷；外部 AI 和短信服务需自行配置。
