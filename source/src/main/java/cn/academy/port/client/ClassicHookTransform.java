/* AcademyCraft1.0.7 ItemMagHook.HookRender/RendererMagHook and LambdaLib1.2.3 RenderModelItem. GPLv3/MIT. */
package cn.academy.port.client;

import org.joml.Matrix4f;

/** Exact source OpenGL order, including the shared HookRender's first-equipped-render offset quirk. */
public final class ClassicHookTransform {
    public static final float ENTITY_SCALE=.0054F,ITEM_SCALE=.15F,MODEL_SCALE=.0625F;
    public static final class ItemState {
        private float equipX=1,equipY=0;
        public Matrix4f render(ClassicPortableTransform.Context context,boolean left){
            var matrix=new Matrix4f();
            boolean equipped=context==ClassicPortableTransform.Context.FIRST_PERSON||context==ClassicPortableTransform.Context.THIRD_PERSON;
            if(equipped){
                if(left)matrix.scale(-1,1,1);
                matrix.rotateZ(rad(40)).translate(equipX,equipY,0).rotateY(rad(-90));
            }
            // HookRender mutates these in renderAtStdPosition, after equipped offset was applied.
            equipX=.5F;equipY=.1F;
            return matrix.scale(ITEM_SCALE).translate(0,0,1).scale(-1,-1,1).rotateY(rad(90)).rotateZ(rad(90)).scale(MODEL_SCALE);
        }
    }
    public static Matrix4f entity(float yaw,float pitch){return new Matrix4f().rotateY(rad(-yaw+90)).rotateZ(rad(pitch-90)).scale(ENTITY_SCALE);}
    private static float rad(float degrees){return(float)Math.toRadians(degrees);}
    private ClassicHookTransform(){}
}
