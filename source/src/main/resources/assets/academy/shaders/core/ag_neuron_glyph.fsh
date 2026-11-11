#version 150
uniform float Time;
in vec2 uv;
in vec4 vertexColor;
in vec3 cameraPosition;
out vec4 fragColor;
void main(){
    vec2 p=uv*2.0-1.0;float r=length(p),a=atan(p.y,p.x),aa=max(fwidth(r),.002);
    float ring=1.0-smoothstep(.012,.012+aa,abs(r-.63));
    float arc=(1.0-smoothstep(.009,.009+aa,abs(r-.84)))*smoothstep(-.2,.4,sin(a*3.0-Time*1.3));
    float tick=(1.0-smoothstep(.009,.009+aa,min(abs(p.x),abs(p.y))))*smoothstep(.34,.4,r)*(1.0-smoothstep(.53,.57,r));
    float dot=exp(-r*r*130.0),halo=exp(-pow((r-.63)/.07,2.0))*.15;
    vec3 colour=mix(vertexColor.rgb,vec3(1.2,1.05,1.1),dot*.75+ring*.35);
    float alpha=(ring+arc*.65+tick*.8+dot+halo)*vertexColor.a;
    if(alpha<.004)discard;fragColor=vec4(colour,alpha);
}
