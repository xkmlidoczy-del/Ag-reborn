package cn.academy.port.client;

import cn.academy.port.highpower.PolarityTether;
import cn.academy.port.highpower.core.PolarityTetherRules;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;

/** NEW-113: two blue-white twisted ribbons, iron-gray anchor, inward dots. Zero spawned particles/entities. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class PolarityTetherEffects {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("academy","textures/port/white.png");
    private static final int MAX_CONTEXTS = 128, QUIET_TICKS = 12, SEGMENTS = 24;
    private static final Map<UUID, Marker> MARKERS = new HashMap<>();
    private static final Map<UUID, Long> TERMINALS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Marker {
        final Entity owner;
        PolarityTether.Visual state;
        int age, quiet;
        Marker(Entity owner, PolarityTether.Visual state) { this.owner = owner; this.state = state; }
    }
    private PolarityTetherEffects() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent e) { PolarityTether.clientReceiver = PolarityTetherEffects::receive; }
    }
    public static void receive(PolarityTether.Visual packet) {
        if (packet == null || packet.entity() < 0 || packet.owner() == null || packet.token() <= 0 || packet.epoch() <= 0
                || packet.sequence() <= 0 || packet.phase() < 0 || packet.phase() > 4 || packet.face() < 0 || packet.face() > 5 || !finite(packet.origin()) || !finite(packet.anchor())
                || packet.phase() != 4 && packet.origin().distanceToSqr(packet.anchor()) > 16.01 * 16.01) return;
        Minecraft mc = Minecraft.getInstance(); ClientLevel captured = mc.level; Object capturedConnection = mc.getConnection();
        mc.execute(() -> {
            if (captured == null || mc.level != captured || mc.getConnection() != capturedConnection) return;
            synchronize(mc);
            Entity owner = captured.getEntity(packet.entity());
            if (owner == null || !owner.getUUID().equals(packet.owner())) return;
            if (owner == mc.player && !AcademyClient.singleOwnerEpochMatches(packet.epoch())) return;
            Marker old = MARKERS.get(packet.owner());
            if (packet.token() <= TERMINALS.getOrDefault(packet.owner(), 0L)
                    || old != null && (packet.token() < old.state.token() || packet.token() == old.state.token() && packet.sequence() <= old.state.sequence())) return;
            if (packet.phase() == 4) { terminal(packet.owner(),packet.token()); MARKERS.remove(packet.owner()); return; }
            if (!owner.isAlive() || owner.isRemoved() || owner == mc.player && !localEligible()) return;
            if (old == null || old.state.token() != packet.token()) {
                if (old == null && MARKERS.size() >= MAX_CONTEXTS) return;
                MARKERS.put(packet.owner(),new Marker(owner,packet));
            } else { old.state = packet; old.quiet = 0; }
        });
    }
    public static void abortLocal() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null) return;
        Marker marker = MARKERS.remove(mc.player.getUUID());
        if (marker != null) terminal(mc.player.getUUID(),marker.state.token());
    }
    public static void clear() { MARKERS.clear(); TERMINALS.clear(); }
    private static void terminal(UUID owner,long token) {
        TERMINALS.merge(owner,token,Math::max);
        if (TERMINALS.size() > 256) TERMINALS.remove(TERMINALS.keySet().iterator().next());
    }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        var state = AcademyClient.state; Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && mc.isWindowActive() && state != null && state.category.equals("electromaster") && state.activated && state.learned(PolarityTether.ID)
                && state.level >= 1 && state.level <= 5 && state.presets.currentContains(PolarityTether.ID);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.isPaused()) return;
        Iterator<Marker> it = MARKERS.values().iterator();
        while (it.hasNext()) {
            Marker m = it.next(); m.age++; m.quiet++;
            if (world == null || !m.owner.isAlive() || m.owner.isRemoved() || world.getEntity(m.owner.getId()) != m.owner
                    || m.quiet >= QUIET_TICKS || m.age >= PolarityTetherRules.MAX_TICKS + QUIET_TICKS
                    || m.owner == mc.player && (!localEligible() || !AcademyClient.singleOwnerEpochMatches(m.state.epoch()))) {
                terminal(m.owner.getUUID(),m.state.token()); it.remove();
            }
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (world == null || MARKERS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var type = ClassicRenderTypes.world(WHITE); VertexConsumer out = buffers.getBuffer(type);
        Matrix4f matrix = event.getPoseStack().last().pose(); Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (Marker marker : MARKERS.values()) {
            if (marker.owner == mc.player && !localEligible()) continue;
            var state = marker.state;
            var face = net.minecraft.core.Direction.values()[state.face()];
            Vec3 normal = new Vec3(face.getStepX(),face.getStepY(),face.getStepZ());
            Vec3 start = marker.owner.getEyePosition(partial).add(0,-.18,0).subtract(camera), end = state.anchor().add(normal.scale(.035)).subtract(camera);
            Vec3 nearClipLook = null;
            if (marker.owner == mc.player && mc.options.getCameraType().isFirstPerson() && event.getCamera().getEntity() == marker.owner) {
                var look = event.getCamera().getLookVector(); var cameraUp = event.getCamera().getUpVector();
                nearClipLook = new Vec3(look.x(),look.y(),look.z());
                var wrist = PolarityTetherGeometry.wrist(point(nearClipLook),new PolarityTetherGeometry.Point(cameraUp.x(),cameraUp.y(),cameraUp.z()),
                        mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
                if (wrist == null) continue;
                start = vec(wrist);
            }
            Vec3 axis = end.subtract(start); double length = axis.length();
            if (!Double.isFinite(length) || length < .1 || length > 17) continue;
            axis = axis.scale(1/length);
            Vec3 across = axis.cross(new Vec3(0,1,0)).normalize();
            if (across.lengthSqr() < .1) across = axis.cross(new Vec3(1,0,0)).normalize();
            Vec3 up = axis.cross(across).normalize(); double time = marker.age + partial;
            // Pending placement is visibly dashed and subdued; no paid/tensioned look before server commit.
            int alpha = state.phase() == 0 ? 80 : state.phase() == 3 ? 150 : 220;
            for (int strand = 0; strand < 2; strand++) {
                Vec3 previous = helix(start,end,across,up,0,time,strand);
                for (int i = 1; i <= SEGMENTS; i++) {
                    Vec3 next = helix(start,end,across,up,(double)i/SEGMENTS,time,strand);
                    if (state.phase() != 0 || i % 2 == 0) line(out,matrix,previous,next,nearClipLook,.013,strand == 0 ? 130 : 225,strand == 0 ? 202 : 246,255,alpha);
                    previous = next;
                }
            }
            // Four iron-gray edges and an inset cross communicate the fixed magnetic anchor.
            Vec3 anchorSide = normal.cross(new Vec3(0,1,0)).normalize();
            if (anchorSide.lengthSqr() < .1) anchorSide = normal.cross(new Vec3(1,0,0)).normalize();
            Vec3 a = anchorSide.scale(.14), b = normal.cross(anchorSide).scale(.14);
            line(out,matrix,end.add(a),end.add(b),nearClipLook,.028,83,93,102,230);
            line(out,matrix,end.add(b),end.subtract(a),nearClipLook,.028,83,93,102,230);
            line(out,matrix,end.subtract(a),end.subtract(b),nearClipLook,.028,83,93,102,230);
            line(out,matrix,end.subtract(b),end.add(a),nearClipLook,.028,83,93,102,230);
            if (state.phase() == 2) for (int i = 0; i < 4; i++) {
                double along = PolarityTetherRules.inwardDotProgress(time,i);
                Vec3 dot = start.lerp(end,along);
                line(out,matrix,dot.subtract(axis.scale(.035)),dot.add(axis.scale(.035)),nearClipLook,.03,240,252,255,240);
            }
        }
        buffers.endBatch(type);
    }
    private static Vec3 helix(Vec3 start,Vec3 end,Vec3 across,Vec3 up,double t,double age,int strand) {
        double angle = t*Math.PI*6-age*.12+strand*Math.PI;
        double radius = .055*Math.sin(t*Math.PI);
        return start.lerp(end,t).add(across.scale(Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius));
    }
    private static PolarityTetherGeometry.Point point(Vec3 v) { return new PolarityTetherGeometry.Point(v.x,v.y,v.z); }
    private static Vec3 vec(PolarityTetherGeometry.Point p) { return new Vec3(p.x(),p.y(),p.z()); }
    private static void line(VertexConsumer out,Matrix4f matrix,Vec3 start,Vec3 end,Vec3 nearClipLook,double width,int r,int g,int b,int alpha) {
        if (nearClipLook != null) {
            var segment = PolarityTetherGeometry.clip(point(start),point(end),point(nearClipLook),width);
            if (segment == null) return;
            start = vec(segment.start()); end = vec(segment.end()); width = segment.halfWidth();
        }
        Vec3 direction = end.subtract(start), normal = direction.cross(start.scale(-1)).normalize();
        if (normal.lengthSqr() < 1e-10) normal = direction.cross(new Vec3(0,1,0)).normalize();
        if (normal.lengthSqr() < 1e-10) normal = new Vec3(1,0,0);
        Vec3 side = normal.scale(width);
        vertex(out,matrix,start.add(side),0,0,r,g,b,alpha); vertex(out,matrix,start.subtract(side),0,1,r,g,b,alpha);
        vertex(out,matrix,end.subtract(side),1,1,r,g,b,alpha); vertex(out,matrix,end.add(side),1,0,r,g,b,alpha);
    }
    private static void vertex(VertexConsumer out,Matrix4f matrix,Vec3 p,float u,float v,int r,int g,int b,int alpha) {
        out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(r,g,b,alpha);
    }
    private static boolean finite(Vec3 v) { return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }

    /** Read-only snapshot: points = origin, anchor, anchor-face normal; numbers = phase, quiet ticks. */
    private static double[] dp(net.minecraft.world.phys.Vec3 p) { return p == null ? DecorSnap.NONE : new double[] {p.x, p.y, p.z}; }
    static java.util.List<DecorSnap> decorSnaps(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        var out = new java.util.ArrayList<DecorSnap>();
        if (world == null) return out;
        for (Marker m : MARKERS.values()) {
            if (m.owner.isRemoved() || m.owner == mc.player && !localEligible()) continue;
            var s = m.state; var n = net.minecraft.core.Direction.values()[Math.floorMod(s.face(), 6)].getNormal();
            out.add(DecorSnap.of(m.owner == mc.player, m.age, s.phase(), s.token(), java.util.List.of(dp(s.origin()), dp(s.anchor()), new double[] {n.getX(), n.getY(), n.getZ()}), s.phase(), m.quiet));
        }
        return out;
    }
}
