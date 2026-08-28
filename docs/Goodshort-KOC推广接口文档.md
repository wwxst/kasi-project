# Goodshort-KOC推广接口文档

插入链接

# 版本记录

| **文档版本** | **更新内容** | **更新时间** |
| --- | --- | --- |
| v1.0.5 | 剧单返回参数新增对应剧的类型字段，包括原创/翻译，字幕/配音 | 2025-11-05 |
| v1.0.4 | 剧单返回参数新增对应剧的中文名，字段bookNameZh | 2025-10-15 |
| v1.0.3 | 1.生成口令接口上行新增媒体<br>2.订单列表接口下行渠道号支持口令媒体格式<br>3.新增账号报备接口 | 2025-09-04 |
| v1.0.2 | 订单列表接口下行新增渠道号 | 2025-08-27 |
| v1.0.1 | 新增订单转化明细接口 | 2025-07-04 |
| v1.0.0 | 创建文档 | 2025-07-01 |

# 接入指南

## 总体流程

*   **机构端与平台对接获取pid及签名密钥**

*   **机构的达人在机构端APP或小程序选择一部短剧生产口令然后去海外平台发布推广**

*   **通过口令进入的用户后续产生的订单会通过接口提供给合作方，订单接口会原样返回对应的达人标识。**


## 注意事项

重要参数缺一不可：

*   **pid：机构唯一标识ID,等待对接人员开通机构权限并发回对接参数key。**

*   bookId：短剧ID

*   customParams: 达人/博主标识，限制最大长度64。通过此参数来区分下面的博主/达人。

*   timestamp: 请求时间戳, 单位：**毫秒**。

*   key：生成签名的密钥，联系对接人员获取。请注意密钥的保密，如发生密钥泄漏请立即联系对接人员更换新密钥

*   sign: **签名(在http请求 header 中传递)**


## 接口域名

https://api.novelopen.com/creek/

# 签名算法

## 签名生成的步骤

### 第一步

设所有发送或者接收到的数据为集合M，将集合M内非空参数值的参数按照参数名ASCII码从小到大排序（字典序），使用URL键值对的格式（即key1=value1&key2=value2…）拼接成字符串stringA。

特别注意以下重要规则：

◆ 参数名ASCII码从小到大排序（字典序）；<br>
◆ 如果参数的值为空不参与签名；<br>
◆ 参数名区分大小写；<br>
◆ sign参数不参与签名

### 第二步

在stringA最后拼接上key(密钥)得到stringSignTemp字符串，并对stringSignTemp进行MD5运算，再将得到的字符串所有字符转换为大写，得到sign值。 注意：密钥的长度为32个字节。

◆ key获取：联系对接人员

举例：

假设请求参数如下：

```json
pid： 123456

timestamp： 1681810530092

pageNo： 1

pageSize：10
```

第1步：对参数按照key=value的格式，并按照参数名ASCII字典序排序如下：

```json
pageNo=1&pageSize=10&pid=123456&timestamp=1681810530092
```

第2步：拼接key：

```json
String stringSignTemp = "pageNo=1&pageSize=10&pid=123456&timestamp=1681810530092&key=aaabbbccc" //注：key为密钥 联系对接人员获取

计算签名：MD5(stringSignTemp).toUpperCase()="973FB9A689D3924CAC1967EF6E0BD012" //注：MD5签名，再转为大写。
```

# 数据接口

## 短剧列表

##### 简要描述

*   短剧列表全量


##### 请求URL

\* 全量接口POST https://api.novelopen.com/creek/open/book/initBooks

\* 增量接口POST https://api.novelopen.com/creek/open/book/incrementBooks

##### 限流频率

*   100次/min


短剧列表更新频率：初始一次。<br>
建议：首次初始化全部短剧使用全量接口，后续更新数据使用增量接口。

##### 请求参数

| **参数名** | **必选** | **类型** | **说明** |
| --- | --- | --- | --- |
| pid | 是 | string | 合作伙伴ID |
| timestamp | 是 | string | 当前时间戳(签名用) |
| sign | 是 | string | 签名，生成规则如上 |
| pageNo | 是 | int | 第几页 |
| pageSize | 是 | int | 每页几条，最大限制50 |
| language | 是 | string | 语言：<br>ENGLISH - 英语<br>SPANISH - 西班牙语<br>PORTUGUESE - 葡萄牙语<br>DEUTSCH - 德语<br>FRENCH - 法语<br>BAHASA\_INDONESIA - 印尼语<br>KOREAN - 韩语<br>ARAB - 阿拉伯语<br>THAI - 泰语<br>JAPANESE - 日语<br>TRADITIONAL\_CHINESE - 中文（繁体）<br>POLISH - 波兰语<br>TURKISH - 土耳其语<br>等 |
| utimeStart | 增量接口必填、初始接口不需要 | date | 短剧更新时间。查询开始时间，精确到秒，不传默认空.格式: 2023-04-18 10:30:00 【**重要建议**：首次初始化全部短剧后，每天根据此字段增量拉取】 |
| utimeEnd | 增量接口选填 | date | 短剧更新时间。查询开始时间，精确到秒，不传默认空.格式: 2023-04-18 10:30:00 【**重要建议**：首次初始化全部短剧后，每天根据此字段增量拉取】 |

##### 返回参数

| **参数名** | **类型** | **说明** |
| --- | --- | --- |
| bookId | string | 短剧id |
| bookName | string | 短剧名字 |
| bookNameZh | string | 短剧中文名 |
| bookCover | string | 封面地址 |
| labelNames | string\[\] | 短剧标签 |
| introduce | string | 短剧介绍 |
| typeTwoName | string | 分类名 |
| language | string | 短剧语言 |
| rank | int | 排序（从小到大） |
| showStatus | int | 有效状态， 0 无效 1 有效 |
| novelType | string | 短剧类型<br>ORIGINAL-原创<br>TRANSLATION-翻译 |
| novelSubType | int | 短剧类型子类型<br>0-字幕<br>1-配音 |
| ctime | date | 创建时间 |
| utime | date | 更新时间 |

**请求示例**

```json
curl --location --request POST 'https://api.novelopen.com/creek/open/book/initBooks' \

--header 'sign: XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX' \
--header 'Content-Type: application/json' \
--header 'Accept: */*' \
--header 'Host: api.novelopen.com' \
--header 'Connection: keep-alive' \
--data-raw '{
    "pageNo":1,
    "pageSize":15,
    "language":"ENGLISH",
    "pid": "XXXXX",
    "timestamp": 1731563911924
}'
```

##### 返回示例

```json
"data": {
  "current": 1,
  "size": 1,
  "pages": 15,
  "total": 15,
  "records": [
    {
      "bookId": "zYnr2wM1aEHp1h6OL/m1ng==",
      "bookName": "JP 日语 005",
      "bookNameZh": "JP 日语 005",
      "bookCover": "https://acf-hot.xssky.com/book/202408/cover-Ukko5yyDBt.jpg",
      "labelNames": [
        "sor-日语2",
        "c-日语2",
        "i-日语2"
      ],
      "typeTwoName": "",
      "language": "JAPANESE",
      "rank": 0,
      "introduce": "You can visit https://www.goodnovel.com, the GoodNovel writing platform can realize your creative dream for you, and use your words to connect readers all over the world.",
      "showStatus": 0,
      "ctime": "2024-12-28T14:02:10.000+0000",
      "utime": "2024-12-28T14:02:10.000+0000"
    }
  ]
},
"status": 0,
"message": "success",
"timestamp": 1724068952256,
"ip": "",
"region": null,
"path": "/open/book/incrementBooks",
"success": true
}
```
# 免费内容

##### 简要描述

*   免费内容


##### 请求URL

*   POST https://api.novelopen.com/creek/open/book/freeContent


##### 限流频率

*   100次/min


##### 请求参数

| **参数名** | **必选** | **类型** | **说明** |
| --- | --- | --- | --- |
| pid | 是 | string | 合作伙伴ID |
| timestamp | 是 | string | 当前时间戳，毫秒 |
| sign | 是 | string | 签名(生成规则) |
| bookId | 是 | string | 短剧id |

#####   返回参数

| **参数名** | **类型** | **说明** |
| --- | --- | --- |
| chapterName | string | 剧集名 |
| content | string | 视频链接 |

#####   返回示例

```json
{
    "data": [
        {
            "chapterName": "Chapter 1",
            "content": "https://xx.goodshort.com/234/344351243/xxx.m3u8"
        }
    ],
    "status": 0,
    "message": "success",
    "timestamp": 1725347331456,
    "ip": "",
    "region": null,
    "path": "/open/book/freeContent",
    "success": true
}
```

# 订单列表

##### 简要描述

*   订单列表


##### 请求URL

*   POST https://api.novelopen.com/creek/open/partner/orders


##### 限流频率

*   100次/min

*   建议：每天早上 8 点后执行一次。startDate传入当天的开始时间 endDate 传入当天的结束时间

*   举例：2025-07-01 进行订单数据拉取 startDate 传入 2025-07-01 00:00:00 endDate  传入2025-07-01  23:59:59


##### 请求参数

| **参数名** | **必选** | **类型** | **说明** |
| --- | --- | --- | --- |
| pid | 是 | string | KOC机构ID |
| timestamp | 是 | string | 当前时间戳，毫秒 |
| sign | 是 | string | 签名 |
| pageNo | 是 | int | 第几页 |
| pageSize | 是 | int | 每页几条，最大限制500 |
| startDate | 否 | string | 查询开始时间，精确到秒,不传默认当天0点。格式：2023-04-18 00:00:00 |
| endDate | 否 | string | 查询结束时间, 精确到秒，不传默认当天23:59:59。格式：2023-04-19 23:59:59 |

##### 返回参数

| **参数名** | **类型** | **说明** |
| --- | --- | --- |
| orderId | string | 订单id |
| userId | string | 下单用户id |
| payMoney | Integer | 订单总金额，以美分为单位。（分成前的金额） |
| payTime | string | 支付时间 例：2023-04-17 20:01:15 |
| payStatus | Integer | 支付状态。<br>0-未支付 1-已支付 **3-退款** |
| customParams | string | 自定义参数，原样返回(一般是达人id) |
| bookId | string | 产生充值的短剧id |
| searchCode | string | 搜索口令 |
| channelCode | string | 渠道号<br>1.渠道号是GSSM+数字格式，属于历史提供的老链接归因的支付订单<br>2.渠道号是GRKOC00001格式，属于通过koc落地页、onelink、搜索code归因的支付订单<br>3.渠道号是GRKOC+两位机构代码字母+媒体编码缩写(TT、FB、GG)+数字格式，属于通过koc落地页、onelink、搜索code归因的支付订单，可以按渠道号直接区分投放媒体 |
| pid | string | KOC机构ID |
| utime | string | 更新时间 例：2023-04-17 20:01:15 |

##### 返回示例

```json
{
  "data": {
    "records": [
      {
        "userId": 20031995,
        "orderId": "pSkYKM4IvI3/DGuIUw7Rag==",
        "payMoney": 99,
        "payTime": "2024-08-19 15:55:30",
        "payStatus": 1,
        "customParams": "05310531",
        "bookId": "veXpFK9K6FkJ7jLp1w+HVA==",
        "searchCode": "21302",
        "channelCode": "KOCT00001",
        "pid": "531534"
      },
      {
        "userId": 20031995,
        "orderId": "9B9hgl0UgNr2P9RoW7wblQ==",
        "payMoney": 99,
        "payTime": "2024-08-19 17:43:41",
        "payStatus": 1,
        "customParams": "05310531",
        "bookId": "veXpFK9K6FkJ7jLp1w+HVA==",
        "searchCode": "21302",
        "channelCode": "KOCT00001",
        "pid": "531534"
      }
    ],
    "pageNo": 1,
    "pageSize": 10,
    "pages": 1,
    "total": 2
  },
  "status": 0,
  "message": "success",
  "timestamp": 1724233067861,
  "ip": "",
  "region": null,
  "path": "/open/partner/orders",
  "success": true
}
```

# 生成口令/落地页/onelink

##### 简要描述

*   根据pid+bookId+customParams+codeMedia生成口令/落地页/onelink，这三个字段对应一条唯一的口令。


##### 请求URL

*   POST https://api.novelopen.com/creek/open/inviteCode/generate/partner/code


##### 限流频率

*   pid+bookId+customParams+codeMedia作为唯一标识，限制2秒1次


##### 请求方式

*   POST


##### 请求参数

| **参数名** | **必选** | **类型** | **说明** |
| --- | --- | --- | --- |
| pid | 是 | string | KOC机构ID |
| bookId | 是 | string | 短剧ID<br>（PS:当短剧进行请求时，支持不传具体bookId，入参7DhUvlVErWaoOA7ZG3kUHQ==即可，此时返回的shareUrl支持直接拉起主页，而非单部剧，且同时返回的口令码不支持分发给用户进行口令搜索（口令只能绑定具体剧）—— 建议bookId=具体短剧ID，会直接拉起对应剧，转化更好） |
| customParams | 是 | string | 机构方的达人标识/达人ID（此字段会在订单接口原样返回，用于机构方区分订单属于哪个达人） |
| shareUrlType | 否 | int | 分享链接类型，1-落地页，2-OneLink；默认1 |
| codeMedia | 否 | string | shareUrlType的分享媒体, FACEBOOK、TIKTOK、GOOGLE、YOUTUBE、TWITTER、X、WHATSAPP、以及其他具体媒体，未知则是 UNKNOWN（默认） |
| timestamp | 是 | string | 当前时间戳，毫秒 |
| sign | 是 | string | 签名 |

##### 返回参数

| **参数名** | **类型** | **说明** |
| --- | --- | --- |
| code | string | 口令码，6位纯数字（举例 123456），后期替换为字母+数字（举例A12345）。**（建议机构对口令和shareUrl做映射保存，后续口令将会作为入参，可查询其他接口数据）** |
| customParams | string | 入参customParams |
| shareUrl | string | 分享链接 |

##### 返回示例

```json
{
  "data": {
    "code": "54788",
    "customParams": "sss",
    "shareUrl": "https://demo.com/koc/GRKOC00001/54788-KOC"
  },
  "status": 0,
  "message": "success",
  "timestamp": 1724232196893,
  "ip": "",
  "region": null,
  "path": "/open/inviteCode/generate/partner/code",
  "success": true
}

```

# 订单转化明细

##### 简要描述

*   订单转化明细


##### 请求URL

*   POST [https://api.novelopen.com/creek/open/promotion/analyticalReport](https://api.novelopen.com/creek/open/promotion/analyticalReport)


##### 限流频率

*   100次/min

*   建议：每天早上8点以后调用，获取前一天的数据。


##### 请求参数

| **参数名** | **必选** | **类型** | **说明** |
| --- | --- | --- | --- |
| pid | 是 | string | KOC机构ID |
| timestamp | 是 | string | 当前时间戳，毫秒 |
| sign | 是 | string | 签名 |
| pageNo | 是 | int | 第几页 |
| pageSize | 是 | int | 每页几条，最大限制500 |
| startTime | 是 | string | 查询开始时间，精确到天。格式：2023-04-18 |
| endTime | 是 | string | 查询结束时间,  精确到天。格式：2023-04-19，开始时间结束时间之间不能超过30天 |
| code | 否 | string | 口令 |
| bookId | 否 | string | 剧集ID |
| customParams | 否 | string | 机构达人ID |

##### 返回参数

| **参数名** | **类型** | **说明** |
| --- | --- | --- |
| reportDate | string | 记录数据日期，自然日单天 |
| pId | string | 机构ID，机构唯一ID |
| customParams | string | 达人ID，达人唯一ID |
| bookId | string | 剧集ID |
| code | string | 口令标识，口令码口令标识，口令码 |
| clickCount | Integer | 链接点击数量，落地页/Onelink曝光PV |
| attributedUserCount | Integer | 导端用户数，符合端内归因用户数量去重 |
| newRegisteredUserCount | Integer | 新注册人数，符合端内归因新用户数量去重 |
| newPaidUserCount | Integer | 新充值人数，符合端内归因新用户中有充值数量去重 |
| newMemberUserCount | Integer | 新开会员数，符合端内归因新用户中新订阅会员用户数量去重 |
| paidUserCount | Integer | 充值用户数，符合归因充值用户数去重 |
| orderCount | Integer | 充值订单数，符合归因充值订单数量 |
| orderAmount | Double | 充值订单金额，符合归因充值订单金额 |

##### 返回示例

```json
{
    "data": {
        "current": 1,
        "size": 500,
        "total": 1,
        "records": [
            {
                "reportDate": "2025-06-18",
                "customParams": "customParams",
                "bookId": "FhxQGC2cRAG+bpIb8GJk0A==",
                "code": "code",
                "clickCount": 8,
                "attributedUserCount": 2,
                "newRegisteredUserCount": 0,
                "newPaidUserCount": 0,
                "newMemberUserCount": 0,
                "paidUserCount": 0,
                "orderCount": 0,
                "orderAmount": 0.0,
                "pid": "pid"
            }
        ],
        "pages": 1
    },
    "status": 0,
    "message": "success",
    "timestamp": 1751530054270,
    "ip": "",
    "region": null,
    "path": "/open/promotion/analyticalReport",
    "success": true
}
```

# 账号报备

##### 报备上报

##### 简要描述

*   账号报备上报


##### 请求URL

*   POST [https://api.novelopen.com/creek/open/filing/report](https://api.novelopen.com/creek/open/promotion/analyticalReport)


##### 限流频率

*   100次/min


##### 请求参数

| **参数名** | **必选** | **类型** | **说明** |
| --- | --- | --- | --- |
| pid | 是 | string | KOC机构ID |
| timestamp | 是 | string | 当前时间戳，毫秒 |
| sign | 是 | string | 签名 |
| type | 是 | string | 报备类型: ACCOUNT-账号报备 |
| media | 是 | string | 媒体平台: FACEBOOK、TIKTOK、YOUTUBE、INSTAGRAM |
| accountId | 是 | string | 账号ID |
| accountName | 否 | string | 账号名称 |
| accountLink | 否 | string | 账号链接 |

##### 返回参数

无

##### 返回示例

```json
{
    "data": {},
    "status": 0,
    "message": "success",
    "timestamp": 1751530054270,
    "ip": "",
    "region": null,
    "path": "/open/filing/report",
    "success": true
}
```

##### 报备查询

##### 简要描述

*   账号报备查询


##### 请求URL

*   POST [https://api.novelopen.com/creek/open/filing/query](https://api.novelopen.com/creek/open/promotion/analyticalReport)


##### 限流频率

*   100次/min


##### 请求参数

| **参数名** | **必选** | **类型** | **说明** |
| --- | --- | --- | --- |
| pid | 是 | string | KOC机构ID |
| timestamp | 是 | string | 当前时间戳，毫秒 |
| sign | 是 | string | 签名 |
| type | 是 | string | 报备类型: ACCOUNT-账号报备 |
| media | 是 | string | 媒体平台: FACEBOOK/TIKTOK/YOUTUBE/INSTAGRAM |
| accountId | 是 | string | 账号ID |

##### 返回参数

| **参数名** | **类型** | **说明** |
| --- | --- | --- |
| filingTime | date | 报备时间 |
| media | string | 媒体平台 |
| accountId | string | 账号ID |
| accountName | string | 账号名称 |
| accountLink | string | 账号链接 |
| status | int | 状态: 0-审核中,1-已加白,2-拒绝加白 |
| operateTime | date | 审核时间 |

##### 返回示例

```json
{
    "data": {
        "filingTime": "2025-08-28T11:26:18.000+0000",
        "media": "FACEBOOK",
        "accountName": "1111",
        "accountId": "2222",
        "accountLink": "333333",
        "status": 1,
        "operateTime": "2025-08-28T12:52:36.000+0000"
    },
    "status": 0,
    "message": "success",
    "timestamp": 1751530054270,
    "ip": "",
    "region": null,
    "path": "/open/filing/query",
    "success": true
}
```
