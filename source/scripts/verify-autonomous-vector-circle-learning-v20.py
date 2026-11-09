#!/usr/bin/env python3
"""NEW452 append proof and current learning companions against authentic .80.

Audit mode is CPU-only. Execution compiles only 29 temporary test companions
against actual existing production classes. Historical runners and gates stay
immutable, and their expected failures are separate from this scoped result.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import sys
sys.dont_write_bytecode = True
BASELINE = "c334bbc6583d991bcd4bfa28de4312213d156089"
ROOT = "src/test/resources/highpower-learning/autonomous-vector-circle-v20"
CONTRACT = ROOT + "/contract-v20-r3.json"
CONTRACT_SHA = "191194b588335b3f0692b6bd3310f472e80786d98c523a2a4c9d196d1ac3fc05"
REGISTRY = "src/main/java/cn/academy/port/highpower/HighPowerSkills.java"
PROTOCOL = "src/main/java/cn/academy/port/highpower/HighPowerLearningProtocol.java"
NEW_TEST = "cn.academy.port.highpower.AutonomousVectorCircleLearningRegressionTest"
V18 = "scripts/verify-scatter-locator-learning-v18.py"
V19 = "scripts/verify-scatter-locator-native-v19.py"
RECORD = rb'new SkillCatalog\.Skill\(([^,\n]+), ([^,\n]+), ([0-9]+), (true|false),\s*(List\.of\((?:[^()]|\([^()]*\))*\)), ([0-9]+)\)'


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


def reconstruct(source, spec):
    pinned(source, spec["input_sha256"], "BASELINE_SOURCE_CHANGED")
    candidate = source
    for patch in spec["patches"]:
        before, after = patch["before"].encode(), patch["after"].encode()
        require(before != after and candidate.count(before) == patch["occurrences"],
                "APPROVED_PATCH_SITE_CHANGED " + patch["name"])
        candidate = candidate.replace(before, after)
    pinned(candidate, spec["output_sha256"], "UNAPPROVED_OUTPUT_SOURCE_CHANGE")
    reverse = candidate
    for patch in reversed(spec["patches"]):
        before, after = patch["before"].encode(), patch["after"].encode()
        require(reverse.count(after) == patch["occurrences"], "INVERSE_PATCH_SITE_CHANGED " + patch["name"])
        reverse = reverse.replace(after, before)
    require(reverse == source, "INVERSE_NOT_BYTE_EXACT_AUTHENTIC_V80")
    return candidate


def exact(source, candidate, spec):
    require(candidate == reconstruct(source, spec), "UNAPPROVED_SOURCE_CHANGE")


def rejected(name, operation, marker):
    try:
        operation()
    except AssertionError as error:
        require(str(error).startswith(marker), "WRONG_NEGATIVE_REJECTION " + name + ": " + str(error))
        return {"mutation": name, "result": "EXPECTED_REJECTION", "marker": str(error)}
    raise AssertionError("NEGATIVE_CONTROL_SURVIVED " + name)


def source_controls(path, source, candidate, spec):
    controls = [rejected(path + ":baseline-byte", lambda: reconstruct(source + b"\n", spec), "BASELINE_SOURCE_CHANGED")]
    def mutate(name, before, after):
        require(before != after and before in candidate, "NEGATIVE_SITE_MISSING " + name)
        changed = candidate.replace(before, after, 1)
        controls.append(rejected(path + ":" + name, lambda: exact(source, changed, spec), "UNAPPROVED_SOURCE_CHANGE"))
    mutate("unrelated-byte", candidate[-1:], candidate[-1:] + b"\n")
    for patch in spec["patches"]:
        after = patch["after"].encode()
        if after in candidate:
            mutate("omitted-" + patch["name"], after, patch["before"].encode())
    if "main_class" in spec:
        mutate("mechanics-assertion-bypassed", b"check(", b"unreviewedCheck(")
        main = re.search(rb"public static void main\([^)]*\)[^{]*\{", candidate)
        require(main is not None, "ORIGINAL_MAIN_MISSING")
        mutate("original-main-skipped", main.group(), main.group() + b" if (true) return; ")
    if path.endswith("/CirculationReliefLearningRegressionTest.java"):
        mutate("new452-page-id", b'page.get(12).id().equals("autonomous_vector_circle")', b'page.get(12).id().equals("wrong_id")')
        mutate("new452-page-prerequisite", b'page.get(12).parent().equals("multi_axis_reflection")', b'page.get(12).parent().equals("wind_pressure_lift")')
        mutate("new452-page-count", b'page.size() == ids.length + 5', b'page.size() == ids.length + 6')
        mutate("retained-old-page-ordinal", b'page.get(11).id().equals("wind_pressure_lift")', b'page.get(10).id().equals("wind_pressure_lift")')
        mutate("retained-classic-page-count", b'classic.size() == 12', b'classic.size() == 13')
    return controls


def registry_checks(source, candidate, spec):
    exact(source, candidate, spec)
    prior = list(re.finditer(RECORD, source)); current = list(re.finditer(RECORD, candidate))
    require(len(prior) == 52 and len(current) == 53, "REGISTRY_COUNTS_CHANGED")
    require([m.group() for m in prior] == [m.group() for m in current[:52]], "OLD_52_ORDINAL_OR_RECORD_CHANGED")
    require(current[-1].groups() == (b"HighPowerCategories.VECMANIP", b"AUTONOMOUS_VECTOR_CIRCLE", b"5", b"true",
            b"List.of(new SkillCatalog.Requirement(MULTI_AXIS_REFLECTION, 0))", b"0"), "NEW452_RECORD_CHANGED")
    ids = re.search(rb"IDS = Set.of\(([^)]+)\);", candidate)
    require(ids is not None and ids.group(1).split(b", ") == [m.group(2) for m in current], "ID_SET_APPEND_CHANGED")
    return prior, current


def registry_controls(source, candidate, spec):
    prior, parsed = registry_checks(source, candidate, spec)
    controls = []
    def mutate(name, start, end, replacement):
        changed = candidate[:start] + replacement + candidate[end:]
        require(changed != candidate, "NEGATIVE_DID_NOT_MUTATE " + name)
        row = rejected(name, lambda: registry_checks(source, changed, spec), "UNAPPROVED_SOURCE_CHANGE")
        row["mutant_sha256"] = sha(changed); controls.append(row)
    for ordinal, record in enumerate(parsed):
        prefix = ("old-" if ordinal < 52 else "new-") + str(ordinal)
        choices = {1: b"HighPowerCategories.TELEPORTER" if record.group(1) != b"HighPowerCategories.TELEPORTER" else b"HighPowerCategories.VECMANIP",
                   2: b"UNAPPROVED_ID", 3: str(int(record.group(3)) + 1).encode(),
                   4: b"false" if record.group(4) == b"true" else b"true",
                   5: b"List.of(new SkillCatalog.Requirement(AUTONOMOUS_VECTOR_CIRCLE, .5))",
                   6: str(int(record.group(6)) + 1).encode()}
        for group, replacement in choices.items():
            mutate(prefix + "-field-" + str(group), *record.span(group), replacement)
        for req in re.finditer(rb"new SkillCatalog.Requirement\(([^,]+), ([^)]+)\)", record.group(5)):
            mutate(prefix + "-prerequisite-mastery", record.start(5) + req.start(2), record.start(5) + req.end(2), b".5")
        constant = re.search(rb"\b" + record.group(2) + rb' = "([^\"]+)"', candidate)
        require(constant is not None, "SAVED_ID_CONSTANT_MISSING")
        mutate(prefix + "-saved-id", *constant.span(1), constant.group(1) + b"_changed")
        proposal = re.search(rb"\b" + record.group(2) + rb"_PROPOSAL = ([0-9]+)", candidate)
        if proposal is not None:
            mutate(prefix + "-proposal", *proposal.span(1), str(int(proposal.group(1)) + 1).encode())
        else:
            require(record.group(2) == b"CHARGE_DECOY", "OLD_PROPOSAL_MISSING")
    for ordinal in range(52):
        first, second = parsed[ordinal:ordinal + 2]
        mutate("old-ordinal-swap-" + str(ordinal), first.start(), second.end(),
               second.group() + candidate[first.end():second.start()] + first.group())
    ids = re.search(rb"IDS = Set.of\(([^)]+)\);", candidate); names = ids.group(1).split(b", ")
    for ordinal in range(52):
        changed = names.copy(); changed[ordinal], changed[ordinal + 1] = changed[ordinal + 1], changed[ordinal]
        mutate("id-ordinal-swap-" + str(ordinal), *ids.span(1), b", ".join(changed))
    controls.append(rejected("missing-new452-entire-append", lambda: registry_checks(source, source, spec), "UNAPPROVED_SOURCE_CHANGE"))
    return controls


def audit(project):
    require(git(project, "rev-parse", "--verify", BASELINE + "^{commit}").decode().strip() == BASELINE, "GENUINE_V80_BASELINE_MISSING")
    raw = read(project / CONTRACT); pinned(raw, CONTRACT_SHA, "V20_CONTRACT_CHANGED"); contract = json.loads(raw)
    require(contract["version"] == 20 and contract["baseline_revision"] == BASELINE and len(contract["files"]) == 29,
            "CONTRACT_SCOPE_CHANGED")
    inputs = {CONTRACT: sha(raw)}; controls = []; generated = {}
    revision_two_spec = contract["previous_contract"]
    revision_two_raw = read(project / revision_two_spec["path"])
    pinned(revision_two_raw, "89b467f13df12a664da85c3fb3af2b17aae56a7a8e522b9904e8f2eb1fa91810", "V20_R2_CONTRACT_CHANGED")
    require(revision_two_spec["sha256"] == sha(revision_two_raw), "V20_R2_CONTRACT_PIN_CHANGED")
    revision_two = json.loads(revision_two_raw)
    previous_spec = revision_two["previous_contract"]
    previous_raw = read(project / previous_spec["path"])
    pinned(previous_raw, "3113207f7f1a607d1fa4edf618cd278202eb39449aca100be65afdad38939e97", "V20_R1_CONTRACT_CHANGED")
    require(previous_spec["sha256"] == sha(previous_raw), "V20_R1_CONTRACT_PIN_CHANGED")
    amended = json.loads(previous_raw)
    amended["files"]["src/test/java/cn/academy/port/client/SafeLandingProbeLayoutRegressionTest.java"]["main_class"] = "cn.academy.port.client.SafeLandingProbeLayoutRegressionTest"
    amended.update(revision=2, previous_contract=previous_spec)
    require(revision_two == amended, "V20_R2_EXCEEDS_PACKAGE_METADATA_CORRECTION")
    append_spec = amended["files"]["src/test/java/cn/academy/port/highpower/CirculationReliefLearningRegressionTest.java"]
    append_spec["patches"].append({"name":"new452-circulation-relief-vector-page-append",
        "before":"page.size() == ids.length + 4",
        "after":'page.size() == ids.length + 5 && page.get(12).id().equals("autonomous_vector_circle") && page.get(12).index() == 452 && page.get(12).level() == 5 && page.get(12).parent().equals("multi_axis_reflection")',
        "occurrences":1})
    append_spec["output_sha256"] = "8962fb92f7d8bac9fa4279c4b0ebce440f54eb480b4e96cdb883073c6a701a6a"
    amended.update(revision=3, previous_contract=revision_two_spec, patch_count=237, append_patch_count=121)
    require(contract == amended, "V20_R3_EXCEEDS_EXACT_CIRCULATION_PAGE_APPEND")
    prior_runner_path = ROOT + "/runner-v20-r1.py.txt"
    prior_runner = read(project / prior_runner_path)
    pinned(prior_runner, "1806d4b0fd1ac693f6ccf160aca777eb337faea7d31072215a635b7509b904d5", "V20_R1_RUNNER_CHANGED")
    inputs.update({previous_spec["path"]:sha(previous_raw), prior_runner_path:sha(prior_runner)})
    revision_two_runner_path = ROOT + "/runner-v20-r2.py.txt"
    revision_two_runner = read(project / revision_two_runner_path)
    pinned(revision_two_runner, "bfd90943151b8558fc8fbbc0a4cbd8ae36df17f10d4636b1407888858192281a", "V20_R2_RUNNER_CHANGED")
    inputs.update({revision_two_spec["path"]:sha(revision_two_raw), revision_two_runner_path:sha(revision_two_runner)})
    # The .80 object itself pins every historical runner, script, Gradle fragment and fixture.
    protected = []
    for entry in git(project, "ls-tree", "-r", "-z", BASELINE).split(b"\0"):
        if not entry: continue
        meta, path_raw = entry.split(b"\t", 1); path = path_raw.decode()
        if not path.startswith(("src/test/", "scripts/", "gradle/")): continue
        mode, kind, oid = meta.decode().split()
        require(kind == "blob" and mode in ("100644", "100755"), "UNSUPPORTED_HISTORICAL_INPUT")
        data = read(project / path)
        require(hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() == oid,
                "HISTORICAL_INPUT_CHANGED " + path)
        inputs[path] = sha(data); protected.append(path)
    for path, expected in contract["classic_inputs"].items():
        data = read(project / path)
        require(data == git(project, "show", BASELINE + ":" + path), "CLASSIC_BASELINE_CHANGED " + path)
        pinned(data, expected, "CLASSIC_INPUT_CHANGED " + path); inputs[path] = sha(data)
    legacy_spec = contract["legacy_contract"]
    legacy_raw = read(project / legacy_spec["path"]); pinned(legacy_raw, legacy_spec["sha256"], "V18_CONTRACT_CHANGED")
    legacy = json.loads(legacy_raw)
    require(sum(len(s["patches"]) for s in contract["files"].values()) == 237
            and sum(len(s["patches"]) - s["legacy_patch_count"] for s in contract["files"].values()) == 121,
            "APPROVED_PATCH_COUNT_CHANGED")
    for path, spec in {**contract["production"], **contract["files"]}.items():
        baseline = git(project, "show", BASELINE + ":" + path)
        snapshot_path = ROOT + "/" + spec["snapshot"]
        snapshot = read(project / snapshot_path)
        require(snapshot == baseline, "SNAPSHOT_NOT_AUTHENTIC_V80 " + path)
        output = reconstruct(baseline, spec)
        if path in contract["production"]:
            exact(baseline, read(project / path), spec)
        else:
            require(read(project / path) == baseline, "HISTORICAL_RUNNER_MODIFIED " + path)
            count = spec["legacy_patch_count"]
            require(spec["patches"][:count] == legacy["files"][path]["patches"] if count else path not in legacy["files"],
                    "LEGACY_PATCHES_CHANGED " + path)
            legacy_output = baseline
            for patch in spec["patches"][:count]: legacy_output = legacy_output.replace(patch["before"].encode(), patch["after"].encode())
            pinned(legacy_output, spec["legacy_output_sha256"], "LEGACY_OUTPUT_CHANGED")
            require(baseline.count(b"check(") == output.count(b"check(") == spec["assertion_call_tokens"], "ASSERTION_CALL_COUNT_CHANGED")
            require(baseline.count(b"System.out.println") == output.count(b"System.out.println"), "ORIGINAL_RESULT_REPORT_CHANGED")
            package = re.search(rb"^package\s+([a-zA-Z0-9_.]+);", baseline, re.MULTILINE)
            require(package is not None and spec["main_class"] == package.group(1).decode() + "." + Path(path).stem,
                    "MAIN_CLASS_DOES_NOT_MATCH_AUTHENTIC_PACKAGE " + path)
            generated[path] = output
        inputs[path] = sha(read(project / path)); inputs[snapshot_path] = sha(snapshot)
        controls.extend(source_controls(path, baseline, output, spec))
    registry_base = git(project, "show", BASELINE + ":" + REGISTRY); registry = read(project / REGISTRY)
    controls.extend(registry_controls(registry_base, registry, contract["production"][REGISTRY]))
    protocol_base = git(project, "show", BASELINE + ":" + PROTOCOL); protocol = read(project / PROTOCOL)
    for name, before, after in (("new-wire-alias", b'"avc"', b'"avx"'), ("old-wire-alias", b'"slp"', b'"old"'),
                               ("max-wire-limit", b'wire.length()>64', b'wire.length()>65'),
                               ("sequence-validation", b'sequence>0', b'sequence>=0')):
        require(before in protocol, "WIRE_NEGATIVE_SITE_MISSING")
        changed = protocol.replace(before, after, 1)
        controls.append(rejected(name, lambda changed=changed: exact(protocol_base, changed, contract["production"][PROTOCOL]), "UNAPPROVED_SOURCE_CHANGE"))
    historical = {}
    for path, function in ((V18, "audit"), (V19, "prove")):
        namespace = runpy.run_path(str(project / path))
        historical[path] = rejected("unchanged-historical-gate", lambda namespace=namespace, function=function: namespace[function](project),
                                    "REGISTRY_223_NONAPPEND_CHANGE")
    for path, expected in inputs.items(): pinned(read(project / path), expected, "INPUT_CHANGED_DURING_AUDIT " + path)
    require(len(controls) == 849, "NEGATIVE_CONTROL_COVERAGE_CHANGED " + str(len(controls)))
    registration = {"result":"PASS_NEW452_REGISTRATION_SCOPE_ONLY", "old_records":52, "current_records":53,
                    "new_ordinal":52, "new_proposal":452, "wire":"avc", "inverse":"BYTE_EXACT_AUTHENTIC_V80",
                    "preserved_historical_inputs":len(protected), "historical_status":historical}
    return contract, generated, controls, inputs, registration


def execute(project, args, report):
    contract, generated, controls, inputs, registration = audit(project)
    report.update({"contract":"autonomous-vector-circle-learning-v20", "baseline_commit":BASELINE,
                   "candidate_head":git(project,"rev-parse","HEAD").decode().strip(), "registry_gate":registration,
                   "source_count":29,"source_negative_controls":controls,"current_inputs":inputs,
                   "source_reconstruction":{p:{"input_sha256":s["input_sha256"],"output_sha256":s["output_sha256"],
                       "legacy_hunks":s["legacy_patch_count"],"new452_hunks":len(s["patches"])-s["legacy_patch_count"],
                       "assertion_call_tokens":s["assertion_call_tokens"]} for p,s in contract["files"].items()},
                   "limitations":["Source and learning companion scope only; this is not aggregate check, gameplay or visual acceptance.",
                       "Original v18/v19 gates remain immutable and still reject NEW452 independently.",
                       "Only temporary historical test copies are amended. Production classes are actual supplied compileJava output.",
                       "Native assertion source maintenance is separately proved by v21; native execution must be reported separately."]})
    if args.audit_only:
        report.update(result="PASS_PATCH_PROVENANCE_ONLY",execution="NOT_REQUESTED; NO_COMPILER_JVM_GRADLE_OR_SERVER_STARTED")
        return
    helper = runpy.run_path(str(project / V18))
    main_root=(args.main_classes or project/"build/classes/java/main").resolve()
    test_root=(args.test_classes or project/"build/classes/java/test").resolve()
    resources=(args.resources or project/"build/resources/main").resolve()
    test_resources=(args.test_resources or project/"build/resources/test").resolve()
    cp_file=(args.classpath_file or project/"build/vfx2/native-classpath.txt").resolve()
    java=(args.java or project/".tools/jdk-21.0.12.1+1/bin/java").resolve()
    javac=(args.javac or java.with_name("javac")).resolve()
    require(java.is_file() and javac.is_file(),"EXISTING_JDK_REQUIRED")
    dependency=read(cp_file).decode().strip()
    require(dependency and all(Path(p).exists() for p in dependency.split(os.pathsep)),"CURRENT_CLASSPATH_MISSING")
    roots={"production_classes":(main_root,".class"),"test_classes":(test_root,".class"),"main_resources":(resources,None),"test_resources":(test_resources,None)}
    before={name:helper["inventory"](root,suffix) for name,(root,suffix) in roots.items()}
    require(NEW_TEST.replace(".","/")+".class" in before["test_classes"],"ACTUAL_NEW452_LEARNING_TEST_CLASS_MISSING")
    report["actual_compiled_inputs"]={name:{"directory":str(roots[name][0]),"files":len(value),"inventory_sha256":helper["inventory_sha"](value)} for name,value in before.items()}
    report["actual_production_class_hashes"]=before["production_classes"]
    work=(args.work_dir or args.report.with_name(args.report.stem+"-work")).resolve(); work.mkdir(parents=True,exist_ok=False)
    report["work_directory"]=str(work)
    baseline_cp=os.pathsep.join(map(str,(main_root,test_root,resources,test_resources)))+os.pathsep+dependency
    report["new452_learning_gate"]=helper["run_class"](java,baseline_cp,NEW_TEST,project,work)
    require(report["new452_learning_gate"]["result"]=="PASS","NEW452_LEARNING_GATE_FAILED")
    source_root=work/"sources";classes=work/"classes";source_root.mkdir();classes.mkdir();paths=[]
    for path,content in generated.items():
        target=source_root/Path(path).relative_to("src/test/java");target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content);paths.append(target)
    command=[str(javac),"-J-Xmx384m","-proc:none","-encoding","UTF-8","-sourcepath",str(source_root),"-cp",baseline_cp,"-d",str(classes)]+list(map(str,paths))
    code,output=helper["invoke"](command,project,work/"compile-companions.log")
    report["compilation"]={"exit_code":code,"source_count":len(paths),"production_recompiled":False,"log":str(work/"compile-companions.log"),"log_sha256":sha(output)}
    require(code==0,"COMPANION_COMPILATION_FAILED "+output.decode(errors="replace")[-4000:])
    outputs=helper["inventory"](classes,".class");prefixes=[s["main_class"].replace(".","/") for s in contract["files"].values()]
    require(all(any(p==prefix+".class" or p.startswith(prefix+"$") for prefix in prefixes) for p in outputs),"UNAPPROVED_COMPILED_OUTPUT")
    require(all(prefix+".class" in outputs for prefix in prefixes),"MISSING_COMPANION_CLASS")
    report["generated_class_hashes"]=outputs
    report["executions"]=[helper["run_class"](java,str(classes)+os.pathsep+baseline_cp,s["main_class"],project,work) for s in contract["files"].values()]
    for name,(root,suffix) in roots.items(): require(helper["inventory"](root,suffix)==before[name],"ACTUAL_COMPILED_INPUT_CHANGED "+name)
    for path,expected in inputs.items():pinned(read(project/path),expected,"SOURCE_INPUT_CHANGED_DURING_RUN "+path)
    for path,content in generated.items():require(read(source_root/Path(path).relative_to("src/test/java"))==content,"GENERATED_SOURCE_CHANGED")
    require(helper["inventory"](classes,".class")==outputs,"GENERATED_CLASS_CHANGED")
    report["total_companion_checks"]=sum(row["checks"] or 0 for row in report["executions"])
    failures=[row["main_class"] for row in report["executions"] if row["result"]!="PASS"]
    require(not failures,"CURRENT_COMPANION_FAILURES "+", ".join(failures))
    report["result"]="PASS_CURRENT_LEARNING_COMPANION_ONLY"


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project",type=Path,default=Path(__file__).resolve().parents[1]);parser.add_argument("--report",type=Path,required=True)
    parser.add_argument("--audit-only",action="store_true")
    for name in ("work-dir","main-classes","test-classes","resources","test-resources","classpath-file","java","javac"):parser.add_argument("--"+name,type=Path)
    args=parser.parse_args();args.report=args.report.resolve();args.report.parent.mkdir(parents=True,exist_ok=True)
    with args.report.open("x") as output:
        report={"result":"FAIL","contract":"autonomous-vector-circle-learning-v20"}
        try:execute(args.project.resolve(),args,report)
        except Exception as error:report.update(result="FAIL",reason=str(error))
        json.dump(report,output,indent=2);output.write("\n")
    print(report["result"],args.report)
    if report["result"]=="FAIL":print(report["reason"],file=sys.stderr);return 1
    return 0

if __name__=="__main__":raise SystemExit(main())
