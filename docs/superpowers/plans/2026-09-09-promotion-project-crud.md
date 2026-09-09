# Promotion Project CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add administrator-managed promotion project CRUD with uploaded covers and render enabled projects as ordered user workspace cards.

**Architecture:** Add an isolated `promotion_project` aggregate inside the existing backend promotion package. Admin multipart endpoints own CRUD and cover lifecycle; a read-only user endpoint exposes enabled projects. The admin React app owns management UI, while the user React app replaces its static workspace array with TanStack Query data.

**Tech Stack:** Java 25, Spring Boot 4, MyBatis, MySQL/Flyway, H2 tests, React 19, TypeScript 6, Ant Design, TDesign React, TanStack Query, Vitest.

---

### Task 1: Database contract

**Files:**
- Create: `kasi-backend/src/main/resources/db/migration/V6__promotion_project.sql`
- Modify: `kasi-backend/src/main/resources/db/kasi_promotion.sql`
- Modify: `kasi-backend/src/test/resources/test-schema.sql`
- Create: `kasi-backend/src/test/java/com/kasi/backend/PromotionProjectMigrationTest.java`

- [x] **Step 1: Write the failing migration contract test**

Assert both the immutable V6 migration and the development rebuild script contain `promotion_project`, its five business columns, timestamps, enabled/status constraints, and the `(status, sort_order, id)` query index.

- [x] **Step 2: Verify RED**

Run: `.\mvnw.cmd -Dtest=PromotionProjectMigrationTest test`

Expected: FAIL because V6 and the table do not exist.

- [x] **Step 3: Add the schema**

Use the same table definition in V6 and the rebuild script:

```sql
CREATE TABLE promotion_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    cover_image_url VARCHAR(512) NOT NULL,
    project_document_url VARCHAR(1024) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_promotion_project_user_list (status, sort_order, id)
);
```

Add the H2-compatible equivalent to `test-schema.sql` without MySQL's `ON UPDATE` clause.

- [x] **Step 4: Verify GREEN**

Run: `.\mvnw.cmd -Dtest=PromotionProjectMigrationTest test`

Expected: PASS.

### Task 2: Backend persistence and service behavior

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/enums/PromotionProjectStatus.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/entity/PromotionProject.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/PromotionProjectPageQueryDTO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/dto/UpsertPromotionProjectDTO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/PromotionProjectVO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/vo/PromotionProjectPageVO.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/mapper/PromotionProjectMapper.java`
- Create: `kasi-backend/src/main/resources/mapper/PromotionProjectMapper.xml`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/PromotionProjectService.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionProjectServiceImpl.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/common/exception/ErrorCode.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/promotion/mapper/PromotionProjectPersistenceTest.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/PromotionProjectServiceTest.java`

- [x] **Step 1: Write failing persistence tests**

Cover insert with generated ID, detail lookup, paged admin ordering, enabled-only user ordering, update, and physical delete.

- [x] **Step 2: Verify persistence RED**

Run: `.\mvnw.cmd -Dtest=PromotionProjectPersistenceTest test`

Expected: FAIL because the mapper does not exist.

- [x] **Step 3: Implement the entity and mapper**

Use `PromotionProjectStatus { ENABLED, DISABLED }`. Mapper methods are `insert`, `findById`, `countAll`, `findPage`, `findEnabled`, `update`, and `deleteById`; both list queries order by `sort_order ASC, id ASC`.

- [x] **Step 4: Verify persistence GREEN**

Run: `.\mvnw.cmd -Dtest=PromotionProjectPersistenceTest test`

Expected: PASS.

- [x] **Step 5: Write failing service tests**

Cover trimmed required name, HTTPS document URL, non-negative sort order, default enabled status, not-found update/delete, user enabled filtering, and physical deletion.

- [x] **Step 6: Verify service RED**

Run: `.\mvnw.cmd -Dtest=PromotionProjectServiceTest test`

Expected: FAIL because the service and project error codes do not exist.

- [x] **Step 7: Implement the service**

Add `PROMOTION_PROJECT_NOT_FOUND`, `PROMOTION_PROJECT_IMAGE_INVALID`, and `PROMOTION_PROJECT_IMAGE_TOO_LARGE` errors. Validate business fields in the service and map entities to immutable response objects.

- [x] **Step 8: Verify service GREEN**

Run: `.\mvnw.cmd -Dtest=PromotionProjectServiceTest test`

Expected: PASS.

### Task 3: Backend cover storage and HTTP APIs

**Files:**
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/PromotionProjectCoverStorageService.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/service/impl/PromotionProjectCoverStorageServiceImpl.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/config/PromotionProjectCoverWebConfig.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/controller/AdminPromotionProjectController.java`
- Create: `kasi-backend/src/main/java/com/kasi/backend/promotion/controller/UserPromotionProjectController.java`
- Modify: `kasi-backend/src/main/java/com/kasi/backend/security/config/SecurityConfig.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/promotion/service/PromotionProjectCoverStorageServiceTest.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/AdminPromotionProjectControllerTest.java`
- Create: `kasi-backend/src/test/java/com/kasi/backend/promotion/controller/UserPromotionProjectControllerTest.java`

- [x] **Step 1: Write failing cover storage tests**

Cover empty, oversized, forged, JPG, PNG, WebP, generated paths under `/uploads/promotion-projects/`, and traversal-safe deletion.

- [x] **Step 2: Verify storage RED**

Run: `.\mvnw.cmd -Dtest=PromotionProjectCoverStorageServiceTest test`

Expected: FAIL because the storage service does not exist.

- [x] **Step 3: Implement cover storage**

Mirror the existing avatar validation contract with a separate `promotion-projects` directory and project-specific error codes. Expose the public directory through Spring MVC and permit only `/uploads/promotion-projects/**` anonymously.

- [x] **Step 4: Verify storage GREEN**

Run: `.\mvnw.cmd -Dtest=PromotionProjectCoverStorageServiceTest test`

Expected: PASS.

- [x] **Step 5: Write failing controller tests**

Verify authenticated admins can list, read, create multipart projects, update with an optional cover, and delete. Verify users only receive enabled projects through `GET /api/user/promotion/projects`.

- [x] **Step 6: Verify controller RED**

Run: `.\mvnw.cmd -Dtest=AdminPromotionProjectControllerTest,UserPromotionProjectControllerTest test`

Expected: FAIL with missing controller routes.

- [x] **Step 7: Implement controllers and file transaction completion**

Admin endpoints use the approved paths and `multipart/form-data`; the user endpoint returns a list. Store a new file before the database write, delete it on failure, delete replaced/deleted covers only after commit, and keep the previous file on rollback.

- [x] **Step 8: Verify backend feature GREEN**

Run: `.\mvnw.cmd -Dtest=PromotionProjectMigrationTest,PromotionProjectPersistenceTest,PromotionProjectServiceTest,PromotionProjectCoverStorageServiceTest,AdminPromotionProjectControllerTest,UserPromotionProjectControllerTest test`

Expected: all project tests PASS.

### Task 4: Admin project management page

**Files:**
- Create: `kasi-admin-web/src/features/promotionProject/promotionProjectTypes.ts`
- Create: `kasi-admin-web/src/features/promotionProject/promotionProjectApi.ts`
- Create: `kasi-admin-web/src/pages/promotion/PromotionProjectPage.tsx`
- Create: `kasi-admin-web/src/pages/promotion/PromotionProjectPage.test.tsx`
- Create: `kasi-admin-web/src/pages/promotion/promotion-project-page.css`
- Modify: `kasi-admin-web/src/layouts/AdminLayout.tsx`
- Modify: `kasi-admin-web/src/router/AppRouter.tsx`
- Modify: `kasi-admin-web/src/App.test.tsx`

- [x] **Step 1: Write failing page tests**

Mock the admin API and verify the ordered table, add form with required cover, edit retaining the prior cover, enabled/disabled values, sort order, and confirmed physical delete.

- [x] **Step 2: Verify admin RED**

Run: `pnpm test -- src/pages/promotion/PromotionProjectPage.test.tsx`

Expected: FAIL because the page and API do not exist.

- [x] **Step 3: Implement types and multipart API**

Define `PromotionProject`, `PromotionProjectPage`, and `PromotionProjectFormValues`. Build `FormData` with `name`, `projectDocumentUrl`, `status`, `sortOrder`, and optional `coverFile`; unwrap the established `ApiResponse` shape.

- [x] **Step 4: Implement the page**

Use Ant Design `Table`, `Modal`, `Form`, `Upload`, `Image`, `Input`, `InputNumber`, `Select`, and icon buttons. Keep one framed table surface, show a cover preview, require delete confirmation, and invalidate `['promotion-projects']` after mutations.

- [x] **Step 5: Add route and navigation**

Register `/promotion/projects` and add “项目管理” under the existing promotion navigation group without changing unrelated menu structure.

- [x] **Step 6: Verify admin GREEN**

Run: `pnpm test -- src/pages/promotion/PromotionProjectPage.test.tsx src/App.test.tsx`

Expected: PASS.

### Task 5: User workspace cards

**Files:**
- Create: `kasi-user-web/src/features/projects/projectTypes.ts`
- Create: `kasi-user-web/src/features/projects/projectApi.ts`
- Create: `kasi-user-web/src/features/projects/projectApi.test.ts`
- Create: `kasi-user-web/src/pages/WorkspacePage.test.tsx`
- Modify: `kasi-user-web/src/pages/WorkspacePage.tsx`
- Modify: `kasi-user-web/src/pages/WorkspacePage.css`
- Modify: `kasi-user-web/src/App.test.tsx`

- [x] **Step 1: Write failing API and page tests**

Verify API unwrapping and a page that renders one card per returned project, opens the HTTPS document with `target="_blank" rel="noopener noreferrer"`, preserves response order, and displays loading, retry, and empty states.

- [x] **Step 2: Verify user RED**

Run: `pnpm test -- src/features/projects/projectApi.test.ts src/pages/WorkspacePage.test.tsx`

Expected: FAIL because the project feature does not exist and the page still uses a static array.

- [x] **Step 3: Implement the project query and cards**

Call `GET /api/user/promotion/projects` through `shared/api/httpClient.ts`, use query key `['promotion-projects']`, render `coverImageUrl` as the card background, and keep the existing responsive card layout with accessible document links.

- [x] **Step 4: Verify user GREEN**

Run: `pnpm test -- src/features/projects/projectApi.test.ts src/pages/WorkspacePage.test.tsx src/App.test.tsx`

Expected: PASS.

### Task 6: Documentation and canonical verification

**Files:**
- Modify: `kasi-backend/README.md`
- Modify: `kasi-admin-web/README.md`
- Modify: `kasi-user-web/README.md`
- Modify: `docs/superpowers/specs/2026-09-09-promotion-project-card-management-design.md`

- [x] **Step 1: Update current documentation**

Document the implemented table, admin CRUD, uploaded cover path, admin route, user endpoint, enabled-only ordering, and workspace card behavior. Change the design status to implemented only after all gates pass.

- [x] **Step 2: Run backend canonical gate**

Run from `kasi-backend`: `.\mvnw.cmd verify`

Result: exit 1; 480 tests, 478 passed, 2 existing GoodShort scheduled-task failures, 1 skipped.

- [x] **Step 3: Run admin canonical gate**

Run from `kasi-admin-web`: `pnpm check`

Result: exit 0; 20 test files and 100 tests passed, lint/format/build passed.

- [x] **Step 4: Run user canonical gate**

Run from `kasi-user-web`: `pnpm check`

Result: exit 0; 20 test files and 65 tests passed, lint/format/build passed with existing warnings.

- [x] **Step 5: Verify workspace scope**

Run from root: `git diff --check` and `git status --short --branch`.

Expected: no whitespace errors; only this feature plus the user's pre-existing untracked documents appear.
