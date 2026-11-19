package cn.aca.infinity.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

public final class StarDialDraw {
    private StarDialDraw() {}

    public static int withAlpha(int argb, float opacity) {
        int baseA = (argb >>> 24) & 0xff;
        if (baseA == 0) baseA = 255;
        int outA = Math.clamp(Math.round(baseA * opacity), 0, 255);
        return (outA << 24) | (argb & 0xffffff);
    }

    /**
     * Emits a guaranteed front-facing GUI quad in Minecraft's flipped-Y orthographic projection.
     * Automatically normalizes vertex winding so OpenGL backface culling never hides angled/mirrored shapes.
     */
    public static void quad(VertexConsumer vc, Matrix4f mat,
                            float x0, float y0, int c0,
                            float x1, float y1, int c1,
                            float x2, float y2, int c2,
                            float x3, float y3, int c3) {
        float cross = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
        if (cross <= 0f) {
            vc.addVertex(mat, x0, y0, 0).setColor(c0);
            vc.addVertex(mat, x1, y1, 0).setColor(c1);
            vc.addVertex(mat, x2, y2, 0).setColor(c2);
            vc.addVertex(mat, x3, y3, 0).setColor(c3);
        } else {
            vc.addVertex(mat, x0, y0, 0).setColor(c0);
            vc.addVertex(mat, x3, y3, 0).setColor(c3);
            vc.addVertex(mat, x2, y2, 0).setColor(c2);
            vc.addVertex(mat, x1, y1, 0).setColor(c1);
        }
    }

    public static void flatQuad(VertexConsumer vc, Matrix4f mat,
                                float x0, float y0, float x1, float y1,
                                float x2, float y2, float x3, float y3, int color) {
        quad(vc, mat, x0, y0, color, x1, y1, color, x2, y2, color, x3, y3, color);
    }

    public static void ring(GuiGraphics g, float cx, float cy, float innerR, float outerR, float fromRad, float toRad, int color) {
        if (Math.abs(toRad - fromRad) < 1e-4f) return;
        float aStart = Math.min(fromRad, toRad);
        float aEnd = Math.max(fromRad, toRad);
        int segs = Math.max(12, (int) ((aEnd - aStart) * Math.max(16f, outerR) / 2.8f));
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f mat = g.pose().last().pose();
        for (int i = 0; i < segs; i++) {
            float a0 = aStart + (aEnd - aStart) * i / segs;
            float a1 = aStart + (aEnd - aStart) * (i + 1) / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            flatQuad(vc, mat,
                    cx + innerR * c0, cy + innerR * s0,
                    cx + innerR * c1, cy + innerR * s1,
                    cx + outerR * c1, cy + outerR * s1,
                    cx + outerR * c0, cy + outerR * s0,
                    color);
        }
    }

    public static void segmentedArc(GuiGraphics g, float cx, float cy, float rIn, float rOut,
                                    float fromRad, float toRad, int segments, float ratio,
                                    int litColor, int tipColor, int dimColor) {
        if (segments <= 0) return;
        float span = toRad - fromRad;
        float step = span / segments;
        float gap = Math.min(Math.abs(step) * 0.20f, 0.028f) * Math.signum(step);
        float filled = Mth.clamp(ratio, 0f, 1f) * segments;
        for (int i = 0; i < segments; i++) {
            float s0 = fromRad + i * step + gap * 0.5f;
            float s1 = fromRad + (i + 1) * step - gap * 0.5f;
            int col;
            if (i + 1 <= filled) {
                col = (i + 1 >= filled - 0.8f) ? tipColor : litColor;
            } else if (i < filled) {
                col = tipColor;
            } else {
                col = dimColor;
            }
            ring(g, cx, cy, rIn, rOut, s0, s1, col);
        }
    }

    public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.01f) return;
        float nx = dy * thickness / (2f * len);
        float ny = -dx * thickness / (2f * len);
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f mat = g.pose().last().pose();
        flatQuad(vc, mat,
                x1 - nx, y1 - ny,
                x2 - nx, y2 - ny,
                x2 + nx, y2 + ny,
                x1 + nx, y1 + ny,
                color);
    }

    public static void radialTick(GuiGraphics g, float cx, float cy, float r0, float r1, float angleRad, float width, int color) {
        float c = (float) Math.cos(angleRad), s = (float) Math.sin(angleRad);
        line(g, cx + c * r0, cy + s * r0, cx + c * r1, cy + s * r1, width, color);
    }

    /**
     * 8-Sided Chamfered Sovereign Obsidian-Gold Panel:
     * Replaces clunky rectangular boxes with beveled Chitauri-Asgardian glass plates, double hairline filigree & corner accents.
     */
    public static void chamferPanel(GuiGraphics g, float x, float y, float w, float h, float cut,
                                    int topBg, int botBg, int borderCol, int accentCol) {
        float c = Math.min(cut, Math.min(w, h) * 0.35f);
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f mat = g.pose().last().pose();

        // Top chamfer trapezoid
        quad(vc, mat,
                x + c, y, topBg,
                x, y + c, topBg,
                x + w, y + c, topBg,
                x + w - c, y, topBg);
        // Center gradient body
        quad(vc, mat,
                x, y + c, topBg,
                x, y + h - c, botBg,
                x + w, y + h - c, botBg,
                x + w, y + c, topBg);
        // Bottom chamfer trapezoid
        quad(vc, mat,
                x, y + h - c, botBg,
                x + c, y + h, botBg,
                x + w - c, y + h, botBg,
                x + w, y + h - c, botBg);

        // 8-edge crisp filigree border
        float bw = 1.05f;
        line(g, x + c, y, x + w - c, y, bw, borderCol);
        line(g, x + w - c, y, x + w, y + c, bw, accentCol);
        line(g, x + w, y + c, x + w, y + h - c, bw, borderCol);
        line(g, x + w, y + h - c, x + w - c, y + h, bw, accentCol);
        line(g, x + w - c, y + h, x + c, y + h, bw, borderCol);
        line(g, x + c, y + h, x, y + h - c, bw, accentCol);
        line(g, x, y + h - c, x, y + c, bw, borderCol);
        line(g, x, y + c, x + c, y, bw, accentCol);

        // Luminous top center crest bar
        float crestHalf = Math.min(w * 0.28f, 52f);
        float midX = x + w * 0.5f;
        line(g, midX - crestHalf, y + 0.6f, midX + crestHalf, y + 0.6f, 1.8f, accentCol);
    }

    /** Angled parallelogram armor / energy cells with a bright leading charge cap. */
    public static void skewedCells(GuiGraphics g, float x, float y, float w, float h,
                                   int cells, float ratio, float skew,
                                   int litColor, int tipColor, int dimColor) {
        if (cells <= 0) return;
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f mat = g.pose().last().pose();
        float gap = 1.6f;
        float cellW = (w - gap * (cells - 1)) / cells;
        float filled = Mth.clamp(ratio, 0f, 1f) * cells;
        for (int i = 0; i < cells; i++) {
            float cx0 = x + i * (cellW + gap);
            float cx1 = cx0 + cellW;
            int col;
            if (i + 1 <= filled) {
                col = (i + 1 >= filled - 0.9f) ? tipColor : litColor;
            } else if (i < filled) {
                col = tipColor;
            } else {
                col = dimColor;
            }
            flatQuad(vc, mat,
                    cx0 + skew, y,
                    cx0, y + h,
                    cx1, y + h,
                    cx1 + skew, y,
                    col);
        }
    }

    /** Crisp diamond emblem / missile warhead indicator. */
    public static void diamond(GuiGraphics g, float cx, float cy, float rx, float ry, int fillCol, int rimCol) {
        VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
        Matrix4f mat = g.pose().last().pose();
        flatQuad(vc, mat,
                cx, cy - ry,
                cx - rx, cy,
                cx, cy + ry,
                cx + rx, cy,
                fillCol);
        if ((rimCol >>> 24) > 0) {
            line(g, cx, cy - ry, cx + rx, cy, 1.0f, rimCol);
            line(g, cx + rx, cy, cx, cy + ry, 1.0f, rimCol);
            line(g, cx, cy + ry, cx - rx, cy, 1.0f, rimCol);
            line(g, cx - rx, cy, cx, cy - ry, 1.0f, rimCol);
        }
    }
}
