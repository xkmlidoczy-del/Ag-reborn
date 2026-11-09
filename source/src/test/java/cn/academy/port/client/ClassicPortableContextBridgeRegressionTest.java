package cn.academy.port.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Original1.7.10 caller vs modern native swing matrix, plus real OBJ projection regression. */
public final class ClassicPortableContextBridgeRegressionTest {
    private static int assertions;
    private static void yes(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    private static void near(float expected,float actual,String label){yes(Float.isFinite(actual)&&Math.abs(expected-actual)<3e-6,label);}
    private static float radians(float degrees){return (float)Math.toRadians(degrees);}
    private static Matrix4f forge(){return new Matrix4f().translate(0,-.3f,0).scale(1.5f).rotateY(radians(50)).rotateZ(radians(335)).translate(-.9375f,-.0625f,0);}
    private static Matrix4f oldSwing(float swing){
        float sinSquared=(float)Math.sin(swing*swing*Math.PI),sinRoot=(float)Math.sin(Math.sqrt(swing)*Math.PI);
        return new Matrix4f().rotateY(radians(45)).rotateY(radians(-20*sinSquared)).rotateZ(radians(-20*sinRoot)).rotateX(radians(-80*sinRoot)).scale(.4f).mul(forge());
    }
    private static Matrix4f modernSwing(float swing){
        float sinSquared=(float)Math.sin(swing*swing*Math.PI),sinRoot=(float)Math.sin(Math.sqrt(swing)*Math.PI);
        return new Matrix4f().rotateY(radians(45-20*sinSquared)).rotateZ(radians(-20*sinRoot)).rotateX(radians(-80*sinRoot)).rotateY(radians(-45));
    }
    private static void equal(Matrix4f expected,Matrix4f actual){float[] a=new float[16],b=new float[16];expected.get(a);actual.get(b);for(int i=0;i<16;i++)near(a[i],b[i],"legacy caller composition component"+i);}
    public static void main(String[] args)throws Exception{
        for(int sample=0;sample<=100;sample++){
            float swing=sample/100f;Matrix4f local=ClassicPortableTransform.matrix(ClassicPortableTransform.Context.FIRST_PERSON,false);
            Matrix4f expected=oldSwing(swing).mul(new Matrix4f(local));
            Matrix4f actual=modernSwing(swing).mul(ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.FIRST_PERSON,false)).mul(new Matrix4f(local));
            equal(expected,actual);
            Matrix4f mirror=new Matrix4f().scale(-1,1,1);
            Matrix4f left=ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.FIRST_PERSON,true).mul(ClassicPortableTransform.matrix(ClassicPortableTransform.Context.FIRST_PERSON,true));
            equal(new Matrix4f(mirror).mul(ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.FIRST_PERSON,false)).mul(local),left);
        }
        for(var context:ClassicPortableTransform.Context.values())if(context==ClassicPortableTransform.Context.STANDARD){
            equal(new Matrix4f(),ClassicPortableContextBridge.matrix(context,false));equal(new Matrix4f(),ClassicPortableContextBridge.matrix(context,true));
        }
        ClassicDeveloperObj.Mesh mesh;
        try(var input=ClassicPortableContextBridgeRegressionTest.class.getResourceAsStream("/assets/academy/models/developer_portable.obj");var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){mesh=ClassicDeveloperObj.parse(reader);}
        Matrix4f outer=new Matrix4f().translate(.56f,-.52f,-.72f);
        Matrix4f current=new Matrix4f(outer).mul(ClassicPortableTransform.matrix(ClassicPortableTransform.Context.FIRST_PERSON,false));
        Matrix4f corrected=new Matrix4f(outer).mul(ClassicPortableContextBridge.matrix(ClassicPortableTransform.Context.FIRST_PERSON,false)).mul(ClassicPortableTransform.matrix(ClassicPortableTransform.Context.FIRST_PERSON,false));
        double tangent=Math.tan(Math.toRadians(70)/2),aspect=1180/812d;
        double previousMax=-Double.MAX_VALUE,nextMax=-Double.MAX_VALUE,previousMin=Double.MAX_VALUE,nextMin=Double.MAX_VALUE;
        for(var triangle:mesh.triangles())for(var v:new ClassicDeveloperObj.Vertex[]{triangle.a(),triangle.b(),triangle.c()}){
            var before=current.transformPosition(new Vector3f(v.x(),v.y(),v.z()));var after=corrected.transformPosition(new Vector3f(v.x(),v.y(),v.z()));
            yes(before.z<0&&after.z<0,"source mesh remains in front of hand camera");
            double oldX=before.x/(-before.z*tangent*aspect),newX=after.x/(-after.z*tangent*aspect);
            previousMax=Math.max(previousMax,oldX);nextMax=Math.max(nextMax,newX);previousMin=Math.min(previousMin,oldX);nextMin=Math.min(nextMin,newX);
        }
        yes(previousMax>2.6&&previousMin>.75,"captured screenshot's gross right-crop reproduced mathematically");
        yes(nextMax<1.25&&nextMin<.29,"legacy bridge returns most projected width into frame without custom fit offsets");
        System.out.printf(java.util.Locale.ROOT,"ClassicPortableContextBridgeRegressionTest: %d assertions passed; idle projected X %.3f..%.3f -> %.3f..%.3f at70deg/1180x812%n",assertions,previousMin,previousMax,nextMin,nextMax);
    }
}
