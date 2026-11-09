/* AcademyCraft 1.0.7 GuiWindGenBase/Main/page_inv/TechUI. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.wind.ClassicWindMenu;
import cn.academy.port.wind.ClassicWindRules;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Original wind inventory art with native slot actions and the server-authoritative wireless tab. */
public final class ClassicWindScreen extends AbstractContainerScreen<ClassicWindMenu> {
    private static final ClassicHudTimeline.Rgba WHITE = rgba(1, 1, 1, 1);
    private final ClassicHudFont sourceFont = new ClassicHudFont();
    private long openedAt, lastInfoTime;
    private double infoHeight;

    public ClassicWindScreen(ClassicWindMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ClassicWindVisualRules.INVENTORY_WIDTH;
        imageHeight = ClassicWindVisualRules.INVENTORY_HEIGHT;
    }

    @Override protected void init() {
        super.init();
        leftPos = ClassicWindVisualRules.slotX(width);
        topPos = ClassicWindVisualRules.slotY(height);
        sourceFont.use(ClassicHudConfig.SPEC.isLoaded() ? ClassicHudConfig.FONT.get() : "Microsoft YaHei");
        openedAt = lastInfoTime = ClassicWirelessClock.millis();
        infoHeight = 0;
        addRenderableWidget(new InventoryTab((int)Math.round(ClassicWindVisualRules.mainX(width)-20),(int)Math.round(ClassicWindVisualRules.inventoryY(height))));
        if(menu.base())addRenderableWidget(new ClassicWirelessTabButton((int)Math.round(ClassicWindVisualRules.mainX(width)-20),(int)Math.round(ClassicWindVisualRules.inventoryY(height)+22),
                () -> ClassicWirelessClient.openWindGenerator(menu.containerId, menu.sourcePos())));
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ClassicHudCanvas canvas = new ClassicHudCanvas(graphics);
        canvas.begin();
        try {
            double pageX=ClassicWindVisualRules.inventoryX(width),pageY=ClassicWindVisualRules.inventoryY(height);
            canvas.rect(tex("parent/parent_background"), pageX, pageY, 176, 187, WHITE);
            double breathe = ClassicWirelessVisualRules.breatheAlpha(ClassicWirelessClock.millis());
            canvas.rect(tex("ui/ui_inventory"), pageX, pageY, 176, 187, rgba(1, 1, 1, breathe));
            canvas.rect(tex(menu.base()?"ui/ui_windbase":"ui/ui_windmain"), pageX, pageY, 176, 187, rgba(1, 1, 1, breathe));
            if(menu.base()){double[] alpha=ClassicWindVisualRules.iconAlpha(menu.completeness());String[] names={"main","middle","base"};int[] ys={13,31,49};for(int i=0;i<3;i++)canvas.rect(tex("icons/icon_wind_"+names[i]),pageX+76,pageY+ys[i],24,24,rgba(1,1,1,alpha[i]));}
            drawInfo(canvas);
        } finally {
            canvas.end();
        }
    }

    private void drawInfo(ClassicHudCanvas canvas) {
        double x = ClassicWindVisualRules.mainX(width) + ClassicWindVisualRules.INFO_X;
        double y = ClassicWindVisualRules.inventoryY(height) + ClassicWindVisualRules.INFO_Y;
        long now = ClassicWirelessClock.millis();
        double step = Math.min(500, Math.max(0, now - lastInfoTime)) * .5;
        double target = (menu.base()?ClassicWindVisualRules.BASE_INFO_HEIGHT:ClassicWindVisualRules.MAIN_INFO_HEIGHT);
        infoHeight += Math.signum(target - infoHeight) * Math.min(step, Math.abs(target - infoHeight));
        lastInfoTime = now;
        double alpha = Math.max(0, Math.min(1, (now - openedAt - 300) / 300d));
        double w = ClassicWindVisualRules.INFO_WIDTH;
        double[] xs = {x - 4, x, x + w, x + w + 4};
        double[] ys = {y - 4, y, y + infoHeight, y + infoHeight + 4};
        for (int col = 0; col < 3; col++) for (int row = 0; row < 3; row++)
            canvas.rect(tex("blend_quad"), xs[col], ys[row], xs[col + 1] - xs[col], ys[row + 1] - ys[row],
                    col / 3d, row / 3d, (col + 1) / 3d, (row + 1) / 3d,
                    rgba(0, 0, 0, .5), null, null);
        canvas.rect(tex("line"), x - 3.2, y - 8.6, w + 6.4, 12, WHITE);
        canvas.rect(tex("line"), x - 3.2, y + infoHeight - 2, w + 6.4, 8, WHITE);
        if(menu.base()){
            canvas.rect(tex("histogram"),x,y-20,84,84,rgba(1,1,1,alpha));
            double fraction=ClassicWirelessVisualRules.histogramFraction(menu.energy(),ClassicWindRules.CAPACITY);
            double barHeight=48*fraction;canvas.fill(x+22.4,y+59.2-barHeight,6.4,barHeight,color(ClassicWindVisualRules.BUFFER_COLOR,alpha),false);
            canvas.fill(x+3,y+65.5,6,6,color(ClassicWindVisualRules.BUFFER_COLOR,alpha),false);
            sourceFont.draw(canvas,Component.translatable("ac.gui.common.hist.buffer").getString(),x+10,y+64,8,rgba(1,1,1,alpha),false,false);
            sourceFont.draw(canvas,String.format(Locale.ROOT,"%.0f IF",menu.energy()),x+46,y+64,8,rgba(1,1,1,alpha),false,false);
            sourceFont.draw(canvas,Component.translatable("ac.gui.common.sep.info").getString(),x+3,y+75,6,rgba(1,1,1,.6*alpha),false,false);
        }
        double propertyY=menu.base()?83:10;
        sourceFont.draw(canvas,Component.translatable("ac.gui.common.prop.altitude").getString(),x+6,y+propertyY,8,rgba(1,1,1,alpha),false,false);
        sourceFont.draw(canvas,Integer.toString(menu.altitude()),x+46,y+propertyY,8,rgba(1,1,1,alpha),false,false);
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
