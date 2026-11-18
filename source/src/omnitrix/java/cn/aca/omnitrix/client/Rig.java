package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Omnitrix;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

final class Rig {
    record Joint(float x, float y, float z, float pitch, float yaw, float roll, float scale, int tint) {
        Joint(float x, float y, float z, float pitch, float yaw, float roll, float scale) { this(x,y,z,pitch,yaw,roll,scale,-1); }
        Joint tint(int color) { return new Joint(x,y,z,pitch,yaw,roll,scale,color); }
        static final Joint ZERO = new Joint(0, 0, 0, 0, 0, 0, 1);
        static Joint bend(float pitch, float yaw, float roll) { return new Joint(0, 0, 0, pitch, yaw, roll, 1); }
    }

    @FunctionalInterface
    interface Motion { Joint at(String name); }

    private static final Map<String, Rig> CACHE = new LinkedHashMap<>();
    static final Motion STILL = name -> Joint.ZERO;
    private static final float UNIT = 1 / 16f;
    private static final float PIX = 1 / 256f;
    private static final int[][] FACES = {
            {1, 0, 4, 5}, {3, 2, 6, 7}, {4, 0, 3, 7}, {0, 1, 2, 3}, {1, 5, 6, 2}, {5, 4, 7, 6}
    };
    private static final int[][] NORMALS = {{0,-1,0},{0,1,0},{-1,0,0},{0,0,-1},{1,0,0},{0,0,1}};

    private record Cube(float[][] vertices, float[][] restVertices, float[][] uv,
                        boolean glow, boolean badge, boolean transparent, boolean masked) {
        static Cube read(JsonArray a, float px, float py, float pz, String boneId, int cubeIndex, String rigPath, float texel) {
            int u = a.get(0).getAsInt(), v = a.get(1).getAsInt();
            float rx = a.get(2).getAsFloat();
            float ry = a.get(3).getAsFloat();
            float rz = a.get(4).getAsFloat();
            float rx2 = a.get(5).getAsFloat();
            float ry2 = a.get(6).getAsFloat();
            float rz2 = a.get(7).getAsFloat();
            float x = rx - px;
            float y = ry - py;
            float z = rz - pz;
            float x2 = rx2 - px;
            float y2 = ry2 - py;
            float z2 = rz2 - pz;
            int dx = Math.max(1, (int)(x2 - x + .0002f));
            int dy = Math.max(1, (int)(y2 - y + .0002f));
            int dz = Math.max(1, (int)(z2 - z + .0002f));
            float[][] faces = {
                    {u+dz+dx,v+dz,u+dz+2*dx,v}, {u+dz,v,u+dz+dx,v+dz},
                    {u+dz+dx,v+dz,u+2*dz+dx,v+dz+dy}, {u+dz,v+dz,u+dz+dx,v+dz+dy},
                    {u,v+dz,u+dz,v+dz+dy}, {u+2*dz+dx,v+dz,u+2*(dz+dx),v+dz+dy}
            };
            for (float[] rect : faces) for (int i=0; i<4; i++) rect[i] *= texel;
            boolean glow = a.get(8).getAsBoolean();
            boolean badge = a.size() > 9 && a.get(9).getAsBoolean();
            boolean transparent = a.size() > 10 && a.get(10).getAsBoolean();

            boolean isEye = glow && boneId.equals("head");
            boolean isWhiteHand = rigPath.contains("alien_x") && boneId.startsWith("arm_") && (cubeIndex == 2 || ry < 15.5f);
            boolean masked = a.size() > 11 ? a.get(11).getAsBoolean() : (badge || isEye || isWhiteHand);

            return new Cube(new float[][]{
                    {x,y,z},{x2,y,z},{x2,y2,z},{x,y2,z},{x,y,z2},{x2,y,z2},{x2,y2,z2},{x,y2,z2}
            }, new float[][]{
                    {rx,ry,rz},{rx2,ry,rz},{rx2,ry2,rz},{rx,ry2,rz},{rx,ry,rz2},{rx2,ry,rz2},{rx2,ry2,rz2},{rx,ry2,rz2}
            }, faces, glow, badge, transparent, masked);
        }

        static Cube readBox(JsonObject data, float px, float py, float pz, float texel) {
            var from = data.getAsJsonArray("from");
            var to = data.getAsJsonArray("to");
            float rx = from.get(0).getAsFloat(), ry = from.get(1).getAsFloat(), rz = from.get(2).getAsFloat();
            float rx2 = to.get(0).getAsFloat(), ry2 = to.get(1).getAsFloat(), rz2 = to.get(2).getAsFloat();
            float x = rx - px, y = ry - py, z = rz - pz;
            float x2 = rx2 - px, y2 = ry2 - py, z2 = rz2 - pz;
            var uvArray = data.getAsJsonArray("uv");
            float[][] faces = new float[6][4];
            for (int side = 0; side < 6; side++) {
                var rect = uvArray.get(side).getAsJsonArray();
                for (int i = 0; i < 4; i++) faces[side][i] = rect.get(i).getAsFloat() * texel;
            }
            return new Cube(new float[][]{
                    {x,y,z},{x2,y,z},{x2,y2,z},{x,y2,z},{x,y,z2},{x2,y,z2},{x2,y2,z2},{x,y2,z2}
            }, new float[][]{
                    {rx,ry,rz},{rx2,ry,rz},{rx2,ry2,rz},{rx,ry2,rz},{rx,ry,rz2},{rx2,ry,rz2},{rx2,ry2,rz2},{rx,ry2,rz2}
            }, faces, data.get("glow").getAsBoolean(), data.get("badge").getAsBoolean(),
                    data.get("transparent").getAsBoolean(), data.get("masked").getAsBoolean());
        }

        void draw(PoseStack.Pose pose, VertexConsumer buffer, int color, int light, int overlay, boolean starfield) {
            for (int side=0; side<6; side++) {
                int[] normal = NORMALS[side];
                float[] rectangle = uv[side];
                for (int i=0; i<4; i++) {
                    int corner = FACES[side][i];
                    float[] at = vertices[corner];
                    float u = rectangle[i == 0 || i == 3 ? 0 : 2];
                    float v = rectangle[i < 2 ? 3 : 1];
                    int vertexColor = starfield ? starColor(restVertices[corner], masked) : color;
                    buffer.addVertex(pose, at[0], at[1], at[2]).setColor(vertexColor).setUv(u,v)
                            .setOverlay(overlay).setLight(light)
                            .setNormal(pose, normal[0], normal[1], normal[2]);
                }
            }
        }
    }

    private static int starColor(float[] position, boolean masked) {
        int r = coordinateByte((position[0] + 32) / 64);
        int g = coordinateByte((position[1] + 4) / 48);
        int b = coordinateByte((position[2] + 32) / 64);
        return (masked ? 0 : 0xff000000) | r << 16 | g << 8 | b;
    }

    private static int coordinateByte(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255)));
    }

    private record Face(int[] corners, float[][] uv, Vector3f normal) {}

    private record Mesh(float[][] positions, int[] starColors, List<Face> faces,
                        boolean glow, boolean badge, boolean transparent) {
        static Mesh read(JsonObject data, float x, float y, float z, float texel) {
            var vertices = data.getAsJsonArray("vertices");
            var positions = new float[vertices.size()][];
            var colors = new int[vertices.size()];
            boolean masked = data.get("masked").getAsBoolean();
            for (int i = 0; i < vertices.size(); i++) {
                var point = vertices.get(i).getAsJsonArray();
                float[] rest = {point.get(0).getAsFloat(), point.get(1).getAsFloat(), point.get(2).getAsFloat()};
                positions[i] = new float[]{rest[0] - x, rest[1] - y, rest[2] - z};
                colors[i] = starColor(rest, masked);
            }
            List<Face> faces = new ArrayList<>();
            for (var entry : data.getAsJsonArray("faces")) {
                var face = entry.getAsJsonObject();
                var source = face.getAsJsonArray("corners");
                int[] corners = new int[source.size()];
                float[][] uv = new float[source.size()][2];
                for (int i = 0; i < source.size(); i++) {
                    corners[i] = source.get(i).getAsInt();
                    var point = face.getAsJsonArray("uv").get(i).getAsJsonArray();
                    uv[i][0] = point.get(0).getAsFloat() * texel;
                    uv[i][1] = point.get(1).getAsFloat() * texel;
                }
                var a = new Vector3f(positions[corners[0]]);
                var b = new Vector3f(positions[corners[1]]).sub(a);
                var c = new Vector3f(positions[corners[2]]).sub(a);
                var normal = b.cross(c);
                if (normal.lengthSquared() > 1e-8f) {
                    faces.add(new Face(corners, uv, normal.normalize()));
                }
            }
            return new Mesh(positions, colors, faces, data.get("glow").getAsBoolean(),
                    data.get("badge").getAsBoolean(), data.get("transparent").getAsBoolean());
        }

        void draw(PoseStack.Pose pose, VertexConsumer buffer, int color, int light, int overlay, boolean starfield) {
            for (Face face : faces) {
                for (int i = 0; i < 4; i++) {
                    // Minecraft batches quads. Repeating the last corner preserves a triangular face.
                    int corner = Math.min(i, face.corners.length - 1);
                    int index = face.corners[corner];
                    float[] point = positions[index];
                    float[] uv = face.uv[corner];
                    buffer.addVertex(pose, point[0], point[1], point[2])
                            .setColor(starfield ? starColors[index] : color).setUv(uv[0], uv[1])
                            .setOverlay(overlay).setLight(light)
                            .setNormal(pose, face.normal.x, face.normal.y, face.normal.z);
                }
            }
        }
    }

    private static final class Bone {
        final String id;
        final float x,y,z,pitch,yaw,roll;
        final List<Cube> cubes = new ArrayList<>();
        final List<Mesh> meshes = new ArrayList<>();
        final List<Bone> children = new ArrayList<>();

        Bone(JsonObject data, String rigPath, float texel) {
            id = data.get("id").getAsString();
            var p = data.getAsJsonArray("p");
            var r = data.getAsJsonArray("r");
            x = p.get(0).getAsFloat();
            y = p.get(1).getAsFloat();
            z = p.get(2).getAsFloat();
            pitch = r.get(0).getAsFloat();
            yaw = r.get(1).getAsFloat();
            roll = r.get(2).getAsFloat();
            var cArray = data.getAsJsonArray("c");
            for (int i = 0; i < cArray.size(); i++) {
                cubes.add(Cube.read(cArray.get(i).getAsJsonArray(), x, y, z, id, i, rigPath, texel));
            }
            if (data.has("boxes")) {
                for (var entry : data.getAsJsonArray("boxes")) {
                    cubes.add(Cube.readBox(entry.getAsJsonObject(), x, y, z, texel));
                }
            }
            if (data.has("meshes")) {
                for (var entry : data.getAsJsonArray("meshes")) {
                    meshes.add(Mesh.read(entry.getAsJsonObject(), x, y, z, texel));
                }
            }
        }

        void draw(PoseStack pose, MultiBufferSource buffers, RenderType solid, RenderType glass, Motion motion,
                  float parentX, float parentY, float parentZ, int color, int light, int overlay, boolean starfield) {
            Joint change = motion.at(id);
            if (change.scale <= 0) return;
            pose.pushPose();
            pose.translate(x-parentX+change.x, y-parentY+change.y, z-parentZ+change.z);
            rotate(pose, pitch+change.pitch, yaw+change.yaw, roll+change.roll);
            if (change.scale != 1) pose.scale(change.scale, change.scale, change.scale);
            for (Cube cube : cubes) {
                int tint = change.tint != -1 && (cube.badge || id.startsWith("circuit_pulse_")) ? change.tint : color;
                cube.draw(pose.last(), buffers.getBuffer(cube.transparent ? glass : solid), tint,
                        cube.glow ? LightTexture.FULL_BRIGHT : light, overlay, starfield);
            }
            for (Mesh mesh : meshes) {
                int tint = change.tint != -1 && (mesh.badge || id.startsWith("circuit_pulse_")) ? change.tint : color;
                mesh.draw(pose.last(), buffers.getBuffer(mesh.transparent ? glass : solid), tint,
                        mesh.glow ? LightTexture.FULL_BRIGHT : light, overlay, starfield);
            }
            for (Bone child : children) child.draw(pose,buffers,solid,glass,motion,x,y,z,color,light,overlay,starfield);
            pose.popPose();
        }
    }

    final float height;
    private final ResourceLocation texture;
    private final Map<String, Bone> bones;
    private final Map<String, PoseClip> clips = new LinkedHashMap<>();
    final boolean starfield;

    private Rig(String path, JsonObject json) {
        height = json.get("height").getAsFloat();
        starfield = path.equals("alien_x") || path.startsWith("x_");
        texture = resolveTexture(path);
        float texel = json.has("texture_size") ? 1f / json.get("texture_size").getAsInt() : PIX;
        bones = new LinkedHashMap<>();
        for (var entry : json.getAsJsonArray("bones")) {
            var data = entry.getAsJsonObject();
            var bone = new Bone(data, path, texel);
            bones.put(bone.id, bone);
            if (!data.get("parent").isJsonNull()) bones.get(data.get("parent").getAsString()).children.add(bone);
        }
        if (json.has("animations")) {
            for (var entry : json.getAsJsonObject("animations").entrySet()) {
                clips.put(entry.getKey(), new PoseClip(entry.getValue().getAsJsonObject()));
            }
        }
    }

    private static ResourceLocation resolveTexture(String path) {
        ResourceLocation loc = Omnitrix.id("textures/rig/" + path + ".png");
        var rm = Minecraft.getInstance().getResourceManager();
        if (rm.getResource(loc).isEmpty() && path.startsWith("x_")) {
            return Omnitrix.id("textures/rig/" + path.substring(2) + ".png");
        }
        return loc;
    }

    static Rig get(String path) {
        return CACHE.computeIfAbsent(path, key -> {
            var rm = Minecraft.getInstance().getResourceManager();
            ResourceLocation rigLoc = Omnitrix.id("rigs/" + key + ".json");
            if (rm.getResource(rigLoc).isEmpty() && key.startsWith("x_")) {
                rigLoc = Omnitrix.id("rigs/" + key.substring(2) + ".json");
            }
            try (var reader = rm.openAsReader(rigLoc)) {
                return new Rig(key, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException e) {
                throw new IllegalStateException("Missing Omnitrix rig " + key, e);
            }
        });
    }

    static void clear() { CACHE.clear(); }

    Motion animate(Motion base, String name, float tick, float weight) {
        PoseClip clip = clips.get(name);
        return clip == null ? base : clip.layer(base, tick, weight);
    }

    boolean hasClip(String name) { return clips.containsKey(name); }

    float armSpan(String side) {
        Bone arm = bones.get("arm_" + side);
        if (arm == null) return 12.0f;
        Bone grip = bones.get("grip_" + side);
        if (grip != null) return Math.max(4.5f, (float) Math.hypot(arm.y - grip.y, arm.z - grip.z));
        Bone elbow = bones.get("elbow_arm_" + side);
        if (elbow != null) return Math.max(5.5f, (float) Math.hypot(arm.y - elbow.y, arm.z - elbow.z) * 1.95f);
        return 11.5f;
    }

    Motion materialize(float fraction) {
        PoseClip clip = clips.get("materialize");
        return clip == null ? STILL : clip.layer(STILL, clip.length * fraction, 1);
    }

    void draw(PoseStack pose, MultiBufferSource buffers, Motion motion, float factor, int color, int light, int overlay) {
        draw(pose, buffers, motion, factor, color, light, overlay, this.starfield);
    }

    void draw(PoseStack pose, MultiBufferSource buffers, Motion motion, float factor, int color, int light, int overlay, boolean starfield) {
        starfield &= StarfieldShader.available();
        pose.pushPose();
        pose.scale(UNIT*factor, UNIT*factor, UNIT*factor);
        RenderType solid = starfield ? StarfieldShader.cutout(texture) : renderType(color);
        RenderType glass = starfield ? StarfieldShader.translucent(texture) : RenderType.entityTranslucent(texture);
        bones.get("root").draw(pose,buffers,solid,glass,motion,0,0,0,color,light,overlay,starfield);
        pose.popPose();
    }

    // Reuse the rig's bind-pose coordinate encoding to animate the blade surface itself.
    void drawAscalonSurface(PoseStack pose, MultiBufferSource buffers, Motion motion, float factor, int light, int overlay) {
        if (!AscalonSurfaceShader.available()) {
            draw(pose, buffers, motion, factor, -1, light, overlay, false);
            return;
        }
        pose.pushPose();
        pose.scale(UNIT * factor, UNIT * factor, UNIT * factor);
        bones.get("root").draw(pose, buffers, AscalonSurfaceShader.cutout(), AscalonSurfaceShader.translucent(),
                motion, 0, 0, 0, -1, light, overlay, true);
        pose.popPose();
    }

    void arm(PoseStack pose, MultiBufferSource buffers, String side, Motion motion, float factor, int color, int light) {
        limb(pose, buffers, "arm_" + side, motion, factor, color, light, this.starfield);
    }

    void arm(PoseStack pose, MultiBufferSource buffers, String side, Motion motion, float factor, int color, int light, boolean starfield) {
        limb(pose, buffers, "arm_" + side, motion, factor, color, light, starfield);
    }

    void limb(PoseStack pose, MultiBufferSource buffers, String boneId, Motion motion, float factor, int color, int light, boolean starfield) {
        Bone bone = bones.get(boneId);
        if (bone == null) return;
        starfield &= StarfieldShader.available();
        pose.pushPose();
        pose.scale(UNIT*factor,UNIT*factor,UNIT*factor);
        RenderType solid = starfield ? StarfieldShader.cutout(texture) : renderType(color);
        RenderType glass = starfield ? StarfieldShader.translucent(texture) : RenderType.entityTranslucent(texture);
        bone.draw(pose,buffers,solid,glass,motion,bone.x,bone.y,bone.z,
                color,light,OverlayTexture.NO_OVERLAY,starfield);
        pose.popPose();
    }

    boolean attach(PoseStack pose, Motion motion, String id, float factor) {
        return attach(pose, motion, bones.get("root"), bones.get(id), factor, 0, 0, 0);
    }

    boolean attachArmGrip(PoseStack pose, String side, Motion motion, float factor) {
        Bone arm = bones.get("arm_" + side);
        return arm != null && attach(pose, motion, arm, bones.get("grip_" + side), factor, arm.x, arm.y, arm.z);
    }

    private static boolean attach(PoseStack pose, Motion motion, Bone from, Bone target, float factor,
                                  float x, float y, float z) {
        if (target == null || !contains(from, target)) return false;
        pose.scale(UNIT*factor, UNIT*factor, UNIT*factor);
        chain(pose, from, target, motion, x, y, z);
        pose.scale(16/factor, 16/factor, 16/factor);
        return true;
    }

    private static boolean chain(PoseStack pose, Bone b, Bone target, Motion motion, float x,float y,float z) {
        if (b != target && b.children.stream().noneMatch(child -> contains(child,target))) return false;
        Joint offset = motion.at(b.id);
        pose.translate(b.x-x+offset.x,b.y-y+offset.y,b.z-z+offset.z);
        rotate(pose,b.pitch+offset.pitch,b.yaw+offset.yaw,b.roll+offset.roll);
        if (offset.scale != 1) pose.scale(offset.scale, offset.scale, offset.scale);
        if (b == target) return true;
        for (Bone child : b.children) if (chain(pose,child,target,motion,b.x,b.y,b.z)) return true;
        return false;
    }

    private static boolean contains(Bone bone, Bone target) {
        return bone == target || bone.children.stream().anyMatch(child -> contains(child,target));
    }

    private RenderType renderType(int color) {
        return color >>> 24 == 255 ? RenderType.entityCutoutNoCull(texture) : RenderType.entityTranslucent(texture);
    }

    static void rotate(PoseStack pose, float pitch,float yaw,float roll) {
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        if (yaw != 0) pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (pitch != 0) pose.mulPose(Axis.XP.rotationDegrees(pitch));
    }
}
