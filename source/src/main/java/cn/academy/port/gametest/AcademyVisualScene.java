package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.*;
import cn.academy.port.develop.*;
import cn.academy.port.machine.*;
import cn.academy.port.preset.PresetSkills;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.nio.file.Path;

/** Opt-in controlled visual fixture; never distributed or used as normal progression. */
@EventBusSubscriber(modid="academy")
public final class AcademyVisualScene {
    private static boolean enabled(){return Boolean.getBoolean("academy.visual.qa")&&Path.of("").toAbsolutePath().getFileName().toString().startsWith("run-client-visual-");}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        if(!enabled())return;
        event.getDispatcher().register(Commands.literal("academyqa").requires(source->source.hasPermission(2))
            .then(Commands.literal("body").executes(context->{learning(context.getSource().getPlayerOrException(),false);return 1;}))
            .then(Commands.literal("clap").executes(context->{learning(context.getSource().getPlayerOrException(),true);return 1;}))
            .then(Commands.literal("md").executes(context->{meltdowner(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("mdtarget").executes(context->{meltdownerTarget(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("mdcore").executes(context->{meltdownerCoreLearning(context.getSource().getPlayerOrException(),false);return 1;}))
            .then(Commands.literal("mdminelearn").executes(context->{meltdownerCoreLearning(context.getSource().getPlayerOrException(),true);return 1;}))
            .then(Commands.literal("mdbeamwall").executes(context->{meltdownerCoreTarget(context.getSource().getPlayerOrException(),false);return 1;}))
            .then(Commands.literal("mdminewall").executes(context->{meltdownerCoreTarget(context.getSource().getPlayerOrException(),true);return 1;}))
            .then(Commands.literal("mdadvancedlearn").executes(context->{meltdownerAdvancedLearning(context.getSource().getPlayerOrException(),false);return 1;}))
            .then(Commands.literal("mdupperlearn").executes(context->{meltdownerAdvancedLearning(context.getSource().getPlayerOrException(),true);return 1;}))
            .then(Commands.literal("mdsilicon").executes(context->{siliconCraftInputs(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("mdmineral").executes(context->{advancedMiningTargets(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("mdflight").executes(context->{advancedCombatTargets(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("tpfirstlearn").executes(context->{classicProgressionLearning(context.getSource().getPlayerOrException(),"teleporter",0);return 1;}))
            .then(Commands.literal("tpsecondlearn").executes(context->{classicProgressionLearning(context.getSource().getPlayerOrException(),"teleporter",1);return 1;}))
            .then(Commands.literal("tpfleshlearn").executes(context->{classicProgressionLearning(context.getSource().getPlayerOrException(),"teleporter",2);return 1;}))
            .then(Commands.literal("vecfirstlearn").executes(context->{classicProgressionLearning(context.getSource().getPlayerOrException(),"vecmanip",0);return 1;}))
            .then(Commands.literal("vecsecondlearn").executes(context->{classicProgressionLearning(context.getSource().getPlayerOrException(),"vecmanip",1);return 1;}))
            .then(Commands.literal("tploclearn").executes(context->{finalProgressionLearning(context.getSource().getPlayerOrException(),"teleporter",0);return 1;}))
            .then(Commands.literal("tpshiftlearn").executes(context->{finalProgressionLearning(context.getSource().getPlayerOrException(),"teleporter",1);return 1;}))
            .then(Commands.literal("tpflashlearn").executes(context->{finalProgressionLearning(context.getSource().getPlayerOrException(),"teleporter",2);return 1;}))
            .then(Commands.literal("veccombatlearn").executes(context->{finalProgressionLearning(context.getSource().getPlayerOrException(),"vecmanip",0);return 1;}))
            .then(Commands.literal("vecupperlearn").executes(context->{finalProgressionLearning(context.getSource().getPlayerOrException(),"vecmanip",1);return 1;}))
            .then(Commands.literal("vecplasmalearn").executes(context->{finalProgressionLearning(context.getSource().getPlayerOrException(),"vecmanip",2);return 1;}))
            .then(Commands.literal("tpview").executes(context->{progressionTarget(context.getSource().getPlayerOrException(),false);return 1;}))
            .then(Commands.literal("vecview").executes(context->{progressionTarget(context.getSource().getPlayerOrException(),true);return 1;}))
            .then(Commands.literal("formerparts").executes(context->{formerParts(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("terminalparts").executes(context->{terminalParts(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("windparts").executes(context->{windParts(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("solar").executes(context->{solar(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("scene").executes(context->{scene(context.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("normal").executes(context->{view(context.getSource().getPlayerOrException(),-2.5,-1.5,14);return 1;}))
            .then(Commands.literal("advanced").executes(context->{view(context.getSource().getPlayerOrException(),3.5,-1.5,14);return 1;}))
            .then(Commands.literal("wide").executes(context->{view(context.getSource().getPlayerOrException(),.5,-7.5,4);return 1;})));
    }
    /** Seed only declared level/prerequisites, not either target skill. Physical GUI must earn it.
     * Body reuses the previously genuinely crafted, node-powered Normal; no energy setter.
     * Thunder uses a separate finite Advanced visual fixture. Prerequisite grinding is not claimed. */
    private static void learning(ServerPlayer player,boolean clap){
        if(clap)scene(player);
        else if(!(player.serverLevel().getBlockEntity(new BlockPos(20,-59,26)) instanceof MachineDeveloperBlockEntity machine)||!machine.available()||machine.developerType()!=DeveloperType.NORMAL)throw new IllegalStateException("Body learning requires the retained genuinely crafted m10 Normal developer");
        player.setGameMode(GameType.SURVIVAL);player.onUpdateAbilities();DevelopmentController.remove(player);
        var state=AbilityStorage.get(player);state.selectCategory("electromaster");state.setLevel(clap?5:3);
        for(String id:clap?new String[]{"thunder_bolt"}:new String[]{"arc_gen","charging"}){state.learn(id);state.experience.put(id,1d);}
        state.activated=true;state.overloadFine=true;
        player.getInventory().selected=7;
        if(clap)view(player,3.5,-1.5,14);else{player.connection.teleport(20.5,-59,23.5,0,14);player.setYHeadRot(0);}
        AbilityStorage.save(player);AcademyNetwork.sync(player);
        player.displayClientMessage(Component.literal("Declared learning QA: supplied mature prerequisite skills/level; "+(clap?"finite Advanced power fixture":"retained crafted, genuinely solar/node-powered Normal")+". Target "+(clap?"ThunderClap":"BodyIntensify")+" is UNLEARNED. Earn through real developer UI; prerequisite grinding is not claimed."),false);
    }
    /** Declared recipe ingredients only; actual crafting, charging, placement and work remain physical. */
    private static void formerParts(ServerPlayer player){
        var world=player.serverLevel();
        if(player.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL
                ||!(world.getBlockEntity(new BlockPos(20,-59,26)) instanceof MachineDeveloperBlockEntity machine)
                ||!machine.available()||machine.developerType()!=DeveloperType.NORMAL
                ||!(world.getBlockEntity(new BlockPos(26,-59,23)) instanceof cn.academy.port.solar.ClassicSolarBlockEntity solar)
                ||!solar.available())throw new IllegalStateException("Retained genuinely crafted Solar/Normal Survival branch required; no power/placement fallback");
        String flag="academy:m17_former_parts_given";
        if(player.getPersistentData().getBoolean(flag))throw new IllegalStateException("Bounded raw-parts fixture was already supplied; no duplicate grants");
        var stacks=new java.util.ArrayList<ItemStack>();
        stacks.add(new ItemStack(Items.SHEARS));stacks.add(new ItemStack(Items.OAK_PLANKS,4));
        stacks.add(new ItemStack(Items.IRON_INGOT));stacks.add(new ItemStack(Items.GOLD_INGOT,2));stacks.add(new ItemStack(Items.IRON_ORE));
        for(var entry:java.util.Map.of("calc_chip",2,"machine_frame",1,"constraint_plate",6,"matter_unit",1,"crystal_low",1,"data_chip",1).entrySet()){
            var key=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy",entry.getKey());
            if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing genuine recipe ingredient "+key);
            stacks.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key),entry.getValue()));
        }
        int free=0;for(int slot=0;slot<36;slot++)if(player.getInventory().getItem(slot).isEmpty())free++;
        if(free<stacks.size())throw new IllegalStateException("Keep sufficient free slots before bounded fixture supply");
        for(var stack:stacks)if(!player.getInventory().add(stack))throw new IllegalStateException("Native inventory rejected a preflighted fixture stack");
        player.getPersistentData().putBoolean(flag,true);player.containerMenu.broadcastChanges();
        player.connection.teleport(23.5,-59,25.5,0,15);player.setYHeadRot(0);
        player.displayClientMessage(Component.literal("Declared QA: provided recipe26/18 intermediates, four planks and ordinary iron/gold/ore inputs. Physically craft the table, Former and EMPTY Energy Unit, obtain genuine Solar power and operate all modes. No machine, charged battery, skill, mastery, CP or power granted. Material acquisition is not natural proof."),false);
    }
    /** Declared ingredients only; no installed terminal, application, charge or progression state. */
    private static void terminalParts(ServerPlayer player){
        var world=player.serverLevel();var formerPos=new BlockPos(25,-59,25);
        if(player.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL
                ||!(world.getBlockEntity(formerPos) instanceof cn.academy.port.former.ClassicMetalFormerBlockEntity)
                ||!world.getBlockState(new BlockPos(21,-59,25)).is(Blocks.CRAFTING_TABLE)
                ||!(world.getBlockEntity(new BlockPos(26,-59,23)) instanceof cn.academy.port.solar.ClassicSolarBlockEntity solar)
                ||!solar.available()
                ||cn.academy.port.wireless.ClassicWirelessSavedData.get(world).graph().nodeForReceiver(cn.academy.port.wireless.ClassicWirelessSavedData.pos(formerPos))==null
                ||cn.academy.port.terminal.TerminalStorage.get(player).terminalInstalled())
            throw new IllegalStateException("Retained physically crafted Former/table and genuine Solar/node Survival branch with uninstalled terminal required");
        String flag="academy:m18_terminal_parts_given";
        if(player.getPersistentData().getBoolean(flag))throw new IllegalStateException("Bounded terminal ingredient fixture already supplied");
        var stacks=new java.util.ArrayList<ItemStack>();
        stacks.add(new ItemStack(Items.IRON_INGOT,2));stacks.add(new ItemStack(Items.GLASS_PANE));
        stacks.add(new ItemStack(Items.REDSTONE_BLOCK));stacks.add(new ItemStack(Items.COMPASS));stacks.add(new ItemStack(Items.NOTE_BLOCK,3));
        for(var entry:java.util.Map.of("data_chip",5,"info_component",5,"brain_component",1,"resonance_component",1).entrySet()){
            var key=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy",entry.getKey());
            if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing source terminal recipe ingredient "+key);
            stacks.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key),entry.getValue()));
        }
        int free=0;for(int slot=0;slot<36;slot++)if(player.getInventory().getItem(slot).isEmpty())free++;
        if(free<stacks.size())throw new IllegalStateException("Keep sufficient free slots before bounded terminal ingredient supply");
        for(var stack:stacks)if(!player.getInventory().add(stack))throw new IllegalStateException("Native inventory rejected preflighted terminal ingredients");
        player.getPersistentData().putBoolean(flag,true);player.containerMenu.broadcastChanges();
        player.connection.teleport(23.5,-59,25.5,90,25);player.setYHeadRot(90);
        player.displayClientMessage(Component.literal("Declared QA: supplied ordinary iron and source recipe22/38-40 intermediates. Physically plate iron, craft and use the installer/apps. No terminal, application, charge, energy, ability or progression state granted. Material acquisition is not natural proof."),false);
    }
    /** One declared raw-material batch; every Wind component, fan and empty battery must be crafted. */
    private static void windParts(ServerPlayer player){
        var world=player.serverLevel();
        if(player.gameMode.getGameModeForPlayer()!=GameType.SURVIVAL
                ||!world.getBlockState(new BlockPos(21,-59,25)).is(Blocks.CRAFTING_TABLE)
                ||!(world.getBlockEntity(new BlockPos(24,-59,25)) instanceof cn.academy.port.phasegen.ClassicPhaseGeneratorBlockEntity phase)
                ||Double.compare(cn.academy.port.energy.ClassicEnergyItemHelper.getEnergy(phase.getItem(2)),1115.9999999999245)!=0
                ||!cn.academy.port.terminal.TerminalStorage.get(player).terminalInstalled())
            throw new IllegalStateException("Retained physically crafted Phase/table/charged-unit/installed-terminal Survival branch required");
        String flag="academy:m20_wind_parts_given";
        if(player.getPersistentData().getBoolean(flag))throw new IllegalStateException("Bounded Wind ingredient fixture already supplied");
        var stacks=new java.util.ArrayList<ItemStack>();
        stacks.add(new ItemStack(Items.IRON_INGOT));stacks.add(new ItemStack(Items.IRON_BARS,17));stacks.add(new ItemStack(Items.REDSTONE,8));
        String[] names={"machine_frame","energy_convert_component","constraint_plate","reinforced_iron_plate","crystal_low","data_chip"};int[] counts={3,2,5,4,1,1};
        for(int index=0;index<names.length;index++){
            var key=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy",names[index]);
            if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key))throw new IllegalStateException("Missing genuine Wind recipe ingredient "+key);
            stacks.add(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key),counts[index]));
        }
        int free=0;for(int slot=0;slot<36;slot++)if(player.getInventory().getItem(slot).isEmpty())free++;
        if(free<stacks.size())throw new IllegalStateException("Keep sufficient free slots before bounded Wind ingredient supply");
        for(var stack:stacks)if(!player.getInventory().add(stack))throw new IllegalStateException("Native inventory rejected preflighted Wind ingredients");
        player.getPersistentData().putBoolean(flag,true);player.containerMenu.broadcastChanges();
        player.connection.teleport(23.5,-59,25.5,90,30);player.setYHeadRot(90);
        player.displayClientMessage(Component.literal("Declared QA: ordinary iron/bars/redstone and source Wind10-13/empty Energy Unit18 intermediates supplied once. Physically craft every component and install the fan; genuine Wind generation must charge the empty battery. No machine, fan, IF, skill or progression state granted. Material acquisition is not natural proof."),false);
    }
    /** Empty native portable only. Retained genuine Solar must generate all learning power. */
    private static void meltdowner(ServerPlayer player){
        var world=player.serverLevel();var origin=new BlockPos(26,-59,23);
        if(!(world.getBlockEntity(origin) instanceof cn.academy.port.solar.ClassicSolarBlockEntity solar)||!solar.available())throw new IllegalStateException("Retained m10 Solar required; no power fixture fallback");
        DevelopmentController.remove(player);player.setGameMode(GameType.SURVIVAL);player.onUpdateAbilities();
        var state=AbilityStorage.get(player);state.selectCategory("meltdowner");state.setLevel(2);state.learn("electron_bomb");state.experience.put("electron_bomb",1d);state.activated=true;state.overloadFine=true;
        var portable=world.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","classic/portable_developer_42")).orElseThrow().value().getResultItem(world.registryAccess()).copy();
        if(cn.academy.port.energy.ClassicEnergyItemHelper.getEnergy(portable)!=0)throw new IllegalStateException("Native portable recipe must be empty");
        player.getInventory().clearContent();player.getInventory().setItem(0,portable);player.getInventory().selected=0;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));player.containerMenu.broadcastChanges();
        world.setDayTime(6000);world.setWeatherParameters(240000,0,false,false);world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,world.getServer());
        player.connection.teleport(26.5,-59,20.5,0,25);player.setYHeadRot(0);AbilityStorage.save(player);AcademyNetwork.sync(player);
        player.displayClientMessage(Component.literal("Declared Meltdowner QA: mature Electron Bomb prerequisite/level2 supplied; EMPTY native-recipe portable supplied. Retained genuine Solar untouched. Charge physically, then earn ScatterBomb and LightShield via real portable GUI. Neither target nor power granted."),false);
    }
    private static void meltdownerTarget(ServerPlayer player){
        var world=player.serverLevel();player.connection.teleport(32.5,-60,20.5,0,0);player.setYHeadRot(0);
        var target=new Villager(EntityType.VILLAGER,world);target.moveTo(32.5,-60,24.5,180,0);target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);target.setCustomName(Component.literal("MD visual QA target"));target.setCustomNameVisible(true);world.addFreshEntity(target);
        player.displayClientMessage(Component.literal("Provided stationary native target for real earned-skill input/effect QA; no ability or energy mutation."),false);
    }
    /** Target skills must be earned through the retained genuinely crafted/powered Normal.
     * Only explicitly declared prerequisite maturity/level is supplied; no machine energy mutation. */
    private static void meltdownerCoreLearning(ServerPlayer player,boolean mining){
        if(!(player.serverLevel().getBlockEntity(new BlockPos(20,-59,26)) instanceof MachineDeveloperBlockEntity machine)
                ||!machine.available()||machine.developerType()!=DeveloperType.NORMAL)
            throw new IllegalStateException("Core Meltdowner learning requires retained genuine Normal developer");
        var state=AbilityStorage.get(player);
        if(!state.category.equals("meltdowner")||!state.learned("scatter_bomb")||!state.learned("light_shield"))
            throw new IllegalStateException("Retain the actually GUI-earned m12 Meltdowner starters first");
        if(mining){
            if(!state.learned("meltdowner")||state.learned("mine_ray_basic"))
                throw new IllegalStateException("First genuinely learn Meltdowner; MineRayBasic must remain unlearned");
            state.experience.put("meltdowner",(double).3F);
        }else{
            if(state.learned("meltdowner")||state.learned("mine_ray_basic"))
                throw new IllegalStateException("Both core targets must remain unlearned");
            state.setLevel(3);state.experience.put("scatter_bomb",(double).8F);state.experience.put("light_shield",(double).8F);
        }
        DevelopmentController.remove(player);player.setGameMode(GameType.SURVIVAL);player.onUpdateAbilities();
        state.activated=true;state.overloadFine=true;player.getInventory().selected=7;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(7));
        player.connection.teleport(20.5,-59,23.5,0,14);player.setYHeadRot(0);
        AbilityStorage.save(player);AcademyNetwork.sync(player);
        player.displayClientMessage(Component.literal("Declared core QA: supplied only "+(mining?"mature Meltdowner prerequisite":"level3 and mature GUI-earned Scatter/Shield prerequisites")+". Target "+(mining?"MineRayBasic":"Meltdowner")+" remains UNLEARNED. Earn through real retained solar/node-powered Normal GUI; no developer power setter."),false);
    }
    /** Bounded remote QA cells, away from retained machines. No ability/resource changes. */
    private static void meltdownerCoreTarget(ServerPlayer player,boolean mining){
        var world=player.serverLevel();int z=mining?95:66;
        for(int x=mining?60:58;x<=(mining?60:62);x++)for(int y=mining?-58:-59;y<=(mining?-58:-56);y++)
            for(int depth=0;depth<(mining?3:1);depth++)
                if(world.getBlockEntity(new BlockPos(x,y,z+depth))!=null)
                    throw new IllegalStateException("QA cells must not overwrite a block entity");
        for(int x=mining?60:58;x<=(mining?60:62);x++)for(int y=mining?-58:-59;y<=(mining?-58:-56);y++)
            for(int depth=0;depth<(mining?3:1);depth++)world.setBlock(new BlockPos(x,y,z+depth),Blocks.STONE.defaultBlockState(),3);
        if(!mining){
            var target=new Villager(EntityType.VILLAGER,world);target.moveTo(60.5,-59,69.5,180,0);target.setNoAi(true);target.setNoGravity(true);
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
            target.setCustomName(Component.literal("Core MD visual QA target"));target.setCustomNameVisible(true);world.addFreshEntity(target);
        }
        player.connection.teleport(60.5,-59,mining?92.5:60.5,0,0);player.setYHeadRot(0);
        player.displayClientMessage(Component.literal("Provided bounded "+(mining?"three-stone native mining":"stone wall and stationary native damage target")+" QA cells away from retained machines; no skill, CP or power mutation."),false);
    }
    private static void view(ServerPlayer player,double x,double z,float pitch){player.connection.teleport(x,-60,z,0,pitch);player.setYHeadRot(0);}
    /** Declared category/level and mature already-earned parents only; never target grants or power.
     * Separate copied QA worlds preserve the closed m14 player before changing category. */
    private static void classicProgressionLearning(ServerPlayer player,String category,int step){
        if(!(player.serverLevel().getBlockEntity(new BlockPos(20,-59,26)) instanceof MachineDeveloperBlockEntity machine)
                ||!machine.available()||machine.developerType()!=DeveloperType.NORMAL)
            throw new IllegalStateException("Retained genuinely crafted solar/node-powered Normal required; no power or placement fallback");
        var state=AbilityStorage.get(player);
        if(step==0){
            if(!state.category.equals("meltdowner"))throw new IllegalStateException("Cold-open the copied m14 earned world before starting a new QA category");
            for(String id:new String[]{"mine_ray_expert","mine_ray_luck","ray_barrage","jet_engine","electron_missile"})
                if(!state.learned(id))throw new IllegalStateException("Retain all five genuinely GUI-earned m14 targets before category setup");
            state.selectCategory(category);state.setLevel(1);
        }else if(!state.category.equals(category))throw new IllegalStateException("Keep the actually GUI-earned category and parents");
        if(category.equals("teleporter")){
            if(step==1){
                if(!state.learned("threatening_teleport")||state.learned("penetrate_teleport")||state.learned("mark_teleport"))
                    throw new IllegalStateException("First GUI-earn ThreateningTeleport; both level-two targets must remain unlearned");
                state.setLevel(2);state.experience.put("threatening_teleport",.5d);
            }else if(step==2){
                if(!state.learned("penetrate_teleport")||!state.learned("mark_teleport")||state.learned("flesh_ripping"))
                    throw new IllegalStateException("First GUI-earn both level-two parents; FleshRipping must remain unlearned");
                state.setLevel(3);state.experience.put("penetrate_teleport",.5d);state.experience.put("mark_teleport",.5d);
            }
        }else if(category.equals("vecmanip")&&step==1){
            if(!state.learned("dir_shock")||state.learned("vec_accel")||state.learned("vec_deviation"))
                throw new IllegalStateException("First GUI-earn DirectedShock; both vector starters must remain unlearned");
            state.setLevel(2); // Original starter prerequisites require learned parents, zero mastery suffices.
        }
        DevelopmentController.remove(player);player.setGameMode(GameType.SURVIVAL);player.onUpdateAbilities();
        state.activated=true;state.overloadFine=true;player.getInventory().selected=7;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(7));
        player.connection.teleport(20.5,-59,23.5,0,14);player.setYHeadRot(0);AbilityStorage.save(player);AcademyNetwork.sync(player);
        player.displayClientMessage(Component.literal("Declared "+category+" QA: category/level supplied, and only already GUI-earned parent maturity where required. New targets remain UNLEARNED. Physically earn through retained crafted solar/node-powered Normal; no battery setter, materials or target grant. Full natural acquisition is not claimed."),false);
    }
    /** Final live learning setup only declares level and maturity of already menu-earned parents.
     * No target grants, materials, developer placement or battery mutation. QA-only class is JAR-excluded. */
    private static void finalProgressionLearning(ServerPlayer player,String category,int step){
        var state=AbilityStorage.get(player);
        if(!state.category.equals(category))throw new IllegalStateException("Retain the matching genuinely GUI-earned m15 category");
        var type=step==0?DeveloperType.NORMAL:DeveloperType.ADVANCED;var origin=step==0?new BlockPos(20,-59,26):new BlockPos(3,-60,1);
        if(!(player.serverLevel().getBlockEntity(origin) instanceof MachineDeveloperBlockEntity machine)||!machine.available()||machine.developerType()!=type)
            throw new IllegalStateException("Retained "+type+" required; no placement or power fallback");
        for(String id:category.equals("teleporter")?new String[]{"threatening_teleport","penetrate_teleport","mark_teleport","flesh_ripping"}:new String[]{"dir_shock","vec_accel","vec_deviation"})
            if(!state.learned(id))throw new IllegalStateException("Existing genuine parent must be retained: "+id);
        if(category.equals("teleporter")){
            String target=step==0?"location_teleport":step==1?"shift_tp":"flashing";
            if(state.learned(target))throw new IllegalStateException("New target must remain unlearned: "+target);
            if(step==1&&!state.learned("location_teleport")||step==2&&!state.learned("shift_tp"))throw new IllegalStateException("Physically GUI-earn the preceding target before mature-parent setup");
            state.setLevel(step+3);
            if(step==0){state.experience.put("penetrate_teleport",.8d);state.experience.put("mark_teleport",.8d);}
            else state.experience.put(step==1?"location_teleport":"shift_tp",step==1?.5d:.8d);
        }else{
            for(String id:step==0?new String[]{"ground_shock","dir_blast","storm_wing"}:step==1?new String[]{"blood_retro","vec_reflection"}:new String[]{"plasma_cannon"})
                if(state.learned(id))throw new IllegalStateException("New target must remain unlearned: "+id);
            if(step>=1)for(String id:new String[]{"ground_shock","dir_blast","storm_wing"})if(!state.learned(id))throw new IllegalStateException("First physically GUI-earn the level-three chain: "+id);
            if(step==2)for(String id:new String[]{"blood_retro","vec_reflection"})if(!state.learned(id))throw new IllegalStateException("First physically GUI-earn both level-four targets: "+id);
            state.setLevel(step+3); // Original Vector parents require learning, zero mastery is sufficient.
        }
        DevelopmentController.remove(player);player.setGameMode(GameType.SURVIVAL);player.onUpdateAbilities();state.activated=true;state.overloadFine=true;
        player.getInventory().selected=7;player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(7));
        player.connection.teleport(origin.getX()+.5,origin.getY(),origin.getZ()-2.5,0,14);player.setYHeadRot(0);AbilityStorage.save(player);AcademyNetwork.sync(player);
        player.displayClientMessage(Component.literal("Declared final "+category+" learning QA: level"+(step+3)+" supplied"+(category.equals("teleporter")?"; only genuinely GUI-earned parent maturity supplied":"; original learned zero-mastery parents retained")+". New targets remain UNLEARNED. Earn through actual "+type+" GUI. "+(step==0?"Retained genuinely crafted solar/node-powered Normal":"Retained finite Advanced visual fixture; power acquisition is not natural proof")+"; no battery/target/material grant."),false);
    }
    /** Bounded native target/camera only, away from progression machines; no skill or resource mutation. */
    private static void progressionTarget(ServerPlayer player,boolean vector){
        var world=player.serverLevel();double x=vector?180.5:150.5;
        var target=new Villager(EntityType.VILLAGER,world);target.moveTo(x,-60,64.5,180,0);target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
        target.setCustomName(Component.literal(vector?"Vector progression QA target":"Teleporter progression QA target"));target.setCustomNameVisible(true);world.addFreshEntity(target);
        player.connection.teleport(x,-60,60.5,0,0);player.setYHeadRot(0);
        player.displayClientMessage(Component.literal("Provided native stationary target and camera for physical earned-skill effects. No skill, CP, item or developer power change."),false);
    }
    /** Existing finite Advanced visual machine is retained, not rebuilt or refilled.
     * New targets remain unlearned; only maturity of already GUI-earned parents is declared. */
    private static void meltdownerAdvancedLearning(ServerPlayer player,boolean upper){
        if(!(player.serverLevel().getBlockEntity(new BlockPos(3,-60,1)) instanceof MachineDeveloperBlockEntity machine)
                ||!machine.available()||machine.developerType()!=DeveloperType.ADVANCED)
            throw new IllegalStateException("Retained m11 finite Advanced visual fixture required; no new power/placement fallback");
        var state=AbilityStorage.get(player);
        if(!state.category.equals("meltdowner")||!state.learned("meltdowner")||!state.learned("mine_ray_basic"))
            throw new IllegalStateException("Retain genuinely GUI-earned m13 core targets before advanced learning");
        if(upper){
            if(!state.learned("mine_ray_expert")||!state.learned("jet_engine")||state.learned("mine_ray_luck")||state.learned("electron_missile"))
                throw new IllegalStateException("Genuinely learn Expert and Jet first; both upper targets must remain unlearned");
            state.setLevel(5);state.experience.put("mine_ray_expert",1d);state.experience.put("jet_engine",(double).3F);
        }else{
            for(String id:new String[]{"mine_ray_expert","ray_barrage","jet_engine","mine_ray_luck","electron_missile"})
                if(state.learned(id))throw new IllegalStateException("New advanced targets must remain unlearned: "+id);
            state.setLevel(4);state.experience.put("meltdowner",1d);state.experience.put("mine_ray_basic",(double).8F);
        }
        DevelopmentController.remove(player);player.setGameMode(GameType.SURVIVAL);player.onUpdateAbilities();
        state.activated=true;state.overloadFine=true;player.getInventory().selected=7;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(7));
        view(player,3.5,-1.5,14);AbilityStorage.save(player);AcademyNetwork.sync(player);
        player.displayClientMessage(Component.literal("Declared QA: only level"+(upper?"5 and mature genuinely GUI-earned Expert/Jet":"4 and mature genuinely GUI-earned core parents")+" supplied. New target skills remain UNLEARNED. Retained finite Advanced battery unchanged; earn via real machine GUI. Full natural prerequisites/power are not claimed."),false);
    }
    private static void siliconCraftInputs(ServerPlayer player){
        player.getInventory().add(new ItemStack(cn.academy.port.survival.ClassicMaterials.IMAG_SILICON_PIECE.get(),4));
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.literal("Provided four raw imaginary silicon pieces for physical two-piece Silicon Barn recipe QA; no crafted Barn or skill granted. Raw material acquisition is not natural evidence."),false);
    }
    private static void advancedMiningTargets(ServerPlayer player){
        var world=player.serverLevel();
        for(int z=95;z<=97;z++)if(world.getBlockEntity(new BlockPos(80,-59,z))!=null)
            throw new IllegalStateException("Bounded ore fixture must not overwrite block entities");
        for(int z=95;z<=97;z++)world.setBlock(new BlockPos(80,-59,z),Blocks.DIAMOND_ORE.defaultBlockState(),3);
        player.connection.teleport(80.5,-60,92.5,0,2);player.setYHeadRot(0);
        player.displayClientMessage(Component.literal("Provided three bounded native diamond ores and aim for actual earned Expert/Luck holds. No skill, CP, mastery or power changed; ore placement is declared QA."),false);
    }
    private static void advancedCombatTargets(ServerPlayer player){
        var world=player.serverLevel();player.connection.teleport(100.5,-60,60.5,0,0);player.setYHeadRot(0);
        var target=new Villager(EntityType.VILLAGER,world);target.moveTo(100.5,-60,66.5,180,0);target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
        target.setCustomName(Component.literal("Advanced MD visual QA target"));target.setCustomNameVisible(true);world.addFreshEntity(target);
        player.displayClientMessage(Component.literal("Provided stationary native target and open-air start for physical Barrage/Jet/Missile QA; no ability, CP or machine-power mutation."),false);
    }
    private static void place(ServerPlayer player,BlockPos origin,MachineDeveloperItem item){
        var hit=new BlockHitResult(new Vec3(origin.getX()+.5,origin.getY(),origin.getZ()+.5),Direction.UP,origin.below(),false);
        var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,item.getDefaultInstance(),hit);
        if(!item.place(context).consumesAction())throw new IllegalStateException("Owned visual fixture machine placement failed at "+origin);
        var entity=player.serverLevel().getBlockEntity(origin);
        if(!(entity instanceof MachineDeveloperBlockEntity machine)||!machine.available())throw new IllegalStateException("Owned visual fixture has no intact origin");
        machine.battery().load(machine.battery().getMaxEnergy());machine.setChanged();
    }

    private static void solar(ServerPlayer player){
        if(!enabled())throw new IllegalStateException("Visual fixture opt-in required");
        var world=player.serverLevel();DevelopmentController.remove(player);AbilityStorage.get(player).selectCategory("");
        player.setGameMode(GameType.SURVIVAL);player.getAbilities().flying=false;player.onUpdateAbilities();
        for(var pos:BlockPos.betweenClosed(-3,-61,-5,3,-61,5))world.setBlock(pos,Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
        for(var pos:BlockPos.betweenClosed(-1,-60,-1,1,-57,3))world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        world.setDayTime(6000);world.setWeatherParameters(24000,0,false,false);
        world.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,world.getServer());
        for(var entity:world.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(-8,-62,-8,8,-54,10)))entity.discard();
        view(player,.5,-1.5,28);
        player.getInventory().clearContent();player.getInventory().selected=0;
        var registry=world.registryAccess();
        java.util.function.Function<String,ItemStack> recipeOutput=name->world.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","classic/"+name)).orElseThrow().value().getResultItem(registry).copy();
        var portable=recipeOutput.apply("portable_developer_42");
        if(cn.academy.port.energy.ClassicEnergyItemHelper.getEnergy(portable)!=0)throw new IllegalStateException("Fixture portable must be native empty recipe result");
        player.getInventory().setItem(0,portable);player.getInventory().setItem(1,recipeOutput.apply("energy_unit_18"));
        player.getInventory().setItem(8,InductionFactors.stack("electromaster"));
        var generator=recipeOutput.apply("solar_gen_09");var origin=new BlockPos(0,-60,1);
        var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,generator,new BlockHitResult(new Vec3(.5,-60,1.5),Direction.UP,origin.below(),false));
        if(!cn.academy.port.solar.ClassicSolarGenerators.ITEM.get().place(context).consumesAction())throw new IllegalStateException("Owned solar fixture failed to place");
        AbilityStorage.save(player);AcademyNetwork.sync(player);
        player.displayClientMessage(Component.literal("Controlled single-player solar fixture: EMPTY native recipe outputs, selected factor, clear daytime. Charge and develop through real inventory/UI; resources are provided, not earned survival."),false);
    }

    private static void scene(ServerPlayer player){
        if(!enabled())throw new IllegalStateException("Visual fixture opt-in required");
        var world=player.serverLevel();player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=false;player.onUpdateAbilities();
        player.connection.teleport(.5,-60,-7.5,0,4);player.setYHeadRot(0);
        for(var pos:BlockPos.betweenClosed(-6,-61,-10,6,-61,8))world.setBlock(pos,Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
        for(var pos:BlockPos.betweenClosed(-6,-60,-5,6,-56,8))world.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        world.setDayTime(6000);world.setWeatherParameters(240000,0,false,false);
        place(player,new BlockPos(-3,-60,1),MachineDevelopers.NORMAL_ITEM.get());
        place(player,new BlockPos(3,-60,1),MachineDevelopers.ADVANCED_ITEM.get());
        var target=new Villager(EntityType.VILLAGER,world);target.moveTo(.5,-60,-2.5,180,0);target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
        target.setCustomName(Component.literal("Visual QA target"));target.setCustomNameVisible(true);world.addFreshEntity(target);
        var state=AbilityStorage.get(player);state.selectCategory("electromaster");state.setLevel(5);
        for(String id:new String[]{"arc_gen","charging","railgun"})state.experience.put(id,1d);
        state.activated=true;state.recoverAll();state.overloadFine=true;
        state.presets.edit(0,0,"arc_gen",id->PresetSkills.selectable(state,id));state.presets.edit(0,2,"charging",id->PresetSkills.selectable(state,id));state.presets.edit(0,3,"railgun",id->PresetSkills.selectable(state,id));state.presets.switchTo(0);
        player.getInventory().clearContent();player.getInventory().selected=0;player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,8));
        var portable=AcademyCraft.DEVELOPER.get().getDefaultInstance();new DeveloperItemEnergy(portable,DeveloperType.PORTABLE).energy(10000);player.getInventory().setItem(1,portable);
        player.getInventory().setItem(2,MachineDevelopers.NORMAL_ITEM.get().getDefaultInstance());player.getInventory().setItem(3,MachineDevelopers.ADVANCED_ITEM.get().getDefaultInstance());
        AbilityStorage.save(player);AcademyNetwork.sync(player);player.displayClientMessage(Component.literal("Controlled visual QA fixture: granted test mastery, finite full machines; not earned survival progression"),false);
    }
}
