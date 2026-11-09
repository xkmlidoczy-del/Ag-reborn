# Native Current Charging block-manager completion

## Outcome

The staged source IF managers and a narrow `ChargingEnergy` patch close Current Charging's native-block gap. Real wireless nodes, Fusor, and intact Normal/Advanced developer origins and all body-part aliases now accept finite double-valued IF. Existing external FE support remains a single-call, UP-side fallback. Native generators and matrices have no invented charging support.

The frozen `.staging/wireless-energy` slice was not changed. No production edits or Gradle/Minecraft/client launches occurred in this slice.

## Promotion

1. Copy **only** this slice's `src/main/java/` and `src/test/java/` additive files into the corresponding production trees
2. Run `patch --dry-run -p1 < .staging/native-block-charging/docs/native-block-charging-integration.patch` from the repository root, then apply the patch
3. Keep all existing registrations, recipes, resources, tasks and GameTest jar exclusions. There are no new device registrations or assets in this slice
4. Do not replace shared files with the whole `integration/` overlays; those are private cached-API compilation inputs. The patch changes only `ChargingEnergy.java` and adds two `build.gradle` check tasks
5. Parent runs the actual serial aggregate/native tests. The eight new fixtures use `academy_native_block_charging` and `runtime_empty`; their fixture class and provider remain excluded from the distributable jar

`docs/handoff-manifest.json` lists each additive path/hash and the patch. `docs/integration-base-sha256.json` records the exact shared-file bases.

## Source behavior preserved

Canonical provenance and precise line citations are in `../audit/SOURCE-CITATIONS.md`, with SHA-256 hashes of inspected original files.

- `ClassicIFNodeManager`: source node getter/setter; charge min(request, headroom), then min(bandwidth) only when `ignoreBandwidth=false`; pull min(request, stored), with the same optional per-call bandwidth. No network-link, matrix, item-slot, or FE requirement
- `ClassicIFReceiverManager`: source manager getter always zero, setter a no-op; charge/pull directly delegate exact raw double IF regardless of `ignoreBandwidth`. The actual receiver itself still bounds capacity/remaining energy
- The Current Charging caller remains unchanged: block mode asks to ignore bandwidth, disregards the charge remainder, awards EXP based on support even for full stores, then attempts CP consumption. Its final transfer/EXP before failed CP remains intact
- Native IF remains fractional, including values below one FE. FE-only blocks retain the existing floor(4 × IF) conversion, finite acceptance, one receive call, and truthful fractional remainder. Native support takes precedence over a representation of the same store as FE
- Both common/server and client support classification use loaded-only `getChunkNow`. Every machine occupied-cell chunk is checked before intactness/capability lookup. Classifying client effects cannot mutate a store; only server-level targets charge
- Targets resolve the live loaded endpoint again at support/charge time rather than retaining a detached block entity

## Explicit fidelity and safety boundaries

1. Original `EnergyBlockHelper.charge` invokes **every** matching manager with the original unchanged amount, discards manager leftovers, then returns the original input. Its own documented remainder contract is contradicted. This port selects one representation and returns real unaccepted IF. Current Charging ignores the remainder, so valid stock native transfer and EXP behavior are preserved. Ordinary original native node/receiver roles are disjoint. Original RF/EU output converters do overlap a native receiver and external provider/source, but their external charging method is a no-op. The new Normal FE compatibility layer can actually receive into the same native store, so charging both would genuinely duplicate energy; it is intentionally not invoked after native charging. A hypothetical foreign node+receiver hybrid selects node-first deterministically
2. Original developer slaves are **technically chargeable** through IFReceiverManager. Each holds its own persisted receiver field; ordinary developer use redirects to the origin, so those slave-local stores do not power ordinary development. This slice preserves the existing modern body-part alias by resolving any intact clicked cell to the canonical native origin. It never fills the original separate slave-local field or performs an additional FE transfer. This is the existing playable cleanup, not exact original slave-local storage parity. Removed/incomplete structures remain unsupported
3. Native managers reject nonpositive/nonfinite charge/pull requests before mutation. Existing native tiles already sanitize persisted energy and reject invalid receiver requests. Original managers do not guard such inputs and can subtract, overfill, or poison stores with NaN. Faithful arithmetic applies to valid positive finite IF and source-defined finite stores
4. Receiver getter/setter manager quirks remain preserved; no receiver refill setter is added. Source generator interfaces, matrix interfaces, and generic wireless markers alone do not imply native charging support

## Verification completed here

`../scripts/verify-native-block-charging.sh` used only cached JDK21, merged NeoForge21.1.252 API/dependencies, current production classes and the frozen wireless/fusion compile overlays. It does not invoke Gradle or launch Minecraft.

- 110,044 deterministic native source-manager/conservation assertions passed, including 5,000 exact binary-fraction samples in each bypass mode for native charge/pull, capacities, bandwidths, truthful leftovers, one-manager hybrid dispatch, invalid-input protection, receiver zero-get/no-op-set quirks
- Existing 1,224 finite FE adapter assertions passed unchanged
- Existing 4,085 Current Charging ordering/mastery/resource/EXP regressions passed unchanged
- Nine native adapter/common types cold-linked while denying actual client namespaces; existing 15-type energy integration cold-link check also passed
- **115,353 total deterministic assertions passed**
- All eight new native GameTests compiled successfully. They were **not executed here**; actual parent runtime is still required
- Integration patch dry-run succeeded against its recorded current production bases

The only compilation warnings are the already-used `EventBusSubscriber.bus` deprecation annotations in the test-only provider. `verification.log` is the raw result.

## Native fixture coverage and provenance

`AcademyNativeBlockChargingRuntimeTests` covers:

1. All three actual node tiers: fractional IF, native per-call bandwidth and bypass, finite headroom, full support
2. Actual Fusor native receiver: both flags bypass receiver bandwidth, raw fractions and finite leftover
3. Normal and Advanced, four facings, all seven body cells: every part aliases one intact live origin with exact sub-FE IF, no slave-local storage or duplicate FE transfer, finite capacity; captured removed and disconnected targets remain unsupported
4. Solar, working matrix/all parts, air/null and absent chunk: unsupported, no mutation or force-loading
5. Test-only EMERALD_BLOCK FE provider: only UP requested, one receive call, 4FE/IF flooring, bandwidth/full/sub-FE behavior
6. Real Normal native-plus-FE capability: exact single native delta via origin and body part, with unchanged FE per-tick receive quota
7. Real `CurrentChargingSession` with actual native block target: bypass=true, captured mastery, final native transfer/EXP before failed CP and ended replay
8. Full node/Fusor/developer targets remain good for supported EXP despite zero effective transfer

These are explicitly supplied-block/energy/learned-skill QA fixtures. They establish the block-manager adapter and caller-order behavior; they are not proof of naturally acquiring Current Charging or a human survival playthrough. The separate parent-owned wireless progression fixture remains the solar → node → Fusor → crafted Normal → earned category/Arc route.

## Remaining limitations

- Actual serial GameTest/runtime execution and integrated client good-target visual behavior are parent-owned and pending for this slice
- No additional source generators, converters, metal former, frequency transmitter, terminal app, tutorial/achievement, or unrelated skills are included
- Existing common-menu extension exemption in cold server tests remains exact; actual client classes are rejected
