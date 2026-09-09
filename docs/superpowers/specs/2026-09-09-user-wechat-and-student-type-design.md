# 用户微信号与学员类型设计

## 状态

已确认设计。`wechat_id` 与 `student_type` 已实施用户端展示和数据持久化，管理端用户管理已支持列表、详情、新建和编辑维护。

## 背景

推广用户需要增加微信号资料，并由后台维护用户是否属于学员。学员后续可能继续细分，因此不使用布尔字段表达学员身份。

## 数据模型

原设计计划在 `promotion_user` 表增加以下字段：

```sql
wechat_id VARCHAR(128) DEFAULT NULL COMMENT '微信号'
student_type TINYINT NOT NULL DEFAULT 0 COMMENT '学员类型：0基础用户 1基础学员'
```

两个字段均已落地：`wechat_id` 由 Flyway `V7__user_profile_contacts.sql` 创建，`student_type` 由 Flyway `V8__user_student_type.sql` 创建，开发重建脚本也包含二者。`student_type` 已加入用户实体、MyBatis 映射和当前用户响应。

当前枚举值：

| 值 | 含义 |
|---:|---|
| 0 | 基础用户 |
| 1 | 基础学员 |

`student_type` 保留后续扩展空间。新增类型时必须同步更新后端枚举约束、管理端展示和筛选文案。`wechat_id` 暂不增加唯一索引，除非后续业务明确要求一个微信号只能绑定一个用户并完成历史数据清理。

## 权限与业务规则

- 当前用户端可通过个人资料接口修改 `wechat_id`；个人中心同时支持修改昵称、真实姓名、手机号和邮箱。手机号或邮箱变更会使旧会话失效。
- 用户端 `/api/user/auth/me` 返回 `wechat_id` 和 `studentType`；个人中心将微信号作为可编辑联系资料，将学员类型作为只读信息展示。
- `student_type` 不加入用户端编辑资料五项，也不接受用户自助修改；管理端可维护学员类型。
- 管理端用户列表、详情、新建和编辑表单同步支持微信号与学员类型，类型文案为 `0=基础用户`、`1=基础学员`。
- 微信号暂不增加唯一索引，列表脱敏规则待后台页面隐私规范确定。

## 接口与代码影响

后端已同步修改 `PromotionUser`、`CurrentUserVO`、管理端 `UserDetailVO`/`UserListItemVO`、管理 DTO、MyBatis resultMap 与管理/自助 update/select，并新增 Flyway V7、V8 迁移；开发重建脚本 `kasi_promotion.sql` 和测试 schema 也已包含 `wechat_id`、`student_type`。

管理端用户管理已同步用户类型定义、用户列表/详情、新增和编辑表单及测试；当前未增加按学员类型筛选条件。

用户端已扩展当前用户响应类型和资料更新请求，允许更新 `wechatId`、`mobile`、`email` 以及昵称和真实姓名；`studentType` 只存在于当前用户响应类型，不存在于资料更新请求。

## 迁移

生产数据库使用新的 Flyway 版本迁移，不修改已执行的 `V1__baseline.sql`。当前新增的 V7、V8 迁移脚本为：

```sql
ALTER TABLE promotion_user
    ADD COLUMN wechat_id VARCHAR(128) DEFAULT NULL COMMENT '微信号' AFTER email;
```

```sql
ALTER TABLE promotion_user
    ADD COLUMN student_type TINYINT NOT NULL DEFAULT 0 COMMENT '学员类型：0基础用户 1基础学员' AFTER wechat_id;
```

本设计不包含历史数据回填或学员类型自动计算；现有数据使用默认值 `0`，用户端仅按响应值只读展示，管理端可显式维护 `0`/`1`。
