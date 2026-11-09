package cn.academy.port.highpower;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NEW131 finite native offhand ingredient. Paid sand is never an entity, escrow or refundable item. */
@EventBusSubscriber(modid = "academy")
public final class IronSandMedium {
    public static final int UNITS_PER_CAST = 3, MAX_STACK_SIZE = 64;
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    public static final DeferredItem<Item> ITEM = ITEMS.registerSimpleItem("iron_sand", new Item.Properties().stacksTo(MAX_STACK_SIZE));
    private static final Map<ServerPlayer, Object> REVISIONS = new WeakHashMap<>();

    /** Only a live server charge owns this snapshot; it is deliberately absent from item and player save data. */
    public static final class Snapshot {
        private final ServerPlayer owner;
        private final ItemStack stack, original;
        private final Object revision;
        private boolean attempted;
        private Snapshot(ServerPlayer owner, ItemStack stack, Object revision) {
            this.owner = owner; this.stack = stack; original = stack.copy(); this.revision = revision;
        }
    }

    private IronSandMedium() {}
    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(IronSandMedium::creative);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(ITEM);
    }

    public static Snapshot capture(ServerPlayer player) {
        if (!eligible(player)) return null;
        ItemStack stack = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!medium(stack)) return null;
        Object revision = new Object();
        REVISIONS.put(player, revision); // A newer charge also retires any previous snapshot.
        return new Snapshot(player, stack, revision);
    }

    /** Sample every charge tick and again at release; a same-looking replacement stack cannot inherit the charge. */
    public static boolean matches(ServerPlayer player, Snapshot snapshot) {
        if (!eligible(player) || snapshot == null || snapshot.owner != player || snapshot.attempted
                || REVISIONS.get(player) != snapshot.revision) return false;
        ItemStack current = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return current == snapshot.stack && medium(current) && ItemStack.matches(current, snapshot.original);
    }

    /**
     * Single server-thread transaction, after all target/pose and charge checks.
     * The existing prototype ledger and ItemStack.shrink are direct mutations with no callbacks
     * between their commits. Inventory notifications run only after both are final. Unlike
     * ItemStack.consume, this path cannot silently waive materials for a creative player.
     */
    public static boolean commit(ServerPlayer player, Snapshot snapshot, String skill, double cpCost, int cooldownTicks) {
        if (!matches(player, snapshot)) return false;
        snapshot.attempted = true;
        REVISIONS.remove(player);
        if (!ElectromasterPrototype.trySpend(player, skill, cpCost, cooldownTicks)) return false;
        snapshot.stack.shrink(UNITS_PER_CAST);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        return true;
    }

    private static boolean medium(ItemStack stack) {
        return stack.is(ITEM.get()) && stack.getCount() >= UNITS_PER_CAST && stack.getCount() <= MAX_STACK_SIZE;
    }
    private static boolean eligible(ServerPlayer player) {
        return player != null && player.server.isSameThread() && player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && !player.isCreative() && !player.getAbilities().instabuild;
    }

    /** F-swapping away and back between charge samples still invalidates that charge. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void handSwap(LivingSwapItemsEvent.Hands event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player && player.server.isSameThread())
            REVISIONS.remove(player);
    }
}
