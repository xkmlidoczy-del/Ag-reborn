package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.MagneticItemProvenance;
import cn.academy.port.highpower.MagneticItemPull;
import cn.academy.port.highpower.NativeItemMergeAccess;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Short, bounded air-buoyancy leases. They never replace an entity's gravity flag. */
@EventBusSubscriber(modid="academy")
public final class CanonFloatExpansion {
    private static final String CATEGORY="float_dial";
    private static final List<SkillCatalog.Skill> SKILLS=List.of(
            skill("float_hover",2,"float_lift"),
            skill("float_ascent",2,"float_lift"),
            skill("float_descent",2,"float_lift"),
            skill("float_air_brake",3,"float_rescue"),
            skill("float_ally_hover",3,"float_rescue"),
            skill("float_ally_ascent",3,"float_rescue"),
            skill("float_ally_descent",4,"float_rescue"),
            skill("float_suspend",3,"float_lift"),
            skill("float_hostile_descent",4,"float_suspend"),
            skill("float_hover_field",5,"float_field"),
            skill("float_rescue_field",5,"float_rescue"),
            skill("float_item_shelf",3,"float_item_lift"),
            skill("float_item_orbit",4,"float_item_lift"),
            skill("float_item_shelf_field",5,"float_item_field"));
    private static final Map<UUID,Lease> ACTIVE=new HashMap<>();
    private static final Map<UUID,ItemLease> ITEMS=new HashMap<>();
    private static final Set<ServerPlayer> BUSY=Collections.newSetFromMap(new IdentityHashMap<>());
    private enum Mode { HOVER, ASCEND, DESCEND, BRAKE }
    private record Lease(ServerPlayer owner,ServerLevel world,LivingEntity target,String skill,Mode mode,double height,
                         long expires,int preset,long revision) {}
    private record ItemLease(ServerPlayer owner,ServerLevel world,ItemEntity target,ItemStack stack,String skill,
                             Vec3 anchor,long expires,int preset,long revision) {}
    private CanonFloatExpansion() {}
    public static List<SkillCatalog.Skill> skills(){return SKILLS;}
    public static boolean contains(String id){return SKILLS.stream().anyMatch(s->s.id().equals(id));}
    static boolean leased(Entity entity){return entity!=null&&(ACTIVE.containsKey(entity.getUUID())||ITEMS.containsKey(entity.getUUID()));}
    private static SkillCatalog.Skill skill(String id,int level,String prerequisite){
        return new SkillCatalog.Skill(CATEGORY,id,level,true,List.of(new SkillCatalog.Requirement(prerequisite,0)),0);
    }
    public static boolean cast(ServerPlayer p,String id){
        if(p==null||id==null||!p.server.isSameThread()||!contains(id)||!ready(p,id)||!BUSY.add(p))return false;
        try {
            var state=AbilityStorage.get(p);var world=p.serverLevel();int preset=state.presets.current();long revision=state.presets.revision();
            var pos=p.position();var eye=p.getEyePosition();var look=ClassicRaytrace.direction(p);var velocity=p.getDeltaMovement();
            if(!finite(pos)||!finite(eye)||!finite(look)||!finite(velocity))return false;
            if(id.startsWith("float_item_"))return itemCast(p,id,state,world,preset,revision,pos,eye,look,velocity);
            List<LivingEntity> targets=select(p,id); if(targets.isEmpty()||ACTIVE.size()+targets.size()>64)return false;
            for(var t:targets)if(occupied(t))return false;
            int ticks=integer(id, "duration_ticks");
            long now=world.getGameTime();if(now<0||now>Long.MAX_VALUE-ticks)return false;
            double cost=HighPowerGrowth.values(p).arcCost()*(targets.size()>1?3:1.25);
            if(!Double.isFinite(cost)||cost<=0||!HighPowerResources.trySpend(p,id,cost,45))return false;
            if(AbilityStorage.get(p)!=state||p.serverLevel()!=world||world.getGameTime()!=now||!readyAfter(p,id)
                    ||state.presets.current()!=preset||state.presets.revision()!=revision||!p.position().equals(pos)
                    ||!p.getEyePosition().equals(eye)||!ClassicRaytrace.direction(p).equals(look)||!p.getDeltaMovement().equals(velocity)
                    ||ACTIVE.size()+targets.size()>64||!select(p,id).equals(targets))return false;
            for(var t:targets)if(occupied(t))return false;
            Mode mode=mode(id); for(var t:targets) ACTIVE.put(t.getUUID(),new Lease(p,world,t,id,mode,hostile(id)&&mode==Mode.HOVER?t.getY()+number(id, "rise_limit"):t.getY(),now+ticks,preset,revision));
            state.addExperience(id,.01);AbilityStorage.save(p);
            CanonVfx.emit(p,id,p.position(),targets.getFirst().position(),CanonVfx.Phase.START,15,id.equals("float_hover_field")||id.equals("float_rescue_field")?number(id, "radius"):0);return true;
        } finally {BUSY.remove(p);}
    }
    private static boolean ready(ServerPlayer p,String id){return !AbilityConsumption.busy(p)&&HighPowerResources.ready(p)&&readyAfter(p,id)&&AbilityStorage.get(p).canUse(id);}
    private static boolean readyAfter(ServerPlayer p,String id){
        if(p.connection==null||p.hasDisconnected()||p.isRemoved()||!p.isAlive()||p.isSpectator()||!p.server.isSameThread()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||HighPowerCombat.unsupportedReason(p).isPresent())return false;
        var state=AbilityStorage.get(p);var def=SKILLS.stream().filter(s->s.id().equals(id)).findFirst().orElse(null);
        return def!=null&&CATEGORY.equals(state.category)&&state.level>=def.level()&&state.activated&&state.overloadFine&&!state.interfering&&state.learned(id)
                &&state.presets.currentContains(id)&&SkillCatalog.enabled(CATEGORY,id);
    }
    private static Mode mode(String id){return switch(id){
        case "float_ascent","float_ally_ascent"->Mode.ASCEND;
        case "float_descent","float_ally_descent","float_hostile_descent"->Mode.DESCEND;
        case "float_air_brake","float_rescue_field"->Mode.BRAKE;
        default->Mode.HOVER;};}
    private static boolean hostile(String id){return id.equals("float_suspend")||id.equals("float_hover_field")||id.equals("float_hostile_descent");}
    private static boolean occupied(Entity e){return leased(e)||CanonSkillsCombatB.leased(e)||CanonFloatItems.leased(e);}
    private static boolean itemCast(ServerPlayer p,String id,cn.academy.port.core.AbilityProgress state,ServerLevel world,
                                    int preset,long revision,Vec3 pos,Vec3 eye,Vec3 look,Vec3 velocity){
        if(MagneticItemPull.itemsActive(p))return false;
        var items=itemTargets(p,id);if(items.isEmpty()||ITEMS.size()+items.size()>64)return false;
        var snapshots=items.stream().map(e->e.getItem().copy()).toList();
        var positions=items.stream().map(Entity::position).toList();var speeds=items.stream().map(Entity::getDeltaMovement).toList();
        int ticks=integer(id, "duration_ticks");
        long now=world.getGameTime();if(now<0||now>Long.MAX_VALUE-ticks)return false;
        double cost=HighPowerGrowth.values(p).arcCost()*(items.size()>1?3:1.5);
        if(!Double.isFinite(cost)||cost<=0||!HighPowerResources.trySpend(p,id,cost,50))return false;
        if(AbilityStorage.get(p)!=state||p.serverLevel()!=world||world.getGameTime()!=now||!readyAfter(p,id)
                ||state.presets.current()!=preset||state.presets.revision()!=revision||!p.position().equals(pos)
                ||!p.getEyePosition().equals(eye)||!ClassicRaytrace.direction(p).equals(look)||!p.getDeltaMovement().equals(velocity)
                ||MagneticItemPull.itemsActive(p)||ITEMS.size()+items.size()>64||!itemTargets(p,id).equals(items))return false;
        for(int i=0;i<items.size();i++)if(!items.get(i).position().equals(positions.get(i))
                ||!items.get(i).getDeltaMovement().equals(speeds.get(i))||!ItemStack.matches(items.get(i).getItem(),snapshots.get(i)))return false;
        for(int i=0;i<items.size();i++)ITEMS.put(items.get(i).getUUID(),new ItemLease(p,world,items.get(i),snapshots.get(i),id,positions.get(i),now+ticks,preset,revision));
        state.addExperience(id,.01);AbilityStorage.save(p);CanonVfx.emit(p,id,pos,positions.getFirst(),CanonVfx.Phase.START,15,id.equals("float_item_shelf_field")?number(id, "radius"):0);return true;
    }
    private static List<ItemEntity> itemTargets(ServerPlayer p,String id){
        Vec3 eye=p.getEyePosition();var w=p.serverLevel();
        if(id.equals("float_item_shelf_field")){
            double radius=number(id, "radius");
            if(!loaded(w,p.position().add(-radius,0,-radius),p.position().add(radius,0,radius)))return List.of();
            var found=w.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(radius),e->itemEligible(p,e,radius)&&!occupied(e));
            if(found.size()>64)return List.of();found.sort(Comparator.comparing(Entity::getUUID));return List.copyOf(found.subList(0,Math.min(integer(id, "target_cap"),found.size())));
        }
        double reach=number(id, "reach");
        Vec3 end=eye.add(ClassicRaytrace.direction(p).scale(reach));if(!loaded(w,eye,end))return List.of();
        var found=w.getEntitiesOfClass(ItemEntity.class,new AABB(eye,end).inflate(.5),e->itemEligible(p,e,reach)&&!occupied(e));if(found.size()>64)return List.of();
        var block=w.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        double best=block.getType()==HitResult.Type.MISS?eye.distanceToSqr(end):eye.distanceToSqr(block.getLocation());ItemEntity chosen=null;
        for(var item:found){var hit=item.getBoundingBox().inflate(.25).clip(eye,end);if(hit.isEmpty())continue;double d=eye.distanceToSqr(hit.get());
            if(d<best){best=d;chosen=item;}}
        return chosen==null?List.of():List.of(chosen);
    }
    private static boolean itemEligible(ServerPlayer p,ItemEntity item,double reach){
        var w=p.serverLevel();var stack=item.getItem();
        if(item.getClass()!=ItemEntity.class||item.getType()!=EntityType.ITEM||item.level()!=w||item.isRemoved()||!item.isAlive()
                ||w.getEntity(item.getUUID())!=item||item.noPhysics||item.isNoGravity()||item.isPassenger()||item.isVehicle()||item.isInvulnerable()
                ||item.getPersistentData().getBoolean("academy:protected")||item.getPersistentData().getBoolean("academy:immovable")
                ||!(item instanceof NativeItemMergeAccess seam)||seam.academy$nativeItemMergeContractVersion()!=1
                ||!MagneticItemProvenance.ownedBy(item,p.getUUID())||item.getTarget()!=null&&!p.getUUID().equals(item.getTarget())
                ||stack.isEmpty()||stack.getItem() instanceof BundleItem||stack.getItem() instanceof BlockItem b&&b.getBlock() instanceof EntityBlock
                ||stack.has(DataComponents.CONTAINER)||stack.has(DataComponents.BUNDLE_CONTENTS)||stack.has(DataComponents.CONTAINER_LOOT)
                ||stack.has(DataComponents.BLOCK_ENTITY_DATA)||!finite(item.position())||!finite(item.getDeltaMovement())||p.distanceToSqr(item)>reach*reach
                ||!w.hasChunkAt(item.blockPosition())||!w.mayInteract(p,item.blockPosition())||!w.getWorldBorder().isWithinBounds(item.getBoundingBox()))return false;
        Vec3 eye=p.getEyePosition(),center=item.getBoundingBox().getCenter();return loaded(w,eye,center)
                &&w.clip(new ClipContext(eye,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    private static List<LivingEntity> select(ServerPlayer p,String id){
        if(id.equals("float_hover")||id.equals("float_ascent")||id.equals("float_descent")||id.equals("float_air_brake"))
            return eligible(p,p,id,true)?List.of(p):List.of();
        if(id.equals("float_hover_field")||id.equals("float_rescue_field")){
            double radius=number(id, "radius");
            if(!loaded(p.serverLevel(),p.position().add(-radius,0,-radius),p.position().add(radius,0,radius)))return List.of();
            var found=p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(radius),e->eligible(p,e,id,true)&&p.distanceToSqr(e)<=radius*radius);
            if(found.size()>64)return List.of();found.sort(Comparator.comparing(Entity::getUUID));
            return List.copyOf(found.subList(0,Math.min(found.size(),integer(id, "target_cap"))));
        }
        double reach=number(id, "reach");
        Vec3 eye=p.getEyePosition(),end=eye.add(ClassicRaytrace.direction(p).scale(reach));if(!loaded(p.serverLevel(),eye,end))return List.of();
        var hit=ClassicRaytrace.living(p,reach,ClipContext.Fluid.NONE);
        return hit instanceof EntityHitResult e&&e.getEntity() instanceof LivingEntity living&&eligible(p,living,id,true)?List.of(living):List.of();
    }
    private static double livingReach(String skill) {
        return skill.equals("float_hover_field")||skill.equals("float_rescue_field")
                ? cn.academy.port.core.RangedSkillRanges.CANON_TARGET : number(skill, "reach");
    }
    private static boolean eligible(ServerPlayer p,LivingEntity e,String id,boolean starting){
        var w=p.serverLevel();if(e.level()!=w||e.isRemoved()||!e.isAlive()||e.isPassenger()||e.isVehicle()||e.isNoGravity()
                ||e.hasEffect(MobEffects.LEVITATION)||e.hasEffect(MobEffects.SLOW_FALLING)||!finite(e.position())
                ||!finite(e.getDeltaMovement())||e!=p&&p.distanceToSqr(e)>Math.pow(livingReach(id),2)||!w.hasChunkAt(e.blockPosition())
                ||!w.mayInteract(p,e.blockPosition())||!w.getWorldBorder().isWithinBounds(e.getBoundingBox()))return false;
        boolean self=e==p,ally=id.startsWith("float_ally_")||id.equals("float_rescue_field");
        if(e instanceof ServerPlayer other){
            if(!self&&!ally)return false;
            if(other.isSpectator()||other.hasDisconnected()||other.connection==null||other.getAbilities().flying||other.isFallFlying()
                    ||other.server.getPlayerList().getPlayer(other.getUUID())!=other||other.onGround())return false;
            if(!self&&(!other.isShiftKeyDown()||!p.isAlliedTo(other)||!other.isAlliedTo(p)||!p.hasLineOfSight(other)))return false;
            if(id.equals("float_rescue_field")&&starting&&other.getDeltaMovement().y>=0)return false;
            return true;
        }
        if(self||ally||!(e instanceof Mob)||!CanonTargetPolicy.hostile(e)||e.isAlliedTo(p)||p.isAlliedTo(e)||!p.hasLineOfSight(e))return false;
        var type=e.getType();return CanonTargetPolicy.reviewed(e)||BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")
                &&type!=EntityType.WITHER&&type!=EntityType.ENDER_DRAGON&&type!=EntityType.WARDEN&&type!=EntityType.ELDER_GUARDIAN;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        for(Lease lease:List.copyOf(ACTIVE.values())){
            if(lease.owner.server!=event.getServer())continue;
            if(!valid(lease)){end(lease);continue;}
            var t=lease.target;Vec3 old=t.getDeltaMovement();double y=switch(lease.mode){
                case HOVER->Math.max(-.12,Math.min(.12,(lease.height-t.getY())*.18));
                case ASCEND->Math.min(number(lease.skill, "vertical_speed"),Math.max(.04,old.y+.06));
                case DESCEND->Math.max(-number(lease.skill, "vertical_speed"),Math.min(-.04,old.y-.04));
                case BRAKE->Math.max(-.10,Math.min(.06,old.y));};
            Vec3 next=new Vec3(lease.mode==Mode.BRAKE?old.x*.75:old.x,y,lease.mode==Mode.BRAKE?old.z*.75:old.z);
            if(hostile(lease.skill)){
                // A held enemy has nothing to push against: no walking, no steering, it hangs where the buoyancy put it.
                if(lease.mode==Mode.HOVER)next=new Vec3(old.x*.3,Math.max(-.12,Math.min(.3,(lease.height-t.getY())*.3)),old.z*.3);
                else if(t.onGround())next=new Vec3(old.x*.2,0,old.z*.2);
                if(t instanceof net.minecraft.world.entity.Mob mob)mob.getNavigation().stop();
                if(lease.mode==Mode.DESCEND&&t.onGround()&&!t.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
                    t.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,3,false,true),lease.owner);
            }
            if(!permitted(lease,next)){end(lease);continue;}
            t.setDeltaMovement(next);t.hasImpulse=true;t.hurtMarked=true;t.fallDistance=0;
        }
        for(ItemLease lease:List.copyOf(ITEMS.values())){
            if(lease.owner.server!=event.getServer())continue;
            if(!validItem(lease)){endItem(lease);continue;}
            var item=lease.target;Vec3 delta;
            if(lease.skill.equals("float_item_orbit")){
                Vec3 radial=item.position().subtract(lease.owner.position());Vec3 flat=new Vec3(radial.x,0,radial.z);
                if(flat.lengthSqr()<.25){endItem(lease);continue;}
                Vec3 tangent=new Vec3(-flat.z,0,flat.x).normalize().scale(number(lease.skill, "horizontal_speed"));
                Vec3 correction=flat.normalize().scale(Math.max(-.08,Math.min(.08,(number(lease.skill, "orbit_radius")-flat.length())*.05)));
                delta=tangent.add(correction).add(0,Math.max(-.08,Math.min(.08,(lease.anchor.y-item.getY())*.12)),0);
            }else{
                Vec3 diff=lease.anchor.subtract(item.position());delta=new Vec3(Math.max(-.12,Math.min(.12,diff.x*.15)),
                        Math.max(-.12,Math.min(.12,diff.y*.15)),Math.max(-.12,Math.min(.12,diff.z*.15)));
            }
            Vec3 end=item.position().add(delta);
            if(!finite(delta)||!finite(end)||!loaded(lease.world,item.position(),end)||!lease.world.mayInteract(lease.owner,BlockPos.containing(end))
                    ||!lease.world.getWorldBorder().isWithinBounds(item.getBoundingBox().move(delta))||!lease.world.noCollision(item,item.getBoundingBox().move(delta))){endItem(lease);continue;}
            item.setDeltaMovement(delta);item.hasImpulse=true;item.hurtMarked=true;
        }
    }
    private static boolean valid(Lease l){
        var p=l.owner;var s=AbilityStorage.get(p);return ACTIVE.get(l.target.getUUID())==l&&p.serverLevel()==l.world
                &&l.world.getGameTime()<l.expires&&readyAfter(p,l.skill)&&s.presets.current()==l.preset&&s.presets.revision()==l.revision
                &&eligible(p,l.target,l.skill,false)&&!CanonSkillsCombatB.leased(l.target)&&!CanonFloatItems.leased(l.target);
    }
    private static boolean permitted(Lease l,Vec3 v){
        var w=l.world;var target=l.target;Vec3 end=target.position().add(v);
        return finite(v)&&finite(end)&&v.lengthSqr()<4&&loaded(w,target.position(),end)
                &&w.mayInteract(l.owner,BlockPos.containing(end))&&w.getWorldBorder().isWithinBounds(target.getBoundingBox().move(v))
                &&w.noCollision(target,target.getBoundingBox().move(v));
    }
    private static void end(Lease l){if(ACTIVE.remove(l.target.getUUID(),l))CanonVfx.emit(l.owner,l.skill,l.target.position(),l.target.position(),CanonVfx.Phase.END,8);}
    private static boolean validItem(ItemLease l){
        var s=AbilityStorage.get(l.owner);return ITEMS.get(l.target.getUUID())==l&&l.owner.serverLevel()==l.world
                &&l.world.getGameTime()<l.expires&&readyAfter(l.owner,l.skill)&&s.presets.current()==l.preset&&s.presets.revision()==l.revision
                &&!MagneticItemPull.itemsActive(l.owner)&&!CanonFloatItems.leased(l.target)&&itemEligible(l.owner,l.target,l.skill.equals("float_item_shelf_field")
                        ? cn.academy.port.core.RangedSkillRanges.CANON_TARGET : number(l.skill, "reach"))
                &&ItemStack.matches(l.target.getItem(),l.stack);
    }
    private static void endItem(ItemLease l){if(ITEMS.remove(l.target.getUUID(),l))CanonVfx.emit(l.owner,l.skill,l.target.position(),l.target.position(),CanonVfx.Phase.END,8);}
    private static boolean loaded(ServerLevel w,Vec3 a,Vec3 b){
        if(!finite(a)||!finite(b))return false;
        int x0=Math.floorDiv((int)Math.floor(Math.min(a.x,b.x)),16),x1=Math.floorDiv((int)Math.floor(Math.max(a.x,b.x)),16);
        int z0=Math.floorDiv((int)Math.floor(Math.min(a.z,b.z)),16),z1=Math.floorDiv((int)Math.floor(Math.max(a.z,b.z)),16);
        if((long)x1-x0>4||(long)z1-z0>4)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!w.hasChunk(x,z))return false;return true;
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void remove(ServerPlayer p){for(var l:List.copyOf(ACTIVE.values()))if(l.owner==p||l.target==p)end(l);for(var l:List.copyOf(ITEMS.values()))if(l.owner==p)endItem(l);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){for(var l:List.copyOf(ACTIVE.values()))if(l.owner.server==e.getServer())end(l);for(var l:List.copyOf(ITEMS.values()))if(l.owner.server==e.getServer())endItem(l);}
}
