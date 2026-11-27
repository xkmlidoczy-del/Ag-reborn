# Titan Ecology Expansion V4.9

本目录保存从 Titan Ecology Expansion 上游源码构建的 V4.9 主体贴图复修版。模组 ID 为 `titanecology`，元数据版本为 `0.1.0`，模组列表显示 V4.9，许可证为 MIT。内嵌 JAR 的 SHA-256 为 `debf2e8682ed07fd14b4c4f334269eed8fdd0018f823bf6ee402f782ffa989d8`。

V4.9 增加分基地的分段道路检查和断路停运，按世界写入预算推进建设，保护隔离信标覆盖区域。包内新增 48 套结构和 8 款方向构件，四种地网和四种墙材使用加权变体。六种样板生物使用新的裁切片模型与贴图。

美术复修覆盖六种样板及六套阶段变体。翼膜、筋膜、背棘和散热片各自接入翼架、骨肋、背甲与背包铰链，配色取自相邻材质。编辑工程的模型和内嵌贴图与游戏资源同步。

主体贴图复修覆盖十种生物和八套 S2／S3 阶段造型。材质沿模型的头、背、腹、四肢与装甲绘制。阶段模型的 114 个共用 UV 面采用各自的图集位置。断腱驰龙、噬骸嵌合猎犬和灾厄凝袭沼鲸的眼组已移至头壳表面。

V4.9 上游源码已包含 rc4 的头部骨骼修复，在每次绘制结束时恢复头颈和身体的动画姿态。原 a3.87-rc4 发布包内嵌的 V4.8 修订哈希为 `9782f16cf9f43c3a7176febe111293c00097d4b835ab45ccb44161fa097ab8b0`。[V4.8 渲染修复补丁](head-rotation.patch)

在 `source/` 目录使用 Java 21 运行 `./gradlew jar`。Gradle 从本目录解析该依赖，校验所用版本的文件后将其嵌入 ACA 整合 JAR。客户端和服务端都使用同一份整合包，NeoForge 负责加载内部模组和 GeckoLib。

整合层在 `data/titanecology/tags/entity_type/` 合并四条生态链的 ACA 巨兽注册名。寄生链识别 `academy:ossuary_dragon`，血肉链识别 `academy:lava_rex`，菌群链识别 `academy:grove_titan`，机械链识别 `academy:clockwork_rex`。其余八只 ACA 巨兽加入 `neighbor_titans`。

ACA 将原版护甲属性上限提高到了 100。机械形态的自身护甲加成按 V4.8 原有的 30 点上限换算，外部装备和效果继续按 ACA 的原生属性规则叠加。
