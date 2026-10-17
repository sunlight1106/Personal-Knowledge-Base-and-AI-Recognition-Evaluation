# 本地开发

[← 项目首页](../README.md) · [文档目录](README.md)

只想使用网站，下载 [Windows 测试版](TEST_RELEASE_0.1.md) 即可。修改代码前，先按 [源码部署](SOURCE_INSTALL.md) 启动所需服务。

## 项目结构

| 目录 | 内容 |
| --- | --- |
| `cle/` | Vue 客户端 |
| `src/` | Spring Boot 后端 |
| `ai/` | 模型适配、媒体处理和训练 |
| `database/migrations/` | 数据库迁移 |
| `scripts/` | 启动、打包、备份与维护 |
| `docs/` | 使用和部署文档 |

## 开发环境

需要 Node.js **22.22.2+（22.x）**、JDK **17**、Python **3.10+** 和 FFmpeg。只启动前端无法完成登录，还需要数据库等服务；组件关系见 [部署结构](STACK.md)。

前端安装、检查与构建：

```sh
npm ci --prefix cle
npm run test:unit --prefix cle
npm run test:ui --prefix cle
npm run build --prefix cle
```

后端测试：Windows 运行 `.\mvnw.cmd -B verify`，Linux / macOS 运行 `./mvnw -B verify`。H2 仅用于测试，MySQL 检查见 [数据库说明](../database/README.md)。

修改后需要更新运行中的网站，按 [源码更新步骤](SOURCE_INSTALL.md#更新版本) 重新构建。单独运行测试不会更新已部署的容器。

## 验证与反馈

[功能验收](FUNCTIONAL_TESTS.md) 列出了复跑方法、正常流程和异常边界；[验证记录](VALIDATION.md) 保存实际结果。自动交互检查使用模拟资料，不发送真实消息或收费 AI 请求。

遇到问题可以 [提交 Issue](https://github.com/sunlight1106/Personal-Knowledge-Base-and-AI-Recognition-Evaluation/issues)。请写明使用版本、安装方式、操作步骤、预期结果与实际结果；附截图或错误编号时隐藏个人资料。不要上传 `.env`、密码、API 密钥或完整私人数据。
