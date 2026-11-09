package cn.academy.port.energy;

import cn.academy.port.survival.ClassicMaterials;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Exact source-shaped energy unit recipes with legitimate empty count 1/2/4 output.
 * Vanilla 1.21.1's shaped result STRICT_CODEC otherwise rejects the source count 2/4
 * because this item remains genuinely nonstackable. Matching and mirroring stay native.
 */
public final class ClassicEnergyUnitRecipe extends ShapedRecipe {
    private final int outputCount;

    public ClassicEnergyUnitRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
                                  ItemStack result, boolean showNotification) {
        super(group, category, pattern, validateOutput(result).getOrThrow(), showNotification);
        outputCount = result.getCount();
    }

    @Override public RecipeSerializer<?> getSerializer() { return ClassicEnergyRecipes.ENERGY_UNIT_SHAPED.get(); }

    /** Each preview/assembly is an independent empty source unit payload, even after consumer mutation. */
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get(), outputCount);
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return getResultItem(registries);
    }

    private static DataResult<ItemStack> validateOutput(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ClassicEnergyItems.ENERGY_UNIT.get()))
            return DataResult.error(() -> "Source energy-unit recipe must output academy:energy_unit");
        int count = stack.getCount();
        if (count != 1 && count != 2 && count != 4)
            return DataResult.error(() -> "Source energy-unit recipe count must be 1, 2 or 4");
        if (!ItemStack.isSameItemSameComponents(stack, new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get())))
            return DataResult.error(() -> "Source energy-unit output must have only its default empty components");
        return ItemStack.validateComponents(stack.getComponents()).map(ignored -> stack);
    }

    private static DataResult<ClassicEnergyUnitRecipe> validateRecipe(ClassicEnergyUnitRecipe recipe) {
        var output = validateOutput(recipe.getResultItem(null));
        if (output.isError()) return output.map(ignored -> recipe);
        if (recipe.pattern.width() != 3 || recipe.pattern.height() != 3)
            return DataResult.error(() -> "Source energy-unit recipe must preserve its 3x3 grid");
        Item crystal = switch (recipe.outputCount) {
            case 1 -> ClassicMaterials.CRYSTAL_LOW.get();
            case 2 -> ClassicMaterials.CRYSTAL_NORMAL.get();
            case 4 -> ClassicMaterials.CRYSTAL_PURE.get();
            default -> throw new IllegalStateException("Unvalidated source output count");
        };
        Item plate = ClassicMaterials.CONSTRAINT_PLATE.get();
        Item[] sourceCells = {null, plate, null, plate, crystal, plate, null, ClassicMaterials.DATA_CHIP.get(), null};
        var ingredients = recipe.pattern.ingredients();
        if (ingredients.size() != sourceCells.length)
            return DataResult.error(() -> "Source energy-unit recipe must preserve all nine grid cells");
        for (int i = 0; i < sourceCells.length; i++) {
            var ingredient = ingredients.get(i);
            Item expected = sourceCells[i];
            if (expected == null) {
                if (!ingredient.isEmpty())
                    return DataResult.error(() -> "Source energy-unit recipe has an ingredient in an empty cell");
            } else {
                // Source aliases resolve concrete Items, not tags, alternatives or custom predicates.
                var values = ingredient.getValues();
                if (ingredient.isCustom() || values.length != 1 || !(values[0] instanceof Ingredient.ItemValue value)
                        || !value.item().is(expected))
                    return DataResult.error(() -> "Source energy-unit recipe ingredient/tier does not match its yield");
            }
        }
        return DataResult.success(recipe);
    }

    public static final class Serializer implements RecipeSerializer<ClassicEnergyUnitRecipe> {
        private static final Codec<ItemStack> BOUNDED_OUTPUT = ItemStack.CODEC.validate(ClassicEnergyUnitRecipe::validateOutput);

        /** Inspect the raw result before item-load normalization can erase a forged multi-count charge. */
        private static final Codec<ItemStack> OUTPUT_CODEC = new Codec<>() {
            @Override public <T> DataResult<Pair<ItemStack, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getMap(input).flatMap(map -> {
                    if (map.get("components") != null)
                        return DataResult.error(() -> "Source energy-unit recipe result may not declare components");
                    return BOUNDED_OUTPUT.decode(ops, input);
                });
            }

            @Override public <T> DataResult<T> encode(ItemStack stack, DynamicOps<T> ops, T prefix) {
                // Unit load normalization adds an explicit zero-IF payload; source recipe JSON has none.
                return BOUNDED_OUTPUT.encode(stack, ops, prefix).map(encoded -> ops.remove(encoded, "components"));
            }
        };

        public static final MapCodec<ClassicEnergyUnitRecipe> CODEC = RecordCodecBuilder.<ClassicEnergyUnitRecipe>mapCodec(
                instance -> instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapedRecipe::category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                        OUTPUT_CODEC.fieldOf("result").forGetter(recipe -> recipe.getResultItem(null)),
                        Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(ShapedRecipe::showNotification)
                ).apply(instance, ClassicEnergyUnitRecipe::new)
        ).flatXmap(ClassicEnergyUnitRecipe::validateRecipe, ClassicEnergyUnitRecipe::validateRecipe);

        /** Same native fields/codecs as ShapedRecipe.Serializer, with bounded output/source validation. */
        public static final StreamCodec<RegistryFriendlyByteBuf, ClassicEnergyUnitRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

        @Override public MapCodec<ClassicEnergyUnitRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ClassicEnergyUnitRecipe> streamCodec() { return STREAM_CODEC; }

        private static ClassicEnergyUnitRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            var category = buffer.readEnum(CraftingBookCategory.class);
            var pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            var output = ItemStack.STREAM_CODEC.decode(buffer);
            boolean showNotification = buffer.readBoolean();
            try {
                return validateRecipe(new ClassicEnergyUnitRecipe(group, category, pattern, output, showNotification)).getOrThrow();
            } catch (IllegalStateException exception) {
                throw new DecoderException("Invalid source energy-unit shaped recipe", exception);
            }
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, ClassicEnergyUnitRecipe recipe) {
            try {
                validateRecipe(recipe).getOrThrow();
            } catch (IllegalStateException exception) {
                throw new EncoderException("Invalid source energy-unit shaped recipe", exception);
            }
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.getResultItem(null));
            buffer.writeBoolean(recipe.showNotification());
        }
    }
}
