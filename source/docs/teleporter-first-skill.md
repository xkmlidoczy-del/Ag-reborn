# First Teleporter increment: Threatening Teleport

## Source and scope

The baseline is AcademyCraft tag **1.0.7**, commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, not its later master branch. The gameplay below was audited from:

- `src/main/scala/cn/academy/vanilla/teleporter/skill/ThreateningTeleport.scala`
- `src/main/java/cn/academy/vanilla/teleporter/util/TPSkillHelper.java`
- `src/main/java/cn/academy/ability/api/{AbilityContext.java,AbilityPipeline.java}`
- `src/main/java/cn/academy/ability/api/data/{AbilityData.java,CPData.java}`
- `src/main/java/cn/academy/vanilla/teleporter/CatTeleporter.java`

Targeting helpers were additionally read from the exact **LambdaLib 1.2.3** archive at `.reference/LambdaLib-1.2.3`, specifically `util/mc/{Raytrace.java,BlockSelectors.java,EntitySelectors.java}` and `util/helper/Motion3D.java`. Archive SHA256: `06dab431270e1c36351f403c67a24a2e968b50b16293bfb762b66a6a92c987d3`. Its MIT notice is preserved in `docs/LAMBDALIB-LICENSE`.

Implementation: `src/main/java/cn/academy/port/skill/ThreateningTeleport.java`. This is an item teleport attack, **not** a teleport of the player. It has no flying projectile and no charge-dependent damage. The server implementation is compiled; runtime-world execution and exact visual parity are not claimed.

## Audited default behavior

Let `e` be normalized Threatening Teleport mastery. Original `TTContext.exp` captures `e` when the context starts; CP, overload, range and cooldown use that snapshot. Damage reads current Threatening Teleport mastery when executing.

| Property | At e=0 | At e=1 | Rule |
|---|---:|---:|---|
| CP cost | 35 | 100 | Linear interpolation |
| Overload | 18 | 10 | Linear interpolation |
| Range | 8 | 15 | Linear interpolation |
| Raw damage | 3 | 6 | Linear interpolation of execution-time mastery |
| Cooldown | 30 ticks | 15 ticks | Truncate interpolated value toward zero |
| Needle damage | 4.5 | 9 | Raw damage ×1.5 before critical multiplier |
| EXP on traced entity | .003 | .003 | Independent of whether damage is accepted |
| EXP otherwise | .0006 | .0006 | Includes both block placement and full-range miss |
| Item return on traced entity | 30% | 30% | One item is dropped at target position + target height |
| Item return otherwise | 100% | 100% | One item is dropped at block hit or full-range endpoint |

- Key-down starts a context; key-up executes and terminates it. No minimum hold duration, maximum hold duration, automatic shot or charge cost exists in this skill
- Any nonempty **main-hand** stack is valid. The current main-hand item at release is used; changing directly between two nonempty items is allowed
- An empty hand at creation or a held-context server tick terminates the context. Key abort has no cost or item loss
- Costs are checked before attacking or consuming an item. If CP is insufficient, there is no ammo debit, damage, drop, EXP or cooldown
- On successful execution, the order is CP/overload consumption, optional critical processing/attack, one-item debit, return roll/drop, EXP, cooldown
- Creative bypasses CP/overload debit and item debit, but still gets training, EXP and cooldown. It can still spawn the returned item, matching the source's creative duplication behavior
- `academy:needle` is recognized by exact item registry identity. Any other item, even a renamed needle-like item, receives ordinary damage
- Returned items retain their item components, including custom name/data. The port copies one item before shrinking the held stack, preserving the source's last-item return despite modern `ItemStack.copy` treating a depleted stack as empty
- Armor is bypassed through the shared `AbilityDamage` pipeline. Resistance, normal invulnerability, PvP/team protections, configured player-attack blocking, mutable skill attack events and NeoForge damage hooks still apply. The source does not use magic damage or a projectile damage classification

## Targeting

The first classic ray has the living selector and `BlockSelectors.filEverything`. LambdaLib 1.2.3 defines that block filter as always false: **all blocks are ignored for this first trace**. A living entity behind a wall can therefore be hit. A normal block-only trace is performed only when no eligible entity intersects the full-range ray.

The source's living selector also includes dragon parts. The port selects living entities and `EnderDragonPart`, excludes the caster, and requires a live, nonspectator, pickable target. NeoForge's entity query includes multipart dragon bodies.

The broad phase is the eye-to-end bounding box inflated by 1. The narrow phase reproduces the fixed **float .3** inflation and six-plane legacy intercept. In particular, a ray that starts inside an inflated target box selects the exit face if the exit is in range; a ray entirely contained in the box has no intercept. LambdaLib's `d0 == 0` target-order quirk is retained. A target drop is located at its world position plus bounding height; a block drop uses the block hit vector; a miss uses eye position plus look direction × range.

Modern block fallback uses collision shapes with `ClipContext.Block.COLLIDER` / `Fluid.NONE`. This corresponds to the old normal-block selector's collidable/nonliquid intent; exact cross-version shape differences still need live comparison. No line-of-sight gate is added to the entity-first trace.

## Critical passives and original progression quirk

Let `d` be Dim Folding Theorem mastery and `s` Space Fluctuation mastery. A contribution is zero when its passive is unlearned.

| Tier | Conditional roll probability | Damage multiplier |
|---|---|---:|
| 0 | lerp(.10,.20,d) + lerp(.18,.25,s) | 1.3 |
| 1 | lerp(.10,.15,s) | 1.6 |
| 2 | lerp(.01,.03,s) | 2.6 |

The three independent rolls occur in order 0,1,2. The first successful roll ends the loop. This is not a combined weighted lottery; at fully mastered passives the effective tier probabilities are .45, .0825 and .014025. Rolls use strict `<` and even unlearned zero-probability tiers consume a draw.

Every critical hit adds `(tier+1)*.005` Dim Folding EXP and `.0001` Space Fluctuation EXP. The original `AbilityData.addSkillExp` first calls `learnSkill`, with no level/tree prerequisite check. Consequently, a Dim Folding critical hit can automatically learn **level-4 Space Fluctuation at level 1**. A Space-Fluctuation-only critical hit can similarly learn Dim Folding.

This surprising behavior is preserved as a documented source quirk. Learning a new passive invokes the equivalent of `CPData.Events.learnedSkill -> recalcMaxValue`: CP is refilled and current overload is cleared, while recovery delays and `overloadFine` are preserved. Already learned passives do not trigger another refill. Neither the player's level nor the normal developer/tree learning requirements are altered. Normal manual learning still requires the catalog prerequisites.

## Integration contract

All player entry points enforce the logical server thread; caller-supplied mastery, range, damage, target and coordinates are never accepted.

| Method | Call site | Meaning |
|---|---|---|
| `boolean start(ServerPlayer)` | Skill-specific key-down request | Opens one held context and snapshots mastery; duplicate starts return false |
| `void tick(ServerPlayer)` | Once per player server post tick | Cancels on invalid ability state, death, spectator, empty hand, changed state identity, respawn ID or dimension |
| `boolean release(ServerPlayer)` | Skill-specific key-up request | Removes context before validation; true means a paid successful execution, including a miss |
| `boolean perform(ServerPlayer)` | Optional immediate cast path | Safe start+release convenience; will not overwrite an already held context |
| `void abort(ServerPlayer)` | Abort, deactivation/preset switch, logout and clone | Removes transient context with no debit |
| `void clear()` | Server stop | Clears all transient contexts |

The caller owns request routing, input/UI, lifecycle registration and normal state sync. `release` saves state after successful execution. Every failed release terminates the session, preventing replay. Ability gates include category, catalog controllability/minimum level, learned skill, activation, overload lock, interference and cooldown. Sessions are additionally bound to state identity, server entity ID and dimension.

Before querying/clipping, the port checks the bounded broad-phase chunk footprint using `hasChunk`; it does not force chunk loading or generation. Nonfinite aim/position and missing chunks cancel before charging CP. These defensive validation cancellations are explicit differences from the older unchecked helper.

## Verification and remaining gaps

- `scripts/gradle-cloud.sh compileJava --no-daemon`: **PASS**, exact Java 21 / Minecraft 1.21.1 / NeoForge 21.1.252 APIs
- `ThreateningTeleportRegressionTest`: **117 PASS** deterministic assertions covering curves, needle multiplier, EXP/drop coefficients, server-state gates, sequential critical rolls, automatic passive learning/refill semantics and legacy entity-ray geometry
- The standalone main can be registered by a Gradle `JavaExec` task using `sourceSets.test.runtimeClasspath`, depending on `testClasses`; no game process is required
- Server/client/GameTest execution: **NOT RUN**, pending Minecraft EULA authorization
- Live-world armor/PvP hooks, loaded-chunk rejection, item-component survival, actual hit/miss drop spawning, same-tick replay, empty-hand cancellation, held-item switching, state replacement/respawn/dimension cleanup and creative duplication still need runtime-world tests
- `AcademyNetwork.effect(player,"threatening_teleport",range)` currently carries only the caster's eye/direction/range notification. It cannot encode the actual entity-top/block endpoint, hovered target, marker color, critical tier or legacy particle origin offsets. The original moving marker, target tint, teleport particle trail, exact sound playback, server/client critical event and achievements remain pending until payload/client integration
- Original skill-local CP/overload/EXP multiplier configuration and the full classic event ecosystem are not implemented by this class. Default coefficients and the shared modern damage/config pipeline are implemented
- Other Teleporter skills, full developer progression and category visuals are outside this increment

Do not describe a catalog entry, compiled class, deterministic test pass or approximate notification as full Teleporter gameplay/visual parity.
