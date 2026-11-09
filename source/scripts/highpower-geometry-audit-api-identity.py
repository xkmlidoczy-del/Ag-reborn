#!/usr/bin/env python3
"""Read-only, limited API byte-identity audit; NOT a geometry-test replacement.

Run from any directory with Python 3. No Gradle, compilation, cache writes, or
test/fixture rewrites occur. JSON goes only to stdout. Exit 0 means the limited
identity audit succeeded, even when the separately reported archive guard fails.

Trust root: the immutable original M41 source ZIP's independently recorded SHA.
Its original tests anchor the fixture indexes, which anchor the API fingerprints.
This proves identity of the explicitly fingerprinted class files only. It does
not prove the archive's other classes/transitive dependencies, class-loader
origins, renderer execution, geometry outputs, GPU visuals, or a full check pass.
"""

import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import sys
import zipfile


ORIGINAL_SOURCE_SHA256 = "8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b"
ORIGINAL_MERGED_SHA256 = "59a14efc6e5c163fe7e1374b3cf66ace00dd20f1b847ae18dd5a08aee186d0c7"
CAMERAS = {"charging": "Charging", "meltdowner": "Meltdowner", "thunderbolt": "ThunderBolt"}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def properties(data):
    result = {}
    for line in data.decode("utf-8").splitlines():
        if not line.strip() or line.startswith(("#", "!")):
            continue
        key, value = line.split("=", 1)
        require(key not in result, "Duplicate guard key: " + key)
        result[key] = value
    return result


def archive_member(archive, member):
    require(archive.namelist().count(member) == 1, "Missing/ambiguous archive member: " + member)
    return archive.read(member)


def audit(args):
    project = args.project.resolve()
    original = (args.original_source or project.parents[1] / "00_original_m41" /
                "academycraft-m41-meltdowner-camera-source.zip").resolve()
    snapshots = {}

    def read(path):
        path = path.resolve()
        data = path.read_bytes()
        digest = sha(data)
        require(path not in snapshots or snapshots[path] == digest, "Input changed during audit: " + str(path))
        snapshots[path] = digest
        return data

    original_bytes = read(original)
    require(sha(original_bytes) == ORIGINAL_SOURCE_SHA256, "Original source ZIP does not match frozen M41 SHA-256")
    reference = zipfile.ZipFile(io.BytesIO(original_bytes))
    reference_api = {}
    evidence = []
    expected_joml = set()

    def unchanged(relative):
        frozen = archive_member(reference, relative)
        current = read(project / relative)
        require(current == frozen, "Current test/fixture differs from immutable M41: " + relative)
        return frozen

    for camera, name in CAMERAS.items():
        test = "src/test/java/cn/academy/port/client/Classic" + name + "GeometryClassResourcesTest.java"
        test_bytes = unchanged(test)
        unchanged("gradle/classic-" + camera + "-geometry-tests.gradle")
        match = re.findall(rb'INDEX_SHA = "([a-f0-9]{64})"', test_bytes)
        require(len(match) == 1, "Missing/ambiguous frozen INDEX_SHA: " + test)
        prefix = "src/test/resources/classic-oracles/" + camera + "-camera/"
        index = unchanged(prefix + "files.sha256")
        require(sha(index) == match[0].decode(), "Frozen fixture index does not match original test: " + camera)
        indexed = set()
        for line in index.decode().splitlines():
            if not line.strip():
                continue
            digest, relative = line.split("  ", 1)
            path = PurePosixPath(relative)
            require(re.fullmatch(r"[a-f0-9]{64}", digest) and not path.is_absolute() and
                    ".." not in path.parts and relative not in indexed,
                    "Unsafe/duplicate frozen fixture index entry")
            indexed.add(relative)
            require(sha(unchanged(prefix + relative)) == digest, "Frozen indexed fixture hash differs: " + relative)
        actual_files = {p.relative_to(project / prefix).as_posix() for p in (project / prefix).rglob("*") if p.is_file()}
        require(actual_files == indexed | {"files.sha256"}, "Current fixture inventory differs from closed index: " + camera)
        guards = properties(archive_member(reference, prefix + "guards.properties"))
        require(guards["minecraft.jar.sha256"] == ORIGINAL_MERGED_SHA256, "Unrecognized original merged archive anchor")
        expected_joml.add(guards["joml.jar.sha256"])
        for key, expected in guards.items():
            if key.startswith("api."):
                cls = key[4:]
                require(cls not in reference_api or reference_api[cls] == expected, "Conflicting original API class fingerprints: " + cls)
                reference_api[cls] = expected
        evidence.append({"camera": camera, "test_sha256": sha(test_bytes), "fixture_index_sha256": sha(index),
                         "unchanged_indexed_fixture_files": len(indexed)})

    require(len(expected_joml) == 1, "Conflicting original JOML archive fingerprints")
    merged_path = project / "build/moddev/artifacts/neoforge-21.1.252-merged.jar"
    merged_bytes = read(merged_path)
    if args.joml_jar:
        joml_path = args.joml_jar.resolve()
    else:
        classpath = read(project / "build/moddev/clientLegacyClasspath.txt").decode()
        candidates = [Path(line) for line in classpath.splitlines() if Path(line).name == "joml-1.10.5.jar"]
        require(len(candidates) == 1, "Need one selected JOML jar in clientLegacyClasspath.txt, or explicit --joml-jar")
        joml_path = candidates[0]
    joml_bytes = read(joml_path)
    require(sha(joml_bytes) == next(iter(expected_joml)), "Selected JOML archive differs from original frozen fingerprint")
    merged = zipfile.ZipFile(io.BytesIO(merged_bytes))
    joml = zipfile.ZipFile(io.BytesIO(joml_bytes))
    classes = []
    for cls, expected in sorted(reference_api.items()):
        is_joml = cls.startswith("org.joml.")
        actual = sha(archive_member(joml if is_joml else merged, cls.replace(".", "/") + ".class"))
        require(actual == expected, "API class differs from frozen original fingerprint: " + cls)
        classes.append({"class": cls, "sha256": actual, "archive": "joml" if is_joml else "merged", "byte_identity": "MATCH"})
    for path, digest in snapshots.items():
        require(sha(path.read_bytes()) == digest, "Input changed before audit completion: " + str(path))
    archive_matches = sha(merged_bytes) == ORIGINAL_MERGED_SHA256
    return {
        "audit": "FROZEN_API_CLASS_IDENTITY_ONLY", "class_identity_result": "PASS",
        "original_source_zip_sha256": ORIGINAL_SOURCE_SHA256,
        "fixtures_and_existing_tests": "UNCHANGED_FROM_IMMUTABLE_M41",
        "fixture_evidence": evidence,
        "original_merged_archive_sha256": ORIGINAL_MERGED_SHA256,
        "selected_merged_archive_sha256": sha(merged_bytes),
        "selected_joml_archive_sha256": sha(joml_bytes),
        "existing_whole_archive_guard": "MATCH" if archive_matches else "FAIL",
        "original_geometry_tasks": "NOT_EXECUTED_BY_THIS_AUDIT",
        "class_count": len(classes), "classes": classes,
        "input_stability": "ALL_READ_INPUTS_HASHED_AGAIN_UNCHANGED",
        "limitations": [
            "The three existing geometry tests and all guards remain unmodified; this audit cannot replace them or make full check pass.",
            "Byte identity is proven only for the listed fingerprinted API classes. No reference exists here for every other merged-archive entry or transitive dependency.",
            "A changed archive hash is not proof that only ZIP metadata changed. The reason for the whole-archive difference is not established.",
            "No current renderer execution, golden-vertex comparison, class-loader origin, gameplay, or GPU visual equivalence is established by this audit."
        ]
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--original-source", type=Path)
    parser.add_argument("--joml-jar", type=Path)
    args = parser.parse_args()
    try:
        result = audit(args)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        print(json.dumps({"audit": "FROZEN_API_CLASS_IDENTITY_ONLY", "class_identity_result": "FAIL", "error": str(error)}, indent=2))
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
