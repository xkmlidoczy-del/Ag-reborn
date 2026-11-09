#!/usr/bin/env python3
"""Read-only final ACA JAR audit; never invokes Gradle, Java, or Minecraft.

Requires the actual client compiled snapshot and source-freeze JSON. Paths in
those manifests are relative to source/build/ and source/ respectively. The
checkout is located from this script, not from the caller's working directory.
Exit 0 means every recorded check passed; exit 1 writes the concrete failures.
This proves package identity/integrity, not gameplay or user visual acceptance.
"""
import argparse
import collections
import datetime
import fnmatch
import hashlib
import json
import pathlib
import re
import struct
import sys
import zipfile


SOURCE = pathlib.Path(__file__).resolve().parents[1]
EXCLUDES = (
    "cn/academy/port/gametest/**",
    "cn/academy/port/qa/**",
    "cn/academy/port/client/MagneticDomainQaFixture*.class",
    "cn/academy/port/highpower/ZeroGapArmorQaFixture*.class",
    "cn/academy/port/highpower/VectorParryQaObserver*.class",
)
METADATA_EXCLUDES = ("**/._*", "**/.DS_Store")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def metadata(name):
    return any(p.startswith("._") or p == ".DS_Store" or p == "__MACOSX"
               for p in pathlib.PurePosixPath(name).parts)


def excluded(name):
    return any(fnmatch.fnmatchcase(name, p) for p in EXCLUDES) or metadata(name)


def load_json(data):
    def unique(pairs):
        out = {}
        for key, value in pairs:
            if key in out:
                raise ValueError("duplicate JSON key: " + key)
            out[key] = value
        return out
    return json.loads(data, object_pairs_hook=unique)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ("jar", "snapshot", "source-freeze", "out"):
        parser.add_argument("--" + key, required=True, type=pathlib.Path)
    args = parser.parse_args()
    failures, checks = [], []
    report = {
        "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "scope": "read_only_package_identity_not_gameplay_or_user_acceptance",
        "frozenSourceScope": "src/main, src/test, src/showcaseQa, gradle and three root build files; unused historical src/socketQa is outside this compiled/frozen source scope",
        "sourceRoot": str(SOURCE), "inputs": {}, "checks": checks,
        "failures": failures,
    }

    def check(ok, label, details=None):
        checks.append({"check": label, "passed": bool(ok), "details": details})
        if not ok:
            failures.append({"check": label, "details": details})

    def compare_sets(actual, expected, label):
        check(actual == expected, label,
              {"actualCount": len(actual), "expectedCount": len(expected),
               "missing": sorted(expected - actual),
               "unexpected": sorted(actual - expected)})

    try:
        for key in ("jar", "snapshot", "source_freeze"):
            path = getattr(args, key).resolve()
            report["inputs"][key] = {"file": str(path), "sha256": sha(path.read_bytes())}
        snapshot = load_json(args.snapshot.read_bytes())["files"]
        freeze = load_json(args.source_freeze.read_bytes())["files"]
        check(bool(snapshot) and bool(freeze), "nonempty manifests")
        for label, mapping in (("snapshot", snapshot), ("source freeze", freeze)):
            bad = [n for n, h in mapping.items()
                   if pathlib.PurePosixPath(n).is_absolute() or ".." in pathlib.PurePosixPath(n).parts
                   or "\\" in n or not re.fullmatch("[0-9a-f]{64}", h)]
            check(not bad, label + " canonical paths and SHA256", bad)
            if bad:
                raise ValueError("unsafe manifest paths")

        drift = []
        for name, digest in freeze.items():
            p = SOURCE / name
            if not p.is_file() or sha(p.read_bytes()) != digest:
                drift.append(name)
        check(not drift, "all frozen source bytes unchanged", drift)
        # Detect additions/removals in the frozen source/build configuration roots.
        actual_source = {
            p.relative_to(SOURCE).as_posix()
            for root in (SOURCE / "src/main", SOURCE / "src/test", SOURCE / "src/showcaseQa", SOURCE / "gradle")
            for p in root.rglob("*") if p.is_file() and not metadata(p.name)
        } | {"build.gradle", "settings.gradle", "gradle.properties"}
        compare_sets(actual_source, set(freeze), "complete frozen source inventory")

        gradle = (SOURCE / "build.gradle").read_text()
        jar_block = re.search(r'tasks\.named\("jar"\)\s*\{(.*?)\n\}', gradle, re.S)
        patterns = tuple(re.findall(r'exclude\("([^"]+)"\)', jar_block.group(1))) if jar_block else ()
        check(patterns == EXCLUDES, "exact current jar exclusion contract", list(patterns))
        check("exclude('**/._*', '**/.DS_Store')" in gradle,
              "archive metadata exclusion contract")
        check(not any(metadata(n) for n in snapshot), "snapshot excludes filesystem metadata")

        expected_classes = {n.removeprefix("classes/java/main/"): h
                            for n, h in snapshot.items()
                            if n.startswith("classes/java/main/") and not excluded(n.removeprefix("classes/java/main/"))}
        expected_resources = {n.removeprefix("resources/main/"): h
                              for n, h in snapshot.items() if n.startswith("resources/main/")}
        check(bool(expected_classes) and bool(expected_resources), "nonempty production snapshot")
        check(all(n.endswith(".class") for n in expected_classes), "class output contains only class entries")
        current_classes = {p.relative_to(SOURCE / "build/classes/java/main").as_posix(): sha(p.read_bytes())
                           for p in (SOURCE / "build/classes/java/main").rglob("*")
                           if p.is_file() and not excluded(p.relative_to(SOURCE / "build/classes/java/main").as_posix())}
        current_resources = {p.relative_to(SOURCE / "build/resources/main").as_posix(): sha(p.read_bytes())
                             for p in (SOURCE / "build/resources/main").rglob("*")
                             if p.is_file() and not metadata(p.name)}
        for label, actual, expected in (("production classes", current_classes, expected_classes),
                                        ("processed resources", current_resources, expected_resources)):
            compare_sets(set(actual), set(expected), label + " current/snapshot inventory")
            mismatch = [n for n in expected if actual.get(n) != expected[n]]
            check(not mismatch, label + " current/snapshot exact bytes", mismatch)

        with zipfile.ZipFile(args.jar) as z:
            entries = z.infolist()
            dup = [n for n, count in collections.Counter(x.filename for x in entries).items() if count != 1]
            check(not dup, "no duplicate ZIP entries including directories", dup)
            bad = [x.filename for x in entries if metadata(x.filename) or "\\" in x.filename
                   or x.filename.startswith("/") or ".." in pathlib.PurePosixPath(x.filename).parts]
            check(not bad, "no metadata or unsafe ZIP paths", bad)
            check(z.testzip() is None, "all ZIP CRCs valid")
            files = {x.filename for x in entries if not x.is_dir()}
            bad = sorted(n for n in files if excluded(n))
            check(not bad, "no QA/gametest/three isolated fixture classes", bad)
            compare_sets({n for n in files if n.endswith(".class")}, set(expected_classes),
                         "JAR production class inventory equals actual client snapshot")
            for label, expected in (("production class", expected_classes), ("processed resource", expected_resources)):
                mismatch = [n for n, digest in expected.items()
                            if n not in files or sha(z.read(n)) != digest]
                check(not mismatch, "JAR " + label + " bytes equal snapshot and current output", mismatch)
            # Gradle's generated manifest is the sole non-processed resource.
            compare_sets(files, set(expected_classes) | set(expected_resources) | {"META-INF/MANIFEST.MF"},
                         "exact JAR entry inventory; generated manifest is sole addition")
            check(z.read("META-INF/MANIFEST.MF") == b"Manifest-Version: 1.0\r\n\r\n",
                  "default generated Gradle manifest unchanged")
            manifest = load_json(z.read("skill-icons-v2.json"))
            icons = manifest["icons"]
            identities, paths, hashes = set(), set(), set()
            icon_errors = []
            for icon in icons:
                identity = icon["category"] + ":" + icon["id"]
                namespace, texture = icon["texture"].split(":", 1)
                name = "assets/" + namespace + "/" + texture
                data = z.read(name)
                digest = sha(data)
                valid_png = (data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR"
                             and struct.unpack(">II", data[16:24]) == (32, 32) and data[25] in (4, 6))
                if (identity in identities or name in paths or digest in hashes
                        or digest != icon["sha256"] or not valid_png or not icon.get("original")):
                    icon_errors.append(identity)
                identities.add(identity); paths.add(name); hashes.add(digest)
            check(len(icons) == len(identities) == len(paths) == len(hashes) == 111
                  and not icon_errors and manifest.get("external_artwork") is False,
                  "111 distinct 32px alpha PNG icons match provenance hashes", icon_errors)
            compare_sets({n for n in files if n.startswith("assets/academy/textures/skill_icons_v2/")},
                         paths, "exact 111 icon texture inventory")
            for locale in ("zh_cn", "zh_tw", "en_us", "ja_jp"):
                data = load_json(z.read("assets/academy/lang/" + locale + ".json"))
                missing = []
                for icon in icons:
                    category = "generic" if icon["id"] in ("brain_course", "brain_course_advanced", "mind_course") else icon["category"]
                    for suffix in ("name", "desc"):
                        key = "ac.ability." + category + "." + icon["id"] + "." + suffix
                        if not isinstance(data.get(key), str) or not data[key].strip():
                            missing.append(key)
                check(not missing, locale + " valid unique-key JSON and all 111 skill names/descriptions", sorted(set(missing)))
        report["counts"] = {"frozenFiles": len(freeze), "productionClasses": len(expected_classes),
                            "processedResources": len(expected_resources), "icons": len(icons)}
    except Exception as exc:
        failures.append({"check": "audit completed without exception", "details": type(exc).__name__ + ": " + str(exc)})
    report["success"] = not failures
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"success": report["success"], "failures": len(failures), "out": str(args.out.resolve())}))
    return 0 if report["success"] else 1


if __name__ == "__main__":
    sys.exit(main())
