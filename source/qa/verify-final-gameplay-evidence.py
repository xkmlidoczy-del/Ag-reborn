#!/usr/bin/env python3
"""Read-only final-suite evidence audit. Does not launch/build, or approve pixels from counters.

Explicit plans/results are required; historical logs are never auto-discovered into a final run.
Exit 0 means the specified evidence contracts passed, not user acceptance or release approval.
"""
import argparse
import hashlib
import json
import re
import runpy
from pathlib import Path

BASE = Path(__file__).resolve().parent
ROOT = BASE.parents[1]
MECHANICAL = re.compile(r"\b(?:QA_GAMEPLAY(?:_[A-Z_]+)?_PASS|QA_VM_EFFICIENCY_AB_PASS|QA_GROUND_(?:TRAVEL|STOP|ADMISSION)_PASS|QA_LIVE_MOVEMENT_PASS|QA_LIVE_NEW_WINDOW_(?:(?:DEATH|LIFECYCLE)_PASS|PASS)|QA_LIVE_STORM_(?:SUSTAIN|SOFT|STOP)_PASS)\b")
FAILURE = re.compile(r"QA_[A-Z_]*(?:FAILED|FAILURE)\b|QA_UNKNOWN_STEP\b|Exception caught during firing event:|Failed to initialize the mod loading system")
EXTRA_GROUP_COUNTS = {"tpvm": 7, "vmenv": 3, "itemSplit": 2, "stormcycle": 1, "visual": 4, "supplemental": 2}
EXTRA_SCRIPTS = {
    "tpvm": {"gameplay-tp-play-group-" + suffix + ".script" for suffix in ("cancel-register", "ordinary-stock", "target-air-movement", "two-needles")}
            | {"gameplay-native-arrow-" + skill + ".script" for skill in ("vector_parry", "autonomous_vector_circle", "phase_corridor")},
    "vmenv": {"gameplay-vm-environment-" + suffix + ".script" for suffix in ("legacy-three", "storm-fragile", "plasma-terrain")},
    "itemSplit": {"gameplay-vm-extra-" + suffix + ".script" for suffix in ("yaw-q-items", "split-narrow")},
    "stormcycle": {"gameplay-storm-cycle-costs.script"},
    "visual": {"gameplay-visual-" + suffix + ".script" for suffix in ("iron-nearplane", "railgun-charge", "thunderclap-peak", "mine-ray-tiers")},
    "supplemental": {"codex-storm-sustain.script", "codex-shield-hud-final.script"},
}
SUPPLEMENTAL_DUAL_KINDS = {"pincer", "tactical", "domain", "poses_forward", "poses_reverse"}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def zero_exit(value):
    return type(value) is int and value == 0  # JSON false/null are missing exit evidence, never success.


def fields(line):
    return dict(re.findall(r"\b([A-Za-z][A-Za-z0-9_]*)=([^\s]+)", line))


def evidence_lines(text, marker):
    return [{"line": n, "message": line.strip()} for n, line in enumerate(text.splitlines(), 1) if marker in line]


def vector_field(line, key):
    found = re.search(r"\b" + re.escape(key) + r"=\(([^)]+)\)", line)
    try:
        value = tuple(float(part.strip()) for part in found.group(1).split(",")) if found else ()
        return value if len(value) == 3 and all(abs(v) < float("inf") for v in value) else None
    except ValueError:
        return None


class Audit:
    def __init__(self, args):
        self.args = args
        self.errors = []
        self.cases = []
        self.snapshots = []
        self.images = {}
        self.observed_visual = set()
        self.observed_mechanical = set()

    def require(self, condition, message):
        if not condition:
            self.errors.append(message)
        return bool(condition)

    def read(self, path):
        if not self.require(path.is_file(), "missing file: " + str(path)):
            return ""
        return path.read_text(encoding="utf-8", errors="replace")

    def json(self, path, fallback):
        text = self.read(path)
        if not text:
            return fallback
        try:
            return json.loads(text)
        except ValueError as exc:
            self.errors.append("invalid JSON " + str(path) + ": " + str(exc))
            return fallback

    def snapshot(self, path, name):
        value = self.json(path, {})
        files, mods = value.get("files", {}), value.get("mods", {})
        valid = self.require(isinstance(files, dict) and bool(files), name + ": missing per-file compiled snapshot")
        if isinstance(files, dict):
            for prefix in ("classes/java/main/", "resources/main/"):
                valid &= self.require(any(k.startswith(prefix) for k in files), name + ": snapshot missing " + prefix)
            # ModDev currently compiles showcase sources into main, and its actual launch classpath
            # contains main only. Also support the explicitly separate showcaseQa source-set layout.
            qa_prefixes = ("classes/java/showcaseQa/", "classes/java/main/cn/academy/port/qa/")
            valid &= self.require(any(k.startswith(qa_prefixes) for k in files), name + ": snapshot missing actual QA class inventory")
        valid &= self.require(isinstance(mods, dict) and bool(mods), name + ": missing mod JAR SHA inventory")
        if valid:
            for part in (files, mods):
                valid &= self.require(all(isinstance(v, str) and re.fullmatch(r"[a-f0-9]{64}", v) for v in part.values()), name + ": malformed SHA inventory")
        if valid:
            self.snapshots.append((name, files, mods))

    def screenshots(self, manifest, name, dual=False):
        rows = self.json(manifest, [])
        self.require(isinstance(rows, list) and bool(rows), name + ": empty screenshot archive")
        verified = {}
        if not isinstance(rows, list):
            return verified
        for row in rows:
            relative, sha = row.get("file", ""), row.get("sha256", "")
            path = (manifest.parent / relative) if dual else (ROOT / relative)
            if not self.require(path.is_file(), name + ": missing archived PNG " + str(path)):
                continue
            if not self.require(digest(path) == sha, name + ": PNG SHA mismatch " + str(path)):
                continue
            header = path.read_bytes()[:24]
            if not self.require(len(header) == 24 and header[:8] == b"\x89PNG\r\n\x1a\n" and header[12:16] == b"IHDR" and int.from_bytes(header[16:20], "big") > 0 and int.from_bytes(header[20:24], "big") > 0,
                                name + ": invalid PNG header " + str(path)):
                continue
            image = {"file": str(path.resolve()), "sha256": sha, "scope": "native_saved_frame_pending_manual_pixel_review"}
            verified[path.name] = image
            self.images[str(path.resolve())] = image
        return verified

    def visual(self, text, skill, images, name):
        passed = evidence_lines(text, "QA_FINAL_VISUAL_PASS skill=" + skill + " ")
        samples = evidence_lines(text, "QA_FINAL_VISUAL_SAMPLE skill=" + skill + " ")
        self.require(bool(passed) and bool(samples), name + ": missing final actual visual observation for " + skill)
        valid = False
        for sample in samples:
            f = fields(sample["message"])
            try:
                okay = int(f.get("signatureFrames", 0)) > 0 and int(f.get("projectedQuads", 0)) > 0
                okay &= f.get("firstPerson") != "true" or int(f.get("handMatrixFrames", 0)) > 0
                okay &= int(f.get("level", 0)) < 4 or int(f.get("actualCameraFovFrames", 0)) > 0
                okay &= f.get("actionSaved") == "true" and f.get("releaseSaved") == "true"
            except ValueError:
                okay = False
            valid |= okay
        self.require(valid, name + ": invalid real-stage/hand/FOV/image observations for " + skill)
        for phase in ("action", "release"):
            captures = evidence_lines(text, "QA_FINAL_VISUAL_SCREENSHOT skill=" + skill + " phase=" + phase + " ")
            saved = []
            for row in captures:
                f = fields(row["message"])
                try:
                    representative = float(f.get("sampledAge", "nan")) >= 4 if phase == "action" else float(f.get("sampledSinceRelease", "nan")) >= 2
                except ValueError:
                    representative = False
                if f.get("saved") == "true" and representative and f.get("sampledArchetype"):
                    saved.append(f.get("file"))
            self.require(any(file in images for file in saved), name + ": no verified archived native " + phase + " PNG for " + skill)
        if passed and valid:
            self.observed_visual.add(skill)
        return {"skill": skill, "passes": passed, "samples": samples, "pixelReview": "separate_required"}

    def mechanism(self, text, skill, name):
        if skill == "wind_pressure_lift":
            groups = [evidence_lines(text, "QA_LIVE_MOVEMENT_PASS from="),
                      [r for r in evidence_lines(text, "QA_LIVE_WIND_PREFLIGHT") if fields(r["message"]).get("nativeReady") == "true"],
                      evidence_lines(text, "QA_LIVE_CAMERA_NEUTRAL_PASS skill=wind_pressure_lift")]
            okay = all(groups)
            rows = [row for group in groups for row in group]
        elif skill == "magnetic_ground_step":
            groups = [evidence_lines(text, "QA_LEARNING_PASS id=magnetic_ground_step "),
                      evidence_lines(text, "QA_GROUND_ADMISSION_PASS "),
                      evidence_lines(text, "QA_GROUND_TRAVEL_PASS "), evidence_lines(text, "QA_GROUND_STOP_PASS ")]
            okay = all(groups) and len(groups[2]) >= 2 and len(groups[3]) >= 2
            rows = [row for group in groups for row in group]
        else:
            rows = [row for row in evidence_lines(text, "QA_GAMEPLAY_PASS skill=" + skill + " ")]
            # Some legacy toggles emit an extra native melee receipt, but still need the general effect PASS.
            okay = bool(rows)
        self.require(okay, name + ": missing actual mechanical witness for " + skill)
        if okay:
            self.observed_mechanical.add(skill)
        return {"skill": skill, "witnesses": rows}

    def markers(self, text, markers, name):
        """Every specified marker is required. An unrelated active/phase/render count is insufficient."""
        valid = isinstance(markers, list) and bool(markers) and all(isinstance(m, str) and m for m in markers)
        self.require(valid, name + ": explicit nonempty requiredLogMarkers list required")
        rows = []
        for marker in markers if valid else []:
            found = evidence_lines(text, marker)
            self.require(bool(found), name + ": missing specific witness " + marker)
            rows.extend(found)
        return rows

    def extras_contract(self, plan):
        groups = {group: sum(row.get("suiteGroup") == group for row in plan) for group in EXTRA_GROUP_COUNTS}
        self.require(len(plan) == 19 and groups == EXTRA_GROUP_COUNTS, "extras must preserve exact 19 cases: TPVM7, VMenv3, itemSplit2, stormcycle1, visual4, supplemental2")
        self.require(all(row.get("suiteGroup") in EXTRA_GROUP_COUNTS for row in plan), "extras contains unknown suiteGroup")
        self.require(len({row.get("script") for row in plan}) == len(plan), "extras must preserve distinct frozen source scripts")
        for group, expected in EXTRA_SCRIPTS.items():
            self.require({row.get("script") for row in plan if row.get("suiteGroup") == group} == expected,
                         "extras " + group + ": exact required original scripts do not match")

    def extra(self, case, text, captured, images):
        name = case["name"]
        result = {"witnesses": self.markers(text, case.get("requiredLogMarkers"), name), "sources": []}
        markers = case.get("requiredLogMarkers", [])
        self.require(any("_PASS" in m and not any(x in m for x in ("VISUAL", "CAMERA", "POSE")) for m in markers),
                     name + ": extras require a specific mechanical/lifecycle witness, not only rendering or DONE")
        for marker, count in case.get("requiredLogMarkerCounts", {}).items():
            self.require(isinstance(count, int) and count > 0 and len(evidence_lines(text, marker)) == count,
                         name + ": wrong exact witness count for " + marker)
        if "requiredRecoveredRemaining" in case:
            rows = evidence_lines(text, "QA_TP_PLAY_TARGETED_RECOVERY_PASS ")
            remaining = [fields(row["message"]).get("remaining") for row in rows]
            self.require(remaining == [str(value) for value in case["requiredRecoveredRemaining"]],
                         name + ": two actual targeted recoveries must preserve remaining sequence [1, 0]")
        sources = case.get("sourceScripts", [])
        if sources == [case.get("script")]:
            sources = []  # Independent source already matches captured exactly; it is not a merged script.
        style = case.get("sourceBoundaryStyle", "stem")
        self.require(style in ("stem", "filename"), name + ": unsupported source boundary style")
        per_source = case.get("sourceRequiredLogMarkers", {})
        for source in sources:
            marker = Path(source).name if style == "filename" else Path(source).stem
            begin, end = "QA_SCRIPT_MARK begin:" + marker, "QA_SCRIPT_MARK end:" + marker
            # A trailing space/newline boundary prevents a stem accidentally matching a filename suffix.
            begins = list(re.finditer(re.escape(begin) + r"(?=\s|$)", text))
            ends = list(re.finditer(re.escape(end) + r"(?=\s|$)", text))
            okay = len(begins) == len(ends) == 1 and begins[0].start() < ends[0].start()
            self.require(okay, name + ": merged extra source boundary absent/duplicate/out of order: " + source)
            chunk = text[begins[0].start():ends[0].start()] if okay else ""
            original = self.read(self.args.script_dir / source).strip().split("|")
            self.require(original[-1:] == ["quit"] and original.count("quit") == 1, name + ": source quit structure " + source)
            required = "|".join(["mark:begin:" + marker] + original[:-1] + ["mark:end:" + marker])
            self.require(required in captured, name + ": merged extra lost/changed source inputs or assertions: " + source)
            source_rows = self.markers(chunk, per_source.get(source), name + ":" + source)
            result["sources"].append({"source": source, "witnesses": source_rows})
        if case.get("suiteGroup") == "visual":
            result["visual"] = [self.visual(text, skill, images, name) for skill in case.get("skills", [])]
            self.require(bool(case.get("skills")), name + ": visual extra skill identities absent")
        return result

    def plan(self, plan_path, results_path, kind):
        plan, results = self.json(plan_path, []), self.json(results_path, [])
        self.require(isinstance(plan, list) and bool(plan), str(plan_path) + ": nonempty case list required")
        self.require(isinstance(results, list), str(results_path) + ": result list required")
        if not isinstance(plan, list) or not isinstance(results, list):
            return []
        names = [r.get("name") for r in results]
        self.require(len(names) == len(set(names)), str(results_path) + ": duplicate results")
        wanted = [r.get("name") for r in plan]
        self.require(len(wanted) == len(set(wanted)), str(plan_path) + ": duplicate case names")
        self.require(set(names) == set(wanted), str(results_path) + ": results do not exactly match specified plan")
        byname = {row.get("name"): row for row in results}
        for case in plan:
            name = case["name"]
            before = len(self.errors)
            prefix = self.args.evidence_dir / name
            text = self.read(prefix.with_suffix(".log"))
            result = byname.get(name, {})
            self.require(zero_exit(result.get("exit")), name + ": missing successful runner result")
            self.require(result.get("script") == case.get("script"), name + ": runner result script mismatch")
            self.require(self.read(prefix.with_suffix(".exit")).strip() == "0", name + ": actual client exit is not zero")
            self.require("QA_DONE" in text, name + ": QA_DONE absent")
            self.require(not FAILURE.search(text), name + ": QA failure/exception present")
            captured = self.read(Path(str(prefix) + "-script.txt")).strip()
            expected = self.read(self.args.script_dir / case["script"]).strip()
            self.require(bool(expected) and captured == expected, name + ": captured script differs from specified frozen script")
            if "scriptSha256" in case:
                path = self.args.script_dir / case["script"]
                self.require(path.is_file() and digest(path) == case["scriptSha256"], name + ": frozen script SHA differs from plan")
            for source, sha in case.get("sourceSha256", {}).items():
                path = self.args.script_dir / source
                self.require(path.is_file() and digest(path) == sha, name + ": frozen component source SHA differs: " + source)
            self.snapshot(Path(str(prefix) + "-compiled-snapshot.json"), name)
            images = self.screenshots(Path(str(prefix) + "-screenshots.json"), name)
            report = {"name": name, "kind": kind, "log": str(prefix.with_suffix(".log")), "mechanisms": [], "visual": []}
            if kind == "main":
                self.require("academy.qa.finalVisual=true" in self.read(Path(str(prefix) + "-properties.txt")), name + ": final visual property absent")
                for skill in case.get("skills", []):
                    report["mechanisms"].append(self.mechanism(text, skill, name))
                    report["visual"].append(self.visual(text, skill, images, name))
            elif kind == "passives":
                for skill in case.get("records", []):
                    identity = "category=" + case["category"] + " skill=" + skill + " "
                    learned = evidence_lines(text, "QA_PASSIVE_LEARNING_PASS " + identity)
                    effect = evidence_lines(text, "QA_PASSIVE_EFFECT_PASS " + identity)
                    self.require(bool(learned) and bool(effect), name + ": missing actual machine learning/effect " + identity)
                    report["mechanisms"].append({"category": case["category"], "skill": skill, "learning": learned, "effect": effect})
            elif kind == "extras":
                if case.get("suiteGroup") == "visual":
                    self.require("academy.qa.finalVisual=true" in self.read(Path(str(prefix) + "-properties.txt")), name + ": final visual extra property absent")
                report["extras"] = self.extra(case, text, captured, images)
            else:
                report["modes"] = []
                for source in case.get("sourceScripts", []):
                    marker = Path(source).stem
                    begin, end = "QA_SCRIPT_MARK begin:" + marker, "QA_SCRIPT_MARK end:" + marker
                    self.require(text.count(begin) == 1 and text.count(end) == 1, name + ": mode boundary missing/duplicate " + source)
                    start, stop = text.find(begin), text.find(end)
                    self.require(0 <= start < stop, name + ": mode boundary out of order " + source)
                    chunk = text[start:stop] if 0 <= start < stop else ""
                    witnesses = [line.strip() for line in chunk.splitlines() if MECHANICAL.search(line)]
                    self.require(bool(witnesses), name + ": no real mechanical/lifecycle witness inside " + source)
                    # Prove every current source input/assertion is in the captured group, rather than count markers only.
                    original = self.read(self.args.script_dir / source).strip().split("|")
                    self.require(original[-1:] == ["quit"] and original.count("quit") == 1, name + ": source quit structure " + source)
                    required = "|".join(["mark:begin:" + marker] + original[:-1] + ["mark:end:" + marker])
                    self.require(required in captured, name + ": merged script lost/changed source steps " + source)
                    report["modes"].append({"source": source, "mechanicalWitnesses": witnesses})
            report["errors"] = self.errors[before:]
            report["verified"] = not report["errors"]
            self.cases.append(report)
        return plan

    def supplemental_dual(self, manifest_path, base_directory):
        manifest = self.json(manifest_path, [])
        valid = isinstance(manifest, list) and len(manifest) == 5 and all(isinstance(row, dict) and isinstance(row.get("directory"), str) and bool(row["directory"]) for row in manifest)
        valid = valid and {row.get("kind") for row in manifest} == SUPPLEMENTAL_DUAL_KINDS
        self.require(valid, "supplemental dual manifest must specify exactly three consent kinds and both pose directions")
        if not valid:
            return []
        directories = [(base_directory / row.get("directory", "")).resolve() for row in manifest]
        self.require(len(set(directories)) == 5, "supplemental dual runs must use five distinct output directories")
        for row, directory in zip(manifest, directories):
            self.dual_supplement_case(row["kind"], directory)
        return manifest

    def dual_supplement_case(self, kind, directory):
        name = "supplemental-dual-" + kind
        before = len(self.errors)
        result = self.json(directory / "result.json", {})
        self.require(result.get("kind") == kind and zero_exit(result.get("hostExit")) and zero_exit(result.get("observerExit")),
                     name + ": actual two participant exits/kind not successful")
        host, observer = self.read(directory / "host.log"), self.read(directory / "observer.log")
        self.require("QA_DONE" in host and "QA_DUAL_DONE" in observer, name + ": host or observer completion absent")
        self.require(not FAILURE.search(host + "\n" + observer), name + ": participant failure/unknown route/exception")
        states = set(self.read(directory / "observer-states.txt").splitlines())
        self.require("disconnect-clean" in states, name + ": actual observer disconnect cleanup absent")
        for role in ("host", "observer"):
            if kind.startswith("poses_"):
                script_name = "live-dual-tech.script" if kind == "poses_forward" else "live-dual-reverse-" + role + ".script"
                expected = "quit" if kind == "poses_forward" and role == "observer" else self.read(self.args.script_dir / script_name).strip()
            else:
                expected = self.read(self.args.script_dir / ("consent-" + kind + "-" + role + ".script")).strip()
            self.require(bool(expected) and self.read(directory / (role + "-script.txt")).strip() == expected, name + ": archived " + role + " script differs from frozen source")
        proof = self.pose_supplement(kind, result, host, observer, states, directory, name) if kind.startswith("poses_") else self.consent_supplement(kind, result, host, observer, name)
        self.snapshot(directory / "compiled-snapshot.json", name)
        self.screenshots(directory / "screenshots.json", name, dual=True)
        self.cases.append({"name": name, "kind": "supplemental-dual", "directory": str(directory), "proof": proof,
                           "errors": self.errors[before:], "verified": len(self.errors) == before})

    def consent_supplement(self, kind, result, host, observer, name):
        self.require(result.get("actualServerProof") is True and result.get("actualRecipientCommand") is True,
                     name + ": successful actual server/recipient command proof absent")
        required = ["QA_DUAL_CONSENT stage=PASS kind=" + kind + " ", "QA_DUAL_CONSENT stage=DONE " + kind]
        if kind in ("pincer", "tactical"):
            self.require(result.get("actualRecipientUi") is True, name + ": actual invitation recipient UI not verified")
            self.require(bool(evidence_lines(observer, "QA_DUAL_CONSENT stage=review-requested receivedNonce=")), name + ": actual recipient review command/nonce absent")
            receipts = evidence_lines(host, "QA_DUAL_CONSENT stage=receipt ")
            baseline = evidence_lines(host, "QA_DUAL_CONSENT stage=baseline ")
            peers = {fields(row["message"]).get("peer") for row in baseline}
            accepted = []
            for row in receipts:
                msg = row["message"]
                token, nonce = re.search(r"\btoken=(\d+)\b", msg), re.search(r"\binput=(\d+)\b", msg)
                traveler = re.search(r"\btraveler=([a-f0-9-]{36})\b", msg)
                if "paid=true" in msg and "moved=true" in msg and token and nonce and int(token[1]) > 0 and int(nonce[1]) > 0 and traveler and traveler[1] in peers:
                    accepted.append(row)
            self.require(bool(accepted), name + ": positive-input paid production movement receipt for actual baseline peer absent")
            moved = False
            for row in evidence_lines(host, required[0]):
                old, new = vector_field(row["message"], "peerBefore"), vector_field(row["message"], "peerAfter")
                if old and new and sum((a-b)**2 for a, b in zip(old, new)) > 4:
                    for receipt in accepted:
                        destination = vector_field(receipt["message"], "destination")
                        moved |= bool(destination and sum((a-b)**2 for a, b in zip(new, destination)) < .16)
            self.require(moved, name + ": peer actual displacement/destination does not match paid receipt")
        else:
            required += ["QA_DUAL_CONSENT stage=OFF_BEFORE ", "QA_DUAL_CONSENT stage=OFF_RELEASED ",
                         "QA_DUAL_CONSENT stage=OFF_EXPIRED ", "QA_DUAL_CONSENT stage=OFF_PASS "]
        rows = self.markers(host, required, name)
        passes = evidence_lines(host, required[0])
        paid = False
        for row in passes:
            f = fields(row["message"])
            try:
                paid |= int(f.get("closedSpendClockAfter", 0)) > int(f.get("closedSpendClockBefore", 0))
            except ValueError:
                pass
        self.require(paid, name + ": closed successful payer spend clock did not advance (CP refill is not a substitute)")
        if kind == "domain":
            self.domain_off(host, name)
        return {"kind": kind, "witnesses": rows, "scope": "frozen real recipient input plus server receipt/resource assertion; images still need explicit review"}

    def domain_off(self, text, name):
        stages = [evidence_lines(text, "QA_DUAL_CONSENT stage=" + stage + " ")
                  for stage in ("OFF_BEFORE", "OFF_RELEASED", "OFF_EXPIRED", "OFF_PASS")]
        complete = all(len(rows) == 1 for rows in stages)
        self.require(complete, name + ": exact single native own-off chronology required")
        if not complete:
            return
        self.require([rows[0]["line"] for rows in stages] == sorted(rows[0]["line"] for rows in stages), name + ": own-off chronology out of order")
        before, released, expired, passed = [fields(rows[0]["message"]) for rows in stages]
        peers = {fields(row["message"]).get("peer") for row in evidence_lines(text, "QA_DUAL_CONSENT stage=baseline ")}
        self.require(before.get("recipient") in peers and before.get("recipient") == passed.get("recipient") and bool(before.get("recipient")), name + ": own-off recipient differs from actual consenting peer")
        self.require(bool(before.get("pulseIdentity")) and before.get("pulseIdentity") == passed.get("ownedPulse") and before.get("tick") == passed.get("commandTick"), name + ": own-off pulse identity/command tick changed")
        try:
            duration, released_elapsed, expired_elapsed = int(before["duration"]), int(released["elapsed"]), int(expired["elapsed"])
            okay = 0 < duration <= 2 and 0 <= released_elapsed <= 1 and released_elapsed <= expired_elapsed <= 3
            okay &= int(released["tick"]) - int(before["tick"]) == released_elapsed
        except (ValueError, KeyError):
            okay = False
        self.require(okay, name + ": own-off must release lease within one boundary and naturally expire within two native pulse ticks plus one observation boundary")
        old, new = vector_field(stages[0][0]["message"], "pos"), vector_field(stages[2][0]["message"], "pos")
        initial_v, final_v = vector_field(stages[0][0]["message"], "velocity"), vector_field(stages[2][0]["message"], "velocity")
        self.require(bool(old and new and initial_v and final_v and initial_v[1] < 0 and final_v[1] < 0 and new[1] < old[1]), name + ": native pulse expiry was not witnessed during actual descending travel")

    def pose_supplement(self, kind, result, host, observer, states, directory, name):
        self.require(result.get("actualRemoteStatesVerified") is True and result.get("disconnectClean") is True, name + ": actual remote states/cleanup result absent")
        if kind == "poses_forward":
            expected = set("DISCHARGE,ACCELERATOR,LANCE,RAIL_DEPLOY,FIELD_TUNE,CANOPY,PHASE_ROUTE,PHASE_GATE,VECTOR_CAGE,VECTOR_DOMAIN,FLIGHT,BRAKE,LISTEN,construct-0,construct-2,construct-3,construct-4,construct-left,construct-steer".split(","))
            expected |= {"signature-" + category for category in ("electromaster", "meltdowner", "teleporter", "vecmanip")}
            expected |= {"signature-skill-" + skill for skill in ("iron_sand_lance", "rail_thunder_array", "magnetic_domain", "multicore_canopy", "multidimensional_landing_array", "phase_corridor", "vector_cage", "zenith_vector_dominion", "storm_wing", "reverse_jet_brake", "circulation_listening", "thunder_clap", "plasma_cannon")}
            expected.add("construct-channel-vector_construct")
            self.require("QA_DUAL_HOST_PASS" in host, name + ": host did not verify forward remote states")
            remote = observer
        else:
            expected = {"LANCE", "signature-electromaster", "signature-skill-iron_sand_lance"}
            states = set(self.read(directory / "host-seen.txt").splitlines())
            self.require("QA_DUAL_HOST_REVERSE_PASS" in host, name + ": host did not verify actual peer cast")
            remote = host
        self.require(expected <= states, name + ": remote state inventory missing " + ",".join(sorted(expected - states)))
        rendered = set()
        for row in evidence_lines(remote, "QA_DUAL_REMOTE_RENDER state="):
            f = fields(row["message"])
            try:
                if int(f.get("frames", 0)) > 0:
                    rendered.add(f.get("state"))
            except ValueError:
                pass
        self.require(expected <= rendered, name + ": per-state actual remote model render witness missing " + ",".join(sorted(expected - rendered)))
        return {"kind": kind, "expectedRemoteStates": sorted(expected), "actualRemoteRenderedStates": sorted(rendered)}

    def dual(self, directory):
        name = "dual-grounding"
        before = len(self.errors)
        result = self.json(directory / "result.json", {})
        self.require(result.get("kind") == "grounding" and zero_exit(result.get("hostExit")) and zero_exit(result.get("observerExit")) and result.get("actualServerProof") is True,
                     "dual-grounding: missing successful actual two-client result")
        host = self.read(directory / "host.log")
        observer = self.read(directory / "observer.log")
        required = ("QA_DUAL_GROUNDING_PASS round=late ", "QA_DUAL_GROUNDING_PASS round=precise ", "QA_DUAL_GROUNDING_DONE", "QA_DONE")
        for marker in required:
            self.require(marker in host, name + ": missing " + marker)
        self.require("QA_DUAL_DONE" in observer, name + ": observer completion absent")
        self.require("disconnect-clean" in self.read(directory / "observer-states.txt"), name + ": observer disconnect cleanup absent")
        self.require(not FAILURE.search(host + "\n" + observer), name + ": participant QA failure/exception")
        for role in ("host", "observer"):
            self.require(self.read(directory / (role + "-script.txt")).strip() == self.read(self.args.script_dir / ("consent-grounding-" + role + ".script")).strip(), name + ": archived " + role + " script differs")
        self.snapshot(directory / "compiled-snapshot.json", name)
        images = self.screenshots(directory / "screenshots.json", name, dual=True)
        visual = self.visual(host, "grounding_return", images, name)
        okay = all(marker in host for marker in required) and zero_exit(result.get("hostExit")) and zero_exit(result.get("observerExit"))
        if okay:
            self.observed_mechanical.add("grounding_return")
        self.cases.append({"name": name, "kind": "dual", "mechanicalWitnesses": [r for marker in required[:2] for r in evidence_lines(host, marker)], "visual": visual, "errors": self.errors[before:], "verified": len(self.errors) == before})

    def same_snapshot(self):
        if not self.snapshots:
            self.errors.append("no complete production/QA/resource/mod snapshots")
            return None
        first_name, first_files, first_mods = self.snapshots[0]
        mismatches = []
        for name, files, mods in self.snapshots[1:]:
            changed = [key for key in sorted(set(first_files) | set(files)) if first_files.get(key) != files.get(key)]
            mod_changed = [key for key in sorted(set(first_mods) | set(mods)) if first_mods.get(key) != mods.get(key)]
            if changed or mod_changed:
                mismatches.append({"case": name, "changedFiles": changed, "changedMods": mod_changed})
                self.errors.append(name + ": compiled files/mods differ from " + first_name)
        return {"referenceCase": first_name, "completeSnapshots": len(self.snapshots), "fileCount": len(first_files), "mods": first_mods,
                "canonicalFilesSha256": hashlib.sha256(json.dumps(first_files, sort_keys=True).encode()).hexdigest(), "mismatches": mismatches}

    def pixels(self, expected):
        if not self.args.pixel_review:
            return {"status": "pending", "note": "Native stage/matrix/FOV counters and valid PNGs are not pixel or aesthetic approval.", "reviewedSkills": [], "userFinalAestheticAcceptance": "not_assessed_by_this_script"}
        rows = self.json(self.args.pixel_review, [])
        self.require(isinstance(rows, list), "pixel review manifest must be an array of explicit visual image reviews")
        checked = set()
        for row in rows if isinstance(rows, list) else []:
            path = Path(row.get("file", "")).resolve()
            image = self.images.get(str(path))
            skill, phase = row.get("skill"), row.get("phase")
            valid = image is not None and row.get("sha256") == image["sha256"] and row.get("result") == "pass"
            valid &= bool(row.get("reviewer")) and row.get("reviewerKind") in ("agent", "user") and bool(row.get("notes")) and skill in expected and phase in ("action", "release")
            valid &= path.name.startswith("codex_final_" + str(skill) + "_" + str(phase) + "_")
            self.require(valid, "invalid/unmatched explicit visual review: " + str(path))
            if valid:
                checked.add((skill, phase))
        missing = [skill + ":" + phase for skill in expected for phase in ("action", "release") if (skill, phase) not in checked]
        return {"status": "complete" if not missing else "partial", "reviewedSkillPhases": len(checked), "missingSkillPhases": missing,
                "reviewerKinds": sorted({row.get("reviewerKind") for row in rows if row.get("reviewerKind") in ("agent", "user")}), "userFinalAestheticAcceptance": "not_assessed_by_this_script",
                "scope": "Explicit named agent/user reviewer assertions bound to verified archived image SHA; not inferred by this script."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for group in ("main", "modes", "passives"):
        parser.add_argument("--" + group + "-plan", required=True, type=Path)
        parser.add_argument("--" + group + "-results", required=True, type=Path)
    parser.add_argument("--extras-plan", type=Path, help="Optional exact 19-case extension plan; does not change original 33/7/7/51 contract")
    parser.add_argument("--extras-results", type=Path, help="Results for exactly the specified 19 extension cases")
    parser.add_argument("--supplemental-dual-manifest", type=Path, help="Optional five-run array: kind and explicit directory for pincer/tactical/domain/poses_forward/poses_reverse")
    parser.add_argument("--supplemental-dual-base-directory", "--base-directory", dest="supplemental_dual_base_directory", type=Path,
                        help="Resolve relative supplemental dual directories here; default is manifest parent")
    parser.add_argument("--evidence-dir", required=True, type=Path)
    parser.add_argument("--dual-grounding", required=True, type=Path)
    parser.add_argument("--script-dir", type=Path, default=BASE, help="Frozen script sources used by the supplied plans")
    parser.add_argument("--modes-source-plan", type=Path, default=BASE / "gameplay-final-modes-plan.json", help="Frozen complete 51-scenario source plan")
    parser.add_argument("--pixel-review", type=Path, help="Optional explicit visual-review array (named agent/user); never automatically generated")
    parser.add_argument("--require-pixel-review", action="store_true")
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    audit = Audit(args)
    catalog = runpy.run_path(str(BASE / "parse-cast-coverage.py"))["catalog"]()
    active = {row["id"] for row in catalog if row["controllable"]}
    passive = {(row["category"], row["id"]) for row in catalog if not row["controllable"]}
    main_plan = audit.plan(args.main_plan, args.main_results, "main")
    mode_plan = audit.plan(args.modes_plan, args.modes_results, "modes")
    passive_plan = audit.plan(args.passives_plan, args.passives_results, "passives")
    extras_plan, supplemental_duals = [], []
    audit.require(bool(args.extras_plan) == bool(args.extras_results), "--extras-plan and --extras-results must be supplied together")
    if args.extras_plan and args.extras_results:
        extras_plan = audit.plan(args.extras_plan, args.extras_results, "extras")
        audit.extras_contract(extras_plan)
    audit.require(not args.supplemental_dual_base_directory or args.supplemental_dual_manifest,
                  "supplemental dual base directory requires its explicit manifest")
    if args.supplemental_dual_manifest:
        supplemental_duals = audit.supplemental_dual(args.supplemental_dual_manifest,
                                                   args.supplemental_dual_base_directory or args.supplemental_dual_manifest.parent)
    audit.require(len(main_plan) == 33 and len(mode_plan) == 7 and len(passive_plan) == 7, "specified suite requires 33 main, 7 merged-mode and 7 passive client cases")
    main_ids = {skill for row in main_plan for skill in row.get("skills", [])}
    audit.require(len(active) == 96 and len(main_ids) == 95 and main_ids | {"grounding_return"} == active and "grounding_return" not in main_ids,
                  "main scope must be exact catalog 95 single-client plus the separate real dual grounding skill")
    passive_ids = {(row.get("category"), skill) for row in passive_plan for skill in row.get("records", [])}
    audit.require(len(passive) == 15 and passive_ids == passive, "passive/training plan must exactly cover 15 category/id records")
    mode_sources = [source for row in mode_plan for source in row.get("sourceScripts", [])]
    audit.require(len(mode_sources) == 51 and len(set(mode_sources)) == 51, "mode plan must preserve exactly 51 distinct source scenarios")
    source_modes = audit.json(args.modes_source_plan, [])
    audit.require(isinstance(source_modes, list) and set(mode_sources) == {row.get("script") for row in source_modes}, "merged modes do not match the specified complete 51-scenario source plan")
    audit.dual(args.dual_grounding)
    snapshot = audit.same_snapshot()
    audit.require(len(audit.snapshots) == len(main_plan) + len(mode_plan) + len(passive_plan) + len(extras_plan) + len(supplemental_duals) + 1,
                  "one complete per-file+mods snapshot required for every client case and dual run")
    audit.require(audit.observed_mechanical == active, "actual mechanical witnesses do not cover exact active 96")
    audit.require(audit.observed_visual == active, "actual final render/matrix/FOV observations do not cover exact active 96")
    pixels = audit.pixels(sorted(active))
    if args.require_pixel_review:
        audit.require(pixels["status"] == "complete", "complete explicit visual review required but absent/partial")
    status = "EVIDENCE_CONTRACT_FAILED" if audit.errors else ("MECHANICAL_AND_IMAGE_EVIDENCE_VERIFIED_PIXEL_REVIEW_PENDING" if pixels["status"] != "complete" else "SPECIFIED_EVIDENCE_AND_REVIEW_MANIFEST_VERIFIED")
    output = {"status": status, "notReleaseOrUserAcceptance": True, "activeCatalogCount": len(active), "singleClientDeclaredCount": len(main_ids),
              "mechanicallyObservedSkills": sorted(audit.observed_mechanical), "finalVisualObservedSkills": sorted(audit.observed_visual),
              "sameCompiledSnapshot": snapshot, "pixels": pixels, "verifiedArchivedImages": list(audit.images.values()), "cases": audit.cases, "errors": audit.errors,
              "optionalSuites": {"extrasRequested": bool(args.extras_plan), "extrasCaseCount": len(extras_plan),
                                 "supplementalDualRequested": bool(args.supplemental_dual_manifest), "supplementalDualCaseCount": len(supplemental_duals)},
              "limits": ["Mechanical checks prove their named conditions, not every unstated use/environment or probability distribution.",
                         "The script validates existing artifacts and never opens a game, injects a skill, or calls a renderer.",
                         "Full-frame images still require explicit image inspection of ability identity, hand/overlay alignment and high-tier presentation."]}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"status": status, "errors": len(audit.errors), "observedMechanisms": len(audit.observed_mechanical), "observedVisualSkills": len(audit.observed_visual), "pixelReview": pixels["status"], "output": str(args.out)}, ensure_ascii=False))
    raise SystemExit(1 if audit.errors else 0)


if __name__ == "__main__":
    main()
