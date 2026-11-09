/* AcademyCraft1.0.7 ContainerPhaseGen/TechUIContainer native slots. GPLv3. See NOTICE. */
package cn.academy.port.phasegen;

import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.fusion.ClassicMatterUnitItem;
import net.minecraft.core.BlockPos;
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

public final class ClassicPhaseGeneratorMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final ContainerData data;
    private BlockPos sourcePos=BlockPos.ZERO;
    public ClassicPhaseGeneratorMenu(int id,Inventory player,FriendlyByteBuf wire){this(id,player,new SimpleContainer(3),new SimpleContainerData(5));sourcePos=wire.readBlockPos();}
    public ClassicPhaseGeneratorMenu(int id,Inventory player,Container inventory,ContainerData data){
        super(ClassicPhaseGenerators.MENU.get(),id);checkContainerSize(inventory,3);checkContainerDataCount(data,5);this.inventory=inventory;this.data=data;
        if(inventory instanceof net.minecraft.world.level.block.entity.BlockEntity tile)sourcePos=tile.getBlockPos();
        addSlot(new Slot(inventory,0,45,12){@Override public boolean mayPlace(ItemStack stack){return ClassicMatterUnitItem.filled(stack);}});
        // Literal source SlotMatterUnit(imagPhase) is retained for manual clicks in its return slot.
        addSlot(new Slot(inventory,1,112,51){@Override public boolean mayPlace(ItemStack stack){return ClassicMatterUnitItem.filled(stack);}});
        addSlot(new Slot(inventory,2,42,80){@Override public boolean mayPlace(ItemStack stack){return ClassicEnergyItemHelper.isSupported(stack);}});
        for(int column=0;column<9;column++)addSlot(new Slot(player,column,6+column*18,163));
        for(int row=1;row<4;row++)for(int column=0;column<9;column++)addSlot(new Slot(player,(4-row)*9+column,6+column*18,159-row*18));
        addDataSlots(data);
    }
    public BlockPos sourcePos(){return sourcePos;}
    public boolean isFor(ClassicPhaseGeneratorBlockEntity tile){return inventory==tile;}
    public double energy(){return ClassicPhaseGeneratorRules.fromWords(data.get(0),data.get(1),data.get(2),data.get(3));}
    public int liquid(){return ClassicPhaseGeneratorRules.sanitizeLiquid(data.get(4));}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;ItemStack current=slot.getItem(),original=current.copy();
        // Correct the source gRange(4,40) indexing slip: all36 actual player slots begin at3.
        if(index<3){if(!moveItemStackTo(current,3,slots.size(),false))return ItemStack.EMPTY;}
        else if(ClassicEnergyItemHelper.isSupported(current)){if(!moveItemStackTo(current,2,3,false))return ItemStack.EMPTY;}
        else if(ClassicMatterUnitItem.filled(current)){if(!moveItemStackTo(current,0,1,false))return ItemStack.EMPTY;}
        else return ItemStack.EMPTY;
        if(current.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,current);return original;
    }
}
