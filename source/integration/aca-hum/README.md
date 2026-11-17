# ACA bio HUM 0.9.1 接入 Ag 重生

`source/src/hum/` 收录独立交付版 0.9.1 的运行源码和资源。独立版 JAR 的 SHA-256 为 `bd455cedb4b63687bbceab4dbc37a4a64a58bbbb5b0f60f81f5ddb72c719501f`。Gradle 的 `humJar` 将此源码和 [来源与权利说明](NOTICE.md) 封入 `acabiohum` 模块，随本体的 `jar` 打包。实体 ID、存档字段、动作、材质和技能沿用独立版。

创造物品栏的「学园都市人物 NPC」分类收录全部 100 枚人物刷怪蛋。环境中唯一新增的自动生成是第七学区无窗之楼的亚雷斯塔。玩家靠近时，如果区块和实体数据已加载且 `doMobSpawning` 开启，服务器在塔内安装维生舱并放置一名倒悬状态的亚雷斯塔。安装前核对范围内的原始塔体，自动生成记录随世界保存。

塔东侧地面坐标约为 `-134 0 -152`，所在维度为 `academy:story_city_map`。在楼旁使用 `/biohum windowless enter`，离开用 `/biohum windowless exit`。原附属的手动建塔和角色指令继续可用。
