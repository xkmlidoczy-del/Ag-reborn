/* AcademyCraft1.0.7 converter base adapter. GPLv3; see NOTICE. */
package cn.academy.port.bridge;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Zero inventory, zero spontaneous generation. Every retained endpoint rechecks its actual loaded block entity. */
public abstract class ClassicEnergyBridgeBlockEntity extends BlockEntity {
    protected final ClassicEnergyBridgeBuffer buffer=new ClassicEnergyBridgeBuffer();
    private int syncTicks;
    private double publishedEnergy;
    private boolean syncPending;
    protected ClassicEnergyBridgeBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public abstract boolean input();
    public final boolean available(){return level!=null&&!level.isClientSide&&level.getServer()!=null&&level.getServer().isSameThread()&&level.hasChunkAt(worldPosition)&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&getBlockState().is(input()?ClassicEnergyBridges.INPUT.get():ClassicEnergyBridges.OUTPUT.get());}
    public final double presentationEnergy(){return buffer.energy();}
    public final double getEnergy(){return available()?buffer.energy():0;}
    public final double getBandwidth(){return ClassicEnergyBridgeBuffer.BANDWIDTH;}
    public final double getMaxEnergy(){return ClassicEnergyBridgeBuffer.CAPACITY;}
    protected final double take(double request){if(!available())return 0;double taken=buffer.pull(request);if(taken>0)setChanged();return taken;}
    public final IEnergyStorage forgeEnergy(){return forge;}
    private final IEnergyStorage forge=new IEnergyStorage(){
        public int receiveEnergy(int amount,boolean simulate){if(!input()||!available())return 0;int moved=buffer.receive(amount,simulate);if(moved>0&&!simulate)setChanged();return moved;}
        public int extractEnergy(int amount,boolean simulate){if(input()||!available())return 0;int moved=buffer.extract(amount,simulate);if(moved>0&&!simulate)setChanged();return moved;}
        public int getEnergyStored(){return available()?ClassicEnergyBridgeBuffer.fe(buffer.energy()):0;}
        public int getMaxEnergyStored(){return available()?ClassicEnergyBridgeBuffer.fe(ClassicEnergyBridgeBuffer.CAPACITY):0;}
        public boolean canReceive(){return input()&&available();}public boolean canExtract(){return !input()&&available();}
    };
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicEnergyBridgeBlockEntity tile){
        if(!tile.available())return;
        // Source TileReceiverBase publishes BEFORE TileRFOutput's six-neighbor push; input generates exactly zero.
        if(++tile.syncTicks==20){tile.syncTicks=0;tile.publishedEnergy=tile.buffer.energy();tile.syncPending=true;level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}
        if(!tile.input())for(Direction direction:Direction.values()){
            BlockPos neighbor=pos.relative(direction);if(!level.hasChunkAt(neighbor)||tile.buffer.energy()<=0)continue;
            var other=level.getBlockEntity(neighbor);if(other!=null&&other.isRemoved())continue;
            var endpoint=level.getCapability(Capabilities.EnergyStorage.BLOCK,neighbor,level.getBlockState(neighbor),other,direction.getOpposite());
            if(tile.buffer.push(endpoint)>0)tile.setChanged();
        }
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putDouble("energy",buffer.energy());}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);buffer.load(tag.getDouble("energy"));syncTicks=0;syncPending=false;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putDouble("energy",buffer.energy());return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){buffer.load(tag.getDouble("energy"));}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){double value=syncPending?publishedEnergy:buffer.energy();syncPending=false;return ClientboundBlockEntityDataPacket.create(this,(tile,lookup)->{var tag=new CompoundTag();tag.putDouble("energy",value);return tag;});}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider lookup){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),lookup);}
}
