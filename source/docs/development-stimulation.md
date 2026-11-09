# Classic development stimulation and energy foundation

This slice adapts the exact AcademyCraft **1.0.7** development sources and the checked-out LambdaLib **1.2.3** lifecycle. It is a finite, timed server process, not instant skill acquisition. It does not itself register items, capabilities, packets, events, blocks, or GUIs. See the integration status below before treating a device as implemented.

## Source behavior retained

| Developer | Capacity (IF) | Charging bandwidth (IF/transfer) | Source syncRate | TPS | CPS | Actual ticks/stimulation | Actual IF/stimulation |
|---|---:|---:|---:|---:|---:|---:|---:|
| PORTABLE | 10,000 | 50 | 0.3 | 25 | 750 | 26 | 780 |
| NORMAL | 50,000 | 100 | 0.7 | 20 | 700 | 21 | 735 |
| ADVANCED | 200,000 | 300 | 1.0 | 15 | 600 | 16 | 640 |

- Each server tick pulls `CPS/TPS` energy. The original `++tickThisStim > TPS` comparison remains deliberately unchanged. All stimulation cycles take **TPS+1 ticks**, including the final cycle
- Skill stimulation count is `int(3 + level² * 0.5)`: levels 1–5 require **3, 5, 7, 11, 15** stimulations
- The source estimated consumption is `CPS * stimulations`; it underestimates actual cost. The API exposes both nominal and actual estimates, so the discrepancy is testable rather than hidden
- A portable level-1 skill takes 78 ticks and **2,340 IF**. A level-2 skill takes 130 ticks and **3,900 IF**
- Starting a new action overrides an active action, resets its progress, and never refunds energy already spent
- Actions are revalidated when all stimulations complete. A changed prerequisite or missing reset ingredient fails the action after spending process energy
- Failed consumption clears the developer/action/progress and sets FAILED. Portable `IFItemManager.pull` consumes any remaining partial energy before reporting failure; the adapter preserves this. A future machine adapter must use its own source atomic pull behavior instead
- `abort` changes an active process to FAILED; reset changes it to IDLE. Success changes it to DONE and clears the active references/counters. Terminal state is synchronized on the following tick
- Dirty synchronization occurs before tick logic. Periodic synchronization uses `tickSync-- == 0` with reset to 5, therefore every **six ticks**, not every five. `syncRate` is retained as separate tier metadata; it is not a replacement for this tick cadence
- `DevelopData.getDevelopProgress` has a source formula missing the per-stimulation divisor on its fractional term. `getDevelopProgress`/`Snapshot.sourceProgress` retain that formula. `Snapshot.normalizedProgress` is explicitly a modern display adaptation, bounded to 0–1
- Portable energy reads the **current main hand** each time. Offhand holding does not qualify. Switching to another portable item uses that item's own energy; switching away causes the next pull to fail. Closing the portable GUI does not stop the process: source `IDeveloper.onGuiClosed` defaults to a no-op
- The source client UI checks only nominal energy before sending a start packet (`SkillTree.scala`, level/skill buttons). The server `NetDelegate` and `DevelopData` have no total-energy preflight. The pure engine therefore permits starting with insufficient energy and fails when a pull cannot be satisfied
- Source `DevelopData` enables ticking but never calls LambdaLib `setNBTStorage`; an active process is not restored after logout. Only ability progress and item energy persist

## Learning, category, level, and reset actions

`DevelopmentActions` works against the existing `AbilityProgress` and `SkillCatalog`:

- Level-1 and level-2 skills accept portable or better developers; level-3 skills require normal or advanced; level-4 and level-5 skills require advanced
- Skill completion rechecks current category, level, tier, experience dependencies, and any-learned-skill-of-level conditions. Already-learned completion remains idempotent, as in the source; the controller refuses duplicate *requests* before starting
- Category acquisition takes `5 * (currentLevel + 1)` stimulations. An uncategorized level-0 player therefore requires 5 portable stimulations: **130 ticks / 3,900 IF**
- Acquisition chooses a valid induction factor in inventory and consumes its whole slot; absent a factor, it chooses a random registered classic category. The new player starts at **level 1**, matching source `AbilityData.setCategory`. There is no arbitrary category-selector action
- Existing-category level-up uses the source level-experience gate, requires the same `5 * (level + 1)` stimulations, and caps at level 5. The source places no explicit machine-tier restriction on this action. Portable capacity alone cannot fund some larger level-ups without ongoing charging
- Category reset requires level ≥3, an advanced developer, a held magnetic coil, and an induction factor belonging to a different category. It takes `level * 10` stimulations, clears learned skills through category reassignment, and leaves the player at previous level minus one. It consumes the entire held-coil slot and factor slot, matching source slot clearing
- `CategoryItems` is an inventory integration contract. `NONE` supplies no factors or coil and therefore cannot reset. Implementing this interface does not manufacture those items or register recipes

## Item persistence and Forge Energy contract

`DeveloperItemEnergy` stores a double under `academy:developer_energy` inside Minecraft 1.21.1 `DataComponents.CUSTOM_DATA`. Updating energy preserves other custom-data keys. Missing data reads as **zero energy**. The storage sanitizes malformed, negative, and nonfinite values to prevent corrupt data from becoming an energy exploit; valid source positive-energy arithmetic is retained.

`DeveloperEnergy.charge` returns **untransferred** IF, matching `IFItemManager`; `pull` returns removed IF. Charging/external extraction obey bandwidth unless explicitly bypassed; stimulation pulls bypass bandwidth, as classic `PortableDevData` does.

NeoForge **21.1.252** exposes the item energy capability as `Capabilities.EnergyStorage.ITEM` with the **`IEnergyStorage`** contract; there is no separate `IItemEnergyStorage` interface. `DeveloperItemEnergy.forgeEnergy()` supplies its implementation. The registration owner can register a provider that returns this adapter for the actual portable-developer item.

The compatibility adapter uses the source `RFSupport.CONV_RATE = 4`: **1 IF = 4 RF/FE**. Thus a portable developer exposes 40,000 FE capacity and accepts at most 200 FE per capability call. This directly exposed modern item capability is an intentional compatibility adaptation; original AcademyCraft used RF converter blocks. The adapter preserves fractional IF for individual FE transfers and floors only its integer FE view. Simulation never changes stored energy; nonpositive transfer requests return zero.

There is no automatic battery refill, creative exemption, or infinite developer supplied by these classes. Explicit test-fixture charging is confined to regression tests.

## Integration seam and remaining gaps

`DevelopmentController` offers `startSkill`, `startLevel`, `portable`, `tick`, `abort`, `remove`, `clear`, and an NBT snapshot encoder. The gameplay integration wires portable request handlers, per-player ticking, lifecycle cleanup, the item energy capability, and progress snapshots. It also supplies an actual `InductionFactors` inventory adapter and registered induction-factor/magnetic-coil item identities. These shared integrations compile with this package, but their in-game behavior has not been run. The GUI retains the source nominal-energy admission check; actual stimulation consumption and late failures remain audited separately.

Normal and advanced developer blocks/multiblock structures, source seating/user ownership, charging machines, generators, wireless networks, converter blocks, recipes, magnetic-coil crafting, original GUI animations, and full rendered machine models are **not implemented by this foundation**. Persisted item storage and an FE capability do not establish a complete original power network. Induction-factor inventory adapters and item registrations are supplied by the gameplay integration; recipes and full machine integration remain separate work. A portable developer remains restricted to L1/L2 skill learning; learning higher-level skills requires real future normal/advanced device implementations.

No Minecraft client, dedicated server, or GameTest server was launched for this work. EULA authorization remains pending. Compile and standalone JVM regressions exercise arithmetic and process behavior, not in-game capability discovery, network synchronization, inventory interaction, charging interoperability, or GUI rendering.

## Standalone regression entry points

- `cn.academy.port.develop.DevelopmentRegressionTest`: tier constants, every skill stimulation count and tick boundary, nominal/actual budgets, per-tick pull, override, partial depletion, validation failure, abort/reset, source/normalized progress, six-tick synchronization, acquisition, level cap, dependencies, skill-tier restrictions, and reset ingredient/level rules
- `cn.academy.port.develop.DeveloperEnergyRegressionTest`: finite empty storage, bandwidth, overflow, partial drain, invalid values, persisted custom-data payload preservation, double/fractional energy, source RF conversion, FE simulation, minimum transfers, capacity, extraction limits, and retained-capability behavior after the backing item disappears

The energy payload tests use NBT payloads through the exact helper read/write methods. They do **not** claim a Minecraft `ItemStack` serialization/registry runtime test. That additional integration check needs authorized runtime tests.

## Exact provenance

Sources checked in this workspace:

- `AcademyCraft-1.0.7`: `ability/develop/DevelopData.java`, `DeveloperType.java`, `PortableDevData.java`, `LearningHelper.java`, `action/DevelopActionSkill.java`, `action/DevelopActionLevel.java`, `action/DevelopActionReset.java`, `condition/*`, `ability/api/Skill.java`, `ability/api/data/AbilityData.java`, `ability/block/TileDeveloper.java`, `ability/client/ui/SkillTree.scala`, `energy/IFConstants.java`, `energy/api/IFItemManager.java`, `support/rf/RFSupport.java`
- `LambdaLib-1.2.3`: `util/datapart/DataPart.java`, `EntityData.java` for opt-in persistence and sync lifecycle
- Resolved NeoForge 21.1.252 sources: `capabilities/Capabilities.java`, `energy/IEnergyStorage.java`; Minecraft 1.21.1 resolved sources: `world/item/component/CustomData.java`

AcademyCraft-derived behavior is covered by the project's upstream GPLv3 provenance and NOTICE. No original shader, GUI, or model file is asserted to run merely because its reference was inspected.
