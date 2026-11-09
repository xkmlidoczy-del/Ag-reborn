#!/usr/bin/env python3
"""Pure canon-VFX design/geometry test; no Gradle, game, or GPU is used."""
from pathlib import Path
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
files=[root/'src/main/java/cn/academy/port/canon/CanonVfxDesign.java',
       root/'src/main/java/cn/academy/port/client/canon/CanonVfxGeometry.java',
       root/'src/main/java/cn/academy/port/client/canon/CanonPoseMath.java',
       root/'src/main/java/cn/academy/port/client/canon/CanonMeshGeometry.java',
       root/'src/test/java/cn/academy/port/client/canon/CanonVfxGeometryTest.java',
       root/'src/test/java/cn/academy/port/client/canon/CanonPoseMathTest.java',
       root/'src/test/java/cn/academy/port/client/canon/CanonMeshGeometryTest.java']
with tempfile.TemporaryDirectory(prefix='academy-canon-vfx-') as output:
    subprocess.run(['javac','--release','21','-d',output,*map(str,files)],check=True)
    subprocess.run(['java','-cp',output,'cn.academy.port.client.canon.CanonVfxGeometryTest'],check=True)
    subprocess.run(['java','-cp',output,'cn.academy.port.client.canon.CanonPoseMathTest'],check=True)
    subprocess.run(['java','-cp',output,'cn.academy.port.client.canon.CanonMeshGeometryTest'],check=True)
