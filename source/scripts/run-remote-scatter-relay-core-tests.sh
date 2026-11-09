#!/usr/bin/env bash
set -euo pipefail

# Pure NEW253 production classes; no Minecraft, Gradle cache or network is required.
# Set REMOTE_RELAY_JDK when Java 21 is not on PATH. An optional argument retains evidence.
source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
relay_jdk="${REMOTE_RELAY_JDK:-${JAVA_HOME:-}}"
if [[ -n "$relay_jdk" ]]; then
    relay_javac="$relay_jdk/bin/javac"
    relay_java="$relay_jdk/bin/java"
else
    relay_javac=javac
    relay_java=java
fi
if [[ $# -gt 0 ]]; then
    evidence_root="$1"
    mkdir -p -- "$evidence_root"
else
    evidence_root="$(mktemp -d "${TMPDIR:-/tmp}/remote-scatter-relay-core.XXXXXX")"
    trap 'rm -rf -- "$evidence_root"' EXIT
fi
mkdir -p -- "$evidence_root/classes"
core="$source_root/src/main/java/cn/academy/port/highpower/core"
tests="$source_root/src/test/java/cn/academy/port/highpower/core"
"$relay_javac" --release 21 -d "$evidence_root/classes" \
    "$core/CombatAmounts.java" "$core/ContinuousHighPowerGrowth.java" \
    "$core/RemoteScatterRelayRules.java" "$core/RemoteScatterRelayGeometry.java" \
    "$tests/RemoteScatterRelayRulesRegressionTest.java" \
    "$tests/RemoteScatterRelayGeometryRegressionTest.java"
for test in RemoteScatterRelayRulesRegressionTest RemoteScatterRelayGeometryRegressionTest; do
    "$relay_java" -ea -cp "$evidence_root/classes" "cn.academy.port.highpower.core.$test" \
        | tee "$evidence_root/$test.log"
done
