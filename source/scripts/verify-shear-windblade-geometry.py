#!/usr/bin/env python3
"""Compile and exercise actual NEW421 pure geometry without Gradle or Minecraft."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
java_home = os.environ.get("JAVA_HOME")
candidates = ([Path(java_home) / "bin/javac"] if java_home else [])
if shutil.which("javac"):
    candidates.append(Path(shutil.which("javac")))
for parent in [ROOT, *ROOT.parents]:
    candidates.extend(parent.glob(".tools/jdk-*/bin/javac"))
    candidates.extend(parent.glob("aca-tools/jdk-*/bin/javac"))
javac = next((candidate for candidate in candidates if candidate.is_file()), None)
if javac is None:
    raise SystemExit("JDK 21 is required; set JAVA_HOME to its installation")
java = javac.with_name("java")
with tempfile.TemporaryDirectory(prefix="aca-new421-geometry-") as classes:
    subprocess.run([str(javac), "--release", "21", "-d", classes,
                    str(ROOT / "src/main/java/cn/academy/port/highpower/core/ShearWindbladeGeometry.java"),
                    str(ROOT / "src/test/java/cn/academy/port/highpower/core/ShearWindbladeGeometryRegressionTest.java")],
                   check=True, cwd=ROOT)
    subprocess.run([str(java), "-cp", classes,
                    "cn.academy.port.highpower.core.ShearWindbladeGeometryRegressionTest"], check=True, cwd=ROOT)
