# kasi-admin-web

React 19 + TypeScript + Vite + Ant Design Pro 管理端。`src/api` 对接后端，`src/features` 按领域组织，`src/pages` 组合管理页面；Node/pnpm 版本以 `package.json` 为机器真相。

当前页面覆盖认证、管理员/推广用户管理、平台接入、媒体账号报备、短剧目录与同步、CPS 费率、推广订单和系统定时任务。页面不得把钱包、结算或其他未实现能力伪装成真实数据。

管理端导航按“管理员管理、用户管理、项目管理、短剧管理、系统设置”组织。短剧管理包含短剧目录、账号报备、推广任务、推广订单和同步管理；同步管理包含短剧同步与剧集同步。管理员管理和系统设置中的系统配置仅超级管理员可见，其余系统设置入口按现有权限开放。菜单名称调整不改变既有路由地址和后端接口。用户管理列表、详情、新建和编辑表单支持微信号与学员类型，学员类型为 `0=基础用户`、`1=基础学员`。

```powershell
cd kasi-admin-web
pnpm install --frozen-lockfile
pnpm check
```

`pnpm check` 包含 lint、format、test 和 build。Vitest 当前使用 `maxWorkers=2` 以保持本仓库测试稳定；该值是当前工程配置，不是永久架构规则。
