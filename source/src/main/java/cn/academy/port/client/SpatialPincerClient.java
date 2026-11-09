package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.SpatialPincerConsent;
import cn.academy.port.highpower.core.SpatialPincerCorridorRules;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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

/** Explicit named teammate selection and recipient consent. Notices never open a screen. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class SpatialPincerClient {
    public static final String PREFIX = "academy.highpower.spatial_pincer_corridor.";
    public record Recipient(UUID id, String name) {}
    public record Snapshot(String phase, int actor, UUID owner, long input, long epoch, long token, UUID nonce,
                           long sequence, String reason, long planToken, long revision, String dimension,
                           Vec3 source, Vec3 destination, float yaw, float pitch, double cost, int cooldown,
                           int remaining, int planRemaining, UUID caster, String casterName, UUID recipient,
                           String recipientName, List<Recipient> recipients, boolean invitationPending,
                           boolean moved, UUID movedOwner, int movedActor, Vec3 from) {
        public SpatialPincerUiState.Identity identity() { return new SpatialPincerUiState.Identity(token, nonce); }
        public boolean invitation() { return phase.equals("invite") || phase.equals("review"); }
    }
    private record Notice(Snapshot snapshot, long expiresAt) {}
    private static final SpatialPincerUiState.Receipts RECEIPTS = new SpatialPincerUiState.Receipts();
    private static final SpatialPincerUiState.ReviewGate REVIEW = new SpatialPincerUiState.ReviewGate();
    private static final LinkedHashMap<UUID, Notice> NOTICES = new LinkedHashMap<>();
    private static Object world, player, connection;
    private static long generation, ticks, pendingAt, menuExpiresAt;
    private static HighPowerInput.Input pending;
    private static String menuWire = "";
    private static Snapshot queuedReview, menu;
    private SpatialPincerClient() {}

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { SpatialPincerConsent.clientReceiver = SpatialPincerClient::receive; }
    }
    public static net.minecraft.network.chat.MutableComponent text(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    public static String position(Vec3 p) { return p == null ? "?" : String.format(Locale.ROOT, "%.1f, %.1f, %.1f", p.x, p.y, p.z); }
    public static Component reason(String key) {
        String full = PREFIX + "reason." + key;
        return net.minecraft.locale.Language.getInstance().has(full) ? Component.translatable(full) : text("unavailable");
    }
    /** Called for the locally released crouch gesture before its authenticated request is sent. */
    public static boolean expectOpen(HighPowerInput.Input identity) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (identity == null || pending != null || menu != null || mc.screen != null || mc.player == null || mc.level == null) return false;
        pending = identity; pendingAt = ticks; menuWire = identity.wire(); return true;
    }
    public static void cancelPending() { pending = null; if (menu == null) menuWire = ""; }
    public static boolean ownsPicker() {
        Minecraft mc = Minecraft.getInstance();
        return menu != null && menuExpiresAt > ticks && world == mc.level && player == mc.player && connection == mc.getConnection()
                && mc.screen instanceof SpatialPincerScreen screen && !screen.review() && screen.matches(menu);
    }
    public static long ticks() { return ticks; }
    public static void clear() {
        generation++; pending = null; menu = null; menuWire = ""; menuExpiresAt = 0;
        REVIEW.clear(); queuedReview = null; NOTICES.clear(); RECEIPTS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SpatialPincerScreen screen) screen.closeFromServer();
    }
    private static void synchronize(Minecraft mc) {
        if (world == mc.level && player == mc.player && connection == mc.getConnection()) return;
        clear(); world = mc.level; player = mc.player; connection = mc.getConnection();
    }
    public static void receive(SpatialPincerConsent.ClientData packet) {
        Snapshot snapshot = parse(packet == null ? null : packet.data()); if (snapshot == null) return;
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        Object capturedWorld = mc.level, capturedPlayer = mc.player, capturedConnection = mc.getConnection();
        long capturedGeneration = generation;
        mc.execute(() -> {
            if (capturedWorld == null || capturedPlayer == null || capturedWorld != mc.level || capturedPlayer != mc.player
                    || capturedConnection != mc.getConnection() || capturedGeneration != generation) return;
            if (snapshot.actor != mc.player.getId() || !snapshot.owner.equals(mc.player.getUUID()) || !mc.player.isAlive()
                    || mc.player.isRemoved() || !snapshot.dimension.equals(mc.level.dimension().location().toString())
                    || !RECEIPTS.accept(snapshot.identity(), snapshot.sequence)) return;
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
            if (!samePlan(menu, snapshot)) return;
            menu = snapshot; menuExpiresAt = Math.min(menuExpiresAt, ticks + snapshot.planRemaining);
            if (mc.screen instanceof SpatialPincerScreen screen && screen.matches(snapshot)) screen.update(snapshot);
            return;
        }
        if (pending == null || pending.input() != snapshot.input || pending.epoch() != snapshot.epoch || mc.screen != null
                || !AcademyClient.singleOwnerEpochMatches(snapshot.epoch)) {
            if (pending != null && pending.input() == snapshot.input && pending.epoch() == snapshot.epoch) {
                send(snapshot, "close", null, menuWire); cancelPending();
            }
            return;
        }
        pending = null; menu = snapshot; menuExpiresAt = ticks + snapshot.planRemaining;
        mc.setScreen(new SpatialPincerScreen(snapshot));
    }
    private static void notice(Minecraft mc, Snapshot snapshot) {
        if (snapshot.remaining <= 0) return;
        Notice previous = NOTICES.get(snapshot.nonce);
        if (previous != null && !sameInvitation(previous.snapshot, snapshot)) return;
        long expires = SpatialPincerUiState.invitationDeadline(ticks, Math.min(snapshot.remaining, snapshot.planRemaining), previous == null ? Long.MAX_VALUE : previous.expiresAt);
        NOTICES.put(snapshot.nonce, new Notice(snapshot, expires));
        while (NOTICES.size() > SpatialPincerUiState.MAX_NOTICES) NOTICES.remove(NOTICES.keySet().iterator().next());
        if (previous == null) mc.player.displayClientMessage(text("invitation_notice", snapshot.casterName)
                .append(Component.literal(" ")).append(text("review").withStyle(style -> style.withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/aca_pincer_review " + snapshot.nonce)))), false);
    }
    private static void receiveReview(Minecraft mc, Snapshot snapshot) {
        Notice previous = NOTICES.get(snapshot.nonce);
        if (previous == null || previous.expiresAt <= ticks || !sameInvitation(previous.snapshot, snapshot)) return;
        NOTICES.put(snapshot.nonce, new Notice(snapshot, SpatialPincerUiState.invitationDeadline(ticks, Math.min(snapshot.remaining, snapshot.planRemaining), previous.expiresAt)));
        if (mc.screen instanceof SpatialPincerScreen screen && screen.review() && screen.matches(snapshot)) { screen.update(snapshot); return; }
        if (REVIEW.expects(snapshot.nonce, generation)) queuedReview = snapshot;
    }
    private static boolean samePlan(Snapshot a, Snapshot b) {
        return a.planToken == b.planToken && a.revision == b.revision && a.dimension.equals(b.dimension)
                && Objects.equals(a.source, b.source) && Objects.equals(a.destination, b.destination)
                && Float.compare(a.yaw, b.yaw) == 0 && Float.compare(a.pitch, b.pitch) == 0
                && Double.compare(a.cost, b.cost) == 0 && a.cooldown == b.cooldown;
    }
    private static boolean sameInvitation(Snapshot a, Snapshot b) {
        return a.identity().equals(b.identity()) && samePlan(a, b) && Objects.equals(a.caster, b.caster)
                && Objects.equals(a.recipient, b.recipient) && a.owner.equals(b.owner);
    }
    private static void terminal(Minecraft mc, Snapshot snapshot) {
        RECEIPTS.retire(snapshot.identity());
        if (SpatialPincerUiState.confirmedArrival(snapshot.phase, snapshot.reason, snapshot.moved)
                && snapshot.owner.equals(snapshot.movedOwner) && snapshot.movedActor == mc.player.getId()
                && snapshot.from != null && snapshot.destination != null)
            SpatialPincerEffects.confirm(snapshot.dimension, snapshot.from, snapshot.destination);
        if (menu != null && menu.identity().equals(snapshot.identity())) { menu = null; menuWire = ""; menuExpiresAt = 0; }
        if (pending != null && pending.input() == snapshot.input && pending.epoch() == snapshot.epoch) cancelPending();
        NOTICES.remove(snapshot.nonce); REVIEW.cancel(snapshot.nonce);
        if (queuedReview != null && queuedReview.nonce.equals(snapshot.nonce)) queuedReview = null;
        if (mc.screen instanceof SpatialPincerScreen screen && screen.matches(snapshot)) screen.closeFromServer();
        if (!snapshot.reason.isEmpty() && !snapshot.reason.equals("closed")) mc.player.displayClientMessage(reason(snapshot.reason), true);
    }
    public static boolean act(Snapshot snapshot, String action, UUID recipient) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        if (mc.player == null || !(mc.screen instanceof SpatialPincerScreen screen) || !screen.matches(snapshot)) return false;
        if (screen.review()) {
            Notice notice = NOTICES.get(snapshot.nonce);
            if (notice == null || notice.expiresAt <= ticks || !sameInvitation(notice.snapshot, snapshot)
                    || notice.snapshot.sequence != snapshot.sequence || (!action.equals("accept") && !action.equals("reject"))) return false;
        } else if (menu == null || !menu.identity().equals(snapshot.identity()) || menu.sequence != snapshot.sequence
                || menuExpiresAt <= ticks || !action.equals("invite") || snapshot.invitationPending || recipient == null
                || snapshot.recipients.stream().noneMatch(person -> person.id.equals(recipient))) return false;
        send(snapshot, action, recipient, screen.review() ? "" : menuWire); return true;
    }
    private static void send(Snapshot snapshot, String action, UUID recipient, String wire) {
        PacketDistributor.sendToServer(new SpatialPincerConsent.Request(wire, snapshot.token, snapshot.nonce, snapshot.sequence,
                action, snapshot.planToken, snapshot.revision, recipient, snapshot.actor, snapshot.owner));
    }
    public static void closed(Snapshot snapshot, boolean review) {
        if (review) { REVIEW.cancel(snapshot.nonce); if (queuedReview != null && queuedReview.nonce.equals(snapshot.nonce)) queuedReview = null; return; }
        if (menu != null && menu.identity().equals(snapshot.identity())) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == player && mc.level == world && mc.getConnection() == connection && connection != null) send(menu, "close", null, menuWire);
            RECEIPTS.retire(menu.identity()); menu = null; menuWire = ""; menuExpiresAt = 0;
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_pincer_review").then(Commands.argument("nonce", StringArgumentType.word())
                .executes(context -> requestReview(StringArgumentType.getString(context, "nonce")))));
    }
    private static int requestReview(String value) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc);
        UUID nonce; try { nonce = UUID.fromString(value); } catch (IllegalArgumentException invalid) { return 0; }
        Notice notice = NOTICES.get(nonce);
        if (notice == null || notice.expiresAt <= ticks || mc.player == null) { if (mc.player != null) mc.player.displayClientMessage(text("expired"), true); return 0; }
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) { mc.player.displayClientMessage(text("close_other_menu"), true); return 0; }
        REVIEW.begin(nonce, mc.screen, generation); queuedReview = null;
        send(notice.snapshot, "open_review", null, ""); return 1;
    }
    public static int remaining(Snapshot snapshot) {
        if (!snapshot.invitation()) return menu != null && menu.identity().equals(snapshot.identity()) ? (int)Math.max(0, menuExpiresAt - ticks) : 0;
        Notice notice = NOTICES.get(snapshot.nonce); return notice == null ? 0 : (int)Math.max(0, notice.expiresAt - ticks);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); if (!mc.isPaused()) ticks++;
        if (pending != null && ticks - pendingAt > 100) cancelPending();
        NOTICES.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt > ticks) return false;
            REVIEW.cancel(entry.getKey());
            if (queuedReview != null && queuedReview.nonce.equals(entry.getKey())) queuedReview = null;
            return true;
        });
        if (mc.player == null || !mc.player.isAlive() || mc.player.isRemoved()) { clear(); return; }
        if (REVIEW.active() && !REVIEW.allows(mc.screen, generation)) { REVIEW.clear(); queuedReview = null; }
        if (queuedReview != null && REVIEW.expects(queuedReview.nonce, generation)) {
            if (!NOTICES.containsKey(queuedReview.nonce)) { queuedReview = null; REVIEW.clear(); }
            else if (REVIEW.allows(mc.screen, generation)) {
                Snapshot next = queuedReview; queuedReview = null; REVIEW.clear(); mc.setScreen(new SpatialPincerScreen(next));
            }
        }
    }
    @SubscribeEvent public static void notification(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (NOTICES.isEmpty() || mc.player == null || mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        Notice notice = NOTICES.values().stream().reduce((a, b) -> b).orElse(null); if (notice == null) return;
        String line = text("notification", notice.snapshot.casterName, (Math.max(0, notice.expiresAt - ticks) + 19) / 20).getString();
        line = mc.font.plainSubstrByWidth(line, Math.max(100, event.getGuiGraphics().guiWidth() - 24));
        event.getGuiGraphics().drawString(mc.font, line, (event.getGuiGraphics().guiWidth() - mc.font.width(line)) / 2, 28, 0xFFE2E7E8, true);
    }
    private static Snapshot parse(CompoundTag data) {
        if (data == null) return null;
        try {
            String phase = data.getString("phase"); if (!List.of("menu", "invite", "review", "success", "end").contains(phase)) return null;
            if (!data.hasUUID("owner") || !data.hasUUID("nonce") || data.getLong("token") <= 0 || data.getLong("sequence") <= 0
                    || data.getInt("actor") < 0 || data.getLong("plan_token") <= 0 || data.getLong("revision") <= 0
                    || data.getString("reason").length() > 128 || data.getString("dimension").length() > 256) return null;
            Vec3 source = point(data, "a_"), destination = point(data, "");
            if (source == null || destination == null || !Float.isFinite(data.getFloat("yaw")) || !Float.isFinite(data.getFloat("pitch"))
                    || !Double.isFinite(data.getDouble("cost")) || data.getDouble("cost") < 0 || data.getInt("cooldown") < 0) return null;
            int remaining = data.getInt("remaining"), planRemaining = data.getInt("plan_remaining");
            if (remaining < 0 || remaining > SpatialPincerCorridorRules.PREPARATION_TICKS
                    || planRemaining < 0 || planRemaining > SpatialPincerCorridorRules.PREPARATION_TICKS) return null;
            var recipients = new ArrayList<Recipient>(); var people = data.getList("recipients", Tag.TAG_COMPOUND);
            if (people.size() > SpatialPincerUiState.MAX_RECIPIENTS) return null;
            var ids = new java.util.HashSet<UUID>();
            for (int i = 0; i < people.size(); i++) {
                var row = people.getCompound(i);
                if (!row.hasUUID("uuid") || row.getString("name").length() > 64 || !ids.add(row.getUUID("uuid"))) return null;
                recipients.add(new Recipient(row.getUUID("uuid"), row.getString("name")));
            }
            boolean invitation = phase.equals("invite") || phase.equals("review");
            if (invitation && (!data.hasUUID("caster") || !data.hasUUID("recipient") || !data.getUUID("recipient").equals(data.getUUID("owner"))
                    || remaining > SpatialPincerCorridorRules.INVITATION_TICKS || data.getString("caster_name").length() > 64)) return null;
            return new Snapshot(phase, data.getInt("actor"), data.getUUID("owner"), data.getLong("input"), data.getLong("epoch"),
                    data.getLong("token"), data.getUUID("nonce"), data.getLong("sequence"), data.getString("reason"), data.getLong("plan_token"),
                    data.getLong("revision"), data.getString("dimension"), source, destination, data.getFloat("yaw"), data.getFloat("pitch"),
                    data.getDouble("cost"), data.getInt("cooldown"), remaining, planRemaining, uuid(data, "caster"), data.getString("caster_name"),
                    uuid(data, "recipient"), data.getString("recipient_name"), List.copyOf(recipients), data.getBoolean("invitation_pending"),
                    data.getBoolean("moved"), uuid(data, "moved_owner"), data.getInt("moved_actor"), point(data, "from_"));
        } catch (RuntimeException malformed) { return null; }
    }
    private static UUID uuid(CompoundTag data, String key) { return data.hasUUID(key) ? data.getUUID(key) : null; }
    private static Vec3 point(CompoundTag data, String prefix) {
        if (!data.contains(prefix + "x", Tag.TAG_ANY_NUMERIC) || !data.contains(prefix + "y", Tag.TAG_ANY_NUMERIC) || !data.contains(prefix + "z", Tag.TAG_ANY_NUMERIC)) return null;
        double x = data.getDouble(prefix + "x"), y = data.getDouble(prefix + "y"), z = data.getDouble(prefix + "z");
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Math.abs(x) <= 30_000_001 && Math.abs(z) <= 30_000_001 && Math.abs(y) <= 1_000_000 ? new Vec3(x, y, z) : null;
    }
}
