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

/** A small native panel with deliberate named selection and independent recipient Accept/Reject. */
public final class SpatialPincerScreen extends Screen {
    private static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/icons/icon_location_on.png");
    private SpatialPincerClient.Snapshot snapshot;
    private final SpatialPincerUiState.Identity identity;
    private final boolean review;
    private final SpatialPincerUiState.Keys keys = new SpatialPincerUiState.Keys();
    private final List<PaletteButton> buttons = new ArrayList<>();
    private SpatialPincerUiState.Layout layout;
    private boolean serverClosing, closed, waiting;
    private long requestedAt;
    private UUID selectedRecipient;
    private int scroll;
    private EditBox search;
    private String query = "";
    private String lastReason = "";
    private long reasonUntil;

    public SpatialPincerScreen(SpatialPincerClient.Snapshot snapshot) {
        super(SpatialPincerClient.text(snapshot.invitation() ? "review_title" : "choose_recipient"));
        this.snapshot = snapshot; identity = snapshot.identity(); review = snapshot.invitation();
    }
    public boolean review() { return review; }
    public boolean matches(SpatialPincerClient.Snapshot next) { return identity.equals(next.identity()); }
    public void update(SpatialPincerClient.Snapshot next) {
        if (!matches(next) || next.sequence() <= snapshot.sequence()) return;
        snapshot = next; waiting = false;
        if (!next.reason().isEmpty()) { lastReason = next.reason(); reasonUntil = SpatialPincerClient.ticks() + 60; }
        if (selectedRecipient != null && next.recipients().stream().noneMatch(r -> r.id().equals(selectedRecipient))) selectedRecipient = null;
        refreshEnabled();
    }
    @Override protected void init() {
        layout = SpatialPincerUiState.layout(width, height);
        long window = Minecraft.getInstance().getWindow().getWindow();
        for (int key : new int[]{GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE})
            if (InputConstants.isKeyDown(window, key)) keys.blockHeld(key);
        build();
    }
    private void build() {
        clearWidgets(); buttons.clear(); search = null;
        if (review) {
            add(SpatialPincerClient.text("accept"), layout.button(0), () -> action("accept", null));
            add(SpatialPincerClient.text("reject"), layout.button(1), () -> action("reject", null));
        } else {
            var p = layout.panel();
            search = new EditBox(font, p.x() + 8, p.y() + 41, p.width() - 16, 18, SpatialPincerClient.text("search"));
            search.setMaxLength(32); search.setHint(SpatialPincerClient.text("search")); search.setValue(query);
            search.setResponder(value -> { query = value; scroll = 0; selectedRecipient = null; }); addRenderableWidget(search);
            for (int i = 0; i < 4; i++) {
                final int row = i;
                add(Component.empty(), layout.recipient(row), () -> {
                    var people = filtered(); int index = scroll + row;
                    if (index < people.size()) selectedRecipient = people.get(index).id();
                    setFocused(null); refreshEnabled();
                });
            }
            add(SpatialPincerClient.text("close"), layout.button(0), this::onClose);
            add(SpatialPincerClient.text("invite"), layout.button(1), () -> action("invite", selectedRecipient));
        }
        setFocused(null); refreshEnabled();
    }
    private void add(Component title, SpatialPincerUiState.Rect rect, Runnable action) {
        PaletteButton button = new PaletteButton(rect, title, action);
        buttons.add(button); addRenderableWidget(button);
    }
    private void refreshEnabled() {
        if (buttons.isEmpty()) return;
        boolean live = SpatialPincerClient.remaining(snapshot) > 0;
        for (var button : buttons) { button.active = !waiting && live; button.selected = false; }
        if (!review) {
            var people = filtered(); scroll = Math.max(0, Math.min(scroll, Math.max(0, people.size() - 4)));
            for (int i = 0; i < 4; i++) {
                var button = buttons.get(i); int index = scroll + i; button.visible = index < people.size();
                if (button.visible) {
                    var person = people.get(index); button.setMessage(Component.literal(person.name()));
                    button.selected = person.id().equals(selectedRecipient); button.active &= !snapshot.invitationPending();
                }
            }
            buttons.get(4).active = true;
            buttons.get(5).active &= selectedRecipient != null && !snapshot.invitationPending();
            if (search != null) search.setEditable(!waiting && !snapshot.invitationPending());
        }
    }
    private List<SpatialPincerClient.Recipient> filtered() {
        String filter = query.toLowerCase(Locale.ROOT);
        return snapshot.recipients().stream().filter(r -> r.name().toLowerCase(Locale.ROOT).contains(filter)).toList();
    }
    private void action(String action, UUID recipient) {
        if (waiting || action.equals("invite") && recipient == null) return;
        if (SpatialPincerClient.act(snapshot, action, recipient)) { waiting = true; requestedAt = SpatialPincerClient.ticks(); }
        setFocused(null); refreshEnabled();
    }
    @Override public void tick() {
        super.tick(); refreshEnabled();
        if (SpatialPincerClient.remaining(snapshot) <= 0) onClose();
        else if (waiting && SpatialPincerClient.ticks() - requestedAt > 100) onClose();
    }
    /** Vanilla's Screen.render would otherwise blur text already painted by this custom panel. */
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: the native non-pausing panel keeps the world and route legible.
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var p = layout.panel();
        graphics.fill(p.x(), p.y(), p.x() + p.width(), p.y() + p.height(), 0xD5353535);
        graphics.renderOutline(p.x(), p.y(), p.width(), p.height(), 0xA0C1CFD5);
        graphics.setColor(.76F, .81F, .84F, 1);
        graphics.blit(LOCATION, p.x() + 8, p.y() + 7, 12, 12, 0, 0, 32, 32, 32, 32);
        graphics.setColor(1, 1, 1, 1);
        line(graphics, title, 24, 8, p.width() - 32, 0xFFE2E7E8);
        if (review) renderReview(graphics);
        else {
            line(graphics, SpatialPincerClient.text("picker_route"), 8, 26, p.width() - 16, 0xFFC1CFD5);
            if (filtered().isEmpty()) line(graphics, SpatialPincerClient.text("no_recipients"), 8, 82, p.width() - 16, 0xFFC1CFD5);
            Component status = waiting ? SpatialPincerClient.text("waiting") : snapshot.invitationPending()
                    ? SpatialPincerClient.text("invitation_pending") : !lastReason.isEmpty() && SpatialPincerClient.ticks() < reasonUntil
                    ? SpatialPincerClient.reason(lastReason) : SpatialPincerClient.text("recipient_count", filtered().size(), (SpatialPincerClient.remaining(snapshot) + 19) / 20);
            line(graphics, status, 8, 142, p.width() - 16, 0xFF9EA9AD);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (review && mouseY >= p.y() + 4 && mouseY < p.y() + 24 && mouseX >= p.x() && mouseX < p.x() + p.width()) {
            var details = new ArrayList<net.minecraft.util.FormattedCharSequence>();
            for (Component detail : List.of(SpatialPincerClient.text("caster_identity", snapshot.caster()),
                    SpatialPincerClient.text("request_identity", snapshot.nonce()), Component.literal(snapshot.dimension())))
                details.addAll(font.split(detail, Math.min(280, width - 24)));
            graphics.renderTooltip(font, details, mouseX, mouseY);
        }
        if (!review) for (int i = 0; i < 4; i++) {
            int index = scroll + i; var people = filtered();
            if (index < people.size() && layout.recipient(i).contains(mouseX, mouseY))
                graphics.renderTooltip(font, Component.literal(people.get(index).name() + " · " + people.get(index).id()), mouseX, mouseY);
        }
    }
    private void renderReview(GuiGraphics graphics) {
        var p = layout.panel();
        line(graphics, SpatialPincerClient.text("invited_by", snapshot.casterName()), 8, 28, p.width() - 16, 0xFFE2E7E8);
        line(graphics, SpatialPincerClient.text("route_a", SpatialPincerClient.position(snapshot.source())), 8, 44, p.width() - 16, 0xFFC1CFD5);
        line(graphics, SpatialPincerClient.text("route_c", SpatialPincerClient.position(snapshot.destination())), 8, 58, p.width() - 16, 0xFFC1CFD5);
        line(graphics, SpatialPincerClient.text("b_attack_only"), 8, 72, p.width() - 16, 0xFF9EA9AD);
        line(graphics, SpatialPincerClient.text("expires", (SpatialPincerClient.remaining(snapshot) + 19) / 20), 8, 88, p.width() - 16, 0xFFC1CFD5);
        line(graphics, SpatialPincerClient.text("consent"), 8, 105, p.width() - 16, 0xFFE2E7E8);
        line(graphics, SpatialPincerClient.text("cost", String.format(Locale.ROOT, "%.2f", snapshot.cost()), (snapshot.cooldown() + 19) / 20), 8, 120, p.width() - 16, 0xFF9EA9AD);
        line(graphics, waiting ? SpatialPincerClient.text("waiting") : SpatialPincerClient.text("review_footer"), 8, 142, p.width() - 16, 0xFF9EA9AD);
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
            boolean edge = keys.press(key, true);
            if (search != null && search.isFocused()) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) return true;
                return super.keyPressed(key, scanCode, modifiers);
            }
            if (!edge) return true;
            if (enter) return getFocused() instanceof PaletteButton && super.keyPressed(key, scanCode, modifiers);
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int key, int scanCode, int modifiers) { keys.release(key); return super.keyReleased(key, scanCode, modifiers); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (!handled && layout.panel().contains(x, y)) { setFocused(null); if (search != null) search.setFocused(false); return true; }
        return handled;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!review && layout.panel().contains(x, y)) { scroll = Math.max(0, Math.min(Math.max(0, filtered().size() - 4), scroll - (int)Math.signum(vertical))); return true; }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        if (!closed) { closed = true; keys.clear(); if (!serverClosing) SpatialPincerClient.closed(snapshot, review); }
        super.removed();
    }
    public void closeFromServer() { serverClosing = true; if (minecraft != null && minecraft.screen == this) minecraft.setScreen(null); }
    private static final class PaletteButton extends Button {
        boolean selected;
        PaletteButton(SpatialPincerUiState.Rect rect, Component title, Runnable action) {
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
