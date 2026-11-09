/* AcademyCraft1.0.7 CustomizeUI/ui_edit.xml configuration editor. GPLv3. */
package cn.academy.port.client.terminal;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.client.ClassicTerminalDrawing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Edits actual registered HUD config values. Additional genuine HUD implementations may register their own preview. */
public final class TerminalHudCustomization extends Screen {
    @FunctionalInterface public interface Preview{void render(GuiGraphics graphics,double x,double y);}
    private record Node(Supplier<double[]> get,BiConsumer<Double,Double> set,Preview preview){}
    private static final Map<String,Node> NODES=new LinkedHashMap<>();
    private final ClassicTerminalDrawing drawing=new ClassicTerminalDrawing();
    private String selected;
    private EditBox x,y;
    private boolean initializing;
    static {
        registerNode("cpbar",()->new double[]{ClassicHudConfig.CP_X.get(),ClassicHudConfig.CP_Y.get()},(x,y)->{ClassicHudConfig.CP_X.set(x);ClassicHudConfig.CP_Y.set(y);},null);
        registerNode("keyhint",()->new double[]{ClassicHudConfig.KEY_X.get(),ClassicHudConfig.KEY_Y.get()},(x,y)->{ClassicHudConfig.KEY_X.set(x);ClassicHudConfig.KEY_Y.set(y);},null);
        registerNode("notification",()->new double[]{ClassicHudConfig.NOTIFICATION_X.get(),ClassicHudConfig.NOTIFICATION_Y.get()},(x,y)->{ClassicHudConfig.NOTIFICATION_X.set(x);ClassicHudConfig.NOTIFICATION_Y.set(y);},null);
        registerNode("media",cn.academy.port.client.terminal.media.ClassicMediaClient::hudPosition,cn.academy.port.client.terminal.media.ClassicMediaClient::setHudPosition,cn.academy.port.client.terminal.media.ClassicMediaClient::renderHudPreview);
    }
    public static void registerNode(String id,Supplier<double[]> position,BiConsumer<Double,Double> setter,Preview preview){NODES.put(id,new Node(position,setter,preview));}
    public TerminalHudCustomization(){super(Component.translatable("ac.settings.prop.edit_ui"));}
    @Override protected void init(){if(selected!=null)focus(selected);}
    private void focus(String id){selected=id;clearWidgets();Node node=NODES.get(id);double[] position=node.get.get();initializing=true;
        int index=NODES.keySet().stream().toList().indexOf(id),xx=167,yy=120+index*12;
        x=new EditBox(font,xx+10,yy+3,34,10,Component.literal("X"));y=new EditBox(font,xx+53,yy+3,34,10,Component.literal("Y"));x.setBordered(false);y.setBordered(false);x.setMaxLength(32);y.setMaxLength(32);x.setValue(Double.toString(position[0]));y.setValue(Double.toString(position[1]));
        addRenderableWidget(x);addRenderableWidget(y);initializing=false;
    }
    private void commit(EditBox box,boolean horizontal){if(initializing||selected==null)return;try{double value=Double.parseDouble(box.getValue());if(!Double.isFinite(value)||value< -512||value>512)throw new NumberFormatException();Node node=NODES.get(selected);double[] previous=node.get.get();node.set.accept(horizontal?value:previous[0],horizontal?previous[1]:value);box.setTextColor(0xcccccc);}catch(NumberFormatException ignored){box.setTextColor(0xbb3333);}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER||key==org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER){if(x!=null&&x.isFocused()){commit(x,true);return true;}if(y!=null&&y.isFocused()){commit(y,false);return true;}}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&mx>=98&&mx<=162&&my>=122&&my<=122+NODES.size()*12){int index=(int)((my-122)/12);focus(NODES.keySet().stream().toList().get(index));return true;}return super.mouseClicked(mx,my,button);}
    @Override public void render(GuiGraphics graphics,int mx,int my,float partial){
        for(var node:NODES.values())if(node.preview!=null){double[] position=node.get.get();node.preview.render(graphics,position[0],position[1]);}
        drawing.begin(graphics);
        try{
            for(var entry:NODES.entrySet())drawPreview(graphics,entry.getKey(),entry.getValue());
            drawing.texture("guis/window_ui_resize",94,104,72,101.5,1);drawing.text(Component.translatable("ac.gui.uiedit.elements").getString(),98,108,8,0xffffff,1,0);
            int i=0;for(String id:NODES.keySet()){drawing.text(Component.translatable("ac.gui.uiedit.elm."+id).getString(),98,122+12*i,6,0xffffff,1,0);i++;}
            if(selected!=null){int index=NODES.keySet().stream().toList().indexOf(selected);drawing.fill(167,120+index*12,90,16,0x272727,.8);drawing.fill(177,123+index*12,34,10,0x333333,1);drawing.fill(220,123+index*12,34,10,0x333333,1);drawing.text("X",169,123+index*12,10,0xffffff,1,0);drawing.text("Y",213,123+index*12,10,0xffffff,1,0);}
        }finally{drawing.end();}
        super.render(graphics,mx,my,partial);
    }
    private void drawPreview(GuiGraphics graphics,String id,Node node){double[] pos=node.get.get();double px=pos[0],py=pos[1];
        if(node.preview!=null)return;
        double w,h;
        switch(id){case "cpbar"->{w=964*.2;h=147*.2;px+=width-w;drawing.texture("guis/edit_preview/cpbar",px,py,w,h,1);}case "keyhint"->{w=140*.46;h=210*.46;px+=width-w;py+=(height-h)/2;drawing.texture("guis/edit_preview/key_hint",px,py,128*.46,193*.46,1);}default->{w=517*.25;h=170*.25;drawing.texture("guis/notification/back",px,py,w,h,1);drawing.texture("guis/edit_preview/notify_logo",px+34*.25,py+42*.25,83*.25,83*.25,1);drawing.text("Some Notification",px+137*.25,py+32*.25,38*.25,0xffffff,1,0);drawing.text("blablabla",px+137*.25,py+81*.25,54*.25,0xffffff,1,0);}}
        if(id.equals(selected))drawing.glow(px,py,w,h,1,0xffffff,1);
    }
    @Override public void removed(){drawing.release();if(ClassicHudConfig.SPEC.isLoaded())ClassicHudConfig.SPEC.save();super.removed();}
}
