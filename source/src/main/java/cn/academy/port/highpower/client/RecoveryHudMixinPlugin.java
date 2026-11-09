package cn.academy.port.highpower.client;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Bytecode-only pinned client callsite admission; failure leaves the native HUD untouched. */
public final class RecoveryHudMixinPlugin implements IMixinConfigPlugin {
    public static final String GUI = "net/minecraft/client/gui/Gui";
    public static final String HEARTS = "(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V";
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!targetClassName.equals(GUI.replace('/', '.'))) return false;
        try { return supported(MixinService.getService().getBytecodeProvider().getClassNode(targetClassName)); }
        catch (Exception unavailable) { return false; }
    }
    public static boolean supported(ClassNode node) {
        if (!GUI.equals(node.name)) return false;
        var overlay = node.methods.stream().filter(method -> method.name.equals("renderOverlayMessage")
                && method.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V")
                && (method.access & Opcodes.ACC_STATIC) == 0).findFirst().orElse(null);
        if (overlay == null) return false;
        int overlayReads = 0;
        for (var instruction : overlay.instructions) if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                && field.owner.equals(GUI) && field.name.equals("overlayMessageString")) {
            if (field.getOpcode() != Opcodes.GETFIELD || !field.desc.equals("Lnet/minecraft/network/chat/Component;")) return false;
            overlayReads++;
        }
        if (overlayReads != 3) return false;
        var render = node.methods.stream().filter(method -> method.name.equals("renderHealthLevel")
                && method.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;)V")).findFirst().orElse(null);
        var hearts = node.methods.stream().filter(method -> method.name.equals("renderHearts") && method.desc.equals(HEARTS)).findFirst().orElse(null);
        if (render == null || hearts == null) return false;
        int jitterThresholds = 0;
        for (var instruction : hearts.instructions) if (instruction.getOpcode() == Opcodes.ICONST_4) {
            jitterThresholds++;
            AbstractInsnNode sum = previous(instruction), absorption = previous(sum), current = previous(absorption), branch = next(instruction);
            if (sum == null || sum.getOpcode() != Opcodes.IADD
                    || !(current instanceof VarInsnNode hp) || hp.getOpcode() != Opcodes.ILOAD || hp.var != 8
                    || !(absorption instanceof VarInsnNode gold) || gold.getOpcode() != Opcodes.ILOAD || gold.var != 10
                    || !(branch instanceof JumpInsnNode) || branch.getOpcode() != Opcodes.IF_ICMPGT) return false;
        }
        if (jitterThresholds != 1) return false;
        int matches = 0;
        for (var instruction : render.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(GUI) && call.name.equals("renderHearts")) {
            if (!call.desc.equals(HEARTS) || call.getOpcode() != Opcodes.INVOKEVIRTUAL) return false;
            matches++;
        }
        return matches == 1;
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        if (node == null) return null;
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    private static AbstractInsnNode next(AbstractInsnNode node) {
        if (node == null) return null;
        do { node = node.getNext(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
