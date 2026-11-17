#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in vec2 localUV;
in vec4 tint;
in float distanceToEye;
out vec4 fragColor;

void main() {
    float density = texture(Sampler0, localUV).a;
    float opacity = density * tint.a * ColorModulator.a * linear_fog_fade(distanceToEye, FogStart, FogEnd);
    if (opacity < 0.002) discard;
    vec3 emission = mix(tint.rgb, vec3(1.0), 0.48 * smoothstep(0.43, 0.98, density));
    fragColor = vec4(emission * ColorModulator.rgb, opacity);
}
