package cn.academy.port.gametest;

import cn.academy.port.highpower.VanillaCombatContract;
import cn.academy.port.highpower.VanillaMiningContract;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Confirms the exact embedded module is accepted, and other data still fail closed. */
@GameTestHolder("academy_mech_integration")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class MechIntegrationRuntimeTests {
    private static final String TEMPLATE = "mech_empty";
    private MechIntegrationRuntimeTests() {}

    @SubscribeEvent
    public static void template(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag();
        var size = new ListTag();
        for (int n : new int[] {4, 4, 4}) size.add(IntTag.valueOf(n));
        var pos = new ListTag();
        for (int n = 0; n < 3; n++) pos.add(IntTag.valueOf(0));
        var cell = new CompoundTag(); cell.put("pos", pos); cell.putInt("state", 0);
        var blocks = new ListTag(); blocks.add(cell);
        var air = new CompoundTag(); air.putString("Name", "minecraft:air");
        var palette = new ListTag(); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_mech_integration", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void strict_contract_accepts_exact_loaded_mech_but_not_other_mods(GameTestHelper h) {
        Map<String, String> mods = ModList.get().getMods().stream()
                .collect(Collectors.toMap(m -> m.getModId(), m -> m.getVersion().toString()));
        Set<String> packs = h.getLevel().getServer().getResourceManager().listPacks()
                .map(p -> p.packId()).collect(Collectors.toSet());
        h.assertTrue("0.4.3".equals(mods.get("acamech")), "real mech 0.4.3 is loaded: " + mods);
        h.assertTrue(VanillaCombatContract.runtimeReason(mods, packs).isEmpty(), "strict combat admits the actual mod and pack set");
        h.assertTrue(VanillaMiningContract.runtimeReason(mods, packs).isEmpty(), "strict mining admits the actual mod and pack set");
        var changed = new HashMap<>(mods); changed.put("acamech", "0.4.4");
        h.assertTrue(VanillaCombatContract.runtimeReason(changed, packs).isPresent()
                && VanillaMiningContract.runtimeReason(changed, packs).isPresent(), "other mech versions fail closed");
        changed = new HashMap<>(mods); changed.put("unreviewed", "1");
        h.assertTrue(VanillaCombatContract.runtimeReason(changed, packs).isPresent()
                && VanillaMiningContract.runtimeReason(changed, packs).isPresent(), "other mods fail closed");
        var extraPack = new HashSet<>(packs); extraPack.add("file/unreviewed.zip");
        h.assertTrue(VanillaCombatContract.runtimeReason(mods, extraPack).isPresent()
                && VanillaMiningContract.runtimeReason(mods, extraPack).isPresent(), "other packs fail closed");
        h.succeed();
    }
}
