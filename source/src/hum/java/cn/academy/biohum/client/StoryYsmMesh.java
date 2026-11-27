package cn.academy.biohum.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The release's authored cubes and UVs, driven by ACA's six existing joints. */
final class StoryYsmMesh {
    private static final float PLAYER_FOOT = 24f / 16f;
    private static final float CAST_GRIP_Y = 9.4f / 16f;
    private static final float CAST_GRIP_Z = -.35f / 16f;
    private static final float SHEATH_X = 5f / 16f;
    private static final float SHEATH_Y = 10f / 16f;

    private enum Joint {
        BODY("Body"), HEAD("Head"), RIGHT_ARM("RightArm"), LEFT_ARM("LeftArm"),
        RIGHT_LEG("RightLeg"), LEFT_LEG("LeftLeg");

        final String bone;

        Joint(String bone) { this.bone = bone; }

        ModelPart part(NpcModel model) {
            return switch (this) {
                case BODY -> model.body;
                case HEAD -> model.head;
                case RIGHT_ARM -> model.rightArm;
                case LEFT_ARM -> model.leftArm;
                case RIGHT_LEG -> model.rightLeg;
                case LEFT_LEG -> model.leftLeg;
            };
        }
    }

    private enum Detail {
        BODY, EYES_OPEN, EYES_CLOSED, SMILE_LID, MOUTH_REST, MOUTH_SMILE, MOUTH_ANGRY, BACK_SWORD, HIDDEN;

        static Detail of(String role, String bone, Detail inherited) {
            if (bone.startsWith("Emote") || bone.startsWith("ysmGlow")) return HIDDEN;
            if (role.equals("kanzaki") && bone.equals("RifleLocator")) return BACK_SWORD;
            return switch (bone) {
                case "Eyes", "Pupils", "EyeShapeL", "EyeShapeR" -> EYES_OPEN;
                case "EyelidsClosed" -> EYES_CLOSED;
                case "SmileLidL", "SmileLidR" -> SMILE_LID;
                case "MouthRest" -> MOUTH_REST;
                case "MouthSmile" -> MOUTH_SMILE;
                case "MouthAngry" -> MOUTH_ANGRY;
                case "MouthCat", "MouthOpen", "MouthO" -> HIDDEN;
                default -> inherited;
            };
        }
    }

    private record Face(float[] vertices, float nx, float ny, float nz) {
        void emit(PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int color) {
            for (int i = 0; i < vertices.length; i += 5) {
                out.addVertex(pose, vertices[i], vertices[i+1], vertices[i+2]).setColor(color)
                        .setUv(vertices[i+3], vertices[i+4]).setOverlay(overlay).setLight(light)
                        .setNormal(pose, nx, ny, nz);
            }
        }
    }

    private record Part(Joint joint, Vector3f pivot, Vector3f grip, Map<Detail, List<Face>> faces) {
        void drawFaces(NpcModel model, PoseStack.Pose pose, VertexConsumer out, int light, int overlay, int color) {
            for (var batch : faces.entrySet()) {
                boolean draw = switch (batch.getKey()) {
                    case BODY -> true;
                    case EYES_OPEN -> !model.storyEyesClosed();
                    case EYES_CLOSED -> model.storyEyesClosed();
                    case SMILE_LID -> model.storyExpression() == 1 && !model.storyEyesClosed();
                    case MOUTH_REST -> model.storyExpression() == 0;
                    case MOUTH_SMILE -> model.storyExpression() == 1;
                    case MOUTH_ANGRY -> model.storyExpression() == 2;
                    case BACK_SWORD -> !model.storyUsesAcaSword();
                    case HIDDEN -> false;
                };
                if (draw) for (var face : batch.getValue()) face.emit(pose, out, light, overlay, color);
            }
        }
    }

    private record JointPose(Vector3f pivot, Quaternionf rotation) {
        void apply(PoseStack poses) {
            poses.translate(pivot.x, pivot.y, pivot.z);
            poses.mulPose(rotation);
        }

        Vector3f transform(Vector3f local) {
            return rotation.transform(new Vector3f(local)).add(pivot);
        }
    }

    private final Map<Joint, Part> parts;
    private final Vector3f sheath;

    private StoryYsmMesh(Map<Joint, Part> parts, Vector3f sheath) {
        this.parts = parts;
        this.sheath = sheath;
    }

    static StoryYsmMesh bake(String role, String geometry) {
        var raw = KeyFramesAdapter.GEO_GSON.fromJson(geometry, Model.class);
        var baked = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(raw));
        var anchors = new HashMap<String, Vector3f>();
        for (var root : baked.topLevelBones()) pivots(root, new PoseStack(), anchors);
        // Normalize the sole and neck to the standing ACA model. The NPC's
        // regular phase scale applies to the whole character afterward.
        float scale = PLAYER_FOOT / requiredPivot(anchors, Joint.HEAD.bone).y;
        if (!Float.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid story model scale");
        var parts = new EnumMap<Joint, Part>(Joint.class);
        for (Joint joint : Joint.values()) {
            var pivot = project(requiredPivot(anchors, joint.bone), scale);
            Vector3f grip = null;
            if (joint == Joint.RIGHT_ARM || joint == Joint.LEFT_ARM) {
                String hand = joint == Joint.RIGHT_ARM ? "RightHandLocator" : "LeftHandLocator";
                grip = project(requiredPivot(anchors, hand), scale).sub(pivot);
            }
            parts.put(joint, new Part(joint, pivot, grip, new EnumMap<>(Detail.class)));
        }
        for (var root : baked.topLevelBones()) {
            collect(role, root, new PoseStack(), parts.get(Joint.BODY), Detail.BODY, parts, scale);
        }
        // The original scabbard and hilt share a pivot. Move both to the
        // model's own belt instead of leaving them beside the wider vanilla hip.
        var sheath = project(requiredPivot(anchors, "LeftWaistLocator"), scale).sub(parts.get(Joint.BODY).pivot());
        return new StoryYsmMesh(parts, sheath);
    }

    private static Vector3f requiredPivot(Map<String, Vector3f> pivots, String bone) {
        var pivot = pivots.get(bone);
        if (pivot == null) throw new IllegalArgumentException("Missing story bone " + bone);
        return pivot;
    }

    private static void pivots(GeoBone bone, PoseStack stack, Map<String, Vector3f> out) {
        stack.pushPose();
        RenderUtil.prepMatrixForBone(stack, bone);
        out.put(bone.getName(), stack.last().pose().transformPosition(
                bone.getPivotX() / 16, bone.getPivotY() / 16, bone.getPivotZ() / 16, new Vector3f()));
        for (var child : bone.getChildBones()) pivots(child, stack, out);
        stack.popPose();
    }

    private static Vector3f project(Vector3f p, float scale) {
        // GeckoLib's local X and Y face the opposite way from the PlayerModel mesh.
        return new Vector3f(-p.x * scale, PLAYER_FOOT - p.y * scale, p.z * scale);
    }

    private static void collect(String role, GeoBone bone, PoseStack stack, Part parent, Detail inherited,
                                Map<Joint, Part> parts, float scale) {
        if (bone.isHidden() || Boolean.TRUE.equals(bone.shouldNeverRender())) return;
        var detail = Detail.of(role, bone.getName(), inherited);
        if (detail == Detail.HIDDEN) return;
        var target = parent;
        for (Joint joint : Joint.values()) if (joint.bone.equals(bone.getName())) target = parts.get(joint);
        stack.pushPose();
        RenderUtil.prepMatrixForBone(stack, bone);
        if (!bone.getCubes().isEmpty()) {
            var faces = target.faces().computeIfAbsent(detail, key -> new ArrayList<>());
            for (var cube : bone.getCubes()) {
                stack.pushPose();
                RenderUtil.translateToPivotPoint(stack, cube);
                RenderUtil.rotateMatrixAroundCube(stack, cube);
                RenderUtil.translateAwayFromPivotPoint(stack, cube);
                var transform = stack.last();
                for (var quad : cube.quads()) {
                    if (quad == null) continue;
                    var normal = transform.normal().transform(new Vector3f(quad.normal()));
                    RenderUtil.fixInvertedFlatCube(cube, normal);
                    float[] vertices = new float[quad.vertices().length * 5];
                    int i = 0;
                    for (var vertex : quad.vertices()) {
                        var position = transform.pose().transformPosition(vertex.position(), new Vector3f());
                        var local = project(position, scale).sub(target.pivot());
                        if (detail == Detail.EYES_CLOSED) local.z -= .315f / 16 * scale;
                        if (detail == Detail.SMILE_LID) local.z -= .32f / 16 * scale;
                        vertices[i++] = local.x;
                        vertices[i++] = local.y;
                        vertices[i++] = local.z;
                        vertices[i++] = vertex.texU();
                        vertices[i++] = vertex.texV();
                    }
                    faces.add(new Face(vertices, -normal.x, -normal.y, normal.z));
                }
                stack.popPose();
            }
        }
        for (var child : bone.getChildBones()) collect(role, child, stack, target, detail, parts, scale);
        stack.popPose();
    }

    private static JointPose poseFor(NpcModel model, Part part) {
        var joint = part.joint().part(model);
        var base = new Vector3f(joint.x / 16f, joint.y / 16f, joint.z / 16f);
        var rest = joint.getInitialPose();
        var offset = new Vector3f(part.pivot()).sub(rest.x / 16f, rest.y / 16f, rest.z / 16f);
        if (part.joint() != Joint.RIGHT_LEG && part.joint() != Joint.LEFT_LEG) {
            // ACA rotates the chest and the shoulder/neck translations together.
            // The model-space offset to each authored pivot must turn with them.
            new Quaternionf().rotationZYX(model.body.zRot, model.body.yRot, model.body.xRot).transform(offset);
        }
        var position = new Vector3f(base).add(offset);
        var rotation = new Quaternionf().rotationZYX(joint.zRot, joint.yRot, joint.xRot);
        if (part.grip() != null) {
            float weight = model.storyGripWeight(part.joint() == Joint.RIGHT_ARM);
            if (weight > 0) {
                var palmDirection = rotation.transform(new Vector3f(part.grip()));
                var castDirection = rotation.transform(new Vector3f(0, CAST_GRIP_Y, CAST_GRIP_Z)).sub(offset);
                if (castDirection.lengthSquared() > 1e-6f) {
                    // Keep the authored arm length, including its sleeve and fingers.
                    // Only the active cast hand aims toward ACA's release point.
                    var aim = new Quaternionf().rotationTo(palmDirection.normalize(), castDirection.normalize());
                    aim.mul(rotation);
                    rotation.slerp(aim, weight);
                }
            }
        }
        return new JointPose(position, rotation);
    }

    Vector3f gripPosition(NpcModel model, boolean right) {
        var part = parts.get(right ? Joint.RIGHT_ARM : Joint.LEFT_ARM);
        return poseFor(model, part).transform(part.grip());
    }

    void translateToGrip(NpcModel model, boolean right, PoseStack poses) {
        var part = parts.get(right ? Joint.RIGHT_ARM : Joint.LEFT_ARM);
        poseFor(model, part).apply(poses);
        poses.translate(part.grip().x, part.grip().y, part.grip().z);
    }

    void translateToHeldPart(NpcModel model, boolean right, PoseStack poses) {
        translateToGrip(model, right, poses);
        poses.translate(0, -CAST_GRIP_Y, -CAST_GRIP_Z);
    }

    void translateToSheath(NpcModel model, PoseStack poses) {
        poseFor(model, parts.get(Joint.BODY)).apply(poses);
        poses.translate(sheath.x - SHEATH_X, sheath.y - SHEATH_Y, sheath.z);
    }

    void render(NpcModel model, PoseStack poses, VertexConsumer out, int light, int overlay, int color) {
        for (var part : parts.values()) {
            if (!part.joint().part(model).visible) continue;
            poses.pushPose();
            poseFor(model, part).apply(poses);
            part.drawFaces(model, poses.last(), out, light, overlay, color);
            poses.popPose();
        }
    }
}
