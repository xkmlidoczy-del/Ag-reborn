# Ag:reborn · Academy generation reborn

Ag:reborn（Ag重生）是基于 **Lambda Innovation** 开源项目 [AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft) 移植、修改和扩展的非官方 Minecraft 模组。原作者及原项目的署名、许可和附加声明予以保留；本项目不代表原作者的官方发布或背书。

这是与 **0.81.5-city-a3.61** 玩法对应的公开源码快照，适用于 **Minecraft 1.21.1、NeoForge 21.1.252 和 Java 21**。它包含十二能力系、学园都市、传送门、枪械和载具、守护小队、O5 增援与十二只巨型生物。都市部分区域、多人和长期游玩仍需进一步验证。

## 构建

安装 JDK 21，然后在终端运行：

```sh
cd source
./gradlew jar
```

Windows 可使用 `gradlew.bat jar`。首次构建需要联网获取 Gradle、NeoForge、Minecraft 开发依赖和 GeckoLib 等依赖；成品位于 `source/build/libs/`。GeckoLib 4.7 随构建的模组打包。客户端和服务器应使用相同版本。

## 目录

- `source/src/main/`：模组代码、模型、贴图、音效和数据。
- `source/src/test/`、`source/qa/`：测试代码、测试夹具及辅助脚本；部分历史人工或原生客户端验证仍需要外部环境。
- `source/model-sources/`：已纳入快照的可编辑模型源文件。
- `source/docs/`、`source/NOTICE`：技术说明、来源和第三方版权声明。

公开快照清理了本机路径、列车模型中的本机字段以及部分生成贴图的非画面元数据。原始像素和游戏逻辑保留；由于元数据不同，重新构建的 JAR 文件哈希可能与此前发布的 a3.61 不同。完整范围见 [来源与清理说明](SOURCE_PROVENANCE.md)。

## 原作者与许可

原作品：**AcademyCraft by Lambda Innovation**，经典参考版本 1.0.7。标准 GPLv3 文本见 [LICENSE](LICENSE)，上游附加声明及第三方组件的版权说明见 [NOTICE](NOTICE) 和 `source/docs/`。原项目附加声明包括禁止出售的条款；本仓库保留这些原始声明，并不对其与 GPLv3 的法律关系作出结论。LambdaLib、GeckoLib 和其他组件保留各自的许可及署名。
