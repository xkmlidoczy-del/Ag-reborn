package cn.academy.port.client;

import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.*;
import static cn.academy.port.highpower.client.WindPressureLiftSableProjection.*;
import static cn.academy.port.highpower.client.WindPressureLiftTravelMixinPlugin.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Actual Sable export projection plus destructive controls; composed wind wrappers are fixtures,
 * never evidence of a running client. Arguments: raw merged jar, Sable final-export class folder. */
public final class WindPressureLiftSableProjectionRegressionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(args.length==2,"requires independent raw jar and actual Sable export directory");
        Map<String,ClassNode> raw=new HashMap<>(),finals=new HashMap<>();
        try(var jar=new JarFile(args[0])) {
            for(String owner:requiredClasses()) {
                try(var in=jar.getInputStream(jar.getJarEntry(owner+".class"))) {raw.put(owner,read(in.readAllBytes()));}
                Path file=Path.of(args[1],owner+".class");if(Files.exists(file))finals.put(owner,read(Files.readAllBytes(file)));
            }
        }
        check(supported(raw),"raw admission remains independent and exact");
        boolean actualWind=finals.get(MINECRAFT).methods.stream().anyMatch(m->m.name.contains("academy$windLiftRun"));
        int nativeChanges=0,helperBodies=0;
        for(var entry:finals.entrySet()) {
            String owner=entry.getKey();ClassNode baseline=raw.get(owner),actual=entry.getValue(),projected=project(baseline,actual);
            check(projected!=null,"actual export projects: "+owner);
            for(String key:reviewedMethods())if(key.startsWith(owner+"#")) {
                int desc=key.indexOf('(');String name=key.substring(owner.length()+1,desc),signature=key.substring(desc);
                boolean wrapped=actualWind&&owner.equals(MINECRAFT)&&name.equals(RUN_TICK);
                check(wrapped?inspectMinecraft(baseline,actual)!=null:fingerprint(baseline,name,signature).equals(fingerprint(projected,name,signature)),"native stream restored or actual exact wind wrapper proved: "+key);
                if(!fingerprint(baseline,name,signature).equals(fingerprint(actual,name,signature))) {
                    nativeChanges++;ClassNode broken=copy(actual);method(broken,name,signature).instructions.insert(new InsnNode(Opcodes.NOP));
                    check(wrapped?inspectMinecraft(baseline,broken)==null:project(baseline,broken)==null,"extra native instruction refused: "+key);
                }
            }
            for(MethodNode helper:actual.methods)if(origin(helper).startsWith("dev.ryanhcode.sable.")||origin(helper).startsWith("foundry.veil.")) {
                helperBodies++;ClassNode broken=copy(actual);method(broken,helper.name,helper.desc).instructions.insert(new InsnNode(Opcodes.NOP));
                check(project(baseline,broken)==null,"modified author helper refused: "+key(owner,helper));
                broken=copy(actual);MethodNode altered=method(broken,helper.name,helper.desc);
                for(AnnotationNode annotation:altered.visibleAnnotations)if(annotation.desc.endsWith("/MixinMerged;"))
                    for(int i=0;i+1<annotation.values.size();i+=2)if(annotation.values.get(i).equals("mixin"))annotation.values.set(i+1,"unknown.mixin.Owner");
                check(project(baseline,broken)==null,"mismatched author provenance refused: "+key(owner,helper));
            }
            if(owner.equals(MINECRAFT)||owner.equals(LOCAL_PLAYER))for(FieldNode field:actual.fields)
                if(baseline.fields.stream().noneMatch(f->f.name.equals(field.name)&&f.desc.equals(field.desc))) {
                    ClassNode broken=copy(actual);broken.fields.stream().filter(f->f.name.equals(field.name)).findFirst().orElseThrow().access^=Opcodes.ACC_FINAL;
                    check(project(baseline,broken)==null,"added field contract is exact: "+field.name);
                }
        }
        check(nativeChanges==10,"actual inspected export has all ten reviewed native method changes");
        check(helperBodies>100,"all Sable/Veil helper implementations inspected, not a version-only allowance");
        var pre=cn.academy.port.highpower.client.WindPressureLiftTravelMixinPlugin.class.getDeclaredMethod("preTargetSupported",ClassNode.class,ClassNode.class);pre.setAccessible(true);
        for(String owner:List.of(MINECRAFT,LOCAL_PLAYER))check(actualWind?(owner.equals(MINECRAFT)?inspectMinecraft(raw.get(owner),finals.get(owner))!=null:localPlayerTransformationSupported(raw.get(owner),finals.get(owner))):(boolean)pre.invoke(null,raw.get(owner),finals.get(owner)),"actual Sable target layout and phase-specific native contract: "+owner);
        Class<?> fixtures=WindPressureLiftTravelMixinRegressionTest.class;
        var installMc=fixtures.getDeclaredMethod("installedMinecraft",ClassNode.class);installMc.setAccessible(true);
        ClassNode composedMc=actualWind?copy(finals.get(MINECRAFT)):(ClassNode)installMc.invoke(null,finals.get(MINECRAFT));
        check(inspectMinecraft(raw.get(MINECRAFT),composedMc)!=null,"compiled wind wrapper composed with actual Sable/Veil Minecraft export");
        MethodNode moved=composedMc.methods.stream().filter(m->m.name.startsWith("runTick$mixinextras$wrapped$")).findFirst().orElseThrow();
        moved.instructions.insert(new InsnNode(Opcodes.NOP));check(inspectMinecraft(raw.get(MINECRAFT),composedMc)==null,"composed native-body mutation refused");
        var installPlayer=fixtures.getDeclaredMethod("installedPlayer",ClassNode.class);installPlayer.setAccessible(true);
        ClassNode windPlayer=(ClassNode)installPlayer.invoke(null,raw.get(LOCAL_PLAYER)),composedPlayer=copy(finals.get(LOCAL_PLAYER));
        if(!actualWind)composedPlayer.methods.add(method(windPlayer,TRAVEL,TRAVEL_DESC));
        check(localPlayerTransformationSupported(raw.get(LOCAL_PLAYER),composedPlayer),"compiled wind travel composed with actual Sable player and getter export");
        for(String owner:externalOwners())try(var in=WindPressureLiftSableProjectionRegressionTest.class.getClassLoader().getResourceAsStream(owner+".class")) {
            check(in!=null,"external API resource exists: "+owner);ClassNode api=read(in.readAllBytes());
            check(externalSupported(api),"external original API full-class pin: "+owner);
            ClassNode broken=copy(api);var body=broken.methods.stream().filter(m->m.instructions.size()>0).findFirst();if(body.isPresent())body.get().instructions.insert(new InsnNode(Opcodes.NOP));else broken.methods.getFirst().access^=Opcodes.ACC_SYNTHETIC;
            check(!externalSupported(broken),"external API code modification refused: "+owner);
        }
        check(externalOwners().size()==14,"all fourteen state API/resource contracts are present");
        check(supported(raw),"destructive controls never mutate independent raw oracle");
        System.out.println("PASS WindPressureLiftSableProjectionRegressionTest: "+checks+" checks; actual Sable export projection; wind composition fixtures only, no live-client claim");
    }
    private static ClassNode read(byte[] bytes){var node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static ClassNode copy(ClassNode node){var result=new ClassNode();node.accept(result);return result;}
    private static MethodNode method(ClassNode node,String name,String desc){return node.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    private static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
}
