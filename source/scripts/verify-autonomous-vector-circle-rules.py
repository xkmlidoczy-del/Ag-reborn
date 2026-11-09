#!/usr/bin/env python3
"""Compile and run the actual NEW452 pure resource/geometry/quota rules with JDK 21."""
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
    names = ("CombatAmounts", "ContinuousHighPowerGrowth", "HighPowerLevel", "DefenseProfile", "DamageMitigation",
             "DamageResult", "ExactCombatState", "MagneticDomainShieldRules", "SharedShieldReserveShares",
             "VectorParryRules", "AutonomousVectorCircleRules")
    sources = [root / "src/main/java" / package / (name + ".java") for name in names]
    sources.append(root / "src/test/java" / package / "AutonomousVectorCircleRulesRegressionTest.java")
    with tempfile.TemporaryDirectory(prefix="new452-rules-") as temporary:
        subprocess.run([str(java / "bin/javac"), "--release", "21", "-encoding", "UTF-8", "-d", temporary,
                        *map(str, sources)], check=True)
        subprocess.run([str(java / "bin/java"), "-ea", "-cp", temporary,
                        "cn.academy.port.highpower.core.AutonomousVectorCircleRulesRegressionTest"], check=True)
    print("NEW452 pure rules only; no native Minecraft runtime or visual claim")


if __name__ == "__main__":
    main()
