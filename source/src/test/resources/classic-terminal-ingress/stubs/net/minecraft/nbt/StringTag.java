package net.minecraft.nbt;
public final class StringTag extends Tag {private final String value;private StringTag(String v){value=v;}public static StringTag valueOf(String v){return new StringTag(v);}public String getAsString(){return value;}}
