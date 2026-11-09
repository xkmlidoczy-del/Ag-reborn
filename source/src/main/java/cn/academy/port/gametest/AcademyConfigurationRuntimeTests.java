/* Opt-in isolated configuration/progression fixture. Excluded from distributed jar. */
package cn.academy.port.gametest;
import cn.academy.port.*;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.develop.*;
import cn.academy.port.preset.PresetSkills;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("academy_configuration") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyConfigurationRuntimeTests {
 @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
 public static final class Lifecycle {static ModConfig loaded;@SubscribeEvent public static void loading(ModConfigEvent.Loading e){if(e.getConfig().getSpec()==AcademyConfig.SPEC)loaded=e.getConfig();}}
 @SubscribeEvent public static void template(LevelEvent.Load e){if(!GameTestHooks.isGametestEnabled()||!Boolean.getBoolean("academy.configuration.qa")||!(e.getLevel() instanceof ServerLevel w))return;var t=new CompoundTag();var size=new ListTag();for(int i=0;i<3;i++)size.add(IntTag.valueOf(12));t.put("size",size);var blocks=new ListTag();var cell=new CompoundTag();var pos=new ListTag();for(int i=0;i<3;i++)pos.add(IntTag.valueOf(0));cell.put("pos",pos);cell.putInt("state",0);blocks.add(cell);t.put("blocks",blocks);t.put("entities",new ListTag());var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);t.put("palette",palette);w.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_configuration","runtime_empty")).load(w.registryAccess().lookupOrThrow(Registries.BLOCK),t);}
 @GameTest(template="runtime_empty",batch="academy_configuration",timeoutTicks=30)
 public static void loaded_startup_overrides_real_portable_learning_live_reload_and_preserved_old_mapping(GameTestHelper h){
  h.assertTrue(Boolean.getBoolean("academy.configuration.qa")&&Lifecycle.loaded!=null,"actual NeoForge SERVER Loading event captured");var arc=ClassicSkillConfiguration.key("electromaster","arc_gen");var charging=ClassicSkillConfiguration.key("electromaster","charging");var flags=AcademyConfig.SKILL_FLAGS.get(arc);var cf=AcademyConfig.SKILL_FLAGS.get(charging);
  h.assertFalse(flags.enabled().get(),"isolated world's authored initial TOML override loaded");h.assertFalse(SkillCatalog.enabled("electromaster","arc_gen"),"production Loading listener captured disabled startup parent");h.assertTrue(SkillCatalog.configuration().parent("electromaster","arc_gen")==null,"initial disabled dependency omitted");var snapshot=SkillCatalog.configurationSnapshot();
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){var p=actors.player();var s=AbilityStorage.get(p);var portable=new ItemStack(AcademyCraft.DEVELOPER.get());p.setItemInHand(InteractionHand.MAIN_HAND,portable);p.getInventory().setItem(7,InductionFactors.stack("electromaster"));new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy(10000); // Declared finite test battery/materials; no category/target mastery grants.
   h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("develop_level","")),"real authenticated initial acquisition");for(int tick=0;tick<130;tick++)DevelopmentController.tick(p,v->{});h.assertTrue(s.level==1&&s.category.equals("electromaster")&&p.getInventory().getItem(7).isEmpty(),"actual stimulation completion consumes factor and acquires chosen category");
   double before=new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy();h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("learn","charging")),"enabled child accepts real portable ingress without disabled Arc parent");for(int tick=0;tick<78;tick++)DevelopmentController.tick(p,v->{});h.assertTrue(s.learned("charging")&&!s.learned("arc_gen"),"child earned through completed learning, parent never granted");h.assertTrue(Math.abs(before-new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy()-2340)<1E-4,"finite source three26tick stimulation cost actually withdrawn");h.assertValueEqual(SkillCatalog.levelSkillCount(s),1,"disabled active omitted from live denominator");
   // Preexisting learned/bound identity is an explicit persisted-state fixture, independent of the earned child above.
   s.learn("arc_gen");s.activated=true;h.assertFalse(PresetSkills.selectable(s,"arc_gen"),"disabled active absent from new selector");h.assertTrue(s.presets.edit(0,0,"arc_gen",id->PresetSkills.mappedUsable(s,id)),"source old mapped fixture retained");h.assertTrue(AbilityStorage.decode(AbilityStorage.encode(s)).presets.currentSkill(0).equals("arc_gen"),"actual NBT codec preserves disabled old mapping across reload");h.assertTrue(AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press","0")),"actual bounded ingress preserves old mapped source activation");
   flags.enabled().set(true);cf.enabled().set(false);reload();h.assertTrue(SkillCatalog.enabled(s.category,"arc_gen")&&!SkillCatalog.enabled(s.category,"charging"),"real ModConfig Reloading event updates production getters");h.assertTrue(SkillCatalog.configuration().parent(s.category,"arc_gen")==null,"reload cannot recreate omitted startup parent");h.assertTrue(s.learned("charging")&&s.learned("arc_gen")&&s.presets.currentSkill(0).equals("arc_gen"),"reload preserves earned child and old preset identity");
   var wire=SkillCatalog.configurationSnapshot();SkillCatalog.resetConfiguration();h.assertTrue(SkillCatalog.applyConfigurationSnapshot(wire)&&SkillCatalog.configuration().parent(s.category,"arc_gen")==null,"authoritative snapshot reconstructs initial skipped graph despite live reenable");h.assertFalse(SkillCatalog.applyConfigurationSnapshot(new CompoundTag()),"partial snapshot rejected without corrupting server view");h.succeed();
  }finally{flags.enabled().set(false);cf.enabled().set(true);reload();SkillCatalog.applyConfigurationSnapshot(snapshot);}
 }
 private static void reload(){net.neoforged.fml.ModList.get().getModContainerById("academy").orElseThrow().acceptEvent(new ModConfigEvent.Reloading(Lifecycle.loaded));}
 @GameTest(template="runtime_empty",batch="academy_configuration_terrain",timeoutTicks=20)
 public static void source_local_terrain_global_world_override_living_attack_and_event_order(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){var p=actors.player();h.assertFalse(AcademyConfig.DESTROY_BLOCKS.get(),"authored global denial loaded");h.assertTrue(AcademyConfig.canDestroyBlocks(h.getLevel()),"legacy dimension0 whitelist allows actual Overworld");h.assertFalse(AcademyConfig.contextTerrain(h.getLevel(),"electromaster","arc_gen"),"world override never bypasses local false");h.assertTrue(AcademyConfig.contextTerrain(h.getLevel(),"electromaster","charging"),"same global decision preserves independent sibling local true");
   var at=h.absolutePos(new BlockPos(4,2,4));var cow=EntityType.COW.create(h.getLevel());cow.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);h.getLevel().addFreshEntity(cow);var frame=new ItemFrame(h.getLevel(),at,Direction.SOUTH);var hook=new AttackHook(p);NeoForge.EVENT_BUS.register(hook);try{float health=cow.getHealth();h.assertTrue(AbilityDamage.attack(p,"electromaster.arc_gen",cow,4)&&cow.getHealth()<health,"local terrain denial does not suppress actual living damage");h.assertFalse(AbilityDamage.attack(p,"electromaster.arc_gen",frame,4),"same skill protects actual HangingEntity");h.assertValueEqual(hook.calls,2,"both actual calculation events precede hanging permission rejection");}finally{NeoForge.EVENT_BUS.unregister(hook);cow.discard();frame.discard();}h.succeed();
  }
 }
 public static final class AttackHook {final ServerPlayer p;int calls;AttackHook(ServerPlayer p){this.p=p;}@SubscribeEvent public void attack(SkillAttackEvent e){if(e.player==p)calls++;}}
 @GameTest(template="runtime_empty",batch="academy_configuration_affection",timeoutTicks=20)
 public static void configured_vector_difficulty_order_class_exclusions_unknown_names_and_startup_capture(GameTestHelper h){
  try(var actors=new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)){var p=actors.player();var w=h.getLevel();var arrow=EntityType.ARROW.create(w);var spectral=EntityType.SPECTRAL_ARROW.create(w);var potion=EntityType.POTION.create(w);var snowball=EntityType.SNOWBALL.create(w);var pig=EntityType.PIG.create(w);var dropped=new net.minecraft.world.entity.item.ItemEntity(w,0,0,0,new ItemStack(net.minecraft.world.item.Items.DIRT));
   h.assertTrue(Math.abs(cn.academy.port.skill.VecDeviation.difficulty(arrow)-3.25F)<1E-5&&Math.abs(cn.academy.port.skill.VecDeviation.difficulty(spectral)-3.25F)<1E-5,"authored first legacy Arrow class difficulty affects native arrow subclasses");h.assertTrue(cn.academy.port.skill.VecDeviation.excluded(snowball)&&cn.academy.port.skill.VecDeviation.excluded(pig)&&cn.academy.port.skill.VecDeviation.excluded(dropped)&&!cn.academy.port.skill.VecDeviation.excluded(p),"configured modern Snowball plus source class exclusions retain caster inclusion");h.assertTrue(Math.abs(cn.academy.port.skill.VecDeviation.difficulty(potion)-1.4F)<1E-5,"independent potion default preserved");var old=List.copyOf(AcademyConfig.ENTITY_DIFFICULTIES.get());try{AcademyConfig.ENTITY_DIFFICULTIES.set(List.of("Arrow=9"));reload();h.assertTrue(Math.abs(cn.academy.port.skill.VecDeviation.difficulty(arrow)-3.25F)<1E-5,"live Skill reload does not rebuild original startup EntityAffection val");}finally{AcademyConfig.ENTITY_DIFFICULTIES.set(old);reload();}
   var modern=new cn.academy.port.skill.ClassicEntityAffectionConfiguration(List.of("minecraft:arrow=2","UnknownOriginalName=100"),List.of("minecraft:pig"));h.assertTrue(modern.difficulty(arrow)==2&&modern.difficulty(potion)==1&&modern.excluded(pig)&&!modern.excluded(p),"official modern vanilla class identity resolves; unknown names ignored, unmatched default1");for(var e:List.of(arrow,spectral,potion,snowball,pig,dropped))e.discard();h.succeed();
  }
 }
}
