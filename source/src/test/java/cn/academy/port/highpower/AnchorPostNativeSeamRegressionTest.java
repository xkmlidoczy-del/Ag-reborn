package cn.academy.port.highpower;
import cn.academy.port.highpower.mixin.NativeAnchorPostMixinPlugin;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Native bytecode, installed delegate and lifecycle controls. Fixtures do not claim a live transformation. */
public final class AnchorPostNativeSeamRegressionTest {
    static final String L="net/minecraft/world/entity/LivingEntity", P="net/minecraft/world/entity/player/Player", H="net/neoforged/neoforge/common/CommonHooks", C="net/neoforged/neoforge/common/damagesource/DamageContainer", B="net/neoforged/bus/api/IEventBus", E="net/neoforged/bus/api/Event", O="Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", A="L"+L+";L"+C+";", PD="("+A+")V", BD="(L"+E+";)L"+E+";", HD="(Lnet/minecraft/world/damagesource/DamageSource;F)V";
    static int checks;
    public static void main(String[] args)throws Exception {
        if(args.length>0) {
            Map<String,ClassNode> exports=new HashMap<>();
            for(String owner:List.of(L,P,H)) {
                ClassNode actual=new ClassNode();
                new ClassReader(Files.readAllBytes(Path.of(args[0],owner+".class"))).accept(actual,0);
                check(NativeAnchorPostMixinPlugin.installedCallbacksSupported(actual),"real transformed witness "+owner);
                exports.put(owner,actual);
                ClassNode changed=copy(actual);
                MethodNode host=method(changed,owner.equals(H)?"onLivingDamagePost":"actuallyHurt");
                MethodInsnNode callback=null;
                for(var instruction:host.instructions)if(instruction instanceof MethodInsnNode c&&c.name.endsWith(owner.equals(H)?"anchor$nativeBusPost":"anchor$nativePost"))callback=c;
                host.instructions.remove(callback);
                check(!NativeAnchorPostMixinPlugin.installedCallbacksSupported(changed),"missing real transformed callback "+owner);
                changed=copy(actual);
                for(var m:changed.methods)if(m.name.startsWith("mixinextras$bridge$")&&m.instructions.iterator().hasNext())for(var instruction:m.instructions)if(instruction instanceof MethodInsnNode c&&c.owner.equals(owner.equals(H)?B:H)&&c.name.equals(owner.equals(H)?"post":"onLivingDamagePost")) {
                    m.instructions.insertBefore(c,new InsnNode(Opcodes.NOP));
                    break;
                }
                check(!NativeAnchorPostMixinPlugin.installedCallbacksSupported(changed),"mutated real original delegate "+owner);
            }
            lifecycle(exports);
            System.out.println("PASS "+checks+" actual exported transformation, mutation and lifecycle checks");
            return;
        }
        Map<String,ClassNode> classes=new HashMap<>();
        for(String owner:List.of(L,P,H,"net/neoforged/bus/EventBus"))classes.put(owner.replace('/','.'),read(owner));
        check(NativeAnchorPostMixinPlugin.supported(classes),"actual reviewed native classes");
        check(!NativeAnchorPostMixinPlugin.supported(null),"missing source evidence");
        ClassNode bus=classes.get("net.neoforged.bus.EventBus");
        check(NativeAnchorPostMixinPlugin.busDispatchSupported(bus),"reviewed actual bus dispatch");
        for(MethodNode m:bus.methods)if(m.name.equals("post")) {
            ClassNode changed=copy(bus);
            changed.methods.stream().filter(x->x.name.equals("post")&&x.desc.equals(m.desc)).findFirst().orElseThrow().instructions.insert(new InsnNode(Opcodes.NOP));
            check(!NativeAnchorPostMixinPlugin.busDispatchSupported(changed),"changed bus "+m.desc);
        }
        ClassNode wrongBus=copy(bus);
        wrongBus.methods.stream().filter(m->m.name.equals("post")&&!m.tryCatchBlocks.isEmpty()).findFirst().orElseThrow().tryCatchBlocks.getFirst().type="java/lang/Exception";
        check(!NativeAnchorPostMixinPlugin.busDispatchSupported(wrongBus),"changed dispatch catch table");
        ClassNode hooks=classes.get(H.replace('/','.'));
        for(int index=0;
        index<9;
        index++) {
            ClassNode mutated=copy(hooks);
            var m=method(mutated,"onLivingDamagePost");
            var code=code(m);
            m.instructions.remove(code.get(index));
            check(!NativeAnchorPostMixinPlugin.nativeSitesSupported(mutated),"missing native instruction "+index);
        }
        ClassNode wrong=copy(hooks);
        method(wrong,"onLivingDamagePost").instructions.insert(new InsnNode(Opcodes.NOP));
        check(!NativeAnchorPostMixinPlugin.nativeSitesSupported(wrong),"changed native body");
        for(String owner:List.of(L,P,H)) {
            ClassNode fixture=fixture(classes.get(owner.replace('/','.')), !owner.equals(H));
            check(NativeAnchorPostMixinPlugin.installedCallbacksSupported(fixture),"constructed installed fixture "+owner);
            ClassNode changed=copy(fixture);
            method(changed,"anchor$delegate").instructions.insert(new InsnNode(Opcodes.NOP));
            check(!NativeAnchorPostMixinPlugin.installedCallbacksSupported(changed),"changed delegate "+owner);
            changed=copy(fixture);
            method(changed,owner.equals(H)?"onLivingDamagePost":"actuallyHurt").instructions.remove(call(method(changed,owner.equals(H)?"onLivingDamagePost":"actuallyHurt"),owner.equals(H)?"anchor$nativeBusPost":"anchor$nativePost"));
            check(!NativeAnchorPostMixinPlugin.installedCallbacksSupported(changed),"missing actual installed callback "+owner);
            changed=copy(fixture);
            method(changed,"anchor$delegate").instructions.remove(call(method(changed,"anchor$delegate"),owner.equals(H)?"post":"onLivingDamagePost"));
            check(!NativeAnchorPostMixinPlugin.installedCallbacksSupported(changed),"missing real delegate "+owner);
        }
        Map<String,ClassNode> fixtures=new HashMap<>();
        for(String owner:List.of(L,P,H))fixtures.put(owner,fixture(classes.get(owner.replace('/','.')),!owner.equals(H)));
        lifecycle(fixtures);
        System.out.println("PASS "+checks+" raw bytecode / constructed witness admission and refusal checks; no live transformation claimed");
    }
    static void lifecycle(Map<String,ClassNode> classes)throws Exception {
        NativeAnchorPostMixinPlugin plugin=new NativeAnchorPostMixinPlugin();
        for(String field:List.of("admitted","sitesPresent","merged")) {
            var member=plugin.getClass().getDeclaredField(field);
            member.setAccessible(true);
            member.setBoolean(plugin,true);
        }
        var type=Class.forName("cn.academy.port.highpower.mixin.NativeAnchorPostMixinPlugin$FinalWitness");
        var constructor=type.getDeclaredConstructor(NativeAnchorPostMixinPlugin.class);
        constructor.setAccessible(true);
        var witness=(org.spongepowered.asm.mixin.transformer.ext.IExtension)constructor.newInstance(plugin);
        int observed=0;
        for(String owner:List.of(L,P,H)) {
            witness.export(null,owner,false,classes.get(owner));
            observed++;
            check(NativeAnchorPostMixinPlugin.isReviewedPostSupported()==(observed==3),"availability waits for all three installed witnesses");
        }
        ClassNode missing=copy(classes.get(H));
        missing.methods.removeIf(m->m.name.endsWith("anchor$nativeBusPost"));
        witness.export(null,H,false,missing);
        check(!NativeAnchorPostMixinPlugin.isReviewedPostSupported(),"a later missing native wrapper revokes availability");
    }
    static ClassNode fixture(ClassNode raw,boolean living) {
        ClassNode n=copy(raw);
        String cb=living?"anchor$nativePost":"anchor$nativeBusPost", cd=living?"("+A+O+")V":"(L"+B+";L"+E+";"+O+A+")L"+E+";";
        MethodNode callback=new MethodNode(Opcodes.ACC_PRIVATE|(living?0:Opcodes.ACC_STATIC),cb,cd,null,null);
        int ac=living?3:5;
        for(int i=0;
        i<ac;
        i++)callback.instructions.add(new VarInsnNode(Opcodes.ALOAD,(living?1:0)+i));
        callback.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"cn/academy/port/highpower/AnchorDamagePostProof",living?"nativePost":"nativeBusPost",cd,false));
        callback.instructions.add(new InsnNode(living?Opcodes.RETURN:Opcodes.ARETURN));
        n.methods.add(callback);
        MethodNode d=new MethodNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC,"anchor$delegate","([Ljava/lang/Object;)"+(living?"Ljava/lang/Void;":"L"+E+";"),null,null);
        String one=living?L:B,two=living?C:E;
        d.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        d.instructions.add(new IntInsnNode(Opcodes.BIPUSH,2));
        d.instructions.add(new LdcInsnNode("["+one.replace('/','.')+", "+two.replace('/','.')+"]"));
        d.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"com/llamalad7/mixinextras/injector/wrapoperation/WrapOperationRuntime","checkArgumentCount","([Ljava/lang/Object;ILjava/lang/String;)V",false));
        d.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        for(int i=0;
        i<2;
        i++) {
            d.instructions.add(new InsnNode(Opcodes.DUP));
            d.instructions.add(new IntInsnNode(Opcodes.BIPUSH,i));
            d.instructions.add(new InsnNode(Opcodes.AALOAD));
            d.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,i==0?one:two));
            d.instructions.add(new InsnNode(Opcodes.SWAP));
        }
        d.instructions.add(new InsnNode(Opcodes.POP));
        d.instructions.add(new MethodInsnNode(living?Opcodes.INVOKESTATIC:Opcodes.INVOKEINTERFACE,living?H:B,living?"onLivingDamagePost":"post",living?PD:BD,!living));
        if(living) {
            d.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            d.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST,"java/lang/Void"));
        }
        d.instructions.add(new InsnNode(Opcodes.ARETURN));
        n.methods.add(d);
        MethodNode host=method(n,living?"actuallyHurt":"onLivingDamagePost");
        MethodInsnNode old=call(host,living?"onLivingDamagePost":"post");
        InsnList replacement=new InsnList();
        if(living) {
            replacement.add(new VarInsnNode(Opcodes.ASTORE,21));
            replacement.add(new VarInsnNode(Opcodes.ASTORE,20));
            replacement.add(new VarInsnNode(Opcodes.ALOAD,0));
            replacement.add(new VarInsnNode(Opcodes.ALOAD,20));
            replacement.add(new VarInsnNode(Opcodes.ALOAD,21));
        }
        if(!living) {
            replacement.add(new VarInsnNode(Opcodes.ASTORE,3));
            replacement.add(new VarInsnNode(Opcodes.ASTORE,2));
            replacement.add(new VarInsnNode(Opcodes.ALOAD,2));
            replacement.add(new VarInsnNode(Opcodes.ALOAD,3));
        }
        replacement.add(new InvokeDynamicInsnNode("call","()"+O,new Handle(Opcodes.H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory","(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false),Type.getMethodType("([Ljava/lang/Object;)Ljava/lang/Object;"),new Handle(Opcodes.H_INVOKESTATIC,n.name,d.name,d.desc,false),Type.getMethodType(d.desc)));
        if(!living) {
            replacement.add(new VarInsnNode(Opcodes.ALOAD,0));
            replacement.add(new VarInsnNode(Opcodes.ALOAD,1));
        }
        replacement.add(new MethodInsnNode(living?Opcodes.INVOKESPECIAL:Opcodes.INVOKESTATIC,n.name,cb,cd,false));
        host.instructions.insertBefore(old,replacement);
        host.instructions.remove(old);
        return n;
    }
    static List<AbstractInsnNode>code(MethodNode m) {
        var a=new ArrayList<AbstractInsnNode>();
        for(var x:m.instructions)if(x.getOpcode()>=0)a.add(x);
        return a;
    }
    static MethodInsnNode call(MethodNode m,String name) {
        for(var x:m.instructions)if(x instanceof MethodInsnNode c&&c.name.equals(name))return c;
        throw new AssertionError(name);
    }
    static MethodNode method(ClassNode n,String name) {
        return n.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
    }
    static ClassNode read(String name)throws Exception {
        try(var s=AnchorPostNativeSeamRegressionTest.class.getResourceAsStream("/"+name+".class")) {
            ClassNode n=new ClassNode();
            new ClassReader(s.readAllBytes()).accept(n,0);
            return n;
        }
    }
    static ClassNode copy(ClassNode n) {
        ClassNode c=new ClassNode();
        n.accept(c);
        return c;
    }
    static void check(boolean p,String reason) {
        checks++;
        if(!p)throw new AssertionError(reason);
    }
}
