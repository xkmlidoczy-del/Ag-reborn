package cn.academy.port.client;

import cn.academy.port.skill.VectorFinalSourceFixtures;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/** Actual ball-uniform helper versus the native vertex-shader matrix contract; no game bootstrap. */
public final class PlasmaCameraSpaceRegressionTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        check(Double.isFinite(actual) && Double.isFinite(expected)
                && Math.abs(actual - expected) <= tolerance,
                message + " actual=" + actual + " expected=" + expected);
    }

    // Camera.setRotation in the pinned 21.1.252 native sources uses this YXZ quaternion.
    // GameRenderer.renderLevel passes its conjugate as the camera model-view matrix.
    private static Matrix4f nativeModelView(float yaw, float pitch, float roll) {
        float radians = (float) (Math.PI / 180.0);
        var camera = new Quaternionf().rotationYXZ((float) Math.PI - yaw * radians,
                -pitch * radians, -roll * radians);
        return new Matrix4f().rotation(camera.conjugate());
    }

    private static Matrix4f cameraPose(double x, double y, double z) {
        return new Matrix4f().translation((float) -x, (float) -y, (float) -z);
    }

    // Independent vertex path: the GPU sees ModelViewMat * already-pose-transformed Position.
    private static Vector4f shaderCameraPoint(Matrix4f pose, Matrix4f modelView,
                                               double x, double y, double z) {
        var combined = new Matrix4f(modelView).mul(pose);
        var result = combined.transform(new Vector4f((float) x, (float) y, (float) z, 1));
        return result.div(result.w);
    }

    private static void mapping(Matrix4f pose, Matrix4f modelView,
                                double x, double y, double z, float size, String label) {
        var savedPose = new Matrix4f(pose);
        var savedView = new Matrix4f(modelView);
        var gpu = shaderCameraPoint(pose, modelView, x, y, z);
        var uniform = ClassicPlasmaCameraSpace.transform(pose, modelView, x, y, z, size);
        // Combining translated float matrices can round differently from sequential transforms.
        // These normal-coordinate fixtures are below 2048; this tolerance is below 1 mm.
        near(uniform.x, gpu.x, .0008, label + " camera X agrees with vertex path");
        near(uniform.y, gpu.y, .0008, label + " camera Y agrees with vertex path");
        near(uniform.z, gpu.z, .0008, label + " camera Z agrees with vertex path");
        near(uniform.w, size, 0, label + " original ball size occupies W after mapping");
        check(pose.equals(savedPose) && modelView.equals(savedView), label + " input matrices unchanged");
    }

    private static double[] shaderDensityPoint(Vector4f point, float size) {
        // Both original shader paths deliberately negate camera Z after transformation.
        return new double[] {point.x, point.y, -point.z, size};
    }

    private static String resource(String path) throws Exception {
        try (var input = PlasmaCameraSpaceRegressionTest.class.getResourceAsStream(path)) {
            check(input != null, "required shader resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void nativeMappingCases() {
        double[][] cameras = {{0, 0, 0}, {80.084257, 201.62, 14.547469},
                {-1024.375, 71.125, 997.625}};
        double[][] offsets = {{0, 13.38, 0}, {2.125, -1.75, 4.625}, {-3.5, 2.25, -6.75}};
        for (var camera : cameras) {
            var pose = cameraPose(camera[0], camera[1], camera[2]);
            for (float yaw : new float[] {-170, -90, 0, 37, 110}) {
                for (float pitch : new float[] {-90, -67, 0, 41, 90}) {
                    for (float roll : new float[] {0, 18}) {
                        var view = nativeModelView(yaw, pitch, roll);
                        for (int i = 0; i < offsets.length; i++) {
                            var offset = offsets[i];
                            mapping(pose, view, camera[0] + offset[0], camera[1] + offset[1],
                                    camera[2] + offset[2], new float[] {.125F, 1.375F, 1.5F}[i],
                                    "yaw=" + yaw + " pitch=" + pitch + " roll=" + roll);
                        }
                    }
                }
            }
        }

        var pose = new Matrix4f().translation(-12.5F, 37.25F, -19.75F)
                .rotateY(.37F).rotateX(-.21F);
        var view = nativeModelView(110, -67, 18).translate(.4F, -.8F, 1.2F);
        for (float size : new float[] {0, .125F, 1.5F}) {
            mapping(pose, view, 15.75, -20.125, 25.875, size,
                    "noncommuting pose/view and homogeneous W=1 size=" + size);
        }
        var small = ClassicPlasmaCameraSpace.transform(pose, view, 15.75, -20.125, 25.875, .125F);
        var large = ClassicPlasmaCameraSpace.transform(pose, view, 15.75, -20.125, 25.875, 1.5F);
        check(small.x == large.x && small.y == large.y && small.z == large.z,
                "ball size does not scale affine translation before W is replaced");

        var rotation = nativeModelView(37, -41, 0);
        var atOrigin = ClassicPlasmaCameraSpace.transform(new Matrix4f(), rotation,
                2.125, 13.38, -3.75, 1.25F);
        var translated = ClassicPlasmaCameraSpace.transform(cameraPose(80.084257, 201.62, 14.547469),
                rotation, 82.209257, 215, 10.797469, 1.25F);
        near(translated.x, atOrigin.x, .00005, "normal-coordinate camera translation preserves relative X");
        near(translated.y, atOrigin.y, .00005, "normal-coordinate camera translation preserves relative Y");
        near(translated.z, atOrigin.z, .00005, "normal-coordinate camera translation preserves relative Z");
    }

    private static void existingLargeCoordinateLimit() {
        // At 2^24 a quarter-block world offset is lost by the pre-existing float narrowing.
        // This camera-space repair intentionally preserves that limitation; subtracting camera
        // in double precision would be a separate precision change to the whole renderer.
        double camera = 16_777_216;
        var pose = cameraPose(camera, 0, -camera);
        var actual = ClassicPlasmaCameraSpace.transform(pose, new Matrix4f(),
                camera + .25, 13.375, -camera + .25, 1.375F);
        var gpu = shaderCameraPoint(pose, new Matrix4f(), camera + .25, 13.375, -camera + .25);
        near(actual.x, 0, 0, "documented existing large-world X fraction loss");
        near(actual.z, 0, 0, "documented existing large-world Z fraction loss");
        near(actual.y, 13.375, 0, "unaffected representable Y offset");
        near(actual.w, 1.375, 0, "size preserved at large world coordinates");
        check(actual.x == gpu.x && actual.y == gpu.y && actual.z == gpu.z,
                "large-coordinate identity-view limit matches the unchanged float vertex path");
        check(Math.abs(actual.x - .25) == .25 && Math.abs(actual.z - .25) == .25,
                "test records float precision loss rather than claiming large-coordinate modernization");
    }

    private static void skywardOldPathNegativeControl() {
        double x = 80.084257, y = 215, z = 14.547469;
        var pose = cameraPose(x, 201.62, z);
        var view = nativeModelView(110, -90, 0);
        var quadCenter = shaderCameraPoint(pose, view, x, y, z);
        double[] cameraRay = {quadCenter.x, quadCenter.y, -quadCenter.z};
        near(cameraRay[0], 0, .0001, "skyward billboard center camera X");
        near(cameraRay[1], 0, .0001, "skyward billboard center camera Y");
        near(cameraRay[2], 13.38, .0001, "skyward billboard center positive ray Z");

        var wrongCenter = pose.transform(new Vector4f((float) x, (float) y, (float) z, 1));
        double displacement = Math.sqrt(Math.pow(wrongCenter.x - cameraRay[0], 2)
                + Math.pow(wrongCenter.y - cameraRay[1], 2)
                + Math.pow(-wrongCenter.z - cameraRay[2], 2));
        check(displacement > 18.9 && displacement < 19,
                "old path leaves uniform cluster about 18.922 m away from the ray center");

        for (int seed : new int[] {0, 1, 7, 107, 313, 20261009}) {
            var correct = new ArrayList<double[]>();
            var old = new ArrayList<double[]>();
            for (var ball : ClassicVectorFinalTimeline.balls(new Random(seed))) {
                var offset = ClassicVectorFinalTimeline.ball(ball, .016F);
                var mapped = ClassicPlasmaCameraSpace.transform(pose, view, x + offset[0],
                        y + offset[1], z + offset[2], (float) offset[3]);
                correct.add(shaderDensityPoint(mapped, mapped.w));
                var missingView = pose.transform(new Vector4f((float) (x + offset[0]),
                        (float) (y + offset[1]), (float) (z + offset[2]), 1));
                old.add(shaderDensityPoint(missingView, (float) offset[3]));
            }
            var oldPixel = ClassicVectorFinalTimeline.rayMarch(cameraRay, old, 1);
            var correctPixel = ClassicVectorFinalTimeline.rayMarch(cameraRay, correct, 1);
            near(oldPixel[3], 0, 0, "old-path skyward center remains empty at mature alpha seed=" + seed);
            check(correctPixel[3] > .2 && correctPixel[3] <= 1,
                    "camera-space repair restores nonempty classic center density seed=" + seed);
            for (double channel : correctPixel) check(Double.isFinite(channel), "correct classic raymarch finite");
        }
    }

    private static void unchangedOriginalShaderContract() throws Exception {
        String body = VectorFinalSourceFixtures.source("academy/PlasmaBodyEffect.scala");
        check(body.contains("acquireMatrix(GL_MODELVIEW_MATRIX, matrix)")
                        && body.contains("Matrix4f.transform(matrix, pos, null)")
                        && body.contains("camPos.x, camPos.y, -camPos.z, ball.size"),
                "hashed original source transforms relative balls by model-view before Z reflection");
        String original = VectorFinalSourceFixtures.source("academy/plasma_body.frag");
        String modern = resource("/assets/academy/shaders/core/classic_plasma_body.fsh");
        for (String function : List.of("vec4 rayMarch", "void main")) {
            String expected = original.substring(original.indexOf(function))
                    .replace("gl_FragColor = rc;", "fragColor = rc;");
            check(modern.substring(modern.indexOf(function)).equals(expected),
                    "unchanged pinned original shader tail " + function);
        }
        check(modern.contains("cam.z = -cam.z;") && modern.contains("float distance = max(0.1, length(position - balls[i].xyz))")
                        && modern.contains("ret += alpha * balls[i].w / (distance * distance);"),
                "original ray Z reflection and density formula retained");
        String vertex = resource("/assets/academy/shaders/core/classic_plasma_body.vsh");
        check(vertex.contains("ProjMat * ModelViewMat * vec4(Position, 1.0)")
                        && vertex.contains("vec4 cam = ModelViewMat * vec4(Position, 1.0)")
                        && vertex.contains("camspace = cam.xyz / cam.w"),
                "vertex path still owns model-view projection and homogeneous camera position");
        String upload = Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicVectorFinalShader.java"));
        check(upload.contains("u.set(b.x,b.y,-b.z,b.w)"), "uniform upload retains original downstream Z reflection");
        String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/ClassicVectorFinalEffects.java"));
        check(effects.contains("ClassicPlasmaCameraSpace.transform(poses.last().pose(),")
                        && effects.contains("RenderSystem.getModelViewMatrix(),p.at.x+at[0],p.at.y+at[1],p.at.z+at[2]"),
                "live renderer supplies its pose and the same RenderSystem model-view used by the GPU");
        check(effects.contains("ClassicVectorFinalShader.set(p.alpha*p.alpha,ballData)")
                        && effects.contains("quad(out,poses.last().pose(),-11,-11,22,0,1)"),
                "original squared alpha and size-22 billboard retained");
    }

    public static void main(String[] args) throws Exception {
        nativeMappingCases();
        existingLargeCoordinateLimit();
        skywardOldPathNegativeControl();
        unchangedOriginalShaderContract();
        System.out.println("PASS " + checks + " classic Plasma camera-space, original-shader and old-path negative-control checks");
    }
}
