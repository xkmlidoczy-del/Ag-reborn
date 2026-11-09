package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.fusion.ClassicFusorBlock;
import cn.academy.port.fusion.ClassicFusorBlockEntity;
import cn.academy.port.survival.ClassicMaterials;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import cn.academy.port.machine.MachineDeveloperBlock;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDeveloperItem;
import cn.academy.port.machine.MachineDeveloperSessions;
import cn.academy.port.machine.MachineDeveloperStructure;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.solar.ClassicSolarBlock;
import cn.academy.port.solar.ClassicSolarBlockEntity;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.solar.ClassicSolarRules;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

/** Opt-in native Anvil probe only. No fixture block/state is reconstructed in the verify JVM. */
final class AcademyEnergyDiskFixture {
    private static final ChunkPos CHUNK = new ChunkPos(64, 64);
    private static final BlockPos NORMAL = new BlockPos(1028, 80, 1028);
    private static final BlockPos ADVANCED = new BlockPos(1035, 80, 1028);
    private static final BlockPos SOLAR = new BlockPos(1028, 80, 1035);
    private static final BlockPos FUSOR = new BlockPos(1035, 80, 1035);
    private static final double FUSOR_IF=1999.625, FUSOR_UNIT_IF=324.375;
    private static final double NORMAL_IF = 12345.25, ADVANCED_IF = 123456.875;
    private static final double SOLAR_IF = 123.625, SOLAR_UNIT_IF = 456.375;
    private final GameTestHelper helper;
    private final Path regionFile;
    private volatile int saves, loads;
    private volatile CompoundTag loadedData;
    private boolean seeded;
    private ServerPlayer seededPlayer;
    private int rechargeTicks;
    private boolean fusionCompleted;

    AcademyEnergyDiskFixture(GameTestHelper helper, Path world) {
        this.helper = helper;
        regionFile = world.resolve("region/r.2.2.mca");
        check(!helper.absolutePos(BlockPos.ZERO).closerThan(NORMAL, 256),
                "Remote persisted chunk must be outside the randomized GameTest reset area");
    }

    static ItemStack playerUnit() { return unit(5432.125, "player-unit-seed-v2"); }

    private LevelChunk loadNativeChunk() {
        var chunk = helper.getLevel().getChunk(CHUNK.x, CHUNK.z);
        // getChunk adds a FULL (not BLOCK_TICKING) ticket. No player, forced-chunk
        // ticket or natural ticker is put in this remote fixture. This makes the
        // initial finite partial solar state independent of server startup ticks.
        check(!chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),
                "Remote persistence fixture must remain loaded but naturally non-ticking");
        return chunk;
    }

    void seed(ServerPlayer player) {
        check(!Files.exists(regionFile), "Seed requires a fresh native probe region; no existing state is overwritten");
        loadNativeChunk();
        var normal = place(player, MachineDevelopers.NORMAL_ITEM.get(), NORMAL, 0);
        normal.battery().injectEnergy(NORMAL_IF);
        normal.setItem(0, unit(765.625, "normal-unit-seed-v2"));
        normal.setItem(1, new ItemStack(Items.DIAMOND, 3));
        var advanced = place(player, MachineDevelopers.ADVANCED_ITEM.get(), ADVANCED, 90);
        advanced.battery().injectEnergy(ADVANCED_IF);
        advanced.setItem(0, unit(9876.375, "advanced-unit-seed-v2"));
        advanced.setItem(1, portable(2468.5, "advanced-portable-seed-v2"));
        var level = helper.getLevel();
        check(level.isEmptyBlock(SOLAR) && level.isEmptyBlock(SOLAR.above()), "Solar seed cells start empty");
        level.setBlockAndUpdate(SOLAR, solarState());
        level.setBlockAndUpdate(SOLAR.above(), Blocks.STONE.defaultBlockState());
        awaitNativeLight("seed-roof");
        var solar = solar();
        solar.buffer().load(SOLAR_IF);
        solar.setItem(0, unit(SOLAR_UNIT_IF, "solar-unit-seed-v2"));
        solar.setChanged();
        seedFusion();
        // Actual server-authenticated occupancy is transient even while origin
        // inventory and energy persist. Move only the admitted mock profile,
        // without advancing any world tick or adding a remote player ticket.
        Vec3 original = player.position(); float yaw = player.getYRot();
        player.moveTo(ADVANCED.getX() + .5, ADVANCED.getY(), ADVANCED.getZ() - 1.5, 0, 0);
        try {
            check(advanced.use(player) && player.getUUID().equals(advanced.user())
                    && MachineDeveloperSessions.activeToken(player).isPresent(), "Seed establishes a genuine transient machine user/session");
        } finally { player.moveTo(original.x, original.y, original.z, yaw, 0); }
        seeded = true;
        seededPlayer = player;
        checkLive(false);
        check(advanced.user().equals(player.getUUID()), "Seed occupancy remains until real production shutdown cleanup");
        System.out.println("ACADEMY_RESTART seed native energy/world fixture prepared; chunk=64,64");
    }

    private MachineDeveloperBlockEntity place(ServerPlayer player, MachineDeveloperItem item, BlockPos pos, float yaw) {
        var level = helper.getLevel();
        for (int part = 0; part < 8; part++)
            check(level.isEmptyBlock(pos.offset(MachineDeveloperBlock.offset(part, yaw == 0 ? Direction.NORTH : Direction.EAST))),
                    "All eight seed placement cells start empty");
        var stack = new ItemStack(item); player.setYRot(yaw);
        var placement = item.place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        check(placement.consumesAction() && stack.isEmpty(), "Native BlockItem places exactly eight cells and consumes one machine");
        return machine(pos);
    }

    void nativeSave(ChunkDataEvent.Save event) {
        if (event.getLevel() == helper.getLevel() && event.getChunk().getPos().equals(CHUNK)) saves++;
    }

    void nativeLoad(ChunkDataEvent.Load event) {
        if (event.getLevel() == helper.getLevel() && event.getChunk().getPos().equals(CHUNK)) {
            loads++;
            loadedData = event.getData().copy();
        }
    }

    void stopping(ServerPlayer player) {
        check(seeded && player == seededPlayer, "Owned native player reaches actual server stopping");
        checkLive(false);
    }

    void certifyClosedWorld(CompoundTag proof) {
        check(seeded && saves > 0, "Actual native ChunkDataEvent.Save must serialize the owned probe chunk");
        checkDisk(readNativeAnvil()); // ServerStoppedEvent follows world close/flush.
        check(MachineDeveloperSessions.activeToken(seededPlayer).isEmpty(), "Production logout/unload/stopped lifecycle releases seeded transient nonce/session");
        proof.putInt("energy_world_schema", 1);
        proof.putBoolean("energy_world_transient_session_cleaned", true);
        proof.putBoolean("energy_world_closed_disk_certified", true);
        proof.putInt("energy_world_native_save_events", saves);
        proof.put("expected_energy_world", expectedWorld(0));
        System.out.println("ACADEMY_RESTART seed actual closed-world Anvil energy save certified; chunk=64,64");
    }

    void verifyBeforeMutation(CompoundTag proof) {
        check(proof.getInt("energy_world_schema") == 1 && proof.getBoolean("energy_world_transient_session_cleaned") && proof.getBoolean("energy_world_closed_disk_certified")
                && proof.getInt("energy_world_native_save_events") > 0, "Seed certificate requires actual closed-world native energy save");
        check(proof.getCompound("expected_energy_world").equals(expectedWorld(0)), "World certificate matches independent known constants/components");
        checkDisk(readNativeAnvil());
        check(loads == 0, "Remote chunk must not already have loaded in the verify JVM");
        loadNativeChunk(); // ChunkSerializer.read -> native BlockEntity.loadStatic.
        check(loads == 1 && loadedData != null, "Verify observes exactly one native ChunkDataEvent.Load for persisted world data");
        checkDisk(loadedData);
        awaitNativeLight("verify-native-load");
        checkLive(true);
        var solar = solar();
        ClassicSolarBlockEntity.serverTick(helper.getLevel(), SOLAR, solar.getBlockState(), solar);
        rechargeTicks = 1;
        // Source work progress is deliberately transient. Run a whole new121-work-tick job,
        // rather than resuming the eleven work ticks performed before the old process stopped.
        var fusor=fusor();
        for(int tick=0;tick<130;tick++)ClassicFusorBlockEntity.serverTick(helper.getLevel(),FUSOR,fusor.getBlockState(),fusor);
        fusionCompleted=true;
        checkLive(true);
        System.out.println("ACADEMY_RESTART verify native Fusor cold-load, nonpersisted-progress reset and conserved complete job passed; consumed=1452IF liquid=3000mB");
        System.out.println("ACADEMY_RESTART verify native Anvil/new-JVM energy reload and finite solar recharge passed; chunk=64,64");
    }

    void verified(CompoundTag result) {
        check(loads == 1 && rechargeTicks == 1, "One native cold chunk load and one declared post-reload recharge tick");
        checkLive(true);
        result.putInt("energy_world_native_load_events", loads);
        result.putInt("energy_world_recharge_ticks", rechargeTicks);
        result.putBoolean("energy_world_verified", true);
        result.putBoolean("fusion_world_verified", fusionCompleted);
        result.put("verified_energy_world", expectedWorld(rechargeTicks));
    }

    void checkLive(boolean noUser) {
        loadNativeChunk();
        checkMachine(NORMAL, MachineDevelopers.NORMAL.get().defaultBlockState().setValue(MachineDeveloperBlock.FACING, Direction.NORTH), NORMAL_IF, noUser);
        checkMachine(ADVANCED, MachineDevelopers.ADVANCED.get().defaultBlockState().setValue(MachineDeveloperBlock.FACING, Direction.EAST), ADVANCED_IF, noUser);
        var solar = solar();
        check(solar.available(), "Native solar remains attached to the authoritative live tile: removed=" + solar.isRemoved()
                + " level=" + solar.getLevel() + " state=" + solar.getBlockState());
        check(solar.getBlockState().equals(solarState()), "Exact native solar WEST facing persists: " + solar.getBlockState());
        check(helper.getLevel().getBlockState(SOLAR.above()).is(Blocks.STONE), "Exact native stone solar roof persists: "
                + helper.getLevel().getBlockState(SOLAR.above()));
        check(solar.status() == ClassicSolarRules.Status.STOPPED, "Native roof must stop solar generation after real light completion: status="
                + solar.status() + " sky=" + helper.getLevel().getBrightness(LightLayer.SKY, SOLAR.above())
                + " canSeeSky=" + helper.getLevel().canSeeSky(SOLAR.above()) + " dayTime=" + helper.getLevel().getDayTime()
                + " raining=" + helper.getLevel().isRaining());
        equal(SOLAR_IF - 20 * rechargeTicks, solar.getEnergy(), "Exact finite fractional solar buffer");
        check(solar.getContainerSize() == 1 && solar.saveWithFullMetadata(helper.getLevel().registryAccess())
                .equals(expectedEntity(SOLAR, "academy:solar_gen", SOLAR_IF - 20 * rechargeTicks,
                        unit(SOLAR_UNIT_IF + 20 * rechargeTicks, "solar-unit-seed-v2"))), "Full native solar recharge-slot components and metadata");
        check(helper.getLevel().getCapability(ClassicSolarGenerators.IMAG_FLUX, SOLAR, Direction.UP) == solar,
                "Cold-loaded native solar generator capability resolves to the real finite tile");
        checkFusion();
    }

    /** Await the native asynchronous light task queue without advancing a gameplay/world tick. */
    private void awaitNativeLight(String phase) {
        var level = helper.getLevel(); var light = level.getChunkSource().getLightEngine();
        long gameTime = level.getGameTime(), dayTime = level.getDayTime();
        int beforeSky = level.getBrightness(LightLayer.SKY, SOLAR.above());
        var pending = light.waitForPendingTasks(CHUNK.x, CHUNK.z);
        long started = System.nanoTime();
        level.getServer().managedBlock(() -> {
            // Native checkBlock tasks are enqueued by an asynchronous priority
            // mailbox. Keep scheduling their normal update runner until this
            // same-chunk POST_UPDATE barrier is completed. Do not call the
            // unsupported synchronous runLightUpdates or tick any block entity.
            light.tryScheduleUpdate();
            return pending.isDone() || System.nanoTime() - started >= 10_000_000_000L;
        });
        check(pending.isDone(), "Native light POST_UPDATE barrier timed out during " + phase);
        pending.join();
        check(level.getGameTime() == gameTime && level.getDayTime() == dayTime,
                "Native lighting completion must not advance gameplay/world time");
        check(!level.getChunk(CHUNK.x, CHUNK.z).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING),
                "Native light completion must not turn the remote fixture into a naturally ticking chunk");
        System.out.println("ACADEMY_RESTART native solar light barrier phase=" + phase + " before_sky=" + beforeSky
                + " after_sky=" + level.getBrightness(LightLayer.SKY, SOLAR.above())
                + " can_see_sky=" + level.canSeeSky(SOLAR.above()) + " day_time=" + dayTime);
    }

    private void checkMachine(BlockPos pos, BlockState rootState, double energy, boolean noUser) {
        var root = machine(pos); var level = helper.getLevel();
        check(root.available() && MachineDeveloperStructure.complete(level, pos, rootState), "Cold-loaded eight-cell machine is complete and available");
        equal(energy, root.battery().getEnergy(), "Exact finite fractional tier battery");
        check(root.getContainerSize() == 2 && (!noUser || root.user() == null), "Two source slots persist and transient user never survives shutdown/reload");
        for (int part = 0; part < 8; part++) {
            var cell = pos.offset(MachineDeveloperBlock.offset(part, root.facing()));
            var entity = machine(cell);
            check(level.getBlockState(cell).equals(rootState.setValue(MachineDeveloperBlock.PART, part))
                    && entity.origin() == root, "Each exact persisted cardinal part redirects to the loaded origin");
            check(entity.saveWithFullMetadata(level.registryAccess()).equals(expectedMachineEntity(pos, root.facing(), part)),
                    "Full independently expected native tier energy/inventory NBT on all eight cells");
            check(level.getCapability(MachineDevelopers.IMAG_FLUX, cell, Direction.UP) == root.battery(), "Native IF capability redirects after cold load");
            var fe = level.getCapability(Capabilities.EnergyStorage.BLOCK, cell, Direction.NORTH);
            check(fe != null && fe.canReceive() && !fe.canExtract() && fe.getMaxEnergyStored() == (int) (root.developerType().energy * 4)
                    && fe.getEnergyStored() == (int) Math.floor(energy * 4) && fe.receiveEnergy(1, true) == 1,
                    "Cold-loaded tier finite FE adapter retains source capacity and non-mutating simulation");
        }
        equal(energy, root.battery().getEnergy(), "Repeated capability simulation never changes fractional IF");
        check(level.isEmptyBlock(pos.above(2)), "Source top-front cell remains deliberately unoccupied");
    }

    private CompoundTag expectedMachineEntity(BlockPos origin, Direction facing, int part) {
        var pos = origin.offset(MachineDeveloperBlock.offset(part, facing));
        if (part != 0) return expectedEntity(pos, "academy:developer", 0);
        return origin.equals(NORMAL)
                ? expectedEntity(pos, "academy:developer", NORMAL_IF, unit(765.625, "normal-unit-seed-v2"), new ItemStack(Items.DIAMOND, 3))
                : expectedEntity(pos, "academy:developer", ADVANCED_IF, unit(9876.375, "advanced-unit-seed-v2"), portable(2468.5, "advanced-portable-seed-v2"));
    }

    private CompoundTag expectedWorld(int ticks) {
        var result = new CompoundTag(); result.putInt("chunk_x", CHUNK.x); result.putInt("chunk_z", CHUNK.z);
        var entities = new ListTag();
        for (int part = 0; part < 8; part++) entities.add(expectedMachineEntity(NORMAL, Direction.NORTH, part));
        for (int part = 0; part < 8; part++) entities.add(expectedMachineEntity(ADVANCED, Direction.EAST, part));
        entities.add(expectedEntity(SOLAR, "academy:solar_gen", SOLAR_IF - 20 * ticks, unit(SOLAR_UNIT_IF + 20 * ticks, "solar-unit-seed-v2")));
        entities.add(expectedFusion(ticks>0));
        result.put("block_entities", entities);
        return result;
    }

    private CompoundTag expectedEntity(BlockPos pos, String id, double energy, ItemStack... inventory) {
        var tag = new CompoundTag(); tag.putString("id", id); tag.putInt("x", pos.getX()); tag.putInt("y", pos.getY()); tag.putInt("z", pos.getZ());
        tag.putDouble("energy", energy); var items = new ListTag();
        for (int slot = 0; slot < inventory.length; slot++) {
            if(inventory[slot].isEmpty())continue;
            var item = (CompoundTag) inventory[slot].save(helper.getLevel().registryAccess()); item.putByte("Slot", (byte) slot); items.add(item);
        }
        tag.put("Items", items);
        return tag;
    }

    private void checkDisk(CompoundTag chunk) {
        check(chunk.getInt("xPos") == CHUNK.x && chunk.getInt("zPos") == CHUNK.z && chunk.getString("Status").equals("minecraft:full"), "Correct native full chunk identity/status on disk");
        var entities = chunk.getList("block_entities", 10); var expected = expectedWorld(0).getList("block_entities", 10);
        check(entities.size() == 18 && expected.size() == 18, "Exactly eight normal/eight advanced/one solar/one Fusor native block entity");
        Set<CompoundTag> actual = new HashSet<>();
        for (int i = 0; i < entities.size(); i++) { var tag = entities.getCompound(i).copy(); check(tag.contains("keepPacked", 1) && !tag.getBoolean("keepPacked"), "Native loaded tiles are serialized rather than fixture-packed NBT"); tag.remove("keepPacked"); check(actual.add(tag), "No duplicated saved world fixture entity"); }
        for (int i = 0; i < expected.size(); i++) check(actual.remove(expected.getCompound(i)), "Anvil NBT equals full independently known finite energy/inventory components");
        check(actual.isEmpty(), "No unexpected Anvil fixture data");
        for (int part = 0; part < 8; part++) {
            checkDiskState(chunk, NORMAL.offset(MachineDeveloperBlock.offset(part, Direction.NORTH)), MachineDevelopers.NORMAL.get().defaultBlockState().setValue(MachineDeveloperBlock.FACING, Direction.NORTH).setValue(MachineDeveloperBlock.PART, part));
            checkDiskState(chunk, ADVANCED.offset(MachineDeveloperBlock.offset(part, Direction.EAST)), MachineDevelopers.ADVANCED.get().defaultBlockState().setValue(MachineDeveloperBlock.FACING, Direction.EAST).setValue(MachineDeveloperBlock.PART, part));
        }
        checkDiskState(chunk, SOLAR, solarState()); checkDiskState(chunk, SOLAR.above(), Blocks.STONE.defaultBlockState());
        checkDiskState(chunk,FUSOR,fusorState(true));
    }

    private void checkDiskState(CompoundTag chunk, BlockPos pos, BlockState expected) {
        var sections = chunk.getList("sections", 10);
        for (int i = 0; i < sections.size(); i++) {
            var section = sections.getCompound(i); if (section.getByte("Y") != (pos.getY() >> 4)) continue;
            var states = section.getCompound("block_states"); var palette = states.getList("palette", 10);
            check(!palette.isEmpty(), "Native persisted block palette exists");
            int index = 0;
            if (palette.size() > 1) {
                int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
                int perWord = 64 / bits;
                int cell = (pos.getY() & 15) * 256 + (pos.getZ() & 15) * 16 + (pos.getX() & 15);
                long[] data = states.getLongArray("data"); check(cell / perWord < data.length, "Native palette data contains fixture cell");
                index = (int) ((data[cell / perWord] >>> ((cell % perWord) * bits)) & ((1L << bits) - 1));
            }
            check(index < palette.size() && NbtUtils.readBlockState(helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK), palette.getCompound(index)).equals(expected), "Exact native Anvil blockstate/facing/part at " + pos);
            return;
        }
        check(false, "Native Anvil section missing at " + pos);
    }

    /** Read-only bounded Anvil sector reader; never opens native RegionFile in create/write mode. */
    private CompoundTag readNativeAnvil() {
        try {
            check(Files.isRegularFile(regionFile) && Files.size(regionFile) >= 8192, "Closed-world native region exists");
            try (var file = new RandomAccessFile(regionFile.toFile(), "r")) {
                file.seek(((CHUNK.x & 31) + (CHUNK.z & 31) * 32) * 4L);
                int location = file.readInt(); int sector = location >>> 8, count = location & 255;
                check(sector >= 2 && count > 0 && (sector + (long) count) * 4096 <= file.length(), "Native chunk sector location is bounded and allocated");
                file.seek(sector * 4096L); int length = file.readInt(); int compression = file.readUnsignedByte();
                check(length > 1 && length <= count * 4096 - 4 && length < 4 * 1024 * 1024 && compression == 2, "Bounded inline native zlib chunk data is required");
                byte[] bytes = new byte[length - 1]; file.readFully(bytes);
                try (var input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))) {
                    return NbtIo.read(input, NbtAccounter.create(4 * 1024 * 1024));
                }
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** Declared persistence seeds, deliberately distinct from natural resource acquisition. */
    private void seedFusion(){
        var level=helper.getLevel();check(level.isEmptyBlock(FUSOR),"Fusor seed cell starts empty");
        level.setBlockAndUpdate(FUSOR,fusorState(false));var tile=fusor();
        tile.injectEnergy(2000);check(tile.fluidHandler().fill(new FluidStack(ClassicFusion.PHASE_SOURCE.get(),3500),IFluidHandler.FluidAction.EXECUTE)==3500,"Native Fusor accepts declared3500mB persistence seed");
        tile.setItem(0,new ItemStack(ClassicMaterials.CRYSTAL_LOW.get(),2));
        tile.setItem(3,unit(456.375,"fusor-unit-seed-v3"));tile.setItem(4,new ItemStack(ClassicFusion.MATTER_UNIT.get(),3));
        for(int tick=0;tick<20;tick++)ClassicFusorBlockEntity.serverTick(level,FUSOR,tile.getBlockState(),tile);
        tile.pullEnergy(.375);tile.setChanged();
        double expectedProgress=0;for(int tick=0;tick<11;tick++)expectedProgress+=1.0/120;
        equal(expectedProgress,tile.workProgress(),"Seed executed exactly eleven original work ticks");
        check(tile.isWorking(),"Seed has genuine unfinished source work before shutdown");
        System.out.println("ACADEMY_RESTART seed native Fusor persistence fixture prepared; progress_ticks=11 energy=1999.625 unit=324.375 phase=3500mB");
    }
    private void checkFusion(){
        var level=helper.getLevel();var tile=fusor();
        check(tile.available()&&tile.getContainerSize()==5,"Native cold Fusor and all five source raw slots");
        check(tile.getBlockState().equals(fusorState(!fusionCompleted)),"Native persisted facing/work texture state");
        check(tile.saveWithFullMetadata(level.registryAccess()).equals(expectedFusion(fusionCompleted)),"Exact independently known Fusor IF, phase and native item components");
        equal(fusionCompleted?872:FUSOR_IF,tile.getEnergy(),"Finite fractional Fusor buffer and conserved job debit");
        check(tile.liquid()==(fusionCompleted?500:3500),"Exact source phase tank after native cold load/job");
        if(loads==0)check(tile.isWorking()&&tile.workProgress()>0,"Seed partial progress exists only in old process");
        else check(!tile.isWorking()&&tile.workProgress()==0,"Cold process resets source transient work; completed new job remains idle");
        check(level.getCapability(MachineDevelopers.IMAG_FLUX,FUSOR,Direction.UP)==tile,"Cold Fusor genuine finite IF receiver");
        check(level.getCapability(Capabilities.FluidHandler.BLOCK,FUSOR,Direction.WEST)==tile.fluidHandler(),"Cold Fusor genuine source phase tank capability");
    }
    private CompoundTag expectedFusion(boolean completed){
        var tag=expectedEntity(FUSOR,"academy:imag_fusor",completed?872:FUSOR_IF,
                new ItemStack(ClassicMaterials.CRYSTAL_LOW.get(),completed?1:2),
                completed?new ItemStack(ClassicMaterials.CRYSTAL_NORMAL.get()):ItemStack.EMPTY,ItemStack.EMPTY,
                unit(completed?0:FUSOR_UNIT_IF,"fusor-unit-seed-v3"),new ItemStack(ClassicFusion.MATTER_UNIT.get(),3));
        tag.put("Fluid",new FluidStack(ClassicFusion.PHASE_SOURCE.get(),completed?500:3500).save(helper.getLevel().registryAccess()));return tag;
    }
    private ClassicFusorBlockEntity fusor(){var tile=helper.getLevel().getBlockEntity(FUSOR);check(tile instanceof ClassicFusorBlockEntity,"Native Fusor exists at owned persistence cell");return (ClassicFusorBlockEntity)tile;}
    private static BlockState fusorState(boolean working){return ClassicFusion.FUSOR_BLOCK.get().defaultBlockState().setValue(ClassicFusorBlock.FACING,Direction.EAST).setValue(ClassicFusorBlock.WORKING,working);}

    private MachineDeveloperBlockEntity machine(BlockPos pos) {
        var tile = helper.getLevel().getBlockEntity(pos); check(tile instanceof MachineDeveloperBlockEntity, "Native machine block entity exists at " + pos); return (MachineDeveloperBlockEntity) tile;
    }
    private ClassicSolarBlockEntity solar() {
        var tile = helper.getLevel().getBlockEntity(SOLAR); check(tile instanceof ClassicSolarBlockEntity, "Native solar block entity exists"); return (ClassicSolarBlockEntity) tile;
    }
    private static BlockState solarState() { return ClassicSolarGenerators.BLOCK.get().defaultBlockState().setValue(ClassicSolarBlock.FACING, Direction.WEST); }
    private static ItemStack unit(double energy, String sentinel) {
        var stack = new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get()); ClassicEnergyItemHelper.setEnergy(stack, energy);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("restart_fixture", sentinel)); return stack;
    }
    private static ItemStack portable(double energy, String sentinel) {
        var stack = new ItemStack(AcademyCraft.DEVELOPER.get()); new DeveloperItemEnergy(stack, DeveloperType.PORTABLE).energy(energy);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("restart_fixture", sentinel)); return stack;
    }
    private static void equal(double expected, double actual, String message) { check(Double.isFinite(actual) && actual == expected, message + ": " + actual + " != " + expected); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
