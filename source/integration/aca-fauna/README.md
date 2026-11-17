# ACA Fauna R18 整合记录

本体内置的动物模块来自 `aca-fauna` 工程的 `b529444228f639b2ce417da029907ec94a6d61f9`。核对基线为独立最终包 `aca-fauna-0.1.17.jar`，SHA-256 为 `72c374ca0d574e506f0c2d9bed7891e02222ee02b4f4dde382d5164e701b1f37`。

`source/src/fauna/` 为整合版的运行源码与资源。`faunaJar` 将其构建为 `aca_fauna` 模块，ACA 的 `jar` 任务将该模块、ACA Mech 和一份 GeckoLib 4.7 打进同一安装包。`aca_fauna` 的注册名、声音和保存字段沿用独立版。来源说明与许可随 `src/fauna/resources/META-INF/licenses/` 入包。Blockbench 编辑源留在原动物工程。

整合版修正了视线偏移在缺少转动关键帧的骨骼上累加的问题。ACA 本体的 `CityFaunaSpawns` 屏蔽该模块在两张都市地图中因平原生境表触发的自动生成，刷怪蛋和命令仍可使用。原版世界的模型、食性、繁殖、警戒及生境配置继续由动物模块管理。

精确战斗和采掘兼容校验分别识别已核对的 Fauna 0.1.17 及其数据包。该模组只向原版水下呼吸实体标签追加条目，其他数据使用自己的命名空间。
