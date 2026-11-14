#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform vec4 FogColor;
uniform float FogStart;
uniform float FogEnd;

in vec3 viewPosition;
in vec3 viewNormal;
in vec3 keyLight;
in vec4 shadedColor;
in vec3 worldLight;
in vec2 texCoord;
in float distanceToEye;

out vec4 fragColor;

vec3 brushedNormal(vec3 normal, float grain, float variation) {
    vec2 du = dFdx(texCoord);
    vec2 dv = dFdy(texCoord);
    vec3 dx = dFdx(viewPosition);
    vec3 dy = dFdy(viewPosition);
    vec3 alongU = dx * dv.y - dy * du.y;
    vec3 alongV = dy * du.x - dx * dv.x;
    vec3 crossGrain = grain < 0.5 ? alongV : alongU;
    float area = length(crossGrain);
    if (area < 0.00001) return normal;
    return normalize(normal + crossGrain / area * variation);
}

void main() {
    vec4 base = texture(Sampler0, texCoord);
    if (base.a < 0.1) discard;
    vec3 finish = texture(Sampler1, texCoord).rgb;
    vec3 flatNormal = normalize(viewNormal);
    float grain = smoothstep(0.14, 0.62, abs(finish.b - 0.5) * 2.0);
    float microGrain = (finish.g - mix(0.55, 0.88, step(0.80, finish.r))) * 0.13;
    vec3 normal = brushedNormal(flatNormal, finish.b, microGrain * (1.0 - grain * 0.45));
    vec3 eye = normalize(-viewPosition);
    vec3 light = normalize(keyLight + vec3(0.001));
    vec3 halfVector = normalize(light + eye);
    float gloss = clamp(finish.g, 0.0, 1.0);
    float specular = pow(max(dot(normal, halfVector), 0.0), mix(10.0, 70.0, gloss));
    float broad = pow(max(dot(normal, halfVector), 0.0), mix(8.0, 20.0, gloss));
    float grazing = pow(1.0 - clamp(dot(flatNormal, eye), 0.0, 1.0), 4.0);
    float facing = 0.23 + 0.77 * max(dot(normal, light), 0.0);
    float illumination = dot(worldLight, vec3(0.2126, 0.7152, 0.0722));
    float highlight = finish.r * (specular * 0.34 + broad * 0.11 + grazing * 0.105 * facing)
        * smoothstep(0.17, 0.92, illumination);
    vec3 steelTint = mix(vec3(0.77, 0.89, 1.0), min(base.rgb + 0.37, vec3(1.0)), 0.35);
    // Zero finish marks a lens. Its powered color comes from the separate emission pass.
    float unlitLens = mix(0.40, 1.0, step(0.05, finish.r));
    vec3 color = base.rgb * shadedColor.rgb * worldLight * unlitLens + highlight * steelTint;
    fragColor = linear_fog(vec4(color, base.a) * ColorModulator, distanceToEye, FogStart, FogEnd, FogColor);
}
