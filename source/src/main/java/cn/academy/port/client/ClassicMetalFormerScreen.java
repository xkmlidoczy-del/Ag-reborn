/* AcademyCraft 1.0.7 GuiMetalFormer/page_metalformer/TechUI. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.former.ClassicMetalFormerMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Original page art with real native slots, server-confirmed mode controls, and the shared wireless page. */
public final class ClassicMetalFormerScreen extends AbstractContainerScreen<ClassicMetalFormerMenu> {
    private static final ClassicHudTimeline.Rgba WHITE = rgba(1, 1, 1, 1);
    private final ClassicHudFont sourceFont = new ClassicHudFont();
    private long openedAt, lastInfoTime;
    private double infoHeight;

    public ClassicMetalFormerScreen(ClassicMetalFormerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 187;
    }

    @Override protected void init() {
        super.init();
        leftPos = (width - 304) / 2;
        topPos = (height - imageHeight) / 2;
        sourceFont.use(ClassicHudConfig.SPEC.isLoaded() ? ClassicHudConfig.FONT.get() : "Microsoft YaHei");
        openedAt = lastInfoTime = ClassicWirelessClock.millis();
        infoHeight = 0;
        addRenderableWidget(new SourceButton(leftPos + 60, topPos + 8, 16, 16,
                Component.translatable("screen.academy.metal_former.previous"),
                "button/button_arrowlefta", 60, 8.5, false, () -> cycle(0)));
        addRenderableWidget(new SourceButton(leftPos + 100, topPos + 8, 16, 16,
                Component.translatable("screen.academy.metal_former.next"),
                "button/button_arrowrighta", 100, 8.5, false, () -> cycle(1)));
        addRenderableWidget(new SourceButton(leftPos - 20, topPos, 17, 17,
                Component.translatable("screen.academy.wireless.return"),
                "icons/icon_inv", -20, 0, true, () -> {}));
        addRenderableWidget(new SourceButton(leftPos - 20, topPos + 22, 17, 17,
                Component.translatable("screen.academy.wireless.title"),
                "icons/icon_wireless", -20, 22, false,
                () -> ClassicMetalFormerClient.openWireless(menu.containerId, menu.sourcePos())));
    }

    private void cycle(int button) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ClassicHudCanvas canvas = new ClassicHudCanvas(graphics);
        canvas.begin();
        try {
            canvas.rect(tex("parent/parent_background"), leftPos, topPos, 176, 187, WHITE);
            double breathe = ClassicWirelessVisualRules.breatheAlpha(ClassicWirelessClock.millis());
            canvas.rect(tex("ui/ui_inventory"), leftPos, topPos, 176, 187, rgba(1, 1, 1, breathe));
            canvas.rect(tex("ui/ui_metalformer"), leftPos, topPos, 176, 187, rgba(1, 1, 1, breathe));
            double progress = Math.max(0, Math.min(1, menu.progress()));
            // ProgressBar.RIGHT crops both geometry and UVs; it does not stretch the full source image.
            if (progress > 0) canvas.rect(tex("progress/progress_metalformer"),
                    leftPos + 59.5, topPos + 46.5, 57 * progress, 15,
                    0, 0, progress, 1, WHITE, null, null);
            canvas.rect(tex("icons/icon_former_" + menu.mode().name().toLowerCase(Locale.ROOT)),
                    leftPos + 76, topPos + 4.5, 24, 24, WHITE);
            drawInfo(canvas);
        } finally {
            canvas.end();
        }
    }

    private void drawInfo(ClassicHudCanvas canvas) {
        double x = leftPos + 179, y = topPos + 5;
        long now = ClassicWirelessClock.millis();
        double step = Math.min(500, Math.max(0, now - lastInfoTime)) * .5;
        infoHeight += Math.signum(80 - infoHeight) * Math.min(step, Math.abs(80 - infoHeight));
        lastInfoTime = now;
        double alpha = Math.max(0, Math.min(1, (now - openedAt - 300) / 300d));
        // TechUI.BlendQuad: nine source slices, black .5 tint and original decorative lines.
        double[] xs = {x - 4, x, x + 100, x + 104};
        double[] ys = {y - 4, y, y + infoHeight, y + infoHeight + 4};
        for (int col = 0; col < 3; col++) for (int row = 0; row < 3; row++)
            canvas.rect(tex("blend_quad"), xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                    col / 3d, row / 3d, (col + 1) / 3d, (row + 1) / 3d,
                    rgba(0, 0, 0, .5), null, null);
        canvas.rect(tex("line"), x - 3.2, y - 8.6, 106.4, 12, WHITE);
        canvas.rect(tex("line"), x - 3.2, y + infoHeight - 2, 106.4, 8, WHITE);
        canvas.rect(tex("histogram"), x, y - 20, 84, 84, rgba(1, 1, 1, alpha));
        double fraction = ClassicWirelessVisualRules.histogramFraction(menu.energy(), 3000);
        canvas.fill(x + 22.4, y + 59.2 - 48 * fraction, 6.4, 48 * fraction,
                rgba(37 / 255d, 196 / 255d, 1, alpha), false);
        // histProperty's row starts at x6; its key has another x4, marker x-3, and value x40.
        canvas.fill(x + 3, y + 65.5, 6, 6, rgba(37 / 255d, 196 / 255d, 1, alpha), false);
        sourceFont.draw(canvas, Component.translatable("ac.gui.common.hist.energy").getString(),
                x + 10, y + 64, 8, rgba(1, 1, 1, alpha), false, false);
        sourceFont.draw(canvas, String.format(Locale.ROOT, "%.0f IF", menu.energy()),
                x + 46, y + 64, 8, rgba(1, 1, 1, alpha), false, false);
    }

    private final class SourceButton extends Button {
        private final ResourceLocation texture;
        private final double sourceX, sourceY;
        private final boolean selected;

        SourceButton(int x, int y, int width, int height, Component label, String texture,
                     double sourceX, double sourceY, boolean selected, Runnable callback) {
            super(x, y, width, height, label, button -> callback.run(), DEFAULT_NARRATION);
            this.texture = tex(texture);
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.selected = selected;
            setTooltip(Tooltip.create(label));
        }

        @Override public void renderWidget(GuiGraphics graphics, int mx, int my, float partialTick) {
            ClassicHudCanvas canvas = new ClassicHudCanvas(graphics);
            canvas.begin();
            try {
                boolean tab = sourceX < 0;
                double size = tab ? 16.8 : 16;
                double tint = tab && !selected ? .8 : 1;
                double alpha = tab && !selected && !isHoveredOrFocused() ? .8 : 1;
                canvas.rect(texture, leftPos + sourceX, topPos + sourceY, size, size,
                        rgba(tint, tint, tint, alpha));
            } finally {
                canvas.end();
            }
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 76 && mouseX < leftPos + 100
                && mouseY >= topPos + 4.5 && mouseY < topPos + 28.5) {
            // GuiMetalFormer mode hover uses its uppercase enum and source 10px centered textbox.
            ClassicHudCanvas canvas = new ClassicHudCanvas(graphics);
            canvas.begin();
            try {
                String label = menu.mode().name();
                double textWidth = sourceFont.width(label, 10, false);
                double x = leftPos + 76 + 6 - textWidth / 2, y = topPos + 4.5 - 10;
                canvas.fill(x, y, textWidth + 12, 14, rgba(0, 0, 0, .5), false);
                sourceFont.draw(canvas, label, x + 5, y + 2, 10, rgba(1, 1, 1, 170 / 255d), false, false);
            } finally {
                canvas.end();
            }
        }
    }

    @Override protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return mouseX < leftPos - 23 || mouseX >= leftPos + 286
                || mouseY < topPos - 25 || mouseY >= topPos + 195;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Consume sidebar clicks so a carried stack is never dropped behind the histogram.
        if (mouseX >= leftPos + 176 && mouseX < leftPos + 286
                && mouseY >= topPos - 10 && mouseY < topPos + 195) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void removed() {
        super.removed();
        sourceFont.release();
    }

    private static ResourceLocation tex(String path) { return ClassicHudCanvas.texture("guis/" + path); }
    private static ClassicHudTimeline.Rgba rgba(double r, double g, double b, double a) {
        return new ClassicHudTimeline.Rgba(r, g, b, a);
    }
}
