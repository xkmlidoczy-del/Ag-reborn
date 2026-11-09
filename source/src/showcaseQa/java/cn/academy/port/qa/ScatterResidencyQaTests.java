package cn.academy.port.qa;

import cn.academy.port.gametest.HighPowerScatterPlateArrowRuntimeTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runs the unchanged native-arrow regression bodies in isolation for residency diagnosis. Dev classpath only. */
@GameTestHolder("academy_scatter_residency_qa")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class ScatterResidencyQaTests {
    @net.neoforged.bus.api.SubscribeEvent
    public static void template(net.neoforged.neoforge.event.level.LevelEvent.Load e) {
        if (!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()
                || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        HighPowerScatterPlateArrowRuntimeTests.installCorridor(level, "academy_scatter_residency_qa");
        var tag = new net.minecraft.nbt.CompoundTag();
        var size = new net.minecraft.nbt.ListTag();
        for (int n : new int[] {48, 48, 48}) size.add(net.minecraft.nbt.IntTag.valueOf(n));
        tag.put("size", size); tag.put("blocks", new net.minecraft.nbt.ListTag());
        tag.put("palette", new net.minecraft.nbt.ListTag()); tag.put("entities", new net.minecraft.nbt.ListTag());
        level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_scatter_residency_qa", "highpower_empty"))
                .load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), tag);
    }

    @GameTest(template = "highpower_empty", timeoutTicks = 201)
    public static void unloaded(GameTestHelper h) {
        HighPowerScatterPlateArrowRuntimeTests.scatter_unloaded_native_arrow_retires_receipt_without_changing_motion(h);
    }

    @GameTest(template = "scatter_saturation_corridor", timeoutTicks = 80)
    public static void saturation(GameTestHelper h) {
        HighPowerScatterPlateArrowRuntimeTests.scatter_controller_receipt_saturation_never_changes_native_arrow_tick(h);
    }
}
