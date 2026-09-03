# kasi-user-web

React 19 + TypeScript + Vite + TDesign React 推广用户端。Node/pnpm 版本以 `package.json` 为机器真相；当前业务范围以子项目 README 和 scoped `AGENTS.md` 为准。

用户端只展示后端已确认的个人短剧、推广链接、订单和佣金快照，不在前端重算费率，也不把月度结果描述为已付款账单。

个人中心通过工作区 Header 头像菜单进入，使用现有用户认证接口只读展示本人资料并修改密码；修改成功后旧会话失效，用户返回登录页重新登录。当前不提供本人资料编辑能力。

```powershell
cd kasi-user-web
pnpm install --frozen-lockfile
pnpm check
```

`pnpm check` 包含 lint、format、test 和 build。`pnpm-lock.yaml` 是生成文件并排除出 Prettier；frozen install 仍负责校验 package manifest 与 lockfile 是否一致。
