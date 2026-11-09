/* AcademyCraft1.0.7 CPData/PresetData/CooldownData category handlers, GPLv3; see NOTICE. */
package cn.academy.port.api;

import cn.academy.port.core.AbilityProgress;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;

/** Common server adapter only; category acquisition deliberately emits no LevelChangeEvent. */
@EventBusSubscriber(modid="academy")
public final class AbilityCategoryLifecycle {
    private AbilityCategoryLifecycle(){}
    public static void bind(ServerPlayer player,AbilityProgress state){
        state.bindCategoryChanges(effects->{
            if(player.server.isSameThread())NeoForge.EVENT_BUS.post(new CategoryChangeEvent(player,state,effects));
            else effects.run();
        });
    }
    /** Source NORMAL order: cooldown clear, CP recalc/refill retaining training, preset clear. */
    @SubscribeEvent public static void changed(CategoryChangeEvent event){cn.academy.port.AcademyGameplay.disposeAbilityContexts((ServerPlayer)event.getEntity());event.applyCommonEffects();}
}
