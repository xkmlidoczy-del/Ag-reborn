/* AcademyCraft1.0.7 TilePhaseGen/TileGeneratorBase/TileInventory native adapter. GPLv3. See NOTICE. */
package cn.academy.port.phasegen;

import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.fusion.ClassicMatterUnitItem;
import cn.academy.port.solar.ImagFluxGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Uses the existing genuine phase fluid/material endpoints; generation never draws world fluid implicitly. */
public final class ClassicPhaseGeneratorBlockEntity extends BlockEntity implements Container,ImagFluxGenerator {
    public static final int LIQUID_IN=0,LIQUID_OUT=1,OUTPUT=2;
    private final NonNullList<ItemStack> inventory=NonNullList.withSize(3,ItemStack.EMPTY);
    private final ClassicPhaseGeneratorBuffer buffer=new ClassicPhaseGeneratorBuffer();
    private int fluidSyncTicks,energySyncTicks;
    private boolean pendingFluidSync,pendingEnergySync;
    private int publishedLiquid;
    private double publishedEnergy;
    private final ContainerData data=new ContainerData(){
        public int get(int index){return index>=0&&index<4?ClassicPhaseGeneratorRules.word(buffer.energy(),index):index==4?buffer.liquid():0;}
        public void set(int index,int value){}public int getCount(){return 5;}
    };
    private final IFluidHandler fluidHandler=new IFluidHandler(){
        public int getTanks(){return 1;}
        public int getTankCapacity(int index){return index==0?ClassicPhaseGeneratorRules.TANK_SIZE:0;}
        public FluidStack getFluidInTank(int index){return index==0&&available()?phase(buffer.liquid()):FluidStack.EMPTY;}
        public boolean isFluidValid(int index,FluidStack fluid){return index==0&&!fluid.isEmpty()&&fluid.getFluid().isSame(ClassicFusion.PHASE_SOURCE.get());}
        public int fill(FluidStack fluid,FluidAction action){if(!available()||!isFluidValid(0,fluid))return 0;int accepted=buffer.fill(fluid.getAmount(),action.simulate());if(accepted>0&&action.execute())setChanged();return accepted;}
        public FluidStack drain(FluidStack fluid,FluidAction action){return isFluidValid(0,fluid)?drain(fluid.getAmount(),action):FluidStack.EMPTY;}
        public FluidStack drain(int amount,FluidAction action){if(!available()||amount<=0)return FluidStack.EMPTY;int taken=buffer.drain(amount,action.simulate());if(taken>0&&action.execute())setChanged();return phase(taken);}
    };
    private static FluidStack phase(int amount){return amount>0?new FluidStack(ClassicFusion.PHASE_SOURCE.get(),amount):FluidStack.EMPTY;}
    public ClassicPhaseGeneratorBlockEntity(BlockPos pos,BlockState state){super(ClassicPhaseGenerators.TILE.get(),pos,state);}
    public boolean available(){return level!=null&&!level.isClientSide&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&getBlockState().is(ClassicPhaseGenerators.BLOCK.get());}
    public ClassicPhaseGeneratorBuffer buffer(){return buffer;}
    public IFluidHandler fluidHandler(){return fluidHandler;}
    public int liquid(){return buffer.liquid();}
    public int getTankSize(){return ClassicPhaseGeneratorRules.TANK_SIZE;}
    public double presentationEnergy(){return buffer.energy();}
    @Override public double getEnergy(){return available()?buffer.energy():0;}
    @Override public double getBandwidth(){return ClassicPhaseGeneratorRules.BANDWIDTH;}
    @Override public double getProvidedEnergy(double request){if(!available())return 0;double provided=buffer.getProvidedEnergy(request);if(provided>0)setChanged();return provided;}
    private void acquireMatterUnit(){
        ItemStack input=inventory.get(LIQUID_IN),output=inventory.get(LIQUID_OUT);
        if(!ClassicMatterUnitItem.filled(input)||!ClassicPhaseGeneratorRules.canAcquireUnit(buffer.liquid())||!(output.isEmpty()||ClassicMatterUnitItem.empty(output)&&output.getCount()<output.getMaxStackSize()))return;
        // All guards precede debit: one whole1000mB container is consumed and one empty shell refunded.
        if(buffer.fill(ClassicPhaseGeneratorRules.PER_UNIT,false)!=ClassicPhaseGeneratorRules.PER_UNIT)throw new IllegalStateException("Phase generator unit exceeds guarded tank space");
        input.shrink(1);if(input.isEmpty())inventory.set(LIQUID_IN,ItemStack.EMPTY);
        if(output.isEmpty())inventory.set(LIQUID_OUT,new ItemStack(ClassicFusion.MATTER_UNIT.get()));else output.grow(1);
        setChanged();
    }
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicPhaseGeneratorBlockEntity tile){
        if(!tile.available())return;
        // TileGeneratorBase.updateEntity runs first, including its20tick energy publication.
        double energyBefore=tile.buffer.energy();int fluidBefore=tile.buffer.liquid();
        tile.buffer.generate();
        boolean energyDue=++tile.energySyncTicks>=20,fluidDue=++tile.fluidSyncTicks>=10;
        if(energyDue){tile.energySyncTicks=0;tile.publishedEnergy=tile.buffer.energy();tile.pendingEnergySync=true;}
        if(fluidDue){tile.fluidSyncTicks=0;tile.publishedLiquid=tile.buffer.liquid();tile.pendingFluidSync=true;}
        if(energyDue||fluidDue)level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);
        tile.acquireMatterUnit();
        ItemStack battery=tile.inventory.get(OUTPUT);
        tile.buffer.charge(request->request-ClassicEnergyItemHelper.charge(battery,request,false));
        if(energyBefore!=tile.buffer.energy()||fluidBefore!=tile.buffer.liquid())tile.setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putDouble("energy",buffer.energy());tag.putInt("liquid",buffer.liquid());ContainerHelper.saveAllItems(tag,inventory,lookup);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);buffer.load(tag.getDouble("energy"),tag.getInt("liquid"));inventory.clear();ContainerHelper.loadAllItems(tag,inventory,lookup);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putDouble("energy",buffer.energy());tag.putInt("liquid",buffer.liquid());return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){buffer.load(tag.contains("energy")?tag.getDouble("energy"):buffer.energy(),tag.contains("liquid")?tag.getInt("liquid"):buffer.liquid());}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider lookup){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),lookup);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){
        if(!pendingFluidSync&&!pendingEnergySync)return ClientboundBlockEntityDataPacket.create(this);
        // Source packet snapshots precede unit acquisition/item charging and retain separate10/20tick clocks.
        var tag=new CompoundTag();if(pendingEnergySync)tag.putDouble("energy",publishedEnergy);if(pendingFluidSync)tag.putInt("liquid",publishedLiquid);
        pendingFluidSync=pendingEnergySync=false;return ClientboundBlockEntityDataPacket.create(this,(ignored,lookup)->tag);
    }
    public Component getDisplayName(){return Component.translatable("block.academy.phase_gen");}
    public AbstractContainerMenu createMenu(int id,Inventory player,Player owner){return new ClassicPhaseGeneratorMenu(id,player,this,data);}
    @Override public int getContainerSize(){return 3;}
    @Override public boolean isEmpty(){return inventory.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getItem(int slot){return slot>=0&&slot<3?inventory.get(slot):ItemStack.EMPTY;}
    @Override public ItemStack removeItem(int slot,int count){if(slot<0||slot>=3)return ItemStack.EMPTY;var result=ContainerHelper.removeItem(inventory,slot,count);if(!result.isEmpty())setChanged();return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return slot>=0&&slot<3?ContainerHelper.takeItem(inventory,slot):ItemStack.EMPTY;}
    @Override public void setItem(int slot,ItemStack stack){if(slot<0||slot>=3)return;inventory.set(slot,stack);stack.limitSize(getMaxStackSize(stack));setChanged();}
    @Override public boolean stillValid(Player player){return available()&&player.level()==level&&player.distanceToSqr(worldPosition.getX(),worldPosition.getY(),worldPosition.getZ())<64;}
    @Override public void clearContent(){inventory.clear();setChanged();}
    // Source TileInventory.isItemValidForSlot returns true; default Container insertion preserves that.
}
