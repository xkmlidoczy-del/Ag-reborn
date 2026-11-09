/* AcademyCraft1.0.7 EnergyBlockHelper/ChargingContext native runtime fixtures. GPLv3; see NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.fusion.ClassicFusorBlockEntity;
import cn.academy.port.machine.MachineDeveloperBlock;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.skill.ChargingEnergy;
import cn.academy.port.solar.ClassicSolarBlockEntity;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.survival.ClassicMaterials;
import cn.academy.port.wireless.ClassicWirelessDevices;
import cn.academy.port.wireless.ClassicWirelessMatrixBlock;
import cn.academy.port.wireless.ClassicWirelessMatrixBlockEntity;
import cn.academy.port.wireless.ClassicWirelessNodeBlock;
import cn.academy.port.wireless.ClassicWirelessNodeBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real ServerLevel/native block/capability targets, with no GUI or client dependency.
 * Fixtures supply blocks, finite energy and explicitly learned ability state: this is
 * adapter/order verification, not a claim of survival acquisition or socket transport.
 * Parent owns serial execution. This class/providers must remain excluded from the mod jar.
 * Source: EnergyBlockHelper managers and ChargingContext block branch, AcademyCraft1.0.7.
 */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
public final class AcademyNativeBlockChargingRuntimeTests {
    private static final String TEMPLATE="runtime_empty",BATCH="academy_native_block_charging";
    private static final BlockPos ORIGIN=new BlockPos(4,1,4),FE_POS=new BlockPos(2,1,2);
    private static final List<Direction> FACINGS=List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST);
    private record FixtureKey(Level level,BlockPos pos) {}
    private static final Map<FixtureKey,CountingFe> FE_FIXTURES=new HashMap<>();
    private AcademyNativeBlockChargingRuntimeTests() {}

    /** Separate from AcademyChargingRuntimeTests.REDSTONE_BLOCK, and only enabled in GameTest mode. */
    @SubscribeEvent public static void registerFixtureCapabilities(RegisterCapabilitiesEvent event){
        if(!GameTestHooks.isGametestEnabled())return;
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,(level,pos,state,entity,side)->{
            var storage=FE_FIXTURES.get(new FixtureKey(level,pos));
            if(storage!=null)storage.queries.add(side);
            return side==Direction.UP?storage:null;
        },Blocks.EMERALD_BLOCK);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_nodes_all_three_tiers_fractional_left_and_bandwidth_bypass(GameTestHelper h){
        var level=h.getLevel();
        for(var block:List.of(ClassicWirelessDevices.BASIC.get(),ClassicWirelessDevices.STANDARD.get(),ClassicWirelessDevices.ADVANCED.get())){
            var node=node(h,block);var pos=node.getBlockPos();
            h.assertTrue(ChargingEnergy.blockSupported(level,pos),node.nodeType()+" native node recognized");
            var target=ChargingEnergy.block(level,pos);h.assertTrue(target.supported(),"real native target classified supported");
            node.setEnergy(.375);double request=node.getBandwidth()+.625;
            equal(h,target.charge(request,false),.625,"native node false flag enforces one source per-call bandwidth");
            equal(h,node.getEnergy(),node.getBandwidth()+.375,"bounded native node preserves exact fractional IF");
            equal(h,target.charge(request,true),0,"native node true flag bypasses node bandwidth");
            equal(h,node.getEnergy(),2*node.getBandwidth()+1,"bypassed native charge transfers each request once");
            node.setEnergy(node.getMaxEnergy()-.375);
            equal(h,target.charge(2.125,true),1.75,"native node returns actual finite-capacity leftover");
            equal(h,node.getEnergy(),node.getMaxEnergy(),"native node cannot exceed source finite capacity");
            h.assertTrue(target.supported()&&ChargingEnergy.blockSupported(level,pos),"full native node remains supported");
            equal(h,target.charge(.125,false),.125,"full native node returns whole untouched request");
            level.removeBlock(pos,false);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_fusor_receiver_ignores_flag_and_retains_fractional_capacity_left(GameTestHelper h){
        h.setBlock(ORIGIN,ClassicFusion.FUSOR_BLOCK.get());var fusor=(ClassicFusorBlockEntity)h.getBlockEntity(ORIGIN);
        h.assertTrue(fusor.available(),"real source Fusor receiver is available");var target=ChargingEnergy.block(h.getLevel(),fusor.getBlockPos());
        h.assertTrue(target.supported()&&ChargingEnergy.blockSupported(h.getLevel(),fusor.getBlockPos()),"native ImagFluxReceiver Fusor recognized");
        double amount=fusor.getBandwidth()+.375;
        equal(h,target.charge(amount,false),0,"receiver injection ignores false bandwidth flag");
        equal(h,target.charge(amount,true),0,"receiver injection ignores true bandwidth flag");
        equal(h,fusor.getEnergy(),2*amount,"native receiver fractional IF added exactly once per call");
        equal(h,fusor.injectEnergy(fusor.getMaxEnergy()-fusor.getEnergy()-.125),0,"declared fixture fills receiver to fractional headroom");
        equal(h,target.charge(1.375,false),1.25,"Fusor truthful returned leftover");
        equal(h,fusor.getEnergy(),2000,"Fusor finite capacity is preserved");
        h.assertTrue(target.supported(),"full native Fusor stays supported");equal(h,target.charge(.625,true),.625,"full native receiver returns untouched amount");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_normal_advanced_all_four_facings_body_parts_alias_live_origin(GameTestHelper h){
        for(var block:List.of(MachineDevelopers.NORMAL.get(),MachineDevelopers.ADVANCED.get()))for(var facing:FACINGS){
            var root=machine(h,block,facing);var level=h.getLevel();var pos=root.getBlockPos();
            h.assertTrue(ChargingEnergy.blockSupported(level,pos),root.developerType()+" "+facing+" native origin recognized");
            var target=ChargingEnergy.block(level,pos);double request=root.developerType().bandwidth+.375;
            equal(h,target.charge(request,false),0,"native receiver false flag does not apply portable/FE bandwidth");
            equal(h,target.charge(request,true),0,"native receiver true flag remains same injection manager");
            equal(h,root.battery().getEnergy(),2*request,"actual orientation origin receives one native fractional representation");
            for(int part=1;part<8;part++){
                BlockPos slavePos=pos.offset(MachineDeveloperBlock.offset(part,facing));var slave=(MachineDeveloperBlockEntity)level.getBlockEntity(slavePos);
                h.assertTrue(slave!=null&&!slave.isOrigin()&&slave.origin()==root,"actual native slave belongs to exact source root");
                // Preserve the existing modern body-part redirect, with one exact native
                // transfer rather than the original unusable separate slave-local store.
                h.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK,slavePos,Direction.UP)!=null,"fixture verifies existing slave FE bridge exists");
                h.assertTrue(ChargingEnergy.blockSupported(level,slavePos),"intact source body part aliases live finite origin");
                var slaveTarget=ChargingEnergy.block(level,slavePos);h.assertTrue(slaveTarget.supported(),"native body-part charging supported");
                equal(h,slaveTarget.charge(.125,true),0,"body-part alias retains fractional IF below one FE");
                equal(h,root.battery().getEnergy(),2*request+part*.125,"each native body-part request reaches finite origin once");
                equal(h,slave.battery().persistedEnergy(),0,"modern correction never fills unusable slave-local store");
            }
            root.battery().energy(root.developerType().energy-.375);
            equal(h,target.charge(1.125,false),.75,"native machine truthful finite capacity leftover");
            equal(h,root.battery().getEnergy(),root.developerType().energy,"actual native machine finite full capacity");
            h.assertTrue(target.supported()&&ChargingEnergy.blockSupported(level,pos),"full native machine still supported");
            level.removeBlock(pos,false);
            h.assertFalse(target.supported(),"removed canonical origin invalidates old body-part target");
            equal(h,target.charge(15.375,true),15.375,"removed structure cannot be charged by a captured target");
            // Deliberately supply a disconnected origin-only cell, not a complete device.
            var disconnected=block.defaultBlockState().setValue(MachineDeveloperBlock.FACING,facing).setValue(MachineDeveloperBlock.PART,0);
            level.setBlock(pos,disconnected,Block.UPDATE_CLIENTS);
            h.assertFalse(ChargingEnergy.blockSupported(level,pos),"incomplete supplied origin remains unsupported");
            equal(h,ChargingEnergy.block(level,pos).charge(15.375,true),15.375,"incomplete structure neither accepts native IF nor falls back to FE");
            level.removeBlock(pos,false);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_solar_matrix_null_air_and_unloaded_positions_are_unchanged(GameTestHelper h){
        var level=h.getLevel();h.setBlock(ORIGIN,ClassicSolarGenerators.BLOCK.get());var solar=(ClassicSolarBlockEntity)h.getBlockEntity(ORIGIN);solar.buffer().load(123.375);
        unsupported(h,level,solar.getBlockPos(),"source generator is not an input manager");equal(h,solar.getEnergy(),123.375,"query/charge cannot inject or remove solar generated IF");
        level.removeBlock(solar.getBlockPos(),false);var matrix=matrix(h);
        h.assertTrue(matrix.available()&&matrix.getCapacity()==8,"real complete three-plate/core matrix is working fixture");
        for(int part=0;part<8;part++)unsupported(h,level,matrix.getBlockPos().offset(ClassicWirelessMatrixBlock.offset(part,matrix.facing())),"matrix origin/slave has no block charging input manager");
        h.assertValueEqual(matrix.getCapacity(),8,"unsupported matrix queries preserve actual source core/plate state");
        level.removeBlock(matrix.getBlockPos(),false);unsupported(h,level,h.absolutePos(ORIGIN),"loaded air unsupported");
        unsupported(h,null,null,"null level and target unsupported");unsupported(h,level,null,"null target unsupported");
        BlockPos absent=new BlockPos(29000000,level.getMinBuildHeight()+1,29000000);
        h.assertTrue(level.getChunkSource().getChunkNow(absent.getX()>>4,absent.getZ()>>4)==null,"remote native chunk fixture starts absent");
        int loaded=level.getChunkSource().getLoadedChunksCount();
        for(int repeat=0;repeat<4;repeat++)unsupported(h,level,absent,"remote unloaded target unsupported without force-loading");
        h.assertValueEqual(level.getChunkSource().getLoadedChunksCount(),loaded,"native support/target/charge queries do not create loaded chunks");
        h.assertTrue(level.getChunkSource().getChunkNow(absent.getX()>>4,absent.getZ()>>4)==null,"same absent chunk remains absent");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void fallback_fe_is_up_only_one_receive_per_call_and_truthful_fractional_left(GameTestHelper h){
        var level=h.getLevel();h.setBlock(FE_POS,Blocks.EMERALD_BLOCK);var key=new FixtureKey(level,h.absolutePos(FE_POS));var storage=new CountingFe(100,7);FE_FIXTURES.put(key,storage);level.invalidateCapabilities(key.pos());
        try{
            h.assertTrue(ChargingEnergy.blockSupported(level,key.pos()),"real FE-only emerald fixture supported");
            var target=ChargingEnergy.block(level,key.pos());h.assertTrue(target.supported(),"real FE target supported independent of receive amount");
            equal(h,target.charge(3.375,true),1.625,"one fallback request13FE accepts7FE and retains fractional IF");
            h.assertValueEqual(storage.energy,7,"single FE receive adds only reported amount");h.assertValueEqual(storage.receives,1,"one target charge makes exactly one receiveEnergy call");
            h.assertValueEqual(storage.requests.getFirst(),13,"fractional IF converts with source floor at4FE per IF");
            equal(h,target.charge(3.375,false),1.625,"ignore flag cannot bypass FE receiver per-call bandwidth");
            h.assertValueEqual(storage.receives,2,"second target charge adds exactly one receive call");h.assertValueEqual(storage.energy,14,"FE is not duplicate representation");
            h.assertTrue(!storage.queries.isEmpty()&&storage.queries.stream().allMatch(side->side==Direction.UP),"charging support/lookup always request UP, never hit-side probing");
            storage.energy=storage.capacity;h.assertTrue(ChargingEnergy.blockSupported(level,key.pos())&&target.supported(),"full FE-only capability still supported");
            int before=storage.receives;equal(h,target.charge(.125,true),.125,"sub-FE request remains fractional IF");h.assertValueEqual(storage.receives,before,"sub-FE charge never calls zero receive");
            equal(h,target.charge(2.125,true),2.125,"full FE fallback truthfully returns all IF");h.assertValueEqual(storage.receives,before+1,"full FE request still at most one real receive call");
            h.succeed();
        }finally{FE_FIXTURES.remove(key);level.invalidateCapabilities(key.pos());}
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void native_normal_precedes_fe_no_double_transfer_and_no_fe_budget_used(GameTestHelper h){
        var root=machine(h,MachineDevelopers.NORMAL.get(),Direction.NORTH);var level=h.getLevel();var pos=root.getBlockPos();
        var bridge=level.getCapability(Capabilities.EnergyStorage.BLOCK,pos,Direction.UP);h.assertTrue(bridge!=null,"actual Normal has existing native-plus-FE compatibility");
        int quota=(int)(DeveloperType.NORMAL.bandwidth*ChargingEnergy.FE_PER_IF);
        h.assertValueEqual(bridge.receiveEnergy(quota,true),quota,"fresh FE quota starts untouched");
        var target=ChargingEnergy.block(level,pos);equal(h,target.charge(250.375,false),0,"native receiver wins over simultaneous limited FE representation");
        equal(h,root.battery().getEnergy(),250.375,"preferred native manager adds requested IF exactly once");
        h.assertValueEqual(bridge.receiveEnergy(quota,true),quota,"native selection never consumes same-world-tick FE receive quota");
        var slave=root.getBlockPos().offset(MachineDeveloperBlock.offset(7,root.facing()));
        equal(h,ChargingEnergy.block(level,slave).charge(.125,true),0,"body-part preferred native manager preserves sub-FE fractional request");
        equal(h,root.battery().getEnergy(),250.5,"native-over-FE preference preserves exact fraction without double transfer");
        h.assertValueEqual(bridge.receiveEnergy(quota,true),quota,"second native request still leaves FE quota untouched");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void block_session_supplied_learned_state_passes_bypass_and_source_exhaustion_order(GameTestHelper h){
        var node=node(h,ClassicWirelessDevices.BASIC.get());node.setEnergy(.375);
        var state=learnedFixture(.5);var session=CurrentChargingSession.begin(state,false,false);
        h.assertTrue(session!=null&&session.active()&&!session.itemMode(),"explicitly supplied learned mastery fixture starts real block session");
        var probe=new NativeTargetProbe(ChargingEnergy.block(h.getLevel(),node.getBlockPos()));
        double speed=CurrentChargingSession.speed(.5),cost=CurrentChargingSession.consumption(.5),beforeExp=state.exp("charging");
        h.assertTrue(session.tick(probe,false)==CurrentChargingSession.TickResult.CONTINUE,"actual block CurrentChargingSession tick succeeds");
        h.assertValueEqual(probe.calls,1,"real session delegates native block charge exactly once");h.assertTrue(probe.ignoreBandwidth,"real block branch passes source bypass true");
        equal(h,probe.request,speed,"block native request uses captured source mastery speed");equal(h,node.getEnergy(),.375+speed,"real native fractional buffer charged by session");
        equal(h,state.cp,1800-cost,"successful block tick pays source CP after charging");equal(h,state.exp("charging")-beforeExp,CurrentChargingSession.SUPPORTED_EXPERIENCE,"native supported source EXP awarded");session.end();
        node.setEnergy(.625);var exhausted=learnedFixture(0);exhausted.cp=0;var finalSession=CurrentChargingSession.begin(exhausted,false,false);
        h.assertTrue(finalSession!=null&&finalSession.active(),"zero-CP declared skill fixture still starts source zero-CP held context");
        var finalProbe=new NativeTargetProbe(ChargingEnergy.block(h.getLevel(),node.getBlockPos()));
        h.assertTrue(finalSession.tick(finalProbe,false)==CurrentChargingSession.TickResult.RESOURCE_EXHAUSTED,"actual block tick fails CP only after source transfer");
        equal(h,node.getEnergy(),15.625,"real native target retains final free charge before failed CP");equal(h,exhausted.exp("charging"),CurrentChargingSession.SUPPORTED_EXPERIENCE,"source supported EXP retained before failed CP");equal(h,exhausted.cp,0,"failed CP leaves zero balance");
        h.assertTrue(!finalSession.active()&&finalProbe.ignoreBandwidth&&finalProbe.calls==1,"exhausted native block branch terminates after one bypass request");
        h.assertTrue(finalSession.tick(finalProbe,false)==CurrentChargingSession.TickResult.ALREADY_ENDED,"ended session replay cannot charge again");h.assertValueEqual(finalProbe.calls,1,"ended replay never re-enters native target");equal(h,node.getEnergy(),15.625,"ended replay preserves finite target IF");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void full_native_node_fusor_and_both_machine_tiers_still_award_supported_session_exp(GameTestHelper h){
        for(var block:List.of(ClassicWirelessDevices.BASIC.get(),ClassicWirelessDevices.STANDARD.get(),ClassicWirelessDevices.ADVANCED.get())){
            var node=node(h,block);node.setEnergy(node.getMaxEnergy());fullSession(h,ChargingEnergy.block(h.getLevel(),node.getBlockPos()),"full "+node.nodeType()+" node");equal(h,node.getEnergy(),node.getMaxEnergy(),"session cannot overflow full native node");h.getLevel().removeBlock(node.getBlockPos(),false);
        }
        h.setBlock(ORIGIN,ClassicFusion.FUSOR_BLOCK.get());var fusor=(ClassicFusorBlockEntity)h.getBlockEntity(ORIGIN);fusor.injectEnergy(fusor.getMaxEnergy());fullSession(h,ChargingEnergy.block(h.getLevel(),fusor.getBlockPos()),"full Fusor");equal(h,fusor.getEnergy(),fusor.getMaxEnergy(),"session cannot overflow full native Fusor");h.getLevel().removeBlock(fusor.getBlockPos(),false);
        for(var block:List.of(MachineDevelopers.NORMAL.get(),MachineDevelopers.ADVANCED.get())){
            var machine=machine(h,block,Direction.NORTH);machine.battery().energy(machine.developerType().energy);fullSession(h,ChargingEnergy.block(h.getLevel(),machine.getBlockPos()),"full "+machine.developerType()+" receiver");equal(h,machine.battery().getEnergy(),machine.developerType().energy,"session cannot overflow full native developer");h.getLevel().removeBlock(machine.getBlockPos(),false);
        }
        h.succeed();
    }

    private static ClassicWirelessNodeBlockEntity node(GameTestHelper h,ClassicWirelessNodeBlock block){h.setBlock(ORIGIN,block);var result=(ClassicWirelessNodeBlockEntity)h.getBlockEntity(ORIGIN);h.assertTrue(result.available(),"native node fixture attached to actual ServerLevel");return result;}
    private static MachineDeveloperBlockEntity machine(GameTestHelper h,MachineDeveloperBlock block,Direction facing){
        var level=h.getLevel();var origin=h.absolutePos(ORIGIN);
        for(int part=0;part<8;part++)level.setBlock(origin.offset(MachineDeveloperBlock.offset(part,facing)),block.defaultBlockState().setValue(MachineDeveloperBlock.FACING,facing).setValue(MachineDeveloperBlock.PART,part),Block.UPDATE_CLIENTS);
        var root=(MachineDeveloperBlockEntity)level.getBlockEntity(origin);h.assertTrue(root!=null&&root.isOrigin()&&root.available(),"complete actual eight-cell "+block.type+" "+facing+" native fixture");return root;
    }
    private static ClassicWirelessMatrixBlockEntity matrix(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(ORIGIN);var block=ClassicWirelessDevices.MATRIX.get();
        for(int part=0;part<8;part++)level.setBlock(origin.offset(ClassicWirelessMatrixBlock.offset(part,Direction.NORTH)),block.defaultBlockState().setValue(ClassicWirelessMatrixBlock.FACING,Direction.NORTH).setValue(ClassicWirelessMatrixBlock.PART,part),Block.UPDATE_CLIENTS);
        var root=(ClassicWirelessMatrixBlockEntity)level.getBlockEntity(origin);
        for(int slot=0;slot<3;slot++)root.setItem(slot,new ItemStack(ClassicMaterials.CONSTRAINT_PLATE.get()));
        root.setItem(3,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy","matrix_core_0"))));return root;
    }
    private static void unsupported(GameTestHelper h,Level level,BlockPos pos,String label){h.assertFalse(ChargingEnergy.blockSupported(level,pos),label+" support");var target=ChargingEnergy.block(level,pos);h.assertFalse(target.supported(),label+" native target");equal(h,target.charge(123.375,true),123.375,label+" leaves all IF unchanged");}
    /** Test-provided learned skill state, deliberately not a survival-learning fixture. */
    private static AbilityProgress learnedFixture(double mastery){var state=new AbilityProgress();state.selectCategory("electromaster");state.setLevel(1);state.activated=true;state.experience.put("charging",mastery);return state;}
    private static void fullSession(GameTestHelper h,CurrentChargingSession.Target target,String label){
        h.assertTrue(target.supported(),label+" is supported before tick");var state=learnedFixture(0);var session=CurrentChargingSession.begin(state,false,false);h.assertTrue(session!=null&&session.active(),label+" declared learned fixture starts");
        h.assertTrue(session.tick(target,false)==CurrentChargingSession.TickResult.CONTINUE,label+" source tick continues despite zero accepted IF");equal(h,state.exp("charging"),CurrentChargingSession.SUPPORTED_EXPERIENCE,label+" gets full supported EXP");equal(h,state.cp,1797,label+" source tick pays CP");h.assertTrue(target.supported(),label+" still supported after no-op charge");session.end();
    }
    private static void equal(GameTestHelper h,double actual,double expected,String label){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<=1e-7,label+": expected "+expected+", got "+actual);}
    private static final class NativeTargetProbe implements CurrentChargingSession.Target {
        private final CurrentChargingSession.Target actual;private int calls;private boolean ignoreBandwidth;private double request;
        NativeTargetProbe(CurrentChargingSession.Target actual){this.actual=actual;}
        public boolean present(){return actual.present();}public boolean supported(){return actual.supported();}
        public double charge(double amount,boolean ignore){calls++;ignoreBandwidth=ignore;request=amount;return actual.charge(amount,ignore);}
    }
    private static final class CountingFe implements IEnergyStorage {
        final int capacity,bandwidth;int energy,receives;final List<Direction> queries=new ArrayList<>();final List<Integer> requests=new ArrayList<>();
        CountingFe(int capacity,int bandwidth){this.capacity=capacity;this.bandwidth=bandwidth;}
        public int receiveEnergy(int maxReceive,boolean simulate){if(!simulate){receives++;requests.add(maxReceive);}int accepted=Math.max(0,Math.min(maxReceive,Math.min(bandwidth,capacity-energy)));if(!simulate)energy+=accepted;return accepted;}
        public int extractEnergy(int maxExtract,boolean simulate){return 0;}public int getEnergyStored(){return energy;}public int getMaxEnergyStored(){return capacity;}public boolean canExtract(){return false;}public boolean canReceive(){return true;}
    }
}
