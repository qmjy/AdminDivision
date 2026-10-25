# AdminDivision

从 OpenStreetMap `.osm.pbf` 文件中提取行政区划边界数据，导出为与
[osm-boundaries.com](https://osm-boundaries.com/) 下载格式一致的 GeoJSON
（参考样本：`OSMB-China.geojson`）。

基于 **Spring Boot + Maven** 构建，OSM 解析使用 Osmosis PBF 读取器。

## 输出格式

与 OSMB 导出文件一致，每个行政区一个 Feature：

```json
{
  "type": "Feature",
  "geometry": { "type": "Polygon", "coordinates": [[[lon, lat], ...]] },
  "properties": {
    "osm_id": 1208116850,
    "boundary": "national_park",
    "admin_level": null,
    "parents": "-4425368,-2777425,-286937,-270056",
    "name": "Xiaoqikong",
    "local_name": "小七孔风景区",
    "name_en": "Xiaoqikong",
    "all_tags": { "area": "yes", "name": "小七孔风景区", "...": "..." }
  }
}
```

说明：

- `osm_id`：relation 的正数 OSM ID；`parents` 中的 ID 为负数（与 OSMB 一致），
  从最近一级父级向上逐级排列。
- `geometry`：单个外环 + 洞时为 `Polygon`，多个外环时为 `MultiPolygon`。
- `name` 优先取 `name:en`（没有则取 `name`），`local_name` 取 `name`，
  `name_en` 取 `name:en`（可能为 null），与 OSMB 行为一致。
- 坐标保留 7 位小数（可通过 `--precision` 调整）。

## 构建

需要 JDK 17+ 和 Maven：

```bash
mvn package -DskipTests
```

产物：`target/admin-division-extractor-1.0.0.jar`（Spring Boot 可执行 jar）。

## 运行

```bash
java -Xmx8g -jar target/admin-division-extractor-1.0.0.jar china-latest.osm.pbf \
     --output=china.geojson --admin-levels=4,6,8 --simplify=0.001
```

> 建议给足堆内存（全国数据建议 `-Xmx8g` 以上）。

## 导出控制参数（对照 osm-boundaries.com 导出设置）

| 参数 | 对应 OSMB 设置 | 说明 |
|---|---|---|
| `<input.osm.pbf>` | 下载选择 | 位置参数，源 PBF 文件（必填） |
| `--output=<file>` | 输出格式（GeoJSON） | 输出文件路径，默认为输入文件名 + `.geojson` |
| `--admin-levels=<4,6,8>` 或 `<2-10>` | Admin level filter | 按 `admin_level` 过滤，逗号分隔或区间；空 = 全部 |
| `--boundary-types=<a,b>` | Boundary filter | 按 `boundary=*` 标签过滤，如 `administrative,national_park`；空 = 全部 |
| `--bbox=<minLon,minLat,maxLon,maxLat>` | 地图选择 | 范围框过滤，输出的几何与其相交即保留 |
| `--simplify=<factor>` | Simplify | Visvalingam–Whyatt 简化因子（度），`0.001≈100m`、`0.01≈1km`、`0.1≈10km`；`0` = 不简化 |
| `--include-tags=<true\|false>` | Tags | 是否输出 `all_tags` 属性（默认 true） |
| `--hierarchy=<true\|false>` | Hierarchy data | 是否输出 `parents` 层级链（默认 true） |
| `--srid=<4326>` | SRID | 目前仅支持 EPSG:4326（WGS84） |
| `--precision=<7>` | - | 输出坐标的小数位数 |
| `--verbose=<true\|false>` | - | 进度与告警输出（默认 true） |

参数也可以用 Spring 属性风格传入（等价）：`--export.input=... --export.admin-levels=...`。

## 与 OSMB 的已知差异

- `all_tags` 中不含 OSMB 后处理生成的 `way_area` 字段（OSMB 由 PostGIS 计算）。
- OSMB 的 "Water: land-only polygons"（仅陆地多边形，需海岸线处理）未实现，
  输出对应 "complete polygons"。
- 简化按环独立进行（OSMB 为拓扑感知简化，共享边界两侧可能产生细微差异）。

## 实现说明

- **三遍流式扫描**（relations → ways → nodes），只缓存行政区划成员节点坐标，
  内存占用远低于全量加载节点表。
- **环组装**：按端点节点拼接 outer/inner 成员 way 成闭合环（`RingAssembler`）。
- **洞归属**：内环按"最小包含外环"原则分配（point-in-polygon）。
- **简化**：Visvalingam–Whyatt 优先队列实现，O(n log n)。
- 无法闭合的坏数据会记录警告并跳过（stderr 输出）。

## 目录结构

```
src/main/java/cc/xqhui/admindivision/
├── AdminDivisionApplication.java   # Spring Boot 入口
├── BoundaryExtractorRunner.java    # 命令行参数解析与用法说明
├── ExportOptions.java              # 导出控制参数（对照 OSMB 设置）
├── osm/
│   ├── OsmPbfScanner.java          # Osmosis PBF 流式扫描
│   ├── BoundaryRelation.java       # boundary relation 内存模型
│   └── ExtractionEngine.java       # 三遍扫描 + 特征构建主流程
├── geo/
│   ├── RingAssembler.java          # 成员 way -> 闭合环
│   ├── GeometryOps.java            # 面积 / 包含 / bbox 计算
│   └── VisvalingamSimplifier.java  # 几何简化
└── export/
    └── GeoJsonWriter.java          # 流式 GeoJSON 输出（OSMB 格式）
```
