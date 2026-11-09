package cn.academy.port.qa;
import cn.academy.port.*;
import cn.academy.port.highpower.*;
import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
/** Actual two-press client input, sealed source basins across a loaded chunk boundary, normal save/reopen. */
final class CanonWaterTransferClientQa {
    private static final BlockPos SOURCE=new BlockPos(2079,161,2051),TARGET=new BlockPos(2081,161,2051);
    private static CompletableFuture<?> pending;
    private static double before,paidCp=Double.NaN;private static int releaseTick;
    private CanonWaterTransferClientQa(){}
    static boolean step(Minecraft mc,String action){
        if(!Boolean.getBoolean("academy.qa.showcase")||mc.getSingleplayerServer()==null||!mc.gameDirectory.toString().contains("water-formal"))throw new IllegalStateException("isolated water-formal client required");
        if(releaseTick>0){if(--releaseTick==0){System.clearProperty("academy.qa.key.82");mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),82,0,0,0);
            var owner=mc.player.getUUID();mc.getSingleplayerServer().execute(()->record(mc,"press-released",mc.getSingleplayerServer().getPlayerList().getPlayer(owner)));return true;}return false;}
        if(action.equals("source")||action.equals("target")){
            mc.setScreen(null);
            Vec3 point=(action.equals("source")?SOURCE.getCenter():TARGET.below().getCenter().add(0,.5,0));Vec3 delta=point.subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));mc.player.setYHeadRot(mc.player.getYRot());
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot(),mc.player.onGround()));
            var local=cn.academy.port.client.AcademyClient.state;
            com.mojang.logging.LogUtils.getLogger().info("QA_WATER_CLIENT_PREFLIGHT action={} player={} position={} localCategory={} localLevel={} activated={} canUse={} learned={} slot={} actualMappedKey={} ownCamera={} screen={} nativeCp={}",
                action,mc.player.getUUID(),mc.player.position(),local.category,local.level,local.activated,local.canUse("hydro_transfer"),local.learned("hydro_transfer"),local.presets.currentSkill(2),
                cn.academy.port.client.AcademyClient.SLOTS[2].getKey().getValue(),mc.getCameraEntity()==mc.player,mc.screen,local.cp);
            long window=mc.getWindow().getWindow();System.setProperty("academy.qa.key.82","true");mc.keyboardHandler.keyPress(window,82,0,1,0);releaseTick=3;return false;
        }
        if(pending==null){var id=mc.player.getUUID();pending=mc.getSingleplayerServer().submit(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var world=p.serverLevel();
            if(action.equals("setup")){
                p.setGameMode(GameType.SURVIVAL);var state=AbilityStorage.get(p);state.changeCategoryClassic("hydro_hand");state.setLevel(5);state.learn("hydro_whip");state.learn("hydro_transfer");state.setActivateState(true);state.presets.edit(0,2,"hydro_transfer",state::learned);
                p.getPersistentData().remove(HighPowerCombat.DATA_KEY);if(!HighPowerCombat.enroll(p,5))throw new IllegalStateException("QA LV5 enrollment refused");HighPowerResources.initializeBudget(p);before=HighPowerResources.cp(p);
                for(int x=2076;x<=2084;x++)for(int z=2046;z<=2054;z++){
                    world.setBlock(new BlockPos(x,160,z),Blocks.STONE.defaultBlockState(),3);
                    for(int y=161;y<=166;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                    if(z<=2049)world.setBlock(new BlockPos(x,161,z),Blocks.STONE.defaultBlockState(),3);
                }
                for(BlockPos cell:new BlockPos[]{SOURCE,TARGET})for(BlockPos wall:new BlockPos[]{cell.north(),cell.south(),cell.east(),cell.west()})world.setBlock(wall,Blocks.STONE.defaultBlockState(),3);
                world.setBlock(SOURCE,Blocks.WATER.defaultBlockState(),3);world.setBlock(TARGET,Blocks.AIR.defaultBlockState(),3);
                // The shared corner gives both rays a clear path over the one-block rims;
                // a distant raised platform intercepts the downward destination ray itself.
                world.setBlock(new BlockPos(2080,161,2050),Blocks.STONE.defaultBlockState(),3);
                // Observe the real post-debit placement callback, before normal CP recovery.
                // Reading CP several ticks later cannot establish spending when regeneration
                // has already returned the ledger to its maximum.
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.bus.api.EventPriority.LOWEST,false,
                    net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent.class,event->{
                        if(event.getEntity()==p&&event.getPos().equals(TARGET)){
                            paidCp=HighPowerResources.cp(p);record(mc,"paid-placement",p);
                        }
                    });
                p.teleportTo(2080.5,162,2050.5);p.setDeltaMovement(Vec3.ZERO);AbilityStorage.save(p);AcademyNetwork.sync(p);record(mc,"setup",p);return true;
            }
            if(action.equals("check")||action.equals("restart-check")){
                world.getChunk(SOURCE);world.getChunk(TARGET);
                if(!world.getBlockState(SOURCE).isAir()||!world.getFluidState(TARGET).isSource()||!world.getBlockState(TARGET).is(Blocks.WATER)
                        ||SOURCE.getX()>>4==TARGET.getX()>>4)throw new IllegalStateException("water transfer state mismatch source="+world.getBlockState(SOURCE)+" target="+world.getBlockState(TARGET));
                if(action.equals("check")&&(!Double.isFinite(paidCp)||paidCp>=before
                        ||Math.abs(before-paidCp-HighPowerGrowth.values(p).arcCost())>1e-6))throw new IllegalStateException("no exact real resource debit");
                record(mc,action.equals("check")?"complete":"restart-verified",p);return true;
            }
            throw new IllegalArgumentException(action);
        });return false;}
        if(!pending.isDone())return false;pending.join();pending=null;return true;
    }
    private static synchronized void record(Minecraft mc,String event,ServerPlayer p){var r=new JsonObject();r.addProperty("event",event);r.addProperty("source",SOURCE.toShortString());r.addProperty("target",TARGET.toShortString());r.addProperty("cp",HighPowerResources.cp(p));r.addProperty("time",System.currentTimeMillis());
        r.addProperty("resourceReady",HighPowerResources.ready(p));r.addProperty("unsupportedReason",HighPowerCombat.unsupportedReason(p).orElse("none"));r.addProperty("position",p.position().toString());r.addProperty("gameMode",p.gameMode.getGameModeForPlayer().getName());
        try{var field=Class.forName("cn.academy.port.canon.CanonWaterTransfer").getDeclaredField("SELECTED");field.setAccessible(true);r.addProperty("selectedOwnerPresent",((java.util.Map<?,?>)field.get(null)).containsKey(p.getUUID()));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        var state=AbilityStorage.get(p);r.addProperty("activated",state.activated);r.addProperty("canUse",state.canUse("hydro_transfer"));r.addProperty("slot",state.presets.currentSkill(2));r.addProperty("headYaw",p.getYHeadRot());r.addProperty("pitch",p.getXRot());
        var eye=p.getEyePosition();var end=eye.add(cn.academy.port.skill.ClassicRaytrace.direction(p).scale(6));
        for(var fluid:new net.minecraft.world.level.ClipContext.Fluid[]{net.minecraft.world.level.ClipContext.Fluid.NONE,net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY}){
            var hit=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.OUTLINE,fluid,p));r.addProperty("ray_"+fluid,hit.getBlockPos().toShortString()+":"+hit.getDirection()+":"+hit.getType());}
        try{Files.writeString(mc.gameDirectory.toPath().resolve("water-formal-receipts.jsonl"),r+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("QA_WATER_FORMAL {}",r);}
}
