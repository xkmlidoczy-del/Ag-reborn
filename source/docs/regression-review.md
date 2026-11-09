# Regression and authority review

Reviewed 2026-09-30 UTC against the authorized cloud checkout, canonical AcademyCraft **1.0.7**, LambdaLib **1.2.3**, and the resolved Minecraft **1.21.1 / NeoForge 21.1.252** source artifacts. This is a source/standalone-JVM review, not a running-world or visual-fidelity certification. No Minecraft client, dedicated server, or GameTest server was started. No EULA acceptance, user-computer access, remote mutation, or publication occurred.

## Verification performed

- Added `cn.academy.port.CorruptedSaveRegressionTest`: **711 passing assertions**, through the real `AbilityStorage.encode/decode` methods and NBT classes
- Focused verification used a temporary init-script `JavaExec` for the regression main: exit 0, `BUILD SUCCESSFUL`, 2026-09-30 UTC. The same runner is now permanently wired into `check` as `corruptionTest`; reproduce with `scripts/gradle-cloud.sh corruptionTest --console=plain`. Neither route selects a Minecraft run
- Corruption cases cover NaN, positive/negative infinity, extreme/negative values, CP/overload/training/level EXP, level/recovery/cooldown bounds, mastery, wrong NBT field types, invalid categories, foreign-category/unknown skill keys, orphan cooldowns, missing schema, and sanitized round-trip stability
- Static model/override and namespaced sound-file scan: **61 explicit references checked; zero missing**
- No recipe JSON exists in the port's data resources. Survival acquisition/crafting therefore remains unfinished, consistent with README/MIGRATION-CHECKLIST; creative contents do not constitute survival progression
- Existing native-world tests are compiled-only pending EULA authorization. See `runtime-test-report.md` and `VALIDATION.md` for their actual execution status and aggregate results; this review does not convert those tests into passes

## Concrete regressions found and corrected by integration

The following fixes were re-read in the current implementation. They remain subject to real-world/client verification.

1. **Invalid-player C2S/Arc execution.** The legacy-style immediate Arc path had no dead/removed/spectator guard. `AcademyGameplay.request`, `arc`, private Railgun firing and charge tick now reject those states
2. **Iron Railgun charge crossing a world or player state.** Charge storage was UUID-to-count only. The `RailgunCharge` record now pins `ServerLevel` and `AbilityProgress` identity, validates them before every decrement/fire, and the dimension event cancels transient holds/casts. Ordinary replay cannot reset an already-present charge
3. **Development continuing in spectator mode.** `DevelopmentController.start` now rejects invalid players/off-thread starts; `tick` aborts an active process before pulling energy or completing an action when the player is dead, removed or spectral. The dirty FAILED snapshot is still delivered by the existing process tick
4. **Canceled-death ordering.** Port cleanup formerly ran at default NORMAL priority, before a later death-cancel handler could rescue a player. `AcademyGameplay.death` now uses LOWEST, matching canonical `CPData.Events.playerDeath` (`CPData.java:573–581`); default canceled-event exclusion is retained. As with all event systems, equally low-priority listener ordering still needs integration coverage
5. **Same-dimension client respawn retaining stale developer/input state.** Minecraft's `ClientPacketListener.handleRespawn` replaces `LocalPlayer` while keeping `ClientLevel` when the dimension is unchanged (`1102–1152` in the resolved source). The new `ClientSessionGuard` tracks both identities before tick and packet application, clears local ability/developer/input state and coin visuals, and no state packet overwrites a level-only reset sentinel. Login/respawn also sends authoritative development snapshots
6. **Another player's coin affecting local G input.** A nearby remote QTE toss could set the local `charging` latch. The packet switch now requires the toss entity ID to equal the current local player's ID before modifying that latch; nearby coin rendering remains independent
7. **Saving stale cached state after an automatic shot.** Arc and Railgun commits now copy ability state into persistent NBT immediately. `ServerStoppingEvent` returns outstanding coin escrow and then flushes every connected player's cached ability state before the server's final player save. The updated source was re-read after integration; disk/restart behavior remains an unexecuted native check

## Positive authority and interruption boundaries

- NeoForge's registrar defaults to `HandlerThread.MAIN`; `MainThreadPayloadHandler` enqueues gameplay handlers on the main thread. C2S `Request` strings are bounded to 32/64 characters and contain no client-supplied costs, damage, mastery, target, position or hold duration
- Hold/release skills remove their active context before consumption/damage. Duplicate start requests do not reset Directed Shock's server clock or overwrite Threatening Teleport's mastery snapshot. Repeated releases of the same terminated context cannot spend again
- Directed Shock requires strictly **7–49** server-world ticks, rejects an invalid/world-swapped/state-swapped hold, and eventually aborts a 200-tick hold. Threatening Teleport checks player identity, dimension, captured state and a nonempty current main hand
- Coin judgement uses a server-issued token. An old token cannot judge a later toss, one early attempt is spent, and accepted late judgement consumes the escrow before Railgun CP checks. Coin refunds remove the transient entry/persistent escrow first, preventing repeated cleanup from returning the same coin twice. Orphan escrow recovery accepts only this mod's coin and clamps its returned count to one
- Coin duplicate ticks in one game tick do not accelerate physics. Electron Bomb removes ready casts before invoking damage hooks, avoiding reentrant/double-fire callbacks; dimension/time reversal removes stale casts. Ordinary key-up/deactivation intentionally does not revoke a previously accepted delayed launch
- Death/clone/logout/server-stop paths clear transient processes/casts/holds, while ability progression and finite item energy are persisted. Original `DevelopData` never opted into NBT storage, so active development is intentionally discarded rather than restored on login
- Developer energy is stack-owned, defaults to zero, clamps corrupt/nonfinite values to zero, preserves unrelated custom data, and exposes finite FE conversion. The controller re-reads the current main hand for every energy pull, preserving canonical portable behavior
- Development repeats/override, partial drain, completion-time prerequisite validation, dirty-before-consume sync, six-tick periodic sync, and next-tick terminal sync match canonical `DevelopData.java:57–66,121–169`. Closing the GUI does not implicitly abort; an explicit Abort does

## Save-boundary rationale and remaining disk check

Before the fix, automatic iron Railgun firing changed cached CP/overload/mastery/cooldown after the request had already been saved. Periodic `tickCount % 10` storage left a short interval in which `Entity.saveWithoutId` could see an older encoded `persistentData` value beside current inventory/world changes. Resolved `MinecraftServer.stopServer` saves players before calling `removeAll`; logout may flush later, but channel-close timing must not be the correctness requirement.

The integrated fix saves at Arc/Railgun completion and flushes all connected players' current cached ability state during `ServerStoppingEvent`, before the final player save. Canonical `CPData.toNBT` serializes its live fields directly (`CPData.java:518–525`), without this separate cache-copy window. A graceful-stop/immediate-save regression still needs authorized native execution to establish disk behavior; this review did not execute it.

## Remaining verification boundaries

- Standalone corruption tests establish finite/bounded codec output; they do not validate full player/item registry serialization, world disk persistence, clean restart, or real multiplayer login
- Schema presence currently selects the decoder; there is no migration contract for a future schema. Numeric sanitization is not proof that an arbitrary edited save represents legally acquired skills. In particular, canonical critical passives may be auto-learned at a lower level, so blanket deletion of above-level learned entries would break source parity
- C2S hold actions have no per-hold nonce. The reviewed current-context checks prevent repeated execution of one context; cross-session delayed/duplicated release or abort behavior is not covered by a real transport test. Only coin attempts have an explicit replay token
- Client render helpers capture the current `ClientLevel` and reject deferred callbacks if that level changes. Payloads themselves contain no sender world/session epoch, so this is a callback guard rather than proof against every packet already queued across a disconnect/reconnect/respawn boundary
- Same-dimension respawn effect teardown, input held during dimension travel, terminal development sync after canceled death, spectator transitions, coins with full inventory, malformed serialized item escrow and immediate save after automatic fire need actual native/client coverage
- Source quirks retained include ammunition-before-CP, the development TPS+1 cost/nominal estimate mismatch, pre-consume/next-tick-terminal sync, the legacy entity-feet/block distance comparison, and the original radiation marker's caster-tick duration lookup. No source quirk is silently promoted into evidence of intended gameplay
- Damage/config hooks, canceled attacks, PvP/team protection and reflection are reviewed source paths, not executed interoperability tests with other mods
- Registered item models/overrides and sounds resolve statically. Port-specific literal UI keys are present in English and Simplified Chinese; Japanese and Traditional Chinese now include the three new ability keybinding labels and developer-screen title. There are no implemented normal/advanced developers, complete charging networks, recipes/ores, all skill behaviors, guide or achievements. A 50-entry catalog is not 50 executable skills

## Next meaningful checks after runtime authorization

1. Run every required native GameTest and verify the nonzero expected test count, not just Gradle exit status
2. Exercise charge/hold/development interruption across death cancellation, same-dimension respawn, dimension travel, logout and spectator transitions
3. Save immediately after an automatic Railgun shot and stop/restart with active coin/development contexts; compare disk state, escrow/ammo, costs and cooldowns
4. Connect a real client and repeat key-down/up, GUI close/Abort, stale/replayed request, remote coin, reconnect and packet-order scenarios
5. Capture representative 1.7.10/1.21.1 audiovisual scenes only after client rendering actually runs, retaining the documented adaptation limits

## Source input and later class-link checks

The current client uses the classic separate physical/logical key-state ordering, so GUI/cooldown/activation abort does not restart an already held key. Activation while a delegate is active aborts that delegate rather than deactivating the entire ability, as in the original activation-handler stack. Session replacement explicitly blocks already held keys as a modern lifecycle safety adaptation. Pure transition assertions and client-session identity tests pass; real GLFW/keybinding/packet cadence remains unexecuted.

The common classes also cold-link under a fresh loader that denies all Minecraft/NeoForge/client-mod namespaces, resolving constructor/method/field signatures without initializing or starting Minecraft.58common classes pass this check. This is a class-link separation result, not dedicated-server startup or FML registration verification.
