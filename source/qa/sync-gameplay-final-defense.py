#!/usr/bin/env python3
"""Regenerate/check the two independent single-client defense flows; no gameplay launch."""
import argparse
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');args=p.parse_args()
base=Path(__file__).resolve().parent
steps=[]
for name in ['gameplay-em-md-defense-potential_buffer.script','gameplay-em-md-defense-magnetic_domain.script']:
    source=(base/name).read_text().strip().split('|')
    assert source[-1]=='quit' and source.count('quit')==1,name
    steps+=['mark:begin:'+Path(name).stem]+source[:-1]+['mark:end:'+Path(name).stem]
expected='|'.join(steps+['quit'])+'\n'
target=base/'gameplay-final-em-defense-rest.script'
if args.check:
    assert target.read_text()==expected,'STALE final defense rest; regenerate with this script'
else:
    target.write_text(expected)
print(('CHECK' if args.check else 'GENERATED')+' current independent potential/domain flows, all input and assertions retained except intermediate quit')
