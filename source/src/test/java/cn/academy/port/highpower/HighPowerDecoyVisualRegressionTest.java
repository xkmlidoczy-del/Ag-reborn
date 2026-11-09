package cn.academy.port.highpower;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;

/**
 * Executes actual compiled receive/tick/cleanup bytecode, not a copied lifecycle model.
 * Only Minecraft boundary types and render-only static initialization are substituted.
 * This is headless lifecycle coverage, not rendered-client acceptance.
 */
public final class HighPowerDecoyVisualRegressionTest {
    private static final String CLIENT = "cn/academy/port/client/ElectromasterChargeDecoyEffects";
    private static final String HARNESS = "cn/academy/port/highpower/generated/DecoyClientHarness";
    private static int checks;

    public static void main(String[] args) throws Exception {
        var h = harness(false);
        for (int phase : new int[]{0, 1, 3}) {
            h.reset();
            int remaining = phase == 1 ? 20 : 8;
            h.receive(packet(1, 1, phase, remaining));
            h.tick();
            check(h.size() == 1 && h.age() == 1 && h.quiet() == 1, "live phase ages once: " + phase);
            StubMinecraft.INSTANCE.paused = true;
            for (int i = 0; i < 100; i++) h.tick();
            check(h.size() == 1 && h.age() == 1 && h.quiet() == 1, "paused phase retains exact age and lease: " + phase);
            check(h.terminals().isEmpty(), "pause never retires an authoritative token: " + phase);
            StubMinecraft.INSTANCE.paused = false;
            h.receive(packet(1, 2, phase, remaining));
            check(h.sequence() == 2 && h.quiet() == 0, "same-token keepalive accepted after pause: " + phase);
            for (int i = 1; i < remaining; i++) h.tick();
            check(h.size() == 1, "phase remains until exact active-tick boundary: " + phase);
            h.tick();
            check(h.size() == 0 && h.terminals().get(StubMinecraft.INSTANCE.player.uuid) == 1L,
                    "unpaused stale phase still retires at its bounded expiry: " + phase);
            h.receive(packet(1, 3, phase, remaining));
            check(h.size() == 0, "late same-token update cannot resurrect a genuinely expired marker: " + phase);
        }

        h.reset(); h.receive(packet(1, 1, 0, 8));
        StubMinecraft.INSTANCE.paused = true;
        h.receive(packet(1, 2, 1, 20));
        for (int i = 0; i < 40; i++) h.tick();
        check(h.size() == 1 && h.phase() == 1 && h.quiet() == 0, "queued authoritative commit survives pause");
        h.receive(packet(1, 3, 2, 8));
        check(h.size() == 0, "authoritative terminal still clears while paused");
        h.receive(packet(1, 4, 1, 20));
        check(h.size() == 0, "terminal cannot be undone by later stale payload");
        h.receive(packet(2, 1, 1, 20));
        check(h.size() == 1, "fresh token remains admissible after terminal");
        h.abort(1);
        check(h.size() == 0, "local input abort remains immediate during pause");

        for (String transition : List.of("world", "player", "connection", "disconnect")) {
            h.reset(); h.receive(packet(1, 1, 1, 20)); StubMinecraft.INSTANCE.paused = true;
            var mc = StubMinecraft.INSTANCE;
            switch (transition) {
                case "world" -> mc.level = new StubLevel();
                case "player" -> mc.player = new StubEntity(2);
                case "connection" -> mc.connection = new Object();
                case "disconnect" -> { mc.level = null; mc.player = null; mc.connection = null; }
            }
            h.tick();
            check(h.size() == 0 && h.terminals().isEmpty(), "session synchronization precedes pause guard: " + transition);
        }

        h.reset(); h.receive(packet(1, 1, 1, 20));
        h.receive(packet(1, 1, 0, 8));
        check(h.phase() == 1, "duplicate sequence cannot roll committed pulse back to preview");
        h.receive(packet(2, 1, 0, 8)); h.receive(packet(1, 9, 2, 8));
        check(h.size() == 1 && h.token() == 2, "older terminal cannot clear replacement token");
        StubMinecraft.INSTANCE.player.alive = false; h.tick();
        check(h.size() == 0, "dead owner cleanup remains active when unpaused");

        // Mutation witness: remove only the production pause decision. The original defect
        // must return, proving the harness exercises that branch rather than a test-only model.
        var broken = harness(true); broken.reset(); broken.receive(packet(1, 1, 1, 20));
        StubMinecraft.INSTANCE.paused = true;
        for (int i = 0; i < 40; i++) broken.tick();
        check(broken.size() == 0 && !broken.terminals().isEmpty(), "pause-guard mutation reproduces old premature retirement");
        StubMinecraft.INSTANCE.paused = false; broken.receive(packet(1, 2, 1, 20));
        check(broken.size() == 0, "mutation also reproduces same-token rejection after resume");
        System.out.println("PASS " + checks + " compiled decoy client pause, resume, expiry, terminal and session checks");
    }

    private static StubVisual packet(long token, long sequence, int phase, int remaining) {
        var owner = StubMinecraft.INSTANCE.player;
        return new StubVisual(owner.id, owner.uuid, 1, token, 1, sequence, phase,
                new StubVector(0, 2, 0), new StubVector(0, .05, 5), remaining);
    }
    private static void check(boolean valid, String message) { checks++; if (!valid) throw new AssertionError(message); }

    private static Harness harness(boolean disablePause) throws Exception {
        var remap = new HashMap<String, String>();
        remap.put(CLIENT, HARNESS); remap.put(CLIENT + "$Marker", HARNESS + "$Marker");
        remap.put("net/minecraft/client/Minecraft", Type.getInternalName(StubMinecraft.class));
        remap.put("net/minecraft/client/multiplayer/ClientLevel", Type.getInternalName(StubLevel.class));
        remap.put("net/minecraft/world/entity/Entity", Type.getInternalName(StubEntity.class));
        remap.put("net/minecraft/client/player/LocalPlayer", Type.getInternalName(StubEntity.class));
        remap.put("net/minecraft/client/multiplayer/ClientPacketListener", "java/lang/Object");
        remap.put("net/minecraft/world/phys/Vec3", Type.getInternalName(StubVector.class));
        remap.put("cn/academy/port/highpower/ElectromasterChargeDecoy$Visual", Type.getInternalName(StubVisual.class));
        remap.put("cn/academy/port/client/AcademyClient", Type.getInternalName(StubAcademyClient.class));
        remap.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post", Type.getInternalName(StubPost.class));
        ClassNode client = read(CLIENT), marker = read(CLIENT + "$Marker");
        Set<String> retained = Set.of("receive", "abortLocal", "clear", "terminal", "synchronize", "tick", "finite", "lambda$receive$0");
        client.methods.removeIf(method -> !retained.contains(method.name));
        client.fields.removeIf(field -> !Set.of("MAX_CONTEXTS", "MARKERS", "TERMINALS", "world", "localPlayer", "connection", "retiredLocalInput").contains(field.name));
        client.innerClasses.removeIf(inner -> !inner.name.equals(CLIENT + "$Marker"));
        client.nestMembers.removeIf(name -> !name.equals(CLIENT + "$Marker"));
        client.visibleAnnotations = null; client.invisibleAnnotations = null;
        MethodNode init = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        for (String field : List.of("MARKERS", "TERMINALS")) {
            init.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/util/LinkedHashMap"));
            init.instructions.add(new InsnNode(Opcodes.DUP));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false));
            init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, CLIENT, field, "Ljava/util/Map;"));
        }
        init.instructions.add(new InsnNode(Opcodes.RETURN)); init.maxStack = 2; client.methods.add(init);
        int pauseCalls = 0;
        for (var method : client.methods) if (method.name.equals("tick")) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals("isPaused")) {
                pauseCalls++; if (disablePause) call.name = "neverPaused";
            }
        check(pauseCalls == 1, "compiled production tick contains one explicit pause decision");
        Map<String, byte[]> classes = Map.of(HARNESS.replace('/', '.'), remapped(client, remap),
                (HARNESS + "$Marker").replace('/', '.'), remapped(marker, remap));
        ClassLoader loader = new ClassLoader(HighPowerDecoyVisualRegressionTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name); if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return new Harness(Class.forName(HARNESS.replace('/', '.'), true, loader));
    }
    private static ClassNode read(String name) throws Exception {
        try (var stream = HighPowerDecoyVisualRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing compiled production class: " + name);
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static byte[] remapped(ClassNode node, Map<String, String> mappings) {
        // Existing stack-map frames are type-remapped; no Minecraft class is loaded for hierarchy computation.
        var writer = new ClassWriter(0); node.accept(new ClassRemapper(writer, new SimpleRemapper(mappings))); return writer.toByteArray();
    }
    private record Harness(Class<?> type) {
        void reset() throws Exception { StubMinecraft.INSTANCE = new StubMinecraft(); type.getMethod("clear").invoke(null); tick(); }
        void receive(StubVisual packet) throws Exception { type.getMethod("receive", StubVisual.class).invoke(null, packet); }
        void tick() throws Exception { type.getMethod("tick", StubPost.class).invoke(null, new StubPost()); }
        void abort(long input) throws Exception { type.getMethod("abortLocal", long.class).invoke(null, input); }
        Object field(Object target, String name) throws Exception {
            Field f = (target == null ? type : target.getClass()).getDeclaredField(name); f.setAccessible(true); return f.get(target);
        }
        @SuppressWarnings("unchecked") Map<UUID, Object> markers() throws Exception { return (Map<UUID, Object>)field(null, "MARKERS"); }
        @SuppressWarnings("unchecked") Map<UUID, Long> terminals() throws Exception { return (Map<UUID, Long>)field(null, "TERMINALS"); }
        int size() throws Exception { return markers().size(); }
        Object marker() throws Exception { return markers().values().iterator().next(); }
        int age() throws Exception { return (int)field(marker(), "age"); }
        int quiet() throws Exception { return (int)field(marker(), "quietTicks"); }
        StubVisual state() throws Exception { return (StubVisual)field(marker(), "state"); }
        long sequence() throws Exception { return state().sequence(); }
        long token() throws Exception { return state().token(); }
        int phase() throws Exception { return state().phase(); }
    }

    public static final class StubMinecraft {
        static StubMinecraft INSTANCE = new StubMinecraft();
        public StubLevel level = new StubLevel();
        public StubEntity player = new StubEntity(1);
        Object connection = new Object(); boolean paused;
        StubMinecraft() { level.entities.put(player.id, player); }
        public static StubMinecraft getInstance() { return INSTANCE; }
        public Object getConnection() { return connection; }
        public void execute(Runnable work) { work.run(); }
        public boolean isPaused() { return paused; }
        public boolean neverPaused() { return false; }
    }
    public static final class StubLevel {
        final Map<Integer, StubEntity> entities = new HashMap<>();
        public StubEntity getEntity(int id) { return entities.get(id); }
    }
    public static final class StubEntity {
        final int id; final UUID uuid = UUID.randomUUID(); boolean alive = true, removed;
        StubEntity(int id) { this.id = id; }
        public int getId() { return id; }
        public UUID getUUID() { return uuid; }
        public boolean isAlive() { return alive; }
        public boolean isRemoved() { return removed; }
    }
    public static final class StubVector {
        public final double x, y, z;
        StubVector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public double distanceToSqr(StubVector other) { return Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2) + Math.pow(z - other.z, 2); }
    }
    public record StubVisual(int entity, UUID owner, long input, long token, long epoch, long sequence,
                             int phase, StubVector origin, StubVector point, int remaining) {}
    public static final class StubAcademyClient { public static boolean singleOwnerEpochMatches(long epoch) { return epoch == 1; } }
    public static final class StubPost {}
}
