#!/usr/bin/env python3
"""Compile production NEW413 input/state/geometry and real coordinator CPU regressions."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
candidates = [Path(os.environ['JAVA_HOME'])] if os.environ.get('JAVA_HOME') else []
if shutil.which('javac'):
    candidates.append(Path(shutil.which('javac')).resolve().parent.parent)
for parent in [root, *root.parents]:
    candidates.extend(sorted((parent / 'aca-tools').glob('jdk*')))
    candidates.extend(sorted((parent / '.tools').glob('jdk*')))
java = next((home for home in candidates if (home / 'bin/javac').is_file() and (home / 'bin/java').is_file()), None)
if java is None:
    raise RuntimeError('Set JAVA_HOME to JDK 21')
guavas = sorted((root / '.gradle-user/caches/modules-2/files-2.1/com.google.guava/guava').glob('*/*/guava-*.jar'))
if not guavas:
    raise RuntimeError('Populate the project Gradle dependency cache before running the real coordinator CPU tests')
gsons = sorted((root / '.gradle-user/caches/modules-2/files-2.1/com.google.code.gson/gson').glob('*/*/gson-*.jar'))
native = sorted((root / 'build/moddev/artifacts').glob('neoforge-*-merged.jar'))
if not gsons or not native:
    raise RuntimeError('Run the project compile once to prepare catalog compile dependencies')
asms = [sorted((root / '.gradle-user/caches/modules-2/files-2.1/org.ow2.asm' / name).glob('*/*/' + name + '-*.jar'))[-1]
        for name in ['asm', 'asm-tree', 'asm-commons']]
compiled = root / 'build/classes/java/main'
if not (compiled / 'cn/academy/port/client/BrakingTouchEffects.class').is_file():
    raise RuntimeError('Compile production classes before verifying actual Post bytecode')
cached = sorted((root / '.gradle-user/caches/modules-2/files-2.1').rglob('*.jar'))
classpath = os.pathsep.join(str(p) for p in dict.fromkeys([guavas[-1], gsons[-1], native[-1], *asms, compiled, *cached]))
names = ['BrakingTouchInputRegressionTest', 'BrakingTouchVisualRegressionTest', 'BrakingTouchDispatchRegressionTest']
production = [root / 'src/main/java/cn/academy/port/client' / (name + '.java')
              for name in ['AcademyClient', 'BrakingTouchInputState', 'BrakingTouchState', 'BrakingTouchGeometry']]
with tempfile.TemporaryDirectory(prefix='new413-client-') as temporary:
    # Compile changed production bytecode here; never alter the shared Gradle output during native QA.
    subprocess.run([str(java / 'bin/javac'), '--release', '21', '-proc:none', '-encoding', 'UTF-8', '-d', temporary,
                    '-cp', classpath, '-sourcepath', os.pathsep.join(str(root / p) for p in ['src/main/java', 'src/test/java']),
                    *map(str, production), *[str(root / 'src/test/java/cn/academy/port/client' / (name + '.java')) for name in names]], check=True)
    for name in names:
        subprocess.run([str(java / 'bin/java'), '-ea', '-cp', temporary + os.pathsep + classpath, 'cn.academy.port.client.' + name], check=True)
print('NEW413 CPU regressions only; this does not establish native runtime or rendered visual quality')
