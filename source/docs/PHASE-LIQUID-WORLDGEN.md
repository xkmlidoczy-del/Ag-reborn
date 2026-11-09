# Classic phase-liquid natural world generation

This is a staged implementation for AcademyCraft `1.0.7` → Minecraft `1.21.1` / NeoForge `21.1.252`. It does not modify the production source tree, run Gradle, start Minecraft, regenerate saves, or touch the user's computer. Integration and native-world verification remain the parent's work.

## Canonical source and preserved behavior

Both original implementations are available; there is no missing AcademyCraft source dependency:

- `.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/crafting/PhaseLiquidGenerator.java`
- `.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/crafting/WorldGenPhaseLiq.java`
- `.reference/AcademyCraft-1.0.7/src/main/java/cn/academy/crafting/ModuleCrafting.java`: `generic.genPhaseLiquid` defaults to `true`

The native feature preserves the original conditional and all generator-local random draw order:

1. Config disabled or dimension other than `Level.OVERWORLD`: reject without RNG draws or writes
2. One `nextDouble() < 0.3` chance per decoration chunk
3. On chance success, draw x offset `nextInt(16)`, y `5 + nextInt(30)`, then z offset `nextInt(16)`
4. Subtract 8 from x/z **before** descending through air at that shifted corner; descend while y > 5
5. Reject y ≤ 4, then subtract 4 to obtain the 16×8×16 volume's base
6. Draw `nextInt(4) + 4` ellipsoids, preserving the original six-double parameter order and loop ranges; union only cells where normalized squared distance is strictly `< 0.6`
7. Check all nonmask six-neighbor shell cells before any write. Reject liquid in the upper four levels; reject nonsolid non-phase blocks in the lower four levels. The lower exception compares the phase block's identity, including existing flowing states, rather than exact block-state equality
8. Fill masked local y 0..3 with the real phase block's default/source state, and local y 4..7 with ordinary `Blocks.AIR`; preserve write order and flag `2`
9. Restore dirt exposed beneath masked upper cells to grass/mycelium when skylight > 0
10. Check the complete 256-position plane at base y + 4 for ordinary native water freezing, as in the source. Phase fluid has no invented ice conversion

Starting heights remain **absolute Y 5..34**, including in the modern -64-bottom Overworld. The implementation does not expand the range to negative Y, add biome rarity, replace the original mask with vanilla `LakeFeature`'s `< 1.0` ellipsoids, add a barrier/lava shell, or introduce retrogen.

`0.3` is the chance of an attempt, not a guarantee that 30% of chunks contain a lake. The terrain/shell rejection and original shape still apply.

## Staged files

All six classes are in `src/main/java/cn/academy/port/fusion/phase/` within this staging slice:

- `PhaseLiquidGenerator`: native `Feature<NoneFeatureConfiguration>`, including the exact chunk chance/start draws
- `WorldGenPhaseLiq`: native block/material/biome adapter
- `PhaseLakeAlgorithm`: production-shared pure algorithm, including masks, atomic shell rejection, carving, soil recovery and freeze pass
- `PhaseLiquidRules`: production-shared pure dimension/config/start rules
- `PhaseLiquidRandom`: two-operation random interface, without extra draws
- `PhaseLiquidWorldgenConfig`: standalone common spec, default-true `genPhaseLiquid`

Related pure tests are in `src/test/java/cn/academy/port/fusion/phase/`.

The three JSON files intentionally live beneath the owned phase directory's `datapack/` folder. Their canonical relative resource paths are:

- `data/academy/worldgen/configured_feature/phase_liquid.json`
- `data/academy/worldgen/placed_feature/phase_liquid.json`
- `data/academy/neoforge/biome_modifier/phase_liquid.json`

At integration, copy the **contents of** `phase/datapack/` into the production `src/main/resources/` root, preserving these relative paths. Do not package them as Java-directory files or copy a leading `datapack/` into resources.

## Narrow common integration

`ClassicFusion` owns the actual phase fluid, block, matter-unit collection and machine registrations. The feature takes that genuine block's deferred supplier; it does not create a proxy item or synthetic fluid.

Add/use the feature deferred register and holder in `ClassicFusion`:

```java
public static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, "academy");
public static final DeferredHolder<Feature<?>, PhaseLiquidGenerator> PHASE_FEATURE =
        FEATURES.register("phase_liquid", () -> new PhaseLiquidGenerator(PHASE_BLOCK));
```

Add these registrations to `ClassicFusion.register(bus, container)`:

```java
FEATURES.register(bus);
container.registerConfig(ModConfig.Type.COMMON, PhaseLiquidWorldgenConfig.SPEC,
        "academy-phase-liquid-worldgen.toml");
```

The classes use `net.minecraft.world.level.levelgen.feature.Feature`, `net.minecraft.core.registries.Registries`, NeoForge `DeferredRegister`/`DeferredHolder`, and the staged phase classes. There is no change to the existing shared `ClassicWorldgenConfig` spec and no custom placement-modifier registry.

The optional two-argument constructor also accepts a `BooleanSupplier` if common configuration is intentionally reorganized later. The one-argument constructor uses the standalone phase spec agreed with the parent.

### Why the placement list is empty

Cached `ChunkGenerator.applyBiomeDecoration` creates the distinct per-step feature index set and invokes each distinct placed feature once for the chunk. Cached `PlacedFeature.placeWithContext` starts from that chunk's origin and applies modifiers only when listed. Thus `"placement": []` passes one chunk-origin invocation to `PhaseLiquidGenerator`, which does the source chance and x/y/z draws itself.

Do not add a count, rarity, in-square, height-range or biome filter. Integer `RarityFilter` cannot represent the exact `0.3` chance, and splitting x/z/y into standard modifiers changes the source draw order. A biome filter at the undecorated modern origin Y=-64 would additionally introduce a restriction absent in the old dimension-only generator.

NeoForge `AnyHolderSet` supports `"biomes": { "type": "neoforge:any" }`, verified from cached `21.1.252` source. This adds the feature to all registered biomes, preserving generation in modded Overworld biomes without guessing tags. The feature itself immediately rejects Nether, End and custom dimensions before consuming random values. Modern decoration step `underground_ores` is declared explicitly.

## Survival acquisition and remaining adapter limits

The lower mask places `PHASE_BLOCK.defaultBlockState()` (source/legacy metadata 0). Integration must provide the parent's real `ClassicPhaseBlock`/source fluid with genuine **matter-unit** collection, matching the original material path. `BlockImagPhase` registers the phase material with `ItemMatterUnit`; the parent's adapter deliberately has no invented vanilla bucket support. Worldgen does not create a direct filled-item recipe, command grant, unlimited dispenser, fake fluid or alternate acquisition shortcut. Its natural source-block path is suitable for collecting phase liquid in Survival once the parent integrates and verifies the actual fluid adapter and resources.

The port preserves algorithm-local behavior, not identical worlds or locations for the same old seed. Modern feature seeding, vanilla terrain, population ordering, dimensions and cave biomes differ from Minecraft 1.7.10. FML world-generator priority `1` is not a modern feature-order API and is not claimed reproduced exactly.

Specific native bridges are explicit rather than hidden:

- `BlockState.liquid()` / `isSolid()` are the same legacy-like material predicates used by modern vanilla `LakeFeature`. Both APIs are deprecated in 1.21.1, but intentionally used here instead of inventing tags, full-collision checks, waterlogged-state rejection or broad replaceable rules. Different modern block material definitions can affect shell acceptance
- Air descent uses native `isEmptyBlock`; carving uses regular `AIR`, not modern vanilla lake `CAVE_AIR`
- Modern biomes no longer expose the old `BiomeGenBase.topBlock`. Dirt restoration samples the biome at `WORLD_SURFACE_WG` height (the old biome lookup was column-based) and recognizes vanilla `MUSHROOM_FIELDS` as the mushroom-island/mycelium equivalent; other biomes produce grass. Arbitrary modded custom surface-rule/mycelium parity is not claimed
- The freeze pass uses `Biome.shouldFreeze(level, position, false)`, matching the lake-style check without a shoreline-only restriction. Modern temperature/light/fluid predicates differ with the modern world; the old Minecraft `World` implementation is not bundled in this reference checkout. The 1.0.7 mod's complete freeze-plane loop is preserved and tested independently of the bridge
- Worldgen writes intentionally keep original flag `2`. No new fluid ticks, replace-protection tags or retrogen are introduced by this generator; the native fluid/block adapter controls normal fluid behavior

## Verification completed

No Gradle, Minecraft/native server, client, desktop computer, network or publication action was run for this subtask.

1. Pure `PhaseLiquidWorldgenRegressionTest` passed **1,126,230 assertions** using isolated cached JDK 21 javac/java. It checks 10,000 source-start/RNG-tail comparisons, 256 source mask/adjacency seeds, 64×10 complete world scenarios, shifted-corner descent, absolute-height behavior, atomic liquid/nonsolid rejection, existing phase-boundary acceptance, exact source writes/order, both fluid/air halves, grass/mycelium/skylight behavior and complete freeze-plane behavior
2. Pure `PhaseLiquidWorldgenDataRegressionTest` passed **14 assertions** checking the three native datapack identities, `NoneFeatureConfiguration`, empty placement, `neoforge:any`, single feature and declared step
3. All six native classes compile with isolated cached JDK 21 against `compiledWithNeoForge_9b400f53e3f669c282ff2a4cfff073f1a13340bb_output.jar` and cached dependency JARs. Its cache metadata confirms NeoForge `21.1.252`. The only warnings are the two intentionally preserved deprecated block-state material predicates
4. All three staged JSON files parse and pass exact static schema/identity checks

These are focused staged-source checks, **not** a full build, codec bootstrap, successful world generation in a native save, real matter-unit collection, or a completed Survival Fusor progression test.

### Pure regression commands (from repository root)

```bash
OUT=$(mktemp -d /tmp/academy-phase-tests-XXXXXX)
PHASE=.staging/crystal-fusion/src/main/java/cn/academy/port/fusion/phase
JAVA_HOME="$PWD/.tools/jdk-21.0.12.1+1"
GSON=$(find .gradle-user/caches/modules-2/files-2.1/com.google.code.gson/gson -name 'gson-*.jar' | head -1)
"$JAVA_HOME/bin/javac" -proc:none -cp "$GSON" -d "$OUT" \
  "$PHASE"/PhaseLiquidRandom.java "$PHASE"/PhaseLiquidRules.java "$PHASE"/PhaseLakeAlgorithm.java \
  .staging/crystal-fusion/src/test/java/cn/academy/port/fusion/phase/*.java
"$JAVA_HOME/bin/java" -cp "$OUT" cn.academy.port.fusion.phase.PhaseLiquidWorldgenRegressionTest
"$JAVA_HOME/bin/java" -cp "$OUT:$GSON:$PHASE/datapack" \
  cn.academy.port.fusion.phase.PhaseLiquidWorldgenDataRegressionTest
```

After promotion, put the production resource root/output on the data test's classpath instead of the staging `phase/datapack` path, and include both main-method regression tests in the aggregate check wiring.

## Parent's next native verification

When native checks are authorized, verify a newly generated Overworld region contains phase sources without a command/recipe acquisition shortcut, actual collection into an empty matter unit yields the real phase material variant, and that collected fluid serves the parent's finite matter-unit/Fusor paths. Verify vanilla bucket interaction does not destroy an unsupported source. Verify the dimension/config gates using the actual configured/placed feature and load the JSON codecs in the production build. Test existing chunks remain unchanged. Reuse the original chance rather than inflating worldgen frequency for production; a temporary deterministic feature test must be labeled as a test, not natural acquisition proof.
