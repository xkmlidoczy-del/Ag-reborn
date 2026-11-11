package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.*;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import cn.academy.port.skill.ClassicRaytrace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Physical paper powers projectiles, limited arrow armor and a short server-driven glide. */
@EventBusSubscriber(modid="academy")
public final class CanonPaperSkills {
    public static final Set<String> IMPLEMENTED = Set.of("paper_blade", "paper_armor", "paper_array", "paper_wing", "paper_patch", "paper_retract", "paper_walkway", "paper_pad", "paper_screen", "paper_step", "paper_safety_rail", "paper_plate", "paper_switch", "paper_ladder", "paper_gate", "paper_plane", "paper_ramp", "paper_lift_bridge", "paper_cover", "paper_platform", "paper_catwalk", "paper_emergency_stairs", "paper_puzzle_link", "paper_switch_bridge");
    private static final String CATEGORY = "paper_psychokinesis";
    private static final int ARMOR_TICKS=integer("paper_armor", "duration_ticks");
    private static final double ARMOR_RADIUS=number("paper_armor", "radius");
    private static final int WING_TICKS=integer("paper_wing", "duration_ticks");
    private static final double WING_RISE=number("paper_wing", "rise_limit");
    private static final double ARRAY_RADIUS=number("paper_array", "radius");
    private static final int ARRAY_TARGETS=integer("paper_array", "target_cap");
    private static final BigDecimal SHIELD_SHARE = new BigDecimal("0.05");
    private static final int MAX_CONTEXTS = 64, MAX_CANDIDATES = 64;
    private static final Map<UUID, Armor> ARMOR = new HashMap<>();
    private static final Map<UUID, Wing> WINGS = new HashMap<>();
    private record Wing(ServerPlayer owner,ServerLevel world,long started,long expires,double ceiling) {}
    private static final class PaperParticles {
        private static final ItemParticleOption INSTANCE = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER));
    }
    private static final class Armor {
        final ServerPlayer owner; final ServerLevel world; final long expires;
        int sheets = 3;
        Armor(ServerPlayer owner, long expires) { this.owner=owner; world=owner.serverLevel(); this.expires=expires; }
    }
    private CanonPaperSkills() {}

    public static boolean cast(ServerPlayer p, String id) {
        if (id == null || !IMPLEMENTED.contains(id) || p == null || p.connection == null || !p.server.isSameThread()
                || p.hasDisconnected() || p.server.getPlayerList().getPlayer(p.getUUID()) != p
                || p.isRemoved() || !p.isAlive() || p.isSpectator() || AbilityConsumption.busy(p)
                || HighPowerCombat.unsupportedReason(p).isPresent() || !HighPowerResources.ready(p)) return false;
        var state = AbilityStorage.get(p);
        int level = id.equals("paper_blade") || id.equals("paper_patch") || id.equals("paper_retract") ? 1 : Set.of("paper_walkway","paper_pad","paper_screen","paper_step","paper_plane","paper_ramp").contains(id) ? 2 : Set.of("paper_safety_rail","paper_ladder","paper_gate","paper_platform","paper_puzzle_link").contains(id) ? 4 : Set.of("paper_array","paper_catwalk","paper_emergency_stairs","paper_switch_bridge").contains(id) ? 5 : 3;
        if (!CATEGORY.equals(state.category) || state.level < level || !state.canUse(id)
                || !state.presets.currentContains(id) || !SkillCatalog.enabled(CATEGORY,id)
                || id.equals("paper_armor") && (ARMOR.containsKey(p.getUUID()) || ARMOR.size() >= MAX_CONTEXTS)
                || id.equals("paper_wing") && (WINGS.containsKey(p.getUUID()) || WINGS.size() >= MAX_CONTEXTS)) return false;
        if(id.equals("paper_retract"))return retract(p,state);
        ItemStack held = CanonAuxItems.isSheet(p.getMainHandItem()) ? p.getMainHandItem() : p.getOffhandItem();
        int amount = id.equals("paper_blade") || id.equals("paper_plane") ? 1 : id.equals("paper_patch") || id.equals("paper_plate") || id.equals("paper_switch") || id.equals("paper_ladder") || id.equals("paper_gate") ? 2 : id.equals("paper_armor") ? 3 : id.equals("paper_puzzle_link") ? 4 : id.equals("paper_platform") ? 18 : id.equals("paper_cover") ? 12 : id.equals("paper_catwalk") ? 10 : Set.of("paper_wing","paper_pad","paper_ramp","paper_lift_bridge","paper_emergency_stairs").contains(id) ? 8 : 6;
        if (!CanonAuxItems.isSheet(held) || held.getCount() < amount) return false;
        long now = p.serverLevel().getGameTime();
        if (now < 0 || now > Long.MAX_VALUE - ARMOR_TICKS) return false;
        if (id.equals("paper_blade")) return blade(p, held, state);
        if (id.equals("paper_plane")) return plane(p,held,state);
        if (id.equals("paper_array")) return array(p, held, state);
        if (id.equals("paper_wing")) return wing(p, held, state, now);
        if (id.equals("paper_patch")) return patch(p,held,state);
        if (id.equals("paper_plate")) return plate(p,held,state);
        if (id.equals("paper_puzzle_link")||id.equals("paper_switch_bridge"))return assembly(p,held,state,id,amount);
        if (id.equals("paper_switch")||id.equals("paper_ladder")||id.equals("paper_gate")) return attached(p,held,state,id);
        if (Set.of("paper_walkway","paper_pad","paper_screen","paper_step","paper_safety_rail","paper_ramp","paper_lift_bridge","paper_cover","paper_platform","paper_catwalk","paper_emergency_stairs").contains(id))
            return structure(p,held,state,id);
        var expected = held.copy();var world=p.serverLevel();long revision=state.presets.revision();int preset=state.presets.current();
        if (!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*2,80)) return false;
        // A failed post-payment material fence never creates free sheets or repeats/refunds the debit.
        if ((p.getMainHandItem() != held && p.getOffhandItem() != held) || !ItemStack.matches(held,expected)
                ||p.serverLevel()!=world||world.getGameTime()!=now||p.hasDisconnected()||p.isRemoved()||!p.isAlive()||p.isSpectator()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||AbilityStorage.get(p)!=state
                ||!CATEGORY.equals(state.category)||state.level<level||!state.activated||!state.overloadFine||state.interfering
                ||!state.learned(id)||!state.presets.currentContains(id)||state.presets.revision()!=revision||state.presets.current()!=preset
                ||!SkillCatalog.enabled(CATEGORY,id)||HighPowerCombat.unsupportedReason(p).isPresent()
                ||ARMOR.containsKey(p.getUUID())||ARMOR.size()>=MAX_CONTEXTS)return false;
        if (!p.getAbilities().instabuild) { held.shrink(3); p.getInventory().setChanged(); }
        ARMOR.put(p.getUUID(),new Armor(p,now+ARMOR_TICKS));
        CanonVfx.emit(p,"paper_armor",p.position(),p.position(),CanonVfx.Phase.START,30);
        if (AbilityStorage.get(p)==state && CATEGORY.equals(state.category) && state.learned(id)) {
            state.addExperience(id,.01); AbilityStorage.save(p);
        }
        return true;
    }

    /** Build physical, native redstone assemblies as one rollback-safe leased transaction. */
    private static boolean assembly(ServerPlayer p,ItemStack held,cn.academy.port.core.AbilityProgress state,String id,int sheets) {
        var hit=patchHit(p,id);if(hit==null)return false;var world=p.serverLevel();BlockPos support=hit.getBlockPos().relative(hit.getDirection());
        BlockPos trigger=support.above(),gate=support.east();boolean bridge=id.equals("paper_switch_bridge");
        var positions=bridge?List.of(support,trigger,gate):List.of(support,trigger);
        for(BlockPos pos:positions)if(!world.hasChunkAt(pos)||!world.getBlockState(pos).isAir()||world.getBlockEntity(pos)!=null
                ||!world.mayInteract(p,pos)||p.distanceToSqr(pos.getCenter())>Math.pow(number(id, "reach"),2)||!world.noCollision(null,new net.minecraft.world.phys.AABB(pos)))return false;
        if(!world.mayInteract(p,hit.getBlockPos()))return false;ItemStack expected=held.copy();
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost()*2,60))return false;
        var checked=patchHit(p,id);
        if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!CATEGORY.equals(state.category)
                ||!state.activated||!state.overloadFine||state.interfering||!state.learned(id)||!state.presets.currentContains(id)
                ||checked==null||!checked.getBlockPos().equals(hit.getBlockPos())||checked.getDirection()!=hit.getDirection())return false;
        var tokens=new ArrayList<UUID>();boolean committed=false;
        try {
            UUID a=CanonPaperContent.place(p,support,hit.getDirection(),id);if(a==null)return false;tokens.add(a);
            UUID b=bridge?CanonPaperContent.placeButton(p,trigger,net.minecraft.core.Direction.UP,id):CanonPaperContent.placePlate(p,trigger,net.minecraft.core.Direction.UP,id);
            if(b==null)return false;tokens.add(b);
            if(bridge){UUID c=CanonPaperContent.placeGate(p,gate,net.minecraft.core.Direction.EAST,id);if(c==null)return false;tokens.add(c);}
            if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!CATEGORY.equals(state.category)
                    ||!state.activated||!state.overloadFine||state.interfering||!state.learned(id)||!state.presets.currentContains(id))return false;
            for(BlockPos pos:positions)if(!CanonPaperContent.owned(p,pos))return false;
            if(!p.getAbilities().instabuild){held.shrink(sheets);p.getInventory().setChanged();}
            committed=true;CanonVfx.emit(p,id,support.getCenter(),positions.getLast().getCenter(),CanonVfx.Phase.START,25);
            state.addExperience(id,.01);AbilityStorage.save(p);return true;
        } finally {if(!committed)for(UUID token:tokens)CanonPaperContent.discard(token);}
    }

    private static boolean plane(ServerPlayer p,ItemStack held,cn.academy.port.core.AbilityProgress state) {
        Vec3 eye=p.getEyePosition(),direction=ClassicRaytrace.direction(p),start=eye.add(direction.scale(.7));var world=p.serverLevel();
        if(!finite(eye)||!finite(direction)||!finite(start)||!chunksLoaded(world,eye,start)
                ||!world.mayInteract(p,BlockPos.containing(start)))return false;
        var plane=CanonPaperContent.PLANE.get().create(world);if(plane==null)return false;
        Vec3 velocity=direction.scale(number("paper_plane", "speed"));
        plane.setOwner(p);plane.setPos(start);plane.setDeltaMovement(velocity);
        if(!world.noCollision(plane,plane.getBoundingBox())){plane.discard();return false;}
        ItemStack expected=held.copy();
        if(!HighPowerResources.trySpend(p,"paper_plane",HighPowerGrowth.values(p).arcCost(),30)){plane.discard();return false;}
        if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine
                ||state.interfering||!state.presets.currentContains("paper_plane")||!world.addFreshEntity(plane)){
            plane.discard();return false;
        }
        if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine
                ||state.interfering||!state.presets.currentContains("paper_plane")||plane.isRemoved()
                ||world.getEntity(plane.getUUID())!=plane||p.serverLevel()!=world||plane.getOwner()!=p
                ||!plane.position().equals(start)||!plane.getDeltaMovement().equals(velocity)
                ||!world.hasChunkAt(plane.blockPosition())||!world.mayInteract(p,plane.blockPosition())
                ||!world.noCollision(plane,plane.getBoundingBox())){plane.discard();return false;}
        if(!p.getAbilities().instabuild){held.shrink(1);p.getInventory().setChanged();}
        CanonVfx.emit(p,"paper_plane",eye,start.add(direction.scale(3)),CanonVfx.Phase.START,15);
        state.addExperience("paper_plane",.01);AbilityStorage.save(p);return true;
    }

    private static BlockHitResult patchHit(ServerPlayer p,String skill) {
        double reach=number(skill, "reach");
        Vec3 from=p.getEyePosition(),direction=ClassicRaytrace.direction(p);
        if(!finite(from)||!finite(direction)||!chunksLoaded(p.serverLevel(),from,from.add(direction.scale(reach))))return null;
        var hit=p.serverLevel().clip(new ClipContext(from,from.add(direction.scale(reach)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK ? hit : null;
    }
    /** Two physical paper sheets become a real, temporary, solid construction block. */
    private static boolean patch(ServerPlayer p,ItemStack held,cn.academy.port.core.AbilityProgress state) {
        var hit=patchHit(p,"paper_patch");if(hit==null)return false;var world=p.serverLevel();
        BlockPos target=hit.getBlockPos().relative(hit.getDirection());
        if(!world.hasChunkAt(target)||!world.getBlockState(target).isAir()||world.getBlockEntity(target)!=null
                ||!world.mayInteract(p,hit.getBlockPos())||!world.mayInteract(p,target)
                ||p.distanceToSqr(target.getCenter())>Math.pow(number("paper_patch", "reach"),2)||!world.noCollision(null,new net.minecraft.world.phys.AABB(target)))return false;
        ItemStack expected=held.copy();
        if(!HighPowerResources.trySpend(p,"paper_patch",HighPowerGrowth.values(p).arcCost(),30))return false;
        var checked=patchHit(p,"paper_patch");
        if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!CATEGORY.equals(state.category)
                ||!state.activated||!state.overloadFine||state.interfering||!state.learned("paper_patch")
                ||!state.presets.currentContains("paper_patch")||!SkillCatalog.enabled(CATEGORY,"paper_patch")
                ||checked==null||!checked.getBlockPos().equals(hit.getBlockPos())||checked.getDirection()!=hit.getDirection()
                ||!world.mayInteract(p,hit.getBlockPos()))return false;
        UUID token=CanonPaperContent.place(p,target,hit.getDirection(),"paper_patch");if(token==null)return false;
        if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine
                ||state.interfering||!CATEGORY.equals(state.category)||!state.learned("paper_patch")
                ||!state.presets.currentContains("paper_patch")||!CanonPaperContent.owned(p,target)) {
            CanonPaperContent.discard(token);return false;
        }
        if(!p.getAbilities().instabuild){held.shrink(2);p.getInventory().setChanged();}
        CanonVfx.emit(p,"paper_patch",p.getEyePosition(),target.getCenter(),CanonVfx.Phase.START,20);
        state.addExperience("paper_patch",.01);AbilityStorage.save(p);return true;
    }

    /** A real redstone pressure plate on an owned support, built from consumed paper. */
    private static boolean plate(ServerPlayer p,ItemStack held,cn.academy.port.core.AbilityProgress state) {
        var hit=patchHit(p,"paper_plate");if(hit==null||hit.getDirection()!=net.minecraft.core.Direction.UP
                ||!CanonPaperContent.owned(p,hit.getBlockPos()))return false;
        var world=p.serverLevel();BlockPos support=hit.getBlockPos(),target=support.above();ItemStack expected=held.copy();
        if(!world.hasChunkAt(target)||!world.getBlockState(target).isAir()||world.getBlockEntity(target)!=null
                ||!world.mayInteract(p,target)||!world.noCollision(null,new net.minecraft.world.phys.AABB(target)))return false;
        if(!HighPowerResources.trySpend(p,"paper_plate",HighPowerGrowth.values(p).arcCost(),30))return false;
        var checked=patchHit(p,"paper_plate");
        if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine||state.interfering
                ||!state.presets.currentContains("paper_plate")||checked==null||!checked.getBlockPos().equals(support)
                ||checked.getDirection()!=net.minecraft.core.Direction.UP||!CanonPaperContent.owned(p,support))return false;
        UUID token=CanonPaperContent.placePlate(p,target,net.minecraft.core.Direction.UP,"paper_plate");if(token==null)return false;
        if(!materialMatches(p,held,expected)||!CanonPaperContent.owned(p,target)||!CanonPaperContent.owned(p,support)
                ||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine||state.interfering
                ||!state.presets.currentContains("paper_plate")){CanonPaperContent.discard(token);return false;}
        if(!p.getAbilities().instabuild){held.shrink(2);p.getInventory().setChanged();}
        CanonVfx.emit(p,"paper_plate",support.getCenter(),target.getCenter(),CanonVfx.Phase.START,18);
        state.addExperience("paper_plate",.01);AbilityStorage.save(p);return true;
    }

    /** Real attached utility block, subject to the owned support and placement protection. */
    private static boolean attached(ServerPlayer p,ItemStack held,cn.academy.port.core.AbilityProgress state,String skill) {
        var hit=patchHit(p,skill);if(hit==null||!CanonPaperContent.owned(p,hit.getBlockPos()))return false;
        if(skill.equals("paper_ladder")&&!hit.getDirection().getAxis().isHorizontal())return false;
        var world=p.serverLevel();BlockPos support=hit.getBlockPos(),target=support.relative(hit.getDirection());ItemStack expected=held.copy();
        if(!world.hasChunkAt(target)||!world.getBlockState(target).isAir()||world.getBlockEntity(target)!=null
                ||!world.mayInteract(p,target)||!world.noCollision(null,new net.minecraft.world.phys.AABB(target)))return false;
        if(!HighPowerResources.trySpend(p,skill,HighPowerGrowth.values(p).arcCost(),30))return false;
        var checked=patchHit(p,skill);
        if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine||state.interfering
                ||!state.presets.currentContains(skill)||checked==null||!checked.getBlockPos().equals(support)
                ||checked.getDirection()!=hit.getDirection()||!CanonPaperContent.owned(p,support))return false;
        UUID token=switch(skill){case "paper_switch"->CanonPaperContent.placeButton(p,target,hit.getDirection(),skill);
            case "paper_ladder"->CanonPaperContent.placeLadder(p,target,hit.getDirection(),skill);
            case "paper_gate"->CanonPaperContent.placeGate(p,target,hit.getDirection(),skill);default->null;};if(token==null)return false;
        if(!materialMatches(p,held,expected)||!CanonPaperContent.owned(p,target)||!CanonPaperContent.owned(p,support)
                ||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine||state.interfering
                ||!state.presets.currentContains(skill)){CanonPaperContent.discard(token);return false;}
        if(!p.getAbilities().instabuild){held.shrink(2);p.getInventory().setChanged();}
        CanonVfx.emit(p,skill,support.getCenter(),target.getCenter(),CanonVfx.Phase.START,18);
        state.addExperience(skill,.01);AbilityStorage.save(p);return true;
    }

    private static boolean retract(ServerPlayer p,cn.academy.port.core.AbilityProgress state) {
        var hit=patchHit(p,"paper_retract");if(hit==null||!CanonPaperContent.owned(p,hit.getBlockPos()))return false;
        BlockPos pos=hit.getBlockPos();Vec3 endpoint=pos.getCenter();
        if(!HighPowerResources.trySpend(p,"paper_retract",HighPowerGrowth.values(p).arcCost()*.25,10))return false;
        var now=patchHit(p,"paper_retract");
        if(AbilityStorage.get(p)!=state||!state.presets.currentContains("paper_retract")||now==null
                ||!now.getBlockPos().equals(pos)||!CanonPaperContent.consumeOwned(p,List.of(pos)))return false;
        CanonVfx.emit(p,"paper_retract",p.getEyePosition(),endpoint,CanonVfx.Phase.END,12);
        return true;
    }
    /** Fixed, bounded physical structures; a failed segment rolls back this attempt's leases. */
    private static boolean structure(ServerPlayer p,ItemStack held,cn.academy.port.core.AbilityProgress state,String skill) {
        var hit=patchHit(p,skill);if(hit==null)return false;var world=p.serverLevel();
        Vec3 look=ClassicRaytrace.direction(p);int dx=0,dz=0;
        if(Math.abs(look.x)>Math.abs(look.z))dx=look.x>0?-1:1;else dz=look.z>0?-1:1;
        int sx=-dz,sz=dx;BlockPos first=hit.getBlockPos().relative(hit.getDirection());var positions=new ArrayList<BlockPos>();
        switch(skill){
            case "paper_walkway" -> {for(int i=0;i<3;i++)positions.add(first.offset(dx*i,0,dz*i));}
            case "paper_screen" -> {for(int i=0;i<3;i++)positions.add(first.above(i));}
            case "paper_safety_rail" -> {for(int i=0;i<3;i++)positions.add(first.offset(sx*i,0,sz*i));}
            case "paper_step" -> {for(int i=0;i<3;i++)positions.add(first.offset(dx*(2-i),i,dz*(2-i)));}
            case "paper_pad" -> {for(int i=0;i<2;i++)for(int j=0;j<2;j++)positions.add(first.offset(dx*i+sx*j,0,dz*i+sz*j));}
            case "paper_ramp" -> {for(int i=0;i<4;i++)positions.add(first.offset(dx*i,i,dz*i));}
            case "paper_lift_bridge" -> {positions.add(first);positions.add(first.offset(dx,0,dz));positions.add(first.offset(dx*2,1,dz*2));positions.add(first.offset(dx*3,1,dz*3));}
            case "paper_cover" -> {for(int i=-1;i<=1;i++)for(int y=0;y<2;y++)positions.add(first.offset(sx*i,y,sz*i));}
            case "paper_platform" -> {for(int i=0;i<3;i++)for(int j=-1;j<=1;j++)positions.add(first.offset(dx*i+sx*j,0,dz*i+sz*j));}
            case "paper_catwalk" -> {for(int i=-2;i<=2;i++)positions.add(first.offset(sx*i,0,sz*i));}
            case "paper_emergency_stairs" -> {for(int i=0;i<4;i++)positions.add(first.offset(dx*i,-i,dz*i));}
            default -> {return false;}
        }
        for(BlockPos pos:positions)if(!world.hasChunkAt(pos)||!world.getBlockState(pos).isAir()||world.getBlockEntity(pos)!=null
                ||!world.mayInteract(p,pos)||p.distanceToSqr(pos.getCenter())>Math.pow(number(skill, "reach"),2)
                ||!world.noCollision(null,new net.minecraft.world.phys.AABB(pos)))return false;
        if(!world.mayInteract(p,hit.getBlockPos()))return false;
        ItemStack expected=held.copy();
        if(!HighPowerResources.trySpend(p,skill,HighPowerGrowth.values(p).arcCost()*2,60))return false;
        if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine
                ||state.interfering||!state.presets.currentContains(skill))return false;
        var tokens=new ArrayList<UUID>();boolean committed=false;
        try {
            for(BlockPos pos:positions) {
                UUID token=CanonPaperContent.place(p,pos,hit.getDirection(),skill);if(token==null)return false;
                tokens.add(token);
                if(!materialMatches(p,held,expected)||AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine
                        ||state.interfering||!state.presets.currentContains(skill))return false;
            }
            for(BlockPos pos:positions)if(!CanonPaperContent.owned(p,pos))return false;
            if(!p.getAbilities().instabuild){held.shrink(positions.size()*2);p.getInventory().setChanged();}
            committed=true;CanonVfx.emit(p,skill,positions.getFirst().getCenter(),positions.getLast().getCenter(),CanonVfx.Phase.START,30);
            state.addExperience(skill,.01);AbilityStorage.save(p);return true;
        } finally {if(!committed)for(UUID token:tokens)CanonPaperContent.discard(token);}
    }

    /** Eight paper sheets provide a bounded glide, with no flight ability flag. */
    private static boolean wing(ServerPlayer p,ItemStack held,cn.academy.port.core.AbilityProgress state,long now) {
        ServerLevel world=p.serverLevel();Vec3 start=p.position();
        if(!wingActor(p,world) || !finite(start) || !chunksLoaded(world,start,start)
                || !world.mayInteract(p,BlockPos.containing(start)) || !Double.isFinite(start.y+WING_RISE)
                || now>Long.MAX_VALUE-WING_TICKS) return false;
        ItemStack expected=held.copy();
        if(!HighPowerResources.trySpend(p,"paper_wing",HighPowerGrowth.values(p).arcCost()*3,100))return false;
        if(!materialMatches(p,held,expected) || AbilityStorage.get(p)!=state || !wingActor(p,world)
                || !CATEGORY.equals(state.category) || !state.activated || !state.overloadFine || state.interfering
                || !state.learned("paper_wing") || !state.presets.currentContains("paper_wing")
                || !SkillCatalog.enabled(CATEGORY,"paper_wing") || WINGS.size()>=MAX_CONTEXTS || WINGS.containsKey(p.getUUID())
                || !p.position().equals(start) || !world.mayInteract(p,BlockPos.containing(start)))return false;
        if(!p.getAbilities().instabuild){held.shrink(8);p.getInventory().setChanged();}
        WINGS.put(p.getUUID(),new Wing(p,world,now,now+WING_TICKS,start.y+WING_RISE));
        CanonVfx.emit(p,"paper_wing",p.position(),p.position(),CanonVfx.Phase.START,WING_TICKS);
        state.addExperience("paper_wing",.01);AbilityStorage.save(p);return true;
    }
    private static boolean wingActor(ServerPlayer p,ServerLevel world) {
        return p!=null && p.server.isSameThread() && p.connection!=null && !p.hasDisconnected()
                && p.server.getPlayerList().getPlayer(p.getUUID())==p && p.serverLevel()==world
                && p.isAlive() && !p.isRemoved() && !p.isSpectator() && !p.isPassenger() && !p.isVehicle()
                && !p.getAbilities().flying && !p.isFallFlying() && !p.isNoGravity() && !p.isInWaterOrBubble()
                && !p.isInLava() && !p.isShiftKeyDown() && !AbilityConsumption.busy(p)
                && finite(p.position()) && finite(p.getDeltaMovement()) && HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    private static boolean valid(Wing wing) {
        var p=wing.owner;var state=AbilityStorage.get(p);long now=wing.world.getGameTime();
        return WINGS.get(p.getUUID())==wing && wingActor(p,wing.world) && now<wing.expires
                && (now-wing.started<8 || !p.onGround()) && CATEGORY.equals(state.category)
                && state.activated && state.overloadFine && !state.interfering && state.learned("paper_wing")
                && SkillCatalog.enabled(CATEGORY,"paper_wing") && wing.world.hasChunkAt(p.blockPosition());
    }
    private static void advance(Wing wing) {
        var p=wing.owner;var world=wing.world;Vec3 look=ClassicRaytrace.direction(p),motion=p.getDeltaMovement();
        if(!finite(look)||!finite(motion)){finishWing(wing);return;}
        Vec3 horizontal=new Vec3(look.x,0,look.z);
        if(horizontal.lengthSqr()>1e-8)horizontal=horizontal.normalize().scale(number("paper_wing", "horizontal_speed"));
        double up=p.getY()>=wing.ceiling?-.10:look.y>.35?.10:-.08;
        Vec3 next=new Vec3(horizontal.x,Math.min(.12,Math.max(-.12,Math.max(motion.y,up))),horizontal.z);
        if(p.getY()>=wing.ceiling)next=new Vec3(next.x,Math.min(-.08,next.y),next.z);
        else next=new Vec3(next.x,Math.min(next.y,wing.ceiling-p.getY()),next.z);
        Vec3 endpoint=p.position().add(next);
        if(!finite(endpoint)||!chunksLoaded(world,p.position(),endpoint)
                || !world.mayInteract(p,BlockPos.containing(endpoint))) {finishWing(wing);return;}
        if(!world.noCollision(p,p.getBoundingBox().move(next)))next=new Vec3(0,Math.min(0,next.y),0);
        p.setDeltaMovement(next);p.hasImpulse=true;p.hurtMarked=true;p.fallDistance=0;
        if(world.getGameTime()%4==0) {
            Vec3 right=new Vec3(-look.z,0,look.x);if(right.lengthSqr()>1e-8)right=right.normalize();
            for(int side:new int[]{-1,1})for(int i=1;i<=4;i++) {
                Vec3 at=p.position().add(right.scale(side*(.35+i*.22))).add(0,1.1+i*.08,0);
                world.sendParticles(PaperParticles.INSTANCE,at.x,at.y,at.z,1,.03,.03,.03,0);
            }
        }
    }

    /** Six physical sheets, one bounded whole-cast damage allocation over at most six visible hostiles. */
    private static boolean array(ServerPlayer player, ItemStack held, cn.academy.port.core.AbilityProgress state) {
        Vec3 origin=player.getEyePosition(), center=player.position();
        if(!finite(origin)||!finite(center))return false;
        var candidates=player.serverLevel().getEntitiesOfClass(Mob.class,player.getBoundingBox().inflate(arrayRadius(player)),target ->
                CanonTargetPolicy.hostile(target) && target.isAlive() && !target.isRemoved() && !target.isAlliedTo(player)
                        && !player.isAlliedTo(target) && target.distanceToSqr(player)<=arrayRadius(player)*arrayRadius(player) && player.hasLineOfSight(target)
                        && HighPowerCombat.unsupportedReason(target).isEmpty());
        if(candidates.isEmpty())return false;
        candidates.sort(Comparator.comparingDouble(target->target.distanceToSqr(player)));
        var targets=new ArrayList<LivingEntity>();for(Mob target:candidates){if(targets.size()==ARRAY_TARGETS)break;targets.add(target);}
        var expected=held.copy();
        final ItemStack[] fence={expected};
        var emission=CanonEmission.begin(player,"paper_array",HighPowerGrowth.values(player).arcCost()*4,100,targets,
                target -> materialMatches(player,held,fence[0]) && arrayTarget(player,target));
        if(emission==null)return false;
        if((player.getMainHandItem()!=held&&player.getOffhandItem()!=held)||!ItemStack.matches(held,expected))return false;
        if(!player.getAbilities().instabuild){held.shrink(6);player.getInventory().setChanged();}
        fence[0]=held.copy();
        boolean[] hit={false};
        int[] ringed={0};
        emission.forEach(target->{
            // A previous target's damage hook can move/reclassify later victims or move the caster.
            if(!materialMatches(player,held,fence[0]) || !arrayTarget(player,target))return;
            boolean accepted=emission.attack(target);hit[0] |= accepted;Vec3 end=target.getBoundingBox().getCenter(),delta=end.subtract(origin);
            if(accepted)CanonVfx.emit(player,"paper_array",origin,end,CanonVfx.Phase.HIT,12,ringed[0]++==0?arrayRadius(player):0);
            int count=Math.max(1,Math.min(24,(int)Math.ceil(delta.length()*2)));
            for(int i=0;i<=count;i++){Vec3 point=origin.add(delta.scale((double)i/count));
                player.serverLevel().sendParticles(PaperParticles.INSTANCE,point.x,point.y,point.z,1,.02,.02,.02,0);}
        },()->{
        if(hit[0]&&AbilityStorage.get(player)==state&&CATEGORY.equals(state.category)&&state.learned("paper_array")){
            state.addExperience("paper_array",.01);AbilityStorage.save(player);
        }
        });
        return true;
    }

    private static boolean materialMatches(ServerPlayer p, ItemStack held, ItemStack expected) {
        return (p.getMainHandItem()==held || p.getOffhandItem()==held) && ItemStack.matches(held,expected);
    }
    private static double arrayRadius(ServerPlayer p){return cn.academy.port.macro.MacroSkillRules.area("paper_array",AbilityStorage.get(p).exp("paper_array"),ARRAY_RADIUS);}
    private static boolean arrayTarget(ServerPlayer p, LivingEntity target) {
        return target instanceof Mob && CanonTargetPolicy.hostile(target) && target.isAlive() && !target.isRemoved()
                && target.level()==p.level() && target.distanceToSqr(p)<=arrayRadius(p)*arrayRadius(p) && !target.isAlliedTo(p) && !p.isAlliedTo(target)
                && p.hasLineOfSight(target) && HighPowerCombat.unsupportedReason(target).isEmpty();
    }
    private static boolean bladeTarget(ServerPlayer p, LivingEntity target) {
        if(!(target instanceof Mob) || !CanonTargetPolicy.hostile(target) || !target.isAlive() || target.isRemoved()
                || target.level()!=p.level() || target.isAlliedTo(p) || p.isAlliedTo(target) || !p.hasLineOfSight(target)
                || HighPowerCombat.unsupportedReason(target).isPresent() || !chunksLoaded(p.serverLevel(),p.getEyePosition(),p.getEyePosition().add(ClassicRaytrace.direction(p).scale(cn.academy.port.core.RangedSkillRanges.fixed("paper_blade")))))return false;
        var hit=ClassicRaytrace.living(p,cn.academy.port.core.RangedSkillRanges.fixed("paper_blade"),ClipContext.Fluid.NONE);
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity()==target;
    }

    private static boolean blade(ServerPlayer player, ItemStack held, cn.academy.port.core.AbilityProgress state) {
        Vec3 start = player.getEyePosition(), direction = ClassicRaytrace.direction(player);
        if (!finite(start) || !finite(direction) || !chunksLoaded(player.serverLevel(), start, start.add(direction.scale(cn.academy.port.core.RangedSkillRanges.fixed("paper_blade"))))) return false;
        var hit = ClassicRaytrace.living(player,cn.academy.port.core.RangedSkillRanges.fixed("paper_blade"),ClipContext.Fluid.NONE);
        if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof LivingEntity target)
                || !(target instanceof Mob) || !CanonTargetPolicy.hostile(target) || target.isRemoved() || !target.isAlive()
                || target.isAlliedTo(player) || player.isAlliedTo(target) || !player.hasLineOfSight(target)
                || HighPowerCombat.unsupportedReason(target).isPresent()) return false;
        var expected = held.copy();
        final ItemStack[] fence={expected};
        var emission = CanonEmission.begin(player,"paper_blade",HighPowerGrowth.values(player).arcCost(),30,List.of(target),
                current -> materialMatches(player,held,fence[0]) && bladeTarget(player,current));
        if (emission == null) return false;
        // The material and its identity are fenced after the shared ledger commits; no free projectiles.
        if ((player.getMainHandItem()!=held && player.getOffhandItem()!=held) || !ItemStack.matches(held,expected)) return false;
        if (!player.getAbilities().instabuild) { held.shrink(1); player.getInventory().setChanged(); }
        fence[0]=held.copy();
        boolean accepted=emission.attack(target);
        if(accepted)CanonVfx.emit(player,"paper_blade",start,target.getBoundingBox().getCenter(),CanonVfx.Phase.HIT,12);
        Vec3 end=target.getBoundingBox().getCenter(), delta=end.subtract(start);
        int count=Math.max(1,Math.min(24,(int)Math.ceil(delta.length()*2)));
        for(int i=0;i<=count;i++){Vec3 point=start.add(delta.scale((double)i/count));
            player.serverLevel().sendParticles(PaperParticles.INSTANCE,point.x,point.y,point.z,1,.02,.02,.02,0);}
        if (accepted && AbilityStorage.get(player)==state && CATEGORY.equals(state.category) && state.learned("paper_blade")) {
            state.addExperience("paper_blade",.01); AbilityStorage.save(player);
        }
        return true;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(Wing wing:List.copyOf(WINGS.values())) {
            if(wing.owner.server!=event.getServer())continue;
            if(!valid(wing)){finishWing(wing);continue;}
            advance(wing);
        }
        for (Armor armor : List.copyOf(ARMOR.values())) {
            ServerPlayer p=armor.owner;
            if (p.server != event.getServer()) continue;
            if (!valid(armor)) { finishArmor(armor); continue; }
            if (armor.world.getGameTime()%5 == 0)
                armor.world.sendParticles(PaperParticles.INSTANCE,p.getX(),p.getY()+1,p.getZ(),armor.sheets*2,.45,.55,.45,0);
            Vec3 center=p.getBoundingBox().getCenter();
            var arrows=armor.world.getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(ARMOR_RADIUS),a->eligible(p,a,center));
            if (arrows.size()>MAX_CANDIDATES || arrows.isEmpty()) continue;
            arrows.sort(Comparator.comparingDouble(a->a.distanceToSqr(p)));
            AbstractArrow arrow=arrows.getFirst(); Vec3 velocity=arrow.getDeltaMovement();
            if (!valid(armor) || !eligible(p,arrow,center) || !HighPowerCombat.spendShieldFraction(p,SHIELD_SHARE)) continue;
            // Spend one bound sheet and the shield budget only once, even if the subsequent arrow fence fails.
            armor.sheets--; boolean depleted=armor.sheets==0;
            if (!eligible(p,arrow,center) || !arrow.getDeltaMovement().equals(velocity)) {if(depleted)finishArmor(armor);continue;}
            Vec3 radial=arrow.position().subtract(center).normalize().scale(Math.min(3,Math.max(.35,velocity.length())));
            arrow.setDeltaMovement(radial); arrow.hasImpulse=true; arrow.hurtMarked=true;
            CanonVfx.emit(p,"paper_armor",p.position(),arrow.position(),CanonVfx.Phase.HIT,12);
            if(depleted)finishArmor(armor);
            armor.world.sendParticles(PaperParticles.INSTANCE,arrow.getX(),arrow.getY(),arrow.getZ(),8,.2,.2,.2,0);
        }
    }
    private static void finishWing(Wing wing) {
        if(WINGS.remove(wing.owner.getUUID(),wing) && wing.owner.serverLevel()==wing.world)
            CanonVfx.emit(wing.owner,"paper_wing",wing.owner.position(),wing.owner.position(),CanonVfx.Phase.END,8);
    }
    private static void finishArmor(Armor armor) {
        if(ARMOR.remove(armor.owner.getUUID(),armor) && armor.owner.serverLevel()==armor.world)
            CanonVfx.emit(armor.owner,"paper_armor",armor.owner.position(),armor.owner.position(),CanonVfx.Phase.END,8);
    }
    private static boolean valid(Armor armor) {
        ServerPlayer p=armor.owner;
        if (ARMOR.get(p.getUUID())!=armor || armor.sheets<=0 || p.connection==null || p.hasDisconnected()
                || p.server.getPlayerList().getPlayer(p.getUUID())!=p || !p.server.isSameThread() || p.isRemoved()
                || !p.isAlive() || p.isSpectator() || p.serverLevel()!=armor.world || armor.world.getGameTime()>=armor.expires
                || HighPowerCombat.unsupportedReason(p).isPresent()) return false;
        var state=AbilityStorage.get(p);
        return CATEGORY.equals(state.category) && state.activated && state.overloadFine && !state.interfering
                && state.learned("paper_armor") && SkillCatalog.enabled(CATEGORY,"paper_armor");
    }
    private static boolean eligible(ServerPlayer p, AbstractArrow a, Vec3 center) {
        if (a.getType()!=EntityType.ARROW || a.inGround || a.isRemoved() || !a.isAlive()
                || !(a.getOwner() instanceof Mob shooter) || !(shooter instanceof Enemy)
                || shooter.isAlliedTo(p) || p.isAlliedTo(shooter)
                || ElectromasterPrototype.leased(a) || MultiAxisReflectionArrowBridge.leased(a)
                || TrajectoryReturnArrowBridge.leased(a) || ShortInterceptionArrowBridge.leased(a)
                || IronSandLance.leased(a) || MagneticArrowSeparation.leased(a) || MagneticDomainArrows.leased(a)
                || MagneticGroundStep.leased(a) || ElectromasterChargeDecoy.leased(a)) return false;
        Vec3 radial=a.position().subtract(center), velocity=a.getDeltaMovement();
        return finite(radial) && finite(velocity) && radial.lengthSqr()>1e-8 && radial.lengthSqr()<=ARMOR_RADIUS*ARMOR_RADIUS
                && velocity.lengthSqr()>=.001 && velocity.dot(radial)<0;
    }
    private static boolean finite(Vec3 v) { return v!=null && Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
    private static boolean chunksLoaded(ServerLevel level, Vec3 a, Vec3 b) {
        int minX=Math.floorDiv((int)Math.floor(Math.min(a.x,b.x)),16),maxX=Math.floorDiv((int)Math.floor(Math.max(a.x,b.x)),16);
        int minZ=Math.floorDiv((int)Math.floor(Math.min(a.z,b.z)),16),maxZ=Math.floorDiv((int)Math.floor(Math.max(a.z,b.z)),16);
        if((long)maxX-minX>8||(long)maxZ-minZ>8)return false;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(!level.hasChunk(x,z))return false;
        return true;
    }
    private static void remove(ServerPlayer p) {
        var armor=ARMOR.get(p.getUUID());if(armor!=null)finishArmor(armor);
        var wing=WINGS.get(p.getUUID());if(wing!=null)finishWing(wing);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if(e.getEntity() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if(e.getOriginal() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { ARMOR.values().removeIf(a->a.owner.server==e.getServer()); WINGS.values().removeIf(a->a.owner.server==e.getServer()); }
}
