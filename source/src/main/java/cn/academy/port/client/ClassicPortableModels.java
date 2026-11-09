package cn.academy.port.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** One resource-pack-aware immutable source mesh. Reload cannot retain a stale pack's geometry. */
public final class ClassicPortableModels implements ResourceManagerReloadListener {
    public static final ClassicPortableModels INSTANCE=new ClassicPortableModels();
    public static final ResourceLocation MODEL=ResourceLocation.fromNamespaceAndPath("academy","models/developer_portable.obj");
    public static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("academy","textures/models/developer_portable.png");
    private volatile ClassicDeveloperObj.Mesh mesh;
    public ClassicDeveloperObj.Mesh mesh(){return mesh;}
    @Override public void onResourceManagerReload(ResourceManager resources){
        ClassicDeveloperObj.Mesh next=null;
        try(var input=resources.getResourceOrThrow(MODEL).open();var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){next=ClassicDeveloperObj.parse(reader);}
        catch(Exception error){LogUtils.getLogger().error("Cannot load original portable developer OBJ {}",MODEL,error);}
        mesh=next;
    }
    private ClassicPortableModels(){}
}
