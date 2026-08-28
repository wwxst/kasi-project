# GoodShort Dual-Link Promotion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户一次选择短剧和多个媒体平台后，每个平台生成一条落地页和一条 OneLink；推广链接不绑定具体媒体账号。

**Architecture:** 将 `promotion_link` 从“一个请求一条账号绑定链接”改为“一个批次下的平台/变体链接记录”。创建接口在数据库外逐条调用 GoodShort，使用不同 `trackingNo/customParams` 区分 `LANDING` 与 `ONELINK`，每条记录独立落库状态；用户端按 `batchNo` 聚合展示，但订单仍按单条 trackingNo 归因。

**Tech Stack:** Spring Boot 4、MyBatis XML、MySQL/Flyway、JUnit 5 + H2、React 19、TDesign React、TanStack Query、Axios、Vitest。

---

## 文件范围

后端新增/修改：

- `src/main/resources/db/migration/V19__promotion_link_dual_variants.sql`：迁移旧链接表，移除账号外键，增加媒体平台、变体和批次字段。
- `src/main/java/com/kasi/backend/promotion/dto/CreatePromotionLinkDTO.java`：改为接收 `mediaTypes`，移除 `mediaAccountId` 和单一 `landingType`。
- `src/main/java/com/kasi/backend/promotion/entity/PromotionLink.java`、`vo/PromotionLinkVO.java`：表达 `batchNo`、`mediaType`、`linkVariant`，移除账号字段。
- `src/main/java/com/kasi/backend/promotion/vo/PromotionLinkBatchVO.java`：批量创建响应。
- `src/main/java/com/kasi/backend/promotion/service/PromotionLinkService.java`、`service/impl/PromotionLinkServiceImpl.java`：批量编排与部分成功。
- `src/main/java/com/kasi/backend/promotion/service/PromotionLinkPersistenceService.java`、`service/impl/PromotionLinkPersistenceServiceImpl.java`：批次预创建、变体幂等和独立状态写入。
- `src/main/java/com/kasi/backend/promotion/mapper/PromotionLinkMapper.java`、`src/main/resources/mapper/PromotionLinkMapper.xml`：批次查询、变体查询和新字段映射。
- `src/main/java/com/kasi/backend/provider/spi/PromotionLinkRequest.java`、`src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`：将变体转换为 `shareUrlType=1/2`。
- `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderServiceImpl.java`：归因后不再从链接写入媒体账号。
- `src/test/resources/test-schema.sql` 及相关 promotion/provider 测试：同步新表结构和行为。
- `README.md`、`AGENTS.md`：记录当前批量双链接行为与账号解耦边界。

用户端新增/修改：

- `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotionLinks/types.ts`：批次、链接变体和创建请求类型。
- `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotionLinks/promotionLinksApi.ts`：创建与分页查询 API。
- `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.tsx`、`PromotionLinksPage.module.less`：Starter 风格筛选列表、批次行和复制操作。
- `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/drama/DramaPage.tsx`：创建推广按钮打开批量生成抽屉或跳转带短剧参数。
- `E:/JavaProjects/kasi-project/kasi-user-web/src/app/routes.tsx`：把推广链接占位路由替换为真实页面。
- 对应 `*.test.tsx`、`*.test.ts`：覆盖多平台、双变体、部分失败和复制。

不修改：GoodShort 账号报白接口和媒体账号页面；免费内容播放/下载；`PromotionTask` 占位模块；管理员订单业务字段，除非为兼容空媒体账号归因所需的最小映射调整。

### Task 1: 固定新 API 和数据契约

**Files:**
- Modify: `src/main/java/com/kasi/backend/promotion/dto/CreatePromotionLinkDTO.java`
- Create: `src/main/java/com/kasi/backend/promotion/vo/PromotionLinkBatchVO.java`
- Modify: `src/main/java/com/kasi/backend/promotion/entity/PromotionLink.java`
- Modify: `src/main/java/com/kasi/backend/promotion/vo/PromotionLinkVO.java`
- Test: `src/test/java/com/kasi/backend/promotion/controller/UserPromotionLinkControllerTest.java` (create if absent)

- [ ] **Step 1: Write failing DTO/controller tests**

测试请求必须接受 `providerId`、`dramaId`、`mediaTypes`、UUID `requestKey` 和可选 `campaignName`；缺少 `mediaTypes`、包含第五个平台或包含 `mediaAccountId` 时分别验证校验/契约行为。批量响应断言包含 `batchNo` 和两条变体记录。

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./mvnw.cmd --% -Dtest=UserPromotionLinkControllerTest test`

Expected: FAIL because the current DTO requires `mediaAccountId` and returns a single `PromotionLinkVO`.

- [ ] **Step 3: Implement the Java contract**

`CreatePromotionLinkDTO` 使用 `@NotEmpty @Size(max = 4) List<@Pattern(regexp = "TIKTOK|YOUTUBE|FACEBOOK|INSTAGRAM") String> mediaTypes`，保留 provider/drama/request/campaign 字段；服务内部固定生成 `LANDING`、`ONELINK`，不把变体列表暴露给前端。`PromotionLinkVO` 增加 `batchNo`、`linkVariant` 并删除 `mediaAccountId/mediaAccountName/landingType`；新增 `PromotionLinkBatchVO(batchNo, links, complete)`。

- [ ] **Step 4: Run the focused test**

Run: `./mvnw.cmd --% -Dtest=UserPromotionLinkControllerTest test`

Expected: PASS for request binding and response shape; service behavior remains failing until Task 3.

- [ ] **Step 5: Commit the contract slice**

```powershell
git add src/main/java/com/kasi/backend/promotion/dto/CreatePromotionLinkDTO.java src/main/java/com/kasi/backend/promotion/entity/PromotionLink.java src/main/java/com/kasi/backend/promotion/vo/PromotionLinkVO.java src/main/java/com/kasi/backend/promotion/vo/PromotionLinkBatchVO.java src/test/java/com/kasi/backend/promotion/controller/UserPromotionLinkControllerTest.java
git commit -m "refactor: define dual-link promotion contract"
```

### Task 2: Migrate `promotion_link` storage and MyBatis mappings

**Files:**
- Create: `src/main/resources/db/migration/V19__promotion_link_dual_variants.sql`
- Modify: `src/main/resources/mapper/PromotionLinkMapper.xml`
- Modify: `src/main/java/com/kasi/backend/promotion/mapper/PromotionLinkMapper.java`
- Modify: `src/test/resources/test-schema.sql`
- Create: `src/test/java/com/kasi/backend/promotion/mapper/PromotionLinkDualVariantMigrationTest.java`
- Test: `src/test/java/com/kasi/backend/promotion/mapper/PromotionLinkPersistenceTest.java`

- [ ] **Step 1: Add a migration test before the migration**

验证新列存在、旧账号外键和旧 `(user_id, request_key)` 唯一索引不再存在；验证 `media_type`、`link_variant`、`batch_no` 非空，唯一键为 `(user_id, request_key, media_type, link_variant)`，`tracking_no` 仍全局唯一。

- [ ] **Step 2: Run the migration test and verify failure**

Run: `./mvnw.cmd --% -Dtest=PromotionLinkDualVariantMigrationTest,PromotionLinkPersistenceTest test`

Expected: FAIL because V19 and the new mapper fields do not exist.

- [ ] **Step 3: Implement V19 without rewriting V1**

迁移步骤按顺序执行：新增可回填的 `media_type`、`link_variant`、`batch_no`；从 `promotion_media_account` 回填历史 `media_type`，历史变体统一为 `LANDING`，历史批次使用 `legacy-{id}`；删除旧账号外键、`media_account_id`、旧 `landing_type`；删除旧唯一键并创建新唯一键和批次索引。开发数据库允许重建，历史生产数据的回填必须在删除列之前完成。

- [ ] **Step 4: Update mapper methods and SQL**

将 `findByUserAndRequestKey*` 改为按 `userId/requestKey/mediaType/linkVariant` 查询；增加 `findBatchByUserAndRequestKey`、`findPageByUserId` 的批次字段映射；插入和状态更新使用 `batch_no/media_type/link_variant`。列表查询不再 JOIN `promotion_media_account`。

- [ ] **Step 5: Update H2 schema and run persistence tests**

Run: `./mvnw.cmd --% -Dtest=PromotionLinkDualVariantMigrationTest,PromotionLinkPersistenceTest test`

Expected: PASS with zero failures and zero errors.

- [ ] **Step 6: Commit the storage slice**

```powershell
git add src/main/resources/db/migration/V19__promotion_link_dual_variants.sql src/main/resources/mapper/PromotionLinkMapper.xml src/main/java/com/kasi/backend/promotion/mapper/PromotionLinkMapper.java src/test/resources/test-schema.sql src/test/java/com/kasi/backend/promotion/mapper/PromotionLinkPersistenceTest.java
git commit -m "feat: migrate promotion links to platform variants"
```

### Task 3: Implement GoodShort dual-variant generation

**Files:**
- Modify: `src/main/java/com/kasi/backend/provider/spi/PromotionLinkRequest.java`
- Modify: `src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/PromotionLinkPersistenceService.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkPersistenceServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/PromotionLinkService.java`
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkServiceImpl.java`
- Test: `src/test/java/com/kasi/backend/provider/goodshort/GoodShortPromotionLinkAdapterTest.java`
- Test: `src/test/java/com/kasi/backend/promotion/service/PromotionLinkServiceTest.java`

- [ ] **Step 1: Add failing adapter tests for both `shareUrlType` values**

固定时钟下，断言 `LANDING` 发送 `shareUrlType=1`，`ONELINK` 发送 `shareUrlType=2`；两次请求必须带不同 `customParams`，并分别解析 `code/shareUrl/customParams`。

- [ ] **Step 2: Run adapter tests and verify failure**

Run: `./mvnw.cmd --% -Dtest=GoodShortPromotionLinkAdapterTest test`

Expected: FAIL because `PromotionLinkRequest` 仍使用单一 `landingType`，服务只生成一条记录。

- [ ] **Step 3: Implement request and adapter mapping**

把 SPI 请求字段改为 `linkVariant`；GoodShort 适配器只映射 `LANDING -> 1`、`ONELINK -> 2`，继续发送 `pid/bookId/customParams/codeMedia/timestamp`。`codeMedia` 只允许四个平台，`GOOGLE` 等官方其他值不在本期用户 DTO 中开放。

- [ ] **Step 4: Add batch orchestration tests**

覆盖：一个平台生成两条成功记录；两个平台生成四条记录；同一 `requestKey` 重试不再次调用已成功变体；一个变体远程失败时其他变体仍成功；不创建任何媒体账号或报白查询。

- [ ] **Step 5: Implement persistence and service orchestration**

`prepareBatchPending` 在短事务中校验用户、目标平台/短剧状态、连接、适配器能力和分佣规则，并按平台×变体预创建 `PENDING`；为每条记录生成独立 `trackingNo/customParams` 和共享 `batchNo`。事务提交后，Service 串行调用 GoodShort；每条调用结束后用独立短事务写 `SUCCESS` 或 `FAILED`。已成功变体直接返回，失败/缺失变体才重试。

- [ ] **Step 6: Run promotion tests**

Run: `./mvnw.cmd --% -Dtest=GoodShortPromotionLinkAdapterTest,PromotionLinkServiceTest,UserPromotionLinkControllerTest test`

Expected: PASS with all dual-variant and partial-failure cases.

- [ ] **Step 7: Commit the generation slice**

```powershell
git add src/main/java/com/kasi/backend/provider/spi/PromotionLinkRequest.java src/main/java/com/kasi/backend/provider/goodshort/GoodShortAdapter.java src/main/java/com/kasi/backend/promotion/service/PromotionLinkPersistenceService.java src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkPersistenceServiceImpl.java src/main/java/com/kasi/backend/promotion/service/PromotionLinkService.java src/main/java/com/kasi/backend/promotion/service/impl/PromotionLinkServiceImpl.java src/test/java/com/kasi/backend/provider/goodshort/GoodShortPromotionLinkAdapterTest.java src/test/java/com/kasi/backend/promotion/service/PromotionLinkServiceTest.java src/test/java/com/kasi/backend/promotion/controller/UserPromotionLinkControllerTest.java
git commit -m "feat: generate GoodShort landing and onelink variants"
```

### Task 4: Remove account coupling from order attribution and documentation

**Files:**
- Modify: `src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/promotion/vo/PromotionOrderVO.java` only if it exposes a mandatory account field
- Modify: `README.md`
- Modify: `AGENTS.md`
- Test: `src/test/java/com/kasi/backend/promotion/service/PromotionOrderServiceTest.java`

- [ ] **Step 1: Add attribution regression test**

插入一条无媒体账号的推广链接和一条 GoodShort 订单，断言订单按 `customParams` 找到用户、推广链接、短剧和 `trackingNo`，但 `mediaAccountId` 为空；同批次另一变体使用自己的 trackingNo 归因到同一用户。

- [ ] **Step 2: Implement minimal attribution change**

删除 `order.setMediaAccountId(link.getMediaAccountId())` 及所有要求链接存在媒体账号的分支；保留 `promotionLinkId/userId/dramaId` 和佣金快照逻辑。订单表中已有的可空 `media_account_id` 保留为空，避免无关 schema 扩大。

- [ ] **Step 3: Update current-state documentation**

README/AGENTS 明确：推广链接按批次、媒体平台和 `LANDING/ONELINK` 变体生成，不绑定账号；账号报白是独立能力；`PromotionTask` 仍是未接入真实链接的占位模块。同步补充 V19 和重建开发数据库说明。

- [ ] **Step 4: Run attribution and diff checks**

Run: `./mvnw.cmd --% -Dtest=PromotionOrderServiceTest,PromotionOrderPersistenceTest test`; `git diff --check`

Expected: targeted tests pass; `git diff --check` has no output.

- [ ] **Step 5: Commit attribution/docs slice**

```powershell
git add src/main/java/com/kasi/backend/promotion/service/impl/PromotionOrderServiceImpl.java src/main/java/com/kasi/backend/promotion/vo/PromotionOrderVO.java src/test/java/com/kasi/backend/promotion/service/PromotionOrderServiceTest.java README.md AGENTS.md
git commit -m "docs: decouple promotion links from media accounts"
```

### Task 5: Add user-facing batch API client and promotion links page

**Files:**
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotionLinks/types.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotionLinks/promotionLinksApi.ts`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.tsx`
- Create: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.module.less`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/app/routes.tsx`
- Test: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotionLinks/PromotionLinksPage.test.tsx`

- [ ] **Step 1: Write failing API and page tests**

MSW/Mock Axios tests must verify POST body contains `providerId/dramaId/mediaTypes/requestKey/campaignName` and does not contain `mediaAccountId`; page tests must render platform multi-select, batch rows with Landing/OneLink columns, copy buttons, partial failure status and retry action.

- [ ] **Step 2: Run frontend tests and verify failure**

Run from `E:/JavaProjects/kasi-project/kasi-user-web`: `pnpm exec vitest run src/pages/promotionLinks/PromotionLinksPage.test.tsx --exclude '.worktrees/**'`

Expected: FAIL because the page and API client do not exist and the route is still a workspace placeholder.

- [ ] **Step 3: Implement API types and client**

`createPromotionLinks` accepts selected `mediaTypes` and creates a UUID `requestKey`; `getPromotionLinks` reads the paginated flat records; a pure grouping helper groups records by `batchNo` and maps each platform to `landing/onelink` without inventing links.

- [ ] **Step 4: Implement the page using existing TDesign patterns**

Use the existing Starter-style page container, filter form, table and pagination. The create drawer shows short-drama context, a multi-select restricted to four platforms, campaign name, submit/loading state, and result rows. Display labels are “落地页” and “OneLink”; never display media account selectors, filing state or TikTok anchor controls. Copy actions use the existing browser clipboard/error-message pattern.

- [ ] **Step 5: Replace the route placeholder and run tests**

Update `routes.tsx` to use `PromotionLinksPage` for `/workspace/promotion-links`; preserve `DramaPage` query parameters so clicking “创建推广任务” opens the same drama in the drawer.

Run: `pnpm exec vitest run src/pages/promotionLinks/PromotionLinksPage.test.tsx src/pages/drama/DramaPage.test.tsx --exclude '.worktrees/**'`

Expected: PASS with no layout or interaction failures.

- [ ] **Step 6: Freeze the frontend verification checkpoint**

`kasi-user-web` 当前没有独立 Git 元数据，因此不在后端仓库执行 `git add` 或 `git commit`。记录不会被后端 `git status` 覆盖的前端文件清单，并保留前端变更供用户端工作区统一提交；前端代码的提交由其实际所属仓库在发布流程中完成。

### Task 6: Integrate, verify and document release boundary

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/README.md`
- Test: backend focused suites and frontend focused suites

- [ ] **Step 1: Run backend focused verification**

Run with Java 25: `./mvnw.cmd --% -Dtest=PromotionLinkDualVariantMigrationTest,PromotionLinkPersistenceTest,GoodShortPromotionLinkAdapterTest,PromotionLinkServiceTest,PromotionOrderServiceTest,UserPromotionLinkControllerTest test`

Expected: zero failures and zero errors.

- [ ] **Step 2: Run frontend verification**

Run from `E:/JavaProjects/kasi-project/kasi-user-web`: `pnpm exec vitest run src/pages/promotionLinks/PromotionLinksPage.test.tsx src/pages/drama/DramaPage.test.tsx --exclude '.worktrees/**'`; `pnpm run build`

Expected: focused tests pass and production build exits 0.

- [ ] **Step 3: Perform browser acceptance**

登录用户端，进入短剧页，打开一个短剧的创建抽屉，选择两个媒体平台并提交；确认每个平台出现落地页和 OneLink 两个结果，复制按钮可用；模拟一个变体失败时，其他成功链接仍显示且失败变体可独立重试；确认页面没有媒体账号、报白和 TikTok 锚点。

- [ ] **Step 4: Review final diff and documentation**

Run in the backend repository: `git diff --check`; `git status --short --branch`; `git diff --cached --name-only`. Run in the user frontend directory: `git status --short` if Git metadata is present, otherwise inspect the changed file list directly. Ensure no credentials, generated files or unrelated worktree edits are staged.

- [ ] **Step 5: Freeze user-web documentation**

Update `E:/JavaProjects/kasi-project/kasi-user-web/README.md` with the final route, request shape and the explicit no-account-binding boundary. Do not commit it from the backend repository; leave it for the user-web repository's normal commit flow.

## Self-review checklist

- Spec coverage: one user action, four platform limit, 2N links, distinct customParams, no account binding, partial failure, retry, order attribution and UI labels are covered by Tasks 1-6.
- Placeholder scan: no `TODO`, `TBD`, or unspecified “appropriate handling” steps remain; each test has a command and expected result.
- Type consistency: `mediaTypes`, `batchNo`, `linkVariant`, `trackingNo`, and `(user_id, request_key, media_type, link_variant)` are used consistently across migration, Java API, and TypeScript client.
- Boundary: free-content playback/download and `PromotionTask` remain explicitly out of scope.
