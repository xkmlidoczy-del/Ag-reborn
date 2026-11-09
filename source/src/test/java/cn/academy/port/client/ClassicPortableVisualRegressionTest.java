package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Actual OBJ, matrices, model-context routing and buffer emission; no Minecraft/OpenGL startup. */
public final class ClassicPortableVisualRegressionTest {
    private static int assertions;
    private static void yes(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void near(double expected,double actual,String label){yes(Double.isFinite(actual)&&Math.abs(expected-actual)<2e-6,label+" "+expected+" != "+actual);}
    private record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz,int color,int overlayU,int overlayV,int lightU,int lightV){}
    private static final class Recorder implements VertexConsumer {
        final List<Vertex> vertices=new ArrayList<>();float x,y,z,u,v;int color,overlayU,overlayV,lightU,lightV,attributes;
        @Override public VertexConsumer addVertex(float x,float y,float z){yes(attributes==0,"previous vertex completed");this.x=x;this.y=y;this.z=z;attributes=1;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){color=a<<24|r<<16|g<<8|b;attributes|=2;return this;}
        @Override public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;attributes|=4;return this;}
        @Override public VertexConsumer setUv1(int u,int v){overlayU=u;overlayV=v;attributes|=8;return this;}
        @Override public VertexConsumer setUv2(int u,int v){lightU=u;lightV=v;attributes|=16;return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){yes(attributes==31,"all native entity attributes");vertices.add(new Vertex(this.x,this.y,this.z,u,v,x,y,z,color,overlayU,overlayV,lightU,lightV));attributes=0;return this;}
    }
    private static double[] ry(double[] p,double angle){double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));return new double[]{c*p[0]+s*p[2],p[1],-s*p[0]+c*p[2]};}
    private static double[] rz(double[] p,double angle){double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));return new double[]{c*p[0]-s*p[1],s*p[0]+c*p[1],p[2]};}
    private static double[] rx(double[] p,double angle){double c=Math.cos(Math.toRadians(angle)),s=Math.sin(Math.toRadians(angle));return new double[]{p[0],c*p[1]-s*p[2],s*p[1]+c*p[2]};}
    private static double[] oracle(ClassicPortableTransform.Context context,boolean left,ClassicDeveloperObj.Vertex vertex){
        // Independent vector arithmetic: reverse canonical OpenGL call order.
        double[] p={vertex.x()/16d,vertex.y()/16d,vertex.z()/16d};p=ry(p,180);p[0]*=-6;p[1]*=-6;p[2]*=6;
        if(context==ClassicPortableTransform.Context.FIRST_PERSON||context==ClassicPortableTransform.Context.THIRD_PERSON){
            if(context==ClassicPortableTransform.Context.THIRD_PERSON)for(int i=0;i<3;i++)p[i]*=.6;
            p=ry(p,-90);p=rz(p,-5);p=ry(p,-10);p[0]+=.6;p[2]-=.2;
            if(context==ClassicPortableTransform.Context.THIRD_PERSON){p[0]+=.1;p[1]+=.05;p[2]+=.2;}p=rz(p,40);if(left)p[0]=-p[0];
        }else if(context==ClassicPortableTransform.Context.GROUND){p[2]+=.2;p=rx(p,15);}
        return p;
    }
    private static final class FlatModel implements BakedModel {
        int transforms;BakedModel selected;
        @Override public List<BakedQuad> getQuads(BlockState state,Direction direction,RandomSource random){return List.of();}
        @Override public boolean useAmbientOcclusion(){return false;}@Override public boolean isGui3d(){return false;}
        @Override public boolean usesBlockLight(){return false;}@Override public boolean isCustomRenderer(){return false;}
        @Override public TextureAtlasSprite getParticleIcon(){return null;}@Override public ItemOverrides getOverrides(){return new ItemOverrides(){
            @Override public BakedModel resolve(BakedModel model,net.minecraft.world.item.ItemStack stack,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.entity.LivingEntity entity,int seed){return selected==null?model:selected;}
        };}
        @Override public BakedModel applyTransform(ItemDisplayContext context,PoseStack poses,boolean left){transforms++;poses.translate(.01,.02,.03);return this;}
    }
    public static void main(String[] args)throws Exception{
        ClassicDeveloperObj.Mesh mesh;
        try(var stream=ClassicPortableVisualRegressionTest.class.getResourceAsStream("/assets/academy/models/developer_portable.obj");var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){mesh=ClassicDeveloperObj.parse(reader);}
        yes(mesh.positionCount()==32,"canonical32OBJ positions");yes(mesh.triangles().size()==60,"canonical60triangles");
        for(var context:ClassicPortableTransform.Context.values())for(boolean left:new boolean[]{false,true}){
            var poses=new PoseStack();poses.mulPose(ClassicPortableTransform.matrix(context,left));var recorder=new Recorder();
            ClassicPortableMeshEmission.emit(mesh,poses.last(),recorder,0x00F00080,0x000A0003);yes(recorder.vertices.size()==mesh.triangles().size()*4,"triangle emission remains native quads");int i=0;
            for(var triangle:mesh.triangles()){
                for(var vertex:new ClassicDeveloperObj.Vertex[]{triangle.a(),triangle.b(),triangle.c(),triangle.c()}){
                    var expected=oracle(context,left,vertex);var actual=recorder.vertices.get(i++);near(expected[0],actual.x(),"source x");near(expected[1],actual.y(),"source y");near(expected[2],actual.z(),"source z");
                    near(vertex.u(),actual.u(),"original U");near(vertex.v(),actual.v(),"original V");near(1,actual.nx()*actual.nx()+actual.ny()*actual.ny()+actual.nz()*actual.nz(),"normal unit length");
                    yes(actual.color()==0xFFFFFFFF,"untinted original texture");yes(actual.overlayU()==3&&actual.overlayV()==10,"overlay preserved");yes(actual.lightU()==128&&actual.lightV()==240,"packed world light preserved");
                }yes(recorder.vertices.get(i-1).equals(recorder.vertices.get(i-2)),"fourth vertex duplicates third");
            }
        }
        var original=new FlatModel();var wrapper=new ClassicPortableBakedModel(original);
        yes(wrapper.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y==1,"physical dropped helper uses source entity scale1, not flat icon scale");
        for(int round=0;round<20;round++)for(var context:ItemDisplayContext.values()){
            var poses=new PoseStack();var before=new org.joml.Matrix4f(poses.last().pose());var selected=wrapper.applyTransform(context,poses,context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
            if(context==ItemDisplayContext.GUI){yes(selected==original,"GUI retains original2D model");yes(!selected.isCustomRenderer()&&!selected.isGui3d(),"GUI charge sprites never invoke OBJ renderer");}
            else{yes(selected.isCustomRenderer()&&selected.isGui3d(),"all physical contexts route source OBJ");yes(before.equals(poses.last().pose()),"routing introduces no generic generated-item transform");}
        }
        yes(original.transforms==20,"only GUI applies generated icon transform");
        var half=new FlatModel();var full=new FlatModel();
        for(var charge:new FlatModel[]{original,half,full}){
            original.selected=charge;
            BakedModel resolved=wrapper.getOverrides().resolve(wrapper,null,null,null,0);
            yes(resolved==wrapper.getOverrides().resolve(wrapper,null,null,null,1),"same source charge variant reuses its own wrapper");
            yes(resolved.applyTransform(ItemDisplayContext.GUI,new PoseStack(),false)==charge,"empty/half/full GUI preserves exact source variant");
            for(var context:ItemDisplayContext.values())if(context!=ItemDisplayContext.GUI){
                yes(resolved.applyTransform(context,new PoseStack(),false).isCustomRenderer(),"every charge override retains physical OBJ context");
                yes(resolved.applyTransform(ItemDisplayContext.GUI,new PoseStack(),false)==charge,"physical context cannot contaminate next GUI icon");
            }
        }
        var reloadedOriginal=new FlatModel();var reloaded=new ClassicPortableBakedModel(reloadedOriginal);
        yes(reloaded.getOverrides().resolve(reloaded,null,null,null,0)==reloaded,"new resource bake owns fresh wrapper cache");
        yes(reloaded.applyTransform(ItemDisplayContext.GUI,new PoseStack(),false)==reloadedOriginal,"resource reload has no stale icon owner");
        System.out.println("ClassicPortableVisualRegressionTest: "+assertions+" assertions passed");
    }
}
