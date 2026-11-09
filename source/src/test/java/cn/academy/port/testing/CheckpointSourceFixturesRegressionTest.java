package cn.academy.port.testing;

import java.nio.charset.StandardCharsets;

/** No source working directory needed. Explicitly tests each fail-closed boundary. */
public final class CheckpointSourceFixturesRegressionTest {
    private static int checks;
    private static void check(boolean result, String reason) { checks++; if (!result) throw new AssertionError(reason); }
    private static void failure(Checked action, String reason) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (AssertionError expected) { rejected = true; }
        check(rejected, reason);
    }
    private interface Checked { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        for (String path : new String[]{"academycraft-1.0.7/ScatterBomb.scala.txt", "academycraft-1.0.7/LightShield.scala.txt", "academycraft-1.0.7/RenderMdShield.java.txt", "lambdalib-1.2.3/Motion3D.java.txt", "academycraft-1.0.7/mine-detect-original-assets.json"}) {
            String original = CheckpointSourceFixtures.pinnedText(path);
            check(!original.isBlank(), "Required fixture loaded and canonical SHA checked: " + path);
            byte[] bytes = original.getBytes(StandardCharsets.UTF_8); bytes[bytes.length - 1] ^= 1;
            failure(() -> CheckpointSourceFixtures.verifyPinned(path, bytes), "A changed byte fails: " + path);
            failure(() -> CheckpointSourceFixtures.verifyPinned(path, new byte[0]), "Empty fixture fails: " + path);
        }
        failure(() -> CheckpointSourceFixtures.pinnedText("absent.txt"), "Missing canonical fixture fails");
        failure(() -> CheckpointSourceFixtures.verifyPinned("unlisted.txt", new byte[]{1}), "Unlisted canonical fixture fails");
        failure(() -> CheckpointSourceFixtures.resourceBytes("/missing-checkpoint-resource"), "Missing production resource fails");
        failure(() -> CheckpointSourceFixtures.resourceBytes("relative/path"), "Relative resource path fails");
        failure(() -> CheckpointSourceFixtures.resourceBytes("/../untrusted"), "Traversal-like resource path fails");
        System.out.println("PASS " + checks + " fail-closed source-checkpoint resource integrity checks");
    }
}
