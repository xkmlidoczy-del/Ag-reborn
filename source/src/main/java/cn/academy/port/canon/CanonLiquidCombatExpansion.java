package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.highpower.CanonEmission;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Contact techniques of one already materialized, server-owned liquid shadow. */
@EventBusSubscriber(modid="academy")
public final class CanonLiquidCombatExpansion {
    private static final String CATEGORY="liquid_shadow";
    private static final List<SkillCatalog.Skill> SKILLS=List.of(
            skill("liquid_grapple",2,"liquid_scout"),
            skill("liquid_contact_cut",3,"liquid_strike"),
            skill("liquid_contact_sweep",4,"liquid_strike"),
            skill("liquid_contact_crush",5,"liquid_contact_cut"));
    private static final Map<UUID,Binding> BINDINGS=new HashMap<>();
    private static final Set<ServerPlayer> BUSY=Collections.newSetFromMap(new IdentityHashMap<>());
    private record Context(ServerPlayer owner,AbilityProgress state,ServerLevel world,CanonLiquidEntities.Shadow body,
                           String skill,int level,int preset,long revision,long tick,Vec3 position,Vec3 eye,Vec3 look,
                           Vec3 velocity,Vec3 bodyPosition) {}
    private record Binding(ServerPlayer owner,ServerLevel world,CanonLiquidEntities.Shadow body,LivingEntity target,
                           String skill,long expires,int level,int preset,long revision) {}
    private CanonLiquidCombatExpansion() {}
    public static List<SkillCatalog.Skill> skills(){return SKILLS;}
    public static boolean contains(String id){return id!=null&&SKILLS.stream().anyMatch(s->s.id().equals(id));}
    private static SkillCatalog.Skill skill(String id,int level,String prerequisite){
        return new SkillCatalog.Skill(CATEGORY,id,level,true,List.of(new SkillCatalog.Requirement(prerequisite,0)),0);
    }
    public static boolean cast(ServerPlayer p,String id){
        if(p==null||id==null||!p.server.isSameThread()||!contains(id)||!ready(p,id)||!BUSY.add(p))return false;
        try{
            var body=CanonLiquidSkills.controlledBody(p);if(body==null)return false;
            var s=AbilityStorage.get(p);var world=p.serverLevel();var look=ClassicRaytrace.direction(p);
            var c=new Context(p,s,world,body,id,s.level,s.presets.current(),s.presets.revision(),world.getGameTime(),
                    p.position(),p.getEyePosition(),look,p.getDeltaMovement(),body.position());
            if(!context(c)||c.tick<0||c.tick>Long.MAX_VALUE-100)return false;
            List<LivingEntity> selected=targets(c);if(selected.isEmpty())return false;
            if(id.equals("liquid_grapple")){
                LivingEntity target=selected.getFirst();if(BINDINGS.size()>=64||BINDINGS.containsKey(target.getUUID()))return false;
                Vec3 original=target.getDeltaMovement(),position=target.position();
                double cost=HighPowerGrowth.values(p).arcCost();
                if(!Double.isFinite(cost)||cost<=0||!HighPowerResources.trySpend(p,id,cost,40)||!context(c)
                        ||!targets(c).equals(selected)||BINDINGS.size()>=64||BINDINGS.containsKey(target.getUUID())
                        ||!target.position().equals(position)||!target.getDeltaMovement().equals(original))return false;
                BINDINGS.put(target.getUUID(),new Binding(p,world,body,target,id,c.tick+integer(id, "duration_ticks"),c.level,c.preset,c.revision));
                // The shadow wraps the legs: rooted for the tuned binding duration and slowed a little longer once it lets go.
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,integer(id, "slow_ticks"),1,false,true),p);
                success(c,target.position(),CanonVfx.Phase.START);return true;
            }
            if(id.equals("liquid_contact_crush")&&!bindingAvailable(selected.getFirst()))return false;
            double cost=HighPowerGrowth.values(p).arcCost()*(id.equals("liquid_contact_sweep")?3:2);
            if(!Double.isFinite(cost)||cost<=0)return false;
            var emission=CanonEmission.begin(p,id,cost,id.equals("liquid_contact_sweep")?80:50,selected,
                    target->context(c)&&geometry(c,target)&&(!id.equals("liquid_contact_crush")||bindingAvailable(target)));
            if(emission==null)return false;
            boolean hit=false;
            for(var target:selected){
                if(!emission.attack(target))continue;hit=true;
                CanonVfx.emit(p,id,body.position(),target.position(),CanonVfx.Phase.HIT,12);
                if(id.equals("liquid_contact_crush")&&context(c)&&geometry(c,target)&&BINDINGS.size()<64
                        &&!BINDINGS.containsKey(target.getUUID()))
                    BINDINGS.put(target.getUUID(),new Binding(p,world,body,target,id,c.tick+integer(id, "duration_ticks"),c.level,c.preset,c.revision));
            }
            if(hit&&AbilityStorage.get(p)==s&&s.learned(id)){s.addExperience(id,.01);AbilityStorage.save(p);}
            return hit;
        }finally{BUSY.remove(p);}
    }
    private static boolean ready(ServerPlayer p,String id){return !AbilityConsumption.busy(p)&&HighPowerResources.ready(p)&&after(p,id)&&AbilityStorage.get(p).canUse(id);}
    private static boolean after(ServerPlayer p,String id){
        if(p.connection==null||p.hasDisconnected()||p.isRemoved()||!p.isAlive()||p.isSpectator()||!p.server.isSameThread()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||HighPowerCombat.unsupportedReason(p).isPresent())return false;
        var state=AbilityStorage.get(p);var def=SKILLS.stream().filter(s->s.id().equals(id)).findFirst().orElse(null);
        return def!=null&&CATEGORY.equals(state.category)&&state.level>=def.level()&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned(id)&&state.presets.currentContains(id)&&SkillCatalog.enabled(CATEGORY,id);
    }
    private static boolean context(Context c){
        var p=c.owner;var s=AbilityStorage.get(p);return after(p,c.skill)&&s==c.state&&s.level==c.level
                &&s.presets.current()==c.preset&&s.presets.revision()==c.revision&&p.serverLevel()==c.world
                &&c.world.getGameTime()==c.tick&&CanonLiquidSkills.sameBody(p,c.body)&&c.body.position().equals(c.bodyPosition)
                &&p.position().equals(c.position)&&p.getEyePosition().equals(c.eye)&&ClassicRaytrace.direction(p).equals(c.look)
                &&p.getDeltaMovement().equals(c.velocity)&&finite(c.bodyPosition)&&finite(c.eye)&&finite(c.look);
    }
    private static List<LivingEntity> targets(Context c){
        if(c.skill.equals("liquid_contact_sweep")){
            var area=c.body.getBoundingBox().inflate(number(c.skill, "contact_margin"));if(!loaded(c.world,new Vec3(area.minX,area.minY,area.minZ),new Vec3(area.maxX,area.maxY,area.maxZ)))return List.of();
            var found=c.world.getEntitiesOfClass(Mob.class,area,t->geometry(c,t));if(found.size()>64)return List.of();
            found.sort(Comparator.comparing(Entity::getUUID));return List.copyOf(found.subList(0,Math.min(integer(c.skill, "target_cap"),found.size())));
        }
        Vec3 end=c.eye.add(c.look.scale(number(c.skill, "reach")));if(!loaded(c.world,c.eye,end))return List.of();
        var hit=ClassicRaytrace.perform(c.owner,c.eye,end,ClipContext.Fluid.NONE,e->e!=c.body);
        return hit instanceof EntityHitResult h&&h.getEntity() instanceof LivingEntity target&&geometry(c,target)?List.of(target):List.of();
    }
    private static boolean geometry(Context c,LivingEntity target){
        var owner=c.owner;var body=c.body;var world=c.world;double inflate=number(c.skill, "contact_margin");
        double range=number(c.skill, "body_reach");
        if(!CanonLiquidSkills.sameBody(owner,body)||!(target instanceof Mob)||!CanonTargetPolicy.hostile(target)
                ||target.level()!=world||world.getEntity(target.getUUID())!=target||!target.isAlive()||target.isRemoved()
                ||target.isPassenger()||target.isVehicle()||target.isAlliedTo(owner)||owner.isAlliedTo(target)
                ||HighPowerCombat.unsupportedReason(target).isPresent()||!finite(target.position())||!finite(target.getDeltaMovement())
                ||!world.hasChunkAt(target.blockPosition())||!world.mayInteract(owner,target.blockPosition())
                ||!world.getWorldBorder().isWithinBounds(target.getBoundingBox())||target.distanceToSqr(body)>range*range
                ||!body.getBoundingBox().inflate(inflate).intersects(target.getBoundingBox())||!owner.hasLineOfSight(target))return false;
        var type=target.getType();if(!CanonTargetPolicy.reviewed(target)&&(!BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")
                ||type==EntityType.WITHER||type==EntityType.ENDER_DRAGON||type==EntityType.WARDEN||type==EntityType.ELDER_GUARDIAN))return false;
        Vec3 from=body.getEyePosition(),to=target.getBoundingBox().getCenter();
        if(!loaded(world,from,to)||world.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,body)).getType()!=HitResult.Type.MISS)return false;
        if(c.skill.equals("liquid_contact_sweep"))return true;
        Vec3 end=owner.getEyePosition().add(ClassicRaytrace.direction(owner).scale(number(c.skill, "reach")));
        if(!loaded(world,owner.getEyePosition(),end))return false;
        var hit=ClassicRaytrace.perform(owner,owner.getEyePosition(),end,ClipContext.Fluid.NONE,e->e!=body);
        return hit instanceof EntityHitResult entityHit&&entityHit.getEntity()==target;
    }
    private static void success(Context c,Vec3 end,CanonVfx.Phase phase){
        if(AbilityStorage.get(c.owner)==c.state&&c.state.learned(c.skill)){c.state.addExperience(c.skill,.01);AbilityStorage.save(c.owner);}
        CanonVfx.emit(c.owner,c.skill,c.body.position(),end,phase,12);
    }
    private static boolean bindingAvailable(LivingEntity target){return BINDINGS.size()<64&&!BINDINGS.containsKey(target.getUUID());}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        for(var binding:List.copyOf(BINDINGS.values())){
            if(binding.owner.server!=event.getServer())continue;
            if(!valid(binding)){end(binding);continue;}
            var t=binding.target;Vec3 old=t.getDeltaMovement(),next=new Vec3(old.x*.2,t.onGround()?Math.max(0,old.y):old.y,old.z*.2),to=t.position().add(next);   // grounded: gravity is not a collision
            if(!finite(next)||!finite(to)||!loaded(binding.world,t.position(),to)
                    ||!binding.world.mayInteract(binding.owner,BlockPos.containing(to))
                    ||!binding.world.getWorldBorder().isWithinBounds(t.getBoundingBox().move(next))
                    ||!binding.world.noCollision(t,t.getBoundingBox().move(next))){end(binding);continue;}
            t.setDeltaMovement(next);t.hasImpulse=true;t.hurtMarked=true;
            if(t instanceof net.minecraft.world.entity.Mob mob)mob.getNavigation().stop();
        }
    }
    private static boolean valid(Binding b){
        var p=b.owner;var s=AbilityStorage.get(p);if(BINDINGS.get(b.target.getUUID())!=b||p.serverLevel()!=b.world
                ||b.world.getGameTime()>=b.expires||!after(p,b.skill)||s.level!=b.level||s.presets.current()!=b.preset
                ||s.presets.revision()!=b.revision||!CanonLiquidSkills.sameBody(p,b.body))return false;
        var c=new Context(p,s,b.world,b.body,b.skill,s.level,b.preset,b.revision,b.world.getGameTime(),p.position(),
                p.getEyePosition(),ClassicRaytrace.direction(p),p.getDeltaMovement(),b.body.position());
        return geometry(c,b.target);
    }
    private static void end(Binding b){if(BINDINGS.remove(b.target.getUUID(),b))CanonVfx.emit(b.owner,b.skill,b.body.position(),b.target.position(),CanonVfx.Phase.END,8);}
    private static boolean loaded(ServerLevel w,Vec3 a,Vec3 b){
        if(!finite(a)||!finite(b))return false;
        int x0=Math.floorDiv((int)Math.floor(Math.min(a.x,b.x)),16),x1=Math.floorDiv((int)Math.floor(Math.max(a.x,b.x)),16);
        int z0=Math.floorDiv((int)Math.floor(Math.min(a.z,b.z)),16),z1=Math.floorDiv((int)Math.floor(Math.max(a.z,b.z)),16);
        if((long)x1-x0>8||(long)z1-z0>8)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!w.hasChunk(x,z))return false;return true;
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void remove(ServerPlayer p){for(var b:List.copyOf(BINDINGS.values()))if(b.owner==p)end(b);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){for(var b:List.copyOf(BINDINGS.values()))if(b.owner.server==e.getServer())end(b);}
}
