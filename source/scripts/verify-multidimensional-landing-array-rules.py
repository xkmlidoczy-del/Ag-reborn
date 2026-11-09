#!/usr/bin/env python3
"""Compile/run NEW351 pure budgets and geometry/source guards; never start Gradle or Minecraft."""
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
                  for name in ("CombatAmounts", "ContinuousHighPowerGrowth", "MultidimensionalLandingArrayRules")]
    tests = ["MultidimensionalLandingArrayRulesRegressionTest", "MultidimensionalLandingArrayGeometryRegressionTest"]
    sources = production + [root / "src/test/java" / package / (name + ".java") for name in tests]
    with tempfile.TemporaryDirectory(prefix="new351-rules-") as temporary:
        subprocess.run([str(java / "bin/javac"), "--release", "21", "-encoding", "UTF-8", "-d", temporary,
                        *map(str, sources)], cwd=root, check=True)
        for test in tests:
            subprocess.run([str(java / "bin/java"), "-ea", "-Xmx256m", "-cp", temporary,
                            "cn.academy.port.highpower.core." + test], cwd=root, check=True)
    print("NEW351 pure rules and source guards only; native Minecraft/world and visual verification remain separate")


if __name__ == "__main__":
    main()
