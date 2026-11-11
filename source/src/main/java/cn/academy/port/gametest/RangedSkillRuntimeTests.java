package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.canon.CanonSkills;
import cn.academy.port.canon.CanonVfx;
import cn.academy.port.core.RangedSkillRanges;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual distant damage/control and visual endpoints, including free refusal and solid occlusion. */
@GameTestHolder("academy_ranged")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class RangedSkillRuntimeTests {
    private record Skill(String category,String id,boolean damaging){}
    private static final List<Skill> SKILLS=List.of(
            new Skill("electromaster","arc_gen",true),new Skill("electromaster","railgun",true),
            new Skill("dark_matter","dark_shard",true),new Skill("dark_matter","dark_pierce",true),
            new Skill("dark_matter","dark_spread",true),new Skill("dark_matter","dark_spear_fan",true),new Skill("dark_matter","dark_matter_barrage",true),
            new Skill("aero_hand","aero_pressure_lance",true),new Skill("aero_hand","aero_barrage",true),new Skill("aero_hand","aero_gust",true),
            new Skill("aero_hand","aero_cone",true),new Skill("aero_hand","aero_ground_blast",true),
            new Skill("paper_psychokinesis","paper_blade",true),new Skill("hydro_hand","hydro_whip",true),
            new Skill("float_dial","float_lift",false),new Skill("mental_out","mental_probe",false),new Skill("mental_out","mental_intent_scan",false));
    private RangedSkillRuntimeTests(){}
    @SubscribeEvent public static void template(LevelEvent.Load e){
        if(!GameTestHooks.isGametestEnabled()||!(e.getLevel() instanceof ServerLevel w))return;
        var tag=new CompoundTag();var size=new ListTag();for(int v:new int[]{16,12,88})size.add(IntTag.valueOf(v));
        var position=new ListTag();for(int i=0;i<3;i++)position.add(IntTag.valueOf(0));
        var air=new CompoundTag();air.putString("Name","minecraft:air");var cell=new CompoundTag();cell.put("pos",position);cell.putInt("state",0);
        var blocks=new ListTag();blocks.add(cell);var palette=new ListTag();palette.add(air);
        tag.put("size",size);tag.put("blocks",blocks);tag.put("palette",palette);tag.put("entities",new ListTag());
        w.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_ranged","lane")).load(w.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
        var square=new ListTag();for(int v:new int[]{88,12,88})square.add(IntTag.valueOf(v));tag.put("size",square);
        w.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_ranged","terrain")).load(w.registryAccess().lookupOrThrow(Registries.BLOCK),tag);
    }
    @GameTestGenerator public static Collection<TestFunction> boundaries(){
        var list=new ArrayList<TestFunction>();
        for(var skill:SKILLS)for(String mode:List.of("near","extended","outside","blocked")){
            // The classic railgun intentionally penetrates terrain; its charge/admission is covered by the native suite.
            if(skill.id.equals("railgun")&&mode.equals("blocked"))continue;
            String name=skill.id+"_"+mode;
            list.add(new TestFunction("ranged", "academy_ranged."+name,"academy_ranged:lane",Rotation.NONE,100,0,true,false,1,1,false,h->exercise(h,skill,mode)));
        }
        list.add(new TestFunction("ranged_terrain","academy_ranged.railgun_diagonal_terrain_cap","academy_ranged:terrain",Rotation.NONE,100,0,true,false,1,1,false,RangedSkillRuntimeTests::terrain));
        return list;
    }
    private static void terrain(GameTestHelper h){
        double reach = RangedSkillRanges.fixed("railgun");
        int fixtureSpan = (int) Math.ceil(reach) + 32;
        var w=h.getLevel();UUID id=UUID.randomUUID();var cookie=CommonListenerCookie.createInitial(new GameProfile(id,"RT"+id.toString().substring(0,8)),false);
        var p=new ServerPlayer(w.getServer(),w,cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);
        var saved=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try{
            // The native structure block caps its bounds at 48; a full-range terrain trace needs
            // a separate restored corridor beyond the framework's barrier walls.
            NetworkRegistry.configureMockConnection(connection);p.server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);p.moveTo(h.absoluteVec(new Vec3(4.5,2,4.5)).add(-1024,0,-1024),-45,0);p.setYHeadRot(-45);
            for(int x=-16;x<fixtureSpan;x+=8)for(int z=-16;z<fixtureSpan;z+=8)w.getChunkAt(BlockPos.containing(p.position().add(x,0,z)));
            Vec3 origin=p.getEyePosition(),axis=cn.academy.port.skill.ClassicRaytrace.direction(p).normalize();var inside=new ArrayList<BlockPos>();var outside=new ArrayList<BlockPos>();
            for(int step=0;step<=fixtureSpan;step++){
                BlockPos at=BlockPos.containing(origin.add(axis.scale(step)));
                for(BlockPos cell:BlockPos.betweenClosed(at.offset(-4,-3,-4),at.offset(4,3,4)))if(!w.getBlockState(cell).isAir()){
                    BlockPos copy=cell.immutable();saved.putIfAbsent(copy,w.getBlockState(copy));w.setBlock(copy,Blocks.AIR.defaultBlockState(),2);
                }
            }
            for(int layer=0;layer<2;layer++){
                BlockPos centre=BlockPos.containing(origin.add(axis.scale(layer==0?reach-8:reach+10)));
                for(BlockPos cell:BlockPos.betweenClosed(centre.offset(-3,-3,-3),centre.offset(3,3,3))){BlockPos copy=cell.immutable();saved.putIfAbsent(copy,w.getBlockState(copy));w.setBlock(copy,Blocks.STONE.defaultBlockState(),3);(layer==0?inside:outside).add(copy);}
            }
            h.assertTrue(cn.academy.port.AcademyConfig.DESTROY_BLOCKS.get(),"native terrain destruction enabled in isolated fixture");
            var result=cn.academy.port.skill.RailgunDamage.perform(p,60,1000000);
            long changed=inside.stream().filter(pos->!w.getBlockState(pos).is(Blocks.STONE)).count();
            h.assertTrue(result.beamLength()==reach&&changed>0&&changed<=12,"actual diagonal cannon changes at most 12 reachable stone cells; mode="+cn.academy.port.highpower.environment.SkillEnvironment.mode(w)+" interact="+w.mayInteract(p,p.blockPosition())+" changed="+changed+" axis="+axis+" origin="+origin);
            h.assertTrue(outside.stream().allMatch(pos->w.getBlockState(pos).is(Blocks.STONE)),"voxel stepping cannot extend terrain damage past the configured beam reach");h.succeed();
        }finally{for(var entry:saved.entrySet())w.setBlock(entry.getKey(),entry.getValue(),2);try{p.server.getPlayerList().remove(p);}finally{channel.finishAndReleaseAll();}}
    }
    private static void exercise(GameTestHelper h,Skill skill,String mode){
        var w=h.getLevel();w.setDayTime(18000);UUID id=UUID.randomUUID();var cookie=CommonListenerCookie.createInitial(new GameProfile(id,"RG"+id.toString().substring(0,8)),false);
        var p=new ServerPlayer(w.getServer(),w,cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);Zombie mob=null;
        var saved=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try{
            NetworkRegistry.configureMockConnection(connection);p.server.getPlayerList().placeNewPlayer(connection,p,cookie);p.setGameMode(GameType.SURVIVAL);
            // Like the terrain test, use a restored lane outside the structure's capped
            // bounds. A 96-block trace otherwise intersects another test's barrier wall.
            double reach=RangedSkillRanges.at(skill.id,1,0);
            int span=(int)Math.ceil(reach)+4;
            Vec3 feet=h.absoluteVec(new Vec3(4.5,2,3.5)).add(-2048,0,-2048);p.moveTo(feet,0,0);p.setYHeadRot(0);p.setOnGround(true);
            for(int x=(p.blockPosition().getX()-16)>>4;x<=(p.blockPosition().getX()+16)>>4;x++)for(int z=(p.blockPosition().getZ()-16)>>4;z<=(p.blockPosition().getZ()+span)>>4;z++)w.getChunk(x,z);
            for(int x=-1;x<=1;x++)for(int z=0;z<=span;z++)for(int y=-1;y<=3;y++){
                BlockPos cell=BlockPos.containing(feet.add(x,y,z));saved.putIfAbsent(cell,w.getBlockState(cell));
                w.setBlock(cell,(y==-1?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
            }
            var state=AbilityStorage.get(p);state.changeCategoryClassic(skill.category);state.setLevel(5);
            for(var definition:cn.academy.port.SkillCatalog.REGISTERED)if(definition.category().equals(skill.category)){state.learn(definition.id());state.setSkillExp(definition.id(),1);}
            state.setActivateState(true);state.presets.edit(0,0,skill.id,state::learned);h.assertTrue(HighPowerCombat.enroll(p,5),"finite exact caster ledger fixture");HighPowerResources.initializeBudget(p);
            if(skill.id.equals("paper_blade"))p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.PAPER,64));
            if(skill.id.equals("hydro_whip"))w.setBlock(BlockPos.containing(feet.add(-2,0,0)),Blocks.WATER.defaultBlockState(),3);
            double distance=mode.equals("near")?1:mode.equals("outside")?reach+1.25:reach-1;
            mob=EntityType.ZOMBIE.create(w);h.assertTrue(mob!=null,"real hostile entity");mob.moveTo(feet.add(0,0,distance),0,0);mob.setNoAi(!skill.category.equals("mental_out"));w.addFreshEntity(mob);
            h.assertTrue(HighPowerCombat.enroll(mob,5),"finite exact target ledger fixture");
            Vec3 delta=mob.getBoundingBox().getCenter().subtract(p.getEyePosition());float yaw=(float)-Math.toDegrees(Math.atan2(delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
            p.setYRot(yaw);p.setYHeadRot(yaw);p.setXRot(pitch);
            if(mode.equals("blocked")){int z=(int)Math.floor(distance*.5);for(int x=-1;x<=1;x++)for(int y=0;y<4;y++)w.setBlock(BlockPos.containing(feet.add(x,y,z)),Blocks.BEDROCK.defaultBlockState(),3);}
            BigDecimal before=total(mob);double cp=HighPowerResources.cp(p);drain(channel,p);
            boolean accepted;double beam=0;
            if(skill.id.equals("railgun")){var result=cn.academy.port.skill.RailgunDamage.perform(p,60,0);beam=result.beamLength();accepted=true;}
            else accepted=AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request("slot_press","0"));
            boolean expected=mode.equals("near")||mode.equals("extended");boolean hit=total(mob).compareTo(before)<0;var packets=drain(channel,p);var visuals=packets.visuals();Zombie victim=mob;
            if(skill.damaging)h.assertTrue(hit==expected,"real damage at "+distance+" with reach "+reach+": "+skill.id+"/"+mode);
            if(!skill.id.equals("railgun")&&!skill.id.equals("arc_gen")){
                h.assertTrue(accepted==expected,"actual player-path admission: "+skill.id+"/"+mode);
                h.assertTrue(expected?HighPowerResources.cp(p)<cp:HighPowerResources.cp(p)==cp,"accepted cast pays; refused cast is free");
                if(expected)h.assertTrue(visuals.stream().anyMatch(v->v.skill().equals(skill.id)&&v.endpoint().distanceTo(mobPoint(v,victim))<.05&&v.axis().distanceTo(p.getLookAngle().normalize())<1e-6),"server visual retains the actual aim axis and ends on the real target, including a close hit");
                else h.assertTrue(visuals.stream().noneMatch(v->v.skill().equals(skill.id)),"refusal emits no phantom cast");
            }
            if(skill.id.equals("railgun"))h.assertTrue(beam==reach,"actual cannon trace and reported beam have identical maximum reach");
            if(skill.id.equals("arc_gen")){
                h.assertTrue(packets.arcs().size()==1,"the real arc emits one server endpoint, even if damage removes the target");
                var packet=packets.arcs().getFirst();Vec3 from=new Vec3(packet.getDouble("x"),packet.getDouble("y"),packet.getDouble("z"));
                Vec3 point=from.add(new Vec3(packet.getDouble("dx"),packet.getDouble("dy"),packet.getDouble("dz")).scale(packet.getDouble("length")));
                h.assertTrue(packet.getDouble("length")<=reach,"arc light never extends past actual maximum reach");
                if(expected)h.assertTrue(victim.getBoundingBox().inflate(.11).contains(point),"actual emitted arc ends at the struck body");
                if(mode.equals("blocked"))h.assertTrue(w.getBlockState(BlockPos.containing(point.add(point.subtract(from).normalize().scale(.01)))).is(Blocks.BEDROCK),"actual emitted arc stops at the intervening solid block");
            }
            com.mojang.logging.LogUtils.getLogger().info("RANGED_BOUNDARY_PASS skill={} mode={} distance={} reach={} accepted={} damaged={} visuals={}",skill.id,mode,distance,reach,accepted,hit,visuals.stream().map(v->v.skill()+":"+v.origin().distanceTo(v.endpoint())).toList());
            h.succeed();
        }finally{if(mob!=null)mob.discard();for(var entry:saved.entrySet())w.setBlock(entry.getKey(),entry.getValue(),2);try{p.server.getPlayerList().remove(p);}finally{channel.finishAndReleaseAll();}}
    }
    private static Vec3 mobPoint(CanonVfx.Visual v,Zombie mob){return v.skill().equals("float_lift")||v.skill().equals("aero_barrage")?mob.position():mob.getBoundingBox().getCenter();}
    private static BigDecimal total(Zombie mob){return new BigDecimal(HighPowerCombat.healthText(mob)).add(HighPowerCombat.shield(mob));}
    private record Packets(List<CanonVfx.Visual> visuals,List<CompoundTag> arcs){}
    private static Packets drain(EmbeddedChannel channel,ServerPlayer p){
        channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();var result=new ArrayList<CanonVfx.Visual>();var arcs=new ArrayList<CompoundTag>();Object value;
        while((value=channel.readOutbound())!=null){try{if(value instanceof ClientboundCustomPayloadPacket packet){
            if(packet.payload() instanceof CanonVfx.Visual v&&v.owner().equals(p.getUUID()))result.add(v);
            if(packet.payload() instanceof AcademyNetwork.ClientData d&&d.data().getString("kind").equals("arc")&&d.data().getInt("entity")==p.getId())arcs.add(d.data().copy());
        }}finally{ReferenceCountUtil.release(value);}}
        return new Packets(result,arcs);
    }
}
