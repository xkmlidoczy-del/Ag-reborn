package cn.academy.port.canon;

import cn.academy.port.core.RangedSkillRanges;
import cn.academy.port.core.SkillTuning;
import cn.academy.port.skill.ClassicRaytrace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Best-effort reading of one {@link CanonCastHints} requirement against the caster's current situation, so a refused press can
 * name the requirement that is actually missing. Presentation only: null means "cannot tell from here", never a cast decision.
 */
final class CanonNeedCheck {
    private CanonNeedCheck() {}

    static Boolean met(ServerPlayer p, String skill, String need) {
        String kind = CanonCastHints.kind(need);
        double r = range(need);
        return switch (kind) {
            case "aim_hostile" -> aimed(p, r) != null;
            case "aim_mob" -> {
                var hit = ClassicRaytrace.living(p, r > 0 ? r : aimReach(skill, 24), ClipContext.Fluid.NONE);
                yield hit instanceof EntityHitResult e && e.getEntity() instanceof Mob;
            }
            case "hostiles_near" -> !hostiles(p, r, false).isEmpty();
            case "hostiles_ahead" -> !hostiles(p, r, true).isEmpty();
            case "ground" -> p.onGround();
            case "airborne" -> !p.onGround() && !p.isInWater();
            case "falling" -> !p.onGround() && !p.isInWater() && p.fallDistance > 0;
            case "dry" -> !p.isInWater();
            case "in_water" -> p.isInWater();
            case "on_fire" -> p.isOnFire();
            case "water_near" -> waterNear(p, r > 0 ? r : 5);
            case "shell" -> CanonSkillsCombatA.ownsNitrogenShell(p);
            case "wings" -> CanonSkillsCombatA.ownsDarkWings(p);
            case "survival" -> !p.isCreative() && !p.isSpectator();
            case "thrown_item" -> !p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(r > 0 ? r : 8),
                    e -> e.getOwner() == p || p.getUUID().equals(thrower(e))).isEmpty();
            case "incoming_arrow" -> !p.serverLevel().getEntitiesOfClass(AbstractArrow.class, p.getBoundingBox().inflate(r > 0 ? r : 6),
                    a -> a.getOwner() != p && a.getDeltaMovement().dot(p.position().subtract(a.position())) > 0).isEmpty();
            case "target_on_fire" -> { LivingEntity t = aimed(p, aimReach(skill, 6)); yield t == null ? null : t.isOnFire(); }
            case "target_in_water" -> { LivingEntity t = aimed(p, aimReach(skill, 24)); yield t == null ? null : t.isInWater(); }
            case "navigating" -> { LivingEntity t = aimed(p, 24); if (t == null) t = first(hostiles(p, 12, false));
                yield t instanceof Mob m ? m.getNavigation().isInProgress() : null; }
            case "pursuing" -> hostiles(p, r > 0 ? r : 24, false).stream().anyMatch(e -> e instanceof Mob m && m.getTarget() == p);
            case "not_on_you" -> { LivingEntity t = aimed(p, 24); yield t instanceof Mob m ? m.getTarget() != p : null; }
            case "second_hostile", "three_hostiles", "four_hostiles" -> {
                var radius = SkillTuning.fields(skill).get("radius");
                double distance = r > 0 ? r : radius == null ? 16 : radius.doubleValue();
                int count = kind.equals("second_hostile") ? 2 : kind.equals("three_hostiles") ? 3 : 4;
                yield hostiles(p, distance, false).size() >= count;
            }
            case "paper" -> p.getInventory().countItem(Items.PAPER) >= Math.max(1, (int) r);
            case "liquid_body" -> CanonLiquidSkills.controlledBody(p) != null;
            default -> null;
        };
    }

    private static double range(String need) {
        int i = need.indexOf(':');
        if (i < 0) return 0;
        try { return Double.parseDouble(need.substring(i + 1)); } catch (NumberFormatException e) { return 0; }
    }

    private static java.util.UUID thrower(ItemEntity e) {
        var owner = e.getOwner(); return owner == null ? null : owner.getUUID();
    }

    private static LivingEntity aimed(ServerPlayer p, double range) {
        var hit = ClassicRaytrace.living(p, range, ClipContext.Fluid.NONE);
        return hit instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity t && t != p && CanonTargetPolicy.hostile(t) ? t : null;
    }

    private static java.util.List<LivingEntity> hostiles(ServerPlayer p, double range, boolean ahead) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> e != p && e.isAlive()
                && CanonTargetPolicy.hostile(e) && e.distanceTo(p) <= range && p.hasLineOfSight(e)
                && (!ahead || e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) > .5));
    }

    private static LivingEntity first(java.util.List<LivingEntity> list) { return list.isEmpty() ? null : list.getFirst(); }

    private static double aimReach(String skill, double fallback) {
        var reach = SkillTuning.fields(skill).get("reach");
        return reach == null ? RangedSkillRanges.at(skill, 1, fallback) : reach.doubleValue();
    }

    private static boolean waterNear(ServerPlayer p, double radius) {
        int cells = (int) Math.ceil(radius);
        BlockPos at = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-cells, -cells, -cells), at.offset(cells, cells, cells))) {
            if (!p.serverLevel().hasChunkAt(q) || q.getCenter().distanceToSqr(p.position()) > radius * radius) continue;
            var fluid = p.serverLevel().getFluidState(q);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()) return true;
        }
        return false;
    }
}
