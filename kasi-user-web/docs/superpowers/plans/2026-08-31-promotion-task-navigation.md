# 推广任务跳转时机 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户只在链接和口令生成成功后进入推广任务页。

**Architecture:** 将现有创建表单和提交编排从 `PromotionLinksPage` 移到 `DramaPage` 的短剧详情流程中。`DramaPage` 在创建接口成功后使推广任务查询失效并导航，`PromotionLinksPage` 退回为只读任务列表。

**Tech Stack:** React 19、TypeScript、React Router、TanStack Query、TDesign React、Vitest、Testing Library

---

### Task 1: 用失败测试固定导航时机

**Files:**

- Modify: `src/pages/drama/DramaPage.test.tsx`
- Modify: `src/pages/promotionLinks/PromotionLinksPage.test.tsx`

- [ ] **Step 1: 修改短剧页测试表达目标行为**

将原先“点击创建推广任务立即导航”的断言改为：首次点击后 `navigateMock` 未调用且出现“创建链接和口令”；选择 `TikTok` 并提交成功后，断言 `createPromotionLinks` 收到当前 `providerId`、`dramaId`、平台和推广名称，随后导航到 `/workspace/promotion-links`。

- [ ] **Step 2: 增加失败路径断言**

让 `createPromotionLinks` 拒绝请求，提交后断言仍停留在当前页面、弹窗仍可见且 `navigateMock` 未调用。

- [ ] **Step 3: 固定任务页只读边界**

使用 `/workspace/promotion-links?dramaId=7&providerId=2` 渲染任务页，继续断言页面不存在“创建链接和口令”按钮，证明查询参数不再触发创建流程。

- [ ] **Step 4: 运行聚焦测试并确认 RED**

Run: `pnpm exec vitest run src/pages/drama/DramaPage.test.tsx src/pages/promotionLinks/PromotionLinksPage.test.tsx`

Expected: FAIL，短剧页仍提前导航，任务页仍会根据查询参数渲染创建流程。

### Task 2: 将创建流程移到短剧页

**Files:**

- Modify: `src/pages/drama/DramaPage.tsx`
- Modify: `src/pages/drama/DramaPage.module.less`

- [ ] **Step 1: 将入口留在当前页面**

把“创建推广任务”按钮的处理改为选中当前短剧并打开现有详情 Dialog，不调用 `navigate`。在详情标题操作区增加“创建链接和口令”按钮。

- [ ] **Step 2: 迁移现有创建表单**

在 `DramaPage` 增加媒体平台多选、推广名称、提交 loading 和 Dialog 可见状态；继续调用现有 `createPromotionLinks`，请求字段保持 `providerId`、`dramaId`、`mediaTypes`、`campaignName`，`requestKey` 仍由 API 层生成。

- [ ] **Step 3: 只在成功后导航**

成功时依次关闭创建 Dialog、重置表单、使 `['user', 'promotion-links']` 查询失效，并执行 `navigate('/workspace/promotion-links')`。失败时沿用共享 Axios 已处理错误判断，显示现有用户提示且不关闭、不导航。

- [ ] **Step 4: 补充最小样式**

为详情标题的操作按钮和创建表单增加现有 TDesign 变量下的间距，不调整页面整体布局。

- [ ] **Step 5: 运行聚焦测试并确认短剧页 GREEN**

Run: `pnpm exec vitest run src/pages/drama/DramaPage.test.tsx`

Expected: PASS。

### Task 3: 精简推广任务页并同步文档

**Files:**

- Modify: `src/pages/promotionLinks/PromotionLinksPage.tsx`
- Modify: `src/pages/promotionLinks/PromotionLinksPage.module.less`
- Modify: `README.md`

- [ ] **Step 1: 删除任务页创建职责**

移除查询参数、短剧详情查询、详情 Drawer、创建 Dialog、播放 Dialog和相关状态及辅助组件；保留推广任务查询、复制和按单条记录展示的表格，不显示批次。

- [ ] **Step 2: 删除无消费者样式**

从 `PromotionLinksPage.module.less` 删除详情、创建表单和视频播放样式，只保留任务列表和链接单元格样式。

- [ ] **Step 3: 更新当前行为文档**

调整 `README.md` 和 `AGENTS.md` 的短剧推广和推广任务说明，明确生成前留在短剧页，成功后进入不显示批次的只读推广任务列表。

- [ ] **Step 4: 运行聚焦测试并确认全部 GREEN**

Run: `pnpm exec vitest run src/pages/drama/DramaPage.test.tsx src/pages/promotionLinks/PromotionLinksPage.test.tsx`

Expected: PASS。

### Task 4: 完整验证

**Files:**

- Verify only

- [ ] **Step 1: 运行完整测试**

Run: `pnpm test`

Expected: PASS，零失败。

- [ ] **Step 2: 运行静态检查与构建**

Run: `pnpm typecheck`

Expected: exit code 0。

Run: `pnpm lint`

Expected: exit code 0。

Run: `pnpm format:check`

Expected: exit code 0。

Run: `pnpm build`

Expected: exit code 0。

- [ ] **Step 3: 检查差异**

Run: `git diff --check`

Expected: exit code 0；最终 diff 仅包含本计划文件、设计文档、短剧页、推广任务页、对应测试和 README，并保留用户已有布局改动。
