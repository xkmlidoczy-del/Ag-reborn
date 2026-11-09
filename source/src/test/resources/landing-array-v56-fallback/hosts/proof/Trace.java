package proof;
import java.util.*;
public final class Trace {
 public static final List<String> rows=new ArrayList<>(); public static int vertices,batches;
 public static void add(String s){rows.add(s);} public static String f(float x){return Integer.toHexString(Float.floatToRawIntBits(x));}
 public static void reset(){rows.clear();vertices=batches=0;}
}
