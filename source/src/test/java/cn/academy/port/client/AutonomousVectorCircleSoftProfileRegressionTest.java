package cn.academy.port.client;

import cn.academy.port.client.AutonomousVectorCircleMesh.Point;
import cn.academy.port.client.AutonomousVectorCircleMesh.Role;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Production profile and unchanged-artwork witnesses; no assertion here is a rendered beauty/performance test. */
public final class AutonomousVectorCircleSoftProfileRegressionTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static int runChecks() {
        checks = 0;
        for (Role role : Role.values()) for (int alpha : new int[]{0, 1, 32, 64, 72, 128, 176, 224, 255}) {
            var stroke = new AutonomousVectorCircleMesh.Stroke(new Point(.3, .4, 1), new Point(.4, .5, 1), .037, alpha, role);
            var originalCore = AutonomousVectorCircleMesh.profile(stroke, true);
            var originalSoft = AutonomousVectorCircleMesh.profile(stroke, false);
            check(AutonomousVectorCircleMesh.profile(stroke, true, false).equals(originalCore)
                    && AutonomousVectorCircleMesh.profile(stroke, false, false).equals(originalSoft), "OFF keeps both complete original profiles");
            check(originalCore.width() == stroke.width() && originalSoft.width() == stroke.width() * 2.3
                    && originalSoft.alpha() == Math.round(alpha * .30), "default entry point retains original width and alpha equations");
            var core = AutonomousVectorCircleMesh.profile(stroke, true, true);
            var soft = AutonomousVectorCircleMesh.profile(stroke, false, true);
            check(core.width() == stroke.width() * .55 && soft.width() == stroke.width() * 2.0, "new layers remain inside the previous full footprint");
            check(core.alpha() == alpha && soft.alpha() == Math.round(alpha * .22), "only the soft shoulder changes opacity");
            check(core.gray() == originalCore.gray() && soft.gray() == originalSoft.gray(), "white-gray vector identity and spent-mark tone remain unchanged");
            check(core.minV() == originalCore.minV() && core.maxV() == originalCore.maxV()
                    && soft.minV() == originalSoft.minV() && soft.maxV() == originalSoft.maxV(), "original texture coordinates are not repainted or scrolled");
            check(stroke.alpha() == alpha && stroke.role() == role && stroke.from().equals(new Point(.3, .4, 1)), "material selection cannot change centerline or quota state");
        }
        try {
            Path root = Path.of("src/main/resources/assets/academy");
            byte[] glow = Files.readAllBytes(root.resolve("textures/effects/glow_line.png"));
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(glow)).equals("0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361"), "original glow-line PNG remains byte-identical");
            byte[] white = Files.readAllBytes(root.resolve("textures/port/white.png"));
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(white)).equals("7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9"), "existing core texture remains byte-identical");
            var image = ImageIO.read(root.resolve("textures/effects/glow_line.png").toFile());
            double textureAlpha = (image.getRGB(image.getWidth() / 2, image.getHeight() / 4) >>> 24) / 255.0;
            double lowShoulder = textureAlpha * Math.round(72 * .22) / 255.0;
            check(lowShoulder > .001 && lowShoulder < .1, "a real original shoulder texel survives the new cutoff where the old material discards it");
            String fragment = Files.readString(root.resolve("shaders/core/autonomous_vector_circle_soft.fsh"));
            check(fragment.contains("fragColor.a<0.001") && !fragment.contains("fragColor.a<0.1)"), "soft shader does not cut away the translucent shoulder");
            check(fragment.contains("texture(Sampler0,texCoord0)*vertexColor*ColorModulator") && !fragment.contains("pow(") && !fragment.contains("sin("), "one original texture sample and ordinary straight-alpha modulation, without added post processing");
            String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/AutonomousVectorCircleEffects.java"));
            check(effects.contains("new Pass[]{Pass.SOFT, Pass.CORE, Pass.WAVE}") && effects.contains("Pass.SIMPLE"), "existing WAVE absorption feedback and whole-group simple fallback are retained");
            check(effects.contains("enhancedMaterial && (!softRequested || AutonomousVectorCircleSoftMaterial.ready())"), "OFF ignores optional shader readiness; ON requires every material before submission");
            check(effects.contains("new Key(pass, soft && (pass == Pass.SOFT || pass == Pass.CORE))"), "old/new material caches stay separate, and WAVE never changes shader");
            check(effects.contains("Commands.literal(\"vector_circle_soft\")") && effects.contains("requestSoft(true)")
                    && effects.contains("requestSoft(false)") && effects.contains("restarting the client restores OFF"), "bounded session-only ON/OFF/status command states the restart behavior");
            check(effects.contains("cancelAutonomousVectorCircleForReload(); clear();") && effects.contains("Material.TYPES.clear()")
                    && effects.contains("storage.close()"), "resource reload retains the existing complete context, buffer and cached-material cleanup");
            check(effects.contains("LEQUAL_DEPTH_TEST") && effects.contains("COLOR_WRITE") && !effects.contains("COLOR_DEPTH_WRITE"), "world depth test remains on and effect layers write no depth");
        } catch (Exception error) { throw new AssertionError(error); }
        check(AutonomousVectorCircleMesh.MAX_VERTICES == 4096 && AutonomousVectorCircleMesh.MAX_CONTEXT_VERTICES * 4 == 3472,
                "scene ceiling and original WAVE-inclusive worst case are unchanged");
        check(AutonomousVectorCircleMesh.MAX_PARTICLES == 0 && AutonomousVectorCircleState.ABSORPTION_TICKS == 10,
                "zero particles and the actual-absorption ten-tick clock remain unchanged");
        return checks;
    }
    public static void main(String[] args) { System.out.println("PASS " + runChecks() + " optional vector-circle soft profile/resource checks; GPU acceptance pending"); }
}
