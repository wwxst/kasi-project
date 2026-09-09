# kasi-user-web

React 19 + TypeScript + Vite + TDesign React 推广用户端。Node/pnpm 版本以 `package.json` 为机器真相；当前业务范围以子项目 README 和 scoped `AGENTS.md` 为准。

用户端只展示后端已确认的个人短剧、推广链接、订单和佣金快照，不在前端重算费率，也不把月度结果描述为已付款账单。

个人中心通过工作区 Header 头像菜单进入，使用 `/api/user/auth/me` 展示本人资料。用户名下方只读显示学员类型（`0=基础用户`、`1=基础学员`），不属于编辑资料字段。基本信息支持编辑昵称、真实姓名、微信号、手机号码和电子邮箱，并通过 `/api/user/auth/profile` 保存；手机号或邮箱变更会使旧会话失效，需要重新登录。页面不展示实名认证入口，基本信息和安全设置使用椭圆形白色切换按钮，安全设置内容不再嵌套卡片。头像无蓝色外圈。密码修改成功后同样会使旧会话失效并返回登录页。

```powershell
cd kasi-user-web
pnpm install --frozen-lockfile
pnpm check
```

`pnpm check` 包含 lint、format、test 和 build。`pnpm-lock.yaml` 是生成文件并排除出 Prettier；frozen install 仍负责校验 package manifest 与 lockfile 是否一致。
