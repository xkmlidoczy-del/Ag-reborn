# Current Meltdowner CPU geometry companion

Run against existing build outputs, without invoking Gradle or compiling production:

```sh
python3 scripts/verify-current-meltdowner-geometry.py \
  --report build/reports/current-meltdowner-proof-new
```

The report directory must not exist. Optional arguments select the project,
original M41 source ZIP, pinned JDK, and dependency cache. Every renderer JVM is
limited to 512 MiB; the bytecode mutator is limited to 256 MiB.

## Relationship to the historical proof

The original `classicMeltdownerGeometryTest`, its Gradle runner, fixture index,
harness, hosts, guards, and expected digest remain unchanged. The independently
pinned M41 ZIP (`8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b`)
anchors the read-only historical fixture/API audit. The historical whole-archive
admission still fails. A companion success is reported as
`PASS_CURRENT_CONTRACT_ONLY`; it is neither a historical-task pass nor a full
`check` pass.

The original eleven CPU API class fingerprints and JOML archive fingerprint are
retained. Other dependency classes, including their selected multi-release JAR
members, have explicitly labelled current-version fingerprints. These additional
fingerprints establish repeatability, not historical equivalence of the entire
merged JAR or its transitive implementation.

## Subject and adapter

The positive subject is the existing `build/classes/java/main` output. Its
explicit production closure contains the original fifty classes plus nineteen
current config, Light Shield, preview, and MD near-eye types. The closure includes
`LightShieldVfx2$1`, all four preview record/container types, and the MD Owner,
Ready, Cached, Registration, and DrawState types. Every class is admitted by name,
origin, member, bytes, CodeSource, and unique ClassResource. Production constant
pool references are recorded independently of whether the referenced method runs.
An attempted load outside the declared closure fails; `AcademyClient` is not
replaced with a mock.

The added historical adapter retains the original scene loops, geometry
recording, tolerances, attribute comparisons, RNG sample, and serialization. Its
reflection helpers select every constructor and private method by exact parameter
types. The current Beam Ray's eight-argument constructor and four-argument
`renderRay` are deliberately classic-only. They cannot establish near-eye ON
coverage.

A side list records requested RenderType objects for the independent state checks.
It does not rewrite vertices, material names, flush order, or the frozen stream.
The adapter's scope banner names the expanded host boundary rather than repeating
the old seven-host claim.

## Required executions

Four separate JVM lanes each cover the complete 2,640-scene matrix:

- Historical signature lane, with optional materials OFF
- Current five-argument renderer with MD OFF
- Current five-argument renderer with forced pre-selection fallback
- Current five-argument renderer with MD ON

The original matrix covers 3,280,176 near vertices, 18,792 near batches and
39,362,112 relocated-coordinate comparisons, plus its three camera-motion probes.
The first three lanes must retain exactly:

`335d8736629a8bbb585927086393a787fb9019c87befd1b654c57df8b123482a`

The ON lane compares every zero-camera position, UV, RGBA, ordering, and sampled
RNG value against the historically anchored current OFF path, then applies the
original relocated-coordinate comparisons. Exactly 720 matrix draws select MD
near-eye materials: 144 eligible local direct near scenes and their four relocated
copies. Every selected draw remains three four-vertex boards and two 432-vertex
cylinders. The requested-material digest for ON is recorded separately and is not
normalized into the historical digest. Light Shield remains OFF throughout these
matrices; its enhancement is covered by its separate existing differential.

The actual public renderer event entry is additionally exercised using explicitly
seeded production-owned Ray/Context objects and maps. Twenty-two cases cover
direct and reflected rays, mixed direct/reflected rendering, remote/third-person
views, detached and mismatched cameras, dead/removed/spectator/replaced casters,
UUID changes, player/world/connection/epoch changes, wrong render stages, and the
actual basic/expert/luck mining context loops. Caller pose and shader color are
checked after each ordinary event. The accepted contexts are fixtures: this is not
a packet-acceptance, input-delegate, or AcademyClient proof.

Seven additional pre-selection failure samples require exact original geometry
and requested material/flush names: unloaded config, either shader constructor,
missing/inactive uniforms, pending reload, and global forced fallback.

## Independent material and fallback checks

Native RenderType and CompositeState construction execute unchanged. The tests
inspect actual state objects and require the source textures, nearest filtering,
QUADS/POSITION_TEX_COLOR, 4096-byte declaration, unsorted submission, translucent
blend declaration, LEQUAL, depth writes, correct core/glow culling, particle output,
and exact remaining classic state shards. The selected shader identities are
asserted separately. No textual replacement hides material differences.

The real production DrawState setup/clear code also runs in a distinct component
test with recording RenderType delegates. Seven cases check successful selection,
reload, forced fallback, either closed program, deletion-pending glow program,
and enhanced setup failure. The exact original delegate must become active on
failure and be cleared correctly. These calls establish component dispatch, not
native composite GL application. In a late failure, a requested enhancement
wrapper can remain different from the effective original draw; the proof does not
claim that its requested-material digest equals the historical digest.

## Explicit hosts and limits

Fifteen game/graphics host classes provide the existing seven view/entity/glint
boundaries plus ClientPacketListener, ClientLevel, Level, GameRenderer,
ShaderInstance, Uniform, GL20, and RenderSystem. Their field and method descriptors
match actual compileJava bytecode. In particular, Minecraft.player is LocalPlayer,
getConnection returns ClientPacketListener, and Entity.level returns Level.

The historical private contexts retain their original null world/connection
arguments. The host's non-null connection identity makes the newly evaluated
shield validity hook fail before a world lookup. In current event cases, explicit
world/entity maps provide identity, life, and camera state. Shader/GL hosts expose
controlled program/uniform readiness and failures. Native Camera, event accessors,
Vec3, PoseStack, VertexConsumer default methods, Axis, JOML, and material declaration
classes remain original API bytes. A BufferSource subclass records submissions;
it does not allocate or flush native GPU buffers.

Actual ClassicHudConfig, NeoForge ModConfigSpec, and NightConfig execute. An actual
LoadedConfig record wraps an in-memory configuration; no user config is saved.
The separate console-only logging fixture prevents filesystem rollover and its
ambient dependency changes. No host creates accepted packets, audio, or gameplay.
Preview types are admitted for the current signature graph; diagnostic preview is
explicitly disabled.

The existing MD 835-frame differential, shader GL test, Light Shield differential,
and separate native-client observations provide different evidence. This
companion does not rerun or relabel them, and does not establish GPU pixels,
readability, native client operation, audio correctness, full gameplay parity, or
performance.

## Executable negative controls

Temporary mutations of current compiled bytecode must be detected for coordinates,
UVs, extra RNG use, camera subtraction, the five-argument eligibility gate, and
material depth writes. The camera mutant preserves zero-camera geometry while
breaking far-camera comparisons. A temporary host descriptor mutation changes
Minecraft.getConnection's return type and must fail when the actual production
shield Context invokes its native ABI. No production source or positive class is
edited for these controls.

Admission controls reject duplicate subjects, stale pins, a missing Owner class,
unadmitted AcademyClient loading, and byte-identical original API bytes at a false
origin. Reports retain each failed control's actual execution log, expected
failure, and loaded-class ledger. A survivor cannot publish the success marker.
