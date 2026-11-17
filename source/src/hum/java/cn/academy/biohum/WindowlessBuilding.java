package cn.academy.biohum;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Existing saves load their original seat. New sites have separate teleport and chamber anchors. */
public final class WindowlessBuilding extends SavedData {
    private static final Factory<WindowlessBuilding> FACTORY =
            new Factory<>(WindowlessBuilding::new,WindowlessBuilding::load);
    private static final int HALF=12, TOP=54, CHAMBER=36, ARENA=48;
    private static final Site ACADEMY_SITE = new Site(AcademyTowerChamber.SEAT, 3);
    private static final UUID ACADEMY_ALEISTER = UUID.nameUUIDFromBytes(
            "academy:story_city_map/windowless/aleister".getBytes(StandardCharsets.UTF_8));
    private Site site;
    private boolean academyAleisterSpawned;

    private record Site(BlockPos origin,int layout) {
        boolean old(){return layout==1;}
        boolean academy(){return layout==3;}
        BlockPos seat(){return origin.above(old()||academy()?0:CHAMBER);}
        BlockPos console(){return seat().offset(-2,1,2);}
        BlockPos entry(){return academy()?AcademyTowerChamber.ENTRY:origin.offset(0,old()?1:0,old()?6:7);}
        BlockPos rim(){return academy()?AcademyTowerChamber.STREET:origin.offset(0,0,old()?12:14);}
        BlockPos arena(){return academy()?origin.offset(2,1,2):origin.offset(0,ARENA+1,3);}
        BlockPos low(){return origin.offset(-HALF,-1,-HALF);}
        BlockPos high(){return origin.offset(HALF,TOP,HALF);}
        AABB interior(){
            if(academy())return AcademyTowerChamber.INTERIOR;
            return new AABB(Vec3.atLowerCornerOf(origin.offset(old()?-9:-11,0,old()?-9:-11)),
                    Vec3.atLowerCornerOf(origin.offset(old()?10:12,old()?19:TOP,old()?10:12)));
        }
    }

    private static WindowlessBuilding get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(FACTORY,"acabiohum_windowless");
    }

    private static WindowlessBuilding load(CompoundTag tag,HolderLookup.Provider lookup){
        var data=new WindowlessBuilding();
        if(tag.contains("Origin"))data.site=new Site(BlockPos.of(tag.getLong("Origin")),2);
        else if(tag.contains("Seat"))data.site=new Site(BlockPos.of(tag.getLong("Seat")),1);
        data.academyAleisterSpawned=tag.getBoolean("AcademyAleisterSpawned");
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        if(site!=null){
            tag.putLong("Seat",site.seat().asLong());
            tag.putInt("Layout",site.layout);
            if(!site.old())tag.putLong("Origin",site.origin.asLong());
        }
        if(academyAleisterSpawned)tag.putBoolean("AcademyAleisterSpawned",true);
        return tag;
    }

    static void tickAcademyChamber(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(AcademyTowerChamber.CITY)
                || level.getGameTime() % 20 != 0
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        var chunk = AcademyTowerChamber.CHUNK;
        if (level.players().stream().noneMatch(player -> !player.isSpectator()
                        && player.distanceToSqr(Vec3.atCenterOf(AcademyTowerChamber.SEAT)) < 96 * 96)
                || level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                || !level.areEntitiesLoaded(chunk.toLong())) return;

        var data = get(level);
        if (data.academyAleisterSpawned) return;
        // A stable UUID also covers a saved entity with an older or missing SavedData file.
        if (level.getEntity(ACADEMY_ALEISTER) instanceof HumNpc existing && existing.characterId.equals("aleister")) {
            data.rememberAcademySpawn();
            return;
        }
        if (!AcademyTowerChamber.prepare(level)) return;

        var npc = BioHum.NPCS.get("aleister").get().create(level);
        if (npc == null || !npc.selectPhase(HumNpc.SUSPENDED_PHASE)) return;
        npc.setUUID(ACADEMY_ALEISTER);
        npc.mountChamber(AcademyTowerChamber.SEAT);
        npc.setMode(2, null);
        if (level.noCollision(npc) && level.addFreshEntity(npc)) data.rememberAcademySpawn();
    }

    private void rememberAcademySpawn() {
        academyAleisterSpawned = true;
        setDirty();
    }

    private static Site accessibleSite(ServerPlayer player) {
        var level = player.serverLevel();
        var data = get(level);
        var manual = data.site;
        if (!level.dimension().equals(AcademyTowerChamber.CITY) || !data.academyAleisterSpawned) return manual;
        if (manual != null && (manual.interior().contains(player.position())
                || player.distanceToSqr(Vec3.atCenterOf(manual.origin))
                < player.distanceToSqr(Vec3.atCenterOf(ACADEMY_SITE.origin)))) return manual;
        return ACADEMY_SITE;
    }

    static int commandBuild(CommandSourceStack source,ServerPlayer player,boolean relocate){
        String result=place(source.getLevel(),BlockPos.containing(source.getPosition()),player,relocate);
        if(result!=null){source.sendFailure(Component.literal(result));return 0;}
        source.sendSuccess(()->Component.literal(relocate
                ?"原培养舱角色已迁往新版无窗大楼。"
                :"无窗大楼已建立。靠近后使用 /biohum windowless enter 进行坐标传送。"),false);
        return 1;
    }

    public static String build(ServerLevel level,BlockPos origin,ServerPlayer player){
        return place(level,origin,player,false);
    }

    private static String place(ServerLevel level,BlockPos origin,ServerPlayer player,boolean relocate){
        var saved=get(level);
        if(player==null||player.level()!=level)return "须由在同维度的管理员执行。";
        if(!relocate&&saved.site!=null)return "当前维度已有无窗大楼，可用 relocate 迁移。";
        if(relocate&&(saved.site==null||!WorldEffects.inWorld(level,saved.site.seat())))
            return "旧舱位须存在且处于已加载区块。";
        HumNpc original=relocate?occupant(level,saved.site):null;
        if(relocate&&(original==null||!level.mayInteract(player,saved.site.seat())))
            return "旧舱位须有可迁移的亚雷斯塔，且允许操作。";

        var next=new Site(origin.immutable(),2);
        for(int x=-HALF;x<=HALF;x++)for(int z=-HALF;z<=HALF;z++){
            if(!WorldEffects.inWorld(level,origin.offset(x,-1,z))
                    ||!WorldEffects.inWorld(level,origin.offset(x,TOP,z)))
                return "新址须处于已加载区块、建造高度和世界边界内。";
        }
        var bounds=new AABB(Vec3.atLowerCornerOf(next.low()),Vec3.atLowerCornerOf(next.high().offset(1,1,1)));
        if(!level.getEntitiesOfClass(LivingEntity.class,bounds,LivingEntity::isAlive).isEmpty())return "请清空预定建筑范围。";
        for(var pos:BlockPos.betweenClosed(next.low(),next.high())){
            if(!level.getBlockState(pos).isAir())return "新址需要 25×56×25 格净空，占用点 "+pos.toShortString()+"。";
        }
        if(safeExit(player,next)==null)return "墙外锚点下方需要安全地面。";

        var plan=blueprint(origin);
        for(var pos:plan.keySet()){
            if(!level.mayInteract(player,pos))return "新址包含受保护的位置。";
        }
        var npc=relocate?original:BioHum.NPCS.get("aleister").get().create(level);
        if(npc==null)return "培养舱角色创建失败。";
        var placed=new ArrayList<BlockSnapshot>(plan.size());
        for(var entry:plan.entrySet()){
            var pos=entry.getKey();
            if(!level.getBlockState(pos).isAir()){
                undo(placed);
                return "建造时空间发生变化，本次放置已回滚。";
            }
            var snapshot=BlockSnapshot.create(level.dimension(),level,pos);
            if(!level.setBlock(pos,entry.getValue(),Block.UPDATE_CLIENTS)){
                undo(placed);
                return "方块放置失败，本次放置已回滚。";
            }
            placed.add(snapshot);
            if(NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(pos.below()),player)).isCanceled()){
                undo(placed);
                return "领地阻止放置，本次放置已回滚。";
            }
        }
        npc.mountChamber(next.seat());
        if(!relocate){
            npc.setMode(2,null);
            if(!level.addFreshEntity(npc)){
                undo(placed);
                return "培养舱角色安装失败，本次放置已回滚。";
            }
        } else {
            npc.getNavigation().stop();
            npc.setDeltaMovement(Vec3.ZERO);
        }
        saved.site=next;
        saved.setDirty();
        return null;
    }

    private static void undo(List<BlockSnapshot> snapshots){
        for(var snapshot:snapshots.reversed())snapshot.restore(Block.UPDATE_ALL);
    }

    private static Map<BlockPos,BlockState> blueprint(BlockPos origin){
        Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        for(int y=-1;y<=TOP;y++)for(int x=-HALF;x<=HALF;x++)for(int z=-HALF;z<=HALF;z++){
            int span=Math.max(Math.abs(x),Math.abs(z));
            if(Math.abs(x)+Math.abs(z)>22)continue;
            boolean shell=span==HALF||Math.abs(x)+Math.abs(z)==22;
            Block part=null;
            if(shell){
                boolean vertical=Math.abs(x)==5||Math.abs(z)==5||Math.abs(x)+Math.abs(z)==22;
                part=vertical?Blocks.POLISHED_DEEPSLATE:Blocks.GRAY_CONCRETE;
                if(y<2||y>=TOP-2||y==18||y==34)part=Blocks.DEEPSLATE_TILES;
            }
            if(y==-1||y==TOP)part=(span<8?Blocks.SMOOTH_STONE:Blocks.POLISHED_DEEPSLATE);
            if(!shell&&y>=ARENA&&y<=ARENA+1&&span<10&&y==ARENA)
                part=span>=8?Blocks.POLISHED_BLACKSTONE_BRICKS:Blocks.SMOOTH_STONE;
            if(y==CHAMBER&&x*x+z*z<=33)part=span>4?Blocks.POLISHED_DEEPSLATE:Blocks.POLISHED_BLACKSTONE;
            if(y==CHAMBER&&Math.abs(x)<=1&&z>=4&&z<=10)part=Blocks.POLISHED_DEEPSLATE;
            if(y==CHAMBER&&Math.abs(x)<=2&&Math.abs(z)<=2)part=Blocks.WAXED_OXIDIZED_CUT_COPPER;
            if(y>CHAMBER&&y<=CHAMBER+3&&span<=1)part=Blocks.BARRIER;
            if(y>=CHAMBER+5&&y<=CHAMBER+11&&Math.abs(x)<=1&&z==0)part=Blocks.WAXED_EXPOSED_COPPER;
            if(y==CHAMBER+7&&(Math.abs(x)==2||Math.abs(x)==3)&&z==0)part=Blocks.POLISHED_DEEPSLATE;
            if(y==CHAMBER+1&&x==-2&&z==2)part=Blocks.WAXED_OXIDIZED_COPPER_BULB;
            if(y==CHAMBER&&x==-2&&z==2)part=Blocks.CHISELED_COPPER;
            if(y>6&&y<TOP&&y%8==4&&span==11&&Math.min(Math.abs(x),Math.abs(z))==4)
                part=Blocks.PEARLESCENT_FROGLIGHT;
            if(y==CHAMBER&&x*x+z*z>13&&x*x+z*z<=25&&x*z==0)part=Blocks.SEA_LANTERN;
            if(part!=null)blocks.put(origin.offset(x,y,z),part.defaultBlockState());
        }

        // Two-wide maintenance stairs wrap the perimeter. The center stays open through the ascent.
        int x=-9,z=9,y=-1;
        for(Direction direction:new Direction[]{Direction.EAST,Direction.NORTH,Direction.WEST,Direction.SOUTH,
                Direction.EAST,Direction.NORTH,Direction.WEST,Direction.SOUTH}) {
            int length=6;
            for(int i=0;i<length;i++){
                x+=direction.getStepX();z+=direction.getStepZ();
                ++y;
                for(int width=0;width<2;width++){
                    var at=origin.offset(x-direction.getStepZ()*width,y,z+direction.getStepX()*width);
                    blocks.put(at,Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,direction));
                }
            }
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                blocks.putIfAbsent(origin.offset(x+dx,y,z+dz),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            if(y==35){
                for(int bridge=x;bridge<=0;bridge++)blocks.put(origin.offset(bridge,CHAMBER,9),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        // A narrow inner lift links the entry, the chamber landing and the duel floor.
        for(int height=0;height<=ARENA+1;height++){
            for(int dx=-1;dx<=1;dx++)blocks.put(origin.offset(9+dx,height,-8),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            blocks.put(origin.offset(9,height,-7),Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING,Direction.SOUTH));
        }
        for(int i=-7;i<=9;i++)blocks.put(origin.offset(i,CHAMBER,8),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for(int i=-7;i<=8;i++)blocks.put(origin.offset(10,CHAMBER,i),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        blocks.put(origin.offset(10,CHAMBER,-7),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for(int i=8;i<=10;i++)blocks.put(origin.offset(9,CHAMBER,i),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for(int i=-7;i<=-5;i++)blocks.put(origin.offset(10,ARENA,i),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        for(int i=-7;i<=2;i++)blocks.put(origin.offset(9,ARENA,i),Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        return blocks;
    }

    public static void useConsole(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer player)||event.getHand()!=InteractionHand.MAIN_HAND)return;
        var level=player.serverLevel();
        var site=accessibleSite(player);
        if(site==null||!event.getPos().equals(site.console())
                ||!level.getBlockState(event.getPos()).is(Blocks.WAXED_OXIDIZED_COPPER_BULB))return;
        if(openConsole(player)==1){
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static HumNpc occupant(ServerLevel level,Site site){
        var at=Vec3.atBottomCenterOf(site.seat()).add(0,1,0);
        return level.getEntitiesOfClass(HumNpc.class,new AABB(at,at).inflate(1,.4,1),
                n->n.isAlive()&&n.chamberInstalled()&&n.suspended()).stream().findFirst().orElse(null);
    }

    static int openConsole(ServerPlayer player){
        var level=player.serverLevel();
        var site=accessibleSite(player);
        if(site==null||player.distanceToSqr(Vec3.atCenterOf(site.console()))>28)return 0;
        var npc=occupant(level,site);
        if(npc==null||npc.distanceToSqr(player)>36)return 0;
        PacketDistributor.sendToPlayer(player,new Payloads.Open(npc.getId()));
        return 1;
    }

    static int release(CommandSourceStack source,ServerPlayer player){
        var level=source.getLevel();
        var site=accessibleSite(player);
        var npc=site==null?null:occupant(level,site);
        if(npc==null||!site.interior().contains(player.position())||!level.mayInteract(player,site.seat())){
            source.sendFailure(Component.literal("可在大楼内操作已安装的培养舱。"));
            return 0;
        }
        var destination=Vec3.atBottomCenterOf(site.old()?site.seat().offset(0,1,4):site.arena());
        if(!safeStandingAt(level,npc,destination)||!level.mayInteract(player,BlockPos.containing(destination))){
            source.sendFailure(Component.literal("出舱落点被占用或受到保护。"));
            return 0;
        }
        npc.selectPhase("nt18_outside");
        npc.moveTo(destination.x,destination.y,destination.z,180,0);
        npc.setDeltaMovement(Vec3.ZERO);
        npc.resetFallDistance();
        return 1;
    }

    private static boolean safeStandingAt(ServerLevel level,HumNpc npc,Vec3 at){
        var block=BlockPos.containing(at);
        var space=new AABB(at.x-.31,at.y,at.z-.31,at.x+.31,at.y+1.87,at.z+.31);
        return WorldEffects.inWorld(level,block)&&level.getBlockState(block.below()).isSolid()
                &&level.getFluidState(block).isEmpty()&&level.noCollision(npc,space)
                &&level.getEntitiesOfClass(LivingEntity.class,space,living->living!=npc&&living.isAlive()).isEmpty();
    }

    private static Vec3 safeExit(ServerPlayer player,Site site){
        for(int y=0;y>=-12;y--){
            var position=Vec3.atBottomCenterOf(site.rim().above(y));
            if(Abilities.safeDestination(player,position))return position;
        }
        return null;
    }

    static int transfer(CommandSourceStack source,ServerPlayer player,boolean enter){
        var level=source.getLevel();
        var site=accessibleSite(player);
        boolean inside=site!=null&&site.interior().contains(player.position());
        if(site==null||player.level()!=level||(!enter&&!inside)
                ||enter&&(inside||Vec3.atCenterOf(site.origin).distanceToSqr(player.position())>38*38)){
            source.sendFailure(Component.literal("当前位置无法联通无窗大楼的坐标锚。"));
            return 0;
        }
        Vec3 dest=enter?Vec3.atBottomCenterOf(site.entry()):safeExit(player,site);
        if(dest==null||!Abilities.safeDestination(player,dest)){
            source.sendFailure(Component.literal("坐标落点缺少安全空间。"));
            return 0;
        }
        Vec3 old=player.position();
        player.connection.teleport(dest.x,dest.y,dest.z,enter?180f:0f,0);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        level.playSound(null,BlockPos.containing(old),SoundEvents.CHORUS_FRUIT_TELEPORT,SoundSource.BLOCKS,.7f,.88f);
        level.playSound(null,BlockPos.containing(dest),SoundEvents.CHORUS_FRUIT_TELEPORT,SoundSource.BLOCKS,.7f,1.11f);
        return 1;
    }

    static Vec3 console(HumNpc npc,float yaw) {
        return npc.position().add(0,.86,0).add(Vec3.directionFromRotation(0,npc.chamberInstalled()?0:yaw).scale(1.66));
    }

    static boolean clearPort(HumNpc npc,LivingEntity target){
        Vec3 from=console(npc,npc.castYaw());
        Vec3 to=target.getBoundingBox().getCenter();
        return SkillTrajectory.surface((ServerLevel)npc.level(),from,to).block()==null;
    }
}
