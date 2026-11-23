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
    private static final Trail HEALTH_TRAIL = new Trail(), SHIELD_TRAIL = new Trail();
    private static final ClassicTerminalDrawing FONT = TerminalSkin.FONT;
    private static long trailEpoch;
    private record Readout(HighPowerRecoveryHudModel.Values source,String language,String health,String shield,String label) {}
    private static Readout cached;
    private static Readout readout(Minecraft mc, HighPowerRecoveryHudModel.Values v) {
        String lang=mc.getLanguageManager().getSelected();
        if(cached!=null && cached.source()==v && lang.equals(cached.language())) return cached;
        var units=HighPowerRecoveryHudModel.hudNumberLocale(lang,(key,fallback)->tr(key,fallback));
        String state=v.superArmor()?tr("super_armor","Super armor"):HighPowerRecoveryHudModel.decimal(v.defense()).signum()==0?
                tr("shield_broken","Broken"):tr("shield_standby","Standby");
        String label=v.hasDna() && HighPowerRecoveryHudModel.decimal(v.dnaShield()).signum()>0?
                tr("dna_short","DNA shield"):tr("base_shield","Shield");
        cached=new Readout(v,lang,HighPowerRecoveryHudModel.pool(v.health(),v.maxHealth(),units).text(),
                HighPowerRecoveryHudModel.pool(v.defense(),v.defenseCapacity(),units).text(),label+" · "+state);
        return cached;
    }
    private static final class Trail {
        double solid, previous;
        long lostAt;
        void update(double ratio,long now,boolean reset) {
            if (reset || ratio > solid) previous=ratio;
            else if (ratio < solid) { previous=Math.max(solid,visible(now)); lostAt=now; }
            solid=ratio;
        }
        double visible(long now) {
            return solid+(previous-solid)*Math.clamp(1-(now-lostAt)/160d,0,1);
        }
    }
    private static final int WHITE = 0xffe4edf2, GRAY = 0xffa4b3be;
    private static final float LABEL_SCALE = 2f / 3f, VALUE_SCALE = .75f;
    private HighPowerRecoveryHud() {}
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void resources(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> {
                TerminalSkin.releaseReadout();
                TerminalSkin.prepareReadout();
            });
        }
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { HighPowerRecoveryHudPayload.clientReceiver = HighPowerRecoveryHud::receive; cn.academy.port.highpower.SkillBudgetHudPayload.clientReceiver = HighPowerRecoveryHud::receiveBudget; cn.academy.port.highpower.EsperGrowthHintPayload.clientReceiver = HighPowerRecoveryHud::receiveGrowthHint; }
    }
    @SubscribeEvent public static void prepareTitle(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen) TerminalSkin.prepareReadout();
    }

    @SubscribeEvent public static void prepareConnection(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        TerminalSkin.prepareReadout();
    }

    private static void synchronize(Minecraft mc) { SESSION.synchronize(mc.getConnection(), mc.level, mc.player); BUDGET_SESSION.synchronize(mc.getConnection(), mc.level, mc.player); }
    public static void receive(HighPowerRecoveryHudPayload.Snapshot packet) {
        Minecraft mc = Minecraft.getInstance();
        Object level = mc.level, player = mc.player, connection = mc.getConnection();
        mc.execute(() -> {
            if (level == null || player == null || mc.level != level || mc.player != player || mc.getConnection() != connection) return;
            synchronize(mc);
            boolean reset=SESSION.current()==null || trailEpoch!=packet.identity().epoch();
            if (!SESSION.accept(packet.identity(), packet.values(), mc.player.getUUID(), mc.player.getId(), mc.level.dimension().location().toString())) return;
            var v=packet.values();
            if(v!=null) {
                long now=net.minecraft.Util.getMillis();
                HEALTH_TRAIL.update(v.healthRatio(),now,reset);
                SHIELD_TRAIL.update(HighPowerRecoveryHudModel.ratio(v.defense(),v.defenseCapacity()),now,reset);
            }
            trailEpoch=packet.identity().epoch();
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
        var current = presetUsesBudget() ? budget(values) : null;
        return current != null && (current.reserved() || current.available() < current.maximum()) ? current : null;
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
        var terminalKey=cn.academy.port.client.terminal.TerminalClient.OPEN;
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT)
                || terminalKey.isDown() && (terminalKey.matches(GLFW.GLFW_KEY_LEFT_ALT,0) || terminalKey.matches(GLFW.GLFW_KEY_RIGHT_ALT,0));
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
                && !mc.options.hideGui && mc.screen == null && !mc.getDebugOverlay().showDebugScreen() && SESSION.current() != null && (SESSION.current().category().equals("equipment") || SESSION.current().category().equals(AcademyClient.state.category));
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
        return HighPowerRecoveryHudModel.presentation(width, height, cp, keys, notification, chatBox, null);
    }
    private static HighPowerRecoveryHudModel.Box layout(Minecraft mc, int width, int height) {
        var chosen = presentation(mc, width, height);
        return chosen == null ? null : chosen.box();
    }
    /** Read-only bounds for the relay panel. Reserve the normal position even while its snapshot is absent. */
    public static HighPowerRecoveryHudModel.Box relayReservedBox(int width, int height) {
        return layout(Minecraft.getInstance(), width, height);
    }
    public static String compactNativeHealth(float health) {
        if (!Float.isFinite(health) || health <= 0) return "0";
        var decimal = new java.math.BigDecimal(Float.toString(health))
                .min(cn.academy.port.highpower.core.CombatAmounts.CAP);
        return HighPowerRecoveryHudModel.compact(decimal.stripTrailingZeros().toPlainString());
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
        var v = SESSION.current();
        int width=box.width(), edge=v.accent().primary();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(box.x(),box.y(),0);
            if (detailHeld(mc)) {
                TerminalSkin.draw(graphics,TerminalSkin.FRAME,0,0,width,56,-1);
                detail(graphics,mc,v,width,56);
                return;
            }
            boolean reserve = v.zeroGapActive() || v.curtainActive() || v.domainActive();
            var skillBudget=ordinaryBudget(v);
            int height=reserve || skillBudget!=null ? 54 : 43;
            TerminalSkin.draw(graphics,TerminalSkin.FRAME,0,0,width,height,-1);
            var numbers=readout(mc,v);
            layer(graphics, width, 4, TerminalSkin.HEALTH,tr("health","Health"),numbers.health(),0xffe5e5e0,HEALTH_TRAIL);
            layer(graphics, width, 23, v.superArmor()?TerminalSkin.SHIELD:TerminalSkin.BROKEN,numbers.label(),numbers.shield(),edge,SHIELD_TRAIL);
            if(reserve || skillBudget!=null) {
                String amount=v.zeroGapActive()?v.zeroGapReserve():v.curtainActive()?v.curtainReserve():v.domainReserve();
                String name=v.zeroGapActive()?tr("zero_gap_reserve","Armor reserve"):v.curtainActive()?tr("curtain_reserve","Curtain reserve"):tr("domain_reserve","Domain reserve");
                if(!reserve) {name=tr("skill_budget_cp_short","Reserve CP");amount=skillBudget.availableText();}
                fittedText(graphics,mc,name,8,43,GRAY,width/2,.75f,false);
                fittedText(graphics,mc,HighPowerRecoveryHudModel.compact(amount,HighPowerRecoveryHudModel.numberLocale(numbers.language())),width/2,43,WHITE,width/2-8,.8f,true);
            }
        } finally { graphics.pose().popPose(); }
    }
    private static void layer(GuiGraphics g,int width,int y,net.minecraft.resources.ResourceLocation icon,
                              String label,String number,int color,Trail trail) {
        TerminalSkin.draw(g,icon,7,y+2,9,9,color);
        FONT.begin(g);
        try {
            double labelSize=Math.min(8,8*(width*.43-18)/Math.max(1,FONT.width(label,8)));
            FONT.text(label,20,y+1,labelSize,0xc9cbca,1,0);
            double size=Math.min(11,11*(width*.54-5)/Math.max(1,FONT.width(number,11)));
            FONT.text(number,width-7,y,size,color&0xffffff,1,2);
        } finally {FONT.end();}
        int length=width-15;
        TerminalSkin.draw(g,TerminalSkin.TRACK,7,y+13,length,3,0xffb1b3b3);
        int trailPixels=(int)Math.round(length*trail.visible(net.minecraft.Util.getMillis()));
        int activePixels=(int)Math.round(length*trail.solid);
        if(trailPixels>activePixels) TerminalSkin.draw(g,TerminalSkin.FILL,7,y+13,trailPixels,3,0xff8c807a);
        TerminalSkin.draw(g,TerminalSkin.FILL,7,y+13,activePixels,3,color);
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
            fields.add(values.superArmor() ? tr("super_armor", "SUPER ARMOR") : tr("shield_broken", "BROKEN"));
        }
        if (values.hasDna()) fields.add(tr("dna_shield","DNA shield")+" "+values.dnaShield()+" / "+values.dnaCapacity());
        if (HighPowerRecoveryHudModel.decimal(values.absorption()).signum()>0) fields.add(tr("absorption","Absorption")+" "+values.absorption());
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
