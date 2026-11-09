package cn.academy.port.highpower;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

/** Server-owned toss evidence. Vanilla Thrower and pickup Target are never evidence of ownership. */
@EventBusSubscriber(modid = "academy")
public final class MagneticItemProvenance {
    public static final String KEY = "academy:magnetic_item_toss";
    public static final int VERSION = 1;
    /** Separate scoped evidence: NEW133's exact three-field version-1 contract is unchanged. */
    public static final String RECOVERY_KEY = "academy:recovery_item_toss";
    public record RecoveryProof(UUID owner, UUID entity, UUID nonce) {}
    private MagneticItemProvenance() {}
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void tossed(ItemTossEvent event) {
        ItemEntity item = event.getEntity();
        if (!(item.level() instanceof ServerLevel world) || !world.getServer().isSameThread()) return;
        // Never let canceled or non-player tosses preserve a copied authorization tag.
        invalidate(item);
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer owner) || owner instanceof FakePlayer
                || owner.serverLevel() != world || item.getClass() != ItemEntity.class || item.getType() != EntityType.ITEM
                || !owner.isAlive() || owner.isRemoved() || item.isRemoved() || item.getItem().isEmpty()
                || HighPowerCombat.unsupportedReason(owner).isPresent()
                || !(item instanceof NativeItemMergeAccess seam) || seam.academy$nativeItemMergeContractVersion() != 1) return;
        CompoundTag proof = new CompoundTag();
        proof.putInt("version", VERSION); proof.putUUID("owner", owner.getUUID()); proof.putUUID("entity", item.getUUID());
        item.getPersistentData().put(KEY, proof);
        SpatialRelayProvenance.recordToss(owner, item);
        // NEW323 admits only an original plain single-needle toss, never a later
        // remainder of a stacked/decorated toss. NEW133 keeps its broader proof.
        if (!DisplacedNeedleEntity.isPlainNeedle(item.getItem())) return;
        CompoundTag recovery = new CompoundTag();
        recovery.putInt("version", 1); recovery.putUUID("owner", owner.getUUID());
        recovery.putUUID("entity", item.getUUID()); recovery.putUUID("nonce", UUID.randomUUID());
        item.getPersistentData().put(RECOVERY_KEY, recovery);
    }
    /** Strict schema and entity binding survive save/reload but cannot transfer to a copied entity. */
    public static UUID owner(ItemEntity item) {
        if (item == null || !(item.level() instanceof ServerLevel world) || !world.getServer().isSameThread()
                || !item.getPersistentData().contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag proof = item.getPersistentData().getCompound(KEY);
        if (proof.getAllKeys().size() != 3 || !proof.contains("version", Tag.TAG_INT) || proof.getInt("version") != VERSION
                || !proof.hasUUID("owner") || !proof.hasUUID("entity") || !item.getUUID().equals(proof.getUUID("entity"))) return null;
        return proof.getUUID("owner");
    }
    public static boolean ownedBy(ItemEntity item, UUID owner) { return owner != null && owner.equals(owner(item)); }
    public static RecoveryProof recoveryProof(ItemEntity item) {
        UUID owner = owner(item);
        if (owner == null || !item.getPersistentData().contains(RECOVERY_KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag proof = item.getPersistentData().getCompound(RECOVERY_KEY);
        if (proof.getAllKeys().size() != 4 || !proof.contains("version", Tag.TAG_INT) || proof.getInt("version") != 1
                || !proof.hasUUID("owner") || !owner.equals(proof.getUUID("owner"))
                || !proof.hasUUID("entity") || !item.getUUID().equals(proof.getUUID("entity"))
                || !proof.hasUUID("nonce") || proof.getUUID("nonce").equals(new UUID(0, 0))) return null;
        return new RecoveryProof(owner, item.getUUID(), proof.getUUID("nonce"));
    }
    public static void invalidate(ItemEntity item) {
        if (item == null) return;
        item.getPersistentData().remove(KEY); invalidateRecovery(item);
        SpatialRelayProvenance.invalidate(item);
    }
    private static void invalidateRecovery(ItemEntity item) {
        item.getPersistentData().remove(RECOVERY_KEY);
        RecoveryMark.invalidateNativeItem(item);
    }
    /** Non-canceling native HEAD hook. Only evidence is changed; all native transfer semantics continue. */
    public static void beforeMerge(ItemEntity survivor, ItemStack survivorStack, ItemEntity donor, ItemStack donorStack) {
        if (!(survivor.level() instanceof ServerLevel world) || !world.getServer().isSameThread()) return;
        SpatialRelayProvenance.beforeMerge(survivor, donor);
        // Irreversible NEW323 retirement precedes any native count transfer, even
        // same-owner or partial merges. Never cancel or alter the native merge itself.
        survivor.getPersistentData().remove(RECOVERY_KEY);
        donor.getPersistentData().remove(RECOVERY_KEY);
        RecoveryMark.invalidateNativeItems(survivor, donor);
        if (!(survivor instanceof NativeItemMergeAccess leftSeam) || leftSeam.academy$nativeItemMergeContractVersion() != 1
                || !(donor instanceof NativeItemMergeAccess rightSeam) || rightSeam.academy$nativeItemMergeContractVersion() != 1) return;
        UUID left = owner(survivor), right = owner(donor);
        if (survivor == donor || donor.level() != world || survivor.getClass() != ItemEntity.class || donor.getClass() != ItemEntity.class
                || survivor.getItem() != survivorStack || donor.getItem() != donorStack || survivorStack.isEmpty() || donorStack.isEmpty()
                || left == null || !left.equals(right)) {
            invalidate(survivor); invalidate(donor);
        }
        // Same-owner evidence stays attached to each original UUID, including a partial donor.
    }
}
