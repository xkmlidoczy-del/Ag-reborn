package cn.academy.port.highpower.mixin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Bytecode-only exact FoodData.tick admission. No gameplay class is loaded by the plugin. */
public final class NativeFoodRecoveryMixinPlugin implements IMixinConfigPlugin {
    private static final String ENTITY = "net.minecraft.world.entity.LivingEntity";
    private static final String REVIEWED_HEAL = "9854336dc026760775dba2d402936b1590e279777c9052661a45c6cd182c42df";
    private static final String FOOD = "net.minecraft.world.food.FoodData";
    private static final String REVIEWED_TICK = "037c5d9e46b0a3be5d9eb70000d6b0c02e23e78563db840edc4df5c524678041";

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!FOOD.equals(targetClassName) && !ENTITY.equals(targetClassName)) return false;
        try {
            ClassNode food = MixinService.getService().getBytecodeProvider().getClassNode(FOOD);
            ClassNode entity = MixinService.getService().getBytecodeProvider().getClassNode(ENTITY);
            boolean admitted = supportedFood(food) && supportedHeal(entity);
            if (!admitted) System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "FoodData natural regeneration differs from reviewed 1.21.1/21.1.252 bytecode; proportional recovery stays unavailable");
            return admitted;
        } catch (Exception unavailable) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                    "Cannot verify FoodData natural recovery seam: " + unavailable);
            return false;
        }
    }

    public static boolean supportedFood(ClassNode food) {
        return food.fields.stream().anyMatch(field -> field.name.equals("tickTimer") && field.desc.equals("I"))
                && REVIEWED_TICK.equals(fingerprint(food));
    }

    public static boolean supportedHeal(ClassNode entity) { return REVIEWED_HEAL.equals(healFingerprint(entity)); }
    public static String healFingerprint(ClassNode entity) { return fingerprint(entity, "heal", "(F)V"); }

    /** Stable symbolic instructions, constants and exact jump destinations; debug metadata is excluded. */
    public static String fingerprint(ClassNode food) {
        return fingerprint(food, "tick", "(Lnet/minecraft/world/entity/player/Player;)V");
    }
    private static String fingerprint(ClassNode food, String name, String descriptor) {
        MethodNode tick = food.methods.stream().filter(m -> m.name.equals(name)
                && m.desc.equals(descriptor)).findFirst().orElse(null);
        if (tick == null || !tick.tryCatchBlocks.isEmpty()) return "unreviewed";
        Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
        int index = 0;
        for (AbstractInsnNode node : tick.instructions) { indices.put(node, index); if (node.getOpcode() >= 0) index++; }
        StringBuilder symbolic = new StringBuilder(tick.desc).append('|');
        for (AbstractInsnNode node : tick.instructions) {
            if (node.getOpcode() < 0) continue;
            symbolic.append(node.getOpcode()).append(':');
            if (node instanceof VarInsnNode n) symbolic.append(n.var);
            else if (node instanceof IntInsnNode n) symbolic.append(n.operand);
            else if (node instanceof TypeInsnNode n) symbolic.append(n.desc);
            else if (node instanceof FieldInsnNode n) symbolic.append(n.owner).append('.').append(n.name).append(n.desc);
            else if (node instanceof MethodInsnNode n) symbolic.append(n.owner).append('.').append(n.name).append(n.desc).append(n.itf);
            else if (node instanceof JumpInsnNode n) symbolic.append(indices.get(n.label));
            else if (node instanceof LdcInsnNode n) symbolic.append(n.cst.getClass().getName()).append('=').append(n.cst);
            else if (node instanceof IincInsnNode n) symbolic.append(n.var).append(',').append(n.incr);
            else if (!(node instanceof InsnNode)) return "unreviewed";
            symbolic.append(';');
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(symbolic.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
