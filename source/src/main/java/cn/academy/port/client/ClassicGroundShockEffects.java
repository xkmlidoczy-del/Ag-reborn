/*
 * AcademyCraft 1.0.7 Groundshock/SmokeEffect client adaptation.
 * Copyright (c) Lambda Innovation, 2013-2016. GPLv3; see NOTICE.
 */
package cn.academy.port.client;

import cn.academy.port.core.GroundShockWave;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Actual client-only source-style camera, digging, smoke, and following-sound adapter.
 * Forward ground_shock_start/ground_shock_abort/ground_shock tags from AcademyClient.
 * Optional startLocal()/abortLocal() input hooks remove network latency from the uplift.
 * This adapter does not consume CP, damage entities, alter blocks, or send network requests.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicGroundShockEffects {
    private static final ResourceLocation SMOKE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/smokes.png");
    private static final SoundEvent GROUND_SHOCK = SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath("academy", "vecmanip.groundshock"));
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ClassicGroundShockTimeline.Tokens TOKENS = new ClassicGroundShockTimeline.Tokens();
    private static final ClassicGroundShockTimeline.PauseClock CLOCK = new ClassicGroundShockTimeline.PauseClock();
    private static final List<Smoke> SMOKES = new ArrayList<>();
    private static final List<TerrainParticle> DIGGING = new ArrayList<>();
    private static final List<FollowingSound> SOUNDS = new ArrayList<>();
    private static final List<Slash> SLASHES = new ArrayList<>();
    private static final int MAX_PARTICLES = 2048;
    private static ClientLevel activeLevel;
    private static Entity activePlayer;
    private static Object activeConnection;
    private static MultiBufferSource.BufferSource buffers;
    private static Hold hold;
    private static boolean localInputDriven, localInputHeld;
    private static long localInput;

    private ClassicGroundShockEffects() {}

    /** Client-thread physical press hook. The server acknowledgement binds its token, without restarting ticks. */
    public static void startLocal() { startLocalCaptured(0); }

    /** Positive wire prediction is captured after the binding allocates this physical input. */
    public static void startLocal(long input) {
        if (input > 0) startLocalCaptured(input);
    }

    private static void startLocalCaptured(long input) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        if (minecraft.level == null || minecraft.player == null || !minecraft.player.isAlive()) return;
        long ownerEpoch = AcademyClient.serverSingleEpoch();
        if (input > 0 && ownerEpoch <= 0) return;
        localInputDriven = localInputHeld = true;
        localInput = input;
        hold = new Hold(minecraft.player, input, 0, ownerEpoch);
    }

    /** Release/GUI/category/ability input cancellation stops only uplift, not a successful independent slash. */
    public static void abortLocal() {
        localInputHeld = false;
        hold = null;
    }

    /** A delayed cancellation of press1 cannot stop press2's prediction or accepted hold. */
    public static void abortLocal(long input) {
        if (hold != null) abortLocal(input, hold.ownerEpoch);
        else if (input > 0 && localInput == input) localInputHeld = false;
    }

    /** END cleanup uses the immutable disposed identity, never a newer Node's mutable epoch. */
    public static void abortLocal(long input, long capturedOwnerEpoch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (input <= 0 || capturedOwnerEpoch <= 0 || hold == null || hold.caster != minecraft.player
                || localInput != input || hold.input != input || hold.ownerEpoch != capturedOwnerEpoch) return;
        localInputHeld = false;
        hold = null;
    }

    /** HUD bridge: -1 means no preparing context, >=5 means classic ACTIVE. */
    public static int localPrepareTicks() { return hold == null ? -1 : hold.uplift.ticks(); }
    /** Source context's captured IConsumptionProvider hint; no active hold has no hint. */
    public static float consumptionHint() { return hold == null ? 0 : hold.consumption; }

    /** Copies packet ownership before crossing threads; level, player, and connection replacement invalidate it. */
    public static void receive(CompoundTag data) {
        if (data == null) return;
        CompoundTag tag = data.copy();
        if (!tag.contains("entity", Tag.TAG_INT) || !tag.contains("token", Tag.TAG_LONG)
                || !tag.contains("input", Tag.TAG_LONG) || !tag.contains("owner_epoch", Tag.TAG_LONG)
                || !tag.hasUUID("entity_uuid")) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel target = minecraft.level;
        Entity localPlayer = minecraft.player;
        Object connection = minecraft.getConnection();
        long epoch = AcademyClient.inputSessionEpoch();
        minecraft.execute(() -> {
            if (target == null || minecraft.level != target || minecraft.player != localPlayer
                    || minecraft.getConnection() != connection || !AcademyClient.sameInputSessionEpoch(epoch)) return;
            synchronizeSession(minecraft);
            updateClock(minecraft);
            int id = tag.getInt("entity");
            long token = tag.getLong("token"), input = tag.getLong("input"), ownerEpoch = tag.getLong("owner_epoch");
            if (token <= 0 || input < 0 || ownerEpoch < 0 || input > 0 && ownerEpoch == 0) return;
            String kind = tag.getString("kind");
            Entity caster = target.getEntity(id);
            if (caster == null || !caster.isAlive() || caster.isRemoved()
                    || !caster.getUUID().equals(tag.getUUID("entity_uuid"))) return;
            if (caster == localPlayer && input > 0 && !AcademyClient.singleOwnerEpochMatches(ownerEpoch)) return;
            if (caster == localPlayer && input > 0 && AcademyClient.singleTokenConflicts("ground_shock", input, token)) return;
            if (hold != null && hold.caster == caster && hold.uplift.token() == token && hold.input != input) return;
            if ("ground_shock_abort".equals(kind)) {
                TOKENS.rememberAbort(id, token);
                boolean localEnd = caster == localPlayer
                        && AcademyClient.singleTerminalAllowed("ground_shock", input, token);
                if (localEnd) AcademyClient.acceptedSingleEnd("ground_shock", input, token);
                if ((localEnd || input == 0) && ownsTerminal(caster, input, token, ownerEpoch)) {
                    hold = null;
                    localInputHeld = false;
                }
                if (localEnd && localInput == input) localInputHeld = false;
                return;
            }
            if ("ground_shock_start".equals(kind)) {
                if (caster != localPlayer || (input > 0
                        ? !AcademyClient.singleStartAllowed("ground_shock", input, token)
                        : !AcademyClient.legacySingleStartAllowed("ground_shock"))) return;
                // Only this still-held physical input can bind its acknowledgement; preserve its exact uplift age.
                if (input > 0 && localInputDriven && (!localInputHeld || localInput != input)) return;
                if (input == 0 && localInputDriven && localInput == 0 && !localInputHeld) return;
                if (input > 0 && (hold == null || hold.caster != caster || hold.input != input
                        || hold.ownerEpoch != ownerEpoch || hold.uplift.token() != 0)) return;
                if (input == 0 && hold != null && (hold.input != 0 || hold.uplift.token() == 0
                        && !AcademyClient.singleStartAllowed("ground_shock", 0, token))) return;
                if (!TOKENS.acceptStart(id, token)) return;
                if (hold != null && hold.caster == caster && hold.input == input && hold.uplift.token() == 0)
                    hold.uplift.bind(token);
                else hold = new Hold(caster, input, token, ownerEpoch);
                AcademyClient.acceptedSingleStart("ground_shock", input, token);
                return;
            }
            if (!"ground_shock".equals(kind) || !tag.contains("blocks", Tag.TAG_INT_ARRAY)) return;
            int[] cells = tag.getIntArray("blocks");
            if (!validCells(target, cells) || !TOKENS.acceptPerform(id, token)) return;
            boolean localEnd = caster == localPlayer
                    && AcademyClient.singleTerminalAllowed("ground_shock", input, token);
            if (localEnd) AcademyClient.acceptedSingleEnd("ground_shock", input, token);
            if ((localEnd || input == 0) && ownsTerminal(caster, input, token, ownerEpoch)) hold = null;
            if (localEnd && localInput == input) localInputHeld = false;
            if (caster == localPlayer) {
                if (SLASHES.size() < 16) SLASHES.add(new Slash(caster));
            }
            FollowingSound sound = new FollowingSound(caster, target, localPlayer, connection);
            SOUNDS.removeIf(existing -> !minecraft.getSoundManager().isActive(existing));
            if (SOUNDS.size() < 128) {
                SOUNDS.add(sound);
                minecraft.getSoundManager().play(sound);
            }
            for (int i = 0; i < cells.length; i += 3) spawnCell(minecraft, target, new BlockPos(cells[i], cells[i + 1], cells[i + 2]));
        });
    }

    /** Positive pending terminals can close token0 prediction; input0 requires an accepted exact token. */
    private static boolean ownsTerminal(Entity caster, long input, long token, long ownerEpoch) {
        return hold != null && hold.caster == caster && hold.input == input
                && (input == 0 || hold.ownerEpoch == ownerEpoch)
                && (ClassicGroundShockTimeline.matchingToken(hold.uplift.token(), token)
                || input > 0 && hold.uplift.token() == 0);
    }

    /** Client-thread session hook. Clears all owned particles, sounds, camera gestures, and token history. */
    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (TerrainParticle particle : DIGGING) particle.remove();
        for (FollowingSound sound : SOUNDS) {
            sound.close();
            minecraft.getSoundManager().stop(sound);
        }
        SMOKES.clear(); DIGGING.clear(); SOUNDS.clear(); SLASHES.clear();
        TOKENS.clear(); CLOCK.clear(); hold = null;
        localInputDriven = localInputHeld = false;
        localInput = 0;
    }

    private static void synchronizeSession(Minecraft minecraft) {
        if (activeLevel == minecraft.level && activePlayer == minecraft.player
                && activeConnection == minecraft.getConnection()) return;
        clear();
        activeLevel = minecraft.level;
        activePlayer = minecraft.player;
        activeConnection = minecraft.getConnection();
    }

    private static void updateClock(Minecraft minecraft) {
        CLOCK.update(Util.getMillis(), minecraft.level != null && !minecraft.isPaused());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null || minecraft.player == null) return;
        if (hold != null && !validLocal(minecraft, hold.caster)) {
            TOKENS.rememberAbort(hold.caster.getId(), hold.uplift.token());
            hold = null;
            localInputHeld = false;
        }
        SLASHES.removeIf(slash -> !validLocal(minecraft, slash.caster));
        DIGGING.removeIf(particle -> !particle.isAlive());
        SOUNDS.removeIf(sound -> !minecraft.getSoundManager().isActive(sound));
        if (minecraft.isPaused()) return;
        if (hold != null) {
            minecraft.player.setXRot(minecraft.player.getXRot() + hold.uplift.tick());
        }
        SLASHES.removeIf(slash -> {
            slash.ticks++;
            minecraft.player.setXRot(minecraft.player.getXRot() + ClassicGroundShockTimeline.slashPitch(slash.ticks));
            return slash.ticks >= ClassicGroundShockTimeline.SLASH_TICKS;
        });
        SMOKES.removeIf(smoke -> {
            // Source integrates velocity directly once per client tick, without drag, gravity or collision.
            smoke.previous = smoke.position;
            smoke.position = smoke.position.add(smoke.velocity);
            return !ClassicGroundShockTimeline.smokeAlive(CLOCK.elapsed() - smoke.created);
        });
    }

    private static boolean validLocal(Minecraft minecraft, Entity caster) {
        return caster == minecraft.player && caster.isAlive() && !caster.isRemoved()
                && caster.level() == minecraft.level;
    }

    private static boolean validCells(ClientLevel level, int[] cells) {
        if (!ClassicGroundShockTimeline.validBlockArrayLength(cells.length)) return false;
        for (int i = 0; i < cells.length; i += 3) {
            if (cells[i] < -30_000_000 || cells[i] > 30_000_000
                    || cells[i + 2] < -30_000_000 || cells[i + 2] > 30_000_000
                    || cells[i + 1] < level.getMinBuildHeight() || cells[i + 1] >= level.getMaxBuildHeight()) return false;
        }
        return true;
    }

    private static void spawnCell(Minecraft minecraft, ClientLevel level, BlockPos position) {
        // Do not pull new chunks into memory for an effect packet. All source cells are nearby loaded terrain.
        if (level.getChunkSource().getChunk(position.getX() >> 4, position.getZ() >> 4, false) == null) return;
        BlockState state = level.getBlockState(position);
        int count = ClassicGroundShockTimeline.MIN_DIGGING + RANDOM.nextInt(
                ClassicGroundShockTimeline.MAX_DIGGING_EXCLUSIVE - ClassicGroundShockTimeline.MIN_DIGGING);
        for (int i = 0; i < count && DIGGING.size() < MAX_PARTICLES; i++) {
            // Use the current block state, as the source did after its authoritative ground mutations.
            // TerrainParticle's constructor retains vanilla digging initialization, not an artificial burst velocity.
            TerrainParticle particle = new TerrainParticle(level,
                    position.getX() + RANDOM.nextDouble(), position.getY() + 1 + RANDOM.nextDouble() * .5 + .2,
                    position.getZ() + RANDOM.nextDouble(), random(-.2, .2), .1 + RANDOM.nextDouble() * .2,
                    random(-.2, .2), state, position).updateSprite(state, position);
            DIGGING.add(particle);
            minecraft.particleEngine.add(particle);
        }
        if (RANDOM.nextFloat() < ClassicGroundShockTimeline.SMOKE_CHANCE && SMOKES.size() < MAX_PARTICLES) {
            Vec3 origin = new Vec3(position.getX() + .5 + random(-.3, .3),
                    position.getY() + 1 + random(0, .2), position.getZ() + .5 + random(-.3, .3));
            Vec3 velocity = new Vec3(random(-.03, .03), random(.03, .06), random(-.03, .03));
            double modifier = .5F + RANDOM.nextFloat() * .2F;
            // SmokeEffect also increments an unused rotation; its renderer never applies that rotation.
            SMOKES.add(new Smoke(origin, velocity, modifier, RANDOM.nextInt(4), CLOCK.elapsed()));
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeSession(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null || SMOKES.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        RenderType type = ClassicRenderTypes.world(SMOKE_TEXTURE);
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        boolean emitted = false;
        poses.pushPose();
        try {
            poses.translate(-camera.x, -camera.y, -camera.z);
            for (Smoke smoke : SMOKES) {
                long age = CLOCK.elapsed() - smoke.created;
                if (!ClassicGroundShockTimeline.smokeAlive(age)) continue;
                double alpha = ClassicGroundShockTimeline.smokeAlpha(age, smoke.lifeModifier);
                if (alpha <= 0) continue;
                Vec3 position = smoke.previous.lerp(smoke.position, partial);
                Vec3 delta = position.subtract(camera);
                double yaw = Math.toDegrees(Math.atan2(delta.x, delta.z)) + 180;
                double pitch = Math.toDegrees(Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)));
                poses.pushPose();
                try {
                    poses.translate(position.x, position.y, position.z);
                    // Source faces each smoke toward the camera using this yaw/pitch, without roll.
                    poses.mulPose(Axis.YP.rotationDegrees((float) yaw));
                    poses.mulPose(Axis.XP.rotationDegrees((float) pitch));
                    smokeQuad(buffers.getBuffer(type), poses.last().pose(), smoke.frame, alpha);
                    emitted = true;
                } finally { poses.popPose(); }
            }
            if (emitted) buffers.endBatch(type);
        } finally { poses.popPose(); }
    }

    private static void smokeQuad(VertexConsumer out, Matrix4f matrix, int frame, double alpha) {
        float u = (float) ClassicGroundShockTimeline.smokeU(frame), v = (float) ClassicGroundShockTimeline.smokeV(frame);
        float size = (float) ClassicGroundShockTimeline.SMOKE_SIZE;
        // Preserve the original 2x2 atlas frame and vertex/UV order; size 1 spans [-1,+1].
        vertex(out, matrix, -size, -size, u, v, alpha);
        vertex(out, matrix, -size, size, u, v + .5F, alpha);
        vertex(out, matrix, size, size, u + .5F, v + .5F, alpha);
        vertex(out, matrix, size, -size, u + .5F, v, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, float x, float y, float u, float v, double alpha) {
        out.addVertex(matrix, x, y, 0).setUv(u, v).setColor(255, 255, 255,
                Mth.clamp((int) Math.round(alpha * 255), 0, 255));
    }
    private static double random(double low, double high) { return low + RANDOM.nextDouble() * (high - low); }
    private static final class Hold {
        final Entity caster;
        final long input, ownerEpoch;
        final ClassicGroundShockTimeline.Uplift uplift;
        final float consumption;
        Hold(Entity caster, long input, long token, long ownerEpoch) {
            this.caster = caster; this.input = input; this.ownerEpoch = ownerEpoch; this.uplift = new ClassicGroundShockTimeline.Uplift(token);
            consumption = GroundShockWave.parameters(AcademyClient.state.exp(GroundShockWave.ID), 0).cp();
        }
    }
    private static final class Slash {
        final Entity caster;
        int ticks;
        Slash(Entity caster) { this.caster = caster; }
    }
    private static final class Smoke {
        final Vec3 velocity;
        final double lifeModifier;
        final int frame;
        final long created;
        Vec3 position, previous;
        Smoke(Vec3 position, Vec3 velocity, double lifeModifier, int frame, long created) {
            this.position = this.previous = position;
            this.velocity = velocity;
            this.lifeModifier = lifeModifier;
            this.frame = frame;
            this.created = created;
        }
    }
    private static final class FollowingSound extends AbstractTickableSoundInstance {
        final Entity caster, localPlayer;
        final ClientLevel level;
        final Object connection;
        FollowingSound(Entity caster, ClientLevel level, Entity localPlayer, Object connection) {
            super(GROUND_SHOCK, SoundSource.MASTER, RandomSource.create());
            this.caster = caster; this.level = level; this.localPlayer = localPlayer; this.connection = connection;
            volume = ClassicGroundShockTimeline.SOUND_VOLUME;
            tick();
        }
        @Override public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != level || minecraft.player != localPlayer || minecraft.getConnection() != connection
                    || caster.isRemoved() || level.getEntity(caster.getId()) != caster) { stop(); return; }
            x = caster.getX(); y = caster.getY(); z = caster.getZ();
        }
        void close() { stop(); }
    }
}
