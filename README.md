# Ag:reborn · Academy generation reborn

Ag:reborn（Ag重生）是基于 **Lambda Innovation** 开源项目 [AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft) 移植、修改和扩展的非官方 Minecraft 模组。原作者及原项目的署名、许可和附加声明予以保留；本项目不代表原作者的官方发布或背书。

当前公开源码版本为 **0.81.5-city-a3.67 Beta**，适用于 **Minecraft 1.21.1、NeoForge 21.1.252 和 Java 21**。本版内置 **ACA Fauna 0.1.17 R18** 的 53 种动物和 13 套幼体模型，沿用独立版的 `aca_fauna` 注册名和存档字段。动物在原版主世界按生境自然生成，两张学园都市地图保留既有生态，附属动物可用刷怪蛋或命令放置。

本版同时包含 ACA Mech 0.4.5、a3.64 的技能数值与范围基线，以及之后的 Iris、墙面停束、气流护幕与电梯安全修复。升级时将旧 Ag 重生、独立 ACA Fauna 和独立 ACA Mech 的 JAR 移出 `mods/`，安装本仓库构建的整合 JAR。客户端与服务端使用同版。机甲仍可在创造模式「ACA 学园机库」部署，动物蛋位于「刷怪蛋」。

## 构建

安装 JDK 21，然后在终端运行：

```sh
cd source
./gradlew jar
```

Windows 可使用 `gradlew.bat jar`。首次构建需要联网获取 Gradle、NeoForge、Minecraft 开发依赖和 GeckoLib 等依赖；成品位于 `source/build/libs/`。GeckoLib 4.7 随构建的模组打包。客户端和服务器应使用相同版本。

## 目录

- `source/src/main/` 为本体源码和资产，`source/src/mech/` 和 `source/src/fauna/` 为内置机甲及动物模块。
- `source/integration/aca-fauna/` 记录动物整合版本；随动物模块提供素材出处和许可。
- `source/integration/aca-mech/`：机甲许可、来源、可编辑 Blockbench 方盒模型与导出工具；`source/src/mechQa/` 为独立测试驱动。
- `source/src/test/`、`source/qa/`：测试代码、测试夹具及辅助脚本；部分历史人工或原生客户端验证仍需要外部环境。
- `source/model-sources/`：已纳入快照的可编辑模型源文件。
- `source/docs/`、`source/NOTICE`：技术说明、来源和第三方版权声明。

公开快照保留构建源码、运行资源、测试夹具及原作者署名。选定贴图清理了非画面元数据，解码像素保持一致。重新构建的 JAR 摘要受这些元数据影响。[来源与清理说明](SOURCE_PROVENANCE.md)

## 原作者与许可

原作品：**AcademyCraft by Lambda Innovation**，经典参考版本 1.0.7。标准 GPLv3 文本见 [LICENSE](LICENSE)，上游附加声明及第三方组件的版权说明见 [NOTICE](NOTICE) 和 `source/docs/`。原项目附加声明包括禁止出售的条款；本仓库保留这些原始声明，并不对其与 GPLv3 的法律关系作出结论。LambdaLib、GeckoLib 和其他组件保留各自的许可及署名。

内置机甲代码按 MIT 许可提供；转换模型和贴图按其声明采用 CC0。原始机甲模型由 Ramon Linares 提供；详见 [机甲声明](source/integration/aca-mech/NOTICE.md) 和同目录许可文件。
