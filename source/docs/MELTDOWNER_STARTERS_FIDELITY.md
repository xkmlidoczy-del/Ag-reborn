# Portable Meltdowner starters: Scatter Bomb and Light Shield

Private development increment for AcademyCraft classic1.0.7 (`00d19ec0cf538f61c1095c9292f5ee6863db4521`) to Minecraft1.21.1 / NeoForge21.1.252. The complete port remains unfinished. GPLv3 and upstream additional restrictions remain in `LICENSE`/`NOTICE`; no song media is introduced.

## Audited executable sources

AcademyCraft `.reference/AcademyCraft-1.0.7`:

- `src/main/scala/cn/academy/vanilla/meltdowner/skill/ScatterBomb.scala`, `LightShield.scala`
- `src/main/java/cn/academy/vanilla/meltdowner/CatMeltdowner.java`
- `src/main/java/cn/academy/vanilla/meltdowner/entity/EntityMdBall.java`, `EntityMdRaySmall.java`, `EntityMdShield.java`
- `src/main/java/cn/academy/vanilla/meltdowner/client/render/RenderMdShield.java`, `MdParticleFactory.java`
- `src/main/java/cn/academy/vanilla/meltdowner/skill/MDDamageHelper.java`
- `src/main/java/cn/academy/ability/api/Skill.java`, `AbilityContext.java`, `context/ContextManager.java`, `data/CPData.java`

LambdaLib `.reference/LambdaLib-1.2.3`:

- `src/main/java/cn/lambdalib/util/helper/Motion3D.java`
- `src/main/java/cn/lambdalib/util/mc/WorldUtils.java`, `EntitySelectors.java`, `Raytrace.java`
- `src/main/java/cn/lambdalib/util/entityx/EntityAdvanced.java`, `EntityX.java`
- Particle, MeshUtils and ray renderer paths listed in `MELTDOWNER_STARTER_VISUALS.md`

## Genuine earning and input

Canonical catalog entries and IDs remain unchanged: `meltdowner.scatter_bomb` is active level2/index2 with learned Electron Bomb EXP>=.8; `meltdowner.light_shield` is active level2/index3 with Electron Bomb EXP>=1. Both use the portable or higher developer, five stimulations. Portable source TPS+1 scheduling is130 ticks and3900 real IF, distinct from the nominal3750IF estimate. Existing actual finite batteries, completion revalidation, category/level prerequisites and stimulation lifecycle remain authoritative. No auto-learning, auto-binding, infinite energy or mastery grant is added.

The shared `SkillAvailability` registry introduced in the main lane already derives developer ingress from `PresetSkills.IMPLEMENTED`; only the two active IDs are added there. No duplicate GUI or machine list is introduced. The gameplay overlay preserves every prior active path. Authenticated preset slot transitions are the only client ingress. The client nonce correlates results and prevents a retired press from reviving a visual; it cannot supply tick counts, targets, positions, costs or mastery. Classic SingleKeyDelegate state is ACTIVE while a context exists; neither skill has an invented CHARGE/ready20 HUD state.

## Scatter Bomb

Every formula uses mastery captured as a float at context construction, before any new award:

| Value | Literal source rule |
|---|---|
| Initial overload | float lerp80->60 |
| CP per tick1..80 | float lerp3->6 |
| Raw damage per ball | float lerp5->9 |
| Ball creation | ticks20,30,40,50,60,70,80 |
| Ball lifetime | 2333333 ticks, the default constructor value |
| Destination | current eye + normalized current head aim perturbed independently per ball by yaw[-5,5), pitch[-2.5,2.5), *15 |
| Forced end | ordinary player-self damage6 at tick200 |
| Termination EXP | float .001 * accumulated ball count |
| Cooldown | none in executable source |

Tick80 is paid; ticks81..200 consume no CP. Initial overload is held as a floor while charging/holding. Creation happens BEFORE the same tick's CP consume, so a failed tick20 can still create and fire its first ball. Ordinary key-up and key-abort both terminate and shoot all accumulated balls. Preset switches/edits, deactivation and interference use that ordinary termination path; they do not silently erase a prepared Scatter attack. Early termination before20 has no balls and adds zero EXP.

Offsets use spawn body yaw plus a random[-.45pi,.45pi] angle, radius[.8,1.3], and subY[-1.2,.2]+1.6 server correction. They remain fixed in world space while following player translation. The port stores logical orb positions updated at each owned server tick rather than registering noncollidable entities. A release between ticks uses the last orb update, matching the entity-position role. Exact world/entity/context scheduling between1.7.10 and1.21.1 remains a parity limit.

Unlike Electron Bomb, Scatter destination is not a first looking-position ray. Each orb traces to a raw15-block eye destination sampled independently by Motion3D's offset5 rule. The exact float-before-double constructor arithmetic and65536-entry sine lookup are retained in ScatterBombAim; server-owned world RNG supplies the same distributions rather than the old static global stream. Shared tested LambdaLib-style ray semantics select the nearest inflated hitbox and use entity feet for entity-versus-block comparison. Blocks stop attacks and are not destroyed or ignited. On each entity hit, native living invulnerableTime is set to-1 before the skill damage callback, allowing all seven balls to deal distinct hits. The full original small-ray visual still extends to the destination even when damage was blocked sooner. EXP is added after iterating all balls, including misses and canceled damage hooks.

## Light Shield

Every interpolated value is captured as a float at construction:

| Value | Literal source rule |
|---|---|
| Initial overload |110->60 |
| Tick CP |9->4 |
| Maximum time |float120->180; end only when ticks>maximum |
| Touch CP / overload |50->30 CP,5->3 overload |
| Touch raw damage |2->6 |
| Incoming absorb amount |15->50 |
| Incoming CP / overload |5->3 CP,50->30 overload, reversed source arguments |
| Incoming repeat gate |ticks-lastAbsorb<=18 suppresses, tick19 permits |
| Tick EXP |float1e-6 |
| Touch / handled incoming EXP |float .001 |
| Termination |SlownessII100 ticks, captured-mastery int-truncated lerp(2*ticks,ticks) cooldown |

At novice/mastery endpoints, timeout occurs at121/181. Source terminate() only marks its context disposed; the tick handler continues. The terminating tick still consumes tick CP, adds EXP and tests touch candidates. A failed tick consume likewise still reaches the remaining handler, with its trailing tick EXP. Every successful touch consumption awards .001 after trying the damage callback, even if damage is canceled. Failed touch consumption adds no touch EXP. A zero-tick release still applies Slowness, with a zero cooldown.

Touch is a source feet-distance<=3 sphere after an axis-aligned query, excludes the caster and checks raw body yaw with `abs(-degrees(atan2(dx,dz))-bodyYaw)%360<60`. It does not use head yaw, vertical aim, the visible shield mesh or line of sight. The unnormalized modulo comparison intentionally retains wrap asymmetry. Native living invulnerableTime<=0 permits contact; the port does not clear immunity for touch attacks.

Three especially surprising executable quirks are preserved, not corrected according to descriptive ability text:

1. `ctx.consume(getAbsorbConsumption,getAbsorbOverload)` reverses incoming overload/CP relative to touch, because the API signature is consume(overload,CP)
2. The unbraced `if(entity!=null) if(reachable(entity)) perform=true else perform=true` binds else to the inner if. ANY non-null direct source is absorb-eligible, including rear/far sources. Null environmental damage is not absorbed but still reaches .001 EXP. Zero damage and interval-suppressed hits return before EXP
3. `ContextManager.find(LSContext)` in the player-damage event does not verify the hurt player is the context owner. The first active context can absorb damage to another player, even another dimension. This port selects first live insertion order in one server, reflecting the sequential LinkedList stream's practical behavior. `findAny` itself is not a formal source ordering guarantee. No active shield means no modification

Eligible incoming attempts install lastAbsorb BEFORE consumption, even when CP is insufficient. Thus failed attempts still suppress retry for18ticks. Incoming EXP is still awarded on failed attempts. Overload reached by a consume terminates the owned context; work already within the current tick body remains ordered before final disposal. The modern incoming event is the pre-mitigation counterpart of1.7's LivingHurtEvent. Relative listener registration order with existing RadiationMarks/other mods is not claimed identical across versions.

## Damage, transport and lifecycle

Both paths use normal shared ability damage/PVP/config/events plus the existing radiation mark behavior. A narrow RadiationMarks overload accepts the canonical skill ID; the original Electron Bomb call continues to delegate to its original ID. Attack precedes optional marking, even if the attack callback rejects. The existing radiation implementation's client mark-sync/target-particle parity gap is inherited and is not claimed solved here.

Each server context captures its dimension, actual AbilityProgress identity and recipient audience within25 blocks plus caster. Server-owned elapsed ticks are processed at most once per world tick. Death, logout, clone, category/learned loss, dimension change and server shutdown discard contexts without stale-world damage, EXP awards, cooldown or potion changes; ordinary input cancellation remains the source termination path. This is an intentional modern lifecycle safety distinction: classic ContextManager's universal termination callback could attack on some death/category/keepalive disposals. It is documented rather than called byte-for-byte parity. Cloned replacement actors acquire fresh contexts against replacement storage; the original cache is retired.

Contexts are transient and do not persist across restarts. All accepted finite resource spend remains persisted through existing AbilityStorage. Replay guards retire only on genuine lifecycle disposal. Packets expose validated primitives with positive tokens and client input correlation; starts, ball indices, rays and ends are guarded against stale session/entity/player/connection identity and repeated delivery. Context recipients receive end notifications even after moving away from the start location.

## Visuals and verification limits

See `MELTDOWNER_STARTER_VISUALS.md`, `MELTDOWNER_CLIENT_HOOKS.md`, `audit/client-original-assets.json` and `MELTDOWNER_STARTERS_QA.md`. Original sound/texture bytes are reused; the source shield is a flat textured quad, not an invented OBJ. New shaders adapt the original alpha-cutoff contract. Source formulas, coordinates, curves, topology, audio parameters and stale-packet behavior are tested; actual rendered pixels, audible playback, external multiplayer and1.7-vs1.21 recording parity require main-lane native/client execution. Compiling native fixtures is not executing them. No complete-category/full-port claim is made.
