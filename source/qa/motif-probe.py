#!/usr/bin/env python3
"""Builds a showcase script that casts each given skill and screenshots nine moments (three while held, six after release).
Usage: qa/motif-probe.py <prefix> skill [skill ...]  -> prints the script on stdout (feed it to qa/run-showcase.sh).
MOTIF_CAM=third|front|first picks the camera (default third: behind the caster, so effects fly away from the lens);
MOTIF_AIM="x,y,z" is the point the caster looks at (default a few degrees down so ground effects land about 9 blocks ahead)."""
import os
import sys

CAM = os.environ.get("MOTIF_CAM", "first")
AIM = os.environ.get("MOTIF_AIM", "0.5,-60.2,9.5")
# synthetic caster placement: distance ahead of the camera, lateral offset, pitch and yaw in degrees (see MotifDirector.qaPose)
PLACE = os.environ.get("MOTIF_PLACE", "5.5:-2.2:-9:35")
prefix, skills = sys.argv[1], sys.argv[2:]
head = "cmd:time set day|cmd:gamerule doMobSpawning false|cmd:difficulty normal|keyup:82|keyup:340|tree:close"
# (age, since) of the nine shots: three while held, six after release
MOMENTS = [(5, -1), (10, -1), (16, -1), (15, 1), (17, 3), (20, 6), (24, 10), (32, 18), (44, 30)]
steps = [head]
for s in skills:
    n = f"{prefix}_{s}"
    steps += ["cmd:tp @s 0.5 -60 0.5 0 0|key:86|wait:12", f"live:setup:{s}|wait:26|live:target:front|wait:2|cam:{CAM}|aim:{AIM}|live:clearui"]
    for k, (age, since) in zip("abcdefghi", MOMENTS):
        steps += [f"motif:{s}:{age}:{since}:{PLACE}", f"shot:{n}_{k}"]
    steps += ["motif:off|keyup:82|keyup:340|tree:close|wait:6"]
steps.append("quit")
print("|".join(steps))
