# Basic Mine Ray negative-coordinate sentinel adaptation

## Scope and source

AcademyCraft 1.0.7 `MRContext` initializes x/y/z to (-1,-1,-1), uses coordinate equality to choose acquisition versus progress, and resets those literal coordinates on miss, refusal and break. In the original 1.7.10 block world, y=-1 was outside the valid 0..255 range. Modern Minecraft permits negative Y, so real (-1,-1,-1) cannot also encode capture state.

The only gameplay change separates the private capture boolean from the unchanged coordinate representation. The acquisition branch is entered if no target has been captured or the coordinate changes. Miss, denial/harvest refusal and break clear capture. Existing target accessor and particle packets still use the literal original sentinel. No new public API, cost, formula, cooldown, timing or adapter change is introduced.

Pinned source: AcademyCraft tag 1.0.7, expected upstream commit 00d19ec0cf538f61c1095c9292f5ee6863db4521. `MineRaysBase.scala` SHA256 `39d791417af63ffeaaec1f57e4fbb79cc613474ba0353ffa277bb703a70922eb`; `MineRayBasic.scala` SHA256 `6c45d0b528ce98447840c66a33ee6c44f7b9b82175802b8a770be3182e82b130`. The test's source-order oracle is packaged Java; no runtime `.reference` or `.staging` dependency is needed. Adapted portions retain Copyright Lambda Innovation 2013-2016, GPLv3 and all additional upstream notices in LICENSE/NOTICE.

## Preserved original behavior

- Acquisition pays that tick's CP, then checks permission, harvest and captures hardness without subtracting speed, breaking or emitting particles
- Harvest metadata argument remains the previous target coordinate, including the source's old-coordinate quirk
- Progress does not reacquire or reread hardness/harvest each tick
- A failed CP consumption still executes the terminating tick's acquisition/progress work
- Breaking grants source EXP, clears capture/coordinates and emits particles at literal (-1,-1,-1)
- Acquired hardness is retained if the block at the captured cell is replaced by bedrock; the source can break that replacement after the captured hardness expires
- The unchanged world adapter still checks modern native protection immediately before the final break attempt; source EXP still follows the attempt even if that final adapter denies it

## Regression evidence

The independently compiled exact before baseline fails both focused negative-coordinate permission and acquisition witnesses. The corrected pure suite passes 120207 checks, including 40040 ordinary-coordinate steps against the literal source-order oracle. The existing Meltdowner/Basic beam suite passes 8903446 checks against the corrected session.

`AcademyBasicSentinelRuntimeTests` has one dedicated native test in its own `academy_basic_sentinel` batch. It is compiled against cached official Java 21/Minecraft 1.21.1/NeoForge 21.1.252 signatures. Native execution remains pending; no server, client, Gradle task or world process was run in this verification lane.

## Dedicated native fixture placement and cleanup

The test installs a 16x8x16 empty template, then uses public native GameTestInfo.setNorthWestCorner(-8,-5,-8) and prepareTestStructure() to relocate its own disposable fixture. It checks the planned footprint before native preparation, then asserts actual declared bounds equal [-8,-5,-8]..[8,3,8] and contain the real target before explicit target mutations. The player feet, eye and complete 10-block ray also remain within that declared footprint. Native clipping must actually hit (-1,-1,-1), rather than a synthetic World callback.

Native GameTest preparation clears a standard padding region and encases/force-loads the relocated structure just as ordinary GameTest preparation does. The initial automatically placed empty harness is not explicitly restored or deleted; standard relocated preparation may partially clear it if the two preparation regions overlap. Any remaining original harness stays in the disposable test world. This test must only execute in a fresh disposable GameTest directory, within its dedicated batch. Do not use a player's save. The normal native preparation padding is distinct from the test's explicitly scoped target writes, all of which stay inside the new declared footprint.

Player and event hook are listener-owned and removed on success, failure/timeout or rerun. Later scheduled callbacks check a closed flag. Dropped entities are handled by native GameTest success cleanup. The fixture does not globally clear other players' holds or hooks.

Eight real ticks check repeated initial permission denial, first permitted acquisition without subtraction, one subsequent .4 decrement leaving .5 dirt intact, final native BreakEvent cancellation, same-coordinate reacquisition, and retained .5 captured hardness breaking a later bedrock replacement. Both native protection cycles and source EXP/CP/cooldown order are asserted.
