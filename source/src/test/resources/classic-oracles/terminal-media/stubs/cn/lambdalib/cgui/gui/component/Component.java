package cn.lambdalib.cgui.gui.component;
import cn.lambdalib.cgui.gui.Widget;
public class Component implements Cloneable {
 protected Widget widget;public Component(String id){}public void bind(Widget owner){widget=owner;onAdded();}public void onAdded(){}public void onRemoved(){}
 public Component copy(){try{return(Component)clone();}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
}
