# ACA Omnitrix 0.8.1 整合

Ag 重生内置三只变身腕表（经典 Omnitrix、X 天神表、Carnitrix）与阿斯卡隆基本力圣剑，适用于 Minecraft 1.21.1、NeoForge 21.1.252 和 Java 21。升级前移出旧 Ag 重生及独立的 Omnitrix、ACA Infinity、ACA Mech、ACA Fauna 和 ACA bio HUM JAR，客户端和服务端安装同一份整合 JAR。

创造模式可在「ACA 变身腕表」栏领取。生存模式可用指南针右击学园都市公告板领取主世界坠落舱地图，或探索主世界坠落舱并通过封存台装配其余腕表。

- **无遮挡微弧 HUD 与无框星盘（U1）**：佩戴后采用准星两侧发丝级能量微弧（1.5px 细弧）替代传统底框面板；按 `Y` 抬腕展开无黑底全息选角轮盘，滚轮或方向键切换形态，回车确认，长按 `Y` 解除变身。
- **VFX 4.0 次世代特效（V1）**：接入三色阶 LUT 着色、双相位流场扰动、Voronoi 镜面空间碎裂、三维多股曲线能量鞭与傀儡支配光环。
- **四层通用模组 AI 强制接管**：鬼影支配与 X 天神定格支持原版 Goal、Brain 记忆行为树、灾变 Boss 冷却及自定义模组实体的多层接管与服务端傀儡代打。
- **零键位冲突**：主模组能力开关保留 `V`；整合环境下阿斯卡隆覆甲默认绑定 `M`，副技能默认绑定 `N`，持表或持剑时鼠标左右键自动优先控制装备。

Gradle 从 `source/src/omnitrix/` 编译 0.8.1 运行源码和资产，并作为 NeoForge Jar-in-Jar 模块打包进主 JAR。来源、署名及许可证见 [NOTICE](NOTICE.md) 和 [licenses](licenses/)。
