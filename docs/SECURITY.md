# Security boundaries and verification

## 2026-10-15 更新

- API 写入的 JSON 请求在解析前检查大小，包括分块发送的请求；笔记、词书和学习卡导入保留各自的上限。图片和文档使用独立的上传处理，不会再被 JSON 过滤器提前读取。
- 笔记图片、文档、后台导入、站内信附件和词汇备份恢复共用上传频率限制；AI 流式回答和资料问答计入识别调用限制。不能通过更换入口获得新的额度。
- 页面、静态资源和错误响应都带有浏览器安全策略。脚本只允许本站资源，禁止内联脚本和外部页面嵌入；链接不发送来源地址，API 响应禁止缓存。主题和动效需要的内联样式仍然允许。
- Word 文档逐块解压，单个内部文件最多 20 MiB、全部内部文件累计最多 50 MiB；不把无关图片全部读进内存。PDF 在收集文字时检查 50 万字上限，扫描页启用图像降采样。
- API 和后台 worker 以普通用户运行，移除 Linux capabilities、禁止提权，每个容器默认限制为 2 GiB 内存和 512 个进程/线程。大资料处理需要更多内存时，可在 `.env` 调整 `APP_MEMORY_LIMIT`，更新后重建容器。
- 每次请求仍查询真实会话状态；只有最后活跃时间超过五分钟才写入更新。权限或会话撤销不依赖五分钟缓存。后台进度最多每秒写一次，租约清理每 30 秒检查一次。

依赖基线更新为 Spring Boot 3.5.16、Springdoc 2.8.17 和 Vue 3.5.44，保留 Java 17。前端 CI 执行 `npm audit --audit-level=high`；结果反映查询时的已知告警，不代表全部代码和依赖没有风险。后端按实际使用的组件和兼容性测试升级。版本依据见 [Spring Boot 发布说明](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/) 和 [Vue 上游公告](https://github.com/vuejs/core/security/advisories/GHSA-g2v6-rqmx-r4w6)。

浏览器头在各个 Nginx location 显式包含，避免缓存配置覆盖继承规则。CI 同时检查普通页面、缺失资源和上游失败的响应；规则依据见 [Nginx 文档](https://nginx.org/en/docs/http/ngx_http_headers_module.html) 和 [CSP 文档](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Content-Security-Policy)。

The platform has account ownership checks, not a separately modeled multi-tenant database or a guarantee against every attack. Production TLS, proxy topology, backups, host hardening, upstream-provider security, and complete deployment penetration testing remain operator responsibilities.

## Notebook and shared knowledge

- ADMIN permissions are resolved from the complete current catalog, rather than a stored snapshot. Other accounts resolve role defaults plus individual grants/denials. Both request security and business checks use the same result. New module permissions cover vocabulary, contacts, groups, messaging, personal AI and training.
- Account expiry blocks password login and existing JWT sessions. Administrative security changes revoke target sessions; role changes revoke affected role sessions. Only ADMIN may edit individual access, assign roles, disable users, reset credentials or revoke other users' sessions. Delegated user writers may edit display names only.
- Management statistics and audit logs have separate read permissions. Directory search is paginated; detail responses contain metadata and counts, never notebook bodies, private memories, passwords or model keys. Successful administrative changes record their operator, target and permission changes without secret values. Existing notebook ownership boundaries below remain in place.

- Notebook reads, writes, experiment selections, insertion previews and AI source selection are authenticated and owner-scoped on the backend, including when a caller changes request IDs. A platform administrator does not receive an ownership bypass for personal notebook sources.
- References resolve only files/tasks owned by the note owner or legitimately readable knowledge cards. Previously stored invalid cross-user references no longer resolve private metadata.
- Sharing a note grants the displayed note and legitimate embedded reference metadata through its revocable, expiring token. It does not grant access to underlying file content or experiment reports. Text deliberately copied into the note is part of what is shared.
- Public knowledge cards remain readable. Only administrators can modify global cards; private card and topic ownership must both permit reading.
- Workspace roster disclosure requires MEMBERS_READ; content-only viewers see their own membership. Delegated member writers cannot grant permissions they do not hold or modify owner privileges.

## Requests, logging, and payment sandbox

- Anonymous/source rate limits use the socket peer by default. Client-supplied X-Forwarded-For is ignored unless the peer is listed explicitly in app.rate-limit.trusted-proxies as a literal IP address. Trusted chains are evaluated right-to-left; configure only proxies you operate. The supplied Nginx configuration overwrites client-supplied forwarding headers.
- Redis counter increment and expiry are atomic. A capped local counter provides a per-process fallback during a Redis outage, rather than allowing unlimited requests. Distributed deployments still need healthy shared rate storage; per-process fallback is not a distributed quota guarantee.
- Authentication/account routes have a tighter source budget. Personal AI additionally has per-user request/concurrency bounds.
- Application error logs use route templates and error codes, not raw URLs or exception payloads. Nginx access logs omit URLs because share/payment paths contain tokens; error logging is restricted. Do not enable verbose HTTP/body/header logging in production.
- Demo recharge is explicitly blocked outside model demo mode; it does not process real payments. Private AI provider usage is billed by the user's provider and does not debit the demo wallet.

## Tests and limits

Regression coverage includes forged cross-user notebook/source IDs, old invalid references, valid sharing/revocation, public-card readability, restricted global mutations, workspace permission boundaries, spoofed forwarding headers, bounded Redis-outage behavior, sandbox settlement rejection, and secret-bearing error-log redaction. Bounded query-count tests continue to cover large reference lists. SQL uses bound query parameters; rendered Markdown must continue to use the frontend sanitizer. These checks reduce known risks and are not proof of complete attack resistance.

## Account state and browser identity

Every login has a persisted, expiring server session. Logout revokes that session; password resets and administrative status/role changes revoke affected sessions. Security changes require current-password confirmation or ADMIN authority as appropriate. All administrative writes serialize through an ADMIN-role guard before locking users, then refresh the acting administrator and perform a locking last-admin check; concurrent mutual disabling/demotion cannot remove every active administrator.

Browser requests are bound to the account generation loaded in that tab. Another tab's login change clears private UI instead of silently borrowing the new account token. Late responses and old-account 401s cannot overwrite or sign out a newer account. Test coverage includes those races. Browser-local appearance preferences are scoped by account; they are not server-synchronized preferences.

The JSON envelopes for personal AI, account/auth and notes are bounded before JSON deserialization. Notebook bodies have a 200,000-character maximum. Vocabulary imports have their separate 2 MiB envelope and validated per-field/storage limits. Overall disk quotas, distributed admission control and production operational monitoring are still deployment responsibilities.

The Nginx privacy access log is defined at the server level to override the official image's inherited combined log. Adding a second access log at the same HTTP level would retain both logs ([official logging semantics](https://nginx.org/en/docs/http/ngx_http_log_module.html#access_log)). CI starts a temporary official Nginx container and verifies that a failed upstream request containing a synthetic private path/query never appears in logs.

## 群组与站内信访问范围

站内信是基础模块，由 `message:read` 和管理员配置的角色、单独权限控制。私人收件人由服务端限制为平台管理员或符合群内读写权限的同组成员。群内容、回复和附件按当前成员资格及群角色权限校验，群管理员无法获得平台权限或修改其他群管理员。群消息不进入私人已发送列表，消息附件禁止通过普通文件接口绕过成员检查。群发言和成员变更串行锁定同一个群组，避免成员变更与发言提交交错。完整操作及历史消息可见性见 [群组与站内信](GROUPS.md)。

## 搜索与收藏

搜索、原文读取与收藏列表均重新检查来源的当前访问权限。个人 AI 识别结果要求 `personal-ai:use`；群组来源同时要求 `group:use`、`message:read` 和成员的 `CONTENT_READ`。禁用学习中心也会关闭它的 AI 问答接口。

收藏不保存正文或标题副本；仅保存本人选择的资料标识。常用搜索保存本人的筛选条件，不能据此访问他人的资料。并发保存以账号行锁和数据库唯一约束去重，按账号限制数量；清理只处理当前账号的收藏。
