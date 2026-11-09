package cn.academy.port.canon;

import cn.academy.port.AbilityDamage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.core.SkillTuning;
import cn.academy.port.highpower.CanonEmission;
import cn.academy.port.skill.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Entity-caster ports of concrete catalog skills. Classic charge sessions, costs, ray geometry,
 * knockback and CPData are shared with player controllers. No FakePlayer or forged input identity.
 * The host owns consent, alliances and materials; this controller owns skill gates and world effects.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
public final class EntityAbilityRuntime {
    public interface Host {
        LivingEntity entity(); AbilityProgress ability(); boolean mayAttack(LivingEntity target);
        boolean material(String kind,int count,boolean consume);
        default java.math.BigDecimal growthPosition(){return null;}
        default java.math.BigDecimal damageUnit(){return java.math.BigDecimal.ZERO;}
        default double additionalBaseCost(){return 0;}
        default double additionalCp(){return 0;}
        default boolean spendAdditional(String id,double cost,int cooldown){return false;}
    }
    public enum Kind { ATTACK, CONTROL, DEFENSE, MOBILITY }
    public record Definition(String category,String id,Kind kind,int windup,int cooldown,double multiplier) {
        public int level(){return SkillCatalog.find(category,id).orElseThrow().level();}
    }
    private static Definition d(String c,String id,Kind kind,int windup,int cooldown,double multiplier){return new Definition(c,id,kind,windup,cooldown,multiplier);}
    public static final List<Definition> DEFINITIONS=List.of(
        d("electromaster","arc_gen",Kind.ATTACK,10,15,1),d("electromaster","thunder_bolt",Kind.ATTACK,18,120,3),
        d("meltdowner","electron_bomb",Kind.ATTACK,18,20,1),d("meltdowner","meltdowner",Kind.ATTACK,24,240,1),
        d("teleporter","threatening_teleport",Kind.ATTACK,10,30,1),d("teleporter","mark_teleport",Kind.MOBILITY,8,40,1),d("teleporter","flesh_ripping",Kind.ATTACK,15,90,2),
        d("vecmanip","dir_shock",Kind.ATTACK,12,60,1),d("vecmanip","dir_blast",Kind.ATTACK,16,80,3),
        d("mental_out","mental_probe",Kind.CONTROL,10,20,1),d("mental_out","mental_disrupt",Kind.CONTROL,16,80,2),
        d("aero_hand","aero_push",Kind.CONTROL,10,30,1),d("aero_hand","aero_jet",Kind.MOBILITY,8,30,1),
        d("float_dial","float_lift",Kind.CONTROL,12,30,1),d("float_dial","float_suspend",Kind.CONTROL,15,60,2),d("float_dial","float_rescue",Kind.DEFENSE,8,60,2),
        d("nitrogen_armor","nitrogen_armor",Kind.DEFENSE,8,80,2),d("nitrogen_armor","nitrogen_punch",Kind.ATTACK,12,45,2),
        d("dark_matter","dark_blade",Kind.ATTACK,10,30,1),d("dark_matter","dark_shard",Kind.ATTACK,12,40,1),d("dark_matter","dark_wings",Kind.MOBILITY,8,100,3),
        d("hydro_hand","hydro_whip",Kind.ATTACK,12,30,1),d("hydro_hand","hydro_shield",Kind.DEFENSE,8,80,2),
        d("paper_psychokinesis","paper_blade",Kind.ATTACK,12,30,1),d("paper_psychokinesis","paper_armor",Kind.DEFENSE,8,80,2),
        d("liquid_shadow","liquid_scout",Kind.DEFENSE,12,60,2),d("liquid_shadow","liquid_contact_cut",Kind.ATTACK,12,35,1),d("liquid_shadow","liquid_strike",Kind.ATTACK,16,60,2)
    );
    public record Receipt(UUID caster,String skill,String phase,int level,long tick,double cp,double overload,double actualDamage) {}
    public static Consumer<Receipt> observer=r -> {};
    private final Host host;
    private Definition casting;
    private LivingEntity target;
    private int ticks;
    private boolean practice;
    private Vec3 destination;
    private MeltdownerSession melt;
    private ElectronBomb.LaunchPlan electron;
    private long guardUntil,wingsUntil,rescueUntil,shadowUntil;
    private double shield;
    private Definition guardSkill;
    private CanonLiquidEntities.Shadow shadow;
    private final List<Control> controls=new ArrayList<>();
    private static final java.util.Map<Mob,EntityAbilityRuntime> MENTAL=new java.util.WeakHashMap<>();
    private static final java.util.Map<LivingEntity,EntityAbilityRuntime> BUOYANCY=new java.util.WeakHashMap<>();
    private record Control(LivingEntity target,String skill,long until,double initialY) {}
    public EntityAbilityRuntime(Host host){this.host=host;}
    private LivingEntity caster(){return host.entity();}
    private ServerLevel world(){return (ServerLevel)caster().level();}
    private AbilityProgress state(){return host.ability();}
    public List<Definition> available(){return DEFINITIONS.stream().filter(d -> d.category.equals(state().category)&&state().learned(d.id)&&d.level()<=state().level&&SkillCatalog.enabled(d.category,d.id)).toList();}
    public static Definition definition(String id){return DEFINITIONS.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);}
    public boolean busy(){return casting!=null;}
    public String activeSkill(){return casting==null?"":casting.id;}
    public int chargeTicks(){return ticks;}
    public double shield(){return world().getGameTime()<guardUntil?(playerNumbers()?cn.academy.port.highpower.HighPowerCombat.shield(caster()).doubleValue():shield):0;}
    public boolean hasShadow(){return shadow!=null&&shadow.isAlive()&&!shadow.isRemoved();}
    private boolean playerNumbers(){return host.growthPosition()!=null;}
    private boolean additional(Definition d){return playerNumbers()&&CanonSkills.knownSkill(d.id);}
    public ClassicRules.SkillCost cost(Definition d) {
        var base=nativeCost(d);if(!playerNumbers())return base;
        if(!additional(d))return new ClassicRules.SkillCost(base.cp(),base.overload(),scaledDamage(d,base.damage()),base.range(),base.cooldown());
        double multiplier=switch(d.id){case "mental_disrupt","aero_push","nitrogen_armor","nitrogen_punch","dark_shard","hydro_shield","paper_armor","liquid_strike","liquid_contact_cut","float_rescue"->2;case "liquid_scout"->3;case "dark_wings"->5;case "float_suspend"->1.25;default->1;};
        int cooldown=switch(d.id){case "mental_probe"->20;case "mental_disrupt","dark_shard","hydro_shield","paper_armor","liquid_scout"->80;case "aero_push","float_rescue"->60;case "nitrogen_armor"->140;case "nitrogen_punch","float_suspend"->45;case "dark_wings"->220;case "liquid_strike"->40;case "liquid_contact_cut"->50;default->30;};
        double cp=host.additionalBaseCost()*multiplier;
        return new ClassicRules.SkillCost(cp,0,d.kind==Kind.ATTACK?scaledDamage(d,base.damage()):0,base.range(),cooldown);
    }
    private double scaledDamage(Definition d,double raw) {
        if(!playerNumbers()||raw<=0)return raw;
        var unit=host.damageUnit();
        if(CanonSkills.knownSkill(d.id))return unit.multiply(CanonEmission.levelFactor(d.level())).doubleValue();
        return cn.academy.port.highpower.EsperBalanceConfig.scaleClassic()
                ?cn.academy.port.highpower.core.ClassicDamageScale.scaled(raw,unit,cn.academy.port.highpower.EsperBalanceConfig.divisor()):raw;
    }
    private ClassicRules.SkillCost nativeCost(Definition d) {
        double e=state().exp(d.id);var base=ClassicRules.arc(e);
        return switch(d.id) {
            case "arc_gen" -> base;
            case "thunder_bolt" -> ThunderBoltRules.cost(e);
            case "electron_bomb" -> ElectronBomb.cost(e);
            case "meltdowner" -> new ClassicRules.SkillCost(MeltdownerSession.consumption(e)*d.windup,MeltdownerSession.overload(e),MeltdownerSession.shot(e,d.windup).damage(),30,MeltdownerSession.shot(e,d.windup).cooldown());
            case "threatening_teleport" -> ThreateningTeleport.rules(e);
            case "mark_teleport" -> new ClassicRules.SkillCost(TeleporterProgressionRules.markConsumption(e)*6,TeleporterProgressionRules.markOverload(e),0,TeleporterProgressionRules.markRange(e),TeleporterProgressionRules.markCooldown(e));
            case "flesh_ripping" -> new ClassicRules.SkillCost(TeleporterProgressionRules.fleshConsumption(e),TeleporterProgressionRules.fleshOverload(e),TeleporterProgressionRules.fleshDamage(e),TeleporterProgressionRules.fleshRange(e),TeleporterProgressionRules.fleshCooldown(e));
            case "dir_shock" -> DirectedShock.cost(e);
            case "dir_blast" -> new ClassicRules.SkillCost(VectorCombatRules.blastCp(e),VectorCombatRules.blastOverload(e),VectorCombatRules.blastDamage(e),SkillTuning.number(d.id,"reach"),VectorCombatRules.blastCooldown(e));
            default -> new ClassicRules.SkillCost(base.cp()*d.multiplier,base.overload()*d.multiplier,
                    d.kind==Kind.ATTACK?base.damage()*CanonEmission.levelFactor(d.level()).doubleValue():0,range(d),d.cooldown);
        };
    }
    public double range(Definition d) {
        if(List.of("arc_gen","thunder_bolt","electron_bomb","meltdowner","threatening_teleport","mark_teleport","flesh_ripping","dir_shock","dir_blast").contains(d.id))return cost(d).range();
        return SkillTuning.fields(d.id).containsKey("reach")?SkillTuning.number(d.id,"reach"):6;
    }
    public BlockPos waterSource() {
        int r=(int)SkillTuning.number("hydro_whip","source_reach");BlockPos at=caster().blockPosition(),best=null;double distance=Double.MAX_VALUE;
        for(BlockPos p:BlockPos.betweenClosed(at.offset(-r,-r,-r),at.offset(r,r,r))) if(world().hasChunkAt(p)&&world().getFluidState(p).is(FluidTags.WATER)&&world().getFluidState(p).isSource()) {
            double n=p.distSqr(at);if(n<distance&&n<=r*r){distance=n;best=p.immutable();}
        }
        return best;
    }
    public boolean canStart(Definition d,LivingEntity enemy,boolean exercise) {
        if(d==null||busy()||!state().canUse(d.id)||d.level()>state().level||!SkillCatalog.enabled(d.category,d.id))return false;
        var cost=cost(d);double cp=additional(d)?cost.cp():state().consumptionConfig().cp(d.category,d.id,cost.cp());
        if((additional(d)?host.additionalCp():state().cp)<cp)return false;
        if(!exercise&&(d.kind==Kind.ATTACK||d.kind==Kind.CONTROL)&&(!validTarget(enemy)||caster().distanceToSqr(enemy)>range(d)*range(d)||!caster().hasLineOfSight(enemy)))return false;
        if(!exercise&&d.id.equals("mental_disrupt")&&!(enemy instanceof Mob))return false;
        if(!exercise&&d.kind==Kind.CONTROL&&d.id.startsWith("float_")&&(BUOYANCY.containsKey(enemy)||!enemy.onGround()))return false;
        if(d.id.startsWith("hydro_")&&waterSource()==null)return false;
        if(d.id.startsWith("paper_")&&!host.material("paper",d.id.equals("paper_armor")?3:1,false))return false;
        if(d.id.equals("threatening_teleport")&&!host.material("needle",1,false))return false;
        if(d.id.equals("liquid_scout")&&(hasShadow()||!host.material("shadow_medium",1,false)))return false;
        if((d.id.equals("liquid_strike")||d.id.equals("liquid_contact_cut"))&&(!hasShadow()||!exercise&&shadow.distanceToSqr(enemy)>Math.pow(SkillTuning.number(d.id,"body_reach"),2)))return false;
        if(d.id.equals("nitrogen_punch")&&shield()<=0)return false;
        if((d.id.equals("nitrogen_armor")||d.id.equals("paper_armor")||d.id.equals("hydro_shield"))&&shield()>0)return false;
        if(d.id.equals("dark_wings")&&world().getGameTime()<wingsUntil)return false;
        if(d.id.equals("float_rescue")&&caster().getDeltaMovement().y>=-.1)return false;
        if(d.id.equals("mark_teleport")&&teleportDestination(enemy)==null)return false;
        return true;
    }
    public boolean begin(Definition d,LivingEntity enemy,boolean exercise) {
        if(!canStart(d,enemy,exercise))return false;
        casting=d;target=enemy;practice=exercise;ticks=0;electron=null;melt=null;destination=null;
        if(d.id.equals("meltdowner")) {melt=MeltdownerSession.begin(state(),false);if(melt==null){casting=null;return false;}}
        else if(d.id.equals("electron_bomb")) {electron=ElectronBomb.prepare(state(),false);if(electron==null){casting=null;return false;}}
        else {
            var cost=cost(d);if(d.id.equals("mark_teleport")) {
                destination=teleportDestination(enemy);if(destination==null){casting=null;return false;}
                cost=new ClassicRules.SkillCost(destination.distanceTo(caster().position())*TeleporterProgressionRules.markConsumption(state().exp(d.id)),cost.overload(),0,cost.range(),cost.cooldown());
            }
            boolean paid=additional(d)?host.spendAdditional(d.id,cost.cp(),cost.cooldown()):state().consumeSkill(d.id,cost.cp(),cost.overload(),false);
            if(!paid){casting=null;return false;}
            state().setCooldown(d.id,cost.cooldown());
        }
        if(d.id.startsWith("paper_"))host.material("paper",d.id.equals("paper_armor")?3:1,true);
        if(d.id.equals("threatening_teleport"))host.material("needle",1,true);
        if(d.id.equals("liquid_scout"))host.material("shadow_medium",1,true);
        visual(d,"START",emissionOrigin(d),endpoint(),d.id.equals("electron_bomb")?electron.life():d.windup);
        receipt(d,"START",0);return true;
    }
    private boolean validTarget(LivingEntity enemy) {return enemy!=null&&enemy.isAlive()&&!enemy.isRemoved()&&enemy.level()==caster().level()&&host.mayAttack(enemy);}
    private Vec3 emissionOrigin(Definition d) {
        if(!d.id.equals("meltdowner")&&!d.id.equals("electron_bomb"))return caster().getEyePosition();
        Vec3 facing=(validTarget(target)?target.getBoundingBox().getCenter().subtract(caster().getEyePosition()):caster().getLookAngle()).multiply(1,0,1).normalize();
        Vec3 right=new Vec3(-facing.z,0,facing.x);
        // Match the city NPC's forward right-hand CAST pose, shared by both native MD shots.
        return caster().getEyePosition().add(0,-.42,0).add(right.scale(.42)).add(facing.scale(.55));
    }
    private Vec3 endpoint(){return validTarget(target)?target.getBoundingBox().getCenter():caster().getEyePosition().add(caster().getLookAngle().scale(casting==null?6:range(casting)));}
    public void tick() {
        if(!caster().isAlive()){clear();return;}
        long now=world().getGameTime();
        if(now>=guardUntil)shield=0;
        if(now<wingsUntil&&caster().getDeltaMovement().y<0){caster().setDeltaMovement(caster().getDeltaMovement().multiply(1,.35,1));caster().fallDistance=0;caster().hurtMarked=true;}
        if(now<rescueUntil&&caster().getDeltaMovement().y<-.1){caster().setDeltaMovement(caster().getDeltaMovement().x,-.1,caster().getDeltaMovement().z);caster().fallDistance=0;caster().hurtMarked=true;}
        controls.removeIf(c -> {
            if(validTarget(c.target)&&now<c.until)return false;
            if(c.target instanceof Mob mob&&MENTAL.get(mob)==this)MENTAL.remove(mob);
            if(BUOYANCY.get(c.target)==this)BUOYANCY.remove(c.target);return true;
        });
        for(var control:controls) {
            var t=control.target;
            if(control.skill.equals("mental_disrupt")) {if(t instanceof Mob mob){mob.setTarget(null);mob.getNavigation().stop();}}
            else {
                var v=t.getDeltaMovement();double rise=SkillTuning.number(control.skill,"rise_limit"),dy=control.initialY+rise-t.getY();
                t.setDeltaMovement(v.x*.55,Math.max(-.08,Math.min(.34,dy*.28+.08)),v.z*.55);t.hasImpulse=true;t.hurtMarked=true;
                if(t instanceof Mob mob)mob.getNavigation().stop();
            }
        }
        if(shadow!=null) {
            if(!hasShadow()||now>=shadowUntil||shadow.distanceToSqr(caster())>31*31){shadow.discard();shadow=null;}
            else if(validTarget(target)) {
                var delta=target.position().subtract(shadow.position());double speed=SkillTuning.number("liquid_scout","speed");
                Vec3 step=delta.lengthSqr()>.25?delta.normalize().scale(speed):Vec3.ZERO;
                if(world().hasChunkAt(BlockPos.containing(shadow.position().add(step))))shadow.move(MoverType.SELF,step);
                shadow.setYRot(caster().getYRot());
            }
        }
        if(casting==null)return;
        var d=casting;ticks++;
        if(!state().activated||state().interfering){abort();return;}
        if(melt!=null&&melt.tick(false)){abort();return;}
        int windup=electron==null?d.windup:ElectronBomb.firingDelayTicks(state().exp(d.id));
        if(ticks<windup)return;
        double dealt=0;
        if(d.kind==Kind.ATTACK)dealt=attack(d);
        else if(d.kind==Kind.CONTROL)control(d);
        else if(d.kind==Kind.DEFENSE)defense(d);
        else mobility(d);
        if(melt!=null){var shot=melt.release();if(shot!=null)melt.complete(shot);}
        // These native sessions already award their own original mastery.
        if(!d.id.equals("electron_bomb")&&!d.id.equals("meltdowner"))state().addExperience(d.id,.003);
        receipt(d,"RESOLVE",dealt);casting=null;melt=null;electron=null;
    }
    private double attack(Definition d) {
        Vec3 end=endpoint();var cost=nativeCost(d);double damage=cost.damage();
        if(d.id.equals("threatening_teleport"))damage=ThreateningTeleport.damage(state().exp(d.id),true);
        if(d.id.equals("meltdowner"))damage=MeltdownerSession.shot(state().exp(d.id),ticks).damage();
        if(d.id.equals("electron_bomb"))damage=electron.cost().damage();
        boolean hit=validTarget(target)&&caster().hasLineOfSight(target)&&caster().distanceToSqr(target)<=range(d)*range(d);
        double dealt=0;
        if(hit&&d.id.equals("meltdowner"))dealt=beam(d,(float)damage);
        else if(hit) {
            if(d.id.startsWith("liquid_")&&(!hasShadow()||shadow.distanceToSqr(target)>Math.pow(SkillTuning.number(d.id,"body_reach"),2)))hit=false;
            if(hit)dealt=damage(d,target,damage,d.id.equals("threatening_teleport")||d.id.equals("flesh_ripping"));
            if(hit&&d.id.equals("dir_shock"))DirectedShock.knockback(caster(),target,state().exp(d.id));
            if(hit&&d.id.equals("dir_blast"))impulse(target,1.0,.25);
            if(hit&&d.id.equals("aero_push"))impulse(target,SkillTuning.number(d.id,"horizontal_speed"),.15);
            if(hit&&d.id.equals("nitrogen_punch"))impulse(target,SkillTuning.number(d.id,"horizontal_speed"),SkillTuning.number(d.id,"vertical_speed"));
            if(hit&&d.id.equals("hydro_whip"))impulse(target,SkillTuning.number(d.id,"horizontal_speed"),.18);
            if(hit&&d.id.equals("thunder_bolt")) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,ThunderBoltRules.DIRECT_SLOW_TICKS,ThunderBoltRules.SLOW_AMPLIFIER));
                for(var other:world().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(ThunderBoltRules.AOE_RANGE),t -> t!=target&&host.mayAttack(t)&&caster().hasLineOfSight(t)))
                    dealt+=damage(d,other,ThunderBoltRules.aoeDamage(state().exp(d.id)),false);
            }
        }
        visual(d,"HIT",emissionOrigin(d),end,12);return dealt;
    }
    private double damage(Definition d,LivingEntity victim,double amount,boolean ignoreArmor) {
        if(!validTarget(victim))return 0;var before=cn.academy.port.highpower.CityNpcPower.health(victim);
        AbilityDamage.attackEntity(caster(),d.category+"."+d.id,victim,scaledDamage(d,amount),ignoreArmor);
        return before.subtract(cn.academy.port.highpower.CityNpcPower.health(victim)).max(java.math.BigDecimal.ZERO).doubleValue();
    }
    private void impulse(LivingEntity t,double horizontal,double vertical) {
        var v=t.position().subtract(caster().position()).multiply(1,0,1).normalize().scale(horizontal);
        t.setDeltaMovement(v.x,Math.max(t.getDeltaMovement().y,vertical),v.z);t.hasImpulse=true;t.hurtMarked=true;
    }
    private double beam(Definition d,float damage) {
        Vec3 eye=emissionOrigin(d),axis=endpoint().subtract(eye).normalize();double[] dealt={0};
        var shot=MeltdownerSession.shot(state().exp(d.id),ticks);
        var adapter=new ClassicBeamRay.World() {
            public List<ClassicBeamRay.Target> targets(ClassicBeamRay.Box b) {
                return world().getEntitiesOfClass(LivingEntity.class,new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()),t -> host.mayAttack(t)&&caster().distanceToSqr(t)<=30*30&&caster().hasLineOfSight(t))
                        .stream().map(t -> new ClassicBeamRay.Target(t,vec(t.getBoundingBox().getCenter()))).toList();
            }
            public boolean attack(ClassicBeamRay.Target t,float amount){dealt[0]+=damage(d,(LivingEntity)t.identity(),amount,false);return true;}
            public boolean canBreak(){return false;}
            public ClassicBeamRay.Block block(ClassicBeamRay.Cell p){return new ClassicBeamRay.Block(-1,false);}
            public boolean denied(ClassicBeamRay.Cell p){return true;}
            public void destroy(ClassicBeamRay.Cell p,float chance,boolean sound,ClassicBeamRay.Random random){}
        };
        var random=new ClassicBeamRay.Random(){public double nextDouble(){return world().random.nextDouble();}public float nextFloat(){return world().random.nextFloat();}public int nextInt(int bound){return world().random.nextInt(bound);}};
        ClassicBeamRay.perform(adapter,random,vec(caster().position()),vec(eye),vec(axis),shot.radius(),shot.energy(),damage);
        return dealt[0];
    }
    private static ClassicBeamRay.Vec vec(Vec3 p){return new ClassicBeamRay.Vec(p.x,p.y,p.z);}
    private void control(Definition d) {
        if(validTarget(target)&&caster().hasLineOfSight(target)&&caster().distanceToSqr(target)<=range(d)*range(d)) {
            if(d.id.equals("aero_push"))impulse(target,SkillTuning.number(d.id,"horizontal_speed"),.15);
            else if(d.id.equals("mental_probe"))target.addEffect(new MobEffectInstance(MobEffects.GLOWING,SkillTuning.integer(d.id,"reveal_ticks"),0));
            else if(d.id.equals("mental_disrupt")&&target instanceof Mob||d.id.startsWith("float_")&&!BUOYANCY.containsKey(target)) {
                controls.add(new Control(target,d.id,world().getGameTime()+SkillTuning.integer(d.id,"duration_ticks"),target.getY()));
                if(d.id.equals("mental_disrupt")&&target instanceof Mob mob){MENTAL.put(mob,this);mob.setTarget(null);mob.getNavigation().stop();}
                else BUOYANCY.put(target,this);
            }
        }
        visual(d,"HIT",caster().getEyePosition(),endpoint(),Math.min(80,SkillTuning.fields(d.id).containsKey("duration_ticks")?SkillTuning.integer(d.id,"duration_ticks"):20));
    }
    private void defense(Definition d) {
        long now=world().getGameTime();
        if(d.id.equals("float_rescue"))rescueUntil=now+SkillTuning.integer(d.id,"duration_ticks");
        else if(d.id.equals("liquid_scout")) {
            var body=CanonLiquidEntities.SHADOW.get().create(world());
            if(body!=null) {
                var at=caster().position().add(caster().getLookAngle().multiply(1,0,1).normalize().scale(1.2));
                body.moveTo(at.x,at.y,at.z,caster().getYRot(),0);body.controlOwner(caster().getUUID());
                if(world().noCollision(body)&&world().addFreshEntity(body)){shadow=body;shadowUntil=now+SkillTuning.integer(d.id,"duration_ticks");}
            }
        } else {
            guardUntil=now+SkillTuning.integer(d.id,"duration_ticks");shield=playerNumbers()?cn.academy.port.highpower.HighPowerCombat.shield(caster()).doubleValue():ClassicRules.arc(state().exp(d.id)).damage()*CanonEmission.levelFactor(d.level()).doubleValue()*4;
            guardSkill=d;
        }
        visual(d,"HIT",caster().position(),caster().position(),Math.min(200,SkillTuning.integer(d.id,"duration_ticks")));
    }
    private void mobility(Definition d) {
        if(d.id.equals("mark_teleport")) {
            if(destination!=null&&safe(destination)) {Vec3 from=caster().position();caster().teleportTo(destination.x,destination.y,destination.z);visual(d,"HIT",from,destination,12);}
        } else if(d.id.equals("aero_jet")) {
            Vec3 direction=validTarget(target)?caster().position().subtract(target.position()):caster().getLookAngle();
            Vec3 horizontal=direction.multiply(1,0,1).normalize().scale(SkillTuning.number(d.id,"horizontal_speed"));
            Vec3 v=horizontal.add(0,SkillTuning.number(d.id,"vertical_speed"),0);
            if(world().noCollision(caster(),caster().getBoundingBox().move(v.scale(.5)))) {caster().setDeltaMovement(v);caster().hasImpulse=true;caster().hurtMarked=true;}
        } else {
            wingsUntil=world().getGameTime()+SkillTuning.integer(d.id,"duration_ticks");
            if(caster().onGround()&&world().noCollision(caster(),caster().getBoundingBox().move(0,.6,0))) {
                caster().push(0,.42,0);caster().hurtMarked=true;
            }
        }
        visual(d,"HIT",caster().position(),caster().position(),Math.min(200,d.id.equals("dark_wings")?200:12));
    }
    public Vec3 teleportDestination(LivingEntity enemy) {
        Vec3 away=enemy==null?caster().getLookAngle():caster().position().subtract(enemy.position());away=away.multiply(1,0,1).normalize();
        for(double distance:new double[]{6,4,3})for(int angle:new int[]{0,45,-45,90,-90}) {
            Vec3 p=caster().position().add(away.yRot((float)Math.toRadians(angle)).scale(distance));
            for(int dy:new int[]{0,1,-1}) {var at=BlockPos.containing(p).offset(0,dy,0);var point=Vec3.atBottomCenterOf(at);if(safe(point))return point;}
        }
        return null;
    }
    private boolean safe(Vec3 p) {
        var at=BlockPos.containing(p);return world().hasChunkAt(at)&&world().getWorldBorder().isWithinBounds(at)
                &&world().getBlockState(at.below()).isFaceSturdy(world(),at.below(),Direction.UP)
                &&world().getFluidState(at).isEmpty()&&world().noCollision(caster(),caster().getBoundingBox().move(p.subtract(caster().position())));
    }
    public float absorb(float amount) {
        if(playerNumbers())return amount; // the shared base ledger settles the hit exactly once
        double absorbed=Math.min(Math.max(0,amount),shield());shield-=absorbed;
        if(absorbed>0&&guardSkill!=null)receipt(guardSkill,"ABSORB",absorbed);
        return (float)(amount-absorbed);
    }
    private void visual(Definition d,String phase,Vec3 from,Vec3 to,int duration) {
        var hit=world().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,caster()));
        if(d.id.equals("arc_gen")||d.id.equals("electron_bomb")||d.id.equals("meltdowner"))if(hit.getType()!=HitResult.Type.MISS)to=hit.getLocation();
        if(CanonVfxDesign.find(d.id)!=null)CanonVfx.emitEntity(caster(),state(),d.id,from,to,phase.equals("START")?CanonVfx.Phase.START:CanonVfx.Phase.HIT,duration);
        else EntitySkillVisual.emit(caster(),d.id,phase,from,to,duration);
        if(phase.equals("HIT"))world().playSound(null,caster().blockPosition(),d.category.equals("teleporter")?SoundEvents.ENDERMAN_TELEPORT:SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.NEUTRAL,.45f,.9f);
    }
    private void receipt(Definition d,String phase,double damage){observer.accept(new Receipt(caster().getUUID(),d.id,phase,state().level,world().getGameTime(),state().cp,state().overload,damage));}
    public void abort(){if(casting!=null)receipt(casting,"ABORT",0);if(melt!=null)melt.discard();casting=null;melt=null;electron=null;}
    public void clear(){abort();MENTAL.entrySet().removeIf(e -> e.getValue()==this);BUOYANCY.entrySet().removeIf(e -> e.getValue()==this);controls.clear();if(shadow!=null)shadow.discard();shadow=null;shield=0;guardUntil=wingsUntil=rescueUntil=0;}
    @net.neoforged.bus.api.SubscribeEvent
    public static void mentalTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if(event.getEntity() instanceof Mob mob) {
            var runtime=MENTAL.get(mob);
            if(runtime!=null&&runtime.caster().isAlive()&&runtime.controls.stream().anyMatch(c -> c.target==mob&&c.skill.equals("mental_disrupt")&&runtime.world().getGameTime()<c.until))event.setNewAboutToBeSetTarget(null);
        }
    }
}
