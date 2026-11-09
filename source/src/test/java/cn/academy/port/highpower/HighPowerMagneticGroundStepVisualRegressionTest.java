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

/** Actual compiled NEW-132 receiver/tick/abort bytecode with only Minecraft boundaries replaced. */
public final class HighPowerMagneticGroundStepVisualRegressionTest {
    private static final String CLIENT = "cn/academy/port/client/MagneticGroundStepEffects";
    private static final String HARNESS = "cn/academy/port/highpower/generated/MagneticGroundStepClientHarness";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Harness h = harness();
        h.reset(); h.receive(packet(1, 1, 0, 40));
        check(h.size() == 1 && h.arc() == 0 && !h.deflected(), "authoritative glide cannot predict deflection");
        h.receive(packet(1, 2, 0, 39));
        check(h.arc() == 0, "glide keepalive cannot manufacture an arc");
        h.receive(packet(1, 3, 1, 39));
        check(h.arc() == 4 && h.deflected(), "first actual server deflection starts one four-tick arc");
        StubVector capturedStart = h.arcStart(), capturedEnd = h.arcEnd();
        h.tick();
        check(h.arc() == 3, "confirmed arc ages independently of packet phase");
        h.receive(packet(1, 4, 0, 38));
        check(h.arc() == 3 && h.arcStart() == capturedStart && h.arcEnd() == capturedEnd,
                "keepalive neither restarts nor relocates captured deflection arc");
        h.receive(packet(1, 3, 1, 39));
        check(h.arc() == 3 && h.sequence() == 4, "out-of-order event cannot replay arc");
        h.receive(packet(1, 5, 1, 38));
        check(h.arc() == 3 && h.sequence() == 5, "even a newer event sequence cannot retrigger the same token");
        for (int i = 0; i < 3; i++) h.tick();
        check(h.arc() == 0 && h.size() == 1, "arc ends after exactly four active ticks while glide remains");
        h.receive(packet(1, 6, 1, 35));
        check(h.arc() == 0, "used deflection cannot restart after fading");
        h.receive(packet(1, 7, 2, 35));
        check(h.size() == 0, "terminal immediately removes rails and arc");
        h.receive(packet(1, 8, 1, 34));
        check(h.size() == 0, "higher sequence cannot resurrect a terminal token");
        h.receive(packet(2, 1, 1, 40));
        check(h.size() == 1 && h.arc() == 4, "fresh input and token may confirm their own one arc");
        h.receive(packet(1, 99, 2, 0));
        check(h.size() == 1 && h.input() == 2, "old-input terminal cannot remove replacement glide");
        h.receive(packet(StubMinecraft.INSTANCE.player, 2, 999, 1, 2, 1, 39));
        check(h.token() == 2 && h.sequence() == 1, "token cannot change inside an authenticated input");

        h.receive(packet(StubMinecraft.INSTANCE.player, 3, 2, 1, 1, 1, 40));
        check(h.input() == 2 && h.token() == 2, "new input cannot reuse a previously accepted token");
        h.receive(packet(StubMinecraft.INSTANCE.player, 3, 1, 1, 1, 1, 40));
        check(h.input() == 2 && h.token() == 2, "new input cannot replay an older token");

        h.reset(); h.receive(packet(1, 1, 2, 0)); h.receive(packet(1, 99, 1, 40));
        check(h.size() == 0, "terminal arriving before start fences even a newer-sequence first arc");
        h.reset(); h.receive(packet(1, 1, 1, 40)); h.abort();
        h.receive(packet(StubMinecraft.INSTANCE.player, 2, 1, 1, 1, 1, 40));
        check(h.size() == 0, "local retirement retains its token floor for a newer input");
        h.receive(packet(2, 1, 1, 40)); h.clear(); h.receive(packet(3, 1, 0, 40));
        check(h.size() == 1 && h.input() == 3, "explicit clear allows a genuinely fresh input and token");

        h.reset(); h.receive(packet(1, 1, 0, 40));
        for (int i = 0; i < 11; i++) h.tick();
        check(h.size() == 1, "quiet TTL tolerates short authoritative keepalive gaps");
        h.tick(); check(h.size() == 0, "missing keepalive retires marker at twelve active ticks");
        h.receive(packet(1, 99, 0, 28));
        check(h.size() == 0, "expired input remains fenced against delayed keepalive");
        h.reset(); h.receive(packet(1, 1, 0, 1)); h.tick();
        check(h.size() == 0, "authoritative final remaining tick bounds expiry");
        h.reset(); h.receive(packet(1, 1, 1, 0));
        check(h.size() == 0, "zero remaining cannot flash a deflection arc");
        h.reset(); h.receive(packet(1, 1, 0, 40));
        for (int i = 0; i < 39; i++) { h.tick(); h.receive(packet(1, i + 2, 0, 40)); }
        check(h.size() == 1 && h.age() == 39, "remaining refresh cannot reset absolute age");
        h.tick(); h.receive(packet(1, 99, 0, 40));
        check(h.size() == 0, "forty-tick maximum survives continuous refreshes");

        h.reset(); h.receive(packet(1, 1, 1, 40)); h.tick();
        StubMinecraft.INSTANCE.paused = true;
        for (int i = 0; i < 100; i++) h.tick();
        check(h.size() == 1 && h.age() == 1 && h.quiet() == 1 && h.arc() == 3,
                "integrated-server pause freezes absolute age, quiet TTL and arc");
        h.receive(packet(1, 2, 0, 39));
        check(h.quiet() == 0 && h.arc() == 3, "paused authoritative keepalive leaves arc unchanged");
        h.abort(); check(h.size() == 0, "local abort takes effect while paused");
        h.receive(packet(1, 3, 1, 39));
        check(h.size() == 0, "paused late packets cannot undo local cancellation");
        h.reset(); h.abort(1, 1); h.receive(packet(1, 1, 1, 40));
        check(h.size() == 0, "captured-input abort fences first packet before any marker exists");
        h.receive(packet(2, 1, 0, 40)); h.abort(1, 1);
        check(h.size() == 1 && h.input() == 2, "old cancellation cannot remove a newer input");
        h.reset();
        StubEntity remote = new StubEntity(2); StubMinecraft.INSTANCE.level.entities.put(2, remote);
        h.receive(packet(remote, 1, 1, 1, 1, 40));
        h.abort(1, 1);
        check(h.size() == 1 && h.state().owner().equals(remote.uuid), "local abort cannot remove another owner's arc");

        h.reset(); h.abort(1, 1);
        for (int i = 2; i < 1032; i++) {
            StubEntity actor = new StubEntity(i); StubMinecraft.INSTANCE.level.entities.put(i, actor);
            h.receive(packet(actor, 1, 1, 1, 2, 0));
        }
        check(h.terminals().size() == 1024, "watermark cache is bounded without evicting old retirement fences");
        h.receive(packet(1, 1, 1, 40));
        check(h.size() == 0, "remote-owner churn cannot evict local cancellation");
        StubEntity oldest = StubMinecraft.INSTANCE.level.getEntity(2);
        h.receive(packet(oldest, 1, 1, 2, 1, 40));
        check(h.size() == 0, "oldest remote terminal remains fenced at full capacity");
        h.receive(packet(oldest, 2, 1, 1, 0, 40));
        check(h.size() == 1 && h.input() == 2, "known owner can advance without expanding cache");

        h.reset(); h.receive(packet(1, 1, 0, 40)); StubAcademyClient.epoch = 2;
        h.receive(packet(StubMinecraft.INSTANCE.player, 1, 2, 2, 1, 0, 40));
        check(h.size() == 1 && h.epoch() == 2, "new owner epoch may restart input with a fresh token");
        h.receive(packet(StubMinecraft.INSTANCE.player, 999, 1, 999, 2, 0)); h.abort(999, 1);
        check(h.size() == 1 && h.epoch() == 2, "old epoch cannot end or cancel replacement local glide");
        h.reset(); remote = new StubEntity(2); StubMinecraft.INSTANCE.level.entities.put(2, remote);
        h.receive(packet(remote, 5, 3, 1, 0, 40));
        h.receive(packet(remote, 999, 2, 100, 2, 0));
        check(h.size() == 1 && h.epoch() == 3, "remote epoch ordering fences stale terminal");
        StubEntity impostor = new StubEntity(3, remote.uuid); StubMinecraft.INSTANCE.level.entities.put(3, impostor);
        h.receive(packet(impostor, 5, 3, 2, 1, 39));
        check(h.sequence() == 1 && h.arc() == 0, "same UUID cannot change actor id within input");
        StubEntity reused = new StubEntity(2, remote.uuid); StubMinecraft.INSTANCE.level.entities.put(2, reused);
        h.receive(packet(reused, 5, 3, 2, 1, 39));
        check(h.sequence() == 1, "same id and UUID cannot replace actual owner object");
        h.tick(); check(h.size() == 0, "replaced owner reference immediately retires input");

        for (String transition : List.of("world", "player", "connection", "disconnect", "clear")) {
            h.reset(); StubMinecraft mc = StubMinecraft.INSTANCE; mc.defer = true;
            h.receive(packet(1, 1, 1, 40));
            transition(h, mc, transition); mc.flush();
            check(h.size() == 0, "queued packet cannot cross lifecycle boundary: " + transition);
            h.reset(); h.receive(packet(1, 1, 1, 40)); mc = StubMinecraft.INSTANCE; mc.paused = true;
            transition(h, mc, transition); h.tick();
            check(h.size() == 0 && (transition.equals("clear") ? !h.terminals().isEmpty() : h.terminals().isEmpty()),
                    "pause cannot postpone session cleanup; only explicit clear retains fences: " + transition);
            if (transition.equals("clear")) {
                h.receive(packet(1, 99, 1, 39));
                check(h.size() == 0, "packets arriving after clear cannot revive cleared input");
            }
        }
        h.reset(); StubMinecraft mc = StubMinecraft.INSTANCE; mc.defer = true;
        h.receive(packet(1, 1, 1, 40)); h.abort(1, 1); mc.flush();
        check(h.size() == 0, "queued first arc cannot undo captured-input cancellation");
        h.reset(); mc = StubMinecraft.INSTANCE; remote = new StubEntity(2); mc.level.entities.put(2, remote); mc.defer = true;
        h.receive(packet(remote, 1, 1, 1, 1, 40)); mc.level.entities.put(2, new StubEntity(2, remote.uuid)); mc.flush();
        check(h.size() == 0, "queued first packet cannot attach to a replaced remote owner object");
        h.reset(); h.receive(packet(1, 1, 1, 40)); StubMinecraft.INSTANCE.paused = true;
        StubAcademyClient.eligible = false; h.tick();
        check(h.size() == 0, "screen, focus or ability invalidation clears local visuals while paused");
        h.reset(); h.receive(packet(1, 1, 1, 40)); StubMinecraft.INSTANCE.paused = true;
        StubAcademyClient.epoch = 2; h.tick();
        check(h.size() == 0, "epoch replacement clears local visuals while paused");
        h.reset(); h.receive(packet(1, 1, 1, 40)); StubMinecraft.INSTANCE.paused = true;
        StubMinecraft.INSTANCE.player.alive = false; h.tick();
        check(h.size() == 0, "death cleanup does not wait for unpause");
        h.reset(); h.receive(packet(1, 1, 1, 40)); StubMinecraft.INSTANCE.paused = true;
        StubMinecraft.INSTANCE.player.removed = true; h.tick();
        check(h.size() == 0, "removed-owner cleanup does not wait for unpause");

        h.reset(); StubEntity owner = StubMinecraft.INSTANCE.player;
        StubVector zero = StubVector.ZERO, end = new StubVector(.4, 0, 0), deflection = new StubVector(.4, .5, 1);
        for (StubVisual bad : List.of(
                new StubVisual(-1, owner.uuid, 1, 1, 1, 1, 0, zero, end, zero, 40),
                new StubVisual(1, UUID.randomUUID(), 1, 1, 1, 1, 0, zero, end, zero, 40),
                new StubVisual(1, null, 1, 1, 1, 1, 0, zero, end, zero, 40),
                new StubVisual(1, owner.uuid, 0, 1, 1, 1, 0, zero, end, zero, 40),
                new StubVisual(1, owner.uuid, 1, 0, 1, 1, 0, zero, end, zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 0, 1, 0, zero, end, zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 0, 0, zero, end, zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, -1, zero, end, zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 3, zero, end, zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, end, zero, -1),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, end, zero, 41),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, null, end, zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, null, zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, end, null, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, new StubVector(Double.NaN, 0, 0), end, zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, new StubVector(Double.POSITIVE_INFINITY, 0, 0), zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, new StubVector(.811, 0, 0), zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, new StubVector(.4, .026, 0), zero, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, end, deflection, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 2, zero, end, deflection, 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 1, zero, end, new StubVector(4, 0, 0), 40),
                new StubVisual(1, owner.uuid, 1, 1, 1, 1, 1, zero, end, new StubVector(0, Double.NaN, 0), 40))) {
            h.receive(bad); check(h.size() == 0, "invalid identity, bounds or phase geometry is rejected");
        }
        h.receive(null); check(h.size() == 0, "null packet is harmless");
        h.receive(new StubVisual(1, owner.uuid, 1, 1, 1, 1, 0, zero, new StubVector(.8, .025, 0), zero, 40));
        check(h.size() == 1, "maximum server-batched horizontal step and height tolerance remain visible");
        h.reset(); StubMinecraft.INSTANCE.connection = null; h.receive(packet(1, 1, 1, 40));
        check(h.size() == 0, "disconnected client cannot start an arc even with a residual world");
        h.reset();
        for (int i = 2; i < 132; i++) {
            StubEntity actor = new StubEntity(i); StubMinecraft.INSTANCE.level.entities.put(i, actor);
            h.receive(packet(actor, 1, 1, 1, 0, 40));
        }
        check(h.size() == 128, "concurrent visible owners are capped");
        for (double angle : new double[]{0, .4, 1.5, 3.1, 4.2, 5.9}) {
            StubVector travel = new StubVector(Math.cos(angle)*.4, 0, Math.sin(angle)*.4);
            StubVector offset = h.railOffset(zero, travel);
            check(Math.abs(offset.lengthSqr() - .18*.18) < 1e-12 && offset.y == 0,
                    "rails have fixed narrow lateral separation without raising the ground anchor");
            check(Math.abs(offset.x*travel.x + offset.z*travel.z) < 1e-12,
                    "rail separation follows captured server motion, not camera or owner look");
        }
        check(h.railOffset(zero, zero).lengthSqr() == 0, "zero server motion cannot invent travel direction");
        check(h.arcPoint(end, deflection, 0).subtract(end).lengthSqr() == 0
                        && h.arcPoint(end, deflection, 3).subtract(deflection).lengthSqr() < 1e-12,
                "single side arc retains both authoritative event endpoints");
        ClassNode production = read(CLIENT);
        MethodNode line = production.methods.stream().filter(m -> m.name.equals("line")).findFirst().orElseThrow();
        boolean clips = false, particle = false, worldRender = false, whiteTexture = false;
        for (AbstractInsnNode instruction : line.instructions) if (instruction instanceof MethodInsnNode call)
            clips |= call.owner.equals("cn/academy/port/client/PolarityTetherGeometry") && call.name.equals("clip");
        for (MethodNode method : production.methods) for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                particle |= call.name.equals("addParticle") || call.name.equals("addFreshEntity") || call.name.equals("addAlwaysVisibleParticle");
                worldRender |= call.owner.equals("cn/academy/port/client/ClassicRenderTypes") && call.name.equals("world");
            }
            if (instruction instanceof LdcInsnNode constant) whiteTexture |= "textures/port/white.png".equals(constant.cst);
        }
        check(clips && worldRender && whiteTexture, "renderer uses clipped native world ribbons and existing white texture");
        check(!particle && production.methods.stream().noneMatch(m -> m.name.equals("hud")),
                "no spawned particles, entities or opaque screen-overlay path");
        System.out.println("PASS " + checks + " compiled NEW-132 visual lifecycle, single-arc, geometry, pause and replay checks; no rendered acceptance inferred");
    }

    private static void transition(Harness h, StubMinecraft mc, String transition) throws Exception {
        switch (transition) {
            case "world" -> mc.level = new StubLevel();
            case "player" -> mc.player = new StubEntity(9);
            case "connection" -> mc.connection = new Object();
            case "disconnect" -> { mc.level = null; mc.player = null; mc.connection = null; }
            case "clear" -> h.clear();
        }
    }
    private static StubVisual packet(long input, long sequence, int phase, int remaining) {
        return packet(StubMinecraft.INSTANCE.player, input, 1, sequence, phase, remaining);
    }
    private static StubVisual packet(StubEntity owner, long input, long epoch, long sequence, int phase, int remaining) {
        return packet(owner, input, input, epoch, sequence, phase, remaining);
    }
    private static StubVisual packet(StubEntity owner, long input, long token, long epoch, long sequence, int phase, int remaining) {
        return new StubVisual(owner.id, owner.uuid, input, token, epoch, sequence, phase, StubVector.ZERO,
                new StubVector(.4, 0, 0), phase == 1 ? new StubVector(.4, .5, 1) : StubVector.ZERO, remaining);
    }

    private static void check(boolean valid, String message) { checks++; if (!valid) throw new AssertionError(message); }

    private static Harness harness() throws Exception {
        var mappings = new HashMap<String, String>();
        for (String suffix : List.of("", "$Marker", "$Stamp", "$Watermark")) mappings.put(CLIENT + suffix, HARNESS + suffix);
        mappings.put("net/minecraft/client/Minecraft", Type.getInternalName(StubMinecraft.class));
        mappings.put("net/minecraft/client/multiplayer/ClientLevel", Type.getInternalName(StubLevel.class));
        mappings.put("net/minecraft/world/entity/Entity", Type.getInternalName(StubEntity.class));
        mappings.put("net/minecraft/client/player/LocalPlayer", Type.getInternalName(StubEntity.class));
        mappings.put("net/minecraft/client/multiplayer/ClientPacketListener", "java/lang/Object");
        mappings.put("net/minecraft/client/Options", Type.getInternalName(StubOptions.class));
        mappings.put("net/minecraft/client/gui/screens/Screen", "java/lang/Object");
        mappings.put("net/minecraft/world/phys/Vec3", Type.getInternalName(StubVector.class));
        mappings.put("cn/academy/port/highpower/MagneticGroundStep$Visual", Type.getInternalName(StubVisual.class));
        mappings.put("cn/academy/port/highpower/MagneticGroundStep", Type.getInternalName(StubMagneticGroundStep.class));
        mappings.put("cn/academy/port/client/AcademyClient", Type.getInternalName(StubAcademyClient.class));
        mappings.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post", Type.getInternalName(StubPost.class));
        ClassNode client = read(CLIENT);
        Set<String> retained = Set.of("receive", "abortLocal", "clear", "stamp", "compare", "terminal", "synchronize",
                "localEligible", "invalid", "tick", "finite", "validGeometry", "lambda$receive$0", "railOffset", "arcPoint",
                "visualDiagnostic", "retirementDiagnostic", "invalidReason");
        client.methods.removeIf(method -> !retained.contains(method.name));
        client.fields.removeIf(field -> !Set.of("MARKERS", "WATERMARKS", "world", "localPlayer", "connection",
                "retiredLocal", "generation").contains(field.name));
        client.innerClasses.removeIf(inner -> !Set.of(CLIENT + "$Marker", CLIENT + "$Stamp", CLIENT + "$Watermark").contains(inner.name));
        client.nestMembers.removeIf(name -> !Set.of(CLIENT + "$Marker", CLIENT + "$Stamp", CLIENT + "$Watermark").contains(name));
        client.visibleAnnotations = null; client.invisibleAnnotations = null;
        for (var method : client.methods) if (method.name.equals("localEligible")) {
            // UI/ability state is an external boundary; its original method compiles unchanged in production.
            method.instructions.clear(); method.tryCatchBlocks.clear();
            if (method.localVariables != null) method.localVariables.clear();
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(StubAcademyClient.class),
                    "eligible", "()Z", false));
            method.instructions.add(new InsnNode(Opcodes.IRETURN));
            method.maxStack = 1; method.maxLocals = 0;
        }
        MethodNode init = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        for (String name : List.of("MARKERS", "WATERMARKS")) {
            init.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/util/LinkedHashMap"));
            init.instructions.add(new InsnNode(Opcodes.DUP));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false));
            init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, CLIENT, name, "Ljava/util/Map;"));
        }
        init.instructions.add(new InsnNode(Opcodes.RETURN)); init.maxStack = 2; client.methods.add(init);
        Map<String, byte[]> classes = Map.of(HARNESS.replace('/', '.'), remapped(client, mappings),
                (HARNESS + "$Marker").replace('/', '.'), remapped(read(CLIENT + "$Marker"), mappings),
                (HARNESS + "$Stamp").replace('/', '.'), remapped(read(CLIENT + "$Stamp"), mappings),
                (HARNESS + "$Watermark").replace('/', '.'), remapped(read(CLIENT + "$Watermark"), mappings));
        ClassLoader loader = new ClassLoader(HighPowerMagneticGroundStepVisualRegressionTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return new Harness(Class.forName(HARNESS.replace('/', '.'), true, loader));
    }
    private static ClassNode read(String name) throws Exception {
        try (var stream = HighPowerMagneticGroundStepVisualRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing compiled production class: " + name);
            var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static byte[] remapped(ClassNode node, Map<String, String> mappings) {
        var writer = new ClassWriter(0);
        node.accept(new ClassRemapper(writer, new SimpleRemapper(mappings)));
        return writer.toByteArray();
    }
    private record Harness(Class<?> type) {
        void reset() throws Exception {
            StubMinecraft.INSTANCE = new StubMinecraft(); StubAcademyClient.epoch = 1; StubAcademyClient.eligible = true;
            clear(); tick();
        }
        void clear() throws Exception { type.getMethod("clear").invoke(null); }
        void receive(StubVisual packet) throws Exception { type.getMethod("receive", StubVisual.class).invoke(null, packet); }
        void tick() throws Exception { type.getMethod("tick", StubPost.class).invoke(null, new StubPost()); }
        void abort() throws Exception { type.getMethod("abortLocal").invoke(null); }
        void abort(long input, long epoch) throws Exception { type.getMethod("abortLocal", long.class, long.class).invoke(null, input, epoch); }
        Object field(Object target, String name) throws Exception {
            Field field = (target == null ? type : target.getClass()).getDeclaredField(name);
            field.setAccessible(true); return field.get(target);
        }
        @SuppressWarnings("unchecked") Map<UUID, Object> markers() throws Exception { return (Map<UUID, Object>) field(null, "MARKERS"); }
        @SuppressWarnings("unchecked") Map<UUID, Object> terminals() throws Exception { return (Map<UUID, Object>) field(null, "WATERMARKS"); }
        int size() throws Exception { return markers().size(); }
        Object marker() throws Exception { return markers().values().iterator().next(); }
        int age() throws Exception { return (int) field(marker(), "age"); }
        int quiet() throws Exception { return (int) field(marker(), "quiet"); }
        int arc() throws Exception { return (int) field(marker(), "arc"); }
        boolean deflected() throws Exception { return (boolean) field(marker(), "deflected"); }
        StubVector arcStart() throws Exception { return (StubVector) field(marker(), "arcStart"); }
        StubVector arcEnd() throws Exception { return (StubVector) field(marker(), "arcEnd"); }
        StubVisual state() throws Exception { return (StubVisual) field(marker(), "state"); }
        long sequence() throws Exception { return state().sequence(); }
        long input() throws Exception { return state().input(); }
        long token() throws Exception { return state().token(); }
        long epoch() throws Exception { return state().epoch(); }
        StubVector railOffset(StubVector start, StubVector end) throws Exception {
            var method = type.getDeclaredMethod("railOffset", StubVector.class, StubVector.class); method.setAccessible(true);
            return (StubVector) method.invoke(null, start, end);
        }
        StubVector arcPoint(StubVector start, StubVector end, int index) throws Exception {
            var method = type.getDeclaredMethod("arcPoint", StubVector.class, StubVector.class, int.class); method.setAccessible(true);
            return (StubVector) method.invoke(null, start, end, index);
        }
    }

    public static final class StubMinecraft {
        static StubMinecraft INSTANCE = new StubMinecraft();
        public StubLevel level = new StubLevel();
        public StubEntity player = new StubEntity(1);
        public StubEntity camera = player;
        public Object screen;
        public StubOptions options = new StubOptions();
        Object connection = new Object(); boolean paused, defer;
        final List<Runnable> queued = new ArrayList<>();
        StubMinecraft() { level.entities.put(player.id, player); }
        public static StubMinecraft getInstance() { return INSTANCE; }
        public Object getConnection() { return connection; }
        public StubEntity getCameraEntity() { return camera; }
        public void execute(Runnable work) { if (defer) queued.add(work); else work.run(); }
        void flush() { for (Runnable work : List.copyOf(queued)) work.run(); queued.clear(); }
        public boolean isPaused() { return paused; }
    }
    public static final class StubOptions { public boolean hideGui; }
    public static final class StubLevel {
        final Map<Integer, StubEntity> entities = new HashMap<>();
        public StubEntity getEntity(int id) { return entities.get(id); }
    }
    public static final class StubEntity {
        final int id; final UUID uuid; boolean alive = true, removed;
        StubEntity(int id) { this(id, UUID.randomUUID()); }
        StubEntity(int id, UUID uuid) { this.id = id; this.uuid = uuid; }
        public int getId() { return id; }
        public UUID getUUID() { return uuid; }
        public boolean isAlive() { return alive; }
        public boolean isRemoved() { return removed; }
    }
    public static final class StubVector {
        public static final StubVector ZERO = new StubVector(0, 0, 0);
        public final double x, y, z;
        public StubVector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public StubVector add(StubVector other) { return new StubVector(x+other.x, y+other.y, z+other.z); }
        public StubVector subtract(StubVector other) { return new StubVector(x-other.x, y-other.y, z-other.z); }
        public StubVector scale(double value) { return new StubVector(x*value, y*value, z*value); }
        public double lengthSqr() { return x*x+y*y+z*z; }
        public StubVector normalize() { double length = Math.sqrt(lengthSqr()); return length < 1e-4 ? ZERO : scale(1/length); }
    }
    public record StubVisual(int entity, UUID owner, long input, long token, long epoch, long sequence,
                             int phase, StubVector start, StubVector end, StubVector deflection, int remaining) {}
    public static final class StubAcademyClient {
        static long epoch = 1; static boolean eligible = true;
        public static boolean singleOwnerEpochMatches(long candidate) { return candidate > 0 && candidate == epoch; }
        public static boolean eligible() { return eligible; }
    }
    /** Diagnostic output is an external boundary and must never initialize the game backend in this lifecycle harness. */
    public static final class StubMagneticGroundStep {
        public static void diagnostic(long token, String stage, String detail) {}
    }
    public static final class StubPost {}
}
