package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** The reviewed bytecode is executable evidence; a one-opcode change closes the adapter. */
public final class ScatterPlateNativeSeamRegressionTest {
    public static void main(String[] args) throws Exception {
        Map<String, ClassNode> classes = new HashMap<>();
        for (String key : NativeScatterPlateMixinPlugin.reviewedMethods()) {
            String type = key.substring(0, key.indexOf('#'));
            if (!classes.containsKey(type)) try (var stream = ScatterPlateNativeSeamRegressionTest.class.getResourceAsStream("/" + type.replace('.', '/') + ".class")) {
                if (stream == null) throw new AssertionError("Missing pinned native class: " + type);
                ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); classes.put(type, node);
            }
        }
        if (!NativeScatterPlateMixinPlugin.supported(classes)) throw new AssertionError("Reviewed native seams rejected");
        int checks = 1;
        for (String key : NativeScatterPlateMixinPlugin.reviewedMethods()) {
            int split = key.indexOf('#'), desc = key.indexOf('(', split); ClassNode type = classes.get(key.substring(0, split));
            MethodNode method = type.methods.stream().filter(m -> m.name.equals(key.substring(split + 1, desc)) && m.desc.equals(key.substring(desc))).findFirst().orElseThrow();
            InsnNode changed = new InsnNode(Opcodes.NOP); method.instructions.insert(changed);
            if (NativeScatterPlateMixinPlugin.supported(classes)) throw new AssertionError("Changed native path accepted: " + key);
            method.instructions.remove(changed); checks++;
        }
        System.out.println("Scatter plate native seams: " + checks + " checks passed");
    }
}
