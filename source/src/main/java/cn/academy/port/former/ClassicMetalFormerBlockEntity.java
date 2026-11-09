/* AcademyCraft 1.0.7 TileMetalFormer/TileReceiverBase/TileInventory adaptation. GPLv3. */
package cn.academy.port.former;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import cn.academy.port.energy.*;
import cn.academy.port.machine.ImagFluxReceiver;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ClassicMetalFormerBlockEntity extends BlockEntity implements WorldlyContainer,ImagFluxReceiver {
    public static final int INPUT=0,OUTPUT=1,BATTERY=2;
    private final NonNullList<ItemStack> inventory=NonNullList.withSize(3,ItemStack.EMPTY);
    private final ClassicMetalFormerWork work=new ClassicMetalFormerWork();
    private List<ClassicMetalFormerRecipes.Recipe> currentRecipes=List.of();
    private double energy;
    private int syncTicks;
    private final ClassicMetalFormerWork.Access access=new ClassicMetalFormerWork.Access(){
        public int recipeForInput(ClassicMetalFormerWork.Mode mode){currentRecipes=ClassicMetalFormerRecipes.all(level);return ClassicMetalFormerRecipes.find(currentRecipes,inventory.get(INPUT),mode);}
        public boolean accepts(int recipe,ClassicMetalFormerWork.Mode mode){return validRecipe(recipe)&&currentRecipes.get(recipe).accepts(inventory.get(INPUT),mode);}
        public boolean outputAvailable(int recipe){if(!validRecipe(recipe))return false;var result=currentRecipes.get(recipe).output();var out=inventory.get(OUTPUT);return out.isEmpty()||out.getItem()==result.getItem()&&out.getDamageValue()==result.getDamageValue()&&out.getCount()+result.getCount()<=out.getMaxStackSize();}
        public double pullEnergy(double amount){return ClassicMetalFormerBlockEntity.this.pullEnergy(amount);}
        public void complete(int recipe){var selected=currentRecipes.get(recipe);var in=inventory.get(INPUT);in.shrink(selected.input().getCount());if(in.isEmpty())inventory.set(INPUT,ItemStack.EMPTY);var out=inventory.get(OUTPUT);if(out.isEmpty())inventory.set(OUTPUT,selected.output().copy());else out.grow(selected.output().getCount());setChanged();}
    };
    private final ContainerData data=new ContainerData(){
        public int get(int index){return switch(index){case 0,1,2,3->(int)((Double.doubleToLongBits(energy) >>> (index*16))&65535L);case 4->work.mode().ordinal();case 5->work.counter();case 6->work.working()?1:0;default->0;};}
        public void set(int index,int value){}public int getCount(){return 7;}
    };
    public ClassicMetalFormerBlockEntity(BlockPos pos,BlockState state){super(ClassicMetalFormer.TILE.get(),pos,state);}
    private boolean validRecipe(int recipe){return recipe>=0&&recipe<currentRecipes.size();}
    public boolean available(){return level!=null&&!level.isClientSide&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&getBlockState().is(ClassicMetalFormer.BLOCK.get());}
    public ClassicMetalFormerWork.Mode mode(){return work.mode();}
    public double workProgress(){return work.progress();}
    public int workCounter(){return work.counter();}
    public boolean isWorking(){return work.working();}
    public boolean isActionBlocked(){return work.actionBlocked(access);}
    public void cycleMode(int delta){if(!available()||(delta!=1&&delta!=-1))return;work.cycleMode(delta);setChanged();sync();}
    @Override public double getEnergy(){return available()?energy:0;}
    @Override public double getMaxEnergy(){return ClassicMetalFormerWork.CAPACITY;}
    @Override public double getBandwidth(){return ClassicMetalFormerWork.BANDWIDTH;}
    @Override public double getRequiredEnergy(){return available()?getMaxEnergy()-energy:0;}
    @Override public double injectEnergy(double amount){if(!available()||!Double.isFinite(amount)||amount<=0)return amount;double accepted=Math.min(amount,getMaxEnergy()-energy);energy+=accepted;if(accepted>0)setChanged();return amount-accepted;}
    @Override public double pullEnergy(double amount){if(!available()||!Double.isFinite(amount)||amount<=0)return 0;double taken=Math.min(amount,energy);energy-=taken;if(taken>0)setChanged();return taken;}
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicMetalFormerBlockEntity tile){
        if(!tile.available())return;
        tile.work.tick(tile.access);
        // Source work/scan precedes battery charging. A just-starved attempt may refill only afterward.
        var battery=tile.inventory.get(BATTERY);double gain=ClassicEnergyItemHelper.pull(battery,Math.min(tile.getMaxEnergy()-tile.energy,tile.getBandwidth()),false);tile.injectEnergy(gain);
        if(++tile.syncTicks==10){tile.syncTicks=0;tile.sync();}
    }
    private void sync(){if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putDouble("energy",energy);tag.putInt("mode",work.mode().ordinal());ContainerHelper.saveAllItems(tag,inventory,lookup);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);energy=ClassicEnergy.sanitize(tag.getDouble("energy"),getMaxEnergy());inventory.clear();ContainerHelper.loadAllItems(tag,inventory,lookup);work.loadMode(tag.getInt("mode"));currentRecipes=List.of();syncTicks=0;}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putDouble("energy",energy);tag.putInt("mode",work.mode().ordinal());tag.putInt("counter",work.counter());tag.putBoolean("working",work.working());return tag;}
    private boolean clientWorking;
    private int clientCounter;
    private ClassicMetalFormerWork.Mode clientMode=ClassicMetalFormerWork.Mode.PLATE;
    private static Consumer<ClassicMetalFormerBlockEntity> clientObserver=tile->{};
    public static void setClientObserver(Consumer<ClassicMetalFormerBlockEntity> observer){clientObserver=Objects.requireNonNull(observer);}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){energy=ClassicEnergy.sanitize(tag.getDouble("energy"),getMaxEnergy());int ordinal=tag.getInt("mode");clientMode=ordinal>=0&&ordinal<4?ClassicMetalFormerWork.Mode.values()[ordinal]:ClassicMetalFormerWork.Mode.PLATE;clientCounter=Math.max(0,Math.min(59,tag.getInt("counter")));clientWorking=tag.getBoolean("working");clientObserver.accept(this);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider lookup){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),lookup);}
    public boolean clientWorking(){return clientWorking;}
    public ClassicMetalFormerWork.Mode clientMode(){return clientMode;}
    public double clientProgress(){return clientWorking?(double)clientCounter/ClassicMetalFormerWork.WORK_TICKS:0;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    public Component getDisplayName(){return Component.translatable("block.academy.metal_former");}
    public AbstractContainerMenu createMenu(int id,Inventory player,Player owner){return new ClassicMetalFormerMenu(id,player,this,data);}
    @Override public int getContainerSize(){return 3;}
    @Override public boolean isEmpty(){return inventory.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<3?inventory.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot<0||slot>=3)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(inventory,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return slot>=0&&slot<3?ContainerHelper.takeItem(inventory,slot):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=3)return;inventory.set(slot,stack);stack.limitSize(getMaxStackSize(stack));setChanged();}
    @Override public boolean stillValid(Player player){return available()&&player.level()==level&&!player.isRemoved()&&player.isAlive()&&!player.isSpectator()&&player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<64&&level.mayInteract(player,worldPosition);}
    @Override public void clearContent(){inventory.clear();setChanged();}
    // TileInventory.isItemValidForSlot is inherited true in classic. GUI validates independently.
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot>=0&&slot<3;}
    @Override public int[] getSlotsForFace(Direction side){return side==Direction.DOWN?new int[]{OUTPUT,BATTERY}:side==Direction.UP?new int[]{INPUT}:new int[]{BATTERY};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return available()&&canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return available()&&side==Direction.DOWN;}
}
