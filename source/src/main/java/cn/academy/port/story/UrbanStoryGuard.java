package cn.academy.port.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** A real facility guard is bound to one control; only its committed player-caused death opens that node. */
@EventBusSubscriber(modid="academy")
public final class UrbanStoryGuard {
    private static final String GUARD="academy:urban_guard", NODE="academy:urban_guard_node",
            OBJECTIVE="academy:urban_guard_objective", SUMMONER="academy:urban_guard_summoner";
    private static final String EVIDENCE="academy:urban_evidence_guard", FOCUS="academy:urban_guard_focus";
    private UrbanStoryGuard() {}

    /** Chapter-specific evidence is carried by this guard, not by arbitrary hostile mobs. */
    static boolean challengeEvidence(ServerPlayer player,BlockPos anchor,UrbanStoryProgress.Objective objective,java.util.UUID focus){
        if(!UrbanStoryRoutes.evidenceAnchor(player,anchor,objective,focus))return false;
        var world=player.serverLevel();var registry=UrbanStoryGuardData.get(world);var previous=registry.get(anchor.asLong(),objective);
        if(previous!=null){
            var entity=world.getEntity(previous.uuid());
            if(entity instanceof Zombie guard&&matching(guard,anchor,objective)&&guard.getPersistentData().getBoolean(EVIDENCE)&&guard.isAlive()){
                guard.setTarget(player);player.sendSystemMessage(Component.translatable("academy.urban.message.guard_challenge"));return true;
            }
            if(!world.areEntitiesLoaded(previous.chunk()))return false;
            registry.remove(anchor.asLong(),objective,previous.uuid());
        }
        for(int radius=1;radius<=4;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy=0;dy>=-3;dy--){
                BlockPos feet=anchor.offset(dx,dy,dz);
                if(!world.hasChunkAt(feet)||!world.mayInteract(player,feet)||!world.getWorldBorder().isWithinBounds(feet)
                        ||!UrbanStoryRoutes.appropriate(world,feet,objective)||!world.getBlockState(feet.below()).isFaceSturdy(world,feet.below(),Direction.UP))continue;
                Zombie guard=EntityType.ZOMBIE.create(world);if(guard==null)return false;
                guard.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,player.getYRot(),0);
                if(!world.noCollision(guard,guard.getBoundingBox()))continue;
                var data=guard.getPersistentData();data.putBoolean(GUARD,true);data.putBoolean(EVIDENCE,true);
                data.putLong(NODE,anchor.asLong());data.putString(OBJECTIVE,objective.name());data.putUUID(SUMMONER,player.getUUID());
                if(focus!=null)data.putUUID(FOCUS,focus);
                guard.setCustomName(Component.translatable("academy.urban.npc.guard"));guard.setPersistenceRequired();guard.setTarget(player);
                if(!registry.put(anchor.asLong(),objective,guard.getUUID(),new ChunkPos(feet).toLong()))return false;
                if(!world.addFreshEntity(guard)){registry.remove(anchor.asLong(),objective,guard.getUUID());return false;}
                player.sendSystemMessage(Component.translatable("academy.urban.message.guard_challenge"));return true;
            }
        }return false;
    }

    static boolean challenge(ServerPlayer player, BlockPos node) {
        if (!validPlayer(player) || node==null) return false;
        var world=player.serverLevel(); var state=world.getBlockState(node); var progress=UrbanStoryStorage.read(player).orElse(null);
        var objective=UrbanStoryEvents.leverObjective(progress,world.getBlockState(node.below()));
        if (objective==null || progress.completed(objective) || !state.is(Blocks.LEVER)
                || !world.hasChunkAt(node) || !world.mayInteract(player,node)
                || !world.structureManager().getStructureWithPieceAt(node,UrbanStoryEvents.FACILITY).isValid()
                || player.distanceToSqr(node.getX()+.5,node.getY()+.5,node.getZ()+.5)>36
                || !aimed(player,node)) return false;
        var registry=UrbanStoryGuardData.get(world); var existing=registry.get(node.asLong(),objective);
        if (existing!=null) {
            var loaded=world.getEntity(existing.uuid());
            if (loaded instanceof Zombie guard && matching(guard,node,objective) && !guard.isRemoved()) {
                if (guard.isAlive()) guard.setTarget(player);
                player.sendSystemMessage(Component.translatable("academy.urban.message.guard_challenge")); return true;
            }
            // No chunk loads: an unloaded guard retains ownership until its
            // last known entity chunk is already loaded and proves it absent.
            if (!world.areEntitiesLoaded(existing.chunk())) {
                player.sendSystemMessage(Component.translatable("academy.urban.message.guard_unloaded")); return false;
            }
            registry.remove(node.asLong(),objective,existing.uuid());
        }
        for (int radius=1;radius<=3;radius++) for (int dx=-radius;dx<=radius;dx++) for (int dz=-radius;dz<=radius;dz++) {
            if (Math.max(Math.abs(dx),Math.abs(dz))!=radius) continue;
            BlockPos feet=node.offset(dx,0,dz);
            if (!world.hasChunkAt(feet) || !world.hasChunkAt(feet.below()) || !world.mayInteract(player,feet)
                    || !world.getWorldBorder().isWithinBounds(feet)
                    || !world.structureManager().getStructureWithPieceAt(feet,UrbanStoryEvents.FACILITY).isValid()
                    || !world.getBlockState(feet.below()).isFaceSturdy(world,feet.below(),Direction.UP)) continue;
            Zombie guard=EntityType.ZOMBIE.create(world); if (guard==null) return false;
            guard.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,player.getYRot(),0);
            if (!world.noCollision(guard,guard.getBoundingBox())) continue;
            guard.getPersistentData().putBoolean(GUARD,true);
            guard.getPersistentData().putLong(NODE,node.asLong());
            guard.getPersistentData().putString(OBJECTIVE,objective.name());
            guard.getPersistentData().putUUID(SUMMONER,player.getUUID());
            guard.setCustomName(Component.translatable("academy.urban.npc.guard")); guard.setPersistenceRequired();
            guard.setTarget(player);
            if (!registry.put(node.asLong(),objective,guard.getUUID(),new ChunkPos(feet).toLong())) return false;
            if (!world.addFreshEntity(guard)) { registry.remove(node.asLong(),objective,guard.getUUID()); return false; }
            player.sendSystemMessage(Component.translatable("academy.urban.message.guard_challenge")); return true;
        }
        return false;
    }

    private static boolean aimed(ServerPlayer player,BlockPos node) {
        var eye=player.getEyePosition(); var end=eye.add(player.getLookAngle().scale(6));
        var hit=player.serverLevel().clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK && hit instanceof BlockHitResult block && block.getBlockPos().equals(node);
    }

    private static boolean matching(Zombie guard,BlockPos node,UrbanStoryProgress.Objective objective) {
        var data=guard.getPersistentData();
        return guard.getClass()==Zombie.class && guard.getType()==EntityType.ZOMBIE && data.getBoolean(GUARD)
                && data.contains(NODE,Tag.TAG_LONG) && data.getLong(NODE)==node.asLong()
                && data.contains(OBJECTIVE,Tag.TAG_STRING) && data.getString(OBJECTIVE).equals(objective.name()) && data.hasUUID(SUMMONER);
    }
    private static boolean validPlayer(ServerPlayer player) {
        return player!=null && player.server.isSameThread() && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !player.hasDisconnected() && player.server.getPlayerList().getPlayer(player.getUUID())==player;
    }

    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Zombie guard) || !(guard.level() instanceof ServerLevel world)) return;
        var data=guard.getPersistentData();
        if (!data.getBoolean(GUARD) || !data.contains(NODE,Tag.TAG_LONG) || !data.contains(OBJECTIVE,Tag.TAG_STRING)) return;
        try {
            var objective=UrbanStoryProgress.Objective.valueOf(data.getString(OBJECTIVE));
            UrbanStoryGuardData.get(world).update(data.getLong(NODE),objective,guard.getUUID(),new ChunkPos(guard.blockPosition()).toLong());
        } catch (IllegalArgumentException invalid) { /* Unknown objectives cannot acquire ownership. */ }
    }

    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Zombie guard) || !(event.getLevel() instanceof ServerLevel world)) return;
        var data=guard.getPersistentData();
        if (!data.getBoolean(GUARD) || !data.contains(NODE,Tag.TAG_LONG) || !data.contains(OBJECTIVE,Tag.TAG_STRING)) return;
        try {
            var objective=UrbanStoryProgress.Objective.valueOf(data.getString(OBJECTIVE));
            var registry=UrbanStoryGuardData.get(world); var reason=guard.getRemovalReason();
            if (reason!=null && reason!=net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK)
                registry.remove(data.getLong(NODE),objective,guard.getUUID());
            else registry.update(data.getLong(NODE),objective,guard.getUUID(),new ChunkPos(guard.blockPosition()).toLong());
        } catch (IllegalArgumentException invalid) { /* Unknown objectives cannot acquire ownership. */ }
    }

    /** LivingDropsEvent follows uncancelled native die(), after dead has been committed. */
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void defeated(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Zombie guard) || !(guard.level() instanceof ServerLevel world)
                || guard.isAlive() || guard.isRemoved() || world.getEntity(guard.getUUID())!=guard) return;
        var data=guard.getPersistentData();
        if (!data.contains(NODE,Tag.TAG_LONG) || !data.contains(OBJECTIVE,Tag.TAG_STRING)) return;
        BlockPos node=BlockPos.of(data.getLong(NODE));
        final UrbanStoryProgress.Objective objective;
        try { objective=UrbanStoryProgress.Objective.valueOf(data.getString(OBJECTIVE)); }
        catch (IllegalArgumentException invalid) { return; }
        var registry=UrbanStoryGuardData.get(world); var owned=registry.get(node.asLong(),objective);
        if (!matching(guard,node,objective) || owned==null || !owned.uuid().equals(guard.getUUID())) return;
        if(data.getBoolean(EVIDENCE)){
            try{
                if(event.getSource().getEntity() instanceof ServerPlayer player&&validPlayer(player)&&player.serverLevel()==world
                        &&player.distanceToSqr(guard)<=48*48&&UrbanStoryRoutes.appropriate(world,guard.blockPosition(),objective)
                        &&UrbanStoryRoutes.evidenceAnchor(player,node,objective,data.hasUUID(FOCUS)?data.getUUID(FOCUS):null))
                    UrbanStoryRoutes.applyEvidence(player,objective,node,data.hasUUID(FOCUS)?data.getUUID(FOCUS):null,UrbanStoryProgress.Approach.COMBAT);
            }finally{registry.remove(node.asLong(),objective,guard.getUUID());}
            return;
        }
        try {
            if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !validPlayer(player)
                    || player.serverLevel()!=world || player.distanceToSqr(guard)>48*48 || !world.hasChunkAt(node) || !world.mayInteract(player,node)
                    || !world.structureManager().getStructureWithPieceAt(node,UrbanStoryEvents.FACILITY).isValid()
                    || !world.structureManager().getStructureWithPieceAt(guard.blockPosition(),UrbanStoryEvents.FACILITY).isValid()) return;
            var progress=UrbanStoryStorage.read(player).orElse(null); var state=world.getBlockState(node);
            if (progress==null || progress.completed(objective) || UrbanStoryEvents.leverObjective(progress,world.getBlockState(node.below()))!=objective
                    || !state.is(Blocks.LEVER)) return;
            if (state.getValue(LeverBlock.POWERED) && !world.setBlock(node,state.setValue(LeverBlock.POWERED,false),3)) return;
            var current=world.getBlockState(node); var after=UrbanStoryStorage.read(player).orElse(null);
            if (!validPlayer(player) || player.serverLevel()!=world || !world.mayInteract(player,node)
                    || !world.structureManager().getStructureWithPieceAt(node,UrbanStoryEvents.FACILITY).isValid()
                    || !current.is(Blocks.LEVER) || current.getValue(LeverBlock.POWERED) || after==null || after.completed(objective)
                    || UrbanStoryEvents.leverObjective(after,world.getBlockState(node.below()))!=objective) return;
            var result=UrbanStoryStorage.recordVerified(player,objective,UrbanStoryProgress.Approach.COMBAT);
            if(result.orElse(null)==UrbanStoryProgress.Result.RECORDED)
                player.sendSystemMessage(Component.translatable("academy.urban.message.guard_defeated"));
        } finally { registry.remove(node.asLong(),objective,guard.getUUID()); }
    }
}
