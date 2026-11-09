package cn.academy.port.city.defense.armor;


import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public class DefenseArmorItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "academy");
    public static final List<DeferredHolder<Item, ? extends Item>> ALL = new ArrayList<>();

    // 05 / BLACK GOLD
    public static final DeferredHolder<Item, DefenseArmorItem> BLACK_GOLD_HELMET = set("black_gold", "helmet", DefenseArmorMaterials.BLACK_GOLD, ArmorItem.Type.HELMET, 46, Rarity.EPIC);
    public static final DeferredHolder<Item, DefenseArmorItem> BLACK_GOLD_CHESTPLATE = set("black_gold", "chestplate", DefenseArmorMaterials.BLACK_GOLD, ArmorItem.Type.CHESTPLATE, 46, Rarity.EPIC);
    public static final DeferredHolder<Item, DefenseArmorItem> BLACK_GOLD_LEGGINGS = set("black_gold", "leggings", DefenseArmorMaterials.BLACK_GOLD, ArmorItem.Type.LEGGINGS, 46, Rarity.EPIC);
    public static final DeferredHolder<Item, DefenseArmorItem> BLACK_GOLD_BOOTS = set("black_gold", "boots", DefenseArmorMaterials.BLACK_GOLD, ArmorItem.Type.BOOTS, 46, Rarity.EPIC);

    // P-01 / SPECIAL POLICE
    public static final DeferredHolder<Item, DefenseArmorItem> POLICE_HELMET = set("special_police", "helmet", DefenseArmorMaterials.SPECIAL_POLICE, ArmorItem.Type.HELMET, 24, Rarity.RARE);
    public static final DeferredHolder<Item, DefenseArmorItem> POLICE_CHESTPLATE = set("special_police", "chestplate", DefenseArmorMaterials.SPECIAL_POLICE, ArmorItem.Type.CHESTPLATE, 24, Rarity.RARE);
    public static final DeferredHolder<Item, DefenseArmorItem> POLICE_LEGGINGS = set("special_police", "leggings", DefenseArmorMaterials.SPECIAL_POLICE, ArmorItem.Type.LEGGINGS, 24, Rarity.RARE);
    public static final DeferredHolder<Item, DefenseArmorItem> POLICE_BOOTS = set("special_police", "boots", DefenseArmorMaterials.SPECIAL_POLICE, ArmorItem.Type.BOOTS, 24, Rarity.RARE);

    private static DeferredHolder<Item, DefenseArmorItem> set(String setName, String piece, Holder<ArmorMaterial> mat,
                                                          ArmorItem.Type type, int durabilityMul, Rarity rarity) {
        DeferredHolder<Item, DefenseArmorItem> h = ITEMS.register(setName + "_" + piece,
                () -> new DefenseArmorItem(mat, type, properties(type, rarity, setName), setName));
        ALL.add(h);
        return h;
    }
    private static Item.Properties properties(ArmorItem.Type type, Rarity rarity, String setName) {
        var props = new Item.Properties().durability(type.getDurability(setName.equals("black_gold") ? 5 : 37)).rarity(rarity).fireResistant();
        if (setName.equals("black_gold")) props.component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                new net.minecraft.world.item.component.Unbreakable(true));
        return props;
    }
}
