package cn.academy.port.tutorial;

import java.util.ArrayList;
import java.util.List;
import static cn.academy.port.tutorial.TutorialOracleAssertions.*;

/** Executable unchanged ModuleTutorial/ACTutorial declarations and pinned source metadata. */
public final class TutorialMetadataSourceOracleTest {
    public static void main(String[] args) throws Exception {
        equal(TutorialSourceFixtures.verifyAll(),23,"all byte-exact canonical sources/notices");
        TutorialOracleBoundaryFixtures.verifyAll();
        try(var source=new ClassicTutorialOracle(false)) {
            equal(source.ids(),ClassicTutorials.pages().stream().map(ClassicTutorials.Page::id).toList(),"classic page registration order");
            equal(source.ids(),List.of("welcome","ores","phase_generator","solar_generator","wind_generator","metal_former","imag_fusor","terminal","ability_developer","ability_basis","energy_bridge","misc","develop_ability","wireless_network"),"fourteen canonical tutorials");
            equal(source.defaults(),List.of("welcome","ability_basis","misc","develop_ability","wireless_network"),"five source default-installed pages");
            equal(source.defaults(),ClassicTutorials.pages().stream().filter(ClassicTutorials.Page::defaultInstalled).map(ClassicTutorials.Page::id).toList(),"runtime defaults from original execution");
            var conditions=source.conditionTargets();
            equal(conditions.size(),63,"twenty-one actual targets times three event kinds");
            equal(TutorialState.CONDITION_COUNT,conditions.size(),"runtime condition size from original execution");
            var targetItems=new ArrayList<String>();
            for(int index=0;index<conditions.size();index++) {
                String descriptor=conditions.get(index);
                String original=descriptor.substring(0,descriptor.lastIndexOf(':'));
                var event=TutorialState.EventKind.values()[index%3];
                equal(descriptor.substring(descriptor.lastIndexOf(':')+1),event.name().toLowerCase(java.util.Locale.ROOT),"source itemObtained allocates craft,pickup,smelt consecutively");
                equal(TutorialState.conditionIndex(modern(original),event),index,"runtime event bit index from actual original Conditions creation");
                if(index%3==0)targetItems.add(modern(original));
            }
            equal(ClassicTutorials.CONDITION_ITEMS,targetItems,"stable semantic IDs from original module items");
            var previews=source.previews();
            for(var page:ClassicTutorials.pages()) {
                var expected=previews.get(page.id()).stream().map(TutorialOracleAssertions::preview).toList();
                equal(page.previews().stream().map(p->p.kind()+":"+p.target()).toList(),expected,"actual original preview ordering "+page.id());
                check(page.previews().stream().allMatch(p->p.metadata()==0),"all source preview recipe metadata defaults zero");
            }
            equal(source.call("checkMetaConditions",new Class<?>[0]),List.of(true,false,false,true,false,false,true,false,true),"unchanged original wildcard, meta matching, event separation and client exclusion");
            equal(source.call("schedulerProbe",new Class<?>[0]),List.of(3,6,9,-10,12,15,18,-20,21,24,27,30,-30),"unchanged LambdaLib scheduler countdown, server-only and creation order");
            source.clearLog();
            equal(source.call("rightClick",new Class<?>[]{boolean.class},false),true,"server right-click returns original stack");
            equal(source.log(),List.of(),"classic tutorial screen does not open on server");
            equal(source.call("rightClick",new Class<?>[]{boolean.class},true),true,"client right-click returns original stack");
            equal(source.log(),List.of("screen:GuiTutorial"),"classic client guide item screen path");
        }
        // Annotation registration has no prescribed app order. All six explicit fixtures must agree on page semantics.
        for(String order:List.of("skill_tree,freq_transmitter,media_player,settings","skill_tree,media_player,freq_transmitter,settings","freq_transmitter,skill_tree,media_player,settings","freq_transmitter,media_player,skill_tree,settings","media_player,skill_tree,freq_transmitter,settings","media_player,freq_transmitter,skill_tree,settings")) {
            try(var source=new ClassicTutorialOracle(false,order)) {
                equal(source.conditionTargets().size(),63,"app-order fixture keeps exact allocation count");
                equal(source.defaults(),List.of("welcome","ability_basis","misc","develop_ability","wireless_network"),"preinstalled settings/tutorial never add item conditions");
                for(String app:List.of("app_skill_tree","app_freq_transmitter","app_media_player")) {
                    check(source.record(app,TutorialState.EventKind.PICKUP),"each source app installer changes own condition bit");
                    check(source.visible("terminal"),"each source app installer OR-unlocks terminal immediately");
                }
            }
        }
        String state=TutorialSourceFixtures.source("academy/TutorialData.java");
        check(state.contains("@SerializeIncluded\n    private BitSet savedConditions")&&state.contains("@SerializeIncluded\n    private HashSet<String> activatedTuts"),"canonical persisted bitset/set witness");
        check(state.contains("private boolean tutorialAcquired = false")&&state.contains("private int misakaID = -1"),"canonical guide/id persisted fields");
        check(state.contains("if (canAcquireTutorial())")&&state.indexOf("if (canAcquireTutorial())")<state.indexOf("scheduler.every(10)"),"source config is latched while constructing grant schedule");
        check(TutorialSourceFixtures.source("lambdalib/RandUtils.java").contains("RNG.nextInt(to - from)"),"exclusive Misaka upper bound witness");
        check(TutorialSourceFixtures.source("lambdalib/RegistrationManager.java").contains("unloadedClass = new HashSet<>()"),"app registration order explicitly not claimed invariant");
        check(TutorialSourceFixtures.source("academy/ItemApp.java").contains("if(!app.isPreInstalled())"),"preinstalled apps have no installer condition");
        check(TutorialSourceFixtures.source("academy/MediaApp.scala").contains("extends App(\"media_player\")"),"excluded media has source installer identity only");
        System.out.println("PASS "+checks+" immutable tutorial metadata / original conditions / original scheduler checks");
    }
}
