package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.ClassicDeveloperLayout;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.*;
import cn.academy.port.preset.PresetSkills;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** NEW443 paid learning and codec evidence; .71 predecessor bytes are evidence, never regenerated. */
public final class WindPressureLiftLearningRegressionTest {
    private static final String ID="wind_pressure_lift", PARENT="multi_axis_reflection", CATEGORY="vecmanip";
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static AbilityProgress state(boolean parent){var s=new AbilityProgress();s.changeCategoryClassic(CATEGORY);s.setLevel(4);s.setActivateState(true);if(parent)s.learn(PARENT);return s;}
    private static final class Battery implements DevelopmentProcess.Developer {
        double value;Battery(double value){this.value=value;}public DeveloperType type(){return DeveloperType.ADVANCED;}
        public double energy(){return value;}public double maxEnergy(){return 10000;}
        public boolean tryPullEnergy(double amount){if(value<amount)return false;value-=amount;return true;}
    }
    public static void main(String[] args)throws Exception {
        SkillCatalog.resetConfiguration();
        try { registration(); learning(); codec(); predecessorNodes(); page(); }
        finally { SkillCatalog.resetConfiguration(); }
        System.out.println("WindPressureLiftLearningRegressionTest: "+checks+" checks passed");
    }
    private static void registration()throws Exception {
        Path frozen=Path.of("src/test/resources/current-geometry-proof/charging-core/registry-443-v12/HighPowerSkills.v71.java");
        byte[] bytes=Files.readAllBytes(frozen);
        check(sha(bytes).equals("cb3f225d820e1361510d021a52910f7b5f0ee2c3b329cfabbfd67ab3226b0b46"),"exact .71 source captured from git 332710ac953d4681c2ef03eabb19914057292cd7 before443 edits");
        String source=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        Map<String,String> constants=new HashMap<>();
        var strings=Pattern.compile("\\b([A-Z_]+) = \"([^\"]+)\"").matcher(source);
        while(strings.find()){
            constants.put(strings.group(1),strings.group(2));
            check(HighPowerSkills.class.getField(strings.group(1)).get(null).equals(strings.group(2)),"predecessor saved-ID constant unchanged "+strings.group(1));
        }
        var proposals=Pattern.compile("\\b([A-Z_]+_PROPOSAL) = ([0-9]+)").matcher(source);
        while(proposals.find())check(HighPowerSkills.class.getField(proposals.group(1)).getInt(null)==Integer.parseInt(proposals.group(2)),"predecessor proposal unchanged "+proposals.group(1));
        var records=Pattern.compile("new SkillCatalog\\.Skill\\(([^,\\n]+), ([^,\\n]+), ([0-9]+), (true|false),\\s*(List\\.of\\((?:[^()]|\\([^()]*\\))*\\)), ([0-9]+)\\)").matcher(source);
        var previous=new ArrayList<SkillCatalog.Skill>();
        while(records.find()){
            String category=switch(records.group(1)){
                case "CATEGORY" -> constants.get("CATEGORY");
                case "HighPowerCategories.MELTDOWNER" -> "meltdowner";
                case "HighPowerCategories.TELEPORTER" -> "teleporter";
                case "HighPowerCategories.VECMANIP" -> "vecmanip";
                default -> throw new AssertionError("unexpected frozen category "+records.group(1));
            };
            var requirements=new ArrayList<SkillCatalog.Requirement>();
            var requirement=Pattern.compile("new SkillCatalog.Requirement\\(([^,]+), ([^)]+)\\)").matcher(records.group(5));
            while(requirement.find())requirements.add(new SkillCatalog.Requirement(constants.get(requirement.group(1)),Double.parseDouble(requirement.group(2))));
            previous.add(new SkillCatalog.Skill(category,constants.get(records.group(2)),Integer.parseInt(records.group(3)),Boolean.parseBoolean(records.group(4)),List.copyOf(requirements),Integer.parseInt(records.group(6))));
        }
        check(previous.size()==48,"all48 .71 predecessor records parsed from pinned source");
        check(retainsPrevious(HighPowerSkills.ALL.subList(0,49),previous),"all48 earlier fields, prerequisites and ordinals unchanged;443 is sole append");
        check(HighPowerSkills.ALL.size()==50&&HighPowerSkills.ALL.get(49).equals(new SkillCatalog.Skill("meltdowner","point_meltdown",3,true,List.of(new SkillCatalog.Requirement("beam_etching",0)),0)),"current .75 adds only exact NEW233 after the unchanged49-entry .74 prefix");
        var expectedIds=new HashSet<String>();for(var skill:previous)expectedIds.add(skill.id());expectedIds.add(ID);
        check(HighPowerSkills.ALL.subList(0,49).stream().map(SkillCatalog.Skill::id).collect(java.util.stream.Collectors.toSet()).equals(expectedIds),"retained .74 ID set is exactly predecessor48 plus443");
        expectedIds.add("point_meltdown");
        check(HighPowerSkills.IDS.equals(expectedIds),"current .75 ID set appends only canonical233 to the unchanged49 predecessor IDs");
        check(HighPowerSkills.WIND_PRESSURE_LIFT_PROPOSAL==443&&HighPowerSkills.WIND_PRESSURE_LIFT.equals(ID),"new persistent identity and proposal");
        var skill=HighPowerSkills.ALL.get(48);
        check(skill.equals(new SkillCatalog.Skill(CATEGORY,ID,4,true,List.of(new SkillCatalog.Requirement(PARENT,0)),0)),"443 has only LV4 learned442 prerequisite at zero mastery");
        var first=previous.getFirst();
        for(var changed:List.of(
                new SkillCatalog.Skill("teleporter",first.id(),first.level(),first.controllable(),first.requirements(),first.anyLearnedSkillLevel()),
                new SkillCatalog.Skill(first.category(),"tampered_old_id",first.level(),first.controllable(),first.requirements(),first.anyLearnedSkillLevel()),
                new SkillCatalog.Skill(first.category(),first.id(),2,first.controllable(),first.requirements(),first.anyLearnedSkillLevel()),
                new SkillCatalog.Skill(first.category(),first.id(),first.level(),false,first.requirements(),first.anyLearnedSkillLevel()),
                new SkillCatalog.Skill(first.category(),first.id(),first.level(),first.controllable(),List.of(new SkillCatalog.Requirement(PARENT,0)),first.anyLearnedSkillLevel()),
                new SkillCatalog.Skill(first.category(),first.id(),first.level(),first.controllable(),first.requirements(),1))){
            var mutant=new ArrayList<>(HighPowerSkills.ALL.subList(0,49));mutant.set(0,changed);
            check(!retainsPrevious(mutant,previous),"old registration tamper cannot pass by merely updating totals: "+changed);
        }
        var reordered=new ArrayList<>(HighPowerSkills.ALL.subList(0,49));Collections.swap(reordered,46,47);
        check(!retainsPrevious(reordered,previous),"predecessor441/442 order tampering rejects");
        byte[] classic=Files.readAllBytes(Path.of("src/main/resources/classic-skills.json"));
        check(sha(classic).equals("fe698b35ce94e9cbd628dc63950bfd706749177d3b0ebe4048c1b4e7905e4433"),"original50 classic catalog bytes unchanged");
        check(SkillCatalog.ALL.size()==50&&SkillCatalog.REGISTERED.subList(0,50).equals(SkillCatalog.ALL)
                &&SkillCatalog.REGISTERED.subList(50,98).equals(previous)&&SkillCatalog.REGISTERED.get(98).equals(skill),"classic50 and predecessor48 retain every registered ordinal");
        Set<String> classicPresets=Set.of("arc_gen","charging","railgun","electron_bomb","threatening_teleport","dir_shock","ground_shock","dir_blast","storm_wing","blood_retro","vec_accel","vec_deviation","vec_reflection","plasma_cannon","mag_movement","mag_manip","thunder_bolt","mine_detect","body_intensify","thunder_clap","scatter_bomb","light_shield","meltdowner","mine_ray_basic","mine_ray_expert","mine_ray_luck","ray_barrage","jet_engine","electron_missile","penetrate_teleport","mark_teleport","flesh_ripping","location_teleport","shift_tp","flashing");
        check(PresetSkills.IMPLEMENTED.equals(classicPresets)&&classicPresets.size()==35,"all35 classic preset identities unchanged");
        check(SkillCatalog.CONFIG_KEYS.size()==41&&SkillCatalog.REGISTERED.size() == 100 && cn.academy.port.SkillCatalog.REGISTERED.get(99).id().equals("point_meltdown")&&SkillCatalog.REGISTERED_CONFIG_KEYS.size() == 91&&PresetSkills.REGISTERED.size() == 85 && cn.academy.port.preset.PresetSkills.REGISTERED.contains("point_meltdown")&&PresetSkills.REGISTERED.contains(ID),"derived registries retain443 and append only233");
        for(String category:DevelopmentActions.CATEGORIES)for(int level=1;level<=5;level++){
            var s=new AbilityProgress();s.changeCategoryClassic(category);s.setLevel(level);
            int expectedLevel=level;long expected=SkillCatalog.ALL.stream().filter(old->old.category().equals(category)&&old.level()==expectedLevel&&old.controllable()).count();
            check(SkillCatalog.levelSkillCount(s)==expected,"classic XP denominator excludes all highpower additions "+category+" LV"+level);
        }
    }
    private static boolean retainsPrevious(List<SkillCatalog.Skill> candidate,List<SkillCatalog.Skill> previous){
        return candidate.size()==49&&candidate.subList(0,48).equals(previous)&&candidate.get(48).id().equals(ID);
    }
    private static void learning(){
        var skill=SkillCatalog.find(CATEGORY,ID).orElseThrow();var s=state(false);
        check(!SkillCatalog.canLearn(s,skill)&&!DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill)&&!PresetSkills.edit(s,0,0,ID),"missing442 and unlearned443 refuse learning/binding");
        s.learn("vector_cage");check(!SkillCatalog.canLearn(s,skill),"441 cannot substitute for parent442");s.learn(PARENT);s.setLevel(3);
        check(!SkillCatalog.canLearn(s,skill)&&!DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill),"LV3 cannot bypass LV4");s.setLevel(4);s.levelExperience=.125;
        check(SkillCatalog.canLearn(s,skill)&&DevelopmentActions.canLearn(s,DeveloperType.ADVANCED,skill)
                &&!DevelopmentActions.canLearn(s,DeveloperType.NORMAL,skill)&&!DevelopmentActions.canLearn(s,DeveloperType.PORTABLE,skill),"Advanced tier and zero-mastery parent are exact");
        var action=DevelopmentActions.skill(s,skill);check(action.stimulations()==11&&DeveloperType.ADVANCED.actualConsumption(11)==7040,"native eleven stimulations cost176 ticks/7040IF");
        var battery=new Battery(7040);var process=new DevelopmentProcess();process.start(battery,action);
        for(int tick=0;tick<175;tick++)process.tick();check(!s.learned(ID)&&battery.value==40,"no grant before final40IF");
        process.tick();check(process.state()==DevelopmentProcess.State.DONE&&s.learned(ID)&&battery.value==0&&s.exp(ID)==0&&s.levelExperience==.125,"exact paid completion grants no free mastery or XP");
        check(PresetSkills.edit(s,0,2,ID),"learned443 binds through ordinary presets");
        var saved=AbilityStorage.encodeSaved(s);var loaded=AbilityStorage.decodeSaved(saved,s.cpDataConfig());
        check(AbilityStorage.encodeSaved(loaded).equals(saved)&&loaded.presets.skill(0,2).equals(ID)&&loaded.learned(ID),"canonical save/learning/preset identity roundtrips");
        var old=state(true);old.learn("storm_wing");check(PresetSkills.edit(old,0,0,PARENT)&&PresetSkills.edit(old,0,1,"storm_wing"),"prepare predecessor binding save");
        var oldSave=AbilityStorage.encodeSaved(old);var restored=AbilityStorage.decodeSaved(oldSave,old.cpDataConfig());
        check(!restored.learned(ID)&&AbilityStorage.encodeSaved(restored).equals(oldSave),"loading .71-style state does not learn443 or alter prior bindings");
        var shortState=state(true);var shortBattery=new Battery(7039);var shortProcess=new DevelopmentProcess();shortProcess.start(shortBattery,DevelopmentActions.skill(shortState,skill));
        for(int tick=0;tick<176;tick++)shortProcess.tick();check(shortProcess.state()==DevelopmentProcess.State.FAILED&&!shortState.learned(ID),"one missing IF cannot mint learning");
        var interrupted=state(true);var interruptedBattery=new Battery(7040);var canceled=new DevelopmentProcess();canceled.start(interruptedBattery,DevelopmentActions.skill(interrupted,skill));
        for(int tick=0;tick<80;tick++)canceled.tick();canceled.abort();for(int tick=0;tick<176;tick++)canceled.tick();
        check(!interrupted.learned(ID)&&interruptedBattery.value==3840,"interruption grants nothing and keeps exact spent energy");
        for(int invalidation=0;invalidation<3;invalidation++){
            var pending=state(true);var finalTick=new DevelopmentProcess();finalTick.start(new Battery(7040),DevelopmentActions.skill(pending,skill));
            for(int tick=0;tick<175;tick++)finalTick.tick();
            if(invalidation==0)pending.setLevel(3);
            if(invalidation==1)pending.changeCategoryClassic("teleporter");
            if(invalidation==2)SkillCatalog.refreshConfiguration(Map.of(ClassicSkillConfiguration.key(CATEGORY,ID),new ClassicSkillConfiguration.Flags(false,true)));
            finalTick.tick();check(finalTick.state()==DevelopmentProcess.State.FAILED&&!pending.learned(ID),"completion revalidates level/category/live enable "+invalidation);SkillCatalog.resetConfiguration();
        }
    }
    private static void codec(){
        for(int sequence:List.of(1,36,65535,Integer.MAX_VALUE)){
            var request=new HighPowerLearningProtocol.Request(ID,sequence);String wire=request.wire();
            check(wire.startsWith("wpl:~")&&request.equals(HighPowerLearningProtocol.parse(wire)),"only machine wire uses alias; canonical443 roundtrip "+sequence);
            String machine="12345678-1234-1234-1234-123456789012:"+wire;
            check(machine.length()<=64,"UUID plus maximum sequence fits actual unchanged64 value limit");
            actualCodec(new AcademyNetwork.Request("machine_hp_learn",machine));
        }
        check(new HighPowerLearningProtocol.Request(ID,Integer.MAX_VALUE).wire().equals("wpl:~zik0zj"),"canonical max-sequence wire pinned");
        for(String invalid:List.of("wpl:~0","wpl:~-1","wpl:~zik0zk","wpl:~01","wind_pressure_lift:~1","wpl:2147483647"))
            check(HighPowerLearningProtocol.parse(invalid)==null,"noncanonical/overflow alias refuses "+invalid);
        String input=new HighPowerInput.Input(3,Long.MAX_VALUE,Integer.MAX_VALUE,Long.MAX_VALUE).wire();
        check(input.length()==52&&HighPowerInput.parse(input)!=null,"largest authenticated gameplay input still52 characters");
        for(String action:List.of("wind_pressure_lift_start","wind_pressure_lift_end")){
            check(action.length()<=32,"actual443 action stays inside32 bound");actualCodec(new AcademyNetwork.Request(action,input));
        }
        var request=new HighPowerLearningProtocol.Request(ID,Integer.MAX_VALUE);var pending=new HighPowerLearningProtocol.Pending();pending.begin(request);
        check(!pending.receive(HighPowerLearningProtocol.result(new HighPowerLearningProtocol.Request(PARENT,Integer.MAX_VALUE),true,""))&&pending.waiting(),"parent442 reply cannot consume443 attempt");
        check(pending.receive(HighPowerLearningProtocol.result(request,false,"not_ready"))&&!pending.accepted()&&!pending.waiting(),"matching rejection closes only443 pending attempt");
    }
    private static void actualCodec(AcademyNetwork.Request request){
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        try{AcademyNetwork.Request.CODEC.encode(buffer,request);check(request.equals(AcademyNetwork.Request.CODEC.decode(buffer))&&!buffer.isReadable(),"real32/64 native codec consumes exact443 request");}finally{buffer.release();}
    }
    private static void predecessorNodes()throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of("src/test/resources/highpower-learning/wind-pressure-lift-v72/HighPowerDeveloperLayout.v71.java"));
        check(sha(bytes).equals("cd2f0f1350db1589cf1740c6f11a97bd054b992dae997db8d3639603cd4fc5b7"),"exact .71 layout predecessor from git332710a, separate from historical witnesses");
        String source=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        var nodes=Pattern.compile("node\\((?:category,)?HighPowerSkills\\.([A-Z_]+),([0-9]+),([0-9]+),([0-9.]+),([0-9.]+),(?:HighPowerSkills\\.([A-Z_]+)|\"\")\\)").matcher(source);
        Map<String,Integer> next=new HashMap<>();int count=0;
        while(nodes.find()){
            int proposal=Integer.parseInt(nodes.group(2));String category=switch(proposal/100){case 1->"electromaster";case 2->"meltdowner";case 3->"teleporter";case 4->CATEGORY;default->throw new AssertionError("unexpected .71 node");};
            String id=(String)HighPowerSkills.class.getField(nodes.group(1)).get(null);
            String parent=nodes.group(6)==null?"":(String)HighPowerSkills.class.getField(nodes.group(6)).get(null);
            int index=next.getOrDefault(category,0);var node=HighPowerDeveloperLayout.category(category).stream().filter(n->!n.id().equals("point_meltdown")).toList().get(index);next.put(category,index+1);count++;
            check(node.category().equals(category)&&node.id().equals(id)&&node.index()==proposal&&node.level()==Integer.parseInt(nodes.group(3))
                    &&node.x()==Double.parseDouble(nodes.group(4))&&node.y()==Double.parseDouble(nodes.group(5))&&node.parent().equals(parent),"all48 prior node identities/ordinals/positions/parents unchanged "+id);
        }
        check(count==48&&next.equals(Map.of("electromaster",13,"meltdowner",11,"teleporter",13,CATEGORY,11)),"exact prior48 nodes accounted for across all four categories");
        for(var entry:next.entrySet())check(HighPowerDeveloperLayout.category(entry.getKey()).size()==entry.getValue()+(entry.getKey().equals(CATEGORY)||entry.getKey().equals("meltdowner")?1:0),"only retained vector443 and current MD233 change frozen .71 page counts "+entry.getKey());
        var pointNodes=HighPowerDeveloperLayout.category("meltdowner").stream().filter(n->n.id().equals("point_meltdown")).toList();
        check(pointNodes.size()==1&&pointNodes.getFirst().category().equals("meltdowner")&&pointNodes.getFirst().index()==233&&pointNodes.getFirst().level()==3&&pointNodes.getFirst().parent().equals("beam_etching"),"only canonical NEW233 is excluded from the exact old-node ordinal/coordinate witness");
    }
    private static void page()throws Exception {
        var page=HighPowerDeveloperLayout.category(CATEGORY);
        String[] ids={"deflection_strike","vector_parry","braking_touch","shear_windblade","inertial_anchor","yaw_palm","trajectory_return","circulation_relief","circulation_listening","vector_cage",PARENT};
        int[] proposals={411,412,413,421,422,423,431,432,433,441,442};
        double[] xs={118,198,118,38,118,198,118,198,38,38,78},ys={61,61,126,105,105,105,17,17,17,61,61};
        check(page.size()==12&&page.get(11).id().equals(ID),"only443 appends to11 vector nodes");
        for(int i=0;i<ids.length;i++)check(page.get(i).id().equals(ids[i])&&page.get(i).index()==proposals[i]&&page.get(i).x()==xs[i]&&page.get(i).y()==ys[i],"previous vector node identity/ordinal/position unchanged "+ids[i]);
        var node=page.get(11);check(node.index()==443&&node.level()==4&&node.x()==158&&node.y()==105&&node.parent().equals(PARENT),"443 occupies free gap with actual442 parent");
        check(node.conditions().size()==2&&node.conditions().get(0).get("minimum").getAsString().equals("advanced")&&node.conditions().get(1).get("id").getAsString().equals(PARENT),"visible machine and parent conditions match authoritative learning");
        check(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"443 full-hover fits native viewport");
        for(var previous:page.subList(0,11))check(Math.hypot(node.x()-previous.x(),node.y()-previous.y())>37.2,"443 hover clears every predecessor "+previous.id());
        var route=HighPowerDeveloperLayout.windPressureLiftDependency(1);check(route.size()==3,"442→443 uses explicit three-segment gap route");
        for(var edge:route)for(var previous:page.subList(0,10)){
            double distance=java.awt.geom.Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),previous.x()+8,previous.y()+8);
            check(distance>18.6+2.75,"443 route clears unrelated full-hover ring "+previous.id());
        }
        check(HighPowerDeveloperLayout.page(CATEGORY,HighPowerDeveloperLayout.Page.CLASSIC).equals(ClassicDeveloperLayout.NODES.stream().filter(n->n.category().equals(CATEGORY)).toList()),"all classic vector layout identities unchanged");
        var navigation=new HighPowerDeveloperLayout.Navigation();navigation.synchronize(CATEGORY);
        check(navigation.switchTo(HighPowerDeveloperLayout.Page.HIGH_POWER)&&navigation.select(ID)&&navigation.beginLearning(),"443 reachable using real developer navigation");
        check(navigation.back()==HighPowerDeveloperLayout.Back.BUSY&&!navigation.switchTo(HighPowerDeveloperLayout.Page.CLASSIC),"paid learning remains navigation fenced");navigation.finishLearning();
        check(navigation.back()==HighPowerDeveloperLayout.Back.SELECTION&&navigation.back()==HighPowerDeveloperLayout.Back.CLASSIC_PAGE&&navigation.back()==HighPowerDeveloperLayout.Back.CLOSE_SCREEN,"Back preserves existing dismissal sequence");
        var assets=Path.of("src/main/resources/assets/academy/textures/abilities/vecmanip/skills");
        check(Arrays.equals(Files.readAllBytes(assets.resolve("storm_wing.png")),Files.readAllBytes(assets.resolve(ID+".png"))),"443 reuses unchanged original storm_wing bytes");
        for(String language:List.of("zh_cn","en_us")){
            var words=com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/academy/lang/"+language+".json"))).getAsJsonObject();
            for(String key:List.of("ac.ability.vecmanip.wind_pressure_lift.name","ac.ability.vecmanip.wind_pressure_lift.desc"))check(words.has(key)&&!words.get(key).getAsString().isBlank(),"localized learning contract "+language+" "+key);
        }
    }
}
