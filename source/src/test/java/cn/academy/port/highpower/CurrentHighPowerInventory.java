package cn.academy.port.highpower;

import cn.academy.port.SkillCatalog;
import cn.academy.port.client.HighPowerDeveloperLayout;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.preset.PresetSkills;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Current inventory oracle. Historical source hashes and every predecessor ordinal stay pinned. */
public final class CurrentHighPowerInventory {
    private CurrentHighPowerInventory() {}
    private static final List<SkillCatalog.Skill> EXPECTED = load();
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static SkillCatalog.Skill skill(String category,String id,int level,String parent){
        return new SkillCatalog.Skill(category,id,level,true,List.of(new SkillCatalog.Requirement(parent,0)),0);
    }
    private static List<SkillCatalog.Skill> load(){
        try {
            byte[] bytes=Files.readAllBytes(Path.of("src/test/resources/highpower-learning/autonomous-vector-circle-v20/baseline-v80/HighPowerSkills.java"));
            require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals("3b6748cebdd095fd295d18fe0f9f4c742f3dfc41ae345cd11e8bc11221731608"),"authentic 52-entry predecessor source changed");
            String source=new String(bytes,StandardCharsets.UTF_8);
            var constants=new HashMap<String,String>();
            var strings=Pattern.compile("\\b([A-Z_]+) = \"([^\"]+)\"").matcher(source);
            while(strings.find())constants.put(strings.group(1),strings.group(2));
            var records=Pattern.compile("new SkillCatalog\\.Skill\\(([^,\\n]+), ([^,\\n]+), ([0-9]+), (true|false),\\s*(List\\.of\\((?:[^()]|\\([^()]*\\))*\\)), ([0-9]+)\\)").matcher(source);
            var all=new ArrayList<SkillCatalog.Skill>();
            while(records.find()){
                String category=switch(records.group(1)){
                    case "CATEGORY" -> "electromaster";
                    case "HighPowerCategories.MELTDOWNER" -> "meltdowner";
                    case "HighPowerCategories.TELEPORTER" -> "teleporter";
                    case "HighPowerCategories.VECMANIP" -> "vecmanip";
                    default -> throw new AssertionError("unparsed historical category");
                };
                var requirements=new ArrayList<SkillCatalog.Requirement>();
                var req=Pattern.compile("new SkillCatalog.Requirement\\(([^,]+), ([^)]+)\\)").matcher(records.group(5));
                while(req.find())requirements.add(new SkillCatalog.Requirement(constants.get(req.group(1)),Double.parseDouble(req.group(2))));
                all.add(new SkillCatalog.Skill(category,constants.get(records.group(2)),Integer.parseInt(records.group(3)),Boolean.parseBoolean(records.group(4)),List.copyOf(requirements),Integer.parseInt(records.group(6))));
            }
            require(all.size()==52,"unparsed predecessor records");
            all.add(skill("vecmanip","autonomous_vector_circle",5,"multi_axis_reflection"));
            all.add(skill("electromaster","pulse_shield",4,"thunder_net"));
            all.add(skill("electromaster","rail_relay",5,"rail_thunder_array"));
            all.add(skill("meltdowner","reverse_jet_brake",4,"beam_step"));
            all.add(skill("teleporter","incident_bypass",3,"cross_substitution"));
            all.add(skill("teleporter","phase_corridor",5,"emergency_return"));
            all.add(skill("vecmanip","zenith_vector_dominion",5,"vector_cage"));
            all.add(skill("vecmanip","impulse_split",5,"multi_axis_reflection"));
            all.add(skill("vecmanip","vector_construct",4,"vector_cage"));
            return List.copyOf(all);
        } catch(Exception e){throw new AssertionError("current inventory trust root unavailable",e);}
    }
    public static List<List<Double>> mdTurns(String id){
        try {
            byte[] bytes=Files.readAllBytes(Path.of("src/test/resources/developer-layout-v2/meltdowner-routes.json"));
            require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals("8a5ad4299f95b352ad74420da2e819d4b4a0ce123b007715998b7e114aba9942"),"reviewed v2 path fixture changed");
            var points=com.google.gson.JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray(id);
            var turns=new ArrayList<List<Double>>();
            for(int i=1;i<points.size()-1;i++){var p=points.get(i).getAsJsonArray();turns.add(List.of(p.get(0).getAsDouble(),p.get(1).getAsDouble()));}
            return List.copyOf(turns);
        } catch(Exception e){throw new AssertionError("v2 developer route fixture unavailable",e);}
    }
    public static double pageCoordinate(String id,double old,boolean y){
        return switch(id){
            case "scatter_plates" -> y?28:old;
            case "beam_step" -> y?old:54;
            case "beam_etching" -> y?98:old;
            case "zero_gap_armor" -> y?old:161;
            default -> old;
        };
    }
    public static int pageSize(List<cn.academy.port.client.ClassicDeveloperLayout.Node> nodes){
        require(!nodes.isEmpty(),"nonempty high-power page required");
        return (int)EXPECTED.stream().filter(s -> s.category().equals(nodes.getFirst().category())).count();
    }
    public static List<SkillCatalog.Skill> expected(){return EXPECTED;}
    public static boolean matches(List<SkillCatalog.Skill> candidate){return candidate.equals(EXPECTED);}
    public static void verify(){
        require(matches(HighPowerSkills.ALL),"all 61 exact skill records and ordinals required");
        var ids=new HashSet<String>();EXPECTED.forEach(s->require(ids.add(s.id()),"duplicate canonical ID"));
        require(ids.size()==61,"exact high-power base ID count");
        var canon=cn.academy.port.canon.CanonSkills.ALL;var canonIds=cn.academy.port.canon.CanonSkills.IDS;
        require(canon.size()==canonIds.size()&&canon.stream().allMatch(s->canonIds.contains(s.id())),"exact canon extension IDs");
        var allIds=new HashSet<>(ids);for(String id:canonIds)require(allIds.add(id),"canon and high-power IDs do not overlap");
        require(HighPowerSkills.IDS.equals(allIds),"exact high-power and canon ID union");
        for(var skill:EXPECTED)for(int sequence:new int[]{1,35,36,Integer.MAX_VALUE}){
            var request=new HighPowerLearningProtocol.Request(skill.id(),sequence);
            require(("00000000-0000-0000-0000-000000000000:"+request.wire()).length()<=64,"native machine learning value bound: "+skill.id());
            require(request.equals(HighPowerLearningProtocol.parse(request.wire())),"canonical learning roundtrip: "+skill.id());
        }
        var catalog=new ArrayList<>(SkillCatalog.ALL);catalog.addAll(EXPECTED);catalog.addAll(canon);
        require(SkillCatalog.ALL.size()==50&&SkillCatalog.REGISTERED.equals(catalog),"50 classics, exact 61 additions, then canon extension");
        var presets=new HashSet<>(PresetSkills.IMPLEMENTED);presets.addAll(ids);
        require(PresetSkills.IMPLEMENTED.size()==35&&presets.size()==96,"exact classic and high-power preset base");
        for(String id:canonIds)require(presets.add(id),"canon presets do not overlap the original base");
        require(PresetSkills.REGISTERED.equals(presets),"exact base and canon preset union");
        var keys=new HashSet<>(SkillCatalog.CONFIG_KEYS);EXPECTED.forEach(s->keys.add(ClassicSkillConfiguration.key(s.category(),s.id())));
        require(SkillCatalog.CONFIG_KEYS.size()==41&&keys.size()==102,"exact classic and high-power configuration base");
        canon.forEach(s->require(keys.add(ClassicSkillConfiguration.key(s.category(),s.id())),"canon configuration keys do not overlap"));
        require(SkillCatalog.REGISTERED_CONFIG_KEYS.equals(keys),"exact base and canon configuration union");
        for(var category:List.of("electromaster","meltdowner","teleporter","vecmanip")){
            var nodes=HighPowerDeveloperLayout.category(category);var page=HighPowerDeveloperLayout.paged(category)?HighPowerDeveloperLayout.page(category,HighPowerDeveloperLayout.Page.HIGH_POWER):nodes;
            var wanted=EXPECTED.stream().filter(s->s.category().equals(category)).toList();
            require(nodes.size()==wanted.size()&&page.size()==wanted.size(),"no missing/extra tree node: "+category);
            var seen=new HashSet<String>();
            for(var node:page){
                require(seen.add(node.id()),"duplicate tree node");
                var skill=wanted.stream().filter(s->s.id().equals(node.id())).findFirst().orElseThrow();
                require(node.category().equals(category)&&node.level()==skill.level()&&node.parent().equals(skill.requirements().isEmpty()?"":skill.requirements().getFirst().id()),"tree learning identity: "+node.id());
            }
        }
        var meltdowner=HighPowerDeveloperLayout.page("meltdowner",HighPowerDeveloperLayout.Page.HIGH_POWER);
        for(var node:meltdowner){
            require(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"meltdowner hover stays in viewport");
            for(var other:meltdowner)if(!node.id().equals(other.id()))require(Math.hypot(node.x()-other.x(),node.y()-other.y())>=37.2,"meltdowner hover circles disjoint: "+node.id()+"/"+other.id());
            for(int step=0;step<=100;step++)for(var edge:HighPowerDeveloperLayout.meltdownerDependency(node.id(),step/100d))
                for(var other:meltdowner)if(!other.id().equals(node.id())&&!other.id().equals(node.parent()))
                    require(java.awt.geom.Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),other.x()+8,other.y()+8)>18.6+2.75,"meltdowner full-width dependency clearance: "+node.id()+"/"+other.id());
        }
        var teleporter=HighPowerDeveloperLayout.category("teleporter");
        for(var node:teleporter){
            require(node.x()+8-18.6>=0&&node.x()+8+18.6<=257&&node.y()+8-18.6>=0&&node.y()+8+18.6<=139,"teleporter hover stays in viewport");
            for(var other:teleporter)if(!node.id().equals(other.id()))require(Math.hypot(node.x()-other.x(),node.y()-other.y())>=37.2,"teleporter hover circles disjoint");
            for(int step=0;step<=100;step++)for(var edge:HighPowerDeveloperLayout.teleporterDependency(node.id(),step/100d))
                for(var other:teleporter)if(!other.id().equals(node.id())&&!other.id().equals(node.parent()))
                    require(java.awt.geom.Line2D.ptSegDist(edge.x0(),edge.y0(),edge.x1(),edge.y1(),other.x()+8,other.y()+8)>18.6+2.75,"teleporter full-width dependency clearance: "+node.id()+"/"+other.id());
        }
        // Every record field and ordinal, including the new suffix, has a live counterexample.
        for(int i=0;i<EXPECTED.size();i++){
            var mutated=new ArrayList<>(EXPECTED);var s=mutated.get(i);
            mutated.set(i,new SkillCatalog.Skill(s.category(),s.id(),s.level()+1,s.controllable(),s.requirements(),s.anyLearnedSkillLevel()));
            require(!matches(mutated),"changed level admitted at "+i);
            mutated=new ArrayList<>(EXPECTED);Collections.swap(mutated,i,(i+1)%EXPECTED.size());
            require(!matches(mutated),"changed ordinal admitted at "+i);
        }
    }
}
