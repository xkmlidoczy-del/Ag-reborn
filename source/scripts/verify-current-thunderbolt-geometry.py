#!/usr/bin/env python3
"""Execute the independently versioned CURRENT ThunderBolt CPU geometry contract.

This is NOT historical whole-archive admission and cannot make the old check pass.
No production source is compiled, restored, edited, or substituted. The positive
subject is build/classes/java/main. All executable negative controls are required.
Only the unchanged frozen view host/harness and the new test drivers are compiled.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import struct
import sys
import tempfile
import zipfile

CONTRACT = "current-thunderbolt-cpu-geometry-v1"
CONTRACT_SHA256 = "754d368a696a4683f3e676b5c00ef3a846f57e5c3293ea04d43fb18d8ba09597"
GOLDEN = "56d71d97bfb8b4a0203821f24edb2068620944a1d27906274e3aa5fb4f7394e9"
SUBJECT = "cn.academy.port.client.ClassicThunderBoltEffects"
HARNESS = "cn.academy.port.client.ThunderBoltCurrentMainGeometryHarness"
FIXTURE_PREFIX = "src/test/resources/classic-oracles/thunderbolt-camera"
SUPPORT = "src/test/resources/current-geometry-proof/thunderbolt"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, why):
    if not ok:
        raise ValueError(why)


def member(archive, name):
    require(archive.namelist().count(name) == 1, "Missing/duplicate archive entry: " + name)
    return archive.read(name)


def properties(data):
    out = {}
    for line in data.decode().splitlines():
        if line and not line.startswith(("#", "!")):
            key, value = line.split("=", 1)
            require(key not in out, "Duplicate property " + key)
            out[key] = value
    return out


def region(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for i in range(opening, len(source)):
        if source[i] == "{":
            depth += 1
        elif source[i] == "}":
            depth -= 1
            if depth == 0:
                return source[start:i+1].encode()
    raise ValueError("Incomplete protected method " + signature)


def production_references(data):
    """Read Class constants and descriptor UTF8s without resolving additional classes."""
    require(data[:4] == b"\xca\xfe\xba\xbe", "Bad class header")
    count = struct.unpack_from(">H", data, 8)[0]
    pos, index, strings = 10, 1, []
    lengths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
               11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            strings.append(data[pos:pos+length].decode("utf-8", errors="replace"))
            pos += length
        else:
            require(tag in lengths, "Unknown constant-pool tag")
            pos += lengths[tag]
            if tag in (5, 6):
                index += 1
        index += 1
    return sorted({x.replace("/", ".") for text in strings
                   for x in re.findall(r"cn/academy/port/[A-Za-z0-9_$/]+", text)})


def run(command, log, *, expect=0):
    # Prevent inherited JVM flags/agents/classpaths from adding unadmitted code.
    env = dict(os.environ)
    for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "CLASSPATH"):
        env.pop(key, None)
    proc = subprocess.run(list(map(str, command)), capture_output=True, text=True, env=env)
    text = proc.stdout + proc.stderr
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(text)
    if expect == 0:
        require(proc.returncode == 0, "Command failed: " + str(log) + "\n" + text[-5000:])
    elif expect == "failure":
        require(proc.returncode != 0, "Negative control unexpectedly passed: " + str(log))
    return proc.returncode, text


def prove(args):
    project = args.project.resolve(strict=True)
    base = project / SUPPORT
    report = args.report.resolve()
    require(not report.exists(), "Use a new report directory, so old success evidence cannot survive a failed run: " + str(report))
    report.mkdir(parents=True)
    snapshots = {}
    evidence = {"contract": CONTRACT, "result": "FAIL", "report": str(report), "negative_controls": []}

    def read(path):
        path = path.resolve(strict=True)
        data = path.read_bytes()
        digest = sha(data)
        require(path not in snapshots or snapshots[path] == digest, "Input changed: " + str(path))
        snapshots[path] = digest
        return data

    def check_stable():
        for path, digest in snapshots.items():
            require(sha(path.read_bytes()) == digest, "Input changed before proof completion: " + str(path))

    try:
        contract_bytes = read(base / "contract-v1.json")
        require(sha(contract_bytes) == CONTRACT_SHA256, "Current contract changed; needs a new reviewed version")
        contract = json.loads(contract_bytes)
        require(contract["contract"] == CONTRACT, "Unknown contract version")
        evidence["contract_sha256"] = sha(contract_bytes)
        for relative, expected in contract["proof_sources"].items():
            require(sha(read(project / relative)) == expected, "Current proof component changed: " + relative)
        actual_main = (project / contract["main_root"]).resolve(strict=True)
        require(actual_main == (project / "build/classes/java/main").resolve(strict=True), "Not actual compileJava output")
        originals = args.original_source.resolve(strict=True)
        java_home = args.java_home.resolve(strict=True)
        for relative, expected in contract["jdk_files"].items():
            require(sha(read(java_home / relative)) == expected, "Wrong pinned JDK file: " + relative)
        java, javac = java_home / "bin/java", java_home / "bin/javac"
        paths = {"main": actual_main}
        for origin, detail in contract["origins"].items():
            path = detail["path"]
            paths[origin] = ((args.cache_dir / path[6:]) if path.startswith("cache/") else (project / path)).resolve(strict=True)
        archives = {origin: zipfile.ZipFile(path) for origin, path in paths.items() if origin != "main"}
        for path in paths.values():
            if path.is_file():
                read(path)  # stability receipt, never a new historical whole-archive pin
        # Invoke the existing immutable-fixture audit, with its independent original ZIP trust root.
        audit_script = project / "scripts/highpower-geometry-audit-api-identity.py"
        read(audit_script); read(originals)
        _, audit_log = run([sys.executable, audit_script, "--project", project, "--original-source", originals,
                            "--joml-jar", paths["joml-1.10.5.jar"]], report / "historical-api-identity.json")
        historical = json.loads(audit_log)
        require(historical["class_count"] == 11 and historical["class_identity_result"] == "PASS", "Frozen eleven-class identity failed")
        require(historical["existing_whole_archive_guard"] == "FAIL", "Contract v1 expects the separately recorded historical archive mismatch")
        evidence["historical_archive_admission"] = "FAIL_UNCHANGED"
        evidence["historical_whole_archive_expected"] = historical["original_merged_archive_sha256"]
        evidence["current_merged_archive_observed"] = historical["selected_merged_archive_sha256"]
        frozen_api = {x["class"]: x["sha256"] for x in historical["classes"]}
        # Keep all existing tests, Gradle runners, and closed fixtures under before/after receipts.
        for camera, name in (("charging", "Charging"), ("meltdowner", "Meltdowner"), ("thunderbolt", "ThunderBolt")):
            read(project / f"src/test/java/cn/academy/port/client/Classic{name}GeometryClassResourcesTest.java")
            read(project / f"gradle/classic-{camera}-geometry-tests.gradle")
            for path in (project / f"src/test/resources/classic-oracles/{camera}-camera").rglob("*"):
                if path.is_file():
                    read(path)
        guards = properties(read(project / FIXTURE_PREFIX / "guards.properties"))
        require(guards["near.sha256"] == GOLDEN, "Frozen near digest changed")
        require(sha(read(paths["joml-1.10.5.jar"])) == guards["joml.jar.sha256"], "Original JOML archive mismatch")
        for relative, digest in contract["production_sources"].items():
            require(sha(read(project / relative)) == digest, "Current production source changed: " + relative)
        effects = read(project / "src/main/java/cn/academy/port/client/ClassicThunderBoltEffects.java").decode()
        for i in range(int(guards["method.count"])):
            require(sha(region(effects, guards[f"method.{i}.signature"])) == guards[f"method.{i}.sha256"], "Original protected method changed: " + str(i))
        for key, signature in (("candidate.event.sha256", "public static void onRenderLevel("), ("candidate.render.sha256", "private static void render(")):
            require(sha(region(effects, signature)) == guards[key], "Original camera-relative source method changed: " + signature)
        for i in range(int(guards["dependency.count"])):
            require(sha(read(project / guards[f"dependency.{i}.path"])) == guards[f"dependency.{i}.sha256"], "Original math/timeline source changed")
        for i in range(int(guards["asset.count"])):
            for directory in ("src/main/resources", "build/resources/main"):
                asset = project / directory / guards[f"asset.{i}.path"]
                require(sha(read(asset)) == guards[f"asset.{i}.sha256"], "Original asset changed: " + str(asset))
        for relative in ("scripts/verify-current-thunderbolt-geometry.py", SUPPORT + "/CurrentThunderBoltGeometryProof.java", SUPPORT + "/MutateThunderBolt.java"):
            read(project / relative)
        subject_names = {n for n, detail in contract["classes"].items() if detail["origin"] == "main"}
        require(len(subject_names) == 10, "Expected ten explicit production subject classes")
        actual_inventory = {"cn.academy.port.client." + f.stem for f in (actual_main / "cn/academy/port/client").glob("*.class")
                            if re.fullmatch(r"(?:ClassicThunderBoltEffects|ClassicThunderBoltTimeline|ClassicArcGeometry)(?:\$[^/]+)?", f.stem)}
        require(actual_inventory == subject_names, "Unknown/missing production class in actual compileJava inventory")
        bindings = []
        for name, detail in contract["classes"].items():
            origin, expected = detail["origin"], detail["sha256"]
            relative = name.replace(".", "/") + ".class"
            if origin == "main":
                data = read(actual_main / relative)
            else:
                data = member(archives[origin], detail["member"])
            require(sha(data) == expected, "Stale/wrong class: " + name)
            if name in frozen_api:
                require(expected == frozen_api[name] and detail["anchor"] == "original-m41-api", "Original API anchor changed: " + name)
            matches = [o for o, z in archives.items() if relative in z.namelist()]
            if (actual_main / relative).is_file():
                matches.append("main")
            require(matches == [origin], "Duplicate/wrong class origins for " + name + ": " + repr(matches))
            if origin == "main":
                require(production_references(data) == contract["production_graph"][name], "Changed production dependency graph: " + name)
            bindings.append("\t".join(("class", name, str(paths[origin]), expected, detail["member"])))
        require(frozen_api.keys() <= contract["classes"].keys(), "Missing original API pin")
        for resource in contract["resources"]:
            require(sha(member(archives[resource["origin"]], resource["name"])) == resource["sha256"], "Changed declared host resource: " + resource["name"])
            bindings.append("\t".join(("resource", resource["name"], str(paths[resource["origin"]]), resource["sha256"], resource["name"])))
        for absent in contract["optional_absent_classes"]:
            relative = absent.replace(".", "/") + ".class"
            require(not any(relative in z.namelist() for z in archives.values()), "Unexpected optional dependency: " + absent)
            bindings.append("absent\t" + absent)
        for z in archives.values():
            z.close()
        with tempfile.TemporaryDirectory(prefix="current-thunderbolt-proof-") as temporary:
            temp = Path(temporary)
            fixtures, drivers = temp / "fixtures", temp / "drivers"
            fixtures.mkdir(); drivers.mkdir()
            sources = []
            for folder in ("hosts", "harness"):
                for source in (project / FIXTURE_PREFIX / folder).rglob("*.java.txt"):
                    target = temp / source.name[:-4]
                    target.write_bytes(read(source)); sources.append(target)
            require(len(sources) == 2, "Changed frozen fixture source inventory")
            classpath = os.pathsep.join(map(str, [actual_main] + [p for k, p in paths.items() if k != "main"]))
            run([javac, "-proc:none", "-encoding", "UTF-8", "-sourcepath", "", "-cp", classpath, "-d", fixtures, *sources], report / "compile-frozen-fixtures.log")
            runner = base / "CurrentThunderBoltGeometryProof.java"
            read(runner)
            run([javac, "-proc:none", "-encoding", "UTF-8", "-d", drivers, runner], report / "compile-current-runner.log")
            for name in (HARNESS, "net.minecraft.client.Minecraft"):
                relative = name.replace(".", "/") + ".class"
                bindings.append("\t".join(("class", name, str(fixtures), sha((fixtures / relative).read_bytes()), relative)))
            require({p.relative_to(fixtures).as_posix() for p in fixtures.rglob("*.class")} == {n.replace(".", "/") + ".class" for n in (HARNESS, "net.minecraft.client.Minecraft")}, "Unexpected fixture class")
            pins = temp / "pins.tsv"
            pins.write_text("\n".join(bindings) + "\n")
            command = [java, "-ea", "-Xmx256m", "-Djava.awt.headless=true", "-cp", drivers, "CurrentThunderBoltGeometryProof"]

            def execute(label, rows, mode="ACTUAL_MAIN", probe="-", failure=None, admission_pins=None):
                config = temp / (label + ".tsv")
                config.write_text("\n".join(rows) + "\n")
                out = report / label
                rc, log = run([*command, config, actual_main, fixtures, out, mode, probe, admission_pins or pins], out / "execution.log", expect="failure" if failure else 0)
                if failure:
                    require(failure in log, "Wrong negative-control failure: " + label + "\n" + log[-5000:])
                    require("MUTANT_SURVIVED" not in log, "Mutation escaped the geometry oracle: " + label)
                    control = {"name": label, "result": "DETECTED", "expected_failure": failure, "exit": rc}
                    if label in ("mutation-coordinate", "mutation-uv", "mutation-camera"):
                        produced = (out / "near-vertices.bin").read_bytes()
                        require(len(produced) == 174720 and "cases=48 nearVertices=4848 coordinateComparisons=58176" in log,
                                "Behavioral mutant did not complete the frozen matrix: " + label)
                        digest = sha(produced)
                        require((digest == GOLDEN) == (label == "mutation-camera"), "Wrong mutant near-output effect: " + label)
                        control.update(complete_frozen_matrix=True, near_bytes=len(produced), near_sha256=digest)
                    evidence["negative_controls"].append(control)
                return log

            positive = execute("actual-main", bindings)
            require(sha((report / "actual-main/near-vertices.bin").read_bytes()) == GOLDEN, "Positive near output changed")
            # Bytecode mutations avoid source recompilation and leave the positive subject untouched.
            asm = contract["mutation_tool"]
            asm_path = (args.cache_dir / asm["path"]).resolve(strict=True)
            require(sha(read(asm_path)) == asm["sha256"], "Wrong mutation-tool archive")
            mutator = base / "MutateThunderBolt.java"
            read(mutator)
            run([javac, "-proc:none", "-encoding", "UTF-8", "-cp", asm_path, "-d", drivers, mutator], report / "compile-mutator.log")
            effect_path = actual_main / (SUBJECT.replace(".", "/") + ".class")
            mutant_paths = {}
            for mutation, expected in (("coordinate", "NEAR_GOLDEN_MISMATCH"), ("camera", "THUNDERBOLT_FAR_ORIGIN_FAILURE"), ("uv", "NEAR_GOLDEN_MISMATCH"), ("rng", "render consumed effect RNG")):
                root = temp / ("mutant-" + mutation)
                target = root / effect_path.relative_to(actual_main)
                run([java, "-Xmx256m", "-cp", os.pathsep.join((str(drivers), str(asm_path))), "MutateThunderBolt", effect_path, target, mutation], report / ("make-" + mutation + ".log"))
                mutant_paths[mutation] = root
                changed = ["\t".join(("class", SUBJECT, str(root), sha(target.read_bytes()), effect_path.relative_to(actual_main).as_posix())) if row.startswith("class\t" + SUBJECT + "\t") else row for row in bindings]
                execute("mutation-" + mutation, changed, "NEGATIVE_CONTROL:" + mutation, failure=expected)
            # A stale bytecode copy cannot pass the original digest even in an explicitly
            # isolated negative-origin fixture. Both origin maps agree; only bytes differ.
            stale_rows = [row.replace(str(actual_main), str(mutant_paths["coordinate"])) if row.startswith("class\t" + SUBJECT + "\t") else row for row in bindings]
            stale_pins = temp / "stale-byte-pins.tsv"
            stale_pins.write_text("\n".join(stale_rows) + "\n")
            execute("stale-class-bytes", stale_rows, "NEGATIVE_CONTROL:stale-class", failure="STALE_OR_WRONG_CLASS " + SUBJECT, admission_pins=stale_pins)
            # The same altered class is rejected at admission when mutation authority is absent.
            changed = [row.replace(str(actual_main), str(mutant_paths["coordinate"])) if row.startswith("class\t" + SUBJECT + "\t") else row for row in bindings]
            execute("wrong-subject-origin", changed, failure="WRONG_DEPENDENCY_ORIGIN")
            # Keep the canonical origin but poison its expected fingerprint: stale content cannot be blessed.
            stale = ["\t".join(row.split("\t")[:3] + ["0"*64] + row.split("\t")[4:]) if row.startswith("class\t" + SUBJECT + "\t") else row for row in bindings]
            execute("stale-class-pin", stale, failure="WRONG_DEPENDENCY_PIN")
            duplicate = bindings + [next(row for row in bindings if row.startswith("class\t" + SUBJECT + "\t"))]
            execute("duplicate-subject", duplicate, failure="DUPLICATE_CLASS_ADMISSION")
            execute("unknown-production-dependency", bindings, probe="cn.academy.port.client.ClassicRenderTypes", failure="UNKNOWN_DEPENDENCY cn.academy.port.client.ClassicRenderTypes")
            execute("unknown-host-dependency", bindings, probe="net.minecraft.world.phys.AABB", failure="UNKNOWN_DEPENDENCY net.minecraft.world.phys.AABB")
            api = "net.minecraft.world.phys.Vec3"
            # Identical API bytes at an undeclared origin must fail, even though its class hash matches.
            false_origin = temp / "wrong-api-origin"
            api_file = false_origin / (api.replace(".", "/") + ".class")
            api_file.parent.mkdir(parents=True)
            with zipfile.ZipFile(paths["neoforge-21.1.252-merged.jar"]) as z:
                api_file.write_bytes(member(z, api.replace(".", "/") + ".class"))
            wrong_origin = ["\t".join(("class", api, str(false_origin), frozen_api[api], api.replace(".", "/") + ".class")) if row.startswith("class\t" + api + "\t") else row for row in bindings]
            execute("wrong-api-origin", wrong_origin, failure="WRONG_DEPENDENCY_ORIGIN " + api)
            evidence["renderer_summary"] = next(line for line in positive.splitlines() if line.startswith("ThunderBoltCurrentMainGeometryHarness:"))
            evidence["loaded_classes"] = len((report / "actual-main/loaded-classes.tsv").read_text().splitlines())
        check_stable()
        evidence.update(result="PASS_CURRENT_CONTRACT_ONLY", near_sha256=GOLDEN, original_api_classes=11,
                        production_classes=10, declared_classes=len(contract["classes"]), input_stability="ALL_READ_INPUTS_UNCHANGED",
                        limits=["Historical archive admission still fails; this is not the old task or a full-check pass.",
                                "Additional declared host classes/resources have current-version repeatability fingerprints only; their transitive behavior is not proven historically equivalent.",
                                "Only the frozen 48-case third-person/null-player CPU scene matrix executes; no world/bootstrap, first-person, event, GPU, native client, sound, or original-engine pixel parity is claimed.",
                                "Class definitions from unlisted production and host dependencies are rejected. The production graph outside the admitted ten-class CPU slice is not executed."])
        (report / "input-receipts.sha256").write_text("".join(f"{v}  {k}\n" for k, v in sorted(snapshots.items())))
        (report / "evidence.json").write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
        (report / "PASS_CURRENT_CONTRACT_ONLY").write_text(CONTRACT + "\n")
        return evidence
    except Exception as error:
        evidence["error"] = str(error)
        (report / "failure.json").write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n")
        raise


def main():
    project = Path(__file__).resolve().parents[1]
    workspace = project.parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=project)
    parser.add_argument("--original-source", type=Path, default=workspace / "aca-original-m41/academycraft-m41-meltdowner-camera-source.zip")
    parser.add_argument("--java-home", type=Path, default=workspace / "aca-tools/jdk-21.0.12.1+1")
    parser.add_argument("--cache-dir", type=Path, default=workspace / "aca-gradle-cache/caches")
    parser.add_argument("--report", type=Path, required=True, help="New report directory; existing directories are never reused")
    args = parser.parse_args()
    try:
        result = prove(args)
    except Exception as error:
        print(json.dumps({"contract": CONTRACT, "result": "FAIL", "error": str(error)}, indent=2))
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
