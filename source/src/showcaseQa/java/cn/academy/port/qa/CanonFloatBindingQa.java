package cn.academy.port.qa;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonVfxClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Opt-in read-only observation after the original receiver. Never selects a target or casts. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonFloatBindingQa {
    private static final String ROLE=System.getProperty("academy.qa.canon.role","");
    private static final String RUN=System.getProperty("academy.qa.canon.run","");
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.showcase")
            &&Boolean.getBoolean("academy.qa.canon.floatBinding")&&(ROLE.equals("host")||ROLE.equals("observer"));
    private static final Path ROOT=Path.of(System.getProperty("academy.qa.canon.root",".")).toAbsolutePath().normalize();
    private static final List<Watch> WATCHES=new ArrayList<>();
    private static long ticks;
    private static final class Watch {
        final CanonVfx.Visual visual;
        final long tick;
        int frames;
        Watch(CanonVfx.Visual visual){this.visual=visual;this.tick=ticks;}
    }
    private CanonFloatBindingQa() {}

    public static void receive(CanonVfx.Visual visual,Minecraft mc){
        if(!ENABLED||visual==null||!visual.skill().startsWith("float_")||mc.level==null||mc.player==null)return;
        if(WATCHES.size()>=32)WATCHES.removeFirst();
        var watch=new Watch(visual);WATCHES.add(watch);
        observe(mc,watch,"after-original-receiver",1);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED)return;ticks++;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null){WATCHES.clear();return;}
        for(var watch:List.copyOf(WATCHES)){
            long age=ticks-watch.tick;
            if(age<=8||age%4==0)observe(mc,watch,"client-tick",1);
            if(age>=32)WATCHES.remove(watch);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(!ENABLED||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for(var watch:List.copyOf(WATCHES))if(watch.frames<12){watch.frames++;observe(mc,watch,"after-particles-frame-"+watch.frames,partial);}
    }
    private static Object value(Object record,String name){try{
        Method method=record.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(record);
    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static List<?> effects(){try{
        Field field=CanonVfxClient.class.getDeclaredField("EFFECTS");field.setAccessible(true);return List.copyOf((List<?>)field.get(null));
    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static JsonObject point(Vec3 p){var j=new JsonObject();j.addProperty("x",p.x);j.addProperty("y",p.y);j.addProperty("z",p.z);return j;}
    private static JsonObject box(AABB b){var j=new JsonObject();j.addProperty("minX",b.minX);j.addProperty("minY",b.minY);j.addProperty("minZ",b.minZ);j.addProperty("maxX",b.maxX);j.addProperty("maxY",b.maxY);j.addProperty("maxZ",b.maxZ);return j;}
    private static JsonObject entity(Entity e,CanonVfx.Visual v,float partial){
        var j=new JsonObject();j.addProperty("id",e.getId());j.addProperty("uuid",e.getUUID().toString());j.addProperty("type",e.getType().toString());
        j.addProperty("alive",e.isAlive());j.addProperty("removed",e.isRemoved());j.addProperty("dimension",e.level().dimension().location().toString());
        j.addProperty("sameClientWorld",e.level()==Minecraft.getInstance().level);j.add("current",point(e.position()));
        var lerp=new Vec3(e.lerpTargetX(),e.lerpTargetY(),e.lerpTargetZ());j.add("lerpDestination",point(lerp));
        j.add("renderPosition",point(e.getPosition(partial)));j.add("velocity",point(e.getDeltaMovement()));j.add("boundingBox",box(e.getBoundingBox()));
        j.addProperty("currentEndpointDistanceSqr",e.position().distanceToSqr(v.endpoint()));
        j.addProperty("lerpEndpointDistanceSqr",lerp.distanceToSqr(v.endpoint()));
        j.addProperty("candidateKindAllowed",v.skill().startsWith("float_item_")?e instanceof ItemEntity:e instanceof LivingEntity);
        return j;
    }
    private static void observe(Minecraft mc,Watch watch,String stage,float partial){
        if(!RUN.matches("[a-zA-Z0-9_-]{8,64}")||!mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(ROOT))
            throw new IllegalStateException("float binding diagnostics require isolated owned profile");
        var v=watch.visual;var out=new JsonObject();out.addProperty("schema","canon-readonly-float-binding-v1");
        out.addProperty("observationOnly",true);out.addProperty("run",RUN);out.addProperty("role",ROLE);out.addProperty("time",System.currentTimeMillis());
        out.addProperty("stage",stage);out.addProperty("ageClientTicks",ticks-watch.tick);out.addProperty("worldTick",mc.level.getGameTime());
        out.addProperty("world",mc.level.dimension().location().toString());out.addProperty("clientWorldIdentity",System.identityHashCode(mc.level));
        out.addProperty("skill",v.skill());out.addProperty("phase",v.phase().name());out.addProperty("owner",v.owner().toString());out.addProperty("actorId",v.entity());
        out.addProperty("packetObjectIdentity",System.identityHashCode(v));out.addProperty("self",mc.player.getUUID().toString());out.addProperty("localOwner",v.owner().equals(mc.player.getUUID()));
        out.add("origin",point(v.origin()));out.add("endpoint",point(v.endpoint()));out.addProperty("partial",partial);out.addProperty("cameraType",mc.options.getCameraType().name());
        if(mc.getCameraEntity()!=null)out.add("cameraEntity",entity(mc.getCameraEntity(),v,partial));
        out.add("cameraPosition",point(mc.gameRenderer.getMainCamera().getPosition()));
        Object effect=null;for(Object e:effects())if(value(e,"visual")==v){effect=e;break;}
        out.addProperty("effectExists",effect!=null);
        if(effect!=null){
            out.addProperty("effectLife",(int)value(effect,"life"));out.addProperty("effectStarted",(double)value(effect,"started"));
            Entity selected=(Entity)value(effect,"trackedTarget");out.addProperty("selectedIsNull",selected==null);
            if(selected!=null){out.add("selected",entity(selected,v,partial));var render=selected.getPosition(partial);
                out.add("actualInterpolatedTargetBox",box(selected.getBoundingBox().move(render.subtract(selected.position()))));}
        }
        var candidates=new JsonArray();for(var e:mc.level.entitiesForRendering())
            if(e instanceof LivingEntity||e instanceof ItemEntity)candidates.add(entity(e,v,partial));
        out.add("renderingEntities",candidates);
        try{Files.writeString(ROOT.resolve(ROLE+"-float-binding-receipts.jsonl"),out+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(IOException error){throw new IllegalStateException(error);}
        LogUtils.getLogger().info("QA_FLOAT_BINDING {}",out);
    }
}
