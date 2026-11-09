/* AcademyCraft 1.0.7 ContainerSolarGen / TechUIContainer exact slots, GPLv3. See NOTICE. */
package cn.academy.port.solar;

import cn.academy.port.energy.ClassicEnergyItemHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ClassicSolarMenu extends AbstractContainerMenu {
    private final Container inventory;
    private net.minecraft.core.BlockPos sourcePos=net.minecraft.core.BlockPos.ZERO;
    private final ContainerData data;
    public ClassicSolarMenu(int id, Inventory player, FriendlyByteBuf wire) {
        this(id, player, new SimpleContainer(1), new SimpleContainerData(5));
        sourcePos=wire.readBlockPos(); // Position is presentation only; authoritative menu owns its actual tile.
    }
    public ClassicSolarMenu(int id, Inventory player, Container inventory, ContainerData data) {
        super(ClassicSolarGenerators.MENU.get(), id); checkContainerSize(inventory, 1); checkContainerDataCount(data, 5);
        this.inventory = inventory; this.data = data;
        if(inventory instanceof net.minecraft.world.level.block.entity.BlockEntity tile)sourcePos=tile.getBlockPos();
        addSlot(new Slot(inventory, 0, 42, 81) { @Override public boolean mayPlace(ItemStack stack) { return ClassicEnergyItemHelper.isSupported(stack); } });
        for (int column = 0; column < 9; column++) addSlot(new Slot(player, column, 6 + column * 18, 163));
        for (int row = 1; row < 4; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(player, (4 - row) * 9 + column, 6 + column * 18, 159 - row * 18));
        addDataSlots(data);
    }
    public net.minecraft.core.BlockPos sourcePos(){return sourcePos;}
    public boolean isFor(ClassicSolarBlockEntity solar){return inventory==solar;}
    public double energy() { return ClassicSolarRules.fromWords(data.get(0), data.get(1), data.get(2), data.get(3)); }
    public ClassicSolarRules.Status status() { int status = data.get(4); return status >= 0 && status < 3 ? ClassicSolarRules.Status.values()[status] : ClassicSolarRules.Status.STOPPED; }
    @Override public boolean stillValid(Player player) { return inventory.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack current = slot.getItem(), original = current.copy();
        if (index == 0) { if (!moveItemStackTo(current, 1, slots.size(), false)) return ItemStack.EMPTY; }
        else if (!ClassicEnergyItemHelper.isSupported(current) || !moveItemStackTo(current, 0, 1, false)) return ItemStack.EMPTY;
        if (current.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, current); return original;
    }
}
