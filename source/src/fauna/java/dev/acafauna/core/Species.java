package dev.acafauna.core;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/** Immutable authored profiles. Registry names survive standalone-to-ACA migration. */
public record Species(String id,String zh,String en,String temper,String mobility,String diet,
    String habitat,String group,float hp,float damage,float speed,float width,float height,
    String voice,String special_a,String special_b,boolean breed,float aggression,
    String activity,int texture,int color,float clearance,
    boolean juvenile_model,boolean juvenile_texture,float juvenile_height,
    float gait_walk_mps,float gait_run_mps,int flight_launch_ticks,int flight_landing_ticks) {
    private static final Map<String,Species> ALL=load();
    private static Map<String,Species> load() {
        try(var stream=Species.class.getResourceAsStream("/assets/aca_fauna/species.json")) {
            if(stream==null)throw new IllegalStateException("Missing authored species profiles");
            var profiles=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Species[].class);
            Map<String,Species> map=new LinkedHashMap<>();
            for(var p:profiles)if(map.put(p.id,p)!=null)throw new IllegalStateException("Duplicate species "+p.id);
            if(map.size()<52)throw new IllegalStateException("Expected at least the original 52 species; found "+map.size());
            return Collections.unmodifiableMap(map);
        } catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    public static Collection<Species> all(){return ALL.values();}
    public static Species get(String id){return Objects.requireNonNull(ALL.get(id),id);}
    public static Species of(EntityType<?> type){return get(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());}
    public boolean passive(){return temper.equals("PASSIVE");}
    public boolean conditional(){return temper.equals("CONDITIONAL");}
    public boolean flies(){return mobility.equals("FLY");}
    public boolean aquatic(){return mobility.startsWith("AQUATIC");}
    public boolean amphibious(){return mobility.equals("AMPHIBIOUS")||Set.of("swan","mallard_duck","flamingo","pukeko").contains(id);}
    public boolean climbs(){return mobility.equals("CLIMB");}
    public boolean predator(){return Set.of("CARNIVORE","PISCIVORE","INSECTIVORE").contains(diet);}
    public boolean herd(){return !group.equals("solitary");}
    public ResourceLocation resource(String path){return ResourceLocation.fromNamespaceAndPath("aca_fauna",path);}
    public TagKey<Item> food(){return TagKey.create(Registries.ITEM,resource("food/"+diet.toLowerCase(Locale.ROOT)));}
    public TagKey<EntityType<?>> prey(){return TagKey.create(Registries.ENTITY_TYPE,resource("prey/"+id));}
}
