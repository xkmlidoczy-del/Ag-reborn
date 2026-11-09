package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.SkillAvailability;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.InductionFactors;
import cn.academy.port.machine.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Full native block, item, capability, NBT, lifecycle and authenticated session tests. Parent owns execution. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademyDeveloperMachineRuntimeTests {
    private static final String TEMPLATE="runtime_empty";
    private static final BlockPos BASE=new BlockPos(3,1,4);
    private static FakePlayer player(GameTestHelper helper){var player=new FakePlayer(helper.getLevel(),new GameProfile(UUID.randomUUID(),"[AC-Machine-Test]"));var at=helper.absoluteVec(new Vec3(3.5,1,2.5));player.moveTo(at.x,at.y,at.z,0,0);player.getAbilities().instabuild=false;return player;}
    private static BlockPlaceContext context(FakePlayer player,BlockPos origin,ItemStack stack){return new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(origin),Direction.UP,origin,false));}
    private static void cleanup(FakePlayer player){DevelopmentController.remove(player);AbilityStorage.remove(player);}
    private static MachineDeveloperBlockEntity place(GameTestHelper helper,FakePlayer player,MachineDeveloperItem item){
        var stack=new ItemStack(item,2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);var origin=helper.absolutePos(BASE);var result=item.place(context(player,origin,stack));helper.assertTrue(result.consumesAction(),"machine placement succeeds");helper.assertValueEqual(stack.getCount(),1,"all eight cells cost one item");return (MachineDeveloperBlockEntity)helper.getLevel().getBlockEntity(origin);
    }
    private static void near(FakePlayer player,MachineDeveloperBlockEntity machine){var pos=machine.getBlockPos();player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,0);}
    private static void equal(GameTestHelper helper,double a,double b,String label){helper.assertTrue(Math.abs(a-b)<1e-7,label+" "+a+" != "+b);}

    /** Declared prerequisite/mastery and finite battery fixtures, not a mined survival playthrough.
     * Every advertised category entry crosses the same authenticated ingress as the real GUI. */
    @GameTest(template=TEMPLATE,batch="academy_learning_ingress",timeoutTicks=30)
    public static void all_advertised_skills_portable_ingress_preserves_tier_prerequisites_and_finite_cost(GameTestHelper helper){learningIngress(helper,DeveloperType.PORTABLE);}
    @GameTest(template=TEMPLATE,batch="academy_learning_ingress",timeoutTicks=30)
    public static void all_advertised_skills_normal_session_preserves_tier_prerequisites_and_finite_cost(GameTestHelper helper){learningIngress(helper,DeveloperType.NORMAL);}
    @GameTest(template=TEMPLATE,batch="academy_learning_ingress",timeoutTicks=30)
    public static void all_advertised_skills_advanced_session_preserves_prerequisites_and_finite_cost(GameTestHelper helper){learningIngress(helper,DeveloperType.ADVANCED);}
    private static AbilityProgress prerequisites(FakePlayer player,SkillCatalog.Skill skill){
        var state=AbilityStorage.get(player);state.selectCategory(skill.category());state.setLevel(skill.level());
        for(var requirement:skill.requirements()){state.learn(requirement.id());state.experience.put(requirement.id(),(double)(float)requirement.exp());}
        if(skill.anyLearnedSkillLevel()!=0){var prerequisite=SkillCatalog.ALL.stream().filter(other->other.category().equals(skill.category())&&other.level()==skill.anyLearnedSkillLevel()&&!other.id().equals(skill.id())).findFirst().orElseThrow();state.learn(prerequisite.id());}
        return state;
    }
    private static boolean learningRequest(FakePlayer player,MachineDeveloperBlockEntity machine,AcademyNetwork.Request request){return machine==null?AcademyGameplay.requestFromClient(player,request):MachineDeveloperSessions.request(player,request);}
    private static void learningIngress(GameTestHelper helper,DeveloperType type){
        var player=player(helper);try{
            MachineDeveloperBlockEntity machine=type==DeveloperType.PORTABLE?null:place(helper,player,type==DeveloperType.NORMAL?MachineDevelopers.NORMAL_ITEM.get():MachineDevelopers.ADVANCED_ITEM.get());
            for(var skill:SkillCatalog.ALL){if(!SkillAvailability.learnable(skill.id()))continue;
                DevelopmentController.remove(player);var state=prerequisites(player,skill);
                var portable=AcademyCraft.DEVELOPER.get().getDefaultInstance();
                if(machine==null){player.setItemInHand(InteractionHand.MAIN_HAND,portable);new DeveloperItemEnergy(portable,type).energy(type.energy);}
                else{machine.battery().load(type.energy);near(player,machine);helper.assertTrue(machine.use(player),"physical origin opens sender-bound session "+skill.category()+":"+skill.id());}
                String token=machine==null?"":MachineDeveloperSessions.activeToken(player).orElseThrow().toString();
                var request=new AcademyNetwork.Request(machine==null?"learn":"machine_learn",machine==null?skill.id():token+":"+skill.id());
                state.level=Math.max(0,skill.level()-1);learningRequest(player,machine,request);
                helper.assertFalse(DevelopmentController.process(player).isDeveloping(),"source level gate "+skill.id());state.level=skill.level();
                for(var requirement:skill.requirements()){
                    Double previous=state.experience.remove(requirement.id());state.unlearnedExperience.put(requirement.id(),previous);learningRequest(player,machine,request);
                    helper.assertFalse(DevelopmentController.process(player).isDeveloping(),"retained source raw EXP cannot replace prerequisite learned bit "+skill.id()+":"+requirement.id());
                    state.unlearnedExperience.remove(requirement.id());state.experience.put(requirement.id(),previous);
                    if(requirement.exp()>0){
                        state.experience.put(requirement.id(),(double)Math.nextDown((float)requirement.exp()));learningRequest(player,machine,request);
                        helper.assertFalse(DevelopmentController.process(player).isDeveloping(),"previous representable source FLOAT remains below prerequisite "+skill.id()+":"+requirement.id());
                        state.experience.put(requirement.id(),previous);
                    }
                }
                learningRequest(player,machine,request);
                if(!type.supportsSkill(skill.level())){helper.assertFalse(DevelopmentController.process(player).isDeveloping(),"source insufficient developer tier "+skill.id());continue;}
                var process=DevelopmentController.process(player);helper.assertTrue(process.isDeveloping(),"advertised implemented skill crosses actual ingress "+skill.category()+":"+skill.id());
                int stimulations=ClassicRules.learningStimulations(skill.level());
                for(int tick=0;tick<stimulations*type.ticksPerStimulation();tick++)DevelopmentController.tick(player,snapshot->{});
                helper.assertTrue(process.state()==DevelopmentProcess.State.DONE&&state.learned(skill.id()),"genuine finite process completed "+skill.id());
                equal(helper,state.experience.get(skill.id()),0,"new stored skill mastery is zero "+skill.id());
                double energy=machine==null?new DeveloperItemEnergy(portable,type).energy():machine.battery().getEnergy();
                equal(helper,energy,type.energy-type.actualConsumption(stimulations),"exact TPS+1 native depletion "+skill.id());
            }
            helper.succeed();
        }finally{cleanup(player);}
    }

    @GameTest(template=TEMPLATE,batch="academy_developer_machines")
    public static void eight_cells_all_cardinal_rotations_and_collisions(GameTestHelper helper){
        var player=player(helper);try{
            for(var item:new MachineDeveloperItem[]{MachineDevelopers.NORMAL_ITEM.get(),MachineDevelopers.ADVANCED_ITEM.get()})for(int quadrant=0;quadrant<4;quadrant++){
                player.setYRot(quadrant*90);var machine=place(helper,player,item);var origin=machine.getBlockPos();var state=machine.getBlockState();helper.assertTrue(machine.facing()==MachineDeveloperBlock.direction(MachineDeveloperRules.Facing.values()[quadrant]),"exact legacy yaw mapping");
                for(int part=0;part<8;part++){var pos=origin.offset(MachineDeveloperBlock.offset(part,machine.facing()));var cell=helper.getLevel().getBlockState(pos);helper.assertTrue(cell.getBlock()==item.getBlock(),"all source occupied cells");helper.assertValueEqual(cell.getValue(MachineDeveloperBlock.PART),part,"exact part index");helper.assertTrue(Block.isShapeFullBlock(cell.getCollisionShape(helper.getLevel(),pos)),"full source per-cell collision");var slave=(MachineDeveloperBlockEntity)helper.getLevel().getBlockEntity(pos);helper.assertTrue(slave.origin()==machine,"slave redirects to same origin");}
                helper.assertTrue(helper.getLevel().isEmptyBlock(origin.above(2)),"source top-front cell stays unoccupied");helper.getLevel().destroyBlock(origin.offset(MachineDeveloperBlock.offset(7,machine.facing())),false);for(int part=0;part<8;part++)helper.assertTrue(helper.getLevel().isEmptyBlock(origin.offset(MachineDeveloperBlock.offset(part,machine.facing()))),"breaking any part removes complete structure");
            }
            helper.succeed();
        }finally{cleanup(player);}
    }
    @GameTest(template=TEMPLATE,batch="academy_developer_machines")
    public static void blocked_placement_refunds_and_preserves_world(GameTestHelper helper){
        var player=player(helper);try{
            var origin=helper.absolutePos(BASE);var blocked=origin.offset(MachineDeveloperBlock.offset(7,Direction.NORTH));helper.getLevel().setBlock(blocked,Blocks.STONE.defaultBlockState(),3);var stack=new ItemStack(MachineDevelopers.NORMAL_ITEM.get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            helper.assertTrue(!MachineDevelopers.NORMAL_ITEM.get().place(context(player,origin,stack)).consumesAction(),"late source cell obstruction rejects all placement");helper.assertValueEqual(stack.getCount(),2,"rejected placement consumes no item");helper.assertTrue(helper.getLevel().getBlockState(blocked).is(Blocks.STONE),"occupied source cell not overwritten");helper.assertTrue(helper.getLevel().isEmptyBlock(origin),"no partial origin left");
            helper.getLevel().removeBlock(blocked,false);var villager=helper.spawn(EntityType.VILLAGER,BASE.offset(0,0,2));villager.moveTo(blocked.getX()+.5,blocked.getY(),blocked.getZ()+.5,0,0);
            helper.assertTrue(!MachineDevelopers.NORMAL_ITEM.get().place(context(player,origin,stack)).consumesAction(),"entity collision in remote source cell rejects placement");helper.assertValueEqual(stack.getCount(),2,"entity collision refunds entire machine");villager.discard();helper.succeed();
        }finally{cleanup(player);}
    }
    @GameTest(template=TEMPLATE,batch="academy_developer_machines")
    public static void finite_if_and_fe_capabilities_on_every_part(GameTestHelper helper){
        var player=player(helper);try{
            var machine=place(helper,player,MachineDevelopers.NORMAL_ITEM.get());var world=helper.getLevel();equal(helper,machine.battery().getEnergy(),0,"fresh machine is empty");
            for(int part=0;part<8;part++){
                var pos=machine.getBlockPos().offset(MachineDeveloperBlock.offset(part,machine.facing()));var receiver=world.getCapability(MachineDevelopers.IMAG_FLUX,pos,Direction.UP);helper.assertTrue(receiver==machine.battery(),"native IF slave capability redirects to origin");var fe=world.getCapability(Capabilities.EnergyStorage.BLOCK,pos,Direction.NORTH);helper.assertTrue(fe!=null&&fe.canReceive()&&!fe.canExtract(),"finite receiver-only FE compatibility");helper.assertValueEqual(fe.getMaxEnergyStored(),200000,"50kIF times4FE capacity");
            }
            var fe=world.getCapability(Capabilities.EnergyStorage.BLOCK,machine.getBlockPos(),Direction.UP);helper.assertValueEqual(fe.receiveEnergy(10000,true),400,"simulate exact normal bandwidth");equal(helper,machine.battery().getEnergy(),0,"simulation writes nothing");helper.assertValueEqual(fe.receiveEnergy(1,false),1,"fractional IF FE transfer");helper.assertValueEqual(fe.receiveEnergy(10000,false),399,"shared source tick budget");helper.assertValueEqual(fe.receiveEnergy(10000,false),0,"repeat side receives cannot invent power");equal(helper,machine.battery().getEnergy(),100,"finite charge persisted in IF");
            helper.getLevel().destroyBlock(machine.getBlockPos(),false);helper.assertValueEqual(fe.receiveEnergy(10000,false),0,"retained capability refuses removed origin");helper.assertValueEqual(fe.getEnergyStored(),0,"retained capability no longer exposes removed battery");helper.succeed();
        }finally{cleanup(player);}
    }
    @GameTest(template=TEMPLATE,batch="academy_developer_machines")
    public static void machine_nbt_persists_energy_inventory_not_user(GameTestHelper helper){
        var player=player(helper);try{
            var machine=place(helper,player,MachineDevelopers.ADVANCED_ITEM.get());machine.battery().injectEnergy(12345.25);machine.setItem(0,new ItemStack(Items.DIAMOND,2));machine.setItem(1,new ItemStack(Items.IRON_INGOT,3));near(player,machine);helper.assertTrue(machine.use(player),"machine GUI opens");var saved=machine.saveWithFullMetadata(helper.getLevel().registryAccess());helper.assertTrue(!saved.hasUUID("user"),"occupancy not persisted");
            MachineDeveloperSessions.close(player);machine.battery().load(0);machine.clearContent();machine.loadWithComponents(saved,helper.getLevel().registryAccess());equal(helper,machine.battery().getEnergy(),12345.25,"native NBT retains fractional source IF");helper.assertValueEqual(machine.getItem(0).getCount(),2,"source inventory slot0 persists");helper.assertValueEqual(machine.getItem(1).getCount(),3,"source inventory slot1 persists");helper.assertTrue(machine.user()==null,"load clears transient occupancy");
            saved.putDouble("energy",Double.NaN);machine.loadWithComponents(saved,helper.getLevel().registryAccess());equal(helper,machine.battery().getEnergy(),0,"malformed NBT energy sanitizes");saved.putDouble("energy",1e9);machine.loadWithComponents(saved,helper.getLevel().registryAccess());equal(helper,machine.battery().getEnergy(),200000,"NBT capacity clamps");helper.succeed();
        }finally{cleanup(player);}
    }
    @GameTest(template=TEMPLATE,batch="academy_developer_machines")
    public static void sender_nonce_exclusivity_reach_and_gui_close(GameTestHelper helper){
        var owner=player(helper);var other=player(helper);try{
            var machine=place(helper,owner,MachineDevelopers.NORMAL_ITEM.get());machine.battery().injectEnergy(50000);near(owner,machine);near(other,machine);helper.assertTrue(machine.use(owner),"owner opens");var token=MachineDeveloperSessions.activeToken(owner).orElseThrow().toString();
            helper.assertTrue(!machine.use(other),"origin occupancy excludes second player on every part");helper.assertTrue(!MachineDeveloperSessions.request(other,new AcademyNetwork.Request("machine_level",token)),"stolen token cannot impersonate authenticated sender");helper.assertTrue(!MachineDeveloperSessions.request(owner,new AcademyNetwork.Request("machine_level",UUID.randomUUID().toString())),"forged nonce rejected");helper.assertTrue(!MachineDeveloperSessions.request(owner,new AcademyNetwork.Request("machine_learn",token+":plasma_cannon")),"unported skill cannot enter machine ingress");
            helper.assertTrue(MachineDeveloperSessions.request(owner,new AcademyNetwork.Request("machine_level",token)),"authorized real machine development starts");helper.assertTrue(DevelopmentController.process(owner).isDeveloping(),"real process active");helper.assertTrue(MachineDeveloperSessions.request(owner,new AcademyNetwork.Request("machine_close",token)),"GUI closes current occupancy");helper.assertTrue(machine.user()==null,"GUI close frees device");helper.assertTrue(DevelopmentController.process(owner).isDeveloping(),"source close does not abort development");
            double before=machine.battery().getEnergy();DevelopmentController.tick(owner,snapshot->{});equal(helper,before-machine.battery().getEnergy(),35,"process retains finite machine after GUI close");
            helper.assertTrue(machine.use(owner),"new GUI generation opens");var next=MachineDeveloperSessions.activeToken(owner).orElseThrow().toString();helper.assertTrue(!next.equals(token),"nonce changes on reopen");helper.assertTrue(!MachineDeveloperSessions.request(owner,new AcademyNetwork.Request("machine_close",token)),"stale close cannot terminate replacement GUI");
            var p=owner.position();owner.moveTo(p.x+30,p.y,p.z,0,0);helper.assertTrue(!MachineDeveloperSessions.request(owner,new AcademyNetwork.Request("machine_level",next)),"out-of-native-reach requests rejected");MachineDeveloperSessions.tick(machine);helper.assertTrue(machine.user()==null,"unreachable transient GUI occupancy is released");helper.assertTrue(DevelopmentController.process(owner).isDeveloping(),"source process not distance-aborted");helper.succeed();
        }finally{cleanup(owner);cleanup(other);}
    }
    @GameTest(template=TEMPLATE,batch="academy_developer_machines")
    public static void tier_stimulation_atomic_failure_and_portable_unchanged(GameTestHelper helper){
        var player=player(helper);try{
            var machine=place(helper,player,MachineDevelopers.NORMAL_ITEM.get());machine.battery().injectEnergy(34.5);var action=new DevelopmentProcess.Action(){public String id(){return "native-machine-test";}public int stimulations(){return 1;}public boolean validate(DevelopmentProcess.Developer developer){return true;}public void complete(){}};
            helper.assertTrue(DevelopmentController.start(player,machine.developer(),action),"trusted normal machine starts");DevelopmentController.tick(player,snapshot->{});helper.assertTrue(DevelopmentController.process(player).state()==DevelopmentProcess.State.FAILED,"underpowered machine fails");equal(helper,machine.battery().getEnergy(),34.5,"failed machine drain is atomic");
            var portable=new ItemStack(AcademyCraft.DEVELOPER.get());player.setItemInHand(InteractionHand.MAIN_HAND,portable);var portableEnergy=new DeveloperItemEnergy(portable,DeveloperType.PORTABLE);portableEnergy.energy(29.5);helper.assertTrue(DevelopmentController.start(player,DevelopmentController.portable(player),action),"portable seam unchanged");DevelopmentController.tick(player,snapshot->{});equal(helper,portableEnergy.energy(),0,"portable failure still drains remainder");
            machine.battery().load(1000);helper.assertTrue(DevelopmentController.start(player,machine.developer(),action),"normal restarts");for(int tick=0;tick<20;tick++)DevelopmentController.tick(player,snapshot->{});helper.assertTrue(DevelopmentController.process(player).isDeveloping(),"normal TPS20 means21actual ticks");DevelopmentController.tick(player,snapshot->{});helper.assertTrue(DevelopmentController.process(player).state()==DevelopmentProcess.State.DONE,"normal completes tick21");equal(helper,machine.battery().getEnergy(),265,"normal700/20x21 source IF debit");helper.succeed();
        }finally{cleanup(player);}
    }
    @GameTest(template=TEMPLATE,batch="academy_developer_machines")
    public static void removal_drops_one_machine_and_inventory_once(GameTestHelper helper){
        var player=player(helper);try{
            var machine=place(helper,player,MachineDevelopers.NORMAL_ITEM.get());var origin=machine.getBlockPos();machine.setItem(0,new ItemStack(Items.DIAMOND,2));machine.setItem(1,new ItemStack(Items.IRON_INGOT,3));helper.getLevel().destroyBlock(origin.offset(MachineDeveloperBlock.offset(6,machine.facing())),true,player);
            var drops=helper.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(origin).inflate(4));int machines=0,diamonds=0,iron=0;for(var drop:drops){var stack=drop.getItem();if(stack.is(MachineDevelopers.NORMAL_ITEM.get()))machines+=stack.getCount();if(stack.is(Items.DIAMOND))diamonds+=stack.getCount();if(stack.is(Items.IRON_INGOT))iron+=stack.getCount();}
            helper.assertValueEqual(machines,1,"destroyed source part yields one complete developer");helper.assertValueEqual(diamonds,2,"source slot0 refunded once");helper.assertValueEqual(iron,3,"source slot1 refunded once");for(int part=0;part<8;part++)helper.assertTrue(helper.getLevel().isEmptyBlock(origin.offset(MachineDeveloperBlock.offset(part,Direction.NORTH))),"no orphan after destruction");helper.succeed();
        }finally{cleanup(player);}
    }
    @GameTest(template=TEMPLATE,batch="academy_developer_machines")
    public static void advanced_category_reset_uses_real_factor_and_coil(GameTestHelper helper){
        var player=player(helper);
        try {
            var machine=place(helper,player,MachineDevelopers.ADVANCED_ITEM.get());machine.battery().injectEnergy(200000);near(player,machine);
            var state=AbilityStorage.get(player);state.selectCategory("vecmanip");state.setLevel(3);state.learn("vec_deviation");state.activated=true;
            helper.assertTrue(cn.academy.port.skill.VecDeviation.start(player,1),"source persistent old-category context starts before real machine reset");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AcademyCraft.MAGNETIC_COIL.get(),2));player.getInventory().setItem(5,InductionFactors.stack("teleporter"));
            helper.assertTrue(machine.use(player),"advanced opens while holding coil");var token=MachineDeveloperSessions.activeToken(player).orElseThrow().toString();
            helper.assertTrue(MachineDeveloperSessions.request(player,new AcademyNetwork.Request("machine_reset",token)),"real advanced reset accepted");
            var completing=DevelopmentController.process(player);
            java.util.function.Consumer<cn.academy.port.api.CategoryChangeEvent> low=event->{if(event.getEntity()==player){
                helper.assertFalse(cn.academy.port.skill.VecDeviation.active(player),"category NORMAL ends source persistent context");
                helper.assertTrue(DevelopmentController.process(player)==completing&&completing.isDeveloping(),"source category disposal keeps completing process");
                helper.assertTrue(MachineDeveloperSessions.activeToken(player).map(java.util.UUID::toString).filter(token::equals).isPresent(),"source category disposal keeps sender-owned GUI session");
            }};
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,cn.academy.port.api.CategoryChangeEvent.class,low);
            try{for(int tick=0;tick<30*16;tick++)DevelopmentController.tick(player,snapshot->{});}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(low);}
            helper.assertTrue(DevelopmentController.process(player)==completing&&completing.state()==DevelopmentProcess.State.DONE,"source reset30stimulations at16actual ticks");
            helper.assertTrue(state.category.equals("teleporter")&&state.level==2&&state.activated,"reset changes category and loses source one level while retaining activation");
            helper.assertTrue(!cn.academy.port.skill.VecDeviation.active(player)&&MachineDeveloperSessions.activeToken(player).map(java.util.UUID::toString).filter(token::equals).isPresent(),"context remains ended and original machine nonce/session remains available after completion");
            helper.assertTrue(player.getMainHandItem().isEmpty()&&player.getInventory().getItem(5).isEmpty(),"source clears entire coil and factor slots");equal(helper,machine.battery().getEnergy(),180800,"advanced reset source actual19200IF consumption");helper.succeed();
        } finally {cn.academy.port.skill.VecDeviation.remove(player);cleanup(player);}
    }
}
