# Ag:reborn · Academy generation reborn

Ag:reborn（Ag重生）是基于 **Lambda Innovation** 开源项目 [AcademyCraft](https://github.com/LambdaInnovation/AcademyCraft) 移植、修改和扩展的非官方 Minecraft 模组。原作者及原项目的署名、许可和附加声明予以保留；本项目不代表原作者的官方发布或背书。

本地联测版本为 **0.81.5-city-a3.87-rc2**，适用于 **Minecraft 1.21.1、NeoForge 21.1.252 和 Java 21**。整合包内置 **ACA Omnitrix 0.8.4**、**ACA Infinity 0.5.6** 和 **Titan Ecology Expansion V4.8**。

a3.87-rc2 调整开放兼容模式下的技能使用条件。管理员执行 `/academy dev <能力系>` 后可以立即测试 LV5 技能。四个 LV5 技能加入实际伤害，爆破技能支持地形落点、可恢复弹坑或溅射。

a3.87-rc1 在 a3.86 上加入 148 种生态生物和寄生、血肉、菌群、机械四条生态链的基地。生态系统可识别对应的 ACA 巨兽。客户端和服务端的 `mods` 目录各使用一份本体整合 JAR 即可。[V4.8 来源与构建说明](source/integration/titan-ecology/README.md)

a3.86 重制了乌鲁无限手套。佩戴位置随玩家惯用手切换，拇指朝内，第三人称的腕口套在小臂上。指节分段弯曲，六颗原石各自嵌入对应座圈。

a3.85 的普通 LV5 拥有 2,000 生命、600 基础盾和 110 基准技能强度。能力者从 LV3 开始加快成长。常规重招按约 200 点规划，超电磁炮等少数绝招接近 300 点。连击按整招分配伤害，同阶和跨阶对战都使用技能自身的伤害、原生减伤和实际盾血。旧档按生命与各盾层的剩余比例迁移。

a3.84 将 LV1 至 LV5 的基准技能强度提升到 8、14、24、38、54。已学习的低阶技能随能力等级提升，高阶经典技能另有阶位倍率。霸体盾挡住位移及负面控制，六石伤害由原生受击流程处理。集齐六石后的响指抹除本次选中的生命。属性 HUD 默认显示半透明生命条和盾条，F7 切换显示，Alt 展开读数，宝石轮盘打开时收起属性显示。

a3.83 在 ACA 本体加入鱼形计算者。方块占地一格、高两格。三张全身立绘按放置随机选择，分别面向各自的观察者。右键可打开菜单并开关投影。创造栏可取用，也可用玻璃板、两块铁锭、计算芯片和红石粉合成。

a3.82 将普通 LV5 调整为 100 生命、50 基础盾和 24 基准技能强度。戴上 X 表、变为 X 天神或使用阿斯卡隆时，生命和受影响的盾池按各自剩余比例切换容量。都市地图页、小地图和属性 HUD 共用终端风格；按住 Alt 可查看完整读数和护盾来源。

a3.81 根据实体实际盾血、死亡救援和移除原因结算技能。可辨认的单击封顶、阶段减伤和第三方取消各自生效。心灵傀儡、心灵宝石和鬼影附身共用归属，受控者接受点名后保护主人。现实宝石可限时封印主动接入的防御装备。[跨模组战斗接口](source/docs/跨模组战斗接口.md)

a3.80 解除 X 表的变身时限和腕表充能，修复模组生物心控与灾变焰魔的对玩家生命百分比伤害。无限手套默认按英文逗号摘戴。

a3.78 修复 LV5 落空费用、道具强化提示，并补齐 12 系终极演出。详见 [a3.78 发布记录](https://github.com/xkmlidoczy-del/Ag-reborn/releases/tag/v0.81.5-city-a3.78)。

- **全模组一体破限与四层模组 AI 接管**：Academy 能力者、十二巨兽、Omnitrix、阿斯卡隆与无限原石共用 `NativeCombatDamage` 原生单次结算破限管线（含灾变 3.33 单击与 DPS 桶适配）；鬼影支配、X 天神定格与心灵/时间/灵魂原石均实装四层通用模组 AI 接管与傀儡代打兜底。
- **零遮挡 U1 微弧 HUD 与 V1 次世代特效**：腕表与无限手套统一升级为准星发丝微弧 HUD 与无黑框全息选角/选石交互，接入三色阶 LUT、双相位流场、Voronoi 镜面空间碎裂与三维多股曲线能量鞭渲染。
- **全模组零键位冲突**：主模组能力开关为 `V`；腕表轮盘 `Y`、圣剑覆甲 `M`、副技能 `N`；无限手套选石 `H`、原石主副技能 `J` / `K`、响指 `Z`（圣殿二号 `G`/`B`/`V` 仅乘船激活）。[腕表与圣剑说明](source/integration/aca-omnitrix/README.md) · [无限手套与旗舰说明](source/integration/aca-infinity/README.md)

整合 JAR 同时包含 **ACA Mech 0.4.5**、**ACA Fauna 0.1.18**、**ACA bio HUM 0.9.1**、**ACA Omnitrix 0.8.4**、**ACA Infinity 0.5.6** 和 **GeckoLib 4.7**。升级时请从 `mods/` 移出旧版 Ag 重生及上述五个配套模组的独立 JAR，再在客户端和服务端安装同一份整合 JAR。 [历史发布 · a3.78 整合 JAR](https://github.com/xkmlidoczy-del/Ag-reborn/releases/download/v0.81.5-city-a3.78/Ag-reborn-a3.78.jar)。

自动生成人物只有主城第七学区无窗之楼的一名亚雷斯塔。首次靠近时，在原始塔体内安装倒悬维生舱，生成记录随世界保存。塔东侧约 `-134 0 -152`，可用 `/biohum windowless enter` 进楼及 `/biohum windowless exit` 返回。100 枚人物刷怪蛋位于「学园都市人物 NPC」。动物在原版主世界及规划公园/湖岸按生境生成，机甲可在「ACA 学园机库」部署。[人物整合说明](source/integration/aca-hum/README.md)

## 构建

安装 JDK 21，然后在终端运行：

```sh
cd source
./gradlew jar
```

Windows 可使用 `gradlew.bat jar`。首次构建需要联网获取 Gradle、NeoForge、Minecraft 开发依赖和 GeckoLib 等依赖；成品位于 `source/build/libs/`。所有内嵌子模组与 GeckoLib 4.7 随构建自动打包进单一模组 JAR。客户端和服务器应使用相同版本。

## 目录

- `source/src/main/` 为本体源码和资产，`source/src/hum/`、`source/src/fauna/`、`source/src/mech/`、`source/src/omnitrix/` 和 `source/src/infinity/` 分别为内置人物、动物、机甲、腕表和无限手套模块。
- `source/integration/aca-infinity/` 为无限手套与圣殿二号模块整合说明及声明。
- `source/integration/aca-omnitrix/` 为变身腕表与阿斯卡隆整合说明、来源及第三方许可。
- `source/integration/aca-hum/` 为人物整合说明和来源及权利声明。
- `source/integration/aca-fauna/` 记录动物整合版本；随动物模块提供素材出处和许可。
- `source/integration/aca-mech/`：机甲许可、来源、可编辑 Blockbench 方盒模型与导出工具；`source/src/mechQa/` 为独立测试驱动。
- `source/src/test/`、`source/qa/`：测试代码、测试夹具及辅助脚本；部分历史人工或原生客户端验证仍需要外部环境。
- `source/model-sources/`：已纳入快照的可编辑模型源文件。
- `source/docs/`、`source/NOTICE`：技术说明、来源和第三方版权声明。

公开快照保留构建源码、运行资源、测试夹具及原作者署名。选定贴图清理了非画面元数据，解码像素保持一致。重新构建的 JAR 摘要受这些元数据影响。[来源与清理说明](SOURCE_PROVENANCE.md)

## 原作者与许可

原作品：**AcademyCraft by Lambda Innovation**，经典参考版本 1.0.7。标准 GPLv3 文本见 [LICENSE](LICENSE)，上游附加声明及第三方组件的版权说明见 [NOTICE](NOTICE) 和 `source/docs/`。原项目附加声明包括禁止出售的条款；本仓库保留这些原始声明，并不对其与 GPLv3 的法律关系作出结论。LambdaLib、GeckoLib 和其他组件保留各自的许可及署名。

内置机甲代码按 MIT 许可提供；转换模型和贴图按其声明采用 CC0。原始机甲模型由 Ramon Linares 提供；详见 [机甲声明](source/integration/aca-mech/NOTICE.md) 和同目录许可文件。

人物模块是《魔法禁书目录》同人内容，角色及原著设定属于原权利人。人物模块保留独立版的权利声明。[人物模块来源及权利说明](source/integration/aca-hum/NOTICE.md)
