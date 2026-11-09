#!/usr/bin/env python3
"""Compile/run actual NEW351 cue geometry with Java 21; no Gradle, game or graphics stubs."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile


def toolchain(root):
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(Path(os.environ["JAVA_HOME"]))
    for name in ("javac", "java"):
        if shutil.which(name):
            candidates.append(Path(shutil.which(name)).resolve().parent.parent)
    for parent in [root, *root.parents]:
        candidates.extend(sorted((parent / "aca-tools").glob("jdk*")))
        candidates.extend(sorted((parent / ".tools").glob("jdk*")))
    for home in candidates:
        java = home / "bin/java"
        if not java.is_file():
            continue
        javac = home / "bin/javac"
        compiler = [str(javac)] if javac.is_file() else [str(java), "com.sun.tools.javac.Main"]
        result = subprocess.run([*compiler, "-version"], capture_output=True, text=True)
        if result.returncode == 0:
            return java, compiler
    raise RuntimeError("A Java 21 runtime with the jdk.compiler module is required")


def main():
    root = Path(__file__).resolve().parents[1]
    java, compiler = toolchain(root)
    core = root / "src/main/java/cn/academy/port/highpower/core"
    sources = [core / (name + ".java") for name in (
        "CombatAmounts", "ContinuousHighPowerGrowth", "SafeLandingProbeRules", "SafeLandingProbeGeometry")]
    sources += [root / "src/main/java/cn/academy/port/client/MultidimensionalLandingCueGeometry.java",
                root / "src/test/java/cn/academy/port/client/MultidimensionalLandingCueGeometryRegressionTest.java"]
    with tempfile.TemporaryDirectory(prefix="new351-cue-") as temporary:
        # Some supported Java 21 installations retain jdk.compiler but omit ct.sym and bin/javac.
        # Use the current runtime's API: this is a real-source test, not a release-API compatibility test.
        subprocess.run([*compiler, "-source", "21", "-target", "21", "-encoding", "UTF-8", "-d", temporary,
                        *map(str, sources)], cwd=root, check=True)
        subprocess.run([str(java), "-ea", "-Xmx256m", "-cp", temporary,
                        "cn.academy.port.client.MultidimensionalLandingCueGeometryRegressionTest"], cwd=root, check=True)
    print("Production pure geometry passed on the current Java runtime API (-source 21 -target 21).")
    print("Native Minecraft rendering, runtime integration and visual acceptance remain separate checks.")


if __name__ == "__main__":
    main()
