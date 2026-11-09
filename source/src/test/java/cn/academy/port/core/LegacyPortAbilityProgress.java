/* AcademyCraft classic CP/progression rules adapted from CPData/AbilityData. See NOTICE. */
package cn.academy.port.core;

import java.util.HashMap;
import java.util.Map;

public final class LegacyPortAbilityProgress {
    public String category="";
    public int level;
    public boolean activated, overloadFine=true, interfering;
    public double cp=1800, overload, extraCp, extraOverload, levelExperience;
    public int cpDelay, overloadDelay;
    public final SkillPresets presets=new SkillPresets();
    public final Map<String,Double> experience=new HashMap<>();
    public final Map<String,Integer> cooldowns=new HashMap<>();
    public final Map<String,Integer> cooldownMaxTicks=new HashMap<>();
    /** Classic CooldownData preserves the greatest max/remaining ticks while an existing cooldown is alive. */
    public void setCooldown(String id,int ticks) {
        if(id==null||!learned(id)||ticks<=0)return;ticks=Math.min(12000,ticks);
        int previous=cooldowns.getOrDefault(id,0),maximum=previous>0?cooldownMaxTicks.getOrDefault(id,previous):0;
        cooldowns.put(id,Math.max(previous,ticks));cooldownMaxTicks.put(id,Math.max(maximum,ticks));
    }
    public int cooldownMaximum(String id) {return Math.max(1,Math.max(cooldowns.getOrDefault(id,0),cooldownMaxTicks.getOrDefault(id,0)));}
    public void tickCooldowns() {cooldowns.replaceAll((id,ticks)->Math.max(0,ticks-1));cooldowns.values().removeIf(ticks->ticks==0);cooldownMaxTicks.keySet().retainAll(cooldowns.keySet());}
    public boolean hasCategory() { return !category.isEmpty(); }
    public double baseCp() { return ClassicRules.BASE_CP[ClassicRules.level(level)]+(learned("brain_course")?1000:0)+(learned("brain_course_advanced")?1500:0); }
    public double baseOverload() { return ClassicRules.BASE_OVERLOAD[ClassicRules.level(level)]+(learned("brain_course_advanced")?100:0); }
    public double maxCp() { return baseCp()+extraCp; }
    public double maxOverload() { return baseOverload()+extraOverload; }
    public double exp(String skill) { if(category.equals("meltdowner")&&skill.equals("rad_intensify"))return ClassicRules.clamp(maxCp()/(ClassicRules.BASE_CP[5]+(learned("brain_course")?1000:0)+(learned("brain_course_advanced")?1500:0)),0,1);return experience.getOrDefault(skill,0.0); }
    public boolean learned(String skill) { return experience.containsKey(skill); }
    public boolean canUse(String skill) { return hasCategory() && activated && overloadFine && !interfering && learned(skill) && cooldowns.getOrDefault(skill,0)==0; }
    public void selectCategory(String value) { category=value; presets.clear(); level=0; experience.clear(); cooldowns.clear(); cooldownMaxTicks.clear(); levelExperience=0; recalculate(true); activated=false; }
    public void learn(String id) { if(!learned(id)) { experience.put(id,0.0); recalculate(false); } }
    public void setLevel(int value) { level=ClassicRules.level(value); levelExperience=0; recalculate(true); }
    public void recoverAll() { cp=maxCp();overload=0;overloadFine=false; }
    private void recalculate(boolean resetTraining) { if(resetTraining) {extraCp=0;extraOverload=0;} cp=maxCp();overload=0; }
    public double levelProgress(int count) { double threshold=ClassicRules.levelThreshold(level,count);return threshold==0?1:Math.min(1,levelExperience/threshold); }
    public boolean canLevelUp(int count) { return hasCategory() && level<5 && levelProgress(count)>=1; }
    public boolean consume(double cost,double strain,boolean creative) {
        if(!Double.isFinite(cost)||!Double.isFinite(strain)||cost<0||strain<0) return false;
        if(!creative) {
            if(cp<cost) return false;
            cp=Math.max(0,cp-cost);cpDelay=15;
            overload=Math.min(maxOverload(),overload+strain);overloadDelay=32;
            if(overload>=maxOverload()) overloadFine=false;
        }
        extraCp=Math.min(ClassicRules.EXTRA_CP_CAP[level],extraCp+cost*0.0025);
        extraOverload=Math.min(ClassicRules.EXTRA_OVERLOAD_CAP[level],extraOverload+ClassicRules.clamp(strain*0.0058,0,10));
        return true;
    }
    public void addExperience(String id,double raw) { if(!Double.isFinite(raw)||raw<0||!learned(id)) return;experience.put(id,Math.min(1,exp(id)+raw));levelExperience+=raw; }
    public void tick() {
        tickCooldowns();
        if(!hasCategory()) return;
        if(cpDelay>0) cpDelay--; else cp=Math.min(maxCp(),cp+ClassicRules.cpRecovery(cp,baseCp())*(learned("mind_course")?1.2:1));
        if(overloadDelay>0) overloadDelay--; else {overload=Math.max(0,overload-ClassicRules.overloadRecovery(overload,baseOverload()));if(overload==0)overloadFine=true;}
    }
    public void sanitize() {
        level=ClassicRules.level(level);extraCp=ClassicRules.clamp(extraCp,0,ClassicRules.EXTRA_CP_CAP[level]);extraOverload=ClassicRules.clamp(extraOverload,0,ClassicRules.EXTRA_OVERLOAD_CAP[level]);
        cp=ClassicRules.clamp(cp,0,maxCp());overload=ClassicRules.clamp(overload,0,maxOverload());levelExperience=ClassicRules.clamp(levelExperience,0,10000);
        cpDelay=Math.max(0,Math.min(15,cpDelay));overloadDelay=Math.max(0,Math.min(32,overloadDelay));
        experience.replaceAll((id,value)->ClassicRules.clamp(value,0,1));cooldowns.replaceAll((id,value)->Math.max(0,Math.min(12000,value)));cooldownMaxTicks.keySet().retainAll(cooldowns.keySet());cooldownMaxTicks.replaceAll((id,value)->Math.max(cooldowns.getOrDefault(id,0),Math.max(1,Math.min(12000,value))));
    }
}
