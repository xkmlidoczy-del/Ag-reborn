/* AcademyCraft TileInventory modern native inventory, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class ClassicWirelessInventory extends BlockEntity implements Container {
    protected final NonNullList<ItemStack> items;
    protected UUID owner;
    protected String ownerName="";
    protected ClassicWirelessInventory(BlockEntityType<?> type,BlockPos pos,BlockState state,int size){super(type,pos,state);items=NonNullList.withSize(size,ItemStack.EMPTY);}
    public abstract boolean available();
    public void setPlacer(Player player){owner=player.getUUID();ownerName=player.getName().getString();setChanged();}
    public String ownerName(){return ownerName;}
    public boolean ownedBy(Player player){return owner!=null&&owner.equals(player.getUUID());}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);ContainerHelper.saveAllItems(tag,items,lookup);if(owner!=null)tag.putUUID("owner",owner);tag.putString("placer",ownerName);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);items.clear();ContainerHelper.loadAllItems(tag,items,lookup);owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;ownerName=tag.getString("placer");}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider lookup){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public int getContainerSize(){return items.size();}
    @Override public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<items.size()?items.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot<0||slot>=items.size())return ItemStack.EMPTY;var result=ContainerHelper.removeItem(items,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return slot>=0&&slot<items.size()?ContainerHelper.takeItem(items,slot):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=items.size())return;items.set(slot,stack);stack.limitSize(getMaxStackSize(stack));setChanged();}
    @Override public boolean stillValid(Player player){return available()&&player.level()==level&&player.distanceToSqr(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ())<64;}
    @Override public void clearContent(){items.clear();setChanged();}
}
