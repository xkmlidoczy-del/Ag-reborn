/* AcademyCraft1.0.7 TileMatrix capacity/range and source inventory, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Source eight-cell matrix; only canonical origin supplies graph/inventory control. */
public final class ClassicWirelessMatrixBlockEntity extends ClassicWirelessInventory implements ImagFluxMatrix {
    private int displayPlates,displayCore,ticker;
    public ClassicWirelessMatrixBlockEntity(BlockPos pos,BlockState state){super(ClassicWirelessDevices.MATRIX_TILE.get(),pos,state,4);}
    public boolean isOrigin(){return getBlockState().getValue(ClassicWirelessMatrixBlock.PART)==0;}
    @Override public boolean isWirelessOrigin(){return isOrigin();}
    public Direction facing(){return getBlockState().getValue(ClassicWirelessMatrixBlock.FACING);}
    public BlockPos originPos(){return ClassicWirelessMatrixBlock.origin(worldPosition,getBlockState());}
    public ClassicWirelessMatrixBlockEntity origin(){if(level==null||isRemoved()||!level.hasChunkAt(originPos()))return null;var found=level.getBlockEntity(originPos());return found instanceof ClassicWirelessMatrixBlockEntity root&&root.isOrigin()&&root.facing()==facing()?root:null;}
    @Override public boolean available(){return level!=null&&!level.isClientSide&&!isRemoved()&&isOrigin()&&level.getBlockEntity(worldPosition)==this&&ClassicWirelessMatrixStructure.complete(level,worldPosition,getBlockState());}
    public static int coreLevel(ItemStack stack){if(stack==null||stack.isEmpty())return 0;ResourceLocation id=BuiltInRegistries.ITEM.getKey(stack.getItem());for(int index=0;index<3;index++)if(id.equals(ResourceLocation.fromNamespaceAndPath("academy","matrix_core_"+index)))return index+1;return 0;}
    public int coreLevel(){return level!=null&&level.isClientSide?displayCore:coreLevel(getItem(3));}
    public int plateCount(){if(level!=null&&level.isClientSide)return displayPlates;int count=0;for(int slot=0;slot<3;slot++)if(!getItem(slot).isEmpty())count++;return count;}
    @Override public int getCapacity(){return available()?ClassicWirelessRules.matrixCapacity(coreLevel(),plateCount()):0;}
    @Override public double getBandwidth(){return available()?ClassicWirelessRules.matrixBandwidth(coreLevel(),plateCount()):0;}
    @Override public double getRange(){return available()?ClassicWirelessRules.matrixRange(coreLevel(),plateCount()):0;}
    @Override public int getMaxStackSize(){return 1;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot>=0&&slot<3?stack.is(cn.academy.port.survival.ClassicMaterials.CONSTRAINT_PLATE.get()):slot==3&&coreLevel(stack)>0;}
    @Override public void setItem(int slot,ItemStack stack){if(!stack.isEmpty()&&!canPlaceItem(slot,stack))return;super.setItem(slot,stack);}
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicWirelessMatrixBlockEntity matrix){
        if(!matrix.isOrigin()){if(level.getGameTime()%20==0&&level.hasChunkAt(matrix.originPos())&&matrix.origin()==null)level.removeBlock(pos,false);return;}
        if(++matrix.ticker>=15){matrix.ticker=0;if(ClassicWirelessMatrixStructure.allLoaded(level,pos,state)&&!ClassicWirelessMatrixStructure.complete(level,pos,state)){level.removeBlock(pos,false);return;}level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putInt("plateCount",plateCount());tag.putInt("coreLevel",coreLevel());tag.putString("placer",ownerName);return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){displayPlates=Math.max(0,Math.min(3,tag.getInt("plateCount")));displayCore=Math.max(0,Math.min(3,tag.getInt("coreLevel")));ownerName=tag.getString("placer");}
}
