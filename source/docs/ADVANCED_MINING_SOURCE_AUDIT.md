# Expert / Luck Mining Ray source-faithfulness audit

Baseline: cached AcademyCraft tag `1.0.7`, expected commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, and cached LambdaLib `1.2.3`. These are primary local source files, not recollections or a modern redesign. The tag's `build.properties` says `1.0.6`; use the release/tag provenance. This audit changes no production file or prior staging package.

## Server values and formulas

`MineRayExpert.scala:22,29–43`, `MineRayLuck.scala:22,29–43`, `MineRaysBase.scala:60–118`:

| Value | Expert | Luck |
|---|---:|---:|
| Skill level | 4 | 5 |
| Gameplay range | 20 | 20 |
| Harvest limit | 5 | 5 |
| Speed per continuing tick | `.5F + exp * (1F - .5F)` | same |
| CP per tick | `25F + exp * (15F - 25F)` | `50F + exp * (35F - 50F)` |
| Startup overload | `300F + exp * (200F - 300F)` | `350F + exp * (300F - 350F)` |
| End cooldown | `(60F + exp * (30F - 60F)).toInt` | same |
| XP per break | `.0003F` | same |
| Drop chance / fortune | `1F / 0` | `1F / 3` |

`exp` is a Float captured when the context is constructed, not refreshed as mining grants experience. Original LambdaLib `MathUtils.lerpf` is `a + lambda * (b - a)`, with Float operations and no additional clamp in that helper. Original normal skill experience is bounded to `[0,1]` by ability data. AbilityContext applies skill-config CP/overload/XP scale hooks (default one); the primitive table is the default baseline. Original `Skill.getOptionalFloat` has a separate config-path bug: if the requested float key exists, it reads `damage_scale` regardless of the requested key. The port need not invent new scaling from these defaults.

### Exact lifecycle/order and retained quirks

1. Start calls `ctx.consume(overload, 0)` (original argument order is overload then CP), ignores its boolean result, then stores the resulting total overload as `overloadKeep`. Creative mode consumes neither CP nor overload but still enters the context. ContextManager's external overload event can dispose a context; it is separate from the MRContext arithmetic.
2. Every server tick restores total overload to at least the startup floor, then calls `consume(0, cpPerTick)`. Failure requests termination but does **not** return: tracing/mining/particle dispatch still execute on that final tick. ContextManager sends the termination/cooldown after the tick body returns.
3. Trace uses `Raytrace.traceLiving(player, range, EntitySelectors.nothing)`: block-only, no entity damage or entity obstacle. LambdaLib's normal block selector requires a collision box and `canCollideCheck(metadata, false)`; liquids/noncollidable plants are not automatically included.
4. If hit coordinates differ, obtain the hit block, post `BlockDestroyEvent` for the **hit coordinates**, and, only if not canceled, ask that hit block's harvest level using `world.getBlockMetadata(x,y,z)` from the **previous tracked coordinates**. The initial previous position is `(-1,-1,-1)`. This is an actual metadata-position bug, not a desired target-state lookup.
5. If allowed and harvest level `<=5`, store new coordinates and capture block hardness; negative hardness becomes `Float.MaxValue`. Acquisition neither subtracts speed nor sends mining-hit particles. Zero-hardness blocks therefore still require a later continuing tick.
6. If denied/too high tier, reset only coordinates to `(-1,-1,-1)`, leaving old hardness. On the next tick the same denied hit is a changed target again, so it is reevaluated.
7. On the same coordinates, subtract captured speed exactly once. Do not re-post the classic acquisition event, recheck classic harvest level, or recapture changed block hardness. A block replaced at the same coordinates therefore inherits the old progress.
8. When remaining hardness `<=0`: fetch the **current** block there; play its break sound at center, volume `.5F`, pitch `1F`; call its `dropBlockAsItemWithChance` with **current target metadata**, chance `1F`, fortune `0/3`; set that block to air; grant XP; reset coordinates. The pure source does not condition XP on a modern adapter's break-success return.
9. Continuing ticks always send particles after the subtraction/break branch. After a break they therefore send `(-1,-1,-1)`, rather than the just-broken block coordinates. Null trace also resets coordinates, preserving prior hardness, and emits no hit-particle packet.
10. Key-up and key-abort both terminate. Termination applies cooldown with the captured Float exp and truncation; there is no charge threshold or per-block cooldown.

## Learning prerequisites and costs

`CatMeltdowner.java:45–46,81–82`, `Skill.java:94–110,349–355,386–400`, development conditions and `DevelopData.java:149–166`:

- Expert: Meltdowner level at least 4; Basic learned with mastery `>= .8F`; minimum developer **ADVANCED**; tree position `(172,70)`
- Luck: level at least 5; Expert learned with mastery `>= 1F`; minimum developer **ADVANCED**; tree position `(205,82)`
- Parent conditions use Float constants and require both learned status and threshold. A port storing mastery in Double should consciously match the original Float comparison at `.8F`'s boundary
- Stimulations are `(int)(3 + level * level * .5F)`: Expert 11, Luck 15
- ADVANCED has `TPS=15`, `CPS=600`, energy capacity 200000; energy is pulled as `600/15 = 40` per development tick
- UI estimated consumption uses `CPS * stims`: Expert 6600, Luck 9000
- Actual classic increment uses `++tickThisStim > TPS`, not `>=`: 16 ticks per stimulation, so Expert **176 ticks / 7040 energy**, Luck **240 ticks / 9600 energy** when uninterrupted. Do not silently flatten this off-by-one into the estimate
- Validation of learning conditions occurs at successful completion; unsupported developer type/parent mastery is not implied merely by tree visibility

## Client geometry, textures and media

Both client-only entities use life `233333` ticks, blend-in `200ms`, blend-out `400ms`, inherited width-shrink `300ms`, base width/glow wiggle radii `.1`, max wiggle rates `.4/s`, and no-frustum/view-optimized rendering. Termination calls `ray.setDead()` immediately and stops the loop; the 400ms lifetime fade is not a guaranteed key-release tail.

**Visual distance is 15, not gameplay range 20.** Every update computes the end with `new Motion3D(player,true).move(15)`, and the start at `(player.posX, player.posY + (local ? 0 : 1.6), player.posZ)`. `setFromTo` recomputes length and Float yaw/pitch from these endpoints; final geometric length is not necessarily exactly 15. Original LambdaLib Motion3D adds **+1.6 to X**, not Y, for a nonlocal client player; uses player's eye height and head yaw, and Minecraft1.7 Float sin/cos lookup. A normal client-only ray entity has eye height `(1.8F*.85F)`; beam-flight particles are generated using Motion3D on that ray, which adds that eye height again. These are observable legacy quirks.

| Renderer setting | Expert | Luck |
|---|---|---|
| Glow texture family | `effects/mdray_expert/{blend_in,tile,blend_out}.png` | `effects/mdray_luck/{blend_in,tile,blend_out}.png` |
| Inner cylinder radius / RGB | `.045 / (216,248,216)` | `.04 / (241,229,247)` |
| Effective inner alpha | **180/255** | **230/255** |
| Outer cylinder radius / RGBA | `.056 / (106,242,106,50)` | `.05 / (205,166,232,50)` |
| Billboard full width | `.5` | `.45` |
| Effective glow base alpha | **.5** | **.6** |
| Flight-particle chance per update | `.6` | `.6` |
| Flight-particle texture | `effects/md_particle.png` | `effects/md_particle_luck.png` |

Expert's constructor says inner alpha 230 and glow alpha .7, but its **actual doRender override resets them to 180 and .5 on every draw**. Luck has no such override. Keep these distinct. RendererRayComposite invokes glow, inner, outer, in that order, with inner `headFix=.98`, outer `1`. Cylinder geometry uses 12 radial segments and 4 longitudinal sqrt-profile head segments. Cylinder `width` is a radius; billboard `width` is a full width, halved by `drawBoard`.

View-optimization start offsets are first-person `(-.05,-.25,.2)`, otherwise `(.15,-.8,.23)`, rotated by legacy Float yaw math. End is not moved by that correction. Glow's first-person up is normalized `(0,1,-.5)`; otherwise it is the normalized camera-to-origin cross ray direction. Blend-in/out texture head lengths use base glow width even during short/zero blend-in. `RendererRayGlow` multiplies `getAlpha()` **and** `getGlowAlpha()`, whose latter value already contains alpha: squared lifetime alpha fade is original. Width/glow wiggles update per render, not per game tick.

Flight particle random order: probability nextDouble; distance `ranged(0,10)`; velocity X/Y/Z each `ranged(-.03,.03)`; MdParticleFactory decorator life `rangei(25,55)` (upper exclusive), alpha `ranged(.3,.6)`, size `rangef(.05F,.07F)`. Original Motion3D separately consumes a private RNG for zero-offset rotations; those calls do not advance public RandUtils RNG. Global client RNG sequence is frame/scheduling dependent; deterministic unit seeds do not promise full game replay parity.

Shared MRContextC block-hit particles are **always ordinary md_particle in this cached source**, including Luck: `particleTexture` is never assigned anywhere. Only Luck's beam-flight particle explicitly switches texture. `rangei(2,3)` returns 2 but consumes `nextInt(1)`; inclusive Scala `0 to max` therefore spawns exactly **three** hit particles. Their positions offset each coordinate by `[-.2,1.2)`, velocities each `[-.06,.06)`, and Rigidbody uses gravity `.01` with null block/entity collision selectors. The post-break sentinel packet is preserved by the source. Factory fade/alpha/size random calls happen after each particle's positional/velocity random draws.

Startup audio is `md.mine_expert_startup` or `md.mine_luck_startup`, volume `.4F`; shared player-following `md.mine_loop` loops at `.3F` and stops on termination. Original `sounds.json` marks these `master`, nonstreaming. Distinct icons are `abilities/meltdowner/skills/mine_ray_expert.png` and `mine_ray_luck.png`.

`source-media-sha256.json` verifies all **13** above ray/particle/icon/OGG files already exist in production and are byte-identical to the cached classic originals. No asset copy was needed. Byte identity is not sound playback, attenuation, pixel, texture UV or lifecycle parity, and does not resolve distribution rights.

## Unavoidable modern adapters and pitfalls

1. **Harvest levels/metadata:** MC1.21 has block states and tier/tool tags rather than classic integer metadata and arbitrary numeric harvest levels. Current Basic `MeltdownerBeamSupport.harvestLevel` reads target state, deliberately ignoring the previous metadata-position parameter. Advanced should state that adaptation explicitly. Diamond/iron/stone tags and incorrect-for-tool tags can approximate vanilla tiers, but cannot generally reconstruct modded 1.7 levels 4/5 or metadata-dependent levels. Hardness remains a float captured on acquisition. Never treat max harvest tier 5 as unconditional permission to destroy unbreakable/protected blocks.
2. **Modern negative height and sentinel collision:** Classic `(-1,-1,-1)` was out of the valid Y range. It is an actual block coordinate now. Bare sentinel equality can incorrectly enter continuing-target logic before acquisition/permission/harvest/hardness checks at that coordinate. Distinguish an absent target representation from a valid negative-height block, while retaining legacy sentinel packets after breaks, or explicitly disclose a deliberate source-level coordinate-collision choice.
3. **Loot context:** Modern block loot is data-driven `Block.getDrops` with block state, block entity, origin, player and synthetic tool. Expert needs plain drops (Fortune 0), Luck needs exactly Fortune III. A synthetic high-tier tool is an adapter for loot functions, not an actual equipped inventory tool: do not inherit held-item Silk Touch/other enchantments, mutate inventory/durability, multiply drops manually, or consume actual item enchanting resources. A plain iron tool reused from Basic can fail high-tier loot requirements; use and document a suitable synthetic high-tier context and acknowledge arbitrary modded tag limitations.
4. **Loot RNG:** Classic `Block.dropBlockAsItemWithChance`/fortune implementations and modern loot tables differ in distributions, draw counts and internal random streams, especially iron/gold raw ore and modern/modded blocks. Faithfulness is Fortune level, pre-removal state/block entity, chance 1, source operation order, and no additional custom luck math. Full 1.7 vs 1.21 random-bit or item-count parity is not generally achievable. A chance-1 random call may still advance RNG; decide/document its presence instead of calling it deterministic no-cost.
5. **No invented XP/extra break behavior:** The original ray explicitly drops items then sets air and grants skill XP; it does not explicitly emit vanilla XP or run a pickaxe's full playerDestroy pipeline. Modern `destroyBlock`, `spawnAfterBreak`, drops-before-and-after removal, or additional XP handling can duplicate/introduce outcomes. Keep exactly one item-drop path and preserve block entity data before removing the block.
6. **Modern protections and world lifetime:** Existing Basic adds loaded-chunk/build-height/config/mayInteract/native BreakEvent checks on top of classic BlockDestroyEvent, and rechecks at break time. These are intentional compatibility/safety guards, not a literal unchanged 1.7 pipeline. Reentrant/canceling listeners or changed state at break can make an adapter no-op: do not claim a mined block was removed merely because the pure session requested break; decide/document whether source-unconditional skill XP remains or a guarded adapter refuses it.
7. **Classic vs modern ray clipping:** Modern COLLIDER/no-fluid clipping is a reasonable collision-policy adaptation, but shape/boundary behavior and negative-world stepping need dedicated fixtures. Do not use the 15-unit cosmetic ray length as its 20-unit server range, add entity attacks, or let client packets choose target coordinates/range/fortune.
8. **Termination/transport:** Modern token/UUID/dimension/stale-input guards are transport/lifetime adapters. They should make duplicate starts/ends/particles idempotent and retain source final-failed-consumption mining, unless a stricter state/world safety guard explicitly aborts. Do not let start/end packets for one variant stop the other or turn a stale particle into a new session.

## Independent source oracle and scope

- 31 complete unchanged original source files are pinned as mandatory test resources, with original copyright headers, GPLv3/additional README notices, full GPL licence text, LambdaLib MIT notice and SHA256 manifest
- Nine complete unchanged Java class/interface bodies are executable test-side: `EntityMineRayExpert`, `EntityMineRayLuck`, `EntityRayBase`, `IRay`, `RendererRayComposite`, `RendererList`, `MdParticleFactory`, `Motion3D`, `RandUtils`. Only package/imports are relocated. `prepare_ray_oracle.py` asserts whole-body equality
- `AdvancedMineRayUpstreamOracleTest` checks mandatory SHA256 resources, whole-body equality, constructor timing/geometry/colors, **actual renderer override and composite order**, blend-in/fade math, 3,072 local/remote/update cases, both spawn/nonspawn paths, flight-particle endpoint/velocity/texture/factory values and complete public RNG advancement. `sample(luck,local,yaw,pitch,seed)` exposes independent upstream results for the port's differential tests
- Cached standalone JDK21 compile/run passed **46,985** checks. `-Dacademy.advancedMining.oracleSourceRoot=...` also verifies actual compiled Java source input hashes against pinned relocated resources. Compilation emits only the original RendererList raw-generic warning
- OpenGL sinks, minimal world/entity scheduler, particle creation, time, Minecraft identity and MathHelper table boundaries are test-local stubs. Full GL geometry/pixels/audio, real entity scheduler, modern/native loot, gameplay Scala MRContext, and loaded-world/game behavior are **not executed** by this ray oracle. Pinned source audit is not a claim of executable Scala parity
- No Gradle, game, server, save/world or desktop-computer process was started

To reproduce only this oracle from repo root, use `audit/verify_ray_oracle.sh`; the parent integration verifier may instead include these Java files/resources in its existing cached compile suite.
