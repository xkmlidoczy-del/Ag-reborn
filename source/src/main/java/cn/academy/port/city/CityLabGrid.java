package cn.academy.port.city;

import cn.academy.port.train.TrainManager;
import cn.academy.port.wireless.ClassicWirelessNodeBlockEntity;
import cn.academy.port.wireless.ClassicWirelessSavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Connect each generated endpoint once, when its own chunk is loaded. */
@EventBusSubscriber(modid = "academy")
public final class CityLabGrid extends SavedData {
    private static final String ID = "academy_city_lab_grid";
    private int initialized;
    private static CityLabGrid load(CompoundTag tag, HolderLookup.Provider lookup) {
        var data = new CityLabGrid();
        data.initialized = tag.getInt("initialized");
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("initialized", initialized);
        return tag;
    }
    private static CityLabGrid get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(CityLabGrid::new, CityLabGrid::load), ID);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(TrainManager.CITY)
                || !(level.getChunkSource().getGenerator() instanceof CityGreyboxGenerator) || level.getGameTime() % 20 != 0
                || level.players().stream().noneMatch(player -> player.distanceToSqr(-224.5, 1, -104.5) < 128 * 128)
                || !level.hasChunkAt(CityLabPlan.NODE)
                || !(level.getBlockEntity(CityLabPlan.NODE) instanceof ClassicWirelessNodeBlockEntity node)
                || !CityLabPlan.NODE_NAME.equals(node.getNodeName())) return;
        var data = get(level);
        var graph = ClassicWirelessSavedData.get(level).graph();
        var nodePos = ClassicWirelessSavedData.pos(CityLabPlan.NODE);
        for (int i = 0; i < CityLabPlan.WIND_BASES.size(); i++) {
            int mask = 1 << i;
            if ((data.initialized & mask) == 0 && graph.linkGenerator(nodePos,
                    ClassicWirelessSavedData.pos(CityLabPlan.WIND_BASES.get(i)), "", false)) {
                data.initialized |= mask; data.setDirty();
            }
        }
        for (int i = 0; i < CityLabPlan.MACHINES.size(); i++) {
            int mask = 1 << (i + CityLabPlan.WIND_BASES.size());
            if ((data.initialized & mask) == 0 && graph.linkReceiver(nodePos,
                    ClassicWirelessSavedData.pos(CityLabPlan.MACHINES.get(i)), "", false)) {
                data.initialized |= mask; data.setDirty();
            }
        }
    }
}
