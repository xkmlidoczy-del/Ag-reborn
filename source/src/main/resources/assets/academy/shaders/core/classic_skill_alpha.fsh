#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;

void main() {
    // Body HUD disables alpha testing; RippleMark accepts every alpha>0.
    // Zero-alpha blending is also inert, so no fragment discard is necessary.
    fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
}
