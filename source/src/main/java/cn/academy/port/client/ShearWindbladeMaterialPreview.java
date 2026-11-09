package cn.academy.port.client;

import cn.academy.port.highpower.core.ShearWindbladeGeometry;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import cn.academy.port.highpower.core.ShearWindbladeRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.lwjgl.glfw.GLFW;

/** Launch-only singleplayer fixture. No live receipt, cast, damage, packet, impact or gameplay clock. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ShearWindbladeMaterialPreview {
    private static final String PREFIX = "academy.shearWindbladeVolume.preview";
    private static final boolean ENABLED = Boolean.getBoolean("academy.visual.qa") && Boolean.getBoolean(PREFIX);
    private static final Settings SETTINGS = settings();
    static final double PARTIAL = .5;
    private static final double LEADING_DISTANCE = 4 * ShearWindbladeRules.STEP;
    private static final double SEPARATION = ShearWindbladeRules.SECOND_DELAY * ShearWindbladeRules.STEP;
    private static Object world, player, connection;
    private static long session;
    private static List<ShearWindbladeState.Controller> controllers = List.of();
    private static boolean shown = true;
    private static int volumeRendered, classicRendered;

    record Settings(String angle, String mode, double distance) {}
    private ShearWindbladeMaterialPreview() {}

    private static Settings settings() {
        return ENABLED ? parseSettings(System.getProperty(PREFIX + ".angle", "front"),
                System.getProperty(PREFIX + ".mode", "horizontal"), System.getProperty(PREFIX + ".distance", "8")) : null;
    }

    /** Invalid launch settings fail closed; the receipt phase cannot be configured or advanced. */
    static Settings parseSettings(String angle, String mode, String distance) {
        try {
            double value = Double.parseDouble(distance);
            if (!Double.isFinite(value) || value != 8 && value != 16
                    || !("front".equals(angle) || "side".equals(angle))
                    || !("horizontal".equals(mode) || "vertical".equals(mode))) return null;
            return new Settings(angle, mode, value);
        } catch (RuntimeException invalid) { return null; }
    }

    static boolean allowed() {
        if (!ENABLED || SETTINGS == null || !shown) return false;
        var mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null && mc.getConnection() != null
                && mc.player.isAlive() && !mc.player.isRemoved() && mc.hasSingleplayerServer()
                && mc.isWindowActive() && !mc.isPaused() && mc.screen == null && !mc.options.hideGui
                && ShearWindbladeEffects.materialFixtureIdle();
    }

    private static void clear() {
        controllers = List.of(); world = player = connection = null;
        volumeRendered = classicRendered = 0;
    }

    /** Only lifecycle invalidation runs on ticks; fixture positions and receipt ages never tick. */
    private static void synchronize() {
        var mc = Minecraft.getInstance();
        if (world != mc.level || player != mc.player || connection != mc.getConnection()
                || session != AcademyClient.inputSessionEpoch()
                || mc.player != null && (!mc.player.isAlive() || mc.player.isRemoved())) clear();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (ENABLED) synchronize();
    }

    /**
     * Uses the authoritative eight-chord parabola and two-tick front spacing. A private
     * State validates this staged frame; it never reaches Effects.STATE or a receiver.
     * At receiptAge zero and partial .5 the production renderer displays previous points.
     */
    static ShearWindbladeState.Controller fixture(Point camera, Point look, double yaw, Settings settings) {
        if (settings == null || parseSettings(settings.angle(), settings.mode(), Double.toString(settings.distance())) == null) return null;
        try {
            var cameraBasis = ShearWindbladeGeometry.frame(camera, look, yaw);
            Point travel = settings.angle().equals("side") ? cameraBasis.horizontal() : cameraBasis.forward();
            Point center = add(camera, scale(cameraBasis.forward(), settings.distance()));
            // The midpoint of the two actual parabola apexes is exactly the requested camera distance.
            Point origin = add(center, scale(travel, -(LEADING_DISTANCE - SEPARATION / 2 + ShearWindbladeGeometry.BOW)));
            var basis = ShearWindbladeGeometry.frame(origin, travel, yaw);
            int plane = settings.mode().equals("vertical") ? 1 : 0;
            var fronts = new ArrayList<ShearWindbladeState.Front>(ShearWindbladeRules.BLADES);
            for (int blade = 0; blade < ShearWindbladeRules.BLADES; blade++) {
                double from = LEADING_DISTANCE - blade * SEPARATION;
                fronts.add(new ShearWindbladeState.Front(blade, ShearWindbladeGeometry.front(basis, plane, from),
                        ShearWindbladeGeometry.front(basis, plane, from + ShearWindbladeRules.STEP), ShearWindbladeState.TRAVELLING));
            }
            var frame = new ShearWindbladeState.Frame(new ShearWindbladeState.Identity(0, new UUID(421, 62), 1, 1, 1),
                    1, ShearWindbladeState.FLYING, plane == 1, origin, fronts, List.of());
            var fixtureState = new ShearWindbladeState();
            return fixtureState.accept(frame, false) ? fixtureState.active().getFirst() : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }

    private static List<ShearWindbladeState.Controller> controllers(RenderLevelStageEvent event) {
        if (!controllers.isEmpty()) return controllers;
        var mc = Minecraft.getInstance();
        var camera = event.getCamera().getPosition();
        var look = event.getCamera().getLookVector();
        var fixture = fixture(new Point(camera.x, camera.y, camera.z), new Point(look.x(), look.y(), look.z()),
                Math.toRadians(event.getCamera().getYRot()), SETTINGS);
        if (fixture == null) return List.of();
        controllers = List.of(fixture);
        world = mc.level; player = mc.player; connection = mc.getConnection(); session = AcademyClient.inputSessionEpoch();
        return controllers;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (!ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        synchronize(); volumeRendered = classicRendered = 0;
        if (!allowed()) return;
        var samples = controllers(event);
        if (samples.isEmpty()) return;
        VisualDecorRenderer.shearFixture(event, samples, PARTIAL);
        if (ShearWindbladeEffects.renderMaterialFixture(event, samples, PARTIAL)) volumeRendered = samples.size();
        else classicRendered = samples.size();
    }

    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (!ENABLED || SETTINGS == null || event.getAction() != GLFW.GLFW_PRESS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.hasSingleplayerServer() || !mc.isWindowActive()
                || mc.isPaused() || mc.screen != null || !mc.player.isAlive() || mc.player.isRemoved()) return;
        if (event.getKey() == GLFW.GLFW_KEY_F8) clear();
        else if (event.getKey() == GLFW.GLFW_KEY_F9) { shown = !shown; clear(); }
    }

    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if (!allowed()) return;
        var mc = Minecraft.getInstance(); var graphics = event.getGuiGraphics();
        String[] lines = {"NEW421 材质样例 / STAGED FIXTURE", "非实战 / NO CAST OR DAMAGE",
                String.format(Locale.ROOT, "%s / %s | pair center %.0fm | 2 fronts, %.0fm apart", SETTINGS.mode(), SETTINGS.angle(), SETTINGS.distance(), SEPARATION),
                String.format(Locale.ROOT, "receiptAge 0 / partial .5 | .62 volume %d / .61 classic %d", volumeRendered, classicRendered),
                "F8 重定位 / reframe | F9 隐藏 / hide", "/aca_vfx2 shear_windblade on/off"};
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.font.width(Component.literal(line)));
        float scale = Math.min(1F, Math.max(1, graphics.guiWidth() - 14) / (float)Math.max(1, width));
        graphics.fill(3, 3, (int)Math.ceil(width * scale) + 11, (int)Math.ceil(lines.length * 11 * scale) + 11, 0xE010151C);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(7, 7, 0); graphics.pose().scale(scale, scale, 1);
            for (int i = 0; i < lines.length; i++)
                graphics.drawString(mc.font, Component.literal(lines[i]), 0, i * 11, i < 2 ? 0xFFFFC765 : 0xFFE3ECF2, true);
        } finally { graphics.pose().popPose(); }
    }

    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point scale(Point p, double n) { return new Point(p.x() * n, p.y() * n, p.z() * n); }
}
