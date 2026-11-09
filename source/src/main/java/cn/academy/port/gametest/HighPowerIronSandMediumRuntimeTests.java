package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.IronSandMedium;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native item/recipe/slot and ledger checks; ability lifecycle and combat belong to the lance suite. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerIronSandMediumRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_iron_sand_medium";
    private HighPowerIronSandMediumRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_native_recipe_selection_and_stack_crafting_preserve_nine_per_ingot(GameTestHelper h) {
        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath("academy", "iron_sand");
        h.assertTrue(BuiltInRegistries.ITEM.getKey(IronSandMedium.ITEM.get()).equals(itemId), "genuine academy item registration");
        var manager = h.getLevel().getRecipeManager();
        CraftingInput ingot = CraftingInput.of(1, 1, List.of(new ItemStack(Items.IRON_INGOT)));
        var vanilla = manager.getRecipeFor(RecipeType.CRAFTING, ingot, h.getLevel()).orElseThrow();
        ItemStack nuggets = vanilla.value().assemble(ingot, h.getLevel().registryAccess());
        h.assertTrue(vanilla.id().getNamespace().equals("minecraft") && nuggets.is(Items.IRON_NUGGET) && nuggets.getCount() == 9,
                "ordinary recipe selection preserves vanilla one-ingot to nine-nuggets crafting");
        CraftingInput input = CraftingInput.of(1, 1, List.of(new ItemStack(Items.IRON_NUGGET)));
        var holder = manager.getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        h.assertTrue(holder.id().equals(ResourceLocation.fromNamespaceAndPath("academy", "highpower/iron_sand")),
                "ordinary recipe selection reaches iron sand without a preferred recipe ID");
        h.assertValueEqual(manager.getRecipesFor(RecipeType.CRAFTING, input, h.getLevel()).size(), 1, "one-nugget input has exactly one matching recipe");
        CraftingRecipe recipe = holder.value();
        h.assertTrue(recipe.matches(input, h.getLevel()), "one actual iron nugget is the sand ingredient");
        h.assertFalse(recipe.matches(CraftingInput.of(1, 1, List.of(new ItemStack(Items.SAND))), h.getLevel()), "terrain sand is not a medium ingredient");
        h.assertFalse(recipe.matches(ingot, h.getLevel()), "sand recipe cannot compete with vanilla ingot-to-nuggets recipe");
        h.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(new ItemStack(Items.IRON_NUGGET), new ItemStack(Items.IRON_NUGGET))), h.getLevel()), "extra occupied ingredient cannot be silently lost");
        ItemStack output = recipe.assemble(input, h.getLevel().registryAccess());
        h.assertTrue(output.is(IronSandMedium.ITEM.get()) && output.getCount() == 1 && output.getMaxStackSize() == 64, "one nugget yields one stackable sand unit");
        h.assertFalse(output.has(DataComponents.CUSTOM_DATA) || output.isDamageableItem(), "no reservoir, token, charge or durability component");
        ItemStack restored = ItemStack.parse(h.getLevel().registryAccess(), output.save(h.getLevel().registryAccess())).orElseThrow();
        h.assertTrue(ItemStack.matches(output, restored), "native item codec preserves ordinary finite stock");
        // Exercise the same ordinary 2x2 ResultSlot used in the player's inventory, including
        // actual ingredient consumption and the repeated native shift-craft path for a stack of nine.
        try (var actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)) {
            ServerPlayer player = actors.player(); var menu = player.inventoryMenu;
            menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
            h.assertTrue(menu.getSlot(0).getItem().is(Items.IRON_NUGGET) && menu.getSlot(0).getItem().getCount() == 9,
                    "visible native inventory result remains nine vanilla nuggets");
            ItemStack earnedNuggets = menu.getSlot(0).remove(9);
            menu.getSlot(0).onTake(player, earnedNuggets);
            h.assertTrue(menu.getSlot(1).getItem().isEmpty() && earnedNuggets.getCount() == 9, "native result take consumes exactly one ingot");
            menu.getSlot(1).set(earnedNuggets);
            h.assertTrue(menu.getSlot(0).getItem().is(IronSandMedium.ITEM.get()) && menu.getSlot(0).getItem().getCount() == 1,
                    "nine nuggets in one occupied grid cell display one sand per craft");
            menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
            int earnedSand = player.getInventory().items.stream().filter(stack -> stack.is(IronSandMedium.ITEM.get())).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(earnedSand == 9 && menu.getSlot(1).getItem().isEmpty() && menu.getSlot(0).getItem().isEmpty(),
                    "one native shift-craft consumes all nine nuggets and deposits exactly nine sand");
            h.assertTrue(player.getInventory().items.stream().allMatch(stack -> stack.isEmpty() || stack.is(IronSandMedium.ITEM.get())),
                    "two-stage route leaves no ingot, nugget or duplicate recovery item");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_charge_snapshot_requires_exact_offhand_identity_count_and_components(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            ItemStack stock = new ItemStack(IronSandMedium.ITEM.get(), 9);
            f.player.setItemSlot(EquipmentSlot.MAINHAND, stock);
            h.assertTrue(IronSandMedium.capture(f.player) == null, "main hand is not the registered medium slot");
            f.player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            f.player.setItemSlot(EquipmentSlot.OFFHAND, stock);
            var snapshot = IronSandMedium.capture(f.player);
            h.assertTrue(IronSandMedium.matches(f.player, snapshot), "free charge captures the existing stack");
            f.player.setItemSlot(EquipmentSlot.OFFHAND, stock.copy());
            h.assertFalse(IronSandMedium.matches(f.player, snapshot), "identical-looking replacement object cannot inherit a charge");
            f.player.setItemSlot(EquipmentSlot.OFFHAND, stock);
            snapshot = IronSandMedium.capture(f.player); stock.shrink(1);
            h.assertFalse(IronSandMedium.matches(f.player, snapshot), "even still-sufficient stock mutation invalidates the captured count");
            snapshot = IronSandMedium.capture(f.player); stock.set(DataComponents.CUSTOM_NAME, Component.literal("changed during charge"));
            h.assertFalse(IronSandMedium.matches(f.player, snapshot), "component mutation invalidates a charge");
            f.stock(2); h.assertTrue(IronSandMedium.capture(f.player) == null, "fewer than three units refuses before spending");
            f.stock(65); h.assertTrue(IronSandMedium.capture(f.player) == null, "corrupt overstack refuses");
            f.player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_INGOT, 9));
            h.assertTrue(IronSandMedium.capture(f.player) == null, "ordinary iron is not already registered iron sand");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_native_hand_swap_round_trip_retires_the_old_charge(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            ItemStack stock = f.stock(9); var snapshot = IronSandMedium.capture(f.player);
            swap(f.player); swap(f.player);
            h.assertTrue(f.player.getOffhandItem() == stock && stock.getCount() == 9, "actual native swap hooks return the same object and stock");
            h.assertFalse(IronSandMedium.matches(f.player, snapshot), "F-swap round trip invalidates even with no intervening charge tick");
            h.assertTrue(IronSandMedium.matches(f.player, IronSandMedium.capture(f.player)), "fresh charge may use the unchanged returned stock");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_commit_spends_exactly_three_once_and_native_reload_cannot_refund(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.ready(); ItemStack stock = f.stock(9); var snapshot = IronSandMedium.capture(f.player);
            double cp = ElectromasterPrototype.prototypeCp(f.player); int changed = f.player.getInventory().getTimesChanged();
            h.assertTrue(IronSandMedium.commit(f.player, snapshot, HighPowerSkills.IRON_SAND_LANCE, 5, 20), "one reviewed medium plus prototype-ledger transaction");
            h.assertTrue(stock.getCount() == 6 && f.player.getOffhandItem() == stock, "exactly three consumed from the captured native stack");
            h.assertTrue(ElectromasterPrototype.prototypeCp(f.player) == cp - 5, "CP debited once");
            h.assertTrue(AbilityStorage.get(f.player).cooldowns.get(HighPowerSkills.IRON_SAND_LANCE) == 20, "native skill cooldown set once");
            h.assertTrue(f.player.getInventory().getTimesChanged() > changed, "authoritative inventory was marked changed");
            h.assertFalse(IronSandMedium.commit(f.player, snapshot, HighPowerSkills.IRON_SAND_LANCE, 5, 20), "same captured release cannot replay");
            ListTag saved = f.player.getInventory().save(new ListTag());
            f.player.getInventory().load(saved);
            h.assertTrue(f.player.getOffhandItem().is(IronSandMedium.ITEM.get()) && f.player.getOffhandItem().getCount() == 6, "ordinary native inventory reload preserves postcommit stock");
            h.assertFalse(IronSandMedium.matches(f.player, snapshot), "old transient charge never revives on inventory reload");
            h.assertFalse(IronSandMedium.commit(f.player, IronSandMedium.capture(f.player), HighPowerSkills.IRON_SAND_LANCE, 5, 20), "new charge cannot bypass saved prototype deadline");
            h.assertTrue(f.player.getOffhandItem().getCount() == 6 && ElectromasterPrototype.prototypeCp(f.player) == cp - 5, "refused retry neither refunds nor spends again");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_refused_budget_changed_stack_and_creative_leave_both_ledgers_unchanged(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.ready(); ItemStack stock = f.stock(3); double cp = ElectromasterPrototype.prototypeCp(f.player);
            var refused = IronSandMedium.capture(f.player);
            h.assertFalse(IronSandMedium.commit(f.player, refused, HighPowerSkills.IRON_SAND_LANCE, cp + 1, 20), "insufficient prototype CP refuses");
            h.assertFalse(IronSandMedium.matches(f.player, refused), "failed commit attempt is terminal for this charge");
            h.assertTrue(stock.getCount() == 3 && ElectromasterPrototype.prototypeCp(f.player) == cp, "budget refusal preserves exactly three available units and CP");
            var changed = IronSandMedium.capture(f.player); ItemStack replacement = f.stock(9);
            h.assertFalse(IronSandMedium.commit(f.player, changed, HighPowerSkills.IRON_SAND_LANCE, 5, 20), "slot replacement before commit refuses");
            h.assertTrue(stock.getCount() == 3 && replacement.getCount() == 9 && ElectromasterPrototype.prototypeCp(f.player) == cp, "neither detached original nor replacement is consumed");
            var creative = IronSandMedium.capture(f.player); f.player.getAbilities().instabuild = true;
            h.assertTrue(IronSandMedium.capture(f.player) == null, "creative infinite-materials mode cannot begin a charge");
            h.assertFalse(IronSandMedium.commit(f.player, creative, HighPowerSkills.IRON_SAND_LANCE, 5, 20), "creative switch after capture cannot waive materials");
            h.assertTrue(replacement.getCount() == 9 && ElectromasterPrototype.prototypeCp(f.player) == cp, "creative refusal changes neither resource");
            f.player.getAbilities().instabuild = false;
            h.assertFalse(IronSandMedium.commit(f.player, IronSandMedium.capture(f.player), HighPowerSkills.IRON_SAND_LANCE, Double.NaN, 20), "malformed resource request refuses");
            h.assertTrue(replacement.getCount() == 9 && ElectromasterPrototype.prototypeCp(f.player) == cp, "malformed request changes neither resource");
            h.succeed();
        }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_last_three_units_leave_empty_native_slot_without_recovery_items(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.ready(); ItemStack stock = f.stock(3);
            h.assertTrue(IronSandMedium.commit(f.player, IronSandMedium.capture(f.player), HighPowerSkills.IRON_SAND_LANCE, 5, 20), "exact minimum commits");
            h.assertTrue(stock.getCount() == 0 && f.player.getOffhandItem().isEmpty(), "minimum stock becomes empty without negative count");
            h.assertTrue(f.player.getInventory().save(new ListTag()).isEmpty(), "native inventory persists no empty or refund medium");
            h.assertTrue(IronSandMedium.capture(f.player) == null, "empty postcommit slot cannot charge again");
            h.succeed();
        }
    }

    private static void swap(ServerPlayer player) {
        var event = CommonHooks.onLivingSwapHandItems(player);
        if (!event.isCanceled()) {
            player.setItemSlot(EquipmentSlot.OFFHAND, event.getItemSwappedToOffHand());
            player.setItemSlot(EquipmentSlot.MAINHAND, event.getItemSwappedToMainHand());
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper; final ServerPlayer player;
        Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-IronSand]"));
            player.getAbilities().instabuild = false;
        }
        ItemStack stock(int count) {
            ItemStack stack = new ItemStack(IronSandMedium.ITEM.get(), count);
            player.setItemSlot(EquipmentSlot.OFFHAND, stack); return stack;
        }
        void ready() {
            helper.assertTrue(HighPowerCombat.enroll(player, 3), "native LV3 medium fixture enrollment");
            var state = AbilityStorage.get(player); state.selectCategory("electromaster"); state.setLevel(3);
            state.learn(HighPowerSkills.FORKED_ARC); state.learn(HighPowerSkills.POLARITY_TETHER); state.learn(HighPowerSkills.IRON_SAND_LANCE);
            state.activated = true; state.overloadFine = true; ElectromasterPrototype.initializeBudget(player);
        }
        @Override public void close() { AbilityStorage.remove(player); player.discard(); }
    }
}
