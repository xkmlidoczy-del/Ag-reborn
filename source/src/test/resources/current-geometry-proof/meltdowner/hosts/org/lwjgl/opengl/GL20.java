package org.lwjgl.opengl;public class GL20 {
 public static final int GL_LINK_STATUS=35714,GL_DELETE_STATUS=35712;public static String inactive="";
 public static boolean glIsProgram(int id){var p=net.minecraft.client.renderer.ShaderInstance.programs.get(id);return p!=null&&!p.closed;}
 public static int glGetProgrami(int id,int what){var p=net.minecraft.client.renderer.ShaderInstance.programs.get(id);return p==null?0:what==GL_DELETE_STATUS?(p.closed||p.deletePending?1:0):(p.linked?1:0);}
 public static int glGetUniformLocation(int id,CharSequence name){return name.toString().equals(inactive)?-1:1;}
}
