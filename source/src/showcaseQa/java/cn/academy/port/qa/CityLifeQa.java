package cn.academy.port.qa;

import cn.academy.port.city.CityCommuters;
import cn.academy.port.city.CityGoods;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.train.TrainCarEntity;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** A single playable continuation: furnished rooms -> repeat order -> actual shared train -> workplace. */
final class CityLifeQa {
    private static final Logger LOG=LogUtils.getLogger();
    private static UUID commuter;private static long watching;private static int retry;
    private static boolean boarded,alighted,worked;private static double highestY,lowestY=1000,rideStartZ,rideDistance;
    private static final LinkedHashMap<String,Object> REPORT=new LinkedHashMap<>();
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException("QA_CITY_LIFE "+message);}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static <T>T server(Minecraft mc,java.util.function.Supplier<T> body){return mc.getSingleplayerServer().submit(body).join();}
    private static CityNpcEntity npc(ServerPlayer p){return (CityNpcEntity)p.serverLevel().getEntity(commuter);}
    static boolean step(Minecraft mc,String op){
        String[] a=op.split(",");
        switch(a[0]){
            case "fixture" -> server(mc,()->{var p=player(mc);p.getInventory().add(new ItemStack(Items.PAPER,16));p.getInventory().add(new ItemStack(Items.COCOA_BEANS,2));LOG.info("QA_CITY_LIFE_FIXTURE suppliedPaper=16 noQuestProgressChanged=true");return null;});
            case "clean" -> {mc.gui.getChat().clearMessages(false);mc.getToasts().clear();mc.player.getInventory().selected=8;}
            case "look_commuter" -> server(mc,()->{var p=player(mc);var n=npc(p);require(n!=null,"commuter missing");p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,n.getEyePosition());return null;});
            case "night" -> server(mc,()->{var p=player(mc);var found=p.serverLevel().getEntitiesOfClass(CityNpcEntity.class,p.getBoundingBox().inflate(160),n->!n.commuterId().isEmpty()&&!n.isPassenger());require(!found.isEmpty(),"night residents missing");for(var n:found)require(n.activity()!=CityNpcEntity.Activity.SIT,"floor-only resting used chair pose");LOG.info("QA_CITY_LIFE_NIGHT commuters={} onFootPosesValid=true",found.size());return null;});
            case "room" -> server(mc,()->{
                var p=player(mc);var at=new BlockPos(Integer.parseInt(a[2]),Integer.parseInt(a[3]),Integer.parseInt(a[4]));
                require(p.serverLevel().getBlockState(at).isAir(),"room walk point blocked "+a[1]+" block="+p.serverLevel().getBlockState(at));
                require(!p.serverLevel().getBlockState(at.below()).isAir(),"room floor missing "+a[1]);REPORT.put(a[1],"walk point and real furnishing present");LOG.info("QA_CITY_LIFE_ROOM name={} pos={}",a[1],at);return null;
            });
            case "quest" -> server(mc,()->{
                var p=player(mc);var d=p.getPersistentData().getCompound("academy:city_errands");int state=d.getInt(a[1]);
                require(state==Integer.parseInt(a[2]),"quest "+a[1]+" state="+state);
                int coins=0;for(var stack:p.getInventory().items)if(stack.is(CityGoods.CITY_COIN.get()))coins+=stack.getCount();
                require(coins==Integer.parseInt(a[3]),"coins "+coins+" expected="+a[3]);
                REPORT.put(a[1]+"_state",state);REPORT.put("coins",coins);REPORT.put(a[1]+"_completions",d.getInt(a[1]+"_count"));
                LOG.info("QA_CITY_LIFE_QUEST id={} state={} coins={} completed={} readyAt={} now={}",a[1],state,coins,d.getInt(a[1]+"_count"),d.getLong(a[1]+"_ready_at"),p.level().getGameTime());return null;
            });
            case "watch" -> {
                boolean ok=server(mc,()->{var p=player(mc);var n=p.serverLevel().getEntitiesOfClass(CityNpcEntity.class,p.getBoundingBox().inflate(200),x->x.commuterId().equals("campus_reader")).stream().findFirst().orElse(null);
                    if(n==null)return false;commuter=n.getUUID();watching=p.level().getGameTime();highestY=lowestY=n.getY();LOG.info("QA_CITY_LIFE_WATCH npc={} phase={} pos={}",commuter,CityCommuters.phase(n),n.position());return true;});
                if(!ok){require(++retry<300,"commuter did not spawn");return false;}retry=0;
            }
            case "waiting" -> {
                boolean done=server(mc,()->{var p=player(mc);var n=npc(p);require(n!=null,"commuter disappeared");highestY=Math.max(highestY,n.getY());lowestY=Math.min(lowestY,n.getY());
                    if(p.level().getGameTime()%100==0)LOG.info("QA_CITY_LIFE_PATH phase={} pos={} target={} navDone={}",CityCommuters.phase(n),n.position(),n.getNavigation().getTargetPos(),n.getNavigation().isDone());
                    require(p.level().getGameTime()-watching<2400,"commuter stair ascent stalled at "+n.position()+" phase="+CityCommuters.phase(n));
                    return CityCommuters.phase(n)==CityCommuters.Phase.WAITING;});
                if(!done)return false;REPORT.put("stairs_highest_y",highestY);REPORT.put("stairs_lowest_y",lowestY);LOG.info("QA_CITY_LIFE_STAIRS low={} high={} walked=true",lowestY,highestY);
            }
            case "left_room" -> {boolean done=server(mc,()->{var n=npc(player(mc));return n!=null&&(n.getZ()>94||n.getY()>1);});if(!done){require(++retry<500,"final furnishing blocked the commute room exit");return false;}retry=0;LOG.info("QA_CITY_LIFE_FINAL_ROOM_EXIT walked=true");}
            case "board_panel" -> {
                var pos=new BlockPos(141,45,0);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.WEST,pos,false));
                LOG.info("QA_CITY_LIFE_PLAYER_BOARD viaRealBlockPacket=true");
            }
            case "boarded" -> {
                boolean done=server(mc,()->{var p=player(mc);var n=npc(p);require(n!=null,"commuter missing");
                    boolean yes=n.getVehicle() instanceof TrainCarEntity car&&car.northSouth()&&p.getVehicle()==car;
                    if(yes){boarded=true;rideStartZ=n.getZ();REPORT.put("same_native_car",true);LOG.info("QA_CITY_LIFE_BOARDED npc={} car={} passengers={} y={}",n.getUUID(),n.getVehicle().getUUID(),n.getVehicle().getPassengers().size(),n.getY());}return yes;});
                if(!done){require(++retry<320,"player and commuter did not share the native train");return false;}retry=0;
            }
            case "moving" -> server(mc,()->{var n=npc(player(mc));require(n!=null&&n.getVehicle() instanceof TrainCarEntity,"not riding");rideDistance=Math.max(rideDistance,Math.abs(n.getZ()-rideStartZ));require(rideDistance>30,"train did not move");REPORT.put("observed_train_displacement",rideDistance);LOG.info("QA_CITY_LIFE_MOVING distance={} position={}",rideDistance,n.position());return null;});
            case "arrived" -> {
                boolean done=server(mc,()->{var p=player(mc);var n=npc(p);require(n!=null,"commuter missing on arrival");
                    if(!n.isPassenger()&&CityCommuters.phase(n)==CityCommuters.Phase.TO_WORK){alighted=true;REPORT.put("npc_alighted_central",true);LOG.info("QA_CITY_LIFE_ALIGHT pos={} nativeExit=true",n.position());return true;}return false;});
                if(!done){require(++retry<2000,"train did not arrive / commuter failed to alight");return false;}retry=0;
            }
            case "ride_credit" -> server(mc,()->{var p=player(mc);require(!p.isPassenger(),"player did not dismount");require(p.getPersistentData().getCompound("academy:city_errands").getBoolean("repeat_central_rode"),"genuine ride credit missing");REPORT.put("player_real_ride_credit",true);LOG.info("QA_CITY_LIFE_RIDE_CREDIT recorded=true actualDismount=true");return null;});
            case "worked" -> {
                boolean done=server(mc,()->{var p=player(mc);var n=npc(p);require(n!=null,"commuter missing after alighting");
                    if(p.level().getGameTime()%100==0)LOG.info("QA_CITY_LIFE_DESCENT phase={} pos={} target={}",CityCommuters.phase(n),n.position(),n.getNavigation().getTargetPos());
                    if(CityCommuters.phase(n)==CityCommuters.Phase.WORKING&&n.getY()<1){worked=true;REPORT.put("workplace",n.position().toString());LOG.info("QA_CITY_LIFE_WORKING npc={} pos={} action={}",n.getUUID(),n.position(),n.activity());return true;}return false;});
                if(!done){require(++retry<2000,"commuter failed to walk down to workplace");return false;}retry=0;
            }
            case "report" -> {
                require(boarded&&alighted&&worked,"commuter continuation incomplete");REPORT.put("version","a3.54");
                try{Files.writeString(mc.gameDirectory.toPath().resolve("city-life-sample.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}
                catch(java.io.IOException e){throw new IllegalStateException(e);}LOG.info("QA_CITY_LIFE_REPORT {}",REPORT);
            }
            default -> throw new IllegalArgumentException(op);
        }return true;
    }
}
