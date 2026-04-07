# MCP tools/list 导出结果

- 时间: 2026-04-07T18:19:06.498987800
- 总服务数: 1

## dream-interpretation

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/dream-interpretation/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### 周公解梦接口

- 描述: 最新、最全的周公解梦大全查询，有人物、动物、植物、物品、活动、生活、自然、鬼神、建筑、孕妇等10分类，5万数据。
- input_schema:

```json
{
  "properties" : {
    "keyword" : {
      "description" : "关键词",
      "type" : "string"
    }
  },
  "required" : [ "keyword" ],
  "type" : "object"
}
```

