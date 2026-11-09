package cn.academy.port.client.canon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Bounded physical particles for canon effects (plan codes PT1-PT6): gravity, drag, spin, landing on real blocks, and an optional
 * secondary splash and ground decal on landing. Ticked at 20 Hz and interpolated when drawn; drawn through the shared quad budget.
 */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonParticles {
    static final int MAX=700;
    /** What happens when a particle touches ground: nothing, splash into small droplets, or splash and leave a decal. */
    enum Land { NONE, STOP, SPLASH, SPLASH_DECAL }
    static final class P {
        Vec3 pos,prev,vel;final String tex;final int cell,life;final double gravity,drag,size,spin0,spinRate;final float[] rgb;final float alpha;
        final CanonFx4.Mode mode;final Land land;final boolean stretch;int age,delay;boolean landed;
        P(String tex,int cell,Vec3 pos,Vec3 vel,double gravity,double drag,double size,double spin0,double spinRate,int life,float[] rgb,float alpha,
          CanonFx4.Mode mode,Land land,boolean stretch,int delay){
            this.tex=tex;this.cell=cell;this.pos=pos;this.prev=pos;this.vel=vel;this.gravity=gravity;this.drag=drag;this.size=size;this.spin0=spin0;
            this.spinRate=spinRate;this.life=life;this.rgb=rgb;this.alpha=alpha;this.mode=mode;this.land=land;this.stretch=stretch;this.delay=delay;
        }
    }
    private static final List<P> LIVE=new ArrayList<>();
    private static Level world;
    private CanonParticles() {}

    static P spawn(String tex,int cell,Vec3 pos,Vec3 vel,double gravity,double drag,double size,double spinRate,int life,float[] rgb,float alpha,
                   CanonFx4.Mode mode,Land land,boolean stretch,int delay){
        if(LIVE.size()>=MAX)LIVE.remove(0);
        var p=new P(tex,cell,pos,vel,gravity,drag,size,CanonFxScripts.rnd(LIVE.size()+(int)(pos.x*13))*6.28,spinRate,life,rgb,alpha,mode,land,stretch,delay);
        LIVE.add(p);return p;
    }
    static void clear(){LIVE.clear();}
    static boolean any(){return !LIVE.isEmpty();}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level!=world){LIVE.clear();world=mc.level;}
        if(world==null||mc.isPaused()||LIVE.isEmpty())return;
        var born=new ArrayList<P>();
        LIVE.removeIf(p->{
            if(p.delay>0){p.delay--;p.prev=p.pos;return false;}
            if(++p.age>=p.life)return true;
            p.prev=p.pos;if(p.landed)return false;
            Vec3 next=p.pos.add(p.vel);p.vel=p.vel.scale(1-p.drag).add(0,-p.gravity,0);
            if(p.land!=Land.NONE&&solid(next)){
                p.landed=true;p.pos=new Vec3(next.x,Math.floor(next.y)+1.01,next.z);p.vel=Vec3.ZERO;
                if(p.land==Land.SPLASH||p.land==Land.SPLASH_DECAL)for(int k=0;k<3;k++){
                    double a=CanonFxScripts.rnd(p.age*7+k)*6.28,s=.04+.04*CanonFxScripts.rnd(k+p.cell);
                    born.add(new P(p.tex,(p.cell+k+1)%16,p.pos,new Vec3(Math.cos(a)*s,.09+.05*CanonFxScripts.rnd(k+3),Math.sin(a)*s),.03,.02,p.size*.45,0,0,
                            10,p.rgb,p.alpha*.8f,p.mode,Land.NONE,true,0));
                }
                if(p.land==Land.SPLASH_DECAL)CanonDecals.add("hh_wet_decal",p.pos,.25+p.size*2,p.rgb,.55f,80);
                return p.land!=Land.STOP;
            }
            p.pos=next;return false;
        });
        for(P b:born)if(LIVE.size()<MAX)LIVE.add(b);
    }
    private static boolean solid(Vec3 p){
        var pos=BlockPos.containing(p);if(!world.hasChunkAt(pos))return false;var state=world.getBlockState(pos);
        return !state.getCollisionShape(world,pos).isEmpty()||!state.getFluidState().isEmpty();
    }

    /** Draws live particles, interpolated between ticks. */
    static void draw(CanonFx.Canvas c,double partial){
        for(P p:LIVE){
            if(c.left<=0)return;if(p.delay>0)continue;
            Vec3 at=p.prev.add(p.pos.subtract(p.prev).scale(partial));
            double life01=(p.age+partial)/p.life,fade=life01<.1?life01/.1:life01>.7?1-(life01-.7)/.3:1;
            float al=(float)(p.alpha*Math.max(0,fade));if(al<=.004f)continue;
            var grid=Fx4Atlas.get(p.tex);int cell=grid.frames()>0?grid.frame(life01):p.cell;
            if(p.stretch&&!p.landed&&p.vel.lengthSqr()>1e-4){
                Vec3 d=p.vel.normalize().scale(p.size*(1+Math.min(3,p.vel.length()*8)));
                c.cellStrip("fx4:"+p.tex,p.mode,at.subtract(d),at.add(d),p.size,cell,p.rgb,al);
            }else c.cellSprite("fx4:"+p.tex,p.mode,at,p.size,p.spin0+p.spinRate*(p.age+partial),cell,p.rgb,al);
        }
    }
}
