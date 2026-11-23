package cn.academy.port.terminal.fish;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FishMenu extends AbstractContainerMenu {
    public static final int TOGGLE_PROJECTION = 0;
    private final ContainerLevelAccess access;
    private final DataSlot variant = DataSlot.standalone();
    private final DataSlot projection = DataSlot.standalone();

    FishMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    FishMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(FishComputer.MENU.get(), id);
        this.access = access;
        addDataSlot(variant);
        addDataSlot(projection);
        readState();
    }

    static boolean isTerminal(BlockState state) {
        return state.is(FishComputer.TERMINAL.get()) && !FishBlock.upper(state);
    }

    private void readState() {
        access.execute((level, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!isTerminal(state)) return;
            variant.set(state.getValue(FishBlock.VARIANT));
            projection.set(state.getValue(FishBlock.SHOW) ? 1 : 0);
        });
    }

    @Override
    public void broadcastChanges() {
        readState();
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button != TOGGLE_PROJECTION || !stillValid(player)) return false;
        access.execute((level, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (isTerminal(state)) level.setBlock(pos, state.cycle(FishBlock.SHOW), Block.UPDATE_CLIENTS);
        });
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> isTerminal(level.getBlockState(pos))
                && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64.0D, true);
    }

    public int variant() { return variant.get(); }
    public boolean projecting() { return projection.get() != 0; }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
