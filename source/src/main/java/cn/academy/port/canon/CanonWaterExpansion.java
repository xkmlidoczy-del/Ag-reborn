package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.CanonEmission;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/** World interactions draw on a selected, real and permitted water source. */
public final class CanonWaterExpansion {
    private static final String CATEGORY="hydro_hand";
    private static final List<SkillCatalog.Skill> SKILLS=List.of(
            skill("hydro_douse_self",1,"hydro_whip"),
            skill("hydro_douse_ally",2,"hydro_douse_self"),
            skill("hydro_douse_hostile",2,"hydro_douse_self"),
            skill("hydro_campfire_out",2,"hydro_extinguish"),
            skill("hydro_candle_out",3,"hydro_extinguish"),
            skill("hydro_pressure_jet",2,"hydro_whip"),
            skill("hydro_fan",3,"hydro_pressure_jet"),
            skill("hydro_eddy",5,"hydro_domain"),
            skill("hydro_dive",3,"hydro_current"),
            skill("hydro_lateral_current",3,"hydro_current"),
            skill("hydro_undertow",3,"hydro_current"),
            skill("hydro_tow_ally",4,"hydro_rescue"),
            skill("hydro_brake_field",5,"hydro_brake"));
    private static final Set<ServerPlayer> BUSY=Collections.newSetFromMap(new IdentityHashMap<>());
    private CanonWaterExpansion() {}
    public static List<SkillCatalog.Skill> skills(){return SKILLS;}
    public static boolean contains(String id){return SKILLS.stream().anyMatch(s->s.id().equals(id));}
    private static SkillCatalog.Skill skill(String id,int level,String parent){return new SkillCatalog.Skill(CATEGORY,id,level,true,List.of(new SkillCatalog.Requirement(parent,0)),0);}
    public static boolean cast(ServerPlayer p,String id){
        if(p==null||id==null||!p.server.isSameThread()||!contains(id)||!ready(p,id)||!BUSY.add(p))return false;
        try {
            var state=AbilityStorage.get(p);var world=p.serverLevel();BlockPos water=CanonWaterSkills.source(p);
            if(water==null||!CanonWaterSkills.validSource(p,water))return false;
            int preset=state.presets.current();long revision=state.presets.revision(),now=world.getGameTime();
            Vec3 pos=p.position(),eye=p.getEyePosition(),look=ClassicRaytrace.direction(p),velocity=p.getDeltaMovement();
            if(!finite(pos)||!finite(eye)||!finite(look)||!finite(velocity))return false;
            if(id.equals("hydro_dive")||id.equals("hydro_lateral_current")||id.equals("hydro_undertow")
                    ||id.equals("hydro_tow_ally")||id.equals("hydro_brake_field")) {
                var targets=motionTargets(p,id);if(targets.isEmpty())return debug(id,"no motion target");
                var positions=targets.stream().map(LivingEntity::position).toList();
                var speeds=targets.stream().map(LivingEntity::getDeltaMovement).toList();
                var next=new ArrayList<Vec3>();
                for(var t:targets){Vec3 v=motion(p,t,id);if(v==null||!motionPermitted(p,t,v))return debug(id,"motion blocked "+v);next.add(v);}
                if(!pay(p,id)||!same(p,id,state,world,preset,revision,now,pos,eye,look,velocity)
                        ||!CanonWaterSkills.validSource(p,water)||!motionTargets(p,id).equals(targets))return debug(id,"changed after payment");
                for(int i=0;i<targets.size();i++)if(!targets.get(i).position().equals(positions.get(i))
                        ||!targets.get(i).getDeltaMovement().equals(speeds.get(i))||!motionPermitted(p,targets.get(i),next.get(i)))return false;
                for(int i=0;i<targets.size();i++){var t=targets.get(i);t.setDeltaMovement(next.get(i));t.hasImpulse=true;t.hurtMarked=true;}
                success(p,id,water.getCenter(),targets.getFirst().position());return true;
            }
            if(id.equals("hydro_pressure_jet")||id.equals("hydro_fan")||id.equals("hydro_eddy")) {
                var targets=attackTargets(p,id);if(targets.isEmpty())return false;
                double cost=HighPowerGrowth.values(p).arcCost()*(id.equals("hydro_eddy")?4:id.equals("hydro_fan")?2:1);
                if(!Double.isFinite(cost)||cost<=0)return false;
                var emission=CanonEmission.begin(p,id,cost,id.equals("hydro_eddy")?100:50,targets,
                        t->same(p,id,state,world,preset,revision,now,pos,eye,look,velocity)
                                &&CanonWaterSkills.validSource(p,water)&&attackGeometry(p,id,t));
                if(emission==null)return false;
                boolean[] hit={false};emission.forEach(t->{if(emission.attack(t)){
                    CanonVfx.emit(p,id,water.getCenter(),t.getBoundingBox().getCenter(),CanonVfx.Phase.HIT,12,!hit[0]&&id.equals("hydro_eddy")?cn.academy.port.macro.MacroSkillRules.area(id,AbilityStorage.get(p).exp(id),number(id,"radius")):0);hit[0]=true;
                }},()->{
                if(hit[0]&&AbilityStorage.get(p)==state&&state.learned(id)){state.addExperience(id,.01);AbilityStorage.save(p);}
                });return cn.academy.port.macro.MacroSkillRules.ultimate(id)||hit[0];
            }
            if(id.equals("hydro_campfire_out")||id.equals("hydro_candle_out")){
                BlockHitResult hit=block(p,id);if(hit==null)return false;BlockPos target=hit.getBlockPos();BlockState old=world.getBlockState(target);
                var blockEntity=world.getBlockEntity(target);
                if(!blockValid(p,id,water,target,old))return false;
                if(NeoForge.EVENT_BUS.post(new SkillBlockDestroyEvent(p,CATEGORY+"."+id,target)).isCanceled()
                        ||NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,target,old,p)).isCanceled()
                        ||world.getBlockEntity(target)!=blockEntity||!blockValid(p,id,water,target,old)||!same(p,id,state,world,preset,revision,now,pos,eye,look,velocity))return false;
                if(!pay(p,id)||!same(p,id,state,world,preset,revision,now,pos,eye,look,velocity)||world.getBlockEntity(target)!=blockEntity||!blockValid(p,id,water,target,old))return false;
                var desired=old.setValue(BlockStateProperties.LIT,false);var snapshot=BlockSnapshot.create(world.dimension(),world,target);
                if(!world.setBlock(target,desired,2)||!world.getBlockState(target).equals(desired))return false;
                boolean denied;
                try{denied=EventHooks.onBlockPlace(p,snapshot,hit.getDirection());}
                catch(RuntimeException|Error ex){restore(world,target,old,desired,blockEntity);throw ex;}
                if(denied||!world.getBlockState(target).equals(desired)||!CanonWaterSkills.validSource(p,water)
                        ||world.getBlockEntity(target)!=blockEntity||!world.mayInteract(p,target)||!same(p,id,state,world,preset,revision,now,pos,eye,look,velocity)){
                    restore(world,target,old,desired,blockEntity);return false;
                }
                world.updateNeighborsAt(target,old.getBlock());success(p,id,water.getCenter(),target.getCenter());return true;
            }
            LivingEntity target=target(p,id);if(target==null||!validTarget(p,target,id))return false;
            int fire=target.getRemainingFireTicks();Vec3 targetPos=target.position();
            if(!pay(p,id)||!same(p,id,state,world,preset,revision,now,pos,eye,look,velocity)
                    ||!CanonWaterSkills.validSource(p,water)||target(p,id)!=target||!validTarget(p,target,id)
                    ||target.getRemainingFireTicks()!=fire||!target.position().equals(targetPos))return false;
            target.clearFire();if(target.isOnFire())return false;
            success(p,id,water.getCenter(),target.position());return true;
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
    private static boolean same(ServerPlayer p,String id,cn.academy.port.core.AbilityProgress s,ServerLevel w,int preset,long revision,long tick,
                                Vec3 pos,Vec3 eye,Vec3 look,Vec3 velocity){
        return AbilityStorage.get(p)==s&&p.serverLevel()==w&&(w.getGameTime()==tick||cn.academy.port.macro.MacroSkillRules.ultimate(id)&&w.getGameTime()>tick)&&after(p,id)
                &&s.presets.current()==preset&&s.presets.revision()==revision&&p.position().equals(pos)
                &&p.getEyePosition().equals(eye)&&ClassicRaytrace.direction(p).equals(look)&&p.getDeltaMovement().equals(velocity);
    }
    private static boolean pay(ServerPlayer p,String id){double cost=HighPowerGrowth.values(p).arcCost();return Double.isFinite(cost)&&cost>0&&HighPowerResources.trySpend(p,id,cost,30);}
    private static void success(ServerPlayer p,String id,Vec3 from,Vec3 to){
        double radius=id.equals("hydro_brake_field")?cn.academy.port.macro.MacroSkillRules.area(id,AbilityStorage.get(p).exp(id),number(id,"radius")):0;
        var s=AbilityStorage.get(p);if(CATEGORY.equals(s.category)&&s.learned(id)){s.addExperience(id,.01);AbilityStorage.save(p);}
        CanonVfx.emit(p,id,from,to,CanonVfx.Phase.HIT,12,radius);
    }
    private static LivingEntity target(ServerPlayer p,String id){
        if(id.equals("hydro_douse_self"))return p;
        Vec3 eye=p.getEyePosition(),end=eye.add(ClassicRaytrace.direction(p).scale(number(id, "reach")));if(!loaded(p.serverLevel(),eye,end))return null;
        var hit=ClassicRaytrace.living(p,number(id, "reach"),ClipContext.Fluid.NONE);
        return hit instanceof EntityHitResult h&&h.getEntity() instanceof LivingEntity e?e:null;
    }
    /** -Dacademy.debug.lease=true: the reason a motion cast was refused. */
    private static boolean debug(String id,String why){if(Boolean.getBoolean("academy.debug.lease"))com.mojang.logging.LogUtils.getLogger().info("WATER_REFUSED {} {}",id,why);return false;}
    private static boolean validTarget(ServerPlayer p,LivingEntity target,String id){
        var w=p.serverLevel();if(target.level()!=w||target.isRemoved()||!target.isAlive()||!target.isOnFire()||!finite(target.position())
                ||target!=p&&p.distanceToSqr(target)>Math.pow(number(id, "reach"),2)||!w.hasChunkAt(target.blockPosition())||!w.mayInteract(p,target.blockPosition())
                ||!w.getWorldBorder().isWithinBounds(target.getBoundingBox())||target!=p&&!p.hasLineOfSight(target))return false;
        if(id.equals("hydro_douse_self"))return target==p;
        if(id.equals("hydro_douse_ally"))return target instanceof ServerPlayer other&&other!=p&&other.isShiftKeyDown()
                &&!other.isSpectator()&&!other.hasDisconnected()&&other.connection!=null
                &&other.server.getPlayerList().getPlayer(other.getUUID())==other&&p.isAlliedTo(other)&&other.isAlliedTo(p);
        return id.equals("hydro_douse_hostile")&&target instanceof Mob&&CanonTargetPolicy.hostile(target)
                &&!target.isAlliedTo(p)&&!p.isAlliedTo(target);
    }
    private static List<LivingEntity> attackTargets(ServerPlayer p,String id){
        if(id.equals("hydro_pressure_jet")){
            Vec3 eye=p.getEyePosition(),end=eye.add(ClassicRaytrace.direction(p).scale(number(id, "reach")));
            if(!loaded(p.serverLevel(),eye,end))return List.of();
            var hit=ClassicRaytrace.living(p,number(id, "reach"),ClipContext.Fluid.NONE);
            return hit instanceof EntityHitResult h&&h.getEntity() instanceof LivingEntity t&&attackGeometry(p,id,t)?List.of(t):List.of();
        }
        double range=cn.academy.port.macro.MacroSkillRules.area(id,AbilityStorage.get(p).exp(id),number(id,"radius"));var pos=p.position();
        if(!cn.academy.port.macro.MacroSkillRules.ultimate(id)&&!loaded(p.serverLevel(),pos.add(-range,0,-range),pos.add(range,0,range)))return List.of();
        var found=p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range),t->attackGeometry(p,id,t));
        if(found.size()>64&&!cn.academy.port.macro.MacroSkillRules.ultimate(id))return List.of();found.sort(Comparator.comparing(net.minecraft.world.entity.Entity::getUUID));
        return List.copyOf(found.subList(0,Math.min(found.size(),integer(id, "target_cap"))));
    }
    private static boolean attackGeometry(ServerPlayer p,String id,LivingEntity target){
        var w=p.serverLevel();if(!(target instanceof Mob)||!CanonTargetPolicy.hostile(target)||target.level()!=w
                ||w.getEntity(target.getUUID())!=target||!target.isAlive()||target.isRemoved()||target.isPassenger()||target.isVehicle()
                ||p.isAlliedTo(target)||target.isAlliedTo(p)||!finite(target.position())||!w.hasChunkAt(target.blockPosition())
                ||!w.mayInteract(p,target.blockPosition())||!p.hasLineOfSight(target))return false;
        var type=target.getType();if(!CanonTargetPolicy.reviewed(target)&&(!BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")
                ||type==EntityType.WITHER||type==EntityType.ENDER_DRAGON||type==EntityType.WARDEN||type==EntityType.ELDER_GUARDIAN))return false;
        Vec3 delta=target.getBoundingBox().getCenter().subtract(p.getEyePosition());double distance=delta.lengthSqr();
        if(!finite(delta)||distance<1e-8)return false;
        if(id.equals("hydro_eddy"))return target.isInWaterOrBubble()&&p.distanceToSqr(target)<=Math.pow(cn.academy.port.macro.MacroSkillRules.area(id,AbilityStorage.get(p).exp(id),number(id,"radius")),2);
        if(id.equals("hydro_fan"))return p.distanceToSqr(target)<=Math.pow(cn.academy.port.macro.MacroSkillRules.area(id,AbilityStorage.get(p).exp(id),number(id,"radius")),2)&&delta.normalize().dot(ClassicRaytrace.direction(p))>=.6427876097;
        if(p.distanceToSqr(target)>Math.pow(number(id, "reach"),2))return false;
        var hit=ClassicRaytrace.living(p,number(id, "reach"),ClipContext.Fluid.NONE);
        return hit instanceof EntityHitResult e&&e.getEntity()==target;
    }
    private static List<LivingEntity> motionTargets(ServerPlayer p,String id){
        if(id.equals("hydro_dive")||id.equals("hydro_lateral_current"))return motionEligible(p,p,id)?List.of(p):List.of();
        if(id.equals("hydro_brake_field")){
            double radius=cn.academy.port.macro.MacroSkillRules.area(id,AbilityStorage.get(p).exp(id),number(id,"radius"));
            Vec3 c=p.position();if(!cn.academy.port.macro.MacroSkillRules.ultimate(id)&&!loaded(p.serverLevel(),c.add(-radius,0,-radius),c.add(radius,0,radius)))return List.of();
            var found=p.serverLevel().getEntitiesOfClass(ServerPlayer.class,p.getBoundingBox().inflate(radius),t->motionEligible(p,t,id));
            if(found.size()>64&&!cn.academy.port.macro.MacroSkillRules.ultimate(id))return List.of();found.sort(Comparator.comparing(net.minecraft.world.entity.Entity::getUUID));
            return List.copyOf(found.subList(0,Math.min(found.size(),integer(id, "target_cap"))));
        }
        Vec3 eye=p.getEyePosition(),end=eye.add(ClassicRaytrace.direction(p).scale(number(id, "reach")));if(!loaded(p.serverLevel(),eye,end))return List.of();
        var hit=ClassicRaytrace.living(p,number(id, "reach"),ClipContext.Fluid.NONE);
        return hit instanceof EntityHitResult h&&h.getEntity() instanceof LivingEntity t&&motionEligible(p,t,id)?List.of(t):List.of();
    }
    private static boolean motionEligible(ServerPlayer p,LivingEntity t,String id){
        var w=p.serverLevel();if(t.level()!=w||!t.isAlive()||t.isRemoved()||t.isNoGravity()||t.isPassenger()||t.isVehicle()
                ||!t.isInWaterOrBubble()||!finite(t.position())||!finite(t.getDeltaMovement())||t!=p&&p.distanceToSqr(t)>Math.pow(number(id,id.equals("hydro_brake_field")?"radius":"reach"),2)
                ||!w.hasChunkAt(t.blockPosition())||!w.mayInteract(p,t.blockPosition())||t!=p&&!p.hasLineOfSight(t))return false;
        if(t==p)return id.equals("hydro_dive")||id.equals("hydro_lateral_current")||id.equals("hydro_brake_field");
        if(id.equals("hydro_tow_ally")||id.equals("hydro_brake_field"))return t instanceof ServerPlayer other
                &&other.connection!=null&&!other.hasDisconnected()&&!other.isSpectator()&&other.isShiftKeyDown()
                &&other.server.getPlayerList().getPlayer(other.getUUID())==other&&p.isAlliedTo(other)&&other.isAlliedTo(p);
        if(!id.equals("hydro_undertow")||!(t instanceof Mob)||!CanonTargetPolicy.hostile(t)||p.isAlliedTo(t)||t.isAlliedTo(p))return false;
        var type=t.getType();return CanonTargetPolicy.reviewed(t)||BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")
                &&type!=EntityType.WITHER&&type!=EntityType.ENDER_DRAGON&&type!=EntityType.WARDEN&&type!=EntityType.ELDER_GUARDIAN;
    }
    private static Vec3 motion(ServerPlayer p,LivingEntity t,String id){
        Vec3 old=t.getDeltaMovement();
        if(id.equals("hydro_dive"))return new Vec3(old.x*.5,-number(id, "vertical_speed"),old.z*.5);
        if(id.equals("hydro_brake_field"))return old.scale(number(id, "motion_retention"));
        if(id.equals("hydro_lateral_current")){
            Vec3 look=ClassicRaytrace.direction(p),side=new Vec3(-look.z,0,look.x);if(side.lengthSqr()<1e-8)return null;
            side=side.normalize().scale(number(id, "horizontal_speed"));double y=Math.max(-.08,Math.min(.08,old.y));if(t.onGround()&&y<0)y=0;return new Vec3(side.x,y,side.z);
        }
        Vec3 delta=p.position().subtract(t.position()),horizontal=new Vec3(delta.x,0,delta.z);
        if(horizontal.lengthSqr()<1e-8)return null;horizontal=horizontal.normalize().scale(number(id, "horizontal_speed"));
        // A mob standing on the pool floor keeps a small downward speed; carrying it into the floor failed the collision check every time.
        double y=Math.max(-.08,Math.min(.08,old.y));if(t.onGround()&&y<0)y=0;
        return new Vec3(horizontal.x,y,horizontal.z);
    }
    private static boolean motionPermitted(ServerPlayer p,LivingEntity t,Vec3 v){
        var w=p.serverLevel();Vec3 end=t.position().add(v);return finite(v)&&finite(end)&&v.lengthSqr()<1
                &&loaded(w,t.position(),end)&&w.mayInteract(p,BlockPos.containing(end))
                &&w.getWorldBorder().isWithinBounds(t.getBoundingBox().move(v))&&w.noCollision(t,t.getBoundingBox().move(v));
    }
    private static BlockHitResult block(ServerPlayer p,String id){
        Vec3 eye=p.getEyePosition(),end=eye.add(ClassicRaytrace.direction(p).scale(number(id, "reach")));if(!loaded(p.serverLevel(),eye,end))return null;
        var hit=p.serverLevel().clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK?hit:null;
    }
    private static boolean blockValid(ServerPlayer p,String id,BlockPos water,BlockPos pos,BlockState expected){
        var w=p.serverLevel();if(!CanonWaterSkills.validSource(p,water)||!w.hasChunkAt(pos)
                ||!w.mayInteract(p,pos)||p.distanceToSqr(pos.getCenter())>Math.pow(number(id, "reach"),2)||!w.getBlockState(pos).equals(expected)
                ||!expected.hasProperty(BlockStateProperties.LIT)||!expected.getValue(BlockStateProperties.LIT))return false;
        return id.equals("hydro_campfire_out")?expected.is(BlockTags.CAMPFIRES)&&w.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.CampfireBlockEntity
                :expected.is(BlockTags.CANDLES)&&w.getBlockEntity(pos)==null;
    }
    private static void restore(ServerLevel w,BlockPos p,BlockState old,BlockState expected,net.minecraft.world.level.block.entity.BlockEntity be){if(w.hasChunkAt(p)&&w.getBlockEntity(p)==be&&w.getBlockState(p).equals(expected))w.setBlock(p,old,2);}
    private static boolean loaded(ServerLevel w,Vec3 a,Vec3 b){
        if(!finite(a)||!finite(b))return false;
        int x0=Math.floorDiv((int)Math.floor(Math.min(a.x,b.x)),16),x1=Math.floorDiv((int)Math.floor(Math.max(a.x,b.x)),16);
        int z0=Math.floorDiv((int)Math.floor(Math.min(a.z,b.z)),16),z1=Math.floorDiv((int)Math.floor(Math.max(a.z,b.z)),16);
        if((long)x1-x0>4||(long)z1-z0>4)return false;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!w.hasChunk(x,z))return false;return true;
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
