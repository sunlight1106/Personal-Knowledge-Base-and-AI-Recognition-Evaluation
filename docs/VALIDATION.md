# 验证记录

## 2026-10-16：新手指引与悬停说明

- 工作台四项入门任务补充分步操作、完成标志和完整说明链接，沿用服务端的账号权限范围与真实完成记录。展开说明不会计入完成，也不会创建学习数据。
- 操作文档新增“第一次使用：跟着做”；GitHub 日常使用说明补充笔记、词汇、导入、复习卡及常见卡点。新指引和问号说明均支持中英文。
- 33 组界面回归共 818 项断言通过。随后修正放大定位与表单标签，针对性复验通过：提示组件 59 项、个人 AI 59 项；新增覆盖合计为 821 项。
- 问号提示覆盖短暂经过不闪现、悬停、键盘聚焦、Esc、移入提示阅读、点击外部、滚动、缩放、切换页面清理、语言切换及弹窗层级。
- test 账号实际浏览器完成 28 项检查：工作台、笔记、词汇设置弹窗、文档、复习卡、个人 AI 与使用指南；验证中英文、浅深主题、1280 / 1440 / 1920 桌面宽度，以及 200% 布局放大。最终检查没有脚本异常，主流程没有失败的 API 响应。
- 前端类型检查及生产构建通过，已更新本机前端，八个 Docker 服务健康。没有修改后端权限或业务数据，没有调用外部 AI；临时登录会话已撤销。


## 2026-10-16：分页、关联搜索与运行管理

- 后端完整回归：620 项，469 通过、151 按环境或显式开关跳过，0 失败。测试报告统计排除了之前运行遗留的旧类报告。
- 独立 MySQL：资料搜索与工作空间 17 项、群组与消息 16 项全部通过；本地检索和健康汇总 5 项、通知分类 3 项另复验通过。未连接实际用户数据库做破坏性测试。
- 新增覆盖：2,000 篇笔记分页和范围统计、61 封消息的分页及跨页未读数量、素材排除消息附件、管理员不能搜索别人私有笔记、失效来源、关联词排序、原文片段位置和长度、监控权限与时间窗口。
- 前端：32 组界面回归、760 项断言，另有 11 项单元测试和生产构建通过。包含实时刷新与已读响应竞争、切换账号、旧请求失效、图片按需加载及临时地址释放、引导状态和中英文文案。
- 本机实际 HTTP：15 组检查通过，包含新分页入口、非法页码与筛选、管理权限、保存搜索重命名和账号隔离、16 次并发分页读取；合成搜索记录随后删除。
- test 账号的 7 个主要页面在中英文、1280 / 1440 / 1920 宽度和 200% 布局放大下共 56 项检查通过，无脚本错误、接口错误或横向溢出。首次检查发现的英文长菜单溢出已修复；高频整页重载触发的实时连接上限在正常间隔复验时恢复，连接限制未放宽。
- 管理员的管理页、工作台和任务页另有 24 项双语与桌面尺寸检查通过；实际浏览器确认搜索保存、重命名、JSON 下载和删除，以及笔记请求断网后重试恢复。临时登录会话会在验收结束时撤销。
- 本机后端、前端及其余六个 Docker 服务健康。
- 安装器：4 项安全测试通过，覆盖清单重复与路径校验、保留原配置、首次独立密钥生成、损坏下载不替换目标；Compose 镜像覆盖配置检查通过。预构建发布结果以 GitHub Releases 的完整附件为准。

未调用付费模型，没有验证外部供应商额度或公网部署容量；本地关联搜索是有界候选排序，不是全库预训练向量检索。


## 2026-10-15：中英文界面切换

- 导航栏加入 EN / 中文切换，外观设置提供语言选择。偏好在当前浏览器保存，并同步到同源的其他标签页；切换不会重载页面。
- 本轮覆盖公开页面、工作空间、学习、笔记、交流、设置和管理界面的应用文案。用户名称、自建角色、笔记正文、代码、消息、文件和学习资料保留原文；权限编码、表单选项值及学习时区不随界面语言改变。
- 31 组界面回归通过；其中语言专项最终为 23 项断言，加上原有 715 项共 738 项。另有 11 项前端单元测试通过。权限、通知专项在后续文案补齐后再次通过。
- 使用 test 账号检查工作空间及设置入口，管理员另检查 8 个页面。检查包含英文显示、脚本错误、桌面横向溢出及无权限入口的跳转；公开的首页、登录、注册、密码找回、文档、模型、离线页面在 1280 / 1440 / 1920 宽度下均无横向溢出。
- 实际浏览器确认登录输入和未提交笔记正文在切换时保留，刷新后仍使用所选语言。英文文案独立加载，中文启动无需下载英文词库。
- 前端类型检查和生产构建通过，已更新本地 Docker 前端。没有修改后端权限策略，也未调用付费模型或发送真实邮件。

## 2026-10-15：首页与工作空间视觉升级

统一首页、工作台、笔记、词汇、知识库、学习统计、设置、群组、管理页面与登录页的纸面层次、导航、表单和留白。首页加入可切换的记录、学习、探索预览；支持方向键、Home / End 和系统减少动态效果设置。预览使用本地示例，不请求模型接口。

- 前端类型检查与生产构建通过；30 组界面回归、715 项断言通过。
- 普通测试账号检查 33 个页面与设置入口，管理员另检查 8 个界面；未发现页面脚本错误、接口错误、空白内容或横向溢出。
- 四套配色分别检查浅色、深色；紧凑密度可切换。工作台、词汇、笔记编辑、实验台、外观设置在 1280、1440、1920 宽度下检查通过。
- 首页、登录、注册、文档、模型目录、离线页另检查上述三种桌面宽度。
- 首页预览、键盘切换、减少动态效果、目录打开与 Escape 关闭、词书切换共 13 项浏览器检查通过。
- 真实浏览器中新建临时笔记，确认自动保存、刷新回读与代码预览正常；随后删除并清理该测试笔记。
- 修正学习面板被组件旧样式覆盖的内边距，移除已替换首页目录的旧样式；更新 README 截图与外观设置说明。

新版前端已在本机部署，8 个服务健康。本轮以桌面显示和前端交互为验证范围，未重新执行后端全量测试，也未调用付费模型或发送真实邮件。

## 2026-10-15：完整功能回归与恢复修复

本轮使用合成账号、独立数据库以及运行中服务的 test / admin 会话。功能范围与复跑命令见 [功能验收](FUNCTIONAL_TESTS.md)。

| 检查 | 实际结果 |
| --- | --- |
| 后端完整回归 | 593 项：452 通过、141 按环境跳过，0 失败；跳过项随后分环境补跑 |
| MySQL 专项 | 16 个测试类共 174 项，按修复后的最近一次结果计：173 通过、1 个可选查询性能基准未运行，0 失败 |
| 新增权限与视频专项 | Linux 中 6 项通过；权限矩阵覆盖 35 项权限、50 个入口，包含逐项授权/拒绝、三种角色、匿名与无效凭证，共 3,750 次接口鉴权断言 |
| 前端 | 11 项单元测试、30 组交互回归（715 项断言）、类型检查和生产构建通过；页面修正后完整交互回归再次通过 |
| 真实浏览器 | test / admin 各 33 个桌面页面，共 66 次检查通过，没有脚本错误、API 错误或横向溢出；另验证笔记自动保存、刷新恢复、代码预览及词汇开始学习 |
| 运行中接口 | 19 项登录与权限检查、13 项计划/笔记/扫描 PDF 流程检查通过；临时笔记、文档、任务和登录会话已清理 |
| 恢复与训练 | Linux 备份 28 项、真实 MySQL 数据库与文件恢复 1 项、训练 5 项通过；总览与图像处理两个可选基准也执行通过 |
| 部署与负载 | 启动脚本、代理安全头和日志检查通过；8 路并发 48 次请求全部成功，本机样本 P50 / P95 为 85 / 142 ms；八个服务健康 |

发现并修复：

- 群组、设置、审核页重复声明主内容区，文档和公共模型页缺少主内容标识；已统一，真实浏览器复验通过。
- 数据库升级与恢复测试还写着旧版本的表数、约束数和迁移步数。升级测试现覆盖 V39 的 66 张业务表、95 个外键和任务查询索引；恢复测试比较实际源库和恢复库。首轮 MySQL 的 6 个失败已在修正后重跑通过，没有简单取消约束检查。
- 备份测试整套执行时没有应用独立运行时的文件权限设置。已修复测试初始化，Windows 明确跳过 POSIX 专项，Linux 全部执行。
- 实际恢复时，MySQL 会把部分 `COLLATE utf8mb4_*` 字段重新导出为带显式 `CHARACTER SET utf8mb4` 的等价定义，导致旧校验误报。现在仅统一这一类字段声明；原备份的字节哈希仍须通过，字段类型、长度、排序规则、默认值、数据及其他 SQL 改动仍会被检测。
- 恢复导入改为按表提交，避免每条 INSERT 单独落盘。未改变备份格式或原始 SQL；仍只允许恢复到新的独立数据库，失败不会覆盖现有数据。

自动检查补上此前漏跑的 MySQL 流程与备份安全用例，新增一条命令即可运行的隔离数据库检查入口。没有调用付费 AI、发送真实邮件、进行公网部署或大规模持续压测；本机延迟样本不代表公网表现。语音按钮与自动朗读逻辑有交互测试，未验证每种系统语音的实际音质。

## 2026-10-15：用户隔离与权限收回

- 修复群组权限收回后仍可通过消息详情或附件入口访问的问题；联系人或群组权限失效后，不能继续借相应关系发送新的站内信。测试保留原登录凭证，直接请求后端确认限制生效。
- 私人站内信和附件只对原参与者开放，平台管理员通过举报证据处理投诉；响应中不再返回收件人邮箱。新旧邀请搜索入口统一分页，并过滤隐藏、拉黑和到期账号。
- 成员修改接口不能直接拉人入群。旧邀请被接受时会复核邀请人的账号、平台权限、群内权限和黑名单；失败提示不会泄露邀请人的处罚原因。页面同步改为“发送邀请”，群管理员在成员同意入群后由群主设置。
- 账号、权限矩阵、联系人和群权限隔离 28 项专项检查通过；群组、举报、笔记与附件、AI 隔离等另一轮 73 项检查通过。最终 Docker 镜像构建时重新执行群组 21 项检查，包含新加的处罚信息保密检查，均通过。
- 独立 MySQL 上的 29 项检查全部执行通过：群组 20 项、权限矩阵 4 项、管理员并发变更 5 项。测试使用新建的随机数据库，完成后删除临时容器及数据；没有修改业务库中的账号权限。
- 群组与站内信前端 36 项交互断言、类型检查和生产构建通过。检查包含邀请确认、重复提交、只读成员限制、附件选择及切换账号后清空旧内容。
- 部署后八个服务健康；真实 test / admin 账号的 19 项检查通过，覆盖匿名访问拒绝、管理接口边界、站内信邮箱隐藏、通知与群组入口、退出后旧凭证失效。仅创建了检查所需的登录会话，结束时全部撤销，没有发送真实消息或调用外部模型。

## 2026-10-15：安全与资源优化

- Spring Boot 升级到 3.5.16、Springdoc 升级到 2.8.17 后，Docker 内完整后端回归共 557 项：433 项执行通过，124 项按环境条件跳过，失败和错误为 0。新增检查覆盖分块超大请求、上传限流、解压大小、任务取消检查和会话写入次数。实际 MySQL 已执行 V39，两个任务查询索引存在。
- Vue 更新到 3.5.44，前端完整依赖审计由 9 项高危告警降至 0 项已知告警；其中原告警包含传递依赖重复影响，不代表 9 个独立可利用漏洞。11 项前端单元测试、30 组交互回归、类型检查和生产构建通过。审计时间为本次更新当日，后端完整依赖审计不在这一数字中。
- 本地和公网 Nginx 配置各检查五类响应，覆盖页面、缺失资源和上游失败；安全头、API 禁止缓存和日志隐藏私有 URL 均符合预期。真实浏览器切换笔记、词汇、知识库、实验台和工作台，没有非预期脚本错误或安全策略拦截；主动插入的测试内联脚本被阻止。
- 真实 test / admin 账号的 20 项安全检查通过：包括超过 1 MB 的笔记图片上传、附件内容一致、管理员不能读他人私有附件、普通账号不能查看管理用户列表、已声明与分块超大 JSON 拦截、退出后旧令牌被拒绝。另完成 13 项学习流程检查，扫描 PDF 的本地 OCR、原页预览、校对版本冲突和任务归属均正常。合成资料、附件与测试会话已清理，未调用外部付费模型。
- 当前八个容器均健康；API / worker 的 2 GiB 内存、512 个进程/线程上限、移除 capabilities 和禁止提权设置已经生效。八路并发 48 次请求全部成功；启动后的忙时样本 P50 / P95 为 161 / 235 ms，空闲后为 95 / 137 ms，后者最慢 160 ms。这些是不同负载下的本机样本，不据此宣称整体延迟下降。数据库优化由“新鲜会话不写 UPDATE、仍逐次校验撤销”和“任务进度合并写入”的回归测试直接确认。

操作范围见 [安全说明](SECURITY.md)，测量方法见 [性能说明](PERFORMANCE.md)。

## 2026-10-15：时间计划、阅读摘录与恢复演练

- Docker 内完整后端回归共 546 项：422 项执行通过，124 项按环境条件跳过，失败和错误为 0。之后补充旧任务租约、Markdown / HTML 选区检查，最后 11 项相关测试全部通过。跳过的数据库专项测试不计为通过；实际 MySQL 已完成 V38 升级并验证新增接口。
- 前端完整交互回归、类型检查和生产构建通过。新增学习流程组最后覆盖 8 项检查，包括计划版本、取消任务、旧刷新结果隔离、重复删除和带来源的选区保存。
- 真实 test 账号验证时间计划保存与冲突、双语概念检索、摘录重试去重、扫描 PDF 后台识别、原页图片预览和校对版本冲突。管理员也不能读取该账号的私有原文、片段和任务。测试资料与验证会话随后清理。
- 真实桌面浏览器检查学习安排、阅读选区工具、任务中心和校对草稿提醒；这些页面没有横向溢出。重启后端时通知连接出现预期的暂时中断，恢复后可以继续使用。
- 八路并发、48 次接口请求全部成功，IPv4 回环地址下 P50 65 ms、P95 91 ms、最慢 130 ms。验证未登录访问、无效参数、未知任务和超限上传被拒绝；后端重启后原登录会话仍有效。数字仅代表本机当前数据量。
- 备份安全、加密认证、保留策略与演练隔离检查通过；Linux 下在私有文件权限设置中运行 19 项旧备份测试通过。修复自动备份凭据文件末尾换行导致读取失败的问题；快照记录不可变镜像 ID，演练在不开放端口的独立网络中检查业务。
- 实际加密归档恢复到独立实例后，读取 2 篇合成测试笔记，核对 1 个附件的 SHA-256，并完成注册、登录和新建笔记读写。演练容器、临时卷与明文恢复配置已清理，原部署保留；加密归档交由现有保留策略管理。

新功能操作说明见 [学习安排与阅读摘录](STUDY_WORKFLOWS.md)，恢复演练见 [完整部署备份](COMPOSE_BACKUP.md)。OCR 在本地运行，未发送收费模型请求；自然语句搜索是有范围的概念扩展，不是通用向量语义检索。

## 2026-10-15：学习统计与学习流程

- 后端回归及最后两项补充测试累计 537 项：412 项执行通过，125 项按测试环境条件跳过，失败和错误为 0。未把跳过的 MySQL 测试计为通过；运行中的 MySQL 已实际完成 V35–V37 升级，并验证新接口。
- 前端完整交互回归、类型检查和生产构建通过；新增流式生成检查覆盖停止、未完成提示、手动应用、账户切换清空结果。只使用模拟模型响应，没有发送收费模型请求。
- 使用真实 `test` 账号检查每日与累计统计：新增笔记计数、离线答题去重、管理员读取指定用户统计、普通用户越权拒绝。单词数量不等同于掌握程度，时长从新功能启用后估算。
- 真实浏览器验证 PDF 上传经过当前扫描流程后可阅读、按正文搜索并保留页码。文档、原文件和片段的跨用户隔离及重复上传回滚另有自动测试。
- 浏览器断网刷新 `/offline` 后仍可解锁资料、修改笔记和练习拼写；联网后识别笔记冲突、保留线上原文并另存离线副本。IndexedDB 只存盐、随机 IV 与密文；Service Worker 不缓存 API 响应。
- 复习间隔使用 FSRS 默认参数；验证高目标记忆率通常缩短间隔、保存状态可恢复、错误状态被拒绝，不宣称已经按个人历史训练模型权重。
- 发现本机两个启动密码重置开关持续开启，导致管理员和体验账号在每次重启后退出登录；已关闭，并统一非 Docker 启动的体验账号重置默认值为关闭。已验证更新前签发的两个账号会话在重建服务后仍可使用；显式密码重置仍会撤销旧会话。
- 已运行且健康的全部服务再次执行 `deploy.ps1 -SkipBuild`，本机单次检查约 2.2 秒；这是重复启动检查，不能当作冷启动速度。健康检查未跳过。

重跑：`./mvnw.cmd test`、`npm.cmd run test:ui --prefix cle`、`npm.cmd run build --prefix cle`、`./scripts/test-start-project.ps1`。学习功能入口和统计口径见 [学习说明](LEARNING_STATISTICS.md)。


## 2026-10-10：点击、草稿与异常交互

- 复现管理员折线图点击后的焦点底色残留，修复鼠标离开后不清除的问题。底色移到曲线后方，避免遮住折线；保留键盘查看，并补充左右方向键、Home、End、Esc、空数据和单日数据处理。
- 修复筛选或分页失败后误显示旧数据、笔记重复删除、写信重新打开丢稿、聊天离开丢稿、群组刷新跳到其他群组，以及无效附件选择清空已有附件。登录、注册较晚完成时，不再跳回用户已经离开的页面。
- 请求增加等待上限，包含响应正文读取；主动取消仍生效，取消后到达的响应不能影响当前会话。超时提示提醒先确认提交结果，不自动重发。测试工具在退出时清理模拟请求的等待计时器。
- 前端全部 23 组交互回归通过，共 597 项断言；新增异常交互组含 54 项。11 项单元检查通过，类型检查与 Docker 内生产构建通过。本轮只修改前端与说明，后端权限验证记录见下节。
- 1440 × 1000 真实桌面浏览器复查 35 个页面及设置入口，未出现 JavaScript 错误、意外失败接口或横向溢出。另完成图表 17 项和写信、搜索、导航 9 项操作检查；其中两次 503 是主动注入的故障，用于验证错误提示与重试。
- 更新后 8 个服务均健康。本轮没有删除用户资料、发送真实消息、邮件或收费 AI 请求。自动交互检查使用模拟资料；外部 AI 和邮件仍需配置后另行验证。

复查命令与操作见 [交互检查](INTERACTION_CHECKS.md)。

## 2026-10-10：账号权限与桌面页面回归

- 使用现有 `test` 体验账号轮换查看者、研究员、管理员、全部禁止、委派审核和部分授权，真实部署完成 356 项接口检查，失败为 0；包含提权尝试、权限到期、停用和退出后的登录凭证重用。检查后恢复原有研究员角色、单独授权和有效期。
- 新增权限矩阵覆盖 35 项权限的逐个允许与禁止，以及 44 个受保护接口。完整后端构建执行 377 项测试通过，109 项按环境条件跳过，失败和错误为 0；独立 MySQL 8.4 又执行权限矩阵 4 项、账号安全 5 项和举报处罚 19 项，全部通过。
- 前端 22 组交互回归、5 项单元测试、类型检查和生产构建通过。新增界面检查覆盖部分实验授权、上传禁止、公开模型读取、委派账单访问和后台权限刷新。训练服务的 5 项检查也通过。
- 修复缺少查询参数或文件参数时误报 500；本人登录资料同步临时权限限制，菜单按服务器返回的权限显示；模型页与实验页不再请求被禁止的运行信息，上传控件遵守上传权限，委派账单权限不显示最高管理员操作。
- 在真实部署创建了临时私有笔记和 AI 记忆，验证管理员不能跨账号读取、修改或删除，以及旧版本修改会被拒绝。临时资料已清理。
- 1440 × 1000 桌面浏览器分别以管理员、查看者和部分授权账号复查 35 个页面及设置入口，共 105 次访问；没有 JavaScript 错误、意外失败请求或横向溢出。无权页面跳转到个人设置，公开模型页面仍正常读取介绍。
- 更新后 8 个服务均健康。未发送收费 AI 请求或真实邮件；这些外部服务仍需用户配置后另行验收。

复查步骤与命令见 [权限检查](PERMISSION_TESTS.md)。

## 2026-10-10：举报审核与处罚

- 完整后端回归：482 项，373 项执行通过，109 项按环境条件跳过，失败和错误为 0。最后补充同一消息重复举报和数字编号规范化防护，又通过 19 项举报回归与后端构建。
- 独立 MySQL 8.4：19 项举报、禁言、限时权限、封禁、申诉和跨用户访问检查通过；8 项新装、升级、并发迁移和旧举报证据保留检查通过。
- 前端 21 组交互回归与 5 项单元检查通过；举报组覆盖 11 项断言，包括原因必填、限制功能选择、时间换算、过期响应隔离和申诉。类型检查与生产构建通过。
- 自动处罚使用本地的有限规则，不发送真实 AI 请求。处罚不会改写原有角色或账号状态；到期也不会重新启用已停用或注销的账号。
- 更新后 8 个服务均健康，`check` 登录、审核队列、申诉筛选和本人处罚接口均返回 200。1440 × 1000 桌面浏览器检查了必填原因、功能多选、时长单位、本人记录和封禁后的申诉入口；没有横向溢出或 JavaScript 错误。表单演示使用浏览器中的临时数据，没有处罚真实用户。

后续管理员功能见 [管理员使用与权限说明](ADMINISTRATION.md)，搜索、收藏和常用搜索见 [学习中心使用指南](LEARNING_WORKSPACE.md)。本文下面保留的是 2026-10-02 当时的检查记录，其中部署限制不代表当前版本；新的搜索性能记录在 [性能说明](PERFORMANCE.md)。

## 2026-10-10：搜索与整理

- 后端构建：339 项测试，284 项执行通过，55 项按条件跳过，失败和错误为 0。
- 真实 MySQL 8.4：12 项搜索、隐私、收藏去重与数量限制检查通过；7 项新装、旧版升级和并发迁移检查通过。最终的来源校验与收藏清理又复验了 2 项。
- 前端：17 组交互回归通过，最新搜索组包含 14 项断言；类型检查和生产构建通过。
- 本地登录、搜索、收藏、常用搜索保存与更新通过。1440 × 1000 桌面浏览器检查了键盘选择、原文打开、保存后收起、亮暗主题；页面没有横向溢出，最终页面控制台没有错误或警告。
- 更新后 8 个 Compose 服务均健康。临时验收笔记、收藏、搜索和登录会话已清理。

## 2026-10-10：群会话和学习助手

- 完整后端构建：350 项测试，287 项执行通过，63 项按环境条件跳过，失败和错误为 0。
- 独立 MySQL 8.4：群组通信 8 项、数据库迁移 7 项、个人 AI 用量持久化 6 项、词汇与导出 24 项通过。覆盖公告版本冲突、跨群消息限制、退出后的权限撤回、个人会话偏好和旧数据升级。
- 前端 18 组交互回归通过；最后的公告并发编辑修正又通过群组 35 项断言。单词辅助 16 项、笔记与个人配置 57 项及请求保护 3 项通过。类型检查和生产构建通过。
- 在运行中的服务上验证了公告、置顶消息、会话偏好、已读、消息搜索和成员搜索。1440 × 1000 桌面检查了群组、单词助手与笔记入口；长群名没有横向溢出，最终浏览器控制台错误与警告为 0。
- AI 发送确认、权限收回和结果保存使用模拟响应验证，没有发送真实付费模型请求。当前验收账号没有启用个人模型，远程动作需先配置模型才能使用。
- 8 个服务均健康，首页和登录返回 200。仅清理本次临时群组与验收会话，保留原有数据。

## Implementation validation, 2026-10-02

Validation base: repository commit0195f9b. No deployment was performed.

## Actual database and service checks

- MySQL Community Server8.4.6: real persisted InnoDB data directory, loopback TCP, utf8mb4; never relabelled H2.
- Flyway fresh install and populated V6→V7 upgrade: both pass on MySQL8.4.6. All22 application tables,27foreign keys and20Hibernate entities checked; old migration checksums unchanged; Unicode/decimals/domain rows preserved; rerun is idempotent.
- The original application failed real Hibernate validation on CHAR/VARCHAR mismatches. Explicit mappings fix12fields in7entities; full runtime now keeps schema validation enabled.
- MySQL billing concurrency:4tests pass, covering12parallel first-wallet requests,8distinct recharge settlements to one wallet,12admin adjustments and12usage charges across3wallets/shared provider budget; unauthorized/replayed actions rejected.
- Redis7.4.7: actual Redis with AOF persistence. Worker queue integration and restart checks are recorded below after end-to-end completion.
- ClamAV1.4.3: official complete databases, successful clean INSTREAM and harmless EICAR detection.
- MinIO final official patched release: source build and Go module verification pass. Runtime is blocked by sandbox netlink denial, unchanged after one approved escalated attempt. S3 integration and Docker Compose execution are not claimed as passed.

## Actual MySQL HTTP benchmark

Same500-task/500-file synthetic fixture, two4KiBJSON results/task, same local MySQL schema and account. Original packaged jar retained before edits. Python stdlib persistent HTTP/1.1 connection,5warmups then30timed requests,0.65s spacing, concurrency1. Authentication, BCrypt login and Redis rate limiting remained enabled; every measured response HTTP200, no degraded limiter. API/SQL aggregation outputs agree (500total,167completed,167failed,0.15average confidence lift). Filesystem storage and demo models are configured; dashboard does not access model/storage services.

| Metric | Before | After (initial verified build) | Reduction |
|---|---:|---:|---:|
| Median |104.391ms|50.541ms|51.6%|
| p95 |257.405ms|78.746ms|69.4%|

Raw samples live under ignored`.runtime/evidence/http-before.json` and`http-after.json`. These are single-host observations with30samples, not production capacity guarantees. Final-code repeat: median45.415ms, p9562.234ms (same30samples/5warmups), also allHTTP200. Initial and repeat results are both retained rather than selectively substituted.

Focused in-process measurements in[PERFORMANCE.md](PERFORMANCE.md) separately establish work reduction:505→3SQL and500→5task entities; pixel-identical1080p image transform60.99→39.02ms median. They are H2/component measurements and are not interchangeable with MySQL HTTP numbers.

## Current caveats

Boot3.3.5's managed Flyway10.10 emits an upstream tested-version warning for MySQL8.4 despite the passing real-engine contracts above. This change does not silently upgrade the entire dependency framework; supported-version/security upgrades need their own compatibility run. MinIO community upstream is archived; the pinned final source does not imply ongoing vendor support.

The queue still lacks outbox/acknowledgment recovery and financial usage reservations; the changes do not claim exactly-once jobs or production payment support. Full-list API contracts still return all rows. Dashboard SQL aggregation remains O(N) in qualifying rows; large-history rollups/pagination need a separate compatible design.


### Four concurrent clients

30total measured requests per build,2warmups per worker,4independent persistent HTTP connections; paced between request waves to preserve the120/minute limit. Both application jars were resident against the same MySQL fixture, one exercised at a time. Original median232.138ms/p95365.486ms; final median71.615ms/p9576.828ms. All60measured responses200, no degraded rate limiter. This is a paced concurrent latency check, not maximum throughput/load capacity.

### Warm API startup: no meaningful timing gain established

Three serial alternating original/final process launches with MySQL/Redis already running and an already migrated benchmark database. Timer ends after health and an actual authenticated BCrypt login succeed. Bootstrap/migration execution is disabled identically for this particular measurement; the original needs ddl-auto=none because of its confirmed mapping bug, while the final keeps validate. No lazy initialization, weakened BCrypt, disabled authorization or fake health was used to claim faster readiness.

- Original median9.797s, range9.409–10.355s
- Final median9.830s, range9.580–11.724s

The difference is noise-level and **does not demonstrate faster API process startup**. The repeat-start workflow now reuses built images/jars, builds the API image once for API+worker, omits duplicate worker migrations/seeds and uses explicit readiness polling. These reduce avoidable operational work; Docker warm-start time has not been measured here. First-install downloads, virus-library loading and full-stack cold boot are separate from this API warm-restart benchmark.

## Final build checks

- Full Maven verify:41tests,37passed,0failures/errors,4intentional skips (2opt-in timing methods and2external-MySQL tests). The2MySQL tests plus4accounting tests also ran separately on actualMySQL:6/6pass,0skip.
- Frontend TypeScript checking and Vite production build:passed.
- MavenWrapper fresh download with pinnedSHA-256:passed; reportsMaven3.9.9/JDK17.
- YAML parsing, Python compilation and shell syntax checks:passed. Docker image/Compose execution:not run, since this environment has noDocker.

## End-to-end and persisted restart verification

The final packaged application was tested with actual MySQL, Redis AOF, mandatory ClamAV, filesystem storage (explicit partial profile), separate API/worker processes and production frontend preview. Tests passed:

1. Unauthorized dashboard access returns401; authorized login works.
2. PNG upload is scanned by the real daemon and returnsCLEAN; stored/downloaded SHA-256 matches.
3. With worker stopped, inference POST returnsPENDING and the Redis list length is1.
4. Starting the independent worker changes the task toCOMPLETED/DEMO and empties the queue.
5. A Unicode/emoji note round-trips through MySQL.
6. All processes, including MySQL and Redis, were stopped; the new checked-in local launcher restarted them from persisted directories.
7. Users2/topics6/knowledge entries18/notes1/files2/tasks1 counts remain unchanged. The exact note body, task result and original media SHA remain intact; Redis AOF restores a dedicated persistence-test key.
8. API and worker readiness both returnUP, and the rebuilt frontend serves and proxies the API successfully.

There is no claim that browser rendering, MinIO/S3 or Docker runtime passed. The last two are blocked/unavailable as described above. Real external model inference remains intentionally untested with no keys configured.

Artifacts/scripts: `scripts/local/verify-stack.py`, `scripts/local/start-stack.sh`, `scripts/performance/startup_benchmark.py`, the HTTP benchmark and guarded SQL fixture. Evidence excludes authentication tokens, passwords and database files.


## Follow-on note-reference optimization (base 1e9eaf9)

- Production scope: bounded scalar reference/target queries for note detail and shared-note reads. No schema, API or permission changes.
- Final Maven verify in an isolated source copy: 49 tests, 43 passed, zero failures/errors, six intentional skips. The live runtime JAR was not overwritten.
- Actual MySQL 8.4.6: all eight `NoteReferenceBatchQueryTest` methods passed, no skips. Coverage includes exact baseline DTO equality, missing/deleted targets, labels/order, 501-reference boundary, fresh state, note ownership, same-transaction add/remove responses, share-token revocation/expiry, and case-insensitive UUID matching.
- 500-reference service/JDBC benchmark: 667 → 4 SQL statements, 1,333 → 0 entity loads; median 104.116 → 20.976 ms, p95 191.450 → 33.839 ms. Five warmups and 15 alternating samples; full results must match. These are not HTTP or production-throughput numbers.
- Independent static review found no actionable defect. The MySQL CI job now includes the new correctness suite; timing remains opt-in.
- No deployment, runtime restart, new credentials or destructive application-data migration was performed. Earlier component/runtime caveats remain applicable.

Method and reproduction commands: [PERFORMANCE.md](PERFORMANCE.md#follow-on-note-reference-batch-actual-mysql-2026-10-02).

## Private AI / personal settings / vocabulary feature verification (2026-10-02)

New application code was validated separately from the retained historical runtime; it was not deployed over existing user data.

- Independent published security batches: ownership/knowledge `87f31db`, request/proxy/logging `8b0f619`, and upload active-content inspection `38ec65c`. Their frontend/backend/MySQL CI jobs passed.
- Full isolated source build: 211 tests, zero failures/errors, 21 expected skips (external-MySQL cases and optional timing checks). All 190 non-skipped cases executed successfully; actual database validation is listed separately below.
- Real disposable MySQL 8.4.6: 38 cases, zero failures/errors, one opt-in timing skip. Coverage includes vocabulary 15, administrator concurrency 5, migration/upgrade preservation 3, accounting concurrency 4, reference correctness 9 executed cases, and the actual 71-request HTTP workflow. Fresh V1–V12 and populated V6/V7 upgrades validate all 31 tables, 40 foreign keys and 29 Hibernate entities; prior data and migration checksums are preserved.
- The real-engine run found a repeatable-read race that the H2 vocabulary test had not exposed. It was repaired using explicit transaction isolation and locked question/progress reads; the same concurrent replay/next tests then passed against MySQL.
- Personal AI adapters and image recognition use synthetic keys and mocked upstreams. The actual random-port servlet HTTP test made 71 real requests, with exactly three synthetic provider completions and an explicitly mocked antivirus boundary. Verification covers native Anthropic/Gemini versus OpenAI-compatible protocols, private ownership, preview/execute binding, replay, config changes, rate/concurrency bounds, secret redaction, nullable/failed-call usage, SSRF/DNS pinning and output limits. No real provider account, model availability or billing was validated.
- Frontend synthetic DOM suite: 38 assertions plus 3 request-guard unit tests, TypeScript and production build passed. Tests include two-account cross-tab changes, stale responses, no-send cancellation, one-shot consent, private settings and all-time versus recent usage. Full visual browser inspection remains unavailable because the cloud browser blocks local application URLs. No forwarding bypass was attempted.
- Nginx syntax and an actual synthetic private-URL logging regression are now included in CI using the same official image family as the frontend. Local Docker/Compose execution remains unverified.
- A concurrent validation attempt encountered environment stalls and one transient MySQL communication failure. No failed/interrupted run was counted as a pass; the final serial, heap-bounded MySQL run above completed successfully.

The freshly built production JAR was also launched against a separate disposable MySQL 8.4.6 schema for 51 real HTTP requests. It applied 12 migrations, verified owner settings/encrypted storage, notes, vocabulary four-answer mastery/next-day review, session logout replay, and accurate SKIPPED scan handling. Remote execution stayed disabled by the shipped default; no provider call was made. Rate limiting stayed enabled, using its bounded fallback when Redis was absent. No antivirus mock was installed in that production-JAR run: disabled antivirus remained SKIPPED and recognition was denied. The JAR stopped cleanly and its owned temporary database was dropped. JAR SHA-256: `2b96de75b46f27fb2a4f823091dad09b1a31cc7dc017ac3beab0128f1b623504`.

Reproduction: `PersonalPlatformHttpIntegrationTest` (actual HTTP, explicit synthetic provider/AV boundary; H2 or opt-in MySQL), `scripts/tests/private-platform-http-smoke.py` (fresh production JAR, default external-execution denial, guarded disposable MySQL). Production rollout, TLS/MinIO validation and real personal-provider calls remain outside the completed local/mocked checks.

The final long-note regression verifies that local assistance rejects oversized content without silently truncating it: the actual HTTP test saves a body over 24,000 characters, receives an explicit error, then reads back the identical original body. DOM tests verify no replacement result can be applied and that pending explicit saves block navigation; an extra response-generation check discards obsolete saves. No notebook autosave or real-time multi-editor merge is claimed.
