# 中国行政区划快照说明

- 来源项目：AreaCity-JsSpider-StatsGov
- 来源版本：2025.251231.260403
- 发布页：https://github.com/xiangyuecn/AreaCity-JsSpider-StatsGov/releases/tag/2025.251231.260403
- 发布附件：https://github.com/xiangyuecn/AreaCity-JsSpider-StatsGov/releases/download/2025.251231.260403/ok_data_level3-4.csv.7z
- 原始附件期望 SHA-256：0b181b4105c32b2631b1c8c8654859f10684c3748b188ebe08f342291dec1169
- 原始附件实际 SHA-256：0b181b4105c32b2631b1c8c8654859f10684c3748b188ebe08f342291dec1169
- 摘要校验结果：一致，通过后才解压和转换
- 发布核验日期：2026-07-22
- 下载日期：2026-07-22
- 许可证：MIT
- 规范化文件 SHA-256：6a9e6401c56e6d47c141abfd59fefe8c1bfa3f5a62c049d5fd2045c7e07d53de
- 规范化输入：附件内的 ok_data_level4.csv；未读取 ok_data_level3.csv
- 节点数量：省级 34 个，市级 392 个，区县级 3233 个，共 3659 个

## 规范化摘要

文件保留原始唯一 id 字符串作为项目地区编码，完整名称直接取自原始数据的 ext_name，不补零、不自行生成名称。中国大陆与台湾省直接保留省、市、区县三级。香港和澳门保留同名市级过渡节点，跳过重复的同名区县补齐节点，将原始第四层的香港分区和澳门堂区提升为项目第三级。规范化结果不包含乡镇街道等第四级业务节点。
