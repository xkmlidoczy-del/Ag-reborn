package cn.academy.port.city.defense;

import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.fleet.*;
import cn.academy.port.highpower.HighPowerCombat;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Autonomous officers carry finite, real magazines. No fake player, virtual ammunition or teleport movement. */
public class GuardianEntity extends PathfinderMob {
    public enum Action { ALERT, ADVANCE, COVER, FLANK, FIRE, RELOAD, MEDIC, SIGNAL, DRIVE, ESCORT, RETURN }
    private static final EntityDataAccessor<Integer> ROLE=SynchedEntityData.defineId(GuardianEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(GuardianEntity.class,EntityDataSerializers.INT);
    private UUID team;
    private long expires,nextFire,nextPlan,nextHeal;
    private int medical=6,shots,heldFire;
    private final SimpleContainer magazines=new SimpleContainer(8);
    private Vec3 destination;
    public GuardianEntity(EntityType<? extends GuardianEntity> type,Level level){super(type,level);setPersistenceRequired();setCanPickUpLoot(false);xpReward=0;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(ROLE,0);b.define(ACTION,0);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));}
    public int role(){return entityData.get(ROLE);}
    public Action action(){return Action.values()[Math.floorMod(entityData.get(ACTION),Action.values().length)];}
    public UUID response(){return team;}
    public long expires(){return expires;}
    public int shots(){return shots;}
    public int heldFire(){return heldFire;}
    public int medicalCharges(){return medical;}
    public int energy(){int n=GunItem.ammo(getMainHandItem());for(int i=0;i<magazines.getContainerSize();i++)n+=CoilMagazineItem.energy(magazines.getItem(i));return n;}
    public boolean reloading(){return GunItem.reloadEnd(getMainHandItem())>0;}
    public void prepare(UUID response,int role,int level,long expires,String unit){
        this.team=response;this.expires=expires;entityData.set(ROLE,role);
        int variant=switch(role){case 0,2,4,5,7->2;case 3->3;case 6->5;case 1->4;default->1;};
        var gun=new ItemStack(InteractionLab.GUNS[variant].get());GunItem.ammo(gun,InteractionLab.CAPS[variant]);
        setItemSlot(EquipmentSlot.MAINHAND,gun);setDropChance(EquipmentSlot.MAINHAND,0);
        cn.academy.port.city.defense.armor.DefenseArmor.equip(this,this instanceof O5OperativeEntity);
        for(int i=0;i<4;i++)magazines.setItem(i,CoilMagazineItem.create(variant,InteractionLab.CAPS[variant]));
        HighPowerCombat.enroll(this,level);setCustomName(Component.literal(unit+" · "+roleName(role)));setCustomNameVisible(false);
    }
    public static String roleName(int r){return new String[]{"指挥","突入","步枪","精确射手","左翼","右翼","支援","驾驶","救护","护送"}[Math.floorMod(r,10)];}
    private void action(Action a){entityData.set(ACTION,a.ordinal());boolean crouch=a==Action.COVER||a==Action.FIRE||a==Action.MEDIC||a==Action.RELOAD;
        setShiftKeyDown(crouch);setPose(crouch?Pose.CROUCHING:Pose.STANDING);}
    @Override public EntityDimensions getDefaultDimensions(Pose pose){return pose==Pose.CROUCHING?EntityDimensions.scalable(.6f,1.5f).withEyeHeight(1.27f):super.getDefaultDimensions(pose);}
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel world))return;
        if(team==null||world.getGameTime()>expires){if(isPassenger())stopRiding();discard();return;}
        HighPowerCombat.tickShield(this);tickReload();
        if(!isNoAi()&&tickCount%5==Math.floorMod(getId(),5))tactics(CityDefenseData.get(world).team(team));
    }
    protected void tactics(CityDefenseData.Team response){
        if(response==null){expires=Math.min(expires,level().getGameTime()+20);return;}
        long now=level().getGameTime();
        if(getVehicle() instanceof LabVehicle car){action(Action.DRIVE);CityDefense.drive(this,car,response);return;}
        LivingEntity enemy=CityDefense.target((ServerLevel)level(),response);
        LivingEntity civilian=CityDefense.civilian((ServerLevel)level(),response);
        if(role()==8&&treat(civilian,now))return;
        if(role()==8){var injured=level().getEntitiesOfClass(GuardianEntity.class,getBoundingBox().inflate(12),g->g!=this&&Objects.equals(g.team,team)&&g.getHealth()<g.getMaxHealth()*.7f)
            .stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);if(treat(injured,now))return;}
        if(role()==9&&civilian!=null&&civilian.isAlive()&&enemy!=null&&distanceToSqr(civilian)>9){action(Action.ESCORT);move(civilian.position(),1.05);getLookControl().setLookAt(enemy,35,35);return;}
        if(enemy==null){action(Action.RETURN);setTarget(null);getNavigation().stop();return;}
        setTarget(enemy);getLookControl().setLookAt(enemy,45,45);
        if(O5Reinforcements.ownsContact((ServerLevel)level(),response,enemy)){action(role()==0?Action.SIGNAL:Action.COVER);getNavigation().stop();return;}
        if(reloading()){action(Action.RELOAD);getNavigation().stop();return;}
        var stack=getMainHandItem();if(!(stack.getItem() instanceof GunItem gun))return;
        // Team A covers while B moves. A second group starts only once the other has a loaded officer.
        boolean covering=(now/70+role())%2==0||role()==3||role()==6;
        if(GunItem.ammo(stack)<=0||GunItem.ammo(stack)<gun.capacity()/4&&covering&&CityDefense.coverCount((ServerLevel)level(),response,this)>1){
            if(CityDefense.coverCount((ServerLevel)level(),response,this)>0||GunItem.ammo(stack)<=0){beginReload();action(Action.RELOAD);return;}
        }
        boolean clear=FleetBallistics.clearShot(this,enemy);double distance=distanceToSqr(enemy);
        if(now>=nextPlan){
            nextPlan=now+35+role();
            destination=CityDefense.tacticalPoint(this,enemy,response,covering,clear);
        }
        if((!covering||!clear||distance>42*42)&&destination!=null&&destination.distanceToSqr(position())>2){
            action(role()==4||role()==5?Action.FLANK:Action.ADVANCE);move(destination,1.12);
        }else{getNavigation().stop();action(covering?Action.COVER:Action.ALERT);}
        // Deliberate short bursts with a recovery interval, preserving the existing native hurt cooldown.
        if(clear&&distance<60*60&&now>=nextFire&&GunItem.ammo(stack)>0&&GunItem.hasMagazine(stack)&&(!isSprinting())){
            action(Action.FIRE);GunItem.ammo(stack,GunItem.ammo(stack)-1);GunItem.lastFire(stack,now);shots++;
            nextFire=now+Math.max(InteractionLab.COOLDOWN[gun.variant],role()==3?24:10)+(shots%3==0?14:0);
            syncEquipment();AudioDefs.emit(this,gun.variant,"fire",now);
            FleetBallistics.fire(this,gun.variant,enemy.getBoundingBox().getCenter().subtract(getEyePosition().add(0,-.12,0)));
        }else if(!clear){heldFire++;}
        if(role()==0&&now%140<5&&!clear)action(Action.SIGNAL);
    }
    private void move(Vec3 point,double speed){if(!getNavigation().isInProgress()||getNavigation().getTargetPos()==null||getNavigation().getTargetPos().distSqr(net.minecraft.core.BlockPos.containing(point))>4)getNavigation().moveTo(point.x,point.y,point.z,speed);}
    private boolean treat(LivingEntity patient,long now){
        if(patient==null||!patient.isAlive()||patient.getHealth()>=patient.getMaxHealth()*.92f||medical<=0)return false;
        action(Action.MEDIC);if(patient instanceof CityNpcEntity n&&distanceToSqr(patient)<25)n.getNavigation().stop();
        if(distanceToSqr(patient)>6){move(patient.position(),1.1);return true;}
        getNavigation().stop();getLookControl().setLookAt(patient,30,30);
        if(now>=nextHeal){
            if(patient instanceof CityNpcEntity n&&HighPowerCombat.enabled(n))HighPowerCombat.healCityNpc(n,cn.academy.port.highpower.CityNpcPower.maxHealth(n).multiply(new java.math.BigDecimal("0.12")));
            else if(patient instanceof GuardianEntity g&&HighPowerCombat.enabled(g))HighPowerCombat.healGuardian(g,new java.math.BigDecimal("0.12"));
            else patient.heal(patient.getMaxHealth()*.12f);
            medical--;nextHeal=now+60;
            if(level() instanceof ServerLevel world)world.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,patient.getX(),patient.getY()+1.4,patient.getZ(),3,.3,.2,.3,0);}
        return true;
    }
    public boolean beginReload(){
        var gun=getMainHandItem();if(!(gun.getItem() instanceof GunItem item)||reloading()||GunItem.ammo(gun)>=item.capacity()&&GunItem.hasMagazine(gun)||bestMagazine(item.variant)<0)return false;
        boolean empty=GunItem.ammo(gun)==0||!GunItem.hasMagazine(gun);var timing=GunSpec.timing(item.variant);long now=level().getGameTime();
        CustomData.update(DataComponents.CUSTOM_DATA,gun,t->{t.putBoolean("c03_reload_empty",empty);t.putInt("c03_reload_stage",1);});
        GunItem.reload(gun,now,now+(empty?timing.emptyEnd():timing.tacticalEnd()));syncEquipment();return true;
    }
    private int bestMagazine(int variant){int best=-1,energy=0;for(int i=0;i<magazines.getContainerSize();i++){var s=magazines.getItem(i);if(s.is(InteractionLab.magazine(variant))&&CoilMagazineItem.energy(s)>energy){best=i;energy=CoilMagazineItem.energy(s);}}return best;}
    private void stage(int value){CustomData.update(DataComponents.CUSTOM_DATA,getMainHandItem(),t->t.putInt("c03_reload_stage",value));syncEquipment();}
    private void tickReload(){
        var stack=getMainHandItem();if(!(stack.getItem() instanceof GunItem gun)||!reloading())return;
        var timing=GunSpec.timing(gun.variant);long start=GunItem.reloadStart(stack),elapsed=level().getGameTime()-start;
        boolean empty=GunItem.reloadWasEmpty(stack);int stage=GunItem.data(stack).getInt("c03_reload_stage"),insert=empty?timing.emptyInsert():timing.tacticalInsert();
        if(elapsed==timing.release()-1)AudioDefs.emit(this,gun.variant,"latch",start);
        if(stage==1&&elapsed>=timing.release()){
            if(GunItem.hasMagazine(stack)){var old=CoilMagazineItem.create(gun.variant,GunItem.ammo(stack));magazines.addItem(old);GunItem.ammo(stack,0);GunItem.hasMagazine(stack,false);}
            stage(2);stage=2;AudioDefs.emit(this,gun.variant,"mag_out",start);
        }
        if(elapsed==insert-3)AudioDefs.emit(this,gun.variant,"guide",start);
        if(stage==2&&elapsed>=insert){int slot=bestMagazine(gun.variant);if(slot<0){GunItem.reload(stack,0,0);stage(0);return;}
            var fresh=magazines.removeItem(slot,1);GunItem.ammo(stack,CoilMagazineItem.energy(fresh));GunItem.hasMagazine(stack,true);stage(3);stage=3;AudioDefs.emit(this,gun.variant,"mag_in",start);}
        if(empty&&elapsed==insert+Math.round((timing.lock()-insert)*.52f))AudioDefs.emit(this,gun.variant,"charge",start);
        if(empty&&stage==3&&elapsed>=timing.lock()){stage(4);AudioDefs.emit(this,gun.variant,"activate",start);}
        if(elapsed>=(empty?timing.emptyEnd():timing.tacticalEnd())){GunItem.reload(stack,0,0);stage(0);}
    }
    private void syncEquipment(){if(level() instanceof ServerLevel world)world.getChunkSource().broadcast(this,new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(getId(),List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND,getMainHandItem().copy()))));}
    @Override protected void dropCustomDeathLoot(ServerLevel level,net.minecraft.world.damagesource.DamageSource source,boolean player){/* Response equipment belongs to the city. */}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
        if(source.getEntity() instanceof GuardianEntity)return false;
        if(level() instanceof ServerLevel world&&source.getEntity() instanceof LivingEntity attacker&&team!=null)CityDefense.addThreat(world,team,attacker);
        return super.hurt(source,amount);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);if(team!=null)tag.putUUID("Response",team);
        tag.putInt("Role",role());tag.putLong("DutyEnd",expires);tag.putInt("Medical",medical);tag.putInt("Shots",shots);tag.putLong("NextFire",nextFire);tag.putLong("NextHeal",nextHeal);
        var list=new ListTag();for(int i=0;i<magazines.getContainerSize();i++){var entry=new CompoundTag();entry.putInt("Slot",i);entry.put("Stack",magazines.getItem(i).saveOptional(registryAccess()));list.add(entry);}tag.put("Magazines",list);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);team=tag.hasUUID("Response")?tag.getUUID("Response"):null;
        if(!(this instanceof O5OperativeEntity)&&getItemBySlot(EquipmentSlot.HEAD).isEmpty()&&getItemBySlot(EquipmentSlot.CHEST).isEmpty()&&getItemBySlot(EquipmentSlot.LEGS).isEmpty()&&getItemBySlot(EquipmentSlot.FEET).isEmpty())cn.academy.port.city.defense.armor.DefenseArmor.equip(this,false);
        entityData.set(ROLE,Mth.clamp(tag.getInt("Role"),0,9));expires=tag.getLong("DutyEnd");medical=Mth.clamp(tag.getInt("Medical"),0,6);shots=tag.getInt("Shots");nextFire=tag.getLong("NextFire");nextHeal=tag.getLong("NextHeal");
        magazines.clearContent();for(var raw:tag.getList("Magazines",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;int slot=t.getInt("Slot");if(slot>=0&&slot<magazines.getContainerSize())magazines.setItem(slot,ItemStack.parseOptional(registryAccess(),t.getCompound("Stack")));}
    }
}
