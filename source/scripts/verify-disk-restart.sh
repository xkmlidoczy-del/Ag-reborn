#!/usr/bin/env bash
# Bounded two-process probe. Requires the documented restartGameTest Gradle run.
set -euo pipefail
cd "$(dirname "$0")/.."
if (($# > 1)); then
  echo 'Usage: scripts/verify-disk-restart.sh [fresh run-restart* directory name]' >&2
  exit 2
fi
run_dir="${1:-run-restart-$(date -u +%Y%m%dT%H%M%SZ)-$$}"
if [[ ! "$run_dir" =~ ^run-restart[A-Za-z0-9_-]*$ ]]; then
  echo 'Use a single disposable run-restart* directory name, without path separators' >&2
  exit 2
fi
if [[ -e "$run_dir" || -L "$run_dir" ]]; then
  echo "Refusing existing path: $run_dir. Choose a fresh disposable directory; no saves will be deleted." >&2
  exit 2
fi
mkdir -- "$run_dir"
for phase in seed verify; do
  echo "Running actual JVM phase: $phase, isolated directory: $run_dir"
  timeout --signal=TERM --kill-after=30s 240s \
    scripts/gradle-cloud.sh runRestartGameTest --console=plain \
      "-PrestartDirectory=$run_dir" "-PrestartPhase=$phase" \
    2>&1 | tee "$run_dir/$phase-gradle.log"
  grep -Fq 'All 1 required tests passed :)' "$run_dir/$phase-gradle.log"
  if [[ -f "$run_dir/logs/latest.log" ]]; then
    cp -- "$run_dir/logs/latest.log" "$run_dir/$phase-minecraft.log"
  fi
  if [[ "$phase" == seed ]]; then
    grep -Fq 'ACADEMY_RESTART seed actual graceful-stop/final native save certified' "$run_dir/seed-gradle.log"
    grep -Fq 'ACADEMY_RESTART seed actual closed-world Anvil energy save certified' "$run_dir/seed-gradle.log"
    test -s "$run_dir/academy-restart-world/region/r.2.2.mca"
    test -s "$run_dir/academy-restart-seed-proof.nbt"
    test -s "$run_dir/academy-restart-world/playerdata/6c7c7918-557c-4f9a-9426-88dd520dc516.dat"
  else
    grep -Fq 'ACADEMY_RESTART verify native disk/new-JVM reload passed' "$run_dir/verify-gradle.log"
    grep -Fq 'ACADEMY_RESTART verify native Fusor cold-load, nonpersisted-progress reset and conserved complete job passed' "$run_dir/verify-gradle.log"
    grep -Fq 'ACADEMY_RESTART verify native Anvil/new-JVM energy reload and finite solar recharge passed' "$run_dir/verify-gradle.log"
    test -s "$run_dir/academy-restart-verified.nbt"
  fi
done
echo "PASS: two native Minecraft JVM phases, disk reload, graceful coin refund, bounded ticks, energy units, both eight-cell developer tiers, finite solar recharge and a conserved cold-reloaded Fusor job. Evidence retained in $run_dir"
