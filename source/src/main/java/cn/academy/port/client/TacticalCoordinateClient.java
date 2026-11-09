package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.TacticalCoordinateFormation;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Owner-only authenticated menus and explicit recipient consent. Network notices never open a screen. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class TacticalCoordinateClient {
    public static final String PREFIX = "academy.highpower.tactical_coordinate.";
    public record Template(int index, UUID id, long revision, String dimension, Vec3 feet, float yaw, float pitch, boolean valid, String reason) {}
    public record Recipient(UUID id, String name) {}
    public record Snapshot(String phase, int actor, UUID owner, long input, long epoch, long token, UUID nonce, long sequence,
                           int selected, String reason, String confirmReason, String dimension, double cost, int cooldown, int remaining,
                           float draftYaw, boolean dirty, boolean storageValid, boolean canConfirm, List<Template> templates,
                           List<Recipient> recipients, UUID caster, String casterName, UUID templateId, long revision,
                           Vec3 feet, float yaw, float pitch, boolean moved, UUID movedOwner, Vec3 from) {
        public TacticalCoordinateUiState.Identity identity() { return new TacticalCoordinateUiState.Identity(token, nonce); }
        public Template chosen() { return templates.stream().filter(t -> t.index == selected).findFirst().orElse(null); }
        public boolean invitation() { return phase.equals("invite") || phase.equals("review"); }
    }
    private record Notice(Snapshot snapshot, long expiresAt) {}
    private static final TacticalCoordinateUiState.Receipts RECEIPTS = new TacticalCoordinateUiState.Receipts();
    private static final TacticalCoordinateUiState.ReviewGate REVIEW = new TacticalCoordinateUiState.ReviewGate();
    private static final LinkedHashMap<UUID, Notice> NOTICES = new LinkedHashMap<>();
    private static Object world, player, connection;
    private static long generation, ticks;
    private static HighPowerInput.Input pending;
    private static long pendingAt;
    private static String menuWire = "";
    private static Snapshot queuedReview;
    private static Snapshot menu;
    private TacticalCoordinateClient() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { TacticalCoordinateFormation.clientReceiver = TacticalCoordinateClient::receive; }
    }
    public static net.minecraft.network.chat.MutableComponent text(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    public static String position(Vec3 p) { return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", p.x, p.y, p.z); }
    public static Component reason(String key) {
        String full = PREFIX + "reason." + key;
        return net.minecraft.locale.Language.getInstance().has(full) ? Component.translatable(full) : text("unavailable");
    }
    public static boolean expectOpen(HighPowerInput.Input identity) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (identity == null || pending != null || menu != null || mc.screen != null || mc.player == null || mc.level == null) return false;
        pending = identity; pendingAt = ticks; menuWire = identity.wire(); return true;
    }
    public static void cancelPending() { pending = null; menuWire = menu == null ? "" : menuWire; }
    public static ClassicAbilityHud.DelegateVisual localVisual() { return menu == null ? ClassicAbilityHud.DelegateVisual.IDLE : ClassicAbilityHud.DelegateVisual.ACTIVE; }
    public static Snapshot preview() { return Minecraft.getInstance().screen instanceof TacticalCoordinateScreen s && !s.review() ? menu : null; }
    public static long ticks() { return ticks; }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && player == mc.player && connection == mc.getConnection()) return;
        generation++; pending = null; menu = null; menuWire = ""; REVIEW.clear(); queuedReview = null; NOTICES.clear(); RECEIPTS.clear();
        TacticalCoordinateEffects.clear();
        world = mc.level; player = mc.player; connection = mc.getConnection();
        if (mc.screen instanceof TacticalCoordinateScreen screen) screen.closeFromServer();
    }
    public static void receive(TacticalCoordinateFormation.ClientData packet) {
        Snapshot snapshot = parse(packet == null ? null : packet.data()); if (snapshot == null) return;
        Minecraft mc = Minecraft.getInstance();
        Object capturedWorld = mc.level, capturedPlayer = mc.player, capturedConnection = mc.getConnection();
        long capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || capturedPlayer == null || capturedWorld != mc.level || capturedPlayer != mc.player
                    || capturedConnection != mc.getConnection() || capturedGeneration != generation) return;
            synchronize(mc);
            if (mc.player == null || snapshot.actor != mc.player.getId() || !snapshot.owner.equals(mc.player.getUUID())
                    || !mc.player.isAlive() || mc.player.isRemoved() || !RECEIPTS.accept(snapshot.identity(), snapshot.sequence)) return;
            switch (snapshot.phase) {
                case "menu" -> receiveMenu(mc, snapshot);
                case "invite" -> notice(mc, snapshot);
                case "review" -> receiveReview(mc, snapshot);
                case "success", "end" -> terminal(mc, snapshot);
                default -> { }
            }
        });
    }
    private static void receiveMenu(Minecraft mc, Snapshot snapshot) {
        if (menu != null && menu.identity().equals(snapshot.identity())) {
            menu = snapshot;
            if (mc.screen instanceof TacticalCoordinateScreen screen && screen.matches(snapshot)) screen.update(snapshot);
            return;
        }
        if (pending == null || pending.input() != snapshot.input || pending.epoch() != snapshot.epoch || mc.screen != null) {
            // A delayed opening must not replace chat, an inventory, or an unrelated ability screen.
            if (pending != null && pending.input() == snapshot.input && pending.epoch() == snapshot.epoch) {
                send(snapshot, "close", null, menuWire); pending = null;
            }
            return;
        }
        pending = null; menu = snapshot; mc.setScreen(new TacticalCoordinateScreen(snapshot));
    }
    private static void notice(Minecraft mc, Snapshot snapshot) {
        if (snapshot.remaining <= 0 || snapshot.caster == null || snapshot.templateId == null) return;
        Notice previous = NOTICES.get(snapshot.nonce);
        if (previous != null && !sameInvitation(previous.snapshot,snapshot)) return;
        long expires = TacticalCoordinateUiState.invitationDeadline(ticks,snapshot.remaining,previous == null ? Long.MAX_VALUE : previous.expiresAt);
        NOTICES.put(snapshot.nonce, new Notice(snapshot, expires));
        while (NOTICES.size() > TacticalCoordinateUiState.MAX_NOTICES) NOTICES.remove(NOTICES.keySet().iterator().next());
        if (previous == null) mc.player.displayClientMessage(text("invitation_notice", snapshot.casterName)
                .append(Component.literal(" ")).append(text("review").withStyle(style -> style.withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/aca_tactical_review " + snapshot.nonce)))), false);
    }
    private static void receiveReview(Minecraft mc, Snapshot snapshot) {
        Notice previous = NOTICES.get(snapshot.nonce);
        if (previous == null || previous.expiresAt <= ticks || !sameInvitation(previous.snapshot, snapshot)) return;
        NOTICES.put(snapshot.nonce, new Notice(snapshot, TacticalCoordinateUiState.invitationDeadline(ticks,snapshot.remaining,previous.expiresAt)));
        if (mc.screen instanceof TacticalCoordinateScreen screen && screen.review() && screen.matches(snapshot)) { screen.update(snapshot); return; }
        if (!REVIEW.expects(snapshot.nonce,generation)) return;
        queuedReview = snapshot;
    }
    private static boolean sameInvitation(Snapshot a, Snapshot b) {
        return a.identity().equals(b.identity()) && java.util.Objects.equals(a.caster, b.caster)
                && java.util.Objects.equals(a.templateId, b.templateId) && a.revision == b.revision
                && a.dimension.equals(b.dimension) && java.util.Objects.equals(a.feet,b.feet)
                && Float.compare(a.yaw,b.yaw) == 0 && Float.compare(a.pitch,b.pitch) == 0
                && Double.compare(a.cost,b.cost) == 0 && a.cooldown == b.cooldown;
    }
    private static void terminal(Minecraft mc, Snapshot snapshot) {
        RECEIPTS.retire(snapshot.identity());
        boolean arrived = TacticalCoordinateUiState.confirmedArrival(snapshot.phase,snapshot.reason,snapshot.moved)
                && snapshot.owner.equals(snapshot.movedOwner) && snapshot.from != null && snapshot.feet != null;
        if (menu != null && menu.identity().equals(snapshot.identity())) { menu = null; menuWire = ""; }
        if (pending != null && pending.input() == snapshot.input && pending.epoch() == snapshot.epoch) cancelPending();
        NOTICES.remove(snapshot.nonce);
        REVIEW.cancel(snapshot.nonce);
        if (queuedReview != null && queuedReview.nonce.equals(snapshot.nonce)) queuedReview = null;
        if (mc.screen instanceof TacticalCoordinateScreen screen && screen.matches(snapshot)) screen.closeFromServer();
        if (arrived) {
            TacticalCoordinateEffects.confirm(snapshot.dimension,snapshot.from,snapshot.feet);
            // Invitation recipients have no casting input (0/0); they receive endpoint VFX but never impersonate an LV5 caster.
            if (snapshot.input>0 && snapshot.epoch>0)
                CastPoseClient.acceptedRelease("tactical_coordinate_formation",snapshot.actor,snapshot.owner,
                        snapshot.input,snapshot.epoch,snapshot.token,true);
        }
        if (!snapshot.reason.isEmpty() && !snapshot.reason.equals("closed")) mc.player.displayClientMessage(reason(snapshot.reason), true);
    }
    public static boolean act(Snapshot snapshot, String action, UUID recipient) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || !(mc.screen instanceof TacticalCoordinateScreen screen) || !screen.matches(snapshot)) return false;
        if (screen.review()) {
            Notice notice = NOTICES.get(snapshot.nonce);
            if (notice == null || notice.expiresAt <= ticks || !sameInvitation(notice.snapshot, snapshot)
                    || notice.snapshot.sequence != snapshot.sequence || (!action.equals("accept") && !action.equals("reject"))) return false;
        } else if (menu == null || !menu.identity().equals(snapshot.identity()) || menu.sequence != snapshot.sequence) return false;
        send(snapshot, action, recipient, screen.review() ? "" : menuWire); return true;
    }
    public static boolean select(Snapshot snapshot, int index) {
        if (index < 0 || index >= TacticalCoordinateUiState.SLOTS || menu == null || menu.sequence != snapshot.sequence || !menu.identity().equals(snapshot.identity())) return false;
        send(snapshot, "select", null, menuWire, index); return true;
    }
    private static void send(Snapshot snapshot, String action, UUID recipient, String wire) { send(snapshot, action, recipient, wire, snapshot.selected); }
    private static void send(Snapshot snapshot, String action, UUID recipient, String wire, int index) {
        Template selected = snapshot.templates.stream().filter(t -> t.index == index).findFirst().orElse(null); boolean invitation = snapshot.invitation();
        PacketDistributor.sendToServer(new TacticalCoordinateFormation.Request(wire, snapshot.token, snapshot.nonce, snapshot.sequence,
                action, index, invitation ? snapshot.templateId : selected == null ? null : selected.id,
                invitation ? snapshot.revision : selected == null ? 0 : selected.revision, recipient, snapshot.actor, snapshot.owner));
    }
    public static void closed(Snapshot snapshot, boolean review) {
        if (review) { REVIEW.cancel(snapshot.nonce); if (queuedReview != null && queuedReview.nonce.equals(snapshot.nonce)) queuedReview = null; return; }
        if (menu != null && menu.identity().equals(snapshot.identity())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == player && mc.level == world && mc.getConnection() == connection && mc.getConnection() != null) send(menu, "close", null, menuWire);
            RECEIPTS.retire(menu.identity()); menu = null; menuWire = "";
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_tactical_review").then(Commands.argument("nonce", StringArgumentType.word())
                .executes(context -> requestReview(StringArgumentType.getString(context, "nonce")))));
    }
    private static int requestReview(String value) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        UUID nonce; try { nonce = UUID.fromString(value); } catch (IllegalArgumentException invalid) { return 0; }
        Notice notice = NOTICES.get(nonce);
        if (notice == null || notice.expiresAt <= ticks || mc.player == null) { if (mc.player != null) mc.player.displayClientMessage(text("expired"), true); return 0; }
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) { mc.player.displayClientMessage(text("close_other_menu"), true); return 0; }
        REVIEW.begin(nonce,mc.screen,generation); queuedReview = null;
        send(notice.snapshot, "open_review", null, ""); return 1;
    }
    public static int remaining(Snapshot snapshot) { Notice notice = NOTICES.get(snapshot.nonce); return notice == null ? 0 : (int)Math.max(0, notice.expiresAt - ticks); }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (!mc.isPaused()) ticks++;
        if (pending != null && ticks - pendingAt > 100) cancelPending();
        NOTICES.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt > ticks) return false;
            REVIEW.cancel(entry.getKey());
            if (queuedReview != null && queuedReview.nonce.equals(entry.getKey())) queuedReview = null;
            return true;
        });
        if (mc.player == null || !mc.player.isAlive() || mc.player.isRemoved()) {
            pending = null; NOTICES.clear(); queuedReview = null; REVIEW.clear();
            if (mc.screen instanceof TacticalCoordinateScreen screen) screen.closeFromServer();
            menu = null; return;
        }
        // A clickable RUN_COMMAND leaves vanilla ChatScreen open. Replace only the exact
        // initiating chat after the matching server review arrives; newer navigation cancels it.
        if (REVIEW.active() && !REVIEW.allows(mc.screen,generation)) { REVIEW.clear(); queuedReview = null; }
        if (queuedReview != null && REVIEW.expects(queuedReview.nonce,generation)) {
            if (!NOTICES.containsKey(queuedReview.nonce)) { queuedReview = null; REVIEW.clear(); }
            else if (REVIEW.allows(mc.screen,generation)) {
                Snapshot next = queuedReview; queuedReview = null; REVIEW.clear(); mc.setScreen(new TacticalCoordinateScreen(next));
            }
        }
    }
    @SubscribeEvent public static void notification(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (NOTICES.isEmpty() || mc.player == null || mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        Notice notice = NOTICES.values().stream().reduce((a, b) -> b).orElse(null); if (notice == null) return;
        String line = text("notification", notice.snapshot.casterName, (Math.max(0, notice.expiresAt - ticks) + 19) / 20).getString();
        line = mc.font.plainSubstrByWidth(line, Math.max(100, event.getGuiGraphics().guiWidth() - 24));
        int x = (event.getGuiGraphics().guiWidth() - mc.font.width(line)) / 2;
        event.getGuiGraphics().drawString(mc.font, line, x, 28, 0xFFE2E7E8, true);
    }
    private static Snapshot parse(CompoundTag data) {
        if (data == null) return null;
        try {
            String phase = data.getString("phase"); if (!List.of("menu", "invite", "review", "success", "end").contains(phase)) return null;
            if (!data.hasUUID("owner") || !data.hasUUID("nonce") || data.getLong("token") <= 0 || data.getLong("sequence") < 0) return null;
            int selected = data.getInt("selected"); if (selected < 0 || selected >= 6) return null;
            var templates = new ArrayList<Template>(); var recipients = new ArrayList<Recipient>();
            var rows = data.getList("templates", Tag.TAG_COMPOUND); if (rows.size() > 6) return null;
            boolean[] seen = new boolean[6];
            for (int i = 0; i < rows.size(); i++) {
                CompoundTag row = rows.getCompound(i); int index = row.getInt("index"); Vec3 feet = point(row, "");
                if (index < 0 || index >= 6 || seen[index] || !row.hasUUID("id") || row.getLong("revision") <= 0 || feet == null || !finiteFacing(row)) return null;
                seen[index] = true; templates.add(new Template(index, row.getUUID("id"), row.getLong("revision"), row.getString("dimension"), feet,
                        row.getFloat("yaw"), row.getFloat("pitch"), row.getBoolean("valid"), row.getString("reason")));
            }
            var people = data.getList("recipients", Tag.TAG_COMPOUND); if (people.size() > 32) return null;
            for (int i = 0; i < people.size(); i++) { var row = people.getCompound(i); if (!row.hasUUID("uuid") || row.getString("name").length() > 64) return null; recipients.add(new Recipient(row.getUUID("uuid"), row.getString("name"))); }
            boolean invitation = phase.equals("invite") || phase.equals("review");
            if (invitation && (!data.hasUUID("caster") || !data.hasUUID("template") || data.getLong("revision") <= 0 || point(data, "") == null || !finiteFacing(data))) return null;
            if (!Float.isFinite(data.getFloat("draft_yaw")) || !Double.isFinite(data.getDouble("cost"))) return null;
            return new Snapshot(phase, data.getInt("actor"), data.getUUID("owner"), data.getLong("input"), data.getLong("epoch"),
                    data.getLong("token"), data.getUUID("nonce"), data.getLong("sequence"), selected, data.getString("reason"), data.getString("confirm_reason"), data.getString("dimension"),
                    data.getDouble("cost"), data.getInt("cooldown"), data.getInt("remaining"), data.getFloat("draft_yaw"), data.getBoolean("dirty"),
                    data.getBoolean("storage_valid"), data.getBoolean("can_confirm"), List.copyOf(templates), List.copyOf(recipients),
                    uuid(data, "caster"), data.getString("caster_name"), uuid(data, "template"), data.getLong("revision"), point(data, ""),
                    data.getFloat("yaw"), data.getFloat("pitch"), data.getBoolean("moved"), uuid(data, "moved_owner"), point(data, "from_"));
        } catch (RuntimeException malformed) { return null; }
    }
    private static UUID uuid(CompoundTag data, String key) { return data.hasUUID(key) ? data.getUUID(key) : null; }
    private static boolean finiteFacing(CompoundTag data) { return Float.isFinite(data.getFloat("yaw")) && Float.isFinite(data.getFloat("pitch")); }
    private static Vec3 point(CompoundTag data, String prefix) {
        if (!data.contains(prefix + "x", Tag.TAG_ANY_NUMERIC) || !data.contains(prefix + "y", Tag.TAG_ANY_NUMERIC) || !data.contains(prefix + "z", Tag.TAG_ANY_NUMERIC)) return null;
        double x = data.getDouble(prefix + "x"), y = data.getDouble(prefix + "y"), z = data.getDouble(prefix + "z");
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Math.abs(x) <= 30_000_001 && Math.abs(z) <= 30_000_001 && Math.abs(y) <= 1_000_000 ? new Vec3(x, y, z) : null;
    }
}
