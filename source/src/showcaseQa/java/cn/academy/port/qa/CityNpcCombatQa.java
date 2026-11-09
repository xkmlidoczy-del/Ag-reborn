package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.EntityAbilityRuntime;
import cn.academy.port.city.*;
import cn.academy.port.highpower.*;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

/** One integrated real-world acceptance run. Fixtures create actors, never fabricate ability hits. */
@EventBusSubscriber(modid="academy")
final class CityNpcCombatQa {
    private static final Logger LOG=LogUtils.getLogger();
    private static final List<Trial> TRIALS=new ArrayList<>();
    private static final List<Map<String,Object>> LEVELS=new ArrayList<>();
    private static Trial current;
    private static CityNpcEntity recovery,duelist;
    private static Husk recoveryEnemy;
    private static long recoveryStart;
    private static boolean recoveringSeen,recoveredCast,duelDamage,duelSafe;
    private static int recoveryLockedCasts;
    private static int returnRetry;
    private static final class Trial {
        String profile;int level;UUID caster,target;long start,end;double cpBefore,cpMin,overloadMax,shieldMax,damage,targetYMax;
        boolean controlled,shadow,dailyReturned,persistence;int paperBefore,paperAfter;
        transient CityNpcEntity npc;transient Husk husk;
        final List<EntityAbilityRuntime.Receipt> receipts=new ArrayList<>();
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_CITY_COMBAT "+why);}
    private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());}
    private static <T>T server(Minecraft mc,java.util.function.Supplier<T> body){return mc.getSingleplayerServer().submit(body).join();}
    private static Object invoke(Object target,String method,Class<?>[] types,Object...args){
        try{var m=target.getClass().getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(target,args);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static CityNpcEntity npc(ServerLevel world,String profile,int level,double x,double z,String identity){
        var n=CityNpcContent.NPC.get().create(world);require(n!=null,"NPC unavailable");
        var tag=new CompoundTag();tag.putString("Esper",profile);tag.putInt("EsperLevel",level);tag.putString("Look","esper_uniform");tag.putString("Activity","read");
        n.readAdditionalSaveData(tag);n.moveTo(x,0,z,0,0);
        invoke(n,"prepareAmbient",new Class<?>[]{CityNpcEntity.Look.class,CityNpcEntity.Activity.class},n.look(),CityNpcEntity.Activity.READ);
        if(identity!=null)invoke(n,"identity",new Class<?>[]{String.class},identity);else n.combat();
        n.setCustomName(Component.literal(profile+" Lv"+level));n.setCustomNameVisible(true);
        require(world.addFreshEntity(n),"NPC spawn failed");return n;
    }
    private static Husk enemy(ServerLevel world,CityNpcEntity n,double x,double z,boolean stationary){
        var h=EntityType.HUSK.create(world);require(h!=null,"husk unavailable");h.moveTo(x,0,z,180,0);
        h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);h.setHealth(300);h.setPersistenceRequired();h.setNoAi(stationary);
        require(world.addFreshEntity(h),"husk spawn failed");h.setTarget(n);return h;
    }
    static boolean step(Minecraft mc,String op){
        String[] a=op.split(",");
        switch(a[0]){
            case "init" -> server(mc,()->{
                var p=player(mc);var world=p.serverLevel();p.setGameMode(GameType.SPECTATOR);p.teleportTo(-66,1,-33);
                EntityAbilityRuntime.observer=r->{
                    if(current!=null&&r.caster().equals(current.caster))current.receipts.add(r);
                    if(recovery!=null&&r.caster().equals(recovery.getUUID())&&r.phase().equals("START")){
                        if(!recovery.combat().ability().overloadFine)recoveryLockedCasts++;else recoveredCast=true;
                    }
                    LOG.info("QA_NPC_CAST {}",r);
                };
                for(int level=1;level<=5;level++){
                    var n=npc(world,"electro",level,-79,-35,null);var s=n.combat().ability();
                    require(n.combat().runtime().available().stream().allMatch(d->d.level()<=s.level),"level gate bypass");
                    var row=new LinkedHashMap<String,Object>();row.put("level",level);row.put("health",n.getMaxHealth());row.put("cp",s.maxCp());row.put("overload",s.maxOverload());row.put("skills",n.combat().runtime().available().stream().map(EntityAbilityRuntime.Definition::id).toList());LEVELS.add(row);
                    require(n.getMaxHealth()==20+10*level,"HP tier");n.discard();
                }
                recovery=npc(world,"electro",2,-65.5,-52.5,"qa-recovery");var s=recovery.combat().ability();s.cp=0;s.overload=s.maxOverload();s.overloadFine=false;s.cpDelay=s.overloadDelay=32;
                recoveryEnemy=enemy(world,recovery,-65.5,-48.5,true);recoveryStart=world.getGameTime();
                LOG.info("QA_CITY_COMBAT_INIT levels={} nativeDefinitions={}",LEVELS,EntityAbilityRuntime.DEFINITIONS.size());return null;
            });
            case "start" -> server(mc,()->{
                var p=player(mc);var w=p.serverLevel();p.setGameMode(GameType.SPECTATOR);p.teleportTo(-65.5,1,-32.5);
                var t=new Trial();t.profile=a[1];t.level=a.length>2?Integer.parseInt(a[2]):3;t.npc=npc(w,a[1],t.level,-65.5,-25.5,"qa-combat:"+a[1]+":"+t.level);
                boolean stationary=a.length>3&&a[3].equals("stationary");
                t.husk=enemy(w,t.npc,-65.5,stationary?-16:-20.5,stationary);t.caster=t.npc.getUUID();t.target=t.husk.getUUID();t.start=w.getGameTime();
                t.cpBefore=t.cpMin=t.npc.combat().ability().cp;t.paperBefore=t.npc.combat().save().getInt("paper");
                w.setBlock(new BlockPos(-69,-1,-25),a[1].equals("water")?Blocks.WATER.defaultBlockState():Blocks.SMOOTH_QUARTZ.defaultBlockState(),3);
                if(!a[1].equals("water"))require(t.npc.combat().runtime().waterSource()==null,"unplanned water in arena");
                current=t;TRIALS.add(t);LOG.info("QA_CITY_COMBAT_START profile={} cp={} hp={} realAi=true",a[1],t.cpBefore,t.npc.getMaxHealth());return null;
            });
            case "check" -> server(mc,()->{
                var t=current;t.end=player(mc).level().getGameTime();t.paperAfter=t.npc.combat().save().getInt("paper");
                require(t.receipts.stream().anyMatch(r->r.phase().equals("RESOLVE")),"no real resolution "+t.profile+" mode="+t.npc.combat().mode()+" receipts="+t.receipts);
                require(t.cpMin<t.cpBefore&&t.overloadMax>0,"resource ledger not debited "+t.profile);
                switch(t.profile){
                    case "mental"->require(t.controlled,"mental control missing");
                    case "float"->require(t.targetYMax>.5&&t.targetYMax<=5.5,"float stacking / physics failure height="+t.targetYMax);
                    default->require(t.damage>0,"no actual damage "+t.profile+" receipts="+t.receipts);
                }
                if(List.of("water","nitrogen","paper").contains(t.profile))require(t.shieldMax>0,"shield not created "+t.profile);
                if(t.profile.equals("nitrogen"))require(t.receipts.stream().anyMatch(r->r.phase().equals("ABSORB")&&r.actualDamage()>0),"guard never absorbed a real hostile hit");
                if(t.profile.equals("shadow"))require(t.shadow,"native shadow body missing");
                if(t.profile.equals("paper")){
                    require(t.paperAfter<t.paperBefore,"paper not consumed");var old=t.npc;var before=old.combat().save();
                    old.discard();t.npc=npc(player(mc).serverLevel(),"paper",3,old.getX(),old.getZ(),"qa-combat:paper:3");
                    var after=t.npc.combat().save();require(after.getInt("paper")==before.getInt("paper"),"despawn refilled materials");
                    require(t.npc.combat().ability().cp<=t.cpBefore-1,"despawn refilled CP");t.persistence=true;
                }
                t.husk.discard();LOG.info("QA_CITY_COMBAT_CHECK profile={} damage={} cpMin={} overloadMax={} shield={} lift={} control={} shadow={} paper={}->{}",t.profile,t.damage,t.cpMin,t.overloadMax,t.shieldMax,t.targetYMax,t.controlled,t.shadow,t.paperBefore,t.paperAfter);return null;
            });
            case "returned" -> {boolean done=server(mc,()->{
                var t=current;t.dailyReturned=t.npc.combat().mode()==CityNpcCombat.Mode.DAILY;
                if(!t.dailyReturned){
                    if(returnRetry++%100==0)LOG.info("QA_CITY_COMBAT_RETURN_WAIT profile={} mode={} pos={} navDone={}",t.profile,t.npc.combat().mode(),t.npc.position(),t.npc.getNavigation().isDone());
                    require(returnRetry<650,"did not resume daily behavior "+t.profile+" mode="+t.npc.combat().mode());return false;
                }
                require(t.npc.activity()!=CityNpcEntity.Activity.CAST,"cast pose stuck "+t.profile);t.npc.discard();current=null;return null;
            })!=Boolean.FALSE;if(!done)return false;returnRetry=0;}
            case "recovery" -> server(mc,()->{
                require(recoveringSeen&&recoveryLockedCasts==0,"overload lock bypass");
                // Stationary threats can fall outside the retreat leash; native recovery must
                // still restore the exhausted ledger without a respawn or fabricated tick jump.
                var s=recovery.combat().ability();require(s.overloadFine&&s.cp>0,"native recovery did not finish");
                LOG.info("QA_CITY_COMBAT_RECOVERY elapsed={} lockedCasts={} recoveredCast={} cp={} overload={} mode={}",player(mc).level().getGameTime()-recoveryStart,recoveryLockedCasts,recoveredCast,s.cp,s.overload,recovery.combat().mode());
                recovery.discard();recoveryEnemy.discard();return null;
            });
            case "duel_fixture" -> server(mc,()->{
                var p=player(mc);p.setGameMode(GameType.SURVIVAL);p.removeAllEffects();p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(6);p.teleportTo(-65.5,0,-29);
                duelist=npc(p.serverLevel(),"electro",3,-65.5,-25.5,"qa-duelist");return null;
            });
            case "open_duel" -> {
                var n=mc.level.getEntity(duelist.getId());require(n!=null,"duelist not tracked");mc.gameMode.interact(mc.player,n,InteractionHand.MAIN_HAND);
            }
            case "duel_done" -> server(mc,()->{
                var p=player(mc);require(duelDamage&&duelSafe&&!duelist.combat().dueling(),"nonlethal duel failed hp="+p.getHealth()+" mode="+duelist.combat().mode());
                LOG.info("QA_CITY_COMBAT_DUEL hp={} neverLethal={} returned={}",p.getHealth(),duelSafe,duelist.combat().mode());return null;
            });
            case "counter" -> server(mc,()->{
                var p=player(mc);p.setHealth(p.getMaxHealth());p.invulnerableTime=0;
                require(duelist.combat().startDuel(p),"could not start counter fixture");
                var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");s.setLevel(1);s.learn("arc_gen");s.learn(HighPowerSkills.FORKED_ARC);s.setActivateState(true);
                require(HighPowerCombat.enroll(p,1),"player enrollment failed");HighPowerResources.initializeBudget(p);
                s.presets.edit(0,0,HighPowerSkills.FORKED_ARC,id->true);
                p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,duelist.getEyePosition());
                float hp=duelist.getHealth();double cp=HighPowerResources.cp(p);
                require(ElectromasterPrototype.cast(p),"native player cast refused "+HighPowerCombat.unsupportedReason(p));
                require(duelist.getHealth()<hp&&duelist.getHealth()>=duelist.getMaxHealth()*.15f-.01,"native player ability failed to land safely hp="+duelist.getHealth());
                require(HighPowerResources.cp(p)<cp,"player cast did not spend CP");
                LOG.info("QA_CITY_COMBAT_PLAYER_COUNTER before={} after={} cpBefore={} cpAfter={} originalController=true",hp,duelist.getHealth(),cp,HighPowerResources.cp(p));return null;
            });
            case "report" -> {
                var report=server(mc,()->{var r=new LinkedHashMap<String,Object>();r.put("version","a3.55");r.put("nativeDefinitions",EntityAbilityRuntime.DEFINITIONS.size());r.put("levels",LEVELS);r.put("trials",TRIALS);r.put("overloadLock",recoveringSeen&&recoveryLockedCasts==0);r.put("recoveredCast",recoveredCast);r.put("nonlethalDuel",duelDamage&&duelSafe);return r;});
                try{Files.writeString(mc.gameDirectory.toPath().resolve("city-npc-combat.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
                catch(java.io.IOException e){throw new IllegalStateException(e);}LOG.info("QA_CITY_COMBAT_REPORT trials={} file=city-npc-combat.json",TRIALS.size());
            }
            default -> throw new IllegalArgumentException(op);
        }return true;
    }
    @SubscribeEvent public static void observe(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel w))return;
        if(current!=null&&current.npc.level()==w&&!current.npc.isRemoved()){
            var t=current;var n=t.npc;var s=n.combat().ability();t.cpMin=Math.min(t.cpMin,s.cp);t.overloadMax=Math.max(t.overloadMax,s.overload);t.shieldMax=Math.max(t.shieldMax,n.combat().runtime().shield());
            t.damage=Math.max(t.damage,300-t.husk.getHealth());t.targetYMax=Math.max(t.targetYMax,t.husk.getY());
            t.controlled|=t.husk.getTarget()==null||t.husk.isCurrentlyGlowing();t.shadow|=n.combat().runtime().hasShadow();
        }
        if(recovery!=null&&!recovery.isRemoved()&&recovery.level()==w)recoveringSeen|=recovery.combat().mode()==CityNpcCombat.Mode.RECOVERING;
        if(duelist!=null&&!duelist.isRemoved()&&duelist.level()==w)for(var p:w.players()){
            if(duelist.combat().dueling(p)){duelDamage|=p.getHealth()<p.getMaxHealth();duelSafe=p.getHealth()>=Math.max(2,p.getMaxHealth()*.15f)-.01f;}
        }
    }
}
