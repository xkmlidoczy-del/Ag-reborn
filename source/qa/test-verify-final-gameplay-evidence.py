#!/usr/bin/env python3
"""Parser counterexamples only. Temporary synthetic files are not game acceptance evidence."""
import hashlib
import importlib.util
import json
import tempfile
import types
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("final_evidence", Path(__file__).with_name("verify-final-gameplay-evidence.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EvidenceContracts(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="aca-evidence-contract-")
        self.root = Path(self.tmp.name)
        self.audit = module.Audit(types.SimpleNamespace(pixel_review=None))

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, name, value):
        path = self.root / name
        path.write_text(json.dumps(value))
        return path

    def snapshot(self, name, **extra):
        files = {prefix + "entry": "a" * 64 for prefix in ("classes/java/main/", "classes/java/showcaseQa/", "resources/main/")}
        return self.write(name, {"files": files, "mods": {"sable.jar": "b" * 64}, **extra})

    def test_render_active_is_not_mechanism(self):
        self.audit.mechanism("QA_LIVE_POSE_PASS skill=railgun\nQA_FINAL_VISUAL_PASS skill=railgun ", "railgun", "case")
        self.assertTrue(self.audit.errors)
        self.assertNotIn("railgun", self.audit.observed_mechanical)

    def test_real_skill_witness_is_scoped_to_id(self):
        self.audit.mechanism("QA_GAMEPLAY_PASS skill=railgun witness=actual_world_damage ", "railgun", "case")
        self.assertFalse(self.audit.errors)
        self.audit.mechanism("QA_GAMEPLAY_PASS skill=railgun witness=actual_world_damage ", "charging", "other")
        self.assertTrue(self.audit.errors)

    def test_wind_native_ready_without_authoritative_movement_fails(self):
        self.audit.mechanism("QA_LIVE_WIND_PREFLIGHT nativeReady=true\nQA_LIVE_CAMERA_NEUTRAL_PASS skill=wind_pressure_lift", "wind_pressure_lift", "wind")
        self.assertTrue(self.audit.errors)

    def test_wind_requires_real_server_position_witness(self):
        self.audit.mechanism("QA_LIVE_WIND_PREFLIGHT nativeReady=true\nQA_LIVE_MOVEMENT_PASS from=(0,0,0) to=(0,3,0)\nQA_LIVE_CAMERA_NEUTRAL_PASS skill=wind_pressure_lift", "wind_pressure_lift", "wind")
        self.assertFalse(self.audit.errors)

    def test_ground_needs_two_surfaces_learning_and_clean_stops(self):
        text = "QA_LEARNING_PASS id=magnetic_ground_step viaRealConfirm=true\nQA_GROUND_ADMISSION_PASS enabled=true\nQA_GROUND_TRAVEL_PASS surface=grass\nQA_GROUND_STOP_PASS surface=grass\n"
        self.audit.mechanism(text, "magnetic_ground_step", "ground")
        self.assertTrue(self.audit.errors)
        audit = module.Audit(types.SimpleNamespace(pixel_review=None))
        audit.mechanism(text + "QA_GROUND_TRAVEL_PASS surface=stone\nQA_GROUND_STOP_PASS surface=stone\n", "magnetic_ground_step", "ground")
        self.assertFalse(audit.errors)

    def test_old_missing_mod_inventory_is_missing_evidence(self):
        self.audit.snapshot(self.snapshot("old.json", mods={}), "old")
        self.assertFalse(self.audit.snapshots)
        self.assertTrue(self.audit.errors)

    def test_old_missing_qa_fingerprints_is_missing_evidence(self):
        path = self.snapshot("old.json")
        data = json.loads(path.read_text())
        del data["files"]["classes/java/showcaseQa/entry"]
        path.write_text(json.dumps(data))
        self.audit.snapshot(path, "old")
        self.assertFalse(self.audit.snapshots)

    def test_moddev_actual_qa_under_main_is_inventory_not_missing_source_set(self):
        path = self.snapshot("main-layout.json")
        data = json.loads(path.read_text())
        del data["files"]["classes/java/showcaseQa/entry"]
        data["files"]["classes/java/main/cn/academy/port/qa/GameplayAcceptancePassives.class"] = "d" * 64
        path.write_text(json.dumps(data))
        self.audit.snapshot(path, "actual-main-layout")
        self.assertFalse(self.audit.errors)
        self.assertEqual(len(self.audit.snapshots), 1)

    def test_changed_mod_cannot_be_merged_as_same_build(self):
        self.audit.snapshot(self.snapshot("a.json"), "a")
        self.audit.snapshot(self.snapshot("b.json", mods={"sable.jar": "c" * 64}), "b")
        report = self.audit.same_snapshot()
        self.assertEqual(report["mismatches"][0]["changedMods"], ["sable.jar"])
        self.assertTrue(self.audit.errors)

    def test_png_manifest_hash_is_checked(self):
        path = self.root / "capture.png"
        path.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\0\0\0\rIHDR" + (800).to_bytes(4, "big") + (600).to_bytes(4, "big"))
        manifest = self.write("screenshots.json", [{"file": str(path), "sha256": "0" * 64}])
        self.assertFalse(self.audit.screenshots(manifest, "case"))
        self.assertTrue(self.audit.errors)

    def test_counters_and_saved_images_never_approve_pixels(self):
        self.audit.observed_visual.add("railgun")
        self.assertEqual(self.audit.pixels(["railgun"])["status"], "pending")

    def test_age_zero_screenshot_does_not_satisfy_representative_final_frame(self):
        text = "\n".join([
            "QA_FINAL_VISUAL_PASS skill=charging category=electromaster ",
            "QA_FINAL_VISUAL_SAMPLE skill=charging category=electromaster level=1 signatureFrames=10 projectedQuads=5 firstPerson=true handMatrixFrames=10 actualCameraFovFrames=0 actionSaved=true releaseSaved=true ",
            "QA_FINAL_VISUAL_SCREENSHOT skill=charging phase=action file=action.png saved=true sampledAge=0 sampledSinceRelease=-1 sampledArchetype=GATHER ",
            "QA_FINAL_VISUAL_SCREENSHOT skill=charging phase=release file=release.png saved=true sampledAge=12 sampledSinceRelease=2 sampledArchetype=GATHER ",
        ])
        self.audit.visual(text, "charging", {"action.png": {}, "release.png": {}}, "case")
        self.assertTrue(any("action PNG" in error for error in self.audit.errors))

    def test_manual_review_must_bind_the_archived_hash_and_named_skill(self):
        image = self.root / "codex_final_railgun_action_1.png"
        self.audit.images[str(image.resolve())] = {"file": str(image), "sha256": "a" * 64}
        row = {"skill": "railgun", "phase": "action", "file": str(image), "sha256": "b" * 64, "reviewer": "test", "reviewerKind": "agent", "result": "pass", "notes": "synthetic parser test only"}
        self.audit.args.pixel_review = self.write("review.json", [row])
        self.assertEqual(self.audit.pixels(["railgun"])["status"], "partial")
        self.assertTrue(self.audit.errors)

    def test_explicit_agent_image_review_is_separate_from_user_acceptance(self):
        rows = []
        for phase in ("action", "release"):
            image = self.root / ("codex_final_railgun_" + phase + "_1.png")
            self.audit.images[str(image.resolve())] = {"file": str(image), "sha256": "a" * 64}
            rows.append({"skill": "railgun", "phase": phase, "file": str(image), "sha256": "a" * 64, "reviewer": "test-agent", "reviewerKind": "agent", "result": "pass", "notes": "synthetic parser test only; no actual image inspection"})
        self.audit.args.pixel_review = self.write("review.json", rows)
        result = self.audit.pixels(["railgun"])
        self.assertEqual(result["status"], "complete")
        self.assertEqual(result["reviewerKinds"], ["agent"])
        self.assertEqual(result["userFinalAestheticAcceptance"], "not_assessed_by_this_script")
        self.assertFalse(self.audit.errors)

    def test_respawn_lifecycle_is_a_specific_mechanical_witness_not_visual_only(self):
        self.assertIsNotNone(module.MECHANICAL.search("QA_LIVE_NEW_WINDOW_LIFECYCLE_PASS phase=respawn replacedPlayer=true"))
        self.assertIsNone(module.MECHANICAL.search("QA_LIVE_CAMERA_NEUTRAL_PASS skill=impulse_split"))

    def test_unknown_route_cannot_hide_behind_done(self):
        self.assertIsNotNone(module.FAILURE.search("QA_UNKNOWN_STEP vmextra:arrow\nQA_DONE"))

    def test_extras_require_all_exact_nineteen_scripts_not_a_same_count_substitute(self):
        plan = [{"suiteGroup": group, "script": script} for group, scripts in module.EXTRA_SCRIPTS.items() for script in scripts]
        self.audit.extras_contract(plan)
        self.assertFalse(self.audit.errors)
        plan[0]["script"] = "unrelated-visual-probe.script"
        self.audit.extras_contract(plan)
        self.assertTrue(any("exact required original scripts" in error for error in self.audit.errors))

    def extra(self, case, text, captured="quit"):
        self.audit.args.script_dir = self.root
        return self.audit.extra({"name": "extra", "suiteGroup": "tpvm", **case}, text, captured, {})

    def test_extra_cannot_replace_actual_mechanism_with_done_and_render(self):
        self.extra({"requiredLogMarkers": ["QA_DONE", "QA_FINAL_VISUAL_PASS skill=impulse_split "]},
                   "QA_DONE\nQA_FINAL_VISUAL_PASS skill=impulse_split ")
        self.assertTrue(any("not only rendering" in error for error in self.audit.errors))

    def test_each_specific_extra_marker_required_not_one_of_many(self):
        self.extra({"requiredLogMarkers": ["QA_SPECIALIST_NATIVE_ARROW_COMMIT_PASS mode=vector_parry ", "QA_NATIVE_ARROW_MODE_PASS mode=vector_parry "]},
                   "QA_NATIVE_ARROW_MODE_PASS mode=vector_parry damage=1\nQA_DONE")
        self.assertTrue(any("COMMIT_PASS" in error for error in self.audit.errors))

    def test_two_needles_one_recovery_and_wrong_remaining_are_not_two(self):
        case = {"requiredLogMarkers": ["QA_TP_PLAY_TARGETED_RECOVERY_PASS "],
                "requiredLogMarkerCounts": {"QA_TP_PLAY_TARGETED_RECOVERY_PASS ": 2}, "requiredRecoveredRemaining": [1, 0]}
        self.extra(case, "QA_TP_PLAY_TARGETED_RECOVERY_PASS remaining=1 ")
        self.assertTrue(any("wrong exact witness count" in error for error in self.audit.errors))
        self.audit.errors.clear()
        self.extra(case, "QA_TP_PLAY_TARGETED_RECOVERY_PASS remaining=0 \nQA_TP_PLAY_TARGETED_RECOVERY_PASS remaining=1 ")
        self.assertTrue(any("remaining sequence" in error for error in self.audit.errors))
        self.audit.errors.clear()
        self.extra(case, "QA_TP_PLAY_TARGETED_RECOVERY_PASS remaining=1 \nQA_TP_PLAY_TARGETED_RECOVERY_PASS remaining=0 ")
        self.assertFalse(self.audit.errors)

    def merged_extra(self):
        (self.root / "first.script").write_text("keydown:82|wait:5|keyup:82|assert:first|quit")
        (self.root / "second.script").write_text("keydown:87|wait:5|keyup:87|assert:second|quit")
        case = {"script": "merged.script", "sourceScripts": ["first.script", "second.script"], "sourceBoundaryStyle": "filename",
                "requiredLogMarkers": ["QA_FIRST_PASS ", "QA_SECOND_PASS "],
                "sourceRequiredLogMarkers": {"first.script": ["QA_FIRST_PASS "], "second.script": ["QA_SECOND_PASS "]}}
        captured = "mark:begin:first.script|keydown:82|wait:5|keyup:82|assert:first|mark:end:first.script|mark:begin:second.script|keydown:87|wait:5|keyup:87|assert:second|mark:end:second.script|quit"
        text = "QA_SCRIPT_MARK begin:first.script\nQA_FIRST_PASS actual=true\nQA_SCRIPT_MARK end:first.script\nQA_SCRIPT_MARK begin:second.script\nQA_SECOND_PASS actual=true\nQA_SCRIPT_MARK end:second.script\nQA_DONE"
        return case, text, captured

    def test_filename_extra_boundaries_accept_complete_frozen_input(self):
        self.extra(*self.merged_extra())
        self.assertFalse(self.audit.errors)

    def test_merged_extra_lost_assertion_fails_even_with_success_markers(self):
        case, text, captured = self.merged_extra()
        self.extra(case, text, captured.replace("assert:second|", ""))
        self.assertTrue(any("lost/changed source" in error for error in self.audit.errors))

    def test_extra_success_from_another_chunk_cannot_fill_missing_source_witness(self):
        case, text, captured = self.merged_extra()
        text = text.replace("QA_FIRST_PASS actual=true\n", "").replace("QA_SECOND_PASS", "QA_FIRST_PASS actual=true\nQA_SECOND_PASS")
        self.extra(case, text, captured)
        self.assertTrue(any("extra:first.script: missing specific witness" in error for error in self.audit.errors))

    def test_extra_stem_is_not_filename_boundary(self):
        case, text, captured = self.merged_extra()
        self.extra(case, text.replace("first.script", "first"), captured)
        self.assertTrue(any("boundary absent" in error for error in self.audit.errors))

    def test_independent_extra_self_source_has_no_invented_merge_boundary(self):
        self.extra({"script": "self.script", "sourceScripts": ["self.script"], "requiredLogMarkers": ["QA_NATIVE_ARROW_MODE_PASS mode=phase_corridor "]},
                   "QA_NATIVE_ARROW_MODE_PASS mode=phase_corridor actual=true ")
        self.assertFalse(self.audit.errors)

    def consent(self, changed=""):
        peer = "11111111-1111-1111-1111-111111111111"
        host = "\n".join(["QA_DUAL_CONSENT stage=baseline peer=" + peer,
            "QA_DUAL_CONSENT stage=receipt Receipt[input=2, token=3, paid=true, moved=true, destination=(8, 0, 0), traveler=" + peer + "]",
            "QA_DUAL_CONSENT stage=PASS kind=pincer peerBefore=(0, 0, 0) peerAfter=(8, 0, 0) cpBefore=100 cpAfter=100 closedSpendClockBefore=10 closedSpendClockAfter=20",
            "QA_DUAL_CONSENT stage=DONE pincer"])
        result = {"actualServerProof": True, "actualRecipientCommand": True, "actualRecipientUi": True}
        return result, host, "QA_DUAL_CONSENT stage=review-requested receivedNonce=11111111-1111-1111-1111-111111111111"

    def test_consent_natural_cp_refill_does_not_hide_real_closed_spend(self):
        self.audit.consent_supplement("pincer", *self.consent(), "dual")
        self.assertFalse(self.audit.errors)

    def test_consent_flags_without_actual_peer_displacement_fail(self):
        result, host, observer = self.consent()
        self.audit.consent_supplement("pincer", result, host.replace("peerAfter=(8, 0, 0)", "peerAfter=(0, 0, 0)"), observer, "dual")
        self.assertTrue(any("actual displacement" in error for error in self.audit.errors))

    def test_consent_receipt_for_other_player_or_zero_input_fails(self):
        result, host, observer = self.consent()
        self.audit.consent_supplement("pincer", result, host.replace("input=2", "input=0"), observer, "dual")
        self.assertTrue(any("actual baseline peer absent" in error for error in self.audit.errors))
        self.audit.errors.clear()
        self.audit.consent_supplement("pincer", result, host.replace("traveler=111", "traveler=222"), observer, "dual")
        self.assertTrue(any("actual baseline peer absent" in error for error in self.audit.errors))

    def test_domain_pass_without_actual_own_off_pulse_expiry_fails(self):
        self.audit.consent_supplement("domain", {"actualServerProof": True, "actualRecipientCommand": True},
            "QA_DUAL_CONSENT stage=PASS kind=domain closedSpendClockBefore=0 closedSpendClockAfter=5\nQA_DUAL_CONSENT stage=DONE domain\nQA_DUAL_CONSENT stage=OFF_PASS recipient=peer", "", "domain")
        self.assertTrue(any("OFF_EXPIRED" in error for error in self.audit.errors))

    def test_reverse_pose_inventory_without_actual_model_render_frames_fails(self):
        states = {"LANCE", "signature-electromaster", "signature-skill-iron_sand_lance"}
        (self.root / "host-seen.txt").write_text("\n".join(states))
        self.audit.pose_supplement("poses_reverse", {"actualRemoteStatesVerified": True, "disconnectClean": True},
            "QA_DUAL_HOST_REVERSE_PASS\n" + "\n".join("QA_DUAL_REMOTE_RENDER state=" + state + " frames=0" for state in states), "", set(), self.root, "reverse")
        self.assertTrue(any("actual remote model render witness missing" in error for error in self.audit.errors))

    def test_supplemental_dual_cannot_replace_reverse_with_duplicate_forward(self):
        manifest = self.write("dual.json", [{"kind": kind, "directory": kind} for kind in ["pincer", "tactical", "domain", "poses_forward", "poses_forward"]])
        self.audit.supplemental_dual(manifest, self.root)
        self.assertTrue(any("both pose directions" in error for error in self.audit.errors))

    def test_changed_qa_class_cannot_be_hidden_inside_same_production_inventory(self):
        self.audit.snapshot(self.snapshot("a.json"), "a")
        path = self.snapshot("b.json")
        value = json.loads(path.read_text()); value["files"]["classes/java/showcaseQa/entry"] = "f" * 64; path.write_text(json.dumps(value))
        self.audit.snapshot(path, "b")
        report = self.audit.same_snapshot()
        self.assertEqual(report["mismatches"][0]["changedFiles"], ["classes/java/showcaseQa/entry"])

    def complete_dual_fixture(self):
        self.audit.args.script_dir = self.root
        result, host, observer = self.consent()
        result.update(kind="pincer", hostExit=0, observerExit=0)
        self.write("result.json", result)
        (self.root / "host.log").write_text(host + "\nQA_DONE")
        (self.root / "observer.log").write_text(observer + "\nQA_DUAL_DONE remoteFrames=5")
        (self.root / "observer-states.txt").write_text("disconnect-clean\n")
        for role in ("host", "observer"):
            text = "consent:real-" + role + "|quit"
            (self.root / (role + "-script.txt")).write_text(text)
            (self.root / ("consent-pincer-" + role + ".script")).write_text(text)
        self.snapshot("compiled-snapshot.json")
        png = self.root / "capture.png"
        png.write_bytes(b"\x89PNG\r\n\x1a\n" + b"\0\0\0\rIHDR" + (800).to_bytes(4, "big") + (600).to_bytes(4, "big"))
        self.write("screenshots.json", [{"file": "capture.png", "sha256": module.digest(png)}])

    def test_whole_dual_case_checks_actual_participant_exit_despite_done_and_proof(self):
        self.complete_dual_fixture()
        self.audit.dual_supplement_case("pincer", self.root)
        self.assertFalse(self.audit.errors)
        result = json.loads((self.root / "result.json").read_text()); result["observerExit"] = 1; self.write("result.json", result)
        self.audit.errors.clear()
        self.audit.dual_supplement_case("pincer", self.root)
        self.assertTrue(any("participant exits/kind" in error for error in self.audit.errors))

    def test_unknown_observer_step_fails_even_when_both_clients_report_done(self):
        self.complete_dual_fixture()
        path = self.root / "observer.log"; path.write_text(path.read_text() + "\nQA_UNKNOWN_STEP consent:typo")
        self.audit.dual_supplement_case("pincer", self.root)
        self.assertTrue(any("unknown route" in error for error in self.audit.errors))

    def test_json_false_null_and_float_are_not_actual_zero_exit_codes(self):
        for value in (False, True, None, "0", 0.0):
            self.assertFalse(module.zero_exit(value))
        self.assertTrue(module.zero_exit(0))

    def test_whole_dual_case_rejects_false_as_observer_exit(self):
        self.complete_dual_fixture()
        result = json.loads((self.root / "result.json").read_text()); result["observerExit"] = False; self.write("result.json", result)
        self.audit.dual_supplement_case("pincer", self.root)
        self.assertTrue(any("participant exits/kind" in error for error in self.audit.errors))

    def off_chronology(self):
        return "\n".join([
            "QA_DUAL_CONSENT stage=baseline peer=peer",
            "QA_DUAL_CONSENT stage=OFF_BEFORE recipient=peer tick=562 pos=(0, 10, 0) velocity=(0, -0.3, 0) pulseIdentity=692 duration=2",
            "QA_DUAL_CONSENT stage=OFF_RELEASED tick=563 elapsed=1 effectMayRemain=true",
            "QA_DUAL_CONSENT stage=OFF_EXPIRED elapsed=3 pos=(0, 9.5, 0) velocity=(0, -0.38, 0)",
            "QA_DUAL_CONSENT stage=OFF_PASS recipient=peer commandTick=562 ownedPulse=692"])

    def test_domain_v3_allows_existing_native_two_tick_pulse_to_expire_at_third_observation(self):
        self.audit.domain_off(self.off_chronology(), "domain")
        self.assertFalse(self.audit.errors)

    def test_domain_cannot_keep_refreshing_pulse_past_native_deadline(self):
        self.audit.domain_off(self.off_chronology().replace("OFF_EXPIRED elapsed=3", "OFF_EXPIRED elapsed=4"), "domain")
        self.assertTrue(any("naturally expire" in error for error in self.audit.errors))

    def test_domain_wrong_pulse_or_grounded_expiry_is_not_same_native_lease(self):
        self.audit.domain_off(self.off_chronology().replace("ownedPulse=692", "ownedPulse=693"), "domain")
        self.assertTrue(any("pulse identity" in error for error in self.audit.errors))
        self.audit.errors.clear()
        self.audit.domain_off(self.off_chronology().replace("velocity=(0, -0.38, 0)", "velocity=(0, 0, 0)"), "domain")
        self.assertTrue(any("actual descending travel" in error for error in self.audit.errors))


if __name__ == "__main__":
    unittest.main(verbosity=2)
