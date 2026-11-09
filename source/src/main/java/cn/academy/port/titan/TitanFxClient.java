package cn.academy.port.titan;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import static cn.academy.port.titan.TitanParticleTypes.Shape.*;

/** Client expands a small server cast packet into a capped, themed local effect. */
@EventBusSubscriber(modid=TitanBlocks.MODID,value=Dist.CLIENT)
public final class TitanFxClient {
    private TitanFxClient() {}
    private static int shakeTicks;
    private static float shakeStrength;
    private static final double MAX_PARTICLE_DISTANCE=192;
    private static final int MAX_PER_EVENT=1000;

    public static void accept(TitanFxPayload message) {
        Minecraft mc=Minecraft.getInstance();ClientLevel level=mc.level;
        if(level==null || !TitanProfiles.all().containsKey(message.species()))return;
        if(message.slot()<0 || message.slot()>2 || message.phase()<0 || message.phase()>2)return;
        float quality=switch(mc.options.particles().get()) {
            case ALL -> 1.0f;
            case DECREASED -> .65f;
            case MINIMAL -> .32f;
        };
        float density=Mth.clamp(message.density(),.1f,2f)*quality;
        Random random=new Random(message.species().hashCode()*31L+message.age()*37L+message.phase()*101L);
        Emitter out=new Emitter(mc,level,message.species(),density,random);
        if(message.phase()==2) {
            melee(out,message.target());
            shake(mc,message.target(),14,.8f);return;
        }
        TitanProfiles.Profile profile=TitanProfiles.get(message.species());
        TitanProfiles.Ability ability=message.slot()==2?profile.second():profile.first();
        Vec3 origin=message.origin(),target=message.target();
        Vec3 center=ability.shape()==TitanProfiles.Shape.RING?origin:target;
        if(message.phase()==0)telegraph(out,ability,origin,target,center,message.age());
        else {
            impact(out,ability,origin,target,center);
            shake(mc,center,18,1.7f);
        }
        if(Boolean.getBoolean("titanfxcapture.verify"))
            System.out.println("TITAN_V6_FX: "+message.species()+" slot="+message.slot()+" phase="+message.phase()+" spawned="+out.emitted);
    }
    private static void shake(Minecraft mc,Vec3 center,int ticks,float maximum) {
        if(mc.gameRenderer.getMainCamera()==null)return;
        double distance=mc.gameRenderer.getMainCamera().getPosition().distanceTo(center);
        float attenuation=(float)Mth.clamp(1-distance/100,0,1);
        float strength=(float)(maximum*attenuation*TitanVisualConfig.SHAKE_SCALE.get());
        if(strength>shakeStrength || shakeTicks<4){shakeStrength=strength;shakeTicks=ticks;}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(shakeTicks>0)shakeTicks--;
        if(shakeTicks==0)shakeStrength=0;
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if(shakeTicks<=0 || shakeStrength<=0)return;
        Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        double time=mc.level.getGameTime()+event.getPartialTick();
        float decay=Math.min(1,shakeTicks/12f);
        event.setPitch(event.getPitch()+(float)Math.sin(time*2.3)*shakeStrength*decay);
        event.setYaw(event.getYaw()+(float)Math.sin(time*1.7+1.3)*shakeStrength*.7f*decay);
        event.setRoll(event.getRoll()+(float)Math.sin(time*2.0+.4)*shakeStrength*.5f*decay);
    }
    @SubscribeEvent public static void restoreBlend(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES)RenderSystem.defaultBlendFunc();
    }
    private static final class Emitter {
        final Minecraft mc;final ClientLevel level;final String id;final float density;final Random random;
        int emitted;
        Emitter(Minecraft mc,ClientLevel level,String id,float density,Random random){this.mc=mc;this.level=level;this.id=id;this.density=density;this.random=random;}
        int n(int base){return Math.max(3,Math.round(base*density));}
        void p(TitanParticleTypes.Shape shape,double x,double y,double z,double vx,double vy,double vz) {
            if(emitted>=MAX_PER_EVENT)return;
            Vec3 camera=mc.gameRenderer.getMainCamera().getPosition();
            if(camera.distanceToSqr(x,y,z)>MAX_PARTICLE_DISTANCE*MAX_PARTICLE_DISTANCE)return;
            // Explicit long-distance spawn bypasses vanilla's 32-block particle limiter;
            // each event is bounded and particle-quality settings are applied above.
            level.addParticle(TitanParticleTypes.get(id,shape),true,x,y,z,vx,vy,vz);emitted++;
        }
        void p(TitanParticleTypes.Shape shape,Vec3 v){p(shape,v.x,v.y,v.z,0,0,0);}
        void ring(Vec3 c,double radius,double y,int base,double phase,TitanParticleTypes.Shape shape) {
            int n=n(base);
            for(int i=0;i<n;i++) {
                double a=Math.PI*2*i/n+phase;
                p(shape==OUTLINE?shape:(i%9==0?STARBURST:shape),c.x+Math.cos(a)*radius,c.y+y,c.z+Math.sin(a)*radius,0,.015,0);
            }
        }
        void beam(Vec3 a,Vec3 b,double width,int base) {
            Vec3 direction=b.subtract(a);double len=direction.length();if(len<.01)return;
            Vec3 unit=direction.scale(1/len),right=unit.cross(new Vec3(0,1,0));
            if(right.lengthSqr()<.001)right=new Vec3(1,0,0);else right=right.normalize();
            Vec3 up=right.cross(unit).normalize();int n=n(base);
            for(int i=0;i<=n;i++) {
                double t=(double)i/n,ang=t*Math.PI*12;
                double side,vertical;
                switch(id) {
                    case "storm_drake" -> {side=((i/4)%2==0?1:-1)*width*.38;vertical=((i/7)%2==0?1:-1)*width*.25;}
                    case "crystal_mantis" -> {side=((i%3)-1)*width*.32;vertical=((i%5)-2)*width*.18;}
                    case "clockwork_rex" -> {side=((i/8)%3-1)*width*.35;vertical=((i/12)%2==0?1:-1)*width*.2;}
                    default -> {side=Math.cos(ang)*width*.35;vertical=Math.sin(ang)*width*.35;}
                }
                Vec3 point=a.lerp(b,t).add(right.scale(side)).add(up.scale(vertical));
                p(i%4==0?SLASH:WISP,point);
                if(i%8==0)p(STARBURST,a.lerp(b,t));
            }
        }
        void line(Vec3 start,Vec3 end,int base,TitanParticleTypes.Shape shape,double rise) {
            int count=n(base);
            for(int i=0;i<=count;i++) {
                double t=(double)i/count;Vec3 v=start.lerp(end,t);
                p(i%7==0?STARBURST:shape,v.x,v.y,v.z,0,rise,0);
            }
        }
        void scatter(Vec3 c,double radius,int base) {
            int n=n(base);
            for(int i=0;i<n;i++) {
                double a=random.nextDouble()*Math.PI*2,r=Math.sqrt(random.nextDouble())*radius;
                p(i%3==0?SLASH:STARBURST,c.x+Math.cos(a)*r,c.y+1+random.nextDouble()*12,c.z+Math.sin(a)*r,
                    Math.cos(a)*.04,.03+random.nextDouble()*.09,Math.sin(a)*.04);
            }
        }
    }
    private static void telegraph(Emitter out,TitanProfiles.Ability a,Vec3 origin,Vec3 target,Vec3 center,int age) {
        double phase=age*.035,r=a.radius();
        if(a.shape()==TitanProfiles.Shape.BEAM) {
            out.beam(origin,target,r,45);
        } else {
            // A sparse, low-contrast outline communicates the actual damage boundary.
            out.ring(center,r,.35,64,phase,OUTLINE);
        }
        if(age%10==0)signature(out,center,r,false,a.shape()==TitanProfiles.Shape.RING,TitanProfiles.get(out.id).second().equals(a));
    }
    private static void impact(Emitter out,TitanProfiles.Ability a,Vec3 origin,Vec3 target,Vec3 center) {
        double r=a.radius();
        if(a.shape()==TitanProfiles.Shape.BEAM)out.beam(origin,target,r,105);
        signature(out,center,r,true,a.shape()==TitanProfiles.Shape.RING,TitanProfiles.get(out.id).second().equals(a));
    }
    private static void melee(Emitter out,Vec3 center) {
        out.p(MOTIF,center.add(0,3,0));
        signature(out,center,8,false,false,false);
        out.scatter(center,4,20);
    }
    /** Species have different main silhouettes, paths, and bespoke sprites. */
    private static void signature(Emitter out,Vec3 c,double radius,boolean impact,boolean bodyCenter,boolean alternate) {
        double r=Math.max(6,radius*.82);int detail=impact?1:0;
        if(impact) {
            switch(out.id) {
                case "ironback","dune_scorpion","ossuary_dragon","clockwork_rex" -> out.p(CROWN,c.add(0,bodyCenter?23:15,0));
                case "astral_moth" -> out.p(CROWN,c.add(0,bodyCenter?30:20,0));
                case "storm_drake" -> {
                    out.p(CROWN,c.add(0,18,0));
                    if(radius>=15){out.p(CROWN,c.add(-r*.65,18,0));out.p(CROWN,c.add(r*.65,18,0));}
                }
                case "tidal_sail" -> {
                    out.p(CROWN,c.add(-r*.55,13,r*.28));out.p(CROWN,c.add(r*.55,15,-r*.35));
                }
                default -> {
                    if(radius>=15){out.p(CROWN,c.add(-r*.52,18,r*.22));out.p(CROWN,c.add(r*.52,18,-r*.22));}
                    else out.p(CROWN,c.add(0,15,0));
                }
            }
        }
        switch(out.id) {
            case "lava_rex" -> {
                if(alternate) {
                    int meteors=impact?7:3;
                    for(int i=0;i<meteors;i++) {
                        double a=i*2.399,d=r*(i==0?0:.68);Vec3 land=c.add(Math.cos(a)*d,1,Math.sin(a)*d);
                        Vec3 sky=land.add(-7,34,-3);out.line(sky,land,impact?25:10,SLASH,-.10);out.p(MOTIF,land.add(0,6,0));
                    }
                    break;
                }
                int vents=impact?7:3;
                for(int v=0;v<vents;v++) {
                    double a=v*2.399,dist=r*(v%3==0?.22:.58),x=Math.cos(a)*dist,z=Math.sin(a)*dist;
                    Vec3 vent=c.add(x,0,z);out.p(MOTIF,vent.add(0,7,0));
                    out.line(c.add(0,1,0),vent.add(0,1,0),impact?14:7,SLASH,0);
                    for(int j=0;j<out.n(impact?18:7);j++) {
                        double y=2+j*(impact?1.15:1.3),w=Math.sin(j*.8+v)*2;
                        out.p(j%3==0?STARBURST:WISP,vent.x+w,vent.y+y,vent.z-w,0,.10,0);
                    }
                }
            }
            case "frost_bastion" -> {
                int spikes=impact?10:4;
                for(int i=0;i<spikes;i++) {
                    double a=i*2.399,d=r*(.18+.72*((i%4)/3.0));Vec3 p=c.add(Math.cos(a)*d,0,Math.sin(a)*d);
                    out.p(MOTIF,p.add(0,7,0));
                    if(alternate)out.line(p.add((i%2==0?4:-4),impact?27:16,2),p.add(0,1,0),impact?16:8,SLASH,-.08);
                    else out.line(p.add(0,1,0),p.add((i%2==0?4:-4),impact?22:12,2),impact?13:7,SLASH,.02);
                }
            }
            case "storm_drake" -> {
                int bolts=impact?7:3;
                for(int i=0;i<bolts;i++) {
                    double a=i*2.399,d=r*(i==0?0:.65);Vec3 base=c.add(Math.cos(a)*d,0,Math.sin(a)*d);
                    out.p(MOTIF,base.add(0,17,0));Vec3 prev=base.add(0,33,0);
                    for(int j=1;j<=6;j++) {
                        Vec3 next=base.add((j%2==0?1:-1)*2,33-j*5.2,(j%3-1)*2);
                        out.line(prev,next,impact?8:4,SLASH,0);prev=next;
                    }
                }
            }
            case "grove_titan" -> {
                if(alternate) {
                    int n=out.n(impact?150:50);
                    for(int i=0;i<n;i++) {
                        double a=out.random.nextDouble()*Math.PI*2,d=Math.sqrt(out.random.nextDouble())*r;
                        out.p(i%6==0?STARBURST:WISP,c.x+Math.cos(a)*d,c.y+3+out.random.nextDouble()*18,c.z+Math.sin(a)*d,0,.06,0);
                    }
                    for(int i=0;i<(impact?5:2);i++)out.p(MOTIF,c.add(Math.cos(i*2.4)*r*.55,8,Math.sin(i*2.4)*r*.55));
                    break;
                }
                int roots=impact?8:4;
                for(int i=0;i<roots;i++) {
                    double a=i*Math.PI*2/roots;Vec3 previous=c.add(0,2,0);
                    for(int j=1;j<=10;j++) {
                        double t=j/10.0,d=r*t,angle=a+Math.sin(t*5+i)*.22;
                        Vec3 next=c.add(Math.cos(angle)*d,2+Math.sin(t*Math.PI)*4,Math.sin(angle)*d);
                        out.line(previous,next,impact?4:2,WISP,.02);previous=next;
                    }
                    out.p(MOTIF,previous.add(0,4,0));
                }
            }
            case "tidal_sail" -> {
                int waves=impact?4:2;
                for(int k=0;k<waves;k++) {
                    double z=(k-(waves-1)/2.0)*r*.36;
                    out.p(MOTIF,c.add((k%2==0?-1:1)*r*.4,8,z));
                    int n=out.n(impact?55:22);
                    for(int i=0;i<n;i++) {
                        double t=(double)i/Math.max(1,n-1),x=(t*2-1)*r;
                        double y=3+Math.sin(t*Math.PI)*9,z2=z+Math.sin(t*Math.PI*2+k)*3;
                        out.p(i%6==0?STARBURST:WISP,c.x+x,c.y+y,c.z+z2,.05,.04,0);
                    }
                }
            }
            case "ironback" -> {
                int frames=impact?3:1;
                for(int i=0;i<frames;i++) {
                    double size=r*(.3+i*.22),height=11+i*4;
                    Vec3 a=c.add(-size,height-size*.45,0),b=c.add(size,height-size*.45,0);
                    Vec3 d=c.add(-size,height+size*.45,0),e=c.add(size,height+size*.45,0);
                    out.line(a,b,15,SLASH,0);out.line(b,e,10,SLASH,0);out.line(e,d,15,SLASH,0);out.line(d,a,10,SLASH,0);
                    out.p(MOTIF,c.add(0,height,0));
                }
                int n=out.n(impact?100:35);
                for(int i=0;i<n;i++) {
                    double t=(double)i/n,a=t*Math.PI*8,d=r*(1-t*.9),x=Math.cos(a)*d,z=Math.sin(a)*d;
                    out.p(WISP,c.x+x,c.y+3+t*17,c.z+z,-x*.006,.02,-z*.006);
                }
            }
            case "crystal_mantis" -> {
                int blades=impact?7:3;
                for(int i=0;i<blades;i++) {
                    double a=i*Math.PI/blades;Vec3 left=c.add(-Math.cos(a)*r,2+i*1.6,-Math.sin(a)*r);
                    Vec3 right=c.add(Math.cos(a)*r,13+i*1.6,Math.sin(a)*r);
                    out.line(left,right,impact?26:12,SLASH,.01);out.p(MOTIF,left.lerp(right,.5));
                }
            }
            case "dune_scorpion" -> {
                if(!alternate) {
                    int darts=impact?10:4;
                    for(int i=0;i<darts;i++) {
                        double a=i*2.399,d=r*(.2+.7*(i%3)/2.0);
                        Vec3 end=c.add(Math.cos(a)*d,1,Math.sin(a)*d),start=end.add(-5,23,-3);
                        out.line(start,end,impact?16:7,SLASH,-.08);out.p(MOTIF,end.add(0,4,0));
                    }
                    break;
                }
                int n=out.n(impact?190:70);
                for(int i=0;i<n;i++) {
                    double t=(double)i/n,a=t*Math.PI*14,d=r*(.15+.7*t),x=Math.cos(a)*d,z=Math.sin(a)*d;
                    out.p(i%9==0?SLASH:WISP,c.x+x,c.y+1+t*32,c.z+z,-z*.006,.08,x*.006);
                }
                for(int i=0;i<(impact?4:2);i++)out.p(MOTIF,c.add(0,5+i*7,0));
            }
            case "myco_frog" -> {
                int clouds=impact?8:3;
                for(int k=0;k<clouds;k++) {
                    double a=k*2.399,d=r*(k%3==0?.15:.65);Vec3 cloud=c.add(Math.cos(a)*d,4+(k%3)*4,Math.sin(a)*d);
                    out.p(MOTIF,cloud);int n=out.n(impact?15:7);
                    for(int i=0;i<n;i++) {
                        double p=out.random.nextDouble()*Math.PI*2,q=out.random.nextDouble()*5;
                        out.p(WISP,cloud.x+Math.cos(p)*q,cloud.y+out.random.nextDouble()*7,cloud.z+Math.sin(p)*q,0,.05,0);
                    }
                }
            }
            case "ossuary_dragon" -> {
                if(!alternate) {
                    int n=out.n(impact?170:60);
                    for(int i=0;i<n;i++) {
                        double t=(double)i/n,a=t*Math.PI*10,d=r*(1-.85*t),x=Math.cos(a)*d,z=Math.sin(a)*d;
                        out.p(i%7==0?SLASH:WISP,c.x+x,c.y+3+t*17,c.z+z,-x*.01,.03,-z*.01);
                    }
                    for(int i=0;i<(impact?5:2);i++)out.p(MOTIF,c.add(Math.cos(i*2.4)*r*.55,8,Math.sin(i*2.4)*r*.55));
                    break;
                }
                int ribs=impact?8:3;
                for(int k=0;k<ribs;k++) {
                    double z=(k-(ribs-1)/2.0)*r*.2;out.p(MOTIF,c.add(0,10,z));
                    int n=out.n(impact?30:15);
                    for(int i=0;i<n;i++) {
                        double t=(double)i/Math.max(1,n-1),x=(t*2-1)*r*.7,y=2+Math.sin(t*Math.PI)*21;
                        out.p(i%4==0?SLASH:WISP,c.x+x,c.y+y,c.z+z,0,.025,0);
                    }
                }
            }
            case "clockwork_rex" -> {
                int boxes=impact?3:1;
                for(int k=0;k<boxes;k++) {
                    double s=r*(.38+k*.22),y=2+k*5;
                    Vec3 a=c.add(-s,y,-s),b=c.add(s,y,-s),d=c.add(-s,y,s),e=c.add(s,y,s);
                    out.line(a,b,18,SLASH,0);out.line(b,e,18,SLASH,0);out.line(e,d,18,SLASH,0);out.line(d,a,18,SLASH,0);
                    out.p(MOTIF,c.add(0,y+5,0));
                }
                out.line(c.add(-r,4,0),c.add(r,4,0),25,SLASH,0);
                out.line(c.add(0,4,-r),c.add(0,4,r),25,SLASH,0);
            }
            case "astral_moth" -> {
                if(alternate) {
                    int stars=impact?12:5;
                    for(int i=0;i<stars;i++) {
                        double a=i*2.399,d=r*(.15+.75*(i%4)/3.0);Vec3 end=c.add(Math.cos(a)*d,1,Math.sin(a)*d);
                        out.line(end.add(-3,28,0),end,impact?12:6,STARBURST,-.06);
                    }
                    out.p(MOTIF,c.add(-r*.45,14,0));out.p(MOTIF,c.add(r*.45,14,0));
                    break;
                }
                int n=out.n(impact?190:70);
                for(int i=0;i<n;i++) {
                    double a=(double)i/n*Math.PI*2;
                    double wing=Math.sin(a)*(Math.exp(Math.cos(a))-2*Math.cos(4*a)-Math.pow(Math.sin(a/12),5));
                    double x=wing*r*.37,y=10+Math.cos(a)*r*.42,z=Math.sin(a*2)*r*.28;
                    out.p(i%3==0?STARBURST:WISP,c.x+x,c.y+y,c.z+z,0,.025,0);
                }
                out.p(MOTIF,c.add(-r*.45,12,0));out.p(MOTIF,c.add(r*.45,12,0));
            }
            default -> out.scatter(c,r,detail==1?60:20);
        }
    }
}
