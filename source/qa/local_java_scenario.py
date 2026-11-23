#!/usr/bin/env python3
"""Run one Academy QA scenario at a time on a local POSIX host."""
import argparse
import datetime
import fcntl
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time


def java_usage():
    listing = subprocess.check_output(["ps", "-ww", "-axo", "rss=,comm="], text=True)
    rss = []
    for line in listing.splitlines():
        fields = line.strip().split(None, 1)
        if len(fields) == 2 and Path(fields[1]).name in {"java", "javac", "jar"}:
            rss.append(int(fields[0]))
    return len(rss), sum(rss)


def normal_memory_pressure():
    if sys.platform != "darwin":
        return True
    value = subprocess.run(["sysctl", "-n", "kern.memorystatus_vm_pressure_level"],
                           text=True, capture_output=True)
    return value.returncode != 0 or value.stdout.strip() in ("0", "1")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jvms", type=int, required=True, help="Simultaneous JVMs needed by this scenario")
    parser.add_argument("--max-jvms", type=int, required=True, help="Host budget, including other running Java processes")
    parser.add_argument("--log", type=Path)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command or not 1 <= args.jvms <= args.max_jvms:
        parser.error("Provide a command and reserve between one JVM and the host budget.")
    lock = Path(tempfile.gettempdir()) / f"academy-java-qa-{os.getuid()}.lock"
    with lock.open("a") as owner:
        fcntl.flock(owner, fcntl.LOCK_EX)
        existing, peak_kib = java_usage()
        while existing + args.jvms > args.max_jvms or not normal_memory_pressure():
            time.sleep(3)
            existing, peak_kib = java_usage()
        started = datetime.datetime.now().astimezone().isoformat(timespec="seconds")
        peak_processes = existing
        environment = dict(os.environ, ACADEMY_LOCAL_JAVA_SLOT="1")
        # The direct scenario supervisor keeps the lock if its outer launcher exits.
        with subprocess.Popen(command, env=environment, pass_fds=(owner.fileno(),)) as process:
            while True:
                count, rss = java_usage()
                peak_processes, peak_kib = max(peak_processes, count), max(peak_kib, rss)
                try:
                    code = process.wait(timeout=4)
                    break
                except subprocess.TimeoutExpired:
                    pass
        if args.log:
            python_command = Path(command[0]).name.startswith("python") and len(command) > 1
            started_command = Path(command[1] if python_command else command[0]).name
            measurement = {
                "started": started,
                "end": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
                "scenario": started_command,
                "reserved": args.jvms,
                "limit": args.max_jvms,
                "alreadyRunning": existing,
                "observedPeakJvms": peak_processes,
                "observedPeakRssMiB": round(peak_kib / 1024),
                "exitCode": code,
            }
            with args.log.open("a", encoding="utf-8") as log:
                log.write(json.dumps(measurement) + "\n")
        return code


if __name__ == "__main__":
    raise SystemExit(main())
