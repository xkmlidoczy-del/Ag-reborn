package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeFoodRecoveryMixinPlugin;
import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class NativeFoodRecoveryGuardRegressionTest {
    private static int checks;
    public static void main(String[] args) throws IOException {
        byte[] bytes;
        try (var stream = NativeFoodRecoveryGuardRegressionTest.class.getClassLoader().getResourceAsStream("net/minecraft/world/food/FoodData.class")) {
            if (stream == null) throw new IOException("Cached reviewed FoodData missing");
            bytes = stream.readAllBytes();
        }
        try (var stream = NativeFoodRecoveryGuardRegressionTest.class.getClassLoader().getResourceAsStream("net/minecraft/world/entity/LivingEntity.class")) {
            if (stream == null) throw new IOException("Cached reviewed LivingEntity missing");
            ClassNode entity = node(stream.readAllBytes());
            System.out.println("LivingEntity.heal reviewed fingerprint=" + NativeFoodRecoveryMixinPlugin.healFingerprint(entity));
            check(NativeFoodRecoveryMixinPlugin.supportedHeal(entity), "actual reviewed native heal admitted");
            entity.methods.stream().filter(m -> m.name.equals("heal") && m.desc.equals("(F)V")).findFirst().orElseThrow().instructions.insert(new InsnNode(Opcodes.NOP));
            check(!NativeFoodRecoveryMixinPlugin.supportedHeal(entity), "changed native heal refused");
        }
        System.out.println("FoodData.tick reviewed fingerprint=" + NativeFoodRecoveryMixinPlugin.fingerprint(node(bytes)));
        check(NativeFoodRecoveryMixinPlugin.supportedFood(node(bytes)), "actual reviewed FoodData.tick admitted");
        check(!NativeFoodRecoveryMixinPlugin.supportedFood(new ClassNode()), "missing bytecode refused");
        ClassNode changed = node(bytes);
        changed.fields.removeIf(f -> f.name.equals("tickTimer"));
        check(!NativeFoodRecoveryMixinPlugin.supportedFood(changed), "missing timer field refused");
        // Exhaustively mutate every semantic instruction: no changed healing, hunger, timer,
        // gamerule, branch or starvation instruction can silently pass the pinned seam.
        int instruction = 0;
        for (AbstractInsnNode n : tick(node(bytes)).instructions) {
            if (n.getOpcode() < 0) continue;
            changed = node(bytes);
            int seen = 0;
            for (AbstractInsnNode candidate : tick(changed).instructions) {
                if (candidate.getOpcode() < 0) continue;
                if (seen++ == instruction) { tick(changed).instructions.set(candidate, new InsnNode(Opcodes.NOP)); break; }
            }
            check(!NativeFoodRecoveryMixinPlugin.supportedFood(changed), "semantic instruction " + instruction + " mutation refused");
            instruction++;
        }
        changed = node(bytes);
        tick(changed).instructions.insert(new InsnNode(Opcodes.NOP));
        check(!NativeFoodRecoveryMixinPlugin.supportedFood(changed), "extra semantic instruction refused");
        changed = node(bytes);
        tick(changed).instructions.insert(new LabelNode());
        check(NativeFoodRecoveryMixinPlugin.supportedFood(changed), "debug-only labels do not alter admission");
        System.out.println("PASS " + checks + " natural-recovery bytecode admission/refusal checks");
    }
    private static ClassNode node(byte[] bytes) { var n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n; }
    private static MethodNode tick(ClassNode n) { return n.methods.stream().filter(m -> m.name.equals("tick") && m.desc.endsWith("Player;)V")).findFirst().orElseThrow(); }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
