package cn.academy.port.client.canon;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Short-lived screen-refraction bubbles placed in the world (SH1 heat haze and space warp, SH5 chromatic tear) for effects that are
 * not drawn by a canon script, such as the original four systems. Each grows, holds and fades on its own clock. Drawn through the
 * shared canvas, so they fall back to nothing when shaders are unavailable.
 */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonWarps {
    private static final int MAX=40;
    private record Warp(Vec3 at,double size,float strength,boolean dispersion,float[] tint,int life,int delay,int[] age) {}
    private static final List<Warp> LIVE=new ArrayList<>();
    private CanonWarps() {}

    static void add(Vec3 at,double size,float strength,boolean dispersion,float[] tint,int life,int delay){
        if(LIVE.size()>=MAX)LIVE.remove(0);LIVE.add(new Warp(at,size,strength,dispersion,tint,life,delay,new int[]{0}));
    }
    static boolean any(){return !LIVE.isEmpty();}
    static void clear(){LIVE.clear();}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level==null){LIVE.clear();return;}
        if(mc.isPaused())return;LIVE.removeIf(w->++w.age()[0]>=w.life()+w.delay());
    }
    static void draw(CanonFx.Canvas c,double partial){
        for(Warp w:LIVE){double a=w.age()[0]+partial-w.delay();if(a<0)continue;double p=a/w.life();
            double grow=CanonFxScripts.easeOut(Math.min(1,p*3)),fade=p<.4?1:1-(p-.4)/.6;
            c.refract("fx4:"+(w.dispersion()?"dm_refraction_normal":"common_noise_tile"),w.dispersion(),w.at(),w.size()*(.4+.6*grow),(float)(w.strength()*fade),w.tint());}
    }
}
