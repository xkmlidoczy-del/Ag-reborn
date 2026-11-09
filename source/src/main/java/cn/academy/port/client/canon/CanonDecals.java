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
 * Ground traces left after an effect (plan codes DC1 and LT1): wet patches, scorch, frost, ink, light spots. Each sits on the real
 * block surface below its point, fades in, then dissolves out with the noise shader (plain fade without shaders).
 */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonDecals {
    private static final int MAX=48;
    private record Decal(String tex,Vec3 pos,double radius,double spin,float[] rgb,float alpha,int life,boolean light,int order,int[] age) {}
    private static final List<Decal> LIVE=new ArrayList<>();
    private static Level world;
    private static int counter;
    private CanonDecals() {}

    /** A trace on the ground below {@code at}; skipped when there is no surface within three blocks. */
    static void add(String tex,Vec3 at,double radius,float[] rgb,float alpha,int life){place(tex,at,radius,rgb,alpha,life,false);}
    /** Additive light spot on the ground (LT1). */
    static void light(String tex,Vec3 at,double radius,float[] rgb,float alpha,int life){place(tex,at,radius,rgb,alpha,life,true);}
    private static void place(String tex,Vec3 at,double radius,float[] rgb,float alpha,int life,boolean light){
        var mc=Minecraft.getInstance();if(mc.level==null)return;world=mc.level;
        Double y=surface(at);if(y==null)return;
        if(LIVE.size()>=MAX)LIVE.remove(0);
        counter++;LIVE.add(new Decal(tex,new Vec3(at.x,y,at.z),radius,CanonFxScripts.rnd(counter)*6.28,rgb,alpha,life,light,counter%16,new int[]{0}));
    }
    /** Height of the walkable surface (block top or fluid level) at or up to three blocks below {@code at}, or null. */
    static Double surfaceAt(Vec3 at){var mc=Minecraft.getInstance();if(mc.level==null)return null;world=mc.level;return surface(at);}
    static boolean any(){return !LIVE.isEmpty();}
    private static Double surface(Vec3 at){
        var pos=BlockPos.containing(at);
        for(int i=0;i<4;i++,pos=pos.below()){
            if(!world.hasChunkAt(pos))return null;var state=world.getBlockState(pos);
            var shape=state.getCollisionShape(world,pos);
            if(!shape.isEmpty())return pos.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
            if(!state.getFluidState().isEmpty())return pos.getY()+(double)state.getFluidState().getHeight(world,pos);
        }
        return null;
    }
    static void clear(){LIVE.clear();}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level!=world){LIVE.clear();world=mc.level;}
        if(mc.isPaused())return;
        LIVE.removeIf(d->++d.age[0]>=d.life);
    }

    static void draw(CanonFx.Canvas c,double partial){
        for(Decal d:LIVE){
            if(c.left<=0)return;
            double t=(d.age[0]+partial)/d.life,in=Math.min(1,t/.08);
            // Each decal is lifted by its own small step so overlapping traces never z-fight.
            Vec3 p=d.pos.add(0,.012+d.order*.0015,0);double cs=Math.cos(d.spin)*d.radius,sn=Math.sin(d.spin)*d.radius;
            Vec3 ax=new Vec3(cs,0,sn),ay=new Vec3(-sn,0,cs);
            if(d.light)c.cellPlane("fx4:"+d.tex,CanonFx4.Mode.ADD,p,ax,ay,0,d.rgb,(float)(d.alpha*in*(1-t)));
            else{
                double out=t<.55?0:(t-.55)/.45;
                if(CanonFx4.effective(CanonFx4.Mode.DISSOLVE)==CanonFx4.Mode.DISSOLVE){
                    if(in<1)c.cellPlane("fx4:"+d.tex,CanonFx4.Mode.ALPHA,p,ax.scale(.6+.4*in),ay.scale(.6+.4*in),0,d.rgb,(float)(d.alpha*in));
                    else c.cellPlane("fx4:"+d.tex,CanonFx4.Mode.DISSOLVE,p,ax,ay,0,d.rgb,(float)out);
                }else c.cellPlane("fx4:"+d.tex,CanonFx4.Mode.ALPHA,p,ax,ay,0,d.rgb,(float)(d.alpha*in*(1-out)));
            }
        }
    }
}
