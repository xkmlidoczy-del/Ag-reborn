package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeCooldownMixinPlugin;
import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Reads cached runtime bytecode only; no Minecraft/gameplay class is initialized. */
public final class NativeCooldownGuardRegressionTest {
    private static final String ENTITY = "net/minecraft/world/entity/LivingEntity";
    private static int assertions;

    public static void main(String[] args) throws IOException {
        byte[] entity = read(ENTITY), player = read("net/minecraft/world/entity/player/Player");
        check(NativeCooldownMixinPlugin.supportedEntity(node(entity)), "actual reviewed LivingEntity shape");
        check(NativeCooldownMixinPlugin.containerDrivenDamage(node(player)), "actual reviewed Player container-driven damage");
        check(!NativeCooldownMixinPlugin.supportedEntity(new ClassNode()), "missing method refused");
        ClassNode mutated = node(entity);
        firstRead(hurt(mutated)).owner = "unreviewed/OtherEntity";
        check(!NativeCooldownMixinPlugin.supportedEntity(mutated), "wrong first read refused");
        mutated = node(entity);
        ((VarInsnNode)previous(previous(firstRead(hurt(mutated))))).var = 3;
        check(!NativeCooldownMixinPlugin.supportedEntity(mutated), "wrong current-amount local refused");
        mutated = node(entity);
        MethodNode hurt = hurt(mutated);
        AbstractInsnNode comparison = next(firstRead(hurt));
        hurt.instructions.set(comparison, new InsnNode(Opcodes.FCMPL));
        check(!NativeCooldownMixinPlugin.supportedEntity(mutated), "changed comparison refused");
        mutated = node(entity);
        ((JumpInsnNode)next(next(firstRead(hurt(mutated))))).setOpcode(Opcodes.IFLE);
        check(!NativeCooldownMixinPlugin.supportedEntity(mutated), "changed branch refused");
        mutated = node(entity);
        hurt(mutated).instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY, "lastHurt", "F"));
        check(!NativeCooldownMixinPlugin.supportedEntity(mutated), "unexpected additional read refused");
        mutated = node(player);
        actuallyHurt(mutated).instructions.insert(new VarInsnNode(Opcodes.FLOAD, 2));
        check(!NativeCooldownMixinPlugin.containerDrivenDamage(mutated), "obsolete float-argument dependency refused");
        mutated = node(entity);
        for (var instruction : actuallyHurt(mutated).instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("onLivingDamagePost")) call.name = "unreviewedPost";
        }
        check(!NativeCooldownMixinPlugin.supportedEntity(mutated), "missing reviewed Post hook refused");
        for (byte[] original : new byte[][] {entity, player}) {
            mutated = node(original);
            absorptionEnum(actuallyHurt(mutated)).name = "ARMOR";
            check(!NativeCooldownMixinPlugin.containerDrivenDamage(mutated), "second reduction must consume ABSORPTION, not an ordinal lookalike");
            mutated = node(original);
            absorptionEnum(actuallyHurt(mutated)).owner = "unreviewed/Reduction";
            check(!NativeCooldownMixinPlugin.containerDrivenDamage(mutated), "absorption enum owner is pinned");
            mutated = node(original);
            MethodNode actual = actuallyHurt(mutated);
            MethodInsnNode second = secondReduction(actual);
            actual.instructions.insertBefore(second, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    second.owner, second.name, second.desc, false));
            check(!NativeCooldownMixinPlugin.containerDrivenDamage(mutated), "additional direct reduction call refuses the ordinal seam");
            mutated = node(original);
            hook(actuallyHurt(mutated), "onLivingDamagePre").desc = "()F";
            check(!NativeCooldownMixinPlugin.containerDrivenDamage(mutated), "Pre signature is pinned");
            mutated = node(original);
            actual = actuallyHurt(mutated);
            MethodInsnNode pre = hook(actual, "onLivingDamagePre");
            actual.instructions.remove(pre);
            actual.instructions.insert(secondReduction(actual), pre);
            check(!NativeCooldownMixinPlugin.containerDrivenDamage(mutated), "Pre cannot move after the absorption seam");
            mutated = node(original);
            actual = actuallyHurt(mutated);
            MethodInsnNode debit = call(actual, "setAbsorptionAmount");
            actual.instructions.remove(debit);
            actual.instructions.insertBefore(secondReduction(actual), debit);
            check(!NativeCooldownMixinPlugin.containerDrivenDamage(mutated), "absorption cannot be debited before the seam");
            mutated = node(original);
            ((VarInsnNode)next(hook(actuallyHurt(mutated), "onLivingDamagePre"))).var = 37;
            check(!NativeCooldownMixinPlugin.containerDrivenDamage(mutated), "absorption must use the actual Pre return local");
        }
        System.out.println("PASS " + assertions + " pinned native cooldown bytecode admission/refusal checks");
    }

    private static byte[] read(String type) throws IOException {
        try (var input = NativeCooldownGuardRegressionTest.class.getClassLoader().getResourceAsStream(type + ".class")) {
            if (input == null) throw new IOException("Missing cached class resource " + type);
            return input.readAllBytes();
        }
    }
    private static ClassNode node(byte[] bytes) { var node = new ClassNode(); new ClassReader(bytes).accept(node, 0); return node; }
    private static MethodNode hurt(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals("hurt") && m.desc.endsWith(";F)Z")).findFirst().orElseThrow(); }
    private static MethodNode actuallyHurt(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals("actuallyHurt") && m.desc.endsWith(";F)V")).findFirst().orElseThrow(); }
    private static FieldInsnNode firstRead(MethodNode method) {
        for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && field.owner.equals(ENTITY) && field.name.equals("lastHurt")) return field;
        throw new AssertionError("No native comparison read");
    }
    private static FieldInsnNode absorptionEnum(MethodNode method) {
        for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field
                && field.name.equals("ABSORPTION") && field.getOpcode() == Opcodes.GETSTATIC) return field;
        throw new AssertionError("No absorption enum");
    }
    private static MethodInsnNode secondReduction(MethodNode method) {
        int count = 0;
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals("net/neoforged/neoforge/common/damagesource/DamageContainer")
                && call.name.equals("setReduction") && ++count == 2) return call;
        throw new AssertionError("No second direct reduction");
    }
    private static MethodInsnNode hook(MethodNode method, String name) { return call(method, name); }
    private static MethodInsnNode call(MethodNode method, String name) {
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return call;
        throw new AssertionError("No call " + name);
    }
    private static AbstractInsnNode next(AbstractInsnNode node) { do { node = node.getNext(); } while (node.getOpcode() < 0); return node; }
    private static AbstractInsnNode previous(AbstractInsnNode node) { do { node = node.getPrevious(); } while (node.getOpcode() < 0); return node; }
    private static void check(boolean condition, String why) { assertions++; if (!condition) throw new AssertionError(why); }
}
