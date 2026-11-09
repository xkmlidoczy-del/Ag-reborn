#!/usr/bin/env python3
"""Rebuild the opt-in QA agent with JDK 21 and the existing Gradle ASM cache; no downloads."""
from pathlib import Path
import os
import subprocess
import tempfile
import zipfile

qa = Path(__file__).resolve().parent
jdk = Path(os.environ.get("JAVA_HOME", Path.home() / ".local/jdks/jdk-21.0.12.1+1/Contents/Home"))
asm = next((Path.home() / ".gradle-aca/caches/modules-2/files-2.1/org.ow2.asm/asm/9.9.1").glob("*/asm-9.9.1.jar"))
with tempfile.TemporaryDirectory(prefix="aca-qa-agent-") as temporary:
    out = Path(temporary)
    subprocess.run([str(jdk / "bin/javac"), "--release", "21", "-cp", str(asm), "-d", str(out), str(qa / "HiddenWindowAgent.java")], check=True)
    # Keep the agent self-contained; do not depend on a launcher's module/classloader
    # making ASM available to the instrumentation thread at exactly the right time.
    with zipfile.ZipFile(asm) as library:
        for entry in library.infolist():
            if entry.is_dir() or entry.filename == "module-info.class" or entry.filename.upper().startswith("META-INF/"):
                continue
            target = out / entry.filename
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(library.read(entry))
    notice = out / "META-INF/LICENSE-ASM.txt"
    notice.parent.mkdir(parents=True, exist_ok=True)
    notice.write_bytes((qa / "ASM-LICENSE.txt").read_bytes())
    manifest = out / "MANIFEST.MF"
    manifest.write_text("Manifest-Version: 1.0\nPremain-Class: HiddenWindowAgent\n\n")
    subprocess.run([str(jdk / "bin/jar"), "--create", "--file", str(qa / "hidden-window-agent.jar"), "--manifest", str(manifest), "-C", str(out), "."], check=True)
print("QA agent rebuilt (hidden window, virtual focus and scripted GLFW keys)")
