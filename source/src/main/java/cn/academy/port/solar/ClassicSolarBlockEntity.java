/* AcademyCraft 1.0.7 TileSolarGen + TileGeneratorBase, modern server adapter. GPLv3. See NOTICE. */
package cn.academy.port.solar;

import cn.academy.port.energy.ClassicEnergyItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One server-authoritative native recharge slot. No unloaded ticking, fabricated adjacency, or FE shell. */
public final class ClassicSolarBlockEntity extends BlockEntity implements Container, ImagFluxGenerator {
    private final NonNullList<ItemStack> inventory = NonNullList.withSize(1, ItemStack.EMPTY);
    private final ClassicSolarBuffer buffer = new ClassicSolarBuffer();
    private int syncTicks;
    private final ContainerData data = new ContainerData() {
        public int get(int index) { return index < 4 ? ClassicSolarRules.word(buffer.energy(), index) : status().ordinal(); }
        public void set(int index, int value) {} // Clients own a SimpleContainerData; they cannot mutate this authoritative view.
        public int getCount() { return 5; }
    };
    public ClassicSolarBlockEntity(BlockPos pos, BlockState state) { super(ClassicSolarGenerators.TILE.get(), pos, state); }
    public boolean available() { return level != null && !level.isClientSide && !isRemoved() && level.getBlockEntity(worldPosition) == this && getBlockState().is(ClassicSolarGenerators.BLOCK.get()); }
    public ClassicSolarRules.Status status() {
        return level == null ? ClassicSolarRules.Status.STOPPED : ClassicSolarRules.status(level.getDayTime(), level.canSeeSky(worldPosition.above()), level.isRaining());
    }
    public ClassicSolarBuffer buffer() { return buffer; }
    @Override public double getEnergy() { return available() ? buffer.energy() : 0; }
    @Override public double getBandwidth() { return ClassicSolarRules.BANDWIDTH; }
    @Override public double getProvidedEnergy(double request) { if (!available()) return 0; double provided = buffer.getProvidedEnergy(request); if (provided > 0) setChanged(); return provided; }
    public static void serverTick(Level level, BlockPos pos, BlockState state, ClassicSolarBlockEntity solar) {
        if (!solar.available()) return;
        ItemStack battery = solar.getItem(0);
        var nativeStorage = ClassicEnergyItemHelper.nativeStorage(battery);
        double batteryBefore = nativeStorage == null ? 0 : nativeStorage.energy();
        double change = solar.buffer.tick(solar.status(), nativeStorage == null ? null : request -> ClassicEnergyItemHelper.charge(battery, request, false));
        if (change != 0 || nativeStorage != null && batteryBefore != nativeStorage.energy()) solar.setChanged();
        if (++solar.syncTicks >= 20) { solar.syncTicks = 0; level.sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS); }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider lookup) { super.saveAdditional(tag, lookup); tag.putDouble("energy", buffer.energy()); ContainerHelper.saveAllItems(tag, inventory, lookup); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider lookup) { super.loadAdditional(tag, lookup); buffer.load(tag.getDouble("energy")); inventory.clear(); ContainerHelper.loadAllItems(tag, inventory, lookup); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup) { var tag = new CompoundTag(); tag.putDouble("energy", buffer.energy()); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public Component getDisplayName() { return Component.translatable("block.academy.solar_gen"); }
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) { return new ClassicSolarMenu(id, playerInventory, this, data); }
    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return inventory.getFirst().isEmpty(); }
    @Override public ItemStack getItem(int slot) { return slot == 0 ? inventory.getFirst() : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int count) { if (slot != 0) return ItemStack.EMPTY; var result = ContainerHelper.removeItem(inventory, slot, count); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return slot == 0 ? ContainerHelper.takeItem(inventory, 0) : ItemStack.EMPTY; }
    @Override public void setItem(int slot, ItemStack stack) { if (slot != 0) return; inventory.set(0, stack); stack.limitSize(getMaxStackSize(stack)); setChanged(); }
    @Override public boolean stillValid(Player player) { return available() && player.level() == level && player.distanceToSqr(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()) < 64; }
    @Override public void clearContent() { inventory.clear(); setChanged(); }
}
