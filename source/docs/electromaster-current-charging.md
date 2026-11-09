# Current Charging: source fidelity and verification boundary

Baseline: AcademyCraft **1.0.7**, LambdaLib **1.2.3**, Minecraft **1.21.1**, NeoForge **21.1.252**. New adaptations retain Lambda Innovation attribution and the GPLv3/upstream notices in `NOTICE` and `LICENSE`; shared LambdaLib ray algorithms retain their MIT notice.

## Complete bounded skill path

`electromaster.charging` is the original level-one held skill, parent `arc_gen` at mastery **0.3**. The existing catalog owns learning dependencies. The portable developer is a real finite native IF target; NeoForge item and block FE capabilities provide an actual compatibility path. This does not implement all original energy machines.

Integration contract:

- `CurrentCharging.start(ServerPlayer)` on key-down; `tick` on server player post-tick; `release` on key-up; `abort` for input cancellation, deactivation/preset/screen cancellation, logout, clone, death and dimension change; `clear` on server stop
- Learning allowlist and preset/input selection include `charging`
- Forward `charging_start` / `charging_end` in client-only code to `ClassicChargingEffects.receive`
- Effect tags carry `entity:int`, `token:long`, and `item_mode:boolean` on start. Tokens are positive and monotonically increasing for the running server. The captured 25-block audience includes the caster and receives termination even after leaving the start radius
- Normal gameplay owners continue saving/synchronizing `AbilityProgress`; the context is transient and is never persisted

## Source-derived server semantics

`CurrentChargingSession` separates deterministic ordering from world lookup. At start it captures mastery and **whether the main hand is nonempty**, consumes **65→48 overload and zero CP**, and captures the resulting overload as a floor. It does not require an energy-compatible item to begin, set a cooldown, or grant start EXP. On every tick it restores a lower overload to that floor. Transfer is **15→35 IF/tick** and CP is **3→7/tick**, using source float interpolation and captured mastery.

The source's two tick paths are intentionally different:

1. **Item mode:** read the current main hand, end if empty, consume CP, classify support, charge with bandwidth enabled, add EXP
2. **Block mode:** re-trace the current head aim to 15 blocks, classify a block energy target, charge with `ignoreBandwidth=true`, add EXP, then consume CP

Consequently switching nonempty held items changes the charged stack while retaining original mastery/mode. Emptying an item-mode hand terminates rather than converting to block mode. Equipping an item after a block-mode start does not change that mode. The original block branch can perform its **final transfer and EXP award on the tick whose CP check fails**; the item branch cannot. This quirk is covered, not silently reordered.

EXP is source **0.0001f** for a supported target and **0.00003f** otherwise. `effective/good` in the source means helper support, not energy actually accepted. Full or extract-only supported targets therefore retain supported EXP. Missing/occluding entity/unsupported block traces get smaller EXP. `traceLiving` means trace **from the eye**, not filter targets to living entities; `ClassicRaytrace` preserves LambdaLib's inflated entity intercept and unusual entity-feet-versus-block comparison.

Start that reaches the overload cap commits its strain but leaves no live context, corresponding to the original `OverloadEvent` disposal. Shared classic CP-delay/training/creative rules apply. The server validates category, learned skill, level, activation, overload lock, interference, player liveness, world and progress-object identity; duplicate start and duplicate same-world-tick callbacks cannot repay strain or double transfer. These authoritative checks replace trusting client-selected mode or abort behavior.

## Energy API adaptations and remaining dependencies

- Native `DeveloperItemEnergy` gets priority and preserves fractional IF, **10,000 IF** portable capacity and **50 IF** bandwidth. Missing data stays empty; charging introduces no refill/debug bypass
- FE capability existence means support even if `canReceive=false` or full. A single real receiver call is limited by its native capacity/bandwidth. FE cannot expose arbitrary setter/bandwidth bypass semantics, so block-mode `ignoreBandwidth=true` remains a request in the deterministic core, while the FE adapter respects the receiver's limit
- Conversion is explicitly **1 IF = 4 FE**, integer FE requests truncate fractional remainders and saturate safely at `Integer.MAX_VALUE`; nonfinite/negative requests cannot transfer. No repeated receive loop bypasses a machine limit
- Block capability lookup uses **Direction.UP**, matching original RF managers regardless of the ray-hit face, and never loads extra chunks
- Original `RFReceiverManager.charge` fed raw `(int)amt` RF despite `RFSupport.CONV_RATE=4`; its energy getter/provider conversions are also inconsistent. The port deliberately uses the explicit 4:1 conversion rather than propagating those legacy unit bugs
- Original IF wireless receiver/node and IC2 EU managers, RF converter machines, network storage, battery items, and node bandwidth setters are not recreated. One NeoForge FE capability replaces overlapping helper-manager iteration; no claim is made of IC2 or complete AcademyCraft machine compatibility
- General original per-skill HOCON multipliers/`CalcEvent.SkillPerform` hooks are not ported by this skill. The current shared resource implementation uses source defaults; the original overload/cost/configuration pipeline remains a separate dependency

## Source-style visuals, not audiovisual parity

`ClassicChargingEffects` owns the real client arc/surround/loop adapter. Block mode uses 20 charging-arc templates, source length 20, five passes, width **0.1**, offset **1.2**, branch factor **0.3**, and hide/show/texture rates **0.8/0.2/0.8**. Endpoints are locally retraced each client tick; entity endpoints add their eye height. There are no continuous client aim packets or visual energy writes.

Block surround is **NORMAL**, six fragments around a 1×1 cube at block-center X/Z and lower Y. Item surround is **THIN**, four fragments following caster dimensions ×1.3. The surprising unconditional aimed-supported-block visibility gate is preserved in **both** modes; item mode otherwise has no visible surround even though its loop plays and energy transfers. Source `EntityPos.tick` overwrites item `updatePos`, so its visible surround remains caster-following. Fragment scale **0.3**, frame/switch rates **0.6/0.7**, 30-tick stochastic fragment aging and source template ranges are retained.

The original `em.charge_loop` asset plays as a caster-following loop at volume **0.3**. Release, stale tokens, death, entity disappearance and level/player/connection replacement stop it. Modern eye/feet coordinates replace obsolete 1.7 local-only height offsets; isolated modern translucent buffers replace GL display lists. Shared `ClassicArcGeometry` uses bounded branch generation and omits legacy per-draw random ribbon twist, so the geometry is explicitly an **approximation until live audiovisual comparison**. Long-lived surround lifetime follows the held context rather than reproducing the original independent 100,000-tick entity expiration. No healthy hold receives an invented short timeout. Client capability availability can differ from server support for unsynchronized third-party providers; only visual surround visibility is affected.

## Verification (at implementation handoff)

Passed without Gradle or launching Minecraft, using isolated `/tmp` javac output:

- **4,085** deterministic resource/mode/order/mastery/floor/gating/creative assertions (`CurrentChargingRegressionTest`)
- **1,224** finite FE conversion/capacity/bandwidth/conservation assertions (`ChargingEnergyRegressionTest`)
- **49** dependency-free visual parameters/flicker/token assertions (`ChargingVisualRegressionTest`)
- New server/core/energy/native-test and client-adapter Java sources compile against cached MC1.21.1/NeoForge APIs. Existing deprecated capability/EventBusSubscriber API warnings remain

**Not run at handoff:** Gradle integration/check, the **11 new native GameTests**, live-client charging, real packet codec/socket transport, audio playback, shaders/screenshots and comparison against classic 1.0.7. The native tests use real world time and the registered request/player/lifecycle handlers, but FakePlayer drops network packets. Test-only, finite FE providers are registered only when GameTest mode is enabled and are excluded from the distributable JAR; they verify block/item capability lookup, not an unported machine. Death/clone/dimension checks exercise registered event adapters rather than claiming full vanilla respawn/dimension travel.

## Sources examined

AcademyCraft: `CurrentCharging.scala`; `CatElectromaster`; `Skill.SingleKeyDelegate`; `AbilityContext`; `CPData`; `ClientRuntime`; `ContextManager`; `EnergyItemHelper`; `EnergyBlockHelper`; `ImagEnergyItem`; `IFItemManager`; `IFReceiverManager`; `IFNodeManager`; `IC2Support/IC2EnergyItemManager`; `EUSinkManager`; `EUSourceManager`; `RFSupport`; `RFReceiverManager`; `RFProviderManager`; `EntityArc`; `EntitySurroundArc`; `ArcPatterns`; `ArcFactory`; `SubArcHandler`; `SubArc`; `CubePointFactory`; `FollowEntitySound`; `ACRenderingHelper`. LambdaLib: `MathUtils.lerpf` and shared ray/helper algorithms. NeoForge: pinned capability/energy/event sources and merged MC API signatures.

## Integrated m05 validation (2026-10-01 UTC)

The full Gradle build/check and all 11 new native Current Charging tests executed and passed as part of the 45-test academy suite. This supersedes the handoff-only compile status above. Native fixtures verify real world ticks and capability providers but use synthetic/embedded players; actual live-client charging, wire codec/handshake, audible sound and original visual parity remain unverified. Logs: runtime-evidence/m05-build.log and runtime-evidence/m05-native-gametest.log.
