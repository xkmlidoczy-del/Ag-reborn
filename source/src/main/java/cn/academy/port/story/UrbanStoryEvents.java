package cn.academy.port.story;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.highpower.HighPowerSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server-owned in-world events for the first investigation. */
@EventBusSubscriber(modid = "academy")
public final class UrbanStoryEvents {
    private static final TagKey<Structure> STATION = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("academy", "urban_station"));
    static final TagKey<Structure> FACILITY = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("academy", "urban_facility"));
    private static final String GUIDE = "broadcast_guide", INJURED = "injured_student", PROTOCOL_STUDENT = "protocol_student", RESCUED = "academy:urban_rescued";
    private static final java.util.Set<ServerPlayer> ELECTRIC_ACTIONS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private UrbanStoryEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.isCanceled() && event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player && interact(player, event.getTarget())) {
            event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        if (!event.isCanceled() && event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity() instanceof ServerPlayer player && interact(player, event.getTarget())) {
            event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interactBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player) || !valid(player)
                || !player.serverLevel().mayInteract(player, event.getPos())) return;
        boolean station=player.serverLevel().structureManager().getStructureWithPieceAt(event.getPos(), STATION).isValid();
        boolean facility=player.serverLevel().structureManager().getStructureWithPieceAt(event.getPos(), FACILITY).isValid();
        if (!station && !facility) return;
        var state=player.serverLevel().getBlockState(event.getPos());
        boolean recorded;
        if (facility && state.is(Blocks.LECTERN))
            recorded=record(player,UrbanStoryProgress.Objective.PROTOCOL_ACCESS,UrbanStoryProgress.Approach.EQUIPMENT,
                    "academy.urban.message.protocol_access");
        else if (facility && state.is(Blocks.AMETHYST_BLOCK))
            recorded=UrbanCalibrator.start(player,event.getPos());
        else if (facility && state.is(Blocks.LEVER)) {
            var ability=AbilityStorage.get(player);
            if (player.isShiftKeyDown() && player.getMainHandItem().getItem() instanceof SwordItem) {
                recorded=UrbanStoryGuard.challenge(player,event.getPos());
                event.setCancellationResult(recorded ? InteractionResult.SUCCESS : InteractionResult.FAIL);
                event.setCanceled(true);
                return;
            }
            if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                    && "electromaster".equals(ability.category) && ability.learned(HighPowerSkills.FORKED_ARC)
                    && ability.presets.currentContains(HighPowerSkills.FORKED_ARC)) {
                recorded=disableLeverWithArc(player,event.getPos(),state);
                event.setCancellationResult(recorded ? InteractionResult.SUCCESS : InteractionResult.FAIL);
                event.setCanceled(true);
                return;
            }
            recorded=disableLever(player,event.getPos(),state);
        }
        else if (station && state.is(Blocks.LECTERN))
            recorded=record(player, UrbanStoryProgress.Objective.BROADCAST_SOURCE, UrbanStoryProgress.Approach.EQUIPMENT,
                    "academy.urban.message.broadcast_source");
        else if (station && state.is(Blocks.BOOKSHELF) && player.getMainHandItem().is(Items.BOOK))
            recorded=record(player, UrbanStoryProgress.Objective.STUDENT_RECORDS, UrbanStoryProgress.Approach.EQUIPMENT,
                    "academy.urban.message.student_records");
        else if (station && state.is(Blocks.CYAN_TERRACOTTA) && player.getMainHandItem().is(Items.COMPASS))
            recorded=record(player, UrbanStoryProgress.Objective.STUDENT_TRACES, UrbanStoryProgress.Approach.EQUIPMENT,
                    "academy.urban.message.student_traces");
        else recorded=false;
        if (recorded) {
            event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true);
        }
    }

    /** A real paid arc must cross the selected live node before it can operate it. */
    private static boolean disableLeverWithArc(ServerPlayer p,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState lever) {
        if (!ELECTRIC_ACTIONS.add(p)) return false;
        try {
            var ability=AbilityStorage.get(p); var world=p.serverLevel(); var progress=UrbanStoryStorage.read(p).orElse(null);
            var objective=leverObjective(progress,world.getBlockState(pos.below()));
            if (objective==null || progress.completed(objective) || !lever.getValue(LeverBlock.POWERED)
                    || !SkillCatalog.enabled("electromaster",HighPowerSkills.FORKED_ARC)
                    || !ability.canUse(HighPowerSkills.FORKED_ARC) || !arcReachesLever(p,pos)) return false;
            long revision=ability.presets.revision(); int preset=ability.presets.current(); Vec3 eye=p.getEyePosition(), look=p.getLookAngle();
            if (!ElectromasterPrototype.castLearned(p)) return false;
            if (!valid(p) || p.serverLevel()!=world || AbilityStorage.get(p)!=ability || !ability.activated || !ability.overloadFine
                    || ability.interfering || !"electromaster".equals(ability.category) || !ability.learned(HighPowerSkills.FORKED_ARC)
                    || ability.presets.current()!=preset || ability.presets.revision()!=revision || !ability.presets.currentContains(HighPowerSkills.FORKED_ARC)
                    || !SkillCatalog.enabled("electromaster",HighPowerSkills.FORKED_ARC) || !eye.equals(p.getEyePosition()) || !look.equals(p.getLookAngle())
                    || !world.hasChunkAt(pos) || !world.mayInteract(p,pos) || !world.getBlockState(pos).equals(lever)
                    || !world.structureManager().getStructureWithPieceAt(pos,FACILITY).isValid() || !arcReachesLever(p,pos)
                    || UrbanStoryStorage.read(p).map(current -> leverObjective(current,world.getBlockState(pos.below()))!=objective
                            || current.completed(objective)).orElse(true)) return false;
            if (!world.setBlock(pos,lever.setValue(LeverBlock.POWERED,false),3)) return false;
            var after=world.getBlockState(pos);
            if (!valid(p) || p.serverLevel()!=world || AbilityStorage.get(p)!=ability || !ability.activated || !ability.overloadFine
                    || ability.interfering || !"electromaster".equals(ability.category) || !ability.learned(HighPowerSkills.FORKED_ARC)
                    || ability.presets.current()!=preset || ability.presets.revision()!=revision || !ability.presets.currentContains(HighPowerSkills.FORKED_ARC)
                    || !SkillCatalog.enabled("electromaster",HighPowerSkills.FORKED_ARC) || !after.is(Blocks.LEVER)
                    || after.getValue(LeverBlock.POWERED) || !world.mayInteract(p,pos)
                    || !world.structureManager().getStructureWithPieceAt(pos,FACILITY).isValid()
                    || UrbanStoryStorage.read(p).map(current -> leverObjective(current,world.getBlockState(pos.below()))!=objective
                            || current.completed(objective)).orElse(true)) return false;
            return record(p,objective,UrbanStoryProgress.Approach.ABILITY,
                    objective==UrbanStoryProgress.Objective.PROTOCOL_ARRAY?"academy.urban.message.protocol_array":"academy.urban.message.calibrator_node");
        } finally { ELECTRIC_ACTIONS.remove(p); }
    }

    private static boolean arcReachesLever(ServerPlayer p,net.minecraft.core.BlockPos pos) {
        Vec3 eye=p.getEyePosition(), look=p.getLookAngle();
        if (!Double.isFinite(eye.x) || !Double.isFinite(eye.y) || !Double.isFinite(eye.z)
                || !Double.isFinite(look.x) || !Double.isFinite(look.y) || !Double.isFinite(look.z)
                || p.distanceToSqr(Vec3.atCenterOf(pos))>36 || !p.serverLevel().hasChunkAt(pos)) return false;
        Vec3 end=eye.add(look.scale(6));
        var hit=p.serverLevel().clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        if (!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK || !block.getBlockPos().equals(pos)) return false;
        double distance=eye.distanceToSqr(hit.getLocation());
        var solid=p.serverLevel().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if (solid.getType()!=HitResult.Type.MISS && eye.distanceToSqr(solid.getLocation())+1e-8<distance) return false;
        for (LivingEntity other : p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(eye,hit.getLocation()).inflate(.3),
                e -> e!=p && e.isAlive() && !e.isSpectator())) {
            var intersection=other.getBoundingBox().inflate(.12).clip(eye,hit.getLocation());
            if (intersection.isPresent()) return false;
        }
        return true;
    }

    static UrbanStoryProgress.Objective leverObjective(UrbanStoryProgress progress,net.minecraft.world.level.block.state.BlockState floor) {
        if (progress==null) return null;
        if (progress.chapter()==UrbanStoryProgress.Chapter.PROTOCOL && floor.is(Blocks.MAGENTA_TERRACOTTA))
            return UrbanStoryProgress.Objective.PROTOCOL_ARRAY;
        if (progress.chapter()==UrbanStoryProgress.Chapter.CALIBRATOR) {
            if (floor.is(Blocks.RED_TERRACOTTA)) return UrbanStoryProgress.Objective.CALIBRATOR_NODE_A;
            if (floor.is(Blocks.YELLOW_TERRACOTTA)) return UrbanStoryProgress.Objective.CALIBRATOR_NODE_B;
            if (floor.is(Blocks.LIME_TERRACOTTA)) return UrbanStoryProgress.Objective.CALIBRATOR_NODE_C;
        }
        return null;
    }

    private static boolean disableLever(ServerPlayer player,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        var progress=UrbanStoryStorage.read(player);
        if(progress.isEmpty())return false;
        var floor=player.serverLevel().getBlockState(pos.below());
        UrbanStoryProgress.Objective objective=null;
        if(progress.get().chapter()==UrbanStoryProgress.Chapter.PROTOCOL && floor.is(Blocks.MAGENTA_TERRACOTTA))
            objective=UrbanStoryProgress.Objective.PROTOCOL_ARRAY;
        else if(progress.get().chapter()==UrbanStoryProgress.Chapter.CALIBRATOR){
            if(floor.is(Blocks.RED_TERRACOTTA))objective=UrbanStoryProgress.Objective.CALIBRATOR_NODE_A;
            else if(floor.is(Blocks.YELLOW_TERRACOTTA))objective=UrbanStoryProgress.Objective.CALIBRATOR_NODE_B;
            else if(floor.is(Blocks.LIME_TERRACOTTA))objective=UrbanStoryProgress.Objective.CALIBRATOR_NODE_C;
        }
        if(objective==null || progress.get().completed(objective))return false;
        if(state.getValue(LeverBlock.POWERED) && !player.serverLevel().setBlock(pos,state.setValue(LeverBlock.POWERED,false),3))return false;
        return record(player,objective,UrbanStoryProgress.Approach.EQUIPMENT,
                objective==UrbanStoryProgress.Objective.PROTOCOL_ARRAY?"academy.urban.message.protocol_array":"academy.urban.message.calibrator_node");
    }

    private static boolean valid(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && player.serverLevel().getServer().isSameThread();
    }
    private static boolean interact(ServerPlayer player, Entity target) {
        if (!valid(player) || !(target instanceof Villager villager) || !villager.isAlive() || villager.isRemoved()
                || target.level() != player.serverLevel() || player.serverLevel().getEntity(target.getId()) != target
                || player.distanceToSqr(target) > 36 || !player.hasLineOfSight(target)
                || !player.serverLevel().mayInteract(player, target.blockPosition())) return false;
        String role = target.getPersistentData().getString(UrbanStoryClues.WITNESS_KEY);
        var progress = UrbanStoryStorage.read(player);
        if (progress.isEmpty()) return false;
        if (GUIDE.equals(role)) return record(player, UrbanStoryProgress.Objective.BROADCAST_LEAD,
                UrbanStoryProgress.Approach.EQUIPMENT, "academy.urban.message.broadcast_lead");
        UrbanStoryProgress.Objective rescue=INJURED.equals(role)?UrbanStoryProgress.Objective.BROADCAST_RESCUE:
                PROTOCOL_STUDENT.equals(role)?UrbanStoryProgress.Objective.PROTOCOL_RESCUE:null;
        String rescueMessage=INJURED.equals(role)?"academy.urban.message.broadcast_rescue":"academy.urban.message.protocol_rescue";
        if (PROTOCOL_STUDENT.equals(role) && player.getMainHandItem().is(Items.LEAD)
                && villager.getPersistentData().getBoolean(RESCUED)
                && progress.get().chapter() == UrbanStoryProgress.Chapter.CALIBRATOR)
            return UrbanStoryEscort.begin(player,villager);
        if (rescue!=null && villager.getPersistentData().getBoolean(RESCUED)
                && progress.get().chapter() == rescue.chapter() && !progress.get().completed(rescue))
            return record(player, rescue, UrbanStoryProgress.Approach.EQUIPMENT, rescueMessage);
        if (rescue!=null && player.getMainHandItem().is(Items.HONEY_BOTTLE)
                && villager.getHealth() < villager.getMaxHealth()
                && progress.get().chapter() == rescue.chapter() && !progress.get().completed(rescue)) {
            // Record the actual health change on the NPC so later co-op players can acknowledge the rescue.
            villager.setHealth(villager.getMaxHealth());
            villager.getPersistentData().putBoolean(RESCUED,true);
            if (!player.getAbilities().instabuild) {
                player.getMainHandItem().shrink(1);
                if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))
                    player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
                player.getInventory().setChanged();
            }
            return record(player, rescue, UrbanStoryProgress.Approach.EQUIPMENT, rescueMessage);
        }
        if (UrbanStoryClues.STUDENT_WITNESS.equals(role) && player.getMainHandItem().is(Items.BOOK)
                && UrbanStoryClues.canProbe(player, villager))
            return record(player, UrbanStoryProgress.Objective.STUDENT_WITNESS,
                    UrbanStoryProgress.Approach.EQUIPMENT, "academy.urban.clue.student_witness");
        return false;
    }
    private static boolean record(ServerPlayer player, UrbanStoryProgress.Objective objective,
                                  UrbanStoryProgress.Approach approach, String message) {
        var result = UrbanStoryStorage.recordVerified(player, objective, approach);
        if (result.orElse(null) != UrbanStoryProgress.Result.RECORDED) return false;
        player.sendSystemMessage(Component.translatable(message));
        return true;
    }
}
