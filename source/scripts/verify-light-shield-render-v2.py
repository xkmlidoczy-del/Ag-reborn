#!/usr/bin/env python3
"""Complete current Light Shield suite with the two reviewed .36 integrations.

All original functional/native assertions and mutation controls remain intact.
Only two current source pins and the explicit aggregate task route advance.
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


def adapted_runner():
    original = v2.pinned(ROOT, v2.SHIELD, v2.FROZEN[v2.SHIELD], 'Frozen runner changed').decode()
    substitutions = [
        ("'3870996a872c8f072508eb96850bd1cd90cc3693c7991084ccc73912465dfd9a'",
         "'e9cfa0d45a688f886c52f4c8190cd06a28d3866373e2b6aa72f40442e998e5b7'"),
        ("'4a8174c27d710c6765b99b050fb5538f867053dfabd85f9d8ed5b74a0dc7b1c9'",
         "'9961844c08f827dff49cf1e7bb1047e9cc2fe92888ec0ec1f032ccf61ba8cb6c'"),
    ]
    # The task assertions are complete literal statements from the frozen runner.
    # Keep their count and require the complete V2 route, not a successful no-op alias.
    old_railgun = next(line for line in original.splitlines() if "'aggregate Railgun integration task must run current grand superset runner'" in line)
    old_shield = next(line for line in original.splitlines() if "'aggregate Light Shield integration task must run this versioned complete runner'" in line)
    substitutions = substitutions[:2] + [
        (old_railgun, "    assert build.count(v2.ROUTE) == 1, 'aggregate Railgun integration task must require complete V2 execution'"),
        (old_shield, "    assert \"tasks.register('lightShieldVfx2IntegrationTest') { dependsOn 'railgunVfx2IntegrationTest' }\" in build, 'aggregate Light Shield task must require complete current suite'"),
        ("'railgunRunner': 'scripts/verify-railgun-grand-integration.py'", "'railgunRunner': 'scripts/verify-render-contract-v2.py'"),
        ("'lightShieldRunner': 'scripts/verify-light-shield-grand-integration.py'", "'lightShieldRunner': 'scripts/verify-light-shield-render-v2.py'"),
    ]
    # A new user request authorizes actual ranged gameplay, including the cannon's reach.
    # Keep the historic runner and every historic pin. The separate source proof checks
    # both the exact reach delta and the approved A2 terrain-only adapter.
    range_spec=importlib.util.spec_from_file_location('reviewed_ranged_delta',ROOT/'scripts/reviewed-ranged-delta-20261101.py')
    range_review=importlib.util.module_from_spec(range_spec);range_spec.loader.exec_module(range_review)
    substitutions += [
        ('historical, fixtures, reviewed = {}, {}, {}','historical, fixtures, reviewed, ranged = {}, {}, {}, {}'),
        ("            assert digest((root / path).read_bytes()) == expected_sha256, 'historical Railgun/closed Meltdowner guard changed: ' + path\n            historical[path] = expected_sha256",
         "            if path == range_review.TARGET and digest((root / path).read_bytes()) != expected_sha256:\n                ranged[path] = range_review.verify(root, expected_sha256)\n            else:\n                assert digest((root / path).read_bytes()) == expected_sha256, 'historical Railgun/closed Meltdowner guard changed: ' + path\n                historical[path] = expected_sha256"),
        ("    return {'unchangedHistoricalFiles': historical,","    return {'unchangedHistoricalFiles': historical, 'reviewedGameplayReachUpdate': ranged,"),
        ("paths = {*HISTORICAL_SHA256, *GRAND_INTEGRATION_FIXTURES.values(), FROZEN_LIGHT_SHIELD_RUNNER,",
         "paths = {*HISTORICAL_SHA256, *GRAND_INTEGRATION_FIXTURES.values(), FROZEN_LIGHT_SHIELD_RUNNER, range_review.FIXTURE, range_review.RULES, range_review.PREVIOUS_RULES, range_review.TUNING,"),
        ("        for label, name, expected in cases:","        cases += [('rangePredecessorCorruption',range_review.FIXTURE,'reviewed range predecessor changed'),('rangeTableCorruption',range_review.RULES,'reviewed range table changed'),('rangeDamageOutsideApprovedDelta',range_review.TARGET,'reviewed 20261101 ranged source changed')]\n        for label, name, expected in cases:"),
    ]
    changed = original
    for before, after in substitutions:
        changed = v2.replace_once(changed, before, after)
    restored = changed
    for before, after in reversed(substitutions):
        restored = v2.replace_once(restored, after, before)
    assert restored == original, 'Shield derivative changed unaudited source text'
    module = v2.load_frozen(v2.SHIELD)
    unchanged = {name: getattr(module, name) for name in ['HARNESS', 'PREVIEW_HARNESS', 'NATIVE_CONFIG']}
    historical, scope = dict(module.HISTORICAL_SHA256), dict(module.GRAND_INTEGRATION_FIXTURES)
    module.__dict__['v2'] = v2
    module.__dict__['range_review'] = range_review
    exec(compile(changed, str(ROOT / v2.SHIELD), 'exec'), module.__dict__)
    assert module.HISTORICAL_SHA256 == historical and module.GRAND_INTEGRATION_FIXTURES == scope, 'Original historical requirements or exception scope changed'
    assert set(scope) == set(v2.REVIEWED36) and len(scope) == 2, 'Only two reviewed integration files allowed'
    assert module.REVIEWED_GRAND_INTEGRATION_SHA256 == v2.REVIEWED36, 'Reviewed .36 pins changed'
    assert all(getattr(module, name) == value for name, value in unchanged.items()), 'Frozen Shield harness changed'
    return module, {'originalRunnerSha256': v2.FROZEN[v2.SHIELD], 'exactReviewedSubstitutions': len(substitutions),
                    'allOtherRunnerSourceTextUnchanged': True, 'historicalRequirementCount': len(historical),
                    'unchangedHarnessHashes': {name: v2.digest(value) for name, value in unchanged.items()}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-config-classpath', type=Path, required=True)
    parser.add_argument('--pinned-minecraft-sources', type=Path, required=True)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    inputs = v2.verify_inputs()
    runner, audit = adapted_runner()
    argv = sys.argv
    output = io.StringIO()
    try:
        sys.argv = [str(ROOT / v2.SHIELD), '--native-config-classpath', str(args.native_config_classpath.resolve()),
                    '--pinned-minecraft-sources', str(args.pinned_minecraft_sources.resolve())]
        with contextlib.redirect_stdout(output):
            runner.main()
    finally:
        sys.argv = argv
    report = json.loads(output.getvalue())
    report['contractVersion'] = 'light-shield-render-v2'
    report['runnerAudit'] = audit
    report['preserved35IntermediateHashes'] = {name: inputs['archival35SourceHashes'][name.removeprefix('src/main/java/')]
                                               for name in v2.REVIEWED36}
    report['reviewed36IntegrationHashes'] = v2.REVIEWED36
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
