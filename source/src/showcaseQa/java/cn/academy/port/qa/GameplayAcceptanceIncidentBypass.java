package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** NEW332 real arrow reduction and real thrown-projectile turning, without fabricated collision/skill callbacks. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceIncidentBypass {
    private static UUID owner;private static String mode;
    private static Projectile projectile;private static Zombie enemy;
    private static Vec3 beforeVelocity,beforePosition,beforeToward;private static double beforeDamage,cp,budget,minCp,minBudget;
    private static boolean begun,changed,passed;private static long paidClock;
    private GameplayAcceptanceIncidentBypass(){}
    public static void run(Minecraft mc,String action){
        if(action.startsWith("relay-state:")){
            int expected=Integer.parseInt(action.substring(12));GameplayAcceptanceTpVm.server(mc,p->{
                int points=SpatialRelay.points(p);var receipt=SpatialRelay.receipt(p);
                require(points==expected,"true short tap must preserve expected real anchor count="+expected+" actual="+points+" receipt="+receipt);
                LogUtils.getLogger().info("QA_RELAY_SHORT_TAP points={} expected={} held={} receipt={} actualInput=true",points,expected,SpatialRelay.heldTicks(p),receipt);
            });return;
        }
        if(action.startsWith("setup:")){
            mode=action.substring(6);require(mode.equals("arrow")||mode.equals("snowball"),"known native projectile mode");
            LiveInputQa.run(mc,"setup:incident_bypass");
            GameplayAcceptanceTpVm.server(mc,p->{owner=p.getUUID();begun=changed=passed=false;projectile=null;
                for(var e:p.serverLevel().getEntities(p,p.getBoundingBox().inflate(30)))if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
                var feet=p.blockPosition();for(int x=-4;x<=4;x++)for(int z=-4;z<=22;z++){
                    p.serverLevel().setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<5;y++)p.serverLevel().setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
                }
                enemy=new Zombie(EntityType.ZOMBIE,p.serverLevel());enemy.setNoAi(true);enemy.setSilent(true);enemy.setPos(p.position().add(0,0,12));require(p.serverLevel().addFreshEntity(enemy),"real ordinary native enemy");
            });return;
        }
        GameplayAcceptanceTpVm.server(mc,p->{require(p.getUUID().equals(owner),"same incident actor");switch(action){
            case "baseline"->{cp=minCp=AbilityStorage.get(p).cp;budget=minBudget=HighPowerResources.availableCp(p);paidClock=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_incident_bypass");begun=true;}
            case "launch"->{
                require(begun&&projectile==null,"one real incoming projectile after actual hold arms the window");
                require(NewSkillRuntime.windowArmed(p),"real eight-tick key hold opened the one-use production window");
                if(mode.equals("arrow")){var a=new Arrow(p.serverLevel(),enemy,new ItemStack(Items.ARROW),new ItemStack(Items.BOW));a.setBaseDamage(2.5);projectile=a;}
                else{var ball=new Snowball(EntityType.SNOWBALL,p.serverLevel());ball.setOwner(enemy);projectile=ball;}
                projectile.setPos(p.getEyePosition().add(0,0,6));projectile.setDeltaMovement(0,0,-.8);
                require(p.serverLevel().addFreshEntity(projectile),"real native incoming projectile joins world");
            }
            case "assert"->{
                require(changed,"actual selected ordinary projectile changed its real base damage/velocity; pose cannot pass");
                require(!NewSkillRuntime.windowArmed(p),"one actual interception consumed the real window");
                require(minCp<cp-1e-6||minBudget<budget-1e-6||p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_incident_bypass")>paidClock,"real finite arm payment");passed=true;
                LogUtils.getLogger().info("QA_GAMEPLAY_PASS skill=incident_bypass mode={} mechanism=actual_native_projectile_reduction_or_turn entity={} nativeCpDip={} budgetDip={} noSyntheticCollision=true",mode,projectile.getUUID(),cp-minCp,budget-minBudget);
            }
            default->throw new IllegalArgumentException(action);
        }});
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void before(ServerTickEvent.Post e){sample(e,false);}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void after(ServerTickEvent.Post e){sample(e,true);}
    private static void sample(ServerTickEvent.Post e,boolean after){
        if(!begun||passed||owner==null)return;var p=e.getServer().getPlayerList().getPlayer(owner);if(p==null)return;
        minCp=Math.min(minCp,AbilityStorage.get(p).cp);minBudget=Math.min(minBudget,HighPowerResources.availableCp(p));
        if(projectile==null||projectile.isRemoved()||changed)return;
        if(!after&&!projectile.getPersistentData().getBoolean(NewSkillRuntime.BYPASSED)){
            beforeVelocity=projectile.getDeltaMovement();beforePosition=projectile.position();beforeToward=p.getEyePosition().subtract(beforePosition);if(projectile instanceof Arrow a)beforeDamage=a.getBaseDamage();
        }else if(after&&projectile.getPersistentData().getBoolean(NewSkillRuntime.BYPASSED)&&beforeVelocity!=null){
            require(projectile.position().equals(beforePosition),"skill cannot teleport the ordinary projectile during the post-tick bypass");
            if(projectile instanceof Arrow a){require(Math.abs(a.getBaseDamage()-beforeDamage*.6)<1e-12&&a.getDeltaMovement().equals(beforeVelocity),"actual ordinary arrow loses exactly forty percent base damage and preserves native trajectory");}
            else{
                var velocity=projectile.getDeltaMovement();double dot=velocity.normalize().dot(beforeToward.normalize());
                require(Math.abs(velocity.length()-beforeVelocity.length())<1e-10&&Math.abs(dot-.5)<1e-10&&Math.abs(velocity.x)>.1,"actual native snowball turns sixty degrees beside the actor with unchanged speed");
            }
            changed=true;
            LogUtils.getLogger().info("QA_INCIDENT_NATIVE_WITNESS mode={} entity={} beforeVelocity={} afterVelocity={} beforeBase={} afterBase={} positionUnchanged=true",mode,projectile.getUUID(),beforeVelocity,projectile.getDeltaMovement(),beforeDamage,projectile instanceof Arrow a?a.getBaseDamage():0);
        }
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("QA_INCIDENT_FAILED "+message);}
}
