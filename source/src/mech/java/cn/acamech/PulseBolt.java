package cn.acamech;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Large, visible ATLAS pulse. Collision is swept continuously on the server. */
public final class PulseBolt extends Projectile {
    private static final double SPEED = 1.45;
    private MechEntity launcher;
    private double traveled;

    public PulseBolt(EntityType<? extends PulseBolt> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    void launch(MechEntity launcher, Player pilot, Vec3 origin, Vec3 direction) {
        this.launcher = launcher;
        setOwner(pilot);
        setPos(origin);
        setDeltaMovement(direction.scale(SPEED));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = getDeltaMovement();
        if (!(level() instanceof ServerLevel level)) {
            setPos(position().add(velocity));
            return;
        }
        if (traveled >= RangedWeapon.PULSE.range || velocity.lengthSqr() < .01) {
            if (launcher != null && launcher.isAlive()) launcher.setRangedResult(RangedWeapon.MISS);
            discard();
            return;
        }
        double step = Math.min(velocity.length(), RangedWeapon.PULSE.range - traveled);
        Vec3 next = position().add(velocity.normalize().scale(step));
        if (!level.hasChunkAt(BlockPos.containing(next))) {
            if (launcher != null && launcher.isAlive()) launcher.setRangedResult(RangedWeapon.MISS);
            discard();
            return;
        }
        RangedCombat.Trace trace = RangedCombat.trace(level, this, position(), next, .23);
        if (trace.contact()) {
            RangedCombat.pulseImpact(level, this, getOwner(), launcher, trace, velocity);
            discard();
        } else {
            setPos(next);
            traveled += step;
        }
    }
}
