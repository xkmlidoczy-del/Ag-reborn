#!/usr/bin/env python3
"""Replay every frozen .35 recording check using explicit archive provenance.

Only four exact source-loading substitutions are permitted. No production Java,
recording assertion, harness, shader-profile check, or mutation is rewritten.
"""
from pathlib import Path
import argparse
import contextlib
import importlib.util
import io
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('render_contract_v2', ROOT / 'scripts/verify-render-contract-v2.py')
v2 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v2)


def archive_sources(root, names):
    near = v2.load_frozen(v2.NEAR, root)
    assert set(names) == set(near.BASELINE), 'Archival replay must compile the exact complete .35 graph'
    return {name: v2.pinned(root, v2.BASE35 + '/' + name + '.txt', near.BASELINE[name], 'Archived .35 graph changed').decode()
            for name in names}


def adapted_runner():
    original = v2.pinned(ROOT, v2.GRAND, v2.FROZEN[v2.GRAND], 'Frozen runner changed').decode()
    substitutions = [
        ("    sources = {name: (java_root / name).read_text() for name in names}",
         "    sources = archive_sources(root, names)"),
        ("    baseline = run(['git', 'show', BASELINE + ':src/main/java/cn/academy/port/client/ClassicEffects.java'], cwd=root)",
         "    baseline = v2.pinned(root, v2.PRE31, v2.PRE31_SHA, 'Reconstructed pre31 fixture changed').decode()"),
        ("        original = subprocess.run(['git', 'show', BASELINE + ':' + path], cwd=root, capture_output=True)\n"
         "        assert original.returncode == 0, 'immutable guard missing from recorded baseline: ' + path\n"
         "        assert original.stdout == (root / path).read_bytes(), 'historical guard weakened or changed: ' + path\n"
         "        historical[path] = hashlib.sha256(original.stdout).hexdigest()",
         "        archived = (root / v2.FIXTURES / 'guards' / Path(path).name).read_bytes()\n"
         "        assert hashlib.sha256(archived).hexdigest() == v2.GUARDS[path], 'immutable archive guard hash changed: ' + path\n"
         "        assert archived == (root / path).read_bytes(), 'historical guard weakened or changed: ' + path\n"
         "        historical[path] = hashlib.sha256(archived).hexdigest()"),
        ("                 java_root / 'cn/academy/port/ClassicHudConfig.java', config_test])",
         "                 generated / 'cn/academy/port/ClassicHudConfig.java', config_test])"),
    ]
    changed = original
    for before, after in substitutions:
        changed = v2.replace_once(changed, before, after)
    # Inverting the enumerated substitutions proves no other source text changed.
    restored = changed
    for before, after in reversed(substitutions):
        restored = v2.replace_once(restored, after, before)
    assert restored == original, 'Archival derivative changed unaudited source text'
    module = v2.load_frozen(v2.GRAND)
    unchanged = {name: getattr(module, name) for name in ['HARNESS', 'PREVIEW_HARNESS', 'GRAND_HARNESS', 'NATIVE_CONFIG']}
    module.__dict__.update(v2=v2, archive_sources=archive_sources)
    exec(compile(changed, str(ROOT / v2.GRAND), 'exec'), module.__dict__)
    assert all(getattr(module, name) == value for name, value in unchanged.items()), 'Frozen harness or native config changed'
    return module, {'originalRunnerSha256': v2.FROZEN[v2.GRAND], 'exactLoadingSubstitutions': len(substitutions),
                    'allOtherRunnerSourceTextUnchanged': True,
                    'unchangedHarnessHashes': {name: v2.digest(value) for name, value in unchanged.items()}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-config-classpath', type=Path, required=True)
    parser.add_argument('--pinned-minecraft-sources', type=Path, required=True)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    inputs = v2.verify_inputs()
    assert args.native_config_classpath.is_file(), 'Archival native config classpath is mandatory'
    runner, audit = adapted_runner()
    argv = sys.argv
    output = io.StringIO()
    try:
        sys.argv = [str(ROOT / v2.GRAND), '--native-config-classpath', str(args.native_config_classpath.resolve()),
                    '--pinned-minecraft-sources', str(args.pinned_minecraft_sources.resolve())]
        with contextlib.redirect_stdout(output):
            runner.main()
    finally:
        sys.argv = argv
    report = json.loads(output.getvalue())
    assert report['nativeConfig']['result'] == 'PASS', 'Archival native config must execute'
    report['contractVersion'] = 'railgun-grand-archive-v1'
    report['archivalSourceHashes'] = report.pop('productionSourceHashes')
    report['actualArchivalClasses'] = report.pop('actualProductionClasses')
    report['historicalCommitIdentifierWithoutGitProof'] = report.pop('baselineCommit')
    report['nativeConfig']['archivalClassicHudConfigCompiled'] = report['nativeConfig'].pop('actualClassicHudConfig')
    report['archiveProvenance'] = inputs['reconstruction']
    report['runnerAudit'] = audit
    report['currentProductionCoverage'] = False
    report['limitations'].insert(0, 'This is the complete archival .35 replay against patch-reconstructed pre-.31 bytes, not a current-code or original Git differential')
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
