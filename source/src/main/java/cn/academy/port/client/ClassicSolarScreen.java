/* AcademyCraft1.0.7 GuiSolarGen/page_solar/TechUI source art and slot placement. GPLv3. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.solar.ClassicSolarMenu;
import cn.academy.port.solar.ClassicSolarRules;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Real native inventory clicks/shift transfers over the original source inventory page artwork. */
public final class ClassicSolarScreen extends AbstractContainerScreen<ClassicSolarMenu> {
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png"); }
    private static final ResourceLocation BACKGROUND = texture("parent/parent_background"), INVENTORY = texture("ui/ui_inventory"), SOLAR_PAGE = texture("ui/ui_windbase"), EFFECT = texture("effect/effect_solar"), HISTOGRAM = texture("histogram");
    public ClassicSolarScreen(ClassicSolarMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth = 176; imageHeight = 187; }
    @Override protected void init() { super.init(); leftPos = (width - 304) / 2; topPos = (height - imageHeight) / 2;
        addRenderableWidget(new ClassicWirelessTabButton(leftPos-20,topPos+22,()->ClassicWirelessClient.openSolar(menu.containerId,menu.sourcePos())));
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        graphics.blit(BACKGROUND,leftPos,topPos,176,187,0,0,352,374,352,374);
        graphics.blit(INVENTORY,leftPos,topPos,176,187,0,0,352,374,352,374);
        graphics.blit(SOLAR_PAGE,leftPos,topPos,176,187,0,0,352,374,352,374);
        int sourceY = switch (menu.status()) { case STRONG -> 0; case STOPPED -> 70; case WEAK -> 140; };
        // page_solar: x56/y23, 104×70 source image, scale0.6.
        graphics.blit(EFFECT,leftPos+56,topPos+23,62,42,0,sourceY,104,70,104,210);
        int infoX = leftPos + 186;
        graphics.fill(infoX-4,topPos-2,infoX+118,topPos+187,0xb5182732);
        graphics.fill(infoX-4,topPos-2,infoX+118,topPos,0xff25f7ff);
        graphics.fill(infoX-4,topPos+185,infoX+118,topPos+187,0xff25f7ff);
        graphics.blit(HISTOGRAM,infoX+14,topPos+18,84,84,0,0,210,210,210,210);
        // Source histBuffer color0xff25f7ff, UP fill, value clamped0.03–1, same source bar placement.
        double fraction = Math.max(.03, Math.min(1, menu.energy()/ClassicSolarRules.CAPACITY));
        int barHeight = (int)Math.round(48*fraction);
        graphics.fill(infoX+36,topPos+97-barHeight,infoX+42,topPos+97,0xff25f7ff);
        graphics.drawString(font,title,infoX,topPos+4,0xffdceeff,false);
        graphics.drawString(font,Component.translatable("ac.gui.common.hist.buffer"),infoX,topPos+107,0xffc8e0ec,false);
        graphics.drawString(font,String.format(Locale.ROOT,"%.1f/1000 IF",menu.energy()),infoX,topPos+120,0xff25f7ff,false);
        graphics.drawString(font,Component.translatable("ac.gui.common.prop.gen_speed"),infoX,topPos+140,0xffc8e0ec,false);
        graphics.drawString(font,String.format(Locale.ROOT,"%.1f IF/T",ClassicSolarRules.rate(menu.status())),infoX,topPos+153,0xff25f7ff,false);
        graphics.drawString(font,Component.translatable("academy.solar.status."+menu.status().name().toLowerCase(Locale.ROOT)),infoX,topPos+172,0xffc8e0ec,false);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {} // Original page art supplies inventory framing.
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { super.render(graphics,mouseX,mouseY,partialTick); renderTooltip(graphics,mouseX,mouseY); }
}
