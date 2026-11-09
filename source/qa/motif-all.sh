#!/bin/bash
# Runs the motif probe for every skill with a motif, one category after another, writing contact sheets into $MOTIF_SHEETS.
cd "$(dirname "$0")/.." || exit 1
p=${1:-a}
qa/motif-batch.sh ${p}e arc_gen charging mag_movement mag_manip mine_detect body_intensify thunder_bolt forked_arc magnetic_bracer charge_decoy polarity_tether conductive_circuit grounding_return magnetic_ground_step magnetic_item_pull iron_sand_lance thunder_net potential_buffer pulse_shield magnetic_domain rail_relay
qa/motif-batch.sh ${p}m electron_bomb light_shield meltdowner mine_ray_basic mine_ray_expert mine_ray_luck ray_barrage jet_engine electron_missile dual_point scatter_plates beam_etching folded_mirror_array beam_step particle_laminate directional_dissipation_curtain zero_gap_armor point_meltdown remote_scatter_relay scatter_locator_plate reverse_jet_brake
qa/motif-batch.sh ${p}t threatening_teleport penetrate_teleport mark_teleport flesh_ripping location_teleport shift_tp space_fluct flashing safe_landing_probe phase_sidestep displaced_projection return_mark recovery_mark cross_substitution corner_exchange short_interception spatial_relay emergency_return tactical_coordinate_formation spatial_pincer_corridor incident_bypass phase_corridor
qa/motif-batch.sh ${p}v dir_shock ground_shock vec_accel vec_deviation dir_blast blood_retro vec_reflection deflection_strike vector_parry braking_touch inertial_anchor yaw_palm circulation_relief circulation_listening autonomous_vector_circle impulse_split
