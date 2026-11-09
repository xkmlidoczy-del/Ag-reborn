package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.canon.CanonTargetPolicy;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.skill.ClassicRaytrace;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** QA-only read-only snapshot on the server thread. No controller cast, payment, block write or visual emission. */
final class CanonFormalWaterDiagnostics {
    private CanonFormalWaterDiagnostics() {}
    static JsonObject snapshot(ServerPlayer p,String id){
        if(!p.server.isSameThread()||!id.matches("hydro_[a-z0-9_]{1,48}"))throw new IllegalArgumentException("server water diagnostic only");
        var world=p.serverLevel();var state=AbilityStorage.get(p);var data=new JsonObject();
        data.addProperty("skill",id);data.addProperty("category",state.category);data.addProperty("level",state.level);
        data.addProperty("effectiveLevel",HighPowerResources.effectiveLevel(p));data.addProperty("ready",HighPowerResources.ready(p));
        data.addProperty("learned",state.learned(id));data.addProperty("canUse",state.canUse(id));data.addProperty("presetContains",state.presets.currentContains(id));
        data.addProperty("catalogPresent",SkillCatalog.find(state.category,id).isPresent());data.addProperty("catalogEnabled",SkillCatalog.enabled(state.category,id));
        data.addProperty("highPowerEligible",HighPowerSkills.eligible(state,id));data.addProperty("cp",HighPowerResources.cp(p));
        double cost=HighPowerGrowth.values(p).arcCost();data.addProperty("baseCost",cost);
        data.addProperty("canSpend",(Boolean)invoke(HighPowerResources.class,"canSpend",new Class<?>[]{ServerPlayer.class,double.class},p,cost));
        long now=world.getGameTime();var budget=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        data.addProperty("worldTick",now);data.addProperty("nextSpend",budget.getLong("next_"+id));
        data.addProperty("serverCooldown",state.cooldowns.getOrDefault(id,0));data.addProperty("position",p.position().toString());
        data.addProperty("eye",p.getEyePosition().toString());data.addProperty("direction",ClassicRaytrace.direction(p).toString());
        data.addProperty("bodyYaw",p.getYRot());data.addProperty("headYaw",p.getYHeadRot());data.addProperty("pitch",p.getXRot());
        data.addProperty("velocity",p.getDeltaMovement().toString());data.addProperty("mayBuild",p.getAbilities().mayBuild);
        data.addProperty("unsupportedReason",HighPowerCombat.unsupportedReason(p).map(Object::toString).orElse(""));
        var sourceClass=classFor("cn.academy.port.canon.CanonWaterSkills");
        var selected=selections(sourceClass).get(p.getUUID());data.addProperty("hasExplicitSelection",selected!=null);
        if(selected!=null)try{
            Method at=selected.getClass().getDeclaredMethod("source");at.setAccessible(true);
            BlockPos source=(BlockPos)at.invoke(selected);data.addProperty("selectedPosition",source.toShortString());
            data.addProperty("selectedValidSource",(Boolean)invoke(sourceClass,"validSource",new Class<?>[]{ServerPlayer.class,BlockPos.class},p,source));
        }catch(ReflectiveOperationException e){throw new IllegalStateException("QA read-only selection",e);}
        JsonArray sources=new JsonArray();BlockPos nearest=null;double best=25.000001;int visited=0;
        BlockPos base=p.blockPosition();
        for(BlockPos raw:BlockPos.betweenClosed(base.offset(-5,-5,-5),base.offset(5,5,5))){
            BlockPos pos=raw.immutable();double distance=p.distanceToSqr(pos.getCenter());
            if(distance>25||!world.hasChunkAt(pos)||!world.getFluidState(pos).is(FluidTags.WATER)||!world.getFluidState(pos).isSource())continue;
            boolean valid=(Boolean)invoke(sourceClass,"validSource",new Class<?>[]{ServerPlayer.class,BlockPos.class},p,pos);
            if(visited++<24){var entry=new JsonObject();entry.addProperty("pos",pos.toShortString());entry.addProperty("distanceSquared",distance);
                entry.addProperty("mayInteract",world.mayInteract(p,pos));entry.addProperty("validSource",valid);sources.add(entry);}
            if(valid&&distance<=best){best=distance;nearest=pos;}
        }
        data.add("waterSources",sources);data.addProperty("sourceCount",visited);data.addProperty("nearestValidSource",nearest==null?"":nearest.toShortString());
        var eye=p.getEyePosition();Vec3 end=eye.add(ClassicRaytrace.direction(p).scale(12));
        boolean loaded=true;int x0=Math.floorDiv((int)Math.floor(Math.min(eye.x,end.x)),16),x1=Math.floorDiv((int)Math.floor(Math.max(eye.x,end.x)),16);
        int z0=Math.floorDiv((int)Math.floor(Math.min(eye.z,end.z)),16),z1=Math.floorDiv((int)Math.floor(Math.max(eye.z,end.z)),16);
        if((long)x1-x0>8||(long)z1-z0>8)loaded=false;else for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)loaded&=world.hasChunk(x,z);
        data.addProperty("rayChunksLoaded",loaded);
        if(loaded){var hit=ClassicRaytrace.living(p,12,ClipContext.Fluid.NONE);data.addProperty("rayHit",hit==null?"null":hit.getType().toString());
            if(hit instanceof EntityHitResult entity){var e=entity.getEntity();data.addProperty("rayEntityType",e.getType().toString());data.addProperty("rayEntityId",e.getUUID().toString());
                data.addProperty("rayEntityPosition",e.position().toString());data.addProperty("rayEntityLOS",p.hasLineOfSight(e));
                if(e instanceof LivingEntity living){data.addProperty("rayEntityHostile",CanonTargetPolicy.hostile(living));
                    data.addProperty("rayEntityAlive",living.isAlive());data.addProperty("rayEntityRemoved",living.isRemoved());
                    data.addProperty("rayEntityMayInteract",world.mayInteract(p,living.blockPosition()));
                    data.addProperty("rayEntityAllied",p.isAlliedTo(living)||living.isAlliedTo(p));
                    data.addProperty("rayEntityUnsupported",HighPowerCombat.unsupportedReason(living).map(Object::toString).orElse(""));
                    if(id.equals("hydro_pressure_jet"))data.addProperty("attackGeometry",(Boolean)invoke(classFor("cn.academy.port.canon.CanonWaterExpansion"),"attackGeometry",
                            new Class<?>[]{ServerPlayer.class,String.class,LivingEntity.class},p,id,living));}}
            if(id.equals("hydro_pressure_jet")){Object targets=invoke(classFor("cn.academy.port.canon.CanonWaterExpansion"),"attackTargets",new Class<?>[]{ServerPlayer.class,String.class},p,id);
                data.addProperty("acceptedTargetCount",((List<?>)targets).size());data.addProperty("controllerReady",(Boolean)invoke(classFor("cn.academy.port.canon.CanonWaterExpansion"),"ready",new Class<?>[]{ServerPlayer.class,String.class},p,id));}
        }
        return data;
    }
    private static Class<?> classFor(String name){try{return Class.forName(name);}catch(ClassNotFoundException e){throw new IllegalStateException(e);}}
    private static Object invoke(Class<?> type,String name,Class<?>[] signature,Object... args){try{Method m=type.getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(null,args);}catch(ReflectiveOperationException e){throw new IllegalStateException("QA read-only method "+type.getSimpleName()+"."+name,e);}}
    private static Map<?,?> selections(Class<?> type){try{Field f=type.getDeclaredField("SELECTIONS");f.setAccessible(true);return (Map<?,?>)f.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
}
