# 会话异常恢复与媒体下载超时设计

## 目标

本次只修复两个已确认问题：

1. 敏感数据库操作在 Redis 状态切换为 `MUTATING` 或 `PROCESSING` 后失败时，确保事务回滚能够恢复可用状态。
2. 普通 HTTP 媒体下载增加可配置的连接超时和流式读取超时，避免下载线程无限阻塞。

不处理下载任务持久化恢复、跨域重定向、HLS 子资源校验、CSV 导出或平台主密钥配置。

## 会话与重置凭证恢复

### `MUTATING`

保留 `SessionService.beginMutation` 与 `registerMutationCompletion` 的现有职责。所有事务调用方必须在 `beginMutation` 成功后立即注册事务完成回调，再执行任何可能抛出异常的数据库写操作。

事务提交或回滚后都调用现有 nonce 校验恢复逻辑，把账号版本恢复为新的 `ACTIVE:*`。数据库写失败时不保留 `MUTATING` 状态。

### `PROCESSING`

密码重置 Token 从 `READY` 原子预占为 `PROCESSING` 后立即注册事务同步：

- 事务提交后调用 `completeToken` 消费 Token。
- 事务回滚后调用 `restoreReady` 恢复 Token。

删除用户不存在、数据库更新行数异常等分支中的手动恢复，统一由事务完成状态决定，避免遗漏新的异常路径。

## 媒体下载超时

普通媒体下载使用能够分别设置连接超时和流式读取超时的 HTTP 连接实现：

- `app.drama.download.connect-timeout`，默认 `10s`。
- `app.drama.download.read-timeout`，默认 `30s`。

读取超时表示一次阻塞读取允许等待的最长时间，不限制完整大文件必须在 30 秒内完成。HLS 的 FFmpeg 总执行上限继续保持 30 分钟，本次不改变其网络访问模型。

连接、响应状态、大小限制、临时文件删除及 403/404 刷新语义保持不变。超时按 `IOException` 进入现有三次下载重试和脱敏失败处理。

## 测试与验收

先添加能够在当前实现下失败的测试，再修改生产代码：

- Mapper 写入抛异常时，验证事务回滚仍触发 `MUTATING` 恢复。
- 密码重置流程在预占后异常时，验证 Token 恢复为 `READY`；成功提交后仍被消费。
- HTTP 服务延迟响应头或响应体时，验证连接/读取在配置时间内失败；正常流式下载和 403/404 行为保持通过。

完成后运行相关聚焦测试、完整 Maven 测试套件以及 `git diff --check`。
