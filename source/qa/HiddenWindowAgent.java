import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
/** QA-only: prevent GLFW windows from ever becoming visible or taking focus. */
public final class HiddenWindowAgent {
 public static void premain(String args,Instrumentation inst){
  inst.addTransformer(new ClassFileTransformer(){
   public byte[] transform(ClassLoader loader,String name,Class<?> cls,ProtectionDomain domain,byte[] bytes){
    if(!name.equals("org/lwjgl/glfw/GLFW"))return null;
    try{
     ClassReader r=new ClassReader(bytes);ClassWriter w=new ClassWriter(r,ClassWriter.COMPUTE_MAXS);
     r.accept(new ClassVisitor(Opcodes.ASM8,w){
      public MethodVisitor visitMethod(int access,String method,String desc,String signature,String[] exceptions){
       MethodVisitor mv=super.visitMethod(access,method,desc,signature,exceptions);
       if(method.equals("glfwGetWindowAttrib") && desc.equals("(JI)I") && (access&Opcodes.ACC_NATIVE)==0)
        return new MethodVisitor(Opcodes.ASM8,mv){
         public void visitCode(){
          super.visitCode();super.visitVarInsn(Opcodes.ILOAD,2);super.visitLdcInsn(131073);
          Label original=new Label();super.visitJumpInsn(Opcodes.IF_ICMPNE,original);
          super.visitInsn(Opcodes.ICONST_1);super.visitInsn(Opcodes.IRETURN);
          super.visitLabel(original);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
         }
        };
       // Scripted physical-key state at the GLFW boundary, used only by this opt-in QA agent.
       // The normal KeyMapping, ability input, network and render code still run unchanged.
       if(method.equals("glfwGetKey") && desc.equals("(JI)I") && (access&Opcodes.ACC_NATIVE)==0)
        return new MethodVisitor(Opcodes.ASM8,mv){
         public void visitCode(){
          super.visitCode();
          super.visitLdcInsn("academy.qa.key.");super.visitVarInsn(Opcodes.ILOAD,2);
          super.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Integer","toString","(I)Ljava/lang/String;",false);
          super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,"java/lang/String","concat","(Ljava/lang/String;)Ljava/lang/String;",false);
          super.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Boolean","getBoolean","(Ljava/lang/String;)Z",false);
          Label nativePoll=new Label();super.visitJumpInsn(Opcodes.IFEQ,nativePoll);
          super.visitInsn(Opcodes.ICONST_1);super.visitInsn(Opcodes.IRETURN);
          super.visitLabel(nativePoll);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
         }
        };
       if((method.equals("glfwShowWindow")||method.equals("glfwFocusWindow")||method.equals("glfwRestoreWindow")) && desc.equals("(J)V")){
        mv.visitCode();mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,2);mv.visitEnd();return null;
       }
       if(method.equals("nglfwCreateWindow") && (access&Opcodes.ACC_NATIVE)==0)return new MethodVisitor(Opcodes.ASM8,mv){
        public void visitCode(){super.visitCode();super.visitLdcInsn(131076);super.visitInsn(Opcodes.ICONST_0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/glfw/GLFW","glfwWindowHint","(II)V",false);super.visitLdcInsn(131073);super.visitInsn(Opcodes.ICONST_0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/glfw/GLFW","glfwWindowHint","(II)V",false);}
       };
       return mv;
      }
     },0);
     System.out.println("HIDDEN_AGENT: GLFW creation visibility/focus disabled; show/focus/restore suppressed.");return w.toByteArray();
    }catch(Throwable failure){failure.printStackTrace();Runtime.getRuntime().halt(91);return null;}
   }
  });
 }
}
