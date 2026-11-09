package cn.academy.port.core;
import cn.academy.port.api.*;
import net.neoforged.bus.api.*;
import java.util.*;
public final class SharedSkillConsumptionEventBusTest {
    public static void main(String[] arguments){
        if(ICancellableEvent.class.isAssignableFrom(SkillPerformEvent.class)||ICancellableEvent.class.isAssignableFrom(AbilityOverloadEvent.class))throw new AssertionError("classic events cannot cancel");
        var bus=BusBuilder.builder().build();var order=new ArrayList<String>();
        bus.addListener(EventPriority.LOWEST,SkillPerformEvent.class,e->{order.add("lowest");if(e.cp!=15||e.overload!=8)throw new AssertionError("ordered mutation lost");e.cp=2;});
        bus.addListener(EventPriority.HIGHEST,SkillPerformEvent.class,e->{order.add("highest");e.cp+=5;e.overload*=2;});
        var event=new SkillPerformEvent(null,"arc_gen",4,10,false,false);if(bus.post(event)!=event||event.cp!=2||event.overload!=8||!order.equals(List.of("highest","lowest")))throw new AssertionError("synchronous bus order/identity/float cost");
        var state=new AbilityProgress();state.selectCategory("electromaster");state.setLevel(5);state.learn("arc_gen");state.bindConsumption(()->SkillConsumption.Config.DEFAULT,e->{var nativeEvent=new SkillPerformEvent(null,e.skill,e.overload,e.cp,e.force,e.creative);bus.post(nativeEvent);e.cp=nativeEvent.cp;e.overload=nativeEvent.overload;},()->{},()->true);
        if(!state.consumeSkill("arc_gen",10,4,false)||state.cp!=7998||state.overload!=8)throw new AssertionError("actual event-bus mutation did not reach ledger");
        System.out.println("PASS 8 actual NeoForge bus noncancelable event/order/identity/mutation assertions; no Minecraft run");
    }
}
