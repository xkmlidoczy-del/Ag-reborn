package cn.academy.port.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Geometry, alpha and actual renderer source contracts; does not assert GPU pixel parity. */
public final class MineDetectVisualRegressionTest {
    private static int n;
    private static void check(boolean yes,String text){n++;if(!yes)throw new AssertionError(text);}
    public static void main(String[] args)throws Exception {
        var random=new Random(17);
        for(int i=0;i<20000;i++){
            double x=random.nextDouble()*60-30,y=random.nextDouble()*60-30,z=random.nextDouble()*60-30,r=15+random.nextDouble()*13;
            float oracle=.3F+(float)((1-Math.sqrt(x*x+y*y+z*z)/r*2.2)*.7);
            check(Float.floatToIntBits(oracle)==Float.floatToIntBits(ClassicMineVisual.alpha(x,y,z,r)),"source exact alpha float differential");
        }
        check(ClassicMineVisual.alpha(15,0,0,15)<0,"source scan radius includes negative nominal alpha");
        check(ClassicMineVisual.alphaByte(-1)==0&&ClassicMineVisual.alphaByte(2)==255,"only U8 transport clamps alpha");
        int[][] rgb={{115,200,227},{161,181,188},{87,231,248},{97,204,94},{235,109,84}};
        for(int i=0;i<5;i++)for(int c=0;c<3;c++)check(ClassicMineVisual.color(i,c)==rgb[i][c],"exact original RGB");
        int[][] faces={{0,1,2,3},{4,5,6,7},{5,1,2,6},{6,2,3,7},{7,3,0,4},{4,0,1,5}};
        double[][] points={{0,0,0},{.9,0,0},{.9,0,.9},{0,0,.9},{0,.9,0},{.9,.9,0},{.9,.9,.9},{0,.9,.9}};
        float[][] uv={{0,0},{1,0},{1,1},{0,1}};
        for(int f=0;f<6;f++)for(int c=0;c<4;c++){
            var v=ClassicMineVisual.vertex(f,c);var p=points[faces[f][c]];
            check(v.x()==p[0]&&v.y()==p[1]&&v.z()==p[2],"source six-face position and winding");
            check(v.u()==uv[c][0]&&v.v()==uv[c][1],"full original mineview UV per face");
        }
        check(ClassicMineVisual.INSET==.05&&ClassicMineVisual.WIDTH==.9,"source inset0.05 width0.9");
        check(ClassicMineVisual.SOUND_VOLUME==.5F&&ClassicMineVisual.SOUND.equals("em.minedetect"),"source sound/volume");
        Path stage=Path.of(System.getProperty("academy.mine.sourceRoot","src/main/java")).resolve("cn/academy/port/client");
        var type=Files.readString(stage.resolve("ClassicMineRenderType.java"));
        check(type.contains("RenderSystem.disableDepthTest()")&&type.contains("setDepthTestState(THROUGH_WALL)"),"native depth override disables depth explicitly;NO_DEPTH_TEST alone is no-op");
        check(type.contains("false,false,CompositeState")&&type.contains(".setCullState(NO_CULL)"),"actual buffer is unsorted and uncullable");
        check(type.contains("getPositionTexColorShader")&&type.contains(".setLightmapState(NO_LIGHTMAP)"),"actual fog-free unlit native shader");
        check(type.contains("RenderSystem.blendFunc(")&&type.contains("ONE_MINUS_SRC_ALPHA"),"source src-alpha blend RGB and alpha");
        var effects=Files.readString(stage.resolve("ClassicMineDetectEffects.java"));
        check(effects.contains("modelView.set(event.getModelViewMatrix())")&&effects.contains("modelView.popMatrix()"),"AFTER_LEVEL camera model-view matrix scoped/restored");
        check(effects.contains("tag.getInt(\"entity\")!=player.getId()")&&effects.contains("mc.getConnection()!=connection"),"local-only queued packet session isolation");
        check(effects.contains("for(var ore:handler.aliveSims())")&&effects.contains("ClassicMineVisual.vertex(face,corner)"),"actual renderer uses source accumulation and tested textured geometry");
        check(effects.contains("buffers.endBatch(type)")&&effects.contains("BOXES_PER_BATCH=1000"),"private bounded batches retain all accumulated records");
        System.out.println("PASS "+n+" MineDetect render-input/source-wiring assertions; pixel/audible parity unverified");
    }
}
