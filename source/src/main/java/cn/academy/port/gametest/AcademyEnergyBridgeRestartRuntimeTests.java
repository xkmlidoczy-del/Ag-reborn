/* Opt-in compiled-only native Anvil/new-JVM RF pair fixture. GPLv3; see NOTICE. */
package cn.academy.port.gametest;
import cn.academy.port.bridge.*;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.solar.ClassicSolarGenerators;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;

/** Seed is declared finite fixture energy. Verification must use a genuinely separate JVM and isolated world. */
@GameTestHolder("academy_rf_restart") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="academy")
public final class AcademyEnergyBridgeRestartRuntimeTests {
    private static final UUID BOOT=UUID.randomUUID();
    private static final ChunkPos CHUNK=new ChunkPos(96,96);
    private static final BlockPos INPUT=new BlockPos(1540,80,1540),OUTPUT=INPUT.offset(4,0,0);
    private static final String PROPERTY="academy.energybridges.restart";
    private AcademyEnergyBridgeRestartRuntimeTests(){}
    private static String mode(){return System.getProperty(PROPERTY,"");}
    @SubscribeEvent public static void template(LevelEvent.Load event){if(mode().isEmpty()||!(event.getLevel() instanceof ServerLevel level))return;var tag=new CompoundTag();var size=new ListTag();for(int i=0;i<3;i++)size.add(IntTag.valueOf(4));tag.put("size",size);var blocks=new ListTag();var block=new CompoundTag();var position=new ListTag();for(int i=0;i<3;i++)position.add(IntTag.valueOf(0));block.put("pos",position);block.putInt("state",0);blocks.add(block);tag.put("blocks",blocks);var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);tag.put("entities",new ListTag());level.getStructureManager().getOrCreate(ResourceLocation.parse("academy_rf_restart:rf_restart_empty")).load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag);}
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void equal(double expected,double actual,String why){check(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-8,why+": "+actual);}
    private static CompoundTag expected(boolean input){BlockPos pos=input?INPUT:OUTPUT;var tag=new CompoundTag();tag.putString("id",input?"academy:rf_input":"academy:rf_output");tag.putInt("x",pos.getX());tag.putInt("y",pos.getY());tag.putInt("z",pos.getZ());tag.putDouble("energy",input?123.625:456.375);return tag;}
    private static CompoundTag anvil(Path region)throws Exception{check(Files.isRegularFile(region)&&Files.size(region)>=8192,"actual native RF-pair Anvil region exists before loading");try(var file=new RandomAccessFile(region.toFile(),"r")){file.seek(((CHUNK.x&31)+(CHUNK.z&31)*32)*4L);int location=file.readInt(),sector=location>>>8,count=location&255;check(sector>=2&&count>0&&(sector+(long)count)*4096<=file.length(),"bounded actual Anvil header");file.seek(sector*4096L);int length=file.readInt(),compression=file.readUnsignedByte();check(length>1&&length<=count*4096-4&&length<4*1024*1024&&compression==2,"bounded native inline zlib chunk");byte[] bytes=new byte[length-1];file.readFully(bytes);try(var stream=new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))){return NbtIo.read(stream,NbtAccounter.create(4*1024*1024));}}}
    @GameTest(template="rf_restart_empty",templateNamespace="academy_rf_restart",batch="academy_rf_restart",timeoutTicks=20)
    public static void rf_pair_native_anvil_separate_jvm_seed_or_verify(GameTestHelper h)throws Exception{
        check(mode().equals("seed")||mode().equals("verify"),"owner must set -D"+PROPERTY+"=seed or verify");var level=h.getLevel();Path world=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();check(world.getFileName().toString().equals("academy-energy-bridges-restart-world"),"dedicated isolated native RF-pair world required");check(!h.absolutePos(BlockPos.ZERO).closerThan(INPUT,256),"remote finite probe outside native test reset");Path marker=world.resolve("energy-bridges-seed.nbt"),region=world.resolve("region/r.3.3.mca");
        if(mode().equals("seed")){
            check(!Files.exists(marker)&&!Files.exists(region),"seed refuses an existing proof/region");var chunk=level.getChunk(CHUNK.x,CHUNK.z);check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),"remote probe naturally nonticking");check(level.isEmptyBlock(INPUT)&&level.isEmptyBlock(OUTPUT),"seed refuses occupied cells");level.setBlockAndUpdate(INPUT,ClassicEnergyBridges.INPUT.get().defaultBlockState());level.setBlockAndUpdate(OUTPUT,ClassicEnergyBridges.OUTPUT.get().defaultBlockState());var input=(ClassicRFInputBlockEntity)level.getBlockEntity(INPUT);var output=(ClassicRFOutputBlockEntity)level.getBlockEntity(OUTPUT);input.forgeEnergy().receiveEnergy(496,false);input.getProvidedEnergy(.375);output.injectEnergy(456.375);
            check(input.saveWithFullMetadata(level.registryAccess()).equals(expected(true))&&output.saveWithFullMetadata(level.registryAccess()).equals(expected(false)),"exact independent finite native seed pair");var proof=new CompoundTag();proof.putUUID("boot",BOOT);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",world.toString());proof.put("input",expected(true));proof.put("output",expected(false));NbtIo.writeCompressed(proof,marker);System.out.println("ACADEMY_RF_RESTART finite pair seeded; owner must gracefully stop this JVM for native Anvil persistence");h.succeed();return;
        }
        var proof=NbtIo.readCompressed(marker,NbtAccounter.create(4*1024*1024));check(!proof.getUUID("boot").equals(BOOT)&&proof.getLong("pid")!=ProcessHandle.current().pid(),"verification requires a genuinely separate cold JVM");check(proof.getString("world").equals(world.toString())&&proof.getCompound("input").equals(expected(true))&&proof.getCompound("output").equals(expected(false)),"exact same isolated world and declared finite expectation");var disk=anvil(region);check(disk.getInt("xPos")==CHUNK.x&&disk.getInt("zPos")==CHUNK.z,"actual persisted native chunk coordinates");var entries=disk.getList("block_entities",10);for(boolean input:new boolean[]{true,false}){var expectation=expected(input);expectation.putBoolean("keepPacked",false);int count=0;for(int i=0;i<entries.size();i++){var candidate=entries.getCompound(i);var cell=input?INPUT:OUTPUT;if(candidate.getInt("x")==cell.getX()&&candidate.getInt("y")==cell.getY()&&candidate.getInt("z")==cell.getZ()){check(candidate.equals(expectation),"exact native Anvil BE payload including keepPacked wrapper");count++;}}check(count==1,"each source bridge occurs exactlyonce in actual disk chunk");}
        var chunk=level.getChunk(CHUNK.x,CHUNK.z);check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),"cold probe remains naturally nonticking");for(boolean input:new boolean[]{true,false}){var cell=input?INPUT:OUTPUT;var tile=(ClassicEnergyBridgeBlockEntity)level.getBlockEntity(cell);check(tile!=null&&tile.available()&&tile.saveWithFullMetadata(level.registryAccess()).equals(expected(input)),"genuine registry cold loader restores exact native bridge");equal(input?123.625:456.375,tile.getEnergy(),"fractional IF cold-native persistence");check(level.getCapability(Capabilities.EnergyStorage.BLOCK,cell,Direction.UP)==tile.forgeEnergy(),"real cold FE capability reattached actualBE");check(input?level.getCapability(ClassicSolarGenerators.IMAG_FLUX,cell,Direction.UP)==tile:level.getCapability(MachineDevelopers.IMAG_FLUX,cell,Direction.UP)==tile,"real cold direction-specific IF capability");}
        var verified=proof.copy();verified.putUUID("verify_boot",BOOT);verified.putLong("verify_pid",ProcessHandle.current().pid());verified.putBoolean("verified",true);NbtIo.writeCompressed(verified,world.resolve("energy-bridges-verified.nbt"));System.out.println("ACADEMY_RF_RESTART actual native Anvil and separate-JVM cold pair passed; seed_pid="+proof.getLong("pid")+" verify_pid="+ProcessHandle.current().pid());h.succeed();
    }
}
