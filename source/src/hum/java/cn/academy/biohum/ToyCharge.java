package cn.academy.biohum;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Frenda's fictional toy trap. Its warning and damage share the same radius. */
public final class ToyCharge extends Entity {
    static final double RADIUS = 2.55;
    private UUID creator;
    private UUID issuer;
    private long dueTick;
    private double radius = RADIUS;
    private float power = 7;

    public ToyCharge(EntityType<? extends ToyCharge> type, Level level) {
        super(type, level);
    }

    void arm(HumNpc npc, Vec3 position) {
        arm(npc, position, Skills.get("toy_charge"), null);
    }

    void arm(HumNpc npc, Vec3 position, Skills.Skill skill, UUID player) {
        creator = npc.getUUID();
        issuer = player;
        radius = skill.radius();
        power = (float) skill.power();
        dueTick = level().getGameTime() + skill.duration();
        setPos(position);
    }

    boolean belongsTo(HumNpc npc) {
        return npc.getUUID().equals(creator);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (creator == null || !(level.getEntity(creator) instanceof HumNpc npc) || !npc.isAlive()) {
            discard();
            return;
        }
        long remaining = dueTick - level.getGameTime();
        if (remaining > 0) {
            if (remaining % 8 == 0) Payloads.cue(npc,"toy_charge",Payloads.Cue.FIELD,position(),position(),(float)radius);
            return;
        }
        var visible = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 1.4, radius),
                target -> target.distanceToSqr(position()) <= radius * radius && npc.validTarget(target)
                        && target.hasLineOfSight(this));
        for (var target : visible) target.hurt(damageSources().indirectMagic(this, npc), power);
        Abilities.particles(level, position().add(0, .30, 0), ParticleTypes.EXPLOSION, 1);
        Abilities.particles(level, position().add(0, .37, 0), ParticleTypes.FLAME, 19);
        Payloads.cue(npc,"toy_charge",Payloads.Cue.IMPACT,position(),position(),(float)radius);
        WorldEffects.area(level,WorldEffects.Source.of(npc,issuer),position(),radius,radius*.45,
                WorldEffects.Change.BREAK,power/4,160);
        level.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, .65f, .85f);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (creator != null) tag.putUUID("Creator", creator);
        if (issuer != null) tag.putUUID("Issuer", issuer);
        tag.putDouble("Radius",radius);
        tag.putFloat("Power",power);
        tag.putLong("DueTick", dueTick);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        creator = tag.hasUUID("Creator") ? tag.getUUID("Creator") : null;
        issuer = tag.hasUUID("Issuer") ? tag.getUUID("Issuer") : null;
        if(tag.contains("Radius"))radius=tag.getDouble("Radius");
        if(tag.contains("Power"))power=tag.getFloat("Power");
        dueTick = tag.getLong("DueTick");
    }
}
