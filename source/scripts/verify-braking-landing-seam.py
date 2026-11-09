#!/usr/bin/env python3
"""Isolated CPU native admission checks. Requires the existing pinned native build artifacts.
Pass a Mixin debug-export root to additionally verify all three actual transformed classes.
This script does not replace the real packet/collision GameTests.
"""
from pathlib import Path
import os
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
native = root / 'build/moddev/artifacts/neoforge-21.1.252.jar'
java = root / '.tools/jdk-21.0.12.1+1/bin'
cache = root / '.gradle-user/caches/modules-2/files-2.1'
if not native.is_file():
    raise SystemExit('Pinned native artifact is absent; build the repository first')
jars = sorted(p for p in cache.rglob('*.jar') if not p.name.endswith('-sources.jar'))
# Build-tool fat jars can contain private ASM copies; official ASM must precede them.
asm = [p for p in jars if 'org.ow2.asm' in p.parts]
out = root / 'build/braking-landing-seam-isolated'
out.mkdir(parents=True, exist_ok=True)
classpath = os.pathsep.join(map(str, asm + [native, root / 'build/classes/java/main'] + jars))
source = root / 'src/main/java/cn/academy/port/highpower'
files = [source / 'BrakingTouchLandingProof.java'] + [source / 'mixin' / name for name in (
    'NativeBrakingTouchMixinPlugin.java', 'NativeScatterPlateMixinPlugin.java',
    'EntityBrakingTouchMixin.java', 'ServerConnectionBrakingTouchMixin.java', 'LivingEntityBrakingTouchMixin.java')]
files.append(root / 'src/test/java/cn/academy/port/highpower/BrakingTouchNativeSeamRegressionTest.java')
subprocess.run([str(java / 'javac'), '-proc:none', '-cp', classpath,
                '-sourcepath', str(root / 'src/main/java'), '-d', str(out), *map(str, files)], check=True, cwd=root)
subprocess.run([str(java / 'java'), '-ea', '-cp', str(out) + os.pathsep + classpath,
                'cn.academy.port.highpower.BrakingTouchNativeSeamRegressionTest', *sys.argv[1:]], check=True, cwd=root)
