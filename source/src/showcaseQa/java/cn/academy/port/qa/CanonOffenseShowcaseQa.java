package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.*;
import cn.academy.port.highpower.*;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** Fixture/receipt code only. New attacks and their visuals are always initiated by real key R. */
final class CanonOffenseShowcaseQa {
    private static final Logger LOG=LogUtils.getLogger();
    private static final List<Trial> TRIALS=new ArrayList<>();
    private static final class Trial {
        String skill;int paperBefore,paperAfter,poseSamples,releaseSamples;double hpBefore,hpAfter;
        final List<CanonOffense.Receipt> receipts=new ArrayList<>();
        transient final List<Husk> mobs=new ArrayList<>();
    }
    private static Trial current;
    private static boolean installed;
    private static Vec3 aim;
    private static UUID owner;
    private static void require(boolean b,String why){if(!b)throw new IllegalStateException("QA_OFFENSE "+why);}
    private static <T>T server(Minecraft mc,java.util.function.Supplier<T> body){return mc.getSingleplayerServer().submit(body).join();}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static double hp(Husk t){return CityNpcPower.health(t).doubleValue();}
    static void step(Minecraft mc,String op){
        if(op.startsWith("prepare,")){
            String id=op.substring(8);var spec=CanonOffenseSkills.find(id);require(spec!=null,"registered skill "+id);
            server(mc,()->{
                var p=player(mc);owner=p.getUUID();
                if(!installed){
                    var old=CanonOffense.observer;
                    CanonOffense.observer=r->{old.accept(r);if(current==null||!r.owner().equals(owner)||!r.skill().equals(current.skill))return;
                        current.receipts.add(r);LOG.info("QA_OFFENSE_RECEIPT {}",r);
                        if(r.phase().equals("WARMUP")&&specForCurrent().category().equals("float_dial"))for(var t:current.mobs){t.setNoAi(false);t.setNoGravity(false);}
                    };installed=true;
                }
                if(current!=null)for(var t:current.mobs)t.discard();
                current=new Trial();current.skill=id;TRIALS.add(current);
                int count=Math.min(spec.targets(),spec.shape()==CanonOffenseSkills.Shape.LINE?3:spec.shape()==CanonOffenseSkills.Shape.CONE||spec.shape()==CanonOffenseSkills.Shape.AREA||spec.shape()==CanonOffenseSkills.Shape.BODY_SWEEP?3:1);
                var body=spec.body()?p.serverLevel().getEntitiesOfClass(CanonLiquidEntities.Shadow.class,p.getBoundingBox().inflate(32),b->b.controlOwner().filter(owner::equals).isPresent()).stream().findFirst().orElse(null):null;
                require(!spec.body()||body!=null,"real owned shadow required");
                for(int n=0;n<count;n++){
                    Vec3 pos=body!=null?body.position().add((n-1)*.65,0,1.25)
                        :p.position().add(spec.shape()==CanonOffenseSkills.Shape.LINE?0:n==0?0:n==1?-1:1,spec.shape()==CanonOffenseSkills.Shape.AIR_POINT?3.5:0,6+(spec.shape()==CanonOffenseSkills.Shape.LINE?n*2:n==0?0:.5));
                    var t=EntityType.HUSK.create(p.serverLevel());require(t!=null,"hostile fixture available");
                    t.moveTo(pos,180,0);t.setNoAi(true);t.setNoGravity(true);t.setPersistenceRequired();t.setOnGround(spec.shape()!=CanonOffenseSkills.Shape.AIR_POINT);
                    t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,600,10,false,false));
                    require(p.serverLevel().addFreshEntity(t)&&HighPowerCombat.enroll(t,5),"real enrolled hostile");current.mobs.add(t);
                }
                aim=current.mobs.getFirst().getBoundingBox().getCenter();
                current.hpBefore=current.mobs.stream().mapToDouble(CanonOffenseShowcaseQa::hp).sum();
                current.paperBefore=p.getMainHandItem().is(net.minecraft.world.item.Items.PAPER)?p.getMainHandItem().getCount():0;
                LOG.info("QA_OFFENSE_PREPARED id={} targets={} hp={} body={} viaRealKeyNext=true",id,count,current.hpBefore,body!=null);
                return null;
            });
            mc.player.lookAt(Anchor.EYES,aim);mc.player.setYHeadRot(mc.player.getYRot());
        }else if(op.equals("aim")){
            require(aim!=null,"fixture before aim");mc.player.lookAt(Anchor.EYES,aim);mc.player.setYHeadRot(mc.player.getYRot());
        }else if(op.equals("pose")){
            var pose=cn.academy.port.client.canon.CanonPoseClient.snapshot(mc.player,0);
            if(pose!=null&&pose.skill().equals(current.skill)){
                current.poseSamples++;if(pose.impact())current.releaseSamples++;
                LOG.info("QA_OFFENSE_POSE id={} age={} duration={} impact={} actualServerLease=true",pose.skill(),pose.age(),pose.duration(),pose.impact());
            }
        }else if(op.equals("check")){
            server(mc,()->{
                var p=player(mc);var spec=specForCurrent();
                current.hpAfter=current.mobs.stream().mapToDouble(CanonOffenseShowcaseQa::hp).sum();
                current.paperAfter=p.getMainHandItem().is(net.minecraft.world.item.Items.PAPER)?p.getMainHandItem().getCount():0;
                var hits=current.receipts.stream().filter(r->r.phase().equals("RESOLVE")).toList();
                require(hits.size()==1&&hits.getFirst().targets()>0&&current.hpBefore>current.hpAfter,"exactly one real damage release "+current.skill);
                require(Math.abs(hits.getFirst().cpBefore()-hits.getFirst().cpAfter()-hits.getFirst().cp())<.00001,"one atomic CP debit");
                double expected=1_000_000*CanonEmission.levelFactor(spec.level()).doubleValue()*.984;
                require(Math.abs(current.hpBefore-current.hpAfter-expected)<.05,"unchanged whole-cast player damage factor "+current.skill);
                require(current.poseSamples>0&&current.releaseSamples>0,"accepted warm-up/release pose visible "+current.skill);
                LOG.info("QA_OFFENSE_PASS id={} targets={} damage={} cp={} poseSamples={} releaseSamples={}",current.skill,hits.getFirst().targets(),current.hpBefore-current.hpAfter,hits.getFirst().cp(),current.poseSamples,current.releaseSamples);
                return null;
            });
        }else if(op.equals("report")){
            try{
                Files.writeString(mc.gameDirectory.toPath().resolve("offense-receipts.json"),new GsonBuilder().setPrettyPrinting().create().toJson(TRIALS));
                LOG.info("QA_OFFENSE_REPORT trials={} successful={}",TRIALS.size(),TRIALS.stream().filter(t->t.hpAfter>0&&t.hpAfter<t.hpBefore).count());
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        }else throw new IllegalArgumentException("Unknown offense QA operation: "+op);
    }
    private static CanonOffenseSkills.Spec specForCurrent(){return CanonOffenseSkills.find(current.skill);}
}
