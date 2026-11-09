/* AcademyCraft1.0.7 CPData/AbilityContext consumption adaptation. GPLv3; see NOTICE. */
package cn.academy.port.core;

import java.util.*;

/** Common calculation seam: no Minecraft, event bus, transport, or client dependency. */
public final class SkillConsumption {
    private SkillConsumption() {}
    /** Source SkillPerform is mutable and noncancelable. A skill id is modern diagnostic metadata only. */
    public static final class Request {
        public final String skill;
        public final boolean force, creative;
        public float cp, overload;
        private final double initialCp, initialOverload;
        private final int cpBits, overloadBits;
        public Request(String skill,double cp,double overload,boolean force,boolean creative) {
            this.skill=skill==null?"":skill;this.force=force;this.creative=creative;
            initialCp=cp;initialOverload=overload;this.cp=(float)cp;this.overload=(float)overload;
            cpBits=Float.floatToRawIntBits(this.cp);overloadBits=Float.floatToRawIntBits(this.overload);
        }
        // Preserve the tested double-backed ledger when a default listener leaves its float copy alone.
        public double finalCp(){return Float.floatToRawIntBits(cp)==cpBits?initialCp:cp;}
        public double finalOverload(){return Float.floatToRawIntBits(overload)==overloadBits?initialOverload:overload;}
    }
    @FunctionalInterface public interface Mutation {void post(Request event);}
    @FunctionalInterface public interface Overloaded {void post();}
    @FunctionalInterface public interface Creative {boolean current(boolean requested);}
    public static final Mutation UNMODIFIED=event->{};
    public static final Overloaded NO_OVERLOAD_EVENT=()->{};
    public static boolean valid(double cp,double overload){return Double.isFinite(cp)&&Double.isFinite(overload)&&cp>=0&&overload>=0&&Float.isFinite((float)cp)&&Float.isFinite((float)overload);}

    /** Immutable source configuration snapshot. Optional-path presence matters because of Skill.java's bug. */
    public static final class Config {
        public static final Map<String,Double> SOURCE_DEFAULT_OPTIONS=Map.of("electromaster.arc_gen.damage_scale",1D,"electromaster.arc_gen.cp_consume_speed",1D,"electromaster.arc_gen.overload_consume_speed",1D,"electromaster.arc_gen.exp_incr_speed",1D);
        public static final Config DEFAULT=new Config(15,32,.0025,.0058,1,1,1,
                ClassicRules.BASE_CP,ClassicRules.BASE_OVERLOAD,ClassicRules.EXTRA_CP_CAP,ClassicRules.EXTRA_OVERLOAD_CAP,Map.of(),Map.of());
        public final int cpCooldown,overloadCooldown;
        public final double maxCpIncrease,maxOverloadIncrease,cpRecovery,overloadRecovery,progressIncrease;
        private final double[] baseCp,baseOverload,extraCp,extraOverload;
        private final Map<String,Double> optional,categoryProgress;
        public Config(int cpCooldown,int overloadCooldown,double maxCpIncrease,double maxOverloadIncrease,
                      double cpRecovery,double overloadRecovery,double progressIncrease,double[] baseCp,double[] baseOverload,
                      double[] extraCp,double[] extraOverload,Map<String,Double> optional,Map<String,Double> categoryProgress){
            if(cpCooldown<0||overloadCooldown<0)throw new IllegalArgumentException("Negative recovery cooldown");
            this.cpCooldown=cpCooldown;this.overloadCooldown=overloadCooldown;
            this.maxCpIncrease=number(maxCpIncrease,.0025);this.maxOverloadIncrease=number(maxOverloadIncrease,.0058);
            this.cpRecovery=number(cpRecovery,1);this.overloadRecovery=number(overloadRecovery,1);this.progressIncrease=number(progressIncrease,1);
            this.baseCp=vector(baseCp);this.baseOverload=vector(baseOverload);this.extraCp=vector(extraCp);this.extraOverload=vector(extraOverload);
            var sourceOptions=new HashMap<>(SOURCE_DEFAULT_OPTIONS);sourceOptions.putAll(optional);this.optional=Map.copyOf(sourceOptions);this.categoryProgress=Map.copyOf(categoryProgress);
            for(var entry:this.optional.entrySet())if(!Double.isFinite(entry.getValue())||!Float.isFinite(entry.getValue().floatValue()))throw new IllegalArgumentException("Invalid source option "+entry.getKey());
            for(var entry:this.categoryProgress.entrySet())number(entry.getValue(),1);
        }
        private static double number(double value,double original){if(!Double.isFinite(value)||value<0||!Float.isFinite((float)value))throw new IllegalArgumentException("Invalid classic data value");return value==original?original:(float)value;}
        private static double[] vector(double[] values){if(values.length!=6)throw new IllegalArgumentException("Classic data vectors have six levels");var copy=values.clone();for(int i=0;i<copy.length;i++)copy[i]=(float)number(copy[i],copy[i]);return copy;}
        public double baseCp(int level){return baseCp[ClassicRules.level(level)];}public double baseOverload(int level){return baseOverload[ClassicRules.level(level)];}
        public double extraCpCap(int level){return extraCp[ClassicRules.level(level)];}public double extraOverloadCap(int level){return extraOverload[ClassicRules.level(level)];}
        public double categoryProgress(String category){return number(categoryProgress.getOrDefault(category,1D),1);}
        public boolean has(String category,String skill,String key){return optional.containsKey(category+"."+skill+"."+key);}
        /** The source checks the requested key but reads damage_scale, even for CP/O/EXP. */
        public float optionalFloat(String category,String skill,String requested){
            if(!has(category,skill,requested))return 1;
            var value=optional.get(category+"."+skill+".damage_scale");
            if(value==null)throw new IllegalStateException("Classic Skill.getOptionalFloat requires damage_scale when "+requested+" exists: "+category+"."+skill);
            return value.floatValue();
        }
        public double cp(String category,String skill,double raw){return multiply(raw,optionalFloat(category,skill,"cp_consume_speed"));}
        public double overload(String category,String skill,double raw){return multiply(raw,optionalFloat(category,skill,"overload_consume_speed"));}
        public double damage(String category,String skill,double raw){return multiply(raw,optionalFloat(category,skill,"damage_scale"));}
        public double experience(String category,String skill,double raw){return multiply(raw,optionalFloat(category,skill,"overload_incr_speed"));}
        public double progress(String category,double raw){double a=categoryProgress(category),b=progressIncrease;return a==1&&b==1?raw:(float)raw*(float)a*(float)b;}
        private static double multiply(double raw,float scale){return scale==1?raw:scale*(float)raw;}
        public Config withOptions(Map<String,Double> values){return new Config(cpCooldown,overloadCooldown,maxCpIncrease,maxOverloadIncrease,cpRecovery,overloadRecovery,progressIncrease,baseCp,baseOverload,extraCp,extraOverload,values,categoryProgress);}
        /** Source numeric optional keys, expressed as canonical category.skill.key=value entries. */
        public static Map<String,Double> parseOptions(List<? extends String> entries){
            var result=new LinkedHashMap<String,Double>();
            for(String entry:entries){int equals=entry.indexOf('=');if(equals<1||equals!=entry.lastIndexOf('='))throw new IllegalArgumentException("Expected category.skill.key=value");String key=entry.substring(0,equals).trim();
                if(!key.matches("(generic|electromaster|meltdowner|teleporter|vecmanip)\\.[a-z0-9_]+\\.(damage_scale|cp_consume_speed|overload_consume_speed|overload_incr_speed|exp_incr_speed)"))throw new IllegalArgumentException("Unknown classic numeric option: "+key);
                double value=Double.parseDouble(entry.substring(equals+1).trim());if(!Double.isFinite(value)||!Float.isFinite((float)value)||result.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("Invalid or duplicate option: "+key);
            }return Map.copyOf(result);
        }
    }
}
