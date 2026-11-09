package cn.academy.port.highpower.client;

import static cn.academy.port.highpower.client.WindPressureLiftTravelAdmission.*;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** A projection of the reviewed Sable/Veil weave back to its unchanged native instruction stream.
 * It never edits a live ClassNode. Every removed call has a pinned author body, a precise operand
 * pattern, and the remaining native method must equal the independent raw oracle. */
public final class WindPressureLiftSableProjection {
    private static final Pattern GENERATED=Pattern.compile("^(handler|redirect|localvar|modify|modifyReturnValue|wrapOperation)\\$[a-z0-9]+\\$(sable|veil)\\$(.+)$");
    private static final String PROFILE="wind-pressure-sable-2.0.6.properties";
    private static final Properties RULES=load();
    private static final Set<String> TARGETS=Set.of(MINECRAFT,LOCAL_PLAYER);
    private static final String REF="com/llamalad7/mixinextras/sugar/impl/ref/generated/LocalRefImpl";
    private WindPressureLiftSableProjection(){}
    private static Properties load(){var p=new Properties();try(InputStream in=WindPressureLiftSableProjection.class.getClassLoader().getResourceAsStream(PROFILE)){if(in!=null)p.load(in);}catch(Exception ignored){}return p;}
    public static boolean hasWeave(ClassNode node){return node!=null && node.methods.stream().anyMatch(m->origin(m).startsWith("dev.ryanhcode.sable.")||origin(m).startsWith("foundry.veil."));}
    public static String canonicalName(String name){
        var m=GENERATED.matcher(name);
        if(m.matches())return (m.group(1)+"$"+m.group(2)+"$"+m.group(3)).replaceFirst("\\$mixinextras\\$bridge\\$[0-9]+$","\\$mixinextras\\$bridge");
        return name.replaceFirst("^md[0-9a-f]{6}\\$sable\\$(lambda\\$.*)$","md\\$sable\\$$1");
    }
    public static String origin(MethodNode method){if(method.visibleAnnotations!=null)for(var a:method.visibleAnnotations)if(a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")&&a.values!=null)for(int i=0;i+1<a.values.size();i+=2)if(a.values.get(i).equals("mixin"))return String.valueOf(a.values.get(i+1));return "";}
    public static String key(String owner,MethodNode m){return owner+"#"+canonicalName(m.name)+m.desc;}
    public static String canonicalFingerprint(ClassNode owner,MethodNode original){
        try {
        ClassNode n=new ClassNode();n.version=owner.version;n.access=owner.access;n.name=owner.name;n.superName=owner.superName;
        MethodNode m=new MethodNode(Opcodes.ASM9,original.access,original.name,original.desc,original.signature,original.exceptions.toArray(String[]::new));original.accept(m);
        m.name=canonicalName(m.name);
        if(m.visibleAnnotations!=null)m.visibleAnnotations.removeIf(a->a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;"));
        for(var op:m.instructions) {
            if(op instanceof MethodInsnNode call)call.name=canonicalName(call.name);
            if(op instanceof InvokeDynamicInsnNode dynamic){dynamic.bsmArgs=dynamic.bsmArgs.clone();for(int i=0;i<dynamic.bsmArgs.length;i++)if(dynamic.bsmArgs[i] instanceof org.objectweb.asm.Handle h)
                dynamic.bsmArgs[i]=new org.objectweb.asm.Handle(h.getTag(),h.getOwner(),canonicalName(h.getName()),h.getDesc(),h.isInterface());}
        }
        // Mixin keeps conservative maxima in its live AST; ClassWriter later computes the
        // actual values. Compute those same bounds from the exact pinned instruction stream.
        // A frame-free legacy witness makes COMPUTE_MAXS use instruction/control-flow analysis
        // for both live and serialized trees; this diagnostic class is never executed.
        for(var instruction:m.instructions.toArray())if(instruction instanceof FrameNode||instruction instanceof LineNumberNode)m.instructions.remove(instruction);
        m.maxStack=0;m.maxLocals=0;n.version=Opcodes.V1_6;n.methods.add(m);
        var writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);n.accept(writer);
        var computed=new ClassNode();new org.objectweb.asm.ClassReader(writer.toByteArray()).accept(computed,0);
        return fingerprint(computed,m.name,m.desc);
        } catch(RuntimeException invalid){return "unreviewed";}
    }
    public static boolean helperSupported(ClassNode owner,MethodNode m){String k=key(owner.name,m);return !origin(m).isEmpty()&&origin(m).equals(RULES.getProperty("origin."+k))&&canonicalFingerprint(owner,m).equals(RULES.getProperty("helper."+k));}
    /** Null means the weave was not proved. Raw-only classes keep the original strict path. */
    public static ClassNode project(ClassNode raw,ClassNode transformed){
        if(raw==null||transformed==null||!classSupported(raw)||!raw.name.equals(transformed.name))return null;
        if(!hasWeave(transformed))return transformed;
        ClassNode out=copy(transformed);Set<String> helpers=new HashSet<>();
        for(MethodNode m:out.methods)if(RULES.containsKey("helper."+key(out.name,m))||GENERATED.matcher(m.name).matches()||origin(m).startsWith("dev.ryanhcode.sable.")||origin(m).startsWith("foundry.veil.")){
            if(!helperSupported(out,m))return null;helpers.add(m.name+m.desc);
        }
        for(MethodNode m:new ArrayList<>(out.methods)){
            MethodNode base=unique(raw,m.name,m.desc);
            if(base==null && MINECRAFT.equals(raw.name)&&m.name.matches("runTick\\$mixinextras\\$wrapped\\$[0-9]+")){
                if(!peel(out,m))return null;continue;
            }
            if(base==null)continue;
            if(fingerprint(raw,base.name,base.desc).equals(fingerprint(out,m.name,m.desc)))continue;
            if(MINECRAFT.equals(raw.name)&&m.name.equals(RUN_TICK)&&callsOurRun(m))continue;
            if(reviewedMethods().contains(raw.name+"#"+m.name+m.desc)){
                // The profile pins the known Sable operand spill maxima as well as its helper implementations.
                if(!canonicalFingerprint(out,m).equals(RULES.getProperty("native."+key(out.name,m))))return null;
                if(!peel(out,m))return null;
                m.maxStack=base.maxStack;m.maxLocals=base.maxLocals;
                if(!fingerprint(raw,base.name,base.desc).equals(fingerprint(out,m.name,m.desc)))return null;
            }else if(TARGETS.contains(raw.name)){
                // Non-travel constructor/render/keybind changes are exact reviewed author code, not wildcards.
                if(!canonicalFingerprint(out,m).equals(RULES.getProperty("native."+key(out.name,m))))return null;
                out.methods.set(out.methods.indexOf(m),cloneMethod(base));
            }
        }
        if(TARGETS.contains(raw.name)){
            out.methods.removeIf(m->helpers.contains(m.name+m.desc));
            for(FieldNode f:new ArrayList<>(out.fields)) {
                FieldNode base=raw.fields.stream().filter(x->x.name.equals(f.name)&&x.desc.equals(f.desc)).findFirst().orElse(null);
                if(base!=null&&fieldFingerprint(base).equals(fieldFingerprint(f)))continue;
                String k=out.name+"#"+f.name+f.desc;
                if(!fieldFingerprint(f).equals(RULES.getProperty("field."+k)))return null;
                if(base==null)out.fields.remove(f);else out.fields.set(out.fields.indexOf(f),base);
            }
            for(String iface:new ArrayList<>(out.interfaces))if(iface.equals("dev/ryanhcode/sable/mixinterface/player_freezing/PlayerFreezeExtension")){
                out.interfaces.remove(iface);if(out.signature!=null)out.signature=out.signature.replace("L"+iface+";","");
            }
        }
        return out;
    }
    private static boolean callsOurRun(MethodNode m){for(var op:m.instructions)if(op instanceof MethodInsnNode c&&c.name.matches("wrapMethod\\$[a-z0-9]+\\$academy\\$windLiftRun"))return true;return false;}
    public static String fieldFingerprint(FieldNode original){
        var holder=new ClassNode();holder.version=Opcodes.V21;holder.access=Opcodes.ACC_PUBLIC;holder.name="FieldCopy";holder.superName="java/lang/Object";holder.fields.add(original);
        var copied=new ClassNode();holder.accept(copied);FieldNode f=copied.fields.getFirst();
        if(f.visibleAnnotations!=null)for(var annotation:f.visibleAnnotations)if(annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")&&annotation.values!=null)
            for(int i=0;i+1<annotation.values.size();i+=2)if(annotation.values.get(i).equals("sessionId"))annotation.values.set(i+1,"");
        var n=new ClassNode();n.version=Opcodes.V21;n.access=Opcodes.ACC_PUBLIC;n.name="WindLiftSableFieldWitness";n.superName="java/lang/Object";n.fields.add(f);return classFingerprint(n);}
    public static Set<String> externalOwners(){var out=new HashSet<String>();for(String key:RULES.stringPropertyNames())if(key.startsWith("external."))out.add(key.substring(9));return Set.copyOf(out);}
    public static boolean externalSupported(ClassNode node){return node!=null&&classFingerprint(node).equals(RULES.getProperty("external."+node.name));}
    private static MethodNode cloneMethod(MethodNode m){var c=new MethodNode(Opcodes.ASM9,m.access,m.name,m.desc,m.signature,m.exceptions.toArray(String[]::new));m.accept(c);return c;}
    private static ClassNode copy(ClassNode n){var out=new ClassNode();n.accept(out);return out;}
    private static MethodNode unique(ClassNode n,String name,String desc){MethodNode found=null;for(var m:n.methods)if(m.name.equals(name)&&m.desc.equals(desc)){if(found!=null)return null;found=m;}return found;}
    private static List<AbstractInsnNode> code(MethodNode m){var l=new ArrayList<AbstractInsnNode>();for(var op:m.instructions)if(op.getOpcode()>=0)l.add(op);return l;}
    private static boolean var(AbstractInsnNode n,int op,int slot){return n instanceof VarInsnNode v&&v.getOpcode()==op&&v.var==slot;}
    private static boolean call(AbstractInsnNode n,int op,String owner,String name,String desc){return n instanceof MethodInsnNode c&&c.getOpcode()==op&&c.owner.equals(owner)&&c.name.equals(name)&&c.desc.equals(desc)&&!c.itf;}
    private static boolean exact(List<AbstractInsnNode> c,int at,int[][] pattern){if(at<0||at+pattern.length>c.size())return false;for(int j=0;j<pattern.length;j++){var n=c.get(at+j);if(pattern[j].length==1){if(n.getOpcode()!=pattern[j][0])return false;}else if(!var(n,pattern[j][0],pattern[j][1]))return false;}return true;}
    private static boolean remove(MethodNode m,List<AbstractInsnNode> c,int start,int end,AbstractInsnNode replacement){
        if(start<0||end>=c.size())return false;
        // Never delete an entry label; existing native branches and handlers must retain their exact destination.
        if(replacement!=null)m.instructions.insertBefore(c.get(start),replacement);
        for(int j=start;j<=end;j++)m.instructions.remove(c.get(j));return true;
    }
    private static boolean peel(ClassNode owner,MethodNode m){
        Map<String,Integer> counts=new HashMap<>();
        for(var insn:m.instructions.toArray())if(insn instanceof MethodInsnNode named){
            String n=canonicalName(named.name);if(!GENERATED.matcher(named.name).matches())continue;
            if(!named.owner.equals(owner.name))return false;
            MethodNode helper=unique(owner,named.name,named.desc);if(helper==null||!helperSupported(owner,helper))return false;
            var c=code(m);int i=c.indexOf(insn);int start=i,end=i;AbstractInsnNode replacement=null;
            switch(n){
                case "handler$veil$beginFrame","handler$veil$endFrame","handler$sable$tickPlotContainer","handler$sable$updateRotLerp","handler$sable$updatePlotPosition","handler$sable$preFrozenTick"->{if(!exact(c,i-2,new int[][]{{Opcodes.ALOAD,0},{Opcodes.ACONST_NULL}}))return false;start=i-2;}
                case "handler$sable$beforeAnimation"->{if(!exact(c,i-3,new int[][]{{Opcodes.ALOAD,0},{Opcodes.ALOAD,1},{Opcodes.ACONST_NULL}}))return false;start=i-3;}
                case "localvar$sable$modifyXDifference"->{if(!exact(c,i-2,new int[][]{{Opcodes.ALOAD,0},{Opcodes.DLOAD,1}})||!var(c.get(i+1),Opcodes.DSTORE,1))return false;start=i-2;end=i+1;}
                case "localvar$sable$modifyZDifference"->{if(!exact(c,i-3,new int[][]{{Opcodes.DSTORE,3},{Opcodes.ALOAD,0},{Opcodes.DLOAD,3}}))return false;start=i-3;}
                case "redirect$sable$modifyFlightDir"->{if(!exact(c,i-9,new int[][]{{Opcodes.DSTORE,18},{Opcodes.DSTORE,16},{Opcodes.DSTORE,14},{Opcodes.ASTORE,13},{Opcodes.ALOAD,0},{Opcodes.ALOAD,13},{Opcodes.DLOAD,14},{Opcodes.DLOAD,16},{Opcodes.DLOAD,18}}))return false;start=i-9;replacement=new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/phys/Vec3","add","(DDD)Lnet/minecraft/world/phys/Vec3;",false);}
                case "redirect$sable$freezeTicking"->{if(!exact(c,i-7,new int[][]{{Opcodes.ISTORE,5},{Opcodes.ISTORE,4},{Opcodes.ASTORE,3},{Opcodes.ALOAD,0},{Opcodes.ALOAD,3},{Opcodes.ILOAD,4},{Opcodes.ILOAD,5}}))return false;start=i-7;replacement=new MethodInsnNode(Opcodes.INVOKEVIRTUAL,LEVEL,"hasChunkAt","(II)Z",false);}
                case "redirect$sable$fixRidingBoundingBox"->{if(!exact(c,i-5,new int[][]{{Opcodes.ASTORE,8},{Opcodes.ASTORE,7},{Opcodes.ALOAD,0},{Opcodes.ALOAD,7},{Opcodes.ALOAD,8}}))return false;start=i-5;replacement=new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/world/phys/AABB","minmax","(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/AABB;",false);}
                case "handler$sable$storeUpDeltaMovement"->{if(!exact(c,i-5,new int[][]{{Opcodes.ALOAD,0},{Opcodes.ALOAD,1},{Opcodes.ACONST_NULL},{Opcodes.ALOAD,7},{Opcodes.ALOAD,8}}))return false;start=i-5;}
                case "redirect$sable$modifyTravelSetDeltaMovement"->{if(!exact(c,i-11,new int[][]{{Opcodes.DSTORE,14},{Opcodes.DSTORE,12},{Opcodes.DSTORE,10},{Opcodes.ASTORE,9},{Opcodes.ALOAD,0},{Opcodes.ALOAD,9},{Opcodes.DLOAD,10},{Opcodes.DLOAD,12},{Opcodes.DLOAD,14},{Opcodes.ALOAD,7},{Opcodes.ALOAD,8}}))return false;start=i-11;replacement=new MethodInsnNode(Opcodes.INVOKEVIRTUAL,PLAYER,"setDeltaMovement","(DDD)V",false);}
                default->{return false;}
            }
            if(counts.merge(n,1,Integer::sum)!=1||!remove(m,c,start,end,replacement))return false;
        }
        if(PLAYER.equals(owner.name)&&m.name.equals(TRAVEL)&&counts.containsKey("handler$sable$storeUpDeltaMovement")){
            var c=code(m);if(c.size()<14)return false;
            for(int k=0;k<2;k++){int at=k*7,slot=k==0?8:7;
                if(!(c.get(at) instanceof TypeInsnNode t)||t.getOpcode()!=Opcodes.NEW||!t.desc.equals(REF)||c.get(at+1).getOpcode()!=Opcodes.DUP
                    ||!call(c.get(at+2),Opcodes.INVOKESPECIAL,REF,"<init>","()V")||!var(c.get(at+3),Opcodes.ASTORE,slot)||!var(c.get(at+4),Opcodes.ALOAD,slot)
                    ||c.get(at+5).getOpcode()!=Opcodes.ACONST_NULL||!call(c.get(at+6),Opcodes.INVOKEVIRTUAL,REF,"init","(Ljava/lang/Object;)V"))return false;
            }
            if(!remove(m,c,0,13,null))return false;
        }
        return true;
    }
}
