# Kasi User Web

This is the clean React + Vite + TypeScript starting point for the Kasi user frontend.

The previous business pages, API integrations, tests, and product documentation were intentionally removed so the next UI direction can be implemented from a clean baseline.

## Current Layout

The login route is rendered as a full-page Starter-style surface at `/login`. The login page, browser title, and workspace sidebar use the `卡司短剧推广平台` brand; on screens narrower than 480px, the login header keeps the brand text and hides the full logo image to avoid horizontal crowding. Password and verification-code login are available; WeChat login is currently unavailable and is shown as a non-interactive notice. The authenticated workspace shell is available under `/workspace` and uses the migrated TDesign Starter SideLayout structure: a 232px sidebar, sticky header, breadcrumb-aware page container, footer, and responsive menu collapse. The footer stays at the bottom of the workspace viewport when page content is short. The workspace home page displays a centered welcome message using the signed-in user's nickname, with `用户` as the fallback. The workspace header keeps the message and user menus; help documentation, page settings, and download center entries are not shown.

The current user menu contains `首页`、`账号报白`、`短剧推广`、`推广任务` and `订单`. The order route reads the personal-order contract and shows only the provider order number, payment status/time, tracking number, and the signed-in user's commission snapshot; it does not display the full order amount or internal commission status. There is no standalone commission route. The migrated layout source lives under `src/layout` and keeps the Starter component and Less-module organization, with Redux-specific state replaced by a layout-only React context.

Password login calls `/api/user/auth/login`; mobile verification-code login sends through `/api/user/auth/login/code` and verifies through `/api/user/auth/login/code/verify`. Registration sends a mobile code through `/api/user/auth/register/code` and submits the account, code, password, and matching confirmation to `/api/user/auth/register`. Forgot password uses the existing three-step mobile flow to send a code, exchange it for an in-memory reset token, and reset the password without putting that token in browser storage or the URL. Email remains available for password login, while verification-code flows currently accept mobile numbers only. Successful login stores the returned access token in the auth-only Zustand store. The workspace Header calls `/api/user/auth/me` with that token and renders the persisted `avatarUrl` and `nickname`; without an avatar it uses a 32px gray circle containing the nickname initial, while a missing profile or failed request keeps the neutral `用户` placeholder.

Workspace routes require an access token and redirect unauthenticated visits to `/login`. The shared Axios client clears the session only for HTTP 401 responses; media-account 401 responses therefore return to the login page without rendering the raw Axios error. Other HTTP and network failures use one shared TDesign `MessagePlugin.error` prompt with user-facing Chinese copy, while page-level handlers remain silent for those already handled Axios errors.

## Commands

```powershell
pnpm install --frozen-lockfile
pnpm dev
pnpm check
```

## 已实现页面

`/workspace/media-accounts` 已作为“账号报白”页面接入用户媒体账号接口，并迁移 TDesign Starter 筛选列表结构：账号报白 Dialog、新增账号、GoodShort 报白状态表格、客户端筛选和分页、加载/空数据/错误状态。媒体平台、账号 ID、账号名称和账号主页链接均为必填，主页链接必须使用 HTTPS。

`/workspace/drama` 已接入 `/api/user/promotion/dramas`，沿用账号报白页面的 Starter 筛选列表结构：短剧标题和语言筛选、合并展示海报/中英文名称/标签的短剧信息列、平台/分类/语言/推广说明/发布时间表格以及服务端分页。接口只返回已上架且甲方在线的短剧，并按甲方 `remoteCreatedAt` 发布时间倒序排列。“创建推广任务”操作在当前页面打开所选短剧详情抽屉，抽屉展示封面、标题和简介，不展示推广说明；用户点击“创建链接和口令”后选择落地页或 OneLink，每个媒体平台只生成所选类型的一条链接和一个口令，生成成功后才进入 `/workspace/promotion-links`，生成失败时保留当前创建弹窗。

短剧详情页的剧集下载使用浏览器原生 MP4 下载。单集下载直接保存当前免费剧集；“下载全部”按集数依次触发当前短剧全部可用免费剧集的多个 MP4 文件，不创建后端下载任务、不使用 FFmpeg，也不生成 ZIP。浏览器下载进度由浏览器自身管理。

`/workspace/promotion-links` 菜单显示为“推广任务”，按单条推广记录只读展示创建时间、推广名称、短剧、链接类型、口令和分享链接，不显示批次，也不读取短剧查询参数或承载创建流程。主列表不展示链接状态或重试操作。

`/workspace/orders` 已接入 `/api/user/promotion/orders`，按月份分页展示当前登录用户已归因订单的甲方订单号、未支付/已支付/已退款状态、支付时间、推广跟踪号和“我的收益”。页面不提供订单导出，不展示完整订单金额或内部佣金状态，只展示后端收益结果，不在前端重算佣金；当前不提供独立佣金页面。

`/workspace/profile` 通过工作区 Header 的头像菜单进入，不显示在侧边栏。页面使用 `/api/user/auth/me` 展示本人资料；进入编辑态后，昵称和真实姓名直接在原展示位置切换为输入框，并通过 `/api/user/auth/profile` 保存；头像通过 `/api/user/auth/avatar` 上传 JPG/PNG/WebP 文件。资料或头像更新成功后同步刷新 Header 共用的用户缓存。用户编号、手机号、邮箱、注册时间和最近登录信息保持只读。密码仍通过 `/api/user/auth/password` 修改，成功后清除本地会话并返回登录页重新登录。

Node.js 和 pnpm 版本以 `package.json` 的 `engines` 与 `packageManager` 为准。`pnpm check` 包含 lint、format、test 和包含 TypeScript 编译的 build；生成的 `pnpm-lock.yaml` 不进入 Prettier 扫描，frozen install 仍校验其一致性。

# 手机验证码

登录页已接通手机号注册验证码、验证码登录和找回密码验证码调用；验证码发送成功后才开始 60 秒重发倒计时。邮箱密码登录保持可用，邮箱验证码暂缓。
