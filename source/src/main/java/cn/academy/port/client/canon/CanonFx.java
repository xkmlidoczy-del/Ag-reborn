package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfxDesign.Motif;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Glow / ribbon / fold layer for canon skills, drawn over the flat bodies from {@link CanonMeshGeometry}.
 * Presentation only. Additive light and alpha solids are separate render types; every quad is budgeted and
 * faded near the camera so first person is never covered.
 */
final class CanonFx {
    /** anchor: attached body position for this frame; yaw: actor body yaw degrees. */
    /** {@code radius}/{@code rangeCentre}: the server-checked area of the skill, or 0 when it has none. */
    record View(String skill,String category,Motif motif,Vec3 origin,Vec3 endpoint,Vec3 anchor,float yaw,int phase,double age,int life,
                boolean local,int tier,net.minecraft.world.phys.AABB targetBox,double radius,Vec3 rangeCentre,Vec3 barrierCentre,Vec3 aimAxis) {
        View(String skill,String category,Motif motif,Vec3 origin,Vec3 endpoint,Vec3 anchor,float yaw,int phase,double age,int life,
             boolean local,int tier,net.minecraft.world.phys.AABB targetBox,double radius,Vec3 rangeCentre){
            this(skill,category,motif,origin,endpoint,anchor,yaw,phase,age,life,local,tier,targetBox,radius,rangeCentre,null,null);
        }
        View(String skill,String category,Motif motif,Vec3 origin,Vec3 endpoint,Vec3 anchor,float yaw,int phase,double age,int life,boolean local,int tier){
            this(skill,category,motif,origin,endpoint,anchor,yaw,phase,age,life,local,tier,null,0,null);
        }
        View(String skill,String category,Motif motif,Vec3 origin,Vec3 endpoint,Vec3 anchor,float yaw,int phase,double age,int life,
             boolean local,int tier,net.minecraft.world.phys.AABB targetBox){
            this(skill,category,motif,origin,endpoint,anchor,yaw,phase,age,life,local,tier,targetBox,0,null);
        }
    }

    private static final int QUAD_BUDGET=2600;
    private static final boolean QA_BUDGET=Boolean.getBoolean("academy.qa.signatureBudget");
    private record FrameStats(int quads,int dropped,Map<String,Integer> groups) {}
    private static FrameStats lastStats;
    private static final Map<String,RenderType> TYPES=new HashMap<>();
    private static MultiBufferSource.BufferSource buffers,late;
    private static ByteBufferBuilder storage,lateStorage;
    private static boolean lateUsed;
    private CanonFx() {}

    static void clear(){TYPES.clear();CanonFx4.clear();CanonFxForms.clear();CanonFxClassic.clear();ClassicSignatures.clear();CanonCamera.clear();CanonParticles.clear();CanonDecals.clear();CanonWarps.clear();
        if(storage!=null)storage.close();if(lateStorage!=null)lateStorage.close();storage=lateStorage=null;buffers=late=null;}

    static void render(RenderLevelStageEvent event,List<View> views){
        if(views.isEmpty()&&!CanonParticles.any()&&!CanonDecals.any()&&!CanonWarps.any()&&!CanonUltimates.any()&&!ClassicSignatures.any())return;
        if(buffers==null){storage=new ByteBufferBuilder(1<<18);buffers=MultiBufferSource.immediate(storage);
            lateStorage=new ByteBufferBuilder(1<<15);late=MultiBufferSource.immediate(lateStorage);}
        double partial=net.minecraft.client.Minecraft.getInstance().isPaused()?0:event.getPartialTick().getGameTimeDeltaPartialTick(false);lateUsed=false;
        var camera=event.getCamera();var modelView=RenderSystem.getModelViewStack();float[] color=RenderSystem.getShaderColor().clone();
        modelView.pushMatrix();
        try{
            RenderSystem.setShaderColor(1,1,1,1);modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            var left=camera.getLeftVector();var up=camera.getUpVector();
            Canvas c=new Canvas(event.getPoseStack().last().pose(),camera.getPosition(),new Vec3(-left.x(),-left.y(),-left.z()),new Vec3(up.x(),up.y(),up.z()));
            var visible=views.stream().filter(v->v.anchor.distanceToSqr(c.cam)<=56*56||v.origin.distanceToSqr(c.cam)<=56*56||v.endpoint.distanceToSqr(c.cam)<=56*56).toList();
            int groups=visible.size()+ClassicSignatures.count()+CanonUltimates.count();
            int allowance=Math.max(1,(QUAD_BUDGET-320)/Math.max(1,groups));
            for(View v:visible){
                int before=c.left,scope=c.limit(allowance);
                try{
                    // Leave a share for the identifying detail even when a shell/wing uses many faces.
                    int body=c.limit(allowance*3/4);
                    try{CanonFxScripts.draw(c,v);CanonShapes.draw(c,v);}finally{c.restore(body);}
                    CanonBarrierGeometry.boundary(c,v,CanonSignatures.colour(v.category()),CanonFxScripts.env(v));
                    CanonRangeRing.draw(c,v);CanonSignatures.draw(c,v);
                }finally{c.restore(scope);c.record("view:"+v.skill,before);}
            }
            var level=net.minecraft.client.Minecraft.getInstance().level;
            if(level!=null){CanonUltimates.draw(c,level.getGameTime()+partial,allowance);ClassicSignatures.draw(c,level.getGameTime()+partial,allowance);} // game time: hit freezes must not stretch a finale
            int residue=c.limit(320);
            try{CanonDecals.draw(c,partial);CanonParticles.draw(c,partial);CanonWarps.draw(c,partial);}finally{c.restore(residue);}
            if(QA_BUDGET)lastStats=new FrameStats(QUAD_BUDGET-c.left,c.dropped,Map.copyOf(c.groups));
            buffers.endBatch();
            // Refraction samples what is already on screen, so it is copied after everything else and drawn last.
            if(lateUsed){CanonFx4.captureScene();late.endBatch();}
        }finally{try{buffers.endBatch();late.endBatch();}finally{modelView.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}}
    }

    /** Per-frame drawing surface. Positions are world space; conversion to camera space happens here. */
    static final class Canvas {
        final Matrix4f matrix;final Vec3 cam,right,up;int left=QUAD_BUDGET;
        private int floor,dropped;
        private final Map<String,Integer> groups=QA_BUDGET?new HashMap<>():null;
        Canvas(Matrix4f matrix,Vec3 cam,Vec3 right,Vec3 up){this.matrix=matrix;this.cam=cam;this.right=right;this.up=up;}
        int limit(int allowance){int previous=floor;floor=Math.max(floor,left-Math.max(0,allowance));return previous;}
        void restore(int previous){floor=previous;}
        void record(String key,int before){if(QA_BUDGET)groups.merge(key,before-left,Integer::sum);}
        private boolean room(){if(left>floor)return true;if(QA_BUDGET)dropped++;return false;}

        /** One textured quad; corners a,b,c,d map to (u0,v0),(u1,v0),(u1,v1),(u0,v1). Faded when close to the eye. */
        void quad(String tex,boolean additive,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float u0,float v0,float u1,float v1,float r,float g,float bl,float al){
            if(al<=.003f||!room())return;
            if(additive)al=Math.min(al*.8f,.6f); // additive light stacks fast on a bright sky; cap each layer
            Vec3 mid=a.add(b).add(c).add(d).scale(.25);double dist=mid.distanceTo(cam);
            al*=(float)Math.min(1,Math.max(0,(dist-.45)/1.1));if(al<=.003f)return;
            left--;VertexConsumer out=buffers.getBuffer(type(tex,additive));
            v(out,a,u0,v0,r,g,bl,al);v(out,b,u1,v0,r,g,bl,al);v(out,c,u1,v1,r,g,bl,al);v(out,d,u0,v1,r,g,bl,al);
        }
        private void v(VertexConsumer out,Vec3 p,float u,float v,float r,float g,float b,float a){
            out.addVertex(matrix,(float)(p.x-cam.x),(float)(p.y-cam.y),(float)(p.z-cam.z)).setUv(u,v).setColor(r,g,b,a);
        }
        /** Camera-facing sprite. */
        void sprite(String tex,boolean additive,Vec3 p,double size,double spin,float[] rgb,float al){
            double cs=Math.cos(spin)*size,sn=Math.sin(spin)*size;
            Vec3 x=right.scale(cs).add(up.scale(sn)),y=right.scale(-sn).add(up.scale(cs));
            quad(tex,additive,p.subtract(x).subtract(y),p.add(x).subtract(y),p.add(x).add(y),p.subtract(x).add(y),0,0,1,1,rgb[0],rgb[1],rgb[2],al);
        }
        /** Frame of a 4x2 flip-book sheet (0..7), camera facing. */
        void sheet(String tex,boolean additive,Vec3 p,double size,double spin,int frame,float[] rgb,float al){
            int f=Math.max(0,Math.min(7,frame));float u0=(f%4)/4f,v0=(f/4)/2f;
            double cs=Math.cos(spin)*size,sn=Math.sin(spin)*size;
            Vec3 x=right.scale(cs).add(up.scale(sn)),y=right.scale(-sn).add(up.scale(cs));
            quad(tex,additive,p.subtract(x).subtract(y),p.add(x).subtract(y),p.add(x).add(y),p.subtract(x).add(y),u0,v0,u0+.25f,v0+.5f,rgb[0],rgb[1],rgb[2],al);
        }
        /** Flip-book frame in an arbitrary plane. */
        void sheetPlane(String tex,boolean additive,Vec3 p,Vec3 ax,Vec3 ay,int frame,float[] rgb,float al){
            int f=Math.max(0,Math.min(7,frame));float u0=(f%4)/4f,v0=(f/4)/2f;
            quad(tex,additive,p.subtract(ax).subtract(ay),p.add(ax).subtract(ay),p.add(ax).add(ay),p.subtract(ax).add(ay),u0,v0,u0+.25f,v0+.5f,rgb[0],rgb[1],rgb[2],al);
        }
        /** Sprite in an arbitrary plane (ground rings, shock rings facing along a direction). */
        void plane(String tex,boolean additive,Vec3 p,Vec3 ax,Vec3 ay,float[] rgb,float al){
            quad(tex,additive,p.subtract(ax).subtract(ay),p.add(ax).subtract(ay),p.add(ax).add(ay),p.subtract(ax).add(ay),0,0,1,1,rgb[0],rgb[1],rgb[2],al);
        }
        /** Flat horizontal disc. */
        void ground(String tex,boolean additive,Vec3 p,double radius,float[] rgb,float al){
            plane(tex,additive,p,new Vec3(radius,0,0),new Vec3(0,0,radius),rgb,al);
        }
        /** Camera-facing strip along a path. width(t) and alpha(t) take t in [0,1]. u scrolls along the strip. */
        void ribbon(String tex,boolean additive,List<Vec3> pts,java.util.function.DoubleUnaryOperator width,java.util.function.DoubleUnaryOperator alpha,
                    float[] rgb,double repeat,double scroll){
            int n=pts.size();if(n<2)return;Vec3[] side=new Vec3[n];
            for(int i=0;i<n;i++){
                Vec3 t=pts.get(Math.min(n-1,i+1)).subtract(pts.get(Math.max(0,i-1)));Vec3 toCam=cam.subtract(pts.get(i));
                Vec3 s=t.cross(toCam);side[i]=s.lengthSqr()<1e-10?right:s.normalize();
            }
            for(int i=0;i<n-1;i++){
                double t0=i/(double)(n-1),t1=(i+1)/(double)(n-1),w0=width.applyAsDouble(t0)*.5,w1=width.applyAsDouble(t1)*.5;
                float a=(float)(alpha.applyAsDouble((t0+t1)*.5));if(a<=.003f||(w0<=0&&w1<=0))continue;
                float u0=(float)(t0*repeat+scroll),u1=(float)(t1*repeat+scroll);
                quad(tex,additive,pts.get(i).subtract(side[i].scale(w0)),pts.get(i+1).subtract(side[i+1].scale(w1)),
                        pts.get(i+1).add(side[i+1].scale(w1)),pts.get(i).add(side[i].scale(w0)),u0,0,u1,1,rgb[0],rgb[1],rgb[2],a);
            }
        }
        // ------------------------------------------------------------------ FX4 materials (textures/fx4, shader modes with fallback)
        /** One quad in an FX4 material. For DISSOLVE the alpha argument is the dissolve amount (0 whole, 1 gone). */
        void fx4Quad(String key,CanonFx4.Mode mode,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float[] uv,float r,float g,float bl,float al){
            if(!room())return;CanonFx4.Mode m=CanonFx4.effective(mode);if(m==null)return;
            String tex=key.startsWith("fx4:")?key.substring(4):key;
            if(m==CanonFx4.Mode.DISSOLVE){if(al>=.999f)return;al=Math.max(0,al);}
            else{
                if(mode==CanonFx4.Mode.DISSOLVE)al=1-Math.max(0,al); // fallback: dissolve amount becomes a plain fade
                if(m==CanonFx4.Mode.ADD||m==CanonFx4.Mode.HOLO)al=Math.min(al*.8f,.6f);
                Vec3 mid=a.add(b).add(c).add(d).scale(.25);al*=(float)Math.min(1,Math.max(0,(mid.distanceTo(cam)-.45)/1.1));
                if(al<=.003f)return;
            }
            left--;boolean deferred=CanonFx4.late(m);if(deferred)lateUsed=true;
            VertexConsumer out=(deferred?late:buffers).getBuffer(CanonFx4.type(tex,m));
            v(out,a,uv[0],uv[1],r,g,bl,al);v(out,b,uv[2],uv[1],r,g,bl,al);v(out,c,uv[2],uv[3],r,g,bl,al);v(out,d,uv[0],uv[3],r,g,bl,al);
        }
        private static float[] cellUv(String key,int cell){return Fx4Atlas.get(key.startsWith("fx4:")?key.substring(4):key).cell(cell);}
        /** Four independent UV corners for a projected material on curved geometry. Same shared budget and material fallback. */
        private void filmQuad(String key,CanonFx4.Mode mode,Vec3[] p,float[] uv,float[] rgb,float al){
            if(al<=.003f||!room())return;CanonFx4.Mode m=CanonFx4.effective(mode);if(m==null)return;
            Vec3 mid=p[0].add(p[1]).add(p[2]).add(p[3]).scale(.25);
            al*=(float)Math.min(1,Math.max(0,(mid.distanceTo(cam)-.45)/1.1));if(al<=.003f)return;
            left--;String tex=key.startsWith("fx4:")?key.substring(4):key;
            VertexConsumer out=buffers.getBuffer(CanonFx4.type(tex,m));
            for(int i=0;i<4;i++)v(out,p[i],uv[i*2],uv[i*2+1],rgb[0],rgb[1],rgb[2],al);
        }
        /**
         * Thin-film bubble: preserve a real ellipsoid, project its circular atlas artwork in camera space rather than wrapping
         * a padded circle around longitude/latitude. Only the facing half is drawn, so two translucent rims cannot stack white.
         * The radius/aspect constants match scripts/fx4/materials.py bubble(), including its transparent cell padding.
         */
        void filmShell(String key,CanonFx4.Mode mode,Vec3 centre,double rx,double ry,int lat,int lon,float[] rgb,float al,int cell){
            if(rx<=1e-5||ry<=1e-5||al<=.003f||left<=0)return;
            float[] tile=cellUv(key,cell);double ru=rx*rx,rv=ry*ry;
            double xx=ru*(right.x*right.x+right.z*right.z)+rv*right.y*right.y;
            double yy=ru*(up.x*up.x+up.z*up.z)+rv*up.y*up.y;
            double xy=ru*(right.x*up.x+right.z*up.z)+rv*right.y*up.y;
            double sx=Math.sqrt(Math.max(1e-10,xx)),shear=xy/xx,sy=Math.sqrt(Math.max(1e-10,yy-xy*shear));
            double radius=.42+.035*Math.floorMod(cell,4),tx=.5*radius*(1+.04*Math.sin(cell)),ty=.5*radius*(1+.05*Math.cos(cell*.8));
            for(int i=0;i<lat&&left>0;i++)for(int k=0;k<lon&&left>0;k++){
                double th0=Math.PI*i/lat,th1=Math.PI*(i+1)/lat,ph0=Math.PI*2*k/lon,ph1=Math.PI*2*(k+1)/lon;
                Vec3[] normals={sph(th0,ph0),sph(th1,ph0),sph(th1,ph1),sph(th0,ph1)};
                Vec3 nm=normals[0].add(normals[1]).add(normals[2]).add(normals[3]).normalize();
                Vec3 mid=centre.add(nm.x*rx,nm.y*ry,nm.z*rx),view=cam.subtract(mid);
                double facing=new Vec3(nm.x/rx,nm.y/ry,nm.z/rx).normalize().dot(view.normalize());
                if(facing<=0)continue;
                float alpha=(float)(al*(.018+.982*Math.pow(1-Math.min(1,facing),1.8)));
                if(alpha<=.003f)continue;Vec3[] points=new Vec3[4];float[] uv=new float[8];
                for(int j=0;j<4;j++){
                    Vec3 n=normals[j],offset=new Vec3(n.x*rx,n.y*ry,n.z*rx);points[j]=centre.add(offset);
                    double px=offset.dot(right),py=offset.dot(up),nx=px/sx,ny=(py-shear*px)/sy;
                    double u=Math.max(0,Math.min(1,.5+nx*tx)),w=Math.max(0,Math.min(1,.5-ny*ty));
                    uv[j*2]=(float)(tile[0]+(tile[2]-tile[0])*u);uv[j*2+1]=(float)(tile[1]+(tile[3]-tile[1])*w);
                }
                filmQuad(key,mode,points,uv,rgb,alpha);
            }
        }
        /** Camera-facing sprite showing one grid cell (frame or variant). */
        void cellSprite(String key,CanonFx4.Mode mode,Vec3 p,double size,double spin,int cell,float[] rgb,float al){
            double cs=Math.cos(spin)*size,sn=Math.sin(spin)*size;Vec3 x=right.scale(cs).add(up.scale(sn)),y=right.scale(-sn).add(up.scale(cs));
            fx4Quad(key,mode,p.subtract(x).add(y),p.add(x).add(y),p.add(x).subtract(y),p.subtract(x).subtract(y),cellUv(key,cell),rgb[0],rgb[1],rgb[2],al);
        }
        /** One cell on an arbitrary plane (decals, curtains, splash crowns standing on the ground). */
        void cellPlane(String key,CanonFx4.Mode mode,Vec3 p,Vec3 ax,Vec3 ay,int cell,float[] rgb,float al){
            fx4Quad(key,mode,p.subtract(ax).add(ay),p.add(ax).add(ay),p.add(ax).subtract(ay),p.subtract(ax).subtract(ay),cellUv(key,cell),rgb[0],rgb[1],rgb[2],al);
        }
        /** Camera-facing quad stretched from a to b (motion-stretched droplets, speed lines). */
        void cellStrip(String key,CanonFx4.Mode mode,Vec3 a,Vec3 b,double width,int cell,float[] rgb,float al){
            Vec3 s=b.subtract(a).cross(cam.subtract(a.add(b).scale(.5)));s=s.lengthSqr()<1e-10?right.scale(width):s.normalize().scale(width);
            float[] uv=cellUv(key,cell);
            fx4Quad(key,mode,a.subtract(s),b.subtract(s),b.add(s),a.add(s),new float[]{uv[0],uv[3],uv[2],uv[1]},rgb[0],rgb[1],rgb[2],al);
        }
        /** Camera-facing strip along a path in an FX4 material; u runs along the path and may repeat. */
        void ribbon4(String key,CanonFx4.Mode mode,List<Vec3> pts,java.util.function.DoubleUnaryOperator width,java.util.function.DoubleUnaryOperator alpha,
                     float[] rgb,double repeat,double scroll){
            int n=pts.size();if(n<2)return;Vec3[] side=new Vec3[n];
            for(int i=0;i<n;i++){Vec3 t=pts.get(Math.min(n-1,i+1)).subtract(pts.get(Math.max(0,i-1)));Vec3 s=t.cross(cam.subtract(pts.get(i)));
                side[i]=s.lengthSqr()<1e-10?right:s.normalize();}
            for(int i=0;i<n-1;i++){
                double t0=i/(double)(n-1),t1=(i+1)/(double)(n-1),w0=width.applyAsDouble(t0)*.5,w1=width.applyAsDouble(t1)*.5;
                float al=(float)alpha.applyAsDouble((t0+t1)*.5);if(w0<=0&&w1<=0)continue;
                fx4Quad(key,mode,pts.get(i).subtract(side[i].scale(w0)),pts.get(i+1).subtract(side[i+1].scale(w1)),pts.get(i+1).add(side[i+1].scale(w1)),
                        pts.get(i).add(side[i].scale(w0)),new float[]{(float)(t0*repeat+scroll),0,(float)(t1*repeat+scroll),1},rgb[0],rgb[1],rgb[2],al);
            }
        }
        /**
         * GM1: a real round tube along a path (water whips, tendrils), {@code sides} faces around. u runs along the path, v around it.
         * Faces seen edge-on are more opaque than faces seen head-on, so the tube reads as a volume of liquid rather than a flat band.
         */
        void tube(String key,CanonFx4.Mode mode,List<Vec3> pts,java.util.function.DoubleUnaryOperator radius,java.util.function.DoubleUnaryOperator alpha,
                  float[] rgb,double repeat,double scroll,int sides){
            int n=pts.size();if(n<2||sides<3)return;Vec3[][] ring=new Vec3[n][sides+1];Vec3[][] nrm=new Vec3[n][sides+1];
            Vec3 ref=new Vec3(0,1,0);
            for(int i=0;i<n;i++){
                Vec3 t=pts.get(Math.min(n-1,i+1)).subtract(pts.get(Math.max(0,i-1)));t=t.lengthSqr()<1e-10?new Vec3(0,0,1):t.normalize();
                Vec3 a=t.cross(ref);if(a.lengthSqr()<1e-6)a=t.cross(new Vec3(1,0,0));a=a.normalize();Vec3 b=t.cross(a).normalize();
                double r=Math.max(0,radius.applyAsDouble(i/(double)(n-1)));
                for(int k=0;k<=sides;k++){double ang=k*Math.PI*2/sides;Vec3 o=a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));nrm[i][k]=o;ring[i][k]=pts.get(i).add(o.scale(r));}
            }
            for(int i=0;i<n-1;i++){
                double t0=i/(double)(n-1),t1=(i+1)/(double)(n-1);float base=(float)alpha.applyAsDouble((t0+t1)*.5);if(base<=.003f)continue;
                for(int k=0;k<sides;k++){
                    Vec3 mid=ring[i][k].add(ring[i+1][k+1]).scale(.5),view=cam.subtract(mid);double vl=view.length();if(vl<1e-6)continue;
                    double facing=Math.abs(nrm[i][k].add(nrm[i][k+1]).normalize().dot(view.scale(1/vl)));
                    float al=(float)(base*(.62+.38*Math.pow(1-facing,1.5)));
                    float v0=k/(float)sides,v1=(k+1)/(float)sides;
                    fx4Quad(key,mode,ring[i][k],ring[i+1][k],ring[i+1][k+1],ring[i][k+1],
                            new float[]{(float)(t0*repeat+scroll),v0,(float)(t1*repeat+scroll),v1},rgb[0],rgb[1],rgb[2],al);
                }
            }
        }
        /** SH4 approximation on the CPU: an ellipsoid shell whose silhouette is opaque and whose centre is clear (water globes, shields). */
        /** Like {@link #shell} but the texture is one cell of an atlas (e.g. one bubble of fd_bubble_atlas), not the whole sheet. */
        void shellCell(String key,CanonFx4.Mode mode,Vec3 centre,double rx,double ry,int lat,int lon,float[] rgb,float al,int cell){
            float[] cuv=cellUv(key,cell);float cu0=cuv[0],cv0=cuv[1],cu1=cuv[2],cv1=cuv[3];
            for(int i=0;i<lat;i++)for(int k=0;k<lon;k++){
                double th0=Math.PI*i/lat,th1=Math.PI*(i+1)/lat,ph0=Math.PI*2*k/lon,ph1=Math.PI*2*(k+1)/lon;
                Vec3 n00=sph(th0,ph0),n10=sph(th1,ph0),n11=sph(th1,ph1),n01=sph(th0,ph1);
                Vec3 nm=n00.add(n11).normalize(),mid=centre.add(nm.x*rx,nm.y*ry,nm.z*rx),view=cam.subtract(mid);double vl=view.length();if(vl<1e-6)continue;
                double facing=Math.abs(nm.dot(view.scale(1/vl)));float a=(float)(al*Math.pow(1-facing,2.2));if(a<=.004f)continue;
                float u0=cu0+(cu1-cu0)*(float)(k/(double)lon),u1=cu0+(cu1-cu0)*(float)((k+1)/(double)lon);
                float v0=cv0+(cv1-cv0)*(float)(i/(double)lat),v1=cv0+(cv1-cv0)*(float)((i+1)/(double)lat);
                fx4Quad(key,mode,centre.add(n00.x*rx,n00.y*ry,n00.z*rx),centre.add(n10.x*rx,n10.y*ry,n10.z*rx),centre.add(n11.x*rx,n11.y*ry,n11.z*rx),
                        centre.add(n01.x*rx,n01.y*ry,n01.z*rx),new float[]{u0,v0,u1,v1},rgb[0],rgb[1],rgb[2],a);
            }
        }
        void shell(String key,CanonFx4.Mode mode,Vec3 centre,double rx,double ry,int lat,int lon,float[] rgb,float al,double scroll){
            for(int i=0;i<lat;i++)for(int k=0;k<lon;k++){
                double th0=Math.PI*i/lat,th1=Math.PI*(i+1)/lat,ph0=Math.PI*2*k/lon,ph1=Math.PI*2*(k+1)/lon;
                Vec3 n00=sph(th0,ph0),n10=sph(th1,ph0),n11=sph(th1,ph1),n01=sph(th0,ph1);
                Vec3 nm=n00.add(n11).normalize(),mid=centre.add(nm.x*rx,nm.y*ry,nm.z*rx),view=cam.subtract(mid);double vl=view.length();if(vl<1e-6)continue;
                double facing=Math.abs(nm.dot(view.scale(1/vl)));float a=(float)(al*Math.pow(1-facing,2.2));if(a<=.004f)continue;
                fx4Quad(key,mode,centre.add(n00.x*rx,n00.y*ry,n00.z*rx),centre.add(n10.x*rx,n10.y*ry,n10.z*rx),centre.add(n11.x*rx,n11.y*ry,n11.z*rx),
                        centre.add(n01.x*rx,n01.y*ry,n01.z*rx),new float[]{(float)(k/(double)lon+scroll),(float)i/lat,(float)((k+1)/(double)lon+scroll),(float)(i+1)/lat},
                        rgb[0],rgb[1],rgb[2],a);
            }
        }
        private static Vec3 sph(double th,double ph){return new Vec3(Math.sin(th)*Math.cos(ph),Math.cos(th),Math.sin(th)*Math.sin(ph));}
        /** SH1/SH5 screen refraction bubble: a camera-facing disc that bends the scene behind it; strength 0..1. */
        void refract(String key,boolean dispersion,Vec3 p,double size,float strength,float[] tint){
            Vec3 x=right.scale(size),y=up.scale(size);
            fx4Quad(key,dispersion?CanonFx4.Mode.DISPERSE:CanonFx4.Mode.REFRACT,p.subtract(x).add(y),p.add(x).add(y),p.add(x).subtract(y),p.subtract(x).subtract(y),
                    new float[]{0,0,1,1},tint[0],tint[1],tint[2],strength);
        }

        /** Sheet that rotates about a hinge line: swings from flat (angle 0) up to angle radians about axis. */
        void fold(String tex,Vec3 hinge,Vec3 axis,Vec3 dir,double width,double length,double angle,float[] rgb,float al){
            Vec3 ax=axis.normalize(),d0=dir.normalize();
            Vec3 d=d0.scale(Math.cos(angle)).add(ax.cross(d0).scale(Math.sin(angle))).add(ax.scale(ax.dot(d0)*(1-Math.cos(angle))));
            Vec3 w=ax.scale(width*.5);
            quad(tex,false,hinge.subtract(w),hinge.add(w),hinge.add(w).add(d.scale(length)),hinge.subtract(w).add(d.scale(length)),0,0,1,1,rgb[0],rgb[1],rgb[2],al);
        }
    }

    private static RenderType type(String tex,boolean additive){
        return TYPES.computeIfAbsent(tex+(additive?"+":"="),k->Material.make(tex,additive));
    }

    private static final class Material extends RenderType {
        private Material(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,()->{},()->{});}
        /** Source-alpha additive: the stock additive state is ONE/ONE, which ignores alpha and draws solid discs. */
        static final TransparencyStateShard GLOW_BLEND=new TransparencyStateShard("academy_canon_glow",
                ()->{RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);},
                ()->{RenderSystem.disableBlend();RenderSystem.defaultBlendFunc();});
        static RenderType make(String tex,boolean additive){
            var state=CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/canon_fx/"+tex+".png"),true,false))
                    .setTransparencyState(additive?GLOW_BLEND:TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false);
            return RenderType.create("academy_canon_fx_"+tex+(additive?"_add":"_alpha"),DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,state);
        }
    }

    /** Helper for scripts: a point list sampled along a straight line with lateral wave offset. */
    static List<Vec3> wave(Vec3 a,Vec3 b,int n,double amplitude,double waves,double phase,Vec3 hint){
        Vec3 d=b.subtract(a);Vec3 p1=d.cross(hint);p1=p1.lengthSqr()<1e-9?new Vec3(1,0,0):p1.normalize();Vec3 p2=d.cross(p1).normalize();
        var out=new ArrayList<Vec3>(n);
        for(int i=0;i<n;i++){double t=i/(double)(n-1),env=Math.sin(Math.PI*t);
            double s=Math.sin(t*waves*Math.PI*2+phase)*amplitude*env,c=Math.cos(t*waves*Math.PI*2+phase*1.3)*amplitude*env*.6;
            out.add(a.add(d.scale(t)).add(p1.scale(s)).add(p2.scale(c)));}
        return out;
    }
}
