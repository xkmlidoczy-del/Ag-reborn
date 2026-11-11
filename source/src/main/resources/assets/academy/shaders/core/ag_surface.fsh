#version 150
// Original Ag surface materials. Distinct solids accompany, rather than replace, the native geometry.
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Time;
uniform float Family;
in vec2 uv;
in vec4 vertexColor;
in vec3 cameraPosition;
out vec4 fragColor;
float pattern(vec2 p){return fract(sin(dot(p,vec2(91.37,207.13)))*37841.31);}
void main(){
    vec4 tex=texture(Sampler0,uv);vec3 normal=normalize(cross(dFdx(cameraPosition),dFdy(cameraPosition)));
    vec3 eye=normalize(-cameraPosition);float facing=abs(dot(normal,eye));
    float light=.43+.57*abs(dot(normal,normalize(vec3(.3,.8,.45))));
    vec3 colour=tex.rgb;float alpha=tex.a;
    if(Family>7.5&&Family<8.5){
        // A complete folded sheet: the icon atlas is not printed onto each piece of paper.
        float edge=min(min(uv.x,1.0-uv.x),min(uv.y,1.0-uv.y));
        float fibre=pattern(floor(uv*vec2(930,1700)))*.045;
        float crease=exp(-pow((uv.x-.5)*42.0,2.0));
        colour=vec3(.98,.95,.84)*(light*.78+.22+fibre)-crease*.065;
        alpha=smoothstep(0.0,.008,edge);
    }else if(Family>8.5&&Family<10.5){
        vec2 p=uv*vec2(14,29);float wave=sin(p.x+sin(p.y+Time*2.0))*sin(p.y*.7-Time*1.9);
        float spec=pow(max(0.0,sin(p.x*.8+p.y*.25+Time+wave*.6)),14.0)*(.25+.75*pow(1.0-facing,2.0));
        vec3 base=Family<9.5?vec3(.025,.055,.12):vec3(.035,.26,.45);
        colour=mix(base,tex.rgb,.3)*(.55+light*.45)+vec3(.44,.74,1.0)*spec*.7;
        alpha*=.85;
    }else if(Family>10.5&&Family<11.5){
        vec2 p=uv*5.0;float facets=abs(fract(p.x+p.y*.5)-.5);
        float glint=pow(max(0.0,1.0-abs(facets-.18)*12.0),16.0)*(.5+.5*sin(Time*1.8+p.x));
        colour=tex.rgb*(.5+light*.7)+vec3(.75,.67,1.0)*(glint*.7+pow(1.0-facing,4.0)*.22);
    }else{
        float flow=.92+.08*sin(uv.x*32.0-uv.y*15.0-Time*3.0);
        colour*=flow;
    }
    if(alpha*vertexColor.a<.003)discard;
    fragColor=vec4(colour,alpha)*vertexColor*ColorModulator;
}
