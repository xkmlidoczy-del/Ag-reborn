/* RenderDeveloperPortable/RenderModelItem/ItemModelCustom composition. GPLv3 + MIT; see NOTICE. */
package cn.academy.port.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Original OpenGL call order, converted to modern post-multiplied matrices without hand-tuned re-layout. */
public final class ClassicPortableTransform {
    public enum Context { FIRST_PERSON,THIRD_PERSON,GROUND,STANDARD }
    public static final float SOURCE_SCALE=6,MODEL_SCALE=1/16f,THIRD_PERSON_SCALE=.6f;
    private static Matrix4f rotate(Matrix4f matrix,float x,float y,float z){return matrix.rotateY(radians(y)).rotateZ(radians(z)).rotateX(radians(x));}
    private static float radians(float degrees){return (float)Math.toRadians(degrees);}
    public static Matrix4f matrix(Context context,boolean leftHand){
        Matrix4f matrix=new Matrix4f();
        // Left hands do not exist in1.7.10; mirror only the source-local transform for modern left-hand contexts.
        if(leftHand&&(context==Context.FIRST_PERSON||context==Context.THIRD_PERSON))matrix.scale(-1,1,1);
        if(context==Context.FIRST_PERSON||context==Context.THIRD_PERSON){
            matrix.rotateZ(radians(40));
            if(context==Context.THIRD_PERSON)matrix.translate(.1f,.05f,.2f);
            matrix.translate(.6f,0,-.2f);rotate(matrix,0,-10,-5);matrix.rotateY(radians(-90));
            if(context==Context.THIRD_PERSON)matrix.scale(THIRD_PERSON_SCALE);
        }else if(context==Context.GROUND){rotate(matrix,15,0,0);matrix.translate(0,0,.2f);}
        // Standard scale6, zero standard offset/rotation, axis flip, Y+180, then ItemModelCustom scale1/16.
        matrix.scale(SOURCE_SCALE).scale(-1,-1,1).rotateY(radians(180)).scale(MODEL_SCALE);
        return matrix;
    }
    public static Vector3f point(Context context,boolean leftHand,float x,float y,float z){return matrix(context,leftHand).transformPosition(new Vector3f(x,y,z));}
    private ClassicPortableTransform(){}
}
