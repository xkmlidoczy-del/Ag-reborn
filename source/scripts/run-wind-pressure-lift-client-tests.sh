#!/usr/bin/env bash
set -euo pipefail

# Engine-free production helper tests. Guava is used only by the unchanged classic input coordinator.
# Supply WIND_LIFT_JDK and WIND_LIFT_GUAVA if Java 21 / the cached Guava jar are not available.
source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
wind_jdk="${WIND_LIFT_JDK:-${JAVA_HOME:-}}"
if [[ -n "$wind_jdk" ]]; then
    wind_javac="$wind_jdk/bin/javac"
    wind_java="$wind_jdk/bin/java"
else
    wind_javac=javac
    wind_java=java
fi
wind_guava="${WIND_LIFT_GUAVA:-}"
if [[ -z "$wind_guava" ]]; then
    shopt -s nullglob
    jars=("$source_root"/.gradle-user/caches/modules-2/files-2.1/com.google.guava/guava/*/*/guava-*.jar)
    if [[ ${#jars[@]} -eq 0 ]]; then
        echo 'Set WIND_LIFT_GUAVA to the installed Guava jar; this runner does not download dependencies.' >&2
        exit 2
    fi
    wind_guava="${jars[0]}"
fi
if [[ $# -gt 0 ]]; then
    evidence_root="$1"
    mkdir -p -- "$evidence_root"
else
    evidence_root="$(mktemp -d "${TMPDIR:-/tmp}/wind-pressure-lift-client.XXXXXX")"
    trap 'rm -rf -- "$evidence_root"' EXIT
fi
mkdir -p -- "$evidence_root/classes"
main="$source_root/src/main/java/cn/academy/port"
tests="$source_root/src/test/java/cn/academy/port/client"
"$wind_javac" --release 21 -cp "$wind_guava" -d "$evidence_root/classes" \
    "$main/highpower/core/CombatAmounts.java" "$main/highpower/core/ContinuousHighPowerGrowth.java" \
    "$main/highpower/core/WindPressureLiftRules.java" "$main/highpower/core/WindPressureLiftGeometry.java" \
    "$main/core/SkillPresets.java" "$main/core/LegacySingleKeyProtocol.java" "$main/core/TargetedContextTermination.java" \
    "$main/highpower/HighPowerInput.java" "$main/client/ClassicClientRuntimeCoordinator.java" "$main/client/ClassicClientInputBinding.java" "$main/client/ClassicPhysicalKeys.java" \
    "$main/client/WindPressureLiftInputState.java" "$main/client/WindPressureLiftReceiptState.java" "$main/client/WindPressureLiftTravelState.java" \
    "$main/client/WindPressureLiftNativeSweep.java" "$main/client/WindPressureLiftState.java" "$main/client/WindPressureLiftMesh.java" \
    "$tests/WindPressureLiftInputRegressionTest.java" "$tests/WindPressureLiftReceiptRegressionTest.java" \
    "$tests/WindPressureLiftNativeSweepRegressionTest.java" "$tests/WindPressureLiftVisualRegressionTest.java" "$tests/WindPressureLiftTravelStateRegressionTest.java"
for test in WindPressureLiftInputRegressionTest WindPressureLiftReceiptRegressionTest WindPressureLiftNativeSweepRegressionTest WindPressureLiftVisualRegressionTest WindPressureLiftTravelStateRegressionTest; do
    "$wind_java" -ea -cp "$evidence_root/classes:$wind_guava" "cn.academy.port.client.$test" \
        | tee "$evidence_root/$test.log"
done
