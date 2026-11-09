#!/usr/bin/env bash
set -euo pipefail
# Run from repository root. Arguments allow separate adapter and additive oracle stages.
adapter_stage=${1:-.}
oracle_stage=${2:-$adapter_stage}
phase_output=$(mktemp -d /tmp/academy-phase-generator-portable.XXXXXX)
phase_jdk=${ACADEMY_JDK:-.tools/jdk-21.0.12.1+1}
"$phase_jdk/bin/javac" -proc:none -encoding UTF-8 -d "$phase_output" \
  "$adapter_stage/src/main/java/cn/academy/port/phasegen/ClassicPhaseGeneratorRules.java" \
  "$adapter_stage/src/main/java/cn/academy/port/phasegen/ClassicPhaseGeneratorBuffer.java" \
  "$oracle_stage/src/test/java/cn/academy/port/phasegen/ClassicPhaseGeneratorSourceOracleTest.java"
"$phase_jdk/bin/java" -ea -Dacademy.phasegen.stage="$oracle_stage" -cp "$phase_output" \
  cn.academy.port.phasegen.ClassicPhaseGeneratorSourceOracleTest
