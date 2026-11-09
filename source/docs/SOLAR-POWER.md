# Source-faithful Solar Generator increment

Canonical baseline: AcademyCraft1.0.7 commit00d19ec0cf538f61c1095c9292f5ee6863db4521 and LambdaLib1.2.3, already present in `.reference`. Target: Minecraft1.21.1 / NeoForge21.1.252. New Java adapters retain GPLv3 source attribution; original LICENSE/NOTICE remain applicable. This is a private development increment.

## Implemented

- Real `academy:solar_gen` block/item/tile/menu, exact source nine-cell recipe and one half-height occupied block, source hardness1.5 and stone-pickaxe harvest tier
- Native finite1000IF double buffer; loaded server ticks generate3IF under clear exposed daytime,0.6IF under rain,0 at night or under an opaque roof; source daytime remainder is inclusive0..12500
- Real single recharge slot at42,81, native IF support only, generator request at most100IF/t, then each item's own bandwidth applies: energy unit20IF/t, portable developer50IF/t. Generation precedes recharge; rejected/full/unsupported power is retained. No infinite refill, fake placeholder, FE-only item acceptance or automatic neighboring-machine transfer
- Actual native inventory click/shift-click/menu reach handling; exact source hotbar/player-slot placement. No client-side authoritative battery write
- Original252-vertex/380-triangle solar OBJ, byte-identical source solar texture/icon and GUI assets. Exact source world transform: pivot(.5,0,.5), LambdaLib cardinal rotation then90°Y, scale0.014. Resource-pack reload replaces the mesh atomically and errors explicitly if missing
- Original `page_solar` inventory page textures, correctly indexed clear/stopped/rain three-frame status image, source-colored buffer histogram and generation information. Native menu transmits fractional IF without rounding through four16-bit data words
- Fractional buffer and recharge inventory persist in modern native block-entity NBT, supported battery payload persists on save and block break, removed/stale generator interfaces cannot provide energy. Native `ImagFluxGenerator` capability exposes source extraction arithmetic for a future real wireless network
- Source generator `getProvidedEnergy` returns a finite partial drain; caller is responsible for source bandwidth. No extra FE block capability or unported network is implied

## Exact source locations

Line references refer to the unchanged files under `.reference`:

- AcademyCraft `src/main/java/cn/academy/energy/block/TileSolarGen.java`:43–69 capacity/100IF bandwidth/day/weather/sky;75–83 superclass-generation-before-recharge order
- AcademyCraft `src/main/java/cn/academy/core/block/TileGeneratorBase.java`:35–54 finite buffer generation/sync;60–89 add/provide APIs;95–101 native battery charging via IFItemManager
- AcademyCraft `src/main/java/cn/academy/energy/IFConstants.java`:12–13 LATENCY_MK2=100
- AcademyCraft `src/main/java/cn/academy/energy/block/BlockSolarGen.java`:49–55 half-height/hardness/stone tool;79–87 ordinary non-sneaking activation
- AcademyCraft `src/main/java/cn/academy/energy/block/ContainerSolarGen.java`:12–23 exact recharge slot, native support transfer and player inventory
- AcademyCraft `src/main/java/cn/academy/energy/block/SlotIFItem.java`:20–27 IFItemManager-only support
- AcademyCraft `src/main/java/cn/academy/core/container/TechUIContainer.java`:19–33 exact inventory coordinates;35–38 original8-block/64-square reach measured from the integer block origin
- AcademyCraft `src/main/java/cn/academy/energy/client/render/block/RenderSolarGen.java`:20–29 original model/texture/scale0.014/Y90
- AcademyCraft `src/main/scala/cn/academy/energy/client/ui/GuiSolarGen.scala`:16–46 source solar page, three image frames, buffer histogram and generation speed
- AcademyCraft `src/main/resources/assets/academy/guis/rework/page_solar.xml`: sole-line main176×187, animation104×70 at56,23 with0.6scale; native battery/player-slot source layout
- AcademyCraft `src/main/resources/assets/academy/recipes/default.recipe`:43–47 exact glass panes/wafer/two converters/frame recipe; source recipe ordinal09
- AcademyCraft `src/main/java/cn/academy/energy/ModuleEnergy.java`:57–59 authentic solar identity;78–80 actual native energy-unit capacity/bandwidth
- LambdaLib `src/main/java/cn/lambdalib/multiblock/BlockMulti.java`:64–66 single initial origin;135–163 rotation/pivot tables;180–186 source NORTH/EAST/SOUTH/WEST yaw placement
- LambdaLib `src/main/java/cn/lambdalib/multiblock/RenderBlockMulti.java`:33–46 pivot/rotation; `RenderBlockMultiModel.java`:44–55 model rotation then scale

Actual source and asset SHA256 provenance is in `SOURCE-PROVENANCE.json`; byte-identical8-resource identity is exercised by `ClassicSolarDataRegressionTest`.

## Survival acquisition graph

All referenced source ingredients are genuinely functional or source-inert crafting materials, registered by existing survival/energy stages. There are no new placeholder components or substitution recipes.

Natural route:

1. Vanilla iron-pickaxe progression, Overworld ore mining at source heights0..59: resonance crystal ore yields resonance crystal; constraint-metal ore yields smeltable ore; imaginary-silicon ore yields smeltable ore; crystal ore yields low-purity crystal (1–2, source exclusive-upper-bound behavior)
2. Furnace-smelt constraint-metal and imaginary-silicon ore into their actual ingots; use source wafer, constraint-plate, reinforced-iron-plate, data-chip, calc-chip, info/brain-component and frame recipes
3. A low-purity crystal + three constraint plates + data chip makes a real EMPTY energy unit; calc chip / energy unit / resonance crystal makes an authentic consumed energy converter
4. Two converters + wafer + frame + three glass panes make Solar Generator. Another converter plus ordinary authentic components makes an EMPTY portable developer
5. Put the portable in exposed daylight solar recharge slot.3,334 loaded clear-day ticks (166.7seconds at20TPS) fill10,000IF and leave2IF in the generator, starting from zero. Rain requires slower charging; night/roof requires moving/removing obstruction or waiting for day
6. Remove/hold the powered portable; source initial acquisition completes5stimulations of26actualticks, costing3,900IF. A source factor from dungeon loot can select a category; otherwise it is random. An electromaster result can genuinely learn Arc Generation with3stimulations/78ticks, costing2,340IF. No developer commands or mastery/battery grants are required

One source-recipe shopping bill for Solar Generator + portable, using reinforced-iron-plate data chips and the resonance-crystal calc-chip alternate:18constraint-metal ore,1imaginary-silicon ore,4low-purity crystals,8resonance crystals,21iron ingots,33redstone dust,2gold nuggets,1glowstone dust and6glass blocks→16panes (4panes consumed). Includes source yields:14iron plates,12constraint plates; one spare constraint plate and12spare panes remain. Iron-pickaxe/tool costs, furnace fuel, vanilla glass/metal smelting, exploration and Nether/glowstone acquisition are additional ordinary vanilla requirements. Normal/pure crystals and Imag Fusor are not needed for this route.

### Native full-chain fixture

`AcademySolarRuntimeTests.mined_recipe_chain_empty_portable_solar_power_earned_category_and_arc` is written and cached-API compiled, awaiting the parent's runtime execution. It seeds source ore entries plus declared vanilla-mined/raw products (iron/gold raw materials, sand, redstone, glowstone) and an ordinary iron pickaxe; then:

- Uses real block loot and native source/vanilla furnace and crafting recipes, consuming real ingredient counts and preserving assembled item components across the entire dependency graph
- Obtains the initially EMPTY portable and actual solar only from their native final recipes; consumes the crafted solar by actual BlockItem placement
- Uses3,334actual serverTick calls under declared clear-day/sky fixture conditions to recharge the finite item, with no battery setter anywhere in this chain
- Seeds the FakePlayer's ordinary RNG(4096) and calls the unmodified `DevelopmentController.startLevel` path; category is only assigned by completed source `DevelopmentActions`, not granted. Completes5stimulations, then calls the real `startSkill("arc_gen")` path and completes3stimulations
- Expects earned level1 electromaster/Arc Generation and remaining3,760IF, accounting for exact6,240IF stimulation consumption

This proves a reproducible authentic recipe/power/development availability route when executed successfully. It does not claim an uncheated human playthrough, actual ore-worldgen search, timed furnace operation/fuel consumption, UI clicking, or a real client3334-tick waiting session. Those are declared verification boundaries, not hidden substitutes.

## Persistence and intentional safety adaptations

The old TileGeneratorBase did not write its private `energy` field to NBT; TileSolarGen only persisted inherited inventory and multiblock info. The new port deliberately persists buffer energy because user-required persistence should not discard earned power on reload. This is documented bug fixing rather than a false assertion of exact legacy save behavior. The source's client tick also tried slot charging; this port restricts all authoritative energy transfers to server ticks, so client prediction cannot duplicate stored IF. Nonfinite/negative saved storage and malformed requests cannot create energy. Source time modulo behavior is retained, including negative-time stopped remainders.

NeoForge's native common MenuProvider extension is unfortunately named `net.neoforged.neoforge.client.extensions.IMenuProviderExtension`; it contains only common inventory/network types and server-side writeClientSideData. Cold server-link tests allow this ONE exact engine interface while continuing to deny all real Minecraft, NeoForge and mod client types. No broad client-class allowance is introduced.

## Integration

1. Copy staged `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources` into production preserving paths. Existing copies of GUI/source textures have matching hashes; source-byte verification explicitly covers them
2. Apply `docs/solar-integration.patch` with`patch -p1`. It adds one solar registration call; exact native stone-tool/pickaxe tags; four locale solar-name/status keys; three deterministic Gradle test tasks; and the exact common MenuProvider-extension exception in two existing server-link tests. The patch dry-runs clean against the parent-integrated energy/machine/UI production snapshot recorded by `integration-base-sha256.json`
3. Do NOT replace shared production with entire `integration` overlays after unrelated edits. They are compilation inputs / narrow patch derivation only. Do not copy `.javac` output
4. Keep native GameTests excluded from the shipped JAR using the existing`cn/academy/port/gametest/**` exclusion
5. Parent owns serial Gradle check/build, actual native tests and client/socket sessions. Do not claim those passed from isolated javac. New native batches are`academy_solar` (3tests) and`academy_solar_survival_chain` (1end-to-end fixture)
6. Reconcile source recipe ordinal09/generator status and survival README after actual integration, preserving the limitations below

## Verification achieved here

No Gradle/Minecraft/client/server launch, user-computer access, external publication or production edit was performed. Cached JDK21 javac compiled all12new runtime/client classes and the native class containing4test fixtures (plus the registration overlay) against the pinned merged API. Isolated test script`./.staging/solar-power/scripts/verify-solar-power.sh` passed:

-100,055 arithmetic/storage/conservation/source-boundary/bit-exact menu-sync assertions
-13,706 original8asset/hash/source-recipe/252-vertex/380-triangle/four-rotation bounds assertions
-11common classes cold-linked with real client namespaces denied; exact engine-common MenuProvider extension exception disclosed

Native runtime: written/compiled, NOT executed here. Actual live GUI/world appearance and culling/light/resource reload, physical inventory UI shifts, sky/weather engine behavior, full-chain world fixture and two-process generator disk persistence remain parent-owned verification.

## Remaining source limitations

Wireless nodes/network connection pages, other generators, phase-liquid/container exchange, Imag Fusor tiers, achievements/tutorial unlocks and complete50skill parity remain unfinished. Solar buffer/native generator API does not manufacture wireless delivery or a direct neighboring-machine link. The native GUI reconstructs the original inventory/status/histogram information with original artwork but does not provide the unported wireless page or all old CGUI transitions/font behavior. World mesh rendering uses modern entity buffers with no culling to preserve the thin source panel faces; pixel/audio parity is not claimed. This increment closes one genuine direct-recharge survival route, not the full mod catalog.
