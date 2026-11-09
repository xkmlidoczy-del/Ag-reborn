/* AcademyCraft ItemAchievement/RenderItemAchievement inventory-only icon adaptation. GPLv3. */
package cn.academy.port.client.achievements;
import cn.academy.port.achievements.ClassicAchievementCatalog.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
public final class ClassicAchievementVisual {
    private ClassicAchievementVisual(){}
    /** Display paths are trusted catalog entries, never arbitrary metadata supplied by a client. */
    public static void icon(GuiGraphics g,Entry e,int x,int y){
        if(e.icon().startsWith("texture:")){
            g.renderItem(cn.academy.port.display.ClassicDisplayItems.forIcon(e.icon()),x,y);
        }else if(e.icon().startsWith("item:")){
            var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy",e.icon().substring(5)));
            g.renderItem(new ItemStack(item),x,y);
        }
    }
    /** Source normal achievement frame is 22x22 around a 16x16 inventory icon. */
    public static void frame(GuiGraphics g,int x,int y,boolean earned,boolean available){
        int edge=earned?0xffa2a2a2:available?0xff737373:0xff414141;
        g.fill(x,y,x+22,y+22,0xff101010);g.fill(x+1,y+1,x+21,y+21,edge);
        g.fill(x+2,y+2,x+20,y+20,0xff252525);g.fill(x+2,y+20,x+21,y+21,0xff060606);
    }
}
