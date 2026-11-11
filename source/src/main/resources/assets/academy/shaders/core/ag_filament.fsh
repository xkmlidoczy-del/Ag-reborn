#version 150
uniform float Time;
uniform float Boundary;
uniform float Ink;
in vec2 uv;
in vec4 vertexColor;
in vec3 cameraPosition;
out vec4 fragColor;
void main(){
    float y=abs(uv.y-.5)*2.0;
    float core=exp(-y*y*35.0),corona=exp(-y*y*7.0);
    float flow=.64+.36*pow(.5+.5*sin(uv.x*19.0-Time*8.0),3.0);
    if(Boundary>.5){core=exp(-y*y*20.0);corona*=.35;flow=.88+.12*sin(uv.x*8.0-Time);}
    vec3 tint=vertexColor.rgb;
    vec3 colour=mix(tint,tint*.42+vec3(.86),core*.7)*(.8+flow*.5);
    if(Ink>.5)colour=tint;
    float alpha=(core*.98+corona*.36)*vertexColor.a;
    if(alpha<.005)discard;fragColor=vec4(colour,alpha);
}
