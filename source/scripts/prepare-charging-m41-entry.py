#!/usr/bin/env python3
"""Stage exact M41 Charging entry for the separate native Charging companion.

Run only after compileJava. This compiles the immutable original entry, never a
replacement current subject. The native run must set:
  -Dacademy.charging.companion.baseline=<absolute output directory>
Missing or changed staging is a native test failure, never a skipped comparison.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile


ZIP_SHA = "8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b"
MEMBER = "src/main/java/cn/academy/port/skill/CurrentCharging.java"
SOURCE_SHA = "ea6c88aaa8a6abe6388c93053aa4315d0e839d94ad6e73c5f4278c5a1aaa108d"
SUBJECT = "cn.academy.port.skill.CurrentCharging"
EXPECTED_CLASSES = {SUBJECT, SUBJECT + "$Hold"}
FAMILIES = (
    SUBJECT,
    "cn.academy.port.highpower.HighPowerCharging",
    "cn.academy.port.highpower.HighPowerChargingEnergy",
    "cn.academy.port.highpower.HighPowerChargingRules",
    "cn.academy.port.core.CurrentChargingSession",
    "cn.academy.port.core.AbilityProgress",
    "cn.academy.port.AbilityConsumption",
    "cn.academy.port.skill.ChargingEnergy",
    "cn.academy.port.core.LegacySingleKeyServerIdentity",
    "cn.academy.port.core.LegacySingleKeyProtocol",
    "cn.academy.port.core.LegacySingleKeyOwnerEpoch",
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def demand(condition, message):
    if not condition:
        raise SystemExit(message)


def prop(value):
    return str(value).replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("=", "\\=").replace(":", "\\:")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--original-zip", type=Path, required=True)
    parser.add_argument("--classpath-file", type=Path, default=Path("build/vfx2/native-classpath.txt"))
    parser.add_argument("--main-classes", type=Path, default=Path("build/classes/java/main"))
    parser.add_argument("--output", type=Path, default=Path("build/current-geometry-proof/charging-entry"))
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    project = args.project.resolve()
    resolve = lambda path: (path if path.is_absolute() else project / path).resolve()
    original_zip, main_classes, output = resolve(args.original_zip), resolve(args.main_classes), resolve(args.output)
    demand(output.is_relative_to(project / "build"), "Staging output must stay below this project's build directory")
    fixture_root = project / "src/test/resources/current-geometry-proof/charging-entry"
    manifest = json.loads((fixture_root / "manifest.json").read_text())
    demand(manifest["schema"] == 1 and manifest["original_zip_sha256"] == ZIP_SHA
           and manifest["member"] == MEMBER and manifest["member_sha256"] == SOURCE_SHA
           and set(manifest["compiled_original_classes"]) == EXPECTED_CLASSES, "Wrong closed M41 entry manifest")
    zip_bytes = original_zip.read_bytes()
    demand(digest(zip_bytes) == ZIP_SHA, "Original M41 ZIP hash mismatch")
    with zipfile.ZipFile(original_zip) as archive:
        source = archive.read(MEMBER)
    demand(digest(source) == SOURCE_SHA, "Original M41 CurrentCharging member hash mismatch")
    demand((fixture_root / manifest["fixture"]).read_bytes() == source, "Review fixture is not exact M41 source bytes")
    classpath_file = resolve(args.classpath_file)
    entries = [Path(entry).resolve() for entry in classpath_file.read_text().strip().split(os.pathsep) if entry]
    demand(entries and all(entry.exists() for entry in entries), "Incomplete existing native compile classpath")
    admitted = {}
    for family in FAMILIES:
        relative = Path(family.replace(".", "/"))
        primary = main_classes / relative.with_suffix(".class")
        demand(primary.is_file(), f"Missing actual compileJava output: {primary}")
        files = [primary, *sorted(primary.parent.glob(primary.stem + "$*.class"))]
        for file in files:
            name = file.relative_to(main_classes).as_posix().removesuffix(".class").replace("/", ".")
            admitted[name] = (file, digest(file.read_bytes()))
    current_source = project / MEMBER
    source_before = digest(current_source.read_bytes())
    if output.exists():
        shutil.rmtree(output)
    staged_source = output / "source/cn/academy/port/skill/CurrentCharging.java"
    staged_source.parent.mkdir(parents=True)
    staged_source.write_bytes(source)
    classes = output / "classes"
    classes.mkdir()
    classpath = os.pathsep.join(str(path) for path in [main_classes, *entries])
    command = [args.javac, "--release", "21", "-proc:none", "-implicit:none", "-encoding", "UTF-8",
               "-classpath", classpath, "-d", str(classes), str(staged_source)]
    subprocess.run(command, check=True)
    actual = {path.relative_to(classes).as_posix().removesuffix(".class").replace("/", "."): path
              for path in classes.rglob("*") if path.is_file()}
    demand(set(actual) == EXPECTED_CLASSES, "Original compiler emitted an undeclared class; current subjects must never be recompiled")
    demand(digest(original_zip.read_bytes()) == ZIP_SHA and staged_source.read_bytes() == source,
           "Original proof inputs changed during staging")
    demand(digest(current_source.read_bytes()) == source_before, "Actual current entry source changed during staging")
    for name, (file, expected) in admitted.items():
        demand(digest(file.read_bytes()) == expected, f"Actual compileJava bytes changed during staging: {name}")
    evidence = {
        "original.zip": original_zip, "original.zip.sha256": ZIP_SHA,
        "original.source.sha256": SOURCE_SHA, "current.main.classes": main_classes,
        "current.source.sha256": source_before, "current.class.count": len(admitted),
        "scope": "M41-entry-only/shared-current-native-dependencies",
        "classpath.file": classpath_file, "classpath.file.sha256": digest(classpath_file.read_bytes()),
    }
    for name, file in sorted(actual.items()):
        evidence["original.class." + name] = digest(file.read_bytes())
    for index, (name, (_, sha)) in enumerate(sorted(admitted.items())):
        evidence[f"current.class.{index}.name"] = name
        evidence[f"current.class.{index}.sha256"] = sha
    (output / "provenance.properties").write_text("\n".join(f"{prop(key)}={prop(value)}" for key, value in evidence.items()) + "\n", encoding="utf-8")
    print(json.dumps({"result": "STAGED", "original_entry_source_sha256": SOURCE_SHA,
                      "original_compiled_classes": len(actual), "current_compiled_classes_admitted": len(admitted),
                      "baseline": str(output), "native_jvm_property": f"-Dacademy.charging.companion.baseline={output}",
                      "scope": evidence["scope"], "native_tests_run": False}, indent=2))


if __name__ == "__main__":
    main()
