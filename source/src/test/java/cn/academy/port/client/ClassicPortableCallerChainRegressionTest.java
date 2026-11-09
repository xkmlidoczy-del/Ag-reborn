package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Complete post-arm and dropped caller matrices plus actual native emission, not local-only assertions. */
public final class ClassicPortableCallerChainRegressionTest {
    private static int assertions;
    private static void yes(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void near(double expected,double actual,String label){yes(Double.isFinite(actual)&&Math.abs(expected-actual)<5e-6,label+" "+expected+" != "+actual);}
    private static float radians(float degrees){return (float)Math.toRadians(degrees);}
    private static void equal(Matrix4f expected,Matrix4f actual){float[] a=new float[16],b=new float[16];expected.get(a);actual.get(b);for(int i=0;i<16;i++)near(a[i],b[i],"complete caller component"+i);}
    private static Matrix4f modernThird(boolean left){return new Matrix4f().rotateX(radians(-90)).rotateY(radians(180)).translate(left?-1/16f:1/16f,.125f,-.625f);}
    private static Matrix4f originalThird(){
        // Official Mojang RenderPlayer.bop.a(AbstractClientPlayer,float), full3D and !rotates branch.
        return new Matrix4f().translate(-.0625f,.4375f,.0625f).translate(0,.1875f,0)
                .scale(.625f,-.625f,.625f).rotateX(radians(-100)).rotateY(radians(45))
                .translate(0,-.3f,0).scale(1.5f).rotateY(radians(50)).rotateZ(radians(335)).translate(-.9375f,-.0625f,0);
    }
    private record Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz,int color,int overlayU,int overlayV,int lightU,int lightV){}
    private static final class Recorder implements VertexConsumer {
        final List<Vertex> vertices=new ArrayList<>();float x,y,z,u,v;int color,overlayU,overlayV,lightU,lightV,attributes;
        @Override public VertexConsumer addVertex(float x,float y,float z){yes(attributes==0,"previous vertex finished");this.x=x;this.y=y;this.z=z;attributes=1;return this;}
        @Override public VertexConsumer setColor(int r,int g,int b,int a){color=a<<24|r<<16|g<<8|b;attributes|=2;return this;}
        @Override public VertexConsumer setUv(float u,float v){this.u=u;this.v=v;attributes|=4;return this;}
        @Override public VertexConsumer setUv1(int u,int v){overlayU=u;overlayV=v;attributes|=8;return this;}
        @Override public VertexConsumer setUv2(int u,int v){lightU=u;lightV=v;attributes|=16;return this;}
        @Override public VertexConsumer setNormal(float x,float y,float z){yes(attributes==31,"complete native entity attributes");vertices.add(new Vertex(this.x,this.y,this.z,u,v,x,y,z,color,overlayU,overlayV,lightU,lightV));attributes=0;return this;}
    }
    private static void emission(ClassicDeveloperObj.Mesh mesh,Matrix4f expected,Matrix4f actual){
        var poses=new PoseStack();poses.mulPose(actual);var recorder=new Recorder();ClassicPortableMeshEmission.emit(mesh,poses.last(),recorder,0x00F00080,0x000A0003);
        yes(recorder.vertices.size()==mesh.triangles().size()*4,"exact source topology under whole caller transform");int index=0;
        for(var triangle:mesh.triangles())for(var vertex:new ClassicDeveloperObj.Vertex[]{triangle.a(),triangle.b(),triangle.c(),triangle.c()}){
            var position=expected.transformPosition(new Vector3f(vertex.x(),vertex.y(),vertex.z()));var result=recorder.vertices.get(index++);
            near(position.x,result.x(),"caller vertex x");near(position.y,result.y(),"caller vertex y");near(position.z,result.z(),"caller vertex z");
            near(vertex.u(),result.u(),"source U unchanged");near(vertex.v(),result.v(),"source V unchanged");near(1,result.nx()*result.nx()+result.ny()*result.ny()+result.nz()*result.nz(),"unit transformed normal");
            yes(result.color()==0xFFFFFFFF,"untinted source texture");yes(result.overlayU()==3&&result.overlayV()==10,"native overlay unchanged");yes(result.lightU()==128&&result.lightV()==240,"native light unchanged");
        }
    }
    public static void main(String[] args)throws Exception{
        ClassicDeveloperObj.Mesh mesh;
        try(var input=ClassicPortableCallerChainRegressionTest.class.getResourceAsStream("/assets/academy/models/developer_portable.obj");var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){mesh=ClassicDeveloperObj.parse(reader);}
        Matrix4f flip=new Matrix4f().scale(-1,1,1);
        for(int sample=-90;sample<=90;sample+=15)for(boolean left:new boolean[]{false,true}){
            // Preserve the supplied native arm attachment; changing limb/body animation is outside this bridge.
            Matrix4f arm=new Matrix4f().translate(left?5/16f:-5/16f,2/16f,0).rotateZ(radians(sample/3f)).rotateY(radians(-sample/4f)).rotateX(radians(sample));
            Matrix4f desired=originalThird().mul(ClassicPortableTransform.matrix(ClassicPortableTransform.Context.THIRD_PERSON,false));
            if(left)desired=new Matrix4f(flip).mul(desired);
            Matrix4f expected=new Matrix4f(arm).mul(desired);
            Matrix4f actual=new Matrix4f(arm).mul(modernThird(left)).mul(ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.THIRD_PERSON,left)).mul(ClassicPortableTransform.matrix(ClassicPortableTransform.Context.THIRD_PERSON,left));
            equal(expected,actual);emission(mesh,expected,actual);
        }
        // Old EntityItem's yOffset=height/2 changed what posY meant; compare equal physical bounding boxes.
        var nativeBox=net.minecraft.world.entity.EntityDimensions.fixed(.25f,.25f).makeBoundingBox(3,64,-5);
        near(64,nativeBox.minY,"modern entity Y is box base");near(.25,nativeBox.getYsize(),"native item entity height");
        near(64,64.125-.25/2,"old entity Y minus source half-height equals same physical box base");
        near(.125,ClassicPortableContextBridge.LEGACY_ENTITY_Y_ORIGIN,"official source item entity half-height origin");
        near(-.125,ClassicPortableContextBridge.LEGACY_ENTITY_Y_ORIGIN-ClassicPortableContextBridge.NATIVE_GROUND_LIFT,"exact old-origin vs modern helper bridge");
        // Wrapper exposes identity ground metadata; native ItemEntityRenderer therefore adds exactly .25Y.
        near(1,ItemTransforms.NO_TRANSFORMS.getTransform(ItemDisplayContext.GROUND).scale.y,"native ground metadata assumption");
        for(int age=0;age<=300;age+=25)for(float partial:new float[]{0,.5f,1}){
            float offset=.37f,bob=(float)Math.sin((age+partial)/10+offset)*.1f+.1f,spin=(age+partial)/20+offset;
            Matrix4f expected=new Matrix4f().translate(3,64+.125f+bob,-5).rotateY(spin).scale(.5f).mul(ClassicPortableTransform.matrix(ClassicPortableTransform.Context.GROUND,false));
            Matrix4f actual=new Matrix4f().translate(3,64+bob+.25f,-5).rotateY(spin).mul(ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.GROUND,false)).mul(ClassicPortableTransform.matrix(ClassicPortableTransform.Context.GROUND,false));
            equal(expected,actual);emission(mesh,expected,actual);
        }
        equal(ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.GROUND,false),ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.GROUND,true));
        equal(new Matrix4f(),ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.STANDARD,false));
        System.out.println("ClassicPortableCallerChainRegressionTest: "+assertions+" assertions passed; exact third-person post-arm and dropped bob/spin/helper chains");
    }
}
