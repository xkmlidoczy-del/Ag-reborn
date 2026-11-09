# 最终同构建单端执行清单

仅准备计划，未启动游戏或Gradle。脚本逐字不变，全部66项使用新`final16-`证据前缀，均为`planned_not_run`。

| 组 | 客户端 | 固定脚本tick（含每项120tick世界等待） | 20TPS分钟 |
|---|---:|---:|---:|
| main | 33 | 25744 | 21.45 |
| modes | 7 | 16951 | 14.13 |
| passives | 7 | 20246 | 16.87 |
| tpvm | 7 | 5953 | 4.96 |
| vmenv | 3 | 1864 | 1.55 |
| itemSplit | 2 | 1232 | 1.03 |
| stormcycle | 1 | 1467 | 1.22 |
| visual | 4 | 2478 | 2.06 |
| supplemental | 2 | 2467 | 2.06 |

合计固定 78402 tick，20TPS约 65.33 分钟。另按每次客户端启动25–35秒估计增加27.50–38.50分钟，合计约 92.83–103.83 分钟；真实走路等待、风翼启动轮询、重载/渲染/截图/哈希/归档和掉TPS另计，不是墙钟上限。
共有10处真实walk，其重试最多另计2400tick；条件escape最多另计24tick。

主33覆盖95个主动技能，接地回流必须由独立真实双端grounding补齐第96个；被动7覆盖四系12训练与3被动共15条；模式7逐字保留51个独立源场景。main里的disintegration_sweep重复覆盖攻击/采矿，其他跨组重复是要求的不同玩法、资源/箭处理、视觉和边界，不删。

现有verifier分组使用新生成的gameplay-final16-main/modes/passives-plan.json；按audit.json的groups名称列表从全部runner results筛选原样结果行，分别输出结果文件，不改变name/script/exit。其余19项不在现有47客户端verifier范围，必须另逐项保留真实exit0、QA_DONE、无失败、同编译与mods快照、原严格实效断言。

全部66项目录为run-showcase-sable；当前其外置mods有Sable2.0.6，run-showcase外置mods为空，两者不能强称same-mod。原版兼容对照应另列且保留，不为same-mod合同删除。目录清单不涵盖启动classpath依赖，实际每案snapshot为准。

普通物品在较早实机出现未带坐标的距离/玩家静止断言失败，batch15原阈值独立重跑通过，旧失败原因仍未确定。final16普通物品+方块组必须再次真实通过；组中item先行若失败即阻断该组，不使用后续未执行方块冒充通过。

所有现有脚本SHA、分支源场景、名称映射、directory、props和逐案时长见gameplay-final-all-single-audit.json。计划中仅main33与visual4保留finalVisual=true；其他原props保持空。原prerequisite说明只作为源计划历史备注，最终是否已闭环由实际构建与root证据决定，不在此伪造通过。

main33全部95技能的非视觉机械route已独立列在gameplay-final16-main-mechanical-routes.json，含实际setup/断言的步骤位置、Java handler与必须出现的机制PASS；不能仅凭finalVisual覆盖。extras19已生成gameplay-final16-extras-plan.json，含每项及合并每源的精确requiredLogMarkers；扩展verifier由cast代理独占实现，未在本计划准备阶段运行。
