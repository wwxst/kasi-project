# 推广链接生成实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 为推广用户实现可校验、可重试、可归因的短剧推广链接生成闭环。

**Architecture:** `promotion` 模块负责用户归属、短剧可推广条件、幂等记录和 API；`provider.spi` 增加独立推广链接适配器能力，GoodShort 只负责第三方字段映射和 HTTP 调用。通过 `promotion_link` 的 `request_key + PENDING/SUCCESS/FAILED` 状态保证超时重试不重复生成。

**Tech Stack:** Java 25、Spring Boot、MyBatis、Flyway、H2、JWT/Redis 会话、React 19、TDesign React、React Query、Vitest、MSW。

---

### Task 1: 固化数据库与领域状态

**Files:**
- Create: `src/main/resources/db/migration/V12__promotion_link.sql`
- Modify: `src/test/resources/test-schema.sql`
- Create: `src/main/java/com/kasi/backend/promotion/enums/PromotionLinkStatus.java`
- Create: `src/main/java/com/kasi/backend/promotion/entity/PromotionLink.java`
- Test: `src/test/java/com/kasi/backend/PromotionLinkMigrationTest.java`

- [ ] 写迁移失败测试：执行 V1-V12 后存在 `promotion_link`，`tracking_no` 和 `(user_id, request_key)` 唯一约束生效，状态默认 `PENDING`。
- [ ] 运行迁移测试确认先失败。
- [ ] 添加 V12 表、状态枚举、Entity 和 H2 测试表结构。
- [ ] 运行迁移测试确认通过。

### Task 2: 定义平台推广链接 SPI 和 GoodShort 映射

**Files:**
- Create: `src/main/java/com/kasi/backend/provider/spi/PromotionLinkProviderAdapter.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/PromotionLinkRequest.java`
- Create: `src/main/java/com/kasi/backend/provider/spi/PromotionLinkResult.java`
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Create: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortPromotionLinkAdapterTest.java`

- [ ] 写失败测试：GoodShort 请求使用外部 `bookId`、`codeMedia`、`shareUrlType`、`trackingNo`，响应正确映射 `code/shareUrl/customParams`。
- [ ] 运行适配器测试确认先失败。
- [ ] 实现 SPI、GoodShort 请求 DTO/响应 DTO、签名调用和错误转换；保持密钥不进入日志。
- [ ] 运行适配器测试确认通过，并覆盖远端拒绝、网络异常和 malformed response。

### Task 3: 实现链接 Mapper 与用户资格校验

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/mapper/PromotionLinkMapper.java`
- Create: `src/main/resources/mapper/PromotionLinkMapper.xml`
- Create: `src/main/java/com/kasi/backend/promotion/service/PromotionLinkService.java`
- Create: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkServiceImpl.java`
- Create: `src/main/java/com/kasi/backend/promotion/dto/CreatePromotionLinkDTO.java`
- Create: `src/main/java/com/kasi/backend/promotion/dto/PromotionLinkPageQueryDTO.java`
- Create: `src/test/java/com/kasi/backend/promotion/service/PromotionLinkServiceTest.java`

- [ ] 写失败测试：用户状态、媒体账号归属/启用、报备 `APPROVED`、短剧 `PUBLISHED`、平台连接启用、默认分佣规则和适配器能力任一不满足时返回对应业务错误，且不调用适配器。
- [ ] 运行服务测试确认先失败。
- [ ] 实现查询、`requestKey` 幂等查找、`trackingNo` 生成和事务内 `PENDING` 写入；调用既有 `ProviderCommissionRuleService.findDefaultRule`、`ProviderRuntimeConnectionService.resolve`、`ProviderDramaMapper`、`ProviderMediaFilingMapper`。
- [ ] 运行服务测试确认所有资格校验和用户隔离通过。

### Task 4: 完成生成、重试和查询 API

**Files:**
- Create: `src/main/java/com/kasi/backend/promotion/controller/UserPromotionLinkController.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/PromotionLinkVO.java`
- Modify: `src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Modify: `src/main/java/com/kasi/backend/security/config/SecurityConfig.java`
- Create: `src/test/java/com/kasi/backend/promotion/controller/UserPromotionLinkControllerTest.java`

- [ ] 写 MockMvc 失败测试：USER 可以 GET/POST，匿名和 ADMIN 被拒绝，成功响应不暴露内部 ID/接入账号/密钥。
- [ ] 写幂等测试：同一用户相同 `requestKey` 对 `PENDING/FAILED/SUCCESS` 分别复用、重试或直接返回。
- [ ] 实现 `@Valid` DTO、分页 GET、POST 生成/重试和统一错误码映射。
- [ ] 运行控制器测试确认 HTTP 权限、响应字段和异常码通过。

### Task 5: 增加推广用户端页面

**Files:**
- Modify/Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotion/api/promotionLinkTypes.ts`
- Modify/Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotion/api/promotionLinkApi.ts`
- Modify/Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionLinkPage.tsx`
- Modify/Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/promotion-link.css`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/app/AppRouter.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/layouts/AccountLayout.tsx`
- Test: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionLinkPage.test.tsx`

- [ ] 写页面失败测试：加载可推广短剧、读取本人媒体账号、提交 `requestKey`、成功显示链接、失败显示字段级错误。
- [ ] 实现 TDesign 页面、短剧选择、媒体账号选择、推广名称、生成按钮、链接列表和复制操作。
- [ ] 加入受保护路由和导航项，保持移动端布局。
- [ ] 运行页面测试、`pnpm typecheck`、`pnpm lint`、`pnpm build`。

### Task 6: 文档、回归与提交

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-08-17-multi-drama-provider-promotion-design.md`
- Modify: `docs/superpowers/plans/2026-08-17-multi-drama-provider-roadmap.md`

- [ ] 先运行后端推广链接聚焦测试和完整 `./mvnw.cmd clean test`（Java 25）。
- [ ] 运行两个前端仓库的完整测试、类型检查、lint、build 和 `git diff --check`。
- [ ] 将模块 5 状态更新为已实现，仅记录本阶段链接生成，不把素材下载、订单或转化分析写成已实现。
- [ ] 分别提交后端和用户端，提交前核对只包含本阶段文件。
