# 平台分佣规则配置页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在现有“短剧 API 配置”平台 Tab 内增加平台级分佣规则查看与维护，不新增菜单或路由。

**Architecture:** 复用 `ProviderManagementPage` 的平台切换和管理员权限状态。新增独立的 `commissionRuleApi.ts` 与类型，页面在现有 API 表单下渲染规则区块；规则新增/编辑使用局部 Modal 表单，状态操作调用后端现有五个接口。API 配置保存和规则操作保持独立，业务错误统一交给现有 HTTP 客户端和页面提示。

**Tech Stack:** React 19、TypeScript、Ant Design、Lucide React、Axios、MSW、Vitest、React Testing Library。

---

### Task 1: Add Commission Rule API Types And Request Layer

**Files:**

- Create: `src/features/provider/commissionRuleTypes.ts`
- Create: `src/features/provider/commissionRuleApi.ts`
- Modify: `src/features/provider/providerApi.test.ts`

- [ ] **Step 1: Write failing API tests**

Add MSW tests for `listCommissionRules`, `createCommissionRule`, `updateCommissionRule`, `endCommissionRule`, and `deleteCommissionRule`. Assert the exact URL, HTTP method, JSON body, and unwrapped response.

- [ ] **Step 2: Run the API test and verify red**

Run `pnpm vitest run src/features/provider/providerApi.test.ts`.

Expected: TypeScript/test failure because the commission rule functions and types do not exist.

- [ ] **Step 3: Implement the minimal request layer**

Define `CommissionRuleStatus = 'PENDING' | 'ACTIVE' | 'ENDED'`, a `CommissionRule` response with five numeric percentage fields and ISO timestamps, create/update request types, and functions targeting:

```text
GET    /api/admin/drama/providers/{providerId}/commission-rules
POST   /api/admin/drama/providers/{providerId}/commission-rules
PUT    /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}
PATCH  /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}/end-time
DELETE /api/admin/drama/providers/{providerId}/commission-rules/{ruleId}
```

Use the existing `httpClient` and `unwrapApiResponse` pattern. Keep the frontend contract in percentages; the backend response already exposes percentages.

- [ ] **Step 4: Run the API tests and verify green**

Run `pnpm vitest run src/features/provider/providerApi.test.ts`.

Expected: all existing provider API tests and the five new commission API tests pass.

- [ ] **Step 5: Commit**

Run `git add src/features/provider/commissionRuleTypes.ts src/features/provider/commissionRuleApi.ts src/features/provider/providerApi.test.ts && git commit -m "feat: add commission rule api client"`.

### Task 2: Add The Commission Rule Section To Provider Configuration

**Files:**

- Modify: `src/pages/provider/ProviderManagementPage.tsx`
- Modify: `src/pages/provider/provider-management-page.css`

- [ ] **Step 1: Add page-level failing behavior tests**

Extend `src/pages/provider/ProviderManagementPage.test.tsx` with MSW responses and assertions for:

- the selected provider loads and renders its rule table below the API form;
- switching provider tabs reloads rules for the new provider;
- an ordinary admin sees rules but no rule write buttons;
- a super admin sees “新增规则” and row actions based on `PENDING`, `ACTIVE`, and `ENDED` states;
- the create modal submits all five rates and timestamps to the POST endpoint;
- editing a pending rule submits PUT, ending an active rule submits PATCH, and deleting a pending rule submits DELETE;
- API errors from a rule request show the existing Ant Design message error.

- [ ] **Step 2: Run the page tests and verify red**

Run `pnpm vitest run src/pages/provider/ProviderManagementPage.test.tsx`.

Expected: failures because the page does not yet load or render commission rules.

- [ ] **Step 3: Implement rule loading and platform switching**

Add `commissionRules` and `rulesLoading` state. Load rules whenever `activeProviderId` changes, clear stale rules before the new request, and surface request errors through `App.useApp().message`. Keep the existing provider load and API form behavior unchanged.

- [ ] **Step 4: Implement the rules table**

Render a titled “分佣规则” section below the API form. Use an Ant Design `Table` with stable columns for five percentage fields, effective interval, and derived status. Use compact `Tag` colors for `PENDING`, `ACTIVE`, and `ENDED`. Render only actions valid for the current status and only when `isSuperAdmin` is true.

- [ ] **Step 5: Implement create and edit modal forms**

Use one local Ant Design `Modal` and `Form` for create/edit. Validate all five rates as required numbers in `0..100` with at most four decimal places, require `effectiveFrom`, and allow an optional `effectiveTo`. For edit, disable changes to the active/ended rule by not opening the modal for those statuses. After success, close the modal and reload the current provider rules.

- [ ] **Step 6: Implement end and delete confirmations**

Use `Modal.confirm` for active-rule early end and pending-rule deletion. The end form must require an end timestamp later than the current time; call PATCH with `{ effectiveTo }`. Call DELETE for pending rules only. Reload the rule list after either operation.

- [ ] **Step 7: Add responsive styling**

Add a rule section divider, toolbar, horizontal overflow for the dense table, fixed action column width, and mobile stacking that matches the existing provider configuration page. Keep the rules section in the same outer panel and do not add nested decorative cards.

- [ ] **Step 8: Run page tests and verify green**

Run `pnpm vitest run src/pages/provider/ProviderManagementPage.test.tsx`.

Expected: all existing provider configuration tests and the new commission rule interaction tests pass.

- [ ] **Step 9: Commit**

Run `git add src/pages/provider/ProviderManagementPage.tsx src/pages/provider/provider-management-page.css src/pages/provider/ProviderManagementPage.test.tsx && git commit -m "feat: manage commission rules in provider config"`.

### Task 3: Verify The Frontend Feature

**Files:**

- No source changes expected unless verification exposes a defect.

- [ ] **Step 1: Run focused API and page tests**

Run `pnpm vitest run src/features/provider/providerApi.test.ts src/pages/provider/ProviderManagementPage.test.tsx` and require zero failures.

- [ ] **Step 2: Run the full frontend test suite**

Run `pnpm test` and record the exact test count and zero failures.

- [ ] **Step 3: Run static and production checks**

Run `pnpm typecheck`, `pnpm lint`, `pnpm format:check`, and `pnpm build`. Each command must exit 0.

- [ ] **Step 4: Review scope and working tree**

Run `git diff --check`, `git status --short --branch`, and `git diff --stat`. Confirm no new route/menu was added and no user-facing commission/order workflow was implemented outside the provider configuration page.

- [ ] **Step 5: Commit any verification-only documentation update**

Only if test counts or verified behavior changed, update the design spec status and evidence, then commit with `docs: record commission rule frontend verification`. Otherwise leave the implementation commits unchanged.
