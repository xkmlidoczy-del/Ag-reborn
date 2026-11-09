#!/usr/bin/env python3
"""Run actual NEW341 pure budget/geometry tests in a temporary JDK21 output directory."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile


def toolchain(root):
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    if shutil.which("javac"):
        candidates.append(Path(shutil.which("javac")).resolve().parent.parent)
    for parent in [root, *root.parents]:
        candidates.extend(sorted((parent / "aca-tools").glob("jdk*")))
        candidates.extend(sorted((parent / ".tools").glob("jdk*")))
    for home in candidates:
        if (home / "bin/javac").is_file() and (home / "bin/java").is_file():
            return home
    raise RuntimeError("Set JAVA_HOME to a JDK 21 installation")


def main():
    root = Path(__file__).resolve().parents[1]
    java = toolchain(root)
    package = "cn/academy/port/highpower/core"
    production = [root / "src/main/java" / package / (name + ".java")
                  for name in ("CombatAmounts", "ContinuousHighPowerGrowth", "SpatialPincerCorridorRules")]
    tests = ["SpatialPincerCorridorRulesRegressionTest", "SpatialPincerCorridorGeometryRegressionTest"]
    sources = production + [root / "src/test/java" / package / (name + ".java") for name in tests]
    with tempfile.TemporaryDirectory(prefix="new341-rules-") as temporary:
        subprocess.run([str(java / "bin/javac"), "--release", "21", "-encoding", "UTF-8", "-d", temporary,
                        *map(str, sources)], check=True)
        for test in tests:
            subprocess.run([str(java / "bin/java"), "-ea", "-cp", temporary,
                            "cn.academy.port.highpower.core." + test], check=True)
    print("NEW341 pure rules/geometry only; no native Minecraft runtime or visual claim")


if __name__ == "__main__":
    main()
