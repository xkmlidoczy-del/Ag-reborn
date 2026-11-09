/* AcademyCraft1.0.7 TerminalUI.java / terminal.xml adaptation. GPLv3. */
package cn.academy.port.client.terminal;

import cn.academy.port.client.ClassicTerminalDrawing;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.joml.Matrix4f;
import java.util.List;

/** Genuine source perspective HUD; world movement continues, view turning and left attack are overridden. */
final class TerminalHud {
    private final ClassicTerminalDrawing drawing=new ClassicTerminalDrawing();
    private List<String> apps;
    private double mouseX=150,mouseY=150,buffX=150,buffY=150;
    private final TerminalPointerInput input=new TerminalPointerInput();
    private int selection,scroll,lastSelection=-1;
    private final int tutorialIcon;
    private long created=TerminalClient.gameTime(),lastFrame;
    private long nextQaObservation;
    TerminalHud(List<String> apps){this.apps=List.copyOf(apps);var mc=Minecraft.getInstance();float rand=mc.player.getRandom().nextFloat();tutorialIcon=rand<.2f?0:rand<.3f?1:2;}
    void update(List<String> next){apps=List.copyOf(next);scroll=Math.min(scroll,TerminalTimeline.maxScroll(apps.size()));}
    void captureMouse(double dx,double dy){input.capture(dx,dy);}
    void release(){input.clear();drawing.release();}
    void click(){int selected=TerminalTimeline.selectedIndex(scroll,selection);if(selected>=0&&selected<apps.size())TerminalClient.requestApp(apps.get(selected));}
    void render(GuiGraphics graphics){
        Minecraft mc=Minecraft.getInstance();long now=TerminalClient.gameTime();if(lastFrame==0)lastFrame=now;long dt=Math.max(0,now-lastFrame);
        // Classic selection and edge-scroll run before the current frame's raw pointer delta.
        selection=TerminalTimeline.selection(mouseX,mouseY);
        if(mouseY==0){mouseY=1;if(scroll>0)scroll--;}
        if(mouseY==TerminalTimeline.MAX_MY){mouseY-=1;if(scroll<TerminalTimeline.maxScroll(apps.size()))scroll++;}
        var delta=input.consume(mc.screen==null);
        mouseX=Math.max(0,Math.min(TerminalTimeline.MAX_MX,mouseX+delta.x()*.7));
        mouseY=Math.max(0,Math.min(TerminalTimeline.MAX_MY,mouseY+delta.y()*.7));
        if(Boolean.getBoolean("academy.visual.qa")&&now>=nextQaObservation){
            nextQaObservation=now+1000;
            com.mojang.logging.LogUtils.getLogger().info("QA terminal pointer virtual=({},{}), selection={}, raw=({},{}), frame={}x{}, screen={}, focus={}, grabbed={}, velocity=({},{})",
                    mouseX,mouseY,selection,mc.mouseHandler.xpos(),mc.mouseHandler.ypos(),mc.getWindow().getWidth(),mc.getWindow().getHeight(),mc.screen==null?"world":mc.screen.getClass().getSimpleName(),
                    org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_FOCUSED),mc.mouseHandler.isMouseGrabbed(),mc.mouseHandler.getXVelocity(),mc.mouseHandler.getYVelocity());
        }
        buffX=TerminalTimeline.balance(dt,buffX,mouseX);buffY=TerminalTimeline.balance(dt,buffY,mouseY);
        int selected=TerminalTimeline.selectedIndex(scroll,selection);
        if(selected!=lastSelection&&selected>=0&&selected<apps.size()&&lastSelection>=0)mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy","terminal.select")),1,.2f));
        lastSelection=selected;
        float aspect=(float)mc.getWindow().getWidth()/mc.getWindow().getHeight();
        Matrix4f projection=new Matrix4f(RenderSystem.getProjectionMatrix());VertexSorting sorting=RenderSystem.getVertexSorting();
        var model=RenderSystem.getModelViewStack();model.pushMatrix();model.identity();RenderSystem.applyModelViewMatrix();
        graphics.flush();RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float)Math.toRadians(50),aspect,1,100),VertexSorting.DISTANCE_TO_ORIGIN);
        graphics.pose().pushPose();drawing.begin(graphics);
        try{
            // The classic GL calls are post-multiplied in this exact order, including screen tilt and pointer balancing.
            Matrix4f pose=new Matrix4f().translate(.35f*aspect,1.2f,-4).translate(1,-1.8f,0)
                    .rotateZ((float)Math.toRadians(-1.6)).rotateY((float)Math.toRadians(-18-4*(buffX/605-.5)+Math.sin(now/1000d)))
                    .rotateX((float)Math.toRadians(7+4*(buffY/740-.5))).translate(-1,1.8f,0).scale(1/310f,-1/310f,1/310f);
            graphics.pose().mulPose(pose);
            drawing.texture("guis/data_terminal/back",0,0,640,785,1);drawing.texture("guis/data_terminal/logo",40,50,50,50,1);
            graphics.pose().pushPose();graphics.pose().translate(0,0,15);
            drawing.text("TERMINAL",98,74,40,0xffffff,2/3d,0);drawing.text("DATA",98,42,40,0xffffff,2/3d,0);
            drawing.text(mc.player.getName().getString(),600,41.6667,40,0xffffff,.8,2);
            graphics.pose().popPose();graphics.pose().pushPose();graphics.pose().translate(0,0,5);
            drawing.text(Component.translatable("ac.gui.terminal.appcount",apps.size()).getString()+", "+TerminalTimeline.worldTime(mc.level.getDayTime()),600,84,30,0xffffff,.6,2);
            graphics.pose().popPose();
            if(scroll>0)drawing.texture("guis/data_terminal/arrow_up",280,133,80,20,1);
            if(scroll<TerminalTimeline.maxScroll(apps.size()))drawing.texture("guis/data_terminal/arrow_down",280,725,80,20,1);
            for(int index=scroll*3;index<scroll*3+9&&index<apps.size();index++){
                int order=index-scroll*3;double x=65+180*(order%3),y=155+180*(order/3),a=TerminalTimeline.appAlpha(now-created,index);boolean isSelected=index==selected;String app=apps.get(index);
                graphics.pose().pushPose();graphics.pose().translate(0,0,isSelected?40:10);
                drawing.texture("guis/data_terminal/app_back"+(isSelected?"_highlight":""),x,y,151,151,a);
                drawing.texture("guis/apps/"+app+"/icon"+(app.equals("tutorial")?"_"+tutorialIcon:""),x+9,y+32,110,110,(isSelected?.8:.6)*a);
                drawing.text(Component.translatable("ac.app."+app+".name").getString(),x+75.5,y+148,32,0xffffff,.1+(isSelected?.72:.1)*a,1);
                graphics.pose().popPose();
            }
            double size=TerminalTimeline.cursor(now,selected>=0&&selected<apps.size());
            graphics.pose().translate(0,0,-2);RenderSystem.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,org.lwjgl.opengl.GL11.GL_ONE);
            drawing.texture("guis/data_terminal/cursor",buffX-size/2,buffY+120-size/2,size,size,.4);RenderSystem.defaultBlendFunc();
        }finally{drawing.end();graphics.pose().popPose();RenderSystem.setProjectionMatrix(projection,sorting);model.popMatrix();RenderSystem.applyModelViewMatrix();}
    }
}
