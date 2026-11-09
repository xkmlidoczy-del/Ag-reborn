package net.minecraft.network.chat;
import java.util.Arrays;
/** Current preview-only extension; the frozen Component host remains untouched. */
public record Component(String key,Object[] args) {
    public static Component translatable(String s,Object...v){return new Component(s,v);}
    public static Component literal(String s){return new Component(s,new Object[0]);}
    public String toString(){return key+Arrays.toString(args);}
}
