package cn.academy.port.qa;

import cn.academy.port.city.*;
import cn.academy.port.train.TrainManager;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Own-world graphical verification of generated city buildings and actual UI/network elevator use. Never packaged. */
public final class CityInteriorQa {
    private static CityInteriorPlan plan;private static CityElevatorController ctrl;private static String place;private static int activeFloor;private static BlockPos interaction;private static boolean originalLit;private static UUID serviceNpc;
    private static final Map<String,Object> REPORT=new LinkedHashMap<>();private static int retry;
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("CITY_INTERIOR_QA "+why);}
    private static <T>T server(Minecraft m,java.util.function.Supplier<T> f){require(m.getSingleplayerServer()!=null,"integrated server required");return m.getSingleplayerServer().submit(f).join();}
    private static ServerPlayer player(Minecraft m){return m.getSingleplayerServer().getPlayerList().getPlayer(m.player.getUUID());}
    public static boolean step(Minecraft mc,String arg){String[] a=arg.split(",");
        switch(a[0]){
            case "visit" -> server(mc,()->{
                var p=player(mc);p.stopRiding();var w=p.server.getLevel(TrainManager.CITY);require(w!=null,"city dimension exists");var g=(CityGreyboxGenerator)w.getChunkSource().getGenerator();
                place=a[1];int floor=a.length>2?Integer.parseInt(a[2]):0;CityInteriors.Group group=null;String theme="";
                switch(place){
                    case "school"->{group=g.interiorAt(-160,-105);theme="academic";}
                    case "lab"->{group=g.interiorAt(-225,-105);theme="lab";}
                    case "library"->{group=g.interiorAt(-225,-35);theme="library";}
                    case "clinic"->{group=g.interiorAt(-237,-53);theme="clinic";}
                    case "station"->{group=g.interiorAt(-76,433);theme="station";}
                    case "airport"->{group=g.interiorAt(1620,-1835);theme="airport";}
                    case "commercial"->{group=g.interiorAt(0,760);theme="commercial";}
                    case "residential"->{
                        theme="residential";outer:for(int iz=0;iz<12;iz++)for(int ix=0;ix<12;ix++){var candidate=g.interiorAt(-352+ix*64,-352+iz*64);if(candidate!=null&&candidate.parts().stream().anyMatch(q->q.theme.equals("residential"))){group=candidate;break outer;}}
                    }
                    default->throw new IllegalArgumentException(place);
                }
                require(group!=null,"new-layout group found: "+place);String wanted=theme;plan=group.parts().stream().filter(q->q.theme.equals(wanted)).findFirst().orElseThrow();
                floor=Math.max(0,Math.min(floor,plan.floors.size()-1));activeFloor=floor;var f=plan.floors.get(floor);var r=f.rect();
                for(int cx=Math.floorDiv(r.x0(),16);cx<=Math.floorDiv(r.x1(),16);cx++)for(int cz=Math.floorDiv(r.z0(),16);cz<=Math.floorDiv(r.z1(),16);cz++)w.getChunk(cx,cz);
                Vec3 eyeAt=null,target=null;
                for(var room:plan.rooms)if(room.floor()==floor){var rr=room.rect();target=new Vec3((rr.x0()+rr.x1())/2.0,f.y()+1.2,rr.z1()-1);for(int z=rr.z0()+1;z<rr.z1()&&eyeAt==null;z++)for(int x=rr.x0()+Math.min(3,rr.width()-2);x<rr.x1();x++){
                    Vec3 v=new Vec3(x+.5,f.y(),z+.5);if(CityElevators.validExit(w,v,p)){eyeAt=v;break;}}
                    if(eyeAt!=null)break;
                }
                if(eyeAt==null){for(int z=r.z0()+2;z<r.z1()-1&&eyeAt==null;z++)for(int x=r.x0()+2;x<r.x1()-1;x++){Vec3 v=new Vec3(x+.5,f.y(),z+.5);if(CityElevators.validExit(w,v,p)){eyeAt=v;break;}}target=new Vec3((r.x0()+r.x1())/2.0,f.y()+1.3,r.z1()-2);}
                require(eyeAt!=null,"safe interior viewpoint");p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.getInventory().clearContent();p.setNoGravity(false);w.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);w.setDayTime(6000);w.setWeatherParameters(12000,0,false,false);
                Vec3 d=target.subtract(eyeAt.add(0,p.getEyeHeight(),0));float yaw=(float)-Math.toDegrees(Math.atan2(d.x,d.z)),pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));
                p.teleportTo(w,eyeAt.x,eyeAt.y,eyeAt.z,Set.of(),yaw,pitch);p.fallDistance=0;
                ctrl=plan.lift!=null&&w.getBlockEntity(plan.lift.controller())instanceof CityElevatorController c?c:null;
                REPORT.put(place,Map.of("building",plan.id,"floor",floor+1,"rooms",plan.rooms.size(),"floors",plan.floors.size(),"eye",eyeAt.toString(),"lift",ctrl!=null,"roomPlans",plan.rooms.stream().map(q->Map.of("floor",q.floor(),"use",q.use(),"rect",q.rect().toString(),"variant",q.variant())).toList()));
                LogUtils.getLogger().info("CITY_INTERIOR_VISIT place={} building={} floor={} actualGenerated=true position={} lift={}",place,plan.id,floor,eyeAt,ctrl!=null);return true;
            });
            case "landing" -> server(mc,()->{var p=player(mc);require(plan!=null&&plan.lift!=null,"lift exists");ctrl=(CityElevatorController)p.serverLevel().getBlockEntity(plan.lift.controller());require(ctrl!=null,"actual generated controller");int f=a.length>1?Integer.parseInt(a[1]):0;Vec3 v=ctrl.landing(f);require(CityElevators.validExit(p.level(),v,p),"safe elevator landing");p.teleportTo(p.serverLevel(),v.x,v.y,v.z,Set.of(),180,0);return true;});
            case "panel" -> {require(plan!=null&&plan.lift!=null,"panel fixture");BlockPos pos=plan.lift.panel(0);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));LogUtils.getLogger().info("CITY_INTERIOR_PANEL nativeUsePacket=true pos={}",pos);}
            case "button" -> {require(mc.screen!=null,"lift screen open");String text=a[1];var b=mc.screen.children().stream().filter(x->x instanceof Button button&&button.getMessage().getString().equals(text)).map(x->(Button)x).findFirst().orElseThrow(()->new IllegalStateException("lift button "+text));require(b.active,"button enabled "+text);b.onPress();}
            case "close" -> {if(mc.screen!=null)mc.screen.onClose();}
            case "closed" -> require(mc.screen==null,"closed lift panel must stay closed while travelling");
            case "hall" -> server(mc,()->{var p=player(mc);var f=plan.floors.get(activeFloor);var at=new Vec3(plan.corridorX+.5,f.y(),plan.coreZ-.5);require(CityElevators.validExit(p.level(),at,p),"real circulation corridor");p.teleportTo(p.serverLevel(),at.x,at.y,at.z,Set.of(),0,0);return true;});
            case "stairs-start" -> server(mc,()->{var p=player(mc);var at=new Vec3(plan.coreX+.5,plan.floors.getFirst().y(),plan.coreZ-.5);require(CityElevators.validExit(p.level(),at,p),"native stair approach");p.teleportTo(p.serverLevel(),at.x,at.y,at.z,Set.of(),0,0);return true;});
            case "turn" -> {mc.player.setYRot(Float.parseFloat(a[1]));mc.player.setXRot(0);}
            case "stairs-check" -> server(mc,()->{var p=player(mc);require(!p.isPassenger()&&Math.abs(p.getY()-plan.floors.get(1).y())<.05&&p.getHealth()==20,"native movement keys walk both stair flights to the next floor");REPORT.put("native_stair_walk",Map.of("floor",2,"position",p.position().toString(),"health",p.getHealth()));return true;});
            case "mounted" -> {boolean yes=server(mc,()->player(mc).getVehicle()instanceof CityElevatorEntity);if(!yes){require(++retry<200,"did not board through native UI");return false;}retry=0;require(mc.player.getVehicle()instanceof CityElevatorEntity,"client received actual cabin mount");REPORT.put("native_ui_boarding",true);}
            case "arrived" -> {int f=Integer.parseInt(a[1]);boolean yes=server(mc,()->ctrl!=null&&ctrl.boarding()&&ctrl.current()==f);if(!yes){require(++retry<1800,"lift did not reach F"+(f+1));return false;}retry=0;server(mc,()->{var p=player(mc);require(p.getVehicle()==ctrl.cabin(),"rider preserved");require(Math.abs(p.getY()-ctrl.cabinY())<.03,"standing feet synchronized");REPORT.put("native_ui_arrival",Map.of("floor",f+1,"y",p.getY(),"health",p.getHealth(),"phase",ctrl.phase().name()));LogUtils.getLogger().info("CITY_INTERIOR_ARRIVED nativeUi=true floor={} cabinY={} feetY={} health={}",f+1,ctrl.cabinY(),p.getY(),p.getHealth());return true;});}
            case "moving" -> {boolean yes=server(mc,()->ctrl!=null&&ctrl.phase()==CityElevatorController.Phase.MOVING);if(!yes){require(++retry<500,"real cabin did not start moving");return false;}retry=0;server(mc,()->{REPORT.put("saved_midtrip",Map.of("controller",ctrl.getBlockPos().toShortString(),"identity",ctrl.identity(),"y",ctrl.cabinY(),"phase",ctrl.phase().name()));return true;});}
            case "safe-login" -> server(mc,()->{var p=player(mc);require(!p.isPassenger()&&p.getHealth()==20&&CityElevators.validExit(p.level(),p.position(),p),"actual reopened player has a safe standing point without damage");REPORT.put("safe_reopened_login",Map.of("position",p.position().toString(),"health",p.getHealth(),"dimension",p.level().dimension().location().toString()));return true;});
            case "restored-lift" -> server(mc,()->{require(ctrl!=null&&ctrl.configured()&&ctrl.current()==2&&ctrl.boarding(),"saved moving controller completed its persisted destination");var shaft=new AABB(ctrl.shaftX(),ctrl.floors()[0]-1,ctrl.shaftZ(),ctrl.shaftX()+4,ctrl.floors()[ctrl.floors().length-1]+4,ctrl.shaftZ()+4);var cabins=player(mc).serverLevel().getEntitiesOfClass(CityElevatorEntity.class,shaft,c->c.controller().equals(ctrl.getBlockPos()));require(cabins.size()==1,"exactly one runtime cabin after restart");REPORT.put("restored_lift",Map.of("count",cabins.size(),"floor",ctrl.current()+1,"identity",ctrl.identity()));return true;});
            case "exited" -> {boolean yes=server(mc,()->!player(mc).isPassenger());if(!yes||mc.player.isPassenger()){require(++retry<120,"server/client dismount did not agree");return false;}retry=0;REPORT.put("native_ui_exit",true);}
            case "reopen" -> {require(mc.player.getVehicle()instanceof CityElevatorEntity,"actual cabin mounted");mc.options.keyUse.setDown(true);net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());}
            case "release-use" -> mc.options.keyUse.setDown(false);
            case "prepare" -> server(mc,()->{
                var p=player(mc);require(!p.isPassenger(),"dismount before furniture use");var floor=plan.floors.get(activeFloor);var wanted=CityInteriorContent.block(a[1]);interaction=null;
                int maxDy=a[1].equals("interior_lamp")?8:a[1].equals("wall_light")?2:0;
                outer:for(int x=floor.rect().x0();x<=floor.rect().x1();x++)for(int z=floor.rect().z0();z<=floor.rect().z1();z++)for(int dy=0;dy<=maxDy;dy++){
                    var pos=new BlockPos(x,floor.y()+dy,z);if(!p.serverLevel().getBlockState(pos).is(wanted))continue;
                    for(Direction direction:Direction.Plane.HORIZONTAL){Vec3 at=new Vec3(x+.5+direction.getStepX(),floor.y(),z+.5+direction.getStepZ());if(!CityElevators.validExit(p.level(),at,p)||at.add(0,p.getEyeHeight(),0).distanceTo(Vec3.atCenterOf(pos))>4.5)continue;
                        interaction=pos;Vec3 d=Vec3.atCenterOf(pos).subtract(at.add(0,p.getEyeHeight(),0));p.teleportTo(p.serverLevel(),at.x,at.y,at.z,Set.of(),(float)-Math.toDegrees(Math.atan2(d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));break outer;
                    }
                }
                require(interaction!=null,"accessible generated furniture "+a[1]);if(a[1].contains("lamp")||a[1].equals("wall_light"))originalLit=p.serverLevel().getBlockState(interaction).getValue(CityInteriorContent.LIT);REPORT.put("interaction_"+a[1],interaction.toShortString());LogUtils.getLogger().info("CITY_INTERIOR_PREPARE target={} pos={} player={} state={}",a[1],interaction,p.position(),p.serverLevel().getBlockState(interaction));return true;
            });
            case "use" -> {require(interaction!=null,"prepared real furniture");mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(interaction),Direction.UP,interaction,false));}
            case "seated" -> {boolean yes=server(mc,()->player(mc).getVehicle()instanceof CitySeatEntity);if(!yes||!(mc.player.getVehicle()instanceof CitySeatEntity)){require(++retry<120,"native seat mount not synchronized");return false;}retry=0;REPORT.put("native_seat",Map.of("client",true,"server",true,"sitting",mc.player.getVehicle().shouldRiderSit(),"feetY",mc.player.getY()));}
            case "lamp-changed" -> {boolean yes=server(mc,()->player(mc).serverLevel().getBlockState(interaction).getValue(CityInteriorContent.LIT)!=originalLit);if(!yes){require(++retry<120,"lamp use was not authoritative");return false;}retry=0;require(mc.level.getBlockState(interaction).getValue(CityInteriorContent.LIT)!=originalLit,"lamp state reached the client");REPORT.put("native_lamp_switch",true);}
            case "storage-open" -> {boolean yes=server(mc,()->player(mc).containerMenu instanceof net.minecraft.world.inventory.ChestMenu);if(!yes||!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen)){require(++retry<120,"native storage menu missing");return false;}retry=0;REPORT.put("native_storage_open",true);server(mc,()->{var p=player(mc);var tile=(CityInteriorContent.StorageEntity)p.serverLevel().getBlockEntity(interaction);require(tile.isEmpty(),"new generated storage starts empty");p.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,7));p.containerMenu.broadcastChanges();return true;});}
            case "store" -> {mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,54,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,3,0,net.minecraft.world.inventory.ClickType.PICKUP,mc.player);}
            case "stored" -> server(mc,()->{var tile=(CityInteriorContent.StorageEntity)player(mc).serverLevel().getBlockEntity(interaction);require(tile.getItem(3).is(Items.IRON_INGOT)&&tile.getItem(3).getCount()==7,"actual native container clicks stored seven items");REPORT.put("native_storage_contents",Map.of("position",interaction.toShortString(),"slot",3,"item","minecraft:iron_ingot","count",7));return true;});
            case "service-prepare" -> {
                CityNpcEntity npc=null;double distance=256;for(var e:mc.level.entitiesForRendering())if(e instanceof CityNpcEntity n&&n.look().name().equals(a[1])&&n.distanceToSqr(mc.player)<distance){npc=n;distance=n.distanceToSqr(mc.player);}require(npc!=null,"real existing service NPC "+a[1]);serviceNpc=npc.getUUID();
                server(mc,()->{var p=player(mc);var target=p.serverLevel().getEntity(serviceNpc);require(target instanceof CityNpcEntity,"actual server service NPC identity");Vec3 at=null;for(Direction d:Direction.Plane.HORIZONTAL){var v=target.position().add(d.getStepX()*1.7,0,d.getStepZ()*1.7);if(CityElevators.validExit(p.level(),v,p)){at=v;break;}}require(at!=null,"safe service counter approach");Vec3 direction=target.getEyePosition().subtract(at.add(0,p.getEyeHeight(),0));p.teleportTo(p.serverLevel(),at.x,at.y,at.z,Set.of(),(float)-Math.toDegrees(Math.atan2(direction.x,direction.z)),0);p.serverLevel().setDayTime(6000);return true;});
            }
            case "service-use" -> {Entity target=null;for(var e:mc.level.entitiesForRendering())if(e.getUUID().equals(serviceNpc)){target=e;break;}require(target!=null,"client service NPC");mc.gameMode.interact(mc.player,target,InteractionHand.MAIN_HAND);}
            case "service-menu" -> {if(mc.screen==null){require(++retry<120,"existing service dialogue did not open");return false;}retry=0;require(mc.screen.getClass().getSimpleName().contains("Dialogue"),"existing dialogue screen reused");var labels=mc.screen.children().stream().filter(w->w instanceof Button).map(w->((Button)w).getMessage().getString()).toList();REPORT.put(place+"_actual_service",Map.of("screen",mc.screen.getClass().getName(),"buttons",labels));}
            case "book-prepare" -> server(mc,()->{var p=player(mc);interaction=new BlockPos(-216,0,-43);require(p.serverLevel().getBlockEntity(interaction)instanceof net.minecraft.world.level.block.entity.LecternBlockEntity book&&!book.getBook().isEmpty(),"actual relocated reference book initialized once");var at=CityElevators.safeExit(p.level(),interaction,p).orElseThrow();p.teleportTo(p.serverLevel(),at.x,at.y,at.z,Set.of(),0,0);return true;});
            case "book-open" -> {if(!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.LecternScreen)){require(++retry<120,"native reference lectern did not open");return false;}retry=0;REPORT.put("native_reference_reading",true);}
            case "inspect" -> server(mc,()->{var p=player(mc);int bed=0,furniture=0;var fs=plan.floors.getFirst();for(int x=fs.rect().x0();x<=fs.rect().x1();x++)for(int z=fs.rect().z0();z<=fs.rect().z1();z++){var s=p.serverLevel().getBlockState(new BlockPos(x,fs.y(),z));if(s.getBlock()instanceof net.minecraft.world.level.block.BedBlock)bed++;if(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().startsWith("city_"))furniture++;}REPORT.put(place+"_actual_furniture",Map.of("bedParts",bed,"furnitureBlocks",furniture));LogUtils.getLogger().info("CITY_INTERIOR_FURNITURE place={} beds={} furniture={}",place,bed,furniture);return true;});
            case "clean" -> {mc.gui.getChat().clearMessages(false);mc.getToasts().clear();}
            case "night" -> server(mc,()->{player(mc).serverLevel().setDayTime(18000);return true;});
            case "export" -> {try{Path out=Path.of(System.getProperty("academy.qa.macro.root"));Files.writeString(out.resolve("city-interior-runtime.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)+"\n");}catch(Exception e){throw new IllegalStateException(e);}}
            default->throw new IllegalArgumentException(arg);
        }return true;
    }
}
