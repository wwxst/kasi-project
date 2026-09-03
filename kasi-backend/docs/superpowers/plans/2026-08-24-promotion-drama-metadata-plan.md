# 创建推广短剧元数据 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为“创建推广”短剧资源列表增加真实可维护的分佣范围、上线时间、推广说明、语言和类型字段，并保持推广任务页面不变。

**Architecture:** 在现有 `provider_drama` 表上增加本地推广元数据列。管理员通过目录 Controller 更新这两个本地字段；用户目录 VO 同时返回远端简介、远端更新时间和本地推广元数据。前端只消费用户接口，不写死截图内容。

**Tech Stack:** Spring Boot 4、MyBatis XML、Flyway、H2 MySQL 模式、Java 25、React 19、TDesign React、TanStack Query、Vitest、Testing Library。

---

### Task 1: 添加短剧推广元数据持久化契约

**Files:**
- Create: `src/main/resources/db/migration/V14__provider_drama_promotion_metadata.sql`
- Modify: `src/test/resources/test-schema.sql`
- Modify: `src/main/java/com/kasi/backend/drama/entity/ProviderDrama.java`
- Modify: `src/main/java/com/kasi/backend/drama/mapper/ProviderDramaMapper.java`
- Modify: `src/main/resources/mapper/ProviderDramaMapper.xml`
- Test: `src/test/java/com/kasi/backend/drama/mapper/DramaCatalogPersistenceTest.java`

- [ ] **Step 1: Write the failing persistence test**

Add a test that upserts a drama, updates metadata through the mapper, reads it back, and proves a later catalog upsert does not overwrite it:

```java
@Test
@DisplayName("推广元数据可保存且目录同步upsert不会覆盖")
void promotionMetadataSurvivesCatalogUpsert() {
    Long connectionId = insertConnection();
    ProviderDrama drama = drama(connectionId, "metadata-book");
    drama.setCommissionScope("ORDER,AD");
    drama.setPromotionDescription("1. 单个视频建议不超过17分钟");
    dramaMapper.upsert(drama);
    Long id = dramaMapper.findByConnectionAndExternalId(connectionId, "metadata-book").getId();

    assertThat(dramaMapper.updatePromotionMetadata(id, "AD", "2. 点击创建推广任务获取")).isEqualTo(1);
    drama.setTitle("Remote title updated");
    drama.setCommissionScope(null);
    drama.setPromotionDescription(null);
    dramaMapper.upsert(drama);

    ProviderDrama stored = dramaMapper.findById(id);
    assertThat(stored.getCommissionScope()).isEqualTo("AD");
    assertThat(stored.getPromotionDescription()).isEqualTo("2. 点击创建推广任务获取");
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run `./mvnw.cmd --% -Dtest=DramaCatalogPersistenceTest#promotionMetadataSurvivesCatalogUpsert test` with Java 25. Expected failure: missing mapper method/columns.

- [ ] **Step 3: Add the migration and H2 columns**

Create V14 with:

```sql
ALTER TABLE `provider_drama`
    ADD COLUMN `commission_scope` VARCHAR(255) DEFAULT NULL COMMENT '推广分佣范围编码，逗号分隔',
    ADD COLUMN `promotion_description` TEXT DEFAULT NULL COMMENT '推广说明';
```

Add matching nullable `VARCHAR(255)` and `CLOB` columns to the `provider_drama` definition in `test-schema.sql`.

- [ ] **Step 4: Map the fields without changing catalog upsert ownership**

Add `String commissionScope` and `String promotionDescription` to `ProviderDrama`, map them in `DramaMap`, and include them in the `SELECT` result. Do not add either column to the `INSERT ... ON DUPLICATE KEY UPDATE` list in `upsert`; add mapper method `int updatePromotionMetadata(Long id, String commissionScope, String promotionDescription)` and a dedicated update statement.

- [ ] **Step 5: Run the persistence test and verify it passes**

Run the same focused Maven test. Expected: PASS with the metadata preserved after upsert.

- [ ] **Step 6: Commit the persistence slice**

Stage only the V14/schema/entity/mapper/test files and commit `feat: persist promotion drama metadata`.

### Task 2: Add admin metadata update API and user VO fields

**Files:**
- Create: `src/main/java/com/kasi/backend/drama/enums/PromotionCommissionScope.java`
- Create: `src/main/java/com/kasi/backend/drama/dto/UpdateDramaPromotionMetadataDTO.java`
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaListItemVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/vo/DramaDetailVO.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/DramaCatalogAdminService.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/DramaCatalogAdminServiceImpl.java`
- Modify: `src/main/java/com/kasi/backend/drama/controller/AdminDramaCatalogController.java`
- Modify: `src/main/java/com/kasi/backend/drama/service/impl/UserPromotionDramaServiceImpl.java`
- Test: `src/test/java/com/kasi/backend/drama/service/DramaCatalogAdminServiceTest.java`
- Test: `src/test/java/com/kasi/backend/drama/controller/AdminDramaCatalogControllerTest.java`
- Test: `src/test/java/com/kasi/backend/drama/controller/UserPromotionDramaControllerTest.java`

- [ ] **Step 1: Write service and controller tests first**

Add service coverage for scope normalization and blank description clearing, plus controller coverage for a valid update and validation rejection:

```java
@Test
@DisplayName("推广元数据更新会规范化范围并清理空说明")
void updatePromotionMetadataNormalizesScopes() {
    when(dramaMapper.findById(21L)).thenReturn(drama());
    when(dramaMapper.updatePromotionMetadata(21L, "ORDER,AD", "说明")).thenReturn(1);

    service.updatePromotionMetadata(21L,
            List.of(PromotionCommissionScope.AD, PromotionCommissionScope.ORDER,
                    PromotionCommissionScope.AD), "  说明  ");

    verify(dramaMapper).updatePromotionMetadata(21L, "ORDER,AD", "说明");
}
```

The controller test sends `PUT /api/admin/drama/catalog/{id}/promotion-metadata` with `{"commissionScopes":["ORDER","AD"],"promotionDescription":"说明"}` and asserts HTTP 200 plus `commissionScopes`. A second request with an unknown scope asserts application code `1006`. The user controller test asserts the published list contains `description`, `commissionScopes`, `promotionDescription`, and `remoteUpdatedAt` when seeded.

- [ ] **Step 2: Run the new focused tests and verify expected red failures**

Run `./mvnw.cmd --% -Dtest=DramaCatalogAdminServiceTest,AdminDramaCatalogControllerTest,UserPromotionDramaControllerTest test`. Expected failures identify missing DTO, endpoint and VO fields.

- [ ] **Step 3: Implement the DTO, enum, service method and mappings**

Use `PromotionCommissionScope { ORDER, AD }`. The DTO requires `commissionScopes` to be present, limits it to two values, validates each item, and limits `promotionDescription` to 2000 characters. In the service, reject unknown values through Jakarta binding, trim the description to null, deduplicate scopes, sort in enum order, join as `ORDER,AD`, update the mapper, and return the refreshed detail.

Expose `description`, `commissionScopes`, `promotionDescription`, and `remoteUpdatedAt` in both list/detail VOs. Convert the stored string with `split(",")`, ignoring blanks. Add `@PutMapping("/{id}/promotion-metadata")` to the existing admin controller.

- [ ] **Step 4: Run focused backend tests and verify green**

Run the Task 2 Maven command again. Expected: all focused controller/service tests PASS with no validation regressions.

- [ ] **Step 5: Commit the API slice**

Stage only Task 2 files and commit `feat: expose promotion drama metadata API`.

### Task 3: Extend the user frontend contract and add red tests

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/features/promotion/api/dramaTypes.ts`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionLinkPage.test.tsx`

- [ ] **Step 1: Add failing assertions for the five reference fields**

Extend the MSW drama fixture with `description`, `commissionScopes: ['ORDER','AD']`, `promotionDescription`, `remoteUpdatedAt`, and assert the rendered page shows `订单`, `广告`, `2026-08-23 20:24:46`, the two-line explanation, `英语`, and `本土剧`.

- [ ] **Step 2: Run the targeted frontend test and verify it fails**

From `E:/JavaProjects/kasi-project/kasi-user-web`, run `pnpm test -- PromotionLinkPage.test.tsx`. Expected failure: the new field text is not rendered.

- [ ] **Step 3: Update the TypeScript contract**

Add optional `description`, `commissionScopes?: Array<'ORDER' | 'AD'>`, `promotionDescription`, and `remoteUpdatedAt` to `PromotionDrama`; keep existing fields unchanged.

### Task 4: Implement the reference-style “创建推广” table

**Files:**
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionLinkPage.tsx`
- Modify: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/promotion-link.css`
- Test: `E:/JavaProjects/kasi-project/kasi-user-web/src/pages/promotion/PromotionLinkPage.test.tsx`

- [ ] **Step 1: Implement the rich short-drama cell and five columns**

Add presentation helpers for language/type labels, local date formatting, scope tag themes, and a `PromotionDramaInfoCell` that renders cover, red title, optional tags, original title, and truncated description. Replace the simplified columns with:

```tsx
{ colKey: 'title', title: '短剧名称', width: 360, cell: ({ row }) => <PromotionDramaInfoCell drama={row} /> }
{ colKey: 'commissionScopes', title: '分佣范围', width: 150, cell: ({ row }) => ... }
{ colKey: 'remoteUpdatedAt', title: '上线时间', width: 170, cell: ({ row }) => formatDateTime(row.remoteUpdatedAt) }
{ colKey: 'promotionDescription', title: '推广说明', width: 280, cell: ({ row }) => ... }
{ colKey: 'language', title: '语言', width: 90, cell: ({ row }) => languageLabel(row.language) }
{ colKey: 'dramaType', title: '类型', width: 100, cell: ({ row }) => row.dramaType || '-' }
{ colKey: 'actions', title: '操作', width: 190, cell: ({ row }) => ... }
```

Keep the existing filters, drawer and task dialog. Do not add fields or columns to `PromotionTaskPage`.

- [ ] **Step 2: Replace card/gray-band styling with the table shell from the reference**

In `promotion-link.css`, keep the page layout but remove panel heading decoration and gray filter/table fills. Set table header and body backgrounds to `#fff`, use a 1px `#e7e7e7` bottom border per row, compact cell padding, a fixed minimum table width with horizontal scrolling, a 64px cover, and a left border on `.promotion-drama-actions`. Use `line-clamp`/ellipsis for long titles and promotion descriptions, and preserve a responsive single-column filter layout under 760px.

- [ ] **Step 3: Run the targeted frontend test and verify green**

Run `pnpm test -- PromotionLinkPage.test.tsx`. Expected: all existing interaction tests plus the new metadata assertions PASS.

- [ ] **Step 4: Commit the frontend slice**

Because `kasi-user-web` has no Git metadata, do not commit from that directory. Stage its changed files from the backend repository only if they are tracked by the parent repository; otherwise leave them in the shared worktree and report the exact paths.

### Task 5: Synchronize documentation and verify the full change

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`

- [ ] **Step 1: Document the V14 migration and metadata API**

Update the current-state tables/API section to mention `provider_drama` local promotion metadata, `PUT /api/admin/drama/catalog/{id}/promotion-metadata`, and that only “创建推广” displays the fields; keep the distinction between platform commission rules and drama-level promotion metadata.

- [ ] **Step 2: Run backend verification**

Set Java 25 in the same PowerShell command, then run `./mvnw.cmd test` and `./mvnw.cmd -DskipTests compile`. Expected: zero test failures and successful compile.

- [ ] **Step 3: Run frontend verification**

From `E:/JavaProjects/kasi-project/kasi-user-web`, run `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, and `pnpm build`. Expected: all commands exit 0.

- [ ] **Step 4: Check the final diff**

Run `git diff --check` and `git status --short`; verify no unrelated worktree files were staged or changed. Report any pre-existing dirty files separately.
