package cn.academy.port.client.train;

import cn.academy.port.train.TrainCarEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws the baked Shinkansen car meshes with per-vertex colours on the vanilla white texture; shells cull back faces so riders see out. */
public final class TrainCarRenderer extends EntityRenderer<TrainCarEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public TrainCarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override public ResourceLocation getTextureLocation(TrainCarEntity entity) { return WHITE; }

    @Override
    public boolean shouldRender(TrainCarEntity entity, Frustum frustum, double x, double y, double z) {
        return frustum.isVisible(entity.getBoundingBox().inflate(4));
    }

    @Override
    public void render(TrainCarEntity car, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        boolean mid = car.role() == 1;
        var minecraft = Minecraft.getInstance();
        boolean seeRider = minecraft.player != null && minecraft.player.getVehicle() == car
                && !minecraft.options.getCameraType().isFirstPerson();
        int shellLight = LightTexture.pack(Math.max(LightTexture.block(light), 6), Math.max(LightTexture.sky(light), 13));   // the viaduct canopy must not black the train out
        int cabinLight = LightTexture.pack(Math.max(LightTexture.block(light), 13), Math.max(LightTexture.sky(light), 13));
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        if (seeRider) {
            // Cut away only the side between the camera and rider; the far shell and
            // the rider's own seat remain solid to preserve the sense of a vehicle.
            double angle = Math.toRadians(-yaw), cos = Math.cos(angle), sin = Math.sin(angle);
            double carX = net.minecraft.util.Mth.lerp(partial, car.xo, car.getX());
            double carZ = net.minecraft.util.Mth.lerp(partial, car.zo, car.getZ());
            var camera = minecraft.gameRenderer.getMainCamera().getPosition();
            double cameraLocal = (camera.x - carX) * cos - (camera.z - carZ) * sin;
            double cameraAlong = (camera.x - carX) * sin + (camera.z - carZ) * cos;
            double riderX = net.minecraft.util.Mth.lerp(partial, minecraft.player.xo, minecraft.player.getX());
            double riderZ = net.minecraft.util.Mth.lerp(partial, minecraft.player.zo, minecraft.player.getZ());
            double riderLocal = (riderX - carX) * cos - (riderZ - carZ) * sin;
            double riderAlong = (riderX - carX) * sin + (riderZ - carZ) * cos;
            int side = cameraLocal >= riderLocal ? 1 : -1;
            double across = Math.abs(cameraLocal - riderLocal), along = Math.abs(cameraAlong - riderAlong);
            double broadside = net.minecraft.util.Mth.clamp(1.5 * (across - along) / Math.max(.001, across + along), 0, 1);
            var shell = TrainMesh.part(mid ? TrainMesh.MID_SHELL : TrainMesh.HEAD_SHELL);
            var cabin = TrainMesh.part(mid ? TrainMesh.MID_INTERIOR : TrainMesh.HEAD_INTERIOR);
            var translucent = buffers.getBuffer(Cutaway.TYPE);
            // At oblique/lengthwise angles, other rows can sit in front of the player;
            // fade the far side smoothly as well rather than making it abruptly disappear.
            drawMasked(ps, translucent, shell, shellLight, (int) Math.round(105 + 150 * broadside), side, riderLocal, false);
            drawMasked(ps, translucent, cabin, cabinLight, (int) Math.round(135 + 120 * broadside), side, riderLocal, false);
            drawMasked(ps, translucent, shell, shellLight, 80, side, riderLocal, true);
            drawMasked(ps, translucent, cabin, cabinLight, 100, side, riderLocal, true);
        } else {
            draw(ps, buffers.getBuffer(RenderType.entityCutout(WHITE)), TrainMesh.part(mid ? TrainMesh.MID_SHELL : TrainMesh.HEAD_SHELL), shellLight, 255);
            draw(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), TrainMesh.part(mid ? TrainMesh.MID_INTERIOR : TrainMesh.HEAD_INTERIOR), cabinLight, 255);
        }
        doors(ps, buffers.getBuffer(seeRider ? Cutaway.TYPE : RenderType.entityCutoutNoCull(WHITE)),
                car.doorOpen(partial), shellLight, seeRider ? 105 : 255);
        signal(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), car, partial);
        ps.popPose();
        super.render(car, yaw, partial, ps, buffers, light);
    }

    /** A translucent local cutaway must not write depth over the player's own model. */
    private static final class Cutaway extends RenderType {
        private Cutaway(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                        boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }

        private static final RenderType TYPE = create("academy_train_cutaway", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, true, true, CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                        .setTextureState(new TextureStateShard(WHITE, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static void signal(PoseStack ps, VertexConsumer out, TrainCarEntity car, float partial) {
        int color = switch (car.servicePhase()) {
            case APPROACHING, ARRIVED -> 0x7CCAFF;
            case BOARDING -> 0x4DFFC0;
            case CLOSING -> 0xFFAD49;
            default -> 0;
        };
        if (color == 0) return;
        float pulse = 0.8f + 0.2f * (float) Math.sin((car.tickCount + partial) * 0.18f);
        var pose = ps.last();
        for (int side : new int[]{-1, 1}) {
            float x = 1.74f * side;
            lampVertex(pose, out, x, 2.59f, -3.2f, side, color, pulse);
            lampVertex(pose, out, x, 2.59f, -1.8f, side, color, pulse);
            lampVertex(pose, out, x, 2.72f, -1.8f, side, color, pulse);
            lampVertex(pose, out, x, 2.72f, -3.2f, side, color, pulse);
        }
    }

    /** Visual sliding panels move on the server-synchronised boarding phase. */
    private static void doors(PoseStack ps, VertexConsumer out, float open, int light, int alpha) {
        var pose = ps.last();
        float shift = 0.72f * net.minecraft.util.Mth.clamp(open, 0, 1);
        for (int side : new int[]{-1, 1}) {
            float x = side * 1.72f;
            doorLeaf(pose, out, side, x, 0.8f - shift, 1.5f - shift, light, alpha);
            doorLeaf(pose, out, side, x, 1.5f + shift, 2.2f + shift, light, alpha);
        }
    }

    private static void doorLeaf(PoseStack.Pose pose, VertexConsumer out, int side, float x,
                                 float z0, float z1, int light, int alpha) {
        int frame = 0xDFE7E9, window = 0x456A7B;
        doorQuad(pose, out, side, x, 1.04f, 1.65f, z0, z1, frame, light, alpha);
        doorQuad(pose, out, side, x, 2.55f, 2.95f, z0, z1, frame, light, alpha);
        doorQuad(pose, out, side, x, 1.65f, 2.55f, z0, z0 + .08f, frame, light, alpha);
        doorQuad(pose, out, side, x, 1.65f, 2.55f, z1 - .08f, z1, frame, light, alpha);
        doorQuad(pose, out, side, x, 1.65f, 2.55f, z0 + .08f, z1 - .08f, window, light, alpha);
    }

    private static void doorQuad(PoseStack.Pose pose, VertexConsumer out, int side, float x,
                                 float y0, float y1, float z0, float z1, int color, int light, int alpha) {
        doorVertex(pose, out, side, x, y0, z0, color, light, alpha);
        doorVertex(pose, out, side, x, y0, z1, color, light, alpha);
        doorVertex(pose, out, side, x, y1, z1, color, light, alpha);
        doorVertex(pose, out, side, x, y1, z0, color, light, alpha);
    }

    private static void doorVertex(PoseStack.Pose pose, VertexConsumer out, int side, float x,
                                   float y, float z, int color, int light, int alpha) {
        out.addVertex(pose, x, y, z).setColor(color >> 16 & 255, color >> 8 & 255, color & 255, alpha)
                .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, side, 0, 0);
    }

    private static void lampVertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float z,
                                   int side, int color, float pulse) {
        out.addVertex(pose, x, y, z)
                .setColor((int) ((color >> 16 & 255) * pulse), (int) ((color >> 8 & 255) * pulse),
                        (int) ((color & 255) * pulse), 255)
                .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, side, 0, 0);
    }

    private static void draw(PoseStack ps, VertexConsumer out, TrainMesh.Part part, int light, int alpha) {
        drawMasked(ps, out, part, light, alpha, 0, 0, false);
    }

    private static void drawMasked(PoseStack ps, VertexConsumer out, TrainMesh.Part part, int light, int alpha,
                                   int side, double riderLocal, boolean fadedPass) {
        PoseStack.Pose pose = ps.last();
        float[] v = part.v();
        int[] argb = part.argb();
        for (int t = 0; t < part.triangles(); t++) {
            if (side != 0) {
                int b = t * 18;
                double centroidX = (v[b] + v[b + 6] + v[b + 12]) / 3.0;
                boolean faded = side * (centroidX - riderLocal) > .25;
                if (faded != fadedPass) continue;
            }
            for (int k = 0; k < 4; k++) {                                       // triangles go out as degenerate quads
                int i = t * 3 + Math.min(k, 2);
                int c = argb[i], b = i * 6;
                int lt = (c >>> 24) == 254 ? LightTexture.FULL_BRIGHT : light;
                out.addVertex(pose, v[b], v[b + 1], v[b + 2])
                        .setColor((c >> 16) & 255, (c >> 8) & 255, c & 255, alpha)
                        .setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lt)
                        .setNormal(pose, v[b + 3], v[b + 4], v[b + 5]);
            }
        }
    }
}
