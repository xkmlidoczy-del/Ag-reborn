/* AcademyCraft 1.0.7/LWJGL2 physical key identities; GPLv3. See NOTICE. */
package cn.academy.port.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Engine-free GLFW keysym bridge. Common keys retain classic numeric poll order. */
public final class ClassicPhysicalKeys {
    public static final int W=17,A=30,S=31,D=32;
    private static final int KEYSYM_EXTENSION=1024,SCANCODE_EXTENSION=4096;
    private static final Map<Integer,Integer> TO_SOURCE=new LinkedHashMap<>(),TO_GLFW=new LinkedHashMap<>();
    static {
        add(256,1);for(int n=0;n<9;n++)add(49+n,2+n);add(48,11);
        int[][] keys={
            {45,12},{61,13},{259,14},{258,15},{81,16},{87,17},{69,18},{82,19},{84,20},
            {89,21},{85,22},{73,23},{79,24},{80,25},{91,26},{93,27},{257,28},{341,29},
            {65,30},{83,31},{68,32},{70,33},{71,34},{72,35},{74,36},{75,37},{76,38},
            {59,39},{39,40},{96,41},{340,42},{92,43},{90,44},{88,45},{67,46},{86,47},
            {66,48},{78,49},{77,50},{44,51},{46,52},{47,53},{344,54},{332,55},{342,56},
            {32,57},{280,58},{282,69},{281,70},{327,71},{328,72},{329,73},{333,74},
            {324,75},{325,76},{326,77},{334,78},{321,79},{322,80},{323,81},{320,82},
            {330,83},{300,87},{301,88},{302,100},{303,101},{304,102},{335,156},
            {345,157},{331,181},{283,183},{346,184},{284,197},{268,199},{265,200},
            {266,201},{263,203},{262,205},{269,207},{264,208},{267,209},{260,210},
            {261,211},{343,219},{347,220},{348,221}};
        for(int[] pair:keys)add(pair[0],pair[1]);for(int n=0;n<10;n++)add(290+n,59+n);
    }
    private ClassicPhysicalKeys(){}
    private static void add(int glfw,int source){TO_SOURCE.put(glfw,source);TO_GLFW.put(source,glfw);}
    /** Modern-only keysyms have a documented extension ordering, not classic ordering. */
    public static int keysym(int glfw){return glfw<0?0:TO_SOURCE.getOrDefault(glfw,KEYSYM_EXTENSION+glfw);}
    public static int mouse(int button){if(button<0||button>=100)throw new IllegalArgumentException("Mouse button");return button-100;}
    public static int scancode(int scan){return scan<0?0:SCANCODE_EXTENSION+scan;}
    public static int glfw(int source){return source>=KEYSYM_EXTENSION&&source<SCANCODE_EXTENSION?source-KEYSYM_EXTENSION:TO_GLFW.getOrDefault(source,-1);}
    public static int scan(int source){return source>=SCANCODE_EXTENSION?source-SCANCODE_EXTENSION:-1;}
    public static boolean canonical(int source){return source<0&&source>=-100||TO_GLFW.containsKey(source);}
    public static Map<Integer,Integer> supportedKeysyms(){return Collections.unmodifiableMap(TO_SOURCE);}
}
