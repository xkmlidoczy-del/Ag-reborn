/* AcademyCraft 1.0.7 RenderPhaseGen/GuiPhaseGen/TechUI source semantics. GPLv3; see NOTICE. */
package cn.academy.port.client;

/** Pure source visual policy: the machine has five tank textures and no time/facing transform. */
public final class ClassicPhaseGeneratorVisualRules {
    public static final double PIVOT_X = .5, PIVOT_Y = 0, PIVOT_Z = .5;
    public static final int ENERGY_COLOR = 0xff25c4ff, LIQUID_COLOR = 0xffb983fb;
    public static final int TEXTURE_COUNT = 5;
    public static final int INVENTORY_WIDTH = 176, INVENTORY_HEIGHT = 187;
    public static final double INFO_X = 179, INFO_Y = 5, INFO_WIDTH = 100, INFO_HEIGHT = 88;
    public static final double HISTOGRAM_SCALE = .4, HISTOGRAM_Y = -20;
    public static final double BAR_WIDTH = 16 * HISTOGRAM_SCALE, BAR_HEIGHT = 120 * HISTOGRAM_SCALE;
    public static final double BAR_BOTTOM = HISTOGRAM_Y + (78 + 120) * HISTOGRAM_SCALE;
    private ClassicPhaseGeneratorVisualRules() {}

    public static int textureIndex(int liquid, int tankSize) {
        if (tankSize <= 0) return 0;
        // Original RenderPhaseGen: clampi(0, 4, (int)Math.round(4.0 * amount / capacity)).
        return Math.max(0, Math.min(4, (int)Math.round(4.0 * liquid / tankSize)));
    }

    public static double barX(int index) {
        if (index < 0 || index > 1) throw new IllegalArgumentException("histogram index must be 0 or 1");
        return (56 + index * 40) * HISTOGRAM_SCALE;
    }

    public static ClassicDeveloperObj.Bounds worldBounds(ClassicDeveloperObj.Bounds model) {
        return new ClassicDeveloperObj.Bounds(model.minX() + PIVOT_X, model.minY(), model.minZ() + PIVOT_Z,
                model.maxX() + PIVOT_X, model.maxY(), model.maxZ() + PIVOT_Z);
    }
}
