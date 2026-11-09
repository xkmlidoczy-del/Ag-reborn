/* AcademyCraft1.0.7 page_wireless.xml source geometry. GPLv3; see NOTICE. */
package cn.academy.port.client;
public final class ClassicEnergyBridgeLayout {
    public static final double WIDTH=176,HEIGHT=187,PANEL_WIDTH=165,PANEL_HEIGHT=132.5859375;
    public static final double PANEL_X=(WIDTH-PANEL_WIDTH)/2,PANEL_Y=(HEIGHT-PANEL_HEIGHT)/2+17.4140625;
    public static final double CONNECTED_X=PANEL_X+2,CONNECTED_Y=PANEL_Y-10,ROW_X=PANEL_X+2.5,ROW_Y=PANEL_Y+15.37890625;
    public static final double ARROW_X=PANEL_X+PANEL_WIDTH-16,UP_Y=PANEL_Y+7,DOWN_Y=PANEL_Y+PANEL_HEIGHT-16;
    public static final int ROW_HEIGHT=16,ROWS=7;
    private ClassicEnergyBridgeLayout(){}
    public static int maxProgress(int count){return Math.max(0,count-ROWS);}
}
