/* AcademyCraft1.0.7 achievement page/icon presentation. GPLv3; see NOTICE. */
package cn.academy.port.client.achievements;
import cn.academy.port.achievements.ClassicAchievementCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ClassicAchievementClient {
    private static Set<String> earned=Set.of();
    private static long generation;
    private ClassicAchievementClient(){}
    public static boolean earned(String id){return earned.contains(id);}
    static long generation(){return generation;}
    public static void clear(){earned=Set.of();generation++;}
    public static void receive(CompoundTag tag){
        var data=tag.getCompound("earned");var next=new HashSet<String>();
        for(String id:data.getAllKeys())if(data.getBoolean(id)&&ClassicAchievementCatalog.get(id)!=null)next.add(id);
        earned=Set.copyOf(next);var award=ClassicAchievementCatalog.get(tag.getString("award"));
        if(award!=null&&earned(award.id()))Minecraft.getInstance().getToasts().addToast(new ClassicAchievementToast(award,generation));
    }
    @SubscribeEvent public static void init(ScreenEvent.Init.Post e){
        if(e.getScreen() instanceof AdvancementsScreen){var screen=e.getScreen();
            e.addListener(Button.builder(Component.literal("AcademyCraft"),b->Minecraft.getInstance().setScreen(new ClassicAchievementScreen(screen)))
                .bounds(Math.max(0,screen.width-112),4,110,20).build());
        }
    }
}
