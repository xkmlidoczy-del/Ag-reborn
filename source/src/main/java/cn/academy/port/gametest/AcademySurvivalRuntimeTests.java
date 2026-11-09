package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.InductionFactors;
import cn.academy.port.survival.ClassicFactorLoot;
import cn.academy.port.survival.ClassicMaterials;
import cn.academy.port.survival.ClassicOreRules;
import cn.academy.port.survival.ClassicWorldgenConfig;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native registry, recipe, loot and generation coverage; the parent owns runtime launches. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademySurvivalRuntimeTests {
    private static final String TEMPLATE = "runtime_empty";
    private AcademySurvivalRuntimeTests() {}
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("academy", name); }
    private static ItemStack stack(String name) {
        if(name.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(name)));
    }
    private static void recipe(GameTestHelper helper,String name,String output,int count,int width,int height,String... slots) {
        var level=helper.getLevel();
        var holder=level.getRecipeManager().byKey(id("classic/"+name)).orElseThrow();
        helper.assertTrue(holder.value() instanceof CraftingRecipe,"recipe is native crafting: "+name);
        var recipe=(CraftingRecipe)holder.value();
        var input=CraftingInput.of(width,height,java.util.Arrays.stream(slots).map(AcademySurvivalRuntimeTests::stack).toList());
        helper.assertTrue(recipe.matches(input,level),"exact classic input matches: "+name);
        var result=recipe.assemble(input,level.registryAccess());
        helper.assertTrue(result.is(BuiltInRegistries.ITEM.get(id(output))),"exact recipe item: "+name);
        helper.assertValueEqual(result.getCount(),count,"classic yield: "+name);
        // Reducing any occupied source cell rejects a shaped/shapeless ingredient multiset.
        var reduced=new ArrayList<>(java.util.Arrays.stream(slots).map(AcademySurvivalRuntimeTests::stack).toList());
        for(int i=0;i<reduced.size();i++) if(!reduced.get(i).isEmpty()) {reduced.set(i,ItemStack.EMPTY);break;}
        helper.assertTrue(!recipe.matches(CraftingInput.of(width,height,reduced),level),"insufficient ingredient count rejected: "+name);
    }
    @GameTest(template=TEMPLATE,batch="academy_survival")
    public static void foundation_registration_and_mining_tiers(GameTestHelper helper) {
        helper.assertValueEqual(ClassicMaterials.materials().size(),16,"complete inert crafting material inventory");
        helper.assertValueEqual(ClassicMaterials.ores().size(),4,"four ores");
        for(var entry:ClassicMaterials.materials().entrySet()) {
            helper.assertTrue(BuiltInRegistries.ITEM.getKey(entry.getValue().get()).equals(id(entry.getKey())),"material registry identity");
            helper.assertValueEqual(entry.getValue().get().getDefaultInstance().getMaxStackSize(),64,"classic material stack size");
        }
        for(var ore:ClassicOreRules.ORES) {
            var block=ClassicMaterials.ores().get(ore.id()).get();var state=block.defaultBlockState();
            helper.assertTrue(BuiltInRegistries.BLOCK.getKey(block).equals(id(ore.id())),"ore registry identity");
            helper.assertTrue(state.getDestroySpeed(helper.getLevel(),helper.absolutePos(BlockPos.ZERO))==ore.hardness(),"classic hardness");
            helper.assertTrue(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),"wood pickaxe insufficient");
            helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state)==(ore.harvestLevel()==1),"source harvest tier");
            helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state),"iron pickaxe harvests all classic ores");
            helper.assertTrue(!new ItemStack(Items.DIAMOND_SHOVEL).isCorrectToolForDrops(state),"correct tier with wrong tool rejected");
        }
        helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(ClassicMaterials.MACHINE_FRAME.get().defaultBlockState()),"machine frame stone-pickaxe tier");
        helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_survival")
    public static void native_crafting_preserves_shapes_and_yields(GameTestHelper helper) {
        recipe(helper,"imag_silicon_piece_01","imag_silicon_piece",2,1,1,"academy:wafer");
        recipe(helper,"data_chip_02","data_chip",1,3,2,"minecraft:redstone","minecraft:redstone","minecraft:redstone","","academy:reinforced_iron_plate","");
        recipe(helper,"data_chip_03","data_chip",1,3,2,"minecraft:redstone","minecraft:redstone","minecraft:redstone","","academy:imag_silicon_piece","");
        recipe(helper,"calc_chip_04","calc_chip",1,3,1,"academy:data_chip","minecraft:quartz","minecraft:quartz");
        recipe(helper,"calc_chip_05","calc_chip",1,2,1,"academy:data_chip","academy:reso_crystal");
        recipe(helper,"reinforced_iron_plate_06","reinforced_iron_plate",2,1,3,"minecraft:iron_ingot","minecraft:iron_ingot","minecraft:iron_ingot");
        recipe(helper,"machine_frame_07","machine_frame",1,3,3,"","academy:reinforced_iron_plate","","academy:reinforced_iron_plate","minecraft:redstone","academy:reinforced_iron_plate","","academy:reinforced_iron_plate","");
        recipe(helper,"constraint_plate_21","constraint_plate",2,3,1,"academy:constraint_ingot","academy:constraint_ingot","academy:constraint_ingot");
        recipe(helper,"info_component_34","info_component",1,1,2,"minecraft:glowstone_dust","academy:data_chip");
        recipe(helper,"brain_component_35","brain_component",1,3,3,"","minecraft:gold_nugget","","minecraft:redstone","academy:calc_chip","minecraft:redstone","","minecraft:gold_nugget","");
        recipe(helper,"resonance_component_36","resonance_component",1,3,2,"academy:constraint_plate","academy:reso_crystal","academy:constraint_plate","","minecraft:redstone","");
        recipe(helper,"portable_developer_42","portable_developer",1,3,3,"academy:data_chip","minecraft:glass_pane","academy:calc_chip","academy:brain_component","academy:info_component","academy:energy_convert_component","academy:constraint_plate","academy:crystal_low","academy:constraint_plate");
        recipe(helper,"wafer_47","wafer",1,1,1,"academy:imag_silicon_ingot");
        recipe(helper,"magnetic_coil_49","magnetic_coil",1,3,3,"academy:constraint_plate","academy:reso_crystal","academy:constraint_plate","academy:constraint_plate","academy:reso_crystal","academy:constraint_plate","academy:reinforced_iron_plate","minecraft:diamond","academy:reinforced_iron_plate");
        for(var holder:helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            var result=holder.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(!result.is(AcademyCraft.COIN.get()) && !result.is(AcademyCraft.NEEDLE.get()) && !result.is(AcademyCraft.INDUCTION_FACTOR.get()),"no fabricated coin, needle or factor crafting bypass");
            helper.assertTrue(!result.is(ClassicMaterials.CRYSTAL_NORMAL.get()) && !result.is(ClassicMaterials.CRYSTAL_PURE.get()),"no Imag Fusor tier bypass");
        }
        helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_survival")
    public static void native_smelting_preserves_experience(GameTestHelper helper) {
        var level=helper.getLevel();
        String[][] rules={{"imag_silicon_ingot_31","imag_silicon_ore","imag_silicon_ingot","0.8"},{"constraint_ingot_32","constraint_metal_ore","constraint_ingot","0.7"},{"crystal_low_33","crystal_ore","crystal_low","0.8"}};
        for(var rule:rules) {
            var recipe=(AbstractCookingRecipe)level.getRecipeManager().byKey(id("classic/"+rule[0])).orElseThrow().value();
            var input=new SingleRecipeInput(stack("academy:"+rule[1]));
            helper.assertTrue(recipe.matches(input,level),"native ore smelting input");
            var output=recipe.assemble(input,level.registryAccess());
            helper.assertTrue(output.is(BuiltInRegistries.ITEM.get(id(rule[2]))),"native smelting output");
            helper.assertValueEqual(output.getCount(),1,"smelting yield");
            helper.assertTrue(recipe.getExperience()==Float.parseFloat(rule[3]),"exact classic XP");
            helper.assertValueEqual(recipe.getCookingTime(),200,"vanilla furnace cooking time");
        }
        helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_survival")
    public static void native_ore_loot_fortune_and_silk_touch(GameTestHelper helper) {
        var level=helper.getLevel();var registry=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var normal=new ItemStack(Items.IRON_PICKAXE);var fortune=normal.copy();fortune.enchant(registry.getOrThrow(Enchantments.FORTUNE),3);
        var silk=normal.copy();silk.enchant(registry.getOrThrow(Enchantments.SILK_TOUCH),1);
        for(var ore:ClassicOreRules.ORES) {
            var state=ClassicMaterials.ores().get(ore.id()).get().defaultBlockState();
            var table=level.getServer().reloadableRegistries().getLootTable(state.getBlock().getLootTable());
            for(int mode=0;mode<3;mode++) for(long seed=1;seed<=64;seed++) {
                var params=new LootParams.Builder(level).withParameter(LootContextParams.BLOCK_STATE,state)
                        .withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
                        .withParameter(LootContextParams.TOOL,mode==0?normal:mode==1?fortune:silk).create(LootContextParamSets.BLOCK);
                var drops=table.getRandomItems(params,seed);
                helper.assertValueEqual(drops.size(),1,"one nonexploded loot stack");
                var drop=drops.getFirst();
                String expected=mode==2?ore.id():ore.drop();
                helper.assertTrue(drop.is(BuiltInRegistries.ITEM.get(id(expected))),"source loot identity with Fortune/Silk Touch");
                int maximum=mode==2?1:ore.maximumDrop()*(mode==1&&!ore.drop().equals(ore.id())?4:1);
                helper.assertTrue(drop.getCount()>=1 && drop.getCount()<=maximum,"exclusive RandUtils count and inherited Fortune bounds");
                if(mode==0 && ore.minimumDrop()==ore.maximumDrop()) helper.assertValueEqual(drop.getCount(),1,"self/resonant ore base exactly one");
            }
        }
        helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_survival")
    public static void native_worldgen_decodes_and_replaces_stone_only(GameTestHelper helper) {
        var level=helper.getLevel();var registry=level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        int index=0;
        for(var ore:ClassicOreRules.ORES) {
            var feature=registry.get(id(ore.id()));helper.assertTrue(feature!=null,"configured ore feature loaded");
            var config=(OreConfiguration)feature.config();
            helper.assertValueEqual(config.size,ore.veinSize(),"classic vein size");
            helper.assertValueEqual(config.targetStates.size(),1,"single stone target");
            var target=config.targetStates.getFirst();
            helper.assertTrue(target.target.test(Blocks.STONE.defaultBlockState(),RandomSource.create(1)),"stone accepted");
            helper.assertTrue(!target.target.test(Blocks.DEEPSLATE.defaultBlockState(),RandomSource.create(1)),"no invented deepslate replacement");
            helper.assertTrue(!target.target.test(Blocks.DIRT.defaultBlockState(),RandomSource.create(1)),"dirt rejected");
            var center=helper.absolutePos(new BlockPos(3,0,3+index++*12)).atY(32);
            for(var pos:BlockPos.betweenClosed(center.offset(-5,-5,-5),center.offset(5,5,5))) level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            // This probe places a generation-stage OreFeature in fully generated native chunks.
            // Prime its required WG heightmap in the owned stone-volume footprint.
            for(int chunkX=(center.getX()-6)>>4;chunkX<=(center.getX()+6)>>4;chunkX++)
                for(int chunkZ=(center.getZ()-6)>>4;chunkZ<=(center.getZ()+6)>>4;chunkZ++)
                    net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(level.getChunk(chunkX,chunkZ),
                            java.util.Set.of(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG));
            boolean generated=false;
            for(int seed=1;seed<=64&&!generated;seed++) generated=feature.place(level,level.getChunkSource().getGenerator(),RandomSource.create(seed),center);
            helper.assertTrue(generated,"decoded native feature actually places ore into stone");
            int found=0;for(var pos:BlockPos.betweenClosed(center.offset(-5,-5,-5),center.offset(5,5,5))) if(level.getBlockState(pos).is(ClassicMaterials.ores().get(ore.id()).get())) found++;
            helper.assertTrue(found>0,"ore blocks observable after generation");
        }
        helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_survival")
    public static void native_placement_respects_config_dimension_and_height(GameTestHelper helper) {
        var level=helper.getLevel();var placed=level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(id("crystal_ore"));
        var context=new PlacementContext(level,level.getChunkSource().getGenerator(),java.util.Optional.of(placed));
        var ops=level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var gate=PlacementModifier.CODEC.parse(ops,JsonParserHelper.parse("{\"type\":\"academy:classic_overworld\"}")).getOrThrow();
        boolean original=ClassicWorldgenConfig.GENERATE_ORES.get();
        try {
            ClassicWorldgenConfig.GENERATE_ORES.set(true);
            helper.assertValueEqual(gate.getPositions(context,RandomSource.create(1),BlockPos.ZERO).count(),1L,"default Overworld enabled");
            var nether=level.getServer().getLevel(Level.NETHER);helper.assertTrue(nether!=null,"Nether available for exact dimension gate");
            var netherContext=new PlacementContext(nether,nether.getChunkSource().getGenerator(),java.util.Optional.empty());
            helper.assertValueEqual(gate.getPositions(netherContext,RandomSource.create(1),BlockPos.ZERO).count(),0L,"Nether blocked even with configuration enabled");
            ClassicWorldgenConfig.GENERATE_ORES.set(false);
            helper.assertValueEqual(gate.getPositions(context,RandomSource.create(1),BlockPos.ZERO).count(),0L,"genOres false blocks new placements");
        } finally {ClassicWorldgenConfig.GENERATE_ORES.set(original);}
        for(var ore:ClassicOreRules.ORES) {
            var feature=level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(id(ore.id()));
            var localContext=new PlacementContext(level,level.getChunkSource().getGenerator(),java.util.Optional.of(feature));
            helper.assertValueEqual(feature.placement().get(1).getPositions(localContext,RandomSource.create(1),BlockPos.ZERO).count(),(long)ore.attempts(),"native per-chunk attempt count");
            for(int seed=1;seed<=128;seed++) {
                var position=feature.placement().get(3).getPositions(localContext,RandomSource.create(seed),BlockPos.ZERO).findFirst().orElseThrow();
                helper.assertTrue(position.getY()>=0 && position.getY()<60,"native uniform Y0..59");
            }
        }
        helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch="academy_survival")
    public static void factor_chests_keep_weight_count_categories_and_reload_idempotency(GameTestHelper helper) {
        var level=helper.getLevel();var provider=level.registryAccess();
        var ops=provider.createSerializationContext(JsonOps.INSTANCE);
        for(var chest:ClassicFactorLoot.CHESTS) {
            var table=level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,chest));
            var json=LootTable.DIRECT_CODEC.encodeStart(ops,table).getOrThrow().getAsJsonObject();
            var entries=json.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries");
            int factors=0;for(var raw:entries) {var entry=raw.getAsJsonObject();if(entry.has("name")&&entry.get("name").getAsString().equals("academy:induction_factor")) {factors++;helper.assertValueEqual(entry.get("weight").getAsInt(),4,"classic chest factor weight");}}
            helper.assertValueEqual(factors,4,"all four category factors injected once");
            var event=new LootTableLoadEvent(provider,chest,table);ClassicFactorLoot.load(event);
            helper.assertTrue(LootTable.DIRECT_CODEC.encodeStart(ops,event.getTable()).getOrThrow().equals(json),"reload injection is idempotent");
            var seen=new HashSet<String>();
            var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.ZERO).create(LootContextParamSets.CHEST);
            for(int seed=1;seed<=512;seed++) for(var stack:table.getRandomItems(params,seed)) if(stack.is(AcademyCraft.INDUCTION_FACTOR.get())) {
                helper.assertValueEqual(stack.getCount(),1,"factor chest count remains one");
                var category=InductionFactors.category(stack);helper.assertTrue(category.isPresent(),"factor carries valid source category component");seen.add(category.orElseThrow());
            }
            helper.assertTrue(seen.containsAll(DevelopmentActions.CATEGORIES),"seeded loot generates all four categories");
        }
        helper.succeed();
    }
    private static final class JsonParserHelper {
        private static com.google.gson.JsonElement parse(String json) {return com.google.gson.JsonParser.parseString(json);}
    }
}
