package cn.academy.port.client;

import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Discovers all compiled mod entity registrations, then verifies their subscribed renderer factories. */
public final class EntityRendererRegistrationRegressionTest {
    private static final String ROOT="cn/academy/port/", HOLDER="net/neoforged/neoforge/registries/DeferredHolder",
            REGISTER="net/neoforged/neoforge/registries/DeferredRegister",
            EVENT="net/neoforged/neoforge/client/event/EntityRenderersEvent$RegisterRenderers";
    private static final Pattern ENTITY=Pattern.compile("EntityType<L([^;]+);>");
    private static final Map<String,String> EXPECTED=Map.of(
            "displaced_needle","DisplacedNeedleRenderer", "cross_needle","DisplacedNeedleRenderer",
            "interception_needle","DisplacedNeedleRenderer", "corridor_needle","DisplacedNeedleRenderer",
            "borrowed_needle","BorrowedNeedleRenderer",
            "maghook","ClassicHookRenderer",
            "magnetic_block","ClassicMagneticBlockRenderer", "silbarn","ClassicSilbarnRenderer");
    private record Registration(String field,String path,String entity) {}
    private record InheritedRenderer(String entity,String entityBase,String renderer,String rendererBase,String rendererBaseSuper) {}
    private static final Map<String,InheritedRenderer> INHERITED=Map.of(
            "liquid_shadow_body",new InheritedRenderer(ROOT+"canon/CanonLiquidEntities$Shadow",
                    "net/minecraft/world/entity/decoration/ArmorStand",ROOT+"client/CanonLiquidClient$ShadowRenderer",
                    "net/minecraft/client/renderer/entity/ArmorStandRenderer","net/minecraft/client/renderer/entity/LivingEntityRenderer"),
            "urban_calibrator",new InheritedRenderer(ROOT+"story/UrbanCalibrator$Boss",
                    "net/minecraft/world/entity/animal/IronGolem",ROOT+"client/UrbanCalibratorClient$Renderer",
                    "net/minecraft/client/renderer/entity/IronGolemRenderer","net/minecraft/client/renderer/entity/MobRenderer"));
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        var classes=classes();var entities=entities(classes);var providers=providers(classes,entities.keySet());
        check(!entities.isEmpty(),"compiled entity registry discovery is nonempty");
        check(entities.keySet().equals(providers.keySet()),"every actual deferred entity has one subscribed renderer; missing="
                + difference(entities.keySet(),providers.keySet())+", unexpected="+difference(providers.keySet(),entities.keySet()));
        var byPath=new HashMap<String,Registration>();
        for(var entity:entities.values()) {
            check(byPath.put(entity.path,entity)==null,"distinct EntityTypes cannot share registry ID "+entity.path);
            var found=providers.get(entity.field);
            check(found!=null&&found.size()==1,"exactly one factory registered for "+entity.path);
            var renderer=classes.get(found.getFirst());
            var inherited=INHERITED.get(entity.path);
            if(inherited==null) {
                // EntityRenderer and MobRenderer bind the entity as the first superclass parameter.
                check(renderer!=null&&renderer.signature!=null
                                &&renderer.signature.startsWith("L"+renderer.superName+"<L"+entity.entity+";"),
                        "renderer generic entity type matches actual deferred type for "+entity.path);
            } else {
                var body=classes.get(entity.entity);
                var base=nativeClass(inherited.rendererBase);
                check(body!=null&&renderer!=null&&base!=null&&compatible(inherited,body.name,body.superName,
                        renderer.name,renderer.superName,base.superName,base.signature),
                        "renderer accepts actual deferred entity through verified vanilla parent for "+entity.path);
                check(!compatible(inherited,body.name,"invalid/Entity",renderer.name,renderer.superName,base.superName,base.signature),
                        "wrong entity parent rejected for "+entity.path);
                check(!compatible(inherited,body.name,body.superName,renderer.name,"invalid/Renderer",base.superName,base.signature),
                        "wrong renderer parent rejected for "+entity.path);
                check(!compatible(inherited,body.name,body.superName,renderer.name,renderer.superName,base.superName,
                        "L"+inherited.rendererBaseSuper+"<Linvalid/Entity;>;"),
                        "wrong vanilla generic entity rejected for "+entity.path);
            }
            if(EXPECTED.containsKey(entity.path))check(found.getFirst().equals(ROOT+"client/"+EXPECTED.get(entity.path)),
                    "retained renderer identity for "+entity.path);
        }
        check(byPath.keySet().containsAll(EXPECTED.keySet()),"all known physical mod entity IDs remain registered");
        check(byPath.keySet().containsAll(INHERITED.keySet()),"both inherited vanilla renderer entities remain registered");
        check(Set.of(byPath.get("displaced_needle").field,byPath.get("cross_needle").field,byPath.get("interception_needle").field).size()==3,
                "NEW311/321/331 retain three distinct physical entity registrations");
        check(Set.of(byPath.get("displaced_needle").field,byPath.get("cross_needle").field,
                byPath.get("interception_needle").field,byPath.get("corridor_needle").field).size()==4,
                "NEW341 appends a fourth distinct physical entity registration");
        check(!byPath.get("borrowed_needle").entity.equals(byPath.get("corridor_needle").entity),
                "NEW351 escrow lease is a separate entity class, never old persistent item custody");
        var missingBorrowed=new HashMap<>(providers);missingBorrowed.remove(byPath.get("borrowed_needle").field);
        check(!entities.keySet().equals(missingBorrowed.keySet())&&difference(entities.keySet(),missingBorrowed.keySet()).size()==1,
                "missing phase-one borrowed needle renderer is detected before client testing");
        var missingCorridor=new HashMap<>(providers);missingCorridor.remove(byPath.get("corridor_needle").field);
        check(!entities.keySet().equals(missingCorridor.keySet())&&difference(entities.keySet(),missingCorridor.keySet()).size()==1,
                "missing341 renderer is detected while all311/321/331 factories remain");
        // Regression witness: removing only331's production registration reproduces the renderer-map hole.
        var missing=new HashMap<>(providers);missing.remove(byPath.get("interception_needle").field);
        check(!entities.keySet().equals(missing.keySet())&&difference(entities.keySet(),missing.keySet()).size()==1,
                "missing331 renderer is detected even when311/321 factories remain");
        System.out.println("EntityRendererRegistrationRegressionTest: "+checks+" checks passed for "+entities.size()+" actual compiled EntityTypes");
    }
    private static boolean compatible(InheritedRenderer expected,String entity,String entitySuper,String renderer,
                                      String rendererSuper,String nativeSuper,String nativeSignature) {
        return expected.entity.equals(entity) && expected.entityBase.equals(entitySuper)
                && expected.renderer.equals(renderer) && expected.rendererBase.equals(rendererSuper)
                && expected.rendererBaseSuper.equals(nativeSuper) && nativeSignature!=null
                && nativeSignature.startsWith("L"+expected.rendererBaseSuper+"<L"+expected.entityBase+";");
    }
    /** Read the actual vanilla superclass from this test's runtime classpath, never a stub. */
    private static ClassNode nativeClass(String name)throws Exception {
        try(var in=EntityRendererRegistrationRegressionTest.class.getClassLoader().getResourceAsStream(name+".class")) {
            if(in==null)throw new AssertionError("Missing actual vanilla renderer class "+name);
            var node=new ClassNode();new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            check(node.name.equals(name),"runtime vanilla renderer class identity "+name);return node;
        }
    }
    private static Set<String> difference(Set<String> a,Set<String> b){var result=new java.util.HashSet<>(a);result.removeAll(b);return result;}
    private static Map<String,Registration> entities(Map<String,ClassNode> classes) {
        var result=new LinkedHashMap<String,Registration>();
        for(var owner:classes.values()) {
            var typed=new HashMap<String,String>();var registries=new java.util.HashSet<String>();
            for(var field:owner.fields) {
                if(field.signature==null)continue;
                if(field.desc.equals("L"+HOLDER+";")&&field.signature.contains("Lnet/minecraft/world/entity/EntityType<")) {
                    var match=ENTITY.matcher(field.signature);
                    check(match.find(),"concrete entity type retained in "+owner.name+"."+field.name);typed.put(field.name,match.group(1));
                }
                if(field.desc.equals("L"+REGISTER+";")&&field.signature.contains("Lnet/minecraft/world/entity/EntityType<"))registries.add(field.name);
            }
            if(typed.isEmpty())continue;
            var initialized=new java.util.HashSet<String>();
            for(var method:owner.methods)if(method.name.equals("<clinit>")) {
                String registry=null,path=null,registered=null;
                for(var op:method.instructions) {
                    if(op instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&f.owner.equals(owner.name)&&registries.contains(f.name)) {
                        registry=f.name;path=null;registered=null;
                    } else if(op instanceof LdcInsnNode constant&&registry!=null&&constant.cst instanceof String text) path=text;
                    else if(op instanceof MethodInsnNode call&&call.owner.equals(REGISTER)&&call.name.equals("register")) {
                        registered=registry!=null?path:null;registry=null;
                    } else if(op instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTSTATIC&&f.owner.equals(owner.name)&&typed.containsKey(f.name)) {
                        check(registered!=null&&!registered.isBlank(),"actual DeferredRegister.register path resolved for "+owner.name+"."+f.name);
                        String key=owner.name+"."+f.name;
                        check(result.put(key,new Registration(key,registered,typed.get(f.name)))==null,"one actual registration for "+key);
                        initialized.add(f.name);registered=null;path=null;
                    }
                }
            }
            check(initialized.equals(typed.keySet()),"every deferred EntityType holder is initialized in "+owner.name);
        }
        return result;
    }
    private static Map<String,List<String>> providers(Map<String,ClassNode> classes,Set<String> entityFields) {
        var result=new LinkedHashMap<String,List<String>>();
        for(var owner:classes.values())for(var method:owner.methods) {
            String field=null,provider=null;
            for(var op:method.instructions) {
                if(op instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.GETSTATIC&&entityFields.contains(f.owner+"."+f.name)) {
                    field=f.owner+"."+f.name;provider=null;
                } else if(op instanceof InvokeDynamicInsnNode lambda&&field!=null) {
                    for(Object arg:lambda.bsmArgs)if(arg instanceof Handle factory&&factory.getTag()==Opcodes.H_NEWINVOKESPECIAL
                            &&factory.getName().equals("<init>"))provider=factory.getOwner();
                } else if(op instanceof MethodInsnNode call&&call.owner.equals(EVENT)&&call.name.equals("registerEntityRenderer")) {
                    check(field!=null&&provider!=null,"actual renderer registration has a resolved entity and constructor in "+owner.name+"."+method.name);
                    check(annotation(method.visibleAnnotations,"Lnet/neoforged/bus/api/SubscribeEvent;")!=null
                            &&method.desc.equals("(L"+EVENT+";)V"),"renderer callback is actually subscribed to RegisterRenderers");
                    var subscriber=annotation(owner.visibleAnnotations,"Lnet/neoforged/fml/common/EventBusSubscriber;");
                    check(subscriber!=null&&enumValue(subscriber.values,"Lnet/neoforged/api/distmarker/Dist;","CLIENT")
                            &&enumValue(subscriber.values,"Lnet/neoforged/fml/common/EventBusSubscriber$Bus;","MOD"),
                            "renderer callback runs on the CLIENT mod event bus");
                    result.computeIfAbsent(field,ignored->new ArrayList<>()).add(provider);field=null;provider=null;
                }
            }
        }
        return result;
    }
    private static AnnotationNode annotation(List<AnnotationNode> values,String descriptor) {
        return values==null?null:values.stream().filter(a->a.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static boolean enumValue(Object value,String descriptor,String name) {
        if(value instanceof String[] entry)return entry.length==2&&entry[0].equals(descriptor)&&entry[1].equals(name);
        if(value instanceof List<?> list)return list.stream().anyMatch(item->enumValue(item,descriptor,name));
        return false;
    }
    /** Uses the actual production output directory or JAR selected by this test's runtime classpath. */
    private static Map<String,ClassNode> classes()throws Exception {
        var result=new LinkedHashMap<String,ClassNode>();String anchor=ROOT+"AcademyCraft.class";
        var resource=EntityRendererRegistrationRegressionTest.class.getClassLoader().getResource(anchor);
        check(resource!=null,"actual main output is on the runtime classpath");
        if(resource.getProtocol().equals("file")) {
            Path base=Path.of(resource.toURI());for(int i=0;i<anchor.split("/").length;i++)base=base.getParent();
            try(var files=Files.walk(base.resolve(ROOT))) {
                for(var file:files.filter(p->p.toString().endsWith(".class")).sorted().toList())read(result,Files.readAllBytes(file));
            }
        } else if(resource.getProtocol().equals("jar")) {
            var connection=(JarURLConnection)resource.openConnection();connection.setUseCaches(false);
            try(var jar=connection.getJarFile()) {
                for(var entry:jar.stream().filter(e->e.getName().startsWith(ROOT)&&e.getName().endsWith(".class")).toList())
                    try(var in=jar.getInputStream(entry)){read(result,in.readAllBytes());}
            }
        } else throw new AssertionError("Unsupported production class location: "+resource.getProtocol());
        return result;
    }
    private static void read(Map<String,ClassNode> result,byte[] bytes) {
        var node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        // The optional showcase source set installs a counting wrapper at runtime for
        // evidence capture. It is excluded from the shipped JAR and is not a production factory.
        if(!node.name.startsWith(ROOT+"qa/"))result.put(node.name,node);
    }
}
