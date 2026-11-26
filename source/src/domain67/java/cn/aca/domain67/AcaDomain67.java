package cn.aca.domain67;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(AcaDomain67.ID)
public final class AcaDomain67 {
    public static final String ID = "aca_domain67";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    public static final DeferredItem<CircusMegaphoneItem> CIRCUS_MEGAPHONE_67 = ITEMS.register(
            "circus_megaphone_67", () -> new CircusMegaphoneItem(new Item.Properties().stacksTo(1).fireResistant()));

    public static final DeferredItem<DomainFoodItem> CHOCO_SAUSAGE_NOODLES = ITEMS.register(
            "choco_sausage_noodles", () -> new DomainFoodItem(false));

    public static final DeferredItem<DomainFoodItem> NO_HEARTBURN_BENTO = ITEMS.register(
            "no_heartburn_bento", () -> new DomainFoodItem(true));

    public static final DeferredHolder<EntityType<?>, EntityType<CursedLogEntity>> CURSED_LOG = ENTITIES.register(
            "cursed_log", () -> EntityType.Builder.<CursedLogEntity>of(CursedLogEntity::new, MobCategory.MISC)
                    .sized(1.6f, 1.4f)
                    .clientTrackingRange(96)
                    .updateInterval(1)
                    .noSummon()
                    .build(ID + ":cursed_log"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aca_domain67"))
                    .icon(() -> new ItemStack(CIRCUS_MEGAPHONE_67.get()))
                    .displayItems((params, out) -> {
                        out.accept(CIRCUS_MEGAPHONE_67.get());
                        out.accept(CHOCO_SAUSAGE_NOODLES.get());
                        out.accept(NO_HEARTBURN_BENTO.get());
                    })
                    .build());

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    public AcaDomain67(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(Domain67Network::register);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(AcaDomain67::onServerStarted);
        gameBus.addListener(AcaDomain67::onServerTick);
        gameBus.addListener(AcaDomain67::onPlayerLogout);
        gameBus.addListener(AcaDomain67::onServerStopping);
        gameBus.addListener(AcaDomain67::onBreak);
        gameBus.addListener(AcaDomain67::onPlace);
        gameBus.addListener(AcaDomain67::onExplosion);
        LOGGER.info("ACA Domain 67 initialized.");
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && DomainTerrainVault.get(level).manages(event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        DomainTerrainVault vault = DomainTerrainVault.get(level);
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            if (multi.getReplacedBlockSnapshots().stream().anyMatch(s -> vault.manages(s.getPos()))) {
                event.setCanceled(true);
            }
        } else if (vault.manages(event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        DomainTerrainVault vault = DomainTerrainVault.get(level);
        event.getAffectedBlocks().removeIf(vault::manages);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        // Crash recovery: restore any lingering snapshotted arena blocks from a previous ungraceful exit
        for (ServerLevel level : event.getServer().getAllLevels()) {
            DomainTerrainVault.get(level).restoreAll(level);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        Domain67Manager.tickServer(event.getServer());
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            Domain67Manager.dismissDomain(sp, false);
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            DomainTerrainVault.get(level).restoreAll(level);
        }
        Domain67Manager.clearAll();
    }
}
