package cn.acamech.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class BoxRig {
    private final Map<String, ModelPart> parts = new LinkedHashMap<>();
    private final Map<String, ModelPart> luminous = new LinkedHashMap<>();
    private final Map<String, float[]> origins = new HashMap<>();
    private final Map<String, List<ModelPart>> chains = new HashMap<>();
    private final List<EnergyHalo> halos = new ArrayList<>();
    private final List<Hydraulic> hydraulics = new ArrayList<>();

    BoxRig(ResourceManager assets, String chassis) {
        try (var reader = assets.openAsReader(cn.acamech.MechMod.id("box/" + chassis + ".json"))) {
            JsonObject model = JsonParser.parseReader(reader).getAsJsonObject();
            int width = model.get("width").getAsInt(), height = model.get("height").getAsInt();
            Map<String, Map<String, ModelPart>> baseChildren = new HashMap<>();
            Map<String, Map<String, ModelPart>> glowChildren = new HashMap<>();
            for (var e : model.getAsJsonArray("bones")) {
                JsonObject bone = e.getAsJsonObject();
                String id = bone.get("id").getAsString();
                JsonArray cubes = bone.getAsJsonArray("cubes");
                float[] pivot = vec(bone.getAsJsonArray("pivot"));
                float[] angles = radians(vec(bone.getAsJsonArray("rotation")));
                String parent = bone.get("parent").isJsonNull() ? null : bone.get("parent").getAsString();
                Map<String, ModelPart> baseMap = new LinkedHashMap<>(), glowMap = new LinkedHashMap<>();
                baseChildren.put(id, baseMap);
                glowChildren.put(id, glowMap);
                ModelPart base = part(cubes, pivot, width, height, false, baseMap);
                ModelPart glow = part(cubes, pivot, width, height, true, glowMap);
                float[] offset = parent == null ? new float[3] : origins.get(parent);
                base.setPos(pivot[0] - offset[0], pivot[1] - offset[1], pivot[2] - offset[2]);
                base.setRotation(angles[0], angles[1], angles[2]);
                base.setInitialPose(base.storePose());
                glow.copyFrom(base);
                parts.put(id, base);
                luminous.put(id, glow);
                origins.put(id, pivot);
                var chain = new ArrayList<ModelPart>();
                if (parent != null) {
                    baseChildren.get(parent).put(id, base);
                    glowChildren.get(parent).put(id, glow);
                    chain.addAll(chains.get(parent));
                }
                chain.add(base);
                var complete = List.copyOf(chain);
                chains.put(id, complete);
                for (var element : cubes) {
                    var cube = element.getAsJsonObject();
                    if (cube.has("halo")) halos.add(makeHalo(cube, pivot, complete));
                }
            }
            for (String name : parts.keySet()) {
                if (!name.endsWith("_ram_l.body") && !name.endsWith("_ram_r.body")) continue;
                String prefix = name.substring(0, name.length() - ".body".length());
                var body = parts.get(name);
                var rod = parts.get(prefix + ".rod");
                var pin = chains.get(prefix + ".pin");
                if (rod == null || pin == null) throw new IllegalStateException("Incomplete hydraulic assembly: " + prefix);
                var path = chains.get(name);
                float length = (float) (originsDistance(name, prefix + ".pin") / 16.0);
                hydraulics.add(new Hydraulic(body, rod, List.copyOf(path.subList(0, path.size() - 1)), pin, length));
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Cannot load ACA Mech box rig " + chassis, ex);
        }
    }

    private static ModelPart part(JsonArray cubes, float[] parentPivot, int w, int h, boolean glow,
                                  Map<String, ModelPart> child) {
        var solid = new ArrayList<ModelPart.Cube>();
        for (var e : cubes) {
            JsonObject box = e.getAsJsonObject();
            if (glow && !box.has("glow")) continue;
            float[] pivot = box.has("pivot") ? vec(box.getAsJsonArray("pivot")) : parentPivot;
            float[] from = vec(box.getAsJsonArray("from"));
            float[] size = vec(box.getAsJsonArray("size"));
            JsonArray uv = box.getAsJsonArray("uv");
            var cube = new ModelPart.Cube(uv.get(0).getAsInt(), uv.get(1).getAsInt(),
                    from[0] - pivot[0], from[1] - pivot[1], from[2] - pivot[2],
                    size[0], size[1], size[2], 0, 0, 0, false, w, h, EnumSet.allOf(Direction.class));
            if (box.has("rotation")) {
                var angled = new ModelPart(java.util.List.of(cube), new HashMap<>());
                float[] angle = radians(vec(box.getAsJsonArray("rotation")));
                angled.setPos(pivot[0] - parentPivot[0], pivot[1] - parentPivot[1], pivot[2] - parentPivot[2]);
                angled.setRotation(angle[0], angle[1], angle[2]);
                child.put("cube_" + child.size(), angled);
            } else {
                solid.add(cube);
            }
        }
        return new ModelPart(solid, child);
    }

    private static EnergyHalo makeHalo(JsonObject cube, float[] joint, List<ModelPart> chain) {
        float[] from = vec(cube.getAsJsonArray("from"));
        float[] size = vec(cube.getAsJsonArray("size"));
        float[] angles = new float[3];
        float[] center = new float[3];
        for (int axis = 0; axis < 3; axis++) center[axis] = from[axis] + size[axis] * .5f;
        if (cube.has("rotation")) {
            angles = radians(vec(cube.getAsJsonArray("rotation")));
            float[] pivot = vec(cube.getAsJsonArray("pivot"));
            var delta = new org.joml.Vector3f(center[0] - pivot[0], center[1] - pivot[1], center[2] - pivot[2]);
            new org.joml.Quaternionf().rotationZYX(angles[2], angles[1], angles[0]).transform(delta);
            center = new float[]{pivot[0] + delta.x, pivot[1] + delta.y, pivot[2] + delta.z};
        }
        for (int axis = 0; axis < 3; axis++) center[axis] -= joint[axis];
        var style = EnergyHalo.Style.valueOf(cube.get("halo").getAsString().toUpperCase(Locale.ROOT));
        return new EnergyHalo(chain, center, size, angles, style);
    }

    void renderHalos(PoseStack stack, VertexConsumer vertices, int tint, float strength, float time) {
        for (var halo : halos) halo.render(stack, vertices, tint, strength, time);
    }

    void set(String id, float x, float y, float z) {
        var part = parts.get(id);
        if (part == null) return;
        PartPose rest = part.getInitialPose();
        part.setRotation(x + rest.xRot, y + rest.yRot, z + rest.zRot);
    }

    void reset() {
        parts.values().forEach(ModelPart::resetPose);
    }

    void articulateHydraulics() {
        for (var hydraulic : hydraulics) hydraulic.pose();
    }

    private double originsDistance(String a, String b) { return pivot(a).distanceTo(pivot(b)); }

    private record Hydraulic(ModelPart body, ModelPart rod, List<ModelPart> owner, List<ModelPart> pin, float restLength) {
        void pose() {
            var inverseOwner = new org.joml.Matrix4f(chainPose(owner).last().pose()).invert();
            var goal = chainPose(pin).last().pose().getTranslation(new Vector3f());
            inverseOwner.transformPosition(goal).sub(body.x / 16f, body.y / 16f, body.z / 16f);
            float length = goal.length();
            if (length < .05f || restLength < .05f) return;
            var rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), goal.div(length));
            setOrientation(body, rotation);
            setOrientation(rod, rotation);
            // The body is rigid. Only the hidden length of rod inside the collar changes.
            var direction = new Vector3f(0, length - restLength, 0).rotate(rotation).mul(16f);
            rod.setPos(body.x + direction.x, body.y + direction.y, body.z + direction.z);
        }
    }

    private static PoseStack chainPose(List<ModelPart> chain) {
        var pose = new PoseStack();
        for (var part : chain) part.translateAndRotate(pose);
        return pose;
    }

    Matrix4f parentTransform(String id) {
        var chain = chains.get(id);
        return chainPose(chain.subList(0, chain.size() - 1)).last().pose();
    }

    void orient(String id, Quaternionf rotation) {
        setOrientation(parts.get(id), rotation);
    }

    private static void setOrientation(ModelPart part, Quaternionf q) {
        // ModelPart reconstructs Z-Y-X in translateAndRotate. Extract exactly that sequence.
        double x = q.x, y = q.y, z = q.z, w = q.w;
        double pitch = Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y));
        double yaw = Math.asin(net.minecraft.util.Mth.clamp(2 * (w * y - z * x), -1, 1));
        double roll = Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
        part.setRotation((float) pitch, (float) yaw, (float) roll);
    }

    void rotate(String id, float pitch, float yaw, float roll) {
        var part = parts.get(id);
        part.setRotation(part.xRot + pitch, part.yRot + yaw, part.zRot + roll);
    }

    void offset(String id, float x, float y, float z) {
        var part = parts.get(id);
        part.setPos(part.x + x, part.y + y, part.z + z);
    }

    Vec3 pivot(String id) {
        float[] p = origins.get(id);
        return new Vec3(p[0], p[1], p[2]);
    }

    Vec3 anchor(String id, float x, float y, float z) {
        var stack = chainPose(chains.get(id));
        Vector3f p = stack.last().pose().transformPosition(x / 16f, y / 16f, z / 16f, new Vector3f());
        return new Vec3(p.x, p.y, p.z);
    }
    Vec3 toLocalDirection(String id, Vec3 modelDirection) {
        var p = parentTransform(id).invert().transformDirection(modelDirection.toVector3f());
        return new Vec3(p.x, p.y, p.z);
    }
    void bakeGlowPose() {
        parts.forEach((id, base) -> luminous.get(id).copyFrom(base));
    }
    ModelPart root() { return parts.get("root"); }
    ModelPart glow() { return luminous.get("root"); }

    private static float[] vec(JsonArray a) {
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
    private static float[] radians(float[] vec) {
        for (int i = 0; i < 3; i++) vec[i] *= (float) (Math.PI / 180);
        return vec;
    }
}
