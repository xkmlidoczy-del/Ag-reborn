/* Opt-in native foreign-tag substitution fixture; excluded from distributed jar. */
package cn.academy.port.gametest;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;

/** Runs only in a named test world whose test-only datapack adds distinct tagged vanilla alternatives. */
@GameTestHolder("academy_common_tags") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyCommonTagRuntimeTests {
    @SubscribeEvent public static void template(LevelEvent.Load event){
        if(!GameTestHooks.isGametestEnabled()||!Boolean.getBoolean("academy.common.tags.qa")||!(event.getLevel() instanceof ServerLevel level))return;
        var tag=new CompoundTag();var size=new ListTag();for(int i=0;i<3;i++)size.add(IntTag.valueOf(8));tag.put("size",size);
        var cell=new CompoundTag();var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));cell.put("pos",pos);cell.putInt("state",0);
        var cells=new ListTag();cells.add(cell);tag.put("blocks",cells);tag.put("entities",new ListTag());
        var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_common_tags","runtime_empty")).load(level.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    private static ItemStack stack(String id){return id.isEmpty()?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));}
    private static void recipe(GameTestHelper h,String name,String output,int width,int height,String... slots){
        var level=h.getLevel();var holder=level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("academy","classic/"+name)).orElseThrow();
        h.assertTrue(holder.value() instanceof CraftingRecipe,"genuine loaded native crafting recipe");
        var recipe=(CraftingRecipe)holder.value();var items=Arrays.stream(slots).map(AcademyCommonTagRuntimeTests::stack).toList();
        var input=CraftingInput.of(width,height,items);h.assertTrue(recipe.matches(input,level),"test-only tagged alternative matches original OreDictionary cell: "+name);
        var result=recipe.assemble(input,level.registryAccess());h.assertTrue(result.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy",output)))&&result.getCount()==1,"unchanged actual result and yield");
        for(int i=0;i<items.size();i++)if(!items.get(i).isEmpty()){
            var missing=new ArrayList<>(items);missing.set(i,ItemStack.EMPTY);h.assertFalse(recipe.matches(CraftingInput.of(width,height,missing),level),"each original occupied cell remains required");
            var wrong=new ArrayList<>(items);wrong.set(i,stack("minecraft:stone"));h.assertFalse(recipe.matches(CraftingInput.of(width,height,wrong),level),"untagged stone cannot substitute any original cell");
        }
    }
    @GameTest(template="runtime_empty",batch="academy_common_tags",timeoutTicks=20)
    public static void four_original_ore_keys_accept_loaded_alternatives_without_broadening_shapes(GameTestHelper h){
        recipe(h,"windgen_base_10","windgen_base",1,3,"minecraft:gold_ingot","academy:machine_frame","academy:energy_convert_component");
        recipe(h,"windgen_pillar_11","windgen_pillar",1,3,"minecraft:iron_bars","minecraft:glowstone_dust","minecraft:iron_bars");
        recipe(h,"windgen_fan_13","windgen_fan",3,3,"","minecraft:iron_ingot","","minecraft:iron_ingot","minecraft:iron_bars","minecraft:iron_ingot","","minecraft:iron_ingot","");
        recipe(h,"terminal_installer_22","terminal_installer",3,3,"academy:data_chip","minecraft:glass_pane","academy:data_chip","minecraft:iron_ingot","academy:brain_component","minecraft:iron_ingot","academy:info_component","minecraft:gold_block","academy:info_component");
        h.succeed();
    }
}
