package cn.academy.port.gametest;

import cn.academy.port.city.*;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.*;

/** Enumerates the actual city's generated lots, not just the authored tower library. */
@GameTestHolder("academy_city_interiors")
@PrefixGameTestTemplate(false)
public final class CityInteriorCoverageRuntimeTests {
    private static final String T="interior_empty";
    private static final long SEED=20261103L;
    @GameTest(template=T,batch="interior_coverage",timeoutTicks=200)public static void city_row_north(GameTestHelper h){row(h,0);}
    @GameTest(template=T,batch="interior_coverage",timeoutTicks=200)public static void city_row_north_centre(GameTestHelper h){row(h,1);}
    @GameTest(template=T,batch="interior_coverage",timeoutTicks=200)public static void city_row_centre(GameTestHelper h){row(h,2);}
    @GameTest(template=T,batch="interior_coverage",timeoutTicks=200)public static void city_row_south_centre(GameTestHelper h){row(h,3);}
    @GameTest(template=T,batch="interior_coverage",timeoutTicks=200)public static void city_row_south(GameTestHelper h){row(h,4);}
    private static void row(GameTestHelper h,int row){
        var planner=new CityInteriors.Planner(SEED);planner.policy(CityInteriorVersions.Policy.fresh());
        var seen=new HashSet<String>();var records=new ArrayList<Map<String,Object>>();
        for(int cell=0;cell<5;cell++)for(int bz=0;bz<12;bz++)for(int bx=0;bx<12;bx++){
            int x=-2000+cell*800+36+bx*64,z=-2000+row*800+36+bz*64;
            var group=planner.at(x,z);if(group==null||!seen.add(group.id()))continue;
            verify(h,group);records.add(record(group));
        }
        write("city-interior-coverage-row-"+row+".json",records);
        System.out.println("CITY_INTERIOR_ROW row="+row+" actualBuildings="+records.size()+" floors="+records.stream().mapToInt(r->(int)r.get("floors")).sum());h.succeed();
    }
    private static Map<String,Object> record(CityInteriors.Group g){
        var out=new LinkedHashMap<String,Object>();out.put("id",g.id());out.put("version",1);out.put("bounds",g.reservation());out.put("parts",g.parts().size());out.put("floors",g.surfaces().size());out.put("rooms",g.parts().stream().mapToInt(p->p.rooms.size()).sum());out.put("lifts",g.parts().stream().filter(p->p.lift!=null).count());out.put("themes",g.parts().stream().map(p->p.theme).distinct().toList());out.put("structuralOverlayValidated",true);return out;
    }
    @GameTest(template=T,batch="interior_coverage",timeoutTicks=200)public static void curved_exhibition_observatory_and_hangar_public_routes(GameTestHelper h){
        var biome=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(Biomes.PLAINS);var generator=new CityGreyboxGenerator(new FixedBiomeSource(biome),SEED);generator.interiorPolicy(CityInteriorVersions.Policy.fresh());var height=LevelHeightAccessor.create(-64,1024);var random=h.getLevel().getChunkSource().randomState();
        int[][] paths={{-225,-1024,1,-225,-1005,-224,-1013},{-1755,-825,93,-1753,-819,-1754,-825},{1410,-1750,0,1479,-1755,1395,-1768}};
        for(int[] a:paths){var group=generator.interiorAt(a[0],a[1]);h.assertTrue(group!=null&&group.parts().isEmpty()&&group.surfaces().size()==1,"explicit public surface preserves special landmark volume");var cache=new HashMap<Long,net.minecraft.world.level.NoiseColumn>();CityInteriorRuntimeTests.BlocksAt source=(x,y,z)->cache.computeIfAbsent(ChunkPos.asLong(x,z),key->generator.getBaseColumn(x,z,height,random)).getBlock(y);var start=new BlockPos(a[3],a[2],a[4]);var end=new BlockPos(a[5],a[2],a[6]);
            h.assertTrue(CityInteriorRuntimeTests.roomPath(h,source,start,end,new CityInteriorPlan.Rect(group.reservation().x0()-1,group.reservation().z0()-1,group.reservation().x1()+1,group.reservation().z1()+1)),group.id()+" public entrance reaches work/reading area with three-block clearance");
        }h.succeed();
    }
    private static void write(String file,Object data){try{Files.writeString(Path.of(file),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(data)+"\n");}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    private static void verify(GameTestHelper h,CityInteriors.Group g){
        CityInteriorRuntimeTests.BlocksAt source=(x,y,z)->{var s=g.block(x,y,z);return s==null?Blocks.AIR.defaultBlockState():s;};
        for(var p:g.parts()){
            h.assertTrue(!p.rooms.isEmpty(),p.id+" has rooms");if(p.floors.size()==1)continue;
            for(int f=0;f<p.floors.size();f++){
                int y=p.floors.get(f).y();var lobby=new BlockPos(p.corridorX,y,p.coreZ-1);
                h.assertTrue(CityInteriorRuntimeTests.stand(h,source,lobby.getX(),y,lobby.getZ()),p.id+" three-clear lobby F"+(f+1));
                for(var room:p.rooms)if(room.floor()==f){int rx=room.rect().x0()==p.corridorX+2?room.rect().x0()+1:room.rect().x1()-1;var goal=new BlockPos(rx,y,(room.rect().z0()+room.rect().z1())/2);
                    h.assertTrue(CityInteriorRuntimeTests.roomPath(h,source,lobby,goal,p.floors.get(f).rect()),p.id+" reachable "+room.use()+" F"+(f+1));CityInteriorRuntimeTests.roomPurpose(h,source,p,room);}
                if(f+1<p.floors.size())h.assertTrue(CityInteriorRuntimeTests.path(h,source,new BlockPos(p.coreX,y,p.coreZ-1),new BlockPos(p.coreX+3,p.floors.get(f+1).y(),p.coreZ-1),p),p.id+" staircase F"+(f+1));
            }
            var f=p.floors.getFirst();var r=f.rect();var entry=new BlockPos((r.x0()+r.x1())/2,f.y(),p.entrance==Direction.NORTH?r.z0()+2:r.z1()-2);
            h.assertTrue(CityInteriorRuntimeTests.roomPath(h,source,entry,new BlockPos(p.corridorX,f.y(),p.coreZ-1),r),p.id+" entrance");
            h.assertTrue(p.floors.size()<4||p.lift!=null,p.id+" lift coverage");
        }
    }
    @GameTest(template=T,batch="interior_coverage",timeoutTicks=200)public static void eight_public_families_and_service_devices(GameTestHelper h){
        var biome=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var generator=new CityGreyboxGenerator(new FixedBiomeSource(biome),SEED);generator.interiorPolicy(CityInteriorVersions.Policy.fresh());
        var groups=new LinkedHashMap<String,CityInteriors.Group>();
        for(int[] pos:new int[][]{{-160,-105},{-225,-105},{-225,-35},{-237,-53},{-76,433},{1620,-1835},{1564,-1840},{1670,-1840},{0,760},{-1755,-825},{1410,-1750},{-88,574},{-46,574}}){var g=generator.interiorAt(pos[0],pos[1]);h.assertTrue(g!=null,"public interior at "+Arrays.toString(pos));groups.put(g.id(),g);}
        for(var group:groups.values())verify(h,group);
        var bounds=LevelHeightAccessor.create(-64,1024);var random=h.getLevel().getChunkSource().randomState();
        for(var group:groups.values())for(var p:group.parts())if(p.lift!=null){
            var lift=p.lift;var columns=new HashMap<Long,net.minecraft.world.level.NoiseColumn>();
            for(int x=lift.x();x<=lift.x()+3;x++)for(int z=lift.z();z<=lift.z()+3;z++){
                var column=generator.getBaseColumn(x,z,bounds,random);boolean edge=x==lift.x()||x==lift.x()+3||z==lift.z()||z==lift.z()+3;
                for(int y=lift.floors()[0];y<=lift.floors()[lift.floors().length-1]+2;y++){
                    var state=column.getBlock(y);h.assertTrue(edge?!state.isAir():state.getCollisionShape(h.getLevel(),new BlockPos(x,y,z)).isEmpty(),p.id+" actual generator shaft "+x+","+y+","+z);
                }
            }
        }
        for(var site:CityInteriors.services()){var column=generator.getBaseColumn(site.home().getX(),site.home().getZ(),bounds,random);h.assertTrue(column.getBlock(site.home().getY()-1).is(Blocks.CYAN_GLAZED_TERRACOTTA),"existing service marker retained "+site.role()+site.home());for(int dy=0;dy<2;dy++)h.assertTrue(column.getBlock(site.home().getY()+dy).getCollisionShape(h.getLevel(),site.home()).isEmpty(),"existing clerk stand remains clear "+site.role()+site.home());}
        var node=generator.getBaseColumn(-225,-105,bounds,random);h.assertFalse(node.getBlock(7).getCollisionShape(h.getLevel(),BlockPos.ZERO).isEmpty(),"real wireless node has solid support");
        h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(node.getBlock(8).getBlock()).getPath().contains("wireless"),"real wireless device retained");
        for(int[] book:new int[][]{{-216,0,-43},{-214,0,-44},{-222,0,-30},{-221,0,-95},{-80,0,430}}){var state=generator.getBaseColumn(book[0],book[2],bounds,random).getBlock(book[1]);h.assertTrue(state.is(Blocks.LECTERN)&&state.getValue(LecternBlock.HAS_BOOK),"existing reference reading service retained "+Arrays.toString(book));}
        var clinic=generator.interiorAt(-237,-53);int beds=0;
        for(int x=clinic.reservation().x0();x<=clinic.reservation().x1();x++)for(int z=clinic.reservation().z0();z<=clinic.reservation().z1();z++)if(generator.getBaseColumn(x,z,bounds,random).getBlock(0).getBlock()instanceof BedBlock)beds++;
        h.assertTrue(beds>=2,"public clinic has a real bed");
        write("city-interior-public-coverage.json",groups.values().stream().map(CityInteriorCoverageRuntimeTests::record).toList());h.succeed();
    }
    @GameTest(template=T,batch="interior_policy",timeoutTicks=100)public static void immutable_policy_generation_order_and_legacy_columns(GameTestHelper h){
        var biome=h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var a=new CityGreyboxGenerator(new FixedBiomeSource(biome),SEED);var b=new CityGreyboxGenerator(new FixedBiomeSource(biome),SEED);
        a.interiorPolicy(CityInteriorVersions.Policy.fresh());b.interiorPolicy(CityInteriorVersions.Policy.fresh());
        var points=new ArrayList<int[]>();for(int x=-180;x<=-145;x+=5)for(int z=-116;z<=-96;z+=5)points.add(new int[]{x,z});var bounds=LevelHeightAccessor.create(-64,1024);var rand=h.getLevel().getChunkSource().randomState();var hashes=new HashMap<String,List<BlockState>>();
        for(var p:points){var c=a.getBaseColumn(p[0],p[1],bounds,rand);var column=new ArrayList<BlockState>();for(int y=-1;y<=24;y++)column.add(c.getBlock(y));hashes.put(Arrays.toString(p),column);}
        Collections.reverse(points);for(var p:points){var c=b.getBaseColumn(p[0],p[1],bounds,rand);for(int y=-1;y<=24;y++)h.assertTrue(c.getBlock(y).equals(hashes.get(Arrays.toString(p)).get(y+1)),"reverse generation order is stable");}
        a.interiorPolicy(new CityInteriorVersions.Policy(Set.of(ChunkPos.asLong(-12,-8)),true));var r5=new CityGreyboxGenerator(new FixedBiomeSource(biome),SEED);
        for(var p:points){var c=a.getBaseColumn(p[0],p[1],bounds,rand);var d=r5.getBaseColumn(p[0],p[1],bounds,rand);for(int y=-1;y<=32;y++)h.assertTrue(c.getBlock(y).equals(d.getBlock(y)),"whole school retains exact R5 generation after one old chunk");}
        h.succeed();
    }
}
