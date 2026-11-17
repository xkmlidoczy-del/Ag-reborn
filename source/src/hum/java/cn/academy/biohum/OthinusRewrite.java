package cn.academy.biohum;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** One overlay of the Overworld, with the previous state retained under each changed block. */
public final class OthinusRewrite extends SavedData {
    private static final Factory<OthinusRewrite> FACTORY=new Factory<>(OthinusRewrite::new,OthinusRewrite::read);
    private static final String DATA="acabiohum_othinus";
    private static final String CAST="BioHumRewriteRole";
    private static final String SLEEP="BioHumRewriteSleep";
    private static final int EDITS_PER_TICK=1200;
    private static final int CHUNKS_PER_TICK=4;
    private static final int SET_FLAGS=Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE;
    private static final long TEMPORARY_LENGTH=20*60*15L;

    private record Lease(BlockState original,BlockState applied) {}
    private record Change(BlockPos pos,BlockState state) {}
    private record Sleep(boolean noAi,boolean noGravity,boolean invisible) {}
    private record Weather(long time,int clear,int rain,int thunder,boolean raining,boolean thundering) {}

    private final Map<Long,LinkedHashMap<BlockPos,Lease>> leases=new HashMap<>();
    private final Map<Long,Set<BlockPos>> playerChanges=new HashMap<>();
    private final Set<Long> finished=new HashSet<>();
    private final Map<UUID,Sleep> asleep=new HashMap<>();
    private final Map<UUID,Vec3> relocated=new HashMap<>();
    private final Set<UUID> performers=new HashSet<>();
    private final Map<UUID,Payloads.PhaseState> sent=new HashMap<>();
    private final ArrayDeque<Change> work=new ArrayDeque<>();
    private final Set<BlockPos> brokenOverlay=new HashSet<>();

    private OthinusPhase phase;
    private BlockPos center=BlockPos.ZERO;
    private UUID actor,issuer,subject;
    private Vec3 subjectHome=Vec3.ZERO;
    private Vec3 actorHome=Vec3.ZERO;
    private float subjectHealth;
    private boolean sceneReady,networkSpoken,spearBroken,arrowsComplete,offered,changing,createdSubject;
    private long since,deadline,loopAt,chunkInProgress;
    private int radius,attempts;
    private int minY,maxY;
    private Weather weather;
    private OthinusDuel.Attack attack;

    public static OthinusRewrite get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY,DATA);
    }

    public OthinusPhase phase() { return phase; }
    public BlockPos center() { return center; }
    public int radius() { return radius; }
    public int changedBlocks() { return leases.values().stream().mapToInt(Map::size).sum(); }
    public int settledChunks() { return finished.size(); }
    public boolean sceneReady() { return sceneReady; }
    public boolean networkSpoken() { return networkSpoken; }
    public boolean spearBroken() { return spearBroken; }
    public boolean arrowsComplete() { return arrowsComplete; }
    public boolean offered() { return offered; }
    public int attempts() { return attempts; }
    public UUID subject() { return subject; }

    public boolean inside(Vec3 pos) {
        double dx=pos.x-(center.getX()+.5),dz=pos.z-(center.getZ()+.5);
        return dx*dx+dz*dz<=radius*(double)radius && pos.y>=center.getY()-16 && pos.y<=center.getY()+100;
    }

    private HumNpc caster(ServerLevel level) {
        return actor!=null && level.getEntity(actor) instanceof HumNpc npc?npc:null;
    }

    static boolean canCast(HumNpc npc,Skills.Skill skill,Vec3 at,UUID player) {
        if(!(npc.level() instanceof ServerLevel level)||!level.dimension().equals(Level.OVERWORLD)
                ||!npc.characterId.equals("othinus")||!npc.phaseId().equals("nt09_magic_god"))return false;
        var state=get(level);
        if(player==null&&!state.autonomousCast(npc,skill.id()))return false;
        return switch(skill.id()){
            case "othinus_world_dark" -> state.phase==null && state.leases.isEmpty()
                    && validAnchor(level,BlockPos.containing(CombatSkills.ground(level,at)));
            case "othinus_world_alpha" -> state.from(npc, OthinusPhase.DARK);
            case "othinus_world_beta" -> state.from(npc, OthinusPhase.ALPHA);
            case "othinus_world_loops" -> state.from(npc, OthinusPhase.BETA);
            case "othinus_world_omega" -> state.from(npc, OthinusPhase.SPACE);
            case "othinus_world_duel" -> state.from(npc, OthinusPhase.OMEGA) && state.networkSpoken;
            case "othinus_world_return" -> state.from(npc,OthinusPhase.DUEL) && state.offered;
            case "othinus_black_moon","othinus_sky_crossbow","othinus_duel_reset" ->
                    state.from(npc,OthinusPhase.DUEL) && state.attack==null
                    && (!skill.id().equals("othinus_sky_crossbow") || state.spearBroken);
            default -> false;
        };
    }

    private boolean autonomousCast(HumNpc npc,String skill) {
        var target=subjectEntity((ServerLevel)npc.level());
        return from(npc,OthinusPhase.DUEL)&&npc.validTarget(target)
                &&attack==null&&(skill.equals("othinus_black_moon")||skill.equals("othinus_sky_crossbow"));
    }

    static void autonomousDuel(HumNpc npc,ServerLevel level){
        if(!overworld(level)||npc.isNoAi()||level.getGameTime()%19!=Math.floorMod(npc.getId(),19)
                ||!npc.technique().isEmpty()||npc.controlled())return;
        var state=get(level);
        if(!state.from(npc,OthinusPhase.DUEL)||state.attack!=null||state.arrowsComplete
                ||!(level.getEntity(state.subject) instanceof HumNpc original)||!npc.validTarget(original))return;
        long age=level.getGameTime()-state.since;
        if(age<250)return;
        if(state.spearBroken){
            if(Abilities.cast(npc,"othinus_sky_crossbow",original,null,null))npc.resolvedAutonomousDuel("othinus_sky_crossbow");
        }else if(npc.hasLineOfSight(original)&&Abilities.cast(npc,"gungnir",original,null,null)){
            npc.resolvedAutonomousDuel("gungnir");
        }else if(canDropMoonFrom(npc,original)&&Abilities.cast(npc,"othinus_black_moon",null,original.position(),null)){
            npc.resolvedAutonomousDuel("othinus_black_moon");
        }
    }

    private static boolean canDropMoonFrom(HumNpc npc,HumNpc original){
        double separation=original.position().subtract(npc.position()).horizontalDistance();
        var moon=Skills.get("othinus_black_moon");
        return separation>moon.radius()+2&&separation<=moon.range();
    }

    private static boolean validAnchor(ServerLevel level,BlockPos pos){
        if(pos.getY()<level.getMinBuildHeight()+4||pos.getY()>level.getMaxBuildHeight()-45)return false;
        for(int x=-4;x<=4;x+=4){
            var floor=pos.offset(x,-1,2);
            if(!WorldEffects.inWorld(level,floor)||!level.getBlockState(floor).isSolid()
                    ||!level.getBlockState(floor.above()).getCollisionShape(level,floor.above()).isEmpty())return false;
        }
        return true;
    }

    private boolean from(HumNpc npc,OthinusPhase expected) {
        return phase==expected && sceneReady && npc.getUUID().equals(actor);
    }

    static boolean cast(HumNpc npc,Skills.Skill skill,Vec3 aim,LivingEntity target,UUID player) {
        var level=(ServerLevel)npc.level();
        var state=get(level);
        switch(skill.id()){
            case "othinus_world_dark" -> state.begin(level,npc,skill,aim,player);
            case "othinus_world_alpha" -> state.enter(level,OthinusPhase.ALPHA);
            case "othinus_world_beta" -> state.enter(level,OthinusPhase.BETA);
            case "othinus_world_loops" -> state.enter(level,OthinusPhase.BURIAL);
            case "othinus_world_omega" -> state.enter(level,OthinusPhase.OMEGA);
            case "othinus_world_duel" -> state.enter(level,OthinusPhase.DUEL);
            case "othinus_world_return" -> state.enter(level,OthinusPhase.RETURN);
            case "othinus_black_moon" -> state.attack=OthinusDuel.moon(npc,state,aim,skill);
            case "othinus_sky_crossbow" -> state.attack=OthinusDuel.crossbow(npc,state,target,skill);
            case "othinus_duel_reset" -> state.reset(level,npc);
            default -> {return false;}
        }
        if(skill.id().startsWith("othinus_world_")){
            var base=Vec3.atBottomCenterOf(state.center);
            Payloads.cue(npc,skill.id(),Payloads.Cue.FIELD,base,base,state.radius);
        }
        state.setDirty();
        return true;
    }

    private void begin(ServerLevel level,HumNpc npc,Skills.Skill skill,Vec3 aim,UUID player) {
        center=BlockPos.containing(CombatSkills.ground(level,aim));
        radius=(int)skill.radius();
        minY=Math.max(level.getMinBuildHeight()+1,center.getY()-4);
        maxY=Math.min(level.getMaxBuildHeight()-1,center.getY()+44);
        actor=npc.getUUID();
        actorHome=npc.position();
        issuer=player;
        long now=level.getGameTime();
        deadline=WorldEffects.temporary(level)?now+TEMPORARY_LENGTH:Long.MAX_VALUE;
        var data=level.getServer().getWorldData().overworldData();
        weather=new Weather(level.getDayTime(),data.getClearWeatherTime(),data.getRainTime(),
                data.getThunderTime(),data.isRaining(),data.isThundering());
        networkSpoken=spearBroken=arrowsComplete=offered=createdSubject=false;
        attempts=0;
        var area=new AABB(center).inflate(Math.min(radius,36),16,Math.min(radius,36));
        var original=level.getEntitiesOfClass(HumNpc.class,area,n->n.characterId.equals("touma")
                && n.isAlive() && !n.getPersistentData().contains(CAST)).stream()
                .min(Comparator.comparingDouble(n->n.distanceToSqr(Vec3.atCenterOf(center)))).orElse(null);
        if(original==null){
            original=spawn(level,"touma","subject",0,0,3);
            createdSubject=original!=null;
        }
        if(original!=null){
            subject=original.getUUID();
            subjectHome=original.position();
            subjectHealth=original.getHealth();
        }
        enter(level,OthinusPhase.DARK);
    }

    private void enter(ServerLevel level,OthinusPhase next) {
        clearCast(level);
        phase=next;
        since=level.getGameTime();
        loopAt=0;
        sceneReady=false;
        finished.clear();
        work.clear();
        attack=null;
        var npc=caster(level);
        if(next!=OthinusPhase.RETURN){
            level.setDayTime(next.time);
            level.setWeatherParameters(0,next.rain?1200:0,next.rain,false);
            if(npc!=null) {
                placePad(level,npc);
                var changed=Vec3.atCenterOf(center);
                if(Skills.get(npc.technique())!=null)
                    Payloads.cue(npc,npc.technique(),Payloads.Cue.FIELD,changed,changed,3);
            }
            if(next.empty()){
                sleep(level);
                for(var player:level.players()){
                    if(!player.isSpectator()&&inside(player.position())){
                        relocated.putIfAbsent(player.getUUID(),player.position());
                        placePad(level,player);
                    }
                }
            }
        }
        send(level);
        setDirty();
    }

    private void placePad(ServerLevel level,Entity entity) {
        int x=entity.getUUID().equals(actor)?-3:entity.getUUID().equals(subject)?3:0;
        int y=phase==OthinusPhase.SEA?1:0;
        int z=2;
        if(phase==OthinusPhase.DUEL){
            if(entity.getUUID().equals(subject))z=-12;
            else if(entity.getUUID().equals(actor))z=8;
        }
        if(phase.empty()){
            for(int i=x-1;i<=x+1;i++)for(int j=0;j<=3;j++)
                change(level,center.offset(i,-1,j),Blocks.BLACK_CONCRETE.defaultBlockState());
        }
        var pos=center.offset(x,y,z);
        if(!WorldEffects.inWorld(level,pos))return;
        protectFeet(level,entity,pos);
    }

    public static int release(ServerLevel level) {
        var state=get(level);
        if(state.phase==null||state.phase==OthinusPhase.RETURN)return 0;
        state.enter(level,OthinusPhase.RETURN);
        return 1;
    }

    private static boolean overworld(Level level) { return level.dimension().equals(Level.OVERWORLD); }

    public static void tick(LevelTickEvent.Post event) {
        if(event.getLevel() instanceof ServerLevel level && overworld(level))get(level).tick(level);
    }

    private void tick(ServerLevel level) {
        if(phase==null){
            if(!relocated.isEmpty())returnPlayers(level);
            return;
        }
        brokenOverlay.clear();
        long now=level.getGameTime();
        if(phase!=OthinusPhase.RETURN && (now>=deadline || caster(level)==null
                && level.getChunkSource().hasChunk(center.getX()>>4,center.getZ()>>4) && now>since+100)){
            enter(level,OthinusPhase.RETURN);
        }
        if(phase!=OthinusPhase.RETURN){
            if(level.getDayTime()!=phase.time)level.setDayTime(phase.time);
            if(now%160==0)level.setWeatherParameters(phase.rain?0:1200,phase.rain?1200:0,phase.rain,false);
        }
        advanceTerrain(level);
        if(phase==OthinusPhase.RETURN){
            if(!sceneReady && work.isEmpty() && noLoadedWork(level))finishReturn(level);
            if(sceneReady)wake(level);
            if(sceneReady && leases.isEmpty()){
                phase=null;
                subject=null;
                actor=null;
                issuer=null;
                playerChanges.clear();
                sent.clear();
                setDirty();
            }
            return;
        }
        if(!sceneReady && work.isEmpty() && noLoadedWork(level)){
            sceneReady=true;
            populate(level);
            send(level);
            setDirty();
        }
        if(now%10==0){
            if(phase.empty())sleep(level);else if(sceneReady)wake(level);
            if(sceneReady)behave(level);
            send(level);
        }
        if(loopAt>0 && now>=loopAt){
            if(phase==OthinusPhase.BURIAL)enterLoop(level,OthinusPhase.SEA);
            else if(phase==OthinusPhase.SEA)enterLoop(level,OthinusPhase.SPACE);
        }
        if(attack!=null&&!attack.tick(level,this))attack=null;
    }

    private void enterLoop(ServerLevel level,OthinusPhase next){
        enter(level,next);
        var npc=caster(level);
        if(npc!=null){
            Vec3 at=Vec3.atBottomCenterOf(center);
            Payloads.cue(npc,next.skill,Payloads.Cue.FIELD,at,at,radius);
        }
    }

    private void protectFeet(ServerLevel level,Entity entity,BlockPos at){
        if(entity.level()==level&&level.noCollision(entity,entity.getBoundingBox()
                .move(Vec3.atBottomCenterOf(at).subtract(entity.position())))){
            entity.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean noLoadedWork(ServerLevel level) {
        return nextChunk(level)==null;
    }

    private Long nextChunk(ServerLevel level) {
        if(phase==OthinusPhase.RETURN){
            return leases.keySet().stream().filter(k->loaded(level,k)).findFirst().orElse(null);
        }
        Long best=null;
        int distance=Integer.MAX_VALUE;
        int mx=center.getX()>>4,mz=center.getZ()>>4;
        var border=level.getWorldBorder();
        var middle=Vec3.atCenterOf(center);
        for(int x=(center.getX()-radius)>>4;x<=(center.getX()+radius)>>4;x++)
            for(int z=(center.getZ()-radius)>>4;z<=(center.getZ()+radius)>>4;z++){
                int x0=x<<4,z0=z<<4;
                if(x0+15<border.getMinX()||x0>=border.getMaxX()||z0+15<border.getMinZ()||z0>=border.getMaxZ())continue;
                double dx=middle.x-Math.clamp(middle.x,x0+.5,x0+15.5);
                double dz=middle.z-Math.clamp(middle.z,z0+.5,z0+15.5);
                if(dx*dx+dz*dz>radius*(double)radius)continue;
                long key=ChunkPos.asLong(x,z);
                if(finished.contains(key)||!loaded(level,key))continue;
                int d=(x-mx)*(x-mx)+(z-mz)*(z-mz);
                if(d<distance){distance=d;best=key;}
            }
        return best;
    }

    private static boolean loaded(ServerLevel level,long key) {
        return level.getChunkSource().getChunkNow(ChunkPos.getX(key),ChunkPos.getZ(key))!=null;
    }

    private void advanceTerrain(ServerLevel level) {
        int budget=EDITS_PER_TICK;
        for(int chunks=0;chunks<CHUNKS_PER_TICK && budget>0;chunks++){
            if(work.isEmpty()){
                Long next=nextChunk(level);
                if(next==null)return;
                chunkInProgress=next;
                prepare(level,next);
            }
            if(!loaded(level,chunkInProgress)){
                work.clear();
                return;
            }
            while(!work.isEmpty() && budget-->0){
                var edit=work.removeFirst();
                change(level,edit.pos,edit.state);
            }
            if(!work.isEmpty())return;
            finished.add(chunkInProgress);
            setDirty();
        }
    }

    private void prepare(ServerLevel level,long key) {
        int cx=ChunkPos.getX(key),cz=ChunkPos.getZ(key);
        var snapshot=leases.get(key);
        var desired=OthinusLandscape.chunk(phase,center,radius,cx,cz);
        int bottom=phase.empty()?minY:center.getY();
        if(phase!=OthinusPhase.RETURN && snapshot!=null){
            for(var pos:snapshot.keySet()){
                if(pos.getY()>=bottom&&pos.getY()<=maxY)
                    desired.putIfAbsent(pos,Blocks.AIR.defaultBlockState());
            }
        }
        if(phase!=OthinusPhase.RETURN){
            var chunk=level.getChunkSource().getChunkNow(cx,cz);
            var pos=new BlockPos.MutableBlockPos();
            for(int y=bottom;y<=maxY;y++){
                if(chunk.getSection(chunk.getSectionIndex(y)).hasOnlyAir())continue;
                for(int x=cx<<4;x<(cx<<4)+16;x++)for(int z=cz<<4;z<(cz<<4)+16;z++){
                    int dx=x-center.getX(),dz=z-center.getZ();
                    if(dx*dx+dz*dz>radius*radius)continue;
                    pos.set(x,y,z);
                    if(!chunk.getBlockState(pos).isAir())desired.putIfAbsent(pos.immutable(),Blocks.AIR.defaultBlockState());
                }
            }
        }
        desired.forEach((pos,state)->{if(!level.getBlockState(pos).equals(state))work.add(new Change(pos,state));});
        if(snapshot!=null)for(var entry:snapshot.entrySet()){
            if(!desired.containsKey(entry.getKey()))work.add(new Change(entry.getKey(),null));
        }
    }

    private boolean change(ServerLevel level,BlockPos pos,BlockState next) {
        if(!WorldEffects.inWorld(level,pos))return false;
        long key=ChunkPos.asLong(pos);
        var chunk=leases.get(key);
        var previous=chunk==null?null:chunk.get(pos);
        var actual=level.getBlockState(pos);
        if(next==null){
            if(previous==null)return false;
            if(actual.equals(previous.applied)){
                changing=true;
                try{level.setBlock(pos,previous.original,SET_FLAGS);}finally{changing=false;}
            }
            remove(key,pos);
            return true;
        }
        var exempt=playerChanges.get(key);
        if(exempt!=null&&exempt.contains(pos))return false;
        if(previous!=null&&!actual.equals(previous.applied)){
            exempt(key,pos);
            return false;
        }
        // The sea uses invisible water carriers. Vanilla marks light blocks unbreakable;
        // only carriers placed and still owned by this rewrite can be replaced.
        boolean ownedWater=previous!=null && actual.is(Blocks.LIGHT) && !actual.getFluidState().isEmpty();
        if(actual.equals(next)||actual.hasBlockEntity()||next.hasBlockEntity()
                ||(!ownedWater&&actual.getDestroySpeed(level,pos)<0))return false;
        var source=new WorldEffects.Source(actor,issuer,phase.skill);
        var player=WorldEffects.interactor(level,source,pos);
        if(player==null)return false;
        var snapshot=next.isAir()?null:BlockSnapshot.create(level.dimension(),level,pos);
        changing=true;
        try{
            if(!actual.isAir()&&NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,actual,player)).isCanceled()){
                return false;
            }
            if(!level.setBlock(pos,next,SET_FLAGS))return false;
            if(snapshot!=null&&NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(pos.below()),player)).isCanceled()){
                snapshot.restore(SET_FLAGS);
                return false;
            }
            var target=leases.computeIfAbsent(key,k->new LinkedHashMap<>());
            target.put(pos.immutable(),new Lease(previous==null?actual:previous.original,next));
            setDirty();
            return true;
        }finally{changing=false;}
    }

    private void remove(long key,BlockPos pos) {
        var chunk=leases.get(key);
        if(chunk!=null){
            chunk.remove(pos);
            if(chunk.isEmpty())leases.remove(key);
        }
        setDirty();
    }

    private void exempt(long key,BlockPos pos) {
        playerChanges.computeIfAbsent(key,k->new HashSet<>()).add(pos.immutable());
        remove(key,pos);
    }

    public static void playerEdit(BlockEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!overworld(level))return;
        var state=get(level);
        if(state.phase==null||state.changing||!state.inside(Vec3.atCenterOf(event.getPos())))return;
        state.exempt(ChunkPos.asLong(event.getPos()),event.getPos());
    }

    public static void breakBlock(BlockEvent.BreakEvent event) {
        if(!event.isCanceled()&&!(event.getPlayer() instanceof net.neoforged.neoforge.common.util.FakePlayer)){
            if(event.getLevel() instanceof ServerLevel level && overworld(level)){
                var state=get(level);
                var edits=state.leases.get(ChunkPos.asLong(event.getPos()));
                if(!state.changing&&edits!=null&&edits.containsKey(event.getPos())){
                    state.brokenOverlay.add(event.getPos().immutable());
                }
            }
            playerEdit(event);
        }
    }

    public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)
                ||p instanceof net.neoforged.neoforge.common.util.FakePlayer)return;
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi && event.getLevel() instanceof ServerLevel level){
            var state=get(level);
            if(state.phase==null||state.changing||!overworld(level))return;
            for(var snap:multi.getReplacedBlockSnapshots()){
                var pos=snap.getPos();
                if(state.inside(Vec3.atCenterOf(pos)))state.exempt(ChunkPos.asLong(pos),pos);
            }
        }else playerEdit(event);
    }

    public static void drops(net.neoforged.neoforge.event.level.BlockDropsEvent event){
        if(!overworld(event.getLevel()))return;
        var state=get(event.getLevel());
        if(state.phase==null||state.changing)return;
        if(state.brokenOverlay.remove(event.getPos())){
            event.getDrops().clear();
            event.setDroppedExperience(0);
        }
    }

    public static void noSpawns(MobSpawnEvent.PositionCheck event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!overworld(level))return;
        var state=get(level);
        if(state.phase!=null&&(state.phase.empty()||state.phase==OthinusPhase.OMEGA
                &&event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy)
                && state.inside(event.getEntity().position())
                && event.getSpawnType()==net.minecraft.world.entity.MobSpawnType.NATURAL)
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }

    private void finishReturn(ServerLevel level) {
        if(weather!=null){
            var data=level.getServer().getWorldData().overworldData();
            level.setDayTime(weather.time);
            data.setClearWeatherTime(weather.clear);
            data.setRainTime(weather.rain);
            data.setThunderTime(weather.thunder);
            data.setRaining(weather.raining);
            data.setThundering(weather.thundering);
            weather=null;
        }
        if(subject!=null&&level.getEntity(subject) instanceof LivingEntity original){
            if(createdSubject)original.discard();
            else {
                if(WorldEffects.inWorld(level,BlockPos.containing(subjectHome)))
                    original.teleportTo(subjectHome.x,subjectHome.y,subjectHome.z);
                original.setHealth(Math.min(subjectHealth,original.getMaxHealth()));
            }
        }
        var npc=caster(level);
        if(npc!=null){
            if(WorldEffects.inWorld(level,BlockPos.containing(actorHome))
                    &&level.noCollision(npc,npc.getBoundingBox().move(actorHome.subtract(npc.position())))){
                npc.teleportTo(actorHome.x,actorHome.y,actorHome.z);
            }
            if(offered)npc.selectPhase("nt10_fading");
            else if(spearBroken){
                npc.getPersistentData().remove("BioHumBrokenGungnir");
                npc.selectPhase("nt09_magic_god");
            }
        }
        sceneReady=true;
        send(level);
        setDirty();
    }

    private void sleep(ServerLevel level) {
        var box=new AABB(center).inflate(radius,48,radius);
        for(var mob:level.getEntitiesOfClass(Mob.class,box,m->inside(m.position())&&!exempt(m))){
            if(asleep.putIfAbsent(mob.getUUID(),new Sleep(mob.isNoAi(),mob.isNoGravity(),mob.isInvisible()))==null){
                var tag=new CompoundTag();
                var snapshot=asleep.get(mob.getUUID());
                tag.putBoolean("Ai",snapshot.noAi);
                tag.putBoolean("Gravity",snapshot.noGravity);
                tag.putBoolean("Invisible",snapshot.invisible);
                mob.getPersistentData().put(SLEEP,tag);
                mob.setNoAi(true);
                mob.setNoGravity(true);
                mob.setInvisible(true);
                mob.setDeltaMovement(Vec3.ZERO);
                setDirty();
            }
        }
    }

    private boolean exempt(Entity entity) {
        UUID id=entity.getUUID();
        return id.equals(actor)||id.equals(subject)||entity instanceof Player;
    }

    private void wake(ServerLevel level) {
        for(var i=asleep.entrySet().iterator();i.hasNext();){
            var row=i.next();
            if(level.getEntity(row.getKey()) instanceof Mob mob){
                awaken(mob,row.getValue());
            }
            i.remove();
            setDirty();
        }
        if(phase==OthinusPhase.RETURN && sceneReady)returnPlayers(level);
    }

    private static void awaken(Mob mob,Sleep before){
        mob.setNoAi(before.noAi);
        mob.setNoGravity(before.noGravity);
        mob.setInvisible(before.invisible);
        mob.getPersistentData().remove(SLEEP);
    }

    private void returnPlayers(ServerLevel level) {
        for(var i=relocated.entrySet().iterator();i.hasNext();){
            var row=i.next();
            var player=level.getServer().getPlayerList().getPlayer(row.getKey());
            if(player==null)continue;
            var old=row.getValue();
            if(player.level()==level && player.position().distanceToSqr(old)<radius*(double)radius*4
                    && WorldEffects.inWorld(level,BlockPos.containing(old))
                    && level.noCollision(player,player.getBoundingBox().move(old.subtract(player.position()))))
                player.teleportTo(old.x,old.y,old.z);
            i.remove();
            setDirty();
        }
    }

    private void send(ServerLevel level) {
        sent.keySet().retainAll(level.players().stream().map(Entity::getUUID).toList());
        for(var p:level.players()){
            var packet=phase!=null && (!sceneReady||phase!=OthinusPhase.RETURN)
                    && (p.position().distanceToSqr(Vec3.atCenterOf(center))<Math.pow(radius+32,2))
                    ?new Payloads.PhaseState(phase.id(),center,radius,since,sceneReady):Payloads.PhaseState.clear();
            if(!packet.equals(sent.put(p.getUUID(),packet)))PacketDistributor.sendToPlayer(p,packet);
        }
    }

    // The actors and combat rules live under the same state as the terrain.
    private void populate(ServerLevel level) {
        if(level.getEntity(subject) instanceof HumNpc original)placePad(level,original);
        switch(phase){
            case ALPHA -> {
                spawn(level,"fukiyose","pursuer",6,0,-3);
                spawn(level,"aogami","pursuer",-8,0,-3);
                spawn(level,"komoe","pursuer",4,0,-11);
                spawn(level,"yomikawa","pursuer",-9,0,3);
            }
            case BETA -> {
                spawn(level,"aogami","replacement",0,0,-9);
                spawn(level,"index","classmate",2,0,-7);
                spawn(level,"komoe","classmate",-3,0,-9);
                spawn(level,"fukiyose","classmate",4,0,-7);
                spawn(level,"tsuchimikado","classmate",-4,0,-5);
            }
            case OMEGA -> {
                spawn(level,"misaka_10032","collective",10,0,11);
                spawn(level,"misaka_10032","visitor",-10,0,-11);
                spawn(level,"misaka_worst","visitor",-14,0,-9);
                spawn(level,"last_order","visitor",-12,0,-13);
                spawn(level,"accelerator","visitor",-12,0,-16);
                spawn(level,"mikoto","visitor",10,0,-13);
                spawn(level,"kuroko","visitor",8,0,-11);
                spawn(level,"uiharu","visitor",12,0,-9);
                spawn(level,"saten","visitor",11,0,-7);
                spawn(level,"hamazura","visitor",10,0,1);
                spawn(level,"takitsubo","visitor",8,0,-2);
                spawn(level,"mugino","visitor",12,0,-3);
                spawn(level,"kinuhata","visitor",14,0,-1);
                spawn(level,"frenda","visitor",16,0,-1);
                spawn(level,"index","visitor",-8,0,0);
                spawn(level,"kanzaki","visitor",-12,0,2);
                spawn(level,"stiyl","visitor",-12,0,-2);
            }
            case BURIAL, SEA -> loopAt=level.getGameTime()+105;
            default -> { }
        }
    }

    private HumNpc spawn(ServerLevel level,String id,String role,int x,int y,int z) {
        var pos=center.offset(x,y,z);
        if(!WorldEffects.inWorld(level,pos))return null;
        var npc=BioHum.NPCS.get(id).get().create(level);
        npc.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,180,0);
        npc.setPersistenceRequired();
        npc.setMode(2,null);
        npc.setNoAi(!role.equals("pursuer"));
        if(!role.equals("subject")){
            npc.getPersistentData().putString(CAST,role);
            if(role.equals("replacement")){
                npc.setCustomName(Component.literal("上条当麻"));
                npc.setCustomNameVisible(true);
            }else if(role.equals("collective")){
                npc.setCustomName(Component.literal("御坂网络总意"));
                npc.setCustomNameVisible(true);
            }
            performers.add(npc.getUUID());
        }
        if(!level.addFreshEntity(npc)){
            performers.remove(npc.getUUID());
            return null;
        }
        return npc;
    }

    private void clearCast(ServerLevel level) {
        for(UUID id:performers){
            if(level.getEntity(id) instanceof HumNpc npc)npc.discard();
        }
        performers.clear();
    }

    public static void freezeSleeping(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if(!(event.getEntity() instanceof Mob mob)||!(mob.level() instanceof ServerLevel level))return;
        var state=overworld(level)?get(level):null;
        if(mob.getPersistentData().contains(CAST) && (state==null||!state.performers.contains(mob.getUUID()))){
            mob.discard();
            event.setCanceled(true);
        }else if(mob.getPersistentData().contains(SLEEP)){
            boolean paused=state!=null&&state.phase!=null
                    &&(state.phase.empty()||!state.sceneReady)&&state.inside(mob.position());
            if(paused)event.setCanceled(true);
            else{
                var tag=mob.getPersistentData().getCompound(SLEEP);
                awaken(mob,new Sleep(tag.getBoolean("Ai"),tag.getBoolean("Gravity"),tag.getBoolean("Invisible")));
                if(state!=null&&state.asleep.remove(mob.getUUID())!=null)state.setDirty();
            }
        }
    }

    private void behave(ServerLevel level) {
        var original=subject==null?null:level.getEntity(subject);
        var home=Vec3.atBottomCenterOf(center.offset(3,0,2));
        if(original instanceof HumNpc npc){
            if(phase.empty()){
                if(npc.getY()<center.getY()-1 || npc.distanceToSqr(home)>50)placePad(level,npc);
            }
            if(phase==OthinusPhase.BETA)npc.setTarget(null);
        }
        if(phase.empty())for(var player:level.players()){
            if(!player.isSpectator()&&relocated.containsKey(player.getUUID()) && player.getY()<center.getY()-2
                    && Math.abs(player.getX()-center.getX())<=radius && Math.abs(player.getZ()-center.getZ())<=radius){
                placePad(level,player);
            }
        }
        if(phase==OthinusPhase.ALPHA && original instanceof LivingEntity target && target.isAlive()){
            for(var id:performers)if(level.getEntity(id) instanceof HumNpc npc && npc.validTarget(target)){
                npc.setTarget(target);
                npc.getLookControl().setLookAt(target,60,45);
                npc.getNavigation().moveTo(target,1.16);
                if(npc.distanceToSqr(target)<3.8 && npc.hasLineOfSight(target) && target.invulnerableTime==0){
                    npc.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    npc.doHurtTarget(target);
                }
            }
        }
        if(phase==OthinusPhase.BETA){
            HumNpc replacement=null;
            for(var id:performers)if(level.getEntity(id) instanceof HumNpc n
                    && n.getPersistentData().getString(CAST).equals("replacement"))replacement=n;
            if(replacement!=null)for(var id:performers){
                if(level.getEntity(id) instanceof HumNpc npc && npc!=replacement){
                    npc.getLookControl().setLookAt(replacement,35,30);
                    npc.setYHeadRot(CastMotion.yaw(npc.position(),replacement.position(),npc.getYHeadRot()));
                }
            }
        }
        if(phase==OthinusPhase.OMEGA){
            for(var id:performers)if(level.getEntity(id) instanceof HumNpc npc){
                npc.setTarget(null);
                npc.heal(2);
            }
            for(var p:level.players())if(inside(p.position()))p.heal(1);
        }
        if(phase==OthinusPhase.BURIAL && original instanceof HumNpc n){
            n.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,28,2,false,false));
        }
    }

    public static boolean interact(HumNpc npc,ServerPlayer p) {
        if(!(npc.level() instanceof ServerLevel level)||!overworld(level))return false;
        var state=get(level);
        if(!state.sceneReady||!state.inside(npc.position()))return false;
        String role=npc.getPersistentData().getString(CAST);
        switch(role){
            case "collective" -> {
                if(state.phase!=OthinusPhase.OMEGA)return false;
                state.networkSpoken=true;
                state.setDirty();
                npc.tell(p,"我们还记得那个世界。想回去的话，就把这句话告诉她。");
                return true;
            }
            case "classmate" -> {
                npc.tell(p,"上条在那边。大家都在等他。");
                return true;
            }
            case "replacement" -> {
                npc.tell(p,"我就是上条当麻。今天放学后还得回宿舍。");
                return true;
            }
            case "pursuer" -> {
                npc.tell(p,"这里没有他的容身之处。");
                return true;
            }
            case "visitor" -> {
                npc.tell(p,"大家都来了。前面就是御坂妹们的摊位。");
                return true;
            }
            default -> {
                if(!npc.getUUID().equals(state.subject)||state.phase!=OthinusPhase.DUEL||!state.arrowsComplete)return false;
                state.offered=true;
                state.setDirty();
                npc.tell(p,"我想回到我们原来的世界。用我的右手做那个基准吧。");
                return true;
            }
        }
    }

    static boolean protect(LivingIncomingDamageEvent event) {
        var entity=event.getEntity();
        if(!(entity.level() instanceof ServerLevel level)||!overworld(level))return false;
        var state=get(level);
        if(state.phase==null||!state.inside(entity.position()))return false;
        boolean peaceful=state.phase==OthinusPhase.OMEGA &&
                (event.getSource().getEntity()!=null||event.getSource().getDirectEntity()!=null);
        boolean held=state.asleep.containsKey(entity.getUUID());
        if(peaceful||held){
            event.setCanceled(true);
            return true;
        }
        boolean lastArrow=state.phase==OthinusPhase.DUEL&&state.arrowsComplete
                &&event.getSource().getEntity()!=null&&event.getSource().getEntity().getUUID().equals(state.actor);
        if(entity.getUUID().equals(state.subject)&&(lastArrow||event.getAmount()>=entity.getHealth())){
            event.setCanceled(true);
            if(state.phase==OthinusPhase.DUEL&&!state.arrowsComplete){
                state.reset(level,state.caster(level));
            }else entity.setHealth(1);
            return true;
        }
        return false;
    }

    private void reset(ServerLevel level,HumNpc caster) {
        attempts++;
        if(subject!=null&&level.getEntity(subject) instanceof HumNpc original){
            placePad(level,original);
            original.setHealth(original.getMaxHealth());
        }
        if(caster!=null){
            Vec3 p=Vec3.atCenterOf(center);
            Payloads.cue(caster,"othinus_duel_reset",Payloads.Cue.FIELD,p,p,24);
        }
        setDirty();
    }

    static boolean gungnir(HumNpc npc,Skills.Skill skill,LivingEntity target) {
        if(!(npc.level() instanceof ServerLevel level))return false;
        var state=get(level);
        if(state.phase!=OthinusPhase.DUEL||!npc.getUUID().equals(state.actor))return false;
        if(state.attack==null && !state.spearBroken){
            state.attack=OthinusDuel.spear(npc,state,target,skill);
            state.setDirty();
        }
        return true;
    }

    static boolean spearAvailable(HumNpc npc){
        if(!(npc.level() instanceof ServerLevel level)||!overworld(level))return true;
        var state=get(level);
        return state.phase!=OthinusPhase.DUEL||!npc.getUUID().equals(state.actor)
                ||state.sceneReady&&state.attack==null&&!state.spearBroken;
    }

    void brokenSpear(ServerLevel level,HumNpc caster,Vec3 at){
        spearBroken=true;
        caster.getPersistentData().putBoolean("BioHumBrokenGungnir",true);
        if(caster.getMainHandItem().is(BioHum.GUNGNIR.get()))
            caster.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.item.ItemStack.EMPTY);
        Payloads.cue(caster,"gungnir",Payloads.Cue.CANCEL,at,at,2.5f);
        level.playSound(null,BlockPos.containing(at),SoundEvents.GLASS_BREAK,SoundSource.PLAYERS,1.7f,.61f);
        setDirty();
    }

    void finishedArrows(){
        arrowsComplete=true;
        setDirty();
    }

    boolean isSubject(LivingEntity target){return target!=null&&target.getUUID().equals(subject);}

    HumNpc subjectEntity(ServerLevel level){
        return subject!=null && level.getEntity(subject) instanceof HumNpc n && n.isAlive()?n:null;
    }

    UUID issuer(){return issuer;}

    static boolean guardPrepared(HumNpc npc){return npc.guardUntil>npc.level().getGameTime();}

    static boolean hidden(LivingEntity target) {
        if(!(target.level() instanceof ServerLevel level)||!overworld(level))return false;
        var state=get(level);
        return state.asleep.containsKey(target.getUUID())||state.phase==OthinusPhase.BETA&&state.isSubject(target);
    }

    boolean carve(ServerLevel level,BlockPos pos){
        return change(level,pos,Blocks.AIR.defaultBlockState());
    }

    boolean crack(ServerLevel level,BlockPos pos){
        var actual=level.getBlockState(pos);
        if(actual.isAir())return false;
        return change(level,pos,Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState());
    }

    private static OthinusRewrite read(CompoundTag tag,HolderLookup.Provider lookup) {
        var state=new OthinusRewrite();
        state.phase=OthinusPhase.read(tag.getString("Phase"));
        state.center=BlockPos.of(tag.getLong("Center"));
        state.radius=tag.getInt("Radius");
        state.minY=tag.contains("MinY")?tag.getInt("MinY"):state.center.getY()-4;
        state.maxY=tag.contains("MaxY")?tag.getInt("MaxY"):state.center.getY()+44;
        state.actor=tag.hasUUID("Actor")?tag.getUUID("Actor"):null;
        state.issuer=tag.hasUUID("Issuer")?tag.getUUID("Issuer"):null;
        state.subject=tag.hasUUID("Subject")?tag.getUUID("Subject"):null;
        state.subjectHome=vector(tag.getCompound("SubjectHome"));
        state.actorHome=tag.contains("ActorHome")?vector(tag.getCompound("ActorHome")):Vec3.atBottomCenterOf(state.center);
        state.subjectHealth=tag.getFloat("SubjectHealth");
        state.createdSubject=tag.getBoolean("CreatedSubject");
        state.sceneReady=tag.getBoolean("Ready");
        state.since=tag.getLong("Since");
        state.deadline=tag.getLong("Deadline");
        state.loopAt=tag.getLong("LoopAt");
        state.attempts=tag.getInt("Attempts");
        state.networkSpoken=tag.getBoolean("Network");
        state.spearBroken=tag.getBoolean("Spear");
        state.arrowsComplete=tag.getBoolean("Arrows");
        state.offered=tag.getBoolean("Offered");
        for(long k:tag.getLongArray("Finished"))state.finished.add(k);
        var blocks=lookup.lookupOrThrow(Registries.BLOCK);
        for(Tag entry:tag.getList("Chunks",Tag.TAG_COMPOUND)){
            var chunk=(CompoundTag)entry;
            long key=chunk.getLong("Chunk");
            var palette=new ArrayList<BlockState>();
            for(Tag block:chunk.getList("Palette",Tag.TAG_COMPOUND))
                palette.add(NbtUtils.readBlockState(blocks,(CompoundTag)block));
            long[] positions=chunk.getLongArray("Positions");
            int[] ids=chunk.getIntArray("States");
            var leases=new LinkedHashMap<BlockPos,Lease>();
            for(int i=0;i<positions.length&&i*2+1<ids.length;i++){
                leases.put(BlockPos.of(positions[i]),new Lease(palette.get(ids[i*2]),palette.get(ids[i*2+1])));
            }
            if(!leases.isEmpty())state.leases.put(key,leases);
            var edits=new HashSet<BlockPos>();
            for(long p:chunk.getLongArray("PlayerChanges"))edits.add(BlockPos.of(p));
            if(!edits.isEmpty())state.playerChanges.put(key,edits);
        }
        for(Tag row:tag.getList("Cast",Tag.TAG_COMPOUND)){
            var v=(CompoundTag)row;
            state.performers.add(v.getUUID("Id"));
        }
        for(Tag row:tag.getList("Sleeping",Tag.TAG_COMPOUND)){
            var v=(CompoundTag)row;
            state.asleep.put(v.getUUID("Id"),new Sleep(v.getBoolean("Ai"),v.getBoolean("Gravity"),v.getBoolean("Invisible")));
        }
        for(Tag row:tag.getList("Players",Tag.TAG_COMPOUND)){
            var v=(CompoundTag)row;
            state.relocated.put(v.getUUID("Id"),vector(v));
        }
        if(tag.contains("Weather")){
            var v=tag.getCompound("Weather");
            state.weather=new Weather(v.getLong("Time"),v.getInt("Clear"),v.getInt("Rain"),
                    v.getInt("Thunder"),v.getBoolean("Raining"),v.getBoolean("Thundering"));
        }
        if(tag.contains("Attack"))state.attack=OthinusDuel.Attack.read(tag.getCompound("Attack"));
        return state;
    }

    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        if(phase!=null)tag.putString("Phase",phase.id());
        else if(relocated.isEmpty())return tag;
        tag.putLong("Center",center.asLong());
        tag.putInt("Radius",radius);
        tag.putInt("MinY",minY);
        tag.putInt("MaxY",maxY);
        if(actor!=null)tag.putUUID("Actor",actor);
        if(issuer!=null)tag.putUUID("Issuer",issuer);
        if(subject!=null)tag.putUUID("Subject",subject);
        tag.put("SubjectHome",vector(subjectHome));
        tag.put("ActorHome",vector(actorHome));
        tag.putFloat("SubjectHealth",subjectHealth);
        tag.putBoolean("CreatedSubject",createdSubject);
        tag.putBoolean("Ready",sceneReady);
        tag.putLong("Since",since);
        tag.putLong("Deadline",deadline);
        tag.putLong("LoopAt",loopAt);
        tag.putInt("Attempts",attempts);
        tag.putBoolean("Network",networkSpoken);
        tag.putBoolean("Spear",spearBroken);
        tag.putBoolean("Arrows",arrowsComplete);
        tag.putBoolean("Offered",offered);
        tag.putLongArray("Finished",finished.stream().mapToLong(Long::longValue).toArray());
        var keys=new HashSet<>(leases.keySet());
        keys.addAll(playerChanges.keySet());
        var chunks=new ListTag();
        for(long key:keys){
            var chunk=new CompoundTag();
            chunk.putLong("Chunk",key);
            var indexed=new LinkedHashMap<BlockState,Integer>();
            var blocks=leases.get(key);
            if(blocks!=null){
                long[] positions=new long[blocks.size()];
                int[] states=new int[blocks.size()*2];
                int i=0;
                for(var row:blocks.entrySet()){
                    positions[i]=row.getKey().asLong();
                    states[i*2]=indexed.computeIfAbsent(row.getValue().original,k->indexed.size());
                    states[i*2+1]=indexed.computeIfAbsent(row.getValue().applied,k->indexed.size());
                    i++;
                }
                var palette=new ListTag();
                for(var block:indexed.keySet())palette.add(NbtUtils.writeBlockState(block));
                chunk.put("Palette",palette);
                chunk.putLongArray("Positions",positions);
                chunk.putIntArray("States",states);
            }
            var edits=playerChanges.get(key);
            if(edits!=null)chunk.putLongArray("PlayerChanges",edits.stream().mapToLong(BlockPos::asLong).toArray());
            chunks.add(chunk);
        }
        tag.put("Chunks",chunks);
        var cast=new ListTag();
        for(var id:performers){
            var row=new CompoundTag();
            row.putUUID("Id",id);
            cast.add(row);
        }
        tag.put("Cast",cast);
        var sleeping=new ListTag();
        asleep.forEach((id,value)->{
            var row=new CompoundTag();
            row.putUUID("Id",id);
            row.putBoolean("Ai",value.noAi);
            row.putBoolean("Gravity",value.noGravity);
            row.putBoolean("Invisible",value.invisible);
            sleeping.add(row);
        });
        tag.put("Sleeping",sleeping);
        var players=new ListTag();
        relocated.forEach((id,pos)->{
            var row=vector(pos);
            row.putUUID("Id",id);
            players.add(row);
        });
        tag.put("Players",players);
        if(weather!=null){
            var row=new CompoundTag();
            row.putLong("Time",weather.time);
            row.putInt("Clear",weather.clear);
            row.putInt("Rain",weather.rain);
            row.putInt("Thunder",weather.thunder);
            row.putBoolean("Raining",weather.raining);
            row.putBoolean("Thundering",weather.thundering);
            tag.put("Weather",row);
        }
        if(attack!=null)tag.put("Attack",attack.save());
        return tag;
    }

    static Vec3 vector(CompoundTag tag) {
        return new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
    }

    static CompoundTag vector(Vec3 v) {
        var tag=new CompoundTag();
        tag.putDouble("X",v.x);
        tag.putDouble("Y",v.y);
        tag.putDouble("Z",v.z);
        return tag;
    }
}
