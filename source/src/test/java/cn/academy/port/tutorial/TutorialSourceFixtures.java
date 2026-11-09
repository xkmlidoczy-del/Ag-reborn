package cn.academy.port.tutorial;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Pinned, byte-exact canonical sources. This reader never uses a private reference directory. */
public final class TutorialSourceFixtures {
    public static final String ROOT = "/classic-oracles/tutorial-guide/";
    private static final Map<String, String> HASHES = Map.ofEntries(
        Map.entry("academy/TutorialData.java", "39d9a7ffb9b2dfc5531a533b3b5b26831c774c434f473c75fb2b76796d76da45"),
        Map.entry("academy/Conditions.java", "1bad2206e50b7b26cdcd0ed06405b3aab09b495b4adfa44a35e59322ea99b9e1"),
        Map.entry("academy/ModuleTutorial.java", "f901af8a47373811992c22d7804be37ceed8905e0ee0b56594900f19a696b3e8"),
        Map.entry("academy/ACTutorial.java", "4d3d5e3940c6db455ca488ec1533637c9dcf21168097007ddf145af407142655"),
        Map.entry("academy/TutorialRegistry.java", "4fa70cde3c5fc56b68800b87c05819ce958da03c28b44115ba91127776428853"),
        Map.entry("academy/Condition.java", "655ea395b1ec26484080cf7bff8e6cb8bdb6c463d20414154c5b74cde8ad9dd6"),
        Map.entry("academy/ItemTutorial.java", "3fab9ff391a8b68c84edecf80c8b2612f33d393b0e50a7294533aaa39221e5af"),
        Map.entry("academy/TutorialActivatedEvent.java", "139c072e88dd458f72544f0e5d5d6492dfd4d0fb4d2051e235dcb603e833233a"),
        Map.entry("academy/App.java", "00242d3ab2b0e96a1bf9f93b0c59850532faa112a5d45234b279808965224425"),
        Map.entry("academy/AppRegistry.java", "01961301c1812c7690b2419851fd0ce81485fc380abc2a1f0cf9b227f5c2ab56"),
        Map.entry("academy/ModuleTerminal.java", "b8d9b9b3d67f9e695be3e578524a575e472e5ba0ac8fa2e339bc30f38dd51a6c"),
        Map.entry("academy/ItemApp.java", "6098f54d0850d8f1684c93424b7d0cc7d8cf4e9a3ee6a45ae6f20c0447691f42"),
        Map.entry("academy/AppRegistration.java", "a55b43059730aafff525e726679c629cb4e66ace0b05f687affe7504fdc38216"),
        Map.entry("academy/AppSkillTree.java", "7ae29f03624d42a7adc10bd909fb1d3e8d86b9e247fe4d579cf6933c3d4fea58"),
        Map.entry("academy/AppFreqTransmitter.java", "7fbfb8dd9833730bba61a4ecafdf84a9fdb0654b761ca29dca4f97208d1a60a3"),
        Map.entry("academy/AppSettings.java", "6c6e6a4dbcd4bd9f6ad1e0e85490741fca5bfc7665ab1526548c134b6a054f7a"),
        Map.entry("academy/MediaApp.scala", "f8a5260f26e8a1da47d1161cddbd84118c39b62277d87e8c57e819a38db629f9"),
        Map.entry("lambdalib/TickScheduler.java", "1dc6b67f01253e10ed7d212bc897d595fbaae15bd4784f468044d6ebb8fe5f32"),
        Map.entry("lambdalib/RandUtils.java", "e06e74399c3fbb5de54672c1198135da76d16427e2ab75e943e847e1f05623eb"),
        Map.entry("lambdalib/RegistrationManager.java", "606f12506897ffdc5dcfa0fdd675418592f306b31dc14b8ad6d7448377bd66f5"),
        Map.entry("NOTICE-upstream-port.txt", "3bc6b79e44b92cfd5bf574ce23a339708b1034479237150a55b5faab3ecfdaab"),
        Map.entry("LICENSE-GPL-3.0.txt", "3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986"),
        Map.entry("LICENSE-LambdaLib-MIT.txt", "852bcc033a46c62f99fb5ffd43b3241ba2c0c440c6034aa2114505f2c4f03c4a")
    );
    private TutorialSourceFixtures() {}
    public static byte[] bytes(String name) throws Exception {
        String expected = HASHES.get(name);
        if (name.equals("source-manifest.json")) expected = "6d1ccbcd72f8a90a48890279fbab50432f374299dc56408ee151d73f87c46cc7";
        if (expected == null) throw new AssertionError("Unlisted tutorial source fixture " + name);
        try (var in = TutorialSourceFixtures.class.getResourceAsStream(ROOT + name)) {
            if (in == null) throw new AssertionError("Missing tutorial source fixture " + name);
            byte[] bytes = in.readAllBytes();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(expected)) throw new AssertionError("Altered canonical tutorial fixture " + name);
            return bytes;
        }
    }
    public static String source(String name) throws Exception {
        return new String(bytes(name), StandardCharsets.UTF_8);
    }
    public static int verifyAll() throws Exception {
        bytes("source-manifest.json");
        for (String name : HASHES.keySet()) bytes(name);
        return HASHES.size();
    }
}
