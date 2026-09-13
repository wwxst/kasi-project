# 推广项目类型管理设计

日期：2026-09-12

状态：已批准并实施

## 1. 目标

在现有推广项目之上增加独立项目类型。首批类型为 CPA、CPM、CPS；类型只承担项目分类，不实现对应的计费、佣金或结算规则。

管理端左侧导航调整为：

```text
项目管理
├─ 项目列表
└─ 项目类型
```

## 2. 数据结构

新增 `promotion_project_type` 表，维护唯一类型编码、类型名称、启停状态、排序和创建/更新时间。初始化 CPA、CPM、CPS 三条启用记录。

`promotion_project` 增加 `project_type_id` 逻辑关联。按照仓库现有 schema 规则不建立物理外键，由 Service 校验类型存在性和可选状态。

升级前已经存在的项目无法仅凭名称可靠判断类型，因此迁移不自动猜测，允许旧记录暂时为空。所有新的项目和经过编辑的项目必须选择类型；管理端将尚未归类的旧记录显示为“未设置”。

## 3. 业务规则

- 类型编码去除空白并转为大写，只允许字母开头以及字母、数字、下划线，最大 32 个字符。
- 类型名称去除首尾空白后不能为空，最大 64 个字符。
- 类型状态为 `ENABLED` 或 `DISABLED`，排序为非负整数。
- 新建项目只能选择启用类型。
- 项目原有类型被停用后仍可保留该关联，不自动改变项目状态或用户端展示。
- 已被项目引用的类型禁止物理删除；未被引用的类型可物理删除。

## 4. API

项目类型管理接口：

```http
GET    /api/admin/promotion/project-types
POST   /api/admin/promotion/project-types
PUT    /api/admin/promotion/project-types/{id}
DELETE /api/admin/promotion/project-types/{id}
```

现有项目新增和修改请求增加必填 `projectTypeId`。项目管理响应增加 `projectTypeId`、`projectTypeCode` 和 `projectTypeName`；旧项目未归类时三个字段为空。用户端项目卡片契约保持不变。

## 5. 管理端

`/promotion/projects` 保留为项目列表路由，列表增加“项目类型”列，新增和编辑表单增加项目类型必选下拉框。

`/promotion/project-types` 是独立项目类型页面，提供列表、新增、编辑、启停、排序和删除。两个页面位于“项目管理”一级菜单下，不使用页签混合。

## 6. 非目标

- 不实现 CPA、CPM、CPS 的计费公式、规则版本、账单、钱包或提现。
- 不根据项目名称自动选择类型。
- 不改变用户端项目卡片展示和现有项目封面、文档、启停、排序规则。
