/* AcademyCraft1.0.7 ItemSilbarn.RenderSilbarn + LambdaLib1.2.3 RenderModelItem. GPLv3/MIT; see NOTICE. */
package cn.academy.port.client;
import org.joml.Matrix4f;
/** Original item-local matrices after the already-tested legacy caller bridge. Left hands mirror source basis. */
public final class ClassicSilbarnTransform {
 private ClassicSilbarnTransform(){}private static float rad(float v){return(float)(v*Math.PI/180);}
 public static Matrix4f matrix(ClassicPortableTransform.Context c,boolean left){Matrix4f m=new Matrix4f();if(left&&(c==ClassicPortableTransform.Context.FIRST_PERSON||c==ClassicPortableTransform.Context.THIRD_PERSON))m.scale(-1,1,1);if(c==ClassicPortableTransform.Context.FIRST_PERSON||c==ClassicPortableTransform.Context.THIRD_PERSON)m.rotateZ(rad(40)).translate(.5F,.1F,-.2F).rotateY(rad(90)).rotateY(rad(-90));return m.scale(-1,-1,1).rotateY(rad(180)).rotateX(rad(90)).scale(.0625F);}
}
