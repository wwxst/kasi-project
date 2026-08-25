# 短剧默认上架与甲方下架同步实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将短剧目录的新数据默认状态和甲方下架状态同步改为符合运营规则的行为。

**Architecture:** 使用 V18 Java Migration 兼容既有数据库；在现有 `ProviderDramaMapper.upsert` 中保留管理员手动状态，同时仅在甲方下架时把我方已上架记录置为下架。管理端只做状态展示映射。

**Tech Stack:** Spring Boot 4、MyBatis、Flyway Java Migration、JUnit/H2、React/Ant Design Pro、Vitest。

---

### Task 1: 锁定后端迁移行为

**Files:**
- Create: `kasi-backend/src/main/java/db/migration/V18__drama_default_published.java`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/ScheduledTaskMigrationTest.java` 或新增迁移测试

- [ ] 编写 V18 从 V17 数据库迁移后默认值为 `PUBLISHED`、历史 DRAFT 按远端状态转换的失败测试。
- [ ] 运行 `./mvnw.cmd -Dtest=... test` 确认测试先失败。
- [ ] 实现按 MySQL/H2 数据库类型设置默认值和历史数据转换。
- [ ] 重新运行迁移测试，确认通过。

### Task 2: 锁定目录同步状态行为

**Files:**
- Modify: `kasi-backend/src/main/resources/mapper/ProviderDramaMapper.xml`
- Modify: `kasi-backend/src/test/java/com/kasi/backend/drama/mapper/DramaCatalogPersistenceTest.java`

- [ ] 添加新建在线、新建下架、已有上架遇甲方下架、甲方恢复在线不自动上架四个场景。
- [ ] 运行聚焦测试确认缺少同步状态更新时失败。
- [ ] 修改 upsert：新记录按甲方状态决定本地状态；重复记录仅在甲方非在线且我方已上架时置为 OFFLINE。
- [ ] 运行聚焦测试确认通过。

### Task 3: 更新管理端状态展示

**Files:**
- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.tsx`
- Modify: `kasi-admin-web/src/pages/drama/DramaCatalogPage.test.tsx`

- [ ] 添加远端状态映射测试：`1` 显示“在线”，非 `1` 显示“已下架”，空值显示“未知”。
- [ ] 实现映射并保留原始状态可在详情中查看。
- [ ] 运行管理端页面测试。

### Task 4: 完整验证与文档

- [ ] 运行后端完整测试、管理端测试、lint、typecheck、build。
- [ ] 更新根架构文档和后端 README 的当前行为描述。
- [ ] 运行 `git diff --check`，提交并推送根仓库 `master`。
