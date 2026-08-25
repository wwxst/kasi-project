# Kasi 用户中心

Kasi 推广平台的独立用户端前端，后端项目位于同级目录
`../kasi-backend`。应用面向电脑浏览器设计，并保留移动端基本可用性。

## 技术栈

- React 19 + TypeScript strict + Vite
- TDesign React + TDesign Icons React
- React Router
- TanStack Query
- Zustand
- Axios
- Vitest + React Testing Library + MSW
- Oxlint + Prettier
- pnpm

登录后的后台壳层、内容区面板、表格工具栏和业务表单参考腾讯 TDesign React Starter 的 Side Layout 与基础页面风格，保留本项目现有的认证、路由和业务页面：
<https://github.com/Tencent/tdesign-react-starter>

## 当前功能

- 手机号或邮箱登录
- 登录、注册和忘记密码表单统一使用 TDesign 字段级校验，必填、格式、验证码和密码错误显示在对应输入框下方，不使用整页错误框；接口业务错误使用 TDesign Alert 提示
- 注册验证码和用户注册
- 忘记密码验证码、身份验证和密码重置
- 刷新页面后的登录状态恢复
- 只读账户资料和最近登录信息
- 本人修改密码
- 当前会话退出
- 登录后的用户中心采用 TDesign 后台布局：顶部用户栏、左侧平铺导航和响应式移动端菜单；顶部工具栏保留消息和用户菜单，头像下拉提供个人资料、安全设置和退出登录入口
- `/account` 首页采用 TDesign React Starter Dashboard Base 结构，展示账号指标卡、推广数据区域、账号状态、最近绑定账号和个人资料
- `/promotion/links` 查询已上架短剧和本人已报白媒体账号，调用真实后端接口生成/查询 GoodShort 推广口令、链接和 trackingNo
- `/promotion/income` 按月份展示本人已归因订单、订单金额、计算佣金、退款冲销和净佣金，并支持 CSV 导出

当前后端未提供用户本人资料编辑接口，因此昵称、真实姓名、手机号、
邮箱和头像只展示，不提供编辑。快速上线版以 `PromotionLink` 为唯一推广归因来源，旧 `/promotion/tasks` 自动跳转到 `/promotion/links`，不再展示尚未接入的 0 值统计。订单由管理员手动同步；用户端不提供账单锁定/付款状态、钱包、提现、自动订单同步、转化分析、视频下载、TikTok 锚点和 Token 刷新。

## 环境要求

- Node.js 24
- pnpm 11
- 已启动的 `kasi-backend`

安装并启动：

```powershell
pnpm install
pnpm dev
```

默认地址为 `http://localhost:5173`。开发服务器将 `/api` 请求代理到
`http://localhost:8080`。需要修改后端地址时，复制 `.env.example` 为
`.env.local` 并设置：

```text
VITE_PROXY_TARGET=http://localhost:8080
```

生产环境默认使用同源 `/api`。如使用独立 API 域名，可在构建环境设置
`VITE_API_BASE_URL`，同时确保后端正确配置 CORS。

## 路由

| 路径                | 说明                              |
| ------------------- | --------------------------------- |
| `/login`            | 用户登录                          |
| `/register`         | 用户注册                          |
| `/forgot-password`  | 忘记密码三步流程                  |
| `/account`          | Dashboard Base 风格账户工作台     |
| `/account/security` | 修改密码                          |
| `/account/filing`   | 媒体账号报白                      |
| `/promotion/links`  | 创建并查询真实 GoodShort 推广链接 |
| `/promotion/income` | 本人月度订单与佣金明细            |
| `/promotion/tasks`  | 兼容地址，跳转到推广链接          |

Token 和绝对过期时间保存在 `sessionStorage`。刷新页面保留登录，关闭
当前页面后通常需要重新登录。应用启动时会调用
`GET /api/user/auth/me` 验证服务端会话。

## 校验

```powershell
pnpm test
pnpm typecheck
pnpm lint
pnpm format:check
pnpm build
```

## 账号报白

新增媒体账号时无需选择报白平台；后端会按当前已配置且支持报白的全部短剧平台分别建立报备记录，页面按平台汇总状态，失败时仍可针对具体平台重试。

登录后访问 `/account/filing`。页面使用腾讯后台风格的 TDesign 数据表格和右侧抽屉，支持搜索、选择、分页、导出、详情、新增、编辑媒体账号，以及在详情中对报白失败的账号重新提交。列表字段为复选框、媒体平台、账号名称、GoodShort 报白状态和详情操作；状态只展示“审核中”“已加白”“已失败”，账号 ID、主页链接和错误信息保留在详情抽屉中，不提供启用或禁用账号操作。

## 目录

```text
src/
  app/          Provider 和路由门禁
  features/     auth 与 account 业务领域
  layouts/      登录后的账户壳层
  pages/        路由页面
  shared/       HTTP 响应、错误和通用设施
  styles/       全局样式
  test/         Vitest 与 MSW 基础设施
```

设计和实施记录位于 `docs/superpowers/`。
