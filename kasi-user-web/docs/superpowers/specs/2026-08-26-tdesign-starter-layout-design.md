# TDesign Starter 布局迁移设计

## 目标

将 `tdesign-react-starter` 的中后台布局壳层、样式和交互细节迁移到 `kasi-user-web`，保留用户端自己的业务页面和接口边界。

## 当前状态

- 用户前端当前只有 `App -> LoginPage`。
- TDesign React、TDesign Icons React、React Router 已安装。
- 当前入口已加载 `tdesign-react/es/style/index.css`。
- 用户前端没有 Redux、Starter 布局状态、后台路由树或业务工作台页面。
- 登录页已经完成 Starter 风格复刻，并作为全屏页面使用。

## 迁移范围

### 直接复用

- `Layout` 组合：Side、FullPage。
- 左侧菜单：232px 宽度、收起状态、Logo、激活项、滚动条和主题样式。
- 顶部 Header：菜单按钮、搜索、消息、帮助、用户操作和设置入口的视觉结构。
- Page 容器：24px 页面边距、Breadcrumb、Loading、全屏页面旁路。
- Footer：居中版权信息和显示开关。
- 右侧设置抽屉：主题模式和 Header/Breadcrumb/Footer 显示开关；不暴露 Top/Mix 布局切换。
- Starter 的 Less 模块化样式和 TDesign Design Token 使用方式。
- 小于 900px 自动收起菜单、大于 1000px 自动展开菜单的响应式策略。

### 用户端替换

- 菜单路由改为用户端业务路由，不复制 Starter 的 Dashboard、List、Form、Detail Demo 页面。
- 用户菜单只调用 `/api/user/**` 业务边界，不接入管理员路由。
- 用户信息、退出登录和后续权限状态接入用户端现有认证方案；本阶段不伪造后台业务数据。
- Starter 的 GitHub、Demo Wiki 和示例消息入口改为用户端可用入口，暂时不可用的动作显示为受控占位或隐藏。

## 目标结构

```text
main.tsx
  BrowserRouter
    AppShell
      FullPageRoute: /login
      AuthenticatedLayout: Side
        Header
        UserMenu
        SidebarMenu
        PageRouter
          Page + Breadcrumb
          User business pages
        Footer
        SettingDrawer
```

## 状态设计

新增布局状态模块，最小字段如下：

- `layout`: 固定为 `side`
- `collapsed`: 菜单是否收起
- `theme`: `light | dark`
- `systemTheme`: 是否跟随系统
- `color`: 当前品牌色
- `showHeader`: 是否显示 Header
- `showBreadcrumbs`: 是否显示面包屑
- `showFooter`: 是否显示 Footer
- `settingOpen`: 设置抽屉是否打开

状态只负责布局和主题，不保存业务数据。主题切换通过 `document.documentElement` 的属性和 TDesign token 完成，业务页面不直接修改布局状态。

## 路由和认证

- `/login` 使用 FullPage，不渲染菜单、Header、Breadcrumb 或 Footer。
- 已认证用户进入用户端默认首页；未认证用户访问业务路由时回到 `/login`。
- 页面路由采用 React Router 的嵌套路由，菜单项从同一份路由元数据生成，避免菜单和页面路径分叉。
- 本阶段先提供一个用户端首页占位，确保 Side 壳层可以被实际查看；真实 Dashboard 数据不在本次布局迁移范围内。

## 响应式和视觉验收

- 桌面端保留 Starter 的 232px 侧栏、Header 高度、边框、间距、激活色和主题色。
- 900px 以下自动收起侧栏，菜单仍可通过 Header 按钮打开。
- 移动端不允许文档整体横向溢出；只有确有固定宽度要求的业务内容允许局部滚动。
- 验收覆盖：登录全屏、Side 布局、收起/展开、主题切换、设置抽屉、Breadcrumb/Footer 开关和移动视口。

## 测试和验证

- 为布局状态添加 reducer/状态行为测试。
- 为路由守卫验证登录页旁路和业务页重定向。
- 为菜单渲染验证隐藏路由、单子菜单和当前路径激活。
- 为 AppShell 添加桌面和移动视口的关键 DOM 断言。
- 运行 `pnpm test`、`pnpm typecheck`、`pnpm lint`、`pnpm format:check`、`pnpm build`。
- 使用浏览器检查至少 1280px 桌面和 390px 移动视口，确认无控制台错误、无文档级横向溢出和无壳层重叠。

## 不在本阶段

- 不迁移 Starter 的示例业务页面、图表、Demo 数据和 Redux 业务 slice。
- 不连接真实用户 API，不改变后端认证契约。
- 不实现完整 RBAC、动态菜单、消息中心或真实设置持久化。

## 当前与规划边界

- 当前已实现：Starter 风格登录页和 TDesign 基础样式导入。
- 本设计批准但未实施：用户端 Side AppShell、路由守卫、布局状态、菜单、Header、Footer、设置抽屉和响应式壳层。
- 后续建议：在壳层稳定后，再逐页接入用户端真实业务页面和 API。
