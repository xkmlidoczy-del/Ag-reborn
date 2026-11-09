/* TerminalInstallerRenderer/RenderModelItem/ItemModelCustom. AcademyCraft GPLv3 + LambdaLib MIT. */
package cn.academy.port.client.terminal;
import cn.academy.port.client.ClassicPortableTransform.Context;
import org.joml.Matrix4f;

/** Exact source post-multiplied local matrix; legacy caller reconstruction remains in ClassicPortableContextBridge. */
public final class TerminalInstallerTransform {
    public static final float SCALE=4,MODEL_SCALE=1/16f,EQUIP_SCALE=.8f,THIRD_SCALE=.73f,ENTITY_SCALE=1.1f;
    private TerminalInstallerTransform(){}
    private static float rad(double degrees){return (float)Math.toRadians(degrees);}
    public static Matrix4f matrix(Context context,boolean left){
        Matrix4f matrix=new Matrix4f();if(left&&(context==Context.FIRST_PERSON||context==Context.THIRD_PERSON))matrix.scale(-1,1,1);
        if(context==Context.FIRST_PERSON||context==Context.THIRD_PERSON){
            matrix.rotateZ(rad(40));if(context==Context.THIRD_PERSON)matrix.translate(.2f,-.15f,.25f);
            matrix.translate(.5f,.16f,-.24f).rotateY(rad(-15)).rotateZ(rad(90)).rotateX(0).rotateY(rad(-90)).scale(EQUIP_SCALE);
            if(context==Context.THIRD_PERSON)matrix.scale(THIRD_SCALE);
        }else if(context==Context.GROUND)matrix.rotateY(rad(180)).rotateZ(0).rotateX(0).translate(-.1f,0,0).scale(ENTITY_SCALE);
        return matrix.scale(SCALE).translate(0,0,0).scale(-1,-1,1).rotateY(rad(180)).rotateZ(rad(5)).rotateX(rad(90)).scale(MODEL_SCALE);
    }
}
