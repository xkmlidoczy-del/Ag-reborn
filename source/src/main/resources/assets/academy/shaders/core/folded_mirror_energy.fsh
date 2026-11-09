#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float MaterialKind;
uniform float TexturePart;
uniform float VisualPhase;
uniform vec3 Contact;
in vec2 texCoord0;
in vec4 vertexColor;
in vec3 viewPosition;
out vec4 fragColor;

void main() {
    vec4 source = texture(Sampler0, texCoord0);
    vec4 color = source * vertexColor;
    if (MaterialKind < 0.5) {
        // Retain the original mdshield swirl, but suppress its uniform saturated-green wash.
        vec2 p = (texCoord0 - 0.5) / 0.43;
        float radius = length(p);
        float interior = 1.0 - smoothstep(0.64, 1.0, radius);
        vec2 drift = vec2(sin(p.y * 7.0 + VisualPhase * 0.055),
                          sin(p.x * 6.0 - VisualPhase * 0.043)) * 0.0035 * interior;
        source = texture(Sampler0, texCoord0 + drift);
        // The artwork's flat green has whiteness ~0.196; select the actual pale swirl above it.
        float detail = smoothstep(0.23, 0.64, min(source.r, source.b));
        vec3 surfaceNormal = normalize(cross(dFdx(viewPosition), dFdy(viewPosition)));
        float grazing = pow(1.0 - abs(dot(surfaceNormal, normalize(-viewPosition))), 3.0);
        float edge = smoothstep(0.74, 0.97, radius) * (1.0 - smoothstep(0.98, 1.02, radius));
        // The response is a small, non-ringed caustic at a confirmed reflection point.
        vec2 contactDistance = (texCoord0 - Contact.xy) / vec2(0.050, 0.034);
        float local = exp(-dot(contactDistance, contactDistance) * 1.8) * Contact.z;
        color.rgb = mix(vec3(0.24, 0.85, 0.40), vec3(0.96, 1.0, 0.97), detail);
        color.rgb = mix(color.rgb, vec3(0.93, 1.0, 0.95), local * 0.80);
        // ~.10 alpha in the green wash, up to the original ~.45 in bright source highlights.
        color.a = source.a * (0.22 + detail * 0.78 + edge * (0.12 + grazing * 0.22));
        color.a += local * 0.23;
        color.a *= vertexColor.a;
    } else if (MaterialKind > 4.5 && MaterialKind < 6.5) {
        // Smoothly shade the real closed volume. Geometry keeps its readable .020 support.
        float facing = clamp((texCoord0.y - 0.5) * 2.0, 0.0, 1.0);
        float center = smoothstep(0.30, 0.91, facing);
        float flow = 0.965 + 0.035 * sin(texCoord0.x * 2.8 - VisualPhase * 0.72);
        if (MaterialKind > 5.5) {
            color.rgb = mix(vec3(0.39, 0.98, 0.56), vec3(0.95, 1.0, 0.97), center);
            color.a = vertexColor.a * (0.28 + 0.72 * center) * flow;
        } else {
            color.rgb = vec3(0.30, 0.96, 0.43);
            color.a = vertexColor.a * smoothstep(0.0, 0.80, facing) * 0.65;
        }
    } else if (MaterialKind > 7.5) {
        // Original small-ray color/flow with one soft transverse profile, never two multiplied fades.
        vec2 uv = texCoord0;
        if (TexturePart > 0.5 && TexturePart < 1.5) uv.x = fract(uv.x - VisualPhase * 0.055);
        source = texture(Sampler0, uv);
        float transverse = abs(texCoord0.y * 2.0 - 1.0);
        float longitudinal = texture(Sampler0, vec2(uv.x, 0.5)).a;
        float envelope = exp(-transverse * transverse * 1.4) * (1.0 - smoothstep(0.55, 1.0, transverse));
        color.rgb = mix(source.rgb, vec3(0.48, 1.0, 0.61), 0.42);
        color.a = longitudinal * vertexColor.a * envelope * 0.82;
    } else if (MaterialKind > 0.5 && MaterialKind < 1.5) {
        color.rgb = mix(vertexColor.rgb, vec3(0.81, 1.0, 0.88), 0.16);
        color.a *= 0.76 + 0.08 * sin(VisualPhase * 0.055);
    } else if (MaterialKind > 1.5 && MaterialKind < 2.5) {
        color.a *= 0.58;
    }
    color *= ColorModulator;
    if (color.a <= 0.001) discard;
    fragColor = color;
}
