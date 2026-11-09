package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeBrakingTouchMixinPlugin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** NEW413: evidence for one real accepted movement packet's ordinary block landing.
 * This observer never moves an entity, changes fallDistance, or creates a native fall call. */
public final class BrakingTouchLandingProof {
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<LandingHurt> LANDING_HURT = new ThreadLocal<>();
    private static final double EPS = 1.0e-7;
    private static final boolean DIAGNOSTIC = Boolean.getBoolean("academy.highpower.brakingTouchDiagnostic");
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("academy.braking_touch_qa");
    private static int diagnosticPackets;
    private BrakingTouchLandingProof() {}

    /** Installed on Entity only after the optional, fingerprinted adapter was selected. */
    public interface Access { boolean academy$brakingLandingSeam(); }

    public static boolean available(ServerPlayer player) {
        return player instanceof Access access && access.academy$brakingLandingSeam();
    }

    private static final class Frame {
        final ServerGamePacketListenerImpl connection;
        final ServerPlayer player;
        final ServerLevel level;
        final Vec3 origin;
        final AABB body;
        final boolean grounded;
        final float fall;
        final StringBuilder diagnostic;
        boolean invalid, moveSeen, originUnsupported, acceptedSeen, inAccepted;
        Vec3 incomingMovement, movedPosition, movedVelocity;
        AABB movedBody;
        Contact contact;
        Support support;
        Frame(ServerGamePacketListenerImpl connection) {
            this.connection = connection; player = connection.player; level = player.serverLevel();
            origin = player.position(); body = player.getBoundingBox(); grounded = player.onGround(); fall = player.fallDistance;
            diagnostic = diagnosticArmed(player) ? new StringBuilder() : null;
            diagnostic(this, "begin", () -> "position=" + origin + " body=" + body + " " + basicSnapshot(player));
        }
        boolean owns(ServerPlayer p) {
            return !invalid && ACTIVE.get() == this && p == player && p.connection == connection
                    && connection.player == p && p.serverLevel() == level && basic(p) && available(p);
        }
        void invalidate() { invalid = true; }
    }

    private record Support(BlockPos pos, BlockState state, Vec3 point) {}

    /** Opaque authority exists only while reduceLanding is executing in the native call stack. */
    public static final class Contact {
        private final Frame frame;
        private final Support support;
        private final Vec3 position, velocity;
        private final AABB body;
        private final Pose pose;
        private final float fall, health, absorption;
        private final int hurt;
        private final double safeDistance, damageMultiplier;
        private final Map<Holder<MobEffect>, Tag> effects;
        private boolean inBlock, causeSeen, inCause, eventSeen, window, claimed;
        private boolean positiveEvent, hurtSeen;
        private Map<Holder<Attribute>, AttributeState> landingAttributes;
        private float causeDistance, causeMultiplier;
        private Contact(Frame frame, Support support) {
            this.frame = frame; this.support = support;
            ServerPlayer p = frame.player;
            position = p.position(); velocity = p.getDeltaMovement(); body = p.getBoundingBox(); pose = p.getPose();
            fall = p.fallDistance; health = p.getHealth(); absorption = p.getAbsorptionAmount(); hurt = p.hurtTime;
            safeDistance = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            damageMultiplier = p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
            effects = effects(p);
        }
        public Vec3 point() { return support.point; }
        /** Observed incoming movement-packet displacement, not an invented or rewritten player velocity. */
        public Vec3 incomingVelocity() { return frame.incomingMovement; }
        public boolean valid() { return window && unchanged(); }
        /** Reserve before callbacks/payment. A successful claim cannot be repeated or restored. */
        public boolean claim() {
            if (claimed || !valid()) return false;
            claimed = true; return true;
        }
        private boolean unchanged() {
            ServerPlayer p = frame.player;
            return frame.owns(p) && frame.contact == this && frame.inAccepted && inBlock && inCause
                    && p.onGround() && p.getPose() == pose && position.equals(p.position()) && body.equals(p.getBoundingBox())
                    && velocity.equals(p.getDeltaMovement()) && Float.compare(fall, p.fallDistance) == 0
                    && Float.compare(health, p.getHealth()) == 0 && Float.compare(absorption, p.getAbsorptionAmount()) == 0
                    && hurt == p.hurtTime && safeDistance == p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
                    && damageMultiplier == p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)
                    && effects.equals(effects(p))
                    && supportUnchanged(p, support);
        }
    }

    public static Contact current(ServerPlayer player) {
        Frame frame = ACTIVE.get(); Contact contact = frame == null ? null : frame.contact;
        return frame != null && frame.player == player && contact != null && contact.valid() ? contact : null;
    }

    /** NEW422 authority is separate from the NEW413 event window. Never persisted or caller-created. */
    public static final class LandingHurt implements AutoCloseable {
        private final Contact contact;
        private final DamageSource source;
        private boolean bound, claimed, closed, poisoned;
        private LandingHurt(Contact contact, DamageSource source) {
            this.contact = contact; this.source = source;
        }
        public Vec3 point() { return contact.point(); }
        public boolean valid(ServerPlayer player, DamageSource damageSource) {
            if (closed || poisoned || !bound || claimed || LANDING_HURT.get() != this
                    || contact.frame.player != player || source != damageSource) return false;
            try {
                if (landingPhysicalUnchanged(contact, player) && landingHurtAvailable(player)) return true;
            } catch (RuntimeException | LinkageError failure) { disableLandingHurt(failure); }
            poisoned = true;
            return false;
        }
        /** Consume before callbacks, cost, HP/share settlement, or cue publication. */
        public boolean claim() {
            if (!valid(contact.frame.player, source)) return false;
            claimed = true;
            return true;
        }
        @Override public void close() { closed = true; }
    }

    public static boolean landingHurtAvailable(ServerPlayer player) {
        return available(player) && NativeBrakingTouchMixinPlugin.isReviewedLandingHurtSupported()
                && AnchorDamagePostProof.available();
    }

    /** Called at entry to the actual native hurt frame, before any hurt listener. */
    public static LandingHurt bindLandingHurt(ServerPlayer player, DamageSource source) {
        LandingHurt receipt = LANDING_HURT.get();
        if (receipt == null) return null;
        if (receipt.bound || receipt.closed || receipt.poisoned || receipt.contact.frame.player != player
                || receipt.source != source) {
            receipt.poisoned = true;
            return null;
        }
        try {
            if (!receipt.contact.unchanged() || !landingPhysicalUnchanged(receipt.contact, player)
                    || !landingHurtAvailable(player)) {
                receipt.poisoned = true;
                return null;
            }
            receipt.bound = true;
            return receipt;
        } catch (RuntimeException | LinkageError failure) {
            receipt.poisoned = true; disableLandingHurt(failure); return null;
        }
    }

    /** Exactly the reviewed positive calculated-damage hurt invocation in causeFallDamage. */
    public static boolean nativeLandingHurt(LivingEntity entity, DamageSource source, float amount,
                                            Operation<Boolean> original) {
        LandingHurt parent = LANDING_HURT.get(), receipt = null;
        if (parent != null) parent.poisoned = true;
        Frame frame = ACTIVE.get(); Contact contact = frame == null ? null : frame.contact;
        try {
            if (contact != null) {
                boolean first = !contact.hurtSeen;
                contact.hurtSeen = true;
                if (parent == null && first && entity == frame.player && !contact.claimed && !contact.window
                        && contact.positiveEvent && contact.landingAttributes != null && contact.unchanged()
                        && source == entity.damageSources().fall() && Float.isFinite(amount) && amount > 0
                        && landingHurtAvailable(frame.player) && InertialAnchor.active(frame.player)
                        && landingPhysicalUnchanged(contact, frame.player))
                    receipt = new LandingHurt(contact, source);
            }
        } catch (RuntimeException | LinkageError failure) { disableLandingHurt(failure); }
        if (receipt == null) return original.call(entity, source, amount);
        LANDING_HURT.set(receipt);
        try { return original.call(entity, source, amount); }
        finally { receipt.close(); LANDING_HURT.remove(); }
    }

    private static boolean landingPhysicalUnchanged(Contact contact, ServerPlayer p) {
        Frame frame = contact.frame;
        // Native hurt alone may change HP, absorption, and hurt timers before the exact Post settlement.
        // NEW413 still uses its original alive + HP/absorption/hurt snapshot through Contact.unchanged().
        return !frame.invalid && ACTIVE.get() == frame && frame.player == p && p.connection == frame.connection
                && frame.connection.player == p && p.serverLevel() == frame.level && physicalBasic(p)
                && frame.contact == contact && frame.inAccepted && contact.inBlock && contact.inCause
                && contact.positiveEvent && !contact.window && !contact.claimed
                && p.onGround() && p.getPose() == contact.pose && contact.position.equals(p.position())
                && contact.body.equals(p.getBoundingBox()) && contact.velocity.equals(p.getDeltaMovement())
                && Float.compare(contact.fall, p.fallDistance) == 0
                && contact.safeDistance == p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
                && contact.damageMultiplier == p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)
                && contact.effects.equals(effects(p)) && contact.landingAttributes != null
                && contact.landingAttributes.equals(attributes(p)) && supportUnchanged(p, contact.support);
    }

    private record AttributeState(double base, double value, Set<AttributeModifier> modifiers) {}
    private static Map<Holder<Attribute>, AttributeState> attributes(ServerPlayer player) {
        // Read existing instances only; observation must not instantiate/default or modify attributes.
        Map<Holder<Attribute>, Set<AttributeModifier>> modifiers = new HashMap<>();
        for (Tag saved : player.getAttributes().save()) {
            ResourceLocation id = ResourceLocation.tryParse(((CompoundTag)saved).getString("id"));
            if (id == null) throw new IllegalStateException("Unregistered landing attribute");
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElseThrow();
            var instance = player.getAttributes().getInstance(holder); // Already present in save(), never created here.
            if (instance == null) throw new IllegalStateException("Missing landing attribute");
            modifiers.put(holder, Set.copyOf(instance.getModifiers()));
        }
        Map<Holder<Attribute>, AttributeState> snapshot = new HashMap<>();
        BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
            if (player.getAttributes().hasAttribute(holder))
                snapshot.put(holder, new AttributeState(player.getAttributes().getBaseValue(holder),
                        player.getAttributes().getValue(holder), modifiers.getOrDefault(holder, Set.of())));
        });
        return Map.copyOf(snapshot);
    }

    public static void nativePacket(ServerGamePacketListenerImpl connection, ServerboundMovePlayerPacket packet,
                                    Operation<Void> original) {
        Frame parent = ACTIVE.get();
        if (parent != null) parent.invalidate();
        Frame frame = null;
        try {
            if (parent == null && connection != null && basic(connection.player) && available(connection.player))
                frame = new Frame(connection);
        } catch (RuntimeException | LinkageError failure) { disable(failure); }
        if (frame == null) {
            diagnosticRejectedPacket(connection == null ? null : connection.player, parent != null);
            original.call(packet); return;
        }
        ACTIVE.set(frame);
        try { original.call(packet); }
        finally { diagnosticEmit(frame); frame.invalidate(); ACTIVE.remove(); }
    }

    public static void nativeMove(ServerGamePacketListenerImpl connection, ServerPlayer player, MoverType type,
                                  Vec3 movement, Operation<Void> original) {
        Frame frame = ACTIVE.get(); boolean observe = false;
        try {
            if (frame != null) {
                observe = frame.connection == connection && frame.owns(player) && !frame.moveSeen
                        && type == MoverType.PLAYER && finite(movement) && movement.y < 0 && movement.lengthSqr() <= 100
                        && frame.origin.equals(player.position()) && frame.body.equals(player.getBoundingBox())
                        && Float.compare(frame.fall, player.fallDistance) == 0 && unsupportedOrigin(frame, player);
                frame.moveSeen = true;
                if (observe) { frame.originUnsupported = true; frame.incomingMovement = movement; }
                if (!observe) frame.invalidate();
                final boolean observed = observe;
                diagnostic(frame, "move", () -> "observed=" + observed + " type=" + type + " incoming=" + movement
                        + " groundedBefore=" + frame.grounded + " originUnsupported=" + frame.originUnsupported
                        + " originExact=" + frame.origin.equals(player.position())
                        + " bodyExact=" + frame.body.equals(player.getBoundingBox())
                        + " fallExact=" + (Float.compare(frame.fall, player.fallDistance) == 0) + " " + basicSnapshot(player));
            }
        } catch (RuntimeException | LinkageError failure) { if (frame != null) frame.invalidate(); disable(failure); }
        original.call(player, type, movement);
        if (!observe) return;
        try {
            if (!frame.owns(player) || player.getY() > frame.origin.y || !loaded(frame.level, player.getBoundingBox())) {
                diagnostic(frame, "move_post_rejected", () -> "position=" + player.position() + " " + basicSnapshot(player));
                frame.invalidate(); return;
            }
            frame.movedPosition = player.position(); frame.movedVelocity = player.getDeltaMovement();
            frame.movedBody = player.getBoundingBox();
        } catch (RuntimeException | LinkageError failure) { frame.invalidate(); disable(failure); }
    }

    /** Only ordinal zero in the reviewed accepted packet branch invokes this observer. */
    public static void nativeAcceptedFall(ServerGamePacketListenerImpl connection, ServerPlayer player,
                                          double x, double y, double z, boolean grounded, Operation<Void> original) {
        Frame frame = ACTIVE.get(); boolean accepted = false;
        try {
            if (frame != null) {
                accepted = frame.connection == connection && frame.owns(player) && frame.moveSeen && frame.originUnsupported && !frame.acceptedSeen
                        && frame.movedPosition != null && grounded && player.onGround() && y <= 0
                        && close(frame.movedPosition, player.position()) && frame.movedBody.equals(player.getBoundingBox())
                        && frame.movedVelocity.equals(player.getDeltaMovement())
                        && close(player.position().subtract(frame.origin), new Vec3(x, y, z))
                        && Float.compare(frame.fall, player.fallDistance) == 0;
                frame.acceptedSeen = true;
                final boolean admitted = accepted;
                diagnostic(frame, "accepted_ordinal0", () -> "admittedBeforeSupport=" + admitted + " invalid=" + frame.invalid
                        + " grounded=" + grounded + " nativeDelta=" + new Vec3(x, y, z) + " position=" + player.position()
                        + " positionClose=" + close(frame.movedPosition, player.position())
                        + " bodyExact=" + (frame.movedBody != null && frame.movedBody.equals(player.getBoundingBox()))
                        + " bodyDelta=" + boxDelta(frame.movedBody, player.getBoundingBox())
                        + " velocityExact=" + (frame.movedVelocity != null && frame.movedVelocity.equals(player.getDeltaMovement()))
                        + " displacementClose=" + close(player.position().subtract(frame.origin), new Vec3(x, y, z))
                        + " fallExact=" + (Float.compare(frame.fall, player.fallDistance) == 0) + " " + basicSnapshot(player));
                if (accepted) { frame.support = support(player); accepted = frame.support != null; }
                if (!accepted) frame.invalidate();
            }
        } catch (RuntimeException | LinkageError failure) { if (frame != null) frame.invalidate(); disable(failure); }
        if (!accepted) { original.call(player, x, y, z, grounded); return; }
        frame.inAccepted = true;
        try { original.call(player, x, y, z, grounded); }
        finally {
            frame.inAccepted = false; frame.invalidate();
            // Zero accumulated distance skips native Block.fallOn entirely, but is still this real first landing.
            if (frame.contact == null) {
                try { BrakingTouch.landingFinished(frame.player); }
                catch (RuntimeException | LinkageError failure) { disable(failure); }
            }
        }
    }

    public static void nativeFallOn(Block block, Level level, BlockState state, BlockPos pos, Entity entity,
                                    float distance, Operation<Void> original) {
        Frame frame = ACTIVE.get(); Contact contact = null;
        try {
            if (frame != null && frame.inAccepted && entity == frame.player) {
                if (frame.contact != null) frame.invalidate();
                else if (frame.owns(frame.player) && level == frame.level && frame.support != null
                        && block.getClass() == Block.class && state == frame.support.state && pos.equals(frame.support.pos)
                        && block == state.getBlock() && Float.isFinite(distance) && distance > 0
                        && Float.compare(distance, frame.player.fallDistance) == 0 && supportUnchanged(frame.player, frame.support)) {
                    contact = new Contact(frame, frame.support); frame.contact = contact; contact.inBlock = true;
                }
                final boolean admitted = contact != null;
                diagnostic(frame, "fall_on", () -> "admitted=" + admitted + " block=" + block.getClass().getName()
                        + " position=" + pos + " distance=" + distance + " fall=" + frame.player.fallDistance);
            }
        } catch (RuntimeException | LinkageError failure) { if (frame != null) frame.invalidate(); disable(failure); }
        try { original.call(block, level, state, pos, entity, distance); }
        finally {
            if (contact != null) {
                contact.inBlock = false; contact.window = false;
                // The first eligible physical landing retires the arm even when another listener canceled it.
                try { BrakingTouch.landingFinished(frame.player); }
                catch (RuntimeException | LinkageError failure) { frame.invalidate(); disable(failure); }
            }
        }
    }

    /** Any causeFallDamage reentry retires the outer landing; an unscoped synthetic call is untouched. */
    public static boolean nativeCause(LivingEntity entity, float distance, float multiplier, DamageSource source,
                                      Operation<Boolean> original) {
        Frame frame = ACTIVE.get(); Contact contact = frame == null ? null : frame.contact;
        boolean observe = false;
        try {
            if (contact != null) {
                observe = contact.inBlock && !contact.causeSeen && entity == frame.player && frame.owns(frame.player)
                        && source == entity.damageSources().fall() && Float.isFinite(distance) && distance > 0
                        && distance <= contact.fall && Float.compare(multiplier, 1.0F) == 0;
                contact.causeSeen = true;
                if (!observe) frame.invalidate();
                else { contact.inCause = true; contact.causeDistance = distance; contact.causeMultiplier = multiplier; }
                final boolean admitted = observe;
                diagnostic(frame, "cause", () -> "admitted=" + admitted + " distance=" + distance + " multiplier=" + multiplier);
            }
        } catch (RuntimeException | LinkageError failure) { if (frame != null) frame.invalidate(); disable(failure); }
        if (observe) {
            try {
                if (landingHurtAvailable(frame.player) && InertialAnchor.active(frame.player))
                    contact.landingAttributes = attributes(frame.player);
            } catch (RuntimeException | LinkageError failure) { disableLandingHurt(failure); }
        }
        try { return original.call(distance, multiplier, source); }
        finally { if (observe) contact.inCause = false; }
    }

    public static float[] nativeEvent(LivingEntity entity, float distance, float multiplier, Operation<float[]> original) {
        Frame frame = ACTIVE.get(); Contact contact = frame == null ? null : frame.contact;
        boolean observe = false;
        try {
            if (contact != null) {
                observe = entity == frame.player && !contact.eventSeen && contact.unchanged()
                        && Float.compare(distance, contact.causeDistance) == 0 && Float.compare(multiplier, contact.causeMultiplier) == 0;
                contact.eventSeen = true; // Claim before subscribers, including recursive native falls.
                if (!observe) frame.invalidate();
            }
        } catch (RuntimeException | LinkageError failure) { if (frame != null) frame.invalidate(); disable(failure); }
        float[] result = original.call(entity, distance, multiplier);
        final boolean observed = observe;
        diagnostic(frame, "event_return", () -> "observed=" + observed + " result=" + java.util.Arrays.toString(result));
        if (!observe || result == null || result.length != 2 || !Float.isFinite(result[0]) || result[0] <= 0
                || !Float.isFinite(result[1]) || result[1] <= 0) return result;
        try {
            if (!contact.unchanged()) {
                diagnostic(frame, "event_snapshot_changed", () -> "invalid=" + frame.invalid + " " + basicSnapshot(frame.player));
                return result;
            }
            contact.positiveEvent = true;
            contact.window = true;
            float changed = BrakingTouch.reduceLanding(frame.player, result[0], result[1]);
            diagnostic(frame, "controller_return", () -> "distance=" + result[0] + " changed=" + changed + " claimed=" + contact.claimed);
            if (contact.claimed && contact.valid() && Float.isFinite(changed) && changed >= 0 && changed < result[0])
                return new float[]{changed, result[1]};
        } catch (RuntimeException | LinkageError failure) { frame.invalidate(); disable(failure); }
        finally { contact.window = false; }
        return result;
    }

    private static boolean basic(ServerPlayer p) {
        return p != null && p.getClass() == ServerPlayer.class && p.server.isSameThread() && p.connection != null
                && !p.hasDisconnected() && p.isAlive() && !p.isRemoved() && !p.isSpectator() && !p.isChangingDimension()
                && !p.noPhysics && !p.isNoGravity() && !p.isPassenger() && !p.isVehicle() && !p.isSleeping()
                && !p.getAbilities().flying && !p.getAbilities().mayfly && !p.getAbilities().invulnerable
                && !p.isFallFlying() && (p.getPose() == Pose.STANDING || p.getPose() == Pose.CROUCHING) && !p.isAutoSpinAttack() && !p.isInPowderSnow
                && !p.isInWaterOrBubble() && !p.isInLava() && !p.isSwimming() && p.getMaxHeightFluidType().isAir()
                && !p.hasEffect(MobEffects.LEVITATION) && !p.hasEffect(MobEffects.SLOW_FALLING)
                && p.getGravity() == .08 && finite(p.position()) && finite(p.getDeltaMovement())
                && Float.isFinite(p.fallDistance) && p.fallDistance >= 0 && loaded(p.serverLevel(), p.getBoundingBox());
    }
    /** Same physical restrictions as basic; only an already bound NEW422 hurt may omit the alive test. */
    private static boolean physicalBasic(ServerPlayer p) {
        return p != null && p.getClass() == ServerPlayer.class && p.server.isSameThread() && p.connection != null
                && !p.hasDisconnected() && !p.isRemoved() && !p.isSpectator() && !p.isChangingDimension()
                && !p.noPhysics && !p.isNoGravity() && !p.isPassenger() && !p.isVehicle() && !p.isSleeping()
                && !p.getAbilities().flying && !p.getAbilities().mayfly && !p.getAbilities().invulnerable
                && !p.isFallFlying() && (p.getPose() == Pose.STANDING || p.getPose() == Pose.CROUCHING) && !p.isAutoSpinAttack() && !p.isInPowderSnow
                && !p.isInWaterOrBubble() && !p.isInLava() && !p.isSwimming() && p.getMaxHeightFluidType().isAir()
                && !p.hasEffect(MobEffects.LEVITATION) && !p.hasEffect(MobEffects.SLOW_FALLING)
                && p.getGravity() == .08 && finite(p.position()) && finite(p.getDeltaMovement())
                && Float.isFinite(p.fallDistance) && p.fallDistance >= 0 && loaded(p.serverLevel(), p.getBoundingBox());
    }
    /** Native connection tick restores XYZ after doTick without restoring its collision-derived onGround flag.
     * Prove the captured origin is physically unsupported instead of treating that stale flag as geometry. */
    private static boolean unsupportedOrigin(Frame frame, ServerPlayer p) {
        AABB box = frame.body;
        AABB feet = new AABB(box.minX, box.minY - 1.0e-6, box.minZ, box.maxX, box.minY, box.maxZ);
        Vec3 velocity = p.getDeltaMovement(); Pose pose = p.getPose();
        if (!loaded(frame.level, box) || !loaded(frame.level, feet)
                || !frame.level.noCollision(p, box.deflate(EPS)) || !frame.level.noCollision(p, feet)
                || frame.level.findSupportingBlock(p, feet).isPresent()) {
            diagnostic(frame, "origin_rejected", () -> "loaded_clear_body_and_unsupported_feet_required body=" + box + " feet=" + feet);
            return false;
        }
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (!frame.level.getFluidState(cell).isEmpty()) {
                diagnostic(frame, "origin_rejected", () -> "body_fluid position=" + cell); return false;
            }
        // Collision shape queries may call external block code. Any mutation retires this observation.
        return frame.owns(p) && frame.origin.equals(p.position()) && frame.body.equals(p.getBoundingBox())
                && Float.compare(frame.fall, p.fallDistance) == 0 && velocity.equals(p.getDeltaMovement()) && pose == p.getPose();
    }
    private static Support support(ServerPlayer p) {
        if (!basic(p)) { diagnostic(ACTIVE.get(), "support_rejected", () -> "basic " + basicSnapshot(p)); return null; }
        AABB box = p.getBoundingBox(); ServerLevel level = p.serverLevel();
        AABB feet = new AABB(box.minX, box.minY - 1.0e-6, box.minZ, box.maxX, box.minY, box.maxZ);
        if (!loaded(level, feet)) { diagnostic(ACTIVE.get(), "support_rejected", () -> "feet_not_loaded " + feet); return null; }
        if (!level.noCollision(p, box.deflate(EPS))) { diagnostic(ACTIVE.get(), "support_rejected", () -> "body_collision " + box); return null; }
        var found = level.findSupportingBlock(p, feet);
        if (found.isEmpty()) { diagnostic(ACTIVE.get(), "support_rejected", () -> "no_support " + feet); return null; }
        BlockPos pos = found.get(); BlockState state = level.getBlockState(pos);
        if (state.getBlock().getClass() != Block.class || !state.isCollisionShapeFullBlock(level, pos)
                || !state.getFluidState().isEmpty() || !level.mayInteract(p, pos)
                || Math.abs(box.minY - (pos.getY() + 1.0)) > EPS) {
            diagnostic(ACTIVE.get(), "support_rejected", () -> "block_policy position=" + pos + " state=" + state
                    + " class=" + state.getBlock().getClass().getName() + " heightDelta=" + (box.minY - (pos.getY() + 1.0)));
            return null;
        }
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
            if (!level.getFluidState(cell).isEmpty() || !level.mayInteract(p, cell)) {
                diagnostic(ACTIVE.get(), "support_rejected", () -> "body_fluid_or_permission position=" + cell); return null;
            }
        // A player can stand partly over an edge; use the actual overlapping support patch, not the foot center over air.
        double contactX = (Math.max(box.minX, pos.getX()) + Math.min(box.maxX, pos.getX() + 1.0)) * .5;
        double contactZ = (Math.max(box.minZ, pos.getZ()) + Math.min(box.maxZ, pos.getZ() + 1.0)) * .5;
        diagnostic(ACTIVE.get(), "support", () -> "position=" + pos + " state=" + state + " body=" + box);
        return new Support(pos.immutable(), state, new Vec3(contactX, box.minY, contactZ));
    }
    private static boolean supportUnchanged(ServerPlayer p, Support expected) {
        // No callbacks or collision recomputation here: support geometry was frozen from the reviewed full cube.
        if (!loaded(p.serverLevel(), p.getBoundingBox()) || !p.serverLevel().hasChunkAt(expected.pos)
                || p.serverLevel().getBlockState(expected.pos) != expected.state || !p.serverLevel().mayInteract(p, expected.pos)) return false;
        AABB box = p.getBoundingBox();
        return Math.abs(box.minY - (expected.pos.getY() + 1.0)) <= EPS && box.maxX > expected.pos.getX()
                && box.minX < expected.pos.getX() + 1 && box.maxZ > expected.pos.getZ() && box.minZ < expected.pos.getZ() + 1;
    }
    private static boolean loaded(ServerLevel world, AABB box) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX) || !Double.isFinite(box.minY) || !Double.isFinite(box.maxY)
                || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ) || box.getXsize() > 4 || box.getYsize() > 4 || box.getZsize() > 4
                || box.minY < world.getMinBuildHeight() || box.maxY >= world.getMaxBuildHeight() || !world.getWorldBorder().isWithinBounds(box)) return false;
        for (int x = ((int)Math.floor(box.minX)) >> 4; x <= ((int)Math.floor(box.maxX)) >> 4; x++)
            for (int z = ((int)Math.floor(box.minZ)) >> 4; z <= ((int)Math.floor(box.maxZ)) >> 4; z++) if (!world.hasChunk(x, z)) return false;
        return true;
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static Map<Holder<MobEffect>, Tag> effects(ServerPlayer p) {
        Map<Holder<MobEffect>, Tag> snapshot = new HashMap<>();
        p.getActiveEffectsMap().forEach((effect, instance) -> snapshot.put(effect, instance.save()));
        return Map.copyOf(snapshot);
    }
    private static boolean close(Vec3 a, Vec3 b) { return finite(a) && finite(b) && a.distanceToSqr(b) <= EPS * EPS; }
    /** Default-off observations only. They cannot create a proof, change admission, or retire an arm. */
    private static boolean diagnosticArmed(ServerPlayer p) {
        if (!DIAGNOSTIC || diagnosticPackets >= 256 || p == null) return false;
        try { return BrakingTouch.diagnosticLandingArmed(p); }
        catch (RuntimeException | LinkageError ignored) { return false; }
    }
    private static void diagnostic(Frame frame, String stage, Supplier<String> detail) {
        if (frame == null || frame.diagnostic == null || frame.diagnostic.length() > 12000) return;
        try { frame.diagnostic.append('[').append(stage).append(' ').append(detail.get()).append("] "); }
        catch (RuntimeException | LinkageError ignored) { /* Diagnostics never change the native result. */ }
    }
    private static void diagnosticEmit(Frame frame) {
        if (frame.diagnostic == null || diagnosticPackets >= 256) return;
        try {
            diagnosticPackets++;
            LOG.info("NEW413_PROOF tick={} entity={} moved={} acceptedOrdinal0={} invalid={} contact={} trace={}",
                    frame.level.getGameTime(), frame.player.getId(), frame.moveSeen, frame.acceptedSeen,
                    frame.invalid, frame.contact != null, frame.diagnostic);
        } catch (RuntimeException | LinkageError ignored) { /* Diagnostics never change the native result. */ }
    }
    private static void diagnosticRejectedPacket(ServerPlayer p, boolean nested) {
        if (!diagnosticArmed(p)) return;
        try {
            diagnosticPackets++;
            LOG.info("NEW413_PROOF tick={} entity={} packetFrame=false nested={} available={} state={}",
                    p.serverLevel().getGameTime(), p.getId(), nested, available(p), basicSnapshot(p));
        } catch (RuntimeException | LinkageError ignored) { /* Diagnostics never change the native result. */ }
    }
    private static String boxDelta(AABB before, AABB after) {
        if (before == null) return "missing";
        return "min(" + (after.minX - before.minX) + "," + (after.minY - before.minY) + "," + (after.minZ - before.minZ)
                + ")max(" + (after.maxX - before.maxX) + "," + (after.maxY - before.maxY) + "," + (after.maxZ - before.maxZ) + ")";
    }
    private static String basicSnapshot(ServerPlayer p) {
        return "class=" + p.getClass().getName() + " sameThread=" + p.server.isSameThread()
                + " disconnected=" + p.hasDisconnected() + " alive=" + p.isAlive() + " removed=" + p.isRemoved()
                + " spectator=" + p.isSpectator() + " dimensionChange=" + p.isChangingDimension()
                + " noPhysics=" + p.noPhysics + " noGravity=" + p.isNoGravity() + " passenger=" + p.isPassenger()
                + " vehicle=" + p.isVehicle() + " sleeping=" + p.isSleeping() + " flying=" + p.getAbilities().flying
                + " mayfly=" + p.getAbilities().mayfly + " invulnerable=" + p.getAbilities().invulnerable
                + " fallFlying=" + p.isFallFlying() + " pose=" + p.getPose() + " spin=" + p.isAutoSpinAttack()
                + " powderSnow=" + p.isInPowderSnow + " water=" + p.isInWaterOrBubble() + " lava=" + p.isInLava()
                + " swimming=" + p.isSwimming() + " fluidAir=" + p.getMaxHeightFluidType().isAir()
                + " levitation=" + p.hasEffect(MobEffects.LEVITATION) + " slowFalling=" + p.hasEffect(MobEffects.SLOW_FALLING)
                + " gravity=" + p.getGravity() + " grounded=" + p.onGround() + " fall=" + p.fallDistance
                + " velocity=" + p.getDeltaMovement();
    }
    private static void disable(Throwable failure) { NativeBrakingTouchMixinPlugin.disableAfterCallbackFailure(failure); }
    private static void disableLandingHurt(Throwable failure) {
        NativeBrakingTouchMixinPlugin.disableLandingHurtAfterCallbackFailure(failure);
    }
}
