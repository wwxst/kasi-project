# Project Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前后端骨架的真实状态、开发约束、运行方式和后续路线沉淀到仓库文档中。

**Architecture:** `AGENTS.md` 只承载代理执行规则和验证门槛；`DEVELOPMENT.md` 面向开发者，描述当前架构、数据库草案、启动条件、测试结果和优先级。两份文档都明确区分已实现内容与计划内容。

**Tech Stack:** Markdown、Spring Boot 4.0.7、Java 25、Maven Wrapper、MySQL、Flyway、MyBatis。

---

### Task 1: Add Agent Instructions

**Files:**
- Create: `AGENTS.md`

- [x] **Step 1: Record repository scope and current project reality**
  - State the repository root, the current single-module Spring Boot structure, and the fact that business APIs and application layers do not yet exist.

- [x] **Step 2: Record toolchain and validation commands**
  - Require Java 25, Maven Wrapper, `compile`, `test`, and `git diff --check` verification.
  - Preserve the known datasource failure as a current test limitation instead of claiming a passing suite.

- [x] **Step 3: Record database, security, change-safety, and documentation rules**
  - Require Flyway-compatible names, no fixed-schema `CREATE DATABASE/USE` in migrations, secret hygiene, explicit security design, and preservation of unrelated worktree changes.

### Task 2: Add Developer Documentation

**Files:**
- Create: `DEVELOPMENT.md`

- [x] **Step 1: Document the current structure and build toolchain**
  - Describe the source tree, Maven coordinates, Spring Boot/Java versions, and the current one-class application entry point.

- [x] **Step 2: Document configuration, startup prerequisites, and verified test results**
  - Show portable PowerShell environment variables without committing credentials.
  - Record the Java 21 mismatch, Java 25 compile result, and the datasource-related `contextLoads` failure.

- [x] **Step 3: Document database scope and unfinished application boundaries**
  - Describe `sys_user` and `app_user`, Flyway naming, schema risks, the absence of API/security/MyBatis layers, and the next implementation priorities.

### Task 3: Review Documentation Consistency

**Files:**
- Verify: `AGENTS.md`
- Verify: `DEVELOPMENT.md`
- Verify: `git status`, `git diff --check`

- [x] **Step 1: Check cross-references and required facts**
  - Confirm both documents reference the existing entry point, configuration file, migration script, and test file without inventing modules or APIs.

- [x] **Step 2: Check formatting and worktree scope**
  - Run `git diff --check` and confirm only the two requested project documents plus this plan are newly added.
