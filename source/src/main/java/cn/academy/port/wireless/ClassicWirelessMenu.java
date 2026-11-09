/* AcademyCraft1.0.7 ContainerNode/ContainerMatrix + TechUI native session adapter. GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import java.util.UUID;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.survival.ClassicMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Sender owns a concrete current native menu nonce; position/tier/owner never come from mutation requests. */
public final class ClassicWirelessMenu extends AbstractContainerMenu {
    public enum Kind { NODE,MATRIX,USER }
    private final BlockPos sourcePos;
    private final Kind kind;
    private final UUID token;
    private final Container inventory;
    private CompoundTag snapshot=new CompoundTag();
    private final Inventory playerInventory;
    private final boolean standalone;
    private final Object standaloneSource;
    private int syncTicks;
    public ClassicWirelessMenu(int id,Inventory player,FriendlyByteBuf wire){this(id,player,wire.readBlockPos(),Kind.values()[Math.max(0,Math.min(2,wire.readVarInt()))],wire.readUUID(),null);}
    public ClassicWirelessMenu(int id,Inventory player,BlockPos sourcePos,Kind kind,UUID token,Container inventory){
        super(ClassicWirelessDevices.MENU.get(),id);this.sourcePos=sourcePos.immutable();this.kind=kind;this.token=token;this.playerInventory=player;standaloneSource=player.player.level().hasChunkAt(sourcePos)?player.player.level().getBlockEntity(sourcePos):null;standalone=kind==Kind.USER&&standaloneSource instanceof cn.academy.port.bridge.ClassicEnergyBridgeBlockEntity;
        int size=kind==Kind.NODE?2:kind==Kind.MATRIX?4:0;this.inventory=inventory==null?new SimpleContainer(size):inventory;checkContainerSize(this.inventory,size);
        if(kind==Kind.NODE){addSlot(nativeSlot(0,42,10));addSlot(nativeSlot(1,42,80));}
        if(kind==Kind.MATRIX){addSlot(nativeSlot(0,78,11));addSlot(nativeSlot(1,53,60));addSlot(nativeSlot(2,104,60));addSlot(nativeSlot(3,78,36));}
        if(!standalone){for(int column=0;column<9;column++)addSlot(new Slot(player,column,6+18*column,163));
        for(int row=1;row<4;row++)for(int column=0;column<9;column++)addSlot(new Slot(player,(4-row)*9+column,6+18*column,159-row*18));}
    }
    private Slot nativeSlot(int index,int x,int y){return new Slot(inventory,index,x,y){@Override public boolean mayPlace(ItemStack stack){return kind==Kind.NODE?ClassicEnergyItemHelper.isSupported(stack):index<3?stack.is(ClassicMaterials.CONSTRAINT_PLATE.get()):ClassicWirelessMatrixBlockEntity.coreLevel(stack)>0;}@Override public int getMaxStackSize(){return kind==Kind.MATRIX?1:super.getMaxStackSize();}};}
    public boolean standalone(){return standalone;}
    public BlockPos sourcePos(){return sourcePos;}
    public Kind kind(){return kind;}
    public UUID token(){return token;}
    public CompoundTag snapshot(){return snapshot.copy();}
    /** Server actions never read this client presentation payload. */
    public void acceptSnapshot(CompoundTag data){snapshot=data==null?new CompoundTag():data.copy();}
    @Override public boolean stillValid(Player player){return player.level().isClientSide||player instanceof ServerPlayer server&&ClassicWirelessProtocol.reachable(server,sourcePos,kind)&&(!standalone||player.level().getBlockEntity(sourcePos)==standaloneSource);}
    @Override public void broadcastChanges(){super.broadcastChanges();if(++syncTicks>=20){syncTicks=0;if(playerInventory.player instanceof ServerPlayer server)ClassicWirelessProtocol.sendSnapshot(server,this,"");}}
    @Override public ItemStack quickMoveStack(Player player,int index){
        int machines=kind==Kind.NODE?2:kind==Kind.MATRIX?4:0;if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var original=stack.copy();
        if(index<machines){if(!moveItemStackTo(stack,machines,slots.size(),false))return ItemStack.EMPTY;}
        else if(kind==Kind.NODE){if(!ClassicEnergyItemHelper.isSupported(stack)||!moveItemStackTo(stack,0,2,false))return ItemStack.EMPTY;}
        else if(kind==Kind.MATRIX){if(stack.is(ClassicMaterials.CONSTRAINT_PLATE.get())){if(!moveItemStackTo(stack,0,3,false))return ItemStack.EMPTY;}else if(ClassicWirelessMatrixBlockEntity.coreLevel(stack)>0){if(!moveItemStackTo(stack,3,4,false))return ItemStack.EMPTY;}else return ItemStack.EMPTY;}
        else return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return original;
    }
}
