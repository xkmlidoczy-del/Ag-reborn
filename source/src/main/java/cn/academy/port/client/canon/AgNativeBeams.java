package cn.academy.port.client.canon;

import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import cn.academy.port.macro.MacroVfx;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

/** Small native rays retain their decoded endpoints, scale, lifetime and cadence. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class AgNativeBeams {
    private static int remaining=2048;
    private static int drawn;
    private static Vec3 lastFrom;
    private AgNativeBeams(){}
    @SubscribeEvent public static void frame(RenderFrameEvent.Pre event){
        remaining=VisualTierConfig.tier()==VisualTier.CINEMATIC?2048:VisualTierConfig.tier()==VisualTier.STANDARD?1024:512;drawn=0;lastFrom=null;
    }
    /** Positions and eye use the caller's matrix coordinate system. True means the enhanced path owns this ray. */
    public static boolean draw(MultiBufferSource.BufferSource buffers,Matrix4f matrix,Vec3 eye,Vec3 from,Vec3 to,double width,double alpha,float[] tint){
        if(!AgEffectMaterials.beams())return false;
        if(AgVolumeRenderer.shadow()||!MacroVfx.finite(from)||!MacroVfx.finite(to)||!Double.isFinite(width)||!Double.isFinite(alpha)
                ||width<=0||alpha<=.003||from.distanceToSqr(to)>192*192||MacroVfx.distanceSquared(eye,from,to)>160*160)return true;
        int sides=VisualTierConfig.tier()==VisualTier.CINEMATIC?12:8;
        int segments=Math.min(8,Math.max(1,Math.min(192,remaining)/(sides*2)));if(remaining<sides*2)return true;
        Vec3 axis=to.subtract(from);double length=axis.length();if(length<.01)return true;axis=axis.scale(1/length);
        Vec3 right=axis.cross(new Vec3(0,1,0));right=right.lengthSqr()<1e-10?new Vec3(1,0,0):right.normalize();Vec3 up=right.cross(axis).normalize();
        double projection=Math.max(.1,Math.abs(RenderSystem.getProjectionMatrix().m11()));int height=Math.max(1,Minecraft.getInstance().getWindow().getHeight());
        for(int layer=0;layer<2;layer++){
            var type=AgEffectMaterials.type(6+layer);var out=buffers.getBuffer(type);
            for(int j=0;j<segments;j++){
                double d0=length*j/segments,d1=length*(j+1)/segments;Vec3 c0=from.add(axis.scale(d0)),c1=from.add(axis.scale(d1));
                double r0=Math.max(width*(layer==0?.5:1.8),eye.distanceTo(c0)*(layer==0?2:5)/(height*projection));
                double r1=Math.max(width*(layer==0?.5:1.8),eye.distanceTo(c1)*(layer==0?2:5)/(height*projection));
                for(int k=0;k<sides;k++){
                    double v0=k/(double)sides,v1=(k+1)/(double)sides;Vec3 a=right.scale(Math.cos(v0*Math.PI*2)).add(up.scale(Math.sin(v0*Math.PI*2))),b=right.scale(Math.cos(v1*Math.PI*2)).add(up.scale(Math.sin(v1*Math.PI*2)));
                    vertex(out,matrix,c0.add(a.scale(r0)),d0,v0,tint,alpha);vertex(out,matrix,c1.add(a.scale(r1)),d1,v0,tint,alpha);
                    vertex(out,matrix,c1.add(b.scale(r1)),d1,v1,tint,alpha);vertex(out,matrix,c0.add(b.scale(r0)),d0,v1,tint,alpha);
                }
            }
            buffers.endBatch(type);
        }
        remaining-=segments*sides*2;drawn++;if(Boolean.getBoolean("academy.qa.signatureBudget"))lastFrom=from;return true;
    }
    private static void vertex(VertexConsumer out,Matrix4f m,Vec3 p,double u,double v,float[] rgb,double a){
        out.addVertex(m,(float)p.x,(float)p.y,(float)p.z).setUv((float)u,(float)v).setColor(rgb[0],rgb[1],rgb[2],(float)Math.clamp(a,0,1));
    }
}
