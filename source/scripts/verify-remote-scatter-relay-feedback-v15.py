#!/usr/bin/env python3
"""CPU-only .77 -> .78 source-scope companion; never replaces a historical guard.

This is not the v14 executed Charging differential and is not a Gradle check.
The immutable .77 Git tree is the independent source baseline. No refresh mode,
fixture rewrites, source normalization, compiler, JVM, server, or client launch.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import runpy
import subprocess
import sys

BASELINE = "d6c9eed2544f4743aefd994635cc189121cd5dcb"
OLD_VERSION = b"0.1.0-m41-highpower.0.77-prototype"
NEW_VERSION = b"0.1.0-m41-highpower.0.78-prototype"
JAVA = "src/main/java/cn/academy/port/"
REGISTRY = JAVA + "highpower/HighPowerSkills.java"
FIXTURE = "src/test/resources/current-geometry-proof/charging-core/"
V14 = "scripts/verify-charging-m41-core-v14.py"
VERSION_ONLY = {
    "build.gradle",
    "src/main/resources/META-INF/neoforge.mods.toml",
    JAVA + "highpower/VanillaCombatContract.java",
    JAVA + "highpower/VanillaMiningContract.java",
    JAVA + "gametest/HighPowerCombatRuntimeTests.java",
}
# These named relay implementation/test changes are inventoried, not certified
# by this source-scope proof. Their behavior requires independent tests and QA.
RELAY_EDITS = {
    JAVA + "highpower/RemoteScatterRelay.java",
    JAVA + "client/RemoteScatterRelayEffects.java",
    JAVA + "client/RemoteScatterRelayState.java",
    "src/test/java/cn/academy/port/client/RemoteScatterRelayVisualRegressionTest.java",
    "src/test/java/cn/academy/port/highpower/core/RemoteScatterRelayGeometryRegressionTest.java",
}
LANGUAGES = {"src/main/resources/assets/academy/lang/" + locale + ".json"
             for locale in ("en_us", "zh_cn", "zh_tw")}
PREFIX = "academy.highpower.remote_scatter_relay."
NEW_KEYS = {PREFIX + key for key in ("placing", "clearing", "segment_too_far", "too_close",
                                   "body_blocked", "no_space", "unloaded")}
UPDATED_KEYS = {PREFIX + key for key in ("measuring", "too_far")}
ADDITIONS = {
    JAVA + "gametest/HighPowerRemoteScatterRelayPlacementRuntimeTests.java",
    "docs/remote-scatter-relay-feedback-v78.md",
    "scripts/verify-remote-scatter-relay-feedback-v15.py",
    "docs/remote-scatter-relay-feedback-companion-v15.md",
    "docs/runtime-evidence/remote-scatter-relay-feedback-companion-v15.json",
}


def require(condition, marker):
    if not condition:
        raise AssertionError(marker)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def git(project, *args):
    return subprocess.check_output(["git", "-C", str(project), *args])


def tree(project):
    result = {}
    for entry in git(project, "ls-tree", "-r", "-z", BASELINE).split(b"\0"):
        if entry:
            meta, path = entry.split(b"\t", 1)
            mode, kind, oid = meta.decode().split()
            require(kind == "blob" and mode in ("100644", "100755"), "BASELINE_NONREGULAR_INPUT")
            result[path.decode()] = oid
    require(result, "BASELINE_TREE_EMPTY")
    return result


def blob_id(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def check_version(path, baseline, current):
    require(baseline.count(OLD_VERSION) == 1 and NEW_VERSION not in baseline,
            "VERSION_BASELINE_SITE_CHANGED " + path)
    require(current == baseline.replace(OLD_VERSION, NEW_VERSION),
            "VERSION_NONLITERAL_CHANGE " + path)
    require(current.replace(NEW_VERSION, OLD_VERSION) == baseline,
            "VERSION_REVERSE_ROUNDTRIP_CHANGED " + path)


def check_language(path, baseline, current):
    def parse(data):
        pairs = json.loads(data, object_pairs_hook=lambda values: values)
        require(isinstance(pairs, list) and all(isinstance(x, tuple) and len(x) == 2 for x in pairs),
                "LANGUAGE_NOT_FLAT_OBJECT " + path)
        require(len(pairs) == len(dict(pairs)), "LANGUAGE_DUPLICATE_KEY " + path)
        return dict(pairs)

    before, after = parse(baseline), parse(current)
    require(set(after) - set(before) == NEW_KEYS and not set(before) - set(after),
            "LANGUAGE_KEY_SCOPE_CHANGED " + path)
    require({key for key in before if before[key] != after[key]} == UPDATED_KEYS,
            "LANGUAGE_VALUE_SCOPE_CHANGED " + path)
    require(all(isinstance(after[key], str) and after[key] for key in NEW_KEYS | UPDATED_KEYS),
            "LANGUAGE_FEEDBACK_VALUE_INVALID " + path)

    def retained_lines(data):
        retained = []
        removed = set()
        for line in data.splitlines(keepends=True):
            text = line.lstrip()
            if any(text.startswith(json.dumps(key).encode() + b":") for key in NEW_KEYS | UPDATED_KEYS):
                key = next(key for key in NEW_KEYS | UPDATED_KEYS
                           if text.startswith(json.dumps(key).encode() + b":"))
                require(key not in removed, "LANGUAGE_DUPLICATE_LINE " + path)
                removed.add(key)
            else:
                retained.append(line)
        return b"".join(retained)

    require(retained_lines(baseline) == retained_lines(current),
            "LANGUAGE_OTHER_BYTES_CHANGED " + path)


def expected_rejection(name, callback, marker):
    try:
        callback()
    except AssertionError as error:
        require(str(error).startswith(marker), "WRONG_REJECTION " + name + ": " + str(error))
        return {"mutation": name, "result": "EXPECTED_REJECTION", "marker": marker}
    raise AssertionError("MUTANT_SURVIVED " + name)


def check_unchanged(path, data, expected_oid):
    require(blob_id(data) == expected_oid, "BASELINE_BYTES_CHANGED " + path)


def prove(project):
    baseline = tree(project)
    changed = []
    unchanged = []
    version_controls = []
    language_controls = []
    live_bytes = {}
    source = lambda path: git(project, "show", BASELINE + ":" + path)
    candidate_paths = {path.decode() for path in git(project, "ls-files", "--cached", "--others",
                        "--exclude-standard", "-z").split(b"\0") if path}
    # Also inspect all main/test source files, including ignored additions.
    for root in (project / "src/main", project / "src/test"):
        candidate_paths.update(path.relative_to(project).as_posix()
                               for path in root.rglob("*") if path.is_file())
    added = candidate_paths - set(baseline)
    added = {path for path in added if not (path.startswith("scripts/__pycache__/")
                                          and path.endswith(".pyc"))}
    require(added <= ADDITIONS, "UNAPPROVED_ADDED_INPUT " + repr(sorted(added - ADDITIONS)))
    require(JAVA + "gametest/HighPowerRemoteScatterRelayPlacementRuntimeTests.java" in added,
            "PLACEMENT_FIXTURE_MISSING")
    # The report cannot hash itself. Every other named addition is bound to the
    # actual bytes inspected during this invocation, including this runner.
    addition_audit = []
    evidence_path = "docs/runtime-evidence/remote-scatter-relay-feedback-companion-v15.json"
    for path in sorted(added - {evidence_path}):
        file = project / path
        require(file.is_file() and not file.is_symlink(), "ADDED_INPUT_NOT_REGULAR " + path)
        addition_audit.append({"path": path, "sha256": digest(file.read_bytes())})
    for path, oid in sorted(baseline.items()):
        file = project / path
        require(file.is_file() and not file.is_symlink(), "BASELINE_INPUT_MISSING " + path)
        data = file.read_bytes()
        live_bytes[path] = digest(data)
        row = {"path": path, "sha256": digest(data)}
        if path in VERSION_ONLY:
            old = source(path)
            check_version(path, old, data)
            row["status"] = "ONE_EXACT_77_TO_78_LITERAL_ONLY"
            version_controls.append(expected_rejection(path + ":extra-edit",
                lambda: check_version(path, old, data + b"\n"), "VERSION_NONLITERAL_CHANGE"))
            version_controls.append(expected_rejection(path + ":wrong-version",
                lambda: check_version(path, old, data.replace(NEW_VERSION, b"other-artifact")),
                "VERSION_NONLITERAL_CHANGE"))
        elif path in LANGUAGES:
            old = source(path)
            check_language(path, old, data)
            row["status"] = "SEVEN_NEW_TWO_UPDATED_RELAY_KEYS_ONLY_OTHER_BYTES_IDENTICAL"
            language_controls.append(expected_rejection(path + ":other-byte",
                lambda: check_language(path, old, data + b"\n"), "LANGUAGE_OTHER_BYTES_CHANGED"))
            poisoned = data.replace(b'"academy.highpower.remote_scatter_relay.placing"',
                                    b'"academy.highpower.remote_scatter_relay.unapproved"')
            language_controls.append(expected_rejection(path + ":unknown-key",
                lambda: check_language(path, old, poisoned), "LANGUAGE_KEY_SCOPE_CHANGED"))
        elif path in RELAY_EDITS:
            row["status"] = "NAMED_RELAY_EDIT_REQUIRES_SEPARATE_BEHAVIORAL_PROOF"
        else:
            check_unchanged(path, data, oid)
            unchanged.append(row)
            continue
        row["baseline_sha256"] = digest(source(path))
        changed.append(row)
    require(len(changed) == 13, "EXPECTED_CHANGE_SCOPE_CHANGED")
    immutable_controls = []
    for path in (REGISTRY, JAVA + "highpower/core/RemoteScatterRelayRules.java",
                 "src/main/resources/classic-skills.json", V14, FIXTURE + "contract-v1.json"):
        data = (project / path).read_bytes()
        immutable_controls.append(expected_rejection(path + ":extra-byte",
            lambda: check_unchanged(path, data + b"\n", baseline[path]), "BASELINE_BYTES_CHANGED"))

    # Import only after its full source bytes have been checked against .77.
    # Run existing registry-only functions; never enter v14.main or compile.
    v14 = runpy.run_path(str(project / V14))
    contract_bytes = (project / FIXTURE / "contract-v1.json").read_bytes()
    contract = json.loads(contract_bytes)
    amendment, old_registry, reconstructed, prior = v14["load_registry_chain"](
        project, project / FIXTURE, contract_bytes, contract, lambda path: path.read_bytes())
    registry = (project / REGISTRY).read_bytes()
    require(registry == reconstructed, "V14_RECONSTRUCTION_DIFFERS")
    v14["check_registry_append"](registry, old_registry, amendment)
    controls = v14["registry_append_controls"](registry, old_registry, amendment)
    predecessor = v14["predecessor_guard_controls"](project, project / FIXTURE,
        contract_bytes, contract, amendment, old_registry, registry, lambda path: path.read_bytes())
    require(len(v14["registry_records"](registry)) == 51, "REGISTRY_COUNT_CHANGED")
    # Reject concurrent changes to any audited baseline input.
    for path, expected in live_bytes.items():
        require(digest((project / path).read_bytes()) == expected, "INPUT_CHANGED_DURING_RUN " + path)
    for entry in addition_audit:
        require(digest((project / entry["path"]).read_bytes()) == entry["sha256"],
                "ADDED_INPUT_CHANGED_DURING_RUN " + entry["path"])
    grouped = Counter(path.split("/")[0] for path in live_bytes)
    return {
        "result": "PASS_SOURCE_SCOPE_ONLY",
        "contract": "remote-scatter-relay-feedback-source-companion-v15",
        "baseline_commit": BASELINE,
        "candidate_head": git(project, "rev-parse", "HEAD").decode().strip(),
        "baseline_tree_inputs": len(baseline),
        "unchanged_baseline_inputs": len(unchanged),
        "audited_roots": dict(sorted(grouped.items())),
        "input_inventory_sha256": digest(json.dumps(live_bytes, sort_keys=True).encode()),
        "named_changes": changed,
        "added_inputs": sorted(added),
        "added_input_audit": addition_audit,
        "registry": {"registered_before": 51, "registered_after": 51,
            "new_skills": 0, "source_sha256": digest(registry),
            "v14_registry_negative_controls": len(controls),
            "v14_predecessor_inputs": len(prior),
            "v13_registry_negative_controls": predecessor["unchanged_v13_registry_controls"],
            "v13_current_rejection": predecessor["unchanged_v13_current"],
            "predecessor_tamper_controls": len(predecessor["tamper_controls"])},
        "companion_negative_controls": version_controls + language_controls + immutable_controls,
        "historical_guards": "UNCHANGED_SEPARATE_FAILURES_NOT_REPAIRED",
        "limitations": [
            "Source-scope proof only; not an executed Charging/M41 trace or compiled-class comparison.",
            "Named relay code/test edits are inventoried and require independent behavior and native-client QA.",
            "All .77 baseline inputs outside 13 named paths are byte-identical; seven added and two changed relay translation keys are the only language changes.",
            "No historical runner, contract, fixture, class pin or historical evidence was refreshed.",
            "Historical guard failures remain separate; this result does not establish an aggregate check pass.",
            "No Gradle, Java compiler, JVM, server, or client was started."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--report", type=Path, required=True, help="Fresh JSON path; existing files refused")
    args = parser.parse_args()
    project = args.project.resolve()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    # Exclusive create prevents an old pass from being mistaken for this invocation.
    with args.report.open("x") as output:
        try:
            result = prove(project)
        except Exception as error:
            json.dump({"result": "FAIL", "reason": str(error)}, output, indent=2)
            output.write("\n")
            raise
        json.dump(result, output, indent=2)
        output.write("\n")
    print(json.dumps({key: result[key] for key in ("result", "baseline_commit", "candidate_head",
          "baseline_tree_inputs", "unchanged_baseline_inputs", "registry")}, indent=2))


if __name__ == "__main__":
    sys.dont_write_bytecode = True
    main()
