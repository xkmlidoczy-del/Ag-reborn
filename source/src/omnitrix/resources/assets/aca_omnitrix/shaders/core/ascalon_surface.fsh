#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float StarfieldSeconds;
uniform float ReducedMotion;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

vec2 mirrored(vec2 p) {
    return 1.0 - abs(fract(p * 0.5) * 2.0 - 1.0);
}

void main() {
    vec2 baseUv = vec2(clamp(texCoord0.x * 0.5, 0.0005, 0.4995), clamp(texCoord0.y, 0.0005, 0.9995));
    vec4 base = texture(Sampler0, baseUv);
    if (base.a < 0.02) discard;

    // vertexColor.a == 0 marks fixed hilt/gem surfaces that keep crisp metal shading without heavy flow override
    float maskFactor = vertexColor.a > 0.5 ? 1.0 : 0.18;
    vec3 modelPos = vertexColor.rgb * vec3(64.0, 48.0, 64.0) - vec3(32.0, 4.0, 32.0);
    float t = ReducedMotion > 0.5 ? 0.0 : StarfieldSeconds * 0.14;
    vec2 uv = clamp(mirrored(vec2(modelPos.x * 0.16 + modelPos.z * 0.08,
                                  modelPos.y * 0.085 - t)), vec2(0.002), vec2(0.998));
    vec3 material = texture(Sampler0, vec2(0.5 + uv.x * 0.5, uv.y)).rgb;
    float luma = dot(material, vec3(0.2126, 0.7152, 0.0722));
    float blade = smoothstep(2.6, 5.2, modelPos.y) * maskFactor;
    float sheenSweep = smoothstep(0.58, 0.96, sin(modelPos.y * 0.24 - t * 9.5) * 0.5 + 0.5);

    vec3 coolPlatinum = mix(vec3(0.76, 0.84, 0.95), vec3(0.96, 0.98, 1.0), smoothstep(0.42, 0.88, luma));
    vec3 flowing = base.rgb * 0.78 + coolPlatinum * (0.24 * luma + 0.14 * sheenSweep);
    vec3 rgb = mix(base.rgb, clamp(flowing, 0.0, 1.0), blade * 0.72);
    rgb = mix(overlayColor.rgb, rgb, overlayColor.a) * (0.90 + 0.10 * lightMapColor.rgb);
    fragColor = linear_fog(vec4(rgb, base.a) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
