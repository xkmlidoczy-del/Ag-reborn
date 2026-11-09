#!/usr/bin/env python3
"""Deterministic, byte-preserving visual import from the 1.0.7 source checkout.

No music or song-cover bytes are imported. Old OBJ, LambdaLib GUI XML, and GLSL
remain reference-only; their presence must never be advertised as runtime support.
Run from any directory; --check verifies the recorded import without writing.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT / ".reference/AcademyCraft-1.0.7"
SOURCE = UPSTREAM / "src/main/resources/assets/academy"
RUNTIME = ROOT / "src/main/resources/assets/academy"
REFERENCE = ROOT / "docs/reference-visuals"
MANIFEST = REFERENCE / "asset-manifest.json"
SONG_NAMES = ("only_my_railgun", "level5_judgelight", "sisters_noise")


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def excluded(path: Path) -> bool:
    relative = path.relative_to(SOURCE)
    return relative.parts[0] == "media" or any(name in str(relative) for name in SONG_NAMES)


def png_white_pixel() -> bytes:
    """Generate our own opaque white pixel for the untextured core geometry."""
    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", 1, 1, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\x00\xff\xff\xff\xff")) + chunk(b"IEND", b""))


def entries() -> tuple[list[dict], list[dict]]:
    records, omitted = [], []
    if not SOURCE.is_dir():
        raise SystemExit(f"Missing classic source checkout: {SOURCE}")
    for source in sorted(p for p in SOURCE.rglob("*") if p.is_file()):
        relative = source.relative_to(SOURCE)
        if excluded(source):
            omitted.append({"path": relative.as_posix(), "reason": "media/music/song-cover exclusion"})
            continue
        if relative.parts[0] in {"textures", "sounds"}:
            target, status = RUNTIME / relative, "runtime-resource"
        elif relative.parts[0] in {"models", "shaders", "guis"} or relative == Path("sounds.json"):
            target, status = REFERENCE / "assets/academy" / relative, "reference-only"
        else:
            continue
        data = source.read_bytes()
        records.append({"source": source.relative_to(ROOT).as_posix(),
                        "destination": target.relative_to(ROOT).as_posix(),
                        "sha256": digest(data), "bytes": len(data), "status": status,
                        "data": data})
    # Preserve source headers and exact rendering algorithms for later comparison.
    visual_sources = [
        "cn/academy/core/entity/EntityRayBase.java",
        "cn/academy/core/client/render/ray/RendererRayComposite.java",
        "cn/academy/core/client/render/ray/RendererRayCylinder.java",
        "cn/academy/core/client/render/ray/RendererRayGlow.java",
        "cn/academy/core/client/render/ray/RendererRayBaseGlow.java",
        "cn/academy/vanilla/electromaster/entity/EntityRailgunFX.java",
        "cn/academy/vanilla/electromaster/client/effect/RailgunHandEffect.java",
        "cn/academy/vanilla/electromaster/client/effect/ArcFactory.java",
        "cn/academy/vanilla/electromaster/client/effect/SubArcHandler.java",
        "cn/academy/vanilla/electromaster/client/effect/SubArc.java",
        "cn/academy/vanilla/electromaster/client/effect/ArcPatterns.java",
        "cn/academy/vanilla/electromaster/entity/EntityArc.java",
    ]
    for name in visual_sources:
        source = UPSTREAM / "src/main/java" / name
        data = source.read_bytes()
        target = REFERENCE / "source" / name
        records.append({"source": source.relative_to(ROOT).as_posix(),
                        "destination": target.relative_to(ROOT).as_posix(),
                        "sha256": digest(data), "bytes": len(data), "status": "reference-only",
                        "data": data})
    source = UPSTREAM / "src/main/scala/cn/academy/vanilla/electromaster/skill/ArcGen.scala"
    data = source.read_bytes()
    records.append({"source": source.relative_to(ROOT).as_posix(),
                    "destination": (REFERENCE / "source/cn/academy/vanilla/electromaster/skill/ArcGen.scala").relative_to(ROOT).as_posix(),
                    "sha256": digest(data), "bytes": len(data), "status": "reference-only", "data": data})
    return records, omitted


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    records, omitted = entries()
    # Sound asset references must be explicitly academy namespaced in modern MC.
    # Preserve the exact old JSON above; this is an intentional format adaptation.
    sound_defs = json.loads((SOURCE / "sounds.json").read_text())
    for event in sound_defs.values():
        event.pop("category", None)
        for sound in event["sounds"]:
            if isinstance(sound, str):
                raise SystemExit("Review new string sound references before migrating")
            if ":" not in sound["name"]:
                sound["name"] = "academy:" + sound["name"]
            if any(name in sound["name"] for name in SONG_NAMES) or "/media/" in sound["name"]:
                raise SystemExit("Refusing a music/cover reference in sounds.json")
    generated = {
        "src/main/resources/assets/academy/sounds.json":
            (json.dumps(sound_defs, indent=2, ensure_ascii=False) + "\n").encode(),
        "src/main/resources/assets/academy/textures/port/white.png": png_white_pixel(),
    }
    manifest = {"upstream": "AcademyCraft 1.0.7 / Lambda Innovation",
                "notice": "See docs/UPSTREAM-README-1.0.7.md; upstream declares GPLv3 plus additional restrictions.",
                "files": [{k: v for k, v in record.items() if k != "data"} for record in records],
                "adapted_or_generated": [{"destination": p, "sha256": digest(data), "bytes": len(data)}
                                         for p, data in generated.items()], "excluded": omitted}
    outputs = {record["destination"]: record["data"] for record in records} | generated
    outputs[MANIFEST.relative_to(ROOT).as_posix()] = (json.dumps(manifest, indent=2) + "\n").encode()
    # Never remove arbitrary destination files; fail if excluded media leaked in.
    leaked = [str(p.relative_to(ROOT)) for base in (RUNTIME, REFERENCE) for p in base.rglob("*")
              if p.is_file() and ("media" in p.relative_to(base).parts
                                 or any(name in str(p) for name in SONG_NAMES))]
    if leaked:
        raise SystemExit("Excluded media exists in destination: " + ", ".join(leaked))
    for name, data in outputs.items():
        target = ROOT / name
        if args.check:
            if not target.is_file() or target.read_bytes() != data:
                raise SystemExit(f"Asset differs or is missing: {name}")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    charge = RUNTIME / "textures/effects/arc_burst"
    for frame in range(40):
        if not (charge / f"{frame}.png").is_file():
            raise SystemExit(f"Missing charge animation frame {frame}")
    print(f"{'Verified' if args.check else 'Imported'} {len(records)} exact-byte files, "
          f"{len(generated)} adapted/generated files; excluded {len(omitted)} media/song files")


if __name__ == "__main__":
    main()
