package cn.academy.port.qa;

import cn.academy.port.canon.EntityAbilityRuntime;
import cn.academy.port.city.*;
import cn.academy.port.macro.BeamImpactVfx;
import cn.academy.port.skill.EntitySkillVisual;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;

/** Real NPC controller and passive renderer inspection; never injects a visual packet. */
final class MacroNpcBeamQa {
    private static CityNpcEntity npc;private static Husk target;private static Vec3 origin;private static double damage;
    private static final Map<String,Object> report=new LinkedHashMap<>();private static Vec3 left;
    private static Object call(Object o,String name,Class<?>[] types,Object...args)throws ReflectiveOperationException{var m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}
    static void step(Minecraft mc,String op){
        if(op.equals("spawn")){
            var previous=EntitySkillVisual.clientReceiver;EntitySkillVisual.clientReceiver=v->{previous.accept(v);if(npc!=null&&v.owner().equals(npc.getUUID())&&v.skill().equals("meltdowner")&&v.phase().equals("HIT"))origin=v.from();};
            var observer=EntityAbilityRuntime.observer;EntityAbilityRuntime.observer=r->{observer.accept(r);if(npc!=null&&r.caster().equals(npc.getUUID())&&r.skill().equals("meltdowner")&&r.phase().equals("RESOLVE"))damage=r.actualDamage();};
            mc.getSingleplayerServer().submit(()->{
                var w=mc.getSingleplayerServer().overworld();npc=CityNpcContent.NPC.get().create(w);var tag=new CompoundTag();tag.putString("Esper","melt");tag.putInt("EsperLevel",5);tag.putString("Look","esper_uniform");tag.putString("Activity","stand");npc.readAdditionalSaveData(tag);npc.moveTo(.5,-60,.5,0,0);npc.setYHeadRot(0);npc.setNoAi(true);npc.combat();w.addFreshEntity(npc);
                target=EntityType.HUSK.create(w);target.moveTo(.5,-60,16.5,180,0);target.setNoAi(true);target.setPersistenceRequired();w.addFreshEntity(target);
            }).join();
        }else if(op.equals("fire")){
            mc.getSingleplayerServer().submit(()->{var d=EntityAbilityRuntime.DEFINITIONS.stream().filter(v->v.id().equals("meltdowner")).findFirst().orElseThrow();if(!npc.combat().runtime().begin(d,target,false))throw new IllegalStateException("Real NPC beam start refused");}).join();
        }else if(op.equals("check")){
            try{
                if(origin==null||damage<=0)throw new IllegalStateException("No actual NPC hit receipt");
                var type=Class.forName("cn.academy.port.client.canon.AgEnergyBeam");var live=type.getDeclaredField("LIVE");live.setAccessible(true);Object selected=null;BeamImpactVfx.Scene scene=null;
                for(var p:(List<?>)live.get(null)){var s=(BeamImpactVfx.Scene)call(p,"scene",new Class<?>[0]);if(s.owner().equals(npc.getUUID())){selected=p;scene=s;break;}}
                if(selected==null)throw new IllegalStateException("NPC pulse absent");var resolve=type.getDeclaredMethod("resolve",selected.getClass(),double.class);resolve.setAccessible(true);var resolved=resolve.invoke(null,selected,(double)mc.level.getGameTime());var actual=(Vec3)call(resolved,"from",new Class<?>[0]);
                if(actual.distanceToSqr(origin)>1e-12)throw new IllegalStateException("NPC origin offset twice: "+origin+" -> "+actual);
                var renderer=Class.forName("cn.academy.port.client.canon.AgVolumeRenderer");var diagnostics=renderer.getDeclaredField("diagnostics");diagnostics.setAccessible(true);var map=(Map<?,?>)diagnostics.get(null);
                if(!((List<?>)map.getOrDefault("castIds",null)).contains(scene.cast().toString()))throw new IllegalStateException("NPC pulse did not enter actual volume rendering");
                report.put("npc",Map.of("serverHandOrigin",origin.toString(),"renderedOrigin",actual.toString(),"damageReceipt",damage,"cast",scene.cast().toString(),"verifiedActualVolume",true));
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            write(mc);mc.getSingleplayerServer().submit(()->{npc.discard();target.discard();}).join();
        }else if(op.equals("hover")){
            mc.getSingleplayerServer().submit(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());p.getAbilities().mayfly=true;p.getAbilities().flying=true;p.setNoGravity(true);p.onUpdateAbilities();}).join();
        }else if(op.equals("left")||op.equals("right")){
            try{var c=Class.forName("cn.academy.port.client.canon.AgNativeBeams");var f=c.getDeclaredField("lastFrom");f.setAccessible(true);var v=(Vec3)f.get(null);if(v==null)throw new IllegalStateException("No actual native ray mesh");report.put(op+"VerticalOrigin",v.toString());if(op.equals("left"))left=v;else if(left==null||left.x<=v.x+.1)throw new IllegalStateException("Vertical hand origins did not separate: "+left+" / "+v);}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}write(mc);
        }else throw new IllegalArgumentException(op);
    }
    private static void write(Minecraft mc){try{var path=Path.of(System.getProperty("academy.qa.macro.root",mc.gameDirectory.toString())).resolve("actual-npc-and-vertical-origins.json");Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));LogUtils.getLogger().info("QA_MACRO_ORIGINS {}",report);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
