#!/usr/bin/env python3
"""Keep the raw Gradle verdict; compare historical failure names and root evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import re

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('baseline', type=Path)
p.add_argument('current', type=Path)
p.add_argument('output', type=Path)
a = p.parse_args()
fingerprints = {
    'autonomousVectorCircleCurrentLearningV20': 'c334bbc6583d991bcd4bfa28de4312213d156089',
    'autonomousVectorCircleNativeRegistrationV21': 'c334bbc6583d991bcd4bfa28de4312213d156089',
    'scatterLocatorLearningProofV18': '27ecc8d869ae6389253c2e5c8fe4df68b6291424',
    'scatterLocatorNativeRegistrationProofV19': '27ecc8d869ae6389253c2e5c8fe4df68b6291424',
    'scatterLocatorRegistrationProofV17': '27ecc8d869ae6389253c2e5c8fe4df68b6291424',
    'chargingM41CoreProof': 'aca-original-m41/academycraft-m41-meltdowner-camera-source.zip',
    'chargingM41CoreProofV14': 'aca-original-m41/academycraft-m41-meltdowner-camera-source.zip',
    'classicMeltdownerGeometryTest': 'Official merged/review API archive guard changed',
    'currentMeltdownerGeometryProof': 'aca-gradle-cache',
    'currentThunderBoltGeometryProof': 'aca-original-m41',
    'railThunderArrayV55FallbackProof': 'aca-tools',
    'windPressureLiftFlowV72FixtureProof': 'Restoring only the isolated batch',
    'windPressureLiftFlowV72Proof': 'VanillaCombatContract may change only exact reviewed artifact version',
    'zeroGapArmorFrozenFallbackTest': 'FROZEN_CAPTURE_CHANGED',
}

def read(path):
    text = path.read_text(errors='replace')
    chunks = re.split(r'^> Task :', text, flags=re.M)[1:]
    failed = {c.splitlines()[0].split()[0]: c for c in chunks if c.splitlines()[0].endswith(' FAILED')}
    return text, failed

baseline, old = read(a.baseline)
current, new = read(a.current)
rows = {name: {'fingerprint': token, 'baseline': token in old.get(name, ''),
               'current': token in new.get(name, '')} for name, token in fingerprints.items()}
report = {'rawGradleResult': 'FAILED' if 'BUILD FAILED' in current else 'UNKNOWN',
          'baseline': str(a.baseline.resolve()), 'current': str(a.current.resolve()),
          'logSha256': hashlib.sha256(a.current.read_bytes()).hexdigest(),
          'baselineFailures': sorted(old), 'currentFailures': sorted(new),
          'newFailures': sorted(new.keys() - old.keys()),
          'removedFailures': sorted(old.keys() - new.keys()), 'fingerprints': rows}
report['historicalFailuresOnly'] = set(new) == set(old) == set(fingerprints) and all(
    row['baseline'] and row['current'] for row in rows.values())
a.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({k: report[k] for k in ('rawGradleResult', 'historicalFailuresOnly', 'newFailures')}))
raise SystemExit(0 if report['historicalFailuresOnly'] else 1)
