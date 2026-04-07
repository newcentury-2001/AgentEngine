# MCP tools/list 导出结果

- 时间: 2026-04-07T02:35:01.206083300
- 总服务数: 28

## recognition

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/recognition/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 3

### location_recognition

- 描述: Recognize the location of the image.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The url of the input image.",
      "title" : "Img Url",
      "type" : "string"
    }
  },
  "required" : [ "img_url" ],
  "type" : "object"
}
```

### person_recognition

- 描述: Recognize people's name from an image or a region in the image, if a bbox is provided.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The url of the input image.",
      "title" : "Img Url",
      "type" : "string"
    },
    "bbox" : {
      "default" : "",
      "description" : "Crop box coordinates in thousandths. - Format: [x1, y1, x2, y2], each in 0-999. If provided, only the region inside the bounding box will be recognized. (Optional)",
      "title" : "Bbox",
      "type" : "string"
    },
    "ref_name" : {
      "default" : "",
      "description" : "The name or label of the object in the specified bounding box. (Optional)",
      "title" : "Ref Name",
      "type" : "string"
    }
  },
  "required" : [ "img_url" ],
  "type" : "object"
}
```

### plant_recognition

- 描述: Recognize the name of the plant in the image.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The url of the input image.",
      "title" : "Img Url",
      "type" : "string"
    }
  },
  "required" : [ "img_url" ],
  "type" : "object"
}
```

## image-search

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/image-search/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 5

### web_search

- 描述: Text web search using Search API.
- input_schema:

```json
{
  "properties" : {
    "query" : {
      "description" : "Search query string",
      "title" : "Query",
      "type" : "string"
    },
    "page_num" : {
      "default" : "0",
      "description" : "Page index starting from 0. Each page returns 5 results.",
      "title" : "Page Num",
      "type" : "string"
    }
  },
  "required" : [ "query" ],
  "type" : "object"
}
```

### search_image

- 描述: Search images using a query via image search api. Returns: title, snnipet, image url, thumbnails of max 5 relevant images.
- input_schema:

```json
{
  "properties" : {
    "query" : {
      "description" : "The query text for image search.",
      "title" : "Query",
      "type" : "string"
    }
  },
  "required" : [ "query" ],
  "type" : "object"
}
```

### image_search_with_image

- 描述: Reverse image search on an image via vision search api.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The image url of the target image.",
      "title" : "Img Url",
      "type" : "string"
    }
  },
  "required" : [ "img_url" ],
  "type" : "object"
}
```

### image_zoom_in_search_tool

- 描述: Zoom in on a specific region of an image by cropping it based on a bounding box (bbox) and an optional object ref_name. Then perform an image web search for the cropped image.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The image url of the target image.",
      "title" : "Img Url",
      "type" : "string"
    },
    "bbox" : {
      "default" : "",
      "description" : "The bounding box for cropping image. In the format of '[x1,y1,x2,y2]', range from 0 to 999.",
      "title" : "Bbox",
      "type" : "string"
    },
    "ref_name" : {
      "description" : "The name or label of the object in the specified bounding box (optional).",
      "title" : "Ref Name",
      "type" : "string"
    }
  },
  "required" : [ "img_url" ],
  "type" : "object"
}
```

### text_search_with_image

- 描述: Search web pages containing an image and include brief page text, along with visually similar images and best guess labels.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The image url of the target image.",
      "title" : "Img Url",
      "type" : "string"
    }
  },
  "required" : [ "img_url" ],
  "type" : "object"
}
```

## image-process

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/image-process/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 5

### crop

- 描述: Crop an image from an image url using the provided crop box and return the cropped image url.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The image url of the target image.",
      "title" : "Img Url",
      "type" : "string"
    },
    "box" : {
      "description" : "Crop box coordinates in thousandths. - Format: [x1, y1, x2, y2], each in 0-999.",
      "title" : "Box",
      "type" : "string"
    }
  },
  "required" : [ "img_url", "box" ],
  "type" : "object"
}
```

### draw_boxes

- 描述: Draw a rectangle or multiple rectangles on the image.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The image url of the target image.",
      "title" : "Img Url",
      "type" : "string"
    },
    "boxes" : {
      "description" : "Rectangle coordinates in thousandths. - Format: [[x1, y1, x2, y2][x1, y1, x2, y2]], each in 0-999.",
      "title" : "Boxes",
      "type" : "string"
    },
    "color" : {
      "default" : "red",
      "description" : "Outline color name or hex. Default as red.",
      "title" : "Color",
      "type" : "string"
    },
    "thickness" : {
      "default" : "3",
      "description" : "Outline thickness in pixels (>=1).Default as 3.",
      "title" : "Thickness",
      "type" : "string"
    }
  },
  "required" : [ "img_url", "boxes" ],
  "type" : "object"
}
```

### draw_point

- 描述: Draw a point (filled circle) on the image.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The image url of the target image.",
      "title" : "Img Url",
      "type" : "string"
    },
    "point" : {
      "description" : "Point coordinates in thousandths. - Format: [x, y], each in 0-999.",
      "title" : "Point",
      "type" : "string"
    },
    "color" : {
      "default" : "red",
      "description" : "Fill color name or hex. Default as red.",
      "title" : "Color",
      "type" : "string"
    },
    "radius" : {
      "default" : "4",
      "description" : "Circle radius in pixels (>=1). Default as 4.",
      "title" : "Radius",
      "type" : "string"
    }
  },
  "required" : [ "img_url", "point" ],
  "type" : "object"
}
```

### open_img_url

- 描述: Open an image from a given URL and return it as an Image.
- input_schema:

```json
{
  "properties" : {
    "url" : {
      "description" : "Direct image URL (http/https)",
      "title" : "Url",
      "type" : "string"
    }
  },
  "required" : [ "url" ],
  "type" : "object"
}
```

### image_reference

- 描述: Get the image and its url for future reference. Cropping is optional.
- input_schema:

```json
{
  "properties" : {
    "img_url" : {
      "description" : "The url of the target image.",
      "title" : "Img Url",
      "type" : "string"
    },
    "crop_box" : {
      "description" : "Crop box coordinates in thousandths. (Optional)\n- Format: [x1, y1, x2, y2], each in 0-999.",
      "title" : "Crop Box",
      "type" : "string"
    },
    "ref_name" : {
      "default" : "",
      "description" : "The name for the referenced image. (Optional)",
      "title" : "Ref Name",
      "type" : "string"
    }
  },
  "required" : [ "img_url", "crop_box" ],
  "type" : "object"
}
```

## trustworthy-search

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/trustworthy-search/sse?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### query

- 描述: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
## 数据基础：
 - 全国范围的法律法规库
 - 各行业政策文件集
 - 地市区县级规章制度
## 核心功能：
**精准召回**：从海量政策库中筛选出高相关性文件
## 用户查询示例：
 - 我身份证丢了怎么办？
 - 公积金提取有哪些渠道？
 - 龙岗区政府有什么人工智能领域的扶持政策？
 - 我想开办一个企业，名叫“中华饮料集团”可以么？
- input_schema:

```json
{
  "properties" : {
    "eff_time" : {
      "anyOf" : [ {
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "description" : "用户输入文本信息中若明确或隐含表达需要所属生效日期范围内检索材料时，则需提供对应的生效日期，最多只能提供一个值。日期格式为：• 只有年：xxxx年 •只有月：xxxx年xx⽉ 只有日：xxxx年xx⽉xx⽇",
      "title" : "Eff Time"
    },
    "service_area" : {
      "anyOf" : [ {
        "items" : {
          "type" : "string"
        },
        "type" : "array"
      }, {
        "type" : "null"
      } ],
      "description" : "用户输入文本信息属于政务办事类，则需提供对应的地域，最多只能提供一个值。",
      "title" : "Service Area"
    },
    "query" : {
      "description" : "要提问的问题。",
      "title" : "Query",
      "type" : "string"
    }
  },
  "required" : [ "query" ],
  "type" : "object"
}
```

## gaode-map

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/gaode-map/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 15

### maps_direction_bicycling

- 描述: 骑行路径规划用于规划骑行通勤方案，规划时会考虑天桥、单行线、封路等情况。最大支持 500km 的骑行路线规划
- input_schema:

```json
{
  "properties" : {
    "origin" : {
      "type" : "string",
      "description" : "出发点经纬度，坐标格式为：经度，纬度"
    },
    "destination" : {
      "type" : "string",
      "description" : "目的地经纬度，坐标格式为：经度，纬度"
    }
  },
  "required" : [ "origin", "destination" ],
  "type" : "object"
}
```

### maps_direction_driving

- 描述: 驾车路径规划 API 可以根据用户起终点经纬度坐标规划以小客车、轿车通勤出行的方案，并且返回通勤方案的数据。
- input_schema:

```json
{
  "properties" : {
    "origin" : {
      "type" : "string",
      "description" : "出发点经纬度，坐标格式为：经度，纬度"
    },
    "destination" : {
      "type" : "string",
      "description" : "目的地经纬度，坐标格式为：经度，纬度"
    }
  },
  "required" : [ "origin", "destination" ],
  "type" : "object"
}
```

### maps_direction_transit_integrated

- 描述: 根据用户起终点经纬度坐标规划综合各类公共（火车、公交、地铁）交通方式的通勤方案，并且返回通勤方案的数据，跨城场景下必须传起点城市与终点城市
- input_schema:

```json
{
  "properties" : {
    "origin" : {
      "type" : "string",
      "description" : "出发点经纬度，坐标格式为：经度，纬度"
    },
    "destination" : {
      "type" : "string",
      "description" : "目的地经纬度，坐标格式为：经度，纬度"
    },
    "city" : {
      "type" : "string",
      "description" : "公共交通规划起点城市"
    },
    "cityd" : {
      "type" : "string",
      "description" : "公共交通规划终点城市"
    }
  },
  "required" : [ "origin", "destination", "city", "cityd" ],
  "type" : "object"
}
```

### maps_direction_walking

- 描述: 根据输入起点终点经纬度坐标规划100km 以内的步行通勤方案，并且返回通勤方案的数据
- input_schema:

```json
{
  "properties" : {
    "origin" : {
      "type" : "string",
      "description" : "出发点经度，纬度，坐标格式为：经度，纬度"
    },
    "destination" : {
      "type" : "string",
      "description" : "目的地经度，纬度，坐标格式为：经度，纬度"
    }
  },
  "required" : [ "origin", "destination" ],
  "type" : "object"
}
```

### maps_distance

- 描述: 测量两个经纬度坐标之间的距离,支持驾车、步行以及球面距离测量
- input_schema:

```json
{
  "properties" : {
    "origins" : {
      "type" : "string",
      "description" : "起点经度，纬度，可以传多个坐标，使用竖线隔离，比如120,30|120,31，坐标格式为：经度，纬度"
    },
    "destination" : {
      "type" : "string",
      "description" : "终点经度，纬度，坐标格式为：经度，纬度"
    },
    "type" : {
      "type" : "string",
      "description" : "距离测量类型,1代表驾车距离测量，0代表直线距离测量，3步行距离测量"
    }
  },
  "required" : [ "origins", "destination" ],
  "type" : "object"
}
```

### maps_geo

- 描述: 将详细的结构化地址转换为经纬度坐标。支持对地标性名胜景区、建筑物名称解析为经纬度坐标
- input_schema:

```json
{
  "properties" : {
    "address" : {
      "type" : "string",
      "description" : "待解析的结构化地址信息"
    },
    "city" : {
      "type" : "string",
      "description" : "指定查询的城市"
    }
  },
  "required" : [ "address" ],
  "type" : "object"
}
```

### maps_regeocode

- 描述: 将一个高德经纬度坐标转换为行政区划地址信息
- input_schema:

```json
{
  "properties" : {
    "location" : {
      "type" : "string",
      "description" : "经纬度"
    }
  },
  "required" : [ "location" ],
  "type" : "object"
}
```

### maps_ip_location

- 描述: IP 定位根据用户输入的 IP 地址，定位 IP 的所在位置
- input_schema:

```json
{
  "properties" : {
    "ip" : {
      "type" : "string",
      "description" : "IP地址"
    }
  },
  "required" : [ "ip" ],
  "type" : "object"
}
```

### maps_schema_personal_map

- 描述: 用于行程规划结果在高德地图展示。将行程规划位置点按照行程顺序填入lineList，返回结果为高德地图打开的URI链接，该结果不需总结，直接返回！
- input_schema:

```json
{
  "properties" : {
    "orgName" : {
      "type" : "string",
      "description" : "行程规划地图小程序名称"
    },
    "lineList" : {
      "type" : "array",
      "description" : "行程列表",
      "items" : {
        "type" : "object",
        "properties" : {
          "title" : {
            "type" : "string",
            "description" : "行程名称描述（按行程顺序）"
          },
          "pointInfoList" : {
            "type" : "array",
            "description" : "行程目标位置点描述",
            "items" : {
              "type" : "object",
              "properties" : {
                "name" : {
                  "type" : "string",
                  "description" : "行程目标位置点名称"
                },
                "lon" : {
                  "type" : "number",
                  "description" : "行程目标位置点经度"
                },
                "lat" : {
                  "type" : "number",
                  "description" : "行程目标位置点纬度"
                },
                "poiId" : {
                  "type" : "string",
                  "description" : "行程目标位置点POIID"
                }
              },
              "required" : [ "name", "lon", "lat", "poiId" ]
            }
          }
        },
        "required" : [ "title", "pointInfoList" ]
      }
    }
  },
  "required" : [ "orgName", "lineList" ],
  "type" : "object"
}
```

### maps_around_search

- 描述: 周边搜，根据用户传入关键词以及坐标location，搜索出radius半径范围的POI
- input_schema:

```json
{
  "properties" : {
    "keywords" : {
      "type" : "string",
      "description" : "搜索关键词"
    },
    "location" : {
      "type" : "string",
      "description" : "中心点经度纬度"
    },
    "radius" : {
      "type" : "string",
      "description" : "搜索半径"
    },
    "strategy" : {
      "type" : "integer",
      "description" : "召回策略，0=默认召回策略，1=优先召回扫街榜POI",
      "default" : 0
    }
  },
  "required" : [ "keywords", "location" ],
  "type" : "object"
}
```

### maps_search_detail

- 描述: 查询关键词搜或者周边搜获取到的POI ID的详细信息
- input_schema:

```json
{
  "properties" : {
    "id" : {
      "type" : "string",
      "description" : "关键词搜或者周边搜获取到的POI ID"
    }
  },
  "required" : [ "id" ],
  "type" : "object"
}
```

### maps_text_search

- 描述: 关键字搜索 API 根据用户输入的关键字进行 POI 搜索，并返回相关的信息
- input_schema:

```json
{
  "properties" : {
    "keywords" : {
      "type" : "string",
      "description" : "查询关键字"
    },
    "city" : {
      "type" : "string",
      "description" : "查询城市"
    },
    "citylimit" : {
      "type" : "boolean",
      "default" : false,
      "description" : "是否限制城市范围内搜索，默认不限制"
    }
  },
  "required" : [ "keywords" ],
  "type" : "object"
}
```

### maps_schema_navi

- 描述: Schema唤醒客户端-导航页面，用于根据用户输入终点信息，返回一个拼装好的客户端唤醒URI，用户点击该URI即可唤起对应的客户端APP。唤起客户端后，会自动跳转到导航页面。
- input_schema:

```json
{
  "properties" : {
    "lon" : {
      "type" : "string",
      "description" : "终点经度"
    },
    "lat" : {
      "type" : "string",
      "description" : "终点纬度"
    }
  },
  "required" : [ "lon", "lat" ],
  "type" : "object"
}
```

### maps_schema_take_taxi

- 描述: 根据用户输入的起点和终点信息，返回一个拼装好的客户端唤醒URI，直接唤起高德地图进行打车。直接展示生成的链接，不需要总结
- input_schema:

```json
{
  "properties" : {
    "slon" : {
      "type" : "string",
      "description" : "起点经度"
    },
    "slat" : {
      "type" : "string",
      "description" : "起点纬度"
    },
    "sname" : {
      "type" : "string",
      "description" : "起点名称"
    },
    "dlon" : {
      "type" : "string",
      "description" : "终点经度"
    },
    "dlat" : {
      "type" : "string",
      "description" : "终点纬度"
    },
    "dname" : {
      "type" : "string",
      "description" : "终点名称"
    }
  },
  "required" : [ "dlon", "dlat", "dname" ],
  "type" : "object"
}
```

### maps_weather

- 描述: 根据城市名称或者标准adcode查询指定城市的天气
- input_schema:

```json
{
  "properties" : {
    "city" : {
      "type" : "string",
      "description" : "城市名称或者adcode"
    }
  },
  "required" : [ "city" ],
  "type" : "object"
}
```

## jina-reader

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/jina-reader/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### webReader

- 描述: Fetch and Convert URL to Large Model Friendly Input.
- input_schema:

```json
{
  "additionalProperties" : false,
  "properties" : {
    "url" : {
      "type" : "string",
      "description" : "The URL of the website to fetch and read"
    },
    "timeout" : {
      "type" : "integer",
      "format" : "int32",
      "description" : "Request timeout(unit is second), default is 20"
    },
    "no_cache" : {
      "type" : "boolean",
      "description" : "Disable cache(true/false), default is false"
    },
    "return_format" : {
      "type" : "string",
      "description" : "Reader response content type (markdown or text), default is markdown"
    },
    "retain_images" : {
      "type" : "boolean",
      "description" : "Retain images (true/false), default is true"
    },
    "no_gfm" : {
      "type" : "boolean",
      "description" : "Disable GitHub Flavored Markdown (true/false), default is false"
    },
    "keep_img_data_url" : {
      "type" : "boolean",
      "description" : "Keep image data URL (true/false), default is false"
    },
    "with_images_summary" : {
      "type" : "boolean",
      "description" : "Include images summary (true/false), default is false"
    },
    "with_links_summary" : {
      "type" : "boolean",
      "description" : "Include links summary (true/false), default is false"
    }
  },
  "required" : [ "url" ],
  "type" : "object"
}
```

## business-tax-id

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/business-tax-id/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### 企业税号查询

- 描述: 通过关键词查询税号
- input_schema:

```json
{
  "properties" : {
    "keyword" : {
      "description" : "关键词（公司名称/注册号/统一信用代码/法人/股东/高管等任意关键字）【汉字注意UrlEncode编码】",
      "type" : "string"
    },
    "pageNum" : {
      "description" : "当前页数（默认第1页）",
      "type" : "string"
    },
    "pageSize" : {
      "description" : "每页条数（默认20条，最大20条）",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

## holiday-inquiry

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/holiday-inquiry/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 2

### 假日列表

- 描述: 假日列表
- input_schema:

```json
{
  "properties" : {
    "year" : {
      "description" : "需要查询的年份，默认查询当年的节假日列表",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 节假日查询

- 描述: 节假日查询
- input_schema:

```json
{
  "properties" : {
    "day" : {
      "description" : "要查询是否放假的日期，格式YYYYMMDD",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

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

## lottery-results

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/lottery-results/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 4

### 查询是否中奖

- 描述: 查询是否中奖
- input_schema:

```json
{
  "properties" : {
    "caipiaoid" : {
      "description" : "彩票ID",
      "type" : "string"
    },
    "issueno" : {
      "description" : "期号 默认为最新一期",
      "type" : "string"
    },
    "number" : {
      "description" : "彩票号码 红球",
      "type" : "string"
    },
    "refernumber" : {
      "description" : "彩票剩余号码 蓝球",
      "type" : "string"
    },
    "type" : {
      "description" : "投注类型 1直选 2组三 3组六",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 彩票分类查询接口

- 描述: 查询彩票ID、名称和上级ID。
- input_schema:

```json
{
  "properties" : { },
  "type" : "object"
}
```

### 彩票开奖查询接口

- 描述: 通过彩票ID和期号查询彩票开奖信息。
- input_schema:

```json
{
  "properties" : {
    "caipiaoid" : {
      "description" : "彩票ID（彩票分类查询接口中获取）",
      "type" : "integer"
    },
    "issueno" : {
      "description" : "期号",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 历史开奖信息查询接口

- 描述: 通过彩票ID和期号查询历史开奖信息。
- input_schema:

```json
{
  "properties" : {
    "caipiaoid" : {
      "description" : "彩票ID",
      "type" : "integer"
    },
    "issueno" : {
      "description" : "期号 不选默认是当前期",
      "type" : "string"
    },
    "num" : {
      "description" : "获取数量 最大20 默认10",
      "type" : "integer"
    },
    "start" : {
      "description" : "起始位置 默认0",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

## this-day-in-history

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/this-day-in-history/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### 历史上的今天

- 描述: 历史上的今天
- input_schema:

```json
{
  "properties" : {
    "date" : {
      "description" : "日期，不写的话默认为当前天",
      "type" : "string"
    },
    "needContent" : {
      "description" : "是否返回历史事件的详细内容，1表示需要，0表示不需要",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

## exchange-rate

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/exchange-rate/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 3

### 十大银行的外汇牌价

- 描述: 十大银行的外汇牌价
- input_schema:

```json
{
  "properties" : {
    "bank" : {
      "description" : "银行编码。工商银行：ICBC ，中国银行：BOC ，农业银行：ABCHINA ，交通银行：BANKCOMM ，建设银行：CCB ，招商银行：CMBCHINA ，光大银行：CEBBANK ，浦发银行：SPDB ，兴业银行：CIB ，中信银行：ECITIC，默认BOC",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 单个货币查询接口

- 描述: 查询单个货币与其他货币间的汇率及更新时间。
- input_schema:

```json
{
  "properties" : {
    "currency" : {
      "description" : "货币（所有货币查询接口中获取）",
      "type" : "string"
    }
  },
  "required" : [ "currency" ],
  "type" : "object"
}
```

### 汇率转换接口

- 描述: 汇率转换
- input_schema:

```json
{
  "properties" : {
    "amount" : {
      "description" : "数量",
      "type" : "string"
    },
    "from" : {
      "description" : "要换算的单位（所有货币接口中获取，若为空取CNY或USD）",
      "type" : "string"
    },
    "to" : {
      "description" : "换算后的单位（所有货币接口中获取，若为空取CNY或USD）",
      "type" : "string"
    }
  },
  "required" : [ "from", "amount", "to" ],
  "type" : "object"
}
```

## postal-code

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/postal-code/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 3

### 地址查询邮编

- 描述: 通过地址查询邮编
- input_schema:

```json
{
  "properties" : {
    "address" : {
      "description" : "地址",
      "type" : "string"
    },
    "areaid" : {
      "description" : "区域ID",
      "type" : "integer"
    }
  },
  "type" : "object"
}
```

### 邮编查地址

- 描述: 通过邮编查询邮编地址
- input_schema:

```json
{
  "properties" : {
    "zipcode" : {
      "description" : "邮编",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 区域查询

- 描述: 查询邮编所在区域
- input_schema:

```json
{
  "properties" : { },
  "type" : "object"
}
```

## constellation

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/constellation/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### 星座运势查询

- 描述: 星座运势查询
- input_schema:

```json
{
  "properties" : {
    "needMonth" : {
      "description" : "是否需要本月运势的数据，1为需要，其他不需要",
      "type" : "string"
    },
    "needTomorrow" : {
      "description" : "是否需要明天的数据，1为需要，其他不需要",
      "type" : "string"
    },
    "needWeek" : {
      "description" : "是否需要本周运势的数据，1为需要，其他不需要",
      "type" : "string"
    },
    "needYear" : {
      "description" : "是否需要本年运势的数据，1为需要，其他不需要",
      "type" : "string"
    },
    "star" : {
      "description" : "十二星座，其值分别为 baiyang jinniu shuangzi juxie shizi chunv tiancheng tianxie sheshou mojie shuiping shuangyu",
      "type" : "string"
    }
  },
  "required" : [ "star" ],
  "type" : "object"
}
```

## knowledge-recall

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/knowledge-recall/sse?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### query

- 描述: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
## 数据基础：
 - 全国范围的法律法规库
 - 各行业政策文件集
 - 地市区县级规章制度
## 核心功能：
1.**智能匹配**：理解用户问题语义，匹配相关政策条文
2.**精准召回**：从海量政策库中筛选出高相关性文件
3.**结果排序**：按时间、相关度和权威性对政策清单进行排序
## 用户查询示例：
 - 我身份证丢了怎么办？
 - 公积金提取有哪些渠道？
 - 龙岗区政府有什么人工智能领域的扶持政策？
 - 我想开办一个企业，名叫“中华饮料集团”可以么？
- input_schema:

```json
{
  "properties" : {
    "policy" : {
      "default" : false,
      "description" : "是否需要规范性文件清单，true-需要，此时返回内容中会有policyFiles字段，false-不需要（非必填，默认为false，不会返回规范性文件清单）",
      "title" : "Policy",
      "type" : "boolean"
    },
    "item" : {
      "default" : false,
      "description" : "是否需要公共事项在线办理清单，true-需要，此时返回内容中会有recommendationItems字段，false-不需要（非必填，默认为false，不会返回公共事项在线办理清单）",
      "title" : "Item",
      "type" : "boolean"
    },
    "search" : {
      "default" : true,
      "description" : "联网搜索字段，true-开启，false-关闭（非必填项，默认为true，即开启联网搜索）",
      "title" : "Search",
      "type" : "boolean"
    },
    "query" : {
      "description" : "要提问的问题。",
      "title" : "Query",
      "type" : "string"
    }
  },
  "required" : [ "query" ],
  "type" : "object"
}
```

## tripmatch

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/tripmatch/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 9

### searchFlightsByDepArr

- 描述: Search for flights between airports or cities by date. For cities with multiple airports, use depcity and arrcity parameters; otherwise use dep and arr parameters. Date must be in YYYY-MM-DD format. All airport/city codes must be valid IATA 3-letter codes (e.g.BJS for city of Beijing, PEK for Beijing Capital Airport).
- input_schema:

```json
{
  "properties" : {
    "date" : {
      "description" : "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Date",
      "type" : "string"
    },
    "dep" : {
      "anyOf" : [ {
        "description" : "Departure airport IATA 3-letter code (e.g. PEK for Beijing, CAN for Guangzhou)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Dep"
    },
    "depcity" : {
      "anyOf" : [ {
        "description" : "Departure city IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Depcity"
    },
    "arr" : {
      "anyOf" : [ {
        "description" : "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, HFE for Hefei)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Arr"
    },
    "arrcity" : {
      "anyOf" : [ {
        "description" : "Arrival city IATA 3-letter code (e.g. SHA for Shanghai, BJS for Beijing)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Arrcity"
    }
  },
  "required" : [ "date" ],
  "type" : "object"
}
```

### searchFlightsByNumber

- 描述: Search flights by flight number and date. Flight number should include airline code (e.g. MU2157, CZ3969). dep and arr are optional, keep empty if you don't know them. Date format: YYYY-MM-DD. IMPORTANT: For today's date, you MUST use getTodayDate tool instead of hardcoding any date. Airport codes (optional) should be IATA 3-letter codes.
- input_schema:

```json
{
  "properties" : {
    "fnum" : {
      "description" : "Flight number including airline code (e.g. MU2157, CZ3969)",
      "title" : "Fnum",
      "type" : "string"
    },
    "date" : {
      "description" : "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Date",
      "type" : "string"
    },
    "dep" : {
      "anyOf" : [ {
        "description" : "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Dep"
    },
    "arr" : {
      "anyOf" : [ {
        "description" : "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Arr"
    }
  },
  "required" : [ "fnum", "date" ],
  "type" : "object"
}
```

### getFlightAndTrainTransferInfo

- 描述: Get flight and train transfer info by departure city and arrival city and departure date. Date format: YYYY-MM-DD. IMPORTANT: For today's date, you MUST use getTodayDate tool instead of hardcoding any date. Airport codes should be IATA 3-letter codes.
- input_schema:

```json
{
  "properties" : {
    "depcity" : {
      "description" : "Departure airport IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
      "title" : "Depcity",
      "type" : "string"
    },
    "arrcity" : {
      "description" : "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, LAX for Los Angeles)",
      "title" : "Arrcity",
      "type" : "string"
    },
    "depdate" : {
      "description" : "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Depdate",
      "type" : "string"
    }
  },
  "required" : [ "depcity", "arrcity", "depdate" ],
  "type" : "object"
}
```

### flightHappinessIndex

- 描述: using this tool when you need information related to following topics: Detailed flight comparisons (punctuality, amenities, cabin specs),Health safety protocols for air travel,Baggage allowance verification,Environmental impact assessments,Aircraft configuration visualization,Comfort-focused trip planning (seat dimensions, entertainment, food). etc.
- input_schema:

```json
{
  "properties" : {
    "fnum" : {
      "description" : "Flight number including airline code (e.g. MU2157, CZ3969)",
      "title" : "Fnum",
      "type" : "string"
    },
    "date" : {
      "description" : "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Date",
      "type" : "string"
    },
    "dep" : {
      "anyOf" : [ {
        "description" : "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Dep"
    },
    "arr" : {
      "anyOf" : [ {
        "description" : "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Arr"
    }
  },
  "required" : [ "fnum", "date" ],
  "type" : "object"
}
```

### getTodayDate

- 描述: Get today's date in local timezone (YYYY-MM-DD format). Use this tool whenever you need today's date - NEVER hardcode dates.
- input_schema:

```json
{
  "properties" : {
    "random_string" : {
      "anyOf" : [ {
        "description" : "Dummy parameter for no-parameter tools",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Random String"
    }
  },
  "type" : "object"
}
```

### getFutureWeatherByAirport

- 描述: Get airport future weather for 3days (today、tomorrow、the day after tomorrow) by airport IATA 3-letter code. Airport codes should be IATA 3-letter codes (e.g. PEK for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei).
- input_schema:

```json
{
  "properties" : {
    "airport" : {
      "description" : "Airport IATA 3-letter code (e.g. PEK for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
      "title" : "Airport",
      "type" : "string"
    }
  },
  "required" : [ "airport" ],
  "type" : "object"
}
```

### searchTrainTickets

- 描述: Search for train tickets between two cities on a specific date. Date must be in YYYY-MM-DD format.
- input_schema:

```json
{
  "properties" : {
    "from_city" : {
      "description" : "Departure city name (e.g. 合肥)",
      "title" : "From City",
      "type" : "string"
    },
    "to_city" : {
      "description" : "Arrival city name (e.g. 北京)",
      "title" : "To City",
      "type" : "string"
    },
    "date" : {
      "description" : "Travel date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Date",
      "type" : "string"
    }
  },
  "required" : [ "from_city", "to_city", "date" ],
  "type" : "object"
}
```

### getFlightPriceByCities

- 描述: Get flight price information by departure city, arrival city, and departure date. All city codes must be valid IATA 3-letter codes (e.g. HFE for Hefei, CAN for Guangzhou). Date must be in YYYY-MM-DD format.
- input_schema:

```json
{
  "properties" : {
    "dep_city" : {
      "description" : "Departure city IATA 3-letter code (e.g. HFE for Hefei)",
      "title" : "Dep City",
      "type" : "string"
    },
    "arr_city" : {
      "description" : "Arrival city IATA 3-letter code (e.g. CAN for Guangzhou)",
      "title" : "Arr City",
      "type" : "string"
    },
    "dep_date" : {
      "description" : "Departure date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Dep Date",
      "type" : "string"
    }
  },
  "required" : [ "dep_city", "arr_city", "dep_date" ],
  "type" : "object"
}
```

### searchTrainStations

- 描述: Search for train stations by keyword.
- input_schema:

```json
{
  "properties" : {
    "query" : {
      "description" : "Keyword to search for train stations (e.g. 北京西)",
      "title" : "Query",
      "type" : "string"
    }
  },
  "required" : [ "query" ],
  "type" : "object"
}
```

## aviation

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/aviation/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 8

### searchFlightsByDepArr

- 描述: Search for flights between airports or cities by date. For cities with multiple airports, use depcity and arrcity parameters; otherwise use dep and arr parameters. Date must be in YYYY-MM-DD format. All airport/city codes must be valid IATA 3-letter codes (e.g.BJS for city of Beijing, PEK for Beijing Capital Airport).
- input_schema:

```json
{
  "properties" : {
    "date" : {
      "description" : "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Date",
      "type" : "string"
    },
    "dep" : {
      "anyOf" : [ {
        "description" : "Departure airport IATA 3-letter code (e.g. PEK for Beijing, CAN for Guangzhou)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Dep"
    },
    "depcity" : {
      "anyOf" : [ {
        "description" : "Departure city IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Depcity"
    },
    "arr" : {
      "anyOf" : [ {
        "description" : "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, HFE for Hefei)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Arr"
    },
    "arrcity" : {
      "anyOf" : [ {
        "description" : "Arrival city IATA 3-letter code (e.g. SHA for Shanghai, BJS for Beijing)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Arrcity"
    }
  },
  "required" : [ "date" ],
  "type" : "object"
}
```

### searchFlightsByNumber

- 描述: Search flights by flight number and date. Flight number should include airline code (e.g. MU2157, CZ3969). dep and arr are optional, keep empty if you don't know them. Date format: YYYY-MM-DD. IMPORTANT: For today's date, you MUST use getTodayDate tool instead of hardcoding any date. Airport codes (optional) should be IATA 3-letter codes.
- input_schema:

```json
{
  "properties" : {
    "fnum" : {
      "description" : "Flight number including airline code (e.g. MU2157, CZ3969)",
      "title" : "Fnum",
      "type" : "string"
    },
    "date" : {
      "description" : "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Date",
      "type" : "string"
    },
    "dep" : {
      "anyOf" : [ {
        "description" : "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Dep"
    },
    "arr" : {
      "anyOf" : [ {
        "description" : "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Arr"
    }
  },
  "required" : [ "fnum", "date" ],
  "type" : "object"
}
```

### getFlightTransferInfo

- 描述: Get flight transfer info by departure city and arrival city and departure date. Date format: YYYY-MM-DD. IMPORTANT: For today's date, you MUST use getTodayDate tool instead of hardcoding any date. Airport codes should be IATA 3-letter codes.
- input_schema:

```json
{
  "properties" : {
    "depdate" : {
      "description" : "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Depdate",
      "type" : "string"
    },
    "depcity" : {
      "description" : "Departure airport IATA 3-letter code (e.g. BJS for Beijing, CAN for Guangzhou)",
      "title" : "Depcity",
      "type" : "string"
    },
    "arrcity" : {
      "description" : "Arrival airport IATA 3-letter code (e.g. SHA for Shanghai, LAX for Los Angeles)",
      "title" : "Arrcity",
      "type" : "string"
    }
  },
  "required" : [ "depdate", "depcity", "arrcity" ],
  "type" : "object"
}
```

### flightHappinessIndex

- 描述: using this tool when you need information related to following topics: Detailed flight comparisons (punctuality, amenities, cabin specs),Health safety protocols for air travel,Baggage allowance verification,Environmental impact assessments,Aircraft configuration visualization,Comfort-focused trip planning (seat dimensions, entertainment, food). etc.
- input_schema:

```json
{
  "properties" : {
    "fnum" : {
      "description" : "Flight number including airline code (e.g. MU2157, CZ3969)",
      "title" : "Fnum",
      "type" : "string"
    },
    "date" : {
      "description" : "Flight date in YYYY-MM-DD format. IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Date",
      "type" : "string"
    },
    "dep" : {
      "anyOf" : [ {
        "description" : "Departure airport IATA 3-letter code (e.g. HFE for Hefei)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Dep"
    },
    "arr" : {
      "anyOf" : [ {
        "description" : "Arrival airport IATA 3-letter code (e.g. CAN for Guangzhou)",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Arr"
    }
  },
  "required" : [ "fnum", "date" ],
  "type" : "object"
}
```

### getRealtimeLocationByAnum

- 描述: Get flight realtime location by aircraft number. aircraft number should be Aircraft registration numberlike B2021, B2022, B2023, etc. if aircraft number is unknown, you shuold try to request it using searchFlightsByNumber tool
- input_schema:

```json
{
  "properties" : {
    "anum" : {
      "description" : "Aircraft number like B2021, B2022, B2023, etc.",
      "title" : "Anum",
      "type" : "string"
    }
  },
  "required" : [ "anum" ],
  "type" : "object"
}
```

### getTodayDate

- 描述: Get today's date in local timezone (YYYY-MM-DD format). Use this tool whenever you need today's date - NEVER hardcode dates.
- input_schema:

```json
{
  "properties" : {
    "random_string" : {
      "anyOf" : [ {
        "description" : "Dummy parameter for no-parameter tools",
        "type" : "string"
      }, {
        "type" : "null"
      } ],
      "title" : "Random String"
    }
  },
  "type" : "object"
}
```

### getFutureWeatherByAirport

- 描述: Get airport future weather for 3days (today、tomorrow、the day after tomorrow) by airport IATA 3-letter code. Airport codes should be IATA 3-letter codes (e.g. PEK for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei).
- input_schema:

```json
{
  "properties" : {
    "airport" : {
      "description" : "Airport IATA 3-letter code (e.g. PEK for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
      "title" : "Airport",
      "type" : "string"
    }
  },
  "required" : [ "airport" ],
  "type" : "object"
}
```

### searchFlightItineraries

- 描述: Search for purchasable flight options and the lowest price using the departure city three-letter code, arrival city three-letter code, and departure date. (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei).
- input_schema:

```json
{
  "properties" : {
    "depCityCode" : {
      "description" : "Departure city 3-letter code (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
      "title" : "Depcitycode",
      "type" : "string"
    },
    "depDate" : {
      "description" : "Departure city date (format: YYYY-MM-DD, e.g., 2025-07-04).IMPORTANT: If user input only cotains month and date, you should use getTodayDate tool to get the year. For today's date, use getTodayDate tool instead of hardcoding",
      "title" : "Depdate",
      "type" : "string"
    },
    "arrCityCode" : {
      "description" : "Arrival city 3-letter code (e.g. BJS for Beijing, SHA for Shanghai, CAN for Guangzhou, HFE for Hefei)",
      "title" : "Arrcitycode",
      "type" : "string"
    }
  },
  "required" : [ "depCityCode", "depDate", "arrCityCode" ],
  "type" : "object"
}
```

## administrative-divisions

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/administrative-divisions/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### 全国行政区划

- 描述: 全国行政区划
- input_schema:

```json
{
  "properties" : {
    "cityId" : {
      "description" : "市级行政区ID（市辖区/市辖县），获取区县级行政区",
      "type" : "string"
    },
    "countyId" : {
      "description" : "区县级行政区ID，获取乡镇（街道）级行政区",
      "type" : "string"
    },
    "provinceId" : {
      "description" : "省级行政区ID（含直辖市），获取市级行政区",
      "type" : "string"
    },
    "townId" : {
      "description" : "乡镇（街道）级行政区ID，获取社区（村）级行政区",
      "type" : "string"
    },
    "villageId" : {
      "description" : "社区（村）级行政区ID，获取全部上级行政区",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

## precious-metal-price

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/precious-metal-price/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 6

### 国际贵金属期货合约

- 描述: 国际贵金属期货合约
- input_schema:

```json
{
  "properties" : {
    "symbol" : {
      "description" : "国际贵金属品种，详见国际贵金属现货，详见国际贵金属期货",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 国内贵金属K线

- 描述: 国内贵金属K线
- input_schema:

```json
{
  "properties" : {
    "limit" : {
      "description" : "返回条数 默认10",
      "type" : "string"
    },
    "symbol" : {
      "description" : "国内贵金属品种，仅支持现货AUTD,AGTD和期货的月份合约，详见国内贵金属现货，详见国内贵金属期货",
      "type" : "string"
    },
    "type" : {
      "description" : "k线类型 0：日k 1：1分钟 5：五分钟 30：30分钟 60：60分钟 120：120分钟 240：240分钟",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 国际贵金属报价

- 描述: 国际贵金属报价
- input_schema:

```json
{
  "properties" : {
    "symbol" : {
      "description" : "国际贵金属品种，详见国际贵金属现货，详见国际贵金属期货",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 国内贵金属期货合约

- 描述: 国内贵金属期货合约
- input_schema:

```json
{
  "properties" : {
    "symbol" : {
      "description" : "国内贵金属品种，详见国内贵金属现货，详见国内贵金属期货",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 国内贵金属报价

- 描述: 国内贵金属报价
- input_schema:

```json
{
  "properties" : {
    "symbol" : {
      "description" : "国内贵金属品种，详见国内贵金属现货，详见国内贵金属期货",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 国际贵金属K线

- 描述: 国际贵金属K线
- input_schema:

```json
{
  "properties" : {
    "limit" : {
      "description" : "返回条数 默认10",
      "type" : "string"
    },
    "symbol" : {
      "description" : "国际贵金属品种，详见国际贵金属现货，详见国际贵金属期货",
      "type" : "string"
    },
    "type" : {
      "description" : "k线类型 0：日k 1：1分钟 5：五分钟 30：30分钟 60：60分钟 120：120分钟 240：240分钟",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

## trustworthy-knowledge

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/trustworthy-knowledge/sse?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### query

- 描述: 从全量法规政策数据库中快速筛选和返回与用户查询最相关的**政策文件清单**。
## 数据基础：
 - 全国范围的法律法规库
 - 各行业政策文件集
 - 地市区县级规章制度
## 核心功能：
1.**智能匹配**：理解用户问题语义，匹配相关政策条文
2.**精准召回**：从海量政策库中筛选出高相关性文件
3.**结果排序**：按时间、相关度和权威性对政策清单进行排序
## 用户查询示例：
 - 我身份证丢了怎么办？
 - 公积金提取有哪些渠道？
 - 龙岗区政府有什么人工智能领域的扶持政策？
 - 我想开办一个企业，名叫“中华饮料集团”可以么？
- input_schema:

```json
{
  "properties" : {
    "policy" : {
      "default" : false,
      "description" : "是否需要规范性文件清单，true-需要，此时返回内容中会有policyFiles字段，false-不需要（非必填，默认为false，不会返回规范性文件清单）",
      "title" : "Policy",
      "type" : "boolean"
    },
    "item" : {
      "default" : false,
      "description" : "是否需要公共事项在线办理清单，true-需要，此时返回内容中会有recommendationItems字段，false-不需要（非必填，默认为false，不会返回公共事项在线办理清单）",
      "title" : "Item",
      "type" : "boolean"
    },
    "search" : {
      "default" : true,
      "description" : "联网搜索字段，true-开启，false-关闭（非必填项，默认为true，即开启联网搜索）",
      "title" : "Search",
      "type" : "boolean"
    },
    "query" : {
      "description" : "要提问的问题。",
      "title" : "Query",
      "type" : "string"
    }
  },
  "required" : [ "query" ],
  "type" : "object"
}
```

## delivery-inquiry

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/delivery-inquiry/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 3

### 快递网点查询V2

- 描述: 快递网点查询V2
- input_schema:

```json
{
  "properties" : {
    "address" : {
      "description" : "地址信息，shipperCode为SF、JTSD时必传",
      "type" : "string"
    },
    "areaName" : {
      "description" : "区县",
      "type" : "string"
    },
    "cityName" : {
      "description" : "城市",
      "type" : "string"
    },
    "provinceName" : {
      "description" : "省份",
      "type" : "string"
    },
    "shipperCode" : {
      "description" : "快递公司编码。目前支持顺丰速运：SF、中通快递：STO、圆通快递：YTO、申通快递：STO、韵达快递：YD、极兔速递：JTSD、德邦快递：DBL、邮政平邮：YZPY",
      "type" : "string"
    }
  },
  "required" : [ "address", "cityName", "areaName", "provinceName", "shipperCode" ],
  "type" : "object"
}
```

### 快递单号识别

- 描述: 根据快递运单号 自动识别快递公司
- input_schema:

```json
{
  "properties" : {
    "number" : {
      "description" : "运单编号",
      "type" : "string"
    }
  },
  "required" : [ "number" ],
  "type" : "object"
}
```

### 快递查询V2

- 描述: 根据快递代号 和 快递单号查询实时物流信息
- input_schema:

```json
{
  "properties" : {
    "expressCode" : {
      "description" : "快递公司编号 例如圆通:YTO，详见产品说明中：快递公司编码对照表     注意：快递公司编号不传时，系统会自动识别快递公司编号，但响应时间会比传递快递编号略长",
      "type" : "string"
    },
    "mobile" : {
      "description" : "顺丰速运、中通、跨越速运需要传入收/寄件人手机号或后四位手机号",
      "type" : "string"
    },
    "number" : {
      "description" : "运单编号",
      "type" : "string"
    },
    "sort" : {
      "description" : "物流明细排序，desc：倒序，asc：升序，默认asc",
      "type" : "string"
    }
  },
  "required" : [ "number", "mobile", "expressCode", "sort" ],
  "type" : "object"
}
```

## agricultural-product-data

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/product-barcode-query/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### 商品条码查询

- 描述: 商品条码查询
- input_schema:

```json
{
  "properties" : {
    "code" : {
      "description" : "商品条形码（国内及进口商品、8位商品短码、UPC-A、UPC-E）",
      "type" : "string"
    }
  },
  "required" : [ "code" ],
  "type" : "object"
}
```

## lunar-calendar

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/lunar-calendar/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 5

### 节假日列表

- 描述: 节假日列表
- input_schema:

```json
{
  "properties" : {
    "year" : {
      "description" : "需要查询的年份【注意： 默认查当年，非当年日期也返回当年节假日数据，来年数据需等到当年12月份才能查】",
      "type" : "string"
    }
  },
  "required" : [ "year" ],
  "type" : "object"
}
```

### 黄历运势_新版_黄历

- 描述: 黄历运势_新版_黄历
- input_schema:

```json
{
  "properties" : {
    "date" : {
      "description" : "查询的日期 格式为yyyyMMdd",
      "type" : "string"
    }
  },
  "required" : [ "date" ],
  "type" : "object"
}
```

### 节假日详情

- 描述: 节假日详情
- input_schema:

```json
{
  "properties" : {
    "date" : {
      "description" : "查询的日期，默认当天",
      "type" : "string"
    },
    "needDesc" : {
      "description" : "是否需要返回当日公众日、国际日和我国传统节日的简介，1-返回，默认不返回",
      "type" : "string"
    }
  },
  "required" : [ "date", "needDesc" ],
  "type" : "object"
}
```

### 黄历运势_新版_吉神凶煞

- 描述: 黄历运势_新版_吉神凶煞
- input_schema:

```json
{
  "properties" : {
    "date" : {
      "description" : "查询的日期 格式为yyyyMMdd",
      "type" : "string"
    }
  },
  "required" : [ "date" ],
  "type" : "object"
}
```

### 黄历运势_新版_吉时

- 描述: 黄历运势_新版_吉时
- input_schema:

```json
{
  "properties" : {
    "date" : {
      "description" : "查询的日期 格式为yyyyMMdd",
      "type" : "string"
    }
  },
  "required" : [ "date" ],
  "type" : "object"
}
```

## sequential-thinking

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/sequential-thinking/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### sequentialThinking

- 描述: A detailed tool for dynamic and reflective problem-solving through thoughts.
This tool helps analyze problems through a flexible thinking process that can adapt and evolve.
Each thought can build on, question, or revise previous insights as understanding deepens.

When to use this tool:
- Breaking down complex problems into steps
- Planning and design with room for revision
- Analysis that might need course correction
- Problems where the full scope might not be clear initially
- Problems that require a multi-step solution
- Tasks that need to maintain context over multiple steps
- Situations where irrelevant information needs to be filtered out

Key features:
- You can adjust total_thoughts up or down as you progress
- You can question or revise previous thoughts
- You can add more thoughts even after reaching what seemed like the end
- You can express uncertainty and explore alternative approaches
- Not every thought needs to build linearly - you can branch or backtrack
- Generates a solution hypothesis
- Verifies the hypothesis based on the Chain of Thought steps
- Repeats the process until satisfied
- Provides a correct answer

Parameters explained:
- thought: Your current thinking step, which can include:
* Regular analytical steps
* Revisions of previous thoughts
* Questions about previous decisions
* Realizations about needing more analysis
* Changes in approach
* Hypothesis generation
* Hypothesis verification
- next_thought_needed: True if you need more thinking, even if at what seemed like the end
- thought_number: Current number in sequence (can go beyond initial total if needed)
- total_thoughts: Current estimate of thoughts needed (can be adjusted up/down)
- is_revision: A boolean indicating if this thought revises previous thinking
- revises_thought: If is_revision is true, which thought number is being reconsidered
- branch_from_thought: If branching, which thought number is the branching point
- branch_id: Identifier for the current branch (if any)
- needs_more_thoughts: If reaching end but realizing more thoughts needed

You should:
1. Start with an initial estimate of needed thoughts, but be ready to adjust
2. Feel free to question or revise previous thoughts
3. Don't hesitate to add more thoughts if needed, even at the "end"
4. Express uncertainty when present
5. Mark thoughts that revise previous thinking or branch into new paths
6. Ignore information that is irrelevant to the current step
7. Generate a solution hypothesis when appropriate
8. Verify the hypothesis based on the Chain of Thought steps
9. Repeat the process until satisfied with the solution
10. Provide a single, ideally correct answer as the final output
11. Only set next_thought_needed to false when truly done and a satisfactory answer is reached
- input_schema:

```json
{
  "additionalProperties" : false,
  "properties" : {
    "thought" : {
      "type" : "string",
      "description" : "Your current thinking step"
    },
    "nextThoughtNeeded" : {
      "type" : "boolean",
      "description" : "Whether another thought step is needed"
    },
    "thoughtNumber" : {
      "type" : "integer",
      "format" : "int32",
      "description" : "Current thought number"
    },
    "totalThoughts" : {
      "type" : "integer",
      "format" : "int32",
      "description" : "Estimated total thoughts needed"
    },
    "isRevision" : {
      "type" : "boolean",
      "description" : "Whether this revises previous thinking"
    },
    "revisesThought" : {
      "type" : "integer",
      "format" : "int32",
      "description" : "Which thought is being reconsidered"
    },
    "branchFromThought" : {
      "type" : "integer",
      "format" : "int32",
      "description" : "Branching point thought number"
    },
    "branchId" : {
      "type" : "string",
      "description" : "Branch identifier"
    },
    "needsMoreThoughts" : {
      "type" : "boolean",
      "description" : "If more thoughts are needed"
    }
  },
  "required" : [ "thought", "nextThoughtNeeded", "thoughtNumber", "totalThoughts", "isRevision", "revisesThought", "branchFromThought", "branchId", "needsMoreThoughts" ],
  "type" : "object"
}
```

## short-link-generator

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/short-link-generator/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 2

### 短链接生成

- 描述: 生成短链接 10天（改为30天20250908）
- input_schema:

```json
{
  "properties" : {
    "target" : {
      "description" : "url链接",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

### 短链接统计

- 描述: 统计短链接被点击的次数，包括总次数和ip个数
- input_schema:

```json
{
  "properties" : {
    "begin" : {
      "description" : "起始时间 格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd",
      "type" : "string"
    },
    "end" : {
      "description" : "截止时间 格式：yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd",
      "type" : "string"
    },
    "link" : {
      "description" : "短链接  【短链接和原始链接至少传入一个】",
      "type" : "string"
    },
    "target" : {
      "description" : "原始链接   【短链接和原始链接至少传入一个】",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

## fuel-price

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/fuel-price/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### 今日油价查询

- 描述: 查询今日油价
- input_schema:

```json
{
  "properties" : {
    "province" : {
      "description" : "省份",
      "type" : "string"
    }
  },
  "type" : "object"
}
```

## ip-address

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/ip-address/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 1

### IP定位查询

- 描述: IP定位查询
- input_schema:

```json
{
  "properties" : {
    "ip" : {
      "description" : "ipV4地址",
      "type" : "string"
    }
  },
  "required" : [ "ip" ],
  "type" : "object"
}
```

## time

- URL: `https://open.bigmodel.cn/api/mcp-broker/proxy/time/mcp?Authorization=a0a568242c654099ad5c1fd4e2db048a.5naAs3Cml6OsKNtT`
- 状态: 成功
- 工具数: 2

### convertTime

- 描述: Convert time between timezones
- input_schema:

```json
{
  "additionalProperties" : false,
  "properties" : {
    "time" : {
      "type" : "string",
      "description" : "Time to convert in 24-hour format (HH:MM)"
    },
    "sourceTimezone" : {
      "type" : "string",
      "description" : "Source IANA timezone name"
    },
    "targetTimezone" : {
      "type" : "string",
      "description" : "Target IANA timezone name"
    }
  },
  "required" : [ "time", "sourceTimezone", "targetTimezone" ],
  "type" : "object"
}
```

### getCurrentTime

- 描述: Get current time in a specific timezone
- input_schema:

```json
{
  "additionalProperties" : false,
  "properties" : {
    "timezone" : {
      "type" : "string",
      "description" : "IANA timezone name (e.g., 'America/New_York', 'Europe/London')"
    }
  },
  "required" : [ ],
  "type" : "object"
}
```

