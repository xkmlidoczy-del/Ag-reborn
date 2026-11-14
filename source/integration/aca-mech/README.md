# ACA Mech 内置模块

Ag 重生 a3.63-mech.1 在同一个 JAR 中内置 ACA Mech 0.4.3。机甲仍以 `acamech` 模组和命名空间加载，保留原有实体、物品、配方和网络标识。玩家不需要再放入独立机甲 JAR；升级时应移出旧的独立 ACA Mech 和旧的 Ag 重生 JAR。服务端与客户端均应使用同一整合版本。

机甲源代码及游戏资源在 `../../src/mech/`，测试驱动在 `../../src/mechQa/`。Gradle 用 `mechJar` 构建内置模块，`jar` 将它与现有 GeckoLib 一起打包；测试代码不会放入生产包。Java 21 下使用 `./gradlew jar mechQaJar`。素材版权、上游来源及 MIT/CC0 许可见本目录 `NOTICE.md`、`LICENSE`、`ASSET-LICENSE.md` 与 `CC0-1.0.txt`；三个可编辑方盒模型及贴图在 `art/v0.4.3/`。

编辑 Blockbench 模型后，可在本目录的 Python 虚拟环境安装 `requirements-models.txt`，再运行 `python tools/package_boxes.py`，将模型导出到 `src/mech` 的游戏资源。普通构建使用已导出的资源，不要求安装 Pillow。

三个部署箱既有普通材料配方，也有基于 ACA 材料的配方；重型电池可给机甲补能。G 是机甲远程，也是旧枪械的默认射击键；坐在机甲内时旧枪械不会因为该键射击。可在按键设置中重新绑定。

内置 0.4.3 的服务端侧没有全局伤害事件处理器，数据包仅添加机甲自身配方；原生测试覆盖其加入严格伤害／采矿契约后的识别。其他机甲版本、额外未审查模组及数据包仍按原契约拒绝。多人外网、高延迟、额外模组组合和所有技能对机甲的交互未在此次逐项验证。
