#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec2 uv;
out vec4 vertexColor;
void main(){uv=UV0;vertexColor=Color;gl_Position=vec4(Position.xy,0.0,1.0);}
