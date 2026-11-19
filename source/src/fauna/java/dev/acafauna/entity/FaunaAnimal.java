package dev.acafauna.entity;

import dev.acafauna.core.*;
import dev.acafauna.entity.ai.EcologyGoals;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.control.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Shared physiological rules; player disposition is independent of the prey food chain. */
public class FaunaAnimal extends Animal implements GeoEntity {
    public static final int NORMAL=0,EATING=1,RESTING=2,ALERT=3,SPECIAL_A=4,SPECIAL_B=5,CLIMBING=6;
    private static final int GROUNDED=0, FLAPPING=1, SWIMMING=2, RUNNING=3, GLIDING=4,
        TAKING_OFF=5, APPROACHING_LANDING=6, TOUCHDOWN=7, WALKING=8, ALERT_STAND=9;
    private static final int DEFAULT_TAKEOFF_TICKS=17;
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(FaunaAnimal.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MOVEMENT=SynchedEntityData.defineId(FaunaAnimal.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> FLIGHT_BANK=SynchedEntityData.defineId(FaunaAnimal.class,EntityDataSerializers.BYTE);
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this);
    private long actionUntil;
    private int hunger=600,panicTicks,attackCooldown,warningTicks,forageCooldown;
    private long angryUntil;
    @Nullable private UUID angryAt;
    private int dryTicks, wingTransitionTicks;
    private float wingBank, lastFlightCourse, visualBank, previousVisualBank;
    private float visualSpeed, swimPitch, previousSwimPitch, swimBank, previousSwimBank;
    private boolean trackingFlightCourse;

    public static FaunaAnimal create(EntityType<FaunaAnimal> type,Level world,Species p) {
        return switch(p.mobility()) {
            case "FLY"->new Bird(type,world);
            case "CLIMB"->new Climber(type,world);
            case "AMPHIBIOUS"->new Amphibian(type,world);
            case "AQUATIC","AQUATIC_MAMMAL"->new Swimmer(type,world);
            default->new FaunaAnimal(type,world);
        };
    }
    public FaunaAnimal(EntityType<? extends FaunaAnimal> type,Level world) {
        super(type,world);
        setPathfindingMalus(PathType.WATER,profile().amphibious()||profile().aquatic()?0:-1);
        hunger=600+getRandom().nextInt(2400);
        if(profile().flies())moveControl=new AirWaterMoveControl(this);
        else if(profile().aquatic()||profile().amphibious())
            moveControl=new SmoothSwimmingMoveControl(this,85,10,.12F,.5F,true);
    }
    public Species profile(){return Species.of(getType());}
    public boolean canUseWings(){return profile().flies()&&!isBaby();}
    @Override protected EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose){
        if(isBaby()&&profile().juvenile_model()&&profile().juvenile_height()>0)
            return getType().getDimensions().scale(.5F,profile().juvenile_height()/profile().height());
        return super.getDefaultDimensions(pose);
    }
    public int action(){return entityData.get(ACTION);}
    public int hunger(){return hunger;}
    public void setHunger(int value){hunger=Math.max(0,Math.min(12000,value));}
    public boolean hungry(){return hunger>=2400;}
    public boolean panicking(){return panicTicks>0;}
    public boolean runningAnimation(){int movement=entityData.get(MOVEMENT);return movement==RUNNING||movement==ALERT_STAND;}
    public boolean glidingAnimation(){return entityData.get(MOVEMENT)==GLIDING;}
    public boolean takingOffAnimation(){return entityData.get(MOVEMENT)==TAKING_OFF;}
    public boolean approachingLandingAnimation(){return entityData.get(MOVEMENT)==APPROACHING_LANDING;}
    public int flightBankDegrees(){return entityData.get(FLIGHT_BANK);}
    public float renderedFlightBank(float partialTick){return Mth.lerp(partialTick,previousVisualBank,visualBank);}
    public float renderedSwimPitch(float partialTick){return Mth.lerp(partialTick,previousSwimPitch,swimPitch);}
    public float renderedSwimBank(float partialTick){return Mth.lerp(partialTick,previousSwimBank,swimBank);}
    public boolean isActiveTime(){return profile().activity().equals("night")?!level().isDay():level().isDay();}
    public void pose(int action,int ticks){entityData.set(ACTION,action);actionUntil=level().getGameTime()+ticks;}
    public void sound(String event,float volume){playSound(FaunaSounds.get(profile(),event),volume,getVoicePitch());}
    public void fed(){
        hunger=0;heal(2);getNavigation().stop();pose(EATING,32);sound("eat",.45F);forageCooldown=1200;
    }
    public void warn(int duration){pose(ALERT,duration);sound("alert",.55F);}
    public boolean hasAngerAt(LivingEntity target){return angryAt!=null&&angryAt.equals(target.getUUID())&&level().getGameTime()<angryUntil;}
    public boolean validPlayer(Player player){
        return player.isAlive()&&!player.isCreative()&&!player.isSpectator()&&!isBaby();
    }
    public boolean mayHuntPlayer(Player player) {
        return profile().conditional()&&FaunaConfig.PLAYER_PREDATION.get()&&hungry()
            &&level().getDifficulty()!=Difficulty.PEACEFUL&&validPlayer(player);
    }
    public boolean mayTargetPlayer(Player player) {
        if(profile().passive()||!validPlayer(player))return false;
        return hasAngerAt(player)||(mayHuntPlayer(player)&&warningTicks>=20);
    }
    @Override public void setTarget(@Nullable LivingEntity target) {
        if(target instanceof Player player&&!mayTargetPlayer(player)){super.setTarget(null);return;}
        super.setTarget(target);
    }
    public boolean isPrey(LivingEntity target){
        return target.isAlive()&&target!=this&&!(target instanceof Player)
            &&!(target instanceof net.minecraft.world.entity.npc.AbstractVillager)
            &&!(target instanceof TamableAnimal a&&a.isTame())
            &&!(target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse h&&h.isTamed())
            &&target.getType().is(profile().prey())&&target.getType()!=getType();
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        super.defineSynchedData(b);
        b.define(ACTION,NORMAL);
        b.define(MOVEMENT,GROUNDED);
        b.define(FLIGHT_BANK,(byte)0);
    }
    @Override public void tick(){
        super.tick();
        if(!level().isClientSide)updateMovementAnimation();
        else {
            float rawSpeed=(float)(Math.hypot(getX()-xo,getZ()-zo)*20.0);
            visualSpeed=Mth.lerp(.34F,visualSpeed,rawSpeed);
            if(profile().flies()){
                previousVisualBank=visualBank;
                visualBank=Mth.lerp(.38F,visualBank,flightBankDegrees());
            }
            previousSwimPitch=swimPitch;
            previousSwimBank=swimBank;
            if(isInWater()&&(profile().aquatic()||profile().amphibious())&&isAlive()){
                double horiz=Math.hypot(getX()-xo,getZ()-zo);
                double dy=getY()-yo;
                float targetPitch=horiz*horiz+dy*dy>.0004?Mth.clamp((float)Math.toDegrees(-Math.atan2(dy,Math.max(.025,horiz))),-34F,34F):0F;
                float yawDelta=Mth.wrapDegrees(yBodyRot-yBodyRotO);
                float targetBank=horiz>.012?Mth.clamp(-yawDelta*2.1F,-22F,22F):0F;
                swimPitch=Mth.lerp(.26F,swimPitch,targetPitch);
                swimBank=Mth.lerp(.28F,swimBank,targetBank);
            }else{
                swimPitch=Mth.lerp(.25F,swimPitch,0F);
                swimBank=Mth.lerp(.25F,swimBank,0F);
            }
        }
    }
    private static boolean airborne(int movement){
        return movement==FLAPPING||movement==GLIDING||movement==TAKING_OFF||movement==APPROACHING_LANDING;
    }
    private void updateMovementAnimation(){
        int previous=entityData.get(MOVEMENT);
        double dx=getX()-xo,dz=getZ()-zo;
        int movement=isInWater()?SWIMMING:groundMovement(previous,Math.hypot(dx,dz)*20);
        if(canUseWings()&&isAlive())movement=wingMovement(previous,movement);
        entityData.set(MOVEMENT,movement);
        if(profile().flies())updateFlightBank(movement,dx,dz);
    }
    private int groundMovement(int previous,double speed){
        boolean urgent=panicking()||getTarget()!=null;
        boolean alreadyMoving=previous==WALKING||previous==RUNNING;
        if(speed<(alreadyMoving?.08:.14))return urgent?ALERT_STAND:GROUNDED;
        double authored=profile().gait_walk_mps()*(isBaby()?.5:1.0);
        double fastThreshold=authored*(previous==RUNNING?1.45:1.65);
        return urgent||authored>0&&speed>fastThreshold?RUNNING:WALKING;
    }
    private void updateFlightBank(int movement,double dx,double dz){
        float wanted=0;
        boolean cruising=movement==FLAPPING||movement==GLIDING;
        if(cruising&&dx*dx+dz*dz>.025*.025){
            float course=(float)Math.toDegrees(Math.atan2(-dx,dz));
            if(trackingFlightCourse)wanted=Mth.clamp(Mth.wrapDegrees(course-lastFlightCourse)*2.4F,-34F,34F);
            lastFlightCourse=course;
            trackingFlightCourse=true;
        }else trackingFlightCourse=false;
        wingBank=Mth.lerp(.34F,wingBank,wanted);
        entityData.set(FLIGHT_BANK,(byte)Math.round(wingBank));
    }
    private int wingMovement(int previous,int groundMovement){
        if(onGround()||groundMovement==SWIMMING){
            if(airborne(previous))wingTransitionTicks=9;
            if(wingTransitionTicks>0&&(airborne(previous)||previous==TOUCHDOWN)){
                wingTransitionTicks--;
                return TOUCHDOWN;
            }
            wingTransitionTicks=0;
            return groundMovement;
        }

        double vertical=getDeltaMovement().y;
        boolean approaching=previous==APPROACHING_LANDING;
        if(vertical<(approaching?-.008:-.025)&&hasLandingSurface(vertical,approaching)){
            wingTransitionTicks=0;
            return APPROACHING_LANDING;
        }
        if(!airborne(previous))wingTransitionTicks=vertical>.025?takeoffTicks():0;
        if(wingTransitionTicks>0&&vertical>=-.035){
            wingTransitionTicks--;
            return TAKING_OFF;
        }
        wingTransitionTicks=0;
        double glideThreshold=previous==GLIDING?-.017:-.042;
        return vertical<glideThreshold?GLIDING:FLAPPING;
    }
    private int takeoffTicks(){
        int authored=profile().flight_launch_ticks();
        return authored>0?authored:DEFAULT_TAKEOFF_TICKS;
    }
    private boolean hasLandingSurface(double descent,boolean approaching){
        double reach=Mth.clamp(-descent*8,.30,1.35);
        if(approaching)reach=Math.max(.50,reach);
        var feet=footprint();
        return solidSupportBelow(feet,reach)||profile().amphibious()&&waterSurfaceBelow(feet,reach);
    }
    public boolean canWade(){return isInWater()&&!isUnderWater()&&solidSupportBelow(footprint(),.84);}
    private AABB footprint(){
        var body=getBoundingBox();
        return new AABB(body.minX,body.minY,body.minZ,body.maxX,body.minY+.02,body.maxZ);
    }
    private boolean solidSupportBelow(AABB feet,double reach){
        var swept=feet.expandTowards(0,-reach,0);
        // Match the footprint used by vertical collision, including the edge of a branch or slab.
        return Shapes.collide(Direction.Axis.Y,feet,level().getBlockCollisions(this,swept),-reach)>-reach;
    }
    private boolean waterSurfaceBelow(AABB feet,double reach){
        double edge=.00001;
        var low=BlockPos.containing(feet.minX+edge,feet.minY-reach,feet.minZ+edge);
        var high=BlockPos.containing(feet.maxX-edge,feet.minY,feet.maxZ-edge);
        for(var pos:BlockPos.betweenClosed(low,high)){
            if(!level().hasChunkAt(pos))continue;
            var fluid=level().getFluidState(pos);
            if(!fluid.is(FluidTags.WATER))continue;
            double surface=pos.getY()+fluid.getHeight(level(),pos);
            if(surface>=feet.minY-reach&&surface<=feet.minY+edge)return true;
        }
        return false;
    }
    @Override protected void registerGoals() {
        if(!profile().aquatic()&&!profile().amphibious())goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(0,new EcologyGoals.Breathe(this));
        goalSelector.addGoal(1,new EcologyGoals.Flee(this));
        goalSelector.addGoal(2,new EcologyGoals.Hunt(this));
        goalSelector.addGoal(3,new EcologyGoals.Feed(this));
        if(profile().breed())goalSelector.addGoal(4,new BreedGoal(this,1.0));
        if(!profile().conditional())goalSelector.addGoal(5,new TemptGoal(this,1.0,Ingredient.of(profile().food()),false));
        goalSelector.addGoal(5,new FollowParentGoal(this,1.1));
        if(profile().id().equals("two_toed_sloth"))goalSelector.addGoal(6,new EcologyGoals.Hang(this));
        goalSelector.addGoal(6,new EcologyGoals.Habitat(this));
        goalSelector.addGoal(6,new EcologyGoals.LandedRest(this));
        goalSelector.addGoal(7,new EcologyGoals.Signature(this));
        goalSelector.addGoal(8,new EcologyGoals.Herd(this));
        if(profile().flies()){
            goalSelector.addGoal(9,new WaterAvoidingRandomFlyingGoal(this,1.0){@Override public boolean canUse(){return canUseWings()&&super.canUse();}});
            goalSelector.addGoal(9,new WaterAvoidingRandomStrollGoal(this,1.0){@Override public boolean canUse(){return isBaby()&&super.canUse();}});
        }
        else if(profile().aquatic())goalSelector.addGoal(9,new RandomSwimmingGoal(this,1.0,60));
        else goalSelector.addGoal(9,new WaterAvoidingRandomStrollGoal(this,1.0));
        goalSelector.addGoal(10,new LookAtPlayerGoal(this,Player.class,6));
        goalSelector.addGoal(11,new RandomLookAroundGoal(this));
    }
    @Override protected PathNavigation createNavigation(Level world) {
        if(canUseWings()) {
            var nav=new FlyingPathNavigation(this,world);nav.setCanOpenDoors(false);nav.setCanFloat(profile().amphibious());return nav;
        }
        if(profile().aquatic())return new WaterBoundPathNavigation(this,world);
        if(profile().amphibious())return new AmphibiousPathNavigation(this,world);
        if(profile().climbs())return new WallClimberNavigation(this,world);
        return new GroundPathNavigation(this,world);
    }
    @Override public boolean hurt(DamageSource source,float damage) {
        boolean result=super.hurt(source,damage);
        if(result&&!level().isClientSide) {
            pose(NORMAL,0);panicTicks=100;
            if(source.getEntity() instanceof LivingEntity aggressor&&!isBaby()&&!profile().passive()
                &&(!(aggressor instanceof Player p)||validPlayer(p))) {
                angryAt=aggressor.getUUID();angryUntil=level().getGameTime()+600;
                panicTicks=0;setTarget(aggressor);
            } else setTarget(null);
        }
        return result;
    }
    @Override public boolean doHurtTarget(Entity target) {
        if(!(target instanceof LivingEntity living)||!living.isAlive()||attackCooldown>0)return false;
        if(living instanceof Player player&&!mayTargetPlayer(player))return false;
        if(!hasLineOfSight(living)||distanceToSqr(living)>Math.pow(Math.max(1.4,getBbWidth()*.65+living.getBbWidth()*.5+1),2))return false;
        triggerAnim("action","attack");attackCooldown=24;sound("attack",.65F);
        boolean hit=super.doHurtTarget(target);
        if(hit&&!living.isAlive()){hunger=0;setTarget(null);pose(EATING,40);}
        return hit;
    }
    @Override protected void customServerAiStep() {
        if(hunger<12000)hunger++;
        if(panicTicks>0)panicTicks--;
        if(attackCooldown>0)attackCooldown--;
        if(forageCooldown>0)forageCooldown--;
        if(action()!=NORMAL&&level().getGameTime()>=actionUntil)entityData.set(ACTION,NORMAL);
        if(angryAt!=null&&level().getGameTime()>=angryUntil){angryAt=null;if(getTarget()!=null)setTarget(null);}
        var target=getTarget();
        if(target!=null&&(!target.isAlive()||distanceToSqr(target)>24*24
            ||target instanceof Player p&&!mayTargetPlayer(p))){setTarget(null);warningTicks=0;}
        if(profile().aquatic()&&!isInWater()) {
            if(++dryTicks%40==0&&dryTicks>200)hurt(damageSources().dryOut(),1);
        } else dryTicks=0;
        if(profile().climbs()&&horizontalCollision&&touchesTree()) {
            setDeltaMovement(getDeltaMovement().x,.14,getDeltaMovement().z);
            fallDistance=0;pose(CLIMBING,4);
        }
        if(canUseWings()&&!onGround()&&getDeltaMovement().y<0)
            setDeltaMovement(getDeltaMovement().multiply(1,.65,1));
        super.customServerAiStep();
    }
    public void warningTick(){warningTicks++;}
    public void resetWarning(){warningTicks=0;}
    public int warningTicks(){return warningTicks;}
    public boolean touchesTree(){
        for(var d:Direction.Plane.HORIZONTAL) {
            var state=level().getBlockState(blockPosition().relative(d));
            if(state.is(BlockTags.LOGS)||state.is(BlockTags.LEAVES))return true;
        }
        return false;
    }
    @Override public boolean onClimbable(){return profile().climbs()&&horizontalCollision&&touchesTree();}
    @Override public int getMaxAirSupply(){return profile().aquatic()||profile().amphibious()?4800:300;}
    @Override protected int increaseAirSupply(int air){
        return profile().mobility().equals("AQUATIC_MAMMAL")||profile().id().equals("harbor_seal")||profile().id().equals("sea_otter")?getMaxAirSupply():super.increaseAirSupply(air);
    }
    @Override public void travel(Vec3 direction) {
        if(isEffectiveAi()&&isInWater()&&(profile().aquatic()||profile().amphibious())) {
            moveRelative(.035F,direction);
            if(action()==SPECIAL_B&&profile().id().equals("brown_bear")&&canWade()){
                // Hold the feet against the bed during the fishing pose despite idle swimming buoyancy.
                var drift=getDeltaMovement();
                setDeltaMovement(drift.x,Math.min(drift.y,-.045),drift.z);
            }
            move(MoverType.SELF,getDeltaMovement());setDeltaMovement(getDeltaMovement().scale(.9));
        }else super.travel(direction);
    }
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source) {
        return !canUseWings()&&!profile().climbs()&&super.causeFallDamage(distance,multiplier,source);
    }
    @Override public float getWalkTargetValue(BlockPos pos,LevelReader world) {
        if(profile().aquatic()&&world.getFluidState(pos).is(FluidTags.WATER))return 10;
        return super.getWalkTargetValue(pos,world);
    }
    @Override public boolean isFood(ItemStack stack){return stack.is(profile().food());}
    @Override public boolean canFallInLove(){return profile().breed()&&super.canFallInLove();}
    @Override public boolean canMate(Animal mate){return mate.getType()==getType()&&profile().breed()&&super.canMate(mate);}
    @Nullable @Override public AgeableMob getBreedOffspring(ServerLevel world,AgeableMob mate) {
        return profile().breed()&&mate.getType()==getType()?FaunaCore.TYPES.get(profile().id()).get().create(world):null;
    }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand) {
        var stack=player.getItemInHand(hand);
        if(!isFood(stack))return super.mobInteract(player,hand);
        if(level().isClientSide)return InteractionResult.SUCCESS;
        if(profile().breed()&&(isBaby()||getAge()==0&&canFallInLove())) {
            var result=super.mobInteract(player,hand);fed();return result;
        }
        if(hunger<200&&getHealth()>=getMaxHealth())return InteractionResult.PASS;
        stack.consume(1,player);fed();return InteractionResult.SUCCESS;
    }
    public boolean canForage(){return forageCooldown==0&&hungry();}
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);tag.putInt("FaunaDataVersion",1);tag.putInt("FaunaHunger",hunger);
        if(angryAt!=null) {tag.putUUID("FaunaAngryAt",angryAt);tag.putLong("FaunaAngryUntil",angryUntil);}
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);setHunger(tag.contains("FaunaHunger")?tag.getInt("FaunaHunger"):600);
        angryAt=tag.hasUUID("FaunaAngryAt")?tag.getUUID("FaunaAngryAt"):null;
        angryUntil=tag.getLong("FaunaAngryUntil");entityData.set(ACTION,NORMAL);
    }
    @Override public SoundSource getSoundSource(){return SoundSource.NEUTRAL;}
    @Override public int getAmbientSoundInterval(){return 240+Math.abs(profile().id().hashCode()%160);}
    @Override protected SoundEvent getAmbientSound(){return FaunaSounds.get(profile(),"ambient");}
    @Override protected SoundEvent getHurtSound(DamageSource source){return FaunaSounds.get(profile(),"hurt");}
    @Override protected SoundEvent getDeathSound(){return FaunaSounds.get(profile(),"death");}
    @Override protected void playStepSound(BlockPos pos,BlockState state){sound("step",.12F);}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    private RawAnimation clip(String name){return RawAnimation.begin().thenLoop("animation."+profile().id()+"."+name);}
    private RawAnimation transition(String name){return RawAnimation.begin().thenPlayAndHold("animation."+profile().id()+"."+name);}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<FaunaAnimal>(this,"locomotion",6,state-> {
            state.getController().setAnimationSpeed(1.0);
            state.getController().transitionLength(takingOffAnimation()&&takeoffTicks()<DEFAULT_TAKEOFF_TICKS?2:6);
            double actualSpeed=level().isClientSide?visualSpeed:Math.hypot(getX()-xo,getZ()-zo)*20.0;
            double bodyScale=isBaby()?.5:1.0;
            String name;
            if(isDeadOrDying())return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation."+profile().id()+".death"));
            else if(hurtTime>0)name="hurt";
            else if(action()==CLIMBING)name="climb";
            else if(canUseWings()){
                int movement=entityData.get(MOVEMENT);
                if(movement==TAKING_OFF)return state.setAndContinue(transition("takeoff"));
                if(movement==APPROACHING_LANDING||movement==TOUCHDOWN){
                    state.getController().setAnimationSpeed(Math.max(1.25,profile().flight_landing_ticks()/16.0));
                    return state.setAndContinue(transition("land"));
                }
                if(airborne(movement))return state.setAndContinue(clip(glidingAnimation()?"glide":"fly"));
                name=ordinaryMovement(state.isMoving());
            }
            else name=ordinaryMovement(state.isMoving());
            float authoredSpeed=name.equals("walk")?profile().gait_walk_mps():name.equals("run")?profile().gait_run_mps():0;
            if(authoredSpeed>0){
                state.getController().setAnimationSpeed(net.minecraft.util.Mth.clamp(actualSpeed/(authoredSpeed*bodyScale),.38,2.35));
            }else if(name.equals("idle")||name.equals("rest")){
                double tempo=.89+Math.floorMod(getUUID().getLeastSignificantBits(),25)/100.0;
                state.getController().setAnimationSpeed(tempo);
            }
            return state.setAndContinue(clip(name));
        }));
        var action=new AnimationController<FaunaAnimal>(this,"action",3,state->PlayState.STOP);
        action.triggerableAnim("attack",RawAnimation.begin().thenPlay("animation."+profile().id()+".attack"));
        controllers.add(action);
    }
    private String ordinaryMovement(boolean moving){
        int movement=entityData.get(MOVEMENT);
        if(movement==RUNNING)return "run";
        if(movement==WALKING)return "walk";
        if((moving||action()==NORMAL)&&movement==SWIMMING&&(profile().aquatic()||profile().amphibious()))
            return getDeltaMovement().y<-.025?"dive":Math.abs(Mth.wrapDegrees(getYRot()-yRotO))>4?"turn":"swim";
        return switch(action()){case EATING->"eat";case RESTING->"rest";case ALERT->"alert";case SPECIAL_A->"special_a";case SPECIAL_B->"special_b";default->"idle";};
    }
    public static final class Bird extends FaunaAnimal implements FlyingAnimal {
        public Bird(EntityType<? extends FaunaAnimal> t,Level l){super(t,l);}
        @Override public boolean isFlying(){return canUseWings()&&!onGround()&&!isInWater();}
    }
    private static final class AirWaterMoveControl extends FlyingMoveControl {
        private final FaunaAnimal animal;
        private final SmoothSwimmingMoveControl swimming;
        private final MoveControl walking;
        AirWaterMoveControl(FaunaAnimal a){super(a,12,false);animal=a;swimming=new SmoothSwimmingMoveControl(a,85,10,.12F,.5F,true);walking=new MoveControl(a);}
        @Override public void tick(){
            if(animal.isBaby()&&!animal.isInWater()){
                animal.setNoGravity(false);
                if(operation==Operation.MOVE_TO)walking.setWantedPosition(wantedX,animal.getY(),wantedZ,speedModifier);
                walking.tick();if(!walking.hasWanted())operation=Operation.WAIT;return;
            }
            if(!animal.isInWater()){super.tick();return;}
            animal.setNoGravity(false);
            if(operation==Operation.MOVE_TO)swimming.setWantedPosition(wantedX,wantedY,wantedZ,speedModifier);
            swimming.tick();
            if(!swimming.hasWanted())operation=Operation.WAIT;
        }
    }
    public static final class Climber extends FaunaAnimal {
        public Climber(EntityType<? extends FaunaAnimal> t,Level l){super(t,l);}
        @Override protected double getDefaultGravity() {
            // The synced pose is transient. The entity's saved NoGravity flag stays untouched.
            return profile().id().equals("two_toed_sloth")&&action()==SPECIAL_A ? 0.0 : super.getDefaultGravity();
        }
    }
    public static final class Amphibian extends FaunaAnimal {
        public Amphibian(EntityType<? extends FaunaAnimal> t,Level l){super(t,l);}
    }
    public static final class Swimmer extends FaunaAnimal {
        public Swimmer(EntityType<? extends FaunaAnimal> t,Level l){super(t,l);}
        @Override public boolean checkSpawnObstruction(LevelReader world) {
            // The placement predicate already requires a clear, sufficiently deep water volume.
            return world.isUnobstructed(this);
        }
    }
}
