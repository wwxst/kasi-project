# 生产配置最小加固设计

日期：2026-09-03

## 目标

在不改变业务 API、数据库 schema、验证码发送实现和前端行为的前提下，消除当前阻碍快速上线的生产配置风险，使敏感配置只能由部署环境提供，并为生产启动提供明确入口。

## 当前事实

- `application.properties` 中数据库和 JWT 已经要求环境变量注入。
- `PROVIDER_CREDENTIAL_MASTER_KEY` 仍有仓库内置默认值。
- Redis 仍默认连接 `localhost:6379` 且允许空密码。
- 后端包日志默认使用 `DEBUG`。
- `application-local.properties` 已被 Git 跟踪，包含本地数据库密码和固定 JWT 密钥。
- 短信和邮件验证码生产发送实现已经完成，本设计不修改该模块。

## 实施边界

本次只修改生产配置、开发配置示例、Git 忽略规则和后端运行文档。

明确不修改：

- 验证码发送代码及其配置契约；
- 业务 Controller、Service、Mapper、DTO、VO；
- 数据库初始化 SQL；
- 管理端和用户端前端；
- 账单、钱包、提现、自动对账和转化分析。

## 方案

1. 删除提供商主密钥的默认值，使 `PROVIDER_CREDENTIAL_MASTER_KEY` 成为必填环境变量。
2. 新增 `application-prod.properties`：生产 Redis 主机、端口和密码均从环境变量读取且不提供默认值；后端业务包日志级别设置为 `INFO`。
3. 从 Git 索引移除 `application-local.properties`，但保留开发者本机文件；将其加入 Git 忽略规则。
4. 新增 `application-local.example.properties`，只提供占位符和安全说明，不包含可用凭据。
5. 更新 README，列出生产必需变量、`prod` profile 启动命令、初始化和验证顺序。

不新增 Java 启动校验组件。Spring 的未解析必填占位符和现有配置绑定负责快速失败，避免为了本次配置调整引入额外运行时代码。

## 验证

- 静态检查仓库不再包含原默认主密钥、`root/123456` 和固定本地 JWT 密钥。
- 缺少必填生产变量时，`prod` 启动必须失败。
- 提供完整生产变量并使用可达依赖时，应用应正常启动并通过健康检查；依赖不可达时明确记录为环境阻塞，不伪造通过。
- 使用 Java 25 运行分类 Gate、静态分析和完整 `mvn verify`。
- 运行 `git diff --check`，并复核只包含本设计允许的文件。
- MySQL Contract 和 GoodShort Required Smoke 只有在真实环境变量齐全时才能记为 PASS，否则按项目规则记录 SKIP 或环境阻塞。

## 部署与回滚

部署前在服务器或 Secret 管理系统中配置数据库、JWT、提供商主密钥、Redis 及现有验证码服务变量，然后使用 `prod` profile 启动。

回滚应用版本不等于回滚密钥。已经进入 Git 历史或曾被使用的数据库密码、JWT 密钥和提供商主密钥应在对应系统中轮换；仓库修改只阻止继续暴露，不能撤销历史泄露。
