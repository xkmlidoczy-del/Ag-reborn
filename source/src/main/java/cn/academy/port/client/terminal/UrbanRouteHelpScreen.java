package cn.academy.port.client.terminal;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** In-game route instructions. All completion remains authoritative in the world. */
final class UrbanRouteHelpScreen extends Screen {
    private final Screen parent;private int scroll,max;
    UrbanRouteHelpScreen(Screen parent){super(Component.translatable("academy.urban.routes"));this.parent=parent;}
    @Override protected void init(){addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(width/2-50,height-32,100,20).build());}
    @Override public void render(GuiGraphics g,int mx,int my,float tick){renderBackground(g,mx,my,tick);int w=Math.min(360,width-24),x=(width-w)/2,y=20;
        g.fill(x-8,10,x+w+8,height-40,0xee111f2b);g.drawString(font,title,x,20,0xff80d9e7,false);g.enableScissor(x,40,x+w,height-48);
        int lineY=44-scroll;
        try{for(String key:new String[]{"equipment","ability","combat","final","media"}){
            for(var line:font.split(Component.translatable("academy.urban.help."+key),w)){g.drawString(font,line,x,lineY,0xffe5f2f5,false);lineY+=12;}lineY+=10;
        }}finally{g.disableScissor();}max=Math.max(0,lineY+scroll-(height-48));super.render(g,mx,my,tick);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(max,scroll-(int)(vertical*18)));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
