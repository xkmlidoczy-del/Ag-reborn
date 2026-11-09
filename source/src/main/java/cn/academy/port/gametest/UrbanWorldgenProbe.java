package cn.academy.port.gametest;

import cn.academy.port.story.UrbanStoryClues;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated normal-server worldgen probe, inert unless the explicit test property is set. */
@EventBusSubscriber(modid="academy")
public final class UrbanWorldgenProbe {
    private static final String PROPERTY="academy.urban.worldgenProbe";
    private static final String[] IDS={"urban_campus","urban_station","urban_facility"};
    private static final Map<String,Set<String>> ROLES=Map.of(
            "urban_campus",Set.of("broadcast_guide"),
            "urban_station",Set.of("injured_student","student_witness"),
            "urban_facility",Set.of("protocol_student"));
    private static final int REGIONS=2, MAX_TICKS=2400, MAX_BOX_CHUNKS=16;
    private static Probe active;
    private static boolean started;
    private UrbanWorldgenProbe() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        String phase=System.getProperty(PROPERTY,"");
        if (!phase.equals("discover") && !phase.equals("verify")) return;
        if (!started) { started=true; active=new Probe(event.getServer(),phase); }
        if (active==null || active.server!=event.getServer()) return;
        try { active.tick(); }
        catch (Throwable failure) { active.finish("error", failure.toString()); }
    }

    private static final class Probe {
        final MinecraftServer server; final ServerLevel level; final String phase; final Path directory;
        final JsonObject result=new JsonObject(), entries=new JsonObject(), diagnostics=new JsonObject();
        final Set<ChunkPos> addedTickets=new HashSet<>();
        final Map<String,JsonObject> expected=new HashMap<>();
        int ticks, index, region; JsonArray candidateDiagnostics=new JsonArray(); StructureStart pending; Structure pendingType; String pendingId;
        long pendingSince;
        boolean finished;
        Probe(MinecraftServer server,String phase) {
            this.server=server;this.level=server.overworld();this.phase=phase;this.directory=server.getServerDirectory();
            result.addProperty("phase",phase);result.addProperty("seed",level.getSeed());result.add("structures",entries);result.add("diagnostics",diagnostics);
        }
        void tick() throws Exception {
            if (finished) return;
            if (++ticks>MAX_TICKS) { finish("inconclusive","tick limit exceeded"); return; }
            if (ticks==1) {
                if (!level.structureManager().shouldGenerateStructures()) { finish("error","world has structure generation disabled");return; }
                if (phase.equals("verify")) {
                    Path file=directory.resolve("urban-worldgen-discover.json");
                    if (!Files.isRegularFile(file)) { finish("error","discover result is missing");return; }
                    JsonObject earlier=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    if (!earlier.has("status") || !earlier.get("status").getAsString().equals("pass")
                            || earlier.get("seed").getAsLong()!=level.getSeed()) { finish("error","discover result or seed mismatch");return; }
                    JsonObject structures=earlier.getAsJsonObject("structures");
                    for(String id:IDS) expected.put(id,structures.getAsJsonObject(id));
                }
            }
            if (pending!=null) { inspectPending();return; }
            if (index==IDS.length) { finish("pass","all three naturally generated structures and NPCs verified");return; }
            String id=IDS[index];var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Structure type=registry.get(ResourceLocation.fromNamespaceAndPath("academy",id));
            var set=level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.fromNamespaceAndPath("academy",id));
            if (type==null || set==null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)
                    || set.structures().stream().noneMatch(entry->entry.structure().value()==type)) {
                finish("error","missing or unsupported registered structure set: "+id);return;
            }
            if (region==0 && phase.equals("discover")) {
                var state=level.getChunkSource().getGeneratorState();
                JsonObject detail=new JsonObject();
                detail.addProperty("inGeneratorPossibleSets",state.possibleStructureSets().stream().anyMatch(holder->holder.value()==set));
                detail.addProperty("structureBiomeCount",type.biomes().size());
                detail.addProperty("generatorBiomeOverlap",type.biomes().stream().filter(level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()::contains).count());
                detail.addProperty("templatePresent",level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath("academy","urban/"+id.substring("urban_".length()))).isPresent());
                detail.add("candidates",candidateDiagnostics);diagnostics.add(id,detail);
            }
            ChunkPos pos;
            if (phase.equals("verify")) {
                JsonObject old=expected.get(id);if(old==null){finish("error","missing discover entry: "+id);return;}
                pos=new ChunkPos(old.get("chunkX").getAsInt(),old.get("chunkZ").getAsInt());
            } else {
                int width=REGIONS*2+1;
                if (region>=width*width) {finish("inconclusive","candidate region limit reached for "+id);return;}
                int rx=region%width-REGIONS, rz=region/width-REGIONS;region++;
                pos=placement.getPotentialStructureChunk(level.getChunkSource().getGeneratorState().getLevelSeed(),rx*placement.spacing(),rz*placement.spacing());
                if (!placement.isStructureChunk(level.getChunkSource().getGeneratorState(),pos.x,pos.z)) {
                    var record=new JsonObject();record.addProperty("chunkX",pos.x);record.addProperty("chunkZ",pos.z);
                    record.addProperty("placementAccepted",false);candidateDiagnostics.add(record);return;
                }
            }
            var chunk=level.getChunkSource().getChunk(pos.x,pos.z,ChunkStatus.STRUCTURE_STARTS,true);
            StructureStart start=chunk==null?null:chunk.getStartForStructure(type);
            if(phase.equals("discover")) {
                var record=new JsonObject();record.addProperty("chunkX",pos.x);record.addProperty("chunkZ",pos.z);
                record.addProperty("placementAccepted",true);
                record.addProperty("chunkStatus",chunk==null?"missing":chunk.getHighestGeneratedStatus().toString());
                record.addProperty("storedValidStart",start!=null && start.isValid());
                if(chunk!=null && type instanceof JigsawStructure jigsaw) {
                    var generator=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
                    var context=new Structure.GenerationContext(level.registryAccess(),generator,generator.getBiomeSource(),random,
                            level.getStructureManager(),level.getChunkSource().getGeneratorState().getLevelSeed(),pos,chunk,type.biomes()::contains);
                    var stub=jigsaw.findGenerationPoint(context);record.addProperty("jigsawPoint",stub.isPresent());
                    if(stub.isPresent()) {
                        var point=stub.get().position();
                        var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(point.getX()),QuartPos.fromBlock(point.getY()),
                                QuartPos.fromBlock(point.getZ()),random.sampler());
                        record.addProperty("startX",point.getX());record.addProperty("startY",point.getY());record.addProperty("startZ",point.getZ());
                        record.addProperty("biome",biome.unwrapKey().map(key->key.location().toString()).orElse("unregistered"));
                        record.addProperty("biomeAllowed",type.biomes().contains(biome));
                    }
                }
                candidateDiagnostics.add(record);
            }
            if(start==null || !start.isValid()) {
                if(phase.equals("verify"))finish("fail","previous structure start missing: "+id);
                return;
            }
            pending=start;pendingType=type;pendingId=id;pendingSince=ticks;
            var box=start.getBoundingBox();var chunks=box.intersectingChunks().toList();
            if(chunks.isEmpty() || chunks.size()>MAX_BOX_CHUNKS){finish("error","structure bounds exceed probe budget: "+id);return;}
            for(ChunkPos c:chunks) {
                if(!level.getForcedChunks().contains(c.toLong())){level.setChunkForced(c.x,c.z,true);addedTickets.add(c);}
                level.getChunkSource().getChunk(c.x,c.z,ChunkStatus.FULL,true);
            }
        }
        void inspectPending() {
            BoundingBox box=pending.getBoundingBox(); List<ChunkPos> chunks=box.intersectingChunks().toList();
            if(ticks-pendingSince<5)return;
            if(chunks.stream().anyMatch(c->!level.areEntitiesLoaded(c.toLong()))) {
                if(ticks-pendingSince>200)finish("inconclusive","entity chunks did not load: "+pendingId);
                return;
            }
            Map<String,Villager> found=new HashMap<>();
            var area=new AABB(box.minX(),box.minY(),box.minZ(),box.maxX()+1,box.maxY()+1,box.maxZ()+1);
            for(Villager villager:level.getEntitiesOfClass(Villager.class,area)) {
                String role=villager.getPersistentData().getString(UrbanStoryClues.WITNESS_KEY);
                if(!ROLES.get(pendingId).contains(role))continue;
                if(found.putIfAbsent(role,villager)!=null) {finish("fail","duplicate role "+role+" in "+pendingId);return;}
                if(!villager.isAlive() || villager.isRemoved() || !villager.isPersistenceRequired()
                        || !level.structureManager().getStructureWithPieceAt(villager.blockPosition(),pendingType).isValid()) {
                    finish("fail","invalid story villager "+role+" in "+pendingId);return;
                }
            }
            if(!found.keySet().equals(ROLES.get(pendingId))) {finish("fail","missing story NPCs in "+pendingId+": "+found.keySet());return;}
            int lecterns=0, levers=0, amethyst=0, books=0, traces=0, tables=0, gold=0;Set<String> colors=new HashSet<>();
            for(BlockPos pos:BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) {
                var state=level.getBlockState(pos);
                if(state.is(Blocks.LECTERN))lecterns++;
                if(state.is(Blocks.LEVER))levers++;
                if(state.is(Blocks.AMETHYST_BLOCK))amethyst++;
                if(state.is(Blocks.BOOKSHELF))books++;
                if(state.is(Blocks.CYAN_TERRACOTTA))traces++;
                if(state.is(Blocks.CARTOGRAPHY_TABLE))tables++;
                if(state.is(Blocks.GOLD_BLOCK))gold++;
                if(state.is(Blocks.MAGENTA_TERRACOTTA))colors.add("magenta");
                if(state.is(Blocks.RED_TERRACOTTA))colors.add("red");
                if(state.is(Blocks.YELLOW_TERRACOTTA))colors.add("yellow");
                if(state.is(Blocks.LIME_TERRACOTTA))colors.add("lime");
            }
            if(lecterns<1 || tables!=1 || gold!=1 || pendingId.equals("urban_station")&&(books<1||traces<1) || pendingId.equals("urban_facility") && (levers<4 || amethyst<1
                    || !colors.containsAll(Set.of("magenta","red","yellow","lime")))) {
                finish("fail","missing expected investigation blocks in "+pendingId);return;
            }
            JsonObject item=new JsonObject(),roles=new JsonObject();
            item.addProperty("chunkX",pending.getChunkPos().x);item.addProperty("chunkZ",pending.getChunkPos().z);
            JsonArray bounds=new JsonArray();for(int value:new int[]{box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()})bounds.add(value);
            item.add("bounds",bounds);item.addProperty("lecterns",lecterns);item.addProperty("levers",levers);item.addProperty("bookshelves",books);item.addProperty("paperTables",tables);item.addProperty("paperGoldSockets",gold);
            for(var entry:found.entrySet())roles.addProperty(entry.getKey(),entry.getValue().getUUID().toString());
            item.add("npc",roles);
            if(phase.equals("verify")) {
                JsonObject previous=expected.get(pendingId);
                if(!previous.getAsJsonArray("bounds").equals(bounds) || !previous.getAsJsonObject("npc").equals(roles)) {
                    finish("fail","structure bounds or NPC UUIDs changed on restart: "+pendingId);return;
                }
            }
            entries.add(pendingId,item);index++;region=0;candidateDiagnostics=new JsonArray();pending=null;pendingType=null;pendingId=null;
        }
        void finish(String status,String detail) {
            if(finished)return;finished=true;result.addProperty("status",status);result.addProperty("detail",detail);result.addProperty("ticks",ticks);
            for(ChunkPos pos:addedTickets)level.setChunkForced(pos.x,pos.z,false);
            try {
                server.saveEverything(true,true,true);
                Files.writeString(directory.resolve("urban-worldgen-"+phase+".json"),result.toString()+"\n",StandardCharsets.UTF_8);
                LogUtils.getLogger().info("Urban worldgen probe {}: {} ({})",phase,status,detail);
            } catch(Exception error) {LogUtils.getLogger().error("Urban worldgen probe output failed",error);}
            active=null;server.halt(false);
        }
    }
}
