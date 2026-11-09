#!/usr/bin/env python3
"""Traceable concatenation only: retain every original setup/input/assertion."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--check", action="store_true")
args = parser.parse_args()
base = Path(__file__).resolve().parent
sources = ["gameplay-vm-environment-" + mode + ".script" for mode in ["blast-block", "ground-wave", "ground-soft"]]
steps = []
for name in sources:
    original = (base / name).read_text().strip().split("|")
    assert original[-1] == "quit" and original.count("quit") == 1, name
    steps += ["mark:begin:" + Path(name).stem] + original[:-1] + ["mark:end:" + Path(name).stem]
combined = "|".join(steps + ["quit"]) + "\n"
target = base / "gameplay-vm-environment-legacy-three.script"
plan = [{"name": "vm-environment-legacy-three", "script": target.name, "sourceScripts": sources}]
for mode in ["storm-fragile", "plasma-terrain"]:
    script = "gameplay-vm-environment-" + mode + ".script"
    plan.append({"name": "vm-environment-" + mode, "script": script, "sourceScripts": [script]})
for case in plan:
    case["sourceSha256"] = {name: hashlib.sha256((base / name).read_bytes()).hexdigest() for name in case["sourceScripts"]}
encoded = json.dumps(plan, indent=2) + "\n"
plan_target = base / "gameplay-vm-environment-grouped-plan.json"
if args.check:
    assert target.read_text() == combined, "stale legacy terrain combination"
    assert plan_target.read_text() == encoded, "stale traceable grouped plan"
else:
    target.write_text(combined)
    plan_target.write_text(encoded)
print(("CHECK" if args.check else "GENERATED") + " five unchanged source scripts in three traced diagnostic cases")
