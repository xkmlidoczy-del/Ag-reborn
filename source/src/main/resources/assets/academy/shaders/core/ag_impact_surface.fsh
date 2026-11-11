#version 150
uniform float Layer;
in vec2 uv;
in vec4 vertexColor;
in vec3 cameraPosition;
out vec4 fragColor;
void main(){
    vec2 p=uv*2.0-1.0;float radius=length(p),angle=atan(p.y,p.x);
    float age=vertexColor.r,seed=vertexColor.g*31.0;
    float ragged=.78+.09*sin(angle*9.0+seed)+.05*sin(angle*21.0-seed);
    float edge=1.0-smoothstep(ragged-.08,ragged+.08,radius);
    if(Layer>3.5){
        float cracks=pow(max(0.0,.5+.5*sin(angle*7.0+radius*13.0+seed)),15.0);
        float heat=pow(1.0-age,2.0)*(cracks*.9+exp(-radius*radius*8.0)*.5);
        vec3 c=mix(vec3(.018,.022,.013),vec3(.16,.95,.035),heat);
        fragColor=vec4(c,edge*(.50+.25*(1.0-age))*vertexColor.a);
    }else{
        float rays=pow(max(0.0,cos(angle*13.0+seed+radius*4.0)),16.0);
        float ring=exp(-pow((radius-(.3+age*.5))/.055,2.0));
        float core=exp(-radius*radius*32.0)*(1.0-age);
        float a=(core+rays*exp(-radius*4.0)*.6+ring*.4)*edge*vertexColor.a;
        fragColor=vec4(mix(vec3(.16,1,.035),vec3(1,1,.85),core),a);
    }
    if(fragColor.a<.003)discard;
}
