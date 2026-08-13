# 推广用户管理设计规格

日期：2026-08-13

## 1. 目标

在现有推广用户认证模块上增加管理员可用的推广用户管理 CRUD，并将推广用户登录模型统一为“手机号或邮箱”，不再保留独立登录账号 `username`。

本期由超级管理员和普通管理员共同访问管理接口，权限只要求现有 `ROLE_ADMIN`，不建设 RBAC、不引入新的认证框架或独立身份服务。继续复用现有 JWT + Redis 会话版本机制处理敏感状态变更。

## 2. 范围

### 2.1 本期包含

- 超级管理员和普通管理员分页查询推广用户。
- 使用一个关键词搜索用户编号、手机号、邮箱、昵称和真实姓名。
- 查看推广用户详情。
- 管理员直接新增推广用户，不需要验证码。
- 管理员编辑推广用户资料和联系方式。
- 管理员启用、禁用推广用户。
- 管理员重置推广用户密码。
- 管理员物理删除推广用户。
- 公开注册继续支持手机号或邮箱验证码注册。
- 登录、忘记密码和重置密码继续使用手机号或邮箱作为登录标识。
- 推广用户可以同时拥有手机号和邮箱，二者均可登录。

### 2.2 本期不包含

- 独立推广用户账号或登录名字段。
- 推广用户角色、权限点、部门权限或 RBAC。
- 用户分组、批量导入导出、批量操作和多设备管理页面。
- 软删除、回收站或恢复站。删除统一使用物理 `DELETE`。
- 修改推广用户 `userNo`。它是系统内部稳定编号，不是登录账号。
- 修改管理员认证和管理员表结构。

## 3. 核心业务规则

### 3.1 登录标识

- `username` 从推广用户实体、数据库表、Mapper、DTO、VO、认证 Service 和测试中移除。
- `userNo` 继续保留，用于内部识别和展示，不参与登录。
- `mobile` 和 `email` 都是可选登录标识，但至少一个必须存在。
- 用户可以同时填写手机号和邮箱；手机号和邮箱分别全局唯一。
- 登录请求输入手机号或邮箱，后端按对应字段匹配；不再按 `username` 匹配。
- 邮箱输入统一 `trim` 后整体转小写；手机号输入统一 `trim`。中间含空白字符时校验失败。
- 修改手机号或邮箱后，旧值立即不能登录，新值可以登录。
- 清空联系方式时，后端必须保证修改后手机号和邮箱不会同时为空。

### 3.2 管理员权限

- `/api/user/management/**` 只要求 `ROLE_ADMIN`。
- 超级管理员和普通管理员均可访问推广用户管理接口。
- 推广用户 Token 访问管理接口返回 HTTP 403。
- 未登录访问管理接口返回 HTTP 401。

### 3.3 新增用户

管理员新增不需要短信或邮箱验证码。后端固定设置：

- `status = 1`
- `register_source = ADMIN`
- `userNo` 由后端根据插入后的自增 ID 生成
- `password` 使用 BCrypt 保存
- `mobile`、`email` 至少一个非空

昵称、真实姓名、头像、备注均为资料字段；密码和确认密码为必填初始凭据。管理员请求不能传入 `userNo`、`status` 或 `registerSource` 作为可控值。

### 3.4 删除语义

- 推广用户删除采用物理删除，直接执行 `DELETE FROM promotion_user`。
- 删除前必须使目标用户的全部 Redis 会话失效；Redis 失败时禁止执行数据库删除。
- 删除后原手机号、邮箱和其他唯一字段可以重新使用。
- 删除后目标用户的数据库记录不存在，旧 Token 通过数据库回查和 Redis 校验均不能继续认证。

## 4. 分层与代码结构

在现有 `user` 模块内增加管理职责，保持传统 Java 分层：

```text
user/
├─ controller/
│  ├─ UserAuthController.java
│  └─ UserManagementController.java
├─ service/
│  ├─ UserAuthService.java
│  ├─ UserManagementService.java
│  └─ impl/
│     ├─ UserAuthServiceImpl.java
│     └─ UserManagementServiceImpl.java
├─ dto/
├─ vo/
├─ entity/
│  └─ PromotionUser.java
└─ mapper/
   └─ PromotionUserMapper.java
```

- `UserAuthController` / `UserAuthService` 继续负责公开注册、登录、退出、当前用户、本人改密和忘记密码流程。
- `UserManagementController` / `UserManagementService` 负责管理员对推广用户的 CRUD。
- 两套 Service 复用 `PromotionUserMapper`，因为只操作同一张 `promotion_user` 主表。
- Controller 只负责参数绑定、`@Valid`、当前管理员上下文和响应组装。
- Service 负责联系方式规则、密码业务规则、事务、唯一性和 Redis 会话编排。
- Mapper 只负责 `promotion_user` 的查询和写入，不承载业务逻辑。
- 请求模型使用 `*DTO`，响应模型使用 `*VO`，数据库对象使用 `PromotionUser` Entity。

## 5. API 设计

### 5.1 推广用户管理接口

| 方法 | 路径 | 请求模型 | 响应模型 | 说明 |
|---|---|---|---|---|
| `GET` | `/api/user/management` | `UserPageQueryDTO` | `UserPageVO` | 分页和单一关键词搜索，管理员访问 |
| `GET` | `/api/user/management/{id}` | 路径参数 | `UserDetailVO` | 查询用户详情 |
| `POST` | `/api/user/management` | `CreateUserDTO` | `UserDetailVO` | 管理员直接创建启用用户 |
| `PUT` | `/api/user/management/{id}` | `UpdateUserDTO` | `UserDetailVO` | 编辑资料和联系方式 |
| `PATCH` | `/api/user/management/{id}/status` | `UpdateUserStatusDTO` | 无 | 启用或禁用用户 |
| `PUT` | `/api/user/management/{id}/password` | `ResetUserPasswordDTO` | 无 | 重置用户密码 |
| `DELETE` | `/api/user/management/{id}` | 路径参数 | 无 | 物理删除用户 |

默认分页为 `page=1,size=20`，`size` 最大 100，结果固定按 `id ASC`。关键词搜索以下字段：`user_no`、`mobile`、`email`、`nickname`、`real_name`。列表和详情均不得返回密码哈希。

### 5.2 公开认证接口调整

保留现有路径，但认证实现不再读写 `username`：

- `POST /api/user/auth/register`：根据手机号或邮箱注册，只写对应联系方式。
- `POST /api/user/auth/register/code`：继续由后端固定 `REGISTER` 场景。
- `POST /api/user/auth/login`：只接受手机号或邮箱作为 `account`。
- `GET /api/user/auth/me`：返回 `userNo`、联系方式和用户资料，不返回 `username`。
- 忘记密码和重置密码继续使用手机号或邮箱及 Redis 重置 Token。

公开注册一次仍只提交一个联系方式；管理员新增可以同时提交手机号和邮箱。

## 6. DTO 与 VO 设计

### 6.1 `UserPageQueryDTO`

- `page`：默认 `1`，最小 `1`。
- `size`：默认 `20`，范围 `1..100`。
- `keyword`：选填，查询前 `trim`，空字符串按无关键词处理。

分页响应包含 `list`、`page`、`size`、`total`。

### 6.2 `CreateUserDTO`

必填：`password`、`confirmPassword`、`nickname`，以及手机号或邮箱至少一个。选填：`realName`、`avatarUrl`、`remark`。后端固定 `status=1`、`registerSource=ADMIN`，不接受前端 `userNo`。

### 6.3 `UpdateUserDTO`

包含必填 `nickname` 和选填 `realName`、`mobile`、`email`、`avatarUrl`、`remark`。更新后手机号和邮箱不能同时为空；不包含密码、状态、`userNo` 或 `registerSource`。

### 6.4 密码和状态 DTO

- `ResetUserPasswordDTO`：必填 `newPassword`、`confirmPassword`。
- `UpdateUserStatusDTO.status`：必填，只允许 `0` 或 `1`。
- 管理员重置密码不要求目标用户旧密码。
- 密码使用 ASCII 可见字符规则 `^[!-~]+$`，新密码长度 `8..72`，并执行 UTF-8 72 字节限制。

### 6.5 `UserListItemVO` 与 `UserDetailVO`

列表返回 `id`、`userNo`、`nickname`、`realName`、`mobile`、`email`、`avatarUrl`、`status`、`registerSource`、`lastLoginAt`、`createdAt`；详情额外返回 `lastLoginIp`、`remark`、`updatedAt`。不返回 `password`、删除时间或内部认证 Token。

## 7. 校验与唯一性

- 手机号和邮箱分别全局唯一；数据库唯一索引作为并发写入最终保障。
- 新增和编辑时，Service 先检查规范化后的手机号、邮箱；数据库 `DuplicateKeyException` 必须转换为明确业务错误，不返回 500。
- 手机号和邮箱首尾空格被规范化；邮箱整体转小写；中间空白字符导致校验失败。
- 新增和编辑必须保证手机号、邮箱至少一个非空。
- `nickname`、`realName`、`avatarUrl`、`remark` 的长度不能超过数据库列长度；昵称不能为空且不允许只由空白组成。
- `userNo` 由后端生成，格式继续使用 `KS` 加六位自增 ID，例如 `KS000001`。
- 删除后不保留唯一值占位记录，因此联系方式可以复用。

建议错误码：`3011 USER_MANAGEMENT_NOT_FOUND`、`3012 USER_CONTACT_REQUIRED`、`3013 USER_PASSWORD_NOT_MATCH`。已有 `3006`、`3007`、`3002`、`3003` 继续复用；`USER_USERNAME_DUPLICATE(3008)` 随 `username` 字段移除并清理。

## 8. Redis 会话与事务顺序

只修改 `nickname`、`realName`、`avatarUrl`、`remark` 时不旋转会话。修改手机号、邮箱、密码、状态或物理删除必须调用 `SessionService.beginMutation(USER, userId)`，统一顺序为：

```text
校验请求和目标状态
→ SessionService.beginMutation(USER, userId)
→ 执行 MySQL 写操作
→ MySQL 提交成功
→ SessionService.completeMutation(mutation)
```

Redis 失败返回 HTTP 503 和 `AUTH_STATE_UNAVAILABLE(1007)`，不执行数据库写入。MySQL 回滚或 `completeMutation` 失败时不自动恢复旧 Token，保持不可认证状态。禁用和重新启用都旋转会话版本，防止旧 Token 在重新启用后复活。

## 9. 数据库与 Mapper 调整

直接修改 `V1__kasi_promotion.sql` 和 `test-schema.sql`，不新增 `V2`：

- 从 `promotion_user` 删除 `username` 列及唯一索引。
- 保留 `user_no`、`nickname`、`real_name`、`mobile`、`email`、`avatar_url`、`status`、`register_source`、`remark`、`created_at` 和 `updated_at`。
- 推广用户表不保留 `deleted_at`，管理删除只执行物理 `DELETE`。
- 注册 Mapper 不再插入 `username`；登录查询只按 `mobile` 或 `email` 匹配。
- 新增分页统计、分页查询、资料更新、状态更新、密码更新和物理删除 Mapper 方法。
- 所有 SQL 使用 MyBatis 参数绑定，列表固定 `ORDER BY id ASC`。

已有本地数据库需要重新建表，或手动删除 `username` 及其唯一索引后再运行新代码。

## 10. 测试设计

测试继续继承 `BaseAuthTest`，使用真实 Spring Security FilterChain、H2 和随机端口嵌入式 Redis。

- 权限：未登录 401；普通管理员和超级管理员访问 200；推广用户访问 403。
- 结构：`username` 不再出现在推广用户 Entity、DTO、VO、Mapper SQL、数据库 schema 和认证响应中；`UserManagementService` 为接口，实现位于 `user.service.impl`。
- 查询：默认分页、单关键词搜索、`id ASC`、超大页码不溢出、详情不存在返回 `3011`、列表和详情不返回密码。
- 新增：无需验证码，可只填手机号、只填邮箱或同时填写两者，固定启用和 `registerSource=ADMIN`，密码 BCrypt 保存。
- 编辑：资料字段修改不使 Token 失效；手机号或邮箱变化使旧 Token 失效，旧联系方式不能登录，新联系方式可以登录；联系方式不能同时清空。
- 状态、密码、删除：禁用/启用、重置密码、物理删除均使旧 Token 失效；删除后记录不存在且联系方式可复用。
- 失败路径：Redis 失败时 MySQL 不变，MySQL 失败不恢复旧 Token，并发手机号/邮箱冲突转换为业务错误而非 500。
- 现有注册、登录、退出、忘记密码和用户会话安全测试继续通过。

## 11. 验收命令

在 Java 25 环境下执行：

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-25.0.3'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\mvnw.cmd test
.\mvnw.cmd -DskipTests compile
git diff --check
```

只有最新完整输出显示测试零失败、编译成功且差异检查通过，才可以声明实现完成。
