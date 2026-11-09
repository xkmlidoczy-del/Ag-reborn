package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.HighPowerResources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/** Opt-in checkpoints only: reads real client pools and integrated-server player ledgers.
 * Does not spawn, cast, reset clocks/resources, clean pools, collect GC, or change gameplay. */
final class CanonMultiplayerSoakQa {
    private static CompletableFuture<JsonObject> pending;
    private static String pendingStage;
    private static long startedNs;
    private static Guard guard;
    private static final class Guard {
        final java.util.UUID owner;final String skill,type;final double beforeCp,beforeNative;final long beforeClock;final int beganTick;
        final net.minecraft.world.phys.Vec3 position;final String paymentBefore;
        final com.google.gson.JsonArray trace=new com.google.gson.JsonArray();
        double minCp,minNative,maxHorizontalDistance,maxVerticalDistance,maxRawHorizontal,maxKnownHorizontal;
        double minKnownVertical=Double.POSITIVE_INFINITY,maxKnownVertical=Double.NEGATIVE_INFINITY;
        long maxClock;int maxCooldown,maxContexts,maxQuotaEntries,samples;boolean persistentGrantStateChanged;
        Guard(net.minecraft.server.level.ServerPlayer p,String skill){
            if(!skill.equals("wind_pressure_lift")&&!skill.equals("beam_step"))throw new IllegalArgumentException("unknown guarded motion skill");
            this.skill=skill;type=skill.equals("beam_step")?"BeamStep":"WindPressureLift";
            owner=p.getUUID();beforeCp=minCp=HighPowerResources.cp(p);beforeNative=minNative=AbilityStorage.get(p).cp;
            beforeClock=maxClock=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+skill);beganTick=p.server.getTickCount();
            position=p.position();paymentBefore=persistentGrantState(p);
        }
        String persistentGrantState(net.minecraft.server.level.ServerPlayer p){
            var data=p.getPersistentData();var image=new net.minecraft.nbt.CompoundTag();
            for(String key:skill.equals("beam_step")?new String[]{"academy:beam_step_grants"}:new String[]{"academy:wind_pressure_lift_payment","academy:wind_pressure_lift_budget","academy:wind_pressure_lift_budget_quarantined"})
                if(data.contains(key))image.put(key,data.get(key).copy());
            return image.toString();
        }
        void sample(net.minecraft.server.level.ServerPlayer p){
            double cp=HighPowerResources.cp(p),nativeCp=AbilityStorage.get(p).cp;
            long clock=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+skill);
            int cooldown=AbilityStorage.get(p).cooldowns.getOrDefault(skill,0),contexts;
            try{contexts=((java.util.Map<?,?>)field("cn.academy.port.highpower."+type,"CONTEXTS").get(null)).containsKey(owner)?1:0;}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("guard context observation failed",failure);}
            var delta=p.position().subtract(position);var raw=p.getDeltaMovement();var known=p.getKnownMovement();
            minCp=Math.min(minCp,cp);minNative=Math.min(minNative,nativeCp);maxClock=Math.max(maxClock,clock);
            maxCooldown=Math.max(maxCooldown,cooldown);maxContexts=Math.max(maxContexts,contexts);
            maxHorizontalDistance=Math.max(maxHorizontalDistance,Math.hypot(delta.x,delta.z));maxVerticalDistance=Math.max(maxVerticalDistance,Math.abs(delta.y));
            maxRawHorizontal=Math.max(maxRawHorizontal,Math.hypot(raw.x,raw.z));maxKnownHorizontal=Math.max(maxKnownHorizontal,Math.hypot(known.x,known.z));
            minKnownVertical=Math.min(minKnownVertical,known.y);maxKnownVertical=Math.max(maxKnownVertical,known.y);
            maxQuotaEntries=Math.max(maxQuotaEntries,skill.equals("beam_step")?p.getPersistentData().getLongArray("academy:beam_step_grants").length:0);
            persistentGrantStateChanged|=!paymentBefore.equals(persistentGrantState(p));samples++;
            var row=new JsonObject();row.addProperty("serverTick",p.server.getTickCount());row.addProperty("worldTick",p.serverLevel().getGameTime());
            row.addProperty("cp",cp);row.addProperty("nativeCp",nativeCp);row.addProperty("skillClock",clock);row.addProperty("cooldown",cooldown);row.addProperty("ownedContexts",contexts);
            row.addProperty("position",p.position().toString());row.addProperty("rawDeltaMovement",raw.toString());row.addProperty("knownMovement",known.toString());row.addProperty("onGround",p.onGround());
            trace.add(row);
        }
        JsonObject json(){
            var j=new JsonObject();j.addProperty("skill",skill);j.addProperty("owner",owner.toString());j.addProperty("beforeCp",beforeCp);j.addProperty("minimumCp",minCp);j.addProperty("beforeNativeCp",beforeNative);j.addProperty("minimumNativeCp",minNative);
            j.addProperty("beforeClock",beforeClock);j.addProperty("maximumClock",maxClock);j.addProperty("maximumCooldown",maxCooldown);j.addProperty("maximumOwnedContexts",maxContexts);j.addProperty("maximumQuotaEntries",maxQuotaEntries);
            j.addProperty("persistentGrantStateChanged",persistentGrantStateChanged);j.addProperty("initialPosition",position.toString());j.addProperty("maximumHorizontalDisplacement",maxHorizontalDistance);j.addProperty("maximumVerticalDisplacement",maxVerticalDistance);
            j.addProperty("maximumRawHorizontalVelocity",maxRawHorizontal);j.addProperty("maximumKnownHorizontalVelocity",maxKnownHorizontal);j.addProperty("minimumKnownVerticalVelocity",minKnownVertical);j.addProperty("maximumKnownVerticalVelocity",maxKnownVertical);
            j.addProperty("samples",samples);j.addProperty("beganServerTick",beganTick);j.add("serverTickTrace",trace.deepCopy());
            j.addProperty("scope","read-only server-tick resource/clock/context/grant persistence and actual position/raw/known-motion witness; no skill method called or movement set");return j;
        }
    }
    static void serverTick(net.minecraft.server.MinecraftServer server){if(guard!=null){var p=server.getPlayerList().getPlayer(guard.owner);if(p!=null)guard.sample(p);}}
    private CanonMultiplayerSoakQa() {}
    static boolean step(Minecraft mc,String stage,Consumer<JsonObject> receipt){
        if(startedNs==0)startedNs=System.nanoTime();
        if(mc.level==null||mc.player==null)throw new IllegalStateException("soak checkpoint requires a live client world");
        var server=mc.getSingleplayerServer();
        if(server==null){receipt.accept(client(mc,stage));return true;}
        if(pending==null){
            pendingStage=stage;var owner=mc.player.getUUID();
            pending=server.submit(()->{
                var out=new JsonObject();out.addProperty("serverTick",server.getTickCount());out.addProperty("published",server.isPublished());out.addProperty("singleplayer",server.isSingleplayer());
                String beginSkill=stage.equals("guard-begin")?"wind_pressure_lift":stage.startsWith("guard-begin-")?stage.substring("guard-begin-".length()):null;
                if(beginSkill!=null){if(guard!=null)throw new IllegalStateException("another guard interval is active");guard=new Guard(server.getPlayerList().getPlayer(owner),beginSkill);guard.sample(server.getPlayerList().getPlayer(owner));}
                if(guard!=null){out.add("multiplayerGuard",guard.json());if(guard.skill.equals("wind_pressure_lift"))out.add("windMultiplayerGuard",guard.json());}
                if(stage.equals("guard-end")||stage.startsWith("guard-end-")){
                    String endSkill=stage.equals("guard-end")?"wind_pressure_lift":stage.substring("guard-end-".length());
                    if(guard==null||!endSkill.equals(guard.skill))throw new IllegalStateException("guard interval end mismatch");guard=null;
                }
                var players=new JsonArray();
                for(var p:server.getPlayerList().getPlayers()){
                    var state=AbilityStorage.get(p);var row=new JsonObject();
                    row.addProperty("name",p.getGameProfile().getName());row.addProperty("uuid",p.getUUID().toString());
                    row.addProperty("dimension",p.serverLevel().dimension().location().toString());row.addProperty("position",p.position().toString());
                    row.addProperty("velocity",p.getDeltaMovement().toString());row.addProperty("knownMovement",p.getKnownMovement().toString());row.addProperty("onGround",p.onGround());
                    row.addProperty("inWater",p.isInWaterOrBubble());row.addProperty("onFire",p.isOnFire());row.addProperty("airSupply",p.getAirSupply());
                    row.addProperty("alive",p.isAlive());row.addProperty("cp",HighPowerResources.cp(p));row.addProperty("nativeCp",state.cp);
                    row.addProperty("health",p.getHealth());row.addProperty("category",state.category);row.addProperty("level",state.level);
                    row.addProperty("active",state.activated);row.addProperty("slot2",state.presets.currentSkill(2));
                    row.addProperty("sneaking",p.isShiftKeyDown());row.addProperty("team",p.getTeam()==null?"":p.getTeam().getName());
                    row.addProperty("cooldowns",state.cooldowns.size());players.add(row);
                }
                out.add("players",players);out.addProperty("serverPlayerCount",players.size());return out;
            });return false;
        }
        if(!stage.equals(pendingStage))throw new IllegalStateException("another soak checkpoint is pending");
        if(!pending.isDone())return false;
        var result=client(mc,stage);result.add("server",pending.join());pending=null;pendingStage=null;receipt.accept(result);return true;
    }
    private static JsonObject client(Minecraft mc,String stage){
        var out=new JsonObject();out.addProperty("stage",stage);out.addProperty("elapsedMs",(System.nanoTime()-startedNs)/1e6);
        out.addProperty("clientWorldTick",mc.level.getGameTime());out.addProperty("dimension",mc.level.dimension().location().toString());
        out.addProperty("cameraIsOwnPlayer",mc.getCameraEntity()==mc.player);out.addProperty("self",mc.player.getUUID().toString());
        out.addProperty("effects",size("cn.academy.port.client.canon.CanonVfxClient","EFFECTS"));
        out.addProperty("pendingPreviews",size("cn.academy.port.client.canon.CanonVfxClient","PENDING"));
        out.addProperty("pendingBindings",size("cn.academy.port.client.canon.CanonVfxClient","PENDING_FLOAT_BINDINGS"));
        out.addProperty("particles",size("cn.academy.port.client.canon.CanonParticles","LIVE"));
        out.addProperty("decals",size("cn.academy.port.client.canon.CanonDecals","LIVE"));
        out.addProperty("warps",size("cn.academy.port.client.canon.CanonWarps","LIVE"));
        var camera=new JsonObject();
        for(String name:new String[]{"shakeLeft","fovLeft","freezeLeftMs","tintLeft","darkLeft","edgeLeft"}){
            try{Object value=field("cn.academy.port.client.canon.CanonCamera",name).get(null);camera.addProperty(name,((Number)value).doubleValue());}
            catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        }
        out.add("cameraTimers",camera);
        var runtime=Runtime.getRuntime();out.addProperty("heapUsedBytes",runtime.totalMemory()-runtime.freeMemory());
        out.addProperty("heapCommittedBytes",runtime.totalMemory());
        out.addProperty("measurementScope","read-only native pools/player ledgers at explicit checkpoints; heap is observational, not a GC-controlled leak proof");
        return out;
    }
    private static Field field(String owner,String name) throws ReflectiveOperationException {
        Field field=Class.forName(owner).getDeclaredField(name);field.setAccessible(true);return field;
    }
    private static int size(String owner,String name){
        try{Object value=field(owner,name).get(null);
            if(value instanceof Collection<?> collection)return collection.size();
            if(value instanceof java.util.Map<?,?> map)return map.size();
            throw new IllegalStateException("not a native pool: "+owner+"."+name);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
}
