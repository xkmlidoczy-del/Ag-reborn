package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.CanonEmission;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * An actual non-saving body, steered by the server-observed horizontal look direction.
 * Sneaking pauses movement. The owning client may view the server-authorized body.
 */
@EventBusSubscriber(modid="academy")
public final class CanonLiquidSkills {
    public static final Set<String> IMPLEMENTED=Set.of("liquid_scout","liquid_strike","liquid_shadow_field","liquid_pause","liquid_dismiss","liquid_follow","liquid_recall","liquid_path","liquid_switch","liquid_guard","liquid_push","liquid_pulse","liquid_listen","liquid_turn","liquid_survey","liquid_intercept","liquid_retreat","liquid_route","liquid_screen","liquid_dual_sensor");
    private static final String CATEGORY="liquid_shadow";
    private static final int MAX_CONTEXTS=32, MAX_FIELD_TARGETS=integer("liquid_shadow_field", "target_cap"), MAX_CANDIDATES=64;
    private static final double CONTROL_RANGE=number("liquid_scout", "control_reach");
    private static final int BODY_TICKS=integer("liquid_scout", "duration_ticks");
    private static final double STRIKE_RANGE=number("liquid_strike", "body_reach");
    private static final double FIELD_RANGE=number("liquid_shadow_field", "radius");
    private static final Map<UUID,Lease> ACTIVE=new HashMap<>();
    private static final Set<UUID> PAUSED=new java.util.HashSet<>();
    private enum Movement { AIM, FOLLOW, RECALL, RETREAT, ROUTE }
    private static final Map<UUID,Movement> MOVEMENT=new HashMap<>();
    private static final Map<UUID,Vec3> WAYPOINTS=new HashMap<>();
    private static final Map<UUID,Guard> GUARDS=new HashMap<>();
    private static final Map<UUID,SoundCue> SOUNDS=new HashMap<>();
    private record SoundCue(Lease lease,long tick,Vec3 source) {}
    private record Guard(Lease lease,long expires) {}
    private record Lease(ServerPlayer owner,ServerLevel world,CanonLiquidEntities.Shadow body,long expires) {}
    private CanonLiquidSkills() {}

    public static boolean cast(ServerPlayer p,String id) {
        if(id==null || !IMPLEMENTED.contains(id) || !ready(p,id))return false;
        if(id.equals("liquid_scout"))return spawn(p);
        Lease lease=ACTIVE.get(p.getUUID()); if(lease==null || !valid(lease))return false;
        if(id.equals("liquid_listen"))return listen(p,lease,id);
        if(id.equals("liquid_guard"))return guard(p,lease,id);
        if(id.equals("liquid_push")||id.equals("liquid_pulse"))return impulse(p,lease,id);
        if(id.equals("liquid_path"))return touchPath(p,lease,id);
        if(id.equals("liquid_switch"))return touchSwitch(p,lease,id);
        if(id.equals("liquid_turn"))return turnBody(p,lease,id);
        if(id.equals("liquid_survey"))return touchSurvey(p,lease,id);
        if(id.equals("liquid_intercept"))return intercept(p,lease,id);
        if(id.equals("liquid_route"))return route(p,lease,id);
        if(id.equals("liquid_screen"))return screen(p,lease,id);
        if(id.equals("liquid_dual_sensor"))return dualSensor(p,lease,id);
        if(id.equals("liquid_retreat")) {
            if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.25,10)||!valid(lease)||!readyAfterPayment(p,id))return false;
            PAUSED.remove(p.getUUID());WAYPOINTS.remove(p.getUUID());
            MOVEMENT.put(p.getUUID(),MOVEMENT.getOrDefault(p.getUUID(),Movement.AIM)==Movement.RETREAT?Movement.AIM:Movement.RETREAT);
            CanonVfx.emit(p,id,lease.body.position(),lease.body.position(),CanonVfx.Phase.START,10);return true;
        }
        if(id.equals("liquid_pause") || id.equals("liquid_dismiss") || id.equals("liquid_follow") || id.equals("liquid_recall")) {
            if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.25,10)||!valid(lease))return false;
            Vec3 endpoint=lease.body.position();
            if(id.equals("liquid_pause")) {if(!PAUSED.add(p.getUUID()))PAUSED.remove(p.getUUID());}
            else if(id.equals("liquid_follow")) {
                MOVEMENT.put(p.getUUID(),MOVEMENT.getOrDefault(p.getUUID(),Movement.AIM)==Movement.FOLLOW?Movement.AIM:Movement.FOLLOW);
            } else if(id.equals("liquid_recall")) {PAUSED.remove(p.getUUID());MOVEMENT.put(p.getUUID(),Movement.RECALL);}
            else finish(lease);
            CanonVfx.emit(p,id,p.position(),endpoint,id.equals("liquid_dismiss")?CanonVfx.Phase.END:CanonVfx.Phase.START,12);
            return true;
        }
        List<LivingEntity> targets=id.equals("liquid_strike")?target(p,lease):targets(p,lease);
        if(targets.isEmpty())return false;
        var growth=HighPowerGrowth.values(p);
        double cost=growth.arcCost()*(id.equals("liquid_strike")?2:4);
        int cooldown=id.equals("liquid_strike")?40:100;
        double range=id.equals("liquid_strike")?STRIKE_RANGE:FIELD_RANGE;
        var paid=CanonEmission.begin(p,id,cost,cooldown,targets,target -> valid(lease)
                && eligible(p,lease,target,range) && visible(lease,target)
                && (!id.equals("liquid_strike") || target(p,lease).contains(target))); if(paid==null)return false;
        boolean hit=false,announced=false;
        for(LivingEntity target:targets) {
            if(!valid(lease) || !eligible(p,lease,target,range) || !visible(lease,target))continue;
            boolean accepted=paid.attack(target);hit |= accepted;
            if(accepted)CanonVfx.emit(p,id,lease.body.position(),target.position(),CanonVfx.Phase.HIT,12,!announced&&id.equals("liquid_shadow_field")?FIELD_RANGE:0);
            announced|=accepted;
            inkLine(lease.world,lease.body.getBoundingBox().getCenter(),target.getBoundingBox().getCenter());
        }
        if(hit)award(p,id);return true;
    }

    /** Single burst intercepting up to three real hostile arrows near the body. */
    private static boolean screen(ServerPlayer p,Lease lease,String id) {
        var candidates=lease.world.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,
                lease.body.getBoundingBox().inflate(APPROACH_REACH),a->approaching(p,lease.body,a,id));
        if(candidates.isEmpty()||candidates.size()>64)return false;
        candidates.sort(Comparator.comparingDouble(a->a.distanceToSqr(lease.body)));
        var arrows=List.copyOf(candidates.subList(0,Math.min(integer(id, "target_cap"),candidates.size())));var velocities=new ArrayList<Vec3>();
        for(var arrow:arrows)velocities.add(arrow.getDeltaMovement());
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*2,80)||!valid(lease)||!readyAfterPayment(p,id))return false;
        boolean deflected=false;
        for(int i=0;i<arrows.size();i++) {var arrow=arrows.get(i);Vec3 before=velocities.get(i);
            if(!valid(lease)||!readyAfterPayment(p,id)||!incoming(p,lease.body,arrow,id)||!before.equals(arrow.getDeltaMovement()))continue;
            if(!HighPowerCombat.spendShieldFraction(p,new java.math.BigDecimal("0.05")))break;
            if(!valid(lease)||!readyAfterPayment(p,id)||!incoming(p,lease.body,arrow,id)||!before.equals(arrow.getDeltaMovement()))continue;
            arrow.setDeltaMovement(before.scale(-.6));arrow.hasImpulse=true;arrow.hurtMarked=true;deflected=true;
            CanonVfx.emit(p,id,lease.body.position(),arrow.position(),CanonVfx.Phase.HIT,12);
        }
        return deflected;
    }
    /** Report both a recent real sound and physical creature contact, without sight. */
    private static boolean dualSensor(ServerPlayer p,Lease lease,String id) {
        var box=lease.body.getBoundingBox().inflate(number(id, "contact_margin"));Vec3 before=lease.body.position();
        if(!chunksLoaded(lease.world,new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ)))return false;
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.5,30)
                ||!valid(lease)||!readyAfterPayment(p,id)||!lease.body.position().equals(before))return false;
        var touched=lease.world.getEntitiesOfClass(LivingEntity.class,box,e->e!=lease.body&&e!=p&&e.isAlive()&&!e.isRemoved());
        if(touched.size()>64)return false;SoundCue cue=SOUNDS.get(p.getUUID());long now=lease.world.getGameTime();
        boolean heard=cue!=null&&cue.lease==lease&&now>=cue.tick&&now-cue.tick<=integer(id, "lookback_ticks");
        String key=(touched.isEmpty()?"clear":"contact")+(heard?"_sound":"_quiet");
        String fallback=switch(key){case "contact_sound"->"液影触觉接触生物；听觉有声音。";case "contact_quiet"->"液影触觉接触生物；听觉安静。";
            case "clear_sound"->"液影未接触生物；听觉有声音。";default->"液影未接触生物；听觉安静。";};
        p.displayClientMessage(Component.translatableWithFallback("ac.ability.liquid_shadow.sensor_"+key,fallback),true);
        for(var e:touched)cling(p,e,id);
        CanonVfx.emit(p,id,before,before,CanonVfx.Phase.START,10);return true;
    }

    /** Walk to a player-selected, visible floor position; path is never teleported. */
    private static boolean route(ServerPlayer p,Lease lease,String id) {
        Vec3 eye=p.getEyePosition(),direction=ClassicRaytrace.direction(p),end=eye.add(direction.scale(number(id, "reach")));
        if(!finite(eye)||!finite(direction)||!chunksLoaded(lease.world,eye,end))return false;
        var hit=lease.world.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)return false;
        Vec3 destination=Vec3.atBottomCenterOf(hit.getBlockPos().above());
        if(Math.abs(destination.y-lease.body.getY())>.25||destination.distanceToSqr(p.position())>CONTROL_RANGE*CONTROL_RANGE
                ||!ground(lease.world,destination)||!lease.world.mayInteract(p,hit.getBlockPos())
                ||!lease.world.mayInteract(p,BlockPos.containing(destination))||!lease.world.noCollision(lease.body,lease.body.getBoundingBox().move(destination.subtract(lease.body.position()))))return false;
        Vec3 bodyStart=lease.body.position();
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost(),40)||!valid(lease)||!readyAfterPayment(p,id)
                ||!lease.body.position().equals(bodyStart)||!p.getEyePosition().equals(eye)||!ClassicRaytrace.direction(p).equals(direction)
                ||!lease.world.mayInteract(p,hit.getBlockPos())||!ground(lease.world,destination))return false;
        var checked=lease.world.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(checked.getType()!=HitResult.Type.BLOCK||!checked.getBlockPos().equals(hit.getBlockPos())||checked.getDirection()!=Direction.UP)return false;
        WAYPOINTS.put(p.getUUID(),destination);MOVEMENT.put(p.getUUID(),Movement.ROUTE);PAUSED.remove(p.getUUID());
        CanonVfx.emit(p,id,bodyStart,destination,CanonVfx.Phase.START,20);return true;
    }

    /** Rotate the real body in place, including while paused; no remote sight is implied. */
    private static boolean turnBody(ServerPlayer p,Lease lease,String id) {
        float heading=p.getYHeadRot();Vec3 before=lease.body.position();
        if(!Float.isFinite(heading)||!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.25,10)
                ||!valid(lease)||!readyAfterPayment(p,id)||!lease.body.position().equals(before)||p.getYHeadRot()!=heading)return false;
        lease.body.setYRot(heading);lease.body.setYHeadRot(heading);lease.body.setYBodyRot(heading);
        CanonVfx.emit(p,id,before,before,CanonVfx.Phase.START,10);return true;
    }
    /** Contact-only awareness; no identities or detection through walls. */
    /** Ink clings to what the shadow touches: outlined for a tuned period and slowed for 2 s. Players are only outlined. */
    private static void cling(ServerPlayer p,LivingEntity e,String id){
        e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,integer(id, "duration_ticks"),0,false,false),p);
        if(!(e instanceof net.minecraft.world.entity.player.Player))e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,40,1,false,true),p);
    }
    private static boolean touchSurvey(ServerPlayer p,Lease lease,String id) {
        Vec3 before=lease.body.position();var box=lease.body.getBoundingBox().inflate(number(id, "contact_margin"));
        if(!chunksLoaded(lease.world,new Vec3(box.minX,box.minY,box.minZ),new Vec3(box.maxX,box.maxY,box.maxZ)))return false;
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.25,20)
                ||!valid(lease)||!readyAfterPayment(p,id)||!lease.body.position().equals(before))return false;
        var touched=lease.world.getEntitiesOfClass(LivingEntity.class,box,e->e!=lease.body&&e!=p&&e.isAlive()&&!e.isRemoved());
        if(touched.size()>64)return false;
        p.displayClientMessage(Component.translatableWithFallback(touched.isEmpty()?"ac.ability.liquid_shadow.touch_none":"ac.ability.liquid_shadow.touch_contact",
                touched.isEmpty()?"液影触觉：没有接触生物。":"液影触觉：接触到生物。"),true);
        for(var e:touched)cling(p,e,id);
        CanonVfx.emit(p,id,before,before,CanonVfx.Phase.START,10);return true;
    }
    /** Immediate manual deflection of one actual incoming hostile arrow. */
    private static boolean intercept(ServerPlayer p,Lease lease,String id) {
        var arrows=lease.world.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,
                lease.body.getBoundingBox().inflate(APPROACH_REACH),a->approaching(p,lease.body,a,id));
        if(arrows.isEmpty()||arrows.size()>64)return false;
        arrows.sort(Comparator.comparingDouble(a->a.distanceToSqr(lease.body)));var arrow=arrows.getFirst();Vec3 before=arrow.getDeltaMovement();
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost(),40)||!valid(lease)||!readyAfterPayment(p,id)
                ||!approaching(p,lease.body,arrow,id)||!arrow.getDeltaMovement().equals(before)
                ||!HighPowerCombat.spendShieldFraction(p,new java.math.BigDecimal("0.05")))return false;
        if(!valid(lease)||!readyAfterPayment(p,id)||!approaching(p,lease.body,arrow,id)||!arrow.getDeltaMovement().equals(before))return false;
        arrow.setDeltaMovement(before.scale(-.6));arrow.hasImpulse=true;arrow.hurtMarked=true;
        CanonVfx.emit(p,id,lease.body.position(),arrow.position(),CanonVfx.Phase.HIT,12);return true;
    }

    /** Report only a recent real server sound, without identities or visual information. */
    private static boolean listen(ServerPlayer p,Lease lease,String id) {
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.25,20)||!valid(lease))return false;
        SoundCue cue=SOUNDS.get(p.getUUID());String suffix="quiet";
        if(cue!=null&&cue.lease==lease&&lease.world.getGameTime()-cue.tick<=integer(id, "lookback_ticks")&&lease.world.getGameTime()>=cue.tick){
            Vec3 delta=cue.source.subtract(lease.body.position()),look=ClassicRaytrace.direction(p);
            Vec3 forward=new Vec3(look.x,0,look.z);if(forward.lengthSqr()>1e-8&&finite(delta)){
                forward=forward.normalize();double ahead=delta.x*forward.x+delta.z*forward.z;
                double side=delta.x*(-forward.z)+delta.z*forward.x;
                suffix=Math.abs(ahead)>=Math.abs(side)?(ahead>=0?"front":"back"):(side>=0?"right":"left");
            }
        }
        String fallback=switch(suffix){case "front"->"液影听觉：前方有声音。";case "back"->"液影听觉：后方有声音。";
            case "left"->"液影听觉：左侧有声音。";case "right"->"液影听觉：右侧有声音。";default->"液影听觉：近期没有声音。";};
        p.displayClientMessage(Component.translatableWithFallback("ac.ability.liquid_shadow.heard_"+suffix,fallback),true);
        if(!suffix.equals("quiet")){   // the shadow follows the sound to its maker: the nearest creature at the source is marked
            var maker=lease.world.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(cue.source,cue.source).inflate(2.5),e->e!=p&&e!=lease.body&&e.isAlive())
                    .stream().min(java.util.Comparator.comparingDouble(e->e.distanceToSqr(cue.source))).orElse(null);
            if(maker!=null)maker.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,120,0,false,false),p);
        }
        CanonVfx.emit(p,id,lease.body.position(),lease.body.position(),CanonVfx.Phase.START,12);return true;
    }
    private static void heard(net.neoforged.neoforge.event.PlayLevelSoundEvent event,Vec3 pos) {
        if(event.isCanceled()||event.getSound()==null||!(event.getLevel() instanceof ServerLevel world)
                ||!world.getServer().isSameThread()||!finite(pos)||!Float.isFinite(event.getNewVolume())||event.getNewVolume()<=0)return;
        double radius=Math.min(number("liquid_listen", "radius"),Math.max(0,event.getNewVolume()*16));long now=world.getGameTime();
        for(Lease lease:List.copyOf(ACTIVE.values()))if(lease.world==world
                &&lease.body.position().distanceToSqr(pos)<=radius*radius&&valid(lease))SOUNDS.put(lease.owner.getUUID(),new SoundCue(lease,now,pos));
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void soundAtPosition(net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition e){heard(e,e.getPosition());}
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void soundAtEntity(net.neoforged.neoforge.event.PlayLevelSoundEvent.AtEntity e){heard(e,e.getEntity().position());}

    /** One actual hostile native arrow can be deflected by the body, at exact shield cost. */
    private static boolean guard(ServerPlayer p,Lease lease,String id) {
        long now=lease.world.getGameTime();
        int ticks=integer(id, "duration_ticks");
        if(GUARDS.containsKey(p.getUUID())||GUARDS.size()>=32||now>Long.MAX_VALUE-ticks)return false;
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost(),80)||!valid(lease))return false;
        GUARDS.put(p.getUUID(),new Guard(lease,now+ticks));
        CanonVfx.emit(p,id,lease.body.position(),lease.body.position(),CanonVfx.Phase.START,30);return true;
    }
    /** Non-damaging impulse originates at the dense body's actual position. */
    private static boolean impulse(ServerPlayer p,Lease lease,String id) {
        var selected=new ArrayList<LivingEntity>();
        double range=number(id,id.equals("liquid_push")?"body_reach":"radius");
        if(id.equals("liquid_push")) {
            Vec3 eye=p.getEyePosition(),direction=ClassicRaytrace.direction(p);
            if(!finite(eye)||!finite(direction)||!chunksLoaded(lease.world,eye,eye.add(direction.scale(number(id, "reach")))))return false;
            var hit=ClassicRaytrace.perform(p,eye,eye.add(direction.scale(number(id, "reach"))),ClipContext.Fluid.NONE,e->e!=lease.body);
            if(!(hit instanceof EntityHitResult entityHit)||!(entityHit.getEntity() instanceof LivingEntity target)
                    ||!eligible(p,lease,target,range)||!visible(lease,target))return false;
            selected.add(target);
        } else {
            var candidates=lease.world.getEntitiesOfClass(Mob.class,lease.body.getBoundingBox().inflate(range),m->eligible(p,lease,m,range)&&visible(lease,m));
            if(candidates.size()>64)return false;candidates.sort(Comparator.comparing(Mob::getUUID));
            for(Mob mob:candidates){if(selected.size()==integer(id, "target_cap"))break;selected.add(mob);}
        }
        if(selected.isEmpty())return false;
        var before=new ArrayList<Vec3>();for(var target:selected)before.add(target.getDeltaMovement());
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*(id.equals("liquid_pulse")?2:1),40))return false;
        boolean moved=false;
        for(int i=0;i<selected.size();i++) {
            LivingEntity target=selected.get(i);
            if(!valid(lease)||!eligible(p,lease,target,range)||!visible(lease,target)||!before.get(i).equals(target.getDeltaMovement()))continue;
            Vec3 away=target.position().subtract(lease.body.position());Vec3 flat=new Vec3(away.x,0,away.z);
            if(flat.lengthSqr()<1e-8)continue;Vec3 push=flat.normalize().scale(number(id, "horizontal_speed")).add(0,.08,0);
            Vec3 end=target.position().add(push);
            if(!finite(end)||!chunksLoaded(lease.world,target.position(),end)||!lease.world.mayInteract(p,BlockPos.containing(end))
                    ||!lease.world.noCollision(target,target.getBoundingBox().move(push)))continue;
            CanonVfx.emit(p,id,lease.body.position(),target.position(),CanonVfx.Phase.HIT,12,!moved&&id.equals("liquid_pulse")?range:0);
            target.setDeltaMovement(push);target.hasImpulse=true;target.hurtMarked=true;moved=true;
        }
        if(moved)award(p,id);return true;
    }
    private static final double APPROACH_REACH=16;private static final int APPROACH_TICKS=10;
    /**
     * Intercept/Screen: an arrow inside the 2-block shell, or one whose straight path reaches it within half a second.
     * The shell alone left a window of about two ticks per arrow, which no player press could reliably hit.
     */
    private static boolean approaching(ServerPlayer owner,CanonLiquidEntities.Shadow body,net.minecraft.world.entity.projectile.AbstractArrow arrow,String id) {
        if(!hostileArrow(owner,arrow))return false;
        double radius=number(id, "radius");
        Vec3 radial=arrow.position().subtract(body.getBoundingBox().getCenter()),velocity=arrow.getDeltaMovement();
        if(!finite(radial)||!finite(velocity)||radial.lengthSqr()<=1e-8||velocity.lengthSqr()<.001||velocity.dot(radial)>=0
                ||radial.lengthSqr()>APPROACH_REACH*APPROACH_REACH)return false;
        if(radial.lengthSqr()<=radius*radius)return true;
        double t=Math.min(APPROACH_TICKS,-radial.dot(velocity)/velocity.lengthSqr());
        return radial.add(velocity.scale(t)).lengthSqr()<=radius*radius;
    }
    private static boolean incoming(ServerPlayer owner,CanonLiquidEntities.Shadow body,net.minecraft.world.entity.projectile.AbstractArrow arrow,String id) {
        if(!hostileArrow(owner,arrow))return false;
        double radius=number(id, "radius");
        Vec3 radial=arrow.position().subtract(body.getBoundingBox().getCenter()),velocity=arrow.getDeltaMovement();
        return finite(radial)&&finite(velocity)&&radial.lengthSqr()>1e-8&&radial.lengthSqr()<=radius*radius
                &&velocity.lengthSqr()>=.001&&velocity.dot(radial)<0;
    }
    /** A live, unclaimed arrow shot by a hostile mob that is not on the owner's side. */
    private static boolean hostileArrow(ServerPlayer owner,net.minecraft.world.entity.projectile.AbstractArrow arrow) {
        if(arrow.getType()!=EntityType.ARROW||arrow.inGround||arrow.isRemoved()||!arrow.isAlive()
                ||!(arrow.getOwner() instanceof Mob shooter)||!(shooter instanceof Enemy)||shooter.isAlliedTo(owner)||owner.isAlliedTo(shooter)
                ||cn.academy.port.highpower.ElectromasterPrototype.leased(arrow)||cn.academy.port.highpower.MultiAxisReflectionArrowBridge.leased(arrow)
                ||cn.academy.port.highpower.TrajectoryReturnArrowBridge.leased(arrow)||cn.academy.port.highpower.ShortInterceptionArrowBridge.leased(arrow)
                ||cn.academy.port.highpower.IronSandLance.leased(arrow)||cn.academy.port.highpower.MagneticArrowSeparation.leased(arrow)
                ||cn.academy.port.highpower.MagneticDomainArrows.leased(arrow)||cn.academy.port.highpower.MagneticGroundStep.leased(arrow)
                ||cn.academy.port.highpower.ElectromasterChargeDecoy.leased(arrow))return false;
        return true;
    }

    /** Touch a one-block movement corridor; no remote image or entity vision is supplied. */
    private static boolean touchPath(ServerPlayer p,Lease lease,String id) {
        Vec3 direction=ClassicRaytrace.direction(p);Vec3 horizontal=new Vec3(direction.x,0,direction.z);
        if(!finite(direction)||horizontal.lengthSqr()<1e-8)return false;
        Vec3 next=lease.body.position().add(horizontal.normalize().scale(number(id, "body_reach")));
        if(!chunksLoaded(lease.world,lease.body.position(),next)||!lease.world.mayInteract(p,BlockPos.containing(next)))return false;
        Vec3 start=lease.body.position();
        boolean blocked=!ground(lease.world,next)||!lease.world.noCollision(lease.body,lease.body.getBoundingBox().move(next.subtract(start)));
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.25,20)||!valid(lease))return false;
        if(!readyAfterPayment(p,id)||!lease.body.position().equals(start)||!ClassicRaytrace.direction(p).equals(direction)
                ||!chunksLoaded(lease.world,start,next)||!lease.world.mayInteract(p,BlockPos.containing(next))
                ||blocked!=(!ground(lease.world,next)||!lease.world.noCollision(lease.body,lease.body.getBoundingBox().move(next.subtract(start)))))return false;
        p.displayClientMessage(Component.translatableWithFallback(blocked?"ac.ability.liquid_shadow.touch_blocked":"ac.ability.liquid_shadow.touch_clear",
                blocked?"液影触觉：前方受阻。":"液影触觉：前方可通行。"),true);
        CanonVfx.emit(p,id,lease.body.position(),next,CanonVfx.Phase.START,10);return true;
    }
    /** The body can touch one nearby vanilla lever; the owner must also have native reach. */
    private static boolean touchSwitch(ServerPlayer p,Lease lease,String id) {
        Vec3 from=lease.body.getEyePosition(),direction=ClassicRaytrace.direction(p),to=from.add(direction.scale(number(id, "body_reach")));
        if(!finite(from)||!finite(direction)||!chunksLoaded(lease.world,from,to))return false;
        var hit=lease.world.clip(new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,lease.body));
        BlockPos pos=hit.getBlockPos();var world=lease.world;var old=world.getBlockState(pos);
        if(hit.getType()!=HitResult.Type.BLOCK||!old.is(net.minecraft.world.level.block.Blocks.LEVER)
                ||world.getBlockEntity(pos)!=null||!world.mayInteract(p,pos)||!p.canInteractWithBlock(pos,1.0))return false;
        var ability=AbilityStorage.get(p);long revision=ability.presets.revision();int preset=ability.presets.current();
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.5,20)||!switchStillValid(p,lease,from,direction,hit,old,id,ability,revision,preset))return false;
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pos,hit);
        if(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled()||event.getUseBlock().isFalse()
                ||!switchStillValid(p,lease,from,direction,hit,old,id,ability,revision,preset))return false;
        var result=old.useWithoutItem(world,p,hit);
        if(!result.consumesAction()||world.getBlockState(pos).equals(old))return false;
        CanonVfx.emit(p,id,from,pos.getCenter(),CanonVfx.Phase.HIT,12);return true;
    }
    private static boolean switchStillValid(ServerPlayer p,Lease lease,Vec3 from,Vec3 direction,
            net.minecraft.world.phys.BlockHitResult hit,net.minecraft.world.level.block.state.BlockState old,String id,
            cn.academy.port.core.AbilityProgress ability,long revision,int preset) {
        var world=lease.world;var pos=hit.getBlockPos();Vec3 to=from.add(direction.scale(number(id, "body_reach")));
        if(!valid(lease)||!readyAfterPayment(p,id)||AbilityStorage.get(p)!=ability||ability.presets.revision()!=revision||ability.presets.current()!=preset||!lease.body.getEyePosition().equals(from)
                ||!ClassicRaytrace.direction(p).equals(direction)||!chunksLoaded(world,from,to)
                ||!world.hasChunkAt(pos)||!world.getBlockState(pos).equals(old)||world.getBlockEntity(pos)!=null
                ||!world.mayInteract(p,pos)||!p.canInteractWithBlock(pos,1.0))return false;
        var fresh=world.clip(new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,lease.body));
        return fresh.getType()==HitResult.Type.BLOCK&&fresh.getBlockPos().equals(pos)&&fresh.getDirection()==hit.getDirection();
    }

    private static boolean spawn(ServerPlayer p) {
        if(ACTIVE.containsKey(p.getUUID()) || ACTIVE.size()>=MAX_CONTEXTS || !p.onGround())return false;
        ItemStack held=p.getMainHandItem().is(CanonLiquidEntities.DENSE_MEDIUM.get())?p.getMainHandItem():p.getOffhandItem();
        if(!held.is(CanonLiquidEntities.DENSE_MEDIUM.get()) || held.getCount()<1)return false;
        Vec3 look=ClassicRaytrace.direction(p), at=p.position(); if(!finite(look)||!finite(at))return false;
        Vec3 direction=new Vec3(look.x,0,look.z);if(direction.lengthSqr()<1e-8)return false;
        Vec3 start=at.add(direction.normalize().scale(2.2)); var world=p.serverLevel();
        if(!chunksLoaded(world,at,start) || !ground(world,start) || !world.mayInteract(p,BlockPos.containing(start)))return false;
        var body=CanonLiquidEntities.SHADOW.get().create(world); if(body==null)return false;
        body.setPos(start); body.setYRot(p.getYHeadRot()); body.setYHeadRot(p.getYHeadRot());
        if(!world.noCollision(body,body.getBoundingBox())){body.discard();return false;}
        long now=world.getGameTime();if(now<0||now>Long.MAX_VALUE-BODY_TICKS){body.discard();return false;}
        var ability=AbilityStorage.get(p);long revision=ability.presets.revision();int preset=ability.presets.current();
        ItemStack expected=held.copy();
        // Optional installed observation device; the liquid body itself has touch/hearing, not sight.
        ItemStack optic=p.getMainHandItem()==held?p.getOffhandItem():p.getMainHandItem();
        boolean module=optic.is(CanonLiquidEntities.OBSERVATION_MODULE.get());ItemStack expectedOptic=optic.copy();
        if(!HighPowerResources.trySpend(p,"liquid_scout",HighPowerGrowth.values(p).arcCost()*3,80)){body.discard();return false;}
        if(!materialUnchanged(p,held,expected)||module&&!materialUnchanged(p,optic,expectedOptic)
                ||!world.addFreshEntity(body)){body.discard();return false;}
        // Addition can invoke external handlers: revalidate identity and material before committing.
        if(!materialUnchanged(p,held,expected) || module&&!materialUnchanged(p,optic,expectedOptic)
                || !readyAfterPayment(p,"liquid_scout") || AbilityStorage.get(p)!=ability
                || ability.presets.revision()!=revision || ability.presets.current()!=preset
                || p.serverLevel()!=world || p.server.getPlayerList().getPlayer(p.getUUID())!=p
                || !p.position().equals(at) || world.getGameTime()!=now || ACTIVE.containsKey(p.getUUID())
                || body.isRemoved() || body.level()!=world || !body.position().equals(start)
                || !chunksLoaded(world,at,start) || !ground(world,start) || !world.mayInteract(p,BlockPos.containing(start))
                || world.getEntity(body.getUUID())!=body || !world.noCollision(body,body.getBoundingBox())){
            body.discard();return false;
        }
        if(!p.getAbilities().instabuild){held.shrink(1);if(module)optic.shrink(1);p.getInventory().setChanged();}
        body.observationModule(module);
        ACTIVE.put(p.getUUID(),new Lease(p,world,body,now+BODY_TICKS));
        body.controlOwner(p.getUUID());
        world.sendParticles(ParticleTypes.SQUID_INK,start.x,start.y+1,start.z,18,.3,.4,.3,0);
        CanonVfx.emit(p,"liquid_scout",p.position(),body.position(),CanonVfx.Phase.START,20);
        award(p,"liquid_scout");return true;
    }

    private static boolean ready(ServerPlayer p,String id) {
        if(p==null||p.connection==null||!p.server.isSameThread()||p.hasDisconnected()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||p.isRemoved()||!p.isAlive()||p.isSpectator()
                ||AbilityConsumption.busy(p)||HighPowerCombat.unsupportedReason(p).isPresent()||!HighPowerResources.ready(p))return false;
        var state=AbilityStorage.get(p);int required=id.equals("liquid_scout")||id.equals("liquid_dismiss")||id.equals("liquid_recall")?1:
                id.equals("liquid_pause")||id.equals("liquid_path")||id.equals("liquid_guard")||id.equals("liquid_listen")||id.equals("liquid_turn")||id.equals("liquid_retreat")?2:
                id.equals("liquid_strike")||id.equals("liquid_switch")||id.equals("liquid_push")||id.equals("liquid_intercept")?3:
                id.equals("liquid_follow")||id.equals("liquid_pulse")||id.equals("liquid_survey")||id.equals("liquid_route")||id.equals("liquid_screen")?4:5;
        return CATEGORY.equals(state.category)&&state.level>=required&&state.canUse(id)
                &&state.presets.currentContains(id)&&SkillCatalog.enabled(CATEGORY,id);
    }
    private static boolean readyAfterPayment(ServerPlayer p,String id) {
        var state=AbilityStorage.get(p);
        return p.isAlive()&&!p.isRemoved()&&!p.hasDisconnected()&&CATEGORY.equals(state.category)
                &&state.activated&&state.overloadFine&&!state.interfering&&state.learned(id)&&state.presets.currentContains(id)
                &&SkillCatalog.enabled(CATEGORY,id)&&HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    private static boolean materialUnchanged(ServerPlayer p,ItemStack held,ItemStack expected) {
        return (p.getMainHandItem()==held||p.getOffhandItem()==held)&&ItemStack.matches(held,expected);
    }
    private static List<LivingEntity> target(ServerPlayer p,Lease lease) {
        Vec3 eye=p.getEyePosition(), direction=ClassicRaytrace.direction(p);
        if(!finite(eye)||!finite(direction)||!chunksLoaded(lease.world,eye,eye.add(direction.scale(number("liquid_strike", "reach")))))return List.of();
        var hit=ClassicRaytrace.perform(p,eye,eye.add(direction.scale(number("liquid_strike", "reach"))),ClipContext.Fluid.NONE,entity->entity!=lease.body);
        if(!(hit instanceof EntityHitResult entityHit)||!(entityHit.getEntity() instanceof LivingEntity target)
                ||!eligible(p,lease,target,STRIKE_RANGE)||!visible(lease,target))return List.of();
        return List.of(target);
    }
    private static List<LivingEntity> targets(ServerPlayer p,Lease lease) {
        var area=lease.body.getBoundingBox().inflate(FIELD_RANGE);
        if(!chunksLoaded(lease.world,new Vec3(area.minX,area.minY,area.minZ),new Vec3(area.maxX,area.maxY,area.maxZ)))return List.of();
        var found=lease.world.getEntitiesOfClass(Mob.class,area,m->eligible(p,lease,m,FIELD_RANGE));
        if(found.size()>MAX_CANDIDATES)return List.of();found.sort(Comparator.comparing(Mob::getUUID));
        var selected=new ArrayList<LivingEntity>();
        for(Mob mob:found){if(selected.size()==MAX_FIELD_TARGETS)break;if(p.hasLineOfSight(mob)&&visible(lease,mob))selected.add(mob);}
        return List.copyOf(selected);
    }
    private static boolean eligible(ServerPlayer p,Lease lease,LivingEntity target,double range) {
        if(!(target instanceof Mob)||!CanonTargetPolicy.hostile(target)||target.isRemoved()||!target.isAlive()
                ||target.isPassenger()||target.isVehicle()||target.isAlliedTo(p)||p.isAlliedTo(target)
                ||target.distanceToSqr(lease.body)>range*range||!finite(target.position())||!p.hasLineOfSight(target)
                ||HighPowerCombat.unsupportedReason(target).isPresent())return false;
        var type=target.getType();
        return CanonTargetPolicy.reviewed(target)||net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")
                &&type!=EntityType.WITHER&&type!=EntityType.ENDER_DRAGON&&type!=EntityType.WARDEN&&type!=EntityType.ELDER_GUARDIAN;
    }
    private static boolean visible(Lease lease,LivingEntity target) {
        Vec3 from=lease.body.getEyePosition(),to=target.getBoundingBox().getCenter();
        return finite(from)&&finite(to)&&chunksLoaded(lease.world,from,to)&&lease.world.clip(new ClipContext(from,to,
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,lease.body)).getType()==HitResult.Type.MISS;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(Lease lease:List.copyOf(ACTIVE.values())){
            if(lease.owner.server!=event.getServer())continue;
            if(!valid(lease)){finish(lease);continue;}
            var body=lease.body;var owner=lease.owner;Movement mode=MOVEMENT.getOrDefault(owner.getUUID(),Movement.AIM);
            Vec3 difference=owner.position().subtract(body.position());
            if(mode==Movement.RECALL && difference.lengthSqr()<Math.pow(number("liquid_recall", "stop_distance"),2)){finish(lease);continue;}
            Vec3 waypoint=WAYPOINTS.get(owner.getUUID());
            if(mode==Movement.ROUTE&&(waypoint==null||!finite(waypoint)||waypoint.distanceToSqr(body.position())<.04)) {
                MOVEMENT.put(owner.getUUID(),Movement.AIM);WAYPOINTS.remove(owner.getUUID());mode=Movement.AIM;
                PAUSED.add(owner.getUUID());
            }
            Vec3 direction=mode==Movement.AIM?ClassicRaytrace.direction(owner):mode==Movement.RETREAT?ClassicRaytrace.direction(owner).scale(-1):mode==Movement.ROUTE?waypoint.subtract(body.position()):difference;
            if(mode==Movement.FOLLOW && difference.lengthSqr()<=Math.pow(number("liquid_follow", "stop_distance"),2))direction=Vec3.ZERO;
            if(finite(direction)&&!owner.isShiftKeyDown()&&!PAUSED.contains(owner.getUUID())){
                Vec3 horizontal=new Vec3(direction.x,0,direction.z);
                if(horizontal.lengthSqr()>1e-8){
                    double speed=number(switch(mode){
                        case AIM -> "liquid_scout";
                        case FOLLOW -> "liquid_follow";
                        case RECALL -> "liquid_recall";
                        case RETREAT -> "liquid_retreat";
                        case ROUTE -> "liquid_route";
                    }, "speed");
                    Vec3 step=horizontal.normalize().scale(speed),next=body.position().add(step);
                    if(next.distanceToSqr(owner.position())<=CONTROL_RANGE*CONTROL_RANGE&&chunksLoaded(lease.world,body.position(),next)
                            &&ground(lease.world,next)&&lease.world.mayInteract(owner,BlockPos.containing(next))
                            &&lease.world.noCollision(body,body.getBoundingBox().move(step))){
                        body.move(MoverType.SELF,step);body.setYRot(owner.getYHeadRot());body.setYHeadRot(owner.getYHeadRot());
                        body.setYBodyRot(owner.getYHeadRot());float swing=(float)(22*Math.sin(lease.world.getGameTime()*.35));
                        body.setLeftLegPose(new Rotations(swing,0,0));body.setRightLegPose(new Rotations(-swing,0,0));
                    }
                }
            }
            Guard guard=GUARDS.get(owner.getUUID());
            if(guard!=null) {
                if(guard.lease!=lease||lease.world.getGameTime()>=guard.expires)GUARDS.remove(owner.getUUID(),guard);
                else {
                    var arrows=lease.world.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,body.getBoundingBox().inflate(number("liquid_guard", "radius")),a->incoming(owner,body,a,"liquid_guard"));
                    if(arrows.size()<=64&&!arrows.isEmpty()) {
                        arrows.sort(Comparator.comparingDouble(a->a.distanceToSqr(body)));var arrow=arrows.getFirst();Vec3 before=arrow.getDeltaMovement();
                        if(valid(lease)&&incoming(owner,body,arrow,"liquid_guard")&&HighPowerCombat.spendShieldFraction(owner,new java.math.BigDecimal("0.05"))) {
                            GUARDS.remove(owner.getUUID(),guard);
                            if(incoming(owner,body,arrow,"liquid_guard")&&before.equals(arrow.getDeltaMovement())) {
                                Vec3 radial=arrow.position().subtract(body.getBoundingBox().getCenter()).normalize().scale(Math.min(2,Math.max(.35,before.length())));
                                arrow.setDeltaMovement(radial);arrow.hasImpulse=true;arrow.hurtMarked=true;
                                CanonVfx.emit(owner,"liquid_guard",body.position(),arrow.position(),CanonVfx.Phase.HIT,12);
                            }
                        }
                    }
                }
            }
            if(lease.world.getGameTime()%20==0) {
                lease.world.sendParticles(ParticleTypes.SQUID_INK,body.getX(),body.getY()+.5,body.getZ(),3,.18,.2,.18,0);
            }
        }
    }
    private static boolean valid(Lease lease) {
        var p=lease.owner;var body=lease.body;var world=lease.world;
        if(ACTIVE.get(p.getUUID())!=lease||!p.server.isSameThread()||p.connection==null||p.hasDisconnected()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||p.isRemoved()||!p.isAlive()||p.isSpectator()
                ||p.serverLevel()!=world||world.getGameTime()>=lease.expires||body.isRemoved()||body.level()!=world
                ||!world.hasChunkAt(body.blockPosition())||world.getEntity(body.getUUID())!=body
                ||!body.controlOwner().filter(p.getUUID()::equals).isPresent()
                ||body.distanceToSqr(p)>CONTROL_RANGE*CONTROL_RANGE||!finite(body.position())
                ||HighPowerCombat.unsupportedReason(p).isPresent())return false;
        var state=AbilityStorage.get(p);
        return CATEGORY.equals(state.category)&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned("liquid_scout")&&SkillCatalog.enabled(CATEGORY,"liquid_scout");
    }
    static CanonLiquidEntities.Shadow controlledBody(ServerPlayer p) {
        if(p==null)return null;var lease=ACTIVE.get(p.getUUID());
        return lease!=null&&lease.owner==p&&valid(lease)?lease.body:null;
    }
    static boolean sameBody(ServerPlayer p,CanonLiquidEntities.Shadow body) {
        return body!=null&&controlledBody(p)==body;
    }
    private static boolean ground(ServerLevel world,Vec3 at) {
        BlockPos below=BlockPos.containing(at.x,at.y-.05,at.z);
        return world.hasChunkAt(below)&&world.getBlockState(below).isFaceSturdy(world,below,Direction.UP);
    }
    private static void inkLine(ServerLevel level,Vec3 from,Vec3 to) {
        Vec3 delta=to.subtract(from);int count=Math.max(1,Math.min(24,(int)Math.ceil(delta.length()*2)));
        for(int i=0;i<=count;i++){Vec3 point=from.add(delta.scale((double)i/count));
            level.sendParticles(ParticleTypes.SQUID_INK,point.x,point.y,point.z,1,.02,.02,.02,0);}
    }
    private static boolean chunksLoaded(ServerLevel level,Vec3 a,Vec3 b){
        if(!finite(a)||!finite(b))return false;
        int x0=Math.floorDiv((int)Math.floor(Math.min(a.x,b.x)),16),x1=Math.floorDiv((int)Math.floor(Math.max(a.x,b.x)),16);
        int z0=Math.floorDiv((int)Math.floor(Math.min(a.z,b.z)),16),z1=Math.floorDiv((int)Math.floor(Math.max(a.z,b.z)),16);
        if((long)x1-x0>8||(long)z1-z0>8)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!level.hasChunk(x,z))return false;
        return true;
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void award(ServerPlayer p,String id){var state=AbilityStorage.get(p);if(CATEGORY.equals(state.category)&&state.learned(id)){state.addExperience(id,.01);AbilityStorage.save(p);}}
    private static void finish(Lease lease){
        if(!ACTIVE.remove(lease.owner.getUUID(),lease))return;
        if(lease.owner.serverLevel()==lease.world)
            CanonVfx.emit(lease.owner,"liquid_scout",lease.owner.position(),lease.body.position(),CanonVfx.Phase.END,8);
        PAUSED.remove(lease.owner.getUUID());MOVEMENT.remove(lease.owner.getUUID());WAYPOINTS.remove(lease.owner.getUUID());GUARDS.remove(lease.owner.getUUID());SOUNDS.remove(lease.owner.getUUID());lease.body.controlOwner(null);lease.body.observationModule(false);
        if(!lease.body.isRemoved())lease.body.discard();
    }
    private static void remove(ServerPlayer p){var lease=ACTIVE.get(p.getUUID());if(lease!=null&&lease.owner==p)finish(lease);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){for(Lease lease:List.copyOf(ACTIVE.values()))if(lease.owner.server==e.getServer())finish(lease);}
}
