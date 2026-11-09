package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Reads a real native run's final transformed connection class. No global runtime witness is mutated. */
public final class WindPressureLiftTeleportNativeSeamRegressionTest {
    private static final String MARKER = "cn/academy/port/highpower/NativeWindPressureLiftTeleportAccess";
    private static final String LIFT = "cn/academy/port/highpower/WindPressureLift";
    private static int checks;
    private WindPressureLiftTeleportNativeSeamRegressionTest() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass the actual exported ServerGamePacketListenerImpl.class path");
        byte[] transformed = Files.readAllBytes(Path.of(args[0]));
        ClassNode nativeClass = read(transformed);
        byte[] before = encode(nativeClass);
        require(NativeScatterPlateMixinPlugin.windPressureLiftTeleportInstalled(nativeClass),
                "actual final transformed connection has the exact installed NEW443 teleport invalidator");
        // The read-only witness must leave the actual transform target's instruction order unchanged.
        require(NativeScatterPlateMixinPlugin.windPressureLiftTeleportInstalled(nativeClass), "repeated observation stays admitted");
        require(java.util.Arrays.equals(before, encode(nativeClass)), "witness never mutates its actual transformer target");
        reject(transformed, n -> n.interfaces.remove(MARKER), "missing installed marker");
        reject(transformed, n -> n.methods.remove(callback(n)), "missing actual callback method");
        reject(transformed, n -> {
            MethodNode callback = callback(n); MethodInsnNode abort = liftAbort(callback);
            AbstractInsnNode field = abort.getPrevious(), owner = field.getPrevious();
            callback.instructions.remove(owner); callback.instructions.remove(field); callback.instructions.remove(abort);
        }, "marker-only callback without NEW443 abort");
        reject(transformed, n -> host(n).instructions.remove(hostCall(n)), "host does not invoke the callback");
        reject(transformed, n -> hostCall(n).name = "unrelatedCallback", "host invokes a different callback");
        reject(transformed, n -> liftAbort(callback(n)).name = "active", "callback queries active instead of aborting");
        reject(transformed, n -> {
            List<MethodInsnNode> calls = operations(callback(n)).stream().filter(op -> op instanceof MethodInsnNode)
                    .map(op -> (MethodInsnNode) op).toList();
            String first = calls.getFirst().owner; calls.getFirst().owner = calls.getLast().owner; calls.getLast().owner = first;
        }, "prior controller abort order changed");
        reject(transformed, n -> {
            MethodNode host = host(n); MethodInsnNode call = hostCall(n);
            host.instructions.remove(call); host.instructions.add(call);
        }, "callback moved after native teleport");
        reject(transformed, n -> host(n).instructions.insert(new InsnNode(Opcodes.NOP)), "unknown instruction before the admitted HEAD call");
        reject(transformed, n -> host(n).instructions.add(new InsnNode(Opcodes.NOP)), "unknown native tail instruction");
        reject(transformed, n -> {
            MethodInsnNode move = operations(host(n)).stream().filter(op -> op instanceof MethodInsnNode m && m.name.equals("absMoveTo"))
                    .map(op -> (MethodInsnNode) op).findFirst().orElseThrow();
            move.name = "moveTo";
        }, "native movement call changed under otherwise genuine callback");
        reject(transformed, n -> {
            MethodNode original = callback(n), duplicate = new MethodNode(original.access, "duplicate$academy$clearBeforeTeleport",
                    original.desc, original.signature, original.exceptions.toArray(String[]::new));
            original.accept(duplicate); n.methods.add(duplicate);
        }, "ambiguous duplicate callback");
        reject(transformed, n -> callback(n).access = Opcodes.ACC_PUBLIC, "callback visibility changed");
        require(NativeScatterPlateMixinPlugin.windPressureLiftTeleportInstalled(read(transformed)), "all isolated mutations leave the original export admitted");
        System.out.println("Wind-pressure lift final teleport witness: " + checks + " checks passed");
    }
    private static byte[] encode(ClassNode node) { ClassWriter out = new ClassWriter(0); node.accept(out); return out.toByteArray(); }
    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node;
    }
    private static void reject(byte[] bytes, Consumer<ClassNode> mutation, String reason) {
        ClassNode node = read(bytes); mutation.accept(node);
        require(!NativeScatterPlateMixinPlugin.windPressureLiftTeleportInstalled(node), reason + " must refuse NEW443");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message); checks++;
    }
    private static MethodNode callback(ClassNode n) {
        return n.methods.stream().filter(m -> m.name.endsWith("academy$clearBeforeTeleport")).findFirst().orElseThrow();
    }
    private static MethodNode host(ClassNode n) {
        return n.methods.stream().filter(m -> m.name.equals("teleport") && m.desc.equals("(DDDFFLjava/util/Set;)V")).findFirst().orElseThrow();
    }
    private static MethodInsnNode hostCall(ClassNode n) {
        String callback = callback(n).name;
        return operations(host(n)).stream().filter(op -> op instanceof MethodInsnNode m && m.owner.equals(n.name) && m.name.equals(callback))
                .map(op -> (MethodInsnNode) op).findFirst().orElseThrow();
    }
    private static MethodInsnNode liftAbort(MethodNode method) {
        return operations(method).stream().filter(op -> op instanceof MethodInsnNode m && m.owner.equals(LIFT) && m.name.equals("abort"))
                .map(op -> (MethodInsnNode) op).findFirst().orElseThrow();
    }
    private static List<AbstractInsnNode> operations(MethodNode method) {
        List<AbstractInsnNode> instructions = new ArrayList<>();
        for (AbstractInsnNode op : method.instructions) if (op.getOpcode() >= 0) instructions.add(op);
        return instructions;
    }
}
