/* Optional JEI recipe pages for the Metal Former and Imag Fusor. Loaded by JEI only; ACA never references this class. */
package cn.academy.port.client.jei;
import cn.academy.port.former.*;
import cn.academy.port.fusion.*;
import cn.academy.port.survival.ClassicMaterials;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class AcademyJeiPlugin implements IModPlugin {
    public record FusorRecipe(ItemStack input,int liquid,ItemStack output) {}
    private static final Map<ClassicMetalFormerWork.Mode,RecipeType<ClassicMetalFormerRecipes.Recipe>> FORMER=new EnumMap<>(ClassicMetalFormerWork.Mode.class);
    static{for(var mode:ClassicMetalFormerWork.Mode.values())FORMER.put(mode,RecipeType.create("academy","metal_former_"+mode.name().toLowerCase(Locale.ROOT),ClassicMetalFormerRecipes.Recipe.class));}
    private static final RecipeType<FusorRecipe> FUSOR=RecipeType.create("academy","imag_fusor",FusorRecipe.class);
    /** Source energy per completed job: 60 ticks x 13.3 IF, and 120 ticks x 12 IF. */
    private static final int FORMER_IF=(int)Math.round(ClassicMetalFormerWork.WORK_TICKS*ClassicMetalFormerWork.CONSUME_PER_TICK),FUSOR_IF=(int)Math.round(ClassicFusorWork.CONSUME_PER_TICK/ClassicFusorWork.WORK_SPEED);

    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("academy","jei");}
    @Override public void registerCategories(IRecipeCategoryRegistration registration){
        var gui=registration.getJeiHelpers().getGuiHelper();
        for(var mode:ClassicMetalFormerWork.Mode.values())registration.addRecipeCategories(new FormerCategory(gui,mode));
        registration.addRecipeCategories(new FusorCategory(gui));
    }
    @Override public void registerRecipes(IRecipeRegistration registration){
        // Same resolver the machine uses, so tag-driven deepslate, raw and third-party ores match the world.
        var level=Minecraft.getInstance().level;
        var recipes=level==null?ClassicMetalFormerRecipes.builtIns():ClassicMetalFormerRecipes.all(level);
        for(var mode:ClassicMetalFormerWork.Mode.values())registration.addRecipes(FORMER.get(mode),recipes.stream().filter(recipe->recipe.mode()==mode).toList());
        var fusor=new ArrayList<FusorRecipe>();
        for(var input:List.of(ClassicMaterials.CRYSTAL_LOW.get(),ClassicMaterials.CRYSTAL_NORMAL.get())){
            var stack=new ItemStack(input);int recipe=ClassicFusorBlockEntity.recipe(stack);
            fusor.add(new FusorRecipe(stack,ClassicFusorWork.liquidRequired(recipe),new ItemStack(recipe==0?ClassicMaterials.CRYSTAL_NORMAL.get():ClassicMaterials.CRYSTAL_PURE.get())));
        }
        registration.addRecipes(FUSOR,fusor);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration){
        registration.addRecipeCatalyst(new ItemStack(ClassicMetalFormer.ITEM.get()),FORMER.values().toArray(RecipeType[]::new));
        registration.addRecipeCatalyst(new ItemStack(ClassicFusion.FUSOR_ITEM.get()),FUSOR);
    }

    /**
     * Built only on IRecipeCategory members that exist since JEI 19.8 (draw, blank background, slot drawable),
     * so players on older 1.21.1 JEI builds such as 19.39 do not hit NoSuchMethodError in widget helpers.
     */
    private abstract static class Category<T> implements IRecipeCategory<T> {
        private static final ResourceLocation FURNACE=ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png"),PROGRESS=ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
        private final RecipeType<T> type;private final Component title;private final IDrawable icon,background,slot;private final int width,arrowX,textX,arrowTicks;private final Component energy;
        Category(IGuiHelper gui,RecipeType<T> type,Component title,ItemStack icon,int width,int arrowX,int arrowTicks,int textX,int energy){
            this.type=type;this.title=title;this.icon=gui.createDrawableItemStack(icon);this.background=gui.createBlankDrawable(width,26);this.slot=gui.getSlotDrawable();
            this.width=width;this.arrowX=arrowX;this.arrowTicks=arrowTicks;this.textX=textX;this.energy=Component.literal(energy+" IF");
        }
        @Override public RecipeType<T> getRecipeType(){return type;}
        @Override public Component getTitle(){return title;}
        @SuppressWarnings({"deprecation","removal"}) @Override public IDrawable getBackground(){return background;}
        @Override public int getWidth(){return width;}
        @Override public int getHeight(){return 26;}
        @Override public IDrawable getIcon(){return icon;}
        IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder,RecipeIngredientRole role,int x){return builder.addSlot(role,x,5).setBackground(slot,-1,-1);}
        @Override public void draw(T recipe,IRecipeSlotsView slots,GuiGraphics graphics,double mouseX,double mouseY){
            graphics.blit(FURNACE,arrowX,5,79,35,24,16);
            int filled=(int)(System.currentTimeMillis()/50%arrowTicks*24/arrowTicks)+1;
            graphics.blitSprite(PROGRESS,24,16,0,0,arrowX,5,filled,16);
            graphics.drawString(Minecraft.getInstance().font,energy,textX,9,0xFF808080,false);
        }
    }
    private static final class FormerCategory extends Category<ClassicMetalFormerRecipes.Recipe> {
        FormerCategory(IGuiHelper gui,ClassicMetalFormerWork.Mode mode){
            super(gui,FORMER.get(mode),Component.translatable("block.academy.metal_former").append(" · ").append(Component.translatable("ac.gui.metal_former.mode."+mode.name().toLowerCase(Locale.ROOT))),new ItemStack(ClassicMetalFormer.ITEM.get()),116,24,ClassicMetalFormerWork.WORK_TICKS,80,FORMER_IF);
        }
        @Override public void setRecipe(IRecipeLayoutBuilder builder,ClassicMetalFormerRecipes.Recipe recipe,IFocusGroup focuses){
            slot(builder,RecipeIngredientRole.INPUT,1).addItemStack(recipe.input());
            slot(builder,RecipeIngredientRole.OUTPUT,57).addItemStack(recipe.output());
        }
    }
    private static final class FusorCategory extends Category<FusorRecipe> {
        FusorCategory(IGuiHelper gui){super(gui,FUSOR,Component.translatable("block.academy.imag_fusor"),new ItemStack(ClassicFusion.FUSOR_ITEM.get()),148,44,(int)Math.round(1/ClassicFusorWork.WORK_SPEED),100,FUSOR_IF);}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,FusorRecipe recipe,IFocusGroup focuses){
            slot(builder,RecipeIngredientRole.INPUT,1).addItemStack(recipe.input());
            slot(builder,RecipeIngredientRole.INPUT,21).setFluidRenderer(ClassicFusorWork.TANK_SIZE,false,16,16).addFluidStack(ClassicFusion.PHASE_SOURCE.get(),recipe.liquid());
            slot(builder,RecipeIngredientRole.OUTPUT,77).addItemStack(recipe.output());
        }
    }
}
