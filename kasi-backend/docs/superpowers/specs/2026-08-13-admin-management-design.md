# 管理员账号管理设计规格

日期：2026-08-13

## 1. 目标

在现有管理员认证模块上增加管理员账号管理能力，由系统中唯一的超级管理员管理普通管理员。

本期采用 `is_super_admin` 两级权限，不建设 RBAC，不新增角色表、权限表或关联表。保持现有 Controller、Service、Mapper 分层，并继续使用 Redis 会话版本机制处理敏感状态变更。

## 2. 范围

### 2.1 本期包含

- 超级管理员分页搜索管理员。
- 超级管理员查看管理员详情。
- 超级管理员新增普通管理员。
- 超级管理员编辑普通管理员资料和登录账号。
- 超级管理员启用、禁用普通管理员。
- 超级管理员重置普通管理员密码。
- 超级管理员物理删除普通管理员。
- 所有管理员在个人主页编辑自己的资料和登录账号。
- 所有管理员继续通过现有个人密码接口修改自己的密码。
- 从管理员领域中彻底移除 `nickname`，统一使用必填的 `realName`。

### 2.2 本期不包含

- RBAC、角色、权限点、菜单授权。
- 多个超级管理员、超级管理员转让或普通管理员晋升。
- 软删除、管理员恢复站。
- 操作日志和登录审计扩展。
- 部门表和 `department_id` 外键。
- 初始超级管理员引导流程。初始超级管理员继续由开发阶段的数据库初始化方式提供。

## 3. 核心业务规则

### 3.1 唯一超级管理员

- 系统只允许存在一个 `is_super_admin = 1` 的管理员。
- 管理接口新增管理员时，后端固定写入 `is_super_admin = 0`。
- 所有请求 DTO 均不暴露 `isSuperAdmin` 字段。
- 管理接口不提供晋升、降级或转让超级管理员的能力。
- 超级管理员不能禁用或删除自己。
- 超级管理员不能通过管理接口重置自己的密码。
- 超级管理员在个人主页修改自己的资料和密码，与普通管理员使用相同的个人接口。
- 唯一性由本期业务入口保证；本期不为 `is_super_admin` 增加新的数据库约束。

### 3.2 普通管理员

- 普通管理员可以登录、退出、查看本人信息、编辑本人资料和修改本人密码。
- 普通管理员不能访问 `/api/admin/management/**`。
- 普通管理员不能新增、编辑、禁用、重置或删除其他管理员。
- 普通管理员不能修改自己的状态或 `is_super_admin`。

### 3.3 删除语义

- 管理员删除采用物理删除，直接删除 `sys_admin_user` 记录。
- 只有普通管理员可以被删除。
- 删除后原 `username`、手机号和邮箱可以重新使用。
- 删除会永久移除该管理员记录及其表内登录信息、审计字段。这是本期明确接受的物理删除代价。

## 4. 分层与代码结构

认证与账号管理保持在现有 `admin` 模块内，但职责分离：

```text
admin/
├─ controller/
│  ├─ AdminAuthController.java
│  └─ AdminManagementController.java
├─ service/
│  ├─ AdminAuthService.java
│  ├─ AdminManagementService.java
│  └─ impl/
│     ├─ AdminAuthServiceImpl.java
│     └─ AdminManagementServiceImpl.java
├─ dto/
├─ vo/
├─ entity/
│  └─ SysAdminUser.java
└─ mapper/
   └─ SysAdminUserMapper.java
```

- `AdminAuthController` 和 `AdminAuthService` 继续负责登录、退出、本人信息、本人资料和本人改密。
- `AdminManagementController` 和 `AdminManagementService` 负责超级管理员对普通管理员的管理。
- 两套 Service 复用 `SysAdminUserMapper`，因为只操作同一张 `sys_admin_user` 主表。
- Controller 只做参数绑定、`@Valid` 校验、读取当前管理员 ID 和响应组装。
- Service 负责权限外的领域规则、唯一性检查、事务和 Redis 会话编排。
- Mapper 只负责 `sys_admin_user` 的查询和写入。

## 5. 权限设计

### 5.1 Spring Security 角色

- 所有有效管理员都具有 `ROLE_ADMIN`。
- 当过滤器从数据库回查到 `is_super_admin = 1` 时，额外授予 `ROLE_SUPER_ADMIN`。
- `is_super_admin` 必须以数据库当前值为准，不从请求参数或 JWT 声明中信任该权限。
- `/api/admin/management/**` 要求 `ROLE_SUPER_ADMIN`。
- `/api/admin/auth/**` 保持要求 `ROLE_ADMIN`。

### 5.2 HTTP 行为

- 未登录访问受保护接口返回 HTTP 401。
- 普通管理员访问 `/api/admin/management/**` 返回 HTTP 403 和通用错误码 `1003`。
- Redis 认证状态不可用时返回 HTTP 503 和错误码 `1007`，禁止降级放行。
- 普通业务校验沿用当前项目契约，返回 HTTP 200，并在 `ApiResponse.code` 中携带业务错误码。

## 6. API 设计

### 6.1 超级管理员管理接口

| 方法 | 路径 | 请求模型 | 响应模型 | 说明 |
|---|---|---|---|---|
| `GET` | `/api/admin/management` | `AdminPageQueryDTO` | `AdminPageVO` | 分页和单一关键词搜索 |
| `GET` | `/api/admin/management/{id}` | 路径参数 | `AdminDetailVO` | 超级管理员和普通管理员均可出现在详情中 |
| `POST` | `/api/admin/management` | `CreateAdminDTO` | `AdminDetailVO` | 新增普通管理员 |
| `PUT` | `/api/admin/management/{id}` | `UpdateAdminDTO` | `AdminDetailVO` | 只允许编辑普通管理员资料和账号 |
| `PATCH` | `/api/admin/management/{id}/status` | `UpdateAdminStatusDTO` | 无 | 启用或禁用普通管理员 |
| `PUT` | `/api/admin/management/{id}/password` | `ResetAdminPasswordDTO` | 无 | 重置普通管理员密码 |
| `DELETE` | `/api/admin/management/{id}` | 路径参数 | 无 | 物理删除普通管理员 |

管理接口可以查询唯一超级管理员，但对 `is_super_admin = 1` 的目标执行编辑、状态修改、密码重置或删除时必须拒绝。超级管理员本人资料修改统一走个人接口，避免管理接口同时承担自我管理和他人管理两种语义。

### 6.2 管理员个人接口

| 方法 | 路径 | 请求模型 | 响应模型 | 说明 |
|---|---|---|---|---|
| `GET` | `/api/admin/auth/me` | 无 | `CurrentAdminVO` | 保留现有接口 |
| `PUT` | `/api/admin/auth/profile` | `UpdateAdminProfileDTO` | `CurrentAdminVO` | 修改本人账号和资料 |
| `PUT` | `/api/admin/auth/password` | `ChangePasswordDTO` | 无 | 保留现有接口，只修改本人密码 |

个人资料接口允许修改：

- `username`
- `realName`
- `mobile`
- `email`
- `avatarUrl`

个人资料接口不接收密码、状态、`isSuperAdmin`、部门或备注。部门和备注属于超级管理员维护的企业管理字段。

本人修改密码必须提供旧密码、新密码和确认密码。超级管理员重置普通管理员密码不需要目标管理员的旧密码。

## 7. DTO 字段与校验

### 7.1 `AdminPageQueryDTO`

- `page`：默认 `1`，最小 `1`。
- `size`：默认 `20`，范围 `1..100`。
- `keyword`：选填，查询前 `trim`；空字符串按无关键词处理。

搜索字段：

- `username`
- `real_name`
- `mobile`
- `email`

查询使用参数绑定，结果固定按 `id ASC` 排序。分页响应包含：

```json
{
  "list": [],
  "page": 1,
  "size": 20,
  "total": 1
}
```

### 7.2 `CreateAdminDTO`

必填字段：

- `username`
- `password`
- `confirmPassword`
- `realName`

选填字段：

- `mobile`
- `email`
- `avatarUrl`
- `departmentId`
- `remark`

后端固定字段：

- `status = 1`
- `isSuperAdmin = 0`
- `createdBy = 当前超级管理员 ID`
- `updatedBy = 当前超级管理员 ID`

### 7.3 `UpdateAdminDTO`

- `username`：必填。
- `realName`：必填。
- `mobile`：选填。
- `email`：选填。
- `avatarUrl`：选填。
- `departmentId`：选填。
- `remark`：选填。

该 DTO 不包含密码、状态或 `isSuperAdmin`。密码和状态分别使用专用接口。

### 7.4 `UpdateAdminProfileDTO`

- `username`：必填。
- `realName`：必填。
- `mobile`：选填。
- `email`：选填。
- `avatarUrl`：选填。

### 7.5 密码 DTO

- `ResetAdminPasswordDTO` 包含必填的 `newPassword` 和 `confirmPassword`。
- 本人改密继续使用 `ChangePasswordDTO`，包含必填的 `oldPassword`、`newPassword` 和 `confirmPassword`。
- 创建、本人改密、超级管理员重置密码均要求两次新密码一致。
- 新密码继续执行最小长度和 BCrypt UTF-8 最大 72 字节限制。

### 7.6 状态 DTO

- `UpdateAdminStatusDTO.status` 必填，只允许 `0` 或 `1`。
- `0` 表示禁用，`1` 表示正常。

### 7.7 字符范围与规范化

- `username` 只能包含 ASCII 英文字母和数字，正则为 `^[A-Za-z0-9]+$`；允许纯字母、纯数字或字母数字混合。
- `username` 不允许中文、下划线、连字符、空格或其他符号，长度范围为 `1..64`，后端不对它执行 `trim`。
- `realName` 不允许任何空白字符。
- 管理员密码只能包含 ASCII 可见字符，正则为 `^[!-~]+$`，即允许英文字母、数字和英文特殊符号，不允许空格、Tab、换行、中文、中文标点或其他非 ASCII 字符。
- 管理员密码不要求必须同时包含字母、数字和特殊符号；纯字母、纯数字或任意允许字符组合均可。
- 新密码、初始密码及其确认字段长度范围为 `8..72`；登录密码和旧密码必须非空且不超过 `72` 位。
- `username`、`realName` 或管理员密码不符合字符范围时直接返回校验错误 `1006`，不能通过 `trim` 静默修正。
- 手机号和邮箱允许输入首尾空格，后端统一 `trim`；内容中存在空白字符时格式校验失败。
- 邮箱接收大小写输入，但后端整体转换为小写后保存、查重和登录。
- `realName`、手机号、邮箱和其他字符串字段的最大长度不得超过数据库列长度。
- 手机号和邮箱可以同时为空；填写后必须满足格式并保持唯一。

## 8. VO 设计

### 8.1 `AdminListItemVO`

列表返回：

- `id`
- `username`
- `realName`
- `mobile`
- `email`
- `avatarUrl`
- `departmentId`
- `status`
- `isSuperAdmin`
- `lastLoginAt`
- `createdAt`

### 8.2 `AdminDetailVO`

详情在列表字段之外增加：

- `lastLoginIp`
- `passwordChangedAt`
- `remark`
- `createdBy`
- `updatedBy`
- `updatedAt`

### 8.3 认证响应

- `AdminLoginVO` 和 `CurrentAdminVO` 删除 `nickname`。
- 管理员登录响应和当前管理员响应使用 `realName` 作为展示姓名。
- 所有 VO 均不得返回密码哈希。

## 9. 唯一性规则

- `username`、非空手机号和非空邮箱必须在整个管理员表中唯一。
- 新增时分别检查 `username`、手机号和邮箱。
- 编辑时允许保留目标管理员自己的原值，但不得与其他管理员重复。
- 本人资料编辑和超级管理员编辑普通管理员必须使用同一套规范化与唯一性规则。
- 数据库唯一索引继续作为并发写入的最终保障；Service 负责将可预见冲突转换为明确业务错误。

## 10. Redis 会话与事务顺序

### 10.1 不需要全设备失效的修改

只修改以下字段时，直接在数据库事务中更新，不旋转会话版本：

- `realName`
- `avatarUrl`
- `departmentId`
- `remark`

### 10.2 必须全设备失效的修改

以下操作必须使目标管理员全部旧 Token 失效：

- 修改 `username`
- 修改手机号
- 修改邮箱
- 本人修改密码
- 超级管理员重置普通管理员密码
- 禁用或启用普通管理员
- 物理删除普通管理员

敏感操作统一复用现有流程：

```text
校验请求和目标状态
→ SessionService.beginMutation(ADMIN, targetId)
→ 执行 MySQL 写操作
→ MySQL 提交成功
→ SessionService.completeMutation(mutation)
```

- `beginMutation` 必须在 MySQL 写入前成功。Redis 失败时返回 HTTP 503，不执行数据库写入。
- `MUTATING` 状态立即阻止旧 Token 和并发新登录。
- MySQL 提交后才恢复新的 `ACTIVE:*` 版本，所有旧 Token 因版本不匹配失效。
- 若 MySQL 回滚，不自动恢复旧会话版本，账号保持不可认证直至 TTL 到期或后续人工处理，避免错误恢复旧 Token。
- 若 MySQL 已提交但 `completeMutation` 失败，账号保持 `MUTATING`，不得降级放行；接口报告认证状态不可用，数据库变更不做反向猜测性补偿。
- 删除成功后 Redis 版本键可以保留至 TTL 到期；数据库账号不存在，过滤器仍会拒绝认证。

启用也旋转会话版本，确保被禁用前遗留的旧 Token 不会因恢复状态而重新有效。

## 11. 数据库与 Mapper 调整

项目仍在开发阶段，本期直接修改 `V1__kasi_promotion.sql`，不新增 `V2`：

- 删除 `sys_admin_user.nickname`。
- 将 `sys_admin_user.real_name` 改为 `NOT NULL`。
- 管理员表删除 `deleted_at` 字段，删除操作只执行物理 `DELETE`。
- 推广用户表保留 `nickname`，但不保留 `deleted_at`；推广用户删除同样使用物理 `DELETE`。
- 同步修改 `test-schema.sql`。

`SysAdminUser`、Mapper XML、VO、登录响应和测试数据必须同步删除管理员 `nickname`；Entity、Mapper XML 和查询 SQL 同步删除 `deletedAt`/`deleted_at` 映射与过滤条件。

Mapper 增加最小必要能力：

- 按关键词统计数量。
- 按关键词分页查询，固定 `ORDER BY id ASC`。
- 更新资料和审计字段。
- 物理删除指定普通管理员。

现有管理员查询 SQL 不包含软删除过滤；物理删除接口必须直接执行 `DELETE`。所有 SQL 继续使用 MyBatis 参数绑定。

由于直接修改 V1，本地已有旧表的开发数据库需要重新建库，或由开发者手动删除 `nickname`、`deleted_at` 并补齐 `real_name` 后再运行新代码。

## 12. 错误码

继续以 `ErrorCode` 为唯一真理源，在管理员 `2xxx` 段增加：

| 错误码 | 枚举建议 | 信息 |
|---|---|---|
| `2006` | `ADMIN_MANAGEMENT_NOT_FOUND` | 管理员不存在 |
| `2007` | `ADMIN_USERNAME_DUPLICATE` | 登录账号已存在 |
| `2008` | `ADMIN_MOBILE_DUPLICATE` | 手机号已存在 |
| `2009` | `ADMIN_EMAIL_DUPLICATE` | 邮箱已存在 |
| `2010` | `ADMIN_SUPER_ADMIN_PROTECTED` | 不允许对超级管理员执行该操作 |
| `2011` | `ADMIN_PASSWORD_NOT_MATCH` | 两次输入的密码不一致 |

权限不足继续使用 `FORBIDDEN(1003)`，参数校验继续使用 `VALIDATION_ERROR(1006)`，Redis 失败继续使用 `AUTH_STATE_UNAVAILABLE(1007)`。

现有 `ADMIN_NOT_FOUND(2001)` 和 `ADMIN_PASSWORD_ERROR(2003)` 继续只用于登录认证，避免管理接口返回“账号或密码错误”这种不准确的信息。

## 13. 测试设计

认证和管理员管理测试继续继承 `BaseAuthTest`，使用真实 Spring Security FilterChain、H2 和随机端口嵌入式 Redis。

唯一超级管理员的固定测试凭据为：

- 测试账号：`kasiadmin`
- 测试密码：`kasi123456`

该凭据只用于 H2 测试数据、测试辅助方法和认证测试请求，不得写入生产 Flyway 迁移脚本，也不构成生产初始管理员方案。

### 13.1 权限测试

- 未登录访问管理接口返回 HTTP 401。
- USER Token 访问管理接口返回 HTTP 403。
- 普通 ADMIN Token 访问管理接口返回 HTTP 403。
- 唯一超级管理员可以访问管理接口。
- JWT 中不增加可伪造的超级管理员声明；权限以数据库回查结果为准。

### 13.2 查询测试

- 分页默认值为 `page=1`、`size=20`。
- `size > 100` 和非法页码返回校验错误。
- 无关键词时按 `id ASC` 返回。
- 单一关键词可匹配账号、真实姓名、手机号和邮箱。
- 列表和详情不返回密码。
- 不存在的管理员详情返回 `2006`。

### 13.3 新增和编辑测试

- 超级管理员成功新增普通管理员，密码以 BCrypt 保存。
- 新增记录固定 `status=1`、`is_super_admin=0`。
- `username` 只接受 ASCII 字母和数字；中文、下划线、连字符、空格和其他符号被拒绝。
- 管理员密码接受 ASCII 字母、数字和英文特殊符号，不强制字符类别组合；空白字符、中文、中文标点和其他非 ASCII 字符被拒绝。
- 新密码和初始密码覆盖 `8..72` 长度边界，登录密码和旧密码覆盖 `72` 位上限。
- 邮箱整体小写保存。
- 重复账号、手机号和邮箱分别返回对应错误码。
- 普通管理员资料编辑成功。
- 超级管理员不能通过管理接口编辑唯一超级管理员。
- 本人通过个人资料接口修改资料成功。
- 普通管理员和超级管理员都不能通过个人资料接口修改状态或 `isSuperAdmin`。

### 13.4 状态、密码和删除测试

- 禁用普通管理员后不能登录，旧 Token 返回 401。
- 重新启用后可以使用原密码登录，禁用前 Token 仍返回 401。
- 超级管理员不能禁用或启用自己。
- 超级管理员重置普通管理员密码后，新密码可登录，旧密码不可登录，旧 Token 返回 401。
- 本人修改密码继续验证旧密码，并使本人全部旧 Token 失效。
- 物理删除普通管理员后记录不存在，旧 Token 返回 401。
- 删除后原账号、手机号和邮箱可以重新用于新增管理员。
- 超级管理员不能删除自己，也不能通过管理接口重置自己的密码。

### 13.5 Redis 失败和事务测试

- 修改登录标识前 Redis 失败，返回 503，MySQL 资料不变。
- 禁用、启用、重置密码和删除前 Redis 失败，返回 503，MySQL 状态不变。
- MySQL 写入失败后 Redis 保持不可认证状态，不恢复旧 Token。
- 只修改非登录资料时不依赖 Redis，会话继续有效。

### 13.6 结构与回归测试

- `AdminManagementService` 是接口，Spring 实现位于 `service.impl.AdminManagementServiceImpl`。
- Controller 不直接依赖 Mapper。
- 管理员 Entity、DTO、VO、SQL 和测试结构中不再存在 `nickname` 或软删除字段；删除接口使用物理 `DELETE`。
- 现有管理员登录、退出、本人信息和密码修改测试继续通过。
- 现有用户认证与 Redis 安全测试继续通过。

## 14. 验收命令

在 Java 25 环境下执行：

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
git diff --check
```

只有最新完整输出显示测试零失败、编译成功且差异检查通过，才可以声明实现完成。
