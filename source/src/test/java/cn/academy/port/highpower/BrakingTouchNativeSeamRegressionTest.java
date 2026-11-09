package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeBrakingTouchMixinPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** CPU admission/dispatch checks; real packet/collision behavior is separately covered by native GameTests. */
public final class BrakingTouchNativeSeamRegressionTest {
    private static final String PREFIX = "cn.academy.port.highpower.mixin.";
    private static final String CONNECTION = "net.minecraft.server.network.ServerGamePacketListenerImpl";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";
    private static final String LIVING = "net.minecraft.world.entity.LivingEntity";
    private static final String PACKET = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V";
    private static final String CHECK = "checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V";
    private static final String CAUSE = "causeFallDamage(FFLnet/minecraft/world/damagesource/DamageSource;)Z";
    private static final String WRAP = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String WRAP_METHOD = "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final Map<String,String> MIXINS = Map.of(CONNECTION,"ServerConnectionBrakingTouchMixin",ENTITY,"EntityBrakingTouchMixin",LIVING,"LivingEntityBrakingTouchMixin");
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(!NativeBrakingTouchMixinPlugin.isReviewedLandingSupported(), "landing unavailable before final installation");
        Map<String,ClassNode> nativeClasses = new HashMap<>();
        for (String key : NativeBrakingTouchMixinPlugin.reviewedMethods()) {
            String owner = key.substring(0,key.indexOf('#')); if (!nativeClasses.containsKey(owner)) nativeClasses.put(owner,node(owner));
        }
        check(NativeBrakingTouchMixinPlugin.supported(nativeClasses), "reviewed native movement and fall chain admitted");
        check(!NativeBrakingTouchMixinPlugin.supported(null), "missing native proof refused");
        for (String key : NativeBrakingTouchMixinPlugin.reviewedMethods()) {
            int split=key.indexOf('#'); String owner=key.substring(0,split), selector=key.substring(split+1);
            mutate(nativeClasses,owner,n->n.methods.remove(method(n,selector)),"missing "+key);
            mutate(nativeClasses,owner,n->n.methods.add(copy(method(n,selector))),"duplicate "+key);
            mutate(nativeClasses,owner,n->method(n,selector).access^=Opcodes.ACC_STATIC,"changed access "+key);
            mutate(nativeClasses,owner,n->method(n,selector).instructions.insert(new InsnNode(Opcodes.NOP)),"changed code "+key);
        }
        Map<String,ClassNode> installed = new HashMap<>();
        for (var entry:MIXINS.entrySet()) {
            String owner=entry.getKey(); ClassNode nativeNode=nativeClasses.get(owner), mixin=node(PREFIX+entry.getValue());
            check(NativeBrakingTouchMixinPlugin.nativeSitesSupported(nativeNode),"original exact call counts "+owner);
            hooks(mixin);
            ClassNode fixture=installedFixture(nativeNode,mixin);installed.put(owner,fixture);
            check(NativeBrakingTouchMixinPlugin.installedCallbacksSupported(fixture),"constructed installed witness "+owner);
            check(!NativeBrakingTouchMixinPlugin.installedCallbacksSupported(nativeNode),"raw native class has no installed witness");
            for (MethodNode callback:new ArrayList<>(mixin.methods)) if(callback.name.startsWith("academy$")) {
                ClassNode missing=copy(fixture);missing.methods.removeIf(m->m.name.equals(callback.name));
                check(!NativeBrakingTouchMixinPlugin.installedCallbacksSupported(missing),"missing callback "+callback.name);
                ClassNode bad=copy(fixture);MethodNode changed=byName(bad,callback.name);
                for(AbstractInsnNode i:changed.instructions)if(i instanceof MethodInsnNode call&&call.owner.contains("BrakingTouch")){call.desc="()V";break;}
                check(!NativeBrakingTouchMixinPlugin.installedCallbacksSupported(bad),"wrong bridge signature "+callback.name);
            }
        }
        ClassNode swapped=copy(installed.get(CONNECTION));MethodNode packet=method(swapped,PACKET);
        MethodInsnNode accepted=call(packet,"academy$brakingAcceptedFall"), correction=call(packet,"doCheckFallDamage");
        AbstractInsnNode placeholder=new InsnNode(Opcodes.NOP);packet.instructions.set(accepted,placeholder);
        packet.instructions.set(correction,accepted);packet.instructions.set(placeholder,correction);
        check(!NativeBrakingTouchMixinPlugin.installedCallbacksSupported(swapped),"adapting correction ordinal instead of accepted ordinal refused");
        ClassNode doubled=copy(installed.get(CONNECTION));MethodNode body=method(doubled,PACKET);MethodInsnNode hook=call(body,"academy$brakingAcceptedFall");
        body.instructions.insert(hook,hook.clone(new HashMap<>()));
        check(!NativeBrakingTouchMixinPlugin.installedCallbacksSupported(doubled),"duplicate accepted wrapper refused");
        finalTiming(nativeClasses,installed);
        bridgeControls(node("cn.academy.port.highpower.BrakingTouchLandingProof"));
        if(args.length>0)for(String owner:MIXINS.keySet()) {
            ClassNode real=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0],owner.replace('.','/')+".class"))).accept(real,0);
            check(NativeBrakingTouchMixinPlugin.installedCallbacksSupported(real),"real exported final transformation "+owner);
        }
        System.out.println("Braking touch native seam: "+checks+" checks passed");
    }
    private static void hooks(ClassNode mixin) {
        for(MethodNode method:mixin.methods)if(method.name.startsWith("academy$")&&!method.name.endsWith("LandingSeam")) {
            boolean wrapMethod=method.name.endsWith("Packet")||method.name.endsWith("Cause");
            AnnotationNode annotation=annotation(method,wrapMethod?WRAP_METHOD:WRAP);
            String selector=method.name.endsWith("Packet")||method.name.endsWith("Move")||method.name.endsWith("AcceptedFall")?PACKET:method.name.endsWith("FallOn")?CHECK:CAUSE;
            check(List.of(selector).equals(value(annotation,"method")),"exact descriptor "+method.name);
            if(!wrapMethod) {
                for(String key:List.of("require","expect","allow"))check(Integer.valueOf(1).equals(value(annotation,key)),"exact callback count "+method.name+" "+key);
                Object raw=value(annotation,"at");AnnotationNode at=raw instanceof AnnotationNode a?a:(AnnotationNode)((List<?>)raw).getFirst();
                check("INVOKE".equals(value(at,"value")),"invocation-only hook");
                if(method.name.endsWith("AcceptedFall"))check(Integer.valueOf(0).equals(value(at,"ordinal")),"accepted native ordinal zero only");
            }
        }
    }
    private static void bridgeControls(ClassNode proof) {
        check(originGeometryEvidence(proof),"origin authority requires actual unsupported geometry before native movement");
        ClassNode missingOrigin=copy(proof);MethodNode missingMove=byName(missingOrigin,"nativeMove");
        missingMove.instructions.remove(call(missingMove,"unsupportedOrigin"));
        check(!originGeometryEvidence(missingOrigin),"removing origin geometry proof is refused");
        ClassNode flagOnly=copy(proof);MethodNode flaggedMove=byName(flagOnly,"nativeMove");
        flaggedMove.instructions.insert(new FieldInsnNode(Opcodes.GETFIELD,
                "cn/academy/port/highpower/BrakingTouchLandingProof$Frame","grounded","Z"));
        check(!originGeometryEvidence(flagOnly),"stale onGround field cannot substitute for origin geometry");
        ClassNode missingShape=copy(proof);MethodNode missingSupport=byName(missingShape,"unsupportedOrigin");
        missingSupport.instructions.remove(call(missingSupport,"findSupportingBlock"));
        check(!originGeometryEvidence(missingShape),"origin block support query cannot be omitted");
        ClassNode missingCollision=copy(proof);MethodNode missingClearance=byName(missingCollision,"unsupportedOrigin");
        missingClearance.instructions.remove(call(missingClearance,"noCollision"));
        check(!originGeometryEvidence(missingCollision),"origin body and foot collision checks are both required");
        MethodNode event=byName(proof,"nativeEvent");
        check(event.instructions.indexOf(call(event,"call"))<event.instructions.indexOf(call(event,"reduceLanding")),"controller invoked only after native event returns");
        check(event.tryCatchBlocks.stream().anyMatch(t->t.type==null),"event scope always closed");
        for(String method:List.of("nativePacket","nativeAcceptedFall","nativeFallOn","nativeCause"))
            check(byName(proof,method).tryCatchBlocks.stream().anyMatch(t->t.type==null),"native cleanup finally "+method);
        check(byName(proof,"nativeFallOn").instructions.indexOf(call(byName(proof,"nativeFallOn"),"call"))
                <byName(proof,"nativeFallOn").instructions.indexOf(call(byName(proof,"nativeFallOn"),"landingFinished")),"physical landing completes after original native operation");
        for(MethodNode m:proof.methods)for(AbstractInsnNode i:m.instructions) {
            if(i instanceof MethodInsnNode call)check(!List.of("setDeltaMovement","resetFallDistance","setOnGround","setNoGravity","hurt","causeFallDamage","doCheckFallDamage","setPos","teleport").contains(call.name),"observer does not synthesize or modify native physics");
            if(i instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.PUTFIELD)check(!List.of("fallDistance","noPhysics","verticalCollisionBelow","hurtMarked").contains(field.name),"observer has no gameplay field writes");
        }
    }
    private static boolean originGeometryEvidence(ClassNode proof) {
        MethodNode move=byName(proof,"nativeMove"),origin=byName(proof,"unsupportedOrigin");
        int gate=-1,nativeCall=-1,collisions=0;boolean support=false;
        for(AbstractInsnNode i:move.instructions) {
            if(i instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.name.equals("grounded"))return false;
            if(i instanceof MethodInsnNode call) {
                if(call.name.equals("unsupportedOrigin"))gate=move.instructions.indexOf(i);
                if(call.name.equals("call")&&nativeCall<0)nativeCall=move.instructions.indexOf(i);
            }
        }
        for(AbstractInsnNode i:origin.instructions)if(i instanceof MethodInsnNode call) {
            if(call.name.equals("findSupportingBlock"))support=true;
            if(call.name.equals("noCollision"))collisions++;
        }
        return gate>=0&&gate<nativeCall&&support&&collisions==2;
    }
    private static void finalTiming(Map<String,ClassNode> raw,Map<String,ClassNode> installed)throws Exception {
        NativeBrakingTouchMixinPlugin plugin=new NativeBrakingTouchMixinPlugin();
        var admission=NativeBrakingTouchMixinPlugin.class.getDeclaredField("admitted");admission.setAccessible(true);admission.setBoolean(plugin,true);
        Class<?> type=Class.forName(PREFIX+"NativeBrakingTouchMixinPlugin$FinalWitness");var constructor=type.getDeclaredConstructor(NativeBrakingTouchMixinPlugin.class);constructor.setAccessible(true);
        var witness=(org.spongepowered.asm.mixin.transformer.ext.IExtension)constructor.newInstance(plugin);
        int count=0;
        for(String owner:List.of(ENTITY,LIVING,CONNECTION)) {
            String mixin=PREFIX+MIXINS.get(owner);plugin.preApply(owner,raw.get(owner),mixin,null);
            ClassNode merged=copy(raw.get(owner)),source=node(mixin);merged.interfaces.addAll(source.interfaces);
            for(MethodNode m:source.methods)if(m.name.startsWith("academy$"))merged.methods.add(copy(m));
            plugin.postApply(owner,merged,mixin,null);
            check(!NativeBrakingTouchMixinPlugin.isReviewedLandingSupported(),"postApply merge does not enable landing");
            witness.export(null,owner,false,merged);
            check(!NativeBrakingTouchMixinPlugin.isReviewedLandingSupported(),"merged-only final tree does not enable landing");
            witness.export(null,owner,false,installed.get(owner));
            check(NativeBrakingTouchMixinPlugin.isReviewedLandingSupported()==(++count==3),"all three installed targets required");
        }
        witness.export(null,CONNECTION,false,raw.get(CONNECTION));
        check(!NativeBrakingTouchMixinPlugin.isReviewedLandingSupported(),"missing final target revokes availability");
    }
    /** Structural predicate fixture only; this is deliberately not called a live native transformation. */
    private static ClassNode installedFixture(ClassNode original,ClassNode mixin) {
        ClassNode result=copy(original);result.interfaces.addAll(mixin.interfaces);
        for(MethodNode m:mixin.methods)if(m.name.startsWith("academy$"))result.methods.add(copy(m));
        if(result.name.equals(CONNECTION.replace('.','/'))) {
            MethodNode packet=method(result,PACKET);replace(result,packet,"move","academy$brakingMove");replace(result,packet,"doCheckFallDamage","academy$brakingAcceptedFall");
            MethodNode wrapper=byName(result,"academy$brakingPacket");packet.instructions.insert(new MethodInsnNode(Opcodes.INVOKESPECIAL,result.name,wrapper.name,wrapper.desc,false));
        } else if(result.name.equals(ENTITY.replace('.','/')))replace(result,method(result,CHECK),"fallOn","academy$brakingFallOn");
        else {
            MethodNode cause=method(result,CAUSE);replace(result,cause,"onLivingFall","academy$brakingEvent");
            MethodNode wrapper=byName(result,"academy$brakingCause");cause.instructions.insert(new MethodInsnNode(Opcodes.INVOKESPECIAL,result.name,wrapper.name,wrapper.desc,false));
        }
        return result;
    }
    private static void replace(ClassNode owner,MethodNode body,String before,String after){MethodNode hook=byName(owner,after);body.instructions.set(call(body,before),new MethodInsnNode(Opcodes.INVOKESPECIAL,owner.name,hook.name,hook.desc,false));}
    private static void mutate(Map<String,ClassNode> source,String owner,Consumer<ClassNode> mutation,String why){Map<String,ClassNode> changed=new HashMap<>(source);ClassNode copy=copy(source.get(owner));changed.put(owner,copy);mutation.accept(copy);check(!NativeBrakingTouchMixinPlugin.supported(changed),why+" refused");}
    private static ClassNode node(String owner)throws Exception{try(var stream=BrakingTouchNativeSeamRegressionTest.class.getResourceAsStream("/"+owner.replace('.','/')+".class")){if(stream==null)throw new AssertionError("Missing "+owner);ClassNode n=new ClassNode();new ClassReader(stream).accept(n,0);return n;}}
    private static MethodNode method(ClassNode node,String selector){return node.methods.stream().filter(m->(m.name+m.desc).equals(selector)).findFirst().orElseThrow();}
    private static MethodNode byName(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static MethodInsnNode call(MethodNode m,String name){for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode call&&call.name.equals(name))return call;throw new AssertionError("Missing "+name);}
    private static ClassNode copy(ClassNode source){ClassNode n=new ClassNode();source.accept(n);return n;}
    private static MethodNode copy(MethodNode source){MethodNode n=new MethodNode(source.access,source.name,source.desc,source.signature,source.exceptions.toArray(String[]::new));source.accept(n);return n;}
    private static AnnotationNode annotation(MethodNode m,String descriptor){List<AnnotationNode> all=new ArrayList<>();if(m.visibleAnnotations!=null)all.addAll(m.visibleAnnotations);if(m.invisibleAnnotations!=null)all.addAll(m.invisibleAnnotations);return all.stream().filter(a->a.desc.equals(descriptor)).findFirst().orElse(null);}
    private static Object value(AnnotationNode n,String key){if(n==null||n.values==null)return null;for(int i=0;i<n.values.size();i+=2)if(key.equals(n.values.get(i)))return n.values.get(i+1);return null;}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
