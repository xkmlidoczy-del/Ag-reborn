/* Portable original-source registry/layout/resource/condition regression. GPLv3. */
package cn.academy.port.achievements;
import cn.academy.port.core.AbilityProgress;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
public final class ClassicAchievementRegressionTest {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        Path root=Path.of(System.getProperty("academy.achievement.root")),oracle=Path.of(System.getProperty("academy.achievement.oracle"));
        List<String> rows=Files.readAllLines(oracle);check(rows.size()==56,"unchanged source registered 56");
        check(ClassicAchievementCatalog.get(null)==null,"root has no parent, including client render traversal");
        for(var e:ClassicAchievementCatalog.ALL){
            int depth=0;var ancestor=e;var seen=new HashSet<String>();
            while(ancestor!=null){check(seen.add(ancestor.id()),"source parent chain is acyclic "+e.id());depth++;ancestor=ClassicAchievementCatalog.get(ancestor.parent());}
            check(ClassicAchievementCatalog.distance(e,id->false)==depth,"every locked page icon reaches its root without a null-map lookup "+e.id());
            check(ClassicAchievementCatalog.distance(e,id->true)==0,"every earned page icon remains visible "+e.id());
        }
        for(int i=0;i<rows.size();i++){String[] a=rows.get(i).split("\t");var e=ClassicAchievementCatalog.ALL.get(i);
            check(e.id().equals(a[0])&&e.page().equals(a[1])&&e.x()==Integer.parseInt(a[2])&&e.y()==Integer.parseInt(a[3])&&e.icon().equals(a[4])&&Objects.toString(e.parent(),"null").equals(a[5]),"unchanged-source order/IDs/position/icon/parent "+i+" "+rows.get(i));
            String originalKey=a[7].equals("matrix_core_0")?"matrix_core":a[7];
            check(e.kind().name().equals(a[6])&&e.key().equals(originalKey)&&e.level()==Integer.parseInt(a[8]),"unchanged source registered condition "+e.id());
            if(e.kind()==ClassicAchievementCatalog.Kind.CRAFT)check(a[9].equals("-1")&&a[10].equals("1"),"source wildcard metadata/minimum one craft "+e.id());
            if(e.kind()==ClassicAchievementCatalog.Kind.PICKUP)check(a[9].equals("0"),"source exact low crystal pickup metadata");
            var json=JsonParser.parseString(Files.readString(root.resolve("src/main/resources/data/academy/advancement/"+e.advancementPath()+".json"))).getAsJsonObject();
            check(json.getAsJsonObject("criteria").has(e.criterion()),"native manual criterion "+e.id());
            check(json.getAsJsonObject("criteria").getAsJsonObject(e.criterion()).get("trigger").getAsString().equals("minecraft:impossible"),"no speculative vanilla condition");
            check(!json.has("display"),"silent native persistence; original bitmap page/toast owns presentation");
            if(e.icon().startsWith("texture:")){String rel="assets/academy/textures/"+e.icon().substring(8);byte[] actual=Files.readAllBytes(root.resolve("src/main/resources/"+rel)),original=Files.readAllBytes(root.resolve("src/test/resources/classic-achievements/original/resources/"+rel));check(Arrays.equals(actual,original),"exact original icon bytes "+e.id());}
            if(e.parent()!=null)check(json.get("parent").getAsString().equals("academy:"+ClassicAchievementCatalog.get(e.parent()).advancementPath()),"native parent "+e.id());
        }
        for(String language:List.of("en_US.lang","zh_CN.lang","zh_TW.lang","ja_JP.lang")){
            String text=Files.readString(root.resolve("src/test/resources/classic-achievements/original/resources/assets/academy/lang/"+language));
            var actual=JsonParser.parseString(Files.readString(root.resolve("src/main/resources/assets/academy/lang/"+language.substring(0,5).toLowerCase(java.util.Locale.ROOT)+".json"))).getAsJsonObject();
            Map<String,String> originalText=new HashMap<>();for(String line:text.split("\n")){int eq=line.indexOf('=');if(eq>0)originalText.put(line.substring(0,eq).trim(),line.substring(eq+1).replace("\\n","\n"));}
            for(var e:ClassicAchievementCatalog.ALL)for(String key:List.of(e.title(),e.description()))check(originalText.containsKey(key)&&actual.has(key)&&actual.get(key).getAsString().equals(originalText.get(key)),"exact native source translation "+language+" "+key);
        }
        check(ClassicAchievementCatalog.PAGES.stream().map(p->ClassicAchievementCatalog.page(p).size()).toList().equals(List.of(12,13,11,10,10)),"exact five pages");
        Set<String> earned=new HashSet<>();var child=ClassicAchievementCatalog.get("meltdowner.light_shield");check(!ClassicAchievementCatalog.canAward(child,earned::contains),"event before parent is lost");earned.add("meltdowner.rad_intensify");check(ClassicAchievementCatalog.canAward(child,earned::contains),"new learn event may award after parent");earned.add(child.id());check(!ClassicAchievementCatalog.canAward(child,earned::contains),"repeat award idempotent");
        check(ClassicAchievementCatalog.matching(ClassicAchievementCatalog.Kind.LEVEL,"teleporter",5).getFirst().id().equals("teleporter.lv5"),"exact level5 only");
        check(ClassicAchievementCatalog.matching(ClassicAchievementCatalog.Kind.LEARN,"mine_ray_luck",0).isEmpty(),"mine-ray icon is luck but condition is basic learn");
        check(ClassicAchievementCatalog.matching(ClassicAchievementCatalog.Kind.LEARN,"mine_ray_basic",0).getFirst().id().equals("meltdowner.mine_ray"),"source mine-ray condition");
        for(int meta=0;meta<3;meta++)check(ClassicAchievementCatalog.craftKey("academy:matrix_core_"+meta).equals("matrix_core"),"source any-meta core");
        check(ClassicAchievementCatalog.matching(ClassicAchievementCatalog.Kind.CRAFT,"windgen_fan",0).isEmpty(),"wind icon is fan but award needs main craft");
        var state=new AbilityProgress();List<String> events=new ArrayList<>();state.bindProgress(id->events.add("learn:"+id),value->events.add("level:"+value));state.selectCategory("meltdowner");state.setLevel(1);state.setLevel(1);state.learn("rad_intensify");state.learn("rad_intensify");
        check(events.equals(List.of("level:1","learn:rad_intensify")),"mutation callbacks fire only actual change/first learn");
        check(!Files.exists(root.resolve(".reference"))&&!Files.exists(root.resolve(".staging")),"portable archived root has no private reference/stage dependency");
        System.out.println("56 unchanged-original registrations, five layouts, exact assets/languages, source conditions, actual mutation gates and portable-root checks passed");
    }
}
