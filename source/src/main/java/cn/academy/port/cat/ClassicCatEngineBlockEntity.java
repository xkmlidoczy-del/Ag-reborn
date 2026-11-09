/* AcademyCraft1.0.7 TileCatEngine; server authoritative, common-only. GPLv3. */
package cn.academy.port.cat;
import cn.academy.port.solar.ImagFluxGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class ClassicCatEngineBlockEntity extends BlockEntity implements ImagFluxGenerator {
    private final ClassicCatBuffer buffer=new ClassicCatBuffer();
    private int syncTicks;
    private double visibleEnergy,visibleGenerated;
    public ClassicCatEngineBlockEntity(BlockPos pos,BlockState state){super(ClassicCatEngines.TILE.get(),pos,state);}
    public boolean available(){return level!=null&&!level.isClientSide&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&getBlockState().is(ClassicCatEngines.BLOCK.get());}
    public ClassicCatBuffer buffer(){return buffer;}
    @Override public double getEnergy(){return available()?buffer.energy():0;}
    @Override public double getBandwidth(){return ClassicCatBuffer.BANDWIDTH;}
    @Override public double getProvidedEnergy(double request){if(!available())return 0;double supplied=buffer.provide(request);if(supplied>0)setChanged();return supplied;}
    public double addEnergy(double amount,boolean simulate){if(!available())return amount;double remainder=buffer.addEnergy(amount,simulate);if(!simulate&&remainder!=amount)setChanged();return remainder;}
    public double generation(){return level!=null&&level.isClientSide?visibleGenerated:buffer.generated();}
    public double presentationEnergy(){return level!=null&&level.isClientSide?visibleEnergy:buffer.energy();}
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicCatEngineBlockEntity tile){
        if(!tile.available())return;
        if(tile.buffer.tick()!=0)tile.setChanged();
        // Both source generator-energy and genspeed messages occur every20 ticks, after generation.
        if(++tile.syncTicks>=20){tile.syncTicks=0;level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putDouble("energy",buffer.energy());}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);buffer.load(tag.getDouble("energy"));syncTicks=0;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putDouble("energy",buffer.energy());tag.putDouble("generation",buffer.generated());return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){
        if(tag.contains("energy"))visibleEnergy=bounded(tag.getDouble("energy"),ClassicCatBuffer.CAPACITY);
        if(tag.contains("generation"))visibleGenerated=bounded(tag.getDouble("generation"),ClassicCatBuffer.GENERATION);
    }
    private static double bounded(double value,double max){return Double.isFinite(value)?Math.max(0,Math.min(max,value)):0;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
