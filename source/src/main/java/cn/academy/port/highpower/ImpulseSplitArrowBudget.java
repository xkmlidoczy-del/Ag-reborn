package cn.academy.port.highpower;

import cn.academy.port.highpower.core.ImpulseSplitRules;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** One native integer damage budget split between two server object identities. NBT never grants authority. */
@EventBusSubscriber(modid="academy")
public final class ImpulseSplitArrowBudget {
    private static final int MAX_SHARES=4096;
    private static final Map<Arrow,Share> SHARES=new IdentityHashMap<>();
    private static final StackWalker WALKER=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final ThreadLocal<Frame> FRAME=new ThreadLocal<>();
    private static final class Share {
        final Arrow arrow;final ServerLevel world;final UUID uuid;final int entity;final Entity owner;
        final int budget;final double base;boolean consumed,retired;
        Share(Arrow arrow,int budget,double base){this.arrow=arrow;world=(ServerLevel)arrow.level();uuid=arrow.getUUID();entity=arrow.getId();owner=arrow.getOwner();this.budget=budget;this.base=base;}
    }
    public static final class Frame {
        private final AbstractArrow arrow;private final Share share;private final Frame previous;
        private Frame(AbstractArrow arrow,Share share,Frame previous){this.arrow=arrow;this.share=share;this.previous=previous;}
    }
    public record Snapshot(int budget,boolean consumed,boolean retired,double nativeBase){}
    private ImpulseSplitArrowBudget(){}
    public static Snapshot snapshot(Arrow arrow){if(!(arrow.level() instanceof ServerLevel w)||!w.getServer().isSameThread())return null;var s=SHARES.get(arrow);return s==null?null:new Snapshot(s.budget,s.consumed,s.retired,s.base);}
    public static boolean candidate(AbstractArrow a){return a instanceof Arrow arrow&&plain(arrow)&&!SHARES.containsKey(arrow);}
    private static boolean plain(Arrow a){
        if(a.getClass()!=Arrow.class||!(a.level() instanceof ServerLevel w)||!w.getServer().isSameThread()
                ||!(a instanceof NativeImpulseSplitArrowAccess)||a.isRemoved()||a.inGround||a.isNoPhysics()||a.isNoGravity()
                ||a.isPassenger()||a.isVehicle()||a.isCritArrow()||a.isOnFire()||a.getPierceLevel()!=0
                ||!Double.isFinite(a.getBaseDamage())||a.getBaseDamage()<0||a.getBaseDamage()>64
                ||!Double.isFinite(a.getDeltaMovement().length())||a.getDeltaMovement().length()>8)return false;
        var pickup=a.getPickupItemStackOrigin();var weapon=a.getWeaponItem();
        if(!ItemStack.isSameItemSameComponents(pickup,new ItemStack(Items.ARROW))||pickup.getCount()!=1)return false;
        if(weapon==null||weapon.isEmpty())return true;
        if(!weapon.is(Items.BOW)||weapon.getCount()!=1)return false;
        var canonical=new ItemStack(Items.BOW);int damage=weapon.getDamageValue();
        if(damage<0||damage>canonical.getMaxDamage())return false;
        canonical.setDamageValue(damage);
        return ItemStack.isSameItemSameComponents(weapon,canonical);
    }
    /** Only the production paid split can originate a pair. Before birth failure, neither side can borrow its sibling's share. */
    static boolean arm(Arrow original,Arrow copy){
        if(WALKER.getCallerClass()!=NewSkillRuntime.class||!candidate(original)||!plain(copy)||SHARES.containsKey(copy)
                ||SHARES.size()>MAX_SHARES-2||original.level()!=copy.level()||original==copy||original.getUUID().equals(copy.getUUID())
                ||original.getOwner()!=copy.getOwner()||original.getBaseDamage()!=copy.getBaseDamage()
                ||!original.position().equals(copy.position())||!original.getDeltaMovement().equals(copy.getDeltaMovement())
                ||((ServerLevel)original.level()).getEntity(original.getUUID())!=original
                ||((ServerLevel)copy.level()).getEntity(copy.getUUID())!=null)return false;
        var amounts=ImpulseSplitRules.damageBudget(original.getDeltaMovement().length(),original.getBaseDamage(),original.getWeaponItem()!=null);
        double base=original.getBaseDamage()*Math.sqrt(.5);
        var first=new Share(original,amounts.first(),base);var second=new Share(copy,amounts.second(),base);
        // Persisted zero is the fail-closed state on save/reload, copied NBT, missing object identity or lost registration.
        original.setBaseDamage(0);copy.setBaseDamage(0);SHARES.put(original,first);SHARES.put(copy,second);return true;
    }
    private static boolean nativeCaller(){return WALKER.walk(s->s.skip(2).findFirst().map(f->f.getDeclaringClass()==AbstractArrow.class).orElse(false));}
    public static Frame beginNativeHit(AbstractArrow arrow){
        if(!nativeCaller()||!(arrow.level() instanceof ServerLevel w)||!w.getServer().isSameThread())return null;
        Frame f=new Frame(arrow,arrow instanceof Arrow a?SHARES.get(a):null,FRAME.get());FRAME.set(f);return f;
    }
    public static void endNativeHit(Frame frame){if(!nativeCaller()||frame==null)return;if(FRAME.get()!=frame)throw new IllegalStateException("split native frame order");if(frame.previous==null)FRAME.remove();else FRAME.set(frame.previous);}
    private static boolean identity(Share s){return !s.retired&&!s.arrow.isRemoved()&&s.world.getServer().isSameThread()
            &&s.arrow.level()==s.world&&s.arrow.getUUID().equals(s.uuid)&&s.arrow.getId()==s.entity
            &&s.world.getEntity(s.uuid)==s.arrow&&s.world.getEntity(s.entity)==s.arrow&&s.arrow.getOwner()==s.owner
            &&s.arrow.getBaseDamage()==0&&plain(s.arrow);}
    public static double nativeBaseDamage(AbstractArrow arrow,double ordinary){
        if(!nativeCaller())return ordinary;
        Frame f=FRAME.get();if(f==null||f.arrow!=arrow||f.share==null)return ordinary;
        return !f.share.consumed&&identity(f.share)?f.share.base:0;
    }
    /** Consume before the first real hurt call, including rejected damage and throwing/canceling foreign hooks. */
    public static float nativeHurtAmount(AbstractArrow arrow,Entity target,DamageSource source,float ordinary){
        if(!nativeCaller())return ordinary;
        Frame f=FRAME.get();if(f==null||f.arrow!=arrow||f.share==null)return ordinary;
        Share s=f.share;boolean unused=!s.consumed;s.consumed=true;
        if(!unused||!identity(s)||target==null||target.level()!=s.world||source==null||source.getDirectEntity()!=arrow
                ||!source.is(DamageTypes.ARROW)||source.getEntity()!=(s.owner==null?arrow:s.owner))return 0;
        return ImpulseSplitRules.capNativeDamage(s.budget,ordinary);
    }
    public static void ownerChanged(AbstractArrow arrow){if(!(arrow.level() instanceof ServerLevel w)||!w.getServer().isSameThread())return;var s=arrow instanceof Arrow a?SHARES.get(a):null;if(s!=null){s.retired=true;s.consumed=true;}}
    @SubscribeEvent public static void left(EntityLeaveLevelEvent e){if(e.getEntity() instanceof AbstractArrow a&&!e.getLevel().isClientSide())ownerChanged(a);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){SHARES.entrySet().removeIf(x->x.getValue().world.getServer()==e.getServer()&&x.getKey().isRemoved());}
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){SHARES.entrySet().removeIf(x->x.getValue().world.getServer()==e.getServer());FRAME.remove();}
}
