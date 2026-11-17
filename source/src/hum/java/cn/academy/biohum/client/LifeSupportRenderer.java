package cn.academy.biohum.client;

import cn.academy.biohum.BioHum;
import cn.academy.biohum.HumNpc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Vessel, hoses and meters in world space; the humanoid rig moves independently in the liquid. */
final class LifeSupportRenderer {
    private static final int SIDES=24;
    private static final double STEP=Math.PI*2/SIDES;
    private static final RenderType ALLOY=RenderType.entityCutoutNoCull(tex("alloy"));
    private static final RenderType MONITOR=RenderType.entityCutoutNoCull(tex("monitor"));
    private static final RenderType HOSE=RenderType.entityCutoutNoCull(tex("hose"));
    private static final RenderType HAIR=RenderType.entityCutoutNoCull(tex("silver_strands"));
    private static final RenderType FLUID=RenderType.entityTranslucent(tex("fluid"));
    private static final RenderType FX=RenderType.entityTranslucent(tex("white"));

    private static ResourceLocation tex(String name){return ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/life_support/"+name+".png");}

    static double height(HumNpc npc,float age){return 3.05*npc.phaseScale()+Math.sin(age*.061)*.055;}

    static void frame(HumNpc npc, float partial, PoseStack stack, MultiBufferSource buffers,int light) {
        var pose=stack.last();
        light=Math.max(light,LightTexture.pack(11,0));
        var alloy=buffers.getBuffer(ALLOY);
        band(alloy,pose,1.50,0,.27,0xffabb4be,light);
        band(alloy,pose,1.40,.27,.40,0xffc6cfcf,light);
        band(alloy,pose,1.43,3.49,3.68,0xffb7c2c6,light);
        band(alloy,pose,1.50,3.68,3.87,0xffa1b0ba,light);
        band(alloy,pose,1.18,3.87,3.99,0xffa1b0ba,light);
        cap(alloy,pose,1.42,.28,0xff82929f,light);
        cap(alloy,pose,1.29,3.71,0xff95a0a9,light);
        for(int i=0;i<3;i++)band(alloy,pose,1.49-i*.07,3.73+i*.105,3.83+i*.105,0xffc4ccd5,light);
        for(int i=1;i<=4;i++){
            double a=(i-.5)*Math.PI*.5;
            Vec3 at=new Vec3(Math.cos(a)*1.37,0,Math.sin(a)*1.37);
            box(alloy,pose,at.add(-.092,.34,-.092),at.add(.092,3.72,.092),0xffadb3bf,light);
        }
        // Anchor the palette to how the vessel itself was installed, so turning the head never turns the console.
        double front=(npc.chamberInstalled()?0:npc.yBodyRot)*Mth.DEG_TO_RAD;
        Vec3 facing=new Vec3(-Math.sin(front),0,Math.cos(front));
        Vec3 across=new Vec3(facing.z,0,-facing.x);
        var frontPos=facing.scale(1.48);
        var a=frontPos.add(across.scale(-.49)).add(0,.43,0);
        var b=frontPos.add(across.scale(.49)).add(0,.43,0);
        face(alloy,pose,a.add(facing.scale(.15)),b.add(facing.scale(.15)),
                b.add(0,.47,0),a.add(0,.47,0),0xffc4cdd3,light);
        var panel=buffers.getBuffer(MONITOR);
        face(panel,pose,a.add(across.scale(.04)).add(facing.scale(.152)).add(0,.04,0),
                b.add(across.scale(-.04)).add(facing.scale(.152)).add(0,.04,0),
                b.add(across.scale(-.04)).add(facing.scale(.016)).add(0,.41,0),
                a.add(across.scale(.04)).add(facing.scale(.016)).add(0,.41,0),0xffffffff,LightTexture.FULL_BRIGHT);

        double phase=npc.tickCount+partial;
        var hose=buffers.getBuffer(HOSE);
        for(int i=0;i<7;i++){
            double angle=i*Math.PI*2/7+.23;
            Vec3 start=radial(i%2==0?1.02:1.23,3.85,angle);
            Vec3 end=radial(.66+i*.13,npc.chamberInstalled()?5.42:4.67,angle+.37);
            Vec3 mid=start.lerp(end,.5).add(Math.cos(angle)*(.41+Math.sin(phase*.023+i)*.04),.11,
                    Math.sin(angle)*(.41+Math.sin(phase*.023+i)*.04));
            hose(hose,pose,start,mid,.057+i%3*.019,light);
            hose(hose,pose,mid,end,.057+i%3*.019,light);
        }

        var hair=buffers.getBuffer(HAIR);
        double age=npc.tickCount+partial;
        double head=height(npc,(float)age)-1.71*npc.phaseScale();
        // Position hair behind the visible jaw and allow the tips to drift down, with gravity.
        Vec3 back=facing.scale(-.17);
        for(int i=0;i<5;i++){
            double offset=(i-2)*.105;
            Vec3 root=back.add(across.scale(offset)).add(0,head+.04,0);
            Vec3 tip=root.add(across.scale(Math.sin(age*.054+i*1.21)*.14)).add(0,-.66-.085*(i%3),0).add(back.scale(.57));
            Vec3 width=across.scale(.086+i%2*.023);
            face(hair,pose,tip.subtract(width.scale(.74)),tip.add(width.scale(.74)),
                    root.add(width),root.subtract(width),0xffe0e4ee,LightTexture.pack(14,2));
        }
    }

    static void liquid(HumNpc npc, float partial, PoseStack stack, MultiBufferSource buffers) {
        var p=stack.last();
        var liquid=buffers.getBuffer(FLUID);
        double time=npc.tickCount+partial;
        Vec3 viewer=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(npc.position());
        for(int i=0;i<SIDES;i++){
            double a=i*STEP,b=(i+1)*STEP;
            Vec3 aa=radial(1.355,.38,a),bb=radial(1.355,.38,b);
            boolean near=viewer.dot(radial(1,0,(a+b)*.5))>=0;
            float u0=i/8f,u1=(i+1)/8f;
            int color=near?0x35ffd1da:0xc7ea6486;
            float flow=(float)(time*.0042%1);
            quad(liquid,p,aa,bb,bb.add(0,3.11,0),aa.add(0,3.11,0),u0,1+flow,u1,flow,color,LightTexture.FULL_BRIGHT);
        }
        var lights=buffers.getBuffer(FX);
        int mode=npc.chamberSignalActive()?npc.chamberSignal():0;
        int red=mode==2?0xff7139:mode==1?0x83ffe9:0xd66b85;
        for(int slice=0;slice<SIDES;slice++){
            double a=slice*STEP,b=(slice+1)*STEP;
            Vec3 aa=radial(1.427,.37,a),bb=radial(1.427,.37,b);
            int color=(mode==0?0xaa000000:0xeb000000)|red;
            face(lights,p,aa,bb,bb.add(0,.027,0),aa.add(0,.027,0),color,LightTexture.FULL_BRIGHT);
            aa=radial(1.438,3.52,a);bb=radial(1.438,3.52,b);
            face(lights,p,aa,bb,bb.add(0,.026,0),aa.add(0,.026,0),color,LightTexture.FULL_BRIGHT);
        }
        Vec3 x=viewer.cross(new Vec3(0,1,0)).normalize();
        if(x.lengthSqr()<.1)x=new Vec3(1,0,0);
        for(int i=0;i<18;i++){
            double age=(time*(.012+i%3*.004)+i*.127)%1;
            var at=radial(.53+(.061*i)% .77,.45+age*2.94,i*2.399+time*.012);
            double size=.021+(i%5)*.008;
            Vec3 right=x.scale(size),up=new Vec3(0,size*.83,0);
            int color=((int)(205*Math.sin(age*Math.PI))<<24)|0xffaccd;
            face(lights,p,at.subtract(right).subtract(up),at.add(right).subtract(up),
                    at.add(right).add(up),at.subtract(right).add(up),color,LightTexture.FULL_BRIGHT);
        }
    }

    private static void band(VertexConsumer v,PoseStack.Pose p,double r,double bottom,double top,int color,int light){
        for(int i=0;i<SIDES;i++){
            double a=i*STEP,b=(i+1)*STEP;
            quad(v,p,radial(r,bottom,a),radial(r,bottom,b),radial(r,top,b),radial(r,top,a),
                    i/4f,1,(i+1)/4f,0,color,light);
        }
    }

    private static void cap(VertexConsumer v,PoseStack.Pose p,double r,double y,int color,int light) {
        // Camera views the circular rim from different elevations.
        for(int i=0;i<SIDES;i++){
            var center=new Vec3(0,y,0);
            face(v,p,center,radial(r,y,(i+1)*STEP),radial(r,y,i*STEP),center,color,light);
        }
    }

    private static void box(VertexConsumer v,PoseStack.Pose p,Vec3 low,Vec3 high,int color,int light){
        Vec3[] bottom={low,new Vec3(high.x,low.y,low.z),new Vec3(high.x,low.y,high.z),new Vec3(low.x,low.y,high.z)};
        for(int i=0;i<4;i++){
            Vec3 a=bottom[i],b=bottom[(i+1)%4];
            face(v,p,a,b,new Vec3(b.x,high.y,b.z),new Vec3(a.x,high.y,a.z),color,light);
        }
    }

    private static void hose(VertexConsumer v, PoseStack.Pose p,Vec3 start,Vec3 end,double r,int light){
        Vec3 side=end.subtract(start).cross(new Vec3(0,0,1)).normalize(), up=end.subtract(start).cross(side).normalize();
        for(int i=0;i<6;i++){
            double a=i*Math.PI/3,b=(i+1)*Math.PI/3;
            Vec3 x=side.scale(r*Math.cos(a)).add(up.scale(r*Math.sin(a)));
            Vec3 y=side.scale(r*Math.cos(b)).add(up.scale(r*Math.sin(b)));
            face(v,p,start.add(x),start.add(y),end.add(y),end.add(x),0xffd0a5a0,light);
        }
    }

    private static Vec3 radial(double r,double y,double a){return new Vec3(r*Math.cos(a),y,r*Math.sin(a));}

    private static void face(VertexConsumer v,PoseStack.Pose p,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int col,int light){
        quad(v,p,a,b,c,d,0,1,1,0,col,light);
    }

    private static void quad(VertexConsumer v,PoseStack.Pose p,Vec3 a,Vec3 b,Vec3 c,Vec3 d,
                             float u0,float v0,float u1,float v1,int col,int light){
        Vec3 n=b.subtract(a).cross(c.subtract(a)).normalize();
        if(n.lengthSqr()<.1)n=new Vec3(0,1,0);
        vertex(v,p,a,u0,v0,n,col,light);
        vertex(v,p,b,u1,v0,n,col,light);
        vertex(v,p,c,u1,v1,n,col,light);
        vertex(v,p,d,u0,v1,n,col,light);
    }

    private static void vertex(VertexConsumer v,PoseStack.Pose p,Vec3 a,float u,float w,Vec3 n,int col,int light){
        v.addVertex(p,(float)a.x,(float)a.y,(float)a.z).setColor(col).setUv(u,w)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,(float)n.x,(float)n.y,(float)n.z);
    }

    private LifeSupportRenderer(){}
}
