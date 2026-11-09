#!/usr/bin/env python3
"""Audit the seven versioned NEW223 native-learning registration assertions.

Authentic .79 sources and a pinned contract retain every old mechanics assertion,
ordinal, annotation and call site. Only 25 named count-and-entry clauses change.
This CPU-only proof does not run Java, Gradle or the native GameTest server.
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
ROOT = "src/test/resources/highpower-learning/scatter-locator-native-v19"
CONTRACT = ROOT + "/contract-v19.json"
CONTRACT_SHA = "9be4534cb48e1e4a2e672fa6d8bf376da8b3f9b1d65c47b691d65b3e5450f191"
NATIVE = "src/main/java/cn/academy/port/gametest/"


def require(condition, marker):
    if not condition:
        raise AssertionError(marker)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def git(project, *args):
    return subprocess.check_output(["git", "-C", str(project), *args])


def read(path):
    require(path.is_file() and not path.is_symlink(), "MISSING_OR_NONREGULAR_INPUT " + str(path))
    return path.read_bytes()


def pinned(data, expected, marker):
    require(sha(data) == expected, marker)


def reconstruct(baseline, spec):
    pinned(baseline, spec["sha256"], "NATIVE_BASELINE_SOURCE_CHANGED")
    candidate = baseline
    for patch in spec["patches"]:
        before, after = patch["before"].encode(), patch["after"].encode()
        require(candidate.count(before) == 1, "APPROVED_NATIVE_PATCH_SITE_CHANGED " + patch["name"])
        candidate = candidate.replace(before, after, 1)
    inverse = candidate
    for patch in reversed(spec["patches"]):
        before, after = patch["before"].encode(), patch["after"].encode()
        require(inverse.count(after) == 1, "NATIVE_INVERSE_PATCH_SITE_CHANGED " + patch["name"])
        inverse = inverse.replace(after, before, 1)
    require(inverse == baseline, "NATIVE_INVERSE_NOT_BYTE_EXACT")
    pinned(candidate, spec["current_sha256"], "NATIVE_EXPECTED_SOURCE_PIN_CHANGED")
    return candidate


def exact_source(baseline, candidate, spec):
    require(candidate == reconstruct(baseline, spec), "NATIVE_UNAPPROVED_SOURCE_CHANGE")


def rejected(name, operation, marker):
    try:
        operation()
    except AssertionError as error:
        require(str(error).startswith(marker), "WRONG_NEGATIVE_REJECTION " + name + ": " + str(error))
        return {"mutation": name, "result": "EXPECTED_REJECTION", "marker": str(error)}
    raise AssertionError("NEGATIVE_CONTROL_SURVIVED " + name)


def source_controls(path, baseline, candidate, spec):
    result = [rejected(path + ":baseline-byte", lambda: reconstruct(baseline + b"\n", spec),
                       "NATIVE_BASELINE_SOURCE_CHANGED")]

    def mutant(name, before, after):
        require(before != after and candidate.count(before) >= 1, "NEGATIVE_SITE_MISSING " + name)
        changed = candidate.replace(before, after, 1)
        row = rejected(path + ":" + name, lambda: exact_source(baseline, changed, spec),
                       "NATIVE_UNAPPROVED_SOURCE_CHANGE")
        row["mutant_sha256"] = sha(changed)
        result.append(row)

    mutant("old-ordinal-50-to-49", b'HighPowerSkills.ALL.get(50).id().equals("remote_scatter_relay")',
           b'HighPowerSkills.ALL.get(49).id().equals("remote_scatter_relay")')
    mutant("wrong-new223-id", b'"scatter_locator_plate"', b'"scatter_locator_plate_changed"')
    mutant("wrong-new223-parent", b'new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)',
           b'new cn.academy.port.SkillCatalog.Requirement("thermal_shell", 0)')
    mutant("wrong-new223-proposal", b'SCATTER_LOCATOR_PLATE_PROPOSAL == 223',
           b'SCATTER_LOCATOR_PLATE_PROPOSAL == 224')
    mutant("mechanics-assertion-negated", b"h.assertTrue(", b"h.assertFalse(")
    mutant("test-annotation-disabled", b"@GameTest(", b"@DisabledGameTest(")
    for patch in spec["patches"]:
        before, after = patch["before"].encode(), patch["after"].encode()
        mutant("omitted-" + patch["name"], after, before)
        head, _ = after.split(b" && ", 1)
        mutant("missing-new-entry-" + patch["name"], after, head)
        count = re.search(rb"== ([0-9]+)$", head)
        require(count is not None, "NEGATIVE_TOTAL_SITE_MISSING")
        wrong = head[:count.start(1)] + str(int(count.group(1)) + 1).encode()
        mutant("inflated-total-" + patch["name"], head, wrong)
    return result


def registry_witness(source, parsed):
    result = []
    for ordinal, record in enumerate(parsed):
        key = record.group(2)
        saved = re.search(rb"\b" + key + rb' = "([^\"]+)"', source)
        proposal = re.search(rb"\b" + key + rb"_PROPOSAL = ([0-9]+)", source)
        require(saved is not None, "REGISTRY_SAVED_ID_MISSING")
        result.append({"ordinal": ordinal, "constant": key.decode(), "saved_id": saved.group(1).decode(),
                       "proposal": int(proposal.group(1)) if proposal else None,
                       "record_source": record.group().decode(), "sha256": sha(record.group())})
    return result


def prove(project):
    require(git(project, "rev-parse", "--verify", BASELINE + "^{commit}").decode().strip() == BASELINE,
            "GENUINE_BASELINE_COMMIT_MISSING")
    raw = read(project / CONTRACT)
    pinned(raw, CONTRACT_SHA, "NATIVE_CONTRACT_CHANGED")
    contract = json.loads(raw)
    require(contract["version"] == 19 and contract["baseline_revision"] == BASELINE
            and len(contract["files"]) == 7 and contract["approved_patch_count"] == 25,
            "NATIVE_CONTRACT_SCOPE_CHANGED")
    gate = contract["registration_gate"]
    pinned(read(project / gate["path"]), gate["sha256"], "V17_REGISTRATION_GATE_CHANGED")
    namespace = runpy.run_path(str(project / gate["path"]))
    registration = namespace["prove"](project)
    require(registration["result"] == "PASS_REGISTRATION_SCOPE_ONLY", "REGISTRATION_GATE_FAILED")
    inputs = {CONTRACT: sha(raw), gate["path"]: gate["sha256"]}
    controls = [rejected("contract-byte-tamper", lambda: pinned(raw + b"\n", CONTRACT_SHA,
                        "NATIVE_CONTRACT_CHANGED"), "NATIVE_CONTRACT_CHANGED")]
    registry = contract["registry_baseline"]
    snapshot_path = ROOT + "/" + registry["snapshot"]
    baseline = read(project / snapshot_path)
    pinned(baseline, registry["sha256"], "REGISTRY_SNAPSHOT_CHANGED")
    require(baseline == git(project, "show", BASELINE + ":" + registry["path"]),
            "REGISTRY_SNAPSHOT_NOT_AUTHENTIC")
    witness_path = ROOT + "/" + registry["witness"]
    witness_raw = read(project / witness_path)
    pinned(witness_raw, registry["witness_sha256"], "REGISTRY_WITNESS_CHANGED")
    witness = json.loads(witness_raw)
    require(witness["baseline_revision"] == BASELINE and witness["registry_sha256"] == sha(baseline),
            "REGISTRY_WITNESS_PROVENANCE_CHANGED")
    current_registry = read(project / registry["path"])
    namespace["check_registry"](current_registry, baseline)
    old_records = registry_witness(baseline, namespace["records"](baseline))
    current_prefix = registry_witness(current_registry, namespace["records"](current_registry)[:51])
    require(len(old_records) == 51 and witness["records"] == old_records == current_prefix,
            "OLD_51_RECORD_ORDINAL_ID_WITNESS_CHANGED")
    inputs.update({snapshot_path: sha(baseline), witness_path: sha(witness_raw),
                   registry["path"]: sha(current_registry)})
    controls.append(rejected("registry-witness-byte-tamper", lambda: pinned(witness_raw + b"\n",
                    registry["witness_sha256"], "REGISTRY_WITNESS_CHANGED"), "REGISTRY_WITNESS_CHANGED"))
    controls.append(rejected("registry-snapshot-byte-tamper", lambda: pinned(baseline + b"\n",
                    registry["sha256"], "REGISTRY_SNAPSHOT_CHANGED"), "REGISTRY_SNAPSHOT_CHANGED"))

    tree = git(project, "ls-tree", "-r", "--name-only", BASELINE, NATIVE).decode().splitlines()
    learning = sorted(path for path in tree if "Learning" in Path(path).name and path.endswith(".java"))
    expected = {row["path"]: row["sha256"] for row in contract["native_learning_baseline"]}
    require(learning == sorted(expected) and len(learning) == 22, "NATIVE_LEARNING_INVENTORY_CHANGED")
    changed, untouched, patches = {}, [], 0
    for path in learning:
        prior = git(project, "show", BASELINE + ":" + path)
        pinned(prior, expected[path], "NATIVE_BASELINE_INVENTORY_HASH_CHANGED")
        candidate = read(project / path)
        inputs[path] = sha(candidate)
        if path in contract["files"]:
            spec = contract["files"][path]
            preserved_path = ROOT + "/" + spec["snapshot"]
            preserved = read(project / preserved_path)
            require(preserved == prior, "NATIVE_SNAPSHOT_NOT_AUTHENTIC " + path)
            exact_source(prior, candidate, spec)
            inputs[preserved_path] = sha(preserved)
            controls.extend(source_controls(path, prior, candidate, spec))
            for token in (b"@GameTest(", b"h.assertTrue(", b"h.assertFalse("):
                require(prior.count(token) == candidate.count(token), "NATIVE_ASSERTION_COUNT_CHANGED")
            changed[path] = {"baseline_sha256": sha(prior), "current_sha256": sha(candidate),
                             "named_registration_clauses": len(spec["patches"]),
                             "inverse": "BYTE_EXACT_AUTHENTIC_V79", "old_assertions_and_ordinals": "RETAINED"}
            patches += len(spec["patches"])
        else:
            require(candidate == prior, "OTHER_NATIVE_LEARNING_SOURCE_CHANGED " + path)
            untouched.append(path)
            controls.append(rejected(path + ":unchanged-source-byte", lambda candidate=candidate, path=path:
                pinned(candidate + b"\n", expected[path], "OTHER_NATIVE_LEARNING_SOURCE_CHANGED"),
                "OTHER_NATIVE_LEARNING_SOURCE_CHANGED"))
    require(len(changed) == 7 and len(untouched) == 15 and patches == 25, "NATIVE_APPROVED_SCOPE_CHANGED")
    require(len(controls) == 142, "NATIVE_NEGATIVE_COVERAGE_CHANGED " + str(len(controls)))
    for path, expected_sha in inputs.items():
        pinned(read(project / path), expected_sha, "INPUT_CHANGED_DURING_RUN " + path)
    return {"result": "PASS_NATIVE_REGISTRATION_SOURCE_SCOPE_ONLY", "contract": "scatter-locator-native-v19",
            "baseline_commit": BASELINE, "candidate_head": git(project, "rev-parse", "HEAD").decode().strip(),
            "changed_native_sources": changed, "unchanged_native_learning_sources": untouched,
            "named_registration_clauses": patches, "old_registry_witness": {"count": 51,
                "result": "BYTE_RECORD_ORDINAL_SAVED_ID_AND_PROPOSAL_EXACT", "path": witness_path},
            "registry_gate": {"result": registration["result"], "registry": registration["registry"],
                "negative_controls": len(registration["negative_controls"])},
            "negative_controls": controls, "current_inputs": inputs,
            "limitations": ["Source-scope and registry proof only; native execution results must be reported separately.",
                "The seven current GameTest sources are versioned successors; their authentic prior sources remain in snapshots.",
                "All 25 prior totals are retained in immutable evidence and updated only in named current registration clauses.",
                "No production source, historical src/test runner, existing verifier, fixture pin or build wiring is modified here.",
                "No Gradle, compiler, JVM, server or client is launched; no aggregate or gameplay pass is claimed."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--report", type=Path, required=True, help="Fresh JSON report; existing reports are refused")
    args = parser.parse_args()
    args.report.parent.mkdir(parents=True, exist_ok=True)
    with args.report.open("x") as output:
        try:
            report = prove(args.project.resolve())
        except Exception as error:
            json.dump({"result": "FAIL", "contract": "scatter-locator-native-v19", "reason": str(error)}, output, indent=2)
            output.write("\n")
            print("FAIL scatter-locator-native-v19:", error, file=sys.stderr)
            return 1
        json.dump(report, output, indent=2)
        output.write("\n")
    print(report["result"], "7 versioned sources, 25 clauses, 51 old records,",
          len(report["negative_controls"]), "native negative controls;", args.report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
