#version 150
uniform float Time;
uniform float Layer;
uniform float NativeTint;
uniform float Textured;
uniform sampler2D Sampler0;
in vec2 uv;
in vec4 vertexColor;
in vec3 cameraPosition;
out vec4 fragColor;
float weave(vec2 p){return sin(p.x+sin(p.y*1.7))*sin(p.y-p.x*.33);}
void main(){
    vec3 rawN=cross(dFdx(cameraPosition),dFdy(cameraPosition));
    float nLen=dot(rawN,rawN);
    vec3 n=nLen>1e-12?rawN*inversesqrt(nLen):vec3(0.0,0.0,1.0);
    float cLen=dot(cameraPosition,cameraPosition);
    vec3 eye=cLen>1e-12?-cameraPosition*inversesqrt(cLen):vec3(0.0,0.0,1.0);
    float rim=pow(clamp(1.0-abs(dot(n,eye)),0.0,1.0),2.2);
    float travel=uv.x*3.1-Time*18.0;
    float strands=pow(.5+.5*sin(uv.y*37.6991+travel*.68+weave(vec2(uv.x*.85,uv.y*17.0-Time))*1.35),8.0);
    float grains=.5+.5*weave(vec2(travel*2.3,uv.y*19.0+Time*3.0));
    float pulse=.80+.20*sin(travel*2.2+uv.y*12.0);
    float flecks=smoothstep(.32,.72,grains);
    vec3 colour;float alpha;
    if(Layer<.5){colour=mix(vec3(.32,1.0,.12),vec3(.96,1.0,.92),.68+.32*grains);alpha=(.28+strands*.42)*pulse;}
    else if(Layer<1.5){colour=mix(vec3(.03,.42,.015),vec3(.38,1.0,.14),strands);alpha=(strands*.32+rim*.06)*pulse*(.28+flecks*.72);}
    else{colour=vec3(.14,.92,.06);alpha=(rim*.085+strands*.14)*(.35+.65*flecks);}
    if(NativeTint>.5){
        if(Layer<.5)colour=mix(vertexColor.rgb,vec3(1.0),.70+.24*grains);
        else if(Layer<1.5)colour=mix(vertexColor.rgb*.45,vertexColor.rgb*1.12,strands);
        else colour=vertexColor.rgb*(.55+.45*rim);
    }
    if(Textured>.5){
        float filaments=texture(Sampler0,vec2(fract(uv.x*.29-Time*.79),fract(uv.y+Time*.065))).r;
        float bunch=smoothstep(.35,.83,filaments);
        float packet=.55+.45*sin(uv.x*2.4-Time*27.0);
        if(Layer<.5){
            colour=mix(vec3(.32,1.0,.10),vec3(1.0,1.0,.86),.54+.42*bunch);
            alpha=(.41+.32*bunch+.15*strands)*(.78+.22*packet);
        }else if(Layer<1.5){
            colour=mix(vec3(.035,.59,.06),vec3(.40,1.0,.13),bunch);
            alpha=(.095+.28*bunch+.12*strands)*(.50+.50*packet);
        }else{
            colour=vec3(.16,.88,.08);
            alpha=(.018+.06*strands+.035*bunch)*flecks;
        }
    }
    // An energy stream has no closed glass-wall silhouette or exposed hollow tube mouth.
    float inside=Layer<.5?.88+.12*smoothstep(.65,2.2,length(cameraPosition)):smoothstep(.58,2.8,length(cameraPosition));
    alpha*=vertexColor.a*smoothstep(.02,1.2,uv.x)*inside;if(alpha<.003)discard;fragColor=vec4(clamp(colour,0.0,1.25),clamp(alpha,0.0,1.0));
}
