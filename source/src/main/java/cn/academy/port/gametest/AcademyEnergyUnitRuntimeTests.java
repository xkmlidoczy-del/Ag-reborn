package cn.academy.port.gametest;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.energy.ClassicEnergy;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.energy.ClassicEnergyRecipes;
import cn.academy.port.energy.ClassicEnergyUnitRecipe;
import cn.academy.port.energy.ClassicEnergyUnitItem;
import cn.academy.port.energy.ClassicItemEnergy;
import cn.academy.port.skill.ChargingEnergy;
import cn.academy.port.survival.ClassicMaterials;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real registry/stack/recipe/capability coverage. Written and compiled; parent owns native execution. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademyEnergyUnitRuntimeTests {
    private static final String TEMPLATE = "runtime_empty", BATCH = "academy_energy_units";
    private AcademyEnergyUnitRuntimeTests() {}
    private static ItemStack unit() { return new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get()); }
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("academy", value); }
    private static void close(GameTestHelper helper, double expected, double actual, String label) {
        helper.assertTrue(Double.isFinite(actual) && Math.abs(expected - actual) < .00000001, label + ": " + actual);
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void energy_unit_registered_empty_nonstackable_source_tooltip_and_gauge(GameTestHelper helper) {
        var item = ClassicEnergyItems.ENERGY_UNIT.get(); var stack = unit();
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(item).equals(id("energy_unit")), "authentic source item identity");
        helper.assertValueEqual(stack.getMaxStackSize(), 1, "source nonstackable item");
        helper.assertValueEqual(stack.getMaxDamage(), 13, "source13-step gauge metadata");
        helper.assertValueEqual(stack.getDamageValue(), 13, "new unit starts with empty gauge");
        close(helper, 0, ClassicEnergyItemHelper.getEnergy(stack), "new unit default energy empty");
        close(helper, 10000, item.getMaxEnergy(), "source capacity"); close(helper, 20, item.getBandwidth(), "source bandwidth");
        var tooltip = new ArrayList<Component>(); item.appendHoverText(stack, Item.TooltipContext.of(helper.getLevel()), tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.stream().anyMatch(component -> component.getString().equals("0/10000 IF")), "source rounded IF display");
        helper.assertValueEqual(ClassicEnergyUnitItem.iconLevel(stack), 0, "empty model property");
        helper.assertValueEqual(item.getBarWidth(stack), 0, "empty source gauge width");
        var full = ClassicEnergyItemHelper.createFullItem(item);
        close(helper, 10000, ClassicEnergyItemHelper.getEnergy(full), "explicit full creative helper");
        helper.assertValueEqual(ClassicEnergyUnitItem.iconLevel(full), 2, "full model property");
        helper.assertFalse(item.isBarVisible(full), "source full gauge hidden");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void energy_unit_native_signed_transfer_copy_and_capability_conserve_finite_if(GameTestHelper helper) {
        var stack = unit(); var nativeEnergy = new ClassicItemEnergy(stack);
        close(helper, 15.125, nativeEnergy.charge(35.125, false), "native source bandwidth and fractional leftover");
        close(helper, 20, nativeEnergy.energy(), "native source accepted20IF");
        var independent = stack.copy(); ClassicEnergyItemHelper.setEnergy(independent, 3.125);
        close(helper, 20, nativeEnergy.energy(), "stack-owned copy independence");
        close(helper, -96.875, ClassicEnergyItemHelper.charge(independent, -100, true), "signed negative charge cannot underflow");
        close(helper, 0, ClassicEnergyItemHelper.getEnergy(independent), "signed discharge finite lower bound");
        var fe = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        helper.assertTrue(fe != null && fe.canReceive() && fe.canExtract(), "real registered finite capability");
        helper.assertValueEqual(fe.getMaxEnergyStored(), 40000, "source4:1 finite FE capacity");
        helper.assertValueEqual(fe.receiveEnergy(999999, true), 80, "FE receive bandwidth simulation");
        close(helper, 20, nativeEnergy.energy(), "simulate cannot mutate");
        helper.assertValueEqual(fe.receiveEnergy(999999, false), 80, "FE receive bandwidth actual");
        close(helper, 40, nativeEnergy.energy(), "FE80 converts20IF");
        helper.assertValueEqual(fe.extractEnergy(1, false), 1, "smallest FE extraction");
        close(helper, 39.75, nativeEnergy.energy(), "small FE extraction preserves quarterIF");
        nativeEnergy.energy(.125); helper.assertValueEqual(fe.extractEnergy(1, false), 0, "sub-FE cannot create whole FE");
        nativeEnergy.energy(9999.875); helper.assertValueEqual(fe.receiveEnergy(1, false), 0, "sub-FE headroom preserves capacity");
        close(helper, 9999.875, nativeEnergy.energy(), "fractional IF retained");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void energy_unit_native_component_codec_roundtrip_and_corruption_bounds(GameTestHelper helper) {
        var stack = unit(); var tag = new CompoundTag(); tag.putString("other_mod", "kept");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); ClassicEnergyItemHelper.setEnergy(stack, 1234.625);
        var decoded = ItemStack.parse(helper.getLevel().registryAccess(), stack.save(helper.getLevel().registryAccess())).orElseThrow();
        close(helper, 1234.625, ClassicEnergyItemHelper.getEnergy(decoded), "native item codec fractional round trip");
        helper.assertTrue(decoded.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("other_mod").equals("kept"), "foreign custom data preserved by codec");
        for (double corrupt : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            CustomData.update(DataComponents.CUSTOM_DATA, decoded, data -> data.putDouble(ClassicItemEnergy.KEY, corrupt));
            close(helper, 0, ClassicEnergyItemHelper.getEnergy(decoded), "malformed native storage read finite");
            decoded.getItem().verifyComponentsAfterLoad(decoded);
            helper.assertValueEqual(decoded.getDamageValue(), 13, "corrupt native gauge cannot look full");
            close(helper, 0, decoded.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble(ClassicItemEnergy.KEY), "load normalization stores safe value");
        }
        ClassicEnergyItemHelper.setEnergy(decoded, 100000);
        close(helper, 10000, ClassicEnergyItemHelper.getEnergy(decoded), "overcapacity native setter clamps");
        helper.assertValueEqual(decoded.getDamageValue(), 0, "full source rounded gauge");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void energy_unit_multiyield_and_retained_capability_cannot_duplicate_payload(GameTestHelper helper) {
        for (int count : new int[] {2, 4}) {
            var result = unit(); result.setCount(count); var nativeEnergy = new ClassicItemEnergy(result);
            close(helper, 99.625, nativeEnergy.charge(99.625, true), "multi-unit recipe cursor cannot fill shared payload");
            var capability = result.getCapability(Capabilities.EnergyStorage.ITEM);
            helper.assertTrue(capability != null && !capability.canReceive() && !capability.canExtract(), "multi-unit recipe result finite capability is unavailable until split");
            helper.assertValueEqual(capability.receiveEnergy(80, false), 0, "multi-count capability cannot duplicate charge");
            var one = result.split(1); close(helper, 0, ClassicEnergyItemHelper.getEnergy(one), "source multi-yield splits empty");
            close(helper, 0, ClassicEnergyItemHelper.charge(one, .125, true), "single split accepts native fractional IF");
            close(helper, .125, ClassicEnergyItemHelper.getEnergy(one), "single split owns its own payload");
            close(helper, 0, ClassicEnergyItemHelper.getEnergy(result), "sibling result remains empty");
            // Malformed external/save data can contain a previously charged single
            // whose count was inflated. Native item decode must clear the shared charge.
            var malformed = unit(); ClassicEnergyItemHelper.setEnergy(malformed, 42.625); malformed.setCount(count);
            var decoded = ItemStack.parse(helper.getLevel().registryAccess(), malformed.save(helper.getLevel().registryAccess())).orElseThrow();
            helper.assertValueEqual(decoded.getCount(), count, "native codec retains legitimate multi-unit count");
            while (!decoded.isEmpty()) {
                var single = decoded.split(1);
                close(helper, 0, ClassicEnergyItemHelper.getEnergy(single), "corrupt multi-count save cannot split into charged singles");
                helper.assertValueEqual(single.getDamageValue(), 13, "corrupt multi-count saved gauge normalized empty");
            }
        }
        var stack = unit(); ClassicEnergyItemHelper.setEnergy(stack, 40); var retained = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        stack.shrink(1);
        helper.assertValueEqual(retained.extractEnergy(80, false), 0, "emptied stack invalidates retained extraction");
        helper.assertValueEqual(retained.receiveEnergy(80, false), 0, "emptied stack invalidates retained receiving");
        helper.assertValueEqual(retained.getEnergyStored(), 0, "emptied retained capability has no phantom energy");
        helper.assertValueEqual(retained.getMaxEnergyStored(), 0, "emptied retained capability has no phantom capacity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void energy_unit_exact_crystal_recipes_preserve_yield_and_default_empty_state(GameTestHelper helper) {
        String[] names = {"energy_unit_18", "energy_unit_19", "energy_unit_20"};
        Item[] crystals = {ClassicMaterials.CRYSTAL_LOW.get(), ClassicMaterials.CRYSTAL_NORMAL.get(), ClassicMaterials.CRYSTAL_PURE.get()};
        int[] counts = {1, 2, 4};
        for (int i = 0; i < names.length; i++) {
            var slots = new ArrayList<>(List.of(ItemStack.EMPTY, new ItemStack(ClassicMaterials.CONSTRAINT_PLATE.get()), ItemStack.EMPTY,
                    new ItemStack(ClassicMaterials.CONSTRAINT_PLATE.get()), new ItemStack(crystals[i]), new ItemStack(ClassicMaterials.CONSTRAINT_PLATE.get()),
                    ItemStack.EMPTY, new ItemStack(ClassicMaterials.DATA_CHIP.get()), ItemStack.EMPTY));
            var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(id("classic/" + names[i])).orElseThrow().value();
            var input = CraftingInput.of(3, 3, slots);
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "exact source crystal-tier energy recipe");
            var output = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(output.is(ClassicEnergyItems.ENERGY_UNIT.get()), "all crystal tiers yield identical energy unit item");
            helper.assertValueEqual(output.getCount(), counts[i], "source exact1/2/4 yield");
            close(helper, 0, ClassicEnergyItemHelper.getEnergy(output), "source crafted unit has no implicit charge");
            helper.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "source ordinary ingredients consumed");
            slots.set(1, ItemStack.EMPTY);
            helper.assertFalse(recipe.matches(CraftingInput.of(3, 3, slots), helper.getLevel()), "missing plate rejects recipe");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void energy_unit_authentic_converter_recipe_accepts_all_charge_states_and_consumes_unit(GameTestHelper helper) {
        var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(id("classic/energy_convert_component_37")).orElseThrow().value();
        for (double energy : new double[] {0, .125, 5000, 10000}) {
            var battery = unit(); ClassicEnergyItemHelper.setEnergy(battery, energy);
            var input = CraftingInput.of(1, 3, List.of(new ItemStack(ClassicMaterials.CALC_CHIP.get()), battery, new ItemStack(ClassicMaterials.RESO_CRYSTAL.get())));
            helper.assertTrue(recipe.matches(input, helper.getLevel()), "source unspecified metadata accepts all charge states");
            var result = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(ClassicMaterials.ENERGY_CONVERT_COMPONENT.get()), "authentic converter result");
            helper.assertValueEqual(result.getCount(), 1, "source converter yield1");
            helper.assertTrue(recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "source converter consumes unit without empty-container remainder");
        }
        var wrong = CraftingInput.of(1, 3, List.of(new ItemStack(ClassicMaterials.CALC_CHIP.get()), new ItemStack(Items.REDSTONE), new ItemStack(ClassicMaterials.RESO_CRYSTAL.get())));
        helper.assertFalse(recipe.matches(wrong, helper.getLevel()), "no invented substitute energy converter path");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void energy_unit_source_multiyield_serializer_bounds_and_registry_packet_roundtrip(GameTestHelper helper) {
        var serializer = ClassicEnergyRecipes.ENERGY_UNIT_SHAPED.get();
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        String prefix = "{\"group\":\"\",\"category\":\"misc\",\"pattern\":[\" P \",\"PCP\",\" D \"],\"key\":{\"P\":{\"item\":\"academy:constraint_plate\"},\"C\":{\"item\":\"academy:crystal_normal\"},\"D\":{\"item\":\"academy:data_chip\"}},\"result\":";
        var valid = JsonParser.parseString(prefix + "{\"id\":\"academy:energy_unit\",\"count\":2}}");
        helper.assertTrue(RecipeSerializer.SHAPED_RECIPE.codec().codec().parse(ops, valid).error().isPresent(), "vanilla strict shaped codec rejects nonstackable source multiyield");
        helper.assertTrue(serializer.codec().codec().parse(ops, valid).result().isPresent(), "narrow native source-shaped codec accepts exact2-yield recipe");
        for (String result : new String[] {
                "{\"id\":\"academy:energy_unit\",\"count\":3}",
                "{\"id\":\"minecraft:redstone\",\"count\":2}",
                "{\"id\":\"academy:energy_unit\",\"count\":2,\"components\":{\"minecraft:custom_data\":{\"academy:imaginary_energy\":10000}}}",
                "{\"id\":\"academy:energy_unit\",\"count\":2,\"components\":{}}"}) {
            var rejected = serializer.codec().codec().parse(ops, JsonParser.parseString(prefix + result + "}"));
            helper.assertTrue(rejected.error().isPresent(), "serializer rejects invalid count/output/explicit component result");
        }
        for (int i = 0; i < 3; i++) {
            var holder = helper.getLevel().getRecipeManager().byKey(id("classic/energy_unit_" + (18 + i))).orElseThrow();
            helper.assertTrue(holder.value() instanceof ClassicEnergyUnitRecipe, "native recipe manager uses source multi-yield serializer");
            var source = (ClassicEnergyUnitRecipe) holder.value();
            var actualInput = CraftingInput.of(3, 3, source.getIngredients().stream()
                    .map(ingredient -> ingredient.isEmpty() ? ItemStack.EMPTY : ingredient.getItems()[0].copy()).toList());
            var preview = source.getResultItem(helper.getLevel().registryAccess());
            CustomData.update(DataComponents.CUSTOM_DATA, preview, data -> data.putDouble(ClassicItemEnergy.KEY, 10000));
            var assembled = source.assemble(actualInput, helper.getLevel().registryAccess());
            close(helper, 0, ClassicEnergyItemHelper.getEnergy(assembled), "mutated recipe preview cannot fill assembly result");
            if (i == 0) {
                var retained = unit();
                var guarded = new ClassicEnergyUnitRecipe(source.getGroup(), source.category(), source.pattern, retained, source.showNotification());
                ClassicEnergyItemHelper.setEnergy(retained, 10000);
                close(helper, 0, ClassicEnergyItemHelper.getEnergy(guarded.assemble(actualInput, helper.getLevel().registryAccess())), "retained constructor payload cannot fill future assembly");
            }
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                serializer.streamCodec().encode(buffer, source);
                var decoded = serializer.streamCodec().decode(buffer);
                helper.assertValueEqual(decoded.getWidth(), 3, "registry-aware packet preserves shape width");
                helper.assertValueEqual(decoded.getHeight(), 3, "registry-aware packet preserves shape height");
                var output = decoded.getResultItem(helper.getLevel().registryAccess());
                helper.assertValueEqual(output.getCount(), 1 << i, "registry-aware recipe packet preserves exact1/2/4 yield");
                helper.assertTrue(output.is(ClassicEnergyItems.ENERGY_UNIT.get()), "registry-aware packet preserves source item");
                close(helper, 0, ClassicEnergyItemHelper.getEnergy(output), "recipe packet cannot create filled units");
                helper.assertValueEqual(buffer.readableBytes(), 0, "source recipe packet consumed exact bytes");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void energy_unit_current_charging_native_priority_preserves_fractional_bandwidth_and_support(GameTestHelper helper) {
        var stack = unit(); var target = ChargingEnergy.item(stack);
        helper.assertTrue(target.present() && target.supported(), "native unit is a supported current-charging target");
        close(helper, .125, target.charge(20.125, false), "native priority20IF cap with fractional remainder");
        close(helper, 20, ClassicEnergyItemHelper.getEnergy(stack), "native priority does not round request into FE");
        ClassicEnergyItemHelper.setEnergy(stack, 0);
        var state = new AbilityProgress(); state.selectCategory("electromaster"); state.setLevel(1); state.activated = true; state.experience.put("charging", 1.0);
        var session = CurrentChargingSession.begin(state, true, false);
        helper.assertTrue(session != null && session.active(), "learned native charging session starts");
        double before = state.cp; var result = session.tick(target, false);
        helper.assertTrue(result == CurrentChargingSession.TickResult.CONTINUE, "current charging tick continues");
        close(helper, 20, ClassicEnergyItemHelper.getEnergy(stack), "source mastery35IF request is capped by energy-unit20IF bandwidth");
        close(helper, before - 7, state.cp, "source captured mastery CP consumption");
        ClassicEnergyItemHelper.setEnergy(stack, 10000); helper.assertTrue(target.supported(), "full finite item stays supported");
        close(helper, 35, target.charge(35, false), "full finite item returns full remainder");
        close(helper, 10000, ClassicEnergyItemHelper.getEnergy(stack), "full item cannot exceed capacity");
        helper.succeed();
    }
}
