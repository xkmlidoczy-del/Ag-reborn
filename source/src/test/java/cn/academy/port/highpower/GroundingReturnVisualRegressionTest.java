package cn.academy.port.highpower;

import cn.academy.port.highpower.HighPowerRecoveryHudModel.Box;
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

/** Actual compiled NEW-122 receiver/tick/abort bytecode with only Minecraft boundaries replaced. */
public final class GroundingReturnVisualRegressionTest {
    private static final String CLIENT = "cn/academy/port/client/GroundingReturnEffects";
    private static final String HARNESS = "cn/academy/port/highpower/generated/GroundingReturnClientHarness";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Harness h = harness();
        h.reset();
        h.receive(packet(1, 1, 0, 60));
        check(h.size() == 1 && h.burst() == 0, "authoritative stance starts without absorption success");
        h.receive(packet(1, 2, 0, 59));
        check(h.burst() == 0, "stance keepalive never starts success feedback");
        h.receive(packet(1, 3, 1, 59));
        check(h.burst() == 4, "positive server absorption starts one short burst");
        h.tick();
        check(h.burst() == 3, "burst ages independently of the latest packet phase");
        h.receive(packet(1, 4, 0, 58));
        check(h.burst() == 3, "stance refresh neither resets nor prolongs a confirmed burst");
        h.receive(packet(1, 3, 1, 59));
        check(h.burst() == 3 && h.sequence() == 4, "out-of-order absorption cannot replay its burst");
        for (int i = 0; i < 3; i++) h.tick();
        check(h.burst() == 0 && h.size() == 1, "four active ticks end burst while stance remains");
        h.receive(packet(1, 5, 1, 55));
        check(h.burst() == 4, "a different confirmed absorption may produce its own burst");
        h.receive(packet(1, 6, 2, 55));
        check(h.size() == 0, "authoritative end immediately removes stance and any burst");
        h.receive(packet(1, 7, 1, 54));
        check(h.size() == 0, "late higher-sequence absorption cannot revive ended input");
        h.receive(packet(2, 1, 0, 60));
        h.receive(packet(1, 50, 2, 50));
        check(h.size() == 1 && h.input() == 2, "old-input end cannot remove a replacement stance");

        h.reset(); h.receive(packet(1, 1, 0, 60));
        for (int i = 0; i < 11; i++) h.tick();
        check(h.size() == 1, "three expected keepalive intervals are tolerated");
        h.tick();
        check(h.size() == 0, "missing keepalive expires at twelve active ticks");
        h.receive(packet(1, 99, 0, 40));
        check(h.size() == 0, "expired session cannot reappear from a delayed keepalive");
        h.reset(); h.receive(packet(1, 1, 0, 1)); h.tick();
        check(h.size() == 0, "last remaining authoritative tick bounds expiry");
        h.reset(); h.receive(packet(1, 1, 1, 0));
        check(h.size() == 0, "zero remaining cannot flash success or leave a marker");
        h.reset(); h.receive(packet(1, 1, 0, 60));
        for (int i = 0; i < 59; i++) { h.tick(); h.receive(packet(1, i + 2, 0, 60)); }
        check(h.size() == 1 && h.age() == 59, "higher remaining packets cannot reset absolute stance age");
        h.tick(); h.receive(packet(1, 100, 0, 60));
        check(h.size() == 0, "sixty tick maximum holds despite a refresh every tick");

        h.reset(); h.receive(packet(1, 1, 1, 60)); h.tick();
        StubMinecraft.INSTANCE.paused = true;
        for (int i = 0; i < 100; i++) h.tick();
        check(h.size() == 1 && h.age() == 1 && h.quiet() == 1 && h.burst() == 3,
                "integrated-server pause freezes age, TTL and burst");
        h.receive(packet(1, 2, 0, 59));
        check(h.quiet() == 0 && h.burst() == 3, "paused authoritative refresh remains admissible");
        h.abort();
        check(h.size() == 0, "local abort is immediate while paused");
        h.receive(packet(1, 3, 0, 59));
        check(h.size() == 0, "paused late packets cannot undo local abort");
        h.reset(); h.abort(1, 1); h.receive(packet(1, 1, 0, 60));
        check(h.size() == 0, "captured-input abort fences delayed first server packet");
        h.receive(packet(2, 1, 0, 60)); h.abort(1, 1);
        check(h.size() == 1 && h.input() == 2, "old captured abort leaves newer input visible");
        h.reset(); h.abort(1, 1);
        for (int i = 2; i < 1032; i++) {
            StubEntity remote = new StubEntity(i);
            StubMinecraft.INSTANCE.level.entities.put(i, remote);
            h.receive(packet(remote, 1, 1, 1, 2, 0));
        }
        check(h.terminals().size() == 1024, "session watermark cache is bounded without evicting older fences");
        h.receive(packet(1, 1, 1, 60));
        check(h.size() == 0, "remote cache churn cannot evict local input retirement");
        StubEntity firstRemote = StubMinecraft.INSTANCE.level.getEntity(2);
        h.receive(packet(firstRemote, 1, 1, 2, 0, 60));
        check(h.size() == 0, "oldest remote terminal remains fenced at full cache capacity");
        h.receive(packet(firstRemote, 2, 1, 1, 0, 60));
        check(h.size() == 1 && h.input() == 2, "known owner can advance its session without extra cache space");

        h.reset(); h.receive(packet(1, 1, 0, 60));
        StubAcademyClient.epoch = 2;
        h.receive(packet(StubMinecraft.INSTANCE.player, 1, 2, 1, 0, 60));
        check(h.size() == 1 && h.epoch() == 2, "new owner incarnation may restart its nonce sequence");
        h.receive(packet(StubMinecraft.INSTANCE.player, 999, 1, 999, 2, 0));
        check(h.size() == 1 && h.epoch() == 2, "older epoch cannot end current local stance");
        h.abort(999, 1);
        check(h.size() == 1, "old-epoch local callback cannot cancel current stance");
        h.reset();
        StubEntity remote = new StubEntity(2);
        StubMinecraft.INSTANCE.level.entities.put(2, remote);
        h.receive(packet(remote, 5, 3, 1, 0, 60));
        h.receive(packet(remote, 999, 2, 100, 2, 0));
        check(h.size() == 1 && h.epoch() == 3, "remote epoch ordering also fences older terminal packets");
        StubEntity impostor = new StubEntity(3, remote.uuid);
        StubMinecraft.INSTANCE.level.entities.put(3, impostor);
        h.receive(packet(impostor, 5, 3, 2, 1, 59));
        check(h.sequence() == 1 && h.burst() == 0, "same UUID and nonce cannot change actor id mid-session");
        StubEntity reused = new StubEntity(2, remote.uuid);
        StubMinecraft.INSTANCE.level.entities.put(2, reused);
        h.receive(packet(reused, 5, 3, 2, 1, 59));
        check(h.sequence() == 1, "same actor id cannot replace its actual entity reference mid-session");
        h.tick();
        check(h.size() == 0, "removed or replaced actor retires its session");

        for (String transition : List.of("world", "player", "connection", "disconnect", "clear")) {
            h.reset();
            StubMinecraft mc = StubMinecraft.INSTANCE;
            mc.defer = true;
            h.receive(packet(1, 1, 0, 60));
            switch (transition) {
                case "world" -> mc.level = new StubLevel();
                case "player" -> mc.player = new StubEntity(9);
                case "connection" -> mc.connection = new Object();
                case "disconnect" -> { mc.level = null; mc.player = null; mc.connection = null; }
                case "clear" -> h.clear();
            }
            mc.flush();
            check(h.size() == 0, "queued packet cannot cross session boundary: " + transition);
            h.reset(); h.receive(packet(1, 1, 0, 60));
            mc = StubMinecraft.INSTANCE; mc.paused = true;
            switch (transition) {
                case "world" -> mc.level = new StubLevel();
                case "player" -> mc.player = new StubEntity(9);
                case "connection" -> mc.connection = new Object();
                case "disconnect" -> { mc.level = null; mc.player = null; mc.connection = null; }
                case "clear" -> h.clear();
            }
            h.tick();
            check(h.size() == 0 && (transition.equals("clear") ? !h.terminals().isEmpty() : h.terminals().isEmpty()),
                    "world/relog resets fences; same-session clear retains them while paused: " + transition);
            if (transition.equals("clear")) {
                h.receive(packet(1, 2, 1, 59));
                check(h.size() == 0, "a packet arriving after explicit clear cannot revive the cleared session");
            }
        }
        h.reset(); h.receive(packet(1, 1, 0, 60));
        StubMinecraft.INSTANCE.paused = true; StubAcademyClient.eligible = false; h.tick();
        check(h.size() == 0, "screen, focus and ability invalidation still clear a paused local marker");
        h.reset(); h.receive(packet(1, 1, 0, 60));
        StubMinecraft.INSTANCE.paused = true; StubAcademyClient.epoch = 2; h.tick();
        check(h.size() == 0, "owner epoch replacement retires paused local marker");
        h.reset(); h.receive(packet(1, 1, 0, 60));
        StubMinecraft.INSTANCE.paused = true; StubMinecraft.INSTANCE.player.alive = false; h.tick();
        check(h.size() == 0, "death cleanup never waits for unpause");

        h.reset();
        StubEntity owner = StubMinecraft.INSTANCE.player;
        StubVector point = new StubVector(0, 0, 0);
        for (StubVisual bad : List.of(
                new StubVisual(-1, owner.uuid, 1, 1, 1, 0, point, 60),
                new StubVisual(1, UUID.randomUUID(), 1, 1, 1, 0, point, 60),
                new StubVisual(1, owner.uuid, 0, 1, 1, 0, point, 60),
                new StubVisual(1, owner.uuid, 1, 0, 1, 0, point, 60),
                new StubVisual(1, owner.uuid, 1, 1, 0, 0, point, 60),
                new StubVisual(1, owner.uuid, 1, 1, 1, -1, point, 60),
                new StubVisual(1, owner.uuid, 1, 1, 1, 3, point, 60),
                new StubVisual(1, owner.uuid, 1, 1, 1, 0, point, -1),
                new StubVisual(1, owner.uuid, 1, 1, 1, 0, point, 61),
                new StubVisual(1, owner.uuid, 1, 1, 1, 0, new StubVector(Double.NaN, 0, 0), 60),
                new StubVisual(1, null, 1, 1, 1, 0, point, 60),
                new StubVisual(1, owner.uuid, 1, 1, 1, 0, null, 60))) {
            h.receive(bad);
            check(h.size() == 0, "invalid visual identity or geometry is rejected");
        }
        h.receive(null);
        check(h.size() == 0, "null payload is harmless");
        h.reset();
        for (int i = 2; i < 132; i++) {
            StubEntity actor = new StubEntity(i); StubMinecraft.INSTANCE.level.entities.put(i, actor);
            h.receive(packet(actor, 1, 1, 1, 0, 60));
        }
        check(h.size() == 128, "concurrent visible owners are capped");
        h.reset();
        check(h.cue() == null, "HUD never predicts a stance before authenticated server start");
        StubEntity remoteCue = new StubEntity(2); StubMinecraft.INSTANCE.level.entities.put(2, remoteCue);
        h.receive(packet(remoteCue, 1, 1, 1, 0, 60));
        check(h.cue() == null, "remote stance never creates a local HUD cue");
        h.receive(packet(1, 1, 0, 60));
        check(h.cue()[0] == 60 && h.cue()[1] == 6, "local cue starts at bounded stance and precision counts");
        for (int i = 0; i < 6; i++) h.tick();
        check(h.cue()[0] == 54 && h.cue()[1] == 0, "precision window disappears after exactly six active ticks");
        StubMinecraft.INSTANCE.paused = true; h.tick();
        check(h.cue()[0] == 54, "paused cue does not invent authoritative elapsed time");
        StubMinecraft.INSTANCE.options.hideGui = true;
        check(h.cue() == null, "F1 hides the cue without changing server mechanics");
        StubMinecraft.INSTANCE.options.hideGui = false;
        StubMinecraft.INSTANCE.screen = new Object();
        check(h.cue() == null, "open screen hides cue before the next client tick");
        StubMinecraft.INSTANCE.screen = null;
        StubMinecraft.INSTANCE.camera = remoteCue;
        check(h.cue() == null, "a different camera owner cannot show local stance cue");
        StubMinecraft.INSTANCE.camera = StubMinecraft.INSTANCE.player;
        StubAcademyClient.eligible = false;
        check(h.cue() == null, "focus or ability ineligibility hides cue");
        StubAcademyClient.eligible = true; StubMinecraft.INSTANCE.paused = false;
        for (int i = 0; i < 6; i++) h.tick();
        check(h.cue() == null, "stale authenticated marker cannot leave a HUD cue");
        h.reset(); h.receive(packet(1, 1, 0, 60)); StubAcademyClient.epoch = 2;
        check(h.cue() == null, "cue rejects replaced owner epoch immediately");
        h.reset(); h.receive(packet(1, 1, 0, 60)); StubMinecraft.INSTANCE.connection = new Object();
        check(h.cue() == null, "cue synchronizes and clears relog state without waiting for tick");
        h.reset(); h.receive(packet(1, 1, 0, 1));
        check(h.cue()[0] == 1 && h.cue()[1] == 0, "last stance tick never revives precision");
        h.abort(); check(h.cue() == null, "local cancellation removes HUD cue immediately");
        check(h.seconds(-10).equals("0.0") && h.seconds(1).equals("0.1") && h.seconds(60).equals("3.0")
                && h.seconds(600).equals("3.0"), "read-only time label remains within zero to three seconds");
        for (int width : new int[]{320, 427, 640, 960}) for (int height : new int[]{180, 240, 360, 540}) {
            Box cp = new Box(width - 205, 12, 194, 40);
            Box keys = new Box(width - 187, height / 2 + 5, 185, 88);
            Box notification = new Box(0, 15, 130, 43);
            Box recovery = new Box(width - 162, 54, 150, 56);
            Box box = h.cueBox(width, height, cp, keys, recovery, notification);
            check(box != null && box.width() == 110 && box.height() == 24 && box.within(width, height),
                    "small cue has bounded geometry at common GUI scales");
            check(!box.intersects(cp) && !box.intersects(keys) && !box.intersects(recovery) && !box.intersects(notification)
                    && !box.intersects(new Box(Math.max(0, width / 2 - 110), height - 72, 220, 72)),
                    "cue avoids CP, HP, keys, notification and actionbar/status regions");
        }
        Box empty = new Box(0, 0, 0, 0);
        check(h.cueBox(200, 120, empty, empty, null, empty) == null, "tiny GUI suppresses cue rather than overflowing");
        check(h.cueBox(320, 180, new Box(0, 0, 320, 180), empty, null, empty) == null,
                "custom HUD collision suppresses cue when every bounded candidate is occupied");
        // Actual compiled visual geometry keeps every ribbon outside the opaque body, in crouch and standing poses.
        check(h.geometryNumber("groundAlpha", false) == 170 && h.geometryNumber("groundAlpha", true) == 205,
                "floor spokes have explicit readable opacity without changing precision timing");
        check(h.geometryNumber("spokeHalfWidth", false) == .020 && h.geometryNumber("spokeHalfWidth", true) == .018,
                "floor ribbons have bounded readable widths");
        check(h.geometryNumber("legHalfWidth", false) == .014 && h.geometryNumber("legHalfWidth", true) == .018,
                "external leg trails and pulses retain narrow bounded widths");
        for (double degrees : new double[]{-180,-135,-90,-45,0,45,90,135,180})
            for (double height : new double[]{.69,.828}) for (int leg : new int[]{-1,1}) for (int i=0;i<=80;i++) {
                double yaw=Math.toRadians(degrees), down=i/80.0;
                StubVector flankPoint=h.legPoint(new StubVector(0,0,0),yaw,height,down,leg);
                double lateral=flankPoint.x*Math.cos(yaw)+flankPoint.z*Math.sin(yaw);
                check(leg*lateral-h.geometryNumber("legHalfWidth",true)>.30,
                        "full ribbon remains outside opaque body at every tested body-yaw/downflow sample");
                check(Math.abs(flankPoint.y-height*(1-down))<1e-12 && flankPoint.y>=0 && flankPoint.y<=height,
                        "standing/crouched flow descends within captured leg height");
                check(Math.abs(-flankPoint.x*Math.sin(yaw)+flankPoint.z*Math.cos(yaw))<1e-12,
                        "flank follows the body-yaw axis without forward/backward drift");
            }
        ClassNode geometry=read(CLIENT);
        MethodNode render=geometry.methods.stream().filter(m->m.name.equals("render")).findFirst().orElseThrow();
        boolean body=false,oldBody=false;
        for(AbstractInsnNode instruction:render.instructions)if(instruction instanceof FieldInsnNode field){
            body|=field.name.equals("yBodyRot");oldBody|=field.name.equals("yBodyRotO");
        }
        check(body&&oldBody,"renderer interpolates actual body yaw rather than putting paths inside turning legs");
        MethodNode line=geometry.methods.stream().filter(m->m.name.equals("line")).findFirst().orElseThrow();
        boolean clips=false;
        for(AbstractInsnNode instruction:line.instructions)if(instruction instanceof MethodInsnNode call)
            clips|=call.owner.equals("cn/academy/port/client/PolarityTetherGeometry")&&call.name.equals("clip");
        check(clips,"visibility repair retains near-camera clipping rather than bypassing depth");
        System.out.println("PASS " + checks + " compiled NEW-122 visual identity, burst, TTL, pause, abort, session and bounded HUD checks; no rendered acceptance inferred");
    }

    private static StubVisual packet(long input, long sequence, int phase, int remaining) {
        return packet(StubMinecraft.INSTANCE.player, input, 1, sequence, phase, remaining);
    }
    private static StubVisual packet(StubEntity owner, long input, long epoch, long sequence, int phase, int remaining) {
        return new StubVisual(owner.id, owner.uuid, input, epoch, sequence, phase, new StubVector(0, 0, 0), remaining);
    }
    private static void check(boolean valid, String message) { checks++; if (!valid) throw new AssertionError(message); }

    private static Harness harness() throws Exception {
        var mappings = new HashMap<String, String>();
        for (String suffix : List.of("", "$Marker", "$Stamp", "$Watermark", "$Cue")) mappings.put(CLIENT + suffix, HARNESS + suffix);
        mappings.put("net/minecraft/client/Minecraft", Type.getInternalName(StubMinecraft.class));
        mappings.put("net/minecraft/client/multiplayer/ClientLevel", Type.getInternalName(StubLevel.class));
        mappings.put("net/minecraft/world/entity/Entity", Type.getInternalName(StubEntity.class));
        mappings.put("net/minecraft/client/player/LocalPlayer", Type.getInternalName(StubEntity.class));
        mappings.put("net/minecraft/client/multiplayer/ClientPacketListener", "java/lang/Object");
        mappings.put("net/minecraft/client/Options", Type.getInternalName(StubOptions.class));
        mappings.put("net/minecraft/client/gui/screens/Screen", "java/lang/Object");
        mappings.put("net/minecraft/world/phys/Vec3", Type.getInternalName(StubVector.class));
        mappings.put("cn/academy/port/highpower/GroundingReturn$Visual", Type.getInternalName(StubVisual.class));
        mappings.put("cn/academy/port/client/AcademyClient", Type.getInternalName(StubAcademyClient.class));
        mappings.put("net/neoforged/neoforge/client/event/ClientTickEvent$Post", Type.getInternalName(StubPost.class));
        ClassNode client = read(CLIENT);
        Set<String> retained = Set.of("receive", "abortLocal", "clear", "stamp", "compare", "terminal", "synchronize",
                "localEligible", "localCue", "cueBox", "seconds", "tick", "finite", "lambda$receive$0",
                "groundAlpha", "spokeHalfWidth", "legHalfWidth", "flankOffset", "legPoint");
        client.methods.removeIf(method -> !retained.contains(method.name));
        client.fields.removeIf(field -> !Set.of("MARKERS", "WATERMARKS", "world", "localPlayer", "connection",
                "retiredLocal", "generation").contains(field.name));
        client.innerClasses.removeIf(inner -> !Set.of(CLIENT + "$Marker", CLIENT + "$Stamp", CLIENT + "$Watermark", CLIENT + "$Cue").contains(inner.name));
        client.nestMembers.removeIf(name -> !Set.of(CLIENT + "$Marker", CLIENT + "$Stamp", CLIENT + "$Watermark", CLIENT + "$Cue").contains(name));
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
                (HARNESS + "$Watermark").replace('/', '.'), remapped(read(CLIENT + "$Watermark"), mappings),
                (HARNESS + "$Cue").replace('/', '.'), remapped(read(CLIENT + "$Cue"), mappings));
        ClassLoader loader = new ClassLoader(GroundingReturnVisualRegressionTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return new Harness(Class.forName(HARNESS.replace('/', '.'), true, loader));
    }
    private static ClassNode read(String name) throws Exception {
        try (var stream = GroundingReturnVisualRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
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
        int burst() throws Exception { return (int) field(marker(), "burst"); }
        StubVisual state() throws Exception { return (StubVisual) field(marker(), "state"); }
        long sequence() throws Exception { return state().sequence(); }
        long input() throws Exception { return state().input(); }
        long epoch() throws Exception { return state().epoch(); }
        int[] cue() throws Exception {
            var method = type.getDeclaredMethod("localCue", StubMinecraft.class); method.setAccessible(true);
            Object cue = method.invoke(null, StubMinecraft.INSTANCE);
            return cue == null ? null : new int[]{(int)field(cue, "remaining"), (int)field(cue, "precision")};
        }
        String seconds(int ticks) throws Exception {
            var method = type.getDeclaredMethod("seconds", int.class); method.setAccessible(true);
            return (String)method.invoke(null, ticks);
        }
        double geometryNumber(String name,boolean argument)throws Exception {
            var method=type.getDeclaredMethod(name,boolean.class);method.setAccessible(true);
            return ((Number)method.invoke(null,argument)).doubleValue();
        }
        StubVector legPoint(StubVector ground,double yaw,double height,double down,int leg)throws Exception {
            var method=type.getDeclaredMethod("legPoint",StubVector.class,double.class,double.class,double.class,int.class);
            method.setAccessible(true);return (StubVector)method.invoke(null,ground,yaw,height,down,leg);
        }
        Box cueBox(int width, int height, Box cp, Box keys, Box recovery, Box notification) throws Exception {
            var method = type.getDeclaredMethod("cueBox", int.class, int.class, Box.class, Box.class, Box.class, Box.class);
            method.setAccessible(true); return (Box)method.invoke(null, width, height, cp, keys, recovery, notification);
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
        public final double x, y, z;
        public StubVector(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        public StubVector add(StubVector other){return new StubVector(x+other.x,y+other.y,z+other.z);}
        public StubVector add(double a,double b,double c){return new StubVector(x+a,y+b,z+c);}
    }
    public record StubVisual(int entity, UUID owner, long input, long epoch, long sequence,
                             int phase, StubVector point, int remaining) {}
    public static final class StubAcademyClient {
        static long epoch = 1; static boolean eligible = true;
        public static boolean singleOwnerEpochMatches(long candidate) { return candidate > 0 && candidate == epoch; }
        public static boolean eligible() { return eligible; }
    }
    public static final class StubPost {}
}
