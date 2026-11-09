package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonVfxDesign.Motif;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Unlit, alpha-blended, depth-tested mesh layer; particle geometry remains a separate bounded fallback. */
final class CanonMeshRenderer {
    record View(String skill,Motif motif,Vec3 origin,Vec3 right,Vec3 up,Vec3 forward,int phase,double age,int duration,double length,boolean local) {}
    private static MultiBufferSource.BufferSource buffers;
    private static ByteBufferBuilder storage;
    private static final RenderType TYPE=Material.make();
    private CanonMeshRenderer() {}
    static void clear(){if(storage!=null)storage.close();storage=null;buffers=null;}
    static void render(RenderLevelStageEvent event,List<View> views){
        if(views.isEmpty())return;Minecraft mc=Minecraft.getInstance();Vec3 camera=event.getCamera().getPosition();
        if(buffers==null){storage=new ByteBufferBuilder(32768);buffers=MultiBufferSource.immediate(storage);}
        var modelView=RenderSystem.getModelViewStack();float[] color=RenderSystem.getShaderColor().clone();modelView.pushMatrix();
        try{
            RenderSystem.setShaderColor(1,1,1,1);modelView.set(event.getModelViewMatrix());RenderSystem.applyModelViewMatrix();
            Matrix4f matrix=event.getPoseStack().last().pose();VertexConsumer out=buffers.getBuffer(TYPE);int budget=384;
            for(View view:views){
                if(budget<=0||view.origin.distanceToSqr(camera)>64*64)continue;
                if(view.local && mc.options.getCameraType().isFirstPerson()
                        && (view.motif==Motif.DARK_WINGS||view.motif==Motif.NITROGEN_SHELL||view.motif==Motif.NITROGEN_PUNCH)){
                    for(var q:near(view,event))if(budget-->0)emit(out,matrix,camera,q);continue;
                }
                for(var q:CanonMeshGeometry.shapeFor(view.skill,view.motif,view.phase,view.age,view.duration,view.length)){
                    if(budget--<=0)break;
                    emit(out,matrix,camera,world(q,view));
                }
            }
            buffers.endBatch(TYPE);
        }finally{try{buffers.endBatch();}finally{modelView.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}}
    }
    private static CanonMeshGeometry.Quad world(CanonMeshGeometry.Quad q,View v){return new CanonMeshGeometry.Quad(
            point(q.a(),v),point(q.b(),v),point(q.c(),v),point(q.d(),v),q.red(),q.green(),q.blue(),q.alpha());}
    private static CanonMeshGeometry.Point point(CanonMeshGeometry.Point p,View v){
        Vec3 x=v.origin.add(v.right.scale(p.x())).add(v.up.scale(p.y())).add(v.forward.scale(p.z()));return new CanonMeshGeometry.Point(x.x,x.y,x.z);
    }
    private static List<CanonMeshGeometry.Quad> near(View view,RenderLevelStageEvent event){
        if(view.motif!=Motif.DARK_WINGS&&view.motif!=Motif.NITROGEN_SHELL&&view.motif!=Motif.NITROGEN_PUNCH)return List.of();
        Vec3 eye=event.getCamera().getPosition();var look=event.getCamera().getLookVector();var left=event.getCamera().getLeftVector();var up=event.getCamera().getUpVector();
        Vec3 f=new Vec3(look.x(),look.y(),look.z()),l=new Vec3(left.x(),left.y(),left.z()),u=new Vec3(up.x(),up.y(),up.z());
        double fade=view.phase==3?Math.max(.1,1-view.age/view.duration):Math.min(1,.18+view.age*.12);
        int alpha=(int)((view.motif==Motif.DARK_WINGS?95:65)*fade);
        var out=new java.util.ArrayList<CanonMeshGeometry.Quad>();
        for(int side:new int[]{-1,1}){
            Vec3 a=eye.add(f.scale(.9)).add(l.scale(side*.35)).add(u.scale(-.28));
            Vec3 b=eye.add(f.scale(.9)).add(l.scale(side*.67)).add(u.scale(-.10));
            Vec3 c=eye.add(f.scale(1.0)).add(l.scale(side*.62)).add(u.scale(-.42));
            Vec3 d=eye.add(f.scale(1.0)).add(l.scale(side*.40)).add(u.scale(-.47));
            out.add(new CanonMeshGeometry.Quad(pt(a),pt(b),pt(c),pt(d),210,240,255,alpha));
        }
        return out;
    }
    private static CanonMeshGeometry.Point pt(Vec3 p){return new CanonMeshGeometry.Point(p.x,p.y,p.z);}
    private static void emit(VertexConsumer out,Matrix4f m,Vec3 camera,CanonMeshGeometry.Quad q){
        vertex(out,m,camera,q.a(),0,0,q);vertex(out,m,camera,q.b(),1,0,q);vertex(out,m,camera,q.c(),1,1,q);vertex(out,m,camera,q.d(),0,1,q);
    }
    private static void vertex(VertexConsumer out,Matrix4f m,Vec3 camera,CanonMeshGeometry.Point p,float u,float v,CanonMeshGeometry.Quad q){
        out.addVertex(m,(float)(p.x()-camera.x),(float)(p.y()-camera.y),(float)(p.z()-camera.z))
                .setUv(u,v).setColor(q.red(),q.green(),q.blue(),q.alpha());
    }
    private static final class Material extends RenderType {
        private Material(){super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,()->{},()->{});}
        static RenderType make(){var state=CompositeState.builder().setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png"),false,false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                .setOverlayState(NO_OVERLAY).setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false);
            return RenderType.create("academy_canon_mesh",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,4096,false,true,state);}
    }
}
