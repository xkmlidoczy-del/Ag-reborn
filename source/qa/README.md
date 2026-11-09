# QA 运行

在工程根目录创建独立的 Python 3.12 或更新环境，不安装到 Apple 自带 Python：

```sh
python3.12 -m venv --copies .venv
.venv/bin/python -m pip install -r source/qa/requirements.txt
.venv/bin/python source/qa/run-regression.py
```

本机已用 Codex 附带的 Python 3.12.14 建立 `.venv`。Pillow 12.1.0 提供现有材料测试使用的 `get_flattened_data`；NumPy 固定为 2.3.4。运行器选择 Java 21，忽略不兼容的默认 `JAVA_HOME`，可用 `ACA_QA_JAVA_HOME` 显式指定另一套 Java 21。Gradle 缓存使用 `~/.gradle-aca`。

验收规则：四组原生 GameTest 都必须非零数量、正常结束且零失败；完整 `check --continue jar` 必须正常收尾并执行历史失败名单中的全部任务；原始退出码及失败任务完整保留，仅 `qa/maintainer-waivers.json` 已有14项冻结证明可单列豁免，新增或未豁免失败仍失败，不得修改金标或新增豁免求通过。`qa/baseline-failed-tasks-e681c42.txt` 只用于统计修复，不再构成豁免。启动失败、执行中断、漏跑任务也会失败。完整日志与 `summary.json` 在 `qa/output/`。

`--check-only` 仅供已完成四组原生测试后检查纯测试或客户端改动使用，不可替代修改服务端后的重新验证。

## 客户端快照与技能树

```sh
cd source
bash qa/run-showcase.sh repair-classic-tree '' "$(cat qa/repair-classic-tree.script)"
```

脚本使用独立临时演示存档；macOS 运行期间用 `caffeinate` 临时唤醒并保持显示器工作，进程结束后解除。GUI 无窗口显示，但使用真实 Minecraft 客户端和渲染器。`classiccheck` 检查冻结经典渲染器的只读访问与副本隔离；`tree` 打开所属能力系的统一技能树，新增与原技能同类显示，原高级等级及前置保留。这两项是适配器/界面夹具，不能当作真实技能输入或双客户端联机证据。

本次保留全部旧冻结资源与哈希。新增 `developer-layout-v2/meltdowner-routes.json` 是本轮页面修订的独立坐标夹具；原始节点身份、学习条件及原始布局快照继续验证。当前页面额外检查全部节点的悬停圆、边界、5.5 像素连线、端点缩进与 101 个展开阶段的遮挡。未降低旧半径、间距或线宽阈值。

## 科技风动作实机验证

`live-tech-first-person.script` 通过真实 R/潜行/激活键检查各系高阶技能的第一人称姿态、服务端持续状态、矢量牢笼的两次短按、折镜阵折射伤害、左手与背包中止。切换场景前先通过正常 V 键退出上一个持续技能，再设置学习状态与预算；不会用动作 force 或特效注入代替技能输入。

```sh
cd source
bash qa/run-showcase.sh tech-first-person '' "$(cat qa/live-tech-first-person.script)"
```

构体需要 `run-showcase-sable/mods/` 中的精确 Sable 2.0.6，单客户端脚本为 `live-construct-tech.script`，使用 `-PshowDirectory=run-showcase-sable`。两个客户端的实测则由下述运行器串行准备、同时启动两个独立 JVM；使用真实本地 LAN 连接、独立游戏目录和不同玩家名。观察端必须实际渲染出指定姿态、构体四种权威阶段及左手状态，并在断线后检查通用动作、构体动作与构体特效三个存储全部为空。

```sh
# source/ 内，使用交接文档规定的 Java 21 与 GRADLE_USER_HOME
java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m \
  -PshowcaseQA=true -PshowHidden=true writeShowcaseLaunchMetadata
../.venv/bin/python qa/run-dual-poses.py
```

元数据读取实际 ModDevGradle 的 classpathProvider、environmentProperty 和 modFolders 参数；两个游戏运行时不再启动竞争的 Gradle 构建。运行器设有超时并只结束其自己创建的进程。输出在工程 `qa/output/dual-tech-<随机标识>/`，任何缺失的远端状态、非零退出或缺少完成标志均失败。

风压托举保留既有的单人集成服务器准入；它在单人脚本检查。联机 FLIGHT 姿态使用暴风之翼。装饰动作协议为 3，联机双方须使用同一新构建。动作设计与适用范围见 `docs/CASTING_ACTIONS.md`。
### Sable 在原点附近的完整原生测试

专用 GameTestServer 会随机选择千万格范围内的场地，`--spawnPos` 只改变出生点。Sable 单精度物理在远坐标可能无法收敛；失败日志须保留，不能靠重跑随机位置或放宽误差阈值获得通过。

可在真实集成服务器的固定近原点场地运行十项具名 GameTest（核对完整名称集合，所有原断言及投掷精确伤害/恰好一次结算断言均执行，不触发远坐标说明性分支）。十项包括预览/搬运/落地、远锁冲刺、阻挡取消、潜行取消、占位拒绝、保护地形拒绝、抛掷恰好一次命中、目标环绕、命中后构件束缚及双玩家独立持有。先按上文准备 Sable 游戏目录，在 `source/` 使用同样的 Java 21 环境：

```bash
java -jar gradle/wrapper/gradle-wrapper.jar --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx768m \
  -PshowcaseQA=true -PshowHidden=true -PshowDirectory=run-showcase-sable \
  '-PshowProps=neoforge.enableGameTest=true;neoforge.enabledGameTestNamespaces=academy_construct_sable' \
  '-PshowScript=cmd:difficulty normal|native:start|native:done|quit' runShowcaseClient
```

只在十条 `QA_SABLE_NATIVE_PASS` 完整名称与源码集合相同、每项实际坐标绝对值小于1000且成功、`QA_SABLE_NATIVE_ALL_PASS count=10 exactNames=true farCoordinateSkip=false`、`QA_DONE` 和成功退出都出现时通过。`SableNativeQa` 不打入发行 JAR，此结果不能代替远坐标验收，也不计入默认四组。必须启用精确 `academy_construct_sable` namespace；四项旧日志不能充作这次十项结果。
