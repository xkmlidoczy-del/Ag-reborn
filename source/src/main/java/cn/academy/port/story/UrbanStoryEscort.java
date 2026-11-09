package cn.academy.port.story;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** A real pathfinding escort; no teleport, chunk loading or client-supplied completion. */
@EventBusSubscriber(modid="academy")
public final class UrbanStoryEscort {
    private static final String OWNER="academy:urban_escort_owner", EVACUATED="academy:urban_evacuated",
            STARTED="academy:urban_escort_started", RESCUED="academy:urban_rescued";
    private UrbanStoryEscort() {}

    static boolean begin(ServerPlayer player,Villager student) {
        if(player==null || student==null || !player.server.isSameThread() || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || !student.isAlive() || student.isRemoved() || student.level()!=player.serverLevel()
                || player.serverLevel().getEntity(student.getId())!=student || player.distanceToSqr(student)>36
                || !player.hasLineOfSight(student) || !player.serverLevel().mayInteract(player,student.blockPosition())
                || !"protocol_student".equals(student.getPersistentData().getString(UrbanStoryClues.WITNESS_KEY))
                || !student.getPersistentData().getBoolean(RESCUED))return false;
        var progress=UrbanStoryStorage.read(player);
        if(progress.isEmpty() || progress.get().chapter()!=UrbanStoryProgress.Chapter.CALIBRATOR
                || progress.get().completed(UrbanStoryProgress.Objective.CALIBRATOR_EVACUATION))return false;
        if(student.getPersistentData().getBoolean(EVACUATED))return complete(player,student);
        // An outside student may be reclaimed only if the server already started
        // this escort, including a legacy active owner saved before STARTED existed.
        boolean inside=player.serverLevel().structureManager().getStructureWithPieceAt(student.blockPosition(),UrbanStoryEvents.FACILITY).isValid();
        if(!inside && !student.getPersistentData().getBoolean(STARTED) && !student.getPersistentData().hasUUID(OWNER))return false;
        if(student.getPersistentData().hasUUID(OWNER)){
            UUID current=student.getPersistentData().getUUID(OWNER);
            var owner=player.server.getPlayerList().getPlayer(current);
            if(owner!=null && owner.isAlive() && !owner.isRemoved() && owner.serverLevel()==player.serverLevel()
                    && !current.equals(player.getUUID()))return false;
        }
        student.getPersistentData().putBoolean(STARTED,true);
        student.getPersistentData().putUUID(OWNER,player.getUUID());
        student.setNoAi(false);
        student.getNavigation().moveTo(player,0.9);
        return true;
    }

    @SubscribeEvent public static void tick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof Villager student) || !(student.level() instanceof ServerLevel level)
                || !"protocol_student".equals(student.getPersistentData().getString(UrbanStoryClues.WITNESS_KEY))
                || !student.getPersistentData().hasUUID(OWNER) || student.tickCount%10!=0)return;
        var player=level.getServer().getPlayerList().getPlayer(student.getPersistentData().getUUID(OWNER));
        if(player==null || !player.isAlive() || player.isRemoved() || player.serverLevel()!=level){
            // Preserve proof of the earlier legitimate escort before releasing a
            // disconnected or dimension-changed owner, including older saves.
            student.getPersistentData().putBoolean(STARTED,true);
            student.getNavigation().stop(); student.setNoAi(true); student.getPersistentData().remove(OWNER); return;
        }
        var progress=UrbanStoryStorage.read(player);
        if(progress.isEmpty() || progress.get().chapter()!=UrbanStoryProgress.Chapter.CALIBRATOR
                || progress.get().completed(UrbanStoryProgress.Objective.CALIBRATOR_EVACUATION)){
            student.getNavigation().stop(); student.setNoAi(true); student.getPersistentData().remove(OWNER); return;
        }
        if(student.distanceToSqr(player)<16
                && !level.structureManager().getStructureWithPieceAt(student.blockPosition(),UrbanStoryEvents.FACILITY).isValid()
                && !level.structureManager().getStructureWithPieceAt(player.blockPosition(),UrbanStoryEvents.FACILITY).isValid()) {
            student.getPersistentData().putBoolean(EVACUATED,true);
            complete(player,student);
            return;
        }
        if(student.distanceToSqr(player)<48*48)student.getNavigation().moveTo(player,0.9);
        else student.getNavigation().stop();
    }
    private static boolean complete(ServerPlayer player,Villager student){
        UrbanStoryProgress.Approach approach=UrbanStoryProgress.Approach.EQUIPMENT;
        var methods=student.getPersistentData().getCompound(UrbanStoryRoutes.ESCORT_METHODS);
        try{if(methods.contains(player.getUUID().toString()))approach=UrbanStoryProgress.Approach.valueOf(methods.getString(player.getUUID().toString()));}
        catch(IllegalArgumentException invalid){/* Unknown old route tags never grant a different method. */}
        var result=UrbanStoryStorage.recordVerified(player,UrbanStoryProgress.Objective.CALIBRATOR_EVACUATION,
                approach);
        if(result.orElse(null)!=UrbanStoryProgress.Result.RECORDED)return false;
        student.getNavigation().stop(); student.setNoAi(true); student.getPersistentData().remove(OWNER);
        methods.remove(player.getUUID().toString());student.getPersistentData().put(UrbanStoryRoutes.ESCORT_METHODS,methods);
        player.sendSystemMessage(Component.translatable("academy.urban.message.calibrator_evacuation"));
        return true;
    }
}
