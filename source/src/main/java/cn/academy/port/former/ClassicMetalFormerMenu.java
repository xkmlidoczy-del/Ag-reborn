/* AcademyCraft 1.0.7 ContainerMetalFormer / TechUIContainer slot and transfer rules. GPLv3. */
package cn.academy.port.former;
import cn.academy.port.energy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
public final class ClassicMetalFormerMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final ContainerData data;
    private final net.minecraft.world.level.Level level;
    private BlockPos sourcePos=BlockPos.ZERO;
    public ClassicMetalFormerMenu(int id,Inventory player,FriendlyByteBuf wire){this(id,player,new SimpleContainer(3),new SimpleContainerData(7));sourcePos=wire.readBlockPos();}
    public ClassicMetalFormerMenu(int id,Inventory player,Container inventory,ContainerData data){
        super(ClassicMetalFormer.MENU.get(),id);checkContainerSize(inventory,3);checkContainerDataCount(data,7);this.inventory=inventory;this.data=data;level=player.player.level();if(inventory instanceof ClassicMetalFormerBlockEntity tile)sourcePos=tile.getBlockPos();
        addSlot(new Slot(inventory,0,13,49){@Override public boolean mayPlace(ItemStack stack){return ClassicMetalFormerRecipes.guiInput(stack,level);}});
        addSlot(new Slot(inventory,1,143,49){@Override public boolean mayPlace(ItemStack stack){return false;}});
        addSlot(new Slot(inventory,2,42,80){@Override public boolean mayPlace(ItemStack stack){return ClassicEnergyItemHelper.isSupported(stack);}});
        for(int column=0;column<9;column++)addSlot(new Slot(player,column,6+column*18,163));
        for(int row=1;row<4;row++)for(int column=0;column<9;column++)addSlot(new Slot(player,(4-row)*9+column,6+column*18,159-row*18));addDataSlots(data);
    }
    public BlockPos sourcePos(){return sourcePos;}
    public ClassicMetalFormerBlockEntity tile(){return inventory instanceof ClassicMetalFormerBlockEntity tile?tile:null;}
    public boolean isFor(ClassicMetalFormerBlockEntity tile){return inventory==tile;}
    public double energy(){long bits=(data.get(0)&65535L)|((data.get(1)&65535L)<<16)|((data.get(2)&65535L)<<32)|((data.get(3)&65535L)<<48);return ClassicEnergy.sanitize(Double.longBitsToDouble(bits),3000);}
    public ClassicMetalFormerWork.Mode mode(){int mode=data.get(4);return mode>=0&&mode<4?ClassicMetalFormerWork.Mode.values()[mode]:ClassicMetalFormerWork.Mode.PLATE;}
    public boolean working(){return data.get(6)!=0;}
    public double progress(){return working()?Math.max(0,Math.min(1,(double)data.get(5)/60)):0;}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int button){if((button!=0&&button!=1)||!(inventory instanceof ClassicMetalFormerBlockEntity tile)||player.containerMenu!=this||!tile.stillValid(player)||player.level().isClientSide)return false;tile.cycleMode(button==0?-1:1);broadcastChanges();return true;}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var current=slot.getItem();var original=current.copy();
        if(index<3){if(!moveItemStackTo(current,3,slots.size(),false))return ItemStack.EMPTY;}
        else {boolean moved=false;if(ClassicEnergyItemHelper.isSupported(current))moved=moveItemStackTo(current,2,3,false);if(!moved)moved=moveItemStackTo(current,0,1,false);if(!moved)return ItemStack.EMPTY;}
        if(current.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,current);return original;
    }
}
