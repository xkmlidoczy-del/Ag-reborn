# Native energy two-process disk-restart extension

## Ready for parent integration

Apply `docs/energy-disk-probe-integration.patch` from this stage with `patch -p1` at the checkout root. It narrowly changes the existing opt-in restart test and its orchestrator and adds one package-private helper in the already JAR-excluded `cn/academy/port/gametest/` tree. No gameplay source or Gradle configuration changes are required. Do not replace unrelated production files with entire staging directories.

The patch base hashes are in `integration-base-sha256.json`. This stage does not modify shared production and does not launch Gradle, Minecraft, a server or a client. The staged `scripts/verify-disk-restart.sh` is an integration overlay: use it at its production destination after integration, not from inside this stage.

The safe isolated check is:

```
.staging/energy-disk-probe/scripts/verify-cached-api.sh
```

It uses cached JDK21, Minecraft1.21.1/NeoForge21.1.252 merged APIs and the already built production classes, compiles both staged probe classes, and checks the orchestrator's Bash syntax. Compilation and patch dry-run passed; the actual seed/verify JVM phases remain parent-owned and have not run in this stage.

## Actual disk/new-JVM contract

The existing probe remains exactly one required `academy_restart` GameTest in each of two separately completed Minecraft JVMs. Its original fixed native player UUID, pending-coin escrow/refund, full ability/preset progress, portable4321.25IF, observed native SaveToFile/LoadFromFile, distinct PID/boot UUID and five bounded post-reload gameplay ticks remain checked. The player inventory gains one real energy unit in slot2 at5432.125IF, with complete custom-data sentinel and gauge-damage equality against an independent known fixture. Native inventory count expectations become three rather than two.

The native world fixture occupies only remote chunk64,64, in the same canonical isolated `academy-restart-world`. It refuses a previously existing `region/r.2.2.mca`; the orchestrator still allocates a fresh `run-restart*` directory, refuses existing paths, preserves all saves/logs, and never deletes state. The remote origin is checked to be at least256 blocks away from the randomized GameTest structure, so a new JVM's template placement cannot overwrite the fixture.

The seed invokes actual native BlockItem placement for both eight-cell developers, consuming one item for each in declared SURVIVAL mode. It creates the real solar block entity, inventory and opaque roof as declared fixture state. These are explicit test seeds and do not represent survival-earned items or power.

Independent fixed expectations are:

| Object | Native position/facing | Exact finite IF and native inventory |
| --- | --- | --- |
| Normal developer | (1028,80,1028), NORTH | origin12345.25IF; slot0 unit765.625IF + unique sentinel; slot1 three diamonds |
| Advanced developer | (1035,80,1028), EAST | origin123456.875IF; slot0 unit9876.375IF + unique sentinel; slot1 portable2468.5IF + unique sentinel |
| All fourteen developer subparts | exact source cardinal part offsets | zero independent stored energy, empty two-slot inventories, redirect to their real origin |
| Solar generator | (1028,80,1035), WEST | buffer123.625IF; native slot0 unit456.375IF + unique sentinel; stone roof at(1028,81,1035) |

A genuine server-authenticated advanced-machine occupancy/token is also seeded with the admitted mock profile. Normal source inventories and finite origin energy persist. Occupancy/nonce are deliberately transient; actual logout/unload/stopped cleanup removes the seeded session, the saved tile tags contain no user/nonce, and the second JVM confirms all loaded users are null. No in-progress development is fabricated or resumed.

The remote chunk is loaded through native ServerLevel.getChunk, which adds a FULL ticket without a BLOCK_TICKING ticket. Every live check asserts that it remains naturally non-ticking. No forced chunks are added and no player is left there. This separates exact partial-buffer persistence from uncontrolled natural startup charging. The roof independently makes the solar status STOPPED.

The test observes native ChunkDataEvent.Save. Only after ServerStoppedEvent, following native world close and storage flush, does it open the existing Anvil region read-only using RandomAccessFile mode `r`. A bounded reader follows the real region sector table, requires inline native zlib compression, and parses native NBT with a4MiB NBT account limit. It does not invoke RegionFile's create/write route or write a fixture chunk. It requires:

- Correct FULL chunk identity
- Exactly17 independently known block entities, matching full ID/position/finite energy/Items components, with native keepPacked=false metadata
- Exact persisted developer PART0..7 and NORTH/EAST facing blockstates, decoded from the actual palette/packed longs
- Exact solar WEST blockstate and stone roof

The seed certificate is written only after those native closed-world bytes pass, the native chunk-save event was observed, and seeded transient session cleanup is established. Existing lifecycle flags and progress schema are preserved; additive energy-world fields carry their own schema1 and independently known expected-world values.

Before any verifier world mutation, the new JVM validates the certificate against the fixed known fixture and independently checks the actual Anvil bytes again. It then invokes native ServerLevel.getChunk, requires exactly one native ChunkDataEvent.Load for that chunk, checks the actual serializer event data against the known fixture, and checks all native loaded states/entities/inventories/capabilities. It never calls BlockEntity.loadWithComponents, sets a block, or reconstructs a fixture tile in the verify phase.

Every one of the16 developer cells must expose the actual origin IF receiver and receive-only finite FE adapter. Exact source capacities, floor-valued FE representation and non-mutating1FE simulation are checked; repeated simulation cannot change fractional IF.

After initial native cold-load checks, exactly one explicit production ClassicSolarBlockEntity.serverTick runs on the real reloaded tile. The unit's source20IF bandwidth makes its energy476.375IF and leaves buffer103.625IF. The independent full expected metadata/items and those finite fractions are checked across all five subsequent real world ticks. This explicit stopped-status recharge tests conservation of saved power; it does not claim automatic ticking in an unloaded/non-ticking chunk or fresh solar generation.

The verified certificate records exactly one native chunk-load event, one declared recharge tick, energy-world success and the independently known post-recharge state. The orchestrator requires both energy/world log markers and the actual saved native region file, as well as all earlier player/lifecycle evidence.

## Source fidelity and scope

Developer inventory/energy persistence and intentionally transient users/processes follow `docs/DEVELOPER-MACHINES.md`. Native energy-unit component normalization, finite sanitization and source damage gauge are preserved without any gameplay edits. Solar buffer persistence intentionally fixes the source TileGeneratorBase omission, as already documented in `docs/SOLAR-POWER.md`; this probe does not falsely call that exact legacy save behavior. No new power source, neighboring-machine delivery or wireless-network behavior is introduced.

Cached official source review used the repository's `build/moddev/artifacts/neoforge-21.1.252-sources.jar`: MinecraftServer.stopServer/runServer, ServerChunkCache.getChunkFutureMainThread/save, LevelChunk.isTicking/getBlockEntityNbtForSaving, ChunkSerializer.read/write, ContainerHelper, BlockEntity, NbtIo and NeoForge ChunkDataEvent. These establish real shutdown/flush, native serializer/event paths, the FULL-versus-BLOCK_TICKING distinction, component/default serialization, and the Anvil format used by the read-only probe.

This is graceful-stop disk durability with declared mock/test seeds. It is not hard-crash/power-loss durability, real socket/handshake/transport, client rendering, a human survival playthrough or physical GUI interaction. Both actual JVM phases must complete successfully before native persistence is claimed. The integrated actual runs are recorded below.


## Actual Fusor cold-process extension,2026-10-01

The latest bounded native run used distinct seedPID215 and verifierPID503. Both actual JVM phases passed. Evidence: runtime-evidence/m10-fusion-disk-validation.log. Earlier ability/preset/coin/unit/developer/solar checks remain present and passed in the same processes.

A declared persistence seed adds an EAST-facing Fusor in the same naturally non-ticking remote chunk. Two low crystals, three empty matter units, native energy-unit component sentinel and3500mBsource phase liquid are explicit persistence fixtures, not obtained survival inputs. Twenty real production tile ticks execute exactly eleven work ticks; the final known seed is1999.625IFbuffer and324.375IFunit, with unfinished source work. Native graceful-stop serialization closes the world, and independent read-only Anvil validation checks all eighteen tiles, exact five-slot item components, tank, blockstate and facing.

Before verifier mutation, the separate process checks the closed native Anvil bytes, observes the actual ChunkDataEvent.Load, and verifies full loaded tile data/capabilities. Native loading resets unfinished recipe/progress while the saved working texture blockstate initially remains true, matching the source transient work contract. The verifier does not reconstruct tiles or invoke loadWithComponents.

Exactly130explicit production ticks then perform a whole new121-work-tick fusion job. Its1452IFdebit and3000mBuse leave872IFbuffer, an empty conserved unit,500mBphase, one remaining low crystal and one new normal crystal. Had the prior eleven work ticks resumed, this exact fresh-job expectation would fail. The independently expected full state is checked through five later real world ticks, while the remote chunk remains naturally non-ticking. This is graceful-stop native durability, not hard-crash durability, GUI proof or a natural resource acquisition claim.
