# M24: bounded natural starter route audit

This is a read-only source and current-production audit. It ran no Gradle, client,
server, GameTest, browser, or CUA session. Its Python accounting is not a physical
crafting, actual generated-world, natural survival, or new-JVM pass. Production
files were not edited. All generated artifacts are in this staging directory.

The m14 pristine world remains only a partial natural attempt: three collected
dirt, no wood, no mod material, no recipe, and no acquired category. M22's supplied
empty portable/Solar/factor charging and learning proof does not fill that gap.
M23's completed command/lifecycle work is outside this audit.

## Main findings

1. **A factor and loot chest are unnecessary.** First development without a factor
   chooses a random category, exactly as the original does. Do not turn a bounded
   starter test into a dungeon/temple/stronghold search merely to select a category.
2. **Learn the random category's root.** Electromaster: `arc_gen`; Meltdowner:
   `electron_bomb`; Teleporter: `threatening_teleport`; Vector Manipulation:
   `dir_shock`. Each is implemented, level 1, portable-supported, has no skill/EXP
   prerequisite, and costs three stimulations. Charging requires Arc Generation
   learned with 0.3 EXP, so it adds a mastery task to the starter test.
3. **The current recipe graph has a closed starter path.** No missing recipe,
   absent vanilla ingredient-tag member, or source harvest-tier mismatch was found
   on this route. This conclusion is static, not runtime validation.
4. **The old iron-only chip route is not the only source route.** Silicon pieces
   make the same data chip. Five additional naturally harvested silicon ore can
   replace fifteen iron ingots, reducing furnace work by ten smelts. Adapt to what
   the generated area actually contains; no recipe variant is globally fastest
   independently of seed, location, resources, and player actions.
5. **Glowstone is the significant vanilla detour.** One dust is mandatory for the
   info component. A natural witch can drop it without a Nether trip, but the drop
   can be zero. A known reachable natural Nether glowstone cluster gives a more
   predictable harvesting target. A diamond pickaxe is not required if the portal
   is cast in place with lava/water, or an existing natural portal is repaired.

## Exact device graph and primitive leaves

Both target devices require, in aggregate:

- 10 data chips, 5 calculation chips, 3 energy conversion components
- 3 empty low-crystal energy units, 11 constraint plates, 4 low crystals
- 1 machine frame, 1 brain component, 1 info component, 1 Solar wafer
- 4 glass panes, 2 gold nuggets, and 3 redstone in addition to the chips' 30 redstone

The 11 constraint plates require six recipe batches: 18 constraint ingots become
12 plates, with one plate left. Four panes require six sand smelts followed by one
16-pane batch, with twelve panes left. One raw gold becomes an ingot then nine
nuggets, with seven left.

Preferred **low-iron bounded** variant, all ten chips from silicon pieces and all
five calculation chips from resonant crystals:

| Primitive item | Devices only | Devices + one crafted iron pickaxe |
|---|---:|---:|
| `academy:constraint_metal_ore` | 18 | 18 |
| `academy:imag_silicon_ore` | 6 | 6 |
| `academy:crystal_low` | 4 | 4 |
| `academy:reso_crystal` | 8 | 8 |
| `minecraft:raw_iron` | 6 | 9 |
| `minecraft:raw_gold` | 1 | 1 |
| `minecraft:redstone` | 33 | 33 |
| `minecraft:sand` | 6 | 6 |
| `minecraft:glowstone_dust` | 1 | 1 |

This route uses six silicon ingots -> six wafers: five wafers -> ten silicon
pieces -> ten data chips; the sixth wafer goes into Solar. The six device iron
ingots make four reinforced plates for the frame. The resonant count is five for
calculation chips plus three that remain mandatory for conversion components.

The source-faithful ordinary-tool bootstrap adds:

- 3 logs -> 12 planks: 4 for crafting table, 3 for wooden pickaxe, 4 for two stick
  batches. This leaves 1 plank and 2 of the 8 sticks after wood/stone/iron picks
- 11 collected cobblestone: 3 for stone pickaxe, 8 for one furnace
- 3 raw iron above the device count for an iron pickaxe
- Ideally 5 coal for 40 furnace smelts, at 200 cooking ticks each. This is exact
  fuel capacity under uninterrupted burning and careful input switching. Physical
  input changes, empty inputs, torches, food, extra tools, or tunnel excavation can
  need more resources. Five coal is a lower bound, not a guaranteed field budget
- Food, shelter, lighting, access tunnels, and spare tool durability are situational
  additions. They are not fabricated as fixed source recipe leaves

One furnace takes 8,000 actual cooking ticks (400 seconds at 20 TPS) for those 40
items. This is ordinary bounded cooking work, not an instant recipe-manager
assembly. Extra naturally crafted furnaces can shorten elapsed time but add eight
cobblestone each and may change fuel batching.

### Adapt to the naturally found materials

| Silicon-chip count | Silicon ore | Device iron | Device smelts | Device crafting operations |
|---:|---:|---:|---:|---:|
| 0 | 1 | 21 | 47 | 42 |
| 2 | 2 | 18 | 45 | 43 |
| 4 | 3 | 15 | 43 | 44 |
| 6 | 4 | 12 | 41 | 45 |
| 8 | 5 | 9 | 39 | 46 |
| 10 | 6 | 6 | 37 | 47 |

Every row has the same other primitive leaves. Odd silicon-chip counts cannot
reduce silicon wafer batches; converting the next chip with the second piece
from that wafer is at least as resource-efficient. The old m13 row is reproduced
exactly, including its primitive totals. The all-iron variant uses fewer crafting
operations; the all-silicon variant uses less iron and fewer smelts.

For every calculation chip switched to the source quartz variant, replace one
resonant crystal with two quartz. Thus a Nether trip can make the all-quartz row
use 3 resonant crystals and 10 quartz. It does **not** eliminate resonant crystal
entirely, because the three conversion components each require one. This is a
tradeoff rather than an unconditional improvement: ten quartz ore blocks replace
five resonant ore blocks without Fortune, but may be convenient near glowstone.

`natural-starter-static-audit.json` contains all 36 combinations of the six
silicon-chip batch choices and six quartz-calculation choices, exact recipe
dependencies, craft counts, leftovers, and source hashes. `audit_route.py` reads
production recipes, recursively expands them, then replays each dependencies-first
inventory ledger from exactly the computed leaves. It does not synthesize a
Minecraft result or alter production.

## Real harvesting requirements and avoidable dead ends

| Natural block | Source tier | Current normal-tool drop | Quantity needed on low-iron/resonant route |
|---|---|---|---|
| Constraint metal ore | stone pickaxe | ore block, exactly 1 | 18 blocks |
| Imaginary silicon ore | iron pickaxe | ore block, exactly 1 | 6 blocks |
| Imaginary crystal ore | iron pickaxe | low crystals, 1..2 | 2..4 blocks to reach 4 crystals |
| Resonant crystal ore | iron pickaxe | resonant crystal, exactly 1 | 8 blocks |
| Iron ore | stone pickaxe | raw iron, 1 | 9 blocks including iron pickaxe |
| Gold ore | iron pickaxe | raw gold, 1 | 1 block |
| Redstone ore | iron pickaxe | dust, 4..5 | 7..9 blocks to reach 33 dust |
| Coal ore | wooden pickaxe | coal, 1 | at least 5 under the ideal furnace plan |
| Nether quartz ore, if used | wooden pickaxe | quartz, 1 | 2 per switched calculation chip |
| Glowstone | no special tool | dust, 2..4 without Silk Touch | 1 block suffices for the 1-dust recipe |

Counts are unenchanted, nonexploded block harvests; actual drop RNG can yield
surplus. A bare hand/wooden/stone tool that is below the required tier can destroy
an ore without yielding the needed item. The current `requiresCorrectToolForDrops`,
pickaxe tag, `needs_stone_tool` and `needs_iron_tool` assignments match original
tiers. Modern vanilla `incorrect_for_stone_tool` includes `needs_iron_tool`, so
the newer tool-component model does not accidentally make stone sufficient.

Original `setDropData(crystalLow, 1, 3)` and `setDropData(resoCrystal, 1, 2)` use
LambdaLib `rangei` with an **exclusive** upper endpoint. They mean 1..2 low
crystals and exactly one resonant crystal. The port's current base loot agrees;
do not budget 1..3 and 1..2 respectively.

The source and current port start ore placement at Y 0..59, replace only
`minecraft:stone`, and gate to the Overworld with ore generation enabled. Vein
starts can have a small geometric footprint around that interval. This is not
the modern deep deepslate distribution. A stone-rich Y ~8..40 area is a better
bounded search target than spending the run at Y -58. Chunk counts are generation
attempts, not guaranteed collectible ore counts: resonant 18, constraint 24,
crystal 48, silicon 22, with maximum configured vein sizes 4/4/3/4. Scan actual
generated chunks before promising a seed/location can satisfy the budget.

Concrete paths that add prerequisites or stop at the wrong item:

- Ordinary crystal and resonant ore harvesting gives the crystal item, not the
  ore block. The crystal-ore furnace recipe is source-valid but ordinarily needs
  a Silk Touch ore block; use direct low-crystal drops for the starter. Likewise,
  resonant-ore Metal Former refining requires the ore block, not its normal drop
- Medium/pure crystals are not ordinary worldgen drops. Their efficient 2/4-unit
  recipes require powered Imag Fusor progression, and do not replace the initial
  low-crystal route by assumption
- Metal Former processing yields can save later materials, but the machine and
  working power add their own prerequisites. No Metal Former, Fusor, phase liquid,
  wireless node/matrix, terminal, app, needle, magnetic coil, or factor is needed
  for this Solar/portable/root target
- Crafting energy units creates empty source units. Neither the units nor the
  portable should be treated as crafted full. Solar's native battery slot must
  supply actual IF before development
- Original custom recipe aliases such as `si_piece`, `wafer`, `calc_chip`,
  `cons_plate`, and `crystal0` resolve concrete mapped Items through
  `CustomMappingHelper` before OreDictionary fallback. Current concrete item
  ingredients here are source-faithful, not unexplained ingredient narrowing
- Original `plateIron`, `ingotIron`, `nuggetGold`, `dustRedstone`, and
  `dustGlowstone` resolve through OreDictionary. Current `c:` tags are preserved,
  and their native cached definitions contain vanilla iron/nugget/dust members;
  `c:plates/iron` contains the craftable Academy reinforced plate. The recipe
  route does not silently require another mod's plate/dust

## Glowstone choices and additional primitive leaves

The one-dust recipe is real. No Academy recipe removes it. Viable current vanilla
sources include:

1. A **naturally encountered witch**: its loot has glowstone dust 0..2 in a
   weighted pool. Encountering/killing one is not guaranteed to yield dust. The
   current 1.21.1 loot also gives redstone 4..8 in a separate pool. Do not spawn a
   witch or command a loot roll and label it natural
2. **Naturally generated Nether glowstone**: one ordinary harvested block drops
   2..4 dust and needs no iron/diamond harvesting tier
3. A naturally present **journeyman cleric** sells one glowstone block for four
   emeralds; an eligible wandering-trader offer sells it for two. Place then
   ordinary-break a legitimately bought block for dust. Trader availability,
   leveling/trading costs, emerald acquisition, and brewing-stand dependencies
   make these contingent alternatives rather than the shortest default
4. Bastion hoglin-stable chest loot can contain glowstone blocks. This still
   requires a Nether trip and a hazardous structure search, so it is not the
   bounded default

If neither a witch drop nor a useful trader is already available, plan a small
Nether material branch. A cornerless cast portal needs 10 obsidian positions,
formed in place from 10 lava sources plus reusable water, one bucket and ignition.
Using a crafted flint and steel adds 3 raw iron for the bucket, 1 raw iron for the
flint and steel, and 1 actual flint from gravel. A diamond pickaxe and three
diamonds are avoided; obsidian is created in place rather than mined into inventory.

For the low-iron variant, that means **13 raw iron total** including all device
iron, the iron pickaxe, bucket, and ignition; 44 smelts rather than 40. Ideally
six coal fuels 44 smelts. Gravel count is contingent because flint is random;
carry access/scaffold blocks as needed. Do not pretend ten cast obsidian are ten
naturally collected inventory items. A naturally repaired ruined portal can change
these additions, but cannot be assumed without an actual seed/location check.

An optional fuel optimization, if suitable natural lava is already reachable,
uses the same bucket: smelt the first three raw iron with two planks (3 smelts of
fuel capacity), craft the bucket, collect one extra lava source, and fuel the
remaining furnace work with one lava bucket (100 smelts; bucket returned). The
bootstrap then needs **4 logs** instead of 3, to cover the two extra fuel planks,
and no coal for cooking. The lava-source inventory and travel/setup must be
disclosed. This is not a free fuel grant. Using coal is simpler when natural coal
is abundant. The glowstone portal branch still needs its ten construction sources.

## Development after real crafting

Place the crafted Solar outdoors with sky visible above it. Solar generates at
day time 0..12500: 3 IF/tick in clear weather, 0.6 in rain, zero when stopped.
Insert the actually crafted empty portable into the real Solar battery slot.
No energy, category, level, skill, EXP, or factor grant should enter this route.

First acquisition takes 5 x 26 = 130 development ticks, consuming 3,900 IF.
Learning a level-1 root takes 3 x 26 = 78 ticks and 2,340 IF. Total: 6,240 IF.
An empty Solar plus empty portable needs at least 2,080 clear generating ticks
(104 seconds at 20 TPS) to produce exactly that amount. A full 10,000-IF charge
takes 3,334 clear generating ticks and leaves 3,760 IF after both successful
actions. Full charge is convenient but not a recipe/progression requirement.
Gameplay FPS is not the server tick rate; weather/night and unloaded chunks can
extend waiting. Save-and-quit then a new JVM must verify the genuinely learned
root and retained finite energy in the same bounded world.

## Recommended next checks

### A. Shortest next native generated-world check

Use a new disposable normal Overworld with recorded seed, normal configured
  generation, and `genOres=true`; preserve the m14 pristine save and the m22/m23
evidence worlds. Confirm the relevant root remains enabled in the actual skill
configuration. Generate and inspect one declared small region, for example a
4 x 4 chunk area near suitable terrain, initially scanning stone-bearing heights
and slight vein borders. Record actual target blocks, coordinates, biome,
generation configuration, and required tool/tag decisions. This must use real
chunk generation, not a stone GameTest fixture or explicitly placing ores.

Stop this discovery check when the actual region contains a usable material
budget and reachable tree/stone/sand/iron/fuel resources, or when the declared
region is exhausted and the result is an honest insufficient-location finding.
If glowstone needs the Nether, inspect a similarly bounded actual Nether arrival
region for safely reachable glowstone/quartz. Do not make this an unlimited seed
search or an unsupported claim that the first region must contain everything.

Then use the actual native survival player/game mode and drop rules to harvest
one real generated ore of each type with an iron pickaxe, plus constraint with
stone and a below-tier negative check on a separate block. Record tool source,
tool wear, pre/post block state, dropped item entities, picked-up inventory, and
tile/player NBT. For a native automated check, directly invoking native break or
pickup APIs is **API-level harvesting**, not a physical held-button/camera pass.
If tools are supplied to isolate the ore check, call it **supplied QA tools** and
do not claim a natural tool bootstrap. Its useful stopping condition is actual
generated ore -> correct real drops -> actual inventory, with negative-tier
evidence, rather than reproducing the already available static recipe matches.

### B. Bounded physical material -> recipe -> development continuation

Prefer a verified generated area with a reachable tree, exposed stone, sand, and
the recorded ore budget. Run native mining/crafting/furnace/solar/developer UI
through ordinary controls. A seed chosen after scouting is a declared chosen-seed
test. A pose/location/camera adjustment or teleport that avoids cloud input
trouble is declared setup, and proves material acquisition at that location;
it is not pristine unassisted exploration. Do not edit the original m14 save to
retroactively turn its partial result into a completed natural run.

For a complete natural-material chain, start empty (or preserve exactly the
existing m14 three natural dirt if explicitly continuing that attempt), punch
actual logs, craft the wood/stone/iron tool chain and furnace, mine natural
resources, and follow whichever source recipe variant fits them. Record every
additional resource or setup. Use the cast portal branch only if needed; accept
any random initial category and learn its root. Finish with one actual root use
and a cold JVM reload of persisted category, root learning, inventories, and IF.

If native bootstrap/input is still the main blocker, do not spend hours proving
generic vanilla travel. First finish A, then a tightly staged physical checkpoint
for real tree/tool/ore harvesting and actual furnace output. A later checkpoint
may consume the retained genuine outputs and complete device crafting. Keep the
provenance chain instead of seeding replacement materials. An all-natural end-to-end
claim waits until every relevant checkpoint is actually observed.

### C. Evidence that closes the open loop

Capture raw controls/screenshots at wood collection, crafted correct tool,
generated ore before mining, actual drop/pickup, furnace consumption/output,
source recipe output, empty portable, real solar increase, random acquisition,
root learning, and cold reload. Corroborate with pre/post inventory and player/
block NBT, save creation options, mod/JAR identity, seed/configuration, actual
logs, and explicit setup declarations. Distinguish physical steps, native API
steps, static checks, and supplied fixtures. Do not infer natural gameplay from
the ability state alone.

## Source pointers

- Original recipe variants and targets:
  `.reference/AcademyCraft-1.0.7/src/main/resources/assets/academy/recipes/default.recipe`
  lines 3/7/12/17/21/26/32/43/97/115/173/177/185/190/201/231/259
- Original harvest/drop definitions: source `crafting/ModuleCrafting.java`
  lines 71/76/81/86 and 200/201; `crafting/block/BlockGenericOre.java`;
  LambdaLib `util/generic/RandUtils.java:23`
- Original generation: source `crafting/world/ACWorldGen.java:35` and
  `crafting/world/CustomWorldGen.java:49`
- Original no-factor random acquisition: source
  `ability/develop/action/DevelopActionLevel.java:47`
- Current native recipe JSONs: `src/main/resources/data/academy/recipe/classic/`
- Current harvest/generation/loot: `src/main/java/cn/academy/port/survival/`,
  `data/minecraft/tags/block/`, and `data/academy/{worldgen,loot_table}/`
- Current random acquisition/energy: `develop/DevelopmentActions.java`,
  `DeveloperType.java`, `DevelopmentProcess.java`, and `solar/ClassicSolarRules.java`
- Current roots and ingress: `classic-skills.json`, `SkillAvailability.java`,
  `preset/PresetSkills.java`, `AcademyGameplay.java`, and `DevelopmentController.java`
- Exact cached 1.21.1 vanilla loot/recipes/tool/fuel/trade source names and SHA256s
  appear in `natural-starter-static-audit.json`. The two cached JARs were read as
  ZIPs; no new download or runtime process was used
