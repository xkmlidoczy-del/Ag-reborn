#version 150
uniform sampler2D Medium;
uniform sampler2D MediumDepth;
uniform sampler2D SceneDepth;
uniform vec2 Texel;
uniform float Glow;
in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;
void main(){
    float worldDepth=texture(SceneDepth,uv).r;
    vec4 body=vec4(0);float weight=0.0;vec3 glow=vec3(0);
    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
        vec2 at=clamp(uv+vec2(x,y)*Texel,vec2(.001),vec2(.999));float d=texture(MediumDepth,at).r;
        float w=(x==0&&y==0?4.0:1.0)*step(d,worldDepth+.00025);body+=texture(Medium,at)*w;weight+=w;
    }
    body/=max(1.0,weight);
    for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++){
        vec2 at=clamp(uv+vec2(x,y)*Texel*2.0,vec2(.001),vec2(.999));float d=texture(MediumDepth,at).r;
        float w=exp(-float(x*x+y*y)*.32)*step(d,worldDepth+.0002);
        glow+=max(texture(Medium,at).rgb-vec3(.18),vec3(0))*w*.065;
    }
    vec3 colour=body.rgb/(vec3(1)+body.rgb);colour+=Glow*glow/(vec3(1)+glow);
    if(body.a<.001&&dot(glow,glow)<.00001)discard;
    fragColor=vec4(colour,body.a)*vertexColor;
}
