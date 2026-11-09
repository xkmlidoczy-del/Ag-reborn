/* AcademyCraft1.0.7 ContainerImagFusor / TechUIContainer slots. GPLv3. */
package cn.academy.port.fusion;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
public final class ClassicFusorMenu extends AbstractContainerMenu {
    private final Container inventory;private final ContainerData data;private net.minecraft.core.BlockPos sourcePos=net.minecraft.core.BlockPos.ZERO;
    public ClassicFusorMenu(int id,Inventory player,FriendlyByteBuf wire){this(id,player,new SimpleContainer(5),new SimpleContainerData(7));sourcePos=wire.readBlockPos();}
    public ClassicFusorMenu(int id,Inventory player,Container inventory,ContainerData data){
        super(ClassicFusion.FUSOR_MENU.get(),id);checkContainerSize(inventory,5);checkContainerDataCount(data,7);this.inventory=inventory;this.data=data;if(inventory instanceof net.minecraft.world.level.block.entity.BlockEntity tile)sourcePos=tile.getBlockPos();
        addSlot(new Slot(inventory,0,13,49){@Override public boolean mayPlace(ItemStack stack){return ClassicFusorBlockEntity.recipe(stack)>=0;}});
        addSlot(new Slot(inventory,1,143,49){@Override public boolean mayPlace(ItemStack stack){return false;}});
        addSlot(new Slot(inventory,2,13,10){@Override public boolean mayPlace(ItemStack stack){return ClassicMatterUnitItem.filled(stack);}});
        // Source GUI registration order differs from raw inventory indices: 0,1,2,4,3.
        addSlot(new Slot(inventory,4,143,10){@Override public boolean mayPlace(ItemStack stack){return false;}});
        addSlot(new Slot(inventory,3,42,80){@Override public boolean mayPlace(ItemStack stack){return ClassicEnergyItemHelper.isSupported(stack);}});
        for(int column=0;column<9;column++)addSlot(new Slot(player,column,6+column*18,163));
        for(int row=1;row<4;row++)for(int column=0;column<9;column++)addSlot(new Slot(player,(4-row)*9+column,6+column*18,159-row*18));addDataSlots(data);
    }
    public net.minecraft.core.BlockPos sourcePos(){return sourcePos;}
    public boolean isFor(ClassicFusorBlockEntity fusor){return inventory==fusor;}
    public double energy(){long bits=(data.get(0)&65535L)|((data.get(1)&65535L)<<16)|((data.get(2)&65535L)<<32)|((data.get(3)&65535L)<<48);return cn.academy.port.energy.ClassicEnergy.sanitize(Double.longBitsToDouble(bits),2000);}
    public int liquid(){return Math.max(0,Math.min(8000,data.get(4)));}
    public double progress(){return Math.max(0,Math.min(1,data.get(5)/10000.0));}
    public int liquidRequired(){return ClassicFusorWork.liquidRequired(data.get(6)-1);}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var current=slot.getItem();var original=current.copy();
        if(index<5){if(!moveItemStackTo(current,5,slots.size(),false))return ItemStack.EMPTY;}
        else {int target=ClassicMatterUnitItem.filled(current)?2:ClassicEnergyItemHelper.isSupported(current)?4:ClassicFusorBlockEntity.recipe(current)>=0?0:-1;if(target<0||!moveItemStackTo(current,target,target+1,false))return ItemStack.EMPTY;}
        if(current.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,current);return original;
    }
}
