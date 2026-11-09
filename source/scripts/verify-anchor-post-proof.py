#!/usr/bin/env python3
"""Exercise the real NEW422 Post receipt with isolated, explicit collaborators."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    sources = root / "src/test/resources/anchor-post-proof-fixture"
    proof = root / "src/main/java/cn/academy/port/highpower/AnchorDamagePostProof.java"
    binaries = {}
    for name in ("javac", "java"):
        candidate = str(args.java_home / "bin" / name) if args.java_home else shutil.which(name)
        if not candidate or not Path(candidate).is_file():
            parser.error(f"JDK 21 {name} is required; pass --java-home or set JAVA_HOME")
        binaries[name] = candidate
    with tempfile.TemporaryDirectory(prefix="academy-anchor-post-proof-") as temporary:
        subprocess.run(
            [binaries["javac"], "-proc:none", "-d", temporary,
             *map(str, sorted(sources.rglob("*.java"))), str(proof)],
            check=True,
        )
        subprocess.run(
            [binaries["java"], "-cp", temporary, "AnchorProofRuntimeCheck"],
            check=True,
        )


if __name__ == "__main__":
    main()
