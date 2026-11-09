package proof;
import java.util.*;
public final class Trace {
 public static final List<String> rows=new ArrayList<>(); public static int vertices,batches,labels;
 private static int vertexCountdown=-1,allFlushCountdown=-1,typedFlushCountdown=-1;
 public static int injectedFaults;
 public static void add(String s){rows.add(s);} public static String f(float x){return Integer.toHexString(Float.floatToRawIntBits(x));}
 public static void reset(){rows.clear();vertices=batches=labels=0;disarm();injectedFaults=0;}
 public static void arm(int vertex,int allFlush,int typedFlush){vertexCountdown=vertex;allFlushCountdown=allFlush;typedFlushCountdown=typedFlush;}
 public static void disarm(){vertexCountdown=allFlushCountdown=typedFlushCountdown=-1;}
 public static void beforeVertex(){if(vertexCountdown>=0&&vertexCountdown--==0)fail("vertex");}
 public static void beforeAllFlush(){if(allFlushCountdown>=0&&allFlushCountdown--==0)fail("flush-all");}
 public static void beforeTypedFlush(){if(typedFlushCountdown>=0&&typedFlushCountdown--==0)fail("flush-type");}
 private static void fail(String where){injectedFaults++;add("injected:"+where);throw new InjectedFailure(where);}
 public static final class InjectedFailure extends RuntimeException {public InjectedFailure(String where){super(where);}}
}
