package cn.academy.port.highpower.client;

import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.ClassReader;
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

/** Final read-only proof for the two client-only NEW443 transformations. */
public final class WindPressureLiftTravelMixinPlugin implements IMixinConfigPlugin {
    public static final String RUNTIME = "cn/academy/port/client/WindPressureLiftNativeTravel";
    public static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    public static final String RUN_HANDLER_DESC = "(Z" + OPERATION + ")V";
    public static final String PLAN_HANDLER_DESC = "(L" + TIMER + ";JZ)I";
    public static final String STEP_HANDLER_DESC = "(L" + MINECRAFT + ";)V";
    public static final String BRIDGE_DESC = "([Ljava/lang/Object;)Ljava/lang/Void;";
    public static final String TICKET_DESC = "Lcn/academy/port/client/WindPressureLiftTravelState$Travel;";
    private static final String ACCESSOR = WindPressureLiftAutoJumpMixinPlugin.ACCESSOR;
    private static final String GETTER = WindPressureLiftAutoJumpMixinPlugin.GETTER;
    private static final String PACKAGE = "cn.academy.port.client.highpower.mixin.";
    private static final Map<String, String> MIXINS = Map.of(MINECRAFT, PACKAGE + "WindPressureLiftMinecraftMixin",
            LOCAL_PLAYER, PACKAGE + "WindPressureLiftTravelMixin");
    private static final Handle METAFACTORY = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);
    private static volatile MinecraftShape installedShape;
    private static volatile boolean installedSablePlainState;
    private static volatile Set<String> requiredFinalClasses = Set.of();
    private final Map<String, ClassNode> originals = new HashMap<>();
    private final Map<String, ClassNode> observedFinals = new HashMap<>();
    private final Set<String> prepared = new HashSet<>();
    private final Set<String> finalRequired = new HashSet<>(MIXINS.keySet());
    private boolean registered, checked, admitted, targetInventoryObserved;
    private boolean externalChecked, externalAdmitted;
    private String externalFailure="not_checked";
    private static final boolean QA=Boolean.getBoolean("academy.qa.windLiftTrace");
    private static volatile String qaState="not_loaded";
    private int qaReports;

    /** Names are the exact methods whose final bodies passed, never prefix-based stack skips. */
    public record MinecraftShape(String runHandler, String planHandler, String stepHandler, String nativeBody, String bridge) {}
    public static boolean available() { return installedShape != null; }
    public static MinecraftShape minecraftShape() { return installedShape; }
    public static boolean requiresSablePlainState() { return installedSablePlainState; }
    /** Relevant Mixin targets from the configuration inventory, for truthful export diagnostics. */
    public static Set<String> requiredFinalClasses() { return requiredFinalClasses; }
    /** QA snapshot only; it never substitutes for the live availability predicate. */
    public static String diagnosticState() { return QA ? qaState : "disabled"; }

    @Override public void onLoad(String mixinPackage) {
        installedShape = null;
        installedSablePlainState = false;
        requiredFinalClasses = Set.of();
        if(QA)qaState="loaded_not_observed";
        try {
            Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if (transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(new FinalWitness()); registered = true;
            }
        } catch (RuntimeException | LinkageError unavailable) {
            registered=false;qa("registration_exception type="+unavailable.getClass().getName());
        }
        if(!registered)qa("registration_unavailable");
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        String owner = target.replace('.', '/');
        if (!registered || !mixin.equals(MIXINS.get(owner))) return false;
        if (!checked) {
            checked = true;
            String reading="none";
            try {
                boolean providersSupported=true;
                for (String required : requiredClasses()) {
                    reading=required;
                    // The provider has already run RuntimeDistCleaner. Keep raw bytes as the
                    // unchanged oracle, then separately prove the provider's exact known delta.
                    ClassNode raw;
                    try(var stream=MixinService.getService().getResourceAsStream(required+".class")) {
                        if(stream==null)throw new java.io.IOException("missing native resource");
                        byte[] bytes=stream.readNBytes(4*1024*1024+1);
                        if(bytes.length>4*1024*1024)throw new java.io.IOException("native resource size limit");
                        raw=new ClassNode();new ClassReader(bytes).accept(raw,0);
                    }
                    originals.put(required,raw);
                    ClassNode provider=MixinService.getService().getBytecodeProvider().getClassNode(required.replace('/', '.'));
                    boolean equivalent=providerSupported(raw,provider);
                    providersSupported&=equivalent;
                    if(QA && (!equivalent || !classFingerprint(raw).equals(classFingerprint(provider))))
                        qa("raw_class_checked class="+required+" equivalent="+equivalent
                                +" expected="+expectedClassFingerprint(required)+" resource="+classFingerprint(raw)
                                +" provider="+classFingerprint(provider));
                }
                admitted = providersSupported && supported(originals);
                qa("raw_admission_"+(admitted?"accepted":"refused")+" classes="+originals.size());
            } catch (Exception | LinkageError unavailable) {
                admitted=false;qa("provider_or_resource_exception class="+reading+" type="+unavailable.getClass().getName());
            }
        }
        return admitted;
    }

    /** Exact loader-only deltas. Native bodies, descriptors, interfaces and unrelated metadata remain pinned. */
    private static int visibility(int access) {
        return (access & Opcodes.ACC_PUBLIC) != 0 ? 3 : (access & Opcodes.ACC_PROTECTED) != 0 ? 2 : (access & Opcodes.ACC_PRIVATE) != 0 ? 0 : 1;
    }
    /** Provider access equals expected, or is wider: higher visibility and/or final removed; every other flag unchanged. */
    private static boolean widened(int expected, int provider) {
        int vis = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE;
        if ((provider & ~(vis | Opcodes.ACC_FINAL)) != (expected & ~(vis | Opcodes.ACC_FINAL))) return false;
        if ((provider & Opcodes.ACC_FINAL) != 0 && (expected & Opcodes.ACC_FINAL) == 0) return false;
        return visibility(provider) >= visibility(expected);
    }
    static boolean accessWideningOnly(ClassNode expected, ClassNode provider) {
        if (expected == null || provider == null || !widened(expected.access, provider.access)) return false;
        ClassNode restored = copy(provider); restored.access = expected.access;
        if (restored.fields.size() != expected.fields.size() || restored.methods.size() != expected.methods.size()) return false;
        for (int i = 0; i < restored.fields.size(); i++) {
            FieldNode now = restored.fields.get(i), was = expected.fields.get(i);
            if (!now.name.equals(was.name) || !now.desc.equals(was.desc) || !widened(was.access, now.access)) return false;
            now.access = was.access;
        }
        for (int i = 0; i < restored.methods.size(); i++) {
            MethodNode now = restored.methods.get(i), was = expected.methods.get(i);
            if (!now.name.equals(was.name) || !now.desc.equals(was.desc) || !widened(was.access, now.access)) return false;
            now.access = was.access;
        }
        return classFingerprint(restored).equals(classFingerprint(expected));
    }
    public static boolean providerSupported(ClassNode raw,ClassNode provider) {
        if(!classSupported(raw) || provider==null || !raw.name.equals(provider.name))return false;
        String actual=classFingerprint(provider);
        if(classFingerprint(raw).equals(actual))return true;
        ClassNode expected=copy(raw);
        boolean removed=false;
        for(var iterator=expected.visibleAnnotations==null?java.util.Collections.<AnnotationNode>emptyIterator():expected.visibleAnnotations.iterator();iterator.hasNext();) {
            AnnotationNode annotation=iterator.next();
            if(!"Lnet/neoforged/api/distmarker/OnlyIn;".equals(annotation.desc))continue;
            if(annotation.values==null || annotation.values.size()!=2 || !"value".equals(annotation.values.get(0))
                    || !(annotation.values.get(1) instanceof String[] value) || value.length!=2
                    || !"Lnet/neoforged/api/distmarker/Dist;".equals(value[0]) || !"CLIENT".equals(value[1]))return false;
            iterator.remove();removed=true;
        }
        if (removed && classFingerprint(expected).equals(actual)) return true;
        // Other mods' access transformers (libraries, renderers) only widen visibility or drop final. Accept exactly that:
        // undo pure widenings on a copy of the provider and require the result to equal the cleaned raw class byte for byte.
        if (accessWideningOnly(expected, provider)) return true;
        // Sable 2.0.6's actual accesstransformer.cfg has precisely these two raw-class deltas.
        // Reconstruct the entire expected class, rather than accepting a mod name or an observed hash alone.
        // This only admits the provider into inspection: final Mixin bodies and native stack still need proof.
        if (!Set.of(ENTITY,LIVING_ENTITY).contains(raw.name)) return false;
        int changes=0;
        if (ENTITY.equals(raw.name)) {
            for (FieldNode field:expected.fields) if (field.name.equals("removalReason")
                    && field.desc.equals("Lnet/minecraft/world/entity/Entity$RemovalReason;")
                    || field.name.equals("levelCallback") && field.desc.equals("Lnet/minecraft/world/level/entity/EntityInLevelCallback;")) {
                field.access=(field.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC; changes++;
            }
            for (MethodNode method:expected.methods) if (method.name.equals("getEyePosition")
                    && method.desc.equals("(F)Lnet/minecraft/world/phys/Vec3;")
                    || method.name.equals("setRemoved") && method.desc.equals("(Lnet/minecraft/world/entity/Entity$RemovalReason;)V")) {
                method.access=(method.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED|Opcodes.ACC_FINAL))|Opcodes.ACC_PUBLIC; changes++;
            }
        } else for (MethodNode method:expected.methods) if (method.name.equals("dismountVehicle")
                && method.desc.equals("(Lnet/minecraft/world/entity/Entity;)V")) {
            method.access=(method.access&~(Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PUBLIC; changes++;
        }
        return changes==(ENTITY.equals(raw.name)?4:1) && classFingerprint(expected).equals(actual);
    }
    private void qa(String message) {
        if(!QA || qaReports>=16)return;
        qaReports++;
        try {System.getLogger("academy.qa.wind_pressure_lift").log(System.Logger.Level.INFO,
                "NEW443_QA stage=travel_admission "+message);}
        catch(RuntimeException | LinkageError unavailable) { /* diagnostic only */ }
    }

    @Override public void acceptTargets(Set<String> mine, Set<String> others) {
        // A complete configuration target inventory distinguishes actual final observations
        // from the independent RAW dependency pins. No untargeted class is called exported.
        targetInventoryObserved = true;
        for (String target : mine) if (requiredClasses().contains(target.replace('.', '/')) || WindPressureLiftSableProjection.externalOwners().contains(target.replace('.', '/'))) finalRequired.add(target.replace('.', '/'));
        for (String target : others) if (requiredClasses().contains(target.replace('.', '/')) || WindPressureLiftSableProjection.externalOwners().contains(target.replace('.', '/'))) finalRequired.add(target.replace('.', '/'));
        requiredFinalClasses = Set.copyOf(finalRequired);
        refresh();
    }

    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        String owner = target.replace('.', '/');
        installedShape = null;
        prepared.remove(owner);
        if (admitted && mixin.equals(MIXINS.get(owner)) && node != null && owner.equals(node.name)
                && preTargetSupported(originals.get(owner), node)) prepared.add(owner);
        if(QA)qa("pre_apply class="+owner+" prepared="+prepared.contains(owner)
                +" signature="+(node==null?"missing":node.signature)
                +" getter="+(node!=null && method(node,GETTER,"()I")!=null)
                +" accessor="+(node!=null && node.interfaces.contains(ACCESSOR)));
    }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        installedShape = null; // MixinExtras has not yet completed its transformation.
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }

    private final class FinalWitness implements IExtension {
        public boolean checkActive(MixinEnvironment environment) { return true; }
        public void preApply(ITargetClassContext context) {}
        public void postApply(ITargetClassContext context) {}
        public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
            observeFinal(name.replace('.', '/'), node);
        }
    }

    private void observeFinal(String owner, ClassNode node) {
        if (!requiredClasses().contains(owner) && !WindPressureLiftSableProjection.externalOwners().contains(owner)) return;
        if (node == null || !owner.equals(node.name)) observedFinals.remove(owner);
        else {
            observedFinals.put(owner, copy(node));
            String dump=System.getProperty("academy.qa.windLiftLiveDump", "");
            if(QA&&!dump.isBlank())try {
                // Diagnostic snapshot before ClassWriter computes MixinExtras' unset maxima.
                // These files are proof inputs only and are never loaded as game classes.
                var path=java.nio.file.Path.of(dump).resolve(owner+".class");
                java.nio.file.Files.createDirectories(path.getParent());
                var writer=new org.objectweb.asm.ClassWriter(0);node.accept(writer);
                java.nio.file.Files.write(path,writer.toByteArray());
            }catch(java.io.IOException|RuntimeException failure){qa("live_dump_failed class="+owner+" reason="+failure.getClass().getSimpleName());}
        }
        refresh();
    }
    private void refresh() {
        installedShape = null;
        installedSablePlainState = false;
        if (!admitted || !targetInventoryObserved || !prepared.containsAll(MIXINS.keySet())
                || !observedFinals.keySet().containsAll(finalRequired)) { qaState("incomplete");return; }
        boolean sable=observedFinals.values().stream().anyMatch(WindPressureLiftSableProjection::hasWeave);
        if(sable && !externalApisSupported()){qaState("sable_external_refused:"+externalFailure);return;}
        MinecraftShape shape = inspectMinecraft(originals.get(MINECRAFT), observedFinals.get(MINECRAFT),true);
        if (shape == null) {qaState("minecraft_final_refused");return;}
        if (!localPlayerTransformationSupported(originals.get(LOCAL_PLAYER), observedFinals.get(LOCAL_PLAYER))) {
            qaState("local_player_final_refused");return;
        }
        for (String owner : finalRequired) if (!MIXINS.containsKey(owner)) {
            if(WindPressureLiftSableProjection.externalOwners().contains(owner)) {
                if(!WindPressureLiftSableProjection.externalSupported(observedFinals.get(owner))){qaState("sable_api_final_refused:"+owner);return;}
            }else if(!finalDependencySupported(originals.get(owner),observedFinals.get(owner))){qaState("dependency_final_refused:"+owner);return;}
        }
        installedShape = shape;
        installedSablePlainState=sable;
        qaState("accepted");
    }
    private boolean externalApisSupported(){
        if(externalChecked)return externalAdmitted;
        externalChecked=true;
        if(WindPressureLiftSableProjection.externalOwners().size()!=14){externalFailure="profile_missing";return false;}
        for(String owner:WindPressureLiftSableProjection.externalOwners())try{
            ClassNode raw;
            try(var in=MixinService.getService().getResourceAsStream(owner+".class")){
                if(in==null){externalFailure="resource_missing:"+owner;return false;}
                raw=new ClassNode();new ClassReader(in).accept(raw,0);
            }
            ClassNode provider=MixinService.getService().getBytecodeProvider().getClassNode(owner.replace('/','.'));
            if(!WindPressureLiftSableProjection.externalSupported(raw) || !classFingerprint(raw).equals(classFingerprint(provider))){
                externalFailure=owner;qa("sable_api_refused class="+owner+" raw="+classFingerprint(raw)+" provider="+classFingerprint(provider));return false;
            }
        }catch(Exception | LinkageError failure){externalFailure=owner+":"+failure.getClass().getSimpleName();return false;}
        externalAdmitted=true;externalFailure="";return true;
    }
    private void qaState(String stage) {
        if(!QA)return;
        var missingPrepared=new java.util.TreeSet<>(MIXINS.keySet());missingPrepared.removeAll(prepared);
        var missingFinal=new java.util.TreeSet<>(finalRequired);missingFinal.removeAll(observedFinals.keySet());
        qaState="stage="+stage+" registered="+registered+" admitted="+admitted
                +" targetInventory="+targetInventoryObserved+" missingPrepared="+missingPrepared
                +" finalRequired="+new java.util.TreeSet<>(finalRequired)+" missingFinal="+missingFinal;
        ClassNode minecraft=observedFinals.get(MINECRAFT),local=observedFinals.get(LOCAL_PLAYER);
        MethodNode body=minecraft==null?null:generated(minecraft,"runTick\\$mixinextras\\$wrapped\\$[0-9]+",RUN_TICK_DESC);
        qaState+=" finalNativeBodyMax="+(body==null?"missing":body.maxStack+"/"+body.maxLocals)
                +" finalLocalSignature="+(local==null?"missing":local.signature);
    }

    /** The full native classes are raw-pinned; final dependency methods must remain exact. */
    public static boolean finalDependencySupported(ClassNode raw, ClassNode transformed) {
        transformed=WindPressureLiftSableProjection.project(raw,transformed);
        if (!classSupported(raw) || transformed == null || !raw.name.equals(transformed.name)
                || raw.access != transformed.access || !Objects.equals(raw.superName, transformed.superName)) return false;
        if (ABSTRACT_CLIENT_PLAYER.equals(raw.name) && method(transformed, TRAVEL, TRAVEL_DESC) != null) return false;
        if (Set.of(LOCAL_PLAYER, ABSTRACT_CLIENT_PLAYER, PLAYER, LIVING_ENTITY).contains(raw.name)
                && !inheritsPlainStateAccess(transformed)) return false;
        for (String key : reviewedMethods()) if (key.startsWith(raw.name + "#")) {
            int descriptor = key.indexOf('(');
            if (!nativeMethodSupported(transformed, key.substring(raw.name.length() + 1, descriptor), key.substring(descriptor))) return false;
        }
        return true;
    }

    private static boolean preTargetSupported(ClassNode raw, ClassNode target) {
        if (!classSupported(raw)) return false;
        target=WindPressureLiftSableProjection.project(raw,target);if(target==null)return false;
        if (MINECRAFT.equals(raw.name)) return sameLayout(raw, target, Set.of()) && sameMethods(raw, target, Set.of(), Set.of());
        if (!LOCAL_PLAYER.equals(raw.name) || method(target, TRAVEL, TRAVEL_DESC) != null) return false;
        boolean getter = method(target, GETTER, "()I") != null;
        return sameLayout(raw, target, getter ? Set.of(ACCESSOR) : Set.of())
                && (!getter || WindPressureLiftAutoJumpMixinPlugin.getterSupported(target))
                && sameMethods(raw, target, Set.of(), getter ? Set.of(GETTER + "()I") : Set.of());
    }

    public static MinecraftShape inspectMinecraft(ClassNode raw, ClassNode transformed) {
        return inspectMinecraft(raw,transformed,false);
    }
    private static MinecraftShape inspectMinecraft(ClassNode raw,ClassNode transformed,boolean liveTree) {
        transformed=WindPressureLiftSableProjection.project(raw,transformed);
        if (!classSupported(raw) || !MINECRAFT.equals(raw.name) || transformed == null || !sameLayout(raw, transformed, Set.of())) return null;
        MethodNode run = generated(transformed, "wrapMethod\\$[a-z0-9]+\\$academy\\$windLiftRun", RUN_HANDLER_DESC);
        MethodNode plan = generated(transformed, "redirect\\$[a-z0-9]+\\$academy\\$windLiftPlan", PLAN_HANDLER_DESC);
        MethodNode step = generated(transformed, "redirect\\$[a-z0-9]+\\$academy\\$windLiftStep", STEP_HANDLER_DESC);
        MethodNode body = generated(transformed, "runTick\\$mixinextras\\$wrapped\\$[0-9]+", RUN_TICK_DESC);
        if (run == null || plan == null || step == null || body == null || !handler(run, "run") || !handler(plan, "plan") || !handler(step, "step")) return null;
        MethodNode bridge = generated(transformed, "mixinextras\\$bridge\\$" + java.util.regex.Pattern.quote(body.name) + "\\$[0-9]+", BRIDGE_DESC);
        if (bridge == null || !bridgeSupported(bridge, body.name) || !outerSupported(method(transformed, RUN_TICK, RUN_TICK_DESC), run.name, bridge.name)
                || !nativeBodySupported(raw, body, plan.name, step.name,liveTree)) return null;
        Set<String> additions = Set.of(run.name + run.desc, plan.name + plan.desc, step.name + step.desc, body.name + body.desc, bridge.name + bridge.desc);
        if (!sameMethods(raw, transformed, Set.of(RUN_TICK + RUN_TICK_DESC), additions)) return null;
        return new MinecraftShape(run.name, plan.name, step.name, body.name, bridge.name);
    }

    private static boolean handler(MethodNode method, String helper) {
        List<AbstractInsnNode> code = operations(method);
        int access = helper.equals("run") ? Opcodes.ACC_PRIVATE : Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        if (method.access != access || !method.tryCatchBlocks.isEmpty()) return false;
        if (helper.equals("run")) return code.size() == 5 && var(code.get(0), Opcodes.ALOAD, 0) && var(code.get(1), Opcodes.ILOAD, 1)
                && var(code.get(2), Opcodes.ALOAD, 2) && invocation(code.get(3), Opcodes.INVOKESTATIC, RUNTIME, helper,
                "(L" + MINECRAFT + ";Z" + OPERATION + ")V") && opcode(code.get(4), Opcodes.RETURN);
        if (helper.equals("plan")) return code.size() == 5 && var(code.get(0), Opcodes.ALOAD, 0) && var(code.get(1), Opcodes.LLOAD, 1)
                && var(code.get(2), Opcodes.ILOAD, 3) && invocation(code.get(3), Opcodes.INVOKESTATIC, RUNTIME, helper, PLAN_HANDLER_DESC)
                && opcode(code.get(4), Opcodes.IRETURN);
        return code.size() == 3 && var(code.get(0), Opcodes.ALOAD, 0) && invocation(code.get(1), Opcodes.INVOKESTATIC, RUNTIME, helper, STEP_HANDLER_DESC)
                && opcode(code.get(2), Opcodes.RETURN);
    }

    private static boolean outerSupported(MethodNode method, String handler, String bridge) {
        if (method == null || method.access != Opcodes.ACC_PRIVATE || !method.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> code = operations(method);
        return code.size() == 6 && var(code.get(0), Opcodes.ALOAD, 0) && var(code.get(1), Opcodes.ILOAD, 1)
                && var(code.get(2), Opcodes.ALOAD, 0) && code.get(3) instanceof InvokeDynamicInsnNode dynamic
                && dynamic.name.equals("call") && dynamic.desc.equals("(L" + MINECRAFT + ";)" + OPERATION)
                && dynamic.bsm.equals(METAFACTORY) && dynamic.bsmArgs.length == 3
                && dynamic.bsmArgs[0].equals(Type.getMethodType("([Ljava/lang/Object;)Ljava/lang/Object;"))
                && dynamic.bsmArgs[1].equals(new Handle(Opcodes.H_INVOKESPECIAL, MINECRAFT, bridge, BRIDGE_DESC, false))
                && dynamic.bsmArgs[2].equals(Type.getMethodType(BRIDGE_DESC))
                && invocation(code.get(4), Opcodes.INVOKESPECIAL, MINECRAFT, handler, RUN_HANDLER_DESC) && opcode(code.get(5), Opcodes.RETURN);
    }

    private static boolean bridgeSupported(MethodNode method, String body) {
        if (method.access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC) || !method.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> code = operations(method);
        return code.size() == 17 && var(code.get(0), Opcodes.ALOAD, 1) && integer(code.get(1), 1)
                && code.get(2) instanceof LdcInsnNode names && "[boolean]".equals(names.cst)
                && invocation(code.get(3), Opcodes.INVOKESTATIC, "com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime",
                "checkArgumentCount", "([Ljava/lang/Object;ILjava/lang/String;)V")
                && var(code.get(4), Opcodes.ALOAD, 0) && var(code.get(5), Opcodes.ALOAD, 1) && opcode(code.get(6), Opcodes.DUP)
                && integer(code.get(7), 0) && opcode(code.get(8), Opcodes.AALOAD) && type(code.get(9), Opcodes.CHECKCAST, "java/lang/Boolean")
                && invocation(code.get(10), Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z")
                && opcode(code.get(11), Opcodes.SWAP) && opcode(code.get(12), Opcodes.POP)
                && invocation(code.get(13), Opcodes.INVOKESPECIAL, MINECRAFT, body, RUN_TICK_DESC)
                && opcode(code.get(14), Opcodes.ACONST_NULL) && type(code.get(15), Opcodes.CHECKCAST, "java/lang/Void") && opcode(code.get(16), Opcodes.ARETURN);
    }

    private static boolean nativeBodySupported(ClassNode raw, MethodNode body, String plan, String step,boolean liveTree) {
        MethodNode original=method(raw,RUN_TICK,RUN_TICK_DESC);
        // MixinExtras 0.5.3 WrapMethodStage.move leaves these two fields unset in its
        // in-memory moved method (confirmed in r10). Serialized exports must still
        // have the exact computed maxima. Only this private live-tree path accepts 0/0.
        boolean unsetMovedMaxima=liveTree && body.maxStack==0 && body.maxLocals==0;
        if(original==null || body.access != Opcodes.ACC_PRIVATE) return false;
        int base=original.maxLocals;
        MethodNode normalized = copy(body);
        int foreign=stripForeignCallbacks(normalized,base);
        if(foreign<0)return false;
        if(!unsetMovedMaxima && (body.maxStack<original.maxStack || foreign==0 && body.maxStack!=original.maxStack
                || foreign==0 && body.maxLocals!=original.maxLocals+4 || foreign>0 && body.maxLocals<original.maxLocals+4)) return false;
        if(unsetMovedMaxima)normalized.maxStack=original.maxStack;
        normalized.name = RUN_TICK;
        MethodInsnNode planCall=null,stepCall=null;
        for (AbstractInsnNode instruction : normalized.instructions.toArray()) if (instruction instanceof MethodInsnNode call && call.owner.equals(MINECRAFT)) {
            if (call.name.equals(plan)) {
                if (planCall!=null || !invocation(call, Opcodes.INVOKESTATIC, MINECRAFT, plan, PLAN_HANDLER_DESC)) return false;
                planCall=call;
            } else if (call.name.equals(step)) {
                if (stepCall!=null || !invocation(call, Opcodes.INVOKESTATIC, MINECRAFT, step, STEP_HANDLER_DESC)) return false;
                stepCall=call;
            }
        }
        // Exact static-Redirect argument spills observed in the real r8 export. Fresh slots
        // [base,base+3] cannot alias the frozen native locals; the long occupies two slots.
        var planSpills=redirectSpills(normalized,planCall,
                new int[]{Opcodes.ISTORE,Opcodes.LSTORE,Opcodes.ASTORE,Opcodes.ALOAD,Opcodes.LLOAD,Opcodes.ILOAD},
                new int[]{base+3,base+1,base,base,base+1,base+3});
        var stepSpills=redirectSpills(normalized,stepCall,new int[]{Opcodes.ASTORE,Opcodes.ALOAD},new int[]{base,base});
        if(planSpills==null || stepSpills==null)return false;
        Set<AbstractInsnNode> spills=new HashSet<>(planSpills);spills.addAll(stepSpills);
        if(spills.size()!=8)return false;
        for(var instruction:normalized.instructions) {
            if(instruction instanceof VarInsnNode variable) {
                int width=switch(variable.getOpcode()) {case Opcodes.LLOAD,Opcodes.LSTORE,Opcodes.DLOAD,Opcodes.DSTORE -> 2;default -> 1;};
                if(variable.var<0 || variable.var+width>base && !spills.contains(instruction))return false;
            } else if(instruction instanceof IincInsnNode increment && (increment.var<0 || increment.var>=base))return false;
        }
        for(var spill:spills)normalized.instructions.remove(spill);
        normalized.instructions.set(planCall,new MethodInsnNode(Opcodes.INVOKEVIRTUAL,TIMER,ADVANCE_TIME,ADVANCE_TIME_DESC,false));
        normalized.instructions.set(stepCall,new MethodInsnNode(Opcodes.INVOKEVIRTUAL,MINECRAFT,TICK,VOID_DESC,false));
        normalized.maxLocals=original.maxLocals;
        ClassNode nativeCopy = new ClassNode(); nativeCopy.name = MINECRAFT; nativeCopy.methods.add(normalized);
        return runTickSupported(nativeCopy)
                && fingerprint(raw, RUN_TICK, RUN_TICK_DESC).equals(fingerprint(nativeCopy, RUN_TICK, RUN_TICK_DESC));
    }

    private static final String CALLBACK_INFO="org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    /**
     * Other mods (Sodium: preRender/postRender/updatePercentileCache) add plain @Inject callbacks to runTick. Remove exactly the
     * non-cancellable patterns - (this, the boolean argument, null) when the handler ignores its CallbackInfo, or a fresh
     * new CallbackInfo(name,false) passed to a foreign handler - so the rest still has to equal the native body. Returns how many were removed,
     * or -1 for anything else that touches a handler (a cancellable callback could skip native ticks and stays refused).
     */
    static int stripForeignCallbacks(MethodNode method,int base) {
        int removed=0;
        for(AbstractInsnNode instruction:method.instructions.toArray()) {
            if(!(instruction instanceof MethodInsnNode call) || !call.owner.equals(MINECRAFT) || !call.name.startsWith("handler$")
                    || call.name.contains("$academy$"))continue;
            if(!call.desc.equals("(ZL"+CALLBACK_INFO+";)V"))return -1;
            List<AbstractInsnNode> block=new ArrayList<>();AbstractInsnNode cursor=call;block.add(call);
            cursor=previousOperation(cursor);
            if(opcode(cursor,Opcodes.ACONST_NULL)) {   // Mixin's optimized form when the handler never reads its CallbackInfo
                block.add(cursor);cursor=previousOperation(cursor);
                if(!var(cursor,Opcodes.ILOAD,1))return -1;block.add(cursor);cursor=previousOperation(cursor);
                if(!var(cursor,Opcodes.ALOAD,0))return -1;block.add(cursor);
                for(var each:block)method.instructions.remove(each);
                removed++;continue;
            }
            Integer slot=null;
            if(cursor instanceof VarInsnNode loaded && loaded.getOpcode()==Opcodes.ALOAD && loaded.var>=base){slot=loaded.var;block.add(cursor);cursor=previousOperation(cursor);}
            if(!var(cursor,Opcodes.ILOAD,1))return -1;block.add(cursor);cursor=previousOperation(cursor);
            if(!var(cursor,Opcodes.ALOAD,0))return -1;block.add(cursor);cursor=previousOperation(cursor);
            if(slot!=null){if(!var(cursor,Opcodes.ASTORE,slot))return -1;block.add(cursor);cursor=previousOperation(cursor);}
            if(!(cursor instanceof MethodInsnNode init) || init.getOpcode()!=Opcodes.INVOKESPECIAL || !init.owner.equals(CALLBACK_INFO)
                    || !init.name.equals("<init>") || !init.desc.equals("(Ljava/lang/String;Z)V"))return -1;
            block.add(cursor);cursor=previousOperation(cursor);
            if(!opcode(cursor,Opcodes.ICONST_0))return -1;block.add(cursor);cursor=previousOperation(cursor);
            if(!(cursor instanceof LdcInsnNode ldc) || !(ldc.cst instanceof String))return -1;block.add(cursor);cursor=previousOperation(cursor);
            if(!opcode(cursor,Opcodes.DUP))return -1;block.add(cursor);cursor=previousOperation(cursor);
            if(!type(cursor,Opcodes.NEW,CALLBACK_INFO))return -1;block.add(cursor);
            if(slot!=null)for(var other:method.instructions)if(!block.contains(other) && (other instanceof VarInsnNode v && v.var==slot
                    || other instanceof IincInsnNode i && i.var==slot))return -1;
            for(var each:block)method.instructions.remove(each);
            removed++;
        }
        return removed;
    }
    private static AbstractInsnNode previousOperation(AbstractInsnNode instruction) {
        AbstractInsnNode cursor=instruction.getPrevious();
        while(cursor!=null && cursor.getOpcode()<0)cursor=cursor.getPrevious();
        return cursor;
    }
    private static List<AbstractInsnNode> redirectSpills(MethodNode method,MethodInsnNode call,int[] opcodes,int[] slots) {
        if(call==null)return null;
        List<AbstractInsnNode> result=new ArrayList<>();AbstractInsnNode cursor=call;
        for(int i=opcodes.length-1;i>=0;i--) {
            cursor=cursor.getPrevious();
            // Literal adjacency excludes instructions, labels and branch entries inside the spill.
            if(!var(cursor,opcodes[i],slots[i]))return null;
            result.add(cursor);
        }
        for(var before=cursor.getPrevious();before!=null && before.getOpcode()<0;before=before.getPrevious())
            if(before instanceof LabelNode label && branchTargets(method,label))return null;
        return result;
    }
    private static boolean branchTargets(MethodNode method,LabelNode label) {
        for(var instruction:method.instructions) {
            if(instruction instanceof JumpInsnNode jump && jump.label==label)return true;
            if(instruction instanceof TableSwitchInsnNode table && (table.dflt==label || table.labels.contains(label)))return true;
            if(instruction instanceof LookupSwitchInsnNode lookup && (lookup.dflt==label || lookup.labels.contains(label)))return true;
        }
        for(var handler:method.tryCatchBlocks)if(handler.start==label || handler.end==label || handler.handler==label)return true;
        return false;
    }

    /** Exact getter composition, one super.travel(vec) call, and normal/exceptional finally. */
    public static boolean localPlayerTransformationSupported(ClassNode raw, ClassNode transformed) {
        transformed=WindPressureLiftSableProjection.project(raw,transformed);
        return classSupported(raw) && LOCAL_PLAYER.equals(raw.name) && transformed != null
                && sameLayout(raw, transformed, Set.of(ACCESSOR)) && WindPressureLiftAutoJumpMixinPlugin.getterSupported(transformed)
                && travelSupported(method(transformed, TRAVEL, TRAVEL_DESC))
                && sameMethods(raw, transformed, Set.of(), Set.of(GETTER + "()I", TRAVEL + TRAVEL_DESC));
    }

    private static boolean travelSupported(MethodNode method) {
        if (method == null || method.access != Opcodes.ACC_PUBLIC) return false;
        List<AbstractInsnNode> code = operations(method);
        if (code.size() != 16 || !var(code.get(0), Opcodes.ALOAD, 0) || !var(code.get(1), Opcodes.ALOAD, 1)
                || !invocation(code.get(2), Opcodes.INVOKESTATIC, RUNTIME, "beforeTravel", "(L" + LOCAL_PLAYER + ";Lnet/minecraft/world/phys/Vec3;)" + TICKET_DESC)
                || !var(code.get(3), Opcodes.ASTORE, 2) || !var(code.get(4), Opcodes.ALOAD, 0) || !var(code.get(5), Opcodes.ALOAD, 1)
                // Mixin resolves the inherited super method to its declaring Player class.
                // The separately pinned AbstractClientPlayer closure has no travel override.
                || !invocation(code.get(6), Opcodes.INVOKESPECIAL, PLAYER, TRAVEL, TRAVEL_DESC)
                || !var(code.get(7), Opcodes.ALOAD, 2) || !invocation(code.get(8), Opcodes.INVOKESTATIC, RUNTIME, "afterTravel", "(" + TICKET_DESC + ")V")
                || !(code.get(9) instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.GOTO || nextOperation(jump.label) != code.get(15)
                || !var(code.get(10), Opcodes.ASTORE, 3) || !var(code.get(11), Opcodes.ALOAD, 2)
                || !invocation(code.get(12), Opcodes.INVOKESTATIC, RUNTIME, "afterTravel", "(" + TICKET_DESC + ")V")
                || !var(code.get(13), Opcodes.ALOAD, 3) || !opcode(code.get(14), Opcodes.ATHROW) || !opcode(code.get(15), Opcodes.RETURN)) return false;
        if (method.tryCatchBlocks.size() != 1) return false;
        TryCatchBlockNode handler = method.tryCatchBlocks.getFirst();
        return handler.type == null && nextOperation(handler.start) == code.get(4) && nextOperation(handler.end) == code.get(7)
                && nextOperation(handler.handler) == code.get(10);
    }

    /*
     * Other client mods (shaders, libraries such as Moonlight) routinely add fields, interfaces and mixin handlers to Minecraft and
     * LocalPlayer, or inject into methods this skill never touches. Demanding byte-identical classes refused the skill in practically
     * every modded game. What stays exact: the class identity and hierarchy, every original field, and the hooked methods themselves
     * (runTick / travel, checked instruction by instruction elsewhere). Foreign additions and foreign edits to unhooked methods pass.
     */
    private static boolean sameLayout(ClassNode raw, ClassNode transformed, Set<String> extraInterfaces) {
        if (raw == null || transformed == null || !raw.name.equals(transformed.name) || raw.access != transformed.access
                || raw.version != transformed.version || !Objects.equals(raw.superName, transformed.superName)) return false;
        Set<String> interfaces = new HashSet<>(transformed.interfaces);
        if (interfaces.size() != transformed.interfaces.size() || !interfaces.containsAll(extraInterfaces) || !interfaces.containsAll(raw.interfaces)) return false;
        Map<String, FieldNode> fields = new HashMap<>();
        for (FieldNode field : transformed.fields) if (fields.put(field.name, field) != null) return false;
        for (FieldNode field : raw.fields) {
            FieldNode now = fields.get(field.name);
            if (now == null || !widened(field.access, now.access) || !now.desc.equals(field.desc) || !Objects.equals(now.signature, field.signature)
                    || !Objects.equals(now.value, field.value)) return false;
        }
        return true;
    }
    private static boolean sameMethods(ClassNode raw, ClassNode transformed, Set<String> changed, Set<String> added) {
        Set<String> seen = new HashSet<>();
        for (MethodNode method : transformed.methods) if (!seen.add(method.name + method.desc)) return false;
        for (String key : added) if (!seen.contains(key)) return false;
        // Every original method must still exist; only the hooked ones (changed) and our additions are inspected exactly by the callers.
        Set<String> original = new HashSet<>();
        for (MethodNode method : raw.methods) { original.add(method.name + method.desc); if (!seen.contains(method.name + method.desc)) return false; }
        // A foreign method may be added, but never a new override that could bend the lift's motion behind the checked travel body.
        for (MethodNode method : transformed.methods)
            if (!original.contains(method.name + method.desc) && !added.contains(method.name + method.desc) && MOVEMENT_OVERRIDES.contains(method.name)) return false;
        return true;
    }
    private static final Set<String> MOVEMENT_OVERRIDES = Set.of("setDeltaMovement", "addDeltaMovement", "move", "travel", "push", "setPos", "setOnGround",
            "moveRelative", "travelInAir", "travelInFluid", "jumpFromGround");
    private static MethodNode generated(ClassNode node, String pattern, String desc) {
        List<MethodNode> methods = node.methods.stream().filter(m -> m.name.matches(pattern)).toList();
        return methods.size() == 1 && methods.getFirst().desc.equals(desc) ? methods.getFirst() : null;
    }
    private static MethodNode method(ClassNode node, String name, String desc) {
        if (node == null) return null;
        List<MethodNode> methods = node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).toList();
        return methods.size() == 1 ? methods.getFirst() : null;
    }
    private static ClassNode copy(ClassNode source) {
        ClassNode copy = new ClassNode(); source.accept(copy);
        for (MethodNode method : copy.methods) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof InvokeDynamicInsnNode dynamic) dynamic.bsmArgs = dynamic.bsmArgs.clone();
        return copy;
    }
    private static MethodNode copy(MethodNode source) {
        MethodNode copy = new MethodNode(Opcodes.ASM9, source.access, source.name, source.desc, source.signature, source.exceptions.toArray(String[]::new));
        source.accept(copy); return copy;
    }
    private static List<AbstractInsnNode> operations(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction); return result;
    }
    private static AbstractInsnNode nextOperation(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext(); return instruction;
    }
    private static boolean var(AbstractInsnNode instruction, int opcode, int slot) { return instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot; }
    private static boolean opcode(AbstractInsnNode instruction, int opcode) { return instruction.getOpcode() == opcode; }
    private static boolean integer(AbstractInsnNode instruction, int value) { return instruction instanceof IntInsnNode integer && integer.getOpcode() == Opcodes.BIPUSH && integer.operand == value; }
    private static boolean type(AbstractInsnNode instruction, int opcode, String name) { return instruction instanceof TypeInsnNode type && type.getOpcode() == opcode && type.desc.equals(name); }
    private static boolean invocation(AbstractInsnNode instruction, int opcode, String owner, String name, String desc) {
        return instruction instanceof MethodInsnNode call && call.getOpcode() == opcode && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc) && !call.itf;
    }
}
