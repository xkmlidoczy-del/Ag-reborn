package cn.academy.port.qa;

import cn.academy.port.city.*;
import cn.academy.port.train.TrainManager;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Independent native TCP clients only. Setup relocates players; mounting and floor choices use real UI packets. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CityInteriorMultiplayerQa {
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.city.dual");
    private static final String ROLE=System.getProperty("academy.qa.canon.role",""),RUN=System.getProperty("academy.qa.canon.run","");
    private static final Path ROOT=Path.of(System.getProperty("academy.qa.canon.root","."));
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<Map<String,Object>> TRACE=new ArrayList<>();
    private static BlockPos controller;private static int[] floors;private static boolean tracing;private static CityElevatorController owner;
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException("CITY_DUAL "+message);}
    private static <T>T server(Minecraft mc,java.util.function.Supplier<T> f){require(ROLE.equals("host")&&mc.getSingleplayerServer()!=null,"real integrated host required");return mc.getSingleplayerServer().submit(f).join();}
    private static void write(Path p,Object data){try{Files.createDirectories(p.getParent());Files.writeString(p,GSON.toJson(data)+"\n");}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean readSetup(){
        try{var p=ROOT.resolve("city-setup.json");if(!Files.isRegularFile(p))return false;var j=JsonParser.parseString(Files.readString(p)).getAsJsonObject();require(RUN.equals(j.get("run").getAsString()),"stale setup");var pos=j.getAsJsonArray("controller");controller=new BlockPos(pos.get(0).getAsInt(),pos.get(1).getAsInt(),pos.get(2).getAsInt());var fs=j.getAsJsonArray("floors");floors=new int[fs.size()];for(int i=0;i<floors.length;i++)floors[i]=fs.get(i).getAsInt();return true;}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static CityElevatorEntity car(Minecraft mc){if(mc.level==null||controller==null)return null;for(var e:mc.level.entitiesForRendering())if(e instanceof CityElevatorEntity cabin&&cabin.controller().equals(controller))return cabin;return null;}
    public static boolean step(Minecraft mc,String command){require(ENABLED,"explicit isolated dual run required");var a=command.split(",");
        switch(a[0]){
            case "setup" -> {server(mc,()->{
                var w=mc.getSingleplayerServer().getLevel(TrainManager.CITY);require(w!=null,"actual city exists");var group=((CityGreyboxGenerator)w.getChunkSource().getGenerator()).interiorAt(-160,-105);require(group!=null,"new school layout");var plan=group.parts().getFirst();
                var r=plan.floors.getFirst().rect();for(int x=r.x0()>>4;x<=r.x1()>>4;x++)for(int z=r.z0()>>4;z<=r.z1()>>4;z++)w.getChunk(x,z);
                owner=(CityElevatorController)w.getBlockEntity(plan.lift.controller());require(owner!=null,"real generated owner");controller=owner.getBlockPos();floors=owner.floors();var players=mc.getSingleplayerServer().getPlayerList().getPlayers();require(players.size()==2,"exactly two independent connected players");
                int i=0;for(var p:players){var at=owner.landing(0).add(i++==0?-.25:.25,0,0);require(CityElevators.validExit(w,at,p),"safe staging at actual landing");p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();p.teleportTo(w,at.x,at.y,at.z,Set.of(),0,0);p.fallDistance=0;}
                write(ROOT.resolve("city-setup.json"),Map.of("run",RUN,"building",plan.id,"controller",List.of(controller.getX(),controller.getY(),controller.getZ()),"shaftX",owner.shaftX(),"shaftZ",owner.shaftZ(),"floors",floors,"players",players.stream().map(p->Map.of("name",p.getGameProfile().getName(),"uuid",p.getUUID().toString())).toList()));return true;
            });tracing=true;}
            case "panel" -> {if(!readSetup()||mc.level==null||!mc.level.dimension().equals(TrainManager.CITY)||mc.player==null)return false;int sx,sz;try{var j=JsonParser.parseString(Files.readString(ROOT.resolve("city-setup.json"))).getAsJsonObject();sx=j.get("shaftX").getAsInt();sz=j.get("shaftZ").getAsInt();}catch(Exception e){throw new IllegalStateException(e);}var at=new BlockPos(sx+3,floors[0]+1,sz);if(!mc.level.hasChunkAt(at))return false;tracing=true;mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false));}
            case "mounted" -> {var c=car(mc);if(c==null||mc.player.getVehicle()!=c)return false;require(!c.shouldRiderSit(),"local player remains standing");if(ROLE.equals("host")&&!server(mc,()->owner.cabin()!=null&&owner.cabin().getPassengers().size()==2))return false;}
            case "floor" -> {int f=Integer.parseInt(a[1]);var c=car(mc);if(c==null||c.door()!=12||c.phaseValue()!=CityElevatorController.Phase.DWELL.ordinal()||Math.abs(c.getY()-floors[f])>.03)return false;require(Math.abs(mc.player.getY()-c.getY())<.06,"client rider feet follow real cabin");}
            case "moving" -> {var c=car(mc);if(c==null||c.phaseValue()!=CityElevatorController.Phase.MOVING.ordinal())return false;}
            case "snapshot" -> server(mc,()->{
                var data=new LinkedHashMap<String,Object>();data.put("run",RUN);data.put("stage",a[1]);data.put("cabin",owner.cabin()==null?"none":owner.cabin().getUUID().toString());data.put("phase",owner.phase().name());data.put("y",owner.cabinY());data.put("riders",owner.cabin()==null?0:owner.cabin().getPassengers().size());data.put("players",mc.getSingleplayerServer().getPlayerList().getPlayers().stream().map(p->Map.of("name",p.getGameProfile().getName(),"uuid",p.getUUID().toString(),"riding",p.getVehicle()instanceof CityElevatorEntity,"y",p.getY(),"health",p.getHealth(),"fallDistance",p.fallDistance)).toList());write(ROOT.resolve("city-server-"+a[1]+".json"),data);return true;
            });
            case "observer-view" -> server(mc,()->{for(var p:mc.getSingleplayerServer().getPlayerList().getPlayers())if(!p.getUUID().equals(mc.player.getUUID())){
                Vec3 at=new Vec3(owner.shaftX()+2,floors[0],owner.shaftZ()+4.5);if(!CityElevators.validExit(p.level(),at,p))at=CityElevators.safeExit(p.level(),BlockPos.containing(at),p).orElseThrow();p.teleportTo(p.serverLevel(),at.x,at.y,at.z,Set.of(),180,0);
            }return true;});
            case "recovered" -> {
                if(!readSetup()||mc.player==null||mc.level==null||!mc.level.dimension().equals(TrainManager.CITY)||mc.player.isPassenger())return false;
                if(!mc.player.onGround())return false;require(mc.player.getHealth()==20,"reconnect did not damage local player");tracing=true;
                if(ROLE.equals("host")){
                    boolean ready=server(mc,()->{var list=mc.getSingleplayerServer().getPlayerList().getPlayers();if(list.size()!=2)return false;for(var p:list)if(!p.getUUID().equals(mc.player.getUUID())&&(p.isPassenger()||!CityElevators.validExit(p.level(),p.position(),p)||p.getHealth()!=20))return false;return true;});if(!ready)return false;
                }
            }
            case "export" -> {require(TRACE.stream().filter(r->(int)r.get("phase")==CityElevatorController.Phase.MOVING.ordinal()).map(r->r.get("y")).distinct().count()>=10,"actual client received intermediate cabin motion");require(TRACE.stream().anyMatch(r->(int)r.get("riders")==2),"both actual passengers reached this client");write(ROOT.resolve(ROLE).resolve("city-network.json"),Map.of("run",RUN,"role",ROLE,"player",mc.player.getUUID().toString(),"clientTrace",TRACE,"sameVersion",true,"syntheticConnections",false));}
            default -> throw new IllegalArgumentException(command);
        }return true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){if(!ENABLED||!tracing||TRACE.size()>=3000)return;var mc=Minecraft.getInstance();var c=car(mc);if(c==null||mc.player==null)return;var row=new LinkedHashMap<String,Object>();row.put("timeNs",System.nanoTime());row.put("tick",mc.level.getGameTime());row.put("cabin",c.getUUID().toString());row.put("y",c.getY());row.put("phase",c.phaseValue());row.put("door",c.door());row.put("riders",c.getPassengers().size());row.put("localRiding",mc.player.getVehicle()==c);row.put("playerY",mc.player.getY());row.put("standing",!c.shouldRiderSit());TRACE.add(row);}
}
