# 短剧 API 配置前端设计

日期：2026-08-18

## 目标

在管理后台“系统配置”一级菜单下提供“短剧 API 配置”二级菜单。页面参考设置类后台的 Tabs 表单结构，不使用平台查询表格，让管理员直接按平台配置 API 接入信息。

## 页面结构

- 一级菜单：系统配置。
- 二级菜单：短剧 API 配置。
- 路由：`/system-config/drama-api`；旧 `/provider-management` 自动跳转到新路由。
- 页面标题：短剧 API 配置。
- 顶部 Tabs：每个短剧平台一个 Tab，当前只有 GoodShort。
- 表单字段：接口 URL、PID、KEY、启用状态。
- 底部操作：提交、连接测试。

不在页面展示平台能力、接入名称、币种、更新时间和平台列表表格。平台能力仍由后端适配器声明，供后续业务模块判断，不作为管理员配置项。

## 权限

- 普通管理员可进入页面查看配置，但表单为只读，不显示提交和连接测试操作。
- 超级管理员可以提交配置和执行连接测试。
- 前端权限只改善交互，后端继续使用 `ROLE_SUPER_ADMIN` 作为最终保护。

## 数据契约

页面使用现有三个统一接口：

- `GET /api/admin/drama/providers`
- `PUT /api/admin/drama/providers/{providerId}/connection`
- `POST /api/admin/drama/providers/{providerId}/connection/test`

保存请求只包含：

```json
{
  "baseUrl": "https://api.novelopen.com/creek",
  "partnerId": "平台提供的PID",
  "apiKey": "平台提供的KEY",
  "status": 1
}
```

已有 KEY 时允许省略 `apiKey`，后端保留原密文。响应只返回 `credentialConfigured`，不返回 KEY 明文、密文或掩码。

## 后端约束

- `base_url` 保存到 `short_drama_connection`。
- PID 保存为 `partner_id`。
- KEY 使用 AES-GCM 加密后保存为 `api_key_ciphertext`。
- 连接测试使用数据库中保存的 URL、PID 和解密后的 KEY。
- GoodShort 具体接口路径、签名方式和请求结构仍由 GoodShort 适配器维护。
- 新平台复用同一管理页面和数据库结构；如果第三方协议不同，只新增对应平台适配器。

## 验收

- 菜单层级、Tabs、表单和底部操作符合设置页结构。
- URL、PID、KEY 首次配置必填，已有 KEY 不回填。
- 保存时不提交隐藏的接入名称、币种或平台能力。
- 未配置、平台停用或接入停用时不能测试连接。
- 桌面和移动视口无重叠，控制台无错误。
