package cn.academy.port.survival;

import cn.academy.port.AcademyCraft;
import cn.academy.port.develop.DevelopmentActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetCustomDataFunction;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/** Classic source chest weighting: append four category entries to an existing weighted pool. */
public final class ClassicFactorLoot {
    public static final Set<ResourceLocation> CHESTS = Set.of(
            ResourceLocation.withDefaultNamespace("chests/abandoned_mineshaft"),
            ResourceLocation.withDefaultNamespace("chests/desert_pyramid"),
            ResourceLocation.withDefaultNamespace("chests/jungle_temple"),
            ResourceLocation.withDefaultNamespace("chests/stronghold_library"),
            ResourceLocation.withDefaultNamespace("chests/simple_dungeon"));
    private ClassicFactorLoot() {}
    public static void load(LootTableLoadEvent event) {
        if (!CHESTS.contains(event.getName())) return;
        // Loot is assembled before enchantment tags finish binding. Re-encoding the
        // whole table here rejects registry owners/tags in unchanged vanilla functions.
        // Narrow NeoForge ATs expose only assembly fields; no table, pool, conditions,
        // functions, roll counts or names are reconstructed or replaced.
        for (var pool : event.getTable().pools) {
            if (pool.entries.isEmpty()) continue;
            for (var entry : pool.entries)
                if (entry instanceof LootItem item && item.item.value() == AcademyCraft.INDUCTION_FACTOR.get()) return;
            var entries = new ArrayList<>(pool.entries);
            for (String category : DevelopmentActions.CATEGORIES) {
                var tag = new CompoundTag();tag.putString("academy:induction_category",category);
                entries.add(LootItem.lootTableItem(AcademyCraft.INDUCTION_FACTOR.get()).setWeight(4)
                        .apply(SetCustomDataFunction.setCustomData(tag)).build());
            }
            pool.entries = List.copyOf(entries);
            return;
        }
    }
}
