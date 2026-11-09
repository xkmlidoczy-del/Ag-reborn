package cn.academy.port.highpower.environment;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** Optional Flan block authority. This version's native break and prospective place hooks also remain active. */
final class FlanEnvironmentProtection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD = "flan";
    private static final String VERIFIED = "1.21.1-1.12.8-neoforge";
    private static final boolean LOADED = ModList.get().isLoaded(MOD);
    static final ResourceLocation BREAK = ResourceLocation.fromNamespaceAndPath(MOD, "break");
    static final ResourceLocation PLACE = ResourceLocation.fromNamespaceAndPath(MOD, "place");

    private FlanEnvironmentProtection() {}

    static boolean loaded() {
        return LOADED;
    }

    static boolean available() {
        return !loaded() || Link.CAN_INTERACT != null;
    }

    static boolean permits(ServerPlayer owner, BlockPos pos, ResourceLocation permission) {
        if (!loaded()) return true;
        Method method = Link.CAN_INTERACT;
        if (method == null) return false;
        try {
            return Boolean.TRUE.equals(method.invoke(null, owner, pos, permission));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!Link.loggedFailure) {
                Link.loggedFailure = true;
                LOGGER.warn("ACA environment is refusing Flan-protected writes after its permission API failed", failure);
            }
            return false;
        }
    }

    private static final class Link {
        private static final Method CAN_INTERACT = resolve();
        private static boolean loggedFailure;

        private static Method resolve() {
            try {
                String version = ModList.get().getModContainerById(MOD)
                        .map(mod -> mod.getModInfo().getVersion().toString()).orElse("");
                if (!VERIFIED.equals(version)) {
                    LOGGER.warn("ACA environment is refusing block writes with unverified Flan version {}", version);
                    return null;
                }
                return Class.forName("io.github.flemmli97.flan.api.ClaimHandler")
                        .getMethod("canInteract", ServerPlayer.class, BlockPos.class, ResourceLocation.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LOGGER.warn("ACA environment is refusing block writes because the Flan permission API is unavailable", failure);
                return null;
            }
        }
    }
}
