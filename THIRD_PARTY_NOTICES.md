# 第三方代码

## TinyPinyin (vendored)

- 路径：`app/src/main/java/com/github/promeg/pinyinhelper/`（保留原始包名）
- 来源：https://github.com/promeg/tinypinyin tag `v2.0.3`（commit `c4c65b9c0c82cc8e60ef081e91f3cc6678dcc`）
- 许可证：Apache License 2.0，全文见 [docs/licenses/TINYPINYIN-APACHE-2.0.txt](docs/licenses/TINYPINYIN-APACHE-2.0.txt)
- 用途：`search/` 匹配引擎的汉字→拼音基础数据（单读音）
- 修改：未修改原文件；多音字补充表见 `PolyphoneTable`（本项目自建）
- 引入方式：源码 vendoring（原 Maven 坐标发布在已关停的 jcenter，见 docs/spec.md F2）

## ahocorasick

- 坐标：`org.ahocorasick:ahocorasick:0.3.0`（Maven Central）
- 来源：https://github.com/robert-bor/ahocorasick
- 许可证：Apache License 2.0
- 用途：vendored TinyPinyin 的传递依赖（词组分段引擎；本项目仅用单字查表，但编译需要）

