#!/usr/bin/env python3
"""Index the original appearance assets for the 100 ACA Story YSM v18.5.1 roles."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

RESOURCES = Path(__file__).resolve().parents[1] / "src/hum/resources/assets/acabiohum"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pack", type=Path, help="v18.5.1 modern Story YSM archive")
    parser.add_argument("--roles", nargs="+", help="Restrict the index for a rendering trial")
    args = parser.parse_args()
    profiles = {entry["id"] for entry in json.loads((RESOURCES / "characters.json").read_text())}
    roles = set(args.roles) if args.roles else profiles
    if missing := roles - profiles:
        parser.error(f"Unknown ACA roles: {', '.join(sorted(missing))}")

    with ZipFile(args.pack) as pack:
        models = {
            role: {
                "model": hashlib.sha256(pack.read(f"{role}/models/main.json")).hexdigest(),
                "texture": hashlib.sha256(pack.read(f"{role}/textures/default.png")).hexdigest(),
            }
            for role in sorted(roles)
        }
    target = RESOURCES / "story_ysm_v1851.json"
    target.write_text(json.dumps({"version": "v18.5.1", "models": models}, indent=2) + "\n", encoding="utf-8")
    print(f"{len(models)} roles indexed in {target}")


if __name__ == "__main__":
    main()
