package cn.academy.port.passive;
import cn.academy.port.core.*;
import cn.academy.port.api.AbilityCalculationEvent;
import net.neoforged.bus.api.*;
import java.util.*;
public final class PassiveSkillsEventBusTest {
 static int checks;static void yes(boolean v,String l){checks++;if(!v)throw new AssertionError(l);}
 public static void main(String[] args){
  for(var type:List.of(AbilityCalculationEvent.MaxCP.class,AbilityCalculationEvent.MaxOverload.class,AbilityCalculationEvent.CPRecoverSpeed.class,AbilityCalculationEvent.OverloadRecoverSpeed.class))yes(!ICancellableEvent.class.isAssignableFrom(type),"source calculations cannot cancel");
  var bus=BusBuilder.builder().build();var state=new AbilityProgress();state.selectCategory("meltdowner");state.setLevel(5);state.learn("brain_course");state.learn("brain_course_advanced");state.learn("mind_course");var order=new ArrayList<String>();
  bus.addListener(EventPriority.HIGHEST,AbilityCalculationEvent.MaxCP.class,e->{order.add("highest");e.value*=2;});
  bus.addListener(EventPriority.NORMAL,AbilityCalculationEvent.MaxCP.class,e->{order.add("generic");e.value=ClassicPassiveSkills.calculate(state,AbilityCalculation.Kind.MAX_CP,e.value);});
  bus.addListener(EventPriority.LOWEST,AbilityCalculationEvent.MaxCP.class,e->{order.add("lowest");e.value+=17;});
  bus.addListener(AbilityCalculationEvent.MaxOverload.class,e->e.value=ClassicPassiveSkills.calculate(state,AbilityCalculation.Kind.MAX_OVERLOAD,e.value));
  bus.addListener(AbilityCalculationEvent.CPRecoverSpeed.class,e->e.value=ClassicPassiveSkills.calculate(state,AbilityCalculation.Kind.CP_RECOVERY,e.value));
  bus.addListener(AbilityCalculationEvent.OverloadRecoverSpeed.class,e->e.value=.5f);
  state.bindCalculations(request->{AbilityCalculationEvent event=switch(request.kind){case MAX_CP->new AbilityCalculationEvent.MaxCP(null,request.value);case MAX_OVERLOAD->new AbilityCalculationEvent.MaxOverload(null,request.value);case CP_RECOVERY->new AbilityCalculationEvent.CPRecoverSpeed(null,request.value);case OVERLOAD_RECOVERY->new AbilityCalculationEvent.OverloadRecoverSpeed(null,request.value);};bus.post(event);request.value=event.value;});
  yes(state.baseCp()==10500&&state.baseOverload()==600&&order.isEmpty(),"calculation bus binding retains previous source cached maxima");state.learn("rad_intensify");
  yes(state.baseCp()==18517&&state.baseOverload()==600&&order.equals(List.of("highest","generic","lowest")),"actual NeoForge ordered calculation mutation reaches cached raw capacity");order.clear();for(int i=0;i<20;i++)state.maxCp();yes(order.isEmpty(),"max getter never reposts calculation event");
  state.extraCp=40;yes(state.maxCp()==18557&&order.isEmpty(),"source training adds capacity without recalculation events");
  state.learn("electron_bomb");yes(order.equals(List.of("highest","generic","lowest"))&&state.cp==18557,"source learnedSkill performs one recalc/refill");order.clear();float expected=Math.max(0,Math.min(1,(float)18557/(float)18517));yes(state.exp("rad_intensify")==expected&&order.equals(List.of("highest","generic","lowest")),"radiation getInitCP5 posts MaxCP and includes generic listener exactly once");
  state.cp=0;state.cpDelay=0;state.overload=20;state.overloadDelay=0;double before=state.overload;state.tick();yes(state.cp==ClassicPassiveSkills.cpRecovery(0,18517,1,1.2f),"mind multiplier mutates live native calculation");float sourceRaw=1f*Math.max(.002f*600f,.007f*600f*(1f+((float)before/600f/2f)*(.5f-1f)));float expectedOverload=(float)before-.5f*sourceRaw;yes(Double.doubleToRawLongBits(state.overload)==Double.doubleToRawLongBits((double)expectedOverload),"overload recovery hook mutates source float live ledger");
  state.bindCalculations(e->e.value=Float.NaN);state.learn("scatter_bomb");yes(state.maxCp()==40&&state.maxOverload()==0,"modern finite guard bounds malformed extension mutations");
  System.out.println("PASS "+checks+" actual NeoForge calculation order, cached capacities, course events and float recovery checks; no Minecraft run");
 }
}
