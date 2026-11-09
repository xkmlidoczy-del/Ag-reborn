package cn.academy.port.tutorial;

import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;

/** Actual modern NBT and NeoForge event-bus contracts; no Minecraft bootstrap. */
public final class TutorialStorageRegressionTest {
    private static int checks;
    private static void check(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    public static void main(String[] arguments) {
        for(int i=0;i<100;i++) {
            var s=new TutorialState(1000+i,true);
            for(int n=0;n<ClassicTutorials.CONDITION_ITEMS.size();n++)if((n+i)%3==0)s.recordObtained(ClassicTutorials.CONDITION_ITEMS.get(n),TutorialState.EventKind.values()[(n+i)%3]);
            for(int tick=0;tick<10;tick++)s.tick(id->{},()->{},()->{});
            s.markOpened();var tag=TutorialStorage.encode(s);var roundtrip=TutorialStorage.decode(tag);
            check(Arrays.equals(s.conditionBits(),roundtrip.conditionBits()),"actual NBT condition bits roundtrip");
            check(s.activatedIds().equals(roundtrip.activatedIds()),"actual NBT activation set roundtrip");
            check(roundtrip.tutorialAcquired()&&roundtrip.firstOpened()&&roundtrip.misakaID()==1000+i,"actual NBT first-guide/open/Misaka survives");
            check(!roundtrip.dirty(),"source dirty flag is transient");
            check(!tag.contains("dirty")&&!tag.contains("activationCounter")&&!tag.contains("grantCounter"),"source local schedules not persisted");
            check(tag.getInt("schema")==1,"modern state schema");
            check(TutorialStorage.encode(roundtrip).equals(tag),"deterministic NBT encode/decode");
        }
        var surplus=new TutorialState(18999,false);surplus.restore(new long[]{Long.MIN_VALUE,-1,1},List.of("welcome","other_mod_tutorial","solar_generator"),true,true);
        var restored=TutorialStorage.decode(TutorialStorage.encode(surplus));check(Arrays.equals(surplus.conditionBits(),restored.conditionBits()),"source retains unknown high bits");
        check(restored.activatedIds().equals(surplus.activatedIds()),"source retains unknown/default activation IDs");
        var empty=TutorialStorage.decode(new CompoundTag());check(empty.misakaID()==1000&&!empty.tutorialAcquired()&&!empty.firstOpened(),"client empty snapshot deterministic default");
        check(!ICancellableEvent.class.isAssignableFrom(TutorialActivatedEvent.class),"source notification cannot cancel activation");
        var bus=BusBuilder.builder().build();var order=new java.util.ArrayList<String>();
        bus.addListener(EventPriority.HIGHEST,TutorialActivatedEvent.class,e->{check(e.tutorial.id().equals("solar_generator"),"actual event page identity");order.add("first");});
        bus.addListener(EventPriority.LOWEST,TutorialActivatedEvent.class,e->order.add("last"));
        var event=new TutorialActivatedEvent(null,ClassicTutorials.page("solar_generator"));
        check(bus.post(event)==event&&order.equals(List.of("first","last")),"actual synchronous NeoForge notification order and identity");
        System.out.println("PASS "+checks+" tutorial actual NBT/event-bus assertions; no game startup");
    }
}
