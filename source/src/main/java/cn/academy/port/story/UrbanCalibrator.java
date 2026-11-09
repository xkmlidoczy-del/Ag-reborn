package cn.academy.port.story;

import cn.academy.port.highpower.HighPowerCombat;
import java.math.BigDecimal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Purpose-built, repeatable underground encounter; its attacks never alter terrain. */
public final class UrbanCalibrator {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
    public static final DeferredHolder<EntityType<?>,EntityType<Boss>> BOSS=TYPES.register("urban_calibrator", () ->
            EntityType.Builder.<Boss>of(Boss::new,MobCategory.MONSTER).sized(1.6f,3.2f).clientTrackingRange(64)
                    .updateInterval(2).build("academy:urban_calibrator"));
    private UrbanCalibrator() {}
    public static void register(IEventBus bus){TYPES.register(bus);bus.addListener(UrbanCalibrator::attributes);}
    private static void attributes(EntityAttributeCreationEvent event){
        event.put(BOSS.get(),IronGolem.createAttributes().add(Attributes.MAX_HEALTH,160)
                .add(Attributes.ATTACK_DAMAGE,10).add(Attributes.MOVEMENT_SPEED,.26).add(Attributes.FOLLOW_RANGE,32).build());
    }
    static boolean ready(UrbanStoryProgress progress){
        return progress!=null && progress.chapter()==UrbanStoryProgress.Chapter.CALIBRATOR
                && progress.completed(UrbanStoryProgress.Objective.CALIBRATOR_NODE_A)
                && progress.completed(UrbanStoryProgress.Objective.CALIBRATOR_NODE_B)
                && progress.completed(UrbanStoryProgress.Objective.CALIBRATOR_NODE_C)
                && progress.completed(UrbanStoryProgress.Objective.CALIBRATOR_EVACUATION);
    }
    public static boolean start(ServerPlayer player,BlockPos podium){
        if(player==null||player.isRemoved()||!player.isAlive()||player.isSpectator()
                ||!player.serverLevel().getServer().isSameThread()
                ||!ready(UrbanStoryStorage.read(player).orElse(null)))return false;
        ServerLevel level=player.serverLevel();
        if(!level.structureManager().getStructureWithPieceAt(podium,UrbanStoryEvents.FACILITY).isValid())return false;
        var active=level.getEntitiesOfClass(Boss.class,new AABB(podium).inflate(24),boss->boss.isAlive()&&!boss.isRemoved());
        if(!active.isEmpty()){active.getFirst().setTarget(player);return true;}
        Boss boss=BOSS.get().create(level);
        if(boss==null)return false;
        boss.moveTo(podium.getX()+.5,podium.getY()+1,podium.getZ()+.5,player.getYRot(),0);
        if(!level.noCollision(boss,boss.getBoundingBox()))return false;
        boss.setCustomName(Component.translatable("academy.urban.npc.calibrator"));
        if(!HighPowerCombat.enroll(boss,5))return false;
        boss.setTarget(player);
        if(!level.addFreshEntity(boss))return false;
        player.sendSystemMessage(Component.translatable("academy.urban.message.calibrator_ready"));
        return true;
    }

    private static void awardDefeat(Boss boss,DamageSource source){
        if(!(boss.level() instanceof ServerLevel level)||!(source.getEntity() instanceof ServerPlayer killer))return;
        var participants=new java.util.LinkedHashSet<>(level.getEntitiesOfClass(ServerPlayer.class,boss.getBoundingBox().inflate(24),p->p.isAlive()&&!p.isSpectator()));
        if(killer.isAlive()&&!killer.isRemoved()&&killer.serverLevel()==level)participants.add(killer);
        for(ServerPlayer player:participants){
            if(!ready(UrbanStoryStorage.read(player).orElse(null)))continue;
            if(UrbanStoryStorage.recordVerified(player,UrbanStoryProgress.Objective.CALIBRATOR_DEFEATED,
                    HighPowerCombat.isExactSkillSource(source)?UrbanStoryProgress.Approach.ABILITY:UrbanStoryProgress.Approach.COMBAT).orElse(null)==UrbanStoryProgress.Result.RECORDED)
                player.sendSystemMessage(Component.translatable("academy.urban.message.calibrator_defeated"));
        }
    }

    /** The physical control bus can neutralize a live machine once every safety interlock is clear. */
    static boolean shutdown(ServerPlayer player,BlockPos podium,UrbanStoryProgress.Approach approach){
        if(!UrbanStoryRoutes.near(player,podium)||approach==UrbanStoryProgress.Approach.COMBAT
                ||!ready(UrbanStoryStorage.read(player).orElse(null)))return false;
        var level=player.serverLevel();
        if(!level.getBlockState(podium).is(net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK)
                ||!level.structureManager().getStructureWithPieceAt(podium,UrbanStoryEvents.FACILITY).isValid())return false;
        var bosses=level.getEntitiesOfClass(Boss.class,new AABB(podium).inflate(16),b->b.isAlive()&&!b.isRemoved());
        if(bosses.size()!=1)return false;
        var held=player.getMainHandItem();
        if(approach==UrbanStoryProgress.Approach.EQUIPMENT&&(!held.is(net.minecraft.world.item.Items.REDSTONE)||held.getCount()<3))return false;
        var boss=bosses.getFirst();
        if(!level.structureManager().getStructureWithPieceAt(boss.blockPosition(),UrbanStoryEvents.FACILITY).isValid())return false;
        if(approach==UrbanStoryProgress.Approach.EQUIPMENT&&!player.getAbilities().instabuild){held.shrink(3);player.getInventory().setChanged();}
        boss.setNoAi(true);boss.setTarget(null);
        if(!UrbanStoryRoutes.valid(player)||player.serverLevel()!=level||!level.mayInteract(player,podium)
                ||!ready(UrbanStoryStorage.read(player).orElse(null))||boss.isRemoved()||!boss.isAlive())return false;
        boss.discard();if(!boss.isRemoved())return false;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,boss.getX(),boss.getY()+1,boss.getZ(),35,.6,.8,.6,.02);
        var result=UrbanStoryStorage.recordVerified(player,UrbanStoryProgress.Objective.CALIBRATOR_DEFEATED,approach);
        if(result.orElse(null)!=UrbanStoryProgress.Result.RECORDED)return false;
        player.sendSystemMessage(Component.translatable("academy.urban.message.calibrator_shutdown"));
        return true;
    }

    public static final class Boss extends IronGolem {
        private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("academy.urban.npc.calibrator"),
                BossEvent.BossBarColor.BLUE,BossEvent.BossBarOverlay.PROGRESS);
        private Vec3 marked;
        Boss(EntityType<? extends IronGolem> type,Level level){super(type,level);setPersistenceRequired();xpReward=30;}
        @Override protected void registerGoals(){
            goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new MeleeAttackGoal(this,1.0,true));
            goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,24));goalSelector.addGoal(6,new RandomLookAroundGoal(this));
            targetSelector.addGoal(1,new HurtByTargetGoal(this));targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
        }
        @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);bar.addPlayer(player);}
        @Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);bar.removePlayer(player);}
        @Override public boolean hurt(DamageSource source,float amount){
            // Vanilla equipment uses ordinary health units; this named boss maps those inputs
            // to its high-power pool before the native damage pipeline. Exact skill sources
            // retain their sealed amount and never pass through this multiplier.
            if(source!=null && source.getEntity() instanceof Player && !HighPowerCombat.isExactSkillSource(source)
                    && (source.is(DamageTypes.PLAYER_ATTACK)||source.is(DamageTypes.ARROW)||source.is(DamageTypes.TRIDENT))
                    && Float.isFinite(amount) && amount>0 && HighPowerCombat.enabled(this))
                amount=Math.min(5.0e19f,amount*1.0e18f);
            return super.hurt(source,amount);
        }
        @Override public void die(DamageSource source){
            boolean previouslyDead=dead;
            super.die(source);
            // The native death event may be cancelled; only a committed death can complete the quest.
            if(!previouslyDead && dead)awardDefeat(this,source);
        }
        @Override public void tick(){
            super.tick();if(!(level() instanceof ServerLevel world)||!isAlive())return;
            bar.setProgress(Math.max(0,Math.min(1,getHealth()/getMaxHealth())));
            int phase=tickCount%100;
            if(phase==35 && getTarget()!=null && getTarget().isAlive() && distanceToSqr(getTarget())<24*24){
                marked=getTarget().position();
                world.sendParticles(ParticleTypes.ELECTRIC_SPARK,marked.x,marked.y+.2,marked.z,35,1.2,.2,1.2,.03);
                world.playSound(null,marked.x,marked.y,marked.z,SoundEvents.BEACON_ACTIVATE,SoundSource.HOSTILE,1,1.8f);
            }
            if(phase==55 && marked!=null){
                world.sendParticles(ParticleTypes.END_ROD,marked.x,marked.y+.5,marked.z,50,1.4,.5,1.4,.1);
                for(ServerPlayer target:world.getEntitiesOfClass(ServerPlayer.class,new AABB(marked,marked).inflate(3),p->p.isAlive()&&!p.isSpectator()&&!p.isCreative())){
                    float damage=HighPowerCombat.enabled(target)?new BigDecimal(HighPowerCombat.maxHealthText(target)).multiply(new BigDecimal("0.08")).floatValue():8f;
                    if(Float.isFinite(damage)&&damage>0)target.hurt(damageSources().mobAttack(this),damage);
                }
                marked=null;
            }
        }
    }
}
