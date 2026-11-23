package cn.academy.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class TerminalSkin {
    public static final ClassicTerminalDrawing FONT = new ClassicTerminalDrawing(true);
    public static final ResourceLocation FRAME = sprite("frame"), SELECTED = sprite("selected"),
            HEALTH = sprite("health"), SHIELD = sprite("shield"), BROKEN = sprite("broken"),
            TRACK = sprite("track"), FILL = sprite("fill"), ARROW = sprite("arrow");

    private static String readyLanguage, readyFace;

    /** Prepare the original terminal face while the title or the connection screen is visible.
     * An unawakened player can acquire the DNA HUD on the very first transformation. */
    public static void prepareReadout() {
        var client = Minecraft.getInstance();
        String language = client.getLanguageManager().getSelected();
        String face = cn.academy.port.ClassicHudConfig.SPEC.isLoaded() ? cn.academy.port.ClassicHudConfig.FONT.get() : "Microsoft YaHei";
        if (language.equals(readyLanguage) && face.equals(readyFace)) return;
        StringBuilder alphabet = new StringBuilder("0123456789., /·−-+%XZYNDABKMT万亿萬兆億京 ");
        for (String key : new String[]{"health", "base_shield", "dna_short", "shield_broken", "super_armor", "shield_standby"}) {
            alphabet.append(net.minecraft.network.chat.Component.translatable("hud.academy.highpower." + key).getString());
        }
        FONT.preload(alphabet.toString());
        readyLanguage = language;
        readyFace = face;
    }

    public static void releaseReadout() {
        if (readyLanguage != null) FONT.release();
        readyLanguage = readyFace = null;
    }

    private TerminalSkin() {}

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath("academy", "terminal/" + name);
    }

    public static void draw(GuiGraphics graphics, ResourceLocation id, int x, int y, int width, int height, int argb) {
        if (width <= 0 || height <= 0 || (argb >>> 24) == 0) return;
        var sprites = Minecraft.getInstance().getGuiSprites();
        var sprite = sprites.getSprite(id);
        graphics.setColor(((argb >> 16) & 255) / 255f, ((argb >> 8) & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
        if (sprites.getSpriteScaling(sprite) instanceof GuiSpriteScaling.NineSlice slice) {
            drawStretched(graphics.pose().last().pose(), sprite, slice, x, y, width, height);
        } else {
            graphics.blitSprite(id, x, y, width, height);
        }
        graphics.setColor(1, 1, 1, 1);
    }

    // The native 1.21.1 nine-slice tiles every few texels. Our artwork has continuous edges and a flat
    // interior, so the same atlas sprite can cover an entire panel with at most nine quads in one draw.
    private static void drawStretched(Matrix4f pose, TextureAtlasSprite sprite, GuiSpriteScaling.NineSlice slice,
                                      int x, int y, int width, int height) {
        var border = slice.border();
        int left = Math.min(border.left(), width / 2), right = Math.min(border.right(), width / 2);
        int top = Math.min(border.top(), height / 2), bottom = Math.min(border.bottom(), height / 2);
        int[] xs = {x, x + left, x + width - right, x + width};
        int[] ys = {y, y + top, y + height - bottom, y + height};
        float[] us = {sprite.getU0(), sprite.getU((float) left / slice.width()),
                sprite.getU(1f - (float) right / slice.width()), sprite.getU1()};
        float[] vs = {sprite.getV0(), sprite.getV((float) top / slice.height()),
                sprite.getV(1f - (float) bottom / slice.height()), sprite.getV1()};
        RenderSystem.setShaderTexture(0, sprite.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        var mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int row = 0; row < 3; row++) {
            if (ys[row] == ys[row + 1]) continue;
            for (int col = 0; col < 3; col++) {
                if (xs[col] == xs[col + 1]) continue;
                quad(mesh, pose, xs[col], ys[row], xs[col + 1], ys[row + 1], us[col], vs[row], us[col + 1], vs[row + 1]);
            }
        }
        BufferUploader.drawWithShader(mesh.buildOrThrow());
    }

    private static void quad(BufferBuilder mesh, Matrix4f pose, int left, int top, int right, int bottom,
                             float u0, float v0, float u1, float v1) {
        mesh.addVertex(pose, left, top, 0).setUv(u0, v0);
        mesh.addVertex(pose, left, bottom, 0).setUv(u0, v1);
        mesh.addVertex(pose, right, bottom, 0).setUv(u1, v1);
        mesh.addVertex(pose, right, top, 0).setUv(u1, v0);
    }
}
