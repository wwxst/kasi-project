# Repository Development Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将问题修复、阶段拆解、最小变更和架构决策沉淀为仓库级强制开发流程，并记录当前尚未规范的工程事项。

**Architecture:** `AGENTS.md` 负责执行时必须遵守的短规则；`DEVELOPMENT.md` 负责完整开发流程和变更分级；`docs/architecture-decisions.md` 负责重要架构决策的记录契约；`docs/development-gaps.md` 负责当前缺口清单；`README.md` 只提供入口和文档索引。业务代码和现有未提交功能不在本计划范围内。

**Tech Stack:** Markdown、Git、Maven Wrapper（仅用于确认本次没有代码行为回归）。

---

### Task 1: Add the mandatory development workflow

**Files:**
- Modify: `AGENTS.md`
- Create: `DEVELOPMENT.md`

- [ ] **Step 1: Add the workflow rules to `AGENTS.md`**
  Add a concise mandatory checklist covering change classification, root-cause analysis, one-problem-per-stage, approval gates, minimal changes, verification, and documentation synchronization.

- [ ] **Step 2: Write `DEVELOPMENT.md`**
  Document the same workflow in detail, including required evidence, stop/report conditions, destructive-refactor rules, and the completion checklist.

- [ ] **Step 3: Verify links and terminology**
  Ensure `AGENTS.md` links to `DEVELOPMENT.md` and distinguishes current behavior from planned work.

### Task 2: Record architecture decisions consistently

**Files:**
- Create: `docs/architecture-decisions.md`

- [ ] **Step 1: Define the decision record format**
  Specify required fields: ID, date, status, scope, context, decision, alternatives, consequences, migration/rollback, validation, and superseded records.

- [ ] **Step 2: Define status and boundary rules**
  Require explicit `提议/已批准/已实施/已废弃` status and prohibit describing planned decisions as current implementation.

### Task 3: List current governance gaps

**Files:**
- Create: `docs/development-gaps.md`

- [ ] **Step 1: Categorize open gaps**
  Group gaps under process, API/data contracts, security/operations, async integrations, testing, and documentation/tooling.

- [ ] **Step 2: Assign priority and next action**
  Use `P0/P1/P2` and state whether each item needs an ADR, implementation task, or policy decision before coding.

### Task 4: Add the documentation entry point

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add a development governance section**
  Link the workflow, ADR rules, and gap list without duplicating their full contents.

- [ ] **Step 2: Run documentation checks**
  Run `git diff --check` and verify all new relative links resolve.

