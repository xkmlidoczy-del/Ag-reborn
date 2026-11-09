#!/usr/bin/env bash
set -euo pipefail

# No Minecraft, Gradle cache or network is required. Supply WIND_LIFT_JDK when
# javac is not on PATH. An optional first argument retains classes and logs there.
source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
wind_jdk="${WIND_LIFT_JDK:-${JAVA_HOME:-}}"
if [[ -n "$wind_jdk" ]]; then
    wind_javac="$wind_jdk/bin/javac"
    wind_java="$wind_jdk/bin/java"
else
    wind_javac=javac
    wind_java=java
fi
if [[ $# -gt 0 ]]; then
    evidence_root="$1"
    mkdir -p -- "$evidence_root"
else
    evidence_root="$(mktemp -d "${TMPDIR:-/tmp}/wind-pressure-lift-core.XXXXXX")"
    trap 'rm -rf -- "$evidence_root"' EXIT
fi
mkdir -p -- "$evidence_root/classes"
core="$source_root/src/main/java/cn/academy/port/highpower/core"
tests="$source_root/src/test/java/cn/academy/port/highpower/core"
"$wind_javac" --release 21 -d "$evidence_root/classes" \
    "$core/CombatAmounts.java" "$core/ContinuousHighPowerGrowth.java" \
    "$core/WindPressureLiftRules.java" "$core/WindPressureLiftGeometry.java" \
    "$tests/WindPressureLiftRulesRegressionTest.java" \
    "$tests/WindPressureLiftGeometryRegressionTest.java"
for test in WindPressureLiftRulesRegressionTest WindPressureLiftGeometryRegressionTest; do
    "$wind_java" -ea -cp "$evidence_root/classes" "cn.academy.port.highpower.core.$test" \
        | tee "$evidence_root/$test.log"
done
