#!/usr/bin/env python3
"""Admit local Academy QA jobs according to their JVM, memory and CPU budgets."""
import argparse
from contextlib import contextmanager
from datetime import datetime
import fcntl
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time


def process_usage():
    output = subprocess.check_output(["ps", "-ww", "-axo", "pid=,ppid=,rss=,pcpu=,comm="], text=True)
    parents, java, busy = {}, {}, 0.0
    for row in output.splitlines():
        fields = row.strip().split(None, 4)
        if len(fields) != 5:
            continue
        pid, parent, rss, cpu, executable = fields
        pid = int(pid)
        parents[pid] = int(parent)
        busy += float(cpu) / 100
        if Path(executable).name in {"java", "javac", "jar"}:
            java[pid] = int(rss) / 1024
    return parents, java, busy


def memory_status():
    if sys.platform == "darwin":
        pressure, total = subprocess.check_output(
            ["sysctl", "-n", "kern.memorystatus_vm_pressure_level", "hw.memsize"], text=True
        ).split()
        result = subprocess.run(["/usr/bin/memory_pressure", "-Q"], capture_output=True, text=True)
        match = re.search(r"free percentage:\s*(\d+)%", result.stdout)
        available = int(total) * int(match[1]) // (100 * 1024**2) if match else None
        return int(pressure) <= 1, available
    meminfo = Path("/proc/meminfo")
    if meminfo.exists():
        match = re.search(r"^MemAvailable:\s+(\d+)", meminfo.read_text(), re.M)
        if match:
            return True, int(match[1]) // 1024
    return True, None


def active_leases(directory):
    active = {}
    for path in directory.glob("*.lease"):
        with path.open("r") as entry:
            try:
                fcntl.flock(entry, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                job = json.load(entry)
                active[job["pid"]] = job
            else:
                # A closed lease is also recoverable after a terminated launcher.
                path.unlink()
    return active


def supervisor_of(pid, parents, supervisors):
    while pid > 1 and pid not in supervisors:
        pid = parents.get(pid, 0)
    return pid if pid in supervisors else None


def fits(jobs, args, usage, memory):
    normal, available = memory
    if not normal:
        return False
    parents, jvms, busy = usage
    allocated = {pid: 0.0 for pid in jobs}
    external = 0
    for pid, rss in jvms.items():
        ancestor = supervisor_of(pid, parents, jobs)
        if ancestor is not None:
            allocated[ancestor] += rss
        else:
            external += 1
    host_limit = min([args.max_jvms, *(j.get("limit", args.max_jvms) for j in jobs.values())])
    if external + sum(j["jvms"] for j in jobs.values()) + args.jvms > host_limit:
        return False
    if getattr(args, "quiet_java", False) and external:
        return False
    # Reserve CPU for the desktop. A sample may briefly exceed the threshold
    # during a launch, so use this check when adding to existing Java work.
    cpu_budget = max(1, (os.cpu_count() or 1) * 2 // 3)
    promised = sum(j["cores"] for j in jobs.values())
    if (jobs or external) and max(promised, busy) + args.cores > cpu_budget:
        return False
    if available is None:
        return not jobs  # A lone job can run when the host has no headroom API.
    pending = sum(max(0, j["mib"] - allocated[pid]) for pid, j in jobs.items())
    desktop_mib = 3072 if jobs or external else 1024
    return available >= args.memory_mib + pending + desktop_mib


@contextmanager
def reservation(args):
    prefix = Path(tempfile.gettempdir()) / f"academy-java-qa-{os.getuid()}"
    prefix.mkdir(mode=0o700, exist_ok=True)
    # The lifetime lock is shared for normal jobs and exclusive for benchmarks.
    with prefix.with_suffix(".lock").open("a") as group, (prefix/"admission.lock").open("a") as admission:
        group_mode = fcntl.LOCK_EX if args.exclusive else fcntl.LOCK_SH
        while True:
            # Recheck shared admission after a long exclusive run. Darwin can
            # leave a compatible reader queued behind the first woken reader.
            try:
                fcntl.flock(group, group_mode | fcntl.LOCK_NB)
            except BlockingIOError:
                time.sleep(0.5)
                continue
            fcntl.flock(admission, fcntl.LOCK_EX)
            try:
                jobs = active_leases(prefix)
                usage, memory = process_usage(), memory_status()
                if fits(jobs, args, usage, memory):
                    lease = tempfile.NamedTemporaryFile(mode="w", dir=prefix, suffix=".lease", delete=False)
                    with lease:
                        fcntl.flock(lease, fcntl.LOCK_EX)
                        json.dump(dict(pid=os.getpid(), jvms=args.jvms, limit=args.max_jvms, mib=args.memory_mib, cores=args.cores), lease)
                        lease.flush()
                        # Retain a descriptor for the lifetime of both the launcher and the direct supervisor.
                        handle = os.dup(lease.fileno())
                    break
            finally:
                fcntl.flock(admission, fcntl.LOCK_UN)
            fcntl.flock(group, fcntl.LOCK_UN)
            time.sleep(2)
        try:
            yield (group.fileno(), handle), usage, memory[1]
        finally:
            os.close(handle)


def now():
    return datetime.now().astimezone().isoformat(timespec="milliseconds")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--jvms", type=int, required=True)
    p.add_argument("--max-jvms", type=int, default=3, help="Total local JDK process budget")
    p.add_argument("--memory-mib", type=int, help="Expected combined resident MiB, including native memory")
    p.add_argument("--cores", type=int, help="Expected aggregate CPU cores for this job")
    p.add_argument("--exclusive", action="store_true", help="Run the scenario alone for comparable timings")
    p.add_argument("--quiet-java", action="store_true", help="Start a timed comparison with no outside JDK processes")
    p.add_argument("--log", type=Path)
    p.add_argument("command", nargs=argparse.REMAINDER)
    args = p.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if args.memory_mib is None:
        args.memory_mib = 2048 * args.jvms
    if args.cores is None:
        args.cores = 2 * args.jvms
    if not command or not 1 <= args.jvms <= args.max_jvms or min(args.memory_mib, args.cores) < 1:
        p.error("Provide a command and positive reservations within the host JVM budget.")

    with reservation(args) as (handles, initial, available):
        started = now()
        peak_processes, peak_mib = len(initial[1]), sum(initial[1].values())
        peak_own, peak_other = 0, len(initial[1])
        env = dict(os.environ, ACADEMY_LOCAL_JAVA_SLOT="1")
        with subprocess.Popen(command, env=env, pass_fds=handles) as process:
            while True:
                parents, jvms, _ = process_usage()
                owned = sum(supervisor_of(pid, parents, {os.getpid()}) is not None for pid in jvms)
                peak_own = max(peak_own, owned)
                peak_other = max(peak_other, len(jvms) - owned)
                peak_processes = max(peak_processes, len(jvms))
                peak_mib = max(peak_mib, sum(jvms.values()))
                try:
                    code = process.wait(timeout=2)
                    break
                except subprocess.TimeoutExpired:
                    pass
        if args.log:
            python_command = Path(command[0]).name.startswith("python") and len(command) > 1
            measurement = dict(
                started=started, end=now(), scenario=Path(command[1] if python_command else command[0]).name,
                reserved=args.jvms, limit=args.max_jvms, exclusive=args.exclusive, requestedMiB=args.memory_mib,
                observedPeakOwnJvms=peak_own, observedPeakOtherJvms=peak_other,
                requestedCores=args.cores, availableMiBAtStart=available, alreadyRunning=len(initial[1]),
                observedPeakJvms=peak_processes, observedPeakRssMiB=round(peak_mib), exitCode=code,
            )
            with args.log.open("a", encoding="utf-8") as log:
                fcntl.flock(log, fcntl.LOCK_EX)
                log.write(json.dumps(measurement) + "\n")
                log.flush()
        return code


if __name__ == "__main__":
    raise SystemExit(main())
