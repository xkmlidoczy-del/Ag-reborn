package cn.academy.core;
public final class Resources { public static net.minecraft.util.ResourceLocation getTexture(String path){return new net.minecraft.util.ResourceLocation("academy:textures/"+path+".png");}public static net.minecraft.util.ResourceLocation preloadMipmapTexture(String path){return getTexture(path);} }
