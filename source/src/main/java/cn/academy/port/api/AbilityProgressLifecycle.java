/* AcademyCraft1.0.7 AbilityData/CPData progression event boundary. GPLv3; see NOTICE. */
package cn.academy.port.api;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
/** Common server event adapter. Saved snapshots and client event transport remain separate. */
@EventBusSubscriber(modid="academy")
public final class AbilityProgressLifecycle {
    private AbilityProgressLifecycle(){}
    public static void bind(ServerPlayer player,AbilityProgress state){
        state.bindEarnedProgress(()->cn.academy.port.highpower.HighPowerGrowth.beforeMutation(player),
            receipt->cn.academy.port.highpower.HighPowerCombat.earnedExperience(player,receipt));
        state.bindProgressEvents((id,effects)->{if(player.server.isSameThread())NeoForge.EVENT_BUS.post(new SkillLearnEvent(player,state.category,id,state,effects));else effects.run();},
            (level,effects)->{if(player.server.isSameThread())NeoForge.EVENT_BUS.post(new LevelChangeEvent(player,state,effects));else effects.run();},
            award->{if(player.server.isSameThread())NeoForge.EVENT_BUS.post(new SkillExpChangedEvent(player,award.category(),award.skill(),state));},
            award->{if(player.server.isSameThread())NeoForge.EVENT_BUS.post(new SkillExpAddedEvent(player,award.category(),award.skill(),state,award.amount()));});
    }
    @SubscribeEvent public static void learned(SkillLearnEvent event){event.applyCommonEffects();}
    @SubscribeEvent public static void level(LevelChangeEvent event){event.applyCommonEffects();}
}
