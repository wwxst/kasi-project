# Promotion Library Link Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将推广链接页升级为“短剧库筛选、短剧详情、推广任务表单、链接生成结果与历史记录”的完整第一阶段工作流。

**Architecture:** 在现有 `PromotionLinkPage` 上增加筛选状态、短剧列表分页和详情抽屉；继续通过 TanStack Query 调用现有短剧、媒体账号和推广链接 API，不新增后端接口或数据库结构。详情抽屉只展示短剧列表已有字段和明确的剧集空状态，不猜测不存在的剧集 API。

**Tech Stack:** React 19、TypeScript strict、TDesign React、TDesign Icons React、TanStack Query、Vitest、React Testing Library、MSW、Vite。

---

### Task 1: Extend promotion API query types

**Files:**

- Modify: `src/features/promotion/api/dramaTypes.ts`
- Modify: `src/features/promotion/api/promotionLinkApi.ts`
- Test: `src/features/promotion/api/promotionLinkApi.test.ts`

- [ ] **Step 1: Write failing API tests**

Add a test for `fetchPublishedPromotionDramas` that calls it with `{ title: 'Magic', providerId: 2, language: 'ENGLISH', dramaType: 'LOCAL', page: 2, size: 10 }` and asserts the MSW request URL contains all six query parameters. Add a second test asserting omitted optional filters are not serialized.

- [ ] **Step 2: Run the focused API test**

Run `pnpm exec vitest run src/features/promotion/api/promotionLinkApi.test.ts`. Expected result before implementation: TypeScript/test failure because the function accepts only numeric positional arguments.

- [ ] **Step 3: Implement typed filter parameters**

Add `PromotionDramaQuery` with optional `title`, `providerId`, `language`, `dramaType`, `localStatus`, `page`, and `size`. Change `fetchPublishedPromotionDramas(query: PromotionDramaQuery = {})` to pass a compact params object to `/api/user/promotion/dramas`; preserve default `page=1,size=20` and existing response validation. Keep `PromotionDrama` fields optional where the backend may return null.

- [ ] **Step 4: Run the focused API test**

Run `pnpm exec vitest run src/features/promotion/api/promotionLinkApi.test.ts`. Expected result: all API tests pass.

### Task 2: Build the short-drama library and detail drawer

**Files:**

- Modify: `src/pages/promotion/PromotionLinkPage.tsx`
- Modify: `src/pages/promotion/promotion-link.css`
- Test: `src/pages/promotion/PromotionLinkPage.test.tsx`

- [ ] **Step 1: Add failing page tests**

Cover these observable states in `PromotionLinkPage.test.tsx`: initial filters and table headers are visible; selecting a title and clicking “查询” sends the expected query; clicking a short-drama row opens a drawer with title, provider, language, type, and a “剧集信息由后端同步后提供” empty state; empty API data shows “暂无可推广短剧”.

- [ ] **Step 2: Run the page tests**

Run `pnpm exec vitest run src/pages/promotion/PromotionLinkPage.test.tsx`. Expected result before implementation: failures for missing filter controls, table, and drawer.

- [ ] **Step 3: Implement query state and library UI**

Add local state for `draftFilters` and `appliedFilters`, and query key `['promotion-dramas', appliedFilters]`. Render a TDesign filter form with title input, provider select, language select, type select, and search/reset buttons. Render a bordered table with cover, title, platform, language, type, updated time, and actions. Use a compact row action button with a `ChevronRight`/detail icon and text “查看详情”; keep “创建推广任务” as the primary action. Use `Drawer` for details and keep all controls keyboard accessible.

- [ ] **Step 4: Add drawer and responsive styles**

Add a right-side detail drawer with short-drama metadata, cover image fallback, and an explicit episode empty state. Extend `promotion-link.css` with filter grid, table overflow, drawer metadata grid, and breakpoints at 900px and 760px so no text overlaps at desktop or narrow mobile widths.

- [ ] **Step 5: Run page tests**

Run `pnpm exec vitest run src/pages/promotion/PromotionLinkPage.test.tsx`. Expected result: the new library, filter, empty state, and drawer tests pass.

### Task 3: Connect promotion-task form and link result flow

**Files:**

- Modify: `src/pages/promotion/PromotionLinkPage.tsx`
- Modify: `src/pages/promotion/promotion-link.css`
- Test: `src/pages/promotion/PromotionLinkPage.test.tsx`

- [ ] **Step 1: Add failing generation-flow tests**

Add tests that click “创建推广任务” for a drama, verify the task form opens with the selected title, enter a campaign name, choose an approved media account, submit, and assert the POST body includes `providerId`, `dramaId`, `mediaAccountId`, UUID `requestKey`, trimmed `campaignName`, and `landingType`. Add error coverage for no approved media account and failed generation. Add a success assertion for share URL, external code, tracking number, status tag, copy button, and invalidated history request.

- [ ] **Step 2: Run the focused page tests**

Run `pnpm exec vitest run src/pages/promotion/PromotionLinkPage.test.tsx`. Expected result before implementation: failures for task form and generation assertions.

- [ ] **Step 3: Implement task form behavior**

Move generation controls into a drawer or modal opened from the selected drama row. Keep the existing approved-media-account filtering rule, reset media selection when the drama changes, require drama and media account, trim campaign name, and keep `crypto.randomUUID()` request idempotency. Preserve `DEFAULT` and `ONELINK` radio options.

- [ ] **Step 4: Implement result and history states**

Keep the latest generated result visible beside the library or below it depending on viewport. Show pending, success, and failed states using existing `PromotionLinkStatus`; expose share URL, external code, tracking number, provider, drama, media account, and campaign name. Keep copy action on a familiar icon button with an accessible label and refresh the history query after successful generation.

- [ ] **Step 5: Run focused page tests**

Run `pnpm exec vitest run src/pages/promotion/PromotionLinkPage.test.tsx`. Expected result: generation, failure, copy, and history assertions pass.

### Task 4: Update route-level coverage and project documentation

**Files:**

- Modify: `src/app/AppRouter.test.tsx`
- Modify: `README.md`

- [ ] **Step 1: Add route-level regression coverage**

Assert authenticated users can open `/promotion/links`, see the new “短剧库” heading and “推广链接记录” section, and retain the existing navigation entry. Keep tests isolated with MSW handlers for dramas, media accounts, and links.

- [ ] **Step 2: Update README current-state documentation**

Replace the current one-line promotion-link description with the delivered first-phase behavior: short-drama filtering, detail drawer, approved media-account selection, link/口令 generation, and personal link history. Explicitly keep rankings, orders, downloads, TikTok anchors, and conversion analytics in the future-work section.

- [ ] **Step 3: Run the complete verification set**

Run `pnpm test`, `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, and `pnpm build`. Expected result: zero test failures, zero type errors, zero lint errors, clean formatting, and a successful Vite production build. Run `git diff --check` from the workspace repository if Git metadata is available.

## Self-review

- Spec coverage: library filters, details, task naming, approved media-account selection, generation result, history, error/empty states, responsive layout, and explicit out-of-scope features are covered by Tasks 1–4.
- Placeholder scan: no TODO/TBD or unspecified implementation step is present.
- Type consistency: `PromotionDramaQuery` is defined in `dramaTypes.ts` and consumed by `fetchPublishedPromotionDramas`; page query keys and response types continue using existing `PromotionDramaPage` and `PromotionLinkPage`.
