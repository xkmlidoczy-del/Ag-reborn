# 电击使与原子崩坏实作用验收计划

更新：2026-10-22。本表是待执行/重跑的验收入口，不是通过清单。`QA_GAMEPLAY_PASS` 与 `QA_GAMEPLAY_MODE_PASS` 必须来自真实客户端日志才计通过；既有动画 signature、纯测试、原生 GameTest 分别保留，不能替代本表。

- 仅准备正常学习、有限资源、材料、世界和实际原生目标；技能输入一律 GLFW 按键或原生鼠标/GUI 事件。
- 伤害逐个比对服务端真实 H+S 减少，并要求真实 LivingDamageEvent 的玩家来源；采集要求方块消失与原生掉落，防御要求专用接管见证而非仅基础盾。
- 钱包读 CP/availableCp 最低值与真实 trySpend 成功后闭合的 next_skill 时钟。不会因同刻自然回满把真实低额支付误判为免费；只观察 reservation 不足以证明采掘/伤害成功。
- 松键、缺口、次数、材料和侦测模式单独断言；不调用技能 start/attack/effect 或向客户端注入特效。

## 主机制入口（49 个主动技能）

| 技能 ID | 独立 QA 见证类 |
|---|---|
| `arc_gen` | Combat |
| `beam_etching` | Utility |
| `beam_step` | Motion |
| `body_intensify` | Utility |
| `charge_decoy` | Defense |
| `charging` | Utility |
| `conductive_circuit` | Construction |
| `directional_dissipation_curtain` | Defense |
| `disintegration_sweep` | Combat, Utility |
| `dual_point` | Combat |
| `electron_bomb` | Combat |
| `electron_missile` | Combat |
| `folded_mirror_array` | Construction |
| `forked_arc` | Combat |
| `grounding_return` | Defense |
| `iron_sand_lance` | Combat |
| `jet_engine` | Motion |
| `light_shield` | Defense |
| `mag_manip` | Construction |
| `mag_movement` | Motion |
| `magnetic_bracer` | Defense |
| `magnetic_domain` | Defense |
| `magnetic_ground_step` | Motion |
| `magnetic_item_pull` | Construction |
| `meltdowner` | Combat |
| `mine_detect` | Utility |
| `mine_ray_basic` | Utility |
| `mine_ray_expert` | Utility |
| `mine_ray_luck` | Utility |
| `multicore_canopy` | Combat |
| `particle_laminate` | Construction |
| `point_meltdown` | Utility |
| `polarity_tether` | Motion |
| `potential_buffer` | Defense |
| `pulse_shield` | Construction |
| `rail_relay` | Construction |
| `rail_thunder_array` | Combat |
| `railgun` | Combat |
| `ray_barrage` | Combat |
| `remote_scatter_relay` | Construction |
| `reverse_jet_brake` | Motion |
| `scatter_bomb` | Combat |
| `scatter_locator_plate` | Construction |
| `scatter_plates` | Defense |
| `thermal_shell` | Defense |
| `thunder_bolt` | Combat |
| `thunder_clap` | Combat |
| `thunder_net` | Combat |
| `zero_gap_armor` | Defense |

脚本统一位于本目录，`gameplay-em-damage.script`、`gameplay-md-damage.script` 与 `gameplay-em-md-*.script` 按类分批。磁流步另有普通 LV3 学习/默认开启的专项脚本，由学习树代理维护；Motion 类也保留真实 poweredDistance 见证。

## 明确的替代模式

| 模式脚本后缀 | 真实作用见证 |
|---|---|
| mode-coin-qte | 正常右击真实抛币达到晚判定；玩家来源伤害、原生 CP 支付、恰好一枚硬币与 escrow 闭合 |
| mode-semiconductor-fan | 正常右击投出原生半导体；实际 isHit、三目标归属伤害、恰好一个材料与原生 CP 支付 |
| mode-dual-secondary | 潜行点按确实标记首个目标；转向普通点按后两个实际目标均受玩家伤害 |
| mode-net-hold | 多次付费伤害脉冲、每目标真实移动速度 modifier；松开移除自己的束缚 |
| mode-lance-orbit | 真实三枚付费环绕棱枪偏转敌方原生箭；箭 lease/速度变化、恰好三砂 |
| mode-arrow-separation | 正常潜行按住预览分离走廊、松键确认；真实敌箭 lease 与转向、有限预算支付 |
| mode-rail-hold | 三轴后真实 HOLD 状态、至少两次付费伤害脉冲，松开结束 |
| mode-canopy-held | 真实 paid/held/successes receipt 与目标伤害，松开不再 holding |
| mode-pulse-detect | 正常服务器 DETECT 返回准备目标的真实坐标；两个钱包不扣、不伤害、不打断 AI、不写干扰 |

## 环境与限制分支候选（本轮执行）

`gameplay-em-md-env-*.script` 已新增14项，入口 `gameem:env:*`：充能原生无线节点、训练后电弧实际引火/原生苦力怕带电、真实铁傀儡锚点、分叉潜行只打主目标、光盾近身真实灼伤、波形炮熔穿石墙并伤后方目标、过长蓄力过载/支付后取消、喷射实际冲撞、16块软土完整选取/原生掉落、刻线硬材拒绝、无工具采石拒绝、基岩拒绝、夹层真实付费低输出伤害。

`gameplay-em-md-planning-*.script` 新增3项：折镜长按收回，定位片真实PREVIEW路径后收回，中继真实MEASURING路径后潜行清除。必须同时看到服务端真实plane消失、客户端实际接受的路径、controlhold归零和旁边真实目标始终不受伤。

随机电弧分支只在 setup 准备合法满熟练度（0熟练度点火概率为0），使用有限多次正常R；不改RNG。随机没有发生仍判未取得证据。粒机波形炮的实际风险是过载/持续CP及超过100刻取消，源码没有高蓄力直接自伤分支，不能写成“已验证自伤”。

## 早期候选边界（状态以下方批13—16实证为准）

此段保留早期候选准入要求；当前分支运行状态以下方后续实证为准，不能因主机制成功直接声称全部攻略已验证。恒定磁域同队明确同意共享仍需双端协调真实 `/aca_domain_team on`；专用盾缺口/破片/unsupported 与基础盾回退已新增独立原生8项回归（含续费成功/失败的真实跨周期箭），尚待最终原生执行。

实际失败要先诊断真实位置、保护、输入、支付时序；任何生产修复另附源文件、原生回归和相同客户端反例重跑，不通过写资源/直接伤害/假 marker 制造结果。


## 已取得的防御反例与修复闭环

证据归档根：`../../qa/evidence/2026-10-22-full-gameplay/`。以下只表示这几项真实客户端结算，不能代替全部49技能和模式验收。

- 原 `defense-scatter-first.log` / `defense-curtain-first.log` 的无重力箭不符合专用层普通箭合同，属于夹具失败；不作为生产通过或缺陷证明。
- `defense-scatter-gravity-before.log`：真实重力箭且 native pending capture 为真，基础热壳仍先取消/删除箭并花 `2×10^18` 盾，专用 mastery 缺失。`defense-scatter-after.log`：同类真实箭进入 native hurt，实际只扣基础盾2，HP不损失，专用 mastery 闭合。
- `defense-zero-gravity-before.log` 后更正为瞄准当前实际旋转护片的真重力路径；`defense-zero-after.log` 证明 native pending 为真、实际只扣基础盾6、专用 mastery 闭合。早期零隙未付费另有夹具只learn未走学习后分区初始化的原因，不是“技能免费”。
- `curtain-flight-trace.log`：tick290真实穿过第1弦面并获得 capture，tick293续费边界先 prune 删除，再把 paidUntil293改313，后续 body hit 被基础4%消融。`curtain-renewal-fixed.log`：先续费/关闭再 prune，真实历史 capture 跨同一周期边界保留；最终实际只扣盾1.6（普通箭2伤害的0.8耗盾效率），HP不损失且专用 mastery 闭合。
- 生产交接只信本次原生 tick / hit / defender / ledger identity / 有效剩余护片的 pendingContact；没有实际专用接管时保留基础4%消融。新增8项原生回归仍需最终串行执行，不据客户端单例假报原生全通过。

## 批11实际分支进度（2026-10-23）

`../../qa/evidence/2026-10-22-full-gameplay/batch11-results.json` 及同名原始日志确认：14 个 `env-em-md-env-*` 全部 exit0。见证包括真实原生节点能量、火方块、带电苦力怕、16块软土与掉落、潜行抑制次级电弧、喷射撞击、低输出夹层、近身光盾、实体锚点、过载取消与熔穿后命中，以及拒绝材料或工具后的零支付和零幻影掉落。

9 个模式均已有真实通过：批11的 `canopy-held`、`coin-qte`、`dual-secondary`、`lance-orbit`、`net-hold`、`pulse-detect`、`rail-hold`、`semiconductor-fan`，以及批12的 `arrow-separation-contact-time-retry.log`。箭分离原结尾要求20刻租约在22刻以上之后仍有效；改为在租约真实有效时同时读取同一实际箭的lease、affected>0和速度变化>.01后，同脚本实际见证turn=0.1、真实资源支付及正常箭转向，随后才核验结束结果。

三种镜片规划分支已全部 exit0，原始日志为 `planning-em-md-planning-{folded_mirror_array,remote_scatter_relay,scatter_locator_plate}.log`，真实客户端接收平面/路径、服务端正常收回、控制储备释放及未伤旁观目标的断言均通过。导电回路仍需夹具修正后的真实穿越闭环，导轨与扇扫见下方批12证据。接地回流的原版雷击测试实际被电磁基础免雷保护取消，不能计专用作用；保留原免雷，另由真实双客户端白名单电弧对抗验证。上述分支通过不能替代最终同构建全96主动复跑。

批12 `rail-native-support-retry.log` 已证明原生地面裁剪后的真实位移18.0188米与有限CP支付；`sweep-fan-edge-fixed.log` 保留原三靶并均产生4.92E18的真实归属损失。导电回路仍待自然落地后恢复正常goal/native navigation的夹具重跑；电势缓冲仍待NoAI禁物理夹具纠正后的真实位移；磁域仍待关闭状态自然恢复额外储备、再由真实箭支付1%最大S的前置纠正重跑。不将这些夹具失败写成生产通过。


## 批13—16最终作用补证（2026-10-23）

当前跨批主要机制汇总已达96/96，见 `../../qa/evidence/2026-10-22-full-gameplay/gameplay-progress.json`。这不是同一最终构建的完整96场景复跑证明；最终原生/check、新包与同构建复跑仍由主代理串行完成。

- 批13 `circuit-grounded-native-path`：原生重力落地后恢复真实AI导航；实际走过导线，`electromaster.conductive_circuit`专用来源尝试和原生Post命中，目标钱包下降；保留20tick生产窗口。
- 批13 `potential-native-motion`：仅停goal控制输入而不NoAI，真实位移>.05、专用pulse与资源结算通过；`thermal-specialist-native-damage`进一步核真实`ExactSkillDamageSource`/thermalShellPulse，排除基础盾反击伪证。
- 批15 `gameplay-em-md-defense-magnetic_domain`：真箭在领域前正确朝远处射手的来袭路径上获得专用lease/turn；总钱包7.4E19→7.35E19，恰付maxS1%，基础S优先、储备不被误作唯一支取来源。正常Space后实际下降，exact owned slowfall duration1；CP实际最小99999，QA_DONE。
- `diagnostic-domain-team-consent-v3`：真实双端本人命令on/off，实际下降且current paid source/owned pulse同对象时发off；服务器tick562捕获duration2，下一tick同意与lease已撤；elapsed3仍下降、原脉冲已原生消退，双方exit0与OFF_PASS/DONE。v2的共享PASS保留，但off的客户端快照断言失败不冒充撤回成功。生产合同是立即停止刷新、最多2tick原生寿命，不是删除外来药水。
- `diagnostic-grounding-real-dual-v2`：真实玩家`electromaster.arc_gen`两轮对照，nativeAge15耗盾5E17且HP不变；精准nativeAge4真实先支取5.0192E17后盾回满，防守方预算不耗、攻击方原CP实扣，双方exit0。旧原版雷击被基础免雷取消，不能计接地作用。
- 经典矢量环境独立五源：批14 `vm-environment-legacy-three`（破块/传播/软土）、`vm-environment-storm-fragile`（零熟练破517玻璃）通过；批15 `gameplay-vm-environment-plasma-terrain`真实归属爆炸125干草、125原生产出、基岩保留、CP10500→9438通过。早期泥土夹具混入自然背景的16栈合并归属误差不计生产duplication。

原生新增18矢量专用箭与8崩坏专用箭位于默认第1组；磁流步15/接地10也在第1组，导轨新case在第4组。仅静态确认发现路径，最终原生执行日志尚待。Sable10近原点为独立依赖专项；legacy-domain34只是已在组1的原测试委托，不能重复累计。所有冻结证明/哈希/豁免未改。
