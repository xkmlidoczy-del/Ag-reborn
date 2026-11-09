/* Original AcademyCraft solar OBJ, modern resource-pack reload adapter. GPLv3. See NOTICE. */
package cn.academy.port.client;

import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class ClassicSolarModel implements ResourceManagerReloadListener {
    public static final ClassicSolarModel INSTANCE = new ClassicSolarModel();
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("academy", "models/solar.obj");
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/models/solar.png");
    private volatile ClassicDeveloperObj.Mesh mesh;
    private ClassicSolarModel() {}
    public ClassicDeveloperObj.Mesh mesh() { return mesh; }
    @Override public void onResourceManagerReload(ResourceManager resources) {
        ClassicDeveloperObj.Mesh loaded = null;
        try (var stream = resources.getResourceOrThrow(MODEL).open(); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            loaded = ClassicDeveloperObj.parse(reader);
        } catch (Exception exception) { LogUtils.getLogger().error("Cannot load original solar generator OBJ {}", MODEL, exception); }
        mesh = loaded; // A missing/broken resource cannot silently retain an old pack's geometry.
    }
}
