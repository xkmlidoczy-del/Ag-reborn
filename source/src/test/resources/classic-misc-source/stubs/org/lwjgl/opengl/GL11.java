package org.lwjgl.opengl;
public class GL11 {public static int GL_CULL_FACE=1,GL_BLEND=2,GL_SRC_ALPHA=3,GL_ONE_MINUS_SRC_ALPHA=4;
public static void glPushMatrix(){oracle.Capture.push();}public static void glPopMatrix(){oracle.Capture.pop();}
public static void glDisable(int s){oracle.Capture.calls.add("disable:"+s);}public static void glEnable(int s){oracle.Capture.calls.add("enable:"+s);}public static void glBlendFunc(int a,int b){oracle.Capture.calls.add("blend:"+a+":"+b);}
public static void glTranslated(double x,double y,double z){oracle.Capture.translate(x,y,z);}public static void glRotated(double angle,double x,double y,double z){oracle.Capture.rotate(angle,x,y,z);}}

