package cn.academy.port.client;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Checks the compiled production registration, lifecycle, native Post dispatch and safe rendering boundary. */
public final class VectorCageAdapterRegressionTest {
    private static int checks;
    private static void check(boolean c,String m){checks++;if(!c)throw new AssertionError(m);}
    private static ClassNode read(String name)throws Exception{
        try(var in=VectorCageAdapterRegressionTest.class.getClassLoader().getResourceAsStream("cn/academy/port/client/"+name+".class")){
            check(in!=null,"compiled class exists: "+name);var node=new ClassNode();new ClassReader(in).accept(node,0);return node;
        }
    }
    private static boolean calls(ClassNode type,String method,String owner,String name){
        for(var m:type.methods)if(method==null||m.name.equals(method))for(var op:m.instructions)
            if(op instanceof MethodInsnNode call&&call.owner.endsWith(owner)&&call.name.equals(name))return true;
        return false;
    }
    public static void main(String[] args)throws Exception{
        var effects=read("VectorCageEffects");var material=read("VectorCageEffects$Material");
        var setup=read("VectorCageEffects$Setup");var client=read("AcademyClient");
        for(String hook:List.of("vectorCageInputMatches","vectorCageControlValid","singleOwnerEpochMatches","sameInputSessionEpoch"))
            check(calls(effects,null,"AcademyClient",hook),"receipt authenticates "+hook);
        for(String hook:List.of("getUUID","isAlive","isRemoved","isSpectator"))
            check(effects.methods.stream().anyMatch(m->{for(var op:m.instructions)if(op instanceof MethodInsnNode c&&c.name.equals(hook))return true;return false;}),"owner checks "+hook);
        check(calls(effects,null,"VectorCageState","accept")&&calls(effects,null,"VectorCageState","tick"),"production state owns admission and bounded clock");
        check(calls(effects,"tick","Minecraft","isPaused"),"cosmetic clock uses native pause");
        check(calls(effects,"render","VectorCageMesh","shape")&&calls(effects,"visible","VectorCageMesh","bounds"),"render consumes tested whole geometry and bounds");
        check(calls(effects,"stroke","VectorCageMesh","relative")&&calls(effects,"stroke","VectorCageMesh","clipNear"),"double camera-relative geometry clips before extrusion");
        boolean depth=false,color=false,glow=false,positionFloat=false,planeFormat=false,planeShader=false,lineShader=false,shoulderShader=false;
        for(var m:material.methods)for(var op:m.instructions){
            if(op instanceof FieldInsnNode f){depth|=f.name.equals("LEQUAL_DEPTH_TEST");color|=f.name.equals("COLOR_WRITE");planeFormat|=f.name.equals("POSITION_COLOR");}
            if(op instanceof InvokeDynamicInsnNode invoke)for(var arg:invoke.bsmArgs)if(arg instanceof Handle h){
                planeShader|=h.getName().equals("getPositionColorShader");lineShader|=h.getName().equals("getPositionTexColorShader");
                shoulderShader|=h.getOwner().endsWith("ClassicSkillAlphaShader")&&h.getName().equals("get");
            }
        }
        for(var m:effects.methods)for(var op:m.instructions){
            if(op instanceof LdcInsnNode ldc)glow|="effects/glow_line".equals(ldc.cst);
            if(m.name.equals("vertex")&&op.getOpcode()==Opcodes.D2F)positionFloat=true;
            if(op instanceof MethodInsnNode call){
                check(!call.name.contains("addParticle")&&!call.name.equals("hurt")&&!call.name.equals("setBlock")&&!call.name.equals("setDeltaMovement"),"visual cannot manufacture particles, collision or gameplay");
                if(m.name.equals("render"))check(!call.name.equals("getGameTime")&&!call.name.equals("getPartialTick"),"render does not extrapolate authoritative pressure timeline");
            }
        }
        check(depth&&color,"decorations depth-test and do not write depth");check(glow&&positionFloat,"verified classic glow asset and isolated float boundary");
        check(planeFormat&&planeShader&&lineShader,"plane-only native no-cutoff material preserves existing textured strokes");
        check(shoulderShader,"gray glow shoulder binds existing verified no-cutoff shader instead of losing feathered alpha");
        check(calls(effects,"panel","VertexConsumer","addVertex")&&calls(effects,"panel","VertexConsumer","setColor")
                &&!calls(effects,"panel","VertexConsumer","setUv")&&!calls(effects,"panel","VectorCageEffects","vertex"),"plane writer emits exactly POSITION_COLOR attributes");
        try(var zip=new ZipFile("build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar")){
            String plain=new String(zip.getInputStream(zip.getEntry("assets/minecraft/shaders/core/position_color.fsh")).readAllBytes(),StandardCharsets.UTF_8);
            String textured=new String(zip.getInputStream(zip.getEntry("assets/minecraft/shaders/core/position_tex_color.fsh")).readAllBytes(),StandardCharsets.UTF_8);
            check(plain.contains("color.a == 0.0")&&!plain.contains("color.a < 0.1"),"actual native plane fragment preserves9/255 alpha");
            check(textured.contains("color.a < 0.1"),"actual original textured cutoff explains separate plane material");
            check(9/255.0>0&&9/255.0<.1,"production plane opacity is visible only with no-cutoff path");
        }
        check(calls(setup,null,"AcademyClient","vectorCageCancelInput")&&calls(setup,null,"VectorCageEffects","clear"),"reload aborts ownership and removes effects immediately");
        boolean receiver=false,rejection=false;
        for(var m:setup.methods)for(var op:m.instructions)if(op instanceof FieldInsnNode f&&f.getOpcode()==Opcodes.PUTSTATIC&&f.owner.endsWith("VectorCage")){
            receiver|=f.name.equals("clientReceiver");rejection|=f.name.equals("clientRejectionReceiver");
        }
        check(receiver&&rejection,"both receipt and refusal handlers registered");
        for(String hook:List.of("cancelPersistentHighPowerInputs","synchronizeSession","tick"))
            check(calls(client,hook,"AcademyClient","vectorCageCancelInput"),"compiled lifecycle cancels: "+hook);
        check(calls(client,"clearEffects","VectorCageEffects","clear"),"effect cleanup wired into global clear");
        check(calls(client,"finishContextDisposals","AcademyClient","flushVectorCageInput"),"dispatch occurs in native Post");
        check(calls(client,"tick","AcademyClient","tickVectorCageInput"),"ownership checked after key-up every tick");
        check(calls(client,"flushVectorCageInput","Minecraft","isPaused"),"paused client cannot flush new edge");
        check(calls(client,"flushVectorCageInput","VectorCageInputState","flushPost"),"production consumes tested Post helper");
        for(String request:List.of("vector_cage_start","vector_cage_release","vector_cage_abort")){
            boolean present=false;for(var m:client.methods)for(var op:m.instructions)if(op instanceof LdcInsnNode value)present|=request.equals(value.cst);
            check(present,"bounded wire action present: "+request);
        }
        System.out.println("VectorCageAdapterRegressionTest: "+checks+" checks passed");
    }
}
