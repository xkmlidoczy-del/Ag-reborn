# Classic imaginary phase liquid flow

This staged increment replaces modern water-style flow ticking with the original
Forge 1.7.10 `BlockFluidClassic` algorithm configured by AcademyCraft 1.0.7. It
uses the genuine registered native source/flowing fluid states and liquid block.
It does not add a proxy fluid or claim full fluid rendering/entity-motion parity.

## Source basis

- AcademyCraft 1.0.7 `cn/academy/crafting/block/BlockImagPhase.java`: three quanta
- AcademyCraft 1.0.7 `cn/academy/crafting/ModuleCrafting.java`: chained density
  setters end at density **1**, viscosity **6000**, temperature **0**, luminosity **8**
- [Forge 1.7.10 BlockFluidClassic](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/src/main/java/net/minecraftforge/fluids/BlockFluidClassic.java):
  update order, decay, flow eligibility, shortest-path search, displacement calls
- [Forge 1.7.10 BlockFluidBase](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.7.10/src/main/java/net/minecraftforge/fluids/BlockFluidBase.java):
  density direction, viscosity/200 delay, movement/density displacement rules,
  drop behavior, placement/neighbor tick scheduling and random ticking

The local AcademyCraft reference is the project's already extracted original
1.0.7 source. The Forge files above were checked directly from the official
MinecraftForge repository, rather than inferred from modern vanilla water.

## Pure engine and native entry point

`cn.academy.port.fusion.flow.ClassicPhaseFlowAlgorithm` is independent of Minecraft.
Its `World` exposes cells with old material/displacement/density semantics and
records state writes, drops, neighbor notifications and scheduled updates.

`cn.academy.port.fusion.flow.ClassicPhaseFlow.tick(Level, BlockPos)` bridges those
operations to `ClassicFusion.PHASE_BLOCK`, `LiquidBlock.LEVEL` and the fluid already
present in the block state's `getFluidState()`. Native scheduled updates use that
state's actual fluid type and a 30-tick delay. Client/stale non-phase calls return
without creating liquid. Every produced native state has metadata 0, 1 or 2.

Integration requires `ClassicPhaseFluid.tick(Level, BlockPos, FluidState)` to call
this entry point instead of `super.tick`. The native fluid/block metadata mapping
must retain the three-quanta states, including downward metadata **1**, rather
than introducing modern falling metadata **8**. The engine tolerates old metadata
8 when reading a pre-existing state and follows the original decay calculation.

## Preserved details

- Source metadata0 has three quanta; flowing metadata1/2 has two/one quanta
- No source regeneration from adjacent sources
- Any phase directly above or one horizontal step above a non-source restores
  it to two quanta, regardless of the above cell's metadata
- Otherwise the strongest positive horizontal quanta minus one determines decay
- Decay happens before vertical flow. A cell cleared to air can still place
  metadata1 liquid below during that same tick, exactly as the original did
- Vertical flow always places metadata1 and returns immediately
- Flat-floor lateral flow from a source reaches metadata1 then metadata2, with
  maximum reach two; direct phase above resets lateral placement to metadata1
- Non-sources flowing vertically do not spread sideways. Sources still can
- Existing phase participates in flow-path search but cannot be displaced;
  stronger nearby phase affects it when its own next tick computes decay
- Search tests directions in -X,+X,-Z,+Z order, excludes immediate backtracking,
  recurses through depth4, returns all cheapest ties, and retains the all-1000
  tie even when every direction is blocked. Placement rechecks displacement
- Metadata decay writes use flags3, schedule30, then notify neighbors; source
  normalization uses metadata0/flags2. New flow placement schedules30 to model
  the old `onBlockAdded` scheduling callback

## Displacement bridge

The pure eligibility rules preserve the original distinction:

- `canFlowInto`: air and own phase are allowed; then explicit overrides; then
  movement, water, lava and portal block flow; finally compare density
- `canDisplace`: air is allowed and own phase rejected; then explicit overrides;
  then movement and portal block displacement; finally compare density
- Non-classic-fluid density is `Integer.MAX_VALUE`. Therefore **vanilla water
  and lava block lateral path selection but can be displaced vertically**;
  substituting modern water/lava fluid-type densities would change the source
- Explicitly allowed displacement and ordinary non-fluid displacement call
  the block's drop hook before replacement. Density-based foreign-fluid
  displacement does not call that hook

The native bridge recognizes doors, signs (including their modern variants) and
sugar cane as the original explicit-false blacklist. It uses native legacy
`blocksMotion()` with explicit thin/open wood/rock/cloth corrections for buttons,
pressure plates, carpet, fence gates and trapdoors. Nether/end portals are
rejected; the new end gateway is treated as a portal equivalent. Water/lava use
native fluid tags. A foreign `BaseFlowingFluid` exposes its registered fluid-type
density; vanilla liquids and non-fluid blocks retain the original MAX sentinel.

Minecraft 1.21 has no original `Material` table. The above is an explicit native
compatibility bridge, not a claim that every future third-party block or new
waterlogged block has an exact 1.7 material counterpart. Waterlogged modern
blocks are classified by their contained water/lava and their movement/blacklist
properties. Additional mod block-specific displacement overrides are not added.

## Random ticking

Original `BlockFluidBase` enabled random block ticking as well as scheduled
updates. The host's `ClassicPhaseBlock` should dispatch one random update to the
same entry point while leaving native fluid random ticking disabled. Enabling
both native block and native fluid random ticking without overriding the inherited
`LiquidBlock.randomTick` delegation would cause **two** updates per sampled
position: native `ServerLevel.tickChunk` separately calls block and fluid hooks.

## Verification

- Standalone Java21 pure regression runner passed **12,412 assertions**
- It includes **4,096 seeded differential worlds**, comparing direction results,
  full changed cells, and ordered write/drop/schedule/notify events against an
  independent oracle retaining the original Forge variable/branch expressions
- Named cases cover 3-quanta mapping, no regeneration, isolated decay,
  decay-before-downflow, lateral reach, above/above-adjacent replenishment,
  source-vs-flowing vertical behavior, four-way direction order, depth4 reach and
  cutoff, ties, existing phase strengthening, material overrides and densities
- Both new main classes compiled with cached MC1.21.1/NeoForge21.1.252 APIs and
  existing staged/production classpath using direct `javac -proc:none`
- No Gradle task, Minecraft server/client, GameTest or production source mutation
  was used for this focused verification. Native runtime flow, chunk-border,
  drops/neighbor side effects and random-tick frequency still need runtime tests

Standalone test command (from repository root):

```sh
OUT=/tmp/academy-phase-flow-javac
mkdir -p "$OUT"
.tools/jdk-21.0.12.1+1/bin/javac -d "$OUT" \
  .staging/crystal-fusion/src/main/java/cn/academy/port/fusion/flow/ClassicPhaseFlowAlgorithm.java \
  .staging/crystal-fusion/src/test/java/cn/academy/port/fusion/flow/ClassicPhaseFlowRegressionTest.java
.tools/jdk-21.0.12.1+1/bin/java -cp "$OUT" \
  cn.academy.port.fusion.flow.ClassicPhaseFlowRegressionTest
```
