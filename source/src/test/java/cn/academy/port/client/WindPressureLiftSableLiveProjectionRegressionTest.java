package cn.academy.port.client;

import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.*;
import static cn.academy.port.highpower.client.WindPressureLiftSableProjection.*;
import static cn.academy.port.highpower.client.WindPressureLiftTravelMixinPlugin.*;
import cn.academy.port.highpower.client.WindPressureLiftTravelMixinPlugin;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Requires actual serialized final classes AND the diagnostic pre-ClassWriter live AST.
 * No game is started. Live snapshots may have unset maxima and are never executed. */
public final class WindPressureLiftSableLiveProjectionRegressionTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        check(args.length==3,"raw jar, permanent final export, permanent live AST required");
        for(String owner:Set.of(MINECRAFT,LOCAL_PLAYER,CLIENT_LEVEL,ENTITY,LIVING_ENTITY,PLAYER,LEVEL))
            check(Files.isRegularFile(Path.of(args[1],owner+".class"))&&Files.isRegularFile(Path.of(args[2],owner+".class")),"all seven observed Sable final targets must exist in both snapshots: "+owner);
        try(var jar=new JarFile(args[0])) {
            for(String owner:requiredClasses()) {
                Path finalPath=Path.of(args[1],owner+".class"),livePath=Path.of(args[2],owner+".class");
                if(!Files.exists(finalPath)||!Files.exists(livePath))continue;
                ClassNode raw=readRaw(jar.getInputStream(jar.getJarEntry(owner+".class")).readAllBytes()),finished=read(Files.readAllBytes(finalPath)),live=read(Files.readAllBytes(livePath));
                String rawBefore=classFingerprint(raw),finalBefore=classFingerprint(finished),liveBefore=classFingerprint(live);
                check(classSupported(raw),"independent original: "+owner);
                check(project(raw,finished)!=null&&project(raw,live)!=null,"both actual trees project: "+owner);
                check(valid(raw,finished,false)&&valid(raw,live,true),"complete final contract accepts actual serialized/live tree: "+owner);
                for(MethodNode helper:finished.methods)if(origin(helper).startsWith("dev.ryanhcode.sable.")||origin(helper).startsWith("foundry.veil.")) {
                    var broken=copy(finished);find(broken,helper.name,helper.desc).instructions.insert(new InsnNode(Opcodes.NOP));
                    check(!valid(raw,broken,false),"author helper code mutation refused: "+key(owner,helper));
                    MethodNode corresponding=live.methods.stream().filter(m->canonicalName(m.name).equals(canonicalName(helper.name))&&m.desc.equals(helper.desc)).findFirst().orElseThrow();
                    check(canonicalFingerprint(finished,helper).equals(canonicalFingerprint(live,corresponding)),"independent sessions and live conservative maxima normalize only generated metadata: "+key(owner,helper));
                }
                if(owner.equals(MINECRAFT)) {
                    var broken=copy(live);MethodNode body=broken.methods.stream().filter(m->m.name.startsWith("runTick$mixinextras$wrapped$")).findFirst().orElseThrow();body.instructions.insert(new InsnNode(Opcodes.NOP));
                    check(!valid(raw,broken,true),"live native tick body extra instruction refused");
                }
                if(owner.equals(LOCAL_PLAYER)) {
                    var changed=copy(finished);FieldNode field=changed.fields.stream().filter(f->f.name.equals("sable$oldPos")).findFirst().orElseThrow();
                    String before=fieldFingerprint(field);var merged=field.visibleAnnotations.stream().filter(a->a.desc.endsWith("/MixinMerged;")).findFirst().orElseThrow();
                    for(int i=0;i+1<merged.values.size();i+=2)if(merged.values.get(i).equals("sessionId"))merged.values.set(i+1,"new-process-session");
                    check(before.equals(fieldFingerprint(field))&&valid(raw,changed,false),"generated field session identity is non-semantic");
                    for(int i=0;i+1<merged.values.size();i+=2)if(merged.values.get(i).equals("mixin"))merged.values.set(i+1,"unknown.Author");
                    check(!valid(raw,changed,false),"field author identity remains pinned");
                }
                check(rawBefore.equals(classFingerprint(raw))&&finalBefore.equals(classFingerprint(finished))&&liveBefore.equals(classFingerprint(live)),"proofs and mutations leave raw/final/live input trees unchanged: "+owner);
            }
        }
        System.out.println("PASS WindPressureLiftSableLiveProjectionRegressionTest: "+checks+" checks; actual final/live bytecode proof only; no running ability assertion");
    }
    private static boolean valid(ClassNode raw,ClassNode tree,boolean live)throws Exception {
        if(raw.name.equals(MINECRAFT)){var m=WindPressureLiftTravelMixinPlugin.class.getDeclaredMethod("inspectMinecraft",ClassNode.class,ClassNode.class,boolean.class);m.setAccessible(true);return m.invoke(null,raw,tree,live)!=null;}
        if(raw.name.equals(LOCAL_PLAYER))return localPlayerTransformationSupported(raw,tree);
        var m=WindPressureLiftTravelMixinPlugin.class.getDeclaredMethod("finalDependencySupported",ClassNode.class,ClassNode.class);m.setAccessible(true);return (boolean)m.invoke(null,raw,tree);
    }
    private static ClassNode readRaw(byte[] bytes){var n=new ClassNode();new ClassReader(bytes).accept(n,0);return n;}
    private static ClassNode read(byte[] bytes){var n=new ClassNode();new ClassReader(bytes).accept(n,ClassReader.SKIP_FRAMES);return n;}
    private static ClassNode copy(ClassNode n){var c=new ClassNode();n.accept(c);return c;}
    private static MethodNode find(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    private static void check(boolean condition,String detail){checks++;if(!condition)throw new AssertionError(detail);}
}
