# Source-faithful crystal fusion and Normal developer acquisition

## Result and exact survival obtainability

This increment closes a real source dependency chain:

1. Existing ordinary source ore/smelting/material recipes provide constraint
   plates, glass, low crystals, chips, a machine frame and the converter
2. Original recipe17 crafts **four empty matter units**, stack limit16
3. The real Overworld phase-liquid feature attempts source lakes with probability
   0.3 per new chunk, at the original absolute starting Y5..34; source blocks are
   collected with an empty matter unit, consuming one block and one empty unit
4. All three original Fusor recipes23/24/25 craft the functional source machine
5. An ordinary empty energy unit or portable developer can be charged in the
   already genuine solar generator and inserted into the Fusor's energy slot
6. One low crystal +3000mB phase liquid becomes one normal crystal; one normal
   +8000mB becomes one pure crystal
7. The source-inert matrix core has all three authentic variants and recipes
   28/29/30. Both original Normal developer recipes44/45 now have real crafting
   dependencies and are enabled

**Normal crafting is closed; naturally powering Normal learning is still open.**
Original `TileDeveloper` has no item-energy pull loop. Authentic solar power
transfer to a crafted Normal machine still needs the real wireless matrix/node
network, or source optional converter integration with another supported energy
mod. No battery-pull slot, adjacency shortcut, free IF, substitute recipe or
inactive matrix/node shell is invented here. Advanced developer crafting still
needs the functional standard wireless node tier1; it remains deferred.

## Preserved Fusor behavior

Canonical source:
`AcademyCraft1.0.7/crafting/block/{TileImagFusor,BlockImagFusor,ContainerImagFusor}.java`,
`crafting/api/ImagFusorRecipes.java`, `crafting/ModuleCrafting.java`,
`core/block/TileReceiverBase.java`, and `energy/IFConstants.java`.

- Native IF buffer2000, source bandwidth50, finite and double-valued
- Real imagProj tank8000mB; only that source fluid is accepted
- Five raw slots: crystal input0, crystal output1, phase input2, energy input3,
  empty-container output4
- Native menu registration order0/1/2/4/3 and exact source coordinates:
  (13,49), (143,49), (13,10), (143,10), (42,80)
- Top accessible0/2; bottom1/4/3; other sides3; sided extraction only downward
- Work check starts every10 idle ticks; work runs before matter/energy exchange
-12IF is pulled every work tick. The exact source `1.0/120` double increment
  crosses1 on **tick121**, costing1452IF per successful recipe
- Low input→normal output consumes3000mB once; normal→pure consumes8000mB once
- Source short-circuit order retained: bad/missing input aborts before energy;
  low liquid or wrong output type aborts after power pull; same-type full or
  mismatched-component output blocks progress but still consumes source energy
- Finished work sets check cooldown0, permitting immediate next-tick restart
- Each matter-unit tick exchanges1000mB and returns one empty container
- Energy slot transfers only finite genuine source item storage, honoring item
  bandwidth: energy-unit20IF/tick, portable50IF/tick
- Save preserves the actual five item payloads, energy and fluid. Source recipe/
  progress is deliberately **not persisted**, so reload begins a new10tick check
- The machine drops itself and its real inventory once. Retained removed native
  energy/fluid endpoints cannot mint power or fluid
- Source hardness3, stone-pick harvest tier, cardinal placement, nonoccluding
  all-face source cube, active light6 and four source front frames at400ms/frame

Modern safety/integration corrections are explicit: source GUI transfer indices
accidentally classified its empty-output position as the energy destination.
This port preserves raw/menu slot ordering while directing energy to raw3/menu4.
Outputs are extract-only, and automation rejects wrong matter/unsupported power,
closing the original inherited-all-items slot validation loophole. It never
converts an arbitrary hopper item into phase liquid. Invalid fluid/energy saves
are bounded and wrong-fluid drain requests are rejected.

## Genuine phase fluid and containers

The source fluid's chained setters finish at density1, viscosity6000,
temperature0 and type luminosity8. Its block uses the real source light formula
5/2/0 for metadata0/1/2. Tick delay is6000/200=30; source surface is0.875.
The original Forge3-quanta decay/downflow/shortest-flow/displacement algorithm is
ported, rather than leaving inherited native eight-quanta water ticking. Downflow
has metadata1/quanta2; lateral source flow reaches two cells; it never regenerates
sources. Scheduled fluid ticking and legacy block random ticking dispatch once
per sample, avoiding modern block/fluid double random updates.

`PHASE-LIQUID-WORLDGEN.md` and `PHASE-FLUID-FLOW.md` detail the pure algorithms,
modern material/biome bridges and native-world limits. Vanilla buckets are not a
registered source container and cannot delete this fluid. Matter-unit raycasts
collect source cells only, following original Forge source-only collision.
Capture/place posts the modern `MatterUnitHarvestEvent`; achievements are a
separate unported consumer. Filled placement checks native edit/replaceability
permissions instead of destroying an obstructing neighbor block.

NeoForge native item-fluid capabilities preserve the original all-or-nothing
1000mB container registration. Empty matter→phase matter and back swap one
container. Empty source energy-unit +1000mB becomes full10000IF; a full source
energy-unit drains1000mB and becomes empty. Simulation changes nothing. Partial
IF is not a full fluid unit, partial fluid is not a complete container and
stacked containers cannot duplicate payloads. This is an explicit finite-energy
hardening of old Forge FluidContainerRegistry's item+damage-only matching: its
rounded damage0 could classify a partially charged unit as1000mB full, allowing
a drain/refill loop to regenerate spent IF. Exact0/10000 endpoints prevent that
unbounded cycle, as required by this port. No unrelated energy-unit right-
click injector is introduced; the capability is the modern fluid-container seam.

## Source visuals and honest UI limits

Actual original PNG/OGG assets are used: all machine faces, four working frames,
phase black base, three scrolling phase overlays, matter-unit base/mask/materials,
three matrix-core icons, inventory/Fusor page, progress strip, histogram and the
source loop sound (volume0.6, playing only during unblocked work).

The live phase overlay keeps original distance alpha1/(1+0.2*distance), cut-off0.1,
1.2sqrt(height), layer heights(-0.3,0.35,0.7), exact scroll velocities and density0.7,
alpha blend, no culling, no depth test and no depth writes. The fluid stack icon
still uses source phase_liquid, while world fluid faces use source black.
The native screen keeps source item-slot layout, idle/required-fluid text,
progress and real energy/liquid histograms with the original source colors.

Matter material scrolling is a source-alpha-mask composite on32 pixel-aligned
atlas frames, with6/7tick durations totaling the source10second period. It is a
modern sprite adapter, **not the original continuously interpolated GLSL mask**;
its game-tick clock pauses differently and its held-item scroll differs from the
old static equipped overlay. Native fluid corner interpolation, item extrusion,
text/font rasterization and the information-panel framing likewise are not
claimed pixel-identical. The source wireless link/name page cannot be functional
until the genuine network is ported. Native clicks work; no fake link controls are
shown. Live fusion screenshots, sound and pixel comparison remain unverified.

## Verification and runtime fixture provenance

`verify-crystal-fusion.sh` performs isolated cached JDK21/API compilation only;
it does not invoke Gradle or start Minecraft. All staged main classes, five
native GameTests and the copied common constructor integration compile against
Minecraft1.21.1 / NeoForge21.1.252.

Passed deterministic tests:
-42 exact work-order/energy/fluid/progress assertions
-142 source-recipe geometry/yield/model/animation assertions
-1,126,230 literal source phase-lake/RNG/order/boundary assertions
-14 native phase datapack schema/scope assertions
-12,412 Forge flow oracle assertions, including4,096 differential worlds

The isolated staging validation compiled these native tests. Subsequent integrated validation executed all five fusion/acquisition tests successfully, within the complete120-required-test suite. Actual fusion client GL/UI and physical collection/workflow remain unverified at this checkpoint. They cover source menu/raw/sided
slots, genuine fluid/item capabilities, finite1000mB energy-unit exchange,
NBT/progress/reset/removal drops and exact registered fluid flow/light. The wide
acquisition test uses an explicitly seeded solid lake shell and source feature
seed4096, calls the **registered real Feature**, then uses actual matter-unit
right-clicks to collect14generated liquid blocks. It validates four real matter
recipes, authentic Fusor/basic-core recipes, and powers all three fusion jobs
from actual finite solar charging of an empty unit: two low→normal jobs plus one
normal→pure job consume4356IF and14000mB. The reserved genuinely upgraded normal
crystal enters the real Normal recipe. Ordinary source-material crafting inputs
are declared fixtures; the earlier solar survival test separately verifies the
mined/smelted material→converter→solar chain. Direct server tick loops and FakePlayer
recipe assembly do not claim physical furnace waiting, human GUI clicks, socket
transport, terrain exploration, unseeded natural lake discovery or a full
unchanged survival playthrough. No fluid or charged battery fixture is granted.

For further validation, run serial aggregate checks, native GameTests, fresh-chunk natural
world discovery/collection and live client visual/audio/interaction QA after
integration. Any failed native acquisition edge must be fixed before calling
that path verified. The isolated validation changed no production files or saves and ran no Gradle/game/client process. The later integrated aggregate build and native suite are recorded in runtime-evidence/m09-fusion-final-validation.log.

## Integration

1. Review `docs/fusion-integration.patch`; its base hashes are recorded in
   `integration-base-sha256.json`. It preserves all existing registrations/tasks
   and appends only this slice's constructor hook, five checks, source tags,
   translated native names, recipe statuses, corrected Normal prerequisite
   documentation and historical Forge provenance notices
2. Promote staged `src/main/java`, `src/test/java` and `src/main/resources` by the
   frozen handoff manifest. Existing identical source assets may simply be kept
3. Promote this document plus phase worldgen/flow and legacy Forge notices into
   production docs. Apply the shared patch with a dry run first; do not replace
   shared tags/language/build files with only the new entries
4. Run aggregate `check` and native `academy_fusion` + `academy_fusion_acquisition`
   batches in isolated directories, then client QA. No external publication is
   authorized by this increment

All source additions remain under their appropriate original notices. Historical
Forge-derived portions retain the Minecraft Forge Public Licence1.0; they are not
silently relicensed as current NeoForge LGPL. See LEGACY-FORGE-FLOW-NOTICE.md.
