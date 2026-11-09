package cn.academy.port;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.SkillPresets;
import cn.academy.port.preset.PresetSkills;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
public final class AbilityStorage {
    private static final String KEY="academy:classic_progress";
    private static final Map<UUID,AbilityProgress> CACHE=new HashMap<>();
    public static AbilityProgress get(ServerPlayer player) {var state=CACHE.computeIfAbsent(player.getUUID(),id->decodeSaved(player.getPersistentData().getCompound(KEY),AcademyConfig.consumptionConfig()));if(!state.consumptionInProgress())AbilityConsumption.bind(player,state);cn.academy.port.achievements.ClassicAchievements.bind(player,state);return state;}
    static AbilityProgress cached(ServerPlayer player){return CACHE.get(player.getUUID());}
    /** Existing server-owned state only: no allocation, callback binding, persistence or gameplay work. */
    public static AbilityProgress peek(ServerPlayer player){return player==null?null:CACHE.get(player.getUUID());}
    public static void save(ServerPlayer player) {player.getPersistentData().put(KEY,encodeSaved(get(player)));}
    public static void remove(ServerPlayer player) {save(player);CACHE.remove(player.getUUID());}
    public static CompoundTag encode(AbilityProgress s) {
        var t=new CompoundTag();t.putInt("schema",3);t.putString("category",s.category);t.putInt("level",s.level);t.putBoolean("active",s.activated);t.putBoolean("overload_fine",s.overloadFine);
        t.putDouble("cp",s.cp);t.putDouble("overload",s.overload);t.putDouble("extra_cp",s.extraCp);t.putDouble("extra_overload",s.extraOverload);t.putDouble("level_exp",s.levelExperience);t.putInt("cp_delay",s.cpDelay);t.putInt("overload_delay",s.overloadDelay);
        t.putDouble("raw_max_cp",s.baseCp());t.putDouble("raw_max_overload",s.baseOverload());
        var exps=new CompoundTag();s.experience.forEach(exps::putDouble);t.put("skills",exps);var retained=new CompoundTag();s.unlearnedExperience.forEach(retained::putDouble);t.put("unlearned_skill_exp",retained);var cds=new CompoundTag();s.cooldowns.forEach(cds::putInt);t.put("cooldowns",cds);var maxima=new CompoundTag();s.cooldowns.forEach((id,ticks)->maxima.putInt(id,s.cooldownMaximum(id)));t.put("cooldown_max",maxima);
        var presets=new CompoundTag();presets.putInt("current",s.presets.current());presets.putLong("revision",s.presets.revision());
        for(int preset=0;preset<SkillPresets.MAX_PRESETS;preset++){var mapping=new CompoundTag();for(int slot=0;slot<SkillPresets.MAX_KEYS;slot++){String skill=s.presets.skill(preset,slot);if(!skill.isEmpty())mapping.putString(Integer.toString(slot),skill);}presets.put(Integer.toString(preset),mapping);}
        t.put("presets",presets);return t;
    }
    /** CooldownData never calls DataPart.setNBTStorage: cooldowns belong to the live sync snapshot only. */
    public static CompoundTag encodeSaved(AbilityProgress s){var tag=encode(s);tag.remove("cooldowns");tag.remove("cooldown_max");return tag;}
    /** Ignore cooldown fields in older port saves as well; live network snapshots use decode directly. */
    public static AbilityProgress decodeSaved(CompoundTag tag,cn.academy.port.core.SkillConsumption.Config config){var copy=tag.copy();copy.remove("cooldowns");copy.remove("cooldown_max");return decode(copy,config);}
    public static AbilityProgress decode(CompoundTag t){return decode(t,cn.academy.port.core.SkillConsumption.Config.DEFAULT);}
    public static AbilityProgress decode(CompoundTag t,cn.academy.port.core.SkillConsumption.Config config) {
        var s=new AbilityProgress();s.bindConsumption(()->config,cn.academy.port.core.SkillConsumption.UNMODIFIED,cn.academy.port.core.SkillConsumption.NO_OVERLOAD_EVENT,()->true);if(!t.contains("schema"))return s;
        String cat=t.getString("category");if(cn.academy.port.core.ClassicSkillConfiguration.CATEGORIES.contains(cat))s.category=cat;
        s.level=t.getInt("level");s.activated=t.getBoolean("active");s.overloadFine=t.getBoolean("overload_fine");s.cp=t.getDouble("cp");s.overload=t.getDouble("overload");s.extraCp=t.getDouble("extra_cp");s.extraOverload=t.getDouble("extra_overload");s.levelExperience=t.getDouble("level_exp");s.cpDelay=t.getInt("cp_delay");s.overloadDelay=t.getInt("overload_delay");
        var exps=t.getCompound("skills");for(var id:exps.getAllKeys())if(SkillCatalog.find(s.category,id).isPresent())s.experience.put(id,exps.getDouble(id));
        var retained=t.getCompound("unlearned_skill_exp");for(var id:retained.getAllKeys())if(!s.learned(id)&&SkillCatalog.find(s.category,id).isPresent())s.unlearnedExperience.put(id,retained.getDouble(id));
        if(s.level>=0&&s.level<=5&&t.contains("raw_max_cp",net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)&&t.contains("raw_max_overload",net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)&&cn.academy.port.core.SkillConsumption.valid(t.getDouble("raw_max_cp"),t.getDouble("raw_max_overload")))s.restoreCalculatedMaxima(t.getDouble("raw_max_cp"),t.getDouble("raw_max_overload"));
        else s.restoreLegacyConfiguredMaxima();
        var cds=t.getCompound("cooldowns");for(var id:cds.getAllKeys())if(PresetSkills.registeredMapping(s,id))s.cooldowns.put(id,cds.getInt(id));var maxima=t.getCompound("cooldown_max");for(var id:s.cooldowns.keySet())s.cooldownMaxTicks.put(id,Math.max(s.cooldowns.get(id),maxima.getInt(id)));s.sanitize();
        var presets=t.getCompound("presets");var saved=new String[SkillPresets.MAX_PRESETS][SkillPresets.MAX_KEYS];
        for(int preset=0;preset<SkillPresets.MAX_PRESETS;preset++)for(int slot=0;slot<SkillPresets.MAX_KEYS;slot++)saved[preset][slot]=presets.getCompound(Integer.toString(preset)).getString(Integer.toString(slot));
        s.presets.restore(presets.getInt("current"),presets.getLong("revision"),saved,id->PresetSkills.registeredMapping(s,id));return s;
    }
    /** Original EntityData clone transfers its live parts; category/CP/presets alone enter disk NBT. */
    public static void clone(ServerPlayer original,ServerPlayer replacement) {var source=get(original);var copy=decode(encode(source),source.cpDataConfig());replacement.getPersistentData().put(KEY,encodeSaved(copy));CACHE.remove(original.getUUID());CACHE.put(replacement.getUUID(),copy);}
    public static void clear() {CACHE.clear();}
}
