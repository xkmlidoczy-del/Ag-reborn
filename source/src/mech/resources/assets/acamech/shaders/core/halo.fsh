#version 150
#moj_import <fog.glsl>

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in vec2 localUV;
in vec4 tint;
in float distanceToEye;

out vec4 fragColor;

void main() {
    vec2 point = abs(localUV * 2.0 - 1.0);
    vec2 capsule = vec2(max(point.x - 0.22, 0.0) / 0.78, point.y);
    float radius = length(capsule);
    float haze = (0.65 * exp(-6.2 * radius * radius) + 0.35 * exp(-2.65 * radius * radius))
        * (1.0 - smoothstep(0.62, 1.0, radius));
    float opacity = haze * tint.a * ColorModulator.a * linear_fog_fade(distanceToEye, FogStart, FogEnd);
    fragColor = vec4(tint.rgb * ColorModulator.rgb, opacity);
}
