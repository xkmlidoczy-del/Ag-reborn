# Electromaster prerequisites: source fidelity and integration

## Scope and canonical source

Private staged implementation only. Canonical sources are AcademyCraft1.0.7 commit00d19ec0cf538f61c1095c9292f5ee6863db4521 and LambdaLib1.2.3 in this repository's `.reference`. Source-derived code retains NOTICE/GPLv3 and the original extra restrictions. Existing non-song arc textures, skill icons and em.move_loop/em.lf_loop/em.mag_manip sounds are reused. No songs or song assets were added.

Common sources: `MagMovement.scala`, `MagManip.scala`, `CatElectromaster.java`, `EntityBlock.java`; LambdaLib `Rigidbody.java`, `Raytrace.java`, `Motion3D.java`.
Visual sources: `ArcPatterns.java`, `EntityArc.java`, `EntitySurroundArc.java`, `SubArc.java`, `SubArcHandler.java`, `CubePointFactory.java`, `RenderEntityBlock.java`; LambdaLib `ViewOptimize.java`.
ThunderBolt has its separate complete audit at `../thunderbolt/docs/THUNDERBOLT_FIDELITY.md`.

## Source learning chain once device/resource prerequisites exist

Completing this EM skill dependency chain does NOT make Railgun fully naturally obtainable in the current survival port. The tests below prove source learning eligibility and finite-IF development-state progression, with the needed developer devices/batteries supplied as test fixtures. Authentic Normal Developer crafting remains blocked by matrix_core0/crystal_normal; Advanced Developer crafting remains blocked by genuine node and other unfinished device/resource dependencies. The native-passed initial solar→portable→Arc route is a separate lower-tier result, not evidence of a survival-complete Normal/Advanced/Railgun route.

The unchanged catalog already defines the correct graph. Integration opens only the three actual implementations in server learning ingress, presets and the developer UI:

1. Arc Generation learned; train ArcGen≥.3 and learn Current Charging
2. Train Charging≥.7; reach level2; learn MagMovement with5 portable stimulations
3. Train MagMovement≥.5; learn MagManip with5 portable stimulations
4. Train MagManip to1; reach level4; learn ThunderBolt with11 advanced stimulations
5. Train ThunderBolt≥.3; learn Railgun with11 advanced stimulations

ThunderBolt's parent is learned ArcGen, with Charging≥.7. Railgun needs both ThunderBolt≥.3 and MagManip1. Portable cannot learn level4 skills. Skills are learned at EXP0, never automatically bound. Existing `/academy dev` mastery remains a test grant, not survival acquisition.

`MagneticRegressionTest` starts uncategorized, completes finite-IF DevelopmentProcess actions, uses source XP increments to train dependencies, validates each learning/tier gate, reaches Railgun and checks all presets remain empty. This is a genuine development-state simulation, not an operator grant. It does not prove acquiring materials/energy/machines in an actual survival world. Supplied finite normal-machine test batteries are used for higher-level advancement because a portable's10000IF capacity cannot alone pay level2→3's15 stimulations.

The original AbilityData.addSkillExp adds raw XP to level progression even when skill mastery has reached1. The simulation therefore trains lower-level skills to advance level3 instead of inventing a dependency on unfinished level3 skills.

## Magnetic Movement

- Captures Float mastery and source15→8CP/tick,60→30 initial overload
- Initial overload is consumed before target validation; source invalid termination still awards≥.005Float EXP
- Eye ray25blocks; accepted metal block target is fixed; minecarts/golem target tracks live eye position
- Per-axis acceleration.08 to speed1, including original squared-speed external-impulse correction
- Holds start overload as a floor, consumes CP once per actual server-world tick, no cooldown or hold timeout
- Ends on release/abort, invalid owner/state/world, dead entity target or CP exhaustion; clears fall distance and awards max(.005f,.0011f×traveledDistance)
- Server-authoritative motion and targets replace the original client-authoritative movement/effect-update packet stream
- Thin continuous arc:20templates,5passes,.08width,1.2offset,.2branch,.7shrink,20block unscaled patterns; source1texture/.1show/.6hide wiggle, segment-start clipping, positional em.move_loop volume.3 and first/third-person ViewOptimize hand offsets

### Original metal threshold quirk

CatElectromaster.isMetalBlock means normal OR weak. Consequently MagMovement's `<.6 && !isMetalBlock` branch never excludes a weak metal block. This quirk is retained and tested; we do not silently implement an imagined mastery unlock. Configurable default lists retain original normal/weak identities, using modern registry names or #block tags. Copper/deepslate iron are not silently added. The original magnetic-hook entity is not implemented; its absent ID is not claimed supported.

## Magnetic Manipulation

- Captures Float mastery;140→270CP,35→20strain,60→40cooldown,.5→1 recorded throw velocity
- Held accepted block item has priority; consumes one outside Creative. Otherwise custom metal-only10block ray ignores nonmetal occluders, exactly as LambdaLib's IBlockSelector did
- Doors and current port developer towers are excluded as original BlockDoor/BlockMulti were
- Free acquisition/holding; target is eye-.1Y + look×2; hover velocity is normalizedDelta×.2×min(distanceSquared/4,1)
- Real tracked block entity, real baked block state, synchronized1→3degree/tick yaw/pitch spin; not a generic projectile, fake block particle or explosive proxy
- Original Rigidbody checks eight corners in original order using old motion, advances once, then MagManip computes hover/gravity and advances again. The unusual double displacement is deliberately retained
- Source ActNothing applies-.04Y gravity each tick. No bounce, drag, arbitrary lifetime, homing after release or one-hit-per-flight deduplication
- Source entity damage is fixed10: the context's unused8→15 damage was never passed to the constructor. Uses plain ability attack, not EMDamageHelper creeper charging
- Release requires strict distanceSquared<25; enables collision placement even if release fails resource/distance checks. Valid release pays once, aims toward source20block looking endpoint (entity eye×.6), adds.005EXP/cooldown, plays em.mag_manip
- Abort enables physical placement/gravity; it does not delete the transported material
- Source ten-position face-normal placement search; no speculative world mutation before the protection event
- Continuous Thin surround arcs follow the physical entity through its flight;10templates,3passes,.2width,.8offset,.7branch,.9shrink,4arcs,1.5→2length,1.3bounding-size,scale.3; source SubArc frame.6/switch.7/life30, regenerate only when empty
- Source positional em.lf_loop volume.3 runs during the hold; stops at terminal packet, caster/session loss or local cancellation

## Explicit survival/security adapters

These are deliberate fixes of destructive source behavior, not parity claims:

- Source extraction used only Block identity and lost facing/container data. We retain actual BlockState and full block-entity payload; remove BE before set-air to prevent hopper/dispenser drop duplication; restore if entity spawn is rejected
- Source EntityBlock deletes itself on reload, losing transported materials. Named BlockState and BE data survive disk NBT; no held-input context survives reload, so the block resumes settle/gravity
- Source discards the block even if all ten placement attempts fail. We drop one recoverable block item, preserving BE payload, instead of deleting it
- Void/nonfinite recovery drops at the remembered source/recovery cell when loaded, avoiding source unbounded-fall material loss
- Extraction respects destroyBlocks, mayBuild, spawn/world interaction protection and NeoForge BreakEvent; collision placement respects border/loaded chunks/mayInteract and pre-mutation EntityPlaceEvent
- Common classes do not import client code. Owner state/world/liveness and actual world-tick deduplication protect replay/resource spending. Modern first-person eye coordinates replace 1.7 local-player position offsets
- Client token tombstones reject late/replayed starts and exact-token/monotonic-tick updates; level/player/connection replacement closes loops and removes transient arcs

## Verification and exact limits

Manual cached JDK21/NeoForge javac compiles new common/client/native sources plus private copies of shared integration files. No production changes, Gradle runs, Minecraft launches, EULA acceptance, desktop access, publication or pushes were performed by this worker.

Focused checks:30,104 magnetic numeric/development/visual-timeline assertions;131 revised existing preset assertions;5,083 ThunderBolt numeric/state;16 ThunderBolt learning/catalog;82 ThunderBolt timeline:35,416 total.11 magnetic +5 ThunderBolt common classes cold-link with client namespaces denied.

Nineteen native fixtures compile and are UNRUN:14 Magnetic +5 ThunderBolt. Magnetic covers source spend/update order, invalid/weak targets, distance EXP/fall reset, actual-world-tick CP exhaustion, held-item priority, filtered terrain selection, BE state persistence, actual hopper placement with contents, captured release costs/cooldown, strict5block release, double displacement, protection hook and integrated preset/logout. ThunderBolt scope is documented separately.

Full aggregate Gradle check, native suite, true local-client magnetic motion, geometry/audio playback, save/restart with an in-flight transported container, external multiplayer and original1.7.10/modern screenshot parity still need parent verification. A saved cold-NBT object fixture is not a two-process disk/world-restart test. Shared ClassicArcGeometry is an audited modern procedural adapter, not pixel-identical legacy GL display lists. Moving container BE state is preserved; its special BE renderer/animation is not drawn in flight (the actual baked block state and surrounding lightning are drawn).
