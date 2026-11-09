package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeMiningMixinPlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Exhaustive mutation checks on CURRENT native bytes and the actual compiled NEW213 mixin. */
public final class NativeMiningSeamRegressionTest {
    private static final String OWNER = NativeMiningMixinPlugin.OWNER;
    private static final String MIXIN = "cn/academy/port/highpower/mixin/ServerPlayerGameModeMiningMixin";
    private static final String ACCESS = "cn/academy/port/highpower/NativeMiningAccess";
    private static final String VERSION = "academy$nativeMiningContractVersion";
    private static int checks;
    public static void main(String[] args) throws Exception {
        for (var entry : NativeMiningMixinPlugin.reviewedMethods().entrySet()) {
            ClassNode original = node(entry.getKey());
            check(NativeMiningMixinPlugin.supportedClass(original), "actual pinned native class " + entry.getKey());
            for (String key : entry.getValue().keySet()) {
                MethodNode method = method(original, key);
                for (int i = 0; i < code(method).size(); i++) {
                    ClassNode changed = node(entry.getKey()); MethodNode mutant = method(changed, key);
                    mutant.instructions.set(code(mutant).get(i), new InsnNode(Opcodes.NOP));
                    check(!NativeMiningMixinPlugin.supportedClass(changed), "native instruction mutation " + key + " #" + i);
                }
                ClassNode changed = node(entry.getKey()); changed.methods.remove(method(changed, key));
                check(!NativeMiningMixinPlugin.supportedClass(changed), "missing native method " + key);
                changed = node(entry.getKey()); changed.methods.add(copy(method(changed, key)));
                check(!NativeMiningMixinPlugin.supportedClass(changed), "duplicate native method " + key);
                changed = node(entry.getKey()); method(changed, key).access ^= Opcodes.ACC_STATIC;
                check(!NativeMiningMixinPlugin.supportedClass(changed), "native access mutation " + key);
                changed = node(entry.getKey()); method(changed, key).tryCatchBlocks.add(new TryCatchBlockNode(new LabelNode(),new LabelNode(),new LabelNode(),"java/lang/Throwable"));
                check(!NativeMiningMixinPlugin.supportedClass(changed), "native exception path mutation " + key);
            }
        }
        check(!NativeMiningMixinPlugin.supportedClass(null), "absent native class fails closed");
        ClassNode mixin = node(MIXIN);
        check(mixin.interfaces.equals(List.of(ACCESS)), "exact capability interface");
        int redirects = 0;
        for (MethodNode method : mixin.methods) {
            AnnotationNode redirect = annotation(method, "Lorg/spongepowered/asm/mixin/injection/Redirect;");
            if (redirect == null) continue;
            redirects++;
            for (String key : List.of("require", "expect", "allow")) check(Integer.valueOf(1).equals(value(redirect,key)), "single exact redirect " + method.name + " " + key);
            check(((List<?>)value(redirect,"method")).size()==1,"exact native method selector");
            AnnotationNode at=(AnnotationNode)value(redirect,"at");
            check("INVOKE".equals(value(at,"value"))&&value(at,"shift")==null,"unshifted exact invocation redirect");
        }
        check(redirects == 3, "break, tool, actual removal seams only");
        ClassNode applied = applied();
        check(NativeMiningMixinPlugin.supportedApplied(applied), "actual compiled three-redirect fixture accepted");
        check(!NativeMiningMixinPlugin.supportedClass(applied), "transformed bytes cannot impersonate native");
        check(!NativeMiningMixinPlugin.supportedApplied(node(OWNER)), "native bytes alone do not grant capability");
        ClassNode markerOnly=node(OWNER);markerOnly.interfaces.add(ACCESS);markerOnly.methods.add(copy(method(mixin,VERSION+"()I")));
        check(!NativeMiningMixinPlugin.supportedApplied(markerOnly),"marker-only impostor refused");
        List<String> keys=new ArrayList<>(NativeMiningMixinPlugin.reviewedMethods().get(OWNER).keySet());
        for(MethodNode m:applied.methods)if(m.name.contains("academy$mining")||m.name.equals(VERSION))keys.add(m.name+m.desc);
        for(String key:keys)for(int i=0;i<code(method(applied,key)).size();i++) {
            ClassNode changed=applied();MethodNode m=method(changed,key);m.instructions.set(code(m).get(i),new InsnNode(Opcodes.NOP));
            check(!NativeMiningMixinPlugin.supportedApplied(changed),"applied instruction mutation "+key+" #"+i);
        }
        NativeMiningMixinPlugin plugin=new NativeMiningMixinPlugin();
        check(!plugin.shouldApplyMixin("net.minecraft.world.entity.LivingEntity",MIXIN.replace('/','.')),"unrelated feature not targeted");
        check(!plugin.shouldApplyMixin(OWNER.replace('/','.'),"other.Mixin"),"unrelated mixin refused");
        plugin.postApply(OWNER.replace('/','.'),applied,MIXIN.replace('/','.'),null);
        check(code(method(applied,VERSION+"()I")).getFirst().getOpcode()==Opcodes.ICONST_1,"only verified applied seam grants runtime capability");
        // Core source invariants supplement native bytecode tests; native GameTests prove behavior.
        String source=Files.readString(Path.of("src/main/java/cn/academy/port/highpower/BeamEtchingMining.java"));
        check(!source.contains("getDrops(")&&!source.contains("Blocks.AIR")&&!source.contains("setBlock("),"bridge cannot synthesize drops or AIR writes");
        check(source.contains("reservation.settleRemoved()")&&source.contains("permit.removed"),"payment uses observed native removal");
        if(args.length>0){
            ClassNode actual=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(actual,0);
            check(NativeMiningMixinPlugin.supportedApplied(actual),"actual Mixin0.8.7 transformed runtime class accepted");
            for(MethodNode method:actual.methods)if(method.name.equals("destroyBlock")||method.name.equals("removeBlock")||method.name.contains("academy$mining")||method.name.equals(VERSION)){
                String key=method.name+method.desc;
                for(int i=0;i<code(method).size();i++){
                    ClassNode changed=new ClassNode();actual.accept(changed);MethodNode mutant=method(changed,key);mutant.instructions.set(code(mutant).get(i),new InsnNode(Opcodes.NOP));
                    check(!NativeMiningMixinPlugin.supportedApplied(changed),"actual transformed instruction mutation "+key+" #"+i);
                }
            }
        }
        System.out.println("PASS "+checks+" current native mining seam, transformed capability and compiled redirect checks");
    }
    private static ClassNode applied() throws IOException {
        ClassNode target=node(OWNER),mixin=node(MIXIN);target.interfaces.add(ACCESS);
        target.methods.add(copy(method(mixin,VERSION+"()I")));
        for(MethodNode handler:mixin.methods) {
            AnnotationNode redirect=annotation(handler,"Lorg/spongepowered/asm/mixin/injection/Redirect;");if(redirect==null)continue;
            String selector=(String)((List<?>)value(redirect,"method")).getFirst();
            String invocation=(String)value((AnnotationNode)value(redirect,"at"),"target");
            int semi=invocation.indexOf(';'),paren=invocation.indexOf('(',semi);
            String owner=invocation.substring(1,semi),name=invocation.substring(semi+1,paren),desc=invocation.substring(paren);
            MethodNode nativeMethod=method(target,selector);int replacements=0;
            String generated="redirect$test$"+handler.name;
            for(AbstractInsnNode insn:nativeMethod.instructions.toArray())if(insn instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc)) {
                Type[] args=Type.getArgumentTypes(handler.desc);int base=nativeMethod.name.equals("destroyBlock")?11:5;
                InsnList spill=new InsnList();
                for(int i=args.length-1;i>=0;i--)spill.add(new VarInsnNode(args[i].getOpcode(Opcodes.ISTORE),base+i));
                for(int i=0;i<args.length;i++)spill.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD),base+i));
                nativeMethod.instructions.insertBefore(call,spill);nativeMethod.maxLocals=base+args.length+1;
                nativeMethod.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,OWNER,generated,handler.desc,false));replacements++;
            }
            check(replacements==1,"fixture replaces one real native call "+name);
            MethodNode inserted=copy(handler);inserted.name=generated;target.methods.add(inserted);
        }
        return target;
    }
    private static AnnotationNode annotation(MethodNode m,String desc){for(var list:List.of(m.visibleAnnotations==null?List.<AnnotationNode>of():m.visibleAnnotations,m.invisibleAnnotations==null?List.<AnnotationNode>of():m.invisibleAnnotations))for(var a:list)if(a.desc.equals(desc))return a;return null;}
    private static Object value(AnnotationNode a,String key){if(a.values!=null)for(int i=0;i<a.values.size();i+=2)if(a.values.get(i).equals(key))return a.values.get(i+1);return null;}
    private static ClassNode node(String name)throws IOException{try(var in=NativeMiningSeamRegressionTest.class.getClassLoader().getResourceAsStream(name+".class")){if(in==null)throw new IOException(name);ClassNode n=new ClassNode();new ClassReader(in).accept(n,0);return n;}}
    private static MethodNode method(ClassNode n,String key){return n.methods.stream().filter(m->(m.name+m.desc).equals(key)).findFirst().orElseThrow();}
    private static MethodNode copy(MethodNode m){MethodNode n=new MethodNode(m.access,m.name,m.desc,m.signature,m.exceptions.toArray(String[]::new));m.accept(n);return n;}
    private static List<AbstractInsnNode> code(MethodNode m){List<AbstractInsnNode> out=new ArrayList<>();for(var i:m.instructions)if(i.getOpcode()>=0)out.add(i);return out;}
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
}
