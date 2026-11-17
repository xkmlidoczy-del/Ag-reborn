package cn.academy.biohum;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static cn.academy.biohum.Payloads.Cue.*;

/** Resolve weapon contact and pose from the same motion curves used by the renderer. */
public final class CloseCombat {
    enum Reach {
        FIST(0, .19, 1.18), SHORT(-7, .16, 1.55), SWORD(-12, .13, 1.9),
        LONG_SWORD(-23, .12, 2.5), SPEAR(-15, .11, 2.12), MACE(-10, .22, 1.83);

        final float depth;
        final double thickness, distance;
        Reach(float depth, double thickness, double distance) {
            this.depth=depth;
            this.thickness=thickness;
            this.distance=distance;
        }
    }

    private record Weapon(Vec3 grip, Vec3 tip) {}
    private record Contact(LivingEntity victim, Vec3 at, boolean wall, double order) {}
    static final class Swing {
        final Skills.Skill skill;
        final UUID target;
        Weapon previous;
        boolean contacted;
        Swing(Skills.Skill skill, LivingEntity target) {
            this.skill=skill;
            this.target=target==null?null:target.getUUID();
        }
    }

    public static boolean isAttack(Skills.Skill skill) {
        return skill.action()==Skills.Action.CLOSE&&skill.target()==Skills.Target.ENEMY
                ||skill.id().equals("imagine_breaker");
    }

    static Reach reach(HumNpc npc, Skills.Skill skill) {
        if(skill.id().equals("imagine_breaker")||skill.id().equals("punch"))return Reach.FIST;
        return switch(npc.characterId) {
            case "kanzaki" -> npc.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD)?Reach.LONG_SWORD:Reach.FIST;
            case "itsuwa","sasha","brunhild" -> Reach.SPEAR;
            case "acqua" -> npc.phaseId().equals("ot18_ascalon")?Reach.LONG_SWORD:Reach.MACE;
            case "carissa","elizard","knight_leader","tatemiya" -> Reach.SWORD;
            case "stiyl" -> npc.getMainHandItem().is(BioHum.FLAME_SWORD.get())?Reach.SHORT:Reach.FIST;
            case "ht_trismegistus" -> Reach.SPEAR;
            case "aleister","hamazura","agnese" -> Reach.SHORT;
            default -> Reach.FIST;
        };
    }

    static boolean hasAttack(HumNpc npc) {
        return Skills.get("close_"+npc.characterId+"_strike")!=null;
    }

    static double reach(HumNpc npc) {
        var move=Skills.get("close_"+npc.characterId+"_strike");
        return move==null?0:reach(npc,move).distance*npc.phaseScale();
    }

    static boolean canReach(HumNpc npc, LivingEntity target, Skills.Skill move) {
        if(target==null||!npc.validTarget(target)||!npc.hasLineOfSight(target))return false;
        var goal=target.getBoundingBox();
        var handHeight=npc.getY()+npc.getBbHeight()*.69;
        return goal.minY<=handHeight+.85*npc.phaseScale()&&goal.maxY>=handHeight-.75*npc.phaseScale()
                &&npc.distanceTo(target)<=reach(npc,move).distance*npc.phaseScale()+target.getBbWidth()*.42;
    }

    static boolean begin(HumNpc npc, Skills.Skill skill, LivingEntity target) {
        if(skill==null||CombatSkills.divineSuppressed(npc))return false;
        long now=npc.level().getGameTime();
        if(!npc.technique().isEmpty()||npc.suspended()||npc.readyAt(skill.id())>now)return false;
        boolean contact=isAttack(skill);
        if(contact&&target!=null&&!canReach(npc,target,skill))return false;
        if(contact&&target!=null&&!skill.id().startsWith("close_")&&skill.range()>0
                &&npc.distanceToSqr(target)>skill.range()*skill.range())return false;
        if(contact&&target==null&&!skill.id().equals("imagine_breaker"))return false;
        if(skill.id().equals("yuisen")&&!npc.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SWORD))return false;
        npc.getNavigation().stop();
        npc.beginTechnique(skill.id());
        npc.closeSwing=new Swing(skill,target);
        npc.setReady(skill.id(),now+skill.cooldown());
        npc.expression(contact?2:1);
        if(target!=null)npc.faceCast(aim(npc,target));
        return true;
    }

    public static boolean react(HumNpc npc, LivingEntity target) {
        String id="close_"+npc.characterId;
        var strike=Skills.get(id+"_strike");
        boolean danger=target.distanceTo(npc)<3.25&&npc.hasLineOfSight(target);
        if(!danger)return false;
        if(strike==null)return begin(npc,Skills.get(id+"_react"),target);
        boolean incoming=target instanceof HumNpc other&&incoming(npc,other);
        if(incoming&&npc.hurtTime>0)return false;
        if(npc.characterId.equals("kanzaki")&&npc.riposteUntil>npc.level().getGameTime()
                &&begin(npc,Skills.get("close_kanzaki_counter"),target)){
            npc.riposteUntil=0;
            return true;
        }
        if((incoming||!canReach(npc,target,strike))&&begin(npc,Skills.get(id+"_react"),target))return true;
        return begin(npc,strike,target);
    }

    static boolean incoming(HumNpc defender,HumNpc attacker){
        var swing=attacker.closeSwing;
        return swing!=null&&!swing.contacted&&isAttack(swing.skill)
                &&defender.getUUID().equals(swing.target)&&attacker.signatureAge()<swing.skill.windup();
    }

    static boolean guard(HumNpc defender,LivingEntity attacker){
        return begin(defender,Skills.get("close_"+defender.characterId+"_react"),attacker);
    }

    private static Vec3 aim(HumNpc npc,LivingEntity target) {
        AABB box=target.getBoundingBox();
        double mid=Mth.clamp(npc.getY()+npc.getBbHeight()*.74,
                box.minY+target.getBbHeight()*.25,box.maxY-target.getBbHeight()*.2);
        return new Vec3(target.getX(),mid,target.getZ());
    }

    static void tick(HumNpc npc,ServerLevel level) {
        var swing=npc.closeSwing;
        if(swing==null)return;
        var move=swing.skill;
        long age=npc.signatureAge();
        LivingEntity target=swing.target!=null&&level.getEntity(swing.target) instanceof LivingEntity l?l:null;
        if(age<move.windup()&&target!=null&&npc.validTarget(target))npc.faceCast(aim(npc,target));
        if(!isAttack(move)){
            if(age==move.windup()){
                var at=npc.position().add(0,npc.getBbHeight()*.65,0);
                boolean guarding=move.prop().contains("guard");
                if(guarding)npc.parryUntil=level.getGameTime()+6;
                else if(target!=null) {
                    Vec3 away=npc.position().subtract(target.position()).multiply(1,0,1).normalize();
                    Vec3 side=away.yRot(npc.getId()%2==0?.45f:-.45f).scale(.51);
                    if(level.noCollision(npc,npc.getBoundingBox().move(side))
                            &&level.getBlockState(net.minecraft.core.BlockPos.containing(npc.position().add(side).add(0,-.15,0))).isSolid()){
                        npc.setDeltaMovement(npc.getDeltaMovement().add(side));
                        npc.hasImpulse=true;
                    }
                }
                Payloads.cue(npc,move.id(),RELEASE,at,at,1);
                npc.resolved(move.id());
            }
            return;
        }

        if(age<move.windup()-1||age>move.windup()+3)return;
        var current=weapon(npc,move,age);
        var before=swing.previous;
        swing.previous=current;
        if(age==move.windup()) {
            Payloads.cue(npc,move.id(),RELEASE,current.grip,current.tip,1);
            if(move.id().equals("imagine_breaker"))npc.guardUntil=level.getGameTime()+22;
        }
        if(age<move.windup()||before==null)return;

        Reach reach=reach(npc,move);
        boolean armed=reach!=Reach.FIST;
        if(!swing.contacted&&armed)Payloads.cue(npc,move.id(),BEAM,before.tip,current.tip,reach==Reach.LONG_SWORD?.24f:.17f);
        if(!swing.contacted&&(target!=null||armed)) {
            var from=current.grip;
            var path=new ArrayList<Vec3>(List.of(from,before.grip,before.tip,current.tip));
            AABB area=new AABB(path.getFirst(),path.getLast());
            for(var point:path)area=area.minmax(new AABB(point,point));
            var candidates=level.getEntitiesOfClass(LivingEntity.class,area.inflate(reach.thickness+.1),
                    other->other!=npc&&(other==target||(armed&&npc.validTarget(other))));
            Contact hit=null;
            for(int i=0;i<=4;i++) {
                Vec3 grip=before.grip.lerp(current.grip,i*.25),tip=before.tip.lerp(current.tip,i*.25);
                if(!armed)tip=tip.add(0,-.04,0);
                hit=earlier(hit,contact(level,npc,grip,tip,reach.thickness,candidates,i));
            }
            hit=earlier(hit,contact(level,npc,before.tip,current.tip,reach.thickness,candidates,4));
            if(hit!=null)apply(npc,level,swing,hit);
        }
        if(age==move.windup()+3){
            if(!swing.contacted&&target!=null)Payloads.cue(npc,move.id(),WHIFF,current.tip,current.tip,1);
            else if(target==null)npc.resolved(move.id());
            Payloads.cue(npc,move.id(),RECOVER,current.grip,current.grip,1);
        }
    }

    private static Contact earlier(Contact a,Contact b) {
        return a==null||b!=null&&b.order<a.order?b:a;
    }

    private static Contact contact(ServerLevel level,HumNpc npc,Vec3 from,Vec3 to,double radius,
                                   List<LivingEntity> candidates,int step) {
        var wall=SkillTrajectory.surface(level,from,to);
        double order=from.distanceTo(wall.position());
        Contact first=wall.block()!=null?new Contact(null,wall.position(),true,step*4+order):null;
        for(var victim:candidates) {
            if(!npc.validTarget(victim))continue;
            var box=victim.getBoundingBox().inflate(radius);
            Vec3 hit=box.contains(from)?from:box.clip(from,to).orElse(null);
            if(hit!=null&&from.distanceTo(hit)<order+.000001
                    &&SkillTrajectory.surface(level,npc.position().add(0,npc.getBbHeight()*.68,0),hit).block()==null) {
                order=from.distanceTo(hit);
                first=new Contact(victim,hit,false,step*4+order);
            }
        }
        return first;
    }

    private static void apply(HumNpc npc,ServerLevel level,Swing swing,Contact hit) {
        var move=swing.skill;
        swing.contacted=true;
        if(hit.wall) {
            var center=hit.at.add(hit.at.subtract(npc.position()).normalize().scale(.025));
            var block=net.minecraft.core.BlockPos.containing(center);
            if(move.id().equals("yuisen")&&WorldEffects.edit(level,WorldEffects.Source.of(npc,null),
                    block,WorldEffects.Change.BREAK,move.power()/9)) {
                Payloads.cue(npc,move.id(),TERRAIN,hit.at,hit.at,1);
            } else Payloads.cue(npc,move.id(),WALL,hit.at,hit.at,1);
            return;
        }

        var victim=hit.victim;
        if(move.id().equals("imagine_breaker")&&victim instanceof HumNpc other) {
            var pending=Skills.get(other.casting());
            if(pending!=null&&pending.action()!=Skills.Action.CLOSE
                    &&pending.action()!=Skills.Action.SHOT&&pending.action()!=Skills.Action.STRIKE)other.interrupt();
            other.controlledBy=null;
            other.controlUntil=0;
            CharacterArts.clearCurse(other);
            if(other.characterId.equals("acqua"))other.getPersistentData().putLong("BioHumMercySuppressed",level.getGameTime()+100);
        }

        var incoming=npc.position().subtract(victim.position()).multiply(1,0,1).normalize();
        if(victim instanceof HumNpc defender&&defender.parryUntil>=level.getGameTime()
                &&Vec3.directionFromRotation(0,defender.castYaw()).dot(incoming)>.32) {
            var guarding=Skills.get(defender.technique());
            if(guarding!=null)Payloads.cue(defender,guarding.id(),PARRY,hit.at,hit.at,1);
            npc.interrupt();
            defender.riposteUntil=level.getGameTime()+28;
            return;
        }
        if(victim.hurt(npc.damageSources().mobAttack(npc),(float)move.power())) {
            npc.resolved(move.id());
            npc.getPersistentData().putLong("BioHumCloseHitAt",level.getGameTime());
            Payloads.cue(npc,move.id(),IMPACT,hit.at,hit.at,npc.characterId.equals("acqua")?1.4f:.74f);
            if(npc.characterId.equals("stiyl")&&npc.flameUntil>level.getGameTime())victim.igniteForSeconds(3);
        }
    }

    private static Weapon weapon(HumNpc npc,Skills.Skill move,float age) {
        var angles=CastMotion.aimed(npc,move,age);
        var r=reach(npc,move);
        var grip=CastMotion.modelToWorld(npc,CastMotion.armPoint(angles,npc.profile().slim(),true,9.4f,-.35f),npc.castYaw());
        float tipAlong=r==Reach.FIST?11.6f:9.4f-r.depth*.985f;
        float tipDepth=r==Reach.FIST?-.35f:-.35f+r.depth*.174f;
        var tip=CastMotion.modelToWorld(npc,CastMotion.armPoint(angles,npc.profile().slim(),true,tipAlong,tipDepth),npc.castYaw());
        return new Weapon(grip,tip);
    }

    private CloseCombat() {}
}
