# 历史兼容残留清理设计

## 目标

在不改变现有 Controller、Service、Mapper 分层和认证行为的前提下，删除已经没有调用方、与当前 JWT + Redis 会话模型冲突，或只为旧数据库兼容而保留的代码与配置。

## 清理范围

- 删除 `TokenService` 三参数 `generateToken` 重载。该重载自行生成 `jti` 和 `sessionVersion`，但不创建 Redis 会话，生成的 Token 无法通过当前认证链。
- 删除 `TokenService.validateToken`。受保护请求直接调用 `parseToken` 获取认证上下文，该布尔包装方法没有调用方。
- 删除 `PromotionUserMapper.findByUserNo` 及对应 MyBatis XML 查询。`user_no` 继续用于内部编号、列表展示和关键词搜索，但当前没有按编号单独查询的业务入口。
- 删除未被生产代码或测试引用的错误码：`SUCCESS`、`BAD_REQUEST`、`NOT_FOUND`、`USER_ACCOUNT_REQUIRED`、`VERIFICATION_CODE_EXPIRED`、`VERIFICATION_CODE_ALREADY_USED`、`RESET_TOKEN_EXPIRED`、`RESET_TOKEN_ALREADY_USED`。
- 删除 `spring.flyway.baseline-on-migrate=true`。项目仍处于可重建数据库阶段，没有需要兼容的生产 Flyway 历史；非空且没有 Flyway 元数据的数据库应明确失败，不能静默基线并跳过 V1。
- 将测试基类中 Jackson 3 已废弃的 `JsonNode.asText()` 替换为 `stringValue()`，消除测试编译期废弃 API 警告。

## 保留范围

- 保留 `department_id`、`created_by`、`updated_by`、`password_changed_at`、`remark` 和 `register_source`，这些字段仍有 DTO、VO、Service、Mapper 或测试闭环。
- 保留测试数据 `legacy_username`，它用于证明推广用户旧用户名格式不能登录。
- 不修改 Controller 路径、响应结构、Redis Key、JWT 声明、数据库业务字段或现有错误码数值。

## 验证

新增结构测试，约束上述 API、Mapper、错误码和 Flyway 兼容配置不再出现。随后运行 Java 25 定向测试、完整测试、跳过测试编译、Flyway `validate` 和 `git diff --check`。
