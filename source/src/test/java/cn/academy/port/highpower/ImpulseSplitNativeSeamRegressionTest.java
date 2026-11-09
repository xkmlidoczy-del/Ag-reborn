package cn.academy.port.highpower;

import cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin;
import cn.academy.port.highpower.mixin.NativeCirculationListeningMotionMixinPlugin;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Independent source native fingerprints plus the new adapter's exact, separate injection operands. */
public final class ImpulseSplitNativeSeamRegressionTest {
    private static final String ROOT="cn/academy/port/highpower/",ARROW="net/minecraft/world/entity/projectile/AbstractArrow",BRIDGE=ROOT+"ImpulseSplitArrowBudget";
    private static int checks;
    public static void main(String[] args)throws Exception{
        Map<String,ClassNode> originals=new HashMap<>();
        for(String key:NativeScatterPlateMixinPlugin.reviewedMethods()){String name=key.substring(0,key.indexOf('#'));originals.computeIfAbsent(name,n->{try{return node(n.replace('.','/'));}catch(Exception e){throw new IllegalStateException(e);}});}
        check(NativeScatterPlateMixinPlugin.supported(originals),"all earlier native arrow fingerprints remain independently unchanged");
        var arrow=originals.get(ARROW.replace('/','.'));
        check(NativeCirculationListeningMotionMixinPlugin.nativeSitesSupported(arrow),"original listening Arrow.tick setPos site remains intact");
        var nativeHit=method(arrow,"onHitEntity");
        check(count(nativeHit,n->n instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETFIELD&&f.owner.equals(ARROW)&&f.name.equals("baseDamage")&&f.desc.equals("D"))==1,"one exact native base damage read");
        check(count(nativeHit,n->n instanceof MethodInsnNode m&&m.owner.equals("net/minecraft/world/entity/Entity")&&m.name.equals("hurt"))==1,"one exact native hurt operand");
        var mixin=node(ROOT+"mixin/AbstractArrowImpulseSplitMixin");
        check(mixin.interfaces.equals(List.of(ROOT+"NativeImpulseSplitArrowAccess")),"marker belongs only to the admitted split adapter");
        for(String[] hook:new String[][]{{"academy$impulseSplitNativeHit","beginNativeHit"},{"academy$impulseSplitBase","nativeBaseDamage"},{"academy$impulseSplitHurt","nativeHurtAmount"}}){
            var m=method(mixin,hook[0]);
            check(count(m,n->n instanceof MethodInsnNode c&&c.owner.equals(BRIDGE)&&c.name.equals(hook[1]))==1,"exact bound adapter callback "+hook[0]);
            var annotations=new ArrayList<AnnotationNode>();if(m.visibleAnnotations!=null)annotations.addAll(m.visibleAnnotations);if(m.invisibleAnnotations!=null)annotations.addAll(m.invisibleAnnotations);
            check(annotations.stream().anyMatch(a->contains(a,"onHitEntity(Lnet/minecraft/world/phys/EntityHitResult;)V")),"each injection is local to onHitEntity, not native tick or collision geometry");
        }
        check(count(method(mixin,"academy$impulseSplitNativeHit"),n->n instanceof MethodInsnNode c&&c.owner.equals(BRIDGE)&&c.name.equals("endNativeHit"))>=1,"native scope always exits through finally");
        var bridge=node(BRIDGE);var arm=method(bridge,"arm");
        check(count(arm,n->n instanceof LdcInsnNode l&&l.cst instanceof Type t&&t.getInternalName().equals(ROOT+"NewSkillRuntime"))==1,"only production paid runtime class may originate object shares");
        check(count(arm,n->n instanceof MethodInsnNode c&&c.name.equals("setBaseDamage"))==2,"both physical arrows persist zero before any positive per-object permit can be used");
        check(bridge.methods.stream().allMatch(m->count(m,n->n instanceof MethodInsnNode c&&c.name.equals("getPersistentData"))==0),"NBT is never read as positive split authority");
        var owner=node(ROOT+"mixin/ProjectileImpulseSplitMixin");
        for(String method:List.of("academy$impulseSplitOwner","academy$impulseSplitDeflect"))check(count(method(owner,method),n->n instanceof MethodInsnNode c&&c.owner.equals(BRIDGE)&&c.name.equals("ownerChanged"))==1,"same-owner write and deflection invalidate the object share");
        String config=Files.readString(Path.of("src/main/resources/academy.highpower-impulse-split.mixins.json"));
        check(config.contains("NativeScatterPlateMixinPlugin")&&config.contains("AbstractArrowImpulseSplitMixin")&&config.contains("ProjectileImpulseSplitMixin"),"new configuration reuses the independently pinned source-native contract");
        check(Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml")).contains("academy.highpower-impulse-split.mixins.json"),"shipping metadata actually installs the adapter");
        System.out.println("PASS "+checks+" independent NEW453 native seams and unchanged arrow/listening source admission; transformed native impacts require GameTests");
    }
    private static boolean contains(Object value,String text){if(value instanceof AnnotationNode a)return contains(a.values,text);if(value instanceof Iterable<?> list){for(Object v:list)if(contains(v,text))return true;}return value instanceof String s&&s.equals(text);}
    private static ClassNode node(String name)throws Exception{try(var in=ImpulseSplitNativeSeamRegressionTest.class.getClassLoader().getResourceAsStream(name+".class")){if(in==null)throw new IllegalStateException(name);var n=new ClassNode();new ClassReader(in).accept(n,0);return n;}}
    private static MethodNode method(ClassNode c,String name){return c.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static long count(MethodNode m,java.util.function.Predicate<AbstractInsnNode> test){long n=0;for(var op:m.instructions)if(test.test(op))n++;return n;}
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
}
