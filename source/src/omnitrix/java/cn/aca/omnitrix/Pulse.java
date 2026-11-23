package cn.aca.omnitrix;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class Pulse extends ThrowableProjectile {
    private static final DustColorTransitionOptions HOT = new DustColorTransitionOptions(
            new org.joml.Vector3f(1f, .76f, .26f), new org.joml.Vector3f(.79f, .18f, .063f), 1.45f);
    private static final DustColorTransitionOptions EMBER = new DustColorTransitionOptions(
            new org.joml.Vector3f(.89f, .28f, .11f), new org.joml.Vector3f(.21f, .16f, .18f), 1.16f);
    private static final DustColorTransitionOptions STAR_CORE = new DustColorTransitionOptions(
            new org.joml.Vector3f(.86f, .92f, 1f), new org.joml.Vector3f(.38f, .24f, .78f), 1.35f);

    public enum Variant {
        FIRE(20f, 110_000f, 64, 140, 0, 0, Form.HEATBLAST, false, ParticleTypes.FLAME),
        CRYSTAL(26f, 145_000f, 0, 0, 0, 0, Form.DIAMONDHEAD, true, ParticleTypes.WAX_OFF),
        SLIME(15f, 105_000f, 0, 0, 90, 140, Form.STINKFLY, false, ParticleTypes.ITEM_SLIME);

        final float damage, celestialDamage;
        final int fire, celestialFire, slow, celestialSlow;
        public final Form form;
        public final boolean secondary;
        public final ParticleOptions particle;

        Variant(float damage, float celestialDamage, int fire, int celestialFire, int slow, int celestialSlow,
                Form form, boolean secondary, ParticleOptions particle) {
            this.damage = damage;
            this.celestialDamage = celestialDamage;
            this.fire = fire;
            this.celestialFire = celestialFire;
            this.slow = slow;
            this.celestialSlow = celestialSlow;
            this.form = form;
            this.secondary = secondary;
            this.particle = particle;
        }
    }

    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(Pulse.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WATCH_KIND = SynchedEntityData.defineId(Pulse.class, EntityDataSerializers.INT);
    private boolean broad;
    private float fraction = 1;
    private long formStart;
    private long markSource = Long.MIN_VALUE;

    public Pulse(EntityType<? extends Pulse> type, Level level) { super(type, level); }

    void initialize(ServerPlayer p, Kind kind, Variant variant, Vec3 from, Vec3 velocity, boolean broad, float fraction, long markSource) {
        setOwner(p);
        entityData.set(MODE, variant.ordinal());
        entityData.set(WATCH_KIND, kind == null ? 0 : kind.ordinal());
        setPos(from);
        setDeltaMovement(velocity);
        this.broad = broad;
        this.fraction = fraction;
        this.markSource = markSource;
        this.formStart = Omnitrix.wrist(p).began;
    }

    private boolean current(ServerPlayer p) {
        return p.isAlive() && p.level() == level();
    }

    private boolean pHasOriginalForm(ServerPlayer player) {
        return player.hasData(Omnitrix.WRIST) && Omnitrix.wrist(player).began == formStart;
    }

    @Override protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("variant", variant().ordinal());
        tag.putInt("watch", watchKind().ordinal());
        tag.putBoolean("broad", broad);
        tag.putFloat("fraction", fraction);
        tag.putLong("start", formStart);
        tag.putLong("mark", markSource);
    }

    @Override protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(MODE, tag.getInt("variant"));
        entityData.set(WATCH_KIND, tag.getInt("watch"));
        broad = tag.getBoolean("broad");
        fraction = tag.contains("fraction") ? tag.getFloat("fraction") : 1;
        formStart = tag.getLong("start");
        markSource = tag.contains("mark") ? tag.getLong("mark") : Long.MIN_VALUE;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODE, 0);
        builder.define(WATCH_KIND, 0);
    }

    public Variant variant() {
        int idx = entityData.get(MODE);
        var values = Variant.values();
        return values[Math.max(0, Math.min(values.length - 1, idx))];
    }

    public Kind watchKind() {
        Kind k = Kind.at(entityData.get(WATCH_KIND));
        return k == null ? Kind.CLASSIC : k;
    }

    public boolean celestial() { return watchKind() == Kind.CELESTIAL; }

    @Override protected double getDefaultGravity() { return variant() == Variant.SLIME ? .025 : .009; }
    @Override public boolean canUsePortal(boolean cooldown) { return false; }

    @Override protected boolean canHitEntity(Entity e) {
        return super.canHitEntity(e) && e instanceof LivingEntity target && !(e instanceof PursuitBody)
                && (level().isClientSide || getOwner() instanceof ServerPlayer p && current(p) && Abilities.validTarget(p, target, broad));
    }

    @Override public void tick() {
        if (!level().isClientSide && (tickCount > 45 || !(getOwner() instanceof ServerPlayer p) || !current(p))) {
            discard();
            return;
        }
        super.tick();
        if (level().isClientSide) {
            boolean star = celestial();
            Vec3 back = getDeltaMovement().scale(.15);
            int count = (WristNetwork.client != null && WristNetwork.client.reducedMotion()) ? 2 : 4;
            for (int i = 0; i < count; i++) {
                Vec3 at = position().subtract(back.scale(i));
                ParticleOptions particle = star ? (i == 0 ? ParticleTypes.END_ROD : STAR_CORE)
                        : (variant() == Variant.FIRE ? (i <= 1 ? HOT : EMBER) : variant().particle);
                level().addParticle(particle, at.x, at.y, at.z, 0, .011, 0);
                if (!star && variant() == Variant.FIRE && i == 3) {
                    level().addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0, .023, 0);
                }
            }
        }
    }

    @Override protected void onHit(HitResult hit) {
        if (!(level() instanceof ServerLevel level)) return;
        Variant kind = variant();
        boolean star = celestial();
        float dmg = (star ? kind.celestialDamage : kind.damage) * fraction;
        int burn = star ? kind.celestialFire : kind.fire;
        int slowTicks = star ? kind.celestialSlow : kind.slow;
        boolean hitLiving = false;
        LivingEntity directHit = null;

        if (hit instanceof EntityHitResult entity && entity.getEntity() instanceof LivingEntity target
                && getOwner() instanceof ServerPlayer player && current(player) && Abilities.validTarget(player, target, broad)) {
            directHit = target;
            if (Abilities.damage(player, target, dmg, getDeltaMovement(), .56 * fraction, burn, slowTicks)) {
                hitLiving = true;
                if (markSource != Long.MIN_VALUE && pHasOriginalForm(player)) Abilities.projectileMarked(player, markSource, target);
            }
        }

        Vec3 at = hit.getLocation();
        // X-Heatblast Solar Flare splash corona (3.2m, 55,000 damage) & X-Stinkfly Star-Toxin cloud (2.6m)
        if (star && getOwner() instanceof ServerPlayer player && current(player) && (kind == Variant.FIRE || kind == Variant.SLIME)) {
            final LivingEntity hitTarget = directHit;
            double splashR = kind == Variant.FIRE ? 3.2 : 2.6;
            float splashDmg = kind == Variant.FIRE ? 55_000f : 42_000f;
            for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                    AABB.ofSize(at, splashR * 2, splashR * 2, splashR * 2),
                    e -> e != hitTarget && e.distanceToSqr(at) <= splashR * splashR && Abilities.validTarget(player, e, broad))) {
                if (Abilities.damage(player, nearby, splashDmg, nearby.position().subtract(at), .35, burn / 2, slowTicks)) {
                    hitLiving = true;
                    if (markSource != Long.MIN_VALUE && pHasOriginalForm(player)) Abilities.projectileMarked(player, markSource, nearby);
                }
            }
        }

        Vec3 vel = getDeltaMovement();
        Vec3 origin = vel.lengthSqr() > 1e-4 ? at.subtract(vel.normalize().scale(1.25)) : at;
        if (getOwner() instanceof ServerPlayer player) {
            int action = (kind.secondary ? Abilities.SECONDARY : Abilities.PRIMARY) + kind.form.ordinal();
            int flags = WristNetwork.FLAG_PROJECTILE | (hitLiving ? WristNetwork.FLAG_HIT_ENTITY : 0);
            WristNetwork.cueNear(level, player.getUUID(), watchKind().ordinal(), action, flags, origin, at, 64);
        }
        level.sendParticles(star ? ParticleTypes.END_ROD : kind.particle, at.x, at.y, at.z, 14, .18, .22, .18, .055);
        Kind wKind = watchKind();
        var impactSfx = switch (kind) {
            case FIRE -> wKind == Kind.CARNITRIX ? Omnitrix.CARNI_HEATBLAST.get()
                    : star ? Omnitrix.X_FIRE_LAUNCH.get() : Omnitrix.FIRE_LAUNCH.get();
            case CRYSTAL -> wKind == Kind.CARNITRIX ? Omnitrix.CARNI_DIAMONDHEAD.get()
                    : star ? Omnitrix.X_CRYSTAL_VOLLEY.get() : Omnitrix.CRYSTAL_VOLLEY.get();
            case SLIME -> wKind == Kind.CARNITRIX ? Omnitrix.CARNI_STINKFLY.get()
                    : star ? Omnitrix.X_SLIME_SPLAT.get() : Omnitrix.SLIME_SPLAT.get();
        };
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), impactSfx,
                net.minecraft.sounds.SoundSource.PLAYERS, hitLiving ? .85f : .62f, 1.0f);
        if (kind == Variant.FIRE) {
            level.sendParticles(star ? STAR_CORE : HOT, at.x, at.y, at.z, 18, .28, .28, .28, .034);
            if (!star) level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 9, .28, .28, .28, .026);
        }
        discard();
    }
}
