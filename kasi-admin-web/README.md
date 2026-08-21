# Kasi 管理后台

Kasi 推广平台的独立管理端前端，后端项目位于同级目录 `../kasi-backend`。

## 技术栈

- React 19 + TypeScript + Vite
- Ant Design + Ant Design Pro Components
- Ant Design Plots
- Axios
- React Router
- TanStack Query
- Zustand
- Lucide React + Day.js
- Vitest + React Testing Library + MSW
- Oxlint + Prettier
- pnpm

## 当前状态

当前已完成前端基础工程、管理员登录入口、登录 API 对接、会话保存、受保护路由和管理后台布局。后台壳层包含固定顶栏、导航搜索、通知入口、全屏控制、账户菜单、可折叠桌面侧栏、移动端导航抽屉和面包屑。

首页采用 Ant Design Pro Analysis 分析页结构，包含指标卡、销售趋势、门店排名、热门搜索、销售额类别占比和门店趋势。当前统计值沿用官方演示数据，不代表后端真实业务数据；接入统计 API 后只需替换 `dashboardData.ts` 数据源。个人主页展示当前已认证管理员的真实资料。

管理员管理 `/admin-management` 和推广用户管理 `/user-management` 直接使用 Ant Design Pro 官方 `PageContainer`、`ProTable` 组件，并接入真实后端接口。两个页面支持关键词查询、分页、新建、分组详情抽屉、详情内编辑和物理删除，不展示表格编辑与重置密码入口，也不使用状态开关。详情按“基本信息”和“账号资料”分组展示，并在顶部展示当前详情记录的 64px 头像、名称和账号标识。管理员头像不出现在新增或编辑表单中，只能点击详情顶部头像选择 JPG/PNG/WebP 文件，在 1:1 裁剪后上传，文件不得超过 2 MB；本人头像走 `/api/admin/auth/avatar` 并同步顶部导航状态，其他管理员头像走 `/api/admin/management/{id}/avatar`。管理员详情内提供“编辑”和“修改密码”：打开谁的详情就操作谁；本人资料走 `/api/admin/auth/profile`，本人改密走 `/api/admin/auth/password` 且成功后返回登录页，其他管理员继续使用 `/api/admin/management/{id}` 与 `/api/admin/management/{id}/password`。本人改密和他人重置密码都只填写新密码与确认密码，不要求原密码。推广用户详情只展示头像，不提供修改密码入口。管理员表格固定展示姓名、手机号、邮箱、角色、登录时间、状态和“详情｜删除”；用户表格固定展示用户 ID、昵称、手机号、邮箱、注册来源、状态和“详情｜更多”，“更多”菜单包含启用/禁用和删除。管理员管理仅超级管理员可见和访问，推广用户管理对全部管理员开放；唯一超级管理员仍禁止删除，但允许通过本人认证接口编辑资料、上传头像和修改密码，后端权限规则继续兜底。

左侧“系统配置”一级菜单下提供“短剧 API 配置”二级菜单，页面路由为 `/system-config/drama-api`，旧 `/provider-management` 地址自动跳转到新路由。页面使用 Ant Design `Tabs + Form` 按短剧平台切换配置，只展示接口 URL、PID、KEY 和启用状态；普通管理员只读，超级管理员可提交并测试已保存的启用配置。KEY 只在表单临时输入，已有配置不会回填，留空表示保留原密钥；页面不展示明文、密文或掩码。页面对接后端 `GET /api/admin/drama/providers`、`PUT /api/admin/drama/providers/{providerId}/connection` 和 `POST /api/admin/drama/providers/{providerId}/connection/test`。推广链接、订单、佣金、导出和转化分析仍属于后续模块。

左侧“短剧管理”一级菜单下提供“短剧目录”二级菜单，页面路由为 `/drama/catalog`，普通管理员和超级管理员均可访问。当前只对接 GoodShort：管理员可按平台、名称、语言、远端状态和本地状态查询目录，在右侧抽屉查看短剧与剧集元数据，确认上架或下架，并通过工具栏提交全量/增量同步任务和查看各语言同步状态、分页进度、统计及错误。同步请求只创建后端任务，不在页面请求中等待第三方同步完成。页面对接 `GET /api/admin/drama/catalog`、`GET /api/admin/drama/catalog/{id}`、`POST /api/admin/drama/catalog/sync`、`GET /api/admin/drama/catalog/sync/status` 和 `PATCH /api/admin/drama/catalog/{id}/status`；不展示连接 ID、PID、KEY、凭据或租约字段。

左侧“推广管理”一级菜单下提供“媒体账号报备”二级菜单，页面路由为 `/promotion/media-accounts`。管理员可以按用户编号、媒体平台、短剧平台、账号状态和报备状态筛选媒体账号，查看详情、编辑账号资料以及重试失败报备；页面不提供新增和删除媒体账号。页面对接 `GET/PUT /api/admin/promotion/media-accounts/{id}`、`GET /api/admin/promotion/media-accounts` 和 `POST /api/admin/promotion/media-accounts/{id}/filings/{providerId}/retry`。

Analysis 页面和管理查询表格页根据 Ant Design Pro 官方 MIT 源码适配，来源与许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
分析页通过路由懒加载，图表运行时只在进入 `/dashboard` 后加载。

开发环境中，Vite 会将 `/api` 请求代理到 `http://localhost:8080`。登录接口使用后端现有的 `POST /api/admin/auth/login`；两类管理页面分别使用 `/api/admin/management/**` 和 `/api/user/management/**`。

## 运行

要求 Node.js 24 和 pnpm 11。

```powershell
pnpm install
pnpm dev
```

默认开发地址为 `http://localhost:5173`。本地联调前需要先启动 `kasi-backend`。

如果前后端不是同源部署，可以复制 `.env.example` 为本地环境文件并配置：

```text
VITE_API_BASE_URL=http://localhost:8080
```

## 校验

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
```

## 目录

```text
src/
  api/            Axios 实例与通用响应类型
  features/       按业务领域组织的 API、状态和类型
  layouts/        管理后台通用布局
  pages/          路由页面
  router/         路由与访问控制
  styles/         全局样式
  test/           测试环境配置
```
