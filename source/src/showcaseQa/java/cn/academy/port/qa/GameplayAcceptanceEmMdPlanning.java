package cn.academy.port.qa;

import cn.academy.port.highpower.*;
import com.mojang.logging.LogUtils;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Husk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.slf4j.Logger;

/** Actual planned planes/paths and free native recall, with real passive targets that must remain unharmed. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceEmMdPlanning {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Set<String> IDS=Set.of("folded_mirror_array","scatter_locator_plate","remote_scatter_relay");
    private static final class Fixture {
        final UUID owner;final String id;final Husk target;final BigDecimal pool;double budget,total,held;String placed;boolean prepared,viewed,passed,clientPlane,clientPath,clientMeasure,hit;
        Fixture(UUID owner,String id,Husk target){this.owner=owner;this.id=id;this.target=target;pool=pool(target);}
    }
    private static Fixture fixture;
    private GameplayAcceptanceEmMdPlanning(){}
    public static void run(Minecraft mc,String action){var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated player");var owner=mc.player.getUUID();
        if(action.startsWith("setup:")){String id=action.substring(6);require(IDS.contains(id),"implemented planning skill");GameplayAcceptanceEmMdConstruction.run(mc,"setup:"+id);server.submit(()->{var p=server.getPlayerList().getPlayer(owner);var list=p.serverLevel().getEntitiesOfClass(Husk.class,p.getBoundingBox().inflate(20),e->e.getTags().contains("gameem_target")&&e.isNoAi());require(list.size()==1,"one real passive target");fixture=new Fixture(owner,id,list.getFirst());fixture.budget=HighPowerResources.availableCp(p);fixture.total=HighPowerResources.cp(p);return true;}).join();}
        else server.submit(()->{var p=server.getPlayerList().getPlayer(owner);var f=fixture;require(f!=null&&f.owner.equals(owner),"same planner");switch(action){
            case "placed"->{require(!f.prepared&&plates(p).size()==1,"actual plane/node placed by normal input");f.prepared=true;f.placed=plates(p).toString();f.held=HighPowerResources.heldAmount(p);require(f.clientPlane,"normal server plane accepted by real client state");if(f.id.equals("scatter_locator_plate"))require(f.held>0&&HighPowerResources.cp(p)==f.total,"locator holds real finite control fee without debit");LOG.info("QA_GAMEPLAY_PLANNER_PLACED skill={} planes={} available={} total={} held={}",f.id,f.placed,HighPowerResources.availableCp(p),HighPowerResources.cp(p),f.held);}
            case "viewed"->{require(f.prepared&&!f.viewed&&plates(p).toString().equals(f.placed),"view preserved actual committed plane geometry");require(f.id.equals("scatter_locator_plate")?f.clientPath:f.clientMeasure,"normal client received real planned route or measurement");require(pool(f.target).compareTo(f.pool)==0&&!f.hit,"preview/measurement never attacked real target");f.viewed=true;LOG.info("QA_GAMEPLAY_PLANNER_VIEW_PASS skill={} clientPath={} measurement={} realTargetUnchanged=true",f.id,f.clientPath,f.clientMeasure);}
            case "assert"->{require(f.prepared&&!f.passed&&plates(p).isEmpty()&&!active(p),"normal long hold truly recalled actual controller and planes");if(!f.id.equals("folded_mirror_array"))require(f.viewed,"explicit preview/measurement verified before recall");require(pool(f.target).compareTo(f.pool)==0&&!f.hit,"planning and recall caused no actual target damage");require(HighPowerResources.heldAmount(p)==0,"recall released the actual owned control hold");if(f.id.equals("scatter_locator_plate"))require(HighPowerResources.cp(p)==f.total,"recall did not write/refund wallet that was never debited");f.passed=true;LOG.info("QA_GAMEPLAY_PLANNER_PASS skill={} actualPlanesEmpty=true actualControllerEnded=true clientPlane={} clientPath={} clientMeasure={} hpUnchanged=true totalBefore={} totalAfter={} heldBefore={} heldAfter={}",f.id,f.clientPlane,f.clientPath,f.clientMeasure,f.total,HighPowerResources.cp(p),f.held,HighPowerResources.heldAmount(p));}
            case "verify"->require(f.passed,"real planning result passed");default->throw new IllegalArgumentException(action);
        }return true;}).join();
    }
    @SubscribeEvent public static void client(ClientTickEvent.Post e){var f=fixture;var mc=Minecraft.getInstance();if(f==null||f.passed||mc.player==null||!mc.player.getUUID().equals(f.owner))return;
        String type=switch(f.id){case "folded_mirror_array"->"FoldedMirrorEffects";case "scatter_locator_plate"->"ScatterLocatorEffects";default->"RemoteScatterRelayEffects";};
        try{var field=Class.forName("cn.academy.port.client."+type).getDeclaredField("MARKERS");field.setAccessible(true);var map=(Map<?,?>)field.get(null);Object marker=map.get(f.owner);if(marker==null)return;var ff=marker.getClass().getDeclaredField("frame");ff.setAccessible(true);Object frame=ff.get(marker);var planes=(List<?>)frame.getClass().getMethod("plates").invoke(frame);var path=(List<?>)frame.getClass().getMethod("path").invoke(frame);int phase=(int)frame.getClass().getMethod("phase").invoke(frame);int status=(int)frame.getClass().getMethod("status").invoke(frame);f.clientPlane|=planes.size()==1;if(f.id.equals("scatter_locator_plate"))f.clientPath|=phase==ScatterLocatorPlate.PREVIEW&&path.size()>=2;if(f.id.equals("remote_scatter_relay"))f.clientMeasure|=status==RemoteScatterRelay.MEASURING&&path.size()>=2;
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    @SubscribeEvent public static void hit(LivingDamageEvent.Post e){var f=fixture;if(f!=null&&e.getEntity()==f.target&&e.getSource().getEntity()!=null&&e.getSource().getEntity().getUUID().equals(f.owner))f.hit=true;}
    private static List<?> plates(ServerPlayer p){return switch(fixture.id){case "folded_mirror_array"->FoldedMirrorArray.plates(p);case "scatter_locator_plate"->ScatterLocatorPlate.plates(p);default->RemoteScatterRelay.plates(p);};}
    private static boolean active(ServerPlayer p){return switch(fixture.id){case "folded_mirror_array"->FoldedMirrorArray.active(p);case "scatter_locator_plate"->ScatterLocatorPlate.active(p);default->RemoteScatterRelay.active(p);};}
    private static BigDecimal pool(Husk e){return new BigDecimal(HighPowerCombat.healthText(e)).add(HighPowerCombat.shield(e));}
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException("QA_GAMEPLAY_PLANNER_FAILED "+why);}
}
