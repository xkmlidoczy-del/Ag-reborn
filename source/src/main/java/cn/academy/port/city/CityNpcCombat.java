package cn.academy.port.city;

import cn.academy.port.AbilityDamage;
import cn.academy.port.canon.EntityAbilityRuntime;
import cn.academy.port.core.AbilityProgress;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Bounded, non-griefing city defense; native skill controllers and resource ledgers own every actual action. */
@EventBusSubscriber(modid="academy")
public final class CityNpcCombat implements EntityAbilityRuntime.Host {
    public enum Mode { DAILY, ALERT, CASTING, RETREATING, RECOVERING, RETURNING, DOWNED }
    private final CityNpcEntity npc;
    private final CityNpcAbilityData data;
    private final EntityAbilityRuntime runtime;
    private BlockPos home;
    private Mode mode=Mode.DAILY;
    private LivingEntity enemy;
    private UUID duel;
    private long duelUntil,lastThreat,nextDecision,downUntil;
    private String identity="";
    private boolean fleeOnly;
    private boolean yieldRequested;
    private CityNpcEntity.Activity previousActivity=CityNpcEntity.Activity.STAND;
    private CityNpcEntity.Expression previousExpression=CityNpcEntity.Expression.NEUTRAL;
    public CityNpcCombat(CityNpcEntity npc){this.npc=npc;home=npc.blockPosition();data=new CityNpcAbilityData(npc);runtime=new EntityAbilityRuntime(this);}
    @Override public LivingEntity entity(){return npc;}
    @Override public AbilityProgress ability(){return data.ability;}
    @Override public java.math.BigDecimal growthPosition(){return npc.esper()==CityEspers.Profile.NONE?null:data.position();}
    @Override public java.math.BigDecimal damageUnit(){return data.damageUnit();}
    @Override public double additionalBaseCost(){return data.baseSkillCost();}
    @Override public double additionalCp(){return data.skillCp;}
    public double additionalMaximum(){return data.skillMaximum();}
    @Override public boolean spendAdditional(String id,double cost,int cooldown){return data.spendSkill(id,cost,cooldown);}
    @Override public boolean material(String kind,int count,boolean consume){return data.material(kind,count,consume);}
    public EntityAbilityRuntime runtime(){return runtime;}
    public Mode mode(){return mode;}
    public boolean busy(){return mode!=Mode.DAILY||runtime.busy()||cn.academy.port.city.defense.CityDefense.escorted(npc);}
    public boolean dueling(){return duel!=null;}
    public boolean dueling(Player p){return duel!=null&&duel.equals(p.getUUID());}
    public void yieldAfterHit(){yieldRequested=true;}
    private static boolean lowHealth(LivingEntity entity) {
        return cn.academy.port.highpower.CityNpcPower.health(entity).compareTo(
                cn.academy.port.highpower.CityNpcPower.maxHealth(entity).multiply(new java.math.BigDecimal("0.15")).max(java.math.BigDecimal.valueOf(2)))<=0;
    }
    void dailyHome(BlockPos point){if(!busy())home=point.immutable();}
    @Override public boolean mayAttack(LivingEntity target) {
        if(target==null||target==npc||target instanceof CityNpcEntity||!target.isAlive()||target.isRemoved()
                ||target.level()!=npc.level()||target.isAlliedTo(npc)||npc.isAlliedTo(target))return false;
        if(target instanceof Player p)return duel!=null&&duel.equals(p.getUUID())&&!p.isCreative()&&!p.isSpectator();
        return target instanceof Monster;
    }
    public void identity(String value) {
        identity=value;
        if(npc.level() instanceof ServerLevel level)data.load(CityNpcMemory.get(level).read(value));
        publish();
    }
    public void checkpoint() {
        if(!identity.isEmpty()&&npc.level() instanceof ServerLevel level)CityNpcMemory.get(level).write(identity,data.save());
    }
    public CompoundTag save(){return data.save();}
    public void load(CompoundTag tag){data.load(tag);publish();}
    public void close(){runtime.clear();checkpoint();}
    private void rememberDaily() {
        CityDialogue.interrupt(npc);
        if(mode==Mode.DAILY){previousActivity=npc.activity();previousExpression=npc.expression();}
        npc.getNavigation().stop();
    }
    public boolean startDuel(ServerPlayer player) {
        if(busy()||npc.esper()==CityEspers.Profile.NONE||npc.getHealth()<npc.getMaxHealth()*.5f||player.isCreative()||player.isSpectator()||npc.distanceToSqr(player)>64)return false;
        rememberDaily();duel=player.getUUID();duelUntil=npc.level().getGameTime()+1200;enemy=player;lastThreat=npc.level().getGameTime();mode=Mode.ALERT;fleeOnly=false;nextDecision=lastThreat+20;
        player.sendSystemMessage(Component.translatable("academy.city.combat.duel_started"));publish();return true;
    }
    public void finishDuel(String reason) {
        if(duel!=null&&npc.level() instanceof ServerLevel level) {
            var player=level.getPlayerByUUID(duel);if(player!=null)player.sendSystemMessage(Component.translatable("academy.city.combat."+reason));
        }
        duel=null;enemy=null;runtime.abort();mode=Mode.RETURNING;nextDecision=0;publish();checkpoint();
    }
    public void attacked(DamageSource source) {
        if(source.getEntity() instanceof LivingEntity attacker&&attacker!=npc) {
            if(attacker instanceof Player) {
                if(dueling((Player)attacker)){enemy=attacker;lastThreat=npc.level().getGameTime();}
                else if(!busy()){rememberDaily();mode=Mode.RETREATING;enemy=attacker;lastThreat=npc.level().getGameTime();}
            } else if(attacker instanceof Monster) {
                if(mode==Mode.DAILY)rememberDaily();enemy=attacker;fleeOnly=false;lastThreat=npc.level().getGameTime();mode=Mode.ALERT;
            }
        }
    }
    public float protect(DamageSource source,float amount) {
        if(mode==Mode.DOWNED)return 0;
        if(duel!=null&&source.getEntity() instanceof Player p&&!dueling(p))return 0;
        if(cn.academy.port.highpower.CityNpcPower.active(npc)){attacked(source);return amount;}
        // Exact player casts already pass the consent, guard and nonlethal adapter before
        // their native witness is constructed; do not alter that witness a second time.
        attacked(source);float remaining=source.getEntity()!=null&&!cn.academy.port.highpower.HighPowerCombat.isExactSkillSource(source)?runtime.absorb(amount):amount;
        if(npc.ambient()||duel!=null)remaining=Math.min(remaining,Math.max(0,npc.getHealth()-1));
        if(npc.getHealth()-remaining<=1.01f&&remaining>0) {
            runtime.clear();downUntil=npc.level().getGameTime()+200;
            if(duel!=null)finishDuel("duel_npc_yield");
            enemy=null;mode=Mode.DOWNED;npc.getNavigation().stop();npc.perform(CityNpcEntity.Activity.SIT,CityNpcEntity.Expression.FOCUS);
        }
        return remaining;
    }
    /** Consented sparring is the sole player high-power entry for this entity class. */
    public java.math.BigDecimal sparringIncoming(ServerPlayer player,java.math.BigDecimal amount) {
        if(!dueling(player)||mode==Mode.DOWNED)return java.math.BigDecimal.ZERO;
        if(cn.academy.port.highpower.CityNpcPower.active(npc))return amount;
        float limit=Math.max(0,npc.getHealth()-Math.max(2,npc.getMaxHealth()*.15f));
        float bounded=amount.min(java.math.BigDecimal.valueOf(limit)).floatValue();
        return java.math.BigDecimal.valueOf(runtime.absorb(bounded));
    }
    public boolean practice(ServerPlayer player) {
        if(busy()||npc.esper()==CityEspers.Profile.NONE)return false;
        var definitions=runtime.available();
        for(var d:definitions)if(runtime.canStart(d,null,true)) {
            rememberDaily();if(runtime.begin(d,null,true)){mode=Mode.CASTING;enemy=null;checkpoint();publish();return true;}
        }
        return false;
    }
    private boolean guardian(){return npc.look()==CityNpcEntity.Look.GUARD;}
    private boolean enemyValid(){return enemy!=null&&enemy.isAlive()&&!enemy.isRemoved()&&enemy.level()==npc.level()&&npc.distanceToSqr(enemy)<28*28;}
    public void tick() {
        long now=npc.level().getGameTime();data.tick();runtime.tick();
        if(cn.academy.port.city.defense.CityDefense.escorted(npc)&&duel==null){runtime.abort();return;}
        if(yieldRequested){yieldRequested=false;runtime.clear();downUntil=now+200;mode=Mode.DOWNED;
            npc.getNavigation().stop();npc.perform(CityNpcEntity.Activity.SIT,CityNpcEntity.Expression.FOCUS);}
        if(now%100==0)checkpoint();if(now%5==0)publish();
        if(mode==Mode.DOWNED) {
            if(now>=downUntil){mode=Mode.RETURNING;npc.perform(CityNpcEntity.Activity.STAND,CityNpcEntity.Expression.NEUTRAL);}
            return;
        }
        if(duel!=null) {
            if(!enemyValid()||now>=duelUntil||enemy instanceof Player p&&(p.isCreative()||p.isSpectator())) {finishDuel("duel_finished");return;}
            if(lowHealth(enemy)){finishDuel("duel_player_yield");return;}
            if(lowHealth(npc)){finishDuel("duel_npc_yield");return;}
        }
        if(runtime.busy()) {
            mode=Mode.CASTING;npc.getNavigation().stop();npc.perform(CityNpcEntity.Activity.CAST,CityNpcEntity.Expression.FOCUS);
            if(enemyValid())npc.getLookControl().setLookAt(enemy,30,30);return;
        }
        if(mode==Mode.CASTING&&enemy==null){mode=Mode.RETURNING;}
        if(mode==Mode.DAILY) {
            if(now%20==Math.floorMod(npc.getId(),20)) {
                var danger=((ServerLevel)npc.level()).getEntitiesOfClass(Monster.class,npc.getBoundingBox().inflate(guardian()?18:10,6,guardian()?18:10),m -> m.isAlive()&&npc.hasLineOfSight(m)
                        &&(m.getTarget()==npc||(guardian()||npc.distanceToSqr(m)<36)&&(m.getTarget() instanceof Player||m.getTarget() instanceof CityNpcEntity)))
                        .stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
                if(danger!=null){rememberDaily();enemy=danger;lastThreat=now;fleeOnly=!guardian()&&danger.getTarget()!=npc;mode=fleeOnly?Mode.RETREATING:Mode.ALERT;}
            }
            if(now%20==0){if(npc.getHealth()<npc.getMaxHealth()) {
                if(cn.academy.port.highpower.CityNpcPower.active(npc))cn.academy.port.highpower.HighPowerCombat.healCityNpc(npc,
                        cn.academy.port.highpower.CityNpcPower.maxHealth(npc).multiply(java.math.BigDecimal.valueOf(.4/(20+10.0*npc.esperLevel()))));
                else npc.heal(.4f);
            }if(npc.distanceToSqr(Vec3.atBottomCenterOf(home))<16)data.supplyAtHome();}
            return;
        }
        if(now<nextDecision)return;nextDecision=now+8;
        if(mode==Mode.RETURNING) {
            if(npc.distanceToSqr(Vec3.atBottomCenterOf(home))>6&&now-lastThreat<600) {
                npc.getNavigation().moveTo(home.getX()+.5,home.getY(),home.getZ()+.5,.9);return;
            }
            enemy=null;mode=Mode.DAILY;npc.perform(previousActivity,previousExpression);npc.endConversation();publish();return;
        }
        if(!enemyValid()||duel==null&&now-lastThreat>400||npc.distanceToSqr(Vec3.atBottomCenterOf(home))>28*28) {
            runtime.abort();enemy=null;mode=Mode.RETURNING;return;
        }
        npc.getLookControl().setLookAt(enemy,30,30);
        var state=data.ability;
        if(fleeOnly||npc.esper()==CityEspers.Profile.NONE||!mayAttack(enemy)||!state.overloadFine||state.cp<state.maxCp()*.12||state.overload>state.maxOverload()*.82
                ||data.skillCp<data.skillMaximum()*.12
                ||(mode==Mode.RECOVERING||mode==Mode.RETREATING)&&(state.cp<state.maxCp()*.35||state.overload>state.maxOverload()*.4||data.skillCp<data.skillMaximum()*.35)) {
            mode=!state.overloadFine?Mode.RECOVERING:Mode.RETREATING;retreat(enemy);return;
        }
        mode=Mode.ALERT;
        var options=runtime.available();double distance=npc.distanceTo(enemy);
        var defense=options.stream().filter(d -> d.kind()==EntityAbilityRuntime.Kind.DEFENSE&&runtime.canStart(d,enemy,false)).findFirst().orElse(null);
        if(defense!=null&&(runtime.shield()==0||npc.getHealth()<npc.getMaxHealth()*.6f)&&cast(defense))return;
        var attack=options.stream().filter(d -> (d.kind()==EntityAbilityRuntime.Kind.ATTACK||d.kind()==EntityAbilityRuntime.Kind.CONTROL)&&runtime.canStart(d,enemy,false))
                .max(Comparator.<EntityAbilityRuntime.Definition>comparingInt(EntityAbilityRuntime.Definition::level).thenComparingDouble(d -> runtime.cost(d).damage())).orElse(null);
        if(attack!=null&&cast(attack))return;
        if(distance<3) {
            var movement=options.stream().filter(d -> d.kind()==EntityAbilityRuntime.Kind.MOBILITY&&runtime.canStart(d,enemy,false)).findFirst().orElse(null);
            if(movement!=null&&cast(movement))return;
        }
        if(distance>3&&npc.hasLineOfSight(enemy))npc.getNavigation().moveTo(enemy,.95);
        else retreat(enemy);
    }
    private boolean cast(EntityAbilityRuntime.Definition d) {
        if(!runtime.begin(d,enemy,false))return false;
        mode=Mode.CASTING;npc.getNavigation().stop();npc.perform(CityNpcEntity.Activity.CAST,CityNpcEntity.Expression.FOCUS);checkpoint();publish();return true;
    }
    private void retreat(LivingEntity threat) {
        Vec3 away=npc.position().subtract(threat.position()).multiply(1,0,1).normalize();
        for(int angle:new int[]{0,45,-45,90,-90}) {
            Vec3 at=npc.position().add(away.yRot((float)Math.toRadians(angle)).scale(5));
            var path=npc.getNavigation().createPath(BlockPos.containing(at),1);
            if(path!=null&&path.canReach()){npc.getNavigation().moveTo(path,1.12);npc.perform(CityNpcEntity.Activity.STAND,CityNpcEntity.Expression.FOCUS);return;}
        }
        npc.getNavigation().stop();npc.perform(CityNpcEntity.Activity.CHECK,CityNpcEntity.Expression.FOCUS);
    }
    private void publish(){npc.combatSnapshot(data.ability,mode,runtime.activeSkill(),cn.academy.port.highpower.HighPowerCombat.enabled(npc)?cn.academy.port.highpower.HighPowerCombat.shield(npc).doubleValue():runtime.shield());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void nonlethal(LivingDamageEvent.Pre event) {
        if(event.getSource() instanceof AbilityDamage.EntitySkillDamageSource&&event.getSource().getEntity() instanceof CityNpcEntity npc
                &&event.getEntity() instanceof Player player&&npc.combat().dueling(player)&&!cn.academy.port.highpower.HighPowerCombat.enabled(player))
            event.setNewDamage(Math.min(event.getNewDamage(),Math.max(0,player.getHealth()-Math.max(2,player.getMaxHealth()*.15f))));
        if(event.getEntity() instanceof CityNpcEntity npc&&npc.ambient()&&!cn.academy.port.highpower.CityNpcPower.active(npc))event.setNewDamage(Math.min(event.getNewDamage(),Math.max(0,npc.getHealth()-1)));
    }
}
