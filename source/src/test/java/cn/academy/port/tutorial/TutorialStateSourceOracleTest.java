package cn.academy.port.tutorial;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import static cn.academy.port.tutorial.TutorialOracleAssertions.*;

/** Independent differential checks against original Conditions + TutorialData + LambdaLib TickScheduler. */
public final class TutorialStateSourceOracleTest {
    private static TutorialState state(ClassicTutorialOracle source,boolean grant) throws Exception {
        return new TutorialState(source.misakaID(),grant);
    }
    private static void everyTargetAndEvent() throws Exception {
        // Targets come from executing ModuleTutorial, rather than copying the port's declaration list.
        List<String> originals;
        try(var source=new ClassicTutorialOracle(false)) {
            var conditions=source.conditionTargets();
            originals=java.util.stream.IntStream.range(0,conditions.size()/3)
                .mapToObj(i->conditions.get(i*3).substring(0,conditions.get(i*3).lastIndexOf(':'))).toList();
        }
        for(String original:originals)for(var event:TutorialState.EventKind.values()) {
            String label=original+"/"+event;
            try(var source=new ClassicTutorialOracle(false)) {
                var runtime=state(source,false);
                var trace=new ArrayList<String>();
                compareState(runtime,source,label+" initial");
                equal(source.record(original,event,37,true),false,label+" original client event is ignored");
                compareState(runtime,source,label+" after ignored client event");
                equal(runtime.recordObtained("minecraft:stone",event),source.record("unknown-item",event),label+" unknown item inert");
                equal(runtime.recordObtained(modern(original),event),source.record(original,event,37,false),label+" event mutates exactly one wildcard bit");
                compareState(runtime,source,label+" event before scheduler");
                equal(runtime.recordObtained(modern(original),event),source.record(original,event,0,false),label+" repeated event is not dirty twice");
                for(int i=1;i<=3;i++)tick(runtime,source,trace,label+" first activation tick "+i);
                equal(source.activatedIds().size(),1,label+" one gated tutorial activates");
                equal(trace.size(),2,label+" one activation followed by one sync");
                check(trace.get(0).startsWith("activate:"),label+" activation precedes sync");
                equal(trace.get(1),"sync",label+" dirty batch sync follows activation");
                var other=TutorialState.EventKind.values()[(event.ordinal()+1)%3];
                equal(runtime.recordObtained(modern(original),other),source.record(original,other),label+" same target other event has separate bit");
                for(int i=4;i<=6;i++)tick(runtime,source,trace,label+" repeated tutorial tick "+i);
                equal(trace.stream().filter(t->t.startsWith("activate:")).count(),1L,label+" previously activated tutorial is never re-emitted");
                equal(trace.stream().filter("sync"::equals).count(),2L,label+" new dirty event syncs even without new tutorial");
                check(!runtime.tutorialAcquired(),label+" disabled first-guide schedule absent");
            }
        }
    }
    private static void allTargetsAndOrder() throws Exception {
        try(var source=new ClassicTutorialOracle(true)) {
            var runtime=state(source,true);
            var trace=new ArrayList<String>();
            for(String modern:ClassicTutorials.CONDITION_ITEMS) {
                equal(runtime.recordObtained(modern,TutorialState.EventKind.CRAFT),source.record(classic(modern),TutorialState.EventKind.CRAFT),"complete craft batch");
            }
            for(int tick=1;tick<=40;tick++)tick(runtime,source,trace,"complete batch tick "+tick);
            var gated=source.ids().stream().filter(id->{try{return !source.defaults().contains(id);}catch(Exception e){throw new IllegalStateException(e);}}).map(id->"activate:"+id).toList();
            var expected=new ArrayList<>(gated);expected.add("sync");expected.add("drop");
            equal(trace,expected,"one batched activation in original page order, one sync, one first guide");
            equal(source.call("dropPosition",new Class<?>[0]),List.of(7D,65D,-2D),"guide entity spawned at player's X/Y+1/Z");
            check(runtime.tutorialAcquired(),"guide acquired flag changes after original tenth local tick");
            equal(source.log().stream().filter(s->s.startsWith("local:")).count(),9L,"original server activation sent once to local client per gated page");
            equal(source.activatedIds().size(),9,"only nine gated pages stored as activated");
            for(String id:source.defaults())check(!runtime.activated(id)&&runtime.visible(id),"default source page visible without persisted activation "+id);
        }
    }
    private static void configAndCounterBoundaries() throws Exception {
        for(boolean initial:List.of(false,true)) {
            try(var source=new ClassicTutorialOracle(initial)) {
                var runtime=state(source,initial);
                var trace=new ArrayList<String>();
                source.changeConfig(!initial);
                for(int tick=1;tick<=35;tick++) {
                    tick(runtime,source,trace,"latched config "+initial+" tick "+tick);
                    equal(runtime.tutorialAcquired(),initial&&tick>=10,"first guide waits full ten local ticks and config stays latched");
                }
                equal(trace,initial?List.of("drop"):List.of(),"guide grant has no own sync and no duplicate");
            }
        }
        try(var source=new ClassicTutorialOracle(false)) {
            var runtime=state(source,false);
            var trace=new ArrayList<String>();
            // A player's schedule is local to its data part, regardless of any global tick offset.
            tick(runtime,source,trace,"idle local tick one");
            tick(runtime,source,trace,"idle local tick two");
            runtime.recordObtained("academy:phase_gen",TutorialState.EventKind.PICKUP);
            source.record("phaseGen",TutorialState.EventKind.PICKUP);
            tick(runtime,source,trace,"event one tick before existing local activation schedule");
            equal(trace,List.of("activate:phase_generator","sync"),"event does not restart local activation countdown");
        }
    }
    private static void transientPersistence() throws Exception {
        try(var source=new ClassicTutorialOracle(true)) {
            var runtime=state(source,true);
            var trace=new ArrayList<String>();
            runtime.recordObtained("academy:crystal_ore",TutorialState.EventKind.CRAFT);
            source.record("oreImagCrystal",TutorialState.EventKind.CRAFT);
            tick(runtime,source,trace,"pre-save first tick");
            long[] saved=runtime.conditionBits();
            source.restart();
            runtime.restore(saved,runtime.activatedIds(),runtime.tutorialAcquired(),false);
            compareState(runtime,source,"restore pending source bits before activation");
            check(runtime.visible("ores")&&!runtime.activated("ores"),"source persisted condition visibility may precede stored activation");
            for(int tick=1;tick<=10;tick++)tick(runtime,source,trace,"pending restore local tick "+tick);
            equal(trace,List.of("drop"),"dirty is transient; restore neither recreates pending activation nor immediate guide schedule");
            runtime.recordObtained("academy:crystal_ore",TutorialState.EventKind.PICKUP);
            source.record("oreImagCrystal",TutorialState.EventKind.PICKUP);
            tick(runtime,source,trace,"post-restore new event tick eleven");
            tick(runtime,source,trace,"post-restore new event tick twelve");
            equal(trace,List.of("drop","activate:ores","sync"),"later new event dirties stored source bits and discovers tutorial");
            source.restart();
            runtime.restore(runtime.conditionBits(),runtime.activatedIds(),runtime.tutorialAcquired(),false);
            for(int tick=1;tick<=30;tick++)tick(runtime,source,trace,"acquired/activated restart tick "+tick);
            equal(trace,List.of("drop","activate:ores","sync"),"persistent acquisition and activation never duplicate after restart");
        }
        try(var source=new ClassicTutorialOracle(false)) {
            var runtime=state(source,false);
            var bits=new BitSet();bits.set(0);bits.set(62);bits.set(63);bits.set(127);bits.set(4096);
            var ids=List.of("ores","welcome","future_extension","terminal");
            source.restore(bits.toLongArray(),ids,true);
            runtime.restore(bits.toLongArray(),ids,true,true);
            compareState(runtime,source,"raw surplus bits/default/unknown IDs preserved from source fields");
            equal(runtime.firstOpened(),true,"modern guide-open advancement flag is persisted extension");
            var outward=runtime.conditionBits();outward[0]=0;
            equal(runtime.conditionBits(),source.bits(),"condition snapshot is defensive");
            boolean immutable=false;try{runtime.activatedIds().add("mutated");}catch(UnsupportedOperationException expected){immutable=true;}
            check(immutable,"activated set snapshot is defensive");
            var trace=new ArrayList<String>();
            for(int tick=1;tick<=15;tick++)tick(runtime,source,trace,"raw restored state tick "+tick);
            equal(trace,List.of(),"restored raw fields have no dirty replay");
        }
    }
    private static void deterministicLongTrace() throws Exception {
        try(var source=new ClassicTutorialOracle(true)) {
            var runtime=state(source,true);
            var trace=new ArrayList<String>();
            var random=new Random(1072026L);
            for(int tick=1;tick<=400;tick++) {
                if(random.nextBoolean()) {
                    String item=ClassicTutorials.CONDITION_ITEMS.get(random.nextInt(ClassicTutorials.CONDITION_ITEMS.size()));
                    var event=TutorialState.EventKind.values()[random.nextInt(3)];
                    equal(runtime.recordObtained(item,event),source.record(classic(item),event),"deterministic repeated event trace "+tick);
                }
                if(tick==57||tick==105||tick==263) {
                    source.restart();
                    runtime.restore(runtime.conditionBits(),runtime.activatedIds(),runtime.tutorialAcquired(),runtime.firstOpened());
                }
                tick(runtime,source,trace,"deterministic tick "+tick);
            }
            equal(trace.stream().filter("drop"::equals).count(),1L,"four-hundred-tick trace grants one lifetime guide");
        }
    }
    public static void main(String[] args) throws Exception {
        TutorialSourceFixtures.verifyAll();
        everyTargetAndEvent();
        allTargetsAndOrder();
        configAndCounterBoundaries();
        transientPersistence();
        deterministicLongTrace();
        check(new TutorialState(1000,false).misakaID()==1000&&new TutorialState(18999,false).misakaID()==18999,"source RNG reachable endpoints");
        for(int invalid:List.of(999,19000)) {
            boolean rejected=false;try{new TutorialState(invalid,false);}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected,"modern constructor guards outside source-generated ID range");
        }
        System.out.println("PASS "+checks+" original tutorial state/condition/scheduler differential checks");
    }
}
