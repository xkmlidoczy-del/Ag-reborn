package cn.aca.infinity;

import cn.aca.infinity.world.CosmicVaultStructure;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(AcaInfinity.ID)
public final class AcaInfinity {
    public static final String ID = "aca_infinity";

    public static final TagKey<EntityType<?>> PROTECTED = TagKey.create(Registries.ENTITY_TYPE, id("protected"));

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    public static final Supplier<DataComponentType<GauntletData>> GAUNTLET_DATA =
            COMPONENTS.register("gauntlet_data", () -> DataComponentType.<GauntletData>builder()
                    .persistent(GauntletData.CODEC)
                    .networkSynchronized(GauntletData.STREAM_CODEC)
                    .build());

    public static final Supplier<InfinityGauntletItem> INFINITY_GAUNTLET =
            ITEMS.register("infinity_gauntlet", InfinityGauntletItem::new);

    private static final Map<Stone, Supplier<StoneItem>> STONES = new EnumMap<>(Stone.class);

    public static final Supplier<SanctuaryBeaconItem> SANCTUARY_BEACON =
            ITEMS.register("sanctuary_beacon", () -> new SanctuaryBeaconItem(false));
    public static final Supplier<SanctuaryBeaconItem> HOSTILE_SANCTUARY_BEACON =
            ITEMS.register("hostile_sanctuary_beacon", () -> new SanctuaryBeaconItem(true));

    public static final Supplier<EntityType<SanctuaryShipEntity>> SANCTUARY_SHIP =
            ENTITIES.register("sanctuary_ship", () -> EntityType.Builder
                    .<SanctuaryShipEntity>of(SanctuaryShipEntity::new, MobCategory.MISC)
                    .sized(6.4f, 2.8f)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(ID + ":sanctuary_ship"));

    public static final Supplier<EntityType<ShipOrdnanceEntity>> SHIP_ORDNANCE =
            ENTITIES.register("ship_ordnance", () -> EntityType.Builder
                    .<ShipOrdnanceEntity>of(ShipOrdnanceEntity::new, MobCategory.MISC)
                    .sized(0.45f, 0.45f)
                    .noSummon()
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(ID + ":ship_ordnance"));

    public static final Supplier<AttachmentType<WearerState>> WEARER =
            ATTACHMENTS.register("wearer", () -> AttachmentType.serializable(WearerState::new).build());

    public static final Supplier<AttachmentType<EntityStatusData>> ENTITY_STATUS =
            ATTACHMENTS.register("entity_status", () -> AttachmentType.serializable(EntityStatusData::new).build());

    static {
        for (Stone s : Stone.values()) {
            STONES.put(s, ITEMS.register(s.itemId(), () -> new StoneItem(s)));
        }
        TABS.register("infinity_tab", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.aca_infinity.main"))
                .icon(InfinityGauntletItem::createFullStack)
                .displayItems((params, out) -> {
                    out.accept(InfinityGauntletItem.createFullStack());
                    out.accept(INFINITY_GAUNTLET.get());
                    for (Stone s : Stone.values()) {
                        out.accept(stoneItem(s));
                    }
                    out.accept(SANCTUARY_BEACON.get());
                    out.accept(HOSTILE_SANCTUARY_BEACON.get());
                })
                .build());
    }

    public AcaInfinity(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, InfinityConfig.SPEC);
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        TABS.register(modBus);
        CosmicVaultStructure.register(modBus);
        modBus.addListener(InfinityNetwork::register);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        GauntletService.register(gameBus);
        StoneAbilities.register(gameBus);
        gameBus.addListener(AcademyCompat::onNoticeBoardInteract);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    public static StoneItem stoneItem(Stone stone) {
        return STONES.get(stone).get();
    }

    public static WearerState wearer(Player player) {
        return player.getData(WEARER);
    }

    public static EntityStatusData status(Entity entity) {
        return entity.getData(ENTITY_STATUS);
    }
}
