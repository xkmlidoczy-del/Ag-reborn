/* AcademyCraft 1.0.7 MagMovement/MovementContext adaptation. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.MagneticRules;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-owned magnetic attraction. Client packets cannot choose targets, velocities or EXP. */
public final class MagMovement {
    public static final String ID=MagneticRules.MOVEMENT;
    private static final Map<UUID,Hold> HOLDS=new HashMap<>();
    private static final LegacySingleKeyProtocol.NonceLedger INPUTS=new LegacySingleKeyProtocol.NonceLedger();
    private static long nextToken;
    private MagMovement() {}

    public static boolean canAcceptInput(ServerPlayer p,long input){return LegacySingleKeyServerIdentity.canClaim(INPUTS,p,input);}
    public static boolean start(ServerPlayer p) {return startInternal(p,0);}
    public static boolean start(ServerPlayer p,long input) {return input>0&&startInternal(p,input);}
    private static boolean startInternal(ServerPlayer p,long input) {if(cn.academy.port.AbilityConsumption.busy(p))return false;
        if(!server(p)||!canAct(p,AbilityStorage.get(p))||input>0&&!LegacySingleKeyServerIdentity.claim(INPUTS,p,input))return false;
        var old=HOLDS.get(p.getUUID());if(old!=null){if(valid(p,old))return false;abort(old.identity.owner());if(HOLDS.containsKey(p.getUUID()))return false;}
        var s=AbilityStorage.get(p);var cost=MagneticRules.movement(s.exp(ID));
        // Source consumes overload before validating the magnetic target.
        if(!s.consumeSkill(ID,0,cost.overload(),p.getAbilities().instabuild))return false;
        var hit=ClassicRaytrace.living(p,MagneticRules.MOVEMENT_RANGE,ClipContext.Fluid.NONE);
        Entity target=null;Vec3 point=null;
        if(hit instanceof BlockHitResult b&&hit.getType()==HitResult.Type.BLOCK&&ClassicMetalTargets.movement(p.level().getBlockState(b.getBlockPos()),s.exp(ID)))point=b.getLocation();
        else if(hit instanceof EntityHitResult e&&ClassicMetalTargets.entity(e.getEntity())){target=e.getEntity();point=target.getEyePosition();}
        if(point==null||!finite(point)||!s.overloadFine) {
            // Original server termination awards the floor even for an invalid initial target.
            s.addExperience(ID,MagneticRules.movementExperience(0));cn.academy.port.achievements.ClassicAchievements.trigger(p,"electromaster.mag_movement");p.fallDistance=0;AbilityStorage.save(p);
            if(input>0){var ended=new Hold(new LegacySingleKeyServerIdentity(p,input,++nextToken,s),cost,p.position(),null,p.position(),s.overload,audience(p));send(p,ended,"mag_movement_end");}return true;
        }
        var hold=new Hold(new LegacySingleKeyServerIdentity(p,input,++nextToken,s),cost,p.position(),target,point,s.overload,audience(p));
        HOLDS.put(p.getUUID(),hold);send(p,hold,"mag_movement_start");return true;
    }
    public static void tick(ServerPlayer p) {
        if(!server(p))return;var h=HOLDS.get(p.getUUID());if(h==null)return;if(h.identity.owner()!=p)return;
        if(!valid(p,h)){abort(p);return;}
        long now=h.level.getGameTime();if(now<=h.lastTick)return;h.lastTick=now;
        if(h.target!=null){if(!h.target.isAlive()||h.target.isRemoved()||h.target.level()!=h.level){abort(p);return;}h.point=h.target.getEyePosition();}
        if(!finite(h.point)||!h.state.consumeSkill(ID,h.cost.cpPerTick(),0,p.getAbilities().instabuild,()->HOLDS.get(p.getUUID())==h&&(h.target==null||h.target.isAlive()&&!h.target.isRemoved()&&h.target.level()==h.level))){abort(p);return;}
        // Original client locks overload at its start value while pulling. Authoritative here.
        if(!p.getAbilities().instabuild)h.state.overload=Math.max(h.keepOverload,h.state.overload);
        var motion=MagneticRules.movementVelocity(vector(h.remembered),vector(p.getDeltaMovement()),vector(h.point.subtract(p.position())));
        h.remembered=new Vec3(motion.x(),motion.y(),motion.z());p.setDeltaMovement(h.remembered);p.hurtMarked=true;
        send(p,h,"mag_movement_update");
    }
    public static boolean release(ServerPlayer p){if(!server(p)||!active(p))return false;var owned=HOLDS.get(p.getUUID());if(owned.identity.owner()!=p)return false;abort(p);return true;}
    public static boolean release(ServerPlayer p,long input){if(!server(p)||input<=0)return false;var h=HOLDS.get(p.getUUID());return h!=null&&h.identity.matches(p,input)&&release(p);}
    public static boolean release(ServerPlayer p,long input,long token,long epoch){if(!server(p))return false;var h=HOLDS.get(p.getUUID());return h!=null&&h.identity.matches(p,input,token,epoch)&&release(p,input);}
    public static boolean abort(ServerPlayer p,long input,long token,long epoch){if(!server(p))return false;var h=HOLDS.get(p.getUUID());return h!=null&&h.identity.matches(p,input,token,epoch)&&abort(p,input);}
    public static boolean abort(ServerPlayer p,long input){if(!server(p)||input<=0)return false;var h=HOLDS.get(p.getUUID());if(h==null||!h.identity.matches(p,input))return false;abort(p);return true;}
    public static void abort(ServerPlayer p) {
        if(!server(p))return;var owned=HOLDS.get(p.getUUID());if(owned!=null&&owned.identity.owner()!=p)return;var h=HOLDS.remove(p.getUUID());if(h==null)return;
        if(h.state==AbilityStorage.get(p)&&h.state.category.equals("electromaster"))h.state.addExperience(ID,MagneticRules.movementExperience(p.position().distanceTo(h.start)));
        cn.academy.port.achievements.ClassicAchievements.trigger(p,"electromaster.mag_movement");
        p.fallDistance=0;send(p,h,"mag_movement_end");AbilityStorage.save(p);
    }
    public static boolean active(ServerPlayer p){return p!=null&&HOLDS.containsKey(p.getUUID());}
    public static void remove(ServerPlayer p){if(server(p)){abort(p);INPUTS.forget(p);}}
    public static void clear(){HOLDS.clear();INPUTS.clear();nextToken=0;}
    private static boolean server(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static boolean canAct(ServerPlayer p,AbilityProgress s){return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&s.category.equals("electromaster")&&s.level>=2&&s.canUse(ID)&&finite(p.position())&&finite(ClassicRaytrace.direction(p));}
    private static boolean valid(ServerPlayer p,Hold h){return h.identity.owner()==p&&(h.identity.input()==0||h.identity.owns(p))&&h.level==p.serverLevel()&&h.state==AbilityStorage.get(p)&&canAct(p,h.state)&&h.level.getGameTime()>=h.lastTick;}
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static MagneticRules.Vector vector(Vec3 v){return new MagneticRules.Vector(v.x,v.y,v.z);}
    private static Set<ServerPlayer> audience(ServerPlayer p){var a=new LinkedHashSet<ServerPlayer>();for(var v:p.serverLevel().players())if(v.distanceToSqr(p)<=32*32)a.add(v);a.add(p);return a;}
    private static void send(ServerPlayer p,Hold h,String kind){var t=new CompoundTag();t.putString("kind",kind);h.identity.write(t);t.putLong("tick",h.lastTick);t.putDouble("tx",h.point.x);t.putDouble("ty",h.point.y);t.putDouble("tz",h.point.z);t.putDouble("cp_hint",h.cost.cpPerTick());for(var v:h.audience)if(!v.hasDisconnected())PacketDistributor.sendToPlayer(v,new AcademyNetwork.ClientData(t));}
    private static final class Hold {
        final LegacySingleKeyServerIdentity identity;final ServerLevel level;final AbilityProgress state;final MagneticRules.Movement cost;final Vec3 start;final Entity target;final double keepOverload;final long token;final Set<ServerPlayer> audience;
        Vec3 point,remembered=Vec3.ZERO;long lastTick;
        Hold(LegacySingleKeyServerIdentity identity,MagneticRules.Movement cost,Vec3 start,Entity target,Vec3 point,double keep,Set<ServerPlayer> audience){this.identity=identity;this.level=identity.level();this.state=identity.state();this.cost=cost;this.start=start;this.target=target;this.point=point;keepOverload=keep;this.token=identity.token();this.audience=audience;lastTick=level.getGameTime()-1;}
    }
}
