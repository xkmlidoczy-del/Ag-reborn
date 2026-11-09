package cn.academy.port.client.terminal;

import cn.academy.port.story.UrbanStoryProgress;
import cn.academy.port.story.UrbanStoryStorage;
import cn.academy.port.terminal.TerminalNetwork;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Read-only server snapshot. Completing objectives always requires an in-world server event. */
final class UrbanStoryScreen extends Screen {
    private final UrbanStoryProgress progress;
    private int scroll;
    private Component location;

    UrbanStoryScreen(CompoundTag packet) {
        super(Component.translatable("ac.app.urban_communication.name"));
        progress = packet != null && packet.getBoolean("urban_valid") && packet.contains("urban_story", Tag.TAG_COMPOUND)
                ? UrbanStoryStorage.decode(packet.getCompound("urban_story")).orElse(null) : null;
    }
    private List<UrbanStoryProgress.Objective> objectives() {
        if (progress == null) return List.of();
        return Arrays.stream(UrbanStoryProgress.Objective.values())
                .filter(objective -> objective.chapter().ordinal() <= progress.chapter().ordinal()).toList();
    }
    private int panelWidth() { return Math.max(100, Math.min(360, width - 20)); }
    private int panelHeight() { return Math.max(100, Math.min(240, height - 20)); }
    private int visibleRows() { return Math.max(1, (panelHeight() - 127) / 20); }
    private int maxScroll() { return Math.max(0, objectives().size() - visibleRows()); }
    private static Component label(String prefix, Enum<?> value) {
        return Component.translatable(prefix + value.name().toLowerCase(Locale.ROOT));
    }
    @Override protected void init() {
        int w=panelWidth(), h=panelHeight(), x=(width-w)/2, y=(height-h)/2;
        scroll=maxScroll();
        if(progress!=null) {
            var label=Component.translatable("academy.urban.locate");
            addRenderableWidget(Button.builder(label, button -> TerminalNetwork.requestUrbanLocation())
                    .bounds(x+12,y+h-30,Math.min(Math.max(100,font.width(label)+20),w>=260?w-112:w-24),20).build());
            if(w>=260)addRenderableWidget(Button.builder(Component.translatable("academy.urban.routes"),
                    button -> minecraft.setScreen(new UrbanRouteHelpScreen(this))).bounds(x+w-96,y+h-30,84,20).build());
        }
    }
    void location(CompoundTag data) {
        String status=data.getString("status");
        if (status.equals("located") && data.contains("x",Tag.TAG_INT) && data.contains("z",Tag.TAG_INT))
            location=Component.translatable("academy.urban.located",data.getInt("x"),data.getInt("z"));
        else if(status.equals("located_underground") && data.contains("x",Tag.TAG_INT) && data.contains("y",Tag.TAG_INT) && data.contains("z",Tag.TAG_INT))
            location=Component.translatable("academy.urban.located_underground",data.getInt("x"),data.getInt("y"),data.getInt("z"));
        else if (List.of("not_found","locate_busy","unavailable_dimension","unavailable").contains(status))
            location=Component.translatable("academy.urban."+status);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int w=panelWidth(), h=panelHeight(), x=(width-w)/2, y=(height-h)/2;
        graphics.fill(x, y, x+w, y+h, 0xe8111f2b);
        graphics.fill(x, y, x+w, y+2, 0xff65cfe1);
        graphics.fill(x+12, y+39, x+w-12, y+40, 0xff476c78);
        graphics.drawString(font, title, x+14, y+13, 0xffe9f9fc, false);
        if (progress == null) {
            graphics.drawString(font, Component.translatable("academy.urban.unavailable"), x+14, y+52, 0xffffc378, false);
            return;
        }
        graphics.drawString(font, label("academy.urban.chapter.", progress.chapter()), x+14, y+51, 0xff80d9e7, false);
        var objectives=objectives();
        scroll=Math.min(scroll, maxScroll());
        if(h<130){renderWidgets(graphics,mouseX,mouseY,partialTick);return;}
        graphics.enableScissor(x+10,y+71,x+w-10,y+h-58);
        try {
            for (int i=scroll; i<objectives.size() && i<scroll+visibleRows(); i++) {
                var objective=objectives.get(i); int rowY=y+73+(i-scroll)*20;
                boolean done=progress.completed(objective);
                graphics.fill(x+12,rowY-3,x+w-12,rowY+14,done?0x553e8f87:0x55304451);
                var name=label("academy.urban.objective.",objective);
                graphics.drawString(font,font.plainSubstrByWidth(name.getString(),Math.max(1,w-105)),x+18,rowY,0xffedf5f7,false);
                var state=done?label("academy.urban.approach.",progress.approach(objective).orElseThrow())
                        :Component.translatable("academy.urban.objective.pending");
                graphics.drawString(font,state,x+w-18-font.width(state),rowY,done?0xff84e4c4:0xffafc1ca,false);
            }
        } finally { graphics.disableScissor(); }
        if(location!=null)graphics.drawString(font,font.plainSubstrByWidth(location.getString(),w-24),x+12,y+h-48,0xffb9dae3,false);
        renderWidgets(graphics,mouseX,mouseY,partialTick);
    }
    private void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (var widget : renderables) widget.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical) {
        scroll=Math.max(0,Math.min(maxScroll(),scroll-(int)Math.signum(vertical)));
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
