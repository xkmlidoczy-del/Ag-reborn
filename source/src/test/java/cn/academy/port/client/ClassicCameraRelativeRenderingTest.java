package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.lang.reflect.*;
import java.util.*;

/** Actual renderer vertices must retain the same local ribbon at far-world positions. */
public final class ClassicCameraRelativeRenderingTest {
    static int checks;
    static List<float[]> arc(Vec3 camera, Vec3 localOrigin, boolean baseline) throws Exception {
        var vertices = new ArrayList<float[]>();
        VertexConsumer out = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    if (method.getName().equals("addVertex"))
                        vertices.add(new float[]{(float) args[0], (float) args[1], (float) args[2]});
                    return proxy;
                });
        Class<?> type = Class.forName("cn.academy.port.client.ClassicEffects$WeakArc");
        Constructor<?> constructor = type.getDeclaredConstructor(Vec3.class, Vec3.class, double.class);
        constructor.setAccessible(true);
        Object effect = constructor.newInstance(camera.add(localOrigin), new Vec3(.3, -.2, .8).normalize(), 7.0);
        // Declared fixed visual RNG input; camera relocation must not sample a different source ribbon.
        Field rotation = ClassicArcGeometry.class.getDeclaredField("ROTATION_RANDOM");
        rotation.setAccessible(true);
        ((Random) rotation.get(null)).setSeed(8921);
        PoseStack poses = new PoseStack();
        if (baseline) poses.translate(-camera.x, -camera.y, -camera.z);
        Method method = baseline
                ? ClassicEffects.class.getDeclaredMethod("renderArc", type, Matrix4f.class, VertexConsumer.class)
                : ClassicEffects.class.getDeclaredMethod("renderArc", type, Vec3.class, Matrix4f.class, VertexConsumer.class);
        method.setAccessible(true);
        if (baseline) method.invoke(null, effect, poses.last().pose(), out);
        else method.invoke(null, effect, camera, poses.last().pose(), out);
        return vertices;
    }
    public static void main(String[] args) throws Exception {
        boolean baseline = Arrays.asList(args).contains("--baseline");
        // Declared deterministic render input, not a change to gameplay RNG.
        Class<?> type = Class.forName("cn.academy.port.client.ClassicEffects$WeakArc");
        Field patterns = type.getDeclaredField("PATTERNS");
        patterns.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<List<List<ClassicArcGeometry.Segment>>> actualPatterns =
                (List<List<List<ClassicArcGeometry.Segment>>>) patterns.get(null);
        Field generationRotation = ClassicArcGeometry.class.getDeclaredField("ROTATION_RANDOM");
        generationRotation.setAccessible(true);
        ((Random) generationRotation.get(null)).setSeed(4218);
        actualPatterns.set(0, ClassicArcGeometry.generate(new Random(7345), 20, 6, .1, 1.5, .4, .7));
        Vec3 origin = new Vec3(.37, -.12, .7);
        List<float[]> expected = arc(Vec3.ZERO, origin, baseline);
        if (expected.size() < 100) throw new AssertionError("actual full classic branch geometry was not emitted");
        int mismatches = 0;
        double largestError = 0;
        for (Vec3 camera : List.of(new Vec3(64.5, 72.25, -32.75),
                new Vec3(14502577.5, -59, 4277858.5),
                new Vec3(29999999.75, 319.875, -29999999.125),
                new Vec3(-29999999.5, -63.75, 29999999.5))) {
            List<float[]> actual = arc(camera, origin, baseline);
            if (actual.size() != expected.size()) throw new AssertionError("world translation changed branch topology");
            for (int i = 0; i < actual.size(); i++) for (int axis = 0; axis < 3; axis++) {
                checks++;
                double error = Math.abs(actual.get(i)[axis] - expected.get(i)[axis]);
                largestError = Math.max(largestError, error);
                if (error > 1.0e-5) mismatches++;
            }
        }
        if (baseline && mismatches == 0) throw new AssertionError("baseline must expose actual far-world distortion");
        if (!baseline && mismatches != 0) throw new AssertionError("camera-relative ribbons differ in " + mismatches + " vertices");
        System.out.println("ClassicCameraRelativeRenderingTest: " + checks + " actual cached MC renderer vertex comparisons; "
                + mismatches + " mismatches; maximum drift=" + largestError + "; baseline=" + baseline
                + ". GPU/action/sound acceptance remains separate.");
    }
}
