package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.RailThunderArray;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import java.util.*;

/** NEW151 server-confirmed bounded ribbons; reuses the classic rail assets without changing classic effects. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class RailThunderArrayEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation[] RAIL = {texture("effects/railgun/blend_in"), texture("effects/railgun/tile"), texture("effects/railgun/blend_out")};
    private static final int MAX_CONTEXTS = 64, MAX_VISIBLE_CONTEXTS = 8, MAX_FLASHES = 16;
    private static final RailThunderArrayState STATE = new RailThunderArrayState();
    private static final Map<UUID, Marker> MARKERS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static Entity localPlayer;
    private static Object connection;
    private static long clearGeneration;
    private static MultiBufferSource.BufferSource buffers;
    private static final class Flash {
        final RailThunderArray.Segment segment;
        final int phase;
        int age;
        Flash(RailThunderArray.Segment segment, int phase) { this.segment = segment; this.phase = phase; }
    }
    private static final class Marker {
        final Entity owner;
        final RailThunderArrayState.Lease lease;
        final List<Flash> flashes = new ArrayList<>();
        RailThunderArray.Visual state;
        Marker(Entity owner, RailThunderArray.Visual packet) { this.owner = owner; lease = new RailThunderArrayState.Lease(packet.age()); update(packet); }
        RailThunderArrayState.Identity identity() { return new RailThunderArrayState.Identity(state.entity(), state.owner(), state.epoch(), state.input(), state.token()); }
        void update(RailThunderArray.Visual packet) {
            state = packet; lease.received(packet.age(), packet.phase() == RailThunderArrayState.ENDED);
            // An active heartbeat is empty; it cannot reset an already displayed shot or create a hit.
            for (var segment : packet.segments()) if (segment.status() != RailThunderArrayState.PREVIEW) {
                if (flashes.size() == MAX_FLASHES) flashes.removeFirst();
                flashes.add(new Flash(segment, packet.phase()));
            }
        }
    }
    private RailThunderArrayEffects() {}
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { RailThunderArray.clientReceiver = RailThunderArrayEffects::receive; }
    }
    private static RailThunderArrayState.Frame frame(RailThunderArray.Visual p) {
        return new RailThunderArrayState.Frame(new RailThunderArrayState.Identity(p.entity(), p.owner(), p.epoch(), p.input(), p.token()),
                p.sequence(), p.phase(), p.age(), point(p.origin()), p.axes().stream().map(RailThunderArrayEffects::point).toList(),
                p.segments().stream().map(s -> new RailThunderArrayState.Segment(s.axis(), s.entity(), s.target(), point(s.from()), point(s.to()), s.status())).toList(), p.reason());
    }
    public static void receive(RailThunderArray.Visual packet) {
        if (packet == null || packet.origin() == null || packet.axes() == null || packet.axes().size() > 3
                || packet.axes().stream().anyMatch(Objects::isNull) || packet.segments() == null || packet.segments().size() > 16
                || packet.segments().stream().anyMatch(s -> s == null || s.from() == null || s.to() == null)) return;
        var frame = frame(packet); if (!RailThunderArrayState.valid(frame)) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        ClientLevel captured = mc.level; Entity capturedPlayer = mc.player; Object capturedConnection = mc.getConnection();
        long capturedSession = AcademyClient.inputSessionEpoch(), generation = clearGeneration;
        mc.execute(() -> {
            if (captured == null || mc.level != captured || mc.player != capturedPlayer || mc.getConnection() != capturedConnection
                    || clearGeneration != generation || !AcademyClient.sameInputSessionEpoch(capturedSession)) return;
            Entity owner = captured.getEntity(packet.entity());
            if (owner == null || !owner.getUUID().equals(packet.owner()) || !owner.isAlive() || owner.isRemoved()) return;
            boolean local = owner == mc.player;
            if (local && (!localEligible() || !AcademyClient.singleOwnerEpochMatches(packet.epoch())
                    || !AcademyClient.railArrayInputMatches(packet.input(), packet.epoch()))) return;
            if (!STATE.accept(frame, local)) return;
            Marker old = MARKERS.get(packet.owner());
            if (local) {
                AcademyClient.railArrayAcceptedInput(packet.input(), packet.epoch());
                if (packet.phase() == RailThunderArrayState.ENDED) AcademyClient.railArrayEndedInput(packet.input(), packet.epoch());
                else if (packet.phase() == RailThunderArrayState.PREPARING && (old == null || old.state.axes().size() != packet.axes().size()))
                    mc.player.displayClientMessage(Component.translatable("academy.highpower.rail_thunder_array.marks", packet.axes().size()), true);
                else if (old == null || old.state.phase() != packet.phase()) {
                    if (packet.phase() == RailThunderArrayState.TAP) mc.player.displayClientMessage(Component.translatable("academy.highpower.rail_thunder_array.mode_tap"), true);
                    if (packet.phase() == RailThunderArrayState.HOLD) mc.player.displayClientMessage(Component.translatable("academy.highpower.rail_thunder_array.mode_hold"), true);
                }
            }
            // Normal completion may finish its already confirmed five-tick flashes. Cancellation removes everything now.
            if (packet.phase() == RailThunderArrayState.ENDED && ((packet.reason() != 0 && packet.reason() != 2) || old == null || old.flashes.isEmpty())) { MARKERS.remove(packet.owner()); return; }
            if (old == null || old.state.token() != packet.token()) {
                if (old == null && MARKERS.size() >= MAX_CONTEXTS) { STATE.retire(frame.id()); return; }
                MARKERS.put(packet.owner(), new Marker(owner, packet));
            } else old.update(packet);
        });
    }
    public static void abortLocal(long input, long epoch) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); STATE.cancel(epoch, input);
        if (mc.player == null) return; Marker marker = MARKERS.get(mc.player.getUUID());
        // A newer request can be cancelled before its acknowledgment. It also owns all earlier marks in the same cast.
        if (marker != null && marker.state.epoch() == epoch && marker.state.input() <= input) {
            STATE.retire(marker.identity()); MARKERS.remove(mc.player.getUUID());
        }
    }
    public static void clear() { clearGeneration++; STATE.clear(); MARKERS.clear(); RailThunderArrayMaterialPreview.clear(); }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && localPlayer == mc.player && connection == mc.getConnection()) return;
        clear(); STATE.resetSession(); world = mc.level; localPlayer = mc.player; connection = mc.getConnection();
    }
    private static boolean localEligible() {
        Minecraft mc = Minecraft.getInstance(); var state = AcademyClient.state;
        return mc.screen == null && mc.isWindowActive() && state.activated && state.overloadFine && !state.interfering
                && HighPowerSkills.CATEGORY.equals(state.category) && state.level == 5 && state.learned(RailThunderArray.ID)
                && state.presets.currentContains(RailThunderArray.ID) && SkillCatalog.enabled(HighPowerSkills.CATEGORY, RailThunderArray.ID);
    }
    private static boolean validOwner(Marker marker, Minecraft mc) {
        return world != null && marker.owner.isAlive() && !marker.owner.isRemoved() && world.getEntity(marker.owner.getId()) == marker.owner
                && (marker.owner != mc.player || localEligible() && AcademyClient.singleOwnerEpochMatches(marker.state.epoch())
                && (marker.state.phase() == RailThunderArrayState.ENDED || AcademyClient.railArrayInputMatches(marker.state.input(), marker.state.epoch())));
    }
    public static ClassicAbilityHud.DelegateVisual localVisual() {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Marker marker = mc.player == null ? null : MARKERS.get(mc.player.getUUID());
        if (marker == null || !validOwner(marker, mc) || marker.state.phase() == RailThunderArrayState.ENDED) return ClassicAbilityHud.DelegateVisual.IDLE;
        return marker.state.phase() <= RailThunderArrayState.ARMED ? ClassicAbilityHud.DelegateVisual.CHARGE : ClassicAbilityHud.DelegateVisual.ACTIVE;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); boolean paused = mc.isPaused();
        Iterator<Marker> it = MARKERS.values().iterator();
        while (it.hasNext()) {
            Marker marker = it.next();
            if (!marker.lease.tick(paused, validOwner(marker, mc))) { STATE.retire(marker.identity()); it.remove(); continue; }
            if (!paused) marker.flashes.removeIf(flash -> ++flash.age >= RailThunderArrayState.FLASH_TICKS);
        }
    }
    /** Read-only visibility can suppress stale geometry; it cannot authorize hits or acquire targets. */
    private static boolean visible(Entity owner, Vec3 from, Vec3 to) {
        if (world == null || !RailThunderArrayGeometry.validEndpoints(point(from), point(to))) return false;
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 8));
        for (int i = 0; i <= steps; i++) if (!world.hasChunkAt(BlockPos.containing(from.lerp(to, (double) i / steps)))) return false;
        var hit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) <= .005625;
    }
    private static boolean targetVisible(Marker marker, RailThunderArray.Segment segment, boolean confirmedHit) {
        if (segment.entity() < 0 || confirmedHit) return true;
        Entity target = world.getEntity(segment.entity());
        return target != null && target.getUUID().equals(segment.target()) && target.isAlive() && !target.isRemoved();
    }
    private static Vec3 visualOrigin(Marker marker, RailThunderArray.Segment segment, Minecraft mc, Vec3 camera, Vec3 look, Vec3 up) {
        if (marker.owner != mc.player || !mc.options.getCameraType().isFirstPerson() || !segment.from().equals(marker.state.origin())) return segment.from();
        return vec(RailThunderArrayGeometry.firstPersonOrigin(point(segment.from()), point(segment.to()), point(camera), point(look), point(up),
                mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (world == null || MARKERS.isEmpty() && !RailThunderArrayMaterialPreview.active()) return;
        Vec3 camera = event.getCamera().getPosition();
        List<Marker> visible = MARKERS.values().stream().filter(m -> validOwner(m, mc) && (m.owner == mc.player || m.state.origin().distanceToSqr(camera) <= 128 * 128))
                .sorted(Comparator.comparingInt(m -> m.owner == mc.player ? 0 : 1)).limit(MAX_VISIBLE_CONTEXTS).toList();
        if (visible.isEmpty() && !RailThunderArrayMaterialPreview.active()) return; if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        try {
            modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix(); Matrix4f matrix = event.getPoseStack().last().pose();
            var l = event.getCamera().getLookVector(); var u = event.getCamera().getUpVector();
            Vec3 look = new Vec3(l.x(), l.y(), l.z()), up = new Vec3(u.x(), u.y(), u.z());
            int[] grandBudget = {RailThunderArrayGrandGeometry.maxGlobalQuads(cn.academy.port.VisualTierConfig.tier())};
            int grandContexts = 0;
            double partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
            for (Marker marker : visible) {
                if (RailThunderArrayGrandMaterial.enabled() && grandContexts < RailThunderArrayGrandGeometry.MAX_CONTROLLERS
                        && grandMarker(marker, matrix, mc, camera, look, up, partial, grandBudget)) {
                    grandContexts++;
                    continue;
                }
                originalMarker(marker, matrix, mc, camera, look, up);
            }
            // Explicitly labelled synthetic geometry, opt-in isolated client only. No STATE/receipt ingress.
            RailThunderArrayMaterialPreview.beginRender();
            if (RailThunderArrayMaterialPreview.active()) for (var fixture : RailThunderArrayMaterialPreview.frames()) {
                Marker marker = new Marker(mc.player, fixture);
                for (Flash flash : marker.flashes) flash.age = (int) Math.floor(RailThunderArrayMaterialPreview.segmentAge(flash.segment.axis()));
                if (!RailThunderArrayMaterialPreview.baseline() && grandContexts < RailThunderArrayGrandGeometry.MAX_CONTROLLERS && grandMarker(marker, matrix, mc, camera, look, up, .5, grandBudget)) {grandContexts++;RailThunderArrayMaterialPreview.rendered(true);}
                else {originalMarker(marker, matrix, mc, camera, look, up);RailThunderArrayMaterialPreview.rendered(false);}
            }

        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); }
        }
    }
    /** Verbatim .55 per-marker rendering; optional failure/overflow and OFF retain this path. */
    private static void originalMarker(Marker marker, Matrix4f matrix, Minecraft mc, Vec3 camera, Vec3 look, Vec3 up) {

                if (marker.state.phase() <= RailThunderArrayState.ARMED) for (var segment : marker.state.segments()) {
                    if (segment.status() == RailThunderArrayState.PREVIEW && visible(marker.owner, segment.from(), segment.to())) {
                        Vec3 from = visualOrigin(marker, segment, mc, camera, look, up);
                        line(WHITE, matrix, marker.owner, from, segment.to(), camera, look, up, RailThunderArrayGeometry.PREVIEW_WIDTH,
                                RailThunderArrayGeometry.ORANGE, RailThunderArrayGeometry.PREVIEW_ALPHA);
                    }
                }
                for (Flash flash : marker.flashes) {
                    var segment = flash.segment; boolean hit = segment.status() == RailThunderArrayState.HIT;
                    if (!targetVisible(marker, segment, hit) || !visible(marker.owner, segment.from(), segment.to())) continue;
                    Vec3 from = visualOrigin(marker, segment, mc, camera, look, up);
                    int alpha = RailThunderArrayGeometry.EMISSION_ALPHA * (RailThunderArrayState.FLASH_TICKS - flash.age) / RailThunderArrayState.FLASH_TICKS;
                    if (segment.status() == RailThunderArrayState.REFUSED) {
                        // A refusal never becomes a warm beam, bright branch or confirmed hit marker.
                        for (var tick : RailThunderArrayGeometry.hit(point(segment.to()), point(look), flash.age))
                            line(WHITE, matrix, marker.owner, vec(tick.start()), vec(tick.end()), camera, look, up, .0015, 0x919BA8, alpha / 3);
                        continue;
                    }
                    if (flash.phase == RailThunderArrayState.TAP) {
                        for (var part : RailThunderArrayGeometry.rail(point(from), point(segment.to())))
                            line(RAIL[part.texture()], matrix, marker.owner, vec(part.segment().start()), vec(part.segment().end()), camera, look, up,
                                    RailThunderArrayGeometry.RAIL_HALO_WIDTH, 0xFFFFFF, alpha);
                        line(WHITE, matrix, marker.owner, from, segment.to(), camera, look, up,
                                RailThunderArrayGeometry.RAIL_CORE_WIDTH, RailThunderArrayGeometry.IVORY, alpha);
                        for (var branch : RailThunderArrayGeometry.sideBranches(point(from), point(segment.to()), marker.state.token() + segment.axis(), flash.age / 2))
                            line(WHITE, matrix, marker.owner, vec(branch.start()), vec(branch.end()), camera, look, up,
                                    RailThunderArrayGeometry.BRANCH_WIDTH, RailThunderArrayGeometry.BLUE_WHITE, alpha * 2 / 3);
                    } else if (flash.phase == RailThunderArrayState.HOLD) {
                        for (var branch : RailThunderArrayGeometry.branch(point(from), point(segment.to()), marker.state.token() + segment.axis(), flash.age / 2)) {
                            line(WHITE, matrix, marker.owner, vec(branch.start()), vec(branch.end()), camera, look, up, .007, RailThunderArrayGeometry.BLUE, alpha / 2);
                            line(WHITE, matrix, marker.owner, vec(branch.start()), vec(branch.end()), camera, look, up,
                                    RailThunderArrayGeometry.BRANCH_WIDTH, RailThunderArrayGeometry.BLUE_WHITE, alpha);
                        }
                    }
                    if (hit) for (var tick : RailThunderArrayGeometry.hit(point(segment.to()), point(segment.to().subtract(segment.from())), flash.age))
                        line(WHITE, matrix, marker.owner, vec(tick.start()), vec(tick.end()), camera, look, up, .0025,
                                flash.phase == RailThunderArrayState.TAP ? RailThunderArrayGeometry.IVORY : RailThunderArrayGeometry.BLUE_WHITE, alpha);
                }

    }
    /** Build and validate the complete optional submission before reserving or drawing any vertex. */
    private static boolean grandMarker(Marker marker, Matrix4f matrix, Minecraft mc, Vec3 camera,
                                       Vec3 look, Vec3 up, double partial, int[] budget) {
        int generation = RailThunderArrayGrandMaterial.generation();
        List<RailThunderArrayGrandGeometry.Stroke> strokes = new ArrayList<>();
        List<RailThunderArrayGrandGeometry.Quad> quads = new ArrayList<>();
        List<RailThunderArrayGrandGeometry.Billboard> sprites = new ArrayList<>();
        List<RailThunderArrayGrandGeometry.Stroke> extras = new ArrayList<>();
        List<RailThunderArrayGrandGeometry.Decal> decals = new ArrayList<>();
        boolean low = marker.state.origin().distanceToSqr(camera) > 48 * 48;
        // Far controllers keep the frozen look; near ones follow the configured tier.
        cn.academy.port.VisualTier tier = low ? cn.academy.port.VisualTier.LOW : cn.academy.port.VisualTierConfig.tier();
        double interpolation = Double.isFinite(partial) ? Math.clamp(partial, 0, 1) : 0;
        try {
            if (marker.state.phase() <= RailThunderArrayState.ARMED) {
                for (var segment : marker.state.segments()) {
                    if (segment.status() != RailThunderArrayState.PREVIEW || !visible(marker.owner, segment.from(), segment.to())) continue;
                    Vec3 from = visualOrigin(marker, segment, mc, camera, look, up);
                    strokes.add(new RailThunderArrayGrandGeometry.Stroke(new RailThunderArrayGeometry.Segment(point(from), point(segment.to())),
                            RailThunderArrayGeometry.PREVIEW_WIDTH, RailThunderArrayGeometry.ORANGE, RailThunderArrayGeometry.PREVIEW_ALPHA, RailThunderArrayGrandGeometry.WHITE));
                    strokes.addAll(RailThunderArrayGrandGeometry.preparation(point(from), point(segment.to()), segment.axis(), marker.lease.age() + interpolation, low));
                }
            }
            Set<Integer> converged = new HashSet<>();
            for (Flash flash : marker.flashes) {
                var segment = flash.segment;
                // A refusal preserves the complete original gray feedback for this controller.
                if (segment.status() == RailThunderArrayState.REFUSED) return false;
                boolean hit = segment.status() == RailThunderArrayState.HIT;
                if (!targetVisible(marker, segment, hit) || !visible(marker.owner, segment.from(), segment.to())) continue;
                Vec3 from = visualOrigin(marker, segment, mc, camera, look, up);
                double age = flash.age + interpolation;
                if (flash.phase == RailThunderArrayState.TAP && converged.add(segment.axis()))
                    strokes.addAll(RailThunderArrayGrandGeometry.preparation(point(from), point(segment.to()), segment.axis(), age, low));
                strokes.addAll(RailThunderArrayGrandGeometry.emission(point(from), point(segment.to()), segment.axis(),
                        marker.state.token(), age, flash.phase == RailThunderArrayState.HOLD, hit, low));
                extras.addAll(RailThunderArrayGrandGeometry.amplify(point(from), point(segment.to()), segment.axis(),
                        marker.state.token(), age, flash.phase == RailThunderArrayState.HOLD, hit, tier));
                sprites.addAll(RailThunderArrayGrandGeometry.billboards(point(from), point(segment.to()), segment.axis(),
                        marker.state.token(), age, flash.phase == RailThunderArrayState.HOLD, hit, tier));
                var fx = RailThunderArrayGrandGeometry.fx(point(from), point(segment.to()), segment.axis(),
                        marker.state.token(), age, flash.phase == RailThunderArrayState.HOLD, hit, tier);
                extras.addAll(fx.strokes()); sprites.addAll(fx.sprites()); decals.addAll(fx.decals());
            }
            // Base (frozen) geometry keeps its strict caps and refusal semantics; decoration is trimmed, never fatal.
            if (strokes.size() > RailThunderArrayGrandGeometry.MAX_STROKES_PER_CONTROLLER) return false;
            for (var stroke : strokes) {
                if (!visible(marker.owner, vec(stroke.segment().start()), vec(stroke.segment().end()))) continue;
                for (var quad : RailThunderArrayGrandGeometry.project(stroke, point(camera), point(look), point(up))) {
                    // Supplemental edge probes; native depth testing occludes the entire rasterized interior.
                    if (!visible(marker.owner, camera.add(vec(quad.a())), camera.add(vec(quad.d())))
                            || !visible(marker.owner, camera.add(vec(quad.b())), camera.add(vec(quad.c())))) continue;
                    quads.add(quad);
                }
            }
            if (quads.size() > RailThunderArrayGrandGeometry.MAX_QUADS_PER_CONTROLLER || quads.size() > budget[0]
                    || generation != RailThunderArrayGrandMaterial.generation() || !RailThunderArrayGrandMaterial.enabled()) return false;
            if (tier.enhanced()) {
                List<RailThunderArrayGrandGeometry.Quad> decoration = new ArrayList<>();
                for (var stroke : extras) {
                    if (!visible(marker.owner, vec(stroke.segment().start()), vec(stroke.segment().end()))) continue;
                    decoration.addAll(RailThunderArrayGrandGeometry.project(stroke, point(camera), point(look), point(up), tier));
                }
                for (var decal : decals) decoration.addAll(RailThunderArrayGrandGeometry.projectDecal(decal, point(camera), point(look), point(up), tier));
                for (var sprite : sprites) {
                    if (!visible(marker.owner, camera, camera.add(vec(sprite.center()).subtract(camera)))) continue;
                    decoration.addAll(RailThunderArrayGrandGeometry.projectBillboard(sprite, point(camera), point(look), point(up), tier));
                }
                int room = Math.max(0, Math.min(RailThunderArrayGrandGeometry.maxQuads(tier), budget[0]) - quads.size());
                quads.addAll(decoration.subList(0, Math.min(room, decoration.size())));
            }
        } catch (RuntimeException unavailable) { com.mojang.logging.LogUtils.getLogger().warn("NEW151 optional submission refused", unavailable); return false; }
        budget[0] -= quads.size();
        // No callbacks, resource selection or budget mutation after starting a complete submission.
        for (var quad : quads) {
            int texture = quad.texture();
            VertexConsumer out = buffers.getBuffer(RailThunderArrayGrandGeometry.additive(texture) ? VisualAdditiveMaterial.additive(RailThunderArrayGrandMaterial.texture(texture))
                    : texture == RailThunderArrayGrandGeometry.GLOW ? RailThunderArraySoftMaterial.world(RailThunderArrayGrandMaterial.texture(texture)) : ClassicRenderTypes.world(RailThunderArrayGrandMaterial.texture(texture)));
            float lo = RailThunderArrayGrandGeometry.lineProfile(texture) ? 8f / 32 : 0;
            float hi = RailThunderArrayGrandGeometry.lineProfile(texture) ? 24f / 32 : 1;
            vertex(out, matrix, vec(quad.a()), quad.minV(), lo, quad.rgb(), quad.alpha());
            vertex(out, matrix, vec(quad.b()), quad.minV(), hi, quad.rgb(), quad.alpha());
            vertex(out, matrix, vec(quad.c()), quad.maxV(), hi, quad.rgb(), quad.alpha());
            vertex(out, matrix, vec(quad.d()), quad.maxV(), lo, quad.rgb(), quad.alpha());
        }
        return true;
    }
    private static void line(ResourceLocation texture, Matrix4f matrix, Entity owner, Vec3 from, Vec3 to, Vec3 camera, Vec3 look, Vec3 up,
                             double width, int rgb, int alpha) {
        if (alpha <= 0 || !visible(owner, from, to)) return;
        var clipped = RailThunderArrayGeometry.nearClip(point(from.subtract(camera)), point(to.subtract(camera)), point(look), width);
        VertexConsumer out = buffers.getBuffer(ClassicRenderTypes.world(texture));
        for (var segment : RailThunderArrayGeometry.clearCenter(clipped, point(look), point(up), width)) {
            var ribbon = RailThunderArrayGeometry.ribbon(segment, point(look), width); if (ribbon == null) continue;
            Vec3 a = vec(ribbon.start()), b = vec(ribbon.end()), side = vec(ribbon.side());
            vertex(out, matrix, a.add(side), 0, 0, rgb, alpha); vertex(out, matrix, a.subtract(side), 0, 1, rgb, alpha);
            vertex(out, matrix, b.subtract(side), 1, 1, rgb, alpha); vertex(out, matrix, b.add(side), 1, 0, rgb, alpha);
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int rgb, int alpha) {
        out.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, alpha);
    }
    private static RailThunderArrayGeometry.Point point(Vec3 p) { return new RailThunderArrayGeometry.Point(p.x, p.y, p.z); }
    private static Vec3 vec(RailThunderArrayGeometry.Point p) { return new Vec3(p.x(), p.y(), p.z()); }
}
