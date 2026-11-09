#!/usr/bin/env python3
"""Prove only the seven NEW452 native learning assertion append amendments.

The .80 commit, preserved snapshots and exact reversible manifest retain every
old assertion, annotation, call site and ordinal. No JVM or server is started.
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
BASELINE = "c334bbc6583d991bcd4bfa28de4312213d156089"
ROOT = "src/test/resources/highpower-learning/autonomous-vector-circle-native-v21"
CONTRACT = ROOT + "/contract-v21.json"
CONTRACT_SHA = "fff0b2cf63752a79093a15b59414036b3c175842a2b8c909ca6a0c8b12a1b890"
GATE = "scripts/verify-autonomous-vector-circle-learning-v20.py"
GATE_SHA = "4fbfe71c59d54e1a05b43ca73000c8b5f9837bf7ae1b7ed55a3148b303e3b775"


def prove(project):
    gate_path = project / GATE
    if not gate_path.is_file() or gate_path.is_symlink() or hashlib.sha256(gate_path.read_bytes()).hexdigest() != GATE_SHA:
        raise AssertionError("V20_GATE_CHANGED")
    gate = runpy.run_path(str(project / GATE))
    require, read, sha = gate["require"], gate["read"], gate["sha"]
    git, rejected, exact = gate["git"], gate["rejected"], gate["exact"]
    raw = read(project / CONTRACT)
    require(sha(raw) == CONTRACT_SHA, "V21_CONTRACT_CHANGED")
    contract = json.loads(raw)
    require(contract["version"] == 21 and contract["baseline_revision"] == BASELINE and len(contract["files"]) == 7
            and contract["approved_patch_count"] == 25, "NATIVE_CONTRACT_SCOPE_CHANGED")
    learning, _, _, _, registration = gate["audit"](project)
    require(registration["result"] == "PASS_NEW452_REGISTRATION_SCOPE_ONLY", "NEW452_REGISTRATION_GATE_FAILED")
    paths = git(project,"ls-tree","-r","--name-only",BASELINE,"src/main/java/cn/academy/port/gametest").decode().splitlines()
    actual = sorted(p for p in paths if "Learning" in Path(p).name and p.endswith(".java"))
    expected = {row["path"]:row["sha256"] for row in contract["native_learning_baseline"]}
    require(actual == sorted(expected) and len(actual) == 22,"NATIVE_LEARNING_INVENTORY_CHANGED")
    inputs={CONTRACT:sha(raw),GATE:sha(read(project/GATE))};changed={};untouched=[];controls=[];clauses=0
    controls.append(rejected("contract-byte",lambda:require(sha(raw+b"\n")==CONTRACT_SHA,"V21_CONTRACT_CHANGED"),"V21_CONTRACT_CHANGED"))
    for path in actual:
        baseline=git(project,"show",BASELINE+":"+path);candidate=read(project/path)
        require(sha(baseline)==expected[path],"GENUINE_NATIVE_BASELINE_CHANGED "+path);inputs[path]=sha(candidate)
        if path not in contract["files"]:
            require(candidate==baseline,"OTHER_NATIVE_LEARNING_SOURCE_CHANGED "+path);untouched.append(path)
            controls.append(rejected(path+":unrelated-byte",lambda candidate=candidate,baseline=baseline:
                require(candidate+b"\n"==baseline,"OTHER_NATIVE_LEARNING_SOURCE_CHANGED"),"OTHER_NATIVE_LEARNING_SOURCE_CHANGED"))
            continue
        spec=contract["files"][path];snapshot_path=ROOT+"/"+spec["snapshot"];snapshot=read(project/snapshot_path)
        require(snapshot==baseline,"NATIVE_SNAPSHOT_NOT_AUTHENTIC_V80 "+path);inputs[snapshot_path]=sha(snapshot)
        exact(baseline,candidate,spec)
        controls.extend(gate["source_controls"](path,baseline,candidate,spec))
        for token in (b"@GameTest(",b"h.assertTrue(",b"h.assertFalse("):
            require(baseline.count(token)==candidate.count(token),"NATIVE_OLD_ASSERTION_OR_ANNOTATION_COUNT_CHANGED")
        def mutate(name,before,after):
            require(before in candidate and before!=after,"NATIVE_NEGATIVE_SITE_MISSING "+name)
            mutant=candidate.replace(before,after,1)
            controls.append(rejected(path+":"+name,lambda:exact(baseline,mutant,spec),"UNAPPROVED_SOURCE_CHANGE"))
        for name,before,after in (
            ("old-ordinal-50",b'HighPowerSkills.ALL.get(50).id().equals("remote_scatter_relay")',b'HighPowerSkills.ALL.get(49).id().equals("remote_scatter_relay")'),
            ("old-new223-id",b'"scatter_locator_plate"',b'"old223_changed"'),
            ("old-new223-prerequisite",b'new cn.academy.port.SkillCatalog.Requirement("folded_mirror_array", 0)',b'new cn.academy.port.SkillCatalog.Requirement("thermal_shell", 0)'),
            ("new452-prerequisite",b'new cn.academy.port.SkillCatalog.Requirement("multi_axis_reflection", 0)',b'new cn.academy.port.SkillCatalog.Requirement("vector_cage", 0)'),
            ("new452-proposal",b'AUTONOMOUS_VECTOR_CIRCLE_PROPOSAL == 452',b'AUTONOMOUS_VECTOR_CIRCLE_PROPOSAL == 453'),
            ("mechanics-assertion-negated",b'h.assertTrue(',b'h.assertFalse('),
            ("native-annotation-disabled",b'@GameTest(',b'@DisabledGameTest(')):
            mutate(name,before,after)
        for patch in spec["patches"]:
            head=patch["after"].encode().split(b" && ",1)[0];count=re.search(rb"== ([0-9]+)$",head)
            require(count is not None,"NATIVE_NEGATIVE_COUNT_SITE_MISSING")
            mutate("inflated-"+patch["name"],head,head[:count.start(1)]+str(int(count.group(1))+1).encode())
            mutate("removed-new452-entry-"+patch["name"],patch["after"].encode(),patch["before"].encode().replace(b'== '+str(int(count.group(1))-1).encode(),b'== '+count.group(1),1))
        clauses+=len(spec["patches"])
        changed[path]={"baseline_sha256":sha(baseline),"current_sha256":sha(candidate),"named_registration_clauses":len(spec["patches"]),
                       "inverse":"BYTE_EXACT_AUTHENTIC_V80","old_assertions_and_ordinals":"RETAINED"}
    require(len(changed)==7 and len(untouched)==15 and clauses==25,"NATIVE_APPROVED_SCOPE_CHANGED")
    require(len(controls)==154,"NATIVE_NEGATIVE_CONTROL_COVERAGE_CHANGED " + str(len(controls)))
    for path,expected_sha in inputs.items():require(sha(read(project/path))==expected_sha,"INPUT_CHANGED_DURING_RUN "+path)
    return {"result":"PASS_NATIVE_REGISTRATION_SOURCE_SCOPE_ONLY","contract":"autonomous-vector-circle-native-v21",
            "baseline_commit":BASELINE,"candidate_head":git(project,"rev-parse","HEAD").decode().strip(),
            "changed_native_sources":changed,"unchanged_native_learning_sources":untouched,"named_registration_clauses":clauses,
            "registry_gate":registration,"negative_controls":controls,"current_inputs":inputs,
            "limitations":["Source-scope proof only. Native execution and gameplay must be reported separately.",
                "Exactly seven current sources and 25 registration clauses changed; the other fifteen .80 native learning sources stay byte-exact.",
                "The original v18/v19 scripts, fixtures and hashes remain intact and reject the current NEW452 registry.",
                "No compiler, JVM, Gradle, GameTest server, client, aggregate or full-check execution occurs here."]}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--project",type=Path,default=Path(__file__).resolve().parents[1]);parser.add_argument("--report",type=Path,required=True)
    args=parser.parse_args();args.report.parent.mkdir(parents=True,exist_ok=True)
    with args.report.open("x") as output:
        try:report=prove(args.project.resolve())
        except Exception as error:report={"result":"FAIL","contract":"autonomous-vector-circle-native-v21","reason":str(error)}
        json.dump(report,output,indent=2);output.write("\n")
    print(report["result"],args.report)
    if report["result"]=="FAIL":print(report["reason"],file=sys.stderr);return 1
    return 0
if __name__=="__main__":raise SystemExit(main())
