#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float VisualPhase;
in vec2 texCoord0;
in vec4 vertexColor;
in vec3 viewPosition;
out vec4 fragColor;
void main() {
    // U is continuous around the real 4.4m rectangle; V runs outer to inner.
    // A 120-tick period divides the bounded 2400-tick input clock exactly.
    float flow=0.5+0.5*sin(6.28318530718*(texCoord0.x-VisualPhase/120.0));
    float shoulder=sin(3.14159265359*clamp(texCoord0.y,0.0,1.0));
    bool core=vertexColor.r>0.7;
    float coverage=core?(0.88+0.12*shoulder):pow(max(0.0,shoulder),1.15);
    float light=core?(0.94+0.06*flow):(0.55+0.25*flow);
    vec3 rgb=core?mix(vec3(0.93,1.0,0.96),vec3(0.99,1.0,0.995),flow)
                 :mix(vec3(0.20,0.78,0.42),vec3(0.40,0.96,0.66),flow);
    vec4 color=vec4(rgb,texture(Sampler0,texCoord0).a*vertexColor.a*coverage*light)*ColorModulator;
    if(color.a<=0.001)discard;
    fragColor=color;
}
