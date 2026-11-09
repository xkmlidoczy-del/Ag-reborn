package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin;
import java.util.HashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Pins the actual one-hurt native body and all owner/deflection/metadata evidence NEW331 consumes. */
public final class ShortInterceptionNativeSeamRegressionTest {
    public static void main(String[] args) throws Exception {
        Map<String, ClassNode> classes = new HashMap<>();
        for (String key : NativeScatterPlateMixinPlugin.reviewedMethods()) {
            String type = key.substring(0, key.indexOf('#'));
            if (!classes.containsKey(type)) try (var in = ShortInterceptionNativeSeamRegressionTest.class.getResourceAsStream("/" + type.replace('.', '/') + ".class")) {
                if (in == null) throw new AssertionError("Missing reviewed native class " + type);
                ClassNode node = new ClassNode(); new ClassReader(in).accept(node, 0); classes.put(type, node);
            }
        }
        if (!NativeScatterPlateMixinPlugin.supported(classes)) throw new AssertionError("Reviewed NEW331 native path rejected");
        ClassNode arrow = classes.get("net.minecraft.world.entity.projectile.AbstractArrow");
        var body = arrow.methods.stream().filter(m -> m.name.equals("onHitEntity") && m.desc.equals("(Lnet/minecraft/world/phys/EntityHitResult;)V")).findFirst().orElseThrow();
        int hurt = 0;
        for (var instruction : body.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals("net/minecraft/world/entity/Entity") && call.name.equals("hurt")
                && call.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")) hurt++;
        if (hurt != 1) throw new AssertionError("Native body must have exactly one original boolean hurt operand");
        int checks = 2;
        for (String key : NativeScatterPlateMixinPlugin.reviewedMethods()) {
            int split = key.indexOf('#'), descriptor = key.indexOf('(', split);
            var method = classes.get(key.substring(0, split)).methods.stream()
                    .filter(m -> m.name.equals(key.substring(split + 1, descriptor)) && m.desc.equals(key.substring(descriptor))).findFirst().orElseThrow();
            var changed = new InsnNode(Opcodes.NOP); method.instructions.insert(changed);
            if (NativeScatterPlateMixinPlugin.supported(classes)) throw new AssertionError("Altered native method admitted: " + key);
            method.instructions.remove(changed); checks++;
        }
        for (String field : new String[]{"life", "inGroundTime", "shakeTime", "pickup"}) {
            var original = arrow.fields.stream().filter(f -> f.name.equals(field)).findFirst().orElseThrow();
            String descriptor = original.desc; original.desc = "J";
            if (NativeScatterPlateMixinPlugin.supported(classes)) throw new AssertionError("Altered native metadata admitted: " + field);
            original.desc = descriptor; checks++;
        }
        if (!NativeScatterPlateMixinPlugin.supported(classes)) throw new AssertionError("Restored native evidence remains rejected");
        System.out.println("Short interception native seams: " + checks + " checks passed");
    }
}
