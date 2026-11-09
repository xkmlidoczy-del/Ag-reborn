/* Original AcademyCraft1.0.7 matrix.obj resource-pack reload adapter, GPLv3. See NOTICE. */
package cn.academy.port.client;

import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class ClassicWirelessModels implements ResourceManagerReloadListener {
    public static final ClassicWirelessModels INSTANCE = new ClassicWirelessModels();
    public static final ResourceLocation MATRIX_MODEL = ResourceLocation.fromNamespaceAndPath("academy", "models/matrix.obj");
    public static final ResourceLocation MATRIX_TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", "textures/models/matrix.png");
    private volatile ClassicWirelessObj.Model matrix;
    private ClassicWirelessModels() {}
    public ClassicWirelessObj.Model matrix() { return matrix; }
    @Override public void onResourceManagerReload(ResourceManager resources) {
        ClassicWirelessObj.Model loaded = null;
        try (var stream = resources.getResourceOrThrow(MATRIX_MODEL).open(); var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            loaded = ClassicWirelessObj.parse(reader);
            loaded.require("Main"); loaded.require("Core"); loaded.require("Shield");
        } catch (Exception exception) {
            loaded = null;
            LogUtils.getLogger().error("Cannot load original wireless matrix OBJ {}", MATRIX_MODEL, exception);
        }
        matrix = loaded; // Do not silently keep an old pack's geometry after a failed reload.
    }
}
