package cn.academy.port.fleet.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cn.academy.port.fleet.InteractionLab;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Anchor profiles exported from the same scene and units as each ACM1 mesh.
 * grip/support/mag_grip/charge are authored hand-contact targets, not a request
 * to add legacy lateral or vertical offsets a second time.
 */
public final class GunAnchors {
    public record Point(float x, float y, float z) {
        public Point offset(float dx, float dy, float dz) { return new Point(x + dx, y + dy, z + dz); }
    }
    public record Profile(Point grip, Point support, Point shoulder, Point sight,
                          Point mag, Point magGrip, Point muzzle, Point charge,
                          Point hip, float adsDistance, float magazineDropDistance,
                          float magazineTiltDegrees, float slideTravel) {}

    private static final Map<String, Profile> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_JSON_BYTES = 32 * 1024;
    private GunAnchors() {}

    public static void clearCache() { CACHE.clear(); }

    public static Profile forVariant(int variant) {
        if (variant < 0 || variant >= InteractionLab.GUN_IDS.length) {
            throw new IllegalArgumentException("invalid gun variant: " + variant);
        }
        return forId(InteractionLab.GUN_IDS[variant]);
    }

    public static Profile forId(String id) {
        if (id == null || !id.matches("g0[1-6]_[a-z0-9_]+")) {
            throw new IllegalArgumentException("invalid gun profile ID: " + id);
        }
        return CACHE.computeIfAbsent(id, GunAnchors::load);
    }

    private static Profile load(String id) {
        Profile backup = fallback(id);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("academy", "anchors/" + id + ".json");
        try (var stream = Minecraft.getInstance().getResourceManager().getResource(location).orElseThrow().open()) {
            byte[] bytes = stream.readNBytes(MAX_JSON_BYTES + 1);
            if (bytes.length > MAX_JSON_BYTES) throw new IllegalArgumentException("anchor profile exceeds 32 KiB");
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1
                    || !"m".equals(root.get("units").getAsString())
                    || !"runtime_x_right_y_up_z_forward".equals(root.get("coordinate_system").getAsString())) {
                throw new IllegalArgumentException("unsupported anchor profile version, axes or units");
            }
            JsonObject anchors = root.getAsJsonObject("anchors");
            Point grip = point(anchors, "grip"), support = point(anchors, "support");
            Point shoulder = point(anchors, "shoulder"), sight = point(anchors, "sight");
            Point mag = point(anchors, "mag"), muzzle = point(anchors, "muzzle");
            Point magGrip = anchors.has("mag_grip") ? point(anchors, "mag_grip") : mag.offset(.043f, -.06f, 0);
            Point charge = anchors.has("charge") ? point(anchors, "charge") : grip.offset(.045f, .12f, .09f);
            JsonObject motion = root.has("motion") ? root.getAsJsonObject("motion") : new JsonObject();
            JsonObject camera = root.has("first_person") ? root.getAsJsonObject("first_person") : new JsonObject();
            return new Profile(grip, support, shoulder, sight, mag, magGrip, muzzle, charge,
                    camera.has("hip") ? point(camera, "hip") : backup.hip(),
                    number(camera, "ads_distance", backup.adsDistance(), .1f, 3),
                    number(motion, "magazine_drop", backup.magazineDropDistance(), 0, .6f),
                    number(motion, "magazine_tilt_deg", backup.magazineTiltDegrees(), -45, 45),
                    number(motion, "slide_travel", backup.slideTravel(), 0, .2f));
        } catch (Exception ex) {
            // Logged once per resource-reload generation; the old handover mesh
            // can still be viewed, but this fallback is not a validation pass.
            InteractionLab.LOGGER.warn("Using legacy anchor fallback for {}: {}", id, ex.getMessage());
            return backup;
        }
    }

    private static Point point(JsonObject object, String name) {
        JsonArray array = object.getAsJsonArray(name);
        if (array == null || array.size() != 3) throw new IllegalArgumentException("invalid anchor: " + name);
        float[] xyz = new float[3];
        for (int i = 0; i < 3; i++) {
            xyz[i] = array.get(i).getAsFloat();
            if (!Float.isFinite(xyz[i]) || Math.abs(xyz[i]) > 16) throw new IllegalArgumentException("out-of-range anchor: " + name);
        }
        return new Point(xyz[0], xyz[1], xyz[2]);
    }

    private static float number(JsonObject object, String name, float fallback, float min, float max) {
        if (!object.has(name)) return fallback;
        float value = object.get(name).getAsFloat();
        if (!Float.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("out-of-range profile value: " + name);
        return value;
    }

    /** Compatibility values only; new resources must ship their exported JSON. */
    private static Profile fallback(String id) {
        int index = Math.max(0, Math.min(5, id.charAt(2) - '1'));
        float[] gy = {.019f, .023f, .030f, .021f, .022f, .023f};
        float[] gz = {-.050f, -.099f, -.125f, -.133f, -.117f, -.167f};
        float[] sy = {.005f, .027f, .060f, .060f, .029f, .027f};
        float[] sz = {.044f, .179f, .340f, .337f, .191f, .399f};
        float[] iy = {.177f, .2961f, .277f, .3176f, .250f, .3414f};
        float[] mz = {-.049f, .013f, .040f, .012f, .013f, .013f};
        float[] my = {.038f, .046f, .065f, .042f, .045f, .046f};
        float[] uz = {.1801f, .3817f, .670f, .7248f, .4548f, .7788f};
        Point grip = new Point(-.025f, gy[index], gz[index]);
        Point mag = new Point(0, my[index], mz[index]);
        return new Profile(grip, new Point(.036f, sy[index], sz[index]), new Point(0, .13f, index == 2 ? -.48f : -.38f),
                new Point(0, iy[index], -.08f), mag, mag.offset(.043f, -.06f, 0),
                new Point(0, index == 2 ? .13f : .125f, uz[index]), grip.offset(.045f, .12f, .09f),
                new Point(.23f, -.23f, -.68f), .92f, index == 0 ? .14f : .18f, -12, .045f);
    }
}
