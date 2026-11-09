/* AcademyCraft1.0.7 RenderWindGen*/
package cn.academy.port.client;
import cn.academy.port.wind.ClassicWindRules;
/** Source renderer transforms and literal GUI alpha table. */
public final class ClassicWindVisualRules {
    public static final int INVENTORY_WIDTH=176,INVENTORY_HEIGHT=187,BUFFER_COLOR=0xff25f7ff;
    public static final double INFO_X=179,INFO_Y=5,INFO_WIDTH=100,BASE_INFO_HEIGHT=99,MAIN_INFO_HEIGHT=50;
    private ClassicWindVisualRules(){}
    public static double mainX(int screenWidth){return (screenWidth-172)/2.0-18;}
    public static double inventoryX(int screenWidth){return mainX(screenWidth)-2;}
    public static double inventoryY(int screenHeight){return (screenHeight-187)/2.0;}
    public static int slotX(int screenWidth){return (screenWidth-207)/2;}
    public static int slotY(int screenHeight){return (screenHeight-186)/2;}
    public static double yaw(ClassicWindRules.Facing facing){return switch(facing){case NORTH->180;case EAST->90;case SOUTH->0;case WEST->-90;};}
    public static double pivotX(ClassicWindRules.Kind kind,ClassicWindRules.Facing facing){return kind!=ClassicWindRules.Kind.MAIN?.5:facing==ClassicWindRules.Facing.WEST?.4:facing==ClassicWindRules.Facing.EAST?.6:.5;}
    public static double pivotZ(ClassicWindRules.Kind kind,ClassicWindRules.Facing facing){return kind!=ClassicWindRules.Kind.MAIN?.5:facing==ClassicWindRules.Facing.NORTH?.4:facing==ClassicWindRules.Facing.SOUTH?.6:.5;}
    public static double[] iconAlpha(ClassicWindRules.Completeness state){return switch(state){case BASE_ONLY->new double[]{.2,.2,1};case NO_TOP->new double[]{.2,1,1};case COMPLETE->new double[]{1,1,1};case COMPLETE_NOT_WORKING->new double[]{.6,1,1};};}
    public static boolean showFan(boolean installed,boolean noObstacle){return installed&&noObstacle;}
    public static float rotation(float previous,double speed,long elapsed){return (float)(previous+speed*elapsed/1000.0);}
}
