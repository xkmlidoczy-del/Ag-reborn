#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec2 texCoord0;
out float vfxMode;
out float vfxPhase;
out vec3 vViewNormal;
out vec3 vViewDir;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vertexColor = Color;
    texCoord0 = UV0;
    // UV1.x: 0=OuterBlackShell, 1=InnerNeonDome, 2=SpatialRiftPortal, 3=BlackFlashLogAura, 4=BroadcastRing, 5=SauceWave
    vfxMode = float(UV1.x);
    vfxPhase = clamp(float(UV1.y) * 0.001, 0.0, 1.0);
    vec3 nrm = mat3(ModelViewMat) * Normal;
    float nLen = length(nrm);
    vViewNormal = nLen > 1e-4 ? (nrm / nLen) : vec3(0.0, 0.0, 1.0);
    float vLen = max(1e-4, length(viewPos.xyz));
    vViewDir = -viewPos.xyz / vLen;
}
