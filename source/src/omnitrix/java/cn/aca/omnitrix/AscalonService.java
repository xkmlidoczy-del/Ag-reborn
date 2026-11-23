package cn.aca.omnitrix;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class AscalonService {
    private static final ResourceLocation MODIFIER = Omnitrix.id("ascalon_knight");
    private static final Holder<Attribute> FLIGHT = NeoForgeMod.CREATIVE_FLIGHT;
    public static final double STORM_DOME_RADIUS = 7.2;
    public static final int STORM_DOME_TICKS = 140;
    public static final int PHANTOM_ARRAY_TICKS = 220;
    public static final int SYZYGY_SKY_TICKS = 240;

    /** Viltrumite 3D Leverage Flight Constants: 1.0x (0.55 b/t = 11 m/s) -> 9.0x (4.95 b/t = 99 m/s). */
    public static final float MAX_FLIGHT_MULTIPLIER = 9.0f;
    public static final double BASE_FLIGHT_BLOCKS_PER_TICK = 0.55;

    private static final class FlightDrive {
        volatile float prevBoost;
        volatile float boost;
    }

    /**
     * Identity-isolated weak player key: Minecraft's Entity.equals() compares entity IDs, which collides
     * Singleplayer's client LocalPlayer and integrated-server ServerPlayer in standard Maps!
     */
    private static final class PlayerInstanceKey extends WeakReference<Player> {
        private final int identityHash;

        PlayerInstanceKey(Player referent, ReferenceQueue<Player> queue) {
            super(referent, queue);
            this.identityHash = System.identityHashCode(referent);
        }

        PlayerInstanceKey(Player lookupOnly) {
            super(lookupOnly);
            this.identityHash = System.identityHashCode(lookupOnly);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof PlayerInstanceKey other)) return false;
            Player a = this.get();
            Player b = other.get();
            return a != null && a == b;
        }
    }

    private record SealDomain(UUID ownerId, Vec3 center, double radius, long expiry) {}
    private static final Map<ServerLevel, List<SealDomain>> DOMES = new WeakHashMap<>();
    private static final ReferenceQueue<Player> DRIVE_QUEUE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<PlayerInstanceKey, FlightDrive> FLIGHT_DRIVES = new ConcurrentHashMap<>();

    private AscalonService() {}

    private static void expungeStaleDrives() {
        Object ref;
        while ((ref = DRIVE_QUEUE.poll()) != null) {
            FLIGHT_DRIVES.remove(ref);
        }
    }

    private static FlightDrive getDrive(Player player, boolean create) {
        if (player == null) return null;
        expungeStaleDrives();
        FlightDrive existing = FLIGHT_DRIVES.get(new PlayerInstanceKey(player));
        if (existing != null || !create) return existing;
        return FLIGHT_DRIVES.computeIfAbsent(new PlayerInstanceKey(player, DRIVE_QUEUE), k -> new FlightDrive());
    }

    static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, AscalonService::incomingDamage);
        bus.addListener(EventPriority.HIGHEST, AscalonService::preDamage);
        bus.addListener(EventPriority.HIGHEST, AscalonService::preventDeath);
        bus.addListener(EventPriority.HIGHEST, AscalonService::preventKnockback);
        bus.addListener(EventPriority.HIGHEST, AscalonService::filterEffect);
        bus.addListener(AscalonService::breathe);
        bus.addListener(AscalonService::tickPlayer);
        bus.addListener(AscalonService::tickWorldDomes);
        bus.addListener(AscalonService::login);
        bus.addListener(AscalonService::respawn);
        bus.addListener(AscalonService::changedDimension);
        bus.addListener(AscalonService::cloned);
        bus.addListener(AscalonService::track);
    }

    public static float flightMultiplierFromBoost(float boost) {
        float b = Math.clamp(boost, 0f, 1f);
        float smooth = b * b * (3f - 2f * b);
        return 1.0f + (MAX_FLIGHT_MULTIPLIER - 1.0f) * smooth;
    }

    public static float localFlightBoost(Player player, float partialTick) {
        FlightDrive d = getDrive(player, false);
        if (d == null) return 0f;
        return d.prevBoost + (d.boost - d.prevBoost) * Math.clamp(partialTick, 0f, 1f);
    }

    public static float localFlightMultiplier(Player player, float partialTick) {
        return flightMultiplierFromBoost(localFlightBoost(player, partialTick));
    }

    public static void resetFlightDrive(Player player) {
        FlightDrive d = getDrive(player, false);
        if (d != null) {
            d.prevBoost = 0f;
            d.boost = 0f;
        }
    }

    public static boolean isViltrumFlightActive(Player player) {
        if (player == null || player.isSpectator() || player.isPassenger() || !player.getAbilities().flying || player.isFallFlying()) {
            return false;
        }
        if (player.level().isClientSide) {
            return WristNetwork.client != null && WristNetwork.client.isViltrumPaladin(player);
        }
        return isArmored(player) && (!player.hasData(Omnitrix.WRIST) || !Omnitrix.wrist(player).hasForm());
    }

    /**
     * Viltrumite 3D Leverage Flight Physics:
     * - Strictly runs ONLY on the instance holding real movement input (client LocalPlayer, or headless server FakePlayer).
     *   Integrated-server ServerPlayer NEVER enters here and NEVER collides with LocalPlayer's FlightDrive!
     * - Zero-Slide Smart-Atom Hover Brake: On the VERY FIRST airborne physics tick with zero directional/vertical input,
     *   velocity and boost are cleared to exact 0.0 with zero residual slide, preserving position & flight state.
     * - Progressive Thrust (W): Smoothly ramps from 1.0x (0.55 b/t) up to 9.0x (4.95 b/t) along the 3D gaze vector!
     */
    public static boolean viltrumTravel(Player player, Vec3 input, boolean jumping) {
        boolean hasInputAuthority = player.level().isClientSide
                ? player.isLocalPlayer()
                : (player instanceof FakePlayer);
        if (!hasInputAuthority) {
            return false;
        }
        if (!isViltrumFlightActive(player)) {
            resetFlightDrive(player);
            return false;
        }

        FlightDrive drive = getDrive(player, true);
        boolean forward = input.z > 0.05;
        boolean backward = input.z < -0.05;
        boolean strafe = Math.abs(input.x) > 0.05;
        boolean descend = player.isShiftKeyDown();
        boolean anyMoveInput = forward || backward || strafe || jumping || descend;

        // Instant Smart-Atom Hover Brake: 0.000-block slide on the very first tick all movement keys are released!
        if (!anyMoveInput) {
            drive.prevBoost = 0f;
            drive.boost = 0f;
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            player.calculateEntityAnimation(false);
            if (player instanceof ServerPlayer sp && sp.hasData(Omnitrix.ASCALON_STATE)) {
                Omnitrix.ascalon(sp).flightBoost = 0f;
            }
            return true;
        }

        drive.prevBoost = drive.boost;
        double yawRad = Math.toRadians(player.getYRot());
        Vec3 leftFlat = new Vec3(Math.cos(yawRad), 0.0, Math.sin(yawRad));
        Vec3 fwdFlat = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));

        Vec3 nextVel;
        if (forward) {
            // Progressive Viltrumite Acceleration: 1.0x -> 9.0x (32 ticks / 1.6s with sprint, 54 ticks / 2.7s normal cruise)
            float rampStep = player.isSprinting() ? (1.0f / 32.0f) : (1.0f / 54.0f);
            drive.boost = Math.min(1.0f, drive.boost + rampStep);
            float mult = flightMultiplierFromBoost(drive.boost);

            Vec3 look = player.getLookAngle().normalize();
            double vTrim = (jumping ? 0.36 : 0.0) - (descend ? 0.36 : 0.0);
            Vec3 wish = look.scale(input.z).add(leftFlat.scale(input.x * 0.42)).add(0.0, vTrim, 0.0);
            Vec3 dir = wish.lengthSqr() > 1e-5 ? wish.normalize() : look;
            Vec3 targetVel = dir.scale(BASE_FLIGHT_BLOCKS_PER_TICK * mult);
            nextVel = player.getDeltaMovement().lerp(targetVel, 0.38);
        } else {
            // Non-forward maneuver (Space climb, Shift descend, strafe, or S backstep): reset boost cleanly without slide!
            drive.boost = 0f;
            drive.prevBoost = 0f;
            Vec3 horiz = fwdFlat.scale(input.z).add(leftFlat.scale(input.x));
            double vy = (jumping ? 0.50 : 0.0) - (descend ? 0.50 : 0.0);
            Vec3 targetHover = (horiz.lengthSqr() > 1e-5 ? horiz.normalize().scale(BASE_FLIGHT_BLOCKS_PER_TICK) : Vec3.ZERO)
                    .add(0.0, vy, 0.0);
            Vec3 curVel = player.getDeltaMovement();
            nextVel = curVel.length() > BASE_FLIGHT_BLOCKS_PER_TICK * 1.15
                    ? targetHover
                    : curVel.lerp(targetHover, 0.62);
        }

        player.setDeltaMovement(nextVel);
        player.move(MoverType.SELF, nextVel);

        if ((player.horizontalCollision || player.verticalCollision) && player.getDeltaMovement().length() < 0.18 && forward) {
            drive.boost *= 0.65f;
        }
        if (player.onGround() && descend) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            drive.boost = 0f;
            drive.prevBoost = 0f;
        }
        player.resetFallDistance();
        player.calculateEntityAnimation(false);

        if (player instanceof ServerPlayer sp && sp.hasData(Omnitrix.ASCALON_STATE)) {
            Omnitrix.ascalon(sp).flightBoost = drive.boost;
        }
        return true;
    }

    public static boolean holdingAscalon(Player p) {
        if (p == null || p.getInventory() == null) return false;
        return p.getMainHandItem().is(Omnitrix.ASCALON.get()) || p.getOffhandItem().is(Omnitrix.ASCALON.get());
    }

    public static boolean hasAscalonInInventory(Player p) {
        if (p == null || p.getInventory() == null) return false;
        if (holdingAscalon(p)) return true;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Omnitrix.ASCALON.get())) return true;
        }
        return false;
    }

    public static boolean isArmored(Entity entity) {
        return entity instanceof Player p && p.getInventory() != null
                && p.hasData(Omnitrix.ASCALON_STATE) && Omnitrix.ascalon(p).armored;
    }

    public static boolean isProtected(Entity entity) {
        return entity instanceof Player p && p.getInventory() != null && (isArmored(p) || holdingAscalon(p));
    }

    public static boolean blockForcedRemoval(ServerPlayer sp) {
        return sp.server != null && sp.server.isRunning() && isProtected(sp);
    }

    public static void onInterceptedHurt(Player player, DamageSource source, float amount) {
        if (!(player instanceof ServerPlayer sp)) return;
        var state = Omnitrix.ascalon(sp);
        long now = WristService.time(sp);
        boolean guarding = sp.isUsingItem() && holdingAscalon(sp);
        state.addCharge(guarding ? 12f : 6f);
        if (guarding) {
            state.parryCounterUntil = now + 55;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && living != sp && Abilities.validTarget(sp, living, false)) {
            if (guarding || state.armored) {
                fundamentalCleave(sp, living, guarding ? 85_000_000f : 35_000_000f, false,
                        living.position().subtract(sp.position()), 1.45);
            }
        }
        if (now - state.lastParryCueAt >= 5) {
            state.lastParryCueAt = now;
            Vec3 hitPos = attacker != null ? attacker.getEyePosition() : sp.getEyePosition().add(sp.getLookAngle().scale(1.15));
            WristNetwork.cue(sp, WristNetwork.ASCALON_PARRY_CUE, guarding ? 2 : 0, WristNetwork.FLAG_HIT_ENTITY,
                    sp.position().add(0, 1.18, 0), hitPos);
            sp.level().playSound(null, sp.blockPosition(), Omnitrix.ASCALON_PARRY.get(), SoundSource.PLAYERS, .88f, guarding ? 1.18f : 1.04f);
            WristNetwork.syncAscalon(sp);
        }
    }

    private static void incomingDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof Player p && isProtected(p)) {
            onInterceptedHurt(p, e.getSource(), e.getAmount());
            e.setCanceled(true);
        }
    }

    private static void preDamage(LivingDamageEvent.Pre e) {
        if (e.getEntity() instanceof Player p && isProtected(p)) {
            e.setNewDamage(0f);
        }
    }

    private static void preventDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof Player p && isProtected(p)) {
            e.setCanceled(true);
            p.deathTime = 0;
            p.hurtTime = 0;
            WristService.reviveAtFullHealth(p);
        }
    }

    private static void preventKnockback(LivingKnockBackEvent e) {
        if (e.getEntity() instanceof Player p && isProtected(p)) {
            e.setCanceled(true);
        }
    }

    private static void filterEffect(MobEffectEvent.Applicable e) {
        if (e.getEntity() instanceof Player p && isProtected(p)
                && !e.getEffectInstance().getEffect().value().isBeneficial()) {
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static void breathe(LivingBreatheEvent e) {
        if (e.getEntity() instanceof Player p && isProtected(p)) {
            e.setCanBreathe(true);
            e.setRefillAirAmount(4);
        }
    }

    public static void onOmnitrixTransformStart(ServerPlayer player) {
        var state = Omnitrix.ascalon(player);
        state.clearCast();
        if (state.armored) {
            retractArmor(player, true);
        }
    }

    public static void retractArmor(ServerPlayer player, boolean fromMorph) {
        var state = Omnitrix.ascalon(player);
        if (!state.armored) return;
        state.armored = false;
        state.flightBoost = 0f;
        state.machTier = 0;
        state.flightIdleTicks = 0;
        state.smoothedServerSpeed = 0.0;
        state.lastFlightPos = null;
        resetFlightDrive(player);
        state.clearCast();
        state.phantomArrayUntil = 0L;
        state.transitionTick = WristService.time(player);
        state.revision++;
        applyAttributes(player, false);
        player.level().playSound(null, player.blockPosition(), Omnitrix.ASCALON_ARMOR_OFF.get(), SoundSource.PLAYERS, .88f, 1.0f);
        WristNetwork.cue(player, WristNetwork.ASCALON_ARMOR_CUE, fromMorph ? 2 : 0, 0, player.position(), player.getEyePosition());
        WristNetwork.syncAscalon(player);
    }

    public static void toggleArmor(ServerPlayer player) {
        if (!holdingAscalon(player)) {
            if (recallToHand(player)) {
                if (Omnitrix.ascalon(player).armored) return;
            } else {
                return;
            }
        }
        var state = Omnitrix.ascalon(player);
        if (state.armored) {
            retractArmor(player, false);
            return;
        }
        WristService.forceHumanForAscalon(player);
        long now = WristService.time(player);
        state.armored = true;
        state.flightBoost = 0f;
        state.machTier = 0;
        state.flightIdleTicks = 0;
        state.smoothedServerSpeed = 0.0;
        state.lastFlightPos = player.position();
        state.transitionTick = now;
        state.siphonCharge = Math.max(state.siphonCharge, 100f);
        state.revision++;
        applyAttributes(player, true);
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 40f));
        player.clearFire();
        player.resetFallDistance();
        player.level().playSound(null, player.blockPosition(), Omnitrix.ASCALON_ARMOR_ON.get(), SoundSource.PLAYERS, .96f, 1.0f);
        WristNetwork.cue(player, WristNetwork.ASCALON_ARMOR_CUE, 1, WristNetwork.FLAG_HIT_GROUND,
                player.position(), player.getEyePosition());
        WristNetwork.syncAscalon(player);
    }

    static boolean recallItemEntity(ServerPlayer player, ItemEntity itemEntity) {
        ItemStack copy = itemEntity.getItem().copy();
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, copy);
        } else if (!player.getInventory().add(copy)) {
            return false;
        }
        Vec3 from = itemEntity.position();
        itemEntity.discard();
        player.level().playSound(null, player.blockPosition(), Omnitrix.ASCALON_CLEAVE.get(), SoundSource.PLAYERS, .80f, 1.18f);
        WristNetwork.cue(player, WristNetwork.ASCALON_PARRY_CUE, 1, 0, from, player.getEyePosition());
        return true;
    }

    private static boolean recallToHand(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Omnitrix.ASCALON.get())) {
                ItemStack held = player.getMainHandItem();
                player.setItemInHand(InteractionHand.MAIN_HAND, s.copy());
                inv.setItem(i, held);
                player.level().playSound(null, player.blockPosition(), Omnitrix.ASCALON_CLEAVE.get(), SoundSource.PLAYERS, .80f, 1.15f);
                WristNetwork.cue(player, WristNetwork.ASCALON_PARRY_CUE, 1, 0, player.position(), player.getEyePosition());
                return true;
            }
        }
        ServerLevel sl = player.serverLevel();
        var nearbySword = sl.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(96.0),
                ie -> ie.isAlive() && ie.getItem().is(Omnitrix.ASCALON.get()))
                .stream().min(Comparator.comparingDouble(ie -> ie.distanceToSqr(player))).orElse(null);
        return nearbySword != null && recallItemEntity(player, nearbySword);
    }

    static void handleRequest(ServerPlayer player, WristNetwork.Request req) {
        if (!player.isAlive() || player.isSpectator()) return;
        switch (req.action()) {
            case WristNetwork.ASCALON_TOGGLE -> toggleArmor(player);
            case WristNetwork.ASCALON_SWING -> {
                if (holdingAscalon(player)) swingCleave(player, null);
            }
            case WristNetwork.ASCALON_CHARGE -> {
                if (canCastSwordSkill(player)) setIaidoCharge(player, req.value() != 0);
            }
            case WristNetwork.ASCALON_PRIMARY, WristNetwork.ASCALON_SECONDARY, WristNetwork.ASCALON_SYZYGY -> {
                if (!canCastSwordSkill(player)) return;
                var state = Omnitrix.ascalon(player);
                long now = WristService.time(player);
                if (req.action() == WristNetwork.ASCALON_PRIMARY && (req.value() & 1) == 0
                        && state.iaidoChargeSince != Long.MIN_VALUE) setIaidoCharge(player, false);
                long ready = swordReadyAt(state, req);
                if (ready <= now) {
                    state.bufferedCast = null;
                    releaseSwordCast(player, req);
                } else if (ready - now <= Abilities.INPUT_BUFFER_TICKS) {
                    state.bufferedCast = new AscalonState.BufferedCast(req, now + Abilities.INPUT_BUFFER_TICKS);
                }
            }
            default -> {}
        }
    }

    private static long swordReadyAt(AscalonState state, WristNetwork.Request request) {
        boolean alternate = (request.value() & 1) != 0;
        if (request.action() == WristNetwork.ASCALON_PRIMARY) return alternate ? state.tempestAt : state.slashAt;
        if (request.action() == WristNetwork.ASCALON_SECONDARY && !alternate) return state.riftAt;
        return state.syzygyAt;
    }

    private static void releaseSwordCast(ServerPlayer player, WristNetwork.Request request) {
        boolean alternate = (request.value() & 1) != 0;
        if (request.action() == WristNetwork.ASCALON_PRIMARY) {
            int hold = Math.min(120, request.value() >>> 1);
            if (alternate) tempestDome(player);
            else if (hold >= 10) castAstralIaido(player, hold);
            else crescentSlash(player);
        } else if (request.action() == WristNetwork.ASCALON_SECONDARY && !alternate) {
            dimensionalRift(player);
        } else {
            syzygyAlignment(player);
        }
    }

    public static void setIaidoCharge(ServerPlayer p, boolean active) {
        var state = Omnitrix.ascalon(p);
        long now = WristService.time(p);
        if (active && state.slashAt - now > Abilities.INPUT_BUFFER_TICKS) return;
        state.iaidoChargeSince = active ? now : Long.MIN_VALUE;
        state.revision++;
        if (active) {
            p.level().playSound(null, p.blockPosition(), Omnitrix.ASCALON_ARMOR_OFF.get(), SoundSource.PLAYERS, .52f, 1.48f);
        }
        WristNetwork.syncAscalon(p);
    }

    private static boolean canCastSwordSkill(ServerPlayer p) {
        var w = Omnitrix.wrist(p);
        return p.isAlive() && !p.isSpectator() && !w.hasForm()
                && w.stage != Wrist.Stage.ARMING && (isArmored(p) || holdingAscalon(p));
    }

    private static void tickPlayer(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.hasData(Omnitrix.ASCALON_STATE)) return;
        var state = Omnitrix.ascalon(p);
        var wrist = Omnitrix.wrist(p);
        long now = WristService.time(p);

        if (state.armored && (wrist.hasForm() || wrist.stage == Wrist.Stage.ARMING)) {
            retractArmor(p, true);
            return;
        }
        if (state.armored && !hasAscalonInInventory(p)) {
            if (!recallToHand(p)) {
                retractArmor(p, false);
                return;
            }
        }
        if (state.iaidoChargeSince != Long.MIN_VALUE && (now - state.iaidoChargeSince > 120 || !holdingAscalon(p))) {
            state.iaidoChargeSince = Long.MIN_VALUE;
            WristNetwork.syncAscalon(p);
        }
        var buffered = state.bufferedCast;
        if (buffered != null) {
            if (!canCastSwordSkill(p) || buffered.expires() < now) {
                state.bufferedCast = null;
            } else if (swordReadyAt(state, buffered.request()) <= now) {
                state.bufferedCast = null;
                releaseSwordCast(p, buffered.request());
            }
        }

        boolean prot = isProtected(p);
        if (!prot) {
            state.flightBoost = 0f;
            state.machTier = 0;
            state.flightIdleTicks = 0;
            state.smoothedServerSpeed = 0.0;
            state.lastFlightPos = null;
            return;
        }

        p.clearFire();
        p.resetFallDistance();
        p.deathTime = 0;
        p.hurtTime = 0;
        if (!AcademyInterop.nativeCapacity(p) && p.getHealth() < p.getMaxHealth()) p.heal(p.getMaxHealth() - p.getHealth());
        if (state.armored && p.getAbsorptionAmount() < 40f && now % 20 == 0) {
            p.setAbsorptionAmount(40f);
        }
        if (p.getFoodData().getFoodLevel() < 20) p.getFoodData().setFoodLevel(20);
        if (p.getFoodData().getSaturationLevel() < 20f) p.getFoodData().setSaturation(20f);
        for (var eff : List.copyOf(p.getActiveEffects())) {
            if (!eff.getEffect().value().isBeneficial()) p.removeEffect(eff.getEffect());
        }

        ServerLevel level = p.serverLevel();
        if (p.getY() < level.getMinBuildHeight() - 18) {
            Vec3 from = p.position();
            double safeY = Math.max(level.getMinBuildHeight() + 76, level.getSeaLevel() + 18);
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
            p.moveTo(p.getX(), safeY, p.getZ(), p.getYRot(), p.getXRot());
            if (p.connection != null) p.connection.teleport(p.getX(), safeY, p.getZ(), p.getYRot(), p.getXRot());
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            WristNetwork.cue(p, WristNetwork.ASCALON_RIFT_CUE, 0, 0, from, p.position());
            level.playSound(null, p.blockPosition(), Omnitrix.ASCALON_RIFT.get(), SoundSource.PLAYERS, .95f, 1.05f);
        }

        // Track Server-Observed Viltrumite Flight Speed, 3x/6x/9x Sonic-Astrolabe Cues & Hypersonic Ramming Wake!
        if (state.armored && p.getAbilities().flying && !p.onGround()) {
            Vec3 curPos = p.position();
            Vec3 frameDelta = state.lastFlightPos == null ? Vec3.ZERO : curPos.subtract(state.lastFlightPos);
            state.lastFlightPos = curPos;

            double speedBlocksPerTick;
            float effectiveMult;
            if (p instanceof FakePlayer) {
                speedBlocksPerTick = p.getDeltaMovement().length();
                float measuredMult = (float) Math.clamp(speedBlocksPerTick / BASE_FLIGHT_BLOCKS_PER_TICK, 1.0, MAX_FLIGHT_MULTIPLIER);
                effectiveMult = Math.max(measuredMult, flightMultiplierFromBoost(state.flightBoost));
                state.flightIdleTicks = speedBlocksPerTick < 0.03 ? state.flightIdleTicks + 1 : 0;
            } else {
                double rawStep = frameDelta.length() < 15.0 ? frameDelta.length() : 0.0;
                if (rawStep > 0.03) {
                    double capped = Math.min(rawStep, BASE_FLIGHT_BLOCKS_PER_TICK * MAX_FLIGHT_MULTIPLIER);
                    if (state.flightIdleTicks == 1 && rawStep > BASE_FLIGHT_BLOCKS_PER_TICK * MAX_FLIGHT_MULTIPLIER * 1.08) {
                        capped = Math.min(rawStep * 0.5, BASE_FLIGHT_BLOCKS_PER_TICK * MAX_FLIGHT_MULTIPLIER);
                    }
                    state.flightIdleTicks = 0;
                    state.smoothedServerSpeed = capped >= state.smoothedServerSpeed
                            ? capped
                            : (state.smoothedServerSpeed * 0.62 + capped * 0.38);
                } else {
                    state.flightIdleTicks++;
                    if (state.flightIdleTicks >= 3) {
                        state.smoothedServerSpeed = 0.0;
                    }
                }
                speedBlocksPerTick = state.smoothedServerSpeed;
                effectiveMult = (float) Math.clamp(speedBlocksPerTick / BASE_FLIGHT_BLOCKS_PER_TICK, 1.0, MAX_FLIGHT_MULTIPLIER);
                float nextBoost = speedBlocksPerTick < 0.06
                        ? 0f
                        : Math.clamp((effectiveMult - 1.0f) / (MAX_FLIGHT_MULTIPLIER - 1.0f), 0f, 1f);
                if (Math.abs(nextBoost - state.flightBoost) > 0.08f || (nextBoost == 0f && state.flightBoost > 0f)) {
                    state.flightBoost = nextBoost;
                    WristNetwork.syncAscalon(p);
                } else {
                    state.flightBoost = nextBoost;
                }
            }

            int newTier = effectiveMult >= 8.85f ? 3 : effectiveMult >= 5.85f ? 2 : effectiveMult >= 2.95f ? 1 : 0;
            if (newTier > state.machTier && (newTier > 1 || now - state.lastMachCueAt >= 8)) {
                state.machTier = newTier;
                state.lastMachCueAt = now;
                Vec3 dir = frameDelta.lengthSqr() > 1e-4 ? frameDelta.normalize() : p.getLookAngle().normalize();
                Vec3 chest = curPos.add(0, 1.05, 0);
                WristNetwork.cue(p, WristNetwork.ASCALON_MACH_CUE, newTier,
                        newTier == 3 ? WristNetwork.FLAG_HIT_GROUND : 0, chest, chest.add(dir.scale(4.0)));
                level.playSound(null, p.blockPosition(), Omnitrix.ASCALON_RIFT.get(),
                        SoundSource.PLAYERS, 0.62f + newTier * 0.14f, 1.12f + newTier * 0.14f);
                WristNetwork.syncAscalon(p);
            } else if (state.flightIdleTicks >= 3 && effectiveMult < 1.35f && state.machTier != 0) {
                state.machTier = 0;
                WristNetwork.syncAscalon(p);
            }

            // Viltrumite Hypersonic Ramming Wake (>= 5.8x speed cleaves straight through hostiles in flight path!)
            if (effectiveMult >= 5.8f) {
                Vec3 dir = frameDelta.lengthSqr() > 1e-4 ? frameDelta.normalize() : p.getLookAngle().normalize();
                AABB wakeBox = p.getBoundingBox().inflate(2.2, 1.5, 2.2).expandTowards(dir.scale(2.5));
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, wakeBox,
                        e -> e != p && Abilities.validTarget(p, e, false))) {
                    if (fundamentalCleave(p, target, 95_000_000f, false, dir, 2.4)) {
                        WristNetwork.cue(p, WristNetwork.ASCALON_CLEAVE_HIT_CUE, 1, WristNetwork.FLAG_HIT_ENTITY,
                                curPos.add(0, 1.0, 0), target.getBoundingBox().getCenter());
                    }
                }
            }
        } else {
            if (state.flightBoost != 0f || state.machTier != 0) {
                state.flightBoost = 0f;
                state.machTier = 0;
                WristNetwork.syncAscalon(p);
            }
            state.flightIdleTicks = 0;
            state.smoothedServerSpeed = 0.0;
            state.lastFlightPos = p.position();
        }

        if (state.armored && now % 10 == 0 && state.siphonCharge < 100f) {
            state.addCharge(2.5f);
            WristNetwork.syncAscalon(p);
        }

        // Active Right-Click Hexagonal Heraldic Aegis: Deflects projectiles & arms Instant Parry Counter!
        boolean guarding = p.isUsingItem() && holdingAscalon(p);
        if (guarding || (state.armored && now % 2 == 0)) {
            double r = guarding ? 8.8 : 4.6;
            Vec3 center = p.position().add(0, 1.15, 0);
            var incoming = level.getEntitiesOfClass(Projectile.class, AABB.ofSize(center, r * 2, r * 2, r * 2),
                    proj -> !(proj instanceof AscalonSlash) && proj.getOwner() != p && proj.distanceToSqr(center) <= r * r);
            boolean parried = false;
            for (Projectile proj : incoming) {
                Entity shooter = proj.getOwner();
                Vec3 aimBack;
                if (shooter instanceof LivingEntity le && le != p && le.isAlive()) {
                    aimBack = le.getEyePosition().subtract(proj.position()).normalize();
                } else {
                    LivingEntity nearest = nearbyHostiles(p, center, 36.0).stream().findFirst().orElse(null);
                    aimBack = nearest != null
                            ? nearest.getEyePosition().subtract(proj.position()).normalize()
                            : p.getLookAngle().normalize();
                }
                double speed = Math.max(2.55, proj.getDeltaMovement().length() * 3.1);
                proj.setOwner(p);
                proj.setDeltaMovement(aimBack.scale(speed));
                proj.hurtMarked = true;
                if (proj instanceof AbstractArrow arrow) {
                    arrow.setBaseDamage(Math.max(arrow.getBaseDamage() * 9.0, 280_000.0));
                }
                parried = true;
                WristNetwork.cue(p, WristNetwork.ASCALON_PARRY_CUE, guarding ? 2 : 0, WristNetwork.FLAG_PROJECTILE,
                        center, proj.position());
            }
            if (parried) {
                state.addCharge(14f);
                if (guarding) state.parryCounterUntil = now + 55;
                if (now - state.lastParryCueAt >= 5) {
                    state.lastParryCueAt = now;
                    level.playSound(null, p.blockPosition(), Omnitrix.ASCALON_PARRY.get(), SoundSource.PLAYERS, .94f, 1.14f);
                }
                WristNetwork.syncAscalon(p);
            }
        }
    }

    private static void tickWorldDomes(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var list = DOMES.get(level);
            if (list == null || list.isEmpty()) continue;
            Iterator<SealDomain> it = list.iterator();
            while (it.hasNext()) {
                SealDomain dome = it.next();
                if (now >= dome.expiry) {
                    it.remove();
                    continue;
                }
                Entity ownerEnt = level.getEntity(dome.ownerId);
                if (!(ownerEnt instanceof ServerPlayer owner) || !owner.isAlive()) continue;

                double r = dome.radius;
                Vec3 c = dome.center;
                List<LivingEntity> trapped = level.getEntitiesOfClass(LivingEntity.class,
                        AABB.ofSize(c, r * 2.3, r * 2.3, r * 2.3),
                        e -> e != owner && Abilities.validTarget(owner, e, false) && e.distanceToSqr(c) <= (r + 1.4) * (r + 1.4));

                for (LivingEntity target : trapped) {
                    double d = target.position().distanceTo(c);
                    if (d > r * .68 && !AcademyInterop.holdingShieldArmor(target)) {
                        Vec3 pullIn = c.add(0, .25, 0).subtract(target.position()).normalize().scale(.68);
                        target.setDeltaMovement(pullIn);
                        target.hurtMarked = true;
                    }
                    TemporalEffects.freezeSingle(level, target, now + 8);
                    if (now % 10 == 0) {
                        fundamentalCleave(owner, target, 55_000_000f, false, Vec3.ZERO, 0);
                        WristNetwork.cueNear(level, owner.getUUID(), Kind.CELESTIAL.ordinal(),
                                WristNetwork.ASCALON_DOME_ARC_CUE, WristNetwork.FLAG_HIT_ENTITY,
                                c.add(0, 6.2, 0), target.getBoundingBox().getCenter(), 84);
                    }
                }
            }
        }
    }

    public static boolean fundamentalCleave(ServerPlayer p, LivingEntity target, float amount,
                                            boolean executeAuthority, Vec3 knockDir, double knock) {
        if (target == p || !Abilities.validTarget(p, target, false)) return false;
        if (isProtected(target)) {
            WristNetwork.cue(p, WristNetwork.ASCALON_PARRY_CUE, 1, WristNetwork.FLAG_HIT_ENTITY,
                    p.getEyePosition(), target.getEyePosition());
            return false;
        }
        if (!OmnitrixDamage.hit(p, target, amount, executeAuthority)) return false;
        var state = Omnitrix.ascalon(p);
        float drained = 0f;
        for (var eff : List.copyOf(target.getActiveEffects())) {
            if (eff.getEffect().value().isBeneficial()) {
                target.removeEffect(eff.getEffect());
                drained += 6f;
            }
        }
        if (drained > 0f) state.addCharge(drained);

        target.hurtTime = 0;
        if (knock > 0 && knockDir.lengthSqr() > 1e-6) {
            target.knockback(knock, -knockDir.x, -knockDir.z);
        }
        return true;
    }

    public static void swingCleave(ServerPlayer p, LivingEntity directHit) {
        var state = Omnitrix.ascalon(p);
        long now = WristService.time(p);
        if (now == state.lastSwingAt) return;
        state.lastSwingAt = now;

        if (now < state.parryCounterUntil) {
            executeParryCounterSlash(p, state, now);
            return;
        }

        int combo = state.comboStep;
        state.comboStep = (combo + 1) & 3;

        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        Vec3 chest = p.position().add(0, 1.25, 0);
        boolean finisher = combo == 3;
        boolean hitAny = false;

        if (directHit != null && fundamentalCleave(p, directHit, finisher ? 115_000_000f : 68_000_000f, finisher, look, 1.35)) {
            hitAny = true;
        }
        double sweepRange = combo == 2 ? 7.4 : 6.4;
        double dotThreshold = combo == 2 ? -.85 : -.18;
        for (LivingEntity nearby : nearbyHostiles(p, chest.add(look.scale(combo == 2 ? 1.8 : 3.6)), sweepRange)) {
            if (nearby == directHit) continue;
            Vec3 to = nearby.getBoundingBox().getCenter().subtract(chest);
            if (to.lengthSqr() < 1e-4 || to.normalize().dot(look) >= dotThreshold) {
                if (fundamentalCleave(p, nearby, finisher ? 115_000_000f : 68_000_000f, finisher, look, combo == 1 ? .65 : 1.35)) {
                    hitAny = true;
                    if (combo == 1) {
                        nearby.push(0, .52, 0);
                        nearby.hurtMarked = true;
                    }
                }
            }
        }

        Vec3 remoteEnd = eye.add(look.scale(32.0));
        EntityHitResult ray = ProjectileUtil.getEntityHitResult(p.level(), p, eye, remoteEnd,
                new AABB(eye, remoteEnd).inflate(1.5),
                e -> e instanceof LivingEntity le && le != directHit && Abilities.validTarget(p, le, false), .35f);
        Vec3 focus = chest.add(look.scale(6.4));
        if (ray != null && ray.getEntity() instanceof LivingEntity remoteTarget) {
            focus = remoteTarget.getBoundingBox().getCenter();
            if (fundamentalCleave(p, remoteTarget, finisher ? 125_000_000f : 75_000_000f, finisher, look, 1.15)) {
                hitAny = true;
                WristNetwork.cue(p, WristNetwork.ASCALON_CLEAVE_HIT_CUE, combo, WristNetwork.FLAG_HIT_ENTITY, eye, focus);
            }
        }

        if (now < state.phantomArrayUntil) {
            firePhantomSwordBolts(p, state, now, focus);
        }

        if (hitAny) state.addCharge(12f);
        WristNetwork.syncAscalon(p);
        int flags = (hitAny ? WristNetwork.FLAG_HIT_ENTITY : 0) | (finisher ? WristNetwork.FLAG_HIT_GROUND : 0);
        WristNetwork.cue(p, WristNetwork.ASCALON_SWING_CUE, combo, flags, chest, focus);
        p.level().playSound(null, p.blockPosition(), Omnitrix.ASCALON_CLEAVE.get(),
                SoundSource.PLAYERS, .90f, .94f + combo * .055f);
    }

    private static void executeParryCounterSlash(ServerPlayer p, AscalonState state, long now) {
        state.parryCounterUntil = 0L;
        Vec3 startPos = p.position();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        HitResult clip = p.level().clip(new ClipContext(eye, eye.add(look.scale(12.5)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 endPos = clip.getType() == HitResult.Type.BLOCK
                ? clip.getLocation().subtract(look.scale(1.05))
                : startPos.add(look.multiply(1, .35, 1).scale(11.0));
        endPos = new Vec3(endPos.x, Math.max(p.level().getMinBuildHeight() + 1, endPos.y), endPos.z);

        boolean hitAny = false;
        AABB box = new AABB(startPos, endPos).inflate(5.2, 2.8, 5.2);
        for (LivingEntity target : p.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && Abilities.validTarget(p, e, false))) {
            TemporalEffects.freezeSingle(p.serverLevel(), target, now + 22);
            if (fundamentalCleave(p, target, 235_000_000f, true, look, 1.95)) {
                hitAny = true;
                WristNetwork.cue(p, WristNetwork.ASCALON_CLEAVE_HIT_CUE, 4, WristNetwork.FLAG_HIT_ENTITY,
                        startPos.add(0, 1.2, 0), target.getBoundingBox().getCenter());
            }
        }

        p.moveTo(endPos.x, endPos.y, endPos.z, p.getYRot(), p.getXRot());
        if (p.connection != null) p.connection.teleport(endPos.x, endPos.y, endPos.z, p.getYRot(), p.getXRot());
        state.addCharge(24f);
        WristNetwork.cue(p, WristNetwork.ASCALON_SWING_CUE, 4,
                (hitAny ? WristNetwork.FLAG_HIT_ENTITY : 0) | WristNetwork.FLAG_HIT_GROUND,
                startPos.add(0, 1.2, 0), endPos.add(look.scale(5.0)).add(0, 1.2, 0));
        p.level().playSound(null, BlockPos.containing(endPos), Omnitrix.ASCALON_SLASH_BEAM.get(), SoundSource.PLAYERS, 1.08f, 1.16f);
        WristNetwork.syncAscalon(p);
    }

    public static void crescentSlash(ServerPlayer p) {
        var state = Omnitrix.ascalon(p);
        long now = WristService.time(p);
        if (now < state.slashAt) return;

        int step = now <= state.crescentChainUntil ? state.crescentComboStep : 0;
        state.crescentComboStep = (step + 1) % 3;
        state.crescentChainUntil = now + 38;
        state.slashAt = now + (step < 2 ? 6 : 15);

        Vec3 look = p.getLookAngle().normalize();
        Vec3 origin = p.getEyePosition().add(look.scale(1.15)).add(0, -.14, 0);
        float baseScale = state.armored ? 1.32f : 1.04f;

        if (step < 2) {
            AscalonSlash wave = Omnitrix.ASCALON_SLASH.get().create(p.serverLevel());
            if (wave != null) {
                float roll = step == 0 ? -24f : 24f;
                wave.launch(p, origin, look.scale(2.75), roll, baseScale, 0);
                p.serverLevel().addFreshEntity(wave);
            }
        } else {
            for (float roll : new float[]{-20f, 20f}) {
                AscalonSlash wave = Omnitrix.ASCALON_SLASH.get().create(p.serverLevel());
                if (wave != null) {
                    wave.launch(p, origin, look.scale(2.95), roll, baseScale * 1.18f, 2);
                    p.serverLevel().addFreshEntity(wave);
                }
            }
        }

        if (now < state.phantomArrayUntil) {
            firePhantomSwordBolts(p, state, now, origin.add(look.scale(16.0)));
        }
        WristNetwork.cue(p, WristNetwork.ASCALON_SWING_CUE, step == 2 ? 3 : step, WristNetwork.FLAG_PROJECTILE,
                p.position().add(0, 1.25, 0), origin.add(look.scale(9.0)));
        p.level().playSound(null, p.blockPosition(), Omnitrix.ASCALON_SLASH_BEAM.get(),
                SoundSource.PLAYERS, 1.0f, 0.98f + step * 0.08f);
        WristNetwork.syncAscalon(p);
    }

    public static void castAstralIaido(ServerPlayer p, int heldTicks) {
        var state = Omnitrix.ascalon(p);
        long now = WristService.time(p);
        if (now < state.slashAt) return;
        state.slashAt = now + 22;
        state.iaidoChargeSince = Long.MIN_VALUE;

        Vec3 look = p.getLookAngle().normalize();
        Vec3 chest = p.position().add(0, 1.20, 0);
        Vec3 origin = p.getEyePosition().add(look.scale(1.25)).add(0, -.18, 0);
        float bonus = Math.min(.58f, Math.max(0f, (heldTicks - 10) * .022f));
        float span = (state.armored ? 1.88f : 1.55f) + bonus;

        boolean hitNear = false;
        for (LivingEntity target : nearbyHostiles(p, chest.add(look.scale(4.5)), 8.5)) {
            TemporalEffects.freezeSingle(p.serverLevel(), target, now + 18);
            if (fundamentalCleave(p, target, 260_000_000f, true, look, 2.2)) {
                hitNear = true;
            }
        }

        AscalonSlash iaidoWave = Omnitrix.ASCALON_SLASH.get().create(p.serverLevel());
        if (iaidoWave != null) {
            iaidoWave.launch(p, origin, look.scale(3.35), 4.0f, span, 1);
            p.serverLevel().addFreshEntity(iaidoWave);
        }

        if (now < state.phantomArrayUntil) {
            firePhantomSwordBolts(p, state, now, origin.add(look.scale(24.0)));
        }
        if (hitNear) state.addCharge(20f);
        WristNetwork.cue(p, WristNetwork.ASCALON_SWING_CUE, 5,
                (hitNear ? WristNetwork.FLAG_HIT_ENTITY : 0) | WristNetwork.FLAG_HIT_GROUND,
                chest, origin.add(look.scale(14.0)));
        p.level().playSound(null, p.blockPosition(), Omnitrix.ASCALON_SLASH_BEAM.get(), SoundSource.PLAYERS, 1.15f, .86f);
        p.level().playSound(null, p.blockPosition(), Omnitrix.ASCALON_RIFT.get(), SoundSource.PLAYERS, .78f, 1.24f);
        WristNetwork.syncAscalon(p);
    }

    public static void dimensionalRift(ServerPlayer p) {
        var state = Omnitrix.ascalon(p);
        long now = WristService.time(p);
        if (now < state.riftAt) return;

        if (now < state.riftRecallUntil && state.riftOrigin != null && state.riftDest != null) {
            riftBladeConvergence(p, state, now);
            return;
        }

        state.riftAt = now + 6;
        Vec3 startPos = p.position();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();

        HitResult clip = p.level().clip(new ClipContext(eye, eye.add(look.scale(36.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 dest = clip.getType() == HitResult.Type.BLOCK
                ? clip.getLocation().subtract(look.scale(1.15))
                : eye.add(look.scale(34.0)).subtract(0, p.getEyeHeight() * .5, 0);
        dest = new Vec3(dest.x, Math.max(p.level().getMinBuildHeight() + 2, dest.y), dest.z);
        Vec3 path = dest.subtract(startPos);
        double distance = path.length();
        dest = startPos;
        for (double step = distance; step >= .5; step -= .5) {
            Vec3 candidate = startPos.add(path.scale(step / distance));
            if (WristService.humanFits(p, candidate) && p.mayInteract(p.level(), BlockPos.containing(candidate))) {
                dest = candidate;
                break;
            }
        }

        AABB corridor = new AABB(startPos, dest).inflate(4.4);
        boolean hitAny = false;
        double totalLen = Math.max(1.0, startPos.distanceTo(dest));
        Vec3 axis = dest.subtract(startPos).normalize();
        for (LivingEntity target : p.serverLevel().getEntitiesOfClass(LivingEntity.class, corridor,
                e -> e != p && Abilities.validTarget(p, e, false))) {
            Vec3 rel = target.getBoundingBox().getCenter().subtract(startPos);
            double proj = Math.clamp(rel.dot(axis), 0.0, totalLen);
            Vec3 closest = startPos.add(axis.scale(proj));
            if (p.hasLineOfSight(target) && (target.getBoundingBox().inflate(3.8).contains(closest)
                    || target.distanceToSqr(dest) <= 6.5 * 6.5)) {
                if (fundamentalCleave(p, target, 175_000_000f, false, axis, .65)) {
                    if (target.isAlive()) TemporalEffects.freezeSingle(p.serverLevel(), target, now + 32);
                    hitAny = true;
                    WristNetwork.cue(p, WristNetwork.ASCALON_CLEAVE_HIT_CUE, 2, WristNetwork.FLAG_HIT_ENTITY,
                            closest, target.getBoundingBox().getCenter());
                }
            }
        }

        p.moveTo(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        if (p.connection != null) p.connection.teleport(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        p.resetFallDistance();

        state.riftOrigin = startPos.add(0, 1.0, 0);
        state.riftDest = dest.add(0, 1.0, 0);
        state.riftRecallUntil = now + 80;
        if (hitAny) state.addCharge(18f);

        WristNetwork.cue(p, WristNetwork.ASCALON_RIFT_CUE, 0,
                (hitAny ? WristNetwork.FLAG_HIT_ENTITY : 0) | WristNetwork.FLAG_HIT_GROUND,
                state.riftOrigin, state.riftDest);
        p.level().playSound(null, BlockPos.containing(dest), Omnitrix.ASCALON_RIFT.get(), SoundSource.PLAYERS, 1.02f, 1.0f);
        WristNetwork.syncAscalon(p);
    }

    private static void riftBladeConvergence(ServerPlayer p, AscalonState state, long now) {
        Vec3 a = state.riftOrigin;
        Vec3 b = state.riftDest;
        Vec3 mid = a.lerp(b, .5);
        state.riftRecallUntil = 0L;
        state.riftOrigin = null;
        state.riftDest = null;
        state.riftAt = now + 24;

        var level = p.serverLevel();
        if (!level.hasChunkAt(BlockPos.containing(a)) || !level.hasChunkAt(BlockPos.containing(b))) {
            WristNetwork.syncAscalon(p);
            return;
        }
        Vec3 path = b.subtract(a);
        double lengthSquared = path.lengthSqr();
        AABB box = new AABB(a, b).inflate(6.2);
        boolean hitAny = false;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != p && Abilities.validTarget(p, e, false))) {
            Vec3 center = target.getBoundingBox().getCenter();
            double projection = lengthSquared > 1e-6 ? Math.clamp(center.subtract(a).dot(path) / lengthSquared, 0, 1) : 0;
            if (center.distanceToSqr(a.add(path.scale(projection))) > 6.2 * 6.2
                    || !riftCanReach(p, a, center) && !riftCanReach(p, b, center)) continue;
            Vec3 pullToMid = mid.subtract(target.position());
            if (fundamentalCleave(p, target, 245_000_000f, true, pullToMid, 1.45)) {
                if (target.isAlive()) TemporalEffects.freezeSingle(level, target, now + 26);
                hitAny = true;
                WristNetwork.cue(p, WristNetwork.ASCALON_CLEAVE_HIT_CUE, 3, WristNetwork.FLAG_HIT_ENTITY,
                        mid, target.getBoundingBox().getCenter());
            }
        }
        if (hitAny) state.addCharge(22f);
        WristNetwork.cue(p, WristNetwork.ASCALON_RIFT_CUE, 1,
                (hitAny ? WristNetwork.FLAG_HIT_ENTITY : 0) | WristNetwork.FLAG_HIT_GROUND, a, b);
        p.level().playSound(null, p.blockPosition(), Omnitrix.ASCALON_RIFT.get(), SoundSource.PLAYERS, 1.08f, 1.22f);
        p.level().playSound(null, p.blockPosition(), Omnitrix.ASCALON_CLEAVE.get(), SoundSource.PLAYERS, .95f, 1.18f);
        WristNetwork.syncAscalon(p);
    }

    private static boolean riftCanReach(ServerPlayer p, Vec3 from, Vec3 target) {
        return p.level().clip(new ClipContext(from, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
                .getType() == HitResult.Type.MISS;
    }

    public static void tempestDome(ServerPlayer p) {
        var state = Omnitrix.ascalon(p);
        long now = WristService.time(p);
        if (now < state.tempestAt) return;

        if (now < state.phantomArrayUntil) {
            castHeavenlyLanceSeal(p, state, now);
            return;
        }

        state.phantomArrayUntil = now + PHANTOM_ARRAY_TICKS;
        state.tempestAt = now + 8;
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();

        boolean hitAny = false;
        for (LivingEntity target : nearbyHostiles(p, center, 14.0)) {
            Vec3 pushOut = target.position().subtract(center);
            if (fundamentalCleave(p, target, 115_000_000f, false, pushOut, 2.25)) {
                hitAny = true;
                if (!AcademyInterop.holdingShieldArmor(target)) {
                    target.push(0, .55, 0);
                    target.hurtMarked = true;
                }
            }
        }
        if (hitAny) state.addCharge(18f);
        WristNetwork.cue(p, WristNetwork.ASCALON_TEMPEST_CUE, 0,
                (hitAny ? WristNetwork.FLAG_HIT_ENTITY : 0) | WristNetwork.FLAG_HIT_GROUND,
                center, center.add(p.getLookAngle().scale(8.0)));
        level.playSound(null, p.blockPosition(), Omnitrix.ASCALON_TEMPEST.get(), SoundSource.PLAYERS, 1.0f, 1.08f);
        WristNetwork.syncAscalon(p);
    }

    private static void castHeavenlyLanceSeal(ServerPlayer p, AscalonState state, long now) {
        state.phantomArrayUntil = 0L;
        state.tempestAt = now + 45;
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        Vec3 look = p.getLookAngle().normalize();

        EntityHitResult ray = ProjectileUtil.getEntityHitResult(level, p, p.getEyePosition(),
                p.getEyePosition().add(look.scale(26.0)),
                p.getBoundingBox().expandTowards(look.scale(26.0)).inflate(2.5),
                e -> e instanceof LivingEntity le && Abilities.validTarget(p, le, false), .5f);
        LivingEntity primaryPrisoner = ray != null && ray.getEntity() instanceof LivingEntity le ? le
                : nearbyHostiles(p, center.add(look.scale(7.5)), 20.0).stream().findFirst().orElse(null);
        Vec3 sealCenter = primaryPrisoner != null
                ? primaryPrisoner.position()
                : center.add(look.multiply(1, 0, 1).normalize().scale(8.5));

        boolean hitAny = false;
        for (LivingEntity target : nearbyHostiles(p, sealCenter, STORM_DOME_RADIUS + 4.5)) {
            Vec3 pullIn = sealCenter.subtract(target.position());
            TemporalEffects.freezeSingle(level, target, now + 34);
            if (fundamentalCleave(p, target, 215_000_000f, true, pullIn, .65)) {
                hitAny = true;
            }
        }
        DOMES.computeIfAbsent(level, k -> new ArrayList<>())
                .add(new SealDomain(p.getUUID(), sealCenter, STORM_DOME_RADIUS, now + STORM_DOME_TICKS));
        WristNetwork.beginScene(p, WristNetwork.SEAL_SCENE, 0, sealCenter, STORM_DOME_TICKS);

        if (hitAny) state.addCharge(24f);
        WristNetwork.cue(p, WristNetwork.ASCALON_TEMPEST_CUE, 1,
                (hitAny ? WristNetwork.FLAG_HIT_ENTITY : 0) | WristNetwork.FLAG_HIT_GROUND,
                center.add(0, 1.5, 0), sealCenter);
        level.playSound(null, BlockPos.containing(sealCenter), Omnitrix.ASCALON_TEMPEST.get(), SoundSource.PLAYERS, 1.12f, .92f);
        WristNetwork.syncAscalon(p);
    }

    private static void firePhantomSwordBolts(ServerPlayer p, AscalonState state, long now, Vec3 fallbackFocus) {
        if (now - state.lastPhantomVolleyAt < 5) return;
        state.lastPhantomVolleyAt = now;
        List<LivingEntity> targets = nearbyHostiles(p, p.position().add(p.getLookAngle().scale(9.0)), 22.0);
        Vec3 origin = p.position().add(0, 1.65, 0);
        if (!targets.isEmpty()) {
            int limit = Math.min(3, targets.size());
            for (int i = 0; i < limit; i++) {
                LivingEntity t = targets.get(i);
                fundamentalCleave(p, t, 58_000_000f, false, t.position().subtract(p.position()), .55);
                WristNetwork.cue(p, WristNetwork.ASCALON_TEMPEST_CUE, 2, WristNetwork.FLAG_HIT_ENTITY | WristNetwork.FLAG_PROJECTILE,
                        origin, t.getBoundingBox().getCenter());
            }
        } else {
            WristNetwork.cue(p, WristNetwork.ASCALON_TEMPEST_CUE, 2, WristNetwork.FLAG_PROJECTILE, origin, fallbackFocus);
        }
    }

    public static void syzygyAlignment(ServerPlayer p) {
        var state = Omnitrix.ascalon(p);
        long now = WristService.time(p);
        if (now < state.syzygyAt) return;
        boolean fullAuthority = state.siphonCharge >= 99.5f || p.isCreative();
        state.syzygyAt = now + 70;
        state.skySyzygyUntil = now + SYZYGY_SKY_TICKS;
        if (fullAuthority) state.siphonCharge = 40f;
        else state.addCharge(35f);

        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();

        if (level.isRaining() || level.isThundering()) {
            level.setWeatherParameters(18_000, 0, false, false);
            level.setRainLevel(0f);
            level.setThunderLevel(0f);
        }
        BlockPos pBlock = p.blockPosition();
        for (BlockPos bp : BlockPos.betweenClosed(pBlock.offset(-8, -3, -8), pBlock.offset(8, 5, 8))) {
            if (p.mayInteract(level, bp) && (level.getBlockState(bp).is(Blocks.FIRE) || level.getBlockState(bp).is(Blocks.SOUL_FIRE))) {
                level.removeBlock(bp, false);
            }
        }
        for (ZombieVillager zv : level.getEntitiesOfClass(ZombieVillager.class, AABB.ofSize(center, 96, 48, 96))) {
            Vec3 purified = zv.getBoundingBox().getCenter();
            zv.convertTo(EntityType.VILLAGER, false);
            WristNetwork.cueNear(level, p.getUUID(), Kind.CELESTIAL.ordinal(),
                    WristNetwork.ASCALON_PARRY_CUE, 0, purified, purified, 96);
        }
        for (Player ally : level.getEntitiesOfClass(Player.class, AABB.ofSize(center, 104, 64, 104),
                other -> other == p || !Abilities.validTarget(p, other, false))) {
            for (var eff : List.copyOf(ally.getActiveEffects())) {
                if (!eff.getEffect().value().isBeneficial()) ally.removeEffect(eff.getEffect());
            }
            ally.heal(ally.getMaxHealth() - ally.getHealth());
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 300, 3, false, false));
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 9, false, false));
        }

        boolean hitAny = false;
        float dmg = fullAuthority ? 999_999_999f : 380_000_000f;
        int lightningCount = 0;
        for (LivingEntity enemy : nearbyHostiles(p, center, 52.0)) {
            Vec3 enemyPos = enemy.position();
            if (fundamentalCleave(p, enemy, dmg, fullAuthority, enemyPos.subtract(center), 2.4)) {
                hitAny = true;
                WristNetwork.cueNear(level, p.getUUID(), Kind.CELESTIAL.ordinal(),
                        WristNetwork.ASCALON_CLEAVE_HIT_CUE, WristNetwork.FLAG_HIT_ENTITY,
                        center.add(0, 3.2, 0), enemy.getBoundingBox().getCenter(), 110);
                if (lightningCount < 4) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(enemyPos.x, enemyPos.y, enemyPos.z);
                        bolt.setVisualOnly(true);
                        level.addFreshEntity(bolt);
                        lightningCount++;
                    }
                }
            }
        }

        WristNetwork.cue(p, WristNetwork.ASCALON_SYZYGY_CUE, fullAuthority ? 1 : 0,
                (hitAny ? WristNetwork.FLAG_HIT_ENTITY : 0) | WristNetwork.FLAG_HIT_GROUND,
                center, center.add(0, 2.8, 0));
        WristNetwork.beginScene(p, WristNetwork.SYZYGY_SCENE, fullAuthority ? 1 : 0, center, SYZYGY_SKY_TICKS);
        level.playSound(null, p.blockPosition(), Omnitrix.ASCALON_SYZYGY.get(), SoundSource.PLAYERS, 1.20f, 1.0f);
        WristNetwork.syncAscalon(p);
    }

    private static List<LivingEntity> nearbyHostiles(ServerPlayer p, Vec3 at, double radius) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        AABB.ofSize(at, radius * 2, radius * 2, radius * 2),
                        e -> e != p && e.distanceToSqr(at) <= radius * radius && Abilities.validTarget(p, e, false))
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(at))).limit(64).toList();
    }

    private static void applyAttributes(ServerPlayer p, boolean armored) {
        float oldHealth = p.getHealth(), oldMaximum = p.getMaxHealth();
        modify(p, Attributes.MAX_HEALTH, armored ? 100.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        AcademyInterop.changedVitals(p, oldHealth, oldMaximum);
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        modify(p, Attributes.ARMOR, armored ? 100.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        modify(p, Attributes.ARMOR_TOUGHNESS, armored ? 100.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        modify(p, Attributes.KNOCKBACK_RESISTANCE, armored ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        modify(p, Attributes.MOVEMENT_SPEED, armored ? .44 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(p, Attributes.STEP_HEIGHT, armored ? 1.05 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        modify(p, FLIGHT, armored ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
        if (armored) {
            p.getAbilities().mayfly = true;
            p.getAbilities().setFlyingSpeed(.05f);
        } else {
            var w = Omnitrix.wrist(p);
            boolean alienFly = w.hasForm() && w.form().flies() && w.kind() != Kind.CARNITRIX;
            boolean allowFly = alienFly || p.isCreative() || p.isSpectator();
            p.getAbilities().mayfly = allowFly;
            p.getAbilities().setFlyingSpeed(w.hasForm() && w.form() == Form.ALIEN_X && w.kind() == Kind.CELESTIAL ? .095f : .05f);
            if (!allowFly && p.getAbilities().flying) {
                p.getAbilities().flying = false;
            }
        }
        p.onUpdateAbilities();
    }

    private static void modify(ServerPlayer player, Holder<Attribute> attribute, double amount, AttributeModifier.Operation op) {
        var inst = player.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(MODIFIER);
        if (amount != 0.0) {
            var modifier=new AttributeModifier(MODIFIER, amount, op);
            if (attribute.equals(Attributes.MAX_HEALTH)) inst.addPermanentModifier(modifier);
            else inst.addTransientModifier(modifier);
        }
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.hasData(Omnitrix.ASCALON_STATE)) {
            var st = Omnitrix.ascalon(sp);
            st.clearCast();
            if (st.armored && Omnitrix.wrist(sp).hasForm()) st.armored = false;
            applyAttributes(sp, st.armored);
            WristNetwork.syncAscalon(sp);
        }
    }

    private static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.hasData(Omnitrix.ASCALON_STATE)) {
            applyAttributes(sp, Omnitrix.ascalon(sp).armored);
            WristNetwork.syncAscalon(sp);
        }
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.hasData(Omnitrix.ASCALON_STATE)) {
            Omnitrix.ascalon(sp).clearCast();
            applyAttributes(sp, Omnitrix.ascalon(sp).armored);
            WristNetwork.syncAscalon(sp);
        }
    }

    private static void cloned(PlayerEvent.Clone e) {
        if (e.getEntity() instanceof ServerPlayer sp && e.getOriginal().hasData(Omnitrix.ASCALON_STATE)) {
            var oldState = Omnitrix.ascalon(e.getOriginal());
            var newState = Omnitrix.ascalon(sp);
            newState.armored = oldState.armored && hasAscalonInInventory(sp);
            newState.siphonCharge = oldState.siphonCharge;
            newState.revision = oldState.revision + 1;
        }
    }

    private static void track(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer actor
                && actor.hasData(Omnitrix.ASCALON_STATE)) {
            WristNetwork.trackingAscalon(viewer, actor);
        }
    }
}
