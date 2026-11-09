# 111 条真实学习补充验收计划

状态为 `planned_not_run`。本计划只新增 QA fixture 与脚本，未运行游戏或 Gradle，未修改生产学习费用、前置、熟练度、设备等级或后端请求。

`gameplay-learning111-plan.json` 分四个独立客户端覆盖完整登记目录：电击使27、原子崩坏29、空间移动27、矢量操作28，共111条（96主动、3被动、12训练）。四系同名课程用 `category/id` 选择与验证，不能只统计111个不带类别的名称。

| 技能等级 | 实际最低合格设备 | 原生刺激数 | 每刺激实际刻 | 单条原生开发刻 | 精确实际IF |
|---|---|---:|---:|---:|---:|
| LV1 | PORTABLE | 3 | 26 | 78 | 2340 |
| LV2 | PORTABLE | 5 | 26 | 130 | 3900 |
| LV3 | NORMAL | 7 | 21 | 147 | 5145 |
| LV4 | ADVANCED | 11 | 16 | 176 | 7040 |
| LV5 | ADVANCED | 15 | 16 | 240 | 9600 |

设备分布为40条PORTABLE、24条NORMAL、47条ADVANCED。数值由当前 `DeveloperType` 与 `ClassicRules.learningStimulations` 静态核对；刺激结束保留原生 `++tick > TPS`，不能错写为TPS刻。111条原生开发共17,304刻，每次确认后额外显式等待原生开发刻数加20刻；真实节点选择/确认还保留现有UI默认等待。

每条完整流程为：关闭旧界面 → 合法前置准备并明确取消目标已学状态 → 准备有限真实设备电量 → 真实节点点击与开始学习确认 → 正常原生开发等待 → `live:learned:<category>/<id>` 验证目标已学、相同实际设备类型及精确IF差额 → 原生截图。

NORMAL/ADVANCED fixture 使用真实完整8格机器、真实电池及 `machine.use` 的生产会话界面。其脚本不会调用 `tree:develop` 覆盖机器会话为便携机界面；仅PORTABLE使用已有 `tree:develop` 入口。确认全部通过原屏幕点击进入生产请求，不调用 `DevelopmentController.startSkill` 或给被测目标直接授予学习完成。

前置技能、必要熟练度和玩家等级是明确的合法准备条件；被测目标在前置准备之后必须为未学，再由真实开发过程学会。此计划不宣称从零自然练满整条依赖链，也不替代15条被动实际增益或96主动玩法的独立验收。每条学习前的有限充能分别声明，不能冒称同一台机器不补电连续学习111条。

先用 `gameplay-learning111-advanced-smoke-plan.json` 预检原超电磁炮LV4与新增导轨雷阵LV5的真实ADVANCED链路。代表预检只重复这两条，不计入111个不同记录的分母。主计划和 `gameplay-learning111-manifest.json` 包含逐条等级、类型、刺激数、原生刻数、精确费用与脚本SHA；任何项目没有真正的对应类别/技能学习PASS都不能补算。

旧无类别参数的 `prepare-learning:<id>` 仍是PORTABLE，`prepare-machine-learning:<id>` 仍是NORMAL；便携高阶的未学详情预览不新增资格要求。新增 `prepare-advanced-machine-learning:<id>` 明确选择真实ADVANCED，三种准备与 `learned:` 均可使用不歧义的 `category/id` 参数。
