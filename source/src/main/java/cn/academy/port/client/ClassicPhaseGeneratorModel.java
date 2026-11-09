/* Original AcademyCraft ip_gen OBJ, resource-pack reload adapter. GPLv3; see NOTICE. */
package cn.academy.port.client;

import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class ClassicPhaseGeneratorModel implements ResourceManagerReloadListener {
    public static final ClassicPhaseGeneratorModel INSTANCE = new ClassicPhaseGeneratorModel();
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("academy", "models/ip_gen.obj");
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[ClassicPhaseGeneratorVisualRules.TEXTURE_COUNT];
    static {
        for (int i = 0; i < TEXTURES.length; i++)
            TEXTURES[i] = ResourceLocation.fromNamespaceAndPath("academy", "textures/models/ip_gen" + i + ".png");
    }
    private volatile ClassicDeveloperObj.Mesh mesh;
    private ClassicPhaseGeneratorModel() {}
    public ClassicDeveloperObj.Mesh mesh() { return mesh; }
    public static ResourceLocation texture(int liquid, int tankSize) {
        return TEXTURES[ClassicPhaseGeneratorVisualRules.textureIndex(liquid, tankSize)];
    }
    @Override public void onResourceManagerReload(ResourceManager resources) {
        ClassicDeveloperObj.Mesh loaded = null;
        try (var stream = resources.getResourceOrThrow(MODEL).open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            loaded = ClassicDeveloperObj.parse(reader);
        } catch (Exception exception) {
            LogUtils.getLogger().error("Cannot load original phase generator OBJ {}", MODEL, exception);
        }
        mesh = loaded; // A broken resource pack must not retain geometry from the previous pack.
    }
}
