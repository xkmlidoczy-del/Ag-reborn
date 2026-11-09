/* AcademyCraft1.0.7 TileNode native item loops/finite data, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import cn.academy.port.energy.ClassicEnergyItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ClassicWirelessNodeBlockEntity extends ClassicWirelessInventory implements ImagFluxNode {
    private double energy;
    private String name="Unnamed",password="";
    private boolean enabled,chargingIn,chargingOut;
    private int updateTicker;
    public ClassicWirelessNodeBlockEntity(BlockPos pos,BlockState state){super(ClassicWirelessDevices.NODE_TILE.get(),pos,state,2);}
    public ClassicWirelessRules.NodeType nodeType(){return ((ClassicWirelessNodeBlock)getBlockState().getBlock()).nodeType;}
    @Override public boolean available(){return level!=null&&!level.isClientSide&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&getBlockState().getBlock() instanceof ClassicWirelessNodeBlock;}
    @Override public double getMaxEnergy(){return nodeType().energy;}
    @Override public double getEnergy(){return energy;}
    @Override public void setEnergy(double value){double bounded=ClassicWirelessRules.sanitize(value,getMaxEnergy());if(energy!=bounded){energy=bounded;setChanged();}}
    @Override public double getBandwidth(){return nodeType().bandwidth;}
    @Override public double getRange(){return nodeType().range;}
    @Override public int getCapacity(){return nodeType().capacity;}
    @Override public String getNodeName(){return name;}
    @Override public String getPassword(){return password;}
    public void setNodeName(String name){this.name=name;setChanged();}
    public void setPassword(String password){this.password=password;setChanged();}
    public boolean enabled(){return enabled;}
    public boolean chargingIn(){return chargingIn;}
    public boolean chargingOut(){return chargingOut;}
    public static void serverTick(Level level,BlockPos pos,BlockState state,ClassicWirelessNodeBlockEntity node){
        if(!node.available())return;
        // Source order: input battery first, then output battery. Native-only IF, item bandwidth retained.
        var input=node.getItem(0);double pulled=ClassicEnergyItemHelper.isSupported(input)?ClassicEnergyItemHelper.pull(input,Math.min(node.getBandwidth(),node.getMaxEnergy()-node.energy),false):0;
        node.chargingIn=pulled!=0;node.setEnergy(node.energy+pulled);
        var output=node.getItem(1);double request=Math.min(node.getBandwidth(),node.energy);double remainder=ClassicEnergyItemHelper.isSupported(output)?ClassicEnergyItemHelper.charge(output,request,false):request;
        node.chargingOut=Double.isFinite(remainder)&&remainder!=request;
        double accepted=Double.isFinite(remainder)?Math.max(0,Math.min(request,request-remainder)):0;node.setEnergy(node.energy-accepted);
        if(pulled!=0||accepted!=0)node.setChanged();
        if(++node.updateTicker>=10){node.updateTicker=0;var data=ClassicWirelessSavedData.getNonCreate((ServerLevel)level);node.enabled=data!=null&&data.graph().networkAt(ClassicWirelessSavedData.pos(pos))!=null;
            var next=state.setValue(ClassicWirelessNodeBlock.CONNECTED,node.enabled).setValue(ClassicWirelessNodeBlock.ENERGY_LEVEL,ClassicWirelessRules.level(node.energy,node.getMaxEnergy()));
            if(next!=state)level.setBlock(pos,next,Block.UPDATE_CLIENTS);else level.sendBlockUpdated(pos,state,state,Block.UPDATE_CLIENTS);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.saveAdditional(tag,lookup);tag.putDouble("energy",energy);tag.putString("nodeName",name);tag.putString("password",password);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider lookup){super.loadAdditional(tag,lookup);energy=ClassicWirelessRules.sanitize(tag.getDouble("energy"),getMaxEnergy());name=tag.contains("nodeName")?tag.getString("nodeName"):"Unnamed";password=tag.getString("password");}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider lookup){var tag=new CompoundTag();tag.putDouble("energy",energy);tag.putString("nodeName",name);tag.putString("placer",ownerName);tag.putBoolean("enabled",enabled);tag.putBoolean("chargingIn",chargingIn);tag.putBoolean("chargingOut",chargingOut);return tag;}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider lookup){energy=ClassicWirelessRules.sanitize(tag.getDouble("energy"),getMaxEnergy());name=tag.getString("nodeName");ownerName=tag.getString("placer");enabled=tag.getBoolean("enabled");chargingIn=tag.getBoolean("chargingIn");chargingOut=tag.getBoolean("chargingOut");}
}
