/* Paired AcademyCraft 1.0.7 / LambdaLib 1.2.3 source witnesses. GPLv3; Lambda portions MIT. See NOTICE. */
package cn.academy.port.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Portable resource/source integration check. Does not load a Minecraft client, server, or native sound engine. */
public final class ClassicMetalFormerVisualRegressionTest {
    private static final String FIXTURE = "classic-metal-former-source/client/";
    private static final String MANIFEST_SHA256 = "360ff469c272503234ebd689c7719e935dea50fd8b8f626e293f561df9712847";
    private static Path root, client, common, shared;
    private static int checks;
    private static void yes(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    private static void eq(double expected, double actual, String description) {
        yes(Math.abs(expected - actual) < 1e-9, description + " expected=" + expected + " actual=" + actual);
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Path find(String relative, Path... bases) throws IOException {
        for (Path base : bases) {
            Path path = base.resolve(relative);
            if (Files.isRegularFile(path)) return path;
        }
        throw new IOException("Missing promoted source/resource: " + relative);
    }
    private static byte[] fixture(String name) throws IOException {
        try (var stream = ClassicMetalFormerVisualRegressionTest.class.getClassLoader().getResourceAsStream(FIXTURE + name)) {
            if (stream != null) return stream.readAllBytes();
        }
        return Files.readAllBytes(find("src/test/resources/" + FIXTURE + name, client, root));
    }
    private static String witness(String name) throws IOException {
        return new String(fixture(name), StandardCharsets.UTF_8);
    }
    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static JsonObject resource(String relative) throws IOException {
        return json(Files.readAllBytes(find("src/main/resources/assets/academy/" + relative, client, root)));
    }
    private static String source(String name) throws IOException {
        return Files.readString(find("src/main/java/cn/academy/port/" + name,
                client, shared, common.resolve("integration"), common, root));
    }
    private static void has(String text, String reason, String... fragments) {
        for (String fragment : fragments) yes(text.contains(fragment), reason + ": " + fragment);
    }
    private static Matcher match(String text, String pattern, String reason) {
        Matcher matcher = Pattern.compile(pattern, Pattern.DOTALL).matcher(text);
        yes(matcher.find(), reason);
        return matcher;
    }
    private static Element widget(Element parent, String name) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling())
            if (child instanceof Element e && e.getTagName().equals("Widget") && e.getAttribute("name").equals(name)) return e;
        throw new AssertionError("Missing source widget: " + name);
    }
    private static Element component(Element parent, String suffix) {
        for (var child = parent.getFirstChild(); child != null; child = child.getNextSibling())
            if (child instanceof Element e && e.getTagName().equals("Component") && e.getAttribute("class").endsWith(suffix)) return e;
        throw new AssertionError("Missing source component: " + suffix);
    }
    private static String field(Element element, String name) {
        return element.getElementsByTagName(name).item(0).getTextContent();
    }
    private static double number(Element element, String name) { return Double.parseDouble(field(element, name)); }
    private static Element xmlMain(String name) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return (Element)factory.newDocumentBuilder().parse(new ByteArrayInputStream(fixture(name))).getDocumentElement().getFirstChild();
    }
    private static double[] geometry(Element main, String name) {
        Element transform = component(widget(main, name), ".Transform");
        yes(field(transform, "alignWidth").equals("CENTER") && field(transform, "alignHeight").equals("TOP"), "original widget alignment " + name);
        double width = number(transform, "width");
        double x = (number(component(main, ".Transform"), "width") - width) / 2 + number(transform, "x");
        return new double[]{x, number(transform, "y"), width, number(transform, "height")};
    }
    private static void geometryEq(double[] expected, Matcher actual, String name) {
        for (int i = 0; i < 4; i++) eq(expected[i], Double.parseDouble(actual.group(i + 1)), name + " coordinate " + i);
    }

    public static void main(String[] args) throws Exception {
        root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        client = args.length > 1 ? Path.of(args[1]).toAbsolutePath() : root;
        common = args.length > 2 ? Path.of(args[2]).toAbsolutePath() : root;
        shared = args.length > 3 ? Path.of(args[3]).toAbsolutePath() : root;
        byte[] manifestBytes = fixture("manifest.json");
        yes(hash(manifestBytes).equals(MANIFEST_SHA256), "literal-pinned original source/media witness manifest");
        JsonObject manifest = json(manifestBytes);
        for (var entry : manifest.getAsJsonArray("source_fixtures")) {
            var source = entry.getAsJsonObject();
            yes(hash(fixture(source.get("fixture").getAsString())).equals(source.get("sha256").getAsString()),
                    "immutable original source bytes " + source.get("fixture").getAsString());
        }
        media(manifest);
        geometryAndBindings(manifest);
        models(manifest);
        sound(manifest);
        languages();
        System.out.println("ClassicMetalFormerVisualRegressionTest: " + checks + " paired source/media/geometry/integration checks passed");
    }

    private static void media(JsonObject manifest) throws Exception {
        for (var entry : manifest.getAsJsonArray("original_media")) {
            JsonObject expected = entry.getAsJsonObject();
            String relative = expected.get("asset").getAsString();
            byte[] bytes = Files.readAllBytes(find("src/main/resources/assets/academy/" + relative, client, root));
            yes(bytes.length == expected.get("bytes").getAsInt(), "original media length " + relative);
            yes(hash(bytes).equals(expected.get("sha256").getAsString()), "original media bytes " + relative);
            if (!relative.endsWith(".png")) continue;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            yes(image != null, "valid original PNG " + relative);
            yes(image.getWidth() == expected.get("width").getAsInt() && image.getHeight() == expected.get("height").getAsInt(), "original pixel dimensions " + relative);
            byte[] rgba = rawRgbaPng(bytes, image.getWidth(), image.getHeight());
            int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int alpha = Byte.toUnsignedInt(rgba[(y * image.getWidth() + x) * 4 + 3]);
                if (alpha != 0) { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); }
            }
            yes(hash(rgba).equals(expected.get("rgba_pixel_sha256").getAsString()), "decoded source RGBA pixels " + relative);
            int[] bounds = {minX, minY, maxX + 1, maxY + 1};
            JsonArray expectedBounds = expected.getAsJsonArray("alpha_bounds");
            for (int j = 0; j < 4; j++) yes(bounds[j] == expectedBounds.get(j).getAsInt(), "visible source artwork bounds " + relative + ":" + j);
        }
    }

    /** Exact encoded RGBA values: ImageIO.getRGB applies the source ICC profile, unlike raw source PNG pixels. */
    private static byte[] rawRgbaPng(byte[] bytes, int width, int height) throws Exception {
        DataInputStream chunks = new DataInputStream(new ByteArrayInputStream(bytes, 8, bytes.length - 8));
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        boolean header = false, end = false;
        int bitDepth = 0;
        while (!end) {
            int length = chunks.readInt();
            byte[] type = chunks.readNBytes(4), data = chunks.readNBytes(length);
            int expectedCrc = chunks.readInt();
            CRC32 crc = new CRC32(); crc.update(type); crc.update(data);
            yes(data.length == length && (int)crc.getValue() == expectedCrc, "original PNG chunk checksum");
            String name = new String(type, StandardCharsets.US_ASCII);
            if (name.equals("IHDR")) {
                ByteBuffer ihdr = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
                yes(ihdr.getInt() == width && ihdr.getInt() == height, "raw PNG dimensions");
                bitDepth = Byte.toUnsignedInt(data[8]);
                yes((bitDepth == 8 || bitDepth == 16) && data[9] == 6 && data[10] == 0 && data[11] == 0 && data[12] == 0,
                        "source non-interlaced 8/16-bit RGBA PNG encoding");
                header = true;
            } else if (name.equals("IDAT")) compressed.write(data);
            else if (name.equals("IEND")) end = true;
        }
        yes(header && end, "complete original PNG topology");
        byte[] filtered;
        try (var inflate = new InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray()))) { filtered = inflate.readAllBytes(); }
        int pixelBytes = 4 * (bitDepth / 8);
        int stride = width * pixelBytes;
        yes(filtered.length == (stride + 1) * height, "full source PNG scanline decode");
        byte[] rgba = new byte[stride * height];
        for (int y = 0; y < height; y++) {
            int filter = Byte.toUnsignedInt(filtered[y * (stride + 1)]);
            yes(filter >= 0 && filter <= 4, "standard source PNG filter");
            for (int x = 0; x < stride; x++) {
                int at = y * stride + x;
                int left = x >= pixelBytes ? Byte.toUnsignedInt(rgba[at - pixelBytes]) : 0;
                int up = y > 0 ? Byte.toUnsignedInt(rgba[at - stride]) : 0;
                int upperLeft = y > 0 && x >= pixelBytes ? Byte.toUnsignedInt(rgba[at - stride - pixelBytes]) : 0;
                int prediction = switch (filter) {
                    case 0 -> 0; case 1 -> left; case 2 -> up; case 3 -> (left + up) / 2;
                    case 4 -> { int p = left + up - upperLeft, a = Math.abs(p - left), b = Math.abs(p - up), c = Math.abs(p - upperLeft); yield a <= b && a <= c ? left : b <= c ? up : upperLeft; }
                    default -> throw new AssertionError("invalid PNG filter");
                };
                rgba[at] = (byte)(Byte.toUnsignedInt(filtered[y * (stride + 1) + 1 + x]) + prediction);
            }
        }
        if (bitDepth == 8) return rgba;
        // libpng/Pillow's RGBA8 conversion drops the low sample byte from original 16-bit block art.
        byte[] rgba8 = new byte[width * height * 4];
        for (int i = 0; i < rgba8.length; i++) rgba8[i] = rgba[i * 2];
        return rgba8;
    }

    private static void geometryAndBindings(JsonObject manifest) throws Exception {
        String screen = source("client/ClassicMetalFormerScreen.java");
        String bridge = source("client/ClassicMetalFormerClient.java");
        String menu = source("former/ClassicMetalFormerMenu.java");
        String protocol = source("wireless/ClassicWirelessProtocol.java");
        String wireless = source("client/ClassicWirelessScreen.java");
        String gui = witness("academy/GuiMetalFormer.scala");
        String tech = witness("academy/TechUI.scala");
        Element main = xmlMain("academy/page_metalformer.xml");
        geometryEq(geometry(main, "progress"), match(screen, "leftPos \\+ ([0-9.]+), topPos \\+ ([0-9.]+), ([0-9.]+) \\* progress, ([0-9.]+)", "native progress rectangle"), "progress");
        geometryEq(geometry(main, "icon_mode"), match(screen, "leftPos \\+ ([0-9.]+), topPos \\+ ([0-9.]+), ([0-9.]+), ([0-9.]+), WHITE\\);\\s*drawInfo", "native mode icon rectangle"), "mode icon");
        for (String name : List.of("btn_left", "btn_right")) {
            double[] expected = geometry(main, name);
            String art = name.equals("btn_left") ? "button_arrowlefta" : "button_arrowrighta";
            Matcher button = match(screen, "new SourceButton\\(leftPos \\+ ([0-9]+), topPos \\+ ([0-9]+), ([0-9]+), ([0-9]+),(?:(?!new SourceButton).)*?\\\"button/" + art + "\\\", ([0-9.]+), ([0-9.]+), false, \\(\\) -> cycle\\(([01])\\)", "native original arrow control " + name);
            eq(expected[0], Double.parseDouble(button.group(1)), "arrow hitbox x");
            eq(Math.floor(expected[1]), Double.parseDouble(button.group(2)), "native integer arrow hitbox y adapter");
            eq(expected[2], Double.parseDouble(button.group(3)), "arrow hitbox width");
            eq(expected[3], Double.parseDouble(button.group(4)), "arrow hitbox height");
            eq(expected[0], Double.parseDouble(button.group(5)), "source fractional arrow artwork x");
            eq(expected[1], Double.parseDouble(button.group(6)), "source fractional arrow artwork y");
            yes(button.group(7).equals(name.equals("btn_left") ? "0" : "1"), "native button direction maps original arrow");
        }
        yes(field(component(widget(main, "progress"), ".ProgressBar"), "dir").equals("RIGHT"), "source progress direction");
        has(witness("lambda/ProgressBar.java"), "original cropped progress math", "w = width * disp", "tw = disp", "h = height * disp", "y = height * (1 - disp)");
        has(screen, "native server-owned presentation", "0, 0, progress, 1, WHITE", "menu.progress()", "menu.mode().name().toLowerCase(Locale.ROOT)", "handleInventoryButtonClick(menu.containerId, button)");
        has(gui, "source original pages/mode response", "handleAlt(-1)", "handleAlt(1)", "fut.sendResult(tile.mode)", "InventoryPage(invWidget)", "WirelessPage.userPage(tile)", "new ContainerUI(container, invPage, wirelessPage)");
        yes(!screen.contains("cycleMode(") && !screen.contains("menu.mode ="), "screen awaits server mode confirmation");
        has(menu, "server button validation", "button!=0&&button!=1", "player.containerMenu!=this", "!tile.stillValid(player)", "player.level().isClientSide", "tile.cycleMode(button==0?-1:1)", "broadcastChanges()", "addDataSlots(data)");
        has(bridge, "actual menu/setup registration", "Dist.CLIENT", "event.register(ClassicMetalFormer.MENU.get(), ClassicMetalFormerScreen::new)", "setClientObserver(Sounds::observe)", "menuId, OPEN_TOKEN, \"open_former\", origin, \"\", \"\"");
        has(protocol, "sender and nonce bound origin navigation", "request.action.equals(\"open_former\")", "ClassicMetalFormerMenu menu", "!menu.isFor(former)", "!menu.sourcePos().equals(request.target)", "menu.token().equals(request.token)", "new SimpleMenuProvider(former::createMenu,former.getDisplayName())");
        has(wireless, "USER inventory tab returns to real Former inventory", "if(hit(mx,my,-20,0,17,17)){if(menu.kind()==ClassicWirelessMenu.Kind.USER){request(\"return\",menu.sourcePos(),\"\",\"\");return true;}", "if(!wireless)super.renderSlot", "return wireless||super.mouseClicked", "return wireless||super.mouseDragged");
        has(tech, "source inventory input gating", "page.id == \"inv\"", "override def isSlotActive = shouldDisplayInventory(main.currentPage)", "button.scale(0.7)", "button.pos(-20, idx * 22)");
        eq(16.8, number(component(xmlMain("academy/pageselect.xml"), ".Transform"), "width") * .7, "source scaled page icon width");
        has(tech, "source inherited histogram", "new Widget().size(210, 210).scale(0.4)", "new Widget().size(16, 120).pos(56 + idx * 40, 78)", "Direction.UP", "MathUtils.clampd(0.03, 1, elem.value())", "blank(-30)", "new Color(0xff25c4ff)", "0.675 + sin * 0.175");
        eq(64, 10 - 30 + 210 * .4, "source histogram property row y");
        eq(80, Math.max(50, 10 - 30 + 210 * .4 + 8 + 8), "source one-histogram panel height");
        has(screen, "native inherited histogram and tooltip", "x, y - 20, 84, 84", "x + 22.4, y + 59.2 - 48 * fraction, 6.4, 48 * fraction", "histogramFraction(menu.energy(), 3000)", "Math.signum(80 - infoHeight)", "x + 3, y + 65.5, 6, 6", "x + 10, y + 64, 8", "x + 46, y + 64, 8", "leftPos + 76 + 6 - textWidth / 2, y = topPos + 4.5 - 10", "sourceFont.release()");
        String slotSource = witness("academy/ContainerMetalFormer.java");
        for (int[] slot : new int[][]{{0,13,49},{1,143,49},{2,42,80}}) {
            has(slotSource, "original slot witness", "tile, " + slot[0] + ", " + slot[1] + ", " + slot[2]);
            has(menu, "native source slot geometry", "Slot(inventory," + slot[0] + "," + slot[1] + "," + slot[2] + ")");
        }
        String originalOrder = match(witness("academy/TileMetalFormer.java"), "public enum Mode\\s*\\{\\s*([^;]+);", "source modes").group(1).replaceAll("\\s", "");
        String nativeOrder = match(source("former/ClassicMetalFormerWork.java"), "enum Mode\\s*\\{([^}]+)", "native mode order").group(1).replaceAll("\\s", "");
        yes(originalOrder.equals("PLATE,INCISE,ETCH,REFINE") && originalOrder.equals(nativeOrder), "all four original modes retain source order");
    }

    private static void models(JsonObject manifest) throws Exception {
        String block = witness("academy/BlockMetalFormer.java");
        has(block, "source ordinary cube and face formula", "return 0;", "sideIcons[(offsets[meta] + side) % 4]");
        String[] offsets = match(block, "offsets\\[\\]\\s*=\\s*\\{([^}]+)", "source rotation offsets").group(1).split(",");
        String[] sourceSides = new String[4];
        for (int i = 0; i < 4; i++) sourceSides[i] = match(block, "sideIcons\\[" + i + "\\].*?\\\"metal_former_([a-z]+)\\\"", "source side texture " + i).group(1);
        JsonObject cube = json(fixture("native-cube.json"));
        yes(cube.getAsJsonArray("elements").size() == 1, "actual native cube has one cuboid");
        JsonObject element = cube.getAsJsonArray("elements").get(0).getAsJsonObject();
        yes(element.getAsJsonArray("from").toString().equals("[0,0,0]") && element.getAsJsonArray("to").toString().equals("[16,16,16]"), "full native block geometry");
        Set<String> allFaces = Set.of("up", "down", "north", "south", "west", "east");
        yes(element.getAsJsonObject("faces").keySet().equals(allFaces), "all six native cube faces");
        Map<String,Integer> legacySides = Map.of("north",2,"south",3,"west",4,"east",5);
        JsonObject variants = resource("blockstates/metal_former.json").getAsJsonObject("variants");
        yes(variants.keySet().equals(Set.of("rotation=0","rotation=1","rotation=2","rotation=3")), "only the four original orientation variants");
        for (int rotation = 0; rotation < 4; rotation++) {
            JsonObject variant = variants.getAsJsonObject("rotation=" + rotation);
            yes(variant.size() == 1 && variant.get("model").getAsString().equals("academy:block/metal_former_" + rotation), "source explicit face model without inferred Y rotation");
            JsonObject model = resource("models/block/metal_former_" + rotation + ".json");
            yes(model.get("parent").getAsString().equals("minecraft:block/cube") && !model.has("elements"), "rotation inherits real native full cube");
            for (String face : allFaces) {
                String expected = face.equals("up") ? "top" : face.equals("down") ? "bottom" : sourceSides[(Integer.parseInt(offsets[rotation].trim()) + legacySides.get(face)) % 4];
                JsonObject declaration = element.getAsJsonObject("faces").getAsJsonObject(face);
                yes(declaration.get("texture").getAsString().equals("#" + face) && declaration.get("cullface").getAsString().equals(face), "native texture/culling binding " + face);
                yes(model.getAsJsonObject("textures").get(face).getAsString().equals("academy:blocks/metal_former_" + expected), "original meta " + rotation + " face " + face);
                yes(manifest.getAsJsonObject("source_faces").getAsJsonObject(Integer.toString(rotation)).get(face).getAsString().equals(expected), "paired immutable source face witness");
            }
        }
        yes(resource("models/item/metal_former.json").get("parent").getAsString().equals("academy:block/metal_former_0"), "original metadata-zero inventory model");
    }

    private static void sound(JsonObject manifest) throws Exception {
        String original = witness("academy/TileMetalFormer.java");
        String bridge = source("client/ClassicMetalFormerClient.java");
        String tile = source("former/ClassicMetalFormerBlockEntity.java");
        has(original, "source non-song work loop recipe", "new TileEntitySound(this, \"machine.machine_work\")", ".setLoop().setVolume(.6f)", "!isWorkInProgress()");
        has(witness("academy/TileEntitySound.java"), "source sound position/removal", "_te.xCoord +.5, _te.yCoord + .5, _te.zCoord + .5", "te.isInvalid()", "donePlaying = true");
        has(bridge, "native original work sound and lifecycle", "\"academy\", \"machine.machine_work\"", "SoundSource.MASTER", "volume = .6f", "looping = true", "delay = 0", "getX() + .5", "getY() + .5", "getZ() + .5", "existing.tile != tile", "!tile.clientWorking()", "world.getBlockEntity(tile.getBlockPos()) == tile", "if (mc.level != world) reset(mc.level)", "for (FormerLoop loop : LOOPS.values()) loop.finish()");
        has(tile, "common sound observer data", "Consumer<ClassicMetalFormerBlockEntity>", "clientObserver.accept(this)", "tag.getBoolean(\"working\")", "getUpdateTag", "onDataPacket");
        yes(!tile.contains("net.minecraft.client"), "common block entity cannot link a client class");
        JsonObject oldEvent = json(fixture("academy/sounds.json")).getAsJsonObject("machine.machine_work");
        JsonObject event = resource("sounds.json").getAsJsonObject("machine.machine_work");
        yes(oldEvent.get("category").getAsString().equals("master"), "original category agrees with native MASTER sound source");
        var oldSound = oldEvent.getAsJsonArray("sounds").get(0).getAsJsonObject();
        yes(event.getAsJsonArray("sounds").size() == 1, "exact single source work sound; no song substitution");
        var sound = event.getAsJsonArray("sounds").get(0).getAsJsonObject();
        yes(sound.get("name").getAsString().equals("academy:" + oldSound.get("name").getAsString()) && sound.get("stream").getAsBoolean() == oldSound.get("stream").getAsBoolean(), "source event native namespace/schema adapter");
        byte[] audio = Files.readAllBytes(find("src/main/resources/assets/academy/sounds/machine/machine_work.ogg", client, root));
        yes(new String(audio, 0, 4, StandardCharsets.US_ASCII).equals("OggS"), "original work sound Ogg container");
        int identification = -1;
        for (int i = 0; i < audio.length - 7; i++) if (audio[i] == 1 && new String(audio, i + 1, 6, StandardCharsets.US_ASCII).equals("vorbis")) { identification = i; break; }
        yes(identification >= 0, "original Vorbis identification packet");
        JsonObject expected = manifest.getAsJsonObject("work_audio");
        yes(Byte.toUnsignedInt(audio[identification + 11]) == expected.get("channels").getAsInt(), "original stereo machine loop");
        yes(ByteBuffer.wrap(audio, identification + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() == expected.get("sample_rate").getAsInt(), "original 44.1 kHz machine loop");
    }

    private static void languages() throws Exception {
        for (var entry : Map.of("en_us","en_US", "zh_cn","zh_CN", "zh_tw","zh_TW", "ja_jp","ja_JP").entrySet()) {
            String original = match(witness("academy/lang/" + entry.getValue() + ".lang"), "(?:^|\\n)tile\\.ac_metal_former\\.name=([^\\r\\n]+)", "original localized Former name").group(1);
            Path additions = client.resolve("integration/lang-additions/" + entry.getKey() + ".json");
            JsonObject language = Files.isRegularFile(additions) ? json(Files.readAllBytes(additions)) : resource("lang/" + entry.getKey() + ".json");
            for (String key : List.of("block.academy.metal_former", "item.academy.metal_former"))
                yes(language.has(key) && language.get(key).getAsString().equals(original), "exact original " + entry.getKey() + " machine name alias " + key);
            for (String key : List.of("screen.academy.metal_former.previous", "screen.academy.metal_former.next"))
                yes(language.has(key) && !language.get(key).getAsString().isBlank(), "native narrated arrow label " + entry.getKey());
        }
    }
}
