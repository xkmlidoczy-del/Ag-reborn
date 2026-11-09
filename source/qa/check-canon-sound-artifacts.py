#!/usr/bin/env python3
"""Decode the 16 canon sound assets without opening a player or changing audio."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
import numpy as np

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--ffmpeg', type=Path, required=True)
p.add_argument('--out', type=Path, required=True)
a = p.parse_args()
s = Path(__file__).resolve().parents[1]
assets = s/'src/main/resources/assets/academy'
events = json.loads((assets/'sounds.json').read_text())
rows = []
for file in sorted((assets/'sounds/canon').glob('*.ogg')):
    if file.name.startswith('._'):
        continue
    decoded = subprocess.run([str(a.ffmpeg), '-v', 'error', '-i', str(file),
                              '-ac', '1', '-ar', '48000', '-f', 'f32le', 'pipe:1'],
                             capture_output=True)
    data = np.frombuffer(decoded.stdout, dtype='<f4')
    peak = float(np.max(np.abs(data))) if len(data) else 0.
    rms = float(np.sqrt(np.mean(data.astype(np.float64)**2))) if len(data) else 0.
    family, phase = file.stem.rsplit('_', 1)
    name, ref = 'canon.'+family+'.'+phase, 'academy:canon/'+file.stem
    wired = any(x == ref or isinstance(x, dict) and x.get('name') == ref
                for x in events.get(name, {}).get('sounds', []))
    rows.append({'name': name, 'file': str(file),
                 'sha256': hashlib.sha256(file.read_bytes()).hexdigest(),
                 'decodedSamples': len(data), 'seconds': len(data)/48000,
                 'peakAmplitude': peak, 'rmsAmplitude': rms,
                 'finite': bool(np.isfinite(data).all()), 'decodeExit': decoded.returncode,
                 'decodeErrors': decoded.stderr.decode(errors='replace'),
                 'eventReferencesExactArtifact': wired,
                 'pass': decoded.returncode == 0 and len(data) > 2400
                         and bool(np.isfinite(data).all()) and .001 < rms < 1
                         and peak <= 1 and wired})
report = {'pass': len(rows) == 16 and all(r['pass'] for r in rows),
          'clips': len(rows), 'artifacts': rows, 'decoder': str(a.ffmpeg.resolve()),
          'scope': 'Technical decoding and exact resource wiring only. No subjective hearing/comfort claim, no native audio capture, no sound resources changed.'}
a.out.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
print(json.dumps({'pass': report['pass'], 'clips': len(rows),
                  'failures': [r['name'] for r in rows if not r['pass']]}))
raise SystemExit(0 if report['pass'] else 1)
