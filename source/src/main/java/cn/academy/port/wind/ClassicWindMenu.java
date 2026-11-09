/* AcademyCraft1.0.7 ContainerWindGenBase/Main/TechUIContainer, GPLv3; see NOTICE. */
package cn.academy.port.wind;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
public final class ClassicWindMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final ContainerData data;
    private final boolean base;
    private BlockPos sourcePos=BlockPos.ZERO;
    public ClassicWindMenu(int id,Inventory player,FriendlyByteBuf wire){this(id,player,new SimpleContainer(1),new SimpleContainerData(7),wire.readBoolean());sourcePos=wire.readBlockPos();}
    public ClassicWindMenu(int id,Inventory player,ClassicWindInventory inventory,ContainerData data){this(id,player,inventory,data,inventory instanceof ClassicWindBaseBlockEntity);sourcePos=inventory.getBlockPos();}
    private ClassicWindMenu(int id,Inventory player,Container inventory,ContainerData data,boolean base){super(ClassicWindGenerators.MENU.get(),id);checkContainerSize(inventory,1);checkContainerDataCount(data,7);this.inventory=inventory;this.data=data;this.base=base;
        addSlot(new Slot(inventory,0,base?42:78,base?80:9){@Override public boolean mayPlace(ItemStack stack){return base?ClassicEnergyItemHelper.isSupported(stack):stack.is(ClassicWindGenerators.FAN.get());}});
        for(int column=0;column<9;column++)addSlot(new Slot(player,column,6+column*18,163));for(int row=1;row<4;row++)for(int column=0;column<9;column++)addSlot(new Slot(player,(4-row)*9+column,6+column*18,159-row*18));addDataSlots(data);
    }
    public boolean base(){return base;}
    public BlockPos sourcePos(){return sourcePos;}
    public boolean isFor(ClassicWindBaseBlockEntity tile){return base&&inventory==tile;}
    public double energy(){return ClassicWindRules.fromWords(data.get(0),data.get(1),data.get(2),data.get(3));}
    public ClassicWindRules.Completeness completeness(){return ClassicWindRules.Completeness.values()[Math.max(0,Math.min(3,data.get(4)))];}
    public int altitude(){return data.get(5);}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var current=slot.getItem();var original=current.copy();if(index==0){if(!moveItemStackTo(current,1,37,false))return ItemStack.EMPTY;}else if((base?ClassicEnergyItemHelper.isSupported(current):current.is(ClassicWindGenerators.FAN.get()))){if(!moveItemStackTo(current,0,1,false))return ItemStack.EMPTY;}else return ItemStack.EMPTY;if(current.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,current);return original;}
}
