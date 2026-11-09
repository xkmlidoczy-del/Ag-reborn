# Bounded original-M41 Charging core differential

Run from the source root with existing JDK 21 and dependency/build outputs:

```sh
python3 scripts/verify-charging-m41-core.py --report build/reports/current-geometry-proof/charging-core
```

The report directory must not already exist. Each invocation atomically reserves
a fresh path before inspecting inputs. Choose a different `--report` path for
every run; rejected reruns leave prior evidence unchanged, and a failed new run
cannot inherit an older successful `result.json`.

Optional explicit paths: `--original-source ZIP`, `--jdk JDK`,
`--classpath-file FILE`, `--gson-jar JAR`, `--nbt-jar JAR`, and `--project ROOT`.
The default dependency selection file is `build/vfx2/native-classpath.txt`;
newline-separated and platform classpath-separated paths are supported.
No Gradle, native server, game, UI, or graphical process is launched.
All reference, current, mutant, and guard JVM executions use `-Xmx256m`.
Missing inputs fail; there is no baseline-skip or expected/contract refresh mode.

The reference is the immutable M41 source ZIP with SHA-256
`8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b`.
Exactly ten original production sources are extracted without edits and compiled:
AbilityProgress, CurrentChargingSession, SkillConsumption, AbilityCalculation,
ClassicPassiveSkills, ClassicRules, SkillPresets, ClassicSkillConfiguration,
ClassicInterferenceSources, and SkillCatalog. Its original classic-skills.json is
also immutable. Official Gson and Minecraft NBT signatures supply compilation
APIs; no production stand-ins replace them.

The positive candidate loads the existing `build/classes/java/main` classes.
It does not recompile the candidate production sources. Separate closed loaders
execute one identical driver compiled against the original API. Every loaded
application class has a byte hash, real code source, and unique class resource.
The fixed manifests admit explicit class names, including current HighPowerSkills
and HighPowerCategories because the current catalog references these additions.
They admit the fixed Gson archive's explicit class inventory and NBT Tag and
CompoundTag signatures. Unknown application dependencies fail. Each loader's
actually loaded subset is recorded; only Tag loads in the current tested trace.

The 437 finite, classic, non-enrolled scenarios produce 6,427 ordered trace rows.
Every row compares raw IEEE-754 double bits (including widened classic floats),
CP/overload and training growth, raw/cached maxima, XP and level progression,
recovery delays, activation/interference/busy flags, cooldowns, classic skill
counts, and observable callback/transfer ordering. Coverage includes:

- Item CP failure before transfer versus block transfer/XP before CP failure
- Supported/full/unsupported/absent targets, mastery boundaries and captured mastery
- Newly supplied live targets, captured item/block mode, release and recovery
- Start overload/floors, finite fractional ledger boundaries and training caps
- Creative bypass and training, live creative state, live config and event cost mutation
- Passive learning/XP/recovery and classic progression denominators
- Callback release and bounded reentry at consume, target, and XP stages, retaining
  original quirks such as an in-flight item tick continuing after callback release

The original trace is additionally pinned to
`baa6b9ac524a80027a8c559ecb20b6f5d143ba9e9f38498016895c537811c3e4`.
CP, XP, and ordering mutants are separately compiled in temporary report paths,
executed, and required to differ from the original trace. Their first differing
rows are recorded. Source hash rejection alone cannot satisfy these controls.
Charging core has no RNG behavior to mutate. Separate executable guard controls
reject an unknown dependency, alternate production origin with identical bytes,
duplicate catalog resource, and wrong class digest.

`result.json` contains the result, counts, hashes, actual loaded classes, controls,
and limits. Each `run-*` directory retains original source/classes, manifests,
full traces, loaded-class origins, and execution logs. Inputs are rehashed after
execution; frozen original extraction is checked again too.

The bounded report-lifecycle selfcheck also runs independently without loading
production classes:

```sh
python3 -c "import runpy; print(runpy.run_path('scripts/verify-charging-m41-core.py')['check_report_lifecycle']())"
```

This is a bounded core behavior proof. Current source/class/dependency pins are
repeatability evidence, not original-artifact equivalence. It does not prove
native CurrentCharging ingress, live Minecraft hand/world resolution, capability
adapters, enhanced transactions, enrollment, protocol or serialization behavior,
full artifact equality, or graphics. Native entry tests are separate. Existing
M41 geometry fixtures, archive guards, and tests remain unchanged and retain
their independent status.
