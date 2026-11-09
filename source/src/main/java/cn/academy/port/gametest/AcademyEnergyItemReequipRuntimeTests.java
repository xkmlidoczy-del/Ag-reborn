package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.energy.ClassicEnergy;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.energy.ClassicItemEnergy;
import com.mojang.authlib.GameProfile;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual registered ItemStack/component checks. Owner-run only; no client or renderer claim. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademyEnergyItemReequipRuntimeTests {
    private static final String TEMPLATE = "runtime_empty", BATCH = "academy_energy_item_reequip";
    private AcademyEnergyItemReequipRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void portable_real_storage_IF_updates_keep_equipped_and_leave_both_stacks_unchanged(GameTestHelper helper) {
        var oldStack = new ItemStack(AcademyCraft.DEVELOPER.get());
        var newStack = oldStack.copy();
        new DeveloperItemEnergy(newStack, DeveloperType.PORTABLE).energy(15);
        accepted(helper, oldStack, newStack, "portable first real 15IF update from absent energy");
        oldStack = newStack.copy();
        newStack = oldStack.copy();
        new DeveloperItemEnergy(newStack, DeveloperType.PORTABLE).charge(15, false);
        accepted(helper, oldStack, newStack, "portable actual bandwidth-limited storage charge");
        helper.assertTrue(ClassicEnergyItemHelper.getEnergy(oldStack) == 15
                && ClassicEnergyItemHelper.getEnergy(newStack) == 30, "hook does not transfer IF");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void energy_unit_real_IF_and_source_damage_gauge_updates_keep_equipped(GameTestHelper helper) {
        var oldStack = new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get());
        ClassicEnergyItemHelper.setEnergy(oldStack, 500);
        var newStack = oldStack.copy();
        helper.assertTrue(ClassicEnergyItemHelper.charge(newStack, 1000, true) == 0, "declared fixture transfer has no spare IF");
        helper.assertTrue(oldStack.getDamageValue() != newStack.getDamageValue(), "exercise an actual changed gauge");
        helper.assertTrue(oldStack.getDamageValue() == ClassicEnergy.damage(500, 10000)
                && newStack.getDamageValue() == ClassicEnergy.damage(1500, 10000), "actual storage wrote source-derived gauges");
        accepted(helper, oldStack, newStack, "energy unit combined IF/gauge update");
        helper.assertTrue(ClassicEnergyItemHelper.getEnergy(oldStack) == 500
                && ClassicEnergyItemHelper.getEnergy(newStack) == 1500, "hook preserves actual source storage values");
        oldStack = new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get());
        oldStack.remove(DataComponents.CUSTOM_DATA);
        newStack = oldStack.copy();
        new ClassicItemEnergy(newStack).energy(0);
        accepted(helper, oldStack, newStack, "zero IF key initialization uses the existing absent-zero contract");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void actual_inventory_slot_switches_item_changes_counts_and_empty_hands_still_reequip(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "M35SlotFixture"));
        for (boolean portable : new boolean[]{true, false}) {
            var oldStack = charged(portable, 5000);
            var newStack = charged(portable, 5015);
            player.getInventory().setItem(0, oldStack);
            player.getInventory().setItem(1, newStack);
            player.getInventory().selected = 0;
            int oldSlot = player.getInventory().selected;
            ItemStack equipped = player.getMainHandItem();
            player.getInventory().selected = 1;
            helper.assertTrue(equipped.getItem().shouldCauseReequipAnimation(equipped, player.getMainHandItem(),
                    oldSlot != player.getInventory().selected), "actual selected inventory slot change remains visible");
            helper.assertTrue(oldStack.getItem().shouldCauseReequipAnimation(oldStack, oldStack, true),
                    "slot flag remains authoritative even after renderer refreshes an identical reference");
            retainedDefault(helper, oldStack, charged(!portable, 5015), "different registered item");
            newStack = charged(portable, 5015);
            newStack.setCount(2);
            retainedDefault(helper, oldStack, newStack, "changed count cannot be treated as IF-only");
            retainedDefault(helper, oldStack, ItemStack.EMPTY, "empty hand transition");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void unrelated_native_NBT_components_and_arbitrary_damage_keep_exact_inherited_hook(GameTestHelper helper) {
        for (boolean portable : new boolean[]{true, false}) {
            var oldStack = charged(portable, 5000);
            var newStack = charged(portable, 5015);
            CustomData.update(DataComponents.CUSTOM_DATA, newStack, tag -> tag.putString("m35:foreign", "changed"));
            retainedDefault(helper, oldStack, newStack, "added unrelated custom tag");
            newStack = charged(portable, 5015);
            CustomData.update(DataComponents.CUSTOM_DATA, oldStack, tag -> {
                var nested = new CompoundTag(); nested.putString("value", "before"); tag.put("m35:nested", nested);
            });
            CustomData.update(DataComponents.CUSTOM_DATA, newStack, tag -> {
                var nested = new CompoundTag(); nested.putString("value", "after"); tag.put("m35:nested", nested);
            });
            retainedDefault(helper, oldStack, newStack, "changed nested unrelated NBT");
            oldStack = charged(portable, 5000);
            newStack = charged(portable, 5015);
            newStack.set(DataComponents.CUSTOM_NAME, Component.literal("Different name"));
            retainedDefault(helper, oldStack, newStack, "added native name component");
            oldStack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(7));
            newStack = oldStack.copy();
            ClassicEnergyItemHelper.setEnergy(newStack, 5015);
            newStack.remove(DataComponents.CUSTOM_MODEL_DATA);
            retainedDefault(helper, oldStack, newStack, "removed unrelated native component");
            oldStack = charged(portable, 5000);
            newStack = charged(portable, 5015);
            newStack.set(DataComponents.DAMAGE, 12);
            retainedDefault(helper, oldStack, newStack, "portable damage or non-source energy-unit damage");
            newStack = charged(portable, 5015);
            String energyKey = portable ? DeveloperItemEnergy.KEY : ClassicItemEnergy.KEY;
            CustomData.update(DataComponents.CUSTOM_DATA, newStack, tag -> tag.putString(energyKey, "malformed"));
            retainedDefault(helper, oldStack, newStack, "malformed energy payload");
            newStack = charged(portable, 5015);
            String otherKey = portable ? ClassicItemEnergy.KEY : DeveloperItemEnergy.KEY;
            CustomData.update(DataComponents.CUSTOM_DATA, newStack, tag -> tag.putDouble(otherKey, 9));
            retainedDefault(helper, oldStack, newStack, "other item's energy key remains unrelated data");
            retainedDefault(helper, oldStack, oldStack.copy(), "unchanged distinct snapshot keeps inherited default");
            helper.assertFalse(oldStack.getItem().shouldCauseReequipAnimation(oldStack, oldStack, false),
                    "unchanged exact reference keeps inherited default");
        }
        helper.succeed();
    }

    private static ItemStack charged(boolean portable, double energy) {
        var stack = new ItemStack(portable ? AcademyCraft.DEVELOPER.get() : ClassicEnergyItems.ENERGY_UNIT.get());
        ClassicEnergyItemHelper.setEnergy(stack, energy);
        return stack;
    }

    private static void accepted(GameTestHelper helper, ItemStack oldStack, ItemStack newStack, String reason) {
        helper.assertFalse(ItemStack.matches(oldStack, newStack), reason + " changes the actual component snapshot");
        helper.assertTrue(Items.STONE.shouldCauseReequipAnimation(oldStack, newStack, false),
                reason + " exercises a case where the actual inherited default would re-equip");
        // ItemStack.copy invokes verifyComponentsAfterLoad and normalizes absent energy-unit data.
        // Snapshot effective components directly so checking read-only behavior does not normalize it.
        var oldSnapshot = snapshot(oldStack);
        var newSnapshot = snapshot(newStack);
        int oldCount = oldStack.getCount(), newCount = newStack.getCount();
        helper.assertFalse(oldStack.getItem().shouldCauseReequipAnimation(oldStack, newStack, false), reason);
        helper.assertTrue(oldStack.getCount() == oldCount && newStack.getCount() == newCount
                && sameComponents(oldStack.getComponents(), oldSnapshot) && sameComponents(newStack.getComponents(), newSnapshot),
                reason + " comparison is read-only");
    }

    private static DataComponentMap snapshot(ItemStack stack) {
        var builder = DataComponentMap.builder().addAll(stack.getComponents());
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) builder.set(DataComponents.CUSTOM_DATA, CustomData.of(customData.copyTag()));
        return builder.build();
    }

    private static boolean sameComponents(DataComponentMap first, DataComponentMap second) {
        return first.keySet().equals(second.keySet())
                && first.keySet().stream().allMatch(key -> Objects.equals(first.get(key), second.get(key)));
    }

    private static void retainedDefault(GameTestHelper helper, ItemStack oldStack, ItemStack newStack, String reason) {
        boolean expected = Items.STONE.shouldCauseReequipAnimation(oldStack, newStack, false);
        helper.assertTrue(expected, reason + " has a real inherited re-equip transition");
        helper.assertTrue(oldStack.getItem().shouldCauseReequipAnimation(oldStack, newStack, false) == expected,
                reason + " retains the actual inherited hook result");
    }
}
