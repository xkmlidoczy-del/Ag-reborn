package cn.academy.port.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

/** Executes compiled production receive/Post callbacks; only Minecraft and input/transport boundaries are replaced. */
public final class DeflectionStrikeCleanupRegressionTest {
    private static final String EFFECTS = "cn/academy/port/client/DeflectionStrikeEffects";
    private static final String GENERATED = "cn/academy/port/client/generated/DeflectionStrikeCleanupEffects";
    private static final UUID OWNER = new UUID(411, 1);
    private static Class<?> effects;
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        effects = load();
        reset(); receive(fire(1, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 3)));
        check(state().active() == null && StubMinecraft.INSTANCE.work.size() == 1, "receive queues native receipt before touching state");
        StubMinecraft.INSTANCE.flush(); check(state().active() != null, "queued matching receipt admitted");
        receive(fire(1, 2, 1, new StubVector(0, 64, 3), new StubVector(0, 64, 8))); StubMinecraft.INSTANCE.flush();
        check(state().active().contacts().size() == 2 && StubClient.pending.contains(1L), "both contacts merge before input receipt retires");
        receive(fire(1, 2, 1, new StubVector(0, 64, 3), new StubVector(0, 64, 8))); StubMinecraft.INSTANCE.flush();
        check(state().active().contacts().size() == 2, "duplicate actual packet cannot spawn additional motes");
        StubMinecraft.INSTANCE.paused = true;
        for (int i = 0; i < 30; i++) tick();
        check(state().active().age() == 0 && StubClient.ended == 0, "actual paused Post freezes visual and input lifetime");
        StubMinecraft.INSTANCE.paused = false;
        for (int i = 0; i < 12; i++) tick();
        check(state().active() == null && StubClient.ended == 1 && StubClient.cancelled == 0, "natural visual expiry is one no-wire terminal");
        receive(fire(1, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 3))); StubMinecraft.INSTANCE.flush();
        check(state().active() == null && StubClient.pending.isEmpty() && StubClient.ended == 1,
                "late receipt after normal expiry cannot replay effect or reopen completed input");

        List<Consumer<StubMinecraft>> replacements = List.of(m -> m.level = new StubLevel(), m -> m.player = new StubPlayer(OWNER),
                m -> m.connection = new Object(), m -> StubClient.session++, m -> StubClient.epoch++, m -> m.player.alive = false,
                m -> m.player.removed = true, m -> m.level.player = new StubPlayer(new UUID(411, 2)), m -> StubClient.pending.clear());
        for (var replacement : replacements) {
            reset(); receive(fire(1, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 3)));
            replacement.accept(StubMinecraft.INSTANCE); StubMinecraft.INSTANCE.flush();
            check(state().active() == null, "queued packet cannot cross world/player/connection/session/identity loss");
        }
        reset(); receive(fire(1, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 3)));
        effects.getMethod("clear").invoke(null); StubMinecraft.INSTANCE.flush();
        check(state().active() == null, "explicit clear/reload generation fences an already queued packet");

        reset(); receive(fire(1, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 3))); StubMinecraft.INSTANCE.flush();
        StubClient.control = false; tick();
        check(StubClient.cancelled == 1 && StubClient.ended == 0 && StubClient.pending.isEmpty() && state().active() == null,
                "actual effects Post sends scoped abort before input identity disappears on GUI/focus loss");
        tick(); check(StubClient.cancelled == 1, "later Post cannot duplicate abort");
        receive(fire(1, 2, 1, new StubVector(0, 64, 3), new StubVector(0, 64, 8))); StubMinecraft.INSTANCE.flush();
        check(state().active() == null && StubClient.pending.isEmpty() && StubClient.cancelled == 1,
                "late confirmed contact after real cancellation cannot replay effect or duplicate abort");

        reset(); receive(fire(1, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 3))); StubMinecraft.INSTANCE.flush();
        StubClient.control = false; StubClient.failAbort = true;
        try { tick(); throw new AssertionError("Expected injected transport failure"); }
        catch (java.lang.reflect.InvocationTargetException expected) { check(expected.getCause() instanceof IllegalStateException, "abort transport failure surfaced"); }
        check(StubClient.pending.contains(1L) && state().active() != null, "failed abort keeps exact identity for retry");
        tick(); check(StubClient.cancelled == 1 && state().active() == null, "next Post safely retries once");

        reset(); receive(ended(1, 1)); StubMinecraft.INSTANCE.flush();
        check(state().active() == null && StubClient.ended == 1 && StubClient.cancelled == 0, "zero-target ENDED immediately clears pending input without visuals or abort");
        reset(); receive(fire(1, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 3))); StubMinecraft.INSTANCE.flush();
        receive(ended(1, 2)); StubMinecraft.INSTANCE.flush();
        check(state().active() == null && StubClient.pending.isEmpty() && StubClient.cancelled == 0, "server interruption is no-wire terminal");

        reset(); receive(fire(1, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 3))); StubMinecraft.INSTANCE.flush();
        StubClient.pending.add(2L); receive(fire(2, 1, 0, new StubVector(0, 64, 0), new StubVector(0, 64, 7))); StubMinecraft.INSTANCE.flush();
        check(state().active().id().input() == 2 && !StubClient.pending.contains(1L) && StubClient.pending.contains(2L), "new cast replaces the one controller and retires only old pending identity");
        receive(ended(1, 2)); StubMinecraft.INSTANCE.flush();
        check(state().active().id().input() == 2, "late old ENDED cannot erase fresh cast");
        StubMinecraft.INSTANCE.level.player = new StubPlayer(OWNER); tick();
        check(state().active() == null && StubClient.cancelled == 1, "same UUID/entity ID replacement still loses exact owner object");
        System.out.println("DeflectionStrikeCleanupRegressionTest: " + checks + " compiled receive/Post lifecycle checks passed");
    }
    private static Class<?> load() throws Exception {
        var mappings = new HashMap<String, String>(); mappings.put(EFFECTS, GENERATED);
        mappings.put("cn/academy/port/client/AcademyClient", Type.getInternalName(StubClient.class));
        mappings.put("net/minecraft/client/Minecraft", Type.getInternalName(StubMinecraft.class));
        mappings.put("net/minecraft/client/multiplayer/ClientLevel", Type.getInternalName(StubLevel.class));
        mappings.put("net/minecraft/client/multiplayer/ClientPacketListener", "java/lang/Object");
        for (var entity : List.of("net/minecraft/world/entity/Entity", "net/minecraft/world/entity/player/Player", "net/minecraft/client/player/LocalPlayer")) mappings.put(entity, Type.getInternalName(StubPlayer.class));
        mappings.put("net/minecraft/world/phys/Vec3", Type.getInternalName(StubVector.class));
        mappings.put("cn/academy/port/highpower/DeflectionStrike$Visual", Type.getInternalName(StubVisual.class));
        mappings.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post", Type.getInternalName(StubPost.class));
        ClassNode node = new ClassNode();
        try (var stream = DeflectionStrikeCleanupRegressionTest.class.getClassLoader().getResourceAsStream(EFFECTS + ".class")) {
            if (stream == null) throw new IllegalStateException("Compile production effects first"); new ClassReader(stream).accept(node, 0);
        }
        Set<String> kept = Set.of("receive", "frame", "point", "abortLocal", "clear", "synchronize", "validOwner", "tick");
        node.methods.removeIf(m -> !kept.contains(m.name) && !m.name.startsWith("lambda$receive$"));
        node.fields.removeIf(f -> !Set.of("STATE", "world", "localPlayer", "visualOwner", "connection", "generation").contains(f.name));
        for (var f : node.fields) f.access &= ~Opcodes.ACC_FINAL;
        node.innerClasses.clear(); node.nestMembers.clear(); node.visibleAnnotations = null; node.invisibleAnnotations = null;
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(new ClassRemapper(writer, new SimpleRemapper(mappings)));
        byte[] bytes = writer.toByteArray();
        var loader = new ClassLoader(DeflectionStrikeCleanupRegressionTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!name.equals(GENERATED.replace('/', '.'))) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return Class.forName(GENERATED.replace('/', '.'), true, loader);
    }
    private static Field field(String name) throws Exception { var field = effects.getDeclaredField(name); field.setAccessible(true); return field; }
    private static DeflectionStrikeState state() throws Exception { return (DeflectionStrikeState)field("STATE").get(null); }
    private static void reset() throws Exception {
        StubMinecraft.INSTANCE = new StubMinecraft(); StubClient.reset(); field("STATE").set(null, new DeflectionStrikeState()); effects.getMethod("clear").invoke(null);
    }
    private static void receive(StubVisual visual) throws Exception { effects.getMethod("receive", StubVisual.class).invoke(null, visual); }
    private static void tick() throws Exception { effects.getMethod("tick", StubPost.class).invoke(null, new StubPost()); }
    private static StubVisual fire(long input, long sequence, int slot, StubVector origin, StubVector end) { return new StubVisual(19, OWNER, 71, input, input, sequence, 0, slot, false, origin, end, 1); }
    private static StubVisual ended(long input, long sequence) { return new StubVisual(19, OWNER, 71, input, input, sequence, 1, 0, false, new StubVector(0, 0, 0), new StubVector(0, 0, 0), 0); }
    public static final class StubClient {
        static long session, epoch; static boolean control, failAbort; static int ended, cancelled; static final Set<Long> pending = new HashSet<>();
        static void reset() { session = 1; epoch = 71; control = true; failAbort = false; ended = cancelled = 0; pending.clear(); pending.add(1L); }
        public static long inputSessionEpoch() { return session; }
        public static boolean sameInputSessionEpoch(long value) { return value == session; }
        public static boolean singleOwnerEpochMatches(long value) { return value == epoch; }
        public static boolean deflectionStrikeInputMatches(long input, long ownerEpoch) { return ownerEpoch == epoch && pending.contains(input); }
        public static boolean deflectionStrikeControlValid() { return control && !pending.isEmpty(); }
        public static void deflectionStrikeEndedInput(long input, long ownerEpoch) { if (ownerEpoch == epoch && pending.remove(input)) ended++; }
        public static void deflectionStrikeCancelInput() {
            check(!pending.isEmpty(), "abort callback receives a live input identity");
            if (failAbort) { failAbort = false; throw new IllegalStateException("injected transport failure"); }
            long input = pending.stream().mapToLong(Long::longValue).max().orElseThrow(); cancelled++;
            try { effects.getMethod("abortLocal", long.class, long.class).invoke(null, input, epoch); }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            pending.clear();
        }
    }
    public static final class StubMinecraft {
        static StubMinecraft INSTANCE; public StubLevel level = new StubLevel(); public StubPlayer player = new StubPlayer(OWNER);
        Object connection = new Object(); boolean paused; final List<Runnable> work = new ArrayList<>();
        StubMinecraft() { level.player = player; }
        public static StubMinecraft getInstance() { return INSTANCE; }
        public Object getConnection() { return connection; }
        public void execute(Runnable task) { work.add(task); }
        public boolean isPaused() { return paused; }
        void flush() { var queued = List.copyOf(work); work.clear(); queued.forEach(Runnable::run); }
    }
    public static final class StubLevel { StubPlayer player; public StubPlayer getEntity(int id) { return id == 19 ? player : null; } }
    public static final class StubPlayer {
        final UUID owner; boolean alive = true, removed; StubPlayer(UUID owner) { this.owner = owner; }
        public UUID getUUID() { return owner; } public boolean isAlive() { return alive; } public boolean isRemoved() { return removed; }
    }
    public static final class StubVector { public final double x, y, z; StubVector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; } }
    public record StubVisual(int owner, UUID ownerUuid, long epoch, long input, long token, long sequence, int phase, int slot,
                             boolean crouch, StubVector origin, StubVector end, int status) {}
    public static final class StubPost {}
}
