package cn.academy.port.city;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered food, currency and souvenir items backed by CityCatalog. */
public final class CityGoods {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    public static final DeferredItem<Item> CITY_COIN = ITEMS.registerSimpleItem("city_coin", new Item.Properties());
    private static final Map<String, DeferredItem<Item>> REGISTERED = registerGoods();
    private CityGoods() {}

    private static Map<String, DeferredItem<Item>> registerGoods() {
        var items = new LinkedHashMap<String, DeferredItem<Item>>();
        for (var good : CityCatalog.GOODS) {
            if (good.kind().equals("supply")) continue;
            items.put(good.id(), ITEMS.register(good.id(), () -> {
                var properties = new Item.Properties();
                if (good.tier() != null) {
                    var food = new FoodProperties.Builder().nutrition(good.tier().nutrition)
                            .saturationModifier(good.tier().saturation).build();
                    return new CityFoodItem(properties.food(food), good.tier() == CityCatalog.Tier.DRINK);
                }
                if (good.kind().equals("guide")) return new CityGuideItem(properties.component(DataComponents.WRITTEN_BOOK_CONTENT, guide(good.id())));
                return new Item(properties);
            }));
        }
        return Map.copyOf(items);
    }

    public static void register(IEventBus bus) { ITEMS.register(bus); bus.addListener(CityGoods::creative); }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            for (var good : CityCatalog.GOODS) if (good.tier() != null) event.accept(REGISTERED.get(good.id()));
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CITY_COIN);
            for (var good : CityCatalog.GOODS) if (good.kind().equals("guide") || good.kind().equals("souvenir")) event.accept(REGISTERED.get(good.id()));
        }
    }

    private static WrittenBookContent guide(String id) {
        return new WrittenBookContent(Filterable.passThrough("Academy City"), "Academy City", 0,
                List.of(Filterable.passThrough(Component.translatable("academy.city.guide." + id + ".1")),
                        Filterable.passThrough(Component.translatable("academy.city.guide." + id + ".2"))), true);
    }
    static ItemStack referenceBook(String id) {
        var stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, guide(id));
        return stack;
    }

    private static final class CityFoodItem extends Item {
        private final boolean drink;
        CityFoodItem(Properties properties, boolean drink) { super(properties); this.drink=drink; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return drink ? UseAnim.DRINK : UseAnim.EAT; }
    }
    private static final class CityGuideItem extends Item {
        CityGuideItem(Properties properties) { super(properties.stacksTo(1)); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer server) server.connection.send(new ClientboundOpenBookPacket(hand));
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
        }
    }
}
