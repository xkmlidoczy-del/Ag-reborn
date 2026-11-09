/* AcademyCraft 1.0.7 RendererCoinThrowing/LambdaLib RenderUtils adaptation. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.skill.CoinTosses;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Visual-only local simulation of the trusted coin_toss payload. It never
 * consumes inventory, CP, or applies damage. Reference only from client code.
 * Server progress is authoritative even if network delay makes the hint late.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicCoinEffects {
    public static final float SCALE = 0.3F;
    /** Classic drawEquippedItem places the two faces at +thickness and -thickness. */
    public static final double THICKNESS = 0.0625;
    private static final ResourceLocation FRONT = texture("coin_front");
    private static final ResourceLocation BACK = texture("coin_back");
    private static final ResourceLocation RAILGUN_ICON = ResourceLocation.fromNamespaceAndPath(
            "academy", "textures/abilities/electromaster/skills/railgun.png");
    private static final Map<Integer, VisualCoin> COINS = new HashMap<>();
    private static final cn.academy.port.client.terminal.TerminalCoinReturnLedger RETURNED=new cn.academy.port.client.terminal.TerminalCoinReturnLedger();
    private static ClientLevel activeLevel;
    private static MultiBufferSource.BufferSource buffers;
    private static long lastWallMillis = Util.getMillis(), clockMillis = lastWallMillis;

    private ClassicCoinEffects() {}

    private static final class VisualCoin {
        final long token;
        final CoinTosses.Trajectory trajectory;
        final Vector3f axis;
        final boolean qte;
        long lastTick;
        boolean locallyJudged;

        VisualCoin(CompoundTag tag, ClientLevel level) {
            token = tag.getLong("token");
            trajectory = new CoinTosses.Trajectory(tag.getDouble("y"), tag.getDouble("vy"));
            axis = new Vector3f((float) tag.getDouble("ax"), (float) tag.getDouble("ay"),
                    (float) tag.getDouble("az")).normalize();
            qte = tag.getBoolean("qte");
            lastTick = level.getGameTime();
        }
    }

    /** Forward both coin_toss and coin_end tags here from the client packet switch. */
    public static void receive(CompoundTag data) {
        if (data == null) return;
        CompoundTag tag = data.copy();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel target = minecraft.level;
        minecraft.execute(() -> {
            if (target == null || minecraft.level != target) return;
            synchronizeWorld(minecraft);
            int entity = tag.getInt("entity");
            long token = tag.getLong("token");
            VisualCoin current = COINS.get(entity);
            if ("coin_end".equals(tag.getString("kind"))) {
                if(tag.getBoolean("natural_return"))announceReturn(entity,token);
                if (current != null && current.token == token) COINS.remove(entity);
                return;
            }
            if (!"coin_toss".equals(tag.getString("kind")) || token <= 0
                    || !finite(tag.getDouble("y")) || !finite(tag.getDouble("vy"))
                    || !finite(tag.getDouble("ax")) || !finite(tag.getDouble("ay"))
                    || !finite(tag.getDouble("az"))) return;
            double axisLength = tag.getDouble("ax") * tag.getDouble("ax")
                    + tag.getDouble("ay") * tag.getDouble("ay") + tag.getDouble("az") * tag.getDouble("az");
            if (!Double.isFinite(axisLength) || axisLength < 1.0E-12) return;
            if (current != null && current.token >= token) return;
            if (COINS.size() >= 128 && current == null) return;
            COINS.put(entity, new VisualCoin(tag, target));
            if(minecraft.player!=null&&entity==minecraft.player.getId()&&tag.getBoolean("qte"))AcademyClient.acceptedRailgunCoin();
        });
    }

    /** One local QTE per tossed coin, including an early failed press. */
    public static OptionalLong attempt() {
        VisualCoin coin = localCoin();
        if (coin == null || !coin.qte || coin.locallyJudged) return OptionalLong.empty();
        coin.locallyJudged = true;
        // Send the attempt even when too early: server records the failed judgement.
        return OptionalLong.of(coin.token);
    }

    public static boolean hasPendingAttempt() {
        VisualCoin coin = localCoin();
        return coin != null && coin.qte && !coin.locallyJudged;
    }

    public static boolean ready() {
        VisualCoin coin = localCoin();
        return coin != null && coin.qte && !coin.locallyJudged && coin.trajectory.ready();
    }

    /** UI/debug hint only. This value must never be sent as the server judgement. */
    public static double progress() {
        VisualCoin coin = localCoin();
        return coin == null ? Double.NaN : coin.trajectory.progress();
    }

    public static void clear() { COINS.clear();RETURNED.clear(); }
    private static void announceReturn(int entity,long token){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.player.getId()!=entity||!RETURNED.claim(entity,token))return;
        if(cn.academy.port.ClassicHudConfig.SPEC.isLoaded()&&cn.academy.port.ClassicHudConfig.HEADS_OR_TAILS.get())mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("ac.headsOrTails."+mc.player.getRandom().nextInt(2)),false);
    }

    private static VisualCoin localCoin() {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        return minecraft.player == null ? null : COINS.get(minecraft.player.getId());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null || minecraft.isPaused()) return;
        long now = minecraft.level.getGameTime();
        COINS.entrySet().removeIf(entry -> {
            VisualCoin coin = entry.getValue();
            if (!(minecraft.level.getEntity(entry.getKey()) instanceof Player player)
                    || !player.isAlive() || player.isRemoved()) return true;
            if (coin.lastTick != now) {
                coin.lastTick = now;
                coin.trajectory.tick();
            }
            boolean finished=coin.trajectory.finished(player.getY());
            if(finished)announceReturn(entry.getKey(),coin.token);
            return finished;
        });
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null || COINS.isEmpty()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(32768));
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        for (var entry : COINS.entrySet()) {
            if (!(minecraft.level.getEntity(entry.getKey()) instanceof Player player)) continue;
            VisualCoin coin = entry.getValue();
            if (coin.trajectory.height() < player.getY()) continue;
            boolean local = player == minecraft.player;
            boolean firstPerson = local && minecraft.options.getCameraType().isFirstPerson();
            double x = local ? 0 : Mth.lerp(partial, player.xo, player.getX()) - camera.x;
            double z = local ? 0 : Mth.lerp(partial, player.zo, player.getZ()) - camera.z;
            double y = Mth.lerp(partial, coin.trajectory.previousHeight(), coin.trajectory.height()) - camera.y;
            float yaw = firstPerson ? Mth.rotLerp(partial, player.yRotO, player.getYRot())
                    : Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
            poses.pushPose();
            try {
                poses.translate(x, y, z);
                poses.mulPose(Axis.YN.rotationDegrees(yaw));
                poses.translate(-0.63, -0.60, 0.30);
                poses.scale(SCALE, SCALE, SCALE);
                poses.translate(0.5, 0.5, 0);
                poses.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(rotationDegrees(clockMillis)), coin.axis));
                poses.translate(-0.5, -0.5, 0);
                drawCoin(poses.last().pose());
            } finally {
                poses.popPose();
            }
        }
        // Isolated buffers/types prevent flushing another renderer's pending work.
        buffers.endBatch(CoinRenderTypes.FRONT);
        buffers.endBatch(CoinRenderTypes.BACK);
    }

    /** Exact GameTimer millisecond remainder and original half-turn/reset cadence. */
    public static double rotationDegrees(long gameMillis) {
        return Math.floorMod(gameMillis, 150L) * 360.0 / 300.0;
    }

    /** Original two textured faces and 32 doubled, front-textured extrusion strips. */
    private static void drawCoin(Matrix4f matrix) {
        VertexConsumer back = buffers.getBuffer(CoinRenderTypes.BACK);
        vertex(back, matrix, 0, 0, THICKNESS, 1, 1);
        vertex(back, matrix, 1, 0, THICKNESS, 0, 1);
        vertex(back, matrix, 1, 1, THICKNESS, 0, 0);
        vertex(back, matrix, 0, 1, THICKNESS, 1, 0);
        VertexConsumer front = buffers.getBuffer(CoinRenderTypes.FRONT);
        vertex(front, matrix, 0, 1, -THICKNESS, 1, 0);
        vertex(front, matrix, 1, 1, -THICKNESS, 0, 0);
        vertex(front, matrix, 1, 0, -THICKNESS, 0, 1);
        vertex(front, matrix, 0, 0, -THICKNESS, 1, 1);
        for (int strip = 0; strip < 32; strip++) {
            float x = strip / 32.0F;
            float u = 1 - x - 1.0F / (32 * 32);
            vertex(front, matrix, x, 0, -THICKNESS, u, 1);
            vertex(front, matrix, x, 0, THICKNESS, u, 1);
            vertex(front, matrix, x, 1, THICKNESS, u, 0);
            vertex(front, matrix, x, 1, -THICKNESS, u, 0);
            vertex(front, matrix, x, 1, THICKNESS, u, 0);
            vertex(front, matrix, x, 0, THICKNESS, u, 1);
            vertex(front, matrix, x, 0, -THICKNESS, u, 1);
            vertex(front, matrix, x, 1, -THICKNESS, u, 0);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, double x, double y, double z, float u, float v) {
        out.addVertex(matrix, (float) x, (float) y, (float) z).setUv(u, v).setColor(255, 255, 255, 255);
    }

    private static void synchronizeWorld(Minecraft minecraft) {
        if (minecraft.level == activeLevel) return;
        COINS.clear();RETURNED.clear();
        activeLevel = minecraft.level;
    }

    private static void updateClock(Minecraft minecraft) {
        long now = Util.getMillis();
        if (!minecraft.isPaused()) clockMillis += Math.max(0, now - lastWallMillis);
        lastWallMillis = now;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath("academy", "textures/items/" + path + ".png");
    }

    /** Modern buffered renderer with real back-face culling and alpha-tested texture shader. */
    private static final class CoinRenderTypes extends RenderType {
        static final RenderType FRONT = coin("front", ClassicCoinEffects.FRONT);
        static final RenderType BACK = coin("back", ClassicCoinEffects.BACK);

        private CoinRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                                int size, boolean crumble, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumble, sort, setup, clear);
        }

        private static RenderType coin(String face, ResourceLocation texture) {
            return RenderType.create("academy_coin_" + face, DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(CULL)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .setWriteMaskState(COLOR_DEPTH_WRITE)
                            .setOutputState(PARTICLES_TARGET)
                            .createCompositeState(false));
        }
    }
}
