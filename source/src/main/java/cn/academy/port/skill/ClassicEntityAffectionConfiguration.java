/* AcademyCraft1.0.7 EntityAffection startup class matching. GPLv3; see NOTICE. */
package cn.academy.port.skill;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.*;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.function.Predicate;
/** Original first matching class wins, unknown names are ignored, exclusions include subclasses.
 * Modern resource IDs use the official vanilla EntityType generic class; modded types lacking a
 * public class signature use their exact registered EntityType. No entity is constructed to infer a type. */
public final class ClassicEntityAffectionConfiguration {
 public static final List<String> DEFAULT_DIFFICULTIES=List.of("Arrow=1.0","ThrownPotion=1.4","Snowball=0.1");
 public static final List<String> DEFAULT_EXCLUDED=List.of("Item","Mob","Monster","XPOrb");
 private static final Map<String,Class<? extends Entity>> LEGACY=Map.of("Arrow",AbstractArrow.class,"ThrownPotion",ThrownPotion.class,"Snowball",Snowball.class,"Item",ItemEntity.class,"Mob",Mob.class,"Monster",Monster.class,"XPOrb",ExperienceOrb.class);
 private static final Map<EntityType<?>,Class<?>> VANILLA_CLASSES=vanillaClasses();
 private record Difficulty(Predicate<Entity> matches,float amount){}
 private final List<Difficulty> difficulties;private final List<Predicate<Entity>> exclusions;
 private static volatile ClassicEntityAffectionConfiguration active=new ClassicEntityAffectionConfiguration(DEFAULT_DIFFICULTIES,DEFAULT_EXCLUDED);
 private static Map<EntityType<?>,Class<?>> vanillaClasses(){var result=new HashMap<EntityType<?>,Class<?>>();for(var f:EntityType.class.getFields())if(f.getType()==EntityType.class&&f.getGenericType() instanceof ParameterizedType p&&p.getActualTypeArguments()[0] instanceof Class<?> c&&Entity.class.isAssignableFrom(c))try{result.put((EntityType<?>)f.get(null),c);}catch(IllegalAccessException e){throw new ExceptionInInitializerError(e);}return Map.copyOf(result);}
 private static Predicate<Entity> resolve(String name){var legacy=LEGACY.get(name);if(legacy!=null)return legacy::isInstance;var id=ResourceLocation.tryParse(name);if(id==null||!BuiltInRegistries.ENTITY_TYPE.containsKey(id))return null;var type=BuiltInRegistries.ENTITY_TYPE.get(id);var klass=VANILLA_CLASSES.get(type);return klass==null?e->e.getType()==type:klass::isInstance;}
 public ClassicEntityAffectionConfiguration(List<? extends String> declaredDifficulties,List<? extends String> excluded){var rules=new ArrayList<Difficulty>();for(String line:declaredDifficulties){int equals=line.indexOf('=');if(equals<1||equals==line.length()-1)throw new IllegalArgumentException("Entity difficulty requires name=finite nonnegative float");String name=line.substring(0,equals).trim();float value=Float.parseFloat(line.substring(equals+1).trim());if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("finite nonnegative entity difficulty required");var match=resolve(name);if(match!=null)rules.add(new Difficulty(match,value));}difficulties=List.copyOf(rules);var skip=new ArrayList<Predicate<Entity>>();for(String name:excluded){var match=resolve(name);if(match!=null)skip.add(match);}exclusions=List.copyOf(skip);}
 public boolean excluded(Entity entity){return exclusions.stream().anyMatch(rule->rule.test(entity));}
 public float difficulty(Entity entity){return difficulties.stream().filter(rule->rule.matches.test(entity)).findFirst().map(Difficulty::amount).orElse(1F);}
 public static boolean isExcluded(Entity entity){return active.excluded(entity);}
 public static float difficultyOf(Entity entity){return active.difficulty(entity);}
 /** Source EntityAffection's object val is captured once, separately from live Skill booleans. */
 public static void initialize(List<? extends String> difficulties,List<? extends String> excluded){active=new ClassicEntityAffectionConfiguration(difficulties,excluded);}
 public static void reset(){initialize(DEFAULT_DIFFICULTIES,DEFAULT_EXCLUDED);}
 private ClassicEntityAffectionConfiguration() {throw new AssertionError();}
}
