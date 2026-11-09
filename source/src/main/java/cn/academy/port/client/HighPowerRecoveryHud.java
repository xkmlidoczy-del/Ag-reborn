package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import cn.academy.port.highpower.HighPowerRecoveryHudPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** A small read-only ACA instrument panel; CP/overload and native non-health status remain separate. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class HighPowerRecoveryHud {
    private static final HighPowerRecoveryHudModel.Session SESSION = new HighPowerRecoveryHudModel.Session();
    private static final cn.academy.port.highpower.SkillBudgetHudModel.Session BUDGET_SESSION = new cn.academy.port.highpower.SkillBudgetHudModel.Session();
    private static int detailPage, detailPages = 1;
    private static HighPowerRecoveryHudModel.GrowthHint growthHint;
    private static int growthHintAge = HighPowerRecoveryHudModel.STALE_TICKS;
    private static Object growthConnection, growthLevel, growthPlayer;
    private static final int WHITE = 0xffe4edf2, GRAY = 0xffa4b3be;
    private static final float LABEL_SCALE = 2f / 3f, VALUE_SCALE = .75f;
    private HighPowerRecoveryHud() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { HighPowerRecoveryHudPayload.clientReceiver = HighPowerRecoveryHud::receive; cn.academy.port.highpower.SkillBudgetHudPayload.clientReceiver = HighPowerRecoveryHud::receiveBudget; cn.academy.port.highpower.EsperGrowthHintPayload.clientReceiver = HighPowerRecoveryHud::receiveGrowthHint; }
    }
    private static void synchronize(Minecraft mc) { SESSION.synchronize(mc.getConnection(), mc.level, mc.player); BUDGET_SESSION.synchronize(mc.getConnection(), mc.level, mc.player); }
    public static void receive(HighPowerRecoveryHudPayload.Snapshot packet) {
        Minecraft mc = Minecraft.getInstance();
        Object level = mc.level, player = mc.player, connection = mc.getConnection();
        mc.execute(() -> {
            if (level == null || player == null || mc.level != level || mc.player != player || mc.getConnection() != connection) return;
            synchronize(mc);
            SESSION.accept(packet.identity(), packet.values(), mc.player.getUUID(), mc.player.getId(), mc.level.dimension().location().toString());
        });
    }
    public static void receiveBudget(cn.academy.port.highpower.SkillBudgetHudPayload.Snapshot packet) {
        Minecraft mc = Minecraft.getInstance();
        Object level = mc.level, player = mc.player, connection = mc.getConnection();
        mc.execute(() -> {
            if (level == null || player == null || mc.level != level || mc.player != player || mc.getConnection() != connection) return;
            synchronize(mc);
            BUDGET_SESSION.accept(packet.identity(), packet.values(), mc.player.getUUID(), mc.player.getId(), mc.level.dimension().location().toString());
        });
    }
    private static cn.academy.port.highpower.SkillBudgetHudModel.Values budget(HighPowerRecoveryHudModel.Values values) {
        return values == null ? null : BUDGET_SESSION.current(values.category());
    }
    private static boolean presetUsesBudget() {
        return cn.academy.port.highpower.SkillBudgetHudModel.currentPresetUsesBudget(AcademyClient.state);
    }
    private static cn.academy.port.highpower.SkillBudgetHudModel.Values ordinaryBudget(HighPowerRecoveryHudModel.Values values) {
        return presetUsesBudget() ? budget(values) : null;
    }
    public static void receiveGrowthHint(cn.academy.port.highpower.EsperGrowthHintPayload.Hint packet) {
        Minecraft mc = Minecraft.getInstance();
        Object level = mc.level, player = mc.player, connection = mc.getConnection();
        mc.execute(() -> {
            if (level == null || player == null || mc.level != level || mc.player != player || mc.getConnection() != connection
                    || !packet.owner().equals(mc.player.getUUID())) return;
            synchronizeGrowthHint(mc);
            growthHint = packet.hint(); growthHintAge = 0;
        });
    }
    private static void synchronizeGrowthHint(Minecraft mc) {
        if (growthConnection == mc.getConnection() && growthLevel == mc.level && growthPlayer == mc.player) return;
        growthConnection = mc.getConnection(); growthLevel = mc.level; growthPlayer = mc.player; growthHint = null; growthHintAge = HighPowerRecoveryHudModel.STALE_TICKS;
    }
    /** The growth-style line, only while fresh and for the category the exact snapshot and the local ability both agree on. */
    private static HighPowerRecoveryHudModel.GrowthHint growthHint(HighPowerRecoveryHudModel.Values values) {
        var hint = growthHint;
        return hint != null && growthHintAge < HighPowerRecoveryHudModel.STALE_TICKS && hint.category().equals(values.category()) ? hint : null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronize(mc); SESSION.tick(mc.isPaused()); BUDGET_SESSION.tick(mc.isPaused());
        synchronizeGrowthHint(mc); if (!mc.isPaused() && growthHintAge < HighPowerRecoveryHudModel.STALE_TICKS) growthHintAge++;
        if (!available(mc) || !detailHeld(mc)) { detailPage = 0; detailPages = 1; }
    }
    private static boolean detailHeld(Minecraft mc) {
        long window = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }
    @SubscribeEvent public static void detailScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!available(mc) || !detailHeld(mc) || detailPages <= 1
                || layout(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()) == null
                || !Double.isFinite(event.getScrollDeltaY()) || event.getScrollDeltaY() == 0) return;
        detailPage = Math.max(0, Math.min(detailPages - 1, detailPage + (event.getScrollDeltaY() < 0 ? 1 : -1)));
        event.setCanceled(true); // Only ALT + wheel inside an overflowing exact-detail view owns this input.
    }
    private static boolean available(Minecraft mc) {
        synchronize(mc);
        return mc.gui instanceof cn.academy.port.client.highpower.RecoveryHudHeartsAccess
                && mc.player != null && mc.level != null && mc.getConnection() != null && mc.player.isAlive()
                && !mc.player.isRemoved() && !mc.player.isSpectator() && mc.getCameraEntity() == mc.player
                && !mc.options.hideGui && mc.screen == null && !mc.getDebugOverlay().showDebugScreen() && SESSION.current() != null && SESSION.current().category().equals(AcademyClient.state.category);
    }
    /** Called only for vanilla action-bar delivery; other components retain their exact identity. */
    public static Component compactActionBar(Component input) {
        Minecraft mc = Minecraft.getInstance();
        boolean panel = available(mc) && SESSION.current().category().equals("electromaster") && (!presetUsesBudget() || budget(SESSION.current()) != null) && layout(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()) != null;
        return cn.academy.port.highpower.HighPowerStatusSummary.select(input, panel,
                mc.getWindow().getGuiScaledWidth() - 16, mc.font::width);
    }

    private static HighPowerRecoveryHudModel.Presentation presentation(Minecraft mc, int width, int height) {
        boolean config = ClassicHudConfig.SPEC.isLoaded();
        double cpX = config ? ClassicHudConfig.CP_X.get() : ClassicHudTimeline.CP_X;
        double cpY = config ? ClassicHudConfig.CP_Y.get() : ClassicHudTimeline.CP_Y;
        var cpOrigin = ClassicHudTimeline.cpOrigin(width, cpX, cpY);
        var cp = new HighPowerRecoveryHudModel.Box((int)Math.floor(cpOrigin.x()), (int)Math.floor(cpOrigin.y()), 194, 40);
        var keyOrigin = ClassicHudTimeline.keyOrigin(width, height, config ? ClassicHudConfig.KEY_X.get() : 0, config ? ClassicHudConfig.KEY_Y.get() : 30);
        // Reserve all ordinary directional columns as well as four classic delegate rows.
        var keys = new HighPowerRecoveryHudModel.Box((int)keyOrigin.x() - 155, (int)keyOrigin.y(), 185, 88);
        var notification = new HighPowerRecoveryHudModel.Box((int)Math.floor(config ? ClassicHudConfig.NOTIFICATION_X.get() : 0),
                (int)Math.floor(config ? ClassicHudConfig.NOTIFICATION_Y.get() : 15), 130, 43);
        var chat = mc.gui.getChat();
        int chatHeight = (int)Math.ceil(chat.getHeight() * chat.getScale());
        var chatBox = new HighPowerRecoveryHudModel.Box(0, height - 40 - chatHeight, Math.min(width, chat.getWidth() + 16), chatHeight + 12);
        var values = SESSION.current();
        var preferred = values != null && HighPowerRecoveryHudModel.nativePixelText(mc.getWindow().getGuiScale())
                ? nativePlan(mc, values, width) : null;
        return HighPowerRecoveryHudModel.presentation(width, height, cp, keys, notification, chatBox, preferred);
    }
    private static HighPowerRecoveryHudModel.Box layout(Minecraft mc, int width, int height) {
        var chosen = presentation(mc, width, height);
        return chosen == null ? null : chosen.box();
    }
    /** Read-only bounds for the relay panel. Reserve the normal position even while its snapshot is absent. */
    static HighPowerRecoveryHudModel.Box relayReservedBox(int width, int height) {
        return layout(Minecraft.getInstance(), width, height);
    }
    /** Queried only by the pinned client renderHearts adapter. Never hides absorption, air, armor or vehicle layers. */
    public static boolean replacesNativeHearts(Player cameraPlayer) {
        Minecraft mc = Minecraft.getInstance();
        return cameraPlayer == mc.player && available(mc)
                && layout(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()) != null;
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); if (!available(mc)) return;
        GuiGraphics graphics = event.getGuiGraphics();
        var chosen = presentation(mc, graphics.guiWidth(), graphics.guiHeight()); if (chosen == null) return;
        var box = chosen.box();
        var values = SESSION.current(); var accent = values.accent();
        graphics.pose().pushPose();
        try {
            boolean exact = detailHeld(mc);
            boolean nativePixels = HighPowerRecoveryHudModel.nativePixelText(mc.getWindow().getGuiScale());
            if (chosen.nativeText() && !exact) {
                var nativePlan = chosen.nativePlan();
                graphics.pose().translate(box.x(), box.y(), 0);
                renderNative(graphics, mc, values, nativePlan);
                return;
            }
            // ALT keeps its existing fractional-font 176x56 view; the wider native view is reserved only for safe coexistence.
            int paintWidth = nativePixels ? Math.min(176, box.width()) : box.width();
            graphics.pose().translate(box.x() + box.width() - paintWidth, box.y(), 0);
            if (exact) {
                panel(graphics, paintWidth, 56, accent.edge());
                detail(graphics, mc, values, paintWidth, 56);
                return;
            }
            var skillBudget = ordinaryBudget(values);
            var budgetRows = HighPowerRecoveryHudModel.displayRows(values, skillBudget != null);
            var rows = budgetRows.rows();
            int panelHeight = rows.height();
            // Read-only instrument: one quiet dark surface and a thin category accent.
            panel(graphics, box.width(), panelHeight, accent.edge());
            boolean zeroGap = values.zeroGapVisible();
            boolean curtain = values.curtainVisible();
            boolean domain = values.domainVisible() || curtain || zeroGap;
            var numbers = HighPowerRecoveryHudModel.hudNumberLocale(mc.getLanguageManager().getSelected(), (key, fallback) -> tr(key, fallback));
            var health = HighPowerRecoveryHudModel.pool(values.health(), values.maxHealth(), numbers);
            var shield = HighPowerRecoveryHudModel.pool(values.shield(), values.maxShield(), numbers);
            numberRow(graphics, mc, "HP", health.text(), rows.healthY(), WHITE, box.width());
            bar(graphics, 5, rows.healthBarY(), box.width() - 10, HighPowerRecoveryHudModel.barPixels(values.health(), values.maxHealth(), box.width() - 10), WHITE);
            String shieldState = values.shieldHolding() ? tr("shield_holding_short", "ARMOR") : tr("shield_broken", "BROKEN");
            if (values.shieldVisible()) {
                numberRow(graphics, mc, tr("base_shield", "Shield") + " · " + shieldState, shield.text(), rows.shieldY(), accent.primary(), box.width());
                bar(graphics, 5, rows.shieldBarY(), box.width() - 10, HighPowerRecoveryHudModel.barPixels(values.shield(), values.maxShield(), box.width() - 10), values.shieldHolding() ? accent.primary() : accent.idle());
            } else {
                fittedText(graphics, mc, (values.vector() ? tr("vector_shield_unavailable", "No vector shield installed") : tr("teleporter_shield_unavailable", "Shield inactive for TP")), 5, rows.shieldY(), GRAY, box.width() - 10, LABEL_SCALE, false);
            }
            if (domain) {
                String current = zeroGap ? values.zeroGapTotalShield() : curtain ? values.curtainReserve() : values.domainReserve();
                String maximum = zeroGap ? values.zeroGapFullCapacity() : curtain ? values.curtainCapacity() : values.domainCapacity();
                boolean active = zeroGap ? values.zeroGapActive() : curtain ? values.curtainActive() : values.domainActive();
                var reserve = HighPowerRecoveryHudModel.pool(current, maximum, numbers);
                String label = (zeroGap ? tr("zero_gap_total_short", "Total 2S") : curtain ? tr("curtain_reserve_short", "Curtain") : tr("domain_reserve_short", "Reserve"))
                        + " " + (active ? tr("domain_active", "ACTIVE") : zeroGap ? tr("reserve_stored_short", "Stored") : tr("domain_idle", "IDLE"));
                numberRow(graphics, mc, label, reserve.text(), rows.reserveY(), accent.primary(), box.width());
                bar(graphics, 5, rows.reserveBarY(), box.width() - 10, HighPowerRecoveryHudModel.barPixels(current, maximum, box.width() - 10), active ? accent.primary() : accent.idle());
            }
            if (rows.showRate()) {
                String amount = HighPowerRecoveryHudModel.compactRate(values.exactHpPerSecond(), numbers);
                String rate = (values.teleporter() || values.vector()) ? tr("scheduled_rest_rate", "Rest extension +%s HP/s", amount)
                        : tr("scheduled_rate", "Natural +%s HP/s", amount);
                if (values.healing()) rate += "  " + tr("next_pulse", "next %st", values.ticksUntilPulse());
                fittedText(graphics, mc, rate, 5, rows.rateY(), WHITE, box.width() - 10, LABEL_SCALE, false);
            }
            String status = status(values);
            if (HighPowerRecoveryHudModel.ordinaryStatusVisible(values))
                fittedText(graphics, mc, status, 5, rows.statusY(), GRAY, box.width() - 10, LABEL_SCALE, false);
            int pulseY = rows.pulseY();
            if (pulseY >= 0) {
                graphics.fill(5, pulseY, box.width() - 5, pulseY + 1, 0x8055656d);
                graphics.fill(5, pulseY, 5 + (int)Math.floor((box.width() - 10) * values.pulseRatio()), pulseY + 1, accent.pulse());
            }
            if (skillBudget != null) {
                var cp = HighPowerRecoveryHudModel.pool(skillBudget.availableText(), skillBudget.maximumText(), numbers);
                numberRow(graphics, mc, tr("skill_budget_cp_short", "Reserve CP"), cp.text(), budgetRows.budgetY(), accent.primary(), box.width());
            }
        } finally { graphics.pose().popPose(); }
    }
    private static String growthText(HighPowerRecoveryHudModel.GrowthHint hint) {
        return tr(hint.key(), hint.fallback(), hint.arguments().toArray());
    }
    private static String status(HighPowerRecoveryHudModel.Values values) {
        String status = values.status().equals("unlearned") && values.vector()
                ? tr("status.unlearned_vector", "Deflection Strike not learned")
                : values.status().equals("unlearned") && values.teleporter()
                ? tr("status.unlearned_tp", "Safe Landing Probe not learned")
                : values.status().equals("unlearned") && values.meltdowner()
                ? tr("status.unlearned_md", "Dual Point not learned") : tr("status." + values.status(), fallbackStatus(values.status()));
        if (values.status().equals("paused") && values.pauseTicksRemaining() > 0) status += "  " + tr("pause_ticks", "%st remaining", values.pauseTicksRemaining());
        if (values.domainVisible() && MagneticDomainEffects.slowFallingLocal()) status = tr("domain_slow_fall", "Self: slow fall") + " / " + status;
        if (values.curtainVisible() && values.curtainActive()) status = tr("curtain_efficiency", "Curtain / plain arrows ×1.25") + " / " + status;
        if (values.zeroGapVisible() && values.zeroGapActive()) status = tr("zero_gap_coverage", "Up to 3 of 6 plates protect") + " / " + status;
        return status;
    }
    private static HighPowerRecoveryHudModel.NativePlan nativePlan(Minecraft mc, HighPowerRecoveryHudModel.Values values, int width) {
        var numbers = HighPowerRecoveryHudModel.hudNumberLocale(mc.getLanguageManager().getSelected(), (key, fallback) -> tr(key, fallback));
        var sections = new ArrayList<HighPowerRecoveryHudModel.NativeSection>();
        sections.add(new HighPowerRecoveryHudModel.NativeSection("HP", HighPowerRecoveryHudModel.pool(values.health(), values.maxHealth(), numbers).text(), 0));
        if (values.shieldVisible()) {
            String label = tr("base_shield", "Shield") + " · " + (values.shieldHolding() ? tr("shield_holding_short", "ARMOR") : tr("shield_broken", "BROKEN"));
            sections.add(new HighPowerRecoveryHudModel.NativeSection(label, HighPowerRecoveryHudModel.pool(values.shield(), values.maxShield(), numbers).text(), 1));
        } else sections.add(new HighPowerRecoveryHudModel.NativeSection(values.vector()
                ? tr("vector_shield_unavailable", "No vector shield installed") : tr("teleporter_shield_unavailable", "Shield inactive for TP"), null, 4));
        boolean zeroGap = values.zeroGapVisible(), curtain = values.curtainVisible();
        if (values.domainVisible() || curtain || zeroGap) {
            String current = zeroGap ? values.zeroGapTotalShield() : curtain ? values.curtainReserve() : values.domainReserve();
            String maximum = zeroGap ? values.zeroGapFullCapacity() : curtain ? values.curtainCapacity() : values.domainCapacity();
            boolean active = zeroGap ? values.zeroGapActive() : curtain ? values.curtainActive() : values.domainActive();
            String label = (zeroGap ? tr("zero_gap_total_short", "Total 2S") : curtain ? tr("curtain_reserve_short", "Curtain") : tr("domain_reserve_short", "Reserve"))
                    + " " + (active ? tr("domain_active", "ACTIVE") : zeroGap ? tr("reserve_stored_short", "Stored") : tr("domain_idle", "IDLE"));
            sections.add(new HighPowerRecoveryHudModel.NativeSection(label, HighPowerRecoveryHudModel.pool(current, maximum, numbers).text(), 2));
        }
        if (HighPowerRecoveryHudModel.compactRows(values).showRate()) {
            String amount = HighPowerRecoveryHudModel.compactRate(values.exactHpPerSecond(), numbers);
            String rate = values.teleporter() || values.vector() ? tr("scheduled_rest_rate", "Rest extension +%s HP/s", amount)
                    : tr("scheduled_rate", "Natural +%s HP/s", amount);
            if (values.healing()) rate += "  " + tr("next_pulse", "next %st", values.ticksUntilPulse());
            sections.add(new HighPowerRecoveryHudModel.NativeSection(rate, null, 5));
        }
        var skillBudget = ordinaryBudget(values);
        if (skillBudget != null) sections.add(new HighPowerRecoveryHudModel.NativeSection(tr("skill_budget_cp_short", "Reserve CP"),
                HighPowerRecoveryHudModel.pool(skillBudget.availableText(), skillBudget.maximumText(), numbers).text(), 6));
        if (HighPowerRecoveryHudModel.ordinaryStatusVisible(values))
            sections.add(new HighPowerRecoveryHudModel.NativeSection(status(values), null, 4));
        return HighPowerRecoveryHudModel.nativePlan(sections, width, mc.font.lineHeight,
                (text, available) -> wrapNativeText(mc, text, available), mc.font::width);
    }
    private static List<String> wrapNativeText(Minecraft mc, String text, int width) {
        var lines = new ArrayList<String>();
        while (!text.isEmpty() && lines.size() < 32) {
            String part = mc.font.plainSubstrByWidth(text, width);
            if (part.isEmpty()) return List.of();
            lines.add(part); text = text.substring(part.length());
        }
        return text.isEmpty() ? lines : List.of();
    }
    /** Unscaled native-font branch: every glyph and baseline is at an integer GUI coordinate. */
    private static void renderNative(GuiGraphics graphics, Minecraft mc, HighPowerRecoveryHudModel.Values values,
                                     HighPowerRecoveryHudModel.NativePlan plan) {
        var accent = values.accent();
        boolean showPulse = values.healing() && HighPowerRecoveryHudModel.rate(values.exactHpPerSecond()).signum() > 0;
        int paintHeight = HighPowerRecoveryHudModel.nativePaintHeight(plan, mc.font.lineHeight, showPulse);
        panel(graphics, plan.width(), paintHeight, accent.edge());
        for (var line : plan.text()) graphics.drawString(mc.font, line.text(), line.x(), line.y(), line.ink() == 1 ? WHITE : line.ink() == 2 ? accent.primary() : GRAY, false);
        for (var meter : plan.meters()) {
            String current, maximum; boolean active;
            if (meter.role() == 0) { current = values.health(); maximum = values.maxHealth(); active = true; }
            else if (meter.role() == 1) { current = values.shield(); maximum = values.maxShield(); active = values.shieldHolding(); }
            else {
                current = values.zeroGapVisible() ? values.zeroGapTotalShield() : values.curtainVisible() ? values.curtainReserve() : values.domainReserve();
                maximum = values.zeroGapVisible() ? values.zeroGapFullCapacity() : values.curtainVisible() ? values.curtainCapacity() : values.domainCapacity();
                active = values.zeroGapVisible() ? values.zeroGapActive() : values.curtainVisible() ? values.curtainActive() : values.domainActive();
            }
            bar(graphics, 5, meter.y(), plan.width() - 10, HighPowerRecoveryHudModel.barPixels(current, maximum, plan.width() - 10),
                    meter.role() == 0 ? WHITE : active ? accent.primary() : accent.idle());
        }
        if (showPulse) {
            graphics.fill(5, plan.pulseY(), plan.width() - 5, plan.pulseY() + 1, 0x8055656d);
            graphics.fill(5, plan.pulseY(), 5 + (int)Math.floor((plan.width() - 10) * values.pulseRatio()), plan.pulseY() + 1, accent.pulse());
        }
    }
    private static void panel(GuiGraphics graphics, int width, int height, int accent) {
        graphics.fill(0, 0, width, height, 0xb0081016);
        graphics.fill(0, 0, 1, height, accent);
        graphics.fill(1, 0, width, 1, 0x305e7684);
    }
    private static void bar(GuiGraphics graphics, int x, int y, int width, int filled, int color) {
        graphics.fill(x, y, x + width, y + 1, 0x60495660);
        graphics.fill(x, y, x + Math.max(0, Math.min(width, filled)), y + 1, color);
    }
    /** Stable left labels and right-aligned complete numeric pairs; no number is cut by substring clipping. */
    private static void numberRow(GuiGraphics graphics, Minecraft mc, String label, String value, int y, int color, int width) {
        var row = HighPowerRecoveryHudModel.numberRow(width, mc.font.width(label), mc.font.width(value));
        fittedText(graphics, mc, label, 5, y, GRAY, row.labelWidth(), LABEL_SCALE, false);
        fittedText(graphics, mc, value, row.valueX(), y, color, row.valueWidth(), VALUE_SCALE, true);
    }
    private static void fittedText(GuiGraphics graphics, Minecraft mc, String value, int x, int y, int color, int width, float preferredScale, boolean rightAligned) {
        int textWidth = mc.font.width(value);
        float scale = HighPowerRecoveryHudModel.fittedScale(width, textWidth, preferredScale);
        if (scale <= 0) return;
        graphics.pose().pushPose();
        try {
            double guiScale = mc.getWindow().getGuiScale();
            double drawX = rightAligned ? x + width - textWidth * scale : x;
            // Snap the baseline origin to a physical pixel without rounding a numeric right edge outside its box.
            if (Double.isFinite(guiScale) && guiScale > 0) drawX = Math.floor(drawX * guiScale) / guiScale;
            graphics.pose().translate(drawX, y, 0); graphics.pose().scale(scale, scale, 1);
            graphics.drawString(mc.font, value, 0, 0, color, false);
        } finally { graphics.pose().popPose(); }
    }
    private static void text(GuiGraphics graphics, Minecraft mc, String text, int x, int y, int color, int width, float scale) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0); graphics.pose().scale(scale, scale, 1);
            graphics.drawString(mc.font, mc.font.plainSubstrByWidth(text, (int)(width / scale)), 0, 0, color, false);
        } finally { graphics.pose().popPose(); }
    }
    private static void detail(GuiGraphics graphics, Minecraft mc, HighPowerRecoveryHudModel.Values values, int width, int height) {
        // Exact strings are wrapped in full, never converted through float/double or silently abbreviated.
        List<String> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>(List.of("ACA / " + values.categoryLabel(),
                tr("health", "Health") + " " + values.health() + " / " + values.maxHealth()));
        if (values.shieldVisible()) {
            fields.add(tr(values.shieldKey(), tr("base_shield", "Shield")) + " " + values.shield() + " / " + values.maxShield());
            fields.add(values.shieldHolding() ? tr("super_armor", "SUPER ARMOR") : tr("shield_broken", "BROKEN"));
        }
        if (values.domainVisible()) fields.add(tr("domain_reserve", "Domain reserve") + " " + values.domainReserve() + " / " + values.domainCapacity());
        if (values.curtainVisible()) fields.add(tr("curtain_reserve", "Curtain reserve") + " " + values.curtainReserve() + " / " + values.curtainCapacity());
        if (values.zeroGapVisible()) {
            fields.add(tr("zero_gap_reserve", "Armor reserve") + " " + values.zeroGapReserve() + " / " + values.zeroGapCapacity());
            fields.add(tr("zero_gap_total", "Combined protection") + " " + values.zeroGapTotalShield() + " / " + values.zeroGapFullCapacity());
        }
        var skillBudget = budget(values);
        if (skillBudget != null) {
            fields.add(tr("skill_budget_cp", "Skill reserve CP") + " " + skillBudget.availableText() + " / " + skillBudget.maximumText());
            if (skillBudget.reserved()) fields.add(tr("skill_budget_reserved", "Reserved CP") + " " + skillBudget.reservedText()
                    + " · " + tr("skill_budget_total", "Total CP") + " " + skillBudget.totalText());
        }
        if (values.healing() && HighPowerRecoveryHudModel.rate(values.exactHpPerSecond()).signum() > 0)
            fields.add(tr("scheduled_rate", "Natural +%s HP/s", values.exactHpPerSecond()));
        fields.add(status(values));
        var hint = growthHint(values);
        if (hint != null) fields.add(growthText(hint));
        for (String line : fields) {
            while (!line.isEmpty()) {
                String part = mc.font.plainSubstrByWidth(line, (int)((width - 10) / .7));
                if (part.isEmpty()) break;
                rows.add(part); line = line.substring(part.length());
            }
        }
        int rowsPerPage = Math.max(1, (int)((height - 16) / (9 * .7)));
        detailPages = Math.max(1, (rows.size() + rowsPerPage - 1) / rowsPerPage);
        detailPage = Math.max(0, Math.min(detailPages - 1, detailPage));
        String title = tr("exact", "Exact server snapshot");
        if (detailPages > 1) title += "  " + (detailPage + 1) + "/" + detailPages + "  " + tr("scroll", "ALT+wheel");
        text(graphics, mc, title, 5, 4, GRAY, width - 10, .7f);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(5, 13, 0); graphics.pose().scale(.7f, .7f, 1);
            for (int i = detailPage * rowsPerPage; i < Math.min(rows.size(), (detailPage + 1) * rowsPerPage); i++)
                graphics.drawString(mc.font, rows.get(i), 0, (i % rowsPerPage) * 9, WHITE, false);
        } finally { graphics.pose().popPose(); }
    }
    private static String tr(String key, String fallback, Object... arguments) {
        return Component.translatableWithFallback("hud.academy.highpower." + key, fallback, arguments).getString();
    }
    private static String fallbackStatus(String status) {
        return switch (status) {
            case "saturated" -> "Saturated recovery";
            case "slow" -> "Food recovery";
            case "paused" -> "Combat pause";
            case "unlearned" -> "Steady Breathing not learned";
            case "unsupported" -> "Recovery unavailable";
            case "inactive" -> "Recovery disabled";
            case "gamerule_off" -> "Natural regeneration disabled";
            case "food_low" -> "Food below 18";
            case "dead" -> "No recovery while dead";
            default -> "HP full";
        };
    }
}
