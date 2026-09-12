# Media Account Filing Bulk Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add current-page checkbox selection and bulk approve/reject actions for manual media-account filings in the admin web application.

**Architecture:** Keep the feature inside the existing media-account filing page and reuse `updateManualMediaFilingStatus` for each selected row. Selection is controlled by account ID, disabled for non-manual or provider-less rows, cleared whenever the table request changes, and reduced to failed rows after a partially successful batch.

**Tech Stack:** React 19, TypeScript, Ant Design ProTable/Modal/Message, Vitest, React Testing Library, MSW

---

No commit steps are included because the repository instructions prohibit commits without explicit user authorization.

### Task 1: Add failing page-level behavior tests

**Files:**
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`
- Test: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`

- [x] **Step 1: Extend the ProTable test double to exercise row selection**

Accept `rowKey` and `rowSelection`, render one checkbox per row, apply `rowSelection.getCheckboxProps(row).disabled`, and call `rowSelection.onChange(nextKeys, nextRows)` after each checkbox change. Add a small “reload table” button that invokes a captured `actionRef.current.reload` implementation so refresh behavior remains observable.

- [x] **Step 2: Add the RED test for eligibility and disabled toolbar state**

Serve manual rows, an API row, and a provider-less row. Assert that `批量通过` and `批量未通过` start disabled, manual row checkboxes are enabled, and the other two checkboxes are disabled.

- [x] **Step 3: Add the RED test for successful bulk approval**

Select an already-approved manual row and two actionable manual rows, click `批量通过`, confirm the modal, and assert that only actionable `(accountId, providerId)` endpoints receive `{ status: 'APPROVED' }`. Assert the success message reports two records and selection is cleared after reload.

- [x] **Step 4: Add the RED test for partial bulk rejection**

Configure one status endpoint to return success and one to return an API error. Select both rows, confirm `批量未通过`, assert both requests were attempted, the result message reports one success and one failure, and only the failed row remains selected.

- [x] **Step 5: Run the focused test and verify the expected failure**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/pages/promotion/MediaAccountFilingPage.test.tsx
```

Expected: exit code 1 because the page does not yet expose row selection or bulk action buttons. A compile or test-double error must be corrected until the assertion fails for the missing production behavior.

### Task 2: Implement controlled bulk status updates

**Files:**
- Modify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx`
- Test: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`

- [x] **Step 1: Add selection and operation state**

Add `selectedRowKeys: React.Key[]`, `selectedRows: AdminMediaAccountListItem[]`, and `bulkOperating: boolean`. Keep the selected records so each request has both `id` and `providerId` without an extra detail fetch.

- [x] **Step 2: Clear selection when table input changes**

At the start of `loadPage`, clear both controlled selection states. This covers initial load, pagination, reload, and changed filters while keeping the existing query/export behavior intact.

- [x] **Step 3: Add the batch execution handler**

Create a handler accepting `APPROVED | REJECTED`. Filter out rows already at the target and rows that are no longer eligible. If no actionable rows remain, show `所选记录已是目标状态` and return. Otherwise open `modal.confirm` with the target label and actionable count.

In `onOk`, set `bulkOperating`, call all actionable updates with `Promise.allSettled`, refresh the table once, then:

- on full success, clear selection and call `message.success('已批量更新 N 条记录')`;
- on partial or full failure, retain only failed row IDs/records and call `message.error('批量更新完成：成功 N 条，失败 M 条')`;
- suppress a second page error when every failure is an unauthorized error;
- always reset `bulkOperating` in `finally`.

- [x] **Step 4: Wire toolbar buttons and row selection**

Add `rowSelection` to `ProTable` with controlled keys, `onChange`, and `getCheckboxProps` returning disabled unless `filingMethod === 'MANUAL' && providerId !== null`. Render `批量通过` and `批量未通过` before the existing export button. Disable them when nothing is selected or a batch is operating; show loading only on the button for the active target status.

- [x] **Step 5: Run the focused page test and verify GREEN**

Run:

```powershell
cd kasi-admin-web
pnpm test -- src/pages/promotion/MediaAccountFilingPage.test.tsx
```

Expected: exit code 0 and all tests in `MediaAccountFilingPage.test.tsx` pass.

### Task 3: Verify scope and canonical gates

**Files:**
- Verify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx`
- Verify: `kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx`
- Verify: `docs/superpowers/specs/2026-09-12-media-account-filing-bulk-status-design.md`
- Verify: `docs/superpowers/plans/2026-09-12-media-account-filing-bulk-status.md`

- [x] **Step 1: Run the complete admin-web gate**

Run:

```powershell
cd kasi-admin-web
pnpm check
```

Expected: exit code 0 for lint, Prettier check, all Vitest tests, TypeScript compilation, and Vite build.

- [x] **Step 2: Check whitespace and patch integrity**

Run from the repository root:

```powershell
git diff --check
```

Expected: exit code 0. Existing line-ending warnings may be reported separately but must not be hidden.

- [x] **Step 3: Review the final scope**

Run:

```powershell
git status --short
git diff -- kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.tsx kasi-admin-web/src/pages/promotion/MediaAccountFilingPage.test.tsx docs/superpowers/specs/2026-09-12-media-account-filing-bulk-status-design.md docs/superpowers/plans/2026-09-12-media-account-filing-bulk-status.md
```

Expected: the task changes only the two admin page files plus the two new design/plan documents, while all pre-existing user changes remain untouched.
