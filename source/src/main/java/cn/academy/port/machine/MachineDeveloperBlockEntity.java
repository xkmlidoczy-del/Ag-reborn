package cn.academy.port.machine;

import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentProcess;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Source origin-only receiver/developer, two inventory slots, no persisted user or development session. */
public final class MachineDeveloperBlockEntity extends BlockEntity implements Container {
    private final NonNullList<ItemStack> inventory=NonNullList.withSize(2,ItemStack.EMPTY);
    private final MachineDeveloperEnergy energy;
    private final IEnergyStorage forgeEnergy;
    private UUID user;
    private int syncTicks;
    public MachineDeveloperBlockEntity(BlockPos pos,BlockState state){
        super(MachineDevelopers.BLOCK_ENTITY.get(),pos,state);
        energy=new MachineDeveloperEnergy(((MachineDeveloperBlock)state.getBlock()).type,this::setChanged,this::available);
        forgeEnergy=new MachineDeveloperForgeEnergy(energy,()->level==null?Long.MIN_VALUE:level.getGameTime());
    }
    public DeveloperType developerType(){return ((MachineDeveloperBlock)getBlockState().getBlock()).type;}
    public boolean isOrigin(){return getBlockState().getValue(MachineDeveloperBlock.PART)==0;}
    public Direction facing(){return getBlockState().getValue(MachineDeveloperBlock.FACING);}
    public BlockPos originPos(){return MachineDeveloperBlock.origin(worldPosition,getBlockState());}
    public MachineDeveloperBlockEntity origin(){
        if(level==null||isRemoved()||!level.hasChunkAt(originPos()))return null;
        var found=level.getBlockEntity(originPos());
        return found instanceof MachineDeveloperBlockEntity root&&root.isOrigin()&&root.getBlockState().getBlock()==getBlockState().getBlock()&&root.facing()==facing()?root:null;
    }
    public boolean available(){return level!=null&&!level.isClientSide&&!isRemoved()&&isOrigin()&&level.getBlockEntity(worldPosition)==this&&MachineDeveloperStructure.complete(level,worldPosition,getBlockState());}
    public MachineDeveloperEnergy battery(){return energy;}
    public ImagFluxReceiver imagFluxReceiver(){var root=origin();return root!=null&&root.available()?root.energy:null;}
    public IEnergyStorage forgeEnergy(){var root=origin();return root!=null&&root.available()?root.forgeEnergy:null;}
    public UUID user(){var root=origin();return root==null?null:root.user;}
    void user(UUID user){this.user=user;setChanged();}
    public boolean use(ServerPlayer player){var root=origin();return root!=null&&root.available()&&MachineDeveloperSessions.open(player,root);}
    public void endUsers(){MachineDeveloperSessions.release(this);user=null;}
    public DevelopmentProcess.Developer developer(){return new DevelopmentProcess.Developer(){
        @Override public DeveloperType type(){return developerType();}
        @Override public boolean tryPullEnergy(double amount){return energy.tryPull(amount);}
        @Override public double energy(){return energy.getEnergy();}
        @Override public double maxEnergy(){return energy.getMaxEnergy();}
    };}
    public static void serverTick(Level level,BlockPos pos,BlockState state,MachineDeveloperBlockEntity developer){
        if(!developer.isOrigin()){
            // Unloaded neighboring chunks never force-load or destroy valid structures.
            if(level.getGameTime()%20==0&&level.hasChunkAt(developer.originPos())&&developer.origin()==null)level.removeBlock(pos,false);
            return;
        }
        if(++developer.syncTicks>=20){developer.syncTicks=0;if(MachineDeveloperStructure.allLoaded(level,pos,state)&&!MachineDeveloperStructure.complete(level,pos,state)){level.removeBlock(pos,false);return;}MachineDeveloperSessions.tick(developer);level.sendBlockUpdated(pos,state,state,net.minecraft.world.level.block.Block.UPDATE_CLIENTS);}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putDouble("energy",energy.persistedEnergy());ContainerHelper.saveAllItems(tag,inventory,lookup);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);energy.load(tag.getDouble("energy"));inventory.clear();ContainerHelper.loadAllItems(tag,inventory,lookup);user=null;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putDouble("energy",energy.persistedEnergy());if(user!=null)tag.putUUID("user",user);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void setRemoved(){endUsers();super.setRemoved();}
    @Override public int getContainerSize(){return 2;}
    @Override public boolean isEmpty(){return inventory.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<2?inventory.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int amount){if(slot<0||slot>=2)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(inventory,slot,amount);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return slot>=0&&slot<2?ContainerHelper.takeItem(inventory,slot):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=2)return;inventory.set(slot,stack);stack.limitSize(getMaxStackSize(stack));setChanged();}
    @Override public boolean stillValid(Player player){return available()&&player.level()==level&&player.canInteractWithBlock(worldPosition,0);}
    @Override public void clearContent(){inventory.clear();setChanged();}
}
