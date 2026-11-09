package oracle;
import cn.academy.energy.ModuleEnergy;
import cn.academy.energy.block.wind.*;
import cn.academy.energy.api.*;
import cn.academy.energy.api.item.ImagEnergyItem;
import cn.lambdalib.multiblock.*;
import cn.lambdalib.s11n.network.NetworkMessage;
import net.minecraft.world.World;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
/** Bridge only. Arithmetic/scans/obstacles/schedulers/rotation execute unchanged upstream Java. */
public final class OriginalWindHarness {
    private World world;private TileWindGenBase base;private TileWindGenMain main;private int y,facing;
    public OriginalWindHarness(int mainY,int pillars,boolean fan,int facing){this.y=mainY;this.facing=facing;world=new World();base=baseAt(0,mainY-pillars-2,0,0);baseAt(0,mainY-pillars-1,0,1);for(int i=1;i<=pillars;i++)world.setBlock(0,mainY-i,0,ModuleEnergy.windgenPillar);main=mainAt(0,mainY,0,0);for(int part=1;part<=2;part++){var offset=BlockMulti.rotate(new BlockMulti.SubBlockPos(0,0,part==1?-1:1),dir());mainAt(offset.dx,mainY,offset.dz,part);}if(fan)main.setInventorySlotContents(0,new ItemStack(ModuleEnergy.windgenFan));for(int i=0;i<10;i++){main.updateEntity();base.updateEntity();}}
    private ForgeDirection dir(){return ForgeDirection.valueOf(new String[]{"NORTH","EAST","SOUTH","WEST"}[facing]);}
    private void info(net.minecraft.tileentity.TileEntity tile,int part){var tag=new NBTTagCompound();tag.setByte("dir",(byte)dir().ordinal());tag.setInteger("sub",part);((IMultiTile)tile).setBlockInfo(new InfoBlockMulti(tile,tag));}
    private TileWindGenBase baseAt(int x,int y,int z,int part){var tile=new TileWindGenBase();world.setBlock(x,y,z,ModuleEnergy.windgenBase);world.setTileEntity(x,y,z,tile);info(tile,part);return tile;}
    private TileWindGenMain mainAt(int x,int y,int z,int part){var tile=new TileWindGenMain();world.setBlock(x,y,z,ModuleEnergy.windgenMain);world.setTileEntity(x,y,z,tile);info(tile,part);return tile;}
    public double energy(){return base.getEnergy();}public void energy(double amount){base.setEnergy(amount);}public double capacity(){return base.bufferSize;}public double bandwidth(){return base.getBandwidth();}public double simulated(){return base.getSimulatedGeneration();}public int completeness(){return base.getCompleteness().ordinal();}public boolean complete(){return main.complete;}public boolean noObstacle(){return main.noObstacle;}
    public double generate(){base.setInventorySlotContents(0,null);double before=energy();base.updateEntity();return energy()-before;}
    public double tick(double capacity,double bandwidth){var stack=new ItemStack(new Battery(capacity,bandwidth));base.setInventorySlotContents(0,stack);base.updateEntity();return IFItemManager.instance.getEnergy(stack);}
    public double addEnergy(double amount,boolean simulate){return base.addEnergy(amount,simulate);}public double provide(double amount){return base.getProvidedEnergy(amount);}
    public void obstacle(int x,int dy,int z){world.setBlock(x,y+dy,z,new Block(Material.rock));for(int i=0;i<10;i++)main.updateEntity();}
    public void removeObstacle(int x,int dy,int z){world.setBlockToAir(x,y+dy,z);for(int i=0;i<10;i++)main.updateEntity();}
    public boolean obstacleNow(){return main.isNoObstacle();}
    public void fan(boolean installed){main.setInventorySlotContents(0,installed?new ItemStack(ModuleEnergy.windgenFan):null);}
    public int scanBase(int[] cells)throws Exception{world=new World();base=baseAt(0,0,0,0);for(int i=0;i<cells.length;i++){int y=i+2;switch(cells[i]){case 1->world.setBlock(0,y,0,ModuleEnergy.windgenPillar);case 2->mainAt(0,y,0,0);case 3->mainAt(0,y,0,1);case 4->world.setBlock(0,y,0,ModuleEnergy.windgenBase);default->{}}}var method=TileWindGenBase.class.getDeclaredMethod("updateMainTile");method.setAccessible(true);method.invoke(base);var field=TileWindGenBase.class.getDeclaredField("completeness");field.setAccessible(true);return ((Enum<?>)field.get(base)).ordinal();}
    public boolean scanMain(int[] cells){world=new World();main=mainAt(0,100,0,0);for(int i=0;i<cells.length;i++){switch(cells[i]){case 1->world.setBlock(0,99-i,0,ModuleEnergy.windgenPillar);case 4->world.setBlock(0,99-i,0,ModuleEnergy.windgenBase);default->{}}}return main.isCompleteStructure();}
    public static int[] rotate(int x,int y,int z,int facing){var pos=BlockMulti.rotate(new BlockMulti.SubBlockPos(x,y,z),ForgeDirection.valueOf(new String[]{"NORTH","EAST","SOUTH","WEST"}[facing]));return new int[]{pos.dx,pos.dy,pos.dz};}
    public static String yaw(float yaw){int index=(int)Math.floor(yaw*4.0F/360.0F+.5D)&3;return ModuleEnergy.windgenBase.getRotation(index).name();}
    public void clearNetwork(){NetworkMessage.sent.clear();}public double lastEnergySync(){for(int i=NetworkMessage.sent.size()-1;i>=0;i--){var sent=NetworkMessage.sent.get(i);if(sent.channel().equals("sync_energy"))return ((Number)sent.arguments()[0]).doubleValue();}return -1;}
    public String[] render(long time){cn.lambdalib.util.helper.GameTimer.time=time;org.lwjgl.opengl.GL11.operations.clear();new cn.academy.energy.client.render.block.RenderWindGenMain().renderTileEntityAt(main,0,0,0,0);return org.lwjgl.opengl.GL11.operations.toArray(String[]::new);}
    public double rotation(){return main.lastRotation;}
    public void reload(){var tag=new NBTTagCompound();base.writeToNBT(tag);base=new TileWindGenBase();base.worldObj=world;base.readFromNBT(tag);}
    public double hiddenAuxiliaryGeneration(int pillars){var hidden=new OriginalWindHarness(120,pillars,true,0);var upper=(TileWindGenBase)hidden.world.getTileEntity(0,120-pillars-1,0);for(int i=0;i<10;i++)upper.updateEntity();return upper.getSimulatedGeneration();}
    private static final class Battery extends Item implements ImagEnergyItem{private final double capacity,bandwidth;Battery(double capacity,double bandwidth){this.capacity=capacity;this.bandwidth=bandwidth;stackLimit=1;}public double getMaxEnergy(){return capacity;}public double getBandwidth(){return bandwidth;}}
}
