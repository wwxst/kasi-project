# Kasi User Web

This is the clean React + Vite + TypeScript starting point for the Kasi user frontend.

The previous business pages, API integrations, tests, and product documentation were intentionally removed so the next UI direction can be implemented from a clean baseline.

## Current Layout

The login route is rendered as a full-page Starter-style surface at `/login`. The authenticated workspace shell is available under `/workspace` and uses the migrated TDesign Starter SideLayout structure: a 232px sidebar, sticky header, breadcrumb-aware page container, footer, responsive menu collapse, theme switch, and settings drawer.

The current user menu contains `首页`、`账号报白`、`短剧推广`、`推广任务`、`订单` and `佣金`. These routes currently render a workspace placeholder and do not fabricate business data or replace the existing API contracts. The migrated layout source lives under `src/layout` and keeps the Starter component and Less-module organization, with Redux-specific state replaced by a layout-only React context.

Password login now calls `/api/user/auth/login` and stores the returned access token in the auth-only Zustand store. The workspace Header calls `/api/user/auth/me` with that token and renders the persisted `avatarUrl` and `nickname`; without an avatar it uses a 32px gray circle containing the nickname initial, while a missing profile or failed request keeps the neutral `用户` placeholder.

Workspace routes require an access token and redirect unauthenticated visits to `/login`. The shared Axios client clears the session only for HTTP 401 responses; media-account 401 responses therefore return to the login page without rendering the raw Axios error. Other HTTP and network failures use one shared TDesign `MessagePlugin.error` prompt with user-facing Chinese copy, while page-level handlers remain silent for those already handled Axios errors.

## Commands

```powershell
pnpm dev
pnpm typecheck
pnpm build
```

## 已实现页面

`/workspace/media-accounts` 已作为“账号报白”页面接入用户媒体账号接口，并迁移 TDesign Starter 筛选列表结构：账号报白 Dialog、新增账号、GoodShort 报白状态表格、客户端筛选和分页、加载/空数据/错误状态。

`/workspace/drama` 已接入 `/api/user/promotion/dramas`，沿用账号报白页面的 Starter 筛选列表结构：短剧标题和语言筛选、合并展示海报/中英文名称/标签的短剧信息列、平台/分类/语言/推广说明/发布时间表格以及服务端分页。接口只返回已上架且甲方在线的短剧，并按甲方 `remoteCreatedAt` 发布时间倒序排列。“创建推广任务”操作会携带当前 `dramaId` 和 `providerId` 进入 `/workspace/promotion-links`，由任务流程继续展示剧集并创建口令和链接。
`/workspace/promotion-links` 菜单显示为“推广任务”，默认只读展示已创建的口令、落地页和 OneLink；从短剧推广页携带短剧参数进入时，会打开对应剧集详情和创建推广任务流程。主列表不展示链接状态或重试操作。
