# kasi-admin-web

React 19 + TypeScript + Vite + Ant Design Pro 管理端。`src/api` 对接后端，`src/features` 按领域组织，`src/pages` 组合管理页面；Node/pnpm 版本以 `package.json` 为机器真相。

当前页面覆盖认证、管理员/推广用户管理、平台接入、媒体账号报备、短剧目录与同步、CPS 费率、推广订单和系统定时任务。页面不得把钱包、结算或其他未实现能力伪装成真实数据。

```powershell
cd kasi-admin-web
pnpm install --frozen-lockfile
pnpm check
```

`pnpm check` 包含 lint、format、test 和 build。Vitest 当前使用 `maxWorkers=2` 以保持本仓库测试稳定；该值是当前工程配置，不是永久架构规则。
