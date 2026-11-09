/* AcademyCraft1.0.7 TerminalInstallerRenderer source mesh adapter. GPLv3. */
package cn.academy.port.client;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
public final class TerminalInstallerModels implements ResourceManagerReloadListener {
    public static final TerminalInstallerModels INSTANCE=new TerminalInstallerModels();
    public static final ResourceLocation MODEL=ResourceLocation.fromNamespaceAndPath("academy","models/terminal_installer.obj"),TEXTURE=ResourceLocation.fromNamespaceAndPath("academy","textures/models/terminal_installer.png");
    private volatile ClassicDeveloperObj.Mesh mesh;
    public ClassicDeveloperObj.Mesh mesh(){return mesh;}
    @Override public void onResourceManagerReload(ResourceManager resources){ClassicDeveloperObj.Mesh next=null;try(var stream=resources.getResourceOrThrow(MODEL).open();var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){next=ClassicDeveloperObj.parse(reader);}catch(Exception error){LogUtils.getLogger().error("Cannot load original terminal installer OBJ {}",MODEL,error);}mesh=next;}
    private TerminalInstallerModels(){}
}
