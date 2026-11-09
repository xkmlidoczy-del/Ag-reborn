package cn.academy.port.client;

import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.*;
import static cn.academy.port.highpower.client.WindPressureLiftTravelMixinPlugin.*;

import cn.academy.port.highpower.client.WindPressureLiftAutoJumpMixinPlugin;
import cn.academy.port.highpower.client.WindPressureLiftTravelMixinPlugin;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Destructive final-shape controls. Default fixtures compose actual compiled mixin bodies
 * with the exact static-Redirect spills observed in r8 and the pinned MixinExtras
 * 0.5.3 WrapMethod/bridge pattern independently inspected in
 * run-client-v57/.mixin.out/class/net/minecraft/server/network/ServerGamePacketListenerImpl.class.
 * A fixture is never reported as an actual transformed-client export. Optional args:
 * [raw merged jar] [real final export directory] [known target inventory file]. When real
 * exports are supplied, the inventory file is mandatory: one dotted or slashed class
 * name per line from the actual configuration target inventory (or its relevant subset
 * exposed by requiredFinalClasses()). Both changed clients and every relevant Mixin
 * target need a final export; untargeted dependencies keep only their independent raw pins.
 */
public final class WindPressureLiftTravelMixinRegressionTest {
    private static final String MIXIN_PACKAGE = "cn/academy/port/client/highpower/mixin/";
    private static final String RUN = "wrapMethod$zzw000$academy$windLiftRun";
    private static final String PLAN = "redirect$zzw000$academy$windLiftPlan";
    private static final String STEP = "redirect$zzw000$academy$windLiftStep";
    private static final String BODY = "runTick$mixinextras$wrapped$999";
    private static final String BRIDGE = "mixinextras$bridge$runTick$mixinextras$wrapped$999$1000";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path jar = args.length == 0 ? Path.of("build/moddev/artifacts/neoforge-21.1.252-merged.jar") : Path.of(args[0]);
        Map<String, ClassNode> raw = load(jar);
        check(supported(raw), "independent frozen raw admission is mandatory");
        check(!available() && minecraftShape() == null, "no transformation observations means unavailable");
        providerControls(raw);
        sableProviderControls(raw);
        ClassNode minecraft = installedMinecraft(raw.get(MINECRAFT)), player = installedPlayer(raw.get(LOCAL_PLAYER));
        MinecraftShape shape = inspectMinecraft(raw.get(MINECRAFT), minecraft);
        check(shape != null && shape.equals(new MinecraftShape(RUN, PLAN, STEP, BODY, BRIDGE)), "all five exact generated method identities recovered");
        check(localPlayerTransformationSupported(raw.get(LOCAL_PLAYER), player), "exact compiled travel body plus private read-only getter composition");
        check(!WindPressureLiftAutoJumpMixinPlugin.transformationSupported(raw.get(LOCAL_PLAYER), player), "old getter-only proof is not silently weakened for travel");
        check(!inspectMinecraftIsPresent(raw.get(MINECRAFT), raw.get(MINECRAFT)), "raw Minecraft is not final transformed proof");
        check(!localPlayerTransformationSupported(raw.get(LOCAL_PLAYER), raw.get(LOCAL_PLAYER)), "raw LocalPlayer is not final transformed proof");
        accessWideningControls(raw);
        minecraftControls(raw.get(MINECRAFT), minecraft);
        ClassNode liveMinecraft=runtimeMinecraftControls(raw.get(MINECRAFT),minecraft);
        playerControls(raw.get(LOCAL_PLAYER), player);
        dependencyControls(raw);
        lifecycle(raw, liveMinecraft, player);
        if (args.length > 1) {
            check(args.length == 3, "actual export verification requires its known target inventory file");
            verifyExports(raw, Path.of(args[1]), Path.of(args[2]));
        }
        check(supported(raw), "all raw trees remain unchanged after destructive fixture tests");
        System.out.println("PASS WindPressureLiftTravelMixinRegressionTest: " + checks + " checks; "
                + (args.length > 1 ? "actual final export directory verified" : "synthetic exact-shape controls only; actual final exports not supplied"));
    }

    private static boolean inspectMinecraftIsPresent(ClassNode raw, ClassNode transformed) { return inspectMinecraft(raw, transformed) != null; }

    private static void sableProviderControls(Map<String,ClassNode> raw) {
        Map<String,String> actual=Map.of(ENTITY,"4ea313a49299ba543cef7bbc1f8efe1739db2b167a47d79d28df8b5ecdd005a3",
                LIVING_ENTITY,"1166bddd5a2f3fbe8df9f08035ce596545eb8a0f4cfbd9385790eb78b4854c0d");
        for(String owner:actual.keySet()) {
            ClassNode original=raw.get(owner),provider=copy(original);
            if(ENTITY.equals(owner)) {
                for(var field:provider.fields)if(Set.of("removalReason","levelCallback").contains(field.name))field.access=(field.access&~Opcodes.ACC_PRIVATE)|Opcodes.ACC_PUBLIC;
                for(var method:provider.methods)if(method.name.equals("getEyePosition")&&method.desc.equals("(F)Lnet/minecraft/world/phys/Vec3;")
                        || method.name.equals("setRemoved")&&method.desc.equals("(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"))method.access&=~Opcodes.ACC_FINAL;
            }else method(provider,"dismountVehicle","(Lnet/minecraft/world/entity/Entity;)V").access=Opcodes.ACC_PUBLIC;
            check(classFingerprint(provider).equals(actual.get(owner)),"actual Sable 2.0.6 provider reconstructed exactly: "+owner);
            check(providerSupported(original,provider),"whole reconstructed AT-only provider admitted, not final travel: "+owner);
            rejectProvider(original,provider,n->n.fields.stream().filter(f->(f.access&Opcodes.ACC_FINAL)==0).findFirst().orElseThrow().access|=Opcodes.ACC_FINAL,"AT plus unrelated field made final: "+owner);
            rejectProvider(original,provider,n->method(n,"tick","()V").instructions.insert(new InsnNode(Opcodes.NOP)),"AT plus one changed native instruction: "+owner);
            rejectProvider(original,provider,n->n.interfaces.add("unreviewed/Physics"),"AT plus unreviewed interface: "+owner);
            rejectProvider(original,provider,n->n.methods.add(new MethodNode(Opcodes.ACC_PUBLIC,"forgedTravel",TRAVEL_DESC,null,null)),"AT plus extra method: "+owner);
        }
    }

    private static MinecraftShape inspectLiveMinecraft(ClassNode raw,ClassNode transformed) throws Exception {
        var inspect=WindPressureLiftTravelMixinPlugin.class.getDeclaredMethod("inspectMinecraft",ClassNode.class,ClassNode.class,boolean.class);
        inspect.setAccessible(true);return (MinecraftShape)inspect.invoke(null,raw,transformed,true);
    }
    private static ClassNode runtimeMinecraftControls(ClassNode raw,ClassNode installed) throws Exception {
        ClassNode live=copy(installed);MethodNode body=named(live,BODY);body.maxStack=0;body.maxLocals=0;
        check(inspectLiveMinecraft(raw,live)!=null,"r10 in-memory WrapMethod 0/0 metadata passes exact private runtime proof");
        check(inspectMinecraft(raw,live)==null,"actual serialized export still requires exact computed maxima");
        check(body.maxStack==0 && body.maxLocals==0,"runtime proof never mutates live moved-method metadata");
        check(inspectLiveMinecraft(raw,installed)!=null,"computed exact maxima remain valid in runtime observation");
        for(int[] maxima:List.of(new int[]{0,18},new int[]{8,0},new int[]{1,1},new int[]{7,18},new int[]{9,18},new int[]{8,17},new int[]{8,19},new int[]{-1,0},new int[]{0,-1})) {
            ClassNode changed=copy(live);MethodNode candidate=named(changed,BODY);
            candidate.maxStack=maxima[0];candidate.maxLocals=maxima[1];
            check(inspectLiveMinecraft(raw,changed)==null,"runtime rejects unreviewed maxima "+maxima[0]+"/"+maxima[1]);
        }
        ClassNode changed=copy(live);named(changed,BODY).instructions.insert(new InsnNode(Opcodes.NOP));
        check(inspectLiveMinecraft(raw,changed)==null,"unset maxima cannot hide changed native body instructions");
        changed=copy(live);spill(named(changed,BODY),PLAN,6,0).var++;
        check(inspectLiveMinecraft(raw,changed)==null,"unset maxima retain exact spill slot proof");
        changed=copy(live);MethodNode duplicate=named(changed,BODY);MethodInsnNode stepCall=call(duplicate,STEP);
        duplicate.instructions.insert(stepCall,new MethodInsnNode(stepCall.getOpcode(),stepCall.owner,stepCall.name,stepCall.desc,stepCall.itf));
        check(inspectLiveMinecraft(raw,changed)==null,"unset maxima retain unique native step call proof");
        changed=copy(live);named(changed,BRIDGE).instructions.insert(new InsnNode(Opcodes.NOP));
        check(inspectLiveMinecraft(raw,changed)==null,"unset maxima cannot bypass exact wrapper and bridge proof");
        return live;
    }

    private static void providerControls(Map<String, ClassNode> raw) throws Exception {
        // Observed before recovery in r6 and independently reproduced with the cached CLIENT
        // RuntimeDistCleaner. These are provider observations, never replacement raw oracle pins.
        Map<String,String> cleanedHashes=Map.of(
                MINECRAFT,"a13ac50ddb38377b6ffabc4b8e00ac6361fb49272ff08ab613341cd38f0d1207",
                TIMER,"ca7a58bfbdbdfe824a42471e8a4a4e94b36176d72cd4f1fda4ac742d46085dc7",
                LOCAL_PLAYER,"e968c8363cc26c7ed0ad8c6a8f02fa4cc760c2ae8db448a6a300b01274f08c36",
                ABSTRACT_CLIENT_PLAYER,"598dce3f4bb24cf34b99a25c17ef908540769a13a7aefcc8aa17b56826c9e1e7",
                CLIENT_LEVEL,"ff8197aae43f0e1aafd867042a1bbd54ae6f412c1b6e110e5d9c16a5179e28a6");
        for(String owner:requiredClasses()) {
            ClassNode original=raw.get(owner),cleaned=copy(original);
            String before=classFingerprint(original);
            if(cleaned.visibleAnnotations!=null)
                cleaned.visibleAnnotations.removeIf(a -> a.desc.equals("Lnet/neoforged/api/distmarker/OnlyIn;"));
            check(classFingerprint(cleaned).equals(cleanedHashes.getOrDefault(owner,before)),"exact recorded cleaner-only provider hash: "+owner);
            check(providerSupported(original,original),"unchanged raw provider admitted: "+owner);
            check(providerSupported(original,cleaned),"exact known side-annotation-only provider admitted: "+owner);
            check(classSupported(cleaned)==!cleanedHashes.containsKey(owner),"cleaned provider never replaces raw oracle: "+owner);
            rejectProvider(original,cleaned,n -> n.methods.stream().filter(m -> m.instructions.size()>0).findFirst().orElseThrow().instructions.insert(new InsnNode(Opcodes.NOP)),"code mutation: "+owner);
            rejectProvider(original,cleaned,n -> n.methods.stream().filter(m->(m.access&(Opcodes.ACC_FINAL|Opcodes.ACC_STATIC|Opcodes.ACC_PRIVATE))==0&&!m.name.startsWith("<")).findFirst().orElseThrow().access|=Opcodes.ACC_FINAL,"method made final: "+owner);
            rejectProvider(original,cleaned,n -> n.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"unreviewedField","I",null,null)),"field mutation: "+owner);
            rejectProvider(original,cleaned,n -> n.interfaces.add("unreviewed/Interface"),"interface mutation: "+owner);
            rejectProvider(original,cleaned,n -> n.sourceFile="unreviewed-debug-source.java","debug metadata mutation: "+owner);
            rejectProvider(original,cleaned,n -> {
                if(n.visibleAnnotations==null)n.visibleAnnotations=new ArrayList<>();
                n.visibleAnnotations.add(new AnnotationNode("Lunreviewed/Annotation;"));
            },"unrelated annotation mutation: "+owner);
            rejectProvider(original,cleaned,n -> {
                if(n.visibleAnnotations==null)n.visibleAnnotations=new ArrayList<>();
                AnnotationNode side=new AnnotationNode("Lnet/neoforged/api/distmarker/OnlyIn;");
                side.values=new ArrayList<>(List.of("value",new String[]{"Lnet/neoforged/api/distmarker/Dist;","DEDICATED_SERVER"}));
                n.visibleAnnotations.add(side);
            },"wrong side annotation is not normalized: "+owner);
            ClassNode forged=copy(original);forged.sourceFile="forged-raw.java";
            check(!providerSupported(forged,cleaned),"untrusted raw input cannot certify provider: "+owner);
            check(!providerSupported(original,null),"missing provider fails closed: "+owner);
            check(before.equals(classFingerprint(original)),"provider proof leaves raw tree intact: "+owner);
            for(String key:reviewedMethods())if(key.startsWith(owner+"#")) {
                int descriptor=key.indexOf('(');String name=key.substring(owner.length()+1,descriptor),desc=key.substring(descriptor);
                check(fingerprint(original,name,desc).equals(fingerprint(cleaned,name,desc)),"cleaner-only fixture preserves exact native method: "+key);
            }
            if(Set.of(MINECRAFT,LOCAL_PLAYER).contains(owner)) {
                var pre=WindPressureLiftTravelMixinPlugin.class.getDeclaredMethod("preTargetSupported",ClassNode.class,ClassNode.class);pre.setAccessible(true);
                check((Boolean)pre.invoke(null,original,cleaned),"unchanged preApply proof accepts exact cleaner-only target: "+owner);
            }
        }
        check(!providerSupported(null,raw.get(MINECRAFT)),"missing raw oracle fails closed");
    }
    private static void rejectProvider(ClassNode raw,ClassNode provider,Consumer<ClassNode> mutation,String label) {
        ClassNode changed=copy(provider);mutation.accept(changed);check(!providerSupported(raw,changed),"provider rejects "+label);
    }

    /** Other mods' access transformers only widen access; any instruction change still fails the raw admission. */
    private static void accessWideningControls(Map<String, ClassNode> raw) {
        ClassNode entity = raw.get(ENTITY), widened = copy(entity);
        for (FieldNode field : widened.fields) if ((field.access & Opcodes.ACC_PRIVATE) != 0) { field.access = (field.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC; break; }
        for (MethodNode method : widened.methods) if ((method.access & Opcodes.ACC_FINAL) != 0) { method.access &= ~Opcodes.ACC_FINAL; break; }
        check(providerSupported(entity, widened), "another mod's access widening on Entity admitted");
        ClassNode narrowed = copy(entity);
        for (FieldNode field : narrowed.fields) if ((field.access & Opcodes.ACC_PUBLIC) != 0) { field.access = (field.access & ~Opcodes.ACC_PUBLIC) | Opcodes.ACC_PRIVATE; break; }
        check(!providerSupported(entity, narrowed), "narrowed access refused");
        ClassNode edited = copy(widened);
        for (MethodNode method : edited.methods) if (method.instructions.size() > 0) { method.instructions.insert(new InsnNode(Opcodes.NOP)); break; }
        check(!providerSupported(entity, edited), "widening plus an instruction change refused");
    }

    private static void minecraftControls(ClassNode raw, ClassNode installed) {
        for (String name : List.of(RUN, PLAN, STEP, BODY, BRIDGE, RUN_TICK)) {
            corruptMinecraft(raw, installed, n -> named(n, name).instructions.insert(new InsnNode(Opcodes.NOP)), "extra instruction in " + name);
            corruptMinecraft(raw, installed, n -> named(n, name).access ^= Opcodes.ACC_STATIC, "wrong access in " + name);
            corruptMinecraft(raw, installed, n -> n.methods.removeIf(m -> m.name.equals(name)), "missing method " + name);
            corruptMinecraft(raw, installed, n -> n.methods.add(copy(named(n, name))), "duplicate method " + name);
            corruptMinecraft(raw, installed, n -> named(n, name).desc = "()I", "wrong descriptor in " + name);
        }
        corruptMinecraft(raw, installed, n -> n.superName = "unreviewed/Parent", "wrong Minecraft superclass");
        // Sodium's real runTick callbacks: (this, flag, null) into a private foreign handler at body start, mid and end.
        foreignMinecraft(raw, installed, n -> { MethodNode body = named(n, BODY); InsnList call = new InsnList();
            call.add(new VarInsnNode(Opcodes.ALOAD, 0)); call.add(new VarInsnNode(Opcodes.ILOAD, 1)); call.add(new InsnNode(Opcodes.ACONST_NULL));
            call.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MINECRAFT, "handler$zza000$sodium$preRender", "(ZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", false));
            body.instructions.insert(call); }, "another mod's non-cancellable runTick callback");
        corruptMinecraft(raw, installed, n -> { MethodNode body = named(n, BODY); InsnList call = new InsnList();
            call.add(new VarInsnNode(Opcodes.ALOAD, 0)); call.add(new VarInsnNode(Opcodes.ILOAD, 1)); call.add(new InsnNode(Opcodes.ACONST_NULL));
            call.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MINECRAFT, "handler$zza000$other$skipTick", "(ZLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", false));
            body.instructions.insert(call); }, "foreign handler with an unreviewed callback type");
        foreignMinecraft(raw, installed, n -> n.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "extra", "I", null, null)), "another mod's added field");
        foreignMinecraft(raw, installed, n -> n.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "extraWrapper", VOID_DESC, null, null)), "another mod's mixin handler");
        foreignMinecraft(raw, installed, n -> named(n, TICK).instructions.insert(new InsnNode(Opcodes.NOP)), "another mod's injection into Minecraft.tick");
        foreignMinecraft(raw, installed, n -> n.interfaces.add("other/mod/Accessor"), "another mod's accessor interface");
        corruptMinecraft(raw, installed, n -> ((VarInsnNode)code(named(n, RUN)).get(1)).var = 2, "run boolean not forwarded");
        corruptMinecraft(raw, installed, n -> ((VarInsnNode)code(named(n, PLAN)).get(1)).var = 2, "plan long argument not forwarded");
        corruptMinecraft(raw, installed, n -> ((VarInsnNode)code(named(n, STEP)).getFirst()).var = 1, "step receiver not forwarded");
        corruptMinecraft(raw, installed, n -> ((MethodInsnNode)code(named(n, RUN)).get(3)).owner = "unreviewed/Runtime", "run callback helper changed");
        corruptMinecraft(raw, installed, n -> ((MethodInsnNode)code(named(n, PLAN)).get(3)).name = "step", "plan helper changed");
        corruptMinecraft(raw, installed, n -> call(named(n, BODY), PLAN).desc = ADVANCE_TIME_DESC, "native plan redirect descriptor changed");
        corruptMinecraft(raw, installed, n -> call(named(n, BODY), STEP).owner = RUNTIME, "native step redirect owner changed");
        int base=method(raw,RUN_TICK,RUN_TICK_DESC).maxLocals;
        check(named(installed,BODY).maxLocals==base+4,"real static Redirect shape has four fresh local slots");
        for(int i=0;i<6;i++) {
            final int index=i;
            corruptMinecraft(raw,installed,n -> spill(named(n,BODY),PLAN,6,index).var++,"plan spill wrong slot "+i);
            corruptMinecraft(raw,installed,n -> {
                MethodNode body=named(n,BODY);var variable=spill(body,PLAN,6,index);
                body.instructions.set(variable,new VarInsnNode(variable.getOpcode()==Opcodes.ALOAD?Opcodes.ILOAD:Opcodes.ALOAD,variable.var));
            },"plan spill wrong type/order "+i);
        }
        for(int i=0;i<2;i++) {
            final int index=i;
            corruptMinecraft(raw,installed,n -> spill(named(n,BODY),STEP,2,index).var=base-1,"step spill aliases native slot "+i);
        }
        corruptMinecraft(raw,installed,n -> named(n,BODY).maxLocals--,"spill local range narrowed");
        corruptMinecraft(raw,installed,n -> named(n,BODY).maxLocals++,"extra unreviewed local range");
        corruptMinecraft(raw,installed,n -> named(n,BODY).maxStack++,"native max stack changed");
        corruptMinecraft(raw,installed,n -> {
            MethodNode body=named(n,BODY);body.instructions.insert(new VarInsnNode(Opcodes.ALOAD,base));
        },"temporary slot reused outside exact spills");
        corruptMinecraft(raw,installed,n -> {
            MethodNode body=named(n,BODY);body.instructions.insert(new VarInsnNode(Opcodes.DLOAD,base-1));
        },"wide access overlaps fresh temporary range");
        corruptMinecraft(raw,installed,n -> named(n,BODY).instructions.insert(new IincInsnNode(base,1)),"temporary slot incremented outside spills");
        corruptMinecraft(raw,installed,n -> {
            MethodNode body=named(n,BODY);body.instructions.insertBefore(call(body,PLAN),new InsnNode(Opcodes.NOP));
        },"extra instruction inside plan spill sequence");
        corruptMinecraft(raw,installed,n -> {
            MethodNode body=named(n,BODY);LabelNode target=new LabelNode();body.instructions.insertBefore(spill(body,PLAN,6,3),target);
        },"label inside spill sequence is not normalized away");
        corruptMinecraft(raw,installed,n -> {
            MethodNode body=named(n,BODY);LabelNode target=new LabelNode();body.instructions.insertBefore(spill(body,PLAN,6,0),target);
            body.instructions.insert(new JumpInsnNode(Opcodes.GOTO,target));
        },"branch enters first spill with unproven arguments");
        corruptMinecraft(raw,installed,n -> {
            MethodNode body=named(n,BODY);LabelNode target=new LabelNode(),end=new LabelNode();
            body.instructions.insertBefore(spill(body,STEP,2,0),target);body.instructions.add(end);
            body.tryCatchBlocks.add(new TryCatchBlockNode(target,end,target,"java/lang/Throwable"));
        },"exception edge enters spill sequence");
        corruptMinecraft(raw, installed, n -> {
            MethodNode body = named(n, BODY); MethodInsnNode step = call(body, STEP);
            body.instructions.insert(step, new MethodInsnNode(step.getOpcode(), step.owner, step.name, step.desc, step.itf));
        }, "duplicate native tick redirect");
        corruptMinecraft(raw, installed, n -> {
            MethodNode body = named(n, BODY); MethodInsnNode step = call(body, STEP); body.instructions.remove(step); body.instructions.insertBefore(call(body, PLAN), step);
        }, "native tick reordered before plan");
        corruptMinecraft(raw, installed, n -> {
            MethodNode body = named(n, BODY); MethodInsnNode min = call(body, "min"); ((IntInsnNode)previous(previous(min))).operand = 11;
        }, "native loop cap modified behind wrappers");
        corruptMinecraft(raw, installed, n -> ((InvokeDynamicInsnNode)code(named(n, RUN_TICK)).get(3)).bsmArgs[1] =
                new Handle(Opcodes.H_INVOKEVIRTUAL, MINECRAFT, BRIDGE, BRIDGE_DESC, false), "bootstrap invocation kind changed");
        corruptMinecraft(raw, installed, n -> ((InvokeDynamicInsnNode)code(named(n, RUN_TICK)).get(3)).bsmArgs[1] =
                new Handle(Opcodes.H_INVOKESPECIAL, MINECRAFT, "extraBridge", BRIDGE_DESC, false), "bootstrap target changed");
        corruptMinecraft(raw, installed, n -> ((InvokeDynamicInsnNode)code(named(n, RUN_TICK)).get(3)).bsmArgs[0] = Type.getMethodType("()V"), "bootstrap argument shape changed");
        corruptMinecraft(raw, installed, n -> ((VarInsnNode)code(named(n, RUN_TICK)).get(1)).var = 2, "outer boolean not forwarded");
        corruptMinecraft(raw, installed, n -> ((IntInsnNode)code(named(n, BRIDGE)).get(7)).operand = 1, "bridge array index changed");
        corruptMinecraft(raw, installed, n -> ((LdcInsnNode)code(named(n, BRIDGE)).get(2)).cst = "[int]", "bridge argument declaration changed");
        corruptMinecraft(raw, installed, n -> ((TypeInsnNode)code(named(n, BRIDGE)).get(9)).desc = "java/lang/Integer", "bridge argument cast changed");
        corruptMinecraft(raw, installed, n -> ((MethodInsnNode)code(named(n, BRIDGE)).get(10)).name = "intValue", "bridge boolean unboxing changed");
        corruptMinecraft(raw, installed, n -> ((MethodInsnNode)code(named(n, BRIDGE)).get(13)).name = RUN_TICK, "bridge recurses into wrapper");
        corruptMinecraft(raw, installed, n -> {
            MethodNode body = named(n, BODY); LabelNode start = new LabelNode(), end = new LabelNode(); body.instructions.insert(start); body.instructions.add(end);
            body.tryCatchBlocks.add(new TryCatchBlockNode(start, end, start, "java/lang/Throwable"));
        }, "native exception table changed");
    }

    private static void playerControls(ClassNode raw, ClassNode installed) {
        corruptPlayer(raw, installed, n -> n.superName = PLAYER, "travel superclass changed");
        foreignPlayer(raw, installed, n -> n.signature=null, "class signature rewritten by another mod");
        foreignPlayer(raw, installed, n -> n.signature="L"+PLAYER+";L"+WindPressureLiftAutoJumpMixinPlugin.ACCESSOR+";", "generic signature only (the real superclass is checked)");
        foreignPlayer(raw, installed, n -> { n.interfaces.add("unreviewed/Interface"); n.signature="L"+ABSTRACT_CLIENT_PLAYER+";L"+WindPressureLiftAutoJumpMixinPlugin.ACCESSOR+";Lunreviewed/Interface;"; }, "another mod's interface");
        corruptPlayer(raw, installed, n -> n.fields.stream().filter(f -> f.name.equals("autoJumpTime")).findFirst().orElseThrow().access = Opcodes.ACC_PUBLIC, "private auto-jump field exposed");
        foreignPlayer(raw, installed, n -> named(n, AI_STEP).instructions.insert(new InsnNode(Opcodes.NOP)), "another mod's injection into aiStep");
        foreignPlayer(raw, installed, n -> n.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "otherModState", "I", null, null)), "another mod's added field");
        corruptPlayer(raw, installed, n -> n.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "move", "(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", null, null)), "new move override");
        corruptPlayer(raw, installed, n -> n.fields.removeIf(f -> f.name.equals("autoJumpTime")), "original field removed");
        corruptPlayer(raw, installed, n -> n.interfaces.remove(WindPressureLiftAutoJumpMixinPlugin.ACCESSOR), "getter interface missing");
        corruptPlayer(raw, installed, n -> named(n, WindPressureLiftAutoJumpMixinPlugin.GETTER).instructions.insert(new InsnNode(Opcodes.NOP)), "getter body changed");
        corruptPlayer(raw, installed, n -> n.methods.removeIf(m -> m.name.equals(TRAVEL)), "travel missing");
        corruptPlayer(raw, installed, n -> n.methods.add(copy(named(n, TRAVEL))), "travel duplicate");
        corruptPlayer(raw, installed, n -> named(n, TRAVEL).access = Opcodes.ACC_PROTECTED, "travel visibility changed");
        corruptPlayer(raw, installed, n -> named(n, TRAVEL).instructions.insert(new InsnNode(Opcodes.NOP)), "travel extra instruction");
        corruptPlayer(raw, installed, n -> ((VarInsnNode)code(named(n, TRAVEL)).get(5)).var = 2, "native travel argument changed");
        corruptPlayer(raw, installed, n -> ((VarInsnNode)code(named(n, TRAVEL)).get(4)).var = 1, "native travel receiver changed");
        corruptPlayer(raw, installed, n -> ((MethodInsnNode)code(named(n, TRAVEL)).get(6)).owner = ABSTRACT_CLIENT_PLAYER, "unresolved super-call owner differs from actual Mixin resolution");
        corruptPlayer(raw, installed, n -> ((MethodInsnNode)code(named(n, TRAVEL)).get(6)).owner = LIVING_ENTITY, "super call cannot skip Player.travel");
        corruptPlayer(raw, installed, n -> ((MethodInsnNode)code(named(n, TRAVEL)).get(6)).owner = "unreviewed/Player", "unknown super-call owner");
        corruptPlayer(raw, installed, n -> ((MethodInsnNode)code(named(n, TRAVEL)).get(6)).setOpcode(Opcodes.INVOKEVIRTUAL), "native travel virtual recursion");
        corruptPlayer(raw, installed, n -> ((MethodInsnNode)code(named(n, TRAVEL)).get(2)).name = "afterTravel", "beforeTravel changed");
        corruptPlayer(raw, installed, n -> named(n, TRAVEL).instructions.remove(code(named(n, TRAVEL)).get(8)), "normal finally removed");
        corruptPlayer(raw, installed, n -> named(n, TRAVEL).instructions.remove(code(named(n, TRAVEL)).get(12)), "exceptional finally removed");
        corruptPlayer(raw, installed, n -> ((VarInsnNode)code(named(n, TRAVEL)).get(11)).var = 1, "exceptional cleanup ticket changed");
        corruptPlayer(raw, installed, n -> named(n, TRAVEL).instructions.set(code(named(n, TRAVEL)).get(14), new InsnNode(Opcodes.RETURN)), "native exception swallowed");
        corruptPlayer(raw, installed, n -> named(n, TRAVEL).tryCatchBlocks.clear(), "travel handler removed");
        corruptPlayer(raw, installed, n -> named(n, TRAVEL).tryCatchBlocks.getFirst().type = "java/lang/RuntimeException", "travel handler narrowed");
        corruptPlayer(raw, installed, n -> {
            MethodNode m = named(n, TRAVEL); TryCatchBlockNode handler = m.tryCatchBlocks.getFirst(); handler.start = handler.end;
        }, "travel exception protected range removed");
        corruptPlayer(raw, installed, n -> {
            MethodNode m = named(n, TRAVEL); m.tryCatchBlocks.add(m.tryCatchBlocks.getFirst());
        }, "extra travel exception handler");
        corruptPlayer(raw, installed, n -> n.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "setDeltaMovement", TRAVEL_DESC, null, null)), "callback-capable movement setter override");
    }

    private static void dependencyControls(Map<String, ClassNode> raw) {
        for (String owner : requiredClasses()) if (!Set.of(MINECRAFT, LOCAL_PLAYER).contains(owner)) {
            check(finalDependencySupported(raw.get(owner), raw.get(owner)), "unchanged relevant dependency methods: " + owner);
            ClassNode unrelated = copy(raw.get(owner));
            unrelated.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "unrelatedHelper", VOID_DESC, null, null));
            check(finalDependencySupported(raw.get(owner), unrelated), "dependency proof does not claim whole transformed class equality: " + owner);
            for (String key : reviewedMethods()) if (key.startsWith(owner + "#")) {
                int descriptor = key.indexOf('('); String name = key.substring(owner.length() + 1, descriptor), desc = key.substring(descriptor);
                ClassNode changed = copy(raw.get(owner)); method(changed, name, desc).instructions.insert(new InsnNode(Opcodes.NOP));
                check(!finalDependencySupported(raw.get(owner), changed), "changed final dependency method refused: " + key);
            }
            ClassNode wrongSuper = copy(raw.get(owner)); wrongSuper.superName = "unreviewed/Parent";
            check(!finalDependencySupported(raw.get(owner), wrongSuper), "changed final hierarchy refused: " + owner);
        }
        for (String owner : List.of(ABSTRACT_CLIENT_PLAYER, PLAYER, LIVING_ENTITY)) {
            ClassNode changed = copy(raw.get(owner)); changed.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "setDeltaMovement", TRAVEL_DESC, null, null));
            check(!finalDependencySupported(raw.get(owner), changed), "new final setter override refused: " + owner);
        }
        ClassNode newOverride=copy(raw.get(ABSTRACT_CLIENT_PLAYER));
        newOverride.methods.add(new MethodNode(Opcodes.ACC_PUBLIC,TRAVEL,TRAVEL_DESC,null,null));
        check(!finalDependencySupported(raw.get(ABSTRACT_CLIENT_PLAYER),newOverride),"new AbstractClientPlayer travel override invalidates declaring-owner proof");
    }

    @SuppressWarnings("unchecked")
    private static void lifecycle(Map<String, ClassNode> raw, ClassNode minecraft, ClassNode player) throws Exception {
        var plugin = new WindPressureLiftTravelMixinPlugin();
        var originals = WindPressureLiftTravelMixinPlugin.class.getDeclaredField("originals"); originals.setAccessible(true);
        ((Map<String, ClassNode>)originals.get(plugin)).putAll(raw);
        var admitted = WindPressureLiftTravelMixinPlugin.class.getDeclaredField("admitted"); admitted.setAccessible(true); admitted.setBoolean(plugin, true);
        var observe = WindPressureLiftTravelMixinPlugin.class.getDeclaredMethod("observeFinal", String.class, ClassNode.class); observe.setAccessible(true);
        String prefix = "cn.academy.port.client.highpower.mixin.";
        plugin.preApply(MINECRAFT.replace('/', '.'), raw.get(MINECRAFT), prefix + "WindPressureLiftMinecraftMixin", null);
        plugin.preApply(LOCAL_PLAYER.replace('/', '.'), raw.get(LOCAL_PLAYER), prefix + "WindPressureLiftTravelMixin", null);
        var prepared = WindPressureLiftTravelMixinPlugin.class.getDeclaredField("prepared"); prepared.setAccessible(true);
        check(((Set<String>)prepared.get(plugin)).equals(Set.of(MINECRAFT, LOCAL_PLAYER)), "both original client targets prepare successfully");
        plugin.postApply(MINECRAFT.replace('/', '.'), minecraft, prefix + "WindPressureLiftMinecraftMixin", null);
        plugin.postApply(LOCAL_PLAYER.replace('/', '.'), player, prefix + "WindPressureLiftTravelMixin", null);
        check(!available(), "ordinary postApply cannot enable incomplete MixinExtras transformation");
        observe.invoke(plugin, MINECRAFT, minecraft); observe.invoke(plugin, LOCAL_PLAYER, player);
        check(!available(), "missing complete target inventory refuses availability");
        plugin.acceptTargets(Set.of(MINECRAFT, LOCAL_PLAYER), Set.of(ENTITY, PLAYER, LIVING_ENTITY));
        check(!available(), "missing targeted dependency final observations refuses availability");
        observe.invoke(plugin, ENTITY, raw.get(ENTITY)); observe.invoke(plugin, PLAYER, raw.get(PLAYER));
        check(!available(), "one missing targeted final dependency still refuses availability");
        observe.invoke(plugin, LIVING_ENTITY, raw.get(LIVING_ENTITY));
        var observations = WindPressureLiftTravelMixinPlugin.class.getDeclaredField("observedFinals"); observations.setAccessible(true);
        Map<String, ClassNode> observed = (Map<String, ClassNode>)observations.get(plugin);
        check(inspectLiveMinecraft(raw.get(MINECRAFT), observed.get(MINECRAFT)) != null, "stored live Minecraft snapshot retains exact shape with r10 unset maxima");
        check(localPlayerTransformationSupported(raw.get(LOCAL_PLAYER), observed.get(LOCAL_PLAYER)), "stored final LocalPlayer snapshot retains exact shape");
        for (String owner : List.of(ENTITY, PLAYER, LIVING_ENTITY)) check(finalDependencySupported(raw.get(owner), observed.get(owner)), "stored final dependency remains exact: " + owner);
        check(available() && minecraftShape().equals(new MinecraftShape(RUN, PLAN, STEP, BODY, BRIDGE)), "all required exact synthetic final observations enable metadata");
        ClassNode damaged = copy(player); named(damaged, TRAVEL).instructions.insert(new InsnNode(Opcodes.NOP));
        observe.invoke(plugin, LOCAL_PLAYER, damaged);
        check(!available() && minecraftShape() == null, "later invalid final player revokes availability and metadata");
        observe.invoke(plugin, LOCAL_PLAYER, player);
        check(available(), "valid repeated synthetic final observation restores complete proof");
        ClassNode setter = copy(raw.get(ENTITY)); method(setter, "setDeltaMovement", TRAVEL_DESC).instructions.insert(new InsnNode(Opcodes.NOP));
        observe.invoke(plugin, ENTITY, setter);
        check(!available(), "later invalid targeted final setter revokes availability");
        observe.invoke(plugin, ENTITY, raw.get(ENTITY));
        plugin.acceptTargets(Set.of(MINECRAFT, LOCAL_PLAYER), Set.of(TIMER));
        check(!available(), "newly observed dependency target requires its own final observation");
        observe.invoke(plugin, TIMER, raw.get(TIMER));
        check(available(), "additional target accepted only after its exact final observation");
        observe.invoke(plugin, MINECRAFT, raw.get(MINECRAFT));
        check(!available() && minecraftShape() == null, "leave static availability false after fixture-only tests");
    }

    private static void verifyExports(Map<String, ClassNode> raw, Path exports, Path targetInventory) throws Exception {
        var targets = new java.util.HashSet<String>();
        for (String line : Files.readAllLines(targetInventory)) {
            String owner = line.strip().replace('.', '/');
            if (!owner.isEmpty() && !owner.startsWith("#")) targets.add(owner);
        }
        check(targets.containsAll(Set.of(MINECRAFT, LOCAL_PLAYER)), "known target inventory includes both changed clients");
        for (String owner : requiredClasses()) {
            Path file = exports.resolve(owner + ".class");
            if (!targets.contains(owner)) {
                check(!Files.exists(file), "unexpected dependency export outside the known target inventory: " + owner);
                check(classSupported(raw.get(owner)), "untargeted dependency retains raw admission only: " + owner);
                continue;
            }
            check(Files.isRegularFile(file), "required real final export exists: " + owner);
            ClassNode node = new ClassNode(); new ClassReader(Files.readAllBytes(file)).accept(node, 0);
            check(owner.equals(node.name), "real final export identity: " + owner);
            if (owner.equals(MINECRAFT)) check(inspectMinecraft(raw.get(owner), node) != null, "actual final Minecraft wrappers and unchanged native body");
            else if (owner.equals(LOCAL_PLAYER)) check(localPlayerTransformationSupported(raw.get(owner), node), "actual final LocalPlayer getter and exact travel/finally");
            else check(finalDependencySupported(raw.get(owner), node), "actual final dependency method proof: " + owner);
        }
    }

    private static ClassNode installedMinecraft(ClassNode raw) throws Exception {
        ClassNode result = copy(raw), mixin = resource(MIXIN_PACKAGE + "WindPressureLiftMinecraftMixin");
        MethodNode original = method(result, RUN_TICK, RUN_TICK_DESC); original.name = BODY;
        MethodInsnNode advance = call(original, ADVANCE_TIME), tick = call(original, TICK);
        int base=original.maxLocals;
        InsnList planSpills=new InsnList();
        planSpills.add(new VarInsnNode(Opcodes.ISTORE,base+3));planSpills.add(new VarInsnNode(Opcodes.LSTORE,base+1));
        planSpills.add(new VarInsnNode(Opcodes.ASTORE,base));planSpills.add(new VarInsnNode(Opcodes.ALOAD,base));
        planSpills.add(new VarInsnNode(Opcodes.LLOAD,base+1));planSpills.add(new VarInsnNode(Opcodes.ILOAD,base+3));
        original.instructions.insertBefore(advance,planSpills);
        InsnList stepSpills=new InsnList();stepSpills.add(new VarInsnNode(Opcodes.ASTORE,base));stepSpills.add(new VarInsnNode(Opcodes.ALOAD,base));
        original.instructions.insertBefore(tick,stepSpills);original.maxLocals=base+4;
        original.instructions.set(advance, new MethodInsnNode(Opcodes.INVOKESTATIC, MINECRAFT, PLAN, PLAN_HANDLER_DESC, false));
        original.instructions.set(tick, new MethodInsnNode(Opcodes.INVOKESTATIC, MINECRAFT, STEP, STEP_HANDLER_DESC, false));
        for (String[] names : List.of(new String[]{"academy$windLiftRun", RUN}, new String[]{"academy$windLiftPlan", PLAN}, new String[]{"academy$windLiftStep", STEP})) {
            MethodNode handler = copy(named(mixin, names[0])); handler.name = names[1]; removeSelfCast(handler, MINECRAFT); result.methods.add(handler);
        }
        MethodNode outer = new MethodNode(Opcodes.ACC_PRIVATE, RUN_TICK, RUN_TICK_DESC, null, null);
        add(outer, new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ILOAD, 1), new VarInsnNode(Opcodes.ALOAD, 0),
                new InvokeDynamicInsnNode("call", "(L" + MINECRAFT + ";)" + OPERATION,
                        new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
                        Type.getMethodType("([Ljava/lang/Object;)Ljava/lang/Object;"), new Handle(Opcodes.H_INVOKESPECIAL, MINECRAFT, BRIDGE, BRIDGE_DESC, false), Type.getMethodType(BRIDGE_DESC)),
                new MethodInsnNode(Opcodes.INVOKESPECIAL, MINECRAFT, RUN, RUN_HANDLER_DESC, false), new InsnNode(Opcodes.RETURN));
        outer.maxLocals = 2; outer.maxStack = 3; result.methods.add(outer);
        MethodNode bridge = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, BRIDGE, BRIDGE_DESC, null, null);
        add(bridge, new VarInsnNode(Opcodes.ALOAD, 1), new IntInsnNode(Opcodes.BIPUSH, 1), new LdcInsnNode("[boolean]"),
                new MethodInsnNode(Opcodes.INVOKESTATIC, "com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime", "checkArgumentCount", "([Ljava/lang/Object;ILjava/lang/String;)V", false),
                new VarInsnNode(Opcodes.ALOAD, 0), new VarInsnNode(Opcodes.ALOAD, 1), new InsnNode(Opcodes.DUP), new IntInsnNode(Opcodes.BIPUSH, 0), new InsnNode(Opcodes.AALOAD),
                new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"), new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false), new InsnNode(Opcodes.SWAP), new InsnNode(Opcodes.POP),
                new MethodInsnNode(Opcodes.INVOKESPECIAL, MINECRAFT, BODY, RUN_TICK_DESC, false), new InsnNode(Opcodes.ACONST_NULL), new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Void"), new InsnNode(Opcodes.ARETURN));
        bridge.maxLocals = 2; bridge.maxStack = 4; result.methods.add(bridge); return result;
    }

    private static ClassNode installedPlayer(ClassNode raw) throws Exception {
        ClassNode result = copy(raw); result.interfaces.add(WindPressureLiftAutoJumpMixinPlugin.ACCESSOR);
        result.signature="L"+ABSTRACT_CLIENT_PLAYER+";L"+WindPressureLiftAutoJumpMixinPlugin.ACCESSOR+";";
        MethodNode getter = new MethodNode(Opcodes.ACC_PUBLIC, WindPressureLiftAutoJumpMixinPlugin.GETTER, "()I", null, null);
        add(getter, new VarInsnNode(Opcodes.ALOAD, 0), new FieldInsnNode(Opcodes.GETFIELD, LOCAL_PLAYER, "autoJumpTime", "I"), new InsnNode(Opcodes.IRETURN));
        getter.maxLocals = 1; getter.maxStack = 1; result.methods.add(getter);
        MethodNode travel = copy(named(resource(MIXIN_PACKAGE + "WindPressureLiftTravelMixin"), TRAVEL)); removeSelfCast(travel, LOCAL_PLAYER);
        MethodInsnNode superCall=call(travel,TRAVEL);
        check(superCall.getOpcode()==Opcodes.INVOKESPECIAL && superCall.owner.equals(ABSTRACT_CLIENT_PLAYER),"compiled source calls its immediate superclass before Mixin binding");
        superCall.owner=PLAYER;result.methods.add(travel);return result;
    }
    private static void removeSelfCast(MethodNode method, String target) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.CHECKCAST && type.desc.equals(target)) method.instructions.remove(type);
    }
    private static ClassNode resource(String name) throws Exception {
        try (InputStream input = WindPressureLiftTravelMixinRegressionTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new AssertionError("missing compiled mixin resource: " + name);
            ClassNode result = new ClassNode(); new ClassReader(input).accept(result, 0); return result;
        }
    }
    private static Map<String, ClassNode> load(Path jarPath) throws Exception {
        Map<String, ClassNode> result = new HashMap<>(); try (JarFile jar = new JarFile(jarPath.toFile())) {
            for (String owner : requiredClasses()) try (InputStream input = jar.getInputStream(jar.getJarEntry(owner + ".class"))) {
                ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); result.put(owner, node);
            }
        } return result;
    }
    private static void corruptMinecraft(ClassNode raw, ClassNode installed, Consumer<ClassNode> mutation, String label) {
        ClassNode damaged = copy(installed); mutation.accept(damaged); check(inspectMinecraft(raw, damaged) == null, label + " refused");
    }
    /** Additions other client mods make (fields, interfaces, handlers, edits to unhooked methods) must not disable the lift. */
    private static void foreignMinecraft(ClassNode raw, ClassNode installed, Consumer<ClassNode> mutation, String label) {
        ClassNode changed = copy(installed); mutation.accept(changed); check(inspectMinecraft(raw, changed) != null, label + " tolerated");
    }
    private static void foreignPlayer(ClassNode raw, ClassNode installed, Consumer<ClassNode> mutation, String label) {
        ClassNode changed = copy(installed); mutation.accept(changed); check(localPlayerTransformationSupported(raw, changed), label + " tolerated");
    }
    private static void corruptPlayer(ClassNode raw, ClassNode installed, Consumer<ClassNode> mutation, String label) {
        ClassNode damaged = copy(installed); mutation.accept(damaged); check(!localPlayerTransformationSupported(raw, damaged), label + " refused");
    }
    private static ClassNode copy(ClassNode source) {
        ClassNode result = new ClassNode(); source.accept(result);
        for (MethodNode method : result.methods) for (AbstractInsnNode instruction : method.instructions)
            if (instruction instanceof InvokeDynamicInsnNode dynamic) dynamic.bsmArgs = dynamic.bsmArgs.clone();
        return result;
    }
    private static MethodNode copy(MethodNode source) {
        MethodNode result = new MethodNode(Opcodes.ASM9, source.access, source.name, source.desc, source.signature, source.exceptions.toArray(String[]::new)); source.accept(result); return result;
    }
    private static MethodNode method(ClassNode node, String name, String desc) { return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst().orElseThrow(); }
    private static MethodNode named(ClassNode node, String name) { return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> code(MethodNode method) { List<AbstractInsnNode> result = new ArrayList<>(); for (AbstractInsnNode i : method.instructions) if (i.getOpcode() >= 0) result.add(i); return result; }
    private static MethodInsnNode call(MethodNode method, String name) { return code(method).stream().filter(i -> i instanceof MethodInsnNode m && m.name.equals(name)).map(i -> (MethodInsnNode)i).findFirst().orElseThrow(); }
    private static AbstractInsnNode previous(AbstractInsnNode instruction) { do { instruction = instruction.getPrevious(); } while (instruction != null && instruction.getOpcode() < 0); return instruction; }
    private static VarInsnNode spill(MethodNode method,String name,int count,int index) {
        AbstractInsnNode node=call(method,name);for(int i=count;i>index;i--)node=previous(node);return (VarInsnNode)node;
    }
    private static void add(MethodNode method, AbstractInsnNode... instructions) { for (AbstractInsnNode instruction : instructions) method.instructions.add(instruction); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
