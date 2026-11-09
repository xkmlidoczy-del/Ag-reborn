import java.nio.file.*;
import org.objectweb.asm.*;
/** Executable mutations of temporary current bytecode, never production source. */
public final class MutateMeltdowner {
 public static void main(String[] a)throws Exception {
  ClassReader reader=new ClassReader(Files.readAllBytes(Path.of(a[0])));ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] edits={0};
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
   public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
    String emitted=desc;
    if(a[2].equals("host-signature")&&name.equals("getConnection")&&desc.equals("()Lnet/minecraft/client/multiplayer/ClientPacketListener;")){emitted="()Ljava/lang/Object;";edits[0]++;}
    return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,emitted,signature,exceptions)){
     public void visitCode(){super.visitCode();if(a[2].equals("rng")&&name.equals("renderRay")&&desc.endsWith("Lorg/joml/Matrix4f;)V")){
      super.visitFieldInsn(Opcodes.GETSTATIC,"cn/academy/port/client/ClassicMeltdownerBeamEffects","RANDOM","Ljava/util/Random;");super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/util/Random","nextLong","()J",false);super.visitInsn(Opcodes.POP2);edits[0]++;
     }}
     public void visitFieldInsn(int opcode,String owner,String field,String descriptor){
      if(a[2].equals("coordinate")&&name.equals("vertex")&&owner.equals("net/minecraft/world/phys/Vec3")&&field.equals("x")){field="z";edits[0]++;}
      if(a[2].equals("state")&&name.equals("type")&&field.equals("COLOR_DEPTH_WRITE")){field="COLOR_WRITE";edits[0]++;}
      super.visitFieldInsn(opcode,owner,field,descriptor);
     }
     public void visitVarInsn(int opcode,int index){
      if(a[2].equals("uv")&&name.equals("vertex")&&opcode==Opcodes.FLOAD&&index==3){index=4;edits[0]++;}
      if(a[2].equals("scope")&&name.equals("renderRay")&&desc.endsWith("Lorg/joml/Matrix4f;Z)V")&&opcode==Opcodes.ILOAD&&index==4){super.visitInsn(Opcodes.ICONST_0);edits[0]++;return;}
      super.visitVarInsn(opcode,index);
     }
     public void visitMethodInsn(int opcode,String owner,String name2,String desc2,boolean itf){
      if(a[2].equals("camera")&&name.equals("renderRay")&&desc.endsWith("Lorg/joml/Matrix4f;Z)V")&&owner.equals("net/minecraft/world/phys/Vec3")&&name2.equals("subtract")){name2="add";edits[0]++;}
      super.visitMethodInsn(opcode,owner,name2,desc2,itf);
     }
    };
   }
  },0);
  if(edits[0]!=1)throw new AssertionError("Mutation must hit exactly one instruction: "+edits[0]);
  Files.createDirectories(Path.of(a[1]).getParent());Files.write(Path.of(a[1]),writer.toByteArray());System.out.println("MUTATION "+a[2]+" edits="+edits[0]);
 }
}
