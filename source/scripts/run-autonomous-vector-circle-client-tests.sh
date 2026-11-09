#!/usr/bin/env bash
set -euo pipefail
source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
circle_jdk="${CIRCLE_JDK:-${JAVA_HOME:-}}"
circle_javac="${circle_jdk:+$circle_jdk/bin/}javac"
circle_java="${circle_jdk:+$circle_jdk/bin/}java"
circle_guava="${CIRCLE_GUAVA:-}"
if [[ -z "$circle_guava" ]]; then
    shopt -s nullglob
    jars=("$source_root"/.gradle-user/caches/modules-2/files-2.1/com.google.guava/guava/*/*/guava-*.jar)
    if [[ ${#jars[@]} -eq 0 ]]; then
        echo 'Set CIRCLE_GUAVA to the installed Guava jar; this runner never downloads dependencies.' >&2
        exit 2
    fi
    circle_guava="${jars[0]}"
fi
if [[ $# -gt 0 ]]; then
    evidence_root="$1"; mkdir -p -- "$evidence_root"
else
    evidence_root="$(mktemp -d "${TMPDIR:-/tmp}/autonomous-vector-circle-client.XXXXXX")"
    trap 'rm -rf -- "$evidence_root"' EXIT
fi
mkdir -p -- "$evidence_root/classes"
main="$source_root/src/main/java/cn/academy/port"
tests="$source_root/src/test/java/cn/academy/port/client"
"$circle_javac" --release 21 -cp "$circle_guava" -d "$evidence_root/classes" \
    "$main/core/SkillPresets.java" "$main/core/LegacySingleKeyProtocol.java" "$main/core/TargetedContextTermination.java" \
    "$main/highpower/core/CombatAmounts.java" "$main/highpower/HighPowerRecoveryHudModel.java" \
    "$main/highpower/HighPowerInput.java" "$main/client/ClassicClientRuntimeCoordinator.java" \
    "$main/client/ClassicClientInputBinding.java" "$main/client/ClassicPhysicalKeys.java" \
    "$main/client/AutonomousVectorCircleInputState.java" "$main/client/AutonomousVectorCircleState.java" \
    "$main/client/AutonomousVectorCircleMesh.java" "$main/client/AutonomousVectorCircleHudModel.java" \
    "$tests/AutonomousVectorCircleInputRegressionTest.java" "$tests/AutonomousVectorCircleSoftProfileRegressionTest.java" "$tests/AutonomousVectorCircleVisualRegressionTest.java" "$tests/AutonomousVectorCircleHudRegressionTest.java"
for test in AutonomousVectorCircleInputRegressionTest AutonomousVectorCircleVisualRegressionTest AutonomousVectorCircleHudRegressionTest; do
    "$circle_java" -ea -cp "$evidence_root/classes:$circle_guava" "cn.academy.port.client.$test" | tee "$evidence_root/$test.log"
done
