# Titan Ecology Expansion V4.8

本目录保存 Titan Ecology Expansion Project 交付的“四链基地”版本。上游在模组元数据中标注 MIT，模组 ID 为 `titanecology`。原始 JAR 的 SHA-256 为 `80823a40462a04a46e198dadb8ebc10f42726987f8f9fdd4fe0da4995e507d76`。上游沿用 `0.1.0` 版本号，模组列表标题仍显示 V4.7。

在 `source/` 目录使用 Java 21 运行 `./gradlew jar`。Gradle 从本目录解析该依赖，校验原始文件后将其嵌入 ACA 整合 JAR。客户端和服务端都使用同一份整合包，NeoForge 负责加载内部模组和 GeckoLib。

整合层在 `data/titanecology/tags/entity_type/` 合并四条生态链的 ACA 巨兽注册名。寄生链识别 `academy:ossuary_dragon`，血肉链识别 `academy:lava_rex`，菌群链识别 `academy:grove_titan`，机械链识别 `academy:clockwork_rex`。其余八只 ACA 巨兽加入 `neighbor_titans`。

ACA 将原版护甲属性上限提高到了 100。机械形态的自身护甲加成按 V4.8 原有的 30 点上限换算，外部装备和效果继续按 ACA 的原生属性规则叠加。
