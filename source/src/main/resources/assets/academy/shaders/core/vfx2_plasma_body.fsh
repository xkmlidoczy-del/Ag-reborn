#version 150

uniform int ballCount;
uniform vec4 ball0;
uniform vec4 ball1;
uniform vec4 ball2;
uniform vec4 ball3;
uniform vec4 ball4;
uniform vec4 ball5;
uniform vec4 ball6;
uniform vec4 ball7;
uniform vec4 ball8;
uniform vec4 ball9;
uniform vec4 ball10;
uniform vec4 ball11;
uniform vec4 ball12;
uniform vec4 ball13;
uniform vec4 ball14;
uniform vec4 ball15;
uniform float alpha;
uniform float VisualPhase;
uniform float Charge;
uniform float Ready;
uniform float Launch;

in vec3 camspace;
out vec4 fragColor;

// Same inverse-square support, strength and density ceiling as the original material.
float densityAt(vec3 position) {
    vec4 balls[16] = vec4[16](ball0, ball1, ball2, ball3, ball4, ball5, ball6, ball7,
            ball8, ball9, ball10, ball11, ball12, ball13, ball14, ball15);
    float density = 0.0;
    for (int i = 0; i < 16; ++i) {
        if (i >= ballCount) break;
        float distance = max(0.1, length(position - balls[i].xyz));
        density += alpha * balls[i].w / (distance * distance);
    }
    return clamp(density, 0.0, 2.0);
}

// Anchor the material to the existing ball cluster, including camera rotation.
// No world UVs, textures, random field, extra density samples or new geometry.
mat3 materialFrame() {
    vec3 axis = ballCount > 1 ? ball1.xyz - ball0.xyz : vec3(1.0, 0.0, 0.0);
    vec3 x = dot(axis, axis) > 0.0001 ? normalize(axis) : vec3(1.0, 0.0, 0.0);
    vec3 up = ballCount > 2 ? ball2.xyz - ball0.xyz : vec3(0.0, 1.0, 0.0);
    up -= x * dot(up, x);
    if (dot(up, up) < 0.0001) {
        up = abs(x.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0);
        up -= x * dot(up, x);
    }
    vec3 y = normalize(up);
    return mat3(x, y, cross(x, y));
}

// Broad, smoothly advected volumes, not concentric bands or high-frequency noise.
float flowingVolume(vec3 q, float phase) {
    vec3 drift = vec3(sin(q.y * 0.58 + phase), cos(q.z * 0.53 - phase),
            sin(q.x * 0.61 + phase));
    q += 0.34 * drift;
    float broad = sin(q.x * 0.72 + q.z * 0.24 + phase)
            * cos(q.y * 0.63 - q.z * 0.31 - phase);
    float crossFlow = sin(dot(q, vec3(-0.35, 0.42, 0.50)) - 2.0 * phase);
    return clamp(0.5 + 0.28 * broad + 0.18 * crossFlow, 0.0, 1.0);
}

vec3 layeredColor(float density, float flow) {
    float core = smoothstep(0.40, 1.30, density);
    // A darker blue bed makes sparse energy contours readable through the volume.
    // The low-density sheath remains pink; no opacity or density support is changed.
    vec3 shell = vec3(0.90, 0.30, 0.76);
    vec3 blue = vec3(0.14, 0.44, 0.84);
    vec3 color = mix(shell, blue, core);
    // One continuous contour of the existing broad field, not added noise or trig.
    // Smooth shoulders keep the vein visible at distance without a hard stripe edge.
    float vein = 1.0 - smoothstep(0.025, 0.085, abs(flow - 0.48));
    vec3 veinColor = mix(vec3(0.62, 0.87, 1.0), vec3(0.86, 0.95, 1.0), core * core);
    color = mix(color, veinColor, vein * core);
    float energy = 0.91 + 0.04 * Charge + 0.025 * Ready + 0.025 * Launch;
    return clamp(color * energy, 0.0, 1.0);
}

vec4 rayMarch(vec3 begin, vec3 direction, mat3 frame, float phase) {
    vec3 pos = begin;
    vec3 stride = direction * 0.15;
    // The field varies over roughly ten blocks while this ray spans only 2.85.
    // Two bounded endpoint samples retain broad 3D flow without evaluating six
    // trigonometric functions at every density step. Core/opacity still vary per step.
    vec3 localBegin = (begin - ball0.xyz) * frame;
    vec3 localEnd = (begin + stride * 19.0 - ball0.xyz) * frame;
    float beginFlow = flowingVolume(localBegin, phase);
    float endFlow = flowingVolume(localEnd, phase);
    vec4 accum = vec4(0.0);
    for (int i = 0; i < 20 && accum.a < 1.0; ++i) {
        float density = densityAt(pos);
        // Ignore samples at/behind the eye, retain forward plasma and the distant direction cue.
        // Beyond 1.75 blocks this is exactly one; density and billboard/hit geometry never change.
        float eyeFade = smoothstep(0.35, 1.75, dot(pos, direction));
        float sampleAlpha = 0.075 * density * eyeFade;
        if (sampleAlpha > 0.0) {
            float flow = mix(beginFlow, endFlow, float(i) / 19.0);
            vec3 color = layeredColor(density, flow);
            accum.rgb = mix(accum.rgb, color, sampleAlpha / (accum.a + sampleAlpha));
            accum.a += sampleAlpha;
        }
        pos += stride;
    }
    // Preserve the original soft support boundary and final opacity envelope.
    if (accum.a < 0.2) accum.a = 2.0 * accum.a - 0.2;
    return accum;
}

void main() {
    vec3 cam = camspace;
    cam.z = -cam.z;
    float distance = length(cam);
    if (distance < 0.0001 || ballCount <= 0 || alpha <= 0.0) {
        fragColor = vec4(0.0);
        return;
    }
    vec3 direction = normalize(cam);
    float phase = 6.28318530718 * clamp(VisualPhase, 0.0, 1.0);
    vec4 result = rayMarch(cam - direction * 3.0, direction, materialFrame(), phase);
    result.a = clamp(result.a, 0.0, 1.0) * (0.5 + alpha * 0.5);
    fragColor = result;
}
