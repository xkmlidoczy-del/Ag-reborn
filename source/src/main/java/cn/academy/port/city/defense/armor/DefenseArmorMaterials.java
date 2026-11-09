package cn.academy.port.city.defense.armor;


import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public class DefenseArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, "academy");

    /** O5: leather numeric armor; full-set combat powers are separate. */
    public static final Holder<ArmorMaterial> BLACK_GOLD = MATERIALS.register("black_gold", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 1);
                m.put(ArmorItem.Type.LEGGINGS, 2);
                m.put(ArmorItem.Type.CHESTPLATE, 3);
                m.put(ArmorItem.Type.HELMET, 1);
                m.put(ArmorItem.Type.BODY, 3);
            }),
            15, SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(Items.NETHERITE_INGOT),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath("academy", "black_gold"))),
            0.0f, 0.0f));

    /** P-01: netherite numeric armor, with no O5 combat abilities. */
    public static final Holder<ArmorMaterial> SPECIAL_POLICE = MATERIALS.register("special_police", () -> new ArmorMaterial(
            Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 3);
                m.put(ArmorItem.Type.LEGGINGS, 6);
                m.put(ArmorItem.Type.CHESTPLATE, 8);
                m.put(ArmorItem.Type.HELMET, 3);
                m.put(ArmorItem.Type.BODY, 11);
            }),
            15, SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(Items.NETHERITE_INGOT),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath("academy", "special_police"))),
            3.0f, 0.1f));
}
