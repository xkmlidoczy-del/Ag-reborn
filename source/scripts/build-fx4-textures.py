#!/usr/bin/env python3
"""Build 68 original FX4 staging textures. No runtime wiring or existing art changes.

From the project root:
  .venv/bin/python source/scripts/build-fx4-textures.py
  .venv/bin/python source/scripts/build-fx4-textures.py --out /path/to/rebuild
Dependencies: source/scripts/fx4/requirements.txt. No network/downloads required.
"""
import argparse
from pathlib import Path
import sys

sys.path.insert(0,str(Path(__file__).resolve().parent/'fx4'))
from fx4lib import OUT
import core, tech, air, materials

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--out',type=Path,default=OUT)
 a=p.parse_args()
 for module in [core,tech,air,materials]:
  module.build(a.out)
 print(f'Generated 68 FX4 PNGs in {a.out.resolve()}')

if __name__=='__main__': main()
