# GoodShort 短剧目录完整字段设计

## 目标

将 GoodShort 短剧列表接口文档中的全部请求和返回字段按本地领域命名转换后落库，并通过现有管理员和用户短剧目录接口返回。

## 字段映射

```text
bookId        -> external_drama_id / externalDramaId
bookName      -> title
bookNameZh    -> title_zh / titleZh
bookCover     -> cover_url / coverUrl
labelNames    -> label_names / labelNames（JSON 文本）
introduce     -> description
typeTwoName   -> category_name / categoryName
language      -> language
rank          -> remote_rank / remoteRank
showStatus    -> remote_show_status / remoteShowStatus
novelType     -> novel_type / novelType
novelSubType  -> novel_sub_type / novelSubType
ctime         -> remote_created_at / remoteCreatedAt
utime         -> remote_updated_at / remoteUpdatedAt
```

`created_at` 和 `updated_at` 继续表示本地记录时间，不被远端 `ctime`/`utime` 覆盖。`bookId` 继续作为外部标识，内部主键保持自增 ID。

## 增量请求

适配器对外部接口发送 `utimeStart` 和可选 `utimeEnd`，格式为 `yyyy-MM-dd HH:mm:ss`。现有检查点仍保存内部毫秒水位，发送前转换为文档要求的日期字符串；响应记录的最大 `utime` 更新检查点。

## 兼容性

新增字段均允许为空，使用 V16 迁移兼容既有数据。现有 `coverUrl`、`description`、`language`、`remoteShowStatus`、`remoteUpdatedAt` 等 JSON 字段保持不变；仅新增完整字段，不删除旧字段或改变内部主键。

## 验收

- 文档样例中的全部短剧字段能被 DTO 接收并映射到领域记录。
- 同步后数据库保存全部字段，重复同步按外部 ID 更新。
- 管理端和用户端列表/详情返回新增字段。
- 增量请求包含 `utimeStart`，可选 `utimeEnd`，不再发送未在文档中定义的 `updateTime`。
