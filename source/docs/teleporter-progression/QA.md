# Verification and native handoff

## Completed in this isolated stage

- Run scripts/verify.sh with cached official JDK21; writes only this stage. No Gradle/Minecraft/client/browser/world/save run.
- Five deterministic executable checks passed: TeleporterProgressionRegressionTest, TeleporterProgressionOracleTest, TeleporterProgressionLearningRegressionTest, TeleporterProgressionVisualRegressionTest, TeleporterProgressionServerLinkTest.
- 24 exact source files plus media hash resources are bundled; tests never read .reference. A clean restored Gradle testClasses/check obtains all fixture sources from src/test/resources.
- Actual production/adapters/integration snapshots plus9 native GameTests compile. audit/verification.txt records results. Deprecation warnings come from current AcademyClient and native teleport APIs; no compile errors.

## Promotion contract

1. Run scripts/check_integration.py against docs/promotion-manifest.json. Each shared file must match its frozen baseline or expected after hash; if the main branch changed, hunk-merge/rebase the narrow patch. NEVER copy a full integration snapshot over newer common code.
2. Copy only manifest additive src/main and src/test files to the same paths, then apply/merge docs/integration.patch. SkillAvailability and MachineDeveloperSessions already use the genuine common registry and need no separate patch. No original asset replacement, sounds.json truncation, or target-mastery admission gate.
3. Main owns aggregate offline Gradle and native/client worlds. Run the five registered teleporterProgression* checks and existing tests after merge. Keep existing unrelated Meltdowner integration intact.

## Compiled native fixtures, NOT yet executed

Class cn.academy.port.gametest.AcademyTeleporterProgressionRuntimeTests. Dynamic template academy:teleporter_progression_empty installs only in GameTest-enabled worlds. Batch academy_teleporter_progression(7 tests), academy_teleporter_progression_learning(2 tests).

1. penetrate_open_air_step_overshoot_force_cost_fall_reset_and_counter
2. penetrate_unavailable_still_executes_with_CP_floor_and_no_block_break
3. mark_zero_tick_below_three_aborts_and_tick_one_charge_succeeds
4. mark_real_block_face_head_adjustment_and_entity_eye_offset
5. flesh_native_bypass_armor_protected_training_and_cached_tick_target
6. flesh_wall_occludes_miss_is_free_and_low_CP_first_tick_terminates
7. nonce_slot_ingress_lifecycle_and_nonfinite_aim_guards
8. portable_use_and_common_GUI_ingress_genuinely_learn_both_level_two_targets
9. physical_Normal_GUI_genuinely_learns_flesh_with_two_original_prerequisites

The two Penetrate movement fixtures and the Mark release/charge fixture use actual PlayerList-admitted ServerPlayer, an EmbeddedChannel and official NetworkRegistry.configureMockConnection. Fixture setup independently invokes native teleport and asserts coordinate changes before either skill is called. Owned players are removed through PlayerList and channels are released on pass/fail/rerun. NeoForge21.1.252 ordinary FakePlayer overrides both listener teleport overloads with empty methods, so it is retained only for nonmovement/combat/learning fixtures. No gameplay fallback or fixture-specific movement override was added.

Combat fixtures deliberately seed learned state to isolate gameplay. Learning fixtures DO NOT pre-grant or edit any target mastery: Penetrate/Mark are earned through the real Portable item use and server learn ingress with10000IF finite battery, prerequisites ThreateningTeleport.5/.4,130 ticks and3900IF actual cost. Flesh is earned through physical eight-cell Normal placement/use/authenticated GUI session, both exact.5 prerequisites,50000IF finite battery,147ticks and5145IF cost. Portable tier and forged session/failed second prerequisite are rejected. Every earned target is preset-bound and activates the genuine slot route at zero mastery.

## Required actual client/singleplayer checks

- Natural DeveloperMenu learning, preset editor, left/right/R/F physical bindings, Vtoggle/Cswitch/Nedit, GUI/focus loss, duplicate physical keys and abort/repress. Learnable display must use the correct minimum tier and depend on original prerequisites. No debug mastery command as evidence of natural gameplay.
- Compare original TP biped frames0..6 and source geometry/material through1st and3rd-person cameras: ghost location, yaw, hat, mirrored limbs, unavailable red tint, through-wall depth, fullbright blending, lack of shadow, no false hand animation. Mark held range should grow2blocks/server tick, preserve instantaneous retargeting and exact block-face placement/head lowering.
- Check Penetrate source stage0 overshoot, wall exit, stage1 unavailable behavior and zero-CP force-floor. Ensure contemporary placement does not accidentally introduce a free-space safety teleport different from source.
- Check Flesh target corners correct height/width, red/grey values, line3, through-wall occlusion,5/6blood bursts, ten frames/death tick10, body anchoring in1st/3rd and remote observer contexts. Observe 5% caster nausea in repeat hits, native armor bypass, canceled/zero damage still source training, critical passive EXP and event cosmetics.
- Listen to original tp.tp.5 for Penetrate abort/release and successful Mark only; tp.guts.6 for Flesh hits, following-caster coordinates, no extra sounds on misses or stale worlds. Verify original complete sounds registry after resource reload.
- Pause/resume and resource reload without advancing particle/blood lifecycle while paused; logout/death/clone/dimension/server stop remove all held markers without later release, charge or damage. Old terminal packets must not revive markers after new world/connection identity.
- Successful player dismount/native teleport must retain source orientation/velocity behavior and reset fallDistance; original ac_tpcount persists in ordinary singleplayer save/restart. Achievement-page/toast integration remains outside this stage and is honestly listed in FIDELITY.md.

A fixture pass is evidence of tested native behavior, not pixel/audio fidelity or physical input. Report each never-run stage separately.
