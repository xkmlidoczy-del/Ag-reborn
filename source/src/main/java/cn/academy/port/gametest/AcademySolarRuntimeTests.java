package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.solar.*;
import cn.academy.port.survival.ClassicMaterials;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Parent owns runtime. Seeded ore/vanilla inputs/daylight are declared; no battery/mastery/category grant. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademySolarRuntimeTests {
    private static final String TEMPLATE="runtime_empty", BATCH="academy_solar";
    private static final BlockPos BASE=new BlockPos(3,1,4);
    private AcademySolarRuntimeTests() {}
    private static ResourceLocation id(String value){return ResourceLocation.parse(value.contains(":")?value:"academy:"+value);}
    private static void equal(GameTestHelper helper,double expected,double actual,String message){helper.assertTrue(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-7,message+": "+actual);}
    private static FakePlayer player(GameTestHelper helper){var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-Solar-Test]"));var point=helper.absoluteVec(new Vec3(3.5,1,2.5));player.moveTo(point.x,point.y,point.z,0,0);player.getAbilities().instabuild=false;return player;}
    private static ClassicSolarBlockEntity solar(GameTestHelper helper){helper.setBlock(BASE,ClassicSolarGenerators.BLOCK.get());return (ClassicSolarBlockEntity)helper.getBlockEntity(BASE);}
    private static void close(FakePlayer player){DevelopmentController.remove(player);AbilityStorage.remove(player);}

    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void solar_native_identity_half_height_cardinal_place_and_menu(GameTestHelper helper){
        var player=player(helper);try{
            var level=helper.getLevel();var absolute=helper.absolutePos(BASE);
            for(int quadrant=0;quadrant<4;quadrant++){
                player.setYRot(quadrant*90);var stack=new ItemStack(ClassicSolarGenerators.ITEM.get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var result=ClassicSolarGenerators.ITEM.get().place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false)));
                helper.assertTrue(result.consumesAction(),"real source single-cell placement");helper.assertValueEqual(stack.getCount(),1,"one source item consumed");
                var solar=(ClassicSolarBlockEntity)level.getBlockEntity(absolute);helper.assertTrue(solar.getBlockState().getValue(ClassicSolarBlock.FACING).name().equals(cn.academy.port.machine.MachineDeveloperRules.Facing.values()[quadrant].name()),"source cardinal yaw");
                equal(helper,.5,solar.getBlockState().getCollisionShape(level,absolute).bounds().maxY,"source half-height collision");
                helper.assertTrue(!new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(solar.getBlockState()),"source harvest stone tier");helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(solar.getBlockState()),"source stone pick sufficient");
                var menu=(ClassicSolarMenu)solar.createMenu(11,player.getInventory(),player);helper.assertValueEqual(menu.slots.size(),37,"one source charge slot plus36inventory");
                helper.assertTrue(!menu.slots.get(0).mayPlace(new ItemStack(Items.DIAMOND)),"recharge slot rejects unsupported");helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(AcademyCraft.DEVELOPER.get())),"empty native portable supported");helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get())),"native unit supported");
                player.getInventory().setItem(0,new ItemStack(AcademyCraft.DEVELOPER.get()));helper.assertTrue(!menu.quickMoveStack(player,1).isEmpty(),"native hotbar shift transfer to source recharge slot");helper.assertTrue(solar.getItem(0).is(AcademyCraft.DEVELOPER.get()),"actual tile inventory changed");helper.assertTrue(!menu.quickMoveStack(player,0).isEmpty(),"native shift battery back to inventory");helper.assertTrue(solar.isEmpty(),"battery slot empties");
                player.moveTo(absolute.getX()+20,absolute.getY(),absolute.getZ(),0,0);helper.assertTrue(!menu.stillValid(player),"source64distance squared validity");player.moveTo(absolute.getX()+.5,absolute.getY(),absolute.getZ()-1.5,0,0);
                level.removeBlock(absolute,false);
            }
            helper.succeed();
        }finally{close(player);}
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void solar_finite_native_slot_source_item_bandwidth_and_unloaded_interface(GameTestHelper helper){
        var solar=solar(helper);var level=helper.getLevel();var pos=solar.getBlockPos();
        equal(helper,0,solar.getEnergy(),"new solar buffer empty");
        var unit=new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get());solar.setItem(0,unit);solar.buffer().load(1000);
        ClassicSolarBlockEntity.serverTick(level,pos,solar.getBlockState(),solar);equal(helper,20,ClassicEnergyItemHelper.getEnergy(unit),"real native unit20IF bandwidth");equal(helper,980,solar.getEnergy(),"finite buffer subtracts actual accepted IF");
        var portable=new ItemStack(AcademyCraft.DEVELOPER.get());solar.setItem(0,portable);solar.buffer().load(1000);ClassicSolarBlockEntity.serverTick(level,pos,solar.getBlockState(),solar);equal(helper,50,ClassicEnergyItemHelper.getEnergy(portable),"real source portable50IF bandwidth");equal(helper,950,solar.getEnergy(),"finite source portable debit");
        ClassicEnergyItemHelper.setEnergy(portable,10000);solar.buffer().load(1000);ClassicSolarBlockEntity.serverTick(level,pos,solar.getBlockState(),solar);equal(helper,1000,solar.getEnergy(),"full native battery does not drain saturated buffer");
        var capability=level.getCapability(ClassicSolarGenerators.IMAG_FLUX,pos,Direction.UP);helper.assertTrue(capability==solar,"native generator capability is actual tile");equal(helper,100,capability.getBandwidth(),"source wireless bandwidth");equal(helper,125,capability.getProvidedEnergy(125),"source caller controls its own bandwidth");equal(helper,875,solar.getEnergy(),"native finite extraction");
        level.removeBlock(pos,false);equal(helper,0,capability.getProvidedEnergy(100),"retained removed tile cannot provide phantom energy");equal(helper,0,capability.getEnergy(),"retained removed tile cannot expose storage");helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void solar_native_nbt_energy_inventory_and_single_drop(GameTestHelper helper){
        var solar=solar(helper);var level=helper.getLevel();solar.buffer().load(123.625);var unit=new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get());ClassicEnergyItemHelper.setEnergy(unit,456.375);solar.setItem(0,unit);
        var saved=solar.saveWithFullMetadata(level.registryAccess());solar.buffer().load(0);solar.clearContent();solar.loadWithComponents(saved,level.registryAccess());equal(helper,123.625,solar.getEnergy(),"save retains fractional source buffer");equal(helper,456.375,ClassicEnergyItemHelper.getEnergy(solar.getItem(0)),"native inventory battery payload persists");
        saved.putDouble("energy",Double.NaN);solar.loadWithComponents(saved,level.registryAccess());equal(helper,0,solar.getEnergy(),"malformed saved buffer finite");saved.putDouble("energy",1e9);solar.loadWithComponents(saved,level.registryAccess());equal(helper,1000,solar.getEnergy(),"saved capacity clamps");
        var pos=solar.getBlockPos();level.destroyBlock(pos,true);var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3));int generators=0,units=0;for(var drop:drops){var item=drop.getItem();if(item.is(ClassicSolarGenerators.ITEM.get()))generators+=item.getCount();if(item.is(ClassicEnergyItems.ENERGY_UNIT.get())){units+=item.getCount();equal(helper,456.375,ClassicEnergyItemHelper.getEnergy(item),"broken generator refunds actual finite battery payload");}}
        helper.assertValueEqual(generators,1,"one actual generator block loot");helper.assertValueEqual(units,1,"one actual battery inventory drop");helper.succeed();
    }

    /** Preserve assembled item components while consuming real ingredient counts between every native recipe. */
    private static final class Stock {
        final List<ItemStack> stacks=new ArrayList<>();final GameTestHelper helper;
        Stock(GameTestHelper helper){this.helper=helper;}
        void add(ItemStack stack){if(!stack.isEmpty())stacks.add(stack.copy());}
        void seed(String item,int count){add(new ItemStack(BuiltInRegistries.ITEM.get(id(item)),count));}
        ItemStack take(String item){if(item.isEmpty())return ItemStack.EMPTY;var target=BuiltInRegistries.ITEM.get(id(item));for(var stack:stacks)if(stack.is(target)&&!stack.isEmpty())return stack.split(1);throw new AssertionError("Unobtainable survival-chain ingredient "+item);}
        void craft(String name,int width,int height,String... ingredients){var level=helper.getLevel();var holder=level.getRecipeManager().byKey(id(name)).orElseThrow();helper.assertTrue(holder.value() instanceof CraftingRecipe,"actual native crafting recipe "+name);var recipe=(CraftingRecipe)holder.value();var input=CraftingInput.of(width,height,java.util.Arrays.stream(ingredients).map(this::take).toList());helper.assertTrue(recipe.matches(input,level),"source chain grid accepted "+name);var result=recipe.assemble(input,level.registryAccess());helper.assertFalse(result.isEmpty(),"source chain native output "+name);add(result);for(var remaining:recipe.getRemainingItems(input))add(remaining);}
        void smelt(String recipeName,String ingredient){var level=helper.getLevel();var recipe=(AbstractCookingRecipe)level.getRecipeManager().byKey(id(recipeName)).orElseThrow().value();var input=new SingleRecipeInput(take(ingredient));helper.assertTrue(recipe.matches(input,level),"actual source/vanilla native smelt "+recipeName);add(recipe.assemble(input,level.registryAccess()));}
        void mine(String ore,int times,FakePlayer player){var block=BuiltInRegistries.BLOCK.get(id(ore));var state=block.defaultBlockState();var tool=new ItemStack(Items.IRON_PICKAXE);helper.assertTrue(tool.isCorrectToolForDrops(state),"iron tier source survival ore "+ore);for(int attempt=0;attempt<times;attempt++)for(var drop:Block.getDrops(state,helper.getLevel(),helper.absolutePos(BlockPos.ZERO),null,player,tool))add(drop);}
    }
    @GameTest(template=TEMPLATE,batch="academy_solar_survival_chain",timeoutTicks=20)
    public static void mined_recipe_chain_empty_portable_solar_power_earned_category_and_arc(GameTestHelper helper){
        var player=player(helper);var level=helper.getLevel();long previousTime=level.getDayTime();boolean previousRain=level.isRaining(),previousThunder=level.isThundering();
        try{
            // Declared fixture provenance: source ore loot plus ordinary vanilla mined/raw products.
            // Ore spawning, physical mining/furnace waits, GUI clicks and human playthrough are separate verification.
            var stock=new Stock(helper);stock.mine("academy:constraint_metal_ore",18,player);stock.mine("academy:imag_silicon_ore",1,player);stock.mine("academy:crystal_ore",4,player);stock.mine("academy:reso_crystal_ore",8,player);
            stock.seed("minecraft:raw_iron",21);stock.seed("minecraft:raw_gold",1);stock.seed("minecraft:sand",6);stock.seed("minecraft:redstone",33);stock.seed("minecraft:glowstone_dust",1);
            for(int i=0;i<18;i++)stock.smelt("classic/constraint_ingot_32","academy:constraint_metal_ore");stock.smelt("classic/imag_silicon_ingot_31","academy:imag_silicon_ore");
            for(int i=0;i<21;i++)stock.smelt("minecraft:iron_ingot_from_smelting_raw_iron","minecraft:raw_iron");stock.smelt("minecraft:gold_ingot_from_smelting_raw_gold","minecraft:raw_gold");for(int i=0;i<6;i++)stock.smelt("minecraft:glass","minecraft:sand");
            stock.craft("minecraft:gold_nugget",1,1,"minecraft:gold_ingot");stock.craft("minecraft:glass_pane",3,2,"minecraft:glass","minecraft:glass","minecraft:glass","minecraft:glass","minecraft:glass","minecraft:glass");
            stock.craft("classic/wafer_47",1,1,"academy:imag_silicon_ingot");
            for(int i=0;i<7;i++)stock.craft("classic/reinforced_iron_plate_06",1,3,"minecraft:iron_ingot","minecraft:iron_ingot","minecraft:iron_ingot");
            for(int i=0;i<6;i++)stock.craft("classic/constraint_plate_21",3,1,"academy:constraint_ingot","academy:constraint_ingot","academy:constraint_ingot");
            for(int i=0;i<10;i++)stock.craft("classic/data_chip_02",3,2,"minecraft:redstone","minecraft:redstone","minecraft:redstone","","academy:reinforced_iron_plate","");
            for(int i=0;i<5;i++)stock.craft("classic/calc_chip_05",2,1,"academy:data_chip","academy:reso_crystal");
            for(int i=0;i<3;i++){
                stock.craft("classic/energy_unit_18",3,3,"","academy:constraint_plate","","academy:constraint_plate","academy:crystal_low","academy:constraint_plate","","academy:data_chip","");
                stock.craft("classic/energy_convert_component_37",1,3,"academy:calc_chip","academy:energy_unit","academy:reso_crystal");
            }
            stock.craft("classic/brain_component_35",3,3,"","minecraft:gold_nugget","","minecraft:redstone","academy:calc_chip","minecraft:redstone","","minecraft:gold_nugget","");
            stock.craft("classic/info_component_34",1,2,"minecraft:glowstone_dust","academy:data_chip");
            stock.craft("classic/machine_frame_07",3,3,"","academy:reinforced_iron_plate","","academy:reinforced_iron_plate","minecraft:redstone","academy:reinforced_iron_plate","","academy:reinforced_iron_plate","");
            stock.craft("classic/portable_developer_42",3,3,"academy:data_chip","minecraft:glass_pane","academy:calc_chip","academy:brain_component","academy:info_component","academy:energy_convert_component","academy:constraint_plate","academy:crystal_low","academy:constraint_plate");
            stock.craft("classic/solar_gen_09",3,3,"minecraft:glass_pane","minecraft:glass_pane","minecraft:glass_pane","","academy:wafer","","academy:energy_convert_component","academy:machine_frame","academy:energy_convert_component");
            ItemStack portable=stock.take("academy:portable_developer"),generator=stock.take("academy:solar_gen");equal(helper,0,ClassicEnergyItemHelper.getEnergy(portable),"native crafted portable is EMPTY");
            var absolute=helper.absolutePos(BASE);player.setItemInHand(InteractionHand.MAIN_HAND,generator);var place=ClassicSolarGenerators.ITEM.get().place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,generator,new BlockHitResult(Vec3.atCenterOf(absolute),Direction.UP,absolute,false)));helper.assertTrue(place.consumesAction()&&generator.isEmpty(),"actual crafted solar placed/consumed");
            var solar=(ClassicSolarBlockEntity)level.getBlockEntity(absolute);solar.setItem(0,portable);
            // Independent bounded fixture daylight. Clear only this column through the template roof.
            for(int y=absolute.getY()+1;y<level.getMaxBuildHeight();y++)level.setBlock(new BlockPos(absolute.getX(),y,absolute.getZ()),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
            level.setDayTime(1000);level.setWeatherParameters(24000,0,false,false);helper.assertTrue(solar.status()==ClassicSolarRules.Status.STRONG,"actual server day and visible sky");
            for(int tick=0;tick<3334;tick++)ClassicSolarBlockEntity.serverTick(level,absolute,solar.getBlockState(),solar);
            equal(helper,10000,ClassicEnergyItemHelper.getEnergy(portable),"real solar ticks fill finite crafted portable");equal(helper,2,solar.getEnergy(),"real solar retains generated2IF excess");
            portable=solar.removeItem(0,1);player.setItemInHand(InteractionHand.MAIN_HAND,portable);var state=AbilityStorage.get(player);helper.assertFalse(state.hasCategory(),"fresh survival player has no granted category");
            // This seed selects the source's RANDOM category through the unmodified real startLevel path.
            player.getRandom().setSeed(4096L);helper.assertTrue(DevelopmentController.startLevel(player),"real portable initial acquisition starts");for(int tick=0;tick<130;tick++)DevelopmentController.tick(player,snapshot->{});
            helper.assertTrue(DevelopmentController.process(player).state()==DevelopmentProcess.State.DONE,"five source stimulations genuinely completed");helper.assertTrue(state.category.equals("electromaster")&&state.level==1,"source seeded random acquisition, no factor/category grant");equal(helper,6100,new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy(),"source acquisition consumes3900IF");
            helper.assertTrue(DevelopmentController.startSkill(player,"arc_gen"),"actual earned level1 root skill starts");for(int tick=0;tick<78;tick++)DevelopmentController.tick(player,snapshot->{});
            helper.assertTrue(DevelopmentController.process(player).state()==DevelopmentProcess.State.DONE&&state.learned("arc_gen"),"first Arc Generation skill genuinely earned");equal(helper,3760,new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy(),"source learning consumes2340IF, total6240IF");helper.succeed();
        }finally{close(player);level.setDayTime(previousTime);level.setWeatherParameters(0,0,previousRain,previousThunder);}
    }
}
