/* AcademyCraft1.0.7 page_interfere.xml/TechUI and LambdaLib ElementList. GPLv3/MIT; see NOTICE. */
package cn.academy.port.client;
/** Plain CGuiScreen uses the centered172×187 TechUI root, without a container offset. */
public final class ClassicInterfererVisualRules {
    public static final double WIDTH=176,HEIGHT=187,LIST_X=8,LIST_Y=78.5,AREA_Y=98.5,ROW_HEIGHT=16,CONFIG_X=8,CONFIG_Y=25;
    private ClassicInterfererVisualRules(){}
    public static double rootX(int width){return (width-172)/2d;}
    public static double pageX(int width){return rootX(width)-2;}
    public static double pageY(int height){return (height-187)/2d;}
    /** Literal ElementList.getMaxProgress: equality leaves one scroll step for exactly5 rows. */
    public static int maxProgress(int count){return count<5?0:count-4;}
    public static int visibleRows(int count,int progress){return Math.max(0,Math.min(5,count-progress));}
    public static double rowX(int width){return pageX(width)+LIST_X-5;}
}
