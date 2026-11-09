#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

ivec2 wrapTexel(ivec2 point, ivec2 size) {
    return ivec2((point.x % size.x + size.x) % size.x,
                 (point.y % size.y + size.y) % size.y);
}

float periodicNoise(float u) {
    // Read the reference as a virtual four-cell longitudinal loop. Coarse cell
    // centers and cubic interpolation remove the fine transverse fibres rather
    // than merely reducing their contrast. All original facets share this data.
    // Explicit addressing remains seamless even if the source PNG is clamped.
    ivec2 size = max(textureSize(Sampler0, 0), ivec2(1));
    float position = fract(u) * 4.0 - 0.5;
    int base = int(floor(position));
    float fraction = fract(position);
    float interpolation = fraction * fraction * (3.0 - 2.0 * fraction);
    int cell0 = (base % 4 + 4) % 4;
    int cell1 = ((base + 1) % 4 + 4) % 4;
    ivec2 point0 = ivec2(int((float(cell0) + 0.5) * float(size.x) / 4.0), size.y / 2);
    ivec2 point1 = ivec2(int((float(cell1) + 0.5) * float(size.x) / 4.0), size.y / 2);
    float n0 = texelFetch(Sampler0, wrapTexel(point0, size), 0).r;
    float n1 = texelFetch(Sampler0, wrapTexel(point1, size), 0).r;
    return clamp(mix(n0, n1, interpolation), 0.0, 1.0);
}

void main() {
    // Sparse axial flow varies the ivory light gently. The authentic outer
    // orange cylinder and arcs supply the amber edges without tinting this core.
    float coarse = periodicNoise(texCoord0.x);
    float heat = smoothstep(0.20, 0.80, coarse);
    vec3 warmth = mix(vec3(1.0, 0.99, 0.96), vec3(1.0, 1.0, 1.0), heat);
    float luminosity = mix(0.94, 1.0, heat);
    float continuity = mix(0.90, 0.96, heat);
    // Reference noise is DATA: its opaque alpha never replaces the original
    // beam's envelope. The luminous floor prevents flicker and broken segments.
    fragColor = vec4(vertexColor.rgb * warmth * luminosity, vertexColor.a * continuity) * ColorModulator;
}
