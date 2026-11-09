package cn.academy.port.client;

import org.joml.Matrix4f;

/** Reproduces the missing legacy vanilla/Forge caller basis before the unchanged LambdaLib item-local matrix. */
public final class ClassicPortableContextBridge {
    public static final float LEGACY_HAND_SCALE=.4f,FORGE_EQUIPPED_SCALE=1.5f;
    public static final float NATIVE_GROUND_LIFT=.25f,LEGACY_ENTITY_Y_ORIGIN=.125f,FORGE_ENTITY_SCALE=.5f;
    private static float radians(float degrees){return (float)Math.toRadians(degrees);}
    public static Matrix4f matrix(ClassicPortableTransform.Context context,boolean leftHand){
        if(context==ClassicPortableTransform.Context.GROUND)return new Matrix4f()
                // Old EntityItem posY was boxMinY + height/2; modern posY is boxMinY.
                .translate(0,LEGACY_ENTITY_Y_ORIGIN-NATIVE_GROUND_LIFT,0).scale(FORGE_ENTITY_SCALE);
        if(context==ClassicPortableTransform.Context.STANDARD)return new Matrix4f();
        if(context==ClassicPortableTransform.Context.THIRD_PERSON){
            // After the native arm bone, undo the1.21.1 ItemInHandLayer suffix and restore the
            //1.7.10 full3D/non-rotating/non-block suffix followed by the shared Forge helper.
            Matrix4f modern=new Matrix4f().rotateX(radians(-90)).rotateY(radians(180)).translate(1/16f,.125f,-.625f);
            Matrix4f legacy=new Matrix4f().translate(-1/16f,7/16f,1/16f).translate(0,3/16f,0)
                    .scale(.625f,-.625f,.625f).rotateX(radians(-100)).rotateY(radians(45));
            Matrix4f forge=new Matrix4f().translate(0,-.3f,0).scale(FORGE_EQUIPPED_SCALE)
                    .rotateY(radians(50)).rotateZ(radians(335)).translate(-.9375f,-.0625f,0);
            Matrix4f bridge=modern.invert().mul(legacy).mul(forge);
            return leftHand?new Matrix4f().scale(-1,1,1).mul(bridge).scale(-1,1,1):bridge;
        }
        //1.21.1's attack transform ends Ry(-45);1.7.10 kept Ry45 and scaled .4 before renderItem.
        // Restore that caller basis, then exact ForgeHooksClient's non-EQUIPPED_BLOCK helper.
        Matrix4f bridge=new Matrix4f().rotateY(radians(45)).scale(LEGACY_HAND_SCALE)
                .translate(0,-.3f,0).scale(FORGE_EQUIPPED_SCALE)
                .rotateY(radians(50)).rotateZ(radians(335)).translate(-.9375f,-.0625f,0);
        // The source-local matrix already mirrors a modern left hand. Conjugate this outer basis,
        // so bridgeLeft * localLeft == mirror * bridgeRight * localRight, without a second mirror.
        if(leftHand)return new Matrix4f().scale(-1,1,1).mul(bridge).scale(-1,1,1);
        return bridge;
    }
    private ClassicPortableContextBridge(){}
}
