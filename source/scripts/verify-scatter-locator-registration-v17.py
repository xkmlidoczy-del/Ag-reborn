#!/usr/bin/env python3
"""Independent NEW223 registration companion against immutable existing .79 HEAD.

CPU-only and narrowly scoped: preserves all 51 old registry records/ordinals,
classic progression sources, historical test/script/fixture bytes and original
build wiring. Does not certify gameplay, rendering, class bytes or aggregate check.
No refresh, exclusion, compilation, JVM, server or client option is provided.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import sys

sys.dont_write_bytecode = True
BASELINE = "27ecc8d869ae6389253c2e5c8fe4df68b6291424"
REGISTRY = "src/main/java/cn/academy/port/highpower/HighPowerSkills.java"
SNAPSHOT_ROOT = "src/test/resources/highpower-learning/scatter-locator-v80"
REGISTRY_SHA = "7b7c560aec52fba4884d07c48d89346667847131075d9cd183934592d4b9d104"
MANIFEST_SHA = "ccd37f3d29e8125eb32aaa14ce3178eeda25000c39ae9164acd7f1258ab39a4b"
FIXTURE = "src/test/resources/current-geometry-proof/charging-core"
V14 = "scripts/verify-charging-m41-core-v14.py"
V15 = "scripts/verify-remote-scatter-relay-feedback-v15.py"
V16 = "scripts/verify-remote-scatter-relay-hud-v16.py"
JAVA_TEST = "src/test/java/cn/academy/port/highpower/ScatterLocatorPlateLearningRegressionTest.java"
OLD_VERSION = b"0.1.0-m41-highpower.0.79-prototype"
NEW_VERSION = b"0.1.0-m41-highpower.0.80-prototype"
OLD_NAMESPACE_END = b"academy_remote_scatter_relay_runtime')"
NEW_NAMESPACE_END = b"academy_remote_scatter_relay_runtime,academy_scatter_locator_runtime,academy_control_hold_runtime')"
CLASSIC_INPUTS = {
    "src/main/resources/classic-skills.json",
    "src/main/resources/classic-developer-layout.json",
    "src/main/java/cn/academy/port/SkillCatalog.java",
    "src/main/java/cn/academy/port/preset/PresetSkills.java",
    "src/main/java/cn/academy/port/core/ClassicRules.java",
    "src/main/java/cn/academy/port/core/ClassicSkillConfiguration.java",
    "src/main/java/cn/academy/port/develop/DeveloperType.java",
    "src/main/java/cn/academy/port/develop/DevelopmentActions.java",
    "src/main/java/cn/academy/port/develop/DevelopmentProcess.java",
    "src/main/java/cn/academy/port/client/ClassicDeveloperLayout.java",
}
NEW_CONSTANTS = (b'    public static final String SCATTER_LOCATOR_PLATE = "scatter_locator_plate";\n'
                 b'    public static final int SCATTER_LOCATOR_PLATE_PROPOSAL = 223;\n')
CONSTANT_ANCHOR = b'    public static final String REMOTE_SCATTER_RELAY = "remote_scatter_relay";\n'
NEW_RECORD = (b'            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, SCATTER_LOCATOR_PLATE, 2, true,\n'
              b'                    List.of(new SkillCatalog.Requirement(FOLDED_MIRROR_ARRAY, 0)), 0)')
LAST_RECORD = (b'            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, REMOTE_SCATTER_RELAY, 5, true,\n'
               b'                    List.of(new SkillCatalog.Requirement(FOLDED_MIRROR_ARRAY, 0)), 0)')
RECORD_PATTERN = (rb'new SkillCatalog\.Skill\(([^,\n]+), ([^,\n]+), ([0-9]+), (true|false),\s*'
                  rb'(List\.of\((?:[^()]|\([^()]*\))*\)), ([0-9]+)\)')


def require(condition, marker):
    if not condition:
        raise AssertionError(marker)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(project, *args):
    return subprocess.check_output(["git", "-C", str(project), *args])


def read_regular(path):
    require(path.is_file() and not path.is_symlink(), "NONREGULAR_OR_MISSING_INPUT " + str(path))
    return path.read_bytes()


def unchanged(path, data, oid):
    actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
    require(actual == oid, "HISTORICAL_INPUT_CHANGED " + path)


def records(source):
    result = list(re.finditer(RECORD_PATTERN, source))
    require(len(result) == source.count(b"new SkillCatalog.Skill("), "REGISTRY_UNPARSED_RECORD")
    return result


def reconstruct(baseline):
    require(sha(baseline) == REGISTRY_SHA, "REGISTRY_BASELINE_HASH_CHANGED")
    edits = (
        (CONSTANT_ANCHOR, NEW_CONSTANTS + CONSTANT_ANCHOR),
        (b"POINT_MELTDOWN, REMOTE_SCATTER_RELAY);", b"POINT_MELTDOWN, REMOTE_SCATTER_RELAY, SCATTER_LOCATOR_PLATE);"),
        (LAST_RECORD + b");", LAST_RECORD + b",\n" + NEW_RECORD + b");"),
    )
    result = baseline
    for before, after in edits:
        require(result.count(before) == 1, "REGISTRY_APPROVED_APPEND_SITE_CHANGED")
        result = result.replace(before, after)
    inverse = result
    for before, after in reversed(edits):
        require(inverse.count(after) == 1, "REGISTRY_REVERSE_APPEND_SITE_CHANGED")
        inverse = inverse.replace(after, before)
    require(inverse == baseline, "REGISTRY_REVERSE_APPEND_NOT_BYTE_EXACT")
    return result


def check_registry(candidate, baseline):
    require(candidate == reconstruct(baseline), "REGISTRY_223_NONAPPEND_CHANGE")
    old, new = records(baseline), records(candidate)
    require(len(old) == 51 and len(new) == 52, "REGISTRY_COUNTS_CHANGED")
    require([record.group() for record in old] == [record.group() for record in new[:51]], "REGISTRY_OLD_BYTES_OR_ORDINAL_CHANGED")
    require(new[-1].groups() == (b"HighPowerCategories.MELTDOWNER", b"SCATTER_LOCATOR_PLATE", b"2", b"true",
            b"List.of(new SkillCatalog.Requirement(FOLDED_MIRROR_ARRAY, 0))", b"0"), "REGISTRY_223_RECORD_CHANGED")
    ids = re.search(rb"IDS = Set.of\(([^)]+)\);", candidate)
    require(ids is not None and ids.group(1).split(b", ") == [record.group(2) for record in new], "REGISTRY_IDS_OR_ORDINALS_CHANGED")


def rejected(name, operation, marker):
    try:
        operation()
    except AssertionError as error:
        require(str(error).startswith(marker), "WRONG_NEGATIVE_REJECTION " + name + ": " + str(error))
        return {"mutation": name, "result": "EXPECTED_REJECTION", "marker": str(error)}
    raise AssertionError("NEGATIVE_CONTROL_SURVIVED " + name)


def registry_controls(candidate, baseline):
    controls = []
    parsed = records(candidate)

    def mutant(name, start, end, replacement):
        altered = candidate[:start] + replacement + candidate[end:]
        require(altered != candidate, "NEGATIVE_CONTROL_DID_NOT_MUTATE " + name)
        row = rejected(name, lambda: check_registry(altered, baseline), "REGISTRY_223_NONAPPEND_CHANGE")
        row["mutant_sha256"] = sha(altered)
        controls.append(row)

    for ordinal, record in enumerate(parsed):
        prefix = ("old-" if ordinal < 51 else "new-") + str(ordinal) + "-" + record.group(2).decode().lower()
        alternatives = {
            1: b"HighPowerCategories.VECMANIP" if record.group(1) != b"HighPowerCategories.VECMANIP" else b"HighPowerCategories.TELEPORTER",
            2: b"FORKED_ARC" if record.group(2) != b"FORKED_ARC" else b"MAGNETIC_BRACER",
            3: str(int(record.group(3)) + 1).encode(),
            4: b"false" if record.group(4) == b"true" else b"true",
            5: b"List.of(new SkillCatalog.Requirement(SCATTER_LOCATOR_PLATE, .5))",
            6: str(int(record.group(6)) + 1).encode(),
        }
        for group, replacement in alternatives.items():
            names = {1: "category", 2: "id", 3: "level", 4: "controllable", 5: "parent", 6: "any-level"}
            mutant(prefix + "-" + names[group], *record.span(group), replacement)
        for req in re.finditer(rb"new SkillCatalog.Requirement\(([^,]+), ([^)]+)\)", record.group(5)):
            start = record.start(5) + req.start(2)
            mutant(prefix + "-mastery", start, record.start(5) + req.end(2), b".5")
        constant = re.search(rb"\b" + record.group(2) + rb' = "([^\"]+)"', candidate)
        require(constant is not None, "NEGATIVE_SAVED_ID_CONSTANT_MISSING")
        mutant(prefix + "-saved-id", *constant.span(1), constant.group(1) + b"_changed")
        proposal = re.search(rb"\b" + record.group(2) + rb"_PROPOSAL = ([0-9]+)", candidate)
        if proposal is not None:
            mutant(prefix + "-proposal", *proposal.span(1), str(int(proposal.group(1)) + 1).encode())
        else:
            require(record.group(2) == b"CHARGE_DECOY", "NEGATIVE_PROPOSAL_MISSING")
    for ordinal in range(51):
        first, second = parsed[ordinal:ordinal + 2]
        between = candidate[first.end():second.start()]
        mutant("record-order-" + str(ordinal), first.start(), second.end(), second.group() + between + first.group())
    ids = re.search(rb"IDS = Set.of\(([^)]+)\);", candidate)
    names = ids.group(1).split(b", ")
    for ordinal in range(51):
        altered = names.copy()
        altered[ordinal], altered[ordinal + 1] = altered[ordinal + 1], altered[ordinal]
        mutant("id-order-" + str(ordinal), *ids.span(1), b", ".join(altered))
    controls.append(rejected("missing-new-entry-and-constants", lambda: check_registry(baseline, baseline), "REGISTRY_223_NONAPPEND_CHANGE"))
    mutant("missing-new-entry-only", parsed[-2].end(), parsed[-1].end(), b"")
    mutant("unrelated-old-method-byte", len(candidate), len(candidate), b"\n")
    require(len(controls) == 568, "REGISTRY_NEGATIVE_COVERAGE_CHANGED " + str(len(controls)))
    return controls


def check_build(old, candidate):
    require(old.count(OLD_VERSION) == 1 and candidate.count(NEW_VERSION) == 1, "BUILD_VERSION_SCOPE_CHANGED")
    restored = candidate.replace(NEW_VERSION, OLD_VERSION)
    require(old.count(OLD_NAMESPACE_END) == 1 and restored.count(NEW_NAMESPACE_END) == 1, "BUILD_ADDED_NAMESPACES_CHANGED")
    restored = restored.replace(NEW_NAMESPACE_END, OLD_NAMESPACE_END)
    require(restored.startswith(old), "BUILD_OLD_WIRING_CHANGED")
    appended = restored[len(old):]
    require(appended and not re.search(rb"\b(exclude|onlyIf|setEnabled|setDependsOn)\b|\.enabled\s*=|\.dependsOn\s*=", appended), "BUILD_APPEND_REMOVES_OLD_CHECKS")
    require(b"ScatterLocatorPlateLearningRegressionTest" in appended and b"verify-scatter-locator-registration-v17.py" in appended,
            "NEW_PROOF_BUILD_REGISTRATION_MISSING")
    return {"old_body": "BYTE_EXACT_AFTER_ONE_VERSION_LITERAL_AND_TWO_APPENDED_NAMESPACES_REVERSED", "appended_sha256": sha(appended)}


def prove(project):
    require(git(project, "rev-parse", "--verify", BASELINE + "^{commit}").decode().strip() == BASELINE, "IMMUTABLE_BASELINE_COMMIT_MISSING")
    tree = {}
    for entry in git(project, "ls-tree", "-r", "-z", BASELINE).split(b"\0"):
        if entry:
            meta, path = entry.split(b"\t", 1)
            mode, kind, oid = meta.decode().split()
            tree[path.decode()] = (mode, kind, oid)
    require(CLASSIC_INPUTS <= tree.keys(), "CLASSIC_BASELINE_PATH_MISSING")
    protected = sorted(path for path in tree if path.startswith(("src/test/", "scripts/", "gradle/")) or path in CLASSIC_INPUTS)
    live_hashes = {}
    for path in protected:
        mode, kind, oid = tree[path]
        require(kind == "blob" and mode in ("100644", "100755"), "UNSUPPORTED_BASELINE_INPUT " + path)
        data = read_regular(project / path)
        unchanged(path, data, oid)
        live_hashes[path] = sha(data)
    manifest_bytes = read_regular(project / SNAPSHOT_ROOT / "manifest.json")
    require(sha(manifest_bytes) == MANIFEST_SHA, "SNAPSHOT_MANIFEST_HASH_CHANGED")
    manifest = json.loads(manifest_bytes)
    require(manifest["baseline_revision"] == BASELINE and len(manifest["files"]) == 3, "SNAPSHOT_PROVENANCE_CHANGED")
    for path, entry in manifest["files"].items():
        snapshot = read_regular(project / SNAPSHOT_ROOT / entry["snapshot"])
        require(sha(snapshot) == entry["sha256"] and snapshot == git(project, "show", BASELINE + ":" + path), "SNAPSHOT_NOT_GENUINE_BASELINE " + path)
        live_hashes[SNAPSHOT_ROOT + "/" + entry["snapshot"]] = sha(snapshot)
    live_hashes[SNAPSHOT_ROOT + "/manifest.json"] = sha(manifest_bytes)
    baseline = git(project, "show", BASELINE + ":" + REGISTRY)
    candidate = read_regular(project / REGISTRY)
    check_registry(candidate, baseline)
    controls = registry_controls(candidate, baseline)
    live_hashes[REGISTRY] = sha(candidate)
    build = read_regular(project / "build.gradle")
    build_audit = check_build(git(project, "show", BASELINE + ":build.gradle"), build)
    live_hashes["build.gradle"] = sha(build)
    for path in CLASSIC_INPUTS | {V14, V15, V16}:
        controls.append(rejected("protected-byte-" + path, lambda path=path: unchanged(path, read_regular(project / path) + b"\n", tree[path][2]), "HISTORICAL_INPUT_CHANGED"))
    old_build = git(project, "show", BASELINE + ":build.gradle")
    controls.append(rejected("old-build-task-wiring", lambda: check_build(old_build, build.replace(b"dependsOn 'remoteScatterRelayLearningTest'", b"dependsOn 'missingOldTest'")), "BUILD_OLD_WIRING_CHANGED"))

    # Existing source functions only: no main entry point, JVM or compilation.
    # Their own pins still validate the entire original registry evidence chain.
    v14 = runpy.run_path(str(project / V14))
    contract_bytes = read_regular(project / FIXTURE / "contract-v1.json")
    contract = json.loads(contract_bytes)
    amendment, previous_registry, reconstructed, audit = v14["load_registry_chain"](
        project, project / FIXTURE, contract_bytes, contract, lambda path: path.read_bytes())
    require(reconstructed == baseline, "PRIOR_REGISTRY_CHAIN_NOT_EXACT_HEAD")
    old_controls = v14["registry_append_controls"](baseline, previous_registry, amendment)
    predecessors = v14["predecessor_guard_controls"](project, project / FIXTURE, contract_bytes,
        contract, amendment, previous_registry, baseline, lambda path: path.read_bytes())
    historical = {
        "v14_current_registry": rejected("unchanged-v14-rejects-NEW223", lambda: v14["check_registry_append"](candidate, previous_registry, amendment), "REGISTRY_253_NONAPPEND_CHANGE"),
        "old_learning_runner": "BYTE_EXACT_WITH_51_SKILL_AND_13_MD_NODE_EXPECTATIONS; CURRENT_RUNTIME_RESULT_REPORTED_SEPARATELY",
        "aggregate_check": "NOT_RUN_BY_THIS_COMPANION; NO_AGGREGATE_PASS_CLAIM",
        "existing_three_hash_failures": "NOT_REPAIRED_OR_RELABELLED; HISTORICAL_RUNNERS_FIXTURES_AND_PINS_PRESERVED",
    }
    for name, path in (("v15", V15), ("v16", V16)):
        namespace = runpy.run_path(str(project / path))
        historical[name + "_current_source_scope"] = rejected("unchanged-" + name + "-rejects-new-scope", lambda namespace=namespace: namespace["prove"](project), "UNAPPROVED_ADDED_INPUT")
    for path in (JAVA_TEST, "scripts/verify-scatter-locator-registration-v17.py"):
        live_hashes[path] = sha(read_regular(project / path))
    for path, expected in live_hashes.items():
        require(sha(read_regular(project / path)) == expected, "INPUT_CHANGED_DURING_RUN " + path)
    return {
        "result": "PASS_REGISTRATION_SCOPE_ONLY",
        "contract": "scatter-locator-registration-v17",
        "baseline_commit": BASELINE,
        "candidate_head": git(project, "rev-parse", "HEAD").decode().strip(),
        "registry": {"before": 51, "after": 52, "new_ordinal": 51, "new_proposal": 223,
                     "baseline_sha256": sha(baseline), "candidate_sha256": sha(candidate),
                     "all_old_records": "BYTE_RECORD_AND_ORDINAL_EXACT", "negative_controls": 568},
        "protected_historical_inputs": len(protected),
        "protected_input_inventory_sha256": sha(json.dumps({path: live_hashes[path] for path in protected}, sort_keys=True).encode()),
        "build_wiring": build_audit,
        "negative_controls": controls,
        "prior_registry_chain": {"v14_controls": len(old_controls), "v13_controls": predecessors["unchanged_v13_registry_controls"],
                                 "tamper_controls": len(predecessors["tamper_controls"]), "pinned_inputs": len(audit)},
        "historical_status": historical,
        "current_inputs": live_hashes,
        "limitations": [
            "Registration and historical-input preservation only, not a whole-tree source-scope audit.",
            "No class-byte differential, gameplay execution, rendering, translation quality or aggregate check pass is established.",
            "Learning, codec, preset, configuration, XP denominator, translations and layout are tested by the separate current Java regression.",
            "All historical src/test, scripts, gradle and named classic inputs remain their genuine baseline bytes; no old expected total or hash was refreshed.",
            "The three historical aggregate hash failures and count-bound predecessor failures remain independent reportable results.",
            "No Gradle, compiler, JVM, server or client was launched by this companion.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--report", type=Path, required=True, help="Fresh JSON file; existing reports are refused")
    args = parser.parse_args()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    with args.report.open("x") as output:
        try:
            report = prove(args.project.resolve())
        except Exception as error:
            json.dump({"result": "FAIL", "contract": "scatter-locator-registration-v17", "reason": str(error)}, output, indent=2)
            output.write("\n")
            print("FAIL scatter-locator-registration-v17:", error, file=sys.stderr)
            return 1
        json.dump(report, output, indent=2)
        output.write("\n")
    print(report["result"], "NEW223: 51 retained + 1 appended;", len(report["negative_controls"]), "negative controls;", args.report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
