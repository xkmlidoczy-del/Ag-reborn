package cn.academy.port.client;

import cn.academy.port.highpower.client.WindPressureLiftAutoJumpMixinPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Native admission plus final transformed read-only getter proof. Optional argument verifies an actual Mixin export. */
public final class WindPressureLiftAutoJumpAccessorRegressionTest {
    private static final String TARGET=WindPressureLiftAutoJumpMixinPlugin.TARGET;
    private static final String ACCESSOR=WindPressureLiftAutoJumpMixinPlugin.ACCESSOR;
    private static final String GETTER=WindPressureLiftAutoJumpMixinPlugin.GETTER;
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        check(!WindPressureLiftAutoJumpMixinPlugin.available(),"no final transformation witness means unavailable");
        ClassNode raw=node(TARGET),accessor=node(ACCESSOR);
        check(WindPressureLiftAutoJumpMixinPlugin.supported(raw),"untouched native private int signature admitted");
        check(!WindPressureLiftAutoJumpMixinPlugin.getterSupported(raw),"native class alone cannot grant access");
        check(!WindPressureLiftAutoJumpMixinPlugin.supported(null),"missing native class refused");
        check(accessor.fields.isEmpty(),"accessor adds no field");
        var methods=accessor.methods.stream().filter(m -> !m.name.equals("<clinit>")).toList();
        check(methods.size()==1 && methods.getFirst().name.equals(GETTER) && methods.getFirst().desc.equals("()I"),"only one getter declared; no setter");
        check((methods.getFirst().access & Opcodes.ACC_ABSTRACT)!=0,"accessor contains no hand-written mutable implementation");
        ClassNode installed=installed(raw);
        check(WindPressureLiftAutoJumpMixinPlugin.getterSupported(installed),"exact ALOAD0 GETFIELD IRETURN getter recognized");
        check(WindPressureLiftAutoJumpMixinPlugin.transformationSupported(raw,installed),"read-only getter leaves native methods and private fields unchanged");
        corrupt(raw,n -> n.fields.stream().filter(f -> f.name.equals("autoJumpTime")).findFirst().orElseThrow().access=Opcodes.ACC_PUBLIC,"public access-transformer mutation refused");
        corrupt(raw,n -> n.fields.stream().filter(f -> f.name.equals("autoJumpTime")).findFirst().orElseThrow().desc="J","wrong native field descriptor refused");
        corrupt(raw,n -> n.fields.removeIf(f -> f.name.equals("autoJumpTime")),"missing native field refused");
        corrupt(raw,n -> n.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"autoJumpTime","I",null,null)),"duplicate native field refused");
        mutate(raw,installed,n -> getter(n).instructions.insert(new InsnNode(Opcodes.NOP)),"extra getter instruction refused");
        mutate(raw,installed,n -> getter(n).instructions.set(getter(n).instructions.get(1),new FieldInsnNode(Opcodes.PUTFIELD,TARGET,"autoJumpTime","I")),"getter field write refused");
        mutate(raw,installed,n -> ((FieldInsnNode)getter(n).instructions.get(1)).name="autoJumpEnabled","wrong getter field refused");
        mutate(raw,installed,n -> getter(n).desc="()J","wrong getter descriptor refused");
        mutate(raw,installed,n -> getter(n).access|=Opcodes.ACC_STATIC,"static accessor refused");
        mutate(raw,installed,n -> n.interfaces.remove(ACCESSOR),"missing accessor identity refused");
        mutate(raw,installed,n -> n.methods.stream().filter(m -> m.name.equals("aiStep") && m.desc.equals("()V")).findFirst().orElseThrow().instructions.insert(new InsnNode(Opcodes.NOP)),"native movement method rewrite refused");
        mutate(raw,installed,n -> n.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"extraWindState","I",null,null)),"extra native field refused");
        mutate(raw,installed,n -> n.methods.add(new MethodNode(Opcodes.ACC_PUBLIC,"extraSetter","(I)V",null,null)),"extra setter method refused");
        finalTiming(raw,installed);
        if(args.length>0) {
            Path exported=Path.of(args[0]);if(Files.isDirectory(exported))exported=exported.resolve(TARGET+".class");
            ClassNode real=new ClassNode();new ClassReader(Files.readAllBytes(exported)).accept(real,0);
            check(WindPressureLiftAutoJumpMixinPlugin.getterSupported(real),"real final exported read-only getter");
            check(WindPressureLiftAutoJumpMixinPlugin.transformationSupported(raw,real),"real final export preserves all native methods and field visibility");
        }
        System.out.println("PASS WindPressureLiftAutoJumpAccessorRegressionTest: "+checks+" checks"+(args.length>0?" including actual transformed export":"; actual transformed export not supplied"));
    }
    private static void finalTiming(ClassNode raw,ClassNode installed) throws Exception {
        var plugin=new WindPressureLiftAutoJumpMixinPlugin();
        var admitted=plugin.getClass().getDeclaredField("admitted");admitted.setAccessible(true);admitted.setBoolean(plugin,true);
        plugin.preApply(TARGET.replace('/','.'),raw,ACCESSOR.replace('/','.'),null);
        plugin.postApply(TARGET.replace('/','.'),installed,ACCESSOR.replace('/','.'),null);
        check(!WindPressureLiftAutoJumpMixinPlugin.available(),"merge phase cannot enable accessor before final witness");
        Class<?> type=Class.forName(plugin.getClass().getName()+"$FinalWitness");var constructor=type.getDeclaredConstructor(plugin.getClass());constructor.setAccessible(true);
        var witness=(org.spongepowered.asm.mixin.transformer.ext.IExtension)constructor.newInstance(plugin);
        witness.export(null,TARGET,false,raw);check(!WindPressureLiftAutoJumpMixinPlugin.available(),"untransformed final tree remains unavailable");
        witness.export(null,TARGET,false,installed);check(WindPressureLiftAutoJumpMixinPlugin.available(),"actual final read-only shape enables access");
        ClassNode damaged=copy(installed);getter(damaged).instructions.insert(new InsnNode(Opcodes.NOP));
        witness.export(null,TARGET,false,damaged);check(!WindPressureLiftAutoJumpMixinPlugin.available(),"subsequent invalid final tree revokes access");
    }
    private static ClassNode node(String name)throws Exception {
        try(var stream=WindPressureLiftAutoJumpAccessorRegressionTest.class.getClassLoader().getResourceAsStream(name+".class")) {
            if(stream==null)throw new IllegalStateException("Missing class "+name);
            ClassNode n=new ClassNode();new ClassReader(stream).accept(n,0);return n;
        }
    }
    private static ClassNode copy(ClassNode source){ClassNode n=new ClassNode();source.accept(n);return n;}
    private static MethodNode getter(ClassNode n){return n.methods.stream().filter(m -> m.name.equals(GETTER)).findFirst().orElseThrow();}
    private static ClassNode installed(ClassNode raw) {
        ClassNode n=copy(raw);n.interfaces.add(ACCESSOR);
        MethodNode getter=new MethodNode(Opcodes.ACC_PUBLIC,GETTER,"()I",null,null);
        getter.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));getter.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,TARGET,"autoJumpTime","I"));
        getter.instructions.add(new InsnNode(Opcodes.IRETURN));getter.maxLocals=1;getter.maxStack=1;n.methods.add(getter);return n;
    }
    private static void corrupt(ClassNode raw,Consumer<ClassNode> mutation,String label){ClassNode n=copy(raw);mutation.accept(n);check(!WindPressureLiftAutoJumpMixinPlugin.supported(n),label);}
    private static void mutate(ClassNode raw,ClassNode installed,Consumer<ClassNode> mutation,String label){ClassNode n=copy(installed);mutation.accept(n);check(!WindPressureLiftAutoJumpMixinPlugin.transformationSupported(raw,n),label);}
}
