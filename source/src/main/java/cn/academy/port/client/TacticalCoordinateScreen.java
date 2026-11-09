package cn.academy.port.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Compact native, non-pausing panel. Opening/closing and key release cannot execute a move. */
public final class TacticalCoordinateScreen extends Screen {
    private static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/icons/icon_location_on.png");
    private TacticalCoordinateClient.Snapshot snapshot;
    private final TacticalCoordinateUiState.Identity identity;
    private final boolean review;
    private final TacticalCoordinateUiState.Keys keys = new TacticalCoordinateUiState.Keys();
    private final List<PaletteButton> buttons = new ArrayList<>();
    private TacticalCoordinateUiState.Layout layout;
    private boolean serverClosing, closed, waiting, choosingRecipient;
    private long requestedAt;
    private UUID selectedRecipient;
    private int scroll;
    private EditBox search;
    private String query = "";
    private String lastReason = "";
    private long reasonUntil;

    public TacticalCoordinateScreen(TacticalCoordinateClient.Snapshot snapshot) {
        super(TacticalCoordinateClient.text(snapshot.invitation() ? "review_title" : "title"));
        this.snapshot = snapshot; identity = snapshot.identity(); review = snapshot.invitation();
    }
    public boolean review() { return review; }
    public boolean matches(TacticalCoordinateClient.Snapshot next) { return identity.equals(next.identity()); }
    public void update(TacticalCoordinateClient.Snapshot next) {
        if (!matches(next) || next.sequence() <= snapshot.sequence()) return;
        snapshot = next; waiting = false;
        if (!next.reason().isEmpty()) { lastReason = next.reason(); reasonUntil = TacticalCoordinateClient.ticks() + 60; }
        if (choosingRecipient && next.reason().equals("invited")) { choosingRecipient = false; selectedRecipient = null; build(); }
        if (selectedRecipient != null && next.recipients().stream().noneMatch(r -> r.id().equals(selectedRecipient))) selectedRecipient = null;
        refreshEnabled();
    }
    @Override protected void init() {
        layout = TacticalCoordinateUiState.layout(width, height);
        // Keyboard repeat events can precede the opening key's release, so fence physically held controls.
        long window = Minecraft.getInstance().getWindow().getWindow();
        for (int key : new int[]{GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE})
            if (InputConstants.isKeyDown(window, key)) keys.blockHeld(key);
        build();
    }
    private void build() {
        clearWidgets(); buttons.clear(); search = null;
        if (review) {
            add(TacticalCoordinateClient.text("accept"), layout.button(0, 1), () -> action("accept", null));
            add(TacticalCoordinateClient.text("reject"), layout.button(1, 1), () -> action("reject", null));
        } else if (choosingRecipient) {
            var p = layout.panel();
            search = new EditBox(font, p.x() + 8, p.y() + 27, p.width() - 16, 18, TacticalCoordinateClient.text("search"));
            search.setMaxLength(32); search.setHint(TacticalCoordinateClient.text("search")); search.setValue(query);
            search.setResponder(value -> { query = value; scroll = 0; selectedRecipient = null; }); addRenderableWidget(search);
            for (int i = 0; i < 4; i++) {
                final int row = i;
                add(Component.empty(), new TacticalCoordinateUiState.Rect(p.x() + 8, p.y() + 51 + i * 19, p.width() - 16, 17), () -> {
                    var people = filtered(); int index = scroll + row;
                    if (index < people.size()) selectedRecipient = people.get(index).id();
                    setFocused(null);
                });
            }
            add(TacticalCoordinateClient.text("back"), layout.button(0, 1), () -> { choosingRecipient = false; selectedRecipient = null; build(); });
            add(TacticalCoordinateClient.text("invite"), layout.button(1, 1), () -> action("invite", selectedRecipient));
        } else {
            for (int i = 0; i < 6; i++) {
                final int index = i;
                add(Component.literal(Integer.toString(i + 1)), layout.tab(i), () -> select(index));
            }
            add(TacticalCoordinateClient.text("save_current"), layout.button(0, 0), () -> action("save", null));
            add(TacticalCoordinateClient.text("delete"), layout.button(1, 0), () -> action("delete", null));
            add(TacticalCoordinateClient.text("confirm"), layout.button(0, 1), () -> action("confirm", null));
            add(TacticalCoordinateClient.text("invite"), layout.button(1, 1), () -> { choosingRecipient = true; selectedRecipient = null; scroll = 0; query = ""; build(); });
        }
        setFocused(null); refreshEnabled();
    }
    private void add(Component title, TacticalCoordinateUiState.Rect rect, Runnable action) {
        PaletteButton button = new PaletteButton(rect, title, () -> { if (!waiting) action.run(); });
        buttons.add(button); addRenderableWidget(button);
    }
    private void refreshEnabled() {
        if (buttons.isEmpty()) return;
        for (var button : buttons) { button.active = !waiting; button.selected = false; }
        if (review) { boolean live = TacticalCoordinateClient.remaining(snapshot) > 0; buttons.get(0).active &= live; buttons.get(1).active &= live; }
        else if (choosingRecipient) {
            var people = filtered(); scroll = Math.max(0, Math.min(scroll, Math.max(0, people.size() - 4)));
            for (int i = 0; i < 4; i++) {
                var button = buttons.get(i); int index = scroll + i; button.visible = index < people.size();
                if (button.visible) { var person = people.get(index); button.setMessage(Component.literal(person.name())); button.selected = person.id().equals(selectedRecipient); }
            }
            buttons.get(5).active &= selectedRecipient != null && snapshot.canConfirm() && !snapshot.dirty();
        } else {
            for (int i = 0; i < 6; i++) buttons.get(i).selected = i == snapshot.selected();
            buttons.get(6).setMessage(TacticalCoordinateClient.text(snapshot.chosen() == null ? "save_current" : "save_facing"));
            buttons.get(6).active &= snapshot.storageValid();
            buttons.get(7).active &= snapshot.storageValid() && snapshot.chosen() != null;
            buttons.get(8).active &= snapshot.canConfirm() && !snapshot.dirty();
            buttons.get(9).active &= snapshot.canConfirm() && !snapshot.dirty() && !snapshot.recipients().isEmpty();
        }
    }
    private List<TacticalCoordinateClient.Recipient> filtered() {
        String filter = query.toLowerCase(Locale.ROOT);
        return snapshot.recipients().stream().filter(r -> r.name().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    private void action(String action, UUID recipient) {
        if (waiting || action.equals("invite") && recipient == null) return;
        if (TacticalCoordinateClient.act(snapshot, action, recipient)) { waiting = true; requestedAt = TacticalCoordinateClient.ticks(); }
        setFocused(null); refreshEnabled();
    }
    private void select(int index) {
        if (!waiting && index != snapshot.selected() && TacticalCoordinateClient.select(snapshot, index)) { waiting = true; requestedAt = TacticalCoordinateClient.ticks(); }
        setFocused(null); refreshEnabled();
    }
    @Override public void tick() {
        super.tick(); refreshEnabled();
        if (review && TacticalCoordinateClient.remaining(snapshot) <= 0) closeFromServer();
        else if (waiting && TacticalCoordinateClient.ticks() - requestedAt > 100) onClose();
    }
    /** Screen.render otherwise blurs our already-painted panel before drawing its native widgets. */
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // This non-pausing ACA overlay supplies its own panel and keeps the world previews visible.
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var p = layout.panel();
        graphics.fill(p.x(), p.y(), p.x() + p.width(), p.y() + p.height(), 0xD5353535);
        graphics.renderOutline(p.x(), p.y(), p.width(), p.height(), 0xA0C1CFD5);
        graphics.setColor(.76F, .81F, .84F, 1);
        graphics.blit(LOCATION, p.x() + 8, p.y() + 7, 12, 12, 0, 0, 32, 32, 32, 32);
        graphics.setColor(1, 1, 1, 1);
        line(graphics, choosingRecipient ? TacticalCoordinateClient.text("choose_recipient") : title, 24, 8, p.width() - 50, 0xFFE2E7E8);
        if (review) renderReview(graphics);
        else if (choosingRecipient) {
            if (filtered().isEmpty()) line(graphics, TacticalCoordinateClient.text("no_recipients"), 8, 76, p.width() - 16, 0xFFC1CFD5);
            line(graphics, TacticalCoordinateClient.text("recipient_count", filtered().size()), 8, 159, p.width() - 16, 0xFF9EA9AD);
        } else renderMenu(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (review && mouseY >= p.y() + 4 && mouseY < p.y() + 24 && mouseX >= p.x() && mouseX < p.x() + p.width()) {
            var details = new ArrayList<net.minecraft.util.FormattedCharSequence>();
            for (Component detail : List.of(TacticalCoordinateClient.text("caster_identity", snapshot.caster()),
                    TacticalCoordinateClient.text("template_identity", snapshot.templateId(), snapshot.revision()),
                    TacticalCoordinateClient.text("request_identity", snapshot.nonce()),
                    Component.literal(snapshot.dimension()), TacticalCoordinateClient.text("expires", (TacticalCoordinateClient.remaining(snapshot)+19)/20)))
                details.addAll(font.split(detail, Math.min(280,width-24)));
            graphics.renderTooltip(font, details, mouseX, mouseY);
        }
        if (!choosingRecipient && !review && snapshot.chosen() != null && mouseY >= p.y() + 49 && mouseY < p.y() + 73 && mouseX >= p.x() && mouseX < p.x() + p.width())
            graphics.renderTooltip(font, Component.literal(snapshot.chosen().dimension()), mouseX, mouseY);
    }
    private void renderMenu(GuiGraphics graphics) {
        var selected = snapshot.chosen(); var p = layout.panel();
        if (selected == null) {
            line(graphics, TacticalCoordinateClient.text("empty", snapshot.selected() + 1), 8, 53, p.width() - 16, 0xFFC1CFD5);
            line(graphics, TacticalCoordinateClient.text("save_hint"), 8, 68, p.width() - 16, 0xFF9EA9AD);
        } else {
            line(graphics, Component.literal(TacticalCoordinateClient.position(selected.feet())), 8, 52, p.width() - 16, 0xFFE2E7E8);
            line(graphics, Component.literal(selected.dimension()), 8, 65, p.width() - 16, 0xFF9EA9AD);
            line(graphics, TacticalCoordinateClient.text("facing", Math.round(TacticalCoordinateUiState.normalizedYaw(snapshot.draftYaw())), Math.round(selected.pitch())), 8, 78, p.width() - 16, 0xFFC1CFD5);
        }
        Component status = waiting ? TacticalCoordinateClient.text("waiting") : !snapshot.storageValid() ? TacticalCoordinateClient.reason("corrupt")
                : !lastReason.isEmpty() && TacticalCoordinateClient.ticks() < reasonUntil ? TacticalCoordinateClient.reason(lastReason)
                : snapshot.dirty() ? TacticalCoordinateClient.text("unsaved") : selected == null ? TacticalCoordinateClient.text("empty_status")
                : !snapshot.canConfirm() && !snapshot.confirmReason().isEmpty() ? TacticalCoordinateClient.reason(snapshot.confirmReason())
                : selected.valid() ? TacticalCoordinateClient.text("ready", String.format(Locale.ROOT,"%.2f",snapshot.cost())) : TacticalCoordinateClient.reason(selected.reason());
        line(graphics, status, 8, 94, p.width() - 16, 0xFFC1CFD5);
        line(graphics, TacticalCoordinateClient.text("keys"), 8, 159, p.width() - 16, 0xFF9EA9AD);
    }
    private void renderReview(GuiGraphics graphics) {
        var p = layout.panel();
        line(graphics, TacticalCoordinateClient.text("invited_by", snapshot.casterName()), 8, 28, p.width() - 16, 0xFFE2E7E8);
        line(graphics, Component.literal(TacticalCoordinateClient.position(snapshot.feet())), 8, 44, p.width() - 16, 0xFFC1CFD5);
        line(graphics, Component.literal(snapshot.dimension()), 8, 58, p.width() - 16, 0xFF9EA9AD);
        line(graphics, TacticalCoordinateClient.text("facing", Math.round(TacticalCoordinateUiState.normalizedYaw(snapshot.yaw())), Math.round(snapshot.pitch())), 8, 72, p.width() - 16, 0xFFC1CFD5);
        line(graphics, TacticalCoordinateClient.text("expires", (TacticalCoordinateClient.remaining(snapshot) + 19) / 20), 8, 88, p.width() - 16, 0xFFC1CFD5);
        line(graphics, waiting ? TacticalCoordinateClient.text("waiting") : TacticalCoordinateClient.text("consent"), 8, 107, p.width() - 16, 0xFFE2E7E8);
        line(graphics, TacticalCoordinateClient.text("review_footer"), 8, 159, p.width() - 16, 0xFF9EA9AD);
    }
    private void line(GuiGraphics graphics, Component text, int x, int y, int maximum, int color) {
        String value = text.getString();
        if (font.width(value) > maximum) value = font.plainSubstrByWidth(value, Math.max(0, maximum - font.width("…"))) + "…";
        graphics.drawString(font, value, layout.panel().x() + x, layout.panel().y() + y, color, false);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean arrow = key >= GLFW.GLFW_KEY_RIGHT && key <= GLFW.GLFW_KEY_UP;
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
        if (arrow || enter) {
            // Consume repeats even when focus has moved; typing never invokes an ability action.
            boolean edge = keys.press(key, true);
            if (!edge) return true;
            if (search != null && search.isFocused()) return enter && key != GLFW.GLFW_KEY_SPACE || super.keyPressed(key, scanCode, modifiers);
            if (enter) return getFocused() instanceof PaletteButton && super.keyPressed(key, scanCode, modifiers);
            boolean coordinateFocus = getFocused() == null || !review && !choosingRecipient && buttons.subList(0,6).contains(getFocused());
            if (coordinateFocus && !review && !choosingRecipient) {
                if (key == GLFW.GLFW_KEY_LEFT) select(TacticalCoordinateUiState.selected(snapshot.selected(), -1));
                else if (key == GLFW.GLFW_KEY_RIGHT) select(TacticalCoordinateUiState.selected(snapshot.selected(), 1));
                else if (snapshot.chosen() != null) action(key == GLFW.GLFW_KEY_UP ? "rotate_right" : "rotate_left", null);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && choosingRecipient) { choosingRecipient = false; build(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int key, int scanCode, int modifiers) { keys.release(key); return super.keyReleased(key, scanCode, modifiers); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (!handled && layout.panel().contains(x, y)) { setFocused(null); if (search != null) search.setFocused(false); return true; }
        return handled;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (choosingRecipient && layout.panel().contains(x, y)) { scroll = Math.max(0, Math.min(Math.max(0, filtered().size() - 4), scroll - (int)Math.signum(vertical))); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        if (!closed) { closed = true; keys.clear(); if (!serverClosing) TacticalCoordinateClient.closed(snapshot, review); }
        super.removed();
    }
    public void closeFromServer() { serverClosing = true; if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null); }

    /** Native focus/narration/hit testing with the original ACA gray/white palette. */
    private static final class PaletteButton extends Button {
        boolean selected;
        PaletteButton(TacticalCoordinateUiState.Rect rect, Component title, Runnable action) {
            super(rect.x(), rect.y(), rect.width(), rect.height(), title, ignored -> action.run(), DEFAULT_NARRATION);
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHoveredOrFocused();
            graphics.fill(getX(), getY(), getX() + width, getY() + height, selected ? 0x507E939D : hover && active ? 0x40FFFFFF : 0x18FFFFFF);
            graphics.renderOutline(getX(), getY(), width, height, selected || hover && active ? 0xCCCBD7DB : 0x556B7A80);
            var font = Minecraft.getInstance().font; String label = getMessage().getString();
            if (font.width(label) > width - 10) label = font.plainSubstrByWidth(label, Math.max(0, width - 15)) + "…";
            graphics.drawString(font, label, getX() + (width - font.width(label)) / 2, getY() + (height - 8) / 2, active ? 0xFFE2E7E8 : 0xFF727B7F, false);
        }
    }
}
