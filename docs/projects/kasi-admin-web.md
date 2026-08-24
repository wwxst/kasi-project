# kasi-admin-web

React 19 + TypeScript + Vite + Ant Design Pro 管理端。`src/api` 对接后端，`src/features` 按领域组织，`src/pages` 提供管理页面，测试使用 Vitest/Testing Library/MSW。

当前页面覆盖认证、管理员/推广用户管理、平台接入、媒体账号报备、短剧目录、平台 CPS 费率、推广订单和系统定时任务。页面不能把尚未实现的自动同步、钱包或结算能力伪装成真实数据。

```powershell
cd kasi-admin-web
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm typecheck
pnpm format:check
pnpm build
```
