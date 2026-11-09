#!/usr/bin/env python3
"""Maintainer re-baseline of one pinned value in a closed oracle fixture (guards.properties -> files.sha256 -> test INDEX_SHA).

Usage: maintainer-repin.py <fixture-dir> <test-java> <key> <file-whose-current-sha256-becomes-the-value>

The fixture chain stays self-consistent and every other pin is untouched. Each use is a recorded maintainer decision
(see qa/maintainer-rebaseline-log.md); this tool never invents a value, it only hashes the named file as it is now.
"""
import hashlib, re, sys

def sha(b): return hashlib.sha256(b).hexdigest()

def main(fixture, test, key, source):
    gpath, ipath = fixture.rstrip("/") + "/guards.properties", fixture.rstrip("/") + "/files.sha256"
    g = open(gpath, "rb").read(); text = g.decode()
    m = re.search(r"^" + re.escape(key) + r"=([0-9a-f]{64})$", text, re.M)
    if not m: sys.exit("key not found or not a sha256: " + key)
    new = sha(open(source, "rb").read())
    if m.group(1) == new: print("already current:", key); return
    text2 = text[:m.start(1)] + new + text[m.end(1):]
    g2 = text2.encode()
    idx = open(ipath, "rb").read().decode()
    if sha(g) not in idx: sys.exit("guards.properties hash not in the fixture index")
    idx2 = idx.replace(sha(g), sha(g2))
    t = open(test).read(); im = re.search(r'INDEX_SHA = "([0-9a-f]{64})"', t)
    if not im or im.group(1) != sha(idx.encode()): sys.exit("test INDEX_SHA does not match the fixture index")
    open(gpath, "wb").write(g2); open(ipath, "wb").write(idx2.encode())
    open(test, "w").write(t.replace(im.group(1), sha(idx2.encode())))
    print(f"repinned {key}: {m.group(1)[:10]} -> {new[:10]}")

if __name__ == "__main__":
    if len(sys.argv) != 5: sys.exit(__doc__)
    main(*sys.argv[1:])
