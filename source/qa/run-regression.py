#!/usr/bin/env python3
"""Run all four native groups serially and compare check's failed-task set without hiding either result."""
from pathlib import Path
import argparse
import json
import os
import re
import subprocess

source = Path(__file__).resolve().parents[1]
project = source.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check-only", action="store_true", help="retain already passing native-group results and rerun check/jar after test/client-only changes")
parser.add_argument("--out", type=Path, help="separate evidence directory for this run")
args = parser.parse_args()
out = args.out.resolve() if args.out else project / "qa/output"
out.mkdir(parents=True, exist_ok=True)
default_jdk = Path.home() / ".local/jdks/jdk-21.0.12.1+1/Contents/Home"
jdk = Path(os.environ.get("ACA_QA_JAVA_HOME", os.environ.get("JAVA_HOME", default_jdk)))
def java21(home):
    release = home / "release"
    return release.is_file() and re.search(r'^JAVA_VERSION="21(?:[.\"]|$)', release.read_text(), re.M) is not None
if not java21(jdk) and "ACA_QA_JAVA_HOME" not in os.environ:
    jdk = default_jdk
if not java21(jdk):
    raise SystemExit("Java 21 required; set ACA_QA_JAVA_HOME to its verified JDK home")
env = dict(os.environ, JAVA_HOME=str(jdk), GRADLE_USER_HOME=str(Path.home() / ".gradle-aca"))
env["PATH"] = str(project / ".venv/bin") + os.pathsep + str(jdk / "bin") + os.pathsep + env.get("PATH", "")
gradle = [str(jdk / "bin/java"), "-jar", "gradle/wrapper/gradle-wrapper.jar", "--no-daemon", "--max-workers=1", "-Dorg.gradle.jvmargs=-Xmx768m"]
def clean_appledouble():
    """macOS writes AppleDouble companions (._*) next to every file it creates on non-HFS volumes such as ExFAT.
    Gradle then fails to delete its compile stash and NeoForge's mod scanner rejects the fake ._*.class/._*.json files,
    so they are removed from the build and run directories before every Gradle step."""
    for root in (source / "build", source / "src") + tuple(p / "mods" for p in source.glob("run-*") if p.is_dir()):
        for junk in root.rglob("._*"):
            try:
                junk.unlink()
            except OSError:
                pass
groups = [
    "academy_highpower,academy_highpower_needle_capacity,academy_highpower_spatial_consent,academy_highpower_spatial_corridor,academy_highpower_borrowed,academy_highpower_array",
    "academy_highpower_vector,academy_braking_touch_runtime,academy_windblade_runtime,academy_inertial_anchor_runtime,academy_yaw_palm_runtime,academy_trajectory_return_runtime",
    "academy_circulation_relief_runtime,academy_circulation_listening_runtime,academy_vector_cage_runtime,academy_multi_axis_reflection,academy_wind_pressure_lift,academy_point_meltdown_runtime",
    "academy_remote_scatter_relay_runtime,academy_scatter_locator_runtime,academy_control_hold_runtime,academy_autonomous_vector_circle,academy_magnetic_domain_team,academy_new_skills_runtime,academy_construct_runtime,academy_cast_pose,academy_esper_guard",
]
if args.check_only:
    summary = json.loads((out / "summary.json").read_text())
    if len(summary["groups"]) != 4 or not all(g["success"] for g in summary["groups"]):
        raise SystemExit("check-only requires four already passing native groups")
else:
    summary = {"groups": [], "check": {}}
ok = True
for number, namespaces in ([] if args.check_only else enumerate(groups, 1)):
    log = out / f"group-{number}.log"
    with log.open("w") as stream:
        clean_appledouble()
        result = subprocess.run(gradle + ["-PhighPowerTestNamespaces=" + namespaces, "runHighPowerGameTest"], cwd=source, env=env, stdout=stream, stderr=subprocess.STDOUT)
    text = log.read_text()
    passed = re.search(r"All (\d+) required tests passed", text)
    success = result.returncode == 0 and passed is not None and "GAME TESTS COMPLETE" in text
    summary["groups"].append({"group": number, "exit": result.returncode, "passed": int(passed[1]) if passed else None, "success": success})
    ok &= success
    print(f"group {number}: {'PASS ' + passed[1] if success else 'FAIL (see ' + str(log) + ')'}", flush=True)

# Classic content (devices, original skills, wireless, wind generator...) runs on the gameTestServer configuration and is part of acceptance.
log = out / "classic.log"
with log.open("w") as stream:
    clean_appledouble()
    result = subprocess.run(gradle + ["runGameTestServer"], cwd=source, env=env, stdout=stream, stderr=subprocess.STDOUT)
text = log.read_text()
passed = re.search(r"All (\d+) required tests passed", text)
success = result.returncode == 0 and passed is not None and "GAME TESTS COMPLETE" in text
summary["classic"] = {"exit": result.returncode, "passed": int(passed[1]) if passed else None, "success": success}
ok &= success
print(f"classic gameTestServer: {'PASS ' + passed[1] if success else 'FAIL (see ' + str(log) + ')'}", flush=True)

log = out / "check.log"
with log.open("w") as stream:
    clean_appledouble()
    result = subprocess.run(gradle + ["check", "--continue", "jar"], cwd=source, env=env, stdout=stream, stderr=subprocess.STDOUT)
text = log.read_text()
current = set(re.findall(r"^> Task :.* FAILED$", text, re.M))
baseline = set((project / "qa/baseline-failed-tasks-e681c42.txt").read_text().splitlines())
new = sorted(current - baseline)
# The historical list is diagnostic only. Only tasks named in the maintainer waiver file (qa/maintainer-waivers.json) are exempt,
# and the report always lists them: a waiver never hides a failure and never changes a pinned hash or golden.
waiver_doc = json.loads((project / "qa/maintainer-waivers.json").read_text())
waived = {f"> Task :{entry['task']} FAILED" for entry in waiver_doc["tasks"]}
unwaived = current - waived
waiver_resolved = sorted(waived - current)
complete = "actionable tasks:" in text and ("BUILD SUCCESSFUL" in text or "BUILD FAILED" in text)
executed = set(re.findall(r"^> Task :(\w+)", text, re.M))
required = {re.search(r":(\w+)", line)[1] for line in baseline}
missing_tasks = sorted(required - executed)
resolved = sorted(line for line in baseline - current if re.search(r":(\w+)", line)[1] in executed)
success = complete and not unwaived and not missing_tasks and (result.returncode == 0 or bool(current))
summary["check"] = {"exit": result.returncode, "failed_tasks": len(current), "unwaived_failures": sorted(unwaived), "waived_failures": sorted(current & waived), "waiver_resolved": waiver_resolved, "new": new, "resolved": resolved, "success": success, "unexecuted_required_tasks": missing_tasks}
ok &= success
summary["acceptance"] = ("native groups pass and complete check passes except the %d maintainer-waived frozen proofs" % len(current & waived) if current else "native groups and complete check all pass") if ok else "FAILED"
(out / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
print(f"check: {len(current)} failing tasks ({len(current & waived)} waived), unwaived={sorted(unwaived)}, waiver_resolved={waiver_resolved}; success={success}", flush=True)
print(summary["acceptance"], flush=True)
raise SystemExit(0 if ok else 1)
