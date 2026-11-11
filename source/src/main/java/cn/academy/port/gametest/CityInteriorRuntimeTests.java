package cn.academy.port.gametest;

import cn.academy.port.city.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.*;
import java.util.function.Function;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder("academy_city_interiors")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class CityInteriorRuntimeTests {
    private static final String TEMPLATE="interior_empty";
    @SubscribeEvent public static void template(LevelEvent.Load e){
        if(!GameTestHooks.isGametestEnabled()||!(e.getLevel()instanceof ServerLevel world))return;
        var t=new CompoundTag();var size=new ListTag();for(int n:new int[]{48,48,48})size.add(IntTag.valueOf(n));var pos=new ListTag();for(int n=0;n<3;n++)pos.add(IntTag.valueOf(0));
        var cell=new CompoundTag();cell.put("pos",pos);cell.putInt("state",0);var blocks=new ListTag();blocks.add(cell);var air=new CompoundTag();air.putString("Name","minecraft:air");var palette=new ListTag();palette.add(air);
        t.put("size",size);t.put("blocks",blocks);t.put("palette",palette);t.put("entities",new ListTag());
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_city_interiors",TEMPLATE)).load(world.registryAccess().lookupOrThrow(Registries.BLOCK),t);
    }
    private static void geometry(GameTestHelper h,String id){
        var preview=new CityInteriors.Preview(id);var g=preview.group;
        for(var plan:g.parts()){
            h.assertTrue(!plan.rooms.isEmpty(),id+" real rooms");
            for(int f=0;f<plan.floors.size();f++){
                int index=f;h.assertTrue(plan.rooms.stream().anyMatch(r->r.floor()==index),id+" room coverage F"+f);
                BlockPos lobby=new BlockPos(plan.corridorX,plan.floors.get(f).y(),plan.coreZ-1);
                h.assertTrue(stand(h,preview::block,lobby.getX(),lobby.getY(),lobby.getZ()),id+" safe landing F"+f);
                for(var room:plan.rooms)if(room.floor()==f){
                    int rx=room.rect().x0()==plan.corridorX+2?room.rect().x0()+1:room.rect().x1()-1;
                    var goal=new BlockPos(rx,plan.floors.get(f).y(),(room.rect().z0()+room.rect().z1())/2);
                    h.assertTrue(roomPath(h,preview::block,lobby,goal,plan.floors.get(f).rect()),id+" reachable room "+(f+1)+"-"+room.number()+" "+room.use());
                    roomPurpose(h,preview::block,plan,room);
                }
                if(f+1<plan.floors.size()){
                    BlockPos start=new BlockPos(plan.coreX,plan.floors.get(f).y(),plan.coreZ-1),end=new BlockPos(plan.coreX+3,plan.floors.get(f+1).y(),plan.coreZ-1);
                    h.assertTrue(path(h,preview::block,start,end,plan),id+" continuous two-wide stairs "+f+" -> "+(f+1));
                }
            }
            var first=plan.floors.getFirst();int ex=(first.rect().x0()+first.rect().x1())/2;
            var entry=new BlockPos(ex,first.y(),plan.entrance==Direction.NORTH?first.rect().z0()+2:first.rect().z1()-2);
            var hall=new BlockPos(plan.corridorX,first.y(),plan.coreZ-1);
            h.assertTrue(roomPath(h,preview::block,entry,hall,first.rect()),id+" entrance connects to circulation core");
            h.assertTrue(plan.floors.size()<4||plan.lift!=null,id+" high-rise has lift");
            if(plan.lift!=null)h.assertTrue(plan.lift.floors().length==plan.floors.size(),id+" every open floor is served");
        }
        System.out.println("CITY_INTERIOR_GEOMETRY "+id+" parts="+g.parts().size()+" floors="+g.parts().stream().mapToInt(p->p.floors.size()).sum()+" rooms="+g.parts().stream().mapToInt(p->p.rooms.size()).sum());h.succeed();
    }
    @FunctionalInterface interface BlocksAt {BlockState at(int x,int y,int z);}
    static boolean stand(GameTestHelper h,BlocksAt source,int x,int y,int z){
        BlockState floor=source.at(x,y-1,z);if(floor==null||floor.isAir()||floor.getBlock()instanceof DoorBlock||floor.getCollisionShape(h.getLevel(),new BlockPos(x,y-1,z)).max(Direction.Axis.Y)<.9)return false;
        AABB body=new AABB(.2,0,.2,.8,2.9,.8);
        for(int dy=0;dy<3;dy++){BlockState s=source.at(x,y+dy,z);if(s==null||s.isAir()||s.getBlock()instanceof DoorBlock)continue;for(AABB box:s.getCollisionShape(h.getLevel(),new BlockPos(x,y+dy,z)).toAabbs())if(box.move(0,dy,0).intersects(body))return false;}return true;
    }
    static void roomPurpose(GameTestHelper h,BlocksAt source,CityInteriorPlan p,CityInteriorPlan.Room room){
        Set<Block> content=new HashSet<>();int y=p.floors.get(room.floor()).y();
        for(int x=room.rect().x0();x<=room.rect().x1();x++)for(int z=room.rect().z0();z<=room.rect().z1();z++){
            BlockState s=source.at(x,y,z);if(s==null)continue;content.add(s.getBlock());
            if(s.getBlock()instanceof BedBlock){boolean foot=s.getValue(BedBlock.PART)==net.minecraft.world.level.block.state.properties.BedPart.FOOT;var d=s.getValue(BedBlock.FACING);var other=source.at(x+(foot?d.getStepX():-d.getStepX()),y,z+(foot?d.getStepZ():-d.getStepZ()));
                h.assertTrue(other!=null&&other.is(s.getBlock())&&other.getValue(BedBlock.PART)!=s.getValue(BedBlock.PART),p.id+" complete paired bed "+room);
            }
        }
        String use=room.use();boolean ok;
        if(use.contains("卧室")||use.contains("病床"))ok=content.stream().anyMatch(b->b instanceof BedBlock);
        else if(use.contains("卫生")||use.contains("卫浴"))ok=content.contains(CityInteriorContent.block("sink"))&&content.contains(CityInteriorContent.block("toilet"));
        else if(use.contains("资料")||p.theme.equals("library"))ok=content.contains(Blocks.BOOKSHELF)&&content.contains(CityInteriorContent.block("study_desk"));
        else if(p.theme.equals("lab")||use.contains("实验"))ok=content.contains(CityInteriorContent.block("lab_bench"))||content.contains(CityInteriorContent.block("equipment_rack"));
        else if(use.contains("教室"))ok=content.contains(CityInteriorContent.block("study_desk"))&&content.contains(CityInteriorContent.block("chair"));
        else if(use.contains("厨房"))ok=content.contains(CityInteriorContent.block("sink"));
        else ok=content.stream().anyMatch(b->b==CityInteriorContent.block("office_desk")||b==CityInteriorContent.block("study_desk")||b==CityInteriorContent.block("counter")||b==CityInteriorContent.block("sofa")||b==CityInteriorContent.block("bench")||b==CityInteriorContent.block("cabinet"));
        h.assertTrue(ok,p.id+" furnishings match "+use+" F"+(room.floor()+1)+" room="+room.number()+" rect="+room.rect());
    }
    static boolean path(GameTestHelper h,BlocksAt source,BlockPos from,BlockPos to,CityInteriorPlan p){
        var seen=new HashSet<BlockPos>();var todo=new ArrayDeque<BlockPos>();todo.add(from);seen.add(from);
        int minY=from.getY(),maxY=to.getY();
        while(!todo.isEmpty()){
            BlockPos at=todo.removeFirst();if(at.equals(to))return true;
            for(Direction d:Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1}){
                var n=at.relative(d).offset(0,dy,0);if(n.getX()<p.coreX-1||n.getX()>p.corridorX+1||n.getZ()<p.coreZ-2||n.getZ()>p.coreZ+p.stairDepth||n.getY()<minY||n.getY()>maxY||seen.contains(n))continue;
                if(dy!=0){BlockState a=source.at(at.getX(),at.getY()-1,at.getZ()),b=source.at(n.getX(),n.getY()-1,n.getZ());if(!(a.getBlock()instanceof StairBlock)&&!(b.getBlock()instanceof StairBlock))continue;}
                if(stand(h,source,n.getX(),n.getY(),n.getZ())){seen.add(n);todo.add(n);}
            }
        }return false;
    }
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_01(GameTestHelper h){geometry(h,"academic_01");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_02(GameTestHelper h){geometry(h,"academic_02");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_03(GameTestHelper h){geometry(h,"academic_03");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_04(GameTestHelper h){geometry(h,"academic_04");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_05(GameTestHelper h){geometry(h,"academic_05");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_06(GameTestHelper h){geometry(h,"academic_06");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_07(GameTestHelper h){geometry(h,"academic_07");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_08(GameTestHelper h){geometry(h,"academic_08");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_09(GameTestHelper h){geometry(h,"academic_09");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void academic_10(GameTestHelper h){geometry(h,"academic_10");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_01(GameTestHelper h){geometry(h,"residential_01");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_02(GameTestHelper h){geometry(h,"residential_02");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_03(GameTestHelper h){geometry(h,"residential_03");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_04(GameTestHelper h){geometry(h,"residential_04");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_05(GameTestHelper h){geometry(h,"residential_05");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_06(GameTestHelper h){geometry(h,"residential_06");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_07(GameTestHelper h){geometry(h,"residential_07");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_08(GameTestHelper h){geometry(h,"residential_08");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_09(GameTestHelper h){geometry(h,"residential_09");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void residential_10(GameTestHelper h){geometry(h,"residential_10");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_01(GameTestHelper h){geometry(h,"lab_01");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_02(GameTestHelper h){geometry(h,"lab_02");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_03(GameTestHelper h){geometry(h,"lab_03");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_04(GameTestHelper h){geometry(h,"lab_04");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_05(GameTestHelper h){geometry(h,"lab_05");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_06(GameTestHelper h){geometry(h,"lab_06");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_07(GameTestHelper h){geometry(h,"lab_07");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_08(GameTestHelper h){geometry(h,"lab_08");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_09(GameTestHelper h){geometry(h,"lab_09");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void lab_10(GameTestHelper h){geometry(h,"lab_10");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_01(GameTestHelper h){geometry(h,"commercial_01");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_02(GameTestHelper h){geometry(h,"commercial_02");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_03(GameTestHelper h){geometry(h,"commercial_03");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_04(GameTestHelper h){geometry(h,"commercial_04");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_05(GameTestHelper h){geometry(h,"commercial_05");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_06(GameTestHelper h){geometry(h,"commercial_06");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_07(GameTestHelper h){geometry(h,"commercial_07");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_08(GameTestHelper h){geometry(h,"commercial_08");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_09(GameTestHelper h){geometry(h,"commercial_09");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void commercial_10(GameTestHelper h){geometry(h,"commercial_10");}
    @GameTest(template=TEMPLATE,batch="interior_geometry",timeoutTicks=200) public static void grand_complex(GameTestHelper h){geometry(h,"grand_complex");}
    static boolean roomPath(GameTestHelper h,BlocksAt source,BlockPos from,BlockPos to,CityInteriorPlan.Rect rect){
        var seen=new HashSet<BlockPos>();var q=new ArrayDeque<BlockPos>();q.add(from);seen.add(from);
        while(!q.isEmpty()){var at=q.removeFirst();if(at.equals(to))return true;for(Direction d:Direction.Plane.HORIZONTAL){var n=at.relative(d);if(!rect.inset(1).contains(n.getX(),n.getZ())||seen.contains(n)||!stand(h,source,n.getX(),n.getY(),n.getZ()))continue;seen.add(n);q.add(n);}}return false;
    }
    @GameTest(template=TEMPLATE,batch="interior_policy",timeoutTicks=60) public static void legacy_building_is_all_or_nothing(GameTestHelper h){
        var fresh=CityInteriorVersions.Policy.fresh();var partial=new CityInteriorVersions.Policy(Set.of(ChunkPos.asLong(1,1)),true);
        h.assertTrue(fresh.allows(0,0,47,47),"new whole building enabled");h.assertFalse(partial.allows(0,0,47,47),"one old chunk keeps the entire building legacy");
        var tag=new CompoundTag();tag.putBoolean("Captured",true);tag.putLongArray("LegacyChunks",new long[]{ChunkPos.asLong(-3,2)});var loaded=CityInteriorVersions.load(tag);
        h.assertFalse(loaded.policy().allows(-48,32,-1,79),"negative coordinates and persisted legacy occupancy");h.assertTrue(loaded.policy().allows(64,64,111,111),"unexplored building remains eligible");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="interior_policy",timeoutTicks=100) public static void storage_saves_without_refilling(GameTestHelper h){
        BlockPos p=h.absolutePos(new BlockPos(4,2,4));h.getLevel().setBlockAndUpdate(p,CityInteriorContent.state("cabinet"));
        var box=(CityInteriorContent.StorageEntity)h.getLevel().getBlockEntity(p);h.assertTrue(box.isEmpty(),"new storage is empty");box.setItem(3,new ItemStack(Items.IRON_INGOT,7));
        var tag=box.saveWithFullMetadata(h.getLevel().registryAccess());h.getLevel().removeBlockEntity(p);var restored=new CityInteriorContent.StorageEntity(p,CityInteriorContent.state("cabinet"));restored.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(restored.getItem(3).is(Items.IRON_INGOT)&&restored.getItem(3).getCount()==7,"inventory survives exact roundtrip");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="interior_pilot",timeoutTicks=280) public static void academic_lift(GameTestHelper h){ride(h,"academic");}
    @GameTest(template=TEMPLATE,batch="interior_pilot",timeoutTicks=280) public static void residential_lift(GameTestHelper h){ride(h,"residential");}
    @GameTest(template=TEMPLATE,batch="interior_pilot",timeoutTicks=280) public static void laboratory_lift(GameTestHelper h){ride(h,"lab");}
    @GameTest(template=TEMPLATE,batch="interior_pilot",timeoutTicks=280) public static void commercial_lift(GameTestHelper h){ride(h,"commercial");}
    private static void ride(GameTestHelper h,String theme){
        var f=new Fixture(h,theme,4);var p=f.player();
        f.ready(()->{
            h.assertTrue(f.ctrl.boarding(),"real doors open at initial landing");h.assertTrue(f.ctrl.board(p),"real native standing passenger boards");
            h.assertTrue(p.getVehicle()==f.ctrl.cabin()&&!f.ctrl.cabin().shouldRiderSit(),"mounted on actual cabin with standing pose");
            h.assertTrue(f.ctrl.request(2),"floor request accepted");double before=f.ctrl.cabinY();
            h.runAfterDelay(40,()->{h.assertTrue(f.ctrl.cabinY()>before&&f.ctrl.cabinY()<f.plan.floors.get(2).y(),"actual intermediate cabin motion");h.assertTrue(Math.abs(p.getY()-f.ctrl.cabinY())<.01,"passenger follows real cabin, not floor teleport");p.stopRiding();h.assertTrue(p.isPassenger(),"mid-shaft manual dismount refused");});
            h.runAfterDelay(160,()->{
                h.assertTrue(f.ctrl.boarding()&&f.ctrl.current()==2,"requested floor reached with open doors");h.assertTrue(Math.abs(p.getY()-f.plan.floors.get(2).y())<.01,"standing passenger at destination");
                p.stopRiding();h.assertFalse(p.isPassenger(),"safe landing permits dismount");h.assertTrue(p.getHealth()==20,"ride did not damage player");
                System.out.println("CITY_INTERIOR_RIDE "+theme+" y="+f.ctrl.cabinY()+" phase="+f.ctrl.phase()+" health="+p.getHealth());h.succeed();
            });
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_pilot",timeoutTicks=300) public static void four_passengers_capacity_and_save(GameTestHelper h){
        var f=new Fixture(h,"academic",4);var people=new ArrayList<ServerPlayer>();for(int i=0;i<5;i++)people.add(f.player());
        f.ready(()->{
            for(int i=0;i<4;i++)h.assertTrue(f.ctrl.board(people.get(i)),"standing slot "+i);
            h.assertFalse(f.ctrl.board(people.get(4)),"fifth passenger refused");people.get(4).setPos(f.ctrl.landing(0).add(3,0,0));
            f.ctrl.request(3);f.ctrl.request(3);
            h.runAfterDelay(170,()->{h.assertTrue(f.ctrl.current()==3&&f.ctrl.boarding(),"four passengers carried once to requested floor");for(var p:people.subList(0,4))h.assertTrue(p.isPassenger()&&Math.abs(p.getY()-f.ctrl.cabinY())<.01,"each passenger retains standing attachment");
                var tag=f.ctrl.saveWithFullMetadata(h.getLevel().registryAccess());var restored=new CityElevatorController(f.ctrl.getBlockPos(),CityInteriorContent.state("elevator_controller"));restored.loadWithComponents(tag,h.getLevel().registryAccess());
                h.assertTrue(restored.identity().equals(f.ctrl.identity())&&restored.current()==3&&restored.cabinY()==f.ctrl.cabinY(),"persistent owner roundtrip");h.assertTrue(restored.cabin()==null,"saved controller cannot duplicate a saved vehicle");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_faults",timeoutTicks=300) public static void obstruction_reopens_doors(GameTestHelper h){
        var f=new Fixture(h,"academic",4);var p=f.player();f.ready(()->{
            h.assertTrue(f.ctrl.board(p),"passenger aboard");
            var obstacle=net.minecraft.world.entity.EntityType.ARMOR_STAND.create(h.getLevel());obstacle.setNoGravity(true);obstacle.setPos(f.ctrl.shaftX()+2,f.ctrl.cabinY(),f.ctrl.shaftZ()+.1);h.getLevel().addFreshEntity(obstacle);
            f.ctrl.request(2);double start=f.ctrl.cabinY();
            h.runAfterDelay(45,()->{h.assertTrue(f.ctrl.cabinY()==start&&f.ctrl.phase()!=CityElevatorController.Phase.MOVING,"doorway obstruction prevents departure");obstacle.discard();});
            h.runAfterDelay(190,()->{h.assertTrue(f.ctrl.current()==2&&f.ctrl.boarding(),"request survives reopening and completes after clearing doorway");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_faults",timeoutTicks=300) public static void blocked_destination_refuses_departure(GameTestHelper h){
        var f=new Fixture(h,"commercial",4);var p=f.player();f.ready(()->{
            h.assertTrue(f.ctrl.board(p),"passenger aboard");var blocked=BlockPos.containing(f.ctrl.landing(2));var old=h.getLevel().getBlockState(blocked);h.getLevel().setBlockAndUpdate(blocked,Blocks.STONE.defaultBlockState());f.ctrl.request(2);double start=f.ctrl.cabinY();
            h.runAfterDelay(45,()->{h.assertTrue(f.ctrl.phase()==CityElevatorController.Phase.FAULT&&f.ctrl.failure().equals("exit_blocked")&&f.ctrl.cabinY()==start,"blocked exit detected before movement, with cause");h.getLevel().setBlockAndUpdate(blocked,old);f.ctrl.emergency();});
            h.runAfterDelay(75,()->{h.assertTrue(f.ctrl.boarding()&&f.ctrl.current()==0,"emergency stays at currently safe landing");p.stopRiding();h.assertFalse(p.isPassenger(),"passenger can leave reopened safe cabin");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_faults",timeoutTicks=220) public static void damaged_shaft_refuses_departure(GameTestHelper h){
        var f=new Fixture(h,"lab",4);var p=f.player();f.ready(()->{
            h.assertTrue(f.ctrl.board(p),"passenger aboard");BlockPos wall=new BlockPos(f.ctrl.shaftX(),(int)f.ctrl.cabinY()+2,f.ctrl.shaftZ()+2);var old=h.getLevel().getBlockState(wall);h.getLevel().setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());f.ctrl.request(3);double start=f.ctrl.cabinY();
            h.runAfterDelay(45,()->{h.assertTrue(f.ctrl.phase()==CityElevatorController.Phase.FAULT&&f.ctrl.failure().equals("shaft_damaged")&&f.ctrl.cabinY()==start,"missing shaft enclosure prevents departure");h.getLevel().setBlockAndUpdate(wall,old);f.ctrl.emergency();});
            h.runAfterDelay(75,()->{h.assertTrue(f.ctrl.boarding(),"repaired shaft permits a safe stop");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_faults",timeoutTicks=350) public static void mid_trip_obstacle_stops_and_recovers(GameTestHelper h){
        var f=new Fixture(h,"residential",4);var p=f.player();f.ready(()->{
            h.assertTrue(f.ctrl.board(p),"passenger aboard");f.ctrl.request(3);
            h.runAfterDelay(42,()->{h.assertTrue(f.ctrl.phase()==CityElevatorController.Phase.MOVING,"real intermediate travel");var obstruction=new BlockPos(f.ctrl.shaftX()+1,(int)Math.floor(f.ctrl.cabinY())+2,f.ctrl.shaftZ()+1);h.getLevel().setBlockAndUpdate(obstruction,Blocks.STONE.defaultBlockState());double stopped=f.ctrl.cabinY();
                h.runAfterDelay(3,()->{h.assertTrue(f.ctrl.phase()==CityElevatorController.Phase.FAULT&&f.ctrl.cabinY()==stopped&&p.isPassenger(),"mid-trip obstacle suspends occupied cabin without dropping player");p.stopRiding();h.assertTrue(p.isPassenger(),"unsafe dismount is refused");h.getLevel().setBlockAndUpdate(obstruction,Blocks.AIR.defaultBlockState());f.ctrl.emergency();});
            });
            h.runAfterDelay(185,()->{h.assertTrue(f.ctrl.boarding()&&p.getHealth()==20,"emergency reaches a validated landing without damage");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_interaction",timeoutTicks=160) public static void occupied_seat_native_container_and_switch(GameTestHelper h){
        var f=new Fixture(h,"commercial",4);var p=f.player();
        h.runAfterDelay(5,()->{
            var chair=h.absolutePos(new BlockPos(43,2,5));var lamp=chair.east(2);var box=chair.south(2);
            for(int dx=-2;dx<=4;dx++)for(int dz=-2;dz<=4;dz++)h.getLevel().setBlockAndUpdate(chair.offset(dx,-1,dz),Blocks.STONE.defaultBlockState());
            h.getLevel().setBlockAndUpdate(chair,CityInteriorContent.state("chair"));h.getLevel().setBlockAndUpdate(lamp,CityInteriorContent.state("interior_lamp"));h.getLevel().setBlockAndUpdate(box,CityInteriorContent.state("cabinet"));p.setPos(Vec3.atBottomCenterOf(chair.west()));
            h.assertTrue(h.getLevel().getEntitiesOfClass(CitySeatEntity.class,new AABB(chair).inflate(2)).isEmpty(),"unoccupied chair has no seat entity");
            p.gameMode.useItemOn(p,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(chair),Direction.UP,chair,false));
            h.assertTrue(p.getVehicle()instanceof CitySeatEntity&&p.getVehicle().shouldRiderSit(),"actual block interaction mounts a sitting seat");var second=f.player();second.setPos(Vec3.atBottomCenterOf(chair.west()));h.assertFalse(CitySeatEntity.sit(second,chair),"occupied chair rejects another nearby player");
            p.getVehicle().positionRider(p);h.assertTrue(p.getY()-chair.getY()<0&&p.getY()-chair.getY()>-.2,"vanilla sitting hip rests on the cushion rather than floating above it");
            h.assertTrue(Math.abs(CityInteriorContent.state("bedside").getCollisionShape(h.getLevel(),chair).max(Direction.Axis.Y)-.75)<.001,"bedside collision follows its three-quarter-block model");
            p.stopRiding();p.setPos(Vec3.atBottomCenterOf(lamp.west()));
            p.gameMode.useItemOn(p,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(lamp),Direction.UP,lamp,false));h.assertFalse(h.getLevel().getBlockState(lamp).getValue(CityInteriorContent.LIT),"real use toggles lamp off");
            p.gameMode.useItemOn(p,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(lamp),Direction.UP,lamp,false));h.assertTrue(h.getLevel().getBlockState(lamp).getValue(CityInteriorContent.LIT),"real use toggles lamp back on");
            p.setPos(Vec3.atBottomCenterOf(box.west()));p.gameMode.useItemOn(p,h.getLevel(),ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(box),Direction.UP,box,false));h.assertTrue(p.containerMenu instanceof net.minecraft.world.inventory.ChestMenu,"real block use opens actual storage menu");p.closeContainer();
            h.runAfterDelay(2,()->{h.assertTrue(h.getLevel().getEntitiesOfClass(CitySeatEntity.class,new AABB(chair).inflate(2)).isEmpty(),"seat entity disappears after dismount");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_network",timeoutTicks=180) public static void server_validates_nonce_range_ownership_and_floors(GameTestHelper h){
        var f=new Fixture(h,"academic",4);var p=f.player();f.ready(()->{
            CityElevatorNetwork.open(p,f.ctrl,0);f.drain(p);var view=f.views.get(p);h.assertTrue(view!=null,"server issued a real view payload");long token=view.token();var c=f.ctrl.getBlockPos();
            h.assertFalse(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(c,token+1,CityElevatorNetwork.CALL,0)),"wrong nonce refused");
            h.assertFalse(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(c.offset(20,0,0),token,CityElevatorNetwork.CALL,0)),"wrong controller refused");
            h.assertFalse(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(c,token,CityElevatorNetwork.SELECT,2)),"outside player cannot select another floor");
            p.setPos(f.ctrl.landing(0).add(50,0,0));h.assertFalse(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(c,token,CityElevatorNetwork.CALL,0)),"remote operation refused");p.setPos(f.ctrl.landing(0));
            h.assertTrue(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(c,token,CityElevatorNetwork.BOARD,0)),"valid board request accepted");
            h.assertFalse(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(c,token,CityElevatorNetwork.SELECT,10000)),"invalid floor refused");
            h.assertFalse(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(c,token,99,0)),"unknown action refused");
            h.assertTrue(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(c,token,CityElevatorNetwork.SELECT,2)),"actual passenger can select a served floor");h.succeed();
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_network",timeoutTicks=1400) public static void old_control_session_expires(GameTestHelper h){
        var f=new Fixture(h,"academic",4);var p=f.player();f.ready(()->{CityElevatorNetwork.open(p,f.ctrl,0);f.drain(p);var view=f.views.get(p);h.assertTrue(view!=null,"actual payload issued");h.runAfterDelay(1201,()->{h.assertFalse(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(f.ctrl.getBlockPos(),view.token(),CityElevatorNetwork.CALL,0)),"expired control session refused");h.succeed();});});
    }
    @GameTest(template=TEMPLATE,batch="interior_network",timeoutTicks=1400)public static void occupied_panel_remains_valid_for_a_long_tower_ride(GameTestHelper h){
        var f=new Fixture(h,"academic",4);var p=f.player();f.ready(()->{CityElevatorNetwork.open(p,f.ctrl,0);f.drain(p);var view=f.views.get(p);h.assertTrue(view!=null,"actual view nonce");h.assertTrue(f.ctrl.board(p),"actual standing rider");
            h.runAfterDelay(1201,()->{h.assertTrue(CityElevatorNetwork.request(p,new CityElevatorNetwork.Request(f.ctrl.getBlockPos(),view.token(),CityElevatorNetwork.SELECT,3)),"authorized occupied panel remains valid beyond idle timeout");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_lifecycle",timeoutTicks=220)public static void dimension_change_releases_old_cabin(GameTestHelper h){
        var f=new Fixture(h,"academic",4);var p=f.player();f.ready(()->{h.assertTrue(f.ctrl.board(p),"standing passenger aboard");f.ctrl.request(3);
            h.runAfterDelay(40,()->{var other=h.getLevel().getServer().getLevel(Level.NETHER);h.assertTrue(other!=null,"real other dimension");var oldCabin=f.ctrl.cabin();p.teleportTo(other,3.5,80,3.5,Set.of(),0,0);
                h.runAfterDelay(3,()->{h.assertTrue(p.level()==other&&!p.isPassenger()&&!oldCabin.getPassengers().contains(p),"dimension transfer detaches the old cabin on both sides");h.assertTrue(Math.abs(p.getX()-3.5)<.01&&Math.abs(p.getZ()-3.5)<.01,"requested dimension destination preserved");h.assertFalse(p.getPersistentData().contains("academy:city_lift_trip"),"old-dimensional rescue tag cleared");h.succeed();});
            });
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_lifecycle",timeoutTicks=220)public static void removing_occupied_controller_rescues_player(GameTestHelper h){
        var f=new Fixture(h,"academic",4);var p=f.player();f.ready(()->{h.assertTrue(f.ctrl.board(p),"standing passenger aboard");f.ctrl.request(3);
            h.runAfterDelay(40,()->{h.assertTrue(f.ctrl.phase()==CityElevatorController.Phase.MOVING,"actual cabin in flight");h.getLevel().setBlockAndUpdate(f.ctrl.getBlockPos(),Blocks.AIR.defaultBlockState());
                h.runAfterDelay(3,()->{h.assertFalse(p.isPassenger(),"removed owner rescues passenger");h.assertTrue(CityElevators.validExit(p.level(),p.position(),p)&&p.getHealth()==20,"rescued onto real loaded safe ground without damage");h.assertTrue(f.ctrl.cabin()==null,"removed owner cannot retain a duplicate cabin");h.succeed();});
            });
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_lifecycle",timeoutTicks=220)public static void death_clears_trip_and_seat(GameTestHelper h){
        var f=new Fixture(h,"academic",4);var p=f.player();f.ready(()->{h.assertTrue(f.ctrl.board(p),"standing passenger aboard");f.ctrl.request(3);
            h.runAfterDelay(40,()->{p.kill();h.runAfterDelay(3,()->{h.assertFalse(p.isAlive(),"actual native player died");h.assertFalse(p.getPersistentData().contains("academy:city_lift_trip"),"death removes rescue tag");h.assertFalse(f.ctrl.cabin().getPassengers().contains(p),"dead rider does not occupy cabin capacity");h.succeed();});});
        });
    }
    @GameTest(template=TEMPLATE,batch="interior_policy",timeoutTicks=100)public static void corrupted_controller_state_is_bounded(GameTestHelper h){
        var p=h.absolutePos(new BlockPos(2,2,2));var state=CityInteriorContent.state("elevator_controller");var c=new CityElevatorController(p,state);var tag=new CompoundTag();tag.putString("Identity","corrupt");tag.putIntArray("Floors",new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE});tag.putDouble("CabinY",0);tag.putString("Phase","MOVING");tag.putInt("Target",1);tag.putInt("At",-1);c.loadWithComponents(tag,h.getLevel().registryAccess());h.assertFalse(c.configured(),"corrupt floor span cannot start an unbounded shaft scan");
        tag.putIntArray("Floors",new int[]{0,4,8});tag.putDouble("CabinY",Double.NaN);c.loadWithComponents(tag,h.getLevel().registryAccess());h.assertFalse(c.configured(),"nonfinite cabin coordinate rejected");h.succeed();
    }
    @GameTest(template=TEMPLATE,batch="interior_policy",timeoutTicks=80)public static void dirty_controller_and_exit_probe_do_not_obtain_unloaded_chunks(GameTestHelper h){
        var w=h.getLevel();var far=new BlockPos(12000000,64,12000000);h.assertTrue(w.getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"remote test chunk is absent");int before=w.getChunkSource().getLoadedChunksCount();
        var c=new CityElevatorController(far,CityInteriorContent.state("elevator_controller"));c.setLevel(w);c.setChanged();h.assertFalse(CityElevators.validExit(w,Vec3.atBottomCenterOf(far),null),"unloaded exit is rejected");
        h.assertTrue(w.getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null&&w.getChunkSource().getLoadedChunksCount()==before,"dirty state and exit validation never obtain a chunk");h.succeed();
    }
    private static final class Fixture {
        final GameTestHelper h;final CityInteriorPlan plan;final CityElevatorController ctrl;
        final List<ServerPlayer> players=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final Map<ServerPlayer,EmbeddedChannel> channels=new IdentityHashMap<>();final Map<ServerPlayer,CityElevatorNetwork.View> views=new IdentityHashMap<>();boolean closed;
        Fixture(GameTestHelper h,String theme,int count){this.h=h;var origin=h.absolutePos(new BlockPos(5,2,5));var rect=new CityInteriorPlan.Rect(origin.getX(),origin.getZ(),origin.getX()+24,origin.getZ()+24);var fs=new ArrayList<CityInteriorPlan.Floor>();for(int n=0;n<count;n++)fs.add(new CityInteriorPlan.Floor(origin.getY()+n*4,rect));plan=new CityInteriorPlan("fixture:"+theme+":"+origin,theme,fs,rect,Direction.NORTH,true);
            for(int x=rect.x0();x<=rect.x1();x++)for(int z=rect.z0();z<=rect.z1();z++)for(int y=origin.getY()-1;y<=plan.ceiling();y++){
                BlockState s=plan.block(x,y,z);if(s==null){boolean edge=x==rect.x0()||x==rect.x1()||z==rect.z0()||z==rect.z1();s=y==origin.getY()-1?Blocks.STONE.defaultBlockState():edge?Blocks.WHITE_CONCRETE.defaultBlockState():Blocks.AIR.defaultBlockState();}
                h.getLevel().setBlockAndUpdate(new BlockPos(x,y,z),s);
            }
            ctrl=(CityElevatorController)h.getLevel().getBlockEntity(plan.lift.controller());h.assertTrue(ctrl!=null,"generated real controller block entity");ctrl.configure(plan.lift);
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        ServerPlayer player(){var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"Lift"+UUID.randomUUID().toString().substring(0,8)),false);var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.put(p,channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);drain(p);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setPos(ctrl.landing(0));p.connection.resetPosition();
            var pump=new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p,2000);NeoForge.EVENT_BUS.register(pump);hooks.add(pump);return p;}
        void ready(Runnable r){h.runAfterDelay(61,()->{players.forEach(p->{p.doTick();drain(p);});r.run();});}
        void drain(ServerPlayer p){var c=channels.get(p);c.runPendingTasks();c.flushOutbound();Object packet;while((packet=c.readOutbound())!=null){if(packet instanceof ClientboundPlayerPositionPacket pos)p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(pos.getId()));if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.payload()instanceof CityElevatorNetwork.View view)views.put(p,view);ReferenceCountUtil.release(packet);}}
        void close(){if(closed)return;closed=true;for(var p:players){CityElevators.rescue(p,ctrl);p.server.getPlayerList().remove(p);}hooks.forEach(NeoForge.EVENT_BUS::unregister);channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);if(ctrl.cabin()!=null)ctrl.cabin().discard();}
    }
}
