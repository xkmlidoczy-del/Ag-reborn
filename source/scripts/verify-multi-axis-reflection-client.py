#!/usr/bin/env python3
"""Compile actual NEW442 receipt/render geometry and input/coordinator without Gradle."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
candidates = [Path(os.environ["JAVA_HOME"])] if os.environ.get("JAVA_HOME") else []
if shutil.which("javac"):
    candidates.append(Path(shutil.which("javac")).resolve().parent.parent)
for parent in [root, *root.parents]:
    candidates.extend(sorted((parent / "aca-tools").glob("jdk*")))
    candidates.extend(sorted((parent / ".tools").glob("jdk*")))
java = next((home for home in candidates if (home / "bin/javac").is_file() and (home / "bin/java").is_file()), None)
if java is None:
    raise RuntimeError("Set JAVA_HOME to JDK 21")
guavas = sorted((root / ".gradle-user/caches/modules-2/files-2.1/com.google.guava/guava").glob("*/*/guava-*.jar"))
for parent in [root, *root.parents]:
    guavas.extend(sorted((parent / "aca-tools").glob("gradle-*/lib/guava-*.jar")))
if not guavas:
    raise RuntimeError("The real coordinator CPU regression needs the project's Guava jar")
names = ["MultiAxisReflectionInputRegressionTest", "MultiAxisReflectionVisualRegressionTest"]
with tempfile.TemporaryDirectory(prefix="aca-new442-client-") as temporary:
    subprocess.run([str(java / "bin/javac"), "--release", "21", "-proc:none", "-encoding", "UTF-8", "-d", temporary,
                    "-cp", str(guavas[-1]), "-sourcepath", str(root / "src/main/java"),
                    *[str(root / "src/test/java/cn/academy/port/client" / (name + ".java")) for name in names]], check=True)
    classpath = os.pathsep.join([temporary, str(guavas[-1]), str(root / "src/main/resources")])
    for name in names:
        subprocess.run([str(java / "bin/java"), "-ea", "-cp", classpath, "cn.academy.port.client." + name], check=True, cwd=root)
print("NEW442 CPU regressions only; these do not establish native runtime or rendered visual quality")
