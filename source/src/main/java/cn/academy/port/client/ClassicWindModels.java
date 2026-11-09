/* Unchanged AcademyCraft1.0.7 wind OBJ resources. GPLv3; see NOTICE. */
package cn.academy.port.client;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import com.mojang.logging.LogUtils;
public final class ClassicWindModels implements ResourceManagerReloadListener {
    public static final ClassicWindModels INSTANCE=new ClassicWindModels();
    private volatile Map<String,ClassicDeveloperObj.Mesh> meshes=Map.of();
    private ClassicWindModels(){}
    public ClassicDeveloperObj.Mesh mesh(String name){return meshes.get(name);}
    public static ResourceLocation texture(String name){return ResourceLocation.fromNamespaceAndPath("academy","textures/models/windgen_"+name+".png");}
    @Override public void onResourceManagerReload(ResourceManager resources){var loaded=new java.util.HashMap<String,ClassicDeveloperObj.Mesh>();for(String name:java.util.List.of("base","pillar","main","fan")){var resource=ResourceLocation.fromNamespaceAndPath("academy","models/windgen_"+name+".obj");try(var stream=resources.getResourceOrThrow(resource).open();var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){loaded.put(name,ClassicDeveloperObj.parse(reader));}catch(Exception exception){LogUtils.getLogger().error("Cannot load original wind OBJ {}",resource,exception);}}meshes=Map.copyOf(loaded);}
}
