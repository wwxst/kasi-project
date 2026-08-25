# 当前架构

## 系统边界

```text
管理员浏览器 -> kasi-admin-web -> kasi-backend -> MySQL / Redis / GoodShort
推广用户浏览器 -> kasi-user-web -> kasi-backend -> MySQL / Redis / GoodShort
```

后端根包为 `com.kasi.backend`，按 `admin`、`user`、`auth`、`security`、`provider`、`promotion`、`drama`、`common` 分域。HTTP 入口在 Controller，业务编排在 Service/impl，持久化在 Mapper，DTO 与 VO 分离。

## 已实现主线

- ADMIN/USER 双认证、JWT、Redis 会话版本和单会话校验。
- GoodShort 平台接入与 AES-GCM 凭据保护。
- 媒体账号绑定、GoodShort 报备任务、审核查询和失败重试。
- 短剧目录全量/增量同步、断点续跑、租约和本地上下架；甲方在线的新短剧默认上架，甲方下架会同步我方下架，甲方恢复后需管理员手动重新上架。
- 平台级 CPS 费率及历史快照。
- 推广链接生成、订单每分钟自动同步最近 3 天（保留管理员手动补拉）、trackingNo 归因、订单佣金快照和月度 CSV 查询/导出。

## 非目标和未完成

CapCut 人工数据导入、CPA/CPM 项目化结算、钱包/提现、自动对账和自动月结不属于当前实现。它们只能以独立领域设计进入后续阶段，不得通过项目名称判断业务类型，也不得改写已上线 CPS 结果。

## 数据原则

上游订单原始 payload、归因字段、费率快照和佣金结果分开保存；历史快照不可因新费率修改而变化。账号体系只维护当前项目需要的独立账号，不建立 CapCut 与 TikTok 绑定关系。
