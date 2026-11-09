package cn.academy.port.client.terminal;
import cn.academy.port.client.ClassicDeveloperObj;
import cn.academy.port.client.ClassicPortableTransform.Context;
import cn.academy.port.client.ClassicPortableContextBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import static cn.academy.port.client.terminal.TerminalClientFixtures.*;

public final class TerminalInstallerRegressionTest {
    private static Matrix4f reference(Context context){Matrix4f matrix=new Matrix4f();if(context==Context.FIRST_PERSON||context==Context.THIRD_PERSON){matrix.rotateZ(r(40));if(context==Context.THIRD_PERSON)matrix.translate(.2f,-.15f,.25f);matrix.translate(.5f,.16f,-.24f).rotateY(r(-15)).rotateZ(r(90)).rotateX(0).rotateY(r(-90)).scale(.8f);if(context==Context.THIRD_PERSON)matrix.scale(.73f);}else if(context==Context.GROUND)matrix.rotateY(r(180)).rotateZ(0).rotateX(0).translate(-.1f,0,0).scale(1.1f);return matrix.scale(4).translate(0,0,0).scale(-1,-1,1).rotateY(r(180)).rotateZ(r(5)).rotateX(r(90)).scale(1/16f);}
    private static float r(float degrees){return (float)Math.toRadians(degrees);}
    public static void main(String[] args)throws Exception{
        witnesses();String renderer=text("TerminalInstallerRenderer.java.txt"),lambda=text("RenderModelItem.java.txt"),model=text("ItemModelCustom.java.txt");
        for(String line:new String[]{"renderEntityItem = true","renderInventory = false","this.scale = 4.0","VecUtils.vec(0.5, 0.16, -0.24)","VecUtils.vec(0.2, -0.15, 0.25)","this.thirdPersonScale = 0.73","this.stdRotation.xCoord = 90","this.stdRotation.zCoord = 5","this.entityItemScale = 1.1","this.entityItemOffset.xCoord = -0.1","this.entityItemRotation.yCoord = 180","this.equipRotation.yCoord = -15","this.equipRotation.zCoord = 90","this.equipScale = 0.8"})check(renderer.contains(line),"Original terminal renderer constant "+line);
        check(lambda.indexOf("this.doTransformation(equipOffset)")<lambda.indexOf("this.doRotation(equipRotation)"),"Exact source equip translate→rotate order");check(lambda.indexOf("this.doRotation(entityItemRotation)")<lambda.indexOf("this.doTransformation(entityItemOffset)"),"Source entity rotate→translate order");check(model.contains("GL11.glScalef(scale, scale, scale)"),"Original OBJ1/16 scale");
        try(var oracle=new OriginalTerminalOracle()){
            var sourceMatrix=oracle.method("cn.academy.terminal.client.RendererProbe","matrix",String.class);
            check(sourceMatrix.invoke(null,"INVENTORY")==null,"Actual original renderer refuses inventory");
            var random=new java.util.Random(31);
            for(Context context:Context.values()){
                String sourceContext=switch(context){case FIRST_PERSON->"EQUIPPED_FIRST_PERSON";case THIRD_PERSON->"EQUIPPED";case GROUND,STANDARD->"ENTITY";};
                double[] values=(double[])sourceMatrix.invoke(null,sourceContext);
                if(context==Context.STANDARD)continue; // Source STANDARD is the common inner suffix, not a separate callback render type.
                for(int i=0;i<10000;i++){
                    Vector3f point=new Vector3f(random.nextFloat()*50-25,random.nextFloat()*50-25,random.nextFloat()*50-25);Vector3f actual=TerminalInstallerTransform.matrix(context,false).transformPosition(new Vector3f(point));
                    double[] expected={values[0]*point.x+values[4]*point.y+values[8]*point.z+values[12],values[1]*point.x+values[5]*point.y+values[9]*point.z+values[13],values[2]*point.x+values[6]*point.y+values[10]*point.z+values[14]};
                    equal(actual.x,expected[0],2e-5,"Executed original source renderer X "+context);equal(actual.y,expected[1],2e-5,"Executed original source renderer Y "+context);equal(actual.z,expected[2],2e-5,"Executed original source renderer Z "+context);
                    if(context==Context.FIRST_PERSON||context==Context.THIRD_PERSON){var mirrored=TerminalInstallerTransform.matrix(context,true).transformPosition(new Vector3f(point));equal(mirrored.x,-actual.x,1e-5,"Modern left-hand X mirror");equal(mirrored.y,actual.y,1e-5,"Modern left-hand Y");equal(mirrored.z,actual.z,1e-5,"Modern left-hand Z");}
                }
            }
        }
        try(var input=TerminalInstallerRegressionTest.class.getResourceAsStream("/assets/academy/models/terminal_installer.obj")){check(input!=null,"Genuine installer OBJ packaged");var mesh=ClassicDeveloperObj.parse(new InputStreamReader(input,StandardCharsets.UTF_8));check(mesh.positionCount()==47&&mesh.triangles().size()==72,"Source terminal mesh, not placeholder cube");for(var triangle:mesh.triangles())for(var vertex:java.util.List.of(triangle.a(),triangle.b(),triangle.c()))check(Float.isFinite(vertex.u())&&Float.isFinite(vertex.v()),"Original model UV finite");}
        final int[] guiTransforms={0};BakedModel icon=(BakedModel)Proxy.newProxyInstance(BakedModel.class.getClassLoader(),new Class[]{BakedModel.class},(proxy,method,values)->{if(method.getName().equals("applyTransform")){guiTransforms[0]++;return proxy;}if(method.getName().equals("getTransforms"))return ItemTransforms.NO_TRANSFORMS;if(method.getReturnType()==boolean.class)return false;return null;});
        var wrapper=new cn.academy.port.client.TerminalInstallerBakedModel(icon);for(int loop=0;loop<50;loop++){check(wrapper.applyTransform(ItemDisplayContext.GUI,new PoseStack(),false)==icon,"Retained original2D GUI icon");for(ItemDisplayContext context:ItemDisplayContext.values())if(context!=ItemDisplayContext.GUI){BakedModel physical=wrapper.applyTransform(context,new PoseStack(),false);check(physical.isCustomRenderer(),"Physical source custom model "+context);check(physical.getTransforms()==ItemTransforms.NO_TRANSFORMS,"No generated-icon transform in physical context");}check(wrapper.getTransforms()==ItemTransforms.NO_TRANSFORMS,"Ground helper source metadata");}check(guiTransforms[0]==50,"Context routing never mutates icon");
        check(ClassicPortableContextBridge.matrix(Context.GROUND,false).isFinite(),"Verified legacy full3D caller chain reused");
        System.out.println("PASS TerminalInstallerRegressionTest "+assertions+" assertions; genuine mesh/source transforms and actual BakedModel routing");
    }
}
