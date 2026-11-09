package cn.lambdalib.cgui.gui;
import cn.lambdalib.cgui.gui.component.Component;
/** Finite ownership/transform boundary for unchanged source ElementList; no render or input simulation. */
public class Widget {
 public final Transform transform=new Transform();public boolean dirty,needCopy=true;
 public static class Transform{public double width,height,x,y;public boolean doesDraw=true;}
 public void addWidget(Widget w){}public void dispose(){}public <T extends Component>T getComponent(String id){return null;}
}
