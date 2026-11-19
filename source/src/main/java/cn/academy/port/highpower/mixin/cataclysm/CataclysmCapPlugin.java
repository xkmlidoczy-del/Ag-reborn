package cn.academy.port.highpower.mixin.cataclysm;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CataclysmCapPlugin implements IMixinConfigPlugin {
    // Official CurseForge NeoForge file 8931182. Verify the instructions that determine range and budget.
    private static final Map<String, String> CHECKED_CLASSES = Map.of(
            "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.LLibrary_Boss_Monster",
            "4f7be6e7e14fe81c47f88f50e9d79245edf34203f2f758c4dc6f02b93fcc2622",
            "com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.IABoss_monster",
            "9e31093926761c08c7fcdebd8fbb8ae2482c91b1ee0b897f72515e9b34f858ac");

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        var file = LoadingModList.get().getModFileById("cataclysm");
        if (file == null) return false;
        var info = file.getMods().stream().filter(mod -> mod.getModId().equals("cataclysm")).findFirst().orElseThrow();
        if (!info.getVersion().toString().equals("3.33")) {
            LogUtils.getLogger().warn("Academy is using native Cataclysm caps for unsupported version {}", info.getVersion());
            return false;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.getFile().findResource(target.replace('.', '/') + ".class"));
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (hash.equals(CHECKED_CLASSES.get(target))) {
                LogUtils.getLogger().info("Academy enabled Cataclysm damage cap adapters for {}", target);
                return true;
            }
            LogUtils.getLogger().warn("Academy is using native Cataclysm caps for an unrecognized {}", target);
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException error) {
            LogUtils.getLogger().warn("Academy could not verify Cataclysm cap bytecode for {}", target, error);
        }
        return false;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
