# Remaining classic gameplay content audit

Read-only snapshot, 2026-10-01 UTC. This audit writes only this new report directory. It ran no Gradle, Java compilation, GameTest/server/client, CUA, publication, push, or user-computer operation. The requested baseline is AcademyCraft **classic 1.0.7**, expected commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, with LambdaLib **1.2.3**, from the cached `.reference` trees. These source trees and the working port are materialized without Git metadata; this audit cannot independently resolve their Git HEADs. `inventory.json` records the relevant file hashes, actual source declarations and current bindings instead.

**Recommendation: implement the complete Metal Former next.** It supplies the missing original survival production of needles and coins, adds all four classic machine modes, and reuses the already functional finite solar/energy-unit/node power system. The machine does not need any of the eight staged active skills. Promote those frozen skill/shared-consumption stages in the owner's serial lane, then make Metal Former the next substantial playable content increment.

At this snapshot:

- **39 of 54** classic annotated item/block fields have real registered equivalents; **15 fields lack an equivalent**
- **37 of 49** declarations in `default.recipe` have production resources; **12 are missing**. This measures recipe coverage, not full natural acquisition
- All **21 built-in Metal Former transformations** are missing, together with its conditional third-party refinement rules
- All **14 tutorial pages and their runtime unlock/first-spawn guide flow** are missing
- The source registers **56 achievements across five pages**. The port has one isolated Mine Detection advancement, with 55 missing awards and no classic five-page presentation
- All three dynamically registered terminal-app items are absent; the two IC2 converter blocks are additionally absent but conditional on classic IC2 support

The **27 current active paths**, **eight frozen remaining active paths** and **frozen shared-consumption API** are explicitly outside the missing-content count. This audit does not ask another worker to recreate them. A gameplay-content field count also does not turn an item texture or an unused model into a registered, functioning item.

## Naming and authority

Classic's real item/block registry namespace is `academy-craft`, whereas its asset namespace is `academy`. LambdaLib `RegistryType` combines the AcademyCraft `@RegistrationMod(prefix="ac_")` with registry type and field name. Thus classic Metal Former is **`academy-craft:ac_Block_metalFormer`**, not a historical `academy:metal_former` key. `@RegWithName` explicitly overrides this for RF converters. Modern production uses semantic lowercase `academy:*` IDs. The table below gives exact old keys and proposed modern equivalents; proposed keys are not claims that production currently owns them. Legacy-world/save conversion is not implemented or implied by these mappings.

Primary registration sources: `AC:java/cn/academy/{ability/ModuleAbility.java,crafting/ModuleCrafting.java,energy/ModuleEnergy.java,terminal/ModuleTerminal.java,vanilla/ModuleVanilla.java,misc/tutorial/ModuleTutorial.java,misc/achievements/ModuleAchievements.java}`, `AC:java/cn/academy/core/AcademyCraft.java`, `AC:java/cn/academy/support/rf/RFSupport.java`; registration-key rules are `LL:java/cn/lambdalib/annoreg/core/RegistryType.java:118–145` and `LL:java/cn/lambdalib/annoreg/mc/{BlockRegistration,ItemRegistration}.java`. Here `AC:` means `.reference/AcademyCraft-1.0.7/src/main/`, `LL:` means `.reference/LambdaLib-1.2.3/src/main/`, and `P:` means current production `src/main/`.

The existing `docs/classic-survival-manifest.json` is useful as the complete source inventory, but its status strings are historical. In particular its recipe43 Silicon Barn status is obsolete: production `data/academy/recipe/classic/silbarn_01.json` really implements that exact two-piece recipe. The historical foundation statement that `genPhaseLiquid` is unused is also obsolete/incomplete: canonical **separate `PhaseLiquidGenerator.java`** consults it, and production binds that generator. Do not convert either stale statement into new work.

## Existing content that must be retained

| Classic content | Current actual binding and behavior | Remaining evidence boundary |
| --- | --- | --- |
| 16 materials, frame and four ores | `P:java/cn/academy/port/survival/ClassicMaterials.java` registers the materials and blocks; `AcademyCraft.java` calls `register`. `ClassicOreRules`, mining tags, loot tables, four configured/placed features and `neoforge/biome_modifier/classic_ores.json` exist | Ore parameters are present; a whole ordinary new-chunk natural exploration/playthrough is still unproved |
| Original ore rules | Resonant/constraint/crystal/silicon attempts 18/24/48/22, sizes 4/4/3/4, absolute anchors Y0–59, stone-only replacement, source mining tiers, actual exclusive-upper-bound drops are retained | Modern OreFeature geometry/random use, biome membership and modern terrain are adaptations, not identical classic per-seed worlds. Negative Y and deepslate replacement are deliberately not invented |
| Phase sources, matter units, fusion | `ClassicFusion.register` installs real fluid/type/block/tile/items/Fusor/menu/feature and fluid/item/IF capabilities. `ClassicMatterUnitItem` captures source fluid and releases it, stack16, real harvest event. `ClassicFusorBlockEntity` ticks native inventory/liquid/finite IF | Harvest event exists, but classic achievement/tutorial consumers do not. No bucket shortcut is supplied |
| Phase natural generation | `fusion/phase/PhaseLiquidGenerator` and JSON configured/placed feature + biome modifier are genuinely bound. Default `genPhaseLiquid=true`, one 0.3-chance lake attempt/chunk, absolute start Y5–34, source lake mask/shell/write algorithm | Modern world/chunk access differs. Direct feature fixtures and declared phase supply do not prove natural discovery |
| Two fusion transformations | `ClassicFusorWork` + actual tile: low→normal consumes3000mB; normal→pure8000mB; finite2000IF,50IF bandwidth,12IF/work tick | Source floating accumulation takes121 uninterrupted work ticks,1452IF. Wrong input aborts before debit; low liquid/output mismatch follows debit. Keep these known source quirks |
| Energy unit and components | `ClassicEnergyItems` owns genuine10000IF/20IF item and FE capability; recipe18/19/20 gives1/2/4 empty units; recipe37 makes converter; `ClassicItemEnergy` owns item data | Native IF and 1IF=4FE adapter exist. These do not implement RF converter blocks, IC2 integration or all old manager extension APIs |
| Solar | `ClassicSolarGenerators.register`, actual block/tile/menu/client binding, recipe09. Finite1000IF buffer,100IF bandwidth;3IF/tick clear and0.6 rainy; day interval0..12500 and visible sky; real battery slot | Existing client/native charging workflows have declared setup; independent full survival acquisition and audible/pixel parity are unproved |
| Nodes, matrix and three cores | `ClassicWirelessDevices`, `ClassicWirelessSavedData`, `ClassicWirelessGraph`; three actual nodes and eight-cell matrix, three separately registered core items, recipes14–16 and27–30 | Source metadata cores were intentionally split into modern IDs. Graph fixes source energy-creation/balancing bugs, atomic unlink and unloaded-chunk behavior explicitly; it is not a missing implementation, nor byte-for-byte source bug parity |
| Developers and progression | Portable item, `MachineDevelopers` normal/advanced blocks, finite energy/structure/session/menu; recipes42,44–46, factor loot, source-category reset with held magnetic coil, factors and advanced developer | Earned UI target learning in declared QA worlds is valid narrower evidence. The original terminal/guide and achievement presentation remain missing |
| Coin, needle, Silicon Barn | Actual coin/needle registrations and Barn item/entity/client adapter; Barn recipe43 exists | Coins and needles have no source survival recipe because their only producers are missing Metal Former. Barn's alternative machine ETCH route is missing, but its authentic crafting route is present |

Registration and implementation evidence here is source inspection, not a new runtime recertification. Existing native/client claims belong to the owner's checkpoint/evidence documents. Artwork for Metal Former, phase/wind generators, hook, terminal/tutorial and achievements already lies in `P:resources/assets/academy`, but their presence supplies no missing registry/menu/tick/player-event implementation.

## Missing registered equivalents

All absence findings were checked against actual production Java registration owners and resources, not asset filenames or README status text.

| Exact classic registry key | Modern equivalent candidate | Missing functional scope / source |
| --- | --- | --- |
| `academy-craft:ac_Block_metalFormer` | `academy:metal_former` | Complete block/tile/three-slot inventory/modes/menu/network/IF/recipe processing. `AC:java/cn/academy/crafting/block/{BlockMetalFormer,TileMetalFormer,ContainerMetalFormer,SlotMFItem}.java`; recipe26 |
| `academy-craft:ac_Block_phaseGen` | `academy:phase_gen` | Real liquid-consuming generator/8000mB tank/battery slot/wireless output/menu/render. `AC:java/cn/academy/energy/block/{BlockPhaseGen,TilePhaseGen,ContainerPhaseGen}.java`; recipe08 |
| `academy-craft:ac_Block_windgenBase` | `academy:windgen_base` | Complete multiblock base, finite generator, battery slot, structure states; recipe10 |
| `academy-craft:ac_Block_windgenPillar` | `academy:windgen_pillar` | Actual pillar and vertical structure checks; recipe11 |
| `academy-craft:ac_Block_windgenMain` | `academy:windgen_main` | Multiblock top, fan inventory, rotation/sync, structure state; recipe12 |
| `academy-craft:ac_Item_windgenFan` | `academy:windgen_fan` | Nonstackable maxDamage100 fan and original installation behavior; recipe13. Entire wind family is `AC:java/cn/academy/energy/block/wind/`, source client render/UI companions |
| `academy-craft:ac_Block_infiniteGen` | `academy:cat_engine` | Cat/infinite generator, no source survival recipe. `BlockCatEngine` is asset/block name; `TileCatEngine` uses inventory name `infinite_generator`. Do not confuse them with two registered blocks |
| `academy-craft:ac_Item_terminalInstaller` | `academy:terminal_installer` | Install-on-use persistent terminal state, item consumption, install effect and terminal entry/UI. `AC:java/cn/academy/terminal/`; recipe22 |
| `academy-craft:ac_Item_magHook` | `academy:maghook` | Throw/anchor/drop/retrieve/persist/sync item/entity/render and metal-entity target membership. `AC:java/cn/academy/vanilla/electromaster/{item/ItemMagHook,entity/EntityMagHook,client/renderer/RendererMagHook}.java`; recipe41 |
| `academy-craft:ac_Block_abilityInterferer` | `academy:ability_interferer` | Range10..100, enabled switch, player source lifetime, owner-facing UI and persistence. `AC:scala/cn/academy/ability/block/AbilityInterferer.scala`. No source crafting recipe; current `AbilityProgress.interfering`/HUD support alone supplies no real interference source |
| `academy-craft:rf_input` | `academy:rf_input` | Classic RF→IF bridge block/tile/wireless generator + four pair recipes. `AC:java/cn/academy/support/rf/` |
| `academy-craft:rf_output` | `academy:rf_output` | Classic IF→RF bridge block/tile/wireless receiver. 1IF=4RF; merely exposing FE on developers/units does not supply these devices |
| `academy-craft:ac_Item_itemTutorial` | `academy:tutorial` | Right-click guide, first-spawn grant, fourteen pages/unlocks/previews/localized text. `AC:java/cn/academy/misc/tutorial/`; recipe48 |
| `academy-craft:ac_Item_logo` | `academy:logo` | Source logo display item and original dedicated creative-tab presentation, absent; port currently adds things to vanilla tabs. Source has no ordinary gameplay recipe |
| `academy-craft:ac_Item_DUMMY_ITEM` | `academy:achievement_icon` | Dynamic achievement display icons/presentation, absent. This is a display-only item, not a new obtainable gameplay reward |

Additional **manual/generated** source registrations, outside those54:

- `academy-craft:ac_app_skill_tree`, `academy-craft:ac_app_freq_transmitter`, `academy-craft:ac_app_media_player`: `AC:java/cn/academy/terminal/item/ItemApp.java` generates an installer only for each non-preinstalled app. Recipes38/40/39 respectively are missing. Proposed modern equivalent names are `academy:app_skill_tree`, `academy:app_freq_transmitter`, `academy:app_media_player`
- `academy-craft:eu_input`, `academy-craft:eu_output`: optional IC2 initialization in `AC:java/cn/academy/support/ic2/IC2Support.java`, with four recipes and IC2 energy-item managers. These are a **conditional compatibility gap**, not a required vanilla standalone survival dependency. Do not register dummy copper cable, BatBox or fake IC2 devices when a supported modern counterpart is absent
- `academy-craft:ac_MediaItem`: song-specific collection item, loot and artwork are intentionally excluded with third-party media. Do not treat their omission as a request to restore copyrighted songs. The general terminal/media-player application and safe user-provided-media functionality are separable runtime features and remain unimplemented

## Missing default recipes, exactly twelve

Full original grids and source lines for all49 declarations are in `inventory.json`. All new recipes must retain occupied/empty cells, yield and distinct aliases; the following is an output/dependency index, not substitute recipes.

| Source ordinal | Output | Dependency/order |
| --- | --- | --- |
| 8 | Phase generator×1 | Existing low crystals, frame and empty matter units already close its input chain |
| 10 | Wind base×1 | Existing iron/frame/converter |
| 11 | Wind pillar×1 | Vanilla iron bars/redstone |
| 12 | Wind main×1 | Existing frame/constraint plates/converter |
| 13 | Wind fan×1 | Existing iron plates and vanilla iron bars |
| 22 | Terminal installer×1 | Existing chips/plates/brain/info, vanilla panes/redstone block |
| 26 | Metal Former×1 | Shears, two calculation chips, frame, two constraint plates, empty matter unit; all prerequisites already exist |
| 38 | Skill Tree app×1 | Compass/data/info; requires functional terminal/app installation first |
| 39 | Media Player app×1 | Three note blocks/data/info; requires functional application, independently of excluded bundled songs |
| 40 | Frequency Transmitter app×1 | Resonance component/data/info; requires application and genuine wireless UI integration |
| 41 | Magnetic hook×3 | Five source iron plates in exact source shape; requires real projectile/anchor behavior |
| 48 | Tutorial×1 | Shapeless vanilla book+low crystal; requires functioning guide |

RF converters add four **Java-registered recipes** not counted within49: source primary input/output grids plus each converter's one-item conversion into the other. IC2 conditionally adds its separate four. Built-in Metal Former recipes and fusion operations are also outside the49 default declarations.

## Metal Former: next increment contract

The entire input recipe chain is acyclic today: source ore/ordinary vanilla inputs → existing material recipes → recipe26 machine; crafted Solar charges an authentic Energy Unit, which powers the machine's battery slot. Alternatively genuine node→receiver IF can power it. No phase fluid or purified crystal is needed to operate it; the empty matter unit is only part of the crafting recipe. It immediately closes source coin/needle supply for already implemented combat paths.

Source machine rules, `TileMetalFormer.java:40–203`:

- Slots are input0/output1/battery2; capacity3000IF, bandwidth50IF; modes in exact order **PLATE, INCISE, ETCH, REFINE**, startingPLATE
- Idle recipe scan uses five ticks; matching is first registered recipe with item+metadata, sufficient count and current mode
- Active work calls `pullEnergy(13.3)` **before** its blocked-input/output check. Progress requires exact13.3 returned and unblocked input/output. Failure resets recipe/counter without refund; a partial pull or blocked output can still drain power
- Complete after exactly60 successful work increments, consume exact input count, append exact output count, reset recipe/work. Nominal work debit is798IF; do not round to800, use60.0 work ticks, or make failed pulls transactional
- Battery recharge occurs **after** work/scan; synchronize work/current/mode every10ticks. Source persists inventory, energy and mode, but not current recipe/progress
- GUI input slot accepts items occurring in any mode; output rejects insertion. Shift-click prioritizes supported batteries, then input. Sided automation exposes down(output,battery), up(input), other(battery), extraction down only. **Source inherited `TileInventory.isItemValidForSlot` always returns true**, so source automation is more permissive than GUI input validation. A hardened modern restriction must be recorded as an intentional adaptation, not asserted exact source parity
- Native server menu/session/distance checks must own mode changes; existing node graph can consume its real `ImagFluxReceiver` capability. Use the current finite IF helpers rather than a fake refill or FE-only shell. Source faces/textures, mode icons, progress/energy/wireless pages and looping non-song machine work sound must be genuinely bound

All21 built-in transformations, registered in `ModuleCrafting.java:209–235` and `ModuleVanilla.java:70–79`, are required:

| Mode | Input | Output |
| --- | --- | --- |
| INCISE | Imag silicon ingot×1 | Wafer×2 |
| INCISE | Wafer×1 | Imag silicon piece×4 |
| INCISE | Reinforced iron plate×1 | Needle×6 |
| INCISE | Rail×1 | Needle×2 |
| ETCH | Data chip×1 | Calculation chip×1 |
| ETCH | Wafer×1 | Silicon Barn×1 |
| PLATE | Iron ingot×1 | Reinforced iron plate×1 |
| PLATE | Constraint ingot×1 | Constraint plate×1 |
| PLATE | Reinforced iron plate×2 | Coin×3 |
| REFINE | Imag silicon ore×1 | Imag silicon ingot×4 |
| REFINE | Constraint metal ore×1 | Constraint ingot×2 |
| REFINE | Resonant crystal ore×1 | Resonant crystal×3 |
| REFINE | Imag crystal ore×1 | Low crystal×4 |
| REFINE | Gold ore×1 | Gold ingot×2 |
| REFINE | Iron ore×1 | Iron ingot×2 |
| REFINE | Emerald ore×1 | Emerald×2 |
| REFINE | Quartz ore×1 | Quartz×2 |
| REFINE | Diamond ore×1 | Diamond×2 |
| REFINE | Redstone ore×1 | Redstone block×1 |
| REFINE | Lapis ore×1 | Lapis×12 |
| REFINE | Coal ore×1 | Coal×2 |

Source refinement inputs are OreDictionary **ore blocks**, not modern raw iron/gold items. Modern shared ore tags can map the source dictionary, but adding raw-material recipes, deepslate equivalents or altered furnace yields must be explicitly documented instead of silently inventing gameplay. Conditional Copper/Tin/Lead/Platinum/Silver/Nickel refinement additionally requires both ore/output dictionaries and a valid first-ore furnace result; output is twice furnace yield below32, otherwise64. Do not invent third-party materials or call these conditions satisfied by textures.

Suggested acceptance owned by main: independent canonical oracle for work/order/failure/mode/persistence and all21 recipes; real registered recipe26 consumption/crafting, block/tile/menu/loot/atlas/capability binding; source-compatible battery and node power, side automation, output-full/insufficient-energy/changed-input/reload/replay cases; actual client craft/place/mode-cycle/needle+coin processing and physical use of the outputs. Every supplied raw input, powered store, mature prerequisite and target must be disclosed. A bounded supply-assisted machine test is valuable but still does not prove the whole pristine survival chain.

## Later increments and source traps

1. **Phase Generator** after Metal Former or in an isolated independent stage. Existing real fluid/matter-unit/IF interfaces make it relatively bounded. `TilePhaseGen`:6000IF buffer,50IF bandwidth,8000mB tank; consume at most100mB/tick at0.5IF/mB, integer drain truncation. Source generates before processing incoming cells; cell intake requires **spare tank volume >1000**, not≥1000. Preserve empty-cell return and real battery output. Do not turn1000mB into10000IF by using the distinct energy-unit fluid-container mapping as its generation ratio
2. **Magnetic Hook** is a self-contained item/entity increment: recipe41 yields3; throw speed2, gravity0.05, ordinary4 damage on nonhook entity hit, item return, face anchor state, player-hit recovery, host-air removal and persistent attachment. Add its genuine entity to the configurable metal-target defaults, closing the original Magnetic Movement target option. Current `ClassicMetalTargets` explicitly says hook is unported
3. **Tutorial + terminal/apps** closes discovery and original access paths. They share UI/player state but can be staged independently from generator runtime. Guide can open directly through its own item without a terminal. Terminal unlocks settings/tutorial preinstalled applications plus separately acquired Skill Tree/Frequency Transmitter/Media Player; terminal installation and app acquisition persist and must consume items exactly once
4. **Wind generator family**, complete base/top multiblocks,8..40 pillar chain and installed fan. `TileWindGenBase` buffer20000IF/bandwidth300IF; generation15×lerp(0.5,1,clamp((topY−70)/90)), capped by requested space. Structure refresh10ticks. Source computes `noObstacle` but **does not use it in `shouldGenerate()`**. Fan maxDamage100 is declared, but source contains no wear decrement; adding obstruction gates or wear would change classic behavior. Preserve state/render semantics separately
5. **Achievement system/pages**, then remaining standalone RF bridges/interoperability and display-only source logo/dummy item. Cat engine is a real missing creative/no-recipe device:2000IF buffer,200IF bandwidth, generation min(required,500), source right-click link/unlink behavior. Do not add an invented survival recipe to make it obtainable
6. **Optional integrations**: classic NEI recipe/usage handlers and MineTweaker3 recipe registration APIs exist in source but no matching port integration exists. Their legacy APIs cannot simply run on1.21.1. Choose supported optional modern counterparts separately; standalone machine behavior must remain usable without them. IC2 conditional recipes/energy managers need a real supported target dependency, not inert placeholders

## Guide and achievement scope, precisely

Guide pages: **welcome, ores, phase_generator, solar_generator, wind_generator, metal_former, imag_fusor, terminal, ability_developer, ability_basis, energy_bridge, misc, develop_ability, wireless_network**. Source contains English and Simplified Chinese page Markdown; selected language falls back to English. Existing four language JSON dictionaries/guide textures do not include or render these page contents.

`ACTutorial.addCondition` combines page conditions with **OR**. `Conditions.itemObtained` means remembered crafted **or** picked up **or** smelted events, not current inventory inspection and not all listed devices. Welcome/ability_basis/misc/develop_ability/wireless_network are default installed. `TutorialData` saves condition bits/activated IDs/first-guide state/Misaka ID, checks dirty unlocks every3ticks, emits activation events, and default `giveCloudTerminal=true` drops the actual tutorial item for the first player after its10tick schedule. Its name says terminal, but the granted item is the guide, not `terminal_installer`. These are gameplay/runtime gaps, not merely missing artwork.

Classic achievement IDs are explicit in `inventory.json`. Counts/pages: default12, Electromaster13, Meltdowner11, Teleporter10, Vector10. Source category IDs are `<category>.<achievement>` withlv1..5 on each category. The commented-out `electromaster.iron_sand` achievement has language text but **is not registered** and is not included in56. Source events distinguish pickup/actual crafting/matter harvest/skill learning/level change from manual successful ability conditions; they are not all inventory or learn-skill triggers.

Current `P:resources/data/academy/advancement/electromaster/mine_detect.json` and `P:java/cn/academy/port/skill/MineDetect.java` supply one actual accepted-cast award. It has vanilla iron-ore icon, no page tree parent and no five-page layout. Persistent Teleporter counters/flags record some source facts but have no actual award consumer; other canonical ability awards are absent. Restoring the original page relationships, icons, event timing and persistent player awards needs an explicit modern advancement/presentation adapter. Do not count the presence of56 translated titles as56 working awards.

## Natural obtainability and fidelity limits

There is a genuine static recipe/device DAG for portable+Solar and later node/Fusor/Normal/Advanced workflows. Its source consistency is useful evidence. **It is not a completed survival proof.** Current physical/native workflows use declared ore/material/cell/environment/target/prerequisite setup. The separate pristine Survival/Normal/commands-off world has only **three naturally obtained dirt**, no category, no learned skills and no completed mod progression (`docs/m14-pristine-natural-partial.md`). Static route evidence explicitly excludes fuel, tools, food, portal travel and chosen-category chest search from its device bill.

The strongest unclosed evidence loop remains one provenance-clean natural starter playthrough: ordinary terrain/chunks and drops → fueled native smelting → physically consumed crafting → crafted Solar supplying all IF → earned category/root through actual developer GUI → bind/activate/cast → genuine save/restart retention. Higher Normal/Advanced/Metal Former paths need their own wider provenance before being called naturally completed. A complete source catalog, passing fixtures or a silent gameplay clip cannot substitute for that chain.

The owner should also avoid full parity claims for old save import, modern ore random geometry, source graph bugs intentionally corrected, external multiplayer, native audio playback and1.7.10-versus1.21.1 pixel/audio parity. This report did not run or fail any new runtime test.

Excluded third-party songs/covers/song-item artwork remain **intentional redistribution exclusions**, governed by existing `NOTICE`/`LICENSE`, rather than missing material/ore/device gameplay. General terminal/media-player code is still a runtime gap and can be assessed independently. This audit authorizes neither publication nor a legal/licensing conclusion; no rights were added or media copied.

## Deliverables and next owner action

`inventory.json` contains all54 fields, inferred exact legacy keys, actual current equivalent IDs and registration evidence, all49 recipe declarations with true current status/source lines/grids,56 achievement IDs,14 guide IDs and SHA256 snapshots. It contains no copied songs or runtime binaries.

Main can now scope a separate **complete Metal Former** stage with21 built-in transformations and bounded optional-tag behavior, using existing energy/graph registrations. Resolve any shared integration hunks only after the frozen skills/shared-consumption promotion. Keep main's Gradle/native/client lane serial and preserve the honest pristine-survival evidence boundary.
