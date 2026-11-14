package cn.academy.port.highpower;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * A closed, reviewed runtime, not event-bus discovery. Equality checks cannot prove absence
 * of arbitrary compensating hook mutation. Other mods, injected code, custom data and
 * replacement native handlers require another reviewed adapter; no registration escape hatch.
 */
public final class VanillaMiningContract {
    public static final String ID = "academy:vanilla_beam_etching_harvest_1_21_1_21_1_252_v1";
    private static final Map<String, String> MODS = Map.of("minecraft", "1.21.1", "neoforge", "21.1.252",
            "academy", "0.1.0-m41-highpower.0.81.5-city-a3.63-mech.1");
    private static final Set<String> PACKS = Set.of("vanilla", "mod_data", "mod/neoforge", "mod/academy", "bundle", "trade_rebalance");
    private record BlockContract(Class<?> type, Item drop, String sha256) {}
    private static final Map<Block, BlockContract> BLOCKS = Map.of(
            Blocks.DIRT, new BlockContract(Block.class, Items.DIRT, "9222e5df0ffbb258af7ad3c42a563b432d46bdaf398bf505eccd1df89db24d25"),
            Blocks.COARSE_DIRT, new BlockContract(Block.class, Items.COARSE_DIRT, "fbe643552f776d9a6e3426201b57bd28cc42fac27ded0c254607c04f7a328910"),
            Blocks.ROOTED_DIRT, new BlockContract(RootedDirtBlock.class, Items.ROOTED_DIRT, "80305963f97b89b640adb2840a332bd6ef877a3ce27e490ac060d440273d9a2b"),
            Blocks.MUD, new BlockContract(MudBlock.class, Items.MUD, "c6b57c6bdd13d739768a527f277ea2fc2d7e5a049d3d3d3cd7c33866820c1d11"),
            Blocks.GRASS_BLOCK, new BlockContract(GrassBlock.class, Items.DIRT, "ef73269d2687815bc3bb1b693caa2aaf61fdd7acd3fc67acaa4a6581c217e582"),
            Blocks.PODZOL, new BlockContract(SnowyDirtBlock.class, Items.DIRT, "1ec07336e678b42855a2e73416828280615136ba8a472d5de3c11716ee24f9f8"),
            Blocks.MYCELIUM, new BlockContract(MyceliumBlock.class, Items.DIRT, "6572ddf1d5d1eac383e62d5d237bf0c79fc527e50b74b59d82aea08b33d6d1f5"));
    /** NEW233 alone admits these three additional canonical dry blocks. */
    private static final Map<Block, BlockContract> POINT_BLOCKS = Map.of(
            Blocks.STONE, new BlockContract(Block.class, Items.COBBLESTONE, "9c5016ad4884ab738d790c20d4764493cf272b49f674624110506a9e036f5a9a"),
            Blocks.COBBLESTONE, new BlockContract(Block.class, Items.COBBLESTONE, "53ac67bb445c6247be89043675f7186ac8a463923b306827a1ca32411c64f8df"),
            Blocks.OAK_PLANKS, new BlockContract(Block.class, Items.OAK_PLANKS, "a6bf34724e842c8d3ceed94cc3ca451c333e7d4457382517702f182742551b7e"));
    private static final Set<Item> TOOLS = Set.of(
            Items.WOODEN_SHOVEL, Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL,
            Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE,
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE,
            Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE);
    private VanillaMiningContract() {}

    public static Optional<String> runtimeReason(Map<String, String> mods, Set<String> packs) {
        var reviewed = new java.util.HashMap<>(mods);
        boolean visual = "4.7".equals(reviewed.get("geckolib"));
        if (visual) reviewed.remove("geckolib");
        // The embedded mech data pack contains only its own recipes.
        boolean mech = "0.4.3".equals(reviewed.get("acamech"));
        if (mech) reviewed.remove("acamech");
        boolean physics = PhysicsBundle.exact(MODS, reviewed);
        if (!MODS.equals(reviewed) && !physics) return Optional.of("Unreviewed mining mod/version set");
        if (!packs.contains("vanilla") || !packs.stream().allMatch(pack -> PACKS.contains(pack) || visual && pack.equals("mod/geckolib") || mech && pack.equals("mod/acamech") || physics && PhysicsBundle.PACKS.contains(pack))) return Optional.of("Unreviewed mining data pack");
        return Optional.empty();
    }
    public static Optional<String> unsupportedReason(ServerPlayer player) {
        if (player == null || player.getClass() != ServerPlayer.class || player.gameMode.getClass() != ServerPlayerGameMode.class)
            return Optional.of("Unreviewed player or game-mode class");
        if (!(player.gameMode instanceof NativeMiningAccess access) || access.academy$nativeMiningContractVersion() != 1)
            return Optional.of("Reviewed native mining seam unavailable");
        var packs = player.server.getResourceManager().listPacks().toList();
        if (CompatMode.open()) return Optional.empty();
        var reason = runtimeReason(ModList.get().getMods().stream().collect(Collectors.toMap(m -> m.getModId(), m -> m.getVersion().toString())),
                packs.stream().map(p -> p.packId()).collect(Collectors.toSet()));
        if (reason.isPresent()) return reason;
        for (var pack : packs) if ((pack.packId().equals("bundle") || pack.packId().equals("trade_rebalance"))
                && !pack.knownPackInfo().equals(Optional.of(KnownPack.vanilla(pack.packId()))))
            return Optional.of("Unreviewed feature-pack provenance");
        return Optional.empty();
    }
    public static boolean reviewedBlock(BlockState state) {
        if (state == null) return false;
        BlockContract contract = BLOCKS.get(state.getBlock());
        return contract != null && state.getBlock().getClass() == contract.type && !state.hasBlockEntity()
                && state.getFluidState().isEmpty() && !(state.getBlock() instanceof FallingBlock);
    }
    private static BlockContract pointContract(BlockState state) {
        if (state == null) return null;
        BlockContract original = BLOCKS.get(state.getBlock());
        return original != null ? original : POINT_BLOCKS.get(state.getBlock());
    }
    public static boolean reviewedPointBlock(BlockState state) {
        BlockContract contract = pointContract(state);
        return contract != null && state.getBlock().getClass() == contract.type && !state.hasBlockEntity()
                && state.getFluidState().isEmpty() && !(state.getBlock() instanceof FallingBlock);
    }
    public static boolean reviewedTool(ItemStack stack) {
        if (stack.isEmpty() || handEquivalent(stack)) return true;
        if (!TOOLS.contains(stack.getItem()) || stack.getCount() != 1 || !stack.isDamageableItem()
                || stack.getDamageValue() < 0 || stack.getDamageValue() >= stack.getMaxDamage()) return false;
        Class<?> type = stack.getItem().getClass();
        if (type != ShovelItem.class && type != PickaxeItem.class && type != AxeItem.class && type != HoeItem.class) return false;
        ItemStack canonical = new ItemStack(stack.getItem());
        canonical.setDamageValue(stack.getDamageValue());
        return ItemStack.matches(stack, canonical) && !stack.isEnchanted()
                && stack.get(DataComponents.TOOL) != null && stack.get(DataComponents.TOOL).damagePerBlock() == 1;
    }
    /**
     * A held item that is not a tool at all (a book, paper, coins) breaks blocks exactly as a bare hand does: no tool
     * component, no durability to spend, no enchantment to alter loot. Refusing it made beam etching and point meltdown
     * silently do nothing for anyone holding ACA's own tutorial or materials.
     */
    static boolean handEquivalent(ItemStack stack) {
        return stack.get(DataComponents.TOOL) == null && !stack.isDamageableItem() && !stack.isEnchanted()
                && stack.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty()
                && stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).isEmpty();
    }
    /** Read-only vanilla baseline; canHarvestBlock itself posts the native HarvestCheck event. */
    public static boolean reviewedPointHarvestTool(BlockState state, ItemStack stack) {
        return reviewedPointBlock(state) && reviewedTool(stack)
                && (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state));
    }
    /** Prediction is only capacity admission. Native playerDestroy alone materializes the loot. */
    public static ItemStack expectedDrop(BlockState state) {
        BlockContract contract = BLOCKS.get(state.getBlock());
        return contract == null ? ItemStack.EMPTY : new ItemStack(contract.drop);
    }
    public static ItemStack expectedPointDrop(BlockState state) {
        BlockContract contract = pointContract(state);
        return contract == null ? ItemStack.EMPTY : new ItemStack(contract.drop);
    }
    public static boolean canonicalLoot(ServerPlayer player, BlockState state) {
        return canonicalLoot(player, state, BLOCKS.get(state.getBlock()));
    }
    public static boolean canonicalPointLoot(ServerPlayer player, BlockState state) {
        return canonicalLoot(player, state, pointContract(state));
    }
    private static boolean canonicalLoot(ServerPlayer player, BlockState state, BlockContract contract) {
        if (contract == null) return false;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        var resource = player.server.getResourceManager().getResource(ResourceLocation.withDefaultNamespace("loot_table/blocks/" + path + ".json"));
        if (resource.isEmpty()) return false;
        try (var input = resource.get().open()) {
            return canonicalLootEvidence(contract, resource.get().sourcePackId(), resource.get().knownPackInfo(), input.readAllBytes());
        } catch (Exception unavailable) { return false; }
    }
    /** Read-only evidence check shared by the live resource lookup and tampered-resource tests. */
    public static boolean canonicalLootEvidence(BlockState state, String source, Optional<KnownPack> provenance, byte[] bytes) {
        return canonicalLootEvidence(BLOCKS.get(state.getBlock()), source, provenance, bytes);
    }
    public static boolean canonicalPointLootEvidence(BlockState state, String source, Optional<KnownPack> provenance, byte[] bytes) {
        return canonicalLootEvidence(pointContract(state), source, provenance, bytes);
    }
    private static boolean canonicalLootEvidence(BlockContract contract, String source, Optional<KnownPack> provenance, byte[] bytes) {
        if (contract == null || !"vanilla".equals(source) || !Optional.of(KnownPack.vanilla("core")).equals(provenance)) return false;
        try { return contract.sha256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
