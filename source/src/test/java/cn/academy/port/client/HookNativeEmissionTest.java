package cn.academy.port.client;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.*;import java.util.*;import org.joml.Matrix4f;import org.joml.Vector3f;
/** Executes actual existing modern mesh emission on both staged source OBJs and source item/entity matrices. */
public final class HookNativeEmissionTest {
    static int assertions;
    static void check(boolean b,String m){assertions++;if(!b)throw new AssertionError(m);}
    static void eq(double a,double b){check(Double.isFinite(b)&&Math.abs(a-b)<1E-4,a+" != "+b);}
    record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz,int color,int overlayU,int overlayV,int lightU,int lightV){}
    static final class Recorder implements VertexConsumer {
        final List<Vertex> vertices=new ArrayList<>();float x,y,z,u,v;int color,ou,ov,lu,lv,attributes;
        public VertexConsumer addVertex(float x,float y,float z){check(attributes==0,"previous native vertex complete");this.x=x;this.y=y;this.z=z;attributes=1;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){color=a<<24|r<<16|g<<8|b;attributes|=2;return this;}
        public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;attributes|=4;return this;}
        public VertexConsumer setUv1(int u,int v){ou=u;ov=v;attributes|=8;return this;}
        public VertexConsumer setUv2(int u,int v){lu=u;lv=v;attributes|=16;return this;}
        public VertexConsumer setNormal(float x,float y,float z){check(attributes==31,"native NEW_ENTITY attributes complete");vertices.add(new Vertex(this.x,this.y,this.z,u,v,x,y,z,color,ou,ov,lu,lv));attributes=0;return this;}
    }
    public static void main(String[] args)throws Exception{
        Path assets=Path.of(System.getProperty("hook.assets",".staging/hook/src/main/resources/assets/academy/models"));
        for(String name:List.of("maghook","maghook_open")){
            ClassicDeveloperObj.Mesh mesh;try(var input=Files.newBufferedReader(assets.resolve(name+".obj"))){mesh=ClassicDeveloperObj.parse(input);}check(mesh.triangles().size()>100,"real detailed source mesh");
            var matrices=new ArrayList<Matrix4f>();for(int side=0;side<6;side++){var a=cn.academy.port.hook.ClassicHookRules.anchor(0,0,0,side,31);matrices.add(ClassicHookTransform.entity(a.yaw(),a.pitch()));}
            var item=new ClassicHookTransform.ItemState();for(var context:ClassicPortableTransform.Context.values())for(boolean left:new boolean[]{false,true})matrices.add(ClassicPortableContextBridge.matrix(context,left).mul(item.render(context,left)));
            for(var matrix:matrices){var poses=new PoseStack();poses.mulPose(matrix);var recorder=new Recorder();ClassicPortableMeshEmission.emit(mesh,poses.last(),recorder,0x00F00080,0x000A0003);check(recorder.vertices.size()==mesh.triangles().size()*4,"native quad for source triangle");int i=0;
                for(var triangle:mesh.triangles())for(var source:new ClassicDeveloperObj.Vertex[]{triangle.a(),triangle.b(),triangle.c(),triangle.c()}){var v=recorder.vertices.get(i++);var expected=matrix.transformPosition(new Vector3f(source.x(),source.y(),source.z()));eq(expected.x,v.x);eq(expected.y,v.y);eq(expected.z,v.z);eq(source.u(),v.u);eq(source.v(),v.v);check(v.color==0xFFFFFFFF,"source opaque white emission");check(v.overlayU==3&&v.overlayV==10&&v.lightU==128&&v.lightV==240,"packed light/overlay retained");check(Float.isFinite(v.nx)&&Float.isFinite(v.ny)&&Float.isFinite(v.nz),"transformed normals finite");}
            }
            System.out.println("Source "+name+": "+mesh.positionCount()+" positions, "+mesh.triangles().size()+" triangles; native emission14 transforms");
        }
        System.out.println("PASS "+assertions+" native Hook buffer assertions; both source meshes, six attachment and eight item/hand matrices, UVs/light/normals");
    }
}
