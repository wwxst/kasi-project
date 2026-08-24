# Agent 指南

## 范围

本文件适用于 `E:/JavaProjects/kasi-project/kasi-user-web`。修改前先阅读
`README.md` 和 `docs/superpowers/specs/` 中的当前设计。

## 当前边界

- 项目是 React 19 + TypeScript strict + Vite 的独立用户端 SPA。
- UI 组件统一使用 TDesign React；图标统一使用 TDesign Icons React。
- 用户端只调用 `/api/user/**`：当前包括认证、本人媒体账号/报白、可推广短剧、推广链接和本人订单/佣金；不得调用管理员接口，也不得查询其他用户订单。
- 用户资料当前只读；不要在没有后端接口契约时增加编辑入口。
- Zustand 只管理 Token、过期时间和启动状态；服务端用户资料属于
  TanStack Query 缓存。
- HTTP 200 内仍可能通过 `code != 0` 表示业务失败，所有 API 必须经过
  `shared/api/httpClient.ts`。
- 401 清除会话；503/1007 保留会话并提供重试。

## 开发规则

- 使用 pnpm，不提交 `node_modules`、`dist` 或本地 `.env*`。
- 新行为先写失败测试，再写最小实现。
- 页面只组合 feature；业务 API、类型和状态放在所属 feature 中。
- 快速上线版只使用 `PromotionLink.trackingNo` 归因；`/promotion/tasks` 兼容跳转到 `/promotion/links`，不得展示未接入的 0 值任务统计。
- 本人佣金页按 `paidAt` 月份查询后端已归因订单；前端不重算分佣、不修改费率快照，也不把月度汇总描述为已付款账单。
- 保持表单可访问性：可见标签必须与真实输入控件关联。
- 桌面端为主，同时确保 320px 以上窄屏不发生内容重叠或裁切。
- 不提交密钥、真实手机号、真实邮箱或生产环境凭据。

## 完成校验

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
```

涉及页面布局时还需在桌面和移动视口中完成浏览器截图检查。
