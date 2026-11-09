package cn.academy.port.city.defense;

import cn.academy.port.city.defense.armor.DefenseArmor;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Native falling entrance, articulated melee and private per-operative hit count. Never edits terrain. */
public final class O5OperativeEntity extends GuardianEntity {
    private static final EntityDataAccessor<Boolean> DESCENDING=SynchedEntityData.defineId(O5OperativeEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MOVE=SynchedEntityData.defineId(O5OperativeEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(O5OperativeEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> CONTACT=SynchedEntityData.defineId(O5OperativeEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> LAND=SynchedEntityData.defineId(O5OperativeEntity.class,EntityDataSerializers.LONG);
    private Vec3 landing;private UUID locked;private int sequence;private long hits,nextBlink,nextAttack;private boolean resolved;
    private static final BigInteger[] FACTORIALS=new BigInteger[50];
    static {FACTORIALS[0]=BigInteger.ONE;for(int i=1;i<50;i++)FACTORIALS[i]=FACTORIALS[i-1].multiply(BigInteger.valueOf(i));}
    public O5OperativeEntity(EntityType<? extends O5OperativeEntity> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(DESCENDING,false);b.define(MOVE,-1);b.define(START,-1L);b.define(CONTACT,-100L);b.define(LAND,-100L);}
    public boolean descending(){return entityData.get(DESCENDING);}public int move(){return entityData.get(MOVE);}public long actionStart(){return entityData.get(START);}
    public long contactTick(){return entityData.get(CONTACT);}public long landTick(){return entityData.get(LAND);}
    public long confirmedHits(){return hits;}public boolean armored(){return DefenseArmor.full(this,true);}
    public void prepareDrop(UUID incident,int role,long expires,Vec3 landing,double height){
        super.prepare(incident,role,5,expires,"O5特遣");setCustomName(Component.literal("O5特遣 · "+new String[]{"阿尔法","贝塔","伽马","德尔塔","伊普西隆"}[Math.floorMod(role,5)]));
        setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);this.landing=landing;moveTo(landing.x,landing.y+height,landing.z,role*72,0);
        refreshDimensions();
        entityData.set(DESCENDING,true);setNoGravity(true);setDeltaMovement(0,-.35,0);
    }
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel world)||isRemoved())return;
        if(descending()){
            getNavigation().stop();fallDistance=0;
            if(landing==null){entityData.set(DESCENDING,false);setNoGravity(false);return;}
            if(onGround()||getY()<=landing.y+.10){
                moveTo(landing.x,Math.max(landing.y,getY()),landing.z,getYRot(),0);setDeltaMovement(Vec3.ZERO);setNoGravity(false);entityData.set(DESCENDING,false);entityData.set(LAND,world.getGameTime());
                O5Effect.emit(this,3,0,0,position(),position());world.playSound(null,blockPosition(),SoundEvents.GENERIC_EXPLODE.value(),SoundSource.HOSTILE,.8f,.58f);world.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.HOSTILE,.4f,.7f);
            }else{
                setDeltaMovement(0,-Math.min(.96,.40+(tickCount%200)*.012),0);
                if(tickCount%3==0)world.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,getX(),getY()+1.8,getZ(),3,.18,.7,.18,.02);
            }return;
        }
        if(move()>=0){
            long age=world.getGameTime()-actionStart();var attack=O5Moves.of(move());
            if(!resolved&&age>=attack.hit){resolved=true;var target=locked==null?null:world.getEntity(locked);if(target instanceof LivingEntity enemy)strike(enemy);}
            if(age>=attack.ticks){entityData.set(MOVE,-1);locked=null;}
        }
    }
    @Override protected void tactics(CityDefenseData.Team team){
        if(team==null||descending()||!armored())return;
        var world=(ServerLevel)level();long now=world.getGameTime();var enemy=CityDefense.target(world,team);
        if(enemy==null){setTarget(null);getNavigation().stop();return;}setTarget(enemy);getLookControl().setLookAt(enemy,50,50);
        var toward=enemy.position().subtract(position());float facing=(float)Math.toDegrees(Math.atan2(-toward.x,toward.z));setYRot(facing);yBodyRot=facing;yHeadRot=facing;
        if(move()>=0){getNavigation().stop();return;}
        double distance=distanceToBody(enemy);
        if(distance>4&&distance<42&&now>=nextBlink&&getSensing().hasLineOfSight(enemy)){
            if(blink(world,enemy)){nextBlink=now+38;distance=distanceToBody(enemy);}else nextBlink=now+20;
        }
        if(distance<=5.4&&getSensing().hasLineOfSight(enemy)&&now>=nextAttack){
            int attack=Math.floorMod(sequence++,10);entityData.set(MOVE,attack);entityData.set(START,now);locked=enemy.getUUID();resolved=false;nextAttack=now+O5Moves.of(attack).ticks+3;getNavigation().stop();
            world.playSound(null,blockPosition(),SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.HOSTILE,.65f,.55f+attack*.04f);
        }else getNavigation().moveTo(enemy,1.25);
    }
    private double distanceToBody(LivingEntity target){var box=target.getBoundingBox();Vec3 p=position();return Math.sqrt(new Vec3(net.minecraft.util.Mth.clamp(p.x,box.minX,box.maxX),net.minecraft.util.Mth.clamp(p.y,box.minY,box.maxY),net.minecraft.util.Mth.clamp(p.z,box.minZ,box.maxZ)).distanceToSqr(p));}
    public boolean handles(LivingEntity target){return isAlive()&&armored()&&!descending()&&distanceToBody(target)<=6.2&&getSensing().hasLineOfSight(target);}
    private boolean blink(ServerLevel world,LivingEntity target){
        for(int i=0;i<8;i++){
            double angle=(role()*72+i*45)*Math.PI/180;double radius=Math.max(3.4,target.getBbWidth()*.5+2);
            var at=CityDefense.ground(world,target.position().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));if(at==null)continue;
            var box=getBoundingBox().move(at.subtract(position()));if(!world.getWorldBorder().isWithinBounds(box)||world.getBlockCollisions(this,box).iterator().hasNext())continue;
            var clip=world.clip(new ClipContext(getEyePosition(),at.add(0,getEyeHeight(),0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(clip.getType()!=HitResult.Type.MISS)continue;
            Vec3 before=position();teleportTo(at.x,at.y,at.z);O5Effect.emit(this,0,sequence%10,0,before,position());return true;
        }return false;
    }
    /** Only the current incident threat, in reach and in sight, may receive the one contact. */
    public boolean strike(LivingEntity enemy){
        if(!(level() instanceof ServerLevel world)||!armored()||enemy instanceof GuardianEntity||CityDefense.civilian(enemy)||!enemy.isAlive()||distanceToBody(enemy)>6.2||!getSensing().hasLineOfSight(enemy))return false;
        var t=response()==null?null:CityDefenseData.get(world).team(response());if(t==null||!t.active(world.getGameTime())||!t.threats.contains(enemy.getUUID()))return false;
        long ordinal=hits+1;boolean accepted;
        if(ordinal>=50&&!(enemy instanceof net.minecraft.world.entity.player.Player)){enemy.discard();accepted=true;}
        else{float amount=ordinal>=50?Float.MAX_VALUE:FACTORIALS[(int)ordinal].floatValue();if(!Float.isFinite(amount))amount=Float.MAX_VALUE;accepted=enemy.hurt(damageSources().mobAttack(this),amount);}
        if(!accepted)return false;
        hits=Math.min(50,ordinal);entityData.set(CONTACT,world.getGameTime());
        Vec3 hit=enemy.getBoundingBox().getCenter();O5Effect.emit(this,1,Math.max(0,move()),1,position(),hit);
        if(enemy.isAlive())enemy.knockback(.45,getX()-enemy.getX(),getZ()-enemy.getZ());
        world.playSound(null,BlockPos.containing(hit),SoundEvents.PLAYER_ATTACK_STRONG,SoundSource.HOSTILE,.8f,.55f);return true;
    }
    @Override public boolean hurt(DamageSource source,float amount){
        if(armored()){
            if(source.getEntity() instanceof LivingEntity attacker&&!(attacker instanceof GuardianEntity)&&response()!=null&&level() instanceof ServerLevel w){CityDefense.addThreat(w,response(),attacker);
                if(tickCount%6==0)O5Effect.emit(this,2,Math.max(0,move()),Math.floorMod(tickCount/6,6),position(),position());}
            return false;
        }return super.hurt(source,amount);
    }
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return false;}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putBoolean("O5Descending",descending());tag.putLong("O5Hits",hits);tag.putInt("O5Sequence",sequence);
        if(landing!=null){tag.putDouble("O5LandX",landing.x);tag.putDouble("O5LandY",landing.y);tag.putDouble("O5LandZ",landing.z);}
        tag.putInt("O5Move",move());tag.putLong("O5Start",actionStart());tag.putLong("O5Contact",contactTick());tag.putLong("O5LandTick",landTick());tag.putBoolean("O5Resolved",resolved);if(locked!=null)tag.putUUID("O5Locked",locked);
        tag.putLong("O5NextBlink",nextBlink);tag.putLong("O5NextAttack",nextAttack);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);entityData.set(DESCENDING,tag.getBoolean("O5Descending"));setNoGravity(descending());hits=Math.max(0,Math.min(50,tag.getLong("O5Hits")));sequence=tag.getInt("O5Sequence");
        if(tag.contains("O5LandX"))landing=new Vec3(tag.getDouble("O5LandX"),tag.getDouble("O5LandY"),tag.getDouble("O5LandZ"));
        entityData.set(MOVE,tag.contains("O5Move")?net.minecraft.util.Mth.clamp(tag.getInt("O5Move"),-1,9):-1);entityData.set(START,tag.getLong("O5Start"));entityData.set(CONTACT,tag.getLong("O5Contact"));entityData.set(LAND,tag.getLong("O5LandTick"));resolved=tag.getBoolean("O5Resolved");locked=tag.hasUUID("O5Locked")?tag.getUUID("O5Locked"):null;nextBlink=tag.getLong("O5NextBlink");nextAttack=tag.getLong("O5NextAttack");
    }
}
