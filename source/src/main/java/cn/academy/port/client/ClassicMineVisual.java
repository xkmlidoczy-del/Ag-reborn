/* AcademyCraft MineDetect/HandlerRender and LambdaLib MeshUtils geometry. See NOTICE. */
package cn.academy.port.client;

/** Pure render inputs used by actual vertex submission, not a substitute visual demo. */
public final class ClassicMineVisual {
    public static final String TEXTURE="textures/effects/mineview.png", SOUND="em.minedetect";
    public static final float SOUND_VOLUME=.5F;
    public static final double INSET=.05, WIDTH=.9;
    private static final int[][] COLORS={{115,200,227},{161,181,188},{87,231,248},{97,204,94},{235,109,84}};
    private static final double[][] POINTS={{0,0,0},{WIDTH,0,0},{WIDTH,0,WIDTH},{0,0,WIDTH},
            {0,WIDTH,0},{WIDTH,WIDTH,0},{WIDTH,WIDTH,WIDTH},{0,WIDTH,WIDTH}};
    private static final int[][] FACES={{0,1,2,3},{4,5,6,7},{5,1,2,6},{6,2,3,7},{7,3,0,4},{4,0,1,5}};
    private static final float[][] UV={{0,0},{1,0},{1,1},{0,1}};
    private ClassicMineVisual() {}
    public static float alpha(double x,double y,double z,double range) {
        double jdg=1-Math.sqrt(x*x+y*y+z*z)/range*2.2;
        return .3F+(float)(jdg*.7);
    }
    /** OpenGL fixed-function color clamps normalized channels; modern format is normalized U8. */
    public static int alphaByte(float alpha) {
        if(!Float.isFinite(alpha))return 0;
        return Math.max(0,Math.min(255,Math.round(Math.max(0F,Math.min(1F,alpha))*255F)));
    }
    public static int color(int level,int channel){return COLORS[Math.max(0,Math.min(COLORS.length-1,level))][channel];}
    public static Vertex vertex(int face,int corner) {
        var p=POINTS[FACES[face][corner]];var uv=UV[corner];
        return new Vertex(p[0],p[1],p[2],uv[0],uv[1]);
    }
    public record Vertex(double x,double y,double z,float u,float v) {}
}
