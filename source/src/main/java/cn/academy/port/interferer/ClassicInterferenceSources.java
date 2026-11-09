/* AcademyCraft1.0.7 CPData transient named IInterfSource map. GPLv3; see NOTICE. */
package cn.academy.port.interferer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
/** Player-state-owned transient sources; never serialize a captured world/player predicate. */
public final class ClassicInterferenceSources {
    private final Map<String,BooleanSupplier> sources=new HashMap<>();
    private boolean managed;
    public void add(String id,BooleanSupplier source){if(id==null||id.isEmpty()||source==null)throw new IllegalArgumentException("Interference source");sources.put(id,source);managed=true;}
    public boolean contains(String id){return sources.containsKey(id);}
    public int size(){return sources.size();}
    public boolean managed(){return managed;}
    public boolean tick(){sources.entrySet().removeIf(entry->!entry.getValue().getAsBoolean());boolean result=!sources.isEmpty();if(!result)managed=false;return result;}
}
