package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.highpower.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual opposing LAN clients: both abilities enter through normal GLFW keys.
 * Only the pre-baseline fixture configures terrain/enrollment/positions. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class DualGroundingQa {
    private static final Path SIGNALS=Path.of(System.getProperty("academy.qa.consentDir","dual-grounding-signals"));
    private static UUID defender,attacker;
    private static BigDecimal hitShield,hitHealth,postShield,postHealth,spent;
    private static double attackerCpBefore,attackerCpAtHit,defenderBudgetBefore,defenderCpBefore;
    private static long cooldownBefore;
    private static boolean precision,post,sampled;
    private static ServerPlayer pendingPlayer;
    private static long age;
    private static String round;
    private DualGroundingQa(){}
    static boolean run(Minecraft mc,String action){
        try {
            if(action.equals("await-peer"))return mc.level.players().size()>=2;
            if(action.startsWith("await:"))return Files.isRegularFile(SIGNALS.resolve(action.substring(6)));
            if(action.startsWith("signal:")){signal(action.substring(7));return true;}
            if(action.equals("prepare")){
                server(mc,p->{
                    defender=p.getUUID();var peer=peer(p);attacker=peer.getUUID();
                    for(var pos:BlockPos.betweenClosed(-5,-61,-5,5,-61,9))p.serverLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);
                    for(var pos:BlockPos.betweenClosed(-5,-60,-5,5,-54,9))p.serverLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                    for(var actor:List.of(p,peer)){actor.setGameMode(GameType.SURVIVAL);actor.getAbilities().flying=false;actor.onUpdateAbilities();
                        var team=actor.getTeam();if(team!=null)p.getScoreboard().removePlayerFromTeam(actor.getScoreboardName(),team);}
                    require(AcademyConfig.ATTACK_PLAYERS.get()&&p.server.isPvpAllowed()&&peer.canHarmPlayer(p),"actual configured PvP permits the opposing skill");
                    peer.teleportTo(.5,-60,4.5);peer.getInventory().clearContent();
                    var s=AbilityStorage.get(peer);s.selectCategory("electromaster");s.setLevel(5);
                    peer.getPersistentData().remove(HighPowerCombat.DATA_KEY);require(HighPowerCombat.enroll(peer,5),"attacker actual enrollment");
                    for(var e:SkillCatalog.REGISTERED)if(e.category().equals("electromaster"))s.learn(e.id());
                    s.activated=true;s.overloadFine=true;s.cooldowns.clear();s.presets.switchTo(0);
                    for(int slot=0;slot<4;slot++)s.presets.edit(0,slot,slot==2?"arc_gen":"",s::learned);
                    HighPowerResources.initializeBudget(peer);AbilityStorage.save(peer);AcademyNetwork.sync(peer);
                });signal("prepared");return true;
            }
            if(action.startsWith("baseline:")){
                round=action.substring(9);server(mc,p->{post=sampled=false;pendingPlayer=null;precision=false;spent=BigDecimal.ZERO;hitShield=hitHealth=postShield=postHealth=null;
                    attackerCpBefore=AbilityStorage.get(peer(p)).cp;attackerCpAtHit=Double.NaN;defenderBudgetBefore=HighPowerResources.availableCp(p);defenderCpBefore=AbilityStorage.get(p).cp;
                    cooldownBefore=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_grounding_return");
                    require(!GroundingReturn.active(p),"fresh real stance each round");});return true;
            }
            if(action.equals("await-active"))return server(mc,p->{return GroundingReturn.active(p);});
            if(action.startsWith("check:")){
                server(mc,p->{boolean precise=action.substring(6).equals("precise");
                    require(post&&sampled&&spent.signum()>0,"real opposing arc committed specialist absorption");
                    require(postHealth.compareTo(hitHealth)==0,"specialist protects actual health");
                    require(precision==precise,"precision decision matches actual native age="+age);
                    require(precise?age<6:age>=6,"actual six-tick precision boundary");
                    require(precise?postShield.compareTo(hitShield)==0:postShield.compareTo(hitShield)<0,"actual shield refund or debit");
                    require(attackerCpAtHit<attackerCpBefore,"attacker actually spent native CP at real hit");
                    require(HighPowerResources.availableCp(p)==defenderBudgetBefore&&AbilityStorage.get(p).cp==defenderCpBefore,"stance does not consume or refund CP");
                    require(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_grounding_return")>cooldownBefore,"actual zero-CP start commits its cooldown");
                    com.mojang.logging.LogUtils.getLogger().info("QA_DUAL_GROUNDING_PASS round={} skill=grounding_return source=electromaster.arc_gen nativeAge={} spent={} precision={} shieldBefore={} shieldAfter={} hpBefore={} hpAfter={} attackerCpBefore={} attackerCpAfter={} defenderBudgetBefore={} defenderBudgetAfter={} realTwoClientInput=true",round,age,spent,precision,hitShield,postShield,hitHealth,postHealth,attackerCpBefore,AbilityStorage.get(peer(p)).cp,defenderBudgetBefore,HighPowerResources.availableCp(p));
                });return true;
            }
            if(action.equals("finish")){signal("checked");com.mojang.logging.LogUtils.getLogger().info("QA_DUAL_GROUNDING_DONE");return true;}
            throw new IllegalArgumentException(action);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void incoming(LivingIncomingDamageEvent e){
        if(e.getEntity() instanceof ServerPlayer p&&p.getUUID().equals(defender)&&electric(e.getSource())){
            hitShield=HighPowerCombat.shield(p);hitHealth=new BigDecimal(HighPowerCombat.healthText(p));attackerCpAtHit=AbilityStorage.get((ServerPlayer)e.getSource().getEntity()).cp;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void settled(LivingDamageEvent.Post e){
        if(e.getEntity() instanceof ServerPlayer p&&p.getUUID().equals(defender)&&electric(e.getSource())){
            post=true;pendingPlayer=p;
            try {var f=GroundingReturn.class.getDeclaredField("CONTEXTS");f.setAccessible(true);Object c=((Map<?,?>)f.get(null)).get(defender);require(c!=null,"actual stance retained during real post");
                age=p.serverLevel().getGameTime()-(long)field(c,"started");
            }catch(ReflectiveOperationException x){throw new IllegalStateException(x);}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void afterNativeStack(ServerTickEvent.Post event){
        var p=pendingPlayer;if(p==null||sampled)return;
        try {var f=GroundingReturn.class.getDeclaredField("CONTEXTS");f.setAccessible(true);Object c=((Map<?,?>)f.get(null)).get(defender);require(c!=null,"actual stance remains after native stack");
            spent=(BigDecimal)field(c,"spent");precision=(boolean)field(c,"precisionUsed");
            postShield=HighPowerCombat.shield(p);postHealth=new BigDecimal(HighPowerCombat.healthText(p));sampled=true;pendingPlayer=null;
        }catch(ReflectiveOperationException x){throw new IllegalStateException(x);}
    }
    private static boolean electric(net.minecraft.world.damagesource.DamageSource source){return source instanceof AbilityDamage.SkillDamageSource s&&s.skill.equals("electromaster.arc_gen")&&source.getEntity()!=null&&source.getEntity().getUUID().equals(attacker);}
    private static Object field(Object o,String name)throws ReflectiveOperationException{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static ServerPlayer peer(ServerPlayer p){return p.server.getPlayerList().getPlayers().stream().filter(v->v!=p).findFirst().orElseThrow();}
    private static void signal(String name)throws java.io.IOException{Files.createDirectories(SIGNALS);Files.writeString(SIGNALS.resolve(name),"grounding\n");}
    private static void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> task){server(mc,p->{task.accept(p);return true;});}
    private static <T>T server(Minecraft mc,java.util.function.Function<ServerPlayer,T> task){return mc.getSingleplayerServer().submit(()->task.apply(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()))).join();}
    private static void require(boolean v,String text){if(!v)throw new IllegalStateException("QA_DUAL_GROUNDING_FAILED "+text);}
}
