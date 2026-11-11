#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec2 uv;
out vec4 vertexColor;
out vec3 cameraPosition;
void main(){
    vec4 p=ModelViewMat*vec4(Position,1.0);
    gl_Position=ProjMat*p;cameraPosition=p.xyz;uv=UV0;vertexColor=Color;
}
