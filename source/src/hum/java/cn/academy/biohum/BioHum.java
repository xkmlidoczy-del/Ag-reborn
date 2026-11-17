package cn.academy.biohum;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import java.util.*;

@Mod(BioHum.ID)
public final class BioHum {
 public static final String ID="acabiohum";
 public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
 public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
 private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ID);
 public static final Map<String,DeferredHolder<EntityType<?>,EntityType<HumNpc>>> NPCS=new LinkedHashMap<>();
 public static final List<DeferredItem<Item>> EGGS=new ArrayList<>();
 static {
  for(var p:Profiles.ALL.values()){
   var type=ENTITIES.register(p.id(),()->EntityType.Builder.<HumNpc>of((t,l)->new HumNpc(t,l,p.id()),MobCategory.CREATURE).sized(.6f*p.scale(),1.8f*p.scale()).eyeHeight(1.62f*p.scale()).clientTrackingRange(10).updateInterval(2).build(ID+":"+p.id()));
   NPCS.put(p.id(),type);
   EGGS.add(ITEMS.register(p.id()+"_spawn_egg",()->new SpawnEggItem(type.get(),p.eggBase(),p.eggSpot(),new Item.Properties())));
  }
 }
 private static final DeferredHolder<CreativeModeTab,CreativeModeTab> NPC_TAB=TABS.register("characters",()->
  CreativeModeTab.builder()
   .title(Component.translatable("itemGroup.acabiohum.characters"))
   .icon(()->new ItemStack(EGGS.getFirst().get()))
   .displayItems((parameters,output)->EGGS.forEach(egg->output.accept(egg.get())))
   .build());
 public static final DeferredHolder<EntityType<?>,EntityType<AbilityBolt>> BOLT=ENTITIES.register("ability_bolt",()->EntityType.Builder.<AbilityBolt>of(AbilityBolt::new,MobCategory.MISC).sized(.2f,.2f).clientTrackingRange(8).updateInterval(1).build(ID+":ability_bolt"));
 public static final DeferredHolder<EntityType<?>,EntityType<RuneAnchor>> RUNE=ENTITIES.register("rune_anchor",()->EntityType.Builder.of(RuneAnchor::new,MobCategory.MISC).sized(.45f,.5f).clientTrackingRange(8).build(ID+":rune_anchor"));
 public static final DeferredHolder<EntityType<?>,EntityType<FlameFamiliar>> FLAME=ENTITIES.register("flame_familiar",()->EntityType.Builder.of(FlameFamiliar::new,MobCategory.MONSTER).sized(.9f,2.7f).clientTrackingRange(8).build(ID+":flame_familiar"));
 public static final DeferredHolder<EntityType<?>,EntityType<RoseTrap>> TRAP=ENTITIES.register("rose_trap",()->EntityType.Builder.<RoseTrap>of(RoseTrap::new,MobCategory.MISC).sized(2.5f,.2f).clientTrackingRange(8).build(ID+":rose_trap"));
 public static final DeferredHolder<EntityType<?>,EntityType<ToyCharge>> TOY_CHARGE=ENTITIES.register("toy_charge",()->EntityType.Builder.of(ToyCharge::new,MobCategory.MISC).sized(.42f,.55f).clientTrackingRange(8).build(ID+":toy_charge"));
 public static final DeferredHolder<EntityType<?>,EntityType<Construct>> CONSTRUCT=ENTITIES.register("construct",()->EntityType.Builder.of(Construct::new,MobCategory.MISC).sized(1.2f,2.7f).clientTrackingRange(10).build(ID+":construct"));
 public static final DeferredItem<Item> RUNE_CARD=ITEMS.registerSimpleItem("rune_card");
 public static final DeferredItem<Item> CRYSTAL=ITEMS.registerSimpleItem("body_crystal");
 public static final DeferredItem<Item> MENTAL_REMOTE=ITEMS.registerSimpleItem("mental_remote");
 public static final DeferredItem<Item> HAND_TERMINAL=ITEMS.registerSimpleItem("hand_terminal");
 public static final DeferredItem<Item> PISTOL=ITEMS.registerSimpleItem("improvised_pistol");
 public static final DeferredItem<Item> FLAME_SWORD=ITEMS.registerSimpleItem("flame_sword");
 public static final DeferredItem<Item> CARTRIDGE=ITEMS.registerSimpleItem("cartridge");
 public static final DeferredItem<Item> RELIC=ITEMS.registerSimpleItem("ritual_relic");
 public static final DeferredItem<Item> GUNGNIR=ITEMS.register("gungnir",()->new Item(new Item.Properties().stacksTo(1)));
 public static final DeferredItem<Item> BLASTING_ROD=ITEMS.register("blasting_rod",()->new Item(new Item.Properties().stacksTo(1)));
 public BioHum(IEventBus bus,net.neoforged.fml.ModContainer container){
  PresentationConfig.register(container);
  ENTITIES.register(bus);ITEMS.register(bus);TABS.register(bus);bus.addListener(Payloads::register);
  bus.addListener((EntityAttributeCreationEvent e)->{for(var v:NPCS.values())e.put(v.get(),HumNpc.attributes().build());e.put(RUNE.get(),RuneAnchor.attributes().build());e.put(FLAME.get(),FlameFamiliar.attributes().build());e.put(CONSTRUCT.get(),Construct.attributes().build());});
  bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){e.accept(RUNE_CARD);e.accept(CRYSTAL);e.accept(PISTOL);e.accept(FLAME_SWORD);e.accept(CARTRIDGE);e.accept(RELIC);}});
  NeoForge.EVENT_BUS.addListener(BioCommands::register);NeoForge.EVENT_BUS.addListener(Abilities::incoming);
  NeoForge.EVENT_BUS.addListener(Abilities::damaged);
  NeoForge.EVENT_BUS.addListener(CombatSkills::suppressRetarget);
  NeoForge.EVENT_BUS.addListener(CombatSkills::suppressUse);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,Abilities::arrowImpact);
  NeoForge.EVENT_BUS.addListener(WorldEffects::tick);
  NeoForge.EVENT_BUS.addListener(WindowlessBuilding::useConsole);
  NeoForge.EVENT_BUS.addListener(WindowlessBuilding::tickAcademyChamber);
  NeoForge.EVENT_BUS.addListener(OthinusRewrite::tick);
  NeoForge.EVENT_BUS.addListener(OthinusRewrite::noSpawns);
  NeoForge.EVENT_BUS.addListener(OthinusRewrite::freezeSleeping);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,OthinusRewrite::breakBlock);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,OthinusRewrite::placeBlock);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,OthinusRewrite::drops);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,WorldEffects::broken);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,WorldEffects::placed);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,WorldEffects::drops);
  if(Boolean.getBoolean("biohum.qa.server"))try{
   String qa=switch(System.getProperty("biohum.qa.server.suite","legacy")){
    case "skills" -> "ServerSkillQa";
    case "combat090" -> "CombatRosterQa";
    default -> "ServerQa";
   };
   Class.forName("cn.academy.biohum.qa."+qa).getMethod("register").invoke(null);
  }catch(Exception ex){throw new IllegalStateException(ex);}
 }
}
