#!/usr/bin/env python3
"""Compile actual .62 volume and accepted receipt geometry without Gradle/native."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
candidates = [Path(os.environ["JAVA_HOME"]) / "bin/javac"] if os.environ.get("JAVA_HOME") else []
if shutil.which("javac"):
    candidates.append(Path(shutil.which("javac")))
for parent in [ROOT, *ROOT.parents]:
    candidates.extend(parent.glob("aca-tools/jdk-*/bin/javac"))
    candidates.extend(parent.glob(".tools/jdk-*/bin/javac"))
javac = next((path for path in candidates if path.is_file()), None)
if javac is None:
    raise SystemExit("JDK 21 required; set JAVA_HOME to its installation")
with tempfile.TemporaryDirectory(prefix="aca-new421-volume-") as output:
    subprocess.run([str(javac), "--release", "21", "-proc:none", "-encoding", "UTF-8", "-d", output,
                    "-sourcepath", str(ROOT / "src/main/java"),
                    str(ROOT / "src/test/java/cn/academy/port/client/ShearWindbladeVolumeRegressionTest.java")],
                   check=True, cwd=ROOT)
    subprocess.run([str(javac.with_name("java")), "-ea", "-cp", output,
                    "cn.academy.port.client.ShearWindbladeVolumeRegressionTest"], check=True, cwd=ROOT)
