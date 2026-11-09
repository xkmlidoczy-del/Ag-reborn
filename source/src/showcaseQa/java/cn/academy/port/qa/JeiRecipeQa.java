package cn.academy.port.qa;

import com.mojang.logging.LogUtils;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** QA-only JEI driver: opens real JEI recipe pages for screenshots. Excluded from the distributable JAR. */
@JeiPlugin
public final class JeiRecipeQa implements IModPlugin {
    private static final Logger LOG = LogUtils.getLogger();
    private static IJeiRuntime runtime;

    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("academy", "jei_qa"); }
    @Override public void onRuntimeAvailable(IJeiRuntime jeiRuntime) { runtime = jeiRuntime; LOG.info("QA_JEI_RUNTIME"); }

    /** {@code type:<uid>} shows one category; {@code uses:}, {@code makes:} and {@code catalyst:<item>} show item focus pages. False waits for the runtime. */
    public static boolean step(String arg) {
        if (runtime == null) return false;
        int split = arg.indexOf(':');
        String mode = arg.substring(0, split), id = arg.substring(split + 1);
        var manager = runtime.getRecipeManager();
        switch (mode) {
            case "type" -> {
                var type = manager.getRecipeType(ResourceLocation.parse(id)).orElseThrow(() -> new IllegalStateException("JEI category missing " + id));
                LOG.info("QA_JEI_TYPE {} recipes={}", id, manager.createRecipeLookup(type).get().count());
                runtime.getRecipesGui().showTypes(List.<RecipeType<?>>of(type));
            }
            case "uses", "makes", "catalyst" -> {
                var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
                var role = switch (mode) { case "uses" -> RecipeIngredientRole.INPUT; case "makes" -> RecipeIngredientRole.OUTPUT; default -> RecipeIngredientRole.CATALYST; };
                var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, stack);
                LOG.info("QA_JEI_FOCUS {} {}", mode, id);
                runtime.getRecipesGui().show(focus);
            }
            default -> throw new IllegalStateException("unknown jei step " + arg);
        }
        return true;
    }
}
