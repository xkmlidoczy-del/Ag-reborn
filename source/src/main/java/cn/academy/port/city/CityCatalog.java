package cn.academy.port.city;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/** Names, shops, prices and visual identities come from the same packaged catalogue. */
public final class CityCatalog {
    public enum Tier {
        MEAL(7, 7, .6f), LIGHT(5, 5, .6f), SNACK(3, 3, .4f), DRINK(2, 2, .3f);
        final int price, nutrition; final float saturation;
        Tier(int price, int nutrition, float saturation) { this.price=price; this.nutrition=nutrition; this.saturation=saturation; }
    }
    public record Good(String id, String zh, String en, String shop, String kind, Tier tier, int price, int count) {}
    public static final List<Good> GOODS = load();
    private CityCatalog() {}

    private static List<Good> load() {
        var stream = CityCatalog.class.getResourceAsStream("/assets/academy/city/catalog.json");
        if (stream == null) throw new IllegalStateException("Missing city catalogue");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var list = new ArrayList<Good>();
            for (var value : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("goods")) {
                var g = value.getAsJsonObject();
                Tier tier = g.has("tier") ? Tier.valueOf(g.get("tier").getAsString().toUpperCase(java.util.Locale.ROOT)) : null;
                String kind = tier == null ? g.get("kind").getAsString() : "food";
                list.add(new Good(g.get("id").getAsString(), g.get("zh").getAsString(), g.get("en").getAsString(),
                        g.get("shop").getAsString(), kind, tier, tier == null ? g.get("price").getAsInt() : tier.price,
                        g.has("count") ? g.get("count").getAsInt() : 1));
            }
            return List.copyOf(list);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read city catalogue", e); }
    }

    static MerchantOffers offers(String shop) {
        var offers = new MerchantOffers();
        for (Good good : GOODS) if (good.shop().equals(shop)) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy", good.id()));
            ItemStack output = good.id().equals("energy_unit")
                    ? cn.academy.port.energy.ClassicEnergyItemHelper.createEmptyItem(item)
                    : new ItemStack(item, good.count());
            offers.add(new MerchantOffer(new ItemCost(CityGoods.CITY_COIN.get(), good.price()), output, 1000000, 0, 0f));
        }
        return offers;
    }

    static MerchantOffers exchange() {
        var offers = new MerchantOffers();
        var coin = CityGoods.CITY_COIN.get();
        offers.add(new MerchantOffer(new ItemCost(net.minecraft.world.item.Items.WHEAT, 8), new ItemStack(coin, 2), 1000000, 0, 0f));
        offers.add(new MerchantOffer(new ItemCost(net.minecraft.world.item.Items.PAPER, 8), new ItemStack(coin, 2), 1000000, 0, 0f));
        offers.add(new MerchantOffer(new ItemCost(net.minecraft.world.item.Items.COPPER_INGOT, 4), new ItemStack(coin, 3), 1000000, 0, 0f));
        offers.add(new MerchantOffer(new ItemCost(net.minecraft.world.item.Items.IRON_INGOT, 2), new ItemStack(coin, 3), 1000000, 0, 0f));
        return offers;
    }
}
