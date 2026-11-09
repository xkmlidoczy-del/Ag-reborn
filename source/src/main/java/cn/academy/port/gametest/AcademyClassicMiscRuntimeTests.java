/* Owner-only compiled native fixtures. Declared creative device data; no natural acquisition claim. GPLv3. */
package cn.academy.port.gametest;
import cn.academy.port.cat.*;
import cn.academy.port.display.*;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.wireless.*;
import cn.academy.port.solar.ClassicSolarGenerators;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyClassicMiscRuntimeTests {
    private static final String TEMPLATE="runtime_empty",BATCH="academy_classic_misc";
    private static final BlockPos BASE=new BlockPos(3,1,4),NODE=new BlockPos(5,1,4);
    private AcademyClassicMiscRuntimeTests(){}
    private static ClassicCatEngineBlockEntity tile(GameTestHelper h){h.setBlock(BASE,ClassicCatEngines.BLOCK.get());return (ClassicCatEngineBlockEntity)h.getBlockEntity(BASE);}
    private static void tick(GameTestHelper h,ClassicCatEngineBlockEntity tile,int count){for(int i=0;i<count;i++)ClassicCatEngineBlockEntity.serverTick(h.getLevel(),tile.getBlockPos(),tile.getBlockState(),tile);}
    private static void equal(GameTestHelper h,double expected,double actual,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-9,why+": "+actual);}
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void classic_cat_native_identity_collision_capability_and_no_fuel_inventory(GameTestHelper h){
        var tile=tile(h);h.assertTrue(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("academy","cat_engine"))==ClassicCatEngines.BLOCK.get(),"genuine registered source field");h.assertTrue(tile.getBlockState().getCollisionShape(h.getLevel(),tile.getBlockPos()).bounds().equals(new AABB(0,0,0,1,1,1)),"source full-cell collision");h.assertTrue(h.getLevel().getCapability(ClassicSolarGenerators.IMAG_FLUX,tile.getBlockPos(),Direction.UP)==tile,"real native IF generator role");h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,tile.getBlockPos(),Direction.UP)==null&&h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,tile.getBlockPos(),Direction.UP)==null,"zero slots/no invented FE shell or fuel");equal(h,0,tile.getEnergy(),"initial empty source buffer");tick(h,tile,4);equal(h,2000,tile.getEnergy(),"four genuine ticks fill2000");tick(h,tile,1);equal(h,0,tile.generation(),"full buffer stops rotation-generation feed");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void classic_cat_native_source_right_click_random_node_no_auth_shift_and_unlink(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();var tile=tile(h);h.setBlock(NODE,ClassicWirelessDevices.BASIC.get());var node=(ClassicWirelessNodeBlockEntity)h.getBlockEntity(NODE);node.setPassword("private-cat-fixture");node.setNodeName("Source Cat node");p.moveTo(tile.getBlockPos().getX()+.5,tile.getBlockPos().getY(),tile.getBlockPos().getZ()-1.5,0,0);var hit=new BlockHitResult(Vec3.atCenterOf(tile.getBlockPos()),Direction.NORTH,tile.getBlockPos(),false);p.setShiftKeyDown(true);
            h.assertTrue(tile.getBlockState().useWithoutItem(h.getLevel(),p,hit).consumesAction(),"source shift click consumed");var graph=ClassicWirelessSavedData.get(h.getLevel()).graph();var own=ClassicWirelessSavedData.pos(tile.getBlockPos());h.assertTrue(graph.nodeForGenerator(own).equals(ClassicWirelessSavedData.pos(node.getBlockPos())),"real source no-password Cat links eligible node");tick(h,tile,1);node.setEnergy(0);equal(h,150,node.getBandwidth(),"source BASIC node bandwidth150 is the bottleneck below Cat200");graph.tick();equal(h,350,tile.getEnergy(),"actual BASIC node debits Cat150");equal(h,150,node.getEnergy(),"same real150 IF arrives");tile.getBlockState().useWithoutItem(h.getLevel(),p,hit);h.assertTrue(graph.nodeForGenerator(own)==null,"repeat source click unlinks");h.assertTrue(p.containerMenu==p.inventoryMenu,"no invented Cat menu");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void classic_cat_native_save_reload_old_capability_and_removal_graph(GameTestHelper h){
        var tile=tile(h);tick(h,tile,1);equal(h,123.625,tile.getProvidedEnergy(123.625),"source direct demand draw");var level=h.getLevel();var pos=tile.getBlockPos();var saved=tile.saveWithFullMetadata(level.registryAccess());level.removeBlockEntity(pos);var loaded=(ClassicCatEngineBlockEntity)BlockEntity.loadStatic(pos,ClassicCatEngines.BLOCK.get().defaultBlockState(),saved,level.registryAccess());level.setBlockEntity(loaded);equal(h,376.375,loaded.getEnergy(),"explicit modern energyNBT repair");equal(h,0,loaded.generation(),"source transient generation reset");equal(h,0,tile.getProvidedEnergy(100),"replaced native object cannot supply");h.setBlock(NODE,ClassicWirelessDevices.BASIC.get());var graph=ClassicWirelessSavedData.get(level).graph();var own=ClassicWirelessSavedData.pos(pos);h.assertTrue(graph.linkGenerator(ClassicWirelessSavedData.pos(h.absolutePos(NODE)),own,"invalid",false),"genuine restored native generator linked");level.setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);h.assertTrue(graph.nodeForGenerator(own)==null,"modern removal cleanup leaves no stale graph link");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void classic_cat_native_twenty_tick_packet_energy_generation_and_no_inventory(GameTestHelper h){
        var tile=tile(h);tick(h,tile,20);var tag=tile.getUpdatePacket().getTag();equal(h,2000,tag.getDouble("energy"),"source syncenergy after generation");equal(h,0,tag.getDouble("generation"),"source syncgenspeed after full");h.assertTrue(!tag.contains("Items"),"zero inventory sync");tile.getProvidedEnergy(1876.375);tick(h,tile,1);tag=tile.getUpdateTag(h.getLevel().registryAccess());equal(h,623.625,tag.getDouble("energy"),"fractional native buffer");equal(h,500,tag.getDouble("generation"),"actual source generation");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void classic_hidden_icon_native_component_roundtrip_fallback_and_catalog_adapter(GameTestHelper h){
        var icon=ClassicAchievementIconItem.getStack("achievements/tp_mastery");h.assertTrue(icon.is(ClassicDisplayItems.ACHIEVEMENT_ICON.get()),"genuine hidden source dummy registry");var loaded=ItemStack.parse(h.getLevel().registryAccess(),icon.save(h.getLevel().registryAccess())).orElseThrow();h.assertTrue(ClassicAchievementIconItem.texture(loaded).equals(ResourceLocation.fromNamespaceAndPath("academy","textures/achievements/tp_mastery.png")),"stable resource payload survives native item codec");h.assertTrue(ItemStack.isSameItemSameComponents(icon,ClassicAchievementIconItem.getStack("achievements/tp_mastery")),"source texture identity dedup");h.assertTrue(ClassicDisplayItems.forIcon("texture:achievements/tp_mastery.png").getItem()==ClassicDisplayItems.ACHIEVEMENT_ICON.get(),"unchanged catalog texture adapter");h.assertTrue(ClassicDisplayItems.forIcon("item:cat_engine").is(ClassicCatEngines.ITEM.get()),"catalog ordinary block icon preserved");h.assertTrue(ClassicAchievementIconItem.texture(new ItemStack(ClassicDisplayItems.ACHIEVEMENT_ICON.get())).equals(ClassicAchievementIconItem.NULL_TEXTURE),"source index0 fallback");var invalid=new net.minecraft.nbt.CompoundTag();invalid.putString("academy_achievement_texture","INVALID RESOURCE");loaded.set(DataComponents.CUSTOM_DATA,CustomData.of(invalid));h.assertTrue(ClassicAchievementIconItem.texture(loaded).equals(ClassicAchievementIconItem.NULL_TEXTURE),"finite malformed component fallback adaptation");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void classic_creative_native_tab_logo_hidden_items_and_source_variants(GameTestHelper h){
        var tab=ClassicCreativeTab.TAB.get();tab.buildContents(new CreativeModeTab.ItemDisplayParameters(net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS,true,h.getLevel().registryAccess()));h.assertTrue(tab.getIconItem().is(ClassicDisplayItems.LOGO.get()),"actual dedicated source logo tab icon");var contents=tab.getDisplayItems();h.assertTrue(contents.stream().noneMatch(s->s.is(ClassicDisplayItems.LOGO.get())||s.is(ClassicDisplayItems.ACHIEVEMENT_ICON.get())),"source plain logo and dummy hidden from creative contents");h.assertTrue(contents.stream().anyMatch(s->s.is(ClassicCatEngines.ITEM.get())),"creative-only Cat acquisition");
        var grouped=new HashMap<String,List<ItemStack>>();for(var stack:contents){var path=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();grouped.computeIfAbsent(path,k->new ArrayList<>()).add(stack);h.assertValueEqual(stack.getCount(),1,"source creative singleton");}
        for(var id:List.of("portable_developer","energy_unit")){var variants=grouped.get(id);h.assertValueEqual(variants.size(),2,"source depleted/full pair");equal(h,0,ClassicEnergyItemHelper.getEnergy(variants.get(0)),"source first depleted");equal(h,10000,ClassicEnergyItemHelper.getEnergy(variants.get(1)),"source second full");}h.assertValueEqual(grouped.get("induction_factor").size(),4,"source per-category factors");for(var id:List.of("matrix_core_0","matrix_core_1","matrix_core_2","matter_unit","matter_unit_phase_liquid"))h.assertValueEqual(grouped.get(id).size(),1,"source material/meta variant "+id);h.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void classic_creative_only_cat_native_drop_and_no_invented_acquisition(GameTestHelper h){
        var tile=tile(h);var pos=tile.getBlockPos();h.getLevel().destroyBlock(pos,true);int count=0;for(var entity:h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)))if(entity.getItem().is(ClassicCatEngines.ITEM.get()))count+=entity.getItem().getCount();h.assertValueEqual(count,1,"source ordinary block recovery drop");for(var recipe:h.getLevel().getRecipeManager().getRecipes()){var result=recipe.value().getResultItem(h.getLevel().registryAccess());h.assertTrue(!result.is(ClassicCatEngines.ITEM.get())&&!result.is(ClassicDisplayItems.LOGO.get())&&!result.is(ClassicDisplayItems.ACHIEVEMENT_ICON.get()),"source no survival recipe "+recipe.id());}h.succeed();
    }
}
