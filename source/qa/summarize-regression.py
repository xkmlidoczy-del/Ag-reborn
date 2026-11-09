#!/usr/bin/env python3
"""Rebuild a regression summary from explicit native evidence and a separately completed check/jar log.

This command NEVER runs Gradle. Reuse is visible in each result, with original log hashes;
maintainer-waived failures remain failures in the report, not repaired or silently omitted.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
from zoneinfo import ZoneInfo

SOURCE = Path(__file__).resolve().parents[1]
PROJECT = SOURCE.parent
TASK = re.compile(r"^> Task :([^\s]+)(?:[ \t]+(.*))?$", re.M)
REQUIRED_CURRENT = {
    "categoryShieldTest", "vectorCombatTest", "unifiedSkillTreeLayoutTest", "developerSkillDetailsLayoutTest",
    "castCameraTimelineTest", "castVisualReceiptsTest", "iconAtlasTest", "skillBudgetHudTest",
    "terminalClientIntegrationTest", "scatterPlateVisualTest",
    "highPowerRecoveryHudTest", "castSuccessWiringTest", "castSignatureDecorTest",
}
ZONE = ZoneInfo("Asia/Singapore")


def read(path: Path) -> str:
    return path.read_text(errors="replace").replace("\r\n", "\n")


def evidence(path: Path, task: str | None = None) -> dict:
    path = path.resolve()
    return {"log": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "modified_at": datetime.fromtimestamp(path.stat().st_mtime, ZONE).isoformat(), "task": task}


def section(text: str, task: str) -> str:
    starts = list(TASK.finditer(text))
    found = [index for index, match in enumerate(starts) if match[1] == task]
    if len(found) != 1:
        raise ValueError(f"Expected exactly one :{task} task section, found {len(found)}")
    index = found[0]
    return text[starts[index].end():starts[index + 1].start() if index + 1 < len(starts) else len(text)]


def native(path: Path, task: str, group: int | None = None, verified_check: dict | None = None) -> dict:
    text = read(path)
    body = section(text, task)
    passed = re.findall(r"All (\d+) required tests passed", body)
    complete = re.findall(r"(\d+) GAME TESTS COMPLETE", body)
    build = re.findall(r"^BUILD (SUCCESSFUL|FAILED)\b", text, re.M)
    failed_tasks = [match[1] for match in TASK.finditer(text) if match[2] == "FAILED"]
    source = evidence(path, task)
    # A combined check/classic invocation can end FAILED solely because of approved frozen proofs.
    # Admit that footer only after the complete check was independently verified from this exact file/hash.
    same_check = (verified_check is not None and verified_check["success"]
                  and verified_check["evidence"]["log"] == source["log"]
                  and verified_check["evidence"]["sha256"] == source["sha256"])
    waived_names = ({line.split(":", 1)[1].split()[0] for line in verified_check["waived_failures"]}
                    if same_check else set())
    waived_build = (same_check and build == ["FAILED"] and verified_check["exit"] != 0
                    and bool(failed_tasks) and set(failed_tasks) == waived_names and task not in failed_tasks)
    task_states = [match[2] for match in TASK.finditer(text) if match[1] == task]
    success = (((build == ["SUCCESSFUL"] and not failed_tasks) or waived_build)
               and task_states == [None] and len(passed) == len(complete) == 1 and passed[0] == complete[0])
    result = {"exit": 0 if build == ["SUCCESSFUL"] else 1, "passed": int(passed[0]) if len(passed) == 1 else None,
              "success": success, "reused": True, "evidence": source, "exit_code_source": "inferred from complete Gradle build footer"}
    if same_check:
        result["exit"] = verified_check["exit"]
        result["exit_code_source"] = "explicit process exit code from the same fully validated check log"
    if waived_build:
        result["build_waived_failures"] = verified_check["waived_failures"]
        result["build_note"] = "Native task passed; the shared Gradle invocation failed only the listed approved frozen proofs."
    if group is not None:
        result = {"group": group, **result}
    if not success:
        result["evidence_error"] = "Native task needs an executed task with matching completion/pass counts and one successful complete Gradle invocation, or the same fully verified check log with only approved failures"
    return result


def check_result(path: Path, exit_code: int, baseline: set[str], waived: set[str], required: set[str]) -> dict:
    text = read(path)
    matches = list(TASK.finditer(text))
    executed = {match[1] for match in matches if match[2] not in {"SKIPPED", "NO-SOURCE"}}
    failed = {f"> Task :{match[1]} FAILED" for match in matches if match[2] == "FAILED"}
    exceptions = set(re.findall(r"Execution failed for task ':([^']+)'", text))
    failed_names = {line.split(":", 1)[1].split()[0] for line in failed}
    build = re.findall(r"^BUILD (SUCCESSFUL|FAILED)\b", text, re.M)
    issues = []
    if len(build) != 1 or not re.search(r"\d+ actionable tasks:", text):
        issues.append("Missing or ambiguous complete Gradle footer")
    if "jar" not in executed:
        issues.append("The final log does not include the requested :jar task")
    if exit_code == 0 and (build != ["SUCCESSFUL"] or failed or exceptions):
        issues.append("Exit code zero disagrees with failure evidence")
    if exit_code != 0:
        if build != ["FAILED"]:
            issues.append("Nonzero exit code lacks the completed BUILD FAILED footer")
        if not failed:
            issues.append("Non-task/build/infrastructure failure cannot be covered by task waivers")
        if exceptions != failed_names:
            issues.append("Gradle exception task set disagrees with failed-task set")
    if re.search(r"Gradle build daemon disappeared unexpectedly|OutOfMemoryError|daemon has disappeared", text):
        issues.append("Build infrastructure failure present")
    required = required | {re.search(r":([^\s]+)", line)[1] for line in baseline}
    missing = sorted(required - executed)
    unwaived = failed - waived
    success = not issues and not missing and not unwaived
    return {"exit": exit_code, "failed_tasks": len(failed), "unwaived_failures": sorted(unwaived),
            "waived_failures": sorted(failed & waived),
            "waiver_resolved": sorted(line for line in waived - failed if line.split(":", 1)[1].split()[0] in executed),
            "new": sorted(failed - baseline),
            "resolved": sorted(line for line in baseline - failed if line.split(":", 1)[1].split()[0] in executed),
            "unexecuted_required_tasks": missing, "summary_issues": issues, "success": success,
            "evidence": evidence(path, "check --continue jar"), "exit_code_source": "explicit caller-provided process exit code"}


def scatter_visual_result(native_log: Path, check_log: Path, check: dict) -> dict:
    task = "scatterPlateVisualTest"
    native_text = read(native_log)
    # New delivery-native invocations may run only group4/classic/jar. Absence is not a failed visual run.
    use_native = any(match[1] == task for match in TASK.finditer(native_text))
    path = native_log if use_native else check_log
    text = native_text if use_native else read(check_log)
    matches = [match for match in TASK.finditer(text) if match[1] == task]
    result = {"success": False, "evidence": evidence(path, task),
              "source": "native-final-log" if use_native else "validated final check log"}
    if len(matches) != 1:
        result["evidence_error"] = f"Expected exactly one executed :{task} section in the selected log, found {len(matches)}"
        return result
    body = section(text, task)
    passed = re.findall(r"PASS (\d+) NEW212\b", body)
    build = re.findall(r"^BUILD (SUCCESSFUL|FAILED)\b", text, re.M)
    failed = any(match[2] == "FAILED" for match in TASK.finditer(text))
    same_check = (check["success"] and check["evidence"]["log"] == result["evidence"]["log"]
                  and check["evidence"]["sha256"] == result["evidence"]["sha256"])
    complete = build == ["SUCCESSFUL"] and not failed if use_native else same_check
    result["success"] = bool(complete and matches[0][2] is None and len(passed) == 1 and int(passed[0]) > 0)
    result["passed"] = int(passed[0]) if len(passed) == 1 else None
    if not result["success"]:
        result["evidence_error"] = "Visual proof needs an executed task and one positive NEW212 PASS in a complete successful native build or the same fully validated final check"
    return result


def summarize(native_dir: Path, native_final: Path, check_log: Path, exit_code: int, classic_final: Path | None = None) -> dict:
    baseline_path = PROJECT / "qa/baseline-failed-tasks-e681c42.txt"
    waiver_path = PROJECT / "qa/maintainer-waivers.json"
    baseline = {line for line in read(baseline_path).splitlines() if line.strip()}
    waiver_doc = json.loads(read(waiver_path))
    waived = {f"> Task :{entry['task']} FAILED" for entry in waiver_doc["tasks"]}
    groups = [native(native_dir / f"group-{number}.log", "runHighPowerGameTest", number) for number in range(1, 4)]
    groups.append(native(native_final, "runHighPowerGameTest", 4))
    check = check_result(check_log, exit_code, baseline, waived, REQUIRED_CURRENT)
    classic = native(classic_final or native_final, "runGameTestServer", verified_check=check if classic_final else None)
    scatter = scatter_visual_result(native_final, check_log, check)
    ok = all(group["success"] for group in groups) and classic["success"] and check["success"] and scatter["success"]
    waived_count = len(check["waived_failures"])
    if ok:
        acceptance = (f"reused native evidence and latest complete check pass except {waived_count} explicitly listed maintainer-waived frozen proofs"
                      if waived_count else "reused native evidence and latest complete check all pass")
    else:
        acceptance = "FAILED"
    return {"generated_at": datetime.now(ZONE).isoformat(), "summary_mode": "explicit reused native logs plus a separately completed check/jar log",
            "native_scope": "These native runs occurred at the times in their individual logs. This is not a claim that all suites reran in one invocation or against one proven identical compiled snapshot.",
            "groups": groups, "classic": classic, "check": check,
            "supplementary": {"scatterPlateVisualTest": scatter},
            "waiver_policy": {"file": str(waiver_path), "approved_tasks": len(waived), "sha256": hashlib.sha256(waiver_path.read_bytes()).hexdigest(),
                              "note": "Waived failures are unresolved frozen proofs, not passing tests. No new waiver was added."},
            "acceptance": acceptance}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reuse-native-dir", type=Path, required=True, help="Explicit evidence directory containing this round's passing group-1/2/3.log")
    parser.add_argument("--native-final-log", type=Path, required=True, help="Complete successful group4 log; also supplies classic unless overridden. Visual proof falls back to the final check when absent here.")
    parser.add_argument("--classic-final-log", type=Path, help="Optional latest classic-only log, or the same complete check/jar/classic log as --check-log")
    parser.add_argument("--check-log", type=Path, required=True, help="Latest complete check --continue jar log")
    parser.add_argument("--check-exit-code", type=int, required=True, help="Actual process exit code, including 1 when only approved frozen proofs failed")
    parser.add_argument("--output", type=Path, default=PROJECT / "qa/output/summary.json")
    args = parser.parse_args()
    if args.check_exit_code < 0:
        parser.error("Use the actual nonnegative process exit code; killed/incomplete runs must not be summarized as completed checks")
    try:
        result = summarize(args.reuse_native_dir, args.native_final_log, args.check_log, args.check_exit_code, args.classic_final_log)
    except (OSError, ValueError, KeyError) as error:
        parser.error(str(error))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    # Atomic replacement: an interrupted write cannot leave half a summary. Previous evidence logs are never changed.
    temporary = args.output.with_name(args.output.name + ".tmp")
    temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    temporary.replace(args.output)
    print(result["acceptance"])
    print(f"groups={[entry['passed'] for entry in result['groups']]} classic={result['classic']['passed']} "
          f"unwaived={len(result['check']['unwaived_failures'])} waived={len(result['check']['waived_failures'])} "
          f"missing={result['check']['unexecuted_required_tasks']} issues={result['check']['summary_issues']}")
    print(f"summary={args.output.resolve()}")
    return 1 if result["acceptance"] == "FAILED" else 0


if __name__ == "__main__":
    raise SystemExit(main())
