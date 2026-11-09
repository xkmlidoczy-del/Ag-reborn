package cn.academy.port.api;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/** Non-player counterpart of SkillAttackEvent; player listeners retain their original non-null player contract. */
public final class EntitySkillAttackEvent extends Event {
    public final LivingEntity caster,target;
    public final String skill;
    public double amount;
    public EntitySkillAttackEvent(LivingEntity caster,String skill,LivingEntity target,double amount) {
        this.caster=caster;this.skill=skill;this.target=target;this.amount=amount;
    }
}
