package cn.academy.port.gametest;

import cn.academy.port.highpower.VanillaMiningContract;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native registry/tag/resource checks for NEW233's separately scoped admission contract. */
@GameTestHolder("academy_point_meltdown_runtime")
@PrefixGameTestTemplate(false)
public final class HighPowerPointMiningContractRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_point_mining_contract";
    private static final Set<Block> SOIL = Set.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
            Blocks.MUD, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM);
    private static final Set<Block> EXTRA = Set.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.OAK_PLANKS);
    private HighPowerPointMiningContractRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void point_contract_admits_exact_ten_blocks_without_widening_old_seven(GameTestHelper h) {
        int pointCount = 0, originalCount = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            var state = block.defaultBlockState();
            boolean original = SOIL.contains(block), point = original || EXTRA.contains(block);
            h.assertTrue(VanillaMiningContract.reviewedBlock(state) == original, "original exact-seven whitelist: " + block);
            h.assertTrue(VanillaMiningContract.reviewedPointBlock(state) == point, "point exact-ten whitelist: " + block);
            if (original) originalCount++;
            if (point) {
                pointCount++;
                Item expected = block == Blocks.STONE || block == Blocks.COBBLESTONE ? Items.COBBLESTONE
                        : block == Blocks.GRASS_BLOCK || block == Blocks.PODZOL || block == Blocks.MYCELIUM ? Items.DIRT : block.asItem();
                h.assertTrue(ItemStack.matches(VanillaMiningContract.expectedPointDrop(state), new ItemStack(expected)),
                        "one canonical capacity item: " + block);
            } else h.assertTrue(VanillaMiningContract.expectedPointDrop(state).isEmpty(), "unreviewed point target predicts no drop");
            if (!original) h.assertTrue(VanillaMiningContract.expectedDrop(state).isEmpty(), "old drop predictor cannot borrow point whitelist");
        }
        h.assertValueEqual(originalCount, 7, "original block count");
        h.assertValueEqual(pointCount, 10, "point block count");
        h.assertFalse(VanillaMiningContract.reviewedPointBlock(null), "absent point state refused");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void point_contract_uses_native_tool_baseline_and_accepts_last_durability(GameTestHelper h) {
        for (Block stone : List.of(Blocks.STONE, Blocks.COBBLESTONE)) {
            var state = stone.defaultBlockState();
            h.assertFalse(VanillaMiningContract.reviewedPointHarvestTool(state, ItemStack.EMPTY), "stone requires native correct tool");
            for (Item wrong : List.of(Items.IRON_SHOVEL, Items.IRON_AXE, Items.IRON_HOE))
                h.assertFalse(VanillaMiningContract.reviewedPointHarvestTool(state, new ItemStack(wrong)), "wrong reviewed tool refused before wear");
            for (Item pickaxe : List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                    Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
                ItemStack tool = new ItemStack(pickaxe);
                h.assertTrue(VanillaMiningContract.reviewedPointHarvestTool(state, tool), "native pickaxe tier admitted: " + pickaxe);
                tool.setDamageValue(tool.getMaxDamage() - 1);
                h.assertTrue(VanillaMiningContract.reviewedPointHarvestTool(state, tool), "original last-use snapshot remains harvest-capable");
            }
        }
        for (Block block : SOIL)
            h.assertTrue(VanillaMiningContract.reviewedPointHarvestTool(block.defaultBlockState(), ItemStack.EMPTY), "soil retains empty-hand native harvest");
        h.assertTrue(VanillaMiningContract.reviewedPointHarvestTool(Blocks.OAK_PLANKS.defaultBlockState(), ItemStack.EMPTY), "oak planks retain empty-hand native harvest");
        h.assertTrue(VanillaMiningContract.reviewedPointHarvestTool(Blocks.OAK_PLANKS.defaultBlockState(), new ItemStack(Items.IRON_SHOVEL)), "oak planks do not invent an axe requirement");
        for (Item item : List.of(Items.STICK, Items.DIAMOND_SWORD))
            h.assertFalse(VanillaMiningContract.reviewedPointHarvestTool(Blocks.OAK_PLANKS.defaultBlockState(), new ItemStack(item)), "unreviewed item remains refused");
        ItemStack named = new ItemStack(Items.IRON_PICKAXE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("unreviewed"));
        h.assertFalse(VanillaMiningContract.reviewedPointHarvestTool(Blocks.STONE.defaultBlockState(), named), "noncanonical components refused");
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        h.assertFalse(VanillaMiningContract.reviewedPointHarvestTool(Blocks.STONE.defaultBlockState(), silk), "silk touch cannot change expected cobblestone");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void point_contract_exact_resources_reject_copy_missing_provenance_and_tampering(GameTestHelper h) throws java.io.IOException {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!SOIL.contains(block) && !EXTRA.contains(block)) continue;
            var state = block.defaultBlockState();
            var location = ResourceLocation.withDefaultNamespace("loot_table/blocks/" + BuiltInRegistries.BLOCK.getKey(block).getPath() + ".json");
            var resource = h.getLevel().getServer().getResourceManager().getResource(location).orElseThrow();
            byte[] bytes;
            try (var input = resource.open()) { bytes = input.readAllBytes(); }
            h.assertTrue(VanillaMiningContract.canonicalPointLootEvidence(state, resource.sourcePackId(), resource.knownPackInfo(), bytes), "exact point native loot resource");
            h.assertTrue(VanillaMiningContract.canonicalLootEvidence(state, resource.sourcePackId(), resource.knownPackInfo(), bytes) == SOIL.contains(block), "legacy loot evidence remains exact seven");
            h.assertFalse(VanillaMiningContract.canonicalPointLootEvidence(state, "file/copy.zip", resource.knownPackInfo(), bytes), "identical datapack copy is not canonical provenance");
            h.assertFalse(VanillaMiningContract.canonicalPointLootEvidence(state, "vanilla", Optional.empty(), bytes), "missing provenance cannot impersonate vanilla");
            h.assertFalse(VanillaMiningContract.canonicalPointLootEvidence(state, "vanilla", Optional.of(KnownPack.vanilla("bundle")), bytes), "different vanilla pack cannot impersonate core");
            bytes[0] ^= 1;
            h.assertFalse(VanillaMiningContract.canonicalPointLootEvidence(state, resource.sourcePackId(), resource.knownPackInfo(), bytes), "same-name modified native resource refused");
        }
        h.succeed();
    }
}
