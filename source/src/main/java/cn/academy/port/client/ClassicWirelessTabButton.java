/* AcademyCraft1.0.7 TechUI pageselect source icon; GPLv3. See NOTICE. */
package cn.academy.port.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Native keyboard/narration-capable entry button with original TechUI icon and scale. */
public final class ClassicWirelessTabButton extends Button {
    public ClassicWirelessTabButton(int x,int y,Runnable open){
        super(x,y,18,18,Component.translatable("screen.academy.wireless.title"),button->open.run(),DEFAULT_NARRATION);
        setTooltip(Tooltip.create(getMessage()));
    }
    @Override public void renderWidget(GuiGraphics graphics,int mx,int my,float partial){
        ClassicHudCanvas canvas=new ClassicHudCanvas(graphics);canvas.begin();
        try{canvas.rect(ClassicHudCanvas.texture("guis/icons/icon_wireless"),getX(),getY(),16.8,16.8,new ClassicHudTimeline.Rgba(.8,.8,.8,isHoveredOrFocused()?1:.8));}
        finally{canvas.end();}
    }
}
