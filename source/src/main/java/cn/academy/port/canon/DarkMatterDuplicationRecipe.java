package cn.academy.port.canon;

import cn.academy.port.AcademyConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shapeless Dark Matter Crystal duplication recipe:
 * 1 {@code academy:dark_matter_crystal} + 1 replicable item -> 2 identical copies preserving all DataComponents.
 *
 * <ul>
 *   <li>Stackable items ({@code maxStackSize >= 2}): {@link #assemble} yields {@code copyWithCount(2)} while
 *       {@link #getRemainingItems} returns empty slots so 1 crystal + 1 source item become 2 copies.</li>
 *   <li>Unstackable items ({@code maxStackSize == 1}, such as tools, weapons, armor, enchanted books):
 *       {@link #assemble} yields {@code copyWithCount(1)} while {@link #getRemainingItems} leaves an exact
 *       {@code copyWithCount(1)} in the source slot and consumes the crystal slot, turning 1 item into 2.</li>
 * </ul>
 */
public final class DarkMatterDuplicationRecipe extends CustomRecipe {
    public static final TagKey<Item> UNREPLICABLE = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("academy", "dark_matter_unreplicable"));

    public DarkMatterDuplicationRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** Shared eligibility rule for Dark Matter crafting duplication and Dark Synthesis. */
    public static boolean canDuplicate(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || stack.is(CanonAuxItems.DARK_MATTER_CRYSTAL.get())
                || stack.is(UNREPLICABLE)) {
            return false;
        }
        if (!isContainerAllowed() && hasContainerPayload(stack)) {
            return false;
        }
        return true;
    }

    private static boolean isContainerAllowed() {
        try {
            return AcademyConfig.SPEC.isLoaded() && AcademyConfig.DARK_MATTER_ALLOW_CONTAINERS.get();
        } catch (IllegalStateException unloaded) {
            return false;
        }
    }

    private static boolean hasContainerPayload(ItemStack stack) {
        if (stack.getItem() instanceof BundleItem) {
            BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null && !bundle.isEmpty()) return true;
        }
        if (stack.has(DataComponents.CONTAINER_LOOT) || stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
            return true;
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) {
            return true;
        }
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        return container != null && container.nonEmptyItems().iterator().hasNext();
    }

    private record Match(int crystalSlot, int targetSlot, ItemStack target) {}

    private static Match findMatch(CraftingInput input) {
        int crystalSlot = -1;
        int targetSlot = -1;
        ItemStack target = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack at = input.getItem(i);
            if (at.isEmpty()) continue;
            if (at.is(CanonAuxItems.DARK_MATTER_CRYSTAL.get())) {
                if (crystalSlot != -1) return null;
                crystalSlot = i;
            } else if (canDuplicate(at)) {
                if (targetSlot != -1) return null;
                targetSlot = i;
                target = at;
            } else {
                return null;
            }
        }
        return crystalSlot != -1 && targetSlot != -1 ? new Match(crystalSlot, targetSlot, target) : null;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findMatch(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Match match = findMatch(input);
        if (match == null) return ItemStack.EMPTY;
        int outCount = match.target().getMaxStackSize() >= 2 ? 2 : 1;
        return match.target().copyWithCount(outCount);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Match match = findMatch(input);
        if (match != null && match.target().getMaxStackSize() == 1) {
            remaining.set(match.targetSlot(), match.target().copyWithCount(1));
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CanonAuxItems.DARK_MATTER_DUPLICATION.get();
    }
}
