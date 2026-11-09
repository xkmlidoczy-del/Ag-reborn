package cn.academy.port.highpower;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Executes the real compiled completion body against throwing source and observer witnesses. */
public final class InertialAnchorFinishRegressionTest {
    private static final String CONTROLLER = "cn/academy/port/highpower/InertialAnchor";
    private static final String SELF = "cn/academy/port/highpower/InertialAnchorFinishRegressionTest";
    private static int checks, notifications, cues;
    private static Throwable validationFailure;
    private static boolean current;
    private static Context active;
    private static Method finish;

    public static final class Player {}
    public static final class Identity {
        public Player player;
        public RuntimeException failure;
        public Player owner() { if (failure != null) throw failure; return player; }
    }
    public static final class Context { public Identity identity; public Receipt reducing; }
    public static final class Capture {
        public Context context;
        public Object ownerPosition, facing, contact;
    }
    public static final class Receipt {
        public Capture capture;
        public boolean finished, committed, grounded;
        public Runnable notifications;
    }

    public static void main(String[] args) throws Exception {
        finish = executable();
        nullAndRepeated();
        successfulCompletion();
        failedValidation();
        failedIdentity();
        failedObserver();
        reentrantObserver();
        newerReceiptSurvives();
        System.out.println("InertialAnchorFinishRegressionTest passed " + checks + " checks");
    }

    private static void nullAndRepeated() {
        reset();
        check(invoke(null, null, true) == null, "missing receipt is harmless");
        Receipt r = receipt(); r.finished = true;
        check(invoke(active.identity.player, r, true) == null, "finished receipt is harmless");
        check(notifications == 0 && cues == 0, "finished receipt grants no observer or cue replay");
    }
    private static void successfulCompletion() {
        reset(); Receipt r = receipt();
        check(invoke(active.identity.player, r, true) == null, "verified completion succeeds");
        retired(r);
        check(notifications == 1 && cues == 1, "successful completion notifies then emits one cue");
        check(invoke(active.identity.player, r, true) == null, "completion retry is harmless");
        check(notifications == 1 && cues == 1, "completion retry cannot notify or emit twice");
    }
    private static void failedValidation() {
        for (Throwable failure : new Throwable[] {new IllegalStateException("source callback"), new LinkageError("source linkage")}) {
            reset(); Receipt r = receipt(); validationFailure = failure;
            check(invoke(active.identity.player, r, true) == failure, "original source validation failure propagates");
            retired(r);
            check(notifications == 1 && cues == 0, "paid notifications drain despite source failure, without a success cue");
            check(invoke(active.identity.player, r, true) == null && notifications == 1,
                    "source failure leaves no retry authority or stuck busy receipt");
        }
        reset(); Receipt r = receipt(); current = false;
        check(invoke(active.identity.player, r, true) == null, "ordinary stale source refusal remains harmless");
        retired(r);
        check(notifications == 1 && cues == 0, "stale completed payment retains notification but loses cue");
    }
    private static void failedIdentity() {
        reset(); Receipt r = receipt();
        RuntimeException failure = new IllegalStateException("owner callback"); active.identity.failure = failure;
        check(invoke(active.identity.player, r, true) == failure, "earliest owner validation failure propagates");
        retired(r);
        check(notifications == 1 && cues == 0, "even earliest validation failure retires authority and drains notification");
    }
    private static void failedObserver() {
        reset(); Receipt r = receipt(); RuntimeException failure = new IllegalStateException("observer callback");
        r.notifications = () -> { observe(r); throw failure; };
        check(invoke(active.identity.player, r, true) == failure, "observer failure retains normal exception behavior");
        retired(r);
        check(notifications == 1 && cues == 0, "observer throws only after authority has been retired");
        check(invoke(active.identity.player, r, true) == null && notifications == 1, "throwing observer cannot be replayed");
    }
    private static void reentrantObserver() {
        reset(); Receipt r = receipt();
        r.notifications = () -> {
            observe(r);
            check(invoke(active.identity.player, r, true) == null, "observer cannot reenter the completed receipt");
        };
        check(invoke(active.identity.player, r, true) == null, "reentrant observer is harmless");
        retired(r);
        check(notifications == 1 && cues == 1, "reentrant observer cannot multiply notifications or cues");
    }
    private static void newerReceiptSurvives() {
        reset(); Receipt old = receipt(), replacement = new Receipt(); active.reducing = replacement; current = false;
        old.notifications = () -> { notifications++; check(active.reducing == replacement, "old completion preserves a replacement guard"); };
        check(invoke(active.identity.player, old, false) == null, "old receipt cleanup succeeds");
        check(old.finished && old.notifications == null && active.reducing == replacement,
                "retirement removes only the exact receipt it owns");
        check(notifications == 1 && cues == 0, "old paid receipt drains only its own notification");
    }
    private static void reset() {
        notifications = 0; cues = 0; validationFailure = null; current = true;
        active = new Context(); active.identity = new Identity(); active.identity.player = new Player();
    }
    private static Receipt receipt() {
        Receipt r = new Receipt(); r.capture = new Capture(); r.capture.context = active;
        r.committed = true; active.reducing = r; r.notifications = () -> observe(r); return r;
    }
    private static void observe(Receipt r) {
        notifications++;
        check(r.finished && active.reducing == null && r.notifications == null,
                "observer sees terminal receipt, removed busy guard and consumed callback");
    }
    private static void retired(Receipt r) {
        check(r.finished && active.reducing == null && r.notifications == null,
                "every completion exit releases all receipt authority");
    }
    private static Throwable invoke(Player p, Receipt r, boolean completed) {
        try { finish.invoke(null, p, r, completed); return null; }
        catch (InvocationTargetException failure) { return failure.getCause(); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    // Controlled external witnesses used by the unmodified production method body.
    public static boolean reductionCurrent(Player p, Receipt r) {
        if (validationFailure instanceof RuntimeException failure) throw failure;
        if (validationFailure instanceof Error failure) throw failure;
        return current;
    }
    public static Context owned(Player p) { return active; }
    public static boolean valid(Context c, boolean nativeFrame) { return true; }
    public static void send(Context c, int phase, Object position, Object facing, Object contact, boolean grounded) {
        check(notifications == 1 && c.reducing == null, "success cue follows notification and guard release"); cues++;
    }

    private static Method executable() throws Exception {
        ClassNode controller = new ClassNode();
        try (var stream = InertialAnchorFinishRegressionTest.class.getResourceAsStream("/" + CONTROLLER + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled InertialAnchor");
            new ClassReader(stream).accept(controller, 0);
        }
        MethodNode production = controller.methods.stream().filter(m -> m.name.equals("finishNativeReduction")).findFirst().orElseThrow();
        var remapper = new SimpleRemapper(Map.of(CONTROLLER, SELF,
                CONTROLLER + "$Context", SELF + "$Context", CONTROLLER + "$Capture", SELF + "$Capture",
                CONTROLLER + "$NativeReduction", SELF + "$Receipt",
                "cn/academy/port/core/LegacySingleKeyServerIdentity", SELF + "$Identity",
                "net/minecraft/server/level/ServerPlayer", SELF + "$Player", "net/minecraft/world/phys/Vec3", "java/lang/Object"));
        String generated = SELF + "$ProductionCompletion";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, generated, null, "java/lang/Object", null);
        production.accept(new MethodRemapper(writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                production.name, remapper.mapMethodDesc(production.desc), null, null), remapper));
        writer.visitEnd();
        class Loader extends ClassLoader {
            Loader() { super(InertialAnchorFinishRegressionTest.class.getClassLoader()); }
            Class<?> define() { byte[] bytes = writer.toByteArray(); return defineClass(generated.replace('/', '.'), bytes, 0, bytes.length); }
        }
        return new Loader().define().getMethod(production.name, Player.class, Receipt.class, boolean.class);
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
