/* AcademyCraft1.0.7 TileWindGenMain, GPLv3; see NOTICE. */
package cn.academy.port.wind;

import java.util.ArrayList;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ClassicWindMainBlockEntity extends ClassicWindInventory {
    private boolean complete,noObstacle;
    private int structureTicks,fanSyncTicks;
    private boolean pendingStructure,pendingFan;
    private ItemStack publishedFan=ItemStack.EMPTY;
    /** Original per-tile render state; unsaved and never accepted from clients. */
    public long lastFrame=-1;
    public float lastRotation;
    public ClassicWindMainBlockEntity(BlockPos pos,BlockState state){super(ClassicWindGenerators.MAIN_TILE.get(),pos,state);}
    public boolean complete(){return complete;}
    public boolean noObstacle(){return noObstacle;}
    public boolean fanInstalled(){return getItem(0).is(ClassicWindGenerators.FAN.get());}
    public double spinSpeed(){return complete?60:0;}
    private boolean checkTower(){var cells=new ArrayList<ClassicWindRules.CellKind>();for(int offset=1;offset<=ClassicWindRules.MAX_PILLARS+1;offset++){var pos=worldPosition.below(offset);if(!level.hasChunkAt(pos))return false;var state=level.getBlockState(pos);var cell=state.is(ClassicWindGenerators.PILLAR.get())?ClassicWindRules.CellKind.PILLAR:state.is(ClassicWindGenerators.BASE.get())?ClassicWindRules.CellKind.BASE:ClassicWindRules.CellKind.OTHER;cells.add(cell);if(cell!=ClassicWindRules.CellKind.PILLAR)break;}return ClassicWindRules.scanMain(cells);}
    public boolean checkObstacle(){for(var cell:ClassicWindRules.obstaclePlane(ClassicWindRules.Facing.valueOf(getBlockState().getValue(ClassicWindBlock.FACING).name()))){var pos=worldPosition.offset(cell.x(),cell.y(),cell.z());if(!level.hasChunkAt(pos)||!level.getBlockState(pos).isAir())return false;}return true;}
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicWindMainBlockEntity tile){if(!tile.checkIntegrity())return;if(++tile.structureTicks==10){tile.structureTicks=0;tile.complete=tile.checkTower();tile.noObstacle=tile.complete&&tile.checkObstacle();tile.pendingStructure=true;level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}if(++tile.fanSyncTicks==20){tile.fanSyncTicks=0;tile.publishedFan=tile.getItem(0).copy();tile.pendingFan=true;level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}}
    @Override protected int datum(int index){return index==4?complete?2:0:index==5?worldPosition.getY():index==6?noObstacle?1:0:0;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot!=0||stack.is(ClassicWindGenerators.FAN.get());}
    @Override public Component getDisplayName(){return Component.translatable("block.academy.windgen_main");}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);complete=noObstacle=false;structureTicks=fanSyncTicks=0;lastFrame=-1;lastRotation=0;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putBoolean("complete",complete);tag.putBoolean("no_obstacle",noObstacle);tag.putBoolean("fan_present",true);if(!getItem(0).isEmpty())tag.put("fan",getItem(0).save(lookup));return tag;}
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){if(!pendingStructure&&!pendingFan)return super.getUpdatePacket();var tag=new CompoundTag();tag.putBoolean("complete",complete);tag.putBoolean("no_obstacle",noObstacle);if(pendingFan){tag.putBoolean("fan_present",true);if(!publishedFan.isEmpty())tag.put("fan",publishedFan.save(level.registryAccess()));}pendingStructure=pendingFan=false;return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this,(ignored,lookup)->tag);}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){complete=tag.getBoolean("complete");noObstacle=tag.getBoolean("no_obstacle");if(tag.contains("fan_present"))inventory.set(0,tag.contains("fan")?ItemStack.parseOptional(lookup,tag.getCompound("fan")):ItemStack.EMPTY);}
}
