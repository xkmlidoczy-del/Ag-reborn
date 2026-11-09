/* AcademyCraft1.0.7 TileImagFusor and TileReceiverBase. GPLv3. See NOTICE. */
package cn.academy.port.fusion;
import cn.academy.port.energy.ClassicEnergy;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.survival.ClassicMaterials;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class ClassicFusorBlockEntity extends BlockEntity implements WorldlyContainer,ImagFluxReceiver {
    public static final int INPUT=0,OUTPUT=1,IMAG_INPUT=2,ENERGY_INPUT=3,IMAG_OUTPUT=4;
    private final NonNullList<ItemStack> inventory=NonNullList.withSize(5,ItemStack.EMPTY);
    private final ClassicFusorWork work=new ClassicFusorWork();
    private double energy;
    private int syncTicks;
    private final FluidTank tank=new FluidTank(8000,fluid->fluid.getFluid().isSame(ClassicFusion.PHASE_SOURCE.get())){
        @Override protected void onContentsChanged(){setChanged();}
    };
    private final IFluidHandler fluidHandler=new IFluidHandler(){
        public int getTanks(){return 1;}public int getTankCapacity(int index){return index==0?8000:0;}
        public FluidStack getFluidInTank(int index){return index==0&&available()?tank.getFluid().copy():FluidStack.EMPTY;}
        public boolean isFluidValid(int index,FluidStack stack){return index==0&&tank.isFluidValid(stack);}
        public int fill(FluidStack stack,FluidAction action){return available()?tank.fill(stack,action):0;}
        public FluidStack drain(FluidStack stack,FluidAction action){return available()?tank.drain(stack,action):FluidStack.EMPTY;}
        public FluidStack drain(int amount,FluidAction action){return available()&&amount>0?tank.drain(amount,action):FluidStack.EMPTY;}
    };
    private final ClassicFusorWork.Access access=new ClassicFusorWork.Access(){
        public int recipeForInput(){return recipe(inventory.get(INPUT));}
        public boolean inputMatches(int recipe){return inventory.get(INPUT).is(input(recipe));}
        public boolean enoughInput(int recipe){return !inventory.get(INPUT).isEmpty()&&inventory.get(INPUT).getCount()>=1;}
        public boolean outputTypeMatches(int recipe){return inventory.get(OUTPUT).isEmpty()||inventory.get(OUTPUT).is(output(recipe));}
        public boolean outputAvailable(int recipe){var out=inventory.get(OUTPUT);var result=new ItemStack(output(recipe));return out.isEmpty()||ItemStack.isSameItemSameComponents(out,result)&&out.getCount()+1<=out.getMaxStackSize();}
        public int liquid(){return tank.getFluidAmount();}
        public double pullEnergy(double amount){return ClassicFusorBlockEntity.this.pullEnergy(amount);}
        public void complete(int recipe){tank.drain(ClassicFusorWork.liquidRequired(recipe),IFluidHandler.FluidAction.EXECUTE);inventory.get(INPUT).shrink(1);if(inventory.get(INPUT).isEmpty())inventory.set(INPUT,ItemStack.EMPTY);if(inventory.get(OUTPUT).isEmpty())inventory.set(OUTPUT,new ItemStack(output(recipe)));else inventory.get(OUTPUT).grow(1);setChanged();}
    };
    private final ContainerData data=new ContainerData(){
        public int get(int index){return switch(index){case 0,1,2,3->(int)((Double.doubleToLongBits(energy) >>> (index*16))&65535L);case 4->tank.getFluidAmount();case 5->(int)(work.progress()*10000);case 6->work.recipe()+1;default->0;};}
        public void set(int index,int value){}public int getCount(){return 7;}
    };
    public ClassicFusorBlockEntity(BlockPos pos,BlockState state){super(ClassicFusion.FUSOR_TILE.get(),pos,state);}
    public boolean available(){return level!=null&&!level.isClientSide&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&getBlockState().is(ClassicFusion.FUSOR_BLOCK.get());}
    public static int recipe(ItemStack stack){return stack.is(ClassicMaterials.CRYSTAL_LOW.get())?0:stack.is(ClassicMaterials.CRYSTAL_NORMAL.get())?1:-1;}
    private static Item input(int recipe){return recipe==0?ClassicMaterials.CRYSTAL_LOW.get():ClassicMaterials.CRYSTAL_NORMAL.get();}
    private static Item output(int recipe){return recipe==0?ClassicMaterials.CRYSTAL_NORMAL.get():ClassicMaterials.CRYSTAL_PURE.get();}
    public IFluidHandler fluidHandler(){return fluidHandler;}
    public int liquid(){return tank.getFluidAmount();}
    public double workProgress(){return work.progress();}
    public boolean isWorking(){return work.working();}
    public boolean isActionBlocked(){return work.actionBlocked(access);}
    @Override public double getEnergy(){return available()?energy:0;}
    @Override public double getMaxEnergy(){return 2000;}
    @Override public double getBandwidth(){return 50;}
    @Override public double getRequiredEnergy(){return available()?2000-energy:0;}
    @Override public double injectEnergy(double amount){if(!available()||!Double.isFinite(amount)||amount<=0)return amount;double accepted=Math.min(amount,2000-energy);energy+=accepted;if(accepted>0)setChanged();return amount-accepted;}
    @Override public double pullEnergy(double amount){if(!available()||!Double.isFinite(amount)||amount<=0)return 0;double taken=Math.min(amount,energy);energy-=taken;if(taken>0)setChanged();return taken;}
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicFusorBlockEntity tile){
        if(!tile.available())return;
        tile.work.tick(tile.access);
        var matterIn=tile.inventory.get(IMAG_INPUT);var matterOut=tile.inventory.get(IMAG_OUTPUT);
        if(ClassicMatterUnitItem.filled(matterIn)&&(matterOut.isEmpty()||ClassicMatterUnitItem.empty(matterOut)&&matterOut.getCount()<matterOut.getMaxStackSize())&&tile.tank.getSpace()>=1000){
            tile.tank.fill(new FluidStack(ClassicFusion.PHASE_SOURCE.get(),1000),IFluidHandler.FluidAction.EXECUTE);matterIn.shrink(1);if(matterIn.isEmpty())tile.inventory.set(IMAG_INPUT,ItemStack.EMPTY);if(matterOut.isEmpty())tile.inventory.set(IMAG_OUTPUT,new ItemStack(ClassicFusion.MATTER_UNIT.get()));else matterOut.grow(1);tile.setChanged();
        }
        ItemStack battery=tile.inventory.get(ENERGY_INPUT);double gain=ClassicEnergyItemHelper.pull(battery,Math.min(2000-tile.energy,50),false);tile.injectEnergy(gain);
        boolean working=tile.work.working();if(state.getValue(ClassicFusorBlock.WORKING)!=working){state=state.setValue(ClassicFusorBlock.WORKING,working);level.setBlock(pos,state,Block.UPDATE_CLIENTS);}
        if(++tile.syncTicks>=5){tile.syncTicks=0;level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putDouble("energy",energy);ContainerHelper.saveAllItems(tag,inventory,lookup);tank.writeToNBT(lookup,tag);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);energy=ClassicEnergy.sanitize(tag.getDouble("energy"),2000);inventory.clear();ContainerHelper.loadAllItems(tag,inventory,lookup);tank.readFromNBT(lookup,tag);if(!tank.isEmpty()&&!tank.isFluidValid(tank.getFluid()))tank.setFluid(FluidStack.EMPTY);else if(tank.getFluidAmount()>8000)tank.getFluid().setAmount(8000);work.reset();}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putDouble("energy",energy);tag.putInt("liquid",tank.getFluidAmount());tag.putBoolean("working",work.working());tag.putBoolean("blocked",work.actionBlocked(access));return tag;}
    private boolean clientWorking,clientBlocked;
    private static java.util.function.Consumer<ClassicFusorBlockEntity> clientObserver=tile->{};
    public static void setClientObserver(java.util.function.Consumer<ClassicFusorBlockEntity> observer){clientObserver=java.util.Objects.requireNonNull(observer);}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){energy=ClassicEnergy.sanitize(tag.getDouble("energy"),2000);clientWorking=tag.getBoolean("working");clientBlocked=tag.getBoolean("blocked");clientObserver.accept(this);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider lookup){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),lookup);}
    public boolean clientActive(){return clientWorking&&!clientBlocked;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    public Component getDisplayName(){return Component.translatable("block.academy.imag_fusor");}
    public AbstractContainerMenu createMenu(int id,Inventory player,Player owner){return new ClassicFusorMenu(id,player,this,data);}
    @Override public int getContainerSize(){return 5;}
    @Override public boolean isEmpty(){return inventory.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<5?inventory.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot<0||slot>=5)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(inventory,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return slot>=0&&slot<5?ContainerHelper.takeItem(inventory,slot):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=5)return;inventory.set(slot,stack);stack.limitSize(getMaxStackSize(stack));setChanged();}
    @Override public boolean stillValid(Player player){return available()&&player.level()==level&&player.distanceToSqr(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5)<64;}
    @Override public void clearContent(){inventory.clear();setChanged();}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return switch(slot){case INPUT->recipe(stack)>=0;case IMAG_INPUT->ClassicMatterUnitItem.filled(stack);case ENERGY_INPUT->ClassicEnergyItemHelper.isSupported(stack);default->false;};}
    @Override public int[] getSlotsForFace(Direction side){return side==Direction.UP?new int[]{INPUT,IMAG_INPUT}:side==Direction.DOWN?new int[]{OUTPUT,IMAG_OUTPUT,ENERGY_INPUT}:new int[]{ENERGY_INPUT};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return available()&&canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return available()&&side==Direction.DOWN;}
}
