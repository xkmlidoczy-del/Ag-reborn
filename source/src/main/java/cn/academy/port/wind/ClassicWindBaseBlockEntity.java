/* AcademyCraft1.0.7 TileWindGenBase/TileGeneratorBase, GPLv3; see NOTICE. */
package cn.academy.port.wind;

import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.solar.ImagFluxGenerator;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;

public final class ClassicWindBaseBlockEntity extends ClassicWindInventory implements ImagFluxGenerator {
    private final ClassicWindBuffer buffer=new ClassicWindBuffer();
    private ClassicWindRules.Completeness completeness=ClassicWindRules.Completeness.BASE_ONLY;
    private BlockPos mainPos;
    private boolean towerLoaded;
    private int structureTicks,energyTicks;
    private boolean pendingEnergy,pendingStructure,clientWorking;
    private double publishedEnergy;
    public ClassicWindBaseBlockEntity(BlockPos pos,BlockState state){super(ClassicWindGenerators.BASE_TILE.get(),pos,state);}
    public ClassicWindBuffer buffer(){return buffer;}
    public double presentationEnergy(){return buffer.energy();}
    @Override public double getEnergy(){return available()?buffer.energy():0;}
    @Override public double getBandwidth(){return ClassicWindRules.BANDWIDTH;}
    @Override public double getProvidedEnergy(double amount){if(!available())return 0;double supplied=buffer.getProvidedEnergy(amount);if(supplied>0)setChanged();return supplied;}
    public ClassicWindRules.Completeness completeness(){return ClassicWindRules.presentation(completeness,level!=null&&level.isClientSide?clientWorking:working());}
    public boolean isComplete(){return completeness==ClassicWindRules.Completeness.COMPLETE;}
    private ClassicWindMainBlockEntity main(){return level!=null&&mainPos!=null&&level.hasChunkAt(mainPos)&&level.getBlockEntity(mainPos) instanceof ClassicWindMainBlockEntity tile&&tile.isOrigin()?tile:null;}
    private boolean working(){var main=main();return towerLoaded&&ClassicWindRules.generates(completeness,main!=null&&main.available()&&main.complete(),main!=null&&main.fanInstalled());}
    public double simulatedGeneration(){return ClassicWindRules.generation(mainPos==null?0:mainPos.getY(),working());}
    private void updateTower(){var cells=new ArrayList<ClassicWindRules.CellKind>();towerLoaded=true;for(int offset=2;offset<=ClassicWindRules.MAX_PILLARS+3;offset++){var pos=worldPosition.above(offset);if(!level.hasChunkAt(pos)){towerLoaded=false;return;}var state=level.getBlockState(pos);var cell=state.is(ClassicWindGenerators.PILLAR.get())?ClassicWindRules.CellKind.PILLAR:state.is(ClassicWindGenerators.MAIN.get())?state.getValue(ClassicWindBlock.PART)==0?ClassicWindRules.CellKind.MAIN_ORIGIN:ClassicWindRules.CellKind.MAIN_PART:ClassicWindRules.CellKind.OTHER;cells.add(cell);if(cell!=ClassicWindRules.CellKind.PILLAR)break;}var tower=ClassicWindRules.scanBase(cells);completeness=tower.completeness();mainPos=tower.mainOffset()==0?null:worldPosition.above(tower.mainOffset());}
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicWindBaseBlockEntity tile){
        if(!tile.checkIntegrity())return;
        // Source order: generate using previous10tick tower state, publish energy, then refresh tower, then charge.
        double before=tile.buffer.energy();tile.buffer.generate(tile.mainPos==null?0:tile.mainPos.getY(),tile.working());
        if(++tile.energyTicks==20){tile.energyTicks=0;tile.publishedEnergy=tile.buffer.energy();tile.pendingEnergy=true;level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}
        if(++tile.structureTicks==10){tile.structureTicks=0;tile.updateTower();tile.pendingStructure=true;level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}
        tile.buffer.charge(request->request-ClassicEnergyItemHelper.charge(tile.getItem(0),request,false));if(before!=tile.buffer.energy())tile.setChanged();
    }
    @Override protected int datum(int index){return index<4?ClassicWindRules.word(buffer.energy(),index):index==4?completeness().ordinal():index==5?worldPosition.getY():0;}
    @Override public Component getDisplayName(){return Component.translatable("block.academy.windgen_base");}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);if(isOrigin())tag.putDouble("energy",buffer.energy());}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);buffer.load(isOrigin()?tag.getDouble("energy"):0);mainPos=null;towerLoaded=false;completeness=ClassicWindRules.Completeness.BASE_ONLY;structureTicks=energyTicks=0;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putDouble("energy",buffer.energy());tag.putInt("complete",completeness.ordinal());tag.putBoolean("working",working());return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){if(tag.contains("energy"))buffer.load(tag.getDouble("energy"));if(tag.contains("working"))clientWorking=tag.getBoolean("working");if(tag.contains("complete")){int ordinal=tag.getInt("complete");completeness=ClassicWindRules.Completeness.values()[Math.max(0,Math.min(3,ordinal))];}}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){if(!pendingEnergy&&!pendingStructure)return super.getUpdatePacket();var tag=new CompoundTag();tag.putInt("complete",completeness.ordinal());tag.putBoolean("working",working());if(pendingEnergy)tag.putDouble("energy",publishedEnergy);pendingEnergy=pendingStructure=false;return ClientboundBlockEntityDataPacket.create(this,(ignored,lookup)->tag);}
}
