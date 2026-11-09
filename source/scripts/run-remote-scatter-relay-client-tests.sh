#!/usr/bin/env bash
set -euo pipefail

# Engine-free production helper tests. Guava is used only by the unchanged classic input coordinator.
# Supply REMOTE_RELAY_JDK and REMOTE_RELAY_GUAVA if Java 21 / the cached Guava jar are not available.
source_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
relay_jdk="${REMOTE_RELAY_JDK:-${JAVA_HOME:-}}"
if [[ -n "$relay_jdk" ]]; then
    relay_javac="$relay_jdk/bin/javac"
    relay_java="$relay_jdk/bin/java"
else
    relay_javac=javac
    relay_java=java
fi
relay_guava="${REMOTE_RELAY_GUAVA:-}"
if [[ -z "$relay_guava" ]]; then
    shopt -s nullglob
    jars=("$source_root"/.gradle-user/caches/modules-2/files-2.1/com.google.guava/guava/*/*/guava-*.jar)
    if [[ ${#jars[@]} -eq 0 ]]; then
        echo 'Set REMOTE_RELAY_GUAVA to the installed Guava jar; this runner does not download dependencies.' >&2
        exit 2
    fi
    relay_guava="${jars[0]}"
fi
if [[ $# -gt 0 ]]; then
    evidence_root="$1"
    mkdir -p -- "$evidence_root"
else
    evidence_root="$(mktemp -d "${TMPDIR:-/tmp}/remote-scatter-relay-client.XXXXXX")"
    trap 'rm -rf -- "$evidence_root"' EXIT
fi
mkdir -p -- "$evidence_root/classes"
main="$source_root/src/main/java/cn/academy/port"
tests="$source_root/src/test/java/cn/academy/port/client"
"$relay_javac" --release 21 -cp "$relay_guava" -d "$evidence_root/classes" \
    "$main/core/SkillPresets.java" "$main/core/LegacySingleKeyProtocol.java" "$main/core/TargetedContextTermination.java" \
    "$main/highpower/HighPowerInput.java" "$main/client/ClassicClientRuntimeCoordinator.java" "$main/client/ClassicClientInputBinding.java" "$main/client/ClassicPhysicalKeys.java" \
    "$main/highpower/core/FoldedMirrorGeometry.java" "$main/client/FoldedMirrorState.java" "$main/client/FoldedMirrorMesh.java" \
    "$main/client/RemoteScatterRelayInputState.java" "$main/client/RemoteScatterRelayState.java" "$main/client/RemoteScatterRelayMesh.java" \
    "$tests/RemoteScatterRelayVisualRegressionTest.java"
cd -- "$source_root"
"$relay_java" -ea -cp "$evidence_root/classes:$relay_guava" cn.academy.port.client.RemoteScatterRelayVisualRegressionTest \
    | tee "$evidence_root/RemoteScatterRelayVisualRegressionTest.log"
