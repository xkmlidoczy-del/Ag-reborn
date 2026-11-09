/* AcademyCraft 1.0.7 MetalFormerRecipes and conditional ore-dictionary rules. GPLv3. */
package cn.academy.port.former;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class ClassicMetalFormerRecipes {
    public record Recipe(ClassicMetalFormerWork.Mode mode,ItemStack input,ItemStack output) {
        public boolean accepts(ItemStack stack,ClassicMetalFormerWork.Mode requested){return !stack.isEmpty()&&mode==requested&&stack.getItem()==input.getItem()&&stack.getDamageValue()==input.getDamageValue()&&stack.getCount()>=input.getCount();}
    }
    private ClassicMetalFormerRecipes(){}
    private static Item item(String id){var key=ResourceLocation.parse(id);if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing genuine Metal Former item "+id);return BuiltInRegistries.ITEM.get(key);}
    public static List<Recipe> builtIns(){var list=new ArrayList<Recipe>();for(var rule:ClassicMetalFormerRules.BUILT_INS)list.add(new Recipe(rule.mode(),new ItemStack(item(rule.input()),rule.inputCount()),new ItemStack(item(rule.output()),rule.outputCount())));return List.copyOf(list);}
    private static List<ItemStack> dictionary(String name){var tag=TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c",name));var list=new ArrayList<ItemStack>();BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders->holders.forEach(holder->list.add(new ItemStack(holder.value()))));return list;}
    /** Resolve each scan against current tags/recipes, so reload cannot retain stale compatibility rules. */
    public static List<Recipe> all(Level level){
        var list=new ArrayList<>(builtIns());
        // Source ordinary ore-dictionary aliases. Every block under these c: tags, including vanilla
        // deepslate and nether variants, refines like its stone-era source ore.
        String[] names={"gold","iron","emerald","quartz","diamond","redstone","lapis","coal"};
        for(int i=0;i<names.length;i++)for(var ore:dictionary("ores/"+names[i])){
            var base=list.get(9+i);if(!refines(list,ore))list.add(new Recipe(ClassicMetalFormerWork.Mode.REFINE,ore,base.output().copy()));
        }
        if(level!=null)for(var metal:ClassicMetalFormerRules.CONDITIONAL_METALS){
            var output=dictionary("ingots/"+metal);if(output.isEmpty())continue;
            var ores=dictionary("ores/"+metal);if(ores.isEmpty())continue;
            var smelt=level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(ores.getFirst()),level);
            if(smelt.isEmpty())continue;var result=smelt.get().value().assemble(new SingleRecipeInput(ores.getFirst()),level.registryAccess());if(result.isEmpty())continue;
            var product=output.getFirst().copy();product.setCount(ClassicMetalFormerRules.refinedCount(result.getCount()));
            for(var ore:ores)list.add(new Recipe(ClassicMetalFormerWork.Mode.REFINE,ore,product.copy()));
        }
        if(level!=null)modernRefining(level,list);
        return List.copyOf(list);
    }
    /** Modern additions: raw ores triple their furnace yield, and any other tagged ore with a furnace result doubles it. */
    private static void modernRefining(Level level,List<Recipe> list){
        for(var raw:dictionary("raw_materials")){
            var smelted=smelt(level,raw);if(!smelted.isEmpty()&&!refines(list,raw))list.add(new Recipe(ClassicMetalFormerWork.Mode.REFINE,raw,sized(smelted,ClassicMetalFormerRules.rawRefinedCount(smelted.getCount()))));
        }
        BuiltInRegistries.ITEM.getTagNames().map(TagKey::location).filter(id->id.getNamespace().equals("c")&&id.getPath().startsWith("storage_blocks/raw_")).sorted().forEach(id->{
            var raws=dictionary("raw_materials/"+id.getPath().substring("storage_blocks/raw_".length()));if(raws.isEmpty())return;
            var smelted=smelt(level,raws.getFirst());if(smelted.isEmpty())return;
            for(var block:dictionary(id.getPath()))if(!refines(list,block))list.add(new Recipe(ClassicMetalFormerWork.Mode.REFINE,block,sized(smelted,ClassicMetalFormerRules.rawBlockRefinedCount(smelted.getCount()))));
        });
        for(var ore:dictionary("ores")){
            if(refines(list,ore))continue;var smelted=smelt(level,ore);
            if(!smelted.isEmpty())list.add(new Recipe(ClassicMetalFormerWork.Mode.REFINE,ore,sized(smelted,ClassicMetalFormerRules.refinedCount(smelted.getCount()))));
        }
    }
    private static boolean refines(List<Recipe> list,ItemStack input){return list.stream().anyMatch(recipe->recipe.mode()==ClassicMetalFormerWork.Mode.REFINE&&recipe.input().getItem()==input.getItem());}
    private static ItemStack smelt(Level level,ItemStack input){var in=new SingleRecipeInput(input);return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,in,level).map(recipe->recipe.value().assemble(in,level.registryAccess())).orElse(ItemStack.EMPTY);}
    private static ItemStack sized(ItemStack stack,int count){var copy=stack.copy();copy.setCount(count);return copy;}
    public static int find(List<Recipe> recipes,ItemStack stack,ClassicMetalFormerWork.Mode mode){for(int i=0;i<recipes.size();i++)if(recipes.get(i).accepts(stack,mode))return i;return -1;}
    public static boolean guiInput(ItemStack stack,Level level){if(stack.isEmpty())return false;return all(level).stream().anyMatch(recipe->recipe.input().getItem()==stack.getItem());}
}
