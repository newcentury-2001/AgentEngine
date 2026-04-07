# MCP ??????????????????

- ???????????????? outputSchema ???
- ??????????????????? null?

## recognition

- skillDescription: recognition技能，用于相关能力调用。
- intent: `query`
- actionType: `read`

### location_recognition

- toolDescription: location_recognition：用于相关能力调用。
- outputSlotKeysInferred: `["image_url"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The url of the input image.",
      "title": "Img Url",
      "type": "string"
    }
  },
  "required": [
    "img_url"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |

### person_recognition

- toolDescription: person_recognition：用于相关能力调用。
- outputSlotKeysInferred: `["image_url", "crop_bbox", "ref_name"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The url of the input image.",
      "title": "Img Url",
      "type": "string"
    },
    "bbox": {
      "default": "",
      "description": "Crop box coordinates in thousandths. - Format: [x1, y1, x2, y2], each in 0-999. If provided, only the region inside the bounding box will be recognized. (Optional)",
      "title": "Bbox",
      "type": "string"
    },
    "ref_name": {
      "default": "",
      "description": "The name or label of the object in the specified bounding box. (Optional)",
      "title": "Ref Name",
      "type": "string"
    }
  },
  "required": [
    "img_url"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |
| bbox | string | false | crop_bbox |
| ref_name | string | false | ref_name |

### plant_recognition

- toolDescription: plant_recognition：用于识别图片中的植物信息。
- outputSlotKeysInferred: `["image_url"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The url of the input image.",
      "title": "Img Url",
      "type": "string"
    }
  },
  "required": [
    "img_url"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |

## image-search

- skillDescription: image-search技能，用于图片检索与结果返回。
- intent: `query`
- actionType: `read`

### web_search

- toolDescription: web_search：用于相关能力调用。
- outputSlotKeysInferred: `["query_text", "page_no"]`

- inputSchema:
```json
{
  "properties": {
    "query": {
      "description": "Search query string",
      "title": "Query",
      "type": "string"
    },
    "page_num": {
      "default": "0",
      "description": "Page index starting from 0. Each page returns 5 results.",
      "title": "Page Num",
      "type": "string"
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| query | string | true | query_text |
| page_num | string | false | page_no |

### search_image

- toolDescription: search_image：用于图片检索与结果返回。
- outputSlotKeysInferred: `["query_text"]`

- inputSchema:
```json
{
  "properties": {
    "query": {
      "description": "The query text for image search.",
      "title": "Query",
      "type": "string"
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| query | string | true | query_text |

### image_search_with_image

- toolDescription: image_search_with_image：用于图片检索与结果返回。
- outputSlotKeysInferred: `["image_url"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The image url of the target image.",
      "title": "Img Url",
      "type": "string"
    }
  },
  "required": [
    "img_url"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |

### image_zoom_in_search_tool

- toolDescription: image_zoom_in_search_tool：用于图片检索与结果返回。
- outputSlotKeysInferred: `["image_url", "crop_bbox", "ref_name"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The image url of the target image.",
      "title": "Img Url",
      "type": "string"
    },
    "bbox": {
      "default": "",
      "description": "The bounding box for cropping image. In the format of '[x1,y1,x2,y2]', range from 0 to 999.",
      "title": "Bbox",
      "type": "string"
    },
    "ref_name": {
      "description": "The name or label of the object in the specified bounding box (optional).",
      "title": "Ref Name",
      "type": "string"
    }
  },
  "required": [
    "img_url"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |
| bbox | string | false | crop_bbox |
| ref_name | string | false | ref_name |

### text_search_with_image

- toolDescription: text_search_with_image：用于图片检索与结果返回。
- outputSlotKeysInferred: `["image_url"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The image url of the target image.",
      "title": "Img Url",
      "type": "string"
    }
  },
  "required": [
    "img_url"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |

## image-process

- skillDescription: image-process技能，用于相关能力调用。
- intent: `query`
- actionType: `read`

### crop

- toolDescription: crop：用于图片裁剪。
- outputSlotKeysInferred: `["image_url", "crop_bbox"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The image url of the target image.",
      "title": "Img Url",
      "type": "string"
    },
    "box": {
      "description": "Crop box coordinates in thousandths. - Format: [x1, y1, x2, y2], each in 0-999.",
      "title": "Box",
      "type": "string"
    }
  },
  "required": [
    "img_url",
    "box"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |
| box | string | true | crop_bbox |

### draw_boxes

- toolDescription: draw_boxes：用于在图片上绘制矩形框。
- outputSlotKeysInferred: `["image_url", "crop_bbox", "draw_color", "line_thickness"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The image url of the target image.",
      "title": "Img Url",
      "type": "string"
    },
    "boxes": {
      "description": "Rectangle coordinates in thousandths. - Format: [[x1, y1, x2, y2][x1, y1, x2, y2]], each in 0-999.",
      "title": "Boxes",
      "type": "string"
    },
    "color": {
      "default": "red",
      "description": "Outline color name or hex. Default as red.",
      "title": "Color",
      "type": "string"
    },
    "thickness": {
      "default": "3",
      "description": "Outline thickness in pixels (>=1).Default as 3.",
      "title": "Thickness",
      "type": "string"
    }
  },
  "required": [
    "img_url",
    "boxes"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |
| boxes | string | true | crop_bbox |
| color | string | false | draw_color |
| thickness | string | false | line_thickness |

### draw_point

- toolDescription: draw_point：用于在图片上绘制标记点。
- outputSlotKeysInferred: `["image_url", "crop_bbox", "draw_color", "line_thickness"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The image url of the target image.",
      "title": "Img Url",
      "type": "string"
    },
    "point": {
      "description": "Point coordinates in thousandths. - Format: [x, y], each in 0-999.",
      "title": "Point",
      "type": "string"
    },
    "color": {
      "default": "red",
      "description": "Fill color name or hex. Default as red.",
      "title": "Color",
      "type": "string"
    },
    "radius": {
      "default": "4",
      "description": "Circle radius in pixels (>=1). Default as 4.",
      "title": "Radius",
      "type": "string"
    }
  },
  "required": [
    "img_url",
    "point"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |
| point | string | true | crop_bbox |
| color | string | false | draw_color |
| radius | string | false | line_thickness |

### open_img_url

- toolDescription: open_img_url：用于相关能力调用。
- outputSlotKeysInferred: `["image_url"]`

- inputSchema:
```json
{
  "properties": {
    "url": {
      "description": "Direct image URL (http/https)",
      "title": "Url",
      "type": "string"
    }
  },
  "required": [
    "url"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| url | string | true | image_url |

### image_reference

- toolDescription: image_reference：用于相关能力调用。
- outputSlotKeysInferred: `["image_url", "crop_bbox", "ref_name"]`

- inputSchema:
```json
{
  "properties": {
    "img_url": {
      "description": "The url of the target image.",
      "title": "Img Url",
      "type": "string"
    },
    "crop_box": {
      "description": "Crop box coordinates in thousandths. (Optional)\n- Format: [x1, y1, x2, y2], each in 0-999.",
      "title": "Crop Box",
      "type": "string"
    },
    "ref_name": {
      "default": "",
      "description": "The name for the referenced image. (Optional)",
      "title": "Ref Name",
      "type": "string"
    }
  },
  "required": [
    "img_url",
    "crop_box"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| img_url | string | true | image_url |
| crop_box | string | true | crop_bbox |
| ref_name | string | false | ref_name |

## trustworthy-search

- skillDescription: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
- intent: `query`
- actionType: `read`

### query

- toolDescription: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|

## 数据基础：

- skillDescription: 
- intent: `query`
- actionType: `read`

## 核心功能：

- skillDescription: 
- intent: `query`
- actionType: `read`

## 用户查询示例：

- skillDescription: 
- intent: `query`
- actionType: `read`

## gaode-map

- skillDescription: 骑行路径规划用于规划骑行通勤方案，规划时会考虑天桥、单行线、封路等情况。最大支持 500km 的骑行路线规划
- intent: `query`
- actionType: `read`

### maps_direction_bicycling

- toolDescription: 骑行路径规划用于规划骑行通勤方案，规划时会考虑天桥、单行线、封路等情况。最大支持 500km 的骑行路线规划
- outputSlotKeysInferred: `["origin", "destination"]`

- inputSchema:
```json
{
  "properties": {
    "origin": {
      "type": "string",
      "description": "出发点经纬度，坐标格式为：经度，纬度"
    },
    "destination": {
      "type": "string",
      "description": "目的地经纬度，坐标格式为：经度，纬度"
    }
  },
  "required": [
    "origin",
    "destination"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| origin | string | true | origin |
| destination | string | true | destination |

### maps_direction_driving

- toolDescription: 驾车路径规划 API 可以根据用户起终点经纬度坐标规划以小客车、轿车通勤出行的方案，并且返回通勤方案的数据。
- outputSlotKeysInferred: `["origin", "destination"]`

- inputSchema:
```json
{
  "properties": {
    "origin": {
      "type": "string",
      "description": "出发点经纬度，坐标格式为：经度，纬度"
    },
    "destination": {
      "type": "string",
      "description": "目的地经纬度，坐标格式为：经度，纬度"
    }
  },
  "required": [
    "origin",
    "destination"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| origin | string | true | origin |
| destination | string | true | destination |

### maps_direction_transit_integrated

- toolDescription: 根据用户起终点经纬度坐标规划综合各类公共（火车、公交、地铁）交通方式的通勤方案，并且返回通勤方案的数据，跨城场景下必须传起点城市与终点城市
- outputSlotKeysInferred: `["origin", "destination", "city"]`

- inputSchema:
```json
{
  "properties": {
    "origin": {
      "type": "string",
      "description": "出发点经纬度，坐标格式为：经度，纬度"
    },
    "destination": {
      "type": "string",
      "description": "目的地经纬度，坐标格式为：经度，纬度"
    },
    "city": {
      "type": "string",
      "description": "公共交通规划起点城市"
    },
    "cityd": {
      "type": "string",
      "description": "公共交通规划终点城市"
    }
  },
  "required": [
    "origin",
    "destination",
    "city",
    "cityd"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| origin | string | true | origin |
| destination | string | true | destination |
| city | string | true | city |
| cityd | string | true | city |

### maps_direction_walking

- toolDescription: 根据输入起点终点经纬度坐标规划100km 以内的步行通勤方案，并且返回通勤方案的数据
- outputSlotKeysInferred: `["origin", "destination"]`

- inputSchema:
```json
{
  "properties": {
    "origin": {
      "type": "string",
      "description": "出发点经度，纬度，坐标格式为：经度，纬度"
    },
    "destination": {
      "type": "string",
      "description": "目的地经度，纬度，坐标格式为：经度，纬度"
    }
  },
  "required": [
    "origin",
    "destination"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| origin | string | true | origin |
| destination | string | true | destination |

### maps_distance

- toolDescription: 测量两个经纬度坐标之间的距离,支持驾车、步行以及球面距离测量
- outputSlotKeysInferred: `["destination"]`

- inputSchema:
```json
{
  "properties": {
    "origins": {
      "type": "string",
      "description": "起点经度，纬度，可以传多个坐标，使用竖线隔离，比如120,30|120,31，坐标格式为：经度，纬度"
    },
    "destination": {
      "type": "string",
      "description": "终点经度，纬度，坐标格式为：经度，纬度"
    },
    "type": {
      "type": "string",
      "description": "距离测量类型,1代表驾车距离测量，0代表直线距离测量，3步行距离测量"
    }
  },
  "required": [
    "origins",
    "destination"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| origins | string | true | unknown |
| destination | string | true | destination |
| type | string | false | unknown |

### maps_geo

- toolDescription: 将详细的结构化地址转换为经纬度坐标。支持对地标性名胜景区、建筑物名称解析为经纬度坐标
- outputSlotKeysInferred: `["address", "city"]`

- inputSchema:
```json
{
  "properties": {
    "address": {
      "type": "string",
      "description": "待解析的结构化地址信息"
    },
    "city": {
      "type": "string",
      "description": "指定查询的城市"
    }
  },
  "required": [
    "address"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| address | string | true | address |
| city | string | false | city |

### maps_regeocode

- toolDescription: 将一个高德经纬度坐标转换为行政区划地址信息
- outputSlotKeysInferred: `["geo_point"]`

- inputSchema:
```json
{
  "properties": {
    "location": {
      "type": "string",
      "description": "经纬度"
    }
  },
  "required": [
    "location"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| location | string | true | geo_point |

### maps_ip_location

- toolDescription: IP 定位根据用户输入的 IP 地址，定位 IP 的所在位置
- outputSlotKeysInferred: `["ip"]`

- inputSchema:
```json
{
  "properties": {
    "ip": {
      "type": "string",
      "description": "IP地址"
    }
  },
  "required": [
    "ip"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| ip | string | true | ip |

### maps_schema_personal_map

- toolDescription: 用于行程规划结果在高德地图展示。将行程规划位置点按照行程顺序填入lineList，返回结果为高德地图打开的URI链接，该结果不需总结，直接返回！
- outputSlotKeysInferred: `["org_name", "geo_point"]`

- inputSchema:
```json
{
  "properties": {
    "orgName": {
      "type": "string",
      "description": "行程规划地图小程序名称"
    },
    "lineList": {
      "type": "array",
      "description": "行程列表",
      "items": {
        "type": "object",
        "properties": {
          "title": {
            "type": "string",
            "description": "行程名称描述（按行程顺序）"
          },
          "pointInfoList": {
            "type": "array",
            "description": "行程目标位置点描述",
            "items": {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "行程目标位置点名称"
                },
                "lon": {
                  "type": "number",
                  "description": "行程目标位置点经度"
                },
                "lat": {
                  "type": "number",
                  "description": "行程目标位置点纬度"
                },
                "poiId": {
                  "type": "string",
                  "description": "行程目标位置点POIID"
                }
              },
              "required": [
                "name",
                "lon",
                "lat",
                "poiId"
              ]
            }
          }
        },
        "required": [
          "title",
          "pointInfoList"
        ]
      }
    }
  },
  "required": [
    "orgName",
    "lineList"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| orgName | string | true | org_name |
| lineList | array | true | unknown |
| lineList[] | array_item | false | unknown |
| lineList[].title | string | true | unknown |
| lineList[].pointInfoList | array | true | unknown |
| lineList[].pointInfoList[] | array_item | false | unknown |
| lineList[].pointInfoList[].name | string | true | unknown |
| lineList[].pointInfoList[].lon | number | true | geo_point |
| lineList[].pointInfoList[].lat | number | true | geo_point |
| lineList[].pointInfoList[].poiId | string | true | unknown |

### maps_around_search

- toolDescription: 周边搜，根据用户传入关键词以及坐标location，搜索出radius半径范围的POI
- outputSlotKeysInferred: `["query_text", "geo_point", "line_thickness", "sort_by"]`

- inputSchema:
```json
{
  "properties": {
    "keywords": {
      "type": "string",
      "description": "搜索关键词"
    },
    "location": {
      "type": "string",
      "description": "中心点经度纬度"
    },
    "radius": {
      "type": "string",
      "description": "搜索半径"
    },
    "strategy": {
      "type": "integer",
      "description": "召回策略，0=默认召回策略，1=优先召回扫街榜POI",
      "default": 0
    }
  },
  "required": [
    "keywords",
    "location"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| keywords | string | true | query_text |
| location | string | true | geo_point |
| radius | string | false | line_thickness |
| strategy | integer | false | sort_by |

### maps_search_detail

- toolDescription: 查询关键词搜或者周边搜获取到的POI ID的详细信息
- outputSlotKeysInferred: `["org_code"]`

- inputSchema:
```json
{
  "properties": {
    "id": {
      "type": "string",
      "description": "关键词搜或者周边搜获取到的POI ID"
    }
  },
  "required": [
    "id"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| id | string | true | org_code |

### maps_text_search

- toolDescription: 关键字搜索 API 根据用户输入的关键字进行 POI 搜索，并返回相关的信息
- outputSlotKeysInferred: `["query_text", "city"]`

- inputSchema:
```json
{
  "properties": {
    "keywords": {
      "type": "string",
      "description": "查询关键字"
    },
    "city": {
      "type": "string",
      "description": "查询城市"
    },
    "citylimit": {
      "type": "boolean",
      "default": false,
      "description": "是否限制城市范围内搜索，默认不限制"
    }
  },
  "required": [
    "keywords"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| keywords | string | true | query_text |
| city | string | false | city |
| citylimit | boolean | false | unknown |

### maps_schema_navi

- toolDescription: Schema唤醒客户端-导航页面，用于根据用户输入终点信息，返回一个拼装好的客户端唤醒URI，用户点击该URI即可唤起对应的客户端APP。唤起客户端后，会自动跳转到导航页面。
- outputSlotKeysInferred: `["geo_point"]`

- inputSchema:
```json
{
  "properties": {
    "lon": {
      "type": "string",
      "description": "终点经度"
    },
    "lat": {
      "type": "string",
      "description": "终点纬度"
    }
  },
  "required": [
    "lon",
    "lat"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| lon | string | true | geo_point |
| lat | string | true | geo_point |

### maps_schema_take_taxi

- toolDescription: 根据用户输入的起点和终点信息，返回一个拼装好的客户端唤醒URI，直接唤起高德地图进行打车。直接展示生成的链接，不需要总结
- outputSlotKeysInferred: `["geo_point", "destination", "origin"]`

- inputSchema:
```json
{
  "properties": {
    "slon": {
      "type": "string",
      "description": "起点经度"
    },
    "slat": {
      "type": "string",
      "description": "起点纬度"
    },
    "sname": {
      "type": "string",
      "description": "起点名称"
    },
    "dlon": {
      "type": "string",
      "description": "终点经度"
    },
    "dlat": {
      "type": "string",
      "description": "终点纬度"
    },
    "dname": {
      "type": "string",
      "description": "终点名称"
    }
  },
  "required": [
    "dlon",
    "dlat",
    "dname"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| slon | string | false | geo_point |
| slat | string | false | geo_point |
| sname | string | false | destination |
| dlon | string | true | geo_point |
| dlat | string | true | geo_point |
| dname | string | true | origin |

### maps_weather

- toolDescription: 根据城市名称或者标准adcode查询指定城市的天气
- outputSlotKeysInferred: `["city"]`

- inputSchema:
```json
{
  "properties": {
    "city": {
      "type": "string",
      "description": "城市名称或者adcode"
    }
  },
  "required": [
    "city"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| city | string | true | city |

## jina-reader

- skillDescription: jina-reader技能，用于相关能力调用。
- intent: `query`
- actionType: `read`

### webReader

- toolDescription: webReader：用于相关能力调用。
- outputSlotKeysInferred: `["image_url"]`

- inputSchema:
```json
{
  "additionalProperties": false,
  "properties": {
    "url": {
      "type": "string",
      "description": "The URL of the website to fetch and read"
    },
    "timeout": {
      "type": "integer",
      "format": "int32",
      "description": "Request timeout(unit is second), default is 20"
    },
    "no_cache": {
      "type": "boolean",
      "description": "Disable cache(true/false), default is false"
    },
    "return_format": {
      "type": "string",
      "description": "Reader response content type (markdown or text), default is markdown"
    },
    "retain_images": {
      "type": "boolean",
      "description": "Retain images (true/false), default is true"
    },
    "no_gfm": {
      "type": "boolean",
      "description": "Disable GitHub Flavored Markdown (true/false), default is false"
    },
    "keep_img_data_url": {
      "type": "boolean",
      "description": "Keep image data URL (true/false), default is false"
    },
    "with_images_summary": {
      "type": "boolean",
      "description": "Include images summary (true/false), default is false"
    },
    "with_links_summary": {
      "type": "boolean",
      "description": "Include links summary (true/false), default is false"
    }
  },
  "required": [
    "url"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| url | string | true | image_url |
| timeout | integer | false | unknown |
| no_cache | boolean | false | unknown |
| return_format | string | false | unknown |
| retain_images | boolean | false | unknown |
| no_gfm | boolean | false | unknown |
| keep_img_data_url | boolean | false | unknown |
| with_images_summary | boolean | false | unknown |
| with_links_summary | boolean | false | unknown |

## business-tax-id

- skillDescription: 通过关键词查询税号
- intent: `query`
- actionType: `read`

### 企业税号查询

- toolDescription: 通过关键词查询税号
- outputSlotKeysInferred: `["query_text", "page_no", "result_limit"]`

- inputSchema:
```json
{
  "properties": {
    "keyword": {
      "description": "关键词（公司名称/注册号/统一信用代码/法人/股东/高管等任意关键字）【汉字注意UrlEncode编码】",
      "type": "string"
    },
    "pageNum": {
      "description": "当前页数（默认第1页）",
      "type": "string"
    },
    "pageSize": {
      "description": "每页条数（默认20条，最大20条）",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| keyword | string | false | query_text |
| pageNum | string | false | page_no |
| pageSize | string | false | result_limit |

## holiday-inquiry

- skillDescription: 假日列表
- intent: `query`
- actionType: `read`

### 假日列表

- toolDescription: 假日列表
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{
  "properties": {
    "year": {
      "description": "需要查询的年份，默认查询当年的节假日列表",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| year | string | false | unknown |

### 节假日查询

- toolDescription: 节假日查询
- outputSlotKeysInferred: `["date"]`

- inputSchema:
```json
{
  "properties": {
    "day": {
      "description": "要查询是否放假的日期，格式YYYYMMDD",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| day | string | false | date |

## dream-interpretation

- skillDescription: 最新、最全的周公解梦大全查询，有人物、动物、植物、物品、活动、生活、自然、鬼神、建筑、孕妇等10分类，5万数据。
- intent: `query`
- actionType: `read`

### 周公解梦接口

- toolDescription: 最新、最全的周公解梦大全查询，有人物、动物、植物、物品、活动、生活、自然、鬼神、建筑、孕妇等10分类，5万数据。
- outputSlotKeysInferred: `["query_text"]`

- inputSchema:
```json
{
  "properties": {
    "keyword": {
      "description": "关键词",
      "type": "string"
    }
  },
  "required": [
    "keyword"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| keyword | string | true | query_text |

## lottery-results

- skillDescription: 查询是否中奖
- intent: `query`
- actionType: `read`

### 查询是否中奖

- toolDescription: 查询是否中奖
- outputSlotKeysInferred: `["lottery_id", "issue_no", "express_no"]`

- inputSchema:
```json
{
  "properties": {
    "caipiaoid": {
      "description": "彩票ID",
      "type": "string"
    },
    "issueno": {
      "description": "期号 默认为最新一期",
      "type": "string"
    },
    "number": {
      "description": "彩票号码 红球",
      "type": "string"
    },
    "refernumber": {
      "description": "彩票剩余号码 蓝球",
      "type": "string"
    },
    "type": {
      "description": "投注类型 1直选 2组三 3组六",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| caipiaoid | string | false | lottery_id |
| issueno | string | false | issue_no |
| number | string | false | express_no |
| refernumber | string | false | express_no |
| type | string | false | unknown |

### 彩票分类查询接口

- toolDescription: 查询彩票ID、名称和上级ID。
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{
  "properties": {},
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|

### 彩票开奖查询接口

- toolDescription: 通过彩票ID和期号查询彩票开奖信息。
- outputSlotKeysInferred: `["lottery_id", "issue_no"]`

- inputSchema:
```json
{
  "properties": {
    "caipiaoid": {
      "description": "彩票ID（彩票分类查询接口中获取）",
      "type": "integer"
    },
    "issueno": {
      "description": "期号",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| caipiaoid | integer | false | lottery_id |
| issueno | string | false | issue_no |

### 历史开奖信息查询接口

- toolDescription: 通过彩票ID和期号查询历史开奖信息。
- outputSlotKeysInferred: `["lottery_id", "issue_no", "result_limit", "date"]`

- inputSchema:
```json
{
  "properties": {
    "caipiaoid": {
      "description": "彩票ID",
      "type": "integer"
    },
    "issueno": {
      "description": "期号 不选默认是当前期",
      "type": "string"
    },
    "num": {
      "description": "获取数量 最大20 默认10",
      "type": "integer"
    },
    "start": {
      "description": "起始位置 默认0",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| caipiaoid | integer | false | lottery_id |
| issueno | string | false | issue_no |
| num | integer | false | result_limit |
| start | string | false | date |

## this-day-in-history

- skillDescription: 历史上的今天
- intent: `query`
- actionType: `read`

### 历史上的今天

- toolDescription: 历史上的今天
- outputSlotKeysInferred: `["date"]`

- inputSchema:
```json
{
  "properties": {
    "date": {
      "description": "日期，不写的话默认为当前天",
      "type": "string"
    },
    "needContent": {
      "description": "是否返回历史事件的详细内容，1表示需要，0表示不需要",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| date | string | false | date |
| needContent | string | false | unknown |

## exchange-rate

- skillDescription: 十大银行的外汇牌价
- intent: `query`
- actionType: `read`

### 十大银行的外汇牌价

- toolDescription: 十大银行的外汇牌价
- outputSlotKeysInferred: `["bank"]`

- inputSchema:
```json
{
  "properties": {
    "bank": {
      "description": "银行编码。工商银行：ICBC ，中国银行：BOC ，农业银行：ABCHINA ，交通银行：BANKCOMM ，建设银行：CCB ，招商银行：CMBCHINA ，光大银行：CEBBANK ，浦发银行：SPDB ，兴业银行：CIB ，中信银行：ECITIC，默认BOC",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| bank | string | false | bank |

### 单个货币查询接口

- toolDescription: 查询单个货币与其他货币间的汇率及更新时间。
- outputSlotKeysInferred: `["symbol"]`

- inputSchema:
```json
{
  "properties": {
    "currency": {
      "description": "货币（所有货币查询接口中获取）",
      "type": "string"
    }
  },
  "required": [
    "currency"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| currency | string | true | symbol |

### 汇率转换接口

- toolDescription: 汇率转换
- outputSlotKeysInferred: `["amount", "origin", "destination"]`

- inputSchema:
```json
{
  "properties": {
    "amount": {
      "description": "数量",
      "type": "string"
    },
    "from": {
      "description": "要换算的单位（所有货币接口中获取，若为空取CNY或USD）",
      "type": "string"
    },
    "to": {
      "description": "换算后的单位（所有货币接口中获取，若为空取CNY或USD）",
      "type": "string"
    }
  },
  "required": [
    "from",
    "amount",
    "to"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| amount | string | true | amount |
| from | string | true | origin |
| to | string | true | destination |

## postal-code

- skillDescription: 通过地址查询邮编
- intent: `query`
- actionType: `read`

### 地址查询邮编

- toolDescription: 通过地址查询邮编
- outputSlotKeysInferred: `["address"]`

- inputSchema:
```json
{
  "properties": {
    "address": {
      "description": "地址",
      "type": "string"
    },
    "areaid": {
      "description": "区域ID",
      "type": "integer"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| address | string | false | address |
| areaid | integer | false | address |

### 邮编查地址

- toolDescription: 通过邮编查询邮编地址
- outputSlotKeysInferred: `["zipcode"]`

- inputSchema:
```json
{
  "properties": {
    "zipcode": {
      "description": "邮编",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| zipcode | string | false | zipcode |

### 区域查询

- toolDescription: 查询邮编所在区域
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{
  "properties": {},
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|

## constellation

- skillDescription: 星座运势查询
- intent: `query`
- actionType: `read`

### 星座运势查询

- toolDescription: 星座运势查询
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{
  "properties": {
    "needMonth": {
      "description": "是否需要本月运势的数据，1为需要，其他不需要",
      "type": "string"
    },
    "needTomorrow": {
      "description": "是否需要明天的数据，1为需要，其他不需要",
      "type": "string"
    },
    "needWeek": {
      "description": "是否需要本周运势的数据，1为需要，其他不需要",
      "type": "string"
    },
    "needYear": {
      "description": "是否需要本年运势的数据，1为需要，其他不需要",
      "type": "string"
    },
    "star": {
      "description": "十二星座，其值分别为 baiyang jinniu shuangzi juxie shizi chunv tiancheng tianxie sheshou mojie shuiping shuangyu",
      "type": "string"
    }
  },
  "required": [
    "star"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| needMonth | string | false | unknown |
| needTomorrow | string | false | unknown |
| needWeek | string | false | unknown |
| needYear | string | false | unknown |
| star | string | true | unknown |

## knowledge-recall

- skillDescription: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
- intent: `query`
- actionType: `read`

### query

- toolDescription: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|

## 数据基础：

- skillDescription: 
- intent: `query`
- actionType: `read`

## 核心功能：

- skillDescription: 
- intent: `query`
- actionType: `read`

## 用户查询示例：

- skillDescription: 
- intent: `query`
- actionType: `read`

## tripmatch

- skillDescription: tripmatch技能，用于相关能力调用。
- intent: `query`
- actionType: `read`

### searchFlightsByDepArr

- toolDescription: searchFlightsByDepArr：用于航班与列车信息查询。
- outputSlotKeysInferred: `["date", "dep_airport", "arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "date": {
      "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Date",
      "type": "string"
    },
    "dep": {
      "anyOf": [
        {
          "description": "Departure airport IATA 3-letter code (e.g. PEK for Beijing, CAN for Guangzhou)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Dep"
    },
    "depcity": {
      "anyOf": [
        {
          "description": "Departure city IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Depcity"
    },
    "arr": {
      "anyOf": [
        {
          "description": "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, HFE for Hefei)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Arr"
    },
    "arrcity": {
      "anyOf": [
        {
          "description": "Arrival city IATA 3-letter code (e.g. SHA for Shanghai, BJS for Beijing)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Arrcity"
    }
  },
  "required": [
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| date | string | true | date |
| dep | unknown | false | dep_airport |
| depcity | unknown | false | dep_airport |
| arr | unknown | false | arr_airport |
| arrcity | unknown | false | arr_airport |

### searchFlightsByNumber

- toolDescription: searchFlightsByNumber：用于航班与列车信息查询。
- outputSlotKeysInferred: `["flight_no", "date", "dep_airport", "arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "fnum": {
      "description": "Flight number including airline code (e.g. MU2157, CZ3969)",
      "title": "Fnum",
      "type": "string"
    },
    "date": {
      "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Date",
      "type": "string"
    },
    "dep": {
      "anyOf": [
        {
          "description": "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Dep"
    },
    "arr": {
      "anyOf": [
        {
          "description": "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Arr"
    }
  },
  "required": [
    "fnum",
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| fnum | string | true | flight_no |
| date | string | true | date |
| dep | unknown | false | dep_airport |
| arr | unknown | false | arr_airport |

### getFlightAndTrainTransferInfo

- toolDescription: getFlightAndTrainTransferInfo：用于航班与列车信息查询。
- outputSlotKeysInferred: `["dep_airport", "arr_airport", "date"]`

- inputSchema:
```json
{
  "properties": {
    "depcity": {
      "description": "Departure airport IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
      "title": "Depcity",
      "type": "string"
    },
    "arrcity": {
      "description": "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, LAX for Los Angeles)",
      "title": "Arrcity",
      "type": "string"
    },
    "depdate": {
      "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Depdate",
      "type": "string"
    }
  },
  "required": [
    "depcity",
    "arrcity",
    "depdate"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| depcity | string | true | dep_airport |
| arrcity | string | true | arr_airport |
| depdate | string | true | date |

### flightHappinessIndex

- toolDescription: flightHappinessIndex：用于航班与列车信息查询。
- outputSlotKeysInferred: `["flight_no", "date", "dep_airport", "arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "fnum": {
      "description": "Flight number including airline code (e.g. MU2157, CZ3969)",
      "title": "Fnum",
      "type": "string"
    },
    "date": {
      "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Date",
      "type": "string"
    },
    "dep": {
      "anyOf": [
        {
          "description": "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Dep"
    },
    "arr": {
      "anyOf": [
        {
          "description": "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Arr"
    }
  },
  "required": [
    "fnum",
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| fnum | string | true | flight_no |
| date | string | true | date |
| dep | unknown | false | dep_airport |
| arr | unknown | false | arr_airport |

### getTodayDate

- toolDescription: getTodayDate：用于相关能力调用。
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{
  "properties": {
    "random_string": {
      "anyOf": [
        {
          "description": "Dummy parameter for no-parameter tools",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Random String"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| random_string | unknown | false | unknown |

### getFutureWeatherByAirport

- toolDescription: getFutureWeatherByAirport：用于天气信息查询。
- outputSlotKeysInferred: `["arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "airport": {
      "description": "Airport IATA 3-letter code (e.g. PEK for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
      "title": "Airport",
      "type": "string"
    }
  },
  "required": [
    "airport"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| airport | string | true | arr_airport |

### searchTrainTickets

- toolDescription: searchTrainTickets：用于航班与列车信息查询。
- outputSlotKeysInferred: `["origin", "destination", "date"]`

- inputSchema:
```json
{
  "properties": {
    "from_city": {
      "description": "Departure city name (e.g. 合肥)",
      "title": "From City",
      "type": "string"
    },
    "to_city": {
      "description": "Arrival city name (e.g. 北京)",
      "title": "To City",
      "type": "string"
    },
    "date": {
      "description": "Travel date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Date",
      "type": "string"
    }
  },
  "required": [
    "from_city",
    "to_city",
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| from_city | string | true | origin |
| to_city | string | true | destination |
| date | string | true | date |

### getFlightPriceByCities

- toolDescription: getFlightPriceByCities：用于航班与列车信息查询。
- outputSlotKeysInferred: `["origin", "destination", "date"]`

- inputSchema:
```json
{
  "properties": {
    "dep_city": {
      "description": "Departure city IATA 3-letter code (e.g. HFE for Hefei)",
      "title": "Dep City",
      "type": "string"
    },
    "arr_city": {
      "description": "Arrival city IATA 3-letter code (e.g. CAN for Guangzhou)",
      "title": "Arr City",
      "type": "string"
    },
    "dep_date": {
      "description": "Departure date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Dep Date",
      "type": "string"
    }
  },
  "required": [
    "dep_city",
    "arr_city",
    "dep_date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| dep_city | string | true | origin |
| arr_city | string | true | destination |
| dep_date | string | true | date |

### searchTrainStations

- toolDescription: searchTrainStations：用于航班与列车信息查询。
- outputSlotKeysInferred: `["query_text"]`

- inputSchema:
```json
{
  "properties": {
    "query": {
      "description": "Keyword to search for train stations (e.g. 北京西)",
      "title": "Query",
      "type": "string"
    }
  },
  "required": [
    "query"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| query | string | true | query_text |

## aviation

- skillDescription: aviation技能，用于相关能力调用。
- intent: `query`
- actionType: `read`

### searchFlightsByDepArr

- toolDescription: searchFlightsByDepArr：用于航班与列车信息查询。
- outputSlotKeysInferred: `["date", "dep_airport", "arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "date": {
      "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Date",
      "type": "string"
    },
    "dep": {
      "anyOf": [
        {
          "description": "Departure airport IATA 3-letter code (e.g. PEK for Beijing, CAN for Guangzhou)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Dep"
    },
    "depcity": {
      "anyOf": [
        {
          "description": "Departure city IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Depcity"
    },
    "arr": {
      "anyOf": [
        {
          "description": "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, HFE for Hefei)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Arr"
    },
    "arrcity": {
      "anyOf": [
        {
          "description": "Arrival city IATA 3-letter code (e.g. SHA for Shanghai, BJS for Beijing)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Arrcity"
    }
  },
  "required": [
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| date | string | true | date |
| dep | unknown | false | dep_airport |
| depcity | unknown | false | dep_airport |
| arr | unknown | false | arr_airport |
| arrcity | unknown | false | arr_airport |

### searchFlightsByNumber

- toolDescription: searchFlightsByNumber：用于航班与列车信息查询。
- outputSlotKeysInferred: `["flight_no", "date", "dep_airport", "arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "fnum": {
      "description": "Flight number including airline code (e.g. MU2157, CZ3969)",
      "title": "Fnum",
      "type": "string"
    },
    "date": {
      "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Date",
      "type": "string"
    },
    "dep": {
      "anyOf": [
        {
          "description": "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Dep"
    },
    "arr": {
      "anyOf": [
        {
          "description": "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Arr"
    }
  },
  "required": [
    "fnum",
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| fnum | string | true | flight_no |
| date | string | true | date |
| dep | unknown | false | dep_airport |
| arr | unknown | false | arr_airport |

### getFlightTransferInfo

- toolDescription: getFlightTransferInfo：用于航班与列车信息查询。
- outputSlotKeysInferred: `["date", "dep_airport", "arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "depdate": {
      "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Depdate",
      "type": "string"
    },
    "depcity": {
      "description": "Departure airport IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
      "title": "Depcity",
      "type": "string"
    },
    "arrcity": {
      "description": "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, LAX for Los Angeles)",
      "title": "Arrcity",
      "type": "string"
    }
  },
  "required": [
    "depdate",
    "depcity",
    "arrcity"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| depdate | string | true | date |
| depcity | string | true | dep_airport |
| arrcity | string | true | arr_airport |

### flightHappinessIndex

- toolDescription: flightHappinessIndex：用于航班与列车信息查询。
- outputSlotKeysInferred: `["flight_no", "date", "dep_airport", "arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "fnum": {
      "description": "Flight number including airline code (e.g. MU2157, CZ3969)",
      "title": "Fnum",
      "type": "string"
    },
    "date": {
      "description": "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Date",
      "type": "string"
    },
    "dep": {
      "anyOf": [
        {
          "description": "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Dep"
    },
    "arr": {
      "anyOf": [
        {
          "description": "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Arr"
    }
  },
  "required": [
    "fnum",
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| fnum | string | true | flight_no |
| date | string | true | date |
| dep | unknown | false | dep_airport |
| arr | unknown | false | arr_airport |

### getRealtimeLocationByAnum

- toolDescription: getRealtimeLocationByAnum：用于时间信息查询。
- outputSlotKeysInferred: `["lottery_number"]`

- inputSchema:
```json
{
  "properties": {
    "anum": {
      "description": "Aircraft number like B2021, B2022, B2023, etc.",
      "title": "Anum",
      "type": "string"
    }
  },
  "required": [
    "anum"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| anum | string | true | lottery_number |

### getTodayDate

- toolDescription: getTodayDate：用于相关能力调用。
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{
  "properties": {
    "random_string": {
      "anyOf": [
        {
          "description": "Dummy parameter for no-parameter tools",
          "type": "string"
        },
        {
          "type": "null"
        }
      ],
      "title": "Random String"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| random_string | unknown | false | unknown |

### getFutureWeatherByAirport

- toolDescription: getFutureWeatherByAirport：用于天气信息查询。
- outputSlotKeysInferred: `["arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "airport": {
      "description": "Airport IATA 3-letter code (e.g. PEK for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
      "title": "Airport",
      "type": "string"
    }
  },
  "required": [
    "airport"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| airport | string | true | arr_airport |

### searchFlightItineraries

- toolDescription: searchFlightItineraries：用于航班与列车信息查询。
- outputSlotKeysInferred: `["dep_airport", "date", "arr_airport"]`

- inputSchema:
```json
{
  "properties": {
    "depCityCode": {
      "description": "Departure city 3-letter code (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
      "title": "Depcitycode",
      "type": "string"
    },
    "depDate": {
      "description": "Departure city date (format: YYYY-MM-DD, e.g., 2025-07-04).IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title": "Depdate",
      "type": "string"
    },
    "arrCityCode": {
      "description": "Arrival city 3-letter code (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
      "title": "Arrcitycode",
      "type": "string"
    }
  },
  "required": [
    "depCityCode",
    "depDate",
    "arrCityCode"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| depCityCode | string | true | dep_airport |
| depDate | string | true | date |
| arrCityCode | string | true | arr_airport |

## administrative-divisions

- skillDescription: 全国行政区划
- intent: `query`
- actionType: `read`

### 全国行政区划

- toolDescription: 全国行政区划
- outputSlotKeysInferred: `["city", "address"]`

- inputSchema:
```json
{
  "properties": {
    "cityId": {
      "description": "市级行政区ID（市辖区/市辖县），获取区县级行政区",
      "type": "string"
    },
    "countyId": {
      "description": "区县级行政区ID，获取乡镇（街道）级行政区",
      "type": "string"
    },
    "provinceId": {
      "description": "省级行政区ID（含直辖市），获取市级行政区",
      "type": "string"
    },
    "townId": {
      "description": "乡镇（街道）级行政区ID，获取社区（村）级行政区",
      "type": "string"
    },
    "villageId": {
      "description": "社区（村）级行政区ID，获取全部上级行政区",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| cityId | string | false | city |
| countyId | string | false | address |
| provinceId | string | false | address |
| townId | string | false | address |
| villageId | string | false | address |

## precious-metal-price

- skillDescription: 国际贵金属期货合约
- intent: `query`
- actionType: `read`

### 国际贵金属期货合约

- toolDescription: 国际贵金属期货合约
- outputSlotKeysInferred: `["symbol"]`

- inputSchema:
```json
{
  "properties": {
    "symbol": {
      "description": "国际贵金属品种，详见国际贵金属现货，详见国际贵金属期货",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| symbol | string | false | symbol |

### 国内贵金属K线

- toolDescription: 国内贵金属K线
- outputSlotKeysInferred: `["result_limit", "symbol"]`

- inputSchema:
```json
{
  "properties": {
    "limit": {
      "description": "返回条数 默认10",
      "type": "string"
    },
    "symbol": {
      "description": "国内贵金属品种，仅支持现货AUTD,AGTD和期货的月份合约，详见国内贵金属现货，详见国内贵金属期货",
      "type": "string"
    },
    "type": {
      "description": "k线类型 0：日k 1：1分钟 5：五分钟 30：30分钟 60：60分钟 120：120分钟 240：240分钟",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| limit | string | false | result_limit |
| symbol | string | false | symbol |
| type | string | false | unknown |

### 国际贵金属报价

- toolDescription: 国际贵金属报价
- outputSlotKeysInferred: `["symbol"]`

- inputSchema:
```json
{
  "properties": {
    "symbol": {
      "description": "国际贵金属品种，详见国际贵金属现货，详见国际贵金属期货",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| symbol | string | false | symbol |

### 国内贵金属期货合约

- toolDescription: 国内贵金属期货合约
- outputSlotKeysInferred: `["symbol"]`

- inputSchema:
```json
{
  "properties": {
    "symbol": {
      "description": "国内贵金属品种，详见国内贵金属现货，详见国内贵金属期货",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| symbol | string | false | symbol |

### 国内贵金属报价

- toolDescription: 国内贵金属报价
- outputSlotKeysInferred: `["symbol"]`

- inputSchema:
```json
{
  "properties": {
    "symbol": {
      "description": "国内贵金属品种，详见国内贵金属现货，详见国内贵金属期货",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| symbol | string | false | symbol |

### 国际贵金属K线

- toolDescription: 国际贵金属K线
- outputSlotKeysInferred: `["result_limit", "symbol"]`

- inputSchema:
```json
{
  "properties": {
    "limit": {
      "description": "返回条数 默认10",
      "type": "string"
    },
    "symbol": {
      "description": "国际贵金属品种，详见国际贵金属现货，详见国际贵金属期货",
      "type": "string"
    },
    "type": {
      "description": "k线类型 0：日k 1：1分钟 5：五分钟 30：30分钟 60：60分钟 120：120分钟 240：240分钟",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| limit | string | false | result_limit |
| symbol | string | false | symbol |
| type | string | false | unknown |

## trustworthy-knowledge

- skillDescription: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
- intent: `query`
- actionType: `read`

### query

- toolDescription: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|

## 数据基础：

- skillDescription: 
- intent: `query`
- actionType: `read`

## 核心功能：

- skillDescription: 
- intent: `query`
- actionType: `read`

## 用户查询示例：

- skillDescription: 
- intent: `query`
- actionType: `read`

## delivery-inquiry

- skillDescription: 快递网点查询V2
- intent: `query`
- actionType: `read`

### 快递网点查询V2

- toolDescription: 快递网点查询V2
- outputSlotKeysInferred: `["address", "city", "shipper_code"]`

- inputSchema:
```json
{
  "properties": {
    "address": {
      "description": "地址信息，shipperCode为SF、JTSD时必传",
      "type": "string"
    },
    "areaName": {
      "description": "区县",
      "type": "string"
    },
    "cityName": {
      "description": "城市",
      "type": "string"
    },
    "provinceName": {
      "description": "省份",
      "type": "string"
    },
    "shipperCode": {
      "description": "快递公司编码。目前支持顺丰速运：SF、中通快递：STO、圆通快递：YTO、申通快递：STO、韵达快递：YD、极兔速递：JTSD、德邦快递：DBL、邮政平邮：YZPY",
      "type": "string"
    }
  },
  "required": [
    "address",
    "cityName",
    "areaName",
    "provinceName",
    "shipperCode"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| address | string | true | address |
| areaName | string | true | address |
| cityName | string | true | city |
| provinceName | string | true | address |
| shipperCode | string | true | shipper_code |

### 快递单号识别

- toolDescription: 根据快递运单号 自动识别快递公司
- outputSlotKeysInferred: `["express_no"]`

- inputSchema:
```json
{
  "properties": {
    "number": {
      "description": "运单编号",
      "type": "string"
    }
  },
  "required": [
    "number"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| number | string | true | express_no |

### 快递查询V2

- toolDescription: 根据快递代号 和 快递单号查询实时物流信息
- outputSlotKeysInferred: `["shipper_code", "express_no", "sort_by"]`

- inputSchema:
```json
{
  "properties": {
    "expressCode": {
      "description": "快递公司编号 例如圆通:YTO，详见产品说明中：快递公司编码对照表     注意：快递公司编号不传时，系统会自动识别快递公司编号，但响应时间会比传递快递编号略长",
      "type": "string"
    },
    "mobile": {
      "description": "顺丰速运、中通、跨越速运需要传入收/寄件人手机号或后四位手机号",
      "type": "string"
    },
    "number": {
      "description": "运单编号",
      "type": "string"
    },
    "sort": {
      "description": "物流明细排序，desc：倒序，asc：升序，默认asc",
      "type": "string"
    }
  },
  "required": [
    "number",
    "mobile",
    "expressCode",
    "sort"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| expressCode | string | true | shipper_code |
| mobile | string | true | unknown |
| number | string | true | express_no |
| sort | string | true | sort_by |

## agricultural-product-data

- skillDescription: 商品条码查询
- intent: `query`
- actionType: `read`

### 商品条码查询

- toolDescription: 商品条码查询
- outputSlotKeysInferred: `["org_code"]`

- inputSchema:
```json
{
  "properties": {
    "code": {
      "description": "商品条形码（国内及进口商品、8位商品短码、UPC-A、UPC-E）",
      "type": "string"
    }
  },
  "required": [
    "code"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| code | string | true | org_code |

## lunar-calendar

- skillDescription: 节假日列表
- intent: `query`
- actionType: `read`

### 节假日列表

- toolDescription: 节假日列表
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{
  "properties": {
    "year": {
      "description": "需要查询的年份【注意： 默认查当年，非当年日期也返回当年节假日数据，来年数据需等到当年12月份才能查】",
      "type": "string"
    }
  },
  "required": [
    "year"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| year | string | true | unknown |

### 黄历运势_新版_黄历

- toolDescription: 黄历运势_新版_黄历
- outputSlotKeysInferred: `["date"]`

- inputSchema:
```json
{
  "properties": {
    "date": {
      "description": "查询的日期 格式为yyyyMMdd",
      "type": "string"
    }
  },
  "required": [
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| date | string | true | date |

### 节假日详情

- toolDescription: 节假日详情
- outputSlotKeysInferred: `["date"]`

- inputSchema:
```json
{
  "properties": {
    "date": {
      "description": "查询的日期，默认当天",
      "type": "string"
    },
    "needDesc": {
      "description": "是否需要返回当日公众日、国际日和我国传统节日的简介，1-返回，默认不返回",
      "type": "string"
    }
  },
  "required": [
    "date",
    "needDesc"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| date | string | true | date |
| needDesc | string | true | unknown |

### 黄历运势_新版_吉神凶煞

- toolDescription: 黄历运势_新版_吉神凶煞
- outputSlotKeysInferred: `["date"]`

- inputSchema:
```json
{
  "properties": {
    "date": {
      "description": "查询的日期 格式为yyyyMMdd",
      "type": "string"
    }
  },
  "required": [
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| date | string | true | date |

### 黄历运势_新版_吉时

- toolDescription: 黄历运势_新版_吉时
- outputSlotKeysInferred: `["date"]`

- inputSchema:
```json
{
  "properties": {
    "date": {
      "description": "查询的日期 格式为yyyyMMdd",
      "type": "string"
    }
  },
  "required": [
    "date"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| date | string | true | date |

## sequential-thinking

- skillDescription: sequential-thinking技能，用于相关能力调用。
- intent: `query`
- actionType: `read`

### sequentialThinking

- toolDescription: sequentialThinking：用于相关能力调用。
- outputSlotKeysInferred: `null`

- inputSchema:
```json
{
  "additionalProperties": false,
  "properties": {
    "thought": {
      "type": "string",
      "description": "Your current thinking step"
    },
    "nextThoughtNeeded": {
      "type": "boolean",
      "description": "Whether another thought step is needed"
    },
    "thoughtNumber": {
      "type": "integer",
      "format": "int32",
      "description": "Current thought number"
    },
    "totalThoughts": {
      "type": "integer",
      "format": "int32",
      "description": "Estimated total thoughts needed"
    },
    "isRevision": {
      "type": "boolean",
      "description": "Whether this revises previous thinking"
    },
    "revisesThought": {
      "type": "integer",
      "format": "int32",
      "description": "Which thought is being reconsidered"
    },
    "branchFromThought": {
      "type": "integer",
      "format": "int32",
      "description": "Branching point thought number"
    },
    "branchId": {
      "type": "string",
      "description": "Branch identifier"
    },
    "needsMoreThoughts": {
      "type": "boolean",
      "description": "If more thoughts are needed"
    }
  },
  "required": [
    "thought",
    "nextThoughtNeeded",
    "thoughtNumber",
    "totalThoughts",
    "isRevision",
    "revisesThought",
    "branchFromThought",
    "branchId",
    "needsMoreThoughts"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| thought | string | true | unknown |
| nextThoughtNeeded | boolean | true | unknown |
| thoughtNumber | integer | true | unknown |
| totalThoughts | integer | true | unknown |
| isRevision | boolean | true | unknown |
| revisesThought | integer | true | unknown |
| branchFromThought | integer | true | unknown |
| branchId | string | true | unknown |
| needsMoreThoughts | boolean | true | unknown |

## short-link-generator

- skillDescription: 生成短链接 10天（改为30天20250908）
- intent: `query`
- actionType: `read`

### 短链接生成

- toolDescription: 生成短链接 10天（改为30天20250908）
- outputSlotKeysInferred: `["query_text"]`

- inputSchema:
```json
{
  "properties": {
    "target": {
      "description": "url链接",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| target | string | false | query_text |

### 短链接统计

- toolDescription: 统计短链接被点击的次数，包括总次数和ip个数
- outputSlotKeysInferred: `["date", "image_url", "query_text"]`

- inputSchema:
```json
{
  "properties": {
    "begin": {
      "description": "起始时间 格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd",
      "type": "string"
    },
    "end": {
      "description": "截止时间 格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd",
      "type": "string"
    },
    "link": {
      "description": "短链接  【短链接和原始链接至少传入一个】",
      "type": "string"
    },
    "target": {
      "description": "原始链接   【短链接和原始链接至少传入一个】",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| begin | string | false | date |
| end | string | false | date |
| link | string | false | image_url |
| target | string | false | query_text |

## fuel-price

- skillDescription: 查询今日油价
- intent: `query`
- actionType: `read`

### 今日油价查询

- toolDescription: 查询今日油价
- outputSlotKeysInferred: `["address"]`

- inputSchema:
```json
{
  "properties": {
    "province": {
      "description": "省份",
      "type": "string"
    }
  },
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| province | string | false | address |

## ip-address

- skillDescription: IP定位查询
- intent: `query`
- actionType: `read`

### IP定位查询

- toolDescription: IP定位查询
- outputSlotKeysInferred: `["ip"]`

- inputSchema:
```json
{
  "properties": {
    "ip": {
      "description": "ipV4地址",
      "type": "string"
    }
  },
  "required": [
    "ip"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| ip | string | true | ip |

## time

- skillDescription: time技能，用于时间信息查询。
- intent: `query`
- actionType: `read`

### convertTime

- toolDescription: convertTime：用于时间信息查询。
- outputSlotKeysInferred: `["date", "timezone"]`

- inputSchema:
```json
{
  "additionalProperties": false,
  "properties": {
    "time": {
      "type": "string",
      "description": "Time to convert in 24-hour format (HH:MM)"
    },
    "sourceTimezone": {
      "type": "string",
      "description": "Source IANA timezone name"
    },
    "targetTimezone": {
      "type": "string",
      "description": "Target IANA timezone name"
    }
  },
  "required": [
    "time",
    "sourceTimezone",
    "targetTimezone"
  ],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| time | string | true | date |
| sourceTimezone | string | true | timezone |
| targetTimezone | string | true | timezone |

### getCurrentTime

- toolDescription: getCurrentTime：用于时间信息查询。
- outputSlotKeysInferred: `["timezone"]`

- inputSchema:
```json
{
  "additionalProperties": false,
  "properties": {
    "timezone": {
      "type": "string",
      "description": "IANA timezone name (e.g., 'America/New_York', 'Europe/London')"
    }
  },
  "required": [],
  "type": "object"
}
```

- outputSchema (official):
```json
null
```

| fieldPath | fieldType | required | slotKey |
|---|---|---|---|
| timezone | string | false | timezone |
