package cn.academy.port.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Exact source skill identity/positions; generic skills remain distinct per-category nodes. */
public final class ClassicDeveloperLayout {
    public record Node(String category,String id,int index,int level,double x,double y,String parent,boolean enabled,
                       String hintIcon,String nameKey,String descriptionKey,List<JsonObject> conditions){}
    public static final List<Node> NODES=load();
    private static List<Node> load(){
        try(var reader=new InputStreamReader(Objects.requireNonNull(ClassicDeveloperLayout.class.getResourceAsStream("/classic-developer-layout.json")),StandardCharsets.UTF_8)){
            var list=new ArrayList<Node>();
            for(var value:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("skills")){
                var o=value.getAsJsonObject();var conditions=new ArrayList<JsonObject>();for(var condition:o.getAsJsonArray("conditions"))conditions.add(condition.getAsJsonObject());
                list.add(new Node(o.get("category").getAsString(),o.get("id").getAsString(),o.get("legacy_index").getAsInt(),o.get("level").getAsInt(),
                        o.get("guiX").getAsDouble(),o.get("guiY").getAsDouble(),o.get("parent").isJsonNull()?"":o.get("parent").getAsString(),o.get("enabled").getAsBoolean(),
                        o.get("hintIcon").getAsString(),o.get("nameKey").getAsString(),o.get("descriptionKey").getAsString(),List.copyOf(conditions)));
            }return List.copyOf(list);
        }catch(java.io.IOException error){throw new ExceptionInInitializerError(error);}
    }
    public static List<Node> category(String category){var config=cn.academy.port.SkillCatalog.configuration();return java.util.stream.Stream.concat(NODES.stream().filter(n->n.category().equals(category)).map(n->{String parent=config.parent(category,n.parent.isEmpty()?null:n.parent);var conditions=n.conditions.stream().filter(c->!c.get("type").getAsString().equals("skill_dependency")||config.dependencyPresent(category,c.get("id").getAsString())).toList();return new Node(n.category,n.id,n.index,n.level,n.x,n.y,parent==null?"":parent,config.enabled(category,n.id),n.hintIcon,n.nameKey,n.descriptionKey,conditions);}),HighPowerDeveloperLayout.category(category).stream()).toList();}
    private ClassicDeveloperLayout(){}
}
