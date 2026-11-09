package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.preset.PresetSkills;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** NEW452 learning/identity regression against the authentic .80 registry snapshot. */
public final class AutonomousVectorCircleLearningRegressionTest {
    private static final String ID="autonomous_vector_circle", CATEGORY="vecmanip", PARENT="multi_axis_reflection";
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    private static SkillCatalog.Skill expected(){return new SkillCatalog.Skill(CATEGORY,ID,5,true,List.of(new SkillCatalog.Requirement(PARENT,0)),0);}
    public static void main(String[] args) throws Exception {
        CurrentHighPowerInventory.verify();
        SkillCatalog.resetConfiguration();
        try { registration(); learning(); wire(); savedIdentity(); }
        finally { SkillCatalog.resetConfiguration(); }
        System.out.println("PASS AutonomousVectorCircleLearningRegressionTest: "+checks+" checks");
    }
    private static List<SkillCatalog.Skill> predecessor() throws Exception {
        Path root=Path.of("src/test/resources/highpower-learning/autonomous-vector-circle-v20");
        byte[] bytes=Files.readAllBytes(root.resolve("baseline-v80/HighPowerSkills.java"));
        var manifest=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("contract-v20.json"))).getAsJsonObject();
        check(manifest.get("baseline_revision").getAsString().equals("c334bbc6583d991bcd4bfa28de4312213d156089"),"real .80 git input");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        check(hash.equals("3b6748cebdd095fd295d18fe0f9f4c742f3dfc41ae345cd11e8bc11221731608")
                &&hash.equals(manifest.getAsJsonObject("production").getAsJsonObject("src/main/java/cn/academy/port/highpower/HighPowerSkills.java").get("input_sha256").getAsString()),"complete authentic .80 source pin");
        String source=new String(bytes,StandardCharsets.UTF_8);
        var constants=new HashMap<String,String>();
        var strings=Pattern.compile("\\b([A-Z_]+) = \"([^\"]+)\"").matcher(source);
        while(strings.find()){
            constants.put(strings.group(1),strings.group(2));
            check(HighPowerSkills.class.getField(strings.group(1)).get(null).equals(strings.group(2)),"old saved ID "+strings.group(1));
        }
        var proposals=Pattern.compile("\\b([A-Z_]+_PROPOSAL) = ([0-9]+)").matcher(source);
        while(proposals.find())check(HighPowerSkills.class.getField(proposals.group(1)).getInt(null)==Integer.parseInt(proposals.group(2)),"old proposal "+proposals.group(1));
        var record=Pattern.compile("new SkillCatalog\\.Skill\\(([^,\\n]+), ([^,\\n]+), ([0-9]+), (true|false),\\s*(List\\.of\\((?:[^()]|\\([^()]*\\))*\\)), ([0-9]+)\\)");
        var matches=record.matcher(source);var old=new ArrayList<SkillCatalog.Skill>();var oldBytes=new ArrayList<String>();
        while(matches.find()){
            String category=switch(matches.group(1)){
                case "CATEGORY" -> constants.get("CATEGORY");
                case "HighPowerCategories.MELTDOWNER" -> "meltdowner";
                case "HighPowerCategories.TELEPORTER" -> "teleporter";
                case "HighPowerCategories.VECMANIP" -> "vecmanip";
                default -> throw new AssertionError("Unknown .80 category");
            };
            var requirements=new ArrayList<SkillCatalog.Requirement>();
            var requirement=Pattern.compile("new SkillCatalog.Requirement\\(([^,]+), ([^)]+)\\)").matcher(matches.group(5));
            while(requirement.find())requirements.add(new SkillCatalog.Requirement(constants.get(requirement.group(1)),Double.parseDouble(requirement.group(2))));
            old.add(new SkillCatalog.Skill(category,constants.get(matches.group(2)),Integer.parseInt(matches.group(3)),Boolean.parseBoolean(matches.group(4)),List.copyOf(requirements),Integer.parseInt(matches.group(6))));
            oldBytes.add(matches.group());
        }
        var currentBytes=new ArrayList<String>();matches=record.matcher(Files.readString(Path.of("src/main/java/cn/academy/port/highpower/HighPowerSkills.java")));
        while(matches.find())currentBytes.add(matches.group());
        check(old.size()==52&&currentBytes.size()==61&&currentBytes.subList(0,52).equals(oldBytes),"all 52 old source records and ordinals stay exact");
        return List.copyOf(old);
    }
    private static boolean exactAppend(List<SkillCatalog.Skill> current,List<SkillCatalog.Skill> old){
        return old.size()==52&&current.size()==61&&CurrentHighPowerInventory.matches(current)&&current.subList(0,52).equals(old)&&current.get(52).equals(expected());
    }
    private static void registration() throws Exception {
        var old=predecessor();check(exactAppend(HighPowerSkills.ALL,old),"only exact NEW452 appends at ordinal52");
        check(HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE.equals(ID)&&HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE_PROPOSAL==452,"canonical identity and proposal");
        var ids=new HashSet<String>();old.forEach(skill->ids.add(skill.id()));ids.add(ID);
        CurrentHighPowerInventory.expected().forEach(entry -> ids.add(entry.id()));
        check(ids.size()==61,"exact 61 high-power base IDs"); ids.addAll(cn.academy.port.canon.CanonSkills.IDS);
        check(HighPowerSkills.IDS.equals(ids),"exact high-power and canon ID union");
        check(SkillCatalog.ALL.size()==50&&SkillCatalog.REGISTERED.size()==(111 + cn.academy.port.canon.CanonSkills.ALL.size())&&SkillCatalog.REGISTERED.subList(0,50).equals(SkillCatalog.ALL)
                &&SkillCatalog.REGISTERED.subList(50,102).equals(old)&&SkillCatalog.REGISTERED.get(102).equals(expected()),"derived catalog appends while every classic and old high-power ordinal survives");
        var presets=new HashSet<>(PresetSkills.IMPLEMENTED);presets.addAll(ids);CurrentHighPowerInventory.expected().forEach(entry -> presets.add(entry.id()));
        check(PresetSkills.IMPLEMENTED.size()==35&&presets.size()==96+cn.academy.port.canon.CanonSkills.IDS.size()&&PresetSkills.REGISTERED.equals(presets),"presets gain exactly the canonical new ID");
        check(SkillCatalog.CONFIG_KEYS.size()==41&&SkillCatalog.REGISTERED_CONFIG_KEYS.size()==(102 + cn.academy.port.canon.CanonSkills.ALL.size())
                &&SkillCatalog.REGISTERED_CONFIG_KEYS.contains(ClassicSkillConfiguration.key(CATEGORY,ID)),"configuration adds one canonical key");
        for(String category:List.of("electromaster","meltdowner","teleporter","vecmanip"))for(int level=1;level<=5;level++){
            var state=new AbilityProgress();state.changeCategoryClassic(category);state.setLevel(level);int currentLevel=level;
            long count=SkillCatalog.ALL.stream().filter(skill->skill.category().equals(category)&&skill.level()==currentLevel&&skill.controllable()).count();
            check(SkillCatalog.levelSkillCount(state)==count,"classic XP denominator "+category+" LV"+level);
        }
        for(String category:cn.academy.port.canon.CanonSkills.CATEGORIES)for(int level=1;level<=5;level++){
            var state=new AbilityProgress();state.changeCategoryClassic(category);state.setLevel(level);
            check(SkillCatalog.levelSkillCount(state)==1,"canon progression denominator "+category+" LV"+level);
        }
        for(int i=0;i<old.size();i++){
            var changed=new ArrayList<>(HighPowerSkills.ALL);var original=old.get(i);
            changed.set(i,new SkillCatalog.Skill(original.category(),original.id(),original.level()+1,original.controllable(),original.requirements(),original.anyLearnedSkillLevel()));
            check(!exactAppend(changed,old),"negative old-level "+i);
            changed=new ArrayList<>(HighPowerSkills.ALL);Collections.swap(changed,i,(i+1)%old.size());
            check(!exactAppend(changed,old),"negative old-ordinal "+i);
        }
        check(!exactAppend(old,old),"missing append fails");
        var changed=new ArrayList<>(HighPowerSkills.ALL);changed.set(52,new SkillCatalog.Skill(CATEGORY,ID,5,true,List.of(new SkillCatalog.Requirement("wind_pressure_lift",0)),0));
        check(!exactAppend(changed,old),"new parent cannot become443");
    }
    private static AbilityProgress prepared(){var state=new AbilityProgress();state.changeCategoryClassic(CATEGORY);state.setLevel(5);state.learn(PARENT);return state;}
    private static final class Battery implements DevelopmentProcess.Developer {
        double energy;Battery(double value){energy=value;}
        public DeveloperType type(){return DeveloperType.ADVANCED;}
        public double energy(){return energy;}public double maxEnergy(){return type().energy;}
        public boolean tryPullEnergy(double amount){if(energy<amount)return false;energy-=amount;return true;}
    }
    private static void learning(){
        var state=prepared();var skill=SkillCatalog.find(CATEGORY,ID).orElseThrow();state.unlearn(PARENT);
        check(!SkillCatalog.canLearn(state,skill)&&!PresetSkills.edit(state,0,0,ID),"missing442/unlearned452 fail closed");
        state.learn("wind_pressure_lift");check(!DevelopmentActions.canLearn(state,DeveloperType.ADVANCED,skill),"443 cannot substitute for442");
        state.learn(PARENT);check(state.exp(PARENT)==0&&DevelopmentActions.canLearn(state,DeveloperType.ADVANCED,skill),"zero-mastery442 suffices");
        check(!DevelopmentActions.canLearn(state,DeveloperType.NORMAL,skill)&&!DevelopmentActions.canLearn(state,DeveloperType.PORTABLE,skill),"native LV5 requires Advanced");
        state.setLevel(4);check(!DevelopmentActions.canLearn(state,DeveloperType.ADVANCED,skill),"Advanced cannot bypass LV5");state.setLevel(5);
        state.levelExperience=.25;int denominator=SkillCatalog.levelSkillCount(state);
        var action=DevelopmentActions.skill(state,skill);check(action.stimulations()==15&&action.id().equals("skill:"+ID)
                &&DeveloperType.ADVANCED.ticksPerStimulation()==16&&DeveloperType.ADVANCED.actualConsumption(15)==9600,"native 15 stimulations, 240 ticks, 9600 IF");
        var process=new DevelopmentProcess();var shortBattery=new Battery(9599);process.start(shortBattery,action);
        for(int tick=0;tick<240;tick++)process.tick();
        check(!state.learned(ID)&&shortBattery.energy==39&&process.state()==DevelopmentProcess.State.FAILED,"final atomic shortage grants no learned bit");
        var battery=new Battery(9600);process.start(battery,action);for(int tick=0;tick<239;tick++)process.tick();
        check(!state.learned(ID)&&battery.energy==40,"no early learning before final paid tick");process.tick();
        check(state.learned(ID)&&state.exp(ID)==0&&state.levelExperience==.25&&battery.energy==0&&process.state()==DevelopmentProcess.State.DONE,"learning grants only canonical learned bit");
        check(SkillCatalog.levelSkillCount(state)==denominator&&PresetSkills.edit(state,0,0,ID),"XP denominator unchanged; learned452 binds");
        state.unlearn(ID);process.start(new Battery(9600),action);for(int tick=0;tick<239;tick++)process.tick();state.unlearn(PARENT);process.tick();
        check(!state.learned(ID)&&process.state()==DevelopmentProcess.State.FAILED,"final completion rechecks442");
        state.learn(PARENT);SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY,ID),new ClassicSkillConfiguration.Flags(false,false)));
        check(!DevelopmentActions.canLearn(state,DeveloperType.ADVANCED,skill),"disabled452 rejects native learning");SkillCatalog.resetConfiguration();
        state.changeCategoryClassic("meltdowner");check(!DevelopmentActions.canLearn(state,DeveloperType.ADVANCED,skill),"wrong category fails");
    }
    private static void wire(){
        var request=new HighPowerLearningProtocol.Request(ID,Integer.MAX_VALUE);
        check(request.wire().equals("avc:~zik0zj")&&request.equals(HighPowerLearningProtocol.parse(request.wire())),"maximum sequence canonical round trip");
        String uuid="01234567-89ab-cdef-0123-456789abcdef";
        check((uuid+":"+request.wire()).getBytes(StandardCharsets.UTF_8).length==48&&(uuid+":"+ID+":~zik0zj").length()>64,"compact wire fits native64-byte machine token");
        for(String invalid:List.of("avc:~0","avc:~zik0zk","avc:~01","avc:1",ID+":~1","new452:~1","452:~1","avc:"+"0".repeat(65)))
            check(HighPowerLearningProtocol.parse(invalid)==null,"invalid/noncanonical/overflow wire rejected: "+invalid);
        check(new HighPowerLearningProtocol.Request(ID,1).equals(HighPowerLearningProtocol.parse(ID+":1")),"old canonical decimal form accepted");
        var aliases=Map.ofEntries(Map.entry("scatter_locator_plate","slp"),Map.entry("remote_scatter_relay","rsr"),Map.entry("wind_pressure_lift","wpl"),Map.entry("multi_axis_reflection","mar"),Map.entry("circulation_listening","cl"),Map.entry("multidimensional_landing_array","mla"),Map.entry("spatial_pincer_corridor","spc"),Map.entry("tactical_coordinate_formation","tcf"),Map.entry("displaced_projection","dp"),Map.entry("directional_dissipation_curtain","dc"),Map.entry("magnetic_ground_step","mgs"),Map.entry("disintegration_sweep","ds"));
        for(var skill:HighPowerSkills.ALL.subList(0,52)){
            var old=new HighPowerLearningProtocol.Request(skill.id(),Integer.MAX_VALUE);
            check(old.wire().equals(aliases.getOrDefault(skill.id(),skill.id())+":~zik0zj")&&old.equals(HighPowerLearningProtocol.parse(old.wire())),"old max-sequence wire preserved: "+skill.id());
        }
        var pending=new HighPowerLearningProtocol.Pending();pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(ID,1),true,""))&&pending.waiting(),"stale sequence cannot finish request");
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(PARENT,Integer.MAX_VALUE),true,""))&&pending.waiting(),"wrong skill cannot finish request");
        check(pending.receive(HighPowerLearningProtocol.result(request,true,""))&&pending.accepted()&&!pending.waiting(),"matching final result completes once");
        check(!pending.receive(HighPowerLearningProtocol.result(request,true,"")),"duplicate result rejected");
    }
    private static void savedIdentity(){
        var state=prepared();state.learn(ID);state.setActivateState(true);state.cp=19.5;state.overload=2.25;state.levelExperience=.25;
        check(PresetSkills.edit(state,1,2,ID),"canonical saved preset");var tag=AbilityStorage.encodeSaved(state);var restored=AbilityStorage.decodeSaved(tag,state.cpDataConfig());
        check(restored.learned(ID)&&!restored.learned("avc")&&restored.presets.skill(1,2).equals(ID)&&AbilityStorage.encodeSaved(restored).equals(tag),"saved identity remains canonical with unchanged schema/resource values");
    }
}
