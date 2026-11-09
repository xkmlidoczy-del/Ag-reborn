package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ImpulseSplitRules;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** QA-only native item/arrow branches. Activation is exclusively real scripted GLFW input.
 * Initial floor, finite inventory, ordinary mobs and one ordinary incoming arrow are fixtures.
 * Never calls a skill start, collision, hurt, visual receiver, or budget registration. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT)
public final class GameplayAcceptanceVmItemSplit {
    private static final class Fixture {
        final UUID owner; final String mode; final long startingClock;
        boolean begun, passed, pairSeen, crouchSeen; UUID item; Vec3 itemBefore, side, expectedSide;
        YawPalm.Receipt pushed; double maxSide, cpBefore, budgetBefore;
        Husk shooter; final List<Husk> targets=new ArrayList<>();
        Arrow incoming; Vec3 beforeVelocity; double beforeDamage;
        final Map<UUID,Integer> shares=new HashMap<>(); final Set<UUID> hitArrows=new HashSet<>(), hitTargets=new HashSet<>();
        final Map<UUID,Float> healthBefore=new HashMap<>(); double damageSum, velocityRatio, separation; int integerBefore, integerAfter;
        Fixture(ServerPlayer p,String mode){owner=p.getUUID();this.mode=mode;startingClock=clock(p,skill());}
        String skill(){return mode.startsWith("yaw-")?"yaw_palm":"impulse_split";}
    }
    private static Fixture fixture;
    private GameplayAcceptanceVmItemSplit(){}

    public static void run(Minecraft mc,String action){
        if(action.startsWith("setup:")){
            String mode=action.substring(6);require(Set.of("yaw-left","yaw-right","split-narrow").contains(mode),"registered branch");
            LiveInputQa.run(mc,"setup:"+(mode.startsWith("yaw-")?"yaw_palm":"impulse_split"));
            GameplayAcceptanceTpVm.server(mc,p->prepare(p,mode));return;
        }
        if(action.equals("aim-item")){
            var server=mc.getSingleplayerServer();require(server!=null&&fixture!=null,"actual single-player fixture");
            Vec3 point=server.submit(()->{var p=server.getPlayerList().getPlayer(fixture.owner);return ownedItem(p).getBoundingBox().getCenter();}).join();
            LiveInputQa.aim(mc,point.x+","+point.y+","+point.z);return;
        }
        if(action.startsWith("direction:")){
            mc.player.setYRot(Float.parseFloat(action.substring(10)));mc.player.setXRot(0);return;
        }
        GameplayAcceptanceTpVm.server(mc,p->{
            var f=fixture;require(f!=null&&f.owner.equals(p.getUUID()),"same actual fixture owner");
            switch(action){
                case "tossed"->{
                    var items=p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(7),i->i.getItem().is(Items.IRON_INGOT)&&MagneticItemProvenance.ownedBy(i,p.getUUID()));
                    require(items.size()==1,"one original native Q toss with actual server provenance");
                    var item=items.getFirst();require(item.getClass()==ItemEntity.class&&item.getItem().getCount()==1&&!item.isNoGravity()&&!item.noPhysics,"plain single dropped iron item retains native physics");
                    require(p.getInventory().countItem(Items.IRON_INGOT)==63,"real Q removed exactly one finite initial item");
                    f.item=item.getUUID();f.itemBefore=item.position();
                    LogUtils.getLogger().info("QA_VM_EXTRA_Q_ITEM mode={} item={} position={} velocity={} actualQToss=true inventory=63",f.mode,f.item,item.position(),item.getDeltaMovement());
                }
                case "baseline"->{f.begun=true;f.cpBefore=AbilityStorage.get(p).cp;f.budgetBefore=HighPowerResources.availableCp(p);if(f.item!=null)f.itemBefore=ownedItem(p).position();}
                case "arrow"->launch(p);
                case "assert"->verify(p);
                default->throw new IllegalArgumentException(action);
            }
        });
    }

    private static void prepare(ServerPlayer p,String mode){
        if(fixture!=null){
            if(fixture.shooter!=null)fixture.shooter.discard();for(var t:fixture.targets)t.discard();
            if(fixture.item!=null){var old=p.serverLevel().getEntity(fixture.item);if(old!=null)old.discard();}
        }
        fixture=new Fixture(p,mode);var f=fixture;var feet=p.blockPosition();
        for(int x=-5;x<=5;x++)for(int z=-3;z<=16;z++){
            p.serverLevel().setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<6;y++)p.serverLevel().setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        if(mode.startsWith("yaw-")){
            p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));p.inventoryMenu.broadcastChanges();
        }else{
            f.shooter=husk(p,0,14);
            // Fixed pre-cast bodies, not repositioned on observed split/collision. They straddle the
            // natural +/-10 degree rays beyond the 6.2..7m native interception position.
            f.targets.add(husk(p,-.5,8.8));f.targets.add(husk(p,.5,8.8));
            for(var target:f.targets)f.healthBefore.put(target.getUUID(),target.getHealth());
        }
        AcademyNetwork.sync(p);
        LogUtils.getLogger().info("QA_VM_EXTRA_SETUP mode={} skill={} survival={} actualOrdinaryTargets={} noSkillInvocation=true",mode,f.skill(),!p.isCreative(),f.targets.stream().map(t->t.getUUID()).toList());
    }
    private static Husk husk(ServerPlayer p,double x,double z){
        var target=EntityType.HUSK.create(p.serverLevel());require(target!=null,"ordinary native Husk available");
        target.setNoAi(true);target.setSilent(true);target.setPos(p.getX()+x,p.getY(),p.getZ()+z);
        require(p.serverLevel().addFreshEntity(target),"ordinary native fixture body added");return target;
    }
    private static ItemEntity ownedItem(ServerPlayer p){
        require(p!=null&&fixture!=null&&fixture.item!=null,"real dropped-item identity captured");
        var entity=p.serverLevel().getEntity(fixture.item);
        require(entity instanceof ItemEntity item&&MagneticItemProvenance.ownedBy(item,p.getUUID()),"same native Q item remains owner-proven");return (ItemEntity)entity;
    }
    private static void launch(ServerPlayer p){
        var f=fixture;require(f.mode.equals("split-narrow")&&f.begun&&f.incoming==null,"one incoming native arrow after real direction inputs");
        var arrow=new Arrow(p.serverLevel(),f.shooter,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));
        arrow.setPos(p.position().add(0,1.15,7));arrow.setDeltaMovement(0,0,-.8);
        f.incoming=arrow;f.beforeVelocity=arrow.getDeltaMovement();f.beforeDamage=arrow.getBaseDamage();
        require(p.serverLevel().addFreshEntity(arrow),"real enemy-owned native gravity arrow added");
        LogUtils.getLogger().info("QA_VM_EXTRA_INCOMING arrow={} position={} velocity={} ordinaryGravity={} nativeDamage={} targets={}",arrow.getUUID(),arrow.position(),arrow.getDeltaMovement(),!arrow.isNoGravity(),arrow.getBaseDamage(),f.targets.stream().map(t->t.position()).toList());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void before(ServerTickEvent.Post event){
        var f=fixture;if(f==null||!f.begun||f.passed||f.incoming==null||f.incoming.isRemoved()||f.pairSeen)return;
        if(!f.incoming.getPersistentData().getBoolean(NewSkillRuntime.BYPASSED)){f.beforeVelocity=f.incoming.getDeltaMovement();f.beforeDamage=f.incoming.getBaseDamage();}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void after(ServerTickEvent.Post event){
        var f=fixture;if(f==null||!f.begun||f.passed)return;var p=event.getServer().getPlayerList().getPlayer(f.owner);if(p==null)return;
        if(f.mode.startsWith("yaw-")){
            for(var receipt:YawPalm.receipts(p))if(receipt.status()==YawPalm.PUSHED&&f.item!=null&&f.item.equals(receipt.victim())){
                f.pushed=receipt;f.side=receipt.velocityDelta().normalize();f.crouchSeen=p.isShiftKeyDown();
                var expected=cn.academy.port.highpower.core.YawPalmRules.side(p.getYRot(),f.crouchSeen);
                if(expected!=null)f.expectedSide=new Vec3(expected.x(),expected.y(),expected.z());
            }
            if(f.side!=null&&p.serverLevel().getEntity(f.item) instanceof ItemEntity item)
                f.maxSide=Math.max(f.maxSide,item.position().subtract(f.itemBefore).dot(f.side));
        }else if(!f.pairSeen&&f.incoming!=null&&!f.incoming.isRemoved()&&f.incoming.getPersistentData().getBoolean(NewSkillRuntime.BYPASSED)){
            var pair=p.serverLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(20),a->a.getOwner()==f.shooter&&a.getPersistentData().getBoolean(NewSkillRuntime.BYPASSED));
            if(pair.size()!=2)return;
            var a=pair.get(0);var b=pair.get(1);var sa=ImpulseSplitArrowBudget.snapshot(a);var sb=ImpulseSplitArrowBudget.snapshot(b);
            require(sa!=null&&sb!=null&&!sa.consumed()&&!sb.consumed(),"two real unused server-object integer permits");
            Vec3 va=a.getDeltaMovement(),vb=b.getDeltaMovement();double old=f.beforeVelocity.length();
            f.separation=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,va.normalize().dot(vb.normalize())))));
            f.velocityRatio=va.length()/old;
            require(f.separation>=20&&f.separation<20.1,"independently observed accepted directions meet the strict20-degree floor and remain near20, not +/-45");
            require(f.velocityRatio>.5&&f.velocityRatio<.53&&Math.abs(va.length()-vb.length())<1e-9,"natural near-parallel split slows both arrows below equal-energy .707 speed");
            require(va.add(vb).length()<=old&&va.lengthSqr()+vb.lengthSqr()<=f.beforeVelocity.lengthSqr(),"actual emitted momentum magnitude/energy never amplify incoming native values");
            f.integerBefore=ImpulseSplitRules.damageBudget(old,f.beforeDamage,f.incoming.getWeaponItem()!=null).total();f.integerAfter=sa.budget()+sb.budget();
            require(f.integerBefore==f.integerAfter&&sa.budget()>0&&sb.budget()>0,"actual integer budget is partitioned exactly into two positive native shares");
            f.shares.put(a.getUUID(),sa.budget());f.shares.put(b.getUUID(),sb.budget());f.pairSeen=true;
            LogUtils.getLogger().info("QA_VM_EXTRA_NARROW_PAIR actualDegrees={} nativeSpeedBefore={} emittedSpeedRatio={} integerBefore={} integerAfter={} permits={} nativeGravity=true momentumMagnitudeNotAmplified=true energyNotAmplified=true",f.separation,old,f.velocityRatio,f.integerBefore,f.integerAfter,f.shares);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Post event){
        var f=fixture;if(f==null||!f.begun||f.passed||!f.pairSeen||!f.targets.contains(event.getEntity())||event.getNewDamage()<=0)return;
        if(!(event.getSource().getDirectEntity() instanceof Arrow arrow)||!f.shares.containsKey(arrow.getUUID())||event.getSource().getEntity()!=f.shooter)return;
        var share=ImpulseSplitArrowBudget.snapshot(arrow);
        require(share!=null&&share.consumed()&&share.budget()==f.shares.get(arrow.getUUID()),"actual native hurt consumed this arrow's own server-object share");
        require(event.getNewDamage()<=share.budget(),"actual native target damage never exceeds its integer permit");
        f.hitArrows.add(arrow.getUUID());f.hitTargets.add(event.getEntity().getUUID());f.damageSum+=event.getNewDamage();
        LogUtils.getLogger().info("QA_VM_EXTRA_NATIVE_HIT arrow={} target={} actualDamage={} integerShare={} shareConsumed=true",arrow.getUUID(),event.getEntity().getUUID(),event.getNewDamage(),share.budget());
    }
    private static void verify(ServerPlayer p){
        var f=fixture;require(f.begun&&!f.passed,"one completed real branch");
        if(f.mode.startsWith("yaw-")){
            var item=ownedItem(p);require(f.pushed!=null&&f.pushed.input()>0&&f.pushed.velocityDelta().horizontalDistance()>.249&&Math.abs(f.pushed.velocityDelta().y)<1e-12,"real production receipt pushed the original Q item sideways by .25");
            require(f.crouchSeen==f.mode.equals("yaw-right"),"actual normal crouch input selected the named side");
            require(f.expectedSide!=null&&f.pushed.velocityDelta().dot(f.expectedSide)>.249,"real item impulse matches caster-relative left/right, not merely any horizontal movement");
            require(f.maxSide>.15,"same native item actually travelled along its chosen side");
            require(item.getItem().getCount()==1&&p.getInventory().countItem(Items.IRON_INGOT)==63,"finite original Q item and inventory conserved");
            require(clock(p,"yaw_palm")>f.startingClock,"actual finite payment/cooldown clock advanced");
            LogUtils.getLogger().info("QA_GAMEPLAY_MODE_PASS mode={} skill=yaw_palm actualQToss={} receipt={} sideDisplacement={} inventory=63 itemCount=1 actualCrouch={} noSyntheticPush=true",f.mode,f.item,f.pushed,f.maxSide,f.crouchSeen);
        }else{
            require(f.pairSeen&&f.hitTargets.size()==2&&f.hitArrows.size()==2,"both distinct emitted native arrows actually hit the two prepared native bodies");
            double healthLoss=f.targets.stream().mapToDouble(t->f.healthBefore.get(t.getUUID())-t.getHealth()).sum();
            require(f.damageSum>0&&f.damageSum<=f.integerBefore&&Math.abs(healthLoss-f.damageSum)<1e-4,"real two-target total damage is positive, bounded by original integer budget and reflected in actual health loss");
            require(clock(p,"impulse_split")>f.startingClock,"real narrow window was paid through production input");
            LogUtils.getLogger().info("QA_GAMEPLAY_MODE_PASS mode=split-narrow skill=impulse_split actualDegrees={} emittedSpeedRatio={} integerBefore={} integerAfter={} actualTwoTargetDamage={} actualHealthLoss={} nativeHits={} noCollisionOrHurtCalled=true",f.separation,f.velocityRatio,f.integerBefore,f.integerAfter,f.damageSum,healthLoss,f.hitTargets.size());
        }
        f.passed=true;
    }
    private static long clock(ServerPlayer p,String skill){return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+skill);}
    private static void require(boolean okay,String why){if(!okay)throw new IllegalStateException("QA_VM_EXTRA_FAILED "+why);}
}
