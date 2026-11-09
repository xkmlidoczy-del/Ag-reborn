/* Opt-in native Anvil/new-JVM Phase Generator fixture. GPLv3. See NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.phasegen.ClassicPhaseGeneratorBlockEntity;
import cn.academy.port.phasegen.ClassicPhaseGenerators;
import cn.academy.port.solar.ClassicSolarGenerators;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Separate enabled namespace. Main must stop seed JVM gracefully and reuse its isolated world. */
@GameTestHolder("academy_phase_restart")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class AcademyPhaseGeneratorRestartRuntimeTests {
    private static final UUID BOOT=UUID.randomUUID();
    private static final ChunkPos CHUNK=new ChunkPos(96,96);
    private static final BlockPos CELL=new BlockPos(1540,80,1540);
    private static final String PROPERTY="academy.phasegen.restart";
    private AcademyPhaseGeneratorRestartRuntimeTests(){}
    private static String mode(){return System.getProperty(PROPERTY,"");}
    @SubscribeEvent public static void template(LevelEvent.Load event){
        if(mode().isEmpty()||!(event.getLevel() instanceof ServerLevel level))return;
        var tag=new CompoundTag();var size=new ListTag();for(int i=0;i<3;i++)size.add(IntTag.valueOf(4));tag.put("size",size);var blocks=new ListTag();var block=new CompoundTag();var position=new ListTag();for(int i=0;i<3;i++)position.add(IntTag.valueOf(0));block.put("pos",position);block.putInt("state",0);blocks.add(block);tag.put("blocks",blocks);var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);tag.put("entities",new ListTag());level.getStructureManager().getOrCreate(ResourceLocation.parse("academy_phase_restart:phase_restart_empty")).load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void equal(double expected,double actual,String message){check(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-7,message+": "+actual);}
    private static ItemStack battery(){var unit=new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get());ClassicEnergyItemHelper.setEnergy(unit,456.375);unit.set(DataComponents.CUSTOM_NAME,Component.literal("Phase restart finite battery"));return unit;}
    private static CompoundTag expected(ServerLevel level){var tag=new CompoundTag();tag.putString("id","academy:phase_gen");tag.putInt("x",CELL.getX());tag.putInt("y",CELL.getY());tag.putInt("z",CELL.getZ());tag.putDouble("energy",123.625);tag.putInt("liquid",2345);var inventory=net.minecraft.core.NonNullList.withSize(3,ItemStack.EMPTY);inventory.set(0,new ItemStack(ClassicFusion.PHASE_MATTER_UNIT.get(),2));inventory.set(1,new ItemStack(ClassicFusion.MATTER_UNIT.get(),3));inventory.set(2,battery());net.minecraft.world.ContainerHelper.saveAllItems(tag,inventory,level.registryAccess());return tag;}
    private static CompoundTag readNativeAnvil(Path region)throws Exception{
        check(Files.isRegularFile(region)&&Files.size(region)>=8192,"native persisted phase region exists before chunk loading");
        try(var file=new RandomAccessFile(region.toFile(),"r")){
            file.seek(((CHUNK.x&31)+(CHUNK.z&31)*32)*4L);int location=file.readInt();int sector=location>>>8,count=location&255;check(sector>=2&&count>0&&(sector+(long)count)*4096<=file.length(),"bounded actual Anvil location");file.seek(sector*4096L);int length=file.readInt(),compression=file.readUnsignedByte();check(length>1&&length<=count*4096-4&&length<4*1024*1024&&compression==2,"bounded native inline zlib chunk required");byte[] bytes=new byte[length-1];file.readFully(bytes);try(var stream=new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))){return NbtIo.read(stream,NbtAccounter.create(4*1024*1024));}
        }
    }
    @GameTest(template="phase_restart_empty",templateNamespace="academy_phase_restart",batch="academy_phase_restart",timeoutTicks=20)
    public static void phase_generator_native_anvil_separate_jvm_seed_or_verify(GameTestHelper h)throws Exception{
        check(mode().equals("seed")||mode().equals("verify"),"set -D"+PROPERTY+"=seed or verify in the main-owned native lane");var level=h.getLevel();Path world=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();check(world.getFileName().toString().equals("academy-phase-generator-restart-world"),"requires dedicated isolated academy-phase-generator-restart-world");check(!h.absolutePos(BlockPos.ZERO).closerThan(CELL,256),"remote probe outside GameTest reset area");Path marker=world.resolve("phase-generator-seed.nbt"),region=world.resolve("region/r.3.3.mca");
        if(mode().equals("seed")){
            check(!Files.exists(marker)&&!Files.exists(region),"seed refuses existing native proof/region");var chunk=level.getChunk(CHUNK.x,CHUNK.z);check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),"remote full chunk is naturally nonticking");check(level.isEmptyBlock(CELL),"seed refuses occupied probe cell");level.setBlockAndUpdate(CELL,ClassicPhaseGenerators.BLOCK.get().defaultBlockState());var tile=(ClassicPhaseGeneratorBlockEntity)level.getBlockEntity(CELL);tile.buffer().load(123.625,2345);tile.setItem(0,new ItemStack(ClassicFusion.PHASE_MATTER_UNIT.get(),2));tile.setItem(1,new ItemStack(ClassicFusion.MATTER_UNIT.get(),3));tile.setItem(2,battery());check(tile.saveWithFullMetadata(level.registryAccess()).equals(expected(level)),"exact native finite seed payload");var proof=new CompoundTag();proof.putUUID("boot",BOOT);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",world.toString());proof.put("expected",expected(level));NbtIo.writeCompressed(proof,marker);System.out.println("ACADEMY_PHASE_RESTART seed prepared; native Anvil must persist through graceful seed-JVM shutdown");h.succeed();return;
        }
        var proof=NbtIo.readCompressed(marker,NbtAccounter.create(4*1024*1024));check(!proof.getUUID("boot").equals(BOOT),"verification requires separate cold JVM");check(proof.getString("world").equals(world.toString())&&proof.getCompound("expected").equals(expected(level)),"exact same isolated world and declared finite seed");var disk=readNativeAnvil(region);check(disk.getInt("xPos")==CHUNK.x&&disk.getInt("zPos")==CHUNK.z,"actual persisted chunk coordinates");var entities=disk.getList("block_entities",10);int matches=0;for(int i=0;i<entities.size();i++){var tag=entities.getCompound(i);if(tag.getInt("x")==CELL.getX()&&tag.getInt("y")==CELL.getY()&&tag.getInt("z")==CELL.getZ()){var diskExpected=expected(level);diskExpected.putBoolean("keepPacked",false);check(tag.equals(diskExpected),"full native Anvil payload including vanilla keepPacked=false wrapper equals independent finite expectation");matches++;}}check(matches==1,"exactly one actual persisted phaseBE");var chunk=level.getChunk(CHUNK.x,CHUNK.z);check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),"cold chunk remains naturally nonticking");check(level.getBlockState(CELL).is(ClassicPhaseGenerators.BLOCK.get()),"genuine cold native blockstate");var loaded=(ClassicPhaseGeneratorBlockEntity)level.getBlockEntity(CELL);check(loaded!=null&&loaded.available()&&loaded.saveWithFullMetadata(level.registryAccess()).equals(expected(level)),"native cold loader retains complete finiteBE/inventory");equal(123.625,loaded.getEnergy(),"actual fractional generatorIF");check(loaded.liquid()==2345,"actual native source liquid");equal(456.375,ClassicEnergyItemHelper.getEnergy(loaded.getItem(2)),"actual fractional native battery");check(level.getCapability(ClassicSolarGenerators.IMAG_FLUX,CELL,Direction.UP)==loaded,"cold native generatorcapability reattaches actualBE");var verified=proof.copy();verified.putUUID("verify_boot",BOOT);verified.putLong("verify_pid",ProcessHandle.current().pid());verified.putBoolean("verified",true);NbtIo.writeCompressed(verified,world.resolve("phase-generator-verified.nbt"));System.out.println("ACADEMY_PHASE_RESTART native Anvil and separate-JVM cold load passed; seed_pid="+proof.getLong("pid")+" verify_pid="+ProcessHandle.current().pid());h.succeed();
    }
}
