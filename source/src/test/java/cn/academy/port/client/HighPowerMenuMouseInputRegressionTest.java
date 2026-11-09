package cn.academy.port.client;

import cn.academy.port.core.TargetedContextTermination;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Executes the compiled native mouse/screen hooks with the unchanged production binding/coordinator. */
public final class HighPowerMenuMouseInputRegressionTest {
    private static final String CLIENT = "cn/academy/port/client/AcademyClient";
    private static final String GENERATED = "cn/academy/port/client/generated/MenuMouseDispatchClient";
    private static Class<?> client;
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static Object call(String name, Object... args) {
        try {
            Method method = java.util.Arrays.stream(client.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            method.setAccessible(true); return method.invoke(null, args);
        } catch (ReflectiveOperationException e) { throw new AssertionError(name, e); }
    }
    private static final class Rig implements ClassicClientInputBinding.Host {
        final List<String> wire = new ArrayList<>();
        final String[] skills = {"braking_touch", "", "", "circulation_listening"};
        final int[] keys = {-100, -99, 19, 33};
        ClassicClientInputBinding binding;
        final boolean fenceEnabled;
        long counter;
        Rig(boolean fenceEnabled) throws Exception {
            this.fenceEnabled = fenceEnabled;
            NativeInputs.down.clear(); StubMinecraft.INSTANCE.screen = null;
            var field = client.getDeclaredField("menuMouseFence"); field.setAccessible(true);
            field.set(null, new MenuMouseInputFence());
            binding = new ClassicClientInputBinding(this); bindActualHost(); binding.runtime().updateDefaultGroup(); tick();
        }
        public boolean available() { return true; }
        public boolean inGame() { return true; }
        public boolean categoryActivated() { return true; }
        public boolean canUseAbility() { return true; }
        public boolean terminalOpen() { return StubMinecraft.INSTANCE.screen != null; }
        public boolean physicalDown(int key) { return NativeInputs.physicalDown(key); }
        public boolean registeredMapping(String skill) { return !skill.isEmpty(); }
        public boolean eligible(ClassicClientInputBinding.Node node) {
            return !fenceEnabled || !(boolean) call("menuMouseInputBlocked", node);
        }
        public String mappedSkill(int slot) { return skills[slot]; }
        public int mappedKey(int slot) { return keys[slot]; }
        public int slotCount() { return 4; }
        public void rawActivation(boolean desired) {}
        public void replaceOverrides(int[] keys) {}
        public long down(ClassicClientInputBinding.Node node) { wire.add("start:" + node.slot + ":" + node.skill); return ++counter; }
        public void up(ClassicClientInputBinding.Node node, long input) { wire.add("release:" + node.skill); }
        public void abort(ClassicClientInputBinding.Node node, long input) { wire.add("abort:" + node.skill); }
        public boolean cancelPending(ClassicClientInputBinding.Node node, long input) { return false; }
        public void direction(ClassicClientInputBinding.ContextIdentity context, int direction, ClassicClientInputBinding.Edge edge, boolean silent) {}
        public void localContextEnd(ClassicClientInputBinding.ContextIdentity context) {}
        public void contextAbort(TargetedContextTermination.Request request) {}
        public void modernCleanup(Collection<ClassicClientInputBinding.Node> defaults) {}
        void tick() { call("pollMenuMouseFence"); binding.tickStart(); }
        void bindActualHost() throws Exception {
            var field = client.getDeclaredField("binding"); field.setAccessible(true); field.set(null, binding);
        }
        void open() { StubMinecraft.INSTANCE.screen = new Object(); }
        void close() { call("menuScreenClosing", new StubClosing()); StubMinecraft.INSTANCE.screen = null; }
        void mouse(int button, boolean pressed) {
            if (pressed) NativeInputs.down.add(ClassicPhysicalKeys.mouse(button));
            else NativeInputs.down.remove(ClassicPhysicalKeys.mouse(button));
            call("menuMouseButton", new StubMouse(button, pressed ? 1 : 0));
        }
        void keyboard(int key, boolean pressed) {
            if (pressed) NativeInputs.down.add(key); else NativeInputs.down.remove(key);
            tick();
        }
        long starts(String skill) { return wire.stream().filter(value -> value.endsWith(":" + skill) && value.startsWith("start:")).count(); }
    }
    public static void main(String[] args) throws Exception {
        inspectActualHookSites(); client = loadActualHooks();
        Rig before = new Rig(false);
        before.open(); before.mouse(0, true); before.close(); before.tick();
        check(before.wire.equals(List.of("start:0:braking_touch")), "negative control reproduces original Resume-click cast before F");
        check(before.starts("circulation_listening") == 0, "negative control is a mouse leak, not F misrouting");
        System.out.println("PASS original failure control: unfenced Resume mouse-down starts slot0 braking_touch before F");

        Rig resume = new Rig(true);
        resume.open(); resume.mouse(0, true); resume.close(); resume.tick(); resume.tick();
        check(resume.wire.isEmpty(), "same-frame native Resume click never casts or releases a high-power skill");
        resume.keyboard(33, true);
        check(resume.wire.equals(List.of("start:3:circulation_listening")), "first F press still reaches only Listening while Resume mouse is held");
        resume.keyboard(33, false); resume.mouse(0, false); resume.tick(); resume.mouse(0, true); resume.tick();
        check(resume.starts("braking_touch") == 1, "release then deliberate gameplay click casts once");
        resume.tick(); check(resume.starts("braking_touch") == 1, "held fresh click never repeats");
        System.out.println("PASS Resume mouse fence, independent F routing, and release/repress");

        Rig escape = new Rig(true);
        escape.open(); escape.close(); escape.keyboard(33, true);
        check(escape.starts("circulation_listening") == 1, "Escape resume does not swallow the first normal keyboard press");
        escape.mouse(0, true); escape.tick();
        check(escape.starts("braking_touch") == 1, "Escape resume does not swallow the first fresh mouse press");

        Rig held = new Rig(true);
        held.open(); NativeInputs.down.add(-100); held.close(); held.tick();
        check(held.wire.isEmpty(), "screen-close raw snapshot catches a held menu button even without a press callback");
        NativeInputs.down.remove(-100); held.tick(); held.mouse(0, true); held.tick();
        check(held.starts("braking_touch") == 1, "raw physical release clears a fence even without release callback");

        Rig rebound = new Rig(true);
        rebound.keys[0] = 19; rebound.binding.presetEdit(); rebound.open(); rebound.mouse(7, true);
        rebound.keys[0] = ClassicPhysicalKeys.mouse(7); rebound.binding.presetEdit(); rebound.close(); rebound.tick();
        check(rebound.wire.isEmpty(), "menu press before remapping is fenced on the newly assigned physical button");
        rebound.binding.modernSnapshotFallback(); rebound.tick();
        check(rebound.wire.isEmpty(), "snapshot/delegate replacement cannot leak a held menu button");
        rebound.mouse(7, false); rebound.tick(); rebound.mouse(7, true); rebound.tick();
        check(rebound.starts("braking_touch") == 1, "rebound mouse button casts on fresh post-menu press");

        Rig callbackRelease = new Rig(true);
        callbackRelease.open(); callbackRelease.mouse(0, true); callbackRelease.close();
        callbackRelease.mouse(0, false); callbackRelease.mouse(0, true); callbackRelease.tick();
        check(callbackRelease.starts("braking_touch") == 1, "release callback clears a completed menu click before the first gameplay poll");

        Rig owned = new Rig(true);
        owned.skills[0] = "magnetic_item_pull"; owned.binding.presetEdit(); owned.mouse(0, true); owned.tick();
        owned.open(); owned.close(); owned.tick();
        check(owned.wire.equals(List.of("start:0:magnetic_item_pull")), "closing an intentional skill UI cannot abort an already owned hold");
        owned.mouse(0, false); owned.tick();
        check(owned.wire.equals(List.of("start:0:magnetic_item_pull", "release:magnetic_item_pull")), "intentional selector hold still commits through its normal physical release");

        Rig replaced = new Rig(true);
        replaced.open(); replaced.mouse(0, true); replaced.close();
        replaced.binding.discard();
        var field = client.getDeclaredField("menuMouseFence"); field.setAccessible(true);
        ((MenuMouseInputFence) field.get(null)).clear();
        replaced.binding = new ClassicClientInputBinding(replaced); replaced.bindActualHost(); replaced.binding.runtime().updateDefaultGroup();
        replaced.binding.establishSessionFence(); replaced.tick();
        check(replaced.wire.isEmpty(), "new session's existing held-key fence survives clearing the old menu fence");
        replaced.mouse(0, false); replaced.tick(); replaced.mouse(0, true); replaced.tick();
        check(replaced.starts("braking_touch") == 1, "new session accepts a fresh release/repress after ownership replacement");

        Rig stale = new Rig(true);
        var oldNode = stale.binding.winningNodes().stream().filter(node -> node.slot == 0).findFirst().orElseThrow();
        stale.mouse(0, true); stale.tick(); stale.mouse(0, false); stale.tick();
        stale.skills[0] = "magnetic_item_pull"; stale.binding.presetEdit(); stale.mouse(0, true); stale.tick();
        stale.open(); stale.close();
        var currentNode = stale.binding.winningNodes().stream().filter(node -> node.slot == 0).findFirst().orElseThrow();
        check(!(boolean) call("menuMouseInputBlocked", currentNode), "exact active current node retains its held selector input");
        check((boolean) call("menuMouseInputBlocked", oldNode), "stale same-slot same-key node cannot borrow replacement node's active exemption");
        stale.mouse(0, false); stale.tick(); stale.skills[0] = "braking_touch"; stale.binding.presetEdit();
        stale.open(); stale.mouse(0, true); stale.close(); stale.tick();
        check(stale.starts("braking_touch") == 1, "released and rebound old slot cannot leak a new menu-origin press");

        Rig classic = new Rig(true);
        classic.skills[0] = "charging"; classic.binding.presetEdit();
        classic.open(); classic.mouse(0, true); classic.close(); classic.tick();
        check(classic.wire.equals(List.of("start:0:charging")), "negative control preserves classic source semantics");
        System.out.println("PASS Escape, held-menu fallback, rebind, replacement, callback release, owned selector, session replacement, and classic negative controls");
        System.out.println("HighPowerMenuMouseInputRegressionTest: " + checks + " checks passed");
    }

    private static void inspectActualHookSites() throws Exception {
        ClassNode node = read(CLIENT);
        var mouse = node.methods.stream().filter(m -> m.name.equals("menuMouseButton")).findFirst().orElseThrow();
        check(mouse.desc.equals("(Lnet/neoforged/neoforge/client/event/InputEvent$MouseButton$Pre;)V"), "production subscribes the pinned raw Pre event");
        var annotation = mouse.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/SubscribeEvent;")).findFirst().orElseThrow();
        check(annotation.values.contains("receiveCanceled") && annotation.values.contains(Boolean.TRUE), "release observation survives another listener cancelling input");
        var close = node.methods.stream().filter(m -> m.name.equals("menuScreenClosing")).findFirst().orElseThrow();
        check(close.desc.equals("(Lnet/neoforged/neoforge/client/event/ScreenEvent$Closing;)V") && close.visibleAnnotations != null,
                "production subscribes the actual screen-close boundary");
        var tick = node.methods.stream().filter(m -> m.name.equals("tick")).findFirst().orElseThrow();
        int poll = -1, dispatch = -1, index = 0;
        for (var op : tick.instructions) {
            if (op instanceof MethodInsnNode call) {
                if (call.name.equals("pollMenuMouseFence")) poll = index;
                if (call.owner.endsWith("/ClassicClientInputBinding") && call.name.equals("tickStart")) dispatch = index;
            }
            index++;
        }
        check(poll >= 0 && dispatch > poll, "actual raw-release polling precedes coordinator dispatch");
        var host = read(CLIENT + "$1");
        var eligible = host.methods.stream().filter(m -> m.name.equals("eligible")).findFirst().orElseThrow();
        String firstClientCall = null;
        for (var op : eligible.instructions) if (op instanceof MethodInsnNode call && call.owner.equals(CLIENT)) {
            firstClientCall = call.name; break;
        }
        check("menuMouseInputBlocked".equals(firstClientCall), "actual host applies fence before skill-specific dispatch gates");
    }
    private static ClassNode read(String name) throws Exception {
        try (var stream = HighPowerMenuMouseInputRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static Class<?> loadActualHooks() throws Exception {
        Map<String, String> remap = new HashMap<>(); remap.put(CLIENT, GENERATED);
        remap.put("net/minecraft/client/Minecraft", Type.getInternalName(StubMinecraft.class));
        remap.put("net/minecraft/client/gui/screens/Screen", "java/lang/Object");
        remap.put("net/neoforged/neoforge/client/event/InputEvent$MouseButton$Pre", Type.getInternalName(StubMouse.class));
        remap.put("net/neoforged/neoforge/client/event/ScreenEvent$Closing", Type.getInternalName(StubClosing.class));
        ClassNode node = read(CLIENT);
        Set<String> methods = Set.of("menuMouseButton", "menuScreenClosing", "pollMenuMouseFence", "menuMouseInputBlocked");
        node.methods.removeIf(method -> !methods.contains(method.name) && !method.name.startsWith("lambda$menuScreenClosing") && !method.name.startsWith("lambda$pollMenuMouseFence"));
        node.fields.removeIf(field -> !Set.of("menuMouseFence", "binding").contains(field.name));
        node.fields.forEach(field -> field.access &= ~Opcodes.ACC_FINAL);
        node.innerClasses.clear(); node.nestMembers.clear(); node.visibleAnnotations = null; node.invisibleAnnotations = null;
        for (var method : node.methods) for (var op : method.instructions) {
            if (op instanceof MethodInsnNode call && call.owner.equals(CLIENT) && call.name.equals("physicalDown")) call.owner = Type.getInternalName(NativeInputs.class);
        }
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(new ClassRemapper(writer, new SimpleRemapper(remap)));
        byte[] bytes = writer.toByteArray();
        var loader = new ClassLoader(HighPowerMenuMouseInputRegressionTest.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (!name.equals(GENERATED.replace('/', '.'))) throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return Class.forName(GENERATED.replace('/', '.'), true, loader);
    }
    public static final class StubMinecraft {
        static final StubMinecraft INSTANCE = new StubMinecraft(); public Object screen;
        public static StubMinecraft getInstance() { return INSTANCE; }
    }
    public record StubMouse(int button, int action) {
        public int getButton() { return button; }
        public int getAction() { return action; }
    }
    public static final class StubClosing {}
    public static final class NativeInputs {
        static final Set<Integer> down = new HashSet<>();
        public static boolean physicalDown(int key) { return down.contains(key); }
    }
}
