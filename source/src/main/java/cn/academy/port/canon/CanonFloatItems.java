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
import cn.academy.port.highpower.NativeItemMergeAccess;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Vertical control of the caller's verified native Q-toss only. No transfer or inventory mutation. */
@EventBusSubscriber(modid="academy")
public final class CanonFloatItems {
    public static final Set<String> IMPLEMENTED=Set.of("float_item_lift","float_item_lower","float_item_release","float_item_pull","float_item_push","float_item_field","float_release_field");
    private static final String CATEGORY="float_dial";
    private static final int MAX=64;
    private static final Map<ServerPlayer,Long> FIELD_GENERATION=new java.util.IdentityHashMap<>();
    private static final Set<ServerPlayer> PENDING_FIELDS=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    /** A lifted item rises this far and then floats in place, so lower/pull/release can still be chosen and aimed at it. */
    private static final Map<UUID,Lease> ACTIVE=new HashMap<>();
    private enum Mode { UP, DOWN, PULL }
    private static final class Lease {
        final ServerPlayer owner;final ServerLevel world;final ItemEntity item;final ItemStack stack;final long expires;final double hoverY;final double controlReach;
        Mode mode=Mode.UP;
        Lease(ServerPlayer p,ItemEntity item,long expires,String skill){
            owner=p;world=p.serverLevel();this.item=item;stack=item.getItem().copy();this.expires=expires;
            hoverY=item.getY()+number(skill, "rise_limit");
            controlReach=cn.academy.port.core.RangedSkillRanges.fixed("float_item_lift");
        }
    }
    private CanonFloatItems() {}
    static boolean leased(Entity target) { return target != null && ACTIVE.containsKey(target.getUUID()); }
    public static boolean cast(ServerPlayer p,String id) {
        if(p==null||id==null||!IMPLEMENTED.contains(id)||!ready(p,id))return false;
        if(id.equals("float_item_field"))return field(p,id);
        if(id.equals("float_release_field"))return releaseField(p,id);
        ItemEntity item=target(p,id);if(item==null)return false;Lease lease=ACTIVE.get(item.getUUID());
        if(id.equals("float_item_lift")) {
            if(lease!=null||CanonFloatExpansion.leased(item)||ACTIVE.size()>=MAX||p.serverLevel().getGameTime()>Long.MAX_VALUE-integer(id, "duration_ticks"))return false;
        } else if(!id.equals("float_item_push") && (lease==null||lease.owner!=p||!valid(lease)))return false;
        else if(id.equals("float_item_push") && (lease!=null||CanonFloatExpansion.leased(item)))return false;
        ItemStack before=item.getItem().copy();
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.5,20))return false;
        if(!eligible(p,item,cn.academy.port.core.RangedSkillRanges.fixed(id))||CanonFloatExpansion.leased(item)||!ItemStack.matches(item.getItem(),before)||target(p,id)!=item)return false;
        if(id.equals("float_item_lift")){
            if(ACTIVE.containsKey(item.getUUID())||CanonFloatExpansion.leased(item)||ACTIVE.size()>=MAX)return false;
            ACTIVE.put(item.getUUID(),new Lease(p,item,p.serverLevel().getGameTime()+integer(id, "duration_ticks"),id));
        } else if(id.equals("float_item_push")) {
            Vec3 away=item.position().subtract(p.position());Vec3 flat=new Vec3(away.x,0,away.z);
            if(flat.lengthSqr()<1e-8)return false;Vec3 velocity=flat.normalize().scale(number(id, "horizontal_speed")).add(0,.08,0);
            if(!movementPermitted(p,item,velocity))return false;
            item.setDeltaMovement(velocity);item.hasImpulse=true;item.hurtMarked=true;
        } else {
            if(ACTIVE.get(item.getUUID())!=lease||!valid(lease))return false;
            if(id.equals("float_item_lower"))lease.mode=Mode.DOWN;
            else if(id.equals("float_item_pull"))lease.mode=Mode.PULL;
            else ACTIVE.remove(item.getUUID(),lease);
        }
        CanonVfx.emit(p,id,p.getEyePosition(),item.position(),id.equals("float_item_release")?CanonVfx.Phase.END:CanonVfx.Phase.START,15);
        var state=AbilityStorage.get(p);if(CATEGORY.equals(state.category)&&state.learned(id)){state.addExperience(id,.01);AbilityStorage.save(p);}
        return true;
    }
    private static boolean ready(ServerPlayer p,String id) {
        if(!p.server.isSameThread()||p.connection==null||p.hasDisconnected()||p.isRemoved()||!p.isAlive()||p.isSpectator()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||AbilityConsumption.busy(p)
                ||HighPowerCombat.unsupportedReason(p).isPresent()||!HighPowerResources.ready(p))return false;
        var state=AbilityStorage.get(p);int required=id.equals("float_item_field")||id.equals("float_release_field")?5:2;
        return CATEGORY.equals(state.category)&&state.level>=required&&state.canUse(id)
                &&state.presets.currentContains(id)&&SkillCatalog.enabled(CATEGORY,id);
    }
    private static boolean field(ServerPlayer p,String id) {
        double radius=cn.academy.port.macro.MacroSkillRules.area(id,AbilityStorage.get(p).exp(id),number(id,"radius"));
        int ticks=integer(id, "duration_ticks");
        Vec3 center=p.position();
        var found=p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(radius),
                item->eligible(p,item,radius)&&!ACTIVE.containsKey(item.getUUID())&&!CanonFloatExpansion.leased(item));
        if(found.isEmpty())return false;
        // Buoyancy Float: items nearest the marker are taken first; the caster's own range and checks are unchanged.
        Vec3 marker=CanonAuxItems.floatMarker(p);
        found.sort(marker==null?Comparator.comparing(ItemEntity::getUUID)
                :Comparator.comparingDouble((ItemEntity i)->i.position().distanceToSqr(marker)).thenComparing(ItemEntity::getUUID));
        var selected=List.copyOf(found.subList(0,Math.min(integer(id, "target_cap"),found.size())));
        long now=p.serverLevel().getGameTime();if(now>Long.MAX_VALUE-ticks)return false;
        var snapshots=new ArrayList<ItemStack>();for(var item:selected)snapshots.add(item.getItem().copy());
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*2,60))return false;
        for(int i=0;i<selected.size();i++)if(!eligible(p,selected.get(i),radius)
                ||ACTIVE.containsKey(selected.get(i).getUUID())||CanonFloatExpansion.leased(selected.get(i))||!ItemStack.matches(selected.get(i).getItem(),snapshots.get(i)))return false;

        long generation=FIELD_GENERATION.merge(p,1L,Long::sum);PENDING_FIELDS.add(p);
        var world=p.serverLevel();var ownerState=AbilityStorage.get(p);
        cn.academy.port.macro.MacroSettlement.submit(world,selected,
                ()->p.isAlive()&&!p.hasDisconnected()&&p.serverLevel()==world&&AbilityStorage.get(p)==ownerState&&CATEGORY.equals(ownerState.category)&&ownerState.learned(id)&&FIELD_GENERATION.getOrDefault(p,0L)==generation,
                item->{if(eligible(p,item,radius)&&!ACTIVE.containsKey(item.getUUID())&&!CanonFloatExpansion.leased(item))ACTIVE.put(item.getUUID(),new Lease(p,item,now+ticks,id));},
                ()->{if(FIELD_GENERATION.getOrDefault(p,0L)==generation)PENDING_FIELDS.remove(p);});
        CanonVfx.emit(p,id,p.position(),selected.getFirst().position(),CanonVfx.Phase.START,18,radius);return true;
    }
    private static boolean releaseField(ServerPlayer p,String id) {
        var owned=ACTIVE.values().stream().filter(l->l.owner==p&&valid(l)).toList();if(owned.isEmpty()&&!PENDING_FIELDS.contains(p))return false;
        // A field may still be waiting behind this tick's shared settlement budget.
        // Releasing it has no installed first item; its real center is the owner.
        Vec3 endpoint=owned.isEmpty()?p.position():owned.getFirst().item.position();
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*.5,20))return false;
        if(owned.stream().anyMatch(l->!valid(l)))return false;
        FIELD_GENERATION.merge(p,1L,Long::sum);PENDING_FIELDS.remove(p);
        for(Lease l:owned)ACTIVE.remove(l.item.getUUID(),l);
        CanonVfx.emit(p,id,p.position(),endpoint,CanonVfx.Phase.END,12);return true;
    }
    private static boolean movementPermitted(ServerPlayer p,ItemEntity item,Vec3 next){
        Vec3 end=item.position().add(next);var world=p.serverLevel();
        return finite(end)&&loaded(world,item.position(),end)&&world.mayInteract(p,BlockPos.containing(end))
                &&world.getWorldBorder().isWithinBounds(item.getBoundingBox().move(next))
                &&world.noCollision(item,item.getBoundingBox().move(next));
    }
    private static ItemEntity target(ServerPlayer p,String id) {
        double reach=cn.academy.port.core.RangedSkillRanges.fixed(id);
        Vec3 eye=p.getEyePosition(),look=ClassicRaytrace.direction(p),end=eye.add(look.scale(reach));
        if(!finite(eye)||!finite(look)||!loaded(p.serverLevel(),eye,end))return null;
        // Native ItemEntity is deliberately not pickable: choose actual item hitboxes explicitly,
        // then preserve block occlusion and all ownership/protection fences.
        var found=p.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(eye,end).inflate(.5),item->eligible(p,item,reach));
        if(found.size()>64)return null;
        var block=p.serverLevel().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        double best=block.getType()==HitResult.Type.MISS?eye.distanceToSqr(end):eye.distanceToSqr(block.getLocation());
        ItemEntity selected=null;
        for(ItemEntity item:found){
            var point=item.getBoundingBox().inflate(.25).clip(eye,end);if(point.isEmpty())continue;
            double distance=eye.distanceToSqr(point.get());if(distance<best){best=distance;selected=item;}
        }
        return selected;
    }
    private static boolean eligible(ServerPlayer p,ItemEntity item,double reach) {
        var w=p.serverLevel();var stack=item.getItem();
        if(item.getClass()!=ItemEntity.class||item.getType()!=EntityType.ITEM||item.level()!=w||item.isRemoved()||!item.isAlive()
                ||w.getEntity(item.getUUID())!=item||item.noPhysics||item.isNoGravity()||item.isPassenger()||item.isVehicle()||item.isInvulnerable()
                ||item.getPersistentData().getBoolean("academy:protected")||item.getPersistentData().getBoolean("academy:immovable")
                ||!(item instanceof NativeItemMergeAccess seam)||seam.academy$nativeItemMergeContractVersion()!=1
                ||!MagneticItemProvenance.ownedBy(item,p.getUUID())||item.getTarget()!=null&&!p.getUUID().equals(item.getTarget())
                ||stack.isEmpty()||stack.getItem() instanceof BundleItem||stack.getItem() instanceof BlockItem b&&b.getBlock() instanceof EntityBlock
                ||stack.has(DataComponents.CONTAINER)||stack.has(DataComponents.BUNDLE_CONTENTS)||stack.has(DataComponents.CONTAINER_LOOT)
                ||stack.has(DataComponents.BLOCK_ENTITY_DATA)||!finite(item.position())||!finite(item.getDeltaMovement())
                ||p.distanceToSqr(item)>reach*reach||!w.hasChunkAt(item.blockPosition())||!w.mayInteract(p,item.blockPosition()))return false;
        Vec3 eye=p.getEyePosition(),center=item.getBoundingBox().getCenter();
        return loaded(w,eye,center)&&w.getWorldBorder().isWithinBounds(item.getBoundingBox())
                &&w.clip(new ClipContext(eye,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    private static boolean valid(Lease l) {
        var p=l.owner;var state=AbilityStorage.get(p);
        return ACTIVE.get(l.item.getUUID())==l&&p.serverLevel()==l.world&&p.connection!=null&&!p.hasDisconnected()
                &&p.server.getPlayerList().getPlayer(p.getUUID())==p&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()
                &&l.world.getGameTime()<l.expires&&CATEGORY.equals(state.category)&&state.activated&&state.overloadFine
                &&!state.interfering&&state.learned("float_item_lift")&&SkillCatalog.enabled(CATEGORY,"float_item_lift")
                &&eligible(p,l.item,l.controlReach)&&ItemStack.matches(l.item.getItem(),l.stack);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(Lease lease:List.copyOf(ACTIVE.values())) {
            if(lease.owner.server!=event.getServer())continue;
            if(!valid(lease)){ACTIVE.remove(lease.item.getUUID(),lease);continue;}
            var item=lease.item;Vec3 velocity=item.getDeltaMovement();
            Vec3 next;
            if(lease.mode==Mode.PULL){
                Vec3 delta=lease.owner.position().add(0,.8,0).subtract(item.position());
                if(delta.lengthSqr()<2.25){ACTIVE.remove(item.getUUID(),lease);continue;}
                next=delta.normalize().scale(number("float_item_pull", "speed"));
            } else {
                // UP climbs to the hover height and then cancels the item's own .04 gravity, holding it there until the lease ends.
                double y=lease.mode==Mode.DOWN?Math.max(-number("float_item_lower", "vertical_speed"),Math.min(-.04,velocity.y-.06)):Math.min(.18,Math.max(-.08,(lease.hoverY-item.getY())*.25+.04));
                next=new Vec3(velocity.x,y,velocity.z);
            }
            if(!movementPermitted(lease.owner,item,next)) {ACTIVE.remove(item.getUUID(),lease);continue;}
            item.setDeltaMovement(next);item.hasImpulse=true;item.hurtMarked=true;
        }
    }
    private static boolean loaded(ServerLevel w,Vec3 a,Vec3 b){
        int x0=Math.floorDiv((int)Math.floor(Math.min(a.x,b.x)),16),x1=Math.floorDiv((int)Math.floor(Math.max(a.x,b.x)),16);
        int z0=Math.floorDiv((int)Math.floor(Math.min(a.z,b.z)),16),z1=Math.floorDiv((int)Math.floor(Math.max(a.z,b.z)),16);
        if((long)x1-x0>4||(long)z1-z0>4)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!w.hasChunk(x,z))return false;return true;
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void remove(ServerPlayer p){ACTIVE.values().removeIf(l->l.owner==p);FIELD_GENERATION.remove(p);PENDING_FIELDS.remove(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){ACTIVE.values().removeIf(l->l.owner.server==e.getServer());FIELD_GENERATION.keySet().removeIf(p->p.server==e.getServer());PENDING_FIELDS.removeIf(p->p.server==e.getServer());}
}
