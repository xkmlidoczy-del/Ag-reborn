#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec4 source = texture(Sampler0, texCoord0);
    // Reference strip is neutral white with authored alpha. Sample intensity, retain authentic warm vertex tint.
    // The PNG owns the soft transverse alpha exactly once; no second Gaussian/power mask.
    float intensity = max(source.r, max(source.g, source.b));
    fragColor = vec4(vec3(intensity) * vertexColor.rgb, source.a * vertexColor.a) * ColorModulator;
    if (fragColor.a < 0.001) discard;
}
