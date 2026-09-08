# 用户微信号与学员类型设计

## 状态

已确认设计，尚未实施。

## 背景

推广用户需要增加微信号资料，并由后台维护用户是否属于学员。学员后续可能继续细分，因此不使用布尔字段表达学员身份。

## 数据模型

在 `promotion_user` 表增加以下字段：

```sql
wechat_id VARCHAR(128) DEFAULT NULL COMMENT '微信号'
student_type TINYINT NOT NULL DEFAULT 0 COMMENT '学员类型：0非学员 1基础学员'
```

当前枚举值：

| 值 | 含义 |
|---:|---|
| 0 | 非学员 |
| 1 | 基础学员 |

`student_type` 保留后续扩展空间。新增类型时必须同步更新后端枚举约束、管理端展示和筛选文案。`wechat_id` 暂不增加唯一索引，除非后续业务明确要求一个微信号只能绑定一个用户并完成历史数据清理。

## 权限与业务规则

- `wechat_id` 和 `student_type` 由管理员在后台用户管理中维护。
- 用户端不能通过个人资料接口修改这两个字段。
- 用户端 `/api/user/auth/me` 可以返回这两个字段；是否在个人中心展示由用户端页面需求决定，但展示必须是只读。
- `student_type` 在管理端新增、编辑、详情和列表中使用；列表应支持按学员类型筛选。
- 微信号属于联系资料，列表展示是否脱敏应按后台页面的隐私展示规范处理，详情页可完整展示。

## 接口与代码影响

后端需要同步修改 `PromotionUser`、用户管理和当前用户的 DTO/VO、MyBatis resultMap 与 insert/update/select，以及新增 Flyway 迁移。开发重建脚本 `kasi_promotion.sql` 也必须包含最终字段结构。

管理端需要同步用户类型定义、用户列表/详情、新增和编辑表单、筛选条件及测试。

用户端只扩展当前用户响应类型和只读展示所需类型，不把字段加入 `UpdateUserProfileDTO` 或用户端资料更新请求。

## 迁移

生产数据库使用新的 Flyway 版本迁移，不修改已执行的 `V1__baseline.sql`。迁移示意：

```sql
ALTER TABLE promotion_user
    ADD COLUMN wechat_id VARCHAR(128) DEFAULT NULL COMMENT '微信号' AFTER email,
    ADD COLUMN student_type TINYINT NOT NULL DEFAULT 0 COMMENT '学员类型：0非学员 1基础学员' AFTER wechat_id,
    ADD KEY idx_student_type (student_type);
```

本设计不包含字段实施、历史数据回填或学员类型自动计算。
