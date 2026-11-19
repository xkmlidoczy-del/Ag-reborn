package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.RangedSkillRanges;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Player key-press Dry-Fire presentation for direct/area offensive Canon skills.
 *
 * <p>Direct programmatic {@code cast(player, skill)} calls remain strict target-bound transactions for GameTests,
 * while real client slot presses in empty air spend a light 35% CP / short cooldown pulse and emit the full
 * {@link CanonVfx} slash, lance, or burst along the aim ray so players can preview and swing abilities freely.
 */
public final class CanonDryFire {
    private static final Set<String> CORE_OFFENSIVE = Set.of(
            "dark_blade", "nitrogen_punch", "aero_barrage",
            "hydro_whip", "paper_blade", "paper_array"
    );

    private static final Set<String> EXPANSION_OFFENSIVE = Set.of(
            "dark_shard", "dark_sweep", "dark_spread", "dark_pierce", "dark_shard_salvo",
            "dark_rain", "dark_wing_charge", "dark_spear_fan", "dark_shard_ring", "dark_matter_barrage",
            "nitrogen_burst", "nitrogen_uppercut", "nitrogen_sweep", "nitrogen_cone", "nitrogen_ground_wave",
            "nitrogen_ram", "nitrogen_shock_ring", "nitrogen_impact",
            "aero_gust", "aero_cone", "aero_ground_blast", "aero_pressure_lance", "aero_squall",
            "hydro_pressure_jet", "hydro_fan", "hydro_eddy"
    );

    private CanonDryFire() {}

    public static boolean tryDryFire(ServerPlayer p, String skill) {
        if (p == null || skill == null || !p.server.isSameThread() || p.connection == null
                || p.hasDisconnected() || !p.isAlive() || p.isRemoved() || p.isSpectator()
                || AbilityConsumption.busy(p) || p.containerMenu != p.inventoryMenu
                || !HighPowerResources.ready(p) || HighPowerCombat.unsupportedReason(p).isPresent()) {
            return false;
        }
        var state = AbilityStorage.get(p);
        var def = CanonSkills.find(skill).orElse(null);
        if (def == null || !def.category().equals(state.category) || !state.activated || !state.overloadFine
                || state.interfering || !state.canUse(skill) || !state.learned(skill)
                || !state.presets.currentContains(skill) || !SkillCatalog.enabled(state.category, skill)
                || HighPowerResources.effectiveLevel(p) < def.level()) {
            return false;
        }

        var offenseSpec = CanonOffenseSkills.find(skill);
        if (offenseSpec == null && !CORE_OFFENSIVE.contains(skill) && !EXPANSION_OFFENSIVE.contains(skill)) {
            return false;
        }

        // Respect physical/state prerequisites before emitting a dry-fire swing; if a hard stance/material is missing,
        // return false so CanonCastHints explains the missing stance/material to the player.
        if (skill.startsWith("nitrogen_") && !CanonSkillsCombatA.ownsNitrogenShell(p)) return false;
        if (skill.equals("dark_wing_charge") && (!CanonSkillsCombatA.ownsDarkWings(p) || p.onGround())) return false;
        if ((skill.equals("aero_barrage") || skill.equals("aero_ground_blast")
                || skill.equals("nitrogen_ground_wave") || skill.equals("nitrogen_ram")) && !grounded(p)) return false;
        if ((skill.equals("nitrogen_shock_ring") || skill.equals("nitrogen_impact")) && p.onGround()) return false;
        if (offenseSpec != null && offenseSpec.body() && CanonLiquidSkills.controlledBody(p) == null) return false;

        if (skill.startsWith("hydro_")) {
            BlockPos water = CanonWaterSkills.source(p);
            boolean flask = water == null && CanonAuxItems.hasChargedWaterFlask(p);
            if (water == null && !flask) return false;
        }

        int baseSheets = switch (skill) {
            case "paper_blade" -> 1;
            case "paper_piercing_lance" -> 2;
            case "paper_fold_fan" -> 3;
            case "paper_returning_wheel" -> 4;
            case "paper_array" -> 6;
            default -> 0;
        };
        ItemStack heldSheet = CanonAuxItems.isSheet(p.getMainHandItem()) ? p.getMainHandItem() : p.getOffhandItem();
        int sheetCost = baseSheets > 0 ? Math.max(1, CanonAuxItems.sheetCost(heldSheet, baseSheets) / 2) : 0;
        if (baseSheets > 0 && (!CanonAuxItems.isSheet(heldSheet) || heldSheet.getCount() < sheetCost)) {
            return false;
        }

        double baseArc = HighPowerGrowth.values(p).arcCost();
        double dryCost = Math.max(1.0, baseArc * (def.level() >= 4 ? 0.65 : 0.35));
        int dryCooldown = def.level() >= 5 ? 24 : def.level() == 4 ? 18 : 12;
        if (!Double.isFinite(dryCost) || !HighPowerResources.trySpend(p, skill, dryCost, dryCooldown)) {
            return false;
        }

        if (baseSheets > 0 && !p.getAbilities().instabuild) {
            heldSheet.shrink(sheetCost);
            p.getInventory().setChanged();
        }
        if (skill.startsWith("hydro_") && CanonWaterSkills.source(p) == null) {
            CanonAuxItems.spendWaterFlaskCharge(p);
        }

        double reach = resolveReach(skill, offenseSpec);
        double area = resolveArea(p, skill, offenseSpec);
        Vec3 eye = p.getEyePosition();
        Vec3 dir = ClassicRaytrace.direction(p).normalize();
        Vec3 rawEnd = eye.add(dir.scale(Math.min(48.0, Math.max(2.5, reach))));
        var hit = p.serverLevel().clip(new ClipContext(eye, rawEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? rawEnd : hit.getLocation();
        Vec3 from = area > 0 && offenseSpec == null ? p.position() : eye;
        if (area > 0 && offenseSpec == null && !skill.equals("aero_barrage") && !skill.startsWith("dark_spear")) {
            end = p.position().add(dir.scale(Math.min(4.0, area * 0.35)));
        }

        int vfxTicks = def.level() >= 5 ? 24 : def.level() == 4 ? 20 : 14;
        CanonVfx.emit(p, skill, from, end, CanonVfx.Phase.HIT, vfxTicks, area);
        playDrySound(p, def.category(), def.level());
        return true;
    }

    private static boolean grounded(ServerPlayer p) {
        BlockPos floor = p.blockPosition().below();
        return p.onGround() && p.serverLevel().hasChunkAt(floor)
                && !p.serverLevel().getBlockState(floor).getCollisionShape(p.serverLevel(), floor).isEmpty();
    }

    private static double resolveReach(String skill, CanonOffenseSkills.Spec offenseSpec) {
        if (offenseSpec != null) return offenseSpec.reach();
        var fields = cn.academy.port.core.SkillTuning.fields(skill);
        if (fields.containsKey("reach")) return number(skill, "reach");
        if (fields.containsKey("radius")) return number(skill, "radius");
        return RangedSkillRanges.at(skill, 1, 8.0);
    }

    private static double resolveArea(ServerPlayer p, String skill, CanonOffenseSkills.Spec offenseSpec) {
        double mastery = AbilityStorage.get(p).exp(skill);
        if (offenseSpec != null) {
            return offenseSpec.shape() == CanonOffenseSkills.Shape.AREA
                    ? cn.academy.port.macro.MacroSkillRules.area(skill, mastery, offenseSpec.radius()) : 0;
        }
        var shape = CanonExpansionCombatA.shape(skill);
        if (shape != null && shape.cone() < 0 && !shape.direct() && !shape.line()) {
            return cn.academy.port.macro.MacroSkillRules.area(skill, mastery, shape.radius());
        }
        if (skill.equals("hydro_eddy") || skill.equals("paper_array")) {
            return cn.academy.port.macro.MacroSkillRules.area(skill, mastery, number(skill, "radius"));
        }
        return 0;
    }

    private static void playDrySound(ServerPlayer p, String category, int level) {
        float pitch = level >= 4 ? 0.88f : 1.18f;
        var sound = switch (category) {
            case "dark_matter" -> SoundEvents.AMETHYST_CLUSTER_BREAK;
            case "nitrogen_armor" -> SoundEvents.GLASS_HIT;
            case "aero_hand" -> SoundEvents.BREEZE_WIND_CHARGE_BURST.value();
            case "hydro_hand" -> SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
            case "paper_psychokinesis" -> SoundEvents.BOOK_PAGE_TURN;
            case "mental_out" -> SoundEvents.BEACON_ACTIVATE;
            case "float_dial" -> SoundEvents.SHULKER_BULLET_HIT;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
        p.serverLevel().playSound(null, p.blockPosition(), sound, SoundSource.PLAYERS, 0.55f, pitch);
    }
}
