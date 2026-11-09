/* AcademyCraft1.0.7 RFProviderManager/RFReceiverManager host adaptation. GPLv3; see NOTICE. */
package cn.academy.port.bridge;
import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.wireless.ImagFluxNode;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
/** Native IF wins; source generic managers query UP. Resolve on every operation rather than retaining foreign capabilities. */
public final class ClassicForeignEnergyManager {
    private ClassicForeignEnergyManager(){}
    public static IEnergyStorage endpoint(Object object){
        if(object instanceof ImagFluxNode||object instanceof ImagFluxReceiver||!(object instanceof BlockEntity tile)||tile.isRemoved())return null;
        var level=tile.getLevel();var pos=tile.getBlockPos();if(level==null||level.isClientSide||level.getServer()==null||!level.getServer().isSameThread()||!level.hasChunkAt(pos)||level.getBlockEntity(pos)!=tile)return null;
        return level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,tile.getBlockState(),tile,Direction.UP);
    }
    public static boolean isSupported(Object tile){return endpoint(tile)!=null;}
    public static double getEnergy(Object tile){var endpoint=endpoint(tile);return endpoint==null?0:Math.max(0,endpoint.getEnergyStored())/4d;}
    public static double charge(Object tile,double amount){return charge(endpoint(tile),amount);}
    public static double charge(IEnergyStorage endpoint,double amount){if(endpoint==null||!Double.isFinite(amount)||amount<=0||!endpoint.canReceive())return amount;int requested=ClassicEnergyBridgeBuffer.fe(amount);int accepted=requested<=0?0:Math.max(0,Math.min(requested,endpoint.receiveEnergy(requested,false)));return amount-accepted/4d;}
    public static double pull(Object tile,double amount){return pull(endpoint(tile),amount);}
    public static double pull(IEnergyStorage endpoint,double amount){if(endpoint==null||!Double.isFinite(amount)||amount<=0||!endpoint.canExtract())return 0;int requested=ClassicEnergyBridgeBuffer.fe(amount);return (requested<=0?0:Math.max(0,Math.min(requested,endpoint.extractEnergy(requested,false))))/4d;}
}
