/* AcademyCraft1.0.7 TileInventory and origin ownership lifecycle adapter. GPLv3; see NOTICE. */
package cn.academy.port.wind;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** An auxiliary cell owns no payload. Menus route to the same exact live root. */
public abstract class ClassicWindInventory extends BlockEntity implements Container {
    protected final NonNullList<ItemStack> inventory=NonNullList.withSize(1,ItemStack.EMPTY);
    protected final ContainerData data=new ContainerData(){public int get(int index){return datum(index);}public void set(int index,int value){}public int getCount(){return 7;}};
    private final IItemHandler handler=new InvWrapper(this){
        @Override public void setStackInSlot(int slot,ItemStack stack){if(available())super.setStackInSlot(slot,stack);}
        @Override public int getSlotLimit(int slot){return available()?super.getSlotLimit(slot):0;}
        @Override public ItemStack getStackInSlot(int slot){return available()?super.getStackInSlot(slot):ItemStack.EMPTY;}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return available()?super.insertItem(slot,stack,simulate):stack;}
        @Override public ItemStack extractItem(int slot,int count,boolean simulate){return available()?super.extractItem(slot,count,simulate):ItemStack.EMPTY;}
        @Override public boolean isItemValid(int slot,ItemStack stack){return available()&&super.isItemValid(slot,stack);}
    };
    protected ClassicWindInventory(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    private boolean ownsState(){return getBlockState().is(this instanceof ClassicWindBaseBlockEntity?ClassicWindGenerators.BASE.get():ClassicWindGenerators.MAIN.get());}
    public boolean isOrigin(){return ownsState()&&ClassicWindStructure.valid(getBlockState())&&getBlockState().getValue(ClassicWindBlock.PART)==0;}
    public boolean available(){return level!=null&&!level.isClientSide&&!isRemoved()&&isOrigin()&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this&&ClassicWindStructure.complete(level,worldPosition,getBlockState());}
    /** Unknown neighboring chunks defer teardown; they never force-load or produce energy. */
    protected boolean checkIntegrity(){if(level==null||isRemoved()||!ownsState())return false;var state=getBlockState();if(!ClassicWindStructure.valid(state)){level.removeBlock(worldPosition,false);return false;}var origin=ClassicWindBlock.origin(worldPosition,state);if(!ClassicWindStructure.allLoaded(level,origin,state))return false;if(!ClassicWindStructure.complete(level,origin,state)){ClassicWindStructure.remove(level,worldPosition,state);level.removeBlock(worldPosition,false);return false;}return isOrigin();}
    public IItemHandler itemHandler(){return handler;}
    protected abstract int datum(int index);
    public abstract Component getDisplayName();
    public AbstractContainerMenu createMenu(int id,Inventory player,Player owner){return new ClassicWindMenu(id,player,this,data);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);if(isOrigin())ContainerHelper.saveAllItems(tag,inventory,lookup);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);inventory.clear();if(isOrigin())ContainerHelper.loadAllItems(tag,inventory,lookup);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider lookup){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public int getMaxStackSize(){return 64;}
    @Override public int getContainerSize(){return 1;}
    @Override public boolean isEmpty(){return inventory.get(0).isEmpty();}
    @Override public ItemStack getItem(int slot){return slot==0?inventory.get(0):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot!=0)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(inventory,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return slot==0?ContainerHelper.takeItem(inventory,slot):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){if(slot!=0||!isOrigin())return;inventory.set(slot,stack);stack.limitSize(getMaxStackSize(stack));setChanged();}
    @Override public boolean stillValid(Player player){return available()&&player.level()==level&&player.distanceToSqr(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ())<64;}
    @Override public void clearContent(){inventory.clear();setChanged();}
}
