package com.mojang.blaze3d.systems;public class RenderSystem {
 private static final float[] color={1,1,1,1};public static float[] getShaderColor(){return color;}public static void setShaderColor(float r,float g,float b,float a){color[0]=r;color[1]=g;color[2]=b;color[3]=a;}
}
