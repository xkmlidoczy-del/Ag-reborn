# AcademyCraft 1.0.7 survival foundation

## Source scope and implemented catalog

Canonical references are `.reference/AcademyCraft-1.0.7` and `.reference/LambdaLib-1.2.3` in this same checkout. The machine-readable `classic-survival-manifest.json` inventories all 54 annotated item/block fields, both optional manually registered IC2 converter blocks, generated terminal app items and manually registered MediaItem; all 49 declarations in `assets/academy/recipes/default.recipe` are retained in source order with explicit implemented/deferred statuses.

New runtime catalog:

- 16 ordinary materials: crystal_low, crystal_normal, crystal_pure, calc_chip, data_chip, wafer, constraint_ingot, imag_silicon_ingot, reinforced_iron_plate, imag_silicon_piece, reso_crystal, constraint_plate, brain_component, info_component, resonance_component, energy_convert_component
- 5 complete ordinary blocks and block items: machine_frame, reso_crystal_ore, constraint_metal_ore, crystal_ore, imag_silicon_ore
- 14 exact crafting recipes and 3 smelting recipes, all from default.recipe. IDs use `academy:classic/<normalized output>_<source ordinal>`
- Four configured/placed ore features, one Overworld biome modifier, a dimension/config placement gate, mining/material/ore tags, five block loot tables, native cube/item models and blockstates
- Uncraftable induction factors added as four distinct category-bearing weight-4/count-1 entries to each of the five source-equivalent chest categories
- English and Simplified Chinese names imported from the source language files

Nothing in this stage substitutes another ingredient, registers an inert machine shell, or invents a crafting shortcut to coins, needles, factor items or higher crystal tiers. Mid/high-purity crystals and the energy converter are valid plain material items; their acquiring recipes remain dependent on unfinished machinery/energy units.

## Exact source recipes implemented

| Source ordinal | Result | Exact source yield/input |
| --- | --- | --- |
| 1 | Imag silicon piece ×2 | shapeless wafer ×1 |
| 2 | Data chip ×1 | three redstone across top, iron plate below center |
| 3 | Data chip ×1 | three redstone across top, silicon piece below center |
| 4 | Calculation chip ×1 | shapeless data chip + quartz + quartz |
| 5 | Calculation chip ×1 | shapeless data chip + resonant crystal |
| 6 | Reinforced iron plate ×2 | three iron ingots vertically |
| 7 | Machine frame ×1 | four iron plates in cross around redstone |
| 21 | Constraint plate ×2 | three constraint ingots horizontally |
| 31 | Imag silicon ingot ×1 | smelt imag silicon ore; 0.8 XP |
| 32 | Constraint ingot ×1 | smelt constraint metal ore; 0.7 XP |
| 33 | Low-purity crystal ×1 | smelt crystal ore; 0.8 XP |
| 34 | Information processor ×1 | glowstone dust above data chip |
| 35 | Brainwave analyzer ×1 | gold nuggets above/below calculation chip, redstone left/right |
| 36 | Resonator ×1 | constraint plate/resonant crystal/constraint plate; redstone below center |
| 42 | Portable developer ×1 | data chip/glass pane/calc chip; brain/info/energy converter; constraint plate/low crystal/constraint plate |
| 47 | Wafer ×1 | shapeless imag silicon ingot ×1 |
| 49 | Magnetic coil ×1 | constraint plate/resonant crystal/constraint plate twice; iron plate/diamond/iron plate |

Only the actual source OreDictionary references become shared modern tags: plateIron→c:plates/iron, ingotIron→c:ingots/iron, dustRedstone→c:dusts/redstone, dustGlowstone→c:dusts/glowstone, nuggetGold→c:nuggets/gold. Source custom recipe aliases remain exact items. The plateIron output deterministically uses AcademyCraft's own registered reinforced plate rather than an arbitrary third-party first OreDictionary entry. Counts, occupied/empty cells and default mirrored shaped matching are preserved. Furnace time remains vanilla 200 ticks; no invented blasting recipes.

## Mining, loot and generation

| Ore | Hardness | Harvest tier | Vein size | Attempts/chunk | Unenchanted drop |
| --- | --- | --- | --- | --- | --- |
| Resonant crystal | 3 | iron pickaxe | 4 | 18 | resonant crystal ×1 |
| Constraint metal | 4 | stone pickaxe | 4 | 24 | ore block ×1 |
| Imag crystal | 3 | iron pickaxe | 3 | 48 | low-purity crystal ×1–2 |
| Imag silicon | 3.75 | iron pickaxe | 4 | 22 | ore block ×1 |

All feature starts are uniform absolute Y0 through Y59, matching `rand.nextInt(60)`. Veins replace exactly `minecraft:stone`; no deepslate ore, stone-replaceable tag broadening, negative-Y range expansion, extra ore variants, retrogen or biome restrictions were invented. Modern vanilla OreFeature replaces the removed WorldGenMinable engine; the configured sizes, attempt counts, source order and starting range are preserved, but identical per-seed 1.7.10 vein geometry/random-consumption is not claimed. Veins can extend beyond the anchor height range, as in ordinary ore generation. Modern biome membership and existing-chunk state naturally differ from 1.7.10.

`genOres=true` is the sole active source ore-generation config flag. It lives in a separate COMMON `academy-worldgen.toml` so it is ready during worldgen assembly. The placement gate checks the actual `minecraft:overworld` dimension key, not only an Overworld biome tag; copies of those biomes in Nether/custom dimensions do not bypass it. It also checks the config at placement time. Existing chunks are not regenerated.

Crucial detail: LambdaLib `RandUtils.rangei(from,to)` uses `nextInt(to-from)`, an exclusive upper bound. Thus declared 1..3 crystal drops become actual 1..2, declared 1..2 resonant drops become actual1, and default self-dropping ores are also actual1. Standard inherited BlockOre Fortune multiplication applies only to crystal/resonant item drops; self-dropping ores ignore Fortune. Silk Touch gives one source ore block. No mining XP was invented: plain blocks retain zero experience drops. Native loot RNG replaces LambdaLib's global RNG; exact random sequences are not claimed. Standard modern explosion handling is used.

Source `genPhaseLiquid` is declared in ModuleCrafting but is never consulted or implemented in ACWorldGen. This foundation adds no invented phase-liquid feature or dead config toggle.

## Induction factor loot adaptation

Source `ModuleAbility.__init()` adds one weight4/count1 entry for each category to MINESHAFT_CORRIDOR, PYRAMID_DESERT_CHEST, PYRAMID_JUNGLE_CHEST, STRONGHOLD_LIBRARY and DUNGEON_CHEST. These map to abandoned_mineshaft, desert_pyramid, jungle_temple, stronghold_library and simple_dungeon chest tables.

The NeoForge load handler appends four category-bearing entries to the first primary weighted pool through three narrowly declared loot-assembly access transformers. Existing tables/pools, entries, functions, conditions and rolls stay intact. This avoids re-encoding unresolved enchantment registry tags during datapack loading; the earlier codec-rebuild implementation caused an actual startup failure and was replaced. It does not grant an extra guaranteed item or an additional roll. Per-category weight4 and count1 are exact. Absolute factor appearance probabilities cannot match 1.7.10 because vanilla chest contents/weights/roll partitions changed. This modern pool mapping is intentional. Duplicate injection is prevented; empty/custom tables with no pool are preserved unchanged. Existing unlooted versus already-generated chest behavior follows Minecraft's lazy loot-table engine. No factor crafting recipe exists.

## Intentionally remaining source catalog

All nonfoundation annotated entries remain explicit in the manifest. Principal functional work still needed:

- Imag Phase fluid/block/TileImagPhase and complete matter units (stack16, capture/place/event/fluid-container semantics)
- Imag Fusor, Metal Former, their energy/liquid/inventory/GUI/render systems and native machine recipes
- Energy unit (capacity10000, bandwidth20), matrix cores with three source metadata tiers, wind-generator fan durability100
- Basic/standard/advanced wireless nodes, wireless matrix, infinite/solar/phase/wind generators, wind multiblock parts
- Normal/advanced multiblock developers and ability interferer
- Magnetic hook and silbarn functionality; terminal installer/apps and tutorial item/flows
- RF input/output and optional IC2 input/output converters, integrations and their four recipes each
- Logo/achievement display items and licensed MediaItem/media functionality

### Machine recipes that must not be replaced with crafting

Imag Fusor: low→normal crystal consumes3000; normal→pure consumes8000. These numbers are liquid consumption, not XP or crafting yield.

Metal Former ModuleCrafting fixed recipes:

- INCISE: imag silicon ingot×1→wafer×2; wafer×1→silicon piece×4
- ETCH: data chip×1→calculation chip×1
- PLATE: iron ingot×1→reinforced plate×1; constraint ingot×1→constraint plate×1
- REFINE: imag silicon ore×1→ingot×4; constraint ore×1→ingot×2; resonant ore×1→resonant crystal×3; crystal ore×1→low crystal×4
- REFINE shared vanilla ores: gold→gold ingot×2; iron→iron ingot×2; emerald→emerald×2; quartz→quartz×2; diamond→diamond×2; redstone→redstone block×1; lapis→lapis×12; coal→coal×2
- REFINE optional Copper/Tin/Lead/Platinum/Silver/Nickel: require ore and output ingot dictionaries plus a valid furnace result; output twice furnace yield when below32, otherwise64. Do not register missing third-party materials

ModuleVanilla machine recipes:

- INCISE reinforced plate×1→needle×6; rail×1→needle×2
- PLATE reinforced plates×2→coin×3
- ETCH wafer×1→silbarn×1

The energy-unit recipes18/19/20 and converter37 are now integrated and native-tested. Low-purity units and the converter have an authentic crafting path from source ore drops and existing material recipes. Mid/high-purity crystals still require Imag Fusor. Portable developer crafting now has its genuine converter dependency; independently powered progression still awaits the source generator. The remaining28 declarations include matter unit×4 and machine/generator/node/matrix/app/tool/tutorial recipes. See ENERGY-UNITS.md for exact finite behavior and remaining imagProj fluid dependency.

## Integration ownership and precise instructions

This staged implementation changed only `.staging/survival`. The integration owner handles production integration and Gradle/runtime launches.

1. Copy staged `src/main/java` and `src/test/java` to production preserving paths. Copy `src/main/resources/data` and `src/main/resources/assets/academy/{models,blockstates}` preserving paths
2. Merge the staged `assets/minecraft/atlases/blocks.json` directory sources into the production file by JSON-object equality. Preserve existing sources. Both `items`→`items/` and `blocks`→`blocks/` are required for these plural classic textures; PNG existence alone is insufficient
3. Merge `src/main/resources/academy_survival_integration/en_us.json` and `zh_cn.json` as entries into `assets/academy/lang/en_us.json` and `zh_cn.json`. These are merge fragments, not full-file replacements; do not copy the fragments as a new public artifact
4. Import `cn.academy.port.survival.ClassicMaterials` in AcademyCraft and call `ClassicMaterials.register(bus, container)` once from its mod constructor. The class owns its separate block/item/placement registers, common config, creative-tab callback and factor-loot event listener; do not register them again independently
5. Correct the existing source magnetic-coil registration to `new Item.Properties().stacksTo(1)` in AcademyCraft. This source ModuleAbility limit was64 in the existing port but1 in 1.0.7; this stage deliberately did not edit production
6. Add a JavaExec check task for `cn.academy.port.survival.ClassicSurvivalDataRegressionTest`, using test runtimeClasspath, assertions enabled, dependsOn testClasses; include it in `check`. Native `AcademySurvivalRuntimeTests` follows existing academy namespace and in-memory runtime_empty fixture, seven tests in academy_survival batch
7. Copy this document and the manifest to production docs if desired. The staged generator/verifier expect their current stage location; do not relocate unchanged scripts and pretend their relative-path contract still holds

Do not copy `.javac` outputs or `.compile-classpath`. No Gradle build.gradle or production files were edited by this staged implementation. No external publication, commit/push or user's computer was used.

## Verification and bounded limits

- Cached Java21 javac against NeoForge21.1.252/Minecraft1.21.1 merged API: all5 production classes +7 native test methods compile (only source-compatible deprecated SetCustomDataFunction builder warning)
- Deterministic Java regression: PASS421 assertions covering exact17 recipe structures/yields/XP, source generation rules and actual atlas coverage for16 materials/5 blocks
- Independent read-only Python audit: PASS527 assertions covering all49 source declarations, all54 annotated fields, every staged JSON schema/reference, canonical worldgen tuples, forbidden shortcut outputs, exact stone-only features, modern singular directories and actual atlas coverage
- Native GameTests: written/compiled, NOT launched by this staged implementation. Tests cover native registrations/mining tiers, all14 crafting recipes plus insufficient-cell rejection,3 smelting recipes, seeded normal/Fortune/Silk Touch loot, actual stone ore placement, config/dimension/anchor-count checks, weighted category chest loot and reload idempotency
- Native generation existence uses at most64 fixed seeds per ore into a controlled stone region; height-boundary sampling uses128 fixed seeds per feature. These are bounded smoke/parity checks, not an exhaustive distribution proof
- Factor generation coverage uses512 fixed seeds per chest; native loot64 fixed seeds per tool mode/ore. No broad mod-compatibility or old-world migration pass is claimed
- Not run here: aggregate Gradle check, dedicated server, GameTest server, production jar packaging, real creative/inventory atlas rendering, natural multi-chunk ore survey, external-mod compatibility or full survival completion

## Actual integrated m06 result

The full build and all seven native survival tests executed and passed in the76-test suite. All17 recipes loaded natively, source crafting/smelting/loot/feature/config/chest behavior was exercised. A generation-stage heightmap is primed by the owned native feature fixture; it is not a production-world alteration. Original material/block names are now adapted for all four source languages, including Japanese and Traditional Chinese. Actual live material/ore placement rendering and ordinary new-world chunk distribution remain unverified.

Loot assembly uses the supported NeoForge access-transformer mechanism: https://docs.neoforged.net/docs/advanced/accesstransformers/ . Only LootTable.pools, LootPool.entries and LootItem.item are exposed for this purpose.
