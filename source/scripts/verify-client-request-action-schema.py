#!/usr/bin/env python3
"""Audit actual registered/client action syntax and known pure value schemas, without Gradle or native claims."""
from pathlib import Path
import argparse
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, help="Optional plain-text audit output")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java = toolchain(root)
    production = root / "src/main/java/cn/academy/port"
    sources = [production / "core/SkillPresets.java", production / "core/LegacySingleKeyProtocol.java",
               production / "core/TargetedContextTermination.java", production / "highpower/HighPowerInput.java",
               root / "src/test/java/cn/academy/port/client/ClientRequestActionSchemaRegressionTest.java"]
    with tempfile.TemporaryDirectory(prefix="academy-request-actions-") as temporary:
        subprocess.run([str(java / "bin/javac"), "--release", "21", "-encoding", "UTF-8", "-d", temporary,
                        *map(str, sources)], check=True, cwd=root)
        command = [str(java / "bin/java"), "-ea", "-cp", temporary,
                   "cn.academy.port.client.ClientRequestActionSchemaRegressionTest", str(root)]
        if args.report:
            command.append(str(args.report.resolve()))
        subprocess.run(command, check=True, cwd=root)


if __name__ == "__main__":
    main()
