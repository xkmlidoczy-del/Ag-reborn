package cn.academy.port.gametest;

import cn.academy.port.machine.MachineDeveloperBlock;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.solar.ClassicSolarBlockEntity;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.wireless.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Compiled-only handoff. Parent owns native launches and end-to-end recipe/daylight route. */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademyWirelessGraphRuntimeTests {
    private static final String TEMPLATE="runtime_empty", BATCH="academy_wireless_graph";
    private static final BlockPos NODE=new BlockPos(3,1,3), SOLAR=new BlockPos(2,1,3), MATRIX=new BlockPos(8,1,3), MACHINE=new BlockPos(5,1,6);
    private AcademyWirelessGraphRuntimeTests() {}
    private static void equal(GameTestHelper helper,double expected,double actual,String message){helper.assertTrue(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-7,message+": "+actual);}
    private static ClassicWirelessMatrixBlockEntity matrix(GameTestHelper helper){
        var state=ClassicWirelessDevices.MATRIX.get().defaultBlockState().setValue(ClassicWirelessMatrixBlock.FACING,Direction.NORTH);
        var level=helper.getLevel();var origin=helper.absolutePos(MATRIX);
        for(int part=0;part<8;part++)level.setBlock(origin.offset(ClassicWirelessMatrixBlock.offset(part,Direction.NORTH)),state.setValue(ClassicWirelessMatrixBlock.PART,part),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        return (ClassicWirelessMatrixBlockEntity)level.getBlockEntity(origin);
    }
    private static MachineDeveloperBlockEntity machine(GameTestHelper helper){
        var state=MachineDevelopers.NORMAL.get().defaultBlockState().setValue(MachineDeveloperBlock.FACING,Direction.NORTH);
        var level=helper.getLevel();var origin=helper.absolutePos(MACHINE);
        for(int part=0;part<8;part++)level.setBlock(origin.offset(MachineDeveloperBlock.offset(part,Direction.NORTH)),state.setValue(MachineDeveloperBlock.PART,part),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        return (MachineDeveloperBlockEntity)level.getBlockEntity(origin);
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void wireless_saveddata_native_roundtrip_and_loaded_removal(GameTestHelper helper){
        var level=helper.getLevel();helper.setBlock(NODE,ClassicWirelessDevices.BASIC.get());helper.setBlock(SOLAR,ClassicSolarGenerators.BLOCK.get());var matrix=matrix(helper);
        var data=ClassicWirelessSavedData.get(level);var graph=data.graph();var nodePos=ClassicWirelessSavedData.pos(helper.absolutePos(NODE));var solarPos=ClassicWirelessSavedData.pos(helper.absolutePos(SOLAR));var matrixPos=ClassicWirelessSavedData.pos(matrix.getBlockPos());
        helper.assertTrue(graph.createNetwork(matrixPos,"native-fixture","game-password"),"actual matrix origin create");helper.assertTrue(graph.linkGenerator(nodePos,solarPos,"",false),"actual solar native IF endpoint link");
        data.setDirty(false);var encoded=data.save(new CompoundTag(),level.registryAccess());var loaded=ClassicWirelessSavedData.load(level,encoded);
        helper.assertTrue(loaded.graph().snapshot().equals(graph.snapshot()),"actual SavedData all graph/password links roundtrip");helper.assertTrue(loaded.graph().networkPassword(matrixPos).equals("game-password"),"persisted fictional game network password");
        level.removeBlock(helper.absolutePos(SOLAR),false);loaded.graph().tick();helper.assertTrue(loaded.graph().nodeForGenerator(solarPos)==null,"loaded removed solar auto unlinked");
        level.removeBlock(matrix.getBlockPos(),false);loaded.graph().tick();helper.assertTrue(loaded.graph().networkAt(matrixPos)==null,"loaded removed origin disposes network");
        graph.removeNetwork(matrixPos);graph.unlinkGenerator(solarPos);helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void wireless_native_resolver_origin_aliases_and_unloaded_noncreation(GameTestHelper helper){
        var level=helper.getLevel();var machine=machine(helper);var matrix=matrix(helper);helper.setBlock(NODE,ClassicWirelessDevices.BASIC.get());helper.setBlock(SOLAR,ClassicSolarGenerators.BLOCK.get());
        var resolver=new ClassicWirelessSavedData.NativeResolver(level);var root=ClassicWirelessSavedData.pos(machine.getBlockPos());
        helper.assertTrue(resolver.receiver(root)==machine.battery(),"real IF capability only canonical machine origin");
        for(int part=1;part<8;part++){var slave=ClassicWirelessSavedData.pos(machine.getBlockPos().offset(MachineDeveloperBlock.offset(part,Direction.NORTH)));helper.assertTrue(level.getCapability(MachineDevelopers.IMAG_FLUX,ClassicWirelessSavedData.blockPos(slave),Direction.UP)==machine.battery(),"existing native slave capability aliases origin");helper.assertTrue(resolver.receiver(slave)==null,"graph excludes alias so same receiver cannot multiply throughput");}
        helper.assertTrue(resolver.matrix(ClassicWirelessSavedData.pos(matrix.getBlockPos()))==matrix,"actual matrix origin identity");for(int part=1;part<8;part++)helper.assertTrue(resolver.matrix(ClassicWirelessSavedData.pos(matrix.getBlockPos().offset(ClassicWirelessMatrixBlock.offset(part,Direction.NORTH))))==null,"matrix subpart cannot advertise extra network");
        var unloaded=new ClassicWirelessGraph.Pos(29000000,level.getMinBuildHeight()+1,29000000);helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.x()>>4,unloaded.z()>>4)==null,"declared remote chunk starts unloaded");int count=level.getChunkSource().getLoadedChunksCount();
        helper.assertTrue(!resolver.isLoaded(unloaded)&&resolver.node(unloaded)==null&&resolver.matrix(unloaded)==null&&resolver.generator(unloaded)==null&&resolver.receiver(unloaded)==null,"all native resolver endpoints remain null while unloaded");
        helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(),count,"no native chunk loads by graph lookup");
        var graph=ClassicWirelessSavedData.get(level).graph();var nodePos=ClassicWirelessSavedData.pos(helper.absolutePos(NODE));var slave=ClassicWirelessSavedData.pos(machine.getBlockPos().offset(MachineDeveloperBlock.offset(1,Direction.NORTH)));
        helper.assertTrue(!graph.linkReceiver(nodePos,slave,"",false),"alias reference cannot link");helper.assertTrue(graph.linkReceiver(nodePos,root,"",false),"real canonical machine receiver links");graph.unlinkReceiver(root);helper.succeed();
    }
    @GameTest(template=TEMPLATE,batch=BATCH)
    public static void wireless_native_saveddata_retains_unloaded_graph_refs(GameTestHelper helper){
        var level=helper.getLevel();var unloaded=new ClassicWirelessGraph.Pos(29000000,64,29000000);var node=new ClassicWirelessGraph.Pos(29000001,64,29000000);var generator=new ClassicWirelessGraph.Pos(29000002,64,29000000);var receiver=new ClassicWirelessGraph.Pos(29000003,64,29000000);
        // Persistence fixture contains references only, with zero buffer and no invented node/generator energy.
        var state=new ClassicWirelessGraph.State(java.util.List.of(new ClassicWirelessGraph.NetworkData(unloaded,"persist", "fictional",0,java.util.List.of(node))),java.util.List.of(new ClassicWirelessGraph.ConnectionData(node,java.util.List.of(generator),java.util.List.of(receiver))));
        var encoded=ClassicWirelessSavedData.writeState(new CompoundTag(),state);int count=level.getChunkSource().getLoadedChunksCount();var data=ClassicWirelessSavedData.load(level,encoded);data.graph().tick();
        helper.assertTrue(data.graph().snapshot().equals(state),"missing unloaded native refs preserved through deserialize/tick/save");helper.assertValueEqual(level.getChunkSource().getLoadedChunksCount(),count,"native restore/tick/snapshot never force-load");helper.succeed();
    }
}
