package cn.academy.port.client;

import cn.academy.port.skill.TeleporterFinalSourceFixtures;
import java.io.DataInputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/** Source/media/timeline/input and actual compiled client-contract checks. No client initialization,
 * Minecraft launch, framebuffer, GPU or audio playback is performed. javap inspects class files only. */
public final class TeleporterFinalVisualRegressionTest {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static void near(double actual, double original, String why) {
        check(Math.abs(actual - original) < 1e-10, why);
    }
    private static double number(String source, String expression) {
        var matcher = Pattern.compile(expression).matcher(source);
        if (!matcher.find()) throw new AssertionError("Missing original visual expression " + expression);
        return Double.parseDouble(matcher.group(1));
    }
    private static void source(String text, String contract) {
        TeleporterFinalSourceFixtures.contains(text, contract);
        checks++;
    }

    private static String disassemble(String className) {
        var output = new StringWriter();
        var errors = new StringWriter();
        var javap = java.util.spi.ToolProvider.findFirst("javap").orElseThrow(() -> new AssertionError("Official JDK javap tool required"));
        int result = javap.run(new PrintWriter(output), new PrintWriter(errors), "-c", "-p", "-classpath", System.getProperty("java.class.path"), className);
        if (result != 0) throw new AssertionError("Cannot inspect compiled " + className + ": " + errors);
        return output.toString();
    }
    private static String method(String disassembly, String declaration) {
        int start = disassembly.indexOf("  " + declaration);
        if (start < 0) throw new AssertionError("Compiled method missing " + declaration);
        var next = Pattern.compile("(?m)^  (?:public|private|protected|static) ").matcher(disassembly);
        int end = disassembly.length();
        if (next.find(start + declaration.length() + 2)) end = next.start();
        return disassembly.substring(start, end);
    }
    /** Reads actual class constants without loading or initializing the client class. */
    private static Set<String> constants(String className) throws Exception {
        try (var raw = TeleporterFinalVisualRegressionTest.class.getResourceAsStream("/" + className.replace('.', '/') + ".class")) {
            check(raw != null, "Compiled class resource " + className);
            var input = new DataInputStream(raw);
            check(input.readInt() == 0xcafebabe, "Valid compiled class " + className);
            input.readUnsignedShort();input.readUnsignedShort();
            int count = input.readUnsignedShort();
            Set<String> result = new HashSet<>();
            for (int index = 1; index < count; index++) {
                switch (input.readUnsignedByte()) {
                    case 1 -> result.add(input.readUTF());
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> input.readInt();
                    case 5, 6 -> {input.readLong();index++;}
                    case 7, 8, 16, 19, 20 -> input.readUnsignedShort();
                    case 15 -> {input.readUnsignedByte();input.readUnsignedShort();}
                    default -> throw new AssertionError("Unrecognized class constant tag");
                }
            }
            return result;
        }
    }

    private static void originalTimelineAndInput() {
        String marking = TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/vanilla/teleporter/client/MarkRender.java");
        String corners = TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/vanilla/teleporter/client/RenderMarker.java");
        String factory = TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/vanilla/teleporter/client/TPParticleFactory.java");
        String particle = TeleporterFinalSourceFixtures.lambda("src/main/java/cn/lambdalib/particle/Particle.java");
        double frameTicks = number(marking, "mark.ticksExisted / ([.0-9]+)");
        int frames = (int) number(marking, "getEffectSeq\\(\"tp_mark\", ([0-9]+)\\)");
        for (int tick = 0; tick < 700; tick++) check(ClassicTeleporterTimeline.markFrame(tick) == (int) ((tick / frameTicks) % frames), "Original seven-frame 2.5-tick animation");
        double bob = number(corners, "([.0-9]+) \\* Math.sin\\(GameTimer.getAbsTime\\(\\)"), period = number(corners, "GameTimer.getAbsTime\\(\\) / ([.0-9]+)");
        for (long time : new long[]{0, 1, 400, 1000, 2500, 10000, 1234567}) near(ClassicTeleporterTimeline.markerBob(time), bob * Math.sin(time / period), "Original corner marker bob");
        int fadeIn = (int) number(particle, "fadeInTime = ([0-9]+)"), life = (int) number(factory, "fadeAfter\\(([0-9]+), [0-9]+\\)"), fadeOut = (int) number(factory, "fadeAfter\\([0-9]+, ([0-9]+)\\)");
        for (int age = 0; age <= 43; age++) {
            double expected = age > life ? Math.max(0, 1 - (double) (age - life) / fadeOut) : age < fadeIn ? (double) age / fadeIn : 1;
            near(ClassicTeleporterTimeline.particleAlpha(age), expected, "Original particle alpha");
            check(ClassicTeleporterTimeline.particleAlive(age) == (age <= life + fadeOut), "Original particle survives zero-alpha final tick");
        }
        source(marking, "GL11.glScaled(-1, -1, 1)");
        source(marking, "GL11.glDisable(GL11.GL_DEPTH_TEST)");
        source(corners, "width = targ.width;\n            height = targ.height;");
        source(TeleporterFinalSourceFixtures.shift(), "blockMarker.height = 1.2f");
        source(TeleporterFinalSourceFixtures.shift(), "blockMarker.width = 1.2f");
        source(TeleporterFinalSourceFixtures.flashing(), "marking = new EntityTPMarking(player)");
        source(TeleporterFinalSourceFixtures.flashing(), "ACSounds.playClient(player, \"tp.tp_flashing\", 1.0f)");
        source(TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/core/client/sound/ACSounds.java"), "playClient(new FollowEntitySound(target, name).setVolume(volume))");
        String follow = TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/core/client/sound/FollowEntitySound.java");
        for (String axis : List.of("x", "y", "z")) source(follow, "this." + axis + "PosF = (float) entity.pos" + axis.toUpperCase(java.util.Locale.ROOT));
        check(ClassicTeleporterTimeline.BIPED.size() == 7, "Original static biped and headwear");
        int vertices = 0;
        for (var box : ClassicTeleporterTimeline.BIPED) {
            check(ClassicTeleporterTimeline.mesh(box).size() == 6, "Original ModelBox six faces");
            for (var quad : ClassicTeleporterTimeline.mesh(box)) for (var vertex : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
                check(Double.isFinite(vertex.x() + vertex.y() + vertex.z()) && vertex.u() >= 0 && vertex.u() <= 1 && vertex.v() >= 0 && vertex.v() <= 1, "Finite original 64x32 UV geometry");vertices++;
            }
        }
        check(vertices == 168, "Original seven boxes by six quads");
        var latch = new ClassicInputLatch();
        check(latch.update(true, true).press(), "Directional initial press");
        check(latch.update(true, true).tick(), "Directional preview hold");
        check(latch.update(false, true).release(), "Directional release performs hop");
        check(!latch.update(false, true).release(), "Duplicate directional release rejected");
        latch.update(true, true);
        check(latch.update(true, false).abort(), "GUI/permission interruption aborts direction");
        check(!latch.update(false, true).release(), "Interrupted direction cannot perform late release");
        latch.replaceSession(true);
        check(!latch.update(true, true).press(), "Session replacement preserves held physical key");
        latch.update(false, true);
        check(latch.update(true, true).press(), "Fresh press after replacement works");
    }

    private static void compiledLifecycleAndRoutes() throws Exception {
        String screen = disassemble("cn.academy.port.client.ClassicLocationTeleportScreen");
        String effects = disassemble("cn.academy.port.client.ClassicTeleporterFinalEffects");
        String removed = method(screen, "public void removed();"), hide = method(effects, "public static void hideLocal(java.lang.String);");
        check(removed.contains("ClassicTeleporterFinalEffects.hideLocal:") && !removed.contains("ClassicTeleporterFinalEffects.endLocal:") && !removed.contains("Minecraft.setScreen:"), "Location removal uses non-closing cleanup and cannot recurse into setScreen");
        check(!hide.contains("closeFromServer:") && !hide.contains("Minecraft.setScreen:"), "Local hide never re-enters screen removal");
        check(method(effects, "public static void endLocal(java.lang.String);").contains("ClassicLocationTeleportScreen.closeFromServer:"), "Explicit server/abort cleanup can close live Location screen");
        String sound = method(effects, "private static void sound(net.minecraft.world.entity.Entity, java.lang.String, float);");
        check(sound.contains("tp.tp_flashing") && sound.contains("ClassicTeleporterFinalEffects$FollowSound") && sound.contains("SoundManager.play:"), "Flash audio follows its actor through the original client sound route");
        String followTick = method(disassemble("cn.academy.port.client.ClassicTeleporterFinalEffects$FollowSound"), "public void tick();");
        check(Pattern.compile("\\bd2f\\b").matcher(followTick).results().count() == 3, "Following sound keeps original float actor coordinates on every tick");
        check(method(effects, "public static void clear();").contains("FollowSound.finish:"), "World/player/connection cleanup stops following audio");
        String update = method(screen, "public void update(net.minecraft.nbt.CompoundTag);");
        check(update.contains("Field listUpdated:J") && !update.contains("Field opened:J"), "Location list refresh does not restart the one-time menu-opening blend");
        String locationRequest = method(disassemble("cn.academy.port.skill.LocationTeleport"), "public static boolean request(net.minecraft.server.level.ServerPlayer, java.lang.String, java.lang.String);");
        int perform = locationRequest.indexOf("// Method perform:");
        check(perform >= 0 && locationRequest.indexOf("// Method session:") < perform && locationRequest.indexOf("// Method abort:", perform) > perform, "Authenticated failed Location perform cleans up the screen session");
        String tick = method(effects, "public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent$Post);");
        String direction = method(effects, "public static void direction(long, long, int, cn.academy.port.client.ClassicClientInputBinding$Edge, boolean);");
        check(direction.contains("ClassicClientInputBinding$Edge.DOWN")
                && Pattern.compile("iconst_0\\s*\\n\\s*[0-9]+: putfield[^\\n]+Context.markTicks:I").matcher(direction).find(), "Every accepted Flash direction down resets its marker animation age");
        check(direction.contains("ClassicClientInputBinding$Edge.UP") && direction.contains("flashing_direction_owned")
                && direction.contains("AcademyClient.request:"), "Accepted Flash direction up uses its owned input/token route");
        check(!tick.contains("InputConstants.isKeyDown:") && !tick.contains("ClassicInputLatch.update:"), "Effect tick leaves physical direction polling to the shared runtime");
        check(method(effects, "private static void biped(cn.academy.port.client.ClassicTeleporterFinalEffects$Context, com.mojang.blaze3d.vertex.PoseStack);").contains("Context.markTicks:I"), "Flash renders marker age rather than full mode age");
        String render = method(effects, "public static void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent);");
        int width = render.indexOf("Entity.getBbWidth:()F"), height = render.indexOf("Entity.getBbHeight:()F", width), end = render.indexOf("// Method corners:", height);
        check(width >= 0 && height > width && end > height && !render.substring(width, end).contains("fmul"), "Shift target corners retain exact original entity dimensions");
        String clicks = method(screen, "public boolean mouseClicked(double, double, int);");
        check(clicks.contains("double 29.916d") && clicks.contains("double 63.249d"), "Location buttons have original vertical hitboxes");
        String icons = method(screen, "private static void icon(cn.academy.port.client.ClassicHudCanvas, java.lang.String, double, double, double);");
        for (int channel : new int[]{193, 207, 213}) check(icons.contains("double " + (channel / 255D) + "d"), "Location buttons retain original normal-color channel " + channel);
        Set<String> hud = constants("cn.academy.port.client.ClassicAbilityHud"), routes = constants("cn.academy.port.client.AcademyClient"), cosmetic = constants("cn.academy.port.client.ClassicTeleporterFinalEffects");
        check(hud.contains("TP_Flashing") && hud.stream().anyMatch(s -> s.contains("abilities/teleporter/flashing/")), "HUD resolves original Flash A/D/W/S letter textures");
        for (String route : List.of("location_teleport_list", "location_teleport_end", "shift_tp_start", "shift_tp_end", "shift_tp_placed", "shift_tp_critical", "flashing_start", "flashing_perform", "flashing_end")) check(routes.contains(route), "Verified compiled final packet route " + route);
        check(cosmetic.contains("effects/tp_particle") && cosmetic.stream().anyMatch(s -> s.contains("effects/tp_mark/")), "Compiled effects reference original particle/mark assets");
        for (String field : List.of("epoch", "connection", "entity_uuid", "token", "input", "CLOSED")) check(cosmetic.contains(field), "Compiled effect envelopes retain session/identity/token guard " + field);
    }

    private static void originalGui() throws Exception {
        String xml = TeleporterFinalSourceFixtures.academy("src/main/resources/assets/academy/guis/loctele_new.xml");
        var factory = DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var widgets = document.getElementsByTagName("Widget");Set<String> names = new HashSet<>();
        for (int index = 0; index < widgets.getLength(); index++) names.add(((Element) widgets.item(index)).getAttribute("name"));
        check(names.containsAll(List.of("root", "menu", "list", "elem_template", "add_template", "btn_teleport", "btn_remove", "btn_confirm", "input_text", "info")), "Original Location GUI topology");
        source(xml, "<scale>0.24</scale>");source(xml, "<width>442.0</width>");source(xml, "<height>530.0</height>");
        for (String asset : List.of("academy:textures/guis/icons/icon_location_on.png", "academy:textures/guis/icons/icon_clear.png", "academy:textures/guis/check.png")) source(xml, asset);
        source(TeleporterFinalSourceFixtures.location(), "val ElemTimeStep = 0.06");
        source(TeleporterFinalSourceFixtures.location(), "target.component[DrawTexture].color = color");
        source(TeleporterFinalSourceFixtures.location(), "val TextNormal = c(0xffc1cfd5)");
    }

    public static void main(String[] args) throws Exception {
        check(TeleporterFinalSourceFixtures.verifyAll() == 44, "Exact source bundle");
        check(TeleporterFinalSourceFixtures.verifyMedia() == 28, "Exact original runtime media");
        originalTimelineAndInput();originalGui();compiledLifecycleAndRoutes();
        System.out.println("PASS " + checks + " final Teleporter source/media/timeline/input and actual compiled client lifecycle/routing checks; no client/GPU/audio launch");
    }
}
