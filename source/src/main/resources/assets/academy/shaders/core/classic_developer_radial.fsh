#version 150
// AcademyCraft1.0.7 skill_progbar.frag exact comparison, adapted to core GUI format.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float Progress;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    float threshold=texture(Sampler1,texCoord0).r;
    fragColor=Progress>threshold?texture(Sampler0,texCoord0)*vertexColor*ColorModulator:vec4(0.0);
}
