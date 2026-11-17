package cn.academy.biohum;

import static cn.academy.biohum.Payloads.Cue.*;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashSet;
import java.util.UUID;

/** Authoritative conditions for the additional character-specific arts. */
final class CharacterArts {
    private static final String WING_SCREEN="BioHumWingScreenUntil";
    private static final String TRIP_TARGET="BioHumTripTarget";
    private static final String TRIP_UNTIL="BioHumTripUntil";
    private static final String CURSE_TARGET="BioHumKarmaTarget";
    private static final String CURSE_UNTIL="BioHumKarmaUntil";
    private static final String CURSE_SOURCE="BioHumKarmaSource";
    private static final String AIM_SHARED="BioHumAimShared";
    private static final String AIM_EXPIRES="BioHumAimExpires";

    static boolean threatenedBy(HumNpc npc, LivingEntity target) {
        if(target==null||!npc.validTarget(target))return false;
        return target instanceof Mob mob && mob.getTarget()==npc
                || npc.getLastHurtByMob()==target && npc.tickCount-npc.getLastHurtByMobTimestamp()<90;
    }

    static boolean marked(HumNpc npc, LivingEntity target) {
        var data=npc.getPersistentData();
        return target!=null&&npc.hasLineOfSight(target)&&data.hasUUID(TRIP_TARGET)&&data.getUUID(TRIP_TARGET).equals(target.getUUID())
                &&data.getLong(TRIP_UNTIL)>npc.level().getGameTime();
    }

    static boolean allows(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 aim) {
        var level=(ServerLevel)npc.level();
        boolean open=target!=null&&npc.validTarget(target)&&npc.hasLineOfSight(target);
        return switch(skill.id()){
            case "vector_airlift" -> npc.onGround()&&WorldEffects.inWorld(level,npc.blockPosition().above(3))
                    &&level.noCollision(npc,npc.getBoundingBox().expandTowards(0,1.65,0));
            case "vector_headwind","saint_intercept","aleister_spiritual_trip","aleister_spiritual_rapier" -> open;
            case "electro_magnetic_dash" -> magneticContact(npc,aim)!=null;
            case "iron_sand_lash" -> open&&npc.getOffhandItem().is(Items.IRON_NUGGET)&&npc.getOffhandItem().getCount()>=2;
            case "meltdowner_scatter" -> npc.getOffhandItem().is(Items.AMETHYST_SHARD)
                    &&npc.position().distanceToSqr(aim)>5.4*5.4;
            case "dark_feather_fan" -> open&&!npc.wings().isEmpty();
            case "dark_matter_screen" -> !npc.wings().isEmpty();
            case "rune_relay" -> runeSite(npc,aim)!=null&&npc.getOffhandItem().is(BioHum.RUNE_CARD.get())
                    &&npc.runes.stream().map(level::getEntity).anyMatch(e->e instanceof RuneAnchor r&&r.isAlive());
            case "fire_sever" -> open&&npc.getMainHandItem().is(BioHum.FLAME_SWORD.get())
                    &&npc.flameUntil>level.getGameTime();
            case "saint_water_guard" -> water(npc)!=null
                    &&npc.getPersistentData().getLong("BioHumMercySuppressed")<=level.getGameTime();
            case "holy_right_intercept" -> open&&threatenedBy(npc,target);
            case "bethlehem_relic_survey" -> npc.getOffhandItem().is(BioHum.RELIC.get());
            case "aleister_vitals" -> npc.suspended();
            case "aleister_interlock" -> npc.suspended()&&npc.chamberInstalled()&&threatenedBy(npc,target)
                    &&WindowlessBuilding.clearPort(npc,target);
            case "aleister_staff" -> open&&npc.getMainHandItem().is(BioHum.BLASTING_ROD.get());
            case "aleister_karma" -> open&&!npc.getPersistentData().hasUUID(CURSE_TARGET);
            case "vector_air_intercept","iron_sand_intercept" -> open
                    &&(!target.onGround()&&target.getY()>npc.getY()+1||approaching(npc,target)!=null)
                    &&(!skill.id().equals("iron_sand_intercept")
                        ||npc.getOffhandItem().is(Items.IRON_NUGGET)&&npc.getOffhandItem().getCount()>=2);
            case "iron_sand_deconstruct" -> open&&target instanceof Construct&&ironColossus(npc)!=null;
            case "aim_share" -> target!=null&&npc.getPersistentData().hasUUID("AimSubject")
                    &&npc.getPersistentData().getUUID("AimSubject").equals(target.getUUID())
                    &&npc.getPersistentData().getLong("BioHumAimTrackedUntil")>level.getGameTime()&&mugino(npc)!=null;
            case "meltdowner_aim_relay" -> open&&npc.getPersistentData().hasUUID(AIM_SHARED)
                    &&npc.getPersistentData().getUUID(AIM_SHARED).equals(target.getUUID())
                    &&npc.getPersistentData().getLong(AIM_EXPIRES)>level.getGameTime();
            case "material_counterform" -> npc.getHealth()<npc.getMaxHealth()*.85f
                    &&npc.getLastHurtByMob()!=null&&npc.tickCount-npc.getLastHurtByMobTimestamp()<100;
            case "holy_right_close" -> open&&threatenedBy(npc,target);
            case "curtana_interpose" -> open&&threatenedBy(npc,target)
                    &&WorldEffects.inside(level,npc.position(),WorldEffects.RegionKind.TERRITORY);
            case "amakusa_lance_cover" -> target instanceof HumNpc other&&isAmakusa(other)&&other!=npc
                    &&(other.getTarget()!=null||other.getLastHurtByMob()!=null);
            case "rune_fire_screen" -> open&&npc.flameUntil>level.getGameTime()
                    &&npc.getMainHandItem().is(BioHum.FLAME_SWORD.get())
                    &&npc.runes.stream().map(level::getEntity).anyMatch(e->e instanceof RuneAnchor r&&r.isAlive()&&r.distanceTo(target)<5);
            case "thunder_arc_parry" -> open;
            case "wonderland_corridor" -> WorldEffects.inside(level,aim,WorldEffects.RegionKind.WONDERLAND);
            case "ars_magna_mute" -> open&&target instanceof HumNpc other&&!other.casting().isEmpty()
                    &&npc.getOffhandItem().is(Items.GOLD_NUGGET)&&!npc.windDisrupted();
            default -> false;
        };
    }

    static boolean apply(HumNpc npc, Skills.Skill skill, LivingEntity target, Vec3 at, Vec3 origin, UUID issuer) {
        var level=(ServerLevel)npc.level();
        long now=level.getGameTime();
        int time=skill.duration();
        Vec3 direction=at.subtract(origin).normalize();
        Vec3 side=direction.cross(new Vec3(0,1,0)).normalize();
        var data=npc.getPersistentData();
        switch(skill.id()){
            case "vector_airlift" -> {
                npc.setDeltaMovement(npc.getLookAngle().multiply(.46,0,.46).add(0,1.27,0));
                npc.hasImpulse=true;
                npc.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,time,0,false,false),npc);
                for(int i=0;i<3;i++)cue(npc,skill,MOVE,npc.position(),npc.position().add(0,1.5+i*.8,0),1.15f);
                field(npc,skill,npc.position(),(float)skill.radius());
                return true;
            }
            case "vector_headwind" -> {
                var line=SkillTrajectory.beam(npc,skill,origin,at,issuer,false,null,true);
                for(var shot:level.getEntitiesOfClass(Projectile.class,new AABB(origin,line.end()).inflate(skill.radius()),
                        p->!p.onGround()&&(p.getOwner()==null||!npc.ally(p.getOwner()))
                                &&p.getDeltaMovement().lengthSqr()>.02)){
                    Vec3 projected=origin.add(direction.scale(Math.max(0,shot.position().subtract(origin).dot(direction))));
                    if(shot.distanceToSqr(projected)>skill.radius()*skill.radius()||
                            SkillTrajectory.surface(level,origin,shot.position()).block()!=null)continue;
                    Vec3 turn=shot.position().subtract(projected).add(direction.scale(.6)).normalize();
                    shot.setDeltaMovement(turn.scale(Math.max(.62,shot.getDeltaMovement().length())));
                    shot.hasImpulse=true;
                    cue(npc,skill,REFLECT,shot.position(),shot.position().add(turn.scale(2)),.32f);
                }
                if(target!=null&&line.hits()>0)CombatSkills.shove(target,npc.position(),1.23);
                return true;
            }
            case "electro_magnetic_dash" -> {
                var hit=magneticContact(npc,at);
                if(hit==null)return false;
                Vec3 travel=hit.position().subtract(npc.getEyePosition()).normalize();
                npc.setDeltaMovement(travel.scale(1.38).add(0,.29,0));
                npc.hasImpulse=true;
                npc.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,45,0,false,false),npc);
                cue(npc,skill,BEAM,origin,hit.position(),.35f);
                return true;
            }
            case "iron_sand_lash" -> {
                npc.getOffhandItem().shrink(2);
                for(int i=-1;i<=1;i++){
                    var sweep=origin.add(side.scale(i*.28)).add(0,i*.12,0);
                    SkillTrajectory.directed(npc,skill,sweep,at.add(side.scale(i*1.08)),skill.range(),
                            skill.power(),issuer,false,WorldEffects.Change.BREAK,true);
                }
                return true;
            }
            case "meltdowner_scatter" -> {
                Vec3 lens=origin.add(direction.scale(5.2));
                if(SkillTrajectory.surface(level,origin,lens).block()!=null
                        ||SkillTrajectory.loadedEnd(level,origin,lens).distanceToSqr(lens)>.003)return false;
                var feed=SkillTrajectory.directed(npc,skill,origin,lens,5.2,skill.power(),issuer,false,null,true);
                if(feed.end().distanceToSqr(lens)>.04)return false;
                npc.getOffhandItem().shrink(1);
                cue(npc,skill,IMPACT,lens,lens,1.46f);
                for(int i=-3;i<=3;i++){
                    Vec3 ray=direction.yRot(i*.082f).add(0,i==0?0:Math.sin(i*2.05)*.034,0);
                    SkillTrajectory.directed(npc,skill,lens,lens.add(ray),skill.range()-5,skill.power(),
                            issuer,true,WorldEffects.Change.SCORCH,true);
                }
                return true;
            }
            case "dark_feather_fan" -> {
                for(int wing:new int[]{-1,1})for(int i=0;i<3;i++){
                    Vec3 start=origin.add(side.scale(wing*(.58+i*.26))).add(0,.22+i*.19,0);
                    if(SkillTrajectory.surface(level,npc.getEyePosition(),start).block()!=null)continue;
                    SkillTrajectory.directed(npc,skill,start,at.add(side.scale(wing*i*.72)).add(0,i*.14,0),
                            skill.range(),skill.power(),issuer,false,null,true);
                }
                return true;
            }
            case "dark_matter_screen" -> {
                data.putLong(WING_SCREEN,now+time);
                npc.foldWingsUntil(now+time);
                field(npc,skill,npc.position(),(float)skill.radius());
                return true;
            }
            case "rune_relay" -> {
                var pos=runeSite(npc,at);
                if(pos==null)return false;
                var source=WorldEffects.Source.of(npc,issuer);
                if(WorldEffects.interactor(level,source,pos)==null)return false;
                var rune=BioHum.RUNE.get().create(level);
                rune.creator=npc.getUUID();
                rune.expires=now+time;
                rune.setPos(Vec3.atBottomCenterOf(pos));
                if(!level.addFreshEntity(rune))return false;
                npc.runes.removeIf(id->!(level.getEntity(id) instanceof RuneAnchor r)||!r.isAlive());
                npc.runes.add(rune.getUUID());
                level.getEntitiesOfClass(FlameFamiliar.class,npc.getBoundingBox().inflate(56),
                        familiar->npc.getUUID().equals(familiar.creator)).forEach(familiar->{
                    familiar.anchors.removeIf(id->!(level.getEntity(id) instanceof RuneAnchor r)||!r.isAlive());
                    familiar.anchors.add(rune.getUUID());
                });
                npc.getOffhandItem().shrink(1);
                field(npc,skill,rune.position(),(float)skill.radius());
                return true;
            }
            case "fire_sever" -> {
                for(int i=-1;i<=1;i++){
                    var ray=SkillTrajectory.directed(npc,skill,origin,at.add(side.scale(i*.8)),skill.range(),
                            skill.power(),issuer,false,null,true);
                    if(i==0&&ray.hits()>0&&target.getBoundingBox().inflate(.18).contains(ray.end()))
                        target.igniteForSeconds(3);
                }
                return true;
            }
            case "saint_intercept" -> {
                Vec3 start=npc.position();
                Vec3 toward=at.subtract(start).multiply(1,0,1).normalize();
                double length=Math.min(8,npc.distanceTo(target)-1.5);
                Vec3 landing=null;
                for(double step=.35;step<=length;step+=.35){
                    Vec3 p=start.add(toward.scale(step));
                    if(!WorldEffects.inWorld(level,BlockPos.containing(p))||
                            !level.noCollision(npc,npc.getBoundingBox().move(p.subtract(start))))break;
                    if(Abilities.safeDestination(npc,p))landing=p;
                }
                if(landing==null||!Abilities.teleport(npc,landing,false))return false;
                cue(npc,skill,MOVE,start.add(0,.24,0),landing.add(0,.24,0),1.1f);
                if(npc.distanceTo(target)<2.5&&npc.hasLineOfSight(target)
                        &&target.hurt(npc.damageSources().mobAttack(npc),(float)skill.power())){
                    cue(npc,skill,IMPACT,at,at,1);
                }
                return true;
            }
            case "saint_water_guard" -> {
                var source=water(npc);
                if(source==null)return false;
                npc.clearFire();
                npc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,time,1,false,false),npc);
                cue(npc,skill,BEAM,Vec3.atCenterOf(source),npc.getEyePosition(),.31f);
                field(npc,skill,npc.position(),(float)skill.radius());
                return true;
            }
            case "holy_right_intercept" -> {
                double power=skill.power()+Math.max(0,1-npc.getHealth()/npc.getMaxHealth())*9;
                var strike=SkillTrajectory.directed(npc,skill,origin,at,skill.range(),power,issuer,false,null,true);
                if(strike.hits()==0)return false;
                if(target.getBoundingBox().inflate(.18).contains(strike.end())){
                    if(target instanceof HumNpc other)other.interrupt();
                    CombatSkills.shove(target,npc.position(),.8);
                }
                return true;
            }
            case "bethlehem_relic_survey" -> {
                var player=CombatSkills.audience(npc,issuer);
                boolean sky=level.canSeeSky(npc.blockPosition().above());
                if(player!=null)npc.tell(player,"遗物已验明 · "+(sky?"天顶畅通":"上方有遮挡"));
                field(npc,skill,npc.position(),(float)skill.radius());
                return true;
            }
            case "aleister_vitals" -> {
                npc.chamberSignal(1,90);
                var player=CombatSkills.audience(npc,issuer);
                if(player!=null)npc.tell(player,"培养液循环正常；生命 "+Math.round(npc.getHealth())+" / "+
                        Math.round(npc.getMaxHealth())+(npc.chamberInstalled()?"；楼内联锁在线":"；离线展舱"));
                field(npc,skill,npc.position().add(0,.2,0),(float)skill.radius());
                return true;
            }
            case "aleister_interlock" -> {
                if(!WindowlessBuilding.clearPort(npc,target))return false;
                npc.chamberSignal(2,80);
                if(target instanceof HumNpc other)other.interrupt();
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,time,1),npc);
                npc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,time,1,false,false),npc);
                cue(npc,skill,BEAM,origin,at,.34f);
                field(npc,skill,target.position(),(float)skill.radius());
                return true;
            }
            case "aleister_spiritual_trip","aleister_spiritual_rapier" -> {
                if(blockedByRightHand(npc,target,origin,at))return false;
                data.putUUID(TRIP_TARGET,target.getUUID());data.putLong(TRIP_UNTIL,now+time);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,time,0,false,false),npc);
                if(target instanceof net.minecraft.server.level.ServerPlayer viewer)
                    Payloads.privateCue(npc,viewer,skill.id(),ILLUSION,target.getEyePosition());
                var point=target.getBoundingBox().getCenter();
                if(target.hurt(npc.damageSources().indirectMagic(npc,npc),(float)skill.power()))
                    cue(npc,skill,IMPACT,point,point,.68f);
                return true;
            }
            case "aleister_staff" -> {
                if(SkillTrajectory.surface(level,origin,at).block()!=null||blockedByRightHand(npc,target,origin,at))return false;
                boolean amplification=marked(npc,target);
                if(amplification){
                    data.remove(TRIP_TARGET);data.remove(TRIP_UNTIL);
                }
                boolean hurt=target.hurt(npc.damageSources().indirectMagic(npc,npc),(float)(skill.power()*(amplification?2.7:1)));
                if(hurt){
                    cue(npc,skill,IMPACT,at,at,amplification?1.42f:.78f);
                    if(amplification)field(npc,skill,target.position(),1.2f);
                }
                return hurt;
            }
            case "aleister_karma" -> {
                if(blockedByRightHand(npc,target,origin,at))return false;
                data.putUUID(CURSE_TARGET,target.getUUID());data.putLong(CURSE_UNTIL,now+time);
                target.getPersistentData().putUUID(CURSE_SOURCE,npc.getUUID());
                cue(npc,skill,BEAM,origin,at,.23f);
                return true;
            }
            case "vector_air_intercept","iron_sand_intercept" -> {
                boolean iron=skill.id().startsWith("iron_");
                var shot=approaching(npc,target);
                if(iron)npc.getOffhandItem().shrink(2);
                if(shot!=null){
                    Vec3 contact=shot.position();
                    if(iron)shot.discard();
                    else {shot.setDeltaMovement(shot.getDeltaMovement().scale(-.62).add(0,-.22,0));shot.hasImpulse=true;}
                    cue(npc,skill,BEAM,origin,contact,.22f);
                    cue(npc,skill,REFLECT,contact,contact.add(0,-1,0),.6f);
                    return true;
                }
                var line=SkillTrajectory.beam(npc,skill,origin,at,issuer,false,null,true);
                if(line.hits()==0)return false;
                var v=target.getDeltaMovement();
                target.setDeltaMovement(v.x*.44,Math.min(v.y,-.42),v.z*.44);target.hurtMarked=true;
                return true;
            }
            case "iron_sand_deconstruct" -> {
                var source=ironColossus(npc);
                if(source==null||SkillTrajectory.surface(level,source.getEyePosition(),at).block()!=null)return false;
                double before=target.getHealth();
                var hit=SkillTrajectory.directed(npc,skill,source.getEyePosition(),at,skill.range(),
                        skill.power(),issuer,false,null,true);
                if(hit.hits()==0)return false;
                if(target.getHealth()<before&&target.getHealth()<=0)npc.spawnAtLocation(new ItemStack(Items.IRON_NUGGET,3));
                return true;
            }
            case "aim_share" -> {
                var next=mugino(npc);
                if(next==null)return false;
                next.getPersistentData().putUUID(AIM_SHARED,target.getUUID());
                next.getPersistentData().putLong(AIM_EXPIRES,now+time);
                if(next.getTarget()==null||!next.validTarget(next.getTarget()))next.setTarget(target);
                cue(npc,skill,MOVE,origin,next.getEyePosition(),.36f);
                return true;
            }
            case "meltdowner_aim_relay" -> {
                data.remove(AIM_SHARED);data.remove(AIM_EXPIRES);
                return SkillTrajectory.beam(npc,skill,origin,at,issuer,false,WorldEffects.Change.SCORCH,true).hits()>0;
            }
            case "material_counterform" -> {
                npc.heal((float)skill.power());
                data.putLong("BioHumMaterialShell",now+time);
                var attacker=npc.getLastHurtByMob();
                if(attacker!=null&&npc.validTarget(attacker)&&npc.distanceTo(attacker)<skill.radius()
                        &&npc.hasLineOfSight(attacker))CombatSkills.shove(attacker,npc.position(),.67);
                field(npc,skill,npc.position(),(float)skill.radius());
                return true;
            }
            case "holy_right_close","thunder_arc_parry","rune_fire_screen","curtana_interpose" -> {
                Vec3 end=SkillTrajectory.surface(level,origin,at).position();
                if(end.distanceToSqr(at)>.1)return false;
                double radius=skill.radius();
                int stopped=0;
                for(var projectile:level.getEntitiesOfClass(Projectile.class,new AABB(origin,at).inflate(radius),
                        p->p.getOwner()!=npc&&(p.getOwner()==null||!npc.ally(p.getOwner()))&&!p.onGround())){
                    if(SkillTrajectory.surface(level,origin,projectile.position()).block()!=null)continue;
                    Vec3 p=projectile.position();
                    projectile.discard();
                    cue(npc,skill,PARRY,p,p,.7f);
                    stopped++;
                }
                boolean hurt=target.hurt(npc.damageSources().indirectMagic(npc,npc),(float)skill.power());
                if(hurt){
                    cue(npc,skill,IMPACT,at,at,.86f);
                    if(skill.id().equals("holy_right_close"))CombatSkills.shove(target,npc.position(),.91);
                    if(skill.id().equals("rune_fire_screen"))target.igniteForSeconds(3);
                }
                if(hurt||stopped>0)cue(npc,skill,BEAM,origin,at,.34f);
                return hurt||stopped>0;
            }
            case "amakusa_lance_cover" -> {
                LivingEntity threat=target instanceof Mob other&&other.getTarget()!=null?other.getTarget():target.getLastHurtByMob();
                if(!npc.validTarget(threat))return false;
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,time,0),npc);
                npc.setTarget(threat);
                cue(npc,skill,MOVE,origin,target.getEyePosition(),.41f);
                return true;
            }
            case "wonderland_corridor" -> {
                var move=at.subtract(npc.position()).multiply(1,0,1).normalize();
                int changed=0;
                for(int i=0;i<skill.radius();i++){
                    BlockPos pos=BlockPos.containing(at.add(move.scale(i)).add(0,-.6,0));
                    if(!WorldEffects.inWorld(level,pos)||!WorldEffects.inside(level,Vec3.atCenterOf(pos),WorldEffects.RegionKind.WONDERLAND))break;
                    if(level.getBlockState(pos).is(Blocks.AMETHYST_BLOCK))continue;
                    if(!level.getBlockState(pos).isAir())break;
                    if(WorldEffects.edit(level,WorldEffects.Source.of(npc,issuer),pos,WorldEffects.Change.FORM,3,time)){
                        Vec3 p=Vec3.atCenterOf(pos);
                        cue(npc,skill,TERRAIN,p,p,.86f);changed++;
                    }
                }
                return changed>0;
            }
            case "ars_magna_mute" -> {
                if(!(target instanceof HumNpc other)||!other.interrupt())return false;
                npc.getOffhandItem().shrink(1);
                cue(npc,skill,BEAM,origin,other.getEyePosition(),.26f);
                return true;
            }
            default -> {return false;}
        }
    }

    private static boolean blockedByRightHand(HumNpc npc,LivingEntity target,Vec3 from,Vec3 to) {
        if(!(target instanceof HumNpc touma)||!touma.characterId.equals("touma")
                ||touma.guardUntil<=npc.level().getGameTime())return false;
        Vec3 hand=touma.rightHand();
        if(new AABB(hand,hand).inflate(.46).clip(from,to).isEmpty())return false;
        Payloads.cue(touma,"imagine_breaker",CANCEL,hand,hand,.7f);
        return true;
    }

    static void tickAleister(HumNpc npc,ServerLevel level) {
        var data=npc.getPersistentData();
        if(data.hasUUID(TRIP_TARGET)&&level.getGameTime()%4==0
                &&(!(level.getEntity(data.getUUID(TRIP_TARGET)) instanceof LivingEntity target)||!marked(npc,target))){
            data.remove(TRIP_TARGET);data.remove(TRIP_UNTIL);
        }
        if(!data.hasUUID(CURSE_TARGET))return;
        var target=level.getEntity(data.getUUID(CURSE_TARGET));
        if(!(target instanceof LivingEntity living)||!npc.validTarget(living)
                ||!living.getPersistentData().hasUUID(CURSE_SOURCE)
                ||!living.getPersistentData().getUUID(CURSE_SOURCE).equals(npc.getUUID())
                ||level.getGameTime()>=data.getLong(CURSE_UNTIL)
                ||npc.distanceTo(living)>24||!npc.hasLineOfSight(living)){
            data.remove(CURSE_TARGET);data.remove(CURSE_UNTIL);
            if(target instanceof LivingEntity subject){
                var state=subject.getPersistentData();
                if(state.hasUUID(CURSE_SOURCE)&&state.getUUID(CURSE_SOURCE).equals(npc.getUUID()))clearCurse(subject);
            }
            return;
        }
        if((data.getLong(CURSE_UNTIL)-level.getGameTime())%20!=0)return;
        if(blockedByRightHand(npc,living,npc.getEyePosition(),living.getBoundingBox().getCenter())){
            clearCurse(living);
            return;
        }
        living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,23,0),npc);
        if(living.hurt(npc.damageSources().indirectMagic(npc,npc),3)){
            Vec3 at=living.getBoundingBox().getCenter();
            Payloads.cue(npc,"aleister_karma",IMPACT,at,at,.7f);
        }
    }

    static void clearCurse(LivingEntity victim){victim.getPersistentData().remove(CURSE_SOURCE);}

    private static boolean isAmakusa(HumNpc npc) {
        return npc.characterId.equals("kanzaki")||npc.characterId.equals("tatemiya")||npc.characterId.equals("itsuwa");
    }

    private static HumNpc mugino(HumNpc npc) {
        return npc.level().getEntitiesOfClass(HumNpc.class,npc.getBoundingBox().inflate(22),
                other->other.isAlive()&&other.characterId.equals("mugino")&&other.phaseId().equals(other.profile().phase())
                        &&(other.follower==null||other.ally(npc))&&other.getTarget()!=npc)
                .stream().min(java.util.Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
    }

    private static Construct ironColossus(HumNpc npc) {
        return npc.level().getEntitiesOfClass(Construct.class,npc.getBoundingBox().inflate(25),
                c->c.isAlive()&&c.iron()&&c.belongsTo(npc)).stream().findFirst().orElse(null);
    }

    private static Projectile approaching(HumNpc npc,LivingEntity enemy) {
        var level=(ServerLevel)npc.level();
        return level.getEntitiesOfClass(Projectile.class,npc.getBoundingBox().inflate(13),p->{
            if(p.onGround()||p.getOwner()!=enemy)return false;
            Vec3 v=p.getDeltaMovement();
            Vec3 path=p.position().add(v.scale(16));
            return v.lengthSqr()>.04&&npc.getBoundingBox().inflate(.7).clip(p.position(),path).isPresent()
                    &&SkillTrajectory.surface(level,npc.getEyePosition(),p.position()).block()==null;
        }).stream().min(java.util.Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
    }

    static boolean screenDamage(LivingIncomingDamageEvent event) {
        if(event.getEntity() instanceof HumNpc material&&!material.level().isClientSide
                &&material.phaseId().equals("nt06_material")
                &&material.getPersistentData().getLong("BioHumMaterialShell")>material.level().getGameTime()
                &&event.getSource().getEntity() instanceof LivingEntity attacker
                &&material.getLookAngle().dot(attacker.position().subtract(material.position()).normalize())>.32) {
            material.getPersistentData().remove("BioHumMaterialShell");
            event.setAmount(event.getAmount()*.42f);
            var point=material.getBoundingBox().getCenter();
            Payloads.cue(material,"material_counterform",PARRY,point,point,.75f);
            return true;
        }
        if(!(event.getEntity() instanceof HumNpc npc)||npc.level().isClientSide
                ||!npc.characterId.equals("kakine")||npc.wings().isEmpty()
                ||npc.getPersistentData().getLong(WING_SCREEN)<=npc.level().getGameTime()
                ||event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR))return false;
        Entity source=event.getSource().getDirectEntity();
        Entity attacker=event.getSource().getEntity();
        Vec3 from=attacker==null?null:attacker.position();
        if(!(source instanceof Projectile)&&(from==null||from.distanceToSqr(npc.position())<3.2*3.2))return false;
        if(from==null&&source instanceof Projectile p)from=p.position().subtract(p.getDeltaMovement().scale(3));
        if(from==null||npc.getLookAngle().multiply(1,0,1).normalize().dot(from.subtract(npc.position()).multiply(1,0,1).normalize())<.31)return false;
        if(attacker instanceof LivingEntity living&&!npc.validTarget(living))return false;
        event.setAmount(event.getAmount()*.30f);
        Vec3 at=npc.getEyePosition();
        cue(npc,Skills.get("dark_matter_screen"),IMPACT,at,at,1.10f);
        return true;
    }

    private static SkillTrajectory.Hit magneticContact(HumNpc npc,Vec3 aim) {
        var level=(ServerLevel)npc.level();
        var eye=npc.getEyePosition();
        if(eye.distanceToSqr(aim)<3.1*3.1)return null;
        var hit=SkillTrajectory.surface(level,eye,aim.add(aim.subtract(eye).normalize().scale(.15)));
        if(hit.block()==null)return null;
        var state=level.getBlockState(hit.block());
        return state.is(Blocks.IRON_BLOCK)||state.is(Blocks.RAW_IRON_BLOCK)||state.is(Blocks.IRON_BARS)
                ||state.is(Blocks.IRON_DOOR)||state.is(Blocks.IRON_TRAPDOOR)||state.is(Blocks.CHAIN)?hit:null;
    }

    private static BlockPos runeSite(HumNpc npc,Vec3 aim) {
        var world=(ServerLevel)npc.level();
        var floor=BlockPos.containing(CombatSkills.ground(world,aim));
        if(!WorldEffects.inWorld(world,floor)||!world.getBlockState(floor).isAir()
                ||!world.getBlockState(floor.below()).isSolid()
                ||!world.getEntitiesOfClass(RuneAnchor.class,new AABB(floor).inflate(.3)).isEmpty())return null;
        return SkillTrajectory.surface(world,npc.getEyePosition(),Vec3.atCenterOf(floor)).block()==null?floor:null;
    }

    private static BlockPos water(HumNpc npc) {
        var world=(ServerLevel)npc.level();
        for(BlockPos p:BlockPos.betweenClosed(npc.blockPosition().offset(-4,-2,-4),npc.blockPosition().offset(4,2,4))){
            if(WorldEffects.inWorld(world,p)&&world.getFluidState(p).is(FluidTags.WATER)
                    &&npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))<=30
                    &&SkillTrajectory.surface(world,npc.getEyePosition(),Vec3.atCenterOf(p)).block()==null)return p.immutable();
        }
        return null;
    }

    private static void field(HumNpc npc,Skills.Skill skill,Vec3 at,float size){cue(npc,skill,FIELD,at,at,size);}
    private static void cue(HumNpc npc,Skills.Skill skill,Payloads.Cue type,Vec3 from,Vec3 to,float size){
        Payloads.cue(npc,skill.id(),type,from,to,size);
    }

    private CharacterArts(){}
}
