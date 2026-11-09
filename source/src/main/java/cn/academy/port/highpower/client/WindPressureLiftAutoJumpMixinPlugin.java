package cn.academy.port.highpower.client;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.service.MixinService;

/** Optional client getter admission. Missing/changed native signatures keep NEW443 free and unavailable. */
public final class WindPressureLiftAutoJumpMixinPlugin implements IMixinConfigPlugin {
    public static final String TARGET = "net/minecraft/client/player/LocalPlayer";
    public static final String ACCESSOR = "cn/academy/port/client/highpower/mixin/WindPressureLiftAutoJumpAccessor";
    public static final String GETTER = "academy$windPressureLiftAutoJumpTime";
    private static volatile boolean enabled;
    private boolean registered, admitted;
    private ClassNode before;
    public static boolean available() { return enabled; }
    public static boolean supported(ClassNode node) {
        if (node == null || !TARGET.equals(node.name) || !privateField(node)
                || node.methods.stream().anyMatch(m -> m.name.equals(GETTER))) return false;
        int writes = 0;
        for (MethodNode m : node.methods) for (AbstractInsnNode i : m.instructions)
            if (i instanceof FieldInsnNode f && f.owner.equals(TARGET) && f.name.equals("autoJumpTime")) {
                if (!f.desc.equals("I") || f.getOpcode() != Opcodes.GETFIELD && f.getOpcode() != Opcodes.PUTFIELD) return false;
                if (f.getOpcode() == Opcodes.PUTFIELD) {
                    if (!(m.name.equals("aiStep") && m.desc.equals("()V") && m.access == Opcodes.ACC_PUBLIC)
                            && !(m.name.equals("updateAutoJump") && m.desc.equals("(FF)V") && m.access == Opcodes.ACC_PROTECTED)) return false;
                    writes++;
                }
            }
        return writes == 2;
    }
    private static boolean privateField(ClassNode node) {
        return node.fields.stream().filter(f -> f.name.equals("autoJumpTime")).count() == 1
                && node.fields.stream().anyMatch(f -> f.name.equals("autoJumpTime") && f.desc.equals("I") && f.access == Opcodes.ACC_PRIVATE);
    }
    /** Exact final bytecode witness, including an unchanged private native field and one getter only. */
    public static boolean getterSupported(ClassNode node) {
        if (node == null || !TARGET.equals(node.name) || !privateField(node) || !node.interfaces.contains(ACCESSOR)) return false;
        List<MethodNode> getters = node.methods.stream().filter(m -> m.name.equals(GETTER)).toList();
        if (getters.size()!=1) return false;
        MethodNode getter=getters.getFirst();
        if (!getter.desc.equals("()I") || (getter.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != Opcodes.ACC_PUBLIC
                || !getter.tryCatchBlocks.isEmpty()) return false;
        var code=new ArrayList<AbstractInsnNode>();for(var instruction:getter.instructions) if(instruction.getOpcode()>=0)code.add(instruction);
        return code.size()==3 && code.get(0) instanceof VarInsnNode load && load.getOpcode()==Opcodes.ALOAD && load.var==0
                && code.get(1) instanceof FieldInsnNode field && field.getOpcode()==Opcodes.GETFIELD
                && field.owner.equals(TARGET) && field.name.equals("autoJumpTime") && field.desc.equals("I")
                && code.get(2).getOpcode()==Opcodes.IRETURN;
    }
    /** Compare against the actual pre-accessor tree, without changing frozen upstream class hashes. */
    public static boolean transformationSupported(ClassNode original,ClassNode transformed) {
        if(!supported(original) || !getterSupported(transformed) || original.access!=transformed.access
                || !original.superName.equals(transformed.superName) || original.fields.size()!=transformed.fields.size()
                || transformed.methods.size()!=original.methods.size()+1) return false;
        Map<String,String> oldFields=fields(original),newFields=fields(transformed);
        if(!oldFields.equals(newFields)) return false;
        var oldInterfaces=new java.util.HashSet<>(original.interfaces);var newInterfaces=new java.util.HashSet<>(transformed.interfaces);
        newInterfaces.remove(ACCESSOR);if(!oldInterfaces.equals(newInterfaces))return false;
        Map<String,String> methods=methods(original),after=methods(transformed);after.remove(GETTER+"()I");
        return methods.size()==original.methods.size() && after.size()==original.methods.size() && methods.equals(after);
    }
    private static Map<String,String> fields(ClassNode node) {
        var result=new HashMap<String,String>();
        for(var field:node.fields)result.put(field.name+field.desc,field.access+"|"+field.signature+"|"+field.value);
        return result;
    }
    private static Map<String,String> methods(ClassNode node) {
        var result=new HashMap<String,String>();for(var method:node.methods)result.put(method.name+method.desc,fingerprint(method));return result;
    }
    private static String fingerprint(MethodNode original) {
        MethodNode m=new MethodNode(Opcodes.ASM9,original.access,original.name,original.desc,original.signature,original.exceptions.toArray(String[]::new));original.accept(m);
        for(var i:m.instructions.toArray()) if(i instanceof FrameNode || i instanceof LineNumberNode)m.instructions.remove(i);
        m.localVariables=null;m.visibleLocalVariableAnnotations=null;m.invisibleLocalVariableAnnotations=null;
        ClassWriter writer=new ClassWriter(0);writer.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,"Witness",null,"java/lang/Object",null);
        m.accept(writer);writer.visitEnd();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(writer.toByteArray()));}
        catch(java.security.NoSuchAlgorithmException unavailable){throw new IllegalStateException(unavailable);}
    }
    @Override public void onLoad(String mixinPackage) {
        enabled=false;
        try {
            Object transformer=MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
            if(transformer instanceof IMixinTransformer mixin && mixin.getExtensions() instanceof Extensions extensions) {
                extensions.add(new FinalWitness());registered=true;
            }
        } catch(RuntimeException | LinkageError unavailable) {registered=false;}
    }
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        if(!registered || !TARGET.replace('/','.').equals(target) || !ACCESSOR.replace('/','.').equals(mixin))return false;
        try {admitted=supported(MixinService.getService().getBytecodeProvider().getClassNode(target));}
        catch(Exception | LinkageError unavailable){admitted=false;}
        return admitted;
    }
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {
        enabled=false;before=null;
        if(admitted && TARGET.replace('/','.').equals(target) && ACCESSOR.replace('/','.').equals(mixin) && supported(node)) {
            before=new ClassNode();node.accept(before);
        }
    }
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {enabled=false;}
    private final class FinalWitness implements IExtension {
        public boolean checkActive(MixinEnvironment environment){return true;}
        public void preApply(ITargetClassContext context){}
        public void postApply(ITargetClassContext context){}
        public void export(MixinEnvironment environment,String name,boolean force,ClassNode node) {
            if(!TARGET.equals(name.replace('.','/')))return;
            enabled=admitted && transformationSupported(before,node);
        }
    }
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
}
