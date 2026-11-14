# 调研报告：Appdialer Pro 的开源替代（2026-11）

## 背景

- 用户自 2018 年前购买 Appdialer Pro（$4.99），使用 7.5.1 至今
- 原作者（@pilgr / @AppDialer）的产品线历史：
  - ~2019：Pro 版停止更新
  - 2022-06-24：发布付费新版本 AppDialer X（`name.pilgr.appdialer.x`），理由是"大家希望更新 SDK，但不想免费做"（[原推](https://x.com/AppDialer/status/1540410439066333185)）
  - 2022-08-31 之后购买的 Pro 用户可免费，老付费用户不符合条件
  - 后续 AppDialer 11 / 12 均为付费版
- 核心痛点：target SDK 陈旧 + 按 Android 大版本重复收费

## 需求画像

| 需求 | 说明 |
|---|---|
| 内置 T9 九宫格键盘 | 不唤起系统输入法（默认中文输入法调起慢） |
| 中文应用名支持 | 全拼 / 首字母 / 多音字 / 混合匹配 |
| 只搜应用 | 不要联系人、计算器、web 搜索 |
| 轻量 | 无网络权限，体积极小 |
| 现代 SDK | target SDK 36+，长期跟进 |

## 候选项目评估

| 项目 | 许可证 | 结论 |
|---|---|---|
| [Quick Search](https://github.com/teja2495/quick-search)（640★，target SDK 37，F-Droid 在架） | MIT | 功能是"万能搜索 + 可选 launcher"超集（计算器/单位换算/AI 搜索/文件/书签…），太重；作为代码参考价值高（搜索排序、overlay、触发方式实现） |
| [t9-app-dialer](https://github.com/officialdad/t9-app-dialer)（4★，92 commits） | MIT | 交互模型接近（T9 键盘浮窗），但不支持中文应用名；项目小，疑似 AI 辅助生成，成熟度存疑 |
| QuickLaunch（Professional-X） | 名义 MIT | **仓库只有 docs 和 APK，无源代码**，排除 |
| T9Launcher（h3110w0r1d，2025-09 于小众软件论坛自荐） | — | 曾完美命中（全拼/首字母/多音字/混合输入、`0` 键特殊字符、长按快捷启动、快速输入不丢字符、无网络权限），**但 GitHub 仓库已 404**，仅存 Play 商店闭源版 |
| KISS / Kvaesitso / F-Droid 各 launcher | 多为 GPL | 均为完整桌面替换，非"随时呼出的搜索层"，不对位 |
| antonio-fasolato/T9Launcher（1★，2026-03） | Apache-2.0 | 太小，无拼音支持证据 |

## 算法层可复用资产

- [PinyinSearchLibrary](https://github.com/handsomezhou/PinyinSearchLibrary)（522★，Apache-2.0）：T9 + Qwerty 拼音搜索完整算法（解析/匹配/多音字/高亮），锤子桌面使用过；依赖 pinyin4j。可作为自研 Kotlin 实现的参考或直接移植
- [YiiGuxing/T9Search](https://github.com/YiiGuxing/T9Search)：T9 匹配另一实现，可交叉参考

## 结论

精确对位（T9 键盘 + 拼音 + 只搜应用 + 轻量 + 现代 SDK）且维护良好的开源项目**不存在**——最接近的 T9Launcher 开源版存活不到一年即消失。自研 + GPL-3.0 开源有真实价值，算法层站在 PinyinSearchLibrary 肩膀上，交互层参考 Appdialer Pro 与 T9Launcher 的设计。

## 本项目要修的已知坑（来自同类产品）

1. 冻结应用启动失败（cached app freezer，T9Launcher 论坛反馈）
2. 应用列表冷加载慢 → 磁盘缓存图标/元数据
3. 快速连打丢字符（手指移出按钮再抬起时，按下的数字仍应上屏）
4. 中文应用名无法 T9 匹配
