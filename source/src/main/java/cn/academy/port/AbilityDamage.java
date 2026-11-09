package cn.academy.port;
import cn.academy.port.api.SkillAttackEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
public final class AbilityDamage {
    private AbilityDamage() {}
    /** Native entity caster entry. The controller owns target policy; armor, effects and damage hooks remain native. */
    public static boolean attackEntity(LivingEntity caster,String skill,LivingEntity target,double rawDamage,boolean ignoreArmor) {
        if(caster instanceof ServerPlayer player) return attack(player,skill,target,rawDamage,ignoreArmor);
        if(caster.level().isClientSide||!caster.isAlive()||!target.isAlive()||caster.level()!=target.level()) return false;
        var event=new cn.academy.port.api.EntitySkillAttackEvent(caster,skill,target,rawDamage);
        NeoForge.EVENT_BUS.post(event);
        int dot=skill.indexOf('.');double local=dot<1?1:AcademyConfig.consumptionConfig().optionalFloat(skill.substring(0,dot),skill.substring(dot+1),"damage_scale");
        double amount=event.amount*AcademyConfig.DAMAGE_SCALE.get()*local;
        if(!Double.isFinite(amount)||amount<=0) return false;
        if(caster instanceof cn.academy.port.city.CityNpcEntity npc&&target instanceof Player player&&npc.combat().dueling(player))
            amount=cn.academy.port.highpower.CityNpcPower.sparringDamage(target,amount);
        if(amount<=0)return false;
        return target.hurt(new EntitySkillDamageSource(caster,skill,ignoreArmor),(float)amount);
    }
    public static final class EntitySkillDamageSource extends DamageSource {
        public final String skill;private final boolean ignoreArmor;
        public EntitySkillDamageSource(LivingEntity caster,String skill,boolean ignoreArmor) {
            super(caster.damageSources().mobAttack(caster).typeHolder(),caster);this.skill=skill;this.ignoreArmor=ignoreArmor;
        }
        @Override public boolean is(TagKey<DamageType> tag) {return ignoreArmor&&tag.equals(DamageTypeTags.BYPASSES_ARMOR)||super.is(tag);}
        @Override public Component getLocalizedDeathMessage(LivingEntity target) {
            return Component.translatable("death.attack.ac_skill",target.getDisplayName(),getEntity().getDisplayName(),Component.translatable("ac.ability."+skill+".name"));
        }
    }
    public static boolean attack(ServerPlayer player,String skill,Entity target,double rawDamage) {return attack(player,skill,target,rawDamage,false);}
    public static boolean attack(ServerPlayer player,String skill,Entity target,double rawDamage,boolean ignoreArmor) {
        return attack(player,skill,target,rawDamage,ignoreArmor,false);
    }
    /** Shield retaliation never causes another category-shield retaliation. */
    public static boolean shieldCounter(ServerPlayer player,String skill,Entity target,double rawDamage) {
        return attack(player,skill,target,rawDamage,false,true);
    }
    private static boolean attack(ServerPlayer player,String skill,Entity target,double rawDamage,boolean ignoreArmor,boolean retaliation) {
        var event=new SkillAttackEvent(player,skill,target,rawDamage);NeoForge.EVENT_BUS.post(event);double global=AcademyConfig.DAMAGE_SCALE.get(),local=1;int separator=skill.indexOf('.');if(separator>0)local=AcademyConfig.consumptionConfig().optionalFloat(skill.substring(0,separator),skill.substring(separator+1),"damage_scale");double amount=global==1&&local==1?event.amount:(float)global*(float)local*(float)event.amount;
        if(!Double.isFinite(amount)||amount<=0)return false;
        if(target instanceof Player&&!AcademyConfig.ATTACK_PLAYERS.get())return false;
        if(target instanceof net.minecraft.world.entity.decoration.HangingEntity&&!AcademyConfig.contextTerrain(target.level(),skill))return false;
        var source=new SkillDamageSource(player,skill,ignoreArmor,retaliation);
        if(target instanceof net.minecraft.world.entity.LivingEntity living)cn.academy.port.highpower.CapBreak.arm(living);
        boolean hurt=target.hurt(source,(float)amount);
        if(target instanceof net.minecraft.world.entity.LivingEntity living)cn.academy.port.highpower.CapBreak.afterHit(player,skill,living,source,(float)amount,hurt);
        return hurt;
    }
    public static final class SkillDamageSource extends DamageSource {
        public final String skill;
        private final boolean ignoreArmor;
        public final boolean retaliation;
        public SkillDamageSource(ServerPlayer player,String skill,boolean ignoreArmor) {this(player,skill,ignoreArmor,false);}
        private SkillDamageSource(ServerPlayer player,String skill,boolean ignoreArmor,boolean retaliation) {super(player.damageSources().playerAttack(player).typeHolder(),player);this.skill=skill;this.ignoreArmor=ignoreArmor;this.retaliation=retaliation;}
        @Override public boolean is(TagKey<DamageType> tag) {return ignoreArmor&&tag.equals(DamageTypeTags.BYPASSES_ARMOR)||super.is(tag);}
        @Override public Component getLocalizedDeathMessage(LivingEntity target) {return Component.translatable("death.attack.ac_skill",target.getDisplayName(),getEntity().getDisplayName(),Component.translatable("ac.ability."+skill+".name"));}
    }
}
