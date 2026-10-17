<p align="center">
  <img src="docs/assets/readme-cover.svg" alt="个人知识库 · 笔记、学习与 AI" width="100%" />
</p>

<h1 align="center">个人知识库与 AI 识别评测</h1>
<p align="center">笔记、词汇、代码与个人 AI，在一个地方。</p>
<p align="center">
  <a href="https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/releases/download/v0.1/0.1.exe"><strong>下载 0.1.exe</strong></a>
  &nbsp; · &nbsp; <a href="docs/TEST_RELEASE_0.1.md">安装指南</a>
  &nbsp; · &nbsp; <a href="docs/README.md">使用文档</a>
  &nbsp; · &nbsp; <a href="https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/issues">问题反馈</a>
</p>

## 下载与启动

**0.1 测试版 · 修订 2** — 修复学习中心断线恢复和新版启动后仍显示旧页面的问题。

| 下载方式 | 怎么用 |
| --- | --- |
| [**0.1.exe**](https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/releases/download/v0.1/0.1.exe) | 双击启动，首次自动下载并校验程序文件 |
| [Windows 压缩包](https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/releases/download/v0.1/PKB-AI-0.1-Windows-x64.zip) | 完整解压，运行其中的 **0.1.exe** |

需要 **Windows 10 / 11 x64** 和已启动的 **Docker Desktop（Linux 容器）**。首次需要联网下载镜像；以后复用已有服务和数据，不需要另装 Java、Node.js 或 Python。

1. 下载并打开启动器，等待「平台已就绪」，浏览器会自动打开。
2. 在启动窗口点击「打开安装目录」，从 `.env` 的 `BOOTSTRAP_ADMIN_PASSWORD` 取得初始密码，用 `admin` 登录。
3. 进入「我的笔记」或「背单词」开始使用。普通笔记、背单词和代码可视化不需要 AI 密钥。

已安装用户重新下载并运行新版启动器即可更新。[安装、停止与故障处理](docs/TEST_RELEASE_0.1.md) · [版本说明与校验文件](https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/releases/tag/v0.1)

Linux、macOS 或需要修改代码，请按 [源码安装与更新](docs/SOURCE_INSTALL.md) 操作。网站可运行在自己的电脑或服务器上；本机安装之间不会自动同步账号和资料。

## 主要功能

| | 可以做什么 |
| --- | --- |
| **笔记与资料** | Markdown / HTML 边写边预览，整理知识库、阅读文档、收藏与摘录；历史版本、回收站和常见格式导出。[使用说明](docs/LEARNING_WORKSPACE.md) |
| **词汇与复习** | 音标、搭配、美式听读与拼写练习；同一单词跨词书接续进度，熟词 Skip，导入、备份和学习统计。[词书与学习](docs/VOCABULARY.md) |
| **代码可视化** | C / Python / Java / JavaScript 算法子集的逐步回放、变量图、运算次数与复杂度分析，支持存入笔记和导出。[支持范围](docs/CODE_LAB.md) |
| **个人 AI** | 连接 Kimi、千问、DeepSeek 等供应商，辅助笔记、词汇和图片理解，管理自己的共享记忆。[配置说明](docs/PERSONAL_AI.md) |
| **交流与管理** | 身份码找人、私聊、群组与资料共享；角色权限、登录记录、举报审核和用户统计。[群组](docs/GROUPS.md) · [管理](docs/ADMINISTRATION.md) |

## 界面预览

![首页：笔记、每日学习与探索入口](docs/assets/home-preview.png)

中英切换、深浅主题与四套配色。动态水纹可用右上角的波浪图标开启或暂停；常用页面可固定到顶部。

## 使用与部署

| 你想做什么 | 从这里开始 |
| --- | --- |
| 第一次使用，了解各个入口 | [分步使用指南](docs/USAGE.md#第一次使用先做一件小事) |
| 安装、启动或更新 | [Windows 测试版](docs/TEST_RELEASE_0.1.md) · [源码部署](docs/SOURCE_INSTALL.md) |
| 配置自己的模型 | [个人 AI](docs/PERSONAL_AI.md) |
| 备份数据或迁移电脑 | [完整备份与恢复](docs/COMPOSE_BACKUP.md) |
| 部署到服务器，供多人使用 | [域名与 HTTPS 部署](docs/PUBLIC_DEPLOYMENT.md) |
| 查找其他操作 | [全部文档](docs/README.md) |

## 开发与反馈

前端使用 Vue，后端使用 Spring Boot，数据存储使用 MySQL 与 MinIO。开发环境、源码结构和检查命令见 [开发指南](docs/DEVELOPMENT.md)。

这是第一个公开测试版本。[验证记录](docs/RELEASE_0.1_VALIDATION.md) 列出了已检查的范围与限制。遇到问题可 [提交 Issue](https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/issues)，写明版本和操作步骤即可。

[MIT License](LICENSE) · [依赖许可](THIRD_PARTY_NOTICES.md) · [安全说明](docs/SECURITY.md)
