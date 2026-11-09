# First-skill client visuals: source fidelity and limits

Baseline: AcademyCraft tag **1.0.7**, LambdaLib tag **1.2.3**. Modern hook compatibility was checked against the pinned **NeoForge 21.1.252** sources and its merged Minecraft 1.21.1 sources. No Minecraft process was launched; client appearance and sound have not been inspected in a running game.

## Implementation and integration

- `ClassicFirstSkillEffects` is a client-distribution-only event subscriber. Its public `receive(CompoundTag)` copies the tag, marshals to the client thread, and discards callbacks for a replaced world.
- `ClassicCubicCurve` is the original piecewise cubic Hermite algorithm with averaged adjacent secant slopes and linear endpoint extrapolation. It is not a linear interpolation or clamped spline replacement.
- `ClassicFirstSkillTimeline` contains pure hand curves, orb alpha/size, small-ray timing, marker bob and particle fades. It can be tested without initializing Minecraft classes.
- World draws use isolated buffers; only render types used by those buffers are flushed. Modern unlit alpha blending and depth testing replace legacy direct GL calls. Effect stores are bounded and cleared on world changes. Visual state never applies damage, spends CP, alters inventory, or transmits client aim.

Forward these packet kinds from the existing client receiver to `ClassicFirstSkillEffects.receive`:

| Kind | Required fields / meaning |
| --- | --- |
| `dir_shock_prepare` | `entity`: accepted caster hold |
| `dir_shock_abort` | `entity`: terminate preparation, invalid release or miss |
| `dir_shock` | `entity`: server-traced hit; begin punch and hit sound |
| `electron_bomb_charge` | `entity`, `life` (5 or 20), `x/y/z` actual server orb origin, `offset_x/y/z` fixed world-space offset including server +1.6 Y correction |
| `electron_bomb` | `entity`, `x/y/z` actual ray origin, `dx/dy/dz` direction, `length` actual endpoint distance |
| `threatening_teleport_start` | `entity`, `range` (captured mastery range); `length` accepted as fallback range |
| `threatening_teleport_abort` | `entity`: dispose local held marker |
| `threatening_teleport` | `entity`, `x/y/z` exact drop endpoint / hit target top, `success`, `target` (-1 or entity ID), `critical_tier` (-1 or 0..2) |

Teleporter `success=true` means successful consumption/execution, including a miss. This matches the original `attacked` flag, which was assigned before tracing. It is not the entity-hit flag. A direction-only legacy notification is insufficient to render a faithful trail and is deliberately not used as a guessed endpoint.

## Directed Shock

Sources:

- `AcademyCraft/src/main/scala/cn/academy/vanilla/vecmanip/skill/DirectedShock.scala`
- `AcademyCraft/src/main/scala/cn/academy/vanilla/vecmanip/client/effect/AnimPresets.scala`
- `LambdaLib/src/main/java/cn/lambdalib/vis/curve/CubicCurve.java`
- `LambdaLib/src/main/java/cn/lambdalib/vis/animation/presets/{CompTransformAnim,Vec3Anim}.java`
- `LambdaLib/src/main/java/cn/lambdalib/vis/model/CompTransform.java`
- `LambdaLib/src/main/scala/cn/lambdalib/util/mc/HandRenderInterrupter.scala`

The hold is accepted only on releases at ticks **7–49**; server logic owns that gate. Preparation advances with `t=min(2, elapsedMillis/150)`, rather than stopping at spline knot 1. The animation therefore reaches its held pose after **300 ms / six ticks**:

- Translation: **(-0.04, 0.8, -0.1)**
- Rotation X: **-40 degrees**

Punch evaluates the exact five curves with `t=elapsedMillis/300`. It begins at translation **(-0.04, 0.8, 0)** and rotation **(-40, 0)**, reaches its Z knot **-0.4** / Y-rotation knot **10** at 90 ms, and reaches identity at 300 ms. The source Hermite tangents produce real overshoot, such as Y **0.8204** and X-rotation **-46.6 degrees** at 90 ms. These are preserved.

The original context ends on `punchTicker > 6`, not `>= 6`. Its seventh-tick cleanup permits brief extrapolation after the 300 ms curve endpoint. The visual helper retains a **350 ms context window**, while keeping the curve duration at six ticks. Preparation is bounded by the original 200-tick tolerance.

`academy:vecmanip.directed_shock` is played at **0.5** volume only for the server-emitted hit kind. The sound follows the caster. Neither preparation nor abort/miss plays it. There is no invented beam or impact ribbon for this melee skill.

### Hand-attachment difference

NeoForge's `RenderHandEvent` is fired before vanilla's private `renderArmWithItem`; the event shares the freshly allocated first-person pose stack between hand draws. The port applies the `CompTransform` translation, X rotation and Y rotation once to that stack, allowing vanilla's arms/items and selected hands to render. It does not use reflection or replace the arm with a placeholder mesh.

This preserves the curve and transform order, but is **not a pixel-identical port of the 1.7 projection hook**. Classic `HandRenderer` rebuilt projection/model-view, applied the transform before view bob, and then called the old first-person renderer. Modern vanilla has already applied hurt/view bob and pitch bob when the event fires. Modern hand geometry, FOV and equip/item animations are retained, and the shared transform affects a selected off-hand as well. Third-person arm posing is not supplied; the original visual hand interrupter was first-person-only. Runtime screenshots are still required to evaluate these attachment differences.

## Electron Bomb

Sources:

- `AcademyCraft/src/main/scala/cn/academy/vanilla/meltdowner/skill/ElectronBomb.scala`
- `AcademyCraft/src/main/java/cn/academy/vanilla/meltdowner/entity/{EntityMdBall,EntityMdRaySmall}.java`
- `AcademyCraft/src/main/java/cn/academy/core/entity/EntityRayBase.java`
- `AcademyCraft/src/main/java/cn/academy/core/client/render/ray/RendererRay*.java`
- `AcademyCraft/src/main/java/cn/academy/vanilla/meltdowner/client/render/MdParticleFactory.java`
- `LambdaLib/src/main/java/cn/lambdalib/template/client/render/entity/RenderIcon.java`

The orb lives **20 ticks**, or **5 ticks** at mastery >=0.8. The server separately fires it at life minus two ticks. It follows the caster's translation using the authoritative offset, rather than rotating the sampled offset with later yaw. Orb textures are the original `effects/mdball/0..4` and glow. Each render frame retains the source's 2/8 random frame-switch chance, alpha-wiggle acceleration in [-4,4] with 3/8 change chance, and small sine/cosine surrounding motion.

The original glow/core sizes are **0.7 / 0.5** multiplied by source size growth/shrink. Their alpha multipliers are **0.3+0.7*wiggle / 0.8+0.2*wiggle**. The icon quad keeps `RenderIcon`'s asymmetric vertical coordinates **[-0.25,0.75]** scaled by size. The ordinary orb fades in over 300 ms, begins its burst at lifetime minus 400 ms, and fades out in the last 150 ms. The five-tick orb intentionally starts in the burst branch with alpha **0.84** and size **1.125**, rather than receiving an invented normalized fade-in.

The ray uses the transmitted orb origin and actual direction/length, not eye position plus an arbitrary aim beam. It preserves:

- **14-tick / 700 ms** lifetime, **200 ms** length blend-in, **400 ms** alpha blend-out, **500 ms** width shrink
- Original inner radius **0.03**, RGBA **(216,248,216,230)** and head fix **0.98**
- Original outer radius **0.045**, RGBA **(106,242,106,50)**
- **12-sided** cylindrical bodies and both **four-slice square-root-profile** tapered caps
- Original `mdray_small` blend-in/tile/blend-out glow, **0.3** glow width and **0.5** base alpha; the source's double alpha multiplication and inherited glow wiggle remain
- `academy:md.ray_small` at **0.5** volume at the ray origin
- One original `md_particle` sprite per ray tick, size **0.05–0.07**, alpha **0.3–0.6**, velocity components **[-0.015,0.015]**, lifetime **25–54** ticks then 20 fade ticks

### Orb-attachment and renderer differences

The port renders the orb at interpolated modern entity feet plus the server offset, whose Y already includes +1.6. Classic `EntityMdBall.R` instead subtracted the local player's raw position, used different client/server Y values, and added +1.6 only for remote casters. That 1.7 camera-coordinate hack is not copied into modern feet/camera coordinates. The modern renderer is camera-relative for every viewer; screenshots are needed for first-/third-person comparison.

The pure timing formulas follow source values, but no frame-rate-identical random sequence or bit-exact legacy float/trig-table output is claimed. Modern translucent batching, built-in shaders, target framebuffers, lighting independence and depth conventions replace legacy GL/GLSL/alpha-test state. Very short blend-in geometry keeps the original crossing glow cap boundaries instead of inventing a differently shaped beam.

## Threatening Teleport

Sources:

- `AcademyCraft/src/main/scala/cn/academy/vanilla/teleporter/skill/ThreateningTeleport.scala`
- `AcademyCraft/src/main/java/cn/academy/vanilla/teleporter/entity/EntityMarker.java`
- `AcademyCraft/src/main/java/cn/academy/vanilla/teleporter/client/{RenderMarker,TPParticleFactory,CriticalHitEffect,FormulaParticleFactory}.java`
- `AcademyCraft/src/main/java/cn/academy/vanilla/teleporter/util/TPSkillHelper.java`
- `LambdaLib/src/main/java/cn/lambdalib/particle/{Particle,Sprite}.java`
- `LambdaLib/src/main/java/cn/lambdalib/util/generic/RandUtils.java`

Only the local caster gets a held target marker. It retraces each client tick using the captured range, classic 0.3F-expanded entity intercepts and living-target preference even through walls. A target marker attaches to entity feet and uses target width/height; empty-space/block markers use **0.5 x 0.5**. It draws the original **eight corner brackets / 24 line segments**, each segment **0.2*width**, nominal width **3 pixels**, with depth testing. Colors are original ARGB **0xbabababa / 0xbab2232a**, and bob is **0.05*sin(absoluteMillis/400)**. Release/abort and invalid item/entity/world state dispose it.

Successful paid execution plays `academy:tp.tp` at **0.5** volume following the caster, including a miss. Trail particles follow the original vector from caster feet minus 0.5Y toward exact drop endpoint plus (0.5,0.5,0.5), initial travel step 1 and later steps [1,2]. They use original `tp_particle`, size **0.1–0.2**, alpha **0.6–0.8**, velocity X/Z **[-0.02,0.02]**, Y **[-0.02,0.05]**, five-tick fade-in and 20 life plus 20 fade ticks.

The packet's critical tier/target posts a client `TeleporterCriticalHitEvent`. Its subscriber ports `CriticalHitEffect` and `FormulaParticleFactory`: **5–7** particles around radius **0.5–0.7*target width** and random target height, random component velocities **[-0.03,0.03]**, ten original formula frames, RGB **220**, size **1–1.7**, initial alpha **0.6–1.5**, two-tick fade-in, **10–14** life ticks and 20 fade ticks. The tier does not change the original effect's particle count. The upper limits of integer ranges are exclusive in LambdaLib's `rangei`, which is why source calls `rangei(5,8)`, `rangei(25,55)` and `rangei(10,15)` do not include 8, 55 or 15.

### Marker/event differences

Three-pixel legacy GL lines are translated to NeoForge's line shader and line-width state, not replaced with a solid bounding box. The modern camera-relative pose and interpolation differ from the old entity renderer. The client critical event is a port API counterpart; it does not recreate the old package/class identity or itself supply a server-side NeoForge event. Server damage/critical decisions remain authoritative. Packet-acknowledged preparation/marker start may appear later than classic client-context activation under network latency. Sprite movement is visual-only, with the original zero gravity and unit drag; no legacy EntityX object is created.

## Verification

Passed:

1. `scripts/gradle-cloud.sh compileJava testClasses --no-daemon` against pinned NeoForge 21.1.252
2. `.tools/jdk-21.0.12.1+1/bin/java -ea -cp build/classes/java/main:build/classes/java/test cn.academy.port.FirstSkillVisualRegressionTest`
3. **2,503** pure assertions: empty/single/linear/extrapolated curves, malformed inputs, 2,406 independent Hermite samples including unequal knot spacing, preparation hold/endpoints, punch overshoot/golden values, both orb lifetimes/branch boundaries, ray fades, marker bob and particle fades
4. `scripts/gradle-cloud.sh check --no-daemon`: passed all currently wired checks, including the integrated `firstSkillVisualTest`; **3,057** reported standalone assertions across the eight regression mains

The integration owner wired `firstSkillVisualTest` into `check` and forwarded the packet kinds listed above from `AcademyClient.receive`.

Not run: client/server launch, live networking, shader/framebuffer rendering, screenshots, audiovisual comparison, and third-party-mod interaction. Minecraft EULA permission is still pending. Compilation and mathematical regressions do not establish those runtime results.
