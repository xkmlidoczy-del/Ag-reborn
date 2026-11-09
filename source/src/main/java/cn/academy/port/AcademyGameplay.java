package cn.academy.port;
import cn.academy.port.core.*;
import cn.academy.port.develop.*;
import cn.academy.port.preset.PresetSkills;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import cn.academy.port.skill.RailgunDamage;
import cn.academy.port.skill.RailgunVisualContact;
import cn.academy.port.skill.ClassicRaytrace;
import cn.academy.port.skill.DirectedShock;
import cn.academy.port.skill.ThreateningTeleport;
import cn.academy.port.skill.ElectronBomb;
import cn.academy.port.skill.CoinTosses;
import cn.academy.port.skill.CurrentCharging;
import cn.academy.port.skill.GroundShock;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
public final class AcademyGameplay {
    private record RailgunCharge(ServerPlayer owner,net.minecraft.server.level.ServerLevel level,AbilityProgress state,int remaining) {}
    private static final Map<UUID,RailgunCharge> CHARGES=new HashMap<>();
    /** Only one high-power or classic movement controller may own player motion. */
    private static boolean movementAvailable(ServerPlayer player,String skill){
        return !(cn.academy.port.highpower.SpatialPincerConsent.committing(player)||cn.academy.port.highpower.TacticalCoordinateFormation.committing(player)||cn.academy.port.highpower.EmergencyReturn.isTransporting(player)||cn.academy.port.highpower.CornerExchange.active(player)||cn.academy.port.highpower.CrossSubstitution.isTransporting(player)||cn.academy.port.highpower.ReturnMark.active(player)||cn.academy.port.highpower.PolarityTether.active(player)||cn.academy.port.highpower.MagneticGroundStep.active(player)||cn.academy.port.highpower.BeamStep.active(player)||cn.academy.port.highpower.WindPressureLift.active(player))||!Set.of("mag_movement","vec_accel","jet_engine",
                "penetrate_teleport","mark_teleport","location_teleport","shift_tp","flashing","storm_wing").contains(skill);
    }
    /** Only this entry point is registered on the wire; skill-name request() is a trusted test/internal API. */
    public static boolean requestFromClient(ServerPlayer player,AcademyNetwork.Request request) {
        if(AbilityConsumption.busy(player)||player==null||request==null||request.action()==null||request.value()==null||player.isRemoved()||!player.isAlive()||player.isSpectator()||!player.serverLevel().getServer().isSameThread())return false;
        var state=AbilityStorage.get(player);boolean accepted=false;
        switch(request.action()) {
            case "hp_learn" -> accepted=cn.academy.port.highpower.HighPowerProgression.requestLearning(player,null,request.value());
            case "hp_circuit_tap" -> accepted=cn.academy.port.highpower.ConductiveCircuit.tap(player,request.value());
            case "hp_circuit_abort" -> accepted=cn.academy.port.highpower.ConductiveCircuit.abort(player,request.value());
            case "hp_ground_start" -> accepted=cn.academy.port.highpower.GroundingReturn.start(player,request.value());
            case "hp_ground_abort" -> accepted=cn.academy.port.highpower.GroundingReturn.abort(player,request.value());
            case "hp_beam_step_abort" -> accepted=cn.academy.port.highpower.BeamStep.abort(player,request.value());
            case "hp_beam_step_tap" -> accepted=cn.academy.port.highpower.BeamStep.tap(player,request.value());
            case "hp_ground_step_tap" -> accepted=cn.academy.port.highpower.MagneticGroundStep.tap(player,request.value());
            case "hp_ground_step_abort" -> accepted=cn.academy.port.highpower.MagneticGroundStep.abort(player,request.value());
            case "hp_rail_thunder_array_press" -> accepted=cn.academy.port.highpower.RailThunderArray.press(player,request.value());
            case "hp_rail_thunder_array_release" -> accepted=cn.academy.port.highpower.RailThunderArray.release(player,request.value());
            case "hp_rail_thunder_array_abort" -> accepted=cn.academy.port.highpower.RailThunderArray.abort(player,request.value());
            case "trajectory_return_tap" -> accepted=cn.academy.port.highpower.TrajectoryReturn.tap(player,request.value());
            case "trajectory_return_abort" -> accepted=cn.academy.port.highpower.TrajectoryReturn.abort(player,request.value());
            case "yaw_palm_tap" -> accepted=cn.academy.port.highpower.YawPalm.tap(player,request.value());
            case "yaw_palm_release" -> accepted=cn.academy.port.highpower.YawPalm.release(player,request.value());
            case "yaw_palm_abort" -> accepted=cn.academy.port.highpower.YawPalm.abort(player,request.value());
            case "wind_pressure_lift_start" -> accepted=cn.academy.port.highpower.WindPressureLift.start(player,request.value());
            case "wind_pressure_lift_end" -> accepted=cn.academy.port.highpower.WindPressureLift.abort(player,request.value());
            case "multi_axis_reflection_start" -> accepted=cn.academy.port.highpower.MultiAxisReflection.start(player,request.value());
            case "multi_axis_reflection_end" -> accepted=cn.academy.port.highpower.MultiAxisReflection.abort(player,request.value());
            case "vector_cage_start" -> accepted=cn.academy.port.highpower.VectorCage.start(player,request.value());
            case "vector_cage_release" -> accepted=cn.academy.port.highpower.VectorCage.release(player,request.value());
            case "vector_cage_abort" -> accepted=cn.academy.port.highpower.VectorCage.abort(player,request.value());
            case "circ_listen_start" -> accepted=cn.academy.port.highpower.CirculationListening.start(player,request.value());
            case "circ_listen_release" -> accepted=cn.academy.port.highpower.CirculationListening.release(player,request.value());
            case "circ_listen_abort" -> accepted=cn.academy.port.highpower.CirculationListening.abort(player,request.value());
            case "autonomous_vector_circle" -> accepted=cn.academy.port.highpower.AutonomousVectorCircle.start(player,request.value());
            case "autonomous_vector_circle_end" -> accepted=cn.academy.port.highpower.AutonomousVectorCircle.abort(player,request.value());
            case "circulation_relief" -> accepted=cn.academy.port.highpower.CirculationRelief.start(player,request.value());
            case "circulation_relief_end" -> accepted=cn.academy.port.highpower.CirculationRelief.abort(player,request.value());
            case "inertial_anchor" -> accepted=cn.academy.port.highpower.InertialAnchor.start(player,request.value());
            case "inertial_anchor_end" -> accepted=cn.academy.port.highpower.InertialAnchor.abort(player,request.value());
            case "shear_windblade_tap" -> accepted=cn.academy.port.highpower.ShearWindblade.tap(player,request.value());
            case "shear_windblade_abort" -> accepted=cn.academy.port.highpower.ShearWindblade.abort(player,request.value());
            case "hp_new_start" -> accepted=cn.academy.port.highpower.NewSkillRuntime.start(player,request.value());
            case "hp_new_end" -> accepted=cn.academy.port.highpower.NewSkillRuntime.release(player,request.value());
            case "hp_new_abort" -> accepted=cn.academy.port.highpower.NewSkillRuntime.abort(player,request.value());
            case "brake_touch_start" -> accepted=cn.academy.port.highpower.BrakingTouch.start(player,request.value());
            case "brake_touch_end" -> accepted=cn.academy.port.highpower.BrakingTouch.release(player,request.value());
            case "brake_touch_abort" -> accepted=cn.academy.port.highpower.BrakingTouch.abort(player,request.value());
            case "hp_vector_parry_start" -> accepted=cn.academy.port.highpower.VectorParry.start(player,request.value());
            case "hp_vector_parry_release" -> accepted=cn.academy.port.highpower.VectorParry.release(player,request.value());
            case "hp_vector_parry_abort" -> accepted=cn.academy.port.highpower.VectorParry.abort(player,request.value());
            case "hp_deflect_tap" -> accepted=cn.academy.port.highpower.DeflectionStrike.tap(player,request.value());
            case "hp_deflect_abort" -> accepted=cn.academy.port.highpower.DeflectionStrike.abort(player,request.value());
            case "hp_dual_point_tap" -> accepted=cn.academy.port.highpower.DualPoint.tap(player,request.value());
            case "hp_dual_point_mark" -> accepted=cn.academy.port.highpower.DualPoint.mark(player,request.value());
            case "hp_dual_point_abort" -> accepted=cn.academy.port.highpower.DualPoint.abort(player,request.value());
            case "hp_scatter_plates_toggle" -> accepted=cn.academy.port.highpower.ScatterPlates.toggle(player,request.value());
            case "hp_scatter_plates_abort" -> accepted=cn.academy.port.highpower.ScatterPlates.abort(player,request.value());
            case "hp_disintegration_sweep_start" -> accepted=cn.academy.port.highpower.DisintegrationSweep.start(player,request.value());
            case "hp_disintegration_sweep_release" -> accepted=cn.academy.port.highpower.DisintegrationSweep.release(player,request.value());
            case "hp_disintegration_sweep_abort" -> accepted=cn.academy.port.highpower.DisintegrationSweep.abort(player,request.value());
            case "hp_folded_mirror_start" -> accepted=cn.academy.port.highpower.FoldedMirrorArray.start(player,request.value());
            case "hp_multicore_canopy_start" -> accepted=cn.academy.port.highpower.MulticoreCanopy.start(player,request.value());
            case "hp_multicore_canopy_release" -> accepted=cn.academy.port.highpower.MulticoreCanopy.release(player,request.value());
            case "hp_multicore_canopy_abort" -> accepted=cn.academy.port.highpower.MulticoreCanopy.abort(player,request.value());
            case "hp_safe_landing_probe","hp_safe_landing_probe_start" -> accepted=cn.academy.port.highpower.SafeLandingProbe.press(player,request.value());
            case "hp_safe_landing_probe_release" -> accepted=cn.academy.port.highpower.SafeLandingProbe.release(player,request.value());
            case "hp_safe_landing_probe_abort" -> accepted=cn.academy.port.highpower.SafeLandingProbe.abort(player,request.value());
            case "hp_displaced_projection","hp_displaced_projection_start" -> accepted=cn.academy.port.highpower.DisplacedProjection.start(player,request.value());
            case "hp_displaced_projection_release" -> accepted=cn.academy.port.highpower.DisplacedProjection.release(player,request.value());
            case "hp_displaced_projection_abort" -> accepted=cn.academy.port.highpower.DisplacedProjection.abort(player,request.value());
            case "hp_short_interception_start" -> accepted=cn.academy.port.highpower.ShortInterception.press(player,request.value());
            case "hp_mla_start" -> accepted=cn.academy.port.highpower.MultidimensionalLandingArray.press(player,request.value());
            case "hp_mla_release" -> accepted=cn.academy.port.highpower.MultidimensionalLandingArray.release(player,request.value());
            case "hp_mla_abort" -> accepted=cn.academy.port.highpower.MultidimensionalLandingArray.abort(player,request.value());
            case "hp_spatial_pincer_start" -> accepted=cn.academy.port.highpower.SpatialPincerCorridor.press(player,request.value());
            case "hp_spatial_pincer_release" -> accepted=cn.academy.port.highpower.SpatialPincerCorridor.release(player,request.value());
            case "hp_spatial_pincer_abort" -> accepted=cn.academy.port.highpower.SpatialPincerCorridor.abort(player,request.value());
            case "hp_tactical_coordinate_open" -> accepted=cn.academy.port.highpower.TacticalCoordinateFormation.press(player,request.value());
            case "hp_tactical_coordinate_abort" -> accepted=cn.academy.port.highpower.TacticalCoordinateFormation.abort(player,request.value());
            case "hp_emergency_return_start" -> accepted=cn.academy.port.highpower.EmergencyReturn.press(player,request.value());
            case "hp_emergency_return_release" -> accepted=cn.academy.port.highpower.EmergencyReturn.release(player,request.value());
            case "hp_emergency_return_abort" -> accepted=cn.academy.port.highpower.EmergencyReturn.abort(player,request.value());
            case "hp_spatial_relay_start" -> accepted=cn.academy.port.highpower.SpatialRelay.press(player,request.value());
            case "hp_spatial_relay_release" -> accepted=cn.academy.port.highpower.SpatialRelay.release(player,request.value());
            case "hp_spatial_relay_abort" -> accepted=cn.academy.port.highpower.SpatialRelay.abort(player,request.value());
            case "hp_short_interception_release" -> accepted=cn.academy.port.highpower.ShortInterception.release(player,request.value());
            case "hp_short_interception_abort" -> accepted=cn.academy.port.highpower.ShortInterception.abort(player,request.value());
            case "hp_corner_exchange_start" -> accepted=cn.academy.port.highpower.CornerExchange.press(player,request.value());
            case "hp_corner_exchange_release" -> accepted=cn.academy.port.highpower.CornerExchange.release(player,request.value());
            case "hp_corner_exchange_abort" -> accepted=cn.academy.port.highpower.CornerExchange.abort(player,request.value());
            case "hp_cross_substitution_start" -> accepted=cn.academy.port.highpower.CrossSubstitution.press(player,request.value());
            case "hp_cross_substitution_release" -> accepted=cn.academy.port.highpower.CrossSubstitution.release(player,request.value());
            case "hp_cross_substitution_abort" -> accepted=cn.academy.port.highpower.CrossSubstitution.abort(player,request.value());
            case "hp_recovery_mark_start" -> accepted=cn.academy.port.highpower.RecoveryMark.press(player,request.value());
            case "hp_recovery_mark_release" -> accepted=cn.academy.port.highpower.RecoveryMark.release(player,request.value());
            case "hp_recovery_mark_abort" -> accepted=cn.academy.port.highpower.RecoveryMark.abort(player,request.value());
            case "hp_return_mark_start" -> accepted=cn.academy.port.highpower.ReturnMark.press(player,request.value());
            case "hp_return_mark_release" -> accepted=cn.academy.port.highpower.ReturnMark.release(player,request.value());
            case "hp_return_mark_abort" -> accepted=cn.academy.port.highpower.ReturnMark.abort(player,request.value());
            case "hp_phase_sidestep","hp_phase_sidestep_start" -> accepted=cn.academy.port.highpower.PhaseSidestep.start(player,request.value());
            case "hp_phase_sidestep_release" -> accepted=cn.academy.port.highpower.PhaseSidestep.release(player,request.value());
            case "hp_phase_sidestep_abort" -> accepted=cn.academy.port.highpower.PhaseSidestep.abort(player,request.value());
            case "hp_zero_gap_armor_toggle","hp_zero_gap_armor_start" -> accepted=cn.academy.port.highpower.ZeroGapArmor.toggle(player,request.value());
            case "hp_zero_gap_armor_release" -> accepted=cn.academy.port.highpower.ZeroGapArmor.release(player,request.value());
            case "hp_zero_gap_armor_abort" -> accepted=cn.academy.port.highpower.ZeroGapArmor.abort(player,request.value());
            case "hp_dissipation_curtain_start" -> accepted=cn.academy.port.highpower.DissipationCurtain.start(player,request.value());
            case "hp_dissipation_curtain_release" -> accepted=cn.academy.port.highpower.DissipationCurtain.release(player,request.value());
            case "hp_dissipation_curtain_abort" -> accepted=cn.academy.port.highpower.DissipationCurtain.abort(player,request.value());
            case "hp_particle_laminate_start" -> accepted=cn.academy.port.highpower.ParticleLaminate.start(player,request.value());
            case "hp_folded_mirror_release" -> accepted=cn.academy.port.highpower.FoldedMirrorArray.release(player,request.value());
            case "hp_particle_laminate_release" -> accepted=cn.academy.port.highpower.ParticleLaminate.release(player,request.value());
            case "hp_folded_mirror_abort" -> accepted=cn.academy.port.highpower.FoldedMirrorArray.abort(player,request.value());
            case "hp_particle_laminate_abort" -> accepted=cn.academy.port.highpower.ParticleLaminate.abort(player,request.value());
            case "hp_beam_etching_start" -> accepted=cn.academy.port.highpower.BeamEtching.start(player,request.value());
            case "hp_beam_etching_release" -> accepted=cn.academy.port.highpower.BeamEtching.release(player,request.value());
            case "hp_beam_etching_abort" -> accepted=cn.academy.port.highpower.BeamEtching.abort(player,request.value());
            case "hp_point_meltdown_start" -> accepted=cn.academy.port.highpower.PointMeltdown.start(player,request.value());
            case "hp_point_meltdown_release" -> accepted=cn.academy.port.highpower.PointMeltdown.release(player,request.value());
            case "hp_point_meltdown_abort" -> accepted=cn.academy.port.highpower.PointMeltdown.abort(player,request.value());
            case "hp_scatter_locator_start" -> accepted=cn.academy.port.highpower.ScatterLocatorPlate.start(player,request.value());
            case "hp_scatter_locator_release" -> accepted=cn.academy.port.highpower.ScatterLocatorPlate.release(player,request.value());
            case "hp_scatter_locator_abort" -> accepted=cn.academy.port.highpower.ScatterLocatorPlate.abort(player,request.value());
            case "hp_remote_relay_start" -> accepted=cn.academy.port.highpower.RemoteScatterRelay.start(player,request.value());
            case "hp_remote_relay_release" -> accepted=cn.academy.port.highpower.RemoteScatterRelay.release(player,request.value());
            case "hp_remote_relay_abort" -> accepted=cn.academy.port.highpower.RemoteScatterRelay.abort(player,request.value());
            case "hp_magnetic_domain_toggle" -> accepted=cn.academy.port.highpower.MagneticDomain.toggle(player,request.value());
            case "hp_magnetic_domain_abort" -> accepted=cn.academy.port.highpower.MagneticDomain.abort(player,request.value());
            case "hp_thermal_shell_toggle" -> accepted=cn.academy.port.highpower.ThermalShell.toggle(player,request.value());
            case "hp_thermal_shell_abort" -> accepted=cn.academy.port.highpower.ThermalShell.abort(player,request.value());
            case "hp_potential_buffer_toggle" -> accepted=cn.academy.port.highpower.PotentialBuffer.toggle(player,request.value());
            case "hp_potential_buffer_abort" -> accepted=cn.academy.port.highpower.PotentialBuffer.abort(player,request.value());
            case "hp_thunder_net_begin" -> accepted=cn.academy.port.highpower.ThunderNet.begin(player,request.value());
            case "hp_thunder_net_release" -> accepted=cn.academy.port.highpower.ThunderNet.release(player,request.value());
            case "hp_thunder_net_abort" -> accepted=cn.academy.port.highpower.ThunderNet.abort(player,request.value());
            case "hp_iron_lance_begin" -> accepted=cn.academy.port.highpower.IronSandLance.begin(player,request.value(),false);
            case "hp_iron_lance_orbit_begin" -> accepted=cn.academy.port.highpower.IronSandLance.begin(player,request.value(),true);
            case "hp_iron_lance_release" -> accepted=cn.academy.port.highpower.IronSandLance.release(player,request.value());
            case "hp_iron_lance_abort" -> accepted=cn.academy.port.highpower.IronSandLance.abort(player,request.value());
            case "hp_item_pull_begin" -> accepted=cn.academy.port.highpower.MagneticItemPull.begin(player,request.value());
            case "hp_item_separate_begin" -> accepted=cn.academy.port.highpower.MagneticItemPull.beginArrows(player,request.value());
            case "hp_item_separate_release" -> accepted=cn.academy.port.highpower.MagneticItemPull.releaseArrows(player,request.value());
            case "hp_item_pull_abort" -> accepted=cn.academy.port.highpower.MagneticItemPull.abort(player,request.value());
            case "hp_tether_tap" -> accepted=cn.academy.port.highpower.PolarityTether.tap(player,request.value());
            case "hp_tether_abort" -> accepted=cn.academy.port.highpower.PolarityTether.abort(player,request.value());
            case "hp_bracer_press" -> accepted=cn.academy.port.highpower.HighPowerBracerSession.start(player,request.value());
            case "hp_bracer_stop" -> accepted=cn.academy.port.highpower.HighPowerBracerSession.stop(player,request.value());
            case "hp_decoy_press","hp_decoy_release","hp_decoy_abort" -> {
                var input=cn.academy.port.highpower.HighPowerInput.parse(request.value());
                if(cn.academy.port.highpower.HighPowerBracerSession.authenticated(player,input)) {
                    if(request.action().equals("hp_decoy_press")) {
                        if(cn.academy.port.highpower.HighPowerSkills.CHARGE_DECOY.equals(state.presets.currentSkill(input.slot()))
                                &&cn.academy.port.highpower.HighPowerSkills.eligible(state,cn.academy.port.highpower.HighPowerSkills.CHARGE_DECOY))
                            accepted=cn.academy.port.highpower.ElectromasterChargeDecoy.start(player,input.input());
                    } else accepted=request.action().equals("hp_decoy_abort")
                            ?cn.academy.port.highpower.ElectromasterChargeDecoy.abort(player,input.input())
                            :cn.academy.port.highpower.ElectromasterChargeDecoy.release(player,input.input());
                }
            }
            case "single_key_press" -> {
                var input=cn.academy.port.core.LegacySingleKeyProtocol.parseStart(request.value());
                if(input!=null){String skill=input.skill().id();
                    boolean fresh=input.actorId()==player.getId()&&cn.academy.port.core.LegacySingleKeyOwnerEpoch.matches(player,player.connection,player.serverLevel(),state,input.ownerEpoch())&&!cn.academy.port.core.LegacySingleKeyOwnerEpoch.pendingRetired(player,player.connection,player.serverLevel(),state,input.ownerEpoch(),input.skill(),input.input())&&switch(input.skill()){
                        case CHARGING -> CurrentCharging.canAcceptInput(player,input.input());
                        case MAG_MOVEMENT -> cn.academy.port.skill.MagMovement.canAcceptInput(player,input.input());
                        case MAG_MANIP -> cn.academy.port.skill.MagManip.canAcceptInput(player,input.input());
                        case GROUND_SHOCK -> GroundShock.canAcceptInput(player,input.input());
                        case DIRECTED_SHOCK -> DirectedShock.canAcceptInput(player,input.input());
                        case THREATENING_TELEPORT -> ThreateningTeleport.canAcceptInput(player,input.input());
                    };
                    if(fresh&&SkillPresets.validSlot(input.slot())&&skill.equals(state.presets.currentSkill(input.slot()))&&PresetSkills.mappedUsable(state,skill)&&state.canUse(skill)&&movementAvailable(player,skill)){
                        if(cn.academy.port.skill.Flashing.active(player))cn.academy.port.skill.Flashing.abort(player);
                        accepted=switch(input.skill()){
                            case CHARGING -> CurrentCharging.start(player,input.input());
                            case MAG_MOVEMENT -> cn.academy.port.skill.MagMovement.start(player,input.input());
                            case MAG_MANIP -> cn.academy.port.skill.MagManip.start(player,input.input());
                            case GROUND_SHOCK -> GroundShock.start(player,input.input());
                            case DIRECTED_SHOCK -> DirectedShock.start(player,input.input());
                            case THREATENING_TELEPORT -> ThreateningTeleport.start(player,input.input());
                        };
                    }
                    if(!accepted)rejectSingleInput(player,input.skill().id(),input.input(),input.actorId(),input.ownerEpoch());
                }
            }
            case "single_key_release","single_key_abort" -> {
                var input=cn.academy.port.core.LegacySingleKeyProtocol.parseAccepted(request.value());
                if(input!=null&&input.actorId()==player.getId()&&cn.academy.port.core.LegacySingleKeyOwnerEpoch.matches(player,player.connection,player.serverLevel(),state,input.ownerEpoch())){boolean abort=request.action().equals(cn.academy.port.core.LegacySingleKeyProtocol.ABORT);
                    accepted=switch(input.skill()){
                        case CHARGING -> abort?CurrentCharging.abort(player,input.input(),input.token(),input.ownerEpoch()):CurrentCharging.release(player,input.input(),input.token(),input.ownerEpoch());
                        case MAG_MOVEMENT -> abort?cn.academy.port.skill.MagMovement.abort(player,input.input(),input.token(),input.ownerEpoch()):cn.academy.port.skill.MagMovement.release(player,input.input(),input.token(),input.ownerEpoch());
                        case MAG_MANIP -> abort?cn.academy.port.skill.MagManip.abort(player,input.input(),input.token(),input.ownerEpoch()):cn.academy.port.skill.MagManip.release(player,input.input(),input.token(),input.ownerEpoch());
                        case GROUND_SHOCK -> abort?GroundShock.abort(player,input.input(),input.token(),input.ownerEpoch()):GroundShock.release(player,input.input(),input.token(),input.ownerEpoch());
                        case DIRECTED_SHOCK -> abort?DirectedShock.abort(player,input.input(),input.token(),input.ownerEpoch()):DirectedShock.release(player,input.input(),input.token(),input.ownerEpoch());
                        case THREATENING_TELEPORT -> abort?ThreateningTeleport.abort(player,input.input(),input.token(),input.ownerEpoch()):ThreateningTeleport.release(player,input.input(),input.token(),input.ownerEpoch());
                    };
                }
            }
            case "single_key_pending_release","single_key_pending_abort" -> {
                var input=cn.academy.port.core.LegacySingleKeyProtocol.parsePending(request.value());
                boolean retired=input!=null&&cn.academy.port.core.LegacySingleKeyOwnerEpoch.pendingRetired(player,player.connection,player.serverLevel(),state,input.ownerEpoch(),input.skill(),input.input());
                if(input!=null&&input.actorId()==player.getId()&&player.connection!=null&&!player.hasDisconnected()&&player.serverLevel().getEntity(player.getId())==player&&cn.academy.port.core.LegacySingleKeyOwnerEpoch.retirePending(player,player.connection,player.serverLevel(),state,input.ownerEpoch(),input.skill(),input.input())){boolean abort=request.action().equals(cn.academy.port.core.LegacySingleKeyProtocol.PENDING_ABORT);
                    boolean ended=switch(input.skill()){
                        case CHARGING -> abort?CurrentCharging.abort(player,input.input()):CurrentCharging.release(player,input.input());
                        case MAG_MOVEMENT -> abort?cn.academy.port.skill.MagMovement.abort(player,input.input()):cn.academy.port.skill.MagMovement.release(player,input.input());
                        case MAG_MANIP -> abort?cn.academy.port.skill.MagManip.abort(player,input.input()):cn.academy.port.skill.MagManip.release(player,input.input());
                        case GROUND_SHOCK -> abort?GroundShock.abort(player,input.input()):GroundShock.release(player,input.input());
                        case DIRECTED_SHOCK -> abort?DirectedShock.abort(player,input.input()):DirectedShock.release(player,input.input());
                        case THREATENING_TELEPORT -> abort?ThreateningTeleport.abort(player,input.input()):ThreateningTeleport.release(player,input.input());
                    };
                    // Ordered source transport normally starts first. This bounded modern guard also
                    // retires a pending request processed before start, without inventing a Hold/token.
                    if(!ended&&!retired)rejectSingleInput(player,input.skill().id(),input.input(),input.actorId(),input.ownerEpoch());
                    accepted=ended||!retired;
                }
            }
            case "context_abort" -> {
                var target=TargetedContextTermination.parse(request.value());
                if(target!=null)accepted=switch(target.skill()){
                    case VEC_DEVIATION -> cn.academy.port.skill.VecDeviation.abortContext(player,target.input(),target.token());
                    case VEC_REFLECTION -> cn.academy.port.skill.VecReflection.abortContext(player,target.input(),target.token());
                    case FLASHING -> cn.academy.port.skill.Flashing.abortContext(player,target.input(),target.token());
                    case STORM_WING -> cn.academy.port.skill.StormWing.abortContext(player,target.input(),target.token());
                };
            }
            case "pending_context_abort" -> {
                var pending=TargetedContextTermination.parsePending(request.value());
                if(pending!=null)accepted=switch(pending.skill()){
                    case VEC_DEVIATION -> cn.academy.port.skill.VecDeviation.abortPendingContext(player,pending.input());
                    case VEC_REFLECTION -> cn.academy.port.skill.VecReflection.abortPendingContext(player,pending.input());
                    case FLASHING -> cn.academy.port.skill.Flashing.abortPendingContext(player,pending.input());
                    case STORM_WING -> cn.academy.port.skill.StormWing.abortPendingContext(player,pending.input());
                };
            }
            case "railgun_abort" -> {if(request.value().isEmpty())accepted=cancelRailgunForPreset(player,state);}
            case "preset_switch" -> {
                int preset=presetIndex(request.value());
                if(state.activated&&SkillPresets.validPreset(preset)) {cn.academy.port.highpower.BeamStep.abort(player);cn.academy.port.highpower.WindPressureLift.abort(player);accepted=state.presets.switchTo(preset);if(accepted){cancelRailgunForPreset(player,state);cn.academy.port.highpower.HighPowerBracerSession.abort(player);cn.academy.port.highpower.ElectromasterChargeDecoy.abort(player);cn.academy.port.highpower.PolarityTether.abort(player);cn.academy.port.highpower.ConductiveCircuit.abort(player);cn.academy.port.highpower.GroundingReturn.abort(player);cn.academy.port.highpower.MagneticGroundStep.abort(player);cn.academy.port.highpower.MagneticItemPull.abort(player);cn.academy.port.highpower.IronSandLance.abort(player);cn.academy.port.highpower.ThunderNet.abort(player);cn.academy.port.highpower.PotentialBuffer.abort(player);cn.academy.port.highpower.RailThunderArray.abort(player);cn.academy.port.highpower.MagneticDomain.abort(player);cn.academy.port.highpower.DeflectionStrike.abort(player);cn.academy.port.highpower.YawPalm.abort(player);cn.academy.port.highpower.TrajectoryReturn.abort(player);cn.academy.port.highpower.ShearWindblade.abort(player);cn.academy.port.highpower.VectorParry.abort(player);cn.academy.port.highpower.InertialAnchor.abort(player);cn.academy.port.highpower.DualPoint.abort(player);cn.academy.port.highpower.ScatterPlates.abort(player);cn.academy.port.highpower.BeamEtching.abort(player);cn.academy.port.highpower.PointMeltdown.abort(player);cn.academy.port.highpower.RemoteScatterRelay.abort(player);cn.academy.port.highpower.ScatterLocatorPlate.abort(player);cn.academy.port.highpower.ThermalShell.abort(player);cn.academy.port.highpower.FoldedMirrorArray.abort(player);cn.academy.port.highpower.ParticleLaminate.abort(player);cn.academy.port.highpower.DissipationCurtain.abort(player);cn.academy.port.highpower.ZeroGapArmor.abort(player);cn.academy.port.highpower.MulticoreCanopy.abort(player);cn.academy.port.highpower.DisintegrationSweep.abort(player);cn.academy.port.highpower.BeamStep.abort(player);cn.academy.port.highpower.WindPressureLift.abort(player);}}
            }
            case "preset_edit" -> {
                String[] fields=request.value().split(":",-1);
                if(fields.length==3&&state.hasCategory()) {
                    int preset=presetIndex(fields[0]),slot=presetIndex(fields[1]);
                    cn.academy.port.highpower.BeamStep.abort(player);cn.academy.port.highpower.WindPressureLift.abort(player);
                    accepted=PresetSkills.edit(state,preset,slot,fields[2]);
                    // Classic client PresetUpdate runs individual key-abort callbacks; persistent contexts survive.
                    // Only modern server-owned Railgun countdown needs common transport cancellation here.
                    if(accepted){cancelRailgunForPreset(player,state);cn.academy.port.highpower.HighPowerBracerSession.abort(player);cn.academy.port.highpower.ElectromasterChargeDecoy.abort(player);cn.academy.port.highpower.PolarityTether.abort(player);cn.academy.port.highpower.ConductiveCircuit.abort(player);cn.academy.port.highpower.GroundingReturn.abort(player);cn.academy.port.highpower.MagneticGroundStep.abort(player);cn.academy.port.highpower.MagneticItemPull.abort(player);cn.academy.port.highpower.IronSandLance.abort(player);cn.academy.port.highpower.ThunderNet.abort(player);cn.academy.port.highpower.PotentialBuffer.abort(player);cn.academy.port.highpower.RailThunderArray.abort(player);cn.academy.port.highpower.MagneticDomain.abort(player);cn.academy.port.highpower.DeflectionStrike.abort(player);cn.academy.port.highpower.YawPalm.abort(player);cn.academy.port.highpower.TrajectoryReturn.abort(player);cn.academy.port.highpower.ShearWindblade.abort(player);cn.academy.port.highpower.VectorParry.abort(player);cn.academy.port.highpower.InertialAnchor.abort(player);cn.academy.port.highpower.DualPoint.abort(player);cn.academy.port.highpower.ScatterPlates.abort(player);cn.academy.port.highpower.BeamEtching.abort(player);cn.academy.port.highpower.PointMeltdown.abort(player);cn.academy.port.highpower.RemoteScatterRelay.abort(player);cn.academy.port.highpower.ScatterLocatorPlate.abort(player);cn.academy.port.highpower.ThermalShell.abort(player);cn.academy.port.highpower.FoldedMirrorArray.abort(player);cn.academy.port.highpower.ParticleLaminate.abort(player);cn.academy.port.highpower.DissipationCurtain.abort(player);cn.academy.port.highpower.ZeroGapArmor.abort(player);cn.academy.port.highpower.MulticoreCanopy.abort(player);cn.academy.port.highpower.DisintegrationSweep.abort(player);cn.academy.port.highpower.BeamStep.abort(player);cn.academy.port.highpower.WindPressureLift.abort(player);}
                }
            }
            case "slot_press_token" -> {
                String[] fields=request.value().split(":",-1);
                if(fields.length==2){int slot=presetIndex(fields[0]);String skill=state.presets.currentSkill(slot);
                    long input=0;try{if(fields[1].length()>0&&fields[1].length()<=19&&fields[1].charAt(0)!='0')input=Long.parseLong(fields[1]);}catch(NumberFormatException ignored){}
                    if(input>0&&fields[1].equals(Long.toString(input))&&cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill)==null&&SkillPresets.validSlot(slot)&&PresetSkills.mappedUsable(state,skill)&&state.canUse(skill)&&movementAvailable(player,skill)){
                        if(!skill.equals("flashing")&&cn.academy.port.skill.Flashing.active(player))cn.academy.port.skill.Flashing.abort(player);
                        if(skill.equals("dir_blast"))accepted=cn.academy.port.skill.DirectedBlastwave.start(player,input);
                        else if(skill.equals("storm_wing"))accepted=cn.academy.port.skill.StormWing.start(player,input,slot);
                        else if(skill.equals("blood_retro"))accepted=cn.academy.port.skill.BloodRetrograde.start(player,input);
                        else if(skill.equals("vec_reflection"))accepted=cn.academy.port.skill.VecReflection.start(player,input);
                        else if(skill.equals("plasma_cannon"))accepted=cn.academy.port.skill.PlasmaCannon.start(player,input);
                        else if(skill.equals("vec_accel"))accepted=cn.academy.port.skill.VecAccel.start(player,input);
                        else if(skill.equals("vec_deviation"))accepted=cn.academy.port.skill.VecDeviation.start(player,input);
                        else if(skill.equals("body_intensify"))accepted=cn.academy.port.skill.BodyIntensify.start(player,input);
                        else if(skill.equals("thunder_clap"))accepted=cn.academy.port.skill.ThunderClap.start(player,input);
                        else if(skill.equals("scatter_bomb"))accepted=cn.academy.port.skill.ScatterBomb.start(player,input);
                        else if(skill.equals("light_shield"))accepted=cn.academy.port.skill.LightShield.start(player,input);
                        else if(skill.equals("meltdowner"))accepted=cn.academy.port.skill.Meltdowner.start(player,input);
                        else if(skill.equals("mine_ray_basic"))accepted=cn.academy.port.skill.MineRayBasic.start(player,input);
                        else if(skill.equals("mine_ray_expert"))accepted=cn.academy.port.skill.MineRayExpert.start(player,input);
                        else if(skill.equals("mine_ray_luck"))accepted=cn.academy.port.skill.MineRayLuck.start(player,input);
                        else if(skill.equals("ray_barrage"))accepted=cn.academy.port.skill.RayBarrage.start(player,input);
                        else if(skill.equals("jet_engine"))accepted=cn.academy.port.skill.JetEngine.start(player,input);
                        else if(skill.equals("electron_missile"))accepted=cn.academy.port.skill.ElectronMissile.start(player,input);
                        else if(skill.equals("penetrate_teleport"))accepted=cn.academy.port.skill.PenetrateTeleport.start(player,input);
                        else if(skill.equals("mark_teleport"))accepted=cn.academy.port.skill.MarkTeleport.start(player,input);
                        else if(skill.equals("flesh_ripping"))accepted=cn.academy.port.skill.FleshRipping.start(player,input);
                        else if(skill.equals("location_teleport"))accepted=cn.academy.port.skill.LocationTeleport.start(player,input);
                        else if(skill.equals("shift_tp"))accepted=cn.academy.port.skill.ShiftTeleport.start(player,input);
                        else if(skill.equals("flashing"))accepted=cn.academy.port.skill.Flashing.start(player,input);
                    }
                }
            }
            case "storm_direction" -> {
                String[] fields=request.value().split(":",-1);
                if(fields.length==3){int slot=presetIndex(fields[0]);long nonce=0;int direction=-2;
                    try{nonce=Long.parseLong(fields[1]);direction=Integer.parseInt(fields[2]);}catch(NumberFormatException ignored){}
                    if(SkillPresets.validSlot(slot)&&nonce>0&&fields[1].equals(Long.toString(nonce))&&direction>=-1&&direction<=3&&fields[2].equals(Integer.toString(direction)))
                        accepted=cn.academy.port.skill.StormWing.direction(player,slot,nonce,direction);
                }
            }
            case "slot_release_token","slot_abort_token" -> {
                String[] fields=request.value().split(":",-1);
                if(fields.length==2){int slot=presetIndex(fields[0]);String skill=state.presets.currentSkill(slot);long nonce=0;
                    try{if(fields[1].length()>0&&fields[1].length()<=19&&fields[1].charAt(0)!='0')nonce=Long.parseLong(fields[1]);}catch(NumberFormatException ignored){}
                    if(nonce>0&&fields[1].equals(Long.toString(nonce))&&SkillPresets.validSlot(slot)&&PresetSkills.mappedUsable(state,skill)){
                        boolean abort=request.action().equals("slot_abort_token");
                        if(skill.equals("dir_blast"))accepted=abort?cn.academy.port.skill.DirectedBlastwave.abort(player,nonce):cn.academy.port.skill.DirectedBlastwave.release(player,nonce);
                        else if(skill.equals("storm_wing"))accepted=abort?cn.academy.port.skill.StormWing.abort(player,nonce):cn.academy.port.skill.StormWing.release(player,nonce);
                        else if(skill.equals("blood_retro"))accepted=abort?cn.academy.port.skill.BloodRetrograde.abort(player,nonce):cn.academy.port.skill.BloodRetrograde.release(player,nonce);
                        else if(skill.equals("vec_reflection"))accepted=abort?cn.academy.port.skill.VecReflection.abort(player,nonce):cn.academy.port.skill.VecReflection.release(player,nonce);
                        else if(skill.equals("plasma_cannon"))accepted=abort?cn.academy.port.skill.PlasmaCannon.abort(player,nonce):cn.academy.port.skill.PlasmaCannon.release(player,nonce);
                        else if(skill.equals("vec_accel"))accepted=abort?cn.academy.port.skill.VecAccel.abort(player,nonce):cn.academy.port.skill.VecAccel.release(player,nonce);
                        else if(skill.equals("vec_deviation"))accepted=abort?cn.academy.port.skill.VecDeviation.abort(player,nonce):cn.academy.port.skill.VecDeviation.release(player,nonce);
                        else if(skill.equals("jet_engine"))accepted=abort?cn.academy.port.skill.JetEngine.abort(player,nonce):cn.academy.port.skill.JetEngine.release(player,nonce);
                        else if(skill.equals("electron_missile"))accepted=abort?cn.academy.port.skill.ElectronMissile.abort(player,nonce):cn.academy.port.skill.ElectronMissile.release(player,nonce);
                        else if(skill.equals("penetrate_teleport"))accepted=abort?cn.academy.port.skill.PenetrateTeleport.abort(player,nonce):cn.academy.port.skill.PenetrateTeleport.release(player,nonce);
                        else if(skill.equals("mark_teleport"))accepted=abort?cn.academy.port.skill.MarkTeleport.abort(player,nonce):cn.academy.port.skill.MarkTeleport.release(player,nonce);
                        else if(skill.equals("flesh_ripping"))accepted=abort?cn.academy.port.skill.FleshRipping.abort(player,nonce):cn.academy.port.skill.FleshRipping.release(player,nonce);
                        else if(skill.equals("location_teleport"))accepted=abort?cn.academy.port.skill.LocationTeleport.abort(player,nonce):cn.academy.port.skill.LocationTeleport.release(player,nonce);
                        else if(skill.equals("shift_tp"))accepted=abort?cn.academy.port.skill.ShiftTeleport.abort(player,nonce):cn.academy.port.skill.ShiftTeleport.release(player,nonce);
                        else if(skill.equals("flashing"))accepted=abort?cn.academy.port.skill.Flashing.abort(player,nonce):cn.academy.port.skill.Flashing.release(player,nonce);
                    }
                }
            }
            case "slot_press","slot_release","slot_abort" -> {
                int slot=presetIndex(request.value());String skill=state.presets.currentSkill(slot);
                if(SkillPresets.validSlot(slot)&&PresetSkills.mappedUsable(state,skill)&&!Set.of("magnetic_ground_step","conductive_circuit","grounding_return","polarity_tether","charge_decoy","magnetic_bracer","dir_blast","storm_wing","blood_retro","ray_barrage","jet_engine","electron_missile","penetrate_teleport","mark_teleport","flesh_ripping","vec_accel","vec_deviation","location_teleport","shift_tp","flashing","vec_reflection","plasma_cannon","charging","mag_movement","mag_manip","ground_shock","dir_shock","threatening_teleport").contains(skill)) {
                    if(request.action().equals("slot_abort")) {abortSlot(player,skill);accepted=true;}
                    else if(request.action().equals("slot_release")) {releaseSlot(player,skill);accepted=true;}
                    else if(cn.academy.port.canon.CanonSkills.IDS.contains(skill)) {
                        cn.academy.port.highpower.HighPowerProgression.certifyCanon(player);
                        accepted=cn.academy.port.canon.CanonOffenseSkills.find(skill)!=null
                                ?cn.academy.port.canon.CanonOffense.cast(player,skill)
                                :cn.academy.port.canon.CanonSkillsCombatA.IMPLEMENTED.contains(skill)
                                ? cn.academy.port.canon.CanonSkillsCombatA.cast(player,skill)
                                : cn.academy.port.canon.CanonSkillsCombatB.cast(player,skill);
                        if(!accepted)cn.academy.port.canon.CanonCastHints.refused(player,skill);
                    }
                    else if(skill.equals(cn.academy.port.highpower.HighPowerSkills.FORKED_ARC)) {accepted=cn.academy.port.highpower.ElectromasterPrototype.castLearned(player);}
                    else if(state.canUse(skill)&&movementAvailable(player,skill)) {if(cn.academy.port.skill.Flashing.active(player))cn.academy.port.skill.Flashing.abort(player);pressSlot(player,skill);accepted=true;}
                }
            }
            case "location_add","location_remove","location_perform","location_query","location_close" -> {if((!request.action().equals("location_perform")||movementAvailable(player,"location_teleport"))&&PresetSkills.mappedUsable(state,"location_teleport")&&state.presets.currentContains("location_teleport"))accepted=cn.academy.port.skill.LocationTeleport.request(player,request.action(),request.value());}
            case "flashing_direction_owned" -> {
                var direction=TargetedContextTermination.parseFlashDirection(request.value());
                if(direction!=null)accepted=cn.academy.port.skill.Flashing.directionOwned(player,direction.input(),direction.token(),direction.sequence(),direction.key());
            }
            case "flashing_direction" -> {if(PresetSkills.mappedUsable(state,"flashing")&&state.presets.currentContains("flashing"))accepted=cn.academy.port.skill.Flashing.request(player,request.value());}
            case "flashing_close" -> {try{long nonce=Long.parseLong(request.value());if(nonce>0&&request.value().equals(Long.toString(nonce)))accepted=cn.academy.port.skill.Flashing.abort(player,nonce);}catch(NumberFormatException ignored){}}
            case "coin_attempt" -> {if(PresetSkills.mappedUsable(state,"railgun")&&state.presets.currentContains("railgun")&&state.canUse("railgun")){request(player,request);accepted=true;}}
            case "activate_state" -> {if(state.hasCategory()&&cn.academy.port.core.ActivationTransport.requested(request.value())!=null){request(player,request);accepted=true;}}
            case "toggle","abort_active","abort_delegates","develop_level","develop_abort","learn" -> {request(player,request);accepted=true;}
            default -> {} // Do not expose arbitrary skill names, casts, charges or aborts through network ingress.
        }
        AbilityStorage.save(player);AcademyNetwork.sync(player);return accepted;
    }
    private static void rejectSingleInput(ServerPlayer player,String skill,long input,int actorId,long ownerEpoch){
        var tag=new CompoundTag();tag.putString("kind","single_key_rejected");tag.putString("skill",skill);tag.putLong("input",input);tag.putInt("entity",actorId);tag.putUUID("entity_uuid",player.getUUID());tag.putLong("owner_epoch",ownerEpoch);
        PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(tag));
    }
    /** Preset flush and scoped early-abort cancel only the exact owner's pending Railgun timer. */
    private static boolean cancelRailgunForPreset(ServerPlayer player,AbilityProgress state) {
        if(player==null||state==null||player.hasDisconnected()||player.isRemoved()||!player.isAlive()||player.isSpectator()
                ||!player.serverLevel().getServer().isSameThread()||AbilityConsumption.busy(player)
                ||player.serverLevel().getEntity(player.getId())!=player||AbilityStorage.get(player)!=state)return false;
        var charge=CHARGES.get(player.getUUID());
        return charge!=null&&charge.owner()==player&&charge.level()==player.serverLevel()&&charge.state()==state&&CHARGES.remove(player.getUUID(),charge);
    }
    private static int presetIndex(String value) {return value!=null&&value.length()==1&&value.charAt(0)>='0'&&value.charAt(0)<='3'?value.charAt(0)-'0':-1;}
    private static void pressSlot(ServerPlayer player,String skill) {
        switch(skill) {
            case "arc_gen","electron_bomb","thunder_bolt","mine_detect" -> request(player,new AcademyNetwork.Request("cast",skill));
            case "charging","threatening_teleport","penetrate_teleport","mark_teleport","flesh_ripping","location_teleport","shift_tp","flashing","dir_shock","ground_shock","mag_movement","mag_manip","body_intensify","thunder_clap","scatter_bomb","light_shield","meltdowner","mine_ray_basic","mine_ray_expert","mine_ray_luck","ray_barrage","jet_engine","electron_missile" -> request(player,new AcademyNetwork.Request("skill_start",skill));
            case "railgun" -> request(player,new AcademyNetwork.Request("charge",""));
            default -> {}
        }
    }
    private static void releaseSlot(ServerPlayer player,String skill) {
        if(skill.equals("railgun"))request(player,new AcademyNetwork.Request("abort",""));
        else if(Set.of("charging","threatening_teleport","penetrate_teleport","mark_teleport","flesh_ripping","location_teleport","shift_tp","flashing","dir_shock","ground_shock","mag_movement","mag_manip","body_intensify","thunder_clap","scatter_bomb","light_shield","meltdowner","mine_ray_basic","mine_ray_expert","mine_ray_luck","ray_barrage","jet_engine","electron_missile").contains(skill))request(player,new AcademyNetwork.Request("skill_release",skill));
    }
    private static void abortSlot(ServerPlayer player,String skill) {
        if(skill.equals("railgun"))request(player,new AcademyNetwork.Request("abort",""));
        else if(Set.of("charging","threatening_teleport","penetrate_teleport","mark_teleport","flesh_ripping","location_teleport","shift_tp","flashing","dir_shock","ground_shock","mag_movement","mag_manip","body_intensify","thunder_clap","scatter_bomb","light_shield","meltdowner","mine_ray_basic","mine_ray_expert","mine_ray_luck","ray_barrage","jet_engine","electron_missile").contains(skill))request(player,new AcademyNetwork.Request("skill_abort",skill));
    }
    public static void request(ServerPlayer player,AcademyNetwork.Request request) {
        if(player==null||player.isRemoved()||!player.isAlive()||player.isSpectator())return;
        if(request.action().equals("skill_start")&&!movementAvailable(player,request.value()))return;
        var s=AbilityStorage.get(player);
        switch(request.action()) {
            case "abort_active" -> abortHolds(player);
            case "abort_delegates" -> abortHolds(player,true);
            case "activate_state" -> {Boolean active=cn.academy.port.core.ActivationTransport.requested(request.value());if(s.hasCategory()&&active!=null){boolean changed=s.activated!=active;if(changed)cn.academy.port.highpower.WindPressureLift.abort(player);s.setActivateState(active);if(changed&&!s.activated)abortHolds(player,true);}}
            case "toggle" -> {if(s.hasCategory()){cn.academy.port.highpower.WindPressureLift.abort(player);s.setActivateState(!s.isActivated());if(!s.activated)abortHolds(player,true);}}
            case "cast" -> {switch(request.value()){case "arc_gen" -> arc(player);case "electron_bomb" -> ElectronBomb.perform(player);case "thunder_bolt" -> cn.academy.port.skill.ThunderBolt.perform(player);case "mine_detect" -> cn.academy.port.skill.MineDetect.perform(player);default -> {}}}
            case "skill_start" -> {switch(request.value()){case "dir_blast" -> cn.academy.port.skill.DirectedBlastwave.start(player);case "storm_wing" -> cn.academy.port.skill.StormWing.start(player);case "blood_retro" -> cn.academy.port.skill.BloodRetrograde.start(player);case "vec_reflection" -> cn.academy.port.skill.VecReflection.start(player);case "plasma_cannon" -> cn.academy.port.skill.PlasmaCannon.start(player);case "vec_accel" -> cn.academy.port.skill.VecAccel.start(player);case "vec_deviation" -> cn.academy.port.skill.VecDeviation.start(player);case "threatening_teleport" -> ThreateningTeleport.start(player);case "penetrate_teleport" -> cn.academy.port.skill.PenetrateTeleport.start(player);case "mark_teleport" -> cn.academy.port.skill.MarkTeleport.start(player);case "flesh_ripping" -> cn.academy.port.skill.FleshRipping.start(player);case "location_teleport" -> cn.academy.port.skill.LocationTeleport.start(player);case "shift_tp" -> cn.academy.port.skill.ShiftTeleport.start(player);case "flashing" -> cn.academy.port.skill.Flashing.start(player);case "dir_shock" -> DirectedShock.start(player);case "charging" -> CurrentCharging.start(player);case "ground_shock" -> GroundShock.start(player);case "mag_movement" -> cn.academy.port.skill.MagMovement.start(player);case "mag_manip" -> cn.academy.port.skill.MagManip.start(player);case "body_intensify" -> cn.academy.port.skill.BodyIntensify.start(player);case "thunder_clap" -> cn.academy.port.skill.ThunderClap.start(player);case "scatter_bomb" -> cn.academy.port.skill.ScatterBomb.start(player);case "light_shield" -> cn.academy.port.skill.LightShield.start(player);case "meltdowner" -> cn.academy.port.skill.Meltdowner.start(player);case "mine_ray_basic" -> cn.academy.port.skill.MineRayBasic.start(player);case "mine_ray_expert" -> cn.academy.port.skill.MineRayExpert.start(player);case "mine_ray_luck" -> cn.academy.port.skill.MineRayLuck.start(player);case "ray_barrage" -> cn.academy.port.skill.RayBarrage.start(player);case "jet_engine" -> cn.academy.port.skill.JetEngine.start(player);case "electron_missile" -> cn.academy.port.skill.ElectronMissile.start(player);default -> {}}}
            case "skill_release" -> {switch(request.value()){case "vec_reflection" -> cn.academy.port.skill.VecReflection.release(player);case "plasma_cannon" -> cn.academy.port.skill.PlasmaCannon.release(player);case "dir_blast" -> cn.academy.port.skill.DirectedBlastwave.release(player);case "storm_wing" -> cn.academy.port.skill.StormWing.release(player);case "blood_retro" -> cn.academy.port.skill.BloodRetrograde.release(player);case "vec_accel" -> cn.academy.port.skill.VecAccel.release(player);case "vec_deviation" -> cn.academy.port.skill.VecDeviation.release(player);case "threatening_teleport" -> ThreateningTeleport.release(player);case "penetrate_teleport" -> cn.academy.port.skill.PenetrateTeleport.release(player);case "mark_teleport" -> cn.academy.port.skill.MarkTeleport.release(player);case "flesh_ripping" -> cn.academy.port.skill.FleshRipping.release(player);case "location_teleport" -> cn.academy.port.skill.LocationTeleport.release(player);case "shift_tp" -> cn.academy.port.skill.ShiftTeleport.release(player);case "flashing" -> cn.academy.port.skill.Flashing.release(player);case "dir_shock" -> DirectedShock.release(player);case "charging" -> CurrentCharging.release(player);case "ground_shock" -> GroundShock.release(player);case "mag_movement" -> cn.academy.port.skill.MagMovement.release(player);case "mag_manip" -> cn.academy.port.skill.MagManip.release(player);case "body_intensify" -> cn.academy.port.skill.BodyIntensify.release(player);case "thunder_clap" -> cn.academy.port.skill.ThunderClap.release(player);case "scatter_bomb" -> cn.academy.port.skill.ScatterBomb.release(player);case "light_shield" -> cn.academy.port.skill.LightShield.release(player);case "meltdowner" -> cn.academy.port.skill.Meltdowner.release(player);case "mine_ray_basic" -> cn.academy.port.skill.MineRayBasic.release(player);case "mine_ray_expert" -> cn.academy.port.skill.MineRayExpert.release(player);case "mine_ray_luck" -> cn.academy.port.skill.MineRayLuck.release(player);case "ray_barrage" -> cn.academy.port.skill.RayBarrage.release(player);case "jet_engine" -> cn.academy.port.skill.JetEngine.release(player);case "electron_missile" -> cn.academy.port.skill.ElectronMissile.release(player);default -> {}}}
            case "skill_abort" -> {switch(request.value()){case "dir_blast" -> cn.academy.port.skill.DirectedBlastwave.abort(player);case "storm_wing" -> cn.academy.port.skill.StormWing.abort(player);case "blood_retro" -> cn.academy.port.skill.BloodRetrograde.abort(player);case "vec_reflection" -> cn.academy.port.skill.VecReflection.abort(player);case "plasma_cannon" -> cn.academy.port.skill.PlasmaCannon.abort(player);case "vec_accel" -> cn.academy.port.skill.VecAccel.abort(player);case "vec_deviation" -> cn.academy.port.skill.VecDeviation.abort(player);case "threatening_teleport" -> ThreateningTeleport.abort(player);case "penetrate_teleport" -> cn.academy.port.skill.PenetrateTeleport.abort(player);case "mark_teleport" -> cn.academy.port.skill.MarkTeleport.abort(player);case "flesh_ripping" -> cn.academy.port.skill.FleshRipping.abort(player);case "location_teleport" -> cn.academy.port.skill.LocationTeleport.abort(player);case "shift_tp" -> cn.academy.port.skill.ShiftTeleport.abort(player);case "flashing" -> cn.academy.port.skill.Flashing.abort(player);case "dir_shock" -> DirectedShock.abort(player);case "charging" -> CurrentCharging.abort(player);case "ground_shock" -> GroundShock.abort(player);case "mag_movement" -> cn.academy.port.skill.MagMovement.abort(player);case "mag_manip" -> cn.academy.port.skill.MagManip.abort(player);case "body_intensify" -> cn.academy.port.skill.BodyIntensify.abort(player);case "thunder_clap" -> cn.academy.port.skill.ThunderClap.abort(player);case "scatter_bomb" -> cn.academy.port.skill.ScatterBomb.abort(player);case "light_shield" -> cn.academy.port.skill.LightShield.abort(player);case "meltdowner" -> cn.academy.port.skill.Meltdowner.abort(player);case "mine_ray_basic" -> cn.academy.port.skill.MineRayBasic.abort(player);case "mine_ray_expert" -> cn.academy.port.skill.MineRayExpert.abort(player);case "mine_ray_luck" -> cn.academy.port.skill.MineRayLuck.abort(player);case "ray_barrage" -> cn.academy.port.skill.RayBarrage.abort(player);case "jet_engine" -> cn.academy.port.skill.JetEngine.abort(player);case "electron_missile" -> cn.academy.port.skill.ElectronMissile.abort(player);default -> {}}}
            case "coin_attempt" -> {try{long token=Long.parseLong(request.value());if(CoinTosses.attempt(player,token))railgun(player,false);}catch(NumberFormatException ignored){}}
            case "charge" -> {if(s.canUse("railgun")&&ammo(player)&&!CHARGES.containsKey(player.getUUID())) {CHARGES.put(player.getUUID(),new RailgunCharge(player,player.serverLevel(),s,20));AcademyNetwork.effect(player,"charge",0);}}
            case "abort" -> CHARGES.remove(player.getUUID());
            case "develop_level" -> DevelopmentController.startLevel(player,InductionFactors.inventory(player));
            case "develop_abort" -> DevelopmentController.abort(player);
            case "learn" -> {if(SkillAvailability.learnable(request.value()))DevelopmentController.startSkill(player,request.value());}
            default -> {}
        }
        AbilityStorage.save(player);AcademyNetwork.sync(player);
    }
    public static void cancelRailgunCharge(ServerPlayer player) {CHARGES.remove(player.getUUID());}
    /** Modern lifecycle fence for source administrative category/unlearn/level mutations. */
    public static void cancelForAdministrativeMutation(ServerPlayer player){abortHolds(player);ElectronBomb.abort(player);DevelopmentController.remove(player);}
    /** Source category disposal ends skill contexts while the completing development/session remains owned. */
    public static void disposeAbilityContexts(ServerPlayer player){if(player!=null&&player.server.isSameThread())abortHolds(player);}
    private static void abortHolds(ServerPlayer player) {abortHolds(player,false);}
    /** Source contextActivate survives delegate flushes; V/explicit lifecycle disposal still ends it. */
    private static void abortHolds(ServerPlayer player,boolean preserveDeviation) {cn.academy.port.highpower.NewSkillRuntime.abort(player);cn.academy.port.highpower.EmergencyReturn.cancelActive(player);cn.academy.port.highpower.SpatialRelay.cancelActive(player);cn.academy.port.highpower.ShortInterception.cancelActive(player);cn.academy.port.highpower.CornerExchange.cancelActive(player);cn.academy.port.highpower.CrossSubstitution.cancelActive(player);cn.academy.port.highpower.RecoveryMark.cancelActive(player);cn.academy.port.highpower.ReturnMark.cancelActive(player);cn.academy.port.highpower.DisplacedProjection.cancelActive(player);cn.academy.port.highpower.PhaseSidestep.cancelActive(player);cn.academy.port.highpower.HighPowerBracerSession.abort(player);cn.academy.port.highpower.ElectromasterChargeDecoy.abort(player);cn.academy.port.highpower.PolarityTether.abort(player);cn.academy.port.highpower.ConductiveCircuit.abort(player);cn.academy.port.highpower.GroundingReturn.abort(player);cn.academy.port.highpower.MagneticGroundStep.abort(player);cn.academy.port.highpower.MagneticItemPull.abort(player);cn.academy.port.highpower.IronSandLance.abort(player);cn.academy.port.highpower.ThunderNet.abort(player);cn.academy.port.highpower.PotentialBuffer.abort(player);cn.academy.port.highpower.RailThunderArray.abort(player);cn.academy.port.highpower.MagneticDomain.abort(player);cn.academy.port.highpower.DeflectionStrike.abort(player);cn.academy.port.highpower.YawPalm.abort(player);cn.academy.port.highpower.TrajectoryReturn.abort(player);cn.academy.port.highpower.ShearWindblade.abort(player);cn.academy.port.highpower.VectorParry.abort(player);cn.academy.port.highpower.InertialAnchor.abort(player);cn.academy.port.highpower.DualPoint.abort(player);cn.academy.port.highpower.ScatterPlates.abort(player);cn.academy.port.highpower.BeamEtching.abort(player);cn.academy.port.highpower.PointMeltdown.abort(player);cn.academy.port.highpower.RemoteScatterRelay.abort(player);cn.academy.port.highpower.ScatterLocatorPlate.abort(player);cn.academy.port.highpower.ThermalShell.abort(player);cn.academy.port.highpower.FoldedMirrorArray.abort(player);cn.academy.port.highpower.ParticleLaminate.abort(player);cn.academy.port.highpower.DissipationCurtain.abort(player);cn.academy.port.highpower.ZeroGapArmor.abort(player);cn.academy.port.highpower.MulticoreCanopy.abort(player);cn.academy.port.highpower.DisintegrationSweep.abort(player);cn.academy.port.highpower.BeamStep.abort(player);cn.academy.port.highpower.WindPressureLift.abort(player);cn.academy.port.skill.DirectedBlastwave.abort(player);cn.academy.port.skill.BloodRetrograde.abort(player);if(!preserveDeviation)cn.academy.port.skill.StormWing.abort(player);cn.academy.port.skill.VecAccel.abort(player);if(!preserveDeviation){cn.academy.port.skill.VecDeviation.abort(player);cn.academy.port.skill.VecReflection.abort(player);}cn.academy.port.skill.PlasmaCannon.abort(player);cn.academy.port.skill.RayBarrage.abort(player);cn.academy.port.skill.JetEngine.abort(player);cn.academy.port.skill.ElectronMissile.abort(player);cn.academy.port.skill.Meltdowner.abort(player);cn.academy.port.skill.MineRayBasic.abort(player);cn.academy.port.skill.MineRayExpert.abort(player);cn.academy.port.skill.MineRayLuck.abort(player);cn.academy.port.skill.ScatterBomb.abort(player);cn.academy.port.skill.LightShield.abort(player);CurrentCharging.abort(player);cn.academy.port.skill.BodyIntensify.abort(player);cn.academy.port.skill.ThunderClap.abort(player);GroundShock.abort(player);cn.academy.port.skill.MagMovement.abort(player);cn.academy.port.skill.MagManip.abort(player);DirectedShock.abort(player);ThreateningTeleport.abort(player);cn.academy.port.skill.PenetrateTeleport.abort(player);cn.academy.port.skill.MarkTeleport.abort(player);cn.academy.port.skill.FleshRipping.abort(player);cn.academy.port.skill.LocationTeleport.abort(player);cn.academy.port.skill.ShiftTeleport.abort(player);cn.academy.port.skill.Flashing.abort(player);CHARGES.remove(player.getUUID());}
    public static boolean holdingDeveloper(ServerPlayer p) {return p.getMainHandItem().is(AcademyCraft.DEVELOPER.get())||p.getOffhandItem().is(AcademyCraft.DEVELOPER.get());}
    public static boolean ammo(ServerPlayer p) {return p.getMainHandItem().is(Items.IRON_INGOT)||p.getMainHandItem().is(Items.IRON_BLOCK);}
    public static boolean arc(ServerPlayer player) {
        if(player==null||player.isRemoved()||!player.isAlive()||player.isSpectator())return false;
        var s=AbilityStorage.get(player);if(!s.canUse("arc_gen"))return false;double e=s.exp("arc_gen");var cost=ClassicRules.arc(e);
        if(!s.consumeSkill("arc_gen",cost.cp(),cost.overload(),player.getAbilities().instabuild))return false;
        var world=player.serverLevel();var from=player.getEyePosition();var end=from.add(player.getLookAngle().scale(cost.range()));
        var hit=ClassicRaytrace.living(player,cost.range(),ClipContext.Fluid.WATER);
        var entity=hit instanceof EntityHitResult eh?eh:null;
        var block=hit instanceof BlockHitResult bh?bh:null;
        // Keep the accepted contact before damage can remove the target from the client world.
        var point=entity==null?hit.getLocation():entity.getEntity().getBoundingBox().inflate(.1).clip(from,end).orElse(hit.getLocation());
        if(entity!=null) {
            AbilityDamage.attack(player,"electromaster.arc_gen",entity.getEntity(),cost.damage());
            if(entity.getEntity() instanceof Creeper creeper&&world.random.nextFloat()<.3f) {var tag=new CompoundTag();creeper.addAdditionalSaveData(tag);tag.putBoolean("powered",true);creeper.readAdditionalSaveData(tag);cn.academy.port.achievements.ClassicAchievements.trigger(player,"electromaster.attack_creeper");}
            s.addExperience("arc_gen",ClassicRules.lerp(.0048,.0072,e));
        } else if(block!=null&&block.getType()==HitResult.Type.BLOCK) {
            var pos=block.getBlockPos();var state=world.getBlockState(pos);
            if(state.is(Blocks.WATER)&&state.getFluidState().isSource()) {if(e>.5&&world.random.nextDouble()<.1){world.addFreshEntity(new ItemEntity(world,block.getLocation().x,block.getLocation().y,block.getLocation().z,new ItemStack(Items.COOKED_COD)));cn.academy.port.achievements.ClassicAchievements.trigger(player,"electromaster.arc_gen");}}
            else if(world.random.nextDouble()<ClassicRules.lerp(0,.6,e)&&world.isEmptyBlock(pos.above())&&world.mayInteract(player,pos.above()))world.setBlock(pos.above(),Blocks.FIRE.defaultBlockState(),3);
            s.addExperience("arc_gen",ClassicRules.lerp(.0018,.0027,e));
        }
        s.setCooldown("arc_gen",ClassicRules.arc(s.exp("arc_gen")).cooldown());AbilityStorage.save(player);
        var stroke=point.subtract(from);AcademyNetwork.effect(player,"arc",from,stroke.lengthSqr()>1e-8?stroke.normalize():ClassicRaytrace.direction(player),Math.min(cost.range(),stroke.length()));return true;
    }
    private static boolean railgun(ServerPlayer player) {return railgun(player,true);}
    private static boolean railgun(ServerPlayer player,boolean ironAmmo) {
        if(player==null||player.isRemoved()||!player.isAlive()||player.isSpectator())return false;
        var s=AbilityStorage.get(player);if(!s.canUse("railgun")||(ironAmmo&&!ammo(player)))return false;var cost=ClassicRules.railgun(s.exp("railgun"));
        // Preserve legacy order: ammunition is consumed before the CP check.
        if(ironAmmo&&!player.getAbilities().instabuild)player.getMainHandItem().shrink(1);
        if(!s.consumeSkill("railgun",cost.cp(),cost.overload(),player.getAbilities().instabuild))return false;
        var shotWorld=player.serverLevel();
        var shotDirection=ClassicRaytrace.direction(player).normalize();
        var shotOrigin=player.getEyePosition();
        var result=RailgunDamage.perform(player,(float)cost.damage(),(float)ClassicRules.lerp(900,2000,s.exp("railgun")));
        var contact=RailgunVisualContact.sample(player,shotWorld,shotOrigin,shotDirection,result.beamLength());
        // Legacy normal hits award .005; the shared reflection flag bug is intentionally not reproduced.
        cn.academy.port.achievements.ClassicAchievements.trigger(player,"electromaster.railgun");s.addExperience("railgun",result.reflectedHit()?.01:.005);s.setCooldown("railgun",cost.cooldown());AbilityStorage.save(player);
        if(player.serverLevel()==shotWorld)AcademyNetwork.railgun(player,shotOrigin,shotDirection,result.beamLength(),contact);
        return true;
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;var s=AbilityStorage.get(p);s.tick();DevelopmentController.tick(p,snapshot->{var tag=DevelopmentController.encode(snapshot);tag.putString("kind","development");PacketDistributor.sendToPlayer(p,new AcademyNetwork.ClientData(tag));});CoinTosses.tick(p);cn.academy.port.skill.DirectedBlastwave.tick(p);cn.academy.port.skill.BloodRetrograde.tick(p);cn.academy.port.skill.StormWing.tick(p);cn.academy.port.skill.VecReflection.tick(p);cn.academy.port.skill.PlasmaCannon.tick(p);cn.academy.port.skill.VecAccel.tick(p);cn.academy.port.skill.VecDeviation.tick(p);CurrentCharging.tick(p);cn.academy.port.skill.BodyIntensify.tick(p);cn.academy.port.skill.ThunderClap.tick(p);GroundShock.tick(p);cn.academy.port.skill.MagMovement.tick(p);cn.academy.port.skill.MagManip.tick(p);DirectedShock.tick(p);ThreateningTeleport.tick(p);cn.academy.port.skill.PenetrateTeleport.tick(p);cn.academy.port.skill.MarkTeleport.tick(p);cn.academy.port.skill.FleshRipping.tick(p);cn.academy.port.skill.LocationTeleport.tick(p);cn.academy.port.skill.ShiftTeleport.tick(p);cn.academy.port.skill.Flashing.tick(p);ElectronBomb.tick(p);cn.academy.port.skill.ScatterBomb.tick(p);cn.academy.port.skill.LightShield.tick(p);cn.academy.port.skill.Meltdowner.tick(p);cn.academy.port.skill.MineRayBasic.tick(p);cn.academy.port.skill.MineRayExpert.tick(p);cn.academy.port.skill.MineRayLuck.tick(p);cn.academy.port.skill.JetEngine.tick(p);cn.academy.port.skill.ElectronMissile.tick(p);
        if(cn.academy.port.highpower.EsperShields.eligible(p)||cn.academy.port.highpower.HighPowerCategories.isMeltdowner(s.category)&&s.learned(cn.academy.port.highpower.HighPowerSkills.SCATTER_PLATES)
                ||cn.academy.port.highpower.HighPowerCategories.isTeleporter(s.category)&&s.learned(cn.academy.port.highpower.HighPowerSkills.EMERGENCY_RETURN)
                ||p.isAlive()&&!p.isRemoved()&&cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(s.category)
                    &&(s.learned(cn.academy.port.highpower.HighPowerSkills.VECTOR_PARRY)||s.learned(cn.academy.port.highpower.HighPowerSkills.MULTI_AXIS_REFLECTION)))
            cn.academy.port.highpower.HighPowerCombat.tickShield(p);
        var charge=CHARGES.get(p.getUUID());
        if(charge!=null) {if(!p.isAlive()||p.isRemoved()||p.isSpectator()||charge.owner()!=p||charge.level()!=p.serverLevel()||charge.state()!=s||!ammo(p)||!s.canUse("railgun"))CHARGES.remove(p.getUUID());else if(charge.remaining()<=1) {CHARGES.remove(p.getUUID());railgun(p);}else CHARGES.put(p.getUUID(),new RailgunCharge(charge.owner(),charge.level(),charge.state(),charge.remaining()-1));}
        if(p.tickCount%10==0){AbilityStorage.save(p);AcademyNetwork.sync(p);}
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {if(event.getEntity() instanceof ServerPlayer p){cn.academy.port.highpower.ZeroGapArmor.remove(p);cn.academy.port.skill.PenetrateTeleport.remove(p);cn.academy.port.skill.MarkTeleport.remove(p);cn.academy.port.skill.FleshRipping.remove(p);cn.academy.port.skill.VecReflection.remove(p);cn.academy.port.skill.PlasmaCannon.remove(p);cn.academy.port.skill.LocationTeleport.remove(p);cn.academy.port.skill.ShiftTeleport.remove(p);cn.academy.port.skill.Flashing.remove(p);cn.academy.port.skill.VecAccel.remove(p);cn.academy.port.skill.VecDeviation.remove(p);cn.academy.port.skill.DirectedBlastwave.remove(p);cn.academy.port.skill.BloodRetrograde.remove(p);cn.academy.port.skill.StormWing.remove(p);cn.academy.port.skill.RayBarrage.remove(p);cn.academy.port.skill.JetEngine.remove(p);cn.academy.port.skill.ElectronMissile.remove(p);cn.academy.port.skill.Meltdowner.remove(p);cn.academy.port.skill.MineRayBasic.remove(p);cn.academy.port.skill.MineRayExpert.remove(p);cn.academy.port.skill.MineRayLuck.remove(p);cn.academy.port.skill.ScatterBomb.remove(p);cn.academy.port.skill.LightShield.remove(p);abortHolds(p);CoinTosses.abort(p);ElectronBomb.abort(p);DevelopmentController.remove(p);AcademyNetwork.sync(p);}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {if(event.getEntity() instanceof ServerPlayer p){AcademyNetwork.sync(p);syncDevelopment(p);}}
    private static void syncDevelopment(ServerPlayer player){var tag=DevelopmentController.encode(DevelopmentController.process(player).snapshot());tag.putString("kind","development");PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(tag));}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer p){AcademyNetwork.sync(p);syncDevelopment(p);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {if(event.getEntity() instanceof ServerPlayer p){cn.academy.port.highpower.ZeroGapArmor.remove(p);cn.academy.port.skill.PenetrateTeleport.remove(p);cn.academy.port.skill.MarkTeleport.remove(p);cn.academy.port.skill.FleshRipping.remove(p);cn.academy.port.skill.VecReflection.remove(p);cn.academy.port.skill.PlasmaCannon.remove(p);cn.academy.port.skill.LocationTeleport.remove(p);cn.academy.port.skill.ShiftTeleport.remove(p);cn.academy.port.skill.Flashing.remove(p);cn.academy.port.skill.VecAccel.remove(p);cn.academy.port.skill.VecDeviation.remove(p);cn.academy.port.skill.DirectedBlastwave.remove(p);cn.academy.port.skill.BloodRetrograde.remove(p);cn.academy.port.skill.StormWing.remove(p);cn.academy.port.skill.RayBarrage.remove(p);cn.academy.port.skill.JetEngine.remove(p);cn.academy.port.skill.ElectronMissile.remove(p);cn.academy.port.skill.Meltdowner.remove(p);cn.academy.port.skill.MineRayBasic.remove(p);cn.academy.port.skill.MineRayExpert.remove(p);cn.academy.port.skill.MineRayLuck.remove(p);cn.academy.port.skill.ScatterBomb.remove(p);cn.academy.port.skill.LightShield.remove(p);DevelopmentController.remove(p);CoinTosses.abort(p);CurrentCharging.remove(p);cn.academy.port.skill.BodyIntensify.remove(p);cn.academy.port.skill.ThunderClap.remove(p);GroundShock.remove(p);cn.academy.port.skill.MagMovement.remove(p);cn.academy.port.skill.MagManip.remove(p);DirectedShock.remove(p);ThreateningTeleport.remove(p);cn.academy.port.skill.PenetrateTeleport.abort(p);cn.academy.port.skill.MarkTeleport.abort(p);cn.academy.port.skill.FleshRipping.abort(p);cn.academy.port.skill.LocationTeleport.abort(p);cn.academy.port.skill.ShiftTeleport.abort(p);cn.academy.port.skill.Flashing.abort(p);ElectronBomb.abort(p);cn.academy.port.core.LegacySingleKeyOwnerEpoch.forget(p);AbilityStorage.remove(p);CHARGES.remove(p.getUUID());}}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {if(event.getOriginal() instanceof ServerPlayer old&&event.getEntity() instanceof ServerPlayer p){cn.academy.port.highpower.ZeroGapArmor.remove(old);cn.academy.port.skill.PenetrateTeleport.remove(old);cn.academy.port.skill.MarkTeleport.remove(old);cn.academy.port.skill.FleshRipping.remove(old);cn.academy.port.skill.VecReflection.remove(old);cn.academy.port.skill.PlasmaCannon.remove(old);cn.academy.port.skill.LocationTeleport.remove(old);cn.academy.port.skill.ShiftTeleport.remove(old);cn.academy.port.skill.Flashing.remove(old);cn.academy.port.skill.VecAccel.remove(old);cn.academy.port.skill.VecDeviation.remove(old);cn.academy.port.skill.DirectedBlastwave.remove(old);cn.academy.port.skill.BloodRetrograde.remove(old);cn.academy.port.skill.StormWing.remove(old);cn.academy.port.skill.RayBarrage.remove(old);cn.academy.port.skill.JetEngine.remove(old);cn.academy.port.skill.ElectronMissile.remove(old);cn.academy.port.skill.Meltdowner.remove(old);cn.academy.port.skill.MineRayBasic.remove(old);cn.academy.port.skill.MineRayExpert.remove(old);cn.academy.port.skill.MineRayLuck.remove(old);cn.academy.port.skill.ScatterBomb.remove(old);cn.academy.port.skill.LightShield.remove(old);if(event.isWasDeath()){var state=AbilityStorage.get(old);state.recoverAll();state.setActivateState(false);state.cooldowns.clear();state.cooldownMaxTicks.clear();}DevelopmentController.remove(old);CoinTosses.abort(old);CurrentCharging.remove(old);cn.academy.port.skill.BodyIntensify.remove(old);cn.academy.port.skill.ThunderClap.remove(old);GroundShock.remove(old);cn.academy.port.skill.MagMovement.remove(old);cn.academy.port.skill.MagManip.remove(old);DirectedShock.remove(old);ThreateningTeleport.remove(old);cn.academy.port.skill.PenetrateTeleport.abort(old);cn.academy.port.skill.MarkTeleport.abort(old);cn.academy.port.skill.FleshRipping.abort(old);cn.academy.port.skill.LocationTeleport.abort(old);cn.academy.port.skill.ShiftTeleport.abort(old);cn.academy.port.skill.Flashing.abort(old);ElectronBomb.abort(old);cn.academy.port.core.LegacySingleKeyOwnerEpoch.forget(old);cn.academy.port.skill.LocationTeleport.copyLocations(old,p);AbilityStorage.clone(old,p);CHARGES.remove(p.getUUID());}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {if(event.getEntity() instanceof ServerPlayer p){cn.academy.port.highpower.ZeroGapArmor.remove(p);cn.academy.port.skill.PenetrateTeleport.remove(p);cn.academy.port.skill.MarkTeleport.remove(p);cn.academy.port.skill.FleshRipping.remove(p);cn.academy.port.skill.LocationTeleport.remove(p);cn.academy.port.skill.ShiftTeleport.remove(p);cn.academy.port.skill.Flashing.remove(p);cn.academy.port.skill.VecReflection.remove(p);cn.academy.port.skill.PlasmaCannon.remove(p);cn.academy.port.skill.VecAccel.remove(p);cn.academy.port.skill.VecDeviation.remove(p);cn.academy.port.skill.DirectedBlastwave.remove(p);cn.academy.port.skill.BloodRetrograde.remove(p);cn.academy.port.skill.StormWing.remove(p);cn.academy.port.skill.RayBarrage.remove(p);cn.academy.port.skill.JetEngine.remove(p);cn.academy.port.skill.ElectronMissile.remove(p);cn.academy.port.skill.Meltdowner.remove(p);cn.academy.port.skill.MineRayBasic.remove(p);cn.academy.port.skill.MineRayExpert.remove(p);cn.academy.port.skill.MineRayLuck.remove(p);cn.academy.port.skill.ScatterBomb.remove(p);cn.academy.port.skill.LightShield.remove(p);DevelopmentController.remove(p);CoinTosses.abort(p);CurrentCharging.remove(p);cn.academy.port.skill.BodyIntensify.remove(p);cn.academy.port.skill.ThunderClap.remove(p);GroundShock.remove(p);cn.academy.port.skill.MagMovement.remove(p);cn.academy.port.skill.MagManip.remove(p);DirectedShock.remove(p);ThreateningTeleport.remove(p);cn.academy.port.skill.PenetrateTeleport.abort(p);cn.academy.port.skill.MarkTeleport.abort(p);cn.academy.port.skill.FleshRipping.abort(p);cn.academy.port.skill.LocationTeleport.abort(p);cn.academy.port.skill.ShiftTeleport.abort(p);cn.academy.port.skill.Flashing.abort(p);ElectronBomb.abort(p);cn.academy.port.core.LegacySingleKeyOwnerEpoch.forget(p);CHARGES.remove(p.getUUID());var state=AbilityStorage.get(p);state.recoverAll();state.setActivateState(false);state.cooldowns.clear();state.cooldownMaxTicks.clear();AbilityStorage.save(p);}}
    @SubscribeEvent public static void wake(PlayerWakeUpEvent event) {if(!event.wakeImmediately()&&!event.updateLevel()&&event.getEntity() instanceof ServerPlayer p){AbilityStorage.get(p).recoverAll();AbilityStorage.save(p);AcademyNetwork.sync(p);}}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {CoinTosses.clear();for(var player:event.getServer().getPlayerList().getPlayers()){cn.academy.port.highpower.ZeroGapArmor.remove(player);cn.academy.port.skill.PenetrateTeleport.remove(player);cn.academy.port.skill.MarkTeleport.remove(player);cn.academy.port.skill.FleshRipping.remove(player);cn.academy.port.skill.VecReflection.remove(player);cn.academy.port.skill.PlasmaCannon.remove(player);cn.academy.port.skill.LocationTeleport.remove(player);cn.academy.port.skill.ShiftTeleport.remove(player);cn.academy.port.skill.Flashing.remove(player);cn.academy.port.skill.VecAccel.remove(player);cn.academy.port.skill.VecDeviation.remove(player);cn.academy.port.skill.DirectedBlastwave.remove(player);cn.academy.port.skill.BloodRetrograde.remove(player);cn.academy.port.skill.StormWing.remove(player);cn.academy.port.skill.RayBarrage.remove(player);cn.academy.port.skill.JetEngine.remove(player);cn.academy.port.skill.ElectronMissile.remove(player);cn.academy.port.skill.Meltdowner.remove(player);cn.academy.port.skill.MineRayBasic.remove(player);cn.academy.port.skill.MineRayExpert.remove(player);cn.academy.port.skill.MineRayLuck.remove(player);cn.academy.port.skill.ScatterBomb.remove(player);cn.academy.port.skill.LightShield.remove(player);abortHolds(player);AbilityStorage.save(player);}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {cn.academy.port.highpower.ZeroGapArmor.clear();cn.academy.port.core.LegacySingleKeyOwnerEpoch.clear();DevelopmentController.clear();CoinTosses.clear();CurrentCharging.clear();cn.academy.port.skill.BodyIntensify.clear();cn.academy.port.skill.ThunderClap.clear();GroundShock.clear();cn.academy.port.skill.MagMovement.clear();cn.academy.port.skill.MagManip.clear();DirectedShock.clear();ThreateningTeleport.clear();cn.academy.port.skill.PenetrateTeleport.clear();cn.academy.port.skill.MarkTeleport.clear();cn.academy.port.skill.FleshRipping.clear();cn.academy.port.skill.LocationTeleport.clear();cn.academy.port.skill.ShiftTeleport.clear();cn.academy.port.skill.Flashing.clear();ElectronBomb.clear();cn.academy.port.skill.ScatterBomb.clear();cn.academy.port.skill.LightShield.clear();cn.academy.port.skill.Meltdowner.clear();cn.academy.port.skill.MineRayBasic.clear();cn.academy.port.skill.MineRayExpert.clear();cn.academy.port.skill.MineRayLuck.clear();cn.academy.port.skill.VecReflection.clear();cn.academy.port.skill.PlasmaCannon.clear();cn.academy.port.skill.VecAccel.clear();cn.academy.port.skill.VecDeviation.clear();cn.academy.port.skill.DirectedBlastwave.clear();cn.academy.port.skill.BloodRetrograde.clear();cn.academy.port.skill.StormWing.clear();cn.academy.port.skill.RayBarrage.clear();cn.academy.port.skill.JetEngine.clear();cn.academy.port.skill.ElectronMissile.clear();AbilityStorage.clear();CHARGES.clear();}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        cn.academy.port.command.ClassicAbilityCommands.register(event.getDispatcher());
        event.getDispatcher().register(Commands.literal("academy").then(Commands.literal("status").executes(ctx->{var p=ctx.getSource().getPlayerOrException();var s=AbilityStorage.get(p);ctx.getSource().sendSuccess(()->Component.literal(s.category+" level "+s.level+" CP "+Math.round(s.cp)+"/"+Math.round(s.maxCp())+" overload "+Math.round(s.overload)+" progress "+s.levelProgress(SkillCatalog.levelSkillCount(s))),false);return 1;}))
            .then(Commands.literal("dev").requires(src->src.hasPermission(2)).then(Commands.literal("charge").then(Commands.argument("energy",DoubleArgumentType.doubleArg(0,10000)).executes(ctx->{var player=ctx.getSource().getPlayerOrException();if(!DevelopmentController.holdingPortable(player))return 0;new DeveloperItemEnergy(player.getMainHandItem(),DeveloperType.PORTABLE).energy(DoubleArgumentType.getDouble(ctx,"energy"));return 1;}))).then(Commands.argument("category",StringArgumentType.word()).executes(ctx->{var p=ctx.getSource().getPlayerOrException();var cat=StringArgumentType.getString(ctx,"category");if(!cn.academy.port.develop.DevelopmentActions.CATEGORIES.contains(cat))return 0;var s=AbilityStorage.get(p);cn.academy.port.highpower.ZeroGapArmor.remove(p);cn.academy.port.skill.PenetrateTeleport.remove(p);cn.academy.port.skill.MarkTeleport.remove(p);cn.academy.port.skill.FleshRipping.remove(p);cn.academy.port.skill.LocationTeleport.remove(p);cn.academy.port.skill.ShiftTeleport.remove(p);cn.academy.port.skill.Flashing.remove(p);cn.academy.port.skill.VecReflection.remove(p);cn.academy.port.skill.PlasmaCannon.remove(p);cn.academy.port.skill.VecAccel.remove(p);cn.academy.port.skill.VecDeviation.remove(p);cn.academy.port.skill.DirectedBlastwave.remove(p);cn.academy.port.skill.BloodRetrograde.remove(p);cn.academy.port.skill.StormWing.remove(p);cn.academy.port.skill.RayBarrage.remove(p);cn.academy.port.skill.JetEngine.remove(p);cn.academy.port.skill.ElectronMissile.remove(p);cn.academy.port.skill.Meltdowner.remove(p);cn.academy.port.skill.MineRayBasic.remove(p);cn.academy.port.skill.MineRayExpert.remove(p);cn.academy.port.skill.MineRayLuck.remove(p);cn.academy.port.skill.ScatterBomb.remove(p);cn.academy.port.skill.LightShield.remove(p);abortHolds(p);DevelopmentController.remove(p);ElectronBomb.abort(p);s.selectCategory(cat);s.setLevel(5);s.setActivateState(true);for(var skill:(cn.academy.port.canon.CanonSkills.activeCategory(cat)?cn.academy.port.canon.CanonSkills.ALL:SkillCatalog.ALL))if(skill.category().equals(cat))s.experience.put(skill.id(),1.0);s.recoverAll();AcademyNetwork.sync(p);AbilityStorage.save(p);return 1;}))));
    }
}
