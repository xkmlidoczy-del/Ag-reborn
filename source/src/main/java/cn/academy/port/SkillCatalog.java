package cn.academy.port;
import cn.academy.port.core.AbilityProgress;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class SkillCatalog {
    public record Requirement(String id,double exp) {}
    public record Skill(String category,String id,int level,boolean controllable,List<Requirement> requirements,int anyLearnedSkillLevel) {}
    /** Original source-derived records, retained for source oracles and classic progression. */
    public static final List<Skill> ALL=load();
    public static final List<Skill> REGISTERED=java.util.stream.Stream.concat(java.util.stream.Stream.concat(ALL.stream(),cn.academy.port.highpower.HighPowerSkills.ALL.stream()),cn.academy.port.canon.CanonSkills.ALL.stream()).toList();
    public static final Set<cn.academy.port.core.ClassicSkillConfiguration.Key> CONFIG_KEYS=ALL.stream().map(s->cn.academy.port.core.ClassicSkillConfiguration.key(s.category,s.id)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    public static final Set<cn.academy.port.core.ClassicSkillConfiguration.Key> REGISTERED_CONFIG_KEYS=REGISTERED.stream().map(s->cn.academy.port.core.ClassicSkillConfiguration.key(s.category,s.id)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static volatile cn.academy.port.core.ClassicSkillConfiguration configuration=new cn.academy.port.core.ClassicSkillConfiguration(REGISTERED_CONFIG_KEYS,Map.of());
    public static cn.academy.port.core.ClassicSkillConfiguration configuration(){return configuration;}
    public static void initializeConfiguration(Map<cn.academy.port.core.ClassicSkillConfiguration.Key,cn.academy.port.core.ClassicSkillConfiguration.Flags> flags){configuration=new cn.academy.port.core.ClassicSkillConfiguration(REGISTERED_CONFIG_KEYS,flags);}
    public static void refreshConfiguration(Map<cn.academy.port.core.ClassicSkillConfiguration.Key,cn.academy.port.core.ClassicSkillConfiguration.Flags> flags){configuration.replaceLive(flags);}
    public static void resetConfiguration(){initializeConfiguration(Map.of());}
    public static boolean enabled(String category,String id){if(cn.academy.port.canon.CanonSkills.knownSkill(id)&&!cn.academy.port.canon.CanonSkills.EXPERIMENTAL)return false;return configuration.enabled(category,id);}
    public static List<Requirement> requirements(Skill skill){var config=configuration;if(cn.academy.port.highpower.HighPowerSkills.contains(skill.id))return skill.requirements;return skill.requirements.stream().filter(r->config.dependencyPresent(skill.category,r.id)).toList();}
    /** Server captures relationships once; publish that graph as well as live getters to remote clients. */
    public static net.minecraft.nbt.CompoundTag configurationSnapshot(){var config=configuration;var tag=new net.minecraft.nbt.CompoundTag();config.liveSnapshot().forEach((key,flags)->{var entry=new net.minecraft.nbt.CompoundTag();entry.putBoolean("enabled",flags.enabled());entry.putBoolean("destroy_blocks",flags.destroyBlocks());entry.putBoolean("startup_enabled",config.startupSnapshot().get(key));tag.put(key.location()+"."+key.skill(),entry);});return tag;}
    public static boolean applyConfigurationSnapshot(net.minecraft.nbt.CompoundTag tag){if(tag==null)return false;var live=new LinkedHashMap<cn.academy.port.core.ClassicSkillConfiguration.Key,cn.academy.port.core.ClassicSkillConfiguration.Flags>();var startup=new LinkedHashMap<cn.academy.port.core.ClassicSkillConfiguration.Key,cn.academy.port.core.ClassicSkillConfiguration.Flags>();for(var key:REGISTERED_CONFIG_KEYS){String name=key.location()+"."+key.skill();if(!tag.contains(name,net.minecraft.nbt.Tag.TAG_COMPOUND))return false;var e=tag.getCompound(name);if(!e.contains("enabled",net.minecraft.nbt.Tag.TAG_BYTE)||!e.contains("destroy_blocks",net.minecraft.nbt.Tag.TAG_BYTE)||!e.contains("startup_enabled",net.minecraft.nbt.Tag.TAG_BYTE))return false;live.put(key,new cn.academy.port.core.ClassicSkillConfiguration.Flags(e.getBoolean("enabled"),e.getBoolean("destroy_blocks")));startup.put(key,new cn.academy.port.core.ClassicSkillConfiguration.Flags(e.getBoolean("startup_enabled"),true));}if(!configuration.startupSnapshot().equals(startup.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().enabled())))){var replacement=new cn.academy.port.core.ClassicSkillConfiguration(REGISTERED_CONFIG_KEYS,startup);replacement.replaceLive(live);configuration=replacement;}else configuration.replaceLive(live);return true;}
    private static List<Skill> load() {
        try(var stream=Objects.requireNonNull(SkillCatalog.class.getResourceAsStream("/classic-skills.json"));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var skills=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("skills");var list=new ArrayList<Skill>();
            for(var element:skills) {var s=element.getAsJsonObject();var reqs=new ArrayList<Requirement>();for(var r:s.getAsJsonArray("requirements")) {var o=r.getAsJsonObject();reqs.add(new Requirement(o.get("id").getAsString(),o.get("minimum_exp").getAsDouble()));}
                list.add(new Skill(s.get("category").getAsString(),s.get("id").getAsString(),s.get("level").getAsInt(),s.get("controllable").getAsBoolean(),List.copyOf(reqs),s.has("any_learned_skill_of_level")?s.get("any_learned_skill_of_level").getAsInt():0));}
            return List.copyOf(list);
        } catch(IOException e) {throw new ExceptionInInitializerError(e);}
    }
    public static Optional<Skill> find(String category,String id) {return REGISTERED.stream().filter(s->s.category.equals(category)&&s.id.equals(id)).findFirst().or(()->cn.academy.port.canon.CanonSkills.findKnown(category,id));}
    public static int levelSkillCount(AbilityProgress state) {if(cn.academy.port.canon.CanonSkills.knownCategory(state.category))return 1;return (int)ALL.stream().filter(s->s.category.equals(state.category)&&s.level==state.level&&s.controllable&&enabled(s.category,s.id)).count();}
    public static boolean canLearn(AbilityProgress state,Skill skill) {return (!cn.academy.port.canon.CanonSkills.knownSkill(skill.id)||cn.academy.port.canon.CanonSkills.EXPERIMENTAL)&&(!cn.academy.port.highpower.HighPowerSkills.contains(skill.id)||enabled(skill.category,skill.id))&&state.category.equals(skill.category)&&state.level>=skill.level&&!state.learned(skill.id)&&requirements(skill).stream().allMatch(r->state.learned(r.id)&&(float)state.exp(r.id)>=(float)r.exp)&&(skill.anyLearnedSkillLevel==0||ALL.stream().anyMatch(other->other.category.equals(state.category)&&other.level==skill.anyLearnedSkillLevel&&state.learned(other.id)));}
}
