# ThunderBolt 1.0.7 source contract

Owned staging deliverable only. No production edits, Gradle, launcher, publication or user-computer access occurred.

## Authoritative behavior

ThunderBolt is an immediate single-keydown level-four Electromaster context. Successful consumption runs one ray and one endpoint-centered sphere; it has no hold charge, delayed projectile, ammunition, block destruction, or key-up refund.

The context captures float skill mastery before consumption. Primary range is 20. CP uses double lerp `280 + exp * 140`, truncated to integer. Overload uses float lerp 50→27; primary damage 10→25; AOE damage 6→15; cooldown float lerp 120→50 truncated to integer. Target selection counts as effective even if an attack hook or immunity rejects damage: any primary or AOE yields .005 EXP, otherwise .003. The source awards EXP before setting the cooldown, while its cooldown still uses the captured pre-award mastery.

The existing LambdaLib-derived ClassicRaytrace provides inflated .3 hitbox intercepts and the unusual entity-feet versus block-hit final comparison. Despite its name, traceLiving does not restrict the primary to living targets. ThunderBolt uses the selected entity's feet plus its full eye height as its endpoint. Block hits use the actual hit vector. AOE uses an 8-block inclusive sphere by **entity feet**, after an AABB intersection query; living entities and dragon parts qualify, caster/primary do not. There is no separate AOE line-of-sight check, so nearby entities behind blocks can qualify.

Normal learning is distinct from the operator grant: level4+, ArcGen learned, Charging EXP≥.7, advanced developer, eleven stimulations, then ThunderBolt starts at EXP0. Railgun additionally requires ThunderBolt EXP≥.3 and MagManip EXP1. The catalog already contains these requirements. Production whitelist/dispatch/UI integration is still the aggregator's responsibility.

## Source quirks intentionally preserved

- A null ray result calculates the endpoint from `new Motion3D(player)`'s normalized **velocity**, not head aim
- AOE slowdown rolls apply to the **primary target** rather than each AOE entity. At mastery>.2 and 80% chance, the direct attempt supplies Slowness IV for40t; every AOE iteration can supply20t to that same primary. Java's instanceof safely matches Scala's null.isInstanceOf=false, so a null primary is not a Scala NPE. Vanilla same-amplifier merging retains an existing longer40t duration
- Mastery is Float but the source threshold literal .2 is Double. Float .2f is slightly greater than Double .2 and therefore passes that strict comparison
- The EMDamageHelper30% charged-creeper roll follows the attack attempt even if hurt failed. The port reuses the existing saved-creeper-field adapter; it does not call thunderHit, which would introduce lightning damage/ignition
- Main effect creates three independent strongArc instances, all initially visible with pattern index0. Their length remains fixed20, ignoring the actual ray endpoint. Pattern branches beyond20 are still drawn
- AOE patterns remain twenty blocks long internally; `draw(length)` checks each segment's start-X, preserves the entire qualifying segment, and does not scale the pattern or clip that segment's end. AOE life is15..24ticks because RandUtils.rangei(15,25) excludes25
- EntityArc does not follow or move after construction. Main origin/direction and AOE endpoints are captured on receipt. ViewOptimize's local hand offsets apply to AOE arcs too and depend on the current first/third-person view at render time

## Modern guards and adaptations

- Stationary/invalid normalized velocity would produce NaN in the old Motion3D. Its modern miss endpoint is the caster eye, keeping damage queries and outgoing vectors finite. This changes the pathological stationary miss and is explicitly not silently claimed identical
- Existing modern attack-player/team restrictions also protect the ancillary slowdown, while selected-entity effective EXP classification remains source-style
- A synchronous per-caster reservation prevents reentrant damage callbacks from beginning another context before the source-ordered cooldown is committed. It is removed in finally and needs no persisted or tick state
- The normal server entry uses the level's random generator. A clearly labeled trusted `perform(ServerPlayer, RandomSource)` overload drives deterministic native fixtures through that same path; no client packet or slot dispatch accepts chance input
- Effect data captures authoritative AOE IDs and fallback endpoints before damage callbacks. The renderer uses the current client-side entity eye endpoint if that entity is present, matching the original client's entity-based AttackData use; missing tracked entities fall back to the server snapshot
- Server effects are sent within50blocks, consistent with the classic Context range. Only visual AOE serialization is capped at256; all selected AOE gameplay attacks remain processed. Client arcs are bounded to1024, finite packets are required, and degenerate/over64-block endpoint arcs are skipped
- Explicit modern eye coordinates replace LambdaLib Motion3D's obsolete remote-player `px += 1.6` shim. Original ViewOptimize offsets are retained: first person local(-.05,-.25,.2), third person(.15,-.8,.23)
- Shared ClassicArcGeometry/ClassicRenderTypes replace GL display lists/ShaderSimple with unlit textured ribbon quads. Existing geometry's bounded next-pass branch scheduling differs from the source's growing ping-pong list iteration, and its ribbon normals omit source randomRotate15 jitter. This is a source-parameter modern visual adapter, not pixel-identical procedural output
- ThunderBolt and charged-creeper achievement hooks remain unavailable with the wider classic achievement system; they are not claimed implemented

## Actual client effect

`ClassicThunderBoltEffects` is a Dist.CLIENT event subscriber and is never referenced from common code. It uses the existing original non-song `em.arc_strong` OGG and `effects/arc/line_segment.png`. It plays one non-looping entity-following strong-arc sound at volume .6, pitch1. Main Life20 and random AOE Life15..24 use client ticks, pause with the game, clear on world/player/connection changes and reject queued packets belonging to a replaced session. EntityArc defaults are texture replacement probability .5 per tick and visibility show/hide probability .2/.2. Strong patterns:20templates,5passes,width.3,maxOffset1.4,branch.3. AOE patterns:20templates,5passes,width.13,maxOffset1.2,branch.28. Width shrink is.7.

## Verification and remaining work

The cache-only script `scripts/verify-thunderbolt.sh` compiles all staged common/client/native-fixture Java against JDK21 and cached NeoForge21.1.252 libraries, then runs the deterministic rules, natural-learning/catalog, visual timeline and cold server-link checks. Actual results:5,083 numeric/state assertions,16 natural-learning/catalog assertions,82 visual timeline assertions,5 cold-linked common classes with client namespaces denied, and3 existing non-song asset/event checks. It never runs Gradle, starts Minecraft, accepts an EULA or performs audio playback.

Five native GameTests are staged in `AcademyThunderBoltRuntimeTests`. Their dedicated40×6×56 in-memory template contains the20-block aim ray and8-block endpoint spheres, avoiding the old8-wide neighboring-fixture hazard. Test fixture ownership includes every spawned entity, fake player's ability state and event hook; cleanup runs in finally as well as pass/failure/rerun listeners and continues releasing remaining objects if one cleanup fails. The wrong-primary debuff uses an owned seeded RandomSource rather than changing the world's generator. These tests compile but have not run. The authenticated-slot test intentionally requires the aggregator's actual dispatch/whitelist integration before execution.

Native world effects, existing event armor/PvP integration, current-view hand offsets, actual rendered procedural shape, sound audibility and external multiplayer are not executed here. The staged native cases cover primary/AOE exact damages and exclusion, inclusive8-block feet sphere versus cube corner, AOE through intervening blocks, moving-miss versus stationary finite endpoint, wrong-primary debuff with AOE-only null primary, rejected-damage effective EXP and reentrant/cooldown/resource replay guards, and normal zero-mastery authenticated preset slots versus spoofed direct skill-name requests. Further targeted work can cover nonliving primary, charged-creeper rolls, actual PvP/armor integration and audiovisual captures.

## Reference files

- AcademyCraft1.0.7 `src/main/scala/cn/academy/vanilla/electromaster/skill/ThunderBolt.scala`
- AcademyCraft1.0.7 `src/main/java/cn/academy/vanilla/electromaster/skill/EMDamageHelper.java`
- AcademyCraft1.0.7 `src/main/java/cn/academy/vanilla/electromaster/entity/EntityArc.java`
- AcademyCraft1.0.7 `src/main/java/cn/academy/vanilla/electromaster/client/effect/ArcPatterns.java` and `ArcFactory.java`
- AcademyCraft1.0.7 `core/client/sound/ACSounds.java` and `FollowEntitySound.java`
- LambdaLib1.2.3 `util/helper/Motion3D.java`, `util/mc/Raytrace.java`, `WorldUtils.java`, `EntitySelectors.java`, `util/entityx/handlers/Life.java`, `util/deprecated/ViewOptimize.java`, `util/generic/RandUtils.java` and `MathUtils.java`

## Survival acquisition boundary

The EXP0 learning and prerequisite tests validate eligibility with a supplied Advanced Developer fixture. They do not make ThunderBolt/Railgun fully naturally obtainable in current survival. Authentic Normal Developer crafting still requires unfinished matrix_core0/crystal_normal, and Advanced Developer crafting still requires the genuine node/other unfinished device dependencies. Finite fixture energy is not proof of acquiring those devices/resources. The separately native-passed initial solar→portable→Arc route does not cover the higher-tier chain.
