package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.canon.CanonOffense;
import cn.academy.port.highpower.*;
import cn.academy.port.city.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Owned integrated-world load fixture. These five connected mock casters are explicitly not five real clients. */
final class MacroStressQa {
    private static final List<ServerPlayer> casters=new ArrayList<>();
    private static final List<EmbeddedChannel> channels=new ArrayList<>();
    private static final List<Entity> entities=new ArrayList<>();
    private MacroStressQa(){}
    static void step(Minecraft mc,String op){
        if(!Boolean.getBoolean("academy.qa.confirmOwnWorld"))throw new IllegalStateException("owned QA world required");
        if(op.equals("peek")||op.equals("peek-five")){
            try{var f=Class.forName("cn.academy.port.client.canon.CanonFx").getDeclaredField("lastStats");f.setAccessible(true);LogUtils.getLogger().info("QA_MACRO_STRESS renderedFrameBudget={}",f.get(null));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            if(cn.academy.port.VisualTierConfig.tier()==cn.academy.port.VisualTier.OFF){LogUtils.getLogger().info("QA_MACRO_STRESS volumeFrame={{volumeCount=0, disabledByClassic=true}}");return;}
            try{var f=Class.forName("cn.academy.port.client.canon.AgVolumeRenderer").getDeclaredField("diagnostics");f.setAccessible(true);var values=(Map<?,?>)f.get(null);LogUtils.getLogger().info("QA_MACRO_STRESS volumeFrame={}",values);if(op.equals("peek-five")&&!Integer.valueOf(5).equals(values.get("volumeCount")))throw new IllegalStateException("Five actual concurrent volume renders were not observed");}catch(ClassNotFoundException baseline){}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            return;
        }
        mc.getSingleplayerServer().submit(()->{
            var server=mc.getSingleplayerServer();var world=server.overworld();
            if(op.equals("setup")){
                clear();
                for(int i=0;i<5;i++){
                    var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"FxCaster"+i),false);
                    var p=new ServerPlayer(server,world,cookie.gameProfile(),cookie.clientInformation());
                    var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);NetworkRegistry.configureMockConnection(connection);
                    server.getPlayerList().placeNewPlayer(connection,p,cookie);casters.add(p);channels.add(channel);
                    p.setGameMode(GameType.SURVIVAL);p.moveTo(new Vec3((i-2)*3,-60,0),0,0);p.setYHeadRot(0);p.connection.resetPosition();p.setNoGravity(true);
                    var state=AbilityStorage.get(p);state.changeCategoryClassic("mental_out");state.setLevel(5);
                    for(var skill:SkillCatalog.REGISTERED)if(skill.category().equals("mental_out"))state.learn(skill.id());
                    state.activated=true;state.overloadFine=true;state.experience.put("mental_resonance_cascade",1d);
                    if(!state.presets.edit(0,0,"mental_resonance_cascade",state::learned)||!HighPowerCombat.enroll(p,5))throw new IllegalStateException("finite real caster fixture");
                    HighPowerResources.initializeBudget(p);AbilityStorage.save(p);
                }
                for(int i=0;i<20;i++){
                    var mob=EntityType.HUSK.create(world);mob.moveTo(new Vec3((i%10-4.5)*2,-60,12+i/10*3),180,0);mob.setNoAi(true);mob.setNoGravity(true);
                    world.addFreshEntity(mob);HighPowerCombat.enroll(mob,5);entities.add(mob);
                }
                for(int i=0;i<10;i++){
                    var npc=CityNpcContent.NPC.get().create(world);npc.moveTo(new Vec3(i%2==0?-14:14,-60,3+(i/2)*3));
                    try{var method=CityNpcEntity.class.getDeclaredMethod("prepareAmbient",CityNpcEntity.Look.class,CityNpcEntity.Activity.class);method.setAccessible(true);method.invoke(npc,CityNpcEntity.Look.STUDENT_BOOKS,CityNpcEntity.Activity.STAND);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                    world.addFreshEntity(npc);npc.combat();entities.add(npc);
                }
                for(var caster:casters){
                    var aim=entities.getFirst().getBoundingBox().getCenter().subtract(caster.getEyePosition());
                    caster.setYRot((float)-Math.toDegrees(Math.atan2(aim.x,aim.z)));caster.setXRot((float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z))));caster.setYHeadRot(caster.getYRot());
                }
                LogUtils.getLogger().info("QA_MACRO_STRESS setup actualCityNpcs=10 mockConnectedCasters=5 realHuskTargets=20 syntheticVisuals=false");
            }else if(op.equals("cast")){
                int accepted=0;for(var p:casters){
                    // Living head/body rotation advances during the warm-up fixture wait. Aim at the real
                    // target again at input time, as a real client would, rather than preserving a stale angle.
                    var aim=entities.getFirst().getBoundingBox().getCenter().subtract(p.getEyePosition());
                    p.setYRot((float)-Math.toDegrees(Math.atan2(aim.x,aim.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z))));p.setYHeadRot(p.getYRot());p.setYBodyRot(p.getYRot());
                    if(CanonOffense.cast(p,"mental_resonance_cascade"))accepted++;
                    else {
                        var state=AbilityStorage.get(p);
                        try{var select=CanonOffense.class.getDeclaredMethod("select",ServerPlayer.class,cn.academy.port.canon.CanonOffenseSkills.Spec.class,cn.academy.port.canon.CanonLiquidEntities.Shadow.class);select.setAccessible(true);
                            LogUtils.getLogger().error("QA_MACRO_STRESS_REFUSED canUse={} ready={} activated={} interference={} preset={} unsupported={} targets={} feet={} head={} pitch={}",state.canUse("mental_resonance_cascade"),HighPowerResources.ready(p),state.activated,state.interfering,state.presets.currentContains("mental_resonance_cascade"),HighPowerCombat.unsupportedReason(p),((List<?>)select.invoke(null,p,cn.academy.port.canon.CanonOffenseSkills.find("mental_resonance_cascade"),null)).size(),p.position(),p.getYHeadRot(),p.getXRot());
                            var t=(net.minecraft.world.entity.LivingEntity)entities.getFirst();
                            var point=CanonOffense.class.getDeclaredMethod("aimPoint",ServerPlayer.class,double.class);point.setAccessible(true);
                            LogUtils.getLogger().error("QA_MACRO_STRESS_TARGET type={} alive={} removed={} pos={} unsupported={} los={} allowed={} invulnerable={} aimPoint={}",t.getType(),t.isAlive(),t.isRemoved(),t.position(),HighPowerCombat.unsupportedReason(t),p.hasLineOfSight(t),world.mayInteract(p,t.blockPosition()),t.isInvulnerable(),point.invoke(null,p,cn.academy.port.canon.CanonOffenseSkills.find("mental_resonance_cascade").reach()));
                        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                    }
                }
                LogUtils.getLogger().info("QA_MACRO_STRESS accepted={} simultaneousRealControllers=true",accepted);
                if(accepted!=5)throw new IllegalStateException("Five actual LV5 controllers required, accepted="+accepted);
            }else if(op.equals("check")){
                int pending=0;try{var c=Class.forName("cn.academy.port.macro.MacroSettlement");pending=(int)c.getMethod("pending",net.minecraft.server.MinecraftServer.class).invoke(null,server);}catch(ClassNotFoundException baseline){}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
                LogUtils.getLogger().info("QA_MACRO_STRESS completed={} pendingSettlementJobs={}",casters.stream().noneMatch(CanonOffense::busy),pending);
                if(pending!=0||casters.stream().anyMatch(CanonOffense::busy))throw new IllegalStateException("load queues have not drained");
            }else if(op.equals("clear"))clear();else throw new IllegalArgumentException(op);
            return null;
        }).join();
    }
    private static void clear(){entities.forEach(Entity::discard);entities.clear();for(var p:casters){p.server.getPlayerList().remove(p);p.discard();}casters.clear();channels.forEach(EmbeddedChannel::finishAndReleaseAll);channels.clear();}
}
