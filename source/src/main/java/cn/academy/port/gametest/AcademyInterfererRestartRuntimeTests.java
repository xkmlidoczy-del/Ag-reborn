/* Opt-in native Anvil/new-JVM Ability Interferer fixture. GPLv3. See NOTICE. */
package cn.academy.port.gametest;

import cn.academy.port.interferer.*;
import cn.academy.port.machine.MachineDevelopers;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Separate enabled namespace. Main must stop seed JVM gracefully and reuse its isolated world. */
@GameTestHolder("academy_interferer_restart")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class AcademyInterfererRestartRuntimeTests {
    private static final UUID BOOT=UUID.randomUUID();
    private static final ChunkPos CHUNK=new ChunkPos(96,96);
    private static final BlockPos CELL=new BlockPos(1540,80,1540);
    private static final String PROPERTY="academy.interferer.restart";
    private AcademyInterfererRestartRuntimeTests(){}
    private static String mode(){return System.getProperty(PROPERTY,"");}
    @SubscribeEvent public static void template(LevelEvent.Load event){
        if(mode().isEmpty()||!(event.getLevel() instanceof ServerLevel level))return;
        var tag=new CompoundTag();var size=new ListTag();for(int i=0;i<3;i++)size.add(IntTag.valueOf(4));tag.put("size",size);var blocks=new ListTag();var block=new CompoundTag();var position=new ListTag();for(int i=0;i<3;i++)position.add(IntTag.valueOf(0));block.put("pos",position);block.putInt("state",0);blocks.add(block);tag.put("blocks",blocks);var palette=new ListTag();var air=new CompoundTag();air.putString("Name","minecraft:air");palette.add(air);tag.put("palette",palette);tag.put("entities",new ListTag());level.getStructureManager().getOrCreate(ResourceLocation.parse("academy_interferer_restart:interferer_restart_empty")).load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),tag);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void equal(double expected,double actual,String message){check(Double.isFinite(actual)&&Math.abs(expected-actual)<1e-7,message+": "+actual);}
    private static CompoundTag expected(ServerLevel level){var tag=new CompoundTag();tag.putString("id","academy:ability_interferer");tag.putInt("x",CELL.getX());tag.putInt("y",CELL.getY());tag.putInt("z",CELL.getZ());tag.putBoolean("enabled_",true);tag.putFloat("range_",88.125f);var names=new CompoundTag();names.putInt("size",3);names.putString("0","Alpha");names.putString("1","InterfererSeed");names.putString("2","zeta");tag.put("whitelist_",names);return tag;}
    private static CompoundTag readNativeAnvil(Path region)throws Exception{
        check(Files.isRegularFile(region)&&Files.size(region)>=8192,"native persisted Interferer region exists before chunk loading");
        try(var file=new RandomAccessFile(region.toFile(),"r")){
            file.seek(((CHUNK.x&31)+(CHUNK.z&31)*32)*4L);int location=file.readInt();int sector=location>>>8,count=location&255;check(sector>=2&&count>0&&(sector+(long)count)*4096<=file.length(),"bounded actual Anvil location");file.seek(sector*4096L);int length=file.readInt(),compression=file.readUnsignedByte();check(length>1&&length<=count*4096-4&&length<4*1024*1024&&compression==2,"bounded native inline zlib chunk required");byte[] bytes=new byte[length-1];file.readFully(bytes);try(var stream=new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))){return NbtIo.read(stream,NbtAccounter.create(4*1024*1024));}
        }
    }
    @GameTest(template="interferer_restart_empty",templateNamespace="academy_interferer_restart",batch="academy_interferer_restart",timeoutTicks=20)
    public static void interferer_native_anvil_separate_jvm_seed_or_verify(GameTestHelper h)throws Exception{
        check(mode().equals("seed")||mode().equals("verify"),"set -D"+PROPERTY+"=seed or verify in the main-owned native lane");var level=h.getLevel();Path world=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();check(world.getFileName().toString().equals("academy-interferer-restart-world"),"requires dedicated isolated academy-interferer-restart-world");check(!h.absolutePos(BlockPos.ZERO).closerThan(CELL,256),"remote probe outside GameTest reset area");Path marker=world.resolve("interferer-seed.nbt"),region=world.resolve("region/r.3.3.mca");
        if(mode().equals("seed")){
            check(!Files.exists(marker)&&!Files.exists(region),"seed refuses existing native proof/region");var chunk=level.getChunk(CHUNK.x,CHUNK.z);check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),"remote full chunk is naturally nonticking");check(level.isEmptyBlock(CELL),"seed refuses occupied probe cell");level.setBlockAndUpdate(CELL,ClassicAbilityInterferers.BLOCK.get().defaultBlockState());var tile=(ClassicAbilityInterfererBlockEntity)level.getBlockEntity(CELL);var placer=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"InterfererSeed"));tile.setPlacer(placer);tile.setEnabled(true);tile.setRange(88.125);tile.setWhitelist(java.util.List.of("zeta","InterfererSeed","Alpha"));check(tile.placer().equals("InterfererSeed"),"source transient creator exists before genuine disk save");check(tile.saveWithFullMetadata(level.registryAccess()).equals(expected(level)),"exact native finite seed payload");var proof=new CompoundTag();proof.putUUID("boot",BOOT);proof.putLong("pid",ProcessHandle.current().pid());proof.putString("world",world.toString());proof.put("expected",expected(level));NbtIo.writeCompressed(proof,marker);System.out.println("ACADEMY_INTERFERER_RESTART seed prepared; native Anvil must persist through graceful seed-JVM shutdown");h.succeed();return;
        }
        var proof=NbtIo.readCompressed(marker,NbtAccounter.create(4*1024*1024));check(!proof.getUUID("boot").equals(BOOT),"verification requires separate cold JVM");check(proof.getString("world").equals(world.toString())&&proof.getCompound("expected").equals(expected(level)),"exact same isolated world and declared finite seed");var disk=readNativeAnvil(region);check(disk.getInt("xPos")==CHUNK.x&&disk.getInt("zPos")==CHUNK.z,"actual persisted chunk coordinates");var entities=disk.getList("block_entities",10);int matches=0;for(int i=0;i<entities.size();i++){var tag=entities.getCompound(i);if(tag.getInt("x")==CELL.getX()&&tag.getInt("y")==CELL.getY()&&tag.getInt("z")==CELL.getZ()){var diskExpected=expected(level);diskExpected.putBoolean("keepPacked",false);check(tag.equals(diskExpected),"full native Anvil payload including vanilla keepPacked=false wrapper equals independent finite expectation");matches++;}}check(matches==1,"exactly one actual persisted interfererBE");var chunk=level.getChunk(CHUNK.x,CHUNK.z);check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),"cold chunk remains naturally nonticking");check(level.getBlockState(CELL).is(ClassicAbilityInterferers.BLOCK.get()),"genuine cold native blockstate");var loaded=(ClassicAbilityInterfererBlockEntity)level.getBlockEntity(CELL);check(loaded!=null&&loaded.available()&&loaded.saveWithFullMetadata(level.registryAccess()).equals(expected(level)),"native cold loader retains complete source config");equal(88.125,loaded.range(),"source exact float range retained");check(loaded.enabled()&&loaded.whitelist().equals(new java.util.TreeSet<>(java.util.List.of("Alpha","InterfererSeed","zeta"))),"actual sorted source fields retained");check(loaded.placer()==null,"source intentionally does not persist placer even though whitelist retains their name");check(loaded.scanRemaining()==10&&loaded.syncRemaining()==20,"fresh JVM transient source clocks reset");check(level.getCapability(ClassicSolarGenerators.IMAG_FLUX,CELL,Direction.UP)==null&&level.getCapability(MachineDevelopers.IMAG_FLUX,CELL,Direction.UP)==null,"cold source tile has neither IF role");var verified=proof.copy();verified.putUUID("verify_boot",BOOT);verified.putLong("verify_pid",ProcessHandle.current().pid());verified.putBoolean("verified",true);NbtIo.writeCompressed(verified,world.resolve("interferer-verified.nbt"));System.out.println("ACADEMY_INTERFERER_RESTART native Anvil and separate-JVM cold load passed; seed_pid="+proof.getLong("pid")+" verify_pid="+ProcessHandle.current().pid());h.succeed();
    }
}
