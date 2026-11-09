/* Opt-in native Anvil/new-JVM Cat Engine fixture. GPLv3. See NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.cat.ClassicCatEngineBlockEntity;
import cn.academy.port.cat.ClassicCatEngines;
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
@GameTestHolder("academy_misc_restart")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class AcademyClassicMiscRestartRuntimeTests {
    private static final UUID BOOT=UUID.randomUUID();
    private static final ChunkPos CHUNK=new ChunkPos(128,128);
    private static final BlockPos CELL=new BlockPos(2052,80,2052);
    private static final String PROPERTY="academy.misc.restart";
    private AcademyClassicMiscRestartRuntimeTests(){}
    private static String mode(){return System.getProperty(PROPERTY,"");}
    @SubscribeEvent public static void template(LevelEvent.Load event){
        if(mode().isEmpty()||!(event.getLevel() instanceof ServerLevel level))return;
        var tag=new CompoundTag();var size=new ListTag();for(int i=0;i<3;i++)size.add(IntTag.valueOf(4));tag.put("size",size);var blocks=new ListTag();var block=new CompoundTag();var position=new ListTag();for(int i=0;i<3;i++)position.add(IntTag.valueOf(0));block.put("pos",position);block.putInt("state",0);blocks.add(block);tag.put("blocks",blocks);var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);tag.put("entities",new ListTag());level.getStructureManager().getOrCreate(ResourceLocation.parse("academy_misc_restart:misc_restart_empty")).load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void equal(double expected,double actual,String message){check(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-7,message+": "+actual);}
    private static CompoundTag expected(ServerLevel level){var tag=new CompoundTag();tag.putString("id","academy:cat_engine");tag.putInt("x",CELL.getX());tag.putInt("y",CELL.getY());tag.putInt("z",CELL.getZ());tag.putDouble("energy",123.625);return tag;}
    private static CompoundTag readNativeAnvil(Path region)throws Exception{
        check(Files.isRegularFile(region)&&Files.size(region)>=8192,"native persisted Cat region exists before chunk loading");
        try(var file=new RandomAccessFile(region.toFile(),"r")){
            file.seek(((CHUNK.x&31)+(CHUNK.z&31)*32)*4L);int location=file.readInt();int sector=location>>>8,count=location&255;check(sector>=2&&count>0&&(sector+(long)count)*4096<=file.length(),"bounded actual Anvil location");file.seek(sector*4096L);int length=file.readInt(),compression=file.readUnsignedByte();check(length>1&&length<=count*4096-4&&length<4*1024*1024&&compression==2,"bounded native inline zlib chunk required");byte[] bytes=new byte[length-1];file.readFully(bytes);try(var stream=new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))){return NbtIo.read(stream,NbtAccounter.create(4*1024*1024));}
        }
    }
    @GameTest(template="misc_restart_empty",templateNamespace="academy_misc_restart",batch="academy_misc_restart",timeoutTicks=20)
    public static void classic_cat_native_anvil_separate_jvm_seed_or_verify(GameTestHelper h)throws Exception{
        check(mode().equals("seed")||mode().equals("verify"),"set -D"+PROPERTY+"=seed or verify in the main-owned native lane");var level=h.getLevel();Path world=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();check(world.getFileName().toString().equals("academy-classic-misc-restart-world"),"requires dedicated isolated academy-classic-misc-restart-world");check(!h.absolutePos(BlockPos.ZERO).closerThan(CELL,256),"remote probe outside GameTest reset area");Path marker=world.resolve("classic-misc-seed.nbt"),region=world.resolve("region/r.4.4.mca");
        if(mode().equals("seed")){
            check(!Files.exists(marker)&&!Files.exists(region),"seed refuses existing native proof/region");var chunk=level.getChunk(CHUNK.x,CHUNK.z);check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),"remote full chunk is naturally nonticking");check(level.isEmptyBlock(CELL),"seed refuses occupied probe cell");level.setBlockAndUpdate(CELL,ClassicCatEngines.BLOCK.get().defaultBlockState());var tile=(ClassicCatEngineBlockEntity)level.getBlockEntity(CELL);tile.buffer().load(123.625);check(tile.saveWithFullMetadata(level.registryAccess()).equals(expected(level)),"exact native finite seed payload");var proof=new CompoundTag();proof.putUUID("boot",BOOT);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",world.toString());proof.put("expected",expected(level));NbtIo.writeCompressed(proof,marker);System.out.println("ACADEMY_MISC_RESTART seed prepared; native Anvil must persist through graceful seed-JVM shutdown");h.succeed();return;
        }
        var proof=NbtIo.readCompressed(marker,NbtAccounter.create(4*1024*1024));check(!proof.getUUID("boot").equals(BOOT),"verification requires separate cold JVM");check(proof.getString("world").equals(world.toString())&&proof.getCompound("expected").equals(expected(level)),"exact same isolated world and declared finite seed");var disk=readNativeAnvil(region);check(disk.getInt("xPos")==CHUNK.x&&disk.getInt("zPos")==CHUNK.z,"actual persisted chunk coordinates");var entities=disk.getList("block_entities",10);int matches=0;for(int i=0;i<entities.size();i++){var tag=entities.getCompound(i);if(tag.getInt("x")==CELL.getX()&&tag.getInt("y")==CELL.getY()&&tag.getInt("z")==CELL.getZ()){var diskExpected=expected(level);diskExpected.putBoolean("keepPacked",false);check(tag.equals(diskExpected),"full native Anvil payload including vanilla keepPacked=false wrapper equals independent finite expectation");matches++;}}check(matches==1,"exactly one actual persisted CatBE");var chunk=level.getChunk(CHUNK.x,CHUNK.z);check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),"cold chunk remains naturally nonticking");check(level.getBlockState(CELL).is(ClassicCatEngines.BLOCK.get()),"genuine cold native blockstate");var loaded=(ClassicCatEngineBlockEntity)level.getBlockEntity(CELL);check(loaded!=null&&loaded.available()&&loaded.saveWithFullMetadata(level.registryAccess()).equals(expected(level)),"native cold loader retains complete finiteBE");equal(123.625,loaded.getEnergy(),"actual fractional generatorIF");equal(0,loaded.generation(),"source transient generation starts0 after cold load");check(level.getCapability(ClassicSolarGenerators.IMAG_FLUX,CELL,Direction.UP)==loaded,"cold native generatorcapability reattaches actualBE");var verified=proof.copy();verified.putUUID("verify_boot",BOOT);verified.putLong("verify_pid",ProcessHandle.current().pid());verified.putBoolean("verified",true);NbtIo.writeCompressed(verified,world.resolve("classic-misc-verified.nbt"));System.out.println("ACADEMY_MISC_RESTART native Anvil and separate-JVM cold load passed; seed_pid="+proof.getLong("pid")+" verify_pid="+ProcessHandle.current().pid());h.succeed();
    }
}
