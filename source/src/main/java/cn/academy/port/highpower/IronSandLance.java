package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.IronSandLanceRules;
import cn.academy.port.highpower.core.IronSandLanceRules.Segment;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import java.math.BigDecimal;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW131: one authenticated controller owns three finite server-simulated lances. No recoverable entity/item. */
@EventBusSubscriber(modid="academy")
public final class IronSandLance {
    public static final String ID="iron_sand_lance", LEASE_KEY="academy:iron_sand_lance_lease";
    public static final int CHARGING=0, ATTACK=1, ORBIT=2, ENDED=3;
    public static final int WAITING=0, FLYING=1, HIT=2, DISPERSED=3, DEFLECTED=4, DAMAGED=5, ABSORBED=6, REFUSED=7;
    private static final TagKey<EntityType<?>> METAL=TagKey.create(Registries.ENTITY_TYPE,id("magnetic_projectiles"));
    private static final TagKey<EntityType<?>> PROTECTED=TagKey.create(Registries.ENTITY_TYPE,id("magnetic_pull_protected"));
    private static final TagKey<EntityType<?>> IMMOVABLE=TagKey.create(Registries.ENTITY_TYPE,id("magnetic_pull_immovable"));
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static long nextToken;
    private static final boolean DIAGNOSTICS=Boolean.getBoolean("academy.highpower.ironSandDiagnostics");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG=org.slf4j.LoggerFactory.getLogger("academy.iron_sand_lance_diagnostics");
    private static final Set<Long> DIAGNOSTIC_TOKENS=DIAGNOSTICS?new HashSet<>():null;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record ArrowPosition(Arrow object,int id,Vec3 at,Vec3 velocity,long tick) {}
    public record LancePose(int index,Vec3 tail,Vec3 head,Vec3 previousHead,int status,int hitEntity) {}
    private static final class Lance {
        final int index; Vec3 head,tail,previousHead,direction; int status=WAITING,hitEntity=-1; double distance;
        Lance(int index){this.index=index;}
        LancePose pose(){return new LancePose(index,tail,head,previousHead,status,hitEntity);}
        boolean live(){return status==WAITING||status==FLYING;}
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity; final HighPowerInput.Input input; final int preset;
        final long revision,started; final boolean orbit; final Lance[] lances=new Lance[IronSandLanceRules.LANCES];
        final Set<ServerPlayer> audience=new LinkedHashSet<>();
        final Map<UUID,ArrowPosition> arrows=new HashMap<>();
        IronSandMedium.Snapshot medium; long committed=-1,lastTick,sequence; Vec3 origin,axis,center;
        int emitted,deflected,examined,diagnostics,arrowDiagnostics; BigDecimal damage; boolean awarded;
        Context(ServerPlayer p,HighPowerInput.Input input,boolean orbit,IronSandMedium.Snapshot medium){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));
            this.input=input;this.orbit=orbit;this.medium=medium;preset=identity.state().presets.current();revision=identity.state().presets.revision();
            started=now(p);lastTick=started-1;origin=p.getEyePosition();axis=p.getLookAngle().normalize();center=orbitCenter(p);
            for(int i=0;i<lances.length;i++)lances[i]=new Lance(i);
            assembly(this);
        }
        boolean paid(){return committed>=0;}
    }
    private IronSandLance() {}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("academy",path);}

    /** Both modes are authorized; the configured crouch key chooses once in the client begin packet. */
    public static boolean begin(ServerPlayer p,String wire,boolean orbit){
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark old=INPUTS.get(p.getUUID());
        if(old!=null&&old.epoch==input.epoch()&&old.input>=input.input())return false;
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        // A newer press cannot orphan or replace a paid volley. The existing owner must be aborted first.
        if(owned(p)!=null||!canAct(p)||!IronSandLanceRules.clock(now(p))||nextToken==Long.MAX_VALUE
                ||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                ||!AbilityStorage.get(p).canUse(ID)){feedback(p,"cannot_start");return false;}
        IronSandMedium.Snapshot medium=IronSandMedium.capture(p);if(medium==null){feedback(p,"no_medium");return false;}
        Context c=new Context(p,input,orbit,medium);CONTEXTS.put(p.getUUID(),c);diagnostic(c,"charge","mode="+(orbit?"orbit":"attack")+" sand="+p.getOffhandItem().getCount()+" unpaid=true");send(c,CHARGING);return true;
    }
    public static boolean begin(ServerPlayer p,String wire){return begin(p,wire,false);}
    /** The identical one-use begin identity is mandatory. Release cannot change captured mode. */
    public static boolean release(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context c=owned(p);
        if(c==null||!c.input.equals(input)||c.paid())return false;
        if(!valid(c)||!IronSandLanceRules.charged(c.started,now(p))||!IronSandMedium.matches(p,c.medium)
                ||!AbilityStorage.get(p).canUse(ID)){feedback(p,"charge_cancelled");finish(c);return false;}
        Vec3 look=p.getLookAngle().normalize(),muzzle=p.getEyePosition().add(look.scale(.2));
        Vec3 center=orbitCenter(p);
        if(!clearLine(p,p.getEyePosition(),muzzle)||!permittedVolume(p,new AABB(muzzle,muzzle).inflate(IronSandLanceRules.RADIUS))){feedback(p,"blocked");finish(c);return false;}
        if(c.orbit){
            for(int i=0;i<c.lances.length;i++){
                Segment segment=IronSandLanceRules.orbit(vector(center),i,0);
                if(!orbitPermitted(p,segment,segment)){feedback(p,"blocked");finish(c);return false;}
            }
        }else for(int i=0;i<c.lances.length;i++){
            Vec3 direction=vec(IronSandLanceRules.direction(vector(look),i));
            if(!permittedVolume(p,new AABB(muzzle,muzzle.add(direction.scale(IronSandLanceRules.SPEED))).inflate(IronSandLanceRules.RADIUS))){feedback(p,"blocked");finish(c);return false;}
        }
        var growth=HighPowerGrowth.values(p);int stockBefore=p.getOffhandItem().getCount();double cpBefore=ElectromasterPrototype.prototypeCp(p);
        if(!IronSandMedium.commit(p,c.medium,ID,IronSandLanceRules.cpCost(growth.position()),IronSandLanceRules.cooldownTicks(growth.position()))){feedback(p,"cannot_start");finish(c);return false;}
        // The irreversible inventory+CP transaction is complete before the first emitted lance.
        c.damage=IronSandLanceRules.damage(growth.damage());c.medium=null;c.committed=now(p);c.lastTick=c.committed-1;c.origin=muzzle;c.axis=look;c.center=center;
        if(!c.orbit)HighPowerNaturalRecovery.offensiveAction(p);
        diagnostic(c,"commit","mode="+(c.orbit?"orbit":"attack")+" tick="+c.committed+" sand_before="+stockBefore+" sand_after="+p.getOffhandItem().getCount()+" cp_before="+cpBefore+" cp_after="+ElectromasterPrototype.prototypeCp(p)+" damage="+c.damage+" single_commit=true");
        for(Lance lance:c.lances){
            lance.direction=vec(IronSandLanceRules.direction(vector(look),lance.index));
            if(c.orbit){Segment s=IronSandLanceRules.orbit(vector(center),lance.index,0);lance.tail=vec(s.tail());lance.head=vec(s.head());lance.status=FLYING;}
            else {lance.head=muzzle;lance.tail=muzzle.subtract(lance.direction.scale(IronSandLanceRules.LENGTH));lance.status=WAITING;}
            lance.previousHead=lance.head;
        }
        if(c.orbit)c.emitted=IronSandLanceRules.LANCES;
        tick(p);return true;
    }
    public static boolean abort(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark old=INPUTS.get(p.getUUID());if(old==null||old.epoch!=input.epoch()||old.input<input.input())INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        Context c=owned(p);if(c==null||c.input.epoch()!=input.epoch()||c.input.slot()!=input.slot()||c.input.input()>input.input())return false;
        finish(c);return true;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c);}
    public static void remove(ServerPlayer p){if(!thread(p))return;abort(p);INPUTS.remove(p.getUUID());}
    public static void clear(){CONTEXTS.clear();INPUTS.clear();}
    public static boolean active(ServerPlayer p){return owned(p)!=null;}
    public static boolean paid(ServerPlayer p){Context c=owned(p);return c!=null&&c.paid();}
    public static int emittedCount(ServerPlayer p){Context c=owned(p);return c==null?0:c.emitted;}
    public static int deflectedCount(ServerPlayer p){Context c=owned(p);return c==null?0:c.deflected;}
    public static int scannedLastTick(ServerPlayer p){Context c=owned(p);return c==null?0:c.examined;}
    public static List<LancePose> serverPose(ServerPlayer p){Context c=owned(p);return c==null?List.of():Arrays.stream(c.lances).map(Lance::pose).toList();}

    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null)return;long tick=now(p);
        if(!valid(c)||!IronSandLanceRules.clock(tick)||tick<c.started){finish(c);return;}
        if(!c.paid()){
            if(IronSandLanceRules.chargeExpired(c.started,tick)||!IronSandMedium.matches(p,c.medium)||!AbilityStorage.get(p).canUse(ID)){finish(c);return;}
            if(tick<=c.lastTick)return;c.lastTick=tick;assembly(c);send(c,CHARGING);return;
        }
        if(c.orbit&&IronSandLanceRules.orbitExpired(c.committed,tick)||!c.orbit&&tick-c.committed>28){finish(c);return;}
        if(tick<=c.lastTick)return;
        // Missed simulation steps never fast-forward through a wall, target or unloaded area.
        if(tick-c.lastTick>1){finish(c);return;}
        c.lastTick=tick;
        for(Lance lance:c.lances)if(lance.status>=HIT){lance.status=DISPERSED;lance.hitEntity=-1;}
        if(c.orbit)orbit(c,tick);else attack(c,tick);
        if(CONTEXTS.get(p.getUUID())!=c)return;
        send(c,c.orbit?ORBIT:ATTACK);
        if(Arrays.stream(c.lances).noneMatch(Lance::live)&&Arrays.stream(c.lances).noneMatch(l->l.status==HIT||l.status>=DEFLECTED))finish(c);
    }
    private static void assembly(Context c){
        ServerPlayer p=c.identity.owner();Vec3 look=p.getLookAngle().normalize();Vec3 right=new Vec3(look.z,0,-look.x);
        if(right.lengthSqr()<1e-8)right=new Vec3(1,0,0);else right=right.normalize();
        c.origin=p.getEyePosition();
        for(Lance lance:c.lances){Vec3 head=c.origin.add(look.scale(.7)).add(right.scale((lance.index-1)*.24)).add(0,-.4,0);
            lance.previousHead=lance.head==null?head:lance.head;lance.head=head;lance.tail=head.subtract(look.scale(IronSandLanceRules.LENGTH));}
    }
    private static void attack(Context c,long tick){
        ServerPlayer p=c.identity.owner();
        for(Lance lance:c.lances){
            if(!lance.live()||tick-c.committed<lance.index)continue;
            if(lance.status==WAITING){lance.status=FLYING;c.emitted++;diagnostic(c,"launch","index="+lance.index+" tick="+tick+" origin="+lance.head+" axis="+lance.direction);}
            Vec3 start=lance.head;var step=IronSandLanceRules.step(vector(start),vector(lance.direction),IronSandLanceRules.RANGE-lance.distance);
            if(step==null){lance.status=DISPERSED;continue;}Vec3 end=vec(step);
            lance.previousHead=start;
            AABB swept=new AABB(start,end).inflate(IronSandLanceRules.RADIUS);
            if(!permittedVolume(p,swept)){lance.status=DISPERSED;continue;}
            Vec3 stop=end;boolean block=false;double nearest=start.distanceToSqr(end);
            for(var shape:p.serverLevel().getBlockCollisions(p,swept))for(AABB box:shape.toAabbs()){
                AABB expanded=box.inflate(IronSandLanceRules.RADIUS);Optional<Vec3> hit=expanded.contains(start)?Optional.of(start):expanded.clip(start,end);
                if(hit.isPresent()&&start.distanceToSqr(hit.get())<=nearest){nearest=start.distanceToSqr(hit.get());stop=hit.get();block=true;}
            }
            List<Entity> candidates=new ArrayList<>();
            p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),swept,e->e!=p&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()&&e.canBeHitByProjectile(),candidates,IronSandLanceRules.SCAN_LIMIT+1);
            if(candidates.size()>IronSandLanceRules.SCAN_LIMIT){lance.status=DISPERSED;continue;}
            Entity target=null;
            for(Entity entity:candidates){
                AABB bounds=entity.getBoundingBox().inflate(IronSandLanceRules.RADIUS);
                Optional<Vec3> hit=bounds.contains(start)?Optional.of(start):bounds.clip(start,end);
                if(hit.isPresent()&&(start.distanceToSqr(hit.get())<nearest||!block&&target==null&&start.distanceToSqr(hit.get())==nearest)){
                    nearest=start.distanceToSqr(hit.get());stop=hit.get();target=entity;block=false;
                }
            }
            lance.head=stop;lance.tail=stop.subtract(lance.direction.scale(IronSandLanceRules.LENGTH));lance.distance+=start.distanceTo(stop);
            if(block||target!=null){
                lance.status=HIT;lance.hitEntity=target==null?-1:target.getId();
                if(target instanceof LivingEntity living){
                    lance.status=REFUSED;
                    if(valid(c)&&harmPermitted(p,living)&&c.damage!=null){
                        BigDecimal health=health(living),shield=HighPowerCombat.shield(living);float absorption=living.getAbsorptionAmount();
                        boolean accepted=HighPowerCombat.attackIronSandLance(p,living,c.damage,start);
                        if(accepted&&health(living).compareTo(health)<0)lance.status=DAMAGED;
                        else if(accepted&&(HighPowerCombat.shield(living).compareTo(shield)<0||living.getAbsorptionAmount()<absorption))lance.status=ABSORBED;
                        diagnostic(c,"damage","index="+lance.index+" target="+living.getId()+" source="+start+" impact="+stop+" accepted="+accepted+" status="+lance.status+" exact_health_delta="+health.subtract(health(living))+" exact_shield_delta="+shield.subtract(HighPowerCombat.shield(living)));
                        if(!c.awarded&&(lance.status==DAMAGED||lance.status==ABSORBED)&&valid(c)){
                            c.awarded=true;c.identity.state().addExperience(ID,.005);AbilityStorage.save(p);
                        }
                    }
                }
                diagnostic(c,"contact","index="+lance.index+" type="+(target==null?"solid":"entity")+" target="+lance.hitEntity+" source="+start+" impact="+stop+" traveled="+lance.distance+" status="+lance.status);
                // Hooks can change owner eligibility or remove the controller. Never emit another lance then.
                if(CONTEXTS.get(p.getUUID())!=c||!valid(c)){finish(c);return;}
            }else if(lance.distance>=IronSandLanceRules.RANGE-IronSandLanceRules.RANGE_EPSILON)lance.status=DISPERSED;
        }
    }
    private static BigDecimal health(LivingEntity target){
        if(HighPowerCombat.enabled(target)){
            try{return new BigDecimal(HighPowerCombat.healthText(target));}catch(NumberFormatException ignored){}
        }
        return BigDecimal.valueOf(target.getHealth());
    }
    private static boolean harmPermitted(ServerPlayer p,LivingEntity target){
        ServerLevel world=p.serverLevel();
        if(target==p||target.level()!=world||!target.isAlive()||target.isRemoved()||target.isSpectator()
                ||world.getEntity(target.getUUID())!=target||world.getEntity(target.getId())!=target||!HighPowerCombat.supported(target)
                ||HighPowerCombat.unsupportedReason(target).isPresent()||target.isInvulnerable()||p.isAlliedTo(target)||target.isAlliedTo(p)
                ||!permittedVolume(p,target.getBoundingBox()))return false;
        return !(target instanceof Player)||target.getClass()==ServerPlayer.class&&target instanceof ServerPlayer other
                &&other.connection!=null&&!other.hasDisconnected()&&!other.getAbilities().invulnerable
                &&AcademyConfig.ATTACK_PLAYERS.get()&&p.server.isPvpAllowed()&&p.canHarmPlayer(other)&&other.canHarmPlayer(p);
    }
    private static void orbit(Context c,long tick){
        ServerPlayer p=c.identity.owner();Vec3 center=orbitCenter(p);
        if(center.distanceToSqr(c.center)>1){finish(c);return;}
        Segment[] old=new Segment[c.lances.length],next=new Segment[c.lances.length];
        for(Lance lance:c.lances){
            if(!lance.live())continue;
            old[lance.index]=new Segment(vector(lance.tail),vector(lance.head));next[lance.index]=IronSandLanceRules.orbit(vector(center),lance.index,tick-c.committed);
            lance.previousHead=lance.head;
            if(!orbitPermitted(p,old[lance.index],next[lance.index])){lance.status=DISPERSED;continue;}
            lance.tail=vec(next[lance.index].tail());lance.head=vec(next[lance.index].head());
        }
        c.center=center;c.origin=center;
        List<Arrow> arrows=new ArrayList<>();double reach=IronSandLanceRules.ORBIT_RADIUS+IronSandLanceRules.LENGTH+IronSandLanceRules.MAX_ARROW_SPEED;
        AABB bounds=new AABB(center,center).inflate(reach);
        // Native live-index query never requests a chunk. A hard cap bounds all candidate processing.
        p.serverLevel().getEntities(EntityTypeTest.forClass(Arrow.class),bounds,a->true,arrows,IronSandLanceRules.SCAN_LIMIT);
        c.examined=arrows.size();arrows.sort(Comparator.<Arrow>comparingDouble(a->a.position().distanceToSqr(center)).thenComparing(Entity::getUUID));
        Map<UUID,ArrowPosition> observed=new HashMap<>();
        for(Arrow arrow:arrows){
            Vec3 at=arrow.position();if(!finite(at))continue;
            ArrowPosition previous=c.arrows.get(arrow.getUUID());
            observed.put(arrow.getUUID(),new ArrowPosition(arrow,arrow.getId(),at,arrow.getDeltaMovement(),tick));
            boolean eligible=arrowEligible(p,arrow);
            diagnosticArrow(c,arrow,previous,old,next,tick,eligible);
            if(!eligible)continue;
            Vec3 before=at;
            if(previous!=null&&previous.object==arrow&&previous.id==arrow.getId()&&previous.tick==tick-1){
                if(previous.at.distanceToSqr(at)>IronSandLanceRules.MAX_ARROW_SPEED*IronSandLanceRules.MAX_ARROW_SPEED+1e-8
                        ||previous.at.add(previous.velocity).distanceToSqr(at)>1e-12||!clearLine(p,previous.at,at))continue;
                before=previous.at;
            }
            for(Lance lance:c.lances){
                if(lance.status!=FLYING||old[lance.index]==null||next[lance.index]==null)continue;
                // Without a consecutive observed native position, only the current physical capsule may contact.
                Segment from=previous!=null&&previous.object==arrow&&previous.tick==tick-1?old[lance.index]:next[lance.index];
                if(!Double.isFinite(IronSandLanceRules.contact(vector(before),vector(at),from,next[lance.index])))continue;
                LivingEntity shooter=(LivingEntity)arrow.getOwner();
                var changed=IronSandLanceRules.deflect(vector(arrow.getDeltaMovement()),vector(at),vector(center),vector(shooter.position()),arrow.getUUID());
                if(changed==null||!movementPermitted(p,arrow,vec(changed))||!valid(c)||!arrowEligible(p,arrow))continue;
                CompoundTag lease=new CompoundTag();lease.putInt("version",1);lease.putUUID("arrow",arrow.getUUID());lease.putUUID("owner",p.getUUID());lease.putLong("since",tick);lease.putLong("until",tick+IronSandLanceRules.ORBIT_TICKS);
                arrow.getPersistentData().put(LEASE_KEY,lease);
                diagnostic(c,"arrow_contact","index="+lance.index+" arrow="+arrow.getId()+" actual_previous="+before+" actual_current="+at+" velocity_before="+arrow.getDeltaMovement()+" velocity_after="+vec(changed)+" shooter="+net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(shooter.getType()));
                arrow.setDeltaMovement(vec(changed));arrow.hasImpulse=true;arrow.hurtMarked=true;
                lance.status=DEFLECTED;lance.hitEntity=arrow.getId();c.deflected++;break;
            }
        }
        c.arrows.clear();c.arrows.putAll(observed);
    }
    /** Persistent reciprocal guard. Corrupt/future records fail closed; an expired record remains our one-shot tombstone. */
    public static boolean leased(AbstractArrow arrow){
        if(arrow==null||!(arrow.level() instanceof ServerLevel world)||!world.getServer().isSameThread())return true;
        CompoundTag data=arrow.getPersistentData();if(!data.contains(LEASE_KEY))return false;
        if(!data.contains(LEASE_KEY,Tag.TAG_COMPOUND))return true;
        long tick=world.getServer().overworld().getGameTime();CompoundTag lease=data.getCompound(LEASE_KEY);
        return !validLease(lease,arrow.getUUID(),tick)||tick<lease.getLong("until");
    }
    private static boolean validLease(CompoundTag tag,UUID arrow,long tick){
        return IronSandLanceRules.clock(tick)&&tag.getAllKeys().equals(Set.of("version","arrow","owner","since","until"))
                &&tag.contains("version",Tag.TAG_INT)&&tag.getInt("version")==1&&tag.hasUUID("arrow")&&arrow.equals(tag.getUUID("arrow"))&&tag.hasUUID("owner")
                &&tag.contains("since",Tag.TAG_LONG)&&tag.contains("until",Tag.TAG_LONG)&&IronSandLanceRules.clock(tag.getLong("since"))
                &&tag.getLong("since")<=tick&&tag.getLong("until")==tag.getLong("since")+IronSandLanceRules.ORBIT_TICKS;
    }
    private static boolean arrowEligible(ServerPlayer p,Arrow arrow){
        ServerLevel world=p.serverLevel();
        if(arrow.getClass()!=Arrow.class||arrow.getType()!=EntityType.ARROW||!arrow.getType().is(METAL)||arrow.level()!=world
                ||arrow.isRemoved()||!arrow.isAlive()||world.getEntity(arrow.getUUID())!=arrow||world.getEntity(arrow.getId())!=arrow
                ||arrow.inGround||arrow.isNoPhysics()||arrow.noPhysics||arrow.isNoGravity()||arrow.isPassenger()||arrow.isVehicle()
                ||arrow.isInvulnerable()||arrow.isOnFire()||arrow.isCritArrow()||arrow.getPierceLevel()!=0
                ||arrow.getType().is(PROTECTED)||arrow.getType().is(IMMOVABLE)||arrow.getPersistentData().getBoolean("academy:protected")
                ||arrow.getPersistentData().getBoolean("academy:immovable")||arrow.getPersistentData().contains(LEASE_KEY)
                ||MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow)||ElectromasterPrototype.leased(arrow)||ElectromasterChargeDecoy.leased(arrow)||MagneticGroundStep.leased(arrow)||MagneticArrowSeparation.leased(arrow)||MagneticDomainArrows.leased(arrow)
                ||!finite(arrow.position())||!finite(arrow.getDeltaMovement())||!permittedVolume(p,arrow.getBoundingBox())
                ||arrow.getDeltaMovement().lengthSqr()>IronSandLanceRules.MAX_ARROW_SPEED*IronSandLanceRules.MAX_ARROW_SPEED
                ||!(arrow.getOwner() instanceof LivingEntity shooter)||!enemy(p,shooter)||!clearLine(p,p.getEyePosition(),arrow.position()))return false;
        var stack=arrow.getPickupItemStackOrigin();var weapon=arrow.getWeaponItem();
        return stack.is(Items.ARROW)&&stack.getCount()==1&&stack.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).equals(PotionContents.EMPTY)
                &&stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty()
                &&(weapon==null||!weapon.isEmpty()&&(weapon.is(Items.BOW)||weapon.is(Items.CROSSBOW))&&weapon.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty())
                &&arrow.getGravity()==.05&&!arrow.isInWaterOrBubble()&&!arrow.isInLava()&&world.getFluidState(arrow.blockPosition()).isEmpty();
    }
    private static boolean enemy(ServerPlayer p,LivingEntity shooter){
        if(!harmPermitted(p,shooter)||shooter.isPassenger()||shooter instanceof ServerPlayer other&&other.getAbilities().flying)return false;
        return shooter instanceof ServerPlayer||HighPowerCombat.hostileTarget(shooter)||shooter instanceof Mob mob&&mob.getTarget()==p;
    }
    private static boolean movementPermitted(ServerPlayer p,Arrow arrow,Vec3 changed){
        Vec3 velocity=arrow.getDeltaMovement(),at=arrow.position();
        if(!finite(changed)||Double.doubleToRawLongBits(changed.y)!=Double.doubleToRawLongBits(velocity.y)||changed.lengthSqr()>velocity.lengthSqr()
                ||changed.horizontalDistanceSqr()>velocity.horizontalDistanceSqr())return false;
        AABB swept=arrow.getBoundingBox().expandTowards(velocity).minmax(arrow.getBoundingBox().expandTowards(changed));
        if(!permittedVolume(p,swept))return false;
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(swept.minX,swept.minY,swept.minZ),BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ)))
            if(!p.serverLevel().getFluidState(pos).isEmpty())return false;
        return clearLine(p,at,at.add(velocity))&&clearLine(p,at,at.add(changed));
    }
    private static boolean orbitPermitted(ServerPlayer p,Segment old,Segment next){
        if(old==null||next==null)return false;
        AABB swept=new AABB(vec(old.tail()),vec(old.head())).minmax(new AABB(vec(next.tail()),vec(next.head()))).inflate(IronSandLanceRules.RADIUS);
        if(!permittedVolume(p,swept))return false;
        // Bounding the entire swept capsule hull is intentionally conservative near solid corners.
        return !p.serverLevel().getBlockCollisions(p,swept).iterator().hasNext();
    }
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var state=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&ElectromasterPrototype.ready(p)&&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable
                &&state.activated&&state.overloadFine&&!state.interfering&&state.learned(ID)&&state.category.equals("electromaster")
                &&state.level>=3&&state.level<=5&&ElectromasterPrototype.effectiveLevel(p)>=3&&SkillCatalog.enabled("electromaster",ID)
                &&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)&&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()
                &&finite(p.position())&&finite(p.getDeltaMovement())&&finite(p.getLookAngle())&&p.getLookAngle().lengthSqr()>.5
                &&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&permittedVolume(p,p.getBoundingBox());
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();return c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canAct(p)
                &&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean permittedVolume(ServerPlayer p,AABB box){
        ServerLevel world=p.serverLevel();
        if(!finite(new Vec3(box.minX,box.minY,box.minZ))||!finite(new Vec3(box.maxX,box.maxY,box.maxZ))
                ||box.getXsize()>16||box.getYsize()>16||box.getZsize()>16||box.minY<world.getMinBuildHeight()||box.maxY>=world.getMaxBuildHeight()
                ||!world.getWorldBorder().isWithinBounds(box))return false;
        for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)
            for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)if(!world.hasChunk(x,z))return false;
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
            if(!world.mayInteract(p,pos))return false;
        return true;
    }
    private static boolean clearLine(ServerPlayer p,Vec3 start,Vec3 end){
        return permittedVolume(p,new AABB(start,end))&&p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    private static Vec3 orbitCenter(ServerPlayer p){return p.getEyePosition().add(0,-.4,0);}
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static long now(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static IronSandLanceRules.Vector vector(Vec3 v){return new IronSandLanceRules.Vector(v.x,v.y,v.z);}
    private static Vec3 vec(IronSandLanceRules.Vector v){return new Vec3(v.x(),v.y(),v.z());}
    private static void feedback(ServerPlayer p,String key){if(p.connection!=null&&!p.hasDisconnected())p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("academy.highpower.iron_lance."+key));}
    /** Default-off observer only: four near-ring arrow samples at most, including explicit rejection/minima. */
    private static void diagnosticArrow(Context c,Arrow arrow,ArrowPosition previous,Segment[] old,Segment[] next,long tick,boolean eligible){
        if(!DIAGNOSTICS||c.arrowDiagnostics>=4||arrow.inGround||arrow.isNoGravity()||arrow.getDeltaMovement().lengthSqr()<.01
                ||arrow.position().distanceToSqr(c.center)>9)return;
        c.arrowDiagnostics++;
        boolean consecutive=previous!=null&&previous.object==arrow&&previous.id==arrow.getId()&&previous.tick==tick-1;
        Vec3 at=arrow.position(),before=consecutive?previous.at:at;
        boolean nativeSweep=consecutive&&previous.at.add(previous.velocity).distanceToSqr(at)<=1e-12
                &&before.distanceToSqr(at)<=IronSandLanceRules.MAX_ARROW_SPEED*IronSandLanceRules.MAX_ARROW_SPEED+1e-8&&clearLine(c.identity.owner(),before,at);
        StringBuilder capsules=new StringBuilder();
        for(Lance lance:c.lances){
            if(capsules.length()>0)capsules.append(';');capsules.append(lance.index).append(':').append(lance.status);
            if(lance.status!=FLYING||old[lance.index]==null||next[lance.index]==null)continue;
            Segment from=consecutive?old[lance.index]:next[lance.index];
            double min=IronSandLanceRules.diagnosticMinimumDistance(vector(before),vector(at),from,next[lance.index]);
            double hit=IronSandLanceRules.contact(vector(before),vector(at),from,next[lance.index]);
            capsules.append("/min=").append(min).append("/contact=").append(Double.isFinite(hit));
        }
        Entity owner=arrow.getOwner();
        diagnostic(c,"arrow_sample","sample="+c.arrowDiagnostics+" age="+(tick-c.committed)+" arrow="+arrow.getId()
                +" previous="+before+" current="+at+" relative_y="+(at.y-c.center.y)+" orbit_y="+c.center.y
                +" velocity="+arrow.getDeltaMovement()+" owner="+(owner==null?"none":net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(owner.getType())+"#"+owner.getId())
                +" eligible="+eligible+" reason="+(eligible?"eligible":diagnosticRejection(c.identity.owner(),arrow))
                +" consecutive="+consecutive+" native_sweep="+nativeSweep+" capsules="+capsules);
    }
    /** Mirrors guard groups only to explain a refused sample; it never grants eligibility or changes state. */
    private static String diagnosticRejection(ServerPlayer p,Arrow arrow){
        ServerLevel world=p.serverLevel();
        if(arrow.getClass()!=Arrow.class||arrow.getType()!=EntityType.ARROW||!arrow.getType().is(METAL))return "type_or_magnetic_tag";
        if(arrow.level()!=world||arrow.isRemoved()||!arrow.isAlive()||world.getEntity(arrow.getUUID())!=arrow||world.getEntity(arrow.getId())!=arrow)return "live_identity";
        if(arrow.inGround||arrow.isNoPhysics()||arrow.noPhysics||arrow.isNoGravity()||arrow.isPassenger()||arrow.isVehicle())return "native_physics";
        if(arrow.isInvulnerable()||arrow.isOnFire()||arrow.isCritArrow()||arrow.getPierceLevel()!=0)return "damage_metadata";
        if(arrow.getType().is(PROTECTED)||arrow.getType().is(IMMOVABLE)||arrow.getPersistentData().getBoolean("academy:protected")||arrow.getPersistentData().getBoolean("academy:immovable"))return "protected";
        if(arrow.getPersistentData().contains(LEASE_KEY)||MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow)||ElectromasterPrototype.leased(arrow)||ElectromasterChargeDecoy.leased(arrow)||MagneticGroundStep.leased(arrow)||MagneticArrowSeparation.leased(arrow)||MagneticDomainArrows.leased(arrow))return "writer_lease";
        if(!finite(arrow.position())||!finite(arrow.getDeltaMovement())||arrow.getDeltaMovement().lengthSqr()>IronSandLanceRules.MAX_ARROW_SPEED*IronSandLanceRules.MAX_ARROW_SPEED)return "position_or_speed";
        if(!permittedVolume(p,arrow.getBoundingBox()))return "arrow_world_permission";
        if(!(arrow.getOwner() instanceof LivingEntity shooter))return "nonliving_or_missing_owner";
        if(!enemy(p,shooter))return "owner_enemy_permission";
        if(!clearLine(p,p.getEyePosition(),arrow.position()))return "owner_sightline";
        var stack=arrow.getPickupItemStackOrigin();var weapon=arrow.getWeaponItem();
        if(!stack.is(Items.ARROW)||stack.getCount()!=1||!stack.getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).equals(PotionContents.EMPTY))return "pickup_or_potion";
        if(!stack.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty()||weapon!=null&&(weapon.isEmpty()||!weapon.is(Items.BOW)&&!weapon.is(Items.CROSSBOW)||!weapon.getOrDefault(DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty()))return "weapon_or_enchantment";
        if(arrow.getGravity()!=.05||arrow.isInWaterOrBubble()||arrow.isInLava()||!world.getFluidState(arrow.blockPosition()).isEmpty())return "gravity_or_fluid";
        return "other_guard";
    }
    private static void diagnostic(Context c,String stage,String detail){
        if(!DIAGNOSTICS||c.diagnostics>=16||!DIAGNOSTIC_TOKENS.contains(c.identity.token())&&DIAGNOSTIC_TOKENS.size()>=128)return;
        DIAGNOSTIC_TOKENS.add(c.identity.token());c.diagnostics++;
        DIAGNOSTIC_LOG.info("NEW131_LANCE token={} stage={} {}",c.identity.token(),stage,detail);
    }
    private static void finish(Context c){
        if(!CONTEXTS.remove(c.identity.owner().getUUID(),c))return;
        diagnostic(c,"end","paid="+c.paid()+" tick="+now(c.identity.owner())+" emitted="+c.emitted+" deflected="+c.deflected+" remaining_sand="+c.identity.owner().getOffhandItem().getCount());
        c.medium=null;c.arrows.clear();for(Lance lance:c.lances){lance.status=DISPERSED;lance.hitEntity=-1;}
        send(c,ENDED);c.audience.clear();
    }
    private static void send(Context c,int phase){
        ServerPlayer owner=c.identity.owner();
        for(ServerPlayer viewer:c.identity.level().players())if(viewer.position().distanceToSqr(c.origin)<=80*80)c.audience.add(viewer);
        c.audience.add(owner);
        Visual packet=new Visual(owner.getId(),owner.getUUID(),c.input.input(),c.identity.token(),c.identity.epoch(),++c.sequence,phase,c.orbit,
                (int)Math.clamp(now(owner)-c.started,0,IronSandLanceRules.CHARGE_TICKS),c.origin,phase==ENDED?List.of():Arrays.stream(c.lances).map(Lance::pose).toList());
        for(ServerPlayer viewer:c.audience)if(viewer.connection!=null&&!viewer.hasDisconnected()&&viewer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(viewer,packet);
    }
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    private static List<LancePose> readLances(RegistryFriendlyByteBuf b){
        int count=b.readVarInt();if(count<0||count>IronSandLanceRules.LANCES)throw new DecoderException("NEW131 lance visual bound exceeded");
        List<LancePose> lances=new ArrayList<>(count);for(int i=0;i<count;i++)lances.add(new LancePose(b.readUnsignedByte(),readVec(b),readVec(b),readVec(b),b.readUnsignedByte(),b.readInt()));return lances;
    }
    public record Visual(int entity,UUID owner,long input,long token,long epoch,long sequence,int phase,boolean orbit,int chargeTicks,Vec3 origin,List<LancePose> lances) implements CustomPacketPayload {
        public Visual{lances=List.copyOf(lances);if(lances.size()>IronSandLanceRules.LANCES)throw new IllegalArgumentException("NEW131 lance visual bound exceeded");}
        public static final Type<Visual> TYPE=new Type<>(id("hp_iron_sand_lance"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.entity);b.writeUUID(v.owner);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.epoch);b.writeLong(v.sequence);b.writeByte(v.phase);b.writeBoolean(v.orbit);b.writeByte(v.chargeTicks);writeVec(b,v.origin);
            b.writeVarInt(v.lances.size());for(LancePose lance:v.lances){b.writeByte(lance.index);writeVec(b,lance.tail);writeVec(b,lance.head);writeVec(b,lance.previousHead);b.writeByte(lance.status);b.writeInt(lance.hitEntity);}
        },b->new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readUnsignedByte(),b.readBoolean(),b.readUnsignedByte(),readVec(b),readLances(b)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.level().getServer()==e.getServer())tick(c.identity.owner());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
