package cn.academy.port.canon;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.RangedSkillRanges;
import cn.academy.port.core.SkillTuning;
import java.math.BigDecimal;
import cn.academy.port.highpower.HighPowerCombat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Canon casts used to fail silently, so players read missing preconditions as broken skills.
 * Each skill lists the situational requirements its controller checks; a refused key press reports them on the action bar.
 * Presentation only: the controllers remain the authority on what actually casts.
 */
public final class CanonCastHints {
    /** Lang suffixes under ac.canon.need.*; "paper:N" carries the sheet count from the skill's own cost. */
    private static final Map<String, List<String>> NEEDS = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private CanonCastHints() {}

    private static void put(String needs, String... skills) {
        for (String skill : skills) NEEDS.put(skill, List.of(needs.split(",")));
    }
    static {
        // Ranges are the controllers' own reach or radius in blocks, so the hint matches what the skill can touch.
        // Mental Out
        put("aim_hostile:24", "mental_probe", "mental_disrupt");
        put("aim_hostile:16", "mental_intent_scan", "mental_target_sense");
        put("aim_hostile:16,not_on_you", "mental_attention_shift");
        put("hostiles_near:16", "mental_life_scan");
        put("hostiles_near:12", "mental_silence_field");
        put("hostiles_near:12,three_hostiles", "mental_conflict_ring");
        put("hostiles_near:12,four_hostiles", "mental_crossfire");
        put("pursuing", "mental_hostile_scan", "mental_pursuit_break", "mental_pursuit_field");
        put("ally_sneak,ally_pursued", "mental_crowd_detach");
        put("aim_hostile:16,navigating", "mental_focus_break", "mental_route_interrupt");
        put("aim_hostile:16,pursuing", "mental_disengage");
        put("hostiles_near:12,navigating", "mental_panic_field", "mental_relay_interference");
        put("aim_hostile:16,second_hostile", "mental_interpose", "mental_redirect", "mental_conflict", "mental_suppress_pair");
        put("aim_hostile:16,two_pursuing", "mental_lure_pair");
        put("aim_hostile:24,second_hostile", "mental_out_crowd");
        put("aim_mob", "mental_puppet");
        // Dark Matter
        put("aim_hostile:6", "dark_blade");
        put("aim_hostile:12", "dark_shard");
        put("hostiles_ahead:4", "dark_sweep");
        put("hostiles_ahead:8", "dark_spread");
        put("hostiles_ahead:14", "dark_pierce");
        put("hostiles_ahead:12", "dark_spear_fan");
        put("hostiles_ahead:10", "dark_matter_barrage");
        put("hostiles_near:10", "dark_rain", "dark_shard_salvo");
        put("hostiles_near:8", "dark_shard_ring");
        put("incoming_arrow", "dark_feather_guard");
        put("ground", "dark_step");
        put("airborne", "dark_lift");
        put("shield", "dark_guard", "dark_shard_screen", "dark_panel_dome", "dark_construct");
        put("thrown_item", "dark_shard_return", "dark_shard_orbit");
        put("survival", "dark_wings");
        put("wings,moving", "dark_wing_brake");
        put("wings,falling", "dark_wing_rescue");
        put("wings,airborne,aim_hostile:2.5", "dark_wing_charge");
        put("wings,shield,ally_sneak", "dark_wing_shelter");
        put("crystal", "dark_synthesis");
        // Nitrogen Armor
        put("shield,dry", "nitrogen_armor");
        put("shell,incoming_arrow", "nitrogen_guard");
        put("shell,ground", "nitrogen_dash", "nitrogen_rocket");
        put("shell,moving", "nitrogen_brake");
        put("shell,falling", "nitrogen_buoy", "nitrogen_fall_guard");
        put("shell,aim_hostile:4", "nitrogen_punch", "nitrogen_burst", "nitrogen_shove");
        put("shell,aim_hostile:3.5", "nitrogen_uppercut");
        put("shell,hostiles_ahead:4", "nitrogen_sweep");
        put("shell,hostiles_ahead:7", "nitrogen_cone");
        put("shell,ground,aim_hostile:2.5", "nitrogen_ram");
        put("shell,ground,hostiles_near:5", "nitrogen_ground_wave");
        put("shell,airborne,hostiles_near:6", "nitrogen_shock_ring");
        put("shell,falling,aim_hostile:3", "nitrogen_impact");
        put("shell,shield", "nitrogen_plate", "nitrogen_shield_line", "nitrogen_bulwark", "nitrogen_dome");
        put("shell,shield_missing", "nitrogen_shell_reinforce");
        put("shell,thrown_item", "nitrogen_item_push");
        put("shield,ally_sneak", "nitrogen_ally_guard");
        // Aero Hand
        put("ground,dry", "aero_jet");
        put("ground,dry,hostiles_ahead:12", "aero_barrage");
        put("ground", "aero_updraft", "aero_side_step", "aero_backstep");
        put("ground,hostiles_ahead:8", "aero_ground_blast");
        put("moving", "aero_brake");
        put("falling", "aero_fall_brake", "aero_double_jet");
        put("airborne", "aero_vertical_jet");
        put("airborne,moving,turn_view", "aero_airborne_turn");
        put("aim_hostile:4", "aero_push", "aero_crosswind");
        put("aim_hostile:5", "aero_gust");
        put("hostiles_ahead:8", "aero_cone");
        put("hostiles_ahead:14", "aero_pressure_lance");
        put("hostiles_near:4", "aero_ring_push");
        put("hostiles_near:8", "aero_squall");
        put("thrown_item", "aero_item_pull", "aero_launch", "aero_vortex");
        put("incoming_arrow", "aero_arrow_deflect");
        put("shield", "aero_pressure_wall", "aero_air_curtain");
        // Hydro Hand
        put("water_near,aim_hostile:16", "hydro_whip");
        put("water_aim", "hydro_transfer");
        put("water_near,fire_block", "hydro_extinguish", "hydro_campfire_out", "hydro_candle_out");
        put("water_near,farmland", "hydro_irrigate");
        put("water_near", "hydro_shield");
        put("water_near,on_fire", "hydro_douse_self");
        put("water_near,ally_sneak", "hydro_douse_ally");
        put("water_near,aim_hostile:6,target_on_fire", "hydro_douse_hostile");
        put("water_near,hostiles_ahead:12", "hydro_pressure_jet");
        put("water_near,hostiles_ahead:8", "hydro_fan");
        put("water_near,target_in_water,hostiles_near:9", "hydro_domain");
        put("water_near,target_in_water,hostiles_near:6", "hydro_eddy");
        put("in_water", "hydro_current", "hydro_lift", "hydro_brake", "hydro_dive", "hydro_lateral_current", "hydro_brake_field");
        put("aim_hostile:6,target_in_water", "hydro_repel", "hydro_undertow");
        put("in_water,ally_sneak", "hydro_rescue", "hydro_tow_ally");
        // Float Dial
        put("aim_hostile:16", "float_lift");
        put("aim_hostile:12", "float_suspend", "float_hostile_descent");
        put("hostiles_near:10", "float_field");
        put("hostiles_near:8", "float_hover_field");
        put("falling", "float_rescue", "float_rescue_field");
        put("airborne", "float_hover", "float_ascent", "float_descent", "float_air_brake");
        put("airborne,ally_sneak", "float_ally_hover", "float_ally_ascent", "float_ally_descent");
        put("thrown_item", "float_item_lift", "float_item_push", "float_item_field", "float_item_shelf", "float_item_orbit", "float_item_shelf_field");
        put("floating_item", "float_item_lower", "float_item_pull", "float_item_release", "float_release_field");
        // Liquid Shadow
        put("medium", "liquid_scout");
        put("liquid_body", "liquid_pause", "liquid_recall", "liquid_dismiss", "liquid_turn", "liquid_retreat", "liquid_listen",
                "liquid_path", "liquid_follow", "liquid_survey", "liquid_dual_sensor");
        put("liquid_body,block_face", "liquid_route");
        put("liquid_body,body_lever", "liquid_switch");
        put("liquid_body,body_arrow", "liquid_guard", "liquid_intercept", "liquid_screen");
        put("liquid_body,body_hostile:4", "liquid_strike", "liquid_push");
        // Contact skills act only where the shadow's body actually touches the enemy (the sweep: anything brushing it).
        put("liquid_body,aim_hostile:32,body_contact", "liquid_grapple", "liquid_contact_cut", "liquid_contact_crush");
        put("liquid_body,body_contact", "liquid_contact_sweep");
        put("liquid_body,body_hostile:6", "liquid_pulse", "liquid_shadow_field");
        // Paper Psychokinesis: sheets in hand, then the target the construct needs.
        put("paper:1,aim_hostile:16", "paper_blade");
        put("paper:1", "paper_plane");
        put("paper:3", "paper_armor");
        put("paper:8", "paper_wing");
        put("paper:6,hostiles_near:12", "paper_array");
        put("paper:2,block_face", "paper_patch");
        put("construct", "paper_retract");
        put("paper:2,construct", "paper_switch", "paper_plate", "paper_gate", "paper_ladder");
        put("paper:6,block_face", "paper_walkway", "paper_step", "paper_screen", "paper_safety_rail", "paper_switch_bridge");
        put("paper:8,block_face", "paper_ramp", "paper_pad", "paper_lift_bridge");
        put("paper:8,ledge", "paper_emergency_stairs");
        put("paper:12,block_face", "paper_cover");
        put("paper:18,block_face", "paper_platform");
        put("paper:4,block_face", "paper_puzzle_link");
        put("paper:10,block_face", "paper_catwalk");
        // New offensive branches use the same material/target vocabulary as their real controllers.
        for (var skill : CanonOffenseSkills.ALL) {
            String need = switch (skill.shape()) {
                case POINT -> "aim_hostile:" + skill.reach();
                case AIR_POINT -> "aim_hostile:" + skill.reach() + ",target_airborne";
                case CONE, LINE -> "hostiles_ahead:" + skill.reach();
                case AREA -> "aim_hostile:" + skill.reach();
                case BODY_POINT, BODY_SWEEP -> "liquid_body,body_hostile:" + skill.radius();
            };
            int paper = switch (skill.id()) { case "paper_fold_fan" -> 3; case "paper_piercing_lance" -> 2; case "paper_returning_wheel" -> 4; default -> 0; };
            put(paper > 0 ? "paper:" + paper + "," + need : need, skill.id());
        }
    }

    /** Requirement name without its numeric argument, e.g. "aim_hostile" for "aim_hostile:16". */
    public static String kind(String need) { int i = need.indexOf(':'); return i < 0 ? need : need.substring(0, i); }
    public static boolean has(String skill, String kind) { return needs(skill).stream().anyMatch(n -> kind(n).equals(kind)); }

    public static List<String> needs(String skill) {
        return needs(skill, false, false);
    }

    public static boolean covers(String skill) { return NEEDS.containsKey(skill); }

    private static List<String> needs(ServerPlayer player, String skill) {
        boolean remote = RangedSkillRanges.REMOTE_SCANS.contains(skill)
                && CanonAuxItems.inHands(player, CanonAuxItems.MENTAL_REMOTE);
        return needs(skill, remote, true);
    }

    private static List<String> needs(String skill, boolean remote, boolean actionBar) {
        var fields = SkillTuning.fields(skill);
        return NEEDS.getOrDefault(skill, List.of()).stream().map(need -> {
            String kind = kind(need);
            BigDecimal tuned = switch (kind) {
                case "aim_hostile" -> fields.get("reach");
                case "aim_mob" -> actionBar ? fields.get("reach") : null;
                case "hostiles_ahead" -> fields.getOrDefault("reach", fields.get("radius"));
                case "hostiles_near" -> fields.getOrDefault("radius", fields.get("reach"));
                case "body_hostile" -> fields.getOrDefault("body_reach", fields.get("radius"));
                case "body_arrow" -> actionBar ? fields.get("radius") : null;
                case "water_near" -> actionBar ? fields.getOrDefault("source_reach",
                        SkillTuning.fields("hydro_whip").get("source_reach")) : null;
                case "pursuing" -> actionBar ? fields.getOrDefault("radius", fields.get("reach")) : null;
                case "incoming_arrow" -> actionBar ? fields.get("reach") : null;
                case "thrown_item" -> actionBar ? fields.getOrDefault("reach", fields.get("radius")) : null;
                case "ally_sneak", "ally_pursued" -> actionBar ? fields.get("ally_reach") : null;
                default -> null;
            };
            if (remote && (kind.equals("aim_hostile") || kind.equals("hostiles_near") || kind.equals("pursuing"))) {
                tuned = BigDecimal.valueOf(RangedSkillRanges.withMentalRemote(skill));
            }
            return tuned == null ? need : kind + ":" + tuned.stripTrailingZeros().toPlainString();
        }).toList();
    }

    /** Action-bar reason for a refused canon key press, at most twice a second per player. */
    public static void refused(ServerPlayer player, String skill) {
        if (player == null || skill == null || player.connection == null) return;
        long now = player.serverLevel().getGameTime(); Long last = LAST.get(player.getUUID());
        if (last != null && now - last < 10 && now >= last) return;
        LAST.put(player.getUUID(), now);
        var state = AbilityStorage.get(player);
        Component name = Component.translatable("ac.ability." + state.category + "." + skill + ".name");
        MutableComponent message;
        int cooldown = state.cooldowns.getOrDefault(skill, 0);
        var unsupported = HighPowerCombat.unsupportedReason(player);
        if (!state.activated) message = Component.translatable("ac.canon.refused.inactive", name);
        else if (cooldown > 0) message = Component.translatable("ac.canon.refused.cooldown", name, String.format(java.util.Locale.ROOT, "%.1f", cooldown / 20.0));
        else if (unsupported.isPresent()) message = Component.translatable("ac.canon.refused.unsupported", name, unsupported.get());
        else {
            // Name only what is missing right now when that can be read; otherwise (or when everything readable is met) the full list.
            var all = needs(player, skill); var missing = new java.util.ArrayList<String>(); var unknown = new java.util.ArrayList<String>();
            for (String need : all) { Boolean met = CanonNeedCheck.met(player, skill, need); if (met == null) unknown.add(need); else if (!met) missing.add(need); }
            com.mojang.logging.LogUtils.getLogger().info("CANON_REFUSED {} missing={} unknown={}", skill, missing, unknown);
            MutableComponent list = Component.empty(); boolean first = true;
            for (String need : missing.isEmpty() ? all : missing) {
                if (!first) list.append(Component.translatable("ac.canon.need.separator"));
                first = false;
                int split = need.indexOf(':');
                if (split < 0) list.append(Component.translatable("ac.canon.need." + need));
                else list.append(Component.translatable("ac.canon.need." + need.substring(0, split) + ".range", need.substring(split + 1)));
            }
            if (missing.isEmpty()) {
                if (!first) list.append(Component.translatable("ac.canon.need.separator"));
                list.append(Component.translatable("ac.canon.need.cp"));
            }
            message = Component.translatable(missing.isEmpty() ? "ac.canon.refused.needs" : "ac.canon.refused.missing", name, list);
        }
        player.displayClientMessage(message.withStyle(net.minecraft.ChatFormatting.YELLOW), true);
    }
}
