/* Owned native generated-chunk harvest fixtures, not unassisted survival play. */
package cn.academy.port.gametest;

import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Input blocks are frozen actual singleplayer Anvil voxels; no test places or replaces these ores. */
@GameTestHolder("academy_generated_harvest") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyGeneratedHarvestRuntimeTests {
 @SubscribeEvent public static void template(LevelEvent.Load e){if(!GameTestHooks.isGametestEnabled()||!Boolean.getBoolean("academy.generated.harvest.qa")||!(e.getLevel() instanceof ServerLevel w))return;if(w.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)){w.setChunkForced(6,10,true);w.setChunkForced(7,10,true);}var t=new CompoundTag();var size=new ListTag();for(int i=0;i<3;i++)size.add(IntTag.valueOf(12));t.put("size",size);var blocks=new ListTag();var cell=new CompoundTag();var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));cell.put("pos",pos);cell.putInt("state",0);blocks.add(cell);t.put("blocks",blocks);t.put("entities",new ListTag());var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);t.put("palette",palette);w.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_generated_harvest","runtime_empty")).load(w.registryAccess().lookupOrThrow(Registries.BLOCK),t);}
 @GameTest(template="runtime_empty",batch="academy_generated_harvest",timeoutTicks=40)
 public static void actual_generated_constraint_stone_tier_drop_and_real_pickup(GameTestHelper h){harvest(h,"constraint_metal_ore","constraint_metal_ore",new BlockPos(106,11,166),new BlockPos(106,12,166),Items.WOODEN_PICKAXE,Items.STONE_PICKAXE);}
 @GameTest(template="runtime_empty",batch="academy_generated_harvest",timeoutTicks=40)
 public static void actual_generated_crystal_iron_tier_drop_and_real_pickup(GameTestHelper h){harvest(h,"crystal_ore","crystal_low",new BlockPos(112,11,170),new BlockPos(112,16,172),Items.STONE_PICKAXE,Items.IRON_PICKAXE);}
 @GameTest(template="runtime_empty",batch="academy_generated_harvest",timeoutTicks=40)
 public static void actual_generated_silicon_iron_tier_drop_and_real_pickup(GameTestHelper h){harvest(h,"imag_silicon_ore","imag_silicon_ore",new BlockPos(109,27,167),new BlockPos(111,56,168),Items.STONE_PICKAXE,Items.IRON_PICKAXE);}
 @GameTest(template="runtime_empty",batch="academy_generated_harvest",timeoutTicks=40)
 public static void actual_generated_resonant_iron_tier_drop_and_real_pickup(GameTestHelper h){harvest(h,"reso_crystal_ore","reso_crystal",new BlockPos(106,31,167),new BlockPos(103,12,170),Items.STONE_PICKAXE,Items.IRON_PICKAXE);}
 private static List<ItemEntity> drops(ServerLevel w,BlockPos p,Item expected){return w.getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(2),item->item.getItem().is(expected));}
 private static void harvest(GameTestHelper h,String blockName,String dropName,BlockPos wrongPos,BlockPos rightPos,Item wrongTool,Item rightTool){
  h.assertTrue(Boolean.getBoolean("academy.generated.harvest.qa"),"explicit owned generated-world fixture run required");var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h);h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){actors.close();}public void testFailed(GameTestInfo i,GameTestRunner r){actors.close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){actors.close();}});var p=actors.player();var world=h.getLevel();var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("academy",blockName));var expected=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy",dropName));
  h.assertTrue(world.getBlockState(wrongPos).is(block)&&world.getBlockState(rightPos).is(block),"both recorded input voxels came from frozen genuine singleplayer generated chunks, never test placement");h.assertFalse(p.gameMode.isCreative()||p.getAbilities().instabuild,"actual admitted survival player; supplied vanilla tools/pose/invulnerability explicitly fixtures");
  h.runAfterDelay(8,()->{p.getInventory().clearContent();p.moveTo(wrongPos.getX()+.5,wrongPos.getY(),wrongPos.getZ()+.5,0,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(wrongTool));h.assertFalse(p.hasCorrectToolForDrops(world.getBlockState(wrongPos)),"source lower harvest tier truly fails native tool predicate");h.assertTrue(drops(world,wrongPos,expected).isEmpty(),"no preexisting expected item at wrong voxel");h.assertTrue(p.gameMode.destroyBlock(wrongPos)&&world.getBlockState(wrongPos).isAir(),"native survival destroy action removes actual wrong-tier voxel");h.assertTrue(drops(world,wrongPos,expected).isEmpty(),"wrong tool cannot manufacture source ore/crystal drop");
  p.moveTo(rightPos.getX()+.5,rightPos.getY(),rightPos.getZ()+.5,0,0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(rightTool));h.assertTrue(p.hasCorrectToolForDrops(world.getBlockState(rightPos)),"source valid harvest tier satisfies native predicate");h.assertTrue(drops(world,rightPos,expected).isEmpty(),"no preexisting matching item at right voxel");h.assertTrue(p.gameMode.destroyBlock(rightPos)&&world.getBlockState(rightPos).isAir(),"instant native survival destruction of genuine correct-tier ore; not a timed client-mining claim");var actual=drops(world,rightPos,expected);int total=actual.stream().mapToInt(item->item.getItem().getCount()).sum();h.assertTrue(total>0,"actual production loot table produces expected source primitive");h.assertTrue(p.getMainHandItem().getDamageValue()==1,"real survival tool durability consumed");
  h.runAfterDelay(12,()->{for(var item:drops(world,rightPos,expected))item.playerTouch(p);int inventory=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(expected))inventory+=p.getInventory().getItem(i).getCount();h.assertValueEqual(inventory,total,"real native item pickup/inventory conserves the generated ore loot");h.assertTrue(drops(world,rightPos,expected).isEmpty(),"picked-up loot does not remain duplicated in world");System.out.println("ACADEMY_GENERATED_HARVEST "+blockName+" actual generated voxel "+rightPos+" -> "+total+" "+dropName+"; supplied tool/pose, native survival instant break and real pickup verified");h.succeed();});});
 }
}
