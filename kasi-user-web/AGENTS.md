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
- 401 清除会话；503/1007 保留会话并提供重试。非 401 的 HTTP/网络错误由共享 Axios 层统一使用 TDesign 消息提示，页面不得重复显示 Axios 原始错误文本。
- 登录成功后前端保存返回的 access token；工作区 Header 只在有 token 时请求 `/api/user/auth/me`，显示持久化的 `avatarUrl` 和 `nickname`，无头像时使用昵称首字灰色圆形头像，用户信息请求失败时保留“用户”占位。

## 开发规则

- 使用 pnpm，不提交 `node_modules`、`dist` 或本地 `.env*`。
- 新行为先写失败测试，再写最小实现。
- 页面只组合 feature；业务 API、类型和状态放在所属 feature 中。
- 快速上线版只使用 `PromotionLink.trackingNo` 归因；“短剧推广”选择短剧并进入推广任务流程，剧集和创建口令/链接操作在带短剧参数的推广任务页完成；推广任务主列表只展示已创建的 `PromotionLink` 数据，不展示未接入的状态或 0 值任务统计。
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
