package cn.academy.biohum;

import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Durable environmental effects. The per-tick budget only limits work in one tick. */
public final class WorldEffects extends SavedData {
    private static final Factory<WorldEffects> FACTORY = new Factory<>(WorldEffects::new, WorldEffects::read);
    private static final int WORK_PER_TICK = 384;
    private static final int DEFAULT_RESTORE_TICKS = 240;

    public enum Change { BREAK, SCORCH, FREEZE, FORM, UPLIFT, IGNITE, FLOOD, IRON, CARBON, RELIC, PRESERVE, FORTRESS, CITADEL }
    public enum RegionKind { TERRITORY, WONDERLAND, SHADOW, CROSS, MANIFEST }
    public record Source(UUID actor, UUID issuer, String skill, long cast) {
        public Source(UUID actor,UUID issuer,String skill){this(actor,issuer,skill,0);}
        public static Source of(HumNpc actor, UUID issuer) {
            return new Source(actor.getUUID(),issuer,actor.technique(),actor.techniqueStarted());
        }
    }
    private static final class TerrainSample {
        final BlockPos first;
        BlockPos last;
        TerrainSample(BlockPos pos){first=pos;last=pos;}
    }
    private record Lease(BlockState original, BlockState applied, long until) {}
    private record Sky(long oldTime, long newTime, long started, long until, boolean wasRaining,
                       boolean wasThundering) {}
    private record Region(Vec3 center, double radius, RegionKind kind, UUID owner, long until) {
        boolean contains(Vec3 position) {
            return center.distanceToSqr(position) <= radius * radius;
        }
    }
    private record Area(Source source, BlockPos low, BlockPos high, Vec3 center, Vec3 end, double radius,
                        double strength, Change change, boolean temporary, int duration, long cursor) {
        long width() { return (long) high.getX() - low.getX() + 1; }
        long depth() { return (long) high.getZ() - low.getZ() + 1; }
        long height() { return (long) high.getY() - low.getY() + 1; }
        long volume() { return width() * depth() * height(); }
        Area progress(long next) {
            return new Area(source, low, high, center, end, radius, strength, change, temporary, duration, next);
        }
        boolean covers(BlockPos pos) {
            Vec3 point = Vec3.atCenterOf(pos).multiply(1, 0, 1);
            Vec3 start = center.multiply(1, 0, 1);
            if (end == null) return start.distanceToSqr(point) <= radius * radius;
            Vec3 delta = end.multiply(1, 0, 1).subtract(start);
            double t = delta.lengthSqr() == 0 ? 0 : Math.clamp(point.subtract(start).dot(delta) / delta.lengthSqr(), 0, 1);
            return start.add(delta.scale(t)).distanceToSqr(point) <= radius * radius;
        }
        BlockState fortress(BlockPos pos) {
            int dx=Math.abs(pos.getX()-Mth.floor(center.x));
            int dz=Math.abs(pos.getZ()-Mth.floor(center.z));
            int y=pos.getY()-Mth.floor(center.y);
            int r=(int)radius;
            int distance=dx+dz, width=Math.max(dx,dz);
            if (change == Change.FORTRESS) {
                boolean legacy = y < 0 ? width <= r/3 : y == 0 ? distance <= r+4
                        : width <= 2 && y <= 6 || distance >= r+2 && distance <= r+4 && y == 1
                        || y <= 3 && width == r-2 && Math.min(dx,dz) <= 2;
                return legacy ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : null;
            }
            if (y < 0) {
                if (distance > r + 4 + y * 4) return null;
                boolean rib = Math.min(dx,dz) <= 1 || distance >= r + 1 + y * 4;
                return (rib ? Blocks.STONE_BRICKS : Blocks.SMOOTH_QUARTZ).defaultBlockState();
            }
            if (y == 0 && distance <= r + 4) {
                return (distance >= r + 2 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_QUARTZ).defaultBlockState();
            }
            if (distance >= r + 2 && distance <= r + 4 && (y == 1 || y == 2 && dx % 4 < 2)) {
                return (y == 1 && distance == r+2 && dx%5==0 ? Blocks.GLOWSTONE : Blocks.STONE_BRICKS).defaultBlockState();
            }
            int spire = y <= 5 ? 5 : y <= 8 ? 3 : y <= 11 ? 1 : 0;
            if (width <= spire) {
                boolean pillar = width == spire && dx > 0 && dz > 0;
                return (pillar ? Blocks.QUARTZ_PILLAR : Blocks.SMOOTH_QUARTZ).defaultBlockState();
            }
            int tower = Math.max(Math.abs(width - (r-2)), Math.min(dx,dz));
            if (tower <= 2 && y <= 5 && (y <= 2 || tower == 2)) {
                return (y == 5 ? Blocks.GLOWSTONE : Blocks.STONE_BRICKS).defaultBlockState();
            }
            return null;
        }
    }

    private final Map<BlockPos, Lease> leases = new LinkedHashMap<>();
    private final ArrayDeque<Area> jobs = new ArrayDeque<>();
    private final List<Region> regions = new ArrayList<>();
    private boolean temporary;
    private boolean changing;
    private Sky sky;

    public static WorldEffects get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, "acabiohum_environment");
    }

    public static boolean temporary(ServerLevel level) {
        return get(level.getServer().overworld()).temporary;
    }

    public static void setTemporary(ServerLevel level, boolean value) {
        var config = get(level.getServer().overworld());
        config.temporary = value;
        config.setDirty();
    }

    static boolean inWorld(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos);
    }

    public static boolean edit(ServerLevel level, Source source, BlockPos pos, Change kind, double strength) {
        return get(level).apply(level, source, pos, kind, strength, temporary(level), DEFAULT_RESTORE_TICKS);
    }

    static boolean edit(ServerLevel level,Source source,BlockPos pos,Change kind,double strength,int duration){
        return get(level).apply(level,source,pos,kind,strength,temporary(level),duration);
    }

    static boolean preserveTemperature(ServerLevel level,Source source,BlockPos pos,int duration) {
        if(!inWorld(level,pos))return false;
        var state=level.getBlockState(pos);
        if(!state.is(Blocks.ICE)&&!state.is(Blocks.FROSTED_ICE)&&!state.is(Blocks.SNOW_BLOCK))return false;
        return get(level).apply(level,source,pos,Change.PRESERVE,4,true,duration);
    }

    static boolean moveBlock(ServerLevel level,Source source,BlockPos from,BlockPos to,int duration) {
        return get(level).relocate(level,source,from,to,duration);
    }

    private boolean relocate(ServerLevel level,Source source,BlockPos from,BlockPos to,int duration) {
        if(from.equals(to)||!inWorld(level,from)||!inWorld(level,to))return false;
        if(leases.containsKey(from)||leases.containsKey(to))return false;
        var moving=level.getBlockState(from);
        var air=level.getBlockState(to);
        if(!moving.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
                ||moving.getDestroySpeed(level,from)<0||moving.getDestroySpeed(level,from)>8
                ||moving.hasBlockEntity()||!air.isAir()||!level.getEntities(null,new net.minecraft.world.phys.AABB(to)).isEmpty())return false;
        var player=interactor(level,source,from);
        if(player==null||!level.mayInteract(player,to))return false;
        var snapshot=BlockSnapshot.create(level.dimension(),level,to);
        boolean temporary=temporary(level);
        changing=true;
        try{
            if(NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,from,moving,player)).isCanceled())return false;
            if(!level.setBlock(to,moving,Block.UPDATE_ALL))return false;
            if(NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(to.below()),player)).isCanceled()
                    ||!level.setBlock(from,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL)){
                snapshot.restore(Block.UPDATE_ALL);
                return false;
            }
            if(temporary){
                lease(from,moving,Blocks.AIR.defaultBlockState(),level.getGameTime()+duration);
                lease(to,air,moving,level.getGameTime()+duration);
            }else{
                leases.remove(from);
                leases.remove(to);
            }
            setDirty();
            return true;
        }finally{
            changing=false;
        }
    }

    private void lease(BlockPos pos,BlockState old,BlockState next,long until) {
        var previous=leases.get(pos);
        var original=previous!=null&&old.equals(previous.applied)?previous.original:old;
        leases.put(pos.immutable(),new Lease(original,next,until));
    }

    public static void area(ServerLevel level, Source source, Vec3 center, double radius, double height,
                            Change change, double strength, int duration) {
        var low = BlockPos.containing(Math.max(-29_999_984, center.x - radius),
                Math.max(level.getMinBuildHeight(), center.y - height), Math.max(-29_999_984, center.z - radius));
        var high = BlockPos.containing(Math.min(29_999_983, center.x + radius),
                Math.min(level.getMaxBuildHeight() - 1, center.y + height), Math.min(29_999_983, center.z + radius));
        if (low.getX() > high.getX() || low.getY() > high.getY() || low.getZ() > high.getZ()) return;
        enqueue(level, source, low, high, center, null, radius, strength, change, duration);
    }

    static void fortress(ServerLevel level, Source source, Vec3 center, double radius, double strength, int duration) {
        fortressSection(level,source,center,radius,0,2,radius+4,strength,duration);
        fortressSection(level,source,center,radius,-7,-1,radius,strength,duration);
        fortressSection(level,source,center,radius,3,5,5,strength,duration);
        fortressSection(level,source,center,radius,6,12,3,strength,duration);
        for (var direction:net.minecraft.core.Direction.Plane.HORIZONTAL) {
            Vec3 tower=center.add(direction.getStepX()*(radius-2),0,direction.getStepZ()*(radius-2));
            fortressSection(level,source,center,radius,tower,3,5,2,strength,duration);
        }
    }

    private static void fortressSection(ServerLevel level, Source source, Vec3 center, double radius,
                                        int bottom, int top, double width, double strength, int duration) {
        fortressSection(level,source,center,radius,center,bottom,top,width,strength,duration);
    }

    private static void fortressSection(ServerLevel level, Source source, Vec3 center, double radius, Vec3 section,
                                        int bottom, int top, double width, double strength, int duration) {
        var low = BlockPos.containing(section.x-width,Math.max(level.getMinBuildHeight(),center.y+bottom),section.z-width);
        var high = BlockPos.containing(section.x+width,Math.min(level.getMaxBuildHeight()-1,center.y+top),section.z+width);
        if (low.getY() <= high.getY()) {
            enqueue(level,source,low,high,center,null,radius,strength,Change.CITADEL,duration);
        }
    }

    static void plane(ServerLevel level, Source source, Vec3 from, Vec3 to, double strength, int duration) {
        BlockPos low = BlockPos.containing(Math.min(from.x, to.x) - 1, Math.max(level.getMinBuildHeight(), from.y - 2),
                Math.min(from.z, to.z) - 1);
        BlockPos high = BlockPos.containing(Math.max(from.x, to.x) + 1, Math.min(level.getMaxBuildHeight() - 1, from.y + 2),
                Math.max(from.z, to.z) + 1);
        enqueue(level, source, low, high, from, to, .78, strength, Change.BREAK, duration);
    }

    private static void enqueue(ServerLevel level, Source source, BlockPos low, BlockPos high, Vec3 center, Vec3 end,
                                double radius, double strength, Change change, int duration) {
        var data = get(level);
        boolean temporary = temporary(level);
        var border=level.getWorldBorder();
        int firstX = Math.max(low.getX() >> 4, Mth.floor(border.getMinX()) >> 4);
        int lastX = Math.min(high.getX() >> 4, Mth.ceil(border.getMaxX()) >> 4);
        int firstZ = Math.max(low.getZ() >> 4, Mth.floor(border.getMinZ()) >> 4);
        int lastZ = Math.min(high.getZ() >> 4, Mth.ceil(border.getMaxZ()) >> 4);
        for (int x = firstX; x <= lastX; x++) for (int z = firstZ; z <= lastZ; z++) {
            if (!level.getChunkSource().hasChunk(x, z)) continue;
            var start = new BlockPos(Math.max(x << 4, low.getX()), low.getY(), Math.max(z << 4, low.getZ()));
            var stop = new BlockPos(Math.min((x << 4) + 15, high.getX()), high.getY(),
                    Math.min((z << 4) + 15, high.getZ()));
            data.jobs.add(new Area(source, start, stop, center, end, radius, strength, change, temporary,
                    duration == 0 ? DEFAULT_RESTORE_TICKS : duration, 0));
        }
        data.setDirty();
    }

    /** Restore the disposable QA stage between casts using the same guarded rollback as a timeout. */
    public void restorePreview(ServerLevel level) {
        jobs.clear();
        for (var entry : leases.entrySet()) {
            if (inWorld(level,entry.getKey()) && level.getBlockState(entry.getKey()).equals(entry.getValue().applied)) {
                level.setBlock(entry.getKey(),entry.getValue().original,Block.UPDATE_ALL);
            }
        }
        leases.clear();
        setDirty();
    }

    static void region(ServerLevel level, Vec3 at, double radius, RegionKind kind, UUID owner, int duration) {
        var data = get(level);
        data.regions.removeIf(region -> region.owner.equals(owner) && region.kind == kind);
        data.regions.add(new Region(at, radius, kind, owner, level.getGameTime() + duration));
        data.setDirty();
    }

    static boolean inside(ServerLevel level, Vec3 at, RegionKind kind) {
        return get(level).regions.stream().anyMatch(region ->
                region.kind == kind && region.until > level.getGameTime() && region.contains(at));
    }

    static void endRegion(ServerLevel level,UUID owner,RegionKind kind) {
        var data=get(level);
        if(data.regions.removeIf(region->region.owner.equals(owner)&&region.kind==kind))data.setDirty();
    }

    static void sky(ServerLevel level, long time, boolean storm, int duration) {
        var data = get(level);
        if (temporary(level)) {
            long old = data.sky == null ? level.getDayTime()
                    : data.sky.oldTime + (level.getGameTime() - data.sky.started);
            data.sky = new Sky(old, time, level.getGameTime(), level.getGameTime() + duration,
                    data.sky == null ? level.isRaining() : data.sky.wasRaining,
                    data.sky == null ? level.isThundering() : data.sky.wasThundering);
        } else data.sky = null;
        level.setDayTime(time);
        level.setWeatherParameters(storm ? 0 : duration, storm ? duration : 0, storm, storm);
        data.setDirty();
    }

    private boolean apply(ServerLevel level, Source source, BlockPos pos, Change kind, double strength,
                          boolean temporaryEdit, int duration) {
        return apply(level,source,pos,kind,strength,temporaryEdit,duration,null);
    }

    private boolean apply(ServerLevel level, Source source, BlockPos pos, Change kind, double strength,
                          boolean temporaryEdit, int duration, BlockState fortress) {
        if (!inWorld(level, pos)) return false;
        BlockState old = level.getBlockState(pos);
        float hardness = old.getDestroySpeed(level, pos);
        if (hardness < 0 || kind!=Change.FREEZE&&hardness>strength
                || temporaryEdit&&old.hasBlockEntity()) return false;
        BlockState next = switch (kind) {
            case BREAK -> Blocks.AIR.defaultBlockState();
            case SCORCH -> old.is(Blocks.STONE) || old.is(Blocks.COBBLESTONE)
                    ? Blocks.BASALT.defaultBlockState() : Blocks.AIR.defaultBlockState();
            case FREEZE -> old.is(Blocks.WATER) && old.getFluidState().isSource()
                    ? Blocks.ICE.defaultBlockState() : old;
            case FORM -> old.canBeReplaced() ? Blocks.AMETHYST_BLOCK.defaultBlockState() : old;
            case IRON -> old.canBeReplaced() ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : old;
            case CARBON -> old.canBeReplaced() ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : old;
            case RELIC -> old.canBeReplaced() ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : old;
            case PRESERVE -> old.is(Blocks.SNOW_BLOCK)?Blocks.WHITE_CONCRETE.defaultBlockState():Blocks.PACKED_ICE.defaultBlockState();
            case FORTRESS, CITADEL -> old.canBeReplaced() && fortress != null ? fortress : old;
            case UPLIFT -> old.canBeReplaced() ? Blocks.STONE.defaultBlockState() : old;
            case IGNITE -> old.isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(),
                    net.minecraft.core.Direction.UP) ? (temporaryEdit ? Blocks.MAGMA_BLOCK : Blocks.FIRE).defaultBlockState() : old;
            case FLOOD -> old.canBeReplaced() ? (temporaryEdit ? Blocks.ICE : Blocks.WATER).defaultBlockState() : old;
        };
        if (old.equals(next)) return false;
        ServerPlayer player=interactor(level,source,pos);
        if(player==null)return false;
        changing = true;
        try {
            if (!old.isAir() && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, old, player)).isCanceled()) {
                return false;
            }
            var snapshot = next.isAir() ? null : BlockSnapshot.create(level.dimension(), level, pos);
            if (!level.setBlock(pos, next, Block.UPDATE_ALL)) return false;
            if (snapshot != null && NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot,
                    level.getBlockState(pos.below()), player)).isCanceled()) {
                snapshot.restore(Block.UPDATE_ALL);
                return false;
            }
            if (temporaryEdit) {
                lease(pos,old,next,level.getGameTime()+duration);
            } else leases.remove(pos);
            setDirty();
            return true;
        } finally {
            changing = false;
        }
    }

    static ServerPlayer interactor(ServerLevel level,Source source,BlockPos pos) {
        ServerPlayer player;
        if(source.issuer!=null){
            player=level.getServer().getPlayerList().getPlayer(source.issuer);
            if(player==null||player.level()!=level)return null;
        }else{
            if(!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return null;
            player=FakePlayerFactory.get(level,new GameProfile(source.actor,"[BioHum]"));
        }
        return level.mayInteract(player,pos)?player:null;
    }

    public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) get(level).advance(level);
    }

    private void advance(ServerLevel level) {
        long now = level.getGameTime();
        if (regions.removeIf(region -> region.until <= now)) setDirty();
        if (now % 10 == 0) {
            for (var region : regions) {
                if (region.kind == RegionKind.TERRITORY||region.kind==RegionKind.MANIFEST
                        || !level.hasChunkAt(BlockPos.containing(region.center))) continue;
                var owner=level.getEntity(region.owner);
                for (var e : level.getEntitiesOfClass(LivingEntity.class,
                        new net.minecraft.world.phys.AABB(region.center, region.center).inflate(region.radius),
                        entity -> entity.isAlive() && region.contains(entity.position()))) {
                    if(region.kind==RegionKind.CROSS){
                        if(e==owner||owner instanceof HumNpc npc&&npc.ally(e)){
                            e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,22,0,false,false));
                            e.addEffect(new MobEffectInstance(MobEffects.LUCK,22,0,false,false));
                        }
                    }else{
                        var effect=region.kind==RegionKind.WONDERLAND?MobEffects.SLOW_FALLING:MobEffects.BLINDNESS;
                        e.addEffect(new MobEffectInstance(effect,22,0,false,false));
                    }
                }
            }
        }
        if (sky != null && sky.until <= now) {
            long elapsed = now - sky.started;
            long progressed = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) ? elapsed : 0;
            if (level.getDayTime() == sky.newTime + progressed) {
                level.setDayTime(sky.oldTime + progressed);
                level.setWeatherParameters(6000, sky.wasRaining ? 6000 : 0, sky.wasRaining, sky.wasThundering);
            }
            sky = null;
            setDirty();
        }
        var changes = leases.entrySet().iterator();
        int restored = 0;
        while (changes.hasNext() && restored < WORK_PER_TICK) {
            var change = changes.next();
            var lease = change.getValue();
            if (lease.until > now || !inWorld(level, change.getKey())) continue;
            if (level.getBlockState(change.getKey()).equals(lease.applied)) {
                level.setBlock(change.getKey(), lease.original, Block.UPDATE_ALL);
            }
            changes.remove();
            restored++;
            setDirty();
        }
        int remaining = WORK_PER_TICK;
        while (remaining > 0 && !jobs.isEmpty()) {
            Area area = jobs.removeFirst();
            long end = Math.min(area.volume(), area.cursor + Math.min(remaining, 96));
            Map<Long,TerrainSample> changed=null;
            for (long index = area.cursor; index < end; index++) {
                var pos = new BlockPos(area.low.getX() + (int) (index % area.width()),
                        area.low.getY() + (int) (index / (area.width() * area.depth())),
                        area.low.getZ() + (int) ((index / area.width()) % area.depth()));
                boolean building=area.change==Change.FORTRESS || area.change==Change.CITADEL;
                var masonry=building?area.fortress(pos):null;
                boolean inside=building?masonry!=null:area.covers(pos);
                if (inside&&apply(level,area.source,pos,area.change,area.strength,area.temporary,area.duration,masonry)) {
                    if(!area.source.skill.isEmpty()){
                        if(changed==null)changed=new LinkedHashMap<>();
                        changed.computeIfAbsent(ChunkPos.asLong(pos),key->new TerrainSample(pos)).last=pos;
                    }
                }
            }
            if(changed!=null&&Skills.get(area.source.skill)!=null)for(var batch:changed.values())
                Payloads.cue(level,area.source.actor,area.source.skill,area.source.cast,Payloads.Cue.TERRAIN,
                        Vec3.atCenterOf(batch.first),Vec3.atCenterOf(batch.last),1,area.change.ordinal(),null);
            if (end < area.volume()) jobs.addLast(area.progress(end));
            remaining -= (int) (end - area.cursor);
            setDirty();
        }
    }

    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer
                && event.getLevel() instanceof ServerLevel level) get(level).forget(event.getPos());
    }


    public static void broken(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            var data = get(level);
            // For real player breaks, suppress drops before releasing the terrain lease.
            if (!data.changing && data.leases.containsKey(event.getPos())
                    && event.getState().equals(data.leases.get(event.getPos()).applied)) return;
            data.forget(event.getPos());
        }
    }

    public static void drops(BlockDropsEvent event) {
        var data = get(event.getLevel());
        var lease = data.leases.get(event.getPos());
        if (!data.changing && lease != null && lease.applied.equals(event.getState())) {
            event.getDrops().clear();
            event.setDroppedExperience(0);
            data.forget(event.getPos());
        }
    }

    private void forget(BlockPos pos) {
        if(!changing&&leases.remove(pos)!=null)setDirty();
    }

    private static WorldEffects read(CompoundTag tag, HolderLookup.Provider lookup) {
        var result = new WorldEffects();
        result.temporary = tag.getBoolean("Temporary");
        var blocks = lookup.lookupOrThrow(Registries.BLOCK);
        for (var element : tag.getList("Leases", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            result.leases.put(BlockPos.of(row.getLong("Pos")), new Lease(
                    NbtUtils.readBlockState(blocks, row.getCompound("Original")),
                    NbtUtils.readBlockState(blocks, row.getCompound("Applied")), row.getLong("Until")));
        }
        for (var element : tag.getList("Areas", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            if (!row.hasUUID("Actor")) continue;
            var source = new Source(row.getUUID("Actor"), row.hasUUID("Issuer") ? row.getUUID("Issuer") : null,
                    row.getString("Skill"),row.getLong("Cast"));
            var end = row.contains("EndX") ? new Vec3(row.getDouble("EndX"), row.getDouble("EndY"), row.getDouble("EndZ")) : null;
            result.jobs.add(new Area(source, BlockPos.of(row.getLong("Low")), BlockPos.of(row.getLong("High")),
                    new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z")), end, row.getDouble("Radius"),
                    row.getDouble("Strength"), Change.valueOf(row.getString("Change")), row.getBoolean("Temporary"),
                    row.getInt("Duration"), row.getLong("Cursor")));
        }
        for (var element : tag.getList("Regions", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) element;
            result.regions.add(new Region(new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z")),
                    row.getDouble("Radius"), RegionKind.valueOf(row.getString("Kind")), row.getUUID("Owner"),
                    row.getLong("Until")));
        }
        if (tag.contains("Sky")) {
            var row = tag.getCompound("Sky");
            result.sky = new Sky(row.getLong("Old"), row.getLong("New"), row.getLong("Started"), row.getLong("Until"),
                    row.getBoolean("Rain"), row.getBoolean("Thunder"));
        }
        return result;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("Temporary", temporary);
        var saved = new ListTag();
        leases.forEach((pos, lease) -> {
            var row = new CompoundTag();
            row.putLong("Pos", pos.asLong());
            row.put("Original", NbtUtils.writeBlockState(lease.original));
            row.put("Applied", NbtUtils.writeBlockState(lease.applied));
            row.putLong("Until", lease.until);
            saved.add(row);
        });
        tag.put("Leases", saved);
        var queued = new ListTag();
        for (var area : jobs) {
            var row = new CompoundTag();
            row.putUUID("Actor", area.source.actor);
            if (area.source.issuer != null) row.putUUID("Issuer", area.source.issuer);
            row.putString("Skill",area.source.skill);
            row.putLong("Cast",area.source.cast);
            row.putLong("Low", area.low.asLong());
            row.putLong("High", area.high.asLong());
            row.putDouble("X", area.center.x);
            row.putDouble("Y", area.center.y);
            row.putDouble("Z", area.center.z);
            if (area.end != null) {
                row.putDouble("EndX", area.end.x);
                row.putDouble("EndY", area.end.y);
                row.putDouble("EndZ", area.end.z);
            }
            row.putDouble("Radius", area.radius);
            row.putDouble("Strength", area.strength);
            row.putString("Change", area.change.name());
            row.putBoolean("Temporary", area.temporary);
            row.putInt("Duration", area.duration);
            row.putLong("Cursor", area.cursor);
            queued.add(row);
        }
        tag.put("Areas", queued);
        var rules = new ListTag();
        for (var region : regions) {
            var row = new CompoundTag();
            row.putDouble("X", region.center.x);
            row.putDouble("Y", region.center.y);
            row.putDouble("Z", region.center.z);
            row.putDouble("Radius", region.radius);
            row.putString("Kind", region.kind.name());
            row.putUUID("Owner", region.owner);
            row.putLong("Until", region.until);
            rules.add(row);
        }
        tag.put("Regions", rules);
        if (sky != null) {
            var row = new CompoundTag();
            row.putLong("Old", sky.oldTime);
            row.putLong("New", sky.newTime);
            row.putLong("Started", sky.started);
            row.putLong("Until", sky.until);
            row.putBoolean("Rain", sky.wasRaining);
            row.putBoolean("Thunder", sky.wasThundering);
            tag.put("Sky", row);
        }
        return tag;
    }
}
