/* AcademyCraft1.0.7 finite native achievement fixtures. GPLv3. See NOTICE. */
package cn.academy.port.gametest;
import cn.academy.port.*;
import cn.academy.port.achievements.*;
import cn.academy.port.develop.*;
import cn.academy.port.skill.*;
import cn.academy.port.survival.ClassicMaterials;
import cn.academy.port.terminal.TerminalModule;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.*;

/** Compiled only in isolated staging. Setup awards/mastery are explicitly fixtures, not natural-earned proof. */
@GameTestHolder("academy") @PrefixGameTestTemplate(false)
public final class AcademyAchievementRuntimeTests {
    private static final String TEMPLATE="runtime_empty",BATCH="academy_achievements";
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void all56_native_registered_holders_source_parents_repeat_gate_and_snapshot(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();
            for(var e:ClassicAchievementCatalog.ALL){
                var holder=p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("academy",e.advancementPath()));
                h.assertTrue(holder!=null,"registered native advancement "+e.id());
                if(e.parent()!=null){h.assertTrue(holder.value().parent().orElseThrow().equals(ResourceLocation.fromNamespaceAndPath("academy",ClassicAchievementCatalog.get(e.parent()).advancementPath())),"source immediate parent "+e.id());
                    h.assertFalse(ClassicAchievements.trigger(p,e.id()),"parent is required before fixture award "+e.id());}
            }
            // Explicit trigger fixtures verify persistence plumbing, not acquisition of these 56 conditions.
            for(var e:ClassicAchievementCatalog.ALL){h.assertTrue(ClassicAchievements.trigger(p,e.id()),"source ordered parent fixture "+e.id());h.assertFalse(ClassicAchievements.trigger(p,e.id()),"repeat never creates second native completion");}
            h.assertValueEqual(ClassicAchievements.snapshot(p,"").getCompound("earned").getAllKeys().size(),56,"all earned source IDs synced");
            try{
                var path=java.nio.file.Files.createTempFile("academy-achievement-native-",".json");java.nio.file.Files.delete(path);
                var saved=new net.minecraft.server.PlayerAdvancements(p.server.getFixerUpper(),p.server.getPlayerList(),p.server.getAdvancements(),path,p);
                try{for(var e:ClassicAchievementCatalog.ALL){var holder=p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("academy",e.advancementPath()));saved.award(holder,e.criterion());}saved.save();
                    var reloaded=new net.minecraft.server.PlayerAdvancements(p.server.getFixerUpper(),p.server.getPlayerList(),p.server.getAdvancements(),path,p);
                    try{for(var e:ClassicAchievementCatalog.ALL)h.assertTrue(reloaded.getOrStartProgress(p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("academy",e.advancementPath()))).isDone(),"vanilla persisted reload "+e.id());}finally{reloaded.stopListening();}
                }finally{saved.stopListening();java.nio.file.Files.deleteIfExists(path);}
            }catch(java.io.IOException failure){throw new RuntimeException(failure);}h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void genuine_portable_development_session_initial_level1_and_skilllearn_boundary(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();var s=AbilityStorage.get(p);var portable=new ItemStack(AcademyCraft.DEVELOPER.get());
            p.setItemInHand(InteractionHand.MAIN_HAND,portable);p.getInventory().setItem(7,InductionFactors.stack("meltdowner"));
            new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy(10000);
            h.assertFalse(s.hasCategory(),"category never fixture granted");
            h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("develop_level","")),"authenticated real initial development request");
            for(int tick=1;tick<=130;tick++){DevelopmentController.tick(p,v->{});if(tick<130)h.assertFalse(ClassicAchievements.earned(p,"meltdowner.lv1"),"source five26tick stimulation gate");}
            h.assertTrue(s.category.equals("meltdowner")&&s.level==1&&!ClassAchievements(p,"meltdowner.lv1"),"unchanged source acquisition emits CategoryChangeEvent only; no false lvl1 achievement");
            h.assertTrue(p.getInventory().getItem(7).isEmpty(),"source full factor slot consumed");
            h.assertFalse(ClassAchievements(p,"meltdowner.lv2"),"no retrospective higher-level completion");
            new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy(10000);
            s.learn("electron_bomb");s.experience.put("electron_bomb",.5); // Explicit prerequisite fixture; target passive is never granted
            h.assertTrue(DevelopmentController.startSkill(p,"rad_intensify"),"real registered passive learning starts");
            for(int tick=1;tick<=78;tick++){DevelopmentController.tick(p,v->{});if(tick<78)h.assertFalse(ClassicAchievements.earned(p,"meltdowner.rad_intensify"),"three26tick learn gate");}
            h.assertTrue(s.learned("rad_intensify")&&ClassAchievements(p,"meltdowner.rad_intensify"),"real learn event awards source passive root");h.succeed();
        }
    }
    private static boolean ClassAchievements(net.minecraft.server.level.ServerPlayer p,String id){return ClassicAchievements.earned(p,id);}
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_arc_fish_branch_authenticated_slot_and_no_generic_cast_award(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("electromaster");s.setLevel(1);s.learn("arc_gen");s.experience.put("arc_gen",.6);s.activated=true;s.overloadFine=true;
            h.assertTrue(s.presets.edit(0,0,"arc_gen",id->cn.academy.port.preset.PresetSkills.selectable(s,id)),"actual source preset fixture binding");
            var pos=h.absolutePos(new BlockPos(2,1,2));p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);p.setYHeadRot(0);
            h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press","0")),"authenticated normal empty-air cast");
            h.assertFalse(ClassicAchievements.earned(p,"electromaster.arc_gen"),"normal successful arc does not award source cooked-fish root");
            s.cooldowns.clear();s.cooldownMaxTicks.clear();s.cp=s.maxCp();s.overloadFine=true;
            h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(2,1,5)),Blocks.WATER.defaultBlockState());p.setXRot(20);
            long seed=0;for(;seed<100000;seed++){p.serverLevel().random.setSeed(seed);if(p.serverLevel().random.nextDouble()<.1)break;}p.serverLevel().random.setSeed(seed);
            h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press","0")),"authenticated real source water trace");
            h.assertTrue(ClassicAchievements.earned(p,"electromaster.arc_gen"),"actual cooked-fish branch awards root");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(8),e->e.getItem().is(Items.COOKED_COD)).isEmpty(),"actual modern equivalent of cooked-fish spawn");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void real_terminal_resultslot_and_no_inventory_catchup(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();p.getInventory().add(new ItemStack(TerminalModule.INSTALLER.get()));
            h.assertFalse(ClassicAchievements.earned(p,"terminal"),"inventory possession is not source craft ingress");
            var table=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlockAndUpdate(table,Blocks.CRAFTING_TABLE.defaultBlockState());
            var menu=new CraftingMenu(94,p.getInventory(),ContainerLevelAccess.create(h.getLevel(),table));p.containerMenu=menu;
            Item[] cells={ClassicMaterials.DATA_CHIP.get(),Items.GLASS_PANE,ClassicMaterials.DATA_CHIP.get(),ClassicMaterials.REINFORCED_IRON_PLATE.get(),ClassicMaterials.BRAIN_COMPONENT.get(),ClassicMaterials.REINFORCED_IRON_PLATE.get(),ClassicMaterials.INFO_COMPONENT.get(),Items.REDSTONE_BLOCK,ClassicMaterials.INFO_COMPONENT.get()};
            for(int i=0;i<9;i++)menu.getSlot(i+1).set(new ItemStack(cells[i],2));
            h.assertTrue(menu.getSlot(0).getItem().is(TerminalModule.INSTALLER.get()),"real classic recipe result");
            h.assertFalse(ClassicAchievements.earned(p,"terminal"),"preview doesn't count as craft");
            var result=menu.getSlot(0).remove(1);menu.getSlot(0).onTake(p,result);
            h.assertTrue(ClassicAchievements.earned(p,"terminal"),"actual native ResultSlot event awards source root");
            for(int i=1;i<=9;i++)h.assertValueEqual(menu.getSlot(i).getItem().getCount(),1,"one native ingredient debit");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void mine_native_cast_requires_source_parent_retains_cast_criterion(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("electromaster");s.setLevel(3);s.learn(MineDetect.ID);s.activated=true;s.overloadFine=true;
            h.assertTrue(MineDetect.perform(p),"native zero-mastery cast accepted");h.assertFalse(ClassicAchievements.earned(p,"electromaster.mine_detect"),"cast cannot bypass missing original parent");
            for(String id:java.util.List.of("electromaster.arc_gen","electromaster.attack_creeper","electromaster.mag_movement","electromaster.body_intensify"))h.assertTrue(ClassicAchievements.trigger(p,id),"explicit source parent setup fixture");
            s.cooldowns.clear();s.cooldownMaxTicks.clear();s.cp=s.maxCp();s.overloadFine=true;
            h.assertTrue(MineDetect.perform(p)&&ClassicAchievements.earned(p,"electromaster.mine_detect"),"actual repeated native cast earns after original parent");
            h.assertFalse(MineDetect.perform(p),"cooldown replay is denied");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void existing_legacy_mine_completion_is_retained_without_retroactive_parent_awards(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();var holder=p.server.getAdvancements().get(ResourceLocation.parse("academy:electromaster/mine_detect"));
            h.assertTrue(p.getAdvancements().award(holder,"cast"),"explicit pre-restoration save fixture uses accepted old path and criterion");
            h.assertTrue(ClassicAchievements.earned(p,"electromaster.mine_detect"),"accepted old completion is preserved");
            h.assertFalse(ClassicAchievements.earned(p,"electromaster.body_intensify"),"preserved completion never synthesizes missing parents");
            h.assertFalse(ClassicAchievements.trigger(p,"electromaster.mine_detect"),"no duplicate completion/toast");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_threatening_entity_awards_even_rejected_damage_firstcrit_not_queued(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("teleporter");s.setLevel(1);s.learn(ThreateningTeleport.ID);s.learn("dim_folding_theorem");s.activated=true;s.overloadFine=true;
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COBBLESTONE,3));
            var target=h.spawn(EntityType.ZOMBIE,new BlockPos(2,1,5));target.setNoAi(true);target.setInvulnerable(true);
            var pos=h.absolutePos(new BlockPos(2,1,2));p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
            long seed=0;for(;seed<100000;seed++){p.serverLevel().random.setSeed(seed);if(p.serverLevel().random.nextFloat()<.1F)break;}p.serverLevel().random.setSeed(seed);
            h.assertTrue(ThreateningTeleport.perform(p),"real source entity trace executes");h.assertTrue(ClassicAchievements.earned(p,"teleporter.threatening_teleport"),"entity-hit root after rejected attack");
            h.assertFalse(ClassicAchievements.earned(p,"teleporter.critical_attack"),"first critical happened before missing root and is not retrospectively queued");
            h.assertValueEqual(target.getHealth(),20F,"protected target confirms rejected damage branch");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_clone_counter_and_snapshot_do_not_convert_old_markers_to_awards(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var old=actors.player();var replacement=actors.player();old.getPersistentData().putInt("ac_tpcount",399);old.getPersistentData().putBoolean("ac_teleporter_mastery",true);
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement,old,true));
            h.assertValueEqual(replacement.getPersistentData().getInt("ac_tpcount"),399,"counter survives actual clone event");
            h.assertFalse(ClassicAchievements.earned(replacement,"teleporter.mastery"),"saved compatibility marker cannot forge a source award");
            h.assertTrue(ClassicAchievements.snapshot(replacement,"").getCompound("earned").isEmpty(),"snapshot is authoritative advancements only");h.succeed();
        }
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void native_reflection_awards_before_per_entity_consumption_even_without_cp(GameTestHelper h){
        try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){
            var p=actors.player();var s=AbilityStorage.get(p);s.selectCategory("vecmanip");s.setLevel(5);s.learn(VecReflection.ID);s.activated=true;s.overloadFine=true; // Explicit source-allowed level fixture leaves capacity above the initial350 overload
            for(String id:java.util.List.of("vecmanip.ground_shock","vecmanip.dir_blast","vecmanip.storm_wing","vecmanip.blood_retro"))h.assertTrue(ClassicAchievements.trigger(p,id),"explicit achievement-parent setup fixture");
            s.cp=0;p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            h.assertTrue(VecReflection.start(p,1),"native zero-CP source initial-overload-only entry");
            VecReflection.tick(p);
            h.assertTrue(ClassicAchievements.earned(p,"vecmanip.vec_reflection"),"source includes eligible caster and awards before insufficient per-entity CP");
            h.assertTrue(s.exp(VecReflection.ID)==0&&!VecDeviation.marked(p),"no successful reflection/experience was invented");h.succeed();
        }
    }

}
