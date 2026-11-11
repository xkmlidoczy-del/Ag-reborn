#version 150
uniform float Time;
uniform float Layer;
uniform float NativeTint;
in vec2 uv;
in vec4 vertexColor;
in vec3 cameraPosition;
out vec4 fragColor;
float weave(vec2 p){return sin(p.x+sin(p.y*1.7))*sin(p.y-p.x*.33);}
void main(){
    vec3 n=normalize(cross(dFdx(cameraPosition),dFdy(cameraPosition)));vec3 eye=normalize(-cameraPosition);
    float rim=pow(1.0-abs(dot(n,eye)),2.0);
    float travel=uv.x*2.8-Time*16.0;
    float strands=pow(.5+.5*sin(uv.y*37.6991+travel*.65+weave(vec2(uv.x*.8,uv.y*17.0-Time))*1.3),9.0);
    float grains=.5+.5*weave(vec2(travel*2.3,uv.y*19.0+Time*3.0));
    float pulse=.78+.22*sin(travel*2.0+uv.y*12.0);
    float flecks=smoothstep(.34,.72,grains);
    vec3 colour;float alpha;
    if(Layer<.5){colour=mix(vec3(.32,1.0,.12),vec3(1.15,1.2,.95),.65+.35*grains);alpha=(.21+strands*.38)*pulse;}
    else if(Layer<1.5){colour=mix(vec3(.025,.36,.008),vec3(.35,1.1,.09),strands);alpha=(strands*.28+rim*.025)*pulse*(.25+flecks*.75);}
    else{colour=vec3(.12,.85,.03);alpha=(rim*.018+strands*.12)*flecks;}
    if(NativeTint>.5)colour=Layer<.5?mix(vertexColor.rgb,vec3(1.1),.72+.20*grains):vertexColor.rgb*(.25+strands*.9);
    // An energy stream has no closed glass-wall silhouette or exposed hollow tube mouth.
    alpha*=vertexColor.a*smoothstep(.02,1.4,uv.x);if(alpha<.003)discard;fragColor=vec4(colour,alpha);
}
