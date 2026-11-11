#version 150
// Original Ag material fields, in camera-relative world space.
uniform sampler2D SceneDepth;
uniform sampler2D SceneColor;
uniform mat4 InverseProjection;
uniform mat4 InverseView;
uniform mat4 ViewRotation;
uniform mat4 ProjectionMatrix;
uniform vec3 Centre;
uniform vec3 SegmentStart;
uniform vec3 SegmentEnd;
uniform vec4 Dimensions;
uniform vec4 Animation;
uniform float Family;
uniform float Form;
uniform float Steps;
in vec2 uv;
in vec4 vertexColor;
out vec4 fragColor;
const float PI=3.14159265;
float random3(vec3 p){p=fract(p*vec3(.1193,.1377,.1089));p+=dot(p,p.yzx+27.41);return fract((p.x+p.z)*p.y);}
float agNoise(vec3 p){vec3 a=floor(p),u=fract(p);u=u*u*(3.0-2.0*u);
    return mix(mix(mix(random3(a),random3(a+vec3(1,0,0)),u.x),mix(random3(a+vec3(0,1,0)),random3(a+vec3(1,1,0)),u.x),u.y),
               mix(mix(random3(a+vec3(0,0,1)),random3(a+vec3(1,0,1)),u.x),mix(random3(a+vec3(0,1,1)),random3(a+vec3(1,1,1)),u.x),u.y),u.z);}
float layers(vec3 p){return .64*agNoise(p)+.25*agNoise(p*2.17+vec3(9,3,7))+.11*agNoise(p*4.31-vec3(1,8,2));}
float ridge(float x,float width){return exp(-x*x/max(.0001,width*width));}
vec3 spin(vec3 p,float angle){float c=cos(angle),s=sin(angle);return vec3(p.x*c-p.z*s,p.y,p.x*s+p.z*c);}
vec2 sphere(vec3 origin,vec3 direction,vec3 centre,float radius){vec3 q=origin-centre;float b=dot(q,direction),d=b*b-dot(q,q)+radius*radius;return d<0.0?vec2(1,-1):vec2(-b-sqrt(d),-b+sqrt(d));}
vec2 finiteTube(vec3 direction,vec3 a,vec3 b,float radius){
    vec3 ab=b-a;float len=length(ab);vec3 axis=ab/max(len,.001);vec3 origin=-a;
    float along=dot(origin,axis),slope=dot(direction,axis);vec3 x=origin-axis*along,y=direction-axis*slope;
    float aa=dot(y,y),bb=dot(x,y),cc=dot(x,x)-radius*radius;vec2 radial=vec2(-1e7,1e7);
    if(aa<.000001){if(cc>0.0)return vec2(1,-1);}else{float disc=bb*bb-aa*cc;if(disc<0.0)return vec2(1,-1);radial=vec2(-bb-sqrt(disc),-bb+sqrt(disc))/aa;}
    vec2 cap=vec2(-1e7,1e7);if(abs(slope)<.000001){if(along<0.0||along>len)return vec2(1,-1);}else{cap=vec2(-along,len-along)/slope;cap=vec2(min(cap.x,cap.y),max(cap.x,cap.y));}
    return vec2(max(radial.x,cap.x),min(radial.y,cap.y));
}
float phaseScale(){return Animation.y<.5?.5+min(.5,Animation.x*.22):Animation.y<1.5?1.0:Animation.y<2.5?min(1.0,.15+Animation.x*2.3):1.0;}
vec3 palette(float family,float hot){
    if(family<.5)return mix(vec3(.12,.38,.9),vec3(1.4,1.8,2.4),hot);
    if(family<1.5)return mix(vec3(.22,.055,.55),mix(vec3(.18,.55,1.6),vec3(1.9,1.1,2.1),hot),hot*.85+.1);
    if(family<2.5)return mix(vec3(.22,.36,.48),vec3(1.0,1.35,1.7),hot);
    if(family<3.5)return mix(vec3(.015,.38,.035),vec3(.7,2.2,.6),hot);
    if(family<4.5)return mix(vec3(.12,.06,.3),vec3(.85,.55,1.7),hot);
    if(family<5.5)return mix(vec3(.35,.1,.16),vec3(1.35,.74,.42),hot);
    if(family<6.5)return mix(vec3(.12,.19,.22),vec3(.65,.85,.9),hot);
    if(family<7.5)return mix(vec3(.16,.15,.35),vec3(.72,.64,1.3),hot);
    if(family<8.5)return mix(vec3(.45,.39,.25),vec3(1.0,.92,.72),hot);
    if(family<9.5)return mix(vec3(.012,.025,.08),vec3(.11,.3,.58),hot);
    if(family<10.5)return mix(vec3(.012,.13,.35),vec3(.12,.66,1.2),hot);
    if(family<11.5)return mix(vec3(.11,.05,.22),vec3(1.05,.78,1.5),hot);
    return mix(vec3(.2,.31,.37),vec3(.8,1.1,1.3),hot);
}
vec4 field(vec3 world,float footprint,out vec2 distortion){
    float r=max(Dimensions.x,.25),h=max(Dimensions.y,1.0),time=Animation.x,seed=Animation.w*11.0;
    vec3 local=world-Centre;vec3 q=local/r;float radial=length(q.xz),sy=local.y/h;
    float envelope=1.0-smoothstep(.83,1.12,length(vec3(q.x,sy*1.8,q.z)));
    float density=0.0,hot=0.0,refraction=0.0;distortion=vec2(0);
    vec3 line=SegmentEnd-SegmentStart;float len=length(line);
    if(Dimensions.w>.5&&len>1.0){
        vec3 axis=line/len;float along=clamp(dot(world-SegmentStart,axis),0.0,len);vec3 offset=world-SegmentStart-axis*along;
        float distance=length(offset);if(distance>r*1.4)return vec4(0);
        float strand=layers(offset*(2.5/r)+axis*(along*.5-time*5.0)+seed);
        float core=ridge(distance,r*.12),sheath=ridge(distance-r*.38,r*.18)*strand;
        density=(core*2.1+sheath*.6)/max(r,.5);hot=core*.8+strand*.4;
        distortion=normalize(offset.xy+vec2(.01))*sheath*.002;refraction=.12;
        float lineIntensity=Family<3.5?2.0:1.2;
        return vec4(palette(Family,clamp(hot,0.0,1.0))*lineIntensity,density);
    }
    if(envelope<=0.0)return vec4(0);
    if(Family<.5){
        // A conducting channel, wandering branches and a gathering canopy.
        vec3 a=q;float yy=sy+.5;vec2 bend=vec2(sin(yy*18.0+time*7.0),cos(yy*13.0-time*8.0))*.09;
        float channel=ridge(length(a.xz-bend),.025+footprint/r*.3);
        float branches=ridge(sin(atan(q.z,q.x)*5.0+yy*16.0+time*2.0),.1)*ridge(radial-(.1+yy*.65),.055);
        float cloud=ridge(yy-.85,.13)*ridge(radial-.25,.38)*layers(q*5.0-vec3(time*.3,0,0));
        density=(channel*2.5+branches*.55+cloud*.24)*envelope/r;hot=clamp(channel+branches,0.0,1.0);
    }else if(Family<1.5||Family>5.5&&Family<6.5){
        // Nested rotating sheaths; ascending cells break up the continuous flow.
        float spinRate=Family<1.5?.8:.55;
        float angle=time*spinRate+sy*5.7;
        vec3 p=spin(vec3(q.x,sy*2.0,q.z),angle);
        vec3 drift=vec3(layers(p*2.2+vec3(0,time*.3,seed)),layers(p*2.3-vec3(seed,0,time*.3)),layers(p*2.1+vec3(time*.4,seed,0)))-.5;
        float n=layers(p*7.3+drift*1.6+vec3(seed,-time*1.9,0));
        float curl=layers(spin(p*2.0,-sy*3.0)+drift+vec3(time*.2,0,seed));
        float waist=.14+.46*pow(clamp(sy+.6,0.0,1.2),1.6);
        float sheath=ridge(radial-waist-(curl-.5)*.21,.11+(n*.07));
        float ribs=pow(max(0.0,.5+.5*sin(atan(p.z,p.x)*7.0+sy*16.0-time*4.0+curl*9.0)),5.0);
        float cells=pow(max(0.0,n-.3)*1.9,2.0);
        float core=ridge(length(vec3(q.x,sy*1.1,q.z)),.18);
        if(Animation.y>1.5&&Animation.y<2.5){float front=clamp(time*1.5,.08,1.0);sheath+=ridge(length(vec3(q.x,sy*1.5,q.z))-front,.095)*(n+.3);}
        float billows=pow(clamp(n*1.4-.35,0.0,1.0),2.0)*ridge(radial-waist,.3);
        float sheathIntensity=Family<1.5?1.8:1.1;
        density=(sheath*(.12+cells*3.2+ribs*.5)+billows*1.8+core*.55)*envelope/r*sheathIntensity;
        hot=clamp(ribs*.35+core*.7+pow(cells,2.0)*1.8,0.0,1.0);refraction=Family<1.5?.22:.6;
        distortion=vec2(p.z,-p.x)*(sheath+core)*.003;
    }else if(Family<2.5){
        float wave=dot(q,normalize(vec3(.75,.3,.45)))-fract(time*.65)*1.8+.9;
        float sheets=ridge(wave,.07)+.45*ridge(wave+.3,.045);
        float flowing=layers(q*6.0-vec3(time*2.0,time*.4,0));density=sheets*(.35+flowing)*envelope/r;hot=flowing;refraction=.7;distortion=q.xy*sheets*.002;
    }else if(Family<3.5){
        float sparks=pow(max(0.0,agNoise(q*24.0-vec3(0,time*4.0,0))-.74)*4.0,3.0);
        density=(ridge(radial-.3,.04)*ridge(sy,.35)+sparks*.12)*envelope/r;hot=1.0;
    }else if(Family<4.5){
        vec3 p=spin(q,time*.08+seed);float slabs=min(abs(p.x),min(abs(p.y),abs(p.z)));
        density=ridge(slabs,.012)*(.25+.75*agNoise(floor(p*10.0)+seed))*envelope/r*.7;hot=.7;refraction=.75;distortion=p.xy*.003;
    }else if(Family<5.5){
        vec3 p=vec3(q.x,sy*2.5,q.z)*7.0;vec3 cell=fract(p)-.5;
        float node=ridge(length(cell),.11);float filaments=max(ridge(length(cell.xy),.018),ridge(length(cell.yz),.018));
        float pulse=.35+.65*pow(max(0.0,sin(length(q)*15.0-time*5.5+seed)),4.0);
        density=(node*1.6+filaments*.38)*pulse*envelope/r;hot=node+.4;
    }else if(Family<7.5){
        float strata=ridge(sin((sy+.6)*13.0+time*.7),.19);
        float motes=pow(max(0.0,agNoise(vec3(q.x*17.0,sy*13.0-time,q.z*17.0))-.75)*4.0,2.0);
        density=(strata*.2+motes*.25)*ridge(radial-.5,.35)*envelope/r;hot=motes+.3;refraction=.4;distortion=q.xz*strata*.001;
    }else if(Family<8.5){
        // Fine airborne fibres surround the folded solid paper; no opaque fog substitute.
        float fibre=ridge(sin(q.x*68.0+q.z*41.0+time*2.0),.025)*ridge(sy,.16);
        density=fibre*envelope/r*.12;hot=.4;
    }else if(Family<9.5){
        float streams=ridge(sin(atan(q.z,q.x)*6.0-radial*10.0+time*2.1),.23);
        density=streams*ridge(sy+.12,.055)*envelope/r*2.0;hot=layers(q*9.0+vec3(time,0,0));refraction=.9;distortion=q.xz*streams*.004;
    }else if(Family<10.5){
        vec3 p=spin(vec3(q.x,sy*2.0,q.z),time*.9+sy*5.0);float n=layers(p*8.0-vec3(0,time*2.0,0));
        float water=ridge(radial-(.17+.45*(sy+.5)),.08+(n*.045));
        density=water*(.4+n)*envelope/r*1.8;hot=n*.65;refraction=.85;distortion=p.xz*water*.006;
    }else if(Family<11.5){
        vec3 p=spin(vec3(q.x,sy*2.0,q.z),seed+time*.12)*4.0;vec3 cell=fract(p)-.5;
        float facet=abs(cell.x)+abs(cell.y)*.7+abs(cell.z);float crystal=ridge(facet-.29,.035);
        density=crystal*envelope/r*.48;hot=pow(max(0.0,1.0-facet),4.0);refraction=.5;distortion=cell.xy*crystal*.003;
    }else{
        vec3 p=spin(vec3(q.x,sy*2.0,q.z),time*.13);float n=layers(p*7.0+vec3(0,time*.7,seed));
        float shell=ridge(length(vec3(q.x,sy*2.0,q.z))-.65,.12);
        density=shell*max(0.0,n-.18)*envelope/r*1.4;hot=n*.6;refraction=.18;distortion=p.xz*shell*.001;
    }
    // Match the native skill's functional silhouette while retaining the category material.
    if(Family>1.5){
        if(Form>.5&&Form<1.5)density*=ridge(length(vec3(q.x,sy*1.8,q.z))-.7,.18)*1.8;
        else if(Form>1.5&&Form<2.5){float wing=abs(q.x)*.45+.12;density*=ridge(sy-wing,.14)*1.6;}
        else if(Form>2.5&&Form<3.5)density*=ridge(radial-.24,.32)*(1.0+.25*sin(sy*20.0-time*5.0));
        else if(Form>3.5&&Form<4.5)density*=ridge(length(vec3(q.x,sy*1.8,q.z)),.45)*1.8;
        else if(Form>4.5)density*=ridge(length(vec3(q.x,sy*1.8,q.z))-fract(time*.6),.055)*1.4;
    }
    vec3 colour=palette(Family,clamp(hot,0.0,1.0));
    // Keep the scalar conditional separate: optional shader AST preprocessors can
    // lose the required grouping of a compound vector *= conditional expression.
    float categoryIntensity=Family<1.5?2.6:Family<3.5?1.5:Family<5.5?1.1:Family<6.5?.65:1.0;
    colour*=categoryIntensity;
    distortion*=refraction;return vec4(colour,max(0.0,density));
}
void main(){
    vec4 f=InverseProjection*vec4(uv*2.0-1.0,1.0,1.0);vec3 viewRay=normalize(f.xyz/f.w);vec3 direction=normalize(mat3(InverseView)*viewRay);
    vec3 segment=SegmentEnd-SegmentStart;float lengthSegment=length(segment);bool line=Dimensions.w>.5&&lengthSegment>1.0;
    vec3 boundCentre=line?(SegmentStart+SegmentEnd)*.5:Centre;
    float bound=line?lengthSegment*.5+Dimensions.x*1.5:max(Dimensions.x*1.2,Dimensions.y*.9);
    vec2 range=line?finiteTube(direction,SegmentStart,SegmentEnd,Dimensions.x*1.4):sphere(vec3(0),direction,boundCentre,bound);float near=max(1.05,range.x),far=range.y;if(far<=near)discard;
    float scene=texture(SceneDepth,uv).r;
    if(scene<.999999){vec4 depthPoint=InverseProjection*vec4(uv*2.0-1.0,scene*2.0-1.0,1.0);float limit=length(depthPoint.xyz/depthPoint.w);far=min(far,limit);}
    if(far<=near)discard;
    int count=int(clamp(Steps,12.0,56.0));float stepLength=(far-near)/float(count);
    float jitter=random3(vec3(gl_FragCoord.xy,7.0));float at=near+stepLength*jitter;
    vec3 radiance=vec3(0);float transmission=1.0;vec2 bend=vec2(0);float first=far;
    for(int i=0;i<56;i++){
        if(i>=count||transmission<.015)break;
        vec2 distortion;vec4 sampleValue=field(direction*at,stepLength,distortion);
        float absorb=1.0-exp(-sampleValue.a*stepLength*Animation.z*phaseScale()*2.4);
        if(absorb>.001){first=min(first,at);radiance+=transmission*absorb*sampleValue.rgb;bend+=distortion*transmission*absorb;transmission*=1.0-absorb;}
        at+=stepLength;
    }
    float alpha=1.0-transmission;if(alpha<.002)discard;
    if(length(bend)>.00001){vec3 refracted=texture(SceneColor,clamp(uv+bend,vec2(.001),vec2(.999))).rgb;radiance+=refracted*alpha*.35;}
    vec4 depth=ProjectionMatrix*ViewRotation*vec4(direction*first,1.0);gl_FragDepth=clamp(depth.z/max(.001,depth.w)*.5+.5,0.0,1.0);
    fragColor=vec4(radiance,alpha)*vertexColor;
}
