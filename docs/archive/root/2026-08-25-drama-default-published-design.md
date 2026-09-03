# 短剧默认上架与甲方下架同步设计

- 日期：2026-08-25
- 状态：已实施

## 目标

GoodShort 目录同步后，甲方在线的短剧默认在我方上架；甲方下架时同步将我方短剧下架。甲方重新上架后不自动恢复我方状态，由管理员确认后手动上架。

## 状态边界

- `remote_show_status`：甲方返回的原始状态，`1` 表示当前可用。
- `local_status`：我方运营状态，仍保留 `DRAFT/PUBLISHED/OFFLINE` 兼容旧数据。
- 用户端可推广条件保持为 `local_status=PUBLISHED AND remote_show_status=1`。

## 同步规则

- 新记录：甲方状态为 `1` 时写入 `PUBLISHED`，否则写入 `OFFLINE`。
- 已有记录：甲方变为非 `1` 时，仅把我方 `PUBLISHED` 改为 `OFFLINE`；已有 `OFFLINE` 或 `DRAFT` 不被覆盖。
- 甲方重新变为 `1` 时不自动修改我方状态。
- 管理员现有 `PATCH /api/admin/drama/catalog/{id}/status` 继续负责手动上架/下架。

## 迁移

新增 V18 Java Migration：将 `provider_drama.local_status` 默认值改为 `PUBLISHED`，并把历史导入产生的 `DRAFT` 按甲方状态转换为 `PUBLISHED/OFFLINE`。Java Migration 按数据库类型执行兼容的默认值修改，避免 MySQL 与 H2 SQL 方言分叉。

## 管理端

目录表将远端原始值映射为“在线/已下架/未知”，本地状态继续显示“已上架/已下架/草稿”。不新增用户端接口，不改变订单或推广链接归因。

## 验证

- 迁移测试覆盖 V18 默认值和历史 DRAFT 转换。
- 同步持久化测试覆盖新建在线/下架、已有上架遇甲方下架、甲方恢复在线不自动上架。
- 管理端页面测试覆盖远端状态中文展示。
