package cn.academy.port.highpower;

import cn.academy.port.highpower.core.SpatialRelayRules;
import java.util.UUID;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.common.util.FakePlayer;

/** NEW343 evidence is independent of NEW133 and NEW323 and bound to one original native toss. */
public final class SpatialRelayProvenance {
    public static final String KEY = "academy:spatial_relay_toss";
    private static final Set<DataComponentType<?>> REVIEWED_COMPONENTS = Set.of(
            DataComponents.MAX_STACK_SIZE, DataComponents.MAX_DAMAGE, DataComponents.DAMAGE,
            DataComponents.RARITY, DataComponents.LORE, DataComponents.ENCHANTMENTS,
            DataComponents.REPAIR_COST, DataComponents.ATTRIBUTE_MODIFIERS, DataComponents.FOOD,
            DataComponents.FIRE_RESISTANT, DataComponents.TOOL);
    private SpatialRelayProvenance() {}

    /** Invoked only from the authenticated, non-cancelled native ItemTossEvent admission seam. */
    static void recordToss(ServerPlayer owner, ItemEntity item) {
        if (owner == null || item == null || owner instanceof FakePlayer || !(item.level() instanceof ServerLevel world)
                || !world.getServer().isSameThread() || owner.serverLevel() != world || !owner.isAlive() || owner.isRemoved()
                || item.getClass() != ItemEntity.class || item.getType() != EntityType.ITEM || item.isRemoved()
                || !(item instanceof NativeItemMergeAccess seam) || seam.academy$nativeItemMergeContractVersion() != 1
                || !ordinary(item.getItem())) return;
        CompoundTag proof = new CompoundTag();
        proof.putInt("version", 1); proof.putUUID("owner", owner.getUUID()); proof.putUUID("entity", item.getUUID());
        proof.putUUID("nonce", UUID.randomUUID()); proof.putString("item", BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString());
        proof.putInt("count", item.getItem().getCount());
        item.getPersistentData().put(KEY, proof);
    }

    public static SpatialRelayRules.Proof proof(ItemEntity item) {
        if (item == null || !(item.level() instanceof ServerLevel world) || !world.getServer().isSameThread()
                || item.getClass() != ItemEntity.class || item.getType() != EntityType.ITEM || !ordinary(item.getItem())
                || !item.getPersistentData().contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = item.getPersistentData().getCompound(KEY);
        if (tag.getAllKeys().size() != 6 || !tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != 1
                || !tag.hasUUID("owner") || !tag.hasUUID("entity") || !tag.hasUUID("nonce")
                || !tag.contains("item", Tag.TAG_STRING) || !tag.contains("count", Tag.TAG_INT)) return null;
        var proof = new SpatialRelayRules.Proof(tag.getUUID("owner"), tag.getUUID("entity"), tag.getUUID("nonce"),
                tag.getString("item"), tag.getInt("count"));
        return proof.valid() && proof.entity().equals(item.getUUID()) && proof.count() == item.getItem().getCount()
                && proof.item().equals(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString()) ? proof : null;
    }

    /** Exact defaults close the custom/enchanted/decorated/data-bearing stack domain. */
    public static boolean ordinary(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isComponentsPatchEmpty() || stack.getCount() < 1
                || stack.getCount() > stack.getMaxStackSize()
                || !"minecraft".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                || !stack.getItem().getClass().getName().startsWith("net.minecraft.world.item.")
                || stack.getItem() instanceof BundleItem
                || stack.getItem() instanceof BlockItem block && block.getBlock() instanceof EntityBlock
                || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.CONTAINER_LOOT) || stack.has(DataComponents.BLOCK_ENTITY_DATA)
                || stack.has(DataComponents.CUSTOM_DATA)) return false;
        if (!REVIEWED_COMPONENTS.containsAll(stack.getComponents().keySet()) || stack.isEnchanted()
                || stack.getDamageValue() != 0 || stack.getOrDefault(DataComponents.REPAIR_COST, 0) != 0
                || !stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().isEmpty()) return false;
        return ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
    }

    public static void invalidate(ItemEntity item) {
        if (item == null) return;
        item.getPersistentData().remove(KEY);
        SpatialRelay.invalidateNativeItems(item);
    }
    /** Both proofs and all queued handles die before any vanilla count transfer, including same-owner partial merges. */
    public static void beforeMerge(ItemEntity survivor, ItemEntity donor) {
        survivor.getPersistentData().remove(KEY); donor.getPersistentData().remove(KEY);
        SpatialRelay.invalidateNativeItems(survivor, donor);
    }
}
