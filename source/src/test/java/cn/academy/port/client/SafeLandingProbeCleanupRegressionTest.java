package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Actual compiled NEW313 receive/Post -> client cancellation/transport; native boundaries alone are substituted. */
public final class SafeLandingProbeCleanupRegressionTest {
    private static final String EFFECTS = "cn/academy/port/client/SafeLandingProbeEffects", CLIENT = "cn/academy/port/client/AcademyClient";
    private static final String HE = "cn/academy/port/client/generated/SafeLandingCleanupEffects", HC = "cn/academy/port/client/generated/SafeLandingCleanupClient";
    private static int checks;
    private static Harness current;
    private static final List<StubRequest> sent = new ArrayList<>();
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        var h = harness(false); current = h;
        for (boolean released : new boolean[]{false, true}) {
            h.reset(); h.start(1); if (released) h.input().release(h.input().current());
            // Vanilla can open ChatScreen after ability Pre and before Effects Post.
            h.input().tick(h.gates(), false, ignored -> { throw new AssertionError("Pre still owns its preview"); });
            StubMinecraft.INSTANCE.screen = new Object(); h.tick();
            check(sent.size() == 1 && sent.getFirst().value.equals("0:1:19:71"), "actual Post GUI loss aborts before clear after release=" + released);
            check(h.input().current() == null, "input clears after wire dispatch");
            h.tick(); check(sent.size() == 1, "subsequent Post sends no duplicate abort");
        }
        h.reset(); h.start(1);
        for (int tick = 0; tick < SafeLandingProbeState.QUIET_TICKS; tick++) h.tick();
        check(sent.size() == 1 && h.input().current() == null, "actual lost-receipt lease aborts its server owner");
        h.tick(); check(sent.size() == 1, "expired preview cannot re-abort");

        h.reset(); h.start(1); h.receive(1, 2, SafeLandingProbeState.ENDED);
        check(h.input().current() == null && sent.isEmpty(), "actual received server ENDED is no-wire");
        h.tick(); h.loss(1, 71); h.receive(1, 2, SafeLandingProbeState.ENDED);
        check(sent.isEmpty(), "duplicate ENDED and stale cosmetic callbacks remain no-wire");

        h.reset(); h.start(1);
        check(h.input().press(new HighPowerInput.Input(0, 2, 19, 71), h.gates()).dispatch(), "fresh confirmation ready for actual success");
        h.receive(2, 1, 2, SafeLandingProbeState.SUCCESS);
        check(h.input().current() == null && sent.isEmpty(), "actual newer-input same-token SUCCESS closes control without abort");
        for (int tick = 0; tick < SafeLandingProbeMesh.SUCCESS_TICKS; tick++) h.tick();
        h.loss(1, 71); h.loss(2, 71); check(sent.isEmpty(), "success decay and delayed callbacks stay no-wire");

        h.reset(); h.start(1);
        check(h.input().press(new HighPowerInput.Input(0, 2, 19, 71), h.gates()).dispatch(), "fresh confirmation owns newer command");
        h.loss(1, 71);
        check(sent.size() == 1 && sent.getFirst().value.equals("0:2:19:71"), "old preview loss aborts latest newer confirmation");
        h.loss(1, 71); h.loss(2, 71); check(sent.size() == 1, "both delayed identities are idempotent");

        h.reset(); h.start(1); h.loss(1, 72); h.loss(99, 71);
        check(sent.isEmpty() && h.input().current() != null, "wrong epoch and input cannot discard ownership");
        h.receive(1, 2, SafeLandingProbeState.ENDED); h.start(2); h.loss(1, 71);
        check(sent.isEmpty() && h.input().current().input() == 2, "old visual cannot close independent newer token");

        h.reset(); h.start(1); StubPackets.failOnce = true;
        try { h.loss(1, 71); throw new AssertionError("missing transport failure"); }
        catch (java.lang.reflect.InvocationTargetException expected) { check(expected.getCause() instanceof IllegalStateException, "actual callback surfaces transport failure"); }
        check(h.input().current() != null && sent.isEmpty(), "failed wire leaves exact input owned");
        h.loss(1, 71); check(sent.size() == 1 && h.input().current() == null, "same cancellation retries successfully once");

        h.reset(); h.start(1); StubMinecraft.INSTANCE.player.alive = false; h.menuFence().press(0, true); h.deathCancel(); h.discard();
        check(sent.size() == 1 && h.epoch() == 0 && h.input().current() == null, "death abort occurs before server epoch discard");
        check(!h.menuFence().blocks(-100), "actual discard also clears the retained production menu-mouse fence");
        verifyDeathOrder();

        var broken = harness(true); current = broken; broken.reset(); broken.start(1);
        StubMinecraft.INSTANCE.screen = new Object(); broken.tick();
        check(sent.isEmpty() && broken.input().current() == null, "old ended-callback mutation reproduces orphaned server control");
        broken.input().tick(broken.gates(), false, ignored -> { throw new AssertionError("identity was lost by mutation"); });
        check(sent.isEmpty(), "next Pre cannot recover an input cleared by false server-ended callback");
        var missing = harness(false, true); current = missing; missing.reset();
        try { missing.discard(); throw new AssertionError("missing menu-fence closure was not detected"); }
        catch (java.lang.reflect.InvocationTargetException expected) { check(expected.getCause() instanceof NoSuchFieldError && expected.getCause().getMessage().contains("menuMouseFence"), "old missing-field fixture mutation still fails at actual discardBinding"); }
        System.out.println("SafeLandingProbeCleanupRegressionTest: " + checks + " compiled callback-order and mutation checks passed");
    }
    private static void verifyDeathOrder() throws Exception {
        var source = read(CLIENT); boolean found = false;
        for (var method : source.methods) if (method.name.equals("tick")) {
            List<String> calls = new ArrayList<>();
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(CLIENT)) calls.add(call.name);
            int discard = calls.indexOf("discardBinding");
            found = discard > 1 && calls.get(discard - 2).equals("cancelSafeLandingProbeBeforeDiscard")
                    && calls.get(discard - 1).equals("cancelZeroGapArmorBeforeDiscard") && calls.get(discard + 1).equals("clearEffects");
        }
        check(found, "actual death branch cancels NEW313 before original NEW252 cancellation and epoch discard");
    }
    private static Harness harness(boolean oldCallback) throws Exception { return harness(oldCallback, false); }
    private static Harness harness(boolean oldCallback, boolean missingMenuFence) throws Exception {
        Map<String, String> mappings = new HashMap<>(); mappings.put(EFFECTS, HE); mappings.put(EFFECTS + "$Cosmetic", HE + "$Cosmetic"); mappings.put(CLIENT, HC);
        for (String name : List.of("net/minecraft/world/entity/Entity", "net/minecraft/world/entity/player/Player", "net/minecraft/client/player/LocalPlayer")) mappings.put(name, Type.getInternalName(StubPlayer.class));
        mappings.put("net/minecraft/client/Minecraft", Type.getInternalName(StubMinecraft.class));
        mappings.put("net/minecraft/client/multiplayer/ClientLevel", Type.getInternalName(StubLevel.class));
        mappings.put("net/minecraft/client/multiplayer/ClientPacketListener", "java/lang/Object");
        mappings.put("net/minecraft/client/gui/screens/Screen", "java/lang/Object");
        mappings.put("net/minecraft/world/entity/player/Abilities", Type.getInternalName(StubAbilities.class));
        mappings.put("net/minecraft/world/phys/Vec3", Type.getInternalName(StubVector.class));
        mappings.put("net/minecraft/world/phys/AABB", Type.getInternalName(StubBox.class));
        mappings.put("cn/academy/port/highpower/SafeLandingProbe$Visual", Type.getInternalName(StubVisual.class));
        mappings.put("cn/academy/port/core/AbilityProgress", Type.getInternalName(StubProgress.class));
        mappings.put("cn/academy/port/core/SkillPresets", Type.getInternalName(StubPresets.class));
        mappings.put("cn/academy/port/SkillCatalog", Type.getInternalName(StubCatalog.class));
        mappings.put("cn/academy/port/AcademyNetwork$Request", Type.getInternalName(StubRequest.class));
        mappings.put("net/neoforged/neoforge/network/PacketDistributor", Type.getInternalName(StubPackets.class));
        mappings.put("net/minecraft/network/protocol/common/custom/CustomPacketPayload", "java/lang/Object");
        mappings.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post", Type.getInternalName(StubPost.class));
        var effects = read(EFFECTS); var cosmetic = read(EFFECTS + "$Cosmetic"); var client = read(CLIENT);
        effects.methods.removeIf(m -> !Set.of("receive", "frame", "point", "box", "abortLocal", "clear", "synchronize", "localEligible", "validOwner", "previewOwned", "tick", "lambda$receive$0").contains(m.name));
        effects.fields.removeIf(f -> !Set.of("STATE", "success", "refusal", "world", "localPlayer", "connection", "generation").contains(f.name));
        effects.innerClasses.removeIf(c -> !c.name.equals(EFFECTS + "$Cosmetic")); effects.nestMembers.removeIf(n -> !n.equals(EFFECTS + "$Cosmetic"));
        effects.visibleAnnotations = null; effects.invisibleAnnotations = null;
        var ei = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initialize(ei, EFFECTS, "STATE", "cn/academy/port/client/SafeLandingProbeState", "Lcn/academy/port/client/SafeLandingProbeState;"); finishInit(effects, ei);
        for (var method : effects.methods) for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(CLIENT)) {
            if (Set.of("inputSessionEpoch", "sameInputSessionEpoch", "safeLandingProbeControlValid").contains(call.name)) call.owner = Type.getInternalName(StubSession.class);
            if (oldCallback && method.name.equals("tick") && call.name.equals("safeLandingProbeLocalVisualLost")) call.name = "safeLandingProbeEndedInput";
        }
        client.methods.removeIf(m -> !Set.of("safeLandingProbeInputMatches", "safeLandingProbePreviewInput", "safeLandingProbeEndedInput", "safeLandingProbeLocalVisualLost",
                "sendSafeLandingProbeCancellation", "cancelSafeLandingProbeBeforeDiscard", "singleOwnerEpochMatches", "request", "discardBinding").contains(m.name));
        client.fields.removeIf(f -> !Set.of("safeLandingProbeInputs", "serverSingleEpoch", "singleInputEpoch", "state", "binding", "overrideKeys", "menuMouseFence").contains(f.name));
        if (missingMenuFence) client.fields.removeIf(f -> f.name.equals("menuMouseFence"));
        client.innerClasses.clear(); client.nestMembers.clear(); client.visibleAnnotations = null; client.invisibleAnnotations = null;
        var ci = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initialize(ci, CLIENT, "safeLandingProbeInputs", "cn/academy/port/client/SafeLandingProbeInputState", "Lcn/academy/port/client/SafeLandingProbeInputState;");
        if (!missingMenuFence) initialize(ci, CLIENT, "menuMouseFence", "cn/academy/port/client/MenuMouseInputFence", "Lcn/academy/port/client/MenuMouseInputFence;");
        initialize(ci, CLIENT, "state", "cn/academy/port/core/AbilityProgress", "Lcn/academy/port/core/AbilityProgress;"); finishInit(client, ci);
        Map<String, byte[]> classes = Map.of(HE.replace('/', '.'), remap(effects, mappings), (HE + "$Cosmetic").replace('/', '.'), remap(cosmetic, mappings), HC.replace('/', '.'), remap(client, mappings));
        ClassLoader loader = new ClassLoader(SafeLandingProbeCleanupRegressionTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name); if (bytes == null) throw new ClassNotFoundException(name); return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return new Harness(Class.forName(HE.replace('/', '.'), true, loader), Class.forName(HC.replace('/', '.'), true, loader));
    }
    private static void initialize(MethodNode init, String owner, String field, String type, String descriptor) {
        init.instructions.add(new TypeInsnNode(Opcodes.NEW, type)); init.instructions.add(new InsnNode(Opcodes.DUP));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, type, "<init>", "()V", false)); init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, owner, field, descriptor));
    }
    private static void finishInit(ClassNode owner, MethodNode init) { init.instructions.add(new InsnNode(Opcodes.RETURN)); init.maxStack = 2; owner.methods.add(init); }
    private static ClassNode read(String name) throws Exception {
        try (var stream = SafeLandingProbeCleanupRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing production class " + name); var node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static byte[] remap(ClassNode node, Map<String, String> mappings) { var writer = new ClassWriter(0); node.accept(new ClassRemapper(writer, new SimpleRemapper(mappings))); return writer.toByteArray(); }
    private record Harness(Class<?> effects, Class<?> client) {
        void reset() throws Exception { StubMinecraft.INSTANCE = new StubMinecraft(); input().reset(); field("serverSingleEpoch").setLong(null, 71); effects.getMethod("clear").invoke(null); sent.clear(); StubPackets.failOnce = false; }
        Field field(String name) throws Exception { var field = client.getDeclaredField(name); field.setAccessible(true); return field; }
        SafeLandingProbeInputState input() throws Exception { return (SafeLandingProbeInputState) field("safeLandingProbeInputs").get(null); }
        MenuMouseInputFence menuFence() throws Exception { return (MenuMouseInputFence) field("menuMouseFence").get(null); }
        long epoch() throws Exception { return field("serverSingleEpoch").getLong(null); }
        SafeLandingProbeInputState.Gates gates() {
            var mc = StubMinecraft.INSTANCE;
            return new SafeLandingProbeInputState.Gates(new SafeLandingProbeInputState.DispatchContext(mc.level, mc.player, mc.connection, 1, 19, 71, 0, 1, "same", mc.player.pose, 0, 64, 0),
                    0, -100, mc.player.alive, true, true, true, true, true, mc.screen == null, true, true, true);
        }
        void start(long value) throws Exception { check(input().press(new HighPowerInput.Input(0, value, 19, 71), gates()).dispatch(), "captured preview start"); receive(value, 1, SafeLandingProbeState.PREVIEW); check(input().acceptedIdentity() != null, "actual receive acknowledges matching preview"); }
        void receive(long input, long sequence, int phase) throws Exception { receive(input, input, sequence, phase); }
        void receive(long input, long token, long sequence, int phase) throws Exception {
            var mc = StubMinecraft.INSTANCE;
            var packet = new StubVisual(19, mc.player.uuid, input, 71, token, sequence, phase, new StubVector(0, 64, 0), new StubVector(8, 64, 0),
                    new StubBox(7.7, 64, -.3, 8.3, 65.8, .3), phase == SafeLandingProbeState.PREVIEW || phase == SafeLandingProbeState.SUCCESS,
                    "", phase == SafeLandingProbeState.PREVIEW ? 100 : 0);
            effects.getMethod("receive", StubVisual.class).invoke(null, packet);
        }
        void tick() throws Exception { effects.getMethod("tick", StubPost.class).invoke(null, new StubPost()); }
        void loss(long input, long epoch) throws Exception { client.getMethod("safeLandingProbeLocalVisualLost", long.class, long.class).invoke(null, input, epoch); }
        void privateCall(String name) throws Exception { var method = client.getDeclaredMethod(name); method.setAccessible(true); method.invoke(null); }
        void deathCancel() throws Exception { privateCall("cancelSafeLandingProbeBeforeDiscard"); }
        void discard() throws Exception { privateCall("discardBinding"); }
    }
    public static final class StubMinecraft {
        static StubMinecraft INSTANCE = new StubMinecraft(); public StubLevel level = new StubLevel(); public StubPlayer player = new StubPlayer();
        public Object screen; Object connection = new Object();
        StubMinecraft() { level.player = player; }
        public static StubMinecraft getInstance() { return INSTANCE; } public Object getConnection() { return connection; }
        public void execute(Runnable work) { work.run(); } public boolean isPaused() { return false; } public boolean isWindowActive() { return true; } public StubPlayer getCameraEntity() { return player; }
    }
    public static final class StubLevel { StubPlayer player; public StubPlayer getEntity(int id) { return id == 19 ? player : null; } }
    public static final class StubPlayer {
        final UUID uuid = UUID.randomUUID(); final Object pose = new Object(); boolean alive = true; public boolean noPhysics; final StubAbilities abilities = new StubAbilities();
        public int getId() { return 19; } public UUID getUUID() { return uuid; } public boolean isAlive() { return alive; } public boolean isRemoved() { return false; }
        public boolean isSpectator() { return false; } public boolean isPassenger() { return false; } public boolean isSleeping() { return false; } public StubAbilities getAbilities() { return abilities; }
        public boolean onGround() { return true; } public boolean isFallFlying() { return false; } public boolean isSwimming() { return false; } public boolean isInWater() { return false; } public boolean isInLava() { return false; }
    }
    public static final class StubAbilities { public boolean instabuild, invulnerable; }
    public static final class StubProgress {
        public boolean activated = true, overloadFine = true, interfering; public String category = "teleporter"; public int level = 1; public StubPresets presets = new StubPresets();
        public boolean learned(String ignored) { return true; } public boolean consumptionInProgress() { return false; }
    }
    public static final class StubPresets { public boolean currentContains(String ignored) { return true; } }
    public static final class StubCatalog { public static boolean enabled(String category, String skill) { return true; } }
    public static final class StubSession {
        public static long inputSessionEpoch() { return 1; } public static boolean sameInputSessionEpoch(long epoch) { return epoch == 1; }
        public static boolean safeLandingProbeControlValid() { try { return current.input().ownsControl(current.gates()); } catch (Exception e) { throw new AssertionError(e); } }
    }
    public static final class StubVector { public final double x, y, z; StubVector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; } }
    public static final class StubBox { public final double minX, minY, minZ, maxX, maxY, maxZ; StubBox(double a, double b, double c, double d, double e, double f) { minX = a; minY = b; minZ = c; maxX = d; maxY = e; maxZ = f; } }
    public record StubVisual(int entity, UUID owner, long input, long epoch, long token, long sequence, int phase, StubVector source, StubVector destination, StubBox bounds, boolean valid, String reason, int remainingTicks) {}
    public record StubRequest(String action, String value) {}
    public static final class StubPackets {
        static boolean failOnce;
        public static void sendToServer(Object payload, Object... rest) {
            try {
                var request = (StubRequest) payload; check(request.action.equals("hp_safe_landing_probe_abort"), "actual scoped abort action");
                check(current.input().current() != null && current.epoch() == 71, "actual transport runs before input and epoch cleanup");
                if (failOnce) { failOnce = false; throw new IllegalStateException("injected dispatch failure"); } sent.add(request);
            } catch (IllegalStateException expected) { throw expected; } catch (Exception error) { throw new AssertionError(error); }
        }
    }
    public static final class StubPost {}
}
