# First Meltdowner skill: Electron Bomb

This implementation ports the server-side default mechanics of classic AcademyCraft 1.0.7's `meltdowner.electron_bomb` to NeoForge 21.1.252 / Minecraft 1.21.1. It does not claim original visual or complete category parity.

## Audited sources

Relative to `.reference/AcademyCraft-1.0.7`:

- `src/main/scala/cn/academy/vanilla/meltdowner/skill/ElectronBomb.scala`
- `src/main/java/cn/academy/vanilla/meltdowner/entity/EntityMdBall.java`
- `src/main/java/cn/academy/vanilla/meltdowner/skill/MDDamageHelper.java`
- `src/main/scala/cn/academy/vanilla/meltdowner/passiveskill/RadiationIntensify.scala`
- `src/main/java/cn/academy/ability/api/AbilityContext.java`
- `src/main/java/cn/academy/ability/api/Skill.java` (`SingleKeyDelegate`)

Relative to `.reference/LambdaLib-1.2.3`:

- `src/main/java/cn/lambdalib/util/mc/Raytrace.java`
- `src/main/java/cn/lambdalib/util/mc/BlockSelectors.java`
- `src/main/java/cn/lambdalib/util/helper/Motion3D.java`
- `src/main/java/cn/lambdalib/util/entityx/EntityAdvanced.java`
- `src/main/java/cn/lambdalib/util/entityx/EntityX.java`

The skill is the Meltdowner root, classic numeric index 0, level 1, controllable, with no explicit skill dependency. The portable developer requires three learning stimulations. These facts match `docs/classic-skills.json` and `docs/classic-behavior-audit.md`.

## Accepted launch and delayed shot

At mastery `e` captured before its own EXP gain:

| Property | Formula / value |
|---|---|
| CP | `lerp(35,80,e)` |
| Overload | `lerp(16,13,e)` |
| Raw damage | `lerp(6,12,e)` |
| Eye-aim range | 15 blocks |
| Orb life | 20 ticks; 5 when `e >= .8` |
| Shot callback age | Orb life minus 2: 18 or 3 ticks |
| Cooldown | Integer truncation of `lerp(20,10,e)` ticks |
| EXP | `.005` per accepted launch, regardless of eventual hit |

Electron Bomb executes on `MSG_MADEALIVE`. It is an immediate single-key activation, not a held charge or release-triggered attack. `EBContext` has no key-up or key-abort listener. Ordinary key release therefore must not revoke an accepted launch.

Consumption happens before spawning the ball. EXP and cooldown are committed immediately after successful consumption, rather than waiting for the delayed shot. The manifest's phrase “On successful shot” is shorthand for this successful launch branch. Damage, cooldown and the `.8` life threshold all use pre-award mastery. Crossing `.8` via this launch's EXP only improves a later ball.

The classic ball picks a fixed world-space offset using spawn yaw: angle `-yaw + uniform(-.45pi,.45pi)`, horizontal radius `.8..1.3`, and vertical sub-offset `-1.2.. .2`. Its server position adds another `1.6` to Y. It follows player translation without rotating the offset when the player later turns.

`EntityX` dispatches its age-indexed callback inside `EntityAdvanced.onUpdate`, before `EntityMdBall.onUpdate` refreshes the ball position. The shot therefore starts at the preceding ball-update position. The port stores that prior position explicitly; its aim is sampled from the player's current server eye position and head yaw at firing time.

The first ray finds the player's current looking destination. If it selects an entity, LambdaLib returns the entity base position and then adds `.6 * targetEyeHeight` to Y. A second ray travels from the orb to that destination. Only an entity collision applies damage. A block collision stops the damage trace but neither destroys nor ignites the block. The classic visual still extends to the looking destination, even if this oblique second ray hit sooner.

LambdaLib entity selection expands bounding boxes by the literal float `.3F`, selects the nearest legacy six-plane box intercept, then reports entity base position rather than the intercept. Entity-versus-block comparison consequently uses distance to that base position; an entity wins equal distances. Starting inside a box chooses its exit surface, and a segment wholly inside has no box intercept. The port reuses the tested legacy-intersection helper from `ThreateningTeleport` rather than modern `AABB.clip`, which behaves differently for this case.

## Server integration

`cn.academy.port.skill.ElectronBomb` exposes:

- `perform(ServerPlayer)`: validate the actual player and stored ability state, consume once and queue one delayed launch
- `tick(ServerPlayer)`: call once per player's server post tick to update/finalize pending launches
- `abort(ServerPlayer)`: remove transient launches on logout, clone/death or equivalent lifecycle cleanup
- `clear()`: clear launches when the server stops

All player operations must occur on the server thread. The implementation explicitly checks this and rejects off-thread requests. Requests supply no client position, target, damage, cost or mastery. Activation validates category `meltdowner`, level at least 1, learned skill, active abilities, interference/overload lock and cooldown. Dead or spectator players cannot launch.

After acceptance, the delayed tick does not recheck cooldown or `canUse`: the cast's own new cooldown would make that test fail. Once cooldown ends, a new input activation may queue another ball while an older one is pending. Each accepted launch is removed before damage callbacks and can fire at most once. Death, spectating, loss of the category/learned skill, leaving its dimension, logout and server stop clean up its transient state. The cleanup has no resource refund.

The shared `AbilityDamage.attack(player,"meltdowner.electron_bomb",target,amount)` is used at impact. This skill uses ordinary armor, matching classic `SkillDamageSource`; no armor bypass or reflection behavior is requested by Electron Bomb itself. Shared port damage scaling, PVP configuration and attack events apply. Persist/sync the changed ability state through the normal gameplay request/tick lifecycle.

## Explicit remaining gaps

- The ball is an authoritative logical delayed launch, not a registered/synchronized Minecraft entity. This is sufficient for its server damage schedule because classic `EntityMdBall` is noncollidable. Original entity tracking and client billboard rendering are not reproduced
- The server emits `electron_bomb_charge` with actual origin, entity ID, life and `offset_x/y/z` (including server Y correction); it emits `electron_bomb` with actual orb origin, normalized direction and destination length. These packets need their own client visual implementation
- The original `EntityMdBall` random five-texture/glow animation, alpha/size/wiggle curves, render-pass behavior and first/third-person coordinate corrections are not implemented here
- The original `EntityMdRaySmall` composite renderer and sound are not implemented here
- The `rad_intensify` passive's 60-tick radiation marking, subsequent-damage multiplier and target particles are not implemented by this first-skill class. In classic code those are optional effects in `MDDamageHelper`, not changes to Electron Bomb's raw direct damage
- The port uses the world's modern RNG for the same offset distributions, not the original ball/global RNG stream. Cross-version collision shapes and loaded-world scheduling cannot be asserted byte-for-byte identical
- The logical schedule uses elapsed server game ticks; it is not a wall-clock timer and does not survive server restart or player logout

## Verification

`ElectronBombRegressionTest` passes 59 deterministic assertions covering endpoint/interpolated costs, fixed range, cooldown truncation, `.8` threshold and captured pre-award state, life-minus-two readiness, success/failure ordering, overload locking, creative training, capped mastery level progress, category/level/learned/active/interference validation and duplicate cooldown rejection.

`compileJava`, `compileTestJava` and the standalone Java regression were run successfully. The regression does not bootstrap Minecraft. No Minecraft client, dedicated server or GameTest process was launched for this verification; runtime/visual behavior still needs an authorized in-game smoke test.

## Later shared passive integration

`RadiationMarks` extends the initial skill slice: Electron Bomb now attacks first, then stamps a learned Radiation Intensify mark. The mark lasts at least60living-update ticks, inheriting a longer caster mark as the source does, and multiplies all incoming damage by1.4→1.8 based on customized mastery(maxCP/event-adjusted level5initialCP). It uses the modern pre-mitigation incoming-damage event and persists counters on the target. The original client sync addresses the caster rather than the marked target; that source quirk and client MD-particle rendering are still unimplemented/unaudited as a visual path. Event ordering and live-world armor behavior need native verification.
