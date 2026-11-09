#version 150
// AcademyCraft 1.0.7 cpbar_cp.frag equations, migrated to modern core-shader names.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    vec2 iconUV = (texCoord0 * vec2(964.0,147.0) - vec2(857.0,43.0)) / 65.0;
    float maskColor = 1.0;
    if (iconUV.x >= 0.0 && iconUV.x <= 1.0 && iconUV.y >= 0.0 && iconUV.y <= 1.0)
        maskColor -= texture(Sampler1, iconUV).a;
    vec4 texColor = texture(Sampler0, texCoord0);
    fragColor = vertexColor * ColorModulator * vec4(texColor.rgb,texColor.a * maskColor);
}
