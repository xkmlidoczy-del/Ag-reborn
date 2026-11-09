/* AcademyCraft1.0.7 original Silicon Barn OBJ loader. GPLv3; see NOTICE. */
package cn.academy.port.client;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
public final class ClassicSilbarnModels implements ResourceManagerReloadListener {
 public static final ClassicSilbarnModels INSTANCE=new ClassicSilbarnModels();public static final ResourceLocation MODEL=ResourceLocation.fromNamespaceAndPath("academy","models/silbarn.obj"),TEXTURE=ResourceLocation.fromNamespaceAndPath("academy","textures/models/silbarn.png");private volatile ClassicDeveloperObj.Mesh mesh;
 public ClassicDeveloperObj.Mesh mesh(){return mesh;}private ClassicSilbarnModels(){}
 @Override public void onResourceManagerReload(ResourceManager r){ClassicDeveloperObj.Mesh next=null;try(var in=r.getResourceOrThrow(MODEL).open();var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){next=ClassicDeveloperObj.parse(reader);}catch(Exception error){LogUtils.getLogger().error("Cannot load source Silicon Barn OBJ {}",MODEL,error);}mesh=next;}
}
