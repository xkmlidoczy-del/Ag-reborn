#!/usr/bin/env bash
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
jdk="${CIRCLE_JDK:-${JAVA_HOME:-}}"
javac="${jdk:+$jdk/bin/}javac"; java="${jdk:+$jdk/bin/}java"
if [[ $# -gt 0 ]]; then out="$1"; mkdir -p -- "$out"; else
    out="$(mktemp -d "${TMPDIR:-/tmp}/circle-soft-fallback.XXXXXX")"; trap 'rm -rf -- "$out"' EXIT
fi
hosts="$root/src/test/resources/landing-array-v56-fallback/hosts"
test="$root/src/test/resources/autonomous-vector-circle-soft"
mkdir -p -- "$out/classes"
"$javac" --release 21 -d "$out/classes" \
    "$hosts/com/mojang/blaze3d/vertex/VertexFormat.java" \
    "$hosts/com/mojang/blaze3d/vertex/DefaultVertexFormat.java" \
    "$hosts/net/minecraft/resources/ResourceLocation.java" \
    "$hosts/net/minecraft/server/packs/resources/ResourceManager.java" \
    "$hosts/net/minecraft/client/renderer/ShaderInstance.java" \
    "$hosts/net/neoforged/neoforge/client/event/RegisterShadersEvent.java" \
    "$hosts/org/lwjgl/opengl/GL20.java" \
    "$test/hosts/net/minecraft/client/renderer/GameRenderer.java" \
    "$root/src/main/java/cn/academy/port/client/AutonomousVectorCircleSoftMaterial.java" \
    "$test/AutonomousVectorCircleSoftFallbackHarness.java"
for lane in valid missing compile link forced stale; do
    "$java" -ea -cp "$out/classes" cn.academy.port.client.AutonomousVectorCircleSoftFallbackHarness \
        "$root/src/main/resources" "$lane" | tee "$out/$lane.log"
done
# Independently omit each of the three actual shader resources; no downloaded or alternate asset is used.
for extension in json vsh fsh; do
    resources="$out/missing-$extension"
    mkdir -p -- "$resources/assets/academy/shaders/core"
    for retained in json vsh fsh; do
        if [[ "$retained" != "$extension" ]]; then
            cp -- "$root/src/main/resources/assets/academy/shaders/core/autonomous_vector_circle_soft.$retained" \
                "$resources/assets/academy/shaders/core/"
        fi
    done
    "$java" -ea -cp "$out/classes" cn.academy.port.client.AutonomousVectorCircleSoftFallbackHarness \
        "$resources" "missing-$extension" | tee "$out/missing-$extension.log"
done
