package cn.academy.port.qa;

import cn.academy.port.city.CityEspers;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.client.city.CityDialogueScreen;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

/** Read-only server-tick observer around real NPC menu releases in an isolated test world. Never shipped. */
@EventBusSubscriber(modid="academy")
public final class CityEsperNumericQa {
    private static final Logger LOG=LogUtils.getLogger();
    private static final List<Receipt> RECEIPTS=new ArrayList<>();
    private static final List<Fall> FALLS=new ArrayList<>();
    private static Receipt current;
    private static UUID owner,natural;
    private static Map<String,Object> naturalState;
    private static float baselineDamage;
    private static int retry;
    private static final class Receipt {
        String id,profile,restoredActivity,effect;
        UUID npc;
        int level,declaredEffectTicks,initialRemainingTicks,effectAmplifier,bottlesBefore,bottlesAfter,waterBefore,waterAfter;
        long start=-1,poseEnd=-1,effectEnd=-1,cooldownDue=-1,cooldownReady=-1,blocksBefore,blocksAfter;
        double npcMaxHealth,npcSpeed,x0,y0,z0,x1,y1,z1,displacementXZ,damageReceived;
        float witnessBefore,witnessAfter;
        boolean npcInvulnerable,cooldownButtonDisabled;
        transient Holder<MobEffect> observedEffect;
        transient UUID witness;
        transient BlockPos center;
        final List<String> durationSamples=new ArrayList<>();
    }
    private static final class Fall {
        String label;long start,firstDescent=-1,landed=-1;
        double height,lastY,maxDownStep;float healthBefore,healthAfter;
    }
    private static void require(boolean ok,String why) {if(!ok) throw new IllegalStateException("QA_CITY_ESPER "+why);}
    private static ServerPlayer player(Minecraft mc) {return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static <T> T server(Minecraft mc,java.util.function.Supplier<T> call) {return mc.getSingleplayerServer().submit(call).join();}
    private static Object field(Object instance,String name) {
        try {var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}
        catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }
    private static long started(CityNpcEntity npc) {
        Object demo=field(npc,"demonstration");if(demo==null) return -1;
        try {var method=demo.getClass().getDeclaredMethod("started");method.setAccessible(true);return (long)method.invoke(demo);}
        catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
    }
    private static long cooldown(CityNpcEntity npc) {return (long)field(npc,"nextDemonstration");}
    private static int count(ServerPlayer p,boolean water) {
        int total=0;
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var s=p.getInventory().getItem(i);
            if(water?s.is(Items.POTION)&&s.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).is(Potions.WATER):s.is(Items.GLASS_BOTTLE)) total+=s.getCount();
        }
        return total;
    }
    private static long blocks(ServerLevel level,BlockPos pos) {
        long hash=1;for(var p:BlockPos.betweenClosed(pos.offset(-2,-1,-2),pos.offset(2,3,2))) hash=31*hash+level.getBlockState(p).hashCode();
        return hash;
    }
    private static void arm(ServerPlayer p,CityNpcEntity npc,String name) {
        p.removeAllEffects();p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(6);
        p.setAbsorptionAmount(0);p.invulnerableTime=0;
        var r=new Receipt();r.id=name;r.profile=npc.esper().id();r.level=npc.esper().level();r.npc=npc.getUUID();
        r.npcMaxHealth=npc.getMaxHealth();r.npcSpeed=npc.getAttributeValue(Attributes.MOVEMENT_SPEED);r.npcInvulnerable=npc.isInvulnerable();
        r.x0=npc.getX();r.y0=npc.getY();r.z0=npc.getZ();r.center=npc.blockPosition();r.blocksBefore=blocks(p.serverLevel(),r.center);
        r.bottlesBefore=count(p,false);r.waterBefore=count(p,true);
        r.observedEffect=switch(npc.esper()) {
            case VECTOR,AIR,FLOAT -> MobEffects.SLOW_FALLING;case MENTAL -> MobEffects.NIGHT_VISION;
            case NITROGEN -> MobEffects.DAMAGE_RESISTANCE;case SHADOW -> MobEffects.INVISIBILITY;default -> null;
        };
        r.effect=r.observedEffect==null?"none":r.observedEffect.getRegisteredName();
        r.declaredEffectTicks=switch(npc.esper()) {case VECTOR,AIR,FLOAT ->300;case MENTAL ->400;case NITROGEN ->200;case SHADOW ->160;default ->0;};
        if(npc.esper()==CityEspers.Profile.MELT) {
            var cow=EntityType.COW.create(p.serverLevel());require(cow!=null,"cow witness unavailable");
            var direction=p.position().subtract(npc.position()).multiply(1,0,1).normalize();
            cow.moveTo(npc.getX()+direction.x*1.05,npc.getY(),npc.getZ()+direction.z*1.05,0,0);cow.setNoAi(true);
            require(p.serverLevel().addFreshEntity(cow),"cow witness did not spawn");r.witness=cow.getUUID();r.witnessBefore=cow.getHealth();
        }
        current=r;RECEIPTS.add(r);owner=p.getUUID();
        LOG.info("QA_ESPER_ARM id={} profile={} level={} npcHp={} speed={} invulnerable={}",name,r.profile,r.level,r.npcMaxHealth,r.npcSpeed,r.npcInvulnerable);
    }
    private static void startFall(ServerPlayer p,String label) {
        var f=new Fall();f.label=label;f.start=p.serverLevel().getGameTime();f.height=8;f.lastY=8;f.healthBefore=p.getHealth();
        p.fallDistance=0;p.invulnerableTime=0;p.teleportTo(-66,8,-29);FALLS.add(f);owner=p.getUUID();
    }
    @SubscribeEvent public static void observe(LevelTickEvent.Post event) {
        if(!(event.getLevel() instanceof ServerLevel level)||owner==null) return;
        if(!(level.getPlayerByUUID(owner) instanceof ServerPlayer p)) return;long now=level.getGameTime();
        for(var f:FALLS) if(f.landed<0) {
            double drop=f.lastY-p.getY();f.lastY=p.getY();
            if(drop>.001&&f.firstDescent<0) f.firstDescent=now;
            f.maxDownStep=Math.max(f.maxDownStep,drop);
            if(f.firstDescent>=0&&p.onGround()&&p.getY()<.1) {
                f.landed=now;f.healthAfter=p.getHealth();
                LOG.info("QA_ESPER_FALL label={} totalTicks={} movingTicks={} maxDownStep={} damage={}",f.label,f.landed-f.start,f.landed-f.firstDescent,f.maxDownStep,f.healthBefore-f.healthAfter);
            }
        }
        for(var r:RECEIPTS) {
            if(!(level.getEntity(r.npc) instanceof CityNpcEntity npc)) continue;
            if(r.start<0) {
                long actual=started(npc);if(actual<0) continue;r.start=actual;r.cooldownDue=cooldown(npc);
                if(r.observedEffect!=null) {
                    var effect=p.getEffect(r.observedEffect);require(effect!=null,"release did not apply "+r.effect);
                    r.initialRemainingTicks=effect.getDuration();r.effectAmplifier=effect.getAmplifier();
                }
                r.bottlesAfter=count(p,false);r.waterAfter=count(p,true);
                if(npc.esper()==CityEspers.Profile.NITROGEN) {
                    float health=p.getHealth();p.hurt(p.damageSources().generic(),10);r.damageReceived=health-p.getHealth();
                }
                LOG.info("QA_ESPER_START id={} tick={} next={} effect={} duration={} amplifier={} bottles={}->{} water={}->{} damage={}",r.id,r.start,r.cooldownDue,r.effect,r.initialRemainingTicks,r.effectAmplifier,r.bottlesBefore,r.bottlesAfter,r.waterBefore,r.waterAfter,r.damageReceived);
            }
            if(r.poseEnd<0&&npc.activity()!=CityNpcEntity.Activity.CAST) {
                r.poseEnd=now;r.restoredActivity=npc.activity().id();r.x1=npc.getX();r.y1=npc.getY();r.z1=npc.getZ();
                r.displacementXZ=Math.hypot(r.x1-r.x0,r.z1-r.z0);r.blocksAfter=blocks(level,r.center);
                if(r.witness!=null&&level.getEntity(r.witness) instanceof LivingEntity witness) {r.witnessAfter=witness.getHealth();witness.discard();}
                LOG.info("QA_ESPER_END id={} castTicks={} restored={} dx={} dy={} dz={} xz={} blocksUnchanged={} witnessHealth={}->{}",r.id,r.poseEnd-r.start,r.restoredActivity,r.x1-r.x0,r.y1-r.y0,r.z1-r.z0,r.displacementXZ,r.blocksBefore==r.blocksAfter,r.witnessBefore,r.witnessAfter);
            }
            if(r.observedEffect!=null&&r.effectEnd<0) {
                var effect=p.getEffect(r.observedEffect);
                if(effect==null) {
                    r.effectEnd=now;LOG.info("QA_ESPER_EFFECT_END id={} ticksAfterStart={}",r.id,now-r.start);
                } else if((now-r.start)%20==0) r.durationSamples.add((now-r.start)+":"+effect.getDuration());
            }
            if(r.cooldownReady<0&&now>=r.cooldownDue) {r.cooldownReady=now;LOG.info("QA_ESPER_READY id={} elapsedTicks={}",r.id,now-r.start);}
        }
    }
    static boolean step(Minecraft mc,String op) {
        var a=op.split(",");
        switch(a[0]) {
            case "baseline" -> server(mc,() -> {
                var p=player(mc);p.removeAllEffects();p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(6);p.setAbsorptionAmount(0);p.invulnerableTime=0;
                float before=p.getHealth();p.hurt(p.damageSources().generic(),10);baselineDamage=before-p.getHealth();p.setHealth(p.getMaxHealth());
                startFall(p,"unbuffed");LOG.info("QA_ESPER_BASELINE genericInput=10 actualDamage={}",baselineDamage);return null;
            });
            case "fall_done" -> {
                boolean done=server(mc,() -> !FALLS.isEmpty()&&FALLS.getLast().landed>=0);
                if(!done) {require(++retry<500,"fall did not land");return false;}retry=0;
            }
            case "arm" -> {
                require(mc.screen instanceof CityDialogueScreen,"ability menu required");var id=((CityDialogueScreen)mc.screen).view().npc();
                server(mc,() -> {var p=player(mc);var npc=(CityNpcEntity)p.serverLevel().getEntity(id);arm(p,npc,a[1]);return null;});
            }
            case "aim" -> {
                double[] at=server(mc,() -> {var npc=player(mc).serverLevel().getEntity(current.npc);return new double[]{npc.getX(),npc.getY()+1,npc.getZ()};});
                LiveInputQa.aim(mc,at[0]+","+at[1]+","+at[2]);
            }
            case "fall_buffed" -> server(mc,() -> {var p=player(mc);require(p.hasEffect(MobEffects.SLOW_FALLING),"NPC slow fall missing");startFall(p,"npc_slow_falling");return null;});
            case "done" -> {
                boolean done=server(mc,() -> current.start>=0&&current.poseEnd>=0&&(current.observedEffect==null||current.effectEnd>=0));
                if(!done) {require(++retry<1000,"ability or effect did not finish");return false;}retry=0;
                server(mc,() -> {
                    var r=current;require(r.poseEnd-r.start>=59&&r.poseEnd-r.start<=61,"cast duration "+r.id);
                    require(r.cooldownDue-r.start==1200,"cooldown mismatch "+r.id);
                    require(r.blocksBefore==r.blocksAfter,"nearby blocks changed "+r.id);
                    if(r.declaredEffectTicks>0) require(Math.abs((r.effectEnd-r.start)-r.declaredEffectTicks)<=2,"effect duration mismatch "+r.id);
                    if(r.profile.equals("water")) require(r.bottlesAfter==r.bottlesBefore-1&&r.waterAfter==r.waterBefore+1,"water conversion failed");
                    if(r.profile.equals("nitrogen")) require(Math.abs(r.damageReceived-8)<.05&&Math.abs(baselineDamage-10)<.05,"resistance reduction mismatch");
                    if(r.profile.equals("melt")) require(r.witnessAfter==r.witnessBefore,"visual-only beam damaged witness");return null;
                });
            }
            case "cooldown" -> {
                require(mc.screen instanceof CityDialogueScreen,"ability page required");
                var view=((CityDialogueScreen)mc.screen).view();var option=view.options().stream().filter(o -> o.id().equals("demo")).findFirst().orElseThrow();
                boolean expect=Boolean.parseBoolean(a[1]);require(option.enabled()==expect,"cooldown menu did not reflect readiness");
                server(mc,() -> {var receipt=RECEIPTS.stream().filter(r -> r.npc.equals(view.npc())).findFirst().orElseThrow();
                    if(!expect) receipt.cooldownButtonDisabled=true;
                    LOG.info("QA_ESPER_COOLDOWN_UI id={} enabled={} remainingTicks={}",receipt.id,expect,Math.max(0,receipt.cooldownDue-player(mc).serverLevel().getGameTime()));return null;});
            }
            case "natural" -> {
                if(natural==null) natural=server(mc,() -> {
                    var p=player(mc);var npc=p.serverLevel().getEntitiesOfClass(CityNpcEntity.class,p.getBoundingBox().inflate(90),n -> n.ambient()&&n.getCustomName()==null&&n.esper()!=CityEspers.Profile.NONE&&cooldown(n)<=p.serverLevel().getGameTime()).stream().findFirst().orElse(null);
                    if(npc==null) return null;
                    var state=new LinkedHashMap<String,Object>();state.put("profile",npc.esper().id());state.put("level",npc.esper().level());state.put("look",npc.look().id());state.put("maxHealth",npc.getMaxHealth());state.put("movementSpeed",npc.getAttributeValue(Attributes.MOVEMENT_SPEED));state.put("invulnerable",npc.isInvulnerable());state.put("activityBefore",npc.activity().id());naturalState=state;
                    return npc.getUUID();
                });
                if(natural==null) {require(++retry<800,"no natural esper appeared");return false;}
                var npc=mc.level.getEntitiesOfClass(CityNpcEntity.class,mc.player.getBoundingBox().inflate(110),n -> n.getUUID().equals(natural)).stream().findFirst().orElse(null);
                if(npc==null||npc.distanceToSqr(mc.player)>9) {
                    if(++retry%20==1) server(mc,() -> {var p=player(mc);var n=p.serverLevel().getEntity(natural);p.teleportTo(n.getX()+2.4,n.getY(),n.getZ());return null;});
                    require(retry<800,"natural esper not reachable");return false;
                }
                retry=0;mc.gameMode.interact(mc.player,npc,InteractionHand.MAIN_HAND);LOG.info("QA_ESPER_NATURAL {}",naturalState);
            }
            case "report" -> {
                String json=server(mc,() -> {
                    var data=new LinkedHashMap<String,Object>();data.put("receipts",RECEIPTS);data.put("falls",FALLS);data.put("unbuffedGenericDamage",baselineDamage);data.put("naturalEsper",naturalState);
                    require(RECEIPTS.size()>=13,"missing releases");require(FALLS.size()==2,"missing fall comparison");
                    require(FALLS.get(1).healthAfter==FALLS.get(1).healthBefore,"slow fall did not prevent fall damage");
                    return new GsonBuilder().setPrettyPrinting().create().toJson(data);
                });
                try {Files.writeString(mc.gameDirectory.toPath().resolve("esper-release-measurements.json"),json);}catch(java.io.IOException e) {throw new IllegalStateException(e);}
                LOG.info("QA_ESPER_REPORT receipts={} falls={}",RECEIPTS.size(),FALLS.size());
            }
            default -> throw new IllegalArgumentException("Unknown esper step "+op);
        }
        return true;
    }
}
