# m08 native restart fixture failure and narrow native-light barrier

## Observed failure

Parent executed the actual two-process probe. Its seed failed before any certificate at the combined assertion `Native solar facing/roof/stopped status persists`; the verify phase did not run. Evidence is preserved in `docs/runtime-evidence/m08-energy-disk-restart.log` and the untouched `run-restart-m08-energy-20261001` directory. The old assertion combined four values and did not log each runtime value, so its initial transient sky/status value cannot be recovered directly from that log.

Read-only native Anvil inspection of the retained closed-world `region/r.2.2.mca` confirms:

- Correct FULL chunk64,64, isLightOn=true, exactly17 block entities
- Native solar at(1028,80,1035) has exactly WEST facing
- Native roof at(1028,81,1035) is exactly minecraft:stone
- Final saved skylight at the roof is0, and at the solar is14
- Native solar energy123.625IF and recharge-slot unit456.375IF/sentinel/damage12 remain exact
- Both native developer origin energies and both inventories retain their exact known fractional seeds

Reader and text evidence: `diagnostics/inspect-anvil.py`, `diagnostics/m08-saved-chunk.txt`. It uses read-only Python file access and the native Anvil header/compressed NBT; it does not launch Minecraft or mutate retained state.

## Source-supported diagnosis

The fixture immediately reads status in the same server callback that places its stone roof. Cached official source `LevelChunk.setBlockState` queues `ThreadedLevelLightEngine.checkBlock`. That method submits PRE_UPDATE work through an asynchronous priority mailbox. `BlockAndTintGetter.canSeeSky` meanwhile reads the visible skylight value and reports true for15. `SkyLightSectionStorage` can return15 while a new section/column has not published its blocked-light data. Real `ThreadedLevelLightEngine.runUpdate` processes native propagation and only then executes POST_UPDATE callbacks.

The persisted WEST/stone values eliminate incorrect final facing or roof as causes. The unchanged production source and native tile identity/attachment path support the transient status branch as the failure mechanism. Exact original invocation values were not logged; the rerun's before_sky/after_sky diagnostic will establish whether this race occurred rather than claiming the initial value was observed.

## Integration fix and explicit diagnostics

Apply only `docs/energy-disk-native-light-barrier.patch` at the checkout root with `patch -p1`. It is based on the current integrated helper captured in `integration-base-m08`, with base hash in `m08-light-barrier-base-sha256.json`. The patch dry-runs clean and cached JDK21/NeoForge API compilation passed, recorded in `verification-m08-light-barrier.log`.

The fixture waits for a same-chunk native `ThreadedLevelLightEngine.waitForPendingTasks` POST_UPDATE future after roof placement and after the verifier's native chunk load. It requests the ordinary asynchronous update scheduler while the server's managed event loop waits, with a10-second diagnostic bound. It does not call unsupported synchronous `runLightUpdates`, change time, emulate status, replace native physics, charge a battery, tick a block entity, or create a BLOCK_TICKING ticket. It asserts unchanged world game/day time and the remote chunk's non-ticking status.

All former conjuncts are separate exact assertions: authoritative live tile, exact WEST state, exact stone roof and genuine production STOPPED status. Status failures include status, native sky brightness, canSeeSky, day time and rain; light completion logs include phase and before/after skylight. Every existing full Anvil/state/item/energy/conservation/cold-process check remains intact.

No gameplay production source, Gradle task, server or client was modified/run in this stage. The parent must run the patched probe in another fresh `run-restart*` directory. The failed m08 world must remain preserved and must not be reused or deleted.
