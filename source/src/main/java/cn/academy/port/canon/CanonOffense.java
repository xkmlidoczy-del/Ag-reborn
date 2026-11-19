package cn.academy.port.canon;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.highpower.CanonEmission;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real warm-up, fixed targets, one paid whole-cast budget and server-confirmed impact geometry. */
@EventBusSubscriber(modid = "academy")
public final class CanonOffense {
    private static final Map<ServerPlayer, Cast> CASTS = new IdentityHashMap<>();
    public record Receipt(UUID owner, String skill, String phase, long tick, int targets, double cp, double damage, double cpBefore, double cpAfter) {}
    public static java.util.function.Consumer<Receipt> observer = r -> {};

    private static final class Cast {
        final ServerPlayer player; final ServerLevel world; final AbilityProgress state; final CanonOffenseSkills.Spec spec;
        final int preset, level; final long revision, started; final Vec3 axis, areaCentre; final List<LivingEntity> targets;
        final CanonLiquidEntities.Shadow body; final ItemStack paper, paperSnapshot; final int sheets;
        final double effectiveReach;
        boolean committed;
        Cast(ServerPlayer p, CanonOffenseSkills.Spec spec, List<LivingEntity> targets, CanonLiquidEntities.Shadow body, ItemStack paper, int sheets, double effectiveReach) {
            player = p; world = p.serverLevel(); state = AbilityStorage.get(p); this.spec = spec; this.targets = List.copyOf(targets); this.body = body;
            this.paper = paper; paperSnapshot = paper.copy(); this.sheets = sheets; this.effectiveReach = effectiveReach;
            preset = state.presets.current(); revision = state.presets.revision();
            level = state.level; started = world.getGameTime(); axis = ClassicRaytrace.direction(p); areaCentre = aimPoint(p, spec, effectiveReach);
        }
        Vec3 origin() {
            if (body != null) return body.getBoundingBox().getCenter();
            Vec3 right = axis.cross(new Vec3(0, 1, 0)); if (right.lengthSqr() < 1e-8) right = new Vec3(-1, 0, 0); else right = right.normalize();
            double side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? .32 : -.32;
            return player.getEyePosition().add(0, -.35, 0).add(right.scale(side)).add(axis.scale(.48));
        }
        Vec3 centre() { return targets.isEmpty() ? areaCentre : targets.getFirst().getBoundingBox().getCenter(); }
        double cost() { return HighPowerGrowth.values(player).arcCost() * spec.cp(); }
    }

    private CanonOffense() {}
    public static boolean busy(ServerPlayer player) { return CASTS.containsKey(player); }
    public static String activeSkill(ServerPlayer player) { var c = CASTS.get(player); return c == null ? "" : c.spec.id(); }

    private static double effectiveReach(ServerPlayer p, CanonOffenseSkills.Spec spec) {
        if (spec.category().equals("mental_out") && CanonAuxItems.inHands(p, CanonAuxItems.MENTAL_REMOTE)) {
            return spec.reach() * 1.25;
        }
        return spec.reach();
    }

    public static boolean cast(ServerPlayer player, String id) {
        var spec = CanonOffenseSkills.find(id);
        if (spec == null || player == null || !AbilityStorage.get(player).canUse(id) || AbilityConsumption.busy(player) || !owner(player, spec) || CASTS.size() >= 64) return false;
        var body = spec.body() ? CanonLiquidSkills.controlledBody(player) : null;
        if (spec.body() && body == null) return false;
        int baseSheets = switch (id) { case "paper_fold_fan" -> 3; case "paper_piercing_lance" -> 2; case "paper_returning_wheel" -> 4; default -> 0; };
        ItemStack paper = CanonAuxItems.isSheet(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
        int sheets = baseSheets > 0 ? CanonAuxItems.sheetCost(paper, baseSheets) : 0;
        if (baseSheets > 0 && (!CanonAuxItems.isSheet(paper) || paper.getCount() < sheets)) return false;
        double reach = effectiveReach(player, spec);
        var targets = select(player, spec, body, reach); if (targets.isEmpty()) return false;
        var c = new Cast(player, spec, targets, body, paper, sheets, reach);
        if (HighPowerResources.availableCp(player) < c.cost() || !valid(c)) return false;
        CASTS.put(player, c);
        CanonVfx.emit(player, id, c.origin(), area(player, spec) > 0 ? c.areaCentre : visualPoint(c, targets.getFirst()), CanonVfx.Phase.START, spec.warmup() + 2, area(player, spec));
        observer.accept(new Receipt(player.getUUID(), id, "WARMUP", c.started, targets.size(), 0, 0, HighPowerResources.cp(player), HighPowerResources.cp(player)));
        return true;
    }

    private static boolean owner(ServerPlayer p, CanonOffenseSkills.Spec spec) {
        if (p.connection == null || p.hasDisconnected() || p.isRemoved() || !p.isAlive() || p.isSpectator() || !p.server.isSameThread()
                || p.server.getPlayerList().getPlayer(p.getUUID()) != p || !HighPowerResources.ready(p)
                || HighPowerCombat.unsupportedReason(p).isPresent()) return false;
        var s = AbilityStorage.get(p);
        return spec.category().equals(s.category) && s.level >= spec.level() && s.activated && s.overloadFine && !s.interfering
                && !s.consumptionInProgress() && s.learned(spec.id()) && s.presets.currentContains(spec.id()) && SkillCatalog.enabled(s.category, spec.id());
    }

    private static boolean valid(Cast c) {
        var p = c.player; var s = AbilityStorage.get(p);
        return owner(p, c.spec) && s == c.state && s.level == c.level && s.presets.current() == c.preset && s.presets.revision() == c.revision
                && p.serverLevel() == c.world && c.world.getGameTime() >= c.started && (c.committed || c.world.getGameTime() - c.started <= c.spec.warmup() + 2)
                && (c.body == null || CanonLiquidSkills.sameBody(p, c.body))
                && (c.sheets == 0 || c.committed || (p.getMainHandItem() == c.paper || p.getOffhandItem() == c.paper)
                    && ItemStack.matches(c.paper, c.paperSnapshot) && c.paper.getCount() >= c.sheets);
    }

    private static boolean target(ServerPlayer p, CanonOffenseSkills.Spec spec, LivingEntity t, double reach) {
        if (t == null || t == p || !t.isAlive() || t.isRemoved() || t.level() != p.level() || t.isSpectator() || t.isInvulnerable()
                || t.isAlliedTo(p) || p.isAlliedTo(t) || p.distanceToSqr(t) > reach * reach || !p.hasLineOfSight(t)
                || !p.serverLevel().hasChunkAt(t.blockPosition()) || !p.serverLevel().mayInteract(p, t.blockPosition())
                || HighPowerCombat.unsupportedReason(t).isPresent()) return false;
        if (t instanceof Player other) return AcademyConfig.ATTACK_PLAYERS.get() && p.server.isPvpAllowed() && p.canHarmPlayer(other);
        boolean directShape = spec.shape() == CanonOffenseSkills.Shape.POINT || spec.shape() == CanonOffenseSkills.Shape.AIR_POINT;
        return directShape ? CanonTargetPolicy.directTarget(p, t) : (t instanceof Mob && CanonTargetPolicy.hostile(t));
    }

    private static double radius(ServerPlayer p, CanonOffenseSkills.Spec spec) {
        return cn.academy.port.macro.MacroSkillRules.area(spec.id(), AbilityStorage.get(p).exp(spec.id()), spec.radius());
    }
    private static double area(ServerPlayer p, CanonOffenseSkills.Spec spec) {
        return spec.shape() == CanonOffenseSkills.Shape.AREA ? radius(p, spec) : 0;
    }

    private static LivingEntity aimed(ServerPlayer p, double reach) {
        var end = p.getEyePosition().add(ClassicRaytrace.direction(p).scale(reach));
        if (reach > 64) end = cn.academy.port.macro.MacroGeometry.loadedEnd(p.serverLevel(), p.getEyePosition(), end);
        var hit = ClassicRaytrace.perform(p, p.getEyePosition(), end, ClipContext.Fluid.NONE, e -> true);
        if (hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t) return t;
        return CanonSkillsCombatA.softAim(p, p.getEyePosition().distanceTo(end));
    }

    private static Vec3 aimPoint(ServerPlayer p, CanonOffenseSkills.Spec spec, double reach) {
        if (spec.category().equals("float_dial")) {
            Vec3 marker = CanonAuxItems.floatMarker(p);
            if (marker != null && p.position().distanceToSqr(marker) <= reach * reach) return marker;
        }
        var t = aimed(p, reach); if (target(p, spec, t, reach)) return t.getBoundingBox().getCenter();
        Vec3 from = p.getEyePosition(), end = from.add(ClassicRaytrace.direction(p).scale(reach));
        if (reach > 64) end = cn.academy.port.macro.MacroGeometry.loadedEnd(p.serverLevel(), from, end);
        var hit = p.serverLevel().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static List<LivingEntity> select(ServerPlayer p, CanonOffenseSkills.Spec spec, CanonLiquidEntities.Shadow body, double reach) {
        var world = p.serverLevel(); Vec3 eye = p.getEyePosition(), axis = ClassicRaytrace.direction(p);
        int cap = spec.targets() + (spec.category().equals("mental_out") && spec.targets() > 1 && CanonAuxItems.inHands(p, CanonAuxItems.MENTAL_REMOTE) ? 1 : 0);
        if (spec.shape() == CanonOffenseSkills.Shape.POINT || spec.shape() == CanonOffenseSkills.Shape.AIR_POINT) {
            var t = aimed(p, reach);
            return target(p, spec, t, reach) && (spec.shape() != CanonOffenseSkills.Shape.AIR_POINT || airborne(t)) ? List.of(t) : List.of();
        }
        if (spec.body()) {
            Vec3 origin = body.getBoundingBox().getCenter(); double radius = radius(p, spec);
            var list = new ArrayList<>(world.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(radius), t -> t != body && target(p, spec, t, reach)
                    && t.getBoundingBox().getCenter().distanceToSqr(origin) <= radius * radius
                    && world.clip(new ClipContext(origin, t.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body)).getType() == HitResult.Type.MISS));
            list.sort(Comparator.comparingDouble(t -> t.distanceToSqr(body))); return List.copyOf(list.subList(0, Math.min(cap, list.size())));
        }
        Vec3 end = eye.add(axis.scale(reach)); if (reach > 64) end = cn.academy.port.macro.MacroGeometry.loadedEnd(world, eye, end);
        var block = world.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        if (spec.shape() == CanonOffenseSkills.Shape.AREA) {
            Vec3 centre = aimPoint(p, spec, reach);
            double r = radius(p, spec);
            var list = new ArrayList<>(world.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(r), v -> target(p, spec, v, reach + r)
                    && v.getBoundingBox().getCenter().distanceToSqr(centre) <= r * r));
            list.sort(Comparator.comparingDouble(v -> v.getBoundingBox().getCenter().distanceToSqr(centre)));
            return List.copyOf(list.subList(0, Math.min(cap, list.size())));
        }
        final Vec3 finish = end; double length = eye.distanceTo(end);
        double r = radius(p, spec);
        var list = new ArrayList<>(world.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(r), t -> {
            if (!target(p, spec, t, reach)) return false;
            Vec3 delta = t.getBoundingBox().getCenter().subtract(eye); double along = delta.dot(axis);
            if (along <= 0 || along > length) return false;
            return spec.shape() == CanonOffenseSkills.Shape.LINE ? t.getBoundingBox().inflate(r).clip(eye, finish).isPresent()
                    : delta.lengthSqr() > 0 && delta.normalize().dot(axis) >= .82;
        }));
        list.sort(Comparator.comparingDouble(p::distanceToSqr)); return List.copyOf(list.subList(0, Math.min(cap, list.size())));
    }

    private static boolean geometry(Cast c, LivingEntity t) {
        if (!valid(c) || !target(c.player, c.spec, t, c.effectiveReach + (c.spec.shape() == CanonOffenseSkills.Shape.AREA ? radius(c.player, c.spec) : 0)) || !c.targets.contains(t)) return false;
        if (c.spec.body()) return c.body.getBoundingBox().getCenter().distanceToSqr(t.getBoundingBox().getCenter()) <= radius(c.player, c.spec) * radius(c.player, c.spec)
                && c.world.clip(new ClipContext(c.body.getBoundingBox().getCenter(), t.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.body)).getType() == HitResult.Type.MISS;
        if (c.spec.shape() == CanonOffenseSkills.Shape.AREA) return t.getBoundingBox().getCenter().distanceToSqr(c.areaCentre) <= radius(c.player, c.spec) * radius(c.player, c.spec);
        if (c.spec.shape() == CanonOffenseSkills.Shape.POINT || c.spec.shape() == CanonOffenseSkills.Shape.AIR_POINT) {
            if (c.spec.shape() == CanonOffenseSkills.Shape.AIR_POINT && !airborne(t)) return false;
            var hit = ClassicRaytrace.perform(c.player, c.origin(), t.getBoundingBox().getCenter(), ClipContext.Fluid.NONE, e -> true);
            if (hit instanceof EntityHitResult contact && contact.getEntity() == t) return true;
            var blockHit = c.world.clip(new ClipContext(c.origin(), t.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.player));
            return blockHit.getType() == HitResult.Type.MISS && aimed(c.player, c.effectiveReach) == t;
        }
        return select(c.player, c.spec, c.body, c.effectiveReach).contains(t);
    }

    private static boolean airborne(LivingEntity target) {
        return !target.onGround() && target.level().noCollision(target, target.getBoundingBox().move(0, -.08, 0));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (var c : List.copyOf(CASTS.values())) {
            if (c.player.server != event.getServer()) continue;
            if (!valid(c)) { end(c); continue; }
            if (c.world.getGameTime() - c.started < c.spec.warmup()) continue;
            CASTS.remove(c.player, c);
            var victims = c.targets.stream().filter(t -> geometry(c, t)).toList();
            if (victims.isEmpty()) { end(c); continue; }
            Vec3 origin = c.origin(); double cp = c.cost(), cpBefore = HighPowerResources.cp(c.player);
            var emission = CanonEmission.begin(c.player, c.spec.id(), cp, c.spec.cooldown(), victims, t -> geometry(c, t));
            if (emission == null) { end(c); continue; }
            if (c.sheets > 0) {
                if (!valid(c)) { emission.finish(); end(c); continue; }
                if (!c.player.getAbilities().instabuild) { c.paper.shrink(c.sheets); c.player.getInventory().setChanged(); }
            }
            c.committed = true;
            final double[] dealt = {0}; final int[] hits = {0};
            emission.forEach(t -> {
                var before = cn.academy.port.highpower.CityNpcPower.health(t);
                if (!emission.attack(t)) return;
                hits[0]++;
                dealt[0] += before.subtract(cn.academy.port.highpower.CityNpcPower.health(t)).max(java.math.BigDecimal.ZERO).doubleValue();
                affect(c, t);
                CanonVfx.emit(c.player, c.spec.id(), origin, hits[0] == 1 && area(c.player, c.spec) > 0 ? c.areaCentre : visualPoint(c, t), CanonVfx.Phase.HIT, 26, hits[0] == 1 ? area(c.player, c.spec) : 0,
                        c.effectiveReach > c.spec.reach() || c.sheets > 0 && CanonAuxItems.isOrigami(c.paperSnapshot));
            }, () -> {
                if (hits[0] > 0 && valid(c)) { c.state.addExperience(c.spec.id(), .01); AbilityStorage.save(c.player); }
                else CanonVfx.emit(c.player, c.spec.id(), origin, c.centre(), CanonVfx.Phase.END, 8);
                observer.accept(new Receipt(c.player.getUUID(), c.spec.id(), "RESOLVE", c.world.getGameTime(), hits[0], cp, dealt[0], cpBefore, HighPowerResources.cp(c.player)));
            });
        }
    }

    private static void affect(Cast c, LivingEntity t) {
        if (cn.academy.port.highpower.PlayerShieldArmor.holding(t)) return;
        String id = c.spec.id();
        if (c.spec.category().equals("mental_out")) {
            int time = cn.academy.port.core.SkillTuning.fields(id).containsKey("control_ticks") ? cn.academy.port.core.SkillTuning.integer(id, "control_ticks") : 0;
            if (time > 0 && t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, time, 1, false, true), c.player))
                cn.academy.port.macro.BeamImpactVfx.contact(c.player, id, c.origin(), visualPoint(c, t), cn.academy.port.macro.BeamImpactVfx.Outcome.CONTROL);
            if (id.equals("mental_feedback_spike") && t instanceof Mob m) { m.getNavigation().stop(); }
        } else if (c.spec.category().equals("float_dial")) {
            var v = t.getDeltaMovement(); double vertical = id.equals("float_reversal_drop") || id.equals("float_pressure_drop") || id.equals("float_collapse_field") ? -.42 : .34;
            if (id.equals("float_buoyant_strike")) t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0, false, true), c.player);
            if (vertical < 0) t.removeEffect(MobEffects.LEVITATION);
            t.setDeltaMovement(v.x * .55, vertical, v.z * .55); t.fallDistance = 0; t.hasImpulse = true; t.hurtMarked = true;
        } else if (id.equals("aero_pressure_bullet")) {
            t.knockback(.23, -c.axis.x, -c.axis.z);
        }
    }

    private static Vec3 visualPoint(Cast c, LivingEntity target) {
        return c.spec.category().equals("mental_out") ? target.getEyePosition() : target.getBoundingBox().getCenter();
    }

    private static void end(Cast c) {
        CASTS.remove(c.player, c);
        CanonVfx.emit(c.player, c.spec.id(), c.origin(), c.centre(), CanonVfx.Phase.END, 8);
        double balance = HighPowerResources.cp(c.player);
        observer.accept(new Receipt(c.player.getUUID(), c.spec.id(), "CANCEL", c.world.getGameTime(), 0, 0, 0, balance, balance));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer p) { var c = CASTS.remove(p); if (c != null) end(c); } }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { CASTS.entrySet().removeIf(e -> e.getKey().server == event.getServer()); }
}
