/* Original AcademyCraft OBJ assets, modern resource-pack aware loading. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.develop.DeveloperType;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** One immutable CPU mesh per source model; replaced atomically on F3+T/resource-pack reload. */
public final class ClassicDeveloperModels implements ResourceManagerReloadListener {
    public static final ClassicDeveloperModels INSTANCE = new ClassicDeveloperModels();
    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile Map<DeveloperType, ClassicDeveloperObj.Mesh> meshes = Map.of();
    private ClassicDeveloperModels() {}

    public static ResourceLocation model(DeveloperType type) { return resource(type, "models/", ".obj"); }
    public static ResourceLocation texture(DeveloperType type) { return resource(type, "textures/models/", ".png"); }
    private static ResourceLocation resource(DeveloperType type, String prefix, String extension) {
        String name = switch (type) {
            case NORMAL -> "developer_normal";
            case ADVANCED -> "developer_advanced";
            case PORTABLE -> throw new IllegalArgumentException("Portable developer has a different renderer");
        };
        return ResourceLocation.fromNamespaceAndPath("academy", prefix + name + extension);
    }
    public ClassicDeveloperObj.Mesh mesh(DeveloperType type) { return meshes.get(type); }

    @Override public void onResourceManagerReload(ResourceManager resources) {
        var loaded = new EnumMap<DeveloperType, ClassicDeveloperObj.Mesh>(DeveloperType.class);
        for (DeveloperType type : new DeveloperType[]{DeveloperType.NORMAL, DeveloperType.ADVANCED}) {
            ResourceLocation resource = model(type);
            try (var input = resources.getResourceOrThrow(resource).open();
                 var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                loaded.put(type, ClassicDeveloperObj.parse(reader));
            } catch (Exception exception) {
                // Do not keep a stale mesh from the previous pack or silently substitute block cubes.
                LOGGER.error("Cannot load original developer model {}", resource, exception);
            }
        }
        meshes = Map.copyOf(loaded);
    }
}
