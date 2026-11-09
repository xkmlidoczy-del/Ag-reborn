/* AcademyCraft1.0.7 SettingsUI/PropertyElements/settings.xml adaptation. GPLv3. */
package cn.academy.port.client.terminal;

import cn.academy.port.AcademyConfig;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.client.AcademyClient;
import cn.academy.port.client.ClassicTerminalDrawing;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Source checkboxes, real binding edits and UI callback, backed by persistent configs/options. */
final class TerminalSettingsScreen extends Screen {
    private record Row(String label,BooleanSupplier checked,Consumer<Boolean> change,KeyMapping key,Runnable action,boolean heading){}
    private final ClassicTerminalDrawing drawing=new ClassicTerminalDrawing();
    private final List<Row> rows=new ArrayList<>();
    private KeyMapping editing;
    private double scroll;
    private boolean dragging;
    TerminalSettingsScreen(){super(Component.translatable("ac.app.settings.name"));}
    @Override protected void init(){
        if(!rows.isEmpty())return;
        header("generic");
        if(minecraft.isLocalServer()){
            check("attackPlayer",()->AcademyConfig.ATTACK_PLAYERS.get(),value->minecraft.getSingleplayerServer().execute(()->AcademyConfig.ATTACK_PLAYERS.set(value)));
            check("destroyBlocks",()->AcademyConfig.DESTROY_BLOCKS.get(),value->minecraft.getSingleplayerServer().execute(()->AcademyConfig.DESTROY_BLOCKS.set(value)));
        }
        check("headsOrTails",()->ClassicHudConfig.HEADS_OR_TAILS.get(),ClassicHudConfig.HEADS_OR_TAILS::set);
        header("keys");key("ability_activation",AcademyClient.TOGGLE);key("edit_preset",AcademyClient.EDIT_PRESET);key("switch_preset",AcademyClient.SWITCH_PRESET);key("open_data_terminal",TerminalClient.OPEN);
        for(int slot=0;slot<4;slot++)key("ability_"+slot,AcademyClient.SLOTS[slot]);
        header("misc");rows.add(new Row("ac.settings.prop.edit_ui",null,null,null,()->minecraft.setScreen(new TerminalHudCustomization()),false));
    }
    private void header(String id){rows.add(new Row("ac.settings.cat."+id,null,null,null,null,true));}
    private void check(String id,BooleanSupplier current,Consumer<Boolean> change){rows.add(new Row("ac.settings.prop."+id,current,change,null,null,false));}
    private void key(String id,KeyMapping mapping){rows.add(new Row("ac.settings.prop."+id,null,null,mapping,null,false));}
    private double x0(){return (width-742*.2)/2;}
    private double y0(){return (height-923*.2)/2;}
    private double maxScroll(){return Math.max(0,rows.size()*60+60-720);}
    private static String local(String id){return Component.translatable(id).getString();}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        graphics.fillGradient(0,0,width,height,0xc0101010,0xd0101010);graphics.flush();drawing.begin(graphics);graphics.pose().pushPose();
        try{
            graphics.pose().translate(x0(),y0(),0);graphics.pose().scale(.2f,.2f,1);drawing.texture("guis/settings",0,0,742,923,1);
            graphics.enableScissor((int)(x0()+60*.2),(int)(y0()+120*.2),(int)(x0()+674*.2),(int)(y0()+840*.2));
            try{for(int i=0;i<rows.size();i++)drawRow(rows.get(i),120+i*60-scroll);}finally{graphics.disableScissor();}
            double progress=maxScroll()==0?0:scroll/maxScroll();drawing.texture("guis/life_record/rollbar",673,119+641*progress,9,96,1);
        }finally{graphics.pose().popPose();drawing.end();}
    }
    private void drawRow(Row row,double y){
        if(row.heading){drawing.text(local(row.label),68,y+18,42,0xffffff,2/3d,0);drawing.fill(60,y+56,611,4,0xffffff,.35);return;}
        drawing.text(local(row.label),75,y+10,40,0xffffff,1,0);
        if(row.checked!=null)drawing.texture("guis/check_"+(row.checked.getAsBoolean()?"true":"false"),610,y+12.5,35,35,1);
        else if(row.key!=null)drawing.text(editing==row.key?"PRESS":row.key.getTranslatedKeyMessage().getString(),500,y+10,40,editing==row.key?0xfb8525:0xc8c8c8,editing==row.key?200/255d:1,0);
        else if(row.action!=null)drawing.text("OK",580,y+10,40,0xffffff,1,1);
    }
    private void finish(InputConstants.Key key){if(editing!=null){editing.setKey(key);KeyMapping.resetMapping();minecraft.options.save();editing=null;}}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(editing!=null){if(key==GLFW.GLFW_KEY_ESCAPE)editing=null;else finish(InputConstants.getKey(key,scan));return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(editing!=null){finish(InputConstants.Type.MOUSE.getOrCreate(button));return true;}
        double x=(mx-x0())/.2,y=(my-y0())/.2;
        if(button==0&&x>=668&&x<=692&&y>=119&&y<=856){dragging=true;drag(mx,my);return true;}
        if(button!=0||x<60||x>674||y<120||y>840)return super.mouseClicked(mx,my,button);
        int index=(int)((y-120+scroll)/60);if(index<0||index>=rows.size())return true;Row row=rows.get(index);
        if(row.checked!=null&&x>=610&&x<=645)row.change.accept(!row.checked.getAsBoolean());
        else if(row.key!=null&&x>=500&&x<=660)editing=row.key;
        else if(row.action!=null&&x>=500&&x<=660)row.action.run();
        return true;
    }
    private void drag(double mx,double my){scroll=TerminalTimeline.clamp(((my-y0())/.2-119-48)/641)*maxScroll();}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging){drag(x,y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(maxScroll(),scroll-dy*60));return true;}
    @Override public void removed(){drawing.release();if(ClassicHudConfig.SPEC.isLoaded())ClassicHudConfig.SPEC.save();if(minecraft.isLocalServer())minecraft.getSingleplayerServer().execute(()->AcademyConfig.SPEC.save());super.removed();}
}
