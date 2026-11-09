package cn.academy.port.titan;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Server controls the encounter; the client only plays synced GeckoLib clips and particles. */
public class TitanEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> LIMB_SWING = SynchedEntityData.defineId(TitanEntity.class,EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent boss = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private long cityNaturalExpires;
    public void markCityNatural(){cityNaturalExpires=level().getGameTime()+24000;getPersistentData().putLong("academy_city_titan_expiry",cityNaturalExpires);}
    private TitanProfiles.Ability casting;
    private Vec3 castOrigin, castEnd;
    private LivingEntity castTarget;
    private LivingEntity meleeTarget;
    private int castAge, meleeAge, firstCooldown=65, secondCooldown=125, meleeCooldown=20;
    private double currentMeleeReach;
    private boolean secondNext, enraged, meleeBNext;
    private boolean limbSwing, limbVariantB, castingSecond, meleeImpactSoundPlayed;
    private float limbTailYaw, previousLimbYaw;
    private Vec3 previousLimbPosition;
    private final Set<UUID> limbHitVictims=new HashSet<>();

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);builder.define(LIMB_SWING,0);
    }
    public int limbSwingCode(){return entityData.get(LIMB_SWING);}
    public static boolean limbCodeVariantB(int code){return (code&1)!=0;}
    public static double limbCodeTime(int code,float partialTick){return Math.max(0,((code>>1)-1+partialTick)/20.0);}

    public TitanEntity(EntityType<? extends Monster> type, Level level) {
        super(type,level);
        xpReward=200;
        if(profile().mobility()==TitanProfiles.Mobility.FLY) {
            moveControl=new FlyingMoveControl(this,12,true);
            navigation=new FlyingPathNavigation(this,level);
            setNoGravity(true);
        } else if(profile().mobility()==TitanProfiles.Mobility.SWIM) {
            moveControl=new SmoothSwimmingMoveControl(this,85,10,.12f,.55f,true);
            navigation=new WaterBoundPathNavigation(this,level);
        }
    }
    public String species() {return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();}
    public TitanProfiles.Profile profile() {return TitanProfiles.get(species());}
    public boolean enraged() {return enraged;}
    @Override protected SoundEvent getHurtSound(DamageSource source) {return TitanSounds.sound(species(),"hurt");}
    @Override protected SoundEvent getDeathSound() {return TitanSounds.sound(species(),"death");}
    @Override protected float getSoundVolume() {return 1.6f;}
    @Override protected void playStepSound(BlockPos pos,BlockState state) {
        if(profile().mobility()==TitanProfiles.Mobility.GROUND)
            TitanSounds.play(this,"step",position(),3.0f);
    }
    /** Limit multi-victim attacks to one heavy hit layer per swing. */
    public void meleeImpactSound(Vec3 position) {
        if(!meleeImpactSoundPlayed) {
            TitanSounds.play(this,limbVariantB?"hit_b":"hit_a",position,5.0f);
            meleeImpactSoundPlayed=true;
        }
    }
    public static AttributeSupplier.Builder attributes(TitanProfiles.Profile p) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,p.health())
            .add(Attributes.ARMOR,p.armor()).add(Attributes.ATTACK_DAMAGE,p.attack())
            .add(Attributes.MOVEMENT_SPEED,p.speed()).add(Attributes.FLYING_SPEED,p.speed())
            .add(Attributes.FOLLOW_RANGE,112).add(Attributes.STEP_HEIGHT,1.0)
            .add(Attributes.KNOCKBACK_RESISTANCE,.95);
    }
    @Override protected void registerGoals() {
        if(profile().mobility()==TitanProfiles.Mobility.GROUND) {
            goalSelector.addGoal(0,new FloatGoal(this));
            goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.7));
        }
        goalSelector.addGoal(1,new ChaseGoal());
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,32));
        goalSelector.addGoal(7,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,10,false,false,
            e->!(e instanceof Player p) || (!p.isCreative() && !p.isSpectator())));
    }
    private boolean busy() {return casting!=null || meleeAge>0;}
    private void halt() {
        getNavigation().stop();
        getMoveControl().setWantedPosition(getX(),getY(),getZ(),0);
        setSpeed(0);
        Vec3 v=getDeltaMovement();
        setDeltaMovement(0,profile().mobility()==TitanProfiles.Mobility.GROUND?v.y:0,0);
    }
    private final class ChaseGoal extends Goal {
        private int pathDelay;
        ChaseGoal(){setFlags(EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK));}
        @Override public boolean canUse(){return getTarget()!=null && getTarget().isAlive();}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void stop(){getNavigation().stop();}
        @Override public void tick() {
            LivingEntity target=getTarget();
            if(target==null)return;
            if(busy()){halt();return;}
            getLookControl().setLookAt(target,25,25);
            double standOff=Math.max(8,getBbWidth()*.5+3);
            if(distanceToSqr(target)<=standOff*standOff){halt();return;}
            var mobility=profile().mobility();
            if(mobility==TitanProfiles.Mobility.FLY) {
                getMoveControl().setWantedPosition(target.getX(),target.getY()+7,target.getZ(),1.0);
                return;
            }
            if(mobility==TitanProfiles.Mobility.SWIM && isInWaterOrBubble()) {
                getMoveControl().setWantedPosition(target.getX(),target.getY(),target.getZ(),1.0);
                return;
            }
            if(--pathDelay>0)return;
            pathDelay=25+random.nextInt(16);
            if(!getNavigation().moveTo(target,1.0)) {
                // Very large bodies often fail the voxel pathfinder. Direct movement still respects collision.
                getMoveControl().setWantedPosition(target.getX(),target.getY(),target.getZ(),1.0);
            }
        }
    }
    @Override public void travel(Vec3 input) {
        if(isEffectiveAi() && profile().mobility()==TitanProfiles.Mobility.SWIM && isInWater()) {
            LivingEntity target=getTarget();
            if(target!=null && target.isAlive() && !busy()) {
                Vec3 toward=target.position().subtract(position());
                double horizontal=Math.hypot(toward.x,toward.z);
                double standOff=Math.max(8,getBbWidth()*.5+3);
                if(horizontal>standOff) {
                    // SmoothSwimmingMoveControl may provide no forward input for
                    // very large mobs. Steer toward the target explicitly while
                    // leaving actual movement to Minecraft's collision solver.
                    Vec3 v=getDeltaMovement();
                    double speed=Math.max(.12,Math.min(.28,profile().speed()*.8));
                    double vertical=Math.max(-.18,Math.min(.18,v.y*.8+toward.y*.02));
                    setDeltaMovement(v.x*.75+toward.x/horizontal*speed*.25,vertical,
                        v.z*.75+toward.z/horizontal*speed*.25);
                } else moveRelative(.1f,input);
            } else moveRelative(.1f,input);
            move(MoverType.SELF,getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(.90));
        } else super.travel(input);
    }
    private void beginCast(ServerLevel level,TitanProfiles.Ability a,LivingEntity target,boolean second) {
        casting=a;castingSecond=second;castAge=0;castTarget=target;
        castOrigin=a.shape()==TitanProfiles.Shape.BEAM?TitanCombat.beamStart(this):position();
        Vec3 desired=target.position();
        if(a.shape()==TitanProfiles.Shape.BEAM) {
            desired=desired.add(0,target.getBbHeight()*.5,0);
            desired=TitanCombat.clippedBeamEnd(level,this,castOrigin,desired,a.range());
        }
        castEnd=desired;
        if(second)secondCooldown=a.cooldownTicks();else firstCooldown=a.cooldownTicks();
        secondNext=!second;halt();
        triggerAnim("action","skill");
        TitanSounds.play(this,second?"charge_b":"charge_a",position().add(0,Math.min(8,getBbHeight()*.4),0),5.0f);
        TitanVfx.telegraph(level,species(),a,castOrigin,castEnd,0);
    }
    private void updateCombat(ServerLevel level) {
        if(isNoAi() || !isAlive())return;
        firstCooldown=Math.max(0,firstCooldown-1);secondCooldown=Math.max(0,secondCooldown-1);
        meleeCooldown=Math.max(0,meleeCooldown-1);
        if(!enraged && getHealth()<=getMaxHealth()*.5f) {
            enraged=true;firstCooldown=Math.min(firstCooldown,65);secondCooldown=Math.min(secondCooldown,110);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(profile().speed()*1.12);
            TitanSounds.play(this,"roar",position().add(0,Math.min(8,getBbHeight()*.4),0),6.0f);
            triggerAnim("action","roar");
        }
        if(casting!=null) {
            halt();castAge++;
            // Track through most of the windup, then lock for the final warning.
            // The same castEnd feeds both the visible telegraph and the damage resolver.
            if(castAge<casting.windupTicks()-8 && castTarget!=null && TitanCombat.eligible(this,castTarget)
                && distanceToSqr(castTarget)<=casting.range()*casting.range()*1.21) {
                Vec3 desired=castTarget.position();
                if(casting.shape()==TitanProfiles.Shape.BEAM) {
                    desired=desired.add(0,castTarget.getBbHeight()*.5,0);
                    desired=TitanCombat.clippedBeamEnd(level,this,castOrigin,desired,casting.range());
                }
                castEnd=desired;
            }
            if(castAge<casting.windupTicks() && castAge%5==0)TitanVfx.telegraph(level,species(),casting,castOrigin,castEnd,castAge);
            if(castAge==casting.windupTicks()) {
                TitanCombat.resolve(level,this,casting,castOrigin,castEnd);
                TitanSounds.play(this,castingSecond?"skill_b":"skill_a",
                    casting.shape()==TitanProfiles.Shape.RING?castOrigin:castEnd,6.0f);
            }
            if(castAge>=Math.max(56,casting.windupTicks()+8)){casting=null;castTarget=null;}
            return;
        }
        if(meleeAge>0) {
            halt();meleeAge++;
            if(meleeAge==8)TitanSounds.play(this,limbVariantB?"swing_b":"swing_a",
                position().add(0,Math.min(8,getBbHeight()*.35),0),5.0f);
            if(limbSwing) {
                // Face the enemy for a sting or tentacle strike. Tail swipes
                // turn away gradually so the tail reaches around from behind.
                float rotation=Mth.approachDegrees(yBodyRot,limbTailYaw,16.0f);
                yBodyRot=rotation;setYRot(rotation);yHeadRot=rotation;
            }
            // A short collision-aware step gives each strike some forward weight.
            // Keep a stand-off so large models do not push through their target.
            if(meleeAge>=5 && meleeAge<=9 && meleeTarget!=null && meleeTarget.isAlive()) {
                Vec3 toTarget=meleeTarget.position().subtract(position());
                double horizontal=Math.hypot(toTarget.x,toTarget.z);
                if(horizontal>Math.max(4,getBbWidth()*.5+1.5))
                    move(MoverType.SELF,new Vec3(toTarget.x/horizontal*.16,0,toTarget.z/horizontal*.16));
            }
            if(limbSwing) {
                entityData.set(LIMB_SWING,(meleeAge<<1)|(limbVariantB?1:0));
                if(meleeAge>=13 && meleeAge<=23) {
                    TitanLimbCollision.sweep(level,this,limbVariantB,(meleeAge-2)/20.0,(meleeAge-1)/20.0,
                        previousLimbPosition,previousLimbYaw,limbHitVictims);
                }
                previousLimbPosition=position();previousLimbYaw=yBodyRot;
            } else if(meleeAge==14)TitanCombat.melee(level,this,meleeTarget,currentMeleeReach);
            if(meleeAge>=36){meleeAge=0;meleeTarget=null;limbSwing=false;entityData.set(LIMB_SWING,0);limbHitVictims.clear();}
            return;
        }
        LivingEntity target=getTarget();
        if(target==null || !TitanCombat.eligible(this,target))return;
        double d=distanceToSqr(target);
        TitanProfiles.Ability preferred=secondNext?profile().second():profile().first();
        TitanProfiles.Ability alternate=secondNext?profile().first():profile().second();
        if((secondNext?secondCooldown:firstCooldown)==0 && inAbilityRange(preferred,target,d)) {
            beginCast(level,preferred,target,secondNext);return;
        }
        if((secondNext?firstCooldown:secondCooldown)==0 && inAbilityRange(alternate,target,d)) {
            beginCast(level,alternate,target,!secondNext);return;
        }
        // The scorpion uses its claws at close range; its tail reaches farther
        // behind the body after the turn and must physically touch the victim.
        boolean selectedB=meleeBNext || (species().equals("dune_scorpion") && d<20*20);
        double reach=meleeReach(selectedB);
        if(meleeCooldown==0 && d<=reach*reach) {
            meleeTarget=target;currentMeleeReach=reach;meleeAge=1;meleeCooldown=enraged?45:60;halt();
            limbVariantB=selectedB;limbSwing=TitanBoneRig.supported(species(),limbVariantB);meleeImpactSoundPlayed=false;
            if(limbSwing) {
                previousLimbPosition=position();previousLimbYaw=yBodyRot;limbHitVictims.clear();
                double dx=target.getX()-getX(),dz=target.getZ()-getZ();
                float faceTarget=(float)Math.toDegrees(Math.atan2(-dx,dz));
                limbTailYaw=Mth.wrapDegrees(faceTarget+(TitanBoneRig.turnsTailToTarget(species(),limbVariantB)?180:0));
                entityData.set(LIMB_SWING,(meleeAge<<1)|(limbVariantB?1:0));
            }
            triggerAnim("action",selectedB?"attack_b":"attack_a");
            meleeBNext=!selectedB;
        }
    }
    private double meleeReach(boolean variantB) {
        double ordinary=Math.max(12,getBbWidth()*.5+8);
        if(species().equals("myco_frog") && !variantB)return Math.max(28,ordinary);
        if(species().equals("ironback"))return Math.max(24,ordinary);
        if(species().equals("dune_scorpion") && !variantB)return Math.max(25,ordinary);
        if(species().equals("lava_rex") && variantB)return Math.max(19,ordinary);
        if(species().equals("tidal_sail") && variantB)return Math.max(22,ordinary);
        return ordinary;
    }
    private boolean inAbilityRange(TitanProfiles.Ability ability,LivingEntity target,double distanceSquared) {
        double range=ability.shape()==TitanProfiles.Shape.RING?Math.min(ability.range(),ability.radius()+target.getBbWidth()*.5):ability.range();
        return distanceSquared<=range*range;
    }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide){if(cityNaturalExpires==0)cityNaturalExpires=getPersistentData().getLong("academy_city_titan_expiry");if(cityNaturalExpires>0&&level().getGameTime()>=cityNaturalExpires){discard();return;}}
        if(!level().isClientSide) {
            if(isDeadOrDying() && limbSwing) {
                limbSwing=false;meleeAge=0;meleeTarget=null;limbHitVictims.clear();entityData.set(LIMB_SWING,0);
            }
            boss.setProgress(Math.max(0,getHealth()/getMaxHealth()));
            Component title=getDisplayName().copy();
            if(casting!=null)title=title.copy().append("  ·  ").append(casting.name());
            else if(enraged)title=title.copy().append("  ·  狂暴");
            boss.setName(title);
            if(level() instanceof ServerLevel server)updateCombat(server);
            // Airborne/swimming creatures have no normal ground step callback.
            if(profile().mobility()!=TitanProfiles.Mobility.GROUND && !busy() && tickCount%24==0
                && getDeltaMovement().horizontalDistanceSqr()>.002)
                TitanSounds.play(this,"step",position(),3.0f);
            if(isDeadOrDying() && profile().mobility()==TitanProfiles.Mobility.FLY)setNoGravity(false);
        }
    }
    @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);boss.addPlayer(player);}
    @Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);boss.removePlayer(player);}
    @Override public void remove(RemovalReason reason){boss.removeAllPlayers();super.remove(reason);}
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putBoolean("TitanEnraged",enraged);tag.putBoolean("TitanSecondNext",secondNext);
        tag.putBoolean("TitanMeleeBNext",meleeBNext);
        tag.putInt("TitanFirstCooldown",firstCooldown);tag.putInt("TitanSecondCooldown",secondCooldown);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);enraged=tag.getBoolean("TitanEnraged");secondNext=tag.getBoolean("TitanSecondNext");
        meleeBNext=tag.getBoolean("TitanMeleeBNext");
        if(tag.contains("TitanFirstCooldown"))firstCooldown=Math.max(0,tag.getInt("TitanFirstCooldown"));
        if(tag.contains("TitanSecondCooldown"))secondCooldown=Math.max(0,tag.getInt("TitanSecondCooldown"));
        if(enraged)getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(profile().speed()*1.12);
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this,"locomotion",8,state -> {
            String action=isDeadOrDying()?"death":state.isMoving()?"walk":"idle";
            if(state.isMoving() && !isDeadOrDying()) {
                double distance=Math.hypot(getX()-xo,getZ()-zo);
                double ref=switch(species()) {
                    case "myco_frog" -> .1097;case "lava_rex" -> .1083;case "frost_bastion" -> .0902;
                    case "grove_titan" -> .0690;case "crystal_mantis" -> .1189;case "dune_scorpion" -> .1027;
                    case "ossuary_dragon" -> .0491;default -> .1742;
                };
                state.setControllerSpeed((float)Math.max(.4,Math.min(2.4,distance/ref)));
            } else state.setControllerSpeed(1.0f);
            RawAnimation anim=isDeadOrDying()?RawAnimation.begin().thenPlay("animation."+species()+"."+action)
                :RawAnimation.begin().thenLoop("animation."+species()+"."+action);
            return state.setAndContinue(anim);
        }));
        controllers.add(new AnimationController<>(this,"action",1,state -> PlayState.STOP)
            .triggerableAnim("attack",RawAnimation.begin().thenPlay("animation."+species()+".attack"))
            .triggerableAnim("attack_a",RawAnimation.begin().thenPlay("animation."+species()+".attack_a"))
            .triggerableAnim("attack_b",RawAnimation.begin().thenPlay("animation."+species()+".attack_b"))
            .triggerableAnim("roar",RawAnimation.begin().thenPlay("animation."+species()+".roar"))
            .triggerableAnim("hit",RawAnimation.begin().thenPlay("animation."+species()+".hit"))
            .triggerableAnim("skill",RawAnimation.begin().thenPlay("animation."+species()+".skill")));
    }
    @Override protected void tickDeath(){if(++deathTime>=55 && !level().isClientSide)remove(RemovalReason.KILLED);}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
}
