package cn.academy.port.gametest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
/** Calls unchanged historical native tests, with isolated batches to avoid unrelated global tick fixtures. */
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
@EventBusSubscriber(modid="academy")
@GameTestHolder("academy_domain_legacy_companion")
@PrefixGameTestTemplate(false)
public final class MagneticDomainLegacyCompanionTests {
    @SubscribeEvent
    public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(32)); size.add(IntTag.valueOf(8)); size.add(IntTag.valueOf(32)); tag.put("size", size);
        ListTag blocks = new ListTag(); CompoundTag first = new CompoundTag(); ListTag pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0));
        first.put("pos", pos); first.putInt("state", 0); blocks.add(first); tag.put("blocks", blocks);
        tag.put("entities", new ListTag()); ListTag palette = new ListTag(); CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_domain_legacy_companion", "highpower_empty"))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template="highpower_empty",batch="legacy_domain_zero_cp_replay_pending_abort_and_closed_first_payment",timeoutTicks=100)
    public static void domain_zero_cp_replay_pending_abort_and_closed_first_payment(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_zero_cp_replay_pending_abort_and_closed_first_payment(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_exact_twenty_tick_cost_and_real_lv5_cp_recovery",timeoutTicks=120)
    public static void domain_exact_twenty_tick_cost_and_real_lv5_cp_recovery(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_exact_twenty_tick_cost_and_real_lv5_cp_recovery(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_native_save_reload_cannot_duplicate_charge_or_reuse_paid_window",timeoutTicks=110)
    public static void domain_native_save_reload_cannot_duplicate_charge_or_reuse_paid_window(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_native_save_reload_cannot_duplicate_charge_or_reuse_paid_window(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_preset_category_death_and_epoch_invalidation_revoke_same_tick",timeoutTicks=100)
    public static void domain_preset_category_death_and_epoch_invalidation_revoke_same_tick(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_preset_category_death_and_epoch_invalidation_revoke_same_tick(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_real_dimension_change_keeps_global_fence_without_stranding_new_world",timeoutTicks=120)
    public static void domain_real_dimension_change_keeps_global_fence_without_stranding_new_world(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_real_dimension_change_keeps_global_fence_without_stranding_new_world(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_native_arrow_first_turn_debits_one_percent_preserves_metadata_and_shared_lease",timeoutTicks=100)
    public static void domain_native_arrow_first_turn_debits_one_percent_preserves_metadata_and_shared_lease(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_native_arrow_first_turn_debits_one_percent_preserves_metadata_and_shared_lease(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_arrow_admission_filters_four_quota_and_unaffordable_turns_are_inert",timeoutTicks=100)
    public static void domain_arrow_admission_filters_four_quota_and_unaffordable_turns_are_inert(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_arrow_admission_filters_four_quota_and_unaffordable_turns_are_inert(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_unaffordable_and_wall_hidden_arrows_never_pay_or_receive_a_lease",timeoutTicks=100)
    public static void domain_unaffordable_and_wall_hidden_arrows_never_pay_or_receive_a_lease(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_unaffordable_and_wall_hidden_arrows_never_pay_or_receive_a_lease(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_episode_45degree_cap_no_second_fee_and_serialized_arrow_never_readmitted",timeoutTicks=120)
    public static void domain_episode_45degree_cap_no_second_fee_and_serialized_arrow_never_readmitted(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_episode_45degree_cap_no_second_fee_and_serialized_arrow_never_readmitted(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_new_wall_and_current_owner_revocation_stop_existing_native_curvature",timeoutTicks=110)
    public static void domain_new_wall_and_current_owner_revocation_stop_existing_native_curvature(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_new_wall_and_current_owner_revocation_stop_existing_native_curvature(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_reciprocal_native_writers_refuse_an_owned_arrow",timeoutTicks=100)
    public static void domain_reciprocal_native_writers_refuse_an_owned_arrow(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_reciprocal_native_writers_refuse_an_owned_arrow(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_current_team_and_native_pvp_permissions_revalidated_each_tick",timeoutTicks=110)
    public static void domain_current_team_and_native_pvp_permissions_revalidated_each_tick(GameTestHelper h) { HighPowerMagneticDomainRuntimeTests.domain_current_team_and_native_pvp_permissions_revalidated_each_tick(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_reserve_starts_zero_activation_save_reload_and_category_do_not_refill",timeoutTicks=140)
    public static void domain_reserve_starts_zero_activation_save_reload_and_category_do_not_refill(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_reserve_starts_zero_activation_save_reload_and_category_do_not_refill(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_quiet_recovery_has_one_budget_base_first_and_active_blocks_both",timeoutTicks=145)
    public static void domain_quiet_recovery_has_one_budget_base_first_and_active_blocks_both(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_quiet_recovery_has_one_budget_base_first_and_active_blocks_both(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_recovery_before_due_native_renewal_cannot_award_a_free_tick",timeoutTicks=165)
    public static void domain_recovery_before_due_native_renewal_cannot_award_a_free_tick(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_recovery_before_due_native_renewal_cannot_award_a_free_tick(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_malformed_reserve_fails_closed_without_invalidating_base_combat",timeoutTicks=100)
    public static void domain_malformed_reserve_fails_closed_without_invalidating_base_combat(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_malformed_reserve_fails_closed_without_invalidating_base_combat(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_rear_damage_is_shielded_but_unknown_beams_bypass_only_this_layer",timeoutTicks=100)
    public static void domain_rear_damage_is_shielded_but_unknown_beams_bypass_only_this_layer(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_rear_damage_is_shielded_but_unknown_beams_bypass_only_this_layer(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_partial_exhaustion_spends_both_once_then_damages_hp",timeoutTicks=100)
    public static void domain_partial_exhaustion_spends_both_once_then_damages_hp(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_partial_exhaustion_spends_both_once_then_damages_hp(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_arrow_fee_is_fixed_base_percentage_atomic_and_cannot_spend_hp",timeoutTicks=100)
    public static void domain_arrow_fee_is_fixed_base_percentage_atomic_and_cannot_spend_hp(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_arrow_fee_is_fixed_base_percentage_atomic_and_cannot_spend_hp(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_grounding_then_buffer_precedence_never_spends_reserve",timeoutTicks=100)
    public static void domain_grounding_then_buffer_precedence_never_spends_reserve(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_grounding_then_buffer_precedence_never_spends_reserve(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_lethal_remainder_keeps_native_totem_and_consumed_reserve",timeoutTicks=100)
    public static void domain_lethal_remainder_keeps_native_totem_and_consumed_reserve(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_lethal_remainder_keeps_native_totem_and_consumed_reserve(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_native_death_and_post_callback_reentry_preserve_committed_debits",timeoutTicks=100)
    public static void domain_native_death_and_post_callback_reentry_preserve_committed_debits(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_native_death_and_post_callback_reentry_preserve_committed_debits(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_paired_native_armor_toughness_resistance_protection_absorption_precede_shared_shield",timeoutTicks=100)
    public static void domain_paired_native_armor_toughness_resistance_protection_absorption_precede_shared_shield(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_paired_native_armor_toughness_resistance_protection_absorption_precede_shared_shield(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_incoming_cancellation_and_snapshot_abort_cannot_spend_either_tranche",timeoutTicks=100)
    public static void domain_incoming_cancellation_and_snapshot_abort_cannot_spend_either_tranche(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_incoming_cancellation_and_snapshot_abort_cannot_spend_either_tranche(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_clone_copies_reserve_without_refill_and_reset_refuses_lost_capacity",timeoutTicks=100)
    public static void domain_clone_copies_reserve_without_refill_and_reset_refuses_lost_capacity(GameTestHelper h) { HighPowerMagneticDomainShieldRuntimeTests.domain_clone_copies_reserve_without_refill_and_reset_refuses_lost_capacity(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_slow_fall_only_descends_without_direct_motion_or_fall_distance_writes",timeoutTicks=110)
    public static void domain_slow_fall_only_descends_without_direct_motion_or_fall_distance_writes(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_slow_fall_only_descends_without_direct_motion_or_fall_distance_writes(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_ascending_stops_refresh_and_owned_effect_expires_within_two_native_ticks",timeoutTicks=110)
    public static void domain_ascending_stops_refresh_and_owned_effect_expires_within_two_native_ticks(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_ascending_stops_refresh_and_owned_effect_expires_within_two_native_ticks(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_native_expiry_allows_reacquisition_but_preexisting_potion_yields_entire_lease",timeoutTicks=110)
    public static void domain_native_expiry_allows_reacquisition_but_preexisting_potion_yields_entire_lease(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_native_expiry_allows_reacquisition_but_preexisting_potion_yields_entire_lease(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_foreign_added_in_place_merge_and_explicit_remove_surrender_ownership",timeoutTicks=110)
    public static void domain_foreign_added_in_place_merge_and_explicit_remove_surrender_ownership(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_foreign_added_in_place_merge_and_explicit_remove_surrender_ownership(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_real_milk_cure_is_never_undone_by_refresh",timeoutTicks=110)
    public static void domain_real_milk_cure_is_never_undone_by_refresh(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_real_milk_cure_is_never_undone_by_refresh(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_added_callback_abort_cannot_commit_after_paid_context_ends",timeoutTicks=110)
    public static void domain_added_callback_abort_cannot_commit_after_paid_context_ends(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_added_callback_abort_cannot_commit_after_paid_context_ends(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_nested_foreign_added_keeps_foreign_effect_without_outer_overwrite",timeoutTicks=110)
    public static void domain_nested_foreign_added_keeps_foreign_effect_without_outer_overwrite(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_nested_foreign_added_keeps_foreign_effect_without_outer_overwrite(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_nested_reuse_of_exact_pending_effect_fails_closed",timeoutTicks=110)
    public static void domain_nested_reuse_of_exact_pending_effect_fails_closed(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_nested_reuse_of_exact_pending_effect_fails_closed(h); }
    @GameTest(template="highpower_empty",batch="legacy_domain_native_ai_step_uses_slow_falling_gravity_and_native_fall_reset",timeoutTicks=110)
    public static void domain_native_ai_step_uses_slow_falling_gravity_and_native_fall_reset(GameTestHelper h) { HighPowerMagneticDomainSlowFallRuntimeTests.domain_native_ai_step_uses_slow_falling_gravity_and_native_fall_reset(h); }
}
