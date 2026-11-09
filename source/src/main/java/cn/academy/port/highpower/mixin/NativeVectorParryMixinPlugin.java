package cn.academy.port.highpower.mixin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

/** Admission is local to NEW412 precision. Unsupported native code retains ordinary combat. */
public final class NativeVectorParryMixinPlugin implements IMixinConfigPlugin {
    private static final String LIVING = "net.minecraft.world.entity.LivingEntity";
    private static final String HOOKS = "net.neoforged.neoforge.common.CommonHooks";
    private static final String EVENT = "net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent";
    private static final String MIXIN = "cn.academy.port.highpower.mixin.LivingEntityVectorParryMixin";
    private static final String BRIDGE = "cn/academy/port/highpower/VectorParryNativeBridge";
    private static final String ACCESS = "cn/academy/port/highpower/NativeVectorParryAccess";
    private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    private static final String ENTITY_ARG = "Lnet/minecraft/world/entity/LivingEntity;";
    private static final String HURT = "(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    private static final String KNOCKBACK = "(DDD)V";
    private static final String EVENT_DESC = "(Lnet/minecraft/world/entity/LivingEntity;FDD)Lnet/neoforged/neoforge/event/entity/living/LivingKnockBackEvent;";
    // Review source and bytecode together; never refresh fingerprints merely to make a mismatch pass.
    private static final Map<String, String> REVIEWED = Map.ofEntries(
            Map.entry(LIVING + "#hurt" + HURT, "1bf1a85771f8e3c1d9327d6a4d12001eb0b6007fc16c175983027415c0e5f37e"),
            Map.entry(LIVING + "#knockback" + KNOCKBACK, "c447c49266093eb1b720a175963ea40fa2c2ccb85277a52ea0e6322ba3e53376"),
            Map.entry(HOOKS + "#onLivingKnockBack" + EVENT_DESC, "061433ba07ddc057e6dc3ac7329e13a66738115a4473659617f5e75937b476a0"),
            Map.entry(EVENT + "#<init>(Lnet/minecraft/world/entity/LivingEntity;FDD)V", "fce319db26bb4214996c3712df92b30796ed2de71535238ceb5a143c4a1a07a6"),
            Map.entry(EVENT + "#getStrength()F", "72a4311a68d0820305105cff9aa221d66888f717ad577f2a66e126ebafa53a98"),
            Map.entry(EVENT + "#getRatioX()D", "fb7d9f60c944fa35430a0046b98beb222eb33e9f6536da742111f544c43bceb4"),
            Map.entry(EVENT + "#getRatioZ()D", "efcdb27b9be9cbec4b94f9f4ab3c16a58db93117dfd14df148e55ee17ab71150"),
            Map.entry(EVENT + "#setRatioX(D)V", "8f5712b3719512584905bcf64466f96b24ecfadb125afd9c30170f2c18b1d1a1"),
            Map.entry(EVENT + "#setRatioZ(D)V", "a4f496f3f9e5060874656403508b1a2666efa3f40fa6b26958487365e9b40f3a"),
            Map.entry("net.minecraft.world.phys.Vec3#normalize()Lnet/minecraft/world/phys/Vec3;", "b74ba6bc2a5ff0081ac542a160d1905a17b47a174044d19cbdac7dab9f9ead20"),
            Map.entry("net.minecraft.world.phys.Vec3#scale(D)Lnet/minecraft/world/phys/Vec3;", "e4e3e61de86f5fcffcd3618d91ba5fbe22a93dce0a35ce385462230001686f9e"));
    private static volatile boolean reviewedNativeTurnSupported;
    private boolean admitted;
    private boolean sitesPresent;
    private boolean merged;
    private boolean finalWitnessRegistered;

    public static boolean isReviewedNativeTurnSupported() { return reviewedNativeTurnSupported; }
    public static void disableAfterCallbackFailure(Throwable failure) {
        reviewedNativeTurnSupported = false;
        warn("NEW412 precision adapter failed; ordinary combat is unchanged: " + failure);
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!LIVING.equals(target) || !MIXIN.equals(mixin)) return false;
        if (!finalWitnessRegistered) {
            warn("NEW412 final transformation witness is unavailable; precision adapter is not applied");
            return false;
        }
        reviewedNativeTurnSupported = false;
        admitted = false;
        try {
            Map<String, ClassNode> classes = new HashMap<>();
            for (String key : REVIEWED.keySet()) {
                String owner = key.substring(0, key.indexOf('#'));
                if (!classes.containsKey(owner)) classes.put(owner, MixinService.getService().getBytecodeProvider().getClassNode(owner));
            }
            admitted = supported(classes);
            if (!admitted) warn("Native knockback differs from reviewed 1.21.1/21.1.252; NEW412 precision is unavailable");
            return admitted;
        } catch (Exception | LinkageError unavailable) {
            warn("Cannot verify NEW412 native knockback; precision is unavailable: " + unavailable);
            return false;
        }
    }

    public static Set<String> reviewedMethods() { return REVIEWED.keySet(); }
    public static boolean supported(Map<String, ClassNode> classes) {
        if (classes == null) return false;
        for (var expected : REVIEWED.entrySet()) {
            String key = expected.getKey(); int split = key.indexOf('#'), descriptor = key.indexOf('(', split);
            String owner = key.substring(0, split); ClassNode type = classes.get(owner);
            if (type == null || !owner.replace('.', '/').equals(type.name)
                    || !expected.getValue().equals(fingerprint(type, key.substring(split + 1, descriptor), key.substring(descriptor)))) return false;
        }
        return nativeSitesSupported(classes.get(LIVING));
    }

    public static String fingerprint(ClassNode type, String name, String descriptor) {
        if (type == null || type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).count() != 1) return "unreviewed";
        MethodNode method = method(type, name, descriptor);
        int access = Opcodes.ACC_PUBLIC | (type.name.equals(HOOKS.replace('.', '/')) ? Opcodes.ACC_STATIC : 0);
        if (method.access != access) return "unreviewed";
        return NativeScatterPlateMixinPlugin.fingerprint(type, name, descriptor);
    }

    /** Existing, independently reviewed damage adapters may already be merged; retain exact invocation counts. */
    public static boolean nativeSitesSupported(ClassNode type) {
        return type != null && LIVING.replace('.', '/').equals(type.name)
                && count(method(type, "hurt", HURT), type.name, "knockback", KNOCKBACK, Opcodes.INVOKEVIRTUAL) == 1
                && count(method(type, "knockback", KNOCKBACK), HOOKS.replace('.', '/'), "onLivingKnockBack", EVENT_DESC, Opcodes.INVOKESTATIC) == 1;
    }

    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        sitesPresent = admitted && LIVING.equals(target) && MIXIN.equals(mixin) && nativeSitesSupported(node);
        if (!sitesPresent) reviewedNativeTurnSupported = false;
    }

    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        // MixinExtras 0.5.3 applies WrapOperation and WrapMethod later in IExtension.postApply.
        // Merged handlers alone are not proof of installed call sites. Keep admission false here.
        merged = sitesPresent && LIVING.equals(target) && MIXIN.equals(mixin) && mergedCallbacksSupported(node);
        reviewedNativeTurnSupported = false;
    }

    /** Confirm both installed call-site wrappers and the merged read-only availability witness. */
    public static boolean installedCallbacksSupported(ClassNode type) {
        if (!mergedCallbacksSupported(type)) return false;
        MethodNode knockback = callback(type, "academy$vectorParryKnockback", "nativeKnockback");
        MethodNode event = callback(type, "academy$vectorParryEvent", "nativeEvent");
        MethodNode hurtGuard = callback(type, "academy$vectorParryHurt", "nativeHurt");
        // WrapMethod moves the native hurt body after the normal Mixin postApply phase. Its
        // moved body keeps HURT's descriptor; count the one actual wrapper invocation there too.
        long hurtCalls = type.methods.stream().filter(m -> m.desc.equals(HURT))
                .mapToLong(m -> count(m, type.name, knockback.name, knockback.desc, -1)).sum();
        long hurtGuardCalls = type.methods.stream().filter(m -> m.desc.equals(HURT))
                .mapToLong(m -> count(m, type.name, hurtGuard.name, hurtGuard.desc, -1)).sum();
        return hurtCalls == 1 && hurtGuardCalls == 1
                && count(method(type, "knockback", KNOCKBACK), type.name, event.name, event.desc, -1) == 1;
    }

    private static boolean mergedCallbacksSupported(ClassNode type) {
        if (type == null || !LIVING.replace('.', '/').equals(type.name) || !type.interfaces.contains(ACCESS)) return false;
        MethodNode access = method(type, "academy$vectorParryNativeSeam", "()Z");
        return callback(type, "academy$vectorParryKnockback", "nativeKnockback") != null
                && callback(type, "academy$vectorParryEvent", "nativeEvent") != null
                && callback(type, "academy$vectorParryHurt", "nativeHurt") != null
                && access != null && access.access == Opcodes.ACC_PUBLIC
                && count(access, "cn/academy/port/highpower/mixin/NativeVectorParryMixinPlugin",
                        "isReviewedNativeTurnSupported", "()Z", Opcodes.INVOKESTATIC) == 1;
    }

    private static MethodNode callback(ClassNode type, String suffix, String helper) {
        List<MethodNode> found = type.methods.stream().filter(m -> m.name.endsWith(suffix)).toList();
        if (found.size() != 1) return null;
        MethodNode method = found.getFirst();
        String args = helper.equals("nativeHurt") ? "Lnet/minecraft/world/damagesource/DamageSource;F"
                : ENTITY_ARG + (helper.equals("nativeKnockback") ? "DDD" : "FDD");
        String result = helper.equals("nativeHurt") ? "Z" : helper.equals("nativeKnockback") ? "V" : "Lnet/neoforged/neoforge/event/entity/living/LivingKnockBackEvent;";
        String callbackDescriptor = "(" + args + OPERATION + ")" + result;
        String helperDescriptor = "(" + (helper.equals("nativeHurt") ? ENTITY_ARG : "") + args + OPERATION + ")" + result;
        if (!method.desc.equals(callbackDescriptor)
                || (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != Opcodes.ACC_PRIVATE) return null;
        int calls = 0;
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(BRIDGE)) {
            if (!call.name.equals(helper) || !call.desc.equals(helperDescriptor) || call.getOpcode() != Opcodes.INVOKESTATIC) return null;
            calls++;
        }
        return calls == 1 ? method : null;
    }

    private static int count(MethodNode method, String owner, String name, String descriptor, int opcode) {
        if (method == null) return 0;
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)
                && (opcode < 0 || call.getOpcode() == opcode)) count++;
        return count;
    }
    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type == null ? null : type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static void warn(String message) { System.getLogger(NativeVectorParryMixinPlugin.class.getName()).log(System.Logger.Level.WARNING, message); }
    @Override public void onLoad(String mixinPackage) {
        reviewedNativeTurnSupported = false;
        try {
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                // onLoad is called during selectConfigs, before Extensions.select chooses active extensions.
                extensions.add(new FinalWitness());
                finalWitnessRegistered = true;
            }
        } catch (RuntimeException | LinkageError unavailable) {
            warn("Cannot register NEW412 final native witness; precision is unavailable: " + unavailable);
        }
    }

    /** This callback observes the final tree after every extension postApply; it does not export a file. */
    private final class FinalWitness implements IExtension {
        @Override public boolean checkActive(MixinEnvironment environment) { return true; }
        @Override public void preApply(ITargetClassContext context) {}
        @Override public void postApply(ITargetClassContext context) {}
        @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
            if (!LIVING.equals(name) && !LIVING.replace('.', '/').equals(name)) return;
            reviewedNativeTurnSupported = admitted && sitesPresent && merged && installedCallbacksSupported(node);
            if (!reviewedNativeTurnSupported) warn("NEW412 final native wrappers were not installed; precision is unavailable");
        }
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
