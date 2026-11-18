package cn.aca.omnitrix;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

final class AcademyInterop {
    private static boolean resolved;
    private static Method release;

    private AcademyInterop() {}

    static void releaseHeldAbility(ServerPlayer player) {
        if (!ModList.get().isLoaded("academy")) return;
        try {
            if (!resolved) {
                resolved = true;
                release = Class.forName("cn.academy.port.AcademyGameplay")
                        .getMethod("disposeAbilityContexts", ServerPlayer.class);
            }
            if (release != null) release.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError ex) {
            release = null;
            LogUtils.getLogger().warn("ACA ability context could not be released", ex);
        }
    }
}
