/* AcademyCraft classic CP/progression rules adapted from CPData/AbilityData. See NOTICE. */
package cn.academy.port.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class AbilityProgress {
    private Supplier<SkillConsumption.Config> consumptionConfig=()->SkillConsumption.Config.DEFAULT;
    private SkillConsumption.Config dataConfig;
    /** CPData.wake captures its immutable data subtree; skill/progression options stay live. */
    public SkillConsumption.Config cpDataConfig(){return dataConfig==null?SkillConsumption.Config.DEFAULT:dataConfig;}
    private SkillConsumption.Mutation consumptionMutation=SkillConsumption.UNMODIFIED;
    private SkillConsumption.Overloaded overloadEvent=SkillConsumption.NO_OVERLOAD_EVENT;
    private BooleanSupplier consumptionValidity=()->true;
    private SkillConsumption.Creative creativeMode=requested->requested;
    private int consumptionDepth;
    /** Transient hooks are never persisted. Direct core reentry retains source ordering; wire ingress sees busy. */
    public void bindConsumption(Supplier<SkillConsumption.Config> config,SkillConsumption.Mutation mutation,
                                SkillConsumption.Overloaded overloaded,BooleanSupplier validity) {
        consumptionConfig=Objects.requireNonNull(config);if(dataConfig==null)dataConfig=Objects.requireNonNull(config.get());consumptionMutation=Objects.requireNonNull(mutation);
        overloadEvent=Objects.requireNonNull(overloaded);consumptionValidity=Objects.requireNonNull(validity);creativeMode=requested->requested;
    }
    public void bindCreativeMode(SkillConsumption.Creative resolver){creativeMode=Objects.requireNonNull(resolver);}
    public SkillConsumption.Config consumptionConfig(){return Objects.requireNonNull(consumptionConfig.get());}
    public boolean consumptionInProgress(){return consumptionDepth!=0;}

    private AbilityCalculation.Mutation calculation=request->ClassicPassiveSkills.calculate(this,request);
    private boolean calculating;
    private double calculatedCp=100,calculatedOverload=100;
    /** Binding the calculation bus is wake plumbing and never recalculates cached maxima. */
    public void bindCalculations(AbilityCalculation.Mutation mutation) {calculation=Objects.requireNonNull(mutation);}
    private double calculate(AbilityCalculation.Kind kind,double initial){
        var request=new AbilityCalculation.Request(kind,initial);calculation.post(request);return finiteLedger((float)request.result());
    }
    /** Only source category/learn/level handlers recalculate raw maxima; getters retain the cache. */
    private void calculateMaxima(){
        if(calculating)return;
        var config=cpDataConfig();calculating=true;
        try{calculatedCp=calculate(AbilityCalculation.Kind.MAX_CP,config.baseCp(level));calculatedOverload=calculate(AbilityCalculation.Kind.MAX_OVERLOAD,config.baseOverload(level));}
        finally{calculating=false;}
    }
    public double initCp(int requestedLevel){return calculate(AbilityCalculation.Kind.MAX_CP,cpDataConfig().baseCp(requestedLevel));}
    /** CPData persists raw calculated maxima, including extension mutations, separately from training. */
    public void restoreCalculatedMaxima(double cpMaximum,double overloadMaximum){
        if(level<0||level>5||!SkillConsumption.valid(cpMaximum,overloadMaximum))return;
        calculatedCp=cpMaximum;calculatedOverload=overloadMaximum;
    }
    /** Port-save migration only: old/malformed raw-max fields use configured values without refilling CP. */
    public void restoreLegacyConfiguredMaxima(){calculateMaxima();}
    public String category="";
    public int level;
    public boolean activated, overloadFine=true, interfering;
    public double cp, overload, extraCp, extraOverload, levelExperience;
    public int cpDelay, overloadDelay;
    public final SkillPresets presets=new SkillPresets();
    public final Map<String,Double> experience=new HashMap<>();
    /** Classic AbilityData retains skillExps when the separate learned bit is cleared. */
    public final Map<String,Double> unlearnedExperience=new HashMap<>();
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
    private final cn.academy.port.interferer.ClassicInterferenceSources interferenceSources=new cn.academy.port.interferer.ClassicInterferenceSources();
    public void addInterference(String id,BooleanSupplier source){interferenceSources.add(id,source);}
    public boolean hasInterference(String id){return interferenceSources.contains(id);}
    public int interferenceSourceCount(){return interferenceSources.size();}
    public boolean hasCategory() { return !category.isEmpty(); }
    private java.util.function.Consumer<Boolean> activationChanged=value->{};
    /** Server CPData.setActivateState posts only on a real change, after mutating the raw flag. */
    public void bindActivationEvents(java.util.function.Consumer<Boolean> changed){activationChanged=Objects.requireNonNull(changed);}
    public boolean isActivated(){return hasCategory()&&activated;}
    public void setActivateState(boolean value){
        if(value&&!hasCategory())throw new IllegalStateException("Trying to activate ability when player doesn't have one");
        if(activated!=value){activated=value;activationChanged.accept(value);}
    }
    public double baseCp() { return calculatedCp; }
    public double baseOverload() { return calculatedOverload; }
    public double maxCp() { return maximum(baseCp(),extraCp); }
    public double maxOverload() { return maximum(baseOverload(),extraOverload); }
    public double exp(String skill) { if(category.equals("meltdowner")&&skill.equals("rad_intensify"))return ClassicPassiveSkills.radiationMastery((float)baseCp()+(float)extraCp,initCp(5));return experience.getOrDefault(skill,unlearnedExperience.getOrDefault(skill,0.0)); }
    public boolean learned(String skill) { return experience.containsKey(skill); }
    public boolean canUse(String skill) { return !consumptionInProgress() && hasCategory() && activated && overloadFine && !interfering && learned(skill) && cooldowns.getOrDefault(skill,0)==0; }
    public void selectCategory(String value) { beforeEarnedMutation.run();earnedMutationRevision++;category=value; presets.clear(); level=0; experience.clear(); unlearnedExperience.clear(); cooldowns.clear(); cooldownMaxTicks.clear(); levelExperience=0; recalculate(true); setActivateState(false); }
    private int categoryChangeDepth;
    private java.util.function.Consumer<Runnable> categoryChanged=Runnable::run;
    /** Synchronous CategoryChangeEvent dispatch; its common handler must run the supplied effects once. */
    public void bindCategoryChanges(java.util.function.Consumer<Runnable> changed){categoryChanged=Objects.requireNonNull(changed);}
    /** AbilityData.setCategory: category/slots change before the event; its CP handler retains training/progress. */
    public void changeCategoryClassic(String value){
        Objects.requireNonNull(value);if(category.equals(value))return;
        beforeEarnedMutation.run();earnedMutationRevision++;
        category=value;level=value.isEmpty()?0:level==0?1:level;experience.clear();unlearnedExperience.clear();
        categoryChangeDepth++;
        try{categoryChanged.accept(()->{cooldowns.clear();cooldownMaxTicks.clear();if(category.isEmpty())setActivateState(false);recalculate(false);presets.clear();});}
        finally{categoryChangeDepth--;}
    }
    private java.util.function.BiConsumer<String,Runnable> skillLearned=(id,effects)->effects.run();
    private java.util.function.BiConsumer<Integer,Runnable> levelChanged=(value,effects)->effects.run();
    public record ExperienceAward(String category,String skill,float amount){}
    private Runnable beforeEarnedMutation=()->{};
    private java.util.function.Consumer<ExperienceReceipt> earnedExperience=receipt->{};
    private long earnedMutationRevision;
    /** Additive observer; the original primitive math and Changed/Added event order are unchanged. */
    public void bindEarnedProgress(Runnable beforeMutation,java.util.function.Consumer<ExperienceReceipt> observer){
        beforeEarnedMutation=Objects.requireNonNull(beforeMutation);earnedExperience=Objects.requireNonNull(observer);
    }
    /** Closed, one-use provenance for the actual native float progression delta. */
    public static final class ExperienceReceipt {
        private final AbilityProgress owner; private final String category; private final int level,count;
        private final float before,after; private final long revision; private boolean open,claimed;
        private ExperienceReceipt(AbilityProgress owner,String category,int level,int count,float before,float after,long revision){
            this.owner=owner;this.category=category;this.level=level;this.count=count;this.before=before;this.after=after;this.revision=revision;
        }
        public String category(){return category;} public int level(){return level;} public int count(){return count;}
        public float before(){return before;} public float after(){return after;}
        public boolean claim(AbilityProgress expected){
            if(!open||claimed||owner!=expected)return false;claimed=true;
            return revision==owner.earnedMutationRevision&&category.equals(owner.category)&&level==owner.level
                    &&Float.isFinite(before)&&Float.isFinite(after)&&before>=0&&after>before&&count>0;
        }
    }
    private java.util.function.Consumer<ExperienceAward> skillExpChanged=award->{};
    private java.util.function.Consumer<ExperienceAward> skillExpAdded=award->{};
    /** Compatibility observers run after the default source common effects. Native dispatch uses bindProgressEvents. */
    public void bindProgress(java.util.function.Consumer<String> learned,java.util.function.IntConsumer level){
        Objects.requireNonNull(learned);Objects.requireNonNull(level);
        skillLearned=(id,effects)->{effects.run();learned.accept(id);};levelChanged=(value,effects)->{effects.run();level.accept(value);};
    }
    /** Primitive mutations precede synchronous events; each event owns its once-only CPData NORMAL effects. */
    public void bindProgressEvents(java.util.function.BiConsumer<String,Runnable> learned,
                                  java.util.function.BiConsumer<Integer,Runnable> level,
                                  java.util.function.Consumer<ExperienceAward> changed,
                                  java.util.function.Consumer<ExperienceAward> added){
        skillLearned=Objects.requireNonNull(learned);levelChanged=Objects.requireNonNull(level);
        skillExpChanged=Objects.requireNonNull(changed);skillExpAdded=Objects.requireNonNull(added);
    }
    private static Runnable once(Runnable effects){return new Runnable(){private boolean applied;public void run(){if(!applied){applied=true;effects.run();}}};}
    private void checkSkill(String id){if(id==null||!hasCategory()||cn.academy.port.SkillCatalog.find(category,id).isEmpty())throw new IllegalStateException("Skill "+id+" not in category "+category);}
    public void applySkillLearnEffects(){recalculate(false);}
    public void applyLevelChangeEffects(){recalculate(true);}
    public void learn(String id) {
        checkSkill(id);if(!learned(id)){experience.put(id,unlearnedExperience.getOrDefault(id,0.0));unlearnedExperience.remove(id);skillLearned.accept(id,once(this::applySkillLearnEffects));}
    }
    /** Source unlearning does not emit SkillLearnEvent or recalculate cached CPData maxima. */
    public void unlearn(String id){if(!learned(id))return;double rawCp=baseCp(),rawOverload=baseOverload();unlearnedExperience.put(id,experience.remove(id));restoreCalculatedMaxima(rawCp,rawOverload);}
    /** AbilityData.learnAllSkills sets the learned bitset directly, with no SkillLearnEvent/CP recalculation. */
    public void learnAllClassic(Iterable<String> ids){double rawCp=baseCp(),rawOverload=baseOverload();for(String id:ids)if(!learned(id)){experience.put(id,unlearnedExperience.getOrDefault(id,0.0));unlearnedExperience.remove(id);}restoreCalculatedMaxima(rawCp,rawOverload);}
    public void setLevel(int value) {
        if(!hasCategory())throw new IllegalStateException("Player doesn't have category");
        int next=ClassicRules.level(value);if(level==next)return;
        beforeEarnedMutation.run();earnedMutationRevision++;
        level=next;levelExperience=0;levelChanged.accept(level,once(this::applyLevelChangeEffects));
    }
    public void recoverAll() { cp=maxCp();overload=0;overloadFine=false; }
    private void recalculate(boolean resetTraining) { if(resetTraining) {extraCp=0;extraOverload=0;} calculateMaxima();cp=maxCp();overload=0; }
    public double levelProgress(int count) { float threshold=(float)ClassicRules.levelThreshold(level,count);return threshold==0?1:Math.min(1f,(float)levelExperience/threshold); }
    public boolean canLevelUp(int count) { return hasCategory() && level<5 && levelProgress(count)>=1; }
    /** CPData.canPerform uses raw cost and never posts SkillPerform or applies skill scales. */
    public boolean canPerform(double cost,boolean creative){return creative||(float)cp>=(float)cost;}
    public boolean consume(double cost,double strain,boolean creative){return perform("",cost,strain,creative,false,()->true);}
    public boolean consumeSkill(String skill,double cost,double strain,boolean creative){return perform(skill,cost,strain,creative,false,()->true);}
    /** Source AbilityContext.consumeWithForce bypasses per-skill CP/O scales, but still posts SkillPerform. */
    public boolean consumeWithForceSkill(String skill,double cost,double strain,boolean creative){return perform(skill,cost,strain,creative,true,()->true);}
    public boolean consumeSkill(String skill,double cost,double strain,boolean creative,BooleanSupplier operationValid){return perform(skill,cost,strain,creative,false,operationValid);}
    public boolean consumeWithForceSkill(String skill,double cost,double strain,boolean creative,BooleanSupplier operationValid){return perform(skill,cost,strain,creative,true,operationValid);}
    private boolean perform(String skill,double cost,double strain,boolean creative,boolean force,BooleanSupplier operationValid) {
        if(!SkillConsumption.valid(cost,strain))return false;
        var config=consumptionConfig();var data=cpDataConfig();var mutation=consumptionMutation;var overloaded=overloadEvent;var valid=consumptionValidity;var liveCreative=creativeMode;
        String beforeCategory=category;int beforeLevel=level;boolean wasLearned=skill!=null&&!skill.isEmpty()&&learned(skill);
        if(!force&&skill!=null&&!skill.isEmpty()){cost=config.cp(category,skill,cost);strain=config.overload(category,skill,strain);}
        var request=new SkillConsumption.Request(skill,(float)cost,(float)strain,force,creative);
        consumptionDepth++;
        try {
            mutation.post(request); // Always before insufficient-CP and creative branches.
            cost=(float)request.finalCp();strain=(float)request.finalOverload();creative=liveCreative.current(creative);
            if(!SkillConsumption.valid(cost,strain)||!valid.getAsBoolean()||!operationValid.getAsBoolean()||!category.equals(beforeCategory)||level!=beforeLevel||wasLearned&&!learned(skill))return false;
            if(!creative) {
                if(!force&&(float)cp<(float)cost)return false;
                cp=Math.max(0f,(float)cp-(float)cost);cpDelay=data.cpCooldown;
                overload=Math.min((float)maxOverload(),(float)overload+(float)strain);overloadDelay=data.overloadCooldown;
                if(overload==maxOverload()){overloaded.post();overloadFine=false;}
            }
            // Creative and force train using the full event-mutated costs, even beyond available CP.
            extraCp=Math.min((float)data.extraCpCap(level),(float)extraCp+(float)cost*(float)data.maxCpIncrease);
            extraOverload=Math.min((float)data.extraOverloadCap(level),(float)extraOverload+Math.max(0f,Math.min(10f,(float)strain*(float)data.maxOverloadIncrease)));
            return true;
        } finally {consumptionDepth--;}
    }
    /**
     * Pack-only, closed native-energy transaction. Classic perform above is unchanged.
     * The caller supplies a fully planned, deterministic native write and its read-only
     * identity/snapshot validation. Unknown receivers must never enter this seam.
     * All mutable hooks run before either ledger is changed. Unexpected cost mutations,
     * creative bypass, would-overload, zero float debit and reentry fail closed.
     * The write commits the energy field before native dirty notifications; an unexpected
     * thrown write is not retried or "refunded", because its mutation may already exist.
     */
    public boolean consumeKnownEnergy(String skill, double rawCost, double rawStrain,
                                     BooleanSupplier operationValid, Runnable nativeWrite, Runnable awarded) {
        Objects.requireNonNull(operationValid); Objects.requireNonNull(nativeWrite); Objects.requireNonNull(awarded);
        if (consumptionInProgress() || !SkillConsumption.valid(rawCost, rawStrain) || rawCost <= 0
                || !canUse(skill) || creativeMode.current(false)) return false;
        var config = consumptionConfig(); var data = cpDataConfig();
        float cost = (float) config.cp(category, skill, rawCost);
        float strain = (float) config.overload(category, skill, rawStrain);
        if (!SkillConsumption.valid(cost, strain) || cost <= 0) return false;
        String oldCategory = category; int oldLevel = level;
        double oldCp = cp, oldOverload = overload, oldExtraCp = extraCp, oldExtraOverload = extraOverload;
        int oldCpDelay = cpDelay, oldOverloadDelay = overloadDelay;
        var request = new SkillConsumption.Request(skill, cost, strain, false, false);
        consumptionDepth++;
        try {
            consumptionMutation.post(request);
            if (Float.floatToRawIntBits(request.cp) != Float.floatToRawIntBits(cost)
                    || Float.floatToRawIntBits(request.overload) != Float.floatToRawIntBits(strain)
                    || creativeMode.current(false) || !consumptionValidity.getAsBoolean()
                    || !category.equals(oldCategory) || level != oldLevel || !learned(skill)
                    || !activated || !overloadFine || interfering || cooldowns.getOrDefault(skill, 0) != 0
                    || cp != oldCp || overload != oldOverload || extraCp != oldExtraCp || extraOverload != oldExtraOverload
                    || cpDelay != oldCpDelay || overloadDelay != oldOverloadDelay
                    || !SkillConsumption.valid(cp, overload) || (float) cp < cost) return false;
            float nextCp = Math.max(0f, (float) cp - cost);
            float nextOverload = (float) overload + strain;
            if (!(nextCp < (float) cp) || !Float.isFinite(nextOverload)
                    || !(nextOverload < (float) maxOverload()) || !operationValid.getAsBoolean()) return false;
            cp = nextCp; overload = nextOverload;
            cpDelay = data.cpCooldown; overloadDelay = data.overloadCooldown;
            extraCp = Math.min((float) data.extraCpCap(level), (float) extraCp + cost * (float) data.maxCpIncrease);
            extraOverload = Math.min((float) data.extraOverloadCap(level),
                    (float) extraOverload + Math.max(0f, Math.min(10f, strain * (float) data.maxOverloadIncrease)));
            nativeWrite.run();
            awarded.run();
            return true;
        } finally { consumptionDepth--; }
    }

    /** AbilityData.setSkillExp: learned-only direct raw float, Changed even for equal values. */
    public void setSkillExp(String id,float value){
        checkSkill(id);if(!learned(id))return;
        experience.put(id,(double)value);skillExpChanged.accept(new ExperienceAward(category,id,value));
    }
    public void addExperience(String id,double raw) {
        if(!Double.isFinite(raw)||!Float.isFinite((float)raw)||raw<0)return;
        checkSkill(id);awardExperience(id,consumptionConfig().experience(category,id,raw));
    }
    /** Raw AbilityData-style award bypasses skill scaling and auto-learns without tree prerequisites. */
    public void addExperienceRaw(String id,double raw){awardExperience(id,raw);}
    /** Source passive helper calls the same generic AbilityData.addSkillExp path. */
    public void addPassiveExperienceRaw(String id,float raw){awardExperience(id,raw);}
    private void awardExperience(String id,double raw){
        if(!Double.isFinite(raw)||!Float.isFinite((float)raw)||raw<0)return;
        checkSkill(id);beforeEarnedMutation.run();int receiptLevel=level;long receiptRevision=earnedMutationRevision;
        var award=new ExperienceAward(category,id,(float)raw);learn(id);
        String slot=award.skill();
        // Original skillExps use the award skill's ordinal even if a learn listener changes category.
        if(!category.equals(award.category())){
            // Pack-only skills have no classic ordinal in another category. Never award cross-category XP.
            if(cn.academy.port.highpower.HighPowerSkills.contains(award.skill()))return;
            var original=cn.academy.port.SkillCatalog.ALL.stream().filter(s->s.category().equals(award.category())).toList();
            int ordinal=original.indexOf(cn.academy.port.SkillCatalog.find(award.category(),award.skill()).orElseThrow());
            slot=cn.academy.port.SkillCatalog.ALL.stream().filter(s->s.category().equals(category)).skip(ordinal).findFirst().map(cn.academy.port.SkillCatalog.Skill::id).orElse(award.skill());
        }
        float old=experience.getOrDefault(slot,unlearnedExperience.getOrDefault(slot,0.0)).floatValue(),amount=(float)raw;
        // A synchronous learn listener may unlearn again; the source still updates its independent raw slot.
        var slots=learned(slot)?experience:unlearnedExperience;slots.put(slot,(double)(old+Math.min(1f-old,amount)));
        if(!hasCategory())throw new IllegalStateException("Player doesn't have category");
        float receiptBefore=(float)levelExperience;
        int receiptCount=cn.academy.port.SkillCatalog.levelSkillCount(this);
        levelExperience=finiteLedger((float)levelExperience+(float)consumptionConfig().progress(category,amount));
        var receipt=new ExperienceReceipt(this,award.category(),receiptLevel,receiptCount,receiptBefore,(float)levelExperience,receiptRevision);
        skillExpChanged.accept(award);skillExpAdded.accept(award);
        receipt.open=true;
        try{earnedExperience.accept(receipt);}finally{receipt.open=false;}
    }
    /** Invalid stored operands must remain visible to existing fail-closed session checks. */
    private static double maximum(double raw,double growth){
        if(!Double.isFinite(raw)||raw<0||!Double.isFinite(growth)||growth<0)return Double.NaN;
        return finiteLedger((float)raw+(float)growth);
    }
    /** Finite modern storage protection for otherwise overflowing classic float intermediates. */
    private static float finiteLedger(float value){return Float.isNaN(value)||value<0?0:Math.min(Float.MAX_VALUE,value);}
    public void tick() {
        tickCooldowns();
        if(!hasCategory()) return;
        if(cpDelay>0) cpDelay--; else {
            float raw=ClassicPassiveSkills.cpRecovery(cp,baseCp(),cpDataConfig().cpRecovery,1);
            float recovered=(float)calculate(AbilityCalculation.Kind.CP_RECOVERY,1)*raw;
            if(!Float.isFinite(recovered)||recovered<0)recovered=0;
            cp=Math.min((float)maxCp(),(float)cp+recovered);
        }
        if(overloadDelay>0) overloadDelay--; else {float raw=ClassicPassiveSkills.overloadRecovery(overload,baseOverload(),cpDataConfig().overloadRecovery,1);float recovered=(float)calculate(AbilityCalculation.Kind.OVERLOAD_RECOVERY,1)*raw;if(!Float.isFinite(recovered)||recovered<0)recovered=0;overload=Math.max(0f,(float)overload-recovered);if(overload==0)overloadFine=true;}
        tickInterference();
    }
    public void tickInterference(){if(hasCategory()&&interferenceSources.managed())interfering=interferenceSources.tick();}
    public void sanitize() {
        level=ClassicRules.level(level);double maxSavedCpGrowth=0,maxSavedOverloadGrowth=0;for(int i=0;i<=5;i++){maxSavedCpGrowth=Math.max(maxSavedCpGrowth,cpDataConfig().extraCpCap(i));maxSavedOverloadGrowth=Math.max(maxSavedOverloadGrowth,cpDataConfig().extraOverloadCap(i));}
        // Direct classic category reset retains growth even at level0; reject corrupt values with finite all-level bounds.
        extraCp=ClassicRules.clamp(extraCp,0,maxSavedCpGrowth);extraOverload=ClassicRules.clamp(extraOverload,0,maxSavedOverloadGrowth);
        cp=ClassicRules.clamp(cp,0,maxCp());overload=ClassicRules.clamp(overload,0,maxOverload());levelExperience=ClassicRules.clamp(levelExperience,0,10000);
        cpDelay=Math.max(0,Math.min(cpDataConfig().cpCooldown,cpDelay));overloadDelay=Math.max(0,Math.min(cpDataConfig().overloadCooldown,overloadDelay));
        experience.replaceAll((id,value)->ClassicRules.clamp(value,0,1));cooldowns.replaceAll((id,value)->Math.max(0,Math.min(12000,value)));cooldownMaxTicks.keySet().retainAll(cooldowns.keySet());cooldownMaxTicks.replaceAll((id,value)->Math.max(cooldowns.getOrDefault(id,0),Math.max(1,Math.min(12000,value))));
        unlearnedExperience.keySet().removeAll(experience.keySet());unlearnedExperience.replaceAll((id,value)->ClassicRules.clamp(value,0,1));
    }
}
