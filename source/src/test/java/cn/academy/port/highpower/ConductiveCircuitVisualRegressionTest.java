package cn.academy.port.highpower;

import java.lang.reflect.Field;
import java.util.ArrayList;
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

/** Executes current compiled NEW-121 receive, eligibility, replay fences, tick and cleanup with boundary substitutes. */
public final class ConductiveCircuitVisualRegressionTest {
    private static final String CLIENT = "cn/academy/port/client/ConductiveCircuitEffects";
    private static final String HARNESS = "cn/academy/port/highpower/generated/CircuitClientHarness";
    private static int checks;
    private static void check(boolean valid, String message) { checks++; if (!valid) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        var h = harness(false);
        for (int phase : new int[]{0, 1}) {
            h.reset(); h.receive(packet(1, 1, phase, 20)); h.tick();
            check(h.size() == 1 && h.age() == 1 && h.quiet() == 1, "live phase advances once");
            StubMinecraft.INSTANCE.paused = true;
            for (int i = 0; i < 300; i++) h.tick();
            check(h.size() == 1 && h.age() == 1 && h.quiet() == 1, "pause preserves lifetime and payload TTL");
            h.receive(packet(1, 2, phase, 20));
            check(h.sequence() == 2 && h.quiet() == 0, "same-token authoritative update accepted after pause");
            StubMinecraft.INSTANCE.paused = false;
            for (int i = 1; i < 20; i++) h.tick();
            check(h.size() == 1, "marker survives until exact remaining boundary");
            h.tick();
            if (phase == 0) {
                check(h.size() == 0, "overall remaining boundary retires resting marker");
                h.receive(packet(1, 3, phase, 20)); check(h.size() == 0, "expired circuit token never resurrects");
            } else {
                check(h.size() == 1 && !h.visible(), "expired energized lease is hidden without inventing resting phase");
                h.receive(packet(1, 2, 1, 20)); check(!h.visible(), "duplicate packet cannot relight an expired pulse");
                h.receive(packet(1, 3, 0, 100)); check(h.visible() && h.phase() == 0, "new authoritative resting packet restores surviving circuit");
            }
        }
        h.reset(); h.receive(packet(1, 1, 0, 200));
        for (int i = 1; i < 200; i++) { h.tick(); h.receive(packet(1, i + 1, 0, 200)); }
        check(h.size() == 1 && h.age() == 199, "keepalive cannot rewind total token age");
        h.tick(); check(h.size() == 0, "hard 200 active-tick total lifetime remains enforced");

        h.reset(); h.receive(packet(1, 1, 0, 200)); h.receive(packet(1, 2, 1, 20));
        check(h.phase() == 1, "only authoritative active packet energizes line");
        h.receive(packet(1, 1, 0, 200)); check(h.phase() == 1, "reordered phase cannot rewind sequence");
        h.receive(packet(2, 1, 0, 200)); h.receive(packet(1, 99, 2, 0));
        check(h.size() == 1 && h.token() == 2, "older terminal cannot remove replacement token");
        h.receive(packet(2, 2, 2, 0)); check(h.size() == 0, "authoritative terminal clears immediately");
        h.receive(packet(2, 3, 1, 20)); check(h.size() == 0, "terminal token never restarts");
        h.receive(packet(3, 1, 0, 200)); h.clear(); h.receive(packet(3, 2, 1, 20));
        check(h.size() == 0 && h.fences() == 1, "clear preserves anti-replay watermark");
        h.receive(packet(4, 1, 0, 200)); h.abort(); h.receive(packet(4, 2, 1, 20));
        check(h.size() == 0, "local abort retires same token");

        for (String transition : List.of("world", "player", "connection", "disconnect")) {
            h.reset(); h.receive(packet(1, 1, 0, 200)); StubMinecraft.INSTANCE.paused = true;
            var mc = StubMinecraft.INSTANCE;
            switch (transition) {
                case "world" -> mc.level = new StubLevel();
                case "player" -> mc.player = new StubEntity(2);
                case "connection" -> mc.connection = new Object();
                case "disconnect" -> { mc.level = null; mc.player = null; mc.connection = null; }
            }
            h.tick(); check(h.size() == 0 && h.fences() == 0, "session replaced even during pause: " + transition);
        }
        for (String invalidation : List.of("dead", "removed", "untracked", "screen", "focus", "category", "inactive", "unlearned", "preset", "epoch", "downgrade")) {
            h.reset(); h.receive(packet(1, 1, 0, 200)); var mc = StubMinecraft.INSTANCE; mc.paused = true;
            switch (invalidation) {
                case "dead" -> mc.player.alive = false;
                case "removed" -> mc.player.removed = true;
                case "untracked" -> mc.level.entities.clear();
                case "screen" -> mc.screen = new Object();
                case "focus" -> mc.focused = false;
                case "category" -> StubAcademyClient.state.category = "meltdowner";
                case "inactive" -> StubAcademyClient.state.activated = false;
                case "unlearned" -> StubAcademyClient.state.learned = false;
                case "preset" -> StubAcademyClient.state.presets.present = false;
                case "epoch" -> StubAcademyClient.epoch = 2;
                case "downgrade" -> StubAcademyClient.state.level = 1;
            }
            h.tick(); check(h.size() == 0 && h.fences() == 1, "context invalidation is immediate during pause: " + invalidation);
        }

        h.reset(); var mc = StubMinecraft.INSTANCE; var observer = new StubEntity(8); mc.level.entities.put(observer.id, observer);
        h.receive(packet(observer, 1, 1, 0, 200)); mc.level.entities.remove(observer.id); mc.paused = true; h.tick();
        check(h.size() == 0, "observer loses tracked actor immediately even while paused");
        for (String invalidation : List.of("clear", "abort", "world", "player", "connection")) {
            h.reset(); mc = StubMinecraft.INSTANCE; mc.defer = true; h.receive(packet(1, 1, 0, 200));
            switch (invalidation) {
                case "clear" -> h.clear();
                case "abort" -> h.abort();
                case "world" -> mc.level = new StubLevel();
                case "player" -> mc.player = new StubEntity(2);
                case "connection" -> mc.connection = new Object();
            }
            mc.flush(); check(h.size() == 0, "queued packet cannot cross invalidation: " + invalidation);
        }
        h.reset(); h.receive(packet(1, 1, 2, 0));
        for (int i = 2; i <= 1025; i++) {
            var remote = new StubEntity(i); StubMinecraft.INSTANCE.level.entities.put(i, remote);
            h.receive(packet(remote, 1, 1, 2, 0));
        }
        check(h.fences() == 1024, "owner watermarks stay strictly bounded without eviction");
        h.receive(packet(1, 50, 0, 200)); check(h.size() == 0, "capacity pressure cannot erase earlier retired token");
        h.receive(packet(2, 1, 0, 200)); check(h.size() == 1, "known owner can still use a newer token at capacity");

        h.reset();
        for (int badRemaining : new int[]{-1, 201}) h.receive(packet(1, 1, 0, badRemaining));
        check(h.size() == 0, "invalid lifetime payload rejected");
        StubEntity owner = StubMinecraft.INSTANCE.player;
        h.receive(new StubVisual(owner.id, owner.uuid, 1, 1, 1, 0, new StubVector(0, 1.05, 0), new StubVector(0, 1.1, 5), 200));
        check(h.size() == 0, "airborne endpoint pair rejected at packet boundary");

        // The original paused-TTL defect must reappear if the production pause decision is removed.
        var broken = harness(true); broken.reset(); broken.receive(packet(1, 1, 1, 20)); StubMinecraft.INSTANCE.paused = true;
        for (int i = 0; i < 20; i++) broken.tick();
        check(!broken.visible(), "pause mutation demonstrates actual compiled tick branch coverage");
        System.out.println("PASS " + checks + " compiled NEW-121 replay, phase, pause, TTL, observer and session checks");
    }

    private static StubVisual packet(long token, long sequence, int phase, int remaining) { return packet(StubMinecraft.INSTANCE.player, token, sequence, phase, remaining); }
    private static StubVisual packet(StubEntity owner, long token, long sequence, int phase, int remaining) {
        return new StubVisual(owner.id, owner.uuid, token, 1, sequence, phase, new StubVector(0, 1.05, 0), new StubVector(0, 1.05, 5), remaining);
    }
    private static Harness harness(boolean disablePause) throws Exception {
        var remap = new HashMap<String, String>();
        remap.put(CLIENT, HARNESS);
        for (String nested : List.of("Marker", "Watermark")) remap.put(CLIENT + "$" + nested, HARNESS + "$" + nested);
        remap.put("net/minecraft/client/Minecraft", Type.getInternalName(StubMinecraft.class));
        remap.put("net/minecraft/client/multiplayer/ClientLevel", Type.getInternalName(StubLevel.class));
        remap.put("net/minecraft/world/entity/Entity", Type.getInternalName(StubEntity.class));
        remap.put("net/minecraft/client/player/LocalPlayer", Type.getInternalName(StubEntity.class));
        remap.put("net/minecraft/client/multiplayer/ClientPacketListener", "java/lang/Object");
        remap.put("net/minecraft/client/gui/screens/Screen", "java/lang/Object");
        remap.put("net/minecraft/world/phys/Vec3", Type.getInternalName(StubVector.class));
        remap.put("cn/academy/port/highpower/ConductiveCircuit$Visual", Type.getInternalName(StubVisual.class));
        remap.put("cn/academy/port/client/AcademyClient", Type.getInternalName(StubAcademyClient.class));
        remap.put("cn/academy/port/core/AbilityProgress", Type.getInternalName(StubProgress.class));
        remap.put("cn/academy/port/core/SkillPresets", Type.getInternalName(StubPresets.class));
        remap.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post", Type.getInternalName(StubPost.class));
        ClassNode client = read(CLIENT);
        Set<String> retained = Set.of("receive", "abortLocal", "clear", "retire", "synchronize", "localEligible", "invalid", "tick", "finite", "point", "lambda$receive$0");
        client.methods.removeIf(method -> !retained.contains(method.name));
        client.fields.removeIf(field -> !Set.of("MAX_CONTEXTS", "MAX_WATERMARKS", "MAX_TICKS", "MARKERS", "WATERMARKS", "world", "localPlayer", "connection", "clearGeneration", "localAbortGeneration").contains(field.name));
        client.innerClasses.removeIf(inner -> !inner.name.equals(CLIENT + "$Marker") && !inner.name.equals(CLIENT + "$Watermark"));
        client.nestMembers.removeIf(name -> !name.equals(CLIENT + "$Marker") && !name.equals(CLIENT + "$Watermark"));
        client.visibleAnnotations = null; client.invisibleAnnotations = null;
        MethodNode init = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        for (String field : List.of("MARKERS", "WATERMARKS")) {
            init.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/util/HashMap"));
            init.instructions.add(new InsnNode(Opcodes.DUP));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false));
            init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, CLIENT, field, "Ljava/util/Map;"));
        }
        init.instructions.add(new InsnNode(Opcodes.RETURN)); init.maxStack = 2; client.methods.add(init);
        int pauseCalls = 0;
        for (var method : client.methods) if (method.name.equals("tick")) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals("isPaused")) { pauseCalls++; if (disablePause) call.name = "neverPaused"; }
        check(pauseCalls == 1, "compiled production tick includes a pause decision");
        Map<String, byte[]> classes = new HashMap<>(); classes.put(HARNESS.replace('/', '.'), remapped(client, remap));
        for (String nested : List.of("Marker", "Watermark")) classes.put((HARNESS + "$" + nested).replace('/', '.'), remapped(read(CLIENT + "$" + nested), remap));
        ClassLoader loader = new ClassLoader(ConductiveCircuitVisualRegressionTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name); if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return new Harness(Class.forName(HARNESS.replace('/', '.'), true, loader));
    }
    private static ClassNode read(String name) throws Exception {
        try (var stream = ConductiveCircuitVisualRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing compiled production class: " + name);
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static byte[] remapped(ClassNode node, Map<String, String> mappings) {
        var writer = new ClassWriter(0); node.accept(new ClassRemapper(writer, new SimpleRemapper(mappings))); return writer.toByteArray();
    }
    private record Harness(Class<?> type) {
        void reset() throws Exception { StubMinecraft.INSTANCE = new StubMinecraft(); StubAcademyClient.state = new StubProgress(); StubAcademyClient.epoch = 1; tick(); }
        void receive(StubVisual packet) throws Exception { type.getMethod("receive", StubVisual.class).invoke(null, packet); }
        void tick() throws Exception { type.getMethod("tick", StubPost.class).invoke(null, new StubPost()); }
        void abort() throws Exception { type.getMethod("abortLocal").invoke(null); }
        void clear() throws Exception { type.getMethod("clear").invoke(null); }
        Object field(Object target, String name) throws Exception { Field field = (target == null ? type : target.getClass()).getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        @SuppressWarnings("unchecked") Map<UUID, Object> markers() throws Exception { return (Map<UUID, Object>) field(null, "MARKERS"); }
        int fences() throws Exception { return ((Map<?, ?>) field(null, "WATERMARKS")).size(); }
        int size() throws Exception { return markers().size(); }
        Object marker() throws Exception { return markers().values().iterator().next(); }
        int age() throws Exception { return (int) field(marker(), "age"); }
        int quiet() throws Exception { return (int) field(marker(), "quietTicks"); }
        StubVisual state() throws Exception { return (StubVisual) field(marker(), "state"); }
        long sequence() throws Exception { return state().sequence(); }
        long token() throws Exception { return state().token(); }
        int phase() throws Exception { return state().phase(); }
        boolean visible() throws Exception { return size() > 0 && quiet() < state().remaining(); }
    }
    public static final class StubMinecraft {
        static StubMinecraft INSTANCE = new StubMinecraft();
        public StubLevel level = new StubLevel(); public StubEntity player = new StubEntity(1); public Object screen;
        Object connection = new Object(); boolean paused, defer, focused = true; final List<Runnable> queued = new ArrayList<>();
        StubMinecraft() { level.entities.put(player.id, player); }
        public static StubMinecraft getInstance() { return INSTANCE; }
        public Object getConnection() { return connection; }
        public void execute(Runnable work) { if (defer) queued.add(work); else work.run(); }
        void flush() { var work = List.copyOf(queued); queued.clear(); work.forEach(Runnable::run); }
        public boolean isPaused() { return paused; }
        public boolean neverPaused() { return false; }
        public boolean isWindowActive() { return focused; }
    }
    public static final class StubLevel { final Map<Integer, StubEntity> entities = new HashMap<>(); public StubEntity getEntity(int id) { return entities.get(id); } }
    public static final class StubEntity {
        final int id; final UUID uuid = UUID.randomUUID(); boolean alive = true, removed;
        StubEntity(int id) { this.id = id; }
        public int getId() { return id; } public UUID getUUID() { return uuid; }
        public boolean isAlive() { return alive; } public boolean isRemoved() { return removed; }
    }
    public static final class StubVector { public final double x, y, z; StubVector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; } }
    public record StubVisual(int entity, UUID owner, long token, long epoch, long sequence, int phase, StubVector start, StubVector end, int remaining) {}
    public static final class StubProgress {
        public String category = "electromaster"; public boolean activated = true, learned = true; public int level = 2; public StubPresets presets = new StubPresets();
        public boolean learned(String skill) { return learned; }
    }
    public static final class StubPresets { boolean present = true; public boolean currentContains(String skill) { return present; } }
    public static final class StubAcademyClient { public static StubProgress state = new StubProgress(); static long epoch = 1; public static boolean singleOwnerEpochMatches(long value) { return value == epoch; } }
    public static final class StubPost {}
}
