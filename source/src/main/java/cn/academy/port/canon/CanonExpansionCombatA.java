package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.CanonEmission;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.MagneticItemProvenance;
import cn.academy.port.highpower.MagneticItemPull;
import cn.academy.port.skill.ClassicRaytrace;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Bounded server actions for the first group of formal canon expansions. */
@EventBusSubscriber(modid="academy")
public final class CanonExpansionCombatA {
    private static final TagKey<EntityType<?>> MENTAL_TARGETS=TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("academy","mental_controllable"));
    private static final Set<ServerPlayer> BUSY=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Mob> MUTATING=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Mob,MindLease> MIND_LEASES=new IdentityHashMap<>();
    private static final Map<ServerPlayer,Barrier> BARRIERS=new IdentityHashMap<>();
    private static final Map<ServerPlayer,FallGuard> FALL_GUARDS=new IdentityHashMap<>();
    private static final Map<ServerPlayer,AllyGuard> ALLY_GUARDS=new IdentityHashMap<>();
    private record FallGuard(Context initial,long expires) {}
    private record AllyGuard(Context initial,ServerPlayer ally,long expires) {}
    private static final double CONTACT_GUARD_HINT_WIDTH = 2.1;
    /** Frontal box dimensions are shared by server interception and the client boundary. */
    public static double barrierHalfWidth(String skill){return number(skill, "half_width");}
    public static double barrierDistance(String skill){return number(skill, "deploy_distance");}
    public static boolean frontalBarrier(String skill){return Set.of("dark_shard_screen","nitrogen_plate","nitrogen_shield_line","nitrogen_bulwark","aero_pressure_wall").contains(skill);}
    public static boolean physicalBarrier(String skill){return frontalBarrier(skill)||Set.of("dark_guard","dark_panel_dome","aero_air_curtain").contains(skill);}
    public static AABB barrierBox(Vec3 centre,String skill){double w=frontalBarrier(skill)?barrierHalfWidth(skill):skill.equals("dark_guard")?CONTACT_GUARD_HINT_WIDTH:number(skill, "radius");return new AABB(centre.subtract(w,w,w),centre.add(w,w,w));}
    private static final class Barrier {
        final Context initial; final Vec3 anchor,axis; final long expires; final int cap; long lastTick=Long.MIN_VALUE;int intercepted;
        Barrier(Context initial,int life,int cap) {this.initial=initial;anchor=initial.eye;axis=initial.look.normalize();expires=initial.tick+life;this.cap=cap;}
    }
    private record MindLease(Context owner,Mob mob,LivingEntity previous,LivingEntity written,long expires,
                             boolean routeOnly,ServerPlayer consent) {}
    private record Assignment(Mob mob,LivingEntity previous,LivingEntity written,boolean routeOnly,ServerPlayer consent) {
        Assignment(Mob mob,LivingEntity previous,LivingEntity written) { this(mob,previous,written,false,null); }
    }
    public static boolean mentalLeased(Mob mob) { return mob!=null && (MIND_LEASES.containsKey(mob) || MUTATING.contains(mob) || CanonPuppets.isPuppet(mob)); }
    private CanonExpansionCombatA() {}

    public static boolean cast(ServerPlayer p,String id) {
        var definition=CanonExpansionA.find(id);
        if (p==null || definition==null || !p.server.isSameThread() || p.connection==null || p.hasDisconnected()
                || p.server.getPlayerList().getPlayer(p.getUUID())!=p || !p.isAlive() || p.isRemoved() || p.isSpectator()
                || AbilityConsumption.busy(p) || p.containerMenu!=p.inventoryMenu || !HighPowerResources.ready(p)
                || HighPowerCombat.unsupportedReason(p).isPresent() || !finite(p.position()) || !finite(p.getEyePosition())
                || !finite(p.getLookAngle()) || !finite(p.getDeltaMovement()) || !finite(p.getKnownMovement()) || !BUSY.add(p)) return false;
        try {
            var state=AbilityStorage.get(p);
            if (!definition.category().equals(state.category) || !state.canUse(id) || !state.presets.currentContains(id)
                    || !state.learned(id) || !SkillCatalog.enabled(state.category,id)
                    || HighPowerResources.effectiveLevel(p)<definition.level()) return false;
            Context c=new Context(p,state,p.serverLevel(),p.position(),p.getEyePosition(),p.getLookAngle(),p.getDeltaMovement(),p.getKnownMovement(),
                    state.presets.current(),state.presets.revision(),state.level,p.serverLevel().getGameTime(),p.onGround(),p.isShiftKeyDown(),id);
            return switch(id) {
                case "mental_puppet" -> puppet(c);
                case "dark_synthesis" -> synthesize(c);
                case "mental_life_scan", "mental_hostile_scan" -> scan(c);
                case "mental_intent_scan", "mental_target_sense", "mental_focus_break" -> mental(c);
                case "mental_disengage", "mental_attention_shift", "mental_interpose", "mental_redirect", "mental_conflict",
                     "mental_suppress_pair", "mental_pursuit_break", "mental_route_interrupt", "mental_silence_field",
                     "mental_panic_field", "mental_pursuit_field", "mental_crowd_detach", "mental_conflict_ring",
                     "mental_crossfire", "mental_relay_interference", "mental_lure_pair" -> control(c);
                case "aero_brake", "aero_updraft", "aero_side_step", "aero_backstep", "aero_fall_brake", "aero_airborne_turn",
                     "dark_step", "dark_lift", "dark_wing_brake", "dark_wing_rescue", "nitrogen_dash", "nitrogen_brake", "nitrogen_buoy",
                     "aero_vertical_jet", "aero_double_jet", "nitrogen_rocket" -> motion(c);
                case "aero_push", "aero_crosswind", "nitrogen_shove" -> shove(c);
                case "aero_item_pull", "dark_shard_return", "nitrogen_item_push" -> pullItem(c);
                case "aero_vortex", "dark_shard_orbit" -> vortex(c);
                case "nitrogen_shell_reinforce" -> reinforce(c);
                case "aero_ring_push" -> ringPush(c);
                case "dark_feather_guard", "nitrogen_guard", "aero_arrow_deflect" -> arrowGuard(c);
                case "dark_guard", "dark_shard_screen", "dark_panel_dome", "nitrogen_plate", "nitrogen_shield_line", "nitrogen_bulwark",
                     "aero_pressure_wall", "aero_air_curtain" -> barrier(c);
                case "nitrogen_fall_guard" -> fallGuard(c);
                case "nitrogen_ally_guard", "dark_wing_shelter" -> allyGuard(c);
                case "dark_shard", "dark_sweep", "dark_spread", "dark_pierce", "dark_shard_salvo",
                     "nitrogen_burst", "nitrogen_uppercut", "nitrogen_sweep", "nitrogen_cone", "nitrogen_ground_wave",
                     "aero_gust", "aero_cone", "aero_ground_blast", "aero_pressure_lance", "aero_squall",
                     "dark_rain", "dark_wing_charge", "dark_spear_fan", "dark_shard_ring", "dark_matter_barrage",
                     "nitrogen_ram", "nitrogen_shock_ring", "nitrogen_impact" -> strike(c);
                default -> false;
            };
        } finally { BUSY.remove(p); }
    }
    private static final Set<String> REMOTE_SCANS=cn.academy.port.core.RangedSkillRanges.REMOTE_SCANS;
    /** Only the four scan skills accept the Mind Remote's alternate reach. */
    private static double reach(Context c) {
        return number(c.skill, REMOTE_SCANS.contains(c.skill) && CanonAuxItems.inHands(c.p, CanonAuxItems.MENTAL_REMOTE)
                ? "remote_reach" : "reach");
    }
    /** Star marks over scanned enemies; the Mind Remote only. Presentation, capped, and a no-op when the skill is not learned. */
    private static void marks(Context c,java.util.Collection<? extends Mob> seen){
        if(!REMOTE_SCANS.contains(c.skill)||!CanonAuxItems.inHands(c.p,CanonAuxItems.MENTAL_REMOTE))return;
        int n=0;for(Mob m:seen){if(n++>=4)break;CanonVfx.emit(c.p,"mental_probe",c.eye,m.position().add(0,m.getBbHeight()*.5,0),CanonVfx.Phase.HIT,100);}
    }
    private record Context(ServerPlayer p,AbilityProgress state,ServerLevel world,Vec3 pos,Vec3 eye,Vec3 look,Vec3 velocity,Vec3 knownMovement,
                           int preset,long revision,int level,long tick,boolean ground,boolean shift,String skill) {}
    private static boolean context(Context c) {
        ServerPlayer p=c.p; var state=AbilityStorage.get(p);
        return p.server.isSameThread() && p.serverLevel()==c.world && c.world.getGameTime()==c.tick
                && !p.isRemoved() && p.isAlive() && !p.isSpectator() && !p.hasDisconnected() && p.connection!=null
                && p.server.getPlayerList().getPlayer(p.getUUID())==p && p.containerMenu==p.inventoryMenu
                && state==c.state && state.level==c.level && state.presets.current()==c.preset && state.presets.revision()==c.revision
                && state.activated && state.overloadFine && !state.interfering && state.learned(c.skill)
                && state.presets.currentContains(c.skill) && SkillCatalog.enabled(state.category,c.skill)
                && p.position().equals(c.pos) && p.getEyePosition().equals(c.eye) && p.getLookAngle().equals(c.look)
                && p.getDeltaMovement().equals(c.velocity) && p.getKnownMovement().equals(c.knownMovement) && p.onGround()==c.ground && p.isShiftKeyDown()==c.shift
                && HighPowerResources.ready(p);
    }
    /** Accepted player input updates knownMovement; server impulses still use deltaMovement.
     * Both were frozen in Context and are revalidated before payment and application. */
    private static Vec3 movementVelocity(Context c) {
        return c.knownMovement.lengthSqr()>1e-12?c.knownMovement:c.velocity;
    }
    private static boolean pay(Context c,double multiplier,int cooldown) {
        if (!context(c)) return false;
        double cost=HighPowerGrowth.values(c.p).arcCost()*multiplier;
        return Double.isFinite(cost) && cost>0 && HighPowerResources.trySpend(c.p,c.skill,cost,cooldown) && context(c);
    }
    /** Radius each area skill really checks around the caster, sent with its visual so the drawn reach is the real one. */
    private static double area(Context c) {
        return switch(c.skill) {
            case "mental_life_scan", "mental_hostile_scan" -> reach(c);
            case "mental_silence_field", "mental_panic_field", "mental_pursuit_field", "mental_pursuit_break", "mental_relay_interference",
                 "mental_conflict_ring", "mental_crossfire", "mental_crowd_detach", "mental_lure_pair" -> number(c.skill, "radius");
            case "aero_ring_push" -> number(c.skill, "radius");
            case "dark_guard" -> CONTACT_GUARD_HINT_WIDTH;
            case "dark_spear_fan" -> 0; // Three rays, not a spherical area.
            case "dark_panel_dome", "aero_air_curtain" -> number(c.skill, "radius");
            default -> { StrikeShape shape=shape(c.skill); yield shape!=null && shape.cone<0 && !shape.direct && !shape.line ? shape.radius : 0; }
        };
    }
    private static void success(Context c,Vec3 end) {
        if (AbilityStorage.get(c.p)==c.state && c.state.learned(c.skill)) {
            c.state.addExperience(c.skill,.01); AbilityStorage.save(c.p);
        }
        CanonVfx.emit(c.p,c.skill,c.pos,end,CanonVfx.Phase.HIT,10,area(c));
    }
    private static boolean eligible(Context c,Mob mob) {
        return mob instanceof Monster && mob.getType().is(MENTAL_TARGETS) && mob.isAlive() && !mob.isRemoved()
                && !mob.isNoAi() && !mob.isInvulnerable() && mob.level()==c.world && c.world.getEntity(mob.getUUID())==mob
                && !mob.isAlliedTo(c.p) && !c.p.isAlliedTo(mob) && c.p.distanceToSqr(mob)<=reach(c)*reach(c)
                && c.p.hasLineOfSight(mob) && c.world.hasChunkAt(mob.blockPosition()) && c.world.mayInteract(c.p,mob.blockPosition());
    }
    private static Mob aimed(Context c) {
        if (!chunks(c.world,c.eye,c.eye.add(c.look.scale(reach(c))))) return null;
        var hit=ClassicRaytrace.living(c.p,reach(c),ClipContext.Fluid.NONE);
        return hit instanceof EntityHitResult e && e.getEntity() instanceof Mob m && eligible(c,m) ? m : null;
    }
    /** Physical shoves move a body, not a mind: any hostile vanilla mob or reviewed target, AI or not (creepers, spiders, endermen too). */
    private static Mob aimedBody(Context c) {
        if (!chunks(c.world,c.eye,c.eye.add(c.look.scale(reach(c))))) return null;
        var hit=ClassicRaytrace.living(c.p,reach(c),ClipContext.Fluid.NONE);
        if (!(hit instanceof EntityHitResult e) || !(e.getEntity() instanceof Mob m)) return null;
        boolean known=CanonTargetPolicy.reviewed(m) || BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getNamespace().equals("minecraft");
        return known && CanonTargetPolicy.hostile(m) && m.isAlive() && !m.isRemoved() && !m.isInvulnerable() && m.level()==c.world
                && c.world.getEntity(m.getUUID())==m && !m.isAlliedTo(c.p) && !c.p.isAlliedTo(m) && !m.isPassenger() && !m.isVehicle()
                && c.p.hasLineOfSight(m) && c.world.hasChunkAt(m.blockPosition()) && c.world.mayInteract(c.p,m.blockPosition()) ? m : null;
    }
    /** Mind Puppet: any non-player creature, one at a time, with a bounded lease. The lease itself lives in {@link CanonPuppets}. */
    private static boolean puppet(Context c) {
        int duration = integer(c.skill, "duration_ticks");
        if (CanonPuppets.owns(c.p) || c.tick<0 || c.tick>Long.MAX_VALUE-duration) return false;
        Mob target=CanonPuppets.pick(c.p);
        if (target==null || mentalLeased(target) || CanonSkillsCombatA.mentalTargetLeased(target)) return false;
        if (!pay(c,4,400) || CanonPuppets.pick(c.p)!=target || CanonPuppets.owns(c.p)
                || mentalLeased(target) || CanonSkillsCombatA.mentalTargetLeased(target)) return false;
        if (!CanonPuppets.begin(c.p,target,c.tick+duration)) return false;
        c.state.addExperience(c.skill,.01); AbilityStorage.save(c.p);
        CanonVfx.emit(c.p,c.skill,c.eye,CanonPuppets.center(target),CanonVfx.Phase.START,duration); return true;
    }
    /** Dark Synthesis: one Dark Matter Crystal turns the main-hand item into two, copying every data component. */
    private static boolean synthesize(Context c) {
        ItemStack held=c.p.getMainHandItem();
        if (held.isEmpty() || held.is(CanonAuxItems.DARK_MATTER_CRYSTAL.get())) return false;
        if (!CanonAuxItems.inInventory(c.p,CanonAuxItems.DARK_MATTER_CRYSTAL)) return false;
        ItemStack expected=held.copy();
        boolean grows=held.getCount()<held.getMaxStackSize();
        if (!grows && !canFit(c.p,held)) return false;
        if (!pay(c,3,200)) return false;
        if (c.p.getMainHandItem()!=held || !ItemStack.matches(held,expected) || (!grows && !canFit(c.p,held))
                || !CanonAuxItems.consume(c.p,CanonAuxItems.DARK_MATTER_CRYSTAL)) return false;
        if (grows) held.grow(1);
        else if (!c.p.getInventory().add(held.copyWithCount(1))) return false;
        c.state.addExperience(c.skill,.01); AbilityStorage.save(c.p);
        success(c,c.pos.add(0,1,0)); return true;
    }
    private static boolean canFit(ServerPlayer p,ItemStack stack) {
        var inv=p.getInventory();
        for (int i=0;i<inv.items.size();i++) {
            ItemStack at=inv.items.get(i);
            if (at.isEmpty() || ItemStack.isSameItemSameComponents(at,stack) && at.getCount()<at.getMaxStackSize()) return true;
        }
        return false;
    }
    /** A read mind is outlined (Glowing) for {@code ticks}; {@code daze} > 0 adds a short stagger. Never damage. */
    static void reveal(ServerPlayer caster, net.minecraft.world.entity.LivingEntity target, int ticks, int daze, int stagger) {
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, ticks, 0, false, false), caster);
        if (daze > 0 && stagger > 0) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, stagger, daze - 1, false, true), caster);
    }
    private static boolean scan(Context c) {
        double r=reach(c);
        if (!chunks(c.world,c.pos.add(-r,0,-r),c.pos.add(r,0,r))) return false;
        var seen=new ArrayList<Mob>(c.world.getEntitiesOfClass(Mob.class,c.p.getBoundingBox().inflate(r),m -> eligible(c,m)));
        if (seen.size()>64) return false;
        long count=seen.stream().filter(m -> !c.skill.equals("mental_hostile_scan") || m.getTarget()==c.p).count();
        if (!pay(c,1,40)) return false;
        if (seen.stream().anyMatch(m -> !eligible(c,m))) return false;
        long now=c.world.getEntitiesOfClass(Mob.class,c.p.getBoundingBox().inflate(r),m -> eligible(c,m)
                && (!c.skill.equals("mental_hostile_scan") || m.getTarget()==c.p)).size();
        if (now!=count) return false;
        c.p.sendSystemMessage(Component.translatable("ac.canon."+c.skill+".result",count));
        // The scan does not stop at a number: every mind it touched stays outlined for the caster's whole party.
        for (Mob m:seen) if (!c.skill.equals("mental_hostile_scan") || m.getTarget()==c.p) reveal(c.p,m,integer(c.skill, "reveal_ticks"),0,0);
        if (count>0) {success(c,c.pos);marks(c,seen.stream().filter(m -> !c.skill.equals("mental_hostile_scan") || m.getTarget()==c.p).toList());} else CanonVfx.emit(c.p,c.skill,c.pos,c.pos,CanonVfx.Phase.HIT,10);
        return true;
    }
    private static boolean mental(Context c) {
        Mob mob=aimed(c); if (mob==null) return false;
        LivingEntity previous=mob.getTarget();
        if (c.skill.equals("mental_focus_break") && (mob.getNavigation().isDone() || CanonSkillsCombatA.mentalTargetLeased(mob))) return false;
        if (!pay(c,1,40) || aimed(c)!=mob || mob.getTarget()!=previous
                || c.skill.equals("mental_focus_break") && (mob.getNavigation().isDone() || CanonSkillsCombatA.mentalTargetLeased(mob))) return false;
        switch(c.skill) {
            case "mental_intent_scan" -> c.p.sendSystemMessage(Component.translatable("ac.canon.mental_intent_scan."
                    +(previous==null?"none":previous==c.p?"self":"other")));
            case "mental_target_sense" -> c.p.sendSystemMessage(Component.translatable("ac.canon.mental_target_sense."
                    +(previous==null?"none":previous instanceof Player?"player":"mob")));
            case "mental_focus_break" -> { mob.getNavigation().stop(); if (!mob.getNavigation().isDone()) return false; }
            default -> { return false; }
        }
        if (c.skill.equals("mental_intent_scan")) reveal(c.p,mob,integer(c.skill, "reveal_ticks"),previous==c.p?1:0,integer(c.skill, "stagger_ticks"));   // read intent: the hunter is exposed and its approach falters
        if (c.skill.equals("mental_target_sense")) { reveal(c.p,mob,integer(c.skill, "reveal_ticks"),0,0); if (previous instanceof Mob other && eligible(c,other)) reveal(c.p,other,integer(c.skill, "reveal_ticks"),0,0); }
        success(c,mob.position().add(0,mob.getBbHeight()*.5,0)); marks(c,java.util.List.of(mob)); return true;
    }
    /** Server-owned AI target leases. Target-change event reentry is fenced and unwound conditionally. */
    private static boolean control(Context c) {
        int duration = integer(c.skill, "duration_ticks");
        int cap = integer(c.skill, "target_cap");
        if (MIND_LEASES.size()>=64 || c.tick<0 || c.tick>Long.MAX_VALUE-duration) return false;
        Mob selected=aimed(c); var changes=new ArrayList<Assignment>();
        if (c.skill.equals("mental_route_interrupt")) {
            if(selected==null || mentalLeased(selected) || CanonSkillsCombatA.mentalTargetLeased(selected)
                    || selected.getNavigation().isDone())return false;
            changes.add(new Assignment(selected,selected.getTarget(),selected.getTarget(),true,null));
        } else if (c.skill.equals("mental_silence_field") || c.skill.equals("mental_panic_field")
                || c.skill.equals("mental_relay_interference") || c.skill.equals("mental_pursuit_break")
                || c.skill.equals("mental_pursuit_field") || c.skill.equals("mental_crowd_detach")
                || c.skill.equals("mental_conflict_ring") || c.skill.equals("mental_crossfire") || c.skill.equals("mental_lure_pair")) {
            ServerPlayer protectedAlly=null;
            if(c.skill.equals("mental_crowd_detach")) {
                var hit=ClassicRaytrace.living(c.p,number(c.skill, "ally_reach"),ClipContext.Fluid.NONE);
                if(!(hit instanceof EntityHitResult e) || !(e.getEntity() instanceof ServerPlayer ally) || !consentingAlly(c,ally))return false;
                protectedAlly=ally;
            }
            final ServerPlayer anchor=protectedAlly;
            if(c.skill.equals("mental_lure_pair") && selected==null)return false;
            double radius = number(c.skill, "radius");
            var nearby=new ArrayList<Mob>(c.world.getEntitiesOfClass(Mob.class,c.p.getBoundingBox().inflate(radius),
                    m -> c.p.distanceToSqr(m) <= radius * radius && eligible(c,m) && !mentalLeased(m) && !CanonSkillsCombatA.mentalTargetLeased(m)));
            if(nearby.size()>32)return false;
            nearby.sort(Comparator.comparingDouble((Mob m)->c.p.distanceToSqr(m)).thenComparingInt(Entity::getId));
            if(c.skill.equals("mental_conflict_ring") || c.skill.equals("mental_crossfire")) {
                if(nearby.size()<(c.skill.equals("mental_crossfire")?4:3))return false;
                var group=List.copyOf(nearby.subList(0,Math.min(cap,nearby.size())));
                for(int i=0;i<group.size();i++) {
                    Mob actor=group.get(i);Mob victim=group.get(c.skill.equals("mental_conflict_ring")?(i+1)%group.size():(i+group.size()/2)%group.size());
                    // One blind or distant pair used to void the whole cast, so it almost never fired in real terrain.
                    if(actor.getTarget()==victim || !actor.hasLineOfSight(victim) || actor.distanceToSqr(victim)>pairReachSquared(c)
                            || actor.isAlliedTo(victim) || victim.isAlliedTo(actor))continue;
                    changes.add(new Assignment(actor,actor.getTarget(),victim));
                }
                if(changes.size()<2)return false;
            } else if(c.skill.equals("mental_lure_pair")) {
                for(Mob actor:nearby) {
                    if(actor==selected || actor.getTarget()!=c.p || !actor.hasLineOfSight(selected) || actor.distanceToSqr(selected)>pairReachSquared(c)
                            || actor.isAlliedTo(selected) || selected.isAlliedTo(actor))continue;
                    changes.add(new Assignment(actor,c.p,selected));if(changes.size()==cap)break;
                }
                if(changes.size()!=cap)return false;
            } else for(Mob mob:nearby) {
                LivingEntity before=mob.getTarget();
                if(c.skill.equals("mental_route_interrupt"))continue;
                if(c.skill.equals("mental_silence_field") && before==null)continue;
                if((c.skill.equals("mental_panic_field") || c.skill.equals("mental_relay_interference")) && mob.getNavigation().isDone())continue;
                if(c.skill.equals("mental_pursuit_break") && before!=c.p)continue;
                if(c.skill.equals("mental_crowd_detach") && before!=anchor)continue;
                if(c.skill.equals("mental_pursuit_field") && before!=c.p
                        && (!(before instanceof ServerPlayer ally) || !consentingAlly(c,ally)))continue;
                boolean route=c.skill.equals("mental_panic_field") || c.skill.equals("mental_relay_interference");
                ServerPlayer consent=(c.skill.equals("mental_crowd_detach") || c.skill.equals("mental_pursuit_field"))
                        && before instanceof ServerPlayer ally && ally!=c.p ? ally : null;
                changes.add(new Assignment(mob,before,route?before:null,route,consent));
                if(changes.size()>=cap)break;
            }
        } else {
            if (selected==null || mentalLeased(selected) || CanonSkillsCombatA.mentalTargetLeased(selected)) return false;
            LivingEntity before=selected.getTarget();
            if (c.skill.equals("mental_disengage")) {
                if (before!=c.p) return false; changes.add(new Assignment(selected,before,null));
            } else if (c.skill.equals("mental_attention_shift")) {
                if (before==c.p) return false; changes.add(new Assignment(selected,before,c.p));
            } else {
                var nearby=new ArrayList<Mob>(c.world.getEntitiesOfClass(Mob.class,selected.getBoundingBox().inflate(number(c.skill, "pair_reach")),
                        m -> m!=selected && eligible(c,m) && !mentalLeased(m) && !CanonSkillsCombatA.mentalTargetLeased(m)
                                && selected.distanceToSqr(m)<=pairReachSquared(c) && selected.hasLineOfSight(m)
                                && !selected.isAlliedTo(m) && !m.isAlliedTo(selected)));
                if (nearby.isEmpty() || nearby.size()>32) return false;
                nearby.sort(Comparator.comparingDouble((Mob m)->selected.distanceToSqr(m)).thenComparingInt(Entity::getId));
                Mob other=nearby.getFirst();
                if (c.skill.equals("mental_suppress_pair")) {
                    if(before==null || other.getTarget()==null) return false;
                    changes.add(new Assignment(selected,before,null));changes.add(new Assignment(other,other.getTarget(),null));
                } else {
                    if (before==other || c.skill.equals("mental_interpose") && before!=c.p) return false;
                    changes.add(new Assignment(selected,before,other));
                    if (c.skill.equals("mental_conflict")) {
                        if(other.getTarget()==selected || !other.hasLineOfSight(selected)) return false;
                        changes.add(new Assignment(other,other.getTarget(),selected));
                    }
                }
            }
        }
        if (changes.isEmpty() || changes.size()>cap || MIND_LEASES.size()+changes.size()>64) return false;
        for (Assignment a:changes) if (!MUTATING.add(a.mob)) {
            for (Assignment prior:changes) { if(prior==a)break; MUTATING.remove(prior.mob); } return false;
        }
        var written=new ArrayList<Assignment>();
        try {
            if (!pay(c,changes.size(),80)) return false;
            for (Assignment a:changes) {
                if (!context(c) || !eligible(c,a.mob) || a.mob.getTarget()!=a.previous || CanonSkillsCombatA.mentalTargetLeased(a.mob)
                        || MIND_LEASES.containsKey(a.mob) || !validWritten(c,a)) { rollback(written); return false; }
                if(a.routeOnly) a.mob.getNavigation().stop(); else a.mob.setTarget(a.written);
                if (a.mob.getTarget()==a.written) written.add(a);
                if (!context(c) || !eligible(c,a.mob) || a.mob.getTarget()!=a.written || !validWritten(c,a)
                        || written.stream().anyMatch(previous -> !eligible(c,previous.mob)
                                || previous.mob.getTarget()!=previous.written || !validWritten(c,previous))) {
                    rollback(written);return false;
                }
            }
            for (Assignment a:written) {
                if(duration>0)
                    MIND_LEASES.put(a.mob,new MindLease(c,a.mob,a.previous,a.written,c.tick+duration,a.routeOnly,a.consent));
            }
            success(c,changes.getFirst().mob.position());
            if(duration>0)
                CanonVfx.emit(c.p,c.skill,c.pos,changes.getFirst().mob.position(),CanonVfx.Phase.ACTIVE,duration,area(c));
            return true;
        } finally { for (Assignment a:changes) MUTATING.remove(a.mob); }
    }
    private static double pairReachSquared(Context c) {
        double reach = number(c.skill, "pair_reach");
        return reach * reach;
    }

    private static boolean consentingAlly(Context c,ServerPlayer ally) {
        double reach = number(c.skill, "ally_reach");
        return ally!=c.p && ally.serverLevel()==c.world && ally.isAlive() && !ally.isRemoved() && !ally.isSpectator()
                && !ally.hasDisconnected() && ally.connection!=null && ally.server.getPlayerList().getPlayer(ally.getUUID())==ally
                && ally.isShiftKeyDown() && c.p.isAlliedTo(ally) && ally.isAlliedTo(c.p)
                && c.p.distanceToSqr(ally)<=reach*reach && c.p.hasLineOfSight(ally);
    }
    private static boolean validWritten(Context c,Assignment a) {
        if(a.consent!=null && !consentingAlly(c,a.consent))return false;
        if(a.routeOnly)return a.mob.getTarget()==a.previous;
        if (a.written==null || a.written==c.p) return true;
        return a.written instanceof Mob other && eligible(c,other) && a.mob.distanceToSqr(other)<=pairReachSquared(c)
                && a.mob.hasLineOfSight(other) && !a.mob.isAlliedTo(other) && !other.isAlliedTo(a.mob);
    }
    private static void rollback(List<Assignment> written) {
        for (int i=written.size()-1;i>=0;i--) {
            Assignment a=written.get(i);
            if (!a.routeOnly && !a.mob.isRemoved() && a.mob.getTarget()==a.written) a.mob.setTarget(safePrevious(a.mob,a.previous));
        }
    }
    private static LivingEntity safePrevious(Mob mob,LivingEntity previous) {
        return previous!=null && previous.isAlive() && !previous.isRemoved() && previous.level()==mob.level() ? previous : null;
    }
    private static boolean validLease(MindLease lease) {
        Context c=lease.owner;ServerPlayer p=c.p;Mob m=lease.mob;var state=AbilityStorage.get(p);
        return MIND_LEASES.get(m)==lease && p.server.isSameThread() && p.serverLevel()==c.world && !p.hasDisconnected()
                && p.connection!=null && p.server.getPlayerList().getPlayer(p.getUUID())==p && p.isAlive() && !p.isRemoved()
                && state==c.state && state.level==c.level && state.presets.current()==c.preset && state.presets.revision()==c.revision
                && "mental_out".equals(state.category) && state.activated && state.overloadFine && !state.interfering
                && state.learned(c.skill) && state.presets.currentContains(c.skill) && SkillCatalog.enabled(state.category,c.skill)
                && c.world.getGameTime()<lease.expires && m.level()==c.world && !m.isRemoved() && m.isAlive()
                && c.world.getEntity(m.getUUID())==m && p.distanceToSqr(m)<=reach(c)*reach(c) && c.world.hasChunkAt(m.blockPosition())
                && c.world.mayInteract(p,m.blockPosition()) && (lease.consent==null || consentingAlly(c,lease.consent))
                && (lease.written==null || lease.written.isAlive()
                    && !lease.written.isRemoved() && lease.written.level()==c.world)
                && (!(lease.written instanceof Mob other) || eligible(c,other) && m.distanceToSqr(other)<=pairReachSquared(c)
                    && m.hasLineOfSight(other) && !m.isAlliedTo(other) && !other.isAlliedTo(m));
    }
    private static void release(MindLease lease) {
        if (!MIND_LEASES.remove(lease.mob,lease)) return;
        Mob mob=lease.mob;
        if (!lease.routeOnly && !mob.isRemoved() && mob.isAlive() && mob.getTarget()==lease.written && MUTATING.add(mob)) {
            try { mob.setTarget(safePrevious(mob,lease.previous)); } finally { MUTATING.remove(mob); }
        }
        CanonVfx.emit(lease.owner.p,lease.owner.skill,lease.owner.p.position(),lease.owner.p.position(),CanonVfx.Phase.END,1);
    }
    @SubscribeEvent public static void tickMind(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        MindLease lease=MIND_LEASES.get(mob); if (lease==null) return;
        if (!validLease(lease) || mob.getTarget()!=lease.written) { release(lease);return; }
        if (lease.routeOnly || lease.written==null) mob.getNavigation().stop();
    }
    @SubscribeEvent public static void leaveMind(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Mob mob) { MindLease lease=MIND_LEASES.get(mob);if(lease!=null)release(lease); }
    }
    private static void releaseOwner(ServerPlayer p) {
        releaseBarrier(p,BARRIERS.get(p));releaseFall(p,FALL_GUARDS.get(p));releaseAlly(p,ALLY_GUARDS.get(p));
        for (MindLease lease:List.copyOf(MIND_LEASES.values())) if(lease.owner.p==p) release(lease);
    }
    @SubscribeEvent public static void logoutMind(PlayerEvent.PlayerLoggedOutEvent event) {if(event.getEntity() instanceof ServerPlayer p)releaseOwner(p);}
    @SubscribeEvent public static void dimensionMind(PlayerEvent.PlayerChangedDimensionEvent event) {if(event.getEntity() instanceof ServerPlayer p)releaseOwner(p);}
    @SubscribeEvent public static void cloneMind(PlayerEvent.Clone event) {if(event.getOriginal() instanceof ServerPlayer p)releaseOwner(p);}
    @SubscribeEvent public static void stopMind(ServerStoppedEvent event) {
        for (MindLease lease:List.copyOf(MIND_LEASES.values())) if(lease.owner.p.server==event.getServer())release(lease);
        for(Barrier b:List.copyOf(BARRIERS.values()))if(b.initial.p.server==event.getServer())releaseBarrier(b.initial.p,b);
        for(FallGuard f:List.copyOf(FALL_GUARDS.values()))if(f.initial.p.server==event.getServer())releaseFall(f.initial.p,f);
        for(AllyGuard a:List.copyOf(ALLY_GUARDS.values()))if(a.initial.p.server==event.getServer())releaseAlly(a.initial.p,a);
        MUTATING.clear();BUSY.clear();
    }

    private static boolean canMove(Context c) {
        ServerPlayer p=c.p;
        return !p.noPhysics && !p.isNoGravity() && !p.isPassenger() && !p.isVehicle() && !p.isFallFlying()
                && !p.getAbilities().flying && !p.isInWaterOrBubble() && !p.isInLava();
    }
    private static boolean grounded(Context c) {
        BlockPos floor=c.p.blockPosition().below();
        return c.p.onGround() && c.world.hasChunkAt(floor) && c.world.mayInteract(c.p,floor)
                && !c.world.getBlockState(floor).getCollisionShape(c.world,floor).isEmpty();
    }
    private static boolean safeMotion(Context c,Vec3 next) {
        if (!finite(next) || next.lengthSqr()>4) return false;
        AABB moved=c.p.getBoundingBox().move(next.scale(.5)).deflate(1e-7);
        if (!c.world.getWorldBorder().isWithinBounds(moved) || !c.world.noCollision(c.p,moved)) return false;
        for (BlockPos at:BlockPos.betweenClosed(BlockPos.containing(moved.minX,moved.minY,moved.minZ),
                BlockPos.containing(moved.maxX,moved.maxY,moved.maxZ)))
            if (!c.world.hasChunkAt(at) || c.world.isOutsideBuildHeight(at) || !c.world.mayInteract(c.p,at)) return false;
        return true;
    }
    private static boolean motion(Context c) {
        if (!canMove(c)) return false;
        String id=c.skill; boolean shell=id.startsWith("nitrogen_"); boolean wing=id.startsWith("dark_wing_");
        if (shell && !CanonSkillsCombatA.ownsNitrogenShell(c.p) || wing && !CanonSkillsCombatA.ownsDarkWings(c.p)) return false;
        Vec3 horizontal=new Vec3(c.look.x,0,c.look.z); if (horizontal.lengthSqr()<1e-8) return false;
        horizontal=horizontal.normalize(); Vec3 v=movementVelocity(c); Vec3 next;
        switch(id) {
            case "aero_brake", "nitrogen_brake", "dark_wing_brake" -> {
                if (v.x*v.x+v.z*v.z<.0225) return false;
                next=new Vec3(v.x*number(id, "horizontal_factor"),v.y,v.z*number(id, "horizontal_factor"));
            }
            case "aero_updraft", "nitrogen_rocket" -> { if(!grounded(c))return false; next=new Vec3(v.x*number(id, "horizontal_factor"),number(id, "vertical_speed"),v.z*number(id, "horizontal_factor")); }
            case "aero_side_step" -> { if(!grounded(c))return false; Vec3 side=new Vec3(-horizontal.z,0,horizontal.x).scale(number(id, "horizontal_speed")*(c.shift?-1:1)); next=side.add(0,number(id, "vertical_speed"),0); }
            case "aero_backstep" -> { if(!grounded(c))return false; next=horizontal.scale(-number(id, "horizontal_speed")).add(0,number(id, "vertical_speed"),0); }
            case "aero_fall_brake", "nitrogen_buoy", "dark_wing_rescue" -> {
                if(c.p.onGround() || v.y>=-.2)return false;next=new Vec3(v.x, number(id, "vertical_speed"), v.z);
            }
            case "aero_airborne_turn" -> {
                double speed=Math.sqrt(v.x*v.x+v.z*v.z);
                if(Boolean.getBoolean("academy.debug.lease"))com.mojang.logging.LogUtils.getLogger().info("AIR_TURN ground={} v={} speed={} dot={} look={}",c.p.onGround(),v,speed,new Vec3(v.x,0,v.z).normalize().dot(horizontal),horizontal);
                // Only redirects speed it already has (never adds any): .2 sat above what air-sprinting holds (~.19), so it refused in ordinary jumps.
                if(c.p.onGround() || speed<number(id, "speed_floor") || new Vec3(v.x,0,v.z).normalize().dot(horizontal)>.86)return false;
                next=horizontal.scale(Math.min(speed,number(id, "speed_cap"))).add(0,v.y,0);
            }
            case "dark_step", "nitrogen_dash" -> { if(!grounded(c))return false;next=horizontal.scale(number(id, "horizontal_speed")).add(0,number(id, "vertical_speed"),0); }
            case "dark_lift" -> { if(c.p.onGround() || v.y>.15)return false;next=new Vec3(v.x, number(id, "vertical_speed"), v.z); }
            case "aero_vertical_jet" -> { if(c.p.onGround() || v.y>.1)return false;next=new Vec3(v.x*number(id, "horizontal_factor"),number(id, "vertical_speed"),v.z*number(id, "horizontal_factor")); }
            case "aero_double_jet" -> { if(c.p.onGround() || v.y>=-.2)return false;next=horizontal.scale(number(id, "horizontal_speed")).add(0,number(id, "vertical_speed"),0); }
            default -> {return false;}
        }
        // Nitrogen Canister (anywhere in the inventory): +25% push for dash and rocket, one canister per cast, only if the boosted path is also safe.
        Vec3 plain=next;boolean boost=(id.equals("nitrogen_dash")||id.equals("nitrogen_rocket"))&&CanonAuxItems.inInventory(c.p,CanonAuxItems.NITROGEN_CANISTER);
        if(boost){double factor=number(id, "boost_factor");Vec3 stronger=id.equals("nitrogen_dash")?new Vec3(next.x*factor,next.y,next.z*factor):new Vec3(next.x,next.y*factor,next.z);
            if(safeMotion(c,stronger))next=stronger;else boost=false;}
        boolean needsGround=id.equals("nitrogen_rocket") || id.equals("aero_updraft") || id.equals("aero_side_step") || id.equals("aero_backstep")
                || id.equals("dark_step") || id.equals("nitrogen_dash");
        if(Boolean.getBoolean("academy.debug.lease")&&id.equals("aero_airborne_turn"))com.mojang.logging.LogUtils.getLogger().info("AIR_TURN next={} safe={} move={}",next,safeMotion(c,next),canMove(c));
        if (!safeMotion(c,next) || !pay(c,1,40) || !canMove(c) || needsGround && !grounded(c)
                || shell && !CanonSkillsCombatA.ownsNitrogenShell(c.p)
                || wing && !CanonSkillsCombatA.ownsDarkWings(c.p) || !safeMotion(c,next)) return false;
        if (shell && !HighPowerCombat.spendShieldFraction(c.p,new BigDecimal("0.01"))) return false;
        if (!context(c) || !safeMotion(c,next) || needsGround && !grounded(c)
                || shell && !CanonSkillsCombatA.ownsNitrogenShell(c.p) || wing && !CanonSkillsCombatA.ownsDarkWings(c.p)) return false;
        if(boost&&!CanonAuxItems.consume(c.p,CanonAuxItems.NITROGEN_CANISTER)){if(!safeMotion(c,plain))return false;next=plain;}
        c.p.setDeltaMovement(next);c.p.hasImpulse=true;c.p.hurtMarked=true;
        if (id.endsWith("fall_brake") || id.equals("nitrogen_buoy") || id.equals("dark_wing_rescue") || id.equals("aero_double_jet")) c.p.fallDistance=0;
        success(c,c.pos.add(next));return true;
    }
    public record StrikeShape(double radius,double cone,int maximum,boolean direct,boolean line,boolean ground,boolean shell,int push) {}
    public static StrikeShape shape(String id) {
        return switch(id) {
            case "dark_shard" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("dark_shard"),-1,integer(id, "target_cap"),true,false,false,false,0);
            case "dark_sweep" -> new StrikeShape(number(id, "reach"),.35,integer(id, "target_cap"),false,false,false,false,1);
            case "dark_spread" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("dark_spread"),.72,integer(id, "target_cap"),false,false,false,false,0);
            case "dark_pierce" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("dark_pierce"),-1,integer(id, "target_cap"),false,true,false,false,0);
            case "dark_shard_salvo" -> new StrikeShape(number(id, "radius"),-1,integer(id, "target_cap"),false,false,false,false,0);
            case "dark_rain" -> new StrikeShape(number(id, "radius"),-1,integer(id, "target_cap"),false,false,false,false,0);
            case "dark_wing_charge" -> new StrikeShape(number(id, "reach"),-1,integer(id, "target_cap"),true,false,false,false,1);
            case "dark_spear_fan" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("dark_spear_fan"),-1,integer(id, "target_cap"),false,false,false,false,0);
            case "dark_shard_ring" -> new StrikeShape(number(id, "radius"),-1,integer(id, "target_cap"),false,false,false,false,0);
            case "dark_matter_barrage" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("dark_matter_barrage"),.65,integer(id, "target_cap"),false,false,false,false,1);
            case "nitrogen_burst" -> new StrikeShape(number(id, "reach"),-1,integer(id, "target_cap"),true,false,false,true,1);
            case "nitrogen_uppercut" -> new StrikeShape(number(id, "reach"),-1,integer(id, "target_cap"),true,false,false,true,2);
            case "nitrogen_sweep" -> new StrikeShape(number(id, "reach"),.35,integer(id, "target_cap"),false,false,false,true,1);
            case "nitrogen_cone" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("nitrogen_cone"),.72,integer(id, "target_cap"),false,false,false,true,0);
            case "nitrogen_ground_wave" -> new StrikeShape(number(id, "radius"),-1,integer(id, "target_cap"),false,false,true,true,1);
            case "nitrogen_ram" -> new StrikeShape(number(id, "reach"),-1,integer(id, "target_cap"),true,false,true,true,1);
            case "nitrogen_shock_ring" -> new StrikeShape(number(id, "radius"),-1,integer(id, "target_cap"),false,false,false,true,1);
            case "nitrogen_impact" -> new StrikeShape(number(id, "reach"),-1,integer(id, "target_cap"),true,false,false,true,1);
            case "aero_gust" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("aero_gust"),-1,integer(id, "target_cap"),true,false,false,false,1);
            case "aero_cone" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("aero_cone"),.72,integer(id, "target_cap"),false,false,false,false,0);
            case "aero_ground_blast" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("aero_ground_blast"),.55,integer(id, "target_cap"),false,false,true,false,1);
            case "aero_pressure_lance" -> new StrikeShape(cn.academy.port.core.RangedSkillRanges.fixed("aero_pressure_lance"),-1,integer(id, "target_cap"),false,true,false,false,0);
            case "aero_squall" -> new StrikeShape(number(id, "radius"),-1,integer(id, "target_cap"),false,false,false,false,1);
            default -> null;
        };
    }
    private static boolean strike(Context c) {
        StrikeShape shape=shape(c.skill);if(shape==null || shape.shell && !CanonSkillsCombatA.ownsNitrogenShell(c.p)
                || shape.ground && !grounded(c))return false;
        boolean groundBlast = c.skill.equals("aero_ground_blast");
        var selected=new ArrayList<LivingEntity>();
        List<LivingEntity> surplus = List.of();
        if(shape.direct) {
            var hit=ClassicRaytrace.living(c.p,shape.radius,ClipContext.Fluid.NONE);
            if(hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity target && strikeCandidate(c,shape,target))selected.add(target);
        } else {
            var found=c.world.getEntitiesOfClass(LivingEntity.class,c.p.getBoundingBox().inflate(shape.radius),target->strikeCandidate(c,shape,target));
            if(found.size()>64)return false;
            selected.addAll(found);selected.sort(Comparator.comparingDouble((LivingEntity e)->c.p.distanceToSqr(e)).thenComparingInt(Entity::getId));
            if (selected.size() > shape.maximum) {
                var excess = selected.subList(shape.maximum, selected.size());
                if (groundBlast) surplus = List.copyOf(excess);
                excess.clear();
            }
        }
        if(selected.isEmpty())return false;
        double cost=HighPowerGrowth.values(c.p).arcCost()*(shape.maximum>1?4:2);
        if(!Double.isFinite(cost) || cost<=0)return false;
        var cast=CanonEmission.begin(c.p,c.skill,cost,80,List.copyOf(selected),target->strikeCandidate(c,shape,target));
        if(cast==null)return false;
        int hits=0;
        var contacts = new ArrayList<Vec3>();
        for(LivingEntity target:selected) {
            if (!strikeCandidate(c,shape,target)) continue;
            Vec3 before = target.position();
            if (!cast.attack(target)) continue;
            hits++;
            if (groundBlast && target.position().distanceToSqr(before) <= .01) {
                contacts.add(before);
            }
            if(shape.push!=0 && target.isAlive() && strikeCandidate(c,shape,target)) {
                Vec3 radial=target.position().subtract(c.pos).multiply(1,0,1);
                Vec3 impulse=shape.push==2?new Vec3(0,number(c.skill, "vertical_speed"),0):radial.lengthSqr()<1e-8?Vec3.ZERO:
                        radial.normalize().scale(number(c.skill, "knockback_speed")).add(0,number(c.skill, "knockback_lift"),0);
                if(finite(impulse) && safeLivingMotion(c,target,impulse)) { target.push(impulse.x,impulse.y,impulse.z);target.hurtMarked=true; }
            }
            Vec3 visualFrom=c.skill.equals("dark_rain")?target.getBoundingBox().getCenter().add(0,number(c.skill, "origin_height"),0):
                    c.skill.equals("dark_shard_ring")?shardOrigin(c,target):c.eye;
            if(visualFrom!=null)CanonVfx.emit(c.p,c.skill,visualFrom,target.position().add(0,target.getBbHeight()*.5,0),CanonVfx.Phase.HIT,10,hits==1?area(c):0);
        }
        if(hits>0 && context(c) && (c.skill.equals("dark_wing_charge") || c.skill.equals("nitrogen_ram") || c.skill.equals("nitrogen_impact"))) {
            Vec3 next=strikeMotion(c);
            if(next!=null && safeMotion(c,next)){c.p.setDeltaMovement(next);c.p.hasImpulse=true;c.p.hurtMarked=true;
                if(c.skill.equals("nitrogen_impact"))c.p.fallDistance=0;}
        }
        if (!contacts.isEmpty() && context(c)) groundBlastEnvironment(c, shape, contacts, surplus);
        if(hits>0 && AbilityStorage.get(c.p)==c.state && c.state.learned(c.skill)) {c.state.addExperience(c.skill,.01);AbilityStorage.save(c.p);}
        return true;
    }
    private static void groundBlastEnvironment(Context c, StrikeShape shape, List<Vec3> contacts,
                                               List<LivingEntity> surplus) {
        var environment = cn.academy.port.highpower.environment.SkillEnvironment.begin(c.p, "aero_hand.aero_ground_blast", 4);
        for (LivingEntity candidate : surplus) {
            if (!strikeCandidate(c, shape, candidate) || contacts.stream().noneMatch(contact ->
                    contact.distanceToSqr(candidate.position()) <= 2.25)) continue;
            Vec3 outward = candidate.position().subtract(c.pos).multiply(1, 0, 1).normalize();
            if (outward.lengthSqr() > .99 && safeLivingMotion(c, candidate, outward.scale(.28).add(0, .4, 0))) {
                environment.push(candidate, outward, .28);
            }
        }
    }

    private static boolean strikeCandidate(Context c,StrikeShape shape,LivingEntity t) {
        if(!context(c) || t==null || t==c.p || !t.isAlive() || t.isRemoved() || t.isSpectator() || t.level()!=c.world
                || c.world.getEntity(t.getUUID())!=t || c.p.isAlliedTo(t) || t.isAlliedTo(c.p) || !c.p.hasLineOfSight(t)
                || !c.world.hasChunkAt(t.blockPosition()) || !c.world.mayInteract(c.p,t.blockPosition())
                || HighPowerCombat.unsupportedReason(t).isPresent() || shape.shell && !CanonSkillsCombatA.ownsNitrogenShell(c.p)
                || shape.ground && !grounded(c))return false;
        if(c.skill.equals("dark_wing_charge") && (!CanonSkillsCombatA.ownsDarkWings(c.p) || c.p.onGround())
                || c.skill.equals("nitrogen_impact") && (c.p.onGround() || movementVelocity(c).y>=-.2)
                || c.skill.equals("nitrogen_shock_ring") && c.p.onGround())return false;
        if((c.skill.equals("dark_wing_charge") || c.skill.equals("nitrogen_ram") || c.skill.equals("nitrogen_impact"))
                && (strikeMotion(c)==null || !safeMotion(c,strikeMotion(c))))return false;
        if (!(CanonTargetPolicy.hostile(t)
                || t instanceof Player other && AcademyConfig.ATTACK_PLAYERS.get() && c.p.server.isPvpAllowed() && c.p.canHarmPlayer(other)))return false;
        Vec3 delta=t.getBoundingBox().getCenter().subtract(c.eye);
        if(!finite(delta) || delta.lengthSqr()<1e-8 || delta.lengthSqr()>shape.radius*shape.radius)return false;
        if(shape.ground && Math.abs(t.getY()-c.pos.y)>2)return false;
        if(shape.cone>=0 && delta.normalize().dot(c.look.normalize())<shape.cone)return false;
        if(c.skill.equals("dark_rain") && !clearOrigin(c,t,t.getBoundingBox().getCenter().add(0,number(c.skill, "origin_height"),0)))return false;
        if(c.skill.equals("dark_shard_ring") && shardOrigin(c,t)==null)return false;
        if(c.skill.equals("dark_spear_fan") && !fanContains(c,t,shape.radius))return false;
        if(shape.line) {
            Vec3 end=c.eye.add(c.look.normalize().scale(shape.radius));
            if(t.getBoundingBox().inflate(.12).clip(c.eye,end).isEmpty() || !lineClear(c,t,end))return false;
        }
        if(shape.direct) {
            var hit=ClassicRaytrace.living(c.p,shape.radius,ClipContext.Fluid.NONE);
            if(!(hit instanceof EntityHitResult result) || result.getEntity()!=t)return false;
        }
        return true;
    }
    private static Vec3 strikeMotion(Context c) {
        if(c.skill.equals("nitrogen_impact")){Vec3 v=movementVelocity(c);return new Vec3(v.x,0,v.z);}
        Vec3 horizontal=new Vec3(c.look.x,0,c.look.z);if(horizontal.lengthSqr()<1e-8)return null;
        return horizontal.normalize().scale(number(c.skill, "horizontal_speed")).add(0,number(c.skill, "vertical_speed"),0);
    }
    public static java.util.List<Vec3> spearDirections(Vec3 look){
        var directions=new java.util.ArrayList<Vec3>();
        for(double angle:new double[]{-.25,0,.25}) {
            double cos=Math.cos(angle),sin=Math.sin(angle);
            directions.add(new Vec3(look.x*cos-look.z*sin,look.y,look.x*sin+look.z*cos).normalize());
        }
        return java.util.List.copyOf(directions);
    }
    private static boolean fanContains(Context c,LivingEntity target,double radius) {
        for(Vec3 direction:spearDirections(c.look)) {
            Vec3 end=c.eye.add(direction.scale(radius));
            if(target.getBoundingBox().inflate(.12).clip(c.eye,end).isPresent() && lineClear(c,target,end))return true;
        }
        return false;
    }
    private static Vec3 shardOrigin(Context c,LivingEntity target) {
        Vec3 side=new Vec3(-c.look.z,0,c.look.x);if(side.lengthSqr()<1e-8)return null;side=side.normalize().scale(number(c.skill, "origin_offset"));
        Vec3 center=c.pos.add(0,1.2,0);
        Vec3 one=center.add(side),two=center.subtract(side);
        return clearOrigin(c,target,one)?one:clearOrigin(c,target,two)?two:null;
    }
    private static boolean clearOrigin(Context c,LivingEntity target,Vec3 origin) {
        Vec3 end=target.getBoundingBox().getCenter();
        if(!finite(origin) || !finite(end) || !chunks(c.world,origin,end) || origin.distanceToSqr(end)>16*16)return false;
        int steps=Math.max(1,(int)Math.ceil(origin.distanceTo(end)*2));
        for(int i=0;i<=steps;i++) {
            BlockPos at=BlockPos.containing(origin.lerp(end,(double)i/steps));
            if(!c.world.hasChunkAt(at) || c.world.isOutsideBuildHeight(at) || !c.world.mayInteract(c.p,at)
                    || !c.world.getWorldBorder().isWithinBounds(at))return false;
        }
        if(c.world.clip(new ClipContext(origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.p)).getType()!=HitResult.Type.MISS)return false;
        for(LivingEntity other:c.world.getEntitiesOfClass(LivingEntity.class,new AABB(origin,end).inflate(.25),
                e->e!=c.p && e!=target && e.isAlive() && !e.isSpectator()))
            if(!(CanonTargetPolicy.hostile(other))
                    && other.getBoundingBox().inflate(.12).clip(origin,end).isPresent())return false;
        return true;
    }

    private static boolean lineClear(Context c,LivingEntity target,Vec3 end) {
        var hit=target.getBoundingBox().inflate(.12).clip(c.eye,end);if(hit.isEmpty())return false;
        var block=c.world.clip(new ClipContext(c.eye,hit.get(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.p));
        if(block.getType()!=HitResult.Type.MISS)return false;
        for(LivingEntity other:c.world.getEntitiesOfClass(LivingEntity.class,new AABB(c.eye,hit.get()).inflate(.25),
                entity->entity!=c.p && entity!=target && entity.isAlive() && !entity.isSpectator())) {
            if(other.getBoundingBox().inflate(.12).clip(c.eye,hit.get()).isPresent()
                    && !(CanonTargetPolicy.hostile(other)))return false;
        }
        return true;
    }
    private static boolean safeLivingMotion(Context c,LivingEntity target,Vec3 impulse) {
        AABB moved=target.getBoundingBox().move(impulse.scale(.5)).deflate(1e-7);
        if(!c.world.getWorldBorder().isWithinBounds(moved) || !c.world.noCollision(target,moved))return false;
        for(BlockPos at:BlockPos.betweenClosed(BlockPos.containing(moved.minX,moved.minY,moved.minZ),
                BlockPos.containing(moved.maxX,moved.maxY,moved.maxZ)))
            if(!c.world.hasChunkAt(at) || c.world.isOutsideBuildHeight(at) || !c.world.mayInteract(c.p,at))return false;
        return true;
    }

    /** Sustained physical defenses intercept only an actual crossing projectile. */
    private static boolean barrier(Context c) {
        int duration = integer(c.skill, "duration_ticks");
        int boosted = c.skill.startsWith("dark_") ? integer(c.skill, "boosted_duration_ticks") : duration;
        if(BARRIERS.containsKey(c.p) || c.tick<0 || c.tick>Long.MAX_VALUE-Math.max(duration, boosted) || HighPowerCombat.shield(c.p).signum()<=0
                || c.skill.startsWith("nitrogen_") && !CanonSkillsCombatA.ownsNitrogenShell(c.p))return false;
        if(!pay(c,3,140) || BARRIERS.containsKey(c.p) || HighPowerCombat.shield(c.p).signum()<=0
                || c.skill.startsWith("nitrogen_") && !CanonSkillsCombatA.ownsNitrogenShell(c.p))return false;
        // Dark Matter Crystal: +50% lifetime. Air Pressure Chip: one more intercept. Each costs one item, taken only after payment.
        int life=duration,cap=integer(c.skill, "target_cap");
        if(c.skill.startsWith("dark_")&&CanonAuxItems.consume(c.p,CanonAuxItems.DARK_MATTER_CRYSTAL))life=boosted;
        else if(c.skill.startsWith("aero_")&&CanonAuxItems.consume(c.p,CanonAuxItems.AERO_PRESSURE_CHIP))cap++;
        Barrier barrier=new Barrier(c,life,cap);BARRIERS.put(c.p,barrier);
        c.state.addExperience(c.skill,.01);AbilityStorage.save(c.p);
        Vec3 centre=frontalBarrier(c.skill)?c.eye.add(barrier.axis.scale(barrierDistance(c.skill))):c.eye;
        CanonVfx.emit(c.p,c.skill,c.eye,centre,CanonVfx.Phase.START,life,area(c));return true;
    }
    private static boolean validBarrier(Barrier b) {
        Context c=b.initial;ServerPlayer p=c.p;var state=AbilityStorage.get(p);
        return BARRIERS.get(p)==b && p.server.isSameThread() && p.serverLevel()==c.world && p.isAlive() && !p.isRemoved()
                && !p.isSpectator() && p.connection!=null && !p.hasDisconnected() && p.server.getPlayerList().getPlayer(p.getUUID())==p
                && state==c.state && state.level==c.level && state.presets.current()==c.preset && state.presets.revision()==c.revision
                && state.activated && state.overloadFine && !state.interfering && state.learned(c.skill)
                && state.presets.currentContains(c.skill) && SkillCatalog.enabled(state.category,c.skill)
                && c.world.getGameTime()<b.expires && b.intercepted<b.cap && HighPowerCombat.shield(p).signum()>0
                && (!c.skill.startsWith("nitrogen_") || CanonSkillsCombatA.ownsNitrogenShell(p));
    }
    private static void releaseBarrier(ServerPlayer p,Barrier b) {
        if(b!=null && BARRIERS.remove(p,b))CanonVfx.emit(p,b.initial.skill,p.position(),p.position(),CanonVfx.Phase.END,1);
    }
    @SubscribeEvent public static void tickBarrier(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        FallGuard fg=FALL_GUARDS.get(p);if(fg!=null && !validFall(fg))releaseFall(p,fg);
        AllyGuard ag=ALLY_GUARDS.get(p);if(ag!=null && !validAlly(ag))releaseAlly(p,ag);
        Barrier b=BARRIERS.get(p);if(b==null)return;
        if(!validBarrier(b)){releaseBarrier(p,b);return;}
        if(b.initial.skill.equals("dark_guard"))return; // Contact-only plate resolves in ProjectileImpactEvent.
        long now=b.initial.world.getGameTime();if(now<=b.lastTick)return;b.lastTick=now;
        Context c=b.initial;Vec3 center;
        boolean plane=frontalBarrier(c.skill);
        if(plane) center=(c.skill.equals("nitrogen_shield_line")?b.anchor:p.getEyePosition()).add(b.axis.scale(barrierDistance(c.skill)));
        else center=p.getEyePosition();
        if(!finite(center) || center.distanceToSqr(p.position())>64) {releaseBarrier(p,b);return;}
        AABB area=barrierBox(center,c.skill);
        int checked=0;
        for(AbstractArrow arrow:c.world.getEntitiesOfClass(AbstractArrow.class,area.inflate(2),a->barrierArrow(b,a))) {
            if(++checked>16)break;Vec3 from=arrow.position(),velocity=arrow.getDeltaMovement();
            if(area.contains(from))continue;
            var impact=area.clip(from,from.add(velocity));if(impact.isEmpty())continue;
            Vec3 next=velocity.scale(-.6),destination=impact.get().subtract(velocity.normalize().scale(.2));
            AABB moved=arrow.getBoundingBox().move(destination.subtract(from));Entity shooter=arrow.getOwner();
            if(!finite(destination) || !safeArrowPath(c,arrow,moved) || !HighPowerCombat.spendShieldFraction(p,new BigDecimal("0.01")))continue;
            if(!validBarrier(b) || !barrierArrow(b,arrow) || arrow.getOwner()!=shooter || !arrow.position().equals(from)
                    || !arrow.getDeltaMovement().equals(velocity) || !safeArrowPath(c,arrow,moved))continue;
            arrow.setPos(destination);arrow.setDeltaMovement(next);arrow.hasImpulse=true;arrow.hurtMarked=true;b.intercepted++;
            CanonVfx.emit(p,c.skill,center,impact.get(),CanonVfx.Phase.HIT,10);
        }
    }
    private static boolean barrierArrow(Barrier b,AbstractArrow arrow) {
        Context c=b.initial;ServerPlayer p=c.p;
        if((arrow.getClass()!=Arrow.class && arrow.getClass()!=SpectralArrow.class) || arrow.level()!=c.world
                || c.world.getEntity(arrow.getUUID())!=arrow || !arrow.isAlive() || arrow.isRemoved() || arrow.inGround
                || arrow.noPhysics || arrow.getOwner()==p || CanonSkillsCombatA.arrowLeased(arrow)
                || !finite(arrow.position()) || !finite(arrow.getDeltaMovement()) || arrow.getDeltaMovement().lengthSqr()<.01
                || arrow.getDeltaMovement().lengthSqr()>25)return false;
        if(arrow.getOwner() instanceof ServerPlayer shooter && (!AcademyConfig.ATTACK_PLAYERS.get()
                || !p.server.isPvpAllowed() || !p.canHarmPlayer(shooter)))return false;
        Vec3 relative=arrow.position().subtract(p.getEyePosition());
        return relative.lengthSqr()<=36 && arrow.getDeltaMovement().dot(relative)<0;
    }
    private static boolean fallGuard(Context c) {
        int duration = integer(c.skill, "duration_ticks");
        if(FALL_GUARDS.containsKey(c.p) || !CanonSkillsCombatA.ownsNitrogenShell(c.p) || c.tick>Long.MAX_VALUE-duration
                || !pay(c,2,120) || !CanonSkillsCombatA.ownsNitrogenShell(c.p))return false;
        FALL_GUARDS.put(c.p,new FallGuard(c,c.tick+duration));
        c.state.addExperience(c.skill,.01);AbilityStorage.save(c.p);
        CanonVfx.emit(c.p,c.skill,c.pos,c.pos,CanonVfx.Phase.START,duration);return true;
    }
    private static boolean validFall(FallGuard guard) {
        Context c=guard.initial;ServerPlayer p=c.p;var state=AbilityStorage.get(p);
        return FALL_GUARDS.get(p)==guard && p.server.isSameThread() && p.serverLevel()==c.world && p.isAlive() && !p.isRemoved()
                && !p.isSpectator() && !p.hasDisconnected() && p.connection!=null && p.server.getPlayerList().getPlayer(p.getUUID())==p
                && state==c.state && state.level==c.level && state.presets.current()==c.preset && state.presets.revision()==c.revision
                && state.activated && state.overloadFine && !state.interfering && state.learned(c.skill)
                && state.presets.currentContains(c.skill) && SkillCatalog.enabled(state.category,c.skill)
                && c.world.getGameTime()<guard.expires && CanonSkillsCombatA.ownsNitrogenShell(p);
    }
    private static void releaseFall(ServerPlayer p,FallGuard guard) {
        if(guard!=null && FALL_GUARDS.remove(p,guard))CanonVfx.emit(p,guard.initial.skill,p.position(),p.position(),CanonVfx.Phase.END,1);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void protectFall(LivingFallEvent event) {
        if(event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p))return;
        FallGuard guard=FALL_GUARDS.get(p);if(guard==null || !validFall(guard))return;
        float distance=event.getDistance(),multiplier=event.getDamageMultiplier();
        if(!Float.isFinite(distance) || distance<=3 || !Float.isFinite(multiplier) || multiplier<=0
                || !HighPowerCombat.spendShieldFraction(p,new BigDecimal("0.02")))return;
        if(event.isCanceled() || !validFall(guard) || event.getDistance()!=distance || event.getDamageMultiplier()!=multiplier)return;
        event.setDamageMultiplier(multiplier*(float) number(guard.initial.skill, "damage_factor"));
        CanonVfx.emit(p,guard.initial.skill,p.position(),p.position(),CanonVfx.Phase.HIT,10);releaseFall(p,guard);
    }
    private static boolean allyGuard(Context c) {
        int duration = integer(c.skill, "duration_ticks");
        int boosted = c.skill.equals("dark_wing_shelter") ? integer(c.skill, "boosted_duration_ticks") : duration;
        if(ALLY_GUARDS.containsKey(c.p) || c.tick>Long.MAX_VALUE-Math.max(duration, boosted) || HighPowerCombat.shield(c.p).signum()<=0
                || c.skill.equals("nitrogen_ally_guard") && !CanonSkillsCombatA.ownsNitrogenShell(c.p)
                || c.skill.equals("dark_wing_shelter") && !CanonSkillsCombatA.ownsDarkWings(c.p))return false;
        var hit=ClassicRaytrace.living(c.p,number(c.skill, "ally_reach"),ClipContext.Fluid.NONE);
        if(!(hit instanceof EntityHitResult e) || !(e.getEntity() instanceof ServerPlayer ally) || !consentingAlly(c,ally))return false;
        if(!pay(c,3,140) || !consentingAlly(c,ally) || ALLY_GUARDS.containsKey(c.p)
                || c.skill.equals("nitrogen_ally_guard") && !CanonSkillsCombatA.ownsNitrogenShell(c.p)
                || c.skill.equals("dark_wing_shelter") && !CanonSkillsCombatA.ownsDarkWings(c.p))return false;
        int life=c.skill.equals("dark_wing_shelter")&&CanonAuxItems.consume(c.p,CanonAuxItems.DARK_MATTER_CRYSTAL)?boosted:duration;
        ALLY_GUARDS.put(c.p,new AllyGuard(c,ally,c.tick+life));
        c.state.addExperience(c.skill,.01);AbilityStorage.save(c.p);
        CanonVfx.emit(c.p,c.skill,c.pos,ally.position(),CanonVfx.Phase.START,life);return true;
    }
    private static boolean validAlly(AllyGuard guard) {
        Context c=guard.initial;ServerPlayer p=c.p;var state=AbilityStorage.get(p);
        return ALLY_GUARDS.get(p)==guard && p.server.isSameThread() && p.serverLevel()==c.world && p.isAlive() && !p.isRemoved()
                && !p.isSpectator() && !p.hasDisconnected() && p.connection!=null && p.server.getPlayerList().getPlayer(p.getUUID())==p
                && state==c.state && state.level==c.level && state.presets.current()==c.preset && state.presets.revision()==c.revision
                && state.activated && state.overloadFine && !state.interfering && state.learned(c.skill)
                && state.presets.currentContains(c.skill) && SkillCatalog.enabled(state.category,c.skill)
                && c.world.getGameTime()<guard.expires && HighPowerCombat.shield(p).signum()>0 && consentingAlly(c,guard.ally)
                && (!c.skill.equals("nitrogen_ally_guard") || CanonSkillsCombatA.ownsNitrogenShell(p))
                && (!c.skill.equals("dark_wing_shelter") || CanonSkillsCombatA.ownsDarkWings(p));
    }
    private static void releaseAlly(ServerPlayer p,AllyGuard guard) {
        if(guard!=null && ALLY_GUARDS.remove(p,guard))CanonVfx.emit(p,guard.initial.skill,p.position(),p.position(),CanonVfx.Phase.END,1);
    }
    @SubscribeEvent(priority=EventPriority.NORMAL) public static void barrierImpact(ProjectileImpactEvent event) {
        if(event.isCanceled() || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer protectedPlayer) || !(event.getProjectile() instanceof AbstractArrow arrow))return;
        ServerPlayer owner=protectedPlayer;Barrier b=BARRIERS.get(owner);AllyGuard allyGuard=null;
        if(b==null || !b.initial.skill.equals("dark_guard") || !validBarrier(b)) {
            b=null;owner=null;
            for(AllyGuard candidate:List.copyOf(ALLY_GUARDS.values()))if(candidate.ally==protectedPlayer && validAlly(candidate)) {
                allyGuard=candidate;owner=candidate.initial.p;break;
            }
            if(owner==null) for(Barrier candidate:List.copyOf(BARRIERS.values())) {
                if(candidate.initial.skill.equals("nitrogen_bulwark") && validBarrier(candidate)
                        && consentingAlly(candidate.initial,protectedPlayer)) {b=candidate;owner=candidate.initial.p;break;}
            }
            if(owner==null)return;
        }
        Context c=b!=null?b.initial:allyGuard.initial;
        if(!allyArrow(c,protectedPlayer,arrow))return;
        Vec3 velocity=arrow.getDeltaMovement(),from=arrow.position();Entity shooter=arrow.getOwner();
        Vec3 destination=hit.getLocation().subtract(velocity.normalize().scale(.3));
        AABB moved=arrow.getBoundingBox().move(destination.subtract(from));
        BigDecimal fraction=b!=null && owner==protectedPlayer && c.skill.equals("dark_guard")
                ? new BigDecimal("0.01") : new BigDecimal("0.02");
        if(!finite(destination) || !safeArrowPath(c,arrow,moved) || !HighPowerCombat.spendShieldFraction(owner,fraction))return;
        if(event.isCanceled() || b!=null && !validBarrier(b) || allyGuard!=null && !validAlly(allyGuard)
                || !allyArrow(c,protectedPlayer,arrow) || arrow.getOwner()!=shooter || !arrow.position().equals(from)
                || !arrow.getDeltaMovement().equals(velocity) || !safeArrowPath(c,arrow,moved))return;
        event.setCanceled(true);arrow.setPos(destination);arrow.setDeltaMovement(velocity.scale(-.6));arrow.hasImpulse=true;arrow.hurtMarked=true;
        if(b!=null)b.intercepted++;
        CanonVfx.emit(owner,c.skill,owner.position(),hit.getLocation(),CanonVfx.Phase.HIT,10);
    }
    private static boolean allyArrow(Context c,ServerPlayer protectedPlayer,AbstractArrow arrow) {
        if((arrow.getClass()!=Arrow.class && arrow.getClass()!=SpectralArrow.class) || arrow.level()!=c.world
                || c.world.getEntity(arrow.getUUID())!=arrow || !arrow.isAlive() || arrow.isRemoved() || arrow.inGround || arrow.noPhysics
                || arrow.getOwner()==protectedPlayer || arrow.getOwner()==c.p || CanonSkillsCombatA.arrowLeased(arrow)
                || !finite(arrow.position()) || !finite(arrow.getDeltaMovement()) || arrow.getDeltaMovement().lengthSqr()<.01
                || arrow.getDeltaMovement().lengthSqr()>25 || !c.world.mayInteract(c.p,protectedPlayer.blockPosition()))return false;
        if(arrow.getOwner() instanceof ServerPlayer shooter && (!AcademyConfig.ATTACK_PLAYERS.get()
                || !c.p.server.isPvpAllowed() || !protectedPlayer.canHarmPlayer(shooter)))return false;
        Vec3 relative=arrow.position().subtract(protectedPlayer.getEyePosition());
        return relative.lengthSqr()<=36 && arrow.getDeltaMovement().dot(relative)<0;
    }

    /** A single immediate physical arrow deflection; no persistent arrow lease is stolen. */
    private static boolean arrowGuard(Context c) {
        if(c.skill.equals("nitrogen_guard") && !CanonSkillsCombatA.ownsNitrogenShell(c.p))return false;
        var arrows=new ArrayList<AbstractArrow>(c.world.getEntitiesOfClass(AbstractArrow.class,c.p.getBoundingBox().inflate(number(c.skill, "reach")),a->guardArrow(c,a)));
        if(arrows.isEmpty() || arrows.size()>32)return false;
        arrows.sort(Comparator.comparingDouble((AbstractArrow a)->c.p.distanceToSqr(a)).thenComparingInt(Entity::getId));
        AbstractArrow arrow=arrows.getFirst();Vec3 from=arrow.position(),velocity=arrow.getDeltaMovement();Entity shooter=arrow.getOwner();
        Vec3 next;
        if(c.skill.equals("aero_arrow_deflect")) {
            Vec3 side=new Vec3(-c.look.z,0,c.look.x);if(side.lengthSqr()<1e-8)return false;
            next=side.normalize().scale(Math.min(number(c.skill, "speed_cap"),Math.max(number(c.skill, "speed_floor"),velocity.length())));
        } else next=velocity.scale(-number(c.skill, "reflection_factor"));
        AABB swept=arrow.getBoundingBox().expandTowards(next).deflate(1e-7);
        if(!safeArrowPath(c,arrow,swept) || !pay(c,2,60) || !guardArrow(c,arrow) || arrow.getOwner()!=shooter
                || !arrow.position().equals(from) || !arrow.getDeltaMovement().equals(velocity)
                || c.skill.equals("nitrogen_guard") && !CanonSkillsCombatA.ownsNitrogenShell(c.p)
                || !safeArrowPath(c,arrow,swept) || !HighPowerCombat.spendShieldFraction(c.p,new BigDecimal("0.01"))) return false;
        if(!context(c) || !guardArrow(c,arrow) || arrow.getOwner()!=shooter || !arrow.position().equals(from)
                || !arrow.getDeltaMovement().equals(velocity) || !safeArrowPath(c,arrow,swept)
                || c.skill.equals("nitrogen_guard") && !CanonSkillsCombatA.ownsNitrogenShell(c.p))return false;
        arrow.setDeltaMovement(next);arrow.hasImpulse=true;arrow.hurtMarked=true;success(c,arrow.position());return true;
    }
    private static boolean guardArrow(Context c,AbstractArrow a) {
        if((a.getClass()!=Arrow.class && a.getClass()!=SpectralArrow.class) || !a.isAlive() || a.isRemoved()
                || a.level()!=c.world || c.world.getEntity(a.getUUID())!=a || a.inGround || a.noPhysics || a.getOwner()==c.p
                || CanonSkillsCombatA.arrowLeased(a) || !finite(a.position()) || !finite(a.getDeltaMovement()))return false;
        if(a.getOwner() instanceof ServerPlayer shooter && (!AcademyConfig.ATTACK_PLAYERS.get()
                || !c.p.server.isPvpAllowed() || !c.p.canHarmPlayer(shooter)))return false;
        Vec3 delta=a.position().subtract(c.eye),velocity=a.getDeltaMovement();
        double reach = number(c.skill, "reach");
        if(delta.lengthSqr()<1e-8 || delta.lengthSqr()>reach*reach || velocity.lengthSqr()<.01 || velocity.lengthSqr()>25
                || delta.normalize().dot(c.look.normalize())<.5 || velocity.dot(delta)>=0) return false;
        return c.world.clip(new ClipContext(c.eye,a.position(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.p)).getType()==HitResult.Type.MISS;
    }
    private static boolean safeArrowPath(Context c,AbstractArrow arrow,AABB moved) {
        if(!c.world.getWorldBorder().isWithinBounds(moved) || !c.world.noCollision(arrow,moved))return false;
        for(BlockPos at:BlockPos.betweenClosed(BlockPos.containing(moved.minX,moved.minY,moved.minZ),
                BlockPos.containing(moved.maxX,moved.maxY,moved.maxZ)))
            if(!c.world.hasChunkAt(at) || c.world.isOutsideBuildHeight(at) || !c.world.mayInteract(c.p,at))return false;
        return true;
    }

    /** An active shell may convert paid CP into a bounded, non-overflowing shield reinforcement. */
    private static boolean reinforce(Context c) {
        if(!CanonSkillsCombatA.ownsNitrogenShell(c.p))return false;
        BigDecimal before=HighPowerCombat.shield(c.p),maximum=new BigDecimal(HighPowerCombat.maxShieldText(c.p));
        if(before.compareTo(maximum)>=0 || !pay(c,6,200) || !CanonSkillsCombatA.ownsNitrogenShell(c.p)
                || HighPowerCombat.shield(c.p).compareTo(before)!=0) return false;
        HighPowerCombat.rechargeShieldFraction(c.p,BigDecimal.valueOf(number(c.skill, "shield_fraction")));
        if(HighPowerCombat.shield(c.p).compareTo(before)<=0 || HighPowerCombat.shield(c.p).compareTo(maximum)>0)return false;
        success(c,c.pos);return true;
    }

    private record ItemImpulse(ItemEntity item,Vec3 pos,Vec3 velocity,Vec3 next,AABB swept) {}
    private static boolean vortex(Context c) {
        double radius = number(c.skill, "radius");
        if(MagneticItemPull.itemsActive(c.p))return false;
        var candidates=new ArrayList<ItemEntity>(c.world.getEntitiesOfClass(ItemEntity.class,c.p.getBoundingBox().inflate(radius),
                i->eligibleItem(c,i,radius) && visibleItem(c,i)));
        if(candidates.isEmpty() || candidates.size()>32)return false;
        candidates.sort(Comparator.comparingDouble((ItemEntity i)->c.p.distanceToSqr(i)).thenComparingInt(Entity::getId));
        var plans=new ArrayList<ItemImpulse>();
        for(ItemEntity item:candidates) {
            Vec3 radial=item.position().subtract(c.pos).multiply(1,0,1);if(radial.lengthSqr()<.25)continue;
            Vec3 next=new Vec3(-radial.z,0,radial.x).normalize().scale(number(c.skill, "horizontal_speed")).add(0,number(c.skill, "vertical_speed"),0);
            AABB swept=item.getBoundingBox().expandTowards(next).deflate(1e-7);
            if(safeItemPath(c,item,swept))plans.add(new ItemImpulse(item,item.position(),item.getDeltaMovement(),next,swept));
            if(plans.size()==integer(c.skill, "target_cap"))break;
        }
        if(plans.isEmpty() || !pay(c,3,90) || MagneticItemPull.itemsActive(c.p))return false;
        for(ItemImpulse p:plans)if(!eligibleItem(c,p.item,radius) || !visibleItem(c,p.item) || !p.item.position().equals(p.pos) || !p.item.getDeltaMovement().equals(p.velocity)
                || !safeItemPath(c,p.item,p.swept))return false;
        for(ItemImpulse p:plans){p.item.setDeltaMovement(p.next);p.item.hasImpulse=true;p.item.hurtMarked=true;}
        success(c,c.pos);return true;
    }
    private static boolean visibleItem(Context c,ItemEntity item) {
        return c.world.clip(new ClipContext(c.eye,item.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,c.p)).getType()==HitResult.Type.MISS;
    }
    private record PushPlan(LivingEntity target,Vec3 pos,Vec3 velocity,Vec3 impulse) {}
    private static boolean ringPush(Context c) {
        double radius = number(c.skill, "radius");
        int targetCap = integer(c.skill, "target_cap");
        var candidates=new ArrayList<LivingEntity>(c.world.getEntitiesOfClass(LivingEntity.class,c.p.getBoundingBox().inflate(radius),
                t->impulseEligible(c,t)));
        if(candidates.isEmpty() || candidates.size()>32)return false;
        candidates.sort(Comparator.comparingDouble((LivingEntity e)->c.p.distanceToSqr(e)).thenComparingInt(Entity::getId));
        var plans=new ArrayList<PushPlan>();
        int cap=CanonAuxItems.inInventory(c.p,CanonAuxItems.AERO_PRESSURE_CHIP)?targetCap+1:targetCap; // Air Pressure Chip: one more target
        for(LivingEntity target:candidates) {
            Vec3 radial=target.position().subtract(c.pos).multiply(1,0,1);if(radial.lengthSqr()<1e-8)continue;
            Vec3 impulse=radial.normalize().scale(number(c.skill, "horizontal_speed")).add(0,number(c.skill, "vertical_speed"),0);
            if(safeLivingMotion(c,target,impulse))plans.add(new PushPlan(target,target.position(),target.getDeltaMovement(),impulse));
            if(plans.size()==cap)break;
        }
        if(plans.isEmpty() || !pay(c,3,80))return false;
        if(plans.size()>targetCap&&!CanonAuxItems.consume(c.p,CanonAuxItems.AERO_PRESSURE_CHIP))plans.remove(plans.size()-1);
        for(PushPlan plan:plans)if(!impulseEligible(c,plan.target) || !plan.target.position().equals(plan.pos)
                || !plan.target.getDeltaMovement().equals(plan.velocity) || !safeLivingMotion(c,plan.target,plan.impulse))return false;
        for(PushPlan plan:plans){plan.target.push(plan.impulse.x,plan.impulse.y,plan.impulse.z);plan.target.hurtMarked=true;}
        success(c,c.pos);return true;
    }
    private static boolean impulseEligible(Context c,LivingEntity target) {
        if(target==c.p || !target.isAlive() || target.isRemoved() || target.isSpectator() || target.level()!=c.world
                || c.world.getEntity(target.getUUID())!=target || c.p.distanceToSqr(target)>number(c.skill, "radius")*number(c.skill, "radius") || !c.p.hasLineOfSight(target)
                || c.p.isAlliedTo(target) || target.isAlliedTo(c.p) || !c.world.hasChunkAt(target.blockPosition())
                || !c.world.mayInteract(c.p,target.blockPosition()))return false;
        return CanonTargetPolicy.hostile(target);
    }

    /** Pull only an authenticated self-thrown block item, without mining or duplicating it. */
    private static boolean pullItem(Context c) {
        double reach = number(c.skill, "reach");
        boolean nitrogen=c.skill.equals("nitrogen_item_push");
        if (nitrogen && !CanonSkillsCombatA.ownsNitrogenShell(c.p))return false;
        if (MagneticItemPull.itemsActive(c.p) || !chunks(c.world,c.eye,c.eye.add(c.look.scale(reach)))) return false;
        Vec3 end=c.eye.add(c.look.scale(reach));
        var obstacle=c.world.clip(new ClipContext(c.eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.p));
        double best=obstacle.getType()==HitResult.Type.MISS?c.eye.distanceToSqr(end):c.eye.distanceToSqr(obstacle.getLocation());
        ItemEntity item=null;
        // A dropped item is a quarter-block target on the ground: accept a crosshair within half a block of it.
        for(ItemEntity candidate:c.world.getEntitiesOfClass(ItemEntity.class,new AABB(c.eye,end).inflate(.7),e->eligibleItem(c,e,reach))) {
            var hit=candidate.getBoundingBox().inflate(.5).clip(c.eye,end);
            if(hit.isPresent() && c.eye.distanceToSqr(hit.get())<best) {best=c.eye.distanceToSqr(hit.get());item=candidate;}
        }
        if(item==null) return false;
        Vec3 from=item.position(),velocity=item.getDeltaMovement(),toward=c.eye.subtract(item.getBoundingBox().getCenter());
        if(toward.lengthSqr()<.25 || !finite(toward))return false;
        // A pushed item was picked by looking down at it: keep the heading, lift it clear of the ground.
        Vec3 next=nitrogen?new Vec3(c.look.x,Math.max(c.look.y,.25),c.look.z).normalize().scale(number(c.skill, "speed")).add(0,number(c.skill, "vertical_speed"),0)
                :toward.normalize().scale(number(c.skill, "speed"));
        AABB moved=item.getBoundingBox().expandTowards(next).deflate(1e-7);
        if(!safeItemPath(c,item,moved) || !pay(c,2,60) || MagneticItemPull.itemsActive(c.p) || !eligibleItem(c,item,reach)
                || !item.position().equals(from) || !item.getDeltaMovement().equals(velocity) || !safeItemPath(c,item,moved)
                || nitrogen && !CanonSkillsCombatA.ownsNitrogenShell(c.p)) return false;
        item.setDeltaMovement(next);item.hasImpulse=true;item.hurtMarked=true;success(c,item.position());return true;
    }
    private static boolean eligibleItem(Context c,ItemEntity item,double reach) {
        if(item.getClass()!=ItemEntity.class || item.getType()!=EntityType.ITEM || item.level()!=c.world || item.isRemoved()
                || !item.isAlive() || item.noPhysics || item.isPassenger() || item.isVehicle() || item.isInvulnerable()
                || !MagneticItemProvenance.ownedBy(item,c.p.getUUID()) || c.world.getEntity(item.getUUID())!=item
                || item.getTarget()!=null && !c.p.getUUID().equals(item.getTarget()) || item.getItem().isEmpty()
                || !(item.getItem().getItem() instanceof BlockItem block) || block.getBlock() instanceof EntityBlock
                || item.getItem().getItem() instanceof BundleItem || item.getItem().has(DataComponents.CONTAINER)
                || item.getItem().has(DataComponents.BUNDLE_CONTENTS) || item.getItem().has(DataComponents.CONTAINER_LOOT)
                || item.getItem().has(DataComponents.BLOCK_ENTITY_DATA) || c.p.distanceToSqr(item)>reach*reach
                || !finite(item.position()) || !finite(item.getDeltaMovement())) return false;
        return c.world.hasChunkAt(item.blockPosition()) && c.world.mayInteract(c.p,item.blockPosition());
    }
    private static boolean safeItemPath(Context c,ItemEntity item,AABB moved) {
        if(!c.world.getWorldBorder().isWithinBounds(moved) || !c.world.noCollision(item,moved)) return false;
        for(BlockPos at:BlockPos.betweenClosed(BlockPos.containing(moved.minX,moved.minY,moved.minZ),
                BlockPos.containing(moved.maxX,moved.maxY,moved.maxZ)))
            if(!c.world.hasChunkAt(at) || c.world.isOutsideBuildHeight(at) || !c.world.mayInteract(c.p,at))return false;
        return true;
    }

    private static boolean shove(Context c) {
        if (c.skill.equals("nitrogen_shove") && !CanonSkillsCombatA.ownsNitrogenShell(c.p)) return false;
        double reach = reach(c);
        Mob target=aimedBody(c); if (target==null || c.p.distanceToSqr(target)>reach*reach) return false;
        Vec3 delta=target.position().subtract(c.pos).multiply(1,0,1);
        if (delta.lengthSqr()<1e-8) return false;
        Vec3 impulse=c.skill.equals("aero_crosswind") ? new Vec3(-delta.z,0,delta.x).normalize().scale(number(c.skill, "horizontal_speed"))
                : delta.normalize().scale(number(c.skill, "horizontal_speed"));
        Vec3 original=target.getDeltaMovement(), origin=target.position();
        AABB moved=target.getBoundingBox().move(impulse.scale(.5)).deflate(1e-7);
        if (!safeTargetMotion(c,target,moved) || !pay(c,2,60) || aimedBody(c)!=target || !target.position().equals(origin) || !target.getDeltaMovement().equals(original)
                || c.skill.equals("nitrogen_shove") && !CanonSkillsCombatA.ownsNitrogenShell(c.p)
                || !safeTargetMotion(c,target,moved)) return false;
        target.push(impulse.x,0,impulse.z);target.hurtMarked=true;success(c,target.position());return true;
    }
    private static boolean safeTargetMotion(Context c,Mob target,AABB moved) {
        if (!c.world.getWorldBorder().isWithinBounds(moved) || !c.world.noCollision(target,moved)) return false;
        for (BlockPos at:BlockPos.betweenClosed(BlockPos.containing(moved.minX,moved.minY,moved.minZ),
                BlockPos.containing(moved.maxX,moved.maxY,moved.maxZ)))
            if (!c.world.hasChunkAt(at) || c.world.isOutsideBuildHeight(at) || !c.world.mayInteract(c.p,at)) return false;
        return true;
    }

    private static boolean chunks(ServerLevel w,Vec3 a,Vec3 b) {
        if (!finite(a) || !finite(b))return false;
        int x0=BlockPos.containing(a).getX()>>4,x1=BlockPos.containing(b).getX()>>4;
        int z0=BlockPos.containing(a).getZ()>>4,z1=BlockPos.containing(b).getZ()>>4;
        for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)if(!w.hasChunk(x,z))return false;
        return true;
    }
    private static boolean finite(Vec3 v){return v!=null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);}

    /** Keep cleared mental targets suppressed for their lease instead of losing them to the next vanilla AI tick. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void suppressLeasedPursuit(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || MUTATING.contains(mob)) return;
        MindLease lease=MIND_LEASES.get(mob);
        var next=event.getNewAboutToBeSetTarget();
        if (lease!=null && !lease.routeOnly && lease.written!=null && mob.getTarget()==lease.written && next!=lease.written
                && (next==null || next==lease.owner.p) && validLease(lease)) {
            // A rewritten target holds for the whole lease: the mob's own goals may not drop it or snap back to the caster.
            // A write to some third target (another controller) still goes through and retires the lease as before.
            event.setCanceled(true); return;
        }
        if (lease==null || lease.routeOnly || lease.written!=null || lease.previous==null
                || mob.getTarget()!=null || event.getNewAboutToBeSetTarget()!=lease.previous || !validLease(lease)) return;
        // Other targets and another controller's writes remain untouched. Expiry,
        // consent withdrawal and owner changes still use the existing retirement path.
        event.setCanceled(true);
    }
}
