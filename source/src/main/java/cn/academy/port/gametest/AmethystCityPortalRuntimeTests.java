package cn.academy.port.gametest;

import cn.academy.port.city.portal.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("academy_city_portal")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class AmethystCityPortalRuntimeTests {
    private static final String TEMPLATE="amethyst_portal_empty";
    @SubscribeEvent public static void template(LevelEvent.Load e){
        if(!GameTestHooks.isGametestEnabled()||!(e.getLevel() instanceof ServerLevel w))return;
        var t=new CompoundTag();var size=new ListTag();for(int v:new int[]{24,12,24})size.add(IntTag.valueOf(v));t.put("size",size);
        var pos=new ListTag();for(int n=0;n<3;n++)pos.add(IntTag.valueOf(0));var cell=new CompoundTag();cell.put("pos",pos);cell.putInt("state",0);var cells=new ListTag();cells.add(cell);
        var air=new CompoundTag();air.putString("Name","minecraft:air");var palette=new ListTag();palette.add(air);t.put("palette",palette);t.put("blocks",cells);t.put("entities",new ListTag());
        w.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_city_portal",TEMPLATE)).load(w.registryAccess().lookupOrThrow(Registries.BLOCK),t);
    }
    private static BlockPos pool(GameTestHelper h,int x,int z){
        var w=h.getLevel();var a=h.absolutePos(new BlockPos(x,2,z));
        for(int dx=-3;dx<=4;dx++)for(int dz=-3;dz<=4;dz++){
            w.setBlock(a.offset(dx,-1,dz),Blocks.DIRT.defaultBlockState(),3);w.setBlock(a.offset(dx,0,dz),Blocks.GRASS_BLOCK.defaultBlockState(),3);
            for(int y=1;y<=3;y++)w.setBlock(a.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),3);
        }
        for(var o:AmethystPortalRitual.WATER)w.setBlock(a.offset(o),Blocks.WATER.defaultBlockState(),3);
        for(var o:AmethystPortalRitual.FIBERS)w.setBlock(a.offset(o),AmethystPortalContent.FIBER.get().defaultBlockState(),3);
        h.assertTrue(AmethystPortalRitual.sourcePool(w,a),"valid 2x2 sources and eight side fibers");return a;
    }
    private static ItemEntity item(GameTestHelper h,BlockPos a,Item type,int count,boolean add){
        var e=new ItemEntity(h.getLevel(),a.getX()+.5,a.getY()+.7,a.getZ()+.5,new ItemStack(type,count));e.setDeltaMovement(Vec3.ZERO);e.setPickUpDelay(80);if(add)h.getLevel().addFreshEntity(e);return e;
    }
    private static ServerPlayer player(GameTestHelper h,String name){return FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),name));}
    private static final java.util.Set<ServerLevel> CITY_FIXTURES=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static ServerLevel cityFixture(GameTestHelper h){
        var w=h.getLevel();if(CITY_FIXTURES.add(w)){
            var biome=w.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
            var generator=new cn.academy.port.city.CityGreyboxGenerator(new net.minecraft.world.level.biome.FixedBiomeSource(biome),20261103L);
            for(int x=-84;x<=-60;x++)for(int z=-84;z<=-58;z++){
                var column=generator.getBaseColumn(x,z,w,w.getChunkSource().randomState());
                for(int y=-2;y<=3;y++)w.setBlock(new BlockPos(x,y,z),column.getBlock(y),2);
            }
        }return w;
    }
    private static net.minecraft.world.level.portal.DimensionTransition arrival(GameTestHelper h,ServerPlayer p,BlockPos a){
        var city=cityFixture(h);var station=CityPortalTravel.station(city);h.assertTrue(station!=null,"arrival station fits actual city generator columns");return CityPortalTravel.prepareArrival(h.getLevel(),p,a,city,station);
    }
    @GameTest(template=TEMPLATE,batch="portal_logic",timeoutTicks=120)
    public static void dropped_dirt_really_activates_once_preserves_eight_fibers_and_water(GameTestHelper h){
        var a=pool(h,6,6);var dirt=item(h,a,Items.DIRT,3,true);
        h.runAfterDelay(15,()->{h.assertTrue(AmethystPortalRitual.active(h.getLevel(),a),"real item tick activated four portal cells");h.assertTrue(dirt.isAlive()&&dirt.getItem().getCount()==2,"only one dirt consumed");
            for(var o:AmethystPortalRitual.FIBERS)h.assertTrue(h.getLevel().getBlockState(a.offset(o)).is(AmethystPortalContent.FIBER.get()),"fiber retained");
            for(var o:AmethystPortalRitual.WATER)h.assertTrue(h.getLevel().getFluidState(a.offset(o)).isSource()&&h.getLevel().getFluidState(a.offset(o)).getType()==Fluids.WATER,"water semantics retained");});
        h.runAfterDelay(80,()->{h.assertTrue(dirt.getItem().getCount()==2&&AmethystPortalRitual.active(h.getLevel(),a),"remaining stack cannot reactivate an active pool");dirt.discard();h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="portal_logic",timeoutTicks=100)
    public static void missing_wrong_corner_or_wrong_height_fibers_never_consume_offering(GameTestHelper h){
        var a=pool(h,6,6);var w=h.getLevel();var dirt=item(h,a,Items.DIRT,2,false);var at=a.offset(AmethystPortalRitual.FIBERS.getFirst());
        for(var bad:new net.minecraft.world.level.block.state.BlockState[]{Blocks.AIR.defaultBlockState(),Blocks.ALLIUM.defaultBlockState()}){
            w.setBlock(at,bad,3);h.assertTrue(!AmethystPortalRitual.tryActivate(w,dirt)&&dirt.getItem().getCount()==2,"missing or vanilla flower rejected");
        }
        w.setBlock(a.offset(-1,1,-1),AmethystPortalContent.FIBER.get().defaultBlockState(),3);h.assertTrue(!AmethystPortalRitual.tryActivate(w,dirt),"corner cannot substitute for required side fiber");
        w.setBlock(at.above(),AmethystPortalContent.FIBER.get().defaultBlockState(),2);h.assertTrue(!AmethystPortalRitual.tryActivate(w,dirt),"wrong height rejected");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="portal_logic",timeoutTicks=100)
    public static void incomplete_flowing_or_lava_pool_and_non_dirt_items_do_not_activate(GameTestHelper h){
        var a=pool(h,6,6);var w=h.getLevel();var dirt=item(h,a,Items.DIRT,1,false);
        for(var state:new net.minecraft.world.level.block.state.BlockState[]{Blocks.AIR.defaultBlockState(),Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),Blocks.LAVA.defaultBlockState()}){
            w.setBlock(a,state,2);h.assertTrue(!AmethystPortalRitual.tryActivate(w,dirt)&&dirt.getItem().getCount()==1,"four source water cells required");
        }
        w.setBlock(a,Blocks.WATER.defaultBlockState(),2);h.assertTrue(!AmethystPortalRitual.tryActivate(w,item(h,a,Items.COARSE_DIRT,1,false)),"coarse dirt is not the specified offering");h.assertTrue(!AmethystPortalRitual.tryActivate(w,item(h,a,Items.AMETHYST_SHARD,1,false)),"amethyst itself is not the offering");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="portal_logic",timeoutTicks=50)
    public static void breaking_one_fiber_closes_all_four_cells_without_destroying_remaining_fibers(GameTestHelper h){
        var a=pool(h,6,6);var dirt=item(h,a,Items.DIRT,1,false);AmethystPortalRitual.tryActivate(h.getLevel(),dirt);
        h.getLevel().destroyBlock(a.offset(AmethystPortalRitual.FIBERS.getFirst()),true);
        h.runAfterDelay(25,()->{for(var o:AmethystPortalRitual.WATER)h.assertTrue(h.getLevel().getBlockState(a.offset(o)).is(Blocks.WATER),"pool restored to water");for(var o:AmethystPortalRitual.FIBERS.subList(1,8))h.assertTrue(h.getLevel().getBlockState(a.offset(o)).is(AmethystPortalContent.FIBER.get()),"seven other fibers retained");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="portal_logic",timeoutTicks=50)
    public static void invalid_quadrant_closes_portal_and_player_placed_block_is_retained(GameTestHelper h){
        var a=pool(h,6,6);AmethystPortalRitual.open(h.getLevel(),a);h.getLevel().setBlockAndUpdate(a,Blocks.STONE.defaultBlockState());
        h.runAfterDelay(25,()->{h.assertTrue(h.getLevel().getBlockState(a).is(Blocks.STONE),"foreign replacement not overwritten");for(var o:AmethystPortalRitual.WATER.subList(1,4))h.assertTrue(h.getLevel().getBlockState(a.offset(o)).is(Blocks.WATER),"other three portal cells restored");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch="portal_logic",timeoutTicks=100)
    public static void fiber_uses_flower_soil_rules_and_requested_description(GameTestHelper h){
        var a=pool(h,6,6);var p=a.offset(AmethystPortalRitual.FIBERS.getFirst());var state=AmethystPortalContent.FIBER.get().defaultBlockState();
        h.assertTrue(state.canSurvive(h.getLevel(),p),"grass supports fiber");h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);h.assertTrue(!state.canSurvive(h.getLevel(),p),"stone does not support a planted fiber");
        h.assertTrue(AmethystPortalContent.FIBER_ITEM.get().getBlock()==AmethystPortalContent.FIBER.get(),"survival placement item registered");
        try(var in=AmethystPortalContent.class.getResourceAsStream("/assets/academy/lang/zh_cn.json")){var json=com.google.gson.JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();h.assertTrue(json.get("item.academy.amethyst_fiber.desc").getAsString().equals("我的天啊，紫晶矿"),"exact user description");}catch(Exception e){throw new RuntimeException(e);}h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="portal_travel",timeoutTicks=200)
    public static void destination_is_fixed_city_with_safe_dry_arrival_and_personal_return(GameTestHelper h){
        var a=pool(h,6,6);var p=player(h,"PortalRoute");var t=arrival(h,p,a);
        h.assertTrue(t!=null&&t.newLevel()==cityFixture(h),"native transition preparation targets supplied real generator fixture");h.assertTrue(CityPortalTravel.standable(t.newLevel(),t.pos()),"dry unobstructed destination outside portal");
        t.postDimensionTransition().onTransition(p);var home=CityPortalTravel.home(p);h.assertTrue(home!=null&&home.newLevel()==h.getLevel(),"personal source dimension restored");h.assertTrue(home.pos().distanceToSqr(Vec3.atCenterOf(a))<25,"return lands beside original pool");
        h.assertTrue(p.getPortalCooldown()==80,"post-transition cooldown prevents bounce");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="portal_travel",timeoutTicks=200)
    public static void two_players_share_city_pool_but_keep_distinct_return_addresses(GameTestHelper h){
        var a=pool(h,5,5);var b=pool(h,16,16);var first=player(h,"PortalOne");var second=player(h,"PortalTwo");
        var t1=arrival(h,first,a);var t2=arrival(h,second,b);h.assertTrue(t1!=null&&t2!=null,"both destinations available");
        t1.postDimensionTransition().onTransition(first);t2.postDimensionTransition().onTransition(second);var r1=CityPortalTravel.home(first);var r2=CityPortalTravel.home(second);
        h.assertTrue(r1.pos().distanceToSqr(Vec3.atCenterOf(a))<25&&r2.pos().distanceToSqr(Vec3.atCenterOf(b))<25,"return addresses not shared globally");h.assertTrue(!CityPortalTravel.returnRecord(first).equals(CityPortalTravel.returnRecord(second)),"independent persistent NBT");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="portal_travel",timeoutTicks=200)
    public static void blocked_old_return_landing_uses_another_safe_rim_position(GameTestHelper h){
        var a=pool(h,6,6);var p=player(h,"PortalBlocked");var t=arrival(h,p,a);t.postDimensionTransition().onTransition(p);var old=CityPortalTravel.home(p).pos();
        h.getLevel().setBlockAndUpdate(BlockPos.containing(old),Blocks.STONE.defaultBlockState());var next=CityPortalTravel.home(p);
        h.assertTrue(next!=null&&next.pos().distanceToSqr(old)>.2&&CityPortalTravel.standable(h.getLevel(),next.pos()),"blocked position rejected; nearby clear landing selected");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="portal_travel",timeoutTicks=200)
    public static void return_record_is_copied_on_player_death_and_station_is_reused(GameTestHelper h){
        var a=pool(h,6,6);var first=player(h,"PortalBefore");var clone=player(h,"PortalAfter");var t=arrival(h,first,a);t.postDimensionTransition().onTransition(first);
        CityPortalTravel.clone(new net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone(clone,first,true));
        h.assertTrue(CityPortalTravel.returnRecord(first).equals(CityPortalTravel.returnRecord(clone)),"clone preserves only owned return data");var w=t.newLevel();var firstStation=CityPortalTravel.station(w);h.assertTrue(firstStation!=null&&firstStation.equals(CityPortalTravel.station(w)),"same public arrival pool reused");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="portal_logic",timeoutTicks=100)
    public static void portal_is_player_only_delayed_and_preserves_normal_nonportal_water(GameTestHelper h){
        var a=pool(h,6,6);var p=player(h,"PortalDelay");h.assertTrue(AmethystPortalContent.PORTAL.get().getPortalTransitionTime(h.getLevel(),p)==20,"twenty-tick standing transition");
        var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());AmethystPortalRitual.open(h.getLevel(),a);h.assertTrue(AmethystPortalContent.PORTAL.get().getPortalDestination(h.getLevel(),cow,a)==null,"NPCs and loose items cannot be stolen across dimensions");
        h.assertTrue(AmethystPortalContent.PORTAL.get().getPortalDestination(h.getLevel(),p,a.offset(4,0,0))==null,"nonportal water cannot teleport");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="portal_furnace",timeoutTicks=1700)
    public static void real_vanilla_furnace_smelts_eight_shards_into_eight_fibers_with_one_coal(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(5,2,5));h.getLevel().setBlockAndUpdate(pos,Blocks.FURNACE.defaultBlockState());var f=(FurnaceBlockEntity)h.getLevel().getBlockEntity(pos);
        f.setItem(0,new ItemStack(Items.AMETHYST_SHARD,8));f.setItem(1,new ItemStack(Items.COAL,1));
        h.runAfterDelay(1620,()->{h.assertTrue(f.getItem(2).is(AmethystPortalContent.FIBER_ITEM.get())&&f.getItem(2).getCount()==8,"eight actual furnace outputs");h.assertTrue(f.getItem(0).isEmpty()&&f.getItem(1).isEmpty(),"real ingredients and coal consumed");h.succeed();});
    }
}
