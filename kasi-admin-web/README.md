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

当前已完成前端基础工程、管理员登录入口、登录 API 对接、会话保存、受保护路由和管理后台布局。后台壳层包含固定顶栏、导航搜索、通知入口、全屏控制、账户菜单、可折叠桌面侧栏、移动端导航抽屉和面包屑。左侧菜单不再显示“工作台”分组，带子菜单的一级菜单默认收起，点击后展开。

管理端统一处理受保护接口的认证失效：任一已登录请求收到 HTTP 401 时清除当前会话并返回登录页，页面不再为并发 401 重复弹出错误提示。HTTP 403 表示权限不足，HTTP 503 或业务码 1007 表示认证状态服务暂时不可用，这些错误均保留当前会话并由页面展示或重试，不强制重新登录。

首页采用 Ant Design Pro Analysis 分析页结构，包含指标卡、销售趋势、门店排名、热门搜索、销售额类别占比和门店趋势。当前统计值沿用官方演示数据，不代表后端真实业务数据；接入统计 API 后只需替换 `dashboardData.ts` 数据源。个人主页展示当前已认证管理员的真实资料。

管理员管理 `/admin-management` 和推广用户管理 `/user-management` 直接使用 Ant Design Pro 官方 `PageContainer`、`ProTable` 组件，并接入真实后端接口。两个页面支持关键词查询、分页、新建、分组详情抽屉、详情内编辑和物理删除，不展示表格编辑与重置密码入口，也不使用状态开关。详情按“基本信息”和“账号资料”分组展示，并在顶部展示当前详情记录的 64px 头像、名称和账号标识。管理员头像不出现在新增或编辑表单中，只能点击详情顶部头像选择 JPG/PNG/WebP 文件，在 1:1 裁剪后上传，文件不得超过 2 MB；本人头像走 `/api/admin/auth/avatar` 并同步顶部导航状态，其他管理员头像走 `/api/admin/management/{id}/avatar`。管理员详情内提供“编辑”和“修改密码”：打开谁的详情就操作谁；本人资料走 `/api/admin/auth/profile`，本人改密走 `/api/admin/auth/password` 且成功后返回登录页，其他管理员继续使用 `/api/admin/management/{id}` 与 `/api/admin/management/{id}/password`。本人改密和他人重置密码都只填写新密码与确认密码，不要求原密码。推广用户详情只展示头像，不提供修改密码入口。管理员表格固定展示姓名、手机号、邮箱、角色、登录时间、状态和“详情｜删除”；用户表格固定展示用户 ID、昵称、手机号、邮箱、注册来源、状态和“详情｜更多”，“更多”菜单包含启用/禁用和删除。管理员管理仅超级管理员可见和访问，推广用户管理对全部管理员开放；唯一超级管理员仍禁止删除，但允许通过本人认证接口编辑资料、上传头像和修改密码，后端权限规则继续兜底。

左侧“系统配置”一级菜单下提供“短剧 API 配置”二级菜单，页面路由为 `/system-config/drama-api`，旧 `/provider-management` 地址自动跳转到新路由。页面使用 Ant Design `Tabs + Form` 按短剧平台切换配置，展示媒体域名白名单、接口 URL、PID、KEY 和启用状态；媒体域名白名单填写根域名（当前 GoodShort 为 `novelopen.com`），根域及其正规子域允许访问，未知域名不会自动放行。普通管理员只读，超级管理员可提交并测试已保存的启用配置。KEY 只在表单临时输入，已有配置不会回填，留空表示保留原密钥；页面不展示明文、密文或掩码。页面对接后端 `GET /api/admin/drama/providers`、`PUT /api/admin/drama/providers/{providerId}/connection` 和 `POST /api/admin/drama/providers/{providerId}/connection/test`。

左侧“系统配置”一级菜单下提供“定时任务”二级菜单，页面路由为 `/system-config/scheduled-tasks`，对接后端 `GET/PUT /api/admin/system/scheduled-tasks`。页面仅展示标题、任务说明、执行周期、是否开启和操作五列；超级管理员可编辑周期类型、间隔值、执行时间、星期/日期、说明和启停，普通管理员只读。执行周期支持“每隔N秒/分钟/小时/天、每天、每星期、每月、每年”，保存时提交结构化周期字段，由后端计算下一次执行时间。任务标题、编码和执行程序由后端固定，页面不提供新增、删除、日志、执行历史、下次执行时间或立即执行入口。

左侧“短剧管理”一级菜单下提供“短剧目录”二级菜单和独立的“短剧同步”“剧集同步”二级菜单。短剧同步页面路由为 `/drama/sync/catalog`，剧集同步页面路由为 `/drama/sync/content`，两个页面不混合数据。同步记录表统一展示创建时间、触发方式、任务类型、状态、新增数、更新数、总处理数和操作；同一次触发产生的多语言或多短剧子任务在列表中聚合为一条，点击“查看详情”后查看语言、短剧、子任务状态和错误，失败子任务可重试。目录同步保留全量/增量任务及各语言状态；语言留空时由后端展开全部 13 种支持语言。免费剧集同步支持单部、最多 100 部勾选批量、全部在线短剧，以及仅补齐缺失视频地址。所有同步请求只创建后端任务，不在页面请求中等待 GoodShort 完成；当前不支持收费剧集同步，也不展示永久视频 URL。同步页面对接 `GET /api/admin/drama/catalog/sync/records`、`GET /api/admin/drama/catalog/sync/records/{runId}`、`GET /api/admin/drama/catalog/contents/sync/records`、`GET /api/admin/drama/catalog/contents/sync/records/{runId}` 及现有同步写入接口；不展示连接 ID、PID、KEY、凭据或租约字段。

左侧“推广管理”一级菜单下提供“媒体账号报备”二级菜单，页面路由为 `/promotion/media-accounts`。管理员可以按用户编号、媒体平台、短剧平台、账号状态和报备状态筛选媒体账号，查看详情、编辑账号资料以及重试失败报备；页面不提供新增和删除媒体账号。页面对接 `GET/PUT /api/admin/promotion/media-accounts/{id}`、`GET /api/admin/promotion/media-accounts` 和 `POST /api/admin/promotion/media-accounts/{id}/filings/{providerId}/retry`。

“推广管理”下新增“推广订单”，页面路由为 `/promotion/orders`。普通管理员和超级管理员均可按短剧平台、订单状态、归因状态和支付时间查询订单，查看金额、trackingNo、归因与佣金状态，按相同条件导出 CSV，并在弹窗中选择平台和不超过 31 天的时间窗口手动同步 GoodShort 订单。页面对接 `POST /api/admin/promotion/orders/sync`、`GET /api/admin/promotion/orders` 和 `GET /api/admin/promotion/orders/export.csv`。首发不提供自动订单同步、人工改归属、正式账单、钱包或提现。

Analysis 页面和管理查询表格页根据 Ant Design Pro 官方 MIT 源码适配，来源与许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
分析页通过路由懒加载，图表运行时只在进入 `/dashboard` 后加载。

开发环境中，Vite 会将 `/api` 请求代理到 `http://localhost:8080`。登录接口使用后端现有的 `POST /api/admin/auth/login`；两类管理页面分别使用 `/api/admin/management/**` 和 `/api/user/management/**`。

“短剧管理”下的“短剧同步”和“剧集同步”是两个独立页面，分别使用 `/drama/sync/catalog` 和 `/drama/sync/content` 路由。页面只负责创建任务、展示按触发聚合的记录和查看详情；底层 checkpoint、剧集任务、worker、租约和重试模型保持不变。

## 运行

Node.js 和 pnpm 版本以 `package.json` 的 `engines` 与 `packageManager` 为准。

```powershell
pnpm install --frozen-lockfile
pnpm dev
```

默认开发地址为 `http://localhost:5173`。本地联调前需要先启动 `kasi-backend`。

如果前后端不是同源部署，可以复制 `.env.example` 为本地环境文件并配置：

```text
VITE_API_BASE_URL=http://localhost:8080
```

## 校验

```powershell
pnpm check
```

`pnpm check` 包含 lint、format、test 和包含 TypeScript 编译的 build。Vitest 当前使用 `maxWorkers=2` 作为已重复验证的稳定配置，不是永久架构规则。

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

# 短信配置

超级管理员可在 `/system-config/sms` 配置阿里云短信签名、模板和启用状态。AccessKey 输入框仅用于写入，不回显已保存密钥。
