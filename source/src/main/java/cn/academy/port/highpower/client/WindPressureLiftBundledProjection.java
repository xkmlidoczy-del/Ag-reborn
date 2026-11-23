package cn.academy.port.highpower.client;

import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.ENTITY;
import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.LIVING_ENTITY;
import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.TRAVEL;
import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.TRAVEL_DESC;
import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.tree.*;

/** Proves the native travel path in the presence of the bundled FormPhysics and ShieldMotion hooks.
 * Wind lift excludes the three special form movements. The shield hook passes velocity through
 * on the local client. The running classes and their hooks are unmodified. */
final class WindPressureLiftBundledProjection {
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String DESCRIPTOR = "(Lnet/minecraft/world/phys/Vec3;L" + CALLBACK + ";)V";
    private static final String PREFIX = "handler\\$[a-z0-9]+\\$aca_omnitrix\\$";
    private static final String FORM_PHYSICS = "cn.aca.omnitrix.mixin.FormPhysics";
    private static final String FLIGHT = "omnitrix$viltrumFlight";
    private static final String LAVA = "omnitrix$heatblastLava";
    private static final String SNOW = "omnitrix$snowStep";
    private static final String SHIELD_MOTION = "cn.academy.port.mixin.combat.ShieldMotionMixin";
    private static final String SHIELD = "academy$preserveContactVelocity";
    private static final String SET_DELTA = "setDeltaMovement";
    private static final String SET_HOOK_DESC = "(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;";
    private static final int CANCELLABLE_SIZE = 14, PLAIN_SIZE = 4;

    private WindPressureLiftBundledProjection() {}

    static ClassNode project(ClassNode raw, ClassNode transformed) {
        if (transformed == null) return null;
        if (ENTITY.equals(raw.name)) return projectShield(raw, transformed);
        if (!LIVING_ENTITY.equals(raw.name)) return transformed;
        List<MethodNode> hooks = transformed.methods.stream().filter(m -> DESCRIPTOR.equals(m.desc)
                && FORM_PHYSICS.equals(WindPressureLiftSableProjection.origin(m))).toList();
        if (hooks.isEmpty()) return transformed;
        if (hooks.size() != 3) return null;

        MethodNode flight = verified(transformed, hooks, FLIGHT, "0eee3aa4d7506caae2840d74ed990b8db4799b17eddae1ccd82b7723840423a6");
        MethodNode lava = verified(transformed, hooks, LAVA, "9e3d84ccbd631ae5a35a84e602268d80ae31c64bdaeaa61fa9628978ce080f15");
        MethodNode snow = verified(transformed, hooks, SNOW, "d342b49293283259a83196f1568efa9344d37319871654c5ca0422f3467cdd09");
        if (flight == null || lava == null || snow == null) return null;

        var copy = new ClassNode();
        transformed.accept(copy);
        MethodNode travel = method(copy, TRAVEL, TRAVEL_DESC), original = method(raw, TRAVEL, TRAVEL_DESC);
        if (travel == null || original == null) return null;
        List<AbstractInsnNode> code = operations(travel);
        int start = CANCELLABLE_SIZE * 2;
        int size = start + PLAIN_SIZE;
        int firstSlot = original.maxLocals;
        if (code.size() <= size || travel.maxLocals < firstSlot + 2
                || !cancellable(code, 0, firstSlot, flight.name)
                || !cancellable(code, CANCELLABLE_SIZE, firstSlot + 1, lava.name)
                || !var(code.get(start), ALOAD, 0) || !var(code.get(start + 1), ALOAD, 1)
                || code.get(start + 2).getOpcode() != ACONST_NULL
                || !call(code.get(start + 3), INVOKESPECIAL, LIVING_ENTITY, snow.name, DESCRIPTOR)) return null;
        for (int i = 0; i < size; i++) travel.instructions.remove(code.get(i));
        travel.maxLocals -= 2;
        travel.maxStack = original.maxStack;
        return copy;
    }

    private static ClassNode projectShield(ClassNode raw, ClassNode transformed) {
        var hooks = transformed.methods.stream().filter(m -> SET_HOOK_DESC.equals(m.desc)
                && SHIELD_MOTION.equals(WindPressureLiftSableProjection.origin(m))).toList();
        if (hooks.isEmpty()) return transformed;
        if (hooks.size() != 1) return null;
        var hook = hooks.getFirst();
        if (!hook.name.matches("localvar\\$[a-z0-9]+\\$" + java.util.regex.Pattern.quote(SHIELD))
                || !fingerprintMatches(transformed, hook, SHIELD, "7e60b9055879ef3237dc11baddad1ce922a1a713319768bc67629f11bb2f23c2")) return null;
        var copy = new ClassNode();
        transformed.accept(copy);
        MethodNode setter = method(copy, SET_DELTA, TRAVEL_DESC), original = method(raw, SET_DELTA, TRAVEL_DESC);
        if (setter == null || original == null) return null;
        List<AbstractInsnNode> code = operations(setter);
        if (code.size() < 5 || !var(code.get(0), ALOAD, 0) || !var(code.get(1), ALOAD, 1)
                || !call(code.get(2), INVOKESPECIAL, ENTITY, hook.name, SET_HOOK_DESC) || !var(code.get(3), ASTORE, 1)) return null;
        for (int i = 0; i < 4; i++) setter.instructions.remove(code.get(i));
        setter.maxStack = original.maxStack;
        return copy;
    }

    private static List<AbstractInsnNode> operations(MethodNode method) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (var op : method.instructions) if (op.getOpcode() >= 0) code.add(op);
        return code;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var methods = owner.methods.stream().filter(m -> name.equals(m.name) && descriptor.equals(m.desc)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }

    private static MethodNode verified(ClassNode owner, List<MethodNode> hooks, String name, String fingerprint) {
        var matches = hooks.stream().filter(m -> m.name.matches(PREFIX + java.util.regex.Pattern.quote(name))).toList();
        if (matches.size() != 1) return null;
        var hook = matches.getFirst();
        return fingerprintMatches(owner, hook, name, fingerprint) ? hook : null;
    }

    private static boolean fingerprintMatches(ClassNode owner, MethodNode hook, String name, String fingerprint) {
        var normalized = new MethodNode(ASM9, hook.access, name, hook.desc, hook.signature, hook.exceptions.toArray(String[]::new));
        hook.accept(normalized);
        return fingerprint.equals(WindPressureLiftSableProjection.canonicalFingerprint(owner, normalized));
    }

    private static boolean cancellable(List<AbstractInsnNode> code, int at, int slot, String handler) {
        return code.get(at) instanceof TypeInsnNode type && type.getOpcode() == NEW && CALLBACK.equals(type.desc)
                && code.get(at + 1).getOpcode() == DUP && code.get(at + 2) instanceof LdcInsnNode constant && TRAVEL.equals(constant.cst)
                && code.get(at + 3).getOpcode() == ICONST_1
                && call(code.get(at + 4), INVOKESPECIAL, CALLBACK, "<init>", "(Ljava/lang/String;Z)V")
                && var(code.get(at + 5), ASTORE, slot)
                && var(code.get(at + 6), ALOAD, 0) && var(code.get(at + 7), ALOAD, 1) && var(code.get(at + 8), ALOAD, slot)
                && call(code.get(at + 9), INVOKESPECIAL, LIVING_ENTITY, handler, DESCRIPTOR)
                && var(code.get(at + 10), ALOAD, slot)
                && call(code.get(at + 11), INVOKEVIRTUAL, CALLBACK, "isCancelled", "()Z")
                && code.get(at + 12) instanceof JumpInsnNode jump && jump.getOpcode() == IFEQ
                && code.get(at + 13).getOpcode() == RETURN && nextOperation(jump.label) == code.get(at + CANCELLABLE_SIZE);
    }

    private static AbstractInsnNode nextOperation(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static boolean var(AbstractInsnNode node, int opcode, int slot) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot;
    }

    private static boolean call(AbstractInsnNode node, int opcode, String owner, String name, String descriptor) {
        return node instanceof MethodInsnNode call && call.getOpcode() == opcode && !call.itf
                && owner.equals(call.owner) && name.equals(call.name) && descriptor.equals(call.desc);
    }
}
