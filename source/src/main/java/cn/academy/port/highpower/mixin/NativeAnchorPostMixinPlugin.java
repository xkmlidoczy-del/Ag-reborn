package cn.academy.port.highpower.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.service.MixinService;

/** Optional, bytecode-only NEW422 Post admission. No other combat admission is changed. */
public final class NativeAnchorPostMixinPlugin implements IMixinConfigPlugin {
    private static final String LIVING = "net/minecraft/world/entity/LivingEntity";
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String HOOKS = "net/neoforged/neoforge/common/CommonHooks";
    private static final String CONTAINER = "net/neoforged/neoforge/common/damagesource/DamageContainer";
    private static final String POST = "net/neoforged/neoforge/event/entity/living/LivingDamageEvent$Post";
    private static final String BUS = "net/neoforged/bus/api/IEventBus";
    private static final String EVENT_BUS = "net/neoforged/bus/EventBus";
    private static final String DISPATCH = "(Lnet/neoforged/bus/api/Event;[Lnet/neoforged/bus/api/EventListener;)Lnet/neoforged/bus/api/Event;";
    private static final String PHASE_POST = "(Lnet/neoforged/bus/api/EventPriority;Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;";
    private static final String EVENT = "net/neoforged/bus/api/Event";
    private static final String PROOF = "cn/academy/port/highpower/AnchorDamagePostProof";
    private static final String MIXIN = "cn.academy.port.highpower.mixin.CommonHooksAnchorPostMixin";
    private static final String OP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    private static final String ARGS = "L" + LIVING + ";L" + CONTAINER + ";";
    private static final String POST_DESC = "(" + ARGS + ")V";
    private static final String BUS_DESC = "(L" + EVENT + ";)L" + EVENT + ";";
    private static final String METAFACTORY = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;";
    private static final String HURT = "(Lnet/minecraft/world/damagesource/DamageSource;F)V";
    private static final String NATIVE_CALLBACK = "(" + ARGS + OP + ")V";
    private static final String BUS_CALLBACK = "(L" + BUS + ";L" + EVENT + ";" + OP + ARGS + ")L" + EVENT + ";";
    private static volatile boolean reviewedPostSupported, failed;
    private boolean admitted, sitesPresent, merged, witnessRegistered, livingInstalled, playerInstalled, hooksInstalled;

    public static boolean isReviewedPostSupported() { return reviewedPostSupported && !failed; }
    public static void disableAfterCallbackFailure(Throwable failure) {
        failed = true;
        reviewedPostSupported = false;
        warn("NEW422 native Post observer failed; ordinary damage remains unchanged: " + failure);
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!HOOKS.replace('/', '.').equals(target) || !MIXIN.equals(mixin) || !witnessRegistered) return false;
        reviewedPostSupported = false;
        try {
            var provider = MixinService.getService().getBytecodeProvider();
            admitted = supported(Map.of(HOOKS.replace('/', '.'), provider.getClassNode(HOOKS.replace('/', '.')),
                    LIVING.replace('/', '.'), provider.getClassNode(LIVING.replace('/', '.')),
                    PLAYER.replace('/', '.'), provider.getClassNode(PLAYER.replace('/', '.')),
                    EVENT_BUS.replace('/', '.'), provider.getClassNode(EVENT_BUS.replace('/', '.'))));
        } catch (Exception | LinkageError unavailable) {
            admitted = false;
            warn("Cannot verify NEW422 native Post: " + unavailable);
        }
        if (!admitted) warn("Native Post differs from reviewed 1.21.1/21.1.252; NEW422 fall enhancement is unavailable");
        return admitted;
    }

    /** The nine native instructions below are the reviewed CommonHooks source, not a broad hash refresh. */
    public static boolean supported(Map<String, ClassNode> classes) {
        if (classes == null) return false;
        ClassNode living = classes.get(LIVING.replace('/', '.'));
        ClassNode player = classes.get(PLAYER.replace('/', '.'));
        return player != null && PLAYER.equals(player.name) && NativeCooldownMixinPlugin.containerDrivenDamage(player) && nativeDamageSiteSupported(player)
                && living != null && LIVING.equals(living.name) && NativeCooldownMixinPlugin.supportedEntity(living) && nativeDamageSiteSupported(living)
                && nativeSitesSupported(classes.get(HOOKS.replace('/', '.')))
                && busDispatchSupported(classes.get(EVENT_BUS.replace('/', '.')));
    }

    /** Reviewed bus 8.0.5: both public entry points dispatch through this one private loop. */
    public static boolean busDispatchSupported(ClassNode node) {
        if (node == null || !EVENT_BUS.equals(node.name)) return false;
        MethodNode entry = unique(node, "post", BUS_DESC), phase = unique(node, "post", PHASE_POST), dispatch = unique(node, "post", DISPATCH);
        if (entry == null || phase == null || dispatch == null || entry.access != Opcodes.ACC_PUBLIC || phase.access != Opcodes.ACC_PUBLIC
                || dispatch.access != Opcodes.ACC_PRIVATE || !entry.tryCatchBlocks.isEmpty() || !phase.tryCatchBlocks.isEmpty()
                || dispatch.tryCatchBlocks.size() != 1) return false;
        TryCatchBlockNode handler = dispatch.tryCatchBlocks.getFirst();
        if (!"java/lang/Throwable".equals(handler.type) || instructionIndex(dispatch, handler.start) != 2
                || instructionIndex(dispatch, handler.end) != 13 || instructionIndex(dispatch, handler.handler) != 14) return false;
        if (!"8ffc6272a2e003642d255f5492f5f0d84a3bc4bdf92667b5f0c80b7645b40046".equals(NativeScatterPlateMixinPlugin.fingerprint(node, "post", BUS_DESC))
                || !"9079cef4a579f98661b00e4164226518e3fbe1a24c6edfb623b343729c2bc3a9".equals(NativeScatterPlateMixinPlugin.fingerprint(node, "post", PHASE_POST))) return false;
        // The existing fingerprint helper rejects handlers. Pin its exact table above, then hash
        // a copy of only this newly reviewed method; no historical fingerprints are changed.
        MethodNode copied = new MethodNode(dispatch.access, dispatch.name, dispatch.desc, dispatch.signature, null);
        dispatch.accept(copied);
        copied.tryCatchBlocks.clear();
        ClassNode isolated = new ClassNode(); isolated.name = node.name; isolated.methods.add(copied);
        return "a3d90d3cc8b98b66df5ca86460e8eee42756cb34ff9c54426e5b37e0065b9710".equals(NativeScatterPlateMixinPlugin.fingerprint(isolated, "post", DISPATCH));
    }

    private static int instructionIndex(MethodNode method, AbstractInsnNode target) {
        int index = 0;
        for (AbstractInsnNode node : method.instructions) {
            if (node == target) return index;
            if (node.getOpcode() >= 0) index++;
        }
        return -1;
    }

    public static boolean nativeSitesSupported(ClassNode node) {
        if (node == null || !HOOKS.equals(node.name)) return false;
        MethodNode method = unique(node, "onLivingDamagePost", POST_DESC);
        if (method == null || method.access != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) || !method.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> code = code(method);
        return code.size() == 9 && eventConstruction(code)
                && invocation(code.get(6), Opcodes.INVOKEINTERFACE, BUS, "post", BUS_DESC, true)
                && opcode(code.get(7), Opcodes.POP) && opcode(code.get(8), Opcodes.RETURN);
    }

    private static boolean nativeDamageSiteSupported(ClassNode node) {
        MethodNode method = unique(node, "actuallyHurt", HURT);
        if (method == null || method.access != Opcodes.ACC_PROTECTED || count(method, HOOKS, "onLivingDamagePost", POST_DESC) != 1) return false;
        for (AbstractInsnNode instruction : method.instructions) if (invocation(instruction, Opcodes.INVOKESTATIC, HOOKS, "onLivingDamagePost", POST_DESC, false))
            return nativeDamageArguments(node, previous(instruction));
        return false;
    }

    private static boolean nativeDamageArguments(ClassNode node, AbstractInsnNode end) {
        if (!cast(end, CONTAINER)) return false;
        AbstractInsnNode peek = previous(end), stack = previous(peek), self = previous(stack), entity = previous(self);
        return invocation(peek, Opcodes.INVOKEVIRTUAL, "java/util/Stack", "peek", "()Ljava/lang/Object;", false)
                && stack instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD
                && (field.owner.equals(node.name) || field.owner.equals(LIVING)) && field.name.equals("damageContainers") && field.desc.equals("Ljava/util/Stack;")
                && variable(self, Opcodes.ALOAD, 0) && variable(entity, Opcodes.ALOAD, 0);
    }

    private static boolean eventConstruction(List<AbstractInsnNode> code) {
        return code.size() >= 6 && code.get(0) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                && field.owner.equals("net/neoforged/neoforge/common/NeoForge") && field.name.equals("EVENT_BUS") && field.desc.equals("L" + BUS + ";")
                && code.get(1) instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals(POST)
                && opcode(code.get(2), Opcodes.DUP) && variable(code.get(3), Opcodes.ALOAD, 0) && variable(code.get(4), Opcodes.ALOAD, 1)
                && invocation(code.get(5), Opcodes.INVOKESPECIAL, POST, "<init>", POST_DESC, false);
    }

    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        reviewedPostSupported = false;
        hooksInstalled = false;
        sitesPresent = admitted && MIXIN.equals(mixin) && nativeSitesSupported(node);
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        // MixinExtras installs call sites after this callback. A merged handler proves nothing yet.
        merged = sitesPresent && MIXIN.equals(mixin) && callback(node, false) != null;
        reviewedPostSupported = false;
    }

    /** Require installed callbacks AND their own invokedynamic delegates with one original call. */
    public static boolean installedCallbacksSupported(ClassNode node) {
        if (node == null || !(node.name.equals(LIVING) || node.name.equals(PLAYER) || node.name.equals(HOOKS))) return false;
        boolean living = !node.name.equals(HOOKS);
        MethodNode callback = callback(node, living);
        if (callback == null) return false;
        String hostName = living ? "actuallyHurt" : "onLivingDamagePost", hostDesc = living ? HURT : POST_DESC;
        MethodNode host = unique(node, hostName, hostDesc);
        if (host == null || host.access != (living ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)
                || !host.tryCatchBlocks.isEmpty() || count(host, node.name, callback.name, callback.desc) != 1) return false;
        MethodInsnNode call = null;
        for (AbstractInsnNode instruction : host.instructions) if (instruction instanceof MethodInsnNode candidate
                && candidate.owner.equals(node.name) && candidate.name.equals(callback.name) && candidate.desc.equals(callback.desc)) call = candidate;
        if (call == null || call.getOpcode() != (living ? Opcodes.INVOKESPECIAL : Opcodes.INVOKESTATIC)) return false;
        AbstractInsnNode previous = previous(call);
        if (!living) {
            if (!variable(previous, Opcodes.ALOAD, 1) || !variable(previous(previous), Opcodes.ALOAD, 0)) return false;
            previous = previous(previous(previous));
        }
        if (!(previous instanceof InvokeDynamicInsnNode dynamic) || !dynamic.name.equals("call") || !dynamic.desc.equals("()" + OP)
                || dynamic.bsm.getTag() != Opcodes.H_INVOKESTATIC || dynamic.bsm.isInterface()
                || !dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") || !dynamic.bsm.getName().equals("metafactory")
                || !dynamic.bsm.getDesc().equals(METAFACTORY) || dynamic.bsmArgs.length != 3
                || !Type.getMethodType("([Ljava/lang/Object;)Ljava/lang/Object;").equals(dynamic.bsmArgs[0])
                || !Type.getMethodType("([Ljava/lang/Object;)" + (living ? "Ljava/lang/Void;" : "L" + EVENT + ";")).equals(dynamic.bsmArgs[2])) return false;
        if (living) {
            // The installed wrapper still uses this entity and its current native damage container.
            AbstractInsnNode containerLoad = previous(dynamic), entityLoad = previous(containerLoad), self = previous(entityLoad);
            AbstractInsnNode entityStore = previous(self), containerStore = previous(entityStore);
            if (!(entityStore instanceof VarInsnNode storedEntity) || storedEntity.getOpcode() != Opcodes.ASTORE || storedEntity.var < 3
                    || !(containerStore instanceof VarInsnNode storedContainer) || storedContainer.getOpcode() != Opcodes.ASTORE || storedContainer.var < 3
                    || storedEntity.var == storedContainer.var || !variable(self, Opcodes.ALOAD, 0)
                    || !variable(entityLoad, Opcodes.ALOAD, storedEntity.var) || !variable(containerLoad, Opcodes.ALOAD, storedContainer.var)
                    || !nativeDamageArguments(node, previous(containerStore))) return false;
        }
        Handle delegate = null;
        for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle) {
            if (delegate != null) return false;
            delegate = handle;
        }
        if (delegate == null || delegate.getTag() != Opcodes.H_INVOKESTATIC || delegate.isInterface() || !delegate.getOwner().equals(node.name)) return false;
        MethodNode operation = unique(node, delegate.getName(), delegate.getDesc());
        if (!delegateSupported(operation, living)) return false;
        if (count(host, living ? HOOKS : BUS, living ? "onLivingDamagePost" : "post", living ? POST_DESC : BUS_DESC) != 0) return false;
        if (!living) {
            // Apart from generated argument spills and the callback, the native hook is still exact.
            List<AbstractInsnNode> code = code(host);
            if (code.size() != 16 || !eventConstruction(code)
                    || !(code.get(6) instanceof VarInsnNode eventStore) || eventStore.getOpcode() != Opcodes.ASTORE || eventStore.var < 2
                    || !(code.get(7) instanceof VarInsnNode busStore) || busStore.getOpcode() != Opcodes.ASTORE || busStore.var < 2 || eventStore.var == busStore.var
                    || !variable(code.get(8), Opcodes.ALOAD, busStore.var) || !variable(code.get(9), Opcodes.ALOAD, eventStore.var)
                    || code.get(10) != dynamic || code.get(13) != call || !opcode(code.get(14), Opcodes.POP) || !opcode(code.get(15), Opcodes.RETURN)) return false;
        }
        return true;
    }

    private static MethodNode callback(ClassNode node, boolean living) {
        String suffix = living ? "anchor$nativePost" : "anchor$nativeBusPost", descriptor = living ? NATIVE_CALLBACK : BUS_CALLBACK;
        List<MethodNode> matches = node.methods.stream().filter(m -> m.name.endsWith(suffix)).toList();
        if (matches.size() != 1) return null;
        MethodNode method = matches.getFirst();
        if (!method.desc.equals(descriptor) || method.access != (Opcodes.ACC_PRIVATE | (living ? 0 : Opcodes.ACC_STATIC)) || !method.tryCatchBlocks.isEmpty()) return null;
        List<AbstractInsnNode> code = code(method);
        int arguments = living ? 3 : 5, first = living ? 1 : 0;
        if (code.size() != arguments + 2) return null;
        for (int i = 0; i < arguments; i++) if (!variable(code.get(i), Opcodes.ALOAD, first + i)) return null;
        return invocation(code.get(arguments), Opcodes.INVOKESTATIC, PROOF, living ? "nativePost" : "nativeBusPost", descriptor, false)
                && opcode(code.get(arguments + 1), living ? Opcodes.RETURN : Opcodes.ARETURN) ? method : null;
    }

    /** Pin MixinExtras 0.5.3's two-reference argument unpacking; branches/extra calls are refused. */
    private static boolean delegateSupported(MethodNode method, boolean living) {
        if (method == null || method.access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)
                || !method.desc.equals("([Ljava/lang/Object;)" + (living ? "Ljava/lang/Void;" : "L" + EVENT + ";")) || !method.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> code = code(method);
        if (code.size() != (living ? 20 : 18)) return false;
        String first = living ? LIVING : BUS, second = living ? CONTAINER : EVENT;
        String arguments = "[" + first.replace('/', '.') + ", " + second.replace('/', '.') + "]";
        if (!variable(code.get(0), Opcodes.ALOAD, 0) || !integer(code.get(1), 2)
                || !(code.get(2) instanceof LdcInsnNode text) || !arguments.equals(text.cst)
                || !invocation(code.get(3), Opcodes.INVOKESTATIC, "com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime", "checkArgumentCount", "([Ljava/lang/Object;ILjava/lang/String;)V", false)
                || !variable(code.get(4), Opcodes.ALOAD, 0)) return false;
        for (int i = 0; i < 2; i++) {
            int start = 5 + i * 5;
            if (!opcode(code.get(start), Opcodes.DUP) || !integer(code.get(start + 1), i) || !opcode(code.get(start + 2), Opcodes.AALOAD)
                    || !cast(code.get(start + 3), i == 0 ? first : second) || !opcode(code.get(start + 4), Opcodes.SWAP)) return false;
        }
        return opcode(code.get(15), Opcodes.POP)
                && invocation(code.get(16), living ? Opcodes.INVOKESTATIC : Opcodes.INVOKEINTERFACE, living ? HOOKS : BUS,
                        living ? "onLivingDamagePost" : "post", living ? POST_DESC : BUS_DESC, !living)
                && (living ? opcode(code.get(17), Opcodes.ACONST_NULL) && cast(code.get(18), "java/lang/Void") && opcode(code.get(19), Opcodes.ARETURN)
                           : opcode(code.get(17), Opcodes.ARETURN));
    }

    private static boolean integer(AbstractInsnNode node, int value) { return node instanceof IntInsnNode number && number.getOpcode() == Opcodes.BIPUSH && number.operand == value; }
    private static boolean cast(AbstractInsnNode node, String type) { return node instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST && cast.desc.equals(type); }
    private static boolean opcode(AbstractInsnNode node, int opcode) { return node != null && node.getOpcode() == opcode; }
    private static boolean variable(AbstractInsnNode node, int opcode, int local) { return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == local; }
    private static boolean invocation(AbstractInsnNode node, int opcode, String owner, String name, String descriptor, boolean itf) {
        return node instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor) && call.itf == itf;
    }
    private static int count(MethodNode method, String owner, String name, String descriptor) {
        int count = 0;
        for (AbstractInsnNode node : method.instructions) if (node instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor)) count++;
        return count;
    }
    private static MethodNode unique(ClassNode node, String name, String descriptor) {
        List<MethodNode> methods = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }
    private static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode node : method.instructions) if (node.getOpcode() >= 0) code.add(node);
        return code;
    }
    private static AbstractInsnNode previous(AbstractInsnNode node) {
        if (node == null) return null;
        do { node = node.getPrevious(); } while (node != null && node.getOpcode() < 0);
        return node;
    }
    private static void warn(String message) { System.getLogger(NativeAnchorPostMixinPlugin.class.getName()).log(System.Logger.Level.WARNING, message); }
    @Override public void onLoad(String mixinPackage) {
        reviewedPostSupported = false;
        failed = false;
        try {
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(new FinalWitness());
                witnessRegistered = true;
            }
        } catch (RuntimeException | LinkageError unavailable) { warn("Cannot register NEW422 final Post witness: " + unavailable); }
    }
    private final class FinalWitness implements IExtension {
        @Override public boolean checkActive(MixinEnvironment environment) { return true; }
        @Override public void preApply(ITargetClassContext context) {}
        @Override public void postApply(ITargetClassContext context) {}
        @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
            if (node.name.equals(LIVING)) livingInstalled = installedCallbacksSupported(node);
            else if (node.name.equals(PLAYER)) playerInstalled = installedCallbacksSupported(node);
            else if (node.name.equals(HOOKS)) hooksInstalled = sitesPresent && merged && installedCallbacksSupported(node);
            else if (node.name.equals(EVENT_BUS)) { if (!busDispatchSupported(node)) failed = true; }
            else return;
            reviewedPostSupported = admitted && livingInstalled && playerInstalled && hooksInstalled && !failed;
        }
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
}
