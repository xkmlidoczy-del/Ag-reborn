package cn.academy.port.city;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.canon.EntityAbilityRuntime;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.SkillConsumption;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.CityNpcPower;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Player-scale HP/shield, classic CP/overload and the shared additional-skill CP pool. */
final class CityNpcAbilityData {
    final CityNpcEntity npc;
    AbilityProgress ability;
    int paper,needles,medium;
    long supplyDay;
    double skillCp;
    private long resourceTick=Long.MIN_VALUE;
    private BigDecimal lastPosition;
    private double skillMaximum,skillRecovery,baseSkillCost;
    private BigDecimal damageUnit=BigDecimal.ZERO;
    CityNpcAbilityData(CityNpcEntity npc){this.npc=npc;fresh();}
    static String category(CityEspers.Profile p){return switch(p){
        case ELECTRO->"electromaster";case MELT->"meltdowner";case TELEPORT->"teleporter";case VECTOR->"vecmanip";
        case MENTAL->"mental_out";case AIR->"aero_hand";case FLOAT->"float_dial";case NITROGEN->"nitrogen_armor";
        case DARK->"dark_matter";case WATER->"hydro_hand";case PAPER->"paper_psychokinesis";case SHADOW->"liquid_shadow";case NONE->"";
    };}
    private void bind(){ability.bindConsumption(AcademyConfig::consumptionConfig,SkillConsumption.UNMODIFIED,() -> {},npc::isAlive);}
    void fresh() {
        ability=new AbilityProgress();bind();String category=category(npc.esper());
        if(!category.isEmpty()) {
            ability.changeCategoryClassic(category);ability.setLevel(npc.esperLevel());
            for(var d:EntityAbilityRuntime.DEFINITIONS)if(d.category().equals(category)&&d.level()<=ability.level)learn(d.id());
            ability.cp=ability.maxCp();ability.overload=0;ability.overloadFine=true;ability.setActivateState(true);
        }
        paper=npc.esper()==CityEspers.Profile.PAPER?24:0;needles=npc.esper()==CityEspers.Profile.TELEPORT?12:0;medium=npc.esper()==CityEspers.Profile.SHADOW?2:0;
        supplyDay=Math.floorDiv(npc.level().getDayTime(),24000);stats(true);
        skillCp=skillMaximum();
    }
    private void learn(String id) {
        var definition=SkillCatalog.find(ability.category,id).orElse(null);if(definition==null||ability.learned(id))return;
        for(var requirement:definition.requirements())learn(requirement.id());
        ability.learn(id);
        ability.experience.put(id,ClassicRules.clamp(.18+.17*(ability.level-definition.level()),.1,.85));
    }
    void stats(boolean fill) {
        double max=20; // normalized native adapter; true amounts live in the player's exact ledger
        var attribute=npc.getAttribute(Attributes.MAX_HEALTH);if(attribute!=null)attribute.setBaseValue(max);
        if(fill)npc.setHealth((float)max);else npc.setHealth(Math.min(npc.getHealth(),(float)max));
        npc.setInvulnerable(npc.esper()==CityEspers.Profile.NONE);
        if(!npc.level().isClientSide&&npc.esper()!=CityEspers.Profile.NONE) {
            HighPowerCombat.synchronizeCityNpc(npc,position(),null,fill?1:npc.getHealth()/npc.getMaxHealth());
            cachePosition(position());
        }
    }
    BigDecimal position(){return ContinuousHighPowerGrowth.position(ability.level,ability.levelProgress(SkillCatalog.levelSkillCount(ability)));}
    double skillMaximum(){return skillMaximum;}
    double baseSkillCost(){return baseSkillCost;}
    BigDecimal damageUnit(){return damageUnit;}
    private void cachePosition(BigDecimal q) {
        lastPosition=q;skillMaximum=HighPowerGrowth.cpMaximumAt(q);skillRecovery=HighPowerGrowth.cpRecoveryAt(q);
        baseSkillCost=HighPowerGrowth.arcCostAt(q);
        damageUnit=HighPowerGrowth.categoryDamage(ability.category,ContinuousHighPowerGrowth.sample(q).damage());
    }
    void tick() {
        ability.tick();if(npc.esper()==CityEspers.Profile.NONE)return;
        long now=npc.level().getGameTime();if(resourceTick==now)return;resourceTick=now;
        var q=position();
        if(lastPosition==null||lastPosition.compareTo(q)!=0){HighPowerCombat.synchronizeCityNpc(npc,q,null,1);cachePosition(q);}
        skillCp=Math.min(skillMaximum(),Math.max(0,skillCp)+skillRecovery/20);
        HighPowerCombat.tickShield(npc);
    }
    boolean spendSkill(String id,double cost,int cooldown) {
        if(!ability.canUse(id)||!Double.isFinite(cost)||cost<0||skillCp<cost)return false;
        skillCp-=cost;ability.setCooldown(id,cooldown);return true;
    }
    boolean material(String kind,int count,boolean consume) {
        int available=switch(kind){case "paper"->paper;case "needle"->needles;default->medium;};
        if(available<count)return false;
        if(consume)switch(kind){case "paper"->paper-=count;case "needle"->needles-=count;default->medium-=count;}
        return true;
    }
    void supplyAtHome() {
        long day=Math.floorDiv(npc.level().getDayTime(),24000);
        if(day>supplyDay){paper=npc.esper()==CityEspers.Profile.PAPER?24:0;needles=npc.esper()==CityEspers.Profile.TELEPORT?12:0;medium=npc.esper()==CityEspers.Profile.SHADOW?2:0;supplyDay=day;}
    }
    CompoundTag save() {
        var tag=new CompoundTag();tag.put("ability",AbilityStorage.encode(ability));tag.putInt("paper",paper);tag.putInt("needles",needles);tag.putInt("medium",medium);
        tag.putInt("level",npc.esperLevel());tag.putString("profile",npc.esper().id());tag.putFloat("health",npc.getHealth());
        tag.putDouble("skill_cp",skillCp);tag.put("player_power",HighPowerCombat.cityNpcCheckpoint(npc));
        tag.putLong("supply_day",supplyDay);tag.putLong("saved_tick",npc.level().getGameTime());return tag;
    }
    void load(CompoundTag tag) {
        if(!tag.contains("ability")||!tag.getString("profile").equals(npc.esper().id()))return;
        ability=AbilityStorage.decode(tag.getCompound("ability"),AcademyConfig.consumptionConfig());bind();
        npc.abilityLevel(npc.esper()==CityEspers.Profile.NONE?0:Math.max(1,Math.min(5,tag.getInt("level"))));stats(false);
        paper=Math.max(0,Math.min(64,tag.getInt("paper")));needles=Math.max(0,Math.min(64,tag.getInt("needles")));medium=Math.max(0,Math.min(16,tag.getInt("medium")));
        supplyDay=tag.getLong("supply_day");
        if(npc.esper()!=CityEspers.Profile.NONE) {
            double fraction=tag.contains("player_power")?1:Math.clamp(tag.getFloat("health")/(20+10.0*npc.esperLevel()),0,1);
            if(!HighPowerCombat.synchronizeCityNpc(npc,position(),tag.getCompound("player_power"),fraction))
                throw new IllegalArgumentException("Invalid saved city esper combat state");
            cachePosition(position());
        }
        skillCp=tag.contains("skill_cp")?Math.clamp(Double.isFinite(tag.getDouble("skill_cp"))?tag.getDouble("skill_cp"):0,0,skillMaximum()):skillMaximum();
        int elapsed=(int)Math.min(12000,Math.max(0,npc.level().getGameTime()-tag.getLong("saved_tick")));
        for(int i=0;i<elapsed;i++)ability.tick();
        if(npc.esper()!=CityEspers.Profile.NONE)skillCp=Math.min(skillMaximum(),skillCp+elapsed*skillRecovery/20);
    }
}
