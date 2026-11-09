# Classic RF/IF bridges on NeoForge FE

This increment implements the two actual AcademyCraft1.0.7 RF bridge blocks against Minecraft1.21.1 / NeoForge21.1.252. The original AcademyCraft commit is `00d19ec0cf538f61c1095c9292f5ee6863db4521`, with LambdaLib1.2.3. The deliverable was prepared in the isolated `energy-bridges` stage. Production integration and actual game/client acceptance remain the owner's work.

## Source contract and modern endpoints

The primary source is `RFSupport`, `BlockRFInput/Output`, `TileRFInput/Output`, `RFProviderManager`, `RFReceiverManager`, `EnergyBlockHelper`, `BlockConverterBase`, `TileGeneratorBase`, `TileReceiverBase`, and the unchanged standalone `WirelessPage`/`page_wireless.xml`. Packaged primary witnesses, original texture bytes, original AcademyCraft README/notices/GPL3, LambdaLib MIT, and cached official NeoForge API source/LGPL2.1 are in `src/test/resources/classic-energy-bridges-source`. Tests never require `.reference` or `.staging` in the assembled source archive.

| Device | Actual source behavior | Native binding |
| --- | --- | --- |
| `academy:rf_input` | Zero-inventory wireless **generator**; receives external RF; generates exactly zero by itself | `ClassicRFInputBlockEntity`; actual generator capability plus receive-only NeoForge FE capability |
| `academy:rf_output` | Zero-inventory wireless **receiver**; exports RF; six-neighbor push after receiver-base update | `ClassicRFOutputBlockEntity`; actual receiver capability plus extract-only NeoForge FE capability |
| Both | 2000 IF buffer, 100 IF wireless bandwidth, 1 IF = 4 RF, all source faces connect, initially empty | One finite double-valued IF store and one integer FE view, 8000 FE capacity; 1 IF = 4 FE is the explicit modern RF transport mapping |

NeoForge's official `Capabilities.EnergyStorage.BLOCK` and `IEnergyStorage` are used directly. No CoFH/IC2/other mod was installed. FE is a host API adaptation rather than proof that every historical RF mod has a compatible modern counterpart. No second FE battery, duplicate native store, spontaneous source of power, fuel conversion, recipe-power refund or external acquisition path was invented.

The source input truncates its external RF request to **whole IF** before adding it. That quantization is retained. Its corrected FE response is exactly four times the IF actually credited, with less than one FE of spare IF headroom retained rather than misreported. For example input3 FE accepts0, input7 FE accepts4, and input400 FE accepts100 IF. Output extraction and output pushes can transfer quarter IF; sub-FE remainders stay in the finite store.

The **100 IF bandwidth belongs to the wireless graph**. Original direct RF receive/extract and six-neighbor pushes have no additional100 IF rate cap. The current genuine IF graph enforces its existing generator/receiver/node budgets, ordering, passwords, range, combined capacity, unloaded endpoint suspension, stale role pruning and conservation. The increment reuses that graph and its sender-bound wireless protocol. Tick timing remains20-tick energy publication; output publication captures the buffer **before** that tick's six source-order neighbor pushes. Neighbor order is DOWN, UP, NORTH, SOUTH, WEST, EAST; the capability is queried on the opposite face.

## Explicit arithmetic and lifecycle corrections

The independently compiled unchanged original classes demonstrate each defect rather than assuming the source comments describe its implementation:

- Original input reports3 RF accepted for a3 RF request but credits0 IF; a non-integral headroom can also misreport a remainder. The modern reply matches the actual credit
- Original output truncates buffered IF before calculating its returned RF but debits the request. For example0.75 IF can be lost while extraction returns0 RF. Modern extraction debits only the returned integer FE
- Original provider manager multiplies stored RF by4, and divides requested IF by4 before extraction, then multiplies the return by4. A16 IF request can remove4 RF, which is1 IF, while reporting16 IF. Modern manager arithmetic consistently divides stored/removed FE by4 and multiplies requested IF by4
- Original receiver manager sends `(int) amount` directly as RF and subtracts accepted RF from IF. Modern charge submits floor(4×IF) FE and returns actual unaccepted IF
- Original aggregate helper invokes overlapping handlers and returns the entire original request. The existing native helper's single-selected-representation correction is retained and extended to foreign FE. Native node/receiver IF takes precedence, including native fractional IF; a foreign hybrid never gets both native and FE transfers
- Original generator base omits buffered energy from inherited NBT. Both actual modern bridge stores persist finite fractional IF explicitly. Output source NBT already persisted energy

Simulation never changes the store. Negative, non-finite, over-capacity and malformed saved values cannot create power. Output push reserves its requested IF and fences reentrant spending/crediting during the external callback. Reported foreign transfers are bounded to the request. A foreign endpoint that violates the FE contract by mutating itself differently from its reported acceptance cannot be made globally conservative by a caller; this stage does not claim to repair arbitrary broken external mods.

Actual mutations require the owning server thread, a loaded chunk, the current block state and the identical current block entity. Retained native/FE endpoints stop working after removal, replacement, unload or client-side use. Foreign block managers resolve the actual current capability on each operation, use the source UP face, and never retain foreign capabilities or force-load chunks. The standalone native menu also binds the exact bridge entity, so a replacement at the same coordinates cannot reuse an old menu to change graph state. These are explicit host authority/lifecycle protections.

## Acquisition, recipes and display

RFSupport's two fields and four Java recipes are unconditional in the original bundled RF API; no external-RF-mod presence condition is invented. The four native shaped recipes are outside the original49 `default.recipe` declarations:

| Native recipe | Shape | Ingredients/result |
| --- | --- | --- |
| `classic/rf_input` | `abc` / ` d ` | energy unit, machine frame, constraint plate; energy converter below the frame; one input bridge |
| `classic/rf_output` | `abc` / ` d ` | energy unit, machine frame, resonance crystal; energy converter below the frame; one output bridge |
| `classic/rf_input_conversion` | `X` | one output bridge to one input bridge |
| `classic/rf_output_conversion` | `X` | one input bridge to one output bridge |

**Energy-unit ingredient metadata is an explicit host adaptation.** The original direct GameRegistry Item ingredient becomes metadata0 under old ordinary shaped crafting. Offline inspection of the already-cached official Mojang1.7.10 client jar, SHA1 `e80d9b3bf5085002218d4be59e668bac718abbc6`, verified the default constructor and shaped metadata comparison. Source freshly crafted empty units were also damage0 until an IF manager wrote the gauge; the existing modern unit deliberately starts genuinely empty with DAMAGE13. Modern RF recipes use the registered non-simple `academy:bridge_energy_unit` ingredient. It accepts the genuine native zero-IF unit and source-equivalent gauge0 charged states, and rejects intermediate nonzero-energy/nonzero-gauge states, preserving the already-established empty-unit acquisition contract without broadening every charge state. Its stateless codec and real registered stream codec use the official NeoForge custom-ingredient API. The existing energy-unit lifecycle is unchanged. The exact source grids, four materials, counts and consumption remain unchanged; the only deliberate recipe-state extension is the genuine native empty unit. Consumed stored unit IF and placed bridge IF are not refunded or preserved by ordinary source block recrafting.

These recipes create empty functional converters. They do not establish natural acquisition of external FE, ore chains, charged units or a completed survival playthrough. The existing guide's `energy_bridge` bindings now refer to real items/recipes and can unlock via actual pickup/crafting events.

Source blocks are ordinary full stone cubes, hardness2.5, pickaxe harvest level0. Native pickaxe tags allow the corresponding wood-tier tool; no stone/iron/diamond requirement is added. Both cube PNGs are copied byte-for-byte, all faces use the original uniform texture, loot returns one ordinary source block and stored power is not a drop. The original converter item is an ordinary ItemBlock with a localized RF→IF or IF→RF tooltip, not a dedicated flat-icon renderer. This stage does not invent a flat item sprite; native item models inherit the source cube. Original EN/ZH-CN/ZH-TW/JA block names and description template are preserved, including RF wording.

Non-sneaking use opens only the source centered176×187 wireless page. There are no invented inventory slots, player inventory, machine histogram, tabs or energy-management screen. Original page art, six-face source cube art, source font adapter, inline password editing, seven fully visible list rows, linked/unlinked icons, connection/unlink and arrow scrolling use the actual current menu/protocol. `ClassicEnergyBridgeLayout` derives the original fractional panel/connected-row/arrow positions from `page_wireless.xml`; native EditBox hit/IME coordinates are integer host controls. Escape closes the page without pausing gameplay; menus receive the current20-tick source snapshot and existing nonce/reach/loaded-role protections. Actual resizing/reopen/focus/physical clicking/resource-reload/font pixels remain pending.

## Optional IC2 boundary

Original `IC2Support.init` is conditional on `@Optional.Method(modid="IC2")`. It declares `eu_input/eu_output`, a1 IF=1 EU rate, four separate recipes, actual insulated Copper Cable and BatBox dependencies, IC2 source/sink energy-net lifecycle and IC2 electric-item manager integration. This stage packages its exact unchanged source as a witness but registers no EU blocks, recipes, dummy cables/BatBoxes or synthetic EU endpoint. A real modern IC2 target and its compatible source/sink/item/net API are required before choosing an implementation. This optional pair is not standalone survival progression and not covered by the NeoForge FE mapping.

## Verification and acceptance boundary

The isolated verifier assembles the latest owner source plus exact additive/shared deltas in a temporary root with no `.reference`/`.staging`, compiles the full main/client/native fixture set against cached official NeoForge21.1.252 APIs, and runs finite portable Java checks. It does not start Gradle, a native game/server/client, UI/CUA, audio, publication, paid service or the user computer.

Portable suites compile the unchanged original RF/base/helper classes in a separately isolated old API, compare valid receive/extract/simulation cases, witness all documented defects and original Java recipes, exercise100,000 random finite exchanges, actual unchanged IF graph budgets/passwords/unload/removal, foreign partial/invalid/reentrant callbacks, common linkage with client namespaces denied, source resources/PNG equality/layout and current helper/graph regressions. Old boundary stubs do not pretend to run Minecraft or provide gameplay power.

Eight ordinary native fixtures and one opt-in separate-JVM Anvil fixture are compiled only. Ordinary cases cover real registries/all-face caps/simulation, actual opposite-face foreign Chest FE transport, generic UP managers/native precedence, source zero-slot page/password/nonce/graph/stale replacement, registry NBT/publication/zero generation, actual ResultSlot four-recipe consumption/guide unlock, and publication-before-push/broken retained endpoints. The test-only foreign capability is enabled only in GameTest mode and remains excluded by the existing release-JAR gametest exclusion.

The distinct cold namespace is `academy_rf_restart`; use `academy.energybridges.restart=seed` then `verify` in a genuinely separate JVM, with the dedicated `academy-energy-bridges-restart-world`. Its declared remote non-ticking finite input123.625 IF/output456.375 IF probes inspect actual Anvil before loading, then real cold registry/FE/IF bindings. It refuses an existing seed region/proof or wrong world. This is a prepared fixture, not an executed cold proof or natural-power claim.

Owner acceptance must still establish successful aggregate Gradle checks, actual native ordinary/cold tests, physical source crafting/placement/mining/tooltip/cube appearance, input from a genuinely available FE provider, output to a real compatible FE consumer, wireless link/relink/unlink/closed/stale nonce behavior, source standalone window interactions/EN-ZH/resize/reload/focus, game save/restart and multiplayer/external-mod compatibility. No stronger runtime, pixel, natural acquisition or redistribution claim is made by cached compilation and portable tests.
