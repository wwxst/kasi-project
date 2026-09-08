# 推广项目卡片与后台管理设计

日期：2026-09-09

状态：设计草案，待确认，尚未实施

## 1. 背景

用户工作台需要展示推广项目卡片。当前卡片图片和项目资料写在用户端页面代码中，项目文档确定为链接跳转。后台需要能够维护项目内容、显示顺序和卡片上的报白入口。

当前项目包括 CPS 短剧推广、CapCut 拉新和其他运营项目。每个项目的报白方式不同：有的由系统处理，有的跳转外部报白文档，有的没有报白入口。

CapCut 的系统报白和 CAPCUT UID 订单归属属于独立模块，详见[CapCut 报白与订单归属设计](2026-09-09-capcut-filing-and-order-attribution-design.md)。

## 2. 目标

- 后台可以新增、编辑、发布、下线和排序项目卡片。
- 项目可以配置名称、类型、封面图片和项目文档链接。
- 项目可以配置报白入口：系统内报白、外部报白文档或无入口。
- CPS 短剧推广作为正式项目卡片展示，并复用现有短剧账号报白能力。
- 用户端从接口读取项目，不再把项目列表写死在页面组件中。
- 卡片只展示图片，不重复叠加图片内已有文字。

## 3. 非目标

- 不在本模块实现 CapCut 账号表单、审核和订单 UID 匹配。
- 不在用户端制作项目文档编辑器，项目文档只保存链接。
- 不强制所有项目显示报白按钮。
- 不物理删除已经发布过的项目。
- 不改变现有 CPS 短剧推广链接、GoodShort 账号报白和订单行为。

## 4. 报白入口模式

每个项目最多配置一种报白入口：

| 模式 | 卡片行为 | 必须的数据 |
| --- | --- | --- |
| `SYSTEM` | 打开系统内指定的报白页面 | `systemCode`，例如 `CAPCUT` |
| `DOCUMENT` | 在新标签页打开报白文档 | `filingDocumentUrl` |
| `NONE` | 不显示报白按钮 | 无 |

前端根据接口返回的模式判断按钮行为，不根据项目名称判断。

按钮文案规则：

- `SYSTEM`：显示“账号报白”。
- `DOCUMENT`：显示“报白文档”。
- `NONE`：不显示报白按钮。

CapCut 卡片使用 `SYSTEM + CAPCUT`，点击后进入 CapCut 系统报白页面。CPS 短剧卡片继续进入现有短剧账号报白流程，具体入口由项目配置决定。

## 5. 数据库设计

新增 `promotion_project` 表：

```text
promotion_project
- id
- project_key              稳定唯一键，例如 cps-drama、capcut
- name
- project_kind             CPS_DRAMA / CAPCUT / OTHER
- cover_image_url
- project_document_url     可为空
- filing_mode              SYSTEM / DOCUMENT / NONE
- filing_system_code       SYSTEM 模式使用，例如 CAPCUT
- filing_document_url      DOCUMENT 模式使用，可为空
- status                   DRAFT / PUBLISHED / OFFLINE
- sort_order
- created_by
- updated_by
- created_at
- updated_at
```

数据约束：

- `project_key` 唯一，发布后不修改。
- 只有 `PUBLISHED` 项目返回给用户端。
- `filing_mode = SYSTEM` 时必须有 `filing_system_code`。
- `filing_mode = DOCUMENT` 时必须有 `filing_document_url`，且只允许 `https://`。
- `filing_mode = NONE` 时两个报白入口字段为空。
- 下线使用 `OFFLINE`，保留项目和历史关联数据。
- 表不建立物理外键，沿用当前生产 schema 的逻辑关联规则。

生产环境新增表只能通过新的 Flyway 迁移，开发重建脚本必须同步更新。

## 6. 用户端接口

```http
GET /api/user/promotion/projects?placement=WORKSPACE_HOME
```

只返回已发布项目和卡片所需字段：

```json
{
  "id": 2,
  "projectKey": "capcut",
  "name": "CapCut 拉新项目",
  "projectKind": "CAPCUT",
  "coverImageUrl": "https://...",
  "projectDocumentUrl": "https://...",
  "filing": {
    "mode": "SYSTEM",
    "systemCode": "CAPCUT",
    "documentUrl": null
  }
}
```

项目文档和外部报白文档只作为 URL 返回，用户端使用普通链接打开，不由后端代理。

## 7. 管理端接口与页面

```http
GET    /api/admin/promotion/projects?page=1&size=20
POST   /api/admin/promotion/projects
PUT    /api/admin/promotion/projects/{id}
PATCH  /api/admin/promotion/projects/{id}/status
```

后台项目页面维护：

- 项目名称、项目类型和封面图片 URL。
- 项目文档 URL。
- 报白模式、系统标识或报白文档 URL。
- 排序和发布状态。

修改 `filing_mode` 时，后端校验对应字段是否完整。发布前项目名称、封面图片和排序必须有效。

项目页面不负责审核 CapCut 账号；CapCut 账号审核属于独立页面和接口。

## 8. 用户端卡片

工作台使用 TanStack Query 请求项目列表，替换当前页面内的静态项目数组。

- 卡片继续只显示官网图片。
- 悬停显示项目文档和报白入口。
- `SYSTEM` 入口跳转系统页面。
- `DOCUMENT` 入口打开外部报白文档。
- `NONE` 不显示报白按钮。
- 移动端没有鼠标悬停时，操作按钮保持可见或通过点击显示。
- 项目列表提供加载、失败重试和空数据状态。

建议的项目行为：

| 项目 | 项目文档 | 报白入口 |
| --- | --- | --- |
| CPS 短剧推广 | 后台配置链接 | 现有短剧账号报白 |
| CapCut 拉新 | 后台配置链接 | 系统内 CapCut 报白 |
| 其他项目 | 后台配置链接 | 文档链接或不显示 |

## 9. 分层与验证

后端按现有 `promotion/controller`、`dto`、`entity`、`mapper`、`service`、`vo` 分层。Controller 负责权限、参数校验和响应包装；Service 负责发布规则和报白入口条件校验；Mapper 只负责 SQL。

后端验证覆盖：

- 项目 CRUD、发布过滤、排序和状态转换。
- `SYSTEM`、`DOCUMENT`、`NONE` 三种模式的条件校验。
- 普通用户不能读取草稿或下线项目。
- 管理员项目权限和外部 HTTPS 链接校验。
- 现有 CPS 短剧报白和推广链接行为不回归。

前端验证覆盖：

- 卡片按接口模式显示或隐藏报白按钮。
- 系统报白、外部文档和无入口三种行为正确。
- 桌面和 320px 以上移动视口没有按钮遮挡或卡片裁切。

## 10. 实施顺序

1. 新增项目表和项目管理 CRUD。
2. 新增用户项目列表接口。
3. 用户端把静态卡片改为接口数据。
4. 接入项目文档链接和三种报白入口。
5. 在 CapCut 系统报白模块完成后，将 CapCut 项目配置为 `SYSTEM`。

本设计确认前不新增项目 API、schema 或权限代码。
