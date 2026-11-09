/* Original AcademyCraft1.0.7/LambdaLib1.2.3 source witnesses. GPLv3 / MIT; see fixture NOTICE. */
package cn.academy.port.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Portable paired source/mesh/layout/resource tests. No game, client, renderer, or native code is run. */
public final class ClassicPhaseGeneratorVisualRegressionTest {
    private static final String MANIFEST_SHA256 = "e01adbb9c692893bf6f83afcf4b5be309e6674f79af7f738e1e77b22ebef81a7";
    private static Path root, client, common, shared, fixtures;
    private static int checks;
    private static void yes(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void eq(double expected, double actual, String why) {
        yes(Math.abs(expected - actual) < 1e-7, why + ": expected " + expected + ", got " + actual);
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Path find(String path, Path... bases) throws Exception {
        for (Path base : bases) { var file = base.resolve(path); if (Files.isRegularFile(file)) return file; }
        throw new AssertionError("Missing source/resource: " + path);
    }
    private static byte[] fixture(String path) throws Exception { return Files.readAllBytes(fixtures.resolve(path)); }
    private static String witness(String path) throws Exception { return new String(fixture(path), StandardCharsets.UTF_8); }
    private static String source(String path) throws Exception {
        return Files.readString(find("src/main/java/cn/academy/port/" + path, client, common, shared, root));
    }
    private static byte[] asset(String path) throws Exception {
        return Files.readAllBytes(find("src/main/resources/assets/academy/" + path, client, root));
    }
    private static void has(String body, String why, String... parts) {
        for (String part : parts) yes(body.contains(part), why + ": " + part);
    }

    public static void main(String[] args) throws Exception {
        root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath();
        client = args.length > 1 ? Path.of(args[1]).toAbsolutePath() : root;
        common = args.length > 2 ? Path.of(args[2]).toAbsolutePath() : root;
        shared = args.length > 3 ? Path.of(args[3]).toAbsolutePath() : root;
        fixtures = find("src/test/resources/classic-phase-generator-client-source/manifest.json", client, root).getParent();
        byte[] manifestBytes = fixture("manifest.json");
        yes(hash(manifestBytes).equals(MANIFEST_SHA256), "immutable original source/asset manifest");
        JsonObject manifest = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
        for (var entry : manifest.getAsJsonArray("source_witnesses")) {
            var expected = entry.getAsJsonObject();
            yes(hash(fixture(expected.get("fixture").getAsString())).equals(expected.get("sha256").getAsString()),
                    "byte-identical original source witness " + expected.get("fixture").getAsString());
        }
        media(manifest);
        textureThresholds();
        originalGeometry();
        originalInventoryAndInfo();
        bindingsAndItemWitnesses();
        System.out.println("ClassicPhaseGeneratorVisualRegressionTest: " + checks + " source/media/mesh/layout checks passed");
    }

    private static void media(JsonObject manifest) throws Exception {
        for (var item : manifest.getAsJsonArray("assets")) {
            var expected = item.getAsJsonObject(); String name = expected.get("path").getAsString();
            byte[] bytes = asset(name);
            yes(bytes.length == expected.get("bytes").getAsInt(), "source asset length " + name);
            yes(hash(bytes).equals(expected.get("sha256").getAsString()), "source asset bytes " + name);
            if (!name.endsWith(".png")) continue;
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            yes(image != null, "valid PNG " + name);
            yes(image.getWidth() == expected.get("width").getAsInt()
                    && image.getHeight() == expected.get("height").getAsInt(), "original image dimensions " + name);
        }
    }

    private static void textureThresholds() throws Exception {
        has(witness("RenderPhaseGen.java"), "original texture-state equation",
                "Resources.getTextureSeq(\"models/ip_gen\", 5)", "(int) Math.round(4.0 * gen.getLiquidAmount() / gen.getTankSize())");
        // Test every source tank amount plus out-of-range input, rather than a few representative levels.
        for (int amount = -10000; amount <= 20000; amount++) {
            int original = Math.max(0, Math.min(4, (int)Math.round(4.0 * amount / 8000)));
            yes(original == ClassicPhaseGeneratorVisualRules.textureIndex(amount, 8000), "original texture at " + amount + " mB");
        }
        int[] boundaries = {0, 999, 1000, 2999, 3000, 4999, 5000, 6999, 7000, 8000};
        int[] states = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4};
        for (int i = 0; i < boundaries.length; i++)
            yes(ClassicPhaseGeneratorVisualRules.textureIndex(boundaries[i], 8000) == states[i], "half-up source transition " + boundaries[i]);
        yes(ClassicPhaseGeneratorVisualRules.textureIndex(8000, 0) == 0, "invalid replacement capacity fails closed");
        eq(.03, ClassicWirelessVisualRules.histogramFraction(0, 6000), "source minimum visible energy bar");
        eq(.5, ClassicWirelessVisualRules.histogramFraction(3000, 6000), "6000 IF source energy scale");
        eq(.5, ClassicWirelessVisualRules.histogramFraction(4000, 8000), "8000 mB source liquid scale");
        eq(1, ClassicWirelessVisualRules.histogramFraction(9000, 8000), "source histogram clamps over-capacity");
    }

    private static void originalGeometry() throws Exception {
        String obj = new String(asset("models/ip_gen.obj"), StandardCharsets.UTF_8);
        var mesh = ClassicDeveloperObj.parse(new java.io.StringReader(obj));
        var positions = new ArrayList<float[]>(); var faces = new ArrayList<int[]>();
        for (String line : obj.split("\\R")) {
            if (line.startsWith("v ")) {
                String[] p = line.split("\\s+");
                positions.add(new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
            } else if (line.startsWith("f ")) {
                String[] p = line.split("\\s+"); yes(p.length == 4, "original OBJ only has triangular faces");
                int[] indices = new int[3];
                for (int i = 0; i < 3; i++) indices[i] = Integer.parseInt(p[i + 1].split("/")[0]) - 1;
                faces.add(indices);
            }
        }
        yes(mesh.positionCount() == 72 && mesh.uvCount() == 43 && mesh.normalCount() == 24,
                "original OBJ topology: 72 positions, 43 UVs, 24 normals");
        yes(mesh.triangles().size() == 98 && mesh.triangles().size() == faces.size(), "all 98 original faces render once");
        for (int i = 0; i < faces.size(); i++) {
            var triangle = mesh.triangles().get(i); var rendered = List.of(triangle.a(), triangle.b(), triangle.c());
            for (int j = 0; j < 3; j++) {
                var p = positions.get(faces.get(i)[j]); var v = rendered.get(j);
                eq(p[0], v.x(), "original face x " + i + ":" + j);
                eq(p[1], v.y(), "original face y " + i + ":" + j);
                eq(p[2], v.z(), "original face z " + i + ":" + j);
            }
        }
        var transformed = ClassicPhaseGeneratorVisualRules.worldBounds(mesh.bounds());
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (var p : positions) {
            minX = Math.min(minX, p[0] + .5); maxX = Math.max(maxX, p[0] + .5);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
            minZ = Math.min(minZ, p[2] + .5); maxZ = Math.max(maxZ, p[2] + .5);
        }
        eq(minX, transformed.minX(), "source translated minX"); eq(maxX, transformed.maxX(), "source translated maxX");
        eq(minY, transformed.minY(), "source translated minY"); eq(maxY, transformed.maxY(), "source translated maxY");
        eq(minZ, transformed.minZ(), "source translated minZ"); eq(maxZ, transformed.maxZ(), "source translated maxZ");
        String original = witness("RenderPhaseGen.java");
        has(original, "original sole mesh transform", "GL11.glTranslated(x + 0.5, y, z + 0.5)", "model.renderAll()");
        yes(!original.contains("glRotate") && !original.contains("glScale"), "original renderer has no facing or scale transform");
        String renderer = source("client/ClassicPhaseGeneratorRenderer.java");
        yes(!renderer.contains("rotationDegrees") && !renderer.contains("poses.scale") && !renderer.contains("Clock.millis"),
                "native model retains original static geometry");
        has(renderer, "native original texture/mesh binding", "generator.liquid(), generator.getTankSize()",
                "ClassicDeveloperRenderer.emitMesh", "RenderType.entityCutout(texture)", "worldBounds(mesh.bounds())");
    }

    private static void originalInventoryAndInfo() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var xml = factory.newDocumentBuilder().parse(new ByteArrayInputStream(fixture("page_inv.xml")));
        var transform = (Element)xml.getElementsByTagName("Component").item(0);
        eq(Double.parseDouble(transform.getElementsByTagName("width").item(0).getTextContent()),
                ClassicPhaseGeneratorVisualRules.INVENTORY_WIDTH, "source inventory width");
        eq(Double.parseDouble(transform.getElementsByTagName("height").item(0).getTextContent()),
                ClassicPhaseGeneratorVisualRules.INVENTORY_HEIGHT, "source inventory height");
        has(witness("GuiPhaseGen.scala"), "phase page and literal histogram",
                "InventoryPage(\"phasegen\")", "TechUI.histEnergy(() => tile.getEnergy, tile.bufferSize)",
                "TechUI.HistElement(\"IF\", new Color(0xffb983fb)", "\"%d mB\".format(tile.getLiquidAmount)");
        has(witness("TechUI.scala"), "original histogram placement and clipping",
                "size(210, 210).scale(0.4)", "size(16, 120).pos(56 + idx * 40, 78)",
                "progress.dir = Direction.UP", "MathUtils.clampd(0.03, 1, elem.value())", "blank(-30)",
                "val color = new Color(0xff25c4ff)", "private var elemY: Double = 10",
                "infoPage.pos(main.transform.width + 7, 5)");
        eq(22.4, ClassicPhaseGeneratorVisualRules.barX(0), "source first histogram x");
        eq(38.4, ClassicPhaseGeneratorVisualRules.barX(1), "source second histogram x");
        eq(6.4, ClassicPhaseGeneratorVisualRules.BAR_WIDTH, "source scaled bar width");
        eq(48, ClassicPhaseGeneratorVisualRules.BAR_HEIGHT, "source scaled bar height");
        eq(59.2, ClassicPhaseGeneratorVisualRules.BAR_BOTTOM, "source scaled bottom with blank offset");
        yes(ClassicPhaseGeneratorVisualRules.ENERGY_COLOR == 0xff25c4ff
                && ClassicPhaseGeneratorVisualRules.LIQUID_COLOR == 0xffb983fb, "source histogram colors");
        String menu = source("phasegen/ClassicPhaseGeneratorMenu.java");
        has(witness("ContainerPhaseGen.java"), "original three slot coordinates", "SLOT_LIQUID_IN, 45, 12", "SLOT_LIQUID_OUT, 112, 51", "SLOT_OUTPUT, 42, 80");
        has(menu, "native three slot coordinates", "45,12", "112,51", "42,80");
        has(witness("TechUIContainer.java"), "original player slot mapping", "6 + i * STEP, 163", "int slot = (4 - i) * 9 + j", "6 + j * STEP, 159 - i * STEP");
        has(menu, "native player source mapping", "6+column*18,163", "(4-row)*9+column", "6+column*18,159-row*18");
        String screen = source("client/ClassicPhaseGeneratorScreen.java");
        has(screen, "native inventory and histogram bindings", "ui/ui_phasegen", "ui/ui_inventory", "parent/parent_background",
                "menu.energy(), ClassicPhaseGeneratorRules.CAPACITY", "menu.liquid(), ClassicPhaseGeneratorRules.TANK_SIZE",
                "y + 64", "y + 72, \"IF\"", "menu.liquid() + \" mB\"", "breatheAlpha", "renderTooltip");
        has(screen, "native info clicks/font lifecycle", "hasClickedOutside", "mouseClicked", "sourceFont.release()");
    }

    private static void bindingsAndItemWitnesses() throws Exception {
        has(witness("ModuleEnergy.java"), "source phase uses ordinary flat ItemBlock", "@RegBlock\n    @RecipeName(\"phase_gen\")\n    public static BlockPhaseGen phaseGen");
        has(witness("TileEntityRegistration.java"), "source TESR registration only", "ClientRegistry.bindTileEntitySpecialRenderer");
        yes(!witness("TileEntityRegistration.java").contains("registerItemRenderer"), "source registration adds no item OBJ renderer");
        has(witness("RenderEmptyBlock.java"), "source block item is not 3D", "boolean shouldRender3DInInventory(int modelID)", "return false");
        JsonObject item = JsonParser.parseString(new String(asset("models/item/phase_gen.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        yes(item.get("parent").getAsString().equals("minecraft:item/generated"), "native item retains flat original icon");
        yes(item.getAsJsonObject("textures").get("layer0").getAsString().equals("academy:blocks/phase_generator"), "native item uses source icon");
        JsonObject block = JsonParser.parseString(new String(asset("models/block/phase_gen.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        yes(block.getAsJsonArray("elements").isEmpty(), "native block relies solely on original source OBJ");
        has(source("client/ClassicPhaseGeneratorClient.java"), "dist-isolated native registration", "value=Dist.CLIENT",
                "ClassicPhaseGenerators.MENU.get(), ClassicPhaseGeneratorScreen::new",
                "ClassicPhaseGenerators.TILE.get(), ClassicPhaseGeneratorRenderer::new",
                "event.registerReloadListener(ClassicPhaseGeneratorModel.INSTANCE)");
        has(source("client/ClassicPhaseGeneratorModel.java"), "reload adapter replaces even missing meshes",
                "resources.getResourceOrThrow(MODEL).open()", "ClassicDeveloperObj.parse(reader)", "mesh = loaded", "models/ip_gen.obj");
        has(source("client/ClassicWirelessClient.java"), "token-bound native phase tab API",
                "openPhaseGenerator(int menuId,BlockPos origin)", "Request(menuId,OPEN_TOKEN,\"open_phasegen\",origin,\"\",\"\")");
    }
}
