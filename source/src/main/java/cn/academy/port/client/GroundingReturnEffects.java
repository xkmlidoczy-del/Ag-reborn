package cn.academy.port.client;

import cn.academy.port.highpower.GroundingReturn;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.HighPowerRecoveryHudModel.Box;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** NEW-122: grounded blue-white lines and downward leg flow; only server phase 1 sparks a burst. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class GroundingReturnEffects {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/effects/arc/line_segment.png");
    private static final int MAX_CONTEXTS = 128, MAX_WATERMARKS = 1024, QUIET_TICKS = 12;
    private static final int MAX_TICKS = 60, BURST_TICKS = 4, RADIAL_LINES = 10, LEG_SEGMENTS = 6;
    private static final Map<UUID, Marker> MARKERS = new HashMap<>();
    // Fail closed for a new owner at capacity. Never evict a replay fence during a live connection.
    private static final Map<UUID, Watermark> WATERMARKS = new HashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    // Separate from the bounded remote cache, so remote-owner churn cannot evict a local abort.
    private static Stamp retiredLocal;
    private static volatile long generation;
    private static MultiBufferSource.BufferSource buffers;

    private record Stamp(int entity, long input, long epoch) {}
    private record Watermark(Stamp stamp, long sequence, boolean retired) {}
    private record Cue(int remaining, int precision) {}
    private static final class Marker {
        final Entity owner;
        final Stamp stamp;
        GroundingReturn.Visual state;
        int age, quiet, burst;
        Marker(Entity owner, GroundingReturn.Visual state) {
            this.owner = owner;
            this.stamp = stamp(state);
            this.state = state;
            age = MAX_TICKS - state.remaining();
            burst = state.phase() == 1 ? BURST_TICKS : 0;
        }
    }
    private GroundingReturnEffects() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            GroundingReturn.clientReceiver = GroundingReturnEffects::receive;
        }
    }

    public static void receive(GroundingReturn.Visual packet) {
        if (packet == null || packet.entity() < 0 || packet.owner() == null || packet.input() <= 0
                || packet.epoch() <= 0 || packet.sequence() <= 0 || packet.phase() < 0 || packet.phase() > 2
                || packet.remaining() < 0 || packet.remaining() > MAX_TICKS || !finite(packet.point())) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel captured = mc.level;
        Entity capturedPlayer = mc.player;
        Object capturedConnection = mc.getConnection();
        long capturedGeneration = generation;
        mc.execute(() -> {
            if (captured == null || mc.level != captured || mc.player != capturedPlayer
                    || mc.getConnection() != capturedConnection || generation != capturedGeneration) return;
            synchronize(mc);
            Entity owner = captured.getEntity(packet.entity());
            if (owner == null || !owner.getUUID().equals(packet.owner())) return;
            Stamp incoming = stamp(packet);
            if (owner == mc.player && (!AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || retiredLocal != null && compare(incoming, retiredLocal) <= 0)) return;
            Watermark fence = WATERMARKS.get(packet.owner());
            Marker old = MARKERS.get(packet.owner());
            if (fence != null) {
                int order = compare(incoming, fence.stamp());
                if (order < 0 || order == 0 && (fence.retired() || !incoming.equals(fence.stamp())
                        || packet.sequence() <= fence.sequence())) return;
            }
            if (fence == null && WATERMARKS.size() >= MAX_WATERMARKS) return;
            if (old != null && incoming.equals(old.stamp) && owner != old.owner) return;
            if (packet.phase() == 2 || packet.remaining() == 0) {
                terminal(packet.owner(), incoming, packet.sequence());
                if (old != null) MARKERS.remove(packet.owner());
                return;
            }
            if (!owner.isAlive() || owner.isRemoved() || owner == mc.player && !localEligible()) return;
            if (old == null && MARKERS.size() >= MAX_CONTEXTS) return;
            WATERMARKS.put(packet.owner(), new Watermark(incoming, packet.sequence(), false));
            if (old == null || !incoming.equals(old.stamp)) {
                MARKERS.put(packet.owner(), new Marker(owner, packet));
            } else {
                old.state = packet;
                old.quiet = 0;
                old.age = Math.max(old.age, MAX_TICKS - packet.remaining());
                // A stance keepalive never manufactures, repeats, or extends absorption feedback.
                if (packet.phase() == 1) old.burst = BURST_TICKS;
            }
        });
    }

    /** Immediate cancellation of the visible local session; never touches another owner. */
    public static void abortLocal() {
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        if (mc.player == null) return;
        Marker marker = MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.owner == mc.player) abortLocal(marker.stamp.input(), marker.stamp.epoch());
    }

    /** Captured-input overload also fences cancellation before the first authoritative visual arrives. */
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        if (mc.player == null || input <= 0 || !AcademyClient.singleOwnerEpochMatches(epoch)) return;
        Stamp canceled = new Stamp(mc.player.getId(), input, epoch);
        if (retiredLocal == null || compare(canceled, retiredLocal) > 0) retiredLocal = canceled;
        terminal(mc.player.getUUID(), canceled, Long.MAX_VALUE);
        Marker marker = MARKERS.get(mc.player.getUUID());
        if (marker != null && marker.owner == mc.player && compare(marker.stamp, canceled) <= 0)
            MARKERS.remove(mc.player.getUUID());
    }

    /** Clearing visible effects within a connection must not reopen any retired session. */
    public static void clear() {
        for (Marker marker : MARKERS.values()) terminal(marker.owner.getUUID(), marker.stamp, marker.state.sequence());
        MARKERS.clear();
        generation++;
    }

    private static Stamp stamp(GroundingReturn.Visual packet) {
        return new Stamp(packet.entity(), packet.input(), packet.epoch());
    }
    private static int compare(Stamp left, Stamp right) {
        int epochOrder = Long.compare(left.epoch(), right.epoch());
        return epochOrder != 0 ? epochOrder : Long.compare(left.input(), right.input());
    }
    private static void terminal(UUID owner, Stamp stamp, long sequence) {
        if (localPlayer != null && owner.equals(localPlayer.getUUID())
                && (retiredLocal == null || compare(stamp, retiredLocal) > 0)) retiredLocal = stamp;
        Watermark old = WATERMARKS.get(owner);
        if (old == null && WATERMARKS.size() >= MAX_WATERMARKS) return;
        int order = old == null ? 1 : compare(stamp, old.stamp());
        if (order > 0 || order == 0 && sequence >= old.sequence())
            WATERMARKS.put(owner, new Watermark(stamp, sequence, true));
    }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        MARKERS.clear();
        WATERMARKS.clear();
        retiredLocal = null;
        generation++;
        world = mc.level;
        localPlayer = mc.player;
        connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance();
        var state = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && state != null
                && state.category.equals("electromaster") && state.activated && state.level >= 2 && state.level <= 5
                && state.learned(GroundingReturn.ID) && state.presets.currentContains(GroundingReturn.ID);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        Iterator<Marker> iterator = MARKERS.values().iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            // Identity and screen/focus invalidation still take effect while the integrated server is paused.
            if (world == null || !marker.owner.isAlive() || marker.owner.isRemoved()
                    || world.getEntity(marker.owner.getId()) != marker.owner
                    || marker.owner == mc.player && (!localEligible()
                    || !AcademyClient.singleOwnerEpochMatches(marker.stamp.epoch()))) {
                terminal(marker.owner.getUUID(), marker.stamp, marker.state.sequence());
                iterator.remove();
                continue;
            }
            if (mc.isPaused()) continue;
            marker.age++;
            marker.quiet++;
            if (marker.burst > 0) marker.burst--;
            if (marker.age >= MAX_TICKS || marker.quiet >= QUIET_TICKS || marker.quiet >= marker.state.remaining()) {
                terminal(marker.owner.getUUID(), marker.stamp, marker.state.sequence());
                iterator.remove();
            }
        }
    }

    /** Read-only, local-only view of the authenticated stance; never predicts a start or an absorption. */
    private static Cue localCue(Minecraft mc) {
        synchronize(mc);
        if (mc.level == null || mc.player == null || mc.getConnection() == null || mc.screen != null
                || mc.options.hideGui || mc.getCameraEntity() != mc.player || !localEligible()
                || !mc.player.isAlive() || mc.player.isRemoved() || world.getEntity(mc.player.getId()) != mc.player) return null;
        Marker marker = MARKERS.get(mc.player.getUUID());
        if (marker == null || marker.owner != mc.player || marker.quiet >= QUIET_TICKS
                || !AcademyClient.singleOwnerEpochMatches(marker.stamp.epoch())) return null;
        int remaining = Math.min(MAX_TICKS - marker.age, marker.state.remaining() - marker.quiet);
        return remaining <= 0 ? null : new Cue(remaining, Math.min(remaining, Math.max(0, 6 - marker.age)));
    }

    /** A few fixed candidates, or no cue; no overlap with existing resource, key, or actionbar HUD. */
    private static Box cueBox(int width, int height, Box cp, Box keys, Box recovery, Box notification) {
        if (width < 240 || height < 160) return null;
        Box nativeStatus = new Box(Math.max(0, width / 2 - 110), height - 72, 220, 72);
        Box[] candidates = {new Box(8, height / 2 - 12, 110, 24),
                new Box(width / 2 - 55, height / 2 - 36, 110, 24),
                new Box(8, height / 2 - 40, 110, 24)};
        for (Box box : candidates) if (box.within(width, height) && !box.intersects(cp) && !box.intersects(keys)
                && !box.intersects(notification) && !box.intersects(nativeStatus)
                && (recovery == null || !box.intersects(recovery))) return box;
        return null;
    }
    private static Box cueLayout(Minecraft mc, int width, int height) {
        boolean config = ClassicHudConfig.SPEC.isLoaded();
        var cpOrigin = ClassicHudTimeline.cpOrigin(width, config ? ClassicHudConfig.CP_X.get() : ClassicHudTimeline.CP_X,
                config ? ClassicHudConfig.CP_Y.get() : ClassicHudTimeline.CP_Y);
        var keyOrigin = ClassicHudTimeline.keyOrigin(width, height, config ? ClassicHudConfig.KEY_X.get() : ClassicHudTimeline.KEY_X,
                config ? ClassicHudConfig.KEY_Y.get() : ClassicHudTimeline.KEY_Y);
        Box cp = new Box((int)Math.floor(cpOrigin.x()), (int)Math.floor(cpOrigin.y()), 194, 40);
        Box keys = new Box((int)keyOrigin.x() - 155, (int)keyOrigin.y(), 185, 88);
        Box notification = new Box((int)Math.floor(config ? ClassicHudConfig.NOTIFICATION_X.get() : 0),
                (int)Math.floor(config ? ClassicHudConfig.NOTIFICATION_Y.get() : 15), 130, 43);
        var chat = mc.gui.getChat();
        int chatHeight = (int)Math.ceil(chat.getHeight() * chat.getScale());
        Box chatBox = new Box(0, height - 40 - chatHeight, Math.min(width, chat.getWidth() + 16), chatHeight + 12);
        Box recovery = HighPowerRecoveryHudModel.layout(width, height, cp, keys, notification, chatBox);
        return cueBox(width, height, cp, keys, recovery, notification);
    }
    private static String seconds(int ticks) {
        int tenths = (Math.max(0, Math.min(MAX_TICKS, ticks)) + 1) / 2;
        return tenths / 10 + "." + tenths % 10;
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Cue cue = localCue(mc);
        if (cue == null) return;
        var graphics = event.getGuiGraphics();
        Box box = cueLayout(mc, graphics.guiWidth(), graphics.guiHeight());
        if (box == null) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(box.x(), box.y(), 0);
            graphics.fill(0, 0, box.width(), box.height(), 0x99070b0e);
            graphics.fill(0, 0, 1, box.height(), cue.precision() > 0 ? 0xffd9f3ff : 0xff779bab);
            graphics.fill(4, 20, box.width() - 4, 22, 0x8055656d);
            graphics.fill(4, 20, 4 + (box.width() - 8) * cue.remaining() / MAX_TICKS, 22, 0xffb0e5ff);
            graphics.pose().translate(5, 3, 0);
            graphics.pose().scale(.7f, .7f, 1);
            String stance = Component.translatable("academy.highpower.grounding_return.hud.stance", seconds(cue.remaining())).getString();
            graphics.drawString(mc.font, mc.font.plainSubstrByWidth(stance, 140), 0, 0, 0xffd9f3ff, false);
            if (cue.precision() > 0) {
                String precision = Component.translatable("academy.highpower.grounding_return.hud.precision", seconds(cue.precision())).getString();
                graphics.drawString(mc.font, mc.font.plainSubstrByWidth(precision, 140), 0, 11, 0xffb0e5ff, false);
            }
        } finally { graphics.pose().popPose(); }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        synchronize(mc);
        if (world == null || MARKERS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        var type = ClassicRenderTypes.world(TEXTURE);
        VertexConsumer out = buffers.getBuffer(type);
        Matrix4f matrix = event.getPoseStack().last().pose();
        Vec3 camera = event.getCamera().getPosition();
        var cameraLook = event.getCamera().getLookVector();
        var forward = new PolarityTetherGeometry.Point(cameraLook.x(), cameraLook.y(), cameraLook.z());
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Marker marker : MARKERS.values()) {
            if (marker.owner == mc.player && (!localEligible()
                    || !AcademyClient.singleOwnerEpochMatches(marker.stamp.epoch()))) continue;
            Vec3 ground = marker.state.point().add(0, .032, 0).subtract(camera);
            if (!finite(ground) || ground.lengthSqr() > 24 * 24) continue;
            double time = marker.age + partial;
            boolean precise = marker.age < 6;
            int baseAlpha = groundAlpha(precise);
            // At most 10 * 3 radial + 2 * (6 + 2) leg quads = 184 vertices per owner.
            for (int i = 0; i < RADIAL_LINES; i++) {
                double angle = i * Math.PI * 2 / RADIAL_LINES;
                Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 across = new Vec3(-direction.z, 0, direction.x);
                Vec3 inner = ground.add(direction.scale(.17));
                Vec3 bend = ground.add(direction.scale(.39)).add(across.scale(i % 2 == 0 ? .055 : -.055));
                Vec3 outer = ground.add(direction.scale(precise ? .65 : .57));
                line(out, matrix, inner, bend, forward, spokeHalfWidth(false), false, baseAlpha);
                line(out, matrix, bend, outer, forward, spokeHalfWidth(true), false, baseAlpha);
                if (marker.burst > 0) {
                    double progress = Math.min(1, (BURST_TICKS - marker.burst + partial) / BURST_TICKS);
                    Vec3 spark = ground.add(direction.scale(.30 + progress * .6));
                    line(out, matrix, spark, spark.add(direction.scale(.20)), forward, .020, true,
                            (int) (235 * (1 - progress * .7)));
                }
            }
            // The server's stationary ground point is the sole positional anchor. Never follow a moved owner.
            // Body yaw follows the rendered limbs, rather than head/look yaw while the owner turns in place.
            float bodyYaw = marker.owner instanceof LivingEntity living
                    ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : marker.owner.getYRot();
            double yaw = Math.toRadians(bodyYaw);
            double height = Math.min(.85, marker.owner.getBbHeight() * .46);
            for (int leg = -1; leg <= 1; leg += 2) {
                Vec3 previous = legPoint(ground, yaw, height, 0, leg);
                for (int segment = 1; segment <= LEG_SEGMENTS; segment++) {
                    Vec3 next = legPoint(ground, yaw, height, (double) segment / LEG_SEGMENTS, leg);
                    line(out, matrix, previous, next, forward, legHalfWidth(false), false, 150);
                    previous = next;
                }
                for (int pulse = 0; pulse < 2; pulse++) {
                    double down = (time * .10 + pulse * .5) % 1;
                    line(out, matrix, legPoint(ground, yaw, height, down, leg),
                            legPoint(ground, yaw, height, Math.min(1, down + .16), leg),
                            forward, legHalfWidth(true), true, precise ? 235 : 200);
                }
            }
        }
        buffers.endBatch(type);
    }

    private static int groundAlpha(boolean precise) { return precise ? 205 : 170; }
    private static double spokeHalfWidth(boolean outer) { return outer ? .018 : .020; }
    private static double legHalfWidth(boolean pulse) { return pulse ? .018 : .014; }
    /** Entire ribbon stays beyond the reviewed .30m body/armored-limb half-width, including its wiggle. */
    private static Vec3 flankOffset(double yaw, double down, int leg) {
        double lateral = leg * (.34 + Math.sin(down * Math.PI * 4) * .018);
        return new Vec3(Math.cos(yaw) * lateral, 0, Math.sin(yaw) * lateral);
    }
    private static Vec3 legPoint(Vec3 ground, double yaw, double height, double down, int leg) {
        return ground.add(flankOffset(yaw, down, leg)).add(0, height * (1 - down), 0);
    }
    private static PolarityTetherGeometry.Point point(Vec3 value) {
        return new PolarityTetherGeometry.Point(value.x, value.y, value.z);
    }
    private static Vec3 vector(PolarityTetherGeometry.Point value) {
        return new Vec3(value.x(), value.y(), value.z());
    }
    private static void line(VertexConsumer out, Matrix4f matrix, Vec3 start, Vec3 end,
                             PolarityTetherGeometry.Point forward, double width, boolean white, int alpha) {
        // Shared verified clipping bounds every ribbon, including third-person cameras close to another owner.
        var clipped = PolarityTetherGeometry.clip(point(start), point(end), forward, width);
        if (clipped == null) return;
        start = vector(clipped.start());
        end = vector(clipped.end());
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1e-10) return;
        Vec3 normal = direction.cross(start.scale(-1)).normalize();
        if (normal.lengthSqr() < 1e-10) normal = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (normal.lengthSqr() < 1e-10) normal = new Vec3(1, 0, 0);
        Vec3 side = normal.scale(clipped.halfWidth());
        vertex(out, matrix, start.add(side), 0, 0, white, alpha);
        vertex(out, matrix, start.subtract(side), 0, 1, white, alpha);
        vertex(out, matrix, end.subtract(side), 1, 1, white, alpha);
        vertex(out, matrix, end.add(side), 1, 0, white, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 position, float u, float v,
                               boolean white, int alpha) {
        out.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .setUv(u, v).setColor(white ? 225 : 112, white ? 247 : 195, 255, alpha);
    }
    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    /** Read-only snapshot: points = ground point; numbers = phase, remaining ticks, burst ticks left. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            if (m.owner.isRemoved() || m.owner == mc.player && (!localEligible() || !AcademyClient.singleOwnerEpochMatches(m.stamp.epoch()))) continue;
            var s = m.state;
            out.add(DecorSnap.of(m.owner == mc.player, m.age, s.phase(), s.input(), java.util.List.of(dp(s.point())), s.phase(), s.remaining(), m.burst));
        }
        return out;
    }
}
