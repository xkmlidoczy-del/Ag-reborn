package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Kind;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

final class DialDraw {
    private DialDraw() {}

    static int glow(Kind kind) {
        return switch (kind) {
            case CLASSIC -> 0xffb0ef76;
            case CELESTIAL -> 0xffbcdfff;
            case CARNITRIX -> 0xffefb688;
        };
    }

    static int coreTint(Kind kind) {
        return switch (kind) {
            case CLASSIC -> 0xff46df62;
            case CELESTIAL -> 0xff7eb6ff;
            case CARNITRIX -> 0xffff584c;
        };
    }

    static int alpha(int color, float opacity) {
        return ((int) ((color >>> 24) * Math.clamp(opacity, 0, 1)) << 24) | (color & 0xffffff);
    }

    static void arc(GuiGraphics g, float x, float y, float r, float outer, float from, float to, int color) {
        gradientArc(g, x, y, r, outer, from, to, color, color);
    }

    static void gradientArc(GuiGraphics g, float x, float y, float r, float outer, float from, float to,
                            int innerColor, int outerColor) {
        int count = Math.max(3, (int) (Math.abs(to - from) * Math.max(outer, 6f) / 4.2f));
        var sink = g.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < count; i++) {
            float angle = from + (to - from) * i / count;
            float next = from + (to - from) * (i + 1) / count;
            float a = (float) Math.cos(angle), b = (float) Math.sin(angle);
            float c = (float) Math.cos(next), d = (float) Math.sin(next);
            vertex(g, sink, x + r * a, y + r * b, innerColor);
            vertex(g, sink, x + r * c, y + r * d, innerColor);
            vertex(g, sink, x + outer * c, y + outer * d, outerColor);
            vertex(g, sink, x + outer * a, y + outer * b, outerColor);
        }
    }

    static void edge(GuiGraphics g, float x, float y, float endX, float endY, float width, int color) {
        taperedBeam(g, x, y, width, endX, endY, width, color, color);
    }

    static void taperedBeam(GuiGraphics g, float x, float y, float startWidth,
                            float endX, float endY, float endWidth, int startColor, int endColor) {
        float dx = endX - x, dy = endY - y;
        float length = (float) Math.hypot(dx, dy);
        if (length < .01f) return;
        float nx = dy / (2f * length), ny = -dx / (2f * length);
        float h0 = nx * startWidth, v0 = ny * startWidth;
        float h1 = nx * endWidth, v1 = ny * endWidth;
        var sink = g.bufferSource().getBuffer(RenderType.gui());
        vertex(g, sink, x + h0, y + v0, startColor);
        vertex(g, sink, x - h0, y - v0, startColor);
        vertex(g, sink, endX - h1, endY - v1, endColor);
        vertex(g, sink, endX + h1, endY + v1, endColor);
    }

    static void diamond(GuiGraphics g, float x, float y, float rx, float ry, int color) {
        var sink = g.bufferSource().getBuffer(RenderType.gui());
        vertex(g, sink, x, y - ry, color);
        vertex(g, sink, x - rx, y, color);
        vertex(g, sink, x, y + ry, color);
        vertex(g, sink, x + rx, y, color);
    }

    private static void vertex(GuiGraphics g, VertexConsumer sink, float x, float y, int color) {
        sink.addVertex(g.pose().last().pose(), x, y, 0).setColor(color);
    }

    static void tick(GuiGraphics g, float x, float y, float inner, float outer, float angle, int color) {
        float a = (float) Math.cos(angle), b = (float) Math.sin(angle);
        edge(g, x + a * inner, y + b * inner, x + a * outer, y + b * outer, 1.25f, color);
    }
}
