/* AcademyCraft 1.0.7 GuiPhaseGen/page_inv/TechUI. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.phasegen.ClassicPhaseGeneratorMenu;
import cn.academy.port.phasegen.ClassicPhaseGeneratorRules;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Original phase inventory art with native slot actions and the server-authoritative wireless tab. */
public final class ClassicPhaseGeneratorScreen extends AbstractContainerScreen<ClassicPhaseGeneratorMenu> {
    private static final ClassicHudTimeline.Rgba WHITE = rgba(1, 1, 1, 1);
    private final ClassicHudFont sourceFont = new ClassicHudFont();
    private long openedAt, lastInfoTime;
    private double infoHeight;

    public ClassicPhaseGeneratorScreen(ClassicPhaseGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ClassicPhaseGeneratorVisualRules.INVENTORY_WIDTH;
        imageHeight = ClassicPhaseGeneratorVisualRules.INVENTORY_HEIGHT;
    }

    @Override protected void init() {
        super.init();
        leftPos = (width - 304) / 2;
        topPos = (height - imageHeight) / 2;
        sourceFont.use(ClassicHudConfig.SPEC.isLoaded() ? ClassicHudConfig.FONT.get() : "Microsoft YaHei");
        openedAt = lastInfoTime = ClassicWirelessClock.millis();
        infoHeight = 0;
        addRenderableWidget(new InventoryTab(leftPos - 20, topPos));
        addRenderableWidget(new ClassicWirelessTabButton(leftPos - 20, topPos + 22,
                () -> ClassicWirelessClient.openPhaseGenerator(menu.containerId, menu.sourcePos())));
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ClassicHudCanvas canvas = new ClassicHudCanvas(graphics);
        canvas.begin();
        try {
            canvas.rect(tex("parent/parent_background"), leftPos, topPos, 176, 187, WHITE);
            double breathe = ClassicWirelessVisualRules.breatheAlpha(ClassicWirelessClock.millis());
            canvas.rect(tex("ui/ui_inventory"), leftPos, topPos, 176, 187, rgba(1, 1, 1, breathe));
            canvas.rect(tex("ui/ui_phasegen"), leftPos, topPos, 176, 187, rgba(1, 1, 1, breathe));
            drawInfo(canvas);
        } finally {
            canvas.end();
        }
    }

    private void drawInfo(ClassicHudCanvas canvas) {
        double x = leftPos + ClassicPhaseGeneratorVisualRules.INFO_X;
        double y = topPos + ClassicPhaseGeneratorVisualRules.INFO_Y;
        long now = ClassicWirelessClock.millis();
        double step = Math.min(500, Math.max(0, now - lastInfoTime)) * .5;
        double target = ClassicPhaseGeneratorVisualRules.INFO_HEIGHT;
        infoHeight += Math.signum(target - infoHeight) * Math.min(step, Math.abs(target - infoHeight));
        lastInfoTime = now;
        double alpha = Math.max(0, Math.min(1, (now - openedAt - 300) / 300d));
        double w = ClassicPhaseGeneratorVisualRules.INFO_WIDTH;
        double[] xs = {x - 4, x, x + w, x + w + 4};
        double[] ys = {y - 4, y, y + infoHeight, y + infoHeight + 4};
        for (int col = 0; col < 3; col++) for (int row = 0; row < 3; row++)
            canvas.rect(tex("blend_quad"), xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                    col / 3d, row / 3d, (col + 1) / 3d, (row + 1) / 3d,
                    rgba(0, 0, 0, .5), null, null);
        canvas.rect(tex("line"), x - 3.2, y - 8.6, w + 6.4, 12, WHITE);
        canvas.rect(tex("line"), x - 3.2, y + infoHeight - 2, w + 6.4, 8, WHITE);
        canvas.rect(tex("histogram"), x, y + ClassicPhaseGeneratorVisualRules.HISTOGRAM_Y,
                84, 84, rgba(1, 1, 1, alpha));

        double energyFraction = ClassicWirelessVisualRules.histogramFraction(menu.energy(), ClassicPhaseGeneratorRules.CAPACITY);
        double liquidFraction = ClassicWirelessVisualRules.histogramFraction(menu.liquid(), ClassicPhaseGeneratorRules.TANK_SIZE);
        drawBar(canvas, x, y, 0, energyFraction, ClassicPhaseGeneratorVisualRules.ENERGY_COLOR, alpha);
        drawBar(canvas, x, y, 1, liquidFraction, ClassicPhaseGeneratorVisualRules.LIQUID_COLOR, alpha);
        histProperty(canvas, x, y + 64, Component.translatable("ac.gui.common.hist.energy").getString(),
                String.format(Locale.ROOT, "%.0f IF", menu.energy()), ClassicPhaseGeneratorVisualRules.ENERGY_COLOR, alpha);
        // GuiPhaseGen's second HistElement id is literally "IF" although its value is a liquid in mB.
        histProperty(canvas, x, y + 72, "IF", menu.liquid() + " mB", ClassicPhaseGeneratorVisualRules.LIQUID_COLOR, alpha);
    }

    private static void drawBar(ClassicHudCanvas canvas, double x, double y, int index,
                                double fraction, int color, double alpha) {
        double height = ClassicPhaseGeneratorVisualRules.BAR_HEIGHT * fraction;
        canvas.fill(x + ClassicPhaseGeneratorVisualRules.barX(index),
                y + ClassicPhaseGeneratorVisualRules.BAR_BOTTOM - height,
                ClassicPhaseGeneratorVisualRules.BAR_WIDTH, height, color(color, alpha), false);
    }

    private void histProperty(ClassicHudCanvas canvas, double x, double y, String label,
                              String value, int color, double alpha) {
        canvas.fill(x + 3, y + 1.5, 6, 6, color(color, alpha), false);
        sourceFont.draw(canvas, label, x + 10, y, 8, rgba(1, 1, 1, alpha), false, false);
        sourceFont.draw(canvas, value, x + 46, y, 8, rgba(1, 1, 1, alpha), false, false);
    }

    private final class InventoryTab extends Button {
        InventoryTab(int x, int y) {
            super(x, y, 18, 18, Component.translatable("screen.academy.wireless.return"), button -> {}, DEFAULT_NARRATION);
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            ClassicHudCanvas canvas = new ClassicHudCanvas(graphics);
            canvas.begin();
            try { canvas.rect(tex("icons/icon_inv"), getX(), getY(), 16.8, 16.8, WHITE); }
            finally { canvas.end(); }
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return mouseX < leftPos - 23 || mouseX >= leftPos + 286
                || mouseY < topPos - 25 || mouseY >= topPos + 195;
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // An info-sidebar click must not become a native outside-inventory drop of the carried stack.
        if (mouseX >= leftPos + 176 && mouseX < leftPos + 286
                && mouseY >= topPos - 25 && mouseY < topPos + 195) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public void removed() { super.removed(); sourceFont.release(); }

    private static net.minecraft.resources.ResourceLocation tex(String path) { return ClassicHudCanvas.texture("guis/" + path); }
    private static ClassicHudTimeline.Rgba rgba(double r, double g, double b, double a) {
        return new ClassicHudTimeline.Rgba(r, g, b, a);
    }
    private static ClassicHudTimeline.Rgba color(int color, double alpha) {
        return rgba((color >> 16 & 255) / 255d, (color >> 8 & 255) / 255d, (color & 255) / 255d, alpha);
    }
}
