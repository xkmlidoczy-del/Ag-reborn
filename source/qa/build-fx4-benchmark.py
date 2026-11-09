#!/usr/bin/env python3
"""Generate a four-window, single-JVM FX4 preview benchmark; never launch a client.

Example:
  python3 -B qa/build-fx4-benchmark.py --out /absolute/benchmark.script \
    --manifest /absolute/benchmark-manifest.json

Launch that script with run-fx4-rollout.py --max-fps 260 --camera off
--shaders on, in its fresh hidden gameDir, after every other game JVM stops.
All injected effects are explicitly QA previews. No gameplay success is implied.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re


SOURCE = Path(__file__).resolve().parents[1]
LIFE_TICKS = 200
WARMUP_TICKS = 8
NATURAL_CLEANUP_TICKS = 400
FPS_CAP = 260
SINGLE = "dark_construct"
# Twelve distinct LV5 entries; the pool cap is 24. Each of the eight new
# categories is present. IDs remain different even when their visual roles match.
TWELVE = (
    "mental_conflict_ring", "mental_out_crowd",
    "dark_construct", "dark_matter_barrage",
    "nitrogen_dome", "aero_barrage", "aero_pressure_lance",
    "hydro_domain", "hydro_eddy", "float_field",
    "liquid_shadow_field", "paper_array",
)
TARGETS = ((-1.5, 3.3), (.5, 4.3), (2.5, 3.3), (-.7, 6.0), (1.7, 6.0))
CAMERA = (7.5, -57.2, -1.5, .5, -59.0, 3.5)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def catalogue(source):
    rows = json.loads((source / "docs/canon/skills.json").read_text())
    entries = {row["id"]: row for row in rows}
    design_text = (source / "src/main/java/cn/academy/port/canon/CanonVfxDesign.java").read_text()
    designs = dict(re.findall(r'Map\.entry\("([^"]+)",d\("([^"]+)"', design_text))
    for skill in set(TWELVE) | {"mental_probe", SINGLE}:
        if skill not in entries or designs.get(skill) != entries[skill]["category"]:
            raise ValueError("No matching catalogued preview design: " + skill)
    if len(set(TWELVE)) != 12 or any(entries[skill]["level"] != 5 for skill in TWELVE):
        raise ValueError("The twelve-effect fixture requires twelve distinct LV5 skills")
    if len({entries[skill]["category"] for skill in TWELVE}) != 8:
        raise ValueError("The twelve-effect fixture must span all eight new categories")
    return entries


def nominal_cost(step):
    """Driver commands execute on one tick, then consume their configured wait.

    begin/end additionally request an asynchronous server read. Two ticks here
    are a planning estimate, not proof or a hard limit on the actual future.
    """
    if step.startswith("wait:"):
        return 1 + int(step.split(":", 1)[1])
    if step.startswith(("cmd:", "live:")):
        return 7
    if step == "recend":
        return 21
    if step.startswith("fx4qa:"):
        return 2
    return 1


def build(prefix="bench", source=SOURCE):
    if not re.fullmatch(r"[A-Za-z0-9_-]+", prefix):
        raise ValueError("prefix must contain only letters, digits, underscore or hyphen")
    entries = catalogue(source)
    steps = []
    windows = []
    clock = 0

    def add(*items):
        nonlocal clock
        steps.extend(items)
        clock += sum(nominal_cost(item) for item in items)

    add("wait:80", "cmd:time set noon", "cmd:gamerule doDaylightCycle false",
        "cmd:gamerule doMobSpawning false", "cmd:weather clear", "cmd:difficulty normal",
        "escape", "keyup:82", "cmd:effect clear @s",
        "cmd:kill @e[type=!minecraft:player]",
        "cmd:fill -12 -62 -8 12 -57 18 minecraft:air",
        "cmd:fill -12 -62 -8 12 -62 18 minecraft:dirt",
        "cmd:fill -12 -61 -8 12 -61 18 minecraft:grass_block",
        "cmd:tp @s 0.5 -60 0.5 -70 0", "live:setup:mental_probe", "wait:30")
    for x, z in TARGETS:
        add(f"cmd:summon minecraft:husk {x} -60 {z} "
            '{Tags:["fx4_benchmark"],NoAI:0b,Silent:1b,PersistenceRequired:1b}')
    add("cmd:execute as @e[type=minecraft:husk,tag=fx4_benchmark] run attribute @s minecraft:generic.movement_speed base set 0",
        "cmd:tp @s 0.5 -60 0.5 -70 0", "cam:third",
        "vcam:" + ",".join(str(v) for v in CAMERA), "gui:off", "live:clearui", "wait:120")

    def window(label, skills, representative, cleanup_before=0):
        if cleanup_before:
            # Let natural effect, delayed particle and decal leases retire.
            # No pool clearing or world switch can hide lingering effects.
            add(f"wait:{cleanup_before}")
        injections = []
        for skill in skills:
            injections.append({"skill": skill, "nominalCommandTick": clock,
                               "nominalEffectBirthTick": clock + 8})
            add(f"cmd:aca_canon_vfx {skill} active {LIFE_TICKS}")
        if skills:
            # Preview commands with a non-60 duration start eight ticks later.
            # The final command already has its six-tick driver wait; this
            # explicit warm-up leaves the youngest effect fully risen.
            add(f"wait:{WARMUP_TICKS}")
        name = f"{prefix}_{label}"
        add(f"fx4qa:begin:{representative}:preview")
        nominal_open = clock - 1
        # Total ready window is about 75 ticks, INCLUDING recend's twenty-tick
        # tail. Recording is open for four source frames, not all 75 ticks.
        # No screenshot is requested while the twelve-effect pool ramps up.
        add(f"mark:fx4benchmark:{name}", "wait:46", f"rec:{name}", "wait:2",
            "recend", "fx4qa:end")
        nominal_close = clock - 1
        oldest_age = None if not injections else nominal_close - injections[0]["nominalEffectBirthTick"]
        if oldest_age is not None and oldest_age >= LIFE_TICKS - 20:
            raise ValueError("Insufficient lifetime margin for the steady benchmark window")
        row = {"serialExpected": len(windows) + 1, "name": name,
               "representativeSkill": representative, "mode": "preview",
               "representativeCategory": entries[representative]["category"],
               "representativeSkillLevel": entries[representative]["level"],
               "effects": [{"id": skill, "category": entries[skill]["category"],
                            "level": entries[skill]["level"], "phase": "active",
                            "durationTicks": LIFE_TICKS, "qaPreview": True} for skill in skills],
               "effectPoolExpected": {"minCanonVfxEffectsAtLeast": len(skills),
                                      "peakCanonVfxEffectsExactly": len(skills)},
               "warmupAfterInjectionTicksRequested": WARMUP_TICKS if skills else 0,
               "naturalCleanupBeforeTicksRequested": cleanup_before,
               "readyWindowExpectedTickRange": [70, 80],
               "nominalReadyWindowTicks": nominal_close - nominal_open,
               "nominalOldestEffectAgeAtWindowClose": oldest_age,
               "nominalEffectLifetimeMarginAtClose": None if oldest_age is None else LIFE_TICKS - oldest_age,
               "nominalInjections": injections,
               "sourceFramesExpected": 4,
               "capture": "one native screenshot per client tick during a four-tick rec; recend includes an additional twenty ticks in metrics",
               "acceptance": "actual ready-window metrics and preview labels; no real-cast or hit claim"}
        if not skills:
            row["effectPoolExpected"]["minCanonVfxEffectsExactly"] = 0
            row["particlePoolExpected"] = {"peakCanonParticlesLiveExactly": 0}
        windows.append(row)

    window("empty_baseline", (), "mental_probe")
    window("single_lv5", (SINGLE,), SINGLE)
    window("twelve_lv5", TWELVE, "mental_conflict_ring", NATURAL_CLEANUP_TICKS)
    window("empty_cleanup", (), "mental_probe", NATURAL_CLEANUP_TICKS)
    add("gui:on", "quit")
    script = "|".join(steps) + "\n"
    manifest = {
        "schema": 1, "suite": "single-jvm-fx4-preview-benchmark", "prefix": prefix,
        "scriptSha256": hashlib.sha256(script.encode()).hexdigest(),
        "windows": windows, "windowCount": 4,
        "launchRequirements": {"runner": str(source / "qa/run-fx4-rollout.py"),
            "flags": ["--max-fps", str(FPS_CAP), "--camera", "off", "--shaders", "on"],
            "configuredFpsCapExactly": FPS_CAP, "configuredVsyncExactly": False,
            "hiddenAgentRequired": True, "uniqueGameDirReceiptRootAndRunToken": True,
            "frozenClassesAndResourcesRequired": True, "simultaneousGameJvmsAllowed": 1,
            "noCompetingRecordingOrEncodingWorkload": True},
        "fixture": {"camera": list(CAMERA), "caster": [.5, -60, .5],
            "casterYaw": -70, "targetType": "minecraft:husk", "targets": [[x, -60, z] for x, z in TARGETS],
            "targetCount": 5, "targetNoAi": False, "targetMovementSpeed": 0,
            "note": "Same five real AI-enabled native entities, terrain and fixed camera in all four windows; QA previews apply no gameplay damage."},
        "timingPolicy": {"effectDurationTicks": LIFE_TICKS,
            "previewPendingDelayTicks": 8, "driverCommandTickCost": 7,
            "twelveCommandRampTicks": 84, "explicitWarmupTicksRequested": WARMUP_TICKS,
            "rampIncludedInReadyWindow": False, "recendImplicitWaitTicksIncluded": 20,
            "naturalCleanupWaitTicksRequested": NATURAL_CLEANUP_TICKS,
            "planningClockIsRuntimeProof": False,
            "rule": "Require every actual twelve-effect ready-window sample to have minCanonVfxEffects>=12; reject a missing, expired or ramp-up-diluted window. Use observed startClientTick/endClientTick rather than assuming asynchronous begin/end took two ticks."},
        "cpuStatisticsRequired": {
            "source": "fx4-render-metrics.jsonl / session-summary",
            "rowsExpected": 4, "identity": "clip serial, representative skill, mode=preview and manifest order",
            "series": ["afterParticlesDispatchCpuWall", "renderFrameCpuWall", "frameStartIntervals"],
            "reportEverySeries": ["samples", "meanMs", "p95Ms", "maxMs"],
            "quantileSamplesOmittedExpected": 0,
            "dropOutliers": False,
            "aggregation": "Keep each window and each metric separate. Do not replace max with p95, average the four window means, or hide slow individual frames.",
            "gpuTiming": False,
            "scope": "CPU-side System.nanoTime wall intervals, including possible GL driver submission/waits; no GPU timer queries or synchronization. Frame-start intervals also include screenshots, FPS cap and scheduling. AFTER_PARTICLES covers peer listeners; it is not isolated incremental FX cost."},
        "nativeStateRequired": {"nativeEffectPoolSamplesPositive": True,
            "nativeShaderState.shaders": True, "nativeShaderState.loadedShaderCountPositive": True,
            "effectiveShaderModesMustBeReported": True, "telemetryErrorsAllowed": 0},
        "sourceEvidence": {str(path.relative_to(source)): digest(path) for path in (
            source / "docs/canon/skills.json",
            source / "src/main/java/cn/academy/port/canon/CanonVfxDesign.java",
            source / "src/main/java/cn/academy/port/client/canon/CanonVfxClient.java",
            source / "src/showcaseQa/java/cn/academy/port/qa/ShowcaseDriver.java",
            source / "src/showcaseQa/java/cn/academy/port/qa/Fx4RenderMetricsQa.java")},
        "limits": [
            "Generated fixtures and planned timing are not runtime or performance results.",
            "All four Fx4RolloutQa sessions are preview labels. Preview injection deliberately precedes begin and empty windows inject nothing; the gameplay receipt checker which requires an in-window preview command is not the benchmark validator.",
            "CanonVfxClient.EFFECTS is a bounded native pool count, not proof of visible pixels or twelve equally expensive workloads. Review the native twelve-effect frames as a separate check.",
            "Injection order yields different effect ages. This measures a sustained mixed LV5 workload, not twelve simultaneous first-frame impacts or a natural multiplayer encounter.",
            "Cleanup requires actual zero effect and particle counts after a 400-tick natural wait. The current metrics collector does not read the decal pool, so decal retirement is supported by source lifetimes and source-frame review, not a measured native zero-decal counter.",
            "Single-session CPU telemetry is descriptive; no GPU timing, universal FPS guarantee, statistically controlled regression, gameplay success or multiplayer acceptance is implied.",
        ],
    }
    return script, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--prefix", default="bench")
    args = parser.parse_args()
    script, manifest = build(args.prefix)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(script)
    else:
        print(script, end="")
    if args.manifest:
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        args.manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    if args.out:
        print(json.dumps({"script": str(args.out.resolve()),
            "manifest": str(args.manifest.resolve()) if args.manifest else None,
            "windows": len(manifest["windows"]), "sha256": manifest["scriptSha256"],
            "nominalReadyTicks": [row["nominalReadyWindowTicks"] for row in manifest["windows"]],
            "oldestTwelveAgeAtClose": manifest["windows"][2]["nominalOldestEffectAgeAtWindowClose"]}))


if __name__ == "__main__":
    main()
