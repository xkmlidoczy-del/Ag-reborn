package cn.academy.port.worldboss;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.*;

/** A damageable sixty-health monster with the original V9 world and cinematic presentation. */
public final class WorldBossContent {
    public static final float MAX_HEALTH=60F, BASE_ATTACK_DAMAGE=10F;
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,"academy");
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    public static final DeferredHolder<EntityType<?>,EntityType<Boss>> BOSS=TYPES.register("world_boss",() ->
        EntityType.Builder.<Boss>of(Boss::new,MobCategory.MONSTER).sized(14,30).fireImmune().clientTrackingRange(160).updateInterval(1).build("academy:world_boss"));
    public static final DeferredItem<DeferredSpawnEggItem> SPAWN_EGG=ITEMS.register("world_boss_spawn_egg",() ->
        new DeferredSpawnEggItem(BOSS,0x39251B,0xFBA12A,new Item.Properties()) {
            @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<net.minecraft.network.chat.Component> tooltip,TooltipFlag flag) {
                super.appendHoverText(stack,context,tooltip,flag);
                tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.academy.world_boss_spawn_egg").withStyle(net.minecraft.ChatFormatting.GRAY));
                tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.academy.world_boss_spawn_egg.environment").withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        });
    public static final Map<String,DeferredHolder<SoundEvent,SoundEvent>> SND=new LinkedHashMap<>();
    static {
        for(String name:List.of("beam","bite","breath","charge","growl","impact","meteor","nova","slash","stomp","storm","tail","whoosh","thunder","rumble","crack","lava","rise"))
            SND.put(name,SOUNDS.register("world_boss_"+name,() -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy","world_boss_"+name))));
    }
    public static void register(IEventBus bus) {
        TYPES.register(bus); SOUNDS.register(bus); ITEMS.register(bus);
        bus.addListener(WorldBossContent::attributes);bus.addListener(WorldBossContent::creative);bus.addListener(WorldBossContent::payloads);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOSS.get(),Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,MAX_HEALTH)
            .add(Attributes.ATTACK_DAMAGE,BASE_ATTACK_DAMAGE).add(Attributes.MOVEMENT_SPEED,.30)
            .add(Attributes.FOLLOW_RANGE,96).add(Attributes.KNOCKBACK_RESISTANCE,.6).add(Attributes.STEP_HEIGHT,1.5).build());
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==CreativeModeTabs.SPAWN_EGGS)event.accept(SPAWN_EGG);
    }
    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("academy_worldboss_1").playToClient(FxPacket.TYPE,FxPacket.CODEC,(packet,context) -> context.enqueueWork(() -> FxPacket.CLIENT_HANDLER.accept(packet)));
    }

    public static final class Boss extends Monster {
        private static final EntityDataAccessor<Integer> CLIP=SynchedEntityData.defineId(Boss.class,EntityDataSerializers.INT);
        private static final EntityDataAccessor<Integer> START=SynchedEntityData.defineId(Boss.class,EntityDataSerializers.INT);
        private static final EntityDataAccessor<org.joml.Vector3f> AIM=SynchedEntityData.defineId(Boss.class,EntityDataSerializers.VECTOR3);
        private final ServerBossEvent bossBar=new ServerBossEvent(getDisplayName(),BossEvent.BossBarColor.RED,BossEvent.BossBarOverlay.PROGRESS);
        private int cooldown=40,actions,lastAttack=-1;
        private long castSequence;
        private boolean environmentInitialized,pitAnchor;
        private Vec3 spawnOrigin=Vec3.ZERO;
        private final Map<UUID,Float> damageSpent=new HashMap<>();
        final List<WorldBossCombat.Meteor> meteors=new ArrayList<>();
        Vec3 castAim=Vec3.ZERO;
        public int blendFrom=-1,blendStart,clientEvent=-1,seenClip=-1,seenStart;
        public float blendFromFrame;

        public Boss(EntityType<? extends Boss> type,Level level) {
            super(type,level);noCulling=true;xpReward=30;setPersistenceRequired();
            entityData.set(START,(int)level.getGameTime());
        }
        @Override protected void registerGoals() {
            goalSelector.addGoal(0,new FloatGoal(this));
            goalSelector.addGoal(5,new RandomStrollGoal(this,.6) {
                @Override public boolean canUse(){return getTarget()==null&&Clips.get(clip()).loops()&&super.canUse();}
                @Override public boolean canContinueToUse(){return getTarget()==null&&Clips.get(clip()).loops()&&super.canContinueToUse();}
            });
            goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,48));
            goalSelector.addGoal(7,new RandomLookAroundGoal(this));
            targetSelector.addGoal(1,new HurtByTargetGoal(this));
            targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
        }
        @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
            super.defineSynchedData(builder);builder.define(CLIP,Clips.SPAWN);builder.define(START,0);builder.define(AIM,new org.joml.Vector3f(0,0,55));
        }
        public int clip(){return entityData.get(CLIP);}
        public int start(){return entityData.get(START);}
        public boolean intro(){return clip()==Clips.SPAWN;}
        /** Authored body/locator rise from the 17-block intro pit, shared with native hit origins. */
        public float rise(float frame) {
            if(clip()!=Clips.SPAWN)return 0;
            float t=net.minecraft.util.Mth.clamp(frame/66F,0,1);
            return -16F*(1-t*t*(3-2*t));
        }
        public long castSequence(){return castSequence;}
        public float damageSpent(UUID victim){return damageSpent.getOrDefault(victim,0F);}
        public int pendingMeteorCount(){return meteors.size();}
        /** Starts one authored action. Leaving an action cancels its outstanding projectiles. */
        public void play(int clip) {
            if(level().isClientSide||clip<0||clip>=Clips.all().size())return;
            meteors.clear();castSequence++;
            if(clip!=Clips.IDLE&&clip!=Clips.WALK)damageSpent.clear();
            entityData.set(CLIP,clip);entityData.set(START,(int)level().getGameTime());
            var target=getTarget();castAim=target!=null?target.getBoundingBox().getCenter():local(0,0,-55);
            var aimOffset=castAim.subtract(position());entityData.set(AIM,new org.joml.Vector3f((float)aimOffset.x,(float)aimOffset.y,(float)aimOffset.z));
            if(!Clips.get(clip).loops())getNavigation().stop();
        }
        float reserveDamage(LivingEntity victim,float amount) {
            if(!Float.isFinite(amount)||amount<=0)return 0;
            UUID id=victim.getUUID();if(!damageSpent.containsKey(id)&&damageSpent.size()>=256)return 0;
            float before=damageSpent(id),remaining=Math.max(0,WorldBossCombat.maximumDamage(clip())-before),spend=Math.min(amount,remaining);
            if(spend>0)damageSpent.put(id,before+spend);
            return spend;
        }
        public float frame(float partial) {
            var c=Clips.get(clip());float f=(int)((int)level().getGameTime()-start())+partial;
            return c.loops()?Math.max(0,f)%c.ticks():Math.max(0,Math.min(f,c.ticks()));
        }
        public Vec3 forward(){double angle=Math.toRadians(getYRot());return new Vec3(-Math.sin(angle),0,Math.cos(angle));}
        public Vec3 attackAim(){var aim=entityData.get(AIM);return position().add(aim.x,aim.y,aim.z);}
        public Vec3 local(double x,double y,double z) {
            double angle=Math.toRadians(180-getYRot()),c=Math.cos(angle),s=Math.sin(angle);
            return new Vec3(getX()+x*c+z*s,getY()+y,getZ()-x*s+z*c);
        }
        @Override public boolean canAttackType(EntityType<?> type){return type!=BOSS.get()&&super.canAttackType(type);}
        @Override public void tick() {
            if(!level().isClientSide&&isAlive()) {
                if(!environmentInitialized) {
                    environmentInitialized=true;
                    if(intro()){spawnOrigin=position();pitAnchor=true;}
                    WorldBossEnvironment.begin(this);
                }
                if(pitAnchor) {
                    double dx=getX()-spawnOrigin.x,dz=getZ()-spawnOrigin.z;
                    if(dx*dx+dz*dz>1600)pitAnchor=false;
                    else {setPos(getX(),spawnOrigin.y,getZ());setDeltaMovement(getDeltaMovement().x,0,getDeltaMovement().z);fallDistance=0;}
                }
            }
            super.tick();if(level().isClientSide)return;
            if(pitAnchor&&isAlive()) {
                // Like the original groundY anchor, keep the authored rise at the rim; normal gravity resumes outside the crater.
                setPos(getX(),spawnOrigin.y,getZ());setDeltaMovement(getDeltaMovement().x,0,getDeltaMovement().z);fallDistance=0;
                if(clip()==Clips.WALK&&!isNoAi()) {var f=forward();setPos(getX()+f.x*.15,spawnOrigin.y,getZ()+f.z*.15);}
            }
            bossBar.setProgress(Math.max(0,Math.min(1,getHealth()/getMaxHealth())));
            if(!isAlive()){meteors.clear();return;}
            int age=(int)((int)level().getGameTime()-start());var c=Clips.get(clip());
            for(var event:c.events())if(event.tick()==age)WorldBossCombat.event(this,event.type(),age);
            WorldBossCombat.continuous(this,age);WorldBossCombat.meteors(this);
            if(!c.loops()) {
                getNavigation().stop();
                if(clip()==Clips.CHARGE&&age>=42&&age<86&&!isNoAi()) {
                    var f=forward();setDeltaMovement(f.x*.55,getDeltaMovement().y,f.z*.55);hasImpulse=true;
                }
                if(age>=c.ticks()){play(Clips.IDLE);cooldown=30+random.nextInt(31);}
                return;
            }
            if(isNoAi())return;
            var target=getTarget();
            if(!WorldBossCombat.eligibleTarget(this,target)) {
                setTarget(null);int walking=getNavigation().isDone()?Clips.IDLE:Clips.WALK;if(clip()!=walking)play(walking);return;
            }
            double distance=position().distanceTo(target.position());
            if(distance>56) {if(clip()!=Clips.WALK)play(Clips.WALK);turnTowards(target.position(),1.4F);getNavigation().moveTo(target,1);return;}
            getNavigation().stop();
            if(clip()==Clips.WALK)play(Clips.IDLE);
            getLookControl().setLookAt(target,15,15);turnTowards(target.position(),2.2F);
            if(cooldown--<=0){int next=choose(distance);lastAttack=next;actions++;play(next);}
        }
        private void turnTowards(Vec3 to,float limit) {
            float desired=(float)Math.toDegrees(Math.atan2(-(to.x-getX()),to.z-getZ()));
            setYRot(getYRot()+net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(desired-getYRot()),-limit,limit));yBodyRot=getYRot();
        }
        private int choose(double distance) {
            if(actions>=6&&actions%6==0)return Clips.WORLDBREAKER;
            int[] candidates=distance<28?new int[]{Clips.BITE,Clips.COMBO,Clips.RAM,Clips.LRAKE,Clips.RUPPER,Clips.TSWEEP,Clips.TSLAM,Clips.STOMP,Clips.TWIN,Clips.NOVA}
                :distance<44?new int[]{Clips.CHARGE,Clips.BREATH,Clips.TWIN,Clips.ROAR,Clips.METEOR,Clips.NOVA}:new int[]{Clips.LANCE,Clips.METEOR,Clips.BREATH,Clips.CHARGE,Clips.ROAR};
            int index=random.nextInt(candidates.length);if(candidates[index]==lastAttack)index=(index+1)%candidates.length;
            return candidates[index];
        }
        @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);bossBar.addPlayer(player);}
        @Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);bossBar.removePlayer(player);}
        @Override public void setCustomName(net.minecraft.network.chat.Component name){super.setCustomName(name);if(bossBar!=null)bossBar.setName(getDisplayName());}
        @Override public void die(DamageSource source){meteors.clear();getNavigation().stop();if(!level().isClientSide)WorldBossEnvironment.release(this);super.die(source);}
        private void clearCombatLeases(){meteors.clear();bossBar.removeAllPlayers();if(!level().isClientSide)WorldBossEnvironment.release(this);}
        @Override public void remove(RemovalReason reason){clearCombatLeases();super.remove(reason);}
        @Override protected void removeAfterChangingDimensions(){clearCombatLeases();super.removeAfterChangingDimensions();}
        @Override public void onRemovedFromLevel(){clearCombatLeases();super.onRemovedFromLevel();}
        @Override public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putBoolean("WorldBossEnvironmentInitialized",environmentInitialized);tag.putBoolean("WorldBossPitAnchor",pitAnchor);
            tag.putDouble("WorldBossSpawnX",spawnOrigin.x);tag.putDouble("WorldBossSpawnY",spawnOrigin.y);tag.putDouble("WorldBossSpawnZ",spawnOrigin.z);
            tag.putInt("WorldBossActions",actions);tag.putInt("WorldBossLastAttack",lastAttack);tag.putInt("WorldBossCooldown",Math.max(0,cooldown));
        }
        @Override public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            environmentInitialized=tag.getBoolean("WorldBossEnvironmentInitialized");pitAnchor=tag.getBoolean("WorldBossPitAnchor");
            spawnOrigin=new Vec3(tag.getDouble("WorldBossSpawnX"),tag.getDouble("WorldBossSpawnY"),tag.getDouble("WorldBossSpawnZ"));
            actions=Math.max(0,tag.getInt("WorldBossActions"));lastAttack=Math.max(-1,Math.min(18,tag.getInt("WorldBossLastAttack")));cooldown=Math.max(20,Math.min(100,tag.getInt("WorldBossCooldown")));
            // Reloads do not replay old impacts or restore a stale target/projectile lease.
            play(tag.contains("WorldBossActions")?Clips.IDLE:Clips.SPAWN);setTarget(null);
        }
        @Override public boolean displayFireAnimation(){return false;}
    }
    private WorldBossContent() {}
}
