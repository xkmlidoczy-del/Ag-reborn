package cn.academy.biohum;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;

/** A material construct with ordinary collision, navigation, damage and saved ownership. */
public final class Construct extends PathfinderMob {
    private UUID creator;
    private long expires;
    private static final EntityDataAccessor<String> SKILL=SynchedEntityData.defineId(Construct.class,EntityDataSerializers.STRING);

    Construct(EntityType<? extends Construct> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 56)
                .add(Attributes.MOVEMENT_SPEED, .25).add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.FOLLOW_RANGE, 40).add(Attributes.KNOCKBACK_RESISTANCE, .65);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){
        super.defineSynchedData(builder);
        builder.define(SKILL,"ellis_command");
    }
    public String skill(){return entityData.get(SKILL);}
    public boolean iron(){return skill().equals("iron_sand_colossus");}
    @Override public EntityDimensions getDefaultDimensions(Pose pose){
        return iron()?super.getDefaultDimensions(pose).scale(1.42f):super.getDefaultDimensions(pose);
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(SKILL.equals(key))refreshDimensions();
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
    }

    void arm(HumNpc caster, String technique, Vec3 at, float power, int duration) {
        creator = caster.getUUID();
        entityData.set(SKILL,technique);
        expires = level().getGameTime() + duration;
        setPos(at);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(power);
    }

    boolean belongsTo(HumNpc caster) { return caster.getUUID().equals(creator); }

    void command(LivingEntity target, Vec3 at) {
        if (target != null && !isAlliedTo(target)) setTarget(target);
        else getNavigation().moveTo(at.x, at.y, at.z, .9);
    }

    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel world) {
            if (world.getGameTime() >= expires) { discard(); return; }
            if (creator != null && world.getEntity(creator) instanceof HumNpc npc && getTarget() == null
                    && npc.validTarget(npc.getTarget())) setTarget(npc.getTarget());
        }
    }

    @Override public boolean isAlliedTo(Entity e) {
        if (creator != null && level() instanceof ServerLevel world && world.getEntity(creator) instanceof HumNpc npc) {
            return npc.ally(e);
        }
        return super.isAlliedTo(e);
    }

    @Override public boolean doHurtTarget(Entity e) {
        boolean result = super.doHurtTarget(e);
        if (result && creator != null && level() instanceof ServerLevel world && world.getEntity(creator) instanceof HumNpc npc) {
            Payloads.cue(npc,skill(),Payloads.Cue.IMPACT,e.position(),e.position(),1.2f);
        }
        return result;
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (creator != null) tag.putUUID("Creator", creator);
        tag.putLong("Expires", expires);
        tag.putString("Skill",skill());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        creator = tag.hasUUID("Creator") ? tag.getUUID("Creator") : null;
        expires = tag.getLong("Expires");
        if(tag.contains("Skill"))entityData.set(SKILL,tag.getString("Skill"));
    }
}
