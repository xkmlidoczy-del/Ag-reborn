/* Classic per-category generic skill calculation listeners. GPLv3; see NOTICE. */
package cn.academy.port;
import cn.academy.port.api.AbilityCalculationEvent;
import cn.academy.port.core.AbilityCalculation;
import cn.academy.port.core.ClassicPassiveSkills;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** NORMAL priority keeps generic course effects between higher and lower priority extension listeners. */
@EventBusSubscriber(modid="academy")
public final class PassiveSkillEvents {
    private PassiveSkillEvents() {}
    @SubscribeEvent public static void maxCP(AbilityCalculationEvent.MaxCP event){apply(event,AbilityCalculation.Kind.MAX_CP);}
    @SubscribeEvent public static void maxOverload(AbilityCalculationEvent.MaxOverload event){apply(event,AbilityCalculation.Kind.MAX_OVERLOAD);}
    @SubscribeEvent public static void recovery(AbilityCalculationEvent.CPRecoverSpeed event){apply(event,AbilityCalculation.Kind.CP_RECOVERY);}
    private static void apply(AbilityCalculationEvent event,AbilityCalculation.Kind kind){
        var state=event.player==null?null:AbilityStorage.cached(event.player);
        if(state!=null)event.value=ClassicPassiveSkills.calculate(state,kind,event.value);
    }
}
