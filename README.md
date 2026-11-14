# Open AppDialer

> 开源复刻 [Appdialer Pro](https://play.google.com/store/apps/details?id=name.pilgr.appdialer.pro) —— 内置 T9 九宫格键盘，输入几位数字即可搜索并启动 Android 应用。
>
> Open-source reincarnation of Appdialer Pro: a featherweight T9 app search & launcher, with full Chinese pinyin support.

## 为什么做这个

- Appdialer Pro 7.5.1 的 target SDK 过旧，在新版 Android 上问题渐多
- 原作者自 2022 年起对每个新版本（AppDialer X / 11 / 12）重复收费，[作者自述](https://x.com/AppDialer/status/1540410439066333185)
- 现有开源替代品各有缺口：
  - Quick Search：功能是"万能搜索"超集，太重
  - t9-app-dialer：不支持中文应用名
  - T9Launcher：曾完美命中需求，但开源仓库已下线（2025-09 自荐，现已 404）

详见 [docs/research.md](docs/research.md)。

## 功能范围

完整范围见 [docs/scope.md](docs/scope.md)，技术规格见 [docs/spec.md](docs/spec.md)，要点：

1. **内置 T9 九宫格键盘**，全程不唤起系统输入法（中文输入法调起慢是主要痛点）
2. **中文拼音搜索**：全拼 + 首字母 + 多音字 + 混合匹配（微信 → `93946` / `99`）
3. 英文/数字名模糊匹配，特殊字符用 `0` 键兜底（搜 `1DM+` 输 `1dm0`）
4. **只做一件事**：搜索已装应用并启动（无联系人 / widget / 图标包 / 快速启动面板）
5. 深浅两色主题，跟随系统 + 手动切换
6. 轻量：无网络权限，APK 体积目标 < 1 MB 级别；target SDK 36+，长期跟进 Android 版本
7. 仅 Android；界面与搜索明确支持中文、英文

## 计划修复的已知问题（同类应用踩过的坑）

- [ ] 被系统冻结（cached app freezer）的应用无法直接启动
- [ ] 应用列表冷启动慢 → 图标与元数据磁盘缓存
- [ ] 快速连打丢字符 → 按下后手指移出按钮再抬起，数字仍应上屏
- [ ] 中文应用名只能按英文匹配的尴尬

## 技术路线

- Kotlin + Jetpack Compose，单 module
- 拼音/T9 匹配：参考 [PinyinSearchLibrary](https://github.com/handsomezhou/PinyinSearchLibrary)（Apache-2.0，锤子桌面同款算法）自研 Kotlin 实现
- 应用列表：PackageManager + 增量广播（`PACKAGE_ADDED` / `PACKAGE_REMOVED` / `PACKAGE_CHANGED`）

## 参考与致谢

- [Appdialer Pro](https://play.google.com/store/apps/details?id=name.pilgr.appdialer.pro) — 交互范本
- [PinyinSearchLibrary](https://github.com/handsomezhou/PinyinSearchLibrary)（Apache-2.0）— T9 拼音匹配算法
- T9Launcher（已下线）— 交互与功能设计参考
- [t9-app-dialer](https://github.com/officialdad/t9-app-dialer)、[Quick Search](https://github.com/teja2495/quick-search) — 相关实现参考

## 许可证

[GPL-3.0](LICENSE)。任何人可以自由使用、修改、分发本项目（包括商用），但衍生作品必须以同等许可证开源发布。
