# Ag:reborn · Academy generation reborn

Ag:reborn（Ag重生）是基于 **Lambda Innovation** 开源项目 [AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft) 移植、修改和扩展的非官方 Minecraft 模组。原作者及原项目的署名、许可和附加声明予以保留；本项目不代表原作者的官方发布或背书。

当前公开源码版本为 **0.81.5-city-a3.63-mech.1 Beta**，适用于 **Minecraft 1.21.1、NeoForge 21.1.252 和 Java 21**。本版将 ACA Mech 0.4.3 内置到 Ag 重生：ATLAS、AETHER、SERAPH 三台机甲可部署、驾驶、近战、远程攻击、维修和充能，保留原有 `acamech` 存档 ID。新生成的学园都市建筑保留 a3.63 内装、家具和真实电梯；旧存档中涉及已有区块的建筑沿用原布局。

运行时只需安装本仓库构建的 Ag 重生 JAR。升级时移出旧版 Ag 重生和独立 ACA Mech JAR，客户端与服务器使用同一版本。机甲可在创造模式“ACA 学园机库”部署，也有生存合成配方。默认 W/S 移动、A/D 转向、X 专属动作、Z 近战、G 远程、Shift 下机。旧都市存档、真实远程多人及全部能力与机甲的交互尚未逐项复测。

## 构建

安装 JDK 21，然后在终端运行：

```sh
cd source
./gradlew jar
```

Windows 可使用 `gradlew.bat jar`。首次构建需要联网获取 Gradle、NeoForge、Minecraft 开发依赖和 GeckoLib 等依赖；成品位于 `source/build/libs/`。GeckoLib 4.7 随构建的模组打包。客户端和服务器应使用相同版本。

## 目录

- `source/src/main/`：模组代码、模型、贴图、音效和数据；`source/src/mech/` 是内置机甲代码和游戏资源。
- `source/integration/aca-mech/`：机甲许可、来源、可编辑 Blockbench 方盒模型与导出工具；`source/src/mechQa/` 为独立测试驱动。
- `source/src/test/`、`source/qa/`：测试代码、测试夹具及辅助脚本；部分历史人工或原生客户端验证仍需要外部环境。
- `source/model-sources/`：已纳入快照的可编辑模型源文件。
- `source/docs/`、`source/NOTICE`：技术说明、来源和第三方版权声明。

公开快照清理了本机路径、列车模型中的本机字段以及部分生成贴图的非画面元数据。原始像素和游戏逻辑保留；由于元数据不同，重新构建的 JAR 文件哈希可能与本地交付包不同。完整范围见 [来源与清理说明](SOURCE_PROVENANCE.md)。

## 原作者与许可

原作品：**AcademyCraft by Lambda Innovation**，经典参考版本 1.0.7。标准 GPLv3 文本见 [LICENSE](LICENSE)，上游附加声明及第三方组件的版权说明见 [NOTICE](NOTICE) 和 `source/docs/`。原项目附加声明包括禁止出售的条款；本仓库保留这些原始声明，并不对其与 GPLv3 的法律关系作出结论。LambdaLib、GeckoLib 和其他组件保留各自的许可及署名。

内置机甲代码按 MIT 许可提供；转换模型和贴图按其声明采用 CC0。原始机甲模型由 Ramon Linares 提供；详见 [机甲声明](source/integration/aca-mech/NOTICE.md) 和同目录许可文件。
