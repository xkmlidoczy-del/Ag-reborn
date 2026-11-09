/* Original achievement titles and safe original bitmap icons, modern Toast transport. GPLv3. */
package cn.academy.port.client.achievements;
import cn.academy.port.achievements.ClassicAchievementCatalog.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.network.chat.Component;
public final class ClassicAchievementToast implements Toast {
    private final Entry entry;private final long generation;
    public ClassicAchievementToast(Entry entry,long generation){this.entry=entry;this.generation=generation;}
    @Override public Object getToken(){return entry.id();}
    @Override public Visibility render(GuiGraphics g,ToastComponent toasts,long elapsed){
        if(generation!=ClassicAchievementClient.generation())return Visibility.HIDE;
        g.fill(0,0,160,32,0xff101010);g.fill(1,1,159,31,0xffa0a0a0);g.fill(2,2,158,30,0xff252525);
        ClassicAchievementVisual.icon(g,entry,8,8);var font=Minecraft.getInstance().font;
        g.drawString(font,Component.translatable("ac.achievement.get"),30,6,0xffffff00,false);
        var name=Component.translatable(entry.title());g.drawString(font,font.plainSubstrByWidth(name.getString(),125),30,17,0xffffffff,false);
        return elapsed<3000*toasts.getNotificationDisplayTimeMultiplier()?Visibility.SHOW:Visibility.HIDE;
    }
}
