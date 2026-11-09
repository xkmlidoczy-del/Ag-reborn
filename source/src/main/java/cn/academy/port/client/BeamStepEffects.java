package cn.academy.port.client;

import cn.academy.port.highpower.BeamStep;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** NEW232 application-confirmed stationary traces. Original ACA shield/ray media, no entities or collision. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class BeamStepEffects {
    private static final ResourceLocation SHIELD = texture("mdshield"), RAY = texture("mdray_small/tile");
    private static final BeamStepPlateState STATE = new BeamStepPlateState();
    private static final BeamStepFeedbackState FEEDBACK = new BeamStepFeedbackState();
    private static ClientLevel world;
    private static LocalPlayer player;
    private static Object connection;
    private static volatile long generation;
    private static long contextRevision = -1, contextSession, contextOwnerEpoch;
    private static int contextPreset = -1;
    private static String contextCategory;
    private static MultiBufferSource.BufferSource buffers;
    private BeamStepEffects() {}
    private static ResourceLocation texture(String name) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/" + name + ".png"); }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { BeamStep.clientReceiver = BeamStepEffects::receive; }
    }
    public static void receive(BeamStep.Visual packet) {
        if (packet == null || packet.owner() == null || packet.dimension() == null || packet.at() == null) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level; LocalPlayer actor = mc.player; Object link = mc.getConnection();
        long stamp = generation, session = AcademyClient.inputSessionEpoch();
        mc.execute(() -> {
            if (level == null || actor == null || link == null || mc.level != level || mc.player != actor
                    || mc.getConnection() != link || generation != stamp || !AcademyClient.sameInputSessionEpoch(session)) return;
            synchronize(mc);
            if (packet.entity() != actor.getId() || !packet.owner().equals(actor.getUUID())
                    || level.getEntity(packet.entity()) != actor || !packet.dimension().equals(level.dimension().location())
                    || !AcademyClient.singleOwnerEpochMatches(packet.epoch()) || packet.token() <= 0) return;
            if (packet.terminal()) { STATE.terminal(packet.token()); FEEDBACK.terminal(packet.token()); return; }
            if (!visibleState(mc) || packet.at().distanceToSqr(actor.position()) > 16 * 16) return;
            ownerEpoch(packet.epoch());
            publish(FEEDBACK.visual(new BeamStepFeedbackState.Visual(packet.token(),packet.epoch(),packet.issuedAt(),packet.expiresAt(),point(packet.at())),level.getGameTime()));
        });
    }
    /** Called only after the motion receiver authenticated its captured local input. No server acknowledgement. */
    static void motionOutcome(BeamStep.Grant grant, BeamStepFeedbackState.Outcome outcome) {
        Minecraft mc=Minecraft.getInstance(); synchronize(mc);
        if (!feedbackState(mc) || outcome==BeamStepFeedbackState.Outcome.APPLIED && !visibleState(mc)
                || grant == null || !grant.owner().equals(player.getUUID()) || grant.entity()!=player.getId()
                || !grant.dimension().equals(world.dimension().location()) || !AcademyClient.singleOwnerEpochMatches(grant.epoch())) return;
        ownerEpoch(grant.epoch());
        publish(FEEDBACK.outcome(grant.token(),grant.epoch(),grant.issuedAt(),grant.expiresAt(),world.getGameTime(),outcome));
    }
    private static void ownerEpoch(long epoch) {
        if(contextOwnerEpoch!=0 && contextOwnerEpoch!=epoch) { STATE.clear(); FEEDBACK.clear(); }
        contextOwnerEpoch=epoch;
    }
    private static void publish(BeamStepFeedbackState.Decision decision) {
        var visual=decision.visual();
        if(visual!=null) STATE.acceptApplied(visual.token(),visual.epoch(),visual.at(),visual.issued(),visual.expires(),world.getGameTime(),decision.visualAge());
        if(decision.notice()!=BeamStepFeedbackState.Notice.NONE) {
            String key=decision.notice()==BeamStepFeedbackState.Notice.PAID_EXPIRED ? "paid_expired" : "paid_noop";
            player.displayClientMessage(Component.translatable("academy.highpower.beam_step."+key),true);
        }
    }
    private static boolean visibleState(Minecraft mc) {
        var s = AcademyClient.state;
        return feedbackState(mc) && s.activated && s.overloadFine && !s.interfering;
    }
    /** A changed ability state may explain a paid no-op without authorizing a success visual. */
    private static boolean feedbackState(Minecraft mc) {
        var s=AcademyClient.state;
        return mc.getSingleplayerServer()!=null && !mc.getSingleplayerServer().isPublished() && player!=null && world!=null
                && player.isAlive() && !player.isRemoved() && s!=null
                && "meltdowner".equals(s.category) && s.level >= 3 && s.level <= 5 && s.learned(BeamStep.ID)
                && s.presets.currentContains(BeamStep.ID);
    }
    public static void clear() { generation++; STATE.clear(); FEEDBACK.clear(); contextOwnerEpoch=0; }
    private static void synchronize(Minecraft mc) {
        var s=AcademyClient.state;
        long revision=s==null?-1:s.presets.revision(), session=AcademyClient.inputSessionEpoch();
        int preset=s==null?-1:s.presets.current(); String category=s==null?null:s.category;
        if (world == mc.level && player == mc.player && connection == mc.getConnection()
                && contextRevision==revision && contextPreset==preset && contextSession==session && java.util.Objects.equals(contextCategory,category)) return;
        clear(); if (connection != mc.getConnection()) { STATE.resetConnection(); FEEDBACK.resetConnection(); }
        world = mc.level; player = mc.player; connection = mc.getConnection();
        contextRevision=revision; contextPreset=preset; contextSession=session; contextCategory=category;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!visibleState(mc) || contextOwnerEpoch!=0 && !AcademyClient.singleOwnerEpochMatches(contextOwnerEpoch)) { clear(); return; }
        STATE.tick(world.getGameTime(), mc.isPaused());
        FEEDBACK.tick(world.getGameTime(),mc.isPaused());
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!visibleState(mc) || STATE.plates().isEmpty() || mc.options.hideGui || mc.screen != null) return;
        event.getGuiGraphics().drawString(mc.font, Component.translatable("academy.highpower.beam_step.assist_risk"), 12, 55, 0xC4EAD1, true);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (!visibleState(mc) || STATE.plates().isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        Vec3 camera = event.getCamera().getPosition(); var look = event.getCamera().getLookVector();
        Vec3 forward = new Vec3(look.x(), look.y(), look.z());
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var modelView = RenderSystem.getModelViewStack(); modelView.pushMatrix();
        float[] oldColor = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.setShaderColor(1,1,1,1); modelView.set(event.getModelViewMatrix()); RenderSystem.applyModelViewMatrix();
            Matrix4f matrix = event.getPoseStack().last().pose();
            for (var plate : STATE.plates()) {
                Vec3 center = vec(plate.at()).add(0,-.045,0);
                float alpha = BeamStepPlateState.alpha(plate.age() + partial);
                if (alpha <= 0 || center.distanceToSqr(camera) > 32 * 32 || !world.hasChunkAt(BlockPos.containing(center))) continue;
                var corners = BeamStepPlateState.corners(point(center));
                // Avoid projecting any polygon crossing the near plane; there are only three tiny plates.
                if (corners.stream().anyMatch(p -> vec(p).subtract(camera).dot(forward) < .05)) continue;
                var shieldType = ClassicRenderTypes.world(SHIELD);
                VertexConsumer out = buffers.getBuffer(shieldType);
                for (int i = 0; i < corners.size(); i++) {
                    Vec3 a = vec(corners.get(i)), b = vec(corners.get((i+1)%corners.size()));
                    shieldVertex(out,matrix,center,center,camera,(int)(160*alpha));
                    shieldVertex(out,matrix,a,center,camera,(int)(160*alpha));
                    shieldVertex(out,matrix,b,center,camera,(int)(160*alpha));
                    shieldVertex(out,matrix,b,center,camera,(int)(160*alpha));
                }
                buffers.endBatch(shieldType);
                var rayType = ClassicRenderTypes.world(RAY); out = buffers.getBuffer(rayType);
                for (int i = 0; i < corners.size(); i++) {
                    Vec3 a = vec(corners.get(i)), b = vec(corners.get((i+1)%corners.size()));
                    Vec3 side = b.subtract(a).cross(new Vec3(0,1,0)).normalize().scale(.006);
                    vertex(out,matrix,a.add(side).subtract(camera),0,0,(int)(130*alpha));
                    vertex(out,matrix,a.subtract(side).subtract(camera),0,1,(int)(130*alpha));
                    vertex(out,matrix,b.subtract(side).subtract(camera),1,1,(int)(130*alpha));
                    vertex(out,matrix,b.add(side).subtract(camera),1,0,(int)(130*alpha));
                }
                buffers.endBatch(rayType);
            }
        } finally {
            try { buffers.endBatch(); } finally { modelView.popMatrix(); RenderSystem.applyModelViewMatrix(); RenderSystem.setShaderColor(oldColor[0],oldColor[1],oldColor[2],oldColor[3]); }
        }
    }
    private static void shieldVertex(VertexConsumer out, Matrix4f matrix, Vec3 at, Vec3 center, Vec3 camera, int alpha) {
        vertex(out,matrix,at.subtract(camera),(float)(.5+(at.x-center.x)/.88),(float)(.5+(at.z-center.z)/.54),alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int alpha) {
        out.addVertex(matrix,(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(226,255,234,alpha);
    }
    private static BeamStepPlateState.Point point(Vec3 p) { return new BeamStepPlateState.Point(p.x,p.y,p.z); }
    private static Vec3 vec(BeamStepPlateState.Point p) { return new Vec3(p.x(),p.y(),p.z()); }

    /** Read-only snapshot: one snap per live beam-step plate; points = plate centre; age = plate age. */
    static java.util.List<DecorSnap> decorSnaps() {
        var out = new java.util.ArrayList<DecorSnap>();
        if (Minecraft.getInstance().level == null) return out;
        for (var plate : STATE.plates()) if (plate.at() != null && plate.at().finite())
            out.add(DecorSnap.of(true, plate.age(), 0, plate.token(), java.util.List.of(new double[] {plate.at().x(), plate.at().y(), plate.at().z()})));
        return out;
    }
}
