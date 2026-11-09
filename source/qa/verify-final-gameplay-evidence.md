# 最终证据核验器

仅读取明确指定的计划、结果、日志、脚本文本、截图与编译快照，不启动Gradle或游戏，也不修改build。最终实际输出路径由root执行后填写。

```sh
PYTHONDONTWRITEBYTECODE=1 python3 qa/verify-final-gameplay-evidence.py \
  --main-plan /absolute/final-main-plan.json \
  --main-results /absolute/final-main-results.json \
  --modes-plan /absolute/final-modes-merged-plan.json \
  --modes-results /absolute/final-modes-merged-results.json \
  --passives-plan /absolute/final-passives-plan.json \
  --passives-results /absolute/final-passives-results.json \
  --evidence-dir /absolute/native-client-evidence \
  --dual-grounding /absolute/dual-grounding-output \
  --out /absolute/final-evidence-audit.json
```

默认从本QA目录读取原始脚本和完整51模式计划；若已冻结到别处，明确提供`--script-dir`和`--modes-source-plan`。不会自动搜旧日志填补本轮缺项。

- 主计划必须是33案、95个不同主动技能，余下接地回流由真实双客户端电弧的late/precise两轮补齐，合起来精确匹配catalog的96项。
- 每案要求结果exit0、实际客户端`.exit`为0、QA_DONE、无QA失败、归档脚本与所给冻结源完全一致。主技能逐ID检查实际机制PASS；风压额外要求服务器位移、nativeReady与镜头复位；磁步要求真实机器学习、两种地面实际位移及各自停止。
- 模式7案须精确保留原51个场景，每个begin/end之间须有实际机制或生命周期见证，完整原始输入/断言必须仍在归档脚本中。仅多放几个标记或渲染PASS不能通过。
- 非主动7案按category/id核对全部15条真实机器学习PASS和实际增益PASS。训练同名记录不能跨系替代。
- 所有案与双端输出都必须包含生产类、QA类、资源的逐文件SHA和mod JAR SHA，精确相同才是同构建。旧快照缺QA或mods时明确报缺证，不能补猜。
- 主机制和双端接地须有真实stage/hand matrix/FOV观测及action/release两张完整原生帧PNG；核对已归档图片SHA和PNG头。计数、收到phase、保存截图都不等于像素或审美已通过。

默认报告像素审阅为pending。实际逐图查看后可传`--pixel-review review.json --require-pixel-review`，每条必须有skill、phase(action/release)、绝对file、已归档sha256、reviewer、reviewerKind(agent或user)、result(pass)、notes；须绑定全部96技能两阶段实际图片。root代理实际开图后的agent视觉验收与用户最终审美接受明确区分，后者不由本工具推定。工具只核对这份明确审阅声明，不自动生成视觉评价。重点仍应看charging/railgun手势及原电弧挂点、高阶层级、功能辨识与四系风格。

退出0仅表示指定证据合同通过；JSON明确`notReleaseOrUserAcceptance=true`。默认像素pending时状态为`MECHANICAL_AND_IMAGE_EVIDENCE_VERIFIED_PIXEL_REVIEW_PENDING`。缺证输出全部errors并退出1，不将半截日志、旧构建混合集或渲染计数升级为全部验收。

额外19单端和5组补充双端可明确加入同一次审计，原33/7/7/51合同不变：

```sh
  --extras-plan /absolute/gameplay-final16-extras-plan.json \
  --extras-results /absolute/final-extras19-results.json \
  --supplemental-dual-manifest /absolute/final-dual-runs.json \
  --supplemental-dual-base-directory /absolute/final-evidence
```

`--extras-plan/results`须成对提供。extras固定TPVM7、VM环境3、物品/窄角分流2、风翼热循环1、视觉专项4、补充40秒/四系护盾2，共19个精确原脚本；计数相同但替换场景也失败。Tree提供的`requiredLogMarkers`全部要求，合并段按filename或stem分别核完整原始输入/断言及本段专属见证，不能用邻段PASS填补。两针场景要求两次真实定向回收且remaining按1→0排列。只见render/DONE不能替代作用；视觉专项仍要求自然动作年龄、释放时间、真实手矩阵/FOV与归档图。所有19案脚本SHA、实际exit/DONE、无未知路由、生产/QA/资源/mods快照均加入主审计。

33/7/7/19子计划各自只接受完全匹配的结果列表。如果启动器产出66案合并结果，必须按明确计划名称拆为四份结果JSON再传入，不能将未指定旧日志自动混入或静默忽略额外结果。

补充双端manifest为五行明确目录，目录可以是绝对路径，或相对`--supplemental-dual-base-directory`（别名`--base-directory`，默认manifest所在目录）：

```json
[
  {"kind":"pincer","directory":"final-pincer"},
  {"kind":"tactical","directory":"final-tactical"},
  {"kind":"domain","directory":"final-domain"},
  {"kind":"poses_forward","directory":"final-poses-forward"},
  {"kind":"poses_reverse","directory":"final-poses-reverse"}
]
```

每组检查实际两客户端exit0、host/observer完成、无失败/未知路由、断线清理、两份冻结原脚本、原始PNG和同一生产/QA/资源/mods快照。JSON false/null/浮点0不算实际整数退出码。Pincer/Tactical还要求正常接收方review nonce、正input/token且属于baseline实际peer的paid/moved生产receipt、真实位移超过2米且到达receipt destination、付款closed clock推进；CP自然回满不误判为未付款。Domain要求正常接收方on/off脚本、付款clock及真实OFF_BEFORE/RELEASED/EXPIRED/OFF_PASS，不能用单个笼统PASS替代停止缓降。进一步核定同一recipient/pulseIdentity/commandTick、原脉冲duration在1–2tick、lease在下一边界内释放、脉冲自然在最多3个观察tick内消退，且前后都实际向下运动；不错误要求命令当tick强删原生buff。该合同与真实v3的duration2→released elapsed1（effectMayRemain=true）→expired elapsed3匹配。双向pose要求固定remote states及各自正数原生model render帧；只有文本状态列表而未真正渲染不能通过。双端接地仍由原必选`--dual-grounding`独立核验，不被这五组替代。

选项未提供时，输出`optionalSuites`明确未审该套额外范围；不会因此声称已完成全部新增模式。提供后任何一组快照不一致或缺证都会使整次审计失败。图像来源核验仍与明确图像审阅、用户最终审美接受分开。

解析器反例回归：`PYTHONDONTWRITEBYTECODE=1 python3 qa/test-verify-final-gameplay-evidence.py`，39项通过，包括缺专属commit、错分段/漏原断言、一次回收冒充两次、错误remaining顺序、paid/moved标志但无实际位移、零input/错误peer、Domain缺真实停止/延长原生脉冲/错误身份或落地后假结束、remote帧0、observer非零或布尔退出/未知步骤、QA类/mod变化。临时合成资料仅验证解析器拒绝缺证/错证，不是游戏作用证据。
