package cn.academy.port.story;

import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.CanonPaperContent;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import static cn.academy.port.story.UrbanStoryProgress.*;

/** In-world alternatives. Puzzle proof is live, owner-bound collision geometry, never a client objective ID. */
@EventBusSubscriber(modid="academy")
public final class UrbanStoryRoutes {
    static final TagKey<Structure> CAMPUS=tag("urban_campus"), STATION=tag("urban_station");
    static final String ESCORT_METHODS="academy:urban_escort_methods";
    private UrbanStoryRoutes() {}
    private static TagKey<Structure> tag(String name){return TagKey.create(Registries.STRUCTURE,ResourceLocation.fromNamespaceAndPath("academy",name));}
    static boolean valid(ServerPlayer p){return p!=null && p.server.isSameThread() && p.isAlive() && !p.isRemoved()
            && !p.isSpectator() && !p.hasDisconnected() && p.server.getPlayerList().getPlayer(p.getUUID())==p;}
    static boolean near(ServerPlayer p,BlockPos pos){return valid(p) && pos!=null && p.serverLevel().hasChunkAt(pos)
            && p.distanceToSqr(pos.getCenter())<=36 && p.serverLevel().mayInteract(p,pos)
            && p.serverLevel().getWorldBorder().isWithinBounds(pos);}
    static boolean urban(ServerLevel level,BlockPos pos){return level.structureManager().getStructureWithPieceAt(pos,CAMPUS).isValid()
            ||level.structureManager().getStructureWithPieceAt(pos,STATION).isValid()
            ||level.structureManager().getStructureWithPieceAt(pos,UrbanStoryEvents.FACILITY).isValid();}
    static boolean appropriate(ServerLevel level,BlockPos pos,Objective objective){
        TagKey<Structure> key=objective==Objective.BROADCAST_LEAD?CAMPUS:
                objective.chapter()==Chapter.BROADCAST||objective.chapter()==Chapter.STUDENT?STATION:UrbanStoryEvents.FACILITY;
        return level.structureManager().getStructureWithPieceAt(pos,key).isValid();
    }
    static Objective next(ServerPlayer p){var progress=UrbanStoryStorage.read(p).orElse(null);if(progress==null)return null;
        for(Objective objective:Objective.values())if(objective.chapter()==progress.chapter()&&!progress.completed(objective))return objective;return null;}
    private static boolean sword(ServerPlayer p){return p.isShiftKeyDown()&&p.getMainHandItem().getItem() instanceof SwordItem;}

    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void block(PlayerInteractEvent.RightClickBlock event){
        if(event.isCanceled()||event.getHand()!=InteractionHand.MAIN_HAND||!(event.getEntity() instanceof ServerPlayer p)||!near(p,event.getPos()))return;
        var pos=event.getPos();var world=p.serverLevel();if(!urban(world,pos))return;
        var state=world.getBlockState(pos);boolean handled=false,accepted=false;
        if(state.is(Blocks.CARTOGRAPHY_TABLE)){
            var objective=next(p);handled=true;
            if(objective!=null && objective!=Objective.CALIBRATOR_DEFEATED && sword(p))
                accepted=UrbanStoryGuard.challengeEvidence(p,pos,objective,null);
            else if(objective!=null)accepted=solvePaper(p,pos,objective);
            if(!accepted)p.sendSystemMessage(Component.translatable("academy.urban.paper_hint"));
        }else if(state.is(Blocks.AMETHYST_BLOCK)&&p.isShiftKeyDown()&&p.getMainHandItem().is(Items.REDSTONE)){
            handled=true;accepted=UrbanCalibrator.shutdown(p,pos,Approach.EQUIPMENT);
        }else if(sword(p)){
            Objective objective=blockObjective(p,pos);
            if(objective!=null){handled=true;accepted=UrbanStoryGuard.challengeEvidence(p,pos,objective,null);}
        }
        if(handled){event.setCanceled(true);event.setCancellationResult(accepted?InteractionResult.SUCCESS:InteractionResult.FAIL);}
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void entity(PlayerInteractEvent.EntityInteract event){
        if(!event.isCanceled()&&event.getHand()==InteractionHand.MAIN_HAND&&event.getEntity() instanceof ServerPlayer p
                &&sword(p)&&event.getTarget() instanceof Villager v){
            var objective=npcObjective(p,v);if(objective==null)return;
            boolean accepted=UrbanStoryGuard.challengeEvidence(p,v.blockPosition(),objective,v.getUUID());
            event.setCanceled(true);event.setCancellationResult(accepted?InteractionResult.SUCCESS:InteractionResult.FAIL);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void entitySpecific(PlayerInteractEvent.EntityInteractSpecific event){
        if(!event.isCanceled()&&event.getHand()==InteractionHand.MAIN_HAND&&event.getEntity() instanceof ServerPlayer p
                &&sword(p)&&event.getTarget() instanceof Villager v){
            var objective=npcObjective(p,v);if(objective==null)return;
            boolean accepted=UrbanStoryGuard.challengeEvidence(p,v.blockPosition(),objective,v.getUUID());
            event.setCanceled(true);event.setCancellationResult(accepted?InteractionResult.SUCCESS:InteractionResult.FAIL);
        }
    }
    static Objective npcObjective(ServerPlayer p,Villager v){
        if(!valid(p)||v==null||!v.isAlive()||v.isRemoved()||v.level()!=p.level()||p.serverLevel().getEntity(v.getUUID())!=v
                ||p.distanceToSqr(v)>36||!p.hasLineOfSight(v)||!p.serverLevel().mayInteract(p,v.blockPosition()))return null;
        var progress=UrbanStoryStorage.read(p).orElse(null);if(progress==null)return null;
        String role=v.getPersistentData().getString(UrbanStoryClues.WITNESS_KEY);
        Objective objective=switch(role){
            case "broadcast_guide" -> Objective.BROADCAST_LEAD;
            case "injured_student" -> Objective.BROADCAST_RESCUE;
            case "student_witness" -> Objective.STUDENT_WITNESS;
            case "protocol_student" -> progress.chapter()==Chapter.CALIBRATOR?Objective.CALIBRATOR_EVACUATION:Objective.PROTOCOL_RESCUE;
            default -> null;};
        return objective!=null&&objective.chapter()==progress.chapter()&&!progress.completed(objective)?objective:null;
    }
    static Objective blockObjective(ServerPlayer p,BlockPos pos){
        if(!near(p,pos))return null;var world=p.serverLevel();var state=world.getBlockState(pos);
        var progress=UrbanStoryStorage.read(p).orElse(null);if(progress==null)return null;
        Objective result=null;
        if(world.structureManager().getStructureWithPieceAt(pos,STATION).isValid()){
            if(state.is(Blocks.LECTERN))result=Objective.BROADCAST_SOURCE;
            else if(state.is(Blocks.BOOKSHELF))result=Objective.STUDENT_RECORDS;
            else if(state.is(Blocks.CYAN_TERRACOTTA))result=Objective.STUDENT_TRACES;
        }else if(world.structureManager().getStructureWithPieceAt(pos,UrbanStoryEvents.FACILITY).isValid()
                &&state.is(Blocks.LECTERN))result=Objective.PROTOCOL_ACCESS;
        return result!=null&&result.chapter()==progress.chapter()&&!progress.completed(result)?result:null;
    }
    static boolean evidenceAnchor(ServerPlayer p,BlockPos anchor,Objective objective,UUID focus){
        if(!valid(p)||anchor==null||!p.serverLevel().hasChunkAt(anchor)||p.distanceToSqr(anchor.getCenter())>48*48
                ||!p.serverLevel().mayInteract(p,anchor)||objective==null||!appropriate(p.serverLevel(),anchor,objective))return false;
        var progress=UrbanStoryStorage.read(p).orElse(null);
        if(progress==null||objective==null||objective.chapter()!=progress.chapter()||progress.completed(objective))return false;
        if(focus!=null){Entity e=p.serverLevel().getEntity(focus);return e instanceof Villager v&&npcObjective(p,v)==objective;}
        return p.serverLevel().getBlockState(anchor).is(Blocks.CARTOGRAPHY_TABLE)
                ?next(p)==objective:blockObjective(p,anchor)==objective;
    }
    static Villager resident(ServerPlayer p,BlockPos anchor,String role){
        var list=p.serverLevel().getEntitiesOfClass(Villager.class,new AABB(anchor).inflate(24),v->v.isAlive()&&!v.isRemoved()
                &&role.equals(v.getPersistentData().getString(UrbanStoryClues.WITNESS_KEY))&&urban(p.serverLevel(),v.blockPosition()));
        list.sort(java.util.Comparator.comparingDouble(v->v.distanceToSqr(anchor.getCenter())));return list.isEmpty()?null:list.getFirst();
    }
    /** Raised control desk: three owner-made support blocks and the player physically standing on that bridge. */
    static boolean solvePaper(ServerPlayer p,BlockPos table,Objective objective){
        if(!near(p,table)||!p.serverLevel().getBlockState(table).is(Blocks.CARTOGRAPHY_TABLE)||!urban(p.serverLevel(),table)
                ||!"paper_psychokinesis".equals(AbilityStorage.get(p).category))return false;
        var world=p.serverLevel();
        for(Direction direction:Direction.Plane.HORIZONTAL){
            BlockPos center=table.relative(direction,2).below(2);Direction side=direction.getClockWise();
            List<BlockPos> bridge=List.of(center.relative(side,-1),center,center.relative(side));
            if(!world.hasChunkAt(center)||!world.getBlockState(center.below()).is(Blocks.GOLD_BLOCK)
                    ||!world.getBlockState(bridge.getFirst().below()).is(Blocks.IRON_BLOCK)
                    ||!world.getBlockState(bridge.getLast().below()).is(Blocks.IRON_BLOCK)
                    ||bridge.stream().anyMatch(pos->!world.getBlockState(pos).is(CanonPaperContent.PAPER.get())||!CanonPaperContent.owned(p,pos)||!world.mayInteract(p,pos))
                    ||!bridge.contains(p.blockPosition().below())||!p.onGround())continue;
            if(!canApply(p,objective,table,null))return false;
            if(!CanonPaperContent.consumeOwned(p,bridge))return false;
            return applyEvidence(p,objective,table,null,Approach.ABILITY);
        }
        return false;
    }
    private static boolean canApply(ServerPlayer p,Objective objective,BlockPos anchor,UUID focus){
        var progress=UrbanStoryStorage.read(p).orElse(null);if(progress==null||objective==null||objective.chapter()!=progress.chapter()
                ||progress.completed(objective)||!appropriate(p.serverLevel(),anchor,objective))return false;
        return switch(objective){
            case BROADCAST_RESCUE -> resident(p,anchor,"injured_student")!=null;
            case PROTOCOL_RESCUE,CALIBRATOR_EVACUATION -> resident(p,anchor,"protocol_student")!=null;
            case CALIBRATOR_DEFEATED -> UrbanCalibrator.ready(progress);
            default -> true;};
    }
    /** Called only after a real puzzle or bound guard has been completed. Applies physical consequences before saving evidence. */
    static boolean applyEvidence(ServerPlayer p,Objective objective,BlockPos anchor,UUID focus,Approach approach){
        if(!valid(p)||approach==Approach.EQUIPMENT||!canApply(p,objective,anchor,focus))return false;
        var world=p.serverLevel();
        if(objective==Objective.BROADCAST_RESCUE||objective==Objective.PROTOCOL_RESCUE){
            var student=resident(p,anchor,objective==Objective.BROADCAST_RESCUE?"injured_student":"protocol_student");
            if(student==null||!world.mayInteract(p,student.blockPosition()))return false;
            student.setHealth(student.getMaxHealth());student.getPersistentData().putBoolean("academy:urban_rescued",true);
        }else if(objective==Objective.CALIBRATOR_EVACUATION){
            var student=resident(p,anchor,"protocol_student");if(student==null||!student.getPersistentData().getBoolean("academy:urban_rescued"))return false;
            var methods=student.getPersistentData().getCompound(ESCORT_METHODS);methods.putString(p.getUUID().toString(),approach.name());
            student.getPersistentData().put(ESCORT_METHODS,methods);
            return UrbanStoryEscort.begin(p,student);
        }else if(objective==Objective.CALIBRATOR_DEFEATED){
            for(BlockPos pos:BlockPos.betweenClosed(anchor.offset(-12,-4,-12),anchor.offset(12,4,12)))
                if(world.hasChunkAt(pos)&&world.getBlockState(pos).is(Blocks.AMETHYST_BLOCK)
                        &&appropriate(world,pos,objective)&&UrbanCalibrator.shutdown(p,pos,approach))return true;
            return false;
        }else if(objective==Objective.PROTOCOL_ARRAY||objective==Objective.CALIBRATOR_NODE_A||objective==Objective.CALIBRATOR_NODE_B||objective==Objective.CALIBRATOR_NODE_C){
            BlockPos lever=findLever(p,anchor,objective);if(lever==null)return false;
            var before=world.getBlockState(lever);if(!world.setBlock(lever,before.setValue(LeverBlock.POWERED,false),3)
                    ||!world.getBlockState(lever).is(Blocks.LEVER)||world.getBlockState(lever).getValue(LeverBlock.POWERED))return false;
        }
        var result=UrbanStoryStorage.recordVerified(p,objective,approach);
        if(result.orElse(null)!=Result.RECORDED)return false;
        world.levelEvent(2005,anchor,0);p.sendSystemMessage(Component.translatable("academy.urban.route_complete",Component.translatable("academy.urban.objective."+objective.name().toLowerCase(java.util.Locale.ROOT))));
        return true;
    }
    private static BlockPos findLever(ServerPlayer p,BlockPos anchor,Objective objective){
        for(BlockPos pos:BlockPos.betweenClosed(anchor.offset(-20,-6,-20),anchor.offset(20,6,20))){
            if(!p.serverLevel().hasChunkAt(pos)||!p.serverLevel().mayInteract(p,pos))continue;
            var state=p.serverLevel().getBlockState(pos);if(!state.is(Blocks.LEVER))continue;
            if(p.serverLevel().structureManager().getStructureWithPieceAt(pos,UrbanStoryEvents.FACILITY).isValid()
                    &&UrbanStoryEvents.leverObjective(UrbanStoryStorage.read(p).orElse(null),p.serverLevel().getBlockState(pos.below()))==objective)return pos.immutable();
        }return null;
    }
}
