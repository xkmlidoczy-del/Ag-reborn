package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.RangedSkillRanges;
import cn.academy.port.highpower.CompatMode;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.environment.SkillBlastHelper;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Endpoint ground/wall detonation handler for explosive FX4 Canon skills when no direct entity lock occurred.
 *
 * <p>Validates full category/level/stance/resource prerequisites, traces to solid terrain within skill reach,
 * spends combat CP/cooldown/consumables, deals splash damage to nearby combat targets, emits HIT VFX, and
 * triggers {@link SkillBlastHelper#emitBlast} for shockwave and restorable terrain craters.
 */
public final class CanonEndpointBlast {
    public static final Set<String> EXPLOSIVE_SKILLS = Set.of(
            "dark_rain", "dark_matter_barrage", "dark_shard_salvo",
            "nitrogen_impact", "nitrogen_burst", "nitrogen_ground_wave", "nitrogen_shock_ring",
            "aero_ground_blast", "aero_barrage", "aero_pressure_lance",
            "hydro_eddy", "paper_array"
    );

    private static final Set<String> SELF_GROUND_FALLBACK = Set.of(
            "nitrogen_ground_wave", "nitrogen_shock_ring", "paper_array", "aero_ground_blast"
    );

    private CanonEndpointBlast() {}

    public static boolean isExplosive(String skill) {
        return skill != null && EXPLOSIVE_SKILLS.contains(skill);
    }

    public static boolean tryDetonate(ServerPlayer p, String skill) {
        if (!CompatMode.openGameplay()
                || p == null
                || !isExplosive(skill)
                || !p.server.isSameThread()
                || p.connection == null
                || p.hasDisconnected()
                || !p.isAlive()
                || p.isRemoved()
                || p.isSpectator()
                || AbilityConsumption.busy(p)
                || p.containerMenu != p.inventoryMenu
                || !HighPowerResources.ready(p)
                || HighPowerCombat.unsupportedReason(p).isPresent()) {
            return false;
        }

        var state = AbilityStorage.get(p);
        var def = CanonSkills.find(skill).orElse(null);
        if (def == null
                || !def.category().equals(state.category)
                || !state.activated
                || !state.overloadFine
                || state.interfering
                || !state.canUse(skill)
                || !state.learned(skill)
                || !state.presets.currentContains(skill)
                || !SkillCatalog.enabled(state.category, skill)
                || HighPowerResources.effectiveLevel(p) < def.level()) {
            return false;
        }

        if (skill.startsWith("nitrogen_") && !CanonSkillsCombatA.ownsNitrogenShell(p)) return false;
        if ((skill.equals("aero_barrage") || skill.equals("aero_ground_blast") || skill.equals("nitrogen_ground_wave"))
                && !grounded(p)) return false;
        if ((skill.equals("nitrogen_shock_ring") || skill.equals("nitrogen_impact")) && p.onGround()) return false;

        boolean flaskBacked = false;
        BlockPos water = null;
        if (skill.startsWith("hydro_")) {
            water = CanonWaterSkills.source(p);
            flaskBacked = water == null && CanonAuxItems.hasChargedWaterFlask(p);
            if (water == null && !flaskBacked) return false;
        }

        ItemStack heldSheet = CanonAuxItems.isSheet(p.getMainHandItem()) ? p.getMainHandItem() : p.getOffhandItem();
        int sheetCost = skill.equals("paper_array") ? CanonAuxItems.sheetCost(heldSheet, 6) : 0;
        if (sheetCost > 0 && (!CanonAuxItems.isSheet(heldSheet) || heldSheet.getCount() < sheetCost)) {
            return false;
        }

        double reach = resolveReach(skill);
        Vec3 eye = p.getEyePosition();
        Vec3 dir = ClassicRaytrace.direction(p).normalize();
        Vec3 rayEnd = eye.add(dir.scale(Math.min(32.0, Math.max(4.0, reach))));
        var world = p.serverLevel();
        var blockHit = world.clip(new ClipContext(eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));

        Vec3 impact = null;
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            impact = blockHit.getLocation();
        } else if (SELF_GROUND_FALLBACK.contains(skill)) {
            Vec3 probeStart = p.position().add(0, 0.6, 0);
            var downHit = world.clip(new ClipContext(probeStart, probeStart.add(0, -5.0, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (downHit.getType() == HitResult.Type.BLOCK) {
                impact = downHit.getLocation();
            }
        }
        if (impact == null || !world.hasChunkAt(BlockPos.containing(impact))) {
            return false;
        }

        double baseArc = HighPowerGrowth.values(p).arcCost();
        double cpCost = Math.max(1.0, baseArc * (def.level() >= 5 ? 3.5 : def.level() == 4 ? 2.5 : 1.8));
        int cooldown = def.level() >= 5 ? 90 : def.level() == 4 ? 65 : 45;
        if (!Double.isFinite(cpCost) || !HighPowerResources.trySpend(p, skill, cpCost, cooldown)) {
            return false;
        }

        if (flaskBacked && !CanonAuxItems.spendWaterFlaskCharge(p)) {
            return false;
        }
        boolean boosted = flaskBacked;
        if (sheetCost > 0) {
            boosted = CanonAuxItems.isOrigami(heldSheet);
            if (!p.getAbilities().instabuild) {
                heldSheet.shrink(sheetCost);
                p.getInventory().setChanged();
            }
        } else if ((skill.equals("nitrogen_ground_wave") || skill.equals("nitrogen_shock_ring"))
                && CanonAuxItems.inHands(p, CanonAuxItems.NITROGEN_CANISTER)) {
            boosted = CanonAuxItems.consume(p, CanonAuxItems.NITROGEN_CANISTER);
        } else if ((skill.equals("aero_barrage") || skill.equals("aero_ground_blast"))
                && CanonAuxItems.inHands(p, CanonAuxItems.AERO_PRESSURE_CHIP)) {
            boosted = CanonAuxItems.consume(p, CanonAuxItems.AERO_PRESSURE_CHIP);
        }

        int level = def.level();
        double splashRadius = level >= 5 ? 3.6 : level == 4 ? 3.0 : 2.4;
        if (boosted) splashRadius *= 1.15;
        double baseDamage = HighPowerGrowth.values(p).damage().doubleValue();
        double splashDamage = baseDamage * (level >= 5 ? 0.85 : level == 4 ? 0.72 : 0.60);
        String qualified = def.category() + "." + skill;

        SkillBlastHelper.splashEntities(p, qualified, impact, splashRadius, splashDamage, null, false);

        Vec3 visualOrigin = skill.equals("dark_rain") ? impact.add(0, 6.0, 0)
                : (water != null ? water.getCenter() : eye);
        CanonVfx.emit(p, skill, visualOrigin, impact, CanonVfx.Phase.HIT, level >= 5 ? 26 : 20, splashRadius, boosted);

        boolean allowTerrain = !skill.equals("hydro_eddy");
        float blastPower = level >= 5 ? 2.2f : level == 4 ? 1.8f : 1.4f;
        double craterRadius = level >= 5 ? 2.6 : level == 4 ? 2.0 : 1.5;
        SkillBlastHelper.emitBlast(p, qualified, level, impact, blastPower, craterRadius, allowTerrain);

        if (skill.equals("nitrogen_impact")) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.5, 0, v.z * 0.5);
            p.fallDistance = 0;
            p.hurtMarked = true;
        }

        state.addExperience(skill, 0.01);
        AbilityStorage.save(p);
        return true;
    }

    public static void emitHitBlast(ServerPlayer p, String category, String skill, int level, Vec3 impact) {
        if (!CompatMode.openGameplay() || !isExplosive(skill)) return;
        boolean allowTerrain = !skill.equals("hydro_eddy");
        float power = level >= 5 ? 2.2f : level == 4 ? 1.8f : 1.4f;
        double craterRadius = level >= 5 ? 2.5 : level == 4 ? 1.9 : 1.4;
        SkillBlastHelper.emitBlast(p, category + "." + skill, level, impact, power, craterRadius, allowTerrain);
    }

    private static boolean grounded(ServerPlayer p) {
        BlockPos floor = p.blockPosition().below();
        return p.onGround()
                && p.serverLevel().hasChunkAt(floor)
                && !p.serverLevel().getBlockState(floor).getCollisionShape(p.serverLevel(), floor).isEmpty();
    }

    private static double resolveReach(String skill) {
        var fields = cn.academy.port.core.SkillTuning.fields(skill);
        if (fields.containsKey("reach")) return number(skill, "reach");
        if (fields.containsKey("radius")) return Math.max(8.0, number(skill, "radius") * 1.5);
        return RangedSkillRanges.at(skill, 1, 12.0);
    }
}
