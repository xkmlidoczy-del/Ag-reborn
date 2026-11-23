package cn.academy.port.terminal.fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = FishComputer.ID, value = Dist.CLIENT)
public final class FishClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FishComputer.TILE.get(), PortraitRenderer::new);
    }

    @SubscribeEvent
    public static void screens(RegisterMenuScreensEvent event) {
        event.register(FishComputer.MENU.get(), TerminalScreen::new);
    }

    private record Portrait(String key, int width, int height, ResourceLocation texture) {
        Portrait(String key, int width, int height) {
            this(key, width, height, ResourceLocation.fromNamespaceAndPath(FishComputer.ID, "textures/block/fish_computer/portraits/" + key + ".png"));
        }
    }

    private static final Portrait[] PORTRAITS = {
        new Portrait("welcome", 644, 1496), new Portrait("analysis", 685, 1500), new Portrait("thinking", 728, 1509)
    };

    private static Portrait portrait(int index) {
        return PORTRAITS[Math.max(0, Math.min(index, PORTRAITS.length - 1))];
    }

    private static final class PortraitRenderer implements BlockEntityRenderer<FishTile> {
        private final BlockEntityRendererProvider.Context context;

        PortraitRenderer(BlockEntityRendererProvider.Context context) {
            this.context = context;
        }

        @Override
        public AABB getRenderBoundingBox(FishTile tile) {
            var pos = tile.getBlockPos();
            return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+2, pos.getZ()+1);
        }

        @Override
        public void render(FishTile tile, float partialTick, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
            var state = tile.getBlockState();
            if (!FishMenu.isTerminal(state) || !state.getValue(FishBlock.SHOW)) return;
            Vec3 eye = context.getBlockEntityRenderDispatcher().camera.getPosition();
            var pos = tile.getBlockPos();
            double yaw = Math.atan2(eye.x - pos.getX() - .5, eye.z - pos.getZ() - .5);
            Portrait art = portrait(state.getValue(FishBlock.VARIANT));
            float height = Math.min(1.722F, .94F * art.height / art.width);
            float edge = height * art.width / (2 * art.height);
            poses.pushPose();
            poses.translate(.5, .252, .5);
            poses.mulPose(Axis.YP.rotation((float) yaw));
            var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(art.texture));
            var transform = poses.last();
            vertex(consumer, transform, -edge, height, 0, 0, overlay);
            vertex(consumer, transform, -edge, 0, 0, 1, overlay);
            vertex(consumer, transform, edge, 0, 1, 1, overlay);
            vertex(consumer, transform, edge, height, 1, 0, overlay);
            poses.popPose();
        }

        private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int overlay) {
            consumer.addVertex(pose, x, y, 0).setColor(0xffffffff).setUv(u, v)
                    .setOverlay(overlay).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
        }
    }

    private static final class TerminalScreen extends AbstractContainerScreen<FishMenu> {
        private static final int FRAME = 0xffa7c0bb;
        private static final int PANEL = 0xff1b2d35;
        private static final int TEXT = 0xffedf8f3;
        private Button projection;

        TerminalScreen(FishMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
            imageWidth = 228;
            imageHeight = 155;
        }

        @Override
        protected void init() {
            super.init();
            projection = addRenderableWidget(Button.builder(projectionLabel(), button -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FishMenu.TOGGLE_PROJECTION);
                }
            }).bounds(leftPos + 95, topPos + 106, 121, 19).build());
            addRenderableWidget(Button.builder(Component.literal("×"), button -> onClose())
                    .bounds(leftPos + 208, topPos + 3, 16, 14).build());
        }

        private Component projectionLabel() {
            return Component.translatable(menu.projecting() ? "menu.academy.fish_computer.hide" : "menu.academy.fish_computer.show");
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            projection.setMessage(projectionLabel());
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
            int x=leftPos, y=topPos;
            graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
            graphics.renderOutline(x, y, imageWidth, imageHeight, FRAME);
            graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 21, FRAME);
            graphics.fill(x + 11, y + 29, x + 83, y + 144, 0xff294451);
            Portrait art = portrait(menu.variant());
            int renderHeight = 107;
            int renderWidth = Math.round((float) renderHeight * art.width / art.height);
            graphics.blit(art.texture, x + 47 - renderWidth / 2, y + 33, renderWidth, renderHeight,
                    0, 0, art.width, art.height, art.width, art.height);
            graphics.drawString(font, Component.translatable("menu.academy.fish_computer.header"), x + 12, y + 7, 0xff17363d, false);
            graphics.drawString(font, title, x + 95, y + 42, TEXT, false);
            graphics.drawString(font, Component.translatable("menu.academy.fish_computer.variant",
                    Component.translatable("menu.academy.fish_computer." + art.key)), x + 95, y + 62, TEXT, false);
            graphics.drawString(font, Component.translatable("menu.academy.fish_computer.facing"), x + 95, y + 78, 0xffb8cfcc, false);
        }

        @Override
        protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        }
    }
}
