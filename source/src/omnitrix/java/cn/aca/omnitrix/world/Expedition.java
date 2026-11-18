package cn.aca.omnitrix.world;

import cn.aca.omnitrix.Omnitrix;
import cn.aca.omnitrix.WristService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class Expedition {
    private static final ResourceLocation CITY = ResourceLocation.fromNamespaceAndPath("academy", "story_city_map");
    private static final ResourceLocation NOTICE = ResourceLocation.fromNamespaceAndPath("academy", "city_notice_board");
    private static final String LAST_NOTICE = "aca_omnitrix:notice_after";
    public static final TagKey<Structure> CAPSULES = TagKey.create(Registries.STRUCTURE, Omnitrix.id("crash_pod"));

    private Expedition() {}

    public static void onNoticeBoard(PlayerInteractEvent.RightClickBlock event) {
        if (!ModList.get().isLoaded("academy") || !event.getItemStack().is(Items.COMPASS)
                || !event.getLevel().dimension().location().equals(CITY)
                || !BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()).equals(NOTICE)) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            var data = player.getPersistentData();
            long now = WristService.time(player);
            if (data.getLong(LAST_NOTICE) <= now && player.mayInteract(player.level(), event.getPos())) {
                data.putLong(LAST_NOTICE, now + 100);
                var overworld = player.server.getLevel(Level.OVERWORLD);
                var origin = overworld.getSharedSpawnPos();
                var known = player.getRespawnPosition();
                if (player.getRespawnDimension().equals(Level.OVERWORLD) && known != null) origin = known;
                BlockPos pos = overworld.findNearestMapStructure(CAPSULES, origin, 80, false);
                if (pos != null) {
                    var map = MapItem.create(overworld, pos.getX(), pos.getZ(), (byte)2, true, true);
                    map.set(DataComponents.ITEM_NAME, Component.translatable("map.aca_omnitrix.crash_pod"));
                    MapItem.renderBiomePreviewMap(overworld, map);
                    MapItemSavedData.addTargetDecoration(map, pos, "capsule", MapDecorationTypes.RED_X);
                    event.getItemStack().consume(1, player);
                    if (!player.getInventory().add(map)) player.drop(map, false);
                    WristService.tell(player, "map_from_city");
                } else WristService.tell(player, "no_map");
            }
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        event.setCanceled(true);
    }
}
