# 短剧 API 配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将短剧平台表格页改为系统配置下的统一 API 配置表单，并让 URL、PID、KEY 真正驱动后端连接测试。

**Architecture:** 后端通过 V3 为平台接入配置增加 `base_url`，统一管理接口保存 URL、PID、加密 KEY 和状态；GoodShort 适配器从连接配置取得基础 URL。前端使用 Ant Design `Menu + Tabs + Form`，不再使用 ProTable。

**Tech Stack:** Spring Boot 4、MyBatis、Flyway、MySQL/H2、React 19、TypeScript、Ant Design 6、Vitest、MSW。

---

### Task 1: 后端 URL 配置

- [x] 新增 `V3__provider_connection_base_url.sql`。
- [x] 同步 Entity、Mapper、DTO、VO 和测试 schema。
- [x] 配置请求简化为 URL、PID、KEY、状态，接入名称和币种由后端使用兼容默认值。
- [x] GoodShort 连接测试使用数据库中的 `base_url`。
- [x] 定向运行迁移、Mapper、Service、适配器和 Controller 测试。

### Task 2: 前端设置页

- [x] 菜单改为“系统配置 → 短剧 API 配置”。
- [x] 新路由使用 `/system-config/drama-api`，保留旧地址跳转。
- [x] 页面改为平台 Tabs 与 URL、PID、KEY、启用状态表单。
- [x] 普通管理员只读，超级管理员可提交和连接测试。
- [x] 更新 API 类型和 MSW 测试。

### Task 3: 验证与文档

- [x] 运行后端完整测试和 Java 25 编译（185 tests，0 failures，0 errors）。
- [x] 运行前端完整测试、类型检查、lint、格式检查和构建（16 tests passed）。
- [x] 更新 README、AGENTS 和当前设计文档。
- [x] 在桌面与移动视口验证菜单、Tabs、表单和底部按钮。

## 验收记录

- 2026-08-18：在本地登录管理后台后验证 `/system-config/drama-api`，确认左侧菜单为“系统配置 → 短剧 API 配置”，页面使用 GoodShort Tabs 与 URL、PID、KEY、启用状态表单。
- 2026-08-18：桌面端与 390px 移动端均无横向溢出；普通管理员只读状态和超级管理员操作按钮显示符合权限设计。
